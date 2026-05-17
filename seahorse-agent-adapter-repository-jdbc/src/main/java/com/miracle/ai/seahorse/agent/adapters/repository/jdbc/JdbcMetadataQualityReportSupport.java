/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataFieldCoverage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineReasonCount;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewFeedbackSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static java.time.Instant.now;

/**
 * 负责 metadata quality report 的只读统计与聚合，
 * 将主适配器中的报表查询职责收敛到独立协作者。
 */
final class JdbcMetadataQualityReportSupport {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMetadataJsonSupport jsonSupport;
    private final BiFunction<String, String, MetadataSchema> schemaLoader;

    JdbcMetadataQualityReportSupport(JdbcTemplate jdbcTemplate,
                                     JdbcMetadataJsonSupport jsonSupport,
                                     BiFunction<String, String, MetadataSchema> schemaLoader) {
        this.jdbcTemplate = Objects.requireNonNull(jdbcTemplate, "jdbcTemplate must not be null");
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
        this.schemaLoader = Objects.requireNonNull(schemaLoader, "schemaLoader must not be null");
    }

    MetadataQualityReport report(String tenantId,
                                 String knowledgeBaseId,
                                 int quarantineTopN,
                                 Integer schemaVersion,
                                 String extractorVersion,
                                 String llmPromptVersion) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKbId = Objects.requireNonNullElse(knowledgeBaseId, "");
        int safeTopN = Math.max(1, Math.min(quarantineTopN, 50));
        Integer safeSchemaVersion = schemaVersion == null || schemaVersion <= 0 ? null : schemaVersion;
        String safeExtractorVersion = Objects.requireNonNullElse(extractorVersion, "");
        String safeLlmPromptVersion = Objects.requireNonNullElse(llmPromptVersion, "");
        MetadataSchema schema = schemaLoader.apply(safeTenantId, safeKbId);
        List<ExtractionSnapshot> snapshots = latestExtractionSnapshots(
                safeTenantId, safeKbId, safeSchemaVersion, safeExtractorVersion, safeLlmPromptVersion);
        List<ReviewSnapshot> reviewSnapshots = reviewSnapshots(
                safeTenantId, safeKbId, safeSchemaVersion, safeExtractorVersion, safeLlmPromptVersion);
        int totalDocuments = totalDocuments(safeKbId, snapshots, reviewSnapshots,
                hasVersionScope(safeSchemaVersion, safeExtractorVersion, safeLlmPromptVersion));
        Map<String, ReviewFieldAggregate> reviewFieldAggregates = reviewFieldAggregates(reviewSnapshots);
        List<MetadataFieldCoverage> fieldCoverages = fieldCoverages(
                schema, snapshots, totalDocuments, reviewFieldAggregates);
        LowConfidenceStats lowConfidenceStats = lowConfidenceStats(fieldCoverages);
        int pendingReviewCount = pendingReviewCount(reviewSnapshots);
        int unresolvedQuarantineCount = countUnresolvedQuarantines(safeTenantId, safeKbId);
        int indexSyncFailureCount = countIndexSyncFailures(safeTenantId, safeKbId);
        List<MetadataQuarantineReasonCount> reasonTopN = quarantineReasonTopN(safeTenantId, safeKbId, safeTopN);
        List<MetadataReviewFeedbackSummary> feedbackSummaries = reviewFeedbackSummaries(reviewSnapshots, safeTopN);
        return new MetadataQualityReport(
                safeTenantId,
                safeKbId,
                safeSchemaVersion,
                safeExtractorVersion,
                safeLlmPromptVersion,
                totalDocuments,
                snapshots.size(),
                averageCoverage(fieldCoverages),
                ratio(lowConfidenceStats.lowConfidenceFields(), lowConfidenceStats.evaluatedFields()),
                reviewPassRate(reviewSnapshots),
                reviewCorrectionRate(reviewSnapshots),
                pendingReviewCount,
                unresolvedQuarantineCount,
                indexSyncFailureCount,
                fieldCoverages,
                feedbackSummaries,
                reasonTopN,
                now());
    }

    private List<MetadataFieldCoverage> fieldCoverages(MetadataSchema schema,
                                                       List<ExtractionSnapshot> snapshots,
                                                       int totalDocuments,
                                                       Map<String, ReviewFieldAggregate> reviewFieldAggregates) {
        List<MetadataFieldCoverage> coverages = new ArrayList<>();
        // 覆盖率以 schema 字段为基准统计，保持治理报表的字段口径稳定。
        for (MetadataFieldDescriptor field : schema.fields()) {
            int covered = 0;
            int lowConfidence = 0;
            for (ExtractionSnapshot snapshot : snapshots) {
                if (hasValue(snapshot.coveredMetadata().get(field.fieldKey()))) {
                    covered++;
                    if (lowConfidence(field, snapshot.fieldQualities())) {
                        lowConfidence++;
                    }
                }
            }
            ReviewFieldAggregate aggregate = reviewFieldAggregates.getOrDefault(
                    field.fieldKey(), ReviewFieldAggregate.empty());
            coverages.add(new MetadataFieldCoverage(
                    field.fieldKey(),
                    field.displayName(),
                    field.required(),
                    covered,
                    totalDocuments,
                    ratio(covered, totalDocuments),
                    lowConfidence,
                    ratio(lowConfidence, covered),
                    aggregate.reviewedDocuments(),
                    aggregate.correctedDocuments(),
                    ratio(aggregate.correctedDocuments(), aggregate.reviewedDocuments())));
        }
        return List.copyOf(coverages);
    }

    private boolean lowConfidence(MetadataFieldDescriptor field, List<Map<String, Object>> qualities) {
        for (Map<String, Object> quality : qualities) {
            String fieldKey = text(quality.get("fieldKey"), "");
            if (field.fieldKey().equals(fieldKey)
                    && doubleValue(quality.get("confidence"), 0D) < field.minConfidence()) {
                return true;
            }
        }
        return false;
    }

    private LowConfidenceStats lowConfidenceStats(List<MetadataFieldCoverage> coverages) {
        int evaluated = coverages.stream().mapToInt(MetadataFieldCoverage::coveredDocuments).sum();
        int lowConfidence = coverages.stream().mapToInt(MetadataFieldCoverage::lowConfidenceDocuments).sum();
        return new LowConfidenceStats(lowConfidence, evaluated);
    }

    private List<ExtractionSnapshot> latestExtractionSnapshots(String tenantId,
                                                               String knowledgeBaseId,
                                                               Integer schemaVersion,
                                                               String extractorVersion,
                                                               String llmPromptVersion) {
        try {
            List<ExtractionSnapshot> snapshots = jdbcTemplate.query("""
                    SELECT doc_id, schema_version, extractor_version, normalized_metadata,
                           approved_metadata, field_quality, raw_candidates
                    FROM (
                        SELECT doc_id, schema_version, extractor_version, normalized_metadata,
                               approved_metadata, field_quality, raw_candidates,
                               ROW_NUMBER() OVER (
                                   PARTITION BY doc_id
                                   ORDER BY update_time DESC, create_time DESC, id DESC
                               ) AS rn
                        FROM t_metadata_extraction_result
                        WHERE tenant_id = ?
                          AND (? = '' OR kb_id = ?)
                          AND (? IS NULL OR schema_version = ?)
                          AND (? = '' OR extractor_version = ?)
                    ) latest
                    WHERE rn = 1
                    """, this::toExtractionSnapshot, tenantId, knowledgeBaseId, knowledgeBaseId,
                    schemaVersion, schemaVersion, extractorVersion, extractorVersion);
            if (blank(llmPromptVersion)) {
                return snapshots;
            }
            return snapshots.stream()
                    .filter(snapshot -> llmPromptVersion.equals(snapshot.llmPromptVersion()))
                    .toList();
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private int countDocuments(String knowledgeBaseId) {
        try {
            return count("""
                    SELECT COUNT(1)
                    FROM t_knowledge_document
                    WHERE deleted = 0
                      AND (? = '' OR kb_id = ?)
                    """, knowledgeBaseId, knowledgeBaseId);
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    private List<ReviewSnapshot> reviewSnapshots(String tenantId,
                                                 String knowledgeBaseId,
                                                 Integer schemaVersion,
                                                 String extractorVersion,
                                                 String llmPromptVersion) {
        try {
            return jdbcTemplate.query("""
                    SELECT ri.id,
                           ri.doc_id,
                           ri.result_id,
                           ri.reason_code,
                           ri.review_status,
                           ri.suggested_metadata,
                           ri.corrected_metadata,
                           er.job_id,
                           er.schema_version,
                           er.extractor_version,
                           er.raw_candidates
                    FROM t_metadata_review_item ri
                    LEFT JOIN t_metadata_extraction_result er ON er.id = ri.result_id
                    WHERE ri.tenant_id = ?
                      AND (? = '' OR ri.kb_id = ?)
                    """, this::toReviewSnapshot, tenantId, knowledgeBaseId, knowledgeBaseId).stream()
                    .filter(snapshot -> matchesReviewScope(snapshot, schemaVersion, extractorVersion, llmPromptVersion))
                    .toList();
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private int pendingReviewCount(List<ReviewSnapshot> reviewSnapshots) {
        return (int) reviewSnapshots.stream()
                .filter(snapshot -> snapshot.reviewStatus() == MetadataReviewStatus.PENDING)
                .count();
    }

    private double reviewPassRate(List<ReviewSnapshot> reviewSnapshots) {
        int passed = 0;
        int completed = 0;
        for (ReviewSnapshot snapshot : reviewSnapshots) {
            if (snapshot.reviewStatus() == MetadataReviewStatus.APPROVED
                    || snapshot.reviewStatus() == MetadataReviewStatus.CORRECTED) {
                passed++;
                completed++;
                continue;
            }
            if (snapshot.reviewStatus() == MetadataReviewStatus.REJECTED
                    || snapshot.reviewStatus() == MetadataReviewStatus.QUARANTINED) {
                completed++;
            }
        }
        return ratio(passed, completed);
    }

    private double reviewCorrectionRate(List<ReviewSnapshot> reviewSnapshots) {
        int corrected = 0;
        int completed = 0;
        for (ReviewSnapshot snapshot : reviewSnapshots) {
            if (snapshot.reviewStatus() == MetadataReviewStatus.CORRECTED) {
                corrected++;
                completed++;
                continue;
            }
            if (snapshot.reviewStatus() == MetadataReviewStatus.APPROVED
                    || snapshot.reviewStatus() == MetadataReviewStatus.REJECTED
                    || snapshot.reviewStatus() == MetadataReviewStatus.QUARANTINED) {
                completed++;
            }
        }
        return ratio(corrected, completed);
    }

    private int countUnresolvedQuarantines(String tenantId, String knowledgeBaseId) {
        try {
            return count("""
                    SELECT COUNT(1)
                    FROM t_metadata_quarantine_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                      AND resolved = 0
                    """, tenantId, knowledgeBaseId, knowledgeBaseId);
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    private int countIndexSyncFailures(String tenantId, String knowledgeBaseId) {
        try {
            return count("""
                    SELECT COUNT(1)
                    FROM t_metadata_quarantine_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                      AND stage = 'INDEX'
                    """, tenantId, knowledgeBaseId, knowledgeBaseId);
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    private List<MetadataQuarantineReasonCount> quarantineReasonTopN(String tenantId,
                                                                     String knowledgeBaseId,
                                                                     int topN) {
        try {
            return jdbcTemplate.query("""
                    SELECT COALESCE(NULLIF(reason_code, ''), 'UNKNOWN') AS reason_code,
                           MAX(COALESCE(reason_message, '')) AS reason_message,
                           COUNT(1) AS reason_count
                    FROM t_metadata_quarantine_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                      AND resolved = 0
                    GROUP BY COALESCE(NULLIF(reason_code, ''), 'UNKNOWN')
                    ORDER BY reason_count DESC, reason_code ASC
                    LIMIT ?
                    """, (rs, rowNum) -> new MetadataQuarantineReasonCount(
                            rs.getString("reason_code"),
                            rs.getString("reason_message"),
                            rs.getInt("reason_count")),
                    tenantId, knowledgeBaseId, knowledgeBaseId, topN);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private ExtractionSnapshot toExtractionSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new ExtractionSnapshot(
                rs.getString("doc_id"),
                number(rs.getObject("schema_version"), 1),
                rs.getString("extractor_version"),
                jsonSupport.readMap(rs.getString("normalized_metadata")),
                jsonSupport.readMap(rs.getString("approved_metadata")),
                jsonSupport.readMapList(rs.getString("field_quality")),
                promptVersion(jsonSupport.readMapList(rs.getString("raw_candidates"))));
    }

    private ReviewSnapshot toReviewSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewSnapshot(
                rs.getString("id"),
                rs.getString("doc_id"),
                rs.getString("result_id"),
                rs.getString("job_id"),
                rs.getString("reason_code"),
                enumValue(MetadataReviewStatus.class, rs.getString("review_status"), MetadataReviewStatus.PENDING),
                jsonSupport.readMap(rs.getString("suggested_metadata")),
                jsonSupport.readMap(rs.getString("corrected_metadata")),
                nullableInteger(rs.getObject("schema_version")),
                rs.getString("extractor_version"),
                promptVersion(jsonSupport.readMapList(rs.getString("raw_candidates"))));
    }

    private boolean matchesReviewScope(ReviewSnapshot snapshot,
                                       Integer schemaVersion,
                                       String extractorVersion,
                                       String llmPromptVersion) {
        if (schemaVersion != null && !Objects.equals(schemaVersion, snapshot.schemaVersion())) {
            return false;
        }
        if (!blank(extractorVersion) && !extractorVersion.equals(snapshot.extractorVersion())) {
            return false;
        }
        return blank(llmPromptVersion) || llmPromptVersion.equals(snapshot.llmPromptVersion());
    }

    private int totalDocuments(String knowledgeBaseId,
                               List<ExtractionSnapshot> snapshots,
                               List<ReviewSnapshot> reviewSnapshots,
                               boolean versionScoped) {
        if (!versionScoped) {
            return Math.max(countDocuments(knowledgeBaseId), snapshots.size());
        }
        LinkedHashSet<String> documentIds = new LinkedHashSet<>();
        snapshots.stream().map(ExtractionSnapshot::documentId).filter(id -> !blank(id)).forEach(documentIds::add);
        reviewSnapshots.stream().map(ReviewSnapshot::documentId).filter(id -> !blank(id)).forEach(documentIds::add);
        return documentIds.size();
    }

    private boolean hasVersionScope(Integer schemaVersion, String extractorVersion, String llmPromptVersion) {
        return schemaVersion != null || !blank(extractorVersion) || !blank(llmPromptVersion);
    }

    private Map<String, ReviewFieldAggregate> reviewFieldAggregates(List<ReviewSnapshot> reviewSnapshots) {
        Map<String, MutableReviewFieldAggregate> aggregates = new LinkedHashMap<>();
        for (ReviewSnapshot snapshot : reviewSnapshots) {
            for (String fieldKey : feedbackFieldKeys(snapshot)) {
                if ("_document".equals(fieldKey)) {
                    continue;
                }
                MutableReviewFieldAggregate aggregate = aggregates.computeIfAbsent(
                        fieldKey, ignored -> new MutableReviewFieldAggregate());
                aggregate.reviewedDocuments.add(snapshot.documentId());
                if (snapshot.reviewStatus() == MetadataReviewStatus.CORRECTED
                        && fieldCorrected(snapshot, fieldKey)) {
                    aggregate.correctedDocuments.add(snapshot.documentId());
                }
            }
        }
        return aggregates.entrySet().stream()
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        entry -> entry.getValue().freeze(),
                        (left, right) -> left,
                        LinkedHashMap::new));
    }

    private boolean fieldCorrected(ReviewSnapshot snapshot, String fieldKey) {
        Object suggested = snapshot.suggestedMetadata().get(fieldKey);
        if (snapshot.correctedMetadata().containsKey(fieldKey)) {
            return !Objects.equals(suggested, snapshot.correctedMetadata().get(fieldKey));
        }
        return snapshot.suggestedMetadata().containsKey(fieldKey);
    }

    private List<MetadataReviewFeedbackSummary> reviewFeedbackSummaries(List<ReviewSnapshot> reviewSnapshots, int topN) {
        Map<FeedbackKey, MutableFeedbackAggregate> aggregates = new LinkedHashMap<>();
        for (ReviewSnapshot snapshot : reviewSnapshots) {
            for (String fieldKey : feedbackFieldKeys(snapshot)) {
                FeedbackKey key = new FeedbackKey(
                        fieldKey,
                        text(snapshot.reasonCode(), "UNKNOWN"),
                        snapshot.reviewStatus().name());
                MutableFeedbackAggregate aggregate = aggregates.computeIfAbsent(
                        key, ignored -> new MutableFeedbackAggregate());
                aggregate.reviewCount++;
                aggregate.documentIds.add(snapshot.documentId());
                addSampleId(aggregate.reviewItemIds, snapshot.reviewItemId());
                addSampleId(aggregate.resultIds, snapshot.resultId());
                addSampleId(aggregate.jobIds, snapshot.jobId());
            }
        }
        List<Map.Entry<FeedbackKey, MutableFeedbackAggregate>> topEntries = aggregates.entrySet().stream()
                .sorted((left, right) -> {
                    int countCompare = Integer.compare(right.getValue().reviewCount, left.getValue().reviewCount);
                    if (countCompare != 0) {
                        return countCompare;
                    }
                    int docCompare = Integer.compare(right.getValue().documentIds.size(), left.getValue().documentIds.size());
                    if (docCompare != 0) {
                        return docCompare;
                    }
                    int fieldCompare = left.getKey().fieldKey.compareTo(right.getKey().fieldKey);
                    if (fieldCompare != 0) {
                        return fieldCompare;
                    }
                    int reasonCompare = left.getKey().reasonCode.compareTo(right.getKey().reasonCode);
                    if (reasonCompare != 0) {
                        return reasonCompare;
                    }
                    return left.getKey().decisionAction.compareTo(right.getKey().decisionAction);
                })
                .limit(topN)
                .toList();
        Map<String, List<String>> auditIdsByReviewItem = reviewAuditIdsByReviewItems(topEntries.stream()
                .flatMap(entry -> entry.getValue().reviewItemIds.stream())
                .collect(Collectors.toCollection(LinkedHashSet::new)));
        return topEntries.stream()
                .map(entry -> new MetadataReviewFeedbackSummary(
                        entry.getKey().fieldKey,
                        entry.getKey().reasonCode,
                        entry.getKey().decisionAction,
                        entry.getValue().reviewCount,
                        entry.getValue().documentIds.size(),
                        List.copyOf(entry.getValue().reviewItemIds),
                        List.copyOf(entry.getValue().resultIds),
                        sampleAuditIds(entry.getValue().reviewItemIds, auditIdsByReviewItem),
                        List.copyOf(entry.getValue().jobIds)))
                .toList();
    }

    private Map<String, List<String>> reviewAuditIdsByReviewItems(Set<String> reviewItemIds) {
        if (reviewItemIds.isEmpty()) {
            return Map.of();
        }
        String placeholders = reviewItemIds.stream().map(ignored -> "?").collect(Collectors.joining(", "));
        try {
            List<Map.Entry<String, String>> rows = jdbcTemplate.query("""
                            SELECT review_item_id, id
                            FROM t_metadata_review_audit
                            WHERE review_item_id IN (%s)
                            ORDER BY create_time DESC, id DESC
                            """.formatted(placeholders),
                    (rs, rowNum) -> Map.entry(rs.getString("review_item_id"), rs.getString("id")),
                    reviewItemIds.toArray());
            Map<String, LinkedHashSet<String>> grouped = new LinkedHashMap<>();
            for (Map.Entry<String, String> row : rows) {
                if (blank(row.getKey()) || blank(row.getValue())) {
                    continue;
                }
                LinkedHashSet<String> auditIds = grouped.computeIfAbsent(row.getKey(), ignored -> new LinkedHashSet<>());
                if (auditIds.size() < 5) {
                    auditIds.add(row.getValue());
                }
            }
            return grouped.entrySet().stream()
                    .collect(Collectors.toMap(
                            Map.Entry::getKey,
                            entry -> List.copyOf(entry.getValue()),
                            (left, right) -> left,
                            LinkedHashMap::new));
        } catch (DataAccessException ex) {
            return Map.of();
        }
    }

    private List<String> sampleAuditIds(LinkedHashSet<String> reviewItemIds, Map<String, List<String>> auditIdsByReviewItem) {
        LinkedHashSet<String> sampleAuditIds = new LinkedHashSet<>();
        for (String reviewItemId : reviewItemIds) {
            for (String auditId : auditIdsByReviewItem.getOrDefault(reviewItemId, List.of())) {
                addSampleId(sampleAuditIds, auditId);
                if (sampleAuditIds.size() >= 5) {
                    return List.copyOf(sampleAuditIds);
                }
            }
        }
        return List.copyOf(sampleAuditIds);
    }

    private List<String> feedbackFieldKeys(ReviewSnapshot snapshot) {
        LinkedHashSet<String> keys = new LinkedHashSet<>();
        snapshot.suggestedMetadata().keySet().stream().filter(fieldKey -> !blank(fieldKey)).forEach(keys::add);
        snapshot.correctedMetadata().keySet().stream().filter(fieldKey -> !blank(fieldKey)).forEach(keys::add);
        if (keys.isEmpty()) {
            keys.add("_document");
        }
        return List.copyOf(keys);
    }

    private void addSampleId(LinkedHashSet<String> target, String value) {
        if (!blank(value) && target.size() < 5) {
            target.add(value);
        }
    }

    private String promptVersion(List<Map<String, Object>> rawCandidates) {
        for (Map<String, Object> candidate : rawCandidates) {
            String promptVersion = text(candidate.get("promptVersion"), "");
            if (!promptVersion.isBlank()) {
                return promptVersion;
            }
        }
        return "";
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private boolean hasValue(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof String text) {
            return !text.isBlank();
        }
        if (value instanceof java.util.Collection<?> collection) {
            return !collection.isEmpty();
        }
        return true;
    }

    private double averageCoverage(List<MetadataFieldCoverage> coverages) {
        return coverages.stream()
                .mapToDouble(MetadataFieldCoverage::coverageRate)
                .average()
                .orElse(0D);
    }

    private double ratio(int numerator, int denominator) {
        return denominator <= 0 ? 0D : (double) numerator / (double) denominator;
    }

    private String text(Object value, String defaultValue) {
        String text = Objects.toString(value, "");
        return text.isBlank() ? defaultValue : text;
    }

    private int number(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(Objects.toString(value, ""));
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private double doubleValue(Object value, double defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Objects.toString(value, ""));
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private Integer nullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        int number = number(value, 0);
        return number <= 0 ? null : number;
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private <E extends Enum<E>> E enumValue(Class<E> type, String value, E defaultValue) {
        if (blank(value)) {
            return defaultValue;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase());
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private record ExtractionSnapshot(
            String documentId,
            int schemaVersion,
            String extractorVersion,
            Map<String, Object> normalizedMetadata,
            Map<String, Object> acceptedMetadata,
            List<Map<String, Object>> fieldQualities,
            String llmPromptVersion
    ) {

        private ExtractionSnapshot {
            documentId = Objects.requireNonNullElse(documentId, "");
            schemaVersion = Math.max(1, schemaVersion);
            extractorVersion = Objects.requireNonNullElse(extractorVersion, "");
            normalizedMetadata = Map.copyOf(Objects.requireNonNullElse(normalizedMetadata, Map.of()));
            acceptedMetadata = Map.copyOf(Objects.requireNonNullElse(acceptedMetadata, Map.of()));
            fieldQualities = List.copyOf(Objects.requireNonNullElse(fieldQualities, List.of()));
            llmPromptVersion = Objects.requireNonNullElse(llmPromptVersion, "");
        }

        private Map<String, Object> coveredMetadata() {
            return acceptedMetadata.isEmpty() ? normalizedMetadata : acceptedMetadata;
        }
    }

    private record ReviewSnapshot(
            String reviewItemId,
            String documentId,
            String resultId,
            String jobId,
            String reasonCode,
            MetadataReviewStatus reviewStatus,
            Map<String, Object> suggestedMetadata,
            Map<String, Object> correctedMetadata,
            Integer schemaVersion,
            String extractorVersion,
            String llmPromptVersion
    ) {

        private ReviewSnapshot {
            reviewItemId = Objects.requireNonNullElse(reviewItemId, "");
            documentId = Objects.requireNonNullElse(documentId, "");
            resultId = Objects.requireNonNullElse(resultId, "");
            jobId = Objects.requireNonNullElse(jobId, "");
            reasonCode = Objects.requireNonNullElse(reasonCode, "");
            reviewStatus = Objects.requireNonNullElse(reviewStatus, MetadataReviewStatus.PENDING);
            suggestedMetadata = Map.copyOf(Objects.requireNonNullElse(suggestedMetadata, Map.of()));
            correctedMetadata = Map.copyOf(Objects.requireNonNullElse(correctedMetadata, Map.of()));
            extractorVersion = Objects.requireNonNullElse(extractorVersion, "");
            llmPromptVersion = Objects.requireNonNullElse(llmPromptVersion, "");
        }
    }

    private record ReviewFieldAggregate(int reviewedDocuments, int correctedDocuments) {

        private static ReviewFieldAggregate empty() {
            return new ReviewFieldAggregate(0, 0);
        }
    }

    private record LowConfidenceStats(int lowConfidenceFields, int evaluatedFields) {
    }

    private record FeedbackKey(String fieldKey, String reasonCode, String decisionAction) {
    }

    private static final class MutableReviewFieldAggregate {
        private final LinkedHashSet<String> reviewedDocuments = new LinkedHashSet<>();
        private final LinkedHashSet<String> correctedDocuments = new LinkedHashSet<>();

        private ReviewFieldAggregate freeze() {
            return new ReviewFieldAggregate(reviewedDocuments.size(), correctedDocuments.size());
        }
    }

    private static final class MutableFeedbackAggregate {
        private int reviewCount;
        private final LinkedHashSet<String> documentIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> reviewItemIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> resultIds = new LinkedHashSet<>();
        private final LinkedHashSet<String> jobIds = new LinkedHashSet<>();
    }
}

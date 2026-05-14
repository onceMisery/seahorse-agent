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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataFieldCoverage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineReasonCount;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineRetry;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class JdbcMetadataGovernanceRepositoryAdapter implements MetadataSchemaRegistryPort,
        MetadataDictionaryPort, MetadataExtractionResultRepositoryPort, MetadataReviewQueuePort,
        MetadataQuarantinePort, MetadataCanonicalWritePort, MetadataBackfillJobRepositoryPort,
        MetadataQualityReportRepositoryPort, MetadataReviewManagementRepositoryPort,
        MetadataQuarantineManagementRepositoryPort, MetadataSchemaManagementRepositoryPort {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<Map<String, Object>>> MAP_LIST_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private Boolean documentMetadataJsonColumnExists;
    private Boolean chunkMetadataJsonColumnExists;

    public JdbcMetadataGovernanceRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public MetadataSchema loadSchema(String tenantId, String knowledgeBaseId) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKbId = Objects.requireNonNullElse(knowledgeBaseId, "");
        try {
            List<MetadataFieldDescriptor> fields = jdbcTemplate.query("""
                    SELECT field_key, display_name, value_type, allowed_ops, required, filterable, sortable,
                           facetable, indexed, index_policy, min_confidence, trusted_sources, extraction_hints,
                           backend_mapping, schema_version
                    FROM t_metadata_field_schema
                    WHERE tenant_id = ?
                      AND (kb_id = ? OR kb_id IS NULL OR kb_id = '')
                      AND deleted = 0
                    ORDER BY CASE WHEN kb_id = ? THEN 0 ELSE 1 END, field_key
                    """, this::toFieldDescriptor, safeTenantId, safeKbId, safeKbId);
            int schemaVersion = fields.stream()
                    .mapToInt(field -> number(field.extractionHints().get("_schemaVersion"), 1))
                    .max()
                    .orElse(1);
            return new MetadataSchema(safeTenantId, safeKbId, schemaVersion, fields);
        } catch (DataAccessException ex) {
            return MetadataSchema.empty(safeTenantId, safeKbId);
        }
    }

    @Override
    public List<MetadataSchemaFieldRecord> listSchemaFields(String tenantId, String knowledgeBaseId) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKbId = Objects.requireNonNullElse(knowledgeBaseId, "");
        if (blank(safeTenantId)) {
            return List.of();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, field_key, display_name, value_type, allowed_ops,
                           required, filterable, sortable, facetable, indexed, index_policy,
                           min_confidence, trusted_sources, extraction_hints, backend_mapping,
                           schema_version, create_time, update_time
                    FROM t_metadata_field_schema
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ? OR kb_id IS NULL OR kb_id = '')
                      AND deleted = 0
                    ORDER BY CASE WHEN kb_id = ? THEN 0 ELSE 1 END, field_key
                    """, this::toSchemaFieldRecord, safeTenantId, safeKbId, safeKbId, safeKbId);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    @Override
    public Optional<MetadataSchemaFieldRecord> findSchemaField(String fieldId) {
        if (blank(fieldId)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, field_key, display_name, value_type, allowed_ops,
                           required, filterable, sortable, facetable, indexed, index_policy,
                           min_confidence, trusted_sources, extraction_hints, backend_mapping,
                           schema_version, create_time, update_time
                    FROM t_metadata_field_schema
                    WHERE id = ? AND deleted = 0
                    """, this::toSchemaFieldRecord, fieldId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public String createSchemaField(MetadataSchemaFieldPayload payload) {
        MetadataSchemaFieldPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String fieldId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO t_metadata_field_schema(
                    id, tenant_id, kb_id, field_key, display_name, value_type, allowed_ops,
                    required, filterable, sortable, facetable, indexed, index_policy,
                    min_confidence, trusted_sources, extraction_hints, backend_mapping,
                    schema_version, create_time, update_time, deleted
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, fieldId, safePayload.tenantId(), safePayload.knowledgeBaseId(), safePayload.fieldKey(),
                safePayload.displayName(), safePayload.valueType().name(), json(safePayload.allowedOperators()),
                flag(safePayload.required()), flag(safePayload.filterable()), flag(safePayload.sortable()),
                flag(safePayload.facetable()), flag(safePayload.indexed()), safePayload.indexPolicy().name(),
                safePayload.minConfidence(), json(safePayload.trustedSources()), json(safePayload.extractionHints()),
                json(safePayload.backendMapping()), safePayload.schemaVersion());
        return fieldId;
    }

    @Override
    public MetadataSchemaFieldRecord updateSchemaField(String fieldId, MetadataSchemaFieldPayload payload) {
        MetadataSchemaFieldPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE t_metadata_field_schema
                SET tenant_id = ?,
                    kb_id = ?,
                    field_key = ?,
                    display_name = ?,
                    value_type = ?,
                    allowed_ops = ?,
                    required = ?,
                    filterable = ?,
                    sortable = ?,
                    facetable = ?,
                    indexed = ?,
                    index_policy = ?,
                    min_confidence = ?,
                    trusted_sources = ?,
                    extraction_hints = ?,
                    backend_mapping = ?,
                    schema_version = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted = 0
                """, safePayload.tenantId(), safePayload.knowledgeBaseId(), safePayload.fieldKey(),
                safePayload.displayName(), safePayload.valueType().name(), json(safePayload.allowedOperators()),
                flag(safePayload.required()), flag(safePayload.filterable()), flag(safePayload.sortable()),
                flag(safePayload.facetable()), flag(safePayload.indexed()), safePayload.indexPolicy().name(),
                safePayload.minConfidence(), json(safePayload.trustedSources()), json(safePayload.extractionHints()),
                json(safePayload.backendMapping()), safePayload.schemaVersion(), fieldId);
        if (updated <= 0) {
            throw new IllegalArgumentException("Metadata Schema 字段不存在: " + fieldId);
        }
        return findSchemaField(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Metadata Schema 字段不存在: " + fieldId));
    }

    @Override
    public boolean deleteSchemaField(String fieldId) {
        if (blank(fieldId)) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE t_metadata_field_schema
                SET deleted = 1,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ? AND deleted = 0
                """, fieldId) > 0;
    }

    @Override
    public Optional<String> canonicalValue(String tenantId, String dictionaryCode, String rawValue) {
        if (blank(dictionaryCode) || rawValue == null) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT canonical_value
                    FROM t_metadata_dictionary_item
                    WHERE tenant_id = ? AND dict_code = ? AND raw_value = ? AND enabled = 1
                    ORDER BY update_time DESC
                    LIMIT 1
                    """, (rs, rowNum) -> rs.getString("canonical_value"),
                    Objects.requireNonNullElse(tenantId, ""), dictionaryCode, rawValue)
                    .stream()
                    .findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void save(MetadataExtractionRecord record) {
        saveAndReturnId(record);
    }

    @Override
    public String saveAndReturnId(MetadataExtractionRecord record) {
        MetadataExtractionRecord safeRecord = Objects.requireNonNull(record, "record must not be null");
        String resultId = UUID.randomUUID().toString();
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_extraction_result(
                        id, tenant_id, kb_id, doc_id, job_id, schema_version, extractor_version, status,
                        normalized_metadata, raw_candidates, field_quality, validation_issues, approved_metadata,
                        create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, resultId, safeRecord.tenantId(), safeRecord.knowledgeBaseId(),
                    safeRecord.documentId(), safeRecord.taskId(), safeRecord.schemaVersion(),
                    safeRecord.extractorVersion(), safeRecord.status().name(), json(safeRecord.normalizedMetadata()),
                    json(safeRecord.rawCandidates()), json(safeRecord.fieldQualities()), json(safeRecord.issues()),
                    json(safeRecord.acceptedMetadata()));
            return resultId;
        } catch (DataAccessException ignored) {
            return "";
        }
    }

    @Override
    public boolean hasAcceptedResult(String tenantId,
                                     String knowledgeBaseId,
                                     String documentId,
                                     int schemaVersion,
                                     String extractorVersion) {
        if (blank(documentId) || schemaVersion <= 0) {
            return false;
        }
        try {
            return count("""
                    SELECT COUNT(1)
                    FROM t_metadata_extraction_result
                    WHERE tenant_id = ?
                      AND kb_id = ?
                      AND doc_id = ?
                      AND schema_version = ?
                      AND COALESCE(extractor_version, '') = ?
                      AND status IN ('ACCEPT', 'ACCEPTED')
                    """, Objects.requireNonNullElse(tenantId, ""),
                    Objects.requireNonNullElse(knowledgeBaseId, ""),
                    documentId,
                    schemaVersion,
                    Objects.requireNonNullElse(extractorVersion, "")) > 0;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    @Override
    public void enqueue(MetadataReviewItem item) {
        MetadataReviewItem safeItem = Objects.requireNonNull(item, "item must not be null");
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_review_item(
                        id, tenant_id, kb_id, doc_id, result_id, review_status, priority, reason_code,
                        reason_message, suggested_metadata, review_context, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID().toString(), safeItem.tenantId(), safeItem.knowledgeBaseId(),
                    safeItem.documentId(), safeItem.resultId(), safeItem.reasonCode(), safeItem.reasonMessage(),
                    json(safeItem.suggestedMetadata()), json(safeItem.reviewContext()));
        } catch (DataAccessException ignored) {
        }
    }

    @Override
    public void quarantine(MetadataQuarantineItem item) {
        MetadataQuarantineItem safeItem = Objects.requireNonNull(item, "item must not be null");
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_quarantine_item(
                        id, tenant_id, kb_id, doc_id, job_id, stage, reason_code, reason_message,
                        source_snapshot, retry_count, resolved, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID().toString(), safeItem.tenantId(), safeItem.knowledgeBaseId(),
                    safeItem.documentId(), safeItem.taskId(), safeItem.stage(), safeItem.reasonCode(),
                    safeItem.reasonMessage(), json(safeItem.sourceSnapshot()));
        } catch (DataAccessException ignored) {
        }
    }

    @Override
    public MetadataReviewPage pageReviewItems(MetadataReviewQuery query) {
        MetadataReviewQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        SqlWhere where = reviewWhere(safeQuery);
        long total = countLong("SELECT COUNT(1) FROM t_metadata_review_item " + where.sql(), where.args());
        if (total <= 0) {
            return MetadataReviewPage.empty(safeQuery.current(), safeQuery.size());
        }
        List<Object> args = new ArrayList<>(where.args());
        args.add(safeQuery.size());
        args.add(safeQuery.offset());
        List<MetadataReviewRecord> records = jdbcTemplate.query("""
                SELECT id, tenant_id, kb_id, doc_id, result_id, review_status, priority,
                       reason_code, reason_message, suggested_metadata, review_context, corrected_metadata,
                       reviewer_id, review_comment, create_time, update_time
                FROM t_metadata_review_item
                """ + where.sql() + """
                ORDER BY priority DESC, update_time DESC, create_time DESC, id DESC
                LIMIT ? OFFSET ?
                """, this::toReviewRecord, args.toArray());
        return new MetadataReviewPage(records, total, safeQuery.size(), safeQuery.current(),
                pages(total, safeQuery.size()));
    }

    @Override
    public Optional<MetadataReviewRecord> findReviewItem(String itemId) {
        if (blank(itemId)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, doc_id, result_id, review_status, priority,
                           reason_code, reason_message, suggested_metadata, review_context, corrected_metadata,
                           reviewer_id, review_comment, create_time, update_time
                    FROM t_metadata_review_item
                    WHERE id = ?
                    """, this::toReviewRecord, itemId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public MetadataReviewRecord applyReviewDecision(MetadataReviewDecision decision) {
        MetadataReviewDecision safeDecision = Objects.requireNonNull(decision, "decision must not be null");
        MetadataReviewRecord current = findReviewItem(safeDecision.itemId())
                .orElseThrow(() -> new IllegalArgumentException("元数据复核项不存在: " + safeDecision.itemId()));
        Map<String, Object> approvedMetadata = approvedMetadata(current, safeDecision);
        String correctedJson = MetadataReviewStatus.CORRECTED.equals(safeDecision.reviewStatus())
                ? json(approvedMetadata)
                : null;
        int updated = jdbcTemplate.update("""
                UPDATE t_metadata_review_item
                SET review_status = ?,
                    corrected_metadata = ?,
                    reviewer_id = ?,
                    review_comment = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, safeDecision.reviewStatus().name(), correctedJson, safeDecision.reviewerId(),
                safeDecision.reviewComment(), safeDecision.itemId());
        if (updated <= 0) {
            throw new IllegalArgumentException("元数据复核项不存在: " + safeDecision.itemId());
        }
        insertReviewAudit(current, safeDecision, approvedMetadata);
        // 复核完成后同步抽取结果终态，避免管理端仍把已处理数据视为 REVIEW_REQUIRED。
        if (MetadataReviewStatus.APPROVED.equals(safeDecision.reviewStatus())
                || MetadataReviewStatus.CORRECTED.equals(safeDecision.reviewStatus())) {
            updateExtractionApproval(current.resultId(), approvedMetadata, safeDecision.reviewerId());
        } else if (MetadataReviewStatus.REJECTED.equals(safeDecision.reviewStatus())) {
            updateExtractionStatus(current.resultId(), "REJECTED");
        } else if (MetadataReviewStatus.QUARANTINED.equals(safeDecision.reviewStatus())) {
            updateExtractionStatus(current.resultId(), "QUARANTINED");
        } else if (MetadataReviewStatus.RE_EXTRACTING.equals(safeDecision.reviewStatus())) {
            updateExtractionStatus(current.resultId(), "RE_EXTRACTING");
        }
        return findReviewItem(safeDecision.itemId())
                .orElseThrow(() -> new IllegalArgumentException("元数据复核项不存在: " + safeDecision.itemId()));
    }

    private void insertReviewAudit(MetadataReviewRecord current,
                                   MetadataReviewDecision decision,
                                   Map<String, Object> decisionMetadata) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_review_audit(
                        id, review_item_id, tenant_id, kb_id, doc_id, result_id,
                        from_status, to_status, reviewer_id, review_comment,
                        decision_metadata, create_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    """,
                    UUID.randomUUID().toString(),
                    current.id(),
                    current.tenantId(),
                    current.knowledgeBaseId(),
                    current.documentId(),
                    current.resultId(),
                    current.reviewStatus().name(),
                    decision.reviewStatus().name(),
                    decision.reviewerId(),
                    decision.reviewComment(),
                    json(decisionMetadata));
        } catch (DataAccessException ignored) {
            // 兼容尚未执行审计表迁移的旧库，复核主流程不能因此中断。
        }
    }

    @Override
    public MetadataQuarantinePage pageQuarantineItems(MetadataQuarantineQuery query) {
        MetadataQuarantineQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        SqlWhere where = quarantineWhere(safeQuery);
        long total = countLong("SELECT COUNT(1) FROM t_metadata_quarantine_item " + where.sql(), where.args());
        if (total <= 0) {
            return MetadataQuarantinePage.empty(safeQuery.current(), safeQuery.size());
        }
        List<Object> args = new ArrayList<>(where.args());
        args.add(safeQuery.size());
        args.add(safeQuery.offset());
        List<MetadataQuarantineRecord> records = jdbcTemplate.query("""
                SELECT id, tenant_id, kb_id, doc_id, job_id, stage, reason_code, reason_message,
                       source_snapshot, retry_count, next_retry_time, resolved, resolved_by,
                       resolved_time, create_time, update_time
                FROM t_metadata_quarantine_item
                """ + where.sql() + """
                ORDER BY resolved ASC, update_time DESC, create_time DESC, id DESC
                LIMIT ? OFFSET ?
                """, this::toQuarantineRecord, args.toArray());
        return new MetadataQuarantinePage(records, total, safeQuery.size(), safeQuery.current(),
                pages(total, safeQuery.size()));
    }

    @Override
    public Optional<MetadataQuarantineRecord> findQuarantineItem(String itemId) {
        if (blank(itemId)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, doc_id, job_id, stage, reason_code, reason_message,
                           source_snapshot, retry_count, next_retry_time, resolved, resolved_by,
                           resolved_time, create_time, update_time
                    FROM t_metadata_quarantine_item
                    WHERE id = ?
                    """, this::toQuarantineRecord, itemId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public MetadataQuarantineRecord resolveQuarantineItem(MetadataQuarantineResolution resolution) {
        MetadataQuarantineResolution safeResolution = Objects.requireNonNull(resolution,
                "resolution must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE t_metadata_quarantine_item
                SET resolved = 1,
                    resolved_by = ?,
                    resolved_time = CURRENT_TIMESTAMP,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, safeResolution.operator(), safeResolution.itemId());
        if (updated <= 0) {
            throw new IllegalArgumentException("元数据隔离项不存在: " + safeResolution.itemId());
        }
        return findQuarantineItem(safeResolution.itemId())
                .orElseThrow(() -> new IllegalArgumentException("元数据隔离项不存在: " + safeResolution.itemId()));
    }

    @Override
    public MetadataQuarantineRecord scheduleQuarantineRetry(MetadataQuarantineRetry retry) {
        MetadataQuarantineRetry safeRetry = Objects.requireNonNull(retry, "retry must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE t_metadata_quarantine_item
                SET retry_count = retry_count + 1,
                    next_retry_time = ?,
                    resolved = 0,
                    resolved_by = NULL,
                    resolved_time = NULL,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, Timestamp.from(safeRetry.nextRetryTime()), safeRetry.itemId());
        if (updated <= 0) {
            throw new IllegalArgumentException("元数据隔离项不存在: " + safeRetry.itemId());
        }
        return findQuarantineItem(safeRetry.itemId())
                .orElseThrow(() -> new IllegalArgumentException("元数据隔离项不存在: " + safeRetry.itemId()));
    }

    @Override
    public void writeDocumentMetadata(String documentId, Map<String, Object> acceptedMetadata) {
        if (blank(documentId) || acceptedMetadata == null || acceptedMetadata.isEmpty()) {
            return;
        }
        if (!documentMetadataJsonColumnExists()) {
            return;
        }
        // 列存在后写入失败必须向上传递，避免 canonical metadata 静默丢失。
        jdbcTemplate.update("UPDATE t_knowledge_document SET metadata_json = ?, update_time = CURRENT_TIMESTAMP "
                + "WHERE id = ? AND deleted = 0", json(acceptedMetadata), documentId);
        writeChunkMetadata(documentId, acceptedMetadata);
    }

    private void writeChunkMetadata(String documentId, Map<String, Object> acceptedMetadata) {
        if (!chunkMetadataJsonColumnExists()) {
            return;
        }
        List<ChunkMetadataRow> rows = jdbcTemplate.query("""
                SELECT id, metadata_json
                FROM t_knowledge_chunk
                WHERE doc_id = ? AND deleted = 0
                """, (rs, rowNum) -> new ChunkMetadataRow(rs.getString("id"), rs.getString("metadata_json")),
                documentId);
        for (ChunkMetadataRow row : rows) {
            Map<String, Object> merged = new java.util.LinkedHashMap<>(readMap(row.metadataJson()));
            // 人工复核后的 canonical metadata 覆盖旧业务字段，但保留 chunk 原有的系统字段和来源快照。
            merged.putAll(acceptedMetadata);
            jdbcTemplate.update("""
                    UPDATE t_knowledge_chunk
                    SET metadata_json = ?,
                        update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, json(merged), row.id());
        }
    }

    @Override
    public String create(MetadataBackfillJobRecord job) {
        MetadataBackfillJobRecord safeJob = Objects.requireNonNull(job, "job must not be null");
        jdbcTemplate.update("""
                INSERT INTO t_metadata_extraction_job(
                    id, tenant_id, kb_id, pipeline_id, status, current_page, checkpoint_json, batch_size,
                    processed_count, success_count, failed_count, skipped_count, review_count,
                    quarantine_count, failure_summary, operator, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, safeJob.jobId(), safeJob.tenantId(), safeJob.knowledgeBaseId(), safeJob.pipelineId(),
                safeJob.status().name(), safeJob.currentPage(), json(safeJob.checkpoint()), safeJob.batchSize(),
                safeJob.processedDocuments(), safeJob.succeededDocuments(), safeJob.failedDocuments(),
                safeJob.skippedDocuments(), safeJob.reviewDocuments(), safeJob.quarantineDocuments(),
                json(safeJob.failures()), safeJob.operator(), Timestamp.from(safeJob.createTime()),
                Timestamp.from(safeJob.updateTime()));
        return safeJob.jobId();
    }

    @Override
    public Optional<MetadataBackfillJobRecord> findById(String jobId) {
        if (blank(jobId)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, pipeline_id, status, checkpoint_json, batch_size,
                           current_page,
                           processed_count, success_count, failed_count, skipped_count, review_count,
                           quarantine_count, failure_summary, operator, create_time, update_time
                    FROM t_metadata_extraction_job
                    WHERE id = ?
                    """, this::toBackfillJobRecord, jobId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public MetadataBackfillJobPage page(MetadataBackfillJobQuery query) {
        MetadataBackfillJobQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        SqlWhere where = backfillJobWhere(safeQuery);
        long total = countLong("SELECT COUNT(1) FROM t_metadata_extraction_job " + where.sql(), where.args());
        if (total <= 0) {
            return MetadataBackfillJobPage.empty(safeQuery.current(), safeQuery.size());
        }
        List<Object> args = new ArrayList<>(where.args());
        args.add(safeQuery.size());
        args.add(safeQuery.offset());
        List<MetadataBackfillJobRecord> records = jdbcTemplate.query("""
                SELECT id, tenant_id, kb_id, pipeline_id, status, checkpoint_json, batch_size,
                       current_page,
                       processed_count, success_count, failed_count, skipped_count, review_count,
                       quarantine_count, failure_summary, operator, create_time, update_time
                FROM t_metadata_extraction_job
                """ + where.sql() + """
                ORDER BY update_time DESC, create_time DESC, id DESC
                LIMIT ? OFFSET ?
                """, this::toBackfillJobRecord, args.toArray());
        return new MetadataBackfillJobPage(records, total, safeQuery.size(), safeQuery.current(),
                pages(total, safeQuery.size()));
    }

    @Override
    public void save(MetadataBackfillJobRecord job) {
        MetadataBackfillJobRecord safeJob = Objects.requireNonNull(job, "job must not be null");
        jdbcTemplate.update("""
                UPDATE t_metadata_extraction_job
                SET status = ?,
                    current_page = ?,
                    checkpoint_json = ?,
                    batch_size = ?,
                    processed_count = ?,
                    success_count = ?,
                    failed_count = ?,
                    skipped_count = ?,
                    review_count = ?,
                    quarantine_count = ?,
                    failure_summary = ?,
                    operator = ?,
                    update_time = ?
                WHERE id = ?
                """, safeJob.status().name(), safeJob.currentPage(), json(safeJob.checkpoint()), safeJob.batchSize(),
                safeJob.processedDocuments(), safeJob.succeededDocuments(), safeJob.failedDocuments(),
                safeJob.skippedDocuments(), safeJob.reviewDocuments(), safeJob.quarantineDocuments(),
                json(safeJob.failures()), safeJob.operator(), Timestamp.from(safeJob.updateTime()),
                safeJob.jobId());
    }

    @Override
    public MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKbId = Objects.requireNonNullElse(knowledgeBaseId, "");
        int safeTopN = Math.max(1, Math.min(quarantineTopN, 50));
        MetadataSchema schema = loadSchema(safeTenantId, safeKbId);
        List<ExtractionSnapshot> snapshots = latestExtractionSnapshots(safeTenantId, safeKbId);
        int totalDocuments = Math.max(countDocuments(safeKbId), snapshots.size());
        List<MetadataFieldCoverage> fieldCoverages = fieldCoverages(schema, snapshots, totalDocuments);
        LowConfidenceStats lowConfidenceStats = lowConfidenceStats(schema, snapshots);
        int pendingReviewCount = countPendingReviews(safeTenantId, safeKbId);
        int unresolvedQuarantineCount = countUnresolvedQuarantines(safeTenantId, safeKbId);
        int indexSyncFailureCount = countIndexSyncFailures(safeTenantId, safeKbId);
        List<MetadataQuarantineReasonCount> reasonTopN = quarantineReasonTopN(safeTenantId, safeKbId, safeTopN);
        return new MetadataQualityReport(
                safeTenantId,
                safeKbId,
                totalDocuments,
                snapshots.size(),
                averageCoverage(fieldCoverages),
                ratio(lowConfidenceStats.lowConfidenceFields(), lowConfidenceStats.evaluatedFields()),
                reviewPassRate(safeTenantId, safeKbId),
                pendingReviewCount,
                unresolvedQuarantineCount,
                indexSyncFailureCount,
                fieldCoverages,
                reasonTopN,
                Instant.now());
    }

    private List<MetadataFieldCoverage> fieldCoverages(MetadataSchema schema,
                                                       List<ExtractionSnapshot> snapshots,
                                                       int totalDocuments) {
        List<MetadataFieldCoverage> coverages = new ArrayList<>();
        // 覆盖率以 Schema 字段为基准，避免动态 metadata 任意扩张影响治理报表口径。
        for (MetadataFieldDescriptor field : schema.fields()) {
            int covered = 0;
            for (ExtractionSnapshot snapshot : snapshots) {
                if (hasValue(snapshot.coveredMetadata().get(field.fieldKey()))) {
                    covered++;
                }
            }
            coverages.add(new MetadataFieldCoverage(
                    field.fieldKey(),
                    field.displayName(),
                    field.required(),
                    covered,
                    totalDocuments,
                    ratio(covered, totalDocuments)));
        }
        return List.copyOf(coverages);
    }

    private LowConfidenceStats lowConfidenceStats(MetadataSchema schema, List<ExtractionSnapshot> snapshots) {
        Map<String, MetadataFieldDescriptor> fields = schema.fields().stream()
                .collect(Collectors.toMap(MetadataFieldDescriptor::fieldKey, field -> field, (left, right) -> left));
        int evaluated = 0;
        int lowConfidence = 0;
        for (ExtractionSnapshot snapshot : snapshots) {
            Map<String, Object> covered = snapshot.coveredMetadata();
            for (Map<String, Object> quality : snapshot.fieldQualities()) {
                String fieldKey = text(quality.get("fieldKey"), "");
                if (blank(fieldKey) || !hasValue(covered.get(fieldKey))) {
                    continue;
                }
                evaluated++;
                double confidence = doubleValue(quality.get("confidence"), 0D);
                MetadataFieldDescriptor field = fields.get(fieldKey);
                double minConfidence = field == null ? 0.8D : field.minConfidence();
                if (confidence < minConfidence) {
                    lowConfidence++;
                }
            }
        }
        return new LowConfidenceStats(lowConfidence, evaluated);
    }

    private List<ExtractionSnapshot> latestExtractionSnapshots(String tenantId, String knowledgeBaseId) {
        try {
            return jdbcTemplate.query("""
                    SELECT doc_id, normalized_metadata, approved_metadata, field_quality
                    FROM (
                        SELECT doc_id, normalized_metadata, approved_metadata, field_quality,
                               ROW_NUMBER() OVER (
                                   PARTITION BY doc_id
                                   ORDER BY update_time DESC, create_time DESC, id DESC
                               ) AS rn
                        FROM t_metadata_extraction_result
                        WHERE tenant_id = ?
                          AND (? = '' OR kb_id = ?)
                    ) latest
                    WHERE rn = 1
                    """, this::toExtractionSnapshot, tenantId, knowledgeBaseId, knowledgeBaseId);
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

    private int countPendingReviews(String tenantId, String knowledgeBaseId) {
        try {
            return count("""
                    SELECT COUNT(1)
                    FROM t_metadata_review_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                      AND review_status = 'PENDING'
                    """, tenantId, knowledgeBaseId, knowledgeBaseId);
        } catch (DataAccessException ex) {
            return 0;
        }
    }

    private double reviewPassRate(String tenantId, String knowledgeBaseId) {
        try {
            int passed = count("""
                    SELECT COUNT(1)
                    FROM t_metadata_review_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                      AND review_status IN ('APPROVED', 'CORRECTED')
                    """, tenantId, knowledgeBaseId, knowledgeBaseId);
            int completed = count("""
                    SELECT COUNT(1)
                    FROM t_metadata_review_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                      AND review_status IN ('APPROVED', 'CORRECTED', 'REJECTED', 'QUARANTINED')
                    """, tenantId, knowledgeBaseId, knowledgeBaseId);
            return ratio(passed, completed);
        } catch (DataAccessException ex) {
            return 0D;
        }
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
                readMap(rs.getString("normalized_metadata")),
                readMap(rs.getString("approved_metadata")),
                readMapList(rs.getString("field_quality")));
    }

    private MetadataReviewRecord toReviewRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataReviewRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("doc_id"),
                rs.getString("result_id"),
                enumValue(MetadataReviewStatus.class, rs.getString("review_status"), MetadataReviewStatus.PENDING),
                rs.getInt("priority"),
                rs.getString("reason_code"),
                rs.getString("reason_message"),
                readMap(rs.getString("suggested_metadata")),
                readMap(rs.getString("review_context")),
                readMap(rs.getString("corrected_metadata")),
                rs.getString("reviewer_id"),
                rs.getString("review_comment"),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private MetadataQuarantineRecord toQuarantineRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataQuarantineRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("doc_id"),
                rs.getString("job_id"),
                rs.getString("stage"),
                rs.getString("reason_code"),
                rs.getString("reason_message"),
                readMap(rs.getString("source_snapshot")),
                rs.getInt("retry_count"),
                nullableInstant(rs.getTimestamp("next_retry_time")),
                bool(rs, "resolved"),
                rs.getString("resolved_by"),
                nullableInstant(rs.getTimestamp("resolved_time")),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private SqlWhere reviewWhere(MetadataReviewQuery query) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (!blank(query.tenantId())) {
            sql.append(" AND tenant_id = ?");
            args.add(query.tenantId());
        }
        if (!blank(query.knowledgeBaseId())) {
            sql.append(" AND kb_id = ?");
            args.add(query.knowledgeBaseId());
        }
        if (query.reviewStatus() != null) {
            sql.append(" AND review_status = ?");
            args.add(query.reviewStatus().name());
        }
        return new SqlWhere(sql.toString(), args);
    }

    private SqlWhere quarantineWhere(MetadataQuarantineQuery query) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (!blank(query.tenantId())) {
            sql.append(" AND tenant_id = ?");
            args.add(query.tenantId());
        }
        if (!blank(query.knowledgeBaseId())) {
            sql.append(" AND kb_id = ?");
            args.add(query.knowledgeBaseId());
        }
        if (query.resolved() != null) {
            sql.append(" AND resolved = ?");
            args.add(Boolean.TRUE.equals(query.resolved()) ? 1 : 0);
        }
        return new SqlWhere(sql.toString(), args);
    }

    private SqlWhere backfillJobWhere(MetadataBackfillJobQuery query) {
        StringBuilder sql = new StringBuilder(" WHERE 1 = 1");
        List<Object> args = new ArrayList<>();
        if (!blank(query.tenantId())) {
            sql.append(" AND tenant_id = ?");
            args.add(query.tenantId());
        }
        if (!blank(query.knowledgeBaseId())) {
            sql.append(" AND kb_id = ?");
            args.add(query.knowledgeBaseId());
        }
        if (query.status() != null) {
            sql.append(" AND status = ?");
            args.add(query.status().name());
        }
        return new SqlWhere(sql.toString(), args);
    }

    private Map<String, Object> approvedMetadata(MetadataReviewRecord current, MetadataReviewDecision decision) {
        if (MetadataReviewStatus.APPROVED.equals(decision.reviewStatus())) {
            return current.suggestedMetadata();
        }
        if (MetadataReviewStatus.CORRECTED.equals(decision.reviewStatus())) {
            return decision.correctedMetadata();
        }
        return Map.of();
    }

    private void updateExtractionApproval(String resultId, Map<String, Object> approvedMetadata, String reviewerId) {
        if (blank(resultId) || approvedMetadata == null || approvedMetadata.isEmpty()) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    UPDATE t_metadata_extraction_result
                    SET status = 'ACCEPT',
                        approved_metadata = ?,
                        approved_by = ?,
                        approved_time = CURRENT_TIMESTAMP,
                        update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, json(approvedMetadata), reviewerId, resultId);
        } catch (DataAccessException ignored) {
        }
    }

    private void updateExtractionStatus(String resultId, String status) {
        if (blank(resultId) || blank(status)) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    UPDATE t_metadata_extraction_result
                    SET status = ?,
                        update_time = CURRENT_TIMESTAMP
                    WHERE id = ?
                    """, status, resultId);
        } catch (DataAccessException ignored) {
        }
    }

    private MetadataSchemaFieldRecord toSchemaFieldRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataSchemaFieldRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("field_key"),
                rs.getString("display_name"),
                enumValue(MetadataValueType.class, rs.getString("value_type"), MetadataValueType.STRING),
                operators(rs.getString("allowed_ops")),
                bool(rs, "required"),
                bool(rs, "filterable"),
                bool(rs, "sortable"),
                bool(rs, "facetable"),
                bool(rs, "indexed"),
                enumValue(MetadataIndexPolicy.class, rs.getString("index_policy"), MetadataIndexPolicy.NONE),
                rs.getDouble("min_confidence"),
                trustedSources(rs.getString("trusted_sources")),
                readMap(rs.getString("extraction_hints")),
                backendMapping(rs.getString("backend_mapping"), rs.getString("field_key")),
                rs.getInt("schema_version"),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private MetadataFieldDescriptor toFieldDescriptor(ResultSet rs, int rowNum) throws SQLException {
        int schemaVersion = rs.getInt("schema_version");
        Map<String, Object> hints = mutableMap(rs.getString("extraction_hints"));
        hints.put("_schemaVersion", schemaVersion);
        return new MetadataFieldDescriptor(
                rs.getString("field_key"),
                rs.getString("display_name"),
                enumValue(MetadataValueType.class, rs.getString("value_type"), MetadataValueType.STRING),
                operators(rs.getString("allowed_ops")),
                bool(rs, "required"),
                bool(rs, "filterable"),
                bool(rs, "sortable"),
                bool(rs, "facetable"),
                bool(rs, "indexed"),
                enumValue(MetadataIndexPolicy.class, rs.getString("index_policy"), MetadataIndexPolicy.NONE),
                rs.getDouble("min_confidence"),
                trustedSources(rs.getString("trusted_sources")),
                hints,
                backendMapping(rs.getString("backend_mapping"), rs.getString("field_key")));
    }

    private BackendFieldMapping backendMapping(String json, String fieldKey) {
        Map<String, Object> values = readMap(json);
        if (values.isEmpty()) {
            return BackendFieldMapping.defaults(fieldKey);
        }
        return new BackendFieldMapping(
                text(values.get("canonicalName"), fieldKey),
                text(values.get("milvusPath"), ""),
                text(values.get("pgJsonPath"), ""),
                text(values.get("searchFieldName"), fieldKey),
                bool(values.get("pushdownToVector")),
                bool(values.get("pushdownToKeyword")),
                bool(values.get("guardOnly")),
                values);
    }

    private MetadataBackfillJobRecord toBackfillJobRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataBackfillJobRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("pipeline_id"),
                enumValue(MetadataBackfillJobStatus.class, rs.getString("status"), MetadataBackfillJobStatus.PENDING),
                Math.max(1L, rs.getLong("current_page")),
                Math.max(1, rs.getInt("batch_size")),
                rs.getInt("processed_count"),
                rs.getInt("success_count"),
                rs.getInt("failed_count"),
                rs.getInt("skipped_count"),
                rs.getInt("review_count"),
                rs.getInt("quarantine_count"),
                readMap(rs.getString("checkpoint_json")),
                readList(rs.getString("failure_summary")),
                rs.getString("operator"),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private Set<MetadataOperator> operators(String json) {
        List<String> values = readList(json);
        if (values.isEmpty()) {
            return Set.of(MetadataOperator.EQ, MetadataOperator.IN);
        }
        return values.stream()
                .map(value -> enumValue(MetadataOperator.class, value, null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<String> trustedSources(String json) {
        return Set.copyOf(readList(json));
    }

    private List<String> readList(String json) {
        if (blank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, STRING_LIST);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> readMap(String json) {
        if (blank(json)) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, MAP_TYPE);
        } catch (Exception ex) {
            return Map.of();
        }
    }

    private List<Map<String, Object>> readMapList(String json) {
        if (blank(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, MAP_LIST_TYPE);
        } catch (Exception ex) {
            return List.of();
        }
    }

    private Map<String, Object> mutableMap(String json) {
        return new java.util.LinkedHashMap<>(readMap(json));
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(value, Map.of()));
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize metadata json failed", ex);
        }
    }

    private boolean documentMetadataJsonColumnExists() {
        if (documentMetadataJsonColumnExists != null) {
            return documentMetadataJsonColumnExists;
        }
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.columns
                    WHERE lower(table_name) = 't_knowledge_document'
                      AND lower(column_name) = 'metadata_json'
                    """, Integer.class);
            documentMetadataJsonColumnExists = count != null && count > 0;
        } catch (RuntimeException ex) {
            documentMetadataJsonColumnExists = false;
        }
        return documentMetadataJsonColumnExists;
    }

    private boolean chunkMetadataJsonColumnExists() {
        if (chunkMetadataJsonColumnExists != null) {
            return chunkMetadataJsonColumnExists;
        }
        try {
            Integer count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(1)
                    FROM information_schema.columns
                    WHERE lower(table_name) = 't_knowledge_chunk'
                      AND lower(column_name) = 'metadata_json'
                    """, Integer.class);
            chunkMetadataJsonColumnExists = count != null && count > 0;
        } catch (RuntimeException ex) {
            chunkMetadataJsonColumnExists = false;
        }
        return chunkMetadataJsonColumnExists;
    }

    private boolean bool(ResultSet rs, String column) throws SQLException {
        return rs.getInt(column) == 1;
    }

    private int flag(boolean value) {
        return value ? 1 : 0;
    }

    private boolean bool(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        return Boolean.parseBoolean(Objects.toString(value, "false"));
    }

    private int number(Object value, int defaultValue) {
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
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return Double.parseDouble(Objects.toString(value, ""));
        } catch (RuntimeException ex) {
            return defaultValue;
        }
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private long countLong(String sql, List<Object> args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args.toArray());
        return value == null ? 0L : value.longValue();
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

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? Instant.now() : timestamp.toInstant();
    }

    private Instant nullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private long pages(long total, long size) {
        return total <= 0L ? 0L : (total + Math.max(1L, size) - 1L) / Math.max(1L, size);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private record ExtractionSnapshot(
            String documentId,
            Map<String, Object> normalizedMetadata,
            Map<String, Object> acceptedMetadata,
            List<Map<String, Object>> fieldQualities
    ) {

        private ExtractionSnapshot {
            documentId = Objects.requireNonNullElse(documentId, "");
            normalizedMetadata = Map.copyOf(Objects.requireNonNullElse(normalizedMetadata, Map.of()));
            acceptedMetadata = Map.copyOf(Objects.requireNonNullElse(acceptedMetadata, Map.of()));
            fieldQualities = List.copyOf(Objects.requireNonNullElse(fieldQualities, List.of()));
        }

        private Map<String, Object> coveredMetadata() {
            // 人工复核修正后的 approved metadata 是更可信的 canonical 结果，质量报表应优先使用它。
            return acceptedMetadata.isEmpty() ? normalizedMetadata : acceptedMetadata;
        }
    }

    private record LowConfidenceStats(int lowConfidenceFields, int evaluatedFields) {
    }

    private record ChunkMetadataRow(String id, String metadataJson) {
    }

    private record SqlWhere(String sql, List<Object> args) {

        private SqlWhere {
            sql = Objects.requireNonNullElse(sql, "");
            args = List.copyOf(Objects.requireNonNullElse(args, List.of()));
        }
    }
}

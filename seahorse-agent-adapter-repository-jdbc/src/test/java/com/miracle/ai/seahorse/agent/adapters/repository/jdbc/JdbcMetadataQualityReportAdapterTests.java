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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldCandidate;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldQuality;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValidationDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataFieldCoverage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.data.Offset.offset;

class JdbcMetadataQualityReportAdapterTests {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private JdbcTemplate jdbcTemplate;
    private JdbcMetadataGovernanceRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:metadata-quality;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcMetadataGovernanceRepositoryAdapter(dataSource, objectMapper);
    }

    @Test
    void shouldBuildMetadataQualityReportFromGovernanceTables() {
        seedReportRows();

        MetadataQualityReport report = adapter.report("tenant-1", "kb-1", 1);

        assertThat(report.totalDocuments()).isEqualTo(3);
        assertThat(report.extractedDocuments()).isEqualTo(2);
        assertThat(report.schemaVersion()).isNull();
        assertThat(report.extractorVersion()).isBlank();
        assertThat(report.llmPromptVersion()).isBlank();
        assertThat(report.averageFieldCoverage()).isCloseTo(4D / 9D, offset(0.0001D));
        assertThat(report.lowConfidenceRatio()).isCloseTo(0.25D, offset(0.0001D));
        assertThat(report.reviewPassRate()).isCloseTo(2D / 3D, offset(0.0001D));
        assertThat(report.reviewCorrectionRate()).isCloseTo(1D / 3D, offset(0.0001D));
        assertThat(report.pendingReviewCount()).isEqualTo(2);
        assertThat(report.unresolvedQuarantineCount()).isEqualTo(3);
        assertThat(report.indexSyncFailureCount()).isEqualTo(1);
        assertThat(coverage(report, "department").coverageRate()).isCloseTo(2D / 3D, offset(0.0001D));
        assertThat(coverage(report, "department").lowConfidenceDocuments()).isEqualTo(1);
        assertThat(coverage(report, "department").lowConfidenceRate()).isCloseTo(1D / 2D, offset(0.0001D));
        assertThat(coverage(report, "department").reviewedDocuments()).isEqualTo(2);
        assertThat(coverage(report, "department").correctedDocuments()).isEqualTo(1);
        assertThat(coverage(report, "department").correctionRate()).isCloseTo(0.5D, offset(0.0001D));
        assertThat(coverage(report, "securityLevel").coverageRate()).isCloseTo(1D / 3D, offset(0.0001D));
        assertThat(coverage(report, "securityLevel").lowConfidenceRate()).isZero();
        assertThat(coverage(report, "owner").coverageRate()).isCloseTo(1D / 3D, offset(0.0001D));
        assertThat(coverage(report, "owner").lowConfidenceRate()).isZero();
        assertThat(report.reviewFeedbackSummaries()).isNotEmpty();
        assertThat(report.quarantineReasons()).hasSize(1);
        assertThat(report.quarantineReasons().get(0).reasonCode()).isEqualTo("SCHEMA_MISSING");
        assertThat(report.quarantineReasons().get(0).count()).isEqualTo(2);
    }

    @Test
    void shouldPreferApprovedMetadataWhenComputingCoverage() {
        jdbcTemplate.update("INSERT INTO t_knowledge_document(id, kb_id, deleted) VALUES ('doc-1', 'kb-1', 0)");
        insertExtraction("result-1", "doc-1",
                Map.of("department", "Sales"),
                Map.of("owner", "alice"),
                List.of(),
                Instant.parse("2026-05-13T08:00:00Z"));

        MetadataQualityReport report = adapter.report("tenant-1", "kb-1", 1);

        assertThat(report.extractedDocuments()).isEqualTo(1);
        assertThat(coverage(report, "department").coverageRate()).isZero();
        assertThat(coverage(report, "owner").coverageRate()).isEqualTo(1D);
    }

    @Test
    void shouldFilterQualityReportBySchemaAndExtractorVersion() {
        jdbcTemplate.update("INSERT INTO t_knowledge_document(id, kb_id, deleted) VALUES ('doc-1', 'kb-1', 0)");
        jdbcTemplate.update("INSERT INTO t_knowledge_document(id, kb_id, deleted) VALUES ('doc-2', 'kb-1', 0)");
        insertExtraction("result-v1", "doc-1", 1, "extractor-v1",
                Map.of("department", "Sales"),
                Map.of(),
                List.of(quality("department", 0.95D)),
                Instant.parse("2026-05-13T08:00:00Z"));
        insertExtraction("result-v2-doc1", "doc-1", 2, "extractor-v2",
                Map.of("department", "Finance", "owner", "alice"),
                Map.of(),
                List.of(quality("department", 0.91D), quality("owner", 0.9D)),
                Instant.parse("2026-05-13T09:00:00Z"));
        insertExtraction("result-v2-doc2", "doc-2", 2, "extractor-v2",
                Map.of("department", "Finance"),
                Map.of(),
                List.of(quality("department", 0.92D)),
                Instant.parse("2026-05-13T10:00:00Z"));

        MetadataQualityReport report = adapter.report("tenant-1", "kb-1", 1, 2, "extractor-v2");

        assertThat(report.schemaVersion()).isEqualTo(2);
        assertThat(report.extractorVersion()).isEqualTo("extractor-v2");
        assertThat(report.extractedDocuments()).isEqualTo(2);
        assertThat(coverage(report, "department").coverageRate()).isEqualTo(1D);
        assertThat(coverage(report, "owner").coverageRate()).isEqualTo(0.5D);
    }

    @Test
    void shouldFilterQualityReportByLlmPromptVersionAndAggregateReviewFeedback() {
        jdbcTemplate.update("INSERT INTO t_knowledge_document(id, kb_id, deleted) VALUES ('doc-1', 'kb-1', 0)");
        jdbcTemplate.update("INSERT INTO t_knowledge_document(id, kb_id, deleted) VALUES ('doc-2', 'kb-1', 0)");
        insertExtraction("result-p2-1", "doc-1", 2, "extractor-v2",
                Map.of("department", "Finance"),
                Map.of(),
                List.of(quality("department", 0.91D)),
                List.of(rawCandidate("department", "prompt-v2")),
                Instant.parse("2026-05-13T09:00:00Z"));
        insertExtraction("result-p3-2", "doc-2", 2, "extractor-v2",
                Map.of("department", "Legal"),
                Map.of(),
                List.of(quality("department", 0.93D)),
                List.of(rawCandidate("department", "prompt-v3")),
                Instant.parse("2026-05-13T10:00:00Z"));
        insertReviewRow("review-p2", "doc-1", "result-p2-1", "CORRECTED", "LOW_CONFIDENCE",
                Map.of("department", "Finance"), Map.of("department", "Legal"));
        insertReviewRow("review-p3", "doc-2", "result-p3-2", "APPROVED", "LOW_CONFIDENCE",
                Map.of("department", "Legal"), Map.of("department", "Legal"));
        insertReviewAudit("audit-p2", "review-p2", "doc-1", "result-p2-1");

        MetadataQualityReport report = adapter.report("tenant-1", "kb-1", 5, 2, "extractor-v2", "prompt-v2");

        assertThat(report.schemaVersion()).isEqualTo(2);
        assertThat(report.extractorVersion()).isEqualTo("extractor-v2");
        assertThat(report.llmPromptVersion()).isEqualTo("prompt-v2");
        assertThat(report.totalDocuments()).isEqualTo(1);
        assertThat(report.extractedDocuments()).isEqualTo(1);
        assertThat(report.reviewCorrectionRate()).isEqualTo(1D);
        assertThat(coverage(report, "department").reviewedDocuments()).isEqualTo(1);
        assertThat(coverage(report, "department").correctedDocuments()).isEqualTo(1);
        assertThat(report.reviewFeedbackSummaries()).singleElement().satisfies(summary -> {
            assertThat(summary.fieldKey()).isEqualTo("department");
            assertThat(summary.reasonCode()).isEqualTo("LOW_CONFIDENCE");
            assertThat(summary.decisionAction()).isEqualTo("CORRECTED");
            assertThat(summary.reviewCount()).isEqualTo(1);
            assertThat(summary.sampleReviewItemIds()).containsExactly("review-p2");
            assertThat(summary.sampleResultIds()).containsExactly("result-p2-1");
            assertThat(summary.sampleAuditIds()).containsExactly("audit-p2");
            assertThat(summary.sampleJobIds()).containsExactly("job-1");
        });
    }

    @Test
    void shouldPersistFieldQualitiesForLowConfidenceReport() {
        adapter.save(new MetadataExtractionRecord(
                "tenant-1",
                "kb-1",
                "doc-4",
                "job-1",
                1,
                "extractor-v1",
                MetadataValidationDecision.REVIEW_REQUIRED,
                Map.of("department", "Finance"),
                Map.of("department", "Finance"),
                List.of(new MetadataFieldQuality("department", 0.42D, "rule", "extractor", true, "低置信度")),
                List.of()));

        String qualityJson = jdbcTemplate.queryForObject(
                "SELECT field_quality FROM t_metadata_extraction_result WHERE doc_id = 'doc-4'",
                String.class);

        assertThat(qualityJson).contains("department");
        assertThat(qualityJson).contains("0.42");
    }

    @Test
    void shouldPersistRawCandidatesWithEvidence() {
        adapter.save(new MetadataExtractionRecord(
                "tenant-1",
                "kb-1",
                "doc-raw",
                "job-1",
                1,
                "extractor-v1",
                MetadataValidationDecision.ACCEPT,
                Map.of("department", "Finance"),
                Map.of("department", "Finance"),
                List.of(),
                List.of(),
                List.of(new MetadataFieldCandidate("department", "Finance", "source",
                        "SourceMetadataExtractor", 0.91D, "dept", 1, "extractor-v1"))));

        String rawCandidatesJson = jdbcTemplate.queryForObject(
                "SELECT raw_candidates FROM t_metadata_extraction_result WHERE doc_id = 'doc-raw'",
                String.class);

        assertThat(rawCandidatesJson).contains("department");
        assertThat(rawCandidatesJson).contains("SourceMetadataExtractor");
        assertThat(rawCandidatesJson).contains("dept");
    }

    @Test
    void shouldReturnExtractionResultIdWhenSavingRecord() {
        String resultId = adapter.saveAndReturnId(new MetadataExtractionRecord(
                "tenant-1",
                "kb-1",
                "doc-5",
                "job-1",
                1,
                "extractor-v1",
                MetadataValidationDecision.REVIEW_REQUIRED,
                Map.of("department", "Finance"),
                Map.of("department", "Finance"),
                List.of(),
                List.of()));

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_metadata_extraction_result WHERE id = ?",
                Integer.class,
                resultId);
        assertThat(resultId).isNotBlank();
        assertThat(count).isEqualTo(1);
    }

    @Test
    void shouldCheckAcceptedResultBySchemaAndExtractorVersion() {
        insertExtraction("accepted-1", "doc-1",
                Map.of("department", "Finance"),
                List.of(quality("department", 0.93D)),
                Instant.parse("2026-05-13T10:00:00Z"));
        jdbcTemplate.update("""
                INSERT INTO t_metadata_extraction_result(
                    id, tenant_id, kb_id, doc_id, job_id, schema_version, extractor_version, status,
                    normalized_metadata, raw_candidates, field_quality, validation_issues, approved_metadata,
                    create_time, update_time
                ) VALUES ('review-1', 'tenant-1', 'kb-1', 'doc-2', 'job-1', 1, 'extractor-v1', 'REVIEW_REQUIRED',
                          '{}', '[]', '[]', '[]', '{}', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """);

        assertThat(adapter.hasAcceptedResult("tenant-1", "kb-1", "doc-1", 1, "extractor-v1")).isTrue();
        assertThat(adapter.hasAcceptedResult("tenant-1", "kb-1", "doc-1", 2, "extractor-v1")).isFalse();
        assertThat(adapter.hasAcceptedResult("tenant-1", "kb-1", "doc-1", 1, "extractor-v2")).isFalse();
        assertThat(adapter.hasAcceptedResult("tenant-1", "kb-1", "doc-2", 1, "extractor-v1")).isFalse();
    }

    private MetadataFieldCoverage coverage(MetadataQualityReport report, String fieldKey) {
        return report.fieldCoverages().stream()
                .filter(coverage -> fieldKey.equals(coverage.fieldKey()))
                .findFirst()
                .orElseThrow();
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_quarantine_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_review_audit");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_review_item");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_extraction_result");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_metadata_field_schema");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_document");
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_document (
                    id VARCHAR(64) PRIMARY KEY,
                    kb_id VARCHAR(64),
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_field_schema (
                    id VARCHAR(32) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    field_key VARCHAR(128) NOT NULL,
                    display_name VARCHAR(128),
                    value_type VARCHAR(32) NOT NULL,
                    allowed_ops VARCHAR(512) NOT NULL,
                    required SMALLINT NOT NULL DEFAULT 0,
                    filterable SMALLINT NOT NULL DEFAULT 0,
                    sortable SMALLINT NOT NULL DEFAULT 0,
                    facetable SMALLINT NOT NULL DEFAULT 0,
                    indexed SMALLINT NOT NULL DEFAULT 0,
                    index_policy VARCHAR(32) NOT NULL DEFAULT 'NONE',
                    min_confidence DOUBLE NOT NULL DEFAULT 0.8,
                    trusted_sources VARCHAR(512),
                    extraction_hints VARCHAR(512),
                    backend_mapping VARCHAR(512),
                    schema_version INTEGER NOT NULL DEFAULT 1,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_extraction_result (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    doc_id VARCHAR(64) NOT NULL,
                    job_id VARCHAR(64),
                    schema_version INTEGER NOT NULL DEFAULT 1,
                    extractor_version VARCHAR(64),
                    status VARCHAR(32) NOT NULL,
                    normalized_metadata VARCHAR(4096),
                    raw_candidates VARCHAR(4096),
                    field_quality VARCHAR(4096),
                    validation_issues VARCHAR(4096),
                    approved_metadata VARCHAR(4096),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_review_item (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    doc_id VARCHAR(64),
                    result_id VARCHAR(64),
                    review_status VARCHAR(32) NOT NULL,
                    reason_code VARCHAR(64),
                    suggested_metadata VARCHAR(4096),
                    corrected_metadata VARCHAR(4096)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_review_audit (
                    id VARCHAR(64) PRIMARY KEY,
                    review_item_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    doc_id VARCHAR(64),
                    result_id VARCHAR(64),
                    from_status VARCHAR(32),
                    to_status VARCHAR(32),
                    reviewer_id VARCHAR(64),
                    review_comment VARCHAR(512),
                    previous_metadata VARCHAR(4096),
                    updated_metadata VARCHAR(4096),
                    decision_metadata VARCHAR(4096),
                    create_time TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_metadata_quarantine_item (
                    id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    kb_id VARCHAR(64),
                    stage VARCHAR(32),
                    reason_code VARCHAR(64),
                    reason_message VARCHAR(512),
                    resolved SMALLINT NOT NULL DEFAULT 0
                )
                """);
        seedSchemaRows();
    }

    private void seedSchemaRows() {
        insertField("field-1", "department", "部门", true, 0.8D);
        insertField("field-2", "owner", "负责人", false, 0.8D);
        insertField("field-3", "securityLevel", "密级", true, 0.9D);
    }

    private void seedReportRows() {
        jdbcTemplate.update("INSERT INTO t_knowledge_document(id, kb_id, deleted) VALUES ('doc-1', 'kb-1', 0)");
        jdbcTemplate.update("INSERT INTO t_knowledge_document(id, kb_id, deleted) VALUES ('doc-2', 'kb-1', 0)");
        jdbcTemplate.update("INSERT INTO t_knowledge_document(id, kb_id, deleted) VALUES ('doc-3', 'kb-1', 0)");
        insertExtraction("result-1", "doc-1",
                Map.of("department", "Sales", "securityLevel", "internal", "owner", "alice"),
                List.of(
                        quality("department", 0.95D),
                        quality("securityLevel", 0.92D),
                        quality("owner", 0.86D)),
                Instant.parse("2026-05-13T08:00:00Z"));
        insertExtraction("result-2-old", "doc-2",
                Map.of("department", "Sales", "owner", "bob"),
                List.of(quality("department", 0.9D), quality("owner", 0.9D)),
                Instant.parse("2026-05-13T07:00:00Z"));
        insertExtraction("result-2", "doc-2",
                Map.of("department", "Sales"),
                List.of(quality("department", 0.7D)),
                Instant.parse("2026-05-13T09:00:00Z"));
        insertReviewRow("review-1", "doc-2", "result-2", "PENDING", "LOW_CONFIDENCE",
                Map.of("department", "Sales"), Map.of());
        insertReviewRow("review-2", "doc-3", "", "PENDING", "MISSING_OWNER",
                Map.of("owner", ""), Map.of());
        insertReviewRow("review-3", "doc-1", "result-1", "APPROVED", "LOW_CONFIDENCE",
                Map.of("department", "Sales"), Map.of("department", "Sales"));
        insertReviewRow("review-4", "doc-2", "result-2", "CORRECTED", "LOW_CONFIDENCE",
                Map.of("department", "Sales"), Map.of("department", "Legal"));
        insertReviewRow("review-5", "doc-3", "", "REJECTED", "INVALID_SECURITY",
                Map.of("securityLevel", "secret"), Map.of());
        insertQuarantine("q-1", "VALIDATE", "SCHEMA_MISSING", "缺少 Schema", 0);
        insertQuarantine("q-2", "VALIDATE", "SCHEMA_MISSING", "缺少 Schema", 0);
        insertQuarantine("q-3", "PARSE", "PARSE_FAILED", "解析失败", 0);
        insertQuarantine("q-4", "VALIDATE", "SCHEMA_MISSING", "缺少 Schema", 1);
        insertQuarantine("q-5", "INDEX", "KEYWORD_INDEX_FAILED", "关键词索引失败", 1);
    }

    private void insertField(String id, String fieldKey, String displayName, boolean required, double minConfidence) {
        jdbcTemplate.update("""
                INSERT INTO t_metadata_field_schema(
                    id, tenant_id, kb_id, field_key, display_name, value_type, allowed_ops, required,
                    filterable, sortable, facetable, indexed, index_policy, min_confidence,
                    trusted_sources, extraction_hints, backend_mapping, schema_version, deleted
                ) VALUES (?, 'tenant-1', 'kb-1', ?, ?, 'STRING', '[\"EQ\"]', ?,
                          1, 0, 0, 1, 'JSON_GIN', ?, '[]', '{}', '{}', 1, 0)
                """, id, fieldKey, displayName, required ? 1 : 0, minConfidence);
    }

    private void insertExtraction(String id,
                                  String docId,
                                  Map<String, Object> metadata,
                                  List<Map<String, Object>> qualities,
                                  Instant updateTime) {
        insertExtraction(id, docId, 1, "extractor-v1", metadata, metadata, qualities, List.of(), updateTime);
    }

    private void insertExtraction(String id,
                                  String docId,
                                  Map<String, Object> metadata,
                                  Map<String, Object> approvedMetadata,
                                  List<Map<String, Object>> qualities,
                                  Instant updateTime) {
        insertExtraction(id, docId, 1, "extractor-v1", metadata, approvedMetadata, qualities, List.of(), updateTime);
    }

    private void insertExtraction(String id,
                                  String docId,
                                  int schemaVersion,
                                  String extractorVersion,
                                  Map<String, Object> metadata,
                                  Map<String, Object> approvedMetadata,
                                  List<Map<String, Object>> qualities,
                                  Instant updateTime) {
        insertExtraction(id, docId, schemaVersion, extractorVersion,
                metadata, approvedMetadata, qualities, List.of(), updateTime);
    }

    private void insertExtraction(String id,
                                  String docId,
                                  int schemaVersion,
                                  String extractorVersion,
                                  Map<String, Object> metadata,
                                  Map<String, Object> approvedMetadata,
                                  List<Map<String, Object>> qualities,
                                  List<Map<String, Object>> rawCandidates,
                                  Instant updateTime) {
        jdbcTemplate.update("""
                INSERT INTO t_metadata_extraction_result(
                    id, tenant_id, kb_id, doc_id, job_id, schema_version, extractor_version, status,
                    normalized_metadata, raw_candidates, field_quality, validation_issues, approved_metadata,
                    create_time, update_time
                ) VALUES (?, 'tenant-1', 'kb-1', ?, 'job-1', ?, ?, 'ACCEPT',
                          ?, ?, ?, '[]', ?, ?, ?)
                """, id, docId, schemaVersion, extractorVersion, json(metadata), json(rawCandidates), json(qualities), json(approvedMetadata),
                Timestamp.from(updateTime), Timestamp.from(updateTime));
    }

    private void insertReviewRow(String id,
                                 String docId,
                                 String resultId,
                                 String reviewStatus,
                                 String reasonCode,
                                 Map<String, Object> suggestedMetadata,
                                 Map<String, Object> correctedMetadata) {
        jdbcTemplate.update("""
                INSERT INTO t_metadata_review_item(
                    id, tenant_id, kb_id, doc_id, result_id, review_status, reason_code,
                    suggested_metadata, corrected_metadata
                ) VALUES (?, 'tenant-1', 'kb-1', ?, ?, ?, ?, ?, ?)
                """, id, docId, resultId, reviewStatus, reasonCode,
                json(suggestedMetadata), json(correctedMetadata));
    }

    private void insertQuarantine(String id, String stage, String reasonCode, String reasonMessage, int resolved) {
        jdbcTemplate.update("""
                INSERT INTO t_metadata_quarantine_item(id, tenant_id, kb_id, stage, reason_code, reason_message, resolved)
                VALUES (?, 'tenant-1', 'kb-1', ?, ?, ?, ?)
                """, id, stage, reasonCode, reasonMessage, resolved);
    }

    private void insertReviewAudit(String id, String reviewItemId, String docId, String resultId) {
        jdbcTemplate.update("""
                INSERT INTO t_metadata_review_audit(
                    id, review_item_id, tenant_id, kb_id, doc_id, result_id, from_status, to_status,
                    reviewer_id, review_comment, previous_metadata, updated_metadata, decision_metadata, create_time
                ) VALUES (?, ?, 'tenant-1', 'kb-1', ?, ?, 'PENDING', 'CORRECTED',
                          'auditor', 'ok', '{}', '{}', '{}', CURRENT_TIMESTAMP)
                """, id, reviewItemId, docId, resultId);
    }

    private Map<String, Object> quality(String fieldKey, double confidence) {
        return Map.of(
                "fieldKey", fieldKey,
                "confidence", confidence,
                "sourceType", "rule",
                "extractorName", "extractor",
                "normalized", true);
    }

    private Map<String, Object> rawCandidate(String fieldKey, String promptVersion) {
        return Map.of(
                "fieldKey", fieldKey,
                "promptVersion", promptVersion,
                "extractorVersion", "extractor-v2");
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException(ex);
        }
    }
}

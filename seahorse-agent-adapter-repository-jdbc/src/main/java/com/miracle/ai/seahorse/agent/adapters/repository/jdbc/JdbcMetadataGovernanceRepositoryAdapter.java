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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

public class JdbcMetadataGovernanceRepositoryAdapter implements MetadataSchemaRegistryPort,
        MetadataDictionaryPort, MetadataExtractionResultRepositoryPort, MetadataReviewQueuePort,
        MetadataQuarantinePort, MetadataCanonicalWritePort, MetadataBackfillJobRepositoryPort {

    private static final TypeReference<List<String>> STRING_LIST = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

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
        MetadataExtractionRecord safeRecord = Objects.requireNonNull(record, "record must not be null");
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_extraction_result(
                        id, tenant_id, kb_id, doc_id, job_id, schema_version, extractor_version, status,
                        normalized_metadata, raw_candidates, field_quality, validation_issues, approved_metadata,
                        create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID().toString(), safeRecord.tenantId(), safeRecord.knowledgeBaseId(),
                    safeRecord.documentId(), safeRecord.taskId(), safeRecord.schemaVersion(),
                    safeRecord.extractorVersion(), safeRecord.status().name(), json(safeRecord.normalizedMetadata()),
                    json(Map.of()), json(Map.of()), json(safeRecord.issues()), json(safeRecord.acceptedMetadata()));
        } catch (DataAccessException ignored) {
        }
    }

    @Override
    public void enqueue(MetadataReviewItem item) {
        MetadataReviewItem safeItem = Objects.requireNonNull(item, "item must not be null");
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_review_item(
                        id, tenant_id, kb_id, doc_id, result_id, review_status, priority, reason_code,
                        reason_message, suggested_metadata, create_time, update_time
                    ) VALUES (?, ?, ?, ?, ?, 'PENDING', 0, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, UUID.randomUUID().toString(), safeItem.tenantId(), safeItem.knowledgeBaseId(),
                    safeItem.documentId(), safeItem.resultId(), safeItem.reasonCode(), safeItem.reasonMessage(),
                    json(safeItem.suggestedMetadata()));
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
    public void writeDocumentMetadata(String documentId, Map<String, Object> acceptedMetadata) {
        if (blank(documentId) || acceptedMetadata == null || acceptedMetadata.isEmpty()) {
            return;
        }
        try {
            jdbcTemplate.update("UPDATE t_knowledge_document SET metadata_json = ?, update_time = CURRENT_TIMESTAMP "
                    + "WHERE id = ? AND deleted = 0", json(acceptedMetadata), documentId);
        } catch (DataAccessException ignored) {
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

    private boolean bool(ResultSet rs, String column) throws SQLException {
        return rs.getInt(column) == 1;
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

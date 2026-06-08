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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.BackendFieldMapping;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataFieldDescriptor;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataIndexPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataOperator;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataValueType;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldCapabilityRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncStatusRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
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

public class JdbcMetadataSchemaRepositoryAdapter implements MetadataSchemaRegistryPort,
        MetadataSchemaManagementRepositoryPort, MetadataSchemaIndexStatusPort {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMetadataJsonSupport jsonSupport;

    public JdbcMetadataSchemaRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.jsonSupport = new JdbcMetadataJsonSupport(objectMapper);
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
    public List<MetadataSchemaFieldCapabilityRecord> listSchemaFieldCapabilities(String tenantId,
                                                                                 String knowledgeBaseId) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKbId = Objects.requireNonNullElse(knowledgeBaseId, "");
        if (blank(safeTenantId)) {
            return List.of();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, field_key, display_name, value_type,
                           filterable, sortable, facetable, indexed, index_policy,
                           backend_mapping, schema_version, last_sync_backend, last_sync_action,
                           last_sync_outcome, last_sync_error_type, last_sync_error_message,
                           last_sync_time, update_time
                    FROM t_metadata_field_schema
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ? OR kb_id IS NULL OR kb_id = '')
                      AND deleted = 0
                    ORDER BY CASE WHEN kb_id = ? THEN 0 ELSE 1 END, field_key
                    """, this::toSchemaFieldCapabilityRecord, safeTenantId, safeKbId, safeKbId, safeKbId);
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
        String fieldId = SnowflakeIds.nextIdString();
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
            throw new IllegalArgumentException("Metadata Schema not found: " + fieldId);
        }
        return findSchemaField(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Metadata Schema not found: " + fieldId));
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
    public void recordSyncResult(MetadataSchemaIndexSyncStatusRecord status) {
        MetadataSchemaIndexSyncStatusRecord safeStatus = Objects.requireNonNull(status, "status must not be null");
        if (blank(safeStatus.fieldId())) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    UPDATE t_metadata_field_schema
                    SET last_sync_backend = ?,
                        last_sync_action = ?,
                        last_sync_outcome = ?,
                        last_sync_error_type = ?,
                        last_sync_error_message = ?,
                        last_sync_time = ?
                    WHERE id = ?
                    """,
                    trimToLength(safeStatus.backend(), 32),
                    trimToLength(safeStatus.action(), 32),
                    trimToLength(safeStatus.outcome(), 32),
                    trimToLength(safeStatus.errorType(), 64),
                    trimToLength(safeStatus.errorMessage(), 1024),
                    Timestamp.from(safeStatus.syncTime()),
                    safeStatus.fieldId());
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

    private MetadataSchemaFieldCapabilityRecord toSchemaFieldCapabilityRecord(ResultSet rs, int rowNum)
            throws SQLException {
        BackendFieldMapping mapping = backendMapping(rs.getString("backend_mapping"), rs.getString("field_key"));
        return new MetadataSchemaFieldCapabilityRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("field_key"),
                rs.getString("display_name"),
                enumValue(MetadataValueType.class, rs.getString("value_type"), MetadataValueType.STRING),
                bool(rs, "filterable"),
                bool(rs, "sortable"),
                bool(rs, "facetable"),
                bool(rs, "indexed"),
                enumValue(MetadataIndexPolicy.class, rs.getString("index_policy"), MetadataIndexPolicy.NONE),
                mapping.pushdownToKeyword(),
                mapping.pushdownToVector(),
                mapping.guardOnly(),
                rs.getInt("schema_version"),
                rs.getString("last_sync_backend"),
                rs.getString("last_sync_action"),
                rs.getString("last_sync_outcome"),
                rs.getString("last_sync_error_type"),
                rs.getString("last_sync_error_message"),
                nullableInstant(rs.getTimestamp("last_sync_time")),
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
        return jsonSupport.backendMapping(json, fieldKey);
    }

    private Set<MetadataOperator> operators(String json) {
        return jsonSupport.operators(json);
    }

    private Set<String> trustedSources(String json) {
        return jsonSupport.trustedSources(json);
    }

    private Map<String, Object> readMap(String json) {
        return jsonSupport.readMap(json);
    }

    private Map<String, Object> mutableMap(String json) {
        return jsonSupport.mutableMap(json);
    }

    private String json(Object value) {
        return jsonSupport.json(value);
    }

    private boolean bool(ResultSet rs, String column) throws SQLException {
        return rs.getInt(column) == 1;
    }

    private int flag(boolean value) {
        return value ? 1 : 0;
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

    private String trimToLength(String value, int maxLength) {
        String safeValue = Objects.requireNonNullElse(value, "");
        if (maxLength <= 0 || safeValue.length() <= maxLength) {
            return safeValue;
        }
        return safeValue.substring(0, maxLength);
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

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }
}

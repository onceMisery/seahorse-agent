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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillCountItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillOperationsOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryItemRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRecord;
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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewAuditRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewFeedbackSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldCapabilityRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncStatusRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageFieldRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReport;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
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
        MetadataQuarantineManagementRepositoryPort, MetadataSchemaManagementRepositoryPort,
        MetadataDictionaryManagementRepositoryPort, MetadataExtractionResultManagementRepositoryPort,
        MetadataSchemaIndexStatusPort, MetadataSchemaUsageReportRepositoryPort {

    private static final String SCHEMA_USAGE_EVENT_COMPILED = "COMPILED";
    private static final String SCHEMA_USAGE_EVENT_REJECTED = "REJECTED";

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMetadataJsonSupport jsonSupport;
    private final JdbcMetadataSchemaUsageSupport schemaUsageSupport;
    private final JdbcMetadataColumnDetector columnDetector;
    private final JdbcMetadataReviewSupport reviewSupport;
    private final JdbcMetadataQuarantineSupport quarantineSupport;

    public JdbcMetadataGovernanceRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        // 閹?JSON 閸楀繗顔呴妴涓糲hema Usage 缂佸嫬瀵橀崪灞藉灙閹恒垺绁撮幏鍡欑舶閸楀繋缍旈懓鍜冪礉閺€鑸垫殐娑撳鈧倿鍘ら崳銊ㄤ捍鐠愶絻鈧?
        this.jsonSupport = new JdbcMetadataJsonSupport(objectMapper);
        this.schemaUsageSupport = new JdbcMetadataSchemaUsageSupport();
        this.columnDetector = new JdbcMetadataColumnDetector(jdbcTemplate);
        this.reviewSupport = new JdbcMetadataReviewSupport(jdbcTemplate, jsonSupport);
        this.quarantineSupport = new JdbcMetadataQuarantineSupport(jdbcTemplate, jsonSupport);
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
            throw new IllegalArgumentException("Metadata Schema 鐎涙顔屾稉宥呯摠閸? " + fieldId);
        }
        return findSchemaField(fieldId)
                .orElseThrow(() -> new IllegalArgumentException("Metadata Schema 鐎涙顔屾稉宥呯摠閸? " + fieldId));
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
    public List<MetadataDictionaryItemRecord> listDictionaryItems(String tenantId,
                                                                  String dictionaryCode,
                                                                  boolean includeDisabled) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeDictionaryCode = Objects.requireNonNullElse(dictionaryCode, "");
        if (blank(safeTenantId)) {
            return List.of();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, dict_code, raw_value, canonical_value, display_name,
                           enabled, create_time, update_time
                    FROM t_metadata_dictionary_item
                    WHERE tenant_id = ?
                      AND (? = '' OR dict_code = ?)
                      AND (? = 1 OR enabled = 1)
                    ORDER BY dict_code, raw_value, update_time DESC
                    """, this::toDictionaryItemRecord,
                    safeTenantId, safeDictionaryCode, safeDictionaryCode, flag(includeDisabled));
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    @Override
    public Optional<MetadataDictionaryItemRecord> findDictionaryItem(String itemId) {
        if (blank(itemId)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, dict_code, raw_value, canonical_value, display_name,
                           enabled, create_time, update_time
                    FROM t_metadata_dictionary_item
                    WHERE id = ?
                    """, this::toDictionaryItemRecord, itemId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public String createDictionaryItem(MetadataDictionaryItemPayload payload) {
        MetadataDictionaryItemPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String itemId = UUID.randomUUID().toString();
        jdbcTemplate.update("""
                INSERT INTO t_metadata_dictionary_item(
                    id, tenant_id, dict_code, raw_value, canonical_value, display_name,
                    enabled, create_time, update_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                """, itemId, safePayload.tenantId(), safePayload.dictionaryCode(), safePayload.rawValue(),
                safePayload.canonicalValue(), safePayload.displayName(), flag(safePayload.enabled()));
        return itemId;
    }

    @Override
    public MetadataDictionaryItemRecord updateDictionaryItem(String itemId, MetadataDictionaryItemPayload payload) {
        MetadataDictionaryItemPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        int updated = jdbcTemplate.update("""
                UPDATE t_metadata_dictionary_item
                SET tenant_id = ?,
                    dict_code = ?,
                    raw_value = ?,
                    canonical_value = ?,
                    display_name = ?,
                    enabled = ?,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ?
                """, safePayload.tenantId(), safePayload.dictionaryCode(), safePayload.rawValue(),
                safePayload.canonicalValue(), safePayload.displayName(), flag(safePayload.enabled()), itemId);
        if (updated <= 0) {
            throw new IllegalArgumentException("Metadata Dictionary 鐎涙鍚€妞ら€涚瑝鐎涙ê婀? " + itemId);
        }
        return findDictionaryItem(itemId)
                .orElseThrow(() -> new IllegalArgumentException("Metadata Dictionary 鐎涙鍚€妞ら€涚瑝鐎涙ê婀? " + itemId));
    }

    @Override
    public boolean disableDictionaryItem(String itemId) {
        if (blank(itemId)) {
            return false;
        }
        return jdbcTemplate.update("""
                UPDATE t_metadata_dictionary_item
                SET enabled = 0,
                    update_time = CURRENT_TIMESTAMP
                WHERE id = ? AND enabled = 1
                """, itemId) > 0;
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
    public MetadataExtractionResultPage pageExtractionResults(MetadataExtractionResultQuery query) {
        MetadataExtractionResultQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        SqlWhere where = extractionResultWhere(safeQuery);
        long total = countLong("SELECT COUNT(1) FROM t_metadata_extraction_result" + where.sql(), where.args());
        if (total <= 0L) {
            return MetadataExtractionResultPage.empty(safeQuery.current(), safeQuery.size());
        }
        List<Object> args = new ArrayList<>(where.args());
        args.add(safeQuery.size());
        args.add(safeQuery.offset());
        List<MetadataExtractionResultRecord> records = jdbcTemplate.query("""
                SELECT id, tenant_id, kb_id, doc_id, job_id, schema_version, extractor_version, status,
                       normalized_metadata, raw_candidates, field_quality, validation_issues,
                       approved_metadata, approved_by, approved_time, create_time, update_time
                FROM t_metadata_extraction_result
                """.stripTrailing() + where.sql()
                + " ORDER BY update_time DESC, create_time DESC, id DESC LIMIT ? OFFSET ?",
                this::toExtractionResultRecord, args.toArray());
        return new MetadataExtractionResultPage(records, total, safeQuery.size(), safeQuery.current(),
                pages(total, safeQuery.size()));
    }

    @Override
    public Optional<MetadataExtractionResultRecord> findExtractionResult(String resultId) {
        if (blank(resultId)) {
            return Optional.empty();
        }
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, doc_id, job_id, schema_version, extractor_version, status,
                           normalized_metadata, raw_candidates, field_quality, validation_issues,
                           approved_metadata, approved_by, approved_time, create_time, update_time
                    FROM t_metadata_extraction_result
                    WHERE id = ?
                    """, this::toExtractionResultRecord, resultId).stream().findFirst();
        } catch (DataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void enqueue(MetadataReviewItem item) {
        reviewSupport.enqueue(item);
    }

    @Override
    public void quarantine(MetadataQuarantineItem item) {
        quarantineSupport.quarantine(item);
    }

    @Override
    public MetadataReviewPage pageReviewItems(MetadataReviewQuery query) {
        return reviewSupport.pageReviewItems(query);
    }

    @Override
    public Optional<MetadataReviewRecord> findReviewItem(String itemId) {
        return reviewSupport.findReviewItem(itemId);
    }

    @Override
    public List<MetadataReviewAuditRecord> listReviewAudits(String itemId) {
        return reviewSupport.listReviewAudits(itemId);
    }

    @Override
    public MetadataReviewRecord applyReviewDecision(MetadataReviewDecision decision) {
        if (reviewSupport != null) {
            JdbcMetadataReviewSupport.ReviewDecisionResult result = reviewSupport.applyReviewDecision(decision);
            // 优先走 review 子域协作者，主适配器只保留跨子域的抽取结果终态同步。
            if (MetadataReviewStatus.APPROVED.equals(result.reviewStatus())
                    || MetadataReviewStatus.CORRECTED.equals(result.reviewStatus())) {
                updateExtractionApproval(result.resultId(), result.approvedMetadata(), result.reviewerId());
            } else if (MetadataReviewStatus.REJECTED.equals(result.reviewStatus())) {
                updateExtractionStatus(result.resultId(), "REJECTED");
            } else if (MetadataReviewStatus.QUARANTINED.equals(result.reviewStatus())) {
                updateExtractionStatus(result.resultId(), "QUARANTINED");
            } else if (MetadataReviewStatus.RE_EXTRACTING.equals(result.reviewStatus())) {
                updateExtractionStatus(result.resultId(), "RE_EXTRACTING");
            }
            return reviewSupport.findReviewItem(result.itemId())
                    .orElseThrow(() -> new IllegalArgumentException("元数据复核项不存在: " + result.itemId()));
        }
        MetadataReviewDecision safeDecision = Objects.requireNonNull(decision, "decision must not be null");
        MetadataReviewRecord current = findReviewItem(safeDecision.itemId())
                .orElseThrow(() -> new IllegalArgumentException("閸忓啯鏆熼幑顔碱槻閺嶆悂銆嶆稉宥呯摠閸? " + safeDecision.itemId()));
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
            throw new IllegalArgumentException("閸忓啯鏆熼幑顔碱槻閺嶆悂銆嶆稉宥呯摠閸? " + safeDecision.itemId());
        }
        insertReviewAudit(current, safeDecision, decisionAuditMetadata(safeDecision, approvedMetadata));
        // 婢跺秵鐗崇€瑰本鍨氶崥搴℃倱濮濄儲濞婇崣鏍波閺嬫粎绮撻幀渚婄礉闁灝鍘ょ粻锛勬倞缁旑垯绮涢幎濠傚嚒婢跺嫮鎮婇弫鐗堝祦鐟欏棔璐?REVIEW_REQUIRED閵?
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
                .orElseThrow(() -> new IllegalArgumentException("閸忓啯鏆熼幑顔碱槻閺嶆悂銆嶆稉宥呯摠閸? " + safeDecision.itemId()));
    }

    private Map<String, Object> decisionAuditMetadata(MetadataReviewDecision decision,
                                                      Map<String, Object> approvedMetadata) {
        if (MetadataReviewStatus.RE_EXTRACTING.equals(decision.reviewStatus())) {
            // RE_EXTRACT 閻?correctedMetadata 閹佃儻娴囩拫鍐ㄥ娣団剝浼呴敍灞藉涧鏉╂稑鍙嗙€孤ゎ吀閿涘奔绗夐崘?approved_metadata閵?
            return decision.correctedMetadata();
        }
        return approvedMetadata;
    }

    private void insertReviewAudit(MetadataReviewRecord current,
                                   MetadataReviewDecision decision,
                                   Map<String, Object> decisionMetadata) {
        Map<String, Object> previousMetadata = previousAuditMetadata(current);
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_metadata_review_audit(
                        id, review_item_id, tenant_id, kb_id, doc_id, result_id,
                        from_status, to_status, reviewer_id, review_comment,
                        previous_metadata, updated_metadata, decision_metadata, create_time
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
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
                    json(previousMetadata),
                    json(decisionMetadata),
                    json(decisionMetadata));
        } catch (DataAccessException ex) {
            insertReviewAuditLegacy(current, decision, decisionMetadata);
        }
    }

    private Map<String, Object> previousAuditMetadata(MetadataReviewRecord current) {
        if (current.correctedMetadata() != null && !current.correctedMetadata().isEmpty()) {
            return current.correctedMetadata();
        }
        return current.suggestedMetadata();
    }

    private void insertReviewAuditLegacy(MetadataReviewRecord current,
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
            // 閸忕厧顔愮亸姘弓閹笛嗩攽鐎孤ゎ吀鐞涖劏绺肩粔鑽ゆ畱閺冄冪氨閿涘苯顦查弽闀愬瘜濞翠胶鈻兼稉宥堝厴閸ョ姵顒濇稉顓熸焽閵?
        }
    }

    @Override
    public MetadataQuarantinePage pageQuarantineItems(MetadataQuarantineQuery query) {
        if (quarantineSupport != null) {
            return quarantineSupport.pageQuarantineItems(query);
        }
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
        if (quarantineSupport != null) {
            return quarantineSupport.findQuarantineItem(itemId);
        }
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
        if (quarantineSupport != null) {
            return quarantineSupport.resolveQuarantineItem(resolution);
        }
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
            throw new IllegalArgumentException("閸忓啯鏆熼幑顕€娈х粋濠氥€嶆稉宥呯摠閸? " + safeResolution.itemId());
        }
        return findQuarantineItem(safeResolution.itemId())
                .orElseThrow(() -> new IllegalArgumentException("閸忓啯鏆熼幑顕€娈х粋濠氥€嶆稉宥呯摠閸? " + safeResolution.itemId()));
    }

    @Override
    public MetadataQuarantineRecord scheduleQuarantineRetry(MetadataQuarantineRetry retry) {
        if (quarantineSupport != null) {
            return quarantineSupport.scheduleQuarantineRetry(retry);
        }
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
            throw new IllegalArgumentException("閸忓啯鏆熼幑顕€娈х粋濠氥€嶆稉宥呯摠閸? " + safeRetry.itemId());
        }
        return findQuarantineItem(safeRetry.itemId())
                .orElseThrow(() -> new IllegalArgumentException("閸忓啯鏆熼幑顕€娈х粋濠氥€嶆稉宥呯摠閸? " + safeRetry.itemId()));
    }

    @Override
    public void writeDocumentMetadata(String documentId, Map<String, Object> acceptedMetadata) {
        if (blank(documentId) || acceptedMetadata == null || acceptedMetadata.isEmpty()) {
            return;
        }
        if (!documentMetadataJsonColumnExists()) {
            return;
        }
        // 閸掓鐡ㄩ崷銊ユ倵閸愭瑥鍙嗘径杈Е韫囧懘銆忛崥鎴滅瑐娴肩娀鈧帪绱濋柆鍨帳 canonical metadata 闂堟瑩绮稉銏犮亼閵?
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
            // 娴滃搫浼愭径宥嗙壋閸氬海娈?canonical metadata 鐟曞棛娲婇弮褌绗熼崝鈥崇摟濞堢绱濇担鍡曠箽閻?chunk 閸樼喐婀侀惃鍕兇缂佺喎鐡у▓闈涙嫲閺夈儲绨箛顐ゅ弾閵?
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
    public MetadataBackfillOperationsOverview overview(String tenantId, String knowledgeBaseId) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKbId = Objects.requireNonNullElse(knowledgeBaseId, "");
        if (blank(safeTenantId)) {
            return MetadataBackfillOperationsOverview.empty(safeTenantId, safeKbId);
        }
        List<MetadataBackfillJobRecord> jobs = listBackfillJobs(safeTenantId, safeKbId);
        long processedDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::processedDocuments).sum();
        long succeededDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::succeededDocuments).sum();
        long failedDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::failedDocuments).sum();
        long skippedDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::skippedDocuments).sum();
        long reviewDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::reviewDocuments).sum();
        long quarantineDocuments = jobs.stream().mapToLong(MetadataBackfillJobRecord::quarantineDocuments).sum();
        long pendingSchemaCompensationJobs = jobs.stream()
                .filter(this::isPendingSchemaCompensationJob)
                .count();
        long pendingSchemaCompensationDocuments = jobs.stream()
                .filter(this::isPendingSchemaCompensationJob)
                .mapToLong(job -> checkpointDocumentCount(job.checkpoint()))
                .sum();

        Map<String, Long> reviewStatusCounts = reviewStatusCounts(safeTenantId, safeKbId);
        Map<Boolean, Long> quarantineResolvedCounts = quarantineResolvedCounts(safeTenantId, safeKbId);
        return new MetadataBackfillOperationsOverview(
                safeTenantId,
                safeKbId,
                jobs.size(),
                processedDocuments,
                succeededDocuments,
                failedDocuments,
                skippedDocuments,
                reviewDocuments,
                quarantineDocuments,
                reviewStatusCounts.getOrDefault(MetadataReviewStatus.PENDING.name(), 0L),
                reviewStatusCounts.getOrDefault(MetadataReviewStatus.RE_EXTRACTING.name(), 0L),
                quarantineResolvedCounts.getOrDefault(Boolean.FALSE, 0L),
                quarantineResolvedCounts.getOrDefault(Boolean.TRUE, 0L),
                pendingSchemaCompensationJobs,
                pendingSchemaCompensationDocuments,
                statusCounts(jobs),
                failureReasonCounts(safeTenantId, safeKbId),
                pauseReasonCounts(jobs),
                latestMatchingJob(jobs, "reExtract"),
                latestMatchingJob(jobs, "schemaCompensation"),
                Instant.now());
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
    public void recordCompiled(String tenantId,
                               String knowledgeBaseId,
                               Integer schemaVersion,
                               List<String> fieldKeys,
                               List<String> guardOnlyFieldKeys) {
        recordSchemaUsage(tenantId, knowledgeBaseId, schemaVersion, fieldKeys, guardOnlyFieldKeys,
                SCHEMA_USAGE_EVENT_COMPILED, "");
    }

    @Override
    public void recordRejected(String tenantId,
                               String knowledgeBaseId,
                               Integer schemaVersion,
                               List<String> fieldKeys,
                               String rejectReason) {
        recordSchemaUsage(tenantId, knowledgeBaseId, schemaVersion, fieldKeys, List.of(),
                SCHEMA_USAGE_EVENT_REJECTED, rejectReason);
    }

    @Override
    public MetadataSchemaUsageReport report(String tenantId, String knowledgeBaseId, Integer schemaVersion) {
        String safeTenantId = Objects.requireNonNullElse(tenantId, "");
        String safeKbId = Objects.requireNonNullElse(knowledgeBaseId, "");
        Integer safeSchemaVersion = schemaVersion == null || schemaVersion <= 0 ? null : schemaVersion;
        if (blank(safeTenantId) || blank(safeKbId)) {
            return MetadataSchemaUsageReport.empty(safeTenantId, safeKbId, safeSchemaVersion);
        }
        try {
            SqlWhere where = schemaUsageWhere(safeTenantId, safeKbId, safeSchemaVersion);
            long totalCompiledRequests = countLong("""
                    SELECT COUNT(DISTINCT request_id)
                    FROM t_metadata_schema_usage_log
                    """ + where.sql() + """
                    AND event_type = 'COMPILED'
                    """, where.args());
            long totalRejectedRequests = countLong("""
                    SELECT COUNT(DISTINCT request_id)
                    FROM t_metadata_schema_usage_log
                    """ + where.sql() + """
                    AND event_type = 'REJECTED'
                    """, where.args());
            long guardOnlyRequestCount = countLong("""
                    SELECT COUNT(DISTINCT request_id)
                    FROM t_metadata_schema_usage_log
                    """ + where.sql() + """
                    AND event_type = 'COMPILED'
                    AND guard_only = 1
                    """, where.args());

            List<MetadataSchemaUsageAggregate> aggregates = jdbcTemplate.query("""
                    SELECT field_key,
                           SUM(CASE WHEN event_type = 'COMPILED' THEN 1 ELSE 0 END) AS usage_count,
                           SUM(CASE WHEN event_type = 'COMPILED' AND guard_only = 1 THEN 1 ELSE 0 END)
                               AS guard_only_count,
                           SUM(CASE WHEN event_type = 'REJECTED' THEN 1 ELSE 0 END) AS rejected_count
                    FROM t_metadata_schema_usage_log
                    """ + where.sql() + """
                    GROUP BY field_key
                    """, (rs, rowNum) -> new MetadataSchemaUsageAggregate(
                            rs.getString("field_key"),
                            rs.getLong("usage_count"),
                            rs.getLong("guard_only_count"),
                            rs.getLong("rejected_count")),
                    where.args().toArray());
            Map<String, MetadataSchemaUsageAggregate> aggregateByField = new LinkedHashMap<>();
            for (MetadataSchemaUsageAggregate aggregate : aggregates) {
                aggregateByField.put(aggregate.fieldKey(), aggregate);
            }

            LinkedHashMap<String, String> displayNames = new LinkedHashMap<>();
            for (MetadataSchemaFieldRecord field : listSchemaFields(safeTenantId, safeKbId)) {
                displayNames.putIfAbsent(field.fieldKey(), field.displayName());
            }
            for (String fieldKey : aggregateByField.keySet()) {
                displayNames.putIfAbsent(fieldKey, fieldKey);
            }

            List<MetadataSchemaUsageFieldRecord> fields = displayNames.entrySet().stream()
                    .map(entry -> toSchemaUsageFieldRecord(entry.getKey(), entry.getValue(),
                            aggregateByField.get(entry.getKey())))
                    .sorted(java.util.Comparator
                            .comparingLong(MetadataSchemaUsageFieldRecord::usageCount).reversed()
                            .thenComparingLong(MetadataSchemaUsageFieldRecord::rejectedCount).reversed()
                            .thenComparingLong(MetadataSchemaUsageFieldRecord::guardOnlyCount).reversed()
                            .thenComparing(MetadataSchemaUsageFieldRecord::fieldKey))
                    .toList();

            return new MetadataSchemaUsageReport(
                    safeTenantId,
                    safeKbId,
                    safeSchemaVersion,
                    totalCompiledRequests,
                    totalRejectedRequests,
                    guardOnlyRequestCount,
                    ratio(guardOnlyRequestCount, totalCompiledRequests),
                    ratio(totalRejectedRequests, totalCompiledRequests + totalRejectedRequests),
                    fields,
                    Instant.now());
        } catch (DataAccessException ex) {
            return MetadataSchemaUsageReport.empty(safeTenantId, safeKbId, safeSchemaVersion);
        }
    }

    @Override
    public MetadataQualityReport report(String tenantId, String knowledgeBaseId, int quarantineTopN) {
        return report(tenantId, knowledgeBaseId, quarantineTopN, null, "", "");
    }

    @Override
    public MetadataQualityReport report(String tenantId,
                                        String knowledgeBaseId,
                                        int quarantineTopN,
                                        Integer schemaVersion,
                                        String extractorVersion) {
        return report(tenantId, knowledgeBaseId, quarantineTopN, schemaVersion, extractorVersion, "");
    }

    @Override
    public MetadataQualityReport report(String tenantId,
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
        MetadataSchema schema = loadSchema(safeTenantId, safeKbId);
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
                Instant.now());
    }

    private List<MetadataFieldCoverage> fieldCoverages(MetadataSchema schema,
                                                       List<ExtractionSnapshot> snapshots,
                                                       int totalDocuments,
                                                       Map<String, ReviewFieldAggregate> reviewFieldAggregates) {
        List<MetadataFieldCoverage> coverages = new ArrayList<>();
        // 鐟曞棛娲婇悳鍥︿簰 Schema 鐎涙顔屾稉鍝勭唨閸戝棴绱濋柆鍨帳閸斻劍鈧?metadata 娴犵粯鍓伴幍鈺佺炊瑜板崬鎼峰▽鑽ゆ倞閹躲儴銆冮崣锝呯窞閵?
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
        // 娴ｅ海鐤嗘穱鈥冲閹稿鈧粌鐡у▓?+ 閺傚洦銆傞垾婵嗗箵闁插秶绮虹拋鈽呯礉闁灝鍘ら崥灞界摟濞堥潧顦块弶陇宸濋柌蹇氼唶瑜版洟鍣告径宥嗘杹婢堆勭槷娓氬鈧?
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
                    // promptVersion 鐎涙ê婀禍搴㈠▕閸欐牜绮ㄩ弸?raw_candidates 娑擃叏绱濋崶鐘愁劃鏉╂瑩鍣风紒鐔剁閸?Java 娓氀冧粵閺堚偓缂佸牏澧楅張顒冪箖濠娿們鈧?
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
                readMap(rs.getString("normalized_metadata")),
                readMap(rs.getString("approved_metadata")),
                readMapList(rs.getString("field_quality")),
                promptVersion(readMapList(rs.getString("raw_candidates"))));
    }

    private ReviewSnapshot toReviewSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new ReviewSnapshot(
                rs.getString("id"),
                rs.getString("doc_id"),
                rs.getString("result_id"),
                rs.getString("job_id"),
                rs.getString("reason_code"),
                enumValue(MetadataReviewStatus.class, rs.getString("review_status"), MetadataReviewStatus.PENDING),
                readMap(rs.getString("suggested_metadata")),
                readMap(rs.getString("corrected_metadata")),
                nullableInteger(rs.getObject("schema_version")),
                rs.getString("extractor_version"),
                promptVersion(readMapList(rs.getString("raw_candidates"))));
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

    private List<MetadataBackfillJobRecord> listBackfillJobs(String tenantId, String knowledgeBaseId) {
        try {
            return jdbcTemplate.query("""
                    SELECT id, tenant_id, kb_id, pipeline_id, status, checkpoint_json, batch_size,
                           current_page,
                           processed_count, success_count, failed_count, skipped_count, review_count,
                           quarantine_count, failure_summary, operator, create_time, update_time
                    FROM t_metadata_extraction_job
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                    ORDER BY update_time DESC, create_time DESC, id DESC
                    """, this::toBackfillJobRecord, tenantId, knowledgeBaseId, knowledgeBaseId);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private Map<String, Long> reviewStatusCounts(String tenantId, String knowledgeBaseId) {
        try {
            List<MetadataBackfillCountItem> counts = jdbcTemplate.query("""
                    SELECT review_status, COUNT(1) AS item_count
                    FROM t_metadata_review_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                    GROUP BY review_status
                    """, (rs, rowNum) -> new MetadataBackfillCountItem(
                            text(rs.getString("review_status"), ""),
                            rs.getLong("item_count")),
                    tenantId, knowledgeBaseId, knowledgeBaseId);
            Map<String, Long> indexed = new LinkedHashMap<>();
            for (MetadataBackfillCountItem count : counts) {
                indexed.put(count.key(), count.count());
            }
            return indexed;
        } catch (DataAccessException ex) {
            return Map.of();
        }
    }

    private Map<Boolean, Long> quarantineResolvedCounts(String tenantId, String knowledgeBaseId) {
        try {
            List<MetadataBackfillCountItem> counts = jdbcTemplate.query("""
                    SELECT resolved, COUNT(1) AS item_count
                    FROM t_metadata_quarantine_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                    GROUP BY resolved
                    """, (rs, rowNum) -> new MetadataBackfillCountItem(
                            rs.getInt("resolved") == 1 ? "true" : "false",
                            rs.getLong("item_count")),
                    tenantId, knowledgeBaseId, knowledgeBaseId);
            Map<Boolean, Long> indexed = new LinkedHashMap<>();
            for (MetadataBackfillCountItem count : counts) {
                indexed.put(Boolean.parseBoolean(count.key()), count.count());
            }
            return indexed;
        } catch (DataAccessException ex) {
            return Map.of();
        }
    }

    private List<MetadataBackfillCountItem> failureReasonCounts(String tenantId, String knowledgeBaseId) {
        try {
            return jdbcTemplate.query("""
                    SELECT COALESCE(NULLIF(reason_code, ''), 'UNKNOWN') AS reason_code,
                           COUNT(1) AS item_count
                    FROM t_metadata_quarantine_item
                    WHERE tenant_id = ?
                      AND (? = '' OR kb_id = ?)
                    GROUP BY COALESCE(NULLIF(reason_code, ''), 'UNKNOWN')
                    ORDER BY item_count DESC, reason_code ASC
                    """, (rs, rowNum) -> new MetadataBackfillCountItem(
                            rs.getString("reason_code"),
                            rs.getLong("item_count")),
                    tenantId, knowledgeBaseId, knowledgeBaseId);
        } catch (DataAccessException ex) {
            return List.of();
        }
    }

    private List<MetadataBackfillCountItem> statusCounts(List<MetadataBackfillJobRecord> jobs) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MetadataBackfillJobStatus status : MetadataBackfillJobStatus.values()) {
            counts.put(status.name(), 0L);
        }
        for (MetadataBackfillJobRecord job : jobs) {
            counts.computeIfPresent(job.status().name(), (key, value) -> value + 1L);
        }
        return counts.entrySet().stream()
                .map(entry -> new MetadataBackfillCountItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private List<MetadataBackfillCountItem> pauseReasonCounts(List<MetadataBackfillJobRecord> jobs) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MetadataBackfillJobRecord job : jobs) {
            String pauseReason = text(job.checkpoint().get("pauseReason"), "");
            if (!blank(pauseReason)) {
                counts.merge(pauseReason, 1L, Long::sum);
            }
        }
        return sortCountItems(counts);
    }

    private List<MetadataBackfillCountItem> sortCountItems(Map<String, Long> counts) {
        return counts.entrySet().stream()
                .sorted((left, right) -> {
                    int byCount = Long.compare(right.getValue(), left.getValue());
                    return byCount != 0 ? byCount : left.getKey().compareTo(right.getKey());
                })
                .map(entry -> new MetadataBackfillCountItem(entry.getKey(), entry.getValue()))
                .toList();
    }

    private MetadataBackfillJobRecord latestMatchingJob(List<MetadataBackfillJobRecord> jobs, String checkpointKey) {
        return jobs.stream()
                .filter(job -> bool(job.checkpoint().get(checkpointKey)))
                .findFirst()
                .orElse(null);
    }

    private boolean isPendingSchemaCompensationJob(MetadataBackfillJobRecord job) {
        return bool(job.checkpoint().get("schemaCompensation"))
                && job.status() != MetadataBackfillJobStatus.COMPLETED
                && job.status() != MetadataBackfillJobStatus.CANCELLED;
    }

    private long checkpointDocumentCount(Map<String, Object> checkpoint) {
        if (checkpoint == null || !checkpoint.containsKey("documentIds")) {
            return 0L;
        }
        Object value = checkpoint.get("documentIds");
        LinkedHashSet<String> documentIds = new LinkedHashSet<>();
        if (value instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                addCheckpointDocumentId(documentIds, item);
            }
            return documentIds.size();
        }
        // KB 缁狙喫夐崑鎸庣梾閺?documentIds 閺冩儼绻戦崶?0閿涘矂浼╅崗宥嗗Ω閺堫亞鐓￠懠鍐ㄦ纯娴碱亣顥婇幋鎰翱绾喖绶熸径鍕倞闁插繈鈧?
        String text = text(value, "");
        if (blank(text)) {
            return 0L;
        }
        for (String item : text.split(",")) {
            addCheckpointDocumentId(documentIds, item);
        }
        return documentIds.size();
    }

    private void addCheckpointDocumentId(Set<String> documentIds, Object value) {
        String text = text(value, "");
        if (!blank(text)) {
            documentIds.add(text.trim());
        }
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

    /**
     * 閹靛綊鍣烘０鍕絿鐎孤ゎ吀鏉炪劏鎶楅敍宀勪缉閸忓秴寮芥＃鍫熸喅鐟曚礁婀弰鐘茬殸闂冭埖顔岄柅鎰蒋閺屻儱绨遍妴?
     */
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

    private MetadataReviewAuditRecord toReviewAuditRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataReviewAuditRecord(
                rs.getString("id"),
                rs.getString("review_item_id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("doc_id"),
                rs.getString("result_id"),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("reviewer_id"),
                rs.getString("review_comment"),
                readMap(rs.getString("previous_metadata")),
                readMap(rs.getString("updated_metadata")),
                readMap(rs.getString("decision_metadata")),
                instant(rs.getTimestamp("create_time")));
    }

    private MetadataReviewAuditRecord toReviewAuditRecordLegacy(ResultSet rs, int rowNum) throws SQLException {
        Map<String, Object> decisionMetadata = readMap(rs.getString("decision_metadata"));
        return new MetadataReviewAuditRecord(
                rs.getString("id"),
                rs.getString("review_item_id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("doc_id"),
                rs.getString("result_id"),
                rs.getString("from_status"),
                rs.getString("to_status"),
                rs.getString("reviewer_id"),
                rs.getString("review_comment"),
                Map.of(),
                decisionMetadata,
                decisionMetadata,
                instant(rs.getTimestamp("create_time")));
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

    private SqlWhere extractionResultWhere(MetadataExtractionResultQuery query) {
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
        if (!blank(query.documentId())) {
            sql.append(" AND doc_id = ?");
            args.add(query.documentId());
        }
        if (!blank(query.jobId())) {
            sql.append(" AND job_id = ?");
            args.add(query.jobId());
        }
        if (!blank(query.status())) {
            sql.append(" AND status = ?");
            args.add(query.status());
        }
        if (query.schemaVersion() != null) {
            sql.append(" AND schema_version = ?");
            args.add(query.schemaVersion());
        }
        if (!blank(query.extractorVersion())) {
            sql.append(" AND COALESCE(extractor_version, '') = ?");
            args.add(query.extractorVersion());
        }
        return new SqlWhere(sql.toString(), args);
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
        if (!blank(query.reasonCode())) {
            sql.append(" AND reason_code = ?");
            args.add(query.reasonCode());
        }
        if (!blank(query.documentId())) {
            sql.append(" AND doc_id = ?");
            args.add(query.documentId());
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
        if (!blank(query.stage())) {
            sql.append(" AND stage = ?");
            args.add(query.stage());
        }
        if (!blank(query.reasonCode())) {
            sql.append(" AND reason_code = ?");
            args.add(query.reasonCode());
        }
        if (!blank(query.documentId())) {
            sql.append(" AND doc_id = ?");
            args.add(query.documentId());
        }
        if (!blank(query.jobId())) {
            sql.append(" AND job_id = ?");
            args.add(query.jobId());
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
        if (!blank(query.pipelineId())) {
            sql.append(" AND pipeline_id = ?");
            args.add(query.pipelineId());
        }
        if (!blank(query.operator())) {
            sql.append(" AND operator = ?");
            args.add(query.operator());
        }
        if (!blank(query.documentId())) {
            // 閸ョ偛锝炴禒璇插閹稿鏋冨锝呯暰娴ｅ秳绶风挧?checkpoint_json 娑擃厾娈?documentIds/lastDocumentId閿涘奔绗夐弬鏉款杻娴犺濮熼弰搴ｇ矎鐞涖劊鈧?
            appendJsonTextLike(sql, args, "checkpoint_json", query.documentId());
        }
        if (!blank(query.pauseReason())) {
            appendJsonTextLike(sql, args, "checkpoint_json", "\"pauseReason\"");
            appendJsonTextLike(sql, args, "checkpoint_json", query.pauseReason());
        }
        if (!blank(query.failureKeyword())) {
            appendJsonTextLike(sql, args, "failure_summary", query.failureKeyword());
        }
        if (Boolean.TRUE.equals(query.hasFailures())) {
            sql.append("""
                     AND (status = 'FAILED'
                          OR failed_count > 0
                          OR CAST(failure_summary AS VARCHAR) NOT IN ('', '[]', '{}', 'null'))
                    """);
        } else if (Boolean.FALSE.equals(query.hasFailures())) {
            sql.append("""
                     AND status <> 'FAILED'
                     AND failed_count = 0
                     AND (failure_summary IS NULL
                          OR CAST(failure_summary AS VARCHAR) IN ('', '[]', '{}', 'null'))
                    """);
        }
        if (Boolean.TRUE.equals(query.reExtract())) {
            appendJsonTextLike(sql, args, "checkpoint_json", "\"reExtract\"");
            appendJsonTextLike(sql, args, "checkpoint_json", "true");
        } else if (Boolean.FALSE.equals(query.reExtract())) {
            sql.append("""
                     AND (checkpoint_json IS NULL
                          OR CAST(checkpoint_json AS VARCHAR) NOT LIKE ?
                          OR CAST(checkpoint_json AS VARCHAR) LIKE ?)
                    """);
            args.add("%\"reExtract\"%");
            args.add("%false%");
        }
        return new SqlWhere(sql.toString(), args);
    }

    private void appendJsonTextLike(StringBuilder sql, List<Object> args, String column, String value) {
        if (blank(value)) {
            return;
        }
        sql.append(" AND CAST(").append(column).append(" AS VARCHAR) LIKE ?");
        args.add("%" + value.trim() + "%");
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

    private void recordSchemaUsage(String tenantId,
                                   String knowledgeBaseId,
                                   Integer schemaVersion,
                                   List<String> fieldKeys,
                                   List<String> guardOnlyFieldKeys,
                                   String eventType,
                                   String rejectReason) {
        JdbcMetadataSchemaUsageSupport.SchemaUsageBatch batch = schemaUsageSupport.buildBatch(
                tenantId, knowledgeBaseId, schemaVersion, fieldKeys, guardOnlyFieldKeys, eventType, rejectReason);
        if (batch.isEmpty()) {
            return;
        }
        jdbcTemplate.batchUpdate("""
                INSERT INTO t_metadata_schema_usage_log(
                    id, request_id, tenant_id, kb_id, schema_version, field_key,
                    event_type, guard_only, reject_reason, create_time
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, batch.args());
    }

    private SqlWhere schemaUsageWhere(String tenantId, String knowledgeBaseId, Integer schemaVersion) {
        StringBuilder sql = new StringBuilder("""
                WHERE tenant_id = ?
                  AND kb_id = ?
                """);
        List<Object> args = new ArrayList<>();
        args.add(tenantId);
        args.add(knowledgeBaseId);
        if (schemaVersion != null && schemaVersion > 0) {
            sql.append(" AND schema_version = ?");
            args.add(schemaVersion);
        }
        return new SqlWhere(sql.toString(), args);
    }

    private MetadataSchemaUsageFieldRecord toSchemaUsageFieldRecord(String fieldKey,
                                                                    String displayName,
                                                                    MetadataSchemaUsageAggregate aggregate) {
        MetadataSchemaUsageAggregate safeAggregate = aggregate == null
                ? new MetadataSchemaUsageAggregate(fieldKey, 0L, 0L, 0L)
                : aggregate;
        long usageCount = safeAggregate.usageCount();
        long rejectedCount = safeAggregate.rejectedCount();
        return new MetadataSchemaUsageFieldRecord(
                fieldKey,
                displayName,
                usageCount,
                safeAggregate.guardOnlyCount(),
                rejectedCount,
                ratio(safeAggregate.guardOnlyCount(), usageCount),
                ratio(rejectedCount, usageCount + rejectedCount));
    }

    private List<String> normalizedFieldKeys(List<String> fieldKeys) {
        return schemaUsageSupport.normalizedFieldKeys(fieldKeys);
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

    private MetadataDictionaryItemRecord toDictionaryItemRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataDictionaryItemRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("dict_code"),
                rs.getString("raw_value"),
                rs.getString("canonical_value"),
                rs.getString("display_name"),
                bool(rs, "enabled"),
                instant(rs.getTimestamp("create_time")),
                instant(rs.getTimestamp("update_time")));
    }

    private MetadataExtractionResultRecord toExtractionResultRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MetadataExtractionResultRecord(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("kb_id"),
                rs.getString("doc_id"),
                rs.getString("job_id"),
                rs.getInt("schema_version"),
                rs.getString("extractor_version"),
                rs.getString("status"),
                readMap(rs.getString("normalized_metadata")),
                readMapList(rs.getString("raw_candidates")),
                readMapList(rs.getString("field_quality")),
                readMapList(rs.getString("validation_issues")),
                readMap(rs.getString("approved_metadata")),
                rs.getString("approved_by"),
                nullableInstant(rs.getTimestamp("approved_time")),
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
        return jsonSupport.backendMapping(json, fieldKey);
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
        return jsonSupport.operators(json);
    }

    private Set<String> trustedSources(String json) {
        return jsonSupport.trustedSources(json);
    }

    private List<String> readList(String json) {
        return jsonSupport.readList(json);
    }

    private Map<String, Object> readMap(String json) {
        return jsonSupport.readMap(json);
    }

    private List<Map<String, Object>> readMapList(String json) {
        return jsonSupport.readMapList(json);
    }

    private Map<String, Object> mutableMap(String json) {
        return jsonSupport.mutableMap(json);
    }

    private String json(Object value) {
        return jsonSupport.json(value);
    }

    private boolean documentMetadataJsonColumnExists() {
        return columnDetector.hasDocumentMetadataJsonColumn();
    }

    private boolean chunkMetadataJsonColumnExists() {
        return columnDetector.hasChunkMetadataJsonColumn();
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

    private double ratio(long numerator, long denominator) {
        return denominator <= 0L ? 0D : (double) numerator / (double) denominator;
    }

    private String text(Object value, String defaultValue) {
        String text = Objects.toString(value, "");
        return text.isBlank() ? defaultValue : text;
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

    private Integer nullableInteger(Object value) {
        if (value == null) {
            return null;
        }
        int number = number(value, 0);
        return number <= 0 ? null : number;
    }

    private long pages(long total, long size) {
        return total <= 0L ? 0L : (total + Math.max(1L, size) - 1L) / Math.max(1L, size);
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
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
            // 娴滃搫浼愭径宥嗙壋娣囶喗顒滈崥搴ｆ畱 approved metadata 閺勵垱娲块崣顖欎繆閻?canonical 缂佹挻鐏夐敍宀冨窛闁插繑濮ょ悰銊ョ安娴兼ê鍘涙担璺ㄦ暏鐎瑰啨鈧?
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

    private record MetadataSchemaUsageAggregate(
            String fieldKey,
            long usageCount,
            long guardOnlyCount,
            long rejectedCount
    ) {

        private MetadataSchemaUsageAggregate {
            fieldKey = Objects.requireNonNullElse(fieldKey, "");
            usageCount = Math.max(0L, usageCount);
            guardOnlyCount = Math.max(0L, guardOnlyCount);
            rejectedCount = Math.max(0L, rejectedCount);
        }
    }

    private record ChunkMetadataRow(String id, String metadataJson) {
    }

    private record FeedbackKey(String fieldKey, String reasonCode, String decisionAction) {

        private FeedbackKey {
            fieldKey = Objects.requireNonNullElse(fieldKey, "");
            reasonCode = Objects.requireNonNullElse(reasonCode, "");
            decisionAction = Objects.requireNonNullElse(decisionAction, "");
        }
    }

    private record SqlWhere(String sql, List<Object> args) {

        private SqlWhere {
            sql = Objects.requireNonNullElse(sql, "");
            args = List.copyOf(Objects.requireNonNullElse(args, List.of()));
        }
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

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
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
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

public class JdbcMetadataExtractionResultRepositoryAdapter implements MetadataExtractionResultRepositoryPort,
        MetadataExtractionResultManagementRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final JdbcMetadataJsonSupport jsonSupport;

    public JdbcMetadataExtractionResultRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.jsonSupport = new JdbcMetadataJsonSupport(objectMapper);
    }

    @Override
    public void save(MetadataExtractionRecord record) {
        saveAndReturnId(record);
    }

    @Override
    public String saveAndReturnId(MetadataExtractionRecord record) {
        MetadataExtractionRecord safeRecord = Objects.requireNonNull(record, "record must not be null");
        String resultId = SnowflakeIds.nextIdString();
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
                                     Long knowledgeBaseId,
                                     Long documentId,
                                     int schemaVersion,
                                     String extractorVersion) {
        if (documentId == null || schemaVersion <= 0) {
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
                    knowledgeBaseId,
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

    void updateApproval(String resultId, Map<String, Object> approvedMetadata, String reviewerId) {
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

    void updateStatus(String resultId, String status) {
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

    private Map<String, Object> readMap(String json) {
        return jsonSupport.readMap(json);
    }

    private List<Map<String, Object>> readMapList(String json) {
        return jsonSupport.readMapList(json);
    }

    private String json(Object value) {
        return jsonSupport.json(value);
    }

    private int count(String sql, Object... args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args);
        return value == null ? 0 : value.intValue();
    }

    private long countLong(String sql, List<Object> args) {
        Number value = jdbcTemplate.queryForObject(sql, Number.class, args.toArray());
        return value == null ? 0L : value.longValue();
    }

    private long pages(long total, long size) {
        return total <= 0L ? 0L : (total + Math.max(1L, size) - 1L) / Math.max(1L, size);
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

    private record SqlWhere(String sql, List<Object> args) {

        private SqlWhere {
            sql = Objects.requireNonNullElse(sql, "");
            args = List.copyOf(Objects.requireNonNullElse(args, List.of()));
        }
    }
}

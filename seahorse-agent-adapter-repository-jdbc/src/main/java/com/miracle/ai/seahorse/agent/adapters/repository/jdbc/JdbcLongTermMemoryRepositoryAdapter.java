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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcLongTermMemoryRepositoryAdapter implements LongTermMemoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcLongTermMemoryRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<MemoryRecord> findById(String id) {
        return jdbcTemplate.query("""
                SELECT * FROM t_long_term_memory
                WHERE id = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'COMPACTED', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                """, this::mapRecord, toLongId(id)).stream().findFirst();
    }

    @Override
    public List<MemoryRecord> listByConversation(String conversationId, int limit) {
        return List.of();
    }

    @Override
    public List<MemoryRecord> listByUser(String userId, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM t_long_term_memory
                WHERE user_id = ?
                  AND deleted = 0
                  AND COALESCE(status, 'ACTIVE') NOT IN ('OBSOLETE', 'COMPACTED', 'ARCHIVED', 'DELETED', 'PHYSICAL_DELETED')
                ORDER BY importance_score DESC, create_time DESC
                LIMIT ?
                """, this::mapRecord, toLongId(userId), safeLimit(limit));
    }

    

    @Override
    public void save(MemoryRecord record) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO t_long_term_memory
                (id, user_id, memory_category, title, content, source_type, source_ids, tags,
                 importance_score, confidence_level, embedding_model, vector_ref_id,
                 tenant_id, status, generation_id, valid_from, valid_until, last_referenced_at,
                 schema_version, policy_version, sensitivity_level, obsolete_reason,
                 create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), CAST(? AS JSON), ?, ?, ?, ?,
                        ?, 'ACTIVE', ?, ?, NULL, NULL, ?, ?, ?, NULL, ?, ?, 0)
                """,
                JdbcMemorySupport.hasText(record.id()) ? JdbcMemorySupport.nextId() : JdbcMemorySupport.nextId(),
                toLongId(string(record.metadata().get("userId"))),
                record.type(),
                title(record.content()),
                record.content(),
                string(record.metadata().getOrDefault("sourceType", "short_term")),
                sourceIds(record),
                JdbcMemorySupport.writeJson(objectMapper, record.metadata()),
                number(record.metadata().get("importanceScore"), 0D),
                number(record.metadata().get("confidenceLevel"), 0D),
                string(record.metadata().get("embeddingModel")),
                string(record.metadata().get("vectorRefId")),
                tenantId(record.metadata()),
                string(record.metadata().get("generationId")),
                JdbcMemorySupport.timestamp(now),
                stringOrDefault(record.metadata().get("schemaVersion"), "1"),
                stringOrDefault(record.metadata().get("policyVersion"), "memory-governance-v1"),
                stringOrDefault(record.metadata().get("sensitivityLevel"), "LOW"),
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.timestamp(now));
    }

    @Override
    public boolean deleteById(String id) {
        return jdbcTemplate.update("UPDATE t_long_term_memory SET deleted = 1 WHERE id = ? AND deleted = 0", toLongId(id)) > 0;
    }

    private MemoryRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryRecord(
                rs.getString("id"),
                "long_term",
                rs.getString("memory_category"),
                rs.getString("content"),
                JdbcMemorySupport.metadata(objectMapper, rs.getString("tags"), metadata(rs)),
                JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
    }

    private Map<String, Object> metadata(ResultSet rs) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("userId", rs.getString("user_id"));
        values.put("title", rs.getString("title"));
        values.put("sourceType", rs.getString("source_type"));
        values.put("sourceIds", rs.getString("source_ids"));
        values.put("importanceScore", rs.getDouble("importance_score"));
        values.put("confidenceLevel", rs.getDouble("confidence_level"));
        values.put("embeddingModel", rs.getString("embedding_model"));
        values.put("vectorRefId", rs.getString("vector_ref_id"));
        values.put("tenantId", stringOrDefault(rs.getString("tenant_id"), "default"));
        values.put("status", stringOrDefault(rs.getString("status"), "ACTIVE"));
        values.put("generationId", rs.getString("generation_id"));
        values.put("lastReferencedAt", JdbcMemorySupport.instant(rs.getTimestamp("last_referenced_at")));
        values.put("schemaVersion", rs.getString("schema_version"));
        values.put("policyVersion", rs.getString("policy_version"));
        values.put("sensitivityLevel", rs.getString("sensitivity_level"));
        values.put("obsoleteReason", rs.getString("obsolete_reason"));
        return values;
    }

    private int safeLimit(int limit) {
        return limit <= 0 ? 20 : limit;
    }

    private String title(String content) {
        if (content == null) {
            return "";
        }
        return content.length() <= 64 ? content : content.substring(0, 64);
    }

    private String sourceIds(MemoryRecord record) {
        Object explicit = record.metadata().get("sourceIds");
        if (explicit != null) {
            return explicit.toString();
        }
        Object sourceMemoryId = record.metadata().get("sourceMemoryId");
        return sourceMemoryId == null ? "[]" : "[\"" + sourceMemoryId + "\"]";
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private String stringOrDefault(Object value, String fallback) {
        String text = string(value);
        return JdbcMemorySupport.hasText(text) ? text : fallback;
    }

    private String tenantId(Map<String, Object> metadata) {
        return stringOrDefault(metadata.get("tenantId"), "default");
    }

    private double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}

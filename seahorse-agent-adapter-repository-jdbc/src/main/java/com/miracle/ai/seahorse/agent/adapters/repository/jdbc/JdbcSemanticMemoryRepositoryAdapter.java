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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcSemanticMemoryRepositoryAdapter implements SemanticMemoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcSemanticMemoryRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<MemoryRecord> findById(String id) {
        return jdbcTemplate.query("""
                SELECT * FROM t_semantic_memory WHERE id = ? AND deleted = 0
                """, this::mapRecord, id).stream().findFirst();
    }

    @Override
    public List<MemoryRecord> listByConversation(String conversationId, int limit) {
        return List.of();
    }

    @Override
    public List<MemoryRecord> listByUser(String userId, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM t_semantic_memory
                WHERE user_id = ? AND deleted = 0
                ORDER BY update_time DESC
                LIMIT ?
                """, this::mapRecord, userId, safeLimit(limit));
    }

    @Override
    public void save(MemoryRecord record) {
        String userId = string(record.metadata().get("userId"));
        String semanticKey = string(record.metadata().get("semanticKey"));
        if (!JdbcMemorySupport.hasText(userId) || !JdbcMemorySupport.hasText(semanticKey)) {
            throw new IllegalArgumentException("semantic memory requires userId and semanticKey");
        }
        String existingId = findExistingId(userId, semanticKey, record.type());
        Instant now = Instant.now();
        if (existingId == null) {
            jdbcTemplate.update("""
                    INSERT INTO t_semantic_memory
                    (id, user_id, semantic_key, semantic_type, value_json, confidence_level,
                     source_memory_ids, create_time, update_time, deleted)
                    VALUES (?, ?, ?, ?, CAST(? AS JSON), ?, CAST(? AS JSON), ?, ?, 0)
                    """,
                    JdbcMemorySupport.hasText(record.id()) ? record.id() : JdbcMemorySupport.nextId(),
                    userId,
                    semanticKey,
                    record.type(),
                    JdbcMemorySupport.writeJson(objectMapper, Map.of(
                            "type", record.type(),
                            "content", record.content(),
                            "metadata", record.metadata())),
                    number(record.metadata().get("confidenceLevel"), 0D),
                    sourceIds(record),
                    JdbcMemorySupport.timestamp(now),
                    JdbcMemorySupport.timestamp(now));
            return;
        }
        jdbcTemplate.update("""
                UPDATE t_semantic_memory
                SET value_json = CAST(? AS JSON), confidence_level = GREATEST(confidence_level, ?),
                    source_memory_ids = CAST(? AS JSON), update_time = ?
                WHERE id = ? AND deleted = 0
                """,
                JdbcMemorySupport.writeJson(objectMapper, Map.of(
                        "type", record.type(),
                        "content", record.content(),
                        "metadata", record.metadata())),
                number(record.metadata().get("confidenceLevel"), 0D),
                sourceIds(record),
                JdbcMemorySupport.timestamp(now),
                existingId);
    }

    @Override
    public boolean deleteById(String id) {
        return jdbcTemplate.update("UPDATE t_semantic_memory SET deleted = 1 WHERE id = ? AND deleted = 0", id) > 0;
    }

    private MemoryRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryRecord(
                rs.getString("id"),
                "semantic",
                rs.getString("semantic_type"),
                rs.getString("value_json"),
                JdbcMemorySupport.metadata(objectMapper, rs.getString("value_json"), metadata(rs)),
                JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
    }

    private Map<String, Object> metadata(ResultSet rs) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("userId", rs.getString("user_id"));
        values.put("semanticKey", rs.getString("semantic_key"));
        values.put("confidenceLevel", rs.getDouble("confidence_level"));
        values.put("sourceMemoryIds", rs.getString("source_memory_ids"));
        return values;
    }

    private String findExistingId(String userId, String semanticKey, String semanticType) {
        try {
            return jdbcTemplate.queryForObject("""
                    SELECT id FROM t_semantic_memory
                    WHERE user_id = ? AND semantic_key = ? AND semantic_type = ? AND deleted = 0
                    LIMIT 1
                    """, String.class, userId, semanticKey, semanticType);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private int safeLimit(int limit) {
        return limit <= 0 ? 20 : limit;
    }

    private String sourceIds(MemoryRecord record) {
        Object explicit = record.metadata().get("sourceMemoryIds");
        if (explicit != null) {
            return explicit.toString();
        }
        Object sourceMemoryId = record.metadata().get("sourceMemoryId");
        return sourceMemoryId == null ? "[]" : "[\"" + sourceMemoryId + "\"]";
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}

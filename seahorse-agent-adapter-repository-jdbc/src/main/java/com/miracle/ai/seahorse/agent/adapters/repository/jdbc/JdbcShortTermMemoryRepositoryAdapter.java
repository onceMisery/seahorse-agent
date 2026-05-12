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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcShortTermMemoryRepositoryAdapter implements ShortTermMemoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcShortTermMemoryRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    @Override
    public Optional<MemoryRecord> findById(String id) {
        return jdbcTemplate.query("""
                SELECT * FROM t_short_term_memory WHERE id = ? AND deleted = 0
                """, this::mapRecord, id).stream().findFirst();
    }

    @Override
    public List<MemoryRecord> listByConversation(String conversationId, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM t_short_term_memory
                WHERE conversation_id = ? AND deleted = 0
                ORDER BY create_time DESC
                LIMIT ?
                """, this::mapRecord, conversationId, safeLimit(limit));
    }

    @Override
    public List<MemoryRecord> listByUser(String userId, int limit) {
        return jdbcTemplate.query("""
                SELECT * FROM t_short_term_memory
                WHERE user_id = ? AND deleted = 0
                ORDER BY importance_score DESC, create_time DESC
                LIMIT ?
                """, this::mapRecord, userId, safeLimit(limit));
    }

    @Override
    public void save(MemoryRecord record) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO t_short_term_memory
                (id, user_id, conversation_id, memory_type, content, metadata_json, source_message_ids,
                 importance_score, access_count, last_access_time, decay_score, expires_time,
                 create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?, ?, 0)
                """,
                JdbcMemorySupport.hasText(record.id()) ? record.id() : JdbcMemorySupport.nextId(),
                string(record.metadata().get("userId")),
                string(record.metadata().get("conversationId")),
                record.type(),
                record.content(),
                JdbcMemorySupport.writeJson(objectMapper, record.metadata()),
                string(record.metadata().getOrDefault("sourceMessageIds", "[]")),
                number(record.metadata().get("importanceScore"), 0D),
                JdbcMemorySupport.timestamp(now),
                number(record.metadata().get("decayScore"), 0D),
                JdbcMemorySupport.timestamp(now.plusSeconds(30L * 24 * 3600)),
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.timestamp(now));
    }

    @Override
    public boolean deleteById(String id) {
        return jdbcTemplate.update("UPDATE t_short_term_memory SET deleted = 1 WHERE id = ? AND deleted = 0", id) > 0;
    }

    private MemoryRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryRecord(
                rs.getString("id"),
                "short_term",
                rs.getString("memory_type"),
                rs.getString("content"),
                JdbcMemorySupport.metadata(objectMapper, rs.getString("metadata_json"), metadata(rs)),
                JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
    }

    private Map<String, Object> metadata(ResultSet rs) throws SQLException {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("userId", rs.getString("user_id"));
        values.put("conversationId", rs.getString("conversation_id"));
        values.put("sourceMessageIds", rs.getString("source_message_ids"));
        values.put("importanceScore", rs.getDouble("importance_score"));
        values.put("decayScore", rs.getDouble("decay_score"));
        return values;
    }

    private int safeLimit(int limit) {
        return limit <= 0 ? 20 : limit;
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private double number(Object value, double fallback) {
        return value instanceof Number number ? number.doubleValue() : fallback;
    }
}

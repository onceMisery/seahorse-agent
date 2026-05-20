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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class JdbcProfileMemoryRepositoryAdapter implements ProfileMemoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcProfileMemoryRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public Optional<ProfileFact> findActive(String userId, String tenantId, String slotKey) {
        if (!JdbcMemorySupport.hasText(userId) || !JdbcMemorySupport.hasText(slotKey)) {
            return Optional.empty();
        }
        return jdbcTemplate.query("""
                SELECT *
                FROM t_user_profile_fact
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND slot_key = ?
                  AND status = 'ACTIVE'
                  AND deleted = 0
                ORDER BY update_time DESC
                LIMIT 1
                """, this::mapFact, userId, defaultTenant(tenantId), slotKey).stream().findFirst();
    }

    @Override
    public List<ProfileFact> listActive(String userId, String tenantId, int limit) {
        if (!JdbcMemorySupport.hasText(userId)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT *
                FROM t_user_profile_fact
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND status = 'ACTIVE'
                  AND deleted = 0
                ORDER BY update_time DESC
                LIMIT ?
                """, this::mapFact, userId, defaultTenant(tenantId), safeLimit(limit));
    }

    @Override
    public void upsert(ProfileFactUpdate update) {
        Objects.requireNonNull(update, "update must not be null");
        if (!JdbcMemorySupport.hasText(update.userId())
                || !JdbcMemorySupport.hasText(update.slotKey())
                || !JdbcMemorySupport.hasText(update.valueText())) {
            throw new IllegalArgumentException("profile fact requires userId, slotKey and valueText");
        }
        String tenantId = defaultTenant(update.tenantId());
        String existingId = findActiveId(update.userId(), tenantId, update.slotKey());
        Instant now = Instant.now();
        if (existingId == null) {
            jdbcTemplate.update("""
                    INSERT INTO t_user_profile_fact
                    (id, user_id, tenant_id, slot_key, value_text, value_json, confidence_level, source_type,
                     source_ids, generation_id, status, valid_from, valid_until, create_time, update_time, deleted)
                    VALUES (?, ?, ?, ?, ?, CAST(? AS JSON), ?, ?, CAST(? AS JSON), ?, 'ACTIVE', ?, NULL, ?, ?, 0)
                    """,
                    JdbcMemorySupport.nextId(),
                    update.userId(),
                    tenantId,
                    update.slotKey(),
                    update.valueText(),
                    JdbcMemorySupport.writeJson(objectMapper, Map.of("value", update.valueText())),
                    update.confidenceLevel(),
                    update.sourceType(),
                    sourceIds(update.sourceIds()),
                    update.generationId(),
                    JdbcMemorySupport.timestamp(now),
                    JdbcMemorySupport.timestamp(now),
                    JdbcMemorySupport.timestamp(now));
            return;
        }
        jdbcTemplate.update("""
                UPDATE t_user_profile_fact
                SET value_text = ?,
                    value_json = CAST(? AS JSON),
                    confidence_level = ?,
                    source_type = ?,
                    source_ids = CAST(? AS JSON),
                    generation_id = ?,
                    valid_until = NULL,
                    update_time = ?
                WHERE id = ?
                  AND status = 'ACTIVE'
                  AND deleted = 0
                """,
                update.valueText(),
                JdbcMemorySupport.writeJson(objectMapper, Map.of("value", update.valueText())),
                update.confidenceLevel(),
                update.sourceType(),
                sourceIds(update.sourceIds()),
                update.generationId(),
                JdbcMemorySupport.timestamp(now),
                existingId);
    }

    private String findActiveId(String userId, String tenantId, String slotKey) {
        return jdbcTemplate.query("""
                SELECT id
                FROM t_user_profile_fact
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND slot_key = ?
                  AND status = 'ACTIVE'
                  AND deleted = 0
                ORDER BY update_time DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("id"), userId, tenantId, slotKey).stream().findFirst().orElse(null);
    }

    private ProfileFact mapFact(ResultSet rs, int rowNum) throws SQLException {
        return new ProfileFact(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("tenant_id"),
                rs.getString("slot_key"),
                rs.getString("value_text"),
                rs.getDouble("confidence_level"),
                rs.getString("source_type"),
                rs.getString("generation_id"),
                rs.getString("status"),
                JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
    }

    private String sourceIds(List<String> sourceIds) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(sourceIds, List.of()));
        } catch (Exception ex) {
            throw new IllegalArgumentException("profile source ids json serialization failed", ex);
        }
    }

    private String defaultTenant(String tenantId) {
        return JdbcMemorySupport.hasText(tenantId) ? tenantId : "default";
    }

    private int safeLimit(int limit) {
        return limit <= 0 ? 20 : limit;
    }
}

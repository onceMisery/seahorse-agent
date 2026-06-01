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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

public class JdbcCorrectionLedgerRepositoryAdapter implements CorrectionLedgerPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcCorrectionLedgerRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<CorrectionRule> listActive(String userId, String tenantId, int limit) {
        if (!JdbcMemorySupport.hasText(userId)) {
            return List.of();
        }
        return jdbcTemplate.query("""
                SELECT *
                FROM t_memory_correction_ledger
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND status = 'ACTIVE'
                  AND deleted = 0
                ORDER BY update_time DESC
                LIMIT ?
                """, this::mapRule, JdbcMemorySupport.toLongId(userId), defaultTenant(tenantId), safeLimit(limit));
    }

    @Override
    public void upsert(CorrectionCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        if (!JdbcMemorySupport.hasText(command.userId())
                || !JdbcMemorySupport.hasText(command.targetKind())
                || !JdbcMemorySupport.hasText(command.targetKey())
                || !JdbcMemorySupport.hasText(command.ruleText())) {
            throw new IllegalArgumentException("correction rule requires userId, targetKind, targetKey and ruleText");
        }
        String tenantId = defaultTenant(command.tenantId());
        String existingId = findActiveId(command.userId(), tenantId, command.targetKind(), command.targetKey());
        Instant now = Instant.now();
        if (existingId == null) {
            jdbcTemplate.update("""
                    INSERT INTO t_memory_correction_ledger
                    (id, user_id, tenant_id, correction_type, target_kind, target_key, incorrect_value,
                     correct_value, rule_text, priority, source_message_ids, effective_generation_id,
                     status, create_time, update_time, deleted)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'HARD_RULE', CAST(? AS JSON), ?, 'ACTIVE', ?, ?, 0)
                    """,
                    JdbcMemorySupport.nextId(),
                    JdbcMemorySupport.toLongId(command.userId()),
                    tenantId,
                    command.correctionType(),
                    command.targetKind(),
                    command.targetKey(),
                    command.incorrectValue(),
                    command.correctValue(),
                    command.ruleText(),
                    sourceIds(command.sourceIds()),
                    command.generationId(),
                    JdbcMemorySupport.timestamp(now),
                    JdbcMemorySupport.timestamp(now));
            return;
        }
        jdbcTemplate.update("""
                UPDATE t_memory_correction_ledger
                SET correction_type = ?,
                    incorrect_value = ?,
                    correct_value = ?,
                    rule_text = ?,
                    priority = 'HARD_RULE',
                    source_message_ids = CAST(? AS JSON),
                    effective_generation_id = ?,
                    update_time = ?
                WHERE id = ?
                  AND status = 'ACTIVE'
                  AND deleted = 0
                """,
                command.correctionType(),
                command.incorrectValue(),
                command.correctValue(),
                command.ruleText(),
                sourceIds(command.sourceIds()),
                command.generationId(),
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.toLongId(existingId));
    }

    private String findActiveId(String userId, String tenantId, String targetKind, String targetKey) {
        return jdbcTemplate.query("""
                SELECT id
                FROM t_memory_correction_ledger
                WHERE user_id = ?
                  AND tenant_id = ?
                  AND target_kind = ?
                  AND target_key = ?
                  AND status = 'ACTIVE'
                  AND deleted = 0
                ORDER BY update_time DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getString("id"), JdbcMemorySupport.toLongId(userId), tenantId, targetKind, targetKey)
                .stream()
                .findFirst()
                .orElse(null);
    }

    private CorrectionRule mapRule(ResultSet rs, int rowNum) throws SQLException {
        return new CorrectionRule(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("tenant_id"),
                rs.getString("correction_type"),
                rs.getString("target_kind"),
                rs.getString("target_key"),
                rs.getString("incorrect_value"),
                rs.getString("correct_value"),
                rs.getString("rule_text"),
                rs.getString("priority"),
                rs.getString("effective_generation_id"),
                rs.getString("status"),
                JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
    }

    private String sourceIds(List<String> sourceIds) {
        try {
            return objectMapper.writeValueAsString(Objects.requireNonNullElse(sourceIds, List.of()));
        } catch (Exception ex) {
            throw new IllegalArgumentException("correction source ids json serialization failed", ex);
        }
    }

    private String defaultTenant(String tenantId) {
        return JdbcMemorySupport.hasText(tenantId) ? tenantId : "default";
    }

    private int safeLimit(int limit) {
        return limit <= 0 ? 20 : limit;
    }
}

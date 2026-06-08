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

import com.miracle.ai.seahorse.agent.kernel.domain.user.UserTrial;
import com.miracle.ai.seahorse.agent.ports.outbound.user.TrialRepositoryPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC adapter for user trial records.
 */
public class JdbcTrialRepositoryAdapter implements TrialRepositoryPort {

    private static final String COLUMNS = """
            id, tenant_id, user_id, plan_code, status, token_limit, storage_limit_bytes,
            concurrency_limit, started_at, expires_at
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcTrialRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public UserTrial save(UserTrial trial) {
        UserTrial safeTrial = Objects.requireNonNull(trial, "trial must not be null");
        long id = safeTrial.id() == null ? JdbcMemorySupport.nextId() : safeTrial.id();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_user_trial (
                    id, tenant_id, user_id, plan_code, status, token_limit, storage_limit_bytes,
                    concurrency_limit, started_at, expires_at, created_at, updated_at
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (id) DO UPDATE SET
                    tenant_id = EXCLUDED.tenant_id,
                    user_id = EXCLUDED.user_id,
                    plan_code = EXCLUDED.plan_code,
                    status = EXCLUDED.status,
                    token_limit = EXCLUDED.token_limit,
                    storage_limit_bytes = EXCLUDED.storage_limit_bytes,
                    concurrency_limit = EXCLUDED.concurrency_limit,
                    started_at = EXCLUDED.started_at,
                    expires_at = EXCLUDED.expires_at,
                    updated_at = EXCLUDED.updated_at
                """,
                id,
                requireText(safeTrial.tenantId(), "tenantId must not be empty"),
                safeTrial.userId(),
                safeTrial.planCode(),
                safeTrial.status(),
                safeTrial.tokenLimit(),
                safeTrial.storageLimitBytes(),
                safeTrial.concurrencyLimit(),
                timestamp(safeTrial.startedAt()),
                timestamp(safeTrial.expiresAt()),
                now,
                now);
        return new UserTrial(
                id,
                safeTrial.tenantId(),
                safeTrial.userId(),
                safeTrial.planCode(),
                safeTrial.status(),
                safeTrial.tokenLimit(),
                safeTrial.storageLimitBytes(),
                safeTrial.concurrencyLimit(),
                safeTrial.startedAt(),
                safeTrial.expiresAt());
    }

    @Override
    public Optional<UserTrial> findByUserId(Long userId) {
        if (userId == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT " + COLUMNS + " FROM t_user_trial WHERE user_id = ? AND tenant_id = ?",
                    this::mapTrial, userId, JdbcTenantSupport.resolveTenantId()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserTrial> findByTenantId(String tenantId) {
        String resolvedTenantId = JdbcTenantSupport.resolveTenantId(tenantId);
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT " + COLUMNS + " FROM t_user_trial WHERE tenant_id = ? ORDER BY updated_at DESC LIMIT 1",
                    this::mapTrial, resolvedTenantId));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void updateStatus(Long trialId, String status) {
        if (trialId == null) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE t_user_trial SET status = ?, updated_at = ? WHERE id = ? AND tenant_id = ?
                """, requireText(status, "status must not be empty"), Timestamp.from(Instant.now()),
                trialId, JdbcTenantSupport.resolveTenantId());
    }

    private UserTrial mapTrial(ResultSet rs, int rowNum) throws SQLException {
        return new UserTrial(
                rs.getLong("id"),
                rs.getString("tenant_id"),
                rs.getLong("user_id"),
                rs.getString("plan_code"),
                rs.getString("status"),
                rs.getLong("token_limit"),
                rs.getLong("storage_limit_bytes"),
                rs.getInt("concurrency_limit"),
                instant(rs.getTimestamp("started_at")),
                instant(rs.getTimestamp("expires_at")));
    }

    private Timestamp timestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

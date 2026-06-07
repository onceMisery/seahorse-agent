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

import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRepositoryPort;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcRefreshTokenRepositoryAdapter implements RefreshTokenRepositoryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcRefreshTokenRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void save(Long userId, String refreshToken, Instant expiresAt) {
        if (userId == null || !hasText(refreshToken) || expiresAt == null) {
            throw new IllegalArgumentException("refresh token values must not be null");
        }
        jdbcTemplate.update("""
                UPDATE t_user
                SET refresh_token = ?, refresh_token_expires_at = ?, update_time = ?
                WHERE id = ? AND deleted = 0 AND tenant_id = ?
                """,
                refreshToken.trim(),
                Timestamp.from(expiresAt),
                Timestamp.from(Instant.now()),
                userId,
                JdbcTenantSupport.resolveTenantId());
    }

    @Override
    public Optional<RefreshTokenRecord> findValid(String refreshToken, Instant now) {
        if (!hasText(refreshToken) || now == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject("""
                    SELECT id, username, role, avatar, tenant_id, refresh_token, refresh_token_expires_at
                    FROM t_user
                    WHERE refresh_token = ?
                      AND refresh_token_expires_at > ?
                      AND deleted = 0
                      AND tenant_id = ?
                    """,
                    this::mapRecord,
                    refreshToken.trim(),
                    Timestamp.from(now),
                    JdbcTenantSupport.resolveTenantId()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public void revoke(String refreshToken) {
        if (!hasText(refreshToken)) {
            return;
        }
        jdbcTemplate.update("""
                UPDATE t_user
                SET refresh_token = NULL, refresh_token_expires_at = NULL, update_time = ?
                WHERE refresh_token = ? AND tenant_id = ?
                """,
                Timestamp.from(Instant.now()),
                refreshToken.trim(),
                JdbcTenantSupport.resolveTenantId());
    }

    private RefreshTokenRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        return new RefreshTokenRecord(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("role"),
                resultSet.getString("avatar"),
                resultSet.getString("tenant_id"),
                resultSet.getString("refresh_token"),
                toInstant(resultSet.getTimestamp("refresh_token_expires_at")));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

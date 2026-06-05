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

import com.miracle.ai.seahorse.agent.ports.outbound.auth.LoginHistoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * JDBC adapter for {@link LoginHistoryPort} that inserts and queries login history records
 * in the {@code t_login_history} table.
 */
public class JdbcLoginHistoryAdapter implements LoginHistoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcLoginHistoryAdapter.class);

    private static final String SQL_INSERT = """
            INSERT INTO t_login_history
                (user_id, tenant_id, login_type, ip_address, user_agent, device_info, status, failure_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_FIND_BY_USER_ID = """
            SELECT id, user_id, tenant_id, login_type, ip_address, user_agent, device_info, status, failure_reason, created_at
            FROM t_login_history
            WHERE user_id = ?
            ORDER BY created_at DESC
            LIMIT ? OFFSET ?
            """;

    private static final String SQL_COUNT_BY_USER_ID = """
            SELECT COUNT(*) FROM t_login_history WHERE user_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcLoginHistoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void recordLogin(long userId, String tenantId, String loginType,
                            String ipAddress, String userAgent, String deviceInfo,
                            String status, String failureReason) {
        String resolvedTenantId = JdbcTenantSupport.resolveTenantId(tenantId);
        try {
            jdbcTemplate.update(SQL_INSERT,
                    userId,
                    resolvedTenantId,
                    loginType,
                    ipAddress,
                    userAgent,
                    deviceInfo,
                    status,
                    failureReason);
        } catch (Exception e) {
            // Never let history recording break the main login flow
            log.warn("Failed to record login history for user={}, tenant={}, status={}: {}",
                    userId, resolvedTenantId, status, e.getMessage());
        }
    }

    @Override
    public List<LoginHistoryEntry> findByUserId(long userId, int page, int size) {
        try {
            int offset = page * size;
            return jdbcTemplate.query(SQL_FIND_BY_USER_ID, new LoginHistoryRowMapper(), userId, size, offset);
        } catch (Exception e) {
            log.warn("Failed to query login history for user={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public long countByUserId(long userId) {
        try {
            Long count = jdbcTemplate.queryForObject(SQL_COUNT_BY_USER_ID, Long.class, userId);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("Failed to count login history for user={}: {}", userId, e.getMessage());
            return 0L;
        }
    }

    private static class LoginHistoryRowMapper implements RowMapper<LoginHistoryEntry> {
        @Override
        public LoginHistoryEntry mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new LoginHistoryEntry(
                    rs.getLong("id"),
                    rs.getLong("user_id"),
                    rs.getString("tenant_id"),
                    rs.getString("login_type"),
                    rs.getString("ip_address"),
                    rs.getString("user_agent"),
                    rs.getString("device_info"),
                    rs.getString("status"),
                    rs.getString("failure_reason"),
                    createdAt != null ? createdAt.toInstant() : null
            );
        }
    }
}

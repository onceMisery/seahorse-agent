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

import javax.sql.DataSource;
import java.util.Objects;

/**
 * JDBC adapter for {@link LoginHistoryPort} that inserts login history records
 * into the {@code t_login_history} table.
 */
public class JdbcLoginHistoryAdapter implements LoginHistoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcLoginHistoryAdapter.class);

    private static final String SQL_INSERT = """
            INSERT INTO t_login_history
                (user_id, tenant_id, login_type, ip_address, user_agent, status, failure_reason)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcLoginHistoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void recordLogin(long userId, String tenantId, String loginType,
                            String ipAddress, String userAgent,
                            String status, String failureReason) {
        String resolvedTenantId = JdbcTenantSupport.resolveTenantId(tenantId);
        try {
            jdbcTemplate.update(SQL_INSERT,
                    userId,
                    resolvedTenantId,
                    loginType,
                    ipAddress,
                    userAgent,
                    status,
                    failureReason);
        } catch (Exception e) {
            // Never let history recording break the main login flow
            log.warn("Failed to record login history for user={}, tenant={}, status={}: {}",
                    userId, resolvedTenantId, status, e.getMessage());
        }
    }
}

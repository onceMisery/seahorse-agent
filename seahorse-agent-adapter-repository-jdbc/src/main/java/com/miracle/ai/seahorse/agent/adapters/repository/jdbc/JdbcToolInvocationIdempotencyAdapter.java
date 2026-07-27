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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationIdempotencyPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationIdentity;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;

/** JDBC-backed durable at-most-once claim for side-effecting tool invocations. */
public class JdbcToolInvocationIdempotencyAdapter implements ToolInvocationIdempotencyPort {

    private static final String SQL_CLAIM = """
            INSERT INTO sa_idempotency_key
                (tenant_id, idempotency_key, operation_type, status, response_body, created_at, expires_at)
            VALUES (?, ?, 'TOOL_INVOCATION', 'PROCESSING', NULL, ?, ?)
            """;
    private static final String SQL_COMPLETE = """
            UPDATE sa_idempotency_key
            SET status = 'COMPLETED'
            WHERE tenant_id = ?
              AND idempotency_key = ?
              AND operation_type = 'TOOL_INVOCATION'
              AND status = 'PROCESSING'
            """;
    private static final Instant NON_EXPIRING_CLAIM = Instant.parse("9999-12-31T23:59:59Z");

    private final JdbcTemplate jdbcTemplate;

    public JdbcToolInvocationIdempotencyAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public boolean tryClaim(String tenantId, String idempotencyKey, Instant now) {
        if (!hasText(tenantId) || !hasText(idempotencyKey) || now == null) {
            return false;
        }
        try {
            return jdbcTemplate.update(
                    SQL_CLAIM,
                    tenantId.trim(),
                    ToolInvocationIdentity.digest(tenantId, idempotencyKey),
                    Timestamp.from(now),
                    Timestamp.from(NON_EXPIRING_CLAIM)) == 1;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public void markCompleted(String tenantId, String idempotencyKey, Instant completedAt) {
        if (!hasText(tenantId) || !hasText(idempotencyKey) || completedAt == null) {
            return;
        }
        jdbcTemplate.update(SQL_COMPLETE, tenantId.trim(), ToolInvocationIdentity.digest(tenantId, idempotencyKey));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

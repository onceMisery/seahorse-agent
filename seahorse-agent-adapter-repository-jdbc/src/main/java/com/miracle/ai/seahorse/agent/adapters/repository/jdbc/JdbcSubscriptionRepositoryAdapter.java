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

import com.miracle.ai.seahorse.agent.kernel.domain.billing.PlanCode;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.Subscription;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionRepositoryPort;
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
import java.util.Optional;

/**
 * JDBC adapter for {@link SubscriptionRepositoryPort} that manages tenant
 * subscriptions in the {@code sa_subscription} table.
 */
public class JdbcSubscriptionRepositoryAdapter implements SubscriptionRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcSubscriptionRepositoryAdapter.class);

    private static final String SQL_FIND_BY_TENANT_ID = """
            SELECT id, tenant_id, plan_code, status, started_at, expires_at,
                   token_limit, storage_limit_bytes, concurrency_limit
            FROM sa_subscription
            WHERE tenant_id = ?
            ORDER BY created_at DESC
            """;

    private static final String SQL_FIND_ALL_ACTIVE = """
            SELECT id, tenant_id, plan_code, status, started_at, expires_at,
                   token_limit, storage_limit_bytes, concurrency_limit
            FROM sa_subscription
            WHERE status = 'ACTIVE'
            ORDER BY tenant_id, created_at DESC
            """;

    private static final String SQL_FIND_ACTIVE_BY_TENANT_ID = """
            SELECT id, tenant_id, plan_code, status, started_at, expires_at,
                   token_limit, storage_limit_bytes, concurrency_limit
            FROM sa_subscription
            WHERE tenant_id = ? AND status = 'ACTIVE'
            ORDER BY created_at DESC
            LIMIT 1
            """;

    private static final String SQL_INSERT = """
            INSERT INTO sa_subscription
                (id, tenant_id, plan_code, status, started_at, expires_at,
                 token_limit, storage_limit_bytes, concurrency_limit)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_UPDATE = """
            UPDATE sa_subscription
            SET plan_code = ?, status = ?, started_at = ?, expires_at = ?,
                token_limit = ?, storage_limit_bytes = ?, concurrency_limit = ?,
                updated_at = CURRENT_TIMESTAMP
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSubscriptionRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public List<Subscription> findByTenantId(String tenantId) {
        String resolvedTenantId = JdbcTenantSupport.resolveTenantId(tenantId);
        try {
            return jdbcTemplate.query(SQL_FIND_BY_TENANT_ID, new SubscriptionRowMapper(), resolvedTenantId);
        } catch (Exception e) {
            log.warn("Failed to query subscriptions for tenant={}: {}", resolvedTenantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Subscription save(Subscription subscription) {
        try {
            if (subscription.id() == null) {
                Long generatedId = SnowflakeIds.nextId();
                jdbcTemplate.update(SQL_INSERT,
                        generatedId,
                        subscription.tenantId(),
                        subscription.planCode().name(),
                        subscription.status(),
                        toTimestamp(subscription.startedAt()),
                        toTimestamp(subscription.expiresAt()),
                        subscription.tokenLimit(),
                        subscription.storageLimitBytes(),
                        subscription.concurrencyLimit());
                return new Subscription(generatedId, subscription.tenantId(), subscription.planCode(),
                        subscription.status(), subscription.startedAt(), subscription.expiresAt(),
                        subscription.tokenLimit(), subscription.storageLimitBytes(),
                        subscription.concurrencyLimit());
            } else {
                jdbcTemplate.update(SQL_UPDATE,
                        subscription.planCode().name(),
                        subscription.status(),
                        toTimestamp(subscription.startedAt()),
                        toTimestamp(subscription.expiresAt()),
                        subscription.tokenLimit(),
                        subscription.storageLimitBytes(),
                        subscription.concurrencyLimit(),
                        subscription.id());
                return subscription;
            }
        } catch (Exception e) {
            log.error("Failed to save subscription for tenant={}: {}",
                    subscription.tenantId(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<Subscription> findAllActive() {
        try {
            return jdbcTemplate.query(SQL_FIND_ALL_ACTIVE, new SubscriptionRowMapper());
        } catch (Exception e) {
            log.warn("Failed to query active subscriptions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Subscription> findActiveByTenantId(String tenantId) {
        String resolvedTenantId = JdbcTenantSupport.resolveTenantId(tenantId);
        try {
            List<Subscription> results = jdbcTemplate.query(
                    SQL_FIND_ACTIVE_BY_TENANT_ID, new SubscriptionRowMapper(), resolvedTenantId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to query active subscription for tenant={}: {}",
                    resolvedTenantId, e.getMessage());
            return Optional.empty();
        }
    }

    private static Timestamp toTimestamp(Instant instant) {
        return instant != null ? Timestamp.from(instant) : null;
    }

    private static class SubscriptionRowMapper implements RowMapper<Subscription> {
        @Override
        public Subscription mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp startedAt = rs.getTimestamp("started_at");
            Timestamp expiresAt = rs.getTimestamp("expires_at");
            return new Subscription(
                    rs.getLong("id"),
                    rs.getString("tenant_id"),
                    PlanCode.valueOf(rs.getString("plan_code")),
                    rs.getString("status"),
                    startedAt != null ? startedAt.toInstant() : null,
                    expiresAt != null ? expiresAt.toInstant() : null,
                    rs.getLong("token_limit"),
                    rs.getLong("storage_limit_bytes"),
                    rs.getInt("concurrency_limit")
            );
        }
    }
}

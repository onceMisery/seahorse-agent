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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentSubscription;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.AgentSubscriptionRepositoryPort;
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
 * JDBC adapter for {@link AgentSubscriptionRepositoryPort} that persists
 * agent subscription records in the {@code sa_agent_subscription} table.
 */
public class JdbcAgentSubscriptionRepositoryAdapter implements AgentSubscriptionRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcAgentSubscriptionRepositoryAdapter.class);

    private static final String COLUMNS = """
            id, agent_id, user_id, tenant_id, subscribed_at, active
            """;

    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_subscription
            (id, agent_id, user_id, tenant_id, subscribed_at, active)
            VALUES (?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_FIND_BY_AGENT_AND_USER = """
            SELECT %s
            FROM sa_agent_subscription
            WHERE agent_id = ? AND user_id = ? AND tenant_id = ?
            """.formatted(COLUMNS);

    private static final String SQL_FIND_BY_USER = """
            SELECT %s
            FROM sa_agent_subscription
            WHERE user_id = ? AND tenant_id = ?
            ORDER BY subscribed_at DESC
            """.formatted(COLUMNS);

    private static final String SQL_FIND_BY_USER_ACTIVE = """
            SELECT %s
            FROM sa_agent_subscription
            WHERE user_id = ? AND tenant_id = ? AND active = TRUE
            ORDER BY subscribed_at DESC
            """.formatted(COLUMNS);

    private static final String SQL_FIND_BY_AGENT = """
            SELECT %s
            FROM sa_agent_subscription
            WHERE agent_id = ? AND tenant_id = ?
            ORDER BY subscribed_at DESC
            """.formatted(COLUMNS);

    private static final String SQL_COUNT_BY_AGENT = """
            SELECT COUNT(1)
            FROM sa_agent_subscription
            WHERE agent_id = ? AND tenant_id = ?
            """;

    private static final String SQL_UPDATE = """
            UPDATE sa_agent_subscription
            SET active = ?, subscribed_at = ?
            WHERE id = ? AND tenant_id = ?
            """;

    private static final String SQL_DELETE_BY_ID = """
            DELETE FROM sa_agent_subscription
            WHERE id = ? AND tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentSubscriptionRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Long save(AgentSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        Long id = SnowflakeIds.nextId();
        String tenantId = JdbcTenantSupport.resolveTenantId(subscription.tenantId());
        try {
            jdbcTemplate.update(SQL_INSERT,
                    id,
                    subscription.agentId(),
                    subscription.userId(),
                    tenantId,
                    Timestamp.from(subscription.subscribedAt()),
                    subscription.active());
            return id;
        } catch (Exception e) {
            log.warn("Failed to save subscription for agent={}, user={}: {}",
                    subscription.agentId(), subscription.userId(), e.getMessage());
            return null;
        }
    }

    @Override
    public Optional<AgentSubscription> findByAgentIdAndUserId(String agentId, Long userId) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            List<AgentSubscription> results = jdbcTemplate.query(
                    SQL_FIND_BY_AGENT_AND_USER, new SubscriptionRowMapper(), agentId, userId, tenantId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to find subscription for agent={}, user={}: {}", agentId, userId, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<AgentSubscription> findByUserId(Long userId, boolean activeOnly) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            String sql = activeOnly ? SQL_FIND_BY_USER_ACTIVE : SQL_FIND_BY_USER;
            return jdbcTemplate.query(sql, new SubscriptionRowMapper(), userId, tenantId);
        } catch (Exception e) {
            log.warn("Failed to find subscriptions for user={}: {}", userId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<AgentSubscription> findByAgentId(String agentId) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            return jdbcTemplate.query(SQL_FIND_BY_AGENT, new SubscriptionRowMapper(), agentId, tenantId);
        } catch (Exception e) {
            log.warn("Failed to find subscriptions for agent={}: {}", agentId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public long countByAgentId(String agentId) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            Long count = jdbcTemplate.queryForObject(SQL_COUNT_BY_AGENT, Long.class, agentId, tenantId);
            return count != null ? count : 0L;
        } catch (Exception e) {
            log.warn("Failed to count subscriptions for agent={}: {}", agentId, e.getMessage());
            return 0L;
        }
    }

    @Override
    public boolean update(AgentSubscription subscription) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        String tenantId = JdbcTenantSupport.resolveTenantId(subscription.tenantId());
        try {
            int rows = jdbcTemplate.update(SQL_UPDATE,
                    subscription.active(),
                    Timestamp.from(subscription.subscribedAt()),
                    subscription.id(),
                    tenantId);
            return rows > 0;
        } catch (Exception e) {
            log.warn("Failed to update subscription id={}: {}", subscription.id(), e.getMessage());
            return false;
        }
    }

    @Override
    public boolean deleteById(Long id) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            int rows = jdbcTemplate.update(SQL_DELETE_BY_ID, id, tenantId);
            return rows > 0;
        } catch (Exception e) {
            log.warn("Failed to delete subscription id={}: {}", id, e.getMessage());
            return false;
        }
    }

    private static class SubscriptionRowMapper implements RowMapper<AgentSubscription> {
        @Override
        public AgentSubscription mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp subscribedAt = rs.getTimestamp("subscribed_at");
            return new AgentSubscription(
                    rs.getLong("id"),
                    rs.getString("agent_id"),
                    rs.getLong("user_id"),
                    rs.getString("tenant_id"),
                    subscribedAt != null ? subscribedAt.toInstant() : Instant.EPOCH,
                    rs.getBoolean("active")
            );
        }
    }
}

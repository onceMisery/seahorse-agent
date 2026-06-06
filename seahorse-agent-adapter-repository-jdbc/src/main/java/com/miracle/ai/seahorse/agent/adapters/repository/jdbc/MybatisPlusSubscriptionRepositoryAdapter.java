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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.SubscriptionDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.SubscriptionMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.PlanCode;
import com.miracle.ai.seahorse.agent.kernel.domain.billing.Subscription;
import com.miracle.ai.seahorse.agent.ports.outbound.billing.SubscriptionRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * MyBatis Plus adapter for {@link SubscriptionRepositoryPort} that manages
 * subscription persistence in the {@code sa_subscription} table.
 *
 * <p>Read operations that require JOIN with {@code sa_subscription_plan} use
 * {@link JdbcTemplate} as a fallback, while write operations use MyBatis Plus.</p>
 */
public class MybatisPlusSubscriptionRepositoryAdapter implements SubscriptionRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(MybatisPlusSubscriptionRepositoryAdapter.class);

    private static final String SQL_FIND_BY_USER_WITH_PLAN = """
            SELECT s.id, s.user_id, s.plan_code, s.status,
                   s.current_period_start, s.current_period_end,
                   p.token_limit, p.storage_limit_bytes, p.concurrency_limit
            FROM sa_subscription s
            LEFT JOIN sa_subscription_plan p ON s.plan_code = p.code
            WHERE s.user_id = ?
            """;

    private static final String SQL_FIND_ALL_ACTIVE_WITH_PLAN = """
            SELECT s.id, s.user_id, s.plan_code, s.status,
                   s.current_period_start, s.current_period_end,
                   p.token_limit, p.storage_limit_bytes, p.concurrency_limit
            FROM sa_subscription s
            LEFT JOIN sa_subscription_plan p ON s.plan_code = p.code
            WHERE s.status = 'ACTIVE'
            """;

    private static final String SQL_FIND_ACTIVE_BY_TENANT_WITH_PLAN = """
            SELECT s.id, s.user_id, s.plan_code, s.status,
                   s.current_period_start, s.current_period_end,
                   p.token_limit, p.storage_limit_bytes, p.concurrency_limit
            FROM sa_subscription s
            LEFT JOIN sa_subscription_plan p ON s.plan_code = p.code
            WHERE s.user_id = ? AND s.status = 'ACTIVE'
            """;

    private final SubscriptionMapper subscriptionMapper;
    private final JdbcTemplate jdbcTemplate;

    public MybatisPlusSubscriptionRepositoryAdapter(SubscriptionMapper subscriptionMapper,
                                                     DataSource dataSource) {
        this.subscriptionMapper = Objects.requireNonNull(subscriptionMapper, "subscriptionMapper must not be null");
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public List<Subscription> findByTenantId(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        try {
            return jdbcTemplate.query(SQL_FIND_BY_USER_WITH_PLAN,
                    new SubscriptionRowMapper(), Long.valueOf(tenantId));
        } catch (Exception e) {
            log.warn("Failed to find subscriptions for tenant={}: {}", tenantId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Subscription save(Subscription subscription) {
        Objects.requireNonNull(subscription, "subscription must not be null");
        try {
            if (subscription.id() == null) {
                SubscriptionDO entity = toDO(subscription);
                entity.setCreatedAt(LocalDateTime.now());
                subscriptionMapper.insert(entity);
                // Re-query to get plan limits via JOIN
                return findByIdWithPlan(entity.getId()).orElse(subscription);
            } else {
                SubscriptionDO entity = toDO(subscription);
                entity.setUpdatedAt(LocalDateTime.now());
                subscriptionMapper.updateById(entity);
                return findByIdWithPlan(subscription.id()).orElse(subscription);
            }
        } catch (Exception e) {
            log.error("Failed to save subscription id={}: {}", subscription.id(), e.getMessage(), e);
            throw e;
        }
    }

    @Override
    public List<Subscription> findAllActive() {
        try {
            return jdbcTemplate.query(SQL_FIND_ALL_ACTIVE_WITH_PLAN, new SubscriptionRowMapper());
        } catch (Exception e) {
            log.warn("Failed to find all active subscriptions: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public Optional<Subscription> findActiveByTenantId(String tenantId) {
        Objects.requireNonNull(tenantId, "tenantId must not be null");
        try {
            List<Subscription> results = jdbcTemplate.query(
                    SQL_FIND_ACTIVE_BY_TENANT_WITH_PLAN,
                    new SubscriptionRowMapper(), Long.valueOf(tenantId));
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to find active subscription for tenant={}: {}", tenantId, e.getMessage());
            return Optional.empty();
        }
    }

    private Optional<Subscription> findByIdWithPlan(Long id) {
        try {
            List<Subscription> results = jdbcTemplate.query(
                    SQL_FIND_BY_USER_WITH_PLAN + " AND s.id = ?",
                    new SubscriptionRowMapper(), id);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to re-query subscription id={}: {}", id, e.getMessage());
            return Optional.empty();
        }
    }

    private Subscription toDomain(SubscriptionDO entity) {
        return new Subscription(
                entity.getId(),
                entity.getUserId() != null ? String.valueOf(entity.getUserId()) : null,
                entity.getPlanCode() != null ? PlanCode.valueOf(entity.getPlanCode()) : null,
                entity.getStatus(),
                toInstant(entity.getCurrentPeriodStart()),
                toInstant(entity.getCurrentPeriodEnd()),
                0L,
                0L,
                0
        );
    }

    private SubscriptionDO toDO(Subscription subscription) {
        SubscriptionDO entity = new SubscriptionDO();
        entity.setId(subscription.id());
        entity.setUserId(subscription.tenantId() != null ? Long.valueOf(subscription.tenantId()) : null);
        entity.setPlanCode(subscription.planCode() != null ? subscription.planCode().name() : null);
        entity.setStatus(subscription.status());
        entity.setCurrentPeriodStart(toLocalDateTime(subscription.startedAt()));
        entity.setCurrentPeriodEnd(toLocalDateTime(subscription.expiresAt()));
        entity.setIsTrial(false);
        return entity;
    }

    private static java.time.Instant toInstant(LocalDateTime dateTime) {
        return dateTime != null ? dateTime.atZone(ZoneId.systemDefault()).toInstant() : null;
    }

    private static LocalDateTime toLocalDateTime(java.time.Instant instant) {
        return instant != null ? LocalDateTime.ofInstant(instant, ZoneId.systemDefault()) : null;
    }

    private static class SubscriptionRowMapper
            implements org.springframework.jdbc.core.RowMapper<Subscription> {
        @Override
        public Subscription mapRow(ResultSet rs, int rowNum) throws SQLException {
            return new Subscription(
                    rs.getLong("id"),
                    String.valueOf(rs.getLong("user_id")),
                    PlanCode.valueOf(rs.getString("plan_code")),
                    rs.getString("status"),
                    rs.getTimestamp("current_period_start") != null
                            ? rs.getTimestamp("current_period_start").toInstant() : null,
                    rs.getTimestamp("current_period_end") != null
                            ? rs.getTimestamp("current_period_end").toInstant() : null,
                    rs.getLong("token_limit"),
                    rs.getLong("storage_limit_bytes"),
                    rs.getInt("concurrency_limit")
            );
        }
    }
}

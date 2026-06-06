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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.RevenueShare;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.RevenueShareRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC adapter for {@link RevenueShareRepositoryPort} that persists
 * revenue share records in the {@code sa_revenue_share} table.
 */
public class JdbcRevenueShareRepositoryAdapter implements RevenueShareRepositoryPort {

    private static final Logger log = LoggerFactory.getLogger(JdbcRevenueShareRepositoryAdapter.class);

    private static final String COLUMNS = """
            id, tenant_id, agent_id, creator_user_id, period,
            gross_revenue, platform_share, creator_share, platform_rate,
            status, settled_at, paid_at, created_at
            """;

    private static final String SQL_INSERT = """
            INSERT INTO sa_revenue_share
            (id, tenant_id, agent_id, creator_user_id, period,
             gross_revenue, platform_share, creator_share, platform_rate,
             status, settled_at, paid_at, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_FIND_BY_AGENT_AND_PERIOD = """
            SELECT %s
            FROM sa_revenue_share
            WHERE agent_id = ? AND period = ? AND tenant_id = ?
            """.formatted(COLUMNS);

    private static final String SQL_FIND_BY_CREATOR = """
            SELECT %s
            FROM sa_revenue_share
            WHERE creator_user_id = ? AND tenant_id = ?
            ORDER BY period DESC
            """.formatted(COLUMNS);

    private static final String SQL_FIND_BY_CREATOR_AND_STATUS = """
            SELECT %s
            FROM sa_revenue_share
            WHERE creator_user_id = ? AND status = ? AND tenant_id = ?
            ORDER BY period DESC
            """.formatted(COLUMNS);

    private static final String SQL_FIND_PENDING = """
            SELECT %s
            FROM sa_revenue_share
            WHERE status = 'PENDING' AND period = ? AND tenant_id = ?
            ORDER BY created_at ASC
            """.formatted(COLUMNS);

    private static final String SQL_UPDATE_STATUS = """
            UPDATE sa_revenue_share
            SET status = ?, updated_at = CURRENT_TIMESTAMP
            WHERE id = ? AND tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcRevenueShareRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public RevenueShare save(RevenueShare share) {
        Objects.requireNonNull(share, "share must not be null");
        Long id = share.id() != null ? share.id() : SnowflakeIds.nextId();
        String tenantId = JdbcTenantSupport.resolveTenantId(share.tenantId());
        Instant createdAt = share.createdAt() != null ? share.createdAt() : Instant.now();
        try {
            jdbcTemplate.update(SQL_INSERT,
                    id,
                    tenantId,
                    share.agentId(),
                    share.creatorUserId(),
                    share.period(),
                    share.grossRevenue(),
                    share.platformShare(),
                    share.creatorShare(),
                    share.platformRate(),
                    share.status(),
                    share.settledAt() != null ? Timestamp.from(share.settledAt()) : null,
                    share.paidAt() != null ? Timestamp.from(share.paidAt()) : null,
                    Timestamp.from(createdAt));
            return new RevenueShare(
                    id, tenantId, share.agentId(), share.creatorUserId(), share.period(),
                    share.grossRevenue(), share.platformShare(), share.creatorShare(),
                    share.platformRate(), share.status(), share.settledAt(), share.paidAt(), createdAt);
        } catch (Exception e) {
            log.warn("Failed to save revenue share for agent={}, period={}: {}",
                    share.agentId(), share.period(), e.getMessage());
            return share;
        }
    }

    @Override
    public Optional<RevenueShare> findByAgentIdAndPeriod(String agentId, String period) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            List<RevenueShare> results = jdbcTemplate.query(
                    SQL_FIND_BY_AGENT_AND_PERIOD, new RevenueShareRowMapper(), agentId, period, tenantId);
            return results.isEmpty() ? Optional.empty() : Optional.of(results.get(0));
        } catch (Exception e) {
            log.warn("Failed to find revenue share for agent={}, period={}: {}", agentId, period, e.getMessage());
            return Optional.empty();
        }
    }

    @Override
    public List<RevenueShare> findByCreatorUserId(Long creatorUserId, String status) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            if (status == null || status.isBlank()) {
                return jdbcTemplate.query(SQL_FIND_BY_CREATOR, new RevenueShareRowMapper(), creatorUserId, tenantId);
            }
            return jdbcTemplate.query(SQL_FIND_BY_CREATOR_AND_STATUS,
                    new RevenueShareRowMapper(), creatorUserId, status, tenantId);
        } catch (Exception e) {
            log.warn("Failed to find revenue shares for creator={}: {}", creatorUserId, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<RevenueShare> findPendingShares(String period) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            return jdbcTemplate.query(SQL_FIND_PENDING, new RevenueShareRowMapper(), period, tenantId);
        } catch (Exception e) {
            log.warn("Failed to find pending revenue shares for period={}: {}", period, e.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public void updateStatus(Long id, String status) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        try {
            jdbcTemplate.update(SQL_UPDATE_STATUS, status, id, tenantId);
        } catch (Exception e) {
            log.warn("Failed to update revenue share status id={}, status={}: {}", id, status, e.getMessage());
        }
    }

    private static class RevenueShareRowMapper implements RowMapper<RevenueShare> {
        @Override
        public RevenueShare mapRow(ResultSet rs, int rowNum) throws SQLException {
            Timestamp settledAt = rs.getTimestamp("settled_at");
            Timestamp paidAt = rs.getTimestamp("paid_at");
            Timestamp createdAt = rs.getTimestamp("created_at");
            return new RevenueShare(
                    rs.getLong("id"),
                    rs.getString("tenant_id"),
                    rs.getString("agent_id"),
                    rs.getLong("creator_user_id"),
                    rs.getString("period"),
                    rs.getBigDecimal("gross_revenue"),
                    rs.getBigDecimal("platform_share"),
                    rs.getBigDecimal("creator_share"),
                    rs.getBigDecimal("platform_rate"),
                    rs.getString("status"),
                    settledAt != null ? settledAt.toInstant() : null,
                    paidAt != null ? paidAt.toInstant() : null,
                    createdAt != null ? createdAt.toInstant() : null
            );
        }
    }
}

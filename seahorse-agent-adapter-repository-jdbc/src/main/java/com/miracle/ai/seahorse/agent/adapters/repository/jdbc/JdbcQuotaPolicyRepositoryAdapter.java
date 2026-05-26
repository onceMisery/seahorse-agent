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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.QuotaPolicyRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcQuotaPolicyRepositoryAdapter implements QuotaPolicyRepositoryPort {

    private static final String POLICY_COLUMNS = """
            policy_id, tenant_id, scope, subject_id, status, token_limit, call_limit,
            cost_limit, warn_ratio, created_at, updated_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_quota_policy
            (policy_id, tenant_id, scope, subject_id, status, token_limit, call_limit,
             cost_limit, warn_ratio, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE = """
            UPDATE sa_quota_policy
            SET tenant_id = ?,
                scope = ?,
                subject_id = ?,
                status = ?,
                token_limit = ?,
                call_limit = ?,
                cost_limit = ?,
                warn_ratio = ?,
                created_at = ?,
                updated_at = ?
            WHERE policy_id = ?
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_quota_policy
            WHERE policy_id = ?
            """.formatted(POLICY_COLUMNS);
    private static final String SQL_FIND_ACTIVE = """
            SELECT %s
            FROM sa_quota_policy
            WHERE tenant_id = ?
              AND scope = ?
              AND subject_id = ?
              AND status = 'ACTIVE'
            ORDER BY updated_at DESC, policy_id DESC
            LIMIT 1
            """.formatted(POLICY_COLUMNS);
    private static final String SQL_DISABLE = """
            UPDATE sa_quota_policy
            SET status = 'DISABLED',
                updated_at = ?
            WHERE policy_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcQuotaPolicyRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public QuotaPolicy upsert(QuotaPolicy policy) {
        QuotaPolicy safePolicy = Objects.requireNonNull(policy, "policy must not be null");
        if (findById(safePolicy.policyId()).isPresent()) {
            jdbcTemplate.update(SQL_UPDATE,
                    safePolicy.tenantId(),
                    safePolicy.scope().name(),
                    safePolicy.subjectId(),
                    safePolicy.status().name(),
                    safePolicy.tokenLimit(),
                    safePolicy.callLimit(),
                    safePolicy.costLimit(),
                    safePolicy.warnRatio(),
                    toTimestamp(safePolicy.createdAt()),
                    toTimestamp(safePolicy.updatedAt()),
                    safePolicy.policyId());
            return safePolicy;
        }
        jdbcTemplate.update(SQL_INSERT,
                safePolicy.policyId(),
                safePolicy.tenantId(),
                safePolicy.scope().name(),
                safePolicy.subjectId(),
                safePolicy.status().name(),
                safePolicy.tokenLimit(),
                safePolicy.callLimit(),
                safePolicy.costLimit(),
                safePolicy.warnRatio(),
                toTimestamp(safePolicy.createdAt()),
                toTimestamp(safePolicy.updatedAt()));
        return safePolicy;
    }

    @Override
    public Optional<QuotaPolicy> findActive(String tenantId, QuotaScope scope, String subjectId) {
        if (!hasText(tenantId) || scope == null || !hasText(subjectId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(
                        SQL_FIND_ACTIVE,
                        this::mapPolicy,
                        tenantId.trim(),
                        scope.name(),
                        subjectId.trim())
                .stream()
                .findFirst();
    }

    @Override
    public void disable(String policyId, Instant updatedAt) {
        if (!hasText(policyId)) {
            return;
        }
        jdbcTemplate.update(
                SQL_DISABLE,
                toTimestamp(Objects.requireNonNull(updatedAt, "updatedAt must not be null")),
                policyId.trim());
    }

    private Optional<QuotaPolicy> findById(String policyId) {
        if (!hasText(policyId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::mapPolicy, policyId.trim()).stream().findFirst();
    }

    private QuotaPolicy mapPolicy(ResultSet resultSet, int rowNum) throws SQLException {
        return new QuotaPolicy(
                resultSet.getString("policy_id"),
                resultSet.getString("tenant_id"),
                QuotaScope.valueOf(resultSet.getString("scope")),
                resultSet.getString("subject_id"),
                QuotaPolicyStatus.valueOf(resultSet.getString("status")),
                longOrNull(resultSet, "token_limit"),
                longOrNull(resultSet, "call_limit"),
                doubleOrNull(resultSet, "cost_limit"),
                resultSet.getDouble("warn_ratio"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private Long longOrNull(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Double doubleOrNull(ResultSet resultSet, String column) throws SQLException {
        double value = resultSet.getDouble(column);
        return resultSet.wasNull() ? null : value;
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

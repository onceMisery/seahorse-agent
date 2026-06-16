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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageSource;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JdbcCostUsageRepositoryAdapter implements CostUsageRepositoryPort {

    private static final String SQL_INSERT = """
            INSERT INTO sa_cost_usage_record
            (usage_id, tenant_id, agent_id, run_id, rollout_id, user_id, tool_id, model_id,
             source, tokens, calls, cost, reason_ref, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcCostUsageRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public CostUsageRecord append(CostUsageRecord record) {
        CostUsageRecord safeRecord = Objects.requireNonNull(record, "record must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeRecord.usageId(),
                safeRecord.tenantId(),
                safeRecord.agentId(),
                safeRecord.runId(),
                safeRecord.rolloutId(),
                safeRecord.userId(),
                safeRecord.toolId(),
                safeRecord.modelId(),
                safeRecord.source().name(),
                safeRecord.tokens(),
                safeRecord.calls(),
                safeRecord.cost(),
                safeRecord.reasonRef(),
                toTimestamp(safeRecord.createdAt()));
        return safeRecord;
    }

    @Override
    public CostUsageAggregate aggregate(CostUsageQuery query) {
        CostUsageQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        QueryParts where = where(safeQuery);
        return jdbcTemplate.queryForObject("""
                        SELECT
                          COALESCE(SUM(tokens), 0) AS total_tokens,
                          COALESCE(SUM(calls), 0) AS total_calls,
                          COALESCE(SUM(cost), 0) AS total_cost,
                          COUNT(1) AS record_count
                        FROM sa_cost_usage_record
                        %s
                        """.formatted(where.whereClause()),
                (resultSet, rowNum) -> mapAggregate(resultSet, safeQuery),
                where.params().toArray());
    }

    private QueryParts where(CostUsageQuery query) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        addTextFilter(clauses, params, "tenant_id", query.tenantId());
        addTextFilter(clauses, params, "agent_id", query.agentId());
        addTextFilter(clauses, params, "run_id", query.runId());
        addTextFilter(clauses, params, "rollout_id", query.rolloutId());
        if (query.from() != null) {
            clauses.add("created_at >= ?");
            params.add(toTimestamp(query.from()));
        }
        if (query.to() != null) {
            clauses.add("created_at <= ?");
            params.add(toTimestamp(query.to()));
        }
        return clauses.isEmpty()
                ? new QueryParts("", params)
                : new QueryParts("WHERE " + String.join(" AND ", clauses), params);
    }

    private void addTextFilter(List<String> clauses, List<Object> params, String column, String value) {
        if (hasText(value)) {
            clauses.add(column + " = ?");
            params.add(value.trim());
        }
    }

    private CostUsageAggregate mapAggregate(ResultSet resultSet, CostUsageQuery query) throws SQLException {
        return new CostUsageAggregate(
                query.tenantId(),
                query.agentId(),
                query.runId(),
                resultSet.getLong("total_tokens"),
                resultSet.getLong("total_calls"),
                resultSet.getDouble("total_cost"),
                resultSet.getLong("record_count"));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record QueryParts(String whereClause, List<Object> params) {

        private QueryParts {
            params = params == null ? List.of() : List.copyOf(params);
        }
    }
}

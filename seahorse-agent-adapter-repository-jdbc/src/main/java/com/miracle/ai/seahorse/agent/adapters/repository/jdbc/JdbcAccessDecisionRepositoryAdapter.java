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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AccessDecisionQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JdbcAccessDecisionRepositoryAdapter implements AccessDecisionLogPort, AccessDecisionQueryPort {

    private static final String DECISION_COLUMNS = """
            decision_id, tenant_id, subject_type, subject_id, action, resource_type, resource_id,
            effect, reason_code, created_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_access_decision_log
            (decision_id, tenant_id, subject_type, subject_id, action, resource_type, resource_id,
             effect, reason_code, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAccessDecisionRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void record(AccessDecision decision) {
        AccessDecision safeDecision = Objects.requireNonNull(decision, "decision must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeDecision.decisionId(),
                safeDecision.tenantId(),
                safeDecision.subjectType().name(),
                safeDecision.subjectId(),
                safeDecision.action().name(),
                safeDecision.resourceType(),
                safeDecision.resourceId(),
                safeDecision.effect().name(),
                safeDecision.reasonCode(),
                toTimestamp(safeDecision.createdAt()));
    }

    @Override
    public AccessDecisionPage page(AccessDecisionQuery query) {
        AccessDecisionQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        QueryParts parts = buildQueryParts(safeQuery);
        long total = count(parts);
        if (total == 0L) {
            return new AccessDecisionPage(List.of(), 0L, safeQuery.size(), safeQuery.current(), 0L);
        }

        long offset = (safeQuery.current() - 1L) * safeQuery.size();
        List<Object> parameters = new ArrayList<>(parts.parameters());
        parameters.add(safeQuery.size());
        parameters.add(offset);
        List<AccessDecision> records = jdbcTemplate.query("""
                        SELECT %s
                        FROM sa_access_decision_log
                        %s
                        ORDER BY created_at DESC, decision_id DESC
                        LIMIT ? OFFSET ?
                        """.formatted(DECISION_COLUMNS, parts.whereSql()),
                this::mapDecision,
                parameters.toArray());
        long pages = (total + safeQuery.size() - 1L) / safeQuery.size();
        return new AccessDecisionPage(records, total, safeQuery.size(), safeQuery.current(), pages);
    }

    private QueryParts buildQueryParts(AccessDecisionQuery query) {
        List<String> conditions = new ArrayList<>();
        List<Object> parameters = new ArrayList<>();
        addCondition(conditions, parameters, "tenant_id", query.tenantId());
        if (query.subjectType() != null) {
            conditions.add("subject_type = ?");
            parameters.add(query.subjectType().name());
        }
        addCondition(conditions, parameters, "subject_id", query.subjectId());
        if (query.action() != null) {
            conditions.add("action = ?");
            parameters.add(query.action().name());
        }
        addCondition(conditions, parameters, "resource_type", query.resourceType());
        addCondition(conditions, parameters, "resource_id", query.resourceId());
        if (query.effect() != null) {
            conditions.add("effect = ?");
            parameters.add(query.effect().name());
        }
        addCondition(conditions, parameters, "reason_code", query.reasonCode());
        String whereSql = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        return new QueryParts(whereSql, parameters);
    }

    private void addCondition(List<String> conditions, List<Object> parameters, String column, String value) {
        if (!hasText(value)) {
            return;
        }
        conditions.add(column + " = ?");
        parameters.add(value.trim());
    }

    private long count(QueryParts parts) {
        Long count = jdbcTemplate.queryForObject("""
                        SELECT COUNT(1)
                        FROM sa_access_decision_log
                        %s
                        """.formatted(parts.whereSql()),
                Long.class,
                parts.parameters().toArray());
        return count == null ? 0L : count;
    }

    private AccessDecision mapDecision(ResultSet resultSet, int rowNum) throws SQLException {
        return new AccessDecision(
                resultSet.getString("decision_id"),
                resultSet.getString("tenant_id"),
                AccessSubjectType.valueOf(resultSet.getString("subject_type")),
                resultSet.getString("subject_id"),
                ResourceAction.valueOf(resultSet.getString("action")),
                resultSet.getString("resource_type"),
                resultSet.getString("resource_id"),
                AccessDecisionEffect.valueOf(resultSet.getString("effect")),
                resultSet.getString("reason_code"),
                toInstant(resultSet.getTimestamp("created_at")));
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record QueryParts(String whereSql, List<Object> parameters) {

        private QueryParts {
            parameters = List.copyOf(parameters);
        }
    }
}

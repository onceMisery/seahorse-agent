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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcAuditEventRepositoryAdapter implements AuditEventRepositoryPort {

    private static final String EVENT_COLUMNS = """
            audit_id, tenant_id, event_type, actor_type, actor_id, run_id, agent_id,
            resource_type, resource_id, redacted_payload, occurred_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_audit_event
            (audit_id, tenant_id, event_type, actor_type, actor_id, run_id, agent_id,
             resource_type, resource_id, redacted_payload, occurred_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_audit_event
            WHERE audit_id = ?
            """.formatted(EVENT_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditEventRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public AuditEvent save(AuditEvent event) {
        AuditEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeEvent.auditId(),
                safeEvent.tenantId(),
                safeEvent.eventType().name(),
                safeEvent.actorType().name(),
                safeEvent.actorId(),
                safeEvent.runId(),
                safeEvent.agentId(),
                safeEvent.resourceType(),
                safeEvent.resourceId(),
                safeEvent.redactedPayload(),
                toTimestamp(safeEvent.occurredAt()));
        return safeEvent;
    }

    @Override
    public Optional<AuditEvent> findById(String auditId) {
        if (!hasText(auditId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::mapEvent, auditId.trim()).stream().findFirst();
    }

    @Override
    public AuditEventPage page(AuditEventQuery query) {
        AuditEventQuery safeQuery = Objects.requireNonNull(query, "query must not be null");
        QueryParts where = where(safeQuery);
        long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sa_audit_event " + where.whereClause(),
                Long.class,
                where.params().toArray());
        long pages = total == 0 ? 0 : (long) Math.ceil((double) total / safeQuery.size());
        List<Object> pageParams = new ArrayList<>(where.params());
        pageParams.add(safeQuery.size());
        pageParams.add(offset(safeQuery));
        List<AuditEvent> records = jdbcTemplate.query("""
                        SELECT %s
                        FROM sa_audit_event
                        %s
                        ORDER BY occurred_at DESC, audit_id DESC
                        LIMIT ? OFFSET ?
                        """.formatted(EVENT_COLUMNS, where.whereClause()),
                this::mapEvent,
                pageParams.toArray());
        return new AuditEventPage(records, total, safeQuery.size(), safeQuery.current(), pages);
    }

    private QueryParts where(AuditEventQuery query) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        addTextFilter(clauses, params, "tenant_id", query.tenantId());
        addTextFilter(clauses, params, "run_id", query.runId());
        addTextFilter(clauses, params, "agent_id", query.agentId());
        addTextFilter(clauses, params, "resource_type", query.resourceType());
        addTextFilter(clauses, params, "resource_id", query.resourceId());
        if (query.eventType() != null) {
            clauses.add("event_type = ?");
            params.add(query.eventType().name());
        }
        if (query.occurredFrom() != null) {
            clauses.add("occurred_at >= ?");
            params.add(toTimestamp(query.occurredFrom()));
        }
        if (query.occurredTo() != null) {
            clauses.add("occurred_at <= ?");
            params.add(toTimestamp(query.occurredTo()));
        }
        return new QueryParts(clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses), params);
    }

    private void addTextFilter(List<String> clauses, List<Object> params, String column, String value) {
        if (hasText(value)) {
            clauses.add(column + " = ?");
            params.add(value.trim());
        }
    }

    private AuditEvent mapEvent(ResultSet resultSet, int rowNum) throws SQLException {
        return new AuditEvent(
                resultSet.getString("audit_id"),
                resultSet.getString("tenant_id"),
                AuditEventType.valueOf(resultSet.getString("event_type")),
                AuditActorType.valueOf(resultSet.getString("actor_type")),
                resultSet.getString("actor_id"),
                resultSet.getString("run_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("resource_type"),
                resultSet.getString("resource_id"),
                resultSet.getString("redacted_payload"),
                toInstant(resultSet.getTimestamp("occurred_at")));
    }

    private long offset(AuditEventQuery query) {
        return (query.current() - 1) * query.size();
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

    private record QueryParts(String whereClause, List<Object> params) {

        private QueryParts {
            params = params == null ? List.of() : List.copyOf(params);
        }
    }
}

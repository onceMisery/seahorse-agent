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

import com.miracle.ai.seahorse.agent.kernel.domain.audit.AuditLog;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.admin.AuditLogRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JdbcAuditLogRepositoryAdapter implements AuditLogRepositoryPort {

    private static final String COLUMNS = """
            id, tenant_id, operator, action, resource_type, resource_id,
            detail, ip_address, user_agent, created_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_audit_log
            (id, tenant_id, operator, action, resource_type, resource_id,
             detail, ip_address, user_agent, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_DELETE_OLDER_THAN = """
            DELETE FROM sa_audit_log
            WHERE created_at < ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAuditLogRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Long save(AuditLog log) {
        AuditLog safe = Objects.requireNonNull(log, "log must not be null");
        Long id = SnowflakeIds.nextId();
        jdbcTemplate.update(SQL_INSERT,
                id,
                safe.tenantId(),
                safe.operator(),
                safe.action(),
                safe.resourceType(),
                safe.resourceId(),
                safe.detail(),
                safe.ipAddress(),
                safe.userAgent(),
                toTimestamp(safe.createdAt()));
        return id;
    }

    @Override
    public List<AuditLog> queryLogs(String tenantId, String action, String resourceType,
                                     String operator, Instant startTime, Instant endTime,
                                     int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int offset = (safePage - 1) * safeSize;

        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        addTextFilter(clauses, params, "tenant_id", tenantId);
        addTextFilter(clauses, params, "action", action);
        addTextFilter(clauses, params, "resource_type", resourceType);
        addTextFilter(clauses, params, "operator", operator);
        if (startTime != null) {
            clauses.add("created_at >= ?");
            params.add(toTimestamp(startTime));
        }
        if (endTime != null) {
            clauses.add("created_at <= ?");
            params.add(toTimestamp(endTime));
        }

        String where = clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
        String sql = """
                SELECT %s
                FROM sa_audit_log
                %s
                ORDER BY created_at DESC, id DESC
                LIMIT ? OFFSET ?
                """.formatted(COLUMNS, where);

        List<Object> queryParams = new ArrayList<>(params);
        queryParams.add(safeSize);
        queryParams.add(offset);
        return jdbcTemplate.query(sql, this::mapLog, queryParams.toArray());
    }

    @Override
    public long countLogs(String tenantId, String action, String resourceType,
                          String operator, Instant startTime, Instant endTime) {
        List<String> clauses = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        addTextFilter(clauses, params, "tenant_id", tenantId);
        addTextFilter(clauses, params, "action", action);
        addTextFilter(clauses, params, "resource_type", resourceType);
        addTextFilter(clauses, params, "operator", operator);
        if (startTime != null) {
            clauses.add("created_at >= ?");
            params.add(toTimestamp(startTime));
        }
        if (endTime != null) {
            clauses.add("created_at <= ?");
            params.add(toTimestamp(endTime));
        }

        String where = clauses.isEmpty() ? "" : "WHERE " + String.join(" AND ", clauses);
        String sql = "SELECT COUNT(1) FROM sa_audit_log " + where;
        Long count = jdbcTemplate.queryForObject(sql, Long.class, params.toArray());
        return count != null ? count : 0L;
    }

    @Override
    public int deleteOlderThan(Instant cutoff) {
        return jdbcTemplate.update(SQL_DELETE_OLDER_THAN, toTimestamp(cutoff));
    }

    private AuditLog mapLog(ResultSet rs, int rowNum) throws SQLException {
        return new AuditLog(
                rs.getLong("id"),
                rs.getString("tenant_id"),
                rs.getString("operator"),
                rs.getString("action"),
                rs.getString("resource_type"),
                rs.getString("resource_id"),
                rs.getString("detail"),
                rs.getString("ip_address"),
                rs.getString("user_agent"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private void addTextFilter(List<String> clauses, List<Object> params, String column, String value) {
        if (value != null && !value.isBlank()) {
            clauses.add(column + " = ?");
            params.add(value.trim());
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
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

/**
 * 工具目录 JDBC 仓储适配器，负责持久化 Tool Gateway 的工具元数据。
 */
public class JdbcToolCatalogRepositoryAdapter implements ToolCatalogRepositoryPort {

    private static final long MAX_PAGE_SIZE = 100L;

    private static final String SQL_INSERT = """
            INSERT INTO sa_tool_catalog
            (tool_id, provider, name, description, schema_json, output_schema_json, risk_level, action_type,
             resource_type, owner_team, enabled, requires_approval, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE = """
            UPDATE sa_tool_catalog
            SET provider = ?,
                name = ?,
                description = ?,
                schema_json = ?,
                output_schema_json = ?,
                risk_level = ?,
                action_type = ?,
                resource_type = ?,
                owner_team = ?,
                enabled = ?,
                requires_approval = ?,
                created_at = ?,
                updated_at = ?
            WHERE tool_id = ?
            """;
    private static final String SQL_FIND_BY_ID = """
            SELECT tool_id, provider, name, description, schema_json, output_schema_json, risk_level, action_type,
                   resource_type, owner_team, enabled, requires_approval, created_at, updated_at
            FROM sa_tool_catalog
            WHERE tool_id = ?
            """;
    private static final String SQL_SET_ENABLED = """
            UPDATE sa_tool_catalog
            SET enabled = ?
            WHERE tool_id = ?
            """;
    private static final String SQL_COUNT = """
            SELECT COUNT(1)
            FROM sa_tool_catalog
            WHERE 1 = 1
            """;
    private static final String SQL_PAGE = """
            SELECT tool_id, provider, name, description, schema_json, output_schema_json, risk_level, action_type,
                   resource_type, owner_team, enabled, requires_approval, created_at, updated_at
            FROM sa_tool_catalog
            WHERE 1 = 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcToolCatalogRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void save(ToolCatalogEntry entry) {
        ToolCatalogEntry safeEntry = Objects.requireNonNull(entry, "entry must not be null");
        if (findById(safeEntry.toolId()).isPresent()) {
            update(safeEntry);
            return;
        }
        insert(safeEntry);
    }

    @Override
    public Optional<ToolCatalogEntry> findById(String toolId) {
        if (!hasText(toolId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::mapEntry, toolId.trim()).stream().findFirst();
    }

    @Override
    public void setEnabled(String toolId, boolean enabled) {
        if (!hasText(toolId)) {
            return;
        }
        jdbcTemplate.update(SQL_SET_ENABLED, enabled, toolId.trim());
    }

    @Override
    public ToolCatalogPage page(ToolCatalogQuery query) {
        ToolCatalogQuery safeQuery = query == null ? new ToolCatalogQuery(null, null, null, null,
                ToolCatalogQuery.DEFAULT_CURRENT,
                ToolCatalogQuery.DEFAULT_PAGE_SIZE,
                null) : query;
        long current = safeQuery.current();
        long size = clampSize(safeQuery.size());
        QueryParts filters = filters(safeQuery);

        Long total = jdbcTemplate.queryForObject(SQL_COUNT + filters.where(), Long.class, filters.args().toArray());
        long safeTotal = total == null ? 0L : total;

        List<Object> pageArgs = new ArrayList<>(filters.args());
        pageArgs.add(size);
        pageArgs.add((current - 1L) * size);
        List<ToolCatalogEntry> records = jdbcTemplate.query(
                SQL_PAGE + filters.where() + " ORDER BY updated_at DESC, tool_id ASC LIMIT ? OFFSET ?",
                this::mapEntry,
                pageArgs.toArray());
        return new ToolCatalogPage(records, safeTotal, size, current, pages(safeTotal, size));
    }

    private void insert(ToolCatalogEntry entry) {
        jdbcTemplate.update(SQL_INSERT,
                entry.toolId(),
                entry.provider().name(),
                entry.name(),
                entry.description(),
                entry.schemaJson(),
                entry.outputSchemaJson(),
                entry.riskLevel().name(),
                entry.actionType().name(),
                entry.resourceType(),
                entry.ownerTeam(),
                entry.enabled(),
                entry.requiresApproval(),
                toTimestamp(entry.createdAt()),
                toTimestamp(entry.updatedAt()));
    }

    private void update(ToolCatalogEntry entry) {
        jdbcTemplate.update(SQL_UPDATE,
                entry.provider().name(),
                entry.name(),
                entry.description(),
                entry.schemaJson(),
                entry.outputSchemaJson(),
                entry.riskLevel().name(),
                entry.actionType().name(),
                entry.resourceType(),
                entry.ownerTeam(),
                entry.enabled(),
                entry.requiresApproval(),
                toTimestamp(entry.createdAt()),
                toTimestamp(entry.updatedAt()),
                entry.toolId());
    }

    private ToolCatalogEntry mapEntry(ResultSet resultSet, int rowNum) throws SQLException {
        return new ToolCatalogEntry(
                resultSet.getString("tool_id"),
                ToolProvider.valueOf(resultSet.getString("provider")),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("schema_json"),
                resultSet.getString("output_schema_json"),
                ToolRiskLevel.valueOf(resultSet.getString("risk_level")),
                ToolActionType.valueOf(resultSet.getString("action_type")),
                resultSet.getString("resource_type"),
                resultSet.getString("owner_team"),
                resultSet.getBoolean("enabled"),
                resultSet.getBoolean("requires_approval"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private QueryParts filters(ToolCatalogQuery query) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        if (hasText(query.provider())) {
            clauses.add("provider = ?");
            args.add(query.provider());
        }
        if (hasText(query.resourceType())) {
            clauses.add("resource_type = ?");
            args.add(query.resourceType());
        }
        if (hasText(query.riskLevel())) {
            clauses.add("risk_level = ?");
            args.add(query.riskLevel());
        }
        if (hasText(query.keyword())) {
            String keyword = like(query.keyword());
            clauses.add("(tool_id LIKE ? OR name LIKE ? OR description LIKE ?)");
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }
        if (query.enabled() != null) {
            clauses.add("enabled = ?");
            args.add(query.enabled());
        }
        if (clauses.isEmpty()) {
            return new QueryParts("", List.of());
        }
        return new QueryParts(" AND " + String.join(" AND ", clauses), args);
    }

    private long clampSize(long size) {
        if (size <= 0L) {
            return ToolCatalogQuery.DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private long pages(long total, long size) {
        if (total <= 0L || size <= 0L) {
            return 0L;
        }
        return (total + size - 1L) / size;
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
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

    private record QueryParts(String where, List<Object> args) {
    }
}

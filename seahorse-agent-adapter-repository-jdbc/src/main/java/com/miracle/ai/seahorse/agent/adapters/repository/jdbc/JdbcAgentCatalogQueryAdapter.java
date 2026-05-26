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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentCatalogPage;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCatalogQueryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class JdbcAgentCatalogQueryAdapter implements AgentCatalogQueryPort {

    private static final long MAX_PAGE_SIZE = 100L;
    private static final String CATALOG_COLUMNS = """
            d.agent_id, d.tenant_id, d.name, d.description, d.owner_user_id, d.owner_team,
            d.agent_type, d.risk_level, d.latest_version_id, v.published_at
            """;
    private static final String SQL_COUNT_BASE = """
            SELECT COUNT(1)
            FROM sa_agent_definition d
            JOIN sa_agent_version v ON v.version_id = d.latest_version_id
            WHERE d.tenant_id = ? AND d.status = ? AND d.latest_version_id IS NOT NULL
            """;
    private static final String SQL_PAGE_BASE = """
            SELECT %s
            FROM sa_agent_definition d
            JOIN sa_agent_version v ON v.version_id = d.latest_version_id
            WHERE d.tenant_id = ? AND d.status = ? AND d.latest_version_id IS NOT NULL
            """.formatted(CATALOG_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentCatalogQueryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public AgentCatalogPage page(AgentCatalogQuery query) {
        AgentCatalogQuery safeQuery = query == null
                ? new AgentCatalogQuery(null, null, AgentCatalogQuery.DEFAULT_CURRENT, AgentCatalogQuery.DEFAULT_SIZE)
                : query;
        long current = safeQuery.current();
        long size = clampSize(safeQuery.size());
        if (!hasText(safeQuery.tenantId())) {
            return new AgentCatalogPage(List.of(), 0L, size, current, 0L);
        }
        QueryParts keywordFilter = keywordFilter(safeQuery.keyword());
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(safeQuery.tenantId());
        countArgs.add(AgentStatus.PUBLISHED.name());
        countArgs.addAll(keywordFilter.args());
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_BASE + keywordFilter.where(),
                Long.class,
                countArgs.toArray());
        long safeTotal = total == null ? 0L : total;

        List<Object> pageArgs = new ArrayList<>(countArgs);
        pageArgs.add(size);
        pageArgs.add((current - 1L) * size);
        List<AgentCatalogEntry> records = jdbcTemplate.query(
                SQL_PAGE_BASE + keywordFilter.where()
                        + " ORDER BY d.updated_at DESC, d.agent_id ASC LIMIT ? OFFSET ?",
                this::mapEntry,
                pageArgs.toArray());
        return new AgentCatalogPage(records, safeTotal, size, current, pages(safeTotal, size));
    }

    private AgentCatalogEntry mapEntry(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentCatalogEntry(
                resultSet.getString("agent_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("owner_user_id"),
                resultSet.getString("owner_team"),
                AgentType.valueOf(resultSet.getString("agent_type")),
                AgentRiskLevel.valueOf(resultSet.getString("risk_level")),
                resultSet.getString("latest_version_id"),
                toInstant(resultSet.getTimestamp("published_at")));
    }

    private QueryParts keywordFilter(String keyword) {
        if (!hasText(keyword)) {
            return new QueryParts("", List.of());
        }
        String value = "%" + keyword.trim() + "%";
        return new QueryParts(" AND (d.name LIKE ? OR d.description LIKE ? OR d.owner_team LIKE ?)",
                List.of(value, value, value));
    }

    private long clampSize(long size) {
        if (size <= 0) {
            return AgentCatalogQuery.DEFAULT_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private long pages(long total, long size) {
        return total == 0L ? 0L : (total + size - 1L) / size;
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

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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
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

public class JdbcAgentDefinitionRepositoryAdapter implements AgentDefinitionRepositoryPort {

    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;

    private static final String SQL_INSERT_DEFINITION = """
            INSERT INTO sa_agent_definition
            (agent_id, tenant_id, name, description, owner_user_id, owner_team, agent_type, base_agent_id,
             status, risk_level, latest_version_id, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_DEFINITION = """
            UPDATE sa_agent_definition
            SET tenant_id = ?,
                name = ?,
                description = ?,
                owner_user_id = ?,
                owner_team = ?,
                agent_type = ?,
                base_agent_id = ?,
                status = ?,
                risk_level = ?,
                latest_version_id = ?,
                created_at = ?,
                updated_at = ?
            WHERE agent_id = ?
            """;
    private static final String SQL_FIND_DEFINITION = """
            SELECT agent_id, tenant_id, name, description, owner_user_id, owner_team, agent_type, base_agent_id,
                   status, risk_level, latest_version_id, created_at, updated_at
            FROM sa_agent_definition
            WHERE agent_id = ?
            """;
    private static final String SQL_COUNT_DEFINITIONS = """
            SELECT COUNT(1)
            FROM sa_agent_definition
            WHERE tenant_id = ?
            """;
    private static final String SQL_PAGE_DEFINITIONS = """
            SELECT agent_id, tenant_id, name, description, owner_user_id, owner_team, agent_type, base_agent_id,
                   status, risk_level, latest_version_id, created_at, updated_at
            FROM sa_agent_definition
            WHERE tenant_id = ?
            """;
    private static final String SQL_NEXT_VERSION_NO = """
            SELECT COALESCE(MAX(version_no), 0) + 1
            FROM sa_agent_version
            WHERE agent_id = ?
            """;
    private static final String SQL_INSERT_VERSION = """
            INSERT INTO sa_agent_version
            (version_id, agent_id, version_no, instructions, tool_set_json, model_config_json,
             memory_config_json, guardrail_config_json, skill_set_json, published_by, published_at, change_summary)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_LATEST_VERSION = """
            SELECT version_id, agent_id, version_no, instructions, tool_set_json, model_config_json,
                   memory_config_json, guardrail_config_json, skill_set_json, published_by, published_at, change_summary
            FROM sa_agent_version
            WHERE agent_id = ?
            ORDER BY version_no DESC
            LIMIT 1
            """;
    private static final String SQL_FIND_VERSION = """
            SELECT version_id, agent_id, version_no, instructions, tool_set_json, model_config_json,
                   memory_config_json, guardrail_config_json, skill_set_json, published_by, published_at, change_summary
            FROM sa_agent_version
            WHERE agent_id = ? AND version_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentDefinitionRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void create(AgentDefinition definition) {
        AgentDefinition safeDefinition = Objects.requireNonNull(definition, "definition must not be null");
        jdbcTemplate.update(SQL_INSERT_DEFINITION,
                safeDefinition.agentId(),
                safeDefinition.tenantId(),
                safeDefinition.name(),
                safeDefinition.description(),
                safeDefinition.ownerUserId(),
                safeDefinition.ownerTeam(),
                safeDefinition.agentType().name(),
                safeDefinition.baseAgentId(),
                safeDefinition.status().name(),
                safeDefinition.riskLevel().name(),
                safeDefinition.latestVersionId(),
                toTimestamp(safeDefinition.createdAt()),
                toTimestamp(safeDefinition.updatedAt()));
    }

    @Override
    public void update(AgentDefinition definition) {
        AgentDefinition safeDefinition = Objects.requireNonNull(definition, "definition must not be null");
        jdbcTemplate.update(SQL_UPDATE_DEFINITION,
                safeDefinition.tenantId(),
                safeDefinition.name(),
                safeDefinition.description(),
                safeDefinition.ownerUserId(),
                safeDefinition.ownerTeam(),
                safeDefinition.agentType().name(),
                safeDefinition.baseAgentId(),
                safeDefinition.status().name(),
                safeDefinition.riskLevel().name(),
                safeDefinition.latestVersionId(),
                toTimestamp(safeDefinition.createdAt()),
                toTimestamp(safeDefinition.updatedAt()),
                safeDefinition.agentId());
    }

    @Override
    public Optional<AgentDefinition> findById(String agentId) {
        if (!hasText(agentId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_DEFINITION, this::mapDefinition, agentId.trim()).stream().findFirst();
    }

    @Override
    public AgentDefinitionPage page(String tenantId, long current, long size, String keyword) {
        long safeCurrent = current <= 0 ? 1L : current;
        long safeSize = clampSize(size);
        String safeTenantId = defaultTenant(tenantId);
        QueryParts keywordFilter = keywordFilter(keyword);
        List<Object> countArgs = new ArrayList<>();
        countArgs.add(safeTenantId);
        countArgs.addAll(keywordFilter.args());
        Long total = jdbcTemplate.queryForObject(SQL_COUNT_DEFINITIONS + keywordFilter.where(),
                Long.class,
                countArgs.toArray());

        List<Object> pageArgs = new ArrayList<>(countArgs);
        pageArgs.add(safeSize);
        pageArgs.add((safeCurrent - 1) * safeSize);
        List<AgentDefinition> records = jdbcTemplate.query(
                SQL_PAGE_DEFINITIONS + keywordFilter.where() + " ORDER BY updated_at DESC, agent_id ASC LIMIT ? OFFSET ?",
                this::mapDefinition,
                pageArgs.toArray());
        long safeTotal = total == null ? 0L : total;
        long pages = safeTotal == 0L ? 0L : (safeTotal + safeSize - 1L) / safeSize;
        return new AgentDefinitionPage(records, safeTotal, safeSize, safeCurrent, pages);
    }

    @Override
    public long nextVersionNo(String agentId) {
        Long next = jdbcTemplate.queryForObject(SQL_NEXT_VERSION_NO, Long.class, requireText(agentId, "agentId"));
        return next == null ? 1L : next;
    }

    @Override
    public void saveVersion(AgentVersion version) {
        AgentVersion safeVersion = Objects.requireNonNull(version, "version must not be null");
        jdbcTemplate.update(SQL_INSERT_VERSION,
                safeVersion.versionId(),
                safeVersion.agentId(),
                safeVersion.versionNo(),
                safeVersion.instructions(),
                safeVersion.toolSetJson(),
                safeVersion.modelConfigJson(),
                safeVersion.memoryConfigJson(),
                safeVersion.guardrailConfigJson(),
                safeVersion.skillSetJson(),
                safeVersion.publishedBy(),
                toTimestamp(safeVersion.publishedAt()),
                safeVersion.changeSummary());
    }

    @Override
    public Optional<AgentVersion> latestVersion(String agentId) {
        if (!hasText(agentId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_LATEST_VERSION, this::mapVersion, agentId.trim()).stream().findFirst();
    }

    @Override
    public Optional<AgentVersion> findVersion(String agentId, String versionId) {
        if (!hasText(agentId) || !hasText(versionId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_VERSION, this::mapVersion, agentId.trim(), versionId.trim())
                .stream()
                .findFirst();
    }

    private AgentDefinition mapDefinition(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentDefinition(
                resultSet.getString("agent_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("name"),
                resultSet.getString("description"),
                resultSet.getString("owner_user_id"),
                resultSet.getString("owner_team"),
                AgentType.valueOf(resultSet.getString("agent_type")),
                resultSet.getString("base_agent_id"),
                AgentStatus.valueOf(resultSet.getString("status")),
                AgentRiskLevel.valueOf(resultSet.getString("risk_level")),
                resultSet.getString("latest_version_id"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private AgentVersion mapVersion(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentVersion(
                resultSet.getString("version_id"),
                resultSet.getString("agent_id"),
                resultSet.getLong("version_no"),
                resultSet.getString("instructions"),
                resultSet.getString("tool_set_json"),
                resultSet.getString("model_config_json"),
                resultSet.getString("memory_config_json"),
                resultSet.getString("guardrail_config_json"),
                columnExists(resultSet, "skill_set_json") ? resultSet.getString("skill_set_json") : AgentVersion.EMPTY_JSON_OBJECT,
                resultSet.getString("published_by"),
                toInstant(resultSet.getTimestamp("published_at")),
                resultSet.getString("change_summary"));
    }

    private boolean columnExists(ResultSet resultSet, String columnName) {
        try {
            resultSet.findColumn(columnName);
            return true;
        } catch (SQLException ex) {
            return false;
        }
    }

    private QueryParts keywordFilter(String keyword) {
        if (!hasText(keyword)) {
            return new QueryParts("", List.of());
        }
        String value = like(keyword);
        return new QueryParts(" AND (agent_id LIKE ? OR name LIKE ? OR description LIKE ?)",
                List.of(value, value, value));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private long clampSize(long size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String defaultTenant(String tenantId) {
        return hasText(tenantId) ? tenantId.trim() : AgentDefinition.DEFAULT_TENANT_ID;
    }

    private String like(String value) {
        return "%" + value.trim() + "%";
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record QueryParts(String where, List<Object> args) {
    }
}

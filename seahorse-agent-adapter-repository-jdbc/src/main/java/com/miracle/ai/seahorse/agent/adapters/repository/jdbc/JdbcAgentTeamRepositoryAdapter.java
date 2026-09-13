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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamEdge;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamEdgeCondition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamFailurePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMember;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamNodeRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamNodeStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.team.AgentTeamRunStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentTeamRepositoryPort;
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
 * Agent 团队仓储 JDBC 适配器：团队定义存 {@code sa_agent_team}（成员/边为
 * definition_json），团队运行与节点运行存 {@code sa_agent_team_run}。
 */
public class JdbcAgentTeamRepositoryAdapter implements AgentTeamRepositoryPort {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private static final String DEFINITION_COLUMNS = """
            team_id, tenant_id, name, mode, owner_team, supervisor_member_id, status,
            definition_json, created_at, updated_at
            """;
    private static final String SQL_INSERT_DEFINITION = """
            INSERT INTO sa_agent_team
            (team_id, tenant_id, name, mode, owner_team, supervisor_member_id, status,
             definition_json, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_DEFINITION = """
            UPDATE sa_agent_team
            SET tenant_id = ?, name = ?, mode = ?, owner_team = ?, supervisor_member_id = ?,
                status = ?, definition_json = ?, created_at = ?, updated_at = ?
            WHERE team_id = ?
            """;
    private static final String SQL_FIND_DEFINITION = """
            SELECT %s FROM sa_agent_team WHERE team_id = ?
            """.formatted(DEFINITION_COLUMNS);
    private static final String SQL_LIST_DEFINITIONS = """
            SELECT %s FROM sa_agent_team WHERE tenant_id = ? ORDER BY created_at ASC, team_id ASC
            """.formatted(DEFINITION_COLUMNS);

    private static final String RUN_COLUMNS = """
            team_run_id, team_id, tenant_id, user_id, mode, objective, status, parent_run_id,
            summary, error_code, error_message, node_runs_json, started_at, finished_at
            """;
    private static final String SQL_INSERT_RUN = """
            INSERT INTO sa_agent_team_run
            (team_run_id, team_id, tenant_id, user_id, mode, objective, status, parent_run_id,
             summary, error_code, error_message, node_runs_json, started_at, finished_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_RUN = """
            UPDATE sa_agent_team_run
            SET team_id = ?, tenant_id = ?, user_id = ?, mode = ?, objective = ?, status = ?,
                parent_run_id = ?, summary = ?, error_code = ?, error_message = ?,
                node_runs_json = ?, started_at = ?, finished_at = ?
            WHERE team_run_id = ?
            """;
    private static final String SQL_FIND_RUN = """
            SELECT %s FROM sa_agent_team_run WHERE team_run_id = ?
            """.formatted(RUN_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentTeamRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public AgentTeamDefinition saveDefinition(AgentTeamDefinition definition) {
        AgentTeamDefinition safeDefinition = Objects.requireNonNull(definition, "definition must not be null");
        if (findDefinitionById(safeDefinition.teamId()).isPresent()) {
            jdbcTemplate.update(SQL_UPDATE_DEFINITION,
                    safeDefinition.tenantId(),
                    safeDefinition.name(),
                    safeDefinition.mode().name(),
                    safeDefinition.ownerTeam(),
                    nullable(safeDefinition.supervisorMemberId()),
                    safeDefinition.isActive() ? "ACTIVE" : "DISABLED",
                    definitionJson(safeDefinition),
                    toTimestamp(safeDefinition.createdAt()),
                    toTimestamp(safeDefinition.updatedAt()),
                    safeDefinition.teamId());
        } else {
            jdbcTemplate.update(SQL_INSERT_DEFINITION,
                    safeDefinition.teamId(),
                    safeDefinition.tenantId(),
                    safeDefinition.name(),
                    safeDefinition.mode().name(),
                    safeDefinition.ownerTeam(),
                    nullable(safeDefinition.supervisorMemberId()),
                    safeDefinition.isActive() ? "ACTIVE" : "DISABLED",
                    definitionJson(safeDefinition),
                    toTimestamp(safeDefinition.createdAt()),
                    toTimestamp(safeDefinition.updatedAt()));
        }
        return safeDefinition;
    }

    @Override
    public Optional<AgentTeamDefinition> findDefinitionById(String teamId) {
        if (!hasText(teamId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_DEFINITION, this::mapDefinition, teamId.trim()).stream().findFirst();
    }

    @Override
    public List<AgentTeamDefinition> listDefinitions(String tenantId) {
        if (!hasText(tenantId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_DEFINITIONS, this::mapDefinition, tenantId.trim());
    }

    @Override
    public AgentTeamRun saveTeamRun(AgentTeamRun teamRun) {
        AgentTeamRun safeRun = Objects.requireNonNull(teamRun, "teamRun must not be null");
        if (findTeamRunById(safeRun.teamRunId()).isPresent()) {
            return updateTeamRun(safeRun);
        }
        jdbcTemplate.update(SQL_INSERT_RUN,
                safeRun.teamRunId(),
                safeRun.teamId(),
                safeRun.tenantId(),
                nullable(safeRun.userId()),
                safeRun.mode().name(),
                safeRun.objective(),
                safeRun.status().name(),
                nullable(safeRun.parentRunId()),
                nullable(safeRun.summary()),
                nullable(safeRun.errorCode()),
                nullable(safeRun.errorMessage()),
                nodeRunsJson(safeRun.nodeRuns()),
                toTimestamp(safeRun.startedAt()),
                toTimestamp(safeRun.finishedAt()));
        return safeRun;
    }

    @Override
    public Optional<AgentTeamRun> findTeamRunById(String teamRunId) {
        if (!hasText(teamRunId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_RUN, this::mapRun, teamRunId.trim()).stream().findFirst();
    }

    @Override
    public AgentTeamRun updateTeamRun(AgentTeamRun teamRun) {
        AgentTeamRun safeRun = Objects.requireNonNull(teamRun, "teamRun must not be null");
        jdbcTemplate.update(SQL_UPDATE_RUN,
                safeRun.teamId(),
                safeRun.tenantId(),
                nullable(safeRun.userId()),
                safeRun.mode().name(),
                safeRun.objective(),
                safeRun.status().name(),
                nullable(safeRun.parentRunId()),
                nullable(safeRun.summary()),
                nullable(safeRun.errorCode()),
                nullable(safeRun.errorMessage()),
                nodeRunsJson(safeRun.nodeRuns()),
                toTimestamp(safeRun.startedAt()),
                toTimestamp(safeRun.finishedAt()),
                safeRun.teamRunId());
        return safeRun;
    }

    private AgentTeamDefinition mapDefinition(ResultSet resultSet, int rowNum) throws SQLException {
        JsonNode json = readJson(resultSet.getString("definition_json"));
        List<AgentTeamMember> members = new ArrayList<>();
        for (JsonNode member : json.path("members")) {
            members.add(new AgentTeamMember(
                    member.path("memberId").asText(""),
                    member.path("agentId").asText(""),
                    member.path("role").asText(""),
                    member.path("instruction").asText("")));
        }
        List<AgentTeamEdge> edges = new ArrayList<>();
        for (JsonNode edge : json.path("edges")) {
            edges.add(new AgentTeamEdge(
                    edge.path("sourceMemberId").asText(""),
                    edge.path("targetMemberId").asText(""),
                    AgentTeamEdgeCondition.valueOf(edge.path("condition").asText("ALWAYS"))));
        }
        return new AgentTeamDefinition(
                resultSet.getString("team_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("name"),
                AgentTeamMode.valueOf(resultSet.getString("mode")),
                resultSet.getString("owner_team"),
                resultSet.getString("supervisor_member_id"),
                members,
                edges,
                "ACTIVE".equals(resultSet.getString("status")),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")),
                json.hasNonNull("failurePolicy")
                        ? AgentTeamFailurePolicy.valueOf(json.path("failurePolicy").asText("FAIL_FAST"))
                        : null,
                json.hasNonNull("maxRetries") && json.path("maxRetries").isNumber()
                        ? json.path("maxRetries").asInt()
                        : null);
    }

    private AgentTeamRun mapRun(ResultSet resultSet, int rowNum) throws SQLException {
        JsonNode json = readJson(resultSet.getString("node_runs_json"));
        List<AgentTeamNodeRun> nodeRuns = new ArrayList<>();
        for (JsonNode node : json) {
            nodeRuns.add(new AgentTeamNodeRun(
                    node.path("nodeRunId").asText(""),
                    node.path("memberId").asText(""),
                    node.path("agentId").asText(""),
                    node.path("instruction").asText(""),
                    AgentTeamNodeStatus.valueOf(node.path("status").asText("PENDING")),
                    nullableText(node.path("handoffId")),
                    nullableText(node.path("childRunId")),
                    nullableText(node.path("outputSummary")),
                    nullableText(node.path("errorCode")),
                    nullableText(node.path("errorMessage")),
                    instantOr(node, "startedAt", null),
                    instantOr(node, "finishedAt", null)));
        }
        return new AgentTeamRun(
                resultSet.getString("team_run_id"),
                resultSet.getString("team_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("user_id"),
                AgentTeamMode.valueOf(resultSet.getString("mode")),
                resultSet.getString("objective"),
                AgentTeamRunStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("parent_run_id"),
                resultSet.getString("summary"),
                resultSet.getString("error_code"),
                resultSet.getString("error_message"),
                nodeRuns,
                toInstant(resultSet.getTimestamp("started_at")),
                toInstant(resultSet.getTimestamp("finished_at")));
    }

    private String definitionJson(AgentTeamDefinition definition) {
        StringBuilder members = new StringBuilder("[");
        for (int i = 0; i < definition.members().size(); i++) {
            AgentTeamMember member = definition.members().get(i);
            if (i > 0) {
                members.append(',');
            }
            members.append("{\"memberId\":\"").append(escape(member.memberId()))
                    .append("\",\"agentId\":\"").append(escape(member.agentId()))
                    .append("\",\"role\":\"").append(escape(member.role()))
                    .append("\",\"instruction\":\"").append(escape(member.instruction()))
                    .append("\"}");
        }
        members.append(']');
        StringBuilder edges = new StringBuilder("[");
        for (int i = 0; i < definition.edges().size(); i++) {
            AgentTeamEdge edge = definition.edges().get(i);
            if (i > 0) {
                edges.append(',');
            }
            edges.append("{\"sourceMemberId\":\"").append(escape(edge.sourceMemberId()))
                    .append("\",\"targetMemberId\":\"").append(escape(edge.targetMemberId()))
                    .append("\",\"condition\":\"").append(edge.condition().name())
                    .append("\"}");
        }
        edges.append(']');
        String failurePolicy = ",\"failurePolicy\":\"" + definition.failurePolicy().name() + "\"";
        String maxRetries = definition.maxRetries() > 0
                ? ",\"maxRetries\":" + definition.maxRetries()
                : "";
        return "{\"members\":" + members + ",\"edges\":" + edges + failurePolicy + maxRetries + "}";
    }

    private String nodeRunsJson(List<AgentTeamNodeRun> nodeRuns) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < nodeRuns.size(); i++) {
            AgentTeamNodeRun node = nodeRuns.get(i);
            if (i > 0) {
                json.append(',');
            }
            json.append("{\"nodeRunId\":\"").append(escape(node.nodeRunId()))
                    .append("\",\"memberId\":\"").append(escape(node.memberId()))
                    .append("\",\"agentId\":\"").append(escape(node.agentId()))
                    .append("\",\"instruction\":\"").append(escape(node.instruction()))
                    .append("\",\"status\":\"").append(node.status().name())
                    .append("\",\"handoffId\":\"").append(escape(nullable(node.handoffId())))
                    .append("\",\"childRunId\":\"").append(escape(nullable(node.childRunId())))
                    .append("\",\"outputSummary\":\"").append(escape(node.outputSummary()))
                    .append("\",\"errorCode\":\"").append(escape(node.errorCode()))
                    .append("\",\"errorMessage\":\"").append(escape(node.errorMessage()))
                    .append("\",\"startedAt\":\"").append(node.startedAt() == null ? "" : node.startedAt())
                    .append("\",\"finishedAt\":\"").append(node.finishedAt() == null ? "" : node.finishedAt())
                    .append("\"}");
        }
        return json.append(']').toString();
    }

    private JsonNode readJson(String value) {
        try {
            return OBJECT_MAPPER.readTree(value == null || value.isBlank() ? "[]" : value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Failed to parse team JSON payload", ex);
        }
    }

    private String escape(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\n", "\\n").replace("\r", "\\r").replace("\t", "\\t");
    }

    private String nullableText(JsonNode node) {
        String text = node.asText("");
        return text.isEmpty() ? null : text;
    }

    private Instant instantOr(JsonNode node, String field, Instant fallback) {
        String text = node.path(field).asText("");
        return text.isEmpty() ? fallback : Instant.parse(text);
    }

    private String nullable(String value) {
        return value == null || value.isBlank() ? null : value;
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

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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.AgentToolBinding;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Agent 工具绑定 JDBC 仓储适配器，负责保存发布版本的工具绑定快照。
 */
public class JdbcAgentToolBindingRepositoryAdapter implements AgentToolBindingRepositoryPort {

    private static final String SQL_DELETE_VERSION_BINDINGS = """
            DELETE FROM sa_agent_tool_binding
            WHERE agent_id = ? AND version_id = ?
            """;
    private static final String SQL_INSERT_BINDING = """
            INSERT INTO sa_agent_tool_binding
            (id, agent_id, version_id, tool_id, max_calls_per_run, argument_policy_json, created_by, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_LIST_BINDINGS = """
            SELECT id, agent_id, version_id, tool_id, max_calls_per_run, argument_policy_json, created_by, created_at
            FROM sa_agent_tool_binding
            WHERE agent_id = ? AND version_id = ?
            ORDER BY created_at ASC, tool_id ASC
            """;
    private static final String SQL_FIND_BINDING = """
            SELECT id, agent_id, version_id, tool_id, max_calls_per_run, argument_policy_json, created_by, created_at
            FROM sa_agent_tool_binding
            WHERE agent_id = ? AND version_id = ? AND tool_id = ?
            LIMIT 1
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentToolBindingRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void saveBindings(String agentId, String versionId, List<AgentToolBinding> bindings) {
        String safeAgentId = requireText(agentId, "agentId");
        String safeVersionId = requireText(versionId, "versionId");
        List<AgentToolBinding> safeBindings = bindings == null ? List.of() : List.copyOf(bindings);

        // 保存绑定采用版本快照语义：先清空当前版本旧绑定，再写入新的工具集合。
        jdbcTemplate.update(SQL_DELETE_VERSION_BINDINGS, safeAgentId, safeVersionId);
        for (AgentToolBinding binding : safeBindings) {
            insertBinding(safeAgentId, safeVersionId, binding);
        }
    }

    @Override
    public List<AgentToolBinding> listBindings(String agentId, String versionId) {
        if (!hasText(agentId) || !hasText(versionId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_BINDINGS, this::mapBinding, agentId.trim(), versionId.trim());
    }

    @Override
    public Optional<AgentToolBinding> findBinding(String agentId, String versionId, String toolId) {
        if (!hasText(agentId) || !hasText(versionId) || !hasText(toolId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BINDING, this::mapBinding,
                agentId.trim(), versionId.trim(), toolId.trim()).stream().findFirst();
    }

    private void insertBinding(String agentId, String versionId, AgentToolBinding binding) {
        AgentToolBinding safeBinding = Objects.requireNonNull(binding, "binding must not be null");
        if (!agentId.equals(safeBinding.agentId()) || !versionId.equals(safeBinding.versionId())) {
            throw new IllegalArgumentException("binding must match agentId and versionId");
        }
        jdbcTemplate.update(SQL_INSERT_BINDING,
                safeBinding.bindingId(),
                safeBinding.agentId(),
                safeBinding.versionId(),
                safeBinding.toolId(),
                safeBinding.maxCallsPerRun(),
                safeBinding.argumentPolicyJson(),
                safeBinding.createdBy(),
                toTimestamp(safeBinding.createdAt()));
    }

    private AgentToolBinding mapBinding(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentToolBinding(
                resultSet.getString("id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                resultSet.getString("tool_id"),
                resultSet.getInt("max_calls_per_run"),
                resultSet.getString("argument_policy_json"),
                resultSet.getString("created_by"),
                toInstant(resultSet.getTimestamp("created_at")));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
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
}

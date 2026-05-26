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

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplateId;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplateStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentTemplateRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentTemplateRepositoryAdapter implements AgentTemplateRepositoryPort {

    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final String TEMPLATE_COLUMNS = """
            template_id, status, name, description, agent_type, risk_cap, allowed_tool_ids_json,
            base_instructions, guardrail_config_json
            """;
    private static final String SQL_LIST = """
            SELECT %s
            FROM sa_agent_template
            """.formatted(TEMPLATE_COLUMNS);
    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_agent_template
            WHERE template_id = ?
            """.formatted(TEMPLATE_COLUMNS);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAgentTemplateRepositoryAdapter(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcAgentTemplateRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<AgentTemplate> list(boolean includeDisabled) {
        String where = includeDisabled ? "" : " WHERE status = ?";
        Object[] args = includeDisabled ? new Object[0] : new Object[] {AgentTemplateStatus.ENABLED.name()};
        return jdbcTemplate.query(SQL_LIST + where + " ORDER BY template_id ASC", this::mapTemplate, args);
    }

    @Override
    public Optional<AgentTemplate> findById(AgentTemplateId templateId) {
        if (templateId == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::mapTemplate, templateId.value()).stream().findFirst();
    }

    private AgentTemplate mapTemplate(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentTemplate(
                AgentTemplateId.fromValue(resultSet.getString("template_id")),
                AgentTemplateStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("name"),
                resultSet.getString("description"),
                AgentType.valueOf(resultSet.getString("agent_type")),
                AgentRiskLevel.valueOf(resultSet.getString("risk_cap")),
                readToolIds(resultSet.getString("allowed_tool_ids_json")),
                resultSet.getString("base_instructions"),
                resultSet.getString("guardrail_config_json"));
    }

    private List<String> readToolIds(String json) {
        try {
            return objectMapper.readValue(json, STRING_LIST_TYPE);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read agent template tool ids", ex);
        }
    }
}

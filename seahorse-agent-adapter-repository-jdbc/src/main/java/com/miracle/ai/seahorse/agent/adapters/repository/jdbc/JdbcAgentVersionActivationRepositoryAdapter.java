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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentVersionActivation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentVersionActivationType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentVersionActivationRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentVersionActivationRepositoryAdapter implements AgentVersionActivationRepositoryPort {

    private static final String ACTIVATION_COLUMNS = """
            activation_id, tenant_id, agent_id, version_id, activation_type, previous_version_id,
            reason_code, operator_id, created_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_version_activation
            (activation_id, tenant_id, agent_id, version_id, activation_type, previous_version_id,
             reason_code, operator_id, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_ACTIVE = """
            SELECT %s
            FROM sa_agent_version_activation
            WHERE agent_id = ?
            ORDER BY created_at DESC, activation_id DESC
            LIMIT 1
            """.formatted(ACTIVATION_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentVersionActivationRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Optional<AgentVersionActivation> findActive(String agentId) {
        if (!hasText(agentId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_ACTIVE, this::mapActivation, agentId.trim()).stream().findFirst();
    }

    @Override
    public AgentVersionActivation activate(AgentVersionActivation activation) {
        AgentVersionActivation safeActivation = Objects.requireNonNull(activation, "activation must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeActivation.activationId(),
                safeActivation.tenantId(),
                safeActivation.agentId(),
                safeActivation.versionId(),
                safeActivation.activationType().name(),
                safeActivation.previousVersionId(),
                safeActivation.reasonCode().name(),
                safeActivation.operator(),
                toTimestamp(safeActivation.createdAt()));
        return safeActivation;
    }

    private AgentVersionActivation mapActivation(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentVersionActivation(
                resultSet.getString("activation_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                AgentVersionActivationType.valueOf(resultSet.getString("activation_type")),
                resultSet.getString("previous_version_id"),
                AgentRollbackReasonCode.valueOf(resultSet.getString("reason_code")),
                resultSet.getString("operator_id"),
                toInstant(resultSet.getTimestamp("created_at")));
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

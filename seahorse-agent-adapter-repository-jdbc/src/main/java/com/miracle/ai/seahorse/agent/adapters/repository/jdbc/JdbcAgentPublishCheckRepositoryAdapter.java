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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentPublishCheckRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentPublishCheckRepositoryAdapter implements AgentPublishCheckRepositoryPort {

    private static final String SYSTEM_CHECKED_BY = "system";
    private static final TypeReference<List<CheckItemRow>> ITEM_LIST_TYPE = new TypeReference<>() {
    };
    private static final String CHECK_COLUMNS = """
            check_id, agent_id, version_id, status, result_json, checked_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_publish_check
            (check_id, agent_id, version_id, status, result_json, checked_by, checked_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_LATEST = """
            SELECT %s
            FROM sa_agent_publish_check
            WHERE agent_id = ?
            ORDER BY checked_at DESC, check_id DESC
            LIMIT 1
            """.formatted(CHECK_COLUMNS);

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAgentPublishCheckRepositoryAdapter(DataSource dataSource) {
        this(dataSource, new ObjectMapper());
    }

    public JdbcAgentPublishCheckRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public AgentPublishCheckReport save(AgentPublishCheckReport report) {
        AgentPublishCheckReport safeReport = Objects.requireNonNull(report, "report must not be null");
        jdbcTemplate.update(SQL_INSERT,
                safeReport.checkId(),
                safeReport.agentId(),
                safeReport.versionId(),
                safeReport.status().name(),
                writeItems(safeReport.items()),
                SYSTEM_CHECKED_BY,
                toTimestamp(safeReport.checkedAt()));
        return safeReport;
    }

    @Override
    public Optional<AgentPublishCheckReport> latest(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_LATEST, this::mapReport, agentId.trim()).stream().findFirst();
    }

    private AgentPublishCheckReport mapReport(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentPublishCheckReport(
                resultSet.getString("check_id"),
                resultSet.getString("agent_id"),
                resultSet.getString("version_id"),
                AgentPublishCheckStatus.valueOf(resultSet.getString("status")),
                readItems(resultSet.getString("result_json")),
                toInstant(resultSet.getTimestamp("checked_at")));
    }

    private String writeItems(List<AgentPublishCheckItem> items) {
        try {
            List<CheckItemRow> rows = items.stream()
                    .map(CheckItemRow::from)
                    .toList();
            return objectMapper.writeValueAsString(rows);
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot write agent publish check items", ex);
        }
    }

    private List<AgentPublishCheckItem> readItems(String json) {
        try {
            return objectMapper.readValue(json, ITEM_LIST_TYPE).stream()
                    .map(CheckItemRow::toDomain)
                    .toList();
        } catch (Exception ex) {
            throw new IllegalStateException("Cannot read agent publish check items", ex);
        }
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private record CheckItemRow(String code, String status, String message) {

        private static CheckItemRow from(AgentPublishCheckItem item) {
            return new CheckItemRow(item.code().name(), item.status().name(), item.message());
        }

        private AgentPublishCheckItem toDomain() {
            return new AgentPublishCheckItem(
                    AgentPublishCheckCode.valueOf(code),
                    AgentPublishCheckStatus.valueOf(status),
                    message);
        }
    }
}

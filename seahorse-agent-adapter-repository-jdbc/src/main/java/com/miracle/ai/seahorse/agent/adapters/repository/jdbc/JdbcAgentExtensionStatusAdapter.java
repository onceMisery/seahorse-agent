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
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatusPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * 基于 {@code t_agent_extension_status} 的插件状态 JDBC adapter。
 */
public class JdbcAgentExtensionStatusAdapter implements AgentExtensionStatusPort {

    private static final TypeReference<Set<String>> CAPABILITIES_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<Map<String, Object>> DETAILS_TYPE = new TypeReference<>() {
    };
    private static final String SQL_LIST = """
            SELECT extension_name, port_type, feature_type, version, enabled, healthy,
                   capabilities_json, message, last_error, details_json, updated_by, update_time
            FROM t_agent_extension_status
            WHERE deleted = 0
            ORDER BY port_type ASC, extension_name ASC
            """;
    private static final String SQL_COUNT = """
            SELECT COUNT(1)
            FROM t_agent_extension_status
            WHERE extension_name = ? AND port_type = ? AND deleted = 0
            """;
    private static final String SQL_INSERT = """
            INSERT INTO t_agent_extension_status
            (extension_name, port_type, feature_type, version, enabled, healthy,
             capabilities_json, message, last_error, details_json, updated_by, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String SQL_UPDATE = """
            UPDATE t_agent_extension_status
            SET feature_type = ?, version = ?, enabled = ?, healthy = ?, capabilities_json = ?,
                message = ?, last_error = ?, details_json = ?, updated_by = ?, update_time = ?
            WHERE extension_name = ? AND port_type = ? AND deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAgentExtensionStatusAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public List<AgentExtensionStatus> listStatuses() {
        return jdbcTemplate.query(SQL_LIST, this::mapStatus);
    }

    @Override
    public void saveStatus(AgentExtensionStatus status) {
        AgentExtensionStatus safeStatus = Objects.requireNonNull(status, "status must not be null");
        if (countStatus(safeStatus) > 0) {
            updateStatus(safeStatus);
            return;
        }
        insertStatus(safeStatus);
    }

    private int countStatus(AgentExtensionStatus status) {
        Integer count = jdbcTemplate.queryForObject(SQL_COUNT, Integer.class, status.name(), status.portType());
        return count == null ? 0 : count;
    }

    private void insertStatus(AgentExtensionStatus status) {
        jdbcTemplate.update(SQL_INSERT,
                status.name(),
                status.portType(),
                status.featureType(),
                status.version(),
                status.enabled(),
                status.healthy(),
                writeJson(status.capabilities()),
                status.message(),
                status.lastError(),
                writeJson(status.details()),
                status.updatedBy(),
                Timestamp.from(resolveUpdatedAt(status)));
    }

    private void updateStatus(AgentExtensionStatus status) {
        jdbcTemplate.update(SQL_UPDATE,
                status.featureType(),
                status.version(),
                status.enabled(),
                status.healthy(),
                writeJson(status.capabilities()),
                status.message(),
                status.lastError(),
                writeJson(status.details()),
                status.updatedBy(),
                Timestamp.from(resolveUpdatedAt(status)),
                status.name(),
                status.portType());
    }

    private AgentExtensionStatus mapStatus(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentExtensionStatus(
                resultSet.getString("extension_name"),
                resultSet.getString("port_type"),
                resultSet.getString("feature_type"),
                resultSet.getString("version"),
                resultSet.getBoolean("enabled"),
                resultSet.getBoolean("healthy"),
                readJson(resultSet.getString("capabilities_json"), CAPABILITIES_TYPE, Set.of()),
                resultSet.getString("message"),
                resultSet.getString("last_error"),
                readJson(resultSet.getString("details_json"), DETAILS_TYPE, Map.of()),
                resultSet.getString("updated_by"),
                toInstant(resultSet.getTimestamp("update_time")));
    }

    private Instant resolveUpdatedAt(AgentExtensionStatus status) {
        return Instant.EPOCH.equals(status.updatedAt()) ? Instant.now() : status.updatedAt();
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? Instant.EPOCH : timestamp.toInstant();
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("serialize extension status failed", ex);
        }
    }

    private <T> T readJson(String value, TypeReference<T> type, T fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return objectMapper.readValue(value, type);
        } catch (JsonProcessingException ex) {
            return fallback;
        }
    }
}

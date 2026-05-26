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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.Connector;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperationStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiHttpMethod;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
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

public class JdbcConnectorRepositoryAdapter implements ConnectorRepositoryPort {

    private static final long MAX_PAGE_SIZE = 100L;

    private static final String CONNECTOR_COLUMNS = """
            connector_id, tenant_id, provider, name, description, status, created_by, created_at, updated_at
            """;
    private static final String VERSION_COLUMNS = """
            connector_version_id, connector_id, spec_hash, spec_json, imported_by, imported_at
            """;
    private static final String OPERATION_COLUMNS = """
            operation_id, connector_id, connector_version_id, operation_key, original_operation_id, method, path,
            summary, description, schema_json, output_schema_json, tool_id, risk_level, action_type, resource_type,
            auth_type, status, requires_approval, created_at, updated_at
            """;

    private static final String SQL_INSERT_CONNECTOR = """
            INSERT INTO sa_connector
            (connector_id, tenant_id, provider, name, description, status, created_by, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_CONNECTOR = """
            UPDATE sa_connector
            SET tenant_id = ?,
                provider = ?,
                name = ?,
                description = ?,
                status = ?,
                created_by = ?,
                created_at = ?,
                updated_at = ?
            WHERE connector_id = ?
            """;
    private static final String SQL_FIND_CONNECTOR = """
            SELECT %s
            FROM sa_connector
            WHERE connector_id = ?
            """.formatted(CONNECTOR_COLUMNS);
    private static final String SQL_COUNT_CONNECTORS = """
            SELECT COUNT(1)
            FROM sa_connector
            WHERE 1 = 1
            """;
    private static final String SQL_PAGE_CONNECTORS = """
            SELECT %s
            FROM sa_connector
            WHERE 1 = 1
            """.formatted(CONNECTOR_COLUMNS);

    private static final String SQL_INSERT_VERSION = """
            INSERT INTO sa_connector_version
            (connector_version_id, connector_id, spec_hash, spec_json, imported_by, imported_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_VERSION_BY_ID = """
            SELECT %s
            FROM sa_connector_version
            WHERE connector_version_id = ?
            """.formatted(VERSION_COLUMNS);
    private static final String SQL_FIND_VERSION_BY_CONNECTOR_HASH = """
            SELECT %s
            FROM sa_connector_version
            WHERE connector_id = ? AND spec_hash = ?
            """.formatted(VERSION_COLUMNS);

    private static final String SQL_INSERT_OPERATION = """
            INSERT INTO sa_connector_operation
            (operation_id, connector_id, connector_version_id, operation_key, original_operation_id, method, path,
             summary, description, schema_json, output_schema_json, tool_id, risk_level, action_type, resource_type,
             auth_type, status, requires_approval, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_OPERATION = """
            UPDATE sa_connector_operation
            SET connector_id = ?,
                connector_version_id = ?,
                operation_key = ?,
                original_operation_id = ?,
                method = ?,
                path = ?,
                summary = ?,
                description = ?,
                schema_json = ?,
                output_schema_json = ?,
                tool_id = ?,
                risk_level = ?,
                action_type = ?,
                resource_type = ?,
                auth_type = ?,
                status = ?,
                requires_approval = ?,
                created_at = ?,
                updated_at = ?
            WHERE operation_id = ?
            """;
    private static final String SQL_FIND_OPERATION = """
            SELECT %s
            FROM sa_connector_operation
            WHERE connector_id = ? AND operation_id = ?
            """.formatted(OPERATION_COLUMNS);
    private static final String SQL_LIST_OPERATIONS = """
            SELECT %s
            FROM sa_connector_operation
            WHERE connector_id = ?
            ORDER BY created_at ASC, operation_id ASC
            """.formatted(OPERATION_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcConnectorRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Connector saveConnector(Connector connector) {
        Connector safeConnector = Objects.requireNonNull(connector, "connector must not be null");
        if (findConnectorById(safeConnector.connectorId()).isPresent()) {
            updateConnector(safeConnector);
            return safeConnector;
        }
        insertConnector(safeConnector);
        return safeConnector;
    }

    @Override
    public Optional<Connector> findConnectorById(String connectorId) {
        if (!hasText(connectorId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_CONNECTOR, this::mapConnector, connectorId.trim()).stream().findFirst();
    }

    @Override
    public Optional<ConnectorVersion> findVersionByConnectorIdAndSpecHash(String connectorId, String specHash) {
        if (!hasText(connectorId) || !hasText(specHash)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_VERSION_BY_CONNECTOR_HASH, this::mapVersion,
                connectorId.trim(), specHash.trim()).stream().findFirst();
    }

    @Override
    public ConnectorVersion saveVersion(ConnectorVersion version) {
        ConnectorVersion safeVersion = Objects.requireNonNull(version, "version must not be null");
        if (findVersionById(safeVersion.connectorVersionId()).isPresent()) {
            return safeVersion;
        }
        jdbcTemplate.update(SQL_INSERT_VERSION,
                safeVersion.connectorVersionId(),
                safeVersion.connectorId(),
                safeVersion.specHash(),
                safeVersion.specJson(),
                safeVersion.importedBy(),
                toTimestamp(safeVersion.importedAt()));
        return safeVersion;
    }

    @Override
    public ConnectorOperation saveOperation(ConnectorOperation operation) {
        ConnectorOperation safeOperation = Objects.requireNonNull(operation, "operation must not be null");
        if (findOperation(safeOperation.connectorId(), safeOperation.operationId()).isPresent()) {
            updateOperation(safeOperation);
            return safeOperation;
        }
        insertOperation(safeOperation);
        return safeOperation;
    }

    @Override
    public Optional<ConnectorOperation> findOperation(String connectorId, String operationId) {
        if (!hasText(connectorId) || !hasText(operationId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_OPERATION, this::mapOperation,
                connectorId.trim(), operationId.trim()).stream().findFirst();
    }

    @Override
    public List<ConnectorOperation> listOperations(String connectorId) {
        if (!hasText(connectorId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_OPERATIONS, this::mapOperation, connectorId.trim());
    }

    @Override
    public ConnectorPage page(ConnectorQuery query) {
        ConnectorQuery safeQuery = query == null
                ? new ConnectorQuery(null, null, null,
                ConnectorQuery.DEFAULT_CURRENT, ConnectorQuery.DEFAULT_PAGE_SIZE)
                : query;
        long current = safeQuery.current();
        long size = clampSize(safeQuery.size());
        QueryParts filters = filters(safeQuery);

        Long total = jdbcTemplate.queryForObject(
                SQL_COUNT_CONNECTORS + filters.where(), Long.class, filters.args().toArray());
        long safeTotal = total == null ? 0L : total;

        List<Object> pageArgs = new ArrayList<>(filters.args());
        pageArgs.add(size);
        pageArgs.add((current - 1L) * size);
        List<Connector> records = jdbcTemplate.query(
                SQL_PAGE_CONNECTORS + filters.where() + " ORDER BY updated_at DESC, connector_id ASC LIMIT ? OFFSET ?",
                this::mapConnector,
                pageArgs.toArray());
        return new ConnectorPage(records, safeTotal, size, current, pages(safeTotal, size));
    }

    private void insertConnector(Connector connector) {
        jdbcTemplate.update(SQL_INSERT_CONNECTOR,
                connector.connectorId(),
                connector.tenantId(),
                connector.provider().name(),
                connector.name(),
                connector.description(),
                connector.status().name(),
                connector.createdBy(),
                toTimestamp(connector.createdAt()),
                toTimestamp(connector.updatedAt()));
    }

    private void updateConnector(Connector connector) {
        jdbcTemplate.update(SQL_UPDATE_CONNECTOR,
                connector.tenantId(),
                connector.provider().name(),
                connector.name(),
                connector.description(),
                connector.status().name(),
                connector.createdBy(),
                toTimestamp(connector.createdAt()),
                toTimestamp(connector.updatedAt()),
                connector.connectorId());
    }

    private Optional<ConnectorVersion> findVersionById(String connectorVersionId) {
        if (!hasText(connectorVersionId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_VERSION_BY_ID, this::mapVersion,
                connectorVersionId.trim()).stream().findFirst();
    }

    private void insertOperation(ConnectorOperation operation) {
        jdbcTemplate.update(SQL_INSERT_OPERATION,
                operation.operationId(),
                operation.connectorId(),
                operation.connectorVersionId(),
                operation.operationKey(),
                operation.originalOperationId(),
                operation.method().name(),
                operation.path(),
                operation.summary(),
                operation.description(),
                operation.schemaJson(),
                operation.outputSchemaJson(),
                operation.toolId(),
                operation.riskLevel().name(),
                operation.actionType().name(),
                operation.resourceType(),
                operation.authType().name(),
                operation.status().name(),
                operation.requiresApproval(),
                toTimestamp(operation.createdAt()),
                toTimestamp(operation.updatedAt()));
    }

    private void updateOperation(ConnectorOperation operation) {
        jdbcTemplate.update(SQL_UPDATE_OPERATION,
                operation.connectorId(),
                operation.connectorVersionId(),
                operation.operationKey(),
                operation.originalOperationId(),
                operation.method().name(),
                operation.path(),
                operation.summary(),
                operation.description(),
                operation.schemaJson(),
                operation.outputSchemaJson(),
                operation.toolId(),
                operation.riskLevel().name(),
                operation.actionType().name(),
                operation.resourceType(),
                operation.authType().name(),
                operation.status().name(),
                operation.requiresApproval(),
                toTimestamp(operation.createdAt()),
                toTimestamp(operation.updatedAt()),
                operation.operationId());
    }

    private Connector mapConnector(ResultSet resultSet, int rowNum) throws SQLException {
        return new Connector(
                resultSet.getString("connector_id"),
                resultSet.getString("tenant_id"),
                ConnectorProvider.valueOf(resultSet.getString("provider")),
                resultSet.getString("name"),
                resultSet.getString("description"),
                ConnectorStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("created_by"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private ConnectorVersion mapVersion(ResultSet resultSet, int rowNum) throws SQLException {
        return new ConnectorVersion(
                resultSet.getString("connector_version_id"),
                resultSet.getString("connector_id"),
                resultSet.getString("spec_hash"),
                resultSet.getString("spec_json"),
                resultSet.getString("imported_by"),
                toInstant(resultSet.getTimestamp("imported_at")));
    }

    private ConnectorOperation mapOperation(ResultSet resultSet, int rowNum) throws SQLException {
        return new ConnectorOperation(
                resultSet.getString("operation_id"),
                resultSet.getString("connector_id"),
                resultSet.getString("connector_version_id"),
                resultSet.getString("operation_key"),
                resultSet.getString("original_operation_id"),
                OpenApiHttpMethod.valueOf(resultSet.getString("method")),
                resultSet.getString("path"),
                resultSet.getString("summary"),
                resultSet.getString("description"),
                resultSet.getString("schema_json"),
                resultSet.getString("output_schema_json"),
                resultSet.getString("tool_id"),
                ToolRiskLevel.valueOf(resultSet.getString("risk_level")),
                ToolActionType.valueOf(resultSet.getString("action_type")),
                resultSet.getString("resource_type"),
                CredentialAuthType.valueOf(resultSet.getString("auth_type")),
                ConnectorOperationStatus.valueOf(resultSet.getString("status")),
                resultSet.getBoolean("requires_approval"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private QueryParts filters(ConnectorQuery query) {
        List<String> clauses = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addTextFilter(clauses, args, "tenant_id", query.tenantId());
        if (hasText(query.keyword())) {
            String keyword = like(query.keyword());
            clauses.add("(LOWER(connector_id) LIKE ? OR LOWER(name) LIKE ? OR LOWER(description) LIKE ?)");
            args.add(keyword);
            args.add(keyword);
            args.add(keyword);
        }
        if (query.status() != null) {
            clauses.add("status = ?");
            args.add(query.status().name());
        }
        if (clauses.isEmpty()) {
            return new QueryParts("", List.of());
        }
        return new QueryParts(" AND " + String.join(" AND ", clauses), args);
    }

    private void addTextFilter(List<String> clauses, List<Object> args, String column, String value) {
        if (!hasText(value)) {
            return;
        }
        clauses.add(column + " = ?");
        args.add(value.trim());
    }

    private long clampSize(long size) {
        if (size <= 0L) {
            return ConnectorQuery.DEFAULT_PAGE_SIZE;
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
        return "%" + value.trim().toLowerCase() + "%";
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

        private QueryParts {
            args = List.copyOf(args);
        }
    }
}

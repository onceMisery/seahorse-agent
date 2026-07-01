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
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConnectorRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");

    @Test
    void shouldSaveFindPageAndListConnectorOperations() {
        DriverManagerDataSource dataSource = dataSource("connector-repository");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createConnectorSchema(jdbcTemplate);
        JdbcConnectorRepositoryAdapter adapter = new JdbcConnectorRepositoryAdapter(dataSource);

        Connector connector = adapter.saveConnector(connector("conn-1", "CRM API", ConnectorStatus.IMPORTED));
        adapter.saveConnector(connector("conn-2", "ERP API", ConnectorStatus.DISABLED));
        ConnectorVersion version = adapter.saveVersion(version("connv-1", connector.connectorId(), "hash-1"));
        adapter.saveOperation(operation("op-1", connector.connectorId(), version.connectorVersionId(),
                OpenApiHttpMethod.GET, ConnectorOperationStatus.DISABLED));
        ConnectorOperation enabled = adapter.saveOperation(operation("op-2", connector.connectorId(),
                version.connectorVersionId(), OpenApiHttpMethod.DELETE, ConnectorOperationStatus.ENABLED));

        ConnectorPage page = adapter.page(new ConnectorQuery("tenant-1", "crm", null, 1L, 10L));
        List<ConnectorOperation> operations = adapter.listOperations(connector.connectorId());

        assertThat(adapter.findConnectorById("conn-1")).contains(connector);
        assertThat(adapter.findVersionByConnectorIdAndSpecHash("conn-1", "hash-1")).contains(version);
        assertThat(adapter.findOperation("conn-1", "op-2")).contains(enabled);
        assertThat(adapter.findOperationByToolId(enabled.toolId())).contains(enabled);
        assertThat(page.records()).extracting(Connector::connectorId).containsExactly("conn-1");
        assertThat(operations).extracting(ConnectorOperation::operationId).containsExactly("op-1", "op-2");
    }

    @Test
    void shouldPersistConnectorOperationAuthType() {
        DriverManagerDataSource dataSource = dataSource("connector-operation-auth-type");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createConnectorSchema(jdbcTemplate);
        JdbcConnectorRepositoryAdapter adapter = new JdbcConnectorRepositoryAdapter(dataSource);
        Connector connector = adapter.saveConnector(connector("conn-auth", "Authenticated API", ConnectorStatus.IMPORTED));
        ConnectorVersion version = adapter.saveVersion(version("connv-auth", connector.connectorId(), "hash-auth"));
        ConnectorOperation authenticated = new ConnectorOperation(
                "op-auth",
                connector.connectorId(),
                version.connectorVersionId(),
                "GET /secure-customers",
                "listSecureCustomers",
                OpenApiHttpMethod.GET,
                "/secure-customers",
                "List secure customers",
                "operation description",
                "{\"type\":\"object\"}",
                null,
                "tool-op-auth",
                ToolRiskLevel.LOW,
                ToolActionType.READ,
                "CRM_CUSTOMER",
                CredentialAuthType.STATIC_BEARER,
                ConnectorOperationStatus.DISABLED,
                false,
                NOW,
                NOW.plusSeconds(1));

        adapter.saveOperation(authenticated);

        assertThat(adapter.findOperation(connector.connectorId(), "op-auth"))
                .get()
                .extracting(ConnectorOperation::authType)
                .isEqualTo(CredentialAuthType.STATIC_BEARER);
    }

    private static Connector connector(String connectorId, String name, ConnectorStatus status) {
        return new Connector(
                connectorId,
                "tenant-1",
                ConnectorProvider.OPENAPI,
                name,
                name + " description",
                status,
                "admin-1",
                NOW,
                NOW);
    }

    private static ConnectorVersion version(String connectorVersionId, String connectorId, String specHash) {
        return new ConnectorVersion(
                connectorVersionId,
                connectorId,
                specHash,
                "{\"openapi\":\"3.0.3\"}",
                "admin-1",
                NOW);
    }

    private static ConnectorOperation operation(String operationId,
                                                String connectorId,
                                                String connectorVersionId,
                                                OpenApiHttpMethod method,
                                                ConnectorOperationStatus status) {
        boolean delete = method == OpenApiHttpMethod.DELETE;
        return new ConnectorOperation(
                operationId,
                connectorId,
                connectorVersionId,
                method.name() + " /customers",
                method == OpenApiHttpMethod.GET ? "listCustomers" : "deleteCustomer",
                method,
                method == OpenApiHttpMethod.GET ? "/customers" : "/customers/{customerId}",
                method == OpenApiHttpMethod.GET ? "List customers" : "Delete customer",
                "operation description",
                "{\"type\":\"object\"}",
                null,
                "tool-" + operationId,
                delete ? ToolRiskLevel.HIGH : ToolRiskLevel.LOW,
                delete ? ToolActionType.DELETE : ToolActionType.READ,
                "CRM_CUSTOMER",
                status,
                delete,
                NOW,
                NOW.plusSeconds(delete ? 2 : 1));
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createConnectorSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_connector (
                    connector_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    provider VARCHAR(32) NOT NULL,
                    name VARCHAR(128) NOT NULL,
                    description VARCHAR(1000),
                    base_url VARCHAR(1024),
                    status VARCHAR(32) NOT NULL,
                    created_by VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_connector_version (
                    connector_version_id VARCHAR(64) PRIMARY KEY,
                    connector_id VARCHAR(64) NOT NULL,
                    spec_hash VARCHAR(128) NOT NULL,
                    spec_json CLOB NOT NULL,
                    imported_by VARCHAR(64) NOT NULL,
                    imported_at TIMESTAMP NOT NULL,
                    UNIQUE(connector_id, spec_hash)
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_connector_operation (
                    operation_id VARCHAR(64) PRIMARY KEY,
                    connector_id VARCHAR(64) NOT NULL,
                    connector_version_id VARCHAR(64) NOT NULL,
                    operation_key VARCHAR(256) NOT NULL,
                    original_operation_id VARCHAR(128),
                    method VARCHAR(16) NOT NULL,
                    path VARCHAR(512) NOT NULL,
                    summary VARCHAR(256),
                    description VARCHAR(1000),
                    schema_json CLOB NOT NULL,
                    output_schema_json CLOB,
                    tool_id VARCHAR(128) NOT NULL,
                    risk_level VARCHAR(32) NOT NULL,
                    action_type VARCHAR(32) NOT NULL,
                    resource_type VARCHAR(64),
                    auth_type VARCHAR(32) NOT NULL DEFAULT 'NONE',
                    status VARCHAR(32) NOT NULL,
                    requires_approval BOOLEAN NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("CREATE INDEX idx_sa_connector_tenant_status ON sa_connector(tenant_id, status)");
        jdbcTemplate.execute("CREATE INDEX idx_sa_connector_operation_connector ON sa_connector_operation(connector_id, updated_at)");
    }
}

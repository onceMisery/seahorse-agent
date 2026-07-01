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

package com.miracle.ai.seahorse.agent.kernel.application.agent.connector;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.Connector;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiHttpMethod;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiSpecDocument;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiSpecOperation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorCredentialBindingCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorOperationDisableCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorOperationEnableCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.OpenApiImportCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OpenApiSpecParseRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OpenApiSpecParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConnectorAdminOnlyTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-05-25T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void connectorManagementShouldRejectNonAdminUsersBeforeMutatingState() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        KernelOpenApiConnectorImportService service = new KernelOpenApiConnectorImportService(
                connectorRepository,
                new StubOpenApiSpecParser(),
                toolCatalogRepository,
                normalUser(),
                CLOCK);

        assertPermissionDenied(() -> service.importSpec(new OpenApiImportCommand(
                "tenant-1", "crm-api", "{\"openapi\":\"3.0.3\"}", "user-1")));
        assertPermissionDenied(() -> service.page(new ConnectorQuery(
                "tenant-1", null, null, 1, 10)));
        assertPermissionDenied(() -> service.listOperations("connector-1"));
        assertPermissionDenied(() -> service.bindCredential(new ConnectorCredentialBindingCommand(
                "connector-1", "operation-1", CredentialAuthType.STATIC_BEARER, "secret-ref-1", "user-1")));
        assertPermissionDenied(() -> service.enableOperation(new ConnectorOperationEnableCommand(
                "connector-1", "operation-1", "approval-policy-1", false)));
        assertPermissionDenied(() -> service.disableOperation(new ConnectorOperationDisableCommand(
                "connector-1", "operation-1", "disabled")));

        assertTrue(connectorRepository.connectors.isEmpty());
        assertTrue(connectorRepository.versions.isEmpty());
        assertTrue(connectorRepository.operations.isEmpty());
        assertTrue(toolCatalogRepository.entries.isEmpty());
    }

    private static void assertPermissionDenied(Runnable call) {
        assertThrows(IllegalStateException.class, call::run);
    }

    private static CurrentUserPort normalUser() {
        return () -> Optional.of(new CurrentUser(1L, "alice", "user", null));
    }

    private static final class StubOpenApiSpecParser implements OpenApiSpecParserPort {

        @Override
        public OpenApiSpecDocument parse(OpenApiSpecParseRequest request) {
            return new OpenApiSpecDocument(
                    "CRM API",
                    "Customer relationship API",
                    List.of(new OpenApiSpecOperation(
                            "listCustomers",
                            OpenApiHttpMethod.GET,
                            "/customers",
                            "List customers",
                            "Read customers",
                            "{\"type\":\"object\"}",
                            "{\"type\":\"object\"}",
                            "CRM_CUSTOMER")));
        }
    }

    private static final class MemoryConnectorRepository implements ConnectorRepositoryPort {

        private final Map<String, Connector> connectors = new LinkedHashMap<>();
        private final Map<String, ConnectorVersion> versions = new LinkedHashMap<>();
        private final Map<String, ConnectorOperation> operations = new LinkedHashMap<>();

        @Override
        public Connector saveConnector(Connector connector) {
            connectors.put(connector.connectorId(), connector);
            return connector;
        }

        @Override
        public Optional<Connector> findConnectorById(String connectorId) {
            return Optional.ofNullable(connectors.get(connectorId));
        }

        @Override
        public Optional<ConnectorVersion> findVersionByConnectorIdAndSpecHash(String connectorId, String specHash) {
            return versions.values().stream()
                    .filter(version -> version.connectorId().equals(connectorId))
                    .filter(version -> version.specHash().equals(specHash))
                    .findFirst();
        }

        @Override
        public ConnectorVersion saveVersion(ConnectorVersion version) {
            versions.put(version.connectorVersionId(), version);
            return version;
        }

        @Override
        public ConnectorOperation saveOperation(ConnectorOperation operation) {
            operations.put(operation.operationId(), operation);
            return operation;
        }

        @Override
        public Optional<ConnectorOperation> findOperation(String connectorId, String operationId) {
            ConnectorOperation operation = operations.get(operationId);
            if (operation == null || !operation.connectorId().equals(connectorId)) {
                return Optional.empty();
            }
            return Optional.of(operation);
        }

        @Override
        public Optional<ConnectorOperation> findOperationByToolId(String toolId) {
            return operations.values().stream()
                    .filter(operation -> operation.toolId().equals(toolId))
                    .findFirst();
        }

        @Override
        public List<ConnectorOperation> listOperations(String connectorId) {
            return operations.values().stream()
                    .filter(operation -> operation.connectorId().equals(connectorId))
                    .toList();
        }

        @Override
        public ConnectorPage page(ConnectorQuery query) {
            List<Connector> records = new ArrayList<>(connectors.values());
            return new ConnectorPage(records, records.size(), query.size(), query.current(), records.isEmpty() ? 0 : 1);
        }
    }

    private static final class MemoryToolCatalogRepository implements ToolCatalogRepositoryPort {

        private final Map<String, ToolCatalogEntry> entries = new LinkedHashMap<>();

        @Override
        public void save(ToolCatalogEntry entry) {
            entries.put(entry.toolId(), entry);
        }

        @Override
        public Optional<ToolCatalogEntry> findById(String toolId) {
            return Optional.ofNullable(entries.get(toolId));
        }

        @Override
        public void setEnabled(String toolId, boolean enabled) {
        }
    }
}

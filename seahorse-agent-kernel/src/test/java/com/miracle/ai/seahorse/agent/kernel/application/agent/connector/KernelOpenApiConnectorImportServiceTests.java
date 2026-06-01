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

import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.Connector;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorCredentialBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorCredentialBindingStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperationStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiHttpMethod;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiSpecDocument;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiSpecOperation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorCredentialBindingCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorImportResult;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorOperationDisableCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ConnectorOperationEnableCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.OpenApiImportCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorCredentialBindingRepositoryPort;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelOpenApiConnectorImportServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String SPEC_JSON = "{\"openapi\":\"3.0.3\"}";

    @Test
    void shouldImportParsedOpenApiSpecAsDisabledConnectorOperations() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        KernelOpenApiConnectorImportService service = service(connectorRepository, toolCatalogRepository);

        ConnectorImportResult result = service.importSpec(new OpenApiImportCommand(
                "tenant-1",
                "crm-api",
                SPEC_JSON,
                "admin-1"));

        List<ConnectorOperation> operations = connectorRepository.listOperations(result.connectorId());

        assertEquals(ConnectorStatus.IMPORTED, result.status());
        assertEquals(2, result.operationCount());
        assertEquals(2, result.disabledOperationCount());
        assertEquals(1, result.highRiskOperationCount());
        assertEquals(2, operations.size());
        assertTrue(operations.stream().allMatch(operation -> operation.status() == ConnectorOperationStatus.DISABLED));
        assertTrue(toolCatalogRepository.entries.isEmpty());
    }

    @Test
    void shouldReturnExistingVersionWhenSameSpecHashIsImportedAgain() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        KernelOpenApiConnectorImportService service = service(connectorRepository, toolCatalogRepository);

        ConnectorImportResult first = service.importSpec(new OpenApiImportCommand(
                "tenant-1",
                "crm-api",
                SPEC_JSON,
                "admin-1"));
        ConnectorImportResult second = service.importSpec(new OpenApiImportCommand(
                "tenant-1",
                "crm-api",
                SPEC_JSON,
                "admin-1"));

        assertEquals(first.connectorId(), second.connectorId());
        assertEquals(first.connectorVersionId(), second.connectorVersionId());
        assertEquals(1, connectorRepository.versions.size());
        assertEquals(2, connectorRepository.listOperations(first.connectorId()).size());
    }

    @Test
    void shouldEnableOperationAndRegisterOpenApiToolCatalogEntry() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        KernelOpenApiConnectorImportService service = service(connectorRepository, toolCatalogRepository);
        ConnectorImportResult result = service.importSpec(new OpenApiImportCommand(
                "tenant-1",
                "crm-api",
                SPEC_JSON,
                "admin-1"));
        ConnectorOperation deleteOperation = connectorRepository.listOperations(result.connectorId()).stream()
                .filter(operation -> operation.method() == OpenApiHttpMethod.DELETE)
                .findFirst()
                .orElseThrow();

        ConnectorOperation enabled = service.enableOperation(new ConnectorOperationEnableCommand(
                result.connectorId(),
                deleteOperation.operationId(),
                "approval-policy-1",
                false));
        ToolCatalogEntry tool = toolCatalogRepository.findById(deleteOperation.toolId()).orElseThrow();

        assertEquals(ConnectorOperationStatus.ENABLED, enabled.status());
        assertEquals(ToolProvider.OPENAPI, tool.provider());
        assertEquals(ToolRiskLevel.HIGH, tool.riskLevel());
        assertEquals(ToolActionType.DELETE, tool.actionType());
        assertTrue(tool.enabled());
        assertTrue(tool.requiresApproval());
        assertEquals(deleteOperation.resourceType(), tool.resourceType());
    }

    @Test
    void shouldRequireActiveCredentialBindingBeforeEnablingAuthenticatedOperation() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        KernelOpenApiConnectorImportService service = service(
                connectorRepository,
                toolCatalogRepository,
                new MemoryConnectorCredentialBindingRepository(),
                null);
        ConnectorOperation operation = importFirstReadOperation(connectorRepository, service);
        ConnectorOperation authenticated = withAuthType(operation, CredentialAuthType.STATIC_BEARER);
        connectorRepository.saveOperation(authenticated);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.enableOperation(new ConnectorOperationEnableCommand(
                        authenticated.connectorId(),
                        authenticated.operationId())));

        assertEquals("active credential binding required", error.getMessage());
        assertTrue(toolCatalogRepository.entries.isEmpty());
    }

    @Test
    void shouldRequireApprovalPolicyBeforeEnablingHighRiskOperation() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        KernelOpenApiConnectorImportService service = service(
                connectorRepository,
                toolCatalogRepository,
                new MemoryConnectorCredentialBindingRepository(),
                null);
        ConnectorOperation deleteOperation = importDeleteOperation(connectorRepository, service);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.enableOperation(new ConnectorOperationEnableCommand(
                        deleteOperation.connectorId(),
                        deleteOperation.operationId())));

        assertEquals("approval policy required", error.getMessage());
        assertTrue(toolCatalogRepository.entries.isEmpty());
    }

    @Test
    void shouldRotatePreviousCredentialBindingWhenBindingIsReplaced() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        MemoryConnectorCredentialBindingRepository bindingRepository = new MemoryConnectorCredentialBindingRepository();
        KernelOpenApiConnectorImportService service = service(
                connectorRepository,
                toolCatalogRepository,
                bindingRepository,
                null);
        ConnectorOperation operation = importFirstReadOperation(connectorRepository, service);

        ConnectorCredentialBinding first = service.bindCredential(new ConnectorCredentialBindingCommand(
                operation.connectorId(),
                operation.operationId(),
                CredentialAuthType.STATIC_BEARER,
                "secret-ref-old",
                "admin-1"));
        ConnectorCredentialBinding second = service.bindCredential(new ConnectorCredentialBindingCommand(
                operation.connectorId(),
                operation.operationId(),
                CredentialAuthType.STATIC_BEARER,
                "secret-ref-new",
                "admin-1"));

        assertEquals(ConnectorCredentialBindingStatus.ROTATED,
                bindingRepository.bindings.get(first.bindingId()).status());
        assertEquals(ConnectorCredentialBindingStatus.ACTIVE, second.status());
        assertEquals("secret-ref-new",
                bindingRepository.findActive("tenant-1", operation.connectorId(), operation.operationId(),
                        CredentialAuthType.STATIC_BEARER).orElseThrow().credentialRef());
    }

    @Test
    void shouldListOnlyActiveCredentialBindingsForOperation() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        KernelOpenApiConnectorImportService service = service(
                connectorRepository,
                toolCatalogRepository,
                new MemoryConnectorCredentialBindingRepository(),
                null);
        ConnectorOperation operation = importFirstReadOperation(connectorRepository, service);
        service.bindCredential(new ConnectorCredentialBindingCommand(
                operation.connectorId(),
                operation.operationId(),
                CredentialAuthType.STATIC_BEARER,
                "secret-ref-old",
                "admin-1"));
        ConnectorCredentialBinding active = service.bindCredential(new ConnectorCredentialBindingCommand(
                operation.connectorId(),
                operation.operationId(),
                CredentialAuthType.STATIC_BEARER,
                "secret-ref-new",
                "admin-1"));

        List<ConnectorCredentialBinding> bindings = service.listActiveCredentialBindings(
                operation.connectorId(),
                operation.operationId());

        assertEquals(List.of(active), bindings);
    }

    @Test
    void shouldDisableOperationIdempotentlyAndDisableToolCatalogEntry() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        KernelOpenApiConnectorImportService service = service(
                connectorRepository,
                toolCatalogRepository,
                new MemoryConnectorCredentialBindingRepository(),
                null);
        ConnectorOperation operation = importFirstReadOperation(connectorRepository, service);
        ConnectorOperation enabled = service.enableOperation(new ConnectorOperationEnableCommand(
                operation.connectorId(),
                operation.operationId()));
        assertTrue(toolCatalogRepository.findById(enabled.toolId()).orElseThrow().enabled());

        ConnectorOperation disabled = service.disableOperation(new ConnectorOperationDisableCommand(
                enabled.connectorId(),
                enabled.operationId(),
                "admin-disabled"));
        ConnectorOperation disabledAgain = service.disableOperation(new ConnectorOperationDisableCommand(
                enabled.connectorId(),
                enabled.operationId(),
                "admin-disabled"));

        assertEquals(ConnectorOperationStatus.DISABLED, disabled.status());
        assertEquals(ConnectorOperationStatus.DISABLED, disabledAgain.status());
        assertFalse(toolCatalogRepository.findById(enabled.toolId()).orElseThrow().enabled());
    }

    @Test
    void shouldWriteRedactedAuditEventsForConnectorImportBindingEnableAndDisable() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        MemoryToolCatalogRepository toolCatalogRepository = new MemoryToolCatalogRepository();
        MemoryConnectorCredentialBindingRepository bindingRepository = new MemoryConnectorCredentialBindingRepository();
        RecordingAuditEventRepository auditRepository = new RecordingAuditEventRepository();
        KernelAuditLedgerService auditLedger = new KernelAuditLedgerService(
                auditRepository,
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED);
        KernelOpenApiConnectorImportService service = service(
                connectorRepository,
                toolCatalogRepository,
                bindingRepository,
                auditLedger);
        ConnectorOperation operation = importDeleteOperation(connectorRepository, service);
        ConnectorOperation authenticated = withAuthType(operation, CredentialAuthType.STATIC_BEARER);
        connectorRepository.saveOperation(authenticated);
        service.bindCredential(new ConnectorCredentialBindingCommand(
                authenticated.connectorId(),
                authenticated.operationId(),
                CredentialAuthType.STATIC_BEARER,
                "secret-ref-1",
                "admin-1"));
        service.enableOperation(new ConnectorOperationEnableCommand(
                authenticated.connectorId(),
                authenticated.operationId(),
                "approval-policy-1",
                false));
        service.disableOperation(new ConnectorOperationDisableCommand(
                authenticated.connectorId(),
                authenticated.operationId(),
                "admin-disabled"));

        List<AuditEventType> eventTypes = auditRepository.events.stream()
                .map(AuditEvent::eventType)
                .toList();
        assertTrue(eventTypes.contains(AuditEventType.CONNECTOR_IMPORTED));
        assertTrue(eventTypes.contains(AuditEventType.CONNECTOR_CREDENTIAL_BOUND));
        assertTrue(eventTypes.contains(AuditEventType.CONNECTOR_OPERATION_ENABLED));
        assertTrue(eventTypes.contains(AuditEventType.CONNECTOR_OPERATION_DISABLED));
        assertTrue(auditRepository.events.stream()
                .map(AuditEvent::redactedPayload)
                .noneMatch(payload -> payload.contains("bearer-token") || payload.contains("secret-value")));
    }

    private static KernelOpenApiConnectorImportService service(ConnectorRepositoryPort connectorRepository,
                                                              ToolCatalogRepositoryPort toolCatalogRepository) {
        return service(connectorRepository, toolCatalogRepository, null, null);
    }

    private static KernelOpenApiConnectorImportService service(ConnectorRepositoryPort connectorRepository,
                                                              ToolCatalogRepositoryPort toolCatalogRepository,
                                                              ConnectorCredentialBindingRepositoryPort bindingRepository,
                                                              KernelAuditLedgerService auditLedger) {
        return new KernelOpenApiConnectorImportService(
                connectorRepository,
                new StubOpenApiSpecParser(),
                toolCatalogRepository,
                bindingRepository,
                auditLedger,
                adminUser(),
                CLOCK);
    }

    private static ConnectorOperation importFirstReadOperation(MemoryConnectorRepository connectorRepository,
                                                               KernelOpenApiConnectorImportService service) {
        ConnectorImportResult result = service.importSpec(new OpenApiImportCommand(
                "tenant-1",
                "crm-api",
                SPEC_JSON,
                "admin-1"));
        return connectorRepository.listOperations(result.connectorId()).stream()
                .filter(operation -> operation.method() == OpenApiHttpMethod.GET)
                .findFirst()
                .orElseThrow();
    }

    private static ConnectorOperation importDeleteOperation(MemoryConnectorRepository connectorRepository,
                                                            KernelOpenApiConnectorImportService service) {
        ConnectorImportResult result = service.importSpec(new OpenApiImportCommand(
                "tenant-1",
                "crm-api",
                SPEC_JSON,
                "admin-1"));
        return connectorRepository.listOperations(result.connectorId()).stream()
                .filter(operation -> operation.method() == OpenApiHttpMethod.DELETE)
                .findFirst()
                .orElseThrow();
    }

    private static ConnectorOperation withAuthType(ConnectorOperation operation, CredentialAuthType authType) {
        return new ConnectorOperation(
                operation.operationId(),
                operation.connectorId(),
                operation.connectorVersionId(),
                operation.operationKey(),
                operation.originalOperationId(),
                operation.method(),
                operation.path(),
                operation.summary(),
                operation.description(),
                operation.schemaJson(),
                operation.outputSchemaJson(),
                operation.toolId(),
                operation.riskLevel(),
                operation.actionType(),
                operation.resourceType(),
                authType,
                operation.status(),
                operation.requiresApproval(),
                operation.createdAt(),
                operation.updatedAt());
    }

    private static CurrentUserPort adminUser() {
        return () -> Optional.of(new CurrentUser(1L, "root", "admin", null));
    }

    private static final class StubOpenApiSpecParser implements OpenApiSpecParserPort {

        @Override
        public OpenApiSpecDocument parse(OpenApiSpecParseRequest request) {
            return new OpenApiSpecDocument(
                    "CRM API",
                    "Customer relationship API",
                    List.of(
                            new OpenApiSpecOperation(
                                    "listCustomers",
                                    OpenApiHttpMethod.GET,
                                    "/customers",
                                    "List customers",
                                    "Read customers",
                                    "{\"type\":\"object\"}",
                                    "{\"type\":\"object\"}",
                                    "CRM_CUSTOMER"),
                            new OpenApiSpecOperation(
                                    "deleteCustomer",
                                    OpenApiHttpMethod.DELETE,
                                    "/customers/{customerId}",
                                    "Delete customer",
                                    "Delete a customer",
                                    "{\"type\":\"object\"}",
                                    null,
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
            ToolCatalogEntry entry = entries.get(toolId);
            if (entry == null || entry.enabled() == enabled) {
                return;
            }
            entries.put(toolId, new ToolCatalogEntry(
                    entry.toolId(),
                    entry.provider(),
                    entry.name(),
                    entry.description(),
                    entry.schemaJson(),
                    entry.outputSchemaJson(),
                    entry.riskLevel(),
                    entry.actionType(),
                    entry.resourceType(),
                    entry.ownerTeam(),
                    enabled,
                    entry.requiresApproval(),
                    entry.createdAt(),
                    entry.updatedAt()));
        }
    }

    private static final class MemoryConnectorCredentialBindingRepository
            implements ConnectorCredentialBindingRepositoryPort {

        private final Map<String, ConnectorCredentialBinding> bindings = new LinkedHashMap<>();

        @Override
        public ConnectorCredentialBinding save(ConnectorCredentialBinding binding) {
            bindings.put(binding.bindingId(), binding);
            return binding;
        }

        @Override
        public Optional<ConnectorCredentialBinding> findActive(String tenantId,
                                                               String connectorId,
                                                               String operationId,
                                                               CredentialAuthType authType) {
            return bindings.values().stream()
                    .filter(binding -> binding.tenantId().equals(tenantId))
                    .filter(binding -> binding.connectorId().equals(connectorId))
                    .filter(binding -> binding.operationId().equals(operationId))
                    .filter(binding -> binding.authType() == authType)
                    .filter(binding -> binding.status() == ConnectorCredentialBindingStatus.ACTIVE)
                    .findFirst();
        }

        @Override
        public List<ConnectorCredentialBinding> findActiveByOperation(String tenantId,
                                                                      String connectorId,
                                                                      String operationId) {
            return bindings.values().stream()
                    .filter(binding -> binding.tenantId().equals(tenantId))
                    .filter(binding -> binding.connectorId().equals(connectorId))
                    .filter(binding -> binding.operationId().equals(operationId))
                    .filter(binding -> binding.status() == ConnectorCredentialBindingStatus.ACTIVE)
                    .toList();
        }
    }

    private static final class RecordingAuditEventRepository implements AuditEventRepositoryPort {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public AuditEvent save(AuditEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public Optional<AuditEvent> findById(String auditId) {
            return events.stream()
                    .filter(event -> event.auditId().equals(auditId))
                    .findFirst();
        }

        @Override
        public AuditEventPage page(AuditEventQuery query) {
            return new AuditEventPage(events, events.size(), query.size(), query.current(), events.isEmpty() ? 0 : 1);
        }
    }
}

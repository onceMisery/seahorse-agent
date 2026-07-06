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
 */

package com.miracle.ai.seahorse.agent.kernel.application.agent.connector;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.CatalogBackedToolPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.LocalToolGatewayPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.Connector;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorCredentialBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorCredentialBindingStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperationStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiHttpMethod;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolActionType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolCatalogEntry;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditCompletion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolProvider;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorCredentialBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialMaterial;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.SecretValue;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiToolPortAdapterTests {

    private static final Instant NOW = Instant.parse("2026-07-02T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TOOL_ID = "openapi_customers";

    @Test
    void shouldExecuteEnabledOpenApiOperationThroughToolGatewayAndRedactResponse() throws Exception {
        AtomicReference<String> observedQuery = new AtomicReference<>("");
        try (TestHttpApi api = TestHttpApi.start("/api/customers", exchange -> {
            observedQuery.set(exchange.getRequestURI().getRawQuery());
            respond(exchange, 200, "{\"customers\":[{\"id\":\"c1\",\"token\":\"raw-secret\"}]}");
        })) {
            MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
            Connector connector = connector(api.baseUrl());
            ConnectorOperation operation = operation(connector.connectorId(), CredentialAuthType.NONE);
            connectorRepository.saveConnector(connector);
            connectorRepository.saveVersion(version(connector.connectorId()));
            connectorRepository.saveOperation(operation);
            MemoryToolCatalogRepository catalog = new MemoryToolCatalogRepository();
            catalog.save(catalogEntry(operation));
            OpenApiToolPortAdapter adapter = new OpenApiToolPortAdapter(
                    connectorRepository,
                    ConnectorCredentialBindingRepositoryPort.empty(),
                    request -> CredentialMaterial.none(),
                    null,
                    new ObjectMapper(),
                    Duration.ofSeconds(5),
                    64 * 1024);
            ToolRegistryPort registry = new OpenApiAwareToolRegistryPort(ToolRegistryPort.empty(), adapter);
            RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
            LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                    registry,
                    new CatalogBackedToolPolicyPort(catalog, AgentToolBindingRepositoryPort.empty()),
                    audit,
                    CLOCK);

            ToolInvocationResult result = gateway.invoke(request(operation.toolId(), Map.of("status", "active")));

            assertTrue(result.success());
            assertTrue(result.content().contains("\"statusCode\":200"));
            assertTrue(result.content().contains("\"id\":\"c1\""));
            assertTrue(result.content().contains("\"token\":\"[REDACTED]\""));
            assertFalse(result.content().contains("raw-secret"));
            assertEquals("status=active", observedQuery.get());
            assertEquals(ToolInvocationStatus.ALLOWED, audit.decisions.get(0).status());
            assertEquals(ToolInvocationStatus.SUCCEEDED, audit.completed.get(0).status());
        }
    }

    @Test
    void shouldInjectStaticBearerCredentialWithoutLeakingItToResult() throws Exception {
        AtomicReference<String> observedAuthorization = new AtomicReference<>("");
        try (TestHttpApi api = TestHttpApi.start("/api/customers/c1", exchange -> {
            observedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            respond(exchange, 200, "{\"ok\":true}");
        })) {
            MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
            Connector connector = connector(api.baseUrl());
            ConnectorOperation operation = operation(connector.connectorId(), CredentialAuthType.STATIC_BEARER);
            connectorRepository.saveConnector(connector);
            connectorRepository.saveVersion(version(connector.connectorId()));
            connectorRepository.saveOperation(operation);
            MemoryConnectorCredentialBindingRepository bindings = new MemoryConnectorCredentialBindingRepository();
            bindings.save(new ConnectorCredentialBinding(
                    "binding-1",
                    connector.tenantId(),
                    connector.connectorId(),
                    operation.operationId(),
                    CredentialAuthType.STATIC_BEARER,
                    "secret-ref-1",
                    ConnectorCredentialBindingStatus.ACTIVE,
                    "admin-1",
                    NOW,
                    null));
            OpenApiToolPortAdapter adapter = new OpenApiToolPortAdapter(
                    connectorRepository,
                    bindings,
                    request -> CredentialMaterial.staticBearer(request.secretRef(), SecretValue.of("bearer-secret")),
                    null,
                    new ObjectMapper(),
                    Duration.ofSeconds(5),
                    64 * 1024);

            ToolInvocationResult result = adapter.invoke(
                    "call-1",
                    operation.toolId(),
                    Map.of("customerId", "c1"));

            assertTrue(result.success());
            assertEquals("Bearer bearer-secret", observedAuthorization.get());
            assertFalse(result.content().contains("bearer-secret"));
        }
    }

    @Test
    void shouldRedactCredentialShapedFailureMessagesWhilePreservingCredentialRequest() {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        Connector connector = connector("https://api.example.test");
        ConnectorOperation operation = operation(connector.connectorId(), CredentialAuthType.STATIC_BEARER);
        connectorRepository.saveConnector(connector);
        connectorRepository.saveVersion(version(connector.connectorId()));
        connectorRepository.saveOperation(operation);
        MemoryConnectorCredentialBindingRepository bindings = new MemoryConnectorCredentialBindingRepository();
        bindings.save(new ConnectorCredentialBinding(
                "binding-1",
                connector.tenantId(),
                connector.connectorId(),
                operation.operationId(),
                CredentialAuthType.STATIC_BEARER,
                "secret://tenant/openapi/raw-provider-ref",
                ConnectorCredentialBindingStatus.ACTIVE,
                "admin-1",
                NOW,
                null));
        AtomicReference<String> observedSecretRef = new AtomicReference<>("");
        OpenApiToolPortAdapter adapter = new OpenApiToolPortAdapter(
                connectorRepository,
                bindings,
                request -> {
                    observedSecretRef.set(request.secretRef());
                    throw new IllegalStateException(
                            "credential provider failed Authorization: Bearer abcdefghijklmnop api_key=plain-openapi-secret");
                },
                null,
                new ObjectMapper(),
                Duration.ofSeconds(5),
                64 * 1024);

        ToolInvocationResult result = adapter.invoke(
                "call-1",
                operation.toolId(),
                Map.of("customerId", "c1"));

        assertFalse(result.success());
        assertTrue(result.error().contains("[REDACTED]"));
        assertFalse(result.error().contains("abcdefghijklmnop"));
        assertFalse(result.error().contains("plain-openapi-secret"));
        assertEquals("secret://tenant/openapi/raw-provider-ref", observedSecretRef.get());
    }

    @Test
    void shouldRedactCredentialShapedOpenApiResponseFieldsBeforeGatewayFallback() throws Exception {
        try (TestHttpApi api = TestHttpApi.start("/api/customers", exchange -> respond(exchange, 200,
                "{\"secretRef\":\"secret://tenant/openapi\","
                        + "\"Authorization\":\"Bearer upstream-authorization-token\","
                        + "\"Cookie\":\"sid=upstream-cookie-header\","
                        + "\"cookieCount\":1,"
                        + "\"setCookie\":\"sid=upstream-cookie-value\","
                        + "\"nested\":{\"clientSecret\":\"upstream-client-secret\","
                        + "\"private_key\":\"upstream-private-key\"},"
                        + "\"items\":[{\"token\":\"upstream-token\","
                        + "\"sessionToken\":\"upstream-session-token\","
                        + "\"tokenCount\":2,\"secretKey\":\"upstream-secret-key\",\"label\":\"safe\"}]}"))) {
            MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
            Connector connector = connector(api.baseUrl());
            ConnectorOperation operation = operation(connector.connectorId(), CredentialAuthType.NONE);
            connectorRepository.saveConnector(connector);
            connectorRepository.saveVersion(version(connector.connectorId()));
            connectorRepository.saveOperation(operation);
            OpenApiToolPortAdapter adapter = new OpenApiToolPortAdapter(
                    connectorRepository,
                    ConnectorCredentialBindingRepositoryPort.empty(),
                    request -> CredentialMaterial.none(),
                    null,
                    new ObjectMapper(),
                    Duration.ofSeconds(5),
                    64 * 1024);

            ToolInvocationResult result = adapter.invoke(
                    "call-1",
                    operation.toolId(),
                    Map.of("status", "active"));

            assertTrue(result.success());
            assertTrue(result.content().contains("\"secretRef\":\"secret://tenant/openapi\""));
            assertTrue(result.content().contains("\"label\":\"safe\""));
            assertTrue(result.content().contains("\"Authorization\":\"[REDACTED]\""));
            assertTrue(result.content().contains("\"Cookie\":\"[REDACTED]\""));
            assertTrue(result.content().contains("\"cookieCount\":1"));
            assertTrue(result.content().contains("\"setCookie\":\"[REDACTED]\""));
            assertTrue(result.content().contains("\"clientSecret\":\"[REDACTED]\""));
            assertTrue(result.content().contains("\"private_key\":\"[REDACTED]\""));
            assertTrue(result.content().contains("\"token\":\"[REDACTED]\""));
            assertTrue(result.content().contains("\"sessionToken\":\"[REDACTED]\""));
            assertTrue(result.content().contains("\"tokenCount\":2"));
            assertTrue(result.content().contains("\"secretKey\":\"[REDACTED]\""));
            assertFalse(result.content().contains("upstream-authorization-token"));
            assertFalse(result.content().contains("upstream-cookie-header"));
            assertFalse(result.content().contains("upstream-cookie-value"));
            assertFalse(result.content().contains("upstream-client-secret"));
            assertFalse(result.content().contains("upstream-private-key"));
            assertFalse(result.content().contains("upstream-token"));
            assertFalse(result.content().contains("upstream-session-token"));
            assertFalse(result.content().contains("upstream-secret-key"));
        }
    }

    @Test
    void shouldRedactCredentialShapedOpenApiTextResponseBeforeGatewayFallback() throws Exception {
        try (TestHttpApi api = TestHttpApi.start("/api/customers", exchange -> respond(exchange, 200,
                "text/plain",
                "upstream failed Cookie: plain-private-key Authorization: Bearer abcdefghijklmnop sk-live-secret"))) {
            OpenApiToolPortAdapter adapter = adapterFor(api.baseUrl(), CredentialAuthType.NONE);

            ToolInvocationResult result = adapter.invoke(
                    "call-1",
                    TOOL_ID,
                    Map.of("status", "active"));

            assertTrue(result.success());
            assertTrue(result.content().contains("upstream failed [REDACTED] [REDACTED] [REDACTED]"));
            assertFalse(result.content().contains("plain-private-key"));
            assertFalse(result.content().contains("Authorization"));
            assertFalse(result.content().contains("abcdefghijklmnop"));
            assertFalse(result.content().contains("sk-live-secret"));
        }
    }

    @Test
    void shouldRedactCredentialShapedInvalidJsonResponseBeforeGatewayFallback() throws Exception {
        try (TestHttpApi api = TestHttpApi.start("/api/customers", exchange -> respond(exchange, 200,
                "application/json",
                "{\"error\":\"partial\", \"message\":\"cookie: plain-session-cookie"))) {
            OpenApiToolPortAdapter adapter = adapterFor(api.baseUrl(), CredentialAuthType.NONE);

            ToolInvocationResult result = adapter.invoke(
                    "call-1",
                    TOOL_ID,
                    Map.of("status", "active"));

            assertTrue(result.success());
            assertTrue(result.content().contains("[REDACTED]"));
            assertFalse(result.content().contains("plain-session-cookie"));
        }
    }

    private static ToolInvocationRequest request(String toolId, Map<String, Object> arguments) {
        return new ToolInvocationRequest(
                "run-openapi",
                "step-openapi",
                "call-openapi",
                null,
                null,
                "tenant-1",
                "user-1",
                "agent-identity-1",
                toolId,
                arguments,
                Map.of(),
                "run-openapi:call-openapi",
                List.of(toolId));
    }

    private static OpenApiToolPortAdapter adapterFor(String baseUrl, CredentialAuthType authType) {
        MemoryConnectorRepository connectorRepository = new MemoryConnectorRepository();
        Connector connector = connector(baseUrl);
        ConnectorOperation operation = operation(connector.connectorId(), authType);
        connectorRepository.saveConnector(connector);
        connectorRepository.saveVersion(version(connector.connectorId()));
        connectorRepository.saveOperation(operation);
        return new OpenApiToolPortAdapter(
                connectorRepository,
                ConnectorCredentialBindingRepositoryPort.empty(),
                request -> CredentialMaterial.none(),
                null,
                new ObjectMapper(),
                Duration.ofSeconds(5),
                64 * 1024);
    }

    private static Connector connector(String baseUrl) {
        return new Connector(
                "conn-openapi",
                "tenant-1",
                ConnectorProvider.OPENAPI,
                "Customers API",
                "Customers test API",
                baseUrl,
                ConnectorStatus.IMPORTED,
                "admin-1",
                NOW,
                NOW);
    }

    private static ConnectorVersion version(String connectorId) {
        return new ConnectorVersion(
                "connv-openapi",
                connectorId,
                "hash-openapi",
                "{\"openapi\":\"3.0.3\"}",
                "admin-1",
                NOW);
    }

    private static ConnectorOperation operation(String connectorId, CredentialAuthType authType) {
        boolean authenticated = authType != CredentialAuthType.NONE;
        return new ConnectorOperation(
                authenticated ? "op-authenticated" : "op-list",
                connectorId,
                "connv-openapi",
                authenticated ? "getCustomer" : "listCustomers",
                authenticated ? "getCustomer" : "listCustomers",
                OpenApiHttpMethod.GET,
                authenticated ? "/customers/{customerId}" : "/customers",
                authenticated ? "Get customer" : "List customers",
                authenticated ? "Get a customer" : "List customers",
                authenticated
                        ? "{\"parameters\":[{\"name\":\"customerId\",\"in\":\"path\"}]}"
                        : "{\"parameters\":[{\"name\":\"status\",\"in\":\"query\"}]}",
                "{\"type\":\"object\"}",
                TOOL_ID + (authenticated ? "_auth" : ""),
                ToolRiskLevel.LOW,
                ToolActionType.READ,
                "CRM_CUSTOMER",
                authType,
                ConnectorOperationStatus.ENABLED,
                false,
                NOW,
                NOW);
    }

    private static ToolCatalogEntry catalogEntry(ConnectorOperation operation) {
        return new ToolCatalogEntry(
                operation.toolId(),
                ToolProvider.OPENAPI,
                operation.summary(),
                operation.description(),
                operation.schemaJson(),
                operation.outputSchemaJson(),
                operation.riskLevel(),
                operation.actionType(),
                operation.resourceType(),
                "connector:" + operation.connectorId(),
                true,
                false,
                NOW,
                NOW);
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        respond(exchange, status, "application/json", body);
    }

    private static void respond(HttpExchange exchange, int status, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }

    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }

    private static final class TestHttpApi implements AutoCloseable {
        private final HttpServer server;

        private TestHttpApi(HttpServer server) {
            this.server = server;
        }

        static TestHttpApi start(String path, ExchangeHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext(path, exchange -> handler.handle(exchange));
            server.start();
            return new TestHttpApi(server);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/api";
        }

        @Override
        public void close() {
            server.stop(0);
        }
    }

    private static final class RecordingToolInvocationAuditPort implements ToolInvocationAuditPort {
        private final List<ToolInvocationAuditRecord> requested = new ArrayList<>();
        private final List<ToolInvocationAuditDecision> decisions = new ArrayList<>();
        private final List<ToolInvocationAuditCompletion> completed = new ArrayList<>();

        @Override
        public void recordRequested(ToolInvocationAuditRecord record) {
            requested.add(record);
        }

        @Override
        public void recordDecision(ToolInvocationAuditDecision decision) {
            decisions.add(decision);
        }

        @Override
        public void recordCompleted(ToolInvocationAuditCompletion completion) {
            completed.add(completion);
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
            return operation != null && operation.connectorId().equals(connectorId)
                    ? Optional.of(operation)
                    : Optional.empty();
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
            if (entry == null) {
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

        @Override
        public ToolCatalogPage page(ToolCatalogQuery query) {
            List<ToolCatalogEntry> records = new ArrayList<>(entries.values());
            return new ToolCatalogPage(records, records.size(), query.size(), query.current(), records.isEmpty() ? 0 : 1);
        }
    }
}

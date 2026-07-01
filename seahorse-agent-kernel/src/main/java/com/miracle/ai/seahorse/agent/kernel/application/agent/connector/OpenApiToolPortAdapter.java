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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.Connector;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorCredentialBinding;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.ConnectorOperationStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.connector.OpenApiHttpMethod;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorCredentialBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ConnectorRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialMaterial;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialRequest;

import java.io.IOException;
import java.lang.reflect.Array;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class OpenApiToolPortAdapter implements ToolPort {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(15);
    private static final int DEFAULT_MAX_RESPONSE_BYTES = 256 * 1024;
    private static final int CONNECTOR_PAGE_SIZE = 100;
    private static final String APPLICATION_JSON = "application/json";
    private static final String DEFAULT_USER_AGENT = "SeahorseAgent-OpenAPIConnector/1.0";
    private static final Set<String> BLOCKED_ARGUMENT_HEADERS = Set.of(
            "authorization",
            "cookie",
            "host",
            "content-length",
            "transfer-encoding");
    private static final Set<String> SENSITIVE_FIELD_NAMES = Set.of(
            "authorization",
            "password",
            "secret",
            "token",
            "access_token",
            "refresh_token",
            "api_key",
            "apikey",
            "client_secret");

    private final ConnectorRepositoryPort connectorRepository;
    private final ConnectorCredentialBindingRepositoryPort credentialBindingRepository;
    private final CredentialProviderPort credentialProvider;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final Duration timeout;
    private final int maxResponseBytes;

    public OpenApiToolPortAdapter(ConnectorRepositoryPort connectorRepository) {
        this(connectorRepository,
                ConnectorCredentialBindingRepositoryPort.empty(),
                request -> CredentialMaterial.none(),
                null,
                new ObjectMapper(),
                DEFAULT_TIMEOUT,
                DEFAULT_MAX_RESPONSE_BYTES);
    }

    public OpenApiToolPortAdapter(ConnectorRepositoryPort connectorRepository,
                                  ConnectorCredentialBindingRepositoryPort credentialBindingRepository,
                                  CredentialProviderPort credentialProvider,
                                  HttpClient httpClient,
                                  ObjectMapper objectMapper,
                                  Duration timeout,
                                  int maxResponseBytes) {
        this.connectorRepository = Objects.requireNonNull(connectorRepository,
                "connectorRepository must not be null");
        this.credentialBindingRepository = Objects.requireNonNullElseGet(
                credentialBindingRepository,
                ConnectorCredentialBindingRepositoryPort::empty);
        this.credentialProvider = Objects.requireNonNullElse(credentialProvider, request -> CredentialMaterial.none());
        this.timeout = timeout == null || timeout.isZero() || timeout.isNegative() ? DEFAULT_TIMEOUT : timeout;
        this.httpClient = Objects.requireNonNullElseGet(httpClient, this::defaultHttpClient);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        this.maxResponseBytes = maxResponseBytes <= 0 ? DEFAULT_MAX_RESPONSE_BYTES : maxResponseBytes;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        Optional<ConnectorOperation> operation = connectorRepository.findOperationByToolId(toolId);
        if (operation.isEmpty()) {
            return ToolInvocationResult.failed("OPENAPI_OPERATION_NOT_FOUND");
        }
        return invokeOperation(operation.orElseThrow(), arguments == null ? Map.of() : arguments);
    }

    public Optional<ToolDescriptor> descriptor(String toolId) {
        return connectorRepository.findOperationByToolId(toolId)
                .filter(this::isEnabled)
                .map(this::descriptor);
    }

    public List<ToolDescriptor> listEnabledDescriptors() {
        List<ToolDescriptor> descriptors = new ArrayList<>();
        long current = 1L;
        while (true) {
            ConnectorPage page = connectorRepository.page(new ConnectorQuery(
                    null,
                    null,
                    null,
                    current,
                    CONNECTOR_PAGE_SIZE));
            page.records().forEach(connector -> connectorRepository.listOperations(connector.connectorId()).stream()
                    .filter(this::isEnabled)
                    .map(this::descriptor)
                    .forEach(descriptors::add));
            if (page.pages() <= current || page.records().isEmpty()) {
                return List.copyOf(descriptors);
            }
            current++;
        }
    }

    private ToolInvocationResult invokeOperation(ConnectorOperation operation, Map<String, Object> arguments) {
        if (!isEnabled(operation)) {
            return ToolInvocationResult.failed("OPENAPI_OPERATION_DISABLED");
        }
        Connector connector = connectorRepository.findConnectorById(operation.connectorId())
                .orElse(null);
        if (connector == null) {
            return ToolInvocationResult.failed("OPENAPI_CONNECTOR_NOT_FOUND");
        }
        URI uri;
        try {
            uri = buildUri(connector, operation, arguments);
        } catch (IllegalArgumentException ex) {
            return ToolInvocationResult.failed(error("OPENAPI_REQUEST_INVALID", ex.getMessage()));
        }
        try {
            HttpRequest request = buildRequest(uri, operation, arguments, resolveCredential(connector, operation));
            HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
            String result = responsePayload(response);
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return ToolInvocationResult.ok(result);
            }
            return ToolInvocationResult.failed(error("OPENAPI_HTTP_STATUS_" + response.statusCode(), result));
        } catch (CredentialResolutionException ex) {
            return ToolInvocationResult.failed(error(ex.reasonCode(), ex.getMessage()));
        } catch (IOException | InterruptedException ex) {
            if (ex instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            return ToolInvocationResult.failed(error("OPENAPI_HTTP_REQUEST_FAILED", ex.getMessage()));
        } catch (RuntimeException ex) {
            return ToolInvocationResult.failed(error("OPENAPI_EXECUTION_FAILED",
                    Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName())));
        }
    }

    private HttpRequest buildRequest(URI uri,
                                     ConnectorOperation operation,
                                     Map<String, Object> arguments,
                                     CredentialMaterial credential) throws JsonProcessingException {
        HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                .timeout(timeout)
                .header("Accept", APPLICATION_JSON)
                .header("User-Agent", DEFAULT_USER_AGENT);
        headerParameters(operation, arguments).forEach(builder::header);
        if (credential.authType().isBearerMaterial()) {
            builder.header("Authorization", "Bearer " + credential.secretValue().reveal());
        }
        Optional<byte[]> body = requestBody(operation, arguments);
        if (body.isPresent()) {
            builder.header("Content-Type", APPLICATION_JSON);
            builder.method(operation.method().name(), HttpRequest.BodyPublishers.ofByteArray(body.orElseThrow()));
        } else if (operation.method() == OpenApiHttpMethod.GET) {
            builder.GET();
        } else {
            builder.method(operation.method().name(), HttpRequest.BodyPublishers.noBody());
        }
        return builder.build();
    }

    private CredentialMaterial resolveCredential(Connector connector, ConnectorOperation operation) {
        if (operation.authType() == CredentialAuthType.NONE) {
            return CredentialMaterial.none();
        }
        if (operation.authType() != CredentialAuthType.STATIC_BEARER) {
            throw new CredentialResolutionException("OPENAPI_AUTH_TYPE_UNSUPPORTED",
                    "unsupported auth type: " + operation.authType());
        }
        ConnectorCredentialBinding binding = credentialBindingRepository.findActive(
                        connector.tenantId(),
                        operation.connectorId(),
                        operation.operationId(),
                        operation.authType())
                .orElseThrow(() -> new CredentialResolutionException(
                        "OPENAPI_CREDENTIAL_BINDING_REQUIRED",
                        "active credential binding required"));
        return credentialProvider.resolve(CredentialRequest.staticBearer(binding.credentialRef()));
    }

    private URI buildUri(Connector connector, ConnectorOperation operation, Map<String, Object> arguments) {
        String baseUrl = requireText(connector.baseUrl(), "connector baseUrl is required");
        URI base = URI.create(baseUrl);
        if (base.getScheme() == null || base.getHost() == null
                || base.getUserInfo() != null
                || !Set.of("http", "https").contains(base.getScheme().toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("connector baseUrl must be absolute http(s) URL without userinfo");
        }
        List<ParameterSpec> parameters = parameters(operation);
        String resolvedPath = resolvePath(operation.path(), parameters, arguments);
        String query = queryString(parameters, arguments);
        String rawBase = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String rawPath = resolvedPath.startsWith("/") ? resolvedPath : "/" + resolvedPath;
        return URI.create(rawBase + rawPath + (query.isBlank() ? "" : "?" + query));
    }

    private String resolvePath(String path, List<ParameterSpec> parameters, Map<String, Object> arguments) {
        String result = path;
        List<String> pathNames = new ArrayList<>(parameters.stream()
                .filter(parameter -> "path".equals(parameter.in()))
                .map(ParameterSpec::name)
                .toList());
        int cursor = 0;
        while (cursor < result.length()) {
            int start = result.indexOf('{', cursor);
            if (start < 0) {
                break;
            }
            int end = result.indexOf('}', start + 1);
            if (end < 0) {
                break;
            }
            pathNames.add(result.substring(start + 1, end));
            cursor = end + 1;
        }
        for (String name : pathNames.stream().distinct().toList()) {
            Object value = argumentValue(arguments, "path", name)
                    .orElseThrow(() -> new IllegalArgumentException("missing path parameter: " + name));
            result = result.replace("{" + name + "}", encode(String.valueOf(value)));
        }
        return result;
    }

    private String queryString(List<ParameterSpec> parameters, Map<String, Object> arguments) {
        List<String> parts = new ArrayList<>();
        parameters.stream()
                .filter(parameter -> "query".equals(parameter.in()))
                .forEach(parameter -> argumentValue(arguments, "query", parameter.name())
                        .ifPresent(value -> addQueryParts(parts, parameter.name(), value)));
        return String.join("&", parts);
    }

    private void addQueryParts(List<String> parts, String name, Object value) {
        if (value instanceof Iterable<?> iterable) {
            iterable.forEach(item -> parts.add(encode(name) + "=" + encode(String.valueOf(item))));
            return;
        }
        if (value != null && value.getClass().isArray()) {
            for (int i = 0; i < Array.getLength(value); i++) {
                Object item = Array.get(value, i);
                parts.add(encode(name) + "=" + encode(String.valueOf(item)));
            }
            return;
        }
        parts.add(encode(name) + "=" + encode(String.valueOf(value)));
    }

    private Map<String, String> headerParameters(ConnectorOperation operation, Map<String, Object> arguments) {
        Map<String, String> headers = new LinkedHashMap<>();
        parameters(operation).stream()
                .filter(parameter -> "header".equals(parameter.in()))
                .filter(parameter -> !BLOCKED_ARGUMENT_HEADERS.contains(parameter.name().toLowerCase(Locale.ROOT)))
                .forEach(parameter -> argumentValue(arguments, "header", parameter.name())
                        .ifPresent(value -> headers.put(parameter.name(), String.valueOf(value))));
        return headers;
    }

    private Optional<byte[]> requestBody(ConnectorOperation operation,
                                         Map<String, Object> arguments) throws JsonProcessingException {
        if (operation.method() == OpenApiHttpMethod.GET) {
            return Optional.empty();
        }
        Optional<Object> body = Optional.ofNullable(arguments.get("requestBody"))
                .or(() -> Optional.ofNullable(arguments.get("body")));
        if (body.isEmpty()) {
            return Optional.empty();
        }
        Object value = body.orElseThrow();
        if (value instanceof String text) {
            return Optional.of(text.getBytes(StandardCharsets.UTF_8));
        }
        return Optional.of(objectMapper.writeValueAsBytes(value));
    }

    private List<ParameterSpec> parameters(ConnectorOperation operation) {
        try {
            JsonNode parameters = objectMapper.readTree(operation.schemaJson()).path("parameters");
            if (!parameters.isArray()) {
                return List.of();
            }
            List<ParameterSpec> result = new ArrayList<>();
            parameters.forEach(parameter -> {
                String name = textOrNull(parameter.path("name"));
                String in = textOrNull(parameter.path("in"));
                if (name != null && in != null) {
                    result.add(new ParameterSpec(name, in.toLowerCase(Locale.ROOT)));
                }
            });
            return List.copyOf(result);
        } catch (JsonProcessingException ex) {
            return List.of();
        }
    }

    private Optional<Object> argumentValue(Map<String, Object> arguments, String scope, String name) {
        Object scoped = arguments.get(scope);
        if (scoped instanceof Map<?, ?> scopedMap && scopedMap.containsKey(name)) {
            return Optional.ofNullable(scopedMap.get(name));
        }
        Object parameters = arguments.get("parameters");
        if (parameters instanceof Map<?, ?> parameterMap && parameterMap.containsKey(name)) {
            return Optional.ofNullable(parameterMap.get(name));
        }
        return Optional.ofNullable(arguments.get(name));
    }

    private String responsePayload(HttpResponse<byte[]> response) throws JsonProcessingException {
        byte[] body = response.body() == null ? new byte[0] : response.body();
        boolean truncated = body.length > maxResponseBytes;
        if (truncated) {
            body = Arrays.copyOf(body, maxResponseBytes);
        }
        String contentType = response.headers().firstValue("content-type").orElse("");
        String text = new String(body, StandardCharsets.UTF_8);
        ObjectNode result = objectMapper.createObjectNode();
        result.put("statusCode", response.statusCode());
        result.put("contentType", contentType);
        result.put("truncated", truncated);
        if (isJson(contentType)) {
            try {
                result.set("body", redact(objectMapper.readTree(text)));
            } catch (JsonProcessingException ex) {
                result.put("body", text);
            }
        } else {
            result.put("body", text);
        }
        return objectMapper.writeValueAsString(result);
    }

    private JsonNode redact(JsonNode node) {
        if (node == null || node.isNull()) {
            return JsonNodeFactory.instance.nullNode();
        }
        if (node.isObject()) {
            ObjectNode result = objectMapper.createObjectNode();
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSensitiveField(field.getKey())) {
                    result.set(field.getKey(), TextNode.valueOf("[REDACTED]"));
                } else {
                    result.set(field.getKey(), redact(field.getValue()));
                }
            }
            return result;
        }
        if (node.isArray()) {
            ArrayNode result = objectMapper.createArrayNode();
            node.forEach(item -> result.add(redact(item)));
            return result;
        }
        return node;
    }

    private boolean isSensitiveField(String fieldName) {
        String normalized = fieldName == null ? "" : fieldName.trim()
                .replace('-', '_')
                .toLowerCase(Locale.ROOT);
        return SENSITIVE_FIELD_NAMES.contains(normalized);
    }

    private boolean isJson(String contentType) {
        String normalized = Objects.requireNonNullElse(contentType, "").toLowerCase(Locale.ROOT);
        return normalized.contains("json");
    }

    private ToolDescriptor descriptor(ConnectorOperation operation) {
        return new ToolDescriptor(
                operation.toolId(),
                textOrDefault(operation.summary(), operation.operationKey()),
                textOrDefault(operation.description(), operation.operationKey()),
                operation.schemaJson());
    }

    private boolean isEnabled(ConnectorOperation operation) {
        return operation.status() == ConnectorOperationStatus.ENABLED;
    }

    private HttpClient defaultHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(timeout)
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private String error(String reasonCode, String detail) {
        ObjectNode node = objectMapper.createObjectNode();
        node.put("reasonCode", reasonCode);
        node.put("message", Objects.requireNonNullElse(detail, reasonCode));
        try {
            return objectMapper.writeValueAsString(node);
        } catch (JsonProcessingException ex) {
            return reasonCode;
        }
    }

    private String encode(String value) {
        return URLEncoder.encode(Objects.requireNonNullElse(value, ""), StandardCharsets.UTF_8)
                .replace("+", "%20");
    }

    private String textOrDefault(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private String textOrNull(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        return trimToNull(node.asText(null));
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private record ParameterSpec(String name, String in) {
    }

    private static final class CredentialResolutionException extends RuntimeException {
        private final String reasonCode;

        private CredentialResolutionException(String reasonCode, String message) {
            super(message);
            this.reasonCode = reasonCode;
        }

        private String reasonCode() {
            return reasonCode;
        }
    }
}

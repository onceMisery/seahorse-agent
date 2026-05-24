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

package com.miracle.ai.seahorse.agent.adapters.mcp.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionRequest;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialAuthType;
import com.miracle.ai.seahorse.agent.ports.outbound.credential.CredentialMaterial;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpClientPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Streamable HTTP MCP JSON-RPC 客户端。
 *
 * <p>该实现仅属于 L3 adapter，负责 JSON-RPC 封包、HTTP 调用、工具 schema 映射和错误降级。
 */
public class StreamableHttpMcpClient implements McpClientPort {

    private static final Logger LOG = LoggerFactory.getLogger(StreamableHttpMcpClient.class);
    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final String JSON_RPC_VERSION = "2.0";
    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_INITIALIZED = "notifications/initialized";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_TEXT = "text";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String AUTHORIZATION_BEARER_PREFIX = "Bearer ";

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String serverName;
    private final String endpointUrl;
    private final CredentialMaterial credentialMaterial;
    private final AtomicLong requestId = new AtomicLong(1);

    public StreamableHttpMcpClient(OkHttpClient httpClient,
                                   ObjectMapper objectMapper,
                                   String serverName,
                                   String serverUrl) {
        this.httpClient = Objects.requireNonNull(httpClient, "OkHttpClient 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper 不能为空");
        this.serverName = Objects.requireNonNullElse(serverName, "");
        this.endpointUrl = resolveEndpointUrl(serverUrl);
        this.credentialMaterial = CredentialMaterial.none();
    }

    public StreamableHttpMcpClient(OkHttpClient httpClient,
                                   ObjectMapper objectMapper,
                                   String serverName,
                                   String serverUrl,
                                   CredentialMaterial credentialMaterial) {
        this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.serverName = Objects.requireNonNullElse(serverName, "");
        this.endpointUrl = resolveEndpointUrl(serverUrl);
        this.credentialMaterial = Objects.requireNonNullElseGet(credentialMaterial, CredentialMaterial::none);
    }

    /**
     * 初始化 MCP 会话。
     *
     * @return true 表示初始化成功
     */
    public boolean initialize() {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2026-02-28");
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "seahorse-agent");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);

        McpJsonRpcResponse response = sendRequest(METHOD_INITIALIZE, params);
        if (!response.success()) {
            LOG.warn("MCP Server 初始化失败, server={}, reason={}", serverName, response.errorMessage());
            return false;
        }
        sendNotification(METHOD_INITIALIZED);
        return true;
    }

    /**
     * 拉取远程工具定义。
     *
     * @return 工具元数据列表
     */
    public List<McpToolDescriptor> listTools() {
        McpJsonRpcResponse response = sendRequest(METHOD_TOOLS_LIST, objectMapper.createObjectNode());
        if (!response.success()) {
            LOG.warn("MCP 工具列表拉取失败, server={}, reason={}", serverName, response.errorMessage());
            return List.of();
        }
        JsonNode result = response.result();
        if (result == null || !result.has("tools") || !result.get("tools").isArray()) {
            return List.of();
        }
        List<McpToolDescriptor> tools = new ArrayList<>();
        for (JsonNode toolNode : result.get("tools")) {
            McpToolDescriptor descriptor = toDescriptor(toolNode);
            if (!descriptor.toolId().isBlank()) {
                tools.add(descriptor);
            }
        }
        return tools;
    }

    /**
     * 调用远程工具。
     *
     * @param request 工具执行请求
     * @return 执行结果
     */
    @Override
    public McpToolExecutionResult call(McpToolExecutionRequest request) {
        Objects.requireNonNull(request, "MCP 工具执行请求不能为空");
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", request.toolId());
        params.set("arguments", objectMapper.valueToTree(request.arguments()));

        long startMs = System.currentTimeMillis();
        McpJsonRpcResponse response = sendRequest(METHOD_TOOLS_CALL, params);
        long costMs = System.currentTimeMillis() - startMs;
        if (!response.success()) {
            return McpToolExecutionResult.failed(request.toolId(), response.errorMessage());
        }
        return toExecutionResult(request.toolId(), response.result(), costMs);
    }

    private McpJsonRpcResponse sendRequest(String method, JsonNode params) {
        ObjectNode rpcRequest = objectMapper.createObjectNode();
        rpcRequest.put("jsonrpc", JSON_RPC_VERSION);
        rpcRequest.put("id", requestId.getAndIncrement());
        rpcRequest.put("method", method);
        rpcRequest.set("params", params);
        return postJson(method, rpcRequest);
    }

    private void sendNotification(String method) {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", JSON_RPC_VERSION);
        notification.put("method", method);
        McpJsonRpcResponse response = postJson(method, notification);
        if (!response.success()) {
            LOG.warn("MCP 通知发送失败, server={}, method={}, reason={}", serverName, method, response.errorMessage());
        }
    }

    private McpJsonRpcResponse postJson(String method, JsonNode bodyNode) {
        String body;
        try {
            body = objectMapper.writeValueAsString(bodyNode);
        } catch (JsonProcessingException ex) {
            return new McpJsonRpcResponse(null, "JSON 序列化失败: " + ex.getMessage());
        }
        Request.Builder requestBuilder = new Request.Builder()
                .url(endpointUrl)
                .post(RequestBody.create(body, JSON));
        applyCredential(requestBuilder);
        Request request = requestBuilder.build();
        try (Response response = httpClient.newCall(request).execute()) {
            return readResponse(method, response);
        } catch (IOException ex) {
            return new McpJsonRpcResponse(null, "HTTP 调用异常: " + ex.getMessage());
        }
    }

    private void applyCredential(Request.Builder requestBuilder) {
        if (CredentialAuthType.STATIC_BEARER.equals(credentialMaterial.authType())
                && credentialMaterial.secretValue().hasText()) {
            requestBuilder.header(
                    HEADER_AUTHORIZATION,
                    AUTHORIZATION_BEARER_PREFIX + credentialMaterial.secretValue().reveal());
        }
    }

    private McpJsonRpcResponse readResponse(String method, Response response) throws IOException {
        if (!response.isSuccessful()) {
            return new McpJsonRpcResponse(null, "HTTP 状态码 " + response.code());
        }
        String body = response.body() == null ? "" : response.body().string();
        if (body.isBlank()) {
            return new McpJsonRpcResponse(null, "响应体为空");
        }
        JsonNode root = objectMapper.readTree(body);
        if (root.hasNonNull(FIELD_ERROR)) {
            return new McpJsonRpcResponse(null, errorMessage(method, root.get(FIELD_ERROR)));
        }
        return new McpJsonRpcResponse(root.get(FIELD_RESULT), "");
    }

    private String errorMessage(String method, JsonNode error) {
        JsonNode message = error.get("message");
        if (message != null && !message.isNull()) {
            return method + " 失败: " + message.asText();
        }
        return method + " 失败";
    }

    private McpToolExecutionResult toExecutionResult(String toolId, JsonNode result, long costMs) {
        if (result == null || result.isNull()) {
            return McpToolExecutionResult.failed(toolId, "远程工具返回空结果, costMs=" + costMs);
        }
        if (result.has("isError") && result.get("isError").asBoolean(false)) {
            return McpToolExecutionResult.failed(toolId, extractTextContent(result));
        }
        return McpToolExecutionResult.success(toolId, extractTextContent(result));
    }

    private String extractTextContent(JsonNode result) {
        if (!result.has(FIELD_CONTENT) || !result.get(FIELD_CONTENT).isArray()) {
            return "";
        }
        List<String> textSegments = new ArrayList<>();
        for (JsonNode item : result.get(FIELD_CONTENT)) {
            JsonNode text = item.get(FIELD_TEXT);
            if (text != null && !text.isNull()) {
                textSegments.add(text.asText());
            }
        }
        return String.join("\n", textSegments);
    }

    private McpToolDescriptor toDescriptor(JsonNode toolNode) {
        String toolId = readText(toolNode, "name");
        String description = readText(toolNode, "description");
        JsonNode inputSchema = toolNode.get("inputSchema");
        return new McpToolDescriptor(toolId, description, toParameters(inputSchema));
    }

    private Map<String, McpToolDescriptor.Parameter> toParameters(JsonNode inputSchema) {
        if (inputSchema == null || inputSchema.isNull() || !inputSchema.has("properties")) {
            return Map.of();
        }
        List<String> requiredNames = readRequiredNames(inputSchema);
        Map<String, McpToolDescriptor.Parameter> parameters = new LinkedHashMap<>();
        JsonNode properties = inputSchema.get("properties");
        properties.fieldNames().forEachRemaining(name -> parameters.put(
                name, toParameter(properties.get(name), requiredNames.contains(name))));
        return parameters;
    }

    private McpToolDescriptor.Parameter toParameter(JsonNode node, boolean required) {
        List<String> enumValues = readEnumValues(node);
        return new McpToolDescriptor.Parameter(
                readText(node, "description"),
                readTextOrDefault(node, "type", "string"),
                required,
                null,
                enumValues);
    }

    private List<String> readRequiredNames(JsonNode inputSchema) {
        JsonNode required = inputSchema.get("required");
        if (required == null || !required.isArray()) {
            return List.of();
        }
        List<String> names = new ArrayList<>();
        for (JsonNode item : required) {
            names.add(item.asText());
        }
        return names;
    }

    private List<String> readEnumValues(JsonNode node) {
        JsonNode enumNode = node == null ? null : node.get("enum");
        if (enumNode == null || !enumNode.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (JsonNode item : enumNode) {
            values.add(item.asText());
        }
        return values;
    }

    private String readText(JsonNode node, String fieldName) {
        return readTextOrDefault(node, fieldName, "");
    }

    private String readTextOrDefault(JsonNode node, String fieldName, String defaultValue) {
        if (node == null || !node.has(fieldName) || node.get(fieldName).isNull()) {
            return defaultValue;
        }
        return node.get(fieldName).asText(defaultValue);
    }

    private String resolveEndpointUrl(String serverUrl) {
        String safeUrl = Objects.requireNonNullElse(serverUrl, "").trim();
        if (safeUrl.isBlank()) {
            throw new IllegalArgumentException("MCP Server URL 不能为空");
        }
        return safeUrl.endsWith("/mcp") ? safeUrl : safeUrl + "/mcp";
    }
}

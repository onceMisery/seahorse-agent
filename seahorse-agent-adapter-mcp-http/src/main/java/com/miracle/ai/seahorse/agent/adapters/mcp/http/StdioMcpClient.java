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
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpClientPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolDescriptor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

/**
 * MCP JSON-RPC client using stdio transport.
 */
public class StdioMcpClient implements McpClientPort, AutoCloseable {

    private static final Logger LOG = LoggerFactory.getLogger(StdioMcpClient.class);
    private static final String JSON_RPC_VERSION = "2.0";
    private static final String METHOD_INITIALIZE = "initialize";
    private static final String METHOD_INITIALIZED = "notifications/initialized";
    private static final String METHOD_TOOLS_LIST = "tools/list";
    private static final String METHOD_TOOLS_CALL = "tools/call";
    private static final String FIELD_RESULT = "result";
    private static final String FIELD_ERROR = "error";
    private static final String FIELD_CONTENT = "content";
    private static final String FIELD_TEXT = "text";
    private static final int STDERR_TAIL_LIMIT = 4096;

    private final ObjectMapper objectMapper;
    private final String serverName;
    private final String command;
    private final List<String> args;
    private final Map<String, String> env;
    private final String workingDir;
    private final Duration callTimeout;
    private final ExecutorService session;
    private final AtomicLong requestId = new AtomicLong(1);
    private final AtomicBoolean closed = new AtomicBoolean(false);

    private Process process;
    private BufferedWriter stdin;
    private BufferedReader stdout;
    private BufferedReader stderr;
    private Thread stderrThread;
    private final Object stderrLock = new Object();
    private final StringBuilder stderrTail = new StringBuilder();
    private volatile String lastFailureMessage = "";
    private boolean initialized;

    public StdioMcpClient(ObjectMapper objectMapper,
                          String serverName,
                          String command,
                          List<String> args,
                          Map<String, String> env,
                          String workingDir,
                          Duration callTimeout) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.serverName = Objects.requireNonNullElse(serverName, "");
        this.command = Objects.requireNonNullElse(command, "").trim();
        this.args = List.copyOf(Objects.requireNonNullElse(args, List.of()));
        this.env = Map.copyOf(Objects.requireNonNullElse(env, Map.of()));
        this.workingDir = Objects.requireNonNullElse(workingDir, "").trim();
        this.callTimeout = Objects.requireNonNullElse(callTimeout, Duration.ofSeconds(30));
        this.session = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "mcp-stdio-" + safeThreadName(this.serverName));
            thread.setDaemon(true);
            return thread;
        });
    }

    public boolean initialize() {
        if (closed.get()) {
            return false;
        }
        try {
            return await(session.submit(this::initializeOnSession), "initialize");
        } catch (RuntimeException ex) {
            lastFailureMessage = withProcessDiagnostics(ex.getMessage());
            LOG.warn("MCP stdio initialize failed, server={}, reason={}", serverName, lastFailureMessage);
            return false;
        }
    }

    public List<McpToolDescriptor> listTools() {
        if (!initialize()) {
            return List.of();
        }
        try {
            return await(session.submit(() -> {
                McpJsonRpcResponse response = rpc(METHOD_TOOLS_LIST, objectMapper.createObjectNode());
                if (!response.success()) {
                    LOG.warn("MCP stdio tool list failed, server={}, reason={}", serverName, response.errorMessage());
                    return List.of();
                }
                return toDescriptors(response.result());
            }), "tools/list");
        } catch (RuntimeException ex) {
            LOG.warn("MCP stdio tool list failed, server={}, reason={}", serverName, ex.getMessage());
            return List.of();
        }
    }

    @Override
    public McpToolExecutionResult call(McpToolExecutionRequest request) {
        Objects.requireNonNull(request, "request must not be null");
        if (!initialize()) {
            String message = lastFailureMessage == null || lastFailureMessage.isBlank()
                    ? "MCP stdio client is not initialized"
                    : "MCP stdio client is not initialized: " + lastFailureMessage;
            return McpToolExecutionResult.failed(request.toolId(), message);
        }
        try {
            return await(session.submit(() -> callOnSession(request)), "tools/call");
        } catch (RuntimeException ex) {
            return McpToolExecutionResult.failed(request.toolId(), ex.getMessage());
        }
    }

    private boolean initializeOnSession() throws IOException {
        if (initialized && process != null && process.isAlive()) {
            return true;
        }
        closeProcess();
        startProcess();
        ObjectNode params = objectMapper.createObjectNode();
        params.put("protocolVersion", "2026-02-28");
        ObjectNode clientInfo = objectMapper.createObjectNode();
        clientInfo.put("name", "seahorse-agent");
        clientInfo.put("version", "1.0.0");
        params.set("clientInfo", clientInfo);
        McpJsonRpcResponse response = rpc(METHOD_INITIALIZE, params);
        if (!response.success()) {
            throw new IOException(response.errorMessage());
        }
        notify(METHOD_INITIALIZED);
        initialized = true;
        return true;
    }

    private void startProcess() throws IOException {
        if (command.isBlank()) {
            throw new IOException("stdio command is blank");
        }
        List<String> commandLine = new ArrayList<>();
        commandLine.add(command);
        commandLine.addAll(args);
        ProcessBuilder processBuilder = new ProcessBuilder(commandLine)
                .redirectError(ProcessBuilder.Redirect.PIPE);
        if (!env.isEmpty()) {
            processBuilder.environment().putAll(env);
        }
        if (!workingDir.isBlank()) {
            processBuilder.directory(new File(workingDir));
        }
        process = processBuilder.start();
        stdin = new BufferedWriter(new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8));
        stdout = new BufferedReader(new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8));
        stderr = new BufferedReader(new InputStreamReader(process.getErrorStream(), StandardCharsets.UTF_8));
        startStderrDrainer();
    }

    private McpToolExecutionResult callOnSession(McpToolExecutionRequest request) throws IOException {
        ObjectNode params = objectMapper.createObjectNode();
        params.put("name", request.toolId());
        params.set("arguments", objectMapper.valueToTree(request.arguments()));
        McpJsonRpcResponse response = rpc(METHOD_TOOLS_CALL, params);
        if (!response.success()) {
            return McpToolExecutionResult.failed(request.toolId(), response.errorMessage());
        }
        return toExecutionResult(request.toolId(), response.result());
    }

    private McpJsonRpcResponse rpc(String method, JsonNode params) throws IOException {
        ObjectNode request = objectMapper.createObjectNode();
        long id = requestId.getAndIncrement();
        request.put("jsonrpc", JSON_RPC_VERSION);
        request.put("id", id);
        request.put("method", method);
        request.set("params", params);
        writeJson(request);
        String line;
        while ((line = stdout.readLine()) != null) {
            if (line.isBlank()) {
                continue;
            }
            JsonNode response = objectMapper.readTree(line);
            if (!response.has("id") || response.get("id").asLong() != id) {
                continue;
            }
            if (response.hasNonNull(FIELD_ERROR)) {
                return new McpJsonRpcResponse(null, errorMessage(method, response.get(FIELD_ERROR)));
            }
            return new McpJsonRpcResponse(response.get(FIELD_RESULT), "");
        }
        return new McpJsonRpcResponse(null, withProcessDiagnostics("stdio closed before " + method + " response"));
    }

    private void notify(String method) throws IOException {
        ObjectNode notification = objectMapper.createObjectNode();
        notification.put("jsonrpc", JSON_RPC_VERSION);
        notification.put("method", method);
        writeJson(notification);
    }

    private void writeJson(JsonNode node) throws IOException {
        try {
            stdin.write(objectMapper.writeValueAsString(node));
            stdin.newLine();
            stdin.flush();
        } catch (JsonProcessingException ex) {
            throw new IOException("JSON serialization failed", ex);
        }
    }

    private <T> T await(Future<T> future, String operation) {
        try {
            return future.get(Math.max(1, callTimeout.toMillis()), TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            destroyProcess();
            throw new IllegalStateException(withProcessDiagnostics("stdio MCP timeout during " + operation), ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("stdio MCP interrupted during " + operation, ex);
        } catch (ExecutionException ex) {
            Throwable cause = ex.getCause();
            throw new IllegalStateException(withProcessDiagnostics(
                    Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getName())),
                    cause);
        }
    }

    private void startStderrDrainer() {
        synchronized (stderrLock) {
            stderrTail.setLength(0);
        }
        BufferedReader capturedStderr = stderr;
        stderrThread = new Thread(() -> drainStderr(capturedStderr),
                "mcp-stdio-stderr-" + safeThreadName(serverName));
        stderrThread.setDaemon(true);
        stderrThread.start();
    }

    private void drainStderr(BufferedReader reader) {
        if (reader == null) {
            return;
        }
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                appendStderr(line);
            }
        } catch (IOException ignored) {
            // stderr is best-effort diagnostic data.
        }
    }

    private void appendStderr(String line) {
        synchronized (stderrLock) {
            if (!stderrTail.isEmpty()) {
                stderrTail.append(System.lineSeparator());
            }
            stderrTail.append(line);
            if (stderrTail.length() > STDERR_TAIL_LIMIT) {
                stderrTail.delete(0, stderrTail.length() - STDERR_TAIL_LIMIT);
            }
        }
    }

    private String withProcessDiagnostics(String message) {
        waitForProcessExitBriefly();
        String safeMessage = Objects.requireNonNullElse(message, "").trim();
        String stderrText = stderrSnapshot();
        if (stderrText.isBlank()) {
            return safeMessage;
        }
        if (safeMessage.contains(stderrText)) {
            return safeMessage;
        }
        return safeMessage + " stderr: " + stderrText;
    }

    private String stderrSnapshot() {
        synchronized (stderrLock) {
            return stderrTail.toString().trim();
        }
    }

    private void waitForProcessExitBriefly() {
        if (process == null) {
            return;
        }
        try {
            process.waitFor(200, TimeUnit.MILLISECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private List<McpToolDescriptor> toDescriptors(JsonNode result) {
        if (result == null || !result.has("tools") || !result.get("tools").isArray()) {
            return List.of();
        }
        List<McpToolDescriptor> descriptors = new ArrayList<>();
        for (JsonNode toolNode : result.get("tools")) {
            McpToolDescriptor descriptor = toDescriptor(toolNode);
            if (!descriptor.toolId().isBlank()) {
                descriptors.add(descriptor);
            }
        }
        return descriptors;
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

    private McpToolExecutionResult toExecutionResult(String toolId, JsonNode result) {
        if (result == null || result.isNull()) {
            return McpToolExecutionResult.failed(toolId, "stdio tool returned empty result");
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

    private String errorMessage(String method, JsonNode error) {
        JsonNode message = error.get("message");
        if (message != null && !message.isNull()) {
            return method + " failed: " + message.asText();
        }
        return method + " failed";
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

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        Future<?> future = session.submit(this::closeProcess);
        try {
            future.get(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        } catch (ExecutionException | TimeoutException ex) {
            destroyProcess();
        } finally {
            session.shutdownNow();
        }
    }

    private void closeProcess() {
        initialized = false;
        closeQuietly(stdin);
        closeQuietly(stdout);
        closeQuietly(stderr);
        stdin = null;
        stdout = null;
        stderr = null;
        stderrThread = null;
        if (process != null) {
            process.destroy();
            try {
                if (!process.waitFor(2, TimeUnit.SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
            process = null;
        }
    }

    private void destroyProcess() {
        if (process != null) {
            process.destroyForcibly();
        }
    }

    private void closeQuietly(AutoCloseable closeable) {
        if (closeable == null) {
            return;
        }
        try {
            closeable.close();
        } catch (Exception ignored) {
            // best effort cleanup
        }
    }

    private static String safeThreadName(String name) {
        if (name == null || name.isBlank()) {
            return "default";
        }
        return name.replaceAll("[^A-Za-z0-9_.-]", "_");
    }
}

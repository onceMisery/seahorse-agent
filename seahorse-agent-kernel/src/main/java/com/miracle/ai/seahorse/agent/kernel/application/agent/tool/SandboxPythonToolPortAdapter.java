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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationRequestAwarePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SandboxPythonToolPortAdapter implements DescribedToolPort, ToolInvocationRequestAwarePort {

    public static final String TOOL_ID = "sandbox_python";
    private static final int MAX_CODE_CHARS = 20_000;
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_ID,
            "Sandbox Python",
            "Execute a small Python script through the Seahorse sandbox runtime. Use for bounded calculations, parsing, and data transformations that need code execution.",
            """
                    {"type":"object","required":["code"],"properties":{"code":{"type":"string","minLength":1,"maxLength":20000},"networkRequested":{"type":"boolean","default":false},"requestedHosts":{"type":"array","items":{"type":"string"},"default":[]}}}
                    """);

    private final SandboxRuntimeInboundPort sandboxRuntime;
    private final AgentToolJsonSupport jsonSupport;

    public SandboxPythonToolPortAdapter(SandboxRuntimeInboundPort sandboxRuntime,
                                        AgentToolJsonSupport jsonSupport) {
        this.sandboxRuntime = Objects.requireNonNull(sandboxRuntime, "sandboxRuntime must not be null");
        this.jsonSupport = Objects.requireNonNull(jsonSupport, "jsonSupport must not be null");
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        String safeCallId = hasText(toolCallId) ? toolCallId.trim() : "direct";
        return invoke(new ToolInvocationRequest(
                "sandbox-tool-" + safeCallId,
                safeCallId,
                safeCallId,
                null,
                null,
                null,
                null,
                null,
                null,
                TOOL_ID,
                arguments,
                Map.of(),
                null,
                List.of(TOOL_ID)));
    }

    @Override
    public ToolInvocationResult invoke(ToolInvocationRequest request) {
        ToolInvocationRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String code = jsonSupport.string(safeRequest.arguments(), "code");
        if (code.isBlank()) {
            return ToolInvocationResult.failed("sandbox_python failed: code is required");
        }
        if (code.length() > MAX_CODE_CHARS) {
            return ToolInvocationResult.failed("sandbox_python failed: code exceeds " + MAX_CODE_CHARS + " chars");
        }
        boolean networkRequested = booleanArgument(safeRequest.arguments(), "networkRequested");
        List<String> requestedHosts = stringList(safeRequest.arguments().get("requestedHosts"));
        SandboxSession session = null;
        try {
            session = sandboxRuntime.createSession(new SandboxSessionCreateCommand(
                    safeRequest.tenantId(),
                    sandboxRunId(safeRequest),
                    SandboxRuntimeType.CODE_INTERPRETER,
                    networkRequested,
                    requestedHosts));
            if (session.status().isTerminal()) {
                return failed(observation(session, null, List.of(), null),
                        "sandbox session did not start: " + session.reasonCode());
            }
            SandboxExecutionResult result = sandboxRuntime.execute(new SandboxExecutionCommand(
                    session.sessionId(),
                    code,
                    networkRequested,
                    requestedHosts));
            Map<String, Object> observation = observation(session, result.execution(), result.artifacts(), null);
            if (result.execution().status() == SandboxExecutionStatus.SUCCEEDED) {
                return ToolInvocationResult.ok(jsonSupport.write(observation));
            }
            return failed(observation,
                    "sandbox execution " + result.execution().status() + ": " + result.reasonCode());
        } catch (Exception ex) {
            return ToolInvocationResult.failed("sandbox_python failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        } finally {
            closeQuietly(session);
        }
    }

    private ToolInvocationResult failed(Map<String, Object> observation, String summary) {
        String payload = jsonSupport.write(observation);
        return ToolInvocationResult.failed(summary + "; observation=" + payload);
    }

    private Map<String, Object> observation(SandboxSession session,
                                            SandboxExecution execution,
                                            List<SandboxArtifact> artifacts,
                                            String closeStatus) {
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("toolId", TOOL_ID);
        observation.put("sessionId", session == null ? null : session.sessionId());
        observation.put("runtimeType", SandboxRuntimeType.CODE_INTERPRETER.name());
        observation.put("sessionStatus", session == null ? null : session.status().name());
        observation.put("sessionReasonCode", session == null ? null : session.reasonCode().name());
        observation.put("executionId", execution == null ? null : execution.executionId());
        observation.put("executionStatus", execution == null ? null : execution.status().name());
        observation.put("reasonCode", execution == null ? null : execution.reasonCode().name());
        observation.put("resultSummary", execution == null ? null : execution.resultSummary());
        observation.put("artifacts", artifacts(artifacts));
        if (closeStatus != null) {
            observation.put("closeStatus", closeStatus);
        }
        return observation;
    }

    private List<Map<String, Object>> artifacts(List<SandboxArtifact> artifacts) {
        return Objects.requireNonNullElse(artifacts, List.<SandboxArtifact>of()).stream()
                .map(artifact -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("artifactId", artifact.artifactId());
                    item.put("executionId", artifact.executionId());
                    item.put("mediaType", artifact.mediaType());
                    item.put("scanStatus", artifact.scanStatus().name());
                    item.put("sensitivity", artifact.sensitivity().name());
                    item.put("promptVisible", artifact.promptVisible());
                    return item;
                })
                .toList();
    }

    private void closeQuietly(SandboxSession session) {
        if (session == null || session.status().isTerminal()) {
            return;
        }
        try {
            sandboxRuntime.close(session.sessionId());
        } catch (RuntimeException ignored) {
            // Tool observations are about execution; close is best-effort cleanup here.
        }
    }

    private String sandboxRunId(ToolInvocationRequest request) {
        if (hasText(request.runId())) {
            return request.runId().trim();
        }
        return "sandbox-tool-" + request.toolCallId();
    }

    private boolean booleanArgument(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        return value != null && Boolean.parseBoolean(value.toString());
    }

    private List<String> stringList(Object value) {
        if (value instanceof Iterable<?> iterable) {
            return java.util.stream.StreamSupport.stream(iterable.spliterator(), false)
                    .filter(Objects::nonNull)
                    .map(Object::toString)
                    .map(String::trim)
                    .filter(item -> !item.isBlank())
                    .distinct()
                    .toList();
        }
        if (value == null || value.toString().isBlank()) {
            return List.of();
        }
        return List.of(value.toString().trim());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

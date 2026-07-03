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
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SandboxBrowserToolPortAdapter implements DescribedToolPort, ToolInvocationRequestAwarePort {

    public static final String TOOL_ID = "sandbox_browser";
    private static final int MAX_HTML_CHARS = 256 * 1024;
    private static final String ACTION_ARGUMENT = "action";
    private static final String HTML_ARGUMENT = "html";
    private static final String SCREENSHOT_ARGUMENT = "screenshot";
    private static final String VIEWPORT_WIDTH_ARGUMENT = "viewportWidth";
    private static final String VIEWPORT_HEIGHT_ARGUMENT = "viewportHeight";
    private static final String ACTION_SNAPSHOT = "snapshot";
    private static final String ACTION_EXTRACT_TEXT = "extract_text";
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(ACTION_SNAPSHOT, ACTION_EXTRACT_TEXT);
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_ID,
            "Sandbox Browser",
            "Render bounded inline HTML through a no-network Playwright browser sandbox. Supports page snapshots, text extraction, and governed screenshot/result artifacts.",
            """
                    {"type":"object","required":["html"],"properties":{"html":{"type":"string","minLength":1,"maxLength":262144},"action":{"type":"string","enum":["snapshot","extract_text"],"default":"snapshot"},"screenshot":{"type":"boolean","default":true},"viewportWidth":{"type":"integer","minimum":320,"maximum":2400,"default":1280},"viewportHeight":{"type":"integer","minimum":320,"maximum":2400,"default":720}}}
                    """);

    private final SandboxRuntimeInboundPort sandboxRuntime;
    private final AgentToolJsonSupport jsonSupport;

    public SandboxBrowserToolPortAdapter(SandboxRuntimeInboundPort sandboxRuntime,
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
                "sandbox-browser-" + safeCallId,
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
        String action = normalizedAction(jsonSupport.string(safeRequest.arguments(), ACTION_ARGUMENT));
        if (!SUPPORTED_ACTIONS.contains(action)) {
            return ToolInvocationResult.failed(
                    "sandbox_browser failed: supported actions are snapshot and extract_text");
        }
        String html = argumentStringPreservingWhitespace(safeRequest.arguments(), HTML_ARGUMENT);
        if (html.isBlank()) {
            return ToolInvocationResult.failed("sandbox_browser failed: html is required");
        }
        if (html.length() > MAX_HTML_CHARS) {
            return ToolInvocationResult.failed("sandbox_browser failed: html exceeds " + MAX_HTML_CHARS + " chars");
        }
        int viewportWidth = jsonSupport.boundedInt(
                safeRequest.arguments(),
                VIEWPORT_WIDTH_ARGUMENT,
                1280,
                320,
                2400);
        int viewportHeight = jsonSupport.boundedInt(
                safeRequest.arguments(),
                VIEWPORT_HEIGHT_ARGUMENT,
                720,
                320,
                2400);
        boolean screenshot = booleanArgument(
                safeRequest.arguments(),
                SCREENSHOT_ARGUMENT,
                ACTION_SNAPSHOT.equals(action));
        SandboxSession session = null;
        try {
            session = sandboxRuntime.createSession(new SandboxSessionCreateCommand(
                    safeRequest.tenantId(),
                    sandboxRunId(safeRequest),
                    SandboxRuntimeType.BROWSER_AUTOMATION,
                    false,
                    List.of()));
            if (session.status().isTerminal()) {
                return failed(observation(session, null, List.of(), action, viewportWidth, viewportHeight),
                        "sandbox browser session did not start: " + session.reasonCode());
            }
            SandboxExecutionResult result = sandboxRuntime.execute(new SandboxExecutionCommand(
                    session.sessionId(),
                    browserInput(action, html, viewportWidth, viewportHeight, screenshot),
                    false,
                    List.of()));
            Map<String, Object> observation = observation(
                    session,
                    result.execution(),
                    result.artifacts(),
                    action,
                    viewportWidth,
                    viewportHeight);
            if (result.execution().status() == SandboxExecutionStatus.SUCCEEDED) {
                return ToolInvocationResult.ok(jsonSupport.write(observation));
            }
            return failed(observation, "sandbox browser " + result.execution().status() + ": " + result.reasonCode());
        } catch (Exception ex) {
            return ToolInvocationResult.failed("sandbox_browser failed: "
                    + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        } finally {
            closeQuietly(session);
        }
    }

    private String browserInput(String action,
                                String html,
                                int viewportWidth,
                                int viewportHeight,
                                boolean screenshot) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", action);
        input.put("html", html);
        input.put("viewportWidth", viewportWidth);
        input.put("viewportHeight", viewportHeight);
        input.put("screenshot", screenshot);
        return jsonSupport.write(input);
    }

    private ToolInvocationResult failed(Map<String, Object> observation, String summary) {
        String payload = jsonSupport.write(observation);
        return ToolInvocationResult.failed(summary + "; observation=" + payload);
    }

    private Map<String, Object> observation(SandboxSession session,
                                            SandboxExecution execution,
                                            List<SandboxArtifact> artifacts,
                                            String action,
                                            int viewportWidth,
                                            int viewportHeight) {
        Map<String, Object> observation = new LinkedHashMap<>();
        observation.put("toolId", TOOL_ID);
        observation.put("sessionId", session == null ? null : session.sessionId());
        observation.put("runtimeType", SandboxRuntimeType.BROWSER_AUTOMATION.name());
        observation.put("sessionStatus", session == null ? null : session.status().name());
        observation.put("sessionReasonCode", session == null ? null : session.reasonCode().name());
        observation.put("executionId", execution == null ? null : execution.executionId());
        observation.put("executionStatus", execution == null ? null : execution.status().name());
        observation.put("reasonCode", execution == null ? null : execution.reasonCode().name());
        observation.put("resultSummary", execution == null ? null : execution.resultSummary());
        observation.put("browser", browser(action, viewportWidth, viewportHeight));
        observation.put("artifacts", artifacts(artifacts));
        return observation;
    }

    private Map<String, Object> browser(String action, int viewportWidth, int viewportHeight) {
        Map<String, Object> browser = new LinkedHashMap<>();
        browser.put("action", action);
        browser.put("viewportWidth", viewportWidth);
        browser.put("viewportHeight", viewportHeight);
        browser.put("networkAllowed", false);
        return browser;
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
                    item.put("scanSummary", artifact.scanSummary());
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
        return "sandbox-browser-" + request.toolCallId();
    }

    private boolean booleanArgument(Map<String, Object> arguments, String name, boolean defaultValue) {
        Object value = arguments == null ? null : arguments.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private String argumentStringPreservingWhitespace(Map<String, Object> arguments, String name) {
        Object value = arguments == null ? null : arguments.get(name);
        return value == null ? "" : value.toString();
    }

    private String normalizedAction(String action) {
        if (!hasText(action)) {
            return ACTION_SNAPSHOT;
        }
        return action.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

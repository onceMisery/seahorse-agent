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

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class SandboxBrowserToolPortAdapter implements DescribedToolPort, ToolInvocationRequestAwarePort {

    public static final String TOOL_ID = "sandbox_browser";
    private static final int MAX_HTML_CHARS = 256 * 1024;
    private static final int MAX_URL_CHARS = 2048;
    private static final int MAX_ALLOWED_HOSTS = 16;
    private static final String ACTION_ARGUMENT = "action";
    private static final String HTML_ARGUMENT = "html";
    private static final String URL_ARGUMENT = "url";
    private static final String ALLOWED_HOSTS_ARGUMENT = "allowedHosts";
    private static final String SCREENSHOT_ARGUMENT = "screenshot";
    private static final String HAR_ARGUMENT = "har";
    private static final String VIDEO_ARGUMENT = "video";
    private static final String VIEWPORT_WIDTH_ARGUMENT = "viewportWidth";
    private static final String VIEWPORT_HEIGHT_ARGUMENT = "viewportHeight";
    private static final String ACTION_SNAPSHOT = "snapshot";
    private static final String ACTION_EXTRACT_TEXT = "extract_text";
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(ACTION_SNAPSHOT, ACTION_EXTRACT_TEXT);
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_ID,
            "Sandbox Browser",
            "Render bounded inline HTML or an explicitly allowlisted HTTP/HTTPS URL through a Playwright browser sandbox. Inline HTML stays no-network; URL mode requires allowedHosts and returns governed screenshot/result/HAR/video artifacts.",
            """
                    {"type":"object","properties":{"html":{"type":"string","minLength":1,"maxLength":262144},"url":{"type":"string","minLength":1,"maxLength":2048,"description":"HTTP/HTTPS URL to visit. Requires allowedHosts and sandbox egress policy."},"allowedHosts":{"type":"array","items":{"type":"string"},"maxItems":16,"default":[],"description":"Exact host allowlist for URL mode. The URL host must be included."},"action":{"type":"string","enum":["snapshot","extract_text"],"default":"snapshot"},"screenshot":{"type":"boolean","default":true},"har":{"type":"boolean","default":false},"video":{"type":"boolean","default":false},"viewportWidth":{"type":"integer","minimum":320,"maximum":2400,"default":1280},"viewportHeight":{"type":"integer","minimum":320,"maximum":2400,"default":720}},"anyOf":[{"required":["html"]},{"required":["url","allowedHosts"]}]}
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
        String url;
        List<String> allowedHosts;
        String urlHost;
        try {
            url = normalizedUrl(jsonSupport.string(safeRequest.arguments(), URL_ARGUMENT));
            allowedHosts = normalizedAllowedHosts(safeRequest.arguments().get(ALLOWED_HOSTS_ARGUMENT));
            urlHost = hasText(url) ? urlHost(url) : "";
        } catch (IllegalArgumentException ex) {
            return ToolInvocationResult.failed(ex.getMessage());
        }
        boolean urlMode = hasText(url);
        if (!urlMode && html.isBlank()) {
            return ToolInvocationResult.failed("sandbox_browser failed: html is required");
        }
        if (urlMode && allowedHosts.isEmpty()) {
            return ToolInvocationResult.failed("sandbox_browser failed: allowedHosts is required for url mode");
        }
        if (urlMode && !allowedHosts.contains(urlHost)) {
            return ToolInvocationResult.failed("sandbox_browser failed: url host must be included in allowedHosts");
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
        boolean har = booleanArgument(
                safeRequest.arguments(),
                HAR_ARGUMENT,
                false);
        boolean video = booleanArgument(
                safeRequest.arguments(),
                VIDEO_ARGUMENT,
                false);
        boolean networkRequested = urlMode;
        List<String> requestedHosts = urlMode ? allowedHosts : List.of();
        SandboxSession session = null;
        try {
            session = sandboxRuntime.createSession(new SandboxSessionCreateCommand(
                    safeRequest.tenantId(),
                    sandboxRunId(safeRequest),
                    SandboxRuntimeType.BROWSER_AUTOMATION,
                    networkRequested,
                    requestedHosts));
            if (session.status().isTerminal()) {
                return failed(observation(
                                session,
                                null,
                                List.of(),
                                action,
                                url,
                                requestedHosts,
                                networkRequested,
                                viewportWidth,
                                viewportHeight,
                                har,
                                video),
                        "sandbox browser session did not start: " + session.reasonCode());
            }
            SandboxExecutionResult result = sandboxRuntime.execute(new SandboxExecutionCommand(
                    session.sessionId(),
                    browserInput(action, html, url, requestedHosts, viewportWidth, viewportHeight, screenshot, har, video),
                    networkRequested,
                    requestedHosts));
            Map<String, Object> observation = observation(
                    session,
                    result.execution(),
                    result.artifacts(),
                    action,
                    url,
                    requestedHosts,
                    networkRequested,
                    viewportWidth,
                    viewportHeight,
                    har,
                    video);
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
                                String url,
                                List<String> allowedHosts,
                                int viewportWidth,
                                int viewportHeight,
                                boolean screenshot,
                                boolean har,
                                boolean video) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", action);
        input.put("html", html);
        input.put("url", url);
        input.put("allowedHosts", allowedHosts);
        input.put("viewportWidth", viewportWidth);
        input.put("viewportHeight", viewportHeight);
        input.put("screenshot", screenshot);
        input.put("har", har);
        input.put("video", video);
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
                                            String url,
                                            List<String> allowedHosts,
                                            boolean networkRequested,
                                            int viewportWidth,
                                            int viewportHeight,
                                            boolean har,
                                            boolean video) {
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
        observation.put("browser", browser(action, url, allowedHosts, networkRequested, viewportWidth, viewportHeight, har, video));
        observation.put("artifacts", artifacts(artifacts));
        return observation;
    }

    private Map<String, Object> browser(String action,
                                        String url,
                                        List<String> allowedHosts,
                                        boolean networkRequested,
                                        int viewportWidth,
                                        int viewportHeight,
                                        boolean har,
                                        boolean video) {
        Map<String, Object> browser = new LinkedHashMap<>();
        browser.put("action", action);
        browser.put("url", hasText(url) ? url : null);
        browser.put("allowedHosts", allowedHosts == null ? List.of() : List.copyOf(allowedHosts));
        browser.put("viewportWidth", viewportWidth);
        browser.put("viewportHeight", viewportHeight);
        browser.put("networkAllowed", networkRequested);
        browser.put("har", har);
        browser.put("video", video);
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
                    item.put("redactionSummaryJson", artifact.redactionSummaryJson());
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

    private String normalizedUrl(String value) {
        if (!hasText(value)) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_URL_CHARS) {
            throw new IllegalArgumentException("sandbox_browser failed: url exceeds " + MAX_URL_CHARS + " chars");
        }
        try {
            URI uri = new URI(trimmed);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !hasText(uri.getHost())) {
                throw new IllegalArgumentException("sandbox_browser failed: url must be an HTTP/HTTPS URL with a host");
            }
            return uri.normalize().toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("sandbox_browser failed: url is not valid", ex);
        }
    }

    private String urlHost(String url) {
        try {
            return new URI(url).getHost().toLowerCase(Locale.ROOT);
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("sandbox_browser failed: url is not valid", ex);
        }
    }

    private List<String> normalizedAllowedHosts(Object value) {
        if (value == null) {
            return List.of();
        }
        List<?> rawValues;
        if (value instanceof List<?> list) {
            rawValues = list;
        } else if (value instanceof String text) {
            rawValues = List.of(text.split(","));
        } else {
            rawValues = List.of(value);
        }
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        for (Object rawValue : rawValues) {
            String host = normalizedHost(rawValue == null ? "" : rawValue.toString());
            if (hasText(host)) {
                hosts.add(host);
            }
        }
        if (hosts.size() > MAX_ALLOWED_HOSTS) {
            throw new IllegalArgumentException("sandbox_browser failed: allowedHosts exceeds " + MAX_ALLOWED_HOSTS + " hosts");
        }
        return new ArrayList<>(hosts);
    }

    private String normalizedHost(String value) {
        if (!hasText(value)) {
            return "";
        }
        String host = value.trim().toLowerCase(Locale.ROOT);
        if (host.contains("/") || host.contains(":") || !host.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("sandbox_browser failed: allowedHosts must contain host names only");
        }
        return host;
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

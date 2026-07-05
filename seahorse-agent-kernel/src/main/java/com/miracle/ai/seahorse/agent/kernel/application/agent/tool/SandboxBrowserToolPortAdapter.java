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
    private static final int MAX_COOKIES = 16;
    private static final int MAX_COOKIE_NAME_CHARS = 128;
    private static final int MAX_COOKIE_VALUE_CHARS = 4096;
    private static final int MAX_SESSION_STATE_CHARS = 128 * 1024;
    private static final int MAX_SESSION_STATE_COOKIES = 32;
    private static final int MAX_SESSION_STATE_ORIGINS = 16;
    private static final int MAX_SESSION_STATE_LOCAL_STORAGE_ITEMS = 128;
    private static final int MAX_SESSION_STATE_NAME_CHARS = 256;
    private static final int MAX_SESSION_STATE_VALUE_CHARS = 8192;
    private static final String ACTION_ARGUMENT = "action";
    private static final String HTML_ARGUMENT = "html";
    private static final String URL_ARGUMENT = "url";
    private static final String ALLOWED_HOSTS_ARGUMENT = "allowedHosts";
    private static final String COOKIES_ARGUMENT = "cookies";
    private static final String SESSION_STATE_ARGUMENT = "sessionState";
    private static final String SCREENSHOT_ARGUMENT = "screenshot";
    private static final String HAR_ARGUMENT = "har";
    private static final String VIDEO_ARGUMENT = "video";
    private static final String CAPTURE_SESSION_STATE_ARGUMENT = "captureSessionState";
    private static final String VIEWPORT_WIDTH_ARGUMENT = "viewportWidth";
    private static final String VIEWPORT_HEIGHT_ARGUMENT = "viewportHeight";
    private static final String ACTION_SNAPSHOT = "snapshot";
    private static final String ACTION_EXTRACT_TEXT = "extract_text";
    private static final Set<String> SUPPORTED_ACTIONS = Set.of(ACTION_SNAPSHOT, ACTION_EXTRACT_TEXT);
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_ID,
            "Sandbox Browser",
            "Render bounded inline HTML or an explicitly allowlisted HTTP/HTTPS URL through a Playwright browser sandbox. Inline HTML stays no-network; URL mode requires allowedHosts, can inject bounded host-scoped cookies, can replay explicit request-scoped Playwright session state, and can capture governed browser session state without exposing values in observations.",
            """
                    {"type":"object","properties":{"html":{"type":"string","minLength":1,"maxLength":262144},"url":{"type":"string","minLength":1,"maxLength":2048,"description":"HTTP/HTTPS URL to visit. Requires allowedHosts and sandbox egress policy."},"allowedHosts":{"type":"array","items":{"type":"string"},"maxItems":16,"default":[],"description":"Exact host allowlist for URL mode. The URL host must be included."},"cookies":{"type":"array","maxItems":16,"description":"Optional URL-mode cookies. Each cookie domain must match an allowed host; cookie values are injected into the sandbox but omitted from observations.","items":{"type":"object","required":["name","value"],"properties":{"name":{"type":"string","minLength":1,"maxLength":128},"value":{"type":"string","maxLength":4096},"domain":{"type":"string","description":"Host-only cookie domain. Defaults to the URL host and must be in allowedHosts."},"path":{"type":"string","default":"/"},"httpOnly":{"type":"boolean","default":true},"secure":{"type":"boolean","default":false},"sameSite":{"type":"string","enum":["Lax","Strict","None"],"default":"Lax"}}}},"sessionState":{"type":"object","description":"Optional URL-mode Playwright storageState object for one-run replay. Cookie/localStorage values are written only to transient runtime input and omitted from observations and prompt-visible artifacts.","properties":{"cookies":{"type":"array","maxItems":32},"origins":{"type":"array","maxItems":16}}},"captureSessionState":{"type":"boolean","default":false,"description":"URL-mode only. Captures a governed browser storage-state artifact plus a value-free summary; secret values are omitted from observations and prompt-visible artifacts."},"action":{"type":"string","enum":["snapshot","extract_text"],"default":"snapshot"},"screenshot":{"type":"boolean","default":true},"har":{"type":"boolean","default":false},"video":{"type":"boolean","default":false},"viewportWidth":{"type":"integer","minimum":320,"maximum":2400,"default":1280},"viewportHeight":{"type":"integer","minimum":320,"maximum":2400,"default":720}},"anyOf":[{"required":["html"]},{"required":["url","allowedHosts"]}]}
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
        String urlOrigin;
        try {
            url = normalizedUrl(jsonSupport.string(safeRequest.arguments(), URL_ARGUMENT));
            allowedHosts = normalizedAllowedHosts(safeRequest.arguments().get(ALLOWED_HOSTS_ARGUMENT));
            urlHost = hasText(url) ? urlHost(url) : "";
            urlOrigin = hasText(url) ? urlOrigin(url, "url") : "";
        } catch (IllegalArgumentException ex) {
            return ToolInvocationResult.failed(ex.getMessage());
        }
        boolean urlMode = hasText(url);
        List<BrowserCookie> cookies;
        try {
            cookies = normalizedCookies(
                    safeRequest.arguments().get(COOKIES_ARGUMENT),
                    urlHost,
                    allowedHosts,
                    urlMode);
        } catch (IllegalArgumentException ex) {
            return ToolInvocationResult.failed(ex.getMessage());
        }
        Object sessionState;
        try {
            sessionState = normalizedSessionState(
                    safeRequest.arguments().get(SESSION_STATE_ARGUMENT),
                    allowedHosts,
                    urlHost,
                    urlOrigin,
                    urlMode);
        } catch (IllegalArgumentException ex) {
            return ToolInvocationResult.failed(ex.getMessage());
        }
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
        boolean captureSessionState = booleanArgument(
                safeRequest.arguments(),
                CAPTURE_SESSION_STATE_ARGUMENT,
                false);
        if (captureSessionState && !urlMode) {
            return ToolInvocationResult.failed("sandbox_browser failed: captureSessionState is only supported for url mode");
        }
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
                                cookies,
                                sessionState,
                                networkRequested,
                                viewportWidth,
                                viewportHeight,
                                har,
                                video,
                                captureSessionState),
                        "sandbox browser session did not start: " + session.reasonCode());
            }
            SandboxExecutionResult result = sandboxRuntime.execute(new SandboxExecutionCommand(
                    session.sessionId(),
                    browserInput(action, html, url, requestedHosts, cookies, sessionState, viewportWidth, viewportHeight, screenshot, har, video, captureSessionState),
                    networkRequested,
                    requestedHosts));
            Map<String, Object> observation = observation(
                    session,
                    result.execution(),
                    result.artifacts(),
                    action,
                    url,
                    requestedHosts,
                    cookies,
                    sessionState,
                    networkRequested,
                    viewportWidth,
                    viewportHeight,
                    har,
                    video,
                    captureSessionState);
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
                                List<BrowserCookie> cookies,
                                Object sessionState,
                                int viewportWidth,
                                int viewportHeight,
                                boolean screenshot,
                                boolean har,
                                boolean video,
                                boolean captureSessionState) {
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("action", action);
        input.put("html", html);
        input.put("url", url);
        input.put("allowedHosts", allowedHosts);
        if (cookies != null && !cookies.isEmpty()) {
            input.put("cookies", cookiesForRuntime(cookies));
        }
        if (sessionState != null) {
            input.put("sessionState", sessionState);
        }
        input.put("viewportWidth", viewportWidth);
        input.put("viewportHeight", viewportHeight);
        input.put("screenshot", screenshot);
        input.put("har", har);
        input.put("video", video);
        input.put("captureSessionState", captureSessionState);
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
                                            List<BrowserCookie> cookies,
                                            Object sessionState,
                                            boolean networkRequested,
                                            int viewportWidth,
                                            int viewportHeight,
                                            boolean har,
                                            boolean video,
                                            boolean captureSessionState) {
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
        observation.put("browser", browser(action, url, allowedHosts, cookies, sessionState, networkRequested, viewportWidth, viewportHeight, har, video, captureSessionState));
        observation.put("artifacts", artifacts(artifacts));
        return observation;
    }

    private Map<String, Object> browser(String action,
                                        String url,
                                        List<String> allowedHosts,
                                        List<BrowserCookie> cookies,
                                        Object sessionState,
                                        boolean networkRequested,
                                        int viewportWidth,
                                        int viewportHeight,
                                        boolean har,
                                        boolean video,
                                        boolean captureSessionState) {
        Map<String, Object> browser = new LinkedHashMap<>();
        browser.put("action", action);
        browser.put("url", hasText(url) ? url : null);
        browser.put("allowedHosts", allowedHosts == null ? List.of() : List.copyOf(allowedHosts));
        browser.put("cookieCount", cookies == null ? 0 : cookies.size());
        browser.put("cookieDomains", cookieDomains(cookies));
        browser.put("viewportWidth", viewportWidth);
        browser.put("viewportHeight", viewportHeight);
        browser.put("networkAllowed", networkRequested);
        browser.put("har", har);
        browser.put("video", video);
        browser.put("sessionState", Map.of(
                "captureRequested", captureSessionState,
                "replayRequested", sessionState != null));
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
            if (hasText(uri.getUserInfo())) {
                throw new IllegalArgumentException("sandbox_browser failed: url must not include userinfo credentials");
            }
            if (hasText(uri.getRawFragment())) {
                throw new IllegalArgumentException("sandbox_browser failed: url must not include fragment identifiers");
            }
            return uri.normalize().toString();
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("sandbox_browser failed: url is not valid", ex);
        }
    }

    private String urlHost(String url) {
        try {
            String host = new URI(url).getHost().toLowerCase(Locale.ROOT);
            validatePublicBrowserHost(host, "url host");
            return host;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("sandbox_browser failed: url is not valid", ex);
        }
    }

    private String urlOrigin(String url, String label) {
        try {
            URI uri = new URI(url);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !hasText(host)) {
                throw new IllegalArgumentException("sandbox_browser failed: " + label + " must be HTTP/HTTPS");
            }
            validatePublicBrowserHost(host, label + " host");
            int port = uri.getPort();
            if (port < 0) {
                port = "https".equals(scheme) ? 443 : 80;
            }
            return scheme + "://" + host + ":" + port;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("sandbox_browser failed: " + label + " is not valid", ex);
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
        validatePublicBrowserHost(host, "allowedHosts");
        return host;
    }

    private Object normalizedSessionState(Object value,
                                          List<String> allowedHosts,
                                          String urlHost,
                                          String urlOrigin,
                                          boolean urlMode) {
        if (value == null) {
            return null;
        }
        if (!urlMode) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState is only supported for url mode");
        }
        if (!(value instanceof Map<?, ?> state)) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState must be an object");
        }
        validateSessionStateCookies(state.get("cookies"), allowedHosts, urlHost);
        validateSessionStateOrigins(state.get("origins"), allowedHosts, urlHost, urlOrigin);
        if (jsonSupport.write(value).length() > MAX_SESSION_STATE_CHARS) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState exceeds "
                    + MAX_SESSION_STATE_CHARS + " chars");
        }
        return value;
    }

    private void validateSessionStateCookies(Object value, List<String> allowedHosts, String urlHost) {
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> cookies)) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState cookies must be an array");
        }
        if (cookies.size() > MAX_SESSION_STATE_COOKIES) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState cookies exceeds "
                    + MAX_SESSION_STATE_COOKIES + " items");
        }
        for (Object rawCookie : cookies) {
            if (!(rawCookie instanceof Map<?, ?> cookie)) {
                throw new IllegalArgumentException("sandbox_browser failed: sessionState cookie must be an object");
            }
            normalizedCookieName(mapString(cookie, "name"));
            normalizedCookieValue(mapStringPreservingWhitespace(cookie, "value"));
            String domain = normalizedCookieDomain(mapString(cookie, "domain"));
            String domainHost = cookieDomainHost(domain);
            if (!allowedHosts.contains(domainHost)) {
                throw new IllegalArgumentException(
                        "sandbox_browser failed: sessionState cookie domain must be included in allowedHosts");
            }
            if (!domainHost.equals(urlHost)) {
                throw new IllegalArgumentException(
                        "sandbox_browser failed: sessionState cookie domain must match the target URL host");
            }
            normalizedCookiePath(mapString(cookie, "path"));
        }
    }

    private void validateSessionStateOrigins(Object value,
                                             List<String> allowedHosts,
                                             String urlHost,
                                             String urlOrigin) {
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> origins)) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState origins must be an array");
        }
        if (origins.size() > MAX_SESSION_STATE_ORIGINS) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState origins exceeds "
                    + MAX_SESSION_STATE_ORIGINS + " items");
        }
        for (Object rawOrigin : origins) {
            if (!(rawOrigin instanceof Map<?, ?> origin)) {
                throw new IllegalArgumentException("sandbox_browser failed: sessionState origin must be an object");
            }
            String originValue = mapString(origin, "origin");
            String host = sessionStateOriginHost(originValue);
            if (!allowedHosts.contains(host)) {
                throw new IllegalArgumentException(
                        "sandbox_browser failed: sessionState origin host must be included in allowedHosts");
            }
            if (!host.equals(urlHost)) {
                throw new IllegalArgumentException(
                        "sandbox_browser failed: sessionState origin host must match the target URL host");
            }
            if (!urlOrigin(originValue, "sessionState origin").equals(urlOrigin)) {
                throw new IllegalArgumentException(
                        "sandbox_browser failed: sessionState origin must match the target URL origin");
            }
            validateSessionStateLocalStorage(origin.get("localStorage"));
        }
    }

    private void validateSessionStateLocalStorage(Object value) {
        if (value == null) {
            return;
        }
        if (!(value instanceof List<?> entries)) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState localStorage must be an array");
        }
        if (entries.size() > MAX_SESSION_STATE_LOCAL_STORAGE_ITEMS) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState localStorage exceeds "
                    + MAX_SESSION_STATE_LOCAL_STORAGE_ITEMS + " items");
        }
        for (Object rawEntry : entries) {
            if (!(rawEntry instanceof Map<?, ?> entry)) {
                throw new IllegalArgumentException(
                        "sandbox_browser failed: sessionState localStorage item must be an object");
            }
            boundedSessionStateText(mapStringPreservingWhitespace(entry, "name"),
                    "sessionState localStorage name",
                    MAX_SESSION_STATE_NAME_CHARS,
                    true);
            boundedSessionStateText(mapStringPreservingWhitespace(entry, "value"),
                    "sessionState localStorage value",
                    MAX_SESSION_STATE_VALUE_CHARS,
                    false);
        }
    }

    private String boundedSessionStateText(String value, String label, int maxChars, boolean required) {
        String text = value == null ? "" : value;
        if ((required && !hasText(text)) || text.length() > maxChars || containsControlCharacter(text)) {
            throw new IllegalArgumentException("sandbox_browser failed: " + label + " is invalid");
        }
        return text;
    }

    private String cookieDomainHost(String domain) {
        String host = domain.startsWith(".") ? domain.substring(1) : domain;
        if (!hasText(host) || !host.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState cookie domain is invalid");
        }
        validatePublicBrowserHost(host, "sessionState cookie domain");
        return host;
    }

    private String sessionStateOriginHost(String origin) {
        if (!hasText(origin)) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState origin is required");
        }
        try {
            URI uri = new URI(origin.trim());
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!Set.of("http", "https").contains(scheme) || !hasText(uri.getHost())) {
                throw new IllegalArgumentException("sandbox_browser failed: sessionState origin must be HTTP/HTTPS");
            }
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            validatePublicBrowserHost(host, "sessionState origin host");
            return host;
        } catch (URISyntaxException ex) {
            throw new IllegalArgumentException("sandbox_browser failed: sessionState origin is not valid", ex);
        }
    }

    private List<BrowserCookie> normalizedCookies(Object value,
                                                  String urlHost,
                                                  List<String> allowedHosts,
                                                  boolean urlMode) {
        if (value == null) {
            return List.of();
        }
        List<?> rawValues;
        if (value instanceof List<?> list) {
            rawValues = list;
        } else {
            throw new IllegalArgumentException("sandbox_browser failed: cookies must be an array");
        }
        if (rawValues.size() > MAX_COOKIES) {
            throw new IllegalArgumentException("sandbox_browser failed: cookies exceeds " + MAX_COOKIES + " items");
        }
        if (rawValues.isEmpty()) {
            return List.of();
        }
        if (!urlMode) {
            throw new IllegalArgumentException("sandbox_browser failed: cookies are only supported for url mode");
        }
        List<BrowserCookie> cookies = new ArrayList<>();
        for (Object rawValue : rawValues) {
            if (!(rawValue instanceof Map<?, ?> item)) {
                throw new IllegalArgumentException("sandbox_browser failed: each cookie must be an object");
            }
            String name = normalizedCookieName(mapString(item, "name"));
            String cookieValue = normalizedCookieValue(mapStringPreservingWhitespace(item, "value"));
            String domain = normalizedCookieDomain(
                    hasText(mapString(item, "domain")) ? mapString(item, "domain") : urlHost);
            if (!allowedHosts.contains(domain)) {
                throw new IllegalArgumentException("sandbox_browser failed: cookie domain must be included in allowedHosts");
            }
            if (!domain.equals(urlHost)) {
                throw new IllegalArgumentException("sandbox_browser failed: cookie domain must match the target URL host");
            }
            cookies.add(new BrowserCookie(
                    name,
                    cookieValue,
                    domain,
                    normalizedCookiePath(mapString(item, "path")),
                    mapBoolean(item, "httpOnly", true),
                    mapBoolean(item, "secure", false),
                    normalizedSameSite(mapString(item, "sameSite"))));
        }
        return cookies;
    }

    private List<Map<String, Object>> cookiesForRuntime(List<BrowserCookie> cookies) {
        return Objects.requireNonNullElse(cookies, List.<BrowserCookie>of()).stream()
                .map(cookie -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", cookie.name());
                    item.put("value", cookie.value());
                    item.put("domain", cookie.domain());
                    item.put("path", cookie.path());
                    item.put("httpOnly", cookie.httpOnly());
                    item.put("secure", cookie.secure());
                    item.put("sameSite", cookie.sameSite());
                    return item;
                })
                .toList();
    }

    private List<String> cookieDomains(List<BrowserCookie> cookies) {
        if (cookies == null || cookies.isEmpty()) {
            return List.of();
        }
        return cookies.stream()
                .map(BrowserCookie::domain)
                .distinct()
                .toList();
    }

    private String normalizedCookieName(String value) {
        String name = value == null ? "" : value.trim();
        if (!hasText(name)) {
            throw new IllegalArgumentException("sandbox_browser failed: cookie name is required");
        }
        if (name.length() > MAX_COOKIE_NAME_CHARS || name.matches(".*[\\s;,=].*") || containsControlCharacter(name)) {
            throw new IllegalArgumentException("sandbox_browser failed: cookie name is invalid");
        }
        return name;
    }

    private String normalizedCookieValue(String value) {
        String cookieValue = value == null ? "" : value;
        if (cookieValue.length() > MAX_COOKIE_VALUE_CHARS || containsControlCharacter(cookieValue)) {
            throw new IllegalArgumentException("sandbox_browser failed: cookie value is invalid");
        }
        return cookieValue;
    }

    private String normalizedCookieDomain(String value) {
        String domain = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        if (!hasText(domain) || domain.contains("/") || domain.contains(":") || !domain.matches("[a-z0-9.-]+")) {
            throw new IllegalArgumentException("sandbox_browser failed: cookie domain must be a host name only");
        }
        validatePublicBrowserHost(domain, "cookie domain");
        return domain;
    }

    private void validatePublicBrowserHost(String host, String label) {
        if (!hasText(host)
                || "localhost".equals(host)
                || host.endsWith(".localhost")
                || host.contains(":")
                || !host.contains(".")
                || !hasValidDnsLabels(host)
                || isIpv4Literal(host)
                || host.chars().allMatch(Character::isDigit)) {
            throw new IllegalArgumentException("sandbox_browser failed: " + label
                    + " must be a valid dotted DNS host, not localhost or an IP literal");
        }
    }

    private boolean hasValidDnsLabels(String host) {
        String[] labels = host.split("\\.", -1);
        for (String dnsLabel : labels) {
            if (dnsLabel.isEmpty()
                    || dnsLabel.length() > 63
                    || dnsLabel.startsWith("-")
                    || dnsLabel.endsWith("-")) {
                return false;
            }
        }
        return true;
    }

    private boolean isIpv4Literal(String host) {
        if (!host.matches("\\d{1,3}(\\.\\d{1,3}){3}")) {
            return false;
        }
        String[] parts = host.split("\\.");
        for (String part : parts) {
            int value;
            try {
                value = Integer.parseInt(part);
            } catch (NumberFormatException ex) {
                return false;
            }
            if (value < 0 || value > 255) {
                return false;
            }
        }
        return true;
    }

    private String normalizedCookiePath(String value) {
        String path = hasText(value) ? value.trim() : "/";
        if (!path.startsWith("/") || containsControlCharacter(path)) {
            throw new IllegalArgumentException("sandbox_browser failed: cookie path must start with /");
        }
        return path;
    }

    private String normalizedSameSite(String value) {
        if (!hasText(value)) {
            return "Lax";
        }
        return switch (value.trim().toLowerCase(Locale.ROOT)) {
            case "strict" -> "Strict";
            case "none" -> "None";
            default -> "Lax";
        };
    }

    private String mapString(Map<?, ?> map, String name) {
        Object value = map.get(name);
        return value == null ? "" : value.toString().trim();
    }

    private String mapStringPreservingWhitespace(Map<?, ?> map, String name) {
        Object value = map.get(name);
        return value == null ? "" : value.toString();
    }

    private boolean mapBoolean(Map<?, ?> map, String name, boolean defaultValue) {
        Object value = map.get(name);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value == null || value.toString().isBlank()) {
            return defaultValue;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private boolean containsControlCharacter(String value) {
        return value.chars().anyMatch(ch -> ch < 0x20 || ch == 0x7f);
    }

    private String normalizedAction(String action) {
        if (!hasText(action)) {
            return ACTION_SNAPSHOT;
        }
        return action.trim().toLowerCase(Locale.ROOT).replace('-', '_');
    }

    private record BrowserCookie(String name,
                                 String value,
                                 String domain,
                                 String path,
                                 boolean httpOnly,
                                 boolean secure,
                                 String sameSite) {}

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

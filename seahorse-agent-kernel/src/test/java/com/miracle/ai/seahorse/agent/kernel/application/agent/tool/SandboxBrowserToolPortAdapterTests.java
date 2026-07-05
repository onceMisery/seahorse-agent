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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDetailDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionSweepResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxBrowserToolPortAdapterTests {

    private static final Instant NOW = Instant.parse("2026-07-03T00:00:00Z");
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AgentToolJsonSupport jsonSupport = new AgentToolJsonSupport(objectMapper);

    @Test
    void descriptorShouldAdvertiseInlineAndAllowlistedUrlBrowserInputs() {
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(
                new RecordingSandboxRuntime(null),
                jsonSupport);

        String schema = adapter.descriptor().jsonSchema();

        assertEquals(SandboxBrowserToolPortAdapter.TOOL_ID, adapter.descriptor().toolId());
        assertTrue(adapter.descriptor().description().contains("allowlisted HTTP/HTTPS URL"));
        assertTrue(schema.contains("\"html\""));
        assertTrue(schema.contains("\"url\""));
        assertTrue(schema.contains("\"allowedHosts\""));
        assertTrue(schema.contains("\"cookies\""));
        assertTrue(schema.contains("\"sessionState\""));
        assertTrue(schema.contains("\"anyOf\""));
        assertTrue(schema.contains("\"snapshot\""));
        assertTrue(schema.contains("\"extract_text\""));
        assertTrue(schema.contains("\"viewportWidth\""));
        assertTrue(schema.contains("\"screenshot\""));
        assertTrue(schema.contains("\"har\""));
        assertTrue(schema.contains("\"video\""));
        assertTrue(schema.contains("\"captureSessionState\""));
    }

    @Test
    void shouldExecuteSnapshotThroughBrowserRuntime() throws Exception {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(SandboxExecutionResult.succeeded(
                new SandboxExecution(
                        "exec-1",
                        "session-1",
                        SandboxRuntimeType.BROWSER_AUTOMATION,
                        SandboxExecutionStatus.SUCCEEDED,
                        "exitCode=0; stdout=browser snapshot completed",
                        SandboxPolicyReasonCode.VALID_REQUEST,
                        NOW,
                        NOW),
                List.of(
                        new SandboxArtifact(
                                "artifact-json",
                                "session-1",
                                "exec-1",
                                "local://sandbox-artifacts/browser-result.json",
                                "application/json",
                                SandboxArtifactScanStatus.CLEAN,
                                ContextSensitivity.INTERNAL,
                                "metadata scan passed",
                                NOW),
                        new SandboxArtifact(
                                "artifact-png",
                                "session-1",
                                "exec-1",
                                "local://sandbox-artifacts/screenshot.png",
                                "image/png",
                                SandboxArtifactScanStatus.CLEAN,
                                ContextSensitivity.INTERNAL,
                                "metadata scan passed",
                                NOW),
                        new SandboxArtifact(
                                "artifact-har",
                                "session-1",
                                "exec-1",
                                "local://sandbox-artifacts/browser-network.har",
                                "application/har+json",
                                SandboxArtifactScanStatus.CLEAN,
                                ContextSensitivity.INTERNAL,
                                "metadata scan passed",
                                NOW),
                        new SandboxArtifact(
                                "artifact-video",
                                "session-1",
                                "exec-1",
                                "local://sandbox-artifacts/browser-video.webm",
                                "video/webm",
                                SandboxArtifactScanStatus.CLEAN,
                                ContextSensitivity.INTERNAL,
                                "metadata scan passed",
                                NOW))));
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "html", "<!doctype html><title>Browser Smoke</title><main>browser marker</main>",
                "viewportWidth", 1024,
                "viewportHeight", 640,
                "screenshot", true,
                "har", true,
                "video", true,
                "cookies", List.of())));

        assertTrue(result.success());
        assertEquals("tenant-1", runtime.createCommand.tenantId());
        assertEquals("run-1", runtime.createCommand.runId());
        assertEquals(SandboxRuntimeType.BROWSER_AUTOMATION, runtime.createCommand.runtimeType());
        assertFalse(runtime.createCommand.networkRequested());
        assertEquals(List.of(), runtime.createCommand.requestedHosts());
        assertEquals("session-1", runtime.executeCommand.sessionId());
        assertFalse(runtime.executeCommand.networkRequested());
        assertEquals(List.of(), runtime.executeCommand.requestedHosts());
        assertEquals("session-1", runtime.closedSessionId);

        JsonNode browserInput = objectMapper.readTree(runtime.executeCommand.input());
        assertEquals("snapshot", browserInput.path("action").asText());
        assertEquals(1024, browserInput.path("viewportWidth").asInt());
        assertEquals(640, browserInput.path("viewportHeight").asInt());
        assertTrue(browserInput.path("screenshot").asBoolean());
        assertTrue(browserInput.path("har").asBoolean());
        assertTrue(browserInput.path("video").asBoolean());
        assertTrue(browserInput.path("html").asText().contains("browser marker"));
        assertFalse(browserInput.has("cookies"));

        JsonNode root = objectMapper.readTree(result.content());
        assertEquals(SandboxBrowserToolPortAdapter.TOOL_ID, root.path("toolId").asText());
        assertEquals("BROWSER_AUTOMATION", root.path("runtimeType").asText());
        assertEquals("SUCCEEDED", root.path("executionStatus").asText());
        assertEquals("snapshot", root.path("browser").path("action").asText());
        assertFalse(root.path("browser").path("networkAllowed").asBoolean());
        assertTrue(root.path("browser").path("har").asBoolean());
        assertTrue(root.path("browser").path("video").asBoolean());
        assertEquals("application/json", root.path("artifacts").get(0).path("mediaType").asText());
        assertEquals("image/png", root.path("artifacts").get(1).path("mediaType").asText());
        assertEquals("application/har+json", root.path("artifacts").get(2).path("mediaType").asText());
        assertEquals("video/webm", root.path("artifacts").get(3).path("mediaType").asText());
        assertEquals("metadata scan passed", root.path("artifacts").get(0).path("scanSummary").asText());
        assertTrue(root.path("artifacts").get(0).path("promptVisible").asBoolean());
        assertFalse(root.path("artifacts").get(3).path("promptVisible").asBoolean());
    }

    @Test
    void shouldExecuteUrlThroughBrowserRuntimeWithAllowlistedHost() throws Exception {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(SandboxExecutionResult.succeeded(
                new SandboxExecution(
                        "exec-1",
                        "session-1",
                        SandboxRuntimeType.BROWSER_AUTOMATION,
                        SandboxExecutionStatus.SUCCEEDED,
                        "exitCode=0; stdout=browser snapshot completed",
                        SandboxPolicyReasonCode.VALID_REQUEST,
                        NOW,
                        NOW),
                List.of(new SandboxArtifact(
                        "artifact-json",
                        "session-1",
                        "exec-1",
                        "local://sandbox-artifacts/browser-result.json",
                        "application/json",
                        SandboxArtifactScanStatus.CLEAN,
                        ContextSensitivity.INTERNAL,
                        "metadata scan passed",
                        NOW))));
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        String url = "http://example.test/page";
        String cookieValue = "session-secret-value";
        String storageValue = "local-storage-secret-value";
        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", url,
                "allowedHosts", List.of("Example.Test"),
                "cookies", List.of(Map.of(
                        "name", "seahorse_session",
                        "value", cookieValue,
                        "domain", "example.test",
                        "path", "/",
                        "httpOnly", true,
                        "sameSite", "Lax")),
                "sessionState", Map.of(
                        "cookies", List.of(Map.of(
                                "name", "restored_session",
                                "value", "restored-secret-value",
                                "domain", "example.test",
                                "path", "/",
                                "httpOnly", true,
                                "secure", false,
                                "sameSite", "Lax")),
                        "origins", List.of(Map.of(
                                "origin", "http://example.test",
                                "localStorage", List.of(Map.of(
                                        "name", "seahorse_session_marker",
                                        "value", storageValue))))),
                "captureSessionState", true,
                "har", true)));

        assertTrue(result.success());
        assertTrue(runtime.createCommand.networkRequested());
        assertEquals(List.of("example.test"), runtime.createCommand.requestedHosts());
        assertTrue(runtime.executeCommand.networkRequested());
        assertEquals(List.of("example.test"), runtime.executeCommand.requestedHosts());

        JsonNode browserInput = objectMapper.readTree(runtime.executeCommand.input());
        assertEquals(url, browserInput.path("url").asText());
        assertEquals("", browserInput.path("html").asText());
        assertEquals("example.test", browserInput.path("allowedHosts").get(0).asText());
        assertEquals("seahorse_session", browserInput.path("cookies").get(0).path("name").asText());
        assertEquals(cookieValue, browserInput.path("cookies").get(0).path("value").asText());
        assertEquals("example.test", browserInput.path("cookies").get(0).path("domain").asText());
        assertTrue(browserInput.path("captureSessionState").asBoolean());
        assertEquals("restored_session", browserInput.path("sessionState").path("cookies").get(0).path("name").asText());
        assertEquals(storageValue, browserInput.path("sessionState").path("origins").get(0)
                .path("localStorage").get(0).path("value").asText());

        JsonNode root = objectMapper.readTree(result.content());
        assertTrue(root.path("browser").path("networkAllowed").asBoolean());
        assertEquals(url, root.path("browser").path("url").asText());
        assertEquals("example.test", root.path("browser").path("allowedHosts").get(0).asText());
        assertEquals(1, root.path("browser").path("cookieCount").asInt());
        assertEquals("example.test", root.path("browser").path("cookieDomains").get(0).asText());
        assertTrue(root.path("browser").path("sessionState").path("captureRequested").asBoolean());
        assertTrue(root.path("browser").path("sessionState").path("replayRequested").asBoolean());
        assertFalse(result.content().contains(cookieValue));
        assertFalse(result.content().contains(storageValue));
    }

    @Test
    void shouldNormalizeExtractTextActionAndDisableDefaultScreenshot() throws Exception {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(SandboxExecutionResult.succeeded(
                new SandboxExecution(
                        "exec-1",
                        "session-1",
                        SandboxRuntimeType.BROWSER_AUTOMATION,
                        SandboxExecutionStatus.SUCCEEDED,
                        "exitCode=0; stdout=browser extract_text completed",
                        SandboxPolicyReasonCode.VALID_REQUEST,
                        NOW,
                        NOW),
                List.of(new SandboxArtifact(
                        "artifact-json",
                        "session-1",
                        "exec-1",
                        "local://sandbox-artifacts/browser-result.json",
                        "application/json",
                        SandboxArtifactScanStatus.CLEAN,
                        ContextSensitivity.INTERNAL,
                        "metadata scan passed",
                        NOW))));
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "extract-text",
                "html", "<main>plain text</main>")));

        assertTrue(result.success());
        JsonNode browserInput = objectMapper.readTree(runtime.executeCommand.input());
        assertEquals("extract_text", browserInput.path("action").asText());
        assertFalse(browserInput.path("screenshot").asBoolean());
        assertFalse(browserInput.path("har").asBoolean());
        assertFalse(browserInput.path("video").asBoolean());
    }

    @Test
    void shouldRejectUnsupportedActionBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "goto",
                "html", "<main>nope</main>")));

        assertFalse(result.success());
        assertTrue(result.error().contains("supported actions"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectBlankHtmlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "html", " ")));

        assertFalse(result.success());
        assertTrue(result.error().contains("html is required"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectSessionStateCaptureForInlineHtmlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "html", "<main>inline</main>",
                "captureSessionState", true)));

        assertFalse(result.success());
        assertTrue(result.error().contains("captureSessionState is only supported for url mode"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectSessionStateReplayForInlineHtmlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "html", "<main>inline</main>",
                "sessionState", Map.of("cookies", List.of()))));

        assertFalse(result.success());
        assertTrue(result.error().contains("sessionState is only supported for url mode"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectUrlWhenHostIsNotAllowlistedBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/page",
                "allowedHosts", List.of("other.test"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("url host must be included in allowedHosts"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectLocalhostBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://localhost:8080/admin",
                "allowedHosts", List.of("localhost"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("not localhost or an IP literal"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectIpLiteralBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://127.0.0.1:8080/admin",
                "allowedHosts", List.of("127.0.0.1"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("not localhost or an IP literal"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectIpv6LiteralBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://[::1]:8080/admin",
                "allowedHosts", List.of("example.test"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("not localhost or an IP literal"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectSingleLabelBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://metadata/admin",
                "allowedHosts", List.of("metadata"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("dotted DNS host"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectMalformedDnsBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.test/admin",
                "allowedHosts", List.of("aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.test"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("valid dotted DNS host"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectUserinfoBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://alice:secret@example.test/admin",
                "allowedHosts", List.of("example.test"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("must not include userinfo credentials"));
        assertFalse(result.error().contains("alice:secret"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectFragmentBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/admin#access_token=secret",
                "allowedHosts", List.of("example.test"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("must not include fragment identifiers"));
        assertFalse(result.error().contains("access_token=secret"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectCredentialQueryBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/admin?access_token=secret",
                "allowedHosts", List.of("example.test"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("url query must not include credential parameters"));
        assertFalse(result.error().contains("access_token=secret"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectEncodedCredentialQueryBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/admin?access%5Ftoken=secret",
                "allowedHosts", List.of("example.test"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("url query must not include credential parameters"));
        assertFalse(result.error().contains("access%5Ftoken=secret"));
        assertFalse(result.error().contains("secret"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectSemicolonSeparatedCredentialQueryBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/admin?q=roadmap;access_token=secret",
                "allowedHosts", List.of("example.test"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("url query must not include credential parameters"));
        assertFalse(result.error().contains("access_token=secret"));
        assertFalse(result.error().contains("secret"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectBracketCredentialQueryBrowserUrlBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/admin?access_token[]=secret",
                "allowedHosts", List.of("example.test"))));

        assertFalse(result.success());
        assertTrue(result.error().contains("url query must not include credential parameters"));
        assertFalse(result.error().contains("access_token[]=secret"));
        assertFalse(result.error().contains("secret"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldAllowNonCredentialQueryBrowserUrlBeforeCreatingSession() throws Exception {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(SandboxExecutionResult.succeeded(
                new SandboxExecution(
                        "exec-1",
                        "session-1",
                        SandboxRuntimeType.BROWSER_AUTOMATION,
                        SandboxExecutionStatus.SUCCEEDED,
                        "exitCode=0; stdout=browser snapshot completed",
                        SandboxPolicyReasonCode.VALID_REQUEST,
                        NOW,
                        NOW),
                List.of()));
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/search?q=roadmap",
                "allowedHosts", List.of("example.test"))));

        assertTrue(result.success());
        assertEquals(1, runtime.createCalls);
        JsonNode browserInput = objectMapper.readTree(runtime.executeCommand.input());
        assertEquals("http://example.test/search?q=roadmap", browserInput.path("url").asText());
    }

    @Test
    void shouldRejectCookieWhenDomainIsNotAllowlistedBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/page",
                "allowedHosts", List.of("example.test"),
                "cookies", List.of(Map.of(
                        "name", "seahorse_session",
                        "value", "secret",
                        "domain", "other.test")))));

        assertFalse(result.success());
        assertTrue(result.error().contains("cookie domain must be included in allowedHosts"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectCookieWhenDomainDoesNotMatchTargetUrlHostBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/page",
                "allowedHosts", List.of("example.test", "other.test"),
                "cookies", List.of(Map.of(
                        "name", "seahorse_session",
                        "value", "secret",
                        "domain", "other.test")))));

        assertFalse(result.success());
        assertTrue(result.error().contains("cookie domain must match the target URL host"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectSessionStateOriginWhenHostIsNotAllowlistedBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/page",
                "allowedHosts", List.of("example.test"),
                "sessionState", Map.of(
                        "origins", List.of(Map.of(
                                "origin", "http://other.test",
                                "localStorage", List.of(Map.of(
                                        "name", "seahorse_session_marker",
                                        "value", "secret"))))))));

        assertFalse(result.success());
        assertTrue(result.error().contains("sessionState origin host must be included in allowedHosts"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectSessionStateOriginWhenHostDoesNotMatchTargetUrlHostBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/page",
                "allowedHosts", List.of("example.test", "other.test"),
                "sessionState", Map.of(
                        "origins", List.of(Map.of(
                                "origin", "http://other.test",
                                "localStorage", List.of(Map.of(
                                        "name", "seahorse_session_marker",
                                        "value", "secret"))))))));

        assertFalse(result.success());
        assertTrue(result.error().contains("sessionState origin host must match the target URL host"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectSessionStateOriginWhenOriginDoesNotMatchTargetUrlOriginBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test:8080/page",
                "allowedHosts", List.of("example.test"),
                "sessionState", Map.of(
                        "origins", List.of(Map.of(
                                "origin", "http://example.test:9090",
                                "localStorage", List.of(Map.of(
                                        "name", "seahorse_session_marker",
                                        "value", "secret"))))))));

        assertFalse(result.success());
        assertTrue(result.error().contains("sessionState origin must match the target URL origin"));
        assertEquals(0, runtime.createCalls);
    }

    @Test
    void shouldRejectSessionStateOriginWithCredentialPartsBeforeCreatingSession() {
        RecordingSandboxRuntime runtime = new RecordingSandboxRuntime(null);
        SandboxBrowserToolPortAdapter adapter = new SandboxBrowserToolPortAdapter(runtime, jsonSupport);

        ToolInvocationResult result = adapter.invoke(request(Map.of(
                "action", "snapshot",
                "url", "http://example.test/page",
                "allowedHosts", List.of("example.test"),
                "sessionState", Map.of(
                        "origins", List.of(Map.of(
                                "origin", "http://alice:secret@example.test/path?token=secret#frag",
                                "localStorage", List.of(Map.of(
                                        "name", "marker",
                                        "value", "value"))))))));

        assertFalse(result.success());
        assertTrue(result.error().contains("sessionState origin must be an origin only"));
        assertFalse(result.error().contains("alice:secret"));
        assertFalse(result.error().contains("token=secret"));
        assertEquals(0, runtime.createCalls);
    }

    private ToolInvocationRequest request(Map<String, Object> arguments) {
        return new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "rollout-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                SandboxBrowserToolPortAdapter.TOOL_ID,
                arguments,
                Map.of(),
                "run-1:call-1",
                List.of(SandboxBrowserToolPortAdapter.TOOL_ID));
    }

    private static final class RecordingSandboxRuntime implements SandboxRuntimeInboundPort {

        private final SandboxExecutionResult executionResult;
        private SandboxSession session;
        private SandboxSessionCreateCommand createCommand;
        private SandboxExecutionCommand executeCommand;
        private String closedSessionId;
        private int createCalls;

        private RecordingSandboxRuntime(SandboxExecutionResult executionResult) {
            this.executionResult = executionResult;
        }

        @Override
        public SandboxSession createSession(SandboxSessionCreateCommand command) {
            createCalls++;
            createCommand = command;
            session = SandboxSession.created(
                    "session-1",
                    command.tenantId(),
                    command.runId(),
                    command.runtimeType(),
                    NOW);
            return session;
        }

        @Override
        public SandboxExecutionResult execute(SandboxExecutionCommand command) {
            executeCommand = command;
            return executionResult;
        }

        @Override
        public SandboxSession close(String sessionId) {
            closedSessionId = sessionId;
            return session.closed(NOW);
        }

        @Override
        public List<SandboxSession> listSessions(String tenantId, int limit) {
            return List.of();
        }

        @Override
        public SandboxSessionSweepResult sweepExpiredSessions(String tenantId, int limit) {
            return new SandboxSessionSweepResult(tenantId, NOW, 0, 0, 0, List.of());
        }

        @Override
        public SandboxRuntimeCleanupResult sweepOrphanedRuntimeResources() {
            return SandboxRuntimeCleanupResult.empty(NOW, 0);
        }

        @Override
        public SandboxRuntimeHealth inspectRuntimeHealth() {
            return SandboxRuntimeHealth.unsupported(NOW, 0);
        }

        @Override
        public SandboxRuntimeContainerReapResult reapOrphanedRuntimeContainers(boolean dryRun) {
            return SandboxRuntimeContainerReapResult.empty(NOW, dryRun, 0);
        }

        @Override
        public List<SandboxExecution> listExecutions(String sessionId) {
            return List.of();
        }

        @Override
        public List<SandboxArtifact> listArtifacts(String sessionId) {
            return List.of();
        }

        @Override
        public SandboxArtifactDetailDecision describeArtifact(String artifactId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public SandboxArtifactDownloadDecision downloadArtifact(String artifactId) {
            throw new UnsupportedOperationException();
        }
    }
}

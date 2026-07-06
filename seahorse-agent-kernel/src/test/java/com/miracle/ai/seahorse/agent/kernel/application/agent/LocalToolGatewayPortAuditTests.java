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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.PolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditCompletion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationAuditRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolArtifactPublicationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationRequestAwarePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolOutputRedactionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LocalToolGatewayPortAuditTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldRecordRequestedDecisionAndCompletedEventsForAllowedTool() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("weather"));

        assertTrue(result.success());
        assertEquals(1, tool.calls.get());
        assertEquals(1, audit.requested.size());
        assertEquals(1, audit.decisions.size());
        assertEquals(1, audit.completed.size());
        assertEquals(ToolInvocationStatus.REQUESTED, audit.requested.get(0).status());
        assertEquals("run-1", audit.requested.get(0).runId());
        String argumentsSummary = audit.requested.get(0).argumentsSummary();
        assertTrue(argumentsSummary.contains("\"toolId\":\"weather\""));
        assertTrue(argumentsSummary.contains("\"argumentKeys\":[\"input\"]"));
        assertTrue(argumentsSummary.contains("\"argumentCount\":1"));
        assertTrue(argumentsSummary.contains("\"argumentValueCount\":1"));
        assertTrue(argumentsSummary.contains("\"argumentValueTotalLength\":5"));
        assertTrue(argumentsSummary.contains("\"argumentValueMaxLength\":5"));
        assertFalse(argumentsSummary.contains("value"));
        assertEquals(audit.requested.get(0).invocationId(), audit.decisions.get(0).invocationId());
        assertEquals("allow-1", audit.decisions.get(0).policyDecisionId());
        assertEquals(ToolInvocationStatus.ALLOWED, audit.decisions.get(0).status());
        assertEquals(audit.requested.get(0).invocationId(), audit.completed.get(0).invocationId());
        assertEquals(ToolInvocationStatus.SUCCEEDED, audit.completed.get(0).status());
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentPresent\":true"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentLength\":11"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentJsonType\":\"object\""));
        assertEquals(FIXED_CLOCK.instant(), audit.completed.get(0).finishedAt());
    }

    @Test
    void shouldPassFullInvocationRequestToRequestAwareTool() {
        RequestAwareCountingToolPort tool = new RequestAwareCountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                ToolInvocationAuditPort.noop(),
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("sandbox_python"));

        assertTrue(result.success());
        assertEquals(1, tool.requestAwareCalls.get());
        assertEquals(0, tool.legacyCalls.get());
        assertEquals("run-1", tool.lastRequest.runId());
        assertEquals("tenant-1", tool.lastRequest.tenantId());
        assertEquals("user-1", tool.lastRequest.userId());
        assertEquals("sandbox_python", tool.lastRequest.toolId());
        assertEquals(Map.of("input", "value"), tool.lastRequest.arguments());
    }

    @Test
    void shouldRecordDeniedDecisionAndCompletionWithoutExecutingTool() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("should-not-run"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.deny("deny-1",
                        ToolPolicyReasonCodes.TOOL_NOT_BOUND,
                        "Tool is not bound")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("memory-write"));

        assertFalse(result.success());
        assertEquals(ToolPolicyReasonCodes.TOOL_NOT_BOUND, result.error());
        assertEquals(0, tool.calls.get());
        assertEquals(ToolInvocationStatus.DENIED, audit.decisions.get(0).status());
        assertEquals(ToolInvocationStatus.DENIED, audit.completed.get(0).status());
        assertEquals(ToolPolicyReasonCodes.TOOL_NOT_BOUND, audit.completed.get(0).errorMessage());
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentPresent\":false"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"errorPresent\":true"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"errorLength\":14"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"approvalIdPresent\":false"));
    }

    @Test
    void shouldCreatePendingApprovalRequestWhenPolicyRequiresApproval() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("should-not-run"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        RecordingToolApprovalRequestRepositoryPort approvals = new RecordingToolApprovalRequestRepositoryPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.approvalRequired("approval-1",
                        ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED,
                        "Tool requires approval")),
                audit,
                approvals,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(requestWithRollout("memory-forget", "rollout-1"));

        assertFalse(result.success());
        assertEquals(ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED, result.error());
        assertEquals(0, tool.calls.get());
        assertEquals(ToolInvocationStatus.APPROVAL_REQUIRED, audit.decisions.get(0).status());
        assertEquals(ToolInvocationStatus.APPROVAL_REQUIRED, audit.completed.get(0).status());
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentPresent\":false"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"errorPresent\":true"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"errorLength\":22"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"approvalIdPresent\":true"));
        assertEquals(1, approvals.saved.size());
        ApprovalRequest approval = approvals.saved.get(0);
        assertEquals(ApprovalRequestStatus.PENDING, approval.status());
        assertEquals(ApprovalType.TOOL_EXECUTION, approval.approvalType());
        assertEquals("rollout-1", audit.requested.get(0).rolloutId());
        assertEquals("run-1", approval.runId());
        assertEquals("step-1", approval.stepId());
        assertEquals(audit.requested.get(0).invocationId(), approval.toolInvocationId());
        assertEquals("tenant-1", approval.tenantId());
        assertEquals("user-1", approval.userId());
        assertEquals("agent-1", approval.agentId());
        assertEquals("rollout-1", approval.rolloutId());
        assertEquals("memory-forget", approval.toolId());
        assertEquals(FIXED_CLOCK.instant(), approval.requestedAt());
        assertTrue(approval.argumentsPreviewJson().contains("input"));
    }

    @Test
    void shouldStoreOnlyPreviewLimitedArgumentMetadataForApprovalRequests() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("should-not-run"));
        RecordingToolApprovalRequestRepositoryPort approvals = new RecordingToolApprovalRequestRepositoryPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.approvalRequired("approval-1",
                        ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED,
                        "Tool requires approval")),
                ToolInvocationAuditPort.noop(),
                approvals,
                FIXED_CLOCK);

        gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "memory-forget",
                Map.of(
                        "prompt", "x".repeat(2_000),
                        "apiKey", "plain-secret"),
                Map.of("knowledgeBaseId", "kb-secret-ref"),
                "run-1:call-1",
                List.of("memory-forget")));

        assertEquals(1, approvals.saved.size());
        String preview = approvals.saved.get(0).argumentsPreviewJson();
        assertTrue(preview.contains("argumentKeys"));
        assertTrue(preview.contains("argumentCount"));
        assertTrue(preview.contains("\"argumentValueCount\":2"));
        assertTrue(preview.contains("\"argumentValueTotalLength\":2012"));
        assertTrue(preview.contains("\"argumentValueMaxLength\":2000"));
        assertTrue(preview.contains("\"resourceRefKeys\":[\"knowledgeBaseId\"]"));
        assertTrue(preview.contains("\"resourceRefCount\":1"));
        assertTrue(preview.contains("resourceRefHash"));
        assertFalse(preview.contains("plain-secret"));
        assertFalse(preview.contains("kb-secret-ref"));
        assertFalse(preview.contains("xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"));
        assertTrue(preview.length() < 500);
    }

    @Test
    void shouldFilterUnsafeArgumentKeyNamesFromApprovalPreview() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("should-not-run"));
        RecordingToolApprovalRequestRepositoryPort approvals = new RecordingToolApprovalRequestRepositoryPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.approvalRequired("approval-1",
                        ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED,
                        "Tool requires approval")),
                ToolInvocationAuditPort.noop(),
                approvals,
                FIXED_CLOCK);

        gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "memory-forget",
                Map.of(
                        "input", "value",
                        "sessionToken=secret-marker", "x",
                        "line\nbreak", "y",
                        "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", "z"),
                Map.of(),
                "run-1:call-1",
                List.of("memory-forget")));

        assertEquals(1, approvals.saved.size());
        String preview = approvals.saved.get(0).argumentsPreviewJson();
        assertTrue(preview.contains("\"argumentKeys\":[\"input\"]"));
        assertTrue(preview.contains("\"argumentCount\":4"));
        assertFalse(preview.contains("secret-marker"));
        assertFalse(preview.contains("line\\nbreak"));
    }

    @Test
    void shouldExecuteToolWhenApprovalWasAlreadyApprovedForRunStep() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        RecordingToolApprovalRequestRepositoryPort approvals = new RecordingToolApprovalRequestRepositoryPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.approvalRequired("approval-1",
                        ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED,
                        "Tool requires approval")),
                audit,
                approvals,
                new FixedApprovalQueryPort(approval(ApprovalRequestStatus.APPROVED)),
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("memory-forget"));

        assertTrue(result.success());
        assertEquals("{\"ok\":true}", result.content());
        assertEquals(1, tool.calls.get());
        assertEquals(0, approvals.saved.size());
        assertEquals(ToolInvocationStatus.ALLOWED, audit.decisions.get(0).status());
        assertEquals(ToolInvocationStatus.SUCCEEDED, audit.completed.get(0).status());
    }

    @Test
    void shouldRedactSensitiveToolOutputBeforeReturningAndAuditing() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("token=sk-live-secret"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                ToolOutputRedactionPort.basicSecretPatterns(),
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("weather"));

        assertTrue(result.success());
        assertEquals("token=[REDACTED]", result.content());
        assertEquals(ToolInvocationStatus.SUCCEEDED, audit.completed.get(0).status());
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentPresent\":true"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentLength\":16"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentJsonType\":\"text\""));
        assertFalse(audit.completed.get(0).resultSummary().contains("token"));
    }

    @Test
    void shouldRedactBase64ImagePayloadBeforeReturningAndAuditing() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok(
                "{\"status\":\"GENERATED\",\"b64Json\":\"large-base64-payload\",\"mimeType\":\"image/png\"}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                ToolOutputRedactionPort.basicSecretPatterns(),
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("image_generation"));

        assertTrue(result.success());
        assertEquals("{\"status\":\"GENERATED\",\"b64Json\":\"[REDACTED]\",\"mimeType\":\"image/png\"}",
                result.content());
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentPresent\":true"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentLength\":68"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentJsonType\":\"object\""));
        assertFalse(audit.completed.get(0).resultSummary().contains("GENERATED"));
        assertFalse(audit.completed.get(0).resultSummary().contains("b64Json"));
    }

    @Test
    void shouldRedactSecretJsonFieldsBeforeReturningSuccessfulToolOutput() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok(
                "{\"status\":\"ok\",\"apiKey\":\"plain-api-key\","
                        + "\"Authorization\":\"Bearer plain-authorization-token\","
                        + "\"Cookie\":\"sid=plain-cookie-header\","
                        + "\"cookieCount\":1,"
                        + "\"sessionToken\":\"plain-session-token\","
                        + "\"tokenCount\":2,"
                        + "\"setCookie\":\"sid=plain-cookie-value\","
                        + "\"nested\":{\"clientSecret\":\"plain-client-secret\",\"password\":\"plain-password\","
                        + "\"private_key\":\"plain-private-key\"},"
                        + "\"items\":[{\"access_token\":\"plain-access-token\","
                        + "\"secretKey\":\"plain-secret-key\",\"label\":\"safe\"}]}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                ToolOutputRedactionPort.basicSecretPatterns(),
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("weather"));

        assertTrue(result.success());
        assertEquals("{\"status\":\"ok\",\"apiKey\":\"[REDACTED]\","
                        + "\"Authorization\":\"[REDACTED]\","
                        + "\"Cookie\":\"[REDACTED]\","
                        + "\"cookieCount\":1,"
                        + "\"sessionToken\":\"[REDACTED]\","
                        + "\"tokenCount\":2,"
                        + "\"setCookie\":\"[REDACTED]\","
                        + "\"nested\":{\"clientSecret\":\"[REDACTED]\",\"password\":\"[REDACTED]\","
                        + "\"private_key\":\"[REDACTED]\"},"
                        + "\"items\":[{\"access_token\":\"[REDACTED]\","
                        + "\"secretKey\":\"[REDACTED]\",\"label\":\"safe\"}]}",
                result.content());
        assertFalse(result.content().contains("plain-api-key"));
        assertFalse(result.content().contains("plain-authorization-token"));
        assertFalse(result.content().contains("plain-cookie-header"));
        assertFalse(result.content().contains("plain-session-token"));
        assertFalse(result.content().contains("plain-cookie-value"));
        assertFalse(result.content().contains("plain-client-secret"));
        assertFalse(result.content().contains("plain-password"));
        assertFalse(result.content().contains("plain-private-key"));
        assertFalse(result.content().contains("plain-access-token"));
        assertFalse(result.content().contains("plain-secret-key"));
        assertFalse(audit.completed.get(0).resultSummary().contains("plain-api-key"));
    }

    @Test
    void shouldRecordFailedCompletionWhenToolThrowsException() {
        ThrowingToolPort tool = new ThrowingToolPort();
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("weather"));

        assertFalse(result.success());
        assertEquals("tool boom", result.error());
        assertEquals(1, audit.requested.size());
        assertEquals(1, audit.decisions.size());
        assertEquals(1, audit.completed.size());
        assertEquals(ToolInvocationStatus.FAILED, audit.completed.get(0).status());
        assertEquals("tool boom", audit.completed.get(0).errorMessage());
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentPresent\":false"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"errorPresent\":true"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"errorLength\":9"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"approvalIdPresent\":false"));
    }

    @Test
    void shouldRedactCredentialShapedFailedToolErrorBeforeReturningAndAuditing() {
        CountingToolPort tool = new CountingToolPort(
                ToolInvocationResult.failed("upstream failed Cookie: plain-secret-token-123"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                ToolOutputRedactionPort.basicSecretPatterns(),
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("weather"));

        assertFalse(result.success());
        assertEquals("upstream failed [REDACTED]", result.error());
        assertEquals(ToolInvocationStatus.FAILED, audit.completed.get(0).status());
        assertEquals("upstream failed [REDACTED]", audit.completed.get(0).errorMessage());
        assertTrue(audit.completed.get(0).resultSummary().contains("\"contentPresent\":false"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"errorPresent\":true"));
        assertTrue(audit.completed.get(0).resultSummary().contains("\"errorLength\":26"));
        assertFalse(audit.completed.get(0).resultSummary().contains("plain-secret-token-123"));
        assertFalse(result.error().contains("plain-secret-token-123"));
        assertFalse(audit.completed.get(0).errorMessage().contains("plain-secret-token-123"));
    }

    @Test
    void shouldSummarizeSandboxBrowserGovernanceMetadataWithoutSessionValues() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "sandbox_browser",
                Map.of(
                        "url", "https://example.test/page?q=customer-search-marker",
                        "allowedHosts", List.of("example.test"),
                        "cookies", List.of(Map.of(
                                "name", "seahorse_session",
                                "value", "cookie-secret-value",
                                "domain", "example.test")),
                        "sessionState", Map.of(
                                "cookies", List.of(Map.of(
                                        "name", "restored_session",
                                        "value", "restored-secret-value",
                                        "domain", "example.test")),
                                "origins", List.of(Map.of(
                                        "origin", "https://example.test",
                                        "localStorage", List.of(Map.of(
                                                "name", "seahorse_session_marker",
                                                "value", "storage-secret-value"))))),
                        "captureSessionState", true,
                        "screenshot", false,
                        "har", true,
                        "video", true,
                        "viewportWidth", 1366,
                        "viewportHeight", "768"),
                Map.of(),
                "run-1:call-1",
                List.of("sandbox_browser")));

        assertTrue(result.success());
        String summary = audit.requested.get(0).argumentsSummary();
        assertTrue(summary.contains("\"toolId\":\"sandbox_browser\""));
        assertTrue(summary.contains("\"mode\":\"url\""));
        assertTrue(summary.contains("\"networkRequested\":true"));
        assertTrue(summary.contains("\"urlPresent\":true"));
        assertTrue(summary.contains("\"urlLength\":50"));
        assertTrue(summary.contains("\"urlQueryPresent\":true"));
        assertTrue(summary.contains("\"urlQueryLength\":24"));
        assertTrue(summary.contains("\"allowedHostCount\":1"));
        assertTrue(summary.contains("\"allowedHostsPresent\":true"));
        assertTrue(summary.contains("\"cookieCount\":1"));
        assertTrue(summary.contains("\"sessionStateReplayRequested\":true"));
        assertTrue(summary.contains("\"sessionStateCookieCount\":1"));
        assertTrue(summary.contains("\"sessionStateOriginCount\":1"));
        assertTrue(summary.contains("\"sessionStateLocalStorageItemCount\":1"));
        assertTrue(summary.contains("\"captureSessionState\":true"));
        assertTrue(summary.contains("\"screenshot\":false"));
        assertTrue(summary.contains("\"har\":true"));
        assertTrue(summary.contains("\"video\":true"));
        assertTrue(summary.contains("\"viewportWidthPresent\":true"));
        assertTrue(summary.contains("\"viewportWidth\":1366"));
        assertTrue(summary.contains("\"viewportHeightPresent\":true"));
        assertTrue(summary.contains("\"viewportHeight\":768"));
        assertTrue(summary.contains("\"argumentKeys\":[\"url\",\"allowedHosts\",\"cookies\",\"sessionState\",\"captureSessionState\",\"screenshot\",\"har\",\"video\",\"viewportWidth\",\"viewportHeight\"]"));
        assertTrue(summary.contains("\"argumentCount\":10"));
        assertFalse(summary.contains("cookie-secret-value"));
        assertFalse(summary.contains("restored-secret-value"));
        assertFalse(summary.contains("storage-secret-value"));
        assertFalse(summary.contains("customer-search-marker"));
    }

    @Test
    void shouldSummarizeSandboxBrowserInlineHtmlShapeWithoutHtmlValues() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);
        String html = "<main data-secret=\"inline-html-marker\">hello</main>";

        ToolInvocationResult result = gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "sandbox_browser",
                Map.of(
                        "html", html,
                        "action", "snapshot",
                        "har", true),
                Map.of(),
                "run-1:call-1",
                List.of("sandbox_browser")));

        assertTrue(result.success());
        String summary = audit.requested.get(0).argumentsSummary();
        assertTrue(summary.contains("\"toolId\":\"sandbox_browser\""));
        assertTrue(summary.contains("\"mode\":\"inline\""));
        assertTrue(summary.contains("\"networkRequested\":false"));
        assertTrue(summary.contains("\"urlPresent\":false"));
        assertTrue(summary.contains("\"urlLength\":0"));
        assertTrue(summary.contains("\"urlQueryPresent\":false"));
        assertTrue(summary.contains("\"urlQueryLength\":0"));
        assertTrue(summary.contains("\"htmlPresent\":true"));
        assertTrue(summary.contains("\"htmlLength\":" + html.length()));
        assertTrue(summary.contains("\"sessionStateLocalStorageItemCount\":0"));
        assertTrue(summary.contains("\"screenshot\":true"));
        assertTrue(summary.contains("\"viewportWidthPresent\":false"));
        assertTrue(summary.contains("\"viewportWidth\":0"));
        assertTrue(summary.contains("\"viewportHeightPresent\":false"));
        assertTrue(summary.contains("\"viewportHeight\":0"));
        assertTrue(summary.contains("\"argumentKeys\":[\"html\",\"action\",\"har\"]"));
        assertTrue(summary.contains("\"argumentCount\":3"));
        assertTrue(summary.contains("\"argumentValueCount\":3"));
        assertTrue(summary.contains("\"argumentValueTotalLength\":" + (html.length() + 12)));
        assertTrue(summary.contains("\"argumentValueMaxLength\":" + html.length()));
        assertFalse(summary.contains("inline-html-marker"));
        assertFalse(summary.contains(html));
    }

    @Test
    void shouldSummarizeSandboxBrowserAuditWithoutPrevalidatedHostOrActionValues() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.failed("sandbox_browser failed"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "sandbox_browser",
                Map.of(
                        "url", "https://example.test/page",
                        "allowedHosts", List.of("example.test?access_token=host-secret"),
                        "action", "snapshot-secret-action",
                        "token-secret-argument-key", "present"),
                Map.of(),
                "run-1:call-1",
                List.of("sandbox_browser")));

        assertFalse(result.success());
        String summary = audit.requested.get(0).argumentsSummary();
        assertTrue(summary.contains("\"toolId\":\"sandbox_browser\""));
        assertTrue(summary.contains("\"mode\":\"url\""));
        assertTrue(summary.contains("\"action\":\"unsupported\""));
        assertTrue(summary.contains("\"urlPresent\":true"));
        assertTrue(summary.contains("\"urlQueryPresent\":false"));
        assertTrue(summary.contains("\"urlQueryLength\":0"));
        assertTrue(summary.contains("\"allowedHostCount\":1"));
        assertTrue(summary.contains("\"allowedHostsPresent\":true"));
        assertTrue(summary.contains("\"argumentKeys\":[\"url\",\"allowedHosts\",\"action\"]"));
        assertTrue(summary.contains("\"argumentCount\":4"));
        assertFalse(summary.contains("access_token=host-secret"));
        assertFalse(summary.contains("snapshot-secret-action"));
        assertFalse(summary.contains("token-secret-argument-key"));
    }

    @Test
    void shouldSummarizeSandboxPythonGovernanceMetadataWithoutCodeValues() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "sandbox_python",
                Map.of(
                        "code", "print('secret-code-marker')",
                        "networkRequested", true,
                        "requestedHosts", List.of("example.test")),
                Map.of(),
                "run-1:call-1",
                List.of("sandbox_python")));

        assertTrue(result.success());
        String summary = audit.requested.get(0).argumentsSummary();
        assertTrue(summary.contains("\"toolId\":\"sandbox_python\""));
        assertTrue(summary.contains("\"runtimeType\":\"CODE_INTERPRETER\""));
        assertTrue(summary.contains("\"codeLength\":27"));
        assertTrue(summary.contains("\"networkRequested\":true"));
        assertTrue(summary.contains("\"requestedHostsPresent\":true"));
        assertTrue(summary.contains("\"requestedHostCount\":1"));
        assertTrue(summary.contains("\"argumentCount\":3"));
        assertTrue(summary.contains("\"argumentValueCount\":3"));
        assertTrue(summary.contains("\"argumentValueTotalLength\":45"));
        assertTrue(summary.contains("\"argumentValueMaxLength\":27"));
        assertFalse(summary.contains("example.test"));
        assertFalse(summary.contains("secret-code-marker"));
        assertFalse(summary.contains("print("));
    }

    @Test
    void shouldSummarizeSandboxPythonAuditWithoutPrevalidatedHostValues() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "sandbox_python",
                Map.of(
                        "code", "print('secret-code-marker')",
                        "networkRequested", true,
                        "requestedHosts", List.of("example.test?access_token=host-secret")),
                Map.of(),
                "run-1:call-1",
                List.of("sandbox_python")));

        assertTrue(result.success());
        String summary = audit.requested.get(0).argumentsSummary();
        assertTrue(summary.contains("\"toolId\":\"sandbox_python\""));
        assertTrue(summary.contains("\"requestedHostsPresent\":true"));
        assertTrue(summary.contains("\"requestedHostCount\":1"));
        assertFalse(summary.contains("example.test"));
        assertFalse(summary.contains("access_token=host-secret"));
        assertFalse(summary.contains("secret-code-marker"));
    }

    @Test
    void shouldSummarizeSandboxFileConvertGovernanceMetadataWithoutContentValues() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "sandbox_file_convert",
                Map.of(
                        "sourceFormat", "docx",
                        "targetFormat", "txt",
                        "contentEncoding", "base64",
                        "content", "UEsDBAo=secret-docx-marker"),
                Map.of(),
                "run-1:call-1",
                List.of("sandbox_file_convert")));

        assertTrue(result.success());
        String summary = audit.requested.get(0).argumentsSummary();
        assertTrue(summary.contains("\"toolId\":\"sandbox_file_convert\""));
        assertTrue(summary.contains("\"runtimeType\":\"FILE_CONVERSION\""));
        assertTrue(summary.contains("\"sourceFormat\":\"docx\""));
        assertTrue(summary.contains("\"sourceFormatPresent\":true"));
        assertTrue(summary.contains("\"sourceFormatLength\":4"));
        assertTrue(summary.contains("\"targetFormat\":\"txt\""));
        assertTrue(summary.contains("\"targetFormatPresent\":true"));
        assertTrue(summary.contains("\"targetFormatLength\":3"));
        assertTrue(summary.contains("\"contentEncoding\":\"base64\""));
        assertTrue(summary.contains("\"contentEncodingPresent\":true"));
        assertTrue(summary.contains("\"contentEncodingLength\":6"));
        assertTrue(summary.contains("\"contentLength\":26"));
        assertTrue(summary.contains("\"binaryInput\":true"));
        assertTrue(summary.contains("\"networkRequested\":false"));
        assertTrue(summary.contains("\"argumentCount\":4"));
        assertTrue(summary.contains("\"argumentValueCount\":4"));
        assertTrue(summary.contains("\"argumentValueTotalLength\":39"));
        assertTrue(summary.contains("\"argumentValueMaxLength\":26"));
        assertFalse(summary.contains("UEsDBAo="));
        assertFalse(summary.contains("secret-docx-marker"));
    }

    @Test
    void shouldSummarizeSandboxFileConvertAuditWithoutPrevalidatedFormatValues() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.failed("unsupported conversion"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "sandbox_file_convert",
                Map.of(
                        "sourceFormat", "docx-secret-marker",
                        "targetFormat", "txt-secret-marker",
                        "contentEncoding", "base64-secret-marker",
                        "content", "UEsDBAo=secret-docx-marker"),
                Map.of(),
                "run-1:call-1",
                List.of("sandbox_file_convert")));

        assertFalse(result.success());
        String summary = audit.requested.get(0).argumentsSummary();
        assertTrue(summary.contains("\"toolId\":\"sandbox_file_convert\""));
        assertTrue(summary.contains("\"sourceFormat\":\"unsupported\""));
        assertTrue(summary.contains("\"sourceFormatPresent\":true"));
        assertTrue(summary.contains("\"sourceFormatLength\":18"));
        assertTrue(summary.contains("\"targetFormat\":\"unsupported\""));
        assertTrue(summary.contains("\"targetFormatPresent\":true"));
        assertTrue(summary.contains("\"targetFormatLength\":17"));
        assertTrue(summary.contains("\"contentEncoding\":\"unsupported\""));
        assertTrue(summary.contains("\"contentEncodingPresent\":true"));
        assertTrue(summary.contains("\"contentEncodingLength\":20"));
        assertTrue(summary.contains("\"binaryInput\":false"));
        assertFalse(summary.contains("docx-secret-marker"));
        assertFalse(summary.contains("txt-secret-marker"));
        assertFalse(summary.contains("base64-secret-marker"));
        assertFalse(summary.contains("UEsDBAo="));
        assertFalse(summary.contains("secret-docx-marker"));
    }

    @Test
    void shouldSummarizeRemoteA2aGovernanceMetadataWithoutPromptOrMetadataValues() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "invoke_remote_a2a_agent",
                Map.of(
                        "agentName", "planner-secret-agent",
                        "prompt", "draft a confidential launch plan",
                        "metadata", Map.of(
                                "version", "version-secret-marker",
                                "source", "secret-source-marker")),
                Map.of(),
                "run-1:call-1",
                List.of("invoke_remote_a2a_agent")));

        assertTrue(result.success());
        String summary = audit.requested.get(0).argumentsSummary();
        assertTrue(summary.contains("\"toolId\":\"invoke_remote_a2a_agent\""));
        assertTrue(summary.contains("\"agentNamePresent\":true"));
        assertTrue(summary.contains("\"agentNameLength\":20"));
        assertTrue(summary.contains("\"promptLength\":32"));
        assertTrue(summary.contains("\"metadataKeys\":["));
        assertTrue(summary.contains("version"));
        assertTrue(summary.contains("source"));
        assertTrue(summary.contains("\"metadataCount\":2"));
        assertTrue(summary.contains("\"metadataValueCount\":2"));
        assertTrue(summary.contains("\"metadataValueTotalLength\":41"));
        assertTrue(summary.contains("\"metadataValueMaxLength\":21"));
        assertTrue(summary.contains("\"versionPresent\":true"));
        assertTrue(summary.contains("\"versionLength\":21"));
        assertTrue(summary.contains("\"argumentCount\":3"));
        assertTrue(summary.contains("\"argumentValueCount\":3"));
        assertTrue(summary.contains("\"argumentValueTotalLength\":112"));
        assertTrue(summary.contains("\"argumentValueMaxLength\":60"));
        assertFalse(summary.contains("planner-secret-agent"));
        assertFalse(summary.contains("confidential launch plan"));
        assertFalse(summary.contains("version-secret-marker"));
        assertFalse(summary.contains("secret-source-marker"));
    }

    @Test
    void shouldSummarizeOpenApiGovernanceMetadataWithoutParameterHeaderOrBodyValues() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "openapi_customers",
                Map.of(
                        "path", Map.of("customerId", "cust-secret-marker"),
                        "query", Map.of("status", "active-secret-marker"),
                        "parameters", Map.of("page", "page-secret-marker"),
                        "header", Map.of("x-api-key", "header-secret-marker"),
                        "requestBody", Map.of(
                                "email", "customer-secret@example.test",
                                "token", "body-secret-marker")),
                Map.of(),
                "run-1:call-1",
                List.of("openapi_customers")));

        assertTrue(result.success());
        String summary = audit.requested.get(0).argumentsSummary();
        assertTrue(summary.contains("\"toolId\":\"openapi_customers\""));
        assertTrue(summary.contains("\"provider\":\"OPENAPI\""));
        assertTrue(summary.contains("\"argumentCount\":5"));
        assertTrue(summary.contains("\"pathKeys\":[\"customerId\"]"));
        assertTrue(summary.contains("\"pathCount\":1"));
        assertTrue(summary.contains("\"pathValueCount\":1"));
        assertTrue(summary.contains("\"pathValueTotalLength\":18"));
        assertTrue(summary.contains("\"pathValueMaxLength\":18"));
        assertTrue(summary.contains("\"queryKeys\":[\"status\"]"));
        assertTrue(summary.contains("\"queryCount\":1"));
        assertTrue(summary.contains("\"queryValueCount\":1"));
        assertTrue(summary.contains("\"queryValueTotalLength\":20"));
        assertTrue(summary.contains("\"queryValueMaxLength\":20"));
        assertTrue(summary.contains("\"parameterKeys\":[\"page\"]"));
        assertTrue(summary.contains("\"parameterCount\":1"));
        assertTrue(summary.contains("\"parameterValueCount\":1"));
        assertTrue(summary.contains("\"parameterValueTotalLength\":18"));
        assertTrue(summary.contains("\"parameterValueMaxLength\":18"));
        assertTrue(summary.contains("\"headerKeys\":[\"x-api-key\"]"));
        assertTrue(summary.contains("\"headerCount\":1"));
        assertTrue(summary.contains("\"headerValueCount\":1"));
        assertTrue(summary.contains("\"headerValueTotalLength\":20"));
        assertTrue(summary.contains("\"headerValueMaxLength\":20"));
        assertTrue(summary.contains("\"requestBodyPresent\":true"));
        assertTrue(summary.contains("\"requestBodyType\":\"object\""));
        assertTrue(summary.contains("\"requestBodyKeys\":["));
        assertTrue(summary.contains("email"));
        assertTrue(summary.contains("\"requestBodyFieldCount\":2"));
        assertTrue(summary.contains("\"requestBodyValueCount\":2"));
        assertTrue(summary.contains("\"requestBodyValueTotalLength\":46"));
        assertTrue(summary.contains("\"requestBodyValueMaxLength\":28"));
        assertFalse(summary.contains("cust-secret-marker"));
        assertFalse(summary.contains("active-secret-marker"));
        assertFalse(summary.contains("page-secret-marker"));
        assertFalse(summary.contains("header-secret-marker"));
        assertFalse(summary.contains("customer-secret@example.test"));
        assertFalse(summary.contains("token"));
        assertFalse(summary.contains("body-secret-marker"));
    }

    @Test
    void shouldFilterUnsafeKeyNamesFromCrossProviderAuditSummaries() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok("{\"ok\":true}"));
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                "openapi_customers",
                Map.of(
                        "path", Map.of("customerId", "cust-1"),
                        "query", Map.of("access_token_secret_marker", "secret-query-value"),
                        "header", Map.of("x\napi-key", "secret-header-value"),
                        "requestBody", Map.of(
                                "email", "customer@example.test",
                                "sessionToken=secret-marker", "secret-body-value"),
                        "line\nbreak", "unsafe-key-value"),
                Map.of(),
                "run-1:call-1",
                List.of("openapi_customers")));

        assertTrue(result.success());
        String summary = audit.requested.get(0).argumentsSummary();
        assertTrue(summary.contains("\"argumentCount\":5"));
        assertTrue(summary.contains("\"pathKeys\":[\"customerId\"]"));
        assertTrue(summary.contains("\"queryKeys\":[]"));
        assertTrue(summary.contains("\"headerKeys\":[]"));
        assertTrue(summary.contains("\"requestBodyKeys\":[\"email\"]"));
        assertFalse(summary.contains("access_token_secret_marker"));
        assertFalse(summary.contains("sessionToken=secret-marker"));
        assertFalse(summary.contains("secret-query-value"));
        assertFalse(summary.contains("secret-header-value"));
        assertFalse(summary.contains("secret-body-value"));
        assertFalse(summary.contains("unsafe-key-value"));
    }

    @Test
    void shouldPublishArtifactsFromSuccessfulToolResultWithFullRequestContext() {
        CountingToolPort tool = new CountingToolPort(ToolInvocationResult.ok(
                "{\"artifactType\":\"REPORT\",\"b64Json\":\"raw-image-bytes\"}"));
        RecordingToolArtifactPublicationPort artifacts = new RecordingToolArtifactPublicationPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(tool),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                ToolInvocationAuditPort.noop(),
                ToolApprovalRequestRepositoryPort.noop(),
                ApprovalRequestQueryPort.empty(),
                ToolOutputRedactionPort.basicSecretPatterns(),
                artifacts,
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("weather"));

        assertTrue(result.success());
        assertEquals("{\"artifactType\":\"REPORT\",\"b64Json\":\"[REDACTED]\"}", result.content());
        assertEquals(1, artifacts.published.size());
        assertEquals("run-1", artifacts.published.get(0).request().runId());
        assertEquals("step-1", artifacts.published.get(0).request().stepId());
        assertEquals("tenant-1", artifacts.published.get(0).request().tenantId());
        assertEquals("user-1", artifacts.published.get(0).request().userId());
        assertEquals("{\"artifactType\":\"REPORT\",\"b64Json\":\"raw-image-bytes\"}",
                artifacts.published.get(0).result().content());
    }

    @Test
    void shouldNotPublishArtifactsForDeniedOrFailedToolResults() {
        RecordingToolArtifactPublicationPort deniedArtifacts = new RecordingToolArtifactPublicationPort();
        LocalToolGatewayPort deniedGateway = new LocalToolGatewayPort(
                new SingleToolRegistry(new CountingToolPort(ToolInvocationResult.ok("should-not-run"))),
                new FixedToolPolicyPort(PolicyDecision.deny("deny-1",
                        ToolPolicyReasonCodes.TOOL_NOT_BOUND,
                        "Tool is not bound")),
                ToolInvocationAuditPort.noop(),
                ToolApprovalRequestRepositoryPort.noop(),
                ApprovalRequestQueryPort.empty(),
                ToolOutputRedactionPort.noop(),
                deniedArtifacts,
                FIXED_CLOCK);

        deniedGateway.invoke(request("memory-write"));

        RecordingToolArtifactPublicationPort failedArtifacts = new RecordingToolArtifactPublicationPort();
        LocalToolGatewayPort failedGateway = new LocalToolGatewayPort(
                new SingleToolRegistry(new CountingToolPort(ToolInvocationResult.failed("tool failed"))),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                ToolInvocationAuditPort.noop(),
                ToolApprovalRequestRepositoryPort.noop(),
                ApprovalRequestQueryPort.empty(),
                ToolOutputRedactionPort.noop(),
                failedArtifacts,
                FIXED_CLOCK);

        failedGateway.invoke(request("weather"));

        assertEquals(0, deniedArtifacts.published.size());
        assertEquals(0, failedArtifacts.published.size());
    }

    @Test
    void shouldReturnToolResultWhenArtifactPublicationFails() {
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(new CountingToolPort(ToolInvocationResult.ok("{\"artifactType\":\"REPORT\"}"))),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                ToolInvocationAuditPort.noop(),
                ToolApprovalRequestRepositoryPort.noop(),
                ApprovalRequestQueryPort.empty(),
                ToolOutputRedactionPort.noop(),
                (request, result) -> {
                    throw new IllegalStateException("artifact publication failed");
                },
                FIXED_CLOCK);

        ToolInvocationResult result = gateway.invoke(request("weather"));

        assertTrue(result.success());
        assertEquals("{\"artifactType\":\"REPORT\"}", result.content());
    }

    @Test
    void shouldGenerateAuditRunIdForLegacyRequestWithoutRunId() {
        RecordingToolInvocationAuditPort audit = new RecordingToolInvocationAuditPort();
        LocalToolGatewayPort gateway = new LocalToolGatewayPort(
                new SingleToolRegistry(new CountingToolPort(ToolInvocationResult.ok("ok"))),
                new FixedToolPolicyPort(PolicyDecision.allow("allow-1")),
                audit,
                FIXED_CLOCK);

        gateway.invoke(new ToolInvocationRequest(
                null,
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                null,
                null,
                "weather",
                Map.of(),
                Map.of(),
                "call-1",
                List.of("weather")));

        assertTrue(audit.requested.get(0).runId().startsWith("legacy-run:"));
        assertEquals("legacy-user", audit.requested.get(0).userId());
    }

    private static ToolInvocationRequest request(String toolId) {
        return new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "agent-identity-1",
                toolId,
                Map.of("input", "value"),
                Map.of("knowledgeBaseId", "kb-1"),
                "run-1:call-1",
                List.of(toolId));
    }

    private static ToolInvocationRequest requestWithRollout(String toolId, String rolloutId) {
        return new ToolInvocationRequest(
                "run-1",
                "step-1",
                "call-1",
                "agent-1",
                "version-1",
                rolloutId,
                "tenant-1",
                "user-1",
                "agent-identity-1",
                toolId,
                Map.of("input", "value"),
                Map.of("knowledgeBaseId", "kb-1"),
                "run-1:call-1",
                List.of(toolId));
    }

    private static ApprovalRequest approval(ApprovalRequestStatus status) {
        return new ApprovalRequest(
                "approval-1",
                "run-1",
                "step-1",
                "invocation-1",
                "tenant-1",
                "user-1",
                "agent-1",
                "memory-forget",
                ApprovalType.TOOL_EXECUTION,
                com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel.HIGH,
                "Tool memory-forget requires approval",
                "{\"argumentKeys\":[\"input\"]}",
                status,
                FIXED_CLOCK.instant().minusSeconds(60),
                null,
                status == ApprovalRequestStatus.PENDING ? null : "admin-1",
                status == ApprovalRequestStatus.PENDING ? null : FIXED_CLOCK.instant().minusSeconds(1),
                status == ApprovalRequestStatus.PENDING ? null : "decided");
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

    private static final class RecordingToolApprovalRequestRepositoryPort implements ToolApprovalRequestRepositoryPort {
        private final List<ApprovalRequest> saved = new ArrayList<>();

        @Override
        public void save(ApprovalRequest request) {
            saved.add(request);
        }
    }

    private static final class RecordingToolArtifactPublicationPort implements ToolArtifactPublicationPort {
        private final List<PublishedToolArtifact> published = new ArrayList<>();

        @Override
        public void publish(ToolInvocationRequest request, ToolInvocationResult result) {
            published.add(new PublishedToolArtifact(request, result));
        }
    }

    private record PublishedToolArtifact(ToolInvocationRequest request, ToolInvocationResult result) {
    }

    private static final class FixedApprovalQueryPort implements ApprovalRequestQueryPort {
        private final ApprovalRequest approval;

        private FixedApprovalQueryPort(ApprovalRequest approval) {
            this.approval = approval;
        }

        @Override
        public Optional<ApprovalRequest> findById(String approvalId) {
            return approval.approvalId().equals(approvalId) ? Optional.of(approval) : Optional.empty();
        }

        @Override
        public Optional<ApprovalRequest> findLatestByRunIdAndStepId(String runId, String stepId) {
            return runId.equals(approval.runId()) && stepId.equals(approval.stepId())
                    ? Optional.of(approval)
                    : Optional.empty();
        }

        @Override
        public ApprovalRequestPage page(ApprovalRequestQuery query) {
            return new ApprovalRequestPage(List.of(approval), 1L, query.size(), query.current(), 1L);
        }
    }

    private static final class FixedToolPolicyPort implements ToolPolicyPort {
        private final PolicyDecision decision;

        private FixedToolPolicyPort(PolicyDecision decision) {
            this.decision = decision;
        }

        @Override
        public PolicyDecision decide(ToolPolicyRequest request) {
            return decision;
        }
    }

    private static final class SingleToolRegistry implements ToolRegistryPort {
        private final ToolPort tool;

        private SingleToolRegistry(ToolPort tool) {
            this.tool = tool;
        }

        @Override
        public List<ToolDescriptor> listTools() {
            return List.of(new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"));
        }

        @Override
        public Optional<ToolPort> find(String toolId) {
            return Optional.of(tool);
        }
    }

    private static final class CountingToolPort implements ToolPort {
        private final AtomicInteger calls = new AtomicInteger();
        private final ToolInvocationResult result;

        private CountingToolPort(ToolInvocationResult result) {
            this.result = result;
        }

        @Override
        public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
            calls.incrementAndGet();
            return result;
        }
    }

    private static final class RequestAwareCountingToolPort implements ToolPort, ToolInvocationRequestAwarePort {
        private final AtomicInteger legacyCalls = new AtomicInteger();
        private final AtomicInteger requestAwareCalls = new AtomicInteger();
        private final ToolInvocationResult result;
        private ToolInvocationRequest lastRequest;

        private RequestAwareCountingToolPort(ToolInvocationResult result) {
            this.result = result;
        }

        @Override
        public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
            legacyCalls.incrementAndGet();
            return result;
        }

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            requestAwareCalls.incrementAndGet();
            lastRequest = request;
            return result;
        }
    }

    private static final class ThrowingToolPort implements ToolPort {

        @Override
        public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
            throw new IllegalStateException("tool boom");
        }
    }
}

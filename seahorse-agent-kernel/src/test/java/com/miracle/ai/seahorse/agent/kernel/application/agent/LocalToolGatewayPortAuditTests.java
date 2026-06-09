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
        assertTrue(audit.requested.get(0).argumentsSummary().contains("input"));
        assertEquals(audit.requested.get(0).invocationId(), audit.decisions.get(0).invocationId());
        assertEquals("allow-1", audit.decisions.get(0).policyDecisionId());
        assertEquals(ToolInvocationStatus.ALLOWED, audit.decisions.get(0).status());
        assertEquals(audit.requested.get(0).invocationId(), audit.completed.get(0).invocationId());
        assertEquals(ToolInvocationStatus.SUCCEEDED, audit.completed.get(0).status());
        assertTrue(audit.completed.get(0).resultSummary().contains("length"));
        assertEquals(FIXED_CLOCK.instant(), audit.completed.get(0).finishedAt());
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

        ToolInvocationResult result = gateway.invoke(request("memory-forget"));

        assertFalse(result.success());
        assertEquals(ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED, result.error());
        assertEquals(0, tool.calls.get());
        assertEquals(ToolInvocationStatus.APPROVAL_REQUIRED, audit.decisions.get(0).status());
        assertEquals(ToolInvocationStatus.APPROVAL_REQUIRED, audit.completed.get(0).status());
        assertEquals(1, approvals.saved.size());
        ApprovalRequest approval = approvals.saved.get(0);
        assertEquals(ApprovalRequestStatus.PENDING, approval.status());
        assertEquals(ApprovalType.TOOL_EXECUTION, approval.approvalType());
        assertEquals("run-1", approval.runId());
        assertEquals("step-1", approval.stepId());
        assertEquals(audit.requested.get(0).invocationId(), approval.toolInvocationId());
        assertEquals("tenant-1", approval.tenantId());
        assertEquals("user-1", approval.userId());
        assertEquals("agent-1", approval.agentId());
        assertEquals("memory-forget", approval.toolId());
        assertEquals(FIXED_CLOCK.instant(), approval.requestedAt());
        assertTrue(approval.argumentsPreviewJson().contains("input"));
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
        assertEquals("length=16", audit.completed.get(0).resultSummary());
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
        assertEquals("length=68", audit.completed.get(0).resultSummary());
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

    private static final class ThrowingToolPort implements ToolPort {

        @Override
        public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
            throw new IllegalStateException("tool boom");
        }
    }
}

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

package com.miracle.ai.seahorse.agent.kernel.application.agent.approval;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalModifyCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestDecisionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelApprovalManagementServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-23T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldPagePendingApprovalsBehindAdminBoundary() {
        MemoryApprovalRepository repository = new MemoryApprovalRepository(
                List.of(approval("approval-1", ApprovalRequestStatus.PENDING)));
        ApprovalManagementInboundPort service = new KernelApprovalManagementService(
                repository,
                repository,
                adminUser(),
                FIXED_CLOCK);

        ApprovalRequestPage page = service.page("tenant-1", null, 2L, 20L);

        assertEquals("tenant-1", repository.lastQuery.tenantId());
        assertEquals(ApprovalRequestStatus.PENDING, repository.lastQuery.status());
        assertEquals(2L, repository.lastQuery.current());
        assertEquals(20L, repository.lastQuery.size());
        assertEquals(1L, page.total());
    }

    @Test
    void shouldListPendingApprovalsByRunForOwningUser() {
        MemoryApprovalRepository repository = new MemoryApprovalRepository(List.of(
                approval("approval-1", "run-1", "user-1", ApprovalRequestStatus.PENDING),
                approval("approval-2", "run-1", "user-2", ApprovalRequestStatus.PENDING),
                approval("approval-3", "run-2", "user-1", ApprovalRequestStatus.PENDING),
                approval("approval-4", "run-1", "user-1", ApprovalRequestStatus.APPROVED)));
        ApprovalManagementInboundPort service = new KernelApprovalManagementService(
                repository,
                repository,
                user(),
                FIXED_CLOCK);

        List<ApprovalRequest> approvals = service.listPendingByRunId("run-1");

        assertEquals("run-1", repository.lastQuery.runId());
        assertEquals(ApprovalRequestStatus.PENDING, repository.lastQuery.status());
        assertEquals(List.of("approval-1"), approvals.stream().map(ApprovalRequest::approvalId).toList());
    }

    @Test
    void shouldApprovePendingApprovalWithCurrentAdmin() {
        MemoryApprovalRepository repository = new MemoryApprovalRepository(
                List.of(approval("approval-1", ApprovalRequestStatus.PENDING)));
        ApprovalManagementInboundPort service = new KernelApprovalManagementService(
                repository,
                repository,
                adminUser(),
                FIXED_CLOCK);

        ApprovalRequest decided = service.approve(
                "approval-1",
                new ApprovalDecisionCommand("Looks safe"));

        assertEquals(ApprovalRequestStatus.APPROVED, decided.status());
        assertEquals("admin-1", decided.decidedBy());
        assertEquals(NOW, decided.decidedAt());
        assertEquals("Looks safe", decided.decisionComment());
        assertEquals(ApprovalRequestStatus.PENDING, repository.lastDecision.fromStatus());
        assertEquals(ApprovalRequestStatus.APPROVED, repository.lastDecision.toStatus());
    }

    @Test
    void shouldApprovePendingApprovalWithOwningUser() {
        MemoryApprovalRepository repository = new MemoryApprovalRepository(
                List.of(approval("approval-1", "run-1", "user-1", ApprovalRequestStatus.PENDING)));
        ApprovalManagementInboundPort service = new KernelApprovalManagementService(
                repository,
                repository,
                user(),
                FIXED_CLOCK);

        ApprovalRequest decided = service.approve(
                "approval-1",
                new ApprovalDecisionCommand("Confirmed from chat"));

        assertEquals(ApprovalRequestStatus.APPROVED, decided.status());
        assertEquals("user-1", decided.decidedBy());
        assertEquals(NOW, decided.decidedAt());
        assertEquals("Confirmed from chat", decided.decisionComment());
        assertEquals(ApprovalRequestStatus.APPROVED, repository.lastDecision.toStatus());
    }

    @Test
    void shouldRejectDecisionWhenUserDoesNotOwnApproval() {
        MemoryApprovalRepository repository = new MemoryApprovalRepository(
                List.of(approval("approval-1", "run-1", "user-2", ApprovalRequestStatus.PENDING)));
        ApprovalManagementInboundPort service = new KernelApprovalManagementService(
                repository,
                repository,
                user(),
                FIXED_CLOCK);

        assertThrows(IllegalStateException.class,
                () -> service.approve("approval-1", new ApprovalDecisionCommand("not mine")));

        assertNull(repository.lastDecision);
    }

    @Test
    void shouldRejectPendingApprovalWithCurrentAdmin() {
        MemoryApprovalRepository repository = new MemoryApprovalRepository(
                List.of(approval("approval-1", ApprovalRequestStatus.PENDING)));
        ApprovalManagementInboundPort service = new KernelApprovalManagementService(
                repository,
                repository,
                adminUser(),
                FIXED_CLOCK);

        ApprovalRequest decided = service.reject(
                "approval-1",
                new ApprovalDecisionCommand("Risk too high"));

        assertEquals(ApprovalRequestStatus.REJECTED, decided.status());
        assertEquals("admin-1", decided.decidedBy());
        assertEquals(NOW, decided.decidedAt());
        assertEquals("Risk too high", decided.decisionComment());
        assertEquals(ApprovalRequestStatus.REJECTED, repository.lastDecision.toStatus());
    }

    @Test
    void shouldModifyPendingApprovalPreviewWithoutExecutingTool() {
        MemoryApprovalRepository repository = new MemoryApprovalRepository(
                List.of(approval("approval-1", ApprovalRequestStatus.PENDING)));
        ApprovalManagementInboundPort service = new KernelApprovalManagementService(
                repository,
                repository,
                adminUser(),
                FIXED_CLOCK);

        ApprovalRequest decided = service.modify(
                "approval-1",
                new ApprovalModifyCommand("{\"argumentKeys\":[\"input\"],\"modified\":true}", "Reduced scope"));

        assertEquals(ApprovalRequestStatus.MODIFIED, decided.status());
        assertEquals("{\"argumentKeys\":[\"input\"],\"modified\":true}", decided.argumentsPreviewJson());
        assertEquals("Reduced scope", decided.decisionComment());
        assertEquals(ApprovalRequestStatus.MODIFIED, repository.lastDecision.toStatus());
    }

    @Test
    void shouldRejectDecisionWhenApprovalIsNotPending() {
        MemoryApprovalRepository repository = new MemoryApprovalRepository(
                List.of(approval("approval-1", ApprovalRequestStatus.APPROVED)));
        ApprovalManagementInboundPort service = new KernelApprovalManagementService(
                repository,
                repository,
                adminUser(),
                FIXED_CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.approve("approval-1", new ApprovalDecisionCommand("again")));

        assertEquals("审批请求不是待处理状态", error.getMessage());
    }

    @Test
    void shouldRejectNonAdminAccess() {
        MemoryApprovalRepository repository = new MemoryApprovalRepository(List.of());
        ApprovalManagementInboundPort service = new KernelApprovalManagementService(
                repository,
                repository,
                user(),
                FIXED_CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.page(null, null, 1L, 10L));

        assertEquals("权限不足", error.getMessage());
    }

    private static ApprovalRequest approval(String approvalId, ApprovalRequestStatus status) {
        return approval(approvalId, "run-1", "user-1", status);
    }

    private static ApprovalRequest approval(String approvalId,
                                            String runId,
                                            String userId,
                                            ApprovalRequestStatus status) {
        return new ApprovalRequest(
                approvalId,
                runId,
                "step-1",
                "invocation-1",
                "tenant-1",
                userId,
                "agent-1",
                "memory-forget",
                ApprovalType.TOOL_EXECUTION,
                ToolRiskLevel.HIGH,
                "Tool memory-forget requires approval",
                "{\"argumentKeys\":[\"input\"]}",
                status,
                NOW.minusSeconds(60),
                null,
                status == ApprovalRequestStatus.PENDING ? null : "admin-0",
                status == ApprovalRequestStatus.PENDING ? null : NOW.minusSeconds(30),
                status == ApprovalRequestStatus.PENDING ? null : "already decided");
    }

    private static CurrentUserPort adminUser() {
        return () -> Optional.of(new CurrentUser("admin-1", "root", "admin", null));
    }

    private static CurrentUserPort user() {
        return () -> Optional.of(new CurrentUser("user-1", "alice", "user", null));
    }

    private static final class MemoryApprovalRepository
            implements ApprovalRequestQueryPort, ApprovalRequestDecisionPort {

        private final Map<String, ApprovalRequest> approvalsById;
        private ApprovalRequestQuery lastQuery;
        private ApprovalRequestDecision lastDecision;

        private MemoryApprovalRepository(List<ApprovalRequest> approvals) {
            approvalsById = approvals.stream()
                    .collect(java.util.stream.Collectors.toMap(ApprovalRequest::approvalId, request -> request));
        }

        @Override
        public Optional<ApprovalRequest> findById(String approvalId) {
            return Optional.ofNullable(approvalsById.get(approvalId));
        }

        @Override
        public ApprovalRequestPage page(ApprovalRequestQuery query) {
            lastQuery = query;
            List<ApprovalRequest> records = approvalsById.values().stream()
                    .filter(approval -> query.tenantId() == null || approval.tenantId().equals(query.tenantId()))
                    .filter(approval -> query.runId() == null || approval.runId().equals(query.runId()))
                    .filter(approval -> approval.status() == query.status())
                    .toList();
            return new ApprovalRequestPage(records, records.size(), query.size(), query.current(), 1L);
        }

        @Override
        public Optional<ApprovalRequest> decide(ApprovalRequestDecision decision) {
            lastDecision = decision;
            ApprovalRequest current = approvalsById.get(decision.approvalId());
            if (current == null || current.status() != decision.fromStatus()) {
                return Optional.empty();
            }
            ApprovalRequest decided = new ApprovalRequest(
                    current.approvalId(),
                    current.runId(),
                    current.stepId(),
                    current.toolInvocationId(),
                    current.tenantId(),
                    current.userId(),
                    current.agentId(),
                    current.toolId(),
                    current.approvalType(),
                    current.riskLevel(),
                    current.summary(),
                    decision.argumentsPreviewJson() == null
                            ? current.argumentsPreviewJson()
                            : decision.argumentsPreviewJson(),
                    decision.toStatus(),
                    current.requestedAt(),
                    current.expiresAt(),
                    decision.decidedBy(),
                    decision.decidedAt(),
                    decision.decisionComment());
            approvalsById.put(decision.approvalId(), decided);
            return Optional.of(decided);
        }
    }
}

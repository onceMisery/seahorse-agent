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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalModifyCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestDecisionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 审批管理应用服务。只负责编排鉴权、状态流转和仓储端口调用，不执行真实工具。
 */
public class KernelApprovalManagementService implements ApprovalManagementInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "权限不足";
    private static final String APPROVAL_NOT_FOUND = "审批请求不存在";
    private static final String APPROVAL_NOT_PENDING = "审批请求不是待处理状态";
    private static final String APPROVAL_STATE_CHANGED = "审批请求状态已变更";
    private static final long RUN_PENDING_APPROVAL_PAGE_SIZE = 50L;
    private static final int MAX_ARGUMENTS_PREVIEW_JSON_LENGTH = 4_000;
    private static final Set<String> ALLOWED_ARGUMENTS_PREVIEW_FIELDS = Set.of(
            "argumentKeys",
            "argumentCount",
            "argumentHash",
            "resourceRefs",
            "modified",
            "arguments");

    private final ApprovalRequestQueryPort queryPort;
    private final ApprovalRequestDecisionPort decisionPort;
    private final CurrentUserPort currentUserPort;
    private final AgentRunRepositoryPort runRepository;
    private final Clock clock;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public KernelApprovalManagementService(ApprovalRequestQueryPort queryPort,
                                           ApprovalRequestDecisionPort decisionPort,
                                           CurrentUserPort currentUserPort,
                                           Clock clock) {
        this(queryPort, decisionPort, currentUserPort, null, clock);
    }

    public KernelApprovalManagementService(ApprovalRequestQueryPort queryPort,
                                           ApprovalRequestDecisionPort decisionPort,
                                           CurrentUserPort currentUserPort,
                                           AgentRunRepositoryPort runRepository,
                                           Clock clock) {
        this.queryPort = Objects.requireNonNull(queryPort, "queryPort must not be null");
        this.decisionPort = Objects.requireNonNull(decisionPort, "decisionPort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.runRepository = runRepository;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public ApprovalRequestPage page(String tenantId, ApprovalRequestStatus status, long current, long size) {
        requireAdmin();
        return queryPort.page(new ApprovalRequestQuery(tenantId, status, current, size));
    }

    @Override
    public List<ApprovalRequest> listPendingByRunId(String runId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeRunId = requireText(runId, "runId must not be blank");
        requireReadableRunIfConfigured(safeRunId, currentUser);
        ApprovalRequestPage page = queryPort.page(new ApprovalRequestQuery(
                null,
                safeRunId,
                ApprovalRequestStatus.PENDING,
                ApprovalRequestQuery.DEFAULT_CURRENT,
                RUN_PENDING_APPROVAL_PAGE_SIZE));
        return page.records().stream()
                .filter(approval -> isAdmin(currentUser) || ownsUserId(approval.userId(), currentUser))
                .toList();
    }

    private void requireReadableRunIfConfigured(String runId, CurrentUser currentUser) {
        if (runRepository == null) {
            return;
        }
        runRepository.findRunById(runId)
                .map(run -> {
                    if (isAdmin(currentUser) || ownsUserId(run.userId(), currentUser)) {
                        return run;
                    }
                    throw new IllegalStateException(ACCESS_DENIED);
                })
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found"));
    }

    @Override
    public Optional<ApprovalRequest> findById(String approvalId) {
        requireAdmin();
        return queryPort.findById(requireText(approvalId, "approvalId 不能为空"));
    }

    @Override
    public ApprovalRequest approve(String approvalId, ApprovalDecisionCommand command) {
        return decide(approvalId, ApprovalRequestStatus.APPROVED, decisionComment(command), null);
    }

    @Override
    public ApprovalRequest reject(String approvalId, ApprovalDecisionCommand command) {
        return decide(approvalId, ApprovalRequestStatus.REJECTED, decisionComment(command), null);
    }

    @Override
    public ApprovalRequest modify(String approvalId, ApprovalModifyCommand command) {
        ApprovalModifyCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String argumentsPreviewJson = requireText(safeCommand.argumentsPreviewJson(), "argumentsPreviewJson 不能为空");
        return decide(approvalId, ApprovalRequestStatus.MODIFIED, safeCommand.decisionComment(), argumentsPreviewJson);
    }

    private ApprovalRequest decide(String approvalId,
                                   ApprovalRequestStatus toStatus,
                                   String decisionComment,
                                   String argumentsPreviewJson) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeApprovalId = requireText(approvalId, "approvalId 不能为空");
        ApprovalRequest current = queryPort.findById(safeApprovalId)
                .orElseThrow(() -> new IllegalArgumentException(APPROVAL_NOT_FOUND));
        if (!isAdmin(currentUser) && !ownsUserId(current.userId(), currentUser)) {
            throw new IllegalStateException(ACCESS_DENIED);
        }
        if (current.status() != ApprovalRequestStatus.PENDING) {
            throw new IllegalStateException(APPROVAL_NOT_PENDING);
        }
        String safeArgumentsPreviewJson = toStatus == ApprovalRequestStatus.MODIFIED
                ? validatedArgumentsPreviewJson(argumentsPreviewJson)
                : argumentsPreviewJson;
        ApprovalRequestDecision decision = new ApprovalRequestDecision(
                safeApprovalId,
                ApprovalRequestStatus.PENDING,
                toStatus,
                currentUser.userId(),
                clock.instant(),
                decisionComment,
                safeArgumentsPreviewJson);
        return decisionPort.decide(decision)
                .orElseThrow(() -> new IllegalStateException(APPROVAL_STATE_CHANGED));
    }

    private String validatedArgumentsPreviewJson(String value) {
        String json = requireText(value, "argumentsPreviewJson must not be blank");
        if (json.length() > MAX_ARGUMENTS_PREVIEW_JSON_LENGTH) {
            throw new IllegalArgumentException("argumentsPreviewJson exceeds maximum length");
        }
        JsonNode root;
        try {
            root = objectMapper.readTree(json);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("argumentsPreviewJson must be valid JSON", ex);
        }
        if (!root.isObject()) {
            throw new IllegalArgumentException("argumentsPreviewJson must be a JSON object");
        }
        root.fieldNames().forEachRemaining(field -> {
            if (!ALLOWED_ARGUMENTS_PREVIEW_FIELDS.contains(field)) {
                throw new IllegalArgumentException("argumentsPreviewJson contains unsupported fields");
            }
        });
        if (root.has("arguments") && !root.path("arguments").isObject()) {
            throw new IllegalArgumentException("argumentsPreviewJson.arguments must be a JSON object");
        }
        return json;
    }

    private CurrentUser requireAdmin() {
        currentUserPort.requireRole(ADMIN_ROLE);
        return currentUserPort.requireCurrentUser();
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.hasRole(ADMIN_ROLE);
    }

    private String currentUserId(CurrentUser currentUser) {
        Long userId = currentUser == null ? null : currentUser.userId();
        if (userId == null) {
            throw new IllegalStateException(ACCESS_DENIED);
        }
        return String.valueOf(userId);
    }

    private boolean ownsUserId(String resourceUserId, CurrentUser currentUser) {
        if (currentUser == null) {
            return false;
        }
        String numericUserId = currentUser.userId() == null ? null : String.valueOf(currentUser.userId());
        return Objects.equals(resourceUserId, numericUserId)
                || Objects.equals(resourceUserId, currentUser.operator());
    }

    private String decisionComment(ApprovalDecisionCommand command) {
        return command == null ? null : command.decisionComment();
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

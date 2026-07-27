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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.AgentFinalModelTurnPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ModelFailureSanitizer;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialJsonFieldClassifier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationIdentity;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunResumeInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class KernelAgentRunResumeService implements AgentRunResumeInboundPort {

    private static final Logger LOG = LoggerFactory.getLogger(KernelAgentRunResumeService.class);

    private static final String RUN_NOT_FOUND = "Agent run does not exist";
    private static final String CHECKPOINT_NOT_FOUND = "Waiting approval checkpoint does not exist";
    private static final String APPROVAL_NOT_FOUND = "Approval decision does not exist";
    private static final String ACCESS_DENIED = "权限不足";
    private static final String ADMIN_ROLE = "admin";
    private static final String MDC_TENANT_ID = "seahorse.tenant.id";
    private static final String IDENTITY_MISMATCH = "Resume identity does not match the agent run";
    private static final String RESULT_ID_PREFIX = "resume-step_";
    private static final Duration RESUME_LEASE_TTL = Duration.ofMinutes(5);
    private static final Duration RESUME_LEASE_HEARTBEAT_INTERVAL = Duration.ofSeconds(30);
    private static final Duration RESUME_BLOCKING_CALL_TIMEOUT = Duration.ofMinutes(4);
    private static final String SAFE_EVIDENCE_UNAVAILABLE_JSON =
            "{\"schemaVersion\":\"model-context-envelope-v1\",\"reasonCode\":\"EVIDENCE_UNAVAILABLE\"}";
    private static final TypeReference<Map<String, Object>> MAP_TYPE = new TypeReference<>() {
    };

    private final AgentRunRepositoryPort runRepository;
    private final AgentCheckpointRepositoryPort checkpointRepository;
    private final ApprovalRequestQueryPort approvalQueryPort;
    private final ToolGatewayPort toolGateway;
    private final AgentFinalModelTurnPort finalModelTurnPort;
    private final CurrentUserPort currentUserPort;
    private final KernelRagTraceRecorder traceRecorder;
    private final Clock clock;
    private final ObjectMapper objectMapper;
    private final Duration resumeLeaseTtl;
    private final Duration resumeLeaseHeartbeatInterval;
    private final Duration resumeBlockingCallTimeout;

    public KernelAgentRunResumeService(AgentRunRepositoryPort runRepository,
                                       AgentCheckpointRepositoryPort checkpointRepository,
                                       ApprovalRequestQueryPort approvalQueryPort,
                                       ToolGatewayPort toolGateway,
                                        AgentFinalModelTurnPort finalModelTurnPort,
                                       CurrentUserPort currentUserPort,
                                       Clock clock) {
        this(runRepository, checkpointRepository, approvalQueryPort, toolGateway, finalModelTurnPort, currentUserPort,
                clock, new ObjectMapper(), KernelRagTraceRecorder.noop());
    }

    public KernelAgentRunResumeService(AgentRunRepositoryPort runRepository,
                                       AgentCheckpointRepositoryPort checkpointRepository,
                                       ApprovalRequestQueryPort approvalQueryPort,
                                       ToolGatewayPort toolGateway,
                                        AgentFinalModelTurnPort finalModelTurnPort,
                                       CurrentUserPort currentUserPort,
                                       Clock clock,
                                       ObjectMapper objectMapper) {
        this(runRepository, checkpointRepository, approvalQueryPort, toolGateway, finalModelTurnPort, currentUserPort,
                clock, objectMapper, KernelRagTraceRecorder.noop());
    }

    public KernelAgentRunResumeService(AgentRunRepositoryPort runRepository,
                                       AgentCheckpointRepositoryPort checkpointRepository,
                                       ApprovalRequestQueryPort approvalQueryPort,
                                       ToolGatewayPort toolGateway,
                                       AgentFinalModelTurnPort finalModelTurnPort,
                                       CurrentUserPort currentUserPort,
                                       Clock clock,
                                       ObjectMapper objectMapper,
                                       KernelRagTraceRecorder traceRecorder) {
        this(runRepository, checkpointRepository, approvalQueryPort, toolGateway, finalModelTurnPort, currentUserPort,
                clock, objectMapper, traceRecorder, RESUME_LEASE_TTL, RESUME_LEASE_HEARTBEAT_INTERVAL,
                RESUME_BLOCKING_CALL_TIMEOUT);
    }

    KernelAgentRunResumeService(AgentRunRepositoryPort runRepository,
                                 AgentCheckpointRepositoryPort checkpointRepository,
                                 ApprovalRequestQueryPort approvalQueryPort,
                                 ToolGatewayPort toolGateway,
                                 AgentFinalModelTurnPort finalModelTurnPort,
                                 CurrentUserPort currentUserPort,
                                 Clock clock,
                                 ObjectMapper objectMapper,
                                 KernelRagTraceRecorder traceRecorder,
                                 Duration resumeLeaseTtl,
                                 Duration resumeLeaseHeartbeatInterval,
                                 Duration resumeBlockingCallTimeout) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.checkpointRepository = Objects.requireNonNull(
                checkpointRepository,
                "checkpointRepository must not be null");
        this.approvalQueryPort = Objects.requireNonNull(approvalQueryPort, "approvalQueryPort must not be null");
        this.toolGateway = Objects.requireNonNull(toolGateway, "toolGateway must not be null");
        this.finalModelTurnPort = Objects.requireNonNull(
                finalModelTurnPort, "finalModelTurnPort must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.objectMapper = Objects.requireNonNullElseGet(objectMapper, ObjectMapper::new);
        this.resumeLeaseTtl = requirePositiveDuration(resumeLeaseTtl, "resumeLeaseTtl");
        this.resumeLeaseHeartbeatInterval = requirePositiveDuration(
                resumeLeaseHeartbeatInterval, "resumeLeaseHeartbeatInterval");
        this.resumeBlockingCallTimeout = requirePositiveDuration(
                resumeBlockingCallTimeout, "resumeBlockingCallTimeout");
    }

    @Override
    public AgentRun resume(String runId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        AgentRun current = requireReadable(loadRun(runId), currentUser);
        return resumeInRunTenant(current);
    }

    private AgentRun resumeInRunTenant(AgentRun current) {
        String previousTenant = TenantContext.capture();
        Map<String, String> previousMdc = MDC.getCopyOfContextMap();
        Map<String, String> runMdc = previousMdc == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(previousMdc);
        runMdc.put(MDC_TENANT_ID, current.tenantId());
        TenantContext.set(current.tenantId());
        restoreMdc(runMdc);
        try {
            return resumeAuthorized(current);
        } finally {
            TenantContext.restore(previousTenant);
            restoreMdc(previousMdc);
        }
    }

    private AgentRun resumeAuthorized(AgentRun current) {
        if (current.status() != AgentRunStatus.WAITING_APPROVAL
                && current.status() != AgentRunStatus.RUNNING) {
            return current;
        }
        AgentCheckpoint checkpoint = latestWaitingApprovalCheckpoint(current.runId());
        ApprovalRequest approval = approvalQueryPort
                .findLatestByRunIdAndStepId(current.runId(), checkpoint.stepId())
                .orElseThrow(() -> new IllegalStateException(APPROVAL_NOT_FOUND));
        requireApprovalIdentity(current, checkpoint, approval);
        if (approval.status() == ApprovalRequestStatus.REJECTED) {
            if (current.status() != AgentRunStatus.WAITING_APPROVAL) {
                return current;
            }
            return transition(current, AgentRunStatus.REJECTED,
                    AgentRuntimeConstants.AGENT_RUN_APPROVAL_REJECTED_CODE,
                    safeApprovalDecisionComment(approval));
        }
        if (approval.status() == ApprovalRequestStatus.EXPIRED) {
            if (current.status() != AgentRunStatus.WAITING_APPROVAL) {
                return current;
            }
            return transition(current, AgentRunStatus.EXPIRED,
                    AgentRuntimeConstants.AGENT_RUN_APPROVAL_EXPIRED_CODE,
                    safeApprovalDecisionComment(approval));
        }
        if (approval.status() != ApprovalRequestStatus.APPROVED && approval.status() != ApprovalRequestStatus.MODIFIED) {
            throw new IllegalStateException("Approval must be approved before resume");
        }

        String leaseOwner = "resume:" + SnowflakeIds.nextIdString();
        Instant leaseAcquiredAt = clock.instant();
        if (!runRepository.acquireResumeLease(
                current.runId(), leaseOwner, leaseAcquiredAt.plus(resumeLeaseTtl), leaseAcquiredAt)) {
            return loadRun(current.runId());
        }
        try {
            AgentRun leasedRun = loadRun(current.runId());
            if (leasedRun.status() != AgentRunStatus.WAITING_APPROVAL
                    && leasedRun.status() != AgentRunStatus.RUNNING) {
                return leasedRun;
            }
            AgentRun running = leasedRun.status() == AgentRunStatus.RUNNING
                    ? leasedRun
                    : leasedRun.withStatus(AgentRunStatus.RUNNING, null, null, null);
            if (leasedRun.status() == AgentRunStatus.WAITING_APPROVAL
                    && !runRepository.updateRunIfStatusAndResumeLeaseOwner(
                            running, AgentRunStatus.WAITING_APPROVAL, leaseOwner, clock.instant())) {
                return loadRun(current.runId());
            }
            AgentResumeDescriptor resumeDescriptor;
            try {
                resumeDescriptor = resumeDescriptor(checkpoint);
            } catch (RuntimeException ex) {
                return transitionWithLease(running, AgentRunStatus.FAILED,
                        AgentRuntimeConstants.AGENT_RUN_RESUME_FAILED_CODE,
                        safeFailureMessage(ex), leaseOwner);
            }

            TraceRunScope resumeTrace = traceRecorder.startRun(resumeTraceCommand(current, checkpoint));
            TraceNodeScope resumeNode = traceRecorder.startNode(resumeTrace, new TraceNodeStartCommand(
                    "agent-run-resume",
                    "AGENT_RESUME",
                    KernelAgentRunResumeService.class.getName(),
                    "resume",
                    null,
                    0));
            Throwable traceFailure = null;
            try {
                ToolInvocationRequest request = pendingToolInvocation(checkpoint, approval);
                requireToolInvocationIdentity(current, checkpoint, approval, request);
                ToolInvocationResult toolResult = callWithLeaseHeartbeat(
                        running.runId(), leaseOwner, () -> toolGateway.invoke(request));
                appendToolStep(current.runId(), request, toolResult, leaseOwner);
                if (!toolResult.success()) {
                    traceFailure = new IllegalStateException("Resumed tool execution failed");
                    return transitionWithLease(running, AgentRunStatus.FAILED,
                            AgentRuntimeConstants.AGENT_RUN_RESUME_FAILED_CODE,
                            toolResult.error(), leaseOwner);
                }
                AgentFinalModelTurnPort.FinalModelTurnResult finalTurn;
                try {
                    finalTurn = callWithLeaseHeartbeat(
                            running.runId(), leaseOwner,
                            () -> requestModelTurn(
                                    running, checkpoint, request, toolResult, resumeDescriptor, resumeTrace));
                } catch (RuntimeException ex) {
                    appendFailedModelStepBestEffort(current.runId(), ex, leaseOwner);
                    if (ex instanceof AgentFinalModelTurnPort.FinalModelTurnException) {
                        throw ex;
                    }
                    throw new AgentFinalModelTurnPort.FinalModelTurnException(
                            ex, SAFE_EVIDENCE_UNAVAILABLE_JSON);
                }
                appendModelStep(current.runId(), checkpoint, finalTurn, leaseOwner);
                return transitionWithLease(running, AgentRunStatus.SUCCEEDED, null, null, leaseOwner);
            } catch (RuntimeException ex) {
                traceFailure = ex;
                return transitionWithLease(running, AgentRunStatus.FAILED,
                        AgentRuntimeConstants.AGENT_RUN_RESUME_FAILED_CODE,
                        safeFailureMessage(ex), leaseOwner);
            } finally {
                traceRecorder.finishNode(resumeNode, traceFailure);
                traceRecorder.finishRun(resumeTrace, traceFailure);
            }
        } finally {
            runRepository.releaseResumeLease(current.runId(), leaseOwner);
        }
    }

    private AgentRun loadRun(String runId) {
        String safeRunId = requireText(runId, "runId must not be blank");
        return runRepository.findRunById(safeRunId)
                .orElseThrow(() -> new IllegalArgumentException(RUN_NOT_FOUND));
    }

    private AgentCheckpoint latestWaitingApprovalCheckpoint(String runId) {
        return checkpointRepository.findLatestByRunId(runId)
                .filter(checkpoint -> checkpoint.checkpointType() == AgentCheckpointType.WAITING_APPROVAL)
                .orElseThrow(() -> new IllegalStateException(CHECKPOINT_NOT_FOUND));
    }

    private ToolInvocationRequest pendingToolInvocation(AgentCheckpoint checkpoint, ApprovalRequest approval) {
        JsonNode root = readTree(checkpoint.pendingToolCallJson(), "pendingToolCallJson");
        String rolloutId = text(root, "rolloutId");
        if (isBlank(rolloutId)) {
            rolloutId = approval.rolloutId();
        }
        return new ToolInvocationRequest(
                text(root, "runId"),
                text(root, "toolCallId"),
                text(root, "toolCallId"),
                text(root, "agentId"),
                text(root, "versionId"),
                rolloutId,
                text(root, "tenantId"),
                text(root, "userId"),
                text(root, "agentIdentityId"),
                text(root, "toolId"),
                approvalArguments(root, approval),
                stringMap(root.path("resourceRefs")),
                ToolInvocationIdentity.deterministicKey(text(root, "runId"), text(root, "toolCallId")),
                stringList(root.path("allowedToolIds")));
    }

    private Map<String, Object> approvalArguments(JsonNode root, ApprovalRequest approval) {
        Optional<Map<String, Object>> modifiedArguments = modifiedArguments(approval);
        return modifiedArguments.orElseGet(() -> objectMap(root.path("arguments")));
    }

    private Optional<Map<String, Object>> modifiedArguments(ApprovalRequest approval) {
        if (approval.status() != ApprovalRequestStatus.MODIFIED) {
            return Optional.empty();
        }
        if (isBlank(approval.argumentsPreviewJson())) {
            throw new IllegalStateException("Modified approval arguments are missing");
        }
        JsonNode root = readTree(approval.argumentsPreviewJson(), "argumentsPreviewJson");
        JsonNode arguments = root.path("arguments");
        if (!arguments.isObject()) {
            throw new IllegalStateException("Modified approval arguments must be an object");
        }
        return Optional.of(objectMap(arguments));
    }

    private AgentFinalModelTurnPort.FinalModelTurnResult requestModelTurn(
            AgentRun run,
            AgentCheckpoint checkpoint,
            ToolInvocationRequest request,
            ToolInvocationResult toolResult,
            AgentResumeDescriptor resumeDescriptor,
            TraceRunScope resumeTrace) {
        List<ChatMessage> messages = messageHistory(checkpoint.messageHistoryJson());
        messages.add(ChatMessage.tool(request.toolCallId(), toolResult.content()));
        AgentLoopRequest loopRequest = AgentLoopRequest.builder()
                .question("Continue after the approved tool result.")
                .modelId(resumeDescriptor.modelId())
                .samplingOptions(resumeDescriptor.samplingOptions())
                .maxSteps(1)
                .allowedToolIds(List.of())
                .explicitToolAllowlist(true)
                .runId(run.runId())
                .agentId(request.agentId())
                .versionId(request.versionId())
                .rolloutId(request.rolloutId())
                .tenantId(request.tenantId())
                .userId(request.userId())
                .agentIdentityId(request.agentIdentityId())
                .runtimeContextSnapshot(resumeDescriptor.runtimeContextMode()
                        == AgentResumeDescriptor.RuntimeContextMode.SNAPSHOT
                                ? resumeDescriptor.runtimeContextSnapshot()
                                : null)
                .skillRuntimeContext(resumeDescriptor.runtimeContextMode()
                        == AgentResumeDescriptor.RuntimeContextMode.SNAPSHOT
                                ? resumeDescriptor.skillRuntimeContext()
                                : null)
                .build();
        return finalModelTurnPort.requestFinalModelTurn(loopRequest, messages, resumeTrace);
    }

    private TraceRunStartCommand resumeTraceCommand(AgentRun run, AgentCheckpoint checkpoint) {
        Map<String, String> attributes = new LinkedHashMap<>();
        attributes.put("seahorse.tenant.id", run.tenantId());
        attributes.put("seahorse.operation", "agent-run-resume");
        attributes.put("seahorse.resume.original_run_id", run.runId());
        attributes.put("seahorse.resume.checkpoint_id", checkpoint.checkpointId());
        if (!isBlank(run.traceId())) {
            attributes.put("seahorse.resume.original_trace_id", run.traceId());
        }
        return new TraceRunStartCommand(
                "agent-run-resume",
                "KernelAgentRunResumeService.resume",
                run.conversationId(),
                run.runId(),
                run.userId(),
                attributes);
    }

    private void appendToolStep(
            String runId, ToolInvocationRequest request, ToolInvocationResult result, String leaseOwner) {
        Instant now = clock.instant();
        AgentStep step = new AgentStep(
                nextStepId(),
                runId,
                nextStepNo(runId),
                AgentStepType.TOOL_CALL,
                result.success() ? AgentStepStatus.SUCCEEDED : AgentStepStatus.FAILED,
                safeJsonText(toJson(Map.of(
                        "toolCallId", request.toolCallId(),
                        "toolId", request.toolId(),
                        "arguments", request.arguments()))),
                safeJsonText(toJson(Map.of(
                        "success", result.success(),
                        "content", Objects.requireNonNullElse(result.content(), ""),
                        "error", Objects.requireNonNullElse(result.error(), "")))),
                result.success() ? null : AgentRuntimeConstants.AGENT_STEP_FAILURE_CODE,
                result.success() ? null : safeText(result.error()),
                now,
                now);
        if (!runRepository.appendStepIfResumeLeaseOwner(step, leaseOwner, now)) {
            throw new IllegalStateException("Resume execution lease was lost");
        }
    }

    private void appendModelStep(
            String runId,
            AgentCheckpoint checkpoint,
            AgentFinalModelTurnPort.FinalModelTurnResult finalTurn,
            String leaseOwner) {
        Instant now = clock.instant();
        String inputJson = finalTurn.safeEvidenceJson().isBlank()
                ? SAFE_EVIDENCE_UNAVAILABLE_JSON
                : finalTurn.safeEvidenceJson();
        AgentStep step = new AgentStep(
                nextStepId(),
                runId,
                nextStepNo(runId),
                AgentStepType.MODEL_TURN,
                AgentStepStatus.SUCCEEDED,
                safeJsonText(inputJson),
                safeJsonText(toJson(Map.of("content", finalTurn.content()))),
                null,
                null,
                now,
                now);
        if (!runRepository.appendStepIfResumeLeaseOwner(step, leaseOwner, now)) {
            throw new IllegalStateException("Resume execution lease was lost");
        }
    }

    private AgentRun requireReadable(AgentRun run, CurrentUser currentUser) {
        if (currentUser != null && currentUser.hasRole(ADMIN_ROLE)) {
            return run;
        }
        if (currentUser != null
                && Objects.equals(run.tenantId(), currentUser.effectiveTenantId())
                && ownsRun(run, currentUser)) {
            return run;
        }
        throw new IllegalStateException(ACCESS_DENIED);
    }

    private boolean ownsRun(AgentRun run, CurrentUser currentUser) {
        return Objects.equals(run.userId(), currentUser.operator());
    }

    private void requireApprovalIdentity(AgentRun run, AgentCheckpoint checkpoint, ApprovalRequest approval) {
        requireIdentity(run.runId(), checkpoint.runId());
        requireIdentity(run.runId(), approval.runId());
        requireIdentity(checkpoint.stepId(), approval.stepId());
        requireIdentity(run.tenantId(), approval.tenantId());
        requireIdentity(run.agentId(), approval.agentId());
    }

    private void requireToolInvocationIdentity(
            AgentRun run,
            AgentCheckpoint checkpoint,
            ApprovalRequest approval,
            ToolInvocationRequest request) {
        requireIdentity(run.runId(), request.runId());
        requireIdentity(checkpoint.stepId(), request.stepId());
        requireIdentity(run.tenantId(), request.tenantId());
        requireIdentity(approval.userId(), request.userId());
        requireIdentity(run.agentId(), request.agentId());
        requireIdentity(run.versionId(), request.versionId());
        requireIdentity(approval.toolId(), request.toolId());
    }

    private void requireIdentity(String expected, String actual) {
        if (!Objects.equals(expected, actual)) {
            throw new IllegalStateException(IDENTITY_MISMATCH);
        }
    }

    private AgentRun transition(AgentRun run, AgentRunStatus status, String errorCode, String errorMessage) {
        AgentRun next = run.withStatus(
                status,
                errorCode,
                errorMessage,
                status.isFinished() ? clock.instant() : null);
        return runRepository.updateRunIfStatus(next, run.status()) ? next : loadRun(run.runId());
    }

    private AgentRun transitionWithLease(
            AgentRun run,
            AgentRunStatus status,
            String errorCode,
            String errorMessage,
            String leaseOwner) {
        AgentRun next = run.withStatus(
                status,
                errorCode,
                errorMessage,
                status.isFinished() ? clock.instant() : null);
        return runRepository.updateRunIfStatusAndResumeLeaseOwner(
                next, run.status(), leaseOwner, clock.instant()) ? next : loadRun(run.runId());
    }

    private void requireLeaseRenewal(String runId, String leaseOwner) {
        Instant now = clock.instant();
        if (!runRepository.heartbeatResumeLease(runId, leaseOwner, now.plus(resumeLeaseTtl), now)) {
            throw new IllegalStateException("Resume execution lease was lost");
        }
    }

    private <T> T callWithLeaseHeartbeat(String runId, String leaseOwner, Callable<T> operation) {
        requireLeaseRenewal(runId, leaseOwner);
        String capturedTenant = TenantContext.capture();
        Map<String, String> capturedMdc = MDC.getCopyOfContextMap();
        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        Future<T> future = executor.submit(() -> {
            String previousTenant = TenantContext.capture();
            Map<String, String> previousMdc = MDC.getCopyOfContextMap();
            TenantContext.restore(capturedTenant);
            restoreMdc(capturedMdc);
            try {
                return operation.call();
            } finally {
                TenantContext.restore(previousTenant);
                restoreMdc(previousMdc);
            }
        });
        long deadline = System.nanoTime() + resumeBlockingCallTimeout.toNanos();
        try {
            while (true) {
                long remainingNanos = deadline - System.nanoTime();
                if (remainingNanos <= 0L) {
                    future.cancel(true);
                    throw new IllegalStateException("Resume blocking call timed out");
                }
                long waitNanos = Math.min(resumeLeaseHeartbeatInterval.toNanos(), remainingNanos);
                try {
                    T result = future.get(waitNanos, TimeUnit.NANOSECONDS);
                    requireLeaseRenewal(runId, leaseOwner);
                    return result;
                } catch (TimeoutException ignored) {
                    requireLeaseRenewal(runId, leaseOwner);
                } catch (ExecutionException ex) {
                    requireLeaseRenewal(runId, leaseOwner);
                    Throwable cause = ex.getCause();
                    if (cause instanceof RuntimeException runtimeException) {
                        throw runtimeException;
                    }
                    if (cause instanceof Error error) {
                        throw error;
                    }
                    throw new IllegalStateException("Resume blocking call failed", cause);
                }
            }
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Resume blocking call was interrupted", ex);
        } finally {
            if (!future.isDone()) {
                future.cancel(true);
            }
            executor.shutdownNow();
        }
    }

    private static void restoreMdc(Map<String, String> context) {
        if (context == null || context.isEmpty()) {
            MDC.clear();
        } else {
            MDC.setContextMap(context);
        }
    }

    private static Duration requirePositiveDuration(Duration value, String name) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(name + " must be positive");
        }
        return value;
    }

    private AgentResumeDescriptor resumeDescriptor(AgentCheckpoint checkpoint) {
        JsonNode state = readTree(checkpoint.stateJson(), "stateJson");
        return AgentResumeDescriptor.from(state.path("resumeDescriptor"));
    }

    private String safeApprovalDecisionComment(ApprovalRequest approval) {
        return CredentialTextRedactor.redact(approval.decisionComment());
    }

    private String safeFailureMessage(RuntimeException error) {
        if (error == null) {
            return "Resume execution failed";
        }
        if (ModelFailureSanitizer.isModelFailure(error)) {
            return ModelFailureSanitizer.safeMessage(error);
        }
        String message = CredentialTextRedactor.redact(error.getMessage());
        return isBlank(message) ? error.getClass().getSimpleName() : message;
    }

    private List<ChatMessage> messageHistory(String messageHistoryJson) {
        if (isBlank(messageHistoryJson)) {
            return new ArrayList<>();
        }
        JsonNode root = readTree(messageHistoryJson, "messageHistoryJson");
        List<ChatMessage> messages = new ArrayList<>();
        if (!root.isArray()) {
            return messages;
        }
        for (JsonNode node : root) {
            ChatMessage message = new ChatMessage();
            message.setRole(role(node.path("role").asText(null)));
            message.setContent(node.path("content").asText(null));
            if (node.hasNonNull("toolCallId")) {
                message.setToolCallId(node.path("toolCallId").asText());
            }
            message.setToolCalls(toolCalls(node.path("toolCalls")));
            messages.add(message);
        }
        return messages;
    }

    private List<AgentToolCall> toolCalls(JsonNode toolCallsNode) {
        if (!toolCallsNode.isArray()) {
            return List.of();
        }
        List<AgentToolCall> toolCalls = new ArrayList<>();
        for (JsonNode toolCallNode : toolCallsNode) {
            String toolCallId = text(toolCallNode, "toolCallId");
            if (isBlank(toolCallId) && toolCallNode.hasNonNull("id")) {
                toolCallId = toolCallNode.path("id").asText();
            }
            toolCalls.add(AgentToolCall.of(
                    toolCallId,
                    text(toolCallNode, "toolId"),
                    objectMap(toolCallNode.path("arguments"))));
        }
        return toolCalls;
    }

    private ChatRole role(String role) {
        if (isBlank(role)) {
            return null;
        }
        return ChatRole.valueOf(role);
    }

    private Map<String, Object> objectMap(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return Map.of();
        }
        return objectMapper.convertValue(node, MAP_TYPE);
    }

    private Map<String, String> stringMap(JsonNode node) {
        Map<String, Object> source = objectMap(node);
        if (source.isEmpty()) {
            return Map.of();
        }
        LinkedHashMap<String, String> target = new LinkedHashMap<>();
        source.forEach((key, value) -> target.put(key, value == null ? null : value.toString()));
        return target;
    }

    private List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(item -> values.add(item.asText()));
        return values;
    }

    private JsonNode readTree(String json, String label) {
        try {
            return objectMapper.readTree(requireText(json, label + " must not be blank"));
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException(label + " is not valid JSON", ex);
        }
    }

    private String text(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isMissingNode() || value.isNull() ? null : value.asText();
    }

    private int nextStepNo(String runId) {
        return runRepository.listSteps(runId).size() + 1;
    }

    private String nextStepId() {
        return RESULT_ID_PREFIX + SnowflakeIds.nextIdString();
    }

    private String toJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"serializationError\":\"" + escape(safeText(ex.getMessage())) + "\"}";
        }
    }

    private void appendFailedModelStepBestEffort(
            String runId, RuntimeException failure, String leaseOwner) {
        try {
            Instant now = clock.instant();
            String inputJson = failure instanceof AgentFinalModelTurnPort.FinalModelTurnException modelFailure
                    && !modelFailure.safeEvidenceJson().isBlank()
                            ? modelFailure.safeEvidenceJson()
                            : SAFE_EVIDENCE_UNAVAILABLE_JSON;
            AgentStep step = new AgentStep(
                    nextStepId(),
                    runId,
                    nextStepNo(runId),
                    AgentStepType.MODEL_TURN,
                    AgentStepStatus.FAILED,
                    safeJsonText(inputJson),
                    null,
                    AgentRuntimeConstants.AGENT_STEP_FAILURE_CODE,
                    ModelFailureSanitizer.safeMessage(failure),
                    now,
                    now);
            if (!runRepository.appendStepIfResumeLeaseOwner(step, leaseOwner, now)) {
                throw new IllegalStateException("Resume execution lease was lost");
            }
        } catch (RuntimeException ex) {
            LOG.warn("Resume failed model-step evidence recording failed, runId={}, errorType={}",
                    runId, ex.getClass().getSimpleName());
        }
    }

    private String safeJsonText(String value) {
        if (isBlank(value)) {
            return null;
        }
        String text = value.trim();
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            return objectMapper.writeValueAsString(safeJsonValue(null, parsed));
        } catch (Exception ignored) {
            return safeText(text);
        }
    }

    private Object safeJsonValue(String key, Object value) {
        if (key != null && CredentialJsonFieldClassifier.isSensitiveOutputField(key)) {
            return CredentialTextRedactor.REDACTED_VALUE;
        }
        if (value instanceof String text) {
            return safeText(text);
        }
        if (value instanceof Map<?, ?> map) {
            LinkedHashMap<String, Object> safe = new LinkedHashMap<>();
            map.forEach((nestedKey, nestedValue) -> {
                String safeKey = nestedKey == null ? null : String.valueOf(nestedKey);
                safe.put(safeKey, safeJsonValue(safeKey, nestedValue));
            });
            return safe;
        }
        if (value instanceof List<?> list) {
            return list.stream()
                    .map(item -> safeJsonValue(null, item))
                    .toList();
        }
        return value;
    }

    private String safeText(String value) {
        return CredentialTextRedactor.redact(value);
    }

    private String requireText(String value, String message) {
        if (isBlank(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

}

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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.LoadSkillResourceToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ToolSearchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentObservation;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

final class AgentLoopToolExecutor {

    private static final int MAX_TOOL_OBSERVATION_CHARS = 8 * 1024;
    private static final String TOOL_OBSERVATION_TRUNCATED_SUFFIX = "...[truncated]";
    private static final String RAW_ARGUMENTS_KEY = "_raw";
    private static final String IDEMPOTENCY_KEY_SEPARATOR = ":";
    private static final String LEGACY_LOAD_SKILL_TOOL_ID = "load_skill";
    private static final String TRACE_TYPE_AGENT_TOOL = "AGENT_TOOL";
    private static final String TRACE_CLASS_NAME =
            "com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final KernelAgentLoopOptions options;
    private final ToolGatewayPort toolGateway;
    private final KernelRagTraceRecorder traceRecorder;
    private final AgentRunStepRecorder runStepRecorder;
    private final AgentLoopModelTurns modelTurns;

    AgentLoopToolExecutor(
            KernelAgentLoopOptions options,
            ToolGatewayPort toolGateway,
            KernelRagTraceRecorder traceRecorder,
            AgentRunStepRecorder runStepRecorder,
            AgentLoopModelTurns modelTurns) {
        this.options = Objects.requireNonNull(options, "options must not be null");
        this.toolGateway = Objects.requireNonNull(toolGateway, "toolGateway must not be null");
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder must not be null");
        this.runStepRecorder = Objects.requireNonNull(runStepRecorder, "runStepRecorder must not be null");
        this.modelTurns = Objects.requireNonNull(modelTurns, "modelTurns must not be null");
    }

    List<AgentObservation> executeTools(
            List<AgentToolCall> toolCalls,
            List<String> allowedToolIds,
            Set<String> exposedToolIds,
            AgentLoopRequest request,
            AgentRunControl control,
            TraceRunScope traceRunScope,
            TraceNodeScope stepScope,
            ToolEvents events) {
        Set<String> allowed = allowedToolIds == null || allowedToolIds.isEmpty()
                ? Set.of()
                : new HashSet<>(allowedToolIds);
        if (toolCalls.isEmpty()) {
            return List.of();
        }

        int parallelism = Math.min(options.maxParallelTools(), toolCalls.size());
        List<AgentObservation> observations = new ArrayList<>(toolCalls.size());
        for (int start = 0; start < toolCalls.size(); start += parallelism) {
            int end = Math.min(start + parallelism, toolCalls.size());
            observations.addAll(executeToolBatch(
                    toolCalls.subList(start, end),
                    allowed,
                    exposedToolIds,
                    request,
                    parallelism,
                    control,
                    traceRunScope,
                    stepScope,
                    events));
            if (Thread.currentThread().isInterrupted() && observations.size() < toolCalls.size()) {
                for (int i = observations.size(); i < toolCalls.size(); i++) {
                    observations.add(AgentObservation.failed(toolCalls.get(i).id(), "Tool execution was interrupted"));
                }
                break;
            }
        }
        return observations;
    }

    ToolInvocationRequest toolInvocationRequest(
            AgentToolCall toolCall,
            Set<String> allowedToolIds,
            AgentLoopRequest request) {
        String runId = request == null ? null : request.runId();
        return new ToolInvocationRequest(
                runId,
                toolCall.id(),
                toolCall.id(),
                request == null ? null : request.agentId(),
                request == null ? null : request.versionId(),
                request == null ? null : request.rolloutId(),
                request == null ? null : request.tenantId(),
                request == null ? null : request.userId(),
                request == null ? null : request.agentIdentityId(),
                toolCall.toolId(),
                toolArguments(toolCall, request),
                java.util.Map.of(),
                idempotencyKey(runId, toolCall.id()),
                effectiveAllowedToolIds(toolCall, allowedToolIds));
    }

    private List<AgentObservation> executeToolBatch(
            List<AgentToolCall> toolCalls,
            Set<String> allowedToolIds,
            Set<String> exposedToolIds,
            AgentLoopRequest request,
            int parallelism,
            AgentRunControl control,
            TraceRunScope traceRunScope,
            TraceNodeScope stepScope,
            ToolEvents events) {
        ExecutorService executor = Executors.newFixedThreadPool(Math.min(parallelism, toolCalls.size()));
        control.bindToolExecutor(executor);
        try {
            for (AgentToolCall toolCall : toolCalls) {
                events.toolCallStarted(request, toolCall);
            }
            List<Callable<AgentObservation>> tasks = toolCalls.stream()
                    .<Callable<AgentObservation>>map(toolCall ->
                            () -> executeToolTraced(
                                    toolCall,
                                    allowedToolIds,
                                    exposedToolIds,
                                    request,
                                    traceRunScope,
                                    stepScope))
                    .toList();
            List<Future<AgentObservation>> futures = executor.invokeAll(
                    tasks, perToolTimeoutNanos(), TimeUnit.NANOSECONDS);
            List<AgentObservation> observations = new ArrayList<>(toolCalls.size());
            for (int i = 0; i < futures.size(); i++) {
                AgentObservation observation = toObservation(toolCalls.get(i), futures.get(i));
                observations.add(observation);
                runStepRecorder.recordToolCall(request.runId(), toolCalls.get(i), observation);
                if (!isApprovalRequired(observation)) {
                    events.toolCallFinished(request, toolCalls.get(i), observation);
                }
                events.toolCallWaitingUser(request, toolCalls.get(i), observation);
                events.skillResourceLoaded(request, toolCalls.get(i), observation);
            }
            return observations;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            if (control.cancelled()) {
                throw new AgentLoopCancelledException("Agent loop cancelled", ex);
            }
            return toolCalls.stream()
                    .map(toolCall -> AgentObservation.failed(toolCall.id(), "Tool execution was interrupted"))
                    .toList();
        } finally {
            control.clearToolExecutor(executor);
            executor.shutdownNow();
        }
    }

    private long perToolTimeoutNanos() {
        try {
            return Math.max(1L, options.perToolTimeout().toNanos());
        } catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private AgentObservation toObservation(AgentToolCall toolCall, Future<AgentObservation> future) {
        if (future.isCancelled()) {
            return AgentObservation.failed(toolCall.id(), "Tool execution timed out");
        }
        try {
            return future.get();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return AgentObservation.failed(toolCall.id(), "Tool execution was interrupted");
        } catch (ExecutionException ex) {
            Throwable cause = Objects.requireNonNullElse(ex.getCause(), ex);
            return AgentObservation.failed(toolCall.id(),
                    Objects.requireNonNullElse(cause.getMessage(), cause.getClass().getName()));
        }
    }

    private AgentObservation executeTool(
            AgentToolCall toolCall,
            Set<String> allowedToolIds,
            Set<String> exposedToolIds,
            AgentLoopRequest request) {
        if (hasRawArguments(toolCall)) {
            return AgentObservation.failed(toolCall.id(), "arguments is not valid JSON");
        }
        if (LEGACY_LOAD_SKILL_TOOL_ID.equals(toolCall.toolId())) {
            return loadSkillObservation(toolCall, request);
        }
        if (exposedToolIds != null && allowedToolIds != null && allowedToolIds.contains(toolCall.toolId())
                && !exposedToolIds.contains(toolCall.toolId())) {
            return AgentObservation.failed(toolCall.id(), unavailableToolMessage(toolCall.toolId(), exposedToolIds));
        }
        try {
            ToolInvocationResult result = toolGateway.invoke(toolInvocationRequest(toolCall, allowedToolIds, request));
            return result.success()
                    ? AgentObservation.ok(toolCall.id(), truncateObservationText(result.content()))
                    : AgentObservation.failed(
                            toolCall.id(),
                            truncateObservationText(result.error()),
                            result.approvalId());
        } catch (Exception ex) {
            return AgentObservation.failed(toolCall.id(),
                    truncateObservationText(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName())));
        }
    }

    private AgentObservation executeToolTraced(
            AgentToolCall toolCall,
            Set<String> allowedToolIds,
            Set<String> exposedToolIds,
            AgentLoopRequest request,
            TraceRunScope traceRunScope,
            TraceNodeScope stepScope) {
        TraceNodeScope toolScope = traceRecorder.startNode(traceRunScope, agentToolCommand(toolCall, stepScope));
        try {
            AgentObservation observation = executeTool(toolCall, allowedToolIds, exposedToolIds, request);
            if (observation.success()) {
                traceRecorder.finishNode(toolScope);
            } else {
                traceRecorder.finishNode(toolScope, new AgentLoopException(observation.error()));
            }
            return observation;
        } catch (RuntimeException ex) {
            traceRecorder.finishNode(toolScope, ex);
            throw ex;
        }
    }

    private AgentObservation loadSkillObservation(AgentToolCall toolCall, AgentLoopRequest request) {
        String requestedName = loadSkillName(toolCall);
        if (requestedName.isBlank()) {
            return AgentObservation.failed(toolCall.id(), "skill name is required");
        }
        String resourcePath = loadSkillResourcePath(toolCall);
        if (!"SKILL.md".equals(resourcePath)) {
            return AgentObservation.failed(toolCall.id(), "skill resource is not available");
        }
        List<SkillRuntimeBlock> skills = request == null ? List.of() : request.skillRuntimeBlocks();
        return skills.stream()
                .filter(skill -> skill.name().equals(requestedName))
                .findFirst()
                .map(skill -> AgentObservation.ok(toolCall.id(), loadSkillPayload(skill)))
                .orElseGet(() -> AgentObservation.failed(toolCall.id(), "skill is not selected in this Agent version"));
    }

    private String loadSkillName(AgentToolCall toolCall) {
        Object value = toolCall.arguments().get("skillName");
        if (value == null) {
            value = toolCall.arguments().get("name");
        }
        return Objects.toString(value, "").trim();
    }

    private String loadSkillResourcePath(AgentToolCall toolCall) {
        Object value = toolCall.arguments().get("resourcePath");
        if (value == null) {
            return "SKILL.md";
        }
        return Objects.toString(value, "").trim();
    }

    private String loadSkillPayload(SkillRuntimeBlock skill) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", skill.name());
        payload.put("revisionId", skill.revisionId());
        payload.put("contentHash", skill.contentHash());
        payload.put("description", skill.description());
        payload.put("category", skill.category().name());
        payload.put("injectMode", SkillInjectMode.METADATA_AND_BODY.name());
        payload.put("content", skill.content());
        try {
            return OBJECT_MAPPER.writeValueAsString(payload);
        } catch (Exception ex) {
            return "{\"name\":\"" + escapeJson(skill.name()) + "\",\"content\":\""
                    + escapeJson(skill.content()) + "\"}";
        }
    }

    private List<String> effectiveAllowedToolIds(AgentToolCall toolCall, Set<String> allowedToolIds) {
        LinkedHashMap<String, Boolean> effective = new LinkedHashMap<>();
        if (allowedToolIds != null) {
            allowedToolIds.forEach(toolId -> effective.put(toolId, true));
        }
        if (LoadSkillResourceToolPortAdapter.TOOL_ID.equals(toolCall.toolId())) {
            effective.put(LoadSkillResourceToolPortAdapter.TOOL_ID, true);
        }
        if (ToolSearchToolPortAdapter.TOOL_ID.equals(toolCall.toolId())) {
            effective.put(ToolSearchToolPortAdapter.TOOL_ID, true);
        }
        return List.copyOf(effective.keySet());
    }

    private String idempotencyKey(String runId, String toolCallId) {
        if (runId == null || runId.isBlank()) {
            return toolCallId;
        }
        return runId + IDEMPOTENCY_KEY_SEPARATOR + toolCallId;
    }

    private boolean hasRawArguments(AgentToolCall toolCall) {
        return toolCall.arguments().size() == 1 && toolCall.arguments().containsKey(RAW_ARGUMENTS_KEY);
    }

    private java.util.Map<String, Object> toolArguments(AgentToolCall toolCall, AgentLoopRequest request) {
        LinkedHashMap<String, Object> arguments = new LinkedHashMap<>(
                Objects.requireNonNullElse(toolCall.arguments(), java.util.Map.of()));
        if (LoadSkillResourceToolPortAdapter.TOOL_ID.equals(toolCall.toolId())) {
            arguments.put(LoadSkillResourceToolPortAdapter.RUNTIME_SKILLS_ARGUMENT,
                    request == null ? List.of() : request.skillRuntimeBlocks());
        }
        if (ToolSearchToolPortAdapter.TOOL_ID.equals(toolCall.toolId())) {
            arguments.put(ToolSearchToolPortAdapter.ALLOWED_TOOL_IDS_ARGUMENT,
                    request == null ? List.of() : modelTurns.effectiveAllowedToolIds(request));
        }
        if (request == null || request.memoryContext() == null) {
            return arguments;
        }
        arguments.put("_seahorseUserId", Objects.requireNonNullElse(request.memoryContext().getUserId(), ""));
        arguments.put("_seahorseConversationId", Objects.requireNonNullElse(request.memoryContext().getConversationId(), ""));
        arguments.put("_seahorseQuestion", Objects.requireNonNullElse(request.memoryContext().getCurrentQuestion(), ""));
        return arguments;
    }

    private String truncateObservationText(String text) {
        if (text == null || text.length() <= MAX_TOOL_OBSERVATION_CHARS) {
            return text;
        }
        return text.substring(0, MAX_TOOL_OBSERVATION_CHARS) + TOOL_OBSERVATION_TRUNCATED_SUFFIX;
    }

    private String unavailableToolMessage(String toolId, Set<String> exposedToolIds) {
        String available = exposedToolIds == null || exposedToolIds.isEmpty()
                ? "none"
                : String.join(", ", exposedToolIds);
        return "Tool " + toolId
                + " is no longer available in this run. Do not call it again. Continue with available tools: "
                + available + ".";
    }

    private boolean isApprovalRequired(AgentObservation observation) {
        return observation != null
                && !observation.success()
                && ToolPolicyReasonCodes.TOOL_APPROVAL_REQUIRED.equals(observation.error());
    }

    private String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }

    private TraceNodeStartCommand agentToolCommand(AgentToolCall toolCall, TraceNodeScope stepScope) {
        String toolId = toolCall == null ? "unknown" : toolCall.toolId();
        return new TraceNodeStartCommand(
                "agent-tool-" + toolId,
                TRACE_TYPE_AGENT_TOOL,
                TRACE_CLASS_NAME,
                "executeTool",
                stepScope == null ? null : stepScope.nodeId(),
                1);
    }

    interface ToolEvents {

        void toolCallStarted(AgentLoopRequest request, AgentToolCall toolCall);

        void toolCallFinished(AgentLoopRequest request, AgentToolCall toolCall, AgentObservation observation);

        void toolCallWaitingUser(AgentLoopRequest request, AgentToolCall toolCall, AgentObservation observation);

        void skillResourceLoaded(AgentLoopRequest request, AgentToolCall toolCall, AgentObservation observation);
    }
}

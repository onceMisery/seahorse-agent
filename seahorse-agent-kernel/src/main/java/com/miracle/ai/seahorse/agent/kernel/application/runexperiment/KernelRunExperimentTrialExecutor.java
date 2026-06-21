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

package com.miracle.ai.seahorse.agent.kernel.application.runexperiment;

import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import lombok.NonNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KernelRunExperimentTrialExecutor implements RunExperimentTrialExecutorPort {

    @NonNull
    private final ReActExecutorPort executorPort;
    @NonNull
    private final ConversationBranchRepositoryPort branchRepositoryPort;
    @NonNull
    private final RunProfileRepositoryPort runProfileRepositoryPort;
    @NonNull
    private final RunContextSnapshotRepositoryPort runContextSnapshotRepositoryPort;

    public KernelRunExperimentTrialExecutor(
            ReActExecutorPort executorPort,
            ConversationBranchRepositoryPort branchRepositoryPort,
            RunProfileRepositoryPort runProfileRepositoryPort) {
        this(executorPort, branchRepositoryPort, runProfileRepositoryPort, RunContextSnapshotRepositoryPort.noop());
    }

    public KernelRunExperimentTrialExecutor(
            ReActExecutorPort executorPort,
            ConversationBranchRepositoryPort branchRepositoryPort,
            RunProfileRepositoryPort runProfileRepositoryPort,
            RunContextSnapshotRepositoryPort runContextSnapshotRepositoryPort) {
        this.executorPort = Objects.requireNonNull(executorPort, "executorPort must not be null");
        this.branchRepositoryPort = Objects.requireNonNull(branchRepositoryPort, "branchRepositoryPort must not be null");
        this.runProfileRepositoryPort = Objects.requireNonNull(
                runProfileRepositoryPort,
                "runProfileRepositoryPort must not be null");
        this.runContextSnapshotRepositoryPort = Objects.requireNonNullElseGet(
                runContextSnapshotRepositoryPort,
                RunContextSnapshotRepositoryPort::noop);
    }

    @Override
    public RunExperimentTrialExecutionResult execute(RunExperimentTrialExecutionRequest request) {
        RunExperimentTrialExecutionRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        String userId = requireText(safeRequest.getUserId(), "userId must not be blank");
        String conversationId = requireText(String.valueOf(safeRequest.getConversationId()),
                "conversationId must not be blank");
        Long baseLeafMessageId = Objects.requireNonNull(
                safeRequest.getBaseLeafMessageId(),
                "baseLeafMessageId must not be null");
        RunProfileRecord profile = runProfileRepositoryPort.findById(userId, safeRequest.getRunProfileId())
                .orElseThrow(() -> new IllegalArgumentException("run profile not found"));
        List<ConversationMessageRecord> path = resolvePath(conversationId, userId, baseLeafMessageId);
        ExperimentPrompt prompt = resolvePrompt(path);
        String runId = "run-exp-%d-trial-%d".formatted(safeRequest.getExperimentId(), safeRequest.getTrialId());
        List<String> allowedToolIds = enabledToolIds(profile.getId());
        saveRunContextSnapshot(safeRequest, profile, runId, allowedToolIds);
        AgentLoopResult result = executorPort.execute(AgentLoopRequest.builder()
                .question(requireText(prompt.question().getContent(), "base leaf message content must not be blank"))
                .history(prompt.history().stream().map(this::toChatMessage).toList())
                .executorEngine(blankToDefault(profile.getExecutorEngine(), "kernel"))
                .allowedToolIds(allowedToolIds)
                .explicitToolAllowlist(true)
                .samplingOptions(ChatSamplingOptions.builder().build())
                .runId(runId)
                .tenantId(blankToDefault(profile.getTenantId(), "default"))
                .userId(userId)
                .build());
        Long outputMessageId = appendAssistant(conversationId, userId, prompt.parentMessageId(), result.finalAnswer(), runId);
        return RunExperimentTrialExecutionResult.builder()
                .status("SUCCEEDED")
                .runId(runId)
                .outputMessageId(outputMessageId)
                .metricJson(metricJson(profile.getExecutorEngine(), result.truncated()))
                .build();
    }

    private List<ConversationMessageRecord> resolvePath(String conversationId, String userId, Long leafMessageId) {
        List<ConversationMessageRecord> all = branchRepositoryPort.listTree(conversationId, userId);
        Map<Long, ConversationMessageRecord> byId = new LinkedHashMap<>();
        for (ConversationMessageRecord record : all) {
            Long id = parseId(record.getId());
            if (id != null) {
                byId.put(id, record);
            }
        }
        ConversationMessageRecord leaf = byId.get(leafMessageId);
        if (leaf == null) {
            throw new IllegalArgumentException("base leaf message not found");
        }
        List<ConversationMessageRecord> path = new ArrayList<>();
        ConversationMessageRecord current = leaf;
        while (current != null) {
            path.add(current);
            current = current.getParentId() == null ? null : byId.get(current.getParentId());
        }
        Collections.reverse(path);
        return path;
    }

    private ExperimentPrompt resolvePrompt(List<ConversationMessageRecord> path) {
        ConversationMessageRecord leaf = path.get(path.size() - 1);
        if (!isAssistant(leaf)) {
            return new ExperimentPrompt(leaf, parseId(leaf.getId()), path.stream().limit(path.size() - 1L).toList());
        }
        for (int index = path.size() - 2; index >= 0; index--) {
            ConversationMessageRecord candidate = path.get(index);
            if (isUser(candidate)) {
                return new ExperimentPrompt(candidate, parseId(candidate.getId()), path.stream().limit(index).toList());
            }
        }
        return new ExperimentPrompt(leaf, parseId(leaf.getId()), path.stream().limit(path.size() - 1L).toList());
    }

    private boolean isAssistant(ConversationMessageRecord record) {
        return record != null && "assistant".equalsIgnoreCase(Objects.requireNonNullElse(record.getRole(), "").trim());
    }

    private boolean isUser(ConversationMessageRecord record) {
        return record != null && "user".equalsIgnoreCase(Objects.requireNonNullElse(record.getRole(), "").trim());
    }

    private ChatMessage toChatMessage(ConversationMessageRecord record) {
        ChatMessage message = new ChatMessage(toChatRole(record.getRole()), Objects.requireNonNullElse(record.getContent(), ""));
        message.setThinkingContent(record.getThinkingContent());
        message.setThinkingDuration(record.getThinkingDuration());
        return message;
    }

    private ChatRole toChatRole(String role) {
        if (role == null) {
            return ChatRole.USER;
        }
        return switch (role.trim().toLowerCase()) {
            case "system" -> ChatRole.SYSTEM;
            case "assistant" -> ChatRole.ASSISTANT;
            case "tool" -> ChatRole.TOOL;
            default -> ChatRole.USER;
        };
    }

    private Long appendAssistant(
            String conversationId,
            String userId,
            Long parentId,
            String content,
            String runId) {
        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setConversationId(conversationId);
        record.setUserId(userId);
        record.setRole("assistant");
        record.setContent(Objects.requireNonNullElse(content, ""));
        record.setParentId(parentId);
        record.setBranchRootId(parentId);
        record.setActive(1);
        record.setSiblingSeq(branchRepositoryPort.listSiblings(conversationId, userId, parentId).size());
        record.setAgentRunId(runId);
        return branchRepositoryPort.appendMessage(record);
    }

    private List<String> enabledToolIds(Long profileId) {
        if (profileId == null) {
            return List.of();
        }
        return runProfileRepositoryPort.listTools(profileId).stream()
                .filter(tool -> tool != null && Objects.equals(tool.getEnabled(), 1))
                .map(RunProfileToolBindingRecord::getToolId)
                .filter(value -> value != null && !value.isBlank())
                .distinct()
                .toList();
    }

    private void saveRunContextSnapshot(
            RunExperimentTrialExecutionRequest request,
            RunProfileRecord profile,
            String runId,
            List<String> allowedToolIds) {
        RunContextSnapshotRecord snapshot = new RunContextSnapshotRecord();
        snapshot.setTenantId(blankToDefault(profile.getTenantId(), "default"));
        snapshot.setRunId(runId);
        snapshot.setConversationId(request.getConversationId());
        snapshot.setBranchLeafMessageId(request.getBaseLeafMessageId());
        snapshot.setRoleCardId(profile.getRoleCardId());
        snapshot.setRunProfileId(profile.getId());
        snapshot.setExecutorEngine(blankToDefault(profile.getExecutorEngine(), "kernel"));
        snapshot.setExecutorConfigJson(blankToNull(profile.getExecutorConfigJson()));
        snapshot.setTraceContextJson(traceContextJson(request));
        snapshot.setSnapshotJson(snapshotJson(profile, request.getBaseLeafMessageId(), allowedToolIds));
        snapshot.setDeleted(0);
        runContextSnapshotRepositoryPort.save(snapshot);
    }

    private String traceContextJson(RunExperimentTrialExecutionRequest request) {
        return "{\"experimentId\":%d,\"trialId\":%d,\"experimentName\":\"%s\"}".formatted(
                request.getExperimentId(),
                request.getTrialId(),
                escapeJson(Objects.requireNonNullElse(request.getExperimentName(), "")));
    }

    private String snapshotJson(RunProfileRecord profile, Long baseLeafMessageId, List<String> allowedToolIds) {
        return "{\"runProfileId\":%d,\"executorEngine\":\"%s\",\"roleCardId\":%s,\"allowedToolIds\":[%s],\"baseLeafMessageId\":%d}"
                .formatted(
                        profile.getId(),
                        escapeJson(blankToDefault(profile.getExecutorEngine(), "kernel")),
                        profile.getRoleCardId() == null ? "null" : profile.getRoleCardId().toString(),
                        quotedJsonValues(allowedToolIds),
                        baseLeafMessageId);
    }

    private String quotedJsonValues(List<String> values) {
        return Objects.requireNonNullElse(values, List.<String>of()).stream()
                .map(value -> "\"" + escapeJson(value) + "\"")
                .reduce((left, right) -> left + "," + right)
                .orElse("");
    }

    private String metricJson(String executorEngine, boolean truncated) {
        return "{\"executorEngine\":\"%s\",\"truncated\":%s}".formatted(
                escapeJson(blankToDefault(executorEngine, "kernel")),
                truncated);
    }

    private Long parseId(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Long.parseLong(value.trim());
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String escapeJson(String value) {
        return Objects.requireNonNullElse(value, "").replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private record ExperimentPrompt(
            ConversationMessageRecord question,
            Long parentMessageId,
            List<ConversationMessageRecord> history) {
    }
}

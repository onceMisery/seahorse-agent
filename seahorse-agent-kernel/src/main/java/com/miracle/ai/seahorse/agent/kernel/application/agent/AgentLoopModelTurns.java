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

import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.LoadSkillResourceToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ToolSearchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ToolResultReadToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillToolPolicyMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatTokenUsage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelRequestFingerprint;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelContextWindowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

final class AgentLoopModelTurns {

    private static final String LEGACY_LOAD_SKILL_TOOL_ID = "load_skill";
    private static final ToolDescriptor LEGACY_LOAD_SKILL_DESCRIPTOR = new ToolDescriptor(
            LEGACY_LOAD_SKILL_TOOL_ID,
            "Load Skill",
            "Load SKILL.md for a skill selected in the current Agent runtime snapshot.",
            "{\"type\":\"object\",\"properties\":{\"name\":{\"type\":\"string\"}},\"required\":[\"name\"]}");
    private static final String STRUCTURED_TOOL_CALL_PROTOCOL = """
            Tool protocol: when tools are available, call them only through native structured tool calls.
            Do not write <tool_code>, </tool_code>, <tool_call>, </tool_call>, Python-style calls such as
            print('tool(...)'), or any textual tool invocation markup in assistant content.
            If no tool call is needed, answer normally.
            """.trim();

    private final StreamingChatModelPort modelPort;
    private final ToolRegistryPort toolRegistry;
    private final ContextWeaverPort contextWeaver;
    private final ToolCallParser toolCallParser;
    private final Duration modelTurnTimeout;
    private final ModelContextEnvelopeBuilder contextEnvelopeBuilder;
    private final ModelContextEnvelopeCalibrator contextEnvelopeCalibrator;
    private final ModelContextEnvelopeOptions.Mode contextEnvelopeMode;
    private final ModelContextEnvelopeOptions contextEnvelopeOptions;

    AgentLoopModelTurns(
            StreamingChatModelPort modelPort,
            ToolRegistryPort toolRegistry,
            ContextWeaverPort contextWeaver,
            ToolCallParser toolCallParser,
            Duration modelTurnTimeout,
            TokenCounterPort tokenCounter,
            ModelContextWindowPort modelContextWindow,
            ModelContextEnvelopeOptions contextEnvelopeOptions) {
        this(modelPort, toolRegistry, contextWeaver, toolCallParser, modelTurnTimeout,
                tokenCounter, modelContextWindow, contextEnvelopeOptions, null);
    }

    AgentLoopModelTurns(
            StreamingChatModelPort modelPort,
            ToolRegistryPort toolRegistry,
            ContextWeaverPort contextWeaver,
            ToolCallParser toolCallParser,
            Duration modelTurnTimeout,
            TokenCounterPort tokenCounter,
            ModelContextWindowPort modelContextWindow,
            ModelContextEnvelopeOptions contextEnvelopeOptions,
            KeyValueCachePort contextCalibrationCache) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort must not be null");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry must not be null");
        this.contextWeaver = Objects.requireNonNull(contextWeaver, "contextWeaver must not be null");
        this.toolCallParser = Objects.requireNonNull(toolCallParser, "toolCallParser must not be null");
        this.modelTurnTimeout = Objects.requireNonNull(modelTurnTimeout, "modelTurnTimeout must not be null");
        ModelContextEnvelopeOptions safeContextEnvelopeOptions = Objects.requireNonNullElseGet(
                contextEnvelopeOptions, ModelContextEnvelopeOptions::defaults);
        this.contextEnvelopeCalibrator = new ModelContextEnvelopeCalibrator(contextCalibrationCache);
        this.contextEnvelopeBuilder = new ModelContextEnvelopeBuilder(
                tokenCounter, modelContextWindow, contextWeaver, safeContextEnvelopeOptions,
                STRUCTURED_TOOL_CALL_PROTOCOL, contextEnvelopeCalibrator);
        this.contextEnvelopeMode = safeContextEnvelopeOptions.mode();
        this.contextEnvelopeOptions = safeContextEnvelopeOptions;
    }

    ModelTurn requestModelTurn(
            AgentLoopRequest request,
            List<ChatMessage> messages,
            AgentRunControl control,
            Set<String> exhaustedToolIds) {
        return requestModelTurn(
                request,
                messages,
                control,
                exposedTools(effectiveAllowedToolIds(request), request.skillRuntimeBlocks(), exhaustedToolIds),
                "auto");
    }

    ModelTurn requestFinalModelTurn(AgentLoopRequest request, List<ChatMessage> messages, AgentRunControl control) {
        return requestModelTurn(request, messages, control, List.of(), "none");
    }

    List<ToolDescriptor> exposedTools(
            List<String> allowedToolIds,
            List<SkillRuntimeBlock> skillRuntimeBlocks,
            Set<String> exhaustedToolIds) {
        List<ToolDescriptor> result = new ArrayList<>();
        List<ToolDescriptor> all = toolRegistry.listTools();
        List<String> safeAllowedToolIds = allowedToolIds == null ? List.of() : allowedToolIds;
        Set<String> allowed = new HashSet<>(safeAllowedToolIds);
        Set<String> exhausted = exhaustedToolIds == null ? Set.of() : exhaustedToolIds;
        Map<String, ToolDescriptor> descriptorsById = all.stream()
                .filter(tool -> allowed.contains(tool.toolId()))
                .filter(tool -> !exhausted.contains(tool.toolId()))
                .collect(java.util.stream.Collectors.toMap(
                        ToolDescriptor::toolId,
                        tool -> tool,
                        (left, right) -> left,
                        LinkedHashMap::new));
        result.addAll(safeAllowedToolIds.stream()
                .map(descriptorsById::get)
                .filter(Objects::nonNull)
                .toList());
        if (hasLoadableSkills(skillRuntimeBlocks)) {
            boolean registeredLoadSkill = all.stream()
                    .filter(tool -> LoadSkillResourceToolPortAdapter.TOOL_ID.equals(tool.toolId()))
                    .findFirst()
                    .map(result::add)
                    .orElse(false);
            if (!registeredLoadSkill) {
                result.add(LEGACY_LOAD_SKILL_DESCRIPTOR);
            }
        }
        if (!safeAllowedToolIds.isEmpty()) {
            all.stream()
                    .filter(tool -> ToolSearchToolPortAdapter.TOOL_ID.equals(tool.toolId()))
                    .findFirst()
                    .ifPresent(result::add);
            all.stream()
                    .filter(tool -> ToolResultReadToolPortAdapter.TOOL_ID.equals(tool.toolId()))
                    .findFirst()
                    .ifPresent(result::add);
        }
        return List.copyOf(result);
    }

    List<String> effectiveAllowedToolIds(AgentLoopRequest request) {
        if (request == null) {
            return List.of();
        }
        List<String> agentAllowedToolIds = request.allowedToolIds();
        if (request.skillToolPolicyMode() != SkillToolPolicyMode.RESTRICTIVE) {
            return agentAllowedToolIds;
        }
        List<SkillRuntimeBlock> skillRuntimeBlocks = request.skillRuntimeBlocks();
        if (skillRuntimeBlocks.isEmpty()) {
            return agentAllowedToolIds;
        }
        Set<String> skillAllowedToolIds = selectedSkillAllowedToolIds(skillRuntimeBlocks);
        return agentAllowedToolIds.stream()
                .filter(skillAllowedToolIds::contains)
                .toList();
    }

    Set<String> exposedToolIds(AgentLoopRequest request, Set<String> exhaustedToolIds) {
        return exposedTools(effectiveAllowedToolIds(request), request.skillRuntimeBlocks(), exhaustedToolIds).stream()
                .map(ToolDescriptor::toolId)
                .collect(java.util.stream.Collectors.toCollection(HashSet::new));
    }

    boolean usesLegacyContextAssembly() {
        return contextEnvelopeMode != ModelContextEnvelopeOptions.Mode.ENFORCE;
    }

    String resumeRuntimeContextSnapshot(AgentLoopRequest request) {
        return usesLegacyContextAssembly() ? null : contextEnvelopeBuilder.runtimeContextSnapshot(request);
    }

    void installLegacyRuntimeContext(AgentLoopRequest request, List<ChatMessage> messages) {
        String contextText = request.runtimeContextSnapshot() == null
                ? contextWeaver.weave(request.contextPack(), request.memoryContext(), ContextBudget.defaults())
                : request.runtimeContextSnapshot();
        if (request.skillRuntimeContext() != null && !request.skillRuntimeContext().isBlank()) {
            contextText = contextText.isBlank()
                    ? request.skillRuntimeContext().trim()
                    : contextText + System.lineSeparator() + System.lineSeparator()
                    + request.skillRuntimeContext().trim();
        }
        if (contextText.isBlank()) {
            return;
        }
        if (!messages.isEmpty() && messages.getFirst().getRole() == ChatRole.SYSTEM) {
            ChatMessage first = messages.getFirst();
            messages.set(0, ChatMessage.system(appendContextText(first.getContent(), contextText)));
        } else {
            messages.add(0, ChatMessage.system(contextText));
        }
    }

    private ModelTurn requestModelTurn(
            AgentLoopRequest request,
            List<ChatMessage> messages,
            AgentRunControl control,
            List<ToolDescriptor> tools,
            String toolChoice) {
        TurnBuffer callback = new TurnBuffer();
        AtomicReference<List<AgentToolCall>> collectedCalls = new AtomicReference<>();
        AtomicBoolean collectorInvoked = new AtomicBoolean(false);
        List<ToolDescriptor> safeTools = tools == null ? List.of() : tools;
        ChatRequest outboundRequest;
        ModelContextEnvelopeEvidence evidence = null;
        if (contextEnvelopeMode == ModelContextEnvelopeOptions.Mode.DISABLED) {
            outboundRequest = legacyRequest(request, messages, safeTools, toolChoice);
        } else if (contextEnvelopeMode == ModelContextEnvelopeOptions.Mode.OBSERVE) {
            outboundRequest = legacyRequest(request, messages, safeTools, toolChoice);
            try {
                ModelContextEnvelope observed = contextEnvelopeBuilder.observe(request, outboundRequest);
                evidence = observed.evidence();
            } catch (RuntimeException ignored) {
                evidence = observeFallbackEvidence(outboundRequest);
            }
        } else {
            ModelContextEnvelope enforced = contextEnvelopeBuilder.build(
                    request, messages, safeTools, toolChoice);
            outboundRequest = enforced.request();
            evidence = enforced.evidence();
        }
        evidence = fingerprint(outboundRequest, evidence);

        StreamCancellationHandle handle;
        try {
            handle = modelPort.streamChatWithTools(outboundRequest, callback, toolCalls -> {
                if (callback.completed()) {
                    throw new AgentLoopException("Model adapter protocol error: collector called after onComplete");
                }
                if (!collectorInvoked.compareAndSet(false, true)) {
                    throw new AgentLoopException("Tool call collector was called more than once");
                }
                collectedCalls.set(toolCalls == null ? List.of() : List.copyOf(toolCalls));
            });
        } catch (RuntimeException ex) {
            throw withEvidence(ex, evidence);
        }
        control.bindModelHandle(handle);
        try {
            callback.awaitCompletion(control, modelTurnTimeout);
        } catch (RuntimeException ex) {
            if (handle != null) {
                handle.cancel();
            }
            throw withEvidence(ex, evidence);
        } finally {
            control.clearModelHandle(handle);
        }

        ModelContextEnvelopeEvidence completedEvidence = withProviderUsage(evidence, callback.usage());
        contextEnvelopeCalibrator.record(completedEvidence, callback.usage());
        if (callback.error() != null) {
            throw withEvidence(
                    new AgentLoopException("Model streaming call failed", callback.error()), completedEvidence);
        }
        if (!collectorInvoked.get()) {
            throw withEvidence(
                    new AgentLoopException("Model adapter protocol error: collector was not called"),
                    completedEvidence);
        }
        ModelTurn turn = new ModelTurn(callback.content(), callback.thinking(),
                Objects.requireNonNullElse(collectedCalls.get(), List.of()), completedEvidence);
        return normalizeTextEncodedToolCalls(turn, safeTools);
    }

    private ModelContextEnvelopeEvidence fingerprint(
            ChatRequest request, ModelContextEnvelopeEvidence evidence) {
        if (evidence == null) {
            return null;
        }
        try {
            ModelRequestFingerprint fingerprint = modelPort.fingerprint(request);
            return fingerprint != null && fingerprint.available()
                    ? evidence.withPayloadHash(fingerprint.payloadHash(), fingerprint.source())
                    : evidence;
        } catch (RuntimeException ignored) {
            return evidence;
        }
    }

    private ModelContextEnvelopeEvidence withProviderUsage(
            ModelContextEnvelopeEvidence evidence, ChatTokenUsage usage) {
        return evidence == null || usage == null
                ? evidence
                : evidence.withProviderUsage(usage.inputTokens(), usage.outputTokens());
    }

    private ModelContextEnvelopeEvidence observeFallbackEvidence(ChatRequest request) {
        Integer requestedMaxTokens = request.getMaxTokens();
        int outputReserve = requestedMaxTokens != null && requestedMaxTokens > 0
                ? requestedMaxTokens
                : contextEnvelopeOptions.defaultOutputReserveTokens();
        return ModelContextEnvelopeEvidence.observeFallback(
                request.getModelId(),
                outputReserve,
                contextEnvelopeOptions.conservativeSafetyBufferTokens(),
                request.getMessages() == null ? 0 : request.getMessages().size(),
                request.getTools() == null ? 0 : request.getTools().size());
    }

    private ChatRequest legacyRequest(
            AgentLoopRequest request,
            List<ChatMessage> messages,
            List<ToolDescriptor> tools,
            String toolChoice) {
        List<ChatMessage> requestMessages = new ArrayList<>(
                Objects.requireNonNullElse(messages, List.of()));
        return ChatRequest.builder()
                .messages(modelRequestMessages(requestMessages, tools))
                .modelId(request.modelId())
                .samplingOptions(request.samplingOptions())
                .tools(tools)
                .toolChoice(toolChoice)
                .build();
    }

    private List<ChatMessage> modelRequestMessages(
            List<ChatMessage> messages, List<ToolDescriptor> tools) {
        if (tools == null || tools.isEmpty()) {
            return List.copyOf(messages);
        }
        List<ChatMessage> requestMessages = new ArrayList<>(messages);
        if (!requestMessages.isEmpty() && requestMessages.getFirst().getRole() == ChatRole.SYSTEM) {
            ChatMessage first = requestMessages.getFirst();
            String content = Objects.requireNonNullElse(first.getContent(), "");
            if (!content.contains(STRUCTURED_TOOL_CALL_PROTOCOL)) {
                requestMessages.set(0, ChatMessage.system(
                        appendContextText(content, STRUCTURED_TOOL_CALL_PROTOCOL)));
            }
            return List.copyOf(requestMessages);
        }
        requestMessages.add(0, ChatMessage.system(STRUCTURED_TOOL_CALL_PROTOCOL));
        return List.copyOf(requestMessages);
    }

    private String appendContextText(String systemPrompt, String contextText) {
        String safeSystemPrompt = Objects.requireNonNullElse(systemPrompt, "").trim();
        return safeSystemPrompt.isBlank()
                ? contextText
                : safeSystemPrompt + "\n\n" + contextText;
    }

    private RuntimeException withEvidence(RuntimeException error, ModelContextEnvelopeEvidence evidence) {
        if (error instanceof ModelContextEnvelopeException
                || error instanceof ModelTurnExecutionException) {
            return error;
        }
        return new ModelTurnExecutionException(error, evidence);
    }

    private ModelTurn normalizeTextEncodedToolCalls(ModelTurn turn, List<ToolDescriptor> tools) {
        if (turn == null || !turn.toolCalls().isEmpty() || tools == null || tools.isEmpty()
                || turn.content().isBlank()) {
            return turn;
        }
        Set<String> exposedToolIds = tools.stream()
                .filter(Objects::nonNull)
                .map(ToolDescriptor::toolId)
                .collect(java.util.stream.Collectors.toSet());
        ToolCallParser.Result parsed = toolCallParser.parse(turn.content(), exposedToolIds);
        if (parsed.toolCalls().isEmpty()) {
            return turn;
        }
        return new ModelTurn(parsed.content(), turn.thinking(), parsed.toolCalls(), turn.contextEvidence());
    }

    private Set<String> selectedSkillAllowedToolIds(List<SkillRuntimeBlock> skillRuntimeBlocks) {
        if (skillRuntimeBlocks == null || skillRuntimeBlocks.isEmpty()) {
            return Set.of();
        }
        Set<String> allowedToolIds = new HashSet<>();
        for (SkillRuntimeBlock skill : skillRuntimeBlocks) {
            if (skill != null) {
                allowedToolIds.addAll(skill.allowedTools());
            }
        }
        return allowedToolIds;
    }

    private boolean hasLoadableSkills(List<SkillRuntimeBlock> skills) {
        return skills != null && skills.stream().anyMatch(skill -> skill != null && !skill.content().isBlank());
    }

}

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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillToolPolicyMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;

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

    private final StreamingChatModelPort modelPort;
    private final ToolRegistryPort toolRegistry;
    private final ContextWeaverPort contextWeaver;
    private final ToolCallParser toolCallParser;
    private final Duration modelTurnTimeout;

    AgentLoopModelTurns(
            StreamingChatModelPort modelPort,
            ToolRegistryPort toolRegistry,
            ContextWeaverPort contextWeaver,
            ToolCallParser toolCallParser,
            Duration modelTurnTimeout) {
        this.modelPort = Objects.requireNonNull(modelPort, "modelPort must not be null");
        this.toolRegistry = Objects.requireNonNull(toolRegistry, "toolRegistry must not be null");
        this.contextWeaver = Objects.requireNonNull(contextWeaver, "contextWeaver must not be null");
        this.toolCallParser = Objects.requireNonNull(toolCallParser, "toolCallParser must not be null");
        this.modelTurnTimeout = Objects.requireNonNull(modelTurnTimeout, "modelTurnTimeout must not be null");
    }

    void installRuntimeContext(
            List<ChatMessage> messages,
            ContextPack contextPack,
            MemoryContext memoryContext,
            String skillRuntimeContext) {
        String contextText = contextWeaver.weave(contextPack, memoryContext, ContextBudget.defaults());
        if (skillRuntimeContext != null && !skillRuntimeContext.isBlank()) {
            contextText = contextText.isBlank()
                    ? skillRuntimeContext.trim()
                    : contextText + System.lineSeparator() + System.lineSeparator() + skillRuntimeContext.trim();
        }
        if (contextText.isBlank()) {
            return;
        }
        if (!messages.isEmpty() && messages.get(0).getRole() == ChatRole.SYSTEM) {
            ChatMessage first = messages.get(0);
            messages.set(0, ChatMessage.system(appendContextText(first.getContent(), contextText)));
            return;
        }
        messages.add(0, ChatMessage.system(contextText));
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
            boolean registeredLoadSkill = toolRegistry.find(LoadSkillResourceToolPortAdapter.TOOL_ID)
                    .flatMap(ignored -> toolRegistry.listTools().stream()
                            .filter(tool -> LoadSkillResourceToolPortAdapter.TOOL_ID.equals(tool.toolId()))
                            .findFirst())
                    .map(result::add)
                    .orElse(false);
            if (!registeredLoadSkill) {
                result.add(LEGACY_LOAD_SKILL_DESCRIPTOR);
            }
        }
        if (!safeAllowedToolIds.isEmpty()) {
            toolRegistry.find(ToolSearchToolPortAdapter.TOOL_ID)
                    .flatMap(ignored -> toolRegistry.listTools().stream()
                            .filter(tool -> ToolSearchToolPortAdapter.TOOL_ID.equals(tool.toolId()))
                            .findFirst())
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

    private ModelTurn requestModelTurn(
            AgentLoopRequest request,
            List<ChatMessage> messages,
            AgentRunControl control,
            List<ToolDescriptor> tools,
            String toolChoice) {
        TurnBuffer callback = new TurnBuffer();
        AtomicReference<List<AgentToolCall>> collectedCalls = new AtomicReference<>();
        AtomicBoolean collectorInvoked = new AtomicBoolean(false);

        StreamCancellationHandle handle = modelPort.streamChatWithTools(ChatRequest.builder()
                .messages(List.copyOf(messages))
                .modelId(request.modelId())
                .samplingOptions(request.samplingOptions())
                .tools(tools == null ? List.of() : tools)
                .toolChoice(toolChoice)
                .build(), callback, toolCalls -> {
                    if (callback.completed()) {
                        throw new AgentLoopException("Model adapter protocol error: collector called after onComplete");
                    }
                    if (!collectorInvoked.compareAndSet(false, true)) {
                        throw new AgentLoopException("Tool call collector was called more than once");
                    }
                    collectedCalls.set(toolCalls == null ? List.of() : List.copyOf(toolCalls));
                });
        control.bindModelHandle(handle);
        try {
            callback.awaitCompletion(control, modelTurnTimeout);
        } catch (RuntimeException ex) {
            if (handle != null) {
                handle.cancel();
            }
            throw ex;
        } finally {
            control.clearModelHandle(handle);
        }

        if (callback.error() != null) {
            throw new AgentLoopException("Model streaming call failed", callback.error());
        }
        if (!collectorInvoked.get()) {
            throw new AgentLoopException("Model adapter protocol error: collector was not called");
        }
        ModelTurn turn = new ModelTurn(callback.content(), callback.thinking(),
                Objects.requireNonNullElse(collectedCalls.get(), List.of()));
        return normalizeTextEncodedToolCalls(turn, tools);
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
        return new ModelTurn(parsed.content(), turn.thinking(), parsed.toolCalls());
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

    private String appendContextText(String systemPrompt, String contextText) {
        String safeSystemPrompt = Objects.requireNonNullElse(systemPrompt, "").trim();
        if (safeSystemPrompt.isBlank()) {
            return contextText;
        }
        return safeSystemPrompt + "\n\n" + contextText;
    }
}

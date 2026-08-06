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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import static com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatJsonSupport.hasText;

import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
import com.miracle.ai.seahorse.agent.kernel.application.agent.skill.SkillRuntimeComposer;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ResolvedRoleCard;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Agent 循环请求构建协作者（从 {@link KernelChatInboundService} 提取）。
 * 按 §7 收敛原则外提：只负责把聊天命令组装为 {@link AgentLoopRequest}（历史、角色卡、记忆、技能与上下文包）。
 */
final class KernelChatAgentLoopSupport {

    private static final Logger LOG = LoggerFactory.getLogger(KernelChatAgentLoopSupport.class);

    private final KernelChatModelConfigSupport modelConfigSupport;
    private final KernelChatToolSupport toolSupport;
    private final KernelChatAgentRunSupport runSupport;
    private final ContextPackRuntimeAssembler contextPackAssembler;
    private final SkillRuntimeComposer skillRuntimeComposer;
    private final KernelAgentLoopOptions agentLoopOptions;
    private final ConversationMemoryPort memoryPort;
    private final MemoryEnginePort memoryEnginePort;

    KernelChatAgentLoopSupport(KernelChatModelConfigSupport modelConfigSupport,
                               KernelChatToolSupport toolSupport,
                               KernelChatAgentRunSupport runSupport,
                               ContextPackRuntimeAssembler contextPackAssembler,
                               SkillRuntimeComposer skillRuntimeComposer,
                               KernelAgentLoopOptions agentLoopOptions,
                               ConversationMemoryPort memoryPort,
                               MemoryEnginePort memoryEnginePort) {
        this.modelConfigSupport = Objects.requireNonNull(modelConfigSupport, "modelConfigSupport must not be null");
        this.toolSupport = Objects.requireNonNull(toolSupport, "toolSupport must not be null");
        this.runSupport = Objects.requireNonNull(runSupport, "runSupport must not be null");
        this.contextPackAssembler = Objects.requireNonNull(contextPackAssembler, "contextPackAssembler must not be null");
        this.skillRuntimeComposer = Objects.requireNonNullElseGet(skillRuntimeComposer, SkillRuntimeComposer::new);
        this.agentLoopOptions = Objects.requireNonNullElseGet(agentLoopOptions, KernelAgentLoopOptions::defaults);
        this.memoryPort = Objects.requireNonNullElse(memoryPort, ConversationMemoryPort.noop());
        this.memoryEnginePort = Objects.requireNonNullElse(memoryEnginePort, MemoryEnginePort.noop());
    }

    AgentLoopRequest buildAgentLoopRequest(StreamChatCommand command, AgentRun run) {
        MemoryContext memoryContext = loadAgentMemoryContext(command);
        String runId = run == null ? null : run.runId();
        String agentId = run == null ? AgentRuntimeConstants.LEGACY_REACT_AGENT_ID : run.agentId();
        String versionId = run == null ? command.versionId() : run.versionId();
        String rolloutId = run == null ? null : run.rolloutId();
        if (run == null) {
            agentId = toolSupport.selectedAgentId(command);
            versionId = modelConfigSupport.selectedVersion(agentId, versionId).map(AgentVersion::versionId).orElse(versionId);
        }
        String tenantId = run == null ? command.tenantId() : run.tenantId();
        Optional<AgentVersion> selectedVersion = modelConfigSupport.selectedVersion(agentId, versionId);
        KernelChatModelConfigSupport.AgentModelExecutionConfig modelConfig = modelConfigSupport.effectiveModelExecutionConfig(command, agentId, versionId);
        ContextPack contextPack = contextPackAssembler.assembleForAgent(
                command.question(),
                runId,
                command.taskId(),
                agentId,
                versionId,
                tenantId,
                command.userId(),
                memoryContext,
                command.conversationId(),
                command.attachmentIds());
        List<SkillRuntimeBlock> mergedSkills = toolSupport.mergeSkills(selectedVersion, command, tenantId);
        ResolvedRoleCard roleCard = runSupport.resolveRoleCard(command.userId(), runSupport.effectiveRoleCardId(command));
        return AgentLoopRequest.builder()
                .question(command.question())
                .executorEngine(modelConfigSupport.effectiveExecutorEngine(command))
                .modelId(modelConfig.modelId())
                .history(agentHistory(command, roleCard))
                .allowedToolIds(toolSupport.allowedToolIds(command))
                .explicitToolAllowlist(toolSupport.explicitToolAllowlist(command))
                .samplingOptions(modelConfig.samplingOptions())
                .maxSteps(agentLoopOptions.maxSteps())
                .contextPack(contextPack)
                .memoryContext(memoryContext)
                .skillRuntimeContext(agentRuntimeContext(selectedVersion, mergedSkills))
                .skillRuntimeBlocks(mergedSkills)
                .runId(runId)
                .agentId(agentId)
                .versionId(versionId)
                .rolloutId(rolloutId)
                .tenantId(tenantId)
                .userId(command.userId())
                .expectedOutputArtifactType(toolSupport.expectedOutputArtifactType(command))
                .build();
    }

    private String agentRuntimeContext(Optional<AgentVersion> selectedVersion,
                                       List<SkillRuntimeBlock> mergedSkills) {
        List<String> parts = new java.util.ArrayList<>();
        selectedVersion
                .map(AgentVersion::instructions)
                .filter(KernelChatJsonSupport::hasText)
                .map(String::trim)
                .ifPresent(parts::add);
        if (mergedSkills != null && !mergedSkills.isEmpty()) {
            parts.add(skillRuntimeComposer.compose(mergedSkills));
        }
        return parts.isEmpty() ? null : String.join(System.lineSeparator() + System.lineSeparator(), parts);
    }

    private List<ChatMessage> agentHistory(StreamChatCommand command, ResolvedRoleCard roleCard) {
        List<ChatMessage> history = command.history().isEmpty() ? loadAgentHistory(command) : command.history();
        if (roleCard == null) {
            return history;
        }
        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(roleCard.higherPerm()
                ? ChatMessage.system(roleCardPrompt(roleCard))
                : ChatMessage.user(roleCardPrompt(roleCard)));
        messages.addAll(history);
        return List.copyOf(messages);
    }

    private String roleCardPrompt(ResolvedRoleCard roleCard) {
        return """
                # [ROLE DEFINITION]
                - Your name is %s.
                - Your Characteristics:
                %s
                """.formatted(
                Objects.requireNonNullElse(roleCard.name(), "").trim(),
                Objects.requireNonNullElse(roleCard.definition(), "").trim()).trim();
    }

    private MemoryContext loadAgentMemoryContext(StreamChatCommand command) {
        MemoryContext fallback = MemoryContext.builder()
                .conversationId(command.conversationId())
                .userId(command.userId())
                .currentQuestion(command.question())
                .build();
        try {
            MemoryContext loaded = memoryEnginePort.loadMemory(MemoryLoadRequest.builder()
                    .conversationId(command.conversationId())
                    .userId(command.userId())
                    .currentQuestion(command.question())
                    .knowledgeBaseIds(command.knowledgeBaseIds())
                    .build());
            if (loaded == null) {
                return fallback;
            }
            return MemoryContext.builder()
                    .conversationId(command.conversationId())
                    .userId(command.userId())
                    .currentQuestion(command.question())
                    .workingMemory(loaded.getWorkingMemory())
                    .correctionMemories(loaded.getCorrectionMemories())
                    .profileMemories(loaded.getProfileMemories())
                    .shortTermMemories(loaded.getShortTermMemories())
                    .businessDocumentMemories(loaded.getBusinessDocumentMemories())
                    .longTermMemories(loaded.getLongTermMemories())
                    .semanticMemories(loaded.getSemanticMemories())
                    .promptMessages(loaded.getPromptMessages())
                    .build();
        } catch (Exception ex) {
            LOG.warn("Agent memory activation failed, fallback to scoped empty memory: userId={}",
                    command.userId(), ex);
            return fallback;
        }
    }

    List<ChatMessage> loadAgentHistory(StreamChatCommand command) {
        if (command.assistantParentMessageId() != null) {
            return memoryPort.loadBranchPath(
                    command.conversationId(),
                    command.userId(),
                    command.assistantParentMessageId());
        }
        return memoryPort.loadAndAppend(
                command.conversationId(),
                command.userId(),
                ChatMessage.user(command.question()),
                command.branchLeafMessageId());
    }
}

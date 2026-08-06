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


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
import com.miracle.ai.seahorse.agent.kernel.application.agent.skill.SkillRuntimeComposer;
import com.miracle.ai.seahorse.agent.kernel.application.agent.skill.SkillSetJsonSupport;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ChartVisualizationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.FrontendDesignToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.GetDateTimeToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ImageGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryReadToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryWriteToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.SearchKnowledgeBaseToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ToolSearchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.WebFetchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.WebSearchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.runcontext.RunContextSnapshotRedactor;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.CredentialTextRedactor;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.output.OutputArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRuntimeConstants;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateId;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.task.TaskTemplateOutputType;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatTokenUsage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ResolvedRoleCard;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackBuilderInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.TaskTemplateQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.rolecard.RoleCardInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.UUID;

/**
 * L1 问答入站应用服务。
 */
public class KernelChatInboundService implements ChatInboundPort {

    private static final Logger LOG = LoggerFactory.getLogger(KernelChatInboundService.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final String TRACE_NAME_STREAM_CHAT = "stream-chat";
    private static final String TRACE_ENTRY_STREAM_CHAT =
            "com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatInboundService#streamChat";
    private final KernelChatPipeline chatPipeline;
    private final StreamTaskPort streamTaskPort;
    private final Optional<ReActExecutorPort> agentLoop;
    private final KernelRagTraceRecorder traceRecorder;
    private final KernelChatModelConfigSupport modelConfigSupport;
    private final KernelChatToolSupport toolSupport;
    private final KernelChatAgentRunSupport runSupport;
    private final KernelChatAgentLoopSupport loopSupport;

    /**
     * 构造器重载已折叠为 Builder：{@code KernelChatInboundService.builder().chatPipeline(...).streamTaskPort(...).build()}。
     * 可选依赖均有默认值（noop/defaults 端口），@Nullable 入参由 build() 转为 Optional.empty()。
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private KernelChatPipeline chatPipeline;
        private StreamTaskPort streamTaskPort;
        private ReActExecutorPort agentLoop;
        private KernelRagTraceRecorder traceRecorder = KernelRagTraceRecorder.noop();
        private ConversationMemoryPort memoryPort = ConversationMemoryPort.noop();
        private MemoryEnginePort memoryEnginePort = MemoryEnginePort.noop();
        private AgentRunInboundPort agentRunPort;
        private ContextPackBuilderInboundPort contextPackBuilder;
        private AgentDefinitionRepositoryPort agentDefinitionRepository;
        private ConversationAttachmentContextAssembler attachmentContextAssembler =
                ConversationAttachmentContextAssembler.noop();
        private AgentSkillRepositoryPort skillRepository;
        private KernelAgentLoopOptions agentLoopOptions = KernelAgentLoopOptions.defaults();
        private TaskTemplateQueryInboundPort taskTemplateQueryPort;
        private boolean enableSmartSkillMatching = true;
        private SkillSemanticMatcher skillSemanticMatcher;
        private RoleCardInboundPort roleCardPort;
        private CostUsageRepositoryPort costUsageRepository;
        private RunContextSnapshotRepositoryPort runContextSnapshotRepository;
        private RunProfileInboundPort runProfilePort;
        private List<AgentRunMetadataContributor> agentRunMetadataContributors = List.of();

        public Builder chatPipeline(KernelChatPipeline chatPipeline) {
            this.chatPipeline = Objects.requireNonNull(chatPipeline, "chatPipeline must not be null");
            return this;
        }

        public Builder streamTaskPort(StreamTaskPort streamTaskPort) {
            this.streamTaskPort = Objects.requireNonNull(streamTaskPort, "streamTaskPort must not be null");
            return this;
        }

        public Builder agentLoop(ReActExecutorPort agentLoop) {
            this.agentLoop = agentLoop;
            return this;
        }

        public Builder traceRecorder(KernelRagTraceRecorder traceRecorder) {
            this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
            return this;
        }

        public Builder memoryPort(ConversationMemoryPort memoryPort) {
            this.memoryPort = Objects.requireNonNullElse(memoryPort, ConversationMemoryPort.noop());
            return this;
        }

        public Builder memoryEnginePort(MemoryEnginePort memoryEnginePort) {
            this.memoryEnginePort = Objects.requireNonNullElse(memoryEnginePort, MemoryEnginePort.noop());
            return this;
        }

        public Builder agentRunPort(AgentRunInboundPort agentRunPort) {
            this.agentRunPort = agentRunPort;
            return this;
        }

        public Builder contextPackBuilder(ContextPackBuilderInboundPort contextPackBuilder) {
            this.contextPackBuilder = contextPackBuilder;
            return this;
        }

        public Builder agentDefinitionRepository(AgentDefinitionRepositoryPort agentDefinitionRepository) {
            this.agentDefinitionRepository = agentDefinitionRepository;
            return this;
        }

        public Builder attachmentContextAssembler(ConversationAttachmentContextAssembler attachmentContextAssembler) {
            this.attachmentContextAssembler = Objects.requireNonNullElseGet(
                    attachmentContextAssembler,
                    ConversationAttachmentContextAssembler::noop);
            return this;
        }

        public Builder skillRepository(AgentSkillRepositoryPort skillRepository) {
            this.skillRepository = skillRepository;
            return this;
        }

        public Builder agentLoopOptions(KernelAgentLoopOptions agentLoopOptions) {
            this.agentLoopOptions = Objects.requireNonNullElseGet(agentLoopOptions, KernelAgentLoopOptions::defaults);
            return this;
        }

        public Builder taskTemplateQueryPort(TaskTemplateQueryInboundPort taskTemplateQueryPort) {
            this.taskTemplateQueryPort = taskTemplateQueryPort;
            return this;
        }

        public Builder enableSmartSkillMatching(boolean enableSmartSkillMatching) {
            this.enableSmartSkillMatching = enableSmartSkillMatching;
            return this;
        }

        public Builder skillSemanticMatcher(SkillSemanticMatcher skillSemanticMatcher) {
            this.skillSemanticMatcher = skillSemanticMatcher;
            return this;
        }

        public Builder roleCardPort(RoleCardInboundPort roleCardPort) {
            this.roleCardPort = roleCardPort;
            return this;
        }

        public Builder costUsageRepository(CostUsageRepositoryPort costUsageRepository) {
            this.costUsageRepository = costUsageRepository;
            return this;
        }

        public Builder runContextSnapshotRepository(RunContextSnapshotRepositoryPort runContextSnapshotRepository) {
            this.runContextSnapshotRepository = runContextSnapshotRepository;
            return this;
        }

        public Builder runProfilePort(RunProfileInboundPort runProfilePort) {
            this.runProfilePort = runProfilePort;
            return this;
        }

        public Builder agentRunMetadataContributors(List<AgentRunMetadataContributor> agentRunMetadataContributors) {
            this.agentRunMetadataContributors = agentRunMetadataContributors == null
                    ? List.of()
                    : List.copyOf(agentRunMetadataContributors);
            return this;
        }

        public KernelChatInboundService build() {
            return new KernelChatInboundService(
                    chatPipeline,
                    streamTaskPort,
                    Optional.ofNullable(agentLoop),
                    traceRecorder,
                    memoryPort,
                    memoryEnginePort,
                    Optional.ofNullable(agentRunPort),
                    Optional.ofNullable(contextPackBuilder),
                    Optional.ofNullable(agentDefinitionRepository),
                    attachmentContextAssembler,
                    Optional.ofNullable(skillRepository),
                    agentLoopOptions,
                    Optional.ofNullable(taskTemplateQueryPort),
                    enableSmartSkillMatching,
                    skillSemanticMatcher,
                    Optional.ofNullable(roleCardPort),
                    Optional.ofNullable(costUsageRepository),
                    Optional.ofNullable(runContextSnapshotRepository),
                    Optional.ofNullable(runProfilePort),
                    agentRunMetadataContributors);
        }
    }

    private KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<? extends ReActExecutorPort> agentLoop,
                                    KernelRagTraceRecorder traceRecorder,
                                    ConversationMemoryPort memoryPort,
                                    MemoryEnginePort memoryEnginePort,
                                    Optional<AgentRunInboundPort> agentRunPort,
                                    Optional<ContextPackBuilderInboundPort> contextPackBuilder,
                                    Optional<AgentDefinitionRepositoryPort> agentDefinitionRepository,
                                    ConversationAttachmentContextAssembler attachmentContextAssembler,
                                    Optional<AgentSkillRepositoryPort> skillRepository,
                                    KernelAgentLoopOptions agentLoopOptions,
                                    Optional<TaskTemplateQueryInboundPort> taskTemplateQueryPort,
                                    boolean enableSmartSkillMatching,
                                    SkillSemanticMatcher skillSemanticMatcher,
                                    Optional<RoleCardInboundPort> roleCardPort,
                                    Optional<CostUsageRepositoryPort> costUsageRepository,
                                    Optional<RunContextSnapshotRepositoryPort> runContextSnapshotRepository,
                                    Optional<RunProfileInboundPort> runProfilePort,
                                    List<AgentRunMetadataContributor> agentRunMetadataContributors) {
        this.chatPipeline = Objects.requireNonNull(chatPipeline, "chatPipeline must not be null");
        this.streamTaskPort = Objects.requireNonNull(streamTaskPort, "streamTaskPort must not be null");
        this.agentLoop = agentLoop == null ? Optional.empty() : agentLoop.map(ReActExecutorPort.class::cast);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        this.modelConfigSupport = new KernelChatModelConfigSupport(
                OBJECT_MAPPER,
                runProfilePort == null ? Optional.empty() : runProfilePort,
                agentDefinitionRepository == null ? Optional.empty() : agentDefinitionRepository,
                this.agentLoop);
        ChatSelectedSkillResolver chatSkillResolver = skillRepository == null || skillRepository.isEmpty()
                ? null
                : new ChatSelectedSkillResolver(skillRepository.get());
        SkillSmartMatcher skillSmartMatcher = (enableSmartSkillMatching
                        && skillRepository != null && skillRepository.isPresent())
                ? new SkillSmartMatcher(skillRepository.get())
                : null;
        this.toolSupport = new KernelChatToolSupport(
                modelConfigSupport,
                taskTemplateQueryPort,
                agentDefinitionRepository,
                new SkillSetJsonSupport(),
                chatSkillResolver,
                skillSmartMatcher,
                skillSemanticMatcher,
                enableSmartSkillMatching);
        this.runSupport = new KernelChatAgentRunSupport(
                agentRunPort,
                runContextSnapshotRepository,
                agentRunMetadataContributors,
                costUsageRepository,
                roleCardPort,
                modelConfigSupport,
                toolSupport);
        this.loopSupport = new KernelChatAgentLoopSupport(
                modelConfigSupport,
                toolSupport,
                runSupport,
                new ContextPackRuntimeAssembler(contextPackBuilder, attachmentContextAssembler),
                new SkillRuntimeComposer(),
                agentLoopOptions,
                memoryPort,
                memoryEnginePort);
    }

    @Override
    public void streamChat(StreamChatCommand command, StreamCallback callback) {
        StreamChatCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        StreamCallback safeCallback = Objects.requireNonNull(callback, "callback must not be null");
        TraceRunScope traceRunScope = traceRecorder.startRun(new TraceRunStartCommand(
                TRACE_NAME_STREAM_CHAT,
                TRACE_ENTRY_STREAM_CHAT,
                safeCommand.conversationId(),
                safeCommand.taskId(),
                safeCommand.userId(),
                Map.of(
                        "seahorse.tenant.id", Objects.requireNonNullElse(safeCommand.tenantId(), "default"),
                        "seahorse.agent.id", toolSupport.defaultAgentId(safeCommand).orElse(
                                AgentRuntimeConstants.LEGACY_REACT_AGENT_ID),
                        "seahorse.executor.engine", modelConfigSupport.effectiveExecutorEngine(safeCommand))));
        StreamCallback errorCallback = safeCallback;
        try {
            if (safeCommand.chatMode() == ChatMode.AGENT) {
                if (agentLoop.isPresent()) {
                    toolSupport.validateAgentVersionSelection(safeCommand);
                    String metadataJson = runSupport.agentRunMetadataJson(safeCommand);
                    AgentRun run = runSupport.startAgentRun(safeCommand, traceRunScope, metadataJson);
                    if (run != null) {
                        traceRecorder.recordRunAttribute(traceRunScope, "seahorse.run.id", run.runId());
                    }
                    // Agent chat 的上下文快照由 AgentRun 持久化（one snapshot owner）；
                    // 不再由 chat service 额外保存独立 context snapshot。
                    if (run != null) {
                        safeCallback.onRunStarted(run.runId());
                    }
                    AgentLoopRequest loopRequest = loopSupport.buildAgentLoopRequest(safeCommand, run);
                    StreamCallback terminalCallback = finishTraceOnTerminal(
                            safeCallback, traceRunScope, run == null ? null : run.runId());
                    errorCallback = terminalCallback;
                    StreamCancellationHandle handle = agentLoop.get().streamExecute(
                            loopRequest,
                            runSupport.recordAgentUsageOnUsage(terminalCallback, run, loopRequest),
                            traceRunScope);
                    streamTaskPort.bindHandle(safeCommand.taskId(), handle);
                    return;
                }
                LOG.warn("chatMode=AGENT but ReActExecutorPort is not configured, fallback to RAG: taskId={}, userId={}",
                        safeCommand.taskId(), safeCommand.userId());
            }
            runSupport.saveRunContextSnapshot(safeCommand, traceRunScope);
            chatPipeline.execute(buildContext(
                    safeCommand,
                    finishTraceOnTerminal(safeCallback, traceRunScope, null),
                    traceRunScope));
        } catch (Exception ex) {
            if (errorCallback == safeCallback) {
                traceRecorder.finishRun(traceRunScope, ex);
                safeCallback.onError(ex);
                return;
            }
            errorCallback.onError(ex);
        }
    }

    @Override
    public void stopTask(String taskId) {
        streamTaskPort.cancel(taskId);
    }

    private StreamChatContext buildContext(StreamChatCommand command,
                                           StreamCallback callback,
                                           TraceRunScope traceRunScope) {
        return StreamChatContext.builder()
                .question(command.question())
                .conversationId(command.conversationId())
                .taskId(command.taskId())
                .userId(command.userId())
                .deepThinking(command.deepThinking())
                .callback(callback)
                .traceRunScope(traceRunScope)
                .attachmentIds(command.attachmentIds())
                .knowledgeBaseIds(command.knowledgeBaseIds())
                .roleCardId(runSupport.effectiveRoleCardId(command))
                .branchLeafMessageId(command.branchLeafMessageId())
                .assistantParentMessageId(command.assistantParentMessageId())
                .roleCard(runSupport.resolveRoleCard(command.userId(), runSupport.effectiveRoleCardId(command)))
                .build();
    }
    private StreamCallback finishTraceOnTerminal(StreamCallback delegate, TraceRunScope traceRunScope, String runId) {
        AtomicBoolean finished = new AtomicBoolean(false);
        return new StreamCallback() {
            @Override
            public void onContent(String content) {
                delegate.onContent(content);
            }

            @Override
            public void onThinking(String content) {
                delegate.onThinking(content);
            }

            @Override
            public void onUsage(ChatTokenUsage usage) {
                delegate.onUsage(usage);
            }

            @Override
            public void onRunStarted(String runId) {
                delegate.onRunStarted(runId);
            }

            @Override
            public void onEvent(String eventName, Object payload) {
                delegate.onEvent(eventName, payload);
            }

            @Override
            public void onComplete() {
                if (finished.compareAndSet(false, true)) {
                    try {
                        runSupport.finishRun(runId, null);
                        delegate.onComplete();
                    } finally {
                        traceRecorder.finishRun(traceRunScope);
                    }
                }
            }

            @Override
            public void onError(Throwable error) {
                if (finished.compareAndSet(false, true)) {
                    try {
                        runSupport.finishRun(runId, error);
                        delegate.onError(error);
                    } finally {
                        traceRecorder.finishRun(traceRunScope, error);
                    }
                }
            }
        };
    }
}

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
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageSource;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
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
    private static final double DEFAULT_AGENT_TEMPERATURE = 0.3D;
    private static final String MODEL_CONFIG_MODEL_ID = "modelId";
    private static final String MODEL_CONFIG_MODEL = "model";
    private static final String MODEL_CONFIG_TEMPERATURE = "temperature";
    private static final String MODEL_CONFIG_TOP_P = "topP";
    private static final String MODEL_CONFIG_TOP_K = "topK";
    private static final String MODEL_CONFIG_MAX_TOKENS = "maxTokens";
    private static final String MODEL_CONFIG_THINKING = "thinking";
    private static final String TRACE_NAME_STREAM_CHAT = "stream-chat";
    private static final String TRACE_ENTRY_STREAM_CHAT =
            "com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatInboundService#streamChat";
    private static final EnumSet<TaskTemplateId> CONTROLLED_WEB_AGENT_TEMPLATES = EnumSet.of(
            TaskTemplateId.DEEP_RESEARCH,
            TaskTemplateId.WEB_SUMMARY,
            TaskTemplateId.COMPARE_ANALYSIS);
    private static final List<String> CONTROLLED_WEB_RESEARCH_TOOL_IDS = List.of(
            WebSearchToolPortAdapter.TOOL_ID,
            WebFetchToolPortAdapter.TOOL_ID,
            SearchKnowledgeBaseToolPortAdapter.TOOL_ID,
            GetDateTimeToolPortAdapter.TOOL_ID);
    private static final List<String> LEGACY_DEFAULT_TOOL_IDS = List.of(
            SearchKnowledgeBaseToolPortAdapter.TOOL_ID,
            WebSearchToolPortAdapter.TOOL_ID,
            WebFetchToolPortAdapter.TOOL_ID,
            GetDateTimeToolPortAdapter.TOOL_ID,
            ImageGenerationToolPortAdapter.TOOL_ID,
            FrontendDesignToolPortAdapter.TOOL_ID,
            ChartVisualizationToolPortAdapter.TOOL_ID,
            MemoryReadToolPortAdapter.TOOL_ID,
            MemoryWriteToolPortAdapter.TOOL_ID,
            ToolSearchToolPortAdapter.TOOL_ID);

    private final KernelChatPipeline chatPipeline;
    private final StreamTaskPort streamTaskPort;
    private final Optional<ReActExecutorPort> agentLoop;
    private final KernelRagTraceRecorder traceRecorder;
    private final ConversationMemoryPort memoryPort;
    private final MemoryEnginePort memoryEnginePort;
    private final Optional<AgentRunInboundPort> agentRunPort;
    private final Optional<AgentDefinitionRepositoryPort> agentDefinitionRepository;
    private final ContextPackRuntimeAssembler contextPackAssembler;
    private final SkillSetJsonSupport skillSetJsonSupport;
    private final SkillRuntimeComposer skillRuntimeComposer;
    private final ChatSelectedSkillResolver chatSkillResolver;
    private final SkillSmartMatcher skillSmartMatcher;
    private final SkillSemanticMatcher skillSemanticMatcher;
    private final KernelAgentLoopOptions agentLoopOptions;
    private final Optional<TaskTemplateQueryInboundPort> taskTemplateQueryPort;
    private final Optional<RoleCardInboundPort> roleCardPort;
    private final Optional<CostUsageRepositoryPort> costUsageRepository;
    private final Optional<RunContextSnapshotRepositoryPort> runContextSnapshotRepository;
    private final Optional<RunProfileInboundPort> runProfilePort;
    private final List<AgentRunMetadataContributor> agentRunMetadataContributors;
    private final boolean enableSmartSkillMatching;

    public KernelChatInboundService(KernelChatPipeline chatPipeline, StreamTaskPort streamTaskPort) {
        this(chatPipeline, streamTaskPort, KernelRagTraceRecorder.noop());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    KernelRagTraceRecorder traceRecorder) {
        this(chatPipeline, streamTaskPort, Optional.empty(), traceRecorder, ConversationMemoryPort.noop(),
                MemoryEnginePort.noop());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<? extends ReActExecutorPort> agentLoop,
                                    KernelRagTraceRecorder traceRecorder) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, ConversationMemoryPort.noop(),
                MemoryEnginePort.noop());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<? extends ReActExecutorPort> agentLoop,
                                    KernelRagTraceRecorder traceRecorder,
                                    ConversationMemoryPort memoryPort) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, MemoryEnginePort.noop());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<? extends ReActExecutorPort> agentLoop,
                                    KernelRagTraceRecorder traceRecorder,
                                    ConversationMemoryPort memoryPort,
                                    MemoryEnginePort memoryEnginePort) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort, Optional.empty());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<? extends ReActExecutorPort> agentLoop,
                                    KernelRagTraceRecorder traceRecorder,
                                    ConversationMemoryPort memoryPort,
                                    MemoryEnginePort memoryEnginePort,
                                    Optional<AgentRunInboundPort> agentRunPort) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, Optional.empty());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<? extends ReActExecutorPort> agentLoop,
                                    KernelRagTraceRecorder traceRecorder,
                                    ConversationMemoryPort memoryPort,
                                    MemoryEnginePort memoryEnginePort,
                                    Optional<AgentRunInboundPort> agentRunPort,
                                    Optional<ContextPackBuilderInboundPort> contextPackBuilder) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, Optional.empty());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<? extends ReActExecutorPort> agentLoop,
                                    KernelRagTraceRecorder traceRecorder,
                                    ConversationMemoryPort memoryPort,
                                    MemoryEnginePort memoryEnginePort,
                                    Optional<AgentRunInboundPort> agentRunPort,
                                    Optional<ContextPackBuilderInboundPort> contextPackBuilder,
                                    Optional<AgentDefinitionRepositoryPort> agentDefinitionRepository) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository,
                ConversationAttachmentContextAssembler.noop());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<? extends ReActExecutorPort> agentLoop,
                                    KernelRagTraceRecorder traceRecorder,
                                    ConversationMemoryPort memoryPort,
                                    MemoryEnginePort memoryEnginePort,
                                    Optional<AgentRunInboundPort> agentRunPort,
                                    Optional<ContextPackBuilderInboundPort> contextPackBuilder,
                                    Optional<AgentDefinitionRepositoryPort> agentDefinitionRepository,
                                    ConversationAttachmentContextAssembler attachmentContextAssembler) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository, attachmentContextAssembler,
                Optional.empty());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<? extends ReActExecutorPort> agentLoop,
                                    KernelRagTraceRecorder traceRecorder,
                                    ConversationMemoryPort memoryPort,
                                    MemoryEnginePort memoryEnginePort,
                                    Optional<AgentRunInboundPort> agentRunPort,
                                    Optional<ContextPackBuilderInboundPort> contextPackBuilder,
                                    Optional<AgentDefinitionRepositoryPort> agentDefinitionRepository,
                                    ConversationAttachmentContextAssembler attachmentContextAssembler,
                                    Optional<AgentSkillRepositoryPort> skillRepository) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository, attachmentContextAssembler,
                skillRepository, KernelAgentLoopOptions.defaults());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
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
                                    KernelAgentLoopOptions agentLoopOptions) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository, attachmentContextAssembler,
                skillRepository, agentLoopOptions, Optional.empty());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
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
                                    Optional<TaskTemplateQueryInboundPort> taskTemplateQueryPort) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository, attachmentContextAssembler,
                skillRepository, agentLoopOptions, taskTemplateQueryPort, true);
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
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
                                    boolean enableSmartSkillMatching) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository, attachmentContextAssembler,
                skillRepository, agentLoopOptions, taskTemplateQueryPort, enableSmartSkillMatching, null);
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
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
                                    SkillSemanticMatcher skillSemanticMatcher) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository, attachmentContextAssembler,
                skillRepository, agentLoopOptions, taskTemplateQueryPort, enableSmartSkillMatching,
                skillSemanticMatcher, Optional.empty());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
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
                                    Optional<RoleCardInboundPort> roleCardPort) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository, attachmentContextAssembler,
                skillRepository, agentLoopOptions, taskTemplateQueryPort, enableSmartSkillMatching,
                skillSemanticMatcher, roleCardPort, Optional.empty());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
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
                                    Optional<CostUsageRepositoryPort> costUsageRepository) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository, attachmentContextAssembler,
                skillRepository, agentLoopOptions, taskTemplateQueryPort, enableSmartSkillMatching,
                skillSemanticMatcher, roleCardPort, costUsageRepository, Optional.empty(), List.of());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
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
                                    List<AgentRunMetadataContributor> agentRunMetadataContributors) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository, attachmentContextAssembler,
                skillRepository, agentLoopOptions, taskTemplateQueryPort, enableSmartSkillMatching,
                skillSemanticMatcher, roleCardPort, costUsageRepository, Optional.empty(),
                agentRunMetadataContributors);
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
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
                                    List<AgentRunMetadataContributor> agentRunMetadataContributors) {
        this(chatPipeline, streamTaskPort, agentLoop, traceRecorder, memoryPort, memoryEnginePort,
                agentRunPort, contextPackBuilder, agentDefinitionRepository, attachmentContextAssembler,
                skillRepository, agentLoopOptions, taskTemplateQueryPort, enableSmartSkillMatching,
                skillSemanticMatcher, roleCardPort, costUsageRepository, runContextSnapshotRepository,
                Optional.empty(), agentRunMetadataContributors);
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
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
        this.memoryPort = Objects.requireNonNullElse(memoryPort, ConversationMemoryPort.noop());
        this.memoryEnginePort = Objects.requireNonNullElse(memoryEnginePort, MemoryEnginePort.noop());
        this.agentRunPort = agentRunPort == null ? Optional.empty() : agentRunPort;
        this.agentDefinitionRepository = agentDefinitionRepository == null
                ? Optional.empty()
                : agentDefinitionRepository;
        this.contextPackAssembler = new ContextPackRuntimeAssembler(contextPackBuilder, attachmentContextAssembler);
        this.skillSetJsonSupport = new SkillSetJsonSupport();
        this.skillRuntimeComposer = new SkillRuntimeComposer();
        this.chatSkillResolver = skillRepository == null || skillRepository.isEmpty()
                ? null
                : new ChatSelectedSkillResolver(skillRepository.get());
        this.skillSmartMatcher = (enableSmartSkillMatching && skillRepository != null && skillRepository.isPresent())
                ? new SkillSmartMatcher(skillRepository.get())
                : null;
        this.skillSemanticMatcher = skillSemanticMatcher;
        this.agentLoopOptions = Objects.requireNonNullElseGet(agentLoopOptions, KernelAgentLoopOptions::defaults);
        this.taskTemplateQueryPort = taskTemplateQueryPort == null ? Optional.empty() : taskTemplateQueryPort;
        this.roleCardPort = roleCardPort == null ? Optional.empty() : roleCardPort;
        this.costUsageRepository = costUsageRepository == null ? Optional.empty() : costUsageRepository;
        this.runContextSnapshotRepository = runContextSnapshotRepository == null
                ? Optional.empty()
                : runContextSnapshotRepository;
        this.runProfilePort = runProfilePort == null ? Optional.empty() : runProfilePort;
        this.agentRunMetadataContributors = agentRunMetadataContributors == null
                ? List.of()
                : List.copyOf(agentRunMetadataContributors);
        this.enableSmartSkillMatching = enableSmartSkillMatching;
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
                safeCommand.userId()));
        StreamCallback errorCallback = safeCallback;
        try {
            if (safeCommand.chatMode() == ChatMode.AGENT) {
                if (agentLoop.isPresent()) {
                    validateAgentVersionSelection(safeCommand);
                    AgentRun run = startAgentRun(safeCommand, traceRunScope, agentRunMetadataJson(safeCommand));
                    saveRunContextSnapshot(safeCommand, run, traceRunScope);
                    if (run != null) {
                        safeCallback.onRunStarted(run.runId());
                    }
                    AgentLoopRequest loopRequest = buildAgentLoopRequest(safeCommand, run);
                    StreamCallback terminalCallback = finishTraceOnTerminal(
                            safeCallback, traceRunScope, run == null ? null : run.runId());
                    errorCallback = terminalCallback;
                    StreamCancellationHandle handle = agentLoop.get().streamExecute(
                            loopRequest,
                            recordAgentUsageOnUsage(terminalCallback, run, loopRequest),
                            traceRunScope);
                    streamTaskPort.bindHandle(safeCommand.taskId(), handle);
                    return;
                }
                LOG.warn("chatMode=AGENT but ReActExecutorPort is not configured, fallback to RAG: taskId={}, userId={}",
                        safeCommand.taskId(), safeCommand.userId());
            }
            saveRunContextSnapshot(safeCommand, traceRunScope);
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
                .roleCardId(effectiveRoleCardId(command))
                .branchLeafMessageId(command.branchLeafMessageId())
                .assistantParentMessageId(command.assistantParentMessageId())
                .roleCard(resolveRoleCard(command.userId(), effectiveRoleCardId(command)))
                .build();
    }

    private ResolvedRoleCard resolveRoleCard(String userId, Long roleCardId) {
        return roleCardPort.flatMap(port -> port.resolve(userId, roleCardId)).orElse(null);
    }

    private Long effectiveRoleCardId(StreamChatCommand command) {
        if (command.roleCardId() != null) {
            return command.roleCardId();
        }
        return runProfile(command)
                .map(RunProfileDetails::getProfile)
                .map(RunProfileRecord::getRoleCardId)
                .orElse(null);
    }

    private Optional<RunProfileDetails> runProfile(StreamChatCommand command) {
        if (command == null || runProfilePort.isEmpty()) {
            return Optional.empty();
        }
        if (command.runProfileId() != null) {
            return runProfilePort.get().findById(command.userId(), command.runProfileId());
        }
        return runProfilePort.get().findAppliedToConversation(command.userId(), command.conversationId());
    }

    private AgentLoopRequest buildAgentLoopRequest(StreamChatCommand command, AgentRun run) {
        MemoryContext memoryContext = loadAgentMemoryContext(command);
        String runId = run == null ? null : run.runId();
        String agentId = run == null ? AgentRuntimeConstants.LEGACY_REACT_AGENT_ID : run.agentId();
        String versionId = run == null ? command.versionId() : run.versionId();
        String rolloutId = run == null ? null : run.rolloutId();
        if (run == null) {
            agentId = selectedAgentId(command);
            versionId = selectedVersion(agentId, versionId).map(AgentVersion::versionId).orElse(versionId);
        }
        String tenantId = run == null ? command.tenantId() : run.tenantId();
        Optional<AgentVersion> selectedVersion = selectedVersion(agentId, versionId);
        AgentModelExecutionConfig modelConfig = effectiveModelExecutionConfig(command, agentId, versionId);
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
        List<SkillRuntimeBlock> mergedSkills = mergeSkills(selectedVersion, command, tenantId);
        ResolvedRoleCard roleCard = resolveRoleCard(command.userId(), effectiveRoleCardId(command));
        return AgentLoopRequest.builder()
                .question(command.question())
                .executorEngine(effectiveExecutorEngine(command))
                .modelId(modelConfig.modelId())
                .history(agentHistory(command, roleCard))
                .allowedToolIds(allowedToolIds(command))
                .explicitToolAllowlist(explicitToolAllowlist(command))
                .samplingOptions(modelConfig.samplingOptions())
                .maxSteps(agentLoopOptions.maxSteps())
                .contextPack(contextPack)
                .memoryContext(memoryContext)
                .skillRuntimeContext(agentRuntimeContext(selectedVersion, mergedSkills, roleCard))
                .skillRuntimeBlocks(mergedSkills)
                .runId(runId)
                .agentId(agentId)
                .versionId(versionId)
                .rolloutId(rolloutId)
                .tenantId(tenantId)
                .userId(command.userId())
                .expectedOutputArtifactType(expectedOutputArtifactType(command))
                .build();
    }

    private String agentRuntimeContext(Optional<AgentVersion> selectedVersion,
                                       List<SkillRuntimeBlock> mergedSkills,
                                       ResolvedRoleCard roleCard) {
        List<String> parts = new java.util.ArrayList<>();
        selectedVersion
                .map(AgentVersion::instructions)
                .filter(this::hasText)
                .map(String::trim)
                .ifPresent(parts::add);
        if (roleCard != null && roleCard.higherPerm()) {
            parts.add(roleCardPrompt(roleCard));
        }
        if (mergedSkills != null && !mergedSkills.isEmpty()) {
            parts.add(skillRuntimeComposer.compose(mergedSkills));
        }
        return parts.isEmpty() ? null : String.join(System.lineSeparator() + System.lineSeparator(), parts);
    }

    private List<ChatMessage> agentHistory(StreamChatCommand command, ResolvedRoleCard roleCard) {
        List<ChatMessage> history = command.history().isEmpty() ? loadAgentHistory(command) : command.history();
        if (roleCard == null || roleCard.higherPerm()) {
            return history;
        }
        List<ChatMessage> messages = new java.util.ArrayList<>();
        messages.add(ChatMessage.user(roleCardPrompt(roleCard)));
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

    private List<String> allowedToolIds(StreamChatCommand command) {
        Optional<RunProfileDetails> profile = runProfile(command);
        if (profile.isPresent()) {
            return profile.get().getToolBindings().stream()
                    .filter(Objects::nonNull)
                    .filter(binding -> binding.getEnabled() == null || binding.getEnabled() != 0)
                    .map(RunProfileToolBindingRecord::getToolId)
                    .filter(this::hasText)
                    .distinct()
                    .toList();
        }
        if (isControlledWebAgentTemplate(command)) {
            return CONTROLLED_WEB_RESEARCH_TOOL_IDS;
        }
        String agentId = selectedAgentId(command);
        String versionId = command.versionId();
        return selectedVersion(agentId, versionId)
                .map(version -> {
                    List<String> toolIds = toolIdsFromToolSetJson(version.toolSetJson());
                    return toolIds.isEmpty() ? LEGACY_DEFAULT_TOOL_IDS : toolIds;
                })
                .orElse(LEGACY_DEFAULT_TOOL_IDS);
    }

    private boolean explicitToolAllowlist(StreamChatCommand command) {
        return runProfile(command).isPresent();
    }

    private List<String> allowedToolIdsByProvider(StreamChatCommand command, String provider) {
        return runProfile(command)
                .stream()
                .flatMap(profile -> profile.getToolBindings().stream())
                .filter(Objects::nonNull)
                .filter(binding -> binding.getEnabled() == null || binding.getEnabled() != 0)
                .filter(binding -> matchesToolProvider(binding.getProvider(), provider))
                .map(RunProfileToolBindingRecord::getToolId)
                .filter(this::hasText)
                .distinct()
                .toList();
    }

    private boolean matchesToolProvider(String actualProvider, String expectedProvider) {
        if (!hasText(actualProvider) || !hasText(expectedProvider)) {
            return false;
        }
        String actual = actualProvider.trim();
        String expected = expectedProvider.trim();
        if ("MCP".equalsIgnoreCase(expected)) {
            return actual.equalsIgnoreCase("MCP")
                    || actual.toUpperCase(java.util.Locale.ROOT).startsWith("MCP_");
        }
        return actual.equalsIgnoreCase(expected);
    }

    private List<String> toolIdsFromToolSetJson(String toolSetJson) {
        if (toolSetJson == null || toolSetJson.isBlank()) {
            return List.of();
        }
        try {
            JsonNode root = OBJECT_MAPPER.readTree(toolSetJson);
            LinkedHashSet<String> toolIds = new LinkedHashSet<>();
            collectToolIds(root, toolIds);
            return List.copyOf(toolIds);
        } catch (JsonProcessingException ex) {
            LOG.warn("Agent version tool set is not valid JSON, no tools exposed: {}", toolSetJson, ex);
            return List.of();
        }
    }

    private void collectToolIds(JsonNode node, LinkedHashSet<String> toolIds) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isTextual()) {
            addToolId(node.asText(), toolIds);
            return;
        }
        if (node.isArray()) {
            for (JsonNode item : node) {
                collectToolIds(item, toolIds);
            }
            return;
        }
        if (!node.isObject()) {
            return;
        }
        addToolId(text(node, "toolId"), toolIds);
        addToolId(text(node, "tool_id"), toolIds);
        addToolId(text(node, "id"), toolIds);
        addToolId(text(node, "name"), toolIds);
        collectToolIds(node.get("tools"), toolIds);
        collectToolIds(node.get("toolIds"), toolIds);
        collectToolIds(node.get("tool_ids"), toolIds);
        collectToolIds(node.get("selectedTools"), toolIds);
    }

    private void addToolId(String toolId, LinkedHashSet<String> toolIds) {
        if (toolId != null && !toolId.isBlank()) {
            toolIds.add(toolId.trim());
        }
    }

    private OutputArtifactType expectedOutputArtifactType(StreamChatCommand command) {
        Optional<OutputArtifactType> templateType = taskTemplate(command)
                .map(TaskTemplate::defaultOutputType)
                .map(this::outputArtifactType);
        if (templateType.isPresent()) {
            return templateType.get();
        }
        return isControlledWebAgentTemplate(command) ? OutputArtifactType.MARKDOWN : null;
    }

    private OutputArtifactType outputArtifactType(TaskTemplateOutputType outputType) {
        if (outputType == null || outputType == TaskTemplateOutputType.PLAIN_TEXT) {
            return null;
        }
        return OutputArtifactType.MARKDOWN;
    }

    private boolean isControlledWebAgentTemplate(StreamChatCommand command) {
        if (!hasText(command.taskTemplateId())) {
            return false;
        }
        try {
            return CONTROLLED_WEB_AGENT_TEMPLATES.contains(TaskTemplateId.fromValue(command.taskTemplateId()));
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private AgentRun startAgentRun(StreamChatCommand command, TraceRunScope traceRunScope, String metadataJson) {
        if (agentRunPort.isEmpty()) {
            return null;
        }
        return agentRunPort.get().startRun(new AgentRunStartCommand(
                selectedAgentId(command),
                command.versionId(),
                null,
                command.tenantId(),
                command.conversationId(),
                AgentRunTriggerType.CHAT,
                inputSummary(command.question()),
                traceRunScope == null ? null : traceRunScope.traceId(),
                metadataJson,
                effectiveRunProfileId(command),
                effectiveExecutorEngine(command),
                effectiveExecutorConfig(command),
                command.currentUser()));
    }

    private void saveRunContextSnapshot(StreamChatCommand command, AgentRun run, TraceRunScope traceRunScope) {
        if (run == null || runContextSnapshotRepository.isEmpty()) {
            return;
        }
        try {
            RunContextSnapshotRecord record = new RunContextSnapshotRecord();
            record.setTenantId(run.tenantId());
            record.setRunId(run.runId());
            record.setConversationId(parseLong(command.conversationId()));
            record.setBranchLeafMessageId(command.branchLeafMessageId());
            record.setRoleCardId(effectiveRoleCardId(command));
            record.setRunProfileId(effectiveRunProfileId(command));
            record.setExecutorEngine(effectiveExecutorEngine(command));
            record.setExecutorConfigJson(effectiveExecutorConfigJson(command));
            record.setTraceContextJson(traceContextJson(traceRunScope, run));
            record.setSnapshotJson(runContextSnapshotJson(command, run, record.getExecutorEngine()));
            runContextSnapshotRepository.get().save(record);
        } catch (Exception ex) {
            LOG.warn("Failed to save run context snapshot: runId={}, conversationId={}",
                    run.runId(), command.conversationId(), ex);
        }
    }

    private void saveRunContextSnapshot(StreamChatCommand command, TraceRunScope traceRunScope) {
        if (runContextSnapshotRepository.isEmpty()) {
            return;
        }
        try {
            RunContextSnapshotRecord record = new RunContextSnapshotRecord();
            record.setTenantId(hasText(command.tenantId()) ? command.tenantId() : AgentDefinition.DEFAULT_TENANT_ID);
            record.setRunId(command.taskId());
            record.setConversationId(parseLong(command.conversationId()));
            record.setBranchLeafMessageId(command.branchLeafMessageId());
            record.setRoleCardId(effectiveRoleCardId(command));
            record.setRunProfileId(effectiveRunProfileId(command));
            record.setExecutorEngine(effectiveExecutorEngine(command));
            record.setExecutorConfigJson(effectiveExecutorConfigJson(command));
            record.setTraceContextJson(traceContextJson(traceRunScope, null));
            record.setSnapshotJson(runContextSnapshotJson(command, record.getExecutorEngine()));
            runContextSnapshotRepository.get().save(record);
        } catch (Exception ex) {
            LOG.warn("Failed to save chat run context snapshot: runId={}, conversationId={}",
                    command.taskId(), command.conversationId(), ex);
        }
    }

    private String runContextSnapshotJson(StreamChatCommand command, AgentRun run, String executorEngine)
            throws JsonProcessingException {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("conversationId", command.conversationId());
        snapshot.put("branchLeafMessageId", command.branchLeafMessageId());
        snapshot.put("assistantParentMessageId", command.assistantParentMessageId());
        snapshot.put("roleCardId", effectiveRoleCardId(command));
        snapshot.put("runProfileId", effectiveRunProfileId(command));
        snapshot.put("executorEngine", executorEngine);
        snapshot.put("agentId", run.agentId());
        snapshot.put("versionId", run.versionId());
        snapshot.put("rolloutId", run.rolloutId());
        snapshot.put("toolIds", allowedToolIds(command));
        snapshot.put("mcpToolIds", allowedToolIdsByProvider(command, "MCP"));
        snapshot.put("a2aAgentIds", allowedToolIdsByProvider(command, "A2A"));
        snapshot.put("explicitToolAllowlist", explicitToolAllowlist(command));
        snapshot.put("knowledgeBaseIds", command.knowledgeBaseIds());
        snapshot.put("modelConfig",
                modelConfigSnapshot(effectiveModelExecutionConfig(command, run.agentId(), run.versionId())));
        appendRunProfileSnapshot(snapshot, command);
        ResolvedRoleCard roleCard = resolveRoleCard(command.userId(), effectiveRoleCardId(command));
        if (roleCard != null) {
            snapshot.put("roleCard", roleCardSnapshot(roleCard));
        }
        return OBJECT_MAPPER.writeValueAsString(snapshot);
    }

    private String runContextSnapshotJson(StreamChatCommand command, String executorEngine)
            throws JsonProcessingException {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("conversationId", command.conversationId());
        snapshot.put("taskId", command.taskId());
        snapshot.put("chatMode", command.chatMode().name());
        snapshot.put("branchLeafMessageId", command.branchLeafMessageId());
        snapshot.put("assistantParentMessageId", command.assistantParentMessageId());
        snapshot.put("roleCardId", effectiveRoleCardId(command));
        snapshot.put("runProfileId", effectiveRunProfileId(command));
        snapshot.put("executorEngine", executorEngine);
        snapshot.put("toolIds", allowedToolIds(command));
        snapshot.put("mcpToolIds", allowedToolIdsByProvider(command, "MCP"));
        snapshot.put("a2aAgentIds", allowedToolIdsByProvider(command, "A2A"));
        snapshot.put("explicitToolAllowlist", explicitToolAllowlist(command));
        snapshot.put("knowledgeBaseIds", command.knowledgeBaseIds());
        snapshot.put("attachmentIds", command.attachmentIds());
        snapshot.put("selectedSkillNames", command.selectedSkillNames());
        appendRunProfileSnapshot(snapshot, command);
        ResolvedRoleCard roleCard = resolveRoleCard(command.userId(), effectiveRoleCardId(command));
        if (roleCard != null) {
            snapshot.put("roleCard", roleCardSnapshot(roleCard));
        }
        return OBJECT_MAPPER.writeValueAsString(snapshot);
    }

    private String effectiveExecutorEngine(StreamChatCommand command) {
        return runProfile(command)
                .map(RunProfileDetails::getProfile)
                .map(RunProfileRecord::getExecutorEngine)
                .filter(this::hasText)
                .orElseGet(() -> hasText(command.preferredExecutorEngine())
                        ? command.preferredExecutorEngine()
                        : command.chatMode() == ChatMode.AGENT
                                ? agentLoop.map(ReActExecutorPort::engineId).orElse("kernel")
                                : "kernel");
    }

    private Long effectiveRunProfileId(StreamChatCommand command) {
        if (command.runProfileId() != null) {
            return command.runProfileId();
        }
        return runProfile(command)
                .map(RunProfileDetails::getProfile)
                .map(RunProfileRecord::getId)
                .orElse(null);
    }

    private String effectiveExecutorConfigJson(StreamChatCommand command) {
        return runProfile(command)
                .map(RunProfileDetails::getProfile)
                .map(RunProfileRecord::getExecutorConfigJson)
                .filter(this::hasText)
                .orElse(null);
    }

    private Map<String, Object> effectiveExecutorConfig(StreamChatCommand command) {
        String json = effectiveExecutorConfigJson(command);
        if (!hasText(json)) {
            return Map.of();
        }
        try {
            return OBJECT_MAPPER.readValue(
                    json,
                    new com.fasterxml.jackson.core.type.TypeReference<LinkedHashMap<String, Object>>() {
                    });
        } catch (JsonProcessingException ex) {
            LOG.warn("Run profile executorConfig is not valid JSON, ignoring it: conversationId={}, runProfileId={}",
                    command.conversationId(), effectiveRunProfileId(command), ex);
            return Map.of();
        }
    }

    private void appendRunProfileSnapshot(Map<String, Object> snapshot, StreamChatCommand command) {
        runProfile(command).ifPresent(details -> {
            RunProfileRecord profile = details.getProfile();
            if (profile == null) {
                return;
            }
            Map<String, Object> profileSnapshot = new LinkedHashMap<>();
            profileSnapshot.put("id", profile.getId());
            profileSnapshot.put("name", profile.getName());
            profileSnapshot.put("roleCardId", profile.getRoleCardId());
            profileSnapshot.put("executorEngine", profile.getExecutorEngine());
            snapshot.put("runProfile", profileSnapshot);
            putJsonSnapshot(snapshot, "executorConfig", profile.getExecutorConfigJson());
            putJsonSnapshot(snapshot, "profileModelConfig", profile.getModelConfigJson());
            putJsonSnapshot(snapshot, "memoryScope", profile.getMemoryScopeJson());
            putJsonSnapshot(snapshot, "guardrailConfig", profile.getGuardrailConfigJson());
        });
    }

    private void putJsonSnapshot(Map<String, Object> snapshot, String key, String json) {
        if (!hasText(json)) {
            return;
        }
        try {
            snapshot.put(key, OBJECT_MAPPER.readTree(json));
        } catch (JsonProcessingException ex) {
            snapshot.put(key, json);
        }
    }

    private Map<String, Object> modelConfigSnapshot(String agentId, String versionId) {
        return modelConfigSnapshot(modelExecutionConfig(agentId, versionId));
    }

    private Map<String, Object> modelConfigSnapshot(AgentModelExecutionConfig modelConfig) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        if (modelConfig.modelId() != null) {
            snapshot.put("modelId", modelConfig.modelId());
        }
        snapshot.put("temperature", modelConfig.samplingOptions().getTemperature());
        if (modelConfig.samplingOptions().getTopP() != null) {
            snapshot.put("topP", modelConfig.samplingOptions().getTopP());
        }
        if (modelConfig.samplingOptions().getTopK() != null) {
            snapshot.put("topK", modelConfig.samplingOptions().getTopK());
        }
        if (modelConfig.samplingOptions().getMaxTokens() != null) {
            snapshot.put("maxTokens", modelConfig.samplingOptions().getMaxTokens());
        }
        if (modelConfig.samplingOptions().getThinking() != null) {
            snapshot.put("thinking", modelConfig.samplingOptions().getThinking());
        }
        return snapshot;
    }

    private Map<String, Object> roleCardSnapshot(ResolvedRoleCard roleCard) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", roleCard.roleCardId());
        snapshot.put("name", roleCard.name());
        snapshot.put("definition", roleCard.definition());
        snapshot.put("higherPerm", roleCard.higherPerm());
        return snapshot;
    }

    private String traceContextJson(TraceRunScope traceRunScope, AgentRun run) throws JsonProcessingException {
        String traceId = traceRunScope != null && hasText(traceRunScope.traceId())
                ? traceRunScope.traceId()
                : run == null ? null : run.traceId();
        if (!hasText(traceId)) {
            return null;
        }
        return OBJECT_MAPPER.writeValueAsString(Map.of("traceId", traceId));
    }

    private Long parseLong(String value) {
        if (!hasText(value)) {
            return null;
        }
        try {
            return Long.valueOf(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String agentRunMetadataJson(StreamChatCommand command) {
        try {
            String agentId = selectedAgentId(command);
            String versionId = command.versionId();
            Optional<AgentVersion> version = selectedVersion(agentId, versionId);
            Map<String, Object> metadata = new LinkedHashMap<>();
            metadata.put("engine", effectiveExecutorEngine(command));
            metadata.put("agentVersion", version
                    .map(this::agentVersionSnapshot)
                    .orElseGet(() -> {
                        Map<String, Object> fallback = new LinkedHashMap<>();
                        fallback.put("agentId", agentId);
                        fallback.put("versionId", versionId);
                        return fallback;
                    }));
            Map<String, Object> runtime = new LinkedHashMap<>();
            runtime.put("allowedToolIds", agentRunSnapshotToolIds(command, version));
            metadata.put("runtime", runtime);
            appendContributorMetadata(metadata, command);
            return OBJECT_MAPPER.writeValueAsString(metadata);
        } catch (JsonProcessingException ex) {
            LOG.warn("Failed to serialize agent run metadata snapshot: taskId={}, agentId={}",
                    command.taskId(), selectedAgentId(command), ex);
            return null;
        }
    }

    private void appendContributorMetadata(Map<String, Object> metadata, StreamChatCommand command) {
        for (AgentRunMetadataContributor contributor : agentRunMetadataContributors) {
            try {
                Map<String, Object> contributed = contributor.metadata(command);
                if (contributed == null || contributed.isEmpty()) {
                    continue;
                }
                contributed.forEach((key, value) -> {
                    if (key != null && !key.isBlank() && value != null) {
                        metadata.put(key, value);
                    }
                });
            } catch (Exception ex) {
                LOG.warn("Failed to append agent run metadata contribution: taskId={}, agentId={}",
                        command.taskId(), selectedAgentId(command), ex);
            }
        }
    }

    private Map<String, Object> agentVersionSnapshot(AgentVersion version) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("agentId", version.agentId());
        snapshot.put("versionId", version.versionId());
        snapshot.put("instructions", version.instructions());
        snapshot.put("modelConfigJson", version.modelConfigJson());
        snapshot.put("toolSetJson", version.toolSetJson());
        snapshot.put("skillSetJson", version.skillSetJson());
        return snapshot;
    }

    private List<String> agentRunSnapshotToolIds(StreamChatCommand command, Optional<AgentVersion> version) {
        List<String> toolIds = new java.util.ArrayList<>(allowedToolIds(command));
        if (version.isPresent() && !toolIds.contains("load_skill") && hasVersionBoundSkills(version.get())) {
            toolIds.add("load_skill");
        }
        return List.copyOf(toolIds);
    }

    private boolean hasVersionBoundSkills(AgentVersion version) {
        return version != null && version.skillSetJson() != null
                && !skillSetJsonSupport.fromJson(version.skillSetJson()).skills().isEmpty();
    }

    private AgentModelExecutionConfig modelExecutionConfig(String agentId, String versionId) {
        return selectedVersion(agentId, versionId)
                .map(this::modelExecutionConfig)
                .orElseGet(AgentModelExecutionConfig::defaults);
    }

    private AgentModelExecutionConfig effectiveModelExecutionConfig(
            StreamChatCommand command,
            String agentId,
            String versionId) {
        AgentModelExecutionConfig base = modelExecutionConfig(agentId, versionId);
        return runProfile(command)
                .map(RunProfileDetails::getProfile)
                .map(RunProfileRecord::getModelConfigJson)
                .filter(this::hasText)
                .map(json -> modelExecutionConfig(json, base, "run profile", command.conversationId()))
                .orElse(base);
    }

    /**
     * Merge version-bound skills with per-turn selected skills.
     * Version-bound skills take priority on name collision (published contract).
     *
     * <p>智能匹配逻辑（优先级）：
     * <ol>
     *   <li>语义匹配（SkillSemanticMatcher）：基于 Embedding 向量的深度语义理解</li>
     *   <li>规则匹配（SkillSmartMatcher）：基于关键词的规则匹配（降级方案）</li>
     * </ol>
     *
     * @throws IllegalStateException if selectedSkillNames is non-empty but resolver is unavailable
     */
    private List<SkillRuntimeBlock> mergeSkills(Optional<AgentVersion> selectedVersion,
                                                 StreamChatCommand command,
                                                 String tenantId) {
        // Version-bound skills (from published Agent version snapshot)
        List<SkillRuntimeBlock> versionBound = List.of();
        if (selectedVersion != null && selectedVersion.isPresent()) {
            versionBound = skillSetJsonSupport.fromJson(selectedVersion.get().skillSetJson()).skills();
        }
        // Per-turn selected skills (from chat input)
        boolean hasPerTurnSelection = command.selectedSkillNames() != null
                && !command.selectedSkillNames().isEmpty();
        if (hasPerTurnSelection && chatSkillResolver == null) {
            throw new IllegalStateException(
                    "selectedSkillNames provided but ChatSelectedSkillResolver is not configured "
                            + "(AgentSkillRepositoryPort bean is missing)");
        }
        List<SkillRuntimeBlock> perTurn = List.of();
        if (hasPerTurnSelection) {
            perTurn = chatSkillResolver.resolve(tenantId, command.selectedSkillNames());
        }

        // 智能匹配：当没有任何 Skill 时，尝试根据用户问题自动匹配
        if (versionBound.isEmpty() && perTurn.isEmpty() && enableSmartSkillMatching && chatSkillResolver != null) {
            List<String> recommendations = matchSkillsIntelligently(tenantId, command.question());
            if (!recommendations.isEmpty()) {
                LOG.info("Smart skill matching triggered: question='{}', recommendations={}",
                        logQuestion(command.question()), recommendations);
                perTurn = chatSkillResolver.resolve(tenantId, recommendations);
            }
        }

        if (versionBound.isEmpty() && perTurn.isEmpty()) {
            return List.of();
        }
        if (perTurn.isEmpty()) {
            return versionBound;
        }
        if (versionBound.isEmpty()) {
            return perTurn;
        }
        // Merge: version-bound takes priority on name collision
        Map<String, SkillRuntimeBlock> merged = new LinkedHashMap<>();
        for (SkillRuntimeBlock block : perTurn) {
            merged.put(block.name(), block);
        }
        for (SkillRuntimeBlock block : versionBound) {
            merged.put(block.name(), block);
        }
        return List.copyOf(merged.values());
    }

    /**
     * 智能匹配 Skill，优先使用语义匹配，降级到规则匹配。
     *
     * @param tenantId 租户 ID
     * @param question 用户问题
     * @return 推荐的 Skill 名称列表
     */
    private List<String> matchSkillsIntelligently(String tenantId, String question) {
        // 优先使用语义匹配（基于 Embedding 向量）
        if (skillSemanticMatcher != null) {
            try {
                List<String> semanticResults = skillSemanticMatcher.match(tenantId, question);
                if (!semanticResults.isEmpty()) {
                    LOG.debug("Using semantic matching results: {}", semanticResults);
                    return semanticResults;
                }
            } catch (Exception ex) {
                LOG.warn("Semantic matching failed, falling back to rule-based matching: {}", ex.getMessage());
            }
        }

        // 降级到规则匹配（基于关键词）
        if (skillSmartMatcher != null) {
            try {
                List<String> ruleResults = skillSmartMatcher.match(tenantId, question);
                LOG.debug("Using rule-based matching results: {}", ruleResults);
                return ruleResults;
            } catch (Exception ex) {
                LOG.error("Rule-based matching failed: {}", ex.getMessage(), ex);
            }
        }

        return List.of();
    }

    private AgentModelExecutionConfig modelExecutionConfig(AgentVersion version) {
        if (version == null || version.modelConfigJson() == null || version.modelConfigJson().isBlank()) {
            return AgentModelExecutionConfig.defaults();
        }
        return modelExecutionConfig(
                version.modelConfigJson(),
                AgentModelExecutionConfig.defaults(),
                "agent version",
                version.agentId() + ":" + version.versionId());
    }

    private AgentModelExecutionConfig modelExecutionConfig(
            String modelConfigJson,
            AgentModelExecutionConfig fallback,
            String source,
            String sourceId) {
        AgentModelExecutionConfig base = fallback == null ? AgentModelExecutionConfig.defaults() : fallback;
        try {
            JsonNode root = OBJECT_MAPPER.readTree(modelConfigJson);
            if (root == null || !root.isObject()) {
                return base;
            }
            ChatSamplingOptions baseSampling = base.samplingOptions();
            return new AgentModelExecutionConfig(
                    firstText(root, MODEL_CONFIG_MODEL_ID, MODEL_CONFIG_MODEL, base.modelId()),
                    ChatSamplingOptions.builder()
                            .temperature(doubleValue(root, MODEL_CONFIG_TEMPERATURE, baseSampling.getTemperature()))
                            .topP(doubleValue(root, MODEL_CONFIG_TOP_P, baseSampling.getTopP()))
                            .topK(intValue(root, MODEL_CONFIG_TOP_K, baseSampling.getTopK()))
                            .maxTokens(intValue(root, MODEL_CONFIG_MAX_TOKENS, baseSampling.getMaxTokens()))
                            .thinking(booleanValue(root, MODEL_CONFIG_THINKING, baseSampling.getThinking()))
                            .build());
        } catch (JsonProcessingException ex) {
            LOG.warn("{} model config is not valid JSON, fallback to base config: sourceId={}",
                    source, sourceId, ex);
            return base;
        }
    }

    private Optional<AgentVersion> selectedVersion(String agentId, String versionId) {
        if (agentDefinitionRepository.isEmpty() || !hasText(agentId)
                || AgentRuntimeConstants.LEGACY_REACT_AGENT_ID.equals(agentId)) {
            return Optional.empty();
        }
        AgentDefinitionRepositoryPort repository = agentDefinitionRepository.get();
        if (hasText(versionId)) {
            return Optional.of(repository.findVersion(agentId, versionId)
                    .orElseThrow(() -> new IllegalArgumentException("Agent version does not exist")));
        }
        return repository.latestVersion(agentId);
    }

    private String selectedAgentId(StreamChatCommand command) {
        if (hasText(command.agentId())) {
            return command.agentId();
        }
        return defaultAgentId(command).orElse(AgentRuntimeConstants.LEGACY_REACT_AGENT_ID);
    }

    private Optional<String> defaultAgentId(StreamChatCommand command) {
        return taskTemplate(command)
                .map(TaskTemplate::defaultAgentId)
                .filter(this::hasText);
    }

    private Optional<TaskTemplate> taskTemplate(StreamChatCommand command) {
        if (command == null || !hasText(command.taskTemplateId()) || taskTemplateQueryPort.isEmpty()) {
            return Optional.empty();
        }
        try {
            return taskTemplateQueryPort.get()
                    .findById(TaskTemplateId.fromValue(command.taskTemplateId()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }

    private void validateAgentVersionSelection(StreamChatCommand command) {
        String agentId = selectedAgentId(command);
        if (!hasText(command.versionId()) || AgentRuntimeConstants.LEGACY_REACT_AGENT_ID.equals(agentId)
                || agentDefinitionRepository.isEmpty()) {
            return;
        }
        selectedVersion(agentId, command.versionId());
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

    private List<ChatMessage> loadAgentHistory(StreamChatCommand command) {
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

    private String inputSummary(String question) {
        String value = Objects.requireNonNullElse(question, "").trim();
        if (value.length() <= 500) {
            return value;
        }
        return value.substring(0, 500);
    }

    private String logQuestion(String question) {
        String value = Objects.requireNonNullElse(question, "").trim();
        if (value.length() <= 80) {
            return value;
        }
        return value.substring(0, 80) + "...";
    }

    private String text(JsonNode root, String fieldName) {
        JsonNode value = root.get(fieldName);
        if (value == null || value.isNull() || !value.isTextual()) {
            return null;
        }
        String text = value.asText();
        return hasText(text) ? text.trim() : null;
    }

    private String firstText(JsonNode root, String primaryField, String secondaryField, String fallback) {
        String primary = text(root, primaryField);
        if (hasText(primary)) {
            return primary;
        }
        String secondary = text(root, secondaryField);
        return hasText(secondary) ? secondary : fallback;
    }

    private Double doubleValue(JsonNode root, String fieldName, Double fallback) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isNumber()) {
            return fallback;
        }
        return value.asDouble();
    }

    private Integer intValue(JsonNode root, String fieldName, Integer fallback) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isIntegralNumber()) {
            return fallback;
        }
        return value.asInt();
    }

    private Boolean booleanValue(JsonNode root, String fieldName, Boolean fallback) {
        JsonNode value = root.get(fieldName);
        if (value == null || !value.isBoolean()) {
            return fallback;
        }
        return value.asBoolean();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
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
                try {
                    finishRun(runId, null);
                    delegate.onComplete();
                } finally {
                    finishOnce(null);
                }
            }

            @Override
            public void onError(Throwable error) {
                try {
                    finishRun(runId, error);
                    delegate.onError(error);
                } finally {
                    finishOnce(error);
                }
            }

            private void finishOnce(Throwable error) {
                if (!finished.compareAndSet(false, true)) {
                    return;
                }
                if (error == null) {
                    traceRecorder.finishRun(traceRunScope);
                } else {
                    traceRecorder.finishRun(traceRunScope, error);
                }
            }
        };
    }

    private void finishRun(String runId, Throwable error) {
        if (runId == null || agentRunPort.isEmpty()) {
            return;
        }
        if (error == null) {
            agentRunPort.get().succeed(runId);
            return;
        }
        agentRunPort.get().fail(runId, AgentRuntimeConstants.DEFAULT_AGENT_RUN_FAILURE_CODE,
                Objects.requireNonNullElse(error.getMessage(), error.getClass().getName()));
    }

    private StreamCallback recordAgentUsageOnUsage(StreamCallback delegate,
                                                   AgentRun run,
                                                   AgentLoopRequest request) {
        if (costUsageRepository.isEmpty() || run == null) {
            return delegate;
        }
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
                appendAgentModelUsage(run, request, usage);
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
                delegate.onComplete();
            }

            @Override
            public void onError(Throwable error) {
                delegate.onError(error);
            }
        };
    }

    private void appendAgentModelUsage(AgentRun run, AgentLoopRequest request, ChatTokenUsage usage) {
        if (usage == null || usage.totalTokens() <= 0) {
            return;
        }
        try {
            costUsageRepository.get().append(new CostUsageRecord(
                    UUID.randomUUID().toString(),
                    hasText(run.tenantId()) ? run.tenantId() : AgentDefinition.DEFAULT_TENANT_ID,
                    run.agentId(),
                    run.runId(),
                    run.rolloutId(),
                    run.userId(),
                    null,
                    request == null ? null : request.modelId(),
                    CostUsageSource.MODEL,
                    usage.totalTokens(),
                    1L,
                    0.0D,
                    "agentscope.model",
                    Instant.now()));
        } catch (Exception ex) {
            LOG.warn("Failed to record agent model token usage: runId={}, agentId={}",
                    run.runId(), run.agentId(), ex);
        }
    }

    private record AgentModelExecutionConfig(String modelId, ChatSamplingOptions samplingOptions) {

        private AgentModelExecutionConfig {
            modelId = modelId == null || modelId.isBlank() ? null : modelId.trim();
            samplingOptions = Objects.requireNonNullElseGet(samplingOptions, () -> ChatSamplingOptions.builder()
                    .temperature(DEFAULT_AGENT_TEMPERATURE)
                    .build());
        }

        private static AgentModelExecutionConfig defaults() {
            return new AgentModelExecutionConfig(null, ChatSamplingOptions.builder()
                    .temperature(DEFAULT_AGENT_TEMPERATURE)
                    .build());
        }
    }
}

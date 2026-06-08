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

package com.miracle.ai.seahorse.agent.adapters.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.adapters.web.AdvancedFeatureGate;
import com.miracle.ai.seahorse.agent.adapters.web.ProductMode;
import com.miracle.ai.seahorse.agent.kernel.application.agent.CatalogBackedToolPolicyPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.InMemoryToolRegistry;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoopOptions;
import com.miracle.ai.seahorse.agent.kernel.application.agent.LocalToolGatewayPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.McpToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.output.DdlSafetyOutputValidator;
import com.miracle.ai.seahorse.agent.kernel.application.agent.output.JsonSchemaOutputValidator;
import com.miracle.ai.seahorse.agent.kernel.application.agent.output.OutputGovernanceService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.output.SelfHealingOutputRepairService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.AgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunWorkerService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.KernelAgentRunResumeService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.RepositoryAgentApprovalWaitHandler;
import com.miracle.ai.seahorse.agent.kernel.application.agent.runtime.RepositoryAgentRunStepRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.agent.handoff.KernelAgentHandoffService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.handoff.LocalAgentAsToolPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.AgentToolJsonSupport;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ChartVisualizationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.FrontendDesignToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.GetDateTimeToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.GitHubRepositoryReaderToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.ImageGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryForgetToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryReadToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.MemoryWriteToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.NewsletterGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.PptGenerationToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.QueryMetadataToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.SearchKnowledgeBaseToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.WebFetchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.tool.WebSearchToolPortAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.agent.web.WebFetchSafetyPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyRequest;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunResumeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunLeaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkerInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQueueRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentToolBindingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputRepairModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidationRecordPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.OutputValidatorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolApprovalRequestRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolCatalogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationAuditPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationUsagePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolArtifactPublicationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolOutputRedactionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolProviderExposurePolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolResourceAccessPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ImageGenerationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.source.GitHubRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebFetchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.web.WebSearchPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Condition;
import org.springframework.context.annotation.ConditionContext;
import org.springframework.context.annotation.Conditional;
import org.springframework.core.env.Environment;
import org.springframework.core.type.AnnotatedTypeMetadata;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.net.http.HttpClient;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * AgentLoop 鑷姩瑁呴厤銆傞粯璁ゅ叧闂紝浠呭湪鏄惧紡鎵撳紑 agent-mode-enabled 鍚庣敓鏁堛€?
 */
@AutoConfiguration
@AutoConfigureAfter({
        SeahorseAgentKernelMemoryAutoConfiguration.class,
        SeahorseAgentKernelRegistryAutoConfiguration.class,
        SeahorseAgentKernelRetrievalAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SeahorseAgentKernelAgentAutoConfiguration {

    private static final String PROP_AGENT_ENABLED = "seahorse-agent.chat.agent-mode-enabled";
    private static final String PROP_WEB_TASK_AGENT_ENABLED = "seahorse-agent.chat.web-task-agent-enabled";
    private static final String PROP_MAX_STEPS = "seahorse-agent.chat.agent.max-steps";
    private static final String PROP_PER_TOOL_TIMEOUT = "seahorse-agent.chat.agent.per-tool-timeout";
    private static final String PROP_MAX_PARALLEL_TOOLS = "seahorse-agent.chat.agent.max-parallel-tools";
    private static final String PROP_MCP_INCLUDE = "seahorse-agent.chat.agent.tools.mcp.include";
    private static final String PROP_SEARCH_TOOLS_ENABLED = "seahorse-agent.chat.agent.tools.search.enabled";
    private static final String PROP_MEMORY_TOOLS_ENABLED = "seahorse-agent.chat.agent.tools.memory.enabled";
    private static final String PROP_WEB_RESEARCH_TOOLS_ENABLED =
            "seahorse-agent.chat.agent.tools.web-research.enabled";
    private static final String PROP_WEB_FETCH_TIMEOUT =
            "seahorse-agent.chat.agent.tools.web-research.fetch-timeout";
    private static final String PROP_WEB_FETCH_MAX_BYTES =
            "seahorse-agent.chat.agent.tools.web-research.fetch-max-bytes";
    private static final String PROP_WEB_FETCH_USER_AGENT =
            "seahorse-agent.chat.agent.tools.web-research.user-agent";
    private static final String PROP_GITHUB_FETCH_TIMEOUT =
            "seahorse-agent.chat.agent.tools.github.fetch-timeout";
    private static final String PROP_GITHUB_USER_AGENT =
            "seahorse-agent.chat.agent.tools.github.user-agent";
    private static final String PROP_IMAGE_MODEL =
            "seahorse-agent.adapters.ai.image-model";
    private static final String PROP_CHAT_MODEL =
            "seahorse-agent.adapters.ai.chat-model";
    private static final String PROP_PRODUCT_MODE = "seahorse-agent.product-mode";
    private static final String PROP_ADVANCED_AGENT_HANDOFF =
            "seahorse-agent.advanced.agent-handoff-enabled";
    private static final String PROP_ADVANCED_LOCAL_AGENT =
            "seahorse-agent.advanced.local-agent-enabled";

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnMissingBean(ToolRegistryPort.class)
    public InMemoryToolRegistry seahorseAgentToolRegistryPort() {
        return new InMemoryToolRegistry();
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnMissingBean
    public KernelAgentLoopOptions seahorseKernelAgentLoopOptions(Environment environment) {
        return KernelAgentLoopOptions.builder()
                .maxSteps(environment.getProperty(PROP_MAX_STEPS, Integer.class, 6))
                .perToolTimeout(parseDuration(environment.getProperty(PROP_PER_TOOL_TIMEOUT), Duration.ofSeconds(30)))
                .maxParallelTools(environment.getProperty(PROP_MAX_PARALLEL_TOOLS, Integer.class, 1))
                .build();
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(StreamingChatModelPort.class)
    @ConditionalOnMissingBean
    public KernelAgentLoop seahorseKernelAgentLoop(StreamingChatModelPort modelPort,
                                                   ToolRegistryPort toolRegistry,
                                                   KernelAgentLoopOptions options,
                                                   ObjectProvider<KernelRagTraceRecorder> traceRecorder,
                                                   ObjectProvider<ContextWeaverPort> contextWeaverPort,
                                                   ObjectProvider<ToolGatewayPort> toolGatewayPort,
                                                   ObjectProvider<AgentRunStepRecorder> runStepRecorder,
                                                   ObjectProvider<AgentApprovalWaitHandler> approvalWaitHandler,
                                                   ObjectProvider<OutputGovernanceService> outputGovernanceService) {
        return new KernelAgentLoop(modelPort, toolRegistry,
                toolGatewayPort.getIfAvailable(() -> new LocalToolGatewayPort(toolRegistry)),
                options,
                traceRecorder.getIfAvailable(KernelRagTraceRecorder::noop),
                contextWeaverPort.getIfAvailable(DefaultContextWeaver::new),
                runStepRecorder.getIfAvailable(AgentRunStepRecorder::noop),
                approvalWaitHandler.getIfAvailable(AgentApprovalWaitHandler::noop),
                outputGovernanceService.getIfAvailable());
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnMissingBean(name = "seahorseJsonSchemaOutputValidator")
    public JsonSchemaOutputValidator seahorseJsonSchemaOutputValidator(ObjectProvider<ObjectMapper> objectMapper) {
        return new JsonSchemaOutputValidator(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnMissingBean(name = "seahorseDdlSafetyOutputValidator")
    public DdlSafetyOutputValidator seahorseDdlSafetyOutputValidator() {
        return new DdlSafetyOutputValidator();
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(OutputRepairModelPort.class)
    @ConditionalOnMissingBean
    public SelfHealingOutputRepairService seahorseSelfHealingOutputRepairService(
            OutputRepairModelPort outputRepairModelPort) {
        return new SelfHealingOutputRepairService(outputRepairModelPort);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnMissingBean
    public OutputGovernanceService seahorseOutputGovernanceService(
            ObjectProvider<OutputValidatorPort> validators,
            ObjectProvider<OutputValidationRecordPort> recordPort,
            ObjectProvider<ObservationPort> observationPort,
            ObjectProvider<SelfHealingOutputRepairService> selfHealingService) {
        List<OutputValidatorPort> validatorBeans = validators.orderedStream().toList();
        return new OutputGovernanceService(
                validatorBeans,
                recordPort.getIfAvailable(OutputValidationRecordPort::noop),
                observationPort.getIfAvailable(ObservationPort::noop),
                null,
                selfHealingService.getIfAvailable());
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean({ToolCatalogRepositoryPort.class, AgentToolBindingRepositoryPort.class})
    @ConditionalOnMissingBean
    public ToolPolicyPort seahorseCatalogBackedToolPolicyPort(
            ToolCatalogRepositoryPort toolCatalogRepositoryPort,
            AgentToolBindingRepositoryPort agentToolBindingRepositoryPort,
            ObjectProvider<ToolInvocationUsagePort> toolInvocationUsagePort,
            ObjectProvider<ToolResourceAccessPort> toolResourceAccessPort) {
        return new CatalogBackedToolPolicyPort(
                toolCatalogRepositoryPort,
                agentToolBindingRepositoryPort,
                toolInvocationUsagePort.getIfAvailable(ToolInvocationUsagePort::empty),
                ToolPolicyRequest::toolRegistered,
                toolResourceAccessPort.getIfAvailable(ToolResourceAccessPort::allowAll));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(ToolRegistryPort.class)
    @ConditionalOnMissingBean
    public ToolGatewayPort seahorseToolGatewayPort(ToolRegistryPort toolRegistry,
                                                   ObjectProvider<ToolPolicyPort> toolPolicyPort,
                                                   ObjectProvider<ToolInvocationAuditPort> toolInvocationAuditPort,
                                                   ObjectProvider<ToolApprovalRequestRepositoryPort> toolApprovalRequestRepositoryPort,
                                                   ObjectProvider<ApprovalRequestQueryPort> approvalRequestQueryPort,
                                                   ObjectProvider<ToolOutputRedactionPort> toolOutputRedactionPort,
                                                   ObjectProvider<ToolArtifactPublicationPort> toolArtifactPublicationPort,
                                                   ObjectProvider<Clock> clockProvider) {
        return new LocalToolGatewayPort(
                toolRegistry,
                toolPolicyPort.getIfAvailable(ToolPolicyPort::defaults),
                toolInvocationAuditPort.getIfAvailable(ToolInvocationAuditPort::noop),
                toolApprovalRequestRepositoryPort.getIfAvailable(ToolApprovalRequestRepositoryPort::noop),
                approvalRequestQueryPort.getIfAvailable(ApprovalRequestQueryPort::empty),
                toolOutputRedactionPort.getIfAvailable(ToolOutputRedactionPort::noop),
                toolArtifactPublicationPort.getIfAvailable(ToolArtifactPublicationPort::noop),
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean({AgentRunRepositoryPort.class, AgentCheckpointRepositoryPort.class})
    @ConditionalOnMissingBean
    public AgentApprovalWaitHandler seahorseAgentApprovalWaitHandler(
            AgentRunRepositoryPort agentRunRepositoryPort,
            AgentCheckpointRepositoryPort agentCheckpointRepositoryPort,
            ObjectProvider<Clock> clockProvider) {
        return new RepositoryAgentApprovalWaitHandler(
                agentRunRepositoryPort,
                agentCheckpointRepositoryPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean({
            AgentRunRepositoryPort.class,
            AgentCheckpointRepositoryPort.class,
            ApprovalRequestQueryPort.class,
            ToolGatewayPort.class,
            StreamingChatModelPort.class,
            CurrentUserPort.class
    })
    @ConditionalOnMissingBean(AgentRunResumeInboundPort.class)
    public KernelAgentRunResumeService seahorseAgentRunResumeInboundPort(
            AgentRunRepositoryPort agentRunRepositoryPort,
            AgentCheckpointRepositoryPort agentCheckpointRepositoryPort,
            ApprovalRequestQueryPort approvalRequestQueryPort,
            ToolGatewayPort toolGatewayPort,
            StreamingChatModelPort streamingChatModelPort,
            CurrentUserPort currentUserPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentRunResumeService(
                agentRunRepositoryPort,
                agentCheckpointRepositoryPort,
                approvalRequestQueryPort,
                toolGatewayPort,
                streamingChatModelPort,
                currentUserPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean({
            AgentRunQueueRepositoryPort.class,
            AgentRunRepositoryPort.class,
            AgentCheckpointRepositoryPort.class,
            ApprovalRequestQueryPort.class,
            AgentRunLeaseInboundPort.class,
            AgentRunResumeInboundPort.class
    })
    @ConditionalOnMissingBean(AgentRunWorkerInboundPort.class)
    public KernelAgentRunWorkerService seahorseAgentRunWorkerInboundPort(
            AgentRunQueueRepositoryPort agentRunQueueRepositoryPort,
            AgentRunRepositoryPort agentRunRepositoryPort,
            AgentCheckpointRepositoryPort agentCheckpointRepositoryPort,
            ApprovalRequestQueryPort approvalRequestQueryPort,
            AgentRunLeaseInboundPort agentRunLeaseInboundPort,
            AgentRunResumeInboundPort agentRunResumeInboundPort,
            ObjectProvider<Clock> clockProvider) {
        return new KernelAgentRunWorkerService(
                agentRunQueueRepositoryPort,
                agentRunRepositoryPort,
                agentCheckpointRepositoryPort,
                approvalRequestQueryPort,
                agentRunLeaseInboundPort,
                agentRunResumeInboundPort,
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnMissingBean
    public AgentRunStepRecorder seahorseAgentRunStepRecorder(
            ObjectProvider<AgentRunRepositoryPort> agentRunRepositoryPort,
            ObjectProvider<Clock> clockProvider) {
        AgentRunRepositoryPort repository = agentRunRepositoryPort.getIfAvailable();
        if (repository == null) {
            return AgentRunStepRecorder.noop();
        }
        return new RepositoryAgentRunStepRecorder(repository, clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnMissingBean
    public AgentToolJsonSupport seahorseAgentToolJsonSupport(ObjectProvider<ObjectMapper> objectMapper) {
        return new AgentToolJsonSupport(objectMapper.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(KernelRetrievalEngine.class)
    @ConditionalOnProperty(name = PROP_SEARCH_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SearchKnowledgeBaseToolPortAdapter seahorseSearchKnowledgeBaseToolPortAdapter(
            KernelRetrievalEngine retrievalEngine,
            AgentToolJsonSupport jsonSupport) {
        return new SearchKnowledgeBaseToolPortAdapter(retrievalEngine, jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnProperty(name = PROP_SEARCH_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public QueryMetadataToolPortAdapter seahorseQueryMetadataToolPortAdapter(AgentToolJsonSupport jsonSupport) {
        return new QueryMetadataToolPortAdapter(jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(MemoryEnginePort.class)
    @ConditionalOnProperty(name = PROP_MEMORY_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MemoryReadToolPortAdapter seahorseMemoryReadToolPortAdapter(MemoryEnginePort memoryEnginePort,
                                                                       AgentToolJsonSupport jsonSupport) {
        return new MemoryReadToolPortAdapter(memoryEnginePort, jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(MemoryEnginePort.class)
    @ConditionalOnProperty(name = PROP_MEMORY_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MemoryWriteToolPortAdapter seahorseMemoryWriteToolPortAdapter(MemoryEnginePort memoryEnginePort,
                                                                         ObjectProvider<MemoryIngestionWorkflowPort> memoryIngestionWorkflowPort,
                                                                         ObjectProvider<MemoryGovernanceInboundPort> memoryGovernancePort,
                                                                         AgentToolJsonSupport jsonSupport) {
        return new MemoryWriteToolPortAdapter(
                memoryEnginePort,
                memoryIngestionWorkflowPort.getIfAvailable(),
                memoryGovernancePort.getIfAvailable(),
                jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(MemoryManagementInboundPort.class)
    @ConditionalOnProperty(name = PROP_MEMORY_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public MemoryForgetToolPortAdapter seahorseMemoryForgetToolPortAdapter(
            MemoryManagementInboundPort memoryManagementPort,
            AgentToolJsonSupport jsonSupport) {
        return new MemoryForgetToolPortAdapter(memoryManagementPort, jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnMissingBean
    public GetDateTimeToolPortAdapter seahorseGetDateTimeToolPortAdapter() {
        return new GetDateTimeToolPortAdapter();
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnProperty(name = PROP_WEB_RESEARCH_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public WebFetchSafetyPolicy seahorseWebFetchSafetyPolicy() {
        return new WebFetchSafetyPolicy();
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnProperty(name = PROP_WEB_RESEARCH_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(WebFetchPort.class)
    public JdkHttpWebFetchPortAdapter seahorseWebFetchPort(ObjectProvider<HttpClient> httpClient,
                                                           WebFetchSafetyPolicy safetyPolicy,
                                                           Environment environment) {
        return new JdkHttpWebFetchPortAdapter(
                httpClient.getIfAvailable(),
                safetyPolicy,
                parseDuration(environment.getProperty(PROP_WEB_FETCH_TIMEOUT), Duration.ofSeconds(10)),
                environment.getProperty(PROP_WEB_FETCH_MAX_BYTES, Integer.class, 512 * 1024),
                environment.getProperty(PROP_WEB_FETCH_USER_AGENT));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(WebFetchPort.class)
    @ConditionalOnProperty(name = PROP_WEB_RESEARCH_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public WebFetchToolPortAdapter seahorseWebFetchToolPortAdapter(WebFetchPort webFetchPort,
                                                                   WebFetchSafetyPolicy safetyPolicy,
                                                                   AgentToolJsonSupport jsonSupport) {
        return new WebFetchToolPortAdapter(webFetchPort, safetyPolicy, jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnProperty(name = PROP_WEB_RESEARCH_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(GitHubRepositoryPort.class)
    public JdkHttpGitHubRepositoryPortAdapter seahorseGitHubRepositoryPort(
            ObjectProvider<HttpClient> httpClient,
            ObjectProvider<ObjectMapper> objectMapper,
            ObjectProvider<Clock> clockProvider,
            Environment environment) {
        return new JdkHttpGitHubRepositoryPortAdapter(
                httpClient.getIfAvailable(),
                objectMapper.getIfAvailable(ObjectMapper::new),
                parseDuration(environment.getProperty(PROP_GITHUB_FETCH_TIMEOUT), Duration.ofSeconds(15)),
                environment.getProperty(PROP_GITHUB_USER_AGENT),
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(GitHubRepositoryPort.class)
    @ConditionalOnProperty(name = PROP_WEB_RESEARCH_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public GitHubRepositoryReaderToolPortAdapter seahorseGitHubRepositoryReaderToolPortAdapter(
            GitHubRepositoryPort gitHubRepositoryPort,
            AgentToolJsonSupport jsonSupport) {
        return new GitHubRepositoryReaderToolPortAdapter(gitHubRepositoryPort, jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(ImageGenerationPort.class)
    @ConditionalOnMissingBean
    public ImageGenerationToolPortAdapter seahorseImageGenerationToolPortAdapter(
            ImageGenerationPort imageGenerationPort,
            AgentToolJsonSupport jsonSupport,
            Environment environment) {
        return new ImageGenerationToolPortAdapter(
                imageGenerationPort,
                environment.getProperty(PROP_IMAGE_MODEL, ""),
                jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnMissingBean
    public NewsletterGenerationToolPortAdapter seahorseNewsletterGenerationToolPortAdapter(
            ChatModelPort chatModelPort,
            AgentToolJsonSupport jsonSupport,
            Environment environment) {
        return new NewsletterGenerationToolPortAdapter(
                chatModelPort,
                environment.getProperty(PROP_CHAT_MODEL, ""),
                jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnMissingBean
    public PptGenerationToolPortAdapter seahorsePptGenerationToolPortAdapter(
            ChatModelPort chatModelPort,
            AgentToolJsonSupport jsonSupport,
            Environment environment) {
        return new PptGenerationToolPortAdapter(
                chatModelPort,
                environment.getProperty(PROP_CHAT_MODEL, ""),
                jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnMissingBean
    public ChartVisualizationToolPortAdapter seahorseChartVisualizationToolPortAdapter(
            ChatModelPort chatModelPort,
            AgentToolJsonSupport jsonSupport,
            Environment environment) {
        return new ChartVisualizationToolPortAdapter(
                chatModelPort,
                environment.getProperty(PROP_CHAT_MODEL, ""),
                jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnMissingBean
    public FrontendDesignToolPortAdapter seahorseFrontendDesignToolPortAdapter(
            ChatModelPort chatModelPort,
            AgentToolJsonSupport jsonSupport,
            Environment environment) {
        return new FrontendDesignToolPortAdapter(
                chatModelPort,
                environment.getProperty(PROP_CHAT_MODEL, ""),
                jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(WebSearchPort.class)
    @ConditionalOnProperty(name = PROP_WEB_RESEARCH_TOOLS_ENABLED, havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public WebSearchToolPortAdapter seahorseWebSearchToolPortAdapter(WebSearchPort webSearchPort,
                                                                     AgentToolJsonSupport jsonSupport) {
        return new WebSearchToolPortAdapter(webSearchPort, jsonSupport);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @Conditional(AdvancedLocalAgentToolEnabledCondition.class)
    @ConditionalOnBean(KernelAgentHandoffService.class)
    @ConditionalOnMissingBean
    public LocalAgentAsToolPort seahorseLocalAgentAsToolPort(KernelAgentHandoffService handoffService) {
        return new LocalAgentAsToolPort(handoffService);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(ToolRegistryPort.class)
    @ConditionalOnMissingBean
    public BuiltInAgentToolRegistrar seahorseBuiltInAgentToolRegistrar(
            ToolRegistryPort toolRegistry,
            ObjectProvider<DescribedToolPort> toolPorts,
            ObjectProvider<ToolCatalogRepositoryPort> toolCatalogRepository,
            ObjectProvider<Clock> clockProvider) {
        return new BuiltInAgentToolRegistrar(
                toolRegistry,
                toolPorts,
                toolCatalogRepository.getIfAvailable(ToolCatalogRepositoryPort::empty),
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean(KernelMcpOrchestrator.class)
    @ConditionalOnMissingBean
    public McpToolPortAdapter seahorseMcpToolPortAdapter(KernelMcpOrchestrator orchestrator) {
        return new McpToolPortAdapter(orchestrator);
    }

    @Bean
    @ConditionalOnAgentRuntimeEnabled
    @ConditionalOnBean({McpToolPortAdapter.class, McpToolRegistryPort.class, ToolRegistryPort.class})
    @ConditionalOnMissingBean
    public McpToolAllowlistRegistrar seahorseMcpToolAllowlistRegistrar(McpToolPortAdapter adapter,
                                                                        McpToolRegistryPort mcpRegistry,
                                                                        ToolRegistryPort toolRegistry,
                                                                        Environment environment,
                                                                        ObjectProvider<ToolCatalogRepositoryPort> toolCatalogRepository,
                                                                        ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider,
                                                                        ObjectProvider<ToolProviderExposurePolicyPort> toolProviderExposurePolicy,
                                                                        ObjectProvider<ObjectMapper> objectMapper,
                                                                        ObjectProvider<Clock> clockProvider) {
        return new McpToolAllowlistRegistrar(
                adapter,
                mcpRegistry,
                toolRegistry,
                toolCatalogRepository.getIfAvailable(ToolCatalogRepositoryPort::empty),
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults),
                toolProviderExposurePolicy.getIfAvailable(ToolProviderExposurePolicyPort::consumerWebDefaults),
                parseCsv(environment.getProperty(PROP_MCP_INCLUDE, "")),
                objectMapper.getIfAvailable(ObjectMapper::new),
                clockProvider.getIfAvailable(Clock::systemUTC));
    }

    private List<String> parseCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .distinct()
                .toList();
    }

    private Duration parseDuration(String value, Duration defaultValue) {
        if (value == null || value.isBlank()) {
            return defaultValue;
        }
        return DurationStyle.detectAndParse(value);
    }

    @Target({ElementType.METHOD, ElementType.TYPE})
    @Retention(RetentionPolicy.RUNTIME)
    @Conditional(AgentRuntimeEnabledCondition.class)
    private @interface ConditionalOnAgentRuntimeEnabled {
    }

    private static final class AgentRuntimeEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return isEnabled(environment, PROP_AGENT_ENABLED, false)
                    || isEnabled(environment, PROP_WEB_TASK_AGENT_ENABLED, true);
        }

        private boolean isEnabled(Environment environment, String propertyName, boolean defaultValue) {
            return Boolean.TRUE.equals(environment.getProperty(propertyName, Boolean.class, defaultValue));
        }
    }

    private static final class AdvancedLocalAgentToolEnabledCondition implements Condition {

        @Override
        public boolean matches(ConditionContext context, AnnotatedTypeMetadata metadata) {
            Environment environment = context.getEnvironment();
            return !isConsumerWebMode(environment)
                    && isEnabled(environment, PROP_ADVANCED_AGENT_HANDOFF)
                    && isEnabled(environment, PROP_ADVANCED_LOCAL_AGENT);
        }

        private boolean isConsumerWebMode(Environment environment) {
            try {
                return ProductMode.fromProperty(environment.getProperty(PROP_PRODUCT_MODE)) == ProductMode.CONSUMER_WEB;
            } catch (IllegalArgumentException ex) {
                return true;
            }
        }

        private boolean isEnabled(Environment environment, String propertyName) {
            return Boolean.TRUE.equals(environment.getProperty(propertyName, Boolean.class, false));
        }
    }
}

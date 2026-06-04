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
import com.miracle.ai.seahorse.agent.adapters.ai.openai.LlmQueryOptimizerAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalChatStreamCallbackFactory;
import com.miracle.ai.seahorse.agent.adapters.local.LocalStreamTaskPort;
import com.miracle.ai.seahorse.agent.adapters.web.ChatStreamCallbackFactoryPort;
import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.chat.ChatPreparationPorts;
import com.miracle.ai.seahorse.agent.kernel.application.chat.ChatResponsePorts;
import com.miracle.ai.seahorse.agent.kernel.application.chat.ConversationAttachmentContextAssembler;
import com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatInboundService;
import com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatPipeline;
import com.miracle.ai.seahorse.agent.kernel.application.chat.RuleBasedQueryOptimizerPort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackBuilderInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentSkillRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationAttachmentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermExpansionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 聊天链路自动配置。
 *
 * <p>该配置收拢查询优化、流任务默认实现、聊天管线与入口装配，避免主 kernel 配置继续承担聊天主链路细节。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({SeahorseAgentKernelAutoConfiguration.class, SeahorseAgentKernelMemoryAutoConfiguration.class,
        SeahorseAgentAiAdapterAutoConfiguration.class, SeahorseAgentKernelRegistryAutoConfiguration.class,
        SeahorseAgentKernelAgentAutoConfiguration.class})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelChatAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public StreamTaskPort seahorseLocalStreamTaskPort() {
        return new LocalStreamTaskPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatStreamCallbackFactoryPort seahorseLocalChatStreamCallbackFactoryPort(
            StreamTaskPort streamTaskPort,
            ObjectProvider<ConversationMemoryPort> memoryPort,
            ObjectProvider<AgentRunEventBufferPort> eventBufferPort) {
        return new LocalChatStreamCallbackFactory(streamTaskPort,
                memoryPort.getIfAvailable(ConversationMemoryPort::noop),
                eventBufferPort.getIfAvailable(AgentRunEventBufferPort::noop));
    }

    @Bean
    @ConditionalOnBean(ChatModelPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.query-optimizer", name = "llm-enabled", havingValue = "true")
    @ConditionalOnMissingBean(QueryOptimizerPort.class)
    public QueryOptimizerPort seahorseLlmQueryOptimizer(
            ChatModelPort chatModelPort,
            PromptTemplatePort promptTemplatePort,
            ObjectMapper objectMapper) {
        return new LlmQueryOptimizerAdapter(chatModelPort, promptTemplatePort, objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean(QueryOptimizerPort.class)
    public QueryOptimizerPort seahorseRuleBasedQueryOptimizer(
            ObjectProvider<QueryTermExpansionPort> termExpansionPort) {
        return new RuleBasedQueryOptimizerPort(
                termExpansionPort.getIfAvailable(QueryTermExpansionPort::noop));
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatPreparationPorts seahorseChatPreparationPorts(ObjectProvider<ConversationMemoryPort> memoryPort,
                                                             ObjectProvider<MemoryEnginePort> memoryEnginePort,
                                                             ObjectProvider<MemoryIngestionWorkflowPort> memoryIngestionWorkflowPort,
                                                             ObjectProvider<MemoryAggregationServicePort> memoryAggregationServicePort,
                                                             ObjectProvider<MemoryAggregationPolicy> memoryAggregationPolicy,
                                                             ObjectProvider<QueryOptimizerPort> queryOptimizerPort,
                                                             ObjectProvider<QueryRewritePort> queryRewritePort,
                                                             ObjectProvider<IntentResolutionPort> intentResolutionPort,
                                                             ObjectProvider<IntentGuidancePort> intentGuidancePort,
                                                             ObjectProvider<RetrievalContextPort> retrievalContextPort) {
        return new ChatPreparationPorts(
                memoryPort.getIfAvailable(ConversationMemoryPort::noop),
                memoryEnginePort.getIfAvailable(MemoryEnginePort::noop),
                memoryIngestionWorkflowPort.getIfAvailable(() -> command ->
                        com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult.ignored("noop")),
                memoryAggregationServicePort.getIfAvailable(MemoryAggregationServicePort::noop),
                memoryAggregationPolicy.getIfAvailable(MemoryAggregationPolicy::defaults),
                queryOptimizerPort.getIfAvailable(QueryOptimizerPort::passthrough),
                queryRewritePort.getIfAvailable(QueryRewritePort::passthrough),
                intentResolutionPort.getIfAvailable(IntentResolutionPort::empty),
                intentGuidancePort.getIfAvailable(IntentGuidancePort::none),
                retrievalContextPort.getIfAvailable(() -> (subIntents, topK) ->
                        RetrievalContext.builder()
                                .intentChunks(Map.of())
                                .build()));
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatResponsePorts seahorseChatResponsePorts(ObjectProvider<RagPromptPort> ragPromptPort,
                                                       ObjectProvider<PromptTemplatePort> promptTemplatePort,
                                                       ObjectProvider<StreamingChatModelPort> streamingChatModelPort,
                                                       ObjectProvider<StreamTaskPort> streamTaskPort,
                                                       ObjectProvider<ContextWeaverPort> contextWeaverPort) {
        return new ChatResponsePorts(
                ragPromptPort.getIfAvailable(RagPromptPort::simple),
                promptTemplatePort.getIfAvailable(PromptTemplatePort::empty),
                streamingChatModelPort.getIfAvailable(StreamingChatModelPort::noop),
                streamTaskPort.getIfAvailable(StreamTaskPort::noop),
                contextWeaverPort.getIfAvailable(com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver::new));
    }

    @Bean
    @ConditionalOnBean({ConversationAttachmentRepositoryPort.class, ObjectStoragePort.class})
    @ConditionalOnMissingBean
    public ConversationAttachmentContextAssembler seahorseConversationAttachmentContextAssembler(
            ConversationAttachmentRepositoryPort attachmentRepositoryPort,
            ObjectStoragePort objectStoragePort,
            ObjectProvider<DocumentParserPort> documentParserPort) {
        return new ConversationAttachmentContextAssembler(
                attachmentRepositoryPort,
                objectStoragePort,
                documentParserPort.getIfAvailable(DocumentParserPort::plainText));
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelChatPipeline seahorseKernelChatPipeline(ChatPreparationPorts preparationPorts,
                                                         ChatResponsePorts responsePorts,
                                                         ObjectProvider<KernelRagTraceRecorder> traceRecorder,
                                                         ObjectProvider<ContextPackBuilderInboundPort> contextPackBuilder,
                                                         ObjectProvider<ConversationAttachmentContextAssembler> attachmentContextAssembler,
                                                         org.springframework.core.env.Environment environment) {
        String configured = environment.getProperty(
                "seahorse-agent.chat.empty-retrieval-fallback", "generic");
        KernelChatPipeline.EmptyRetrievalStrategy strategy =
                "strict".equalsIgnoreCase(configured) || "message".equalsIgnoreCase(configured)
                        ? KernelChatPipeline.EmptyRetrievalStrategy.STATIC_MESSAGE
                        : KernelChatPipeline.EmptyRetrievalStrategy.FALLBACK_GENERIC;
        return new KernelChatPipeline(preparationPorts, responsePorts,
                traceRecorder.getIfAvailable(KernelRagTraceRecorder::noop),
                strategy,
                Optional.ofNullable(contextPackBuilder.getIfAvailable()),
                attachmentContextAssembler.getIfAvailable(ConversationAttachmentContextAssembler::noop));
    }

    @Bean
    @ConditionalOnBean({KernelChatPipeline.class, StreamTaskPort.class})
    @ConditionalOnMissingBean
    public ChatInboundPort seahorseChatInboundPort(KernelChatPipeline chatPipeline,
                                                   StreamTaskPort streamTaskPort,
                                                   ObjectProvider<KernelAgentLoop> agentLoop,
                                                   ObjectProvider<KernelRagTraceRecorder> traceRecorder,
                                                   ObjectProvider<ConversationMemoryPort> memoryPort,
                                                   ObjectProvider<MemoryEnginePort> memoryEnginePort,
                                                   ObjectProvider<AgentRunInboundPort> agentRunPort,
                                                   ObjectProvider<ContextPackBuilderInboundPort> contextPackBuilder,
                                                   ObjectProvider<AgentDefinitionRepositoryPort> agentDefinitionRepository,
                                                   ObjectProvider<ConversationAttachmentContextAssembler> attachmentContextAssembler,
                                                   ObjectProvider<AgentSkillRepositoryPort> skillRepository) {
        return new KernelChatInboundService(chatPipeline, streamTaskPort,
                Optional.ofNullable(agentLoop.getIfAvailable()),
                traceRecorder.getIfAvailable(KernelRagTraceRecorder::noop),
                memoryPort.getIfAvailable(ConversationMemoryPort::noop),
                memoryEnginePort.getIfAvailable(MemoryEnginePort::noop),
                Optional.ofNullable(agentRunPort.getIfAvailable()),
                Optional.ofNullable(contextPackBuilder.getIfAvailable()),
                Optional.ofNullable(agentDefinitionRepository.getIfAvailable()),
                attachmentContextAssembler.getIfAvailable(ConversationAttachmentContextAssembler::noop),
                Optional.ofNullable(skillRepository.getIfAvailable()));
    }
}

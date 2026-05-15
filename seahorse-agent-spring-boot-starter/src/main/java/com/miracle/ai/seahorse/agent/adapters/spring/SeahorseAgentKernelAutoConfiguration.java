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
import com.miracle.ai.seahorse.agent.adapters.local.LocalChatStreamCallbackFactory;
import com.miracle.ai.seahorse.agent.adapters.local.LocalStreamTaskPort;
import com.miracle.ai.seahorse.agent.adapters.spring.config.AgentAdapterProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.config.AgentKernelProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.config.AgentPluginProperties;
import com.miracle.ai.seahorse.agent.adapters.web.ChatStreamCallbackFactoryPort;
import com.miracle.ai.seahorse.agent.kernel.application.chat.ChatPreparationPorts;
import com.miracle.ai.seahorse.agent.kernel.application.chat.ChatResponsePorts;
import com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatInboundService;
import com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatPipeline;
import com.miracle.ai.seahorse.agent.kernel.application.auth.KernelAuthService;
import com.miracle.ai.seahorse.agent.kernel.application.conversation.KernelConversationManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.dashboard.KernelDashboardService;
import com.miracle.ai.seahorse.agent.kernel.application.feedback.KernelMessageFeedbackService;
import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionEngine;
import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionPipelineService;
import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionTaskService;
import com.miracle.ai.seahorse.agent.kernel.application.intent.KernelIntentTreeService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeBaseService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeChunkService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeDocumentChunkHandler;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.DocumentRefreshServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelDocumentRefreshService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeDocumentService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentVectorPorts;
import com.miracle.ai.seahorse.agent.kernel.application.keyword.KernelKeywordIndexMaintenanceService;
import com.miracle.ai.seahorse.agent.kernel.application.mapping.KernelQueryTermMappingService;
import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryEnginePort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryEngine;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryGovernanceService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryGovernanceServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.memory.RuleBasedMemoryCandidateExtractor;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryInferencePort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryManagementServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataBackfillService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataDictionaryService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataQualityService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataQuarantineService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataReviewService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataSchemaService;
import com.miracle.ai.seahorse.agent.kernel.application.model.KernelModelRoutingService;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelMultiChannelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEvaluationService;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine.KernelRetrievalEnginePorts;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalStrategyTemplateService;
import com.miracle.ai.seahorse.agent.kernel.application.sample.KernelSampleQuestionService;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceService;
import com.miracle.ai.seahorse.agent.kernel.application.user.KernelUserService;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.ChunkerNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.EmbedderNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.EnhancerNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.EnricherNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.FetcherNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.IngestionNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.IndexerNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.MetadataExtractorNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.MetadataNormalizerNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.MetadataValidatorNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.ParserNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.DefaultMetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.FinalTruncatePostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.IntentDirectedSearchFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.KeywordSearchChannelFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.MetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.MetadataGuardPostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.RerankPostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.RrfFusionPostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchChannelFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchResultPostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.VectorGlobalSearchFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureHealthAggregator;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.AuthInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.conversation.ConversationManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.dashboard.DashboardInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.MessageFeedbackInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.intent.IntentTreeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionTaskInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeBaseInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeChunkInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.mapping.QueryTermMappingInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataDictionaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplateInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.sample.SampleQuestionInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.trace.RagTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.user.UserInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.adapters.ai.openai.LlmQueryOptimizerAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.chat.RuleBasedQueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryOptimizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermExpansionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.EnhancementPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.EnrichmentPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionConditionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionNodeLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentTreeRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshStateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpParameterExtractionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataIndexCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelRoutingStatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AdapterHealthIndicatorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalContextFormatPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.Map;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Seahorse 原生 L1/L2 内核自动配置。
 *
 * <p>该配置只装配内核编排、Feature 注册表和 Web 本地流式任务能力，
 * 该配置只装配 Seahorse 原生 kernel 与端口，确保 starter 可作为独立微内核入口使用。
 */
@AutoConfiguration
@EnableConfigurationProperties({
        AgentKernelProperties.class,
        AgentPluginProperties.class,
        AgentAdapterProperties.class
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public FeatureActivationContext seahorseFeatureActivationContext(AgentPluginProperties pluginProperties) {
        return new FeatureActivationContext("", "", Map.of(), pluginProperties.toFeatureProperties());
    }

    @Bean
    @ConditionalOnMissingBean
    public ExtensionRegistry seahorseExtensionRegistry() {
        return new DefaultExtensionRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public FeatureHealthAggregator seahorseFeatureHealthAggregator(
            ObjectProvider<AgentFeature> agentFeatures,
            ObjectProvider<AdapterHealthIndicatorPort> adapterHealthIndicators) {
        return new FeatureHealthAggregator(agentFeatures.orderedStream().toList(),
                adapterHealthIndicators.orderedStream().toList());
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public FetcherNodeFeature seahorseFetcherNodeFeature(ExtensionRegistry extensionRegistry,
                                                         ObjectProvider<DocumentFetcherPort> documentFetcherPort) {
        FetcherNodeFeature feature = new FetcherNodeFeature(
                documentFetcherPort.getIfAvailable(DocumentFetcherPort::unsupported));
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public ParserNodeFeature seahorseParserNodeFeature(ExtensionRegistry extensionRegistry,
                                                       ObjectProvider<DocumentParserPort> documentParserPort) {
        ParserNodeFeature feature = new ParserNodeFeature(documentParserPort.getIfAvailable(DocumentParserPort::plainText));
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public MetadataExtractorNodeFeature seahorseMetadataExtractorNodeFeature(
            ExtensionRegistry extensionRegistry,
            ObjectProvider<MetadataSchemaRegistryPort> schemaRegistryPort,
            ObjectProvider<ChatModelPort> chatModelPort) {
        MetadataExtractorNodeFeature feature = new MetadataExtractorNodeFeature(
                schemaRegistryPort.getIfAvailable(MetadataSchemaRegistryPort::empty),
                chatModelPort.getIfAvailable(ChatModelPort::noop));
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public MetadataNormalizerNodeFeature seahorseMetadataNormalizerNodeFeature(
            ExtensionRegistry extensionRegistry,
            ObjectProvider<MetadataSchemaRegistryPort> schemaRegistryPort,
            ObjectProvider<MetadataDictionaryPort> dictionaryPort) {
        MetadataNormalizerNodeFeature feature = new MetadataNormalizerNodeFeature(
                schemaRegistryPort.getIfAvailable(MetadataSchemaRegistryPort::empty),
                dictionaryPort.getIfAvailable(MetadataDictionaryPort::noop));
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public MetadataValidatorNodeFeature seahorseMetadataValidatorNodeFeature(
            ExtensionRegistry extensionRegistry,
            ObjectProvider<MetadataSchemaRegistryPort> schemaRegistryPort,
            ObjectProvider<MetadataExtractionResultRepositoryPort> resultRepositoryPort,
            ObjectProvider<MetadataReviewQueuePort> reviewQueuePort,
            ObjectProvider<MetadataQuarantinePort> quarantinePort,
            ObjectProvider<MetadataCanonicalWritePort> canonicalWritePort) {
        MetadataValidatorNodeFeature feature = new MetadataValidatorNodeFeature(
                schemaRegistryPort.getIfAvailable(MetadataSchemaRegistryPort::empty),
                resultRepositoryPort.getIfAvailable(MetadataExtractionResultRepositoryPort::noop),
                reviewQueuePort.getIfAvailable(MetadataReviewQueuePort::noop),
                quarantinePort.getIfAvailable(MetadataQuarantinePort::noop),
                canonicalWritePort.getIfAvailable(MetadataCanonicalWritePort::noop));
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnMissingBean
    public EnhancementPromptPort seahorseEnhancementPromptPort() {
        return EnhancementPromptPort.defaults();
    }

    @Bean
    @ConditionalOnMissingBean
    public EnrichmentPromptPort seahorseEnrichmentPromptPort() {
        return EnrichmentPromptPort.defaults();
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public EnhancerNodeFeature seahorseEnhancerNodeFeature(ExtensionRegistry extensionRegistry,
                                                           ObjectProvider<ChatModelPort> chatModelPort,
                                                           EnhancementPromptPort promptPort) {
        EnhancerNodeFeature feature = new EnhancerNodeFeature(
                chatModelPort.getIfAvailable(ChatModelPort::noop), promptPort);
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public EnricherNodeFeature seahorseEnricherNodeFeature(ExtensionRegistry extensionRegistry,
                                                           ObjectProvider<ChatModelPort> chatModelPort,
                                                           EnrichmentPromptPort promptPort) {
        EnricherNodeFeature feature = new EnricherNodeFeature(
                chatModelPort.getIfAvailable(ChatModelPort::noop), promptPort);
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public ChunkerNodeFeature seahorseChunkerNodeFeature(ExtensionRegistry extensionRegistry,
                                                         ObjectProvider<EmbeddingModelPort> embeddingModelPort) {
        ChunkerNodeFeature feature = new ChunkerNodeFeature(embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop));
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public EmbedderNodeFeature seahorseEmbedderNodeFeature(ExtensionRegistry extensionRegistry,
                                                           ObjectProvider<EmbeddingModelPort> embeddingModelPort) {
        EmbedderNodeFeature feature = new EmbedderNodeFeature(embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop));
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean({ExtensionRegistry.class, VectorCollectionAdminPort.class, VectorIndexPort.class,
            KnowledgeChunkRepositoryPort.class})
    public IndexerNodeFeature seahorseIndexerNodeFeature(ExtensionRegistry extensionRegistry,
                                                         VectorCollectionAdminPort collectionAdminPort,
                                                         VectorIndexPort vectorIndexPort,
                                                         KnowledgeChunkRepositoryPort chunkRepositoryPort,
                                                         ObjectProvider<KeywordIndexPort> keywordIndexPort) {
        IndexerNodeFeature feature = new IndexerNodeFeature(collectionAdminPort, vectorIndexPort, chunkRepositoryPort,
                keywordIndexPort.getIfAvailable(KeywordIndexPort::noop));
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean({ExtensionRegistry.class, VectorSearchPort.class})
    public IntentDirectedSearchFeature seahorseIntentDirectedSearchFeature(
            ExtensionRegistry extensionRegistry,
            VectorSearchPort vectorSearchPort,
            ObjectProvider<EmbeddingModelPort> embeddingModelPort,
            @Qualifier("ragInnerRetrievalThreadPoolExecutor") ObjectProvider<Executor> innerRetrievalExecutor) {
        IntentDirectedSearchFeature feature = new IntentDirectedSearchFeature(
                vectorSearchPort, innerRetrievalExecutor.getIfAvailable(() -> Runnable::run),
                null, embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop));
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), SearchChannelFeature.class,
                FeatureType.SEARCH_CHANNEL, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean({ExtensionRegistry.class, KnowledgeBaseQueryPort.class, VectorSearchPort.class})
    public VectorGlobalSearchFeature seahorseVectorGlobalSearchFeature(ExtensionRegistry extensionRegistry,
                                                                       KnowledgeBaseQueryPort knowledgeBaseQueryPort,
                                                                       VectorSearchPort vectorSearchPort,
                                                                       ObjectProvider<EmbeddingModelPort> embeddingModelPort) {
        VectorGlobalSearchFeature feature = new VectorGlobalSearchFeature(knowledgeBaseQueryPort, vectorSearchPort,
                embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop));
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), SearchChannelFeature.class,
                FeatureType.SEARCH_CHANNEL, feature.order(), false), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean({ExtensionRegistry.class, KeywordSearchPort.class})
    public KeywordSearchChannelFeature seahorseKeywordSearchChannelFeature(ExtensionRegistry extensionRegistry,
                                                                          KeywordSearchPort keywordSearchPort) {
        KeywordSearchChannelFeature feature = new KeywordSearchChannelFeature(keywordSearchPort);
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), SearchChannelFeature.class,
                FeatureType.SEARCH_CHANNEL, feature.order(), false), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public MetadataGuardPostProcessorFeature seahorseMetadataGuardPostProcessorFeature(
            ExtensionRegistry extensionRegistry) {
        MetadataGuardPostProcessorFeature feature = new MetadataGuardPostProcessorFeature();
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public RrfFusionPostProcessorFeature seahorseRrfFusionPostProcessorFeature(
            ExtensionRegistry extensionRegistry,
            ObjectProvider<ObservationPort> observationPort) {
        RrfFusionPostProcessorFeature feature = new RrfFusionPostProcessorFeature(observationPort.getIfAvailable());
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, feature.order(), false), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean({ExtensionRegistry.class, RerankModelPort.class})
    public RerankPostProcessorFeature seahorseRerankPostProcessorFeature(ExtensionRegistry extensionRegistry,
                                                                         RerankModelPort rerankModelPort,
                                                                         ObjectProvider<ObservationPort> observationPort) {
        RerankPostProcessorFeature feature = new RerankPostProcessorFeature(rerankModelPort,
                observationPort.getIfAvailable());
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, feature.order(), false), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public FinalTruncatePostProcessorFeature seahorseFinalTruncatePostProcessorFeature(
            ExtensionRegistry extensionRegistry,
            ObjectProvider<ObservationPort> observationPort) {
        FinalTruncatePostProcessorFeature feature = new FinalTruncatePostProcessorFeature(
                observationPort.getIfAvailable());
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, feature.order(), false), feature);
        return feature;
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataFilterCompiler seahorseMetadataFilterCompiler() {
        return new DefaultMetadataFilterCompiler();
    }

    @Bean
    @ConditionalOnMissingBean
    public StreamTaskPort seahorseLocalStreamTaskPort() {
        return new LocalStreamTaskPort();
    }

    @Bean
    @ConditionalOnMissingBean
    public ChatStreamCallbackFactoryPort seahorseLocalChatStreamCallbackFactoryPort(
            StreamTaskPort streamTaskPort,
            ObjectProvider<ConversationMemoryPort> memoryPort) {
        return new LocalChatStreamCallbackFactory(streamTaskPort,
                memoryPort.getIfAvailable(ConversationMemoryPort::noop));
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelMultiChannelRetrievalEngine seahorseKernelMultiChannelRetrievalEngine(
            ExtensionRegistry extensionRegistry,
            @Qualifier("ragRetrievalThreadPoolExecutor") ObjectProvider<Executor> retrievalExecutor,
            FeatureActivationContext activationContext,
            ObjectProvider<MetadataSchemaRegistryPort> schemaRegistryPort,
            ObjectProvider<MetadataFilterCompiler> metadataFilterCompiler,
            ObjectProvider<KernelRagTraceRecorder> traceRecorder) {
        return new KernelMultiChannelRetrievalEngine(extensionRegistry,
                retrievalExecutor.getIfAvailable(() -> Runnable::run), activationContext,
                schemaRegistryPort.getIfAvailable(MetadataSchemaRegistryPort::empty),
                metadataFilterCompiler.getIfAvailable(DefaultMetadataFilterCompiler::new),
                traceRecorder.getIfAvailable(KernelRagTraceRecorder::noop));
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelMcpOrchestrator seahorseKernelMcpOrchestrator(
            ObjectProvider<McpToolRegistryPort> toolRegistryPort,
            ObjectProvider<McpParameterExtractionPort> parameterExtractionPort,
            @Qualifier("mcpBatchThreadPoolExecutor") ObjectProvider<Executor> mcpExecutor) {
        return new KernelMcpOrchestrator(
                toolRegistryPort.getIfAvailable(McpToolRegistryPort::empty),
                parameterExtractionPort.getIfAvailable(McpParameterExtractionPort::noop),
                mcpExecutor.getIfAvailable(() -> Runnable::run));
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelRetrievalEngine seahorseKernelRetrievalEngine(
            KernelMultiChannelRetrievalEngine multiChannelRetrievalEngine,
            KernelMcpOrchestrator mcpOrchestrator,
            ObjectProvider<RetrievalContextFormatPort> formatPort,
            @Qualifier("ragContextThreadPoolExecutor") ObjectProvider<Executor> ragContextExecutor) {
        return new KernelRetrievalEngine(new KernelRetrievalEnginePorts(
                multiChannelRetrievalEngine,
                mcpOrchestrator,
                formatPort.getIfAvailable(RetrievalContextFormatPort::noop),
                ragContextExecutor.getIfAvailable(() -> Runnable::run)));
    }

    @Bean
    @Primary
    @ConditionalOnBean(KernelRetrievalEngine.class)
    public RetrievalContextPort seahorseKernelRetrievalContextPort(KernelRetrievalEngine retrievalEngine) {
        return retrievalEngine;
    }

    @Bean
    @ConditionalOnBean(KernelRetrievalEngine.class)
    @ConditionalOnMissingBean(RetrievalEvaluationInboundPort.class)
    public KernelRetrievalEvaluationService seahorseRetrievalEvaluationInboundPort(
            KernelRetrievalEngine retrievalEngine) {
        return new KernelRetrievalEvaluationService(retrievalEngine);
    }

    @Bean
    @ConditionalOnMissingBean(RetrievalStrategyTemplateInboundPort.class)
    public KernelRetrievalStrategyTemplateService seahorseRetrievalStrategyTemplateInboundPort(
            ObjectProvider<RetrievalStrategyTemplateRepositoryPort> repositoryPort) {
        return new KernelRetrievalStrategyTemplateService(
                repositoryPort.getIfAvailable(RetrievalStrategyTemplateRepositoryPort::empty));
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
                                                             ObjectProvider<QueryOptimizerPort> queryOptimizerPort,
                                                             ObjectProvider<QueryRewritePort> queryRewritePort,
                                                             ObjectProvider<IntentResolutionPort> intentResolutionPort,
                                                             ObjectProvider<IntentGuidancePort> intentGuidancePort,
                                                             ObjectProvider<RetrievalContextPort> retrievalContextPort) {
        return new ChatPreparationPorts(
                memoryPort.getIfAvailable(ConversationMemoryPort::noop),
                memoryEnginePort.getIfAvailable(MemoryEnginePort::noop),
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
                                                       ObjectProvider<StreamTaskPort> streamTaskPort) {
        return new ChatResponsePorts(
                ragPromptPort.getIfAvailable(RagPromptPort::simple),
                promptTemplatePort.getIfAvailable(PromptTemplatePort::empty),
                streamingChatModelPort.getIfAvailable(StreamingChatModelPort::noop),
                streamTaskPort.getIfAvailable(StreamTaskPort::noop));
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelChatPipeline seahorseKernelChatPipeline(ChatPreparationPorts preparationPorts,
                                                         ChatResponsePorts responsePorts,
                                                         ObjectProvider<KernelRagTraceRecorder> traceRecorder) {
        return new KernelChatPipeline(preparationPorts, responsePorts,
                traceRecorder.getIfAvailable(KernelRagTraceRecorder::noop));
    }

    @Bean
    @ConditionalOnBean({KernelChatPipeline.class, StreamTaskPort.class})
    @ConditionalOnMissingBean
    public ChatInboundPort seahorseChatInboundPort(KernelChatPipeline chatPipeline,
                                                   StreamTaskPort streamTaskPort,
                                                   ObjectProvider<KernelRagTraceRecorder> traceRecorder) {
        return new KernelChatInboundService(chatPipeline, streamTaskPort,
                traceRecorder.getIfAvailable(KernelRagTraceRecorder::noop));
    }

    @Bean
    @ConditionalOnBean({UserRepositoryPort.class, PasswordHasherPort.class, TokenServicePort.class})
    @ConditionalOnMissingBean(AuthInboundPort.class)
    public KernelAuthService seahorseAuthInboundPort(UserRepositoryPort userRepositoryPort,
                                                     PasswordHasherPort passwordHasherPort,
                                                     TokenServicePort tokenServicePort) {
        return new KernelAuthService(userRepositoryPort, passwordHasherPort, tokenServicePort);
    }

    @Bean
    @ConditionalOnBean({UserRepositoryPort.class, PasswordHasherPort.class, CurrentUserPort.class})
    @ConditionalOnMissingBean(UserInboundPort.class)
    public KernelUserService seahorseUserInboundPort(UserRepositoryPort userRepositoryPort,
                                                     PasswordHasherPort passwordHasherPort,
                                                     CurrentUserPort currentUserPort) {
        return new KernelUserService(userRepositoryPort, passwordHasherPort, currentUserPort);
    }

    @Bean
    @ConditionalOnBean(MessageFeedbackRepositoryPort.class)
    @ConditionalOnMissingBean(MessageFeedbackInboundPort.class)
    public KernelMessageFeedbackService seahorseMessageFeedbackInboundPort(
            MessageFeedbackRepositoryPort feedbackRepositoryPort) {
        return new KernelMessageFeedbackService(feedbackRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(ConversationRepositoryPort.class)
    @ConditionalOnMissingBean(ConversationManagementInboundPort.class)
    public KernelConversationManagementService seahorseConversationManagementInboundPort(
            ConversationRepositoryPort conversationRepositoryPort) {
        return new KernelConversationManagementService(conversationRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(RagTraceRepositoryPort.class)
    @ConditionalOnMissingBean
    public KernelRagTraceRecorder seahorseRagTraceRecorder(RagTraceRepositoryPort traceRepositoryPort) {
        return new KernelRagTraceRecorder(traceRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(RagTraceRepositoryPort.class)
    @ConditionalOnMissingBean(RagTraceInboundPort.class)
    public KernelRagTraceService seahorseRagTraceInboundPort(RagTraceRepositoryPort traceRepositoryPort) {
        return new KernelRagTraceService(traceRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(SampleQuestionRepositoryPort.class)
    @ConditionalOnMissingBean(SampleQuestionInboundPort.class)
    public KernelSampleQuestionService seahorseSampleQuestionInboundPort(
            SampleQuestionRepositoryPort sampleQuestionRepositoryPort) {
        return new KernelSampleQuestionService(sampleQuestionRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(DashboardRepositoryPort.class)
    @ConditionalOnMissingBean(DashboardInboundPort.class)
    public KernelDashboardService seahorseDashboardInboundPort(DashboardRepositoryPort dashboardRepositoryPort) {
        return new KernelDashboardService(dashboardRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(IntentTreeRepositoryPort.class)
    @ConditionalOnMissingBean(IntentTreeInboundPort.class)
    public KernelIntentTreeService seahorseIntentTreeInboundPort(
            IntentTreeRepositoryPort intentTreeRepositoryPort,
            ObjectProvider<KeyValueCachePort> cachePort) {
        return new KernelIntentTreeService(intentTreeRepositoryPort,
                cachePort.getIfAvailable(SeahorseAgentKernelAutoConfiguration::noopCachePort));
    }

    @Bean
    @ConditionalOnBean(QueryTermMappingRepositoryPort.class)
    @ConditionalOnMissingBean(QueryTermMappingInboundPort.class)
    public KernelQueryTermMappingService seahorseQueryTermMappingInboundPort(
            QueryTermMappingRepositoryPort mappingRepositoryPort,
            ObjectProvider<KeyValueCachePort> cachePort) {
        return new KernelQueryTermMappingService(mappingRepositoryPort,
                cachePort.getIfAvailable(SeahorseAgentKernelAutoConfiguration::noopCachePort));
    }

    @Bean
    @ConditionalOnBean({KnowledgeBaseRepositoryPort.class, VectorCollectionAdminPort.class, ObjectStoragePort.class})
    @ConditionalOnMissingBean(KnowledgeBaseInboundPort.class)
    public KernelKnowledgeBaseService seahorseKnowledgeBaseInboundPort(
            KnowledgeBaseRepositoryPort knowledgeBaseRepositoryPort,
            VectorCollectionAdminPort vectorCollectionAdminPort,
            ObjectStoragePort objectStoragePort) {
        return new KernelKnowledgeBaseService(
                knowledgeBaseRepositoryPort, vectorCollectionAdminPort, objectStoragePort);
    }

    @Bean
    @ConditionalOnBean(KnowledgeChunkRepositoryPort.class)
    @ConditionalOnMissingBean(KnowledgeChunkInboundPort.class)
    public KernelKnowledgeChunkService seahorseKnowledgeChunkInboundPort(
            KnowledgeChunkRepositoryPort knowledgeChunkRepositoryPort,
            ObjectProvider<EmbeddingModelPort> embeddingModelPort,
            ObjectProvider<VectorIndexPort> vectorIndexPort) {
        return new KernelKnowledgeChunkService(
                knowledgeChunkRepositoryPort,
                embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop),
                vectorIndexPort.getIfAvailable(SeahorseAgentKernelAutoConfiguration::noopVectorIndexPort));
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelIngestionEngine seahorseKernelIngestionEngine(ExtensionRegistry extensionRegistry,
                                                               FeatureActivationContext activationContext,
                                                               ObjectProvider<IngestionConditionPort> conditionPort,
                                                               ObjectProvider<IngestionNodeLogPort> nodeLogPort) {
        return new KernelIngestionEngine(extensionRegistry, activationContext,
                conditionPort.getIfAvailable(IngestionConditionPort::alwaysExecute),
                nodeLogPort.getIfAvailable(IngestionNodeLogPort::noop));
    }

    @Bean
    @ConditionalOnBean(IngestionPipelineRepositoryPort.class)
    @ConditionalOnMissingBean(IngestionPipelineInboundPort.class)
    public KernelIngestionPipelineService seahorseIngestionPipelineInboundPort(
            IngestionPipelineRepositoryPort pipelineRepositoryPort) {
        return new KernelIngestionPipelineService(pipelineRepositoryPort);
    }

    @Bean
    @ConditionalOnBean({KernelIngestionEngine.class, PipelineDefinitionRepositoryPort.class,
            IngestionTaskRepositoryPort.class})
    @ConditionalOnMissingBean(IngestionTaskInboundPort.class)
    public KernelIngestionTaskService seahorseIngestionTaskInboundPort(
            KernelIngestionEngine ingestionEngine,
            PipelineDefinitionRepositoryPort pipelineDefinitionRepositoryPort,
            IngestionTaskRepositoryPort taskRepositoryPort) {
        return new KernelIngestionTaskService(
                ingestionEngine, pipelineDefinitionRepositoryPort, taskRepositoryPort);
    }

    @Bean
    @ConditionalOnBean({KnowledgeBaseQueryPort.class, KnowledgeDocumentRepositoryPort.class,
            ObjectStoragePort.class, MessageQueuePort.class, KernelIngestionEngine.class})
    @ConditionalOnMissingBean
    public KnowledgeDocumentServicePorts seahorseKnowledgeDocumentServicePorts(
            KnowledgeBaseQueryPort knowledgeBaseQueryPort,
            KnowledgeDocumentRepositoryPort documentRepositoryPort,
            ObjectStoragePort objectStoragePort,
            MessageQueuePort messageQueuePort,
            KernelIngestionEngine ingestionEngine) {
        return new KnowledgeDocumentServicePorts(
                knowledgeBaseQueryPort, documentRepositoryPort, objectStoragePort, messageQueuePort, ingestionEngine);
    }

    @Bean
    @ConditionalOnMissingBean
    public KnowledgeDocumentVectorPorts seahorseKnowledgeDocumentVectorPorts(
            ObjectProvider<EmbeddingModelPort> embeddingModelPort,
            ObjectProvider<VectorIndexPort> vectorIndexPort,
            ObjectProvider<KeywordIndexPort> keywordIndexPort) {
        return new KnowledgeDocumentVectorPorts(
                embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop),
                vectorIndexPort.getIfAvailable(SeahorseAgentKernelAutoConfiguration::noopVectorIndexPort),
                keywordIndexPort.getIfAvailable(KeywordIndexPort::noop));
    }

    @Bean
    @ConditionalOnBean(KnowledgeDocumentServicePorts.class)
    @ConditionalOnMissingBean(KnowledgeDocumentInboundPort.class)
    public KernelKnowledgeDocumentService seahorseKernelKnowledgeDocumentService(
            KnowledgeDocumentServicePorts servicePorts,
            KnowledgeDocumentVectorPorts documentVectorPorts,
            ObjectProvider<DocumentRefreshSchedulePort> refreshSchedulePort,
            ObjectProvider<SchedulerPort> schedulerPort,
            @Value("${seahorse-agent.adapters.mq.pulsar.topics.knowledge-document-chunk:"
                    + KernelKnowledgeDocumentService.DEFAULT_CHUNK_TOPIC + "}") String chunkTopic) {
        return new KernelKnowledgeDocumentService(
                servicePorts,
                documentVectorPorts,
                chunkTopic,
                refreshSchedulePort.getIfAvailable(DocumentRefreshSchedulePort::noop),
                schedulerPort.getIfAvailable(SchedulerPort::none));
    }

    @Bean
    @ConditionalOnBean(KnowledgeDocumentRepositoryPort.class)
    @ConditionalOnMissingBean(KeywordIndexMaintenanceInboundPort.class)
    public KernelKeywordIndexMaintenanceService seahorseKeywordIndexMaintenanceInboundPort(
            KnowledgeDocumentRepositoryPort documentRepositoryPort,
            ObjectProvider<KeywordIndexPort> keywordIndexPort,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelKeywordIndexMaintenanceService(
                documentRepositoryPort,
                keywordIndexPort.getIfAvailable(KeywordIndexPort::noop),
                observationPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean({KnowledgeDocumentRepositoryPort.class, ObjectStoragePort.class,
            PipelineDefinitionRepositoryPort.class, KernelIngestionEngine.class,
            MetadataBackfillJobRepositoryPort.class})
    @ConditionalOnMissingBean(MetadataBackfillInboundPort.class)
    public KernelMetadataBackfillService seahorseMetadataBackfillInboundPort(
            KnowledgeDocumentRepositoryPort documentRepositoryPort,
            ObjectStoragePort objectStoragePort,
            PipelineDefinitionRepositoryPort pipelineRepositoryPort,
            KernelIngestionEngine ingestionEngine,
            MetadataBackfillJobRepositoryPort jobRepositoryPort,
            ObjectProvider<MetadataExtractionResultRepositoryPort> extractionResultRepositoryPort,
            ObjectProvider<MetadataQuarantinePort> quarantinePort,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelMetadataBackfillService(
                documentRepositoryPort,
                objectStoragePort,
                pipelineRepositoryPort,
                ingestionEngine,
                jobRepositoryPort,
                extractionResultRepositoryPort.getIfAvailable(MetadataExtractionResultRepositoryPort::noop),
                quarantinePort.getIfAvailable(MetadataQuarantinePort::noop),
                observationPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(MetadataQualityReportRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataQualityInboundPort.class)
    public KernelMetadataQualityService seahorseMetadataQualityInboundPort(
            MetadataQualityReportRepositoryPort reportRepositoryPort,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelMetadataQualityService(reportRepositoryPort, observationPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(MetadataReviewManagementRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataReviewInboundPort.class)
    public KernelMetadataReviewService seahorseMetadataReviewInboundPort(
            MetadataReviewManagementRepositoryPort reviewRepositoryPort,
            ObjectProvider<MetadataCanonicalWritePort> canonicalWritePort,
            ObjectProvider<MetadataQuarantinePort> quarantinePort,
            ObjectProvider<MetadataIndexCompensationPort> indexCompensationPort,
            ObjectProvider<MetadataReviewReExtractPort> reExtractPort) {
        return new KernelMetadataReviewService(
                reviewRepositoryPort,
                canonicalWritePort.getIfAvailable(MetadataCanonicalWritePort::noop),
                quarantinePort.getIfAvailable(MetadataQuarantinePort::noop),
                indexCompensationPort.getIfAvailable(MetadataIndexCompensationPort::noop),
                reExtractPort.getIfAvailable(MetadataReviewReExtractPort::noop));
    }

    @Bean
    @ConditionalOnBean(KeywordIndexMaintenanceInboundPort.class)
    @ConditionalOnMissingBean(MetadataIndexCompensationPort.class)
    public MetadataIndexCompensationPort seahorseMetadataIndexCompensationPort(
            KeywordIndexMaintenanceInboundPort keywordIndexMaintenanceInboundPort) {
        return documentId -> {
            if (documentId != null && !documentId.isBlank()) {
                keywordIndexMaintenanceInboundPort.rebuildDocument(documentId);
            }
        };
    }

    @Bean
    @ConditionalOnBean(MetadataQuarantineManagementRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataQuarantineInboundPort.class)
    public KernelMetadataQuarantineService seahorseMetadataQuarantineInboundPort(
            MetadataQuarantineManagementRepositoryPort quarantineRepositoryPort,
            @Value("${seahorse-agent.metadata.governance.quarantine.max-retry-count:3}") int maxRetryCount) {
        return new KernelMetadataQuarantineService(quarantineRepositoryPort, maxRetryCount);
    }

    @Bean
    @ConditionalOnBean(MetadataSchemaManagementRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataSchemaInboundPort.class)
    public KernelMetadataSchemaService seahorseMetadataSchemaInboundPort(
            MetadataSchemaManagementRepositoryPort repositoryPort,
            ObjectProvider<MetadataSchemaIndexSyncPort> indexSyncPort) {
        return new KernelMetadataSchemaService(repositoryPort,
                indexSyncPort.getIfAvailable(MetadataSchemaIndexSyncPort::noop));
    }

    @Bean
    @ConditionalOnBean(MetadataDictionaryManagementRepositoryPort.class)
    @ConditionalOnMissingBean(MetadataDictionaryInboundPort.class)
    public KernelMetadataDictionaryService seahorseMetadataDictionaryInboundPort(
            MetadataDictionaryManagementRepositoryPort repositoryPort) {
        return new KernelMetadataDictionaryService(repositoryPort);
    }

    @Bean
    @ConditionalOnBean(KeywordIndexMaintenanceInboundPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.keyword-index.maintenance", name = "scheduler-enabled",
            havingValue = "true")
    @ConditionalOnMissingBean
    public SeahorseKeywordIndexMaintenanceJob seahorseKeywordIndexMaintenanceJob(
            KeywordIndexMaintenanceInboundPort maintenanceInboundPort,
            ObjectProvider<DistributedLockPort> lockPort,
            @Value("${seahorse-agent.keyword-index.maintenance.doc-ids:}") String docIds,
            @Value("${seahorse-agent.keyword-index.maintenance.kb-ids:}") String kbIds,
            @Value("${seahorse-agent.keyword-index.maintenance.batch-size:50}") int batchSize) {
        return new SeahorseKeywordIndexMaintenanceJob(
                maintenanceInboundPort,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                docIds,
                kbIds,
                batchSize);
    }

    @Bean
    @ConditionalOnBean({DocumentRefreshSchedulePort.class, DocumentRefreshStateRepositoryPort.class,
            KnowledgeDocumentRepositoryPort.class, DocumentFetcherPort.class, ObjectStoragePort.class,
            KnowledgeDocumentInboundPort.class, PipelineDefinitionRepositoryPort.class, SchedulerPort.class})
    @ConditionalOnMissingBean
    public DocumentRefreshServicePorts seahorseDocumentRefreshServicePorts(
            DocumentRefreshSchedulePort schedulePort,
            DocumentRefreshStateRepositoryPort stateRepositoryPort,
            KnowledgeDocumentRepositoryPort documentRepositoryPort,
            DocumentFetcherPort documentFetcherPort,
            ObjectStoragePort objectStoragePort,
            KnowledgeDocumentInboundPort documentInboundPort,
            PipelineDefinitionRepositoryPort pipelineRepositoryPort,
            SchedulerPort schedulerPort,
            ObjectProvider<DistributedLockPort> lockPort) {
        return new DocumentRefreshServicePorts(
                schedulePort,
                stateRepositoryPort,
                documentRepositoryPort,
                documentFetcherPort,
                objectStoragePort,
                documentInboundPort,
                pipelineRepositoryPort,
                schedulerPort,
                lockPort.getIfAvailable(DistributedLockPort::noop));
    }

    @Bean
    @ConditionalOnBean(DocumentRefreshServicePorts.class)
    @ConditionalOnMissingBean(DocumentRefreshInboundPort.class)
    public KernelDocumentRefreshService seahorseDocumentRefreshInboundPort(
            DocumentRefreshServicePorts servicePorts) {
        return new KernelDocumentRefreshService(servicePorts);
    }

    @Bean
    @ConditionalOnBean(DocumentRefreshInboundPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.document-refresh", name = "scheduler-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SeahorseDocumentRefreshJob seahorseDocumentRefreshJob(
            DocumentRefreshInboundPort refreshInboundPort,
            ObjectProvider<DistributedLockPort> lockPort,
            @Value("${seahorse-agent.document-refresh.batch-size:20}") int batchSize) {
        return new SeahorseDocumentRefreshJob(refreshInboundPort,
                lockPort.getIfAvailable(DistributedLockPort::noop), batchSize);
    }

    @Bean
    @ConditionalOnBean({KnowledgeDocumentInboundPort.class, PipelineDefinitionRepositoryPort.class})
    @ConditionalOnMissingBean
    public KernelKnowledgeDocumentChunkHandler seahorseKernelKnowledgeDocumentChunkHandler(
            KnowledgeDocumentInboundPort documentInboundPort,
            PipelineDefinitionRepositoryPort pipelineDefinitionRepositoryPort) {
        return new KernelKnowledgeDocumentChunkHandler(documentInboundPort, pipelineDefinitionRepositoryPort);
    }

    @Bean
    @ConditionalOnBean({KernelKnowledgeDocumentChunkHandler.class, MessageSubscriptionPort.class})
    @ConditionalOnMissingBean(name = "seahorseKnowledgeDocumentChunkSubscription")
    public AutoCloseable seahorseKnowledgeDocumentChunkSubscription(
            KernelKnowledgeDocumentChunkHandler chunkHandler,
            MessageSubscriptionPort subscriptionPort,
            @Value("${seahorse-agent.adapters.mq.pulsar.topics.knowledge-document-chunk:"
                    + KernelKnowledgeDocumentService.DEFAULT_CHUNK_TOPIC + "}") String chunkTopic) {
        return subscriptionPort.subscribe(chunkTopic, "seahorse-knowledge-document-chunk",
                com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentChunkEvent.class,
                chunkHandler::handle);
    }

    @Bean
    @ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
    @ConditionalOnMissingBean(MemoryEnginePort.class)
    public MemoryEnginePort seahorseDefaultMemoryEnginePort(
            ShortTermMemoryPort shortTermMemoryPort,
            LongTermMemoryPort longTermMemoryPort,
            SemanticMemoryPort semanticMemoryPort,
            ObjectProvider<ObjectMapper> objectMapperProvider) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new DefaultMemoryEnginePort(
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelMemoryEngine seahorseKernelMemoryEngine(ObjectProvider<MemoryEnginePort> memoryEnginePort) {
        return new KernelMemoryEngine(memoryEnginePort.getIfAvailable(MemoryEnginePort::noop));
    }

    @Bean
    @ConditionalOnBean({WorkingMemoryPort.class, ShortTermMemoryPort.class, LongTermMemoryPort.class,
            SemanticMemoryPort.class})
    @ConditionalOnMissingBean
    public MemoryManagementServicePorts seahorseMemoryManagementServicePorts(
            WorkingMemoryPort workingMemoryPort,
            ShortTermMemoryPort shortTermMemoryPort,
            LongTermMemoryPort longTermMemoryPort,
            SemanticMemoryPort semanticMemoryPort,
            ObjectProvider<MemoryQualitySnapshotRepositoryPort> qualitySnapshotRepositoryPort,
            ObjectProvider<MemoryConflictLogRepositoryPort> conflictLogRepositoryPort) {
        return new MemoryManagementServicePorts(
                workingMemoryPort,
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                qualitySnapshotRepositoryPort.getIfAvailable(MemoryQualitySnapshotRepositoryPort::empty),
                conflictLogRepositoryPort.getIfAvailable(MemoryConflictLogRepositoryPort::empty));
    }

    @Bean
    @ConditionalOnBean(MemoryManagementServicePorts.class)
    @ConditionalOnMissingBean(MemoryManagementInboundPort.class)
    public KernelMemoryManagementService seahorseMemoryManagementInboundPort(
            MemoryManagementServicePorts servicePorts) {
        return new KernelMemoryManagementService(servicePorts);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryInferencePort.class)
    public MemoryInferencePort seahorseRuleBasedMemoryCandidateExtractor() {
        return new RuleBasedMemoryCandidateExtractor();
    }

    @Bean
    @ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
    @ConditionalOnMissingBean
    public MemoryGovernanceServicePorts seahorseMemoryGovernanceServicePorts(
            ShortTermMemoryPort shortTermMemoryPort,
            LongTermMemoryPort longTermMemoryPort,
            SemanticMemoryPort semanticMemoryPort,
            ObjectProvider<MemoryEnginePort> memoryEnginePort,
            ObjectProvider<MemoryInferencePort> memoryInferencePort) {
        return new MemoryGovernanceServicePorts(
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                memoryEnginePort.getIfAvailable(MemoryEnginePort::noop),
                memoryInferencePort.getIfAvailable(MemoryInferencePort::noop));
    }

    @Bean
    @ConditionalOnBean(MemoryGovernanceServicePorts.class)
    @ConditionalOnMissingBean(MemoryGovernanceInboundPort.class)
    public KernelMemoryGovernanceService seahorseMemoryGovernanceInboundPort(
            MemoryGovernanceServicePorts servicePorts,
            @Value("${seahorse-agent.memory.long-term-importance-threshold:0.6}")
            double promotionThreshold,
            @Value("${seahorse-agent.memory.inference-enabled:false}")
            boolean inferenceEnabled) {
        return new KernelMemoryGovernanceService(servicePorts, promotionThreshold, inferenceEnabled);
    }

    @Bean
    @ConditionalOnBean(MemoryGovernanceInboundPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.governance", name = "scheduler-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SeahorseMemoryGovernanceJob seahorseMemoryGovernanceJob(
            MemoryGovernanceInboundPort governanceInboundPort,
            ObjectProvider<DistributedLockPort> lockPort) {
        return new SeahorseMemoryGovernanceJob(governanceInboundPort,
                lockPort.getIfAvailable(DistributedLockPort::noop));
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelModelRoutingService seahorseKernelModelRoutingService(ObjectProvider<ChatModelPort> chatModelPort,
                                                                       ObjectProvider<StreamingChatModelPort> streamingChatModelPort,
                                                                       ObjectProvider<ModelProviderPort> modelProviderPort,
                                                                       ObjectProvider<EmbeddingModelPort> embeddingModelPort,
                                                                       ObjectProvider<RerankModelPort> rerankModelPort,
                                                                       ObjectProvider<TokenCounterPort> tokenCounterPort,
                                                                       ObjectProvider<ModelHealthPort> modelHealthPort,
                                                                       ObjectProvider<ModelRoutingStatePort> routingStatePort) {
        return new KernelModelRoutingService(
                chatModelPort.getIfAvailable(ChatModelPort::noop),
                streamingChatModelPort.getIfAvailable(StreamingChatModelPort::noop),
                modelProviderPort.getIfAvailable(ModelProviderPort::noop),
                embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop),
                rerankModelPort.getIfAvailable(RerankModelPort::noop),
                tokenCounterPort.getIfAvailable(TokenCounterPort::approximate),
                modelHealthPort.getIfAvailable(ModelHealthPort::noop),
                routingStatePort.getIfAvailable(ModelRoutingStatePort::firstAvailable));
    }

    private static KeyValueCachePort noopCachePort() {
        return new KeyValueCachePort() {
            @Override
            public java.util.Optional<String> get(String key) {
                return java.util.Optional.empty();
            }

            @Override
            public void set(String key, String value, java.time.Duration ttl) {
            }

            @Override
            public boolean delete(String key) {
                return false;
            }
        };
    }

    private static VectorIndexPort noopVectorIndexPort() {
        return new VectorIndexPort() {
            @Override
            public void indexDocumentChunks(String collectionName, String docId,
                                            java.util.List<com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk> chunks) {
            }

            @Override
            public void updateChunk(String collectionName, String docId,
                                    com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk chunk) {
            }

            @Override
            public void deleteDocumentVectors(String collectionName, String docId) {
            }

            @Override
            public void deleteChunkById(String collectionName, String chunkId) {
            }

            @Override
            public void deleteChunksByIds(String collectionName, java.util.List<String> chunkIds) {
            }
        };
    }
}

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

import com.miracle.ai.seahorse.agent.adapters.spring.config.AgentAdapterProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.config.AgentKernelProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.config.AgentPluginProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.metadata.MetadataIndexCompensationAdapter;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryEnginePort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryEngine;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryGovernanceService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KnowledgeDocumentVectorPorts;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryDecayOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryEngineOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryGovernanceServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.memory.RuleBasedMemoryCandidateExtractor;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryInferencePort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryManagementServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelVersionQualityComparisonService;
import com.miracle.ai.seahorse.agent.kernel.application.model.KernelModelRoutingService;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceService;
import com.miracle.ai.seahorse.agent.kernel.application.trace.RagTraceRecorderOptions;
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
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.FinalTruncatePostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.IntentDirectedSearchFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.KeywordSearchChannelFeature;
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
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.trace.RagTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermExpansionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.EnhancementPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.EnrichmentPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshStateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryMaintenancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataIndexCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelRoutingStatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AdapterHealthIndicatorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

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
@AutoConfigureAfter(SeahorseAgentNativeAdapterAutoConfiguration.class)
@EnableConfigurationProperties({
        AgentKernelProperties.class,
        AgentPluginProperties.class,
        AgentAdapterProperties.class
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        SeahorseAgentKernelAuthAutoConfiguration.class,
        SeahorseAgentKernelChatAutoConfiguration.class,
        SeahorseAgentKernelDocumentRefreshAutoConfiguration.class,
        SeahorseAgentKernelKnowledgeAutoConfiguration.class,
        SeahorseAgentKernelKeywordAutoConfiguration.class,
        SeahorseAgentKernelMemoryAutoConfiguration.class,
        SeahorseAgentKernelMetadataAutoConfiguration.class,
        SeahorseAgentKernelModelAutoConfiguration.class,
        SeahorseAgentKernelOpsAutoConfiguration.class,
        SeahorseAgentKernelRetrievalAutoConfiguration.class,
        SeahorseAgentKernelTraceAutoConfiguration.class
})
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
            ObjectProvider<ChatModelPort> chatModelPort,
            ObjectProvider<ObservationPort> observationPort) {
        MetadataExtractorNodeFeature feature = new MetadataExtractorNodeFeature(
                schemaRegistryPort.getIfAvailable(MetadataSchemaRegistryPort::empty),
                chatModelPort.getIfAvailable(ChatModelPort::noop),
                observationPort.getIfAvailable());
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), IngestionNodeFeature.class,
                FeatureType.INGESTION_NODE, feature.order(), true), feature);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public MetadataNormalizerNodeFeature seahorseMetadataNormalizerNodeFeature(
            ExtensionRegistry extensionRegistry,
            ObjectProvider<MetadataSchemaRegistryPort> schemaRegistryPort,
            ObjectProvider<MetadataDictionaryPort> dictionaryPort,
            ObjectProvider<ObservationPort> observationPort) {
        MetadataNormalizerNodeFeature feature = new MetadataNormalizerNodeFeature(
                schemaRegistryPort.getIfAvailable(MetadataSchemaRegistryPort::empty),
                dictionaryPort.getIfAvailable(MetadataDictionaryPort::noop),
                observationPort.getIfAvailable());
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
            ObjectProvider<MetadataCanonicalWritePort> canonicalWritePort,
            ObjectProvider<ObservationPort> observationPort) {
        MetadataValidatorNodeFeature feature = new MetadataValidatorNodeFeature(
                schemaRegistryPort.getIfAvailable(MetadataSchemaRegistryPort::empty),
                resultRepositoryPort.getIfAvailable(MetadataExtractionResultRepositoryPort::noop),
                reviewQueuePort.getIfAvailable(MetadataReviewQueuePort::noop),
                quarantinePort.getIfAvailable(MetadataQuarantinePort::noop),
                canonicalWritePort.getIfAvailable(MetadataCanonicalWritePort::noop),
                observationPort.getIfAvailable());
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
    @ConditionalOnBean({MetadataQualityInboundPort.class, RetrievalEvaluationInboundPort.class})
    @ConditionalOnMissingBean(VersionQualityComparisonInboundPort.class)
    public KernelVersionQualityComparisonService seahorseVersionQualityComparisonInboundPort(
            MetadataQualityInboundPort metadataQualityInboundPort,
            RetrievalEvaluationInboundPort retrievalEvaluationInboundPort,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelVersionQualityComparisonService(
                metadataQualityInboundPort,
                retrievalEvaluationInboundPort,
                observationPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean({KeywordIndexMaintenanceInboundPort.class, KnowledgeDocumentRepositoryPort.class,
            KnowledgeDocumentVectorPorts.class})
    @ConditionalOnMissingBean(MetadataIndexCompensationPort.class)
    public MetadataIndexCompensationPort seahorseMetadataIndexCompensationPort(
            KnowledgeDocumentRepositoryPort documentRepositoryPort,
            KeywordIndexMaintenanceInboundPort keywordIndexMaintenanceInboundPort,
            KnowledgeDocumentVectorPorts documentVectorPorts,
            ObjectProvider<MetadataSchemaRegistryPort> schemaRegistryPort,
            ObjectProvider<MetadataBackfillInboundPort> backfillInboundPort) {
        return new MetadataIndexCompensationAdapter(
                documentRepositoryPort,
                keywordIndexMaintenanceInboundPort,
                documentVectorPorts,
                schemaRegistryPort.getIfAvailable(MetadataSchemaRegistryPort::empty),
                backfillInboundPort.getIfAvailable());
    }

}

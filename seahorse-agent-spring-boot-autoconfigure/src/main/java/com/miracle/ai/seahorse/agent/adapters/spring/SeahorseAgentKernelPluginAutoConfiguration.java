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

import com.miracle.ai.seahorse.agent.adapters.spring.config.AgentPluginProperties;
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
import com.miracle.ai.seahorse.agent.kernel.plugin.AgentFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.DefaultExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionDescriptor;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureHealthAggregator;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureType;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.EnhancementPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.EnrichmentPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AdapterHealthIndicatorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 插件与 Feature 注册自动配置。
 *
 * <p>该配置聚合 Feature 激活上下文、扩展注册表和默认 Feature 注册逻辑，避免主 kernel 配置继续
 * 承担插件注册职责。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({SeahorseAgentKernelAutoConfiguration.class, SeahorseAgentVectorAdapterAutoConfiguration.class, SeahorseAgentKnowledgeRepositoryAutoConfiguration.class, SeahorseAgentKeywordAdapterAutoConfiguration.class, SeahorseAgentAiAdapterAutoConfiguration.class})
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelPluginAutoConfiguration {

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
        register(extensionRegistry, feature, IngestionNodeFeature.class, FeatureType.INGESTION_NODE, true);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public ParserNodeFeature seahorseParserNodeFeature(ExtensionRegistry extensionRegistry,
                                                       ObjectProvider<DocumentParserPort> documentParserPort) {
        ParserNodeFeature feature = new ParserNodeFeature(documentParserPort.getIfAvailable(DocumentParserPort::plainText));
        register(extensionRegistry, feature, IngestionNodeFeature.class, FeatureType.INGESTION_NODE, true);
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
        register(extensionRegistry, feature, IngestionNodeFeature.class, FeatureType.INGESTION_NODE, true);
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
        register(extensionRegistry, feature, IngestionNodeFeature.class, FeatureType.INGESTION_NODE, true);
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
        register(extensionRegistry, feature, IngestionNodeFeature.class, FeatureType.INGESTION_NODE, true);
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
        register(extensionRegistry, feature, IngestionNodeFeature.class, FeatureType.INGESTION_NODE, true);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public EnricherNodeFeature seahorseEnricherNodeFeature(ExtensionRegistry extensionRegistry,
                                                           ObjectProvider<ChatModelPort> chatModelPort,
                                                           EnrichmentPromptPort promptPort) {
        EnricherNodeFeature feature = new EnricherNodeFeature(
                chatModelPort.getIfAvailable(ChatModelPort::noop), promptPort);
        register(extensionRegistry, feature, IngestionNodeFeature.class, FeatureType.INGESTION_NODE, true);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public ChunkerNodeFeature seahorseChunkerNodeFeature(ExtensionRegistry extensionRegistry,
                                                         ObjectProvider<EmbeddingModelPort> embeddingModelPort) {
        ChunkerNodeFeature feature = new ChunkerNodeFeature(embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop));
        register(extensionRegistry, feature, IngestionNodeFeature.class, FeatureType.INGESTION_NODE, true);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public EmbedderNodeFeature seahorseEmbedderNodeFeature(ExtensionRegistry extensionRegistry,
                                                           ObjectProvider<EmbeddingModelPort> embeddingModelPort) {
        EmbedderNodeFeature feature = new EmbedderNodeFeature(embeddingModelPort.getIfAvailable(EmbeddingModelPort::noop));
        register(extensionRegistry, feature, IngestionNodeFeature.class, FeatureType.INGESTION_NODE, true);
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
        register(extensionRegistry, feature, IngestionNodeFeature.class, FeatureType.INGESTION_NODE, true);
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
        register(extensionRegistry, feature, SearchChannelFeature.class, FeatureType.SEARCH_CHANNEL, true);
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
        register(extensionRegistry, feature, SearchChannelFeature.class, FeatureType.SEARCH_CHANNEL, false);
        return feature;
    }

    @Bean
    @ConditionalOnBean({ExtensionRegistry.class, KeywordSearchPort.class})
    public KeywordSearchChannelFeature seahorseKeywordSearchChannelFeature(ExtensionRegistry extensionRegistry,
                                                                          KeywordSearchPort keywordSearchPort) {
        KeywordSearchChannelFeature feature = new KeywordSearchChannelFeature(keywordSearchPort);
        register(extensionRegistry, feature, SearchChannelFeature.class, FeatureType.SEARCH_CHANNEL, false);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public MetadataGuardPostProcessorFeature seahorseMetadataGuardPostProcessorFeature(
            ExtensionRegistry extensionRegistry) {
        MetadataGuardPostProcessorFeature feature = new MetadataGuardPostProcessorFeature();
        register(extensionRegistry, feature, SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, true);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public RrfFusionPostProcessorFeature seahorseRrfFusionPostProcessorFeature(
            ExtensionRegistry extensionRegistry,
            ObjectProvider<ObservationPort> observationPort) {
        RrfFusionPostProcessorFeature feature = new RrfFusionPostProcessorFeature(observationPort.getIfAvailable());
        register(extensionRegistry, feature, SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, false);
        return feature;
    }

    @Bean
    @ConditionalOnBean({ExtensionRegistry.class, RerankModelPort.class})
    public RerankPostProcessorFeature seahorseRerankPostProcessorFeature(ExtensionRegistry extensionRegistry,
                                                                         RerankModelPort rerankModelPort,
                                                                         ObjectProvider<ObservationPort> observationPort) {
        RerankPostProcessorFeature feature = new RerankPostProcessorFeature(rerankModelPort,
                observationPort.getIfAvailable());
        register(extensionRegistry, feature, SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, false);
        return feature;
    }

    @Bean
    @ConditionalOnBean(ExtensionRegistry.class)
    public FinalTruncatePostProcessorFeature seahorseFinalTruncatePostProcessorFeature(
            ExtensionRegistry extensionRegistry,
            ObjectProvider<ObservationPort> observationPort) {
        FinalTruncatePostProcessorFeature feature = new FinalTruncatePostProcessorFeature(
                observationPort.getIfAvailable());
        register(extensionRegistry, feature, SearchResultPostProcessorFeature.class,
                FeatureType.SEARCH_RESULT_POST_PROCESSOR, false);
        return feature;
    }

    private static void register(ExtensionRegistry extensionRegistry,
                                 AgentFeature feature,
                                 Class<?> contractType,
                                 FeatureType featureType,
                                 boolean defaultEnabled) {
        extensionRegistry.register(new ExtensionDescriptor(feature.name(), contractType,
                featureType, feature.order(), defaultEnabled), feature);
    }
}

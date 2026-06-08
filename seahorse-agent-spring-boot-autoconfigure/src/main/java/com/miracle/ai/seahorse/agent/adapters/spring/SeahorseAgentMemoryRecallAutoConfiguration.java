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
import com.miracle.ai.seahorse.agent.adapters.spring.properties.MemoryProperties;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryRetrievalPipeline;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryRouter;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryEngineOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.ClasspathMemoryRecallGoldenCaseRepository;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.GraphMemoryRecallChannel;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.HybridMemoryRecallPipeline;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.KeywordMemoryRecallChannel;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.LayeredScoredMemoryVectorPort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.MemoryRecallEvaluationService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.MemoryRecallGoldenHarnessService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.ModelMemoryRecallReranker;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.RrfMemoryFusion;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.VectorMemoryRecallChannel;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.VectorSearchScoredMemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFusionPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallChannelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallFusionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallGoldenCaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRerankerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenHarnessInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * Spec §9.2：memory recall 子能力域 auto configuration（最后一刀）。
 *
 * <p>从原 {@link SeahorseAgentKernelMemoryAutoConfiguration} 拆出 14 个 recall 相关 bean，
 * 聚焦"混合召回 + 重排 + 多通道融合 + golden harness"能力域：
 *
 * <ul>
 *     <li>{@link DefaultMemoryRouter}：路由决策（layer / track 选择）。</li>
 *     <li>{@link DefaultMemoryRetrievalPipeline}：传统单通道 fallback（hybrid 关闭时启用）。</li>
 *     <li>{@link MemoryFusionPolicy} + {@link RrfMemoryFusion}：RRF 融合策略与默认 fusion port。</li>
 *     <li>{@link ModelMemoryRecallReranker} + noop fallback。</li>
 *     <li>{@link VectorSearchScoredMemoryVectorPort} + {@link LayeredScoredMemoryVectorPort}：
 *         两种 vector port 实现，按 vector-search-enabled 切换。</li>
 *     <li>3 个 recall channel（vector / keyword / graph）。</li>
 *     <li>{@link HybridMemoryRecallPipeline}：默认 hybrid recall 主线。</li>
 *     <li>{@link MemoryRecallEvaluationService} + {@link MemoryRecallGoldenHarnessService} +
 *         {@link ClasspathMemoryRecallGoldenCaseRepository}：召回评测套件。</li>
 * </ul>
 *
 * <p>所有 @ConditionalOnBean / @ConditionalOnProperty / @ConditionalOnExpression 与
 * @ConditionalOnMissingBean 完全保留，property key 与历史一致。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(SeahorseAgentKernelMemoryAutoConfiguration.class)
@EnableConfigurationProperties(MemoryProperties.class)
public class SeahorseAgentMemoryRecallAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(MemoryRouterPort.class)
    public DefaultMemoryRouter seahorseDefaultMemoryRouter() {
        return new DefaultMemoryRouter();
    }

    @Bean
    @ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
    @ConditionalOnMissingBean(MemoryRetrievalPipelinePort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.recall", name = "hybrid-enabled",
            havingValue = "false")
    public DefaultMemoryRetrievalPipeline seahorseMemoryRetrievalPipeline(
            ShortTermMemoryPort shortTermMemoryPort,
            LongTermMemoryPort longTermMemoryPort,
            SemanticMemoryPort semanticMemoryPort,
            ObjectProvider<ProfileMemoryPort> profileMemoryPort,
            ObjectProvider<CorrectionLedgerPort> correctionLedgerPort,
            ObjectProvider<MemoryRouterPort> memoryRouterPort,
            ObjectProvider<MemoryVectorPort> memoryVectorPort,
            ObjectProvider<MemoryBusinessDocumentRetrieverPort> businessDocumentRetrieverPort,
            ObjectProvider<MemoryLifecyclePort> memoryLifecyclePort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            MemoryProperties memoryProperties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        MemoryEngineOptions options = new MemoryEngineOptions(
                memoryProperties.getShortTermLimit(),
                memoryProperties.getLongTermLimit(),
                memoryProperties.getSemanticLimit(),
                memoryProperties.isCaptureEnabled());
        return new DefaultMemoryRetrievalPipeline(
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                objectMapper,
                options,
                profileMemoryPort.getIfAvailable(ProfileMemoryPort::noop),
                correctionLedgerPort.getIfAvailable(CorrectionLedgerPort::noop),
                memoryRouterPort.getIfAvailable(DefaultMemoryRouter::new),
                memoryVectorPort.getIfAvailable(MemoryVectorPort::noop),
                businessDocumentRetrieverPort.getIfAvailable(MemoryBusinessDocumentRetrieverPort::noop),
                memoryLifecyclePort.getIfAvailable(MemoryLifecyclePort::noop));
    }

    @Bean
    @ConditionalOnMissingBean(MemoryFusionPolicy.class)
    public MemoryFusionPolicy seahorseMemoryFusionPolicy(MemoryProperties memoryProperties) {
        MemoryProperties.Recall recall = memoryProperties.getRecall();
        return new MemoryFusionPolicy(
                recall.getRrfK(),
                recall.getDecayLambda(),
                recall.getFinalTopK(),
                recall.isTimeDecayEnabled(),
                recall.getChannelTimeoutMs(),
                Map.copyOf(recall.getChannelWeights()));
    }

    @Bean
    @ConditionalOnMissingBean(MemoryRecallFusionPort.class)
    public RrfMemoryFusion seahorseRrfMemoryFusion() {
        return new RrfMemoryFusion();
    }

    @Bean
    @ConditionalOnBean(RerankModelPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.recall", name = "rerank-enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(MemoryRecallRerankerPort.class)
    public ModelMemoryRecallReranker seahorseModelMemoryRecallReranker(
            RerankModelPort rerankModelPort,
            MemoryProperties memoryProperties) {
        MemoryProperties.Recall recall = memoryProperties.getRecall();
        return new ModelMemoryRecallReranker(rerankModelPort,
                recall.getRerankModel(),
                recall.getRerankInputTopK(),
                recall.getRerankMaxTextChars());
    }

    @Bean
    @ConditionalOnMissingBean(MemoryRecallRerankerPort.class)
    public MemoryRecallRerankerPort seahorseMemoryRecallRerankerPort() {
        return MemoryRecallRerankerPort.noop();
    }

    @Bean
    @ConditionalOnBean({
            ShortTermMemoryPort.class,
            LongTermMemoryPort.class,
            SemanticMemoryPort.class,
            VectorSearchPort.class,
            EmbeddingModelPort.class
    })
    @ConditionalOnExpression("'${seahorse-agent.memory.recall.hybrid-enabled:true}' == 'true'"
            + " && '${seahorse-agent.memory.recall.vector-search-enabled:false}' == 'true'")
    @ConditionalOnMissingBean(ScoredMemoryVectorPort.class)
    public VectorSearchScoredMemoryVectorPort seahorseVectorSearchScoredMemoryVectorPort(
            VectorSearchPort vectorSearchPort,
            EmbeddingModelPort embeddingModelPort,
            MemoryProperties memoryProperties) {
        MemoryProperties.Recall recall = memoryProperties.getRecall();
        return new VectorSearchScoredMemoryVectorPort(
                vectorSearchPort,
                embeddingModelPort,
                recall.getVectorCollection(),
                recall.getEmbeddingModel());
    }

    @Bean
    @ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.recall", name = "hybrid-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(ScoredMemoryVectorPort.class)
    public ScoredMemoryVectorPort seahorseScoredMemoryVectorPort(ObjectProvider<MemoryVectorPort> memoryVectorPort,
                                                                 ShortTermMemoryPort shortTermPort,
                                                                 LongTermMemoryPort longTermPort,
                                                                 SemanticMemoryPort semanticPort) {
        return new LayeredScoredMemoryVectorPort(
                memoryVectorPort.getIfAvailable(MemoryVectorPort::noop),
                shortTermPort,
                longTermPort,
                semanticPort);
    }

    @Bean
    @ConditionalOnBean(ScoredMemoryVectorPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.recall", name = "hybrid-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "seahorseVectorMemoryRecallChannel")
    public VectorMemoryRecallChannel seahorseVectorMemoryRecallChannel(ScoredMemoryVectorPort vectorPort) {
        return new VectorMemoryRecallChannel(vectorPort);
    }

    @Bean
    @ConditionalOnBean(MemoryKeywordSearchPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.recall", name = "hybrid-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "seahorseKeywordMemoryRecallChannel")
    public KeywordMemoryRecallChannel seahorseKeywordMemoryRecallChannel(MemoryKeywordSearchPort keywordSearchPort) {
        return new KeywordMemoryRecallChannel(keywordSearchPort);
    }

    @Bean
    @ConditionalOnBean(MemoryGraphPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.recall", name = "hybrid-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(name = "seahorseGraphMemoryRecallChannel")
    public GraphMemoryRecallChannel seahorseGraphMemoryRecallChannel(
            MemoryGraphPort graphPort,
            MemoryProperties memoryProperties) {
        return new GraphMemoryRecallChannel(graphPort, memoryProperties.getRecall().getGraphMaxHops());
    }

    @Bean
    @ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.recall", name = "hybrid-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(MemoryRetrievalPipelinePort.class)
    public HybridMemoryRecallPipeline seahorseHybridMemoryRecallPipeline(
            ShortTermMemoryPort shortTermMemoryPort,
            LongTermMemoryPort longTermMemoryPort,
            SemanticMemoryPort semanticMemoryPort,
            ObjectProvider<ProfileMemoryPort> profileMemoryPort,
            ObjectProvider<CorrectionLedgerPort> correctionLedgerPort,
            ObjectProvider<MemoryRouterPort> memoryRouterPort,
            ObjectProvider<MemoryBusinessDocumentRetrieverPort> businessDocumentRetrieverPort,
            ObjectProvider<MemoryLifecyclePort> memoryLifecyclePort,
            ObjectProvider<MemoryAliasPort> memoryAliasPort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            List<MemoryRecallChannelPort> recallChannels,
            MemoryRecallFusionPort recallFusionPort,
            MemoryRecallRerankerPort recallRerankerPort,
            MemoryFusionPolicy fusionPolicy,
            ObjectProvider<MemoryTraceRecorder> traceRecorder,
            ObjectProvider<ObservationPort> observationPort,
            @Qualifier("ragRetrievalThreadPoolExecutor") ObjectProvider<Executor> recallExecutor,
            MemoryProperties memoryProperties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        return new HybridMemoryRecallPipeline(
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                objectMapper,
                profileMemoryPort.getIfAvailable(ProfileMemoryPort::noop),
                correctionLedgerPort.getIfAvailable(CorrectionLedgerPort::noop),
                memoryRouterPort.getIfAvailable(DefaultMemoryRouter::new),
                businessDocumentRetrieverPort.getIfAvailable(MemoryBusinessDocumentRetrieverPort::noop),
                memoryLifecyclePort.getIfAvailable(MemoryLifecyclePort::noop),
                recallChannels,
                recallFusionPort,
                fusionPolicy,
                memoryProperties.getRecall().getChannelTopK(),
                traceRecorder.getIfAvailable(MemoryTraceRecorder::noop),
                recallExecutor.getIfAvailable(),
                memoryAliasPort.getIfAvailable(MemoryAliasPort::noop),
                recallRerankerPort,
                observationPort.getIfAvailable(ObservationPort::noop));
    }

    @Bean
    @ConditionalOnBean(MemoryRetrievalPipelinePort.class)
    @ConditionalOnMissingBean(MemoryRecallEvaluationInboundPort.class)
    public MemoryRecallEvaluationService seahorseMemoryRecallEvaluationInboundPort(
            MemoryRetrievalPipelinePort retrievalPipelinePort,
            ObjectProvider<ObservationPort> observationPort) {
        return new MemoryRecallEvaluationService(
                retrievalPipelinePort,
                observationPort.getIfAvailable(ObservationPort::noop));
    }

    @Bean
    @ConditionalOnMissingBean(MemoryRecallGoldenCaseRepositoryPort.class)
    public ClasspathMemoryRecallGoldenCaseRepository seahorseClasspathGoldenCaseRepository(
            ObjectProvider<ObjectMapper> objectMapper) {
        return new ClasspathMemoryRecallGoldenCaseRepository(
                objectMapper.getIfAvailable(ObjectMapper::new),
                Thread.currentThread().getContextClassLoader(),
                ClasspathMemoryRecallGoldenCaseRepository.DEFAULT_ROOT);
    }

    @Bean
    @ConditionalOnBean(MemoryRetrievalPipelinePort.class)
    @ConditionalOnMissingBean(MemoryRecallGoldenHarnessInboundPort.class)
    public MemoryRecallGoldenHarnessService seahorseMemoryRecallGoldenHarnessService(
            MemoryRecallEvaluationInboundPort evaluationPort,
            ObjectProvider<MemoryRecallGoldenCaseRepositoryPort> repositoryPort,
            ObjectProvider<ObservationPort> observationPort) {
        return new MemoryRecallGoldenHarnessService(
                repositoryPort.getIfAvailable(MemoryRecallGoldenCaseRepositoryPort::empty),
                evaluationPort,
                observationPort.getIfAvailable(ObservationPort::noop));
    }
}

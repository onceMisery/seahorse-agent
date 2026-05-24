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
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultContextWeaver;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryEnginePort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryRetrievalPipeline;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryRouter;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryEngine;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryGovernanceService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryReviewService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryTraceQueryService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.InMemoryMemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryDecayOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryCaptureRules;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryEngineOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryGovernanceServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryManagementServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryOutboxRelayService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.RuleBasedMemoryCandidateExtractor;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.DefaultMemoryAggregationService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.ExplicitCueMemoryAggregationTopicShiftDetector;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.InMemoryMemoryAggregationBufferPort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.KernelMemoryAggregationControlService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationTopicShiftDetector;
import com.miracle.ai.seahorse.agent.kernel.application.memory.trace.InMemoryMemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.DefaultMemoryMaintenanceService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryAliasResolutionOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryAliasResolutionService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryCompactionOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryCompactionService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryGarbageCollectionOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryGarbageCollectionService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.outbox.GraphMemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.kernel.application.memory.outbox.KeywordMemoryOutboxTaskHandler;
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
import com.miracle.ai.seahorse.agent.kernel.application.memory.outbox.VectorMemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryAggregationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenHarnessInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationSchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummarizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFusionPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryInferencePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskTypes;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallChannelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallGoldenCaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallFusionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRerankerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryMaintenancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.adapters.spring.properties.MemoryCaptureRuleProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.properties.MemoryProperties;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;

/**
 * 内核记忆能力自动配置。
 *
 * <p>四层记忆引擎、管理服务、治理服务和治理调度属于同一内核职责域，独立配置后主 kernel 配置不再承载记忆闭环细节。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        SeahorseAgentKernelAutoConfiguration.class,
        SeahorseAgentKernelRetrievalAutoConfiguration.class,
        SeahorseAgentMemoryRepositoryAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties({MemoryProperties.class, MemoryCaptureRuleProperties.class})
public class SeahorseAgentKernelMemoryAutoConfiguration {

    private static final String MEMORY_REFINER_ENABLED_PROPERTY = "seahorse-agent.memory.refiner.enabled";

    @Bean
    @ConditionalOnMissingBean(MemoryAggregationPolicy.class)
    public MemoryAggregationPolicy seahorseMemoryAggregationPolicy(MemoryProperties properties) {
        MemoryProperties.Aggregation aggregation = properties.getAggregation();
        return new MemoryAggregationPolicy(
                aggregation.isEnabled(),
                aggregation.getIdleFlushMillis(),
                aggregation.getMaxTurns(),
                aggregation.getMaxTokens(),
                aggregation.getMaxContextBlocks(),
                aggregation.getBufferTtlMillis(),
                aggregation.isCaptureOnError(),
                aggregation.isTopicShiftFlushEnabled());
    }

    @Bean
    @ConditionalOnMissingBean(MemoryAggregationTopicShiftDetector.class)
    public ExplicitCueMemoryAggregationTopicShiftDetector seahorseMemoryAggregationTopicShiftDetector() {
        return new ExplicitCueMemoryAggregationTopicShiftDetector();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.aggregation", name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(MemoryAggregationBufferPort.class)
    public InMemoryMemoryAggregationBufferPort seahorseInMemoryAggregationBufferPort(
            MemoryAggregationPolicy policy) {
        return new InMemoryMemoryAggregationBufferPort(policy);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryAggregationSchedulerPort.class)
    public MemoryAggregationSchedulerPort seahorseMemoryAggregationSchedulerPort() {
        return MemoryAggregationSchedulerPort.noop();
    }

    @Bean
    @ConditionalOnMissingBean(MemoryTraceRecorder.class)
    public MemoryTraceRecorder seahorseMemoryTraceRecorder(
            @Value("${seahorse-agent.memory.trace.max-events:1000}") int maxEvents) {
        return new InMemoryMemoryTraceRecorder(maxEvents);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryTraceInboundPort.class)
    public KernelMemoryTraceQueryService seahorseMemoryTraceInboundPort(MemoryTraceRecorder traceRecorder) {
        return new KernelMemoryTraceQueryService(traceRecorder);
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.aggregation", name = "enabled",
            havingValue = "true")
    @ConditionalOnMissingBean(MemoryAggregationServicePort.class)
    public DefaultMemoryAggregationService seahorseMemoryAggregationService(
            MemoryAggregationPolicy policy,
            ObjectProvider<MemoryAggregationBufferPort> aggregationBufferPort,
            ObjectProvider<MemoryAggregationSchedulerPort> schedulerPort,
            ObjectProvider<MemoryIngestionWorkflowPort> ingestionWorkflowPort,
            ObjectProvider<MemoryTraceRecorder> traceRecorder,
            ObjectProvider<MemoryAggregationTopicShiftDetector> topicShiftDetector,
            ObjectProvider<ObservationPort> observationPort) {
        return new DefaultMemoryAggregationService(
                policy,
                aggregationBufferPort.getIfAvailable(MemoryAggregationBufferPort::noop),
                schedulerPort.getIfAvailable(MemoryAggregationSchedulerPort::noop),
                ingestionWorkflowPort.getIfAvailable(() -> command ->
                        com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult.ignored("noop")),
                traceRecorder.getIfAvailable(MemoryTraceRecorder::noop),
                topicShiftDetector.getIfAvailable(ExplicitCueMemoryAggregationTopicShiftDetector::new),
                observationPort.getIfAvailable(ObservationPort::noop),
                java.time.Clock.systemUTC());
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.aggregation", name = "enabled",
            havingValue = "true")
    @ConditionalOnBean(MemoryAggregationServicePort.class)
    @ConditionalOnMissingBean(MemoryAggregationInboundPort.class)
    public KernelMemoryAggregationControlService seahorseMemoryAggregationInboundPort(
            MemoryAggregationServicePort aggregationServicePort) {
        return new KernelMemoryAggregationControlService(aggregationServicePort);
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.aggregation", name = "enabled",
            havingValue = "true")
    @ConditionalOnBean(MemoryAggregationServicePort.class)
    @ConditionalOnMissingBean
    public SeahorseMemoryAggregationJob seahorseMemoryAggregationJob(
            MemoryAggregationServicePort aggregationServicePort,
            ObjectProvider<DistributedLockPort> lockPort,
            @Value("${seahorse-agent.memory.aggregation.scan-limit:100}") int scanLimit) {
        return new SeahorseMemoryAggregationJob(
                aggregationServicePort,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                scanLimit);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryPolicyConfigPort.class)
    public InMemoryMemoryPolicyConfigPort seahorseMemoryPolicyConfigPort(MemoryProperties properties) {
        MemoryProperties.Policy policy = properties.getPolicy();
        return new InMemoryMemoryPolicyConfigPort(new MemoryPolicyConfig(
                policy.getCaptureAcceptThreshold(),
                policy.getHighValueThreshold(),
                policy.getRiskRejectThreshold(),
                policy.getTokenBudget(),
                policy.isReviewEnabled(),
                policy.getRefinerDropConfidenceThreshold(),
                policy.getRefinerAutoCommitConfidenceThreshold(),
                policy.getRefinerReviewRiskThreshold(),
                MemoryPolicyConfig.defaults().enabledTracks(),
                policy.getSchemaFailureAlertThreshold(),
                policy.getOutboxBacklogAlertThreshold(),
                policy.getGreyReleaseKey()));
    }

    @Bean
    @ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
    @ConditionalOnMissingBean(MemoryEnginePort.class)
    public DefaultMemoryEnginePort seahorseDefaultMemoryEnginePort(
            ShortTermMemoryPort shortTermMemoryPort,
            LongTermMemoryPort longTermMemoryPort,
            SemanticMemoryPort semanticMemoryPort,
            ObjectProvider<ProfileMemoryPort> profileMemoryPort,
            ObjectProvider<CorrectionLedgerPort> correctionLedgerPort,
            ObjectProvider<MemoryRouterPort> memoryRouterPort,
            ObjectProvider<MemoryOperationLogPort> memoryOperationLogPort,
            ObjectProvider<MemoryVectorPort> memoryVectorPort,
            ObjectProvider<MemoryOutboxPort> memoryOutboxPort,
            ObjectProvider<MemoryBusinessDocumentRetrieverPort> businessDocumentRetrieverPort,
            ObjectProvider<MemoryLifecyclePort> memoryLifecyclePort,
            ObjectProvider<MemoryPolicyConfigPort> memoryPolicyConfigPort,
            ObjectProvider<MemoryRetrievalPipelinePort> memoryRetrievalPipelinePort,
            ObjectProvider<MemoryRefinerPort> memoryRefinerPort,
            ObjectProvider<MemoryReviewCandidatePort> memoryReviewCandidatePort,
            ObjectProvider<MemoryAliasPort> memoryAliasPort,
            ObjectProvider<MemoryReviewPolicyPort> memoryReviewPolicyPort,
            ObjectProvider<MemoryReviewFeedbackRepositoryPort> memoryReviewFeedbackRepositoryPort,
            ObjectProvider<MemoryKeywordIndexPort> memoryKeywordIndexPort,
            ObjectProvider<MemoryGraphIndexPort> memoryGraphIndexPort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            Environment environment,
            MemoryCaptureRuleProperties captureRuleProperties,
            MemoryProperties memoryProperties,
            @Value("${seahorse-agent.memory.short-term-limit:5}") int shortTermLimit,
            @Value("${seahorse-agent.memory.long-term-limit:3}") int longTermLimit,
            @Value("${seahorse-agent.memory.semantic-limit:10}") int semanticLimit,
            @Value("${seahorse-agent.memory.capture-enabled:true}") boolean captureEnabled,
            @Value("${seahorse-agent.memory.derived-index.keyword-enabled:true}") boolean keywordIndexOutboxEnabled,
            @Value("${seahorse-agent.memory.derived-index.graph-enabled:true}") boolean graphIndexOutboxEnabled) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        MemoryRefinerPort configuredRefinerPort = memoryRefinerPort.getIfAvailable();
        boolean refinerEnabled = memoryRefinerEnabled(environment, configuredRefinerPort != null);
        MemoryProperties.Refiner refiner = memoryProperties.getRefiner();
        MemoryEngineOptions options = new MemoryEngineOptions(
                shortTermLimit,
                longTermLimit,
                semanticLimit,
                captureEnabled,
                refinerEnabled,
                refiner.isFailOpen(),
                keywordIndexOutboxEnabled && memoryKeywordIndexPort.getIfAvailable() != null,
                graphIndexOutboxEnabled && memoryGraphIndexPort.getIfAvailable() != null,
                refiner.getMaxBatchOperations(),
                refiner.getMaxDeleteRatio(),
                refiner.getReadMaskPerLayerLimit(),
                refiner.getTargetZoneTurnCount(),
                refiner.getStickyAnchorLimit(),
                refiner.getFeedbackExampleLimit(),
                refiner.getStickyAnchorImportanceThreshold(),
                refiner.getStickyAnchorConfidenceThreshold());
        return new DefaultMemoryEnginePort(
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                objectMapper,
                options,
                profileMemoryPort.getIfAvailable(ProfileMemoryPort::noop),
                correctionLedgerPort.getIfAvailable(CorrectionLedgerPort::noop),
                memoryRouterPort.getIfAvailable(DefaultMemoryRouter::new),
                memoryOperationLogPort.getIfAvailable(MemoryOperationLogPort::noop),
                memoryVectorPort.getIfAvailable(MemoryVectorPort::noop),
                memoryOutboxPort.getIfAvailable(MemoryOutboxPort::noop),
                businessDocumentRetrieverPort.getIfAvailable(MemoryBusinessDocumentRetrieverPort::noop),
                memoryLifecyclePort.getIfAvailable(MemoryLifecyclePort::noop),
                memoryPolicyConfigPort.getIfAvailable(MemoryPolicyConfigPort::defaults),
                memoryRetrievalPipelinePort.getIfAvailable(),
                configuredRefinerPort == null ? MemoryRefinerPort.noop() : configuredRefinerPort,
                memoryReviewCandidatePort.getIfAvailable(MemoryReviewCandidatePort::noop),
                memoryAliasPort.getIfAvailable(MemoryAliasPort::noop),
                memoryReviewPolicyPort.getIfAvailable(MemoryReviewPolicyPort::defaults),
                memoryReviewFeedbackRepositoryPort.getIfAvailable(MemoryReviewFeedbackRepositoryPort::empty),
                captureRuleProperties == null ? MemoryCaptureRules.defaults() : captureRuleProperties.toRules());
    }

    private boolean memoryRefinerEnabled(Environment environment, boolean refinerAvailable) {
        if (environment.containsProperty(MEMORY_REFINER_ENABLED_PROPERTY)) {
            return environment.getProperty(MEMORY_REFINER_ENABLED_PROPERTY, Boolean.class, false);
        }
        return refinerAvailable;
    }

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
            @Value("${seahorse-agent.memory.short-term-limit:5}") int shortTermLimit,
            @Value("${seahorse-agent.memory.long-term-limit:3}") int longTermLimit,
            @Value("${seahorse-agent.memory.semantic-limit:10}") int semanticLimit,
            @Value("${seahorse-agent.memory.capture-enabled:true}") boolean captureEnabled) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        MemoryEngineOptions options = new MemoryEngineOptions(
                shortTermLimit,
                longTermLimit,
                semanticLimit,
                captureEnabled);
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
    public MemoryFusionPolicy seahorseMemoryFusionPolicy(
            @Value("${seahorse-agent.memory.recall.rrf-k:60}") int rrfK,
            @Value("${seahorse-agent.memory.recall.decay-lambda:0.05}") double decayLambda,
            @Value("${seahorse-agent.memory.recall.final-top-k:8}") int finalTopK,
            @Value("${seahorse-agent.memory.recall.time-decay-enabled:true}") boolean timeDecayEnabled,
            @Value("${seahorse-agent.memory.recall.channel-timeout-ms:50}") long channelTimeoutMs,
            Environment environment) {
        return new MemoryFusionPolicy(
                rrfK,
                decayLambda,
                finalTopK,
                timeDecayEnabled,
                channelTimeoutMs,
                memoryRecallChannelWeights(environment));
    }

    private Map<String, Double> memoryRecallChannelWeights(Environment environment) {
        return Binder.get(environment)
                .bind("seahorse-agent.memory.recall.channel-weights", Bindable.mapOf(String.class, Double.class))
                .orElse(Map.of());
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
            @Value("${seahorse-agent.memory.recall.rerank-model:}") String rerankModel,
            @Value("${seahorse-agent.memory.recall.rerank-input-top-k:8}") int inputTopK,
            @Value("${seahorse-agent.memory.recall.rerank-max-text-chars:4000}") int maxTextChars) {
        return new ModelMemoryRecallReranker(rerankModelPort, rerankModel, inputTopK, maxTextChars);
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
            @Value("${seahorse-agent.memory.recall.vector-collection:memory_vectors}") String collectionName,
            @Value("${seahorse-agent.memory.recall.embedding-model:}") String embeddingModel) {
        return new VectorSearchScoredMemoryVectorPort(
                vectorSearchPort,
                embeddingModelPort,
                collectionName,
                embeddingModel);
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
            @Value("${seahorse-agent.memory.recall.graph-max-hops:1}") int maxHops) {
        return new GraphMemoryRecallChannel(graphPort, maxHops);
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
            @Value("${seahorse-agent.memory.recall.channel-top-k:20}") int channelTopK) {
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
                channelTopK,
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

    @Bean
    @ConditionalOnMissingBean(ContextWeaverPort.class)
    public DefaultContextWeaver seahorseDefaultContextWeaver(ObjectProvider<MemoryTraceRecorder> traceRecorder,
                                                             ObjectProvider<ObservationPort> observationPort) {
        return new DefaultContextWeaver(
                traceRecorder.getIfAvailable(MemoryTraceRecorder::noop),
                observationPort.getIfAvailable(ObservationPort::noop));
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean(name = "seahorseVectorUpsertMemoryOutboxTaskHandler")
    public VectorMemoryOutboxTaskHandler seahorseVectorUpsertMemoryOutboxTaskHandler(
            ObjectProvider<MemoryVectorPort> memoryVectorPort) {
        return new VectorMemoryOutboxTaskHandler(
                memoryVectorPort.getIfAvailable(MemoryVectorPort::noop),
                MemoryOutboxTaskTypes.VECTOR_UPSERT);
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean(name = "seahorseVectorDeleteMemoryOutboxTaskHandler")
    public VectorMemoryOutboxTaskHandler seahorseVectorDeleteMemoryOutboxTaskHandler(
            ObjectProvider<MemoryVectorPort> memoryVectorPort) {
        return new VectorMemoryOutboxTaskHandler(
                memoryVectorPort.getIfAvailable(MemoryVectorPort::noop),
                MemoryOutboxTaskTypes.VECTOR_DELETE);
    }

    @Bean
    @ConditionalOnBean({MemoryOutboxPort.class, MemoryKeywordIndexPort.class})
    @ConditionalOnMissingBean(name = "seahorseKeywordUpsertMemoryOutboxTaskHandler")
    public KeywordMemoryOutboxTaskHandler seahorseKeywordUpsertMemoryOutboxTaskHandler(
            MemoryKeywordIndexPort keywordIndexPort) {
        return new KeywordMemoryOutboxTaskHandler(keywordIndexPort, MemoryOutboxTaskTypes.KEYWORD_UPSERT);
    }

    @Bean
    @ConditionalOnBean({MemoryOutboxPort.class, MemoryKeywordIndexPort.class})
    @ConditionalOnMissingBean(name = "seahorseKeywordDeleteMemoryOutboxTaskHandler")
    public KeywordMemoryOutboxTaskHandler seahorseKeywordDeleteMemoryOutboxTaskHandler(
            MemoryKeywordIndexPort keywordIndexPort) {
        return new KeywordMemoryOutboxTaskHandler(keywordIndexPort, MemoryOutboxTaskTypes.KEYWORD_DELETE);
    }

    @Bean
    @ConditionalOnBean({MemoryOutboxPort.class, MemoryGraphIndexPort.class})
    @ConditionalOnMissingBean(name = "seahorseGraphUpsertMemoryOutboxTaskHandler")
    public GraphMemoryOutboxTaskHandler seahorseGraphUpsertMemoryOutboxTaskHandler(
            MemoryGraphIndexPort graphIndexPort) {
        return new GraphMemoryOutboxTaskHandler(graphIndexPort, MemoryOutboxTaskTypes.GRAPH_UPSERT);
    }

    @Bean
    @ConditionalOnBean({MemoryOutboxPort.class, MemoryGraphIndexPort.class})
    @ConditionalOnMissingBean(name = "seahorseGraphDeleteMemoryOutboxTaskHandler")
    public GraphMemoryOutboxTaskHandler seahorseGraphDeleteMemoryOutboxTaskHandler(
            MemoryGraphIndexPort graphIndexPort) {
        return new GraphMemoryOutboxTaskHandler(graphIndexPort, MemoryOutboxTaskTypes.GRAPH_DELETE);
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean
    public MemoryOutboxRelayService seahorseMemoryOutboxRelayService(
            MemoryOutboxPort memoryOutboxPort,
            ObjectProvider<MemoryOutboxTaskHandler> taskHandlers,
            ObjectProvider<MemoryTraceRecorder> traceRecorder,
            ObjectProvider<ObservationPort> observationPort) {
        return new MemoryOutboxRelayService(
                memoryOutboxPort,
                taskHandlers.orderedStream().toList(),
                traceRecorder.getIfAvailable(MemoryTraceRecorder::noop),
                observationPort.getIfAvailable(ObservationPort::noop));
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxRelayService.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.outbox", name = "relay-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean
    public SeahorseMemoryOutboxRelayJob seahorseMemoryOutboxRelayJob(
            MemoryOutboxRelayService relayService,
            ObjectProvider<DistributedLockPort> lockPort,
            MemoryProperties memoryProperties) {
        return new SeahorseMemoryOutboxRelayJob(
                relayService,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                memoryProperties.getOutbox().getRelayBatchSize());
    }

    @Bean
    @ConditionalOnBean(MemoryEnginePort.class)
    @ConditionalOnMissingBean(MemoryIngestionWorkflowPort.class)
    public MemoryIngestionWorkflowPort seahorseMemoryIngestionWorkflowPort(MemoryEnginePort memoryEnginePort) {
        return command -> {
            if (memoryEnginePort instanceof MemoryIngestionWorkflowPort workflowPort) {
                return workflowPort.ingest(command);
            }
            memoryEnginePort.writeMemory(command == null ? null : command.writeRequest());
            return com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult.ignored(
                    "delegated_to_memory_engine");
        };
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
            ObjectProvider<MemoryConflictLogRepositoryPort> conflictLogRepositoryPort,
            ObjectProvider<ProfileMemoryPort> profileMemoryPort,
            ObjectProvider<CorrectionLedgerPort> correctionLedgerPort,
            ObjectProvider<MemoryOperationLogPort> operationLogPort,
            ObjectProvider<MemoryOutboxPort> outboxPort,
            ObjectProvider<MemoryReviewManagementRepositoryPort> reviewRepositoryPort,
            ObjectProvider<MemoryPolicyConfigPort> policyConfigPort,
            ObjectProvider<MemoryTraceRecorder> traceRecorder) {
        return new MemoryManagementServicePorts(
                workingMemoryPort,
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                qualitySnapshotRepositoryPort.getIfAvailable(MemoryQualitySnapshotRepositoryPort::empty),
                conflictLogRepositoryPort.getIfAvailable(MemoryConflictLogRepositoryPort::empty),
                profileMemoryPort.getIfAvailable(ProfileMemoryPort::noop),
                correctionLedgerPort.getIfAvailable(CorrectionLedgerPort::noop),
                operationLogPort.getIfAvailable(MemoryOperationLogPort::noop),
                outboxPort.getIfAvailable(MemoryOutboxPort::noop),
                reviewRepositoryPort.getIfAvailable(MemoryReviewManagementRepositoryPort::empty),
                policyConfigPort.getIfAvailable(MemoryPolicyConfigPort::defaults),
                traceRecorder.getIfAvailable(MemoryTraceRecorder::noop));
    }

    @Bean
    @ConditionalOnBean(MemoryManagementServicePorts.class)
    @ConditionalOnMissingBean(MemoryManagementInboundPort.class)
    public KernelMemoryManagementService seahorseMemoryManagementInboundPort(
            MemoryManagementServicePorts servicePorts) {
        return new KernelMemoryManagementService(servicePorts);
    }

    @Bean
    @ConditionalOnBean(MemoryIngestionWorkflowPort.class)
    @ConditionalOnMissingBean(MemoryReviewInboundPort.class)
    public KernelMemoryReviewService seahorseMemoryReviewInboundPort(
            ObjectProvider<MemoryReviewManagementRepositoryPort> reviewRepositoryPort,
            ObjectProvider<MemoryReviewFeedbackRepositoryPort> reviewFeedbackRepositoryPort,
            MemoryIngestionWorkflowPort ingestionWorkflowPort,
            ObjectProvider<MemoryTraceRecorder> traceRecorder,
            ObjectProvider<MemoryAliasPort> memoryAliasPort,
            ObjectProvider<ObservationPort> observationPort) {
        return new KernelMemoryReviewService(
                reviewRepositoryPort.getIfAvailable(MemoryReviewManagementRepositoryPort::empty),
                ingestionWorkflowPort,
                reviewFeedbackRepositoryPort.getIfAvailable(MemoryReviewFeedbackRepositoryPort::empty),
                traceRecorder.getIfAvailable(MemoryTraceRecorder::noop),
                memoryAliasPort.getIfAvailable(MemoryAliasPort::noop),
                observationPort.getIfAvailable(ObservationPort::noop));
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
            ObjectProvider<MemoryInferencePort> memoryInferencePort,
            ObjectProvider<ShortTermMemoryMaintenancePort> shortTermMemoryMaintenancePort,
            ObjectProvider<MemoryQualitySnapshotRepositoryPort> qualitySnapshotRepositoryPort,
            ObjectProvider<MemoryConflictLogRepositoryPort> conflictLogRepositoryPort) {
        return new MemoryGovernanceServicePorts(
                shortTermMemoryPort,
                longTermMemoryPort,
                semanticMemoryPort,
                memoryEnginePort.getIfAvailable(MemoryEnginePort::noop),
                memoryInferencePort.getIfAvailable(MemoryInferencePort::noop),
                shortTermMemoryMaintenancePort.getIfAvailable(ShortTermMemoryMaintenancePort::noop),
                qualitySnapshotRepositoryPort.getIfAvailable(MemoryQualitySnapshotRepositoryPort::empty),
                conflictLogRepositoryPort.getIfAvailable(MemoryConflictLogRepositoryPort::empty));
    }

    @Bean
    @ConditionalOnBean(MemoryGovernanceServicePorts.class)
    @ConditionalOnMissingBean(MemoryGovernanceInboundPort.class)
    public KernelMemoryGovernanceService seahorseMemoryGovernanceInboundPort(
            MemoryGovernanceServicePorts servicePorts,
            @Value("${seahorse-agent.memory.long-term-importance-threshold:0.6}") double promotionThreshold,
            @Value("${seahorse-agent.memory.inference-enabled:false}") boolean inferenceEnabled,
            @Value("${seahorse-agent.memory.decay.scan-limit:500}") int decayScanLimit,
            @Value("${seahorse-agent.memory.decay.threshold:0.1}") double decayThreshold,
            @Value("${seahorse-agent.memory.decay.dry-run:false}") boolean decayDryRun) {
        return new KernelMemoryGovernanceService(servicePorts, promotionThreshold, inferenceEnabled,
                new MemoryDecayOptions(decayScanLimit, decayThreshold, decayDryRun));
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
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean
    public MemoryCompactionService seahorseMemoryCompactionService(
            ObjectProvider<MemoryCompactionPort> compactionPort,
            ObjectProvider<LongTermMemoryPort> longTermMemoryPort,
            MemoryOutboxPort outboxPort,
            ObjectProvider<MemoryCompactionSummarizerPort> summarizerPort,
            ObjectProvider<MemoryKeywordIndexPort> keywordIndexPort,
            ObjectProvider<MemoryGraphIndexPort> graphIndexPort,
            ObjectProvider<ObservationPort> observationPort,
            MemoryProperties memoryProperties) {
        MemoryProperties.Maintenance.Compaction compaction = memoryProperties.getMaintenance().getCompaction();
        return new MemoryCompactionService(
                compactionPort.getIfAvailable(MemoryCompactionPort::noop),
                longTermMemoryPort.getIfAvailable(),
                outboxPort,
                summarizerPort.getIfAvailable(MemoryCompactionSummarizerPort::noop),
                new MemoryCompactionOptions(
                        compaction.getScanLimit(),
                        compaction.getMinGroupSize(),
                        compaction.isVectorIndexEnabled(),
                        compaction.isKeywordIndexEnabled() && keywordIndexPort.getIfAvailable() != null,
                        compaction.isGraphIndexEnabled() && graphIndexPort.getIfAvailable() != null,
                        compaction.getEmbeddingModel()),
                observationPort.getIfAvailable(ObservationPort::noop));
    }

    @Bean
    @ConditionalOnBean(MemoryOutboxPort.class)
    @ConditionalOnMissingBean
    public MemoryGarbageCollectionService seahorseMemoryGarbageCollectionService(
            ObjectProvider<MemoryGarbageCollectionPort> garbageCollectionPort,
            MemoryOutboxPort outboxPort,
            ObjectProvider<MemoryKeywordIndexPort> keywordIndexPort,
            ObjectProvider<MemoryGraphIndexPort> graphIndexPort,
            MemoryProperties memoryProperties) {
        MemoryProperties.Maintenance.Gc gc = memoryProperties.getMaintenance().getGc();
        return new MemoryGarbageCollectionService(
                garbageCollectionPort.getIfAvailable(MemoryGarbageCollectionPort::noop),
                outboxPort,
                new MemoryGarbageCollectionOptions(
                        gc.getScanLimit(),
                        Duration.ofDays(Math.max(0L, gc.getRetentionDays())),
                        gc.isDryRun(),
                        gc.isVectorIndexEnabled(),
                        gc.isKeywordIndexEnabled() && keywordIndexPort.getIfAvailable() != null,
                        gc.isGraphIndexEnabled() && graphIndexPort.getIfAvailable() != null,
                        gc.isArchiveEnabled(),
                        Duration.ofDays(Math.max(0L, gc.getArchiveIdleDays())),
                        gc.getArchiveScoreThreshold(),
                        gc.isPhysicalDeleteEnabled(),
                        Duration.ofDays(Math.max(0L, gc.getPhysicalDeleteRetentionDays()))));
    }

    @Bean
    @ConditionalOnBean(MemoryAliasPort.class)
    @ConditionalOnMissingBean(MemoryAliasResolutionService.class)
    public MemoryAliasResolutionService seahorseMemoryAliasResolutionService(
            MemoryAliasPort aliasPort,
            @Value("${seahorse-agent.memory.alias-resolution.scan-limit:100}") int scanLimit,
            @Value("${seahorse-agent.memory.alias-resolution.auto-resolve-confidence-threshold:0.95}")
            double autoResolveConfidenceThreshold,
            Environment environment) {
        return new MemoryAliasResolutionService(
                aliasPort,
                new MemoryAliasResolutionOptions(
                        scanLimit,
                        "",
                        "default",
                        autoResolveConfidenceThreshold,
                        memoryAliasDictionary(environment)));
    }

    private Map<String, MemoryAliasCandidate> memoryAliasDictionary(Environment environment) {
        Map<String, MemoryAliasDictionaryEntry> entries = Binder.get(environment)
                .bind("seahorse-agent.memory.alias-resolution.dictionary",
                        Bindable.mapOf(String.class, MemoryAliasDictionaryEntry.class))
                .orElse(Map.of());
        Map<String, MemoryAliasCandidate> dictionary = new LinkedHashMap<>();
        entries.forEach((aliasText, entry) -> dictionary.put(aliasText, entry.toCandidate(aliasText)));
        return dictionary;
    }

    private static final class MemoryAliasDictionaryEntry {

        private String userId;
        private String tenantId;
        private String aliasText;
        private String canonicalEntityId;
        private String canonicalName;
        private String entityType;
        private double confidenceLevel;

        MemoryAliasCandidate toCandidate(String dictionaryAliasText) {
            String candidateAliasText = hasText(aliasText) ? aliasText : dictionaryAliasText;
            return new MemoryAliasCandidate(
                    userId,
                    tenantId,
                    candidateAliasText,
                    canonicalEntityId,
                    canonicalName,
                    entityType,
                    confidenceLevel);
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public void setTenantId(String tenantId) {
            this.tenantId = tenantId;
        }

        public void setAliasText(String aliasText) {
            this.aliasText = aliasText;
        }

        public void setCanonicalEntityId(String canonicalEntityId) {
            this.canonicalEntityId = canonicalEntityId;
        }

        public void setCanonicalName(String canonicalName) {
            this.canonicalName = canonicalName;
        }

        public void setEntityType(String entityType) {
            this.entityType = entityType;
        }

        public void setConfidenceLevel(double confidenceLevel) {
            this.confidenceLevel = confidenceLevel;
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @Bean
    @ConditionalOnBean(MemoryGarbageCollectionService.class)
    @ConditionalOnMissingBean(MemoryMaintenanceInboundPort.class)
    public DefaultMemoryMaintenanceService seahorseMemoryMaintenanceInboundPort(
            MemoryGarbageCollectionService garbageCollectionService,
            ObjectProvider<MemoryCompactionService> compactionService,
            ObjectProvider<MemoryAliasResolutionService> aliasResolutionService,
            ObjectProvider<MemoryMaintenanceRunRepositoryPort> maintenanceRunRepositoryPort,
            ObjectProvider<MemoryTraceRecorder> traceRecorder,
            ObjectProvider<ObservationPort> observationPort,
            @Value("${seahorse-agent.memory.maintenance.compaction-enabled:false}") boolean compactionEnabled,
            @Value("${seahorse-agent.memory.maintenance.alias-enabled:false}") boolean aliasEnabled,
            @Value("${seahorse-agent.memory.maintenance.gc-enabled:true}") boolean garbageCollectionEnabled) {
        return new DefaultMemoryMaintenanceService(
                garbageCollectionService,
                compactionService.getIfAvailable(),
                aliasResolutionService.getIfAvailable(),
                maintenanceRunRepositoryPort.getIfAvailable(MemoryMaintenanceRunRepositoryPort::noop),
                traceRecorder.getIfAvailable(MemoryTraceRecorder::noop),
                observationPort.getIfAvailable(ObservationPort::noop),
                compactionEnabled,
                aliasEnabled,
                garbageCollectionEnabled);
    }

    @Bean
    @ConditionalOnBean(MemoryMaintenanceInboundPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.maintenance", name = "scheduler-enabled",
            havingValue = "true")
    @ConditionalOnMissingBean
    public SeahorseMemoryMaintenanceJob seahorseMemoryMaintenanceJob(
            MemoryMaintenanceInboundPort maintenanceInboundPort,
            ObjectProvider<DistributedLockPort> lockPort,
            @Value("${seahorse-agent.memory.maintenance.compaction-enabled:false}") boolean compactionEnabled,
            @Value("${seahorse-agent.memory.maintenance.alias-enabled:false}") boolean aliasEnabled,
            @Value("${seahorse-agent.memory.maintenance.gc-enabled:true}") boolean garbageCollectionEnabled) {
        return new SeahorseMemoryMaintenanceJob(
                maintenanceInboundPort,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                compactionEnabled,
                aliasEnabled,
                garbageCollectionEnabled);
    }

    @Bean
    @ConditionalOnBean(MemoryGarbageCollectionService.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.memory.gc", name = "scheduler-enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnExpression("!${seahorse-agent.memory.maintenance.scheduler-enabled:false}"
            + " || !${seahorse-agent.memory.maintenance.gc-enabled:true}")
    @ConditionalOnMissingBean
    public SeahorseMemoryGarbageCollectionJob seahorseMemoryGarbageCollectionJob(
            MemoryGarbageCollectionService garbageCollectionService,
            ObjectProvider<DistributedLockPort> lockPort) {
        return new SeahorseMemoryGarbageCollectionJob(
                garbageCollectionService,
                lockPort.getIfAvailable(DistributedLockPort::noop));
    }
}

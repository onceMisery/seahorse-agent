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
import com.miracle.ai.seahorse.agent.kernel.application.memory.InMemoryUserMemoryPrivacySettingPort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelUserMemoryPrivacyService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryDecayOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryCaptureRules;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryEngineOptions;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryGovernanceServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryManagementServicePorts;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryOutboxRelayService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.RuleBasedMemoryCandidateExtractor;
import com.miracle.ai.seahorse.agent.kernel.application.memory.UserMemoryPrivacyAwareMemoryEnginePort;
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
import com.miracle.ai.seahorse.agent.ports.inbound.memory.UserMemoryPrivacyInboundPort;
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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.UserMemoryPrivacySettingPort;
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
    @ConditionalOnMissingBean(UserMemoryPrivacySettingPort.class)
    public InMemoryUserMemoryPrivacySettingPort seahorseUserMemoryPrivacySettingPort() {
        return new InMemoryUserMemoryPrivacySettingPort();
    }

    @Bean
    @ConditionalOnMissingBean(UserMemoryPrivacyInboundPort.class)
    public KernelUserMemoryPrivacyService seahorseUserMemoryPrivacyInboundPort(
            UserMemoryPrivacySettingPort settingPort) {
        return new KernelUserMemoryPrivacyService(settingPort);
    }

    @Bean
    @ConditionalOnMissingBean(MemoryTraceRecorder.class)
    public MemoryTraceRecorder seahorseMemoryTraceRecorder(MemoryProperties memoryProperties) {
        return new InMemoryMemoryTraceRecorder(memoryProperties.getTrace().getMaxEvents());
    }

    @Bean
    @ConditionalOnMissingBean(MemoryTraceInboundPort.class)
    public KernelMemoryTraceQueryService seahorseMemoryTraceInboundPort(MemoryTraceRecorder traceRecorder) {
        return new KernelMemoryTraceQueryService(traceRecorder);
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
    public MemoryEnginePort seahorseDefaultMemoryEnginePort(
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
            ObjectProvider<UserMemoryPrivacySettingPort> userMemoryPrivacySettingPort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            Environment environment,
            MemoryCaptureRuleProperties captureRuleProperties,
            MemoryProperties memoryProperties) {
        ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
        MemoryRefinerPort configuredRefinerPort = memoryRefinerPort.getIfAvailable();
        boolean refinerEnabled = memoryRefinerEnabled(environment, configuredRefinerPort != null);
        MemoryProperties.Refiner refiner = memoryProperties.getRefiner();
        MemoryProperties.DerivedIndex derivedIndex = memoryProperties.getDerivedIndex();
        MemoryEngineOptions options = new MemoryEngineOptions(
                memoryProperties.getShortTermLimit(),
                memoryProperties.getLongTermLimit(),
                memoryProperties.getSemanticLimit(),
                memoryProperties.isCaptureEnabled(),
                refinerEnabled,
                refiner.isFailOpen(),
                derivedIndex.isKeywordEnabled() && memoryKeywordIndexPort.getIfAvailable() != null,
                derivedIndex.isGraphEnabled() && memoryGraphIndexPort.getIfAvailable() != null,
                refiner.getMaxBatchOperations(),
                refiner.getMaxDeleteRatio(),
                refiner.getReadMaskPerLayerLimit(),
                refiner.getTargetZoneTurnCount(),
                refiner.getStickyAnchorLimit(),
                refiner.getFeedbackExampleLimit(),
                refiner.getStickyAnchorImportanceThreshold(),
                refiner.getStickyAnchorConfidenceThreshold(),
                refiner.getMaxRefinementDepth());
        DefaultMemoryEnginePort delegate = DefaultMemoryEnginePort.builder(
                        shortTermMemoryPort,
                        longTermMemoryPort,
                        semanticMemoryPort,
                        objectMapper)
                .options(options)
                .profileMemoryPort(profileMemoryPort.getIfAvailable(ProfileMemoryPort::noop))
                .correctionLedgerPort(correctionLedgerPort.getIfAvailable(CorrectionLedgerPort::noop))
                .memoryRouterPort(memoryRouterPort.getIfAvailable(DefaultMemoryRouter::new))
                .memoryOperationLogPort(memoryOperationLogPort.getIfAvailable(MemoryOperationLogPort::noop))
                .memoryVectorPort(memoryVectorPort.getIfAvailable(MemoryVectorPort::noop))
                .memoryOutboxPort(memoryOutboxPort.getIfAvailable(MemoryOutboxPort::noop))
                .businessDocumentRetrieverPort(
                        businessDocumentRetrieverPort.getIfAvailable(MemoryBusinessDocumentRetrieverPort::noop))
                .memoryLifecyclePort(memoryLifecyclePort.getIfAvailable(MemoryLifecyclePort::noop))
                .memoryPolicyConfigPort(memoryPolicyConfigPort.getIfAvailable(MemoryPolicyConfigPort::defaults))
                .memoryRetrievalPipelinePort(memoryRetrievalPipelinePort.getIfAvailable())
                .memoryRefinerPort(configuredRefinerPort == null ? MemoryRefinerPort.noop() : configuredRefinerPort)
                .memoryReviewCandidatePort(memoryReviewCandidatePort.getIfAvailable(MemoryReviewCandidatePort::noop))
                .memoryAliasPort(memoryAliasPort.getIfAvailable(MemoryAliasPort::noop))
                .memoryReviewPolicyPort(memoryReviewPolicyPort.getIfAvailable(MemoryReviewPolicyPort::defaults))
                .memoryReviewFeedbackRepositoryPort(
                        memoryReviewFeedbackRepositoryPort.getIfAvailable(MemoryReviewFeedbackRepositoryPort::empty))
                .captureRules(captureRuleProperties == null ? MemoryCaptureRules.defaults() : captureRuleProperties.toRules())
                .build();
        return new UserMemoryPrivacyAwareMemoryEnginePort(
                delegate,
                userMemoryPrivacySettingPort.getIfAvailable(UserMemoryPrivacySettingPort::defaults));
    }

    private boolean memoryRefinerEnabled(Environment environment, boolean refinerAvailable) {
        if (environment.containsProperty(MEMORY_REFINER_ENABLED_PROPERTY)) {
            return environment.getProperty(MEMORY_REFINER_ENABLED_PROPERTY, Boolean.class, false);
        }
        return refinerAvailable;
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
            MemoryProperties memoryProperties) {
        MemoryProperties.Decay decay = memoryProperties.getDecay();
        return new KernelMemoryGovernanceService(servicePorts,
                memoryProperties.getLongTermImportanceThreshold(),
                memoryProperties.isInferenceEnabled(),
                new MemoryDecayOptions(decay.getScanLimit(), decay.getThreshold(), decay.isDryRun()));
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
}

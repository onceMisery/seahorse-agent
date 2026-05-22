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
import com.miracle.ai.seahorse.agent.adapters.web.ChatStreamCallbackFactoryPort;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatPipeline;
import com.miracle.ai.seahorse.agent.kernel.application.auth.KernelAuthService;
import com.miracle.ai.seahorse.agent.kernel.application.dashboard.KernelDashboardService;
import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionEngine;
import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionPipelineService;
import com.miracle.ai.seahorse.agent.kernel.application.ingestion.KernelIngestionTaskService;
import com.miracle.ai.seahorse.agent.kernel.application.intent.KernelIntentTreeService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeBaseService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelKnowledgeChunkService;
import com.miracle.ai.seahorse.agent.kernel.application.knowledge.KernelDocumentRefreshService;
import com.miracle.ai.seahorse.agent.kernel.application.keyword.KernelKeywordIndexMaintenanceService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryEngine;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryGovernanceService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.KernelMemoryManagementService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.DefaultMemoryRetrievalPipeline;
import com.miracle.ai.seahorse.agent.kernel.application.memory.MemoryOutboxRelayService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.DefaultMemoryAggregationService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationTopicShiftDetector;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.InMemoryMemoryAggregationBufferPort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryAliasResolutionService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance.MemoryGarbageCollectionService;
import com.miracle.ai.seahorse.agent.kernel.application.memory.outbox.GraphMemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.kernel.application.memory.outbox.KeywordMemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.kernel.application.memory.outbox.VectorMemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.HybridMemoryRecallPipeline;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.ModelMemoryRecallReranker;
import com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval.LayeredScoredMemoryVectorPort;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataBackfillService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataDictionaryService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataExtractionResultService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataQualityService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataQuarantineService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataReviewService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataSchemaService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelMetadataSchemaUsageService;
import com.miracle.ai.seahorse.agent.kernel.application.metadata.KernelVersionQualityComparisonService;
import com.miracle.ai.seahorse.agent.kernel.application.model.KernelModelRoutingService;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEvaluationService;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalStrategyTemplateService;
import com.miracle.ai.seahorse.agent.kernel.application.sample.KernelSampleQuestionService;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.kernel.application.user.KernelUserService;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.ChunkerNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.EmbedderNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.EnhancerNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.EnricherNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.FetcherNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.IndexerNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.IngestionNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.MetadataExtractorNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.MetadataNormalizerNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.MetadataValidatorNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.ingestion.ParserNodeFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.IntentDirectedSearchFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchChannelFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.VectorGlobalSearchFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.auth.AuthInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.ingestion.IngestionPipelinePayload;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.DocumentRefreshInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.knowledge.KnowledgeDocumentInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryMaintenanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryTraceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataBackfillInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataDictionaryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataExtractionResultInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQualityInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataQuarantineInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataSchemaUsageInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.metadata.VersionQualityComparisonInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplate;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplateInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.user.UserInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserPage;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserUpdateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelinePage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionPipelineRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskNodeValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskPage;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskUpdateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentTreeRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeBaseValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBasePage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseUpdateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshStateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationSchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDeleteCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDocument;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFusionPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGarbageCollectionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRerankerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.RefinedMemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ScoredMemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataIndexCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNode;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceNodeFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePage;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTracePageRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRun;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRunFinish;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.ArrayList;
import java.util.concurrent.Executor;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Seahorse 原生内核自动配置契约测试。
 *
 * <p>该测试只覆盖 L1/L2 原生内核装配。
 */
class SeahorseAgentKernelAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentKernelAutoConfiguration.class));

    @Test
    void shouldStartKernelInfrastructureWithNativePorts() {
        contextRunner.withPropertyValues("seahorse-agent.kernel.mode=kernel")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelChatPipeline.class);
                    assertThat(context).hasSingleBean(KernelIngestionEngine.class);
                    assertThat(context).hasSingleBean(KernelMemoryEngine.class);
                    assertThat(context).hasSingleBean(KernelModelRoutingService.class);
                    assertThat(context).hasSingleBean(KernelRetrievalEvaluationService.class);
                    assertThat(context).hasSingleBean(RetrievalEvaluationInboundPort.class);
                    assertThat(context).hasSingleBean(KernelRetrievalStrategyTemplateService.class);
                    assertThat(context).hasSingleBean(RetrievalStrategyTemplateInboundPort.class);
                    assertThat(context).hasSingleBean(ChatInboundPort.class);
                    assertThat(context).hasSingleBean(LocalStreamTaskPort.class);
                });
    }

    @Test
    void shouldExposeNativeEntrypointsWhenModeIsKernel() {
        contextRunner.withPropertyValues("seahorse-agent.kernel.mode=kernel")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(StreamTaskPort.class))
                            .isInstanceOf(LocalStreamTaskPort.class);
                    assertThat(context.getBean(ChatStreamCallbackFactoryPort.class))
                            .isInstanceOf(LocalChatStreamCallbackFactory.class);
                    assertThat(context.getBean(RetrievalContextPort.class))
                            .isInstanceOf(KernelRetrievalEngine.class);
                });
    }

    @Test
    void shouldExposeNativeEntrypointsWhenModeIsMissing() {
        contextRunner.run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(RetrievalContextPort.class))
                            .isInstanceOf(KernelRetrievalEngine.class);
                });
    }

    @Test
    void shouldProvideDefaultRetrievalExecutors() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasBean("ragRetrievalThreadPoolExecutor");
            assertThat(context).hasBean("ragInnerRetrievalThreadPoolExecutor");
            assertThat(context).hasBean("ragContextThreadPoolExecutor");
            assertThat(context.getBean("ragRetrievalThreadPoolExecutor", Executor.class)).isNotNull();
            assertThat(context.getBean("ragInnerRetrievalThreadPoolExecutor", Executor.class)).isNotNull();
            assertThat(context.getBean("ragContextThreadPoolExecutor", Executor.class)).isNotNull();
        });
    }

    @Test
    void shouldWireRetrievalStrategyTemplateRepositoryOverride() {
        contextRunner.withUserConfiguration(RetrievalTemplateRepositoryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    RetrievalStrategyTemplateInboundPort templatePort =
                            context.getBean(RetrievalStrategyTemplateInboundPort.class);

                    assertThat(templatePort.listTemplates("kb-1"))
                            .extracting(RetrievalStrategyTemplate::templateKey)
                            .containsExactly("vector_only", "hybrid_rrf", "hybrid_rerank", "kb_custom");
                });
    }

    @Test
    void shouldDisableKernelAutoConfigurationWhenFlagIsFalse() {
        contextRunner.withPropertyValues("seahorse-agent.kernel.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(KernelChatPipeline.class);
                    assertThat(context).doesNotHaveBean(StreamTaskPort.class);
                });
    }

    @Test
    void shouldRegisterNativeIndexerFeatureWhenRepositoryAndVectorPortsExist() {
        contextRunner.withUserConfiguration(NativeIndexerPortsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IndexerNodeFeature.class);
                    assertThat(context).hasSingleBean(KernelKnowledgeChunkService.class);
                    ExtensionRegistry registry = context.getBean(ExtensionRegistry.class);
                    assertThat(registry.getActivatedExtensions(IngestionNodeFeature.class,
                            context.getBean(FeatureActivationContext.class)))
                            .hasExactlyElementsOfTypes(FetcherNodeFeature.class, ParserNodeFeature.class,
                                    EnhancerNodeFeature.class, EnricherNodeFeature.class, ChunkerNodeFeature.class,
                                    EmbedderNodeFeature.class, IndexerNodeFeature.class,
                                    MetadataExtractorNodeFeature.class, MetadataNormalizerNodeFeature.class,
                                    MetadataValidatorNodeFeature.class);
                });
    }

    @Test
    void shouldRegisterNativeVectorGlobalSearchFeatureWhenKnowledgeAndVectorPortsExist() {
        contextRunner.withUserConfiguration(NativeSearchPortsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(IntentDirectedSearchFeature.class);
                    assertThat(context).hasSingleBean(VectorGlobalSearchFeature.class);
                    ExtensionRegistry registry = context.getBean(ExtensionRegistry.class);
                    assertThat(registry.getActivatedExtensions(SearchChannelFeature.class,
                            context.getBean(FeatureActivationContext.class)))
                            .hasExactlyElementsOfTypes(IntentDirectedSearchFeature.class,
                                    VectorGlobalSearchFeature.class);
                });
    }

    @Test
    void shouldRegisterSampleQuestionInboundPortWhenRepositoryExists() {
        contextRunner.withUserConfiguration(SampleQuestionRepositoryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelSampleQuestionService.class);
                });
    }

    @Test
    void shouldRegisterDashboardInboundPortWhenRepositoryExists() {
        contextRunner.withUserConfiguration(DashboardRepositoryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelDashboardService.class);
                });
    }

    @Test
    void shouldRegisterIntentTreeInboundPortWhenRepositoryExists() {
        contextRunner.withUserConfiguration(IntentTreeRepositoryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelIntentTreeService.class);
                });
    }

    @Test
    void shouldRegisterIngestionPipelineInboundPortWhenRepositoryExists() {
        contextRunner.withUserConfiguration(IngestionPipelineRepositoryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelIngestionPipelineService.class);
                });
    }

    @Test
    void shouldRegisterIngestionTaskInboundPortWhenTaskAndPipelineRepositoriesExist() {
        contextRunner.withUserConfiguration(IngestionTaskPortsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelIngestionTaskService.class);
                });
    }

    @Test
    void shouldRegisterKnowledgeBaseInboundPortWhenRepositoryStorageAndVectorPortsExist() {
        contextRunner.withUserConfiguration(KnowledgeBasePortsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelKnowledgeBaseService.class);
                });
    }

    @Test
    void shouldRegisterTraceRecorderWhenTraceRepositoryExists() {
        contextRunner.withUserConfiguration(TraceRepositoryConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelRagTraceRecorder.class);
                    assertThat(context).hasSingleBean(SeahorseRagTraceCleanupJob.class);
                });
    }

    @Test
    void shouldDisableRagTraceCleanupJobWhenConfigured() {
        contextRunner.withUserConfiguration(TraceRepositoryConfiguration.class)
                .withPropertyValues("seahorse-agent.rag-trace.cleanup.enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelRagTraceRecorder.class);
                    assertThat(context).doesNotHaveBean(SeahorseRagTraceCleanupJob.class);
                });
    }

    @Test
    void shouldRegisterAuthAndUserInboundPortsWhenAuthPortsExist() {
        contextRunner.withUserConfiguration(AuthPortsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelAuthService.class);
                    assertThat(context).hasSingleBean(KernelUserService.class);
                    assertThat(context).hasSingleBean(AuthInboundPort.class);
                    assertThat(context).hasSingleBean(UserInboundPort.class);
                });
    }

    @Test
    void shouldRegisterDocumentRefreshInboundPortWhenRefreshPortsExist() {
        contextRunner.withUserConfiguration(DocumentRefreshPortsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KernelDocumentRefreshService.class);
                    assertThat(context).hasSingleBean(DocumentRefreshInboundPort.class);
                    assertThat(context).hasSingleBean(KernelKeywordIndexMaintenanceService.class);
                    assertThat(context).hasSingleBean(KeywordIndexMaintenanceInboundPort.class);
                    assertThat(context).hasSingleBean(KernelMetadataBackfillService.class);
                    assertThat(context).hasSingleBean(MetadataBackfillInboundPort.class);
                    assertThat(context).hasSingleBean(KernelMetadataQualityService.class);
                    assertThat(context).hasSingleBean(MetadataQualityInboundPort.class);
                    assertThat(context).hasSingleBean(KernelMetadataReviewService.class);
                    assertThat(context).hasSingleBean(MetadataReviewInboundPort.class);
                    assertThat(context).hasSingleBean(MetadataReviewReExtractPort.class);
                    assertThat(context).hasSingleBean(MetadataIndexCompensationPort.class);
                    assertThat(context).hasSingleBean(KernelMetadataQuarantineService.class);
                    assertThat(context).hasSingleBean(MetadataQuarantineInboundPort.class);
                    assertThat(context).hasSingleBean(KernelMetadataSchemaService.class);
                    assertThat(context).hasSingleBean(MetadataSchemaInboundPort.class);
                    assertThat(context).hasSingleBean(KernelMetadataSchemaUsageService.class);
                    assertThat(context).hasSingleBean(MetadataSchemaUsageInboundPort.class);
                    assertThat(context).hasSingleBean(KernelVersionQualityComparisonService.class);
                    assertThat(context).hasSingleBean(VersionQualityComparisonInboundPort.class);
                    assertThat(context).hasSingleBean(KernelMetadataDictionaryService.class);
                    assertThat(context).hasSingleBean(MetadataDictionaryInboundPort.class);
                    assertThat(context).hasSingleBean(KernelMetadataExtractionResultService.class);
                    assertThat(context).hasSingleBean(MetadataExtractionResultInboundPort.class);
                    assertThat(context).hasSingleBean(SeahorseDocumentRefreshJob.class);
                    assertThat(context).doesNotHaveBean(SeahorseKeywordIndexMaintenanceJob.class);
                });
    }

    @Test
    void shouldRegisterKeywordIndexMaintenanceJobWhenEnabled() {
        contextRunner.withUserConfiguration(DocumentRefreshPortsConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.keyword-index.maintenance.scheduler-enabled=true",
                        "seahorse-agent.keyword-index.maintenance.kb-ids=kb-1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(KeywordIndexMaintenanceInboundPort.class);
                    assertThat(context).hasSingleBean(SeahorseKeywordIndexMaintenanceJob.class);
                });
    }

    @Test
    void shouldRegisterMemoryManagementAndGovernancePortsWhenMemoryStoresExist() {
        contextRunner.withUserConfiguration(MemoryStorePortsConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryEnginePort.class);
                    assertThat(context).hasSingleBean(MemoryRetrievalPipelinePort.class);
                    assertThat(context.getBean(MemoryRetrievalPipelinePort.class))
                            .isInstanceOf(HybridMemoryRecallPipeline.class);
                    assertThat(context.getBean(ScoredMemoryVectorPort.class))
                            .isInstanceOf(LayeredScoredMemoryVectorPort.class);
                    assertThat(context).doesNotHaveBean(DefaultMemoryRetrievalPipeline.class);
                    assertThat(context).hasSingleBean(MemoryRouterPort.class);
                    assertThat(context).hasSingleBean(ContextWeaverPort.class);
                    assertThat(context).hasSingleBean(MemoryIngestionWorkflowPort.class);
                    assertThat(context).hasSingleBean(MemoryOperationLogPort.class);
                    assertThat(context).hasSingleBean(MemoryPolicyConfigPort.class);
                    assertThat(context).hasSingleBean(ProfileMemoryPort.class);
                    assertThat(context).hasSingleBean(CorrectionLedgerPort.class);
                    assertThat(context).hasSingleBean(KernelMemoryManagementService.class);
                    assertThat(context).hasSingleBean(KernelMemoryGovernanceService.class);
                    assertThat(context).hasSingleBean(MemoryManagementInboundPort.class);
                    assertThat(context).hasSingleBean(MemoryGovernanceInboundPort.class);
                    assertThat(context).hasSingleBean(MemoryRecallEvaluationInboundPort.class);
                    assertThat(context).hasSingleBean(MemoryTraceInboundPort.class);
                    assertThat(context).hasSingleBean(SeahorseMemoryGovernanceJob.class);
                    assertThat(context).hasSingleBean(MemoryAggregationPolicy.class);
                    assertThat(context.getBean(MemoryManagementInboundPort.class)
                            .memoryHealth("user-1", "default")
                            .pendingReviewCount()).isZero();
                    assertThat(context).doesNotHaveBean(MemoryRefinerPort.class);
                    assertThat(context).doesNotHaveBean(MemoryAggregationServicePort.class);
                    assertThat(context).doesNotHaveBean(MemoryAggregationBufferPort.class);
                    assertThat(context).doesNotHaveBean(SeahorseMemoryAggregationJob.class);
                    assertThat(context).doesNotHaveBean(MemoryGarbageCollectionService.class);
                    assertThat(context).doesNotHaveBean(SeahorseMemoryGarbageCollectionJob.class);
                });
    }

    @Test
    void shouldFallbackToDefaultMemoryRetrievalPipelineWhenHybridRecallIsDisabled() {
        contextRunner.withUserConfiguration(MemoryStorePortsConfiguration.class)
                .withPropertyValues("seahorse-agent.memory.recall.hybrid-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryRetrievalPipelinePort.class);
                    assertThat(context.getBean(MemoryRetrievalPipelinePort.class))
                            .isInstanceOf(DefaultMemoryRetrievalPipeline.class);
                    assertThat(context).doesNotHaveBean(HybridMemoryRecallPipeline.class);
                    assertThat(context).doesNotHaveBean(ScoredMemoryVectorPort.class);
                });
    }

    @Test
    void shouldRegisterMemoryOutboxRelayWithDefaultVectorHandlerWhenOutboxExists() {
        contextRunner.withUserConfiguration(MemoryOutboxConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryOutboxRelayService.class);
                    assertThat(context).hasSingleBean(MemoryGarbageCollectionService.class);
                    assertThat(context).hasSingleBean(MemoryMaintenanceInboundPort.class);
                    assertThat(context).hasSingleBean(SeahorseMemoryGarbageCollectionJob.class);
                    assertThat(context.getBeansOfType(VectorMemoryOutboxTaskHandler.class))
                            .hasSize(2);
                    assertThat(context.getBeansOfType(MemoryOutboxTaskHandler.class).values())
                            .extracting(MemoryOutboxTaskHandler::taskType)
                            .containsExactlyInAnyOrder("VECTOR_UPSERT", "VECTOR_DELETE");
                    assertThat(context).doesNotHaveBean(KeywordMemoryOutboxTaskHandler.class);
                    assertThat(context).doesNotHaveBean(GraphMemoryOutboxTaskHandler.class);
                });
    }

    @Test
    void shouldPreferCustomOutboxTaskHandlerOverAutoConfiguredBuiltInHandler() {
        contextRunner.withUserConfiguration(CustomMemoryOutboxTaskHandlerConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryOutboxRelayService.class);
                    assertThat(context.getBeansOfType(VectorMemoryOutboxTaskHandler.class))
                            .hasSize(2);

                    MemoryOutboxRelayService relayService = context.getBean(MemoryOutboxRelayService.class);
                    relayService.processBatch(10);

                    RecordingMemoryOutboxTaskHandler customHandler =
                            context.getBean(RecordingMemoryOutboxTaskHandler.class);
                    RecordingMemoryVectorPort vectorPort = context.getBean(RecordingMemoryVectorPort.class);
                    RecordingMemoryOutboxPort outboxPort = context.getBean(RecordingMemoryOutboxPort.class);
                    assertThat(customHandler.handledTaskIds).containsExactly("outbox-custom-delete");
                    assertThat(vectorPort.deletedMemoryIds).isEmpty();
                    assertThat(outboxPort.succeeded).containsExactly("outbox-custom-delete");
                    assertThat(outboxPort.failed).isEmpty();
                });
    }

    @Test
    void shouldDisableMemoryGarbageCollectionSchedulerWhenConfigured() {
        contextRunner.withUserConfiguration(MemoryOutboxConfiguration.class)
                .withPropertyValues("seahorse-agent.memory.gc.scheduler-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryGarbageCollectionService.class);
                    assertThat(context).doesNotHaveBean(SeahorseMemoryGarbageCollectionJob.class);
                });
    }

    @Test
    void shouldPreferUnifiedMaintenanceSchedulerAsGarbageCollectionOwner() {
        contextRunner.withUserConfiguration(MemoryOutboxConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.memory.maintenance.scheduler-enabled=true",
                        "seahorse-agent.memory.maintenance.gc-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryGarbageCollectionService.class);
                    assertThat(context).hasSingleBean(SeahorseMemoryMaintenanceJob.class);
                    assertThat(context).doesNotHaveBean(SeahorseMemoryGarbageCollectionJob.class);
                });
    }

    @Test
    void shouldRegisterDerivedIndexOutboxHandlersOnlyWhenIndexPortsExist() {
        contextRunner.withUserConfiguration(MemoryOutboxConfiguration.class, MemoryDerivedIndexConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryOutboxRelayService.class);
                    assertThat(context.getBeansOfType(MemoryOutboxTaskHandler.class))
                            .hasSize(6);
                    assertThat(context.getBeansOfType(MemoryOutboxTaskHandler.class).values())
                            .extracting(MemoryOutboxTaskHandler::taskType)
                            .containsExactlyInAnyOrder(
                                    "VECTOR_UPSERT",
                                    "VECTOR_DELETE",
                                    "KEYWORD_UPSERT",
                                    "KEYWORD_DELETE",
                                    "GRAPH_UPSERT",
                                    "GRAPH_DELETE");
                });
    }

    @Test
    void shouldEnqueueDerivedIndexTasksFromMemoryEngineOnlyWhenIndexPortsExist() {
        contextRunner.withUserConfiguration(
                        MemoryStorePortsConfiguration.class,
                        MemoryOutboxConfiguration.class,
                        MemoryDerivedIndexConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MemoryEnginePort memoryEnginePort = context.getBean(MemoryEnginePort.class);
                    RecordingMemoryOutboxPort outboxPort = context.getBean(RecordingMemoryOutboxPort.class);
                    org.mockito.Mockito.when(context.getBean(MemoryOperationLogPort.class)
                                    .tryStart(org.mockito.ArgumentMatchers.any()))
                            .thenReturn(true);

                    memoryEnginePort.writeMemory(MemoryWriteRequest.builder()
                            .userId("user-derived-index")
                            .conversationId("conv-derived-index")
                            .messageId("msg-derived-index")
                            .message(ChatMessage.user("请记住：我喜欢简短回答"))
                            .build());

                    assertThat(outboxPort.tasks)
                            .extracting(MemoryOutboxPort.MemoryOutboxTask::taskType)
                            .containsExactly("KEYWORD_UPSERT", "GRAPH_UPSERT");
                });
    }

    @Test
    void shouldInjectAliasPortIntoMemoryEngineForCanonicalWriteMetadata() {
        contextRunner.withUserConfiguration(MemoryStorePortsConfiguration.class, StaticMemoryAliasConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    org.mockito.Mockito.when(context.getBean(MemoryOperationLogPort.class)
                                    .tryStart(org.mockito.ArgumentMatchers.any()))
                            .thenReturn(true);

                    MemoryEnginePort memoryEnginePort = context.getBean(MemoryEnginePort.class);
                    memoryEnginePort.writeMemory(MemoryWriteRequest.builder()
                            .userId("user-alias-write")
                            .conversationId("conv-alias-write")
                            .messageId("msg-alias-write")
                            .message(ChatMessage.user("请记住：我喜欢使用 K8s 部署服务"))
                            .build());

                    org.mockito.ArgumentCaptor<MemoryRecord> captor =
                            org.mockito.ArgumentCaptor.forClass(MemoryRecord.class);
                    verify(context.getBean(ShortTermMemoryPort.class)).save(captor.capture());
                    assertThat(captor.getValue().metadata())
                            .containsEntry("canonicalEntityId", "ent-core-k8s")
                            .containsEntry("canonicalName", "Kubernetes")
                            .containsEntry("canonicalEntityType", "TECHNOLOGY");
                });
    }

    @Test
    void shouldRegisterHybridMemoryRecallPipelineOnlyWhenEnabled() {
        contextRunner.withUserConfiguration(MemoryStorePortsConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.memory.recall.hybrid-enabled=true",
                        "seahorse-agent.memory.recall.rrf-k=30",
                        "seahorse-agent.memory.recall.final-top-k=4",
                        "seahorse-agent.memory.recall.channel-top-k=12")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryRetrievalPipelinePort.class);
                    assertThat(context.getBean(MemoryRetrievalPipelinePort.class))
                            .isInstanceOf(HybridMemoryRecallPipeline.class);
                    assertThat(context.getBean(ScoredMemoryVectorPort.class))
                            .isInstanceOf(LayeredScoredMemoryVectorPort.class);
                    assertThat(context).doesNotHaveBean(DefaultMemoryRetrievalPipeline.class);
                });
    }

    @Test
    void shouldBindHybridMemoryRecallChannelWeights() {
        contextRunner.withUserConfiguration(MemoryStorePortsConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.memory.recall.hybrid-enabled=true",
                        "seahorse-agent.memory.recall.channel-weights.vector=2.0",
                        "seahorse-agent.memory.recall.channel-weights.keyword=1.5",
                        "seahorse-agent.memory.recall.channel-weights.graph=1.25")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MemoryFusionPolicy policy = context.getBean(MemoryFusionPolicy.class);
                    assertThat(policy.channelWeights()).containsEntry("vector", 2.0D);
                    assertThat(policy.channelWeights()).containsEntry("keyword", 1.5D);
                    assertThat(policy.channelWeights()).containsEntry("graph", 1.25D);
                });
    }

    @Test
    void shouldBindAliasResolutionDictionary() {
        contextRunner.withUserConfiguration(MemoryAliasConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.memory.alias-resolution.auto-resolve-confidence-threshold=0.9",
                        "seahorse-agent.memory.alias-resolution.dictionary.k8s.user-id=user-1",
                        "seahorse-agent.memory.alias-resolution.dictionary.k8s.tenant-id=tenant-1",
                        "seahorse-agent.memory.alias-resolution.dictionary.k8s.canonical-entity-id=ent-core-k8s",
                        "seahorse-agent.memory.alias-resolution.dictionary.k8s.canonical-name=Kubernetes",
                        "seahorse-agent.memory.alias-resolution.dictionary.k8s.entity-type=TECHNOLOGY",
                        "seahorse-agent.memory.alias-resolution.dictionary.k8s.confidence-level=0.99")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MemoryAliasResolutionService service = context.getBean(MemoryAliasResolutionService.class);
                    RecordingMemoryAliasPort aliasPort = context.getBean(RecordingMemoryAliasPort.class);

                    service.run("configured-dictionary");

                    assertThat(aliasPort.commands).hasSize(1);
                    MemoryAliasCommand command = aliasPort.commands.get(0);
                    assertThat(command.userId()).isEqualTo("user-1");
                    assertThat(command.tenantId()).isEqualTo("tenant-1");
                    assertThat(command.aliasText()).isEqualTo("k8s");
                    assertThat(command.canonicalEntityId()).isEqualTo("ent-core-k8s");
                    assertThat(command.canonicalName()).isEqualTo("Kubernetes");
                    assertThat(command.entityType()).isEqualTo("TECHNOLOGY");
                    assertThat(command.metadata()).containsEntry("normalizationStrategy", "dictionary");
                });
    }

    @Test
    void shouldRegisterModelMemoryRecallRerankerWhenEnabled() {
        contextRunner.withUserConfiguration(MemoryStorePortsConfiguration.class, RerankModelConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.memory.recall.hybrid-enabled=true",
                        "seahorse-agent.memory.recall.rerank-enabled=true",
                        "seahorse-agent.memory.recall.rerank-model=rerank-memory")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryRecallRerankerPort.class);
                    assertThat(context.getBean(MemoryRecallRerankerPort.class))
                            .isInstanceOf(ModelMemoryRecallReranker.class);
                });
    }

    @Test
    void shouldRegisterMemoryReviewPortWhenIngestionWorkflowExistsWithoutReviewRepository() {
        contextRunner.withUserConfiguration(MemoryReviewIngestionWorkflowConfiguration.class)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryIngestionWorkflowPort.class);
                    assertThat(context).hasSingleBean(MemoryReviewInboundPort.class);
                    assertThat(context).doesNotHaveBean(MemoryReviewManagementRepositoryPort.class);

                    MemoryReviewInboundPort reviewPort = context.getBean(MemoryReviewInboundPort.class);
                    assertThat(reviewPort.page(
                                    "tenant-1",
                                    "user-1",
                                    MemoryReviewStatus.PENDING,
                                    "",
                                    "",
                                    1L,
                                    10L)
                            .records())
                            .isEmpty();
                });
    }

    @Test
    void shouldUseProvidedMemoryRefinerOnlyWhenRefinerIsEnabled() {
        contextRunner.withUserConfiguration(MemoryStorePortsConfiguration.class, MemoryRefinerConfiguration.class)
                .withPropertyValues("seahorse-agent.memory.refiner.enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MemoryEnginePort memoryEnginePort = context.getBean(MemoryEnginePort.class);
                    ShortTermMemoryPort shortTermMemoryPort = context.getBean(ShortTermMemoryPort.class);
                    when(context.getBean(MemoryOperationLogPort.class).tryStart(any())).thenReturn(true);

                    memoryEnginePort.writeMemory(MemoryWriteRequest.builder()
                            .userId("user-1")
                            .conversationId("conv-refiner")
                            .messageId("msg-refiner")
                            .message(ChatMessage.user("we discussed project state"))
                            .build());

                    verify(shortTermMemoryPort).save(any(MemoryRecord.class));
                });
    }

    @Test
    void shouldLeaveLlmMemoryRefinerRegistrationToAdapterAutoConfiguration() {
        contextRunner.withUserConfiguration(LlmMemoryRefinerConfiguration.class)
                .withPropertyValues("seahorse-agent.memory.refiner.llm-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MemoryRefinerPort.class);
                });
    }

    @Test
    void shouldNotRegisterLlmMemoryRefinerWithoutChatModel() {
        contextRunner.withUserConfiguration(ObjectMapperConfiguration.class)
                .withPropertyValues("seahorse-agent.memory.refiner.llm-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(MemoryRefinerPort.class);
                });
    }

    @Test
    void shouldRegisterMemoryAggregationBeansOnlyWhenEnabled() {
        contextRunner.withUserConfiguration(MemoryStorePortsConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.memory.aggregation.enabled=true",
                        "seahorse-agent.memory.aggregation.idle-flush-millis=1000",
                        "seahorse-agent.memory.aggregation.max-turns=2",
                        "seahorse-agent.memory.aggregation.max-tokens=100",
                        "seahorse-agent.memory.aggregation.capture-on-error=true",
                        "seahorse-agent.memory.aggregation.topic-shift-flush-enabled=true")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MemoryAggregationPolicy.class);
                    assertThat(context).hasSingleBean(MemoryAggregationTopicShiftDetector.class);
                    assertThat(context).hasSingleBean(MemoryAggregationSchedulerPort.class);
                    assertThat(context).hasSingleBean(InMemoryMemoryAggregationBufferPort.class);
                    assertThat(context).hasSingleBean(DefaultMemoryAggregationService.class);
                    assertThat(context).hasSingleBean(SeahorseMemoryAggregationJob.class);

                    MemoryAggregationPolicy policy = context.getBean(MemoryAggregationPolicy.class);
                    assertThat(policy.enabled()).isTrue();
                    assertThat(policy.idleFlushMillis()).isEqualTo(1000L);
                    assertThat(policy.maxTurns()).isEqualTo(2);
                    assertThat(policy.maxTokens()).isEqualTo(100);
                    assertThat(policy.captureOnError()).isTrue();
                    assertThat(policy.topicShiftFlushEnabled()).isTrue();
                });
    }

    @Test
    void shouldApplyMemoryCaptureSwitchFromProperties() {
        contextRunner.withUserConfiguration(MemoryStorePortsConfiguration.class)
                .withPropertyValues("seahorse-agent.memory.capture-enabled=false")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MemoryEnginePort memoryEnginePort = context.getBean(MemoryEnginePort.class);
                    ShortTermMemoryPort shortTermMemoryPort = context.getBean(ShortTermMemoryPort.class);

                    memoryEnginePort.writeMemory(MemoryWriteRequest.builder()
                            .userId("user-1")
                            .conversationId("conv-1")
                            .messageId("msg-1")
                            .message(ChatMessage.user("请记住：我喜欢 Java"))
                            .build());

                    verify(shortTermMemoryPort, never()).save(any(MemoryRecord.class));
                });
    }

    @Test
    void shouldInitializeMemoryPolicyConfigFromProperties() {
        contextRunner.withUserConfiguration(MemoryStorePortsConfiguration.class)
                .withPropertyValues(
                        "seahorse-agent.memory.policy.capture-accept-threshold=0.55",
                        "seahorse-agent.memory.policy.token-budget=1800",
                        "seahorse-agent.memory.policy.review-enabled=true",
                        "seahorse-agent.memory.policy.grey-release-key=tenant-default")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    MemoryPolicyConfigPort port = context.getBean(MemoryPolicyConfigPort.class);

                    assertThat(port.current().captureAcceptThreshold()).isEqualTo(0.55D);
                    assertThat(port.current().tokenBudget()).isEqualTo(1800);
                    assertThat(port.current().reviewEnabled()).isTrue();
                    assertThat(port.current().greyReleaseKey()).isEqualTo("tenant-default");
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class NativeIndexerPortsConfiguration {

        @Bean
        VectorCollectionAdminPort vectorCollectionAdminPort() {
            return new VectorCollectionAdminPort() {
                @Override
                public boolean collectionExists(String collectionName) {
                    return true;
                }

                @Override
                public void ensureCollection(String collectionName) {
                }
            };
        }

        @Bean
        VectorIndexPort vectorIndexPort() {
            return new VectorIndexPort() {
                @Override
                public void indexDocumentChunks(String collectionName, String docId, java.util.List<VectorChunk> chunks) {
                }

                @Override
                public void updateChunk(String collectionName, String docId, VectorChunk chunk) {
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

        @Bean
        KnowledgeChunkRepositoryPort knowledgeChunkRepositoryPort() {
            return (kbId, docId, chunks) -> {
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NativeSearchPortsConfiguration {

        @Bean
        KnowledgeBaseQueryPort knowledgeBaseQueryPort() {
            return new KnowledgeBaseQueryPort() {
                @Override
                public java.util.List<com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentSummary> searchDocuments(
                        String keyword, int limit) {
                    return java.util.List.of();
                }

                @Override
                public java.util.List<com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkSummary> listChunksByDocId(
                        String docId) {
                    return java.util.List.of();
                }
            };
        }

        @Bean
        VectorSearchPort vectorSearchPort() {
            return request -> java.util.List.of();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class SampleQuestionRepositoryConfiguration {

        @Bean
        SampleQuestionRepositoryPort sampleQuestionRepositoryPort() {
            return new SampleQuestionRepositoryPort() {
                @Override
                public java.util.List<com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRecord> listRandomQuestions(
                        int limit) {
                    return java.util.List.of();
                }

                @Override
                public com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionPage page(
                        long current, long size, String keyword) {
                    return new com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionPage(
                            java.util.List.of(), 0, size, current, 0);
                }

                @Override
                public java.util.Optional<com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRecord> findById(
                        String id) {
                    return java.util.Optional.empty();
                }

                @Override
                public String create(String title, String description, String question) {
                    return "1";
                }

                @Override
                public boolean update(
                        String id,
                        com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionUpdateValues values) {
                    return true;
                }

                @Override
                public boolean delete(String id) {
                    return true;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DashboardRepositoryConfiguration {

        @Bean
        DashboardRepositoryPort dashboardRepositoryPort() {
            return new DashboardRepositoryPort() {
                @Override
                public com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardOverview overview(String window) {
                    return null;
                }

                @Override
                public com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardPerformance performance(
                        String window) {
                    return null;
                }

                @Override
                public com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardTrends trends(
                        String metric, String window, String granularity) {
                    return null;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class IntentTreeRepositoryConfiguration {

        @Bean
        IntentTreeRepositoryPort intentTreeRepositoryPort() {
            return new IntentTreeRepositoryPort() {
                @Override
                public java.util.List<com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodeTree> listActiveNodes() {
                    return java.util.List.of();
                }

                @Override
                public java.util.Optional<com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodeTree> findById(
                        String id) {
                    return java.util.Optional.empty();
                }

                @Override
                public boolean existsByIntentCode(String intentCode) {
                    return false;
                }

                @Override
                public String create(
                        com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodePayload payload,
                        String operator) {
                    return "1";
                }

                @Override
                public boolean update(
                        String id,
                        com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodePayload payload,
                        String operator) {
                    return true;
                }

                @Override
                public boolean deleteByIds(java.util.List<String> ids) {
                    return true;
                }

                @Override
                public boolean updateEnabled(java.util.List<String> ids, int enabled, String operator) {
                    return true;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class IngestionPipelineRepositoryConfiguration {

        @Bean
        IngestionPipelineRepositoryPort ingestionPipelineRepositoryPort() {
            return new IngestionPipelineRepositoryPort() {
                @Override
                public IngestionPipelineRecord create(IngestionPipelinePayload payload) {
                    return new IngestionPipelineRecord();
                }

                @Override
                public java.util.Optional<IngestionPipelineRecord> findRecordById(String pipelineId) {
                    return java.util.Optional.empty();
                }

                @Override
                public IngestionPipelinePage page(long current, long size, String keyword) {
                    return new IngestionPipelinePage(java.util.List.of(), 0, size, current, 0);
                }

                @Override
                public boolean update(String pipelineId, IngestionPipelinePayload payload) {
                    return true;
                }

                @Override
                public boolean delete(String pipelineId, String operator) {
                    return true;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class IngestionTaskPortsConfiguration {

        @Bean
        PipelineDefinitionRepositoryPort pipelineDefinitionRepositoryPort() {
            return pipelineId -> java.util.Optional.of(
                    com.miracle.ai.seahorse.agent.kernel.domain.ingestion.PipelineDefinition.builder()
                            .id(pipelineId)
                            .name("pipeline")
                            .nodes(java.util.List.of())
                            .build());
        }

        @Bean
        IngestionTaskRepositoryPort ingestionTaskRepositoryPort() {
            return new IngestionTaskRepositoryPort() {
                @Override
                public String createRunningTask(IngestionTaskCreateValues values) {
                    return "task-1";
                }

                @Override
                public void updateTask(String taskId, IngestionTaskUpdateValues values) {
                }

                @Override
                public void replaceNodeLogs(String taskId, java.util.List<IngestionTaskNodeValues> nodes) {
                }

                @Override
                public java.util.Optional<IngestionTaskRecord> findById(String taskId) {
                    return java.util.Optional.empty();
                }

                @Override
                public java.util.List<IngestionTaskNodeRecord> listNodes(String taskId) {
                    return java.util.List.of();
                }

                @Override
                public IngestionTaskPage page(long current, long size, String status) {
                    return new IngestionTaskPage(java.util.List.of(), 0, size, current, 0);
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DocumentRefreshPortsConfiguration {

        @Bean
        DocumentRefreshSchedulePort documentRefreshSchedulePort() {
            return DocumentRefreshSchedulePort.noop();
        }

        @Bean
        DocumentRefreshStateRepositoryPort documentRefreshStateRepositoryPort() {
            return DocumentRefreshStateRepositoryPort.noop();
        }

        @Bean
        KnowledgeDocumentRepositoryPort documentRefreshKnowledgeDocumentRepositoryPort() {
            return mock(KnowledgeDocumentRepositoryPort.class);
        }

        @Bean
        DocumentFetcherPort documentFetcherPort() {
            return mock(DocumentFetcherPort.class);
        }

        @Bean
        ObjectStoragePort documentRefreshObjectStoragePort() {
            return mock(ObjectStoragePort.class);
        }

        @Bean
        KnowledgeDocumentInboundPort knowledgeDocumentInboundPort() {
            return mock(KnowledgeDocumentInboundPort.class);
        }

        @Bean
        PipelineDefinitionRepositoryPort documentRefreshPipelineDefinitionRepositoryPort() {
            return pipelineId -> java.util.Optional.empty();
        }

        @Bean
        SchedulerPort schedulerPort() {
            return SchedulerPort.none();
        }

        @Bean
        MetadataBackfillJobRepositoryPort metadataBackfillJobRepositoryPort() {
            return mock(MetadataBackfillJobRepositoryPort.class);
        }

        @Bean
        MetadataQualityReportRepositoryPort metadataQualityReportRepositoryPort() {
            return mock(MetadataQualityReportRepositoryPort.class);
        }

        @Bean
        MetadataReviewManagementRepositoryPort metadataReviewManagementRepositoryPort() {
            return mock(MetadataReviewManagementRepositoryPort.class);
        }

        @Bean
        MetadataQuarantineManagementRepositoryPort metadataQuarantineManagementRepositoryPort() {
            return mock(MetadataQuarantineManagementRepositoryPort.class);
        }

        @Bean
        MetadataSchemaManagementRepositoryPort metadataSchemaManagementRepositoryPort() {
            return mock(MetadataSchemaManagementRepositoryPort.class);
        }

        @Bean
        MetadataSchemaUsageReportRepositoryPort metadataSchemaUsageReportRepositoryPort() {
            return mock(MetadataSchemaUsageReportRepositoryPort.class);
        }

        @Bean
        MetadataDictionaryManagementRepositoryPort metadataDictionaryManagementRepositoryPort() {
            return mock(MetadataDictionaryManagementRepositoryPort.class);
        }

        @Bean
        MetadataExtractionResultManagementRepositoryPort metadataExtractionResultManagementRepositoryPort() {
            return mock(MetadataExtractionResultManagementRepositoryPort.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MemoryStorePortsConfiguration {

        @Bean
        WorkingMemoryPort workingMemoryPort() {
            return mock(WorkingMemoryPort.class);
        }

        @Bean
        ShortTermMemoryPort shortTermMemoryPort() {
            return mock(ShortTermMemoryPort.class);
        }

        @Bean
        LongTermMemoryPort longTermMemoryPort() {
            return mock(LongTermMemoryPort.class);
        }

        @Bean
        SemanticMemoryPort semanticMemoryPort() {
            return mock(SemanticMemoryPort.class);
        }

        @Bean
        ProfileMemoryPort profileMemoryPort() {
            return mock(ProfileMemoryPort.class);
        }

        @Bean
        CorrectionLedgerPort correctionLedgerPort() {
            return mock(CorrectionLedgerPort.class);
        }

        @Bean
        MemoryOperationLogPort memoryOperationLogPort() {
            return mock(MemoryOperationLogPort.class);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RerankModelConfiguration {

        @Bean
        RerankModelPort rerankModelPort() {
            return (modelId, query, chunks) -> chunks == null ? List.of() : List.copyOf(chunks);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MemoryOutboxConfiguration {

        @Bean
        RecordingMemoryOutboxPort memoryOutboxPort() {
            return new RecordingMemoryOutboxPort(List.of());
        }

        @Bean
        MemoryVectorPort memoryVectorPort() {
            return MemoryVectorPort.noop();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CustomMemoryOutboxTaskHandlerConfiguration {

        @Bean
        RecordingMemoryOutboxPort memoryOutboxPort() {
            return new RecordingMemoryOutboxPort(List.of(new MemoryOutboxPort.MemoryOutboxTask(
                    "outbox-custom-delete",
                    "VECTOR_DELETE",
                    "stm-1",
                    "user-1",
                    "default",
                    Map.of("memoryId", "stm-1"),
                    "",
                    null,
                    Instant.EPOCH)));
        }

        @Bean
        RecordingMemoryVectorPort memoryVectorPort() {
            return new RecordingMemoryVectorPort();
        }

        @Bean
        RecordingMemoryOutboxTaskHandler customVectorDeleteMemoryOutboxTaskHandler() {
            return new RecordingMemoryOutboxTaskHandler("VECTOR_DELETE");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MemoryAliasConfiguration {

        @Bean
        RecordingMemoryAliasPort memoryAliasPort() {
            return new RecordingMemoryAliasPort();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StaticMemoryAliasConfiguration {

        @Bean
        MemoryAliasPort staticMemoryAliasPort() {
            return new MemoryAliasPort() {
                @Override
                public java.util.Optional<MemoryAliasResolution> resolveAlias(String userId, String tenantId,
                                                                              String aliasText) {
                    if ("K8s".equals(aliasText)) {
                        return java.util.Optional.of(new MemoryAliasResolution(
                                "K8s",
                                "k8s",
                                "ent-core-k8s",
                                "Kubernetes",
                                "TECHNOLOGY",
                                0.99D));
                    }
                    return java.util.Optional.empty();
                }

                @Override
                public void upsertAlias(MemoryAliasCommand command) {
                }
            };
        }
    }

    static class RecordingMemoryAliasPort implements MemoryAliasPort {

        private final List<MemoryAliasCommand> commands = new ArrayList<>();

        @Override
        public java.util.Optional<MemoryAliasResolution> resolveAlias(String userId, String tenantId,
                                                                      String aliasText) {
            return java.util.Optional.empty();
        }

        @Override
        public void upsertAlias(MemoryAliasCommand command) {
            commands.add(command);
        }

        @Override
        public List<MemoryAliasCandidate> findMergeCandidates(String userId, String tenantId, int limit) {
            return List.of();
        }

        @Override
        public List<MemoryAliasCandidate> findMergeCandidates(int limit) {
            return List.of();
        }
    }

    static class RecordingMemoryOutboxPort implements MemoryOutboxPort {

        private final List<MemoryOutboxTask> tasks;
        private final List<String> succeeded = new ArrayList<>();
        private final Map<String, String> failed = new java.util.LinkedHashMap<>();

        RecordingMemoryOutboxPort(List<MemoryOutboxTask> tasks) {
            this.tasks = new ArrayList<>(tasks);
        }

        @Override
        public void enqueue(MemoryOutboxTask task) {
            tasks.add(task);
        }

        @Override
        public List<MemoryOutboxTask> pollPending(int limit) {
            return tasks.stream().limit(limit).toList();
        }

        @Override
        public void markSucceeded(String taskId) {
            succeeded.add(taskId);
        }

        @Override
        public void markFailed(String taskId, String errorMessage) {
            failed.put(taskId, errorMessage);
        }
    }

    static class RecordingMemoryVectorPort implements MemoryVectorPort {

        private final List<String> deletedMemoryIds = new ArrayList<>();

        @Override
        public void upsert(String memoryId, String userId, String content, String embeddingModel) {
        }

        @Override
        public List<String> search(String userId, String query, int topK) {
            return List.of();
        }

        @Override
        public void delete(String memoryId, String userId, String tenantId) {
            deletedMemoryIds.add(memoryId);
        }
    }

    static class RecordingMemoryOutboxTaskHandler implements MemoryOutboxTaskHandler {

        private final String taskType;
        private final List<String> handledTaskIds = new ArrayList<>();

        RecordingMemoryOutboxTaskHandler(String taskType) {
            this.taskType = taskType;
        }

        @Override
        public String taskType() {
            return taskType;
        }

        @Override
        public void handle(MemoryOutboxPort.MemoryOutboxTask task) {
            handledTaskIds.add(task.id());
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MemoryDerivedIndexConfiguration {

        @Bean
        MemoryKeywordIndexPort memoryKeywordIndexPort() {
            return new MemoryKeywordIndexPort() {
                @Override
                public void upsert(MemoryDerivedIndexDocument document) {
                }

                @Override
                public void delete(MemoryDerivedIndexDeleteCommand command) {
                }
            };
        }

        @Bean
        MemoryGraphIndexPort memoryGraphIndexPort() {
            return new MemoryGraphIndexPort() {
                @Override
                public void upsert(MemoryDerivedIndexDocument document) {
                }

                @Override
                public void delete(MemoryDerivedIndexDeleteCommand command) {
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MemoryRefinerConfiguration {

        @Bean
        MemoryRefinerPort memoryRefinerPort() {
            return request -> MemoryRefinementResult.refined(
                    "test-refiner",
                    List.of(RefinedMemoryOperation.add(
                            "PROJECT_FACT",
                            "project.state",
                            "User discussed project state.",
                            0.9D,
                            0.8D,
                            List.of(request.messageId()),
                            List.of("test_refiner"))),
                    Map.of("model", "test"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class MemoryReviewIngestionWorkflowConfiguration {

        @Bean
        MemoryIngestionWorkflowPort memoryIngestionWorkflowPort() {
            return command -> MemoryIngestionResult.accepted(List.of("review"));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class KnowledgeBasePortsConfiguration {

        @Bean
        KnowledgeBaseRepositoryPort knowledgeBaseRepositoryPort() {
            return new KnowledgeBaseRepositoryPort() {
                @Override
                public String create(CreateKnowledgeBaseValues values) {
                    return "1";
                }

                @Override
                public boolean nameExists(String normalizedName, String excludedKbId) {
                    return false;
                }

                @Override
                public java.util.Optional<KnowledgeBaseRecord> findById(String kbId) {
                    return java.util.Optional.empty();
                }

                @Override
                public KnowledgeBasePage page(long current, long size, String name) {
                    return new KnowledgeBasePage(java.util.List.of(), 0, size, current, 0);
                }

                @Override
                public boolean hasDocuments(String kbId) {
                    return false;
                }

                @Override
                public boolean hasVectorizedDocuments(String kbId) {
                    return false;
                }

                @Override
                public boolean update(String kbId, KnowledgeBaseUpdateValues values) {
                    return true;
                }

                @Override
                public boolean delete(String kbId, String operator) {
                    return true;
                }
            };
        }

        @Bean
        VectorCollectionAdminPort knowledgeBaseVectorCollectionAdminPort() {
            return new VectorCollectionAdminPort() {
                @Override
                public boolean collectionExists(String collectionName) {
                    return true;
                }

                @Override
                public void ensureCollection(String collectionName) {
                }
            };
        }

        @Bean
        ObjectStoragePort objectStoragePort() {
            return new ObjectStoragePort() {
                @Override
                public StoredObject upload(String bucketName, java.io.InputStream content, long size,
                                           String originalFilename, String contentType) {
                    return null;
                }

                @Override
                public StoredObject reliableUpload(String bucketName, java.io.InputStream content, long size,
                                                   String originalFilename, String contentType) {
                    return null;
                }

                @Override
                public java.io.InputStream openStream(String url) {
                    return java.io.InputStream.nullInputStream();
                }

                @Override
                public void deleteByUrl(String url) {
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TraceRepositoryConfiguration {

        @Bean
        RagTraceRepositoryPort ragTraceRepositoryPort() {
            return new RagTraceRepositoryPort() {
                @Override
                public RagTracePage<RagTraceRun> pageRuns(RagTracePageRequest request) {
                    return new RagTracePage<>(1, 10, 0, java.util.List.of());
                }

                @Override
                public java.util.Optional<RagTraceRun> findRun(String traceId) {
                    return java.util.Optional.empty();
                }

                @Override
                public java.util.List<RagTraceNode> listNodes(String traceId) {
                    return java.util.List.of();
                }

                @Override
                public void startRun(RagTraceRun run) {
                }

                @Override
                public void finishRun(RagTraceRunFinish finish) {
                }

                @Override
                public void startNode(RagTraceNode node) {
                }

                @Override
                public void finishNode(RagTraceNodeFinish finish) {
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RetrievalTemplateRepositoryConfiguration {

        @Bean
        RetrievalStrategyTemplateRepositoryPort retrievalStrategyTemplateRepositoryPort() {
            return kbId -> java.util.List.of(new RetrievalStrategyTemplate(
                    "kb_custom",
                    "知识库自定义模板",
                    "自动配置应注入模板仓储端口",
                    RetrievalOptions.defaults(7)));
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ObjectMapperConfiguration {

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LlmMemoryRefinerConfiguration extends ObjectMapperConfiguration {

        @Bean
        ChatModelPort chatModelPort() {
            return (request, modelId) -> """
                    {"refined":false,"reason":"test","operations":[]}
                    """;
        }

        @Bean
        PromptTemplatePort promptTemplatePort() {
            return PromptTemplatePort.empty();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AuthPortsConfiguration {

        @Bean
        UserRepositoryPort userRepositoryPort() {
            return new UserRepositoryPort() {
                @Override
                public java.util.Optional<UserRecord> findById(String id) {
                    return java.util.Optional.of(new UserRecord(id, "admin", "pw", "admin", null, null, null));
                }

                @Override
                public java.util.Optional<UserRecord> findByUsername(String username) {
                    return java.util.Optional.empty();
                }

                @Override
                public boolean usernameExists(String username, String excludedId) {
                    return false;
                }

                @Override
                public UserPage page(long current, long size, String keyword) {
                    return new UserPage(java.util.List.of(), 0, size, current, 0);
                }

                @Override
                public String create(UserCreateValues values) {
                    return "1";
                }

                @Override
                public boolean update(String id, UserUpdateValues values) {
                    return true;
                }

                @Override
                public boolean delete(String id) {
                    return true;
                }
            };
        }

        @Bean
        PasswordHasherPort passwordHasherPort() {
            return PasswordHasherPort.plainText();
        }

        @Bean
        TokenServicePort tokenServicePort() {
            return new TokenServicePort() {
                @Override
                public String login(String userId) {
                    return "token";
                }

                @Override
                public void logout() {
                }
            };
        }

        @Bean
        CurrentUserPort currentUserPort() {
            return () -> java.util.Optional.of(new CurrentUser("1", "admin", "admin", null));
        }
    }
}

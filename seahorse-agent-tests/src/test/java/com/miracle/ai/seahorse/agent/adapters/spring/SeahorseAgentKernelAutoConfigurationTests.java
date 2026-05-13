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

import com.miracle.ai.seahorse.agent.adapters.local.LocalChatStreamCallbackFactory;
import com.miracle.ai.seahorse.agent.adapters.local.LocalStreamTaskPort;
import com.miracle.ai.seahorse.agent.adapters.web.ChatStreamCallbackFactoryPort;
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
import com.miracle.ai.seahorse.agent.kernel.application.model.KernelModelRoutingService;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.sample.KernelSampleQuestionService;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

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
                    assertThat(context).hasSingleBean(KernelMemoryManagementService.class);
                    assertThat(context).hasSingleBean(KernelMemoryGovernanceService.class);
                    assertThat(context).hasSingleBean(MemoryManagementInboundPort.class);
                    assertThat(context).hasSingleBean(MemoryGovernanceInboundPort.class);
                    assertThat(context).hasSingleBean(SeahorseMemoryGovernanceJob.class);
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

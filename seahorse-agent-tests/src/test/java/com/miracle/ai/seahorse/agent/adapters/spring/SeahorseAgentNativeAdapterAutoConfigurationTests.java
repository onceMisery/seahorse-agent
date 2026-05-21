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
import com.miracle.ai.seahorse.agent.adapters.ai.openai.OpenAiCompatibleModelAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.local.LocalCacheAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.local.LocalSemaphoreAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.ClasspathPromptTemplateAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalIntentGuidanceAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalIntentResolutionAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalDocumentFetcherAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalQueryRewriteAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalRagPromptAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalRetrievalContextFormatAdapter;
import com.miracle.ai.seahorse.agent.adapters.observation.noop.NoopObservationAdapter;
import com.miracle.ai.seahorse.agent.adapters.parser.tika.TikaDocumentParserAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcConversationMemoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcConversationRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcCorrectionLedgerRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcDashboardRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcDocumentRefreshScheduleAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcIntentTreeRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeBaseRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeBaseQueryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeChunkRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeDocumentRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcLongTermMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryAliasRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryConflictLogRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryGraphRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryMaintenanceRunRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryQualitySnapshotRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryReviewCandidateRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryReviewFeedbackRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMessageFeedbackRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataGovernanceRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataSchemaIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcOutboxEventRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcPipelineDefinitionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcProfileMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcQueryTermMappingRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcRagTraceRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcRetrievalStrategyTemplateRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSampleQuestionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSemanticMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcShortTermMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcUserRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcWorkingMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchMetadataSchemaIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.mq.direct.DirectMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.keyword.KeywordIndexMessageSubscriber;
import com.miracle.ai.seahorse.agent.adapters.spring.keyword.KeywordIndexOutboxAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.ReliableMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.storage.local.LocalObjectStorageAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.noop.NoopVectorStoreAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedSemaphorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalContextFormatPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import io.milvus.v2.client.MilvusClientV2;
import okhttp3.OkHttpClient;
import okhttp3.Protocol;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SeahorseAgentNativeAdapterAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentNativeAdapterAutoConfiguration.class));

    @TempDir
    Path tempDir;

    @Test
    void shouldRegisterLocalAndNoopAdaptersWhenExplicitlySelected() {
        contextRunner.withPropertyValues(
                        "seahorse-agent.adapters.cache.type=local",
                        "seahorse-agent.adapters.mq.type=direct",
                        "seahorse-agent.adapters.storage.type=local",
                        "seahorse-agent.adapters.vector.type=noop",
                        "seahorse-agent.adapters.observation.type=noop")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LocalCacheAdapter.class);
                    assertThat(context).hasSingleBean(LocalSemaphoreAdapter.class);
                    assertThat(context).hasSingleBean(LocalObjectStorageAdapter.class);
                    assertThat(context).hasSingleBean(TikaDocumentParserAdapter.class);
                    assertThat(context).hasSingleBean(LocalQueryRewriteAdapter.class);
                    assertThat(context).hasSingleBean(LocalIntentResolutionAdapter.class);
                    assertThat(context).hasSingleBean(LocalIntentGuidanceAdapter.class);
                    assertThat(context).hasSingleBean(ClasspathPromptTemplateAdapter.class);
                    assertThat(context).hasSingleBean(LocalRagPromptAdapter.class);
                    assertThat(context).hasSingleBean(LocalRetrievalContextFormatAdapter.class);
                    assertThat(context).hasSingleBean(LocalDocumentFetcherAdapter.class);
                    assertThat(context).hasSingleBean(CompositeDocumentFetcherPort.class);
                    assertThat(context).hasSingleBean(NoopVectorStoreAdapter.class);
                    assertThat(context).hasSingleBean(NoopObservationAdapter.class);
                    assertThat(context).hasSingleBean(SpringCronSchedulerPort.class);
                    assertThat(context).hasSingleBean(KeyValueCachePort.class);
                    assertThat(context).hasSingleBean(DistributedSemaphorePort.class);
                    assertThat(context).hasSingleBean(ObjectStoragePort.class);
                    assertThat(context.getBean(DocumentFetcherPort.class))
                            .isInstanceOf(CompositeDocumentFetcherPort.class);
                    assertThat(context).hasSingleBean(DocumentParserPort.class);
                    assertThat(context).hasSingleBean(QueryRewritePort.class);
                    assertThat(context).hasSingleBean(IntentResolutionPort.class);
                    assertThat(context).hasSingleBean(IntentGuidancePort.class);
                    assertThat(context).hasSingleBean(PromptTemplatePort.class);
                    assertThat(context).hasSingleBean(RagPromptPort.class);
                    assertThat(context).hasSingleBean(RetrievalContextFormatPort.class);
                    assertThat(context).hasSingleBean(ReliableMessageQueueAdapter.class);
                    assertThat(context).hasSingleBean(MessageQueuePort.class);
                    assertThat(context).hasSingleBean(MessageSubscriptionPort.class);
                    assertThat(context).hasSingleBean(ObservationPort.class);
                    assertThat(context).hasSingleBean(VectorSearchPort.class);
                    assertThat(context).hasSingleBean(VectorIndexPort.class);
                    assertThat(context).hasSingleBean(VectorCollectionAdminPort.class);
                    assertThat(context).hasSingleBean(SchedulerPort.class);
                });
    }

    @Test
    void shouldStartWithoutOptionalHeavyAdaptersOnClasspath() {
        contextRunner.withClassLoader(new FilteredClassLoader(
                        "com.miracle.ai.seahorse.agent.adapters.ai.openai",
                        "com.miracle.ai.seahorse.agent.adapters.parser.tika",
                        "com.miracle.ai.seahorse.agent.adapters.search.elasticsearch",
                        "com.miracle.ai.seahorse.agent.adapters.search.lucene",
                        "com.miracle.ai.seahorse.agent.adapters.vector.milvus",
                        "com.miracle.ai.seahorse.agent.adapters.vector.pgvector",
                        "com.miracle.ai.seahorse.agent.adapters.cache.redis",
                        "com.miracle.ai.seahorse.agent.adapters.storage.s3",
                        "com.miracle.ai.seahorse.agent.adapters.mq.pulsar",
                        "com.miracle.ai.seahorse.agent.adapters.observation.micrometer",
                        "io.milvus.v2.client",
                        "org.apache.pulsar.client.api",
                        "org.redisson.api",
                        "software.amazon.awssdk.services.s3",
                        "io.micrometer.core.instrument"))
                .withPropertyValues(
                        "seahorse-agent.adapters.cache.type=local",
                        "seahorse-agent.adapters.mq.type=direct",
                        "seahorse-agent.adapters.storage.type=local",
                        "seahorse-agent.adapters.vector.type=noop",
                        "seahorse-agent.adapters.observation.type=noop")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).doesNotHaveBean(TikaDocumentParserAdapter.class);
                    assertThat(context).doesNotHaveBean(OpenAiCompatibleModelAdapter.class);
                    assertThat(context).hasSingleBean(LocalObjectStorageAdapter.class);
                    assertThat(context).hasSingleBean(NoopVectorStoreAdapter.class);
                    assertThat(context).hasSingleBean(NoopObservationAdapter.class);
                });
    }

    @Test
    void shouldRegisterJdbcKnowledgeRepositoryWhenDataSourceExists() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:native-adapter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");

        contextRunner.withBean(DriverManagerDataSource.class, () -> dataSource)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(OkHttpClient.class, OkHttpClient::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcKnowledgeBaseQueryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcKnowledgeBaseRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcKnowledgeChunkRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcKnowledgeDocumentRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcDocumentRefreshScheduleAdapter.class);
                    assertThat(context).hasSingleBean(JdbcPipelineDefinitionRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcConversationMemoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcConversationRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMessageFeedbackRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcOutboxEventRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcRagTraceRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcSampleQuestionRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcDashboardRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcIntentTreeRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcQueryTermMappingRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcUserRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcWorkingMemoryRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcShortTermMemoryRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcLongTermMemoryRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcSemanticMemoryRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcProfileMemoryRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcCorrectionLedgerRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMemoryQualitySnapshotRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMemoryConflictLogRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMemoryReviewCandidateRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMemoryReviewFeedbackRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMemoryMaintenanceRunRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMemoryAliasRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMemoryGraphRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMetadataGovernanceRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcKeywordSearchAdapter.class);
                    assertThat(context).hasSingleBean(JdbcKeywordIndexAdapter.class);
                    assertThat(context).hasSingleBean(JdbcRetrievalStrategyTemplateRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(KnowledgeBaseQueryPort.class);
                    assertThat(context).hasSingleBean(KnowledgeChunkRepositoryPort.class);
                    assertThat(context).hasSingleBean(KnowledgeDocumentRepositoryPort.class);
                    assertThat(context).hasSingleBean(PipelineDefinitionRepositoryPort.class);
                    assertThat(context).hasSingleBean(ConversationMemoryPort.class);
                    assertThat(context).hasSingleBean(ConversationRepositoryPort.class);
                    assertThat(context).hasSingleBean(MessageFeedbackRepositoryPort.class);
                    assertThat(context).hasSingleBean(OutboxEventRepositoryPort.class);
                    assertThat(context).hasSingleBean(RagTraceRepositoryPort.class);
                    assertThat(context).hasSingleBean(SampleQuestionRepositoryPort.class);
                    assertThat(context).hasSingleBean(DashboardRepositoryPort.class);
                    assertThat(context).hasSingleBean(IntentTreeRepositoryPort.class);
                    assertThat(context).hasSingleBean(QueryTermMappingRepositoryPort.class);
                    assertThat(context).hasSingleBean(KnowledgeBaseRepositoryPort.class);
                    assertThat(context).hasSingleBean(DocumentRefreshSchedulePort.class);
                    assertThat(context).hasSingleBean(DocumentRefreshStateRepositoryPort.class);
                    assertThat(context).hasSingleBean(UserRepositoryPort.class);
                    assertThat(context).hasSingleBean(PasswordHasherPort.class);
                    assertThat(context).hasSingleBean(TokenServicePort.class);
                    assertThat(context).hasSingleBean(CurrentUserPort.class);
                    assertThat(context).hasSingleBean(WorkingMemoryPort.class);
                    assertThat(context).hasSingleBean(ShortTermMemoryPort.class);
                    assertThat(context).hasSingleBean(LongTermMemoryPort.class);
                    assertThat(context).hasSingleBean(SemanticMemoryPort.class);
                    assertThat(context).hasSingleBean(ProfileMemoryPort.class);
                    assertThat(context).hasSingleBean(CorrectionLedgerPort.class);
                    assertThat(context).hasSingleBean(MemoryOperationLogPort.class);
                    assertThat(context).hasSingleBean(MemoryQualitySnapshotRepositoryPort.class);
                    assertThat(context).hasSingleBean(MemoryConflictLogRepositoryPort.class);
                    assertThat(context).hasSingleBean(MemoryKeywordSearchPort.class);
                    assertThat(context).hasSingleBean(MemoryReviewCandidatePort.class);
                    assertThat(context).hasSingleBean(MemoryReviewManagementRepositoryPort.class);
                    assertThat(context).hasSingleBean(MemoryReviewFeedbackRepositoryPort.class);
                    assertThat(context).hasSingleBean(MemoryMaintenanceRunRepositoryPort.class);
                    assertThat(context).hasSingleBean(MemoryAliasPort.class);
                    assertThat(context).hasSingleBean(MemoryGraphPort.class);
                    assertThat(context).hasSingleBean(MemoryGraphIndexPort.class);
                    assertThat(context).hasSingleBean(MetadataBackfillJobRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataQualityReportRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataSchemaUsageReportRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataReviewManagementRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataQuarantineManagementRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataSchemaManagementRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataSchemaIndexStatusPort.class);
                    assertThat(context).hasSingleBean(MetadataDictionaryManagementRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataExtractionResultManagementRepositoryPort.class);
                    assertThat(context).hasSingleBean(RetrievalStrategyTemplateRepositoryPort.class);
                    assertThat(context.getBean(KeywordSearchPort.class))
                            .isInstanceOf(JdbcKeywordSearchAdapter.class);
                    assertThat(context.getBean(KeywordIndexPort.class))
                            .isInstanceOf(JdbcKeywordIndexAdapter.class);
                });
    }

    @Test
    void shouldRegisterElasticsearchKeywordAdaptersWhenSelected() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:native-keyword-es;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");

        contextRunner.withBean(DriverManagerDataSource.class, () -> dataSource)
                .withBean(OkHttpClient.class, OkHttpClient::new)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "seahorse-agent.adapters.keyword-search.type=elasticsearch",
                        "seahorse-agent.adapters.keyword-index.type=elasticsearch",
                        "seahorse-agent.adapters.keyword-search.elasticsearch.index-name=test_chunks",
                        "seahorse-agent.adapters.keyword-index.elasticsearch.index-name=test_chunks")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ElasticsearchKeywordSearchAdapter.class);
                    assertThat(context).hasSingleBean(ElasticsearchKeywordIndexAdapter.class);
                    assertThat(context).doesNotHaveBean(JdbcKeywordSearchAdapter.class);
                    assertThat(context).doesNotHaveBean(JdbcKeywordIndexAdapter.class);
                    assertThat(context.getBean(KeywordSearchPort.class))
                            .isInstanceOf(ElasticsearchKeywordSearchAdapter.class);
                    assertThat(context.getBean(KeywordIndexPort.class))
                            .isInstanceOf(ElasticsearchKeywordIndexAdapter.class);
                });
    }

    @Test
    void shouldRegisterOpenAiStreamingExecutorWhenOpenAiAdapterSelected() {
        contextRunner.withBean(OkHttpClient.class, OkHttpClient::new)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "seahorse-agent.adapters.ai.type=openai-compatible",
                        "seahorse-agent.adapters.ai.chat-model=gpt-test",
                        "seahorse-agent.adapters.ai.streaming-executor.core-size=1",
                        "seahorse-agent.adapters.ai.streaming-executor.max-size=2",
                        "seahorse-agent.adapters.ai.streaming-executor.queue-capacity=4")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OpenAiCompatibleModelAdapter.class);
                    assertThat(context).hasBean("openAiStreamingExecutor");
                });
    }

    @Test
    void shouldCreateDefaultOkHttpClientForOpenAiAdapter() {
        contextRunner.withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "seahorse-agent.adapters.ai.type=openai-compatible",
                        "seahorse-agent.adapters.ai.chat-model=gpt-test")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OkHttpClient.class);
                    assertThat(context).hasSingleBean(OpenAiCompatibleModelAdapter.class);
                });
    }

    @Test
    void shouldApplyConfiguredOkHttpProtocols() {
        contextRunner.withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "seahorse-agent.adapters.ai.type=openai-compatible",
                        "seahorse-agent.adapters.ai.chat-model=gpt-test",
                        "seahorse-agent.adapters.http.protocols=http/1.1")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBean(OkHttpClient.class).protocols())
                            .containsExactly(Protocol.HTTP_1_1);
                });
    }

    @Test
    void shouldRegisterLuceneKeywordAdaptersWhenSelected() {
        contextRunner.withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "seahorse-agent.adapters.keyword-search.type=lucene",
                        "seahorse-agent.adapters.keyword-index.type=lucene",
                        "seahorse-agent.adapters.keyword-search.lucene.index-directory=" + lucenePath("adapters"),
                        "seahorse-agent.adapters.keyword-index.lucene.index-directory=" + lucenePath("adapters"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LuceneKeywordSearchAdapter.class);
                    assertThat(context).hasSingleBean(LuceneKeywordIndexAdapter.class);
                    assertThat(context).doesNotHaveBean(JdbcKeywordSearchAdapter.class);
                    assertThat(context).doesNotHaveBean(JdbcKeywordIndexAdapter.class);
                    assertThat(context.getBean(KeywordSearchPort.class))
                            .isInstanceOf(LuceneKeywordSearchAdapter.class);
                    assertThat(context.getBean(KeywordIndexPort.class))
                            .isInstanceOf(LuceneKeywordIndexAdapter.class);
                });
    }

    @Test
    void shouldRegisterMilvusVectorAdapterWithConfigurableProperties() {
        contextRunner.withBean(MilvusClientV2.class, () -> mock(MilvusClientV2.class))
                .withPropertyValues(
                        "seahorse-agent.adapters.vector.type=milvus",
                        "seahorse-agent.adapters.vector.collection-name=kb_chunks",
                        "seahorse-agent.adapters.vector.dimension=8",
                        "seahorse-agent.adapters.vector.metric-type=COSINE",
                        "seahorse-agent.adapters.vector.milvus.content-max-length=2048",
                        "seahorse-agent.adapters.vector.milvus.hnsw.m=16",
                        "seahorse-agent.adapters.vector.milvus.hnsw.ef-construction=96",
                        "seahorse-agent.adapters.vector.milvus.mmap-enabled=true",
                        "seahorse-agent.adapters.vector.milvus.search-ef=64")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MilvusVectorAdapter.class);
                    assertThat(context.getBean(VectorSearchPort.class)).isInstanceOf(MilvusVectorAdapter.class);
                });
    }

    @Test
    void shouldWrapKeywordIndexPortWithOutboxWhenConfigured() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:native-keyword-outbox;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        DirectMessageQueueAdapter queue = new DirectMessageQueueAdapter();

        contextRunner.withBean(DriverManagerDataSource.class, () -> dataSource)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(DirectMessageQueueAdapter.class, () -> queue)
                .withPropertyValues("seahorse-agent.adapters.keyword-index.mode=outbox")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcKeywordIndexAdapter.class);
                    assertThat(context).hasSingleBean(KeywordIndexOutboxAdapter.class);
                    assertThat(context).hasSingleBean(KeywordIndexMessageSubscriber.class);
                    // outbox 作为主端口拦截入库链路，真实索引写入由订阅端选择 JDBC/ES delegate。
                    assertThat(context.getBean(KeywordIndexPort.class))
                            .isInstanceOf(KeywordIndexOutboxAdapter.class);
                });
    }

    @Test
    void shouldAllowLuceneKeywordIndexDelegateWhenOutboxConfigured() {
        DirectMessageQueueAdapter queue = new DirectMessageQueueAdapter();

        contextRunner.withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(DirectMessageQueueAdapter.class, () -> queue)
                .withPropertyValues(
                        "seahorse-agent.adapters.keyword-index.type=lucene",
                        "seahorse-agent.adapters.keyword-index.mode=outbox",
                        "seahorse-agent.adapters.keyword-index.lucene.index-directory=" + lucenePath("outbox"))
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(LuceneKeywordIndexAdapter.class);
                    assertThat(context).hasSingleBean(KeywordIndexOutboxAdapter.class);
                    assertThat(context).hasSingleBean(KeywordIndexMessageSubscriber.class);
                    assertThat(context.getBean(KeywordIndexPort.class))
                            .isInstanceOf(KeywordIndexOutboxAdapter.class);
                });
    }

    @Test
    void shouldRegisterElasticsearchMetadataSchemaIndexAdapterWhenSelected() {
        contextRunner.withBean(OkHttpClient.class, OkHttpClient::new)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "seahorse-agent.adapters.metadata-schema-index.type=elasticsearch",
                        "seahorse-agent.adapters.metadata-schema-index.elasticsearch.index-name=test_chunks")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ElasticsearchMetadataSchemaIndexAdapter.class);
                    assertThat(context.getBean(MetadataSchemaIndexSyncPort.class))
                            .isInstanceOf(ElasticsearchMetadataSchemaIndexAdapter.class);
                });
    }

    @Test
    void shouldRegisterJdbcMetadataSchemaIndexAdapterWhenSelected() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:native-metadata-index;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");

        contextRunner.withBean(DriverManagerDataSource.class, () -> dataSource)
                .withPropertyValues("seahorse-agent.adapters.metadata-schema-index.type=jdbc")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(JdbcMetadataSchemaIndexAdapter.class);
                    assertThat(context.getBean(MetadataSchemaIndexSyncPort.class))
                            .isInstanceOf(JdbcMetadataSchemaIndexAdapter.class);
                });
    }

    private String lucenePath(String name) {
        return tempDir.resolve(name).toString().replace('\\', '/');
    }
}

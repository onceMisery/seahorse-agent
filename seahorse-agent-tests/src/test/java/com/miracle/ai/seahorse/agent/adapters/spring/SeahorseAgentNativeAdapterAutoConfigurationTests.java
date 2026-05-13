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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcDashboardRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcDocumentRefreshScheduleAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcIntentTreeRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeBaseRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeBaseQueryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeChunkRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeDocumentRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcLongTermMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryConflictLogRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryQualitySnapshotRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMessageFeedbackRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataGovernanceRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcOutboxEventRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcPipelineDefinitionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcQueryTermMappingRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcRagTraceRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSampleQuestionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSemanticMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcShortTermMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcUserRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcWorkingMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchMetadataSchemaIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.ReliableMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.storage.local.LocalObjectStorageAdapter;
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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalContextFormatPort;
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
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentNativeAdapterAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentNativeAdapterAutoConfiguration.class));

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
    void shouldRegisterJdbcKnowledgeRepositoryWhenDataSourceExists() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:native-adapter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");

        contextRunner.withBean(DriverManagerDataSource.class, () -> dataSource)
                .withBean(ObjectMapper.class, ObjectMapper::new)
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
                    assertThat(context).hasSingleBean(JdbcMemoryQualitySnapshotRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMemoryConflictLogRepositoryAdapter.class);
                    assertThat(context).hasSingleBean(JdbcMetadataGovernanceRepositoryAdapter.class);
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
                    assertThat(context).hasSingleBean(MemoryQualitySnapshotRepositoryPort.class);
                    assertThat(context).hasSingleBean(MemoryConflictLogRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataBackfillJobRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataQualityReportRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataReviewManagementRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataQuarantineManagementRepositoryPort.class);
                    assertThat(context).hasSingleBean(MetadataSchemaManagementRepositoryPort.class);
                });
    }

    @Test
    void shouldRegisterElasticsearchKeywordAdaptersWhenSelected() {
        contextRunner.withBean(OkHttpClient.class, OkHttpClient::new)
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
                    assertThat(context.getBean(KeywordSearchPort.class))
                            .isInstanceOf(ElasticsearchKeywordSearchAdapter.class);
                    assertThat(context.getBean(KeywordIndexPort.class))
                            .isInstanceOf(ElasticsearchKeywordIndexAdapter.class);
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
}

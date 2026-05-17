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
import com.miracle.ai.seahorse.agent.adapters.ai.openai.OpenAiCompatibleModelProperties;
import com.miracle.ai.seahorse.agent.adapters.local.ClasspathPromptTemplateAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalIntentGuidanceAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalIntentResolutionAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalDocumentFetcherAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalIngestionNodeLogAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalQueryRewriteAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalRagPromptAdapter;
import com.miracle.ai.seahorse.agent.adapters.local.LocalRetrievalContextFormatAdapter;
import com.miracle.ai.seahorse.agent.adapters.mq.direct.DirectMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.mq.pulsar.PulsarMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.mq.pulsar.PulsarMessageQueueProperties;
import com.miracle.ai.seahorse.agent.adapters.parser.tika.TikaDocumentParserAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcUserRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcConversationRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcConversationMemoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcDashboardRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcDocumentRefreshScheduleAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentExtensionStatusAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcIngestionTaskRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcIntentTreeRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeBaseRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeChunkRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeBaseQueryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeDocumentRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcLongTermMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryConflictLogRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryQualitySnapshotRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMessageFeedbackRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataGovernanceRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataSchemaIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcOutboxEventRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcPipelineDefinitionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcQueryTermMappingRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcRagTraceRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcRetrievalEvaluationDatasetRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcRetrievalStrategyTemplateRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSampleQuestionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSemanticMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcShortTermMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcWorkingMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchMetadataSchemaIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordProperties;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordProperties;
import com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.ReliableMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.SeahorseOutboxRelayJob;
import com.miracle.ai.seahorse.agent.adapters.spring.keyword.KeywordIndexMessageSubscriber;
import com.miracle.ai.seahorse.agent.adapters.spring.keyword.KeywordIndexOutboxAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.SaTokenCurrentUserAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.SaTokenServiceAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.SeahorseSaTokenStpInterface;
import com.miracle.ai.seahorse.agent.adapters.web.SpringCurrentUserAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorProperties;
import com.miracle.ai.seahorse.agent.adapters.vector.noop.NoopVectorStoreAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.pgvector.PgVectorAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.pgvector.PgVectorProperties;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionNodeLogPort;
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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalContextFormatPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;
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
import org.apache.pulsar.client.api.PulsarClient;
import cn.dev33.satoken.stp.StpInterface;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executor;

/**
 * Seahorse 原生 L3 adapter 自动装配。
 *
 * <p>该配置把 Seahorse 原生 adapter 注册为端口 Bean，并通过 {@code ConditionalOnMissingBean}
 * 保持可插拔替换能力。
 */
@AutoConfiguration
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
@Import({
        SeahorseAgentCacheAdapterAutoConfiguration.class,
        SeahorseAgentObservationAdapterAutoConfiguration.class,
        SeahorseAgentStorageAdapterAutoConfiguration.class
})
public class SeahorseAgentNativeAdapterAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.mq", name = "type", havingValue = "direct")
    @ConditionalOnMissingBean(MessageQueuePort.class)
    public ReliableMessageQueueAdapter seahorseDirectMessageQueueAdapter(
            ObjectProvider<OutboxEventRepositoryPort> outboxRepositoryPort,
            ObjectProvider<ObjectMapper> objectMapper) {
        DirectMessageQueueAdapter delegate = new DirectMessageQueueAdapter();
        return new ReliableMessageQueueAdapter(delegate, delegate,
                outboxRepositoryPort::getIfAvailable, objectMapper::getIfAvailable);
    }

    @Bean
    @ConditionalOnBean(PulsarClient.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.mq", name = "type", havingValue = "pulsar", matchIfMissing = true)
    @ConditionalOnMissingBean(MessageQueuePort.class)
    public ReliableMessageQueueAdapter seahorsePulsarMessageQueueAdapter(
            PulsarClient pulsarClient,
            ObjectMapper objectMapper,
            ObjectProvider<OutboxEventRepositoryPort> outboxRepositoryPort) {
        PulsarMessageQueueAdapter delegate = new PulsarMessageQueueAdapter(
                pulsarClient, objectMapper, new PulsarMessageQueueProperties());
        return new ReliableMessageQueueAdapter(delegate, delegate, outboxRepositoryPort::getIfAvailable, () -> objectMapper);
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.parser", name = "type", havingValue = "tika",
            matchIfMissing = true)
    @ConditionalOnMissingBean(DocumentParserPort.class)
    public TikaDocumentParserAdapter seahorseTikaDocumentParserAdapter() {
        return new TikaDocumentParserAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.chat.rewrite", name = "type", havingValue = "local",
            matchIfMissing = true)
    @ConditionalOnMissingBean(QueryRewritePort.class)
    public LocalQueryRewriteAdapter seahorseLocalQueryRewriteAdapter() {
        return new LocalQueryRewriteAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.chat.intent", name = "type", havingValue = "local",
            matchIfMissing = true)
    @ConditionalOnMissingBean(IntentResolutionPort.class)
    public LocalIntentResolutionAdapter seahorseLocalIntentResolutionAdapter() {
        return new LocalIntentResolutionAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.chat.guidance", name = "type", havingValue = "local",
            matchIfMissing = true)
    @ConditionalOnMissingBean(IntentGuidancePort.class)
    public LocalIntentGuidanceAdapter seahorseLocalIntentGuidanceAdapter() {
        return new LocalIntentGuidanceAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.prompt", name = "type", havingValue = "classpath",
            matchIfMissing = true)
    @ConditionalOnMissingBean(PromptTemplatePort.class)
    public ClasspathPromptTemplateAdapter seahorseClasspathPromptTemplateAdapter() {
        return new ClasspathPromptTemplateAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.rag-prompt", name = "type", havingValue = "local",
            matchIfMissing = true)
    @ConditionalOnMissingBean(RagPromptPort.class)
    public LocalRagPromptAdapter seahorseLocalRagPromptAdapter() {
        return new LocalRagPromptAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.retrieval-context", name = "type", havingValue = "local",
            matchIfMissing = true)
    @ConditionalOnMissingBean(RetrievalContextFormatPort.class)
    public LocalRetrievalContextFormatAdapter seahorseLocalRetrievalContextFormatAdapter() {
        return new LocalRetrievalContextFormatAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(IngestionNodeLogPort.class)
    public LocalIngestionNodeLogAdapter seahorseLocalIngestionNodeLogAdapter() {
        return new LocalIngestionNodeLogAdapter();
    }

    @Bean
    @ConditionalOnMissingBean(SchedulerPort.class)
    public SpringCronSchedulerPort seahorseSpringCronSchedulerPort() {
        return new SpringCronSchedulerPort();
    }

    @Bean
    @ConditionalOnMissingBean(LocalDocumentFetcherAdapter.class)
    public LocalDocumentFetcherAdapter seahorseLocalDocumentFetcherAdapter(
            ObjectProvider<ObjectStoragePort> objectStoragePort) {
        return new LocalDocumentFetcherAdapter(objectStoragePort.getIfAvailable());
    }

    @Bean
    @Primary
    @ConditionalOnMissingBean(CompositeDocumentFetcherPort.class)
    public CompositeDocumentFetcherPort seahorseCompositeDocumentFetcherPort(
            List<DocumentFetcherPort> documentFetcherPorts) {
        return new CompositeDocumentFetcherPort(documentFetcherPorts);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(UserRepositoryPort.class)
    public JdbcUserRepositoryAdapter seahorseJdbcUserRepositoryAdapter(DataSource dataSource) {
        return new JdbcUserRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(PasswordHasherPort.class)
    public PasswordHasherPort seahorsePasswordHasherPort() {
        return PasswordHasherPort.plainText();
    }

    @Bean
    @ConditionalOnMissingBean(TokenServicePort.class)
    public SaTokenServiceAdapter seahorseSaTokenServiceAdapter() {
        return new SaTokenServiceAdapter();
    }

    @Bean
    @ConditionalOnBean(UserRepositoryPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.auth", name = "current-user", havingValue = "sa-token",
            matchIfMissing = true)
    @ConditionalOnMissingBean(CurrentUserPort.class)
    public SaTokenCurrentUserAdapter seahorseSaTokenCurrentUserAdapter(UserRepositoryPort userRepositoryPort) {
        return new SaTokenCurrentUserAdapter(userRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(UserRepositoryPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.auth", name = "current-user", havingValue = "spring-header")
    @ConditionalOnMissingBean(CurrentUserPort.class)
    public SpringCurrentUserAdapter seahorseSpringCurrentUserAdapter(UserRepositoryPort userRepositoryPort) {
        return new SpringCurrentUserAdapter(userRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(UserRepositoryPort.class)
    @ConditionalOnMissingBean(StpInterface.class)
    public SeahorseSaTokenStpInterface seahorseSaTokenStpInterface(UserRepositoryPort userRepositoryPort) {
        return new SeahorseSaTokenStpInterface(userRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeBaseQueryPort.class)
    public JdbcKnowledgeBaseQueryAdapter seahorseJdbcKnowledgeBaseQueryAdapter(DataSource dataSource) {
        return new JdbcKnowledgeBaseQueryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean({OkHttpClient.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.keyword-search", name = "type",
            havingValue = "elasticsearch")
    @ConditionalOnMissingBean(ElasticsearchKeywordSearchAdapter.class)
    public ElasticsearchKeywordSearchAdapter seahorseElasticsearchKeywordSearchAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.base-url:http://localhost:9200}")
            String baseUrl,
            @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.index-name:seahorse_keyword_chunk}")
            String indexName,
            @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.search-fields:content^3}")
            String searchFields,
            @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.analyzer:}")
            String analyzer,
            @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.minimum-should-match:}")
            String minimumShouldMatch,
            @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.api-key:}")
            String apiKey,
            @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.username:}")
            String username,
            @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.password:}")
            String password,
            @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.timeout:10s}")
            String timeout) {
        return new ElasticsearchKeywordSearchAdapter(httpClient, objectMapper,
                new ElasticsearchKeywordProperties(baseUrl, indexName, csv(searchFields), analyzer,
                        minimumShouldMatch, apiKey, username, password, duration(timeout)));
    }

    @Bean
    @ConditionalOnBean(ElasticsearchKeywordSearchAdapter.class)
    @ConditionalOnMissingBean(KeywordSearchPort.class)
    public KeywordSearchPort seahorseElasticsearchKeywordSearchPort(ElasticsearchKeywordSearchAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.keyword-search", name = "type",
            havingValue = "lucene")
    @ConditionalOnMissingBean(LuceneKeywordSearchAdapter.class)
    public LuceneKeywordSearchAdapter seahorseLuceneKeywordSearchAdapter(
            ObjectMapper objectMapper,
            @Value("${seahorse-agent.adapters.keyword-search.lucene.index-directory:${java.io.tmpdir}/seahorse-agent-lucene-keyword}")
            String indexDirectory,
            @Value("${seahorse-agent.adapters.keyword-search.lucene.search-fields:content^3}")
            String searchFields) {
        return new LuceneKeywordSearchAdapter(objectMapper,
                new LuceneKeywordProperties(Path.of(indexDirectory), csv(searchFields)));
    }

    @Bean
    @ConditionalOnBean(LuceneKeywordSearchAdapter.class)
    @ConditionalOnMissingBean(KeywordSearchPort.class)
    public KeywordSearchPort seahorseLuceneKeywordSearchPort(LuceneKeywordSearchAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KeywordSearchPort.class)
    public JdbcKeywordSearchAdapter seahorseJdbcKeywordSearchAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcKeywordSearchAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean({OkHttpClient.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.keyword-index", name = "type",
            havingValue = "elasticsearch")
    @ConditionalOnMissingBean(ElasticsearchKeywordIndexAdapter.class)
    public ElasticsearchKeywordIndexAdapter seahorseElasticsearchKeywordIndexAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.base-url:http://localhost:9200}")
            String baseUrl,
            @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.index-name:seahorse_keyword_chunk}")
            String indexName,
            @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.search-fields:content^3}")
            String searchFields,
            @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.api-key:}")
            String apiKey,
            @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.username:}")
            String username,
            @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.password:}")
            String password,
            @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.timeout:10s}")
            String timeout) {
        return new ElasticsearchKeywordIndexAdapter(httpClient, objectMapper,
                new ElasticsearchKeywordProperties(baseUrl, indexName, csv(searchFields), apiKey, username, password,
                        duration(timeout)));
    }

    @Bean
    @ConditionalOnBean(ElasticsearchKeywordIndexAdapter.class)
    @ConditionalOnMissingBean(value = KeywordIndexPort.class, ignored = KeywordIndexOutboxAdapter.class)
    public KeywordIndexPort seahorseElasticsearchKeywordIndexPort(ElasticsearchKeywordIndexAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(ObjectMapper.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.keyword-index", name = "type",
            havingValue = "lucene")
    @ConditionalOnMissingBean(LuceneKeywordIndexAdapter.class)
    public LuceneKeywordIndexAdapter seahorseLuceneKeywordIndexAdapter(
            ObjectMapper objectMapper,
            @Value("${seahorse-agent.adapters.keyword-index.lucene.index-directory:${java.io.tmpdir}/seahorse-agent-lucene-keyword}")
            String indexDirectory,
            @Value("${seahorse-agent.adapters.keyword-index.lucene.search-fields:content^3}")
            String searchFields) {
        return new LuceneKeywordIndexAdapter(objectMapper,
                new LuceneKeywordProperties(Path.of(indexDirectory), csv(searchFields)));
    }

    @Bean
    @ConditionalOnBean(LuceneKeywordIndexAdapter.class)
    @ConditionalOnMissingBean(value = KeywordIndexPort.class, ignored = KeywordIndexOutboxAdapter.class)
    public KeywordIndexPort seahorseLuceneKeywordIndexPort(LuceneKeywordIndexAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean({OkHttpClient.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.metadata-schema-index", name = "type",
            havingValue = "elasticsearch")
    @ConditionalOnMissingBean(ElasticsearchMetadataSchemaIndexAdapter.class)
    public ElasticsearchMetadataSchemaIndexAdapter seahorseElasticsearchMetadataSchemaIndexAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            ObjectProvider<ObservationPort> observationPort,
            ObjectProvider<MetadataSchemaIndexStatusPort> indexStatusPort,
            @Value("${seahorse-agent.adapters.metadata-schema-index.elasticsearch.base-url:http://localhost:9200}")
            String baseUrl,
            @Value("${seahorse-agent.adapters.metadata-schema-index.elasticsearch.index-name:seahorse_keyword_chunk}")
            String indexName,
            @Value("${seahorse-agent.adapters.metadata-schema-index.elasticsearch.search-fields:content^3}")
            String searchFields,
            @Value("${seahorse-agent.adapters.metadata-schema-index.elasticsearch.api-key:}")
            String apiKey,
            @Value("${seahorse-agent.adapters.metadata-schema-index.elasticsearch.username:}")
            String username,
            @Value("${seahorse-agent.adapters.metadata-schema-index.elasticsearch.password:}")
            String password,
            @Value("${seahorse-agent.adapters.metadata-schema-index.elasticsearch.timeout:10s}")
            String timeout) {
        return new ElasticsearchMetadataSchemaIndexAdapter(httpClient, objectMapper,
                new ElasticsearchKeywordProperties(baseUrl, indexName, csv(searchFields), apiKey, username, password,
                        duration(timeout)),
                observationPort.getIfAvailable(),
                indexStatusPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(ElasticsearchMetadataSchemaIndexAdapter.class)
    @ConditionalOnMissingBean(MetadataSchemaIndexSyncPort.class)
    public MetadataSchemaIndexSyncPort seahorseElasticsearchMetadataSchemaIndexSyncPort(
            ElasticsearchMetadataSchemaIndexAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.metadata-schema-index", name = "type",
            havingValue = "jdbc")
    @ConditionalOnMissingBean(MetadataSchemaIndexSyncPort.class)
    public JdbcMetadataSchemaIndexAdapter seahorseJdbcMetadataSchemaIndexAdapter(
            DataSource dataSource,
            ObjectProvider<ObservationPort> observationPort,
            ObjectProvider<MetadataSchemaIndexStatusPort> indexStatusPort) {
        return new JdbcMetadataSchemaIndexAdapter(dataSource,
                observationPort.getIfAvailable(),
                indexStatusPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataSchemaIndexStatusPort.class)
    public MetadataSchemaIndexStatusPort seahorseMetadataSchemaIndexStatusPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(value = KeywordIndexPort.class, ignored = KeywordIndexOutboxAdapter.class)
    public JdbcKeywordIndexAdapter seahorseJdbcKeywordIndexAdapter(DataSource dataSource) {
        return new JdbcKeywordIndexAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(RetrievalStrategyTemplateRepositoryPort.class)
    public JdbcRetrievalStrategyTemplateRepositoryAdapter seahorseJdbcRetrievalStrategyTemplateRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcRetrievalStrategyTemplateRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(RetrievalEvaluationDatasetRepositoryPort.class)
    public JdbcRetrievalEvaluationDatasetRepositoryAdapter seahorseJdbcRetrievalEvaluationDatasetRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcRetrievalEvaluationDatasetRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @Primary
    @ConditionalOnBean(MessageQueuePort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.keyword-index", name = "mode", havingValue = "outbox")
    @ConditionalOnMissingBean(KeywordIndexOutboxAdapter.class)
    public KeywordIndexOutboxAdapter seahorseKeywordIndexOutboxAdapter(
            MessageQueuePort messageQueuePort,
            @Value("${seahorse-agent.adapters.keyword-index.topic:" + KeywordIndexOutboxAdapter.DEFAULT_TOPIC + "}")
            String topic) {
        return new KeywordIndexOutboxAdapter(messageQueuePort, topic);
    }

    @Bean
    @ConditionalOnBean(MessageSubscriptionPort.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.keyword-index", name = "mode", havingValue = "outbox")
    @ConditionalOnMissingBean(KeywordIndexMessageSubscriber.class)
    public KeywordIndexMessageSubscriber seahorseKeywordIndexMessageSubscriber(
            MessageSubscriptionPort subscriptionPort,
            ObjectProvider<ElasticsearchKeywordIndexAdapter> elasticsearchKeywordIndexAdapter,
            ObjectProvider<LuceneKeywordIndexAdapter> luceneKeywordIndexAdapter,
            ObjectProvider<JdbcKeywordIndexAdapter> jdbcKeywordIndexAdapter,
            ObjectProvider<ObservationPort> observationPort,
            @Value("${seahorse-agent.adapters.keyword-index.topic:" + KeywordIndexOutboxAdapter.DEFAULT_TOPIC + "}")
            String topic,
            @Value("${seahorse-agent.adapters.keyword-index.subscription:seahorse-keyword-index}")
            String subscriptionName) {
        KeywordIndexPort delegate = elasticsearchKeywordIndexAdapter.getIfAvailable();
        if (delegate == null) {
            delegate = luceneKeywordIndexAdapter.getIfAvailable();
        }
        if (delegate == null) {
            delegate = jdbcKeywordIndexAdapter.getIfAvailable();
        }
        if (delegate == null) {
            delegate = KeywordIndexPort.noop();
        }
        return new KeywordIndexMessageSubscriber(subscriptionPort, topic, subscriptionName, delegate,
                observationPort.getIfAvailable());
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeBaseRepositoryPort.class)
    public JdbcKnowledgeBaseRepositoryAdapter seahorseJdbcKnowledgeBaseRepositoryAdapter(DataSource dataSource) {
        return new JdbcKnowledgeBaseRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeChunkRepositoryPort.class)
    public JdbcKnowledgeChunkRepositoryAdapter seahorseJdbcKnowledgeChunkRepositoryAdapter(DataSource dataSource) {
        return new JdbcKnowledgeChunkRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeDocumentRepositoryPort.class)
    public JdbcKnowledgeDocumentRepositoryAdapter seahorseJdbcKnowledgeDocumentRepositoryAdapter(DataSource dataSource) {
        return new JdbcKnowledgeDocumentRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(JdbcDocumentRefreshScheduleAdapter.class)
    public JdbcDocumentRefreshScheduleAdapter seahorseJdbcDocumentRefreshScheduleAdapter(DataSource dataSource) {
        return new JdbcDocumentRefreshScheduleAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(JdbcDocumentRefreshScheduleAdapter.class)
    @ConditionalOnMissingBean(DocumentRefreshSchedulePort.class)
    public DocumentRefreshSchedulePort seahorseDocumentRefreshSchedulePort(
            JdbcDocumentRefreshScheduleAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcDocumentRefreshScheduleAdapter.class)
    @ConditionalOnMissingBean(DocumentRefreshStateRepositoryPort.class)
    public DocumentRefreshStateRepositoryPort seahorseDocumentRefreshStateRepositoryPort(
            JdbcDocumentRefreshScheduleAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(PipelineDefinitionRepositoryPort.class)
    public JdbcPipelineDefinitionRepositoryAdapter seahorseJdbcPipelineDefinitionRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcPipelineDefinitionRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(IngestionTaskRepositoryPort.class)
    public JdbcIngestionTaskRepositoryAdapter seahorseJdbcIngestionTaskRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcIngestionTaskRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(MetadataSchemaRegistryPort.class)
    public JdbcMetadataGovernanceRepositoryAdapter seahorseJdbcMetadataGovernanceRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcMetadataGovernanceRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataDictionaryPort.class)
    public MetadataDictionaryPort seahorseMetadataDictionaryPort(JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataDictionaryManagementRepositoryPort.class)
    public MetadataDictionaryManagementRepositoryPort seahorseMetadataDictionaryManagementRepositoryPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataExtractionResultRepositoryPort.class)
    public MetadataExtractionResultRepositoryPort seahorseMetadataExtractionResultRepositoryPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataExtractionResultManagementRepositoryPort.class)
    public MetadataExtractionResultManagementRepositoryPort seahorseMetadataExtractionResultManagementRepositoryPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataReviewQueuePort.class)
    public MetadataReviewQueuePort seahorseMetadataReviewQueuePort(JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataQuarantinePort.class)
    public MetadataQuarantinePort seahorseMetadataQuarantinePort(JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataCanonicalWritePort.class)
    public MetadataCanonicalWritePort seahorseMetadataCanonicalWritePort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataBackfillJobRepositoryPort.class)
    public MetadataBackfillJobRepositoryPort seahorseMetadataBackfillJobRepositoryPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataQualityReportRepositoryPort.class)
    public MetadataQualityReportRepositoryPort seahorseMetadataQualityReportRepositoryPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataSchemaUsageReportRepositoryPort.class)
    public MetadataSchemaUsageReportRepositoryPort seahorseMetadataSchemaUsageReportRepositoryPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataReviewManagementRepositoryPort.class)
    public MetadataReviewManagementRepositoryPort seahorseMetadataReviewManagementRepositoryPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataQuarantineManagementRepositoryPort.class)
    public MetadataQuarantineManagementRepositoryPort seahorseMetadataQuarantineManagementRepositoryPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    @ConditionalOnMissingBean(MetadataSchemaManagementRepositoryPort.class)
    public MetadataSchemaManagementRepositoryPort seahorseMetadataSchemaManagementRepositoryPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ConversationMemoryPort.class)
    public JdbcConversationMemoryAdapter seahorseJdbcConversationMemoryAdapter(
            DataSource dataSource,
            @Value("${seahorse-agent.plugins.memory.history-keep-turns:10}")
            int historyKeepTurns) {
        return new JdbcConversationMemoryAdapter(dataSource, historyKeepTurns * 2);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(WorkingMemoryPort.class)
    public JdbcWorkingMemoryRepositoryAdapter seahorseJdbcWorkingMemoryRepositoryAdapter(DataSource dataSource) {
        return new JdbcWorkingMemoryRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ShortTermMemoryPort.class)
    public JdbcShortTermMemoryRepositoryAdapter seahorseJdbcShortTermMemoryRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcShortTermMemoryRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(LongTermMemoryPort.class)
    public JdbcLongTermMemoryRepositoryAdapter seahorseJdbcLongTermMemoryRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcLongTermMemoryRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(SemanticMemoryPort.class)
    public JdbcSemanticMemoryRepositoryAdapter seahorseJdbcSemanticMemoryRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcSemanticMemoryRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(MemoryQualitySnapshotRepositoryPort.class)
    public JdbcMemoryQualitySnapshotRepositoryAdapter seahorseJdbcMemoryQualitySnapshotRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcMemoryQualitySnapshotRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(MemoryConflictLogRepositoryPort.class)
    public JdbcMemoryConflictLogRepositoryAdapter seahorseJdbcMemoryConflictLogRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcMemoryConflictLogRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ConversationRepositoryPort.class)
    public JdbcConversationRepositoryAdapter seahorseJdbcConversationRepositoryAdapter(DataSource dataSource) {
        return new JdbcConversationRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(MessageFeedbackRepositoryPort.class)
    public JdbcMessageFeedbackRepositoryAdapter seahorseJdbcMessageFeedbackRepositoryAdapter(DataSource dataSource) {
        return new JdbcMessageFeedbackRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(OutboxEventRepositoryPort.class)
    public JdbcOutboxEventRepositoryAdapter seahorseJdbcOutboxEventRepositoryAdapter(DataSource dataSource) {
        return new JdbcOutboxEventRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(RagTraceRepositoryPort.class)
    public JdbcRagTraceRepositoryAdapter seahorseJdbcRagTraceRepositoryAdapter(DataSource dataSource) {
        return new JdbcRagTraceRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(SampleQuestionRepositoryPort.class)
    public JdbcSampleQuestionRepositoryAdapter seahorseJdbcSampleQuestionRepositoryAdapter(DataSource dataSource) {
        return new JdbcSampleQuestionRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(DashboardRepositoryPort.class)
    public JdbcDashboardRepositoryAdapter seahorseJdbcDashboardRepositoryAdapter(DataSource dataSource) {
        return new JdbcDashboardRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentExtensionStatusPort.class)
    public JdbcAgentExtensionStatusAdapter seahorseJdbcAgentExtensionStatusAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcAgentExtensionStatusAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(IntentTreeRepositoryPort.class)
    public JdbcIntentTreeRepositoryAdapter seahorseJdbcIntentTreeRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcIntentTreeRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(QueryTermMappingRepositoryPort.class)
    public JdbcQueryTermMappingRepositoryAdapter seahorseJdbcQueryTermMappingRepositoryAdapter(DataSource dataSource) {
        return new JdbcQueryTermMappingRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean({OutboxEventRepositoryPort.class, MessageQueuePort.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.mq.outbox.relay", name = "enabled",
            havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(SeahorseOutboxRelayJob.class)
    public SeahorseOutboxRelayJob seahorseOutboxRelayJob(
            OutboxEventRepositoryPort outboxEventRepositoryPort,
            MessageQueuePort messageQueuePort,
            ObjectMapper objectMapper,
            ObjectProvider<DistributedLockPort> lockPort,
            ObjectProvider<MetadataQuarantinePort> quarantinePort,
            @Value("${seahorse-agent.adapters.mq.outbox.relay.batch-size:50}") int batchSize) {
        return new SeahorseOutboxRelayJob(outboxEventRepositoryPort, messageQueuePort, objectMapper,
                lockPort.getIfAvailable(DistributedLockPort::noop),
                quarantinePort.getIfAvailable(MetadataQuarantinePort::noop), batchSize);
    }

    @Bean
    @ConditionalOnBean(MilvusClientV2.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.vector", name = "type", havingValue = "milvus", matchIfMissing = true)
    @ConditionalOnMissingBean(MilvusVectorAdapter.class)
    public MilvusVectorAdapter seahorseMilvusVectorAdapter(
            MilvusClientV2 milvusClient,
            @Value("${seahorse-agent.adapters.vector.collection-name:}")
            String collectionName,
            @Value("${seahorse-agent.adapters.vector.dimension:1024}") int dimension,
            @Value("${seahorse-agent.adapters.vector.metric-type:COSINE}")
            String metricType,
            @Value("${seahorse-agent.adapters.vector.milvus.content-max-length:65535}") int contentMaxLength,
            @Value("${seahorse-agent.adapters.vector.milvus.hnsw.m:48}") int hnswM,
            @Value("${seahorse-agent.adapters.vector.milvus.hnsw.ef-construction:200}") int hnswEfConstruction,
            @Value("${seahorse-agent.adapters.vector.milvus.mmap-enabled:false}") boolean mmapEnabled,
            @Value("${seahorse-agent.adapters.vector.milvus.search-ef:128}") int searchEf) {
        return new MilvusVectorAdapter(milvusClient, new MilvusVectorProperties(
                collectionName, dimension, metricType, contentMaxLength,
                hnswM, hnswEfConstruction, mmapEnabled, searchEf));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.vector", name = "type", havingValue = "pgvector")
    @ConditionalOnMissingBean(PgVectorAdapter.class)
    public PgVectorAdapter seahorsePgVectorAdapter(
            DataSource dataSource,
            ObjectMapper objectMapper,
            @Value("${seahorse-agent.adapters.vector.dimension:1024}") int dimension) {
        return new PgVectorAdapter(dataSource, objectMapper, new PgVectorProperties("t_knowledge_vector", dimension));
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.vector", name = "type", havingValue = "noop")
    @ConditionalOnMissingBean(NoopVectorStoreAdapter.class)
    public NoopVectorStoreAdapter seahorseNoopVectorStoreAdapter() {
        return new NoopVectorStoreAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.ai", name = "type", havingValue = "openai-compatible")
    @ConditionalOnMissingBean(name = "openAiStreamingExecutor")
    public ThreadPoolTaskExecutor openAiStreamingExecutor(
            @Value("${seahorse-agent.adapters.ai.streaming-executor.core-size:4}") int coreSize,
            @Value("${seahorse-agent.adapters.ai.streaming-executor.max-size:32}") int maxSize,
            @Value("${seahorse-agent.adapters.ai.streaming-executor.queue-capacity:200}") int queueCapacity,
            @Value("${seahorse-agent.adapters.ai.streaming-executor.thread-name-prefix:seahorse-openai-stream-}")
            String threadNamePrefix) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(Math.max(1, coreSize));
        executor.setMaxPoolSize(Math.max(Math.max(1, coreSize), maxSize));
        executor.setQueueCapacity(Math.max(0, queueCapacity));
        executor.setThreadNamePrefix(threadNamePrefix);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnBean(OkHttpClient.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.ai", name = "type", havingValue = "openai-compatible")
    @ConditionalOnMissingBean(OpenAiCompatibleModelAdapter.class)
    public OpenAiCompatibleModelAdapter seahorseOpenAiCompatibleModelAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Qualifier("openAiStreamingExecutor") Executor streamingExecutor,
            @Value("${seahorse-agent.adapters.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${seahorse-agent.adapters.ai.api-key:}") String apiKey,
            @Value("${seahorse-agent.adapters.ai.chat-model:}") String chatModel,
            @Value("${seahorse-agent.adapters.ai.embedding-model:}") String embeddingModel,
            @Value("${seahorse-agent.adapters.ai.rerank-model:}") String rerankModel) {
        OpenAiCompatibleModelProperties properties = new OpenAiCompatibleModelProperties(
                baseUrl, apiKey, chatModel, embeddingModel, rerankModel, List.of());
        return new OpenAiCompatibleModelAdapter(httpClient, objectMapper, properties, streamingExecutor);
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(ChatModelPort.class)
    public ChatModelPort seahorseNativeChatModelPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(StreamingChatModelPort.class)
    public StreamingChatModelPort seahorseNativeStreamingChatModelPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(EmbeddingModelPort.class)
    public EmbeddingModelPort seahorseNativeEmbeddingModelPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(RerankModelPort.class)
    public RerankModelPort seahorseNativeRerankModelPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(ModelProviderPort.class)
    public ModelProviderPort seahorseNativeModelProviderPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(TokenCounterPort.class)
    public TokenCounterPort seahorseNativeTokenCounterPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(OpenAiCompatibleModelAdapter.class)
    @ConditionalOnMissingBean(ModelHealthPort.class)
    public ModelHealthPort seahorseNativeModelHealthPort(OpenAiCompatibleModelAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(MilvusVectorAdapter.class)
    @ConditionalOnMissingBean(VectorSearchPort.class)
    public VectorSearchPort seahorseNativeMilvusVectorSearchPort(MilvusVectorAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(MilvusVectorAdapter.class)
    @ConditionalOnMissingBean(VectorIndexPort.class)
    public VectorIndexPort seahorseNativeMilvusVectorIndexPort(MilvusVectorAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(MilvusVectorAdapter.class)
    @ConditionalOnMissingBean(VectorCollectionAdminPort.class)
    public VectorCollectionAdminPort seahorseNativeMilvusVectorAdminPort(MilvusVectorAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(PgVectorAdapter.class)
    @ConditionalOnMissingBean(VectorSearchPort.class)
    public VectorSearchPort seahorseNativePgVectorSearchPort(PgVectorAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(PgVectorAdapter.class)
    @ConditionalOnMissingBean(VectorIndexPort.class)
    public VectorIndexPort seahorseNativePgVectorIndexPort(PgVectorAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(PgVectorAdapter.class)
    @ConditionalOnMissingBean(VectorCollectionAdminPort.class)
    public VectorCollectionAdminPort seahorseNativePgVectorAdminPort(PgVectorAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(NoopVectorStoreAdapter.class)
    @ConditionalOnMissingBean(VectorSearchPort.class)
    public VectorSearchPort seahorseNativeNoopVectorSearchPort(NoopVectorStoreAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(NoopVectorStoreAdapter.class)
    @ConditionalOnMissingBean(VectorIndexPort.class)
    public VectorIndexPort seahorseNativeNoopVectorIndexPort(NoopVectorStoreAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(NoopVectorStoreAdapter.class)
    @ConditionalOnMissingBean(VectorCollectionAdminPort.class)
    public VectorCollectionAdminPort seahorseNativeNoopVectorAdminPort(NoopVectorStoreAdapter adapter) {
        return adapter;
    }

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .toList();
    }

    private static Duration duration(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ofSeconds(10);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        try {
            if (normalized.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
            }
            if (normalized.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            if (normalized.endsWith("m")) {
                return Duration.ofMinutes(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            return Duration.parse(value.trim());
        } catch (RuntimeException ex) {
            return Duration.ofSeconds(10);
        }
    }
}

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
import com.miracle.ai.seahorse.agent.adapters.cache.local.LocalCacheAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.local.LocalSemaphoreAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.redis.RedisCacheAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.redis.RedisSemaphoreAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.redis.RedisStreamTaskPort;
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
import com.miracle.ai.seahorse.agent.adapters.observation.micrometer.MicrometerObservationAdapter;
import com.miracle.ai.seahorse.agent.adapters.observation.noop.NoopObservationAdapter;
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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKeywordSearchAdapter;
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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcWorkingMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.ReliableMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.SeahorseOutboxRelayJob;
import com.miracle.ai.seahorse.agent.adapters.storage.local.LocalObjectStorageAdapter;
import com.miracle.ai.seahorse.agent.adapters.storage.s3.S3ObjectStorageAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.SaTokenCurrentUserAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.SaTokenServiceAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.SeahorseSaTokenStpInterface;
import com.miracle.ai.seahorse.agent.adapters.web.SpringCurrentUserAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorProperties;
import com.miracle.ai.seahorse.agent.adapters.vector.noop.NoopVectorStoreAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.pgvector.PgVectorAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.pgvector.PgVectorProperties;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedSemaphorePort;
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
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.TokenCounterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalContextFormatPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.schedule.SchedulerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.PasswordHasherPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.TokenServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import io.micrometer.core.instrument.MeterRegistry;
import io.milvus.v2.client.MilvusClientV2;
import okhttp3.OkHttpClient;
import org.apache.pulsar.client.api.PulsarClient;
import org.redisson.api.RedissonClient;
import cn.dev33.satoken.stp.StpInterface;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import software.amazon.awssdk.services.s3.S3Client;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.List;

/**
 * Seahorse 原生 L3 adapter 自动装配。
 *
 * <p>该配置把 Seahorse 原生 adapter 注册为端口 Bean，并通过 {@code ConditionalOnMissingBean}
 * 保持可插拔替换能力。
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
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
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.cache", name = "type", havingValue = "redis", matchIfMissing = true)
    @ConditionalOnMissingBean(RedisCacheAdapter.class)
    public RedisCacheAdapter seahorseRedisCacheAdapter(RedissonClient redissonClient) {
        return new RedisCacheAdapter(redissonClient);
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.cache", name = "type", havingValue = "redis", matchIfMissing = true)
    @ConditionalOnMissingBean(DistributedSemaphorePort.class)
    public RedisSemaphoreAdapter seahorseRedisSemaphoreAdapter(RedissonClient redissonClient) {
        return new RedisSemaphoreAdapter(redissonClient);
    }

    @Bean
    @ConditionalOnBean(RedissonClient.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.stream-task", name = "type", havingValue = "redis")
    @ConditionalOnMissingBean(StreamTaskPort.class)
    public RedisStreamTaskPort seahorseRedisStreamTaskPort(RedissonClient redissonClient) {
        return new RedisStreamTaskPort(redissonClient);
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.cache", name = "type", havingValue = "local")
    @ConditionalOnMissingBean(KeyValueCachePort.class)
    public LocalCacheAdapter seahorseLocalCacheAdapter() {
        return new LocalCacheAdapter();
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.cache", name = "type", havingValue = "local")
    @ConditionalOnMissingBean(DistributedSemaphorePort.class)
    public LocalSemaphoreAdapter seahorseLocalSemaphoreAdapter() {
        return new LocalSemaphoreAdapter();
    }

    @Bean
    @ConditionalOnBean(S3Client.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.storage", name = "type", havingValue = "s3", matchIfMissing = true)
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    public S3ObjectStorageAdapter seahorseS3ObjectStorageAdapter(S3Client s3Client) {
        return new S3ObjectStorageAdapter(s3Client);
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.storage", name = "type", havingValue = "local")
    @ConditionalOnMissingBean(ObjectStoragePort.class)
    public LocalObjectStorageAdapter seahorseLocalObjectStorageAdapter(
            @Value("${seahorse-agent.adapters.storage.local.root:${java.io.tmpdir}/seahorse-agent-storage}")
            Path rootDirectory) {
        return new LocalObjectStorageAdapter(rootDirectory);
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
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KeywordSearchPort.class)
    public JdbcKeywordSearchAdapter seahorseJdbcKeywordSearchAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcKeywordSearchAdapter(dataSource, objectMapper);
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
    @ConditionalOnMissingBean(MetadataExtractionResultRepositoryPort.class)
    public MetadataExtractionResultRepositoryPort seahorseMetadataExtractionResultRepositoryPort(
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
            @Value("${seahorse-agent.adapters.mq.outbox.relay.batch-size:50}") int batchSize) {
        return new SeahorseOutboxRelayJob(outboxEventRepositoryPort, messageQueuePort, objectMapper,
                lockPort.getIfAvailable(DistributedLockPort::noop), batchSize);
    }

    @Bean
    @ConditionalOnBean(MeterRegistry.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.observation", name = "type", havingValue = "micrometer")
    @ConditionalOnMissingBean(ObservationPort.class)
    public MicrometerObservationAdapter seahorseMicrometerObservationAdapter(MeterRegistry meterRegistry) {
        return new MicrometerObservationAdapter(meterRegistry);
    }

    @Bean
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.observation", name = "type", havingValue = "noop")
    @ConditionalOnMissingBean(ObservationPort.class)
    public NoopObservationAdapter seahorseNoopObservationAdapter() {
        return new NoopObservationAdapter();
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
            String metricType) {
        return new MilvusVectorAdapter(milvusClient, new MilvusVectorProperties(collectionName, dimension, metricType));
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
    @ConditionalOnBean(OkHttpClient.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.ai", name = "type", havingValue = "openai-compatible")
    @ConditionalOnMissingBean(OpenAiCompatibleModelAdapter.class)
    public OpenAiCompatibleModelAdapter seahorseOpenAiCompatibleModelAdapter(
            OkHttpClient httpClient,
            ObjectMapper objectMapper,
            @Value("${seahorse-agent.adapters.ai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${seahorse-agent.adapters.ai.api-key:}") String apiKey,
            @Value("${seahorse-agent.adapters.ai.chat-model:}") String chatModel,
            @Value("${seahorse-agent.adapters.ai.embedding-model:}") String embeddingModel,
            @Value("${seahorse-agent.adapters.ai.rerank-model:}") String rerankModel) {
        OpenAiCompatibleModelProperties properties = new OpenAiCompatibleModelProperties(
                baseUrl, apiKey, chatModel, embeddingModel, rerankModel, List.of());
        return new OpenAiCompatibleModelAdapter(httpClient, objectMapper, properties);
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
}

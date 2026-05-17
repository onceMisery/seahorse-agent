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
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordProperties;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.SeahorseOutboxRelayJob;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentTreeRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshStateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
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
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

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
        SeahorseAgentAiAdapterAutoConfiguration.class,
        SeahorseAgentAuthAdapterAutoConfiguration.class,
        SeahorseAgentCacheAdapterAutoConfiguration.class,
        SeahorseAgentKeywordAdapterAutoConfiguration.class,
        SeahorseAgentLocalAdapterAutoConfiguration.class,
        SeahorseAgentMqAdapterAutoConfiguration.class,
        SeahorseAgentObservationAdapterAutoConfiguration.class,
        SeahorseAgentStorageAdapterAutoConfiguration.class,
        SeahorseAgentVectorAdapterAutoConfiguration.class
})
public class SeahorseAgentNativeAdapterAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeBaseQueryPort.class)
    public JdbcKnowledgeBaseQueryAdapter seahorseJdbcKnowledgeBaseQueryAdapter(DataSource dataSource) {
        return new JdbcKnowledgeBaseQueryAdapter(dataSource);
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

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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentExtensionStatusAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcIngestionTaskRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcIntentTreeRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcLongTermMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryConflictLogRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryQualitySnapshotRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMessageFeedbackRepositoryAdapter;
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
import com.miracle.ai.seahorse.agent.adapters.spring.mq.SeahorseOutboxRelayJob;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedLockPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.IngestionTaskRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.ingestion.PipelineDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentTreeRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
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
        SeahorseAgentKnowledgeRepositoryAutoConfiguration.class,
        SeahorseAgentLocalAdapterAutoConfiguration.class,
        SeahorseAgentMetadataAdapterAutoConfiguration.class,
        SeahorseAgentMqAdapterAutoConfiguration.class,
        SeahorseAgentObservationAdapterAutoConfiguration.class,
        SeahorseAgentStorageAdapterAutoConfiguration.class,
        SeahorseAgentVectorAdapterAutoConfiguration.class
})
public class SeahorseAgentNativeAdapterAutoConfiguration {

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

}

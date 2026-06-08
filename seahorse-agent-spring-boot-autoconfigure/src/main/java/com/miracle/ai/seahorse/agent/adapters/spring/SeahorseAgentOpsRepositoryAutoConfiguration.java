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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcAgentExtensionStatusAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcConversationAttachmentRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcConversationRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcDashboardRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcIntentTreeRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMessageFeedbackRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcOutboxEventRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcQueryTermExpansionAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcQueryTermMappingRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcRagTraceRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSampleQuestionRepositoryAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationAttachmentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentTreeRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermExpansionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.plugin.AgentExtensionStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.sample.SampleQuestionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.trace.RagTraceRepositoryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Optional;

/**
 * 运营与管理类 JDBC 仓储适配器自动配置。
 *
 * <p>会话、反馈、看板、扩展状态、意图树和术语映射属于运营管理侧数据域，独立配置可以避免主 native 配置继续聚合杂项仓储。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentOpsRepositoryAutoConfiguration {

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
    @ConditionalOnMissingBean(ConversationAttachmentRepositoryPort.class)
    public JdbcConversationAttachmentRepositoryAdapter seahorseJdbcConversationAttachmentRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcConversationAttachmentRepositoryAdapter(dataSource);
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
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(AgentExtensionStatusPort.class)
    public JdbcAgentExtensionStatusAdapter seahorseJdbcAgentExtensionStatusAdapter(
            DataSource dataSource, ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new JdbcAgentExtensionStatusAdapter(dataSource, objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(IntentTreeRepositoryPort.class)
    public JdbcIntentTreeRepositoryAdapter seahorseJdbcIntentTreeRepositoryAdapter(
            DataSource dataSource, ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new JdbcIntentTreeRepositoryAdapter(dataSource, objectMapperProvider.getIfAvailable(ObjectMapper::new));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(QueryTermMappingRepositoryPort.class)
    public JdbcQueryTermMappingRepositoryAdapter seahorseJdbcQueryTermMappingRepositoryAdapter(DataSource dataSource) {
        return new JdbcQueryTermMappingRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnProperty(prefix = "seahorse-agent.query-term-expansion", name = "enabled", havingValue = "true", matchIfMissing = true)
    @ConditionalOnMissingBean(QueryTermExpansionPort.class)
    public JdbcQueryTermExpansionAdapter seahorseJdbcQueryTermExpansionAdapter(
            DataSource dataSource,
            ObjectProvider<KeyValueCachePort> cachePort,
            ObjectProvider<ObjectMapper> objectMapperProvider,
            Environment environment) {
        return new JdbcQueryTermExpansionAdapter(dataSource,
                cachePort.getIfAvailable(SeahorseAgentOpsRepositoryAutoConfiguration::noopCachePort),
                objectMapperProvider.getIfAvailable(ObjectMapper::new),
                queryTermExpansionOptions(environment));
    }

    private static JdbcQueryTermExpansionAdapter.Options queryTermExpansionOptions(Environment environment) {
        return new JdbcQueryTermExpansionAdapter.Options(
                environment.getProperty("seahorse-agent.query-term-expansion.regex-enabled", Boolean.class, false),
                environment.getProperty("seahorse-agent.query-term-expansion.max-rules", Integer.class, 500),
                environment.getProperty("seahorse-agent.query-term-expansion.max-expanded-terms", Integer.class, 20),
                environment.getProperty("seahorse-agent.query-term-expansion.max-source-term-length", Integer.class, 128),
                environment.getProperty("seahorse-agent.query-term-expansion.cache-ttl", Duration.class,
                        Duration.ofMinutes(5)));
    }

    private static KeyValueCachePort noopCachePort() {
        return new KeyValueCachePort() {
            @Override
            public Optional<String> get(String key) {
                return Optional.empty();
            }

            @Override
            public void set(String key, String value, Duration ttl) {
            }

            @Override
            public boolean delete(String key) {
                return false;
            }
        };
    }
}

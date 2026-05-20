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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcChatSchemaUpgrade;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcConversationMemoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcCorrectionLedgerRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcLongTermMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryConflictLogRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryOperationLogRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemoryQualitySnapshotRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcProfileMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcSemanticMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcShortTermMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcWorkingMemoryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 记忆 JDBC 仓储适配器自动配置。
 *
 * <p>四层记忆和记忆质量治理共享同一数据域，集中装配可以让主链路配置只依赖记忆端口而不承载 JDBC 细节。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentMemoryRepositoryAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(JdbcChatSchemaUpgrade.class)
    public JdbcChatSchemaUpgrade seahorseJdbcChatSchemaUpgrade(DataSource dataSource) {
        JdbcChatSchemaUpgrade upgrade = new JdbcChatSchemaUpgrade(dataSource);
        upgrade.upgrade();
        return upgrade;
    }

    @Bean
    @ConditionalOnBean({DataSource.class, JdbcChatSchemaUpgrade.class})
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ConversationMemoryPort.class)
    public JdbcConversationMemoryAdapter seahorseJdbcConversationMemoryAdapter(
            DataSource dataSource,
            @Value("${seahorse-agent.plugins.memory.history-keep-turns:10}") int historyKeepTurns) {
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
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(ShortTermMemoryPort.class)
    public JdbcShortTermMemoryRepositoryAdapter seahorseJdbcShortTermMemoryRepositoryAdapter(
            DataSource dataSource, ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new JdbcShortTermMemoryRepositoryAdapter(dataSource, objectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(LongTermMemoryPort.class)
    public JdbcLongTermMemoryRepositoryAdapter seahorseJdbcLongTermMemoryRepositoryAdapter(
            DataSource dataSource, ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new JdbcLongTermMemoryRepositoryAdapter(dataSource, objectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(SemanticMemoryPort.class)
    public JdbcSemanticMemoryRepositoryAdapter seahorseJdbcSemanticMemoryRepositoryAdapter(
            DataSource dataSource, ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new JdbcSemanticMemoryRepositoryAdapter(dataSource, objectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc",
            matchIfMissing = true)
    @ConditionalOnMissingBean(ProfileMemoryPort.class)
    public JdbcProfileMemoryRepositoryAdapter seahorseJdbcProfileMemoryRepositoryAdapter(
            DataSource dataSource, ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new JdbcProfileMemoryRepositoryAdapter(dataSource, objectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc",
            matchIfMissing = true)
    @ConditionalOnMissingBean(CorrectionLedgerPort.class)
    public JdbcCorrectionLedgerRepositoryAdapter seahorseJdbcCorrectionLedgerRepositoryAdapter(
            DataSource dataSource, ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new JdbcCorrectionLedgerRepositoryAdapter(dataSource, objectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc",
            matchIfMissing = true)
    @ConditionalOnMissingBean(MemoryOperationLogPort.class)
    public JdbcMemoryOperationLogRepositoryAdapter seahorseJdbcMemoryOperationLogRepositoryAdapter(
            DataSource dataSource, ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new JdbcMemoryOperationLogRepositoryAdapter(dataSource, objectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(MemoryQualitySnapshotRepositoryPort.class)
    public JdbcMemoryQualitySnapshotRepositoryAdapter seahorseJdbcMemoryQualitySnapshotRepositoryAdapter(
            DataSource dataSource, ObjectProvider<ObjectMapper> objectMapperProvider) {
        return new JdbcMemoryQualitySnapshotRepositoryAdapter(dataSource, objectMapper(objectMapperProvider));
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(MemoryConflictLogRepositoryPort.class)
    public JdbcMemoryConflictLogRepositoryAdapter seahorseJdbcMemoryConflictLogRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcMemoryConflictLogRepositoryAdapter(dataSource);
    }

    private ObjectMapper objectMapper(ObjectProvider<ObjectMapper> objectMapperProvider) {
        return objectMapperProvider.getIfAvailable(ObjectMapper::new);
    }
}

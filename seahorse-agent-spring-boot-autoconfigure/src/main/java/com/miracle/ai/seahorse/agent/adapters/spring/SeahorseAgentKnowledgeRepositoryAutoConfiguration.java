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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcDocumentRefreshScheduleAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeBaseQueryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeBaseRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeChunkRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeDocumentRepositoryAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshSchedulePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.DocumentRefreshStateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 知识库 JDBC 仓储适配器自动配置。
 *
 * <p>知识库查询、文档、切片和刷新状态属于同一数据域，集中在这里装配可以降低主 native 配置的职责重量。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKnowledgeRepositoryAutoConfiguration {

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeBaseQueryPort.class)
    public JdbcKnowledgeBaseQueryAdapter seahorseJdbcKnowledgeBaseQueryAdapter(DataSource dataSource) {
        return new JdbcKnowledgeBaseQueryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeBaseRepositoryPort.class)
    public JdbcKnowledgeBaseRepositoryAdapter seahorseJdbcKnowledgeBaseRepositoryAdapter(DataSource dataSource) {
        return new JdbcKnowledgeBaseRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeChunkRepositoryPort.class)
    public JdbcKnowledgeChunkRepositoryAdapter seahorseJdbcKnowledgeChunkRepositoryAdapter(DataSource dataSource) {
        return new JdbcKnowledgeChunkRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(KnowledgeDocumentRepositoryPort.class)
    public JdbcKnowledgeDocumentRepositoryAdapter seahorseJdbcKnowledgeDocumentRepositoryAdapter(DataSource dataSource) {
        return new JdbcKnowledgeDocumentRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnProperty(prefix = "seahorse.agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
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
}

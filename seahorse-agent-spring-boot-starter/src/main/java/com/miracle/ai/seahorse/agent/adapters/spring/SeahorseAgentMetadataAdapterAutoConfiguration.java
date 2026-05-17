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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataGovernanceRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataSchemaIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordProperties;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchMetadataSchemaIndexAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataBackfillJobRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataDictionaryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataExtractionResultRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQualityReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexStatusPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaIndexSyncPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 元数据治理适配器自动配置。
 *
 * <p>将治理仓储、schema index 同步以及治理仓储暴露的细分端口放在同一配置类，
 * 避免拆分后出现 `@ConditionalOnBean` 顺序不一致的问题。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentMetadataAdapterAutoConfiguration {

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
    @ConditionalOnMissingBean(MetadataSchemaIndexStatusPort.class)
    public MetadataSchemaIndexStatusPort seahorseMetadataSchemaIndexStatusPort(
            JdbcMetadataGovernanceRepositoryAdapter adapter) {
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

    private static List<String> csv(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
    }

    private static Duration duration(String value) {
        if (value == null || value.isBlank()) {
            return Duration.ofSeconds(10);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        try {
            return Duration.parse(normalized);
        } catch (Exception ignored) {
            if (normalized.endsWith("ms")) {
                return Duration.ofMillis(Long.parseLong(normalized.substring(0, normalized.length() - 2)));
            }
            if (normalized.endsWith("s")) {
                return Duration.ofSeconds(Long.parseLong(normalized.substring(0, normalized.length() - 1)));
            }
            return Duration.ofSeconds(Long.parseLong(normalized));
        }
    }
}

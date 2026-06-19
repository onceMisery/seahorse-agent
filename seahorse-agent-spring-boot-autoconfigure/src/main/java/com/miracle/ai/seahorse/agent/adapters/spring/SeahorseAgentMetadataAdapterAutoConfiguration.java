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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataBackfillJobRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataCanonicalWriteRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataDictionaryRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataExtractionResultRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataGovernanceRepositoryDelegate;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataGovernanceRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataPortAdapters;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataQualityReportRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataQuarantineRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataReviewRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataSchemaIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataSchemaRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMetadataSchemaUsageReportRepositoryAdapter;
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
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
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
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true",
        matchIfMissing = true)
public class SeahorseAgentMetadataAdapterAutoConfiguration {

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    public JdbcMetadataGovernanceRepositoryDelegate seahorseJdbcMetadataGovernanceRepositoryDelegate(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcMetadataGovernanceRepositoryDelegate(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.metadata-governance-compatibility",
            name = "facade-bean-enabled", havingValue = "true")
    @ConditionalOnMissingBean(JdbcMetadataGovernanceRepositoryAdapter.class)
    public JdbcMetadataGovernanceRepositoryAdapter seahorseJdbcMetadataGovernanceRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcMetadataGovernanceRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean({
            JdbcMetadataSchemaRepositoryAdapter.class,
            MetadataSchemaRegistryPort.class,
            MetadataSchemaManagementRepositoryPort.class,
            MetadataSchemaIndexStatusPort.class
    })
    public JdbcMetadataSchemaRepositoryAdapter seahorseJdbcMetadataSchemaRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcMetadataSchemaRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataSchemaRegistryPort.class)
    public MetadataSchemaRegistryPort seahorseMetadataSchemaRegistryPort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.schemaRegistry(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataSchemaIndexStatusPort.class)
    public MetadataSchemaIndexStatusPort seahorseMetadataSchemaIndexStatusPort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.schemaIndexStatus(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.metadata-schema-index", name = "type",
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
    @ConditionalOnBean(DataSource.class)
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean({
            JdbcMetadataDictionaryRepositoryAdapter.class,
            MetadataDictionaryPort.class,
            MetadataDictionaryManagementRepositoryPort.class
    })
    public JdbcMetadataDictionaryRepositoryAdapter seahorseJdbcMetadataDictionaryRepositoryAdapter(
            DataSource dataSource) {
        return new JdbcMetadataDictionaryRepositoryAdapter(dataSource);
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean({
            JdbcMetadataExtractionResultRepositoryAdapter.class,
            MetadataExtractionResultRepositoryPort.class,
            MetadataExtractionResultManagementRepositoryPort.class
    })
    public JdbcMetadataExtractionResultRepositoryAdapter seahorseJdbcMetadataExtractionResultRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcMetadataExtractionResultRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataExtractionResultRepositoryPort.class)
    public MetadataExtractionResultRepositoryPort seahorseMetadataExtractionResultRepositoryPort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.extractionResult(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataExtractionResultManagementRepositoryPort.class)
    public MetadataExtractionResultManagementRepositoryPort seahorseMetadataExtractionResultManagementRepositoryPort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.extractionResultManagement(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean({
            JdbcMetadataReviewRepositoryAdapter.class,
            MetadataReviewQueuePort.class,
            MetadataReviewManagementRepositoryPort.class
    })
    public JdbcMetadataReviewRepositoryAdapter seahorseJdbcMetadataReviewRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcMetadataReviewRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataReviewQueuePort.class)
    public MetadataReviewQueuePort seahorseMetadataReviewQueuePort(JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.reviewQueue(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean({
            JdbcMetadataQuarantineRepositoryAdapter.class,
            MetadataQuarantinePort.class,
            MetadataQuarantineManagementRepositoryPort.class
    })
    public JdbcMetadataQuarantineRepositoryAdapter seahorseJdbcMetadataQuarantineRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcMetadataQuarantineRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataQuarantinePort.class)
    public MetadataQuarantinePort seahorseMetadataQuarantinePort(JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.quarantine(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean({
            JdbcMetadataCanonicalWriteRepositoryAdapter.class,
            MetadataCanonicalWritePort.class
    })
    public JdbcMetadataCanonicalWriteRepositoryAdapter seahorseJdbcMetadataCanonicalWriteRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcMetadataCanonicalWriteRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataCanonicalWritePort.class)
    public MetadataCanonicalWritePort seahorseMetadataCanonicalWritePort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.canonicalWrite(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean({
            JdbcMetadataBackfillJobRepositoryAdapter.class,
            MetadataBackfillJobRepositoryPort.class
    })
    public JdbcMetadataBackfillJobRepositoryAdapter seahorseJdbcMetadataBackfillJobRepositoryAdapter(
            DataSource dataSource, ObjectMapper objectMapper) {
        return new JdbcMetadataBackfillJobRepositoryAdapter(dataSource, objectMapper);
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataBackfillJobRepositoryPort.class)
    public MetadataBackfillJobRepositoryPort seahorseMetadataBackfillJobRepositoryPort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.backfillJob(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean({DataSource.class, ObjectMapper.class, MetadataSchemaRegistryPort.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean({
            JdbcMetadataQualityReportRepositoryAdapter.class,
            MetadataQualityReportRepositoryPort.class
    })
    public JdbcMetadataQualityReportRepositoryAdapter seahorseJdbcMetadataQualityReportRepositoryAdapter(
            DataSource dataSource,
            ObjectMapper objectMapper,
            MetadataSchemaRegistryPort schemaRegistryPort) {
        return new JdbcMetadataQualityReportRepositoryAdapter(dataSource, objectMapper, schemaRegistryPort);
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataQualityReportRepositoryPort.class)
    public MetadataQualityReportRepositoryPort seahorseMetadataQualityReportRepositoryPort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.qualityReport(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataReviewManagementRepositoryPort.class)
    public MetadataReviewManagementRepositoryPort seahorseMetadataReviewManagementRepositoryPort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.reviewManagement(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataQuarantineManagementRepositoryPort.class)
    public MetadataQuarantineManagementRepositoryPort seahorseMetadataQuarantineManagementRepositoryPort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.quarantineManagement(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataSchemaManagementRepositoryPort.class)
    public MetadataSchemaManagementRepositoryPort seahorseMetadataSchemaManagementRepositoryPort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.schemaManagement(delegate.adapter());
    }

    @Bean
    @ConditionalOnBean({DataSource.class, MetadataSchemaManagementRepositoryPort.class})
    @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.repository", name = "type",
            havingValue = "jdbc", matchIfMissing = true)
    @ConditionalOnMissingBean({
            JdbcMetadataSchemaUsageReportRepositoryAdapter.class,
            MetadataSchemaUsageReportRepositoryPort.class
    })
    public JdbcMetadataSchemaUsageReportRepositoryAdapter seahorseJdbcMetadataSchemaUsageReportRepositoryAdapter(
            DataSource dataSource,
            MetadataSchemaManagementRepositoryPort schemaManagementRepositoryPort) {
        return new JdbcMetadataSchemaUsageReportRepositoryAdapter(dataSource, schemaManagementRepositoryPort);
    }

    @Bean
    @ConditionalOnBean(JdbcMetadataGovernanceRepositoryDelegate.class)
    @ConditionalOnMissingBean(MetadataSchemaUsageReportRepositoryPort.class)
    public MetadataSchemaUsageReportRepositoryPort seahorseMetadataSchemaUsageReportRepositoryPort(
            JdbcMetadataGovernanceRepositoryDelegate delegate) {
        return JdbcMetadataPortAdapters.schemaUsageReport(delegate.adapter());
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

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchMetadataSchemaIndexAdapter",
            "okhttp3.OkHttpClient"
    })
    static class ElasticsearchMetadataSchemaIndexAutoConfiguration {

        @Bean
        @ConditionalOnBean({OkHttpClient.class, ObjectMapper.class})
        @ConditionalOnSeahorseAgentProperty(prefix = "seahorse-agent.adapters.metadata-schema-index", name = "type",
                havingValue = "elasticsearch")
        @ConditionalOnMissingBean(ElasticsearchMetadataSchemaIndexAdapter.class)
        public ElasticsearchMetadataSchemaIndexAdapter seahorseElasticsearchMetadataSchemaIndexAdapter(
                OkHttpClient httpClient,
                ObjectMapper objectMapper,
                ObjectProvider<ObservationPort> observationPort,
                ObjectProvider<MetadataSchemaIndexStatusPort> indexStatusPort,
                @Value("${seahorse.agent.adapters.metadata-schema-index.elasticsearch.base-url:http://localhost:9200}")
                String baseUrl,
                @Value("${seahorse.agent.adapters.metadata-schema-index.elasticsearch.index-name:seahorse_keyword_chunk}")
                String indexName,
                @Value("${seahorse.agent.adapters.metadata-schema-index.elasticsearch.search-fields:content^3}")
                String searchFields,
                @Value("${seahorse.agent.adapters.metadata-schema-index.elasticsearch.api-key:}") String apiKey,
                @Value("${seahorse.agent.adapters.metadata-schema-index.elasticsearch.username:}") String username,
                @Value("${seahorse.agent.adapters.metadata-schema-index.elasticsearch.password:}") String password,
                @Value("${seahorse.agent.adapters.metadata-schema-index.elasticsearch.timeout:10s}") String timeout) {
            return new ElasticsearchMetadataSchemaIndexAdapter(httpClient, objectMapper,
                    new ElasticsearchKeywordProperties(baseUrl, indexName, csv(searchFields),
                            apiKey, username, password, duration(timeout)),
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
    }
}

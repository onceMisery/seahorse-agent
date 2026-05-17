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
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordProperties;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordProperties;
import com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.keyword.KeywordIndexMessageSubscriber;
import com.miracle.ai.seahorse.agent.adapters.spring.keyword.KeywordIndexOutboxAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import okhttp3.OkHttpClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/**
 * 关键词检索与索引适配器自动配置。
 *
 * <p>关键词 search/index 的 Elasticsearch、Lucene、JDBC 实现以及 outbox 订阅在这里聚合；
 * 元数据 schema index 仍留在主配置中，避免和元数据治理仓储的条件装配顺序耦合。
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKeywordAdapterAutoConfiguration {

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
    @ConditionalOnMissingBean(value = KeywordIndexPort.class, ignored = KeywordIndexOutboxAdapter.class)
    public JdbcKeywordIndexAdapter seahorseJdbcKeywordIndexAdapter(DataSource dataSource) {
        return new JdbcKeywordIndexAdapter(dataSource);
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
            "com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordSearchAdapter",
            "okhttp3.OkHttpClient"
    })
    static class ElasticsearchKeywordAutoConfiguration {

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
                @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.analyzer:}") String analyzer,
                @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.minimum-should-match:}")
                String minimumShouldMatch,
                @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.api-key:}") String apiKey,
                @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.username:}") String username,
                @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.password:}") String password,
                @Value("${seahorse-agent.adapters.keyword-search.elasticsearch.timeout:10s}") String timeout) {
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
                @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.api-key:}") String apiKey,
                @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.username:}") String username,
                @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.password:}") String password,
                @Value("${seahorse-agent.adapters.keyword-index.elasticsearch.timeout:10s}") String timeout) {
            return new ElasticsearchKeywordIndexAdapter(httpClient, objectMapper,
                    new ElasticsearchKeywordProperties(baseUrl, indexName, csv(searchFields),
                            apiKey, username, password, duration(timeout)));
        }

        @Bean
        @ConditionalOnBean(ElasticsearchKeywordIndexAdapter.class)
        @ConditionalOnMissingBean(value = KeywordIndexPort.class, ignored = KeywordIndexOutboxAdapter.class)
        public KeywordIndexPort seahorseElasticsearchKeywordIndexPort(ElasticsearchKeywordIndexAdapter adapter) {
            return adapter;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordSearchAdapter")
    static class LuceneKeywordAutoConfiguration {

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
    }
}

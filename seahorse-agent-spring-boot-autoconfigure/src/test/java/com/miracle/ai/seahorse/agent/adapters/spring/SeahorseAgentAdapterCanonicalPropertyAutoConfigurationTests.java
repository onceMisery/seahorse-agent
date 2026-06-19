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
import com.miracle.ai.seahorse.agent.adapters.cache.local.LocalCacheAdapter;
import com.miracle.ai.seahorse.agent.adapters.cache.redis.RedisCacheAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordIndexAdapter;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordProperties;
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.AdvancedFeatureGate;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessCheck;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSendReceipt;
import com.miracle.ai.seahorse.agent.ports.outbound.readiness.ReadinessProbePort;
import java.util.List;
import java.util.Map;
import okhttp3.OkHttpClient;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SeahorseAgentAdapterCanonicalPropertyAutoConfigurationTests {

    @Test
    void keywordAdaptersPreferCanonicalElasticsearchTypeOverLegacyJdbcDefault() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentKeywordAdapterAutoConfiguration.class))
                .withBean(DriverManagerDataSource.class, () -> new DriverManagerDataSource(
                        "jdbc:h2:mem:canonical-keyword;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""))
                .withBean(OkHttpClient.class, OkHttpClient::new)
                .withBean(ObjectMapper.class, ObjectMapper::new);

        contextRunner.withPropertyValues(
                        "seahorse-agent.adapters.keyword-search.type=elasticsearch",
                        "seahorse-agent.adapters.keyword-index.type=elasticsearch",
                        "seahorse-agent.adapters.keyword-search.elasticsearch.base-url=http://elasticsearch:9200",
                        "seahorse-agent.adapters.keyword-search.elasticsearch.index-name=test_chunks",
                        "seahorse-agent.adapters.keyword-index.elasticsearch.base-url=http://elasticsearch:9200",
                        "seahorse-agent.adapters.keyword-index.elasticsearch.index-name=test_chunks")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(ElasticsearchKeywordSearchAdapter.class);
                    assertThat(context).hasSingleBean(ElasticsearchKeywordIndexAdapter.class);
                    assertThat(context).doesNotHaveBean(JdbcKeywordSearchAdapter.class);
                    assertThat(context).doesNotHaveBean(JdbcKeywordIndexAdapter.class);
                    assertThat(context.getBean(KeywordSearchPort.class))
                            .isInstanceOf(ElasticsearchKeywordSearchAdapter.class);
                    assertThat(context.getBean(KeywordIndexPort.class))
                            .isInstanceOf(ElasticsearchKeywordIndexAdapter.class);
                    assertThat(propertiesOf(context.getBean(ElasticsearchKeywordSearchAdapter.class)).baseUrl())
                            .isEqualTo("http://elasticsearch:9200");
                    assertThat(propertiesOf(context.getBean(ElasticsearchKeywordSearchAdapter.class)).indexName())
                            .isEqualTo("test_chunks");
                    assertThat(propertiesOf(context.getBean(ElasticsearchKeywordIndexAdapter.class)).baseUrl())
                            .isEqualTo("http://elasticsearch:9200");
                    assertThat(propertiesOf(context.getBean(ElasticsearchKeywordIndexAdapter.class)).indexName())
                            .isEqualTo("test_chunks");
                });
    }

    @Test
    void keywordAdaptersResolveDockerEnvironmentVariables() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentKeywordAdapterAutoConfiguration.class))
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("docker-keyword-env", Map.of(
                                "SEAHORSE_AGENT_ADAPTERS_KEYWORD_SEARCH_TYPE", "elasticsearch",
                                "SEAHORSE_AGENT_ADAPTERS_KEYWORD_INDEX_TYPE", "elasticsearch",
                                "SEAHORSE_AGENT_ADAPTERS_KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL",
                                "http://elasticsearch:9200",
                                "SEAHORSE_AGENT_ADAPTERS_KEYWORD_SEARCH_ELASTICSEARCH_INDEX_NAME",
                                "docker_chunks",
                                "SEAHORSE_AGENT_ADAPTERS_KEYWORD_INDEX_ELASTICSEARCH_BASE_URL",
                                "http://elasticsearch:9200",
                                "SEAHORSE_AGENT_ADAPTERS_KEYWORD_INDEX_ELASTICSEARCH_INDEX_NAME",
                                "docker_chunks"))))
                .withBean(DriverManagerDataSource.class, () -> new DriverManagerDataSource(
                        "jdbc:h2:mem:docker-keyword;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""))
                .withBean(OkHttpClient.class, OkHttpClient::new)
                .withBean(ObjectMapper.class, ObjectMapper::new);

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ElasticsearchKeywordSearchAdapter.class);
            assertThat(context).hasSingleBean(ElasticsearchKeywordIndexAdapter.class);
            assertThat(context).doesNotHaveBean(JdbcKeywordSearchAdapter.class);
            assertThat(context).doesNotHaveBean(JdbcKeywordIndexAdapter.class);
            assertThat(propertiesOf(context.getBean(ElasticsearchKeywordSearchAdapter.class)).baseUrl())
                    .isEqualTo("http://elasticsearch:9200");
            assertThat(propertiesOf(context.getBean(ElasticsearchKeywordSearchAdapter.class)).indexName())
                    .isEqualTo("docker_chunks");
            assertThat(propertiesOf(context.getBean(ElasticsearchKeywordIndexAdapter.class)).baseUrl())
                    .isEqualTo("http://elasticsearch:9200");
            assertThat(propertiesOf(context.getBean(ElasticsearchKeywordIndexAdapter.class)).indexName())
                    .isEqualTo("docker_chunks");
        });
    }

    @Test
    void keywordAdaptersUseOkHttpClientProvidedByAiAutoConfiguration() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SeahorseAgentAiAdapterAutoConfiguration.class,
                        SeahorseAgentKeywordAdapterAutoConfiguration.class))
                .withBean(DriverManagerDataSource.class, () -> new DriverManagerDataSource(
                        "jdbc:h2:mem:keyword-shared-http;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""))
                .withBean(ObjectMapper.class, ObjectMapper::new);

        contextRunner.withPropertyValues(
                        "seahorse-agent.adapters.ai.type=openai-compatible",
                        "seahorse-agent.adapters.keyword-search.type=elasticsearch",
                        "seahorse-agent.adapters.keyword-index.type=elasticsearch")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(OkHttpClient.class);
                    assertThat(context.getBean(KeywordSearchPort.class))
                            .isInstanceOf(ElasticsearchKeywordSearchAdapter.class);
                    assertThat(context.getBean(KeywordIndexPort.class))
                            .isInstanceOf(ElasticsearchKeywordIndexAdapter.class);
                });
    }

    @Test
    void cacheAdaptersPreferCanonicalRedisTypeOverLegacyLocalType() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentCacheAdapterAutoConfiguration.class))
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class));

        contextRunner.withPropertyValues(
                        "seahorse.agent.adapters.cache.type=local",
                        "seahorse-agent.adapters.cache.type=redis")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(RedisCacheAdapter.class);
                    assertThat(context).doesNotHaveBean(LocalCacheAdapter.class);
                    assertThat(context.getBean(KeyValueCachePort.class))
                            .isInstanceOf(RedisCacheAdapter.class);
                });
    }

    @Test
    void cacheAdaptersCreateRedisPortForSpringDataRedisRuntime() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentCacheAdapterAutoConfiguration.class))
                .withBean("redissonClient", RedissonClient.class, () -> mock(RedissonClient.class));

        contextRunner.withPropertyValues(
                        "seahorse-agent.adapters.cache.type=redis",
                        "spring.data.redis.host=redis",
                        "spring.data.redis.port=6379")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasBean("redissonClient");
                    assertThat(context.getBean(KeyValueCachePort.class)).isInstanceOf(RedisCacheAdapter.class);
                });
    }

    @Test
    void readinessReportsCanonicalKeywordSearchType() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentReadinessAutoConfiguration.class))
                .withBean(KeywordSearchPort.class, () -> request -> List.of())
                .withBean(AdvancedFeatureGate.class, AdvancedFeatureGate::allEnabledForTests);

        contextRunner.withPropertyValues("seahorse-agent.adapters.keyword-search.type=elasticsearch")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ReadinessProbePort probe = context.getBean(ReadinessProbePort.class);
                    assertThat(probe.adapterTypes()).containsEntry("keyword-search", "elasticsearch");

                    ReadinessCheck keywordSearch = context.getBean(ReadinessInboundPort.class)
                            .runCheck("search.keyword");
                    assertThat(keywordSearch.status()).isEqualTo(ReadinessCheck.Status.PASSED);
                    assertThat(keywordSearch.message()).contains("elasticsearch");
                });
    }

    @Test
    void readinessProbesPulsarMqBySendingMessage() {
        RecordingMessageQueue messageQueue = new RecordingMessageQueue();
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentReadinessAutoConfiguration.class))
                .withBean(MessageQueuePort.class, () -> messageQueue)
                .withBean(AdvancedFeatureGate.class, AdvancedFeatureGate::allEnabledForTests);

        contextRunner.withPropertyValues(
                        "seahorse-agent.adapters.mq.type=pulsar",
                        "seahorse-agent.adapters.mq.readiness-probe.topic=persistent://seahorse-agent/ai/test-readiness")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ReadinessProbePort.ComponentStatus mqStatus = context.getBean(ReadinessProbePort.class)
                            .probeComponents()
                            .get("mq");
                    assertThat(mqStatus.available()).isTrue();
                    assertThat(mqStatus.adapterType()).isEqualTo("pulsar");
                    assertThat(mqStatus.detail())
                            .contains("persistent://seahorse-agent/ai/test-readiness");

                    ReadinessCheck mqCheck = context.getBean(ReadinessInboundPort.class)
                            .runCheck("mq");
                    assertThat(mqCheck.status()).isEqualTo(ReadinessCheck.Status.PASSED);
                    assertThat(mqCheck.message()).contains("pulsar");
                    assertThat(messageQueue.sendCalls).isEqualTo(1);
                    assertThat(messageQueue.topic)
                            .isEqualTo("persistent://seahorse-agent/ai/test-readiness");
                    assertThat(messageQueue.key).startsWith("readiness-");
                    assertThat(messageQueue.bizDesc).isEqualTo("readiness-probe");
                    assertThat(messageQueue.body).isInstanceOf(Map.class);
                    assertThat(((Map<?, ?>) messageQueue.body).get("probe")).isEqualTo("readiness");
                });
    }

    @Test
    void readinessFailsPulsarMqWhenProbeSendFails() {
        RecordingMessageQueue messageQueue = RecordingMessageQueue.failing(
                new IllegalStateException("send failed", new IllegalArgumentException("bookie unavailable")));
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentReadinessAutoConfiguration.class))
                .withBean(MessageQueuePort.class, () -> messageQueue)
                .withBean(AdvancedFeatureGate.class, AdvancedFeatureGate::allEnabledForTests);

        contextRunner.withPropertyValues(
                        "seahorse-agent.product-mode=enterprise",
                        "seahorse-agent.adapters.mq.type=pulsar")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ReadinessProbePort.ComponentStatus mqStatus = context.getBean(ReadinessProbePort.class)
                            .probeComponents()
                            .get("mq");
                    assertThat(mqStatus.available()).isFalse();
                    assertThat(mqStatus.detail())
                            .contains("Pulsar readiness probe failed: bookie unavailable");

                    ReadinessCheck mqCheck = context.getBean(ReadinessInboundPort.class)
                            .runCheck("mq");
                    assertThat(mqCheck.status()).isEqualTo(ReadinessCheck.Status.FAILED);
                    assertThat(mqCheck.message())
                            .contains("Pulsar readiness probe failed: bookie unavailable");
                    assertThat(messageQueue.sendCalls).isEqualTo(1);
                });
    }

    @Test
    void readinessFailsConfiguredPulsarMqOutsideEnterpriseWhenProbeSendFails() {
        RecordingMessageQueue messageQueue = RecordingMessageQueue.failing(
                new IllegalStateException("send failed", new IllegalArgumentException("bookie unavailable")));
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentReadinessAutoConfiguration.class))
                .withBean(MessageQueuePort.class, () -> messageQueue)
                .withBean(AdvancedFeatureGate.class, AdvancedFeatureGate::allEnabledForTests);

        contextRunner.withPropertyValues("seahorse-agent.adapters.mq.type=pulsar")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ReadinessCheck mqCheck = context.getBean(ReadinessInboundPort.class)
                            .runCheck("mq");
                    assertThat(mqCheck.status()).isEqualTo(ReadinessCheck.Status.FAILED);
                    assertThat(mqCheck.severity()).isEqualTo(ReadinessCheck.Severity.WARN);
                    assertThat(mqCheck.message())
                            .contains("Pulsar readiness probe failed: bookie unavailable");
                    assertThat(messageQueue.sendCalls).isEqualTo(1);
                });
    }

    @Test
    void readinessDoesNotSendProbeForDirectMq() {
        RecordingMessageQueue messageQueue = new RecordingMessageQueue();
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentReadinessAutoConfiguration.class))
                .withBean(MessageQueuePort.class, () -> messageQueue)
                .withBean(AdvancedFeatureGate.class, AdvancedFeatureGate::allEnabledForTests);

        contextRunner.withPropertyValues("seahorse-agent.adapters.mq.type=direct")
                .run(context -> {
                    assertThat(context).hasNotFailed();

                    ReadinessProbePort.ComponentStatus mqStatus = context.getBean(ReadinessProbePort.class)
                            .probeComponents()
                            .get("mq");
                    assertThat(mqStatus.available()).isTrue();
                    assertThat(mqStatus.adapterType()).isEqualTo("direct");
                    assertThat(messageQueue.sendCalls).isZero();
                });
    }

    @Test
    void fullComposeEnvironmentVariablesCreateExternalAdapterPorts() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        SeahorseAgentCacheAdapterAutoConfiguration.class,
                        SeahorseAgentKeywordAdapterAutoConfiguration.class))
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("full-compose-env", Map.of(
                                "SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE", "redis",
                                "SEAHORSE_AGENT_ADAPTERS_KEYWORD_SEARCH_TYPE", "elasticsearch",
                                "SEAHORSE_AGENT_ADAPTERS_KEYWORD_INDEX_TYPE", "elasticsearch",
                                "SEAHORSE_AGENT_ADAPTERS_KEYWORD_SEARCH_ELASTICSEARCH_BASE_URL",
                                "http://elasticsearch:9200",
                                "SEAHORSE_AGENT_ADAPTERS_KEYWORD_INDEX_ELASTICSEARCH_BASE_URL",
                                "http://elasticsearch:9200"))))
                .withBean(DriverManagerDataSource.class, () -> new DriverManagerDataSource(
                        "jdbc:h2:mem:full-compose-env;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""))
                .withBean(OkHttpClient.class, OkHttpClient::new)
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withBean(RedissonClient.class, () -> mock(RedissonClient.class));

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(KeyValueCachePort.class)).isInstanceOf(RedisCacheAdapter.class);
            assertThat(context.getBean(KeywordSearchPort.class))
                    .isInstanceOf(ElasticsearchKeywordSearchAdapter.class);
            assertThat(context.getBean(KeywordIndexPort.class))
                    .isInstanceOf(ElasticsearchKeywordIndexAdapter.class);
            assertThat(propertiesOf(context.getBean(ElasticsearchKeywordSearchAdapter.class)).baseUrl())
                    .isEqualTo("http://elasticsearch:9200");
        });
    }

    @Test
    void readinessTreatsBlankDimensionPropertiesAsUnset() {
        ApplicationContextRunner contextRunner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(SeahorseAgentReadinessAutoConfiguration.class))
                .withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("blank-dimension-env", Map.of(
                                "SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_DIMENSION", "",
                                "SEAHORSE_AGENT_ADAPTERS_VECTOR_DIMENSION", ""))))
                .withBean(AdvancedFeatureGate.class, AdvancedFeatureGate::allEnabledForTests);

        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            ReadinessProbePort probe = context.getBean(ReadinessProbePort.class);
            assertThat(probe.adapterTypes())
                    .containsEntry("embedding-dimension", "0")
                    .containsEntry("vector-dimension", "0");
            assertThat(probe.probeComponents().get("embedding.dimension").available()).isTrue();
        });
    }

    private static ElasticsearchKeywordProperties propertiesOf(Object adapter) {
        return (ElasticsearchKeywordProperties) ReflectionTestUtils.getField(adapter, "properties");
    }

    private static final class RecordingMessageQueue implements MessageQueuePort {
        private final RuntimeException sendFailure;
        private int sendCalls;
        private String topic;
        private String key;
        private String bizDesc;
        private Object body;

        private RecordingMessageQueue() {
            this(null);
        }

        private RecordingMessageQueue(RuntimeException sendFailure) {
            this.sendFailure = sendFailure;
        }

        private static RecordingMessageQueue failing(RuntimeException sendFailure) {
            return new RecordingMessageQueue(sendFailure);
        }

        @Override
        public MessageSendReceipt send(String topic, String key, String bizDesc, Object body) {
            this.sendCalls++;
            this.topic = topic;
            this.key = key;
            this.bizDesc = bizDesc;
            this.body = body;
            if (sendFailure != null) {
                throw sendFailure;
            }
            return new MessageSendReceipt("test-message-id", topic, key, System.currentTimeMillis());
        }

        @Override
        public void publishReliable(String topic, String key, String bizDesc, Object body) {
            throw new UnsupportedOperationException("publishReliable is not used by readiness probes");
        }
    }
}

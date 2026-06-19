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
import com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordSearchAdapter;
import com.miracle.ai.seahorse.agent.adapters.web.AdvancedFeatureGate;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessCheck;
import com.miracle.ai.seahorse.agent.ports.inbound.readiness.ReadinessInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort;
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
                        "seahorse-agent.adapters.keyword-search.elasticsearch.index-name=test_chunks",
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
}

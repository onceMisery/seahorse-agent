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
import com.miracle.ai.seahorse.agent.adapters.mq.direct.DirectMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.observation.noop.NoopObservationAdapter;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcKnowledgeBaseRepositoryAdapter;
import com.miracle.ai.seahorse.agent.adapters.spring.mq.ReliableMessageQueueAdapter;
import com.miracle.ai.seahorse.agent.adapters.storage.local.LocalObjectStorageAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.noop.NoopVectorStoreAdapter;
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentStarterCoreContextTests {

    private static final List<String> OFFICIAL_HEAVY_ADAPTER_CLASSES = List.of(
            "com.miracle.ai.seahorse.agent.adapters.ai.openai.OpenAiCompatibleModelAdapter",
            "com.miracle.ai.seahorse.agent.adapters.mcp.http.StreamableHttpMcpClient",
            "com.miracle.ai.seahorse.agent.adapters.openapi.OpenApiSpecParserAdapter",
            "com.miracle.ai.seahorse.agent.adapters.parser.tika.TikaDocumentParserAdapter",
            "com.miracle.ai.seahorse.agent.adapters.source.feishu.FeishuDocumentFetcherAdapter",
            "com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorAdapter",
            "com.miracle.ai.seahorse.agent.adapters.vector.pgvector.PgVectorAdapter",
            "com.miracle.ai.seahorse.agent.adapters.cache.redis.RedisCacheAdapter",
            "com.miracle.ai.seahorse.agent.adapters.storage.s3.S3ObjectStorageAdapter",
            "com.miracle.ai.seahorse.agent.adapters.mq.pulsar.PulsarMessageQueueAdapter",
            "com.miracle.ai.seahorse.agent.adapters.observation.micrometer.MicrometerObservationAdapter",
            "com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordSearchAdapter",
            "com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordSearchAdapter");

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    SeahorseAgentCacheAdapterAutoConfiguration.class,
                    SeahorseAgentKnowledgeRepositoryAutoConfiguration.class,
                    SeahorseAgentMqAdapterAutoConfiguration.class,
                    SeahorseAgentObservationAdapterAutoConfiguration.class,
                    SeahorseAgentStorageAdapterAutoConfiguration.class,
                    SeahorseAgentVectorAdapterAutoConfiguration.class))
            .withBean(ObjectMapper.class, ObjectMapper::new)
            .withBean(DriverManagerDataSource.class, SeahorseAgentStarterCoreContextTests::h2DataSource)
            .withPropertyValues(
                    "seahorse-agent.adapters.cache.type=local",
                    "seahorse-agent.adapters.mq.type=direct",
                    "seahorse-agent.adapters.storage.type=local",
                    "seahorse-agent.adapters.vector.type=noop",
                    "seahorse-agent.adapters.observation.type=noop");

    @Test
    void coreOnlyContextStartsWithLocalDirectNoopAndJdbcDefaults() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed();

            assertThat(context).hasSingleBean(LocalCacheAdapter.class);
            assertThat(context).hasSingleBean(KeyValueCachePort.class);
            assertThat(context).hasSingleBean(ReliableMessageQueueAdapter.class);
            assertThat(context).hasSingleBean(MessageQueuePort.class);
            assertThat(context).hasSingleBean(MessageSubscriptionPort.class);
            assertThat(context).doesNotHaveBean(DirectMessageQueueAdapter.class);
            assertThat(context).hasSingleBean(LocalObjectStorageAdapter.class);
            assertThat(context).hasSingleBean(ObjectStoragePort.class);
            assertThat(context).hasSingleBean(NoopVectorStoreAdapter.class);
            assertThat(context).hasSingleBean(VectorSearchPort.class);
            assertThat(context).hasSingleBean(VectorIndexPort.class);
            assertThat(context).hasSingleBean(VectorCollectionAdminPort.class);
            assertThat(context).hasSingleBean(NoopObservationAdapter.class);
            assertThat(context).hasSingleBean(ObservationPort.class);
            assertThat(context).hasSingleBean(JdbcKnowledgeBaseRepositoryAdapter.class);
            assertThat(context).hasSingleBean(KnowledgeBaseRepositoryPort.class);
        });
    }

    @Test
    void coreOnlyClasspathDoesNotContainOfficialHeavyAdapters() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertThat(OFFICIAL_HEAVY_ADAPTER_CLASSES)
                .allSatisfy(className -> assertThat(isClassPresent(classLoader, className))
                        .as(className)
                        .isFalse());
    }

    private static DriverManagerDataSource h2DataSource() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:starter-core-context;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    private static boolean isClassPresent(ClassLoader classLoader, String className) {
        try {
            Class.forName(className, false, classLoader);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }
}

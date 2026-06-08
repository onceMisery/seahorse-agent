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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.annotation.ImportCandidates;

import static org.assertj.core.api.Assertions.assertThat;

class SeahorseAgentStarterAllSmokeTests {

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

    private static final List<String> HEAVY_ADAPTER_AUTO_CONFIGURATIONS = List.of(
            SeahorseAgentAiAdapterAutoConfiguration.class.getName(),
            SeahorseAgentCacheAdapterAutoConfiguration.class.getName(),
            SeahorseAgentKeywordAdapterAutoConfiguration.class.getName(),
            SeahorseAgentMqAdapterAutoConfiguration.class.getName(),
            SeahorseAgentObservationAdapterAutoConfiguration.class.getName(),
            SeahorseAgentStorageAdapterAutoConfiguration.class.getName(),
            SeahorseAgentVectorAdapterAutoConfiguration.class.getName());

    @Test
    void starterAllClasspathContainsOfficialHeavyAdapters() {
        ClassLoader classLoader = getClass().getClassLoader();

        assertThat(OFFICIAL_HEAVY_ADAPTER_CLASSES)
                .allSatisfy(className -> assertThat(loadClass(classLoader, className))
                        .as(className)
                        .isNotNull());
    }

    @Test
    void starterAllClasspathExposesHeavyAdapterAutoConfigurationCandidates() {
        List<String> candidates = ImportCandidates.load(AutoConfiguration.class, getClass().getClassLoader())
                .getCandidates();

        assertThat(candidates).containsAll(HEAVY_ADAPTER_AUTO_CONFIGURATIONS);
    }

    private static Class<?> loadClass(ClassLoader classLoader, String className) {
        try {
            return Class.forName(className, false, classLoader);
        } catch (ClassNotFoundException ex) {
            return null;
        }
    }
}

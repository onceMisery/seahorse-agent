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

import com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorProperties;
import com.miracle.ai.seahorse.agent.adapters.vector.pgvector.PgVectorAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import io.milvus.v2.client.MilvusClientV2;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import javax.sql.DataSource;
import java.lang.reflect.Field;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SeahorseAgentVectorAdapterAutoConfigurationTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(SeahorseAgentVectorAdapterAutoConfiguration.class))
            .withBean(MilvusClientV2.class, () -> mock(MilvusClientV2.class));

    @Test
    void shouldDeriveMilvusDimensionFromEmbeddingModelWhenVectorDimensionIsUnset() {
        contextRunner.withPropertyValues(
                        "seahorse.agent.adapters.vector.type=milvus",
                        "seahorse.agent.adapters.vector.collection-name=kb_chunks",
                        "seahorse.agent.adapters.ai.embedding-model=nomic-embed-text")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MilvusVectorAdapter.class);
                    assertThat(dimension(context.getBean(MilvusVectorAdapter.class))).isEqualTo(768);
                });
    }

    @Test
    void shouldSelectMilvusFromCanonicalAdapterProperties() {
        contextRunner.withPropertyValues(
                        "seahorse.agent.adapters.vector.type=noop",
                        "seahorse-agent.adapters.vector.type=milvus",
                        "seahorse-agent.adapters.vector.collection-name=kb_chunks",
                        "seahorse-agent.adapters.ai.embedding-model=nomic-embed-text")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(MilvusVectorAdapter.class);
                    assertThat(dimension(context.getBean(MilvusVectorAdapter.class))).isEqualTo(768);
                });
    }

    @Test
    void shouldExposePgVectorPortsFromCanonicalAdapterProperties() {
        contextRunner.withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .withPropertyValues(
                        "seahorse.agent.adapters.vector.type=noop",
                        "seahorse-agent.adapters.vector.type=pgvector",
                        "seahorse-agent.adapters.ai.embedding-model=nomic-embed-text")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PgVectorAdapter.class);
                    assertThat(context).hasSingleBean(VectorSearchPort.class);
                    assertThat(context).hasSingleBean(VectorIndexPort.class);
                    assertThat(context).hasSingleBean(VectorCollectionAdminPort.class);
                });
    }

    @Test
    void shouldExposePgVectorPortsFromDockerEnvironmentVariables() {
        contextRunner.withInitializer(context -> context.getEnvironment().getPropertySources().addFirst(
                        new SystemEnvironmentPropertySource("docker-vector-env", Map.of(
                                "SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE", "pgvector",
                                "SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL", "nomic-embed-text"))))
                .withBean(DataSource.class, () -> mock(DataSource.class))
                .withBean(ObjectMapper.class, ObjectMapper::new)
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context).hasSingleBean(PgVectorAdapter.class);
                    assertThat(context).hasSingleBean(VectorSearchPort.class);
                    assertThat(context).hasSingleBean(VectorIndexPort.class);
                    assertThat(context).hasSingleBean(VectorCollectionAdminPort.class);
                });
    }

    @Test
    void shouldLetExplicitVectorDimensionOverrideEmbeddingModelDimension() {
        contextRunner.withPropertyValues(
                        "seahorse.agent.adapters.vector.type=milvus",
                        "seahorse.agent.adapters.vector.collection-name=kb_chunks",
                        "seahorse.agent.adapters.vector.dimension=512",
                        "seahorse.agent.adapters.ai.embedding-model=bge-m3")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(dimension(context.getBean(MilvusVectorAdapter.class))).isEqualTo(512);
                });
    }

    @Test
    void shouldDeriveMilvusDimensionFromConfiguredCustomEmbeddingModelRegistry() {
        contextRunner.withPropertyValues(
                        "seahorse.agent.adapters.vector.type=milvus",
                        "seahorse.agent.adapters.vector.collection-name=kb_chunks",
                        "seahorse.agent.adapters.ai.embedding-model=acme-embed-v2",
                        "seahorse.agent.adapters.ai.embedding-model-dimensions=acme-embed-v2=1024")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(dimension(context.getBean(MilvusVectorAdapter.class))).isEqualTo(1024);
                });
    }

    @Test
    void shouldFailForUnknownEmbeddingModelWithoutDimensionMapping() {
        contextRunner.withPropertyValues(
                        "seahorse.agent.adapters.vector.type=milvus",
                        "seahorse.agent.adapters.vector.collection-name=kb_chunks",
                        "seahorse.agent.adapters.ai.embedding-model=unknown-embedder")
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure()).hasMessageContaining("unknown-embedder");
                    assertThat(context.getStartupFailure()).hasMessageContaining("embedding-model-dimensions");
                });
    }

    private static int dimension(MilvusVectorAdapter adapter) {
        try {
            Field field = MilvusVectorAdapter.class.getDeclaredField("properties");
            field.setAccessible(true);
            return ((MilvusVectorProperties) field.get(adapter)).dimension();
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError("Cannot inspect Milvus vector properties", ex);
        }
    }
}

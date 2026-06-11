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
import com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorProperties;
import com.miracle.ai.seahorse.agent.adapters.vector.noop.NoopVectorStoreAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.pgvector.PgVectorAdapter;
import com.miracle.ai.seahorse.agent.adapters.vector.pgvector.PgVectorProperties;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

/**
 * 向量存储适配器自动配置。
 *
 * <p>将 Milvus、PgVector、Noop 三类 provider 与端口暴露集中管理，避免 native 主配置继续承担向量 SDK 细节。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter(DataSourceAutoConfiguration.class)
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentVectorAdapterAutoConfiguration {
    @Bean
    @ConditionalOnProperty(prefix = "seahorse.agent.adapters.vector", name = "type", havingValue = "noop")
    @ConditionalOnMissingBean(NoopVectorStoreAdapter.class)
    public NoopVectorStoreAdapter seahorseNoopVectorStoreAdapter() {
        return new NoopVectorStoreAdapter();
    }

    @Bean
    @ConditionalOnBean(NoopVectorStoreAdapter.class)
    @ConditionalOnMissingBean(VectorSearchPort.class)
    public VectorSearchPort seahorseNativeNoopVectorSearchPort(NoopVectorStoreAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(NoopVectorStoreAdapter.class)
    @ConditionalOnMissingBean(VectorIndexPort.class)
    public VectorIndexPort seahorseNativeNoopVectorIndexPort(NoopVectorStoreAdapter adapter) {
        return adapter;
    }

    @Bean
    @ConditionalOnBean(NoopVectorStoreAdapter.class)
    @ConditionalOnMissingBean(VectorCollectionAdminPort.class)
    public VectorCollectionAdminPort seahorseNativeNoopVectorAdminPort(NoopVectorStoreAdapter adapter) {
        return adapter;
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = {
            "io.milvus.v2.client.MilvusClientV2",
            "com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorAdapter"
    })
    static class MilvusVectorAutoConfiguration {

        @Bean(destroyMethod = "close")
        @ConditionalOnMissingBean(MilvusClientV2.class)
        @ConditionalOnProperty(prefix = "seahorse.agent.adapters.vector", name = "type", havingValue = "milvus", matchIfMissing = true)
        public MilvusClientV2 seahorseMilvusClient(
                @Value("${seahorse.agent.adapters.vector.milvus.host:localhost}") String host,
                @Value("${seahorse.agent.adapters.vector.milvus.port:19530}") int port) {
            ConnectConfig config = ConnectConfig.builder()
                    .uri("http://" + host + ":" + port)
                    .build();
            return new MilvusClientV2(config);
        }

        @Bean
        @ConditionalOnBean(MilvusClientV2.class)
        @ConditionalOnProperty(prefix = "seahorse.agent.adapters.vector", name = "type",
                havingValue = "milvus", matchIfMissing = true)
        @ConditionalOnMissingBean(MilvusVectorAdapter.class)
        public MilvusVectorAdapter seahorseMilvusVectorAdapter(
                MilvusClientV2 milvusClient,
                @Value("${seahorse.agent.adapters.vector.collection-name:}") String collectionName,
                @Value("${seahorse.agent.adapters.vector.dimension:1024}") int dimension,
                @Value("${seahorse.agent.adapters.vector.metric-type:COSINE}") String metricType,
                @Value("${seahorse.agent.adapters.vector.milvus.content-max-length:65535}") int contentMaxLength,
                @Value("${seahorse.agent.adapters.vector.milvus.hnsw.m:48}") int hnswM,
                @Value("${seahorse.agent.adapters.vector.milvus.hnsw.ef-construction:200}") int hnswEfConstruction,
                @Value("${seahorse.agent.adapters.vector.milvus.mmap-enabled:false}") boolean mmapEnabled,
                @Value("${seahorse.agent.adapters.vector.milvus.search-ef:128}") int searchEf) {
            return new MilvusVectorAdapter(milvusClient, new MilvusVectorProperties(
                    collectionName, dimension, metricType, contentMaxLength,
                    hnswM, hnswEfConstruction, mmapEnabled, searchEf));
        }

        @Bean
        @ConditionalOnBean(MilvusVectorAdapter.class)
        @ConditionalOnMissingBean(VectorSearchPort.class)
        public VectorSearchPort seahorseNativeMilvusVectorSearchPort(MilvusVectorAdapter adapter) {
            return adapter;
        }

        @Bean
        @ConditionalOnBean(MilvusVectorAdapter.class)
        @ConditionalOnMissingBean(VectorIndexPort.class)
        public VectorIndexPort seahorseNativeMilvusVectorIndexPort(MilvusVectorAdapter adapter) {
            return adapter;
        }

        @Bean
        @ConditionalOnBean(MilvusVectorAdapter.class)
        @ConditionalOnMissingBean(VectorCollectionAdminPort.class)
        public VectorCollectionAdminPort seahorseNativeMilvusVectorAdminPort(MilvusVectorAdapter adapter) {
            return adapter;
        }
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(name = "com.miracle.ai.seahorse.agent.adapters.vector.pgvector.PgVectorAdapter")
    static class PgVectorAutoConfiguration {

        @Bean
        @ConditionalOnBean(DataSource.class)
        @ConditionalOnProperty(prefix = "seahorse.agent.adapters.vector", name = "type", havingValue = "pgvector")
        @ConditionalOnMissingBean(PgVectorAdapter.class)
        public PgVectorAdapter seahorsePgVectorAdapter(
                DataSource dataSource,
                ObjectMapper objectMapper,
                @Value("${seahorse.agent.adapters.vector.dimension:1024}") int dimension) {
            return new PgVectorAdapter(dataSource, objectMapper,
                    new PgVectorProperties("t_knowledge_vector", dimension));
        }

        @Bean
        @ConditionalOnBean(PgVectorAdapter.class)
        @ConditionalOnMissingBean(VectorSearchPort.class)
        public VectorSearchPort seahorseNativePgVectorSearchPort(PgVectorAdapter adapter) {
            return adapter;
        }

        @Bean
        @ConditionalOnBean(PgVectorAdapter.class)
        @ConditionalOnMissingBean(VectorIndexPort.class)
        public VectorIndexPort seahorseNativePgVectorIndexPort(PgVectorAdapter adapter) {
            return adapter;
        }

        @Bean
        @ConditionalOnBean(PgVectorAdapter.class)
        @ConditionalOnMissingBean(VectorCollectionAdminPort.class)
        public VectorCollectionAdminPort seahorseNativePgVectorAdminPort(PgVectorAdapter adapter) {
            return adapter;
        }
    }
}

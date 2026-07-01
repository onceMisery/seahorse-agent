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

package com.miracle.ai.seahorse.agent.starter.all;

import java.util.List;
import java.util.Objects;

/**
 * Acceptance coordinates for the heavy adapters aggregated by starter-all.
 */
public final class StarterAllAdapterAcceptanceMatrix {

    private static final List<AdapterAcceptance> ENTRIES = List.of(
            entry(
                    "ai-openai-compatible",
                    "seahorse-agent-adapter-ai-openai-compatible",
                    "com.miracle.ai.seahorse.agent.adapters.ai.openai.OpenAiCompatibleModelAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentAiAdapterAutoConfiguration",
                    List.of(
                            "seahorse-agent.adapters.ai.type=openai-compatible",
                            "seahorse-agent.adapters.ai.base-url=<openai-compatible-url>",
                            "seahorse-agent.adapters.ai.chat-model=<chat-model>",
                            "seahorse-agent.adapters.ai.embedding-model=<embedding-model>"),
                    List.of("OpenAI-compatible chat/embedding endpoint"),
                    "ModelHealthPort reports provider availability or a concrete provider error",
                    "create chat, streaming chat, embedding, rerank, token counter, and model health ports",
                    List.of(
                            "com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.model.ModelHealthPort")),
            entry(
                    "mcp-http",
                    "seahorse-agent-adapter-mcp-http",
                    "com.miracle.ai.seahorse.agent.adapters.mcp.http.StreamableHttpMcpClient",
                    "com.miracle.ai.seahorse.agent.adapters.mcp.http.McpHttpAutoConfiguration",
                    List.of(
                            "seahorse-agent.plugins.native-mcp.enabled=true",
                            "seahorse-agent.adapters.mcp.servers[0].name=<server-name>",
                            "seahorse-agent.adapters.mcp.servers[0].url=<server-url>"),
                    List.of("MCP streamable HTTP server"),
                    "McpToolRegistryPort can list discovered local or remote tools",
                    "discover MCP tools and invoke a harmless listed tool through the registry",
                    List.of(
                            "com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpParameterExtractionPort")),
            entry(
                    "openapi",
                    "seahorse-agent-adapter-openapi",
                    "com.miracle.ai.seahorse.agent.adapters.openapi.OpenApiSpecParserAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.openapi.OpenApiAdapterAutoConfiguration",
                    List.of("spring.autoconfigure.exclude=<none>"),
                    List.of("none (classpath-only OpenAPI spec parsing)"),
                    "OpenApiSpecParserPort bean is present",
                    "parse a minimal OpenAPI document and expose operations",
                    List.of("com.miracle.ai.seahorse.agent.ports.outbound.agent.OpenApiSpecParserPort")),
            entry(
                    "sandbox-container",
                    "seahorse-agent-adapter-sandbox-container",
                    "com.miracle.ai.seahorse.agent.adapters.sandbox.container.ContainerSandboxRuntimeAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.sandbox.container.ContainerSandboxAutoConfiguration",
                    List.of(
                            "seahorse-agent.adapters.sandbox.runtime=container",
                            "seahorse-agent.adapters.sandbox.container.engine=docker",
                            "seahorse-agent.adapters.sandbox.container.python-image=python:3.11-alpine"),
                    List.of("Docker or Podman CLI reachable from the backend host"),
                    "SandboxRuntimePort resolves to the container adapter when explicitly enabled",
                    "create a CODE_INTERPRETER session and execute a small Python script in a no-network container",
                    List.of("com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort")),
            entry(
                    "parser-tika",
                    "seahorse-agent-adapter-parser-tika",
                    "com.miracle.ai.seahorse.agent.adapters.parser.tika.TikaDocumentParserAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentLocalAdapterAutoConfiguration",
                    List.of("seahorse-agent.adapters.parser.type=tika"),
                    List.of("none (classpath-only Apache Tika parser)"),
                    "DocumentParserPort bean resolves to the Tika parser",
                    "parse a small PDF or text document and return normalized metadata",
                    List.of("com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentParserPort")),
            entry(
                    "source-feishu",
                    "seahorse-agent-adapter-source-feishu",
                    "com.miracle.ai.seahorse.agent.adapters.source.feishu.FeishuDocumentFetcherAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.source.feishu.FeishuDocumentSourceAutoConfiguration",
                    List.of(
                            "seahorse-agent.adapters.source.feishu.enabled=true",
                            "seahorse-agent.adapters.source.feishu.base-url=https://open.feishu.cn",
                            "seahorse-agent.adapters.source.feishu.app-id=<app-id>",
                            "seahorse-agent.adapters.source.feishu.app-secret=<app-secret>"),
                    List.of("Feishu/Lark document API"),
                    "DocumentFetcherPort includes the Feishu fetcher when the source is enabled",
                    "fetch a configured Feishu document token into ingestion bytes",
                    List.of("com.miracle.ai.seahorse.agent.ports.outbound.ingestion.DocumentFetcherPort")),
            entry(
                    "vector-milvus",
                    "seahorse-agent-adapter-vector-milvus",
                    "com.miracle.ai.seahorse.agent.adapters.vector.milvus.MilvusVectorAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentVectorAdapterAutoConfiguration",
                    List.of(
                            "seahorse-agent.adapters.vector.type=milvus",
                            "seahorse-agent.adapters.vector.milvus.host=<milvus-host>",
                            "seahorse-agent.adapters.vector.milvus.port=19530",
                            "seahorse-agent.adapters.ai.embedding-model=<known-dimension-model>"),
                    List.of("Milvus 2.x"),
                    "vector-store SRE health contributor reports a VectorSearchPort bean",
                    "create a collection, index one vector, and retrieve it by similarity",
                    List.of(
                            "com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort")),
            entry(
                    "vector-pgvector",
                    "seahorse-agent-adapter-vector-pgvector",
                    "com.miracle.ai.seahorse.agent.adapters.vector.pgvector.PgVectorAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentVectorAdapterAutoConfiguration",
                    List.of(
                            "seahorse-agent.adapters.vector.type=pgvector",
                            "spring.datasource.url=<postgres-jdbc-url>",
                            "seahorse-agent.adapters.ai.embedding-model=<known-dimension-model>"),
                    List.of("PostgreSQL with pgvector extension"),
                    "vector-store SRE health contributor reports a PgVector-backed VectorSearchPort bean",
                    "index one vector into PostgreSQL and retrieve it by similarity",
                    List.of(
                            "com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorSearchPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorIndexPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.vector.VectorCollectionAdminPort")),
            entry(
                    "cache-redis",
                    "seahorse-agent-adapter-cache-redis",
                    "com.miracle.ai.seahorse.agent.adapters.cache.redis.RedisCacheAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentCacheAdapterAutoConfiguration",
                    List.of(
                            "seahorse-agent.adapters.cache.type=redis",
                            "spring.data.redis.host=<redis-host>",
                            "spring.data.redis.port=6379"),
                    List.of("Redis"),
                    "Redis health contributor reports reachable Redis and cache beans are present",
                    "write/read a key and acquire/release a distributed semaphore",
                    List.of(
                            "com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.coordination.DistributedSemaphorePort")),
            entry(
                    "storage-s3",
                    "seahorse-agent-adapter-storage-s3",
                    "com.miracle.ai.seahorse.agent.adapters.storage.s3.S3ObjectStorageAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentS3StorageAutoConfiguration",
                    List.of(
                            "seahorse-agent.adapters.storage.type=s3",
                            "seahorse-agent.adapters.storage.s3.endpoint=<s3-endpoint>",
                            "seahorse-agent.adapters.storage.s3.bucket=<bucket>",
                            "seahorse-agent.adapters.storage.s3.region=<region>"),
                    List.of("S3-compatible object storage such as MinIO"),
                    "object-storage SRE health contributor reports an ObjectStoragePort bean",
                    "put, read, and delete a small object by object reference",
                    List.of("com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort")),
            entry(
                    "mq-pulsar",
                    "seahorse-agent-adapter-mq-pulsar",
                    "com.miracle.ai.seahorse.agent.adapters.mq.pulsar.PulsarMessageQueueAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentMqAdapterAutoConfiguration",
                    List.of(
                            "seahorse-agent.adapters.mq.type=pulsar",
                            "seahorse-agent.adapters.mq.pulsar.service-url=pulsar://<pulsar-host>:6650"),
                    List.of("Apache Pulsar broker"),
                    "MessageQueuePort and MessageSubscriptionPort beans are present",
                    "publish and consume one message on a smoke topic",
                    List.of(
                            "com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageQueuePort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.mq.MessageSubscriptionPort")),
            entry(
                    "observation-micrometer",
                    "seahorse-agent-adapter-observation-micrometer",
                    "com.miracle.ai.seahorse.agent.adapters.observation.micrometer.MicrometerObservationAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentObservationAdapterAutoConfiguration",
                    List.of("seahorse-agent.adapters.observation.type=micrometer"),
                    List.of("Micrometer MeterRegistry"),
                    "ObservationPort bean records counters and timers into the MeterRegistry",
                    "record one timer and verify the metric is visible",
                    List.of("com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort")),
            entry(
                    "search-elasticsearch",
                    "seahorse-agent-adapter-search-elasticsearch",
                    "com.miracle.ai.seahorse.agent.adapters.search.elasticsearch.ElasticsearchKeywordSearchAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentKeywordAdapterAutoConfiguration",
                    List.of(
                            "seahorse-agent.adapters.keyword-search.type=elasticsearch",
                            "seahorse-agent.adapters.keyword-index.type=elasticsearch",
                            "seahorse-agent.adapters.keyword-search.elasticsearch.base-url=http://<elasticsearch-host>:9200",
                            "seahorse-agent.adapters.keyword-index.elasticsearch.index-name=seahorse_keyword_chunk"),
                    List.of("Elasticsearch"),
                    "keyword-search SRE health contributor reports KeywordSearchPort and KeywordIndexPort beans",
                    "index one chunk and retrieve it with a keyword query",
                    List.of(
                            "com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort")),
            entry(
                    "search-lucene",
                    "seahorse-agent-adapter-search-lucene",
                    "com.miracle.ai.seahorse.agent.adapters.search.lucene.LuceneKeywordSearchAdapter",
                    "com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentKeywordAdapterAutoConfiguration",
                    List.of(
                            "seahorse-agent.adapters.keyword-search.type=lucene",
                            "seahorse-agent.adapters.keyword-index.type=lucene",
                            "seahorse-agent.adapters.keyword-search.lucene.index-directory=<writable-directory>"),
                    List.of("local writable Lucene index directory"),
                    "keyword-search SRE health contributor reports KeywordSearchPort and KeywordIndexPort beans",
                    "index one chunk and retrieve it with a keyword query",
                    List.of(
                            "com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordSearchPort",
                            "com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort")));

    private StarterAllAdapterAcceptanceMatrix() {
    }

    public static List<AdapterAcceptance> entries() {
        return ENTRIES;
    }

    private static AdapterAcceptance entry(String id,
                                           String moduleArtifactId,
                                           String adapterClassName,
                                           String autoConfigurationClassName,
                                           List<String> activationProperties,
                                           List<String> requiredInfrastructure,
                                           String healthCheck,
                                           String minimalBusinessAction,
                                           List<String> expectedBeanTypes) {
        return new AdapterAcceptance(
                id,
                moduleArtifactId,
                adapterClassName,
                autoConfigurationClassName,
                activationProperties,
                requiredInfrastructure,
                healthCheck,
                minimalBusinessAction,
                expectedBeanTypes);
    }

    public record AdapterAcceptance(String id,
                                    String moduleArtifactId,
                                    String adapterClassName,
                                    String autoConfigurationClassName,
                                    List<String> activationProperties,
                                    List<String> requiredInfrastructure,
                                    String healthCheck,
                                    String minimalBusinessAction,
                                    List<String> expectedBeanTypes) {

        public AdapterAcceptance {
            id = requireText(id, "id");
            moduleArtifactId = requireText(moduleArtifactId, "moduleArtifactId");
            adapterClassName = requireText(adapterClassName, "adapterClassName");
            autoConfigurationClassName = requireText(autoConfigurationClassName, "autoConfigurationClassName");
            activationProperties = List.copyOf(Objects.requireNonNull(
                    activationProperties, "activationProperties must not be null"));
            requiredInfrastructure = List.copyOf(Objects.requireNonNull(
                    requiredInfrastructure, "requiredInfrastructure must not be null"));
            healthCheck = requireText(healthCheck, "healthCheck");
            minimalBusinessAction = requireText(minimalBusinessAction, "minimalBusinessAction");
            expectedBeanTypes = List.copyOf(Objects.requireNonNull(
                    expectedBeanTypes, "expectedBeanTypes must not be null"));
            if (activationProperties.isEmpty()) {
                throw new IllegalArgumentException("activationProperties must not be empty");
            }
            if (requiredInfrastructure.isEmpty()) {
                throw new IllegalArgumentException("requiredInfrastructure must not be empty");
            }
            if (expectedBeanTypes.isEmpty()) {
                throw new IllegalArgumentException("expectedBeanTypes must not be empty");
            }
        }
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        return value;
    }
}

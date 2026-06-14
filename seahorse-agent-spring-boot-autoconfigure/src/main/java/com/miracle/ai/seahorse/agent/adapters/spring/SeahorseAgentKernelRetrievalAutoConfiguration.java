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

import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelMultiChannelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEngine.KernelRetrievalEnginePorts;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEvaluationDatasetService;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalEvaluationService;
import com.miracle.ai.seahorse.agent.kernel.application.retrieval.KernelRetrievalStrategyTemplateService;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.DefaultMetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.MetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationDatasetInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalStrategyTemplateInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpParameterExtractionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalContextFormatPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationComparisonRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationDatasetRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalEvaluationRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalStrategyTemplateRepositoryPort;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * 检索编排能力自动配置。
 *
 * <p>该配置聚合检索线程池、MCP 编排、检索引擎和检索治理入口，避免主 kernel 配置继续承载
 * 检索编排链路的装配细节。
 */
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({SeahorseAgentKernelAutoConfiguration.class, SeahorseAgentRetrievalRepositoryAutoConfiguration.class})
@ConditionalOnProperty(prefix = "seahorse.agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelRetrievalAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(name = "ragRetrievalThreadPoolExecutor")
    public Executor ragRetrievalThreadPoolExecutor(
            @Value("${seahorse.agent.retrieval.executor.core-size:4}") int coreSize,
            @Value("${seahorse.agent.retrieval.executor.max-size:16}") int maxSize,
            @Value("${seahorse.agent.retrieval.executor.queue-capacity:200}") int queueCapacity,
            @Value("${seahorse.agent.retrieval.executor.thread-name-prefix:seahorse-rag-retrieval-}")
            String threadNamePrefix) {
        return threadPoolExecutor(coreSize, maxSize, queueCapacity, threadNamePrefix);
    }

    @Bean
    @ConditionalOnMissingBean(name = "ragInnerRetrievalThreadPoolExecutor")
    public Executor ragInnerRetrievalThreadPoolExecutor(
            @Value("${seahorse.agent.retrieval.inner-executor.core-size:4}") int coreSize,
            @Value("${seahorse.agent.retrieval.inner-executor.max-size:16}") int maxSize,
            @Value("${seahorse.agent.retrieval.inner-executor.queue-capacity:200}") int queueCapacity,
            @Value("${seahorse.agent.retrieval.inner-executor.thread-name-prefix:seahorse-rag-inner-}")
            String threadNamePrefix) {
        return threadPoolExecutor(coreSize, maxSize, queueCapacity, threadNamePrefix);
    }

    @Bean
    @ConditionalOnMissingBean(name = "ragContextThreadPoolExecutor")
    public Executor ragContextThreadPoolExecutor(
            @Value("${seahorse.agent.retrieval.context-executor.core-size:2}") int coreSize,
            @Value("${seahorse.agent.retrieval.context-executor.max-size:8}") int maxSize,
            @Value("${seahorse.agent.retrieval.context-executor.queue-capacity:100}") int queueCapacity,
            @Value("${seahorse.agent.retrieval.context-executor.thread-name-prefix:seahorse-rag-context-}")
            String threadNamePrefix) {
        return threadPoolExecutor(coreSize, maxSize, queueCapacity, threadNamePrefix);
    }

    @Bean
    @ConditionalOnMissingBean
    public MetadataFilterCompiler seahorseMetadataFilterCompiler() {
        return new DefaultMetadataFilterCompiler();
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelMultiChannelRetrievalEngine seahorseKernelMultiChannelRetrievalEngine(
            ExtensionRegistry extensionRegistry,
            @Qualifier("ragRetrievalThreadPoolExecutor") ObjectProvider<Executor> retrievalExecutor,
            FeatureActivationContext activationContext,
            ObjectProvider<MetadataSchemaRegistryPort> schemaRegistryPort,
            ObjectProvider<MetadataFilterCompiler> metadataFilterCompiler,
            ObjectProvider<KernelRagTraceRecorder> traceRecorder,
            ObjectProvider<ObservationPort> observationPort,
            ObjectProvider<MetadataSchemaUsageReportRepositoryPort> schemaUsageReportRepositoryPort,
            @Value("${seahorse.agent.retrieval.embedding-model:${seahorse.agent.adapters.ai.embedding-model:}}")
            String defaultEmbeddingModel) {
        return new KernelMultiChannelRetrievalEngine(extensionRegistry,
                retrievalExecutor.getIfAvailable(() -> Runnable::run), activationContext,
                schemaRegistryPort.getIfAvailable(MetadataSchemaRegistryPort::empty),
                metadataFilterCompiler.getIfAvailable(DefaultMetadataFilterCompiler::new),
                traceRecorder.getIfAvailable(KernelRagTraceRecorder::noop),
                observationPort.getIfAvailable(),
                schemaUsageReportRepositoryPort.getIfAvailable(MetadataSchemaUsageReportRepositoryPort::empty),
                defaultEmbeddingModel);
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelMcpOrchestrator seahorseKernelMcpOrchestrator(
            ObjectProvider<McpToolRegistryPort> toolRegistryPort,
            ObjectProvider<McpParameterExtractionPort> parameterExtractionPort,
            @Qualifier("mcpBatchThreadPoolExecutor") ObjectProvider<Executor> mcpExecutor) {
        return new KernelMcpOrchestrator(
                toolRegistryPort.getIfAvailable(McpToolRegistryPort::empty),
                parameterExtractionPort.getIfAvailable(McpParameterExtractionPort::noop),
                mcpExecutor.getIfAvailable(() -> Runnable::run));
    }

    @Bean
    @ConditionalOnMissingBean
    public KernelRetrievalEngine seahorseKernelRetrievalEngine(
            KernelMultiChannelRetrievalEngine multiChannelRetrievalEngine,
            KernelMcpOrchestrator mcpOrchestrator,
            ObjectProvider<RetrievalContextFormatPort> formatPort,
            @Qualifier("ragContextThreadPoolExecutor") ObjectProvider<Executor> ragContextExecutor) {
        return new KernelRetrievalEngine(new KernelRetrievalEnginePorts(
                multiChannelRetrievalEngine,
                mcpOrchestrator,
                formatPort.getIfAvailable(RetrievalContextFormatPort::noop),
                ragContextExecutor.getIfAvailable(() -> Runnable::run)));
    }

    @Bean
    @Primary
    @ConditionalOnBean(KernelRetrievalEngine.class)
    public RetrievalContextPort seahorseKernelRetrievalContextPort(KernelRetrievalEngine retrievalEngine) {
        return retrievalEngine;
    }

    @Bean
    @ConditionalOnBean(KernelRetrievalEngine.class)
    @ConditionalOnMissingBean(MemoryBusinessDocumentRetrieverPort.class)
    public MemoryBusinessDocumentRetrieverPort seahorseMemoryBusinessDocumentRetrieverPort(
            KernelRetrievalEngine retrievalEngine) {
        return (tenantId, query, topK) -> retrievalEngine
                .retrieveKnowledgeChannels(List.of(new SubQuestionIntent(query, List.of())), topK)
                .stream()
                .map(chunk -> toBusinessDocumentMemoryItem(tenantId, chunk))
                .toList();
    }

    @Bean
    @ConditionalOnBean(KernelRetrievalEngine.class)
    @ConditionalOnMissingBean(RetrievalEvaluationInboundPort.class)
    public KernelRetrievalEvaluationService seahorseRetrievalEvaluationInboundPort(
            KernelRetrievalEngine retrievalEngine) {
        return new KernelRetrievalEvaluationService(retrievalEngine);
    }

    @Bean
    @ConditionalOnMissingBean(RetrievalStrategyTemplateInboundPort.class)
    public KernelRetrievalStrategyTemplateService seahorseRetrievalStrategyTemplateInboundPort(
            ObjectProvider<RetrievalStrategyTemplateRepositoryPort> repositoryPort) {
        return new KernelRetrievalStrategyTemplateService(
                repositoryPort.getIfAvailable(RetrievalStrategyTemplateRepositoryPort::empty));
    }

    @Bean
    @ConditionalOnMissingBean(RetrievalEvaluationDatasetInboundPort.class)
    public KernelRetrievalEvaluationDatasetService seahorseRetrievalEvaluationDatasetInboundPort(
            ObjectProvider<RetrievalEvaluationDatasetRepositoryPort> repositoryPort,
            ObjectProvider<RetrievalEvaluationComparisonRepositoryPort> comparisonRepositoryPort,
            ObjectProvider<RetrievalEvaluationRunRepositoryPort> runRepositoryPort,
            ObjectProvider<RetrievalEvaluationInboundPort> evaluationPort) {
        return new KernelRetrievalEvaluationDatasetService(
                repositoryPort.getIfAvailable(RetrievalEvaluationDatasetRepositoryPort::empty),
                comparisonRepositoryPort.getIfAvailable(RetrievalEvaluationComparisonRepositoryPort::empty),
                runRepositoryPort.getIfAvailable(RetrievalEvaluationRunRepositoryPort::empty),
                evaluationPort.getIfAvailable());
    }

    private static Executor threadPoolExecutor(int coreSize,
                                               int maxSize,
                                               int queueCapacity,
                                               String threadNamePrefix) {
        int safeCoreSize = Math.max(coreSize, 1);
        int safeMaxSize = Math.max(maxSize, safeCoreSize);
        int safeQueueCapacity = Math.max(queueCapacity, 1);
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(safeCoreSize);
        executor.setMaxPoolSize(safeMaxSize);
        executor.setQueueCapacity(safeQueueCapacity);
        executor.setThreadNamePrefix(threadNamePrefix == null || threadNamePrefix.isBlank()
                ? "seahorse-rag-" : threadNamePrefix);
        executor.initialize();
        return executor;
    }

    private static MemoryItem toBusinessDocumentMemoryItem(String tenantId, RetrievedChunk chunk) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("tenantId", tenantId == null || tenantId.isBlank() ? "default" : tenantId);
        metadata.put("kbId", value(chunk.getKbId()));
        metadata.put("docId", value(chunk.getDocId()));
        metadata.put("collectionName", value(chunk.getCollectionName()));
        metadata.put("chunkIndex", chunk.getChunkIndex());
        metadata.put("generationId", value(safeMetadata(chunk).get("generationId")));
        return MemoryItem.builder()
                .id(businessDocumentId(chunk))
                .layer(MemoryLayer.SEMANTIC)
                .type("BUSINESS_DOCUMENT")
                .content(value(chunk.getText()))
                .metadataJson(toJsonObject(metadata))
                .relevanceScore(chunk.getScore() == null ? null : chunk.getScore().doubleValue())
                .createTime(LocalDateTime.now())
                .build();
    }

    private static String toJsonObject(Map<String, Object> metadata) {
        StringBuilder builder = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, Object> entry : metadata.entrySet()) {
            if (!first) {
                builder.append(',');
            }
            first = false;
            builder.append('"').append(escape(entry.getKey())).append('"').append(':');
            Object value = entry.getValue();
            if (value instanceof Number || value instanceof Boolean) {
                builder.append(value);
            } else {
                builder.append('"').append(escape(value(value))).append('"');
            }
        }
        return builder.append('}').toString();
    }

    private static String value(Object value) {
        return value == null ? "" : value.toString();
    }

    private static String businessDocumentId(RetrievedChunk chunk) {
        String id = value(chunk.getId());
        if (!id.isBlank()) {
            return id;
        }
        String source = value(chunk.getDocId()) + ":" + value(chunk.getChunkIndex()) + ":" + value(chunk.getText());
        return "biz-doc-" + Integer.toUnsignedString(source.hashCode());
    }

    private static Map<String, Object> safeMetadata(RetrievedChunk chunk) {
        return chunk.getMetadata() == null ? Map.of() : chunk.getMetadata();
    }

    private static String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

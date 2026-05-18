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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.DefaultMetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.MetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * L1 多通道检索编排器。
 * <p>
 * 主类负责构建检索上下文、编译 metadata filter、串联通道执行与后处理链。
 * 通道并发、通道降级和观测记录分别由包内协作者承担。
 */
public class KernelMultiChannelRetrievalEngine {

    private static final String EVENT_METADATA_FILTER_COMPILED = "retrieval.metadata.filter.compiled";
    private static final String EVENT_METADATA_FILTER_REJECTED = "retrieval.metadata.filter.rejected";
    private static final String EVENT_CHANNEL_COMPLETED = "retrieval.channel.completed";
    private static final String EVENT_METADATA_GUARD = "retrieval.metadata.guard";
    private static final String EVENT_RETRIEVAL_EMPTY = "retrieval.empty";
    private static final String PROCESSOR_METADATA_GUARD = "MetadataGuard";
    private static final Duration DEFAULT_CHANNEL_TIMEOUT = Duration.ofSeconds(5);

    private final KernelRetrievalObservationSupport observationSupport;
    private final KernelSearchContextFactory contextFactory;
    private final KernelSearchChannelExecutor channelExecutor;
    private final KernelRetrievalPostProcessorChain postProcessorChain;

    public KernelMultiChannelRetrievalEngine(ExtensionRegistry extensionRegistry,
                                             Executor retrievalExecutor,
                                             FeatureActivationContext activationContext) {
        this(extensionRegistry, retrievalExecutor, activationContext,
                MetadataSchemaRegistryPort.empty(), new DefaultMetadataFilterCompiler(), KernelRagTraceRecorder.noop());
    }

    public KernelMultiChannelRetrievalEngine(ExtensionRegistry extensionRegistry,
                                             Executor retrievalExecutor,
                                             FeatureActivationContext activationContext,
                                             MetadataSchemaRegistryPort schemaRegistryPort,
                                             MetadataFilterCompiler metadataFilterCompiler) {
        this(extensionRegistry, retrievalExecutor, activationContext, schemaRegistryPort, metadataFilterCompiler,
                KernelRagTraceRecorder.noop());
    }

    public KernelMultiChannelRetrievalEngine(ExtensionRegistry extensionRegistry,
                                             Executor retrievalExecutor,
                                             FeatureActivationContext activationContext,
                                             MetadataSchemaRegistryPort schemaRegistryPort,
                                             MetadataFilterCompiler metadataFilterCompiler,
                                             KernelRagTraceRecorder traceRecorder) {
        this(extensionRegistry, retrievalExecutor, activationContext, schemaRegistryPort, metadataFilterCompiler,
                traceRecorder, null);
    }

    public KernelMultiChannelRetrievalEngine(ExtensionRegistry extensionRegistry,
                                             Executor retrievalExecutor,
                                             FeatureActivationContext activationContext,
                                             MetadataSchemaRegistryPort schemaRegistryPort,
                                             MetadataFilterCompiler metadataFilterCompiler,
                                             KernelRagTraceRecorder traceRecorder,
                                             ObservationPort observationPort) {
        this(extensionRegistry, retrievalExecutor, activationContext, schemaRegistryPort, metadataFilterCompiler,
                traceRecorder, observationPort, MetadataSchemaUsageReportRepositoryPort.empty());
    }

    public KernelMultiChannelRetrievalEngine(ExtensionRegistry extensionRegistry,
                                             Executor retrievalExecutor,
                                             FeatureActivationContext activationContext,
                                             MetadataSchemaRegistryPort schemaRegistryPort,
                                             MetadataFilterCompiler metadataFilterCompiler,
                                             KernelRagTraceRecorder traceRecorder,
                                             ObservationPort observationPort,
                                             MetadataSchemaUsageReportRepositoryPort schemaUsageRepositoryPort) {
        ExtensionRegistry safeExtensionRegistry = Objects.requireNonNull(extensionRegistry, "extensionRegistry must not be null");
        Executor safeRetrievalExecutor = Objects.requireNonNull(retrievalExecutor, "retrievalExecutor must not be null");
        FeatureActivationContext safeActivationContext = Objects.requireNonNullElse(activationContext,
                FeatureActivationContext.empty());
        MetadataSchemaRegistryPort safeSchemaRegistryPort = Objects.requireNonNullElseGet(schemaRegistryPort,
                MetadataSchemaRegistryPort::empty);
        MetadataFilterCompiler safeMetadataFilterCompiler = Objects.requireNonNullElseGet(metadataFilterCompiler,
                DefaultMetadataFilterCompiler::new);
        KernelRagTraceRecorder safeTraceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        // 观测和 usage 记录集中到协作者，主类只决定何时记录。
        this.observationSupport = new KernelRetrievalObservationSupport(
                safeActivationContext,
                observationPort,
                schemaUsageRepositoryPort,
                EVENT_METADATA_FILTER_COMPILED,
                EVENT_METADATA_FILTER_REJECTED,
                EVENT_CHANNEL_COMPLETED,
                EVENT_METADATA_GUARD,
                EVENT_RETRIEVAL_EMPTY,
                PROCESSOR_METADATA_GUARD);
        this.contextFactory = new KernelSearchContextFactory(
                safeActivationContext,
                safeSchemaRegistryPort,
                safeMetadataFilterCompiler,
                this.observationSupport);
        this.channelExecutor = new KernelSearchChannelExecutor(
                safeExtensionRegistry,
                safeRetrievalExecutor,
                safeActivationContext,
                safeTraceRecorder,
                this.observationSupport,
                DEFAULT_CHANNEL_TIMEOUT);
        this.postProcessorChain = new KernelRetrievalPostProcessorChain(
                safeExtensionRegistry,
                safeActivationContext,
                safeTraceRecorder,
                this.observationSupport);
    }

    /**
     * 执行知识库多通道检索。
     *
     * @param subIntents 子问题意图列表
     * @param topK       期望返回数量
     * @return 检索结果 Chunk 列表
     */
    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents, int topK) {
        return retrieveKnowledgeChannels(subIntents, topK, null, null, null);
    }

    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents,
                                                          int topK,
                                                          TraceRunScope traceRunScope) {
        return retrieveKnowledgeChannels(subIntents, topK, null, null, traceRunScope);
    }

    /**
     * 执行带治理过滤条件的知识库多通道检索。
     *
     * @param subIntents 子问题意图列表
     * @param topK       期望返回数量
     * @param filter     已解析的检索过滤条件
     * @param options    检索策略参数
     * @return 检索结果 Chunk 列表
     */
    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents,
                                                          int topK,
                                                          RetrievalFilter filter,
                                                          RetrievalOptions options) {
        return retrieveKnowledgeChannels(subIntents, topK, filter, options, null);
    }

    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents,
                                                          int topK,
                                                          RetrievalFilter filter,
                                                          RetrievalOptions options,
                                                          TraceRunScope traceRunScope) {
        SearchContext context = contextFactory.build(subIntents, topK, filter, options, traceRunScope);
        List<SearchChannelResult> channelResults = channelExecutor.execute(context);
        if (channelResults.isEmpty()) {
            observationSupport.recordRetrievalEmpty(context, "channel", "no_enabled_channels", 0, 0);
            return List.of();
        }
        List<RetrievedChunk> chunks = postProcessorChain.execute(channelResults, context);
        if (chunks.isEmpty()) {
            int candidateCount = postProcessorChain.candidateCount(channelResults);
            observationSupport.recordRetrievalEmpty(context,
                    candidateCount == 0 ? "channel" : "post_processor",
                    candidateCount == 0 ? "channels_returned_empty" : "post_processor_filtered_all",
                    channelResults.size(),
                    candidateCount);
        }
        return chunks;
    }

}

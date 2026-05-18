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
import com.miracle.ai.seahorse.agent.kernel.domain.metadata.MetadataSchema;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchChannelType;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.filter.CompiledMetadataFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.DefaultMetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.MetadataFilterCompiler;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchChannelFeature;
import com.miracle.ai.seahorse.agent.kernel.feature.retrieval.SearchResultPostProcessorFeature;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataSchemaUsageReportRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * L1 多通道检索编排器。
 * <p>
 * 该类只负责稳定编排：构建检索上下文、并行执行已激活通道、合并结果并按顺序执行后处理链。
 * 通道实现、过滤编译、观测记录分别委托给 feature、compiler 与 support 协作者。
 */
public class KernelMultiChannelRetrievalEngine {

    private static final Logger LOG = LoggerFactory.getLogger(KernelMultiChannelRetrievalEngine.class);
    private static final String EVENT_METADATA_FILTER_COMPILED = "retrieval.metadata.filter.compiled";
    private static final String EVENT_METADATA_FILTER_REJECTED = "retrieval.metadata.filter.rejected";
    private static final String EVENT_CHANNEL_COMPLETED = "retrieval.channel.completed";
    private static final String EVENT_METADATA_GUARD = "retrieval.metadata.guard";
    private static final String EVENT_RETRIEVAL_EMPTY = "retrieval.empty";
    private static final String PROCESSOR_METADATA_GUARD = "MetadataGuard";
    private static final String LOG_MSG_CHANNEL_FAILED = "检索通道 {} 执行失败，按空结果降级";
    private static final String LOG_MSG_CHANNEL_TIMEOUT = "检索通道 {} 执行超时，按空结果降级";
    private static final String LOG_MSG_PROCESSOR_FAILED = "检索后处理器 {} 执行失败，跳过该处理器";
    private static final Duration DEFAULT_CHANNEL_TIMEOUT = Duration.ofSeconds(5);

    private final ExtensionRegistry extensionRegistry;
    private final Executor retrievalExecutor;
    private final FeatureActivationContext activationContext;
    private final MetadataSchemaRegistryPort schemaRegistryPort;
    private final MetadataFilterCompiler metadataFilterCompiler;
    private final KernelRagTraceRecorder traceRecorder;
    private final KernelRetrievalObservationSupport observationSupport;

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
        this.extensionRegistry = Objects.requireNonNull(extensionRegistry, "extensionRegistry must not be null");
        this.retrievalExecutor = Objects.requireNonNull(retrievalExecutor, "retrievalExecutor must not be null");
        this.activationContext = Objects.requireNonNullElse(activationContext, FeatureActivationContext.empty());
        this.schemaRegistryPort = Objects.requireNonNullElseGet(schemaRegistryPort, MetadataSchemaRegistryPort::empty);
        this.metadataFilterCompiler = Objects.requireNonNullElseGet(metadataFilterCompiler,
                DefaultMetadataFilterCompiler::new);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        // 将观测与 usage 记录下沉到独立协作者，避免主类继续承担统计职责。
        this.observationSupport = new KernelRetrievalObservationSupport(
                this.activationContext,
                observationPort,
                schemaUsageRepositoryPort,
                EVENT_METADATA_FILTER_COMPILED,
                EVENT_METADATA_FILTER_REJECTED,
                EVENT_CHANNEL_COMPLETED,
                EVENT_METADATA_GUARD,
                EVENT_RETRIEVAL_EMPTY,
                PROCESSOR_METADATA_GUARD);
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
        SearchContext context = buildSearchContext(subIntents, topK, filter, options, traceRunScope);
        List<SearchChannelResult> channelResults = executeSearchChannels(context);
        if (channelResults.isEmpty()) {
            observationSupport.recordRetrievalEmpty(context, "channel", "no_enabled_channels", 0, 0);
            return List.of();
        }
        List<RetrievedChunk> chunks = executePostProcessors(channelResults, context);
        if (chunks.isEmpty()) {
            int candidateCount = channelCandidateCount(channelResults);
            observationSupport.recordRetrievalEmpty(context,
                    candidateCount == 0 ? "channel" : "post_processor",
                    candidateCount == 0 ? "channels_returned_empty" : "post_processor_filtered_all",
                    channelResults.size(),
                    candidateCount);
        }
        return chunks;
    }

    private List<SearchChannelResult> executeSearchChannels(SearchContext context) {
        List<SearchChannelFeature> enabledChannels = extensionRegistry
                .getActivatedExtensions(SearchChannelFeature.class, activationContext)
                .stream()
                .filter(channel -> channel.enabled(context))
                .toList();

        if (enabledChannels.isEmpty()) {
            return List.of();
        }

        List<CompletableFuture<SearchChannelResult>> futures = enabledChannels.stream()
                .map(channel -> executeSingleChannelWithTimeout(channel, context))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private CompletableFuture<SearchChannelResult> executeSingleChannelWithTimeout(SearchChannelFeature channel,
                                                                                   SearchContext context) {
        TraceNodeScope nodeScope = traceRecorder.startNode(traceRunScope(context), channelTraceCommand(channel));
        long startedAt = System.currentTimeMillis();
        long timeoutMs = channelTimeout(context, channel).toMillis();
        // 单通道超时只影响当前通道，避免慢后端拖垮整个检索流程。
        return CompletableFuture.supplyAsync(() -> channel.search(context), retrievalExecutor)
                .orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .handle((result, throwable) -> completeSingleChannel(
                        channel, context, nodeScope, startedAt, timeoutMs, result, throwable));
    }

    private SearchChannelResult completeSingleChannel(SearchChannelFeature channel,
                                                      SearchContext context,
                                                      TraceNodeScope nodeScope,
                                                      long startedAt,
                                                      long timeoutMs,
                                                      SearchChannelResult result,
                                                      Throwable throwable) {
        long elapsedMs = System.currentTimeMillis() - startedAt;
        Throwable cause = unwrap(throwable);
        if (cause == null) {
            SearchChannelResult safeResult = result == null ? emptyResult(channel, elapsedMs) : result;
            observationSupport.recordChannelCompleted(channel, context, safeResult, null, elapsedMs, timeoutMs, false);
            traceRecorder.finishNode(nodeScope);
            return safeResult;
        }
        SearchChannelResult fallback = emptyResult(channel, elapsedMs);
        boolean timedOut = cause instanceof TimeoutException;
        observationSupport.recordChannelCompleted(channel, context, fallback, cause, elapsedMs, timeoutMs, timedOut);
        traceRecorder.finishNode(nodeScope, cause);
        if (timedOut) {
            LOG.warn(LOG_MSG_CHANNEL_TIMEOUT, channel.name(), cause);
        } else {
            LOG.error(LOG_MSG_CHANNEL_FAILED, channel.name(), cause);
        }
        return fallback;
    }

    private Throwable unwrap(Throwable throwable) {
        if (throwable instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return throwable;
    }

    private Duration channelTimeout(SearchContext context, SearchChannelFeature channel) {
        RetrievalOptions options = context == null ? null : context.effectiveOptions();
        SearchChannelType type = channel == null ? null : channel.channelType();
        Duration configured = switch (type == null ? SearchChannelType.HYBRID : type) {
            case VECTOR_GLOBAL, INTENT_DIRECTED -> options == null ? null : options.vectorTimeout();
            case KEYWORD_BM25, KEYWORD_ES -> options == null ? null : options.keywordTimeout();
            case HYBRID -> null;
        };
        return positiveDuration(configured, DEFAULT_CHANNEL_TIMEOUT);
    }

    private Duration positiveDuration(Duration value, Duration fallback) {
        if (value == null || value.isZero() || value.isNegative()) {
            return fallback;
        }
        return value;
    }

    private SearchChannelResult emptyResult(SearchChannelFeature channel, long latencyMs) {
        return SearchChannelResult.builder()
                .channelType(channel.channelType())
                .channelName(channel.name())
                .chunks(List.of())
                .latencyMs(latencyMs)
                .build();
    }

    private List<RetrievedChunk> executePostProcessors(List<SearchChannelResult> results, SearchContext context) {
        List<SearchResultPostProcessorFeature> processors = extensionRegistry
                .getActivatedExtensions(SearchResultPostProcessorFeature.class, activationContext)
                .stream()
                .filter(processor -> processor.enabled(context))
                .sorted(Comparator.comparingInt(SearchResultPostProcessorFeature::order))
                .toList();

        List<RetrievedChunk> chunks = mergeChunks(results);
        for (SearchResultPostProcessorFeature processor : processors) {
            chunks = executeSingleProcessor(processor, chunks, results, context);
        }
        return chunks;
    }

    private List<RetrievedChunk> executeSingleProcessor(SearchResultPostProcessorFeature processor,
                                                        List<RetrievedChunk> chunks,
                                                        List<SearchChannelResult> results,
                                                        SearchContext context) {
        TraceNodeScope nodeScope = traceRecorder.startNode(traceRunScope(context), processorTraceCommand(processor));
        long startedAt = System.currentTimeMillis();
        int inputCount = chunkCount(chunks);
        try {
            List<RetrievedChunk> processed = processor.process(chunks, results, context);
            observationSupport.recordMetadataGuardCompleted(processor, context, inputCount, chunkCount(processed),
                    System.currentTimeMillis() - startedAt, null);
            traceRecorder.finishNode(nodeScope);
            return processed;
        } catch (Exception ex) {
            observationSupport.recordMetadataGuardCompleted(processor, context, inputCount, inputCount,
                    System.currentTimeMillis() - startedAt, ex);
            traceRecorder.finishNode(nodeScope, ex);
            LOG.error(LOG_MSG_PROCESSOR_FAILED, processor.name(), ex);
            return chunks;
        }
    }

    private List<RetrievedChunk> mergeChunks(List<SearchChannelResult> results) {
        return results.stream()
                .flatMap(result -> safeChunks(result).stream())
                .collect(Collectors.toList());
    }

    private List<RetrievedChunk> safeChunks(SearchChannelResult result) {
        if (result == null) {
            return List.of();
        }
        return Objects.requireNonNullElse(result.getChunks(), List.of());
    }

    private int chunkCount(List<RetrievedChunk> chunks) {
        return chunks == null ? 0 : chunks.size();
    }

    private int channelCandidateCount(List<SearchChannelResult> results) {
        return Objects.requireNonNullElse(results, List.<SearchChannelResult>of()).stream()
                .mapToInt(result -> safeChunks(result).size())
                .sum();
    }

    private SearchContext buildSearchContext(List<SubQuestionIntent> subIntents,
                                             int topK,
                                             RetrievalFilter filter,
                                             RetrievalOptions options,
                                             TraceRunScope traceRunScope) {
        List<SubQuestionIntent> safeSubIntents = Objects.requireNonNullElse(subIntents, List.of());
        String question = safeSubIntents.isEmpty() ? "" : safeSubIntents.get(0).subQuestion();
        return SearchContext.builder()
                .originalQuestion(question)
                .rewrittenQuestion(question)
                .intents(safeSubIntents)
                .topK(topK)
                .filter(filter)
                .options(options)
                .compiledFilter(compileFilter(filter))
                .traceRunScope(traceRunScope)
                .build();
    }

    private TraceRunScope traceRunScope(SearchContext context) {
        TraceRunScope traceRunScope = context == null ? null : context.getTraceRunScope();
        return traceRunScope == null ? TraceRunScope.disabled() : traceRunScope;
    }

    private TraceNodeStartCommand channelTraceCommand(SearchChannelFeature channel) {
        // Trace 只记录编排节点，不改变通道失败时返回空结果的降级语义。
        return new TraceNodeStartCommand(
                "search-channel:" + Objects.requireNonNullElse(channel.name(), "unknown"),
                "RETRIEVAL_CHANNEL",
                channel.getClass().getName(),
                "search",
                null,
                1);
    }

    private TraceNodeStartCommand processorTraceCommand(SearchResultPostProcessorFeature processor) {
        return new TraceNodeStartCommand(
                "post-processor:" + Objects.requireNonNullElse(processor.name(), "unknown"),
                "RETRIEVAL_POST_PROCESSOR",
                processor.getClass().getName(),
                "process",
                null,
                1);
    }

    private CompiledMetadataFilter compileFilter(RetrievalFilter filter) {
        if (filter == null) {
            return null;
        }
        String knowledgeBaseId = filter.system().knowledgeBaseIds().isEmpty()
                ? ""
                : filter.system().knowledgeBaseIds().get(0);
        String tenantId = !filter.system().tenantId().isBlank() ? filter.system().tenantId() : activationContext.tenantId();
        MetadataSchema schema = schemaRegistryPort.loadSchema(tenantId, knowledgeBaseId);
        try {
            // 只有通过 schema 编译后的过滤条件才能下发到检索通道与后处理器。
            CompiledMetadataFilter compiledFilter = metadataFilterCompiler.compile(filter, schema);
            observationSupport.recordMetadataFilterUsage(tenantId, knowledgeBaseId, schema, compiledFilter);
            return compiledFilter;
        } catch (RuntimeException ex) {
            observationSupport.recordMetadataFilterRejected(tenantId, knowledgeBaseId, schema, filter, ex);
            throw ex;
        }
    }
}

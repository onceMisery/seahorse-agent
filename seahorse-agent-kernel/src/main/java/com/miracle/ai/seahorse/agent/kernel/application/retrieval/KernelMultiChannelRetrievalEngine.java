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
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

/**
 * L1 多通道检索编排器。
 * <p>
 * 该类只负责稳定编排：构建检索上下文、并行执行已激活通道、合并结果并按顺序执行后处理链。
 * 具体检索策略保留在 L2 Feature，外部向量库和知识库能力通过 L3 outbound port 提供。
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
    private static final String LOG_MSG_PROCESSOR_FAILED = "检索后处理器 {} 执行失败，跳过该处理器";

    private final ExtensionRegistry extensionRegistry;
    private final Executor retrievalExecutor;
    private final FeatureActivationContext activationContext;
    private final MetadataSchemaRegistryPort schemaRegistryPort;
    private final MetadataFilterCompiler metadataFilterCompiler;
    private final KernelRagTraceRecorder traceRecorder;
    private final ObservationPort observationPort;

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
        this.extensionRegistry = Objects.requireNonNull(extensionRegistry, "extensionRegistry must not be null");
        this.retrievalExecutor = Objects.requireNonNull(retrievalExecutor, "retrievalExecutor must not be null");
        this.activationContext = Objects.requireNonNullElse(activationContext, FeatureActivationContext.empty());
        this.schemaRegistryPort = Objects.requireNonNullElseGet(schemaRegistryPort, MetadataSchemaRegistryPort::empty);
        this.metadataFilterCompiler = Objects.requireNonNullElseGet(metadataFilterCompiler,
                DefaultMetadataFilterCompiler::new);
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
        this.observationPort = observationPort;
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
     * <p>
     * 动态 metadata 过滤会先按 Schema 编译成后端无关 AST，再传递给检索通道和兜底后处理器。
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
            recordRetrievalEmpty(context, "channel", "no_enabled_channels", 0, 0);
            return List.of();
        }
        List<RetrievedChunk> chunks = executePostProcessors(channelResults, context);
        if (chunks.isEmpty()) {
            int candidateCount = channelCandidateCount(channelResults);
            recordRetrievalEmpty(context,
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
                .map(channel -> CompletableFuture.supplyAsync(() -> executeSingleChannel(channel, context),
                        retrievalExecutor))
                .toList();

        return futures.stream()
                .map(CompletableFuture::join)
                .filter(Objects::nonNull)
                .toList();
    }

    private SearchChannelResult executeSingleChannel(SearchChannelFeature channel, SearchContext context) {
        TraceNodeScope nodeScope = traceRecorder.startNode(traceRunScope(context), channelTraceCommand(channel));
        long startedAt = System.currentTimeMillis();
        try {
            SearchChannelResult result = channel.search(context);
            recordChannelCompleted(channel, context, result, null, System.currentTimeMillis() - startedAt);
            traceRecorder.finishNode(nodeScope);
            return result;
        } catch (Exception ex) {
            long latencyMs = System.currentTimeMillis() - startedAt;
            SearchChannelResult result = emptyResult(channel, latencyMs);
            recordChannelCompleted(channel, context, result, ex, latencyMs);
            traceRecorder.finishNode(nodeScope, ex);
            LOG.error(LOG_MSG_CHANNEL_FAILED, channel.name(), ex);
            return result;
        }
    }

    private SearchChannelResult emptyResult(SearchChannelFeature channel, long latencyMs) {
        return SearchChannelResult.builder()
                .channelType(channel.channelType())
                .channelName(channel.name())
                .chunks(List.of())
                .latencyMs(latencyMs)
                .build();
    }

    private void recordChannelCompleted(SearchChannelFeature channel,
                                        SearchContext context,
                                        SearchChannelResult result,
                                        Exception ex,
                                        long elapsedMs) {
        if (observationPort == null) {
            return;
        }
        try {
            Map<String, String> attributes = new java.util.LinkedHashMap<>();
            attributes.put("tenantId", tenantId(context));
            attributes.put("knowledgeBaseId", knowledgeBaseId(context));
            attributes.put("channelName", Objects.requireNonNullElse(channel.name(), ""));
            attributes.put("channelType", channel.channelType() == null ? "" : channel.channelType().name());
            attributes.put("status", ex == null ? "success" : "failure");
            attributes.put("success", Boolean.toString(ex == null));
            attributes.put("hitCount", Integer.toString(safeChunks(result).size()));
            attributes.put("latencyMs", Long.toString(elapsedMs));
            if (ex != null) {
                attributes.put("exception", ex.getClass().getSimpleName());
            }
            // 通道失败会按空结果降级；观测事件保留失败证据，便于识别 ES/关键词通道不可用。
            observationPort.recordEvent(new ObservationEvent(EVENT_CHANNEL_COMPLETED, null, attributes));
        } catch (RuntimeException ignored) {
            // 观测失败不能影响检索通道降级和后处理链。
        }
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
            recordMetadataGuardCompleted(processor, context, inputCount, chunkCount(processed),
                    System.currentTimeMillis() - startedAt, null);
            traceRecorder.finishNode(nodeScope);
            return processed;
        } catch (Exception ex) {
            recordMetadataGuardCompleted(processor, context, inputCount, inputCount,
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

    private String tenantId(SearchContext context) {
        RetrievalFilter filter = context == null ? null : context.getFilter();
        if (filter != null && filter.system() != null && !filter.system().tenantId().isBlank()) {
            return filter.system().tenantId();
        }
        return Objects.requireNonNullElse(activationContext.tenantId(), "");
    }

    private String knowledgeBaseId(SearchContext context) {
        RetrievalFilter filter = context == null ? null : context.getFilter();
        if (filter == null || filter.system() == null || filter.system().knowledgeBaseIds().isEmpty()) {
            return "";
        }
        return Objects.requireNonNullElse(filter.system().knowledgeBaseIds().get(0), "");
    }

    private TraceRunScope traceRunScope(SearchContext context) {
        TraceRunScope traceRunScope = context == null ? null : context.getTraceRunScope();
        return traceRunScope == null ? TraceRunScope.disabled() : traceRunScope;
    }

    private TraceNodeStartCommand channelTraceCommand(SearchChannelFeature channel) {
        // 检索 Trace 只记录编排节点，不改变通道失败时返回空结果的降级语义。
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
            // 只有通过 Schema 编译后的表达式才能进入向量库或关键词后端，避免原始 Map 直通外部查询。
            CompiledMetadataFilter compiledFilter = metadataFilterCompiler.compile(filter, schema);
            recordMetadataFilterUsage(tenantId, knowledgeBaseId, schema, compiledFilter);
            return compiledFilter;
        } catch (RuntimeException ex) {
            recordMetadataFilterRejected(tenantId, knowledgeBaseId, schema, filter, ex);
            throw ex;
        }
    }

    private void recordMetadataFilterRejected(String tenantId,
                                              String knowledgeBaseId,
                                              MetadataSchema schema,
                                              RetrievalFilter filter,
                                              RuntimeException ex) {
        if (observationPort == null || filter == null || filter.metadataConditions().isEmpty()) {
            return;
        }
        try {
            List<String> fieldKeys = filter.metadataConditions().stream()
                    .map(condition -> Objects.requireNonNullElse(condition.fieldKey(), ""))
                    .filter(fieldKey -> !fieldKey.isBlank())
                    .distinct()
                    .toList();
            Map<String, String> attributes = new java.util.LinkedHashMap<>();
            attributes.put("tenantId", Objects.requireNonNullElse(tenantId, ""));
            attributes.put("knowledgeBaseId", Objects.requireNonNullElse(knowledgeBaseId, ""));
            attributes.put("schemaVersion", Integer.toString(schema == null ? 0 : schema.schemaVersion()));
            attributes.put("fieldKeys", String.join(",", fieldKeys));
            attributes.put("fieldCount", Integer.toString(fieldKeys.size()));
            attributes.put("success", "false");
            attributes.put("reason", metadataFilterRejectReason(ex));
            attributes.put("exception", ex.getClass().getSimpleName());
            // 只记录字段名和拒绝原因，不记录过滤值，避免把业务查询条件写入观测事件。
            observationPort.recordEvent(new ObservationEvent(EVENT_METADATA_FILTER_REJECTED, null, attributes));
        } catch (RuntimeException ignored) {
            // 观测失败不能改变 Filter Compiler 的拒绝语义。
        }
    }

    private String metadataFilterRejectReason(RuntimeException ex) {
        String message = Objects.requireNonNullElse(ex.getMessage(), "");
        if (message.contains("not registered")) {
            return "UNREGISTERED_FIELD";
        }
        if (message.contains("not filterable")) {
            return "NOT_FILTERABLE";
        }
        if (message.contains("not allowed")) {
            return "OPERATOR_NOT_ALLOWED";
        }
        if (message.contains("exceeds limit")) {
            return "CONDITION_LIMIT_EXCEEDED";
        }
        return "INVALID_FILTER";
    }

    private void recordMetadataFilterUsage(String tenantId,
                                           String knowledgeBaseId,
                                           MetadataSchema schema,
                                           CompiledMetadataFilter compiledFilter) {
        if (observationPort == null || compiledFilter == null
                || compiledFilter.sourceFilter().metadataConditions().isEmpty()) {
            return;
        }
        try {
            List<String> fieldKeys = compiledFilter.sourceFilter().metadataConditions().stream()
                    .map(condition -> Objects.requireNonNullElse(condition.fieldKey(), ""))
                    .filter(fieldKey -> !fieldKey.isBlank())
                    .distinct()
                    .toList();
            List<String> guardOnlyFieldKeys = compiledFilter.guardOnlyConditions().stream()
                    .map(condition -> Objects.requireNonNullElse(condition.fieldKey(), ""))
                    .filter(fieldKey -> !fieldKey.isBlank())
                    .distinct()
                    .toList();
            // 该事件为后续 Schema 使用情况报表提供原始证据，不影响检索下推和兜底过滤语义。
            observationPort.recordEvent(new ObservationEvent(EVENT_METADATA_FILTER_COMPILED, null, Map.of(
                    "tenantId", Objects.requireNonNullElse(tenantId, ""),
                    "knowledgeBaseId", Objects.requireNonNullElse(knowledgeBaseId, ""),
                    "schemaVersion", Integer.toString(schema == null ? 0 : schema.schemaVersion()),
                    "fieldKeys", String.join(",", fieldKeys),
                    "fieldCount", Integer.toString(fieldKeys.size()),
                    "guardOnlyFieldKeys", String.join(",", guardOnlyFieldKeys),
                    "guardOnlyCount", Integer.toString(guardOnlyFieldKeys.size()),
                    "warningCount", Integer.toString(compiledFilter.warnings().size()))));
        } catch (RuntimeException ignored) {
            // 观测失败不能影响检索主链路，也不能绕过已编译的 Schema/Filter Compiler 结果。
        }
    }

    private void recordMetadataGuardCompleted(SearchResultPostProcessorFeature processor,
                                              SearchContext context,
                                              int inputCount,
                                              int outputCount,
                                              long elapsedMs,
                                              Exception ex) {
        if (observationPort == null
                || !PROCESSOR_METADATA_GUARD.equals(Objects.requireNonNullElse(processor.name(), ""))) {
            return;
        }
        try {
            int filteredCount = Math.max(inputCount - outputCount, 0);
            Map<String, String> attributes = new java.util.LinkedHashMap<>();
            attributes.put("tenantId", tenantId(context));
            attributes.put("knowledgeBaseId", knowledgeBaseId(context));
            attributes.put("inputCount", Integer.toString(inputCount));
            attributes.put("outputCount", Integer.toString(outputCount));
            attributes.put("filteredCount", Integer.toString(filteredCount));
            attributes.put("reason", filteredCount > 0 ? "metadata_or_acl_filtered" : "none");
            attributes.put("durationMs", Long.toString(elapsedMs));
            attributes.put("success", Boolean.toString(ex == null));
            if (ex != null) {
                attributes.put("exception", ex.getClass().getSimpleName());
            }
            // 该事件只记录兜底过滤效果，不能改变后处理器异常时的原有降级行为。
            observationPort.recordEvent(new ObservationEvent(EVENT_METADATA_GUARD, null, attributes));
        } catch (RuntimeException ignored) {
            // 观测失败不能影响权限和动态 metadata 的兜底过滤链路。
        }
    }

    private void recordRetrievalEmpty(SearchContext context,
                                      String stage,
                                      String reason,
                                      int channelCount,
                                      int candidateCount) {
        if (observationPort == null) {
            return;
        }
        try {
            Map<String, String> attributes = new java.util.LinkedHashMap<>();
            attributes.put("tenantId", tenantId(context));
            attributes.put("knowledgeBaseId", knowledgeBaseId(context));
            attributes.put("stage", Objects.requireNonNullElse(stage, ""));
            attributes.put("reason", Objects.requireNonNullElse(reason, ""));
            attributes.put("channelCount", Integer.toString(channelCount));
            attributes.put("candidateCount", Integer.toString(candidateCount));
            attributes.put("topK", Integer.toString(context == null ? 0 : context.getTopK()));
            attributes.put("filterApplied", Boolean.toString(context != null && context.getFilter() != null));
            // 空召回事件只提供质量诊断证据，不触发任何检索重试或兜底改写。
            observationPort.recordEvent(new ObservationEvent(EVENT_RETRIEVAL_EMPTY, null, attributes));
        } catch (RuntimeException ignored) {
            // 观测失败不能改变空结果返回语义。
        }
    }
}

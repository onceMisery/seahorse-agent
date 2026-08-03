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

import com.miracle.ai.seahorse.agent.kernel.application.mcp.KernelMcpOrchestrator;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.QueryOptimizationResult;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentNode;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScoreFilters;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.KbResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.SearchContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.feature.mcp.McpToolExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.plugin.ExtensionRegistry;
import com.miracle.ai.seahorse.agent.kernel.plugin.FeatureActivationContext;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mcp.McpToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.retrieval.RetrievalContextFormatPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.stream.IntStream;

import static com.miracle.ai.seahorse.agent.kernel.domain.retrieval.KernelRagDefaults.DEFAULT_TOP_K;
import static com.miracle.ai.seahorse.agent.kernel.domain.retrieval.KernelRagDefaults.MULTI_CHANNEL_KEY;

/**
 * L1 检索内核门面。
 * <p>
 * 该门面承接旧 {@code RetrievalEngine} 的主干语义：按子问题并发构建上下文、执行 KB 多通道检索、
 * 执行 MCP 工具并合并为统一 {@link RetrievalContext}。具体检索通道、MCP 工具、参数抽取和格式化均经由端口或 Feature 接入。
 */
public class KernelRetrievalEngine implements RetrievalContextPort {

    private static final Logger LOG = LoggerFactory.getLogger(KernelRetrievalEngine.class);
    private static final String FAILURE_CODE_SUBQUESTION_FAILED = "SUBQUESTION_FAILED";

    private final KernelMultiChannelRetrievalEngine multiChannelRetrievalEngine;
    private final KernelMcpOrchestrator mcpOrchestrator;
    private final RetrievalContextFormatPort formatPort;
    private final Executor ragContextExecutor;

    public KernelRetrievalEngine(ExtensionRegistry extensionRegistry,
                                 Executor retrievalExecutor,
                                 FeatureActivationContext activationContext) {
        this(new KernelMultiChannelRetrievalEngine(extensionRegistry, retrievalExecutor, activationContext));
    }

    public KernelRetrievalEngine(KernelMultiChannelRetrievalEngine multiChannelRetrievalEngine) {
        this(new KernelRetrievalEnginePorts(multiChannelRetrievalEngine,
                new KernelMcpOrchestrator(McpToolRegistryPort.empty()),
                RetrievalContextFormatPort.noop(), Runnable::run));
    }

    public KernelRetrievalEngine(KernelRetrievalEnginePorts ports) {
        Objects.requireNonNull(ports, "检索内核端口组不能为空");
        this.multiChannelRetrievalEngine = Objects.requireNonNull(ports.multiChannelRetrievalEngine(),
                "多通道检索内核不能为空");
        this.mcpOrchestrator = Objects.requireNonNull(ports.mcpOrchestrator(), "MCP 编排器不能为空");
        this.formatPort = Objects.requireNonNullElse(ports.formatPort(), RetrievalContextFormatPort.noop());
        this.ragContextExecutor = Objects.requireNonNull(ports.ragContextExecutor(), "RAG 上下文线程池不能为空");
    }

    /**
     * 执行完整检索，合并 KB 和 MCP 上下文。
     *
     * @param subIntents 子问题意图列表
     * @param topK       期望返回数量
     * @return 检索上下文
     */
    @Override
    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK) {
        return retrieve(subIntents, topK, null);
    }

    @Override
    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents, int topK, TraceRunScope traceRunScope) {
        return retrieveInternal(subIntents, topK, null, traceRunScope, null);
    }

    @Override
    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents,
                                     int topK,
                                     TraceRunScope traceRunScope,
                                     QueryOptimizationResult queryOptimizationResult) {
        return retrieveInternal(subIntents, topK, null, traceRunScope, queryOptimizationResult);
    }

    @Override
    public RetrievalContext retrieve(List<SubQuestionIntent> subIntents,
                                     int topK,
                                     RetrievalFilter filter,
                                     TraceRunScope traceRunScope,
                                     QueryOptimizationResult queryOptimizationResult) {
        return retrieveInternal(subIntents, topK, filter, traceRunScope, queryOptimizationResult);
    }

    private RetrievalContext retrieveInternal(List<SubQuestionIntent> subIntents,
                                              int topK,
                                              RetrievalFilter filter,
                                              TraceRunScope traceRunScope,
                                              QueryOptimizationResult queryOptimizationResult) {
        List<SubQuestionIntent> safeIntents = Objects.requireNonNullElse(subIntents, List.of());
        if (safeIntents.isEmpty()) {
            return RetrievalContext.builder().intentChunks(Map.of()).build();
        }

        int finalTopK = topK > 0 ? topK : DEFAULT_TOP_K;
        RetrievalOptions queryExpansionOptions = queryExpansionOptions(finalTopK, queryOptimizationResult);
        List<CompletableFuture<SubQuestionContext>> tasks = IntStream.range(0, safeIntents.size())
                .mapToObj(index -> CompletableFuture.supplyAsync(
                        () -> buildSubQuestionContext(index, safeIntents.get(index),
                                resolveSubQuestionTopK(safeIntents.get(index), finalTopK),
                                filter, traceRunScope, queryExpansionOptions),
                        ragContextExecutor).exceptionally(ex -> failedSubQuestionContext(index, ex)))
                .toList();
        List<SubQuestionContext> contexts = tasks.stream()
                .map(CompletableFuture::join)
                .toList();
        return mergeContexts(contexts);
    }

    /**
     * 执行知识库多通道检索。
     *
     * @param subIntents 子问题意图列表
     * @param topK       期望返回数量
     * @return 检索结果 Chunk 列表
     */
    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents, int topK) {
        return multiChannelRetrievalEngine.retrieveKnowledgeChannels(subIntents, topK);
    }

    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents,
                                                          int topK,
                                                          TraceRunScope traceRunScope) {
        return multiChannelRetrievalEngine.retrieveKnowledgeChannels(subIntents, topK, traceRunScope);
    }

    /**
     * 执行带 Schema 治理过滤条件的知识库检索。
     * <p>
     * 该入口面向后续 API 层传入强类型过滤条件，内部会由多通道引擎完成 Schema 编译。
     */
    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents,
                                                          int topK,
                                                          RetrievalFilter filter,
                                                          RetrievalOptions options) {
        return multiChannelRetrievalEngine.retrieveKnowledgeChannels(subIntents, topK, filter, options);
    }

    public List<RetrievedChunk> retrieveKnowledgeChannels(List<SubQuestionIntent> subIntents,
                                                          int topK,
                                                          RetrievalFilter filter,
                                                          RetrievalOptions options,
                                                          TraceRunScope traceRunScope) {
        return multiChannelRetrievalEngine.retrieveKnowledgeChannels(subIntents, topK, filter, options, traceRunScope);
    }

    private SubQuestionContext buildSubQuestionContext(int subQuestionIndex,
                                                       SubQuestionIntent intent,
                                                       int topK,
                                                       RetrievalFilter filter,
                                                       TraceRunScope traceRunScope,
                                                       RetrievalOptions queryExpansionOptions) {
        SubQuestionIntent safeIntent = safeIntent(intent);
        List<IntentScore> scores = safeScores(safeIntent);
        List<IntentScore> kbIntents = IntentScoreFilters.kb(scores);
        List<IntentScore> mcpIntents = IntentScoreFilters.mcp(scores);
        KbResult kbResult = retrieveAndRerank(safeIntent, kbIntents, topK, filter, traceRunScope,
                queryExpansionOptions);
        String mcpContext = mcpIntents.isEmpty() ? "" : executeMcpAndMerge(safeIntent.subQuestion(), mcpIntents);
        return new SubQuestionContext(safeIntent.subQuestion(), kbResult.groupedContext(), mcpContext,
                kbResult.intentChunks(), prefixEvidence(subQuestionIndex, kbResult.failureEvidence()), false);
    }

    private SubQuestionContext failedSubQuestionContext(int subQuestionIndex, Throwable error) {
        Throwable cause = unwrap(error);
        LOG.error("子问题上下文构建失败，标记为部分检索", cause);
        return new SubQuestionContext("", "", "", Map.of(),
                Map.of("subquestion:" + subQuestionIndex, FAILURE_CODE_SUBQUESTION_FAILED), true);
    }

    private RetrievalContext mergeContexts(List<SubQuestionContext> contexts) {
        if (contexts.stream().allMatch(SubQuestionContext::failed)) {
            throw new IllegalStateException("Retrieval failed for all sub-questions");
        }
        StringBuilder kbBuilder = new StringBuilder();
        StringBuilder mcpBuilder = new StringBuilder();
        Map<String, List<RetrievedChunk>> mergedIntentChunks = new HashMap<>();
        Map<String, String> failureEvidence = new LinkedHashMap<>();
        for (SubQuestionContext context : contexts) {
            failureEvidence.putAll(context.failureEvidence());
            if (context.failed()) {
                continue;
            }
            appendIfNotBlank(kbBuilder, context.question(), context.kbContext());
            appendIfNotBlank(mcpBuilder, context.question(), context.mcpContext());
            mergedIntentChunks.putAll(Objects.requireNonNullElse(context.intentChunks(), Map.of()));
        }
        return RetrievalContext.builder()
                .mcpContext(mcpBuilder.toString().trim())
                .kbContext(kbBuilder.toString().trim())
                .intentChunks(mergedIntentChunks)
                .status(failureEvidence.isEmpty() ? RetrievalStatus.COMPLETE : RetrievalStatus.PARTIAL)
                .failureEvidence(Map.copyOf(failureEvidence))
                .build();
    }

    private Map<String, String> prefixEvidence(int subQuestionIndex, Map<String, String> evidence) {
        if (evidence == null || evidence.isEmpty()) {
            return Map.of();
        }
        Map<String, String> prefixed = new LinkedHashMap<>();
        evidence.forEach((key, value) -> prefixed.put("subquestion:" + subQuestionIndex + "/" + key, value));
        return Map.copyOf(prefixed);
    }

    private Throwable unwrap(Throwable error) {
        if (error instanceof CompletionException completionException && completionException.getCause() != null) {
            return completionException.getCause();
        }
        return error;
    }

    private void appendIfNotBlank(StringBuilder builder, String question, String context) {
        if (context == null || context.isBlank()) {
            return;
        }
        builder.append("---\n")
                .append("**子问题**：").append(Objects.requireNonNullElse(question, "")).append("\n\n")
                .append("**相关文档**：\n")
                .append(context)
                .append("\n\n");
    }

    private int resolveSubQuestionTopK(SubQuestionIntent intent, int fallbackTopK) {
        return IntentScoreFilters.kb(safeScores(intent)).stream()
                .map(IntentScore::getNode)
                .filter(Objects::nonNull)
                .map(IntentNode::getTopK)
                .filter(Objects::nonNull)
                .filter(candidateTopK -> candidateTopK > 0)
                .max(Integer::compareTo)
                .orElse(fallbackTopK);
    }

    private KbResult retrieveAndRerank(SubQuestionIntent intent,
                                       List<IntentScore> kbIntents,
                                       int topK,
                                       RetrievalFilter filter,
                                       TraceRunScope traceRunScope,
                                       RetrievalOptions queryExpansionOptions) {
        KernelMultiChannelRetrievalEngine.RetrievalChannelBatch batch = queryExpansionOptions == null
                ? multiChannelRetrievalEngine.retrieveKnowledgeChannelsWithEvidence(
                        List.of(intent), topK, filter, null, traceRunScope)
                : multiChannelRetrievalEngine.retrieveKnowledgeChannelsWithEvidence(
                        List.of(intent), topK, filter, queryExpansionOptions, traceRunScope);
        List<RetrievedChunk> chunks = batch.chunks();
        if (chunks == null || chunks.isEmpty()) {
            return new KbResult("", Map.of(), batch.failureEvidence());
        }
        Map<String, List<RetrievedChunk>> intentChunks = buildIntentChunks(kbIntents, chunks);
        String groupedContext = formatPort.formatKbContext(kbIntents, intentChunks, topK);
        return new KbResult(groupedContext, intentChunks, batch.failureEvidence());
    }

    private Map<String, List<RetrievedChunk>> buildIntentChunks(List<IntentScore> kbIntents,
                                                                List<RetrievedChunk> chunks) {
        Map<String, List<RetrievedChunk>> intentChunks = new HashMap<>();
        if (kbIntents == null || kbIntents.isEmpty()) {
            intentChunks.put(MULTI_CHANNEL_KEY, chunks);
            return intentChunks;
        }
        for (IntentScore score : kbIntents) {
            IntentNode node = score == null ? null : score.getNode();
            String nodeId = node == null ? "" : Objects.requireNonNullElse(node.getId(), "");
            if (!nodeId.isBlank()) {
                intentChunks.put(nodeId, chunks);
            }
        }
        if (intentChunks.isEmpty()) {
            intentChunks.put(MULTI_CHANNEL_KEY, chunks);
        }
        return intentChunks;
    }

    private String executeMcpAndMerge(String question, List<IntentScore> mcpIntents) {
        List<McpToolExecutionResult> results = mcpOrchestrator.executeTools(question, mcpIntents);
        boolean hasSuccess = results.stream().anyMatch(McpToolExecutionResult::success);
        if (!hasSuccess) {
            return "";
        }
        return formatPort.formatMcpContext(results, mcpIntents);
    }

    private SubQuestionIntent safeIntent(SubQuestionIntent intent) {
        if (intent == null) {
            return new SubQuestionIntent("", List.of());
        }
        return new SubQuestionIntent(Objects.requireNonNullElse(intent.subQuestion(), ""),
                safeScores(intent));
    }

    private List<IntentScore> safeScores(SubQuestionIntent intent) {
        if (intent == null) {
            return List.of();
        }
        return Objects.requireNonNullElse(intent.intentScores(), List.of());
    }

    private RetrievalOptions queryExpansionOptions(int topK, QueryOptimizationResult queryOptimizationResult) {
        List<String> expandedTerms = normalizedDistinct(queryOptimizationResult == null
                ? List.of()
                : queryOptimizationResult.expandedTerms());
        if (expandedTerms.isEmpty()) {
            return null;
        }
        Map<String, Object> channelSettings = new LinkedHashMap<>();
        channelSettings.put(SearchContext.METADATA_QUERY_EXPANDED_TERMS, expandedTerms);
        List<String> appliedRules = normalizedDistinct(queryOptimizationResult.appliedRules());
        if (!appliedRules.isEmpty()) {
            channelSettings.put(SearchContext.METADATA_QUERY_APPLIED_RULES, appliedRules);
        }
        return RetrievalOptions.defaults(topK)
                .withEnableKeyword(true)
                .withAdditionalChannelSettings(channelSettings);
    }

    private List<String> normalizedDistinct(List<String> values) {
        LinkedHashSet<String> result = new LinkedHashSet<>();
        for (String value : Objects.requireNonNullElse(values, List.<String>of())) {
            if (value != null && !value.isBlank()) {
                result.add(value.trim());
            }
        }
        return List.copyOf(result);
    }

    public record KernelRetrievalEnginePorts(KernelMultiChannelRetrievalEngine multiChannelRetrievalEngine,
                                             KernelMcpOrchestrator mcpOrchestrator,
                                             RetrievalContextFormatPort formatPort,
                                             Executor ragContextExecutor) {
    }

    private record SubQuestionContext(String question,
                                      String kbContext,
                                      String mcpContext,
                                      Map<String, List<RetrievedChunk>> intentChunks,
                                      Map<String, String> failureEvidence,
                                      boolean failed) {
    }
}

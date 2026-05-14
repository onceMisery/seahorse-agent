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
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentNode;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScore;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentScoreFilters;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.KbResult;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalFilter;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

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
        List<SubQuestionIntent> safeIntents = Objects.requireNonNullElse(subIntents, List.of());
        if (safeIntents.isEmpty()) {
            return RetrievalContext.builder().intentChunks(Map.of()).build();
        }

        int finalTopK = topK > 0 ? topK : DEFAULT_TOP_K;
        List<CompletableFuture<SubQuestionContext>> tasks = safeIntents.stream()
                .map(intent -> CompletableFuture.supplyAsync(
                        () -> buildSubQuestionContext(intent, resolveSubQuestionTopK(intent, finalTopK), traceRunScope),
                        ragContextExecutor))
                .toList();
        List<SubQuestionContext> contexts = tasks.stream()
                .map(this::joinContext)
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

    private SubQuestionContext buildSubQuestionContext(SubQuestionIntent intent, int topK, TraceRunScope traceRunScope) {
        SubQuestionIntent safeIntent = safeIntent(intent);
        List<IntentScore> scores = safeScores(safeIntent);
        List<IntentScore> kbIntents = IntentScoreFilters.kb(scores);
        List<IntentScore> mcpIntents = IntentScoreFilters.mcp(scores);
        KbResult kbResult = retrieveAndRerank(safeIntent, kbIntents, topK, traceRunScope);
        String mcpContext = mcpIntents.isEmpty() ? "" : executeMcpAndMerge(safeIntent.subQuestion(), mcpIntents);
        return new SubQuestionContext(safeIntent.subQuestion(), kbResult.groupedContext(), mcpContext,
                kbResult.intentChunks());
    }

    private SubQuestionContext joinContext(CompletableFuture<SubQuestionContext> future) {
        try {
            return future.join();
        } catch (Exception ex) {
            LOG.error("子问题上下文构建失败，降级为空上下文", ex);
            return new SubQuestionContext("", "", "", Map.of());
        }
    }

    private RetrievalContext mergeContexts(List<SubQuestionContext> contexts) {
        StringBuilder kbBuilder = new StringBuilder();
        StringBuilder mcpBuilder = new StringBuilder();
        Map<String, List<RetrievedChunk>> mergedIntentChunks = new HashMap<>();
        for (SubQuestionContext context : contexts) {
            appendIfNotBlank(kbBuilder, context.question(), context.kbContext());
            appendIfNotBlank(mcpBuilder, context.question(), context.mcpContext());
            mergedIntentChunks.putAll(Objects.requireNonNullElse(context.intentChunks(), Map.of()));
        }
        return RetrievalContext.builder()
                .mcpContext(mcpBuilder.toString().trim())
                .kbContext(kbBuilder.toString().trim())
                .intentChunks(mergedIntentChunks)
                .build();
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
                                       TraceRunScope traceRunScope) {
        List<RetrievedChunk> chunks = retrieveKnowledgeChannels(List.of(intent), topK, traceRunScope);
        if (chunks == null || chunks.isEmpty()) {
            return KbResult.empty();
        }
        Map<String, List<RetrievedChunk>> intentChunks = buildIntentChunks(kbIntents, chunks);
        String groupedContext = formatPort.formatKbContext(kbIntents, intentChunks, topK);
        return new KbResult(groupedContext, intentChunks);
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

    public record KernelRetrievalEnginePorts(KernelMultiChannelRetrievalEngine multiChannelRetrievalEngine,
                                             KernelMcpOrchestrator mcpOrchestrator,
                                             RetrievalContextFormatPort formatPort,
                                             Executor ragContextExecutor) {
    }

    private record SubQuestionContext(String question,
                                      String kbContext,
                                      String mcpContext,
                                      Map<String, List<RetrievedChunk>> intentChunks) {
    }
}

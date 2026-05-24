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

package com.miracle.ai.seahorse.agent.kernel.application.chat;

import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ContextPackBuilderInboundPort;

import java.util.Objects;
import java.util.Optional;

/**
 * L1 问答主链路内核编排器。
 * <p>
 * 该类只保留固定阶段顺序、trace 节点和短路判断；具体阶段行为委托给 preparation/response 协作者。
 */
public class KernelChatPipeline {

    /**
     * 空检索（无知识库或 0 命中）兜底策略。
     * <p>
     * Agent 平台默认使用 {@link #FALLBACK_GENERIC} 走通用 LLM 对话，避免 RAG 命中为空时退化成"无法回答"。
     * 严格 RAG 场景可切回 {@link #STATIC_MESSAGE}，沿用历史固定文案。
     */
    public enum EmptyRetrievalStrategy {
        /** 复用 system prompt 走通用 LLM 对话（默认）。 */
        FALLBACK_GENERIC,
        /** 直接返回固定文案，不调用模型。 */
        STATIC_MESSAGE
    }

    private static final String EMPTY_RETRIEVAL_MESSAGE = "未检索到与问题相关的文档内容。";
    private static final String TRACE_TYPE_CHAT_STAGE = "CHAT_STAGE";
    private static final EmptyRetrievalStrategy DEFAULT_EMPTY_RETRIEVAL_STRATEGY = EmptyRetrievalStrategy.FALLBACK_GENERIC;

    private final KernelRagTraceRecorder traceRecorder;
    private final KernelChatPreparationSupport preparationSupport;
    private final KernelChatResponseSupport responseSupport;
    private final EmptyRetrievalStrategy emptyRetrievalStrategy;
    private final ContextPackRuntimeAssembler contextPackAssembler;

    public KernelChatPipeline(ChatPreparationPorts preparationPorts, ChatResponsePorts responsePorts) {
        this(preparationPorts, responsePorts, KernelRagTraceRecorder.noop(), DEFAULT_EMPTY_RETRIEVAL_STRATEGY);
    }

    public KernelChatPipeline(ChatPreparationPorts preparationPorts,
                              ChatResponsePorts responsePorts,
                              KernelRagTraceRecorder traceRecorder) {
        this(preparationPorts, responsePorts, traceRecorder, DEFAULT_EMPTY_RETRIEVAL_STRATEGY);
    }

    public KernelChatPipeline(ChatPreparationPorts preparationPorts,
                              ChatResponsePorts responsePorts,
                              KernelRagTraceRecorder traceRecorder,
                              EmptyRetrievalStrategy emptyRetrievalStrategy) {
        this(preparationPorts, responsePorts, traceRecorder, emptyRetrievalStrategy, Optional.empty());
    }

    public KernelChatPipeline(ChatPreparationPorts preparationPorts,
                              ChatResponsePorts responsePorts,
                              KernelRagTraceRecorder traceRecorder,
                              EmptyRetrievalStrategy emptyRetrievalStrategy,
                              Optional<ContextPackBuilderInboundPort> contextPackBuilder) {
        ChatPreparationPorts safePreparationPorts = Objects.requireNonNull(preparationPorts,
                "preparationPorts must not be null");
        ChatResponsePorts safeResponsePorts = Objects.requireNonNull(responsePorts, "responsePorts must not be null");
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder must not be null");
        this.preparationSupport = new KernelChatPreparationSupport(safePreparationPorts);
        this.responseSupport = new KernelChatResponseSupport(safePreparationPorts, safeResponsePorts);
        this.emptyRetrievalStrategy = Objects.requireNonNullElse(emptyRetrievalStrategy, DEFAULT_EMPTY_RETRIEVAL_STRATEGY);
        this.contextPackAssembler = new ContextPackRuntimeAssembler(contextPackBuilder);
    }

    /**
     * 执行流式问答管道。
     *
     * @param context 流式问答上下文
     */
    public void execute(StreamChatContext context) {
        StreamChatContext safeContext = Objects.requireNonNull(context, "流式问答上下文不能为空");
        preparationSupport.installMemoryCapture(safeContext);
        TraceRunScope traceRunScope = safeContext.getTraceRunScope();
        traceRecorder.recordNode(traceRunScope, stage("load-memory", "loadMemory"),
                () -> preparationSupport.loadMemory(safeContext));
        traceRecorder.recordNode(traceRunScope, stage("activate-memory", "activateMemory"),
                () -> preparationSupport.activateMemory(safeContext));
        traceRecorder.recordNode(traceRunScope, stage("optimize-query", "optimizeQuery"),
                () -> preparationSupport.optimizeQuery(safeContext));
        traceRecorder.recordNode(traceRunScope, stage("query-rewrite", "rewriteQuery"),
                () -> preparationSupport.rewriteQuery(safeContext));
        traceRecorder.recordNode(traceRunScope, stage("intent-resolve", "resolveIntents"),
                () -> preparationSupport.resolveIntents(safeContext));

        if (traceRecorder.recordNode(traceRunScope, stage("guidance", "handleGuidance"),
                () -> preparationSupport.handleGuidance(safeContext))) {
            return;
        }
        if (handleSystemOnly(safeContext)) {
            return;
        }

        RetrievalContext retrievalContext = traceRecorder.recordNode(traceRunScope, stage("retrieval", "retrieve"),
                () -> preparationSupport.retrieve(safeContext));
        ContextPack contextPack = contextPackAssembler.assembleForRag(
                safeContext,
                retrievalContext,
                preparationSupport.requireRewriteResult(safeContext));
        if (contextPack != null) {
            safeContext.setContextPack(contextPack);
        }
        if (handleEmptyRetrieval(safeContext, retrievalContext)) {
            return;
        }

        traceRecorder.recordNode(traceRunScope, stage("stream-response", "streamRagResponse"),
                () -> streamRagResponse(safeContext, retrievalContext));
    }

    private boolean handleSystemOnly(StreamChatContext context) {
        return responseSupport.handleSystemOnly(
                context,
                preparationSupport.safeSubIntents(context),
                preparationSupport.requireRewriteResult(context));
    }

    private boolean handleEmptyRetrieval(StreamChatContext context, RetrievalContext retrievalContext) {
        return responseSupport.handleEmptyRetrieval(context, retrievalContext, emptyRetrievalStrategy,
                EMPTY_RETRIEVAL_MESSAGE);
    }

    private void streamRagResponse(StreamChatContext context, RetrievalContext retrievalContext) {
        responseSupport.streamRagResponse(
                context,
                retrievalContext,
                preparationSupport.requireRewriteResult(context),
                preparationSupport.safeSubIntents(context),
                preparationSupport.safeHistory(context),
                context.getMemoryContext());
    }

    private TraceNodeStartCommand stage(String nodeName, String methodName) {
        return TraceNodeStartCommand.pipelineStage(nodeName, TRACE_TYPE_CHAT_STAGE, methodName);
    }
}

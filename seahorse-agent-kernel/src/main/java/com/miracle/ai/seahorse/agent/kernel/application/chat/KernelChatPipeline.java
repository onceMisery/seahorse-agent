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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceNodeStartCommand;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;

import java.util.Objects;

/**
 * L1 问答主链路内核编排器。
 * <p>
 * 该类只保留固定阶段顺序、trace 节点和短路判断；具体阶段行为委托给 preparation/response 协作者。
 */
public class KernelChatPipeline {

    private static final String EMPTY_RETRIEVAL_MESSAGE = "未检索到与问题相关的文档内容。";
    private static final String TRACE_TYPE_CHAT_STAGE = "CHAT_STAGE";

    private final KernelRagTraceRecorder traceRecorder;
    private final KernelChatPreparationSupport preparationSupport;
    private final KernelChatResponseSupport responseSupport;

    public KernelChatPipeline(ChatPreparationPorts preparationPorts, ChatResponsePorts responsePorts) {
        this(preparationPorts, responsePorts, KernelRagTraceRecorder.noop());
    }

    public KernelChatPipeline(ChatPreparationPorts preparationPorts,
                              ChatResponsePorts responsePorts,
                              KernelRagTraceRecorder traceRecorder) {
        ChatPreparationPorts safePreparationPorts = Objects.requireNonNull(preparationPorts, "问答前置端口集合不能为空");
        ChatResponsePorts safeResponsePorts = Objects.requireNonNull(responsePorts, "问答响应端口集合不能为空");
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder must not be null");
        this.preparationSupport = new KernelChatPreparationSupport(safePreparationPorts);
        this.responseSupport = new KernelChatResponseSupport(safePreparationPorts, safeResponsePorts);
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
        return responseSupport.handleEmptyRetrieval(context, retrievalContext, EMPTY_RETRIEVAL_MESSAGE);
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

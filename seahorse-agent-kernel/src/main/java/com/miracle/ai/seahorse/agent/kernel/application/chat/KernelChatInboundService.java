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

import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * L1 问答入站应用服务。
 */
public class KernelChatInboundService implements ChatInboundPort {

    private static final Logger LOG = LoggerFactory.getLogger(KernelChatInboundService.class);

    private static final String TRACE_NAME_STREAM_CHAT = "stream-chat";
    private static final String TRACE_ENTRY_STREAM_CHAT =
            "com.miracle.ai.seahorse.agent.kernel.application.chat.KernelChatInboundService#streamChat";

    private final KernelChatPipeline chatPipeline;
    private final StreamTaskPort streamTaskPort;
    private final Optional<KernelAgentLoop> agentLoop;
    private final KernelRagTraceRecorder traceRecorder;

    public KernelChatInboundService(KernelChatPipeline chatPipeline, StreamTaskPort streamTaskPort) {
        this(chatPipeline, streamTaskPort, KernelRagTraceRecorder.noop());
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    KernelRagTraceRecorder traceRecorder) {
        this(chatPipeline, streamTaskPort, Optional.empty(), traceRecorder);
    }

    public KernelChatInboundService(KernelChatPipeline chatPipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<KernelAgentLoop> agentLoop,
                                    KernelRagTraceRecorder traceRecorder) {
        this.chatPipeline = Objects.requireNonNull(chatPipeline, "chatPipeline must not be null");
        this.streamTaskPort = Objects.requireNonNull(streamTaskPort, "streamTaskPort must not be null");
        this.agentLoop = agentLoop == null ? Optional.empty() : agentLoop;
        this.traceRecorder = Objects.requireNonNull(traceRecorder, "traceRecorder must not be null");
    }

    @Override
    public void streamChat(StreamChatCommand command, StreamCallback callback) {
        StreamChatCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        StreamCallback safeCallback = Objects.requireNonNull(callback, "callback must not be null");
        TraceRunScope traceRunScope = traceRecorder.startRun(new TraceRunStartCommand(
                TRACE_NAME_STREAM_CHAT,
                TRACE_ENTRY_STREAM_CHAT,
                safeCommand.conversationId(),
                safeCommand.taskId(),
                safeCommand.userId()));
        try {
            if (safeCommand.chatMode() == ChatMode.AGENT) {
                if (agentLoop.isPresent()) {
                    agentLoop.get().streamExecute(buildAgentLoopRequest(safeCommand), safeCallback);
                    traceRecorder.finishRun(traceRunScope);
                    return;
                }
                LOG.warn("chatMode=AGENT but KernelAgentLoop is not configured, fallback to RAG: taskId={}, userId={}",
                        safeCommand.taskId(), safeCommand.userId());
            }
            chatPipeline.execute(buildContext(safeCommand, safeCallback, traceRunScope));
            traceRecorder.finishRun(traceRunScope);
        } catch (Exception ex) {
            traceRecorder.finishRun(traceRunScope, ex);
            safeCallback.onError(ex);
        }
    }

    @Override
    public void stopTask(String taskId) {
        streamTaskPort.cancel(taskId);
    }

    private StreamChatContext buildContext(StreamChatCommand command,
                                           StreamCallback callback,
                                           TraceRunScope traceRunScope) {
        return StreamChatContext.builder()
                .question(command.question())
                .conversationId(command.conversationId())
                .taskId(command.taskId())
                .userId(command.userId())
                .deepThinking(command.deepThinking())
                .callback(callback)
                .traceRunScope(traceRunScope)
                .build();
    }

    private AgentLoopRequest buildAgentLoopRequest(StreamChatCommand command) {
        return AgentLoopRequest.builder()
                .question(command.question())
                .history(List.of())
                .allowedToolIds(List.of())
                .samplingOptions(ChatSamplingOptions.builder()
                        .temperature(0.3D)
                        .build())
                .build();
    }
}

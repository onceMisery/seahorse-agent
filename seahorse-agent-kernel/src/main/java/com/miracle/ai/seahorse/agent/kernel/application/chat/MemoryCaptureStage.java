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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 问答完成后的轻量记忆捕获阶段。
 *
 * <p>信任边界在 {@link MemoryEnginePort#writeMemory(MemoryWriteRequest)} 及其实现层：
 * 本阶段会把原始用户问题作为候选记忆提交给记忆引擎，但不在聊天主链路判断其可信度。
 * 可信声明识别、噪声过滤和最终落库策略均由记忆引擎负责，避免主链路直接写入原始噪声。</p>
 */
final class MemoryCaptureStage {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryCaptureStage.class);

    private MemoryCaptureStage() {
    }

    static StreamCallback wrap(StreamCallback delegate,
                               MemoryEnginePort memoryEnginePort,
                               StreamChatContext context) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(memoryEnginePort, "memoryEnginePort must not be null");
        Objects.requireNonNull(context, "context must not be null");
        return new CapturingStreamCallback(delegate, memoryEnginePort, context);
    }

    static StreamCallback wrap(StreamCallback delegate,
                               MemoryIngestionWorkflowPort memoryIngestionWorkflowPort,
                               StreamChatContext context) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(memoryIngestionWorkflowPort, "memoryIngestionWorkflowPort must not be null");
        Objects.requireNonNull(context, "context must not be null");
        return new CapturingStreamCallback(delegate, memoryIngestionWorkflowPort, context);
    }

    private static final class CapturingStreamCallback implements StreamCallback {

        private final StreamCallback delegate;
        private final MemoryEnginePort memoryEnginePort;
        private final MemoryIngestionWorkflowPort memoryIngestionWorkflowPort;
        private final StreamChatContext context;
        private final AtomicBoolean captured = new AtomicBoolean(false);

        private CapturingStreamCallback(StreamCallback delegate,
                                        MemoryEnginePort memoryEnginePort,
                                        StreamChatContext context) {
            this.delegate = delegate;
            this.memoryEnginePort = memoryEnginePort;
            this.memoryIngestionWorkflowPort = null;
            this.context = context;
        }

        private CapturingStreamCallback(StreamCallback delegate,
                                        MemoryIngestionWorkflowPort memoryIngestionWorkflowPort,
                                        StreamChatContext context) {
            this.delegate = delegate;
            this.memoryEnginePort = null;
            this.memoryIngestionWorkflowPort = memoryIngestionWorkflowPort;
            this.context = context;
        }

        @Override
        public void onContent(String content) {
            delegate.onContent(content);
        }

        @Override
        public void onComplete() {
            try {
                delegate.onComplete();
            } finally {
                captureUserStatementOnce();
            }
        }

        @Override
        public void onError(Throwable error) {
            try {
                delegate.onError(error);
            } finally {
                captureUserStatementOnce();
            }
        }

        private void captureUserStatementOnce() {
            if (!captured.compareAndSet(false, true)) {
                return;
            }
            try {
                // 捕获阶段只提交候选，DefaultMemoryEnginePort 会继续做可信过滤。
                MemoryWriteRequest writeRequest = MemoryWriteRequest.builder()
                        .conversationId(context.getConversationId())
                        .userId(context.getUserId())
                        .messageId(context.getTaskId())
                        .message(ChatMessage.user(context.getQuestion()))
                        .build();
                if (memoryIngestionWorkflowPort != null) {
                    memoryIngestionWorkflowPort.ingest(MemoryIngestionCommand.chatCompleted(writeRequest));
                } else {
                    memoryEnginePort.writeMemory(writeRequest);
                }
            } catch (Exception ex) {
                LOG.warn("记忆捕获失败，已降级为不写入: userId={}, conversationId={}",
                        context.getUserId(), context.getConversationId(), ex);
            }
        }
    }
}

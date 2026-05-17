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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 问答完成后的轻量记忆捕获阶段。
 *
 * <p>该阶段只负责把可候选内容交给 {@link MemoryEnginePort}，真正的可信过滤和落库策略
 * 仍由记忆引擎负责，避免主链路直接写入原始噪声。</p>
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

    private static final class CapturingStreamCallback implements StreamCallback {

        private final StreamCallback delegate;
        private final MemoryEnginePort memoryEnginePort;
        private final StreamChatContext context;

        private CapturingStreamCallback(StreamCallback delegate,
                                        MemoryEnginePort memoryEnginePort,
                                        StreamChatContext context) {
            this.delegate = delegate;
            this.memoryEnginePort = memoryEnginePort;
            this.context = context;
        }

        @Override
        public void onContent(String content) {
            delegate.onContent(content);
        }

        @Override
        public void onComplete() {
            delegate.onComplete();
            captureUserStatement();
        }

        @Override
        public void onError(Throwable error) {
            delegate.onError(error);
        }

        private void captureUserStatement() {
            try {
                // 捕获阶段只提交候选，DefaultMemoryEnginePort 会继续做可信过滤。
                memoryEnginePort.writeMemory(MemoryWriteRequest.builder()
                        .conversationId(context.getConversationId())
                        .userId(context.getUserId())
                        .messageId(context.getTaskId())
                        .message(ChatMessage.user(context.getQuestion()))
                        .build());
            } catch (Exception ex) {
                LOG.warn("记忆捕获失败，已降级为不写入: userId={}, conversationId={}",
                        context.getUserId(), context.getConversationId(), ex);
            }
        }
    }
}

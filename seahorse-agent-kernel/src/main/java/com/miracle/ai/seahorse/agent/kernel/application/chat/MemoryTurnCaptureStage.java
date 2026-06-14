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

import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamChatContext;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

final class MemoryTurnCaptureStage {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryTurnCaptureStage.class);

    private static final String DEFAULT_TENANT_ID = "default";
    private static final String ASSISTANT_MESSAGE_SUFFIX = "-assistant";
    private static final int CHARS_PER_TOKEN_ESTIMATE = 4;

    private MemoryTurnCaptureStage() {
    }

    static StreamCallback wrap(StreamCallback delegate,
                               MemoryAggregationServicePort aggregationServicePort,
                               MemoryAggregationPolicy policy,
                               StreamChatContext context) {
        Objects.requireNonNull(delegate, "delegate must not be null");
        Objects.requireNonNull(aggregationServicePort, "aggregationServicePort must not be null");
        Objects.requireNonNull(context, "context must not be null");
        return new CapturingStreamCallback(
                delegate,
                aggregationServicePort,
                Objects.requireNonNullElseGet(policy, MemoryAggregationPolicy::defaults),
                context);
    }

    private static final class CapturingStreamCallback implements StreamCallback {

        private final StreamCallback delegate;
        private final MemoryAggregationServicePort aggregationServicePort;
        private final MemoryAggregationPolicy policy;
        private final StreamChatContext context;
        private final StringBuilder assistantText = new StringBuilder();
        private final AtomicBoolean captured = new AtomicBoolean(false);

        private CapturingStreamCallback(StreamCallback delegate,
                                        MemoryAggregationServicePort aggregationServicePort,
                                        MemoryAggregationPolicy policy,
                                        StreamChatContext context) {
            this.delegate = delegate;
            this.aggregationServicePort = aggregationServicePort;
            this.policy = policy;
            this.context = context;
        }

        @Override
        public void onContent(String content) {
            if (content != null) {
                assistantText.append(content);
            }
            delegate.onContent(content);
        }

        @Override
        public void onThinking(String content) {
            delegate.onThinking(content);
        }

        @Override
        public void onComplete() {
            try {
                captureTurnOnce();
                delegate.onComplete();
            } catch (RuntimeException ex) {
                if (!captured.get()) {
                    captureTurnOnce();
                }
                throw ex;
            }
        }

        @Override
        public void onError(Throwable error) {
            try {
                delegate.onError(error);
            } finally {
                if (policy.captureOnError()) {
                    captureTurnOnce();
                }
            }
        }

        private void captureTurnOnce() {
            if (!captured.compareAndSet(false, true)) {
                return;
            }
            try {
                String userText = Objects.requireNonNullElse(context.getQuestion(), "");
                String answerText = assistantText.toString();
                aggregationServicePort.appendTurn(new MemoryTurnEvent(
                        DEFAULT_TENANT_ID,
                        context.getUserId(),
                        context.getConversationId(),
                        sessionId(),
                        context.getTaskId(),
                        assistantMessageId(),
                        userText,
                        answerText,
                        Instant.now(),
                        estimateTokens(userText, answerText)));
            } catch (Exception ex) {
                LOG.warn("Memory turn aggregation failed, downgraded to no capture: userId={}, conversationId={}",
                        context.getUserId(), context.getConversationId(), ex);
            }
        }

        private String sessionId() {
            String conversationId = context.getConversationId();
            if (conversationId != null && !conversationId.isBlank()) {
                return conversationId;
            }
            return context.getTaskId();
        }

        private String assistantMessageId() {
            String taskId = context.getTaskId();
            if (taskId == null || taskId.isBlank()) {
                return ASSISTANT_MESSAGE_SUFFIX.substring(1);
            }
            return taskId + ASSISTANT_MESSAGE_SUFFIX;
        }

        private int estimateTokens(String userText, String assistantText) {
            int chars = safeLength(userText) + safeLength(assistantText);
            return Math.max(1, (int) Math.ceil(chars / (double) CHARS_PER_TOKEN_ESTIMATE));
        }

        private int safeLength(String value) {
            return value == null ? 0 : value.length();
        }
    }
}

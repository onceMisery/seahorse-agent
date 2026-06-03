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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationAppendResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationServicePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferState;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicBoolean;

class MemoryTurnCaptureStageTests {

    @Test
    void shouldCaptureUserAndAssistantMessagesOnComplete() {
        RecordingAggregationService aggregationService = new RecordingAggregationService();
        RecordingCallback delegate = new RecordingCallback();
        StreamCallback callback = MemoryTurnCaptureStage.wrap(
                delegate,
                aggregationService,
                MemoryAggregationPolicy.defaults().withEnabled(true),
                context("Remember I use Java"));

        callback.onContent("Noted, ");
        callback.onContent("I will remember that.");
        callback.onComplete();

        Assertions.assertTrue(delegate.completed.get());
        Assertions.assertEquals("Noted, I will remember that.", delegate.content.toString());
        Assertions.assertNotNull(aggregationService.lastEvent);
        Assertions.assertEquals("Remember I use Java", aggregationService.lastEvent.userText());
        Assertions.assertEquals("Noted, I will remember that.", aggregationService.lastEvent.assistantText());
        Assertions.assertEquals("task-1", aggregationService.lastEvent.userMessageId());
        Assertions.assertEquals("task-1-assistant", aggregationService.lastEvent.assistantMessageId());
        Assertions.assertEquals("conversation-1", aggregationService.lastEvent.sessionId());
    }

    @Test
    void shouldSkipAggregationOnErrorByDefault() {
        RecordingAggregationService aggregationService = new RecordingAggregationService();
        RecordingCallback delegate = new RecordingCallback();
        StreamCallback callback = MemoryTurnCaptureStage.wrap(
                delegate,
                aggregationService,
                MemoryAggregationPolicy.defaults().withEnabled(true),
                context("Remember I use Java"));

        callback.onContent("partial");
        callback.onError(new IllegalStateException("model unavailable"));

        Assertions.assertEquals("model unavailable", delegate.error.getMessage());
        Assertions.assertNull(aggregationService.lastEvent);
    }

    @Test
    void shouldCaptureOnErrorWhenPolicyAllowsIt() {
        RecordingAggregationService aggregationService = new RecordingAggregationService();
        RecordingCallback delegate = new RecordingCallback();
        StreamCallback callback = MemoryTurnCaptureStage.wrap(
                delegate,
                aggregationService,
                MemoryAggregationPolicy.defaults().withEnabled(true).withCaptureOnError(true),
                context("Remember I use Java"));

        callback.onContent("partial");
        callback.onError(new IllegalStateException("model unavailable"));

        Assertions.assertNotNull(aggregationService.lastEvent);
        Assertions.assertEquals("partial", aggregationService.lastEvent.assistantText());
    }

    private StreamChatContext context(String question) {
        return StreamChatContext.builder()
                .question(question)
                .conversationId("conversation-1")
                .taskId("task-1")
                .userId("user-1")
                .callback(new RecordingCallback())
                .build();
    }

    private static final class RecordingAggregationService implements MemoryAggregationServicePort {

        private MemoryTurnEvent lastEvent;

        @Override
        public MemoryAggregationAppendResult appendTurn(MemoryTurnEvent event) {
            lastEvent = event;
            MemoryBufferState state = new MemoryBufferState(
                    event.tenantId(),
                    event.userId(),
                    event.conversationId(),
                    event.sessionId(),
                    1,
                    event.estimatedTokens(),
                    Instant.now(),
                    false,
                    null);
            return MemoryAggregationAppendResult.pending(state);
        }

        @Override
        public MemoryIngestionResult flushReady(String userId,
                                                String sessionId,
                                                String tenantId,
                                                MemoryFlushTrigger trigger,
                                                Instant now) {
            return MemoryIngestionResult.ignored("not_used");
        }
    }

    private static final class RecordingCallback implements StreamCallback {

        private final StringBuilder content = new StringBuilder();
        private final AtomicBoolean completed = new AtomicBoolean(false);
        private Throwable error;

        @Override
        public void onContent(String content) {
            this.content.append(content);
        }

        @Override
        public void onComplete() {
            completed.set(true);
        }

        @Override
        public void onError(Throwable error) {
            this.error = error;
        }
    }
}

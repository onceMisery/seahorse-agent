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

package com.miracle.ai.seahorse.agent.adapters.local;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class LocalChatStreamCallbackFactoryTests {

    @Test
    void shouldBufferEnvelopedEventsForReplay() {
        RecordingEventBuffer eventBuffer = new RecordingEventBuffer();
        LocalChatStreamCallbackFactory factory = new LocalChatStreamCallbackFactory(
                new LocalStreamTaskPort(),
                ConversationMemoryPort.noop(),
                eventBuffer);
        StreamCallback callback = factory.create(new SseEmitter(), "conversation-1", "task-1", "user-1");

        callback.onRunStarted("run-1");
        callback.onContent("hello");
        callback.onComplete();

        assertThat(eventBuffer.events)
                .extracting(StreamEventEnvelope::eventSeq)
                .containsExactly(1L, 2L);
        assertThat(eventBuffer.events)
                .extracting(StreamEventEnvelope::runId)
                .containsOnly("run-1");
        assertThat(eventBuffer.getAfter("run-1", 0)).hasSize(2);
        assertThat(eventBuffer.getLatestSeq("run-1")).contains(2L);
    }

    @Test
    void shouldResolveEventBufferWhenCallbackIsCreated() {
        RecordingEventBuffer eventBuffer = new RecordingEventBuffer();
        AtomicReference<AgentRunEventBufferPort> eventBufferRef = new AtomicReference<>(AgentRunEventBufferPort.noop());
        LocalChatStreamCallbackFactory factory = new LocalChatStreamCallbackFactory(
                new LocalStreamTaskPort(),
                ConversationMemoryPort.noop(),
                eventBufferRef::get);
        eventBufferRef.set(eventBuffer);

        StreamCallback callback = factory.create(new SseEmitter(), "conversation-1", "task-1", "user-1");
        callback.onRunStarted("run-1");
        callback.onComplete();

        assertThat(eventBuffer.events)
                .extracting(StreamEventEnvelope::eventType)
                .containsExactly(com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType.RUN_STARTED);
    }

    private static final class RecordingEventBuffer implements AgentRunEventBufferPort {

        private final List<StreamEventEnvelope> events = new ArrayList<>();

        @Override
        public void append(String runId, StreamEventEnvelope event) {
            events.add(event);
        }

        @Override
        public List<StreamEventEnvelope> getAfter(String runId, long afterSeq) {
            return events.stream()
                    .filter(event -> event.runId().equals(runId))
                    .filter(event -> event.eventSeq() > afterSeq)
                    .toList();
        }

        @Override
        public Optional<Long> getLatestSeq(String runId) {
            return events.stream()
                    .filter(event -> event.runId().equals(runId))
                    .map(StreamEventEnvelope::eventSeq)
                    .max(Long::compareTo);
        }

        @Override
        public void expire(String runId) {
            events.removeIf(event -> event.runId().equals(runId));
        }
    }
}

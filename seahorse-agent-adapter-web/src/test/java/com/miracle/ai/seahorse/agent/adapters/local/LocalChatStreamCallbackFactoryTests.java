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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
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

    @Test
    void shouldAppendAssistantMessageUnderRequestedParentOnComplete() {
        RecordingMemoryPort memoryPort = new RecordingMemoryPort();
        LocalChatStreamCallbackFactory factory = new LocalChatStreamCallbackFactory(
                new LocalStreamTaskPort(),
                memoryPort,
                AgentRunEventBufferPort.noop());
        StreamCallback callback = factory.create(new SseEmitter(), "conversation-1", "task-1", "user-1", 123L);

        callback.onRunStarted("run-1");
        callback.onContent("answer");
        callback.onComplete();

        assertThat(memoryPort.conversationId).isEqualTo("conversation-1");
        assertThat(memoryPort.userId).isEqualTo("user-1");
        assertThat(memoryPort.message.getContent()).isEqualTo("answer");
        assertThat(memoryPort.agentRunId).isEqualTo("run-1");
        assertThat(memoryPort.parentMessageId).isEqualTo(123L);
    }

    @Test
    void shouldFlushEarlyCustomEventsBeforeFirstContent() {
        LocalChatStreamCallbackFactory factory = new LocalChatStreamCallbackFactory(
                new LocalStreamTaskPort(),
                ConversationMemoryPort.noop(),
                AgentRunEventBufferPort.noop());
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        StreamCallback callback = factory.create(emitter, "conversation-1", "task-1", "user-1");

        callback.onEvent("memory.conflict.prompt", Map.of("conflictId", "conflict-1"));
        assertThat(emitter.events).noneSatisfy(event -> assertThat(event).contains("memory.conflict.prompt"));

        callback.onContent("hello");

        int promptIndex = indexOfEventContaining(emitter.events, "memory.conflict.prompt");
        int messageIndex = indexOfEventContaining(emitter.events, "type=response");
        assertThat(promptIndex).isPositive();
        assertThat(messageIndex).isGreaterThan(promptIndex);
    }

    @Test
    void shouldFlushExternallyBufferedArtifactEventsBeforeCompletion() {
        RecordingEventBuffer eventBuffer = new RecordingEventBuffer();
        LocalChatStreamCallbackFactory factory = new LocalChatStreamCallbackFactory(
                new LocalStreamTaskPort(),
                ConversationMemoryPort.noop(),
                eventBuffer);
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        StreamCallback callback = factory.create(emitter, "conversation-1", "task-1", "user-1");

        callback.onRunStarted("run-1");
        eventBuffer.append("run-1", StreamEventEnvelope.of(
                2,
                StreamEventType.AGENT_ARTIFACT,
                "run-1",
                Map.of("artifactId", "artifact-1", "title", "Generated image")));
        callback.onEvent(StreamEventType.TOOL_CALL_FINISHED.value(),
                Map.of("toolCallId", "tool-call-1", "toolId", "image_generation", "status", "SUCCEEDED"));

        assertThat(eventBuffer.events)
                .extracting(StreamEventEnvelope::eventSeq)
                .containsExactly(1L, 2L, 3L);
        assertThat(eventBuffer.events)
                .extracting(StreamEventEnvelope::eventType)
                .containsExactly(StreamEventType.RUN_STARTED, StreamEventType.AGENT_ARTIFACT,
                        StreamEventType.TOOL_CALL_FINISHED);
        assertThat(emitter.events)
                .anySatisfy(event -> assertThat(event)
                        .contains("stream_event")
                        .contains("eventSeq=2")
                        .contains("AGENT_ARTIFACT"));
        assertThat(emitter.events)
                .anySatisfy(event -> assertThat(event)
                        .contains("agent.artifact")
                        .contains("artifact-1"));
        assertThat(emitter.events)
                .anySatisfy(event -> assertThat(event)
                        .contains("stream_event")
                        .contains("eventSeq=3")
                        .contains("TOOL_CALL_FINISHED"));
    }

    @Test
    void shouldFlushArtifactBufferedWhileAllocatingNextSequence() {
        RaceOnLatestSeqEventBuffer eventBuffer = new RaceOnLatestSeqEventBuffer();
        LocalChatStreamCallbackFactory factory = new LocalChatStreamCallbackFactory(
                new LocalStreamTaskPort(),
                ConversationMemoryPort.noop(),
                eventBuffer);
        RecordingSseEmitter emitter = new RecordingSseEmitter();
        StreamCallback callback = factory.create(emitter, "conversation-1", "task-1", "user-1");

        callback.onRunStarted("run-1");
        eventBuffer.appendArtifactOnNextLatestSeq();
        callback.onEvent(StreamEventType.TOOL_CALL_FINISHED.value(),
                Map.of("toolCallId", "tool-call-1", "toolId", "image_generation", "status", "SUCCEEDED"));

        assertThat(eventBuffer.events)
                .extracting(StreamEventEnvelope::eventSeq)
                .containsExactly(1L, 2L, 3L);
        assertThat(emitter.events)
                .anySatisfy(event -> assertThat(event)
                        .contains("stream_event")
                        .contains("eventSeq=2")
                        .contains("AGENT_ARTIFACT"));
        assertThat(emitter.events)
                .anySatisfy(event -> assertThat(event)
                        .contains("stream_event")
                        .contains("eventSeq=3")
                        .contains("TOOL_CALL_FINISHED"));
    }

    private static int indexOfEventContaining(List<String> events, String fragment) {
        for (int i = 0; i < events.size(); i++) {
            if (events.get(i).contains(fragment)) {
                return i;
            }
        }
        return -1;
    }

    private static class RecordingEventBuffer implements AgentRunEventBufferPort {

        final List<StreamEventEnvelope> events = new ArrayList<>();

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

    private static final class RaceOnLatestSeqEventBuffer extends RecordingEventBuffer {

        private boolean appendArtifactOnNextLatestSeq;

        void appendArtifactOnNextLatestSeq() {
            appendArtifactOnNextLatestSeq = true;
        }

        @Override
        public Optional<Long> getLatestSeq(String runId) {
            Optional<Long> latest = super.getLatestSeq(runId);
            if (appendArtifactOnNextLatestSeq) {
                appendArtifactOnNextLatestSeq = false;
                long nextSeq = latest.orElse(0L) + 1L;
                append(runId, StreamEventEnvelope.of(
                        nextSeq,
                        StreamEventType.AGENT_ARTIFACT,
                        runId,
                        Map.of("artifactId", "artifact-race", "title", "Generated image")));
                return Optional.of(nextSeq);
            }
            return latest;
        }
    }

    private static final class RecordingMemoryPort implements ConversationMemoryPort {

        private String conversationId;
        private String userId;
        private ChatMessage message;
        private String agentRunId;
        private Long parentMessageId;

        @Override
        public List<ChatMessage> loadAndAppend(String conversationId, String userId, ChatMessage message) {
            return List.of();
        }

        @Override
        public void append(
                String conversationId,
                String userId,
                ChatMessage message,
                String agentRunId,
                Long parentMessageId) {
            this.conversationId = conversationId;
            this.userId = userId;
            this.message = message;
            this.agentRunId = agentRunId;
            this.parentMessageId = parentMessageId;
        }
    }

    private static final class RecordingSseEmitter extends SseEmitter {

        private final List<String> events = new ArrayList<>();

        @Override
        public void send(SseEventBuilder builder) throws IOException {
            events.add(builder.build().stream()
                    .map(item -> String.valueOf(item.getData()))
                    .collect(Collectors.joining("\n")));
        }
    }
}

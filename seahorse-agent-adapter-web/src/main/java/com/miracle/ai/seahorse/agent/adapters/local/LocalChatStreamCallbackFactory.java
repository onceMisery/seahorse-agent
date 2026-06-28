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
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamCompletionPayload;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventSender;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamMessageDelta;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamMetaPayload;
import com.miracle.ai.seahorse.agent.adapters.web.ChatStreamCallbackFactoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import com.miracle.ai.seahorse.agent.ports.inbound.task.TaskInboundPort;
import com.miracle.ai.seahorse.agent.kernel.domain.task.Task;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

/**
 * Local stream chat callback factory with event envelope support.
 */
public class LocalChatStreamCallbackFactory implements ChatStreamCallbackFactoryPort {

    private static final int DEFAULT_MESSAGE_CHUNK_SIZE = 5;
    private static final String TYPE_THINK = "think";
    private static final String TYPE_RESPONSE = "response";
    private static final String DONE_PAYLOAD = "[DONE]";
    private static final String STREAM_EVENT_NAME = "stream_event";

    private final StreamTaskPort streamTaskPort;
    private final ConversationMemoryPort memoryPort;
    private final Supplier<AgentRunEventBufferPort> eventBufferPortSupplier;
    private final ObjectProvider<TaskInboundPort> taskPortProvider;

    public LocalChatStreamCallbackFactory(StreamTaskPort streamTaskPort) {
        this(streamTaskPort, ConversationMemoryPort.noop(), AgentRunEventBufferPort::noop, null);
    }

    public LocalChatStreamCallbackFactory(StreamTaskPort streamTaskPort, ConversationMemoryPort memoryPort) {
        this(streamTaskPort, memoryPort, AgentRunEventBufferPort::noop, null);
    }

    public LocalChatStreamCallbackFactory(StreamTaskPort streamTaskPort,
                                          ConversationMemoryPort memoryPort,
                                          AgentRunEventBufferPort eventBufferPort) {
        this(streamTaskPort, memoryPort, () -> eventBufferPort, null);
    }

    public LocalChatStreamCallbackFactory(StreamTaskPort streamTaskPort,
                                          ConversationMemoryPort memoryPort,
                                          Supplier<AgentRunEventBufferPort> eventBufferPortSupplier) {
        this(streamTaskPort, memoryPort, eventBufferPortSupplier, null);
    }

    public LocalChatStreamCallbackFactory(StreamTaskPort streamTaskPort,
                                          ConversationMemoryPort memoryPort,
                                          Supplier<AgentRunEventBufferPort> eventBufferPortSupplier,
                                          ObjectProvider<TaskInboundPort> taskPortProvider) {
        this.streamTaskPort = Objects.requireNonNull(streamTaskPort, "streamTaskPort must not be null");
        this.memoryPort = Objects.requireNonNullElse(memoryPort, ConversationMemoryPort.noop());
        this.eventBufferPortSupplier = eventBufferPortSupplier == null
                ? AgentRunEventBufferPort::noop
                : eventBufferPortSupplier;
        this.taskPortProvider = taskPortProvider;
    }

    @Override
    public StreamCallback create(SseEmitter emitter, String conversationId, String taskId) {
        return create(emitter, conversationId, taskId, "");
    }

    @Override
    public StreamCallback create(SseEmitter emitter, String conversationId, String taskId, String userId) {
        return create(emitter, conversationId, taskId, userId, null);
    }

    @Override
    public StreamCallback create(
            SseEmitter emitter,
            String conversationId,
            String taskId,
            String userId,
            Long assistantParentMessageId) {
        return new LocalChatStreamCallback(emitter, conversationId, taskId, userId,
                assistantParentMessageId, streamTaskPort, memoryPort, eventBufferPort(), taskPortProvider);
    }

    private AgentRunEventBufferPort eventBufferPort() {
        return Objects.requireNonNullElseGet(eventBufferPortSupplier.get(), AgentRunEventBufferPort::noop);
    }

    private static final class LocalChatStreamCallback implements StreamCallback {
    
        private static final Logger log = LoggerFactory.getLogger(LocalChatStreamCallback.class);
    
        private final StreamEventSender sender;
        private final String conversationId;
        private final String taskId;
        private final String userId;
        private final Long assistantParentMessageId;
        private final StreamTaskPort streamTaskPort;
        private final ConversationMemoryPort memoryPort;
        private final AgentRunEventBufferPort eventBufferPort;
        private final ObjectProvider<TaskInboundPort> taskPortProvider;
        private final StringBuilder answer = new StringBuilder();
        private final StringBuilder thinking = new StringBuilder();
        private final List<PendingCustomEvent> pendingCustomEvents = new ArrayList<>();
        private final AtomicLong seqCounter = new AtomicLong(0);
        private long sentEventSeq;
        private String currentRunId;
        private long runStartedAtMs;
        private boolean responseStarted;
    
        private LocalChatStreamCallback(SseEmitter emitter,
                                        String conversationId,
                                        String taskId,
                                        String userId,
                                        Long assistantParentMessageId,
                                        StreamTaskPort streamTaskPort,
                                        ConversationMemoryPort memoryPort,
                                        AgentRunEventBufferPort eventBufferPort,
                                        ObjectProvider<TaskInboundPort> taskPortProvider) {
            this.sender = new SpringSseEventSender(Objects.requireNonNull(emitter, "emitter must not be null"));
            this.conversationId = conversationId;
            this.taskId = taskId;
            this.userId = Objects.requireNonNullElse(userId, "");
            this.assistantParentMessageId = assistantParentMessageId;
            this.streamTaskPort = streamTaskPort;
            this.memoryPort = Objects.requireNonNullElse(memoryPort, ConversationMemoryPort.noop());
            this.eventBufferPort = Objects.requireNonNullElse(eventBufferPort, AgentRunEventBufferPort.noop());
            this.taskPortProvider = taskPortProvider;
            initialize();
        }

        @Override
        public void onContent(String content) {
            if (streamTaskPort.isCancelled(taskId) || isBlank(content)) {
                return;
            }
            answer.append(content);
            markResponseStarted();
            sendChunked(TYPE_RESPONSE, content);
        }

        @Override
        public void onThinking(String content) {
            if (streamTaskPort.isCancelled(taskId) || isBlank(content)) {
                return;
            }
            thinking.append(content);
            markResponseStarted();
            sendChunked(TYPE_THINK, content);
        }

        @Override
        public void onRunStarted(String runId) {
            if (streamTaskPort.isCancelled(taskId) || isBlank(runId)) {
                return;
            }
            currentRunId = runId;
            runStartedAtMs = System.currentTimeMillis();
            sender.sendEvent(StreamEventType.META.value(), new StreamMetaPayload(conversationId, taskId, runId));

            Object runStartedPayload = AgentStreamTimelineEvents.runStartedProtocol(runId, conversationId, taskId);
            emitEnveloped(StreamEventType.RUN_STARTED, runStartedPayload);

            sender.sendEvent(StreamEventType.AGENT_TIMELINE.value(), AgentStreamTimelineEvents.runStarted(runId));
        }

        @Override
        public void onEvent(String eventName, Object payload) {
            if (streamTaskPort.isCancelled(taskId) || isBlank(eventName)) {
                return;
            }
            StreamEventType eventType = resolveEventType(eventName);
            if (eventType != null && currentRunId != null) {
                emitEnveloped(eventType, payload);
            } else if (eventType == null && !responseStarted) {
                pendingCustomEvents.add(new PendingCustomEvent(eventName, payload));
            } else {
                sender.sendEvent(eventName, payload);
            }
        }

        @Override
        public void onComplete() {
            if (streamTaskPort.isCancelled(taskId)) {
                return;
            }
            markResponseStarted();
            flushBufferedEvents();
            sendRunCompletedEvent();
            appendAssistantMessage();
            sender.sendEvent(StreamEventType.FINISH.value(), new StreamCompletionPayload(null, null));
            sender.sendEvent(StreamEventType.DONE.value(), DONE_PAYLOAD);
            streamTaskPort.unregister(taskId);
            sender.complete();
            completeFacadeTask();
        }

        @Override
        public void onError(Throwable error) {
            if (streamTaskPort.isCancelled(taskId)) {
                return;
            }
            markResponseStarted();
            streamTaskPort.unregister(taskId);
            sender.fail(error);
            completeFacadeTask();
        }

        /**
         * SSE 流结束后，查找并闭合对应的 Task Facade 任务（如有）。
         */
        private void completeFacadeTask() {
            if (taskPortProvider == null || conversationId == null || conversationId.isBlank()) {
                return;
            }
            try {
                TaskInboundPort port = taskPortProvider.getIfAvailable();
                if (port == null) return;
                Task facadeTask = port.findRunningTaskByConversation(conversationId);
                if (facadeTask != null) {
                    port.completeTask(facadeTask.getTaskId());
                }
            } catch (Exception e) {
                log.debug("Failed to complete facade task for conversation {}: {}", conversationId, e.toString());
            }
        }

        private void initialize() {
            sender.sendEvent(StreamEventType.META.value(), new StreamMetaPayload(conversationId, taskId));
            streamTaskPort.register(taskId, sender, () -> new StreamCompletionPayload(null, null));
        }

        private void markResponseStarted() {
            if (responseStarted) {
                return;
            }
            responseStarted = true;
            flushPendingCustomEvents();
        }

        private void flushPendingCustomEvents() {
            if (pendingCustomEvents.isEmpty()) {
                return;
            }
            for (PendingCustomEvent event : pendingCustomEvents) {
                sender.sendEvent(event.eventName(), event.payload());
            }
            pendingCustomEvents.clear();
        }

        private void emitEnveloped(StreamEventType eventType, Object payload) {
            if (currentRunId == null) {
                sender.sendEvent(eventType.value(), payload);
                return;
            }
            flushBufferedEvents();
            long seq = nextSeq();
            flushBufferedEventsBefore(seq);
            StreamEventEnvelope envelope = StreamEventEnvelope.of(seq, eventType, currentRunId, payload);
            try {
                eventBufferPort.append(currentRunId, envelope);
            } catch (Exception e) {
                log.warn("Failed to buffer event seq={} type={} for run={}", seq, eventType.value(), currentRunId, e);
            }
            sender.sendEvent(STREAM_EVENT_NAME, envelope);
            sender.sendEvent(eventType.value(), payload);
            sentEventSeq = Math.max(sentEventSeq, seq);
        }

        private void appendAssistantMessage() {
            if (isBlank(conversationId) || isBlank(userId) || answer.isEmpty()) {
                return;
            }
            memoryPort.append(conversationId, userId,
                    ChatMessage.assistant(answer.toString(), thinking.toString(), null),
                    currentRunId,
                    assistantParentMessageId);
        }

        private void sendRunCompletedEvent() {
            if (isBlank(currentRunId) || runStartedAtMs <= 0L) {
                return;
            }
            long durationMs = System.currentTimeMillis() - runStartedAtMs;
            sender.sendEvent(StreamEventType.AGENT_TIMELINE.value(),
                    AgentStreamTimelineEvents.runSucceeded(currentRunId, durationMs));
        }

        private void sendChunked(String type, String content) {
            int length = content.length();
            int index = 0;
            int count = 0;
            StringBuilder buffer = new StringBuilder();
            while (index < length) {
                int codePoint = content.codePointAt(index);
                buffer.appendCodePoint(codePoint);
                index += Character.charCount(codePoint);
                count++;
                if (count >= DEFAULT_MESSAGE_CHUNK_SIZE) {
                    sendMessage(type, buffer);
                    count = 0;
                }
            }
            if (!buffer.isEmpty()) {
                sendMessage(type, buffer);
            }
        }

        private void sendMessage(String type, StringBuilder buffer) {
            StreamMessageDelta delta = new StreamMessageDelta(type, buffer.toString());
            if (currentRunId != null) {
                flushBufferedEvents();
                long seq = nextSeq();
                flushBufferedEventsBefore(seq);
                StreamEventEnvelope envelope = StreamEventEnvelope.of(seq, StreamEventType.MESSAGE, currentRunId, delta);
                try {
                    eventBufferPort.append(currentRunId, envelope);
                } catch (Exception e) {
                    log.warn("Failed to buffer message event seq={} for run={}", seq, currentRunId, e);
                }
                sender.sendEvent(STREAM_EVENT_NAME, envelope);
                sentEventSeq = Math.max(sentEventSeq, seq);
            }
            sender.sendEvent(StreamEventType.MESSAGE.value(), delta);
            buffer.setLength(0);
        }

        private long nextSeq() {
            long latest;
            try {
                latest = eventBufferPort.getLatestSeq(currentRunId).orElse(seqCounter.get());
            } catch (Exception e) {
                log.warn("Failed to read latest event seq for run={}", currentRunId, e);
                latest = seqCounter.get();
            }
            long base = Math.max(seqCounter.get(), latest);
            seqCounter.set(base);
            return seqCounter.incrementAndGet();
        }

        private void flushBufferedEvents() {
            flushBufferedEventsBefore(Long.MAX_VALUE);
        }

        private void flushBufferedEventsBefore(long beforeSeq) {
            if (currentRunId == null) {
                return;
            }
            List<StreamEventEnvelope> events;
            try {
                events = eventBufferPort.getAfter(currentRunId, sentEventSeq);
            } catch (Exception e) {
                log.warn("Failed to flush buffered events after seq={} for run={}", sentEventSeq, currentRunId, e);
                return;
            }
            for (StreamEventEnvelope event : events) {
                if (event.eventSeq() <= sentEventSeq) {
                    continue;
                }
                if (event.eventSeq() >= beforeSeq) {
                    continue;
                }
                sender.sendEvent(STREAM_EVENT_NAME, event);
                sender.sendEvent(event.eventType().value(), event.typedPayload());
                sentEventSeq = event.eventSeq();
                seqCounter.updateAndGet(current -> Math.max(current, event.eventSeq()));
            }
        }

        private StreamEventType resolveEventType(String eventName) {
            for (StreamEventType type : StreamEventType.values()) {
                if (type.value().equals(eventName)) {
                    return type;
                }
            }
            return null;
        }

        private boolean isBlank(String content) {
            return content == null || content.isBlank();
        }

        private record PendingCustomEvent(String eventName, Object payload) {
        }
    }
}

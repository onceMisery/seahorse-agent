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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

/**
 * 把 ResearchRunOrchestrator 写入 eventBuffer 的事件流转发到 SSE。
 *
 * <p>对于每个研究任务启动一个轮询任务，按 seq 顺序拉取并下发事件，
 * 直到看到 FINISH 事件、超时或 SSE 客户端断开。
 */
public class ResearchSseBridge {

    private static final Logger log = LoggerFactory.getLogger(ResearchSseBridge.class);
    private static final String STREAM_EVENT_NAME = "stream_event";
    private static final long DEFAULT_POLL_INTERVAL_MS = 200L;
    private static final long DEFAULT_MAX_DURATION_MS = 10 * 60 * 1000L;
    private static final long DEFAULT_CONTENT_THROTTLE_MS = 50L;

    private final AgentRunEventBufferPort eventBufferPort;
    private final ScheduledExecutorService executor;
    private final long pollIntervalMs;
    private final long maxDurationMs;

    public ResearchSseBridge(AgentRunEventBufferPort eventBufferPort) {
        this(eventBufferPort, defaultExecutor(), DEFAULT_POLL_INTERVAL_MS, DEFAULT_MAX_DURATION_MS);
    }

    public ResearchSseBridge(AgentRunEventBufferPort eventBufferPort,
                             ScheduledExecutorService executor,
                             long pollIntervalMs,
                             long maxDurationMs) {
        this.eventBufferPort = Objects.requireNonNullElse(eventBufferPort, AgentRunEventBufferPort.noop());
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.pollIntervalMs = pollIntervalMs <= 0 ? DEFAULT_POLL_INTERVAL_MS : pollIntervalMs;
        this.maxDurationMs = maxDurationMs <= 0 ? DEFAULT_MAX_DURATION_MS : maxDurationMs;
    }

    public void attach(SseEmitter emitter, String runId, String conversationId, String taskId) {
        attach(emitter, runId, conversationId, taskId, 0L);
    }

    public void attach(SseEmitter emitter, String runId, String conversationId, String taskId, long afterSeq) {
        Objects.requireNonNull(emitter, "emitter must not be null");
        Objects.requireNonNull(runId, "runId must not be null");
        try {
            emitter.send(SseEmitter.event().name(StreamEventType.META.value()).data(meta(runId, conversationId, taskId)));
        } catch (IOException ex) {
            emitter.complete();
            return;
        }
        AtomicLong cursor = new AtomicLong(Math.max(0L, afterSeq));
        AtomicBoolean closed = new AtomicBoolean(false);
        long startedAt = System.currentTimeMillis();
        ThrottledEventSender throttledSender = new ThrottledEventSender(DEFAULT_CONTENT_THROTTLE_MS);
        ScheduledFuture<?>[] handle = new ScheduledFuture<?>[1];

        Runnable poller = () -> {
            if (closed.get()) {
                return;
            }
            try {
                List<StreamEventEnvelope> events = eventBufferPort.getAfter(runId, cursor.get());
                long now = System.currentTimeMillis();
                EventSink sink = envelope -> sendEnvelope(emitter, envelope);
                if (events.isEmpty()) {
                    throttledSender.flushDue(now, sink);
                }
                for (StreamEventEnvelope envelope : events) {
                    if (closed.get()) {
                        return;
                    }
                    throttledSender.accept(envelope, now, sink);
                    cursor.set(envelope.eventSeq());
                    if (envelope.eventType() == StreamEventType.FINISH) {
                        completeStream(emitter, closed, handle);
                        return;
                    }
                }
                if (System.currentTimeMillis() - startedAt > maxDurationMs) {
                    throttledSender.flushNow(sink);
                    completeStream(emitter, closed, handle);
                }
            } catch (IOException ex) {
                cancelStream(emitter, closed, handle);
            } catch (Exception ex) {
                log.warn("Research SSE bridge poll failed run={}", runId, ex);
                cancelStream(emitter, closed, handle);
            }
        };

        emitter.onCompletion(() -> cancelStream(emitter, closed, handle));
        emitter.onTimeout(() -> cancelStream(emitter, closed, handle));
        emitter.onError(ex -> cancelStream(emitter, closed, handle));

        handle[0] = executor.scheduleWithFixedDelay(poller, 0, pollIntervalMs, TimeUnit.MILLISECONDS);
    }

    private static Map<String, Object> meta(String runId, String conversationId, String taskId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("runId", runId);
        if (conversationId != null) {
            payload.put("conversationId", conversationId);
        }
        if (taskId != null) {
            payload.put("taskId", taskId);
        }
        return payload;
    }

    private void sendEnvelope(SseEmitter emitter, StreamEventEnvelope envelope) throws IOException {
        emitter.send(SseEmitter.event().name(STREAM_EVENT_NAME).data(envelope));
        emitter.send(SseEmitter.event().name(envelope.eventType().value()).data(envelope.typedPayload()));
    }

    private void completeStream(SseEmitter emitter, AtomicBoolean closed, ScheduledFuture<?>[] handle) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(StreamEventType.DONE.value()).data("[DONE]"));
            emitter.complete();
        } catch (IOException ex) {
            emitter.complete();
        } finally {
            cancelHandle(handle);
        }
    }

    private void cancelStream(SseEmitter emitter, AtomicBoolean closed, ScheduledFuture<?>[] handle) {
        if (!closed.compareAndSet(false, true)) {
            return;
        }
        try {
            emitter.complete();
        } finally {
            cancelHandle(handle);
        }
    }

    private void cancelHandle(ScheduledFuture<?>[] handle) {
        if (handle[0] != null) {
            handle[0].cancel(false);
        }
    }

    private static ScheduledExecutorService defaultExecutor() {
        return Executors.newScheduledThreadPool(2, runnable -> {
            Thread thread = new Thread(runnable, "research-sse-bridge");
            thread.setDaemon(true);
            return thread;
        });
    }

    @FunctionalInterface
    interface EventSink {
        void send(StreamEventEnvelope envelope) throws IOException;
    }

    static final class ThrottledEventSender {

        private final long throttleMs;
        private StreamEventEnvelope pending;
        private long pendingSinceMs;

        ThrottledEventSender(long throttleMs) {
            this.throttleMs = Math.max(1L, throttleMs);
        }

        void accept(StreamEventEnvelope envelope, long nowMs, EventSink sink) throws IOException {
            Objects.requireNonNull(envelope, "envelope must not be null");
            Objects.requireNonNull(sink, "sink must not be null");
            if (!isContentEvent(envelope.eventType())) {
                flushNow(sink);
                sink.send(envelope);
                return;
            }
            flushDue(nowMs, sink);
            if (pending == null) {
                pending = envelope;
                pendingSinceMs = nowMs;
                return;
            }
            if (canMerge(pending, envelope)) {
                pending = merge(pending, envelope);
                return;
            }
            flushNow(sink);
            pending = envelope;
            pendingSinceMs = nowMs;
        }

        void flushDue(long nowMs, EventSink sink) throws IOException {
            Objects.requireNonNull(sink, "sink must not be null");
            if (pending != null && nowMs - pendingSinceMs >= throttleMs) {
                flushNow(sink);
            }
        }

        void flushNow(EventSink sink) throws IOException {
            Objects.requireNonNull(sink, "sink must not be null");
            if (pending == null) {
                return;
            }
            StreamEventEnvelope envelope = pending;
            pending = null;
            pendingSinceMs = 0L;
            sink.send(envelope);
        }

        private static boolean isContentEvent(StreamEventType eventType) {
            return eventType == StreamEventType.MESSAGE || eventType == StreamEventType.ARTIFACT_CONTENT;
        }

        private static boolean canMerge(StreamEventEnvelope current, StreamEventEnvelope next) {
            if (current.eventType() != next.eventType()) {
                return false;
            }
            if (current.typedPayload() instanceof Map<?, ?> currentMap
                    && next.typedPayload() instanceof Map<?, ?> nextMap) {
                Object currentArtifactId = currentMap.get("artifactId");
                Object nextArtifactId = nextMap.get("artifactId");
                return currentArtifactId == null || nextArtifactId == null || currentArtifactId.equals(nextArtifactId);
            }
            return current.typedPayload() instanceof String && next.typedPayload() instanceof String;
        }

        private static StreamEventEnvelope merge(StreamEventEnvelope current, StreamEventEnvelope next) {
            Object mergedPayload = mergePayload(current.typedPayload(), next.typedPayload());
            return new StreamEventEnvelope(
                    next.eventId(),
                    next.eventSeq(),
                    next.eventType(),
                    next.runId(),
                    next.stepId(),
                    next.timestamp(),
                    mergedPayload);
        }

        private static Object mergePayload(Object currentPayload, Object nextPayload) {
            if (currentPayload instanceof Map<?, ?> currentMap && nextPayload instanceof Map<?, ?> nextMap) {
                Map<String, Object> merged = new LinkedHashMap<>();
                copyMap(merged, currentMap);
                copyMap(merged, nextMap);
                mergeTextField(merged, currentMap, nextMap, "delta");
                mergeTextField(merged, currentMap, nextMap, "content");
                return merged;
            }
            if (currentPayload instanceof String currentText && nextPayload instanceof String nextText) {
                return currentText + nextText;
            }
            return nextPayload;
        }

        private static void copyMap(Map<String, Object> target, Map<?, ?> source) {
            for (Map.Entry<?, ?> entry : source.entrySet()) {
                if (entry.getKey() != null) {
                    target.put(String.valueOf(entry.getKey()), entry.getValue());
                }
            }
        }

        private static void mergeTextField(Map<String, Object> target,
                                           Map<?, ?> currentMap,
                                           Map<?, ?> nextMap,
                                           String field) {
            if (!currentMap.containsKey(field) || !nextMap.containsKey(field)) {
                return;
            }
            target.put(field, String.valueOf(currentMap.get(field)) + nextMap.get(field));
        }
    }
}

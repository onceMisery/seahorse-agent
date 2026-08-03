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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class ResearchSseBridgeThrottlingTests {

    @Test
    void shouldReportTimeoutAsErrorBeforeClosingStream() throws Exception {
        AgentRunEventBufferPort eventBufferPort = mock(AgentRunEventBufferPort.class);
        when(eventBufferPort.getAfter("run-timeout", 0L)).thenAnswer(invocation -> {
            Thread.sleep(5L);
            return List.of();
        });
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<Runnable> poller = new AtomicReference<>();
        when(executor.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    poller.set(invocation.getArgument(0));
                    return future;
                });
        SseEmitter emitter = mock(SseEmitter.class);

        new ResearchSseBridge(eventBufferPort, executor, 1L, 1L)
                .attach(emitter, "run-timeout", null, null);
        poller.get().run();

        verify(emitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(future).cancel(false);
    }

    @Test
    void shouldReportBufferedEventReadFailureBeforeClosingStream() throws Exception {
        AgentRunEventBufferPort eventBufferPort = mock(AgentRunEventBufferPort.class);
        when(eventBufferPort.getAfter("run-read-failure", 0L))
                .thenThrow(new IllegalStateException("database unavailable"));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<Runnable> poller = new AtomicReference<>();
        when(executor.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    poller.set(invocation.getArgument(0));
                    return future;
                });
        SseEmitter emitter = mock(SseEmitter.class);

        new ResearchSseBridge(eventBufferPort, executor, 1L, 10_000L)
                .attach(emitter, "run-read-failure", null, null);
        poller.get().run();

        verify(emitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
        verify(emitter).complete();
        verify(future).cancel(false);
    }

    @Test
    void shouldCancelDisconnectedPollerAndResumeFromLastSequence() throws Exception {
        AgentRunEventBufferPort eventBufferPort = mock(AgentRunEventBufferPort.class);
        when(eventBufferPort.getAfter(eq("run-reconnect"), anyLong())).thenAnswer(invocation -> {
            long afterSeq = invocation.getArgument(1);
            if (afterSeq == 0L) {
                return List.of(event(1, StreamEventType.MESSAGE,
                        Map.of("type", "response", "delta", "first")));
            }
            return List.of(event(2, StreamEventType.FINISH, Map.of("status", "succeeded")));
        });

        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> firstFuture = mock(ScheduledFuture.class);
        ScheduledFuture<?> secondFuture = mock(ScheduledFuture.class);
        List<Runnable> pollers = new ArrayList<>();
        when(executor.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    pollers.add(invocation.getArgument(0));
                    return pollers.size() == 1 ? firstFuture : secondFuture;
                });

        SseEmitter firstEmitter = mock(SseEmitter.class);
        AtomicReference<Runnable> completion = new AtomicReference<>();
        doAnswer(invocation -> {
            completion.set(invocation.getArgument(0));
            return null;
        }).when(firstEmitter).onCompletion(any(Runnable.class));

        ResearchSseBridge bridge = new ResearchSseBridge(eventBufferPort, executor, 1L, 10_000L);
        bridge.attach(firstEmitter, "run-reconnect", null, null);
        pollers.get(0).run();
        completion.get().run();

        verify(firstFuture).cancel(false);
        verify(firstEmitter).complete();

        SseEmitter resumedEmitter = mock(SseEmitter.class);
        bridge.attach(resumedEmitter, "run-reconnect", null, null, 1L);
        pollers.get(1).run();

        verify(eventBufferPort).getAfter("run-reconnect", 1L);
        verify(resumedEmitter).complete();
        verify(secondFuture).cancel(false);
    }

    @Test
    void shouldMergeContentEventsWithinThrottleWindowAndFlushBeforeLifecycleEvents() throws Exception {
        ResearchSseBridge.ThrottledEventSender sender = new ResearchSseBridge.ThrottledEventSender(50L);
        List<StreamEventEnvelope> sent = new ArrayList<>();

        sender.accept(event(1, StreamEventType.ARTIFACT_START, Map.of("artifactId", "artifact-1")),
                1_000L, sent::add);
        sender.accept(event(2, StreamEventType.ARTIFACT_CONTENT, Map.of(
                "artifactId", "artifact-1",
                "delta", "hello ")), 1_001L, sent::add);
        sender.accept(event(3, StreamEventType.ARTIFACT_CONTENT, Map.of(
                "artifactId", "artifact-1",
                "delta", "world")), 1_002L, sent::add);
        sender.flushDue(1_002L, sent::add);
        sender.flushDue(1_052L, sent::add);
        sender.accept(event(4, StreamEventType.ARTIFACT_CONTENT, Map.of(
                "artifactId", "artifact-1",
                "delta", "!")), 1_060L, sent::add);
        sender.flushDue(1_060L, sent::add);
        sender.accept(event(5, StreamEventType.ARTIFACT_END, Map.of(
                "artifactId", "artifact-1",
                "totalChars", 12)), 1_061L, sent::add);

        assertThat(sent).extracting(StreamEventEnvelope::eventType).containsExactly(
                StreamEventType.ARTIFACT_START,
                StreamEventType.ARTIFACT_CONTENT,
                StreamEventType.ARTIFACT_CONTENT,
                StreamEventType.ARTIFACT_END);
        assertThat(sent.get(1).eventSeq()).isEqualTo(3L);
        assertThat(sent.get(1).typedPayload()).isInstanceOf(Map.class);
        assertThat(((Map<?, ?>) sent.get(1).typedPayload()).get("delta")).isEqualTo("hello world");
        assertThat(sent.get(2).eventSeq()).isEqualTo(4L);
        assertThat(((Map<?, ?>) sent.get(2).typedPayload()).get("delta")).isEqualTo("!");
    }

    @Test
    void shouldFlushPendingContentBeforeTerminalDoneEvent() throws Exception {
        AgentRunEventBufferPort eventBufferPort = mock(AgentRunEventBufferPort.class);
        when(eventBufferPort.getAfter("run-terminal-content", 0L)).thenReturn(List.of(
                event(1, StreamEventType.MESSAGE, Map.of("delta", "tail")),
                event(2, StreamEventType.FINISH, Map.of("status", "succeeded"))));
        ScheduledExecutorService executor = mock(ScheduledExecutorService.class);
        ScheduledFuture<?> future = mock(ScheduledFuture.class);
        AtomicReference<Runnable> poller = new AtomicReference<>();
        when(executor.scheduleWithFixedDelay(any(Runnable.class), eq(0L), eq(1L), eq(TimeUnit.MILLISECONDS)))
                .thenAnswer(invocation -> {
                    poller.set(invocation.getArgument(0));
                    return future;
                });
        SseEmitter emitter = mock(SseEmitter.class);
        List<SseEmitter.SseEventBuilder> sent = new ArrayList<>();
        doAnswer(invocation -> {
            sent.add(invocation.getArgument(0));
            return null;
        }).when(emitter).send(any(SseEmitter.SseEventBuilder.class));

        new ResearchSseBridge(eventBufferPort, executor, 1L, 10_000L)
                .attach(emitter, "run-terminal-content", null, null);
        poller.get().run();

        assertThat(sent).hasSize(6);
        verify(emitter).complete();
        verify(future).cancel(false);
    }

    private static StreamEventEnvelope event(long seq, StreamEventType type, Object payload) {
        return new StreamEventEnvelope(
                "event-" + seq,
                seq,
                type,
                "run-1",
                null,
                Instant.parse("2026-05-29T00:00:00Z").plusMillis(seq),
                payload);
    }
}

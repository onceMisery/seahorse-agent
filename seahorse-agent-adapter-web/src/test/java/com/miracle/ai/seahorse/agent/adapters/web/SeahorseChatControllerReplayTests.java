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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunMessageSnapshot;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshot;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseChatControllerReplayTests {

    @Test
    void resumeWithBufferHitShouldReplayMissedEvents() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);
        AgentRunSnapshotInboundPort snapshotPort = mock(AgentRunSnapshotInboundPort.class);
        AgentRunEventBufferPort eventBufferPort = mock(AgentRunEventBufferPort.class);

        StreamEventEnvelope e1 = StreamEventEnvelope.of(2, StreamEventType.MESSAGE, "run-1",
                Map.of("type", "response", "delta", "hello"));
        StreamEventEnvelope e2 = StreamEventEnvelope.of(3, StreamEventType.STEP_STARTED, "run-1",
                Map.of("stepId", "step-1", "title", "Search"));
        when(eventBufferPort.getAfter("run-1", 1L)).thenReturn(List.of(e1, e2));

        MockMvc mvc = buildMvc(chatPort, streamTaskPort, snapshotPort, eventBufferPort);

        MvcResult result = mvc.perform(get("/rag/v3/chat")
                        .param("resumeRunId", "run-1")
                        .param("lastEventSeq", "1")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(1_000L);
        String body = result.getResponse().getContentAsString();

        assertThat(body).contains("event:stream_event");
        assertThat(body).contains("\"eventSeq\":2");
        assertThat(body).contains("\"eventSeq\":3");
        assertThat(body).contains("event:done");
        verifyNoInteractions(snapshotPort);
        verifyNoInteractions(chatPort);
    }

    @Test
    void resumeWithBufferHitShouldContinueStreamingAfterMissedEvents() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);
        AgentRunSnapshotInboundPort snapshotPort = mock(AgentRunSnapshotInboundPort.class);
        AgentRunEventBufferPort eventBufferPort = mock(AgentRunEventBufferPort.class);
        ResearchSseBridge bridge = mock(ResearchSseBridge.class);

        StreamEventEnvelope missed = StreamEventEnvelope.of(2, StreamEventType.MESSAGE, "run-1",
                Map.of("type", "response", "delta", "missed"));
        StreamEventEnvelope live = StreamEventEnvelope.of(3, StreamEventType.MESSAGE, "run-1",
                Map.of("type", "response", "delta", " live"));
        when(eventBufferPort.getAfter("run-1", 1L)).thenReturn(List.of(missed));
        doAnswer(invocation -> {
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = invocation.getArgument(0);
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("stream_event")
                    .data(live));
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name(StreamEventType.DONE.value())
                    .data("[DONE]"));
            emitter.complete();
            return null;
        }).when(bridge).attach(any(), eq("run-1"), isNull(), isNull(), eq(2L));

        MockMvc mvc = buildMvc(chatPort, streamTaskPort, snapshotPort, eventBufferPort, bridge);

        MvcResult result = mvc.perform(get("/rag/v3/chat")
                        .param("resumeRunId", "run-1")
                        .param("lastEventSeq", "1")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(1_000L);
        String body = result.getResponse().getContentAsString();

        assertThat(body).contains("\"eventSeq\":2");
        assertThat(body).contains("\"eventSeq\":3");
        assertThat(body).contains("event:done");
        verify(bridge).attach(any(), eq("run-1"), isNull(), isNull(), eq(2L));
        verifyNoInteractions(snapshotPort);
        verifyNoInteractions(chatPort);
    }

    @Test
    void resumeWithExpiredBufferShouldFallbackToSnapshot() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);
        AgentRunSnapshotInboundPort snapshotPort = mock(AgentRunSnapshotInboundPort.class);
        AgentRunEventBufferPort eventBufferPort = mock(AgentRunEventBufferPort.class);

        when(eventBufferPort.getAfter("run-1", 100L)).thenReturn(List.of());
        when(snapshotPort.getSnapshot("run-1")).thenReturn(snapshot());

        MockMvc mvc = buildMvc(chatPort, streamTaskPort, snapshotPort, eventBufferPort);

        MvcResult result = mvc.perform(get("/rag/v3/chat")
                        .param("resumeRunId", "run-1")
                        .param("lastEventSeq", "100")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(1_000L);
        String body = result.getResponse().getContentAsString();

        assertThat(body).contains("event:run_snapshot");
        assertThat(body).contains("\"runId\":\"run-1\"");
        assertThat(body).contains("event:done");
        verify(snapshotPort).getSnapshot("run-1");
    }

    @Test
    void resumeWithExpiredBufferShouldContinueStreamingWhenSnapshotCanResume() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);
        AgentRunSnapshotInboundPort snapshotPort = mock(AgentRunSnapshotInboundPort.class);
        AgentRunEventBufferPort eventBufferPort = mock(AgentRunEventBufferPort.class);
        ResearchSseBridge bridge = mock(ResearchSseBridge.class);

        StreamEventEnvelope live = StreamEventEnvelope.of(6, StreamEventType.MESSAGE, "run-1",
                Map.of("type", "response", "delta", " live"));
        when(eventBufferPort.getAfter("run-1", 100L)).thenReturn(List.of());
        when(snapshotPort.getSnapshot("run-1")).thenReturn(snapshot());
        doAnswer(invocation -> {
            org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = invocation.getArgument(0);
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("stream_event")
                    .data(live));
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name(StreamEventType.DONE.value())
                    .data("[DONE]"));
            emitter.complete();
            return null;
        }).when(bridge).attach(any(), eq("run-1"), isNull(), isNull(), eq(5L));

        MockMvc mvc = buildMvc(chatPort, streamTaskPort, snapshotPort, eventBufferPort, bridge);

        MvcResult result = mvc.perform(get("/rag/v3/chat")
                        .param("resumeRunId", "run-1")
                        .param("lastEventSeq", "100")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(1_000L);
        String body = result.getResponse().getContentAsString();

        assertThat(body).contains("event:run_snapshot");
        assertThat(body).contains("\"runId\":\"run-1\"");
        assertThat(body).contains("\"eventSeq\":6");
        assertThat(body).contains("event:done");
        verify(snapshotPort).getSnapshot("run-1");
        verify(bridge).attach(any(), eq("run-1"), isNull(), isNull(), eq(5L));
    }

    @Test
    void resumeWithoutLastEventSeqShouldReturnSnapshot() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);
        AgentRunSnapshotInboundPort snapshotPort = mock(AgentRunSnapshotInboundPort.class);
        AgentRunEventBufferPort eventBufferPort = mock(AgentRunEventBufferPort.class);

        when(snapshotPort.getSnapshot("run-1")).thenReturn(snapshot());

        MockMvc mvc = buildMvc(chatPort, streamTaskPort, snapshotPort, eventBufferPort);

        MvcResult result = mvc.perform(get("/rag/v3/chat")
                        .param("resumeRunId", "run-1")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(1_000L);
        String body = result.getResponse().getContentAsString();

        assertThat(body).contains("event:run_snapshot");
        assertThat(body).contains("event:done");
        verifyNoInteractions(eventBufferPort);
        verify(snapshotPort).getSnapshot("run-1");
    }

    private MockMvc buildMvc(ChatInboundPort chatPort,
                             StreamTaskPort streamTaskPort,
                             AgentRunSnapshotInboundPort snapshotPort,
                             AgentRunEventBufferPort eventBufferPort) {
        return buildMvc(chatPort, streamTaskPort, snapshotPort, eventBufferPort, null);
    }

    private MockMvc buildMvc(ChatInboundPort chatPort,
                             StreamTaskPort streamTaskPort,
                             AgentRunSnapshotInboundPort snapshotPort,
                             AgentRunEventBufferPort eventBufferPort,
                             ResearchSseBridge researchSseBridge) {
        SeahorseChatController controller = new SeahorseChatController(
                provider(ChatInboundPort.class, chatPort),
                (emitter, conversationId, taskId) -> new NoopStreamCallback(),
                streamTaskPort,
                provider(AgentRunSnapshotInboundPort.class, snapshotPort),
                provider(com.miracle.ai.seahorse.agent.ports.inbound.agent.ResearchInboundPort.class, null),
                provider(ResearchSseBridge.class, researchSseBridge),
                provider(com.miracle.ai.seahorse.agent.ports.outbound.cache.RateLimiterPort.class, null),
                provider(AgentRunEventBufferPort.class, eventBufferPort),
                provider(AdvancedFeatureGate.class, null),
                1_000L,
                60,
                60000L);
        return MockMvcBuilders.standaloneSetup(controller).build();
    }

    private static AgentRunSnapshot snapshot() {
        return new AgentRunSnapshot(
                new AgentRun(
                        "run-1", "agent-1", "version-1", "tenant-a", "user-1",
                        "conversation-1", AgentRunTriggerType.CHAT, "summary",
                        AgentRunStatus.RUNNING, "trace-1", 0L, 0L, BigDecimal.ZERO,
                        null, null, Instant.EPOCH, null),
                List.of(),
                Optional.empty(),
                new AgentRunMessageSnapshot(null, "partial", null),
                null, List.of(), List.of(), List.of(), 5L, true, false);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getName(), bean);
        }
        return factory.getBeanProvider(type);
    }

    private static final class NoopStreamCallback
            implements com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback {
        @Override public void onContent(String content) {}
        @Override public void onComplete() {}
        @Override public void onError(Throwable error) {}
    }
}

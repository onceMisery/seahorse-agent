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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunMessageSnapshot;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshot;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SeahorseChatControllerTests {

    @Test
    void shouldPassTaskTemplateIdIntoStreamChatCommand() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseChatController(
                        provider(ChatInboundPort.class, chatPort),
                        (emitter, conversationId, taskId) -> new NoopStreamCallback(),
                        streamTaskPort,
                        1_000L,
                        provider(AgentRunSnapshotInboundPort.class, null))).build();

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "Research this")
                        .param("conversationId", "conversation-1")
                        .param("userId", "user-1")
                        .param("taskTemplateId", "quick-answer"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        ArgumentCaptor<StreamChatCommand> captor = ArgumentCaptor.forClass(StreamChatCommand.class);
        verify(chatPort).streamChat(captor.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(captor.getValue().taskTemplateId()).isEqualTo("quick-answer");
        assertThat(captor.getValue().chatMode()).isEqualTo(ChatMode.RAG);
    }

    @Test
    void defaultAgentTemplateShouldEnterAgentModeWithoutExplicitAgentSelection() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseChatController(
                        provider(ChatInboundPort.class, chatPort),
                        (emitter, conversationId, taskId) -> new NoopStreamCallback(),
                        streamTaskPort,
                        1_000L,
                        provider(AgentRunSnapshotInboundPort.class, null))).build();

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "Create a visual GitHub project intro")
                        .param("conversationId", "conversation-1")
                        .param("userId", "user-1")
                        .param("taskTemplateId", "github-visual-project-intro"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        ArgumentCaptor<StreamChatCommand> captor = ArgumentCaptor.forClass(StreamChatCommand.class);
        verify(chatPort).streamChat(captor.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(captor.getValue().taskTemplateId()).isEqualTo("github-visual-project-intro");
        assertThat(captor.getValue().chatMode()).isEqualTo(ChatMode.AGENT);
        assertThat(captor.getValue().agentId()).isNull();
    }

    @Test
    void shouldPassAttachmentIdsIntoStreamChatCommand() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseChatController(
                        provider(ChatInboundPort.class, chatPort),
                        (emitter, conversationId, taskId) -> new NoopStreamCallback(),
                        streamTaskPort,
                        1_000L,
                        provider(AgentRunSnapshotInboundPort.class, null))).build();

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "Read these")
                        .param("conversationId", "conversation-1")
                        .param("userId", "user-1")
                        .param("attachmentIds", "attachment-1", "attachment-2"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        ArgumentCaptor<StreamChatCommand> captor = ArgumentCaptor.forClass(StreamChatCommand.class);
        verify(chatPort).streamChat(captor.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(captor.getValue().attachmentIds()).containsExactly("attachment-1", "attachment-2");
    }

    @Test
    void shouldPassKnowledgeBaseIdsIntoStreamChatCommand() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseChatController(
                        provider(ChatInboundPort.class, chatPort),
                        (emitter, conversationId, taskId) -> new NoopStreamCallback(),
                        streamTaskPort,
                        1_000L,
                        provider(AgentRunSnapshotInboundPort.class, null))).build();

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "Search this knowledge base")
                        .param("conversationId", "conversation-1")
                        .param("userId", "user-1")
                        .param("knowledgeBaseIds", "42", " 43 ", "42"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        ArgumentCaptor<StreamChatCommand> captor = ArgumentCaptor.forClass(StreamChatCommand.class);
        verify(chatPort).streamChat(captor.capture(), org.mockito.ArgumentMatchers.any());
        assertThat(captor.getValue().knowledgeBaseIds()).containsExactly("42", "43");
    }

    @Test
    void resumeRunShouldEmitSnapshotWithoutStartingNewChat() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);
        AgentRunSnapshotInboundPort snapshotPort = mock(AgentRunSnapshotInboundPort.class);
        when(snapshotPort.getSnapshot("run-1")).thenReturn(snapshot());

        MockMvc mvc = MockMvcBuilders.standaloneSetup(
                new SeahorseChatController(
                        provider(ChatInboundPort.class, chatPort),
                        (emitter, conversationId, taskId) -> {
                            throw new AssertionError("resume must not create a chat callback");
                        },
                        streamTaskPort,
                        1_000L,
                        provider(AgentRunSnapshotInboundPort.class, snapshotPort))).build();

        MvcResult result = mvc.perform(get("/rag/v3/chat")
                        .param("resumeRunId", "run-1")
                        .param("lastEventSeq", "1")
                        .param("userId", "user-1"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        result.getAsyncResult(1_000L);
        String body = result.getResponse().getContentAsString();

        assertThat(body).contains("event:run_snapshot");
        assertThat(body).contains("\"runId\":\"run-1\"");
        assertThat(body).contains("\"messageSnapshot\"");
        assertThat(body).contains("\"content\":\"partial\"");
        assertThat(body).contains("event:done");
        verify(snapshotPort).getSnapshot("run-1");
        verifyNoInteractions(chatPort, streamTaskPort);
    }

    private static AgentRunSnapshot snapshot() {
        return new AgentRunSnapshot(
                new AgentRun(
                        "run-1",
                        "agent-1",
                        "version-1",
                        "tenant-a",
                        "user-1",
                        "conversation-1",
                        AgentRunTriggerType.CHAT,
                        "summary",
                        AgentRunStatus.WAITING_APPROVAL,
                        "trace-1",
                        0L,
                        0L,
                        BigDecimal.ZERO,
                        null,
                        null,
                        Instant.EPOCH,
                        null),
                List.of(),
                Optional.empty(),
                new AgentRunMessageSnapshot(null, "partial", "thinking"),
                null,
                List.of(),
                List.of(),
                List.of(),
                1L,
                true,
                false);
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getName(), bean);
        }
        return factory.getBeanProvider(type);
    }

    private static final class NoopStreamCallback implements com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback {

        @Override
        public void onContent(String content) {
        }

        @Override
        public void onComplete() {
        }

        @Override
        public void onError(Throwable error) {
        }
    }
}

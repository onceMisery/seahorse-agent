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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.ChatInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class A2ATriggerRejectedInConsumerWebTests {

    @Test
    void rawAgentModeShouldBeRejectedInConsumerWeb() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);
        MockMvc mvc = mvc(chatPort, streamTaskPort);

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "Run as remote agent")
                        .param("conversationId", "conversation-1")
                        .param("userId", "user-1")
                        .param("chatMode", "agent"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message")
                        .value("Demo mode chat only allows controlled Agent task templates without explicit agentId/versionId"));

        verifyNoInteractions(chatPort, streamTaskPort);
    }

    @Test
    void controlledWebTemplateShouldRejectCustomAgentSelectionInConsumerWeb() throws Exception {
        ChatInboundPort chatPort = mock(ChatInboundPort.class);
        StreamTaskPort streamTaskPort = mock(StreamTaskPort.class);
        MockMvc mvc = mvc(chatPort, streamTaskPort);

        mvc.perform(get("/rag/v3/chat")
                        .param("question", "Research this")
                        .param("conversationId", "conversation-1")
                        .param("userId", "user-1")
                        .param("taskTemplateId", "deep-research")
                        .param("agentId", "remote-agent-1"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message")
                        .value("Demo mode chat only allows controlled Agent task templates without explicit agentId/versionId"));

        verifyNoInteractions(chatPort, streamTaskPort);
    }

    private static MockMvc mvc(ChatInboundPort chatPort, StreamTaskPort streamTaskPort) {
        return MockMvcBuilders.standaloneSetup(
                        new SeahorseChatController(
                                provider(ChatInboundPort.class, chatPort),
                                (emitter, conversationId, taskId) -> new NoopStreamCallback(),
                                streamTaskPort,
                                1_000L,
                                provider(AgentRunSnapshotInboundPort.class, null)))
                .setControllerAdvice(new SeahorseWebExceptionHandler())
                .build();
    }

    private static <T> ObjectProvider<T> provider(Class<T> type, T bean) {
        StaticListableBeanFactory factory = new StaticListableBeanFactory();
        if (bean != null) {
            factory.addBean(type.getName(), bean);
        }
        return factory.getBeanProvider(type);
    }

    private static final class NoopStreamCallback implements StreamCallback {

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

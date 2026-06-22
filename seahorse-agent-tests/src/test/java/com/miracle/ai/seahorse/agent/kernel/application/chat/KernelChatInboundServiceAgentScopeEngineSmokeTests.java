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

import com.miracle.ai.seahorse.agent.adapters.agent.agentscope.AgentScopeAgentClient;
import com.miracle.ai.seahorse.agent.adapters.agent.agentscope.AgentScopeReActExecutor;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KernelChatInboundServiceAgentScopeEngineSmokeTests {

    @Test
    void shouldRouteAgentModeThroughAgentScopeExecutorAndKeepChatContract() {
        KernelChatPipeline pipeline = mock(KernelChatPipeline.class);
        StreamTaskPort taskPort = mock(StreamTaskPort.class);
        MemoryEnginePort memoryEnginePort = mock(MemoryEnginePort.class);
        RecordingAgentScopeClient client = new RecordingAgentScopeClient();
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(
                client,
                Runnable::run);
        RecordingStreamCallback callback = new RecordingStreamCallback();
        when(memoryEnginePort.loadMemory(any(MemoryLoadRequest.class))).thenReturn(MemoryContext.builder().build());
        KernelChatInboundService service = new KernelChatInboundService(
                pipeline,
                taskPort,
                Optional.of(executor),
                KernelRagTraceRecorder.noop(),
                null,
                memoryEnginePort);

        service.streamChat(new StreamChatCommand(
                "hello agentscope", "conv-1", "task-1", "user-1", false, ChatMode.AGENT), callback);

        assertThat(executor.engineId()).isEqualTo("agentscope");
        assertThat(client.lastRequest).isNotNull();
        assertThat(client.lastRequest.question()).isEqualTo("hello agentscope");
        assertThat(client.lastRequest.memoryContext().getConversationId()).isEqualTo("conv-1");
        assertThat(client.lastRequest.memoryContext().getUserId()).isEqualTo("user-1");
        assertThat(client.lastMessages)
                .extracting(Msg::getTextContent)
                .containsExactly("hello agentscope");
        assertThat(callback.contents).containsExactly("agentscope answer");
        assertThat(callback.completed).isTrue();
        assertThat(callback.errors).isEmpty();
        verify(taskPort).bindHandle(any(), any());
        verify(pipeline, never()).execute(any());
    }

    private static final class RecordingAgentScopeClient implements AgentScopeAgentClient {

        private AgentLoopRequest lastRequest;
        private List<Msg> lastMessages = List.of();

        @Override
        public Msg call(AgentLoopRequest request, List<Msg> messages) {
            lastRequest = request;
            lastMessages = List.copyOf(messages);
            return Msg.builder()
                    .role(MsgRole.ASSISTANT)
                    .textContent("agentscope answer")
                    .build();
        }
    }

    private static final class RecordingStreamCallback implements StreamCallback {

        private final List<String> contents = new ArrayList<>();
        private final List<Throwable> errors = new ArrayList<>();
        private boolean completed;

        @Override
        public void onContent(String content) {
            contents.add(content);
        }

        @Override
        public void onComplete() {
            completed = true;
        }

        @Override
        public void onError(Throwable error) {
            errors.add(error);
        }
    }
}

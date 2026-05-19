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

import com.miracle.ai.seahorse.agent.kernel.application.agent.KernelAgentLoop;
import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMode;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Task A12 契约测试：KernelChatInboundService 按 chatMode 路由到 RAG 或 AgentLoop。
 */
class KernelChatInboundServiceAgentModeTests {

    @Test
    void shouldRouteAgentModeToKernelAgentLoopWithoutExecutingRagPipeline() {
        KernelChatPipeline pipeline = mock(KernelChatPipeline.class);
        StreamTaskPort taskPort = mock(StreamTaskPort.class);
        KernelAgentLoop agentLoop = mock(KernelAgentLoop.class);
        StreamCallback callback = mock(StreamCallback.class);
        when(agentLoop.streamExecute(any(), any())).thenReturn(() -> {
        });
        KernelChatInboundService service = new KernelChatInboundService(
                pipeline, taskPort, Optional.of(agentLoop), KernelRagTraceRecorder.noop());

        service.streamChat(new StreamChatCommand(
                "hello", "conv-1", "task-1", "user-1", false, ChatMode.AGENT), callback);

        ArgumentCaptor<AgentLoopRequest> requestCaptor = ArgumentCaptor.forClass(AgentLoopRequest.class);
        verify(agentLoop).streamExecute(requestCaptor.capture(), any());
        verify(pipeline, never()).execute(any());
        AgentLoopRequest request = requestCaptor.getValue();
        assertThat(request.question()).isEqualTo("hello");
        assertThat(request.history()).isEmpty();
        assertThat(request.allowedToolIds()).isEmpty();
        assertThat(request.samplingOptions().getTemperature()).isEqualTo(0.3D);
    }

    @Test
    void shouldFallbackToRagWhenAgentModeIsRequestedButLoopIsMissing() {
        KernelChatPipeline pipeline = mock(KernelChatPipeline.class);
        StreamTaskPort taskPort = mock(StreamTaskPort.class);
        StreamCallback callback = mock(StreamCallback.class);
        KernelChatInboundService service = new KernelChatInboundService(
                pipeline, taskPort, Optional.empty(), KernelRagTraceRecorder.noop());

        service.streamChat(new StreamChatCommand(
                "hello", "conv-1", "task-1", "user-1", false, ChatMode.AGENT), callback);

        verify(pipeline).execute(any());
        verify(callback, never()).onError(any());
    }
}

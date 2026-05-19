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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.GuidanceDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.RewriteResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.IntentGroup;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentGuidancePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.IntentResolutionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.PromptTemplatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.QueryRewritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RagPromptPort;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.RetrievalContextPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.stream.StreamTaskPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KernelChatInboundServiceTests {

    @Test
    void shouldFallbackToGenericChatWhenRetrievalIsEmpty() {
        ConversationMemoryPort memoryPort = mock(ConversationMemoryPort.class);
        QueryRewritePort rewritePort = mock(QueryRewritePort.class);
        IntentResolutionPort intentPort = mock(IntentResolutionPort.class);
        IntentGuidancePort guidancePort = mock(IntentGuidancePort.class);
        RetrievalContextPort retrievalPort = mock(RetrievalContextPort.class);
        StreamTaskPort taskPort = mock(StreamTaskPort.class);
        StreamingChatModelPort streamingModel = mock(StreamingChatModelPort.class);
        StreamCallback callback = mock(StreamCallback.class);
        KernelChatPipeline pipeline = new KernelChatPipeline(
                new ChatPreparationPorts(memoryPort, rewritePort, intentPort, guidancePort, retrievalPort),
                new ChatResponsePorts(
                        RagPromptPort.simple(),
                        PromptTemplatePort.empty(),
                        streamingModel,
                        taskPort));

        when(memoryPort.loadAndAppend(any(), any(), any())).thenReturn(List.of(ChatMessage.user("hello")));
        when(rewritePort.rewriteWithSplit(any(), any())).thenReturn(new RewriteResult("hello", List.of()));
        when(intentPort.resolve(any())).thenReturn(List.of());
        when(intentPort.mergeIntentGroup(any())).thenReturn(new IntentGroup(List.of(), List.of()));
        when(guidancePort.detectAmbiguity(any(), any())).thenReturn(GuidanceDecision.none());
        when(streamingModel.streamChat(any(), any())).thenReturn(() -> {
        });

        KernelChatInboundService service = new KernelChatInboundService(pipeline, taskPort);

        service.streamChat(new StreamChatCommand("hello", "conv-1", "task-1", "user-1", false), callback);

        verify(memoryPort).loadAndAppend("conv-1", "user-1", ChatMessage.user("hello"));
        // 空检索默认走 FALLBACK_GENERIC：调用流式模型而不是返回固定文案
        verify(streamingModel).streamChat(any(), any());
        verify(callback, org.mockito.Mockito.never()).onContent("未检索到与问题相关的文档内容。");
    }

    @Test
    void shouldReturnStaticMessageWhenStrictEmptyRetrievalStrategy() {
        ConversationMemoryPort memoryPort = mock(ConversationMemoryPort.class);
        QueryRewritePort rewritePort = mock(QueryRewritePort.class);
        IntentResolutionPort intentPort = mock(IntentResolutionPort.class);
        IntentGuidancePort guidancePort = mock(IntentGuidancePort.class);
        RetrievalContextPort retrievalPort = mock(RetrievalContextPort.class);
        StreamTaskPort taskPort = mock(StreamTaskPort.class);
        StreamCallback callback = mock(StreamCallback.class);
        KernelChatPipeline pipeline = new KernelChatPipeline(
                new ChatPreparationPorts(memoryPort, rewritePort, intentPort, guidancePort, retrievalPort),
                new ChatResponsePorts(
                        RagPromptPort.simple(),
                        PromptTemplatePort.empty(),
                        StreamingChatModelPort.noop(),
                        taskPort),
                com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder.noop(),
                KernelChatPipeline.EmptyRetrievalStrategy.STATIC_MESSAGE);

        when(memoryPort.loadAndAppend(any(), any(), any())).thenReturn(List.of(ChatMessage.user("hello")));
        when(rewritePort.rewriteWithSplit(any(), any())).thenReturn(new RewriteResult("hello", List.of()));
        when(intentPort.resolve(any())).thenReturn(List.of());
        when(intentPort.mergeIntentGroup(any())).thenReturn(new IntentGroup(List.of(), List.of()));
        when(guidancePort.detectAmbiguity(any(), any())).thenReturn(GuidanceDecision.none());

        KernelChatInboundService service = new KernelChatInboundService(pipeline, taskPort);

        service.streamChat(new StreamChatCommand("hello", "conv-1", "task-1", "user-1", false), callback);

        verify(callback).onContent("未检索到与问题相关的文档内容。");
        verify(callback).onComplete();
    }

    @Test
    void shouldDelegateStopToStreamTaskPort() {
        KernelChatPipeline pipeline = mock(KernelChatPipeline.class);
        StreamTaskPort taskPort = mock(StreamTaskPort.class);
        KernelChatInboundService service = new KernelChatInboundService(pipeline, taskPort);

        service.stopTask("task-1");

        verify(taskPort).cancel("task-1");
    }

    @Test
    void shouldRejectBlankQuestionCommand() {
        Assertions.assertThrows(IllegalArgumentException.class,
                () -> new StreamChatCommand("", "conv-1", "task-1", "user-1", false));
    }
}

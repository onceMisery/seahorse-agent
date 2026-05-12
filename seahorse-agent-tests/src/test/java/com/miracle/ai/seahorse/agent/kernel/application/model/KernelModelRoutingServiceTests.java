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

package com.miracle.ai.seahorse.agent.kernel.application.model;

import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.EmbeddingModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ModelProviderPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.RerankModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 模型路由内核门面契约测试。
 */
class KernelModelRoutingServiceTests {

    @Test
    void shouldDelegateSyncAndStreamingCallsToPorts() {
        ChatModelPort chatModelPort = mock(ChatModelPort.class);
        StreamingChatModelPort streamingChatModelPort = mock(StreamingChatModelPort.class);
        ChatRequest request = ChatRequest.builder().build();
        StreamCallback callback = mock(StreamCallback.class);
        StreamCancellationHandle handle = mock(StreamCancellationHandle.class);
        when(chatModelPort.chat(request, "model-a")).thenReturn("ok");
        when(streamingChatModelPort.streamChat(request, callback)).thenReturn(handle);
        KernelModelRoutingService service = new KernelModelRoutingService(chatModelPort, streamingChatModelPort);

        String response = service.chat(request, "model-a");
        StreamCancellationHandle actualHandle = service.streamChat(request, callback);

        Assertions.assertEquals("ok", response);
        Assertions.assertSame(handle, actualHandle);
        verify(chatModelPort).chat(request, "model-a");
        verify(streamingChatModelPort).streamChat(request, callback);
    }

    @Test
    void shouldExposeProviderEmbeddingAndRerankPorts() {
        ChatModelPort chatModelPort = mock(ChatModelPort.class);
        StreamingChatModelPort streamingChatModelPort = mock(StreamingChatModelPort.class);
        ModelProviderPort providerPort = mock(ModelProviderPort.class);
        EmbeddingModelPort embeddingPort = mock(EmbeddingModelPort.class);
        RerankModelPort rerankPort = mock(RerankModelPort.class);
        List<RetrievedChunk> chunks = List.of(RetrievedChunk.builder().id("chunk-1").build());
        when(providerPort.available("embed-a")).thenReturn(true);
        when(providerPort.listModels("embedding")).thenReturn(List.of("embed-a"));
        when(embeddingPort.embed("embed-a", "hello")).thenReturn(List.of(0.1F, 0.2F));
        when(rerankPort.rerank("rerank-a", "hello", chunks)).thenReturn(chunks);
        KernelModelRoutingService service = new KernelModelRoutingService(
                chatModelPort, streamingChatModelPort, providerPort, embeddingPort, rerankPort);

        Assertions.assertTrue(service.available("embed-a"));
        Assertions.assertEquals(List.of("embed-a"), service.listModels("embedding"));
        Assertions.assertEquals(List.of(0.1F, 0.2F), service.embed("embed-a", "hello"));
        Assertions.assertSame(chunks, service.rerank("rerank-a", "hello", chunks));
    }
}

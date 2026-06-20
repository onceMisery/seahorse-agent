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

package com.miracle.ai.seahorse.agent.adapters.agent.agentscope;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import io.agentscope.core.message.ContentBlock;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.ToolUseBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentScopeModelBridgeTests {

    @Test
    void requestScopedBridgeUsesAgentLoopModelIdAndSamplingOptions() {
        CapturingStreamingModelPort modelPort = new CapturingStreamingModelPort();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("draft")
                .modelId("qwen-plus")
                .samplingOptions(ChatSamplingOptions.builder()
                        .temperature(0.2D)
                        .topP(0.8D)
                        .topK(40)
                        .maxTokens(512)
                        .thinking(true)
                        .build())
                .build();

        new AgentScopeModelBridge(modelPort, "fallback-model")
                .forRequest(request)
                .stream(List.of(Msg.builder().role(MsgRole.USER).textContent("draft").build()), List.of(), null)
                .blockLast();

        ChatRequest captured = modelPort.request.get();
        assertEquals("qwen-plus", captured.getModelId());
        assertEquals(0.2D, captured.getTemperature());
        assertEquals(0.8D, captured.getTopP());
        assertEquals(40, captured.getTopK());
        assertEquals(512, captured.getMaxTokens());
        assertEquals(true, captured.getThinking());
    }

    @Test
    void generateOptionsOverrideRequestScopedSamplingWhenPresent() {
        CapturingStreamingModelPort modelPort = new CapturingStreamingModelPort();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("draft")
                .modelId("qwen-plus")
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.2D).build())
                .build();
        GenerateOptions options = GenerateOptions.builder()
                .modelName("deepseek-chat")
                .temperature(0.7D)
                .topP(0.9D)
                .topK(20)
                .maxTokens(128)
                .build();

        new AgentScopeModelBridge(modelPort, "fallback-model")
                .forRequest(request)
                .stream(List.of(Msg.builder().role(MsgRole.USER).textContent("draft").build()), List.of(), options)
                .blockLast();

        ChatRequest captured = modelPort.request.get();
        assertEquals("deepseek-chat", captured.getModelId());
        assertEquals(0.7D, captured.getTemperature());
        assertEquals(0.9D, captured.getTopP());
        assertEquals(20, captured.getTopK());
        assertEquals(128, captured.getMaxTokens());
    }

    @Test
    void forwardsAgentscopeToolSchemasToSeahorseChatRequest() {
        CapturingStreamingModelPort modelPort = new CapturingStreamingModelPort();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("draft")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();
        LinkedHashMap<String, Object> parameters = new LinkedHashMap<>();
        parameters.put("type", "object");
        parameters.put("properties", new LinkedHashMap<>(java.util.Map.of(
                "city", java.util.Map.of("type", "string"))));

        new AgentScopeModelBridge(modelPort, "fallback-model")
                .forRequest(request)
                .stream(List.of(Msg.builder().role(MsgRole.USER).textContent("draft").build()),
                        List.of(ToolSchema.builder()
                                .name("weather")
                                .description("Get weather")
                                .parameters(parameters)
                                .build()),
                        null)
                .blockLast();

        ChatRequest captured = modelPort.request.get();
        assertEquals(1, modelPort.streamChatWithToolsCalls.get());
        assertEquals("auto", captured.getToolChoice());
        assertEquals(1, captured.getTools().size());
        ToolDescriptor tool = captured.getTools().get(0);
        assertEquals("weather", tool.toolId());
        assertEquals("Get weather", tool.description());
        assertEquals("{\"type\":\"object\",\"properties\":{\"city\":{\"type\":\"string\"}}}", tool.jsonSchema());
    }

    @Test
    void convertsSeahorseToolCallsToAgentscopeToolUseBlocks() {
        CapturingStreamingModelPort modelPort = new CapturingStreamingModelPort();
        modelPort.toolCalls.set(List.of(AgentToolCall.of("call-1", "weather", Map.of("city", "Hangzhou"))));
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("draft")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();

        List<ChatResponse> responses = new AgentScopeModelBridge(modelPort, "fallback-model")
                .forRequest(request)
                .stream(List.of(Msg.builder().role(MsgRole.USER).textContent("draft").build()),
                        List.of(ToolSchema.builder()
                                .name("weather")
                                .description("Get weather")
                                .parameters(Map.of("type", "object"))
                                .build()),
                        null)
                .collectList()
                .block();

        assertEquals(1, modelPort.streamChatWithToolsCalls.get());
        assertEquals(1, responses.size());
        ContentBlock block = responses.get(0).getContent().get(0);
        ToolUseBlock toolUse = (ToolUseBlock) block;
        assertEquals("call-1", toolUse.getId());
        assertEquals("weather", toolUse.getName());
        assertEquals("Hangzhou", toolUse.getInput().get("city"));
    }

    private static final class CapturingStreamingModelPort implements StreamingChatModelPort {
        private final AtomicReference<ChatRequest> request = new AtomicReference<>();
        private final AtomicReference<List<AgentToolCall>> toolCalls = new AtomicReference<>(List.of());
        private final AtomicInteger streamChatWithToolsCalls = new AtomicInteger();

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            this.request.set(request);
            callback.onContent("ok");
            callback.onComplete();
            return () -> { };
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            streamChatWithToolsCalls.incrementAndGet();
            this.request.set(request);
            toolCallCollector.onToolCalls(toolCalls.get());
            callback.onComplete();
            return () -> { };
        }
    }
}

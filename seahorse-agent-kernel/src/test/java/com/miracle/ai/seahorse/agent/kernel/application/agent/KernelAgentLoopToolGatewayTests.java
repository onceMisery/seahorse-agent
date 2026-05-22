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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentToolCall;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolInvocationRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolGatewayPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KernelAgentLoopToolGatewayTests {

    @Test
    void shouldInvokeToolsThroughGatewayWithRunContext() {
        AgentToolCall toolCall = AgentToolCall.of("call-1", "weather", Map.of("city", "Shanghai"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("need weather", List.of(toolCall)),
                Turn.finalAnswer("done")));
        RecordingToolGateway gateway = new RecordingToolGateway();
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("weather?")
                .allowedToolIds(List.of("weather"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .memoryContext(MemoryContext.builder()
                        .conversationId("conversation-1")
                        .userId("user-1")
                        .currentQuestion("weather?")
                        .build())
                .runId("run-1")
                .build());

        assertEquals("done", result.finalAnswer());
        assertEquals(1, gateway.requests.size());
        ToolInvocationRequest request = gateway.requests.get(0);
        assertEquals("run-1", request.runId());
        assertEquals("call-1", request.toolCallId());
        assertEquals("call-1", request.stepId());
        assertEquals("weather", request.toolId());
        assertEquals("Shanghai", request.arguments().get("city"));
        assertEquals("user-1", request.userId());
        assertEquals("conversation-1", request.arguments().get("_seahorseConversationId"));
        assertEquals(List.of("weather"), request.allowedToolIds());
    }

    @Test
    void shouldSendHallucinatedDisallowedToolCallsThroughGatewayPolicyBoundary() {
        AgentToolCall toolCall = AgentToolCall.of("call-2", "delete-memory", Map.of("memoryId", "mem-1"));
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("try delete", List.of(toolCall)),
                Turn.finalAnswer("blocked")));
        RecordingToolGateway gateway = new RecordingToolGateway(ToolInvocationResult.failed("TOOL_NOT_BOUND"));
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                new ListingOnlyToolRegistry(),
                gateway,
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(AgentLoopRequest.builder()
                .question("delete memory")
                .allowedToolIds(List.of("weather"))
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.1D).build())
                .runId("run-2")
                .build());

        assertEquals("blocked", result.finalAnswer());
        assertEquals(1, gateway.requests.size());
        ToolInvocationRequest request = gateway.requests.get(0);
        assertEquals("run-2", request.runId());
        assertEquals("delete-memory", request.toolId());
        assertEquals(List.of("weather"), request.allowedToolIds());
        assertEquals("TOOL_NOT_BOUND", result.steps().get(0).observations().get(0).error());
    }

    private static final class RecordingToolGateway implements ToolGatewayPort {
        private final List<ToolInvocationRequest> requests = new ArrayList<>();
        private final ToolInvocationResult result;

        private RecordingToolGateway() {
            this(ToolInvocationResult.ok("{\"temp\":21}"));
        }

        private RecordingToolGateway(ToolInvocationResult result) {
            this.result = result;
        }

        @Override
        public ToolInvocationResult invoke(ToolInvocationRequest request) {
            requests.add(request);
            return result;
        }
    }

    private static final class ListingOnlyToolRegistry implements ToolRegistryPort {
        @Override
        public List<ToolDescriptor> listTools() {
            return List.of(new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"));
        }

        @Override
        public Optional<ToolPort> find(String toolId) {
            throw new AssertionError("KernelAgentLoop must invoke tools through ToolGatewayPort");
        }
    }

    private static final class ScriptedModel implements StreamingChatModelPort {
        private final List<Turn> turns;
        private int index;

        private ScriptedModel(List<Turn> turns) {
            this.turns = turns;
        }

        @Override
        public StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public StreamCancellationHandle streamChatWithTools(
                ChatRequest request,
                StreamCallback callback,
                ToolCallCollector toolCallCollector) {
            Turn turn = turns.get(index++);
            assertFalse(request.getTools().isEmpty());
            callback.onContent(turn.content);
            toolCallCollector.onToolCalls(turn.toolCalls);
            callback.onComplete();
            return () -> {
            };
        }
    }

    private record Turn(String content, List<AgentToolCall> toolCalls) {

        private static Turn finalAnswer(String content) {
            return new Turn(content, List.of());
        }

        private static Turn toolCalls(String content, List<AgentToolCall> toolCalls) {
            return new Turn(content, toolCalls);
        }
    }
}

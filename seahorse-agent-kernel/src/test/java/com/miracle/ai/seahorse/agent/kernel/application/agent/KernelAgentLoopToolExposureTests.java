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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.policy.ToolPolicyReasonCodes;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import com.miracle.ai.seahorse.agent.ports.outbound.model.StreamingChatModelPort;
import com.miracle.ai.seahorse.agent.ports.outbound.model.ToolCallCollector;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelAgentLoopToolExposureTests {

    @Test
    void exhaustedToolIsNotExposedOnFollowingModelTurns() {
        AgentToolCall weather = AgentToolCall.of("call-1", "weather", Map.of());
        AgentToolCall repeatedWeather = AgentToolCall.of("call-2", "weather", Map.of());
        AgentToolCall calendar = AgentToolCall.of("call-3", "calendar", Map.of());
        ScriptedModel model = new ScriptedModel(List.of(
                Turn.toolCalls("weather exhausted", List.of(weather)),
                Turn.toolCalls("try exhausted weather again", List.of(repeatedWeather)),
                Turn.toolCalls("use remaining calendar", List.of(calendar)),
                Turn.finalAnswer("used remaining tools")));
        InMemoryToolRegistry registry = new InMemoryToolRegistry();
        registry.register(new ToolDescriptor("weather", "Weather", "Weather lookup", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("unused"));
        registry.register(new ToolDescriptor("calendar", "Calendar", "Calendar lookup", "{}"),
                (callId, toolId, arguments) -> ToolInvocationResult.ok("unused"));
        List<String> invokedToolIds = new ArrayList<>();
        KernelAgentLoop loop = new KernelAgentLoop(
                model,
                registry,
                request -> {
                    invokedToolIds.add(request.toolId());
                    if ("weather".equals(request.toolId())) {
                        return ToolInvocationResult.failed(ToolPolicyReasonCodes.TOOL_CALL_LIMIT_EXCEEDED);
                    }
                    return ToolInvocationResult.ok("ok");
                },
                KernelAgentLoopOptions.defaults());

        AgentLoopResult result = loop.execute(requestWithAllowedTools(6, "weather", "calendar"));

        assertEquals("used remaining tools", result.finalAnswer());
        assertEquals(List.of("weather", "calendar"), model.requests.get(0).getTools().stream()
                .map(ToolDescriptor::toolId)
                .toList());
        assertEquals(List.of("calendar"), model.requests.get(1).getTools().stream()
                .map(ToolDescriptor::toolId)
                .toList());
        assertEquals(List.of("calendar"), model.requests.get(2).getTools().stream()
                .map(ToolDescriptor::toolId)
                .toList());
        assertEquals(List.of("weather", "calendar"), invokedToolIds);
        String correction = model.requests.get(2).getMessages().get(model.requests.get(2).getMessages().size() - 1)
                .getContent();
        assertEquals(true, correction.contains("weather"));
        assertEquals(true, correction.contains("calendar"));
    }

    private static AgentLoopRequest requestWithAllowedTools(int maxSteps, String... toolIds) {
        return AgentLoopRequest.builder()
                .question("What should I do?")
                .samplingOptions(ChatSamplingOptions.builder().temperature(0.3D).build())
                .maxSteps(maxSteps)
                .allowedToolIds(List.of(toolIds))
                .build();
    }

    private static final class ScriptedModel implements StreamingChatModelPort {
        private final List<Turn> turns;
        private final List<ChatRequest> requests = new ArrayList<>();
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
            requests.add(request);
            Turn turn = turns.get(index++);
            callback.onContent(turn.content());
            toolCallCollector.onToolCalls(turn.toolCalls());
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

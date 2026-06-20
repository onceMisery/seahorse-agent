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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import io.agentscope.core.hook.Hook;
import io.agentscope.core.hook.HookEvent;
import io.agentscope.core.hook.PostCallEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import io.agentscope.core.message.TextBlock;
import io.agentscope.core.model.ChatResponse;
import io.agentscope.core.model.GenerateOptions;
import io.agentscope.core.model.Model;
import io.agentscope.core.model.ToolSchema;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SuppressWarnings("removal")
class ReActAgentScopeAgentClientTests {

    @Test
    void usesPromptProviderSystemPromptWhenCreatingAgentscopeAgent() {
        CapturingModel model = new CapturingModel();
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getExecutor().setSystemPrompt("local prompt");
        AtomicReference<AgentLoopRequest> promptRequest = new AtomicReference<>();
        AgentScopePromptProvider promptProvider = (request, fallback) -> {
            promptRequest.set(request);
            return "remote prompt";
        };
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("draft")
                .agentId("agent-1")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();

        ReActAgentScopeAgentClient client = new ReActAgentScopeAgentClient(model, properties, null, promptProvider);
        client.call(request, List.of(Msg.builder().role(MsgRole.USER).textContent("draft").build()));

        assertEquals(request, promptRequest.get());
        assertTrue(model.messages.get().stream()
                .anyMatch(message -> message.getRole() == MsgRole.SYSTEM
                        && "remote prompt".equals(message.getTextContent())));
    }

    @Test
    void attachesConfiguredHooksToCreatedAgentscopeAgent() {
        CapturingModel model = new CapturingModel();
        AgentScopeProperties properties = new AgentScopeProperties();
        AtomicReference<Msg> finalMessage = new AtomicReference<>();
        Hook hook = new Hook() {
            @Override
            public <T extends HookEvent> Mono<T> onEvent(T event) {
                if (event instanceof PostCallEvent postCallEvent) {
                    finalMessage.set(postCallEvent.getFinalMessage());
                }
                return Mono.just(event);
            }
        };
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("draft")
                .agentId("agent-1")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();

        ReActAgentScopeAgentClient client = new ReActAgentScopeAgentClient(
                model,
                properties,
                null,
                AgentScopePromptProvider.local(),
                List.of(hook));
        client.call(request, List.of(Msg.builder().role(MsgRole.USER).textContent("draft").build()));

        assertEquals("ok", finalMessage.get().getTextContent());
    }

    private static final class CapturingModel implements Model {
        private final AtomicReference<List<Msg>> messages = new AtomicReference<>(List.of());

        @Override
        public Flux<ChatResponse> stream(List<Msg> messages, List<ToolSchema> tools, GenerateOptions options) {
            this.messages.set(List.copyOf(messages));
            return Flux.just(ChatResponse.builder()
                    .content(List.of(TextBlock.builder().text("ok").build()))
                    .finishReason("stop")
                    .build());
        }

        @Override
        public String getModelName() {
            return "test-model";
        }
    }
}

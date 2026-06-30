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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentExternalInvocationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentExternalInvocationInboundPort;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import io.agentscope.core.a2a.server.executor.runner.AgentRequestOptions;
import io.agentscope.core.a2a.server.executor.runner.AgentRunner;
import io.agentscope.core.agent.Event;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@SuppressWarnings("deprecation")
public class AgentScopeA2aServerRunner implements AgentRunner {

    private final AgentExternalInvocationInboundPort inboundPort;
    private final AgentScopeProperties properties;

    public AgentScopeA2aServerRunner(AgentExternalInvocationInboundPort inboundPort, AgentScopeProperties properties) {
        this.inboundPort = Objects.requireNonNull(inboundPort, "inboundPort must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public String getAgentName() {
        AgentScopeProperties.A2a a2a = properties.getA2a();
        return A2ATenantMetadata.tenantQualifiedAgentName(
                textOrDefault(a2a.getTenantId(), "default"),
                textOrDefault(a2a.getAgentName(), "seahorse-agent"));
    }

    @Override
    public String getAgentDescription() {
        return textOrDefault(properties.getA2a().getDescription(), "Seahorse Agent");
    }

    @Override
    public Flux<Event> stream(List<Msg> messages, AgentRequestOptions options) {
        AgentExternalInvocationCommand command = toExternalInvocationCommand(messages, options);
        return Flux.create(sink -> {
            StreamCancellationHandle handle = inboundPort.streamInvoke(command, new StreamCallback() {
                @Override
                public void onContent(String content) {
                    if (!sink.isCancelled()) {
                        sink.next(AgentScopeEvents.finalAnswer(
                                Msg.builder().role(MsgRole.ASSISTANT).textContent(content).build(),
                                false));
                    }
                }

                @Override
                public void onComplete() {
                    sink.complete();
                }

                @Override
                public void onError(Throwable error) {
                    sink.error(error);
                }
            });
            sink.onCancel(handle::cancel);
        });
    }

    @Override
    public void stop(String taskId) {
        // Seahorse cancellation is bound to the StreamCancellationHandle returned per request.
    }

    private AgentExternalInvocationCommand toExternalInvocationCommand(List<Msg> messages, AgentRequestOptions options) {
        List<Msg> safeMessages = messages == null ? List.of() : messages.stream().filter(Objects::nonNull).toList();
        Msg questionMessage = lastUserMessage(safeMessages);
        String question = textOrDefault(questionMessage == null ? null : questionMessage.getTextContent(), "A2A request");
        List<ChatMessage> history = new ArrayList<>();
        for (Msg message : safeMessages) {
            if (message != questionMessage) {
                history.add(new ChatMessage(toChatRole(message.getRole()), message.getTextContent()));
            }
        }
        AgentScopeProperties.A2a a2a = properties.getA2a();
        return new AgentExternalInvocationCommand(
                a2a.getTenantId(),
                options == null ? null : options.getUserId(),
                a2a.getAgentName(),
                question,
                history,
                java.util.Map.of("source", "agentscope-a2a"),
                null);
    }

    private Msg lastUserMessage(List<Msg> messages) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            Msg message = messages.get(i);
            if (message.getRole() == MsgRole.USER) {
                return message;
            }
        }
        return messages.isEmpty() ? null : messages.get(messages.size() - 1);
    }

    private ChatRole toChatRole(MsgRole role) {
        if (role == null) {
            return ChatRole.USER;
        }
        return switch (role) {
            case SYSTEM -> ChatRole.SYSTEM;
            case ASSISTANT -> ChatRole.ASSISTANT;
            case TOOL -> ChatRole.TOOL;
            case USER -> ChatRole.USER;
        };
    }

    private static String textOrDefault(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNullElse(fallback, "") : value.trim();
    }
}

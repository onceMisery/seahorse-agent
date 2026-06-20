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

import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.event.TextBlockDeltaEvent;
import io.agentscope.core.event.ThinkingBlockDeltaEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import reactor.core.Disposable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.ForkJoinPool;

public class AgentScopeReActExecutor implements ReActExecutorPort {

    private final AgentScopeAgentClient client;
    private final Executor asyncExecutor;

    public AgentScopeReActExecutor(AgentScopeAgentClient client) {
        this(client, ForkJoinPool.commonPool());
    }

    public AgentScopeReActExecutor(AgentScopeAgentClient client, Executor asyncExecutor) {
        this.client = Objects.requireNonNull(client, "client must not be null");
        this.asyncExecutor = Objects.requireNonNull(asyncExecutor, "asyncExecutor must not be null");
    }

    @Override
    public AgentLoopResult execute(AgentLoopRequest request) {
        AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Msg response = client.call(safeRequest, toAgentScopeMessages(safeRequest));
        String finalAnswer = response == null ? "" : response.getTextContent();
        return new AgentLoopResult(finalAnswer, List.of(AgentStep.finalAnswer(finalAnswer)), false);
    }

    @Override
    public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
        StreamCallback safeCallback = Objects.requireNonNull(callback, "callback must not be null");
        String capturedTenant = TenantContext.capture();
        CompletableFuture<Disposable> subscriptionFuture = CompletableFuture.supplyAsync(() -> {
            TenantContext.restore(capturedTenant);
            try {
                AgentLoopRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
                return client.stream(safeRequest, toAgentScopeMessages(safeRequest))
                        .subscribe(
                                event -> emitEvent(event, safeCallback),
                                safeCallback::onError,
                                safeCallback::onComplete);
            } catch (Throwable ex) {
                safeCallback.onError(ex);
                return null;
            } finally {
                TenantContext.clear();
            }
        }, asyncExecutor);
        return () -> {
            Disposable subscription = subscriptionFuture.getNow(null);
            if (subscription != null) {
                subscription.dispose();
            }
            subscriptionFuture.cancel(true);
        };
    }

    @Override
    public String engineId() {
        return "agentscope";
    }

    private void emitEvent(AgentEvent event, StreamCallback callback) {
        if (event == null) {
            return;
        }
        if (event instanceof ThinkingBlockDeltaEvent thinking) {
            emitThinking(thinking.getDelta(), callback);
            return;
        }
        if (event instanceof TextBlockDeltaEvent text) {
            emitContent(text.getDelta(), callback);
            return;
        }
        if (event instanceof AgentResultEvent result && result.getResult() != null) {
            emitContent(result.getResult().getTextContent(), callback);
        }
    }

    private void emitContent(String content, StreamCallback callback) {
        if (content == null || content.isBlank()) {
            return;
        }
        callback.onContent(content);
    }

    private void emitThinking(String content, StreamCallback callback) {
        if (content == null || content.isBlank()) {
            return;
        }
        callback.onThinking(content);
    }

    private List<Msg> toAgentScopeMessages(AgentLoopRequest request) {
        List<Msg> messages = new ArrayList<>();
        for (ChatMessage historyMessage : request.history()) {
            if (historyMessage != null) {
                messages.add(toAgentScopeMessage(historyMessage.getRole(), historyMessage.getContent()));
            }
        }
        messages.add(Msg.builder()
                .role(MsgRole.USER)
                .textContent(request.question())
                .build());
        return messages;
    }

    private Msg toAgentScopeMessage(ChatRole role, String content) {
        return Msg.builder()
                .role(toMsgRole(role))
                .textContent(Objects.requireNonNullElse(content, ""))
                .build();
    }

    private MsgRole toMsgRole(ChatRole role) {
        if (role == null) {
            return MsgRole.USER;
        }
        return switch (role) {
            case SYSTEM -> MsgRole.SYSTEM;
            case ASSISTANT -> MsgRole.ASSISTANT;
            case TOOL -> MsgRole.TOOL;
            case USER -> MsgRole.USER;
        };
    }
}

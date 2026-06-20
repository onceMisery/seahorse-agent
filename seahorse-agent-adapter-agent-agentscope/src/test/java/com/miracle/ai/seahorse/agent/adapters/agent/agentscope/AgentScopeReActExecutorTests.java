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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatSamplingOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import io.agentscope.core.event.AgentEvent;
import io.agentscope.core.event.AgentResultEvent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AgentScopeReActExecutorTests {

    @Test
    void executesThroughAgentscopeClientAndKeepsSeahorseResultContract() {
        CapturingClient client = new CapturingClient();
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .history(List.of(ChatMessage.system("be concise")))
                .samplingOptions(ChatSamplingOptions.builder().build())
                .tenantId("tenant-a")
                .build();

        AgentLoopResult result = executor.execute(request);

        assertEquals("agentscope answer", result.finalAnswer());
        assertEquals("agentscope", executor.engineId());
        assertEquals(2, client.messages.size());
        assertEquals(MsgRole.SYSTEM, client.messages.get(0).getRole());
        assertEquals("plan", client.messages.get(1).getTextContent());
    }

    @Test
    void streamExecuteForwardsAgentscopeStreamEventsBeforeCompleting() {
        CapturingClient client = new CapturingClient();
        client.events = Flux.just(
                event("first ", false),
                event("second", true));
        AgentScopeReActExecutor executor = new AgentScopeReActExecutor(client, Runnable::run);
        RecordingCallback callback = new RecordingCallback();
        AgentLoopRequest request = AgentLoopRequest.builder()
                .question("plan")
                .samplingOptions(ChatSamplingOptions.builder().build())
                .build();

        executor.streamExecute(request, callback);

        assertEquals(List.of("first ", "second"), callback.contents);
        assertEquals(1, callback.completed);
        assertEquals(0, callback.errors);
    }

    private static AgentEvent event(String text, boolean ignoredLast) {
        return new AgentResultEvent(Msg.builder().role(MsgRole.ASSISTANT).textContent(text).build());
    }

    private static final class CapturingClient implements AgentScopeAgentClient {
        private List<Msg> messages = List.of();
        private Flux<AgentEvent> events;

        @Override
        public Msg call(AgentLoopRequest request, List<Msg> messages) {
            this.messages = List.copyOf(messages);
            return Msg.builder()
                    .role(MsgRole.ASSISTANT)
                    .textContent("agentscope answer")
                    .build();
        }

        @Override
        public Flux<AgentEvent> stream(AgentLoopRequest request, List<Msg> messages) {
            this.messages = List.copyOf(messages);
            return events;
        }
    }

    private static final class RecordingCallback implements StreamCallback {
        private final List<String> contents = new ArrayList<>();
        private int completed;
        private int errors;

        @Override
        public void onContent(String content) {
            contents.add(content);
        }

        @Override
        public void onComplete() {
            completed++;
        }

        @Override
        public void onError(Throwable error) {
            errors++;
        }
    }
}

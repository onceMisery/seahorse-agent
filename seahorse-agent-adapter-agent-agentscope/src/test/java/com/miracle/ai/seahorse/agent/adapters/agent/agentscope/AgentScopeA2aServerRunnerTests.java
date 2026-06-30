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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentExternalInvocationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentExternalInvocationInboundPort;
import io.agentscope.core.a2a.server.executor.runner.AgentRequestOptions;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class AgentScopeA2aServerRunnerTests {

    @Test
    void streamsThroughSeahorseExternalInboundPortWithA2aContext() {
        CapturingInboundPort inboundPort = new CapturingInboundPort();
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setTenantId("tenant-a");
        properties.getA2a().setAgentName("planner");
        AgentScopeA2aServerRunner runner = new AgentScopeA2aServerRunner(inboundPort, properties);

        runner.stream(List.of(
                Msg.builder().role(MsgRole.SYSTEM).textContent("system").build(),
                Msg.builder().role(MsgRole.USER).textContent("first").build(),
                Msg.builder().role(MsgRole.ASSISTANT).textContent("answer").build(),
                Msg.builder().role(MsgRole.USER).textContent("second").build()), null).blockLast();

        AgentExternalInvocationCommand command = inboundPort.command.get();
        assertThat(command).isNotNull();
        assertThat(command.tenantId()).isEqualTo("tenant-a");
        assertThat(command.agentName()).isEqualTo("planner");
        assertThat(command.question()).isEqualTo("second");
        assertThat(command.preferredExecutorEngine()).isNull();
        assertThat(command.history()).hasSize(3);
        assertThat(command.history().get(0).getRole()).isEqualTo(ChatRole.SYSTEM);
        assertThat(command.history().get(1).getRole()).isEqualTo(ChatRole.USER);
        assertThat(command.history().get(2).getRole()).isEqualTo(ChatRole.ASSISTANT);
    }

    private static final class CapturingInboundPort implements AgentExternalInvocationInboundPort {
        private final AtomicReference<AgentExternalInvocationCommand> command = new AtomicReference<>();

        @Override
        public StreamCancellationHandle streamInvoke(
                AgentExternalInvocationCommand command,
                StreamCallback callback) {
            this.command.set(command);
            callback.onComplete();
            return () -> { };
        }
    }
}

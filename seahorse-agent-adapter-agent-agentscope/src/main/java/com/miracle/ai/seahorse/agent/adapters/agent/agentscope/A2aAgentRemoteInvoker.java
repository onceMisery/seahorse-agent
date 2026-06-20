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

import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentRequest;
import io.a2a.spec.AgentCard;
import io.agentscope.core.a2a.agent.A2aAgent;
import io.agentscope.core.message.Msg;
import io.agentscope.core.message.MsgRole;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

public class A2aAgentRemoteInvoker implements AgentScopeRemoteAgentInvoker {

    private final Duration timeout;

    public A2aAgentRemoteInvoker(Duration timeout) {
        this.timeout = Objects.requireNonNullElse(timeout, Duration.ofMinutes(2));
    }

    @Override
    public String invoke(AgentCard agentCard, A2AAgentRequest request) {
        A2aAgent agent = A2aAgent.builder()
                .name(request.agentName())
                .agentCard(agentCard)
                .checkRunning(false)
                .build();
        Msg response = agent.call(List.of(Msg.builder()
                .role(MsgRole.USER)
                .textContent(request.prompt())
                .metadata(Maps.stringMetadata(request.metadata()))
                .build())).block(timeout);
        return response == null ? "" : response.getTextContent();
    }
}

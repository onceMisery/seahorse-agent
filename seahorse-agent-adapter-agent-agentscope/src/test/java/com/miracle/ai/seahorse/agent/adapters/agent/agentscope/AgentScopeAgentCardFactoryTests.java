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

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AgentScopeAgentCardFactoryTests {

    private final AgentScopeAgentCardFactory factory = new AgentScopeAgentCardFactory();

    @Test
    void buildsEndpointUrlFromHostPortAndPath() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setHost("127.0.0.1");
        properties.getA2a().setPort(8080);
        properties.getA2a().setPath("/a2a");

        assertEquals("http://127.0.0.1:8080/a2a", factory.agentCard(properties).url());
    }

    @Test
    void rejectsRegistrationWhenEndpointCannotBeResolved() {
        AgentScopeProperties properties = new AgentScopeProperties();

        assertThrows(IllegalStateException.class, () -> factory.agentCard(properties));
    }
}

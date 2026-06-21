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

import com.alibaba.nacos.api.PropertyKeyConst;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResolveRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.A2AAgentResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.RemoteAgentCard;
import io.agentscope.core.nacos.a2a.discovery.NacosAgentCardResolver;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

class AgentScopeA2ALiveSmokeTest {

    @Test
    void resolvesThroughNacosAndInvokesRemoteA2aAgent() throws Exception {
        Assumptions.assumeTrue(Boolean.parseBoolean(System.getenv("SEAHORSE_LIVE_A2A_SMOKE")),
                "set SEAHORSE_LIVE_A2A_SMOKE=true to run live A2A smoke");
        Properties properties = new Properties();
        properties.setProperty(PropertyKeyConst.SERVER_ADDR, env("SEAHORSE_LIVE_NACOS_SERVER", "nacos:8848"));
        properties.setProperty(PropertyKeyConst.NAMESPACE, env("SEAHORSE_LIVE_NACOS_NAMESPACE", "public"));
        String tenantId = env("SEAHORSE_LIVE_A2A_TENANT_ID", "tenant-a");
        String agentName = env("SEAHORSE_LIVE_A2A_AGENT_NAME", "seahorse-b");

        AgentScopeA2AAgentConnector connector = new AgentScopeA2AAgentConnector(
                new NacosAgentCardResolver(properties),
                new A2aAgentRemoteInvoker(
                        Duration.ofSeconds(30),
                        authMode(env("SEAHORSE_LIVE_A2A_AUTH_MODE", "shared-secret")),
                        env("SEAHORSE_LIVE_A2A_AUTH_HEADER", "X-Seahorse-A2A-Token"),
                        env("SEAHORSE_LIVE_A2A_SHARED_SECRET", "")));

        RemoteAgentCard card = connector.resolve(new A2AAgentResolveRequest(tenantId, agentName));
        assertThat(card.agentName()).isEqualTo(tenantId + "/" + agentName);
        assertThat(card.url()).isEqualTo(env("SEAHORSE_LIVE_A2A_EXPECTED_URL", "http://seahorse-backend-b:9090/a2a"));
        assertThat(card.metadata()).containsEntry("tenantId", tenantId);

        A2AAgentResult result = connector.invoke(new A2AAgentRequest(
                tenantId,
                agentName,
                "ping from connector smoke",
                Map.of("smoke", "true")));

        assertThat(result.content()).contains(env("SEAHORSE_LIVE_A2A_EXPECTED_CONTENT", "mock-streaming-chat"));
        assertThat(result.metadata()).containsEntry("tenantId", tenantId);
    }

    private static String env(String key, String fallback) {
        String value = System.getenv(key);
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }

    private static A2aAuthMode authMode(String value) {
        return "tenant-signed".equalsIgnoreCase(value) ? A2aAuthMode.TENANT_SIGNED
                : "none".equalsIgnoreCase(value) ? A2aAuthMode.NONE : A2aAuthMode.SHARED_SECRET;
    }
}

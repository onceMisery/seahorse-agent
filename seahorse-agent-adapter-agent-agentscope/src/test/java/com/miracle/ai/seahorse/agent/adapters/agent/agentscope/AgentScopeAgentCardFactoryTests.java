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

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void qualifiesRegisteredAgentNameWithTenantByDefault() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setUrl("http://127.0.0.1:8080/a2a");
        properties.getA2a().setTenantId("tenant-a");
        properties.getA2a().setAgentName("planner");

        assertEquals("tenant-a/planner", factory.agentCard(properties).name());
        assertEquals("tenant-a/planner", factory.configurableAgentCard(properties).getName());
    }

    @Test
    void rejectsRegistrationWhenEndpointCannotBeResolved() {
        AgentScopeProperties properties = new AgentScopeProperties();

        assertThrows(IllegalStateException.class, () -> factory.agentCard(properties));
    }

    @Test
    void configurableAgentCardCarriesTenantBoundaryMetadata() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getA2a().setUrl("http://127.0.0.1:8080/a2a");
        properties.getA2a().setTenantId("tenant-a");
        properties.getNacos().getM3().setEnabled(true);
        properties.getNacos().getM3().setNamespace("m3-ns");
        properties.getNacos().getM3().setGroup("m3-group");
        properties.getNacos().getM3().setClusterName("m3-cluster");
        properties.getNacos().getM3().setMetadata(Map.of("mode", "M3"));

        var card = factory.configurableAgentCard(properties);

        assertTrue(card.getSkills().stream()
                .filter(skill -> A2ATenantMetadata.TENANT_SKILL_ID.equals(skill.id()))
                .flatMap(skill -> skill.tags().stream())
                .anyMatch("seahorse:tenant:tenant-a"::equals));
        assertTrue(card.getSkills().stream()
                .filter(skill -> A2ATenantMetadata.TENANT_SKILL_ID.equals(skill.id()))
                .flatMap(skill -> skill.tags().stream())
                .anyMatch("seahorse:m3:mode=M3"::equals));
        assertTrue(card.getSkills().stream()
                .filter(skill -> A2ATenantMetadata.TENANT_SKILL_ID.equals(skill.id()))
                .flatMap(skill -> skill.tags().stream())
                .anyMatch("seahorse:m3:namespace=m3-ns"::equals));
        assertTrue(card.getSkills().stream()
                .filter(skill -> A2ATenantMetadata.TENANT_SKILL_ID.equals(skill.id()))
                .flatMap(skill -> skill.tags().stream())
                .anyMatch("seahorse:m3:group=m3-group"::equals));
        assertTrue(card.getSkills().stream()
                .filter(skill -> A2ATenantMetadata.TENANT_SKILL_ID.equals(skill.id()))
                .flatMap(skill -> skill.tags().stream())
                .anyMatch("seahorse:m3:clusterName=m3-cluster"::equals));
    }
}

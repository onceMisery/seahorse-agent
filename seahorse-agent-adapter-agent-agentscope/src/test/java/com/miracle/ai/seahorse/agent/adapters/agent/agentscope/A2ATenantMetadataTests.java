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

import io.a2a.spec.AgentCard;
import io.a2a.spec.AgentCapabilities;
import io.a2a.spec.AgentSkill;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class A2ATenantMetadataTests {

    @Test
    void injectsAndReadsTenantMetadataFromAgentCardSkills() {
        AgentCard card = baseCard();

        AgentCard enriched = A2ATenantMetadata.withTenant(card, "tenant-a", Map.of("mode", "M3"));

        assertEquals("tenant-a", A2ATenantMetadata.tenantId(enriched).orElseThrow());
        assertTrue(enriched.skills().stream()
                .flatMap(skill -> skill.tags().stream())
                .anyMatch("seahorse:m3:mode=M3"::equals));
    }

    static AgentCard baseCard() {
        return new AgentCard.Builder()
                .protocolVersion("0.3.0")
                .name("planner")
                .description("Planner")
                .version("1.0.0")
                .url("http://localhost:8080/a2a")
                .capabilities(new AgentCapabilities.Builder().streaming(true).build())
                .defaultInputModes(List.of("text"))
                .defaultOutputModes(List.of("text"))
                .skills(List.of(new AgentSkill.Builder()
                        .id("planner")
                        .name("Planner")
                        .description("Planner")
                        .tags(List.of("planner"))
                        .build()))
                .build();
    }
}

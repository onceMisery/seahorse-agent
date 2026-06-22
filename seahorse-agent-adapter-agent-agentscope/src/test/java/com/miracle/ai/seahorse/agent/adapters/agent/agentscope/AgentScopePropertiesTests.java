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
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AgentScopePropertiesTests {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(AgentScopeReActAutoConfiguration.class));

    @Test
    void defaultsKeepAgentscopeA2aStudioAndConfigCenterDisabled() {
        AgentScopeProperties properties = new AgentScopeProperties();

        assertFalse(properties.getA2a().isEnabled());
        assertFalse(properties.getA2a().isRegisterEnabled());
        assertFalse(properties.getConfigCenter().isEnabled());
        assertFalse(properties.getStudio().isEnabled());
        assertFalse(properties.getNacos().getM3().isEnabled());
        assertEquals("public", properties.getNacos().getNamespace());
        assertEquals("DEFAULT_GROUP", properties.getNacos().getGroup());
    }

    @Test
    void bindsConfigCenterPromptAndSkillSettings() {
        contextRunner
                .withPropertyValues(
                        "seahorse.agentscope.config-center.prompt-key=agent.system.prompt",
                        "seahorse.agentscope.config-center.prompt-version=v2",
                        "seahorse.agentscope.config-center.prompt-label=stable",
                        "seahorse.agentscope.config-center.skill-namespace=agent-skills",
                        "seahorse.agentscope.config-center.skill-version=2026-06",
                        "seahorse.agentscope.config-center.skill-label=stable",
                        "seahorse.agentscope.config-center.strict-startup=true")
                .run(context -> {
                    AgentScopeProperties properties = context.getBean(AgentScopeProperties.class);

                    assertEquals("agent.system.prompt", properties.getConfigCenter().getPromptKey());
                    assertEquals("v2", properties.getConfigCenter().getPromptVersion());
                    assertEquals("stable", properties.getConfigCenter().getPromptLabel());
                    assertEquals("agent-skills", properties.getConfigCenter().getSkillNamespace());
                    assertEquals("2026-06", properties.getConfigCenter().getSkillVersion());
                    assertEquals("stable", properties.getConfigCenter().getSkillLabel());
                    assertEquals(true, properties.getConfigCenter().isStrictStartup());
                });
    }

    @Test
    void bindsA2aAuthenticationSettings() {
        contextRunner
                .withPropertyValues(
                        "seahorse.agentscope.a2a.auth-mode=tenant-signed",
                        "seahorse.agentscope.a2a.allowed-timestamp-skew=2m",
                        "seahorse.agentscope.a2a.nonce-ttl=7m",
                        "seahorse.agentscope.a2a.registration-ttl=15m",
                        "seahorse.agentscope.a2a.duplicate-registration-policy=replace")
                .run(context -> {
                    AgentScopeProperties properties = context.getBean(AgentScopeProperties.class);

                    assertEquals(A2aAuthMode.TENANT_SIGNED, properties.getA2a().getAuthMode());
                    assertEquals(java.time.Duration.ofMinutes(2), properties.getA2a().getAllowedTimestampSkew());
                    assertEquals(java.time.Duration.ofMinutes(7), properties.getA2a().getNonceTtl());
                    assertEquals(java.time.Duration.ofMinutes(15), properties.getA2a().getRegistrationTtl());
                    assertEquals(A2aDuplicateRegistrationPolicy.REPLACE,
                            properties.getA2a().getDuplicateRegistrationPolicy());
                });
    }
}

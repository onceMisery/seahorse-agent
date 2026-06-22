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

import com.alibaba.nacos.api.exception.NacosException;
import io.agentscope.core.skill.repository.AgentSkillRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentScopeConfigCenterStartupValidatorTests {

    @Test
    void strictStartupValidatesPromptAndSkillRepositoryBeforeServingTraffic() throws Exception {
        AgentScopePromptConfigCenter promptConfigCenter = mock(AgentScopePromptConfigCenter.class);
        AgentSkillRepository skillRepository = skillRepository(List.of("weather"));
        AgentScopeProperties properties = strictProperties();
        when(promptConfigCenter.getPrompt(eq("agent.prompt"), anyMap(), eq(null)))
                .thenReturn("system prompt");
        AgentScopeConfigCenterStartupValidator validator = new AgentScopeConfigCenterStartupValidator(
                properties,
                promptConfigCenter,
                skillRepository);

        validator.afterSingletonsInstantiated();

        verify(promptConfigCenter).getPrompt(eq("agent.prompt"), eq(Map.of()), eq(null));
        verify(skillRepository).getAllSkillNames();
    }

    @Test
    void strictStartupFailsWhenConfiguredPromptCannotBeLoaded() throws Exception {
        AgentScopePromptConfigCenter promptConfigCenter = mock(AgentScopePromptConfigCenter.class);
        AgentScopeProperties properties = strictProperties();
        when(promptConfigCenter.getPrompt(eq("agent.prompt"), anyMap(), eq(null)))
                .thenThrow(new NacosException(500, "nacos unavailable"));
        AgentScopeConfigCenterStartupValidator validator = new AgentScopeConfigCenterStartupValidator(
                properties,
                promptConfigCenter,
                skillRepository(List.of("weather")));

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strict startup")
                .hasMessageContaining("agent.prompt");
    }

    @Test
    void strictStartupFailsWhenConfiguredSkillNamespaceIsEmpty() throws Exception {
        AgentScopePromptConfigCenter promptConfigCenter = mock(AgentScopePromptConfigCenter.class);
        when(promptConfigCenter.getPrompt(eq("agent.prompt"), anyMap(), eq(null)))
                .thenReturn("system prompt");
        AgentScopeConfigCenterStartupValidator validator = new AgentScopeConfigCenterStartupValidator(
                strictProperties(),
                promptConfigCenter,
                skillRepository(List.of()));

        assertThatThrownBy(validator::afterSingletonsInstantiated)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("strict startup")
                .hasMessageContaining("agent-skills");
    }

    @Test
    void nonStrictStartupDoesNotValidateNacosConfig() {
        AgentScopeProperties properties = strictProperties();
        properties.getConfigCenter().setStrictStartup(false);
        AgentScopeConfigCenterStartupValidator validator = new AgentScopeConfigCenterStartupValidator(
                properties,
                null,
                null);

        validator.afterSingletonsInstantiated();
    }

    private AgentScopeProperties strictProperties() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getConfigCenter().setEnabled(true);
        properties.getConfigCenter().setStrictStartup(true);
        properties.getConfigCenter().setPromptKey("agent.prompt");
        properties.getConfigCenter().setPromptVersion("v1");
        properties.getConfigCenter().setPromptLabel("stable");
        properties.getConfigCenter().setSkillNamespace("agent-skills");
        properties.getConfigCenter().setSkillVersion("v1");
        properties.getConfigCenter().setSkillLabel("stable");
        return properties;
    }

    private AgentSkillRepository skillRepository(List<String> skillNames) {
        AgentSkillRepository repository = mock(AgentSkillRepository.class);
        when(repository.getAllSkillNames()).thenReturn(skillNames);
        return repository;
    }
}

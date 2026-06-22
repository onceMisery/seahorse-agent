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
import org.springframework.beans.factory.SmartInitializingSingleton;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class AgentScopeConfigCenterStartupValidator implements SmartInitializingSingleton {

    private final AgentScopeProperties properties;
    private final AgentScopePromptConfigCenter promptConfigCenter;
    private final AgentSkillRepository skillRepository;

    public AgentScopeConfigCenterStartupValidator(
            AgentScopeProperties properties,
            AgentScopePromptConfigCenter promptConfigCenter,
            AgentSkillRepository skillRepository) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.promptConfigCenter = promptConfigCenter;
        this.skillRepository = skillRepository;
    }

    @Override
    public void afterSingletonsInstantiated() {
        AgentScopeProperties.ConfigCenter configCenter = properties.getConfigCenter();
        if (!configCenter.isEnabled() || !configCenter.isStrictStartup()) {
            return;
        }
        validatePrompt(configCenter);
        validateSkills(configCenter);
    }

    private void validatePrompt(AgentScopeProperties.ConfigCenter configCenter) {
        String promptKey = trimToNull(configCenter.getPromptKey());
        if (promptKey == null) {
            return;
        }
        if (promptConfigCenter == null) {
            throw strictFailure("prompt " + promptKey + " is configured but no Nacos prompt provider is available",
                    null);
        }
        try {
            String prompt = promptConfigCenter.getPrompt(promptKey, Map.of(), null);
            if (prompt == null || prompt.isBlank()) {
                throw strictFailure("prompt " + promptKey + " is empty or missing", null);
            }
        } catch (NacosException ex) {
            throw strictFailure("prompt " + promptKey + " cannot be loaded", ex);
        }
    }

    private void validateSkills(AgentScopeProperties.ConfigCenter configCenter) {
        String skillNamespace = trimToNull(configCenter.getSkillNamespace());
        if (skillNamespace == null) {
            return;
        }
        if (skillRepository == null) {
            throw strictFailure(
                    "skill namespace " + skillNamespace + " is configured but no Nacos skill repository is available",
                    null);
        }
        List<String> skillNames;
        try {
            skillNames = skillRepository.getAllSkillNames();
        } catch (RuntimeException ex) {
            throw strictFailure("skill namespace " + skillNamespace + " cannot be loaded", ex);
        }
        if (skillNames == null || skillNames.isEmpty()) {
            throw strictFailure("skill namespace " + skillNamespace + " is empty or missing", null);
        }
    }

    private IllegalStateException strictFailure(String detail, Throwable cause) {
        String message = "AgentScope config center strict startup validation failed: " + detail;
        return cause == null ? new IllegalStateException(message) : new IllegalStateException(message, cause);
    }

    private String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

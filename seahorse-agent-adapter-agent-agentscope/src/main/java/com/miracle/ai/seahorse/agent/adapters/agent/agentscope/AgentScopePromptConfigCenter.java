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
import io.agentscope.core.nacos.prompt.NacosPromptListener;

import java.util.Map;
import java.util.Objects;

public class AgentScopePromptConfigCenter {

    private final NacosPromptListener promptListener;
    private final AgentScopeProperties properties;

    public AgentScopePromptConfigCenter(NacosPromptListener promptListener, AgentScopeProperties properties) {
        this.promptListener = Objects.requireNonNull(promptListener, "promptListener must not be null");
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    public String getPrompt(String promptKey, Map<String, String> args, String defaultValue) throws NacosException {
        AgentScopeProperties.ConfigCenter configCenter = properties.getConfigCenter();
        return promptListener.getPrompt(
                promptKey,
                blankToNull(configCenter.getPromptVersion()),
                blankToNull(configCenter.getPromptLabel()),
                args,
                defaultValue);
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

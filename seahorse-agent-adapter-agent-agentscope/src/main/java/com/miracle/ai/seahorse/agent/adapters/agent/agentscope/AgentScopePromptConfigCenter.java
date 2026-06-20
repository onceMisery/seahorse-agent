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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import io.agentscope.core.nacos.prompt.NacosPromptListener;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class AgentScopePromptConfigCenter implements AgentScopePromptProvider {

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

    @Override
    public String systemPrompt(AgentLoopRequest request, String fallback) {
        String promptKey = blankToNull(properties.getConfigCenter().getPromptKey());
        if (promptKey == null) {
            return fallback;
        }
        try {
            return getPrompt(promptKey, promptArgs(request), fallback);
        } catch (NacosException ex) {
            throw new IllegalStateException("Failed to load AgentScope system prompt from Nacos", ex);
        }
    }

    private Map<String, String> promptArgs(AgentLoopRequest request) {
        Map<String, String> args = new LinkedHashMap<>();
        if (request == null) {
            return args;
        }
        putIfPresent(args, "runId", request.runId());
        putIfPresent(args, "agentId", request.agentId());
        putIfPresent(args, "versionId", request.versionId());
        putIfPresent(args, "rolloutId", request.rolloutId());
        putIfPresent(args, "tenantId", request.tenantId());
        putIfPresent(args, "userId", request.userId());
        putIfPresent(args, "agentIdentityId", request.agentIdentityId());
        putIfPresent(args, "modelId", request.modelId());
        return Map.copyOf(args);
    }

    private void putIfPresent(Map<String, String> args, String key, String value) {
        if (value != null && !value.isBlank()) {
            args.put(key, value.trim());
        }
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

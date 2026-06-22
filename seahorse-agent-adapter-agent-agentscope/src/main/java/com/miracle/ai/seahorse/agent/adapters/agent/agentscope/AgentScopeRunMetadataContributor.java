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

import com.miracle.ai.seahorse.agent.kernel.application.chat.AgentRunMetadataContributor;
import com.miracle.ai.seahorse.agent.ports.inbound.chat.StreamChatCommand;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public class AgentScopeRunMetadataContributor implements AgentRunMetadataContributor {

    private final AgentScopeProperties properties;

    public AgentScopeRunMetadataContributor(AgentScopeProperties properties) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
    }

    @Override
    public Map<String, Object> metadata(StreamChatCommand command) {
        AgentScopeProperties.ConfigCenter config = properties.getConfigCenter();
        if (!config.isEnabled()) {
            return Map.of();
        }
        Map<String, Object> metadata = new LinkedHashMap<>();
        Map<String, Object> prompt = promptMetadata(config);
        if (!prompt.isEmpty()) {
            metadata.put("prompt", prompt);
        }
        Map<String, Object> skillRepository = skillRepositoryMetadata(config);
        if (!skillRepository.isEmpty()) {
            metadata.put("skillRepository", skillRepository);
        }
        return Map.copyOf(metadata);
    }

    private Map<String, Object> promptMetadata(AgentScopeProperties.ConfigCenter config) {
        String promptKey = trimToNull(config.getPromptKey());
        if (promptKey == null) {
            return Map.of();
        }
        Map<String, Object> prompt = nacosConfigMetadata(config.getPromptVersion(), config.getPromptLabel());
        prompt.put("key", promptKey);
        return Map.copyOf(prompt);
    }

    private Map<String, Object> skillRepositoryMetadata(AgentScopeProperties.ConfigCenter config) {
        String namespace = trimToNull(config.getSkillNamespace());
        if (namespace == null) {
            return Map.of();
        }
        Map<String, Object> skillRepository = nacosConfigMetadata(config.getSkillVersion(), config.getSkillLabel());
        skillRepository.put("skillNamespace", namespace);
        return Map.copyOf(skillRepository);
    }

    private Map<String, Object> nacosConfigMetadata(String version, String label) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("source", "nacos");
        putIfPresent(metadata, "version", version);
        putIfPresent(metadata, "label", label);
        putIfPresent(metadata, "namespace", properties.getNacos().getNamespace());
        putIfPresent(metadata, "group", properties.getNacos().getGroup());
        String revision = trimToNull(version);
        if (revision == null) {
            revision = trimToNull(label);
        }
        putIfPresent(metadata, "revision", revision);
        return metadata;
    }

    private void putIfPresent(Map<String, Object> target, String key, String value) {
        String text = trimToNull(value);
        if (text != null) {
            target.put(key, text);
        }
    }

    private String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

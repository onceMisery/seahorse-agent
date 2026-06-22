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

import static org.assertj.core.api.Assertions.assertThat;

class AgentScopeRunMetadataContributorTests {

    @Test
    void contributesNacosPromptAndSkillSourceWhenConfigCenterIsEnabled() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getNacos().setNamespace("public");
        properties.getNacos().setGroup("DEFAULT_GROUP");
        properties.getConfigCenter().setEnabled(true);
        properties.getConfigCenter().setPromptKey("seahorse.agent.prompt");
        properties.getConfigCenter().setPromptVersion("stable");
        properties.getConfigCenter().setPromptLabel("default");
        properties.getConfigCenter().setSkillNamespace("agent-skills");
        properties.getConfigCenter().setSkillVersion("v1");
        properties.getConfigCenter().setSkillLabel("stable");
        AgentScopeRunMetadataContributor contributor = new AgentScopeRunMetadataContributor(properties);

        Map<String, Object> metadata = contributor.metadata(null);

        assertThat(metadata).containsKeys("prompt", "skillRepository");
        assertThat(mapValue(metadata, "prompt"))
                .containsEntry("source", "nacos")
                .containsEntry("key", "seahorse.agent.prompt")
                .containsEntry("version", "stable")
                .containsEntry("label", "default")
                .containsEntry("namespace", "public")
                .containsEntry("group", "DEFAULT_GROUP")
                .containsEntry("revision", "stable");
        assertThat(mapValue(metadata, "skillRepository"))
                .containsEntry("source", "nacos")
                .containsEntry("skillNamespace", "agent-skills")
                .containsEntry("version", "v1")
                .containsEntry("label", "stable")
                .containsEntry("namespace", "public")
                .containsEntry("group", "DEFAULT_GROUP")
                .containsEntry("revision", "v1");
    }

    @Test
    void contributesNothingWhenConfigCenterIsDisabled() {
        AgentScopeProperties properties = new AgentScopeProperties();
        properties.getConfigCenter().setEnabled(false);
        properties.getConfigCenter().setPromptKey("seahorse.agent.prompt");
        AgentScopeRunMetadataContributor contributor = new AgentScopeRunMetadataContributor(properties);

        assertThat(contributor.metadata(null)).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mapValue(Map<String, Object> metadata, String key) {
        assertThat(metadata.get(key)).isInstanceOf(Map.class);
        return (Map<String, Object>) metadata.get(key);
    }
}

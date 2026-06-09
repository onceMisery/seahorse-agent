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

package com.miracle.ai.seahorse.agent.kernel.application.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.AgentSkillCategory;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillInjectMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoadSkillResourceToolPortAdapterTests {

    private final LoadSkillResourceToolPortAdapter adapter =
            new LoadSkillResourceToolPortAdapter(new AgentToolJsonSupport(new ObjectMapper()));

    @Test
    void shouldLoadSelectedSkillMarkdownFromInjectedRuntimeSnapshot() {
        ToolInvocationResult result = adapter.invoke("call-1", LoadSkillResourceToolPortAdapter.TOOL_ID, Map.of(
                "skillName", "research",
                "resourcePath", "SKILL.md",
                LoadSkillResourceToolPortAdapter.RUNTIME_SKILLS_ARGUMENT, List.of(skill("research"))));

        assertTrue(result.success());
        assertTrue(result.content().contains("\"name\":\"research\""));
        assertTrue(result.content().contains("\"resourcePath\":\"SKILL.md\""));
        assertTrue(result.content().contains("Use sources carefully."));
    }

    @Test
    void shouldRejectSkillNotPresentInInjectedRuntimeSnapshot() {
        ToolInvocationResult result = adapter.invoke("call-1", LoadSkillResourceToolPortAdapter.TOOL_ID, Map.of(
                "skillName", "missing",
                "resourcePath", "SKILL.md",
                LoadSkillResourceToolPortAdapter.RUNTIME_SKILLS_ARGUMENT, List.of(skill("research"))));

        assertFalse(result.success());
        assertEquals("skill is not selected in this Agent version", result.error());
    }

    @Test
    void shouldRejectParentTraversalResourcePath() {
        ToolInvocationResult result = adapter.invoke("call-1", LoadSkillResourceToolPortAdapter.TOOL_ID, Map.of(
                "skillName", "research",
                "resourcePath", "../SKILL.md",
                LoadSkillResourceToolPortAdapter.RUNTIME_SKILLS_ARGUMENT, List.of(skill("research"))));

        assertFalse(result.success());
        assertEquals("skill resource path is not allowed", result.error());
    }

    @Test
    void shouldRejectAbsoluteResourcePath() {
        ToolInvocationResult result = adapter.invoke("call-1", LoadSkillResourceToolPortAdapter.TOOL_ID, Map.of(
                "skillName", "research",
                "resourcePath", "/etc/passwd",
                LoadSkillResourceToolPortAdapter.RUNTIME_SKILLS_ARGUMENT, List.of(skill("research"))));

        assertFalse(result.success());
        assertEquals("skill resource path is not allowed", result.error());
    }

    @Test
    void shouldRejectUnavailableResource() {
        ToolInvocationResult result = adapter.invoke("call-1", LoadSkillResourceToolPortAdapter.TOOL_ID, Map.of(
                "skillName", "research",
                "resourcePath", "templates/example.md",
                LoadSkillResourceToolPortAdapter.RUNTIME_SKILLS_ARGUMENT, List.of(skill("research"))));

        assertFalse(result.success());
        assertEquals("skill resource is not available", result.error());
    }

    @Test
    void shouldRejectMissingInjectedRuntimeSnapshot() {
        ToolInvocationResult result = adapter.invoke("call-1", LoadSkillResourceToolPortAdapter.TOOL_ID, Map.of(
                "skillName", "research",
                "resourcePath", "SKILL.md"));

        assertFalse(result.success());
        assertEquals("skill is not selected in this Agent version", result.error());
    }

    private SkillRuntimeBlock skill(String name) {
        return new SkillRuntimeBlock(
                name,
                "rev-1",
                "hash-1",
                "Research workflow",
                AgentSkillCategory.PUBLIC,
                SkillInjectMode.METADATA_ONLY,
                List.of("web_search"),
                "Use sources carefully.");
    }
}

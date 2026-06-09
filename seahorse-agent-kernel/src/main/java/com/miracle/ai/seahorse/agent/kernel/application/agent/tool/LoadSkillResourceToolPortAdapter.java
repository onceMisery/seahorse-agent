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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.skill.SkillRuntimeBlock;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.DescribedToolPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolDescriptor;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ToolInvocationResult;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class LoadSkillResourceToolPortAdapter implements DescribedToolPort {

    public static final String TOOL_ID = "load_skill_resource";
    public static final String RUNTIME_SKILLS_ARGUMENT = "_seahorseSkillRuntimeBlocks";

    private static final String SKILL_MD = "SKILL.md";
    private static final ToolDescriptor DESCRIPTOR = new ToolDescriptor(
            TOOL_ID,
            "Load Skill Resource",
            "Load SKILL.md for a skill selected in the current Agent runtime snapshot.",
            "{\"type\":\"object\",\"properties\":{\"skillName\":{\"type\":\"string\"},"
                    + "\"resourcePath\":{\"type\":\"string\"}},\"required\":[\"skillName\",\"resourcePath\"]}");

    private final AgentToolJsonSupport jsonSupport;

    public LoadSkillResourceToolPortAdapter(AgentToolJsonSupport jsonSupport) {
        this.jsonSupport = Objects.requireNonNullElseGet(jsonSupport, () -> new AgentToolJsonSupport(null));
    }

    @Override
    public ToolDescriptor descriptor() {
        return DESCRIPTOR;
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        if (!TOOL_ID.equals(toolId)) {
            return ToolInvocationResult.failed("Tool id mismatch");
        }
        String skillName = jsonSupport.string(arguments, "skillName");
        if (skillName.isBlank()) {
            return ToolInvocationResult.failed("skill name is required");
        }
        String resourcePath = jsonSupport.string(arguments, "resourcePath");
        if (resourcePath.isBlank()) {
            return ToolInvocationResult.failed("skill resource path is required");
        }
        if (!allowedResourcePath(resourcePath)) {
            return ToolInvocationResult.failed("skill resource path is not allowed");
        }
        if (!SKILL_MD.equals(resourcePath)) {
            return ToolInvocationResult.failed("skill resource is not available");
        }

        return runtimeSkills(arguments).stream()
                .filter(skill -> skill.name().equals(skillName))
                .findFirst()
                .map(skill -> ToolInvocationResult.ok(skillPayload(skill, resourcePath)))
                .orElseGet(() -> ToolInvocationResult.failed("skill is not selected in this Agent version"));
    }

    private boolean allowedResourcePath(String resourcePath) {
        String normalized = resourcePath.replace('\\', '/');
        return !normalized.startsWith("/")
                && !normalized.matches("^[A-Za-z]:/.*")
                && !normalized.contains("../")
                && !normalized.contains("/..")
                && !"..".equals(normalized);
    }

    private List<SkillRuntimeBlock> runtimeSkills(Map<String, Object> arguments) {
        Object value = arguments == null ? null : arguments.get(RUNTIME_SKILLS_ARGUMENT);
        if (!(value instanceof List<?> values)) {
            return List.of();
        }
        return values.stream()
                .filter(SkillRuntimeBlock.class::isInstance)
                .map(SkillRuntimeBlock.class::cast)
                .filter(skill -> !skill.content().isBlank())
                .toList();
    }

    private String skillPayload(SkillRuntimeBlock skill, String resourcePath) {
        LinkedHashMap<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", skill.name());
        payload.put("revisionId", skill.revisionId());
        payload.put("contentHash", skill.contentHash());
        payload.put("description", skill.description());
        payload.put("category", skill.category().name());
        payload.put("injectMode", skill.injectMode().name());
        payload.put("allowedTools", skill.allowedTools());
        payload.put("resourcePath", resourcePath);
        payload.put("content", skill.content());
        return jsonSupport.write(payload);
    }
}

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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.factory;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public record AgentTemplate(AgentTemplateId templateId,
                            AgentTemplateStatus status,
                            String name,
                            String description,
                            AgentType agentType,
                            AgentRiskLevel riskCap,
                            List<String> allowedToolIds,
                            String baseInstructions,
                            String guardrailConfigJson) {

    public static final String TOOL_SUBSET_ERROR = "Requested tools must be a subset of template tools";
    public static final String RISK_CAP_ERROR = "Requested risk level exceeds template risk cap";
    public static final String EMPTY_JSON_OBJECT = "{}";

    public AgentTemplate {
        templateId = Objects.requireNonNull(templateId, "templateId must not be null");
        status = Objects.requireNonNullElse(status, AgentTemplateStatus.DISABLED);
        name = requireText(name, "name must not be blank");
        description = trimToNull(description);
        agentType = Objects.requireNonNullElse(agentType, AgentType.ASSISTANT);
        riskCap = Objects.requireNonNullElse(riskCap, AgentRiskLevel.LOW);
        allowedToolIds = copyToolIds(allowedToolIds);
        baseInstructions = requireText(baseInstructions, "baseInstructions must not be blank");
        guardrailConfigJson = defaultJson(guardrailConfigJson);
    }

    public void validateRequestedTools(List<String> requestedToolIds) {
        Set<String> requested = new LinkedHashSet<>(copyToolIds(requestedToolIds));
        if (!new LinkedHashSet<>(allowedToolIds).containsAll(requested)) {
            throw new IllegalArgumentException(TOOL_SUBSET_ERROR);
        }
    }

    public void validateRequestedRisk(AgentRiskLevel requestedRiskLevel) {
        AgentRiskLevel safeRiskLevel = Objects.requireNonNullElse(requestedRiskLevel, riskCap);
        if (safeRiskLevel.ordinal() > riskCap.ordinal()) {
            throw new IllegalArgumentException(RISK_CAP_ERROR);
        }
    }

    public String mergeInstructionsOverlay(String instructionsOverlay) {
        String overlay = trimToNull(instructionsOverlay);
        if (overlay == null) {
            return baseInstructions;
        }
        return baseInstructions + System.lineSeparator() + overlay;
    }

    private static List<String> copyToolIds(List<String> toolIds) {
        if (toolIds == null) {
            return List.of();
        }
        return toolIds.stream()
                .map(AgentTemplate::requireToolId)
                .distinct()
                .toList();
    }

    private static String requireToolId(String value) {
        return requireText(value, "toolId must not be blank");
    }

    private static String defaultJson(String value) {
        String trimmed = trimToNull(value);
        return trimmed == null ? EMPTY_JSON_OBJECT : trimmed;
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

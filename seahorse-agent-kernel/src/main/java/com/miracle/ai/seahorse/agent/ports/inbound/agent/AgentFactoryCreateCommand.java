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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplateId;

import java.util.List;
import java.util.Objects;

public record AgentFactoryCreateCommand(AgentTemplateId templateId,
                                        String tenantId,
                                        String agentId,
                                        String name,
                                        String description,
                                        String ownerUserId,
                                        String ownerTeam,
                                        List<String> requestedToolIds,
                                        AgentRiskLevel riskLevel,
                                        String instructionsOverlay) {

    public AgentFactoryCreateCommand {
        templateId = Objects.requireNonNull(templateId, "templateId must not be null");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        agentId = requireText(agentId, "agentId must not be blank");
        name = requireText(name, "name must not be blank");
        description = trimToNull(description);
        ownerUserId = requireText(ownerUserId, "ownerUserId must not be blank");
        ownerTeam = trimToNull(ownerTeam);
        requestedToolIds = requestedToolIds == null ? List.of() : List.copyOf(requestedToolIds);
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        instructionsOverlay = trimToNull(instructionsOverlay);
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

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

import java.time.Instant;
import java.util.Objects;

public record AgentCatalogEntry(String agentId,
                                String tenantId,
                                String name,
                                String description,
                                String ownerUserId,
                                String ownerTeam,
                                AgentType agentType,
                                AgentRiskLevel riskLevel,
                                String latestVersionId,
                                Instant publishedAt) {

    public AgentCatalogEntry {
        agentId = requireText(agentId, "agentId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        name = requireText(name, "name must not be blank");
        ownerUserId = requireText(ownerUserId, "ownerUserId must not be blank");
        ownerTeam = trimToNull(ownerTeam);
        agentType = Objects.requireNonNull(agentType, "agentType must not be null");
        riskLevel = Objects.requireNonNull(riskLevel, "riskLevel must not be null");
        latestVersionId = requireText(latestVersionId, "latestVersionId must not be blank");
        publishedAt = Objects.requireNonNull(publishedAt, "publishedAt must not be null");
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

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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;

import java.util.List;

public record MeshPolicyRequest(String tenantId,
                                String parentRunId,
                                String sourceAgentId,
                                String targetAgentId,
                                int depth,
                                List<String> ancestorAgentIds) {

    public MeshPolicyRequest {
        tenantId = defaultText(tenantId, AgentDefinition.DEFAULT_TENANT_ID);
        parentRunId = requireText(parentRunId, "parentRunId must not be blank");
        sourceAgentId = requireText(sourceAgentId, "sourceAgentId must not be blank");
        targetAgentId = requireText(targetAgentId, "targetAgentId must not be blank");
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
        ancestorAgentIds = List.copyOf(ancestorAgentIds == null ? List.of() : ancestorAgentIds);
    }

    private static String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
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

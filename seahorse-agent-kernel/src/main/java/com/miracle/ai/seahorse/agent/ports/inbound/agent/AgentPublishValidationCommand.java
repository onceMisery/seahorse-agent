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

import java.util.List;

public record AgentPublishValidationCommand(String agentId,
                                            String versionId,
                                            String instructions,
                                            List<String> toolIds,
                                            String ownerUserId,
                                            String ownerTeam,
                                            String changeSummary) {

    public AgentPublishValidationCommand {
        agentId = requireText(agentId, "agentId must not be blank");
        versionId = trimToNull(versionId);
        instructions = trimToNull(instructions);
        toolIds = toolIds == null ? List.of() : List.copyOf(toolIds);
        ownerUserId = trimToNull(ownerUserId);
        ownerTeam = trimToNull(ownerTeam);
        changeSummary = trimToNull(changeSummary);
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

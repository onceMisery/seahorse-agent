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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.context;

import java.util.List;

public record ContextBuildRequest(String runId,
                                  String agentId,
                                  String versionId,
                                  String tenantId,
                                  String userId,
                                  String taskGoal,
                                  int budgetTokens,
                                  List<ContextBuildItemCandidate> candidates) {

    public ContextBuildRequest {
        runId = requireText(runId, "runId must not be blank");
        agentId = trimToNull(agentId);
        versionId = trimToNull(versionId);
        tenantId = requireText(tenantId, "tenantId must not be blank");
        userId = requireText(userId, "userId must not be blank");
        taskGoal = requireText(taskGoal, "taskGoal must not be blank");
        if (budgetTokens <= 0) {
            throw new IllegalArgumentException("budgetTokens must be greater than 0");
        }
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
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

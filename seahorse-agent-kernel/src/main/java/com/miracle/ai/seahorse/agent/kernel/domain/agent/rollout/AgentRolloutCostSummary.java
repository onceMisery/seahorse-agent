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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record AgentRolloutCostSummary(String rolloutId,
                                      String tenantId,
                                      String agentId,
                                      String versionId,
                                      Instant windowFrom,
                                      Instant windowTo,
                                      String aggregationScope,
                                      long totalTokens,
                                      long totalCalls,
                                      double totalCost,
                                      long recordCount,
                                      Map<String, Long> runStatusCounts,
                                      long totalRuns,
                                      long succeededRuns,
                                      long failedRuns,
                                      long waitingApprovalRuns,
                                      double errorRate,
                                      long pendingApprovalCount) {

    public AgentRolloutCostSummary(String rolloutId,
                                   String tenantId,
                                   String agentId,
                                   String versionId,
                                   Instant windowFrom,
                                   Instant windowTo,
                                   String aggregationScope,
                                   long totalTokens,
                                   long totalCalls,
                                   double totalCost,
                                   long recordCount) {
        this(rolloutId,
                tenantId,
                agentId,
                versionId,
                windowFrom,
                windowTo,
                aggregationScope,
                totalTokens,
                totalCalls,
                totalCost,
                recordCount,
                Map.of(),
                0L,
                0L,
                0L,
                0L,
                0.0,
                0L);
    }

    public AgentRolloutCostSummary {
        rolloutId = requireText(rolloutId, "rolloutId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        agentId = requireText(agentId, "agentId must not be blank");
        versionId = requireText(versionId, "versionId must not be blank");
        windowFrom = Objects.requireNonNull(windowFrom, "windowFrom must not be null");
        windowTo = Objects.requireNonNull(windowTo, "windowTo must not be null");
        aggregationScope = requireText(aggregationScope, "aggregationScope must not be blank");
        runStatusCounts = runStatusCounts == null ? Map.of() : Map.copyOf(runStatusCounts);
        if (totalTokens < 0 || totalCalls < 0 || totalCost < 0 || recordCount < 0
                || totalRuns < 0 || succeededRuns < 0 || failedRuns < 0
                || waitingApprovalRuns < 0 || pendingApprovalCount < 0) {
            throw new IllegalArgumentException("cost summary values must not be negative");
        }
        if (errorRate < 0.0 || errorRate > 1.0) {
            throw new IllegalArgumentException("errorRate must be between 0 and 1");
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

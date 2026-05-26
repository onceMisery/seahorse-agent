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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.cost;

import java.time.Instant;
import java.util.Objects;

public record CostUsageRecord(String usageId,
                              String tenantId,
                              String agentId,
                              String runId,
                              String userId,
                              String toolId,
                              String modelId,
                              CostUsageSource source,
                              long tokens,
                              long calls,
                              double cost,
                              String reasonRef,
                              Instant createdAt) {

    public CostUsageRecord {
        usageId = requireText(usageId, "usageId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        agentId = trimToNull(agentId);
        runId = trimToNull(runId);
        userId = trimToNull(userId);
        toolId = trimToNull(toolId);
        modelId = trimToNull(modelId);
        source = Objects.requireNonNull(source, "source must not be null");
        if (tokens < 0) {
            throw new IllegalArgumentException("tokens must not be negative");
        }
        if (calls < 0) {
            throw new IllegalArgumentException("calls must not be negative");
        }
        if (cost < 0) {
            throw new IllegalArgumentException("cost must not be negative");
        }
        reasonRef = trimToNull(reasonRef);
        if (source == CostUsageSource.MANUAL_ADJUSTMENT && reasonRef == null) {
            throw new IllegalArgumentException("reasonRef is required for manual adjustment");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
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

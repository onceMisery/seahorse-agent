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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalType;

public record AgentEvalSummaryQuery(String tenantId,
                                    String agentId,
                                    String versionId,
                                    AgentEvalType evalType,
                                    long current,
                                    long size) {

    public AgentEvalSummaryQuery {
        tenantId = requireText(tenantId, "tenantId must not be blank");
        agentId = requireText(agentId, "agentId must not be blank");
        versionId = requireText(versionId, "versionId must not be blank");
        current = current <= 0 ? AgentEvalLimits.DEFAULT_HISTORY_CURRENT : current;
        if (size <= 0) {
            size = AgentEvalLimits.DEFAULT_HISTORY_SIZE;
        }
        size = Math.min(size, AgentEvalLimits.MAX_HISTORY_SIZE);
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

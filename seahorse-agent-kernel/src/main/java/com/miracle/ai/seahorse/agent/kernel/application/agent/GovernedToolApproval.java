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

package com.miracle.ai.seahorse.agent.kernel.application.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record GovernedToolApproval(
        String approvalId,
        String toolInvocationId,
        String toolId,
        ToolRiskLevel riskLevel,
        String summary,
        Map<String, Object> arguments,
        Instant requestedAt) {

    public GovernedToolApproval {
        approvalId = defaultText(approvalId, "");
        toolInvocationId = defaultText(toolInvocationId, "");
        toolId = defaultText(toolId, "");
        riskLevel = Objects.requireNonNullElse(riskLevel, ToolRiskLevel.MEDIUM);
        summary = defaultText(summary, "Tool call requires approval");
        arguments = Map.copyOf(Objects.requireNonNullElse(arguments, Map.of()));
        requestedAt = Objects.requireNonNullElseGet(requestedAt, Instant::now);
    }

    private static String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? Objects.requireNonNullElse(fallback, "") : value.trim();
    }
}

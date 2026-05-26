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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.task;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.EstimatedDurationTier;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaCostTier;

import java.util.Objects;

public record TaskTemplate(TaskTemplateId templateId,
                           String name,
                           String description,
                           TaskTemplateCategory category,
                           String defaultAgentId,
                           String defaultToolPolicyId,
                           TaskTemplateOutputType defaultOutputType,
                           QuotaCostTier maxCostTier,
                           EstimatedDurationTier estimatedDuration,
                           boolean enabled) {

    public TaskTemplate {
        templateId = Objects.requireNonNull(templateId, "templateId must not be null");
        name = requireText(name, "name must not be blank");
        description = trimToNull(description);
        category = Objects.requireNonNullElse(category, TaskTemplateCategory.RESEARCH);
        defaultAgentId = trimToNull(defaultAgentId);
        defaultToolPolicyId = trimToNull(defaultToolPolicyId);
        defaultOutputType = Objects.requireNonNullElse(defaultOutputType, TaskTemplateOutputType.PLAIN_TEXT);
        maxCostTier = Objects.requireNonNullElse(maxCostTier, QuotaCostTier.LOW);
        estimatedDuration = Objects.requireNonNullElse(estimatedDuration, EstimatedDurationTier.SHORT);
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

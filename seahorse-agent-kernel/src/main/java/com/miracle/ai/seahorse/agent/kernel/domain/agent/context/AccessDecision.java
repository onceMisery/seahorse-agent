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

import java.time.Instant;
import java.util.Objects;

public record AccessDecision(String decisionId,
                             String tenantId,
                             AccessSubjectType subjectType,
                             String subjectId,
                             ResourceAction action,
                             String resourceType,
                             String resourceId,
                             AccessDecisionEffect effect,
                             String reasonCode,
                             Instant createdAt) {

    public AccessDecision {
        decisionId = requireText(decisionId, "decisionId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        subjectType = Objects.requireNonNull(subjectType, "subjectType must not be null");
        subjectId = requireText(subjectId, "subjectId must not be blank");
        action = Objects.requireNonNull(action, "action must not be null");
        resourceType = requireText(resourceType, "resourceType must not be blank");
        resourceId = requireText(resourceId, "resourceId must not be blank");
        effect = Objects.requireNonNull(effect, "effect must not be null");
        reasonCode = trimToNull(reasonCode);
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

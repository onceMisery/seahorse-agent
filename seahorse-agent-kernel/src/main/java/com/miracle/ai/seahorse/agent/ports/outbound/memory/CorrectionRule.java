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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.time.Instant;
import java.util.Objects;

/**
 * Active user correction rule.
 */
public record CorrectionRule(
        String id,
        String userId,
        String tenantId,
        String correctionType,
        String targetKind,
        String targetKey,
        String incorrectValue,
        String correctValue,
        String ruleText,
        String priority,
        String generationId,
        String status,
        Instant updatedAt
) {

    public CorrectionRule {
        id = Objects.requireNonNullElse(id, "");
        userId = Objects.requireNonNullElse(userId, "");
        tenantId = Objects.requireNonNullElse(tenantId, "default");
        correctionType = Objects.requireNonNullElse(correctionType, "");
        targetKind = Objects.requireNonNullElse(targetKind, "");
        targetKey = Objects.requireNonNullElse(targetKey, "");
        incorrectValue = Objects.requireNonNullElse(incorrectValue, "");
        correctValue = Objects.requireNonNullElse(correctValue, "");
        ruleText = Objects.requireNonNullElse(ruleText, "");
        priority = Objects.requireNonNullElse(priority, "HARD_RULE");
        generationId = Objects.requireNonNullElse(generationId, "");
        status = Objects.requireNonNullElse(status, "ACTIVE");
        updatedAt = Objects.requireNonNullElse(updatedAt, Instant.EPOCH);
    }
}

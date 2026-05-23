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

public record ContextItem(String itemId,
                          String contextPackId,
                          ContextItemSourceType sourceType,
                          String sourceId,
                          String content,
                          String summary,
                          double score,
                          double confidence,
                          ContextSensitivity sensitivity,
                          String aclDecisionId,
                          String citationJson,
                          int estimatedTokens,
                          Instant expiresAt,
                          Instant createdAt) {

    public ContextItem {
        itemId = requireText(itemId, "itemId must not be blank");
        contextPackId = requireText(contextPackId, "contextPackId must not be blank");
        sourceType = Objects.requireNonNull(sourceType, "sourceType must not be null");
        sourceId = requireText(sourceId, "sourceId must not be blank");
        content = requireText(content, "content must not be blank");
        summary = trimToNull(summary);
        requireRatio(score, "score");
        requireRatio(confidence, "confidence");
        sensitivity = Objects.requireNonNullElse(sensitivity, ContextSensitivity.INTERNAL);
        aclDecisionId = requireText(aclDecisionId, "aclDecisionId must not be blank");
        citationJson = requireText(citationJson, "citationJson must not be blank");
        if (estimatedTokens <= 0) {
            throw new IllegalArgumentException("estimatedTokens must be greater than 0");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    private static void requireRatio(double value, String name) {
        if (value < 0.0d || value > 1.0d) {
            throw new IllegalArgumentException(name + " must be between 0 and 1");
        }
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

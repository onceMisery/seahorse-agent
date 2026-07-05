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

package com.miracle.ai.seahorse.agent.ports.inbound.gate;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record GateResult(String subjectType,
                         String subjectId,
                         String status,
                         boolean passed,
                         List<String> blockingCodes,
                         List<GateResultItem> items,
                         Instant checkedAt,
                         String sourceType,
                         String sourceId) {

    public GateResult {
        subjectType = requireText(subjectType, "subjectType must not be blank");
        subjectId = requireText(subjectId, "subjectId must not be blank");
        status = normalizeStatus(status);
        blockingCodes = blockingCodes == null ? List.of() : List.copyOf(blockingCodes);
        items = items == null ? List.of() : List.copyOf(items);
        checkedAt = Objects.requireNonNullElseGet(checkedAt, Instant::now);
        sourceType = trimToNull(sourceType);
        sourceId = trimToNull(sourceId);
    }

    private static String normalizeStatus(String value) {
        String normalized = requireText(value, "status must not be blank").toUpperCase();
        return "BLOCK".equals(normalized) ? "FAIL" : normalized;
    }

    private static String requireText(String value, String message) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException(message);
        }
        return trimmed;
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

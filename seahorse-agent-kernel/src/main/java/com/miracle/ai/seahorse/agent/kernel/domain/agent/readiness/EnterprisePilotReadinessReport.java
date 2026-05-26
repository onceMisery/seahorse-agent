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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.readiness;

import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record EnterprisePilotReadinessReport(String reportId,
                                             String tenantId,
                                             String agentId,
                                             String versionId,
                                             EnterprisePilotReadinessStatus status,
                                             List<EnterprisePilotReadinessCheckResult> checkResults,
                                             Instant createdAt) {

    public EnterprisePilotReadinessReport {
        reportId = requireText(reportId, "reportId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        agentId = requireText(agentId, "agentId must not be blank");
        versionId = requireText(versionId, "versionId must not be blank");
        checkResults = checkResults == null ? List.of() : List.copyOf(checkResults);
        requireCompleteCheckCodes(checkResults);
        status = status == null ? aggregate(checkResults) : status;
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public Optional<EnterprisePilotReadinessCheckResult> result(EnterprisePilotReadinessCheckCode code) {
        return checkResults.stream()
                .filter(result -> result.code() == code)
                .findFirst();
    }

    private static EnterprisePilotReadinessStatus aggregate(List<EnterprisePilotReadinessCheckResult> results) {
        EnterprisePilotReadinessStatus current = EnterprisePilotReadinessStatus.PASS;
        for (EnterprisePilotReadinessCheckResult result : results) {
            if (result.status().isMoreSevereThan(current)) {
                current = result.status();
            }
        }
        return current;
    }

    private static void requireCompleteCheckCodes(List<EnterprisePilotReadinessCheckResult> results) {
        Map<EnterprisePilotReadinessCheckCode, EnterprisePilotReadinessCheckResult> byCode =
                new EnumMap<>(EnterprisePilotReadinessCheckCode.class);
        for (EnterprisePilotReadinessCheckResult result : results) {
            if (byCode.put(result.code(), result) != null) {
                throw new IllegalArgumentException("duplicate readiness check code: " + result.code().name());
            }
        }
        if (!byCode.keySet().containsAll(EnterprisePilotReadinessCheckCode.all())) {
            throw new IllegalArgumentException("readiness report must contain all check codes");
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

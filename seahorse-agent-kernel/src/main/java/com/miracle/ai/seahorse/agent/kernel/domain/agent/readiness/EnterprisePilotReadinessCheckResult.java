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
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public record EnterprisePilotReadinessCheckResult(EnterprisePilotReadinessCheckCode code,
                                                  EnterprisePilotReadinessStatus status,
                                                  EnterprisePilotReadinessReasonCode reasonCode,
                                                  String evidenceRef,
                                                  String message,
                                                  Instant checkedAt) {

    private static final List<String> FORBIDDEN_EVIDENCE_FRAGMENTS = List.of(
            "secret-token",
            "rawprompt",
            "rawtooloutput",
            "stacktrace",
            "credential:",
            "bearer ");

    public EnterprisePilotReadinessCheckResult {
        code = Objects.requireNonNull(code, "code must not be null");
        status = Objects.requireNonNullElse(status, EnterprisePilotReadinessStatus.FAIL);
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        evidenceRef = requireSafeEvidenceRef(evidenceRef);
        message = defaultText(message, code.name());
        checkedAt = Objects.requireNonNull(checkedAt, "checkedAt must not be null");
    }

    public EnterprisePilotReadinessCheckResult withStatus(EnterprisePilotReadinessStatus nextStatus,
                                                          EnterprisePilotReadinessReasonCode nextReasonCode) {
        return new EnterprisePilotReadinessCheckResult(
                code,
                nextStatus,
                nextReasonCode,
                evidenceRef,
                message,
                checkedAt);
    }

    private static String requireSafeEvidenceRef(String value) {
        String trimmed = trimToNull(value);
        if (trimmed == null) {
            throw new IllegalArgumentException("evidenceRef must not be blank");
        }
        String normalized = trimmed.toLowerCase(Locale.ROOT);
        if (FORBIDDEN_EVIDENCE_FRAGMENTS.stream().anyMatch(normalized::contains)) {
            throw new IllegalArgumentException("evidenceRef must not contain raw sensitive evidence");
        }
        return trimmed;
    }

    private static String defaultText(String value, String fallback) {
        String trimmed = trimToNull(value);
        return trimmed == null ? fallback : trimmed;
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

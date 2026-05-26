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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.eval;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

public record AgentEvalSummary(String summaryId,
                               String tenantId,
                               String agentId,
                               String versionId,
                               AgentEvalType evalType,
                               AgentEvalStatus status,
                               double score,
                               double passThreshold,
                               double warnThreshold,
                               int caseCount,
                               String datasetRef,
                               String evalRunRef,
                               List<String> evidenceRefs,
                               String createdBy,
                               Instant createdAt) {

    public AgentEvalSummary {
        summaryId = requireText(summaryId, "summaryId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        agentId = requireText(agentId, "agentId must not be blank");
        versionId = requireText(versionId, "versionId must not be blank");
        evalType = Objects.requireNonNull(evalType, "evalType must not be null");
        status = Objects.requireNonNull(status, "status must not be null");
        requireNonNegative(score, "score must not be negative");
        requireNonNegative(passThreshold, "passThreshold must not be negative");
        requireNonNegative(warnThreshold, "warnThreshold must not be negative");
        if (passThreshold < warnThreshold) {
            throw new IllegalArgumentException("passThreshold must be greater than or equal to warnThreshold");
        }
        if (caseCount < 0) {
            throw new IllegalArgumentException("caseCount must not be negative");
        }
        datasetRef = trimToNull(datasetRef);
        evalRunRef = trimToNull(evalRunRef);
        evidenceRefs = evidenceRefs == null
                ? List.of()
                : evidenceRefs.stream().map(AgentEvalSummary::trimToNull).filter(Objects::nonNull).toList();
        createdBy = requireText(createdBy, "createdBy must not be blank");
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public AgentEvalStatus effectiveStatus(Instant now) {
        Instant safeNow = Objects.requireNonNull(now, "now must not be null");
        if (status == AgentEvalStatus.FAIL || status == AgentEvalStatus.STALE) {
            return status;
        }
        Instant staleBefore = safeNow.minus(AgentEvalLimits.DEFAULT_MAX_AGE_DAYS, ChronoUnit.DAYS);
        if (createdAt.isBefore(staleBefore)) {
            return AgentEvalStatus.STALE;
        }
        return status;
    }

    private static void requireNonNegative(double value, String message) {
        if (value < 0) {
            throw new IllegalArgumentException(message);
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

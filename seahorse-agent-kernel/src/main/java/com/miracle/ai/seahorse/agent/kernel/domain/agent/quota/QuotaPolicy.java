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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.quota;

import java.time.Instant;
import java.util.Objects;

public record QuotaPolicy(String policyId,
                          String tenantId,
                          QuotaScope scope,
                          String subjectId,
                          QuotaPolicyStatus status,
                          Long tokenLimit,
                          Long callLimit,
                          Double costLimit,
                          double warnRatio,
                          Instant createdAt,
                          Instant updatedAt) {

    public QuotaPolicy {
        policyId = requireText(policyId, "policyId must not be blank");
        tenantId = requireText(tenantId, "tenantId must not be blank");
        scope = Objects.requireNonNull(scope, "scope must not be null");
        subjectId = requireText(subjectId, "subjectId must not be blank");
        status = Objects.requireNonNullElse(status, QuotaPolicyStatus.ACTIVE);
        if (tokenLimit == null && callLimit == null && costLimit == null) {
            throw new IllegalArgumentException("at least one quota limit is required");
        }
        requireNonNegative(tokenLimit, "tokenLimit must not be negative");
        requireNonNegative(callLimit, "callLimit must not be negative");
        requireNonNegative(costLimit, "costLimit must not be negative");
        if (warnRatio <= QuotaPolicyLimits.MIN_WARN_RATIO || warnRatio > QuotaPolicyLimits.MAX_WARN_RATIO) {
            throw new IllegalArgumentException("warnRatio must be within (0, 1]");
        }
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
        updatedAt = Objects.requireNonNullElse(updatedAt, createdAt);
    }

    public QuotaPolicy disable(Instant disabledAt) {
        return new QuotaPolicy(
                policyId,
                tenantId,
                scope,
                subjectId,
                QuotaPolicyStatus.DISABLED,
                tokenLimit,
                callLimit,
                costLimit,
                warnRatio,
                createdAt,
                Objects.requireNonNull(disabledAt, "disabledAt must not be null"));
    }

    public boolean exceededBy(QuotaUsage usage) {
        QuotaUsage safeUsage = Objects.requireNonNull(usage, "usage must not be null");
        return exceeds(tokenLimit, safeUsage.tokens())
                || exceeds(callLimit, safeUsage.calls())
                || exceeds(costLimit, safeUsage.cost());
    }

    public boolean warnThresholdReachedBy(QuotaUsage usage) {
        QuotaUsage safeUsage = Objects.requireNonNull(usage, "usage must not be null");
        return reachesWarn(tokenLimit, safeUsage.tokens())
                || reachesWarn(callLimit, safeUsage.calls())
                || reachesWarn(costLimit, safeUsage.cost());
    }

    private boolean exceeds(Long limit, long used) {
        return limit != null && used > limit;
    }

    private boolean exceeds(Double limit, double used) {
        return limit != null && used > limit;
    }

    private boolean reachesWarn(Long limit, long used) {
        return limit != null && used >= Math.ceil(limit * warnRatio);
    }

    private boolean reachesWarn(Double limit, double used) {
        return limit != null && used >= limit * warnRatio;
    }

    private static void requireNonNegative(Long value, String message) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static void requireNonNegative(Double value, String message) {
        if (value != null && value < 0) {
            throw new IllegalArgumentException(message);
        }
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

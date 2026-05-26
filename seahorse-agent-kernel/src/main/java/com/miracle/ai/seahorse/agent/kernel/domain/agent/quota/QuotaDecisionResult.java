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

public record QuotaDecisionResult(QuotaDecisionEffect effect,
                                  QuotaDecisionReasonCode reasonCode,
                                  String policyId,
                                  QuotaUsage requestedUsage,
                                  Instant decidedAt) {

    public QuotaDecisionResult {
        effect = Objects.requireNonNull(effect, "effect must not be null");
        reasonCode = Objects.requireNonNull(reasonCode, "reasonCode must not be null");
        policyId = trimToNull(policyId);
        requestedUsage = Objects.requireNonNull(requestedUsage, "requestedUsage must not be null");
        decidedAt = Objects.requireNonNull(decidedAt, "decidedAt must not be null");
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

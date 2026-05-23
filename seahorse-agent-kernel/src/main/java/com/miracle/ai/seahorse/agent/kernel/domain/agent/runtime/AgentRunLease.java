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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime;

import java.time.Instant;
import java.util.Objects;

/**
 * Worker lease for an agent run. It prevents multiple workers from executing the same run at the same time.
 *
 * @param runId       run ID
 * @param workerId    worker ID holding the lease
 * @param leaseUntil  lease expiration time
 * @param heartbeatAt last heartbeat time
 */
public record AgentRunLease(String runId, String workerId, Instant leaseUntil, Instant heartbeatAt) {

    public AgentRunLease {
        runId = requireText(runId, "runId must not be blank");
        workerId = requireText(workerId, "workerId must not be blank");
        leaseUntil = Objects.requireNonNull(leaseUntil, "leaseUntil must not be null");
        heartbeatAt = Objects.requireNonNull(heartbeatAt, "heartbeatAt must not be null");
        if (!leaseUntil.isAfter(heartbeatAt)) {
            throw new IllegalArgumentException("leaseUntil must be after heartbeatAt");
        }
    }

    public boolean isExpiredAt(Instant now) {
        Instant safeNow = Objects.requireNonNull(now, "now must not be null");
        return !leaseUntil.isAfter(safeNow);
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

public record AgentRunWorkerCommand(String tenantId,
                                    String workerId,
                                    int maxRuns,
                                    Duration leaseTtl,
                                    Instant now) {

    public AgentRunWorkerCommand {
        tenantId = requireText(tenantId, "tenantId must not be blank");
        workerId = requireText(workerId, "workerId must not be blank");
        leaseTtl = Objects.requireNonNull(leaseTtl, "leaseTtl must not be null");
        if (leaseTtl.isZero() || leaseTtl.isNegative()) {
            throw new IllegalArgumentException("leaseTtl must be positive");
        }
        now = Objects.requireNonNull(now, "now must not be null");
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

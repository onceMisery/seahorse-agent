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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunWorkerOutcome;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunWorkerSkipReason;

import java.util.Objects;

public record AgentRunWorkerTickRecord(String runId,
                                       AgentRunWorkerOutcome outcome,
                                       AgentRunWorkerSkipReason skipReason,
                                       String message) {

    public AgentRunWorkerTickRecord {
        runId = trimToNull(runId);
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        message = trimToNull(message);
    }

    public static AgentRunWorkerTickRecord of(String runId,
                                              AgentRunWorkerOutcome outcome,
                                              AgentRunWorkerSkipReason skipReason) {
        return new AgentRunWorkerTickRecord(runId, outcome, skipReason, null);
    }

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }
}

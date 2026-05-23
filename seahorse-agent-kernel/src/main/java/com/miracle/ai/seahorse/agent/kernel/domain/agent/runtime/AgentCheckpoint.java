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
 * Agent run checkpoint. It stores resumable execution state without binding the kernel to a storage technology.
 */
public record AgentCheckpoint(String checkpointId,
                              String runId,
                              String stepId,
                              long sequenceNo,
                              AgentCheckpointType checkpointType,
                              String stateJson,
                              String messageHistoryJson,
                              String contextPackId,
                              String pendingToolCallJson,
                              Instant createdAt) {

    public AgentCheckpoint {
        checkpointId = requireText(checkpointId, "checkpointId must not be blank");
        runId = requireText(runId, "runId must not be blank");
        stepId = trimToNull(stepId);
        if (sequenceNo <= 0L) {
            throw new IllegalArgumentException("sequenceNo must be greater than 0");
        }
        checkpointType = Objects.requireNonNull(checkpointType, "checkpointType must not be null");
        stateJson = requireText(stateJson, "stateJson must not be blank");
        messageHistoryJson = trimToNull(messageHistoryJson);
        contextPackId = trimToNull(contextPackId);
        pendingToolCallJson = trimToNull(pendingToolCallJson);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
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

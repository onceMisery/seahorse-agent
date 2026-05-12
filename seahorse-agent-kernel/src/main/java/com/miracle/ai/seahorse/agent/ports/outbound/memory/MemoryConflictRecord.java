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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import java.time.Instant;
import java.util.Objects;

public record MemoryConflictRecord(
        String id,
        String userId,
        String memoryId1,
        String memoryId2,
        String conflictType,
        String severity,
        String resolutionStatus,
        String resolutionAction,
        String resolvedBy,
        Instant resolvedAt,
        Instant createTime
) {

    public MemoryConflictRecord {
        id = Objects.requireNonNullElse(id, "");
        userId = Objects.requireNonNullElse(userId, "");
        memoryId1 = Objects.requireNonNullElse(memoryId1, "");
        memoryId2 = Objects.requireNonNullElse(memoryId2, "");
        conflictType = Objects.requireNonNullElse(conflictType, "");
        severity = Objects.requireNonNullElse(severity, "");
        resolutionStatus = Objects.requireNonNullElse(resolutionStatus, "");
        resolutionAction = Objects.requireNonNullElse(resolutionAction, "");
        resolvedBy = Objects.requireNonNullElse(resolvedBy, "");
        resolvedAt = Objects.requireNonNullElse(resolvedAt, Instant.EPOCH);
        createTime = Objects.requireNonNullElse(createTime, Instant.EPOCH);
    }
}

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
import java.util.Map;
import java.util.Objects;

public record MemoryOperationRecord(
        String operationId,
        String userId,
        String tenantId,
        String operationType,
        String targetKind,
        String targetKey,
        Map<String, Object> request,
        Map<String, Object> decision,
        String status,
        String policyVersion,
        String errorMessage,
        Instant createdAt,
        Instant updatedAt
) {

    public MemoryOperationRecord {
        operationId = Objects.requireNonNullElse(operationId, "");
        userId = Objects.requireNonNullElse(userId, "");
        tenantId = Objects.requireNonNullElse(tenantId, "default");
        operationType = Objects.requireNonNullElse(operationType, "");
        targetKind = Objects.requireNonNullElse(targetKind, "");
        targetKey = Objects.requireNonNullElse(targetKey, "");
        request = Map.copyOf(Objects.requireNonNullElse(request, Map.of()));
        decision = Map.copyOf(Objects.requireNonNullElse(decision, Map.of()));
        status = Objects.requireNonNullElse(status, "");
        policyVersion = Objects.requireNonNullElse(policyVersion, "");
        errorMessage = Objects.requireNonNullElse(errorMessage, "");
        createdAt = Objects.requireNonNullElse(createdAt, Instant.EPOCH);
        updatedAt = Objects.requireNonNullElse(updatedAt, Instant.EPOCH);
    }
}

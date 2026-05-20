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

public record MemoryOperation(
        String operationId,
        String userId,
        String tenantId,
        MemoryOperationType operationType,
        String targetKind,
        String targetKey,
        Map<String, Object> request,
        String policyVersion,
        Instant createdAt
) {

    public MemoryOperation {
        operationId = Objects.requireNonNullElse(operationId, "").trim();
        userId = Objects.requireNonNullElse(userId, "").trim();
        tenantId = Objects.requireNonNullElse(tenantId, "default").trim();
        if (tenantId.isBlank()) {
            tenantId = "default";
        }
        operationType = Objects.requireNonNullElse(operationType, MemoryOperationType.IGNORE);
        targetKind = Objects.requireNonNullElse(targetKind, "").trim();
        targetKey = Objects.requireNonNullElse(targetKey, "").trim();
        request = Map.copyOf(Objects.requireNonNullElse(request, Map.of()));
        policyVersion = Objects.requireNonNullElse(policyVersion, "").trim();
        createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
    }
}

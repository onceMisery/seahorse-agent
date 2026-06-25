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
import java.util.List;
import java.util.Objects;

/**
 * Strong user profile fact.
 */
public record ProfileFact(
        String id,
        String userId,
        String tenantId,
        String slotKey,
        String valueText,
        double confidenceLevel,
        String sourceType,
        List<String> sourceIds,
        String generationId,
        String status,
        Instant updatedAt,
        long version,
        Instant lastReferencedAt,
        int accessCount
) {

    public ProfileFact {
        id = Objects.requireNonNullElse(id, "");
        userId = Objects.requireNonNullElse(userId, "");
        tenantId = Objects.requireNonNullElse(tenantId, "default");
        slotKey = Objects.requireNonNullElse(slotKey, "");
        valueText = Objects.requireNonNullElse(valueText, "");
        sourceType = Objects.requireNonNullElse(sourceType, "");
        sourceIds = List.copyOf(Objects.requireNonNullElse(sourceIds, List.of()));
        generationId = Objects.requireNonNullElse(generationId, "");
        status = Objects.requireNonNullElse(status, "ACTIVE");
        updatedAt = Objects.requireNonNullElse(updatedAt, Instant.EPOCH);
        version = version <= 0 ? 1L : version;
        lastReferencedAt = Objects.requireNonNullElse(lastReferencedAt, Instant.EPOCH);
        accessCount = Math.max(0, accessCount);
    }

    public ProfileFact(String id,
                       String userId,
                       String tenantId,
                       String slotKey,
                       String valueText,
                       double confidenceLevel,
                       String sourceType,
                       String generationId,
                       String status,
                       Instant updatedAt) {
        this(id, userId, tenantId, slotKey, valueText, confidenceLevel, sourceType, List.of(), generationId, status,
                updatedAt, 1L, Instant.EPOCH, 0);
    }
}

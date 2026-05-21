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

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record MemoryRecallRequest(
        String userId,
        String tenantId,
        String query,
        Set<MemoryTrack> activeTracks,
        int topK,
        Map<String, Object> filters
) {

    public MemoryRecallRequest {
        userId = normalize(userId, "");
        tenantId = normalize(tenantId, "default");
        query = Objects.requireNonNullElse(query, "");
        activeTracks = activeTracks == null || activeTracks.isEmpty() ? Set.of() : Set.copyOf(activeTracks);
        topK = topK > 0 ? topK : 1;
        filters = Map.copyOf(Objects.requireNonNullElse(filters, Map.of()));
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }
}

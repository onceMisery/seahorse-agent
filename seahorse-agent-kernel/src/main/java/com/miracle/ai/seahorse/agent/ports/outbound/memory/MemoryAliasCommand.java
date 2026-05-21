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

import java.util.List;
import java.util.Map;
import java.util.Objects;

public record MemoryAliasCommand(
        String userId,
        String tenantId,
        String aliasText,
        String canonicalEntityId,
        String canonicalName,
        String entityType,
        double confidenceLevel,
        String sourceType,
        List<String> sourceMemoryIds,
        Map<String, Object> metadata) {

    public MemoryAliasCommand {
        userId = normalize(userId, "");
        tenantId = normalize(tenantId, "default");
        aliasText = normalize(aliasText, "");
        canonicalEntityId = normalize(canonicalEntityId, "");
        canonicalName = normalize(canonicalName, canonicalEntityId);
        entityType = normalize(entityType, "ENTITY");
        confidenceLevel = Math.max(0D, Math.min(1D, confidenceLevel));
        sourceType = normalize(sourceType, "rule");
        sourceMemoryIds = List.copyOf(Objects.requireNonNullElse(sourceMemoryIds, List.of()));
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
    }

    public static MemoryAliasCommand of(String userId,
                                        String tenantId,
                                        String aliasText,
                                        String canonicalEntityId,
                                        String canonicalName,
                                        String entityType) {
        return new MemoryAliasCommand(
                userId,
                tenantId,
                aliasText,
                canonicalEntityId,
                canonicalName,
                entityType,
                1D,
                "rule",
                List.of(),
                Map.of());
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }
}

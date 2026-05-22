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

public record MemoryRefinementMemory(
        String memoryId,
        String layer,
        String type,
        String content,
        String targetKind,
        String targetKey,
        String generationId,
        String status,
        Map<String, Object> metadata
) {

    public MemoryRefinementMemory {
        memoryId = normalize(memoryId, "");
        layer = normalize(layer, "");
        type = normalize(type, "");
        content = Objects.requireNonNullElse(content, "");
        targetKind = normalize(targetKind, "");
        targetKey = normalize(targetKey, "");
        generationId = normalize(generationId, "");
        status = normalize(status, "ACTIVE");
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }
}

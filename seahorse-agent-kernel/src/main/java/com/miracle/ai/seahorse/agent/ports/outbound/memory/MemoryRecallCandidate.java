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

public record MemoryRecallCandidate(
        String memoryId,
        String channel,
        int rank,
        double rawScore,
        String userId,
        String tenantId,
        String layer,
        String type,
        String content,
        String generationId,
        String status,
        Map<String, Object> metadata
) {

    public MemoryRecallCandidate {
        memoryId = normalize(memoryId, "");
        channel = normalize(channel, "");
        rank = rank > 0 ? rank : 1;
        userId = normalize(userId, "");
        tenantId = normalize(tenantId, "default");
        layer = normalize(layer, "");
        type = normalize(type, "");
        content = Objects.requireNonNullElse(content, "");
        generationId = normalize(generationId, "");
        status = normalize(status, "ACTIVE");
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
    }

    public MemoryRecallCandidate withRawScore(double score) {
        return new MemoryRecallCandidate(
                memoryId,
                channel,
                rank,
                score,
                userId,
                tenantId,
                layer,
                type,
                content,
                generationId,
                status,
                metadata);
    }

    public MemoryRecallCandidate withMetadata(Map<String, Object> newMetadata) {
        return new MemoryRecallCandidate(
                memoryId,
                channel,
                rank,
                rawScore,
                userId,
                tenantId,
                layer,
                type,
                content,
                generationId,
                status,
                newMetadata);
    }

    public MemoryRecallCandidate withRankAndScore(int newRank, double score) {
        return new MemoryRecallCandidate(
                memoryId,
                channel,
                newRank,
                score,
                userId,
                tenantId,
                layer,
                type,
                content,
                generationId,
                status,
                metadata);
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }
}

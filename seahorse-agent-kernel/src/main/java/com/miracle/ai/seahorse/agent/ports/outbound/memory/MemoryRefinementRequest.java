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

public record MemoryRefinementRequest(
        String operationId,
        String tenantId,
        String source,
        String userId,
        String conversationId,
        String messageId,
        String sanitizedContent,
        MemoryIngestionAction baselineAction,
        String baselineMemoryType,
        String baselineReason,
        Map<String, Object> baselineDetails,
        List<MemoryRefinementMemory> existingMemories,
        String referenceZone,
        String targetZone,
        List<MemoryRefinementMemory> stickyAnchors
) {

    private static final String DEFAULT_TENANT_ID = "default";

    public MemoryRefinementRequest {
        operationId = normalize(operationId, "");
        tenantId = normalize(tenantId, DEFAULT_TENANT_ID);
        source = normalize(source, "");
        userId = normalize(userId, "");
        conversationId = normalize(conversationId, "");
        messageId = normalize(messageId, "");
        sanitizedContent = Objects.requireNonNullElse(sanitizedContent, "");
        baselineAction = Objects.requireNonNullElse(baselineAction, MemoryIngestionAction.IGNORE);
        baselineMemoryType = normalize(baselineMemoryType, "");
        baselineReason = normalize(baselineReason, "");
        baselineDetails = Map.copyOf(Objects.requireNonNullElse(baselineDetails, Map.of()));
        existingMemories = List.copyOf(Objects.requireNonNullElse(existingMemories, List.of()));
        referenceZone = Objects.requireNonNullElse(referenceZone, "");
        targetZone = Objects.requireNonNullElse(targetZone, "");
        stickyAnchors = List.copyOf(Objects.requireNonNullElse(stickyAnchors, List.of()));
    }

    public MemoryRefinementRequest(String operationId,
                                   String tenantId,
                                   String source,
                                   String userId,
                                   String conversationId,
                                   String messageId,
                                   String sanitizedContent,
                                   MemoryIngestionAction baselineAction,
                                   String baselineMemoryType,
                                   String baselineReason,
                                   Map<String, Object> baselineDetails,
                                   List<MemoryRefinementMemory> existingMemories) {
        this(operationId,
                tenantId,
                source,
                userId,
                conversationId,
                messageId,
                sanitizedContent,
                baselineAction,
                baselineMemoryType,
                baselineReason,
                baselineDetails,
                existingMemories,
                "",
                "",
                List.of());
    }

    public MemoryRefinementRequest(String operationId,
                                   String tenantId,
                                   String source,
                                   String userId,
                                   String conversationId,
                                   String messageId,
                                   String sanitizedContent,
                                   MemoryIngestionAction baselineAction,
                                   String baselineMemoryType,
                                   String baselineReason,
                                   Map<String, Object> baselineDetails) {
        this(operationId,
                tenantId,
                source,
                userId,
                conversationId,
                messageId,
                sanitizedContent,
                baselineAction,
                baselineMemoryType,
                baselineReason,
                baselineDetails,
                List.of(),
                "",
                "",
                List.of());
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }
}

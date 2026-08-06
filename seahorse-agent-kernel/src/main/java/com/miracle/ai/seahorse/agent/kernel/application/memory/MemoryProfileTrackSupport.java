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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 用户画像写入协作者（从 {@link DefaultMemoryEnginePort} 提取）。
 * 按 §7 收敛原则外提：只负责职业纠正、画像事实、捕获元数据与语义键的推导。
 */
final class MemoryProfileTrackSupport {

    private final MemoryTrackWriteService trackWriteService;

    MemoryProfileTrackSupport(MemoryTrackWriteService trackWriteService) {
        this.trackWriteService = Objects.requireNonNull(trackWriteService, "trackWriteService must not be null");
    }

    List<String> captureCorrection(MemoryWriteRequest request, String tenantId, OccupationCorrection correction) {
        MemoryTrackWriteResult result = trackWriteService.writeOccupationCorrection(
                request.userId(),
                tenantId,
                request.messageId(),
                correction.incorrectValue(),
                correction.correctValue());
        return result.operations();
    }

    Map<String, String> profileGenerationIds(
            List<MemoryProfileValueNormalizer.ProfileSlotValue> profileValues) {
        Map<String, String> generationIds = new LinkedHashMap<>();
        for (MemoryProfileValueNormalizer.ProfileSlotValue profileValue : profileValues) {
            generationIds.put(profileValue.slotKey(), profileValue.slotKey() + ":" + SnowflakeIds.nextIdString());
        }
        return generationIds;
    }

    boolean captureProfileFacts(MemoryWriteRequest request,
                                String tenantId,
                                MemoryCaptureDecision decision,
                                List<MemoryProfileValueNormalizer.ProfileSlotValue> profileValues,
                                Map<String, String> profileGenerationIds) {
        if (profileValues.isEmpty()) {
            return false;
        }
        boolean captured = false;
        for (MemoryProfileValueNormalizer.ProfileSlotValue profileValue : profileValues) {
            captured |= trackWriteService.writeProfileFact(
                    request.userId(),
                    tenantId,
                    profileValue.slotKey(),
                    profileValue.valueText(),
                    decision.confidenceLevel(),
                    request.messageId(),
                    profileGenerationIds.getOrDefault(profileValue.slotKey(), ""));
        }
        return captured;
    }

    Map<String, Object> captureMetadata(String operationId,
                                        String tenantId,
                                        MemoryWriteRequest request,
                                        ChatMessage message,
                                        MemoryCaptureDecision decision) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", request.userId());
        metadata.put("tenantId", tenantId);
        metadata.put("operationId", operationId);
        metadata.put("conversationId", Objects.requireNonNullElse(request.conversationId(), ""));
        metadata.put("messageId", Objects.requireNonNullElse(request.messageId(), ""));
        metadata.put("role", message.getRole().name().toLowerCase());
        metadata.put("source", "chat_memory_capture");
        metadata.put("capturePolicy", "explicit_user_memory");
        metadata.put("capturePolicyVersion", decision.policyVersion());
        metadata.put("importanceScore", decision.importanceScore());
        metadata.put("confidenceLevel", decision.confidenceLevel());
        metadata.put("valueScore", decision.valueScore());
        metadata.put("riskScore", decision.riskScore());
        metadata.put("captureSignals", decision.signals());
        metadata.put("captureReasons", decision.reasons());
        metadata.put("semanticKey", deriveSemanticKey(decision));
        return metadata;
    }

    /**
     * Derive a stable semantic key from the capture decision for conflict detection.
     * Memories sharing the same type + semanticKey pair are compared for content conflicts.
     */
    private String deriveSemanticKey(MemoryCaptureDecision decision) {
        String type = Objects.requireNonNullElse(decision.type(), "").toUpperCase(java.util.Locale.ROOT);
        String content = Objects.requireNonNullElse(decision.content(), "").trim().toLowerCase(java.util.Locale.ROOT);
        // Use content hash for stable grouping when content is available
        if (!content.isEmpty()) {
            int hash = content.hashCode();
            return type.toLowerCase(java.util.Locale.ROOT) + ":" + Integer.toHexString(hash & 0xFFFF);
        }
        return type.toLowerCase(java.util.Locale.ROOT) + ":unknown";
    }
}

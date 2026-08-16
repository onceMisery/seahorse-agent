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
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 已接受分类的落地协作者（从 {@link DefaultMemoryEnginePort} 提取）。
 * 按 §7 收敛原则外提：只负责把通过的分类落库（profile 归一化、refiner 元数据、别名解析、存储与索引委托）。
 */
final class MemoryCaptureExecutionSupport {

    private static final String TARGET_KIND_PROFILE_SLOT = "PROFILE_SLOT";
    private static final String TARGET_KEY_IDENTITY_OCCUPATION = "identity.occupation";

    private final MemoryProfileValueNormalizer profileValueNormalizer;
    private final MemoryRefinerMetadataWriter refinerMetadataWriter;
    private final MemoryCanonicalAliasResolver canonicalAliasResolver;
    private final MemoryProfileTrackSupport profileTrackSupport;
    private final MemoryIndexStoreSupport indexStoreSupport;
    private final MemoryReviewStagingSupport reviewStagingSupport;

    MemoryCaptureExecutionSupport(MemoryProfileValueNormalizer profileValueNormalizer,
                                  MemoryRefinerMetadataWriter refinerMetadataWriter,
                                  MemoryCanonicalAliasResolver canonicalAliasResolver,
                                  MemoryProfileTrackSupport profileTrackSupport,
                                  MemoryIndexStoreSupport indexStoreSupport,
                                  MemoryReviewStagingSupport reviewStagingSupport) {
        this.profileValueNormalizer = Objects.requireNonNull(profileValueNormalizer,
                "profileValueNormalizer must not be null");
        this.refinerMetadataWriter = Objects.requireNonNull(refinerMetadataWriter,
                "refinerMetadataWriter must not be null");
        this.canonicalAliasResolver = Objects.requireNonNull(canonicalAliasResolver,
                "canonicalAliasResolver must not be null");
        this.profileTrackSupport = Objects.requireNonNull(profileTrackSupport, "profileTrackSupport must not be null");
        this.indexStoreSupport = Objects.requireNonNull(indexStoreSupport, "indexStoreSupport must not be null");
        this.reviewStagingSupport = Objects.requireNonNull(reviewStagingSupport,
                "reviewStagingSupport must not be null");
    }

    IngestionExecution executeAcceptedClassification(String operationId,
                                                     String tenantId,
                                                     MemoryWriteRequest request,
                                                     ChatMessage message,
                                                     MemoryClassificationResult classification) {
        MemoryCaptureDecision decision = classification.decision();
        List<MemoryProfileValueNormalizer.ProfileSlotValue> profileValues =
                profileValueNormalizer.resolveValues(decision, classification);
        Map<String, String> profileGenerationIds = profileTrackSupport.profileGenerationIds(profileValues);
        Map<String, Object> metadata = profileTrackSupport.captureMetadata(
                operationId, tenantId, request, message, decision);
        refinerMetadataWriter.appendRefined(metadata, classification);
        if (!profileValues.isEmpty()) {
            MemoryProfileValueNormalizer.ProfileSlotValue primaryProfile = profileValues.get(0);
            metadata.put("profileSlot", primaryProfile.slotKey());
            metadata.put("generationId", profileGenerationIds.get(primaryProfile.slotKey()));
            if (profileValues.size() > 1) {
                metadata.put("profileSlots", profileValues.stream()
                        .map(MemoryProfileValueNormalizer.ProfileSlotValue::slotKey)
                        .toList());
                metadata.put("profileGenerationIds", profileGenerationIds);
            }
        }
        canonicalAliasResolver.attachIfResolved(metadata, request.userId(), tenantId, decision.content());
        MemoryLayer targetLayer = indexStoreSupport.targetLayer(classification);
        MemoryRecord record = new MemoryRecord(
                refinerMetadataWriter.buildMemoryId(request, classification),
                targetLayer.name(),
                decision.type(),
                decision.content(),
                metadata,
                java.time.Instant.now());
        List<String> operations = new ArrayList<>();
        operations.add(indexStoreSupport.saveMemory(record, targetLayer));
        if (profileTrackSupport.captureProfileFacts(request, tenantId, decision, profileValues, profileGenerationIds)) {
            operations.add("PROFILE_UPSERT");
        }
        operations.addAll(indexStoreSupport.indexMemoryOrEnqueueOutbox(record, request.userId(), tenantId));
        return new IngestionExecution(MemoryIngestionResult.accepted(
                reviewStagingSupport.resultAction(classification), operations, Map.of(
                "memoryType", decision.type(),
                "valueScore", decision.valueScore(),
                "riskScore", decision.riskScore(),
                "captureReasons", decision.reasons(),
                "captureSignals", decision.signals())), classification);
    }
}

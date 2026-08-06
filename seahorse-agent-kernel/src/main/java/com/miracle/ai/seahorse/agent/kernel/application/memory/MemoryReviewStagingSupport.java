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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLayer;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewApplyDirective;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidatePort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 记忆评审暂存协作者（从 {@link DefaultMemoryEnginePort} 提取）。
 * 按 §7 收敛原则外提：只负责评审候选暂存、评审删除应用、结果动作推导与元数据辅助解析。
 */
final class MemoryReviewStagingSupport {

    private static final String REVIEW_CANDIDATE_PREFIX = "review-";
    private static final String REVIEW_DEFAULT_LAYER = "SHORT_TERM";
    private static final String METADATA_CONTENT = "content";
    private static final String METADATA_CONFIDENCE = "confidence";
    private static final String METADATA_IMPORTANCE = "importance";
    private static final String METADATA_VALUE_SCORE = "valueScore";
    private static final String METADATA_RISK_SCORE = "riskScore";
    private static final String METADATA_SOURCE_MESSAGE_IDS = "sourceMessageIds";
    private static final String METADATA_TARGET_LAYER = "targetLayer";
    private static final String METADATA_REVIEW_REQUESTED_ACTION = "reviewRequestedAction";
    private static final String METADATA_TARGET_MEMORY_ID = "targetMemoryId";

    private final MemoryReviewCandidatePort memoryReviewCandidatePort;
    private final MemoryPolicyConfigPort memoryPolicyConfigPort;
    private final MemoryLayerStoreRegistry stores;
    private final MemoryIndexStoreSupport indexStoreSupport;

    MemoryReviewStagingSupport(MemoryReviewCandidatePort memoryReviewCandidatePort,
                               MemoryPolicyConfigPort memoryPolicyConfigPort,
                               MemoryLayerStoreRegistry stores,
                               MemoryIndexStoreSupport indexStoreSupport) {
        this.memoryReviewCandidatePort = Objects.requireNonNull(memoryReviewCandidatePort,
                "memoryReviewCandidatePort must not be null");
        this.memoryPolicyConfigPort = Objects.requireNonNull(memoryPolicyConfigPort,
                "memoryPolicyConfigPort must not be null");
        this.stores = Objects.requireNonNull(stores, "stores must not be null");
        this.indexStoreSupport = Objects.requireNonNull(indexStoreSupport, "indexStoreSupport must not be null");
    }

    boolean isReviewDeleteApply(MemoryIngestionCommand command) {
        MemoryReviewApplyDirective directive = command == null ? null : command.reviewApplyDirective();
        return directive != null && directive.requestedAction() == MemoryIngestionAction.DELETE;
    }

    IngestionExecution executeReviewDeleteApply(String tenantId,
                                                MemoryWriteRequest request,
                                                MemoryReviewApplyDirective directive) {
        String targetMemoryId = targetMemoryId(directive);
        MemoryLayer layer = indexStoreSupport.targetLayer(directive.targetLayer());
        if (isBlank(targetMemoryId)) {
            return new IngestionExecution(MemoryIngestionResult.rejected(
                    "review_delete_target_key_required",
                    Map.of(
                            METADATA_REVIEW_REQUESTED_ACTION, MemoryIngestionAction.DELETE.name(),
                            METADATA_TARGET_LAYER, layer.name())),
                    null);
        }
        boolean deleted = stores.storeFor(layer).deleteById(targetMemoryId);
        if (!deleted) {
            return new IngestionExecution(MemoryIngestionResult.rejected(
                    "review_delete_target_not_found",
                    Map.of(
                            METADATA_REVIEW_REQUESTED_ACTION, MemoryIngestionAction.DELETE.name(),
                            METADATA_TARGET_LAYER, layer.name(),
                            METADATA_TARGET_MEMORY_ID, targetMemoryId)),
                    null);
        }
        List<String> operations = new ArrayList<>();
        operations.add(layer.name() + "_DELETE");
        operations.addAll(indexStoreSupport.deleteIndexesOrEnqueueOutbox(targetMemoryId, request.userId(), tenantId));
        return new IngestionExecution(MemoryIngestionResult.accepted(
                MemoryIngestionAction.DELETE,
                operations,
                Map.of(
                        METADATA_REVIEW_REQUESTED_ACTION, MemoryIngestionAction.DELETE.name(),
                        METADATA_TARGET_LAYER, layer.name(),
                        METADATA_TARGET_MEMORY_ID, targetMemoryId)),
                null);
    }

    IngestionExecution executeReviewStaging(String operationId,
                                            String tenantId,
                                            MemoryWriteRequest request,
                                            MemoryClassificationResult classification) {
        MemoryPolicyConfig policy = memoryPolicyConfigPort.current();
        if (!policy.reviewEnabled()) {
            return new IngestionExecution(MemoryIngestionResult.ignored("review_disabled"), classification);
        }
        RefinedMemoryDelta delta = classification.refinedDelta();
        Map<String, Object> metadata = delta == null ? Map.of() : delta.metadata();
        MemoryReviewCandidate candidate = new MemoryReviewCandidate(
                REVIEW_CANDIDATE_PREFIX + operationId,
                operationId,
                tenantId,
                request.userId(),
                request.conversationId(),
                request.messageId(),
                delta == null ? MemoryIngestionAction.REVIEW : delta.action(),
                stringMetadata(metadata, METADATA_TARGET_LAYER, REVIEW_DEFAULT_LAYER),
                delta == null ? "" : delta.targetKind(),
                delta == null ? "" : delta.targetKey(),
                stringMetadata(metadata, METADATA_CONTENT, request.message().getContent()),
                doubleMetadata(metadata, METADATA_CONFIDENCE),
                doubleMetadata(metadata, METADATA_IMPORTANCE),
                doubleMetadata(metadata, METADATA_VALUE_SCORE),
                doubleMetadata(metadata, METADATA_RISK_SCORE),
                classification.reason(),
                sourceMessageIds(metadata, request.messageId()),
                metadata,
                Instant.now());
        memoryReviewCandidatePort.save(candidate);
        return new IngestionExecution(MemoryIngestionResult.review(classification.reason()), classification);
    }

    MemoryIngestionAction resultAction(MemoryClassificationResult classification) {
        RefinedMemoryDelta delta = classification == null ? null : classification.refinedDelta();
        if (delta != null && delta.metadata().containsKey(METADATA_REVIEW_REQUESTED_ACTION)
                && delta.action() == MemoryIngestionAction.UPDATE) {
            return MemoryIngestionAction.UPDATE;
        }
        return MemoryIngestionAction.ADD;
    }

    private String targetMemoryId(MemoryReviewApplyDirective directive) {
        if (directive == null) {
            return "";
        }
        Object metadataTarget = directive.metadata().get(METADATA_TARGET_MEMORY_ID);
        if (metadataTarget != null && !metadataTarget.toString().isBlank()) {
            return metadataTarget.toString().trim();
        }
        return directive.targetKey();
    }

    private String stringMetadata(Map<String, Object> metadata, String key, String fallback) {
        Object value = metadata.get(key);
        if (value == null || value.toString().isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return value.toString().trim();
    }

    private double doubleMetadata(Map<String, Object> metadata, String key) {
        Object value = metadata.get(key);
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return 0D;
            }
        }
        return 0D;
    }

    private List<String> sourceMessageIds(Map<String, Object> metadata, String fallbackMessageId) {
        Object value = metadata.get(METADATA_SOURCE_MESSAGE_IDS);
        if (value instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        if (!isBlank(fallbackMessageId)) {
            return List.of(fallbackMessageId);
        }
        return List.of();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

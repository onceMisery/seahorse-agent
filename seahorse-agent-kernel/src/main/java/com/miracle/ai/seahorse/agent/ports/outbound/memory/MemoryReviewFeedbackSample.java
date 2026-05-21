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
import java.util.Map;
import java.util.Objects;

public record MemoryReviewFeedbackSample(
        String sampleId,
        String candidateId,
        String operationId,
        String tenantId,
        String userId,
        String requestedAction,
        MemoryReviewStatus reviewStatus,
        String reviewerId,
        String reviewComment,
        String targetLayer,
        String targetKind,
        String targetKey,
        String rejectedContent,
        String chosenContent,
        Map<String, Object> rejectedMetadata,
        Map<String, Object> chosenMetadata,
        List<String> sourceMessageIds,
        String reviewedMemoryId,
        String reviewedLayer,
        Instant createdAt
) {

    public MemoryReviewFeedbackSample {
        sampleId = normalize(sampleId, "");
        candidateId = normalize(candidateId, "");
        operationId = normalize(operationId, "");
        tenantId = normalize(tenantId, "default");
        userId = normalize(userId, "");
        requestedAction = normalize(requestedAction, MemoryIngestionAction.REVIEW.name());
        reviewStatus = Objects.requireNonNullElse(reviewStatus, MemoryReviewStatus.PENDING);
        reviewerId = normalize(reviewerId, "");
        reviewComment = normalize(reviewComment, "");
        targetLayer = normalize(targetLayer, "");
        targetKind = normalize(targetKind, "");
        targetKey = normalize(targetKey, "");
        rejectedContent = Objects.requireNonNullElse(rejectedContent, "").trim();
        chosenContent = Objects.requireNonNullElse(chosenContent, "").trim();
        rejectedMetadata = Map.copyOf(Objects.requireNonNullElse(rejectedMetadata, Map.of()));
        chosenMetadata = Map.copyOf(Objects.requireNonNullElse(chosenMetadata, Map.of()));
        sourceMessageIds = List.copyOf(Objects.requireNonNullElse(sourceMessageIds, List.of()));
        reviewedMemoryId = normalize(reviewedMemoryId, "");
        reviewedLayer = normalize(reviewedLayer, "");
        createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
    }

    public static MemoryReviewFeedbackSample fromDecision(String sampleId,
                                                          MemoryReviewRecord pending,
                                                          MemoryReviewRecord reviewed) {
        Objects.requireNonNull(pending, "pending must not be null");
        Objects.requireNonNull(reviewed, "reviewed must not be null");
        return new MemoryReviewFeedbackSample(
                sampleId,
                pending.candidateId(),
                pending.operationId(),
                pending.tenantId(),
                pending.userId(),
                pending.requestedAction(),
                reviewed.reviewStatus(),
                reviewed.reviewerId(),
                reviewed.reviewComment(),
                pending.targetLayer(),
                pending.targetKind(),
                pending.targetKey(),
                pending.content(),
                reviewed.chosenContent(),
                pending.metadata(),
                reviewed.chosenMetadata(),
                pending.sourceMessageIds(),
                reviewed.reviewedMemoryId(),
                reviewed.reviewedLayer(),
                reviewed.updatedAt());
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }
}

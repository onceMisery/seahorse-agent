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

public record MemoryReviewRecord(
        String candidateId,
        String operationId,
        String tenantId,
        String userId,
        String conversationId,
        String messageId,
        String requestedAction,
        String targetLayer,
        String targetKind,
        String targetKey,
        String content,
        double confidence,
        double importance,
        double valueScore,
        double riskScore,
        String reason,
        List<String> sourceMessageIds,
        Map<String, Object> metadata,
        MemoryReviewStatus reviewStatus,
        String reviewerId,
        String reviewComment,
        String chosenContent,
        Map<String, Object> chosenMetadata,
        String reviewedMemoryId,
        String reviewedLayer,
        Instant createdAt,
        Instant updatedAt
) {

    public MemoryReviewRecord {
        candidateId = normalize(candidateId, "");
        operationId = normalize(operationId, "");
        tenantId = normalize(tenantId, "default");
        userId = normalize(userId, "");
        conversationId = normalize(conversationId, "");
        messageId = normalize(messageId, "");
        requestedAction = normalize(requestedAction, MemoryIngestionAction.REVIEW.name());
        targetLayer = normalize(targetLayer, "SHORT_TERM");
        targetKind = normalize(targetKind, "");
        targetKey = normalize(targetKey, "");
        content = Objects.requireNonNullElse(content, "").trim();
        confidence = ratio(confidence);
        importance = ratio(importance);
        valueScore = ratio(valueScore);
        riskScore = ratio(riskScore);
        reason = normalize(reason, "");
        sourceMessageIds = List.copyOf(Objects.requireNonNullElse(sourceMessageIds, List.of()));
        metadata = Map.copyOf(Objects.requireNonNullElse(metadata, Map.of()));
        reviewStatus = Objects.requireNonNullElse(reviewStatus, MemoryReviewStatus.PENDING);
        reviewerId = normalize(reviewerId, "");
        reviewComment = normalize(reviewComment, "");
        chosenContent = Objects.requireNonNullElse(chosenContent, "").trim();
        chosenMetadata = Map.copyOf(Objects.requireNonNullElse(chosenMetadata, Map.of()));
        reviewedMemoryId = normalize(reviewedMemoryId, "");
        reviewedLayer = normalize(reviewedLayer, "");
        createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
        updatedAt = Objects.requireNonNullElseGet(updatedAt, Instant::now);
    }

    public static MemoryReviewRecord pending(MemoryReviewCandidate candidate) {
        return new MemoryReviewRecord(
                candidate.candidateId(),
                candidate.operationId(),
                candidate.tenantId(),
                candidate.userId(),
                candidate.conversationId(),
                candidate.messageId(),
                candidate.requestedAction().name(),
                candidate.targetLayer(),
                candidate.targetKind(),
                candidate.targetKey(),
                candidate.content(),
                candidate.confidence(),
                candidate.importance(),
                candidate.valueScore(),
                candidate.riskScore(),
                candidate.reason(),
                candidate.sourceMessageIds(),
                candidate.metadata(),
                MemoryReviewStatus.PENDING,
                "",
                "",
                "",
                Map.of(),
                "",
                "",
                candidate.createdAt(),
                candidate.createdAt());
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }

    private static double ratio(double value) {
        if (Double.isNaN(value) || Double.isInfinite(value)) {
            return 0D;
        }
        return Math.max(0D, Math.min(1D, value));
    }
}

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
        /**
         * 会话维度（取值即 conversationId），与用户反馈的 conversationId 同源。
         * 这是两类信号目前唯一可对齐的共有维度——此前反馈与复核之间只有 userId 能对应上。
         */
        String sessionId,
        /**
         * 产生该复核样本的运行标识。复核发生在人工治理链路、捕获点拿不到 runId，
         * 故此列当前留空，仅作留痕，不参与聚合。
         */
        String runId,
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
        sessionId = normalize(sessionId, "");
        runId = normalize(runId, "");
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

    /**
     * 兼容构造：未提供 sessionId / runId 时按空值处理。
     *
     * <p>既有的 18 处构造点（含各模块测试）因此无需逐个改动，保持向后兼容。
     * 需要携带会话维度的调用方请使用完整规范构造，或走 {@link #fromDecision}。
     */
    public MemoryReviewFeedbackSample(String sampleId,
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
                                      Instant createdAt) {
        this(sampleId, candidateId, operationId, tenantId, userId, "", "",
                requestedAction, reviewStatus, reviewerId, reviewComment,
                targetLayer, targetKind, targetKey, rejectedContent, chosenContent,
                rejectedMetadata, chosenMetadata, sourceMessageIds,
                reviewedMemoryId, reviewedLayer, createdAt);
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
                pending.conversationId(),
                "",
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

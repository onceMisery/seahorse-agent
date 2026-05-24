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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * Slice 3 续 cut 11：refiner 上下文里"最近已解决的同 scope review feedback 样本"查询器。
 *
 * <p>原 facade 私有方法 {@code recentReviewFeedbackExamples} / {@code refinerFeedbackScope} /
 * {@code isResolvedFeedbackSample} 与 {@code RefinerFeedbackScope} record 合并到本 service，
 * 对外只暴露 {@link #recentResolved(String, String, MemoryClassificationResult)} 单一入口。
 *
 * <p>Scope 决策优先级（保持 facade 行为不变）：
 * <ol>
 *     <li>baseline.refinedDelta 同时含 non-blank targetKind + targetKey → 直接采用。</li>
 *     <li>baseline.action == UPDATE 且有 correction → ({@code PROFILE_SLOT}, {@code identity.occupation})。</li>
 *     <li>否则尝试 profile slot resolver；命中 → ({@code PROFILE_SLOT}, slot)。</li>
 *     <li>都未命中 → 空 scope（仍然下发查询，仅获取 user 维度样本）。</li>
 * </ol>
 *
 * <p>已解决样本约束：reviewStatus ∈ {APPLIED, REJECTED}。
 */
public final class MemoryRefinerFeedbackLookup {

    private static final Logger LOG = LoggerFactory.getLogger(MemoryRefinerFeedbackLookup.class);

    private final MemoryReviewFeedbackRepositoryPort memoryReviewFeedbackRepositoryPort;
    private final MemoryProfileValueNormalizer profileValueNormalizer;
    private final int feedbackExampleLimit;
    private final String profileSlotKind;
    private final String identityOccupationSlotKey;

    public MemoryRefinerFeedbackLookup(MemoryReviewFeedbackRepositoryPort memoryReviewFeedbackRepositoryPort,
                                       MemoryProfileValueNormalizer profileValueNormalizer,
                                       int feedbackExampleLimit,
                                       String profileSlotKind,
                                       String identityOccupationSlotKey) {
        this.memoryReviewFeedbackRepositoryPort = Objects.requireNonNull(memoryReviewFeedbackRepositoryPort,
                "memoryReviewFeedbackRepositoryPort must not be null");
        this.profileValueNormalizer = Objects.requireNonNull(profileValueNormalizer,
                "profileValueNormalizer must not be null");
        this.feedbackExampleLimit = feedbackExampleLimit;
        this.profileSlotKind = Objects.requireNonNull(profileSlotKind, "profileSlotKind must not be null");
        this.identityOccupationSlotKey = Objects.requireNonNull(identityOccupationSlotKey,
                "identityOccupationSlotKey must not be null");
    }

    /**
     * 取出 baseline scope 下最近已解决的 review feedback 样本（最多 {@code feedbackExampleLimit} 条）。
     * 仓库抛异常时 swallow 并打 debug 日志，返回空列表。
     */
    public List<MemoryReviewFeedbackSample> recentResolved(String tenantId,
                                                           String userId,
                                                           MemoryClassificationResult baseline) {
        if (isBlank(userId)) {
            return List.of();
        }
        Scope scope = scopeFor(baseline);
        try {
            List<MemoryReviewFeedbackSample> samples = memoryReviewFeedbackRepositoryPort.listSamples(
                    new MemoryReviewFeedbackQuery(
                            tenantId,
                            userId,
                            null,
                            scope.targetKind(),
                            scope.targetKey(),
                            feedbackExampleLimit));
            return samples.stream()
                    .filter(MemoryRefinerFeedbackLookup::isResolved)
                    .limit(feedbackExampleLimit)
                    .toList();
        } catch (RuntimeException ex) {
            LOG.debug("load refiner review-feedback examples failed: tenantId={}, userId={}", tenantId, userId, ex);
            return List.of();
        }
    }

    private Scope scopeFor(MemoryClassificationResult baseline) {
        if (baseline == null) {
            return Scope.empty();
        }
        RefinedMemoryDelta delta = baseline.refinedDelta();
        if (delta != null && !isBlank(delta.targetKind()) && !isBlank(delta.targetKey())) {
            return new Scope(delta.targetKind(), delta.targetKey());
        }
        if (baseline.action() == MemoryIngestionAction.UPDATE && baseline.correction() != null) {
            return new Scope(profileSlotKind, identityOccupationSlotKey);
        }
        String profileSlot = baseline.decision() == null
                ? ""
                : profileValueNormalizer.resolveSlot(baseline.decision(), baseline);
        if (!isBlank(profileSlot)) {
            return new Scope(profileSlotKind, profileSlot);
        }
        return Scope.empty();
    }

    private static boolean isResolved(MemoryReviewFeedbackSample sample) {
        return sample != null
                && (sample.reviewStatus() == MemoryReviewStatus.APPLIED
                || sample.reviewStatus() == MemoryReviewStatus.REJECTED);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private record Scope(String targetKind, String targetKey) {

        private Scope {
            targetKind = Objects.requireNonNullElse(targetKind, "").trim();
            targetKey = Objects.requireNonNullElse(targetKey, "").trim();
        }

        private static Scope empty() {
            return new Scope("", "");
        }
    }
}

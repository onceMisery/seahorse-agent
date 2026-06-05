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

package com.miracle.ai.seahorse.agent.kernel.application.agent.marketplace;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentPublishReview;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentRating;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.marketplace.AgentSubscription;
import com.miracle.ai.seahorse.agent.kernel.tenant.TenantContext;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.AgentPublishReviewRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.AgentRatingRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.marketplace.AgentSubscriptionRepositoryPort;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Agent 市场服务，支持发布审核、订阅管理、评分评论。
 */
public class KernelAgentMarketplaceService {

    private final AgentPublishReviewRepositoryPort reviewRepository;
    private final AgentSubscriptionRepositoryPort subscriptionRepository;
    private final AgentRatingRepositoryPort ratingRepository;

    public KernelAgentMarketplaceService(AgentPublishReviewRepositoryPort reviewRepository,
                                         AgentSubscriptionRepositoryPort subscriptionRepository,
                                         AgentRatingRepositoryPort ratingRepository) {
        this.reviewRepository = Objects.requireNonNull(reviewRepository, "reviewRepository must not be null");
        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository, "subscriptionRepository must not be null");
        this.ratingRepository = Objects.requireNonNull(ratingRepository, "ratingRepository must not be null");
    }

    // ==================== 发布审核 ====================

    /**
     * 提交 Agent 发布审核。
     */
    public Long submitForReview(String agentId, String submittedBy) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (submittedBy == null || submittedBy.isBlank()) {
            throw new IllegalArgumentException("submittedBy 不能为空");
        }

        String tenantId = TenantContext.get();

        // 检查是否已有待审核的记录
        Optional<AgentPublishReview> existing = reviewRepository.findByAgentId(agentId);
        if (existing.isPresent() && existing.get().isPending()) {
            throw new IllegalStateException("Agent 已在审核中");
        }

        AgentPublishReview review = new AgentPublishReview(
                null, agentId, tenantId, submittedBy, AgentPublishReview.PENDING,
                null, null, Instant.now(), null
        );
        return reviewRepository.save(review);
    }

    /**
     * 批准发布。
     */
    public void approve(Long reviewId, String reviewer, String comment) {
        if (reviewId == null) {
            throw new IllegalArgumentException("reviewId 不能为空");
        }
        if (reviewer == null || reviewer.isBlank()) {
            throw new IllegalArgumentException("reviewer 不能为空");
        }

        AgentPublishReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审核记录不存在"));

        if (!review.isPending()) {
            throw new IllegalStateException("审核记录已处理");
        }

        AgentPublishReview approved = review.approve(reviewer, comment, Instant.now());
        reviewRepository.save(approved);
    }

    /**
     * 拒绝发布。
     */
    public void reject(Long reviewId, String reviewer, String comment) {
        if (reviewId == null) {
            throw new IllegalArgumentException("reviewId 不能为空");
        }
        if (reviewer == null || reviewer.isBlank()) {
            throw new IllegalArgumentException("reviewer 不能为空");
        }

        AgentPublishReview review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new IllegalArgumentException("审核记录不存在"));

        if (!review.isPending()) {
            throw new IllegalStateException("审核记录已处理");
        }

        AgentPublishReview rejected = review.reject(reviewer, comment, Instant.now());
        reviewRepository.save(rejected);
    }

    /**
     * 查询待审核列表。
     */
    public List<AgentPublishReview> listPendingReviews(int page, int size) {
        return reviewRepository.findByStatus(AgentPublishReview.PENDING, page, size);
    }

    // ==================== 订阅管理 ====================

    /**
     * 订阅 Agent。
     */
    public Long subscribe(String agentId, Long userId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        String tenantId = TenantContext.get();

        // 检查是否已订阅
        Optional<AgentSubscription> existing = subscriptionRepository.findByAgentIdAndUserId(agentId, userId);
        if (existing.isPresent()) {
            AgentSubscription sub = existing.get();
            if (sub.active()) {
                throw new IllegalStateException("已订阅该 Agent");
            }
            // 重新激活
            AgentSubscription activated = sub.activate(Instant.now());
            subscriptionRepository.update(activated);
            return sub.id();
        }

        AgentSubscription subscription = new AgentSubscription(
                null, agentId, userId, tenantId, Instant.now(), true
        );
        return subscriptionRepository.save(subscription);
    }

    /**
     * 取消订阅。
     */
    public void unsubscribe(String agentId, Long userId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }

        AgentSubscription subscription = subscriptionRepository.findByAgentIdAndUserId(agentId, userId)
                .orElseThrow(() -> new IllegalArgumentException("未订阅该 Agent"));

        if (!subscription.active()) {
            return; // 已经取消
        }

        AgentSubscription cancelled = subscription.cancel();
        subscriptionRepository.update(cancelled);
    }

    /**
     * 查询我的订阅列表。
     */
    public List<AgentSubscription> mySubscriptions(Long userId, boolean activeOnly) {
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        return subscriptionRepository.findByUserId(userId, activeOnly);
    }

    // ==================== 评分评论 ====================

    /**
     * 评分。
     */
    public Long rate(String agentId, Long userId, int rating, String comment) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        if (userId == null) {
            throw new IllegalArgumentException("userId 不能为空");
        }
        if (rating < AgentRating.MIN_RATING || rating > AgentRating.MAX_RATING) {
            throw new IllegalArgumentException("评分必须在 1-5 之间");
        }

        // 检查是否已评分
        Optional<AgentRating> existing = ratingRepository.findByAgentIdAndUserId(agentId, userId);
        if (existing.isPresent()) {
            // 更新评分
            AgentRating updated = existing.get().update(rating, comment, Instant.now());
            ratingRepository.update(updated);
            return existing.get().id();
        }

        AgentRating newRating = new AgentRating(null, agentId, userId, rating, comment, Instant.now());
        return ratingRepository.save(newRating);
    }

    /**
     * 查询平均评分。
     */
    public double getAverageRating(String agentId) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        return ratingRepository.getAverageRating(agentId);
    }

    /**
     * 查询评分列表。
     */
    public List<AgentRating> listRatings(String agentId, int page, int size) {
        if (agentId == null || agentId.isBlank()) {
            throw new IllegalArgumentException("agentId 不能为空");
        }
        return ratingRepository.findByAgentId(agentId, page, size);
    }

    /**
     * 计算 Agent 热度分数。
     * 公式：subscriptions*0.4 + rating*0.3 + activity*0.2 + newBonus*0.1
     */
    public double calculatePopularityScore(String agentId) {
        long subscriptions = subscriptionRepository.countByAgentId(agentId);
        double avgRating = ratingRepository.getAverageRating(agentId);
        long ratingCount = ratingRepository.countByAgentId(agentId);

        // 简化版：假设 activity 为评分数量，newBonus 为固定值
        double subscriptionScore = Math.min(subscriptions * 0.4, 40);
        double ratingScore = avgRating * 0.3 * 10; // 0-5 分映射到 0-15
        double activityScore = Math.min(ratingCount * 0.2, 20);
        double newBonus = 10; // 新 Agent 加成

        return subscriptionScore + ratingScore + activityScore + newBonus;
    }
}

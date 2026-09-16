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

package com.miracle.ai.seahorse.agent.kernel.application.learning;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningSignal;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.FeedbackEvaluationCandidateQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidatePage;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidateQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Objects;

/**
 * 把闭环从"能算"变成"能跑"：{@link KernelLearningLoopService} 是纯计算，样本要外部喂进来；
 * 本类负责按上限取最近的点踩与人工复核样本喂给它。
 *
 * <p><b>取样口径的不对称要如实说明</b>：复核查询 {@link MemoryReviewFeedbackQuery} 有
 * tenantId，可以按租户过滤；反馈查询 {@link FeedbackEvaluationCandidateQuery} 只有
 * userId / runId / reason，<b>没有租户维度</b>。因此生成工单时反馈侧取的是全站最近样本，
 * 只有复核侧做了租户过滤。这不是疏漏，而是现有端口能支撑的粒度——
 * 要真正按租户切分，得先给反馈查询端口补 tenantId，那是一次端口变更。
 *
 * <p>两个端口任一缺失就认为"没配好"，此时 {@link #analyze} 返回空报告而不是抛异常——
 * 闭环是增强能力，不该因为没接数据源就让工单页面打不开。
 */
public final class KernelLearningLoopRunner {

    private static final Logger LOG = LoggerFactory.getLogger(KernelLearningLoopRunner.class);
    private static final int MAX_SAMPLE_LIMIT = 1000;
    private static final int DEFAULT_SAMPLE_LIMIT = 200;

    private final KernelLearningLoopService loopService;
    private final FeedbackEvaluationCandidateQueryPort feedbackQuery;
    private final MemoryReviewFeedbackRepositoryPort reviewRepository;

    public KernelLearningLoopRunner(LearningLoopPolicy policy,
                                    FeedbackEvaluationCandidateQueryPort feedbackQuery,
                                    MemoryReviewFeedbackRepositoryPort reviewRepository) {
        this.loopService = new KernelLearningLoopService(policy);
        this.feedbackQuery = feedbackQuery;
        this.reviewRepository = reviewRepository;
    }

    public boolean configured() {
        return feedbackQuery != null && reviewRepository != null;
    }

    /**
     * 取最近样本跑一次闭环。
     *
     * @param tenantId 租户；复核侧按此过滤，反馈侧暂无该维度
     * @param limit    单侧取样上限，上限 {@value #MAX_SAMPLE_LIMIT}
     */
    public LearningLoopReport analyze(String tenantId, int limit) {
        if (!configured()) {
            LOG.info("Learning loop skipped: feedback or review source not configured");
            return new LearningLoopReport(null, List.of(), List.of(), null,
                    "闭环未配置数据源：反馈或复核查询端口缺失");
        }
        int safeLimit = Math.min(Math.max(limit, 1), MAX_SAMPLE_LIMIT);
        List<FeedbackEvaluationCandidate> feedback = fetchFeedback(safeLimit);
        List<MemoryReviewFeedbackSample> reviews = fetchReviews(tenantId, safeLimit);
        LOG.info("Learning loop sampling: feedback={} reviews={} tenantId={}",
                feedback.size(), reviews.size(), tenantId);
        LearningLoopReport report = loopService.analyze(feedback, reviews, null, null, null);
        int signalCount = report.signals().size();
        if (signalCount > 0) {
            LOG.info("Learning loop produced {} signal(s), {} proposal(s)",
                    signalCount, report.proposals().size());
        }
        return report;
    }

    /** 反馈侧没有租户维度，这里只能按"最近一页"取样。 */
    private List<FeedbackEvaluationCandidate> fetchFeedback(int limit) {
        try {
            FeedbackEvaluationCandidatePage page = feedbackQuery.page(new FeedbackEvaluationCandidateQuery(
                    null, null, null, 1L, limit));
            return page == null || page.records() == null ? List.of() : page.records();
        } catch (RuntimeException ex) {
            LOG.warn("Failed to sample feedback candidates for learning loop", ex);
            return List.of();
        }
    }

    private List<MemoryReviewFeedbackSample> fetchReviews(String tenantId, int limit) {
        try {
            List<MemoryReviewFeedbackSample> samples = reviewRepository.listSamples(
                    new MemoryReviewFeedbackQuery(
                            Objects.requireNonNullElse(tenantId, ""), null, null, null, null, limit));
            return samples == null ? List.of() : samples;
        } catch (RuntimeException ex) {
            LOG.warn("Failed to sample memory review feedback for learning loop", ex);
            return List.of();
        }
    }

    /** 报告里信号量的便捷读取，供调用方判断是否值得提示用户。 */
    public static int signalCount(LearningLoopReport report) {
        return report == null ? 0 : report.signals().size();
    }

    /** 供调用方在报告为空时给出可诊断的说明。 */
    public static boolean hasActionableSignal(LearningLoopReport report, int minSamples) {
        return report != null && report.signals().stream().anyMatch(signal -> signal.actionable(minSamples));
    }
}

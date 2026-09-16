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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentProposal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PreviousProposalOutcome;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 自学习闭环（P3-2）：把已经存在但各自孤立的三个零件接成一条回路。
 *
 * <pre>
 *   用户点踩 (feedback)  ─┐
 *                         ├─► 按目标聚合 ─► 产生调整建议 ─► 人工决定是否落地
 *   人工复核 (memoryReview)┘                      ▲                    │
 *                                                 │                    ▼
 *                                   下一次运行 ────┴──────── 评测通过率回访 (evaluation)
 * </pre>
 *
 * <p>三个零件此前互不相通：反馈只用于展示与导出评测候选，复核只用于记忆治理，
 * 评测只用于发版前的回归。这里做的是把它们之间的连线补上——
 * <b>没有新增任何仓储或端口</b>，聚合与判定全部是纯计算，因此可完整单测。
 *
 * <p>三条刻意的约束：
 * <ol>
 *   <li><b>只产出建议，不自动改配置。</b>阈值一改，记忆写入与复核的流量分布立刻变化，
 *       而反馈信号要几天才追得上；无人值守的自动调整会把系统带进无法归因的状态。</li>
 *   <li><b>样本不足就不给建议。</b>见 {@link LearningLoopPolicy#minSamples()}。</li>
 *   <li><b>每次运行都回访上一次改动。</b>否则闭环只是"不断产生新猜测"。</li>
 * </ol>
 *
 * <p>当前数据模型的诚实说明：反馈记录与复核记录之间只有 userId 是共有字段，
 * 没有可靠的 run↔memory 关联键，因此两者的聚合分别在"反馈原因"与"记忆 targetKey"
 * 维度上进行，再叠加一个全局聚合。这不是理想形态，但它如实反映了现有数据能支撑的粒度。
 */
public class KernelLearningLoopService {

    private static final Logger LOG = LoggerFactory.getLogger(KernelLearningLoopService.class);

    private static final String FEEDBACK_KIND = "feedback-reason";
    private static final String UNATTRIBUTED = "unattributed";
    private static final List<String> NO_EVIDENCE = List.of();

    private final LearningLoopPolicy policy;

    public KernelLearningLoopService() {
        this(LearningLoopPolicy.defaults());
    }

    public KernelLearningLoopService(LearningLoopPolicy policy) {
        this.policy = Objects.requireNonNullElseGet(policy, LearningLoopPolicy::defaults);
    }

    /**
     * 跑一次闭环。
     *
     * @param feedback          用户反馈样本（点踩/点赞）
     * @param reviewSamples     人工复核样本（通过/拒绝）
     * @param baselinePassRate  上一次改动落地前的评测通过率，无可比数据时传 {@code null}
     * @param currentPassRate   当前评测通过率，无可比数据时传 {@code null}
     * @param previouslyApplied 上一次被采纳的建议，没有则传 {@code null}
     */
    public LearningLoopReport analyze(List<FeedbackEvaluationCandidate> feedback,
                                      List<MemoryReviewFeedbackSample> reviewSamples,
                                      Double baselinePassRate,
                                      Double currentPassRate,
                                      PolicyAdjustmentProposal previouslyApplied) {
        List<LearningSignal> signals = new ArrayList<>();
        signals.addAll(aggregateFeedback(feedback));
        signals.addAll(aggregateReviews(reviewSamples));
        LearningSignal global = globalSignal(signals);
        List<LearningSignal> ordered = new ArrayList<>(signals);
        if (global.sampleCount() > 0) {
            ordered.add(global);
        }
        ordered.sort(Comparator.comparingInt(LearningSignal::sampleCount).reversed());

        List<PolicyAdjustmentProposal> proposals = propose(signals, global);
        PreviousProposalOutcome outcome = evaluate(previouslyApplied, baselinePassRate, currentPassRate);
        String summary = summarize(ordered.size(), proposals, outcome);
        LearningLoopReport report = new LearningLoopReport(Instant.now(), ordered, proposals, outcome, summary);
        LOG.info("Learning loop completed: {}", report.toJson());
        return report;
    }

    private List<LearningSignal> aggregateFeedback(List<FeedbackEvaluationCandidate> feedback) {
        if (feedback == null || feedback.isEmpty()) {
            return List.of();
        }
        Map<String, int[]> byReason = new LinkedHashMap<>();
        Map<String, List<String>> evidence = new HashMap<>();
        for (FeedbackEvaluationCandidate candidate : feedback) {
            if (candidate == null) {
                continue;
            }
            // 没有填写原因的反馈也计入：它们仍然是不认可信号，只是缺少归因维度
            String reason = normalize(candidate.reason());
            if (reason.isEmpty()) {
                reason = UNATTRIBUTED;
            }
            int[] counts = byReason.computeIfAbsent(reason, key -> new int[2]);
            if (candidate.vote() > 0) {
                counts[0]++;
            } else if (candidate.vote() < 0) {
                counts[1]++;
            }
            evidence.computeIfAbsent(reason, key -> new ArrayList<>())
                    .add(String.valueOf(candidate.feedbackId()));
        }
        List<LearningSignal> signals = new ArrayList<>(byReason.size());
        byReason.forEach((reason, counts) -> signals.add(new LearningSignal(
                reason, FEEDBACK_KIND, counts[0], counts[1], 0, 0,
                cap(evidence.get(reason)))));
        return signals;
    }

    private List<LearningSignal> aggregateReviews(List<MemoryReviewFeedbackSample> samples) {
        if (samples == null || samples.isEmpty()) {
            return List.of();
        }
        Map<String, int[]> byTarget = new LinkedHashMap<>();
        Map<String, List<String>> evidence = new HashMap<>();
        Map<String, String> kinds = new HashMap<>();
        for (MemoryReviewFeedbackSample sample : samples) {
            if (sample == null) {
                continue;
            }
            String key = normalize(sample.targetKey());
            if (key.isEmpty()) {
                continue;
            }
            if (sample.reviewStatus() != MemoryReviewStatus.APPLIED
                    && sample.reviewStatus() != MemoryReviewStatus.REJECTED) {
                // PENDING / APPLYING 还没有结论，纳入统计只会稀释信号
                continue;
            }
            int[] counts = byTarget.computeIfAbsent(key, ignored -> new int[2]);
            if (sample.reviewStatus() == MemoryReviewStatus.REJECTED) {
                counts[1]++;
            } else {
                counts[0]++;
            }
            kinds.putIfAbsent(key, normalize(sample.targetKind()));
            evidence.computeIfAbsent(key, ignored -> new ArrayList<>()).add(String.valueOf(sample.sampleId()));
        }
        List<LearningSignal> signals = new ArrayList<>(byTarget.size());
        byTarget.forEach((key, counts) -> signals.add(new LearningSignal(
                key, kinds.getOrDefault(key, ""), 0, 0, counts[0], counts[1],
                cap(evidence.get(key)))));
        return signals;
    }

    private static LearningSignal globalSignal(List<LearningSignal> signals) {
        int positiveVotes = 0;
        int negativeVotes = 0;
        int approved = 0;
        int rejected = 0;
        for (LearningSignal signal : signals) {
            positiveVotes += signal.positiveVotes();
            negativeVotes += signal.negativeVotes();
            approved += signal.approvedReviews();
            rejected += signal.rejectedReviews();
        }
        return new LearningSignal(
                LearningSignal.GLOBAL_TARGET, "aggregate",
                positiveVotes, negativeVotes, approved, rejected, NO_EVIDENCE);
    }

    private List<PolicyAdjustmentProposal> propose(List<LearningSignal> signals, LearningSignal global) {
        List<PolicyAdjustmentProposal> proposals = new ArrayList<>();
        for (LearningSignal signal : signals) {
            if (!signal.actionable(policy.minSamples())
                    || LearningSignal.GLOBAL_TARGET.equals(signal.targetKey())
                    // 反馈信号没有"记忆目标"可言，不能据此隔离某条记忆
                    || FEEDBACK_KIND.equals(signal.targetKind())) {
                continue;
            }
            if (signal.disapprovalRate() >= policy.disapprovalRateThreshold()) {
                proposals.add(PolicyAdjustmentProposal.quarantine(signal, policy.disapprovalRateThreshold()));
            }
        }
        if (global.actionable(policy.minSamples())) {
            double rate = global.disapprovalRate();
            if (rate >= policy.disapprovalRateThreshold()) {
                proposals.add(PolicyAdjustmentProposal.tighten(global,
                        PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, policy.confidenceStep()));
            } else if (rate <= policy.loosenRateThreshold()) {
                proposals.add(PolicyAdjustmentProposal.loosen(global,
                        PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, policy.confidenceStep()));
            }
        }
        proposals.sort(Comparator
                .comparingDouble(PolicyAdjustmentProposal::disapprovalRate).reversed()
                .thenComparing(Comparator.comparingInt(PolicyAdjustmentProposal::sampleCount).reversed()));
        if (proposals.size() > policy.maxProposals()) {
            return List.copyOf(proposals.subList(0, policy.maxProposals()));
        }
        return List.copyOf(proposals);
    }

    private PreviousProposalOutcome evaluate(PolicyAdjustmentProposal previouslyApplied,
                                             Double baselinePassRate,
                                             Double currentPassRate) {
        if (previouslyApplied == null) {
            return PreviousProposalOutcome.noProposal();
        }
        if (baselinePassRate == null || currentPassRate == null) {
            return PreviousProposalOutcome.awaitingMeasurement();
        }
        return PreviousProposalOutcome.of(
                currentPassRate - baselinePassRate, policy.evalImprovementThreshold());
    }

    private static String summarize(int signalCount,
                                    List<PolicyAdjustmentProposal> proposals,
                                    PreviousProposalOutcome outcome) {
        return String.format(Locale.ROOT,
                "%d 个信号，%d 条建议；上一次改动：%s",
                signalCount, proposals.size(), outcome.detail());
    }

    private static List<String> cap(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return NO_EVIDENCE;
        }
        return List.copyOf(ids.size() > 10 ? ids.subList(0, 10) : ids);
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }
}

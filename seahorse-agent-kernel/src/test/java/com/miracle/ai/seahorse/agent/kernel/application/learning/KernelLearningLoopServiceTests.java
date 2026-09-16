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
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelLearningLoopServiceTests {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Test
    void shouldProduceNothingWithoutAnySignal() {
        LearningLoopReport report = new KernelLearningLoopService().analyze(List.of(), List.of(), null, null, null);

        assertFalse(report.hasProposals());
        assertTrue(report.signals().isEmpty());
        assertEquals(PreviousProposalOutcome.Status.NO_PROPOSAL, report.previousOutcome().status());
    }

    @Test
    void shouldQuarantineAMemoryTargetThatKeepsGettingRejected() {
        List<MemoryReviewFeedbackSample> reviews = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            reviews.add(review("sample-" + i, "user:preference:language",
                    i < 5 ? MemoryReviewStatus.REJECTED : MemoryReviewStatus.APPLIED));
        }

        LearningLoopReport report = new KernelLearningLoopService().analyze(List.of(), reviews, null, null, null);

        assertTrue(report.hasProposals());
        PolicyAdjustmentProposal proposal = report.proposals().get(0);
        assertEquals("user:preference:language", proposal.targetKey());
        assertEquals(PolicyAdjustmentProposal.Direction.QUARANTINE_TARGET, proposal.direction());
        assertEquals(6, proposal.sampleCount());
        assertFalse(proposal.evidenceIds().isEmpty(), "每条建议都要能回溯到原始复核记录");
    }

    @Test
    void shouldNotQuarantineWhenTheSampleIsTooSmall() {
        List<MemoryReviewFeedbackSample> reviews = List.of(
                review("s1", "user:preference:language", MemoryReviewStatus.REJECTED),
                review("s2", "user:preference:language", MemoryReviewStatus.REJECTED));

        LearningLoopReport report = new KernelLearningLoopService().analyze(List.of(), reviews, null, null, null);

        assertFalse(report.hasProposals(), "样本不足时宁可不给建议");
    }

    @Test
    void shouldAggregateFeedbackByReasonAndKeepItOutOfQuarantine() {
        List<FeedbackEvaluationCandidate> feedback = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            feedback.add(feedback("fb-" + i, i < 5 ? -1 : 1, "答案过时"));
        }

        LearningLoopReport report = new KernelLearningLoopService().analyze(feedback, List.of(), null, null, null);

        assertTrue(report.signals().stream().anyMatch(signal -> "答案过时".equals(signal.targetKey())));
        assertFalse(report.proposals().stream().anyMatch(proposal ->
                        proposal.direction() == PolicyAdjustmentProposal.Direction.QUARANTINE_TARGET),
                "反馈只能说明某类答案不受认可，不足以据此隔离某条记忆");
    }

    @Test
    void shouldTightenTheAutoCommitThresholdWhenDisapprovalIsHigh() {
        LearningLoopPolicy policy = LearningLoopPolicy.builder()
                .minSamples(2)
                .disapprovalRateThreshold(0.4D)
                .build();
        List<MemoryReviewFeedbackSample> reviews = List.of(
                review("s1", "k1", MemoryReviewStatus.REJECTED),
                review("s2", "k2", MemoryReviewStatus.REJECTED),
                review("s3", "k3", MemoryReviewStatus.APPLIED));

        LearningLoopReport report = new KernelLearningLoopService(policy)
                .analyze(List.of(), reviews, null, null, null);

        PolicyAdjustmentProposal global = report.proposals().stream()
                .filter(proposal -> LearningSignal.GLOBAL_TARGET.equals(proposal.targetKey()))
                .findFirst()
                .orElseThrow();
        assertEquals(PolicyAdjustmentProposal.Direction.TIGHTEN, global.direction());
        assertEquals(PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, global.policyKey());
        assertTrue(global.suggestedDelta() > 0);
    }

    @Test
    void shouldLoosenWhenReviewersApproveAlmostEverything() {
        LearningLoopPolicy policy = LearningLoopPolicy.builder()
                .minSamples(2)
                .loosenRateThreshold(0.1D)
                .build();
        List<MemoryReviewFeedbackSample> reviews = List.of(
                review("s1", "k1", MemoryReviewStatus.APPLIED),
                review("s2", "k2", MemoryReviewStatus.APPLIED));

        LearningLoopReport report = new KernelLearningLoopService(policy)
                .analyze(List.of(), reviews, null, null, null);

        PolicyAdjustmentProposal global = report.proposals().stream()
                .filter(proposal -> LearningSignal.GLOBAL_TARGET.equals(proposal.targetKey()))
                .findFirst()
                .orElseThrow();
        assertEquals(PolicyAdjustmentProposal.Direction.LOOSEN, global.direction());
        assertTrue(global.suggestedDelta() < 0);
    }

    @Test
    void shouldVerifyThePreviouslyAppliedProposalAsImproved() {
        PolicyAdjustmentProposal previous = PolicyAdjustmentProposal.tighten(
                new LearningSignal("*", "aggregate", 0, 5, 0, 5, List.of()),
                PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, 0.05D);

        LearningLoopReport report = new KernelLearningLoopService()
                .analyze(List.of(), List.of(), 0.60D, 0.70D, previous);

        assertEquals(PreviousProposalOutcome.Status.VERIFIED_IMPROVED, report.previousOutcome().status());
        assertFalse(report.regressed());
    }

    @Test
    void shouldFlagARegressionSoTheChangeCanBeReverted() {
        PolicyAdjustmentProposal previous = PolicyAdjustmentProposal.loosen(
                new LearningSignal("*", "aggregate", 5, 0, 5, 0, List.of()),
                PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, 0.05D);

        LearningLoopReport report = new KernelLearningLoopService()
                .analyze(List.of(), List.of(), 0.70D, 0.60D, previous);

        assertEquals(PreviousProposalOutcome.Status.REGRESSED, report.previousOutcome().status());
        assertTrue(report.regressed());
    }

    @Test
    void shouldTreatSmallEvalDeltasAsInconclusive() {
        PolicyAdjustmentProposal previous = PolicyAdjustmentProposal.tighten(
                new LearningSignal("*", "aggregate", 0, 5, 0, 5, List.of()),
                PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, 0.05D);

        LearningLoopReport report = new KernelLearningLoopService()
                .analyze(List.of(), List.of(), 0.70D, 0.71D, previous);

        assertEquals(PreviousProposalOutcome.Status.INCONCLUSIVE, report.previousOutcome().status());
    }

    @Test
    void shouldWaitForMeasurementWhenEitherPassRateIsMissing() {
        PolicyAdjustmentProposal previous = PolicyAdjustmentProposal.tighten(
                new LearningSignal("*", "aggregate", 0, 5, 0, 5, List.of()),
                PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, 0.05D);

        LearningLoopReport report = new KernelLearningLoopService()
                .analyze(List.of(), List.of(), null, 0.70D, previous);

        assertEquals(PreviousProposalOutcome.Status.AWAITING_MEASUREMENT, report.previousOutcome().status());
    }

    @Test
    void shouldCapTheNumberOfProposals() {
        LearningLoopPolicy policy = LearningLoopPolicy.builder().minSamples(2).maxProposals(2).build();
        List<MemoryReviewFeedbackSample> reviews = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            reviews.add(review("a" + i, "key-" + i, MemoryReviewStatus.REJECTED));
            reviews.add(review("b" + i, "key-" + i, MemoryReviewStatus.REJECTED));
        }

        LearningLoopReport report = new KernelLearningLoopService(policy)
                .analyze(List.of(), reviews, null, null, null);

        assertEquals(2, report.proposals().size());
    }

    @Test
    void shouldIgnorePendingAndApplyingReviews() {
        List<MemoryReviewFeedbackSample> reviews = List.of(
                review("s1", "k", MemoryReviewStatus.PENDING),
                review("s2", "k", MemoryReviewStatus.APPLYING),
                review("s3", "k", MemoryReviewStatus.REJECTED));

        LearningLoopReport report = new KernelLearningLoopService().analyze(List.of(), reviews, null, null, null);

        assertFalse(report.hasProposals(), "没有结论的复核不应污染信号");
    }

    @Test
    void shouldExposeACompactJsonForObservability() {
        LearningLoopReport report = new KernelLearningLoopService().analyze(List.of(), List.of(), null, null, null);

        assertTrue(report.toJson().contains("\"proposalCount\":0"));
        assertTrue(report.toJson().contains("\"previousOutcome\":\"NO_PROPOSAL\""));
    }

    private static MemoryReviewFeedbackSample review(String sampleId, String targetKey, MemoryReviewStatus status) {
        return new MemoryReviewFeedbackSample(
                sampleId,
                "candidate-" + sampleId,
                "operation-" + sampleId,
                "default",
                "user-1",
                "REVIEW",
                status,
                "reviewer-1",
                "",
                "profile",
                "preference",
                targetKey,
                "被拒绝的内容",
                status == MemoryReviewStatus.APPLIED ? "被采用的内容" : "",
                Map.of(),
                Map.of(),
                List.of(),
                "memory-1",
                "profile",
                NOW);
    }

    private static FeedbackEvaluationCandidate feedback(String feedbackId, int vote, String reason) {
        return new FeedbackEvaluationCandidate(
                feedbackId,
                "message-" + feedbackId,
                "conversation-1",
                "user-1",
                "run-1",
                vote,
                reason,
                "",
                "回答内容",
                NOW);
    }
}

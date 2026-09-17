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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningSignal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentProposal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketBatchDecisionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReview;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReviewRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PreviousProposalOutcome;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.ReviewMeasurement;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketPage;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketReviewRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 回访闭环与批量审批。
 *
 * <p>回访最容易写错的地方不是"算得对不对"，而是"会不会重复写"——
 * 所以这里的用例重点覆盖冷却期、批次去重与不可测量三种跳过路径。
 */
class KernelPolicyAdjustmentTicketReviewTests {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final String TENANT = "tenant-1";

    @Test
    void shouldWriteReviewForAppliedTicketAndKeepStatus() {
        Fixture f = new Fixture();
        PolicyAdjustmentTicket applied = f.givenAppliedTicket();

        PolicyAdjustmentTicketReviewRunResult result = f.service().reviewAppliedTickets(
                TENANT, "batch-1", ReviewMeasurement.of(0.60D, 0.75D, "eval"));

        assertEquals(1, result.reviews().size());
        PolicyAdjustmentTicketReview review = result.reviews().get(0);
        assertEquals(applied.ticketId(), review.ticketId());
        assertEquals(PreviousProposalOutcome.Status.VERIFIED_IMPROVED, review.outcome());
        assertFalse(review.rollbackRecommended());
        assertEquals(1, result.scanned());
        assertEquals(0, result.skipped());
        // 回访不改变状态：系统并没有真的还原或确认任何配置
        assertEquals(PolicyAdjustmentTicketStatus.APPLIED,
                f.storage.tickets.get(applied.ticketId()).status());
    }

    @Test
    void shouldFlagRollbackRecommendationOnRegression() {
        Fixture f = new Fixture();
        f.givenAppliedTicket();

        PolicyAdjustmentTicketReviewRunResult result = f.service().reviewAppliedTickets(
                TENANT, "batch-1", ReviewMeasurement.of(0.80D, 0.60D, "eval"));

        assertEquals(1, result.reviews().size());
        assertTrue(result.reviews().get(0).rollbackRecommended());
        assertTrue(result.hasRollbackRecommendation());
        assertEquals(PreviousProposalOutcome.Status.REGRESSED, result.reviews().get(0).outcome());
        // 关键：建议回滚≠已回滚，状态必须停在 APPLIED 等人确认
        assertTrue(f.storage.tickets.values().stream()
                .allMatch(ticket -> ticket.status() == PolicyAdjustmentTicketStatus.APPLIED));
    }

    @Test
    void shouldSkipTicketWithinReviewCooldown() {
        Fixture f = new Fixture();
        f.givenAppliedTicket();

        f.service().reviewAppliedTickets(TENANT, "batch-1", ReviewMeasurement.of(0.60D, 0.75D, "eval"));
        PolicyAdjustmentTicketReviewRunResult second =
                f.service().reviewAppliedTickets(TENANT, "batch-2", ReviewMeasurement.of(0.60D, 0.75D, "eval"));

        assertEquals(0, second.reviews().size(), "冷却期内不应重复回访");
        assertEquals(1, second.scanned());
        assertEquals(1, second.skipped());
        assertEquals(1, f.reviews.saved.size(), "落库记录数不因重复触发而增长");
    }

    @Test
    void shouldReviewAgainAfterCooldownElapsed() {
        Fixture f = new Fixture();
        f.givenAppliedTicket();
        f.service().reviewAppliedTickets(TENANT, "batch-1", ReviewMeasurement.of(0.60D, 0.75D, "eval"));

        f.clock.advance(Duration.ofHours(13));
        PolicyAdjustmentTicketReviewRunResult second =
                f.service().reviewAppliedTickets(TENANT, "batch-2", ReviewMeasurement.of(0.60D, 0.75D, "eval"));

        assertEquals(1, second.reviews().size());
        assertEquals(2, f.reviews.saved.size(), "过了冷却期应当能再次回访，历史记录不覆盖");
    }

    @Test
    void shouldDeduplicateIdenticalBatchId() {
        Fixture f = new Fixture();
        f.givenAppliedTicket();
        ReviewMeasurement measurement = ReviewMeasurement.of(0.60D, 0.75D, "eval");

        f.service().reviewAppliedTickets(TENANT, "batch-1", measurement);
        f.service().reviewAppliedTickets(TENANT, "batch-1", measurement);

        assertEquals(1, f.reviews.saved.size(), "同一批次重复提交只能落一条");
    }

    @Test
    void shouldNotWriteReviewWhenMeasurementUnavailable() {
        Fixture f = new Fixture();
        f.givenAppliedTicket();

        PolicyAdjustmentTicketReviewRunResult result = f.service().reviewAppliedTickets(
                TENANT, "batch-1", ReviewMeasurement.unavailable());

        assertTrue(result.reviews().isEmpty());
        assertTrue(f.reviews.saved.isEmpty(),
                "不可测量就不落库：否则每轮都多一条内容相同的 AWAITING_MEASUREMENT");
        assertTrue(result.note().contains("缺少可比的评测通过率"));
    }

    @Test
    void shouldOnlyReviewAppliedTickets() {
        Fixture f = new Fixture();
        f.givenTicket(PolicyAdjustmentTicketStatus.PENDING);

        PolicyAdjustmentTicketReviewRunResult result = f.service().reviewAppliedTickets(
                TENANT, "batch-1", ReviewMeasurement.of(0.60D, 0.75D, "eval"));

        assertEquals(0, result.scanned());
        assertTrue(result.reviews().isEmpty());
    }

    @Test
    void shouldReviewAppliedTicketsWhenRunningLoop() {
        Fixture f = new Fixture();
        f.givenAppliedTicket();

        f.service().runAndGenerateTickets(TENANT, "loop", 50,
                ReviewMeasurement.of(0.60D, 0.75D, "eval"));

        assertFalse(f.reviews.saved.isEmpty(), "跑闭环时应顺带回访已落地工单（事件驱动的默认路径）");
    }

    @Test
    void shouldApproveBatchAndReportSuccess() {
        Fixture f = new Fixture();
        PolicyAdjustmentTicket first = f.givenTicket(PolicyAdjustmentTicketStatus.PENDING);
        PolicyAdjustmentTicket second = f.givenTicket(PolicyAdjustmentTicketStatus.PENDING);

        PolicyAdjustmentTicketBatchDecisionResult result = f.service().decideBatch(
                List.of(first.ticketId(), second.ticketId()),
                PolicyAdjustmentTicketStatus.APPROVED, "alice", "同意");

        assertEquals(2, result.total());
        assertEquals(2, result.succeededTicketIds().size());
        assertTrue(result.allSucceeded());
        assertTrue(result.summary().contains("通过 2 张工单"));
    }

    @Test
    void shouldReportPartialFailureWithoutRollingBackSuccesses() {
        Fixture f = new Fixture();
        PolicyAdjustmentTicket good = f.givenTicket(PolicyAdjustmentTicketStatus.PENDING);
        PolicyAdjustmentTicket alreadyRejected = f.givenTicket(PolicyAdjustmentTicketStatus.REJECTED);

        PolicyAdjustmentTicketBatchDecisionResult result = f.service().decideBatch(
                List.of(good.ticketId(), alreadyRejected.ticketId()),
                PolicyAdjustmentTicketStatus.APPROVED, "alice", "同意");

        assertEquals(2, result.total());
        assertEquals(List.of(good.ticketId()), result.succeededTicketIds(),
                "成功的那张必须真的成功——整批回滚会让人白做一遍");
        assertEquals(1, result.failed().size());
        assertEquals(alreadyRejected.ticketId(), result.failed().get(0).ticketId());
        assertFalse(result.failed().get(0).reason().isBlank(), "失败原因要是人话，直接展示给运营看");
        assertTrue(result.summary().contains("失败 1 张"));
        assertEquals(PolicyAdjustmentTicketStatus.APPROVED,
                f.storage.tickets.get(good.ticketId()).status());
    }

    @Test
    void shouldRejectBatchForUnsupportedTarget() {
        Fixture f = new Fixture();
        PolicyAdjustmentTicket ticket = f.givenTicket(PolicyAdjustmentTicketStatus.APPROVED);

        assertThrows(IllegalArgumentException.class, () -> f.service().decideBatch(
                List.of(ticket.ticketId()), PolicyAdjustmentTicketStatus.APPLIED, "alice", "已改"));
    }

    @Test
    void shouldRejectEmptyAndOversizedBatches() {
        Fixture f = new Fixture();
        assertThrows(IllegalArgumentException.class,
                () -> f.service().decideBatch(List.of(), PolicyAdjustmentTicketStatus.APPROVED, "alice", "ok"));
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add("pat-" + i);
        }
        assertThrows(IllegalArgumentException.class,
                () -> f.service().decideBatch(tooMany, PolicyAdjustmentTicketStatus.APPROVED, "alice", "ok"));
    }

    /** 测试的固定时间需要能被推进，才能覆盖冷却期。 */
    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        private void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneOffset getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }

    private static final class InMemoryReviews implements PolicyAdjustmentTicketReviewRepositoryPort {
        private final List<PolicyAdjustmentTicketReview> saved = new ArrayList<>();
        private final Set<String> batchKeys = new LinkedHashSet<>();

        @Override
        public void save(PolicyAdjustmentTicketReview review) {
            // 模拟数据库 (ticket_id, batch_id) 唯一约束
            if (!batchKeys.add(review.ticketId() + "|" + review.batchId())) {
                return;
            }
            saved.add(review);
        }

        @Override
        public List<PolicyAdjustmentTicketReview> listByTicket(String ticketId, int limit) {
            return saved.stream()
                    .filter(review -> review.ticketId().equals(ticketId))
                    .sorted((a, b) -> b.reviewedAt().compareTo(a.reviewedAt()))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Map<String, PolicyAdjustmentTicketReview> latestByTickets(List<String> ticketIds) {
            Map<String, PolicyAdjustmentTicketReview> latest = new LinkedHashMap<>();
            for (String ticketId : ticketIds) {
                listByTicket(ticketId, 1).stream().findFirst()
                        .ifPresent(review -> latest.put(ticketId, review));
            }
            return latest;
        }
    }

    private static final class InMemoryTickets
            implements PolicyAdjustmentTicketRepositoryPort, PolicyAdjustmentTicketQueryPort {

        private final Map<String, PolicyAdjustmentTicket> tickets = new LinkedHashMap<>();
        private int sequence;

        @Override
        public void save(PolicyAdjustmentTicket ticket) {
            tickets.put(ticket.ticketId(), ticket);
        }

        @Override
        public boolean updateIfStatus(String ticketId,
                                      PolicyAdjustmentTicketStatus expectedStatus,
                                      PolicyAdjustmentTicket ticket) {
            PolicyAdjustmentTicket current = tickets.get(ticketId);
            if (current == null || current.status() != expectedStatus) {
                return false;
            }
            tickets.put(ticketId, ticket);
            return true;
        }

        @Override
        public Optional<PolicyAdjustmentTicket> findById(String ticketId) {
            return Optional.ofNullable(tickets.get(ticketId));
        }

        @Override
        public PolicyAdjustmentTicketPage page(PolicyAdjustmentTicketQuery query) {
            List<PolicyAdjustmentTicket> matched = tickets.values().stream()
                    .filter(ticket -> query.status() == null || ticket.status() == query.status())
                    .toList();
            return new PolicyAdjustmentTicketPage(matched, matched.size(), query.size(), query.current(), 1L);
        }

        @Override
        public Set<String> existingFingerprints(String tenantId, List<String> fingerprints) {
            return Set.of();
        }
    }

    private static final class Fixture {
        private final MutableClock clock = new MutableClock(NOW);
        private final InMemoryTickets storage = new InMemoryTickets();
        private final InMemoryReviews reviews = new InMemoryReviews();

        private KernelPolicyAdjustmentTicketService service() {
            return new KernelPolicyAdjustmentTicketService(
                    storage, storage, clock, null, reviews, LearningLoopPolicy.defaults());
        }

        private PolicyAdjustmentTicket givenAppliedTicket() {
            return givenTicket(PolicyAdjustmentTicketStatus.APPLIED);
        }

        private PolicyAdjustmentTicket givenTicket(PolicyAdjustmentTicketStatus status) {
            PolicyAdjustmentTicket pending = PolicyAdjustmentTicket.pending(
                    "pat-" + (++storage.sequence), TENANT, proposal("memory:target:" + storage.sequence),
                    "fp-" + storage.sequence, "loop", clock.instant());
            PolicyAdjustmentTicket target = status == PolicyAdjustmentTicketStatus.PENDING
                    ? pending
                    : pending.withStatus(status, "alice", "测试", clock.instant());
            storage.save(target);
            return target;
        }

        private static PolicyAdjustmentProposal proposal(String targetKey) {
            LearningSignal signal = new LearningSignal(targetKey, "memory", 3, 9, 1, 8, List.of("evidence-1"));
            return PolicyAdjustmentProposal.tighten(signal,
                    PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, 0.05D);
        }
    }
}

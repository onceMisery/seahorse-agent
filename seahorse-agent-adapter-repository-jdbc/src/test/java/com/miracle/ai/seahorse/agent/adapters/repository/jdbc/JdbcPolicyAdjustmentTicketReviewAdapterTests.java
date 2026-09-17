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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentProposal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReview;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PreviousProposalOutcome;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.ReviewMeasurement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPolicyAdjustmentTicketReviewAdapterTests {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private JdbcPolicyAdjustmentTicketReviewAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:policy-review-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        new JdbcChatSchemaUpgrade(dataSource).upgrade();
        adapter = new JdbcPolicyAdjustmentTicketReviewAdapter(dataSource);
    }

    @Test
    void shouldRoundTripEveryField() {
        adapter.save(review("review-1", "ticket-1", "batch-1",
                PreviousProposalOutcome.Status.VERIFIED_IMPROVED, 0.60D, 0.75D));

        List<PolicyAdjustmentTicketReview> found = adapter.listByTicket("ticket-1", 10);

        assertThat(found).hasSize(1);
        PolicyAdjustmentTicketReview review = found.get(0);
        assertThat(review.reviewId()).isEqualTo("review-1");
        assertThat(review.ticketId()).isEqualTo("ticket-1");
        assertThat(review.batchId()).isEqualTo("batch-1");
        assertThat(review.outcome()).isEqualTo(PreviousProposalOutcome.Status.VERIFIED_IMPROVED);
        assertThat(review.baselinePassRate()).isEqualTo(0.60D);
        assertThat(review.currentPassRate()).isEqualTo(0.75D);
        assertThat(review.rollbackRecommended()).isFalse();
    }

    @Test
    void shouldMarkRollbackRecommendedWhenRegressed() {
        adapter.save(review("review-1", "ticket-1", "batch-1",
                PreviousProposalOutcome.Status.REGRESSED, 0.80D, 0.60D));

        assertThat(adapter.listByTicket("ticket-1", 10).get(0).rollbackRecommended()).isTrue();
    }

    @Test
    void shouldIgnoreDuplicateBatch() {
        // 批次幂等的最后一道防线在数据库：应用层"先查再写"在并发下拦不住重复，
        // 唯一约束 (ticket_id, batch_id) 必须真的能挡住。
        adapter.save(review("review-1", "ticket-1", "batch-1",
                PreviousProposalOutcome.Status.VERIFIED_IMPROVED, 0.60D, 0.75D));
        adapter.save(review("review-2", "ticket-1", "batch-1",
                PreviousProposalOutcome.Status.REGRESSED, 0.60D, 0.50D));

        List<PolicyAdjustmentTicketReview> found = adapter.listByTicket("ticket-1", 10);
        assertThat(found).hasSize(1);
        assertThat(found.get(0).reviewId()).isEqualTo("review-1");
    }

    @Test
    void shouldKeepHistoryAcrossBatches() {
        adapter.save(review("review-1", "ticket-1", "batch-1",
                PreviousProposalOutcome.Status.INCONCLUSIVE, 0.60D, 0.61D));
        adapter.save(review("review-2", "ticket-1", "batch-2",
                PreviousProposalOutcome.Status.REGRESSED, 0.60D, 0.50D));

        assertThat(adapter.listByTicket("ticket-1", 10)).hasSize(2);
        // 最近一次排在最前，供冷却期判断与列表展示直接使用
        assertThat(adapter.listByTicket("ticket-1", 10).get(0).batchId()).isEqualTo("batch-2");
    }

    @Test
    void shouldReturnLatestReviewPerTicketInOneQuery() {
        adapter.save(review("review-1", "ticket-1", "batch-1",
                PreviousProposalOutcome.Status.INCONCLUSIVE, 0.60D, 0.61D));
        adapter.save(review("review-2", "ticket-1", "batch-2",
                PreviousProposalOutcome.Status.REGRESSED, 0.60D, 0.50D));
        adapter.save(review("review-3", "ticket-2", "batch-1",
                PreviousProposalOutcome.Status.VERIFIED_IMPROVED, 0.60D, 0.80D));

        Map<String, PolicyAdjustmentTicketReview> latest =
                adapter.latestByTickets(List.of("ticket-1", "ticket-2", "ticket-3"));

        assertThat(latest).hasSize(2);
        assertThat(latest.get("ticket-1").reviewId()).isEqualTo("review-2");
        assertThat(latest.get("ticket-2").reviewId()).isEqualTo("review-3");
        assertThat(latest).doesNotContainKey("ticket-3");
    }

    @Test
    void shouldReturnEmptyForUnknownTicket() {
        assertThat(adapter.listByTicket("missing", 10)).isEmpty();
        assertThat(adapter.latestByTickets(List.of())).isEmpty();
        assertThat(adapter.latestByTickets(List.of("missing"))).isEmpty();
    }

    private static PolicyAdjustmentTicketReview review(String reviewId,
                                                       String ticketId,
                                                       String batchId,
                                                       PreviousProposalOutcome.Status status,
                                                       Double baseline,
                                                       Double current) {
        PolicyAdjustmentTicket ticket = PolicyAdjustmentTicket.pending(
                ticketId, "tenant-1", proposal(), "fp-" + ticketId, "loop", NOW);
        PolicyAdjustmentTicket applied = ticket.withStatus(
                PolicyAdjustmentTicketStatus.APPLIED, "alice", "已改", NOW);
        return PolicyAdjustmentTicketReview.of(
                reviewId,
                applied,
                batchId,
                new ReviewMeasurement(baseline, current, "eval"),
                new PreviousProposalOutcome(status, "detail"),
                NOW);
    }

    private static com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentProposal proposal() {
        com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningSignal signal =
                new com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningSignal(
                        "memory:target:a", "memory", 3, 9, 1, 8, List.of("evidence-1"));
        return PolicyAdjustmentProposal.tighten(signal,
                PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, 0.05D);
    }
}

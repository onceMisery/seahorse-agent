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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningSignal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketNotFoundException;
import com.miracle.ai.seahorse.agent.ports.inbound.feedback.FeedbackEvaluationCandidateQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidatePage;
import com.miracle.ai.seahorse.agent.ports.outbound.feedback.FeedbackEvaluationCandidateQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentProposal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketPage;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelPolicyAdjustmentTicketServiceTests {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);
    private static final String TENANT = "tenant-1";

    @Test
    void shouldTurnProposalsIntoTickets() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = service(storage);

        List<PolicyAdjustmentTicket> created = service.submitProposals(
                report(proposal("memory:target:a"), proposal("memory:target:b")), TENANT, "loop");

        assertEquals(2, created.size());
        assertTrue(created.stream().allMatch(ticket -> ticket.status() == PolicyAdjustmentTicketStatus.PENDING));
        assertTrue(created.stream().allMatch(ticket -> ticket.ticketId().startsWith("pat-")),
                "工单要有稳定 id，否则无法追责也无法回访");
        assertNotEquals(created.get(0).ticketId(), created.get(1).ticketId());
        assertEquals(2, storage.saved.size());
    }

    @Test
    void shouldNotOpenASecondTicketForTheSameProposal() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = service(storage);
        LearningLoopReport report = report(proposal("memory:target:a"));

        assertEquals(1, service.submitProposals(report, TENANT, "loop").size(),
                "同一条建议只该开一次工单——闭环每轮都重算，不去重待办列表会被刷满");
        assertTrue(service.submitProposals(report, TENANT, "loop").isEmpty());
        assertEquals(1, storage.saved.size());
    }

    @Test
    void shouldDedupeWithinASingleReport() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = service(storage);

        List<PolicyAdjustmentTicket> created = service.submitProposals(
                report(proposal("memory:target:a"), proposal("memory:target:a")), TENANT, "loop");

        assertEquals(1, created.size(), "同一批报告里的重复建议也该合并");
    }

    @Test
    void shouldProduceNothingWhenTheReportHasNoProposal() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = service(storage);

        assertTrue(service.submitProposals(new LearningLoopReport(null, List.of(), List.of(), null, ""),
                TENANT, "loop").isEmpty());
        assertTrue(service.submitProposals(null, TENANT, "loop").isEmpty());
        assertTrue(storage.saved.isEmpty());
    }

    @Test
    void shouldWalkTheWholeApprovalFlow() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = service(storage);
        PolicyAdjustmentTicket ticket = service.submitProposals(report(proposal("memory:target:a")),
                TENANT, "loop").get(0);

        PolicyAdjustmentTicket approved = service.decide(new PolicyAdjustmentTicketDecision(
                ticket.ticketId(), PolicyAdjustmentTicketStatus.APPROVED, "alice", "看起来合理"));
        assertEquals(PolicyAdjustmentTicketStatus.APPROVED, approved.status());
        assertEquals("alice", approved.decidedBy());
        assertEquals(NOW, approved.decidedAt());

        PolicyAdjustmentTicket applied = service.decide(new PolicyAdjustmentTicketDecision(
                ticket.ticketId(), PolicyAdjustmentTicketStatus.APPLIED, "bob", "已在配置侧改为 0.85"));
        assertEquals(PolicyAdjustmentTicketStatus.APPLIED, applied.status());
        assertEquals("bob", applied.appliedBy());
        assertEquals("alice", applied.decidedBy(), "落地不该抹掉审批记录");

        PolicyAdjustmentTicket rolledBack = service.decide(new PolicyAdjustmentTicketDecision(
                ticket.ticketId(), PolicyAdjustmentTicketStatus.ROLLED_BACK, "bob", "评测通过率下降"));
        assertEquals(PolicyAdjustmentTicketStatus.ROLLED_BACK, rolledBack.status());
        assertTrue(rolledBack.status().terminal());
    }

    @Test
    void shouldRejectIllegalTransitions() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = service(storage);
        PolicyAdjustmentTicket ticket = service.submitProposals(report(proposal("memory:target:a")),
                TENANT, "loop").get(0);

        assertThrows(IllegalArgumentException.class, () -> service.decide(
                        new PolicyAdjustmentTicketDecision(
                                ticket.ticketId(), PolicyAdjustmentTicketStatus.APPLIED, "bob", "跳步")),
                "不能从待审批直接跳到已落地——中间必须有人工采纳这一步");

        service.decide(new PolicyAdjustmentTicketDecision(
                ticket.ticketId(), PolicyAdjustmentTicketStatus.REJECTED, "alice", "样本量太小"));
        assertThrows(IllegalArgumentException.class, () -> service.decide(
                        new PolicyAdjustmentTicketDecision(
                                ticket.ticketId(), PolicyAdjustmentTicketStatus.APPLIED, "bob", "再试试")),
                "已拒绝是终态，不能再被落地");
    }

    @Test
    void shouldRequireACommentWhenRejectingOrApplying() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = service(storage);
        PolicyAdjustmentTicket ticket = service.submitProposals(report(proposal("memory:target:a")),
                TENANT, "loop").get(0);

        assertThrows(IllegalArgumentException.class, () -> service.decide(
                new PolicyAdjustmentTicketDecision(
                        ticket.ticketId(), PolicyAdjustmentTicketStatus.REJECTED, "alice", "  ")),
                "拒绝不留理由，工单就只剩一个结论");
    }

    @Test
    void shouldFailLoudlyWhenAnotherReviewerMovedTheTicketFirst() {
        InMemoryTickets storage = new InMemoryTickets();
        storage.casResult = false;
        KernelPolicyAdjustmentTicketService service = service(storage);
        PolicyAdjustmentTicket ticket = service.submitProposals(report(proposal("memory:target:a")),
                TENANT, "loop").get(0);

        assertThrows(IllegalStateException.class, () -> service.decide(
                        new PolicyAdjustmentTicketDecision(
                                ticket.ticketId(), PolicyAdjustmentTicketStatus.APPROVED, "alice", "同意")),
                "并发审批下必须报错让人重载，不能静默覆盖别人的决策");
    }

    @Test
    void shouldRejectDecisionForUnknownTicket() {
        KernelPolicyAdjustmentTicketService service = service(new InMemoryTickets());

        assertThrows(PolicyAdjustmentTicketNotFoundException.class, () -> service.decide(
                        new PolicyAdjustmentTicketDecision(
                                "pat-missing", PolicyAdjustmentTicketStatus.APPROVED, "alice", "同意")),
                "工单不存在是 404 语义，不能混进参数非法的 400");
    }

    @Test
    void shouldListPendingTicketsForTheOperatorQueue() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = service(storage);
        service.submitProposals(report(proposal("memory:target:a"), proposal("memory:target:b")),
                TENANT, "loop");

        List<PolicyAdjustmentTicket> pending = service.listPending(TENANT);

        assertEquals(2, pending.size(), "运营看板的'待我处理'要能一次拿到全部待审批工单");
    }

    @Test
    void shouldRunTheLoopAndGenerateTicketsInOneCall() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = new KernelPolicyAdjustmentTicketService(
                storage, storage, CLOCK, runner(storage));

        LearningLoopRunResult result = service.runAndGenerateTickets(TENANT, "ops", 100);

        assertTrue(result.createdCount() > 0, "闭环跑出信号就该开工单，否则页面永远是空的");
        assertTrue(result.tickets().stream()
                .allMatch(ticket -> ticket.status() == PolicyAdjustmentTicketStatus.PENDING));
        assertFalse(result.report().signals().isEmpty());
    }

    @Test
    void shouldGenerateNothingTwiceForTheSameSignals() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = new KernelPolicyAdjustmentTicketService(
                storage, storage, CLOCK, runner(storage));

        assertTrue(service.runAndGenerateTickets(TENANT, "ops", 100).createdCount() > 0);
        LearningLoopRunResult second = service.runAndGenerateTickets(TENANT, "ops", 100);

        assertEquals(0, second.createdCount(), "同一批信号第二次跑不该再开工单");
        assertTrue(second.signalWithoutNewTicket(), "有信号但没新工单，前端要能解释这个状态");
    }

    @Test
    void shouldReturnEmptyReportWhenTheLoopHasNoDataSource() {
        InMemoryTickets storage = new InMemoryTickets();
        KernelPolicyAdjustmentTicketService service = new KernelPolicyAdjustmentTicketService(
                storage, storage, CLOCK);

        LearningLoopRunResult result = service.runAndGenerateTickets(TENANT, "ops", 100);

        assertEquals(0, result.createdCount());
        assertTrue(result.report().summary().contains("未配置"),
                "没接数据源时要给出可诊断的说明，而不是静默返回空");
    }

    private static KernelPolicyAdjustmentTicketService service(InMemoryTickets storage) {
        return new KernelPolicyAdjustmentTicketService(storage, storage, CLOCK);
    }

    /**
     * 用固定的反馈与复核样本喂闭环：6 条同目标的拒绝样本刚好越过默认最小样本量。
     */
    private static KernelLearningLoopRunner runner(InMemoryTickets storage) {
        List<FeedbackEvaluationCandidate> feedback = List.of(
                new FeedbackEvaluationCandidate("fb-1", "msg-1", "conv-1", "user-1", "run-1",
                        -1, "答非所问", "", "不好的答案", NOW));
        List<MemoryReviewFeedbackSample> reviews = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            reviews.add(new MemoryReviewFeedbackSample("sample-" + i, "cand-1", "op-1", TENANT, "user-1",
                    "UPSERT", MemoryReviewStatus.REJECTED, "reviewer", "不准确", "PROFILE",
                    "project.fact", "user:preference:language", "旧值", "新值",
                    Map.of(), Map.of(),
                    List.of("msg-1"), null, null, NOW));
        }
        return new KernelLearningLoopRunner(
                LearningLoopPolicy.defaults(),
                query -> new FeedbackEvaluationCandidatePage(feedback, feedback.size(), 10L, 1L, 1L),
                new StubReviewRepository(reviews));
    }

    private static LearningLoopReport report(PolicyAdjustmentProposal... proposals) {
        return new LearningLoopReport(NOW, List.of(), List.of(proposals), null, "test");
    }

    private static PolicyAdjustmentProposal proposal(String targetKey) {
        LearningSignal signal = new LearningSignal(targetKey, "memory", 3, 9, 1, 8, List.of("evidence-1"));
        return PolicyAdjustmentProposal.tighten(signal,
                PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, 0.05D);
    }

    private record StubReviewRepository(List<MemoryReviewFeedbackSample> samples)
            implements MemoryReviewFeedbackRepositoryPort {

        @Override
        public void save(MemoryReviewFeedbackSample sample) {
            // 该测试只读
        }

        @Override
        public List<MemoryReviewFeedbackSample> listByCandidate(String candidateId, int limit) {
            return samples();
        }

        @Override
        public List<MemoryReviewFeedbackSample> listSamples(MemoryReviewFeedbackQuery query) {
            return samples();
        }
    }

    private static final class InMemoryTickets
            implements PolicyAdjustmentTicketRepositoryPort, PolicyAdjustmentTicketQueryPort {

        private final Map<String, PolicyAdjustmentTicket> tickets = new LinkedHashMap<>();
        private final List<PolicyAdjustmentTicket> saved = new ArrayList<>();
        private boolean casResult = true;

        @Override
        public void save(PolicyAdjustmentTicket ticket) {
            saved.add(ticket);
            tickets.put(ticket.ticketId(), ticket);
        }

        @Override
        public boolean updateIfStatus(String ticketId,
                                      PolicyAdjustmentTicketStatus expectedStatus,
                                      PolicyAdjustmentTicket ticket) {
            PolicyAdjustmentTicket current = tickets.get(ticketId);
            if (!casResult || current == null || current.status() != expectedStatus) {
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
            Set<String> known = tickets.values().stream()
                    .map(PolicyAdjustmentTicket::fingerprint)
                    .collect(java.util.stream.Collectors.toSet());
            return fingerprints.stream().filter(known::contains)
                    .collect(java.util.stream.Collectors.toSet());
        }
    }
}

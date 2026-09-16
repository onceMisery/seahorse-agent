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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentProposal;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningSignal;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketPage;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcPolicyAdjustmentTicketAdapterTests {

    private static final Instant NOW = Instant.parse("2026-09-01T00:00:00Z");

    private JdbcPolicyAdjustmentTicketAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:policy-ticket-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        new JdbcChatSchemaUpgrade(dataSource).upgrade();
        adapter = new JdbcPolicyAdjustmentTicketAdapter(dataSource, new ObjectMapper());
    }

    @Test
    void shouldRoundTripEveryField() {
        adapter.save(ticket("ticket-1", "tenant-1", PolicyAdjustmentTicketStatus.PENDING, null, null, null));

        Optional<PolicyAdjustmentTicket> found = adapter.findById("ticket-1");

        assertThat(found).isPresent();
        PolicyAdjustmentTicket ticket = found.get();
        assertThat(ticket.tenantId()).isEqualTo("tenant-1");
        assertThat(ticket.targetKey()).isEqualTo("memory:target:a");
        assertThat(ticket.policyKey()).isEqualTo(PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE);
        assertThat(ticket.direction()).isEqualTo(PolicyAdjustmentProposal.Direction.TIGHTEN);
        assertThat(ticket.status()).isEqualTo(PolicyAdjustmentTicketStatus.PENDING);
        assertThat(ticket.sampleCount()).isEqualTo(21);
        assertThat(ticket.evidenceIds()).containsExactly("evidence-1", "evidence-2");
        assertThat(ticket.fingerprint()).isEqualTo("TIGHTEN|" + PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE
                + "|memory:target:a");
        assertThat(ticket.createdAt()).isEqualTo(NOW);
        assertThat(ticket.updatedAt()).isEqualTo(NOW);
    }

    @Test
    void shouldPersistDecisionsAndAppliedState() {
        adapter.save(ticket("ticket-1", "tenant-1", PolicyAdjustmentTicketStatus.PENDING, null, null, null));
        PolicyAdjustmentTicket approved = adapter.findById("ticket-1").orElseThrow()
                .withStatus(PolicyAdjustmentTicketStatus.APPROVED, "alice", "合理", NOW.plusSeconds(10));
        assertThat(adapter.updateIfStatus("ticket-1", PolicyAdjustmentTicketStatus.PENDING, approved)).isTrue();

        PolicyAdjustmentTicket applied = approved.withStatus(
                PolicyAdjustmentTicketStatus.APPLIED, "bob", "已改为 0.85", NOW.plusSeconds(20));
        assertThat(adapter.updateIfStatus("ticket-1", PolicyAdjustmentTicketStatus.APPROVED, applied)).isTrue();

        PolicyAdjustmentTicket reloaded = adapter.findById("ticket-1").orElseThrow();
        assertThat(reloaded.status()).isEqualTo(PolicyAdjustmentTicketStatus.APPLIED);
        assertThat(reloaded.decidedBy()).isEqualTo("alice", "落地不能抹掉审批记录");
        assertThat(reloaded.decisionComment()).isEqualTo("合理");
        assertThat(reloaded.appliedBy()).isEqualTo("bob");
        assertThat(reloaded.appliedAt()).isEqualTo(NOW.plusSeconds(20));
    }

    @Test
    void shouldRejectUpdateWhenStatusAlreadyMoved() {
        adapter.save(ticket("ticket-1", "tenant-1", PolicyAdjustmentTicketStatus.PENDING, null, null, null));
        PolicyAdjustmentTicket approved = adapter.findById("ticket-1").orElseThrow()
                .withStatus(PolicyAdjustmentTicketStatus.APPROVED, "alice", "合理", NOW.plusSeconds(10));

        assertThat(adapter.updateIfStatus("ticket-1", PolicyAdjustmentTicketStatus.PENDING, approved)).isTrue();
        assertThat(adapter.updateIfStatus("ticket-1", PolicyAdjustmentTicketStatus.PENDING, approved))
                .as("同一张工单的第二个审批人必须失败，而不是静默覆盖第一个人的决策")
                .isFalse();
    }

    @Test
    void shouldPageByTenantAndStatus() {
        adapter.save(ticket("ticket-1", "tenant-1", PolicyAdjustmentTicketStatus.PENDING, null, null, null));
        adapter.save(ticket("ticket-2", "tenant-1", PolicyAdjustmentTicketStatus.APPROVED, "alice", "合理", null));
        adapter.save(ticket("ticket-3", "tenant-2", PolicyAdjustmentTicketStatus.PENDING, null, null, null));

        PolicyAdjustmentTicketPage all = adapter.page(
                new PolicyAdjustmentTicketQuery("tenant-1", null, 1L, 10L));
        PolicyAdjustmentTicketPage pending = adapter.page(
                new PolicyAdjustmentTicketQuery("tenant-1", PolicyAdjustmentTicketStatus.PENDING, 1L, 10L));

        assertThat(all.records()).hasSize(2);
        assertThat(all.total()).isEqualTo(2);
        assertThat(pending.records()).hasSize(1);
        assertThat(pending.records().get(0).ticketId()).isEqualTo("ticket-1");
        assertThat(pending.pages()).isEqualTo(1L);
    }

    @Test
    void shouldReportOnlyKnownFingerprints() {
        adapter.save(ticket("ticket-1", "tenant-1", PolicyAdjustmentTicketStatus.PENDING, null, null, null));

        Set<String> known = adapter.existingFingerprints("tenant-1", List.of(
                "TIGHTEN|" + PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE + "|memory:target:a",
                "TIGHTEN|" + PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE + "|memory:target:zzz"));

        assertThat(known).containsExactly(
                "TIGHTEN|" + PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE + "|memory:target:a");
        assertThat(adapter.existingFingerprints("tenant-1", List.of())).isEmpty();
        assertThat(adapter.existingFingerprints("tenant-1", null)).isEmpty();
    }

    @Test
    void shouldReturnEmptyForUnknownTicket() {
        assertThat(adapter.findById("missing")).isEmpty();
        assertThat(adapter.findById(null)).isEmpty();
    }

    private static PolicyAdjustmentTicket ticket(String ticketId,
                                                 String tenantId,
                                                 PolicyAdjustmentTicketStatus status,
                                                 String decidedBy,
                                                 String decisionComment,
                                                 Instant decidedAt) {
        PolicyAdjustmentTicket base = PolicyAdjustmentTicket.pending(
                ticketId, tenantId, proposal(), fingerprint(), "loop", NOW);
        if (status == PolicyAdjustmentTicketStatus.PENDING) {
            return base;
        }
        return base.withStatus(status, decidedBy, decisionComment, decidedAt == null ? NOW : decidedAt);
    }

    private static PolicyAdjustmentProposal proposal() {
        LearningSignal signal = new LearningSignal(
                "memory:target:a", "memory", 3, 9, 1, 8, List.of("evidence-1", "evidence-2"));
        return PolicyAdjustmentProposal.tighten(signal,
                PolicyAdjustmentProposal.POLICY_AUTO_COMMIT_CONFIDENCE, 0.05D);
    }

    private static String fingerprint() {
        return PolicyAdjustmentTicket.fingerprintOf(proposal());
    }
}

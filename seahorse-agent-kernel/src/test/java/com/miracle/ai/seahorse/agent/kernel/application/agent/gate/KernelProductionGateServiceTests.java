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

package com.miracle.ai.seahorse.agent.kernel.application.agent.gate;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalSummaryPage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentEvalSummaryRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ProductionGateRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.QuotaPolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SreHealthReportProviderPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class KernelProductionGateServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldGenerateSnapshotReportWithoutPublishingAgent() {
        MemoryProductionGateRepository repository = new MemoryProductionGateRepository();
        KernelProductionGateService service = new KernelProductionGateService(repository, CLOCK);

        ProductionGateReport report = service.generate(agent());

        assertEquals(report, repository.saved);
        assertEquals("agent-1", report.agentId());
        assertEquals("version-1", report.versionId());
        assertEquals(ProductionGateStatus.WARN, report.status());
        assertEquals(ProductionGateStatus.PASS,
                report.item(ProductionGateCheckCode.OWNER_PRESENT).orElseThrow().status());
        assertEquals(ProductionGateStatus.PASS,
                report.item(ProductionGateCheckCode.AUDIT_LEDGER_ENABLED).orElseThrow().status());
        assertEquals(ProductionGateStatus.WARN,
                report.item(ProductionGateCheckCode.EVAL_PASSING).orElseThrow().status());
        assertEquals(ProductionGateStatus.WARN,
                report.item(ProductionGateCheckCode.QUOTA_CONFIGURED).orElseThrow().status());
        assertEquals(ProductionGateStatus.WARN,
                report.item(ProductionGateCheckCode.SRE_HEALTH_GREEN).orElseThrow().status());
    }

    @Test
    void shouldFailHighRiskAgentWithoutSafetyAndTrajectoryEval() {
        MemoryProductionGateRepository repository = new MemoryProductionGateRepository();
        KernelProductionGateService service = new KernelProductionGateService(
                repository,
                null,
                new MemoryAgentEvalSummaryRepository(),
                CLOCK);

        ProductionGateReport report = service.generate(agent(AgentRiskLevel.HIGH));

        assertEquals(ProductionGateStatus.FAIL, report.status());
        assertEquals(ProductionGateStatus.FAIL,
                report.item(ProductionGateCheckCode.EVAL_PASSING).orElseThrow().status());
    }

    @Test
    void shouldWarnLowRiskAgentWithoutEval() {
        MemoryProductionGateRepository repository = new MemoryProductionGateRepository();
        KernelProductionGateService service = new KernelProductionGateService(
                repository,
                null,
                new MemoryAgentEvalSummaryRepository(),
                CLOCK);

        ProductionGateReport report = service.generate(agent(AgentRiskLevel.LOW));

        assertEquals(ProductionGateStatus.WARN,
                report.item(ProductionGateCheckCode.EVAL_PASSING).orElseThrow().status());
    }

    @Test
    void shouldFailWhenLatestEvalFailsOrHighRiskEvalIsStale() {
        MemoryAgentEvalSummaryRepository evalRepository = new MemoryAgentEvalSummaryRepository();
        evalRepository.append(summary("safety-pass", AgentEvalType.SAFETY, AgentEvalStatus.PASS, NOW));
        evalRepository.append(summary("trajectory-fail", AgentEvalType.TRAJECTORY, AgentEvalStatus.FAIL, NOW));
        KernelProductionGateService service = new KernelProductionGateService(
                new MemoryProductionGateRepository(),
                null,
                evalRepository,
                CLOCK);

        ProductionGateReport failedReport = service.generate(agent(AgentRiskLevel.HIGH));

        assertEquals(ProductionGateStatus.FAIL,
                failedReport.item(ProductionGateCheckCode.EVAL_PASSING).orElseThrow().status());

        MemoryAgentEvalSummaryRepository staleEvalRepository = new MemoryAgentEvalSummaryRepository();
        staleEvalRepository.append(summary("safety-stale", AgentEvalType.SAFETY, AgentEvalStatus.PASS,
                NOW.minus(AgentEvalLimits.DEFAULT_MAX_AGE_DAYS + 1L, java.time.temporal.ChronoUnit.DAYS)));
        staleEvalRepository.append(summary("trajectory-pass", AgentEvalType.TRAJECTORY, AgentEvalStatus.PASS, NOW));
        KernelProductionGateService staleService = new KernelProductionGateService(
                new MemoryProductionGateRepository(),
                null,
                staleEvalRepository,
                CLOCK);

        ProductionGateReport staleReport = staleService.generate(agent(AgentRiskLevel.HIGH));

        assertEquals(ProductionGateStatus.FAIL,
                staleReport.item(ProductionGateCheckCode.EVAL_PASSING).orElseThrow().status());
    }

    @Test
    void shouldUseQuotaAndSreEvidenceWhenConfigured() {
        MemoryQuotaPolicyRepository quotaRepository = new MemoryQuotaPolicyRepository();
        quotaRepository.upsert(quotaPolicy("quota-1", QuotaScope.AGENT, "agent-1"));
        SreHealthReportProviderPort greenSre = () -> new SreHealthReport(
                "sre-1",
                SreHealthStatus.GREEN,
                List.of(new SreHealthItem("database", SreHealthStatus.GREEN, "ok", "db:primary")),
                NOW);
        KernelProductionGateService service = new KernelProductionGateService(
                new MemoryProductionGateRepository(),
                null,
                null,
                quotaRepository,
                greenSre,
                CLOCK);

        ProductionGateReport report = service.generate(agent(AgentRiskLevel.MEDIUM));

        assertEquals(ProductionGateStatus.PASS,
                report.item(ProductionGateCheckCode.QUOTA_CONFIGURED).orElseThrow().status());
        assertEquals(ProductionGateStatus.PASS,
                report.item(ProductionGateCheckCode.SRE_HEALTH_GREEN).orElseThrow().status());
    }

    @Test
    void shouldFailHighRiskGateWhenQuotaPolicyIsMissing() {
        KernelProductionGateService service = new KernelProductionGateService(
                new MemoryProductionGateRepository(),
                null,
                null,
                new MemoryQuotaPolicyRepository(),
                () -> new SreHealthReport("sre-1", SreHealthStatus.GREEN, List.of(), NOW),
                CLOCK);

        ProductionGateReport report = service.generate(agent(AgentRiskLevel.CRITICAL));

        assertEquals(ProductionGateStatus.FAIL,
                report.item(ProductionGateCheckCode.QUOTA_CONFIGURED).orElseThrow().status());
    }

    private static AgentDefinition agent() {
        return agent(AgentRiskLevel.MEDIUM);
    }

    private static AgentDefinition agent(AgentRiskLevel riskLevel) {
        return new AgentDefinition(
                "agent-1",
                "tenant-a",
                "Agent",
                "Agent",
                "owner-1",
                "platform",
                AgentType.ASSISTANT,
                null,
                AgentStatus.PUBLISHED,
                riskLevel,
                "version-1",
                NOW,
                NOW);
    }

    private static AgentEvalSummary summary(String summaryId,
                                            AgentEvalType evalType,
                                            AgentEvalStatus status,
                                            Instant createdAt) {
        return new AgentEvalSummary(
                summaryId,
                "tenant-a",
                "agent-1",
                "version-1",
                evalType,
                status,
                status == AgentEvalStatus.FAIL ? 0.4d : 0.95d,
                0.9d,
                0.7d,
                8,
                "dataset:v1",
                "eval-run-1",
                List.of("trace:1"),
                "admin-1",
                createdAt);
    }

    private static QuotaPolicy quotaPolicy(String policyId, QuotaScope scope, String subjectId) {
        return new QuotaPolicy(
                policyId,
                "tenant-a",
                scope,
                subjectId,
                QuotaPolicyStatus.ACTIVE,
                10_000L,
                100L,
                25.0d,
                QuotaPolicyLimits.DEFAULT_WARN_RATIO,
                NOW,
                NOW);
    }

    private static final class MemoryProductionGateRepository implements ProductionGateRepositoryPort {

        private ProductionGateReport saved;

        @Override
        public ProductionGateReport save(ProductionGateReport report) {
            saved = report;
            return report;
        }

        @Override
        public Optional<ProductionGateReport> latest(String agentId) {
            return Optional.ofNullable(saved)
                    .filter(report -> report.agentId().equals(agentId));
        }
    }

    private static final class MemoryAgentEvalSummaryRepository implements AgentEvalSummaryRepositoryPort {

        private final List<AgentEvalSummary> records = new ArrayList<>();

        @Override
        public AgentEvalSummary append(AgentEvalSummary summary) {
            records.add(summary);
            return summary;
        }

        @Override
        public Optional<AgentEvalSummary> findLatest(String tenantId,
                                                     String agentId,
                                                     String versionId,
                                                     AgentEvalType evalType) {
            return records.stream()
                    .filter(summary -> summary.tenantId().equals(tenantId))
                    .filter(summary -> summary.agentId().equals(agentId))
                    .filter(summary -> summary.versionId().equals(versionId))
                    .filter(summary -> summary.evalType() == evalType)
                    .max(Comparator.comparing(AgentEvalSummary::createdAt)
                            .thenComparing(AgentEvalSummary::summaryId));
        }

        @Override
        public AgentEvalSummaryPage findHistory(AgentEvalSummaryQuery query) {
            List<AgentEvalSummary> filtered = records.stream()
                    .filter(summary -> summary.tenantId().equals(query.tenantId()))
                    .filter(summary -> summary.agentId().equals(query.agentId()))
                    .filter(summary -> summary.versionId().equals(query.versionId()))
                    .filter(summary -> query.evalType() == null || summary.evalType() == query.evalType())
                    .sorted(Comparator.comparing(AgentEvalSummary::createdAt)
                            .thenComparing(AgentEvalSummary::summaryId)
                            .reversed())
                    .toList();
            return new AgentEvalSummaryPage(filtered, filtered.size(), query.size(), query.current(), 1L);
        }
    }

    private static final class MemoryQuotaPolicyRepository implements QuotaPolicyRepositoryPort {

        private final List<QuotaPolicy> records = new ArrayList<>();

        @Override
        public QuotaPolicy upsert(QuotaPolicy policy) {
            records.removeIf(record -> record.policyId().equals(policy.policyId()));
            records.add(policy);
            return policy;
        }

        @Override
        public Optional<QuotaPolicy> findActive(String tenantId, QuotaScope scope, String subjectId) {
            return records.stream()
                    .filter(policy -> policy.tenantId().equals(tenantId))
                    .filter(policy -> policy.scope() == scope)
                    .filter(policy -> policy.subjectId().equals(subjectId))
                    .filter(policy -> policy.status() == QuotaPolicyStatus.ACTIVE)
                    .findFirst();
        }

        @Override
        public void disable(String policyId, Instant updatedAt) {
            records.replaceAll(policy -> policy.policyId().equals(policyId)
                    ? policy.disable(updatedAt)
                    : policy);
        }
    }
}

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

package com.miracle.ai.seahorse.agent.kernel.application.agent.rollout;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentCatalogPage;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentRollbackStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentTemplate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutFailureCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutLimits;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentVersionRollout;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentCatalogQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentFactoryInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentPublishValidationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutActionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutRollbackCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentVersionRollbackCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRolloutRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ProductionGateRepositoryPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class KernelAgentRolloutServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldFailClosedWhenPromoteHasNoPassingProductionGateReport() {
        MemoryRolloutRepository rolloutRepository = new MemoryRolloutRepository();
        MemoryProductionGateRepository gateRepository = new MemoryProductionGateRepository();
        KernelAgentRolloutService service = service(rolloutRepository, gateRepository, new RecordingFactoryPort());
        AgentVersionRollout rollout = service.createCanary(new AgentRolloutCreateCommand(
                "tenant-1",
                "agent-1",
                "version-1",
                null,
                "operator-1"));

        AgentVersionRollout missingGate = service.promote(new AgentRolloutActionCommand(
                "tenant-1",
                "agent-1",
                rollout.rolloutId(),
                "operator-1",
                "promote"));

        assertEquals(AgentRolloutStatus.FAILED, missingGate.status());
        assertEquals(AgentRolloutFailureCode.GATE_MISSING, missingGate.failureCode());

        AgentVersionRollout second = service.createCanary(new AgentRolloutCreateCommand(
                "tenant-1",
                "agent-1",
                "version-1",
                AgentRolloutLimits.DEFAULT_CANARY_PERCENT,
                "operator-1"));
        gateRepository.report = report("gate-fail", ProductionGateStatus.FAIL, "version-1");

        AgentVersionRollout failedGate = service.promote(new AgentRolloutActionCommand(
                "tenant-1",
                "agent-1",
                second.rolloutId(),
                "operator-1",
                "promote"));

        assertEquals(AgentRolloutStatus.FAILED, failedGate.status());
        assertEquals(AgentRolloutFailureCode.GATE_FAILED, failedGate.failureCode());
    }

    @Test
    void shouldPromoteOnlyWhenLatestGatePassesForSameVersion() {
        MemoryRolloutRepository rolloutRepository = new MemoryRolloutRepository();
        MemoryProductionGateRepository gateRepository = new MemoryProductionGateRepository();
        gateRepository.report = report("gate-pass", ProductionGateStatus.PASS, "version-1");
        KernelAgentRolloutService service = service(rolloutRepository, gateRepository, new RecordingFactoryPort());
        AgentVersionRollout rollout = service.createCanary(new AgentRolloutCreateCommand(
                "tenant-1",
                "agent-1",
                "version-1",
                AgentRolloutLimits.DEFAULT_CANARY_PERCENT,
                "operator-1"));

        AgentVersionRollout promoted = service.promote(new AgentRolloutActionCommand(
                "tenant-1",
                "agent-1",
                rollout.rolloutId(),
                "operator-1",
                "promote"));

        assertEquals(AgentRolloutStatus.PROMOTED, promoted.status());
        assertEquals("gate-pass", promoted.gateReportId());
        assertEquals(promoted, rolloutRepository.findById(rollout.rolloutId()).orElseThrow());
    }

    @Test
    void shouldRollbackThroughAgentFactoryPortWithoutChangingActiveVersionDirectly() {
        MemoryRolloutRepository rolloutRepository = new MemoryRolloutRepository();
        RecordingFactoryPort factoryPort = new RecordingFactoryPort();
        KernelAgentRolloutService service = service(
                rolloutRepository,
                new MemoryProductionGateRepository(),
                factoryPort);
        AgentVersionRollout rollout = service.createCanary(new AgentRolloutCreateCommand(
                "tenant-1",
                "agent-1",
                "version-2",
                AgentRolloutLimits.DEFAULT_CANARY_PERCENT,
                "operator-1"));

        AgentVersionRollout rolledBack = service.rollback(new AgentRolloutRollbackCommand(
                "tenant-1",
                "agent-1",
                rollout.rolloutId(),
                "version-1",
                "operator-1",
                "rollback canary"));

        assertEquals(AgentRolloutStatus.ROLLED_BACK, rolledBack.status());
        assertNotNull(factoryPort.lastRollbackCommand);
        assertEquals("version-1", factoryPort.lastRollbackCommand.versionId());
        assertEquals(AgentRollbackReasonCode.CANARY_FAILED, factoryPort.lastRollbackCommand.reasonCode());
    }

    private static KernelAgentRolloutService service(MemoryRolloutRepository rolloutRepository,
                                                     MemoryProductionGateRepository gateRepository,
                                                     RecordingFactoryPort factoryPort) {
        return new KernelAgentRolloutService(rolloutRepository, gateRepository, factoryPort, CLOCK);
    }

    private static ProductionGateReport report(String reportId,
                                               ProductionGateStatus status,
                                               String versionId) {
        return new ProductionGateReport(
                reportId,
                "agent-1",
                versionId,
                status,
                List.of(ProductionGateCheckItem.pass(
                        ProductionGateCheckCode.OWNER_PRESENT,
                        "owner exists")),
                NOW);
    }

    private static final class MemoryRolloutRepository implements AgentRolloutRepositoryPort {

        private final Map<String, AgentVersionRollout> records = new LinkedHashMap<>();

        @Override
        public AgentVersionRollout save(AgentVersionRollout rollout) {
            records.put(rollout.rolloutId(), rollout);
            return rollout;
        }

        @Override
        public Optional<AgentVersionRollout> findById(String rolloutId) {
            return Optional.ofNullable(records.get(rolloutId));
        }

        @Override
        public Optional<AgentVersionRollout> findLatest(String tenantId, String agentId, String versionId) {
            return records.values().stream()
                    .filter(rollout -> rollout.tenantId().equals(tenantId))
                    .filter(rollout -> rollout.agentId().equals(agentId))
                    .filter(rollout -> rollout.versionId().equals(versionId))
                    .reduce((first, second) -> second);
        }
    }

    private static final class MemoryProductionGateRepository implements ProductionGateRepositoryPort {

        private ProductionGateReport report;

        @Override
        public ProductionGateReport save(ProductionGateReport report) {
            this.report = report;
            return report;
        }

        @Override
        public Optional<ProductionGateReport> latest(String agentId) {
            return Optional.ofNullable(report)
                    .filter(value -> value.agentId().equals(agentId));
        }
    }

    private static final class RecordingFactoryPort implements AgentFactoryInboundPort {

        private AgentVersionRollbackCommand lastRollbackCommand;

        @Override
        public List<AgentTemplate> listTemplates(boolean includeDisabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition createFromTemplate(
                AgentFactoryCreateCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckReport validatePublish(
                AgentPublishValidationCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.miracle.ai.seahorse.agent.kernel.domain.agent.factory.AgentPublishCheckReport> latestPublishCheck(
                String agentId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentRollbackResult rollback(AgentVersionRollbackCommand command) {
            lastRollbackCommand = command;
            return new AgentRollbackResult(
                    "rollback-1",
                    command.agentId(),
                    "version-2",
                    command.versionId(),
                    AgentRollbackStatus.ROLLED_BACK,
                    command.reasonCode(),
                    NOW);
        }

        @Override
        public AgentCatalogPage catalog(AgentCatalogQuery query) {
            throw new UnsupportedOperationException();
        }
    }
}

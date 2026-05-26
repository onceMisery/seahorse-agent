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

package com.miracle.ai.seahorse.agent.kernel.application.agent.handoff;

import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoff;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.handoff.AgentHandoffStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentHandoffRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KernelAgentHandoffServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final Clock FIXED_CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldCancelPendingHandoffIdempotently() {
        MemoryAgentHandoffRepository repository = new MemoryAgentHandoffRepository();
        repository.save(handoff("handoff-1", AgentHandoffStatus.RUNNING, "child-run-1"));
        RecordingRunPort runPort = new RecordingRunPort();
        KernelAgentHandoffService service = new KernelAgentHandoffService(
                repository,
                runPort,
                new DefaultMeshPolicyPort(),
                FIXED_CLOCK);

        AgentHandoff first = service.cancel("handoff-1");
        AgentHandoff second = service.cancel("handoff-1");

        assertEquals(AgentHandoffStatus.CANCELLED, first.status());
        assertEquals(AgentHandoffStatus.CANCELLED, second.status());
        assertEquals(1, runPort.cancelledRunIds.size());
        assertEquals("child-run-1", runPort.cancelledRunIds.get(0));
    }

    @Test
    void shouldWriteAuditForCreateAndCancelWithoutRawInput() {
        MemoryAgentHandoffRepository repository = new MemoryAgentHandoffRepository();
        RecordingRunPort runPort = new RecordingRunPort();
        RecordingAuditRepository auditRepository = new RecordingAuditRepository();
        KernelAgentHandoffService service = new KernelAgentHandoffService(
                repository,
                runPort,
                new DefaultMeshPolicyPort(),
                new KernelAuditLedgerService(
                        auditRepository,
                        new AuditRedactionPolicy(),
                        AuditWriteFailurePolicy.FAIL_CLOSED),
                FIXED_CLOCK);

        AgentHandoff handoff = service.createLocalHandoff(new AgentHandoffCreateCommand(
                "tenant-1",
                "parent-run-1",
                "source-agent",
                "target-agent",
                "target-version-1",
                "delegate work",
                "raw secret-token input must not be audited",
                "{\"items\":[]}",
                1,
                List.of("source-agent"),
                "trace-1"));
        service.cancel(handoff.handoffId());

        assertEquals(2, auditRepository.saved.size());
        assertEquals(AuditEventType.AGENT_HANDOFF_CREATED, auditRepository.saved.get(0).eventType());
        assertEquals(AuditEventType.AGENT_HANDOFF_FINISHED, auditRepository.saved.get(1).eventType());
        assertEquals("parent-run-1", auditRepository.saved.get(0).runId());
        assertEquals("source-agent", auditRepository.saved.get(0).agentId());
        assertFalse(auditRepository.saved.get(0).redactedPayload().contains("secret-token"));
        assertFalse(auditRepository.saved.get(0).redactedPayload().contains("raw"));
        assertFalse(auditRepository.saved.get(1).redactedPayload().contains("secret-token"));
    }

    private static AgentHandoff handoff(String handoffId, AgentHandoffStatus status, String childRunId) {
        return new AgentHandoff(
                handoffId,
                "tenant-1",
                "parent-run-1",
                childRunId,
                "source-agent",
                "target-agent",
                status,
                null,
                "delegate work",
                "{\"input\":\"summary\"}",
                "{\"items\":[]}",
                NOW,
                NOW,
                null);
    }

    private static final class MemoryAgentHandoffRepository implements AgentHandoffRepositoryPort {
        private final Map<String, AgentHandoff> handoffs = new LinkedHashMap<>();

        @Override
        public AgentHandoff save(AgentHandoff handoff) {
            handoffs.put(handoff.handoffId(), handoff);
            return handoff;
        }

        @Override
        public AgentHandoff update(AgentHandoff handoff) {
            handoffs.put(handoff.handoffId(), handoff);
            return handoff;
        }

        @Override
        public Optional<AgentHandoff> findById(String handoffId) {
            return Optional.ofNullable(handoffs.get(handoffId));
        }

        @Override
        public List<AgentHandoff> listByParentRunId(String tenantId, String parentRunId) {
            return handoffs.values().stream()
                    .filter(handoff -> tenantId.equals(handoff.tenantId()))
                    .filter(handoff -> parentRunId.equals(handoff.parentRunId()))
                    .toList();
        }
    }

    private static final class RecordingAuditRepository implements AuditEventRepositoryPort {
        private final List<AuditEvent> saved = new ArrayList<>();

        @Override
        public AuditEvent save(AuditEvent event) {
            saved.add(event);
            return event;
        }

        @Override
        public Optional<AuditEvent> findById(String auditId) {
            return saved.stream()
                    .filter(event -> event.auditId().equals(auditId))
                    .findFirst();
        }

        @Override
        public AuditEventPage page(AuditEventQuery query) {
            return new AuditEventPage(saved, saved.size(), 10L, 1L, 1L);
        }
    }

    private static final class RecordingRunPort implements AgentRunInboundPort {
        private final List<String> cancelledRunIds = new ArrayList<>();

        @Override
        public AgentRun startRun(AgentRunStartCommand command) {
            return run("child-run-1", command.agentId(), command.versionId(), command.tenantId());
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.empty();
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep> listSteps(String runId) {
            return List.of();
        }

        @Override
        public AgentRun cancel(String runId) {
            cancelledRunIds.add(runId);
            return run(runId, "target-agent", "version-1", "tenant-1").cancel(NOW);
        }

        @Override
        public AgentRun retry(String runId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentRun succeed(String runId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public AgentRun fail(String runId, String errorCode, String errorMessage) {
            throw new UnsupportedOperationException();
        }

        private static AgentRun run(String runId, String agentId, String versionId, String tenantId) {
            return new AgentRun(
                    runId,
                    agentId,
                    versionId,
                    tenantId,
                    "user-1",
                    null,
                    AgentRunTriggerType.A2A,
                    "handoff input",
                    AgentRunStatus.RUNNING,
                    null,
                    0,
                    0,
                    BigDecimal.ZERO,
                    null,
                    null,
                    NOW,
                    null);
        }
    }
}

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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentVersion;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentDefinitionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelAgentRunServiceTests {

    private static final Clock FIXED_CLOCK = Clock.fixed(Instant.parse("2026-05-23T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void shouldStartRunWithLatestPublishedVersionAndCurrentUser() {
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.save(agent("data-analyst", AgentStatus.PUBLISHED, "version-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService service = new KernelAgentRunService(
                definitionRepository, runRepository, currentUser(), FIXED_CLOCK);

        AgentRun run = service.startRun(new AgentRunStartCommand(
                "data-analyst",
                null,
                AgentDefinition.DEFAULT_TENANT_ID,
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "summarized input",
                "trace-1"));

        assertEquals(AgentRunStatus.RUNNING, run.status());
        assertEquals("version-1", run.versionId());
        assertEquals("user-1", run.userId());
        assertEquals("summarized input", run.inputSummary());
        assertTrue(runRepository.runs.containsKey(run.runId()));
    }

    @Test
    void shouldStartLegacyReactAgentRunWithoutRegistryDefinition() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService service = new KernelAgentRunService(
                new MemoryAgentDefinitionRepository(), runRepository, currentUser(), FIXED_CLOCK);

        AgentRun run = service.startRun(new AgentRunStartCommand(
                "legacy-react-agent",
                null,
                AgentDefinition.DEFAULT_TENANT_ID,
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "summarized input",
                "trace-1"));

        assertEquals("legacy-react-agent", run.agentId());
        assertEquals(AgentRunStatus.RUNNING, run.status());
        assertEquals("user-1", run.userId());
        assertEquals("conversation-1", run.conversationId());
        assertTrue(runRepository.runs.containsKey(run.runId()));
    }

    @Test
    void shouldStartRunWithExplicitPublishedVersionWhenVersionExists() {
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.save(agent("data-analyst", AgentStatus.PUBLISHED, "version-2"));
        definitionRepository.saveVersion(agentVersion("data-analyst", "version-1", 1L));
        definitionRepository.saveVersion(agentVersion("data-analyst", "version-2", 2L));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService service = new KernelAgentRunService(
                definitionRepository, runRepository, currentUser(), FIXED_CLOCK);

        AgentRun run = service.startRun(new AgentRunStartCommand(
                "data-analyst",
                "version-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "summarized input",
                "trace-1"));

        assertEquals("version-1", run.versionId());
        assertTrue(runRepository.runs.containsKey(run.runId()));
    }

    @Test
    void shouldRejectExplicitMissingAgentVersion() {
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.save(agent("data-analyst", AgentStatus.PUBLISHED, "version-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService service = new KernelAgentRunService(
                definitionRepository, runRepository, currentUser(), FIXED_CLOCK);

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class, () -> service.startRun(
                new AgentRunStartCommand(
                        "data-analyst",
                        "missing-version",
                        AgentDefinition.DEFAULT_TENANT_ID,
                        "conversation-1",
                        AgentRunTriggerType.CHAT,
                        "summary",
                        null)));

        assertEquals("Agent version does not exist", error.getMessage());
        assertTrue(runRepository.runs.isEmpty());
    }

    @Test
    void shouldRejectRegisteredAgentRunWithoutVersionBinding() {
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.save(agent("draft-agent", AgentStatus.DRAFT, null));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService service = new KernelAgentRunService(
                definitionRepository, runRepository, currentUser(), FIXED_CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.startRun(
                new AgentRunStartCommand(
                        "draft-agent",
                        null,
                        AgentDefinition.DEFAULT_TENANT_ID,
                        "conversation-1",
                        AgentRunTriggerType.CHAT,
                        "summary",
                        null)));

        assertEquals("Agent run requires a versionId", error.getMessage());
        assertTrue(runRepository.runs.isEmpty());
    }

    @Test
    void shouldMarkRunSucceeded() {
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.save(agent("ops-agent", AgentStatus.PUBLISHED, "version-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService service = new KernelAgentRunService(
                definitionRepository, runRepository, currentUser(), FIXED_CLOCK);
        AgentRun run = service.startRun(new AgentRunStartCommand(
                "ops-agent",
                null,
                AgentDefinition.DEFAULT_TENANT_ID,
                null,
                AgentRunTriggerType.API,
                "summary",
                null));

        AgentRun succeeded = service.succeed(run.runId());

        assertEquals(AgentRunStatus.SUCCEEDED, succeeded.status());
        assertEquals(FIXED_CLOCK.instant(), succeeded.finishedAt());
    }

    @Test
    void shouldMarkRunFailed() {
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.save(agent("ops-agent", AgentStatus.PUBLISHED, "version-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService service = new KernelAgentRunService(
                definitionRepository, runRepository, currentUser(), FIXED_CLOCK);
        AgentRun run = service.startRun(new AgentRunStartCommand(
                "ops-agent",
                null,
                AgentDefinition.DEFAULT_TENANT_ID,
                null,
                AgentRunTriggerType.API,
                "summary",
                null));

        AgentRun failed = service.fail(run.runId(), "AGENT_LOOP_FAILED", "boom");

        assertEquals(AgentRunStatus.FAILED, failed.status());
        assertEquals("AGENT_LOOP_FAILED", failed.errorCode());
        assertEquals("boom", failed.errorMessage());
        assertEquals(FIXED_CLOCK.instant(), failed.finishedAt());
    }

    @Test
    void shouldRetryFailedRunIdempotently() {
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.save(agent("ops-agent", AgentStatus.PUBLISHED, "version-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService service = new KernelAgentRunService(
                definitionRepository, runRepository, currentUser(), FIXED_CLOCK);
        AgentRun run = service.startRun(new AgentRunStartCommand(
                "ops-agent",
                null,
                AgentDefinition.DEFAULT_TENANT_ID,
                null,
                AgentRunTriggerType.API,
                "summary",
                null));
        service.fail(run.runId(), "AGENT_LOOP_FAILED", "boom");

        AgentRun first = service.retry(run.runId());
        AgentRun second = service.retry(run.runId());

        assertEquals(AgentRunStatus.RETRYING, first.status());
        assertEquals(AgentRunStatus.RETRYING, second.status());
        assertEquals(null, first.errorCode());
        assertEquals(null, first.errorMessage());
        assertEquals(null, first.finishedAt());
    }

    @Test
    void shouldRejectRetryForNonFailedRun() {
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.save(agent("ops-agent", AgentStatus.PUBLISHED, "version-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService service = new KernelAgentRunService(
                definitionRepository, runRepository, currentUser(), FIXED_CLOCK);
        AgentRun run = service.startRun(new AgentRunStartCommand(
                "ops-agent",
                null,
                AgentDefinition.DEFAULT_TENANT_ID,
                null,
                AgentRunTriggerType.API,
                "summary",
                null));

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.retry(run.runId()));

        assertEquals("Only FAILED runs can be retried", error.getMessage());
    }

    @Test
    void shouldRejectDisabledAgentRun() {
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.save(agent("disabled-agent", AgentStatus.DISABLED, "version-1"));
        KernelAgentRunService service = new KernelAgentRunService(
                definitionRepository, new MemoryAgentRunRepository(), currentUser(), FIXED_CLOCK);

        IllegalStateException error = assertThrows(IllegalStateException.class, () -> service.startRun(
                new AgentRunStartCommand(
                        "disabled-agent",
                        null,
                        AgentDefinition.DEFAULT_TENANT_ID,
                        null,
                        AgentRunTriggerType.API,
                        "summary",
                        null)));
        assertEquals("DISABLED Agent cannot start a new run", error.getMessage());
    }

    @Test
    void shouldCancelRunIdempotently() {
        MemoryAgentDefinitionRepository definitionRepository = new MemoryAgentDefinitionRepository();
        definitionRepository.save(agent("ops-agent", AgentStatus.PUBLISHED, "version-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        KernelAgentRunService service = new KernelAgentRunService(
                definitionRepository, runRepository, currentUser(), FIXED_CLOCK);
        AgentRun run = service.startRun(new AgentRunStartCommand(
                "ops-agent",
                null,
                AgentDefinition.DEFAULT_TENANT_ID,
                null,
                AgentRunTriggerType.API,
                "summary",
                null));

        AgentRun first = service.cancel(run.runId());
        AgentRun second = service.cancel(run.runId());

        assertEquals(AgentRunStatus.CANCELLED, first.status());
        assertEquals(AgentRunStatus.CANCELLED, second.status());
        assertEquals(FIXED_CLOCK.instant(), second.finishedAt());
    }

    private static AgentDefinition agent(String agentId, AgentStatus status, String latestVersionId) {
        Instant now = FIXED_CLOCK.instant();
        return new AgentDefinition(agentId, AgentDefinition.DEFAULT_TENANT_ID, agentId, null, "owner-1", null,
                AgentType.ASSISTANT, null, status, AgentRiskLevel.MEDIUM, latestVersionId, now, now);
    }

    private static AgentVersion agentVersion(String agentId, String versionId, long versionNo) {
        return new AgentVersion(
                versionId,
                agentId,
                versionNo,
                "You are an assistant.",
                AgentVersion.EMPTY_JSON_OBJECT,
                AgentVersion.EMPTY_JSON_OBJECT,
                AgentVersion.EMPTY_JSON_OBJECT,
                AgentVersion.EMPTY_JSON_OBJECT,
                "admin-1",
                FIXED_CLOCK.instant(),
                "publish version");
    }

    private static CurrentUserPort currentUser() {
        return () -> Optional.of(new CurrentUser(1L, "alice", "user", null));
    }

    private static class MemoryAgentDefinitionRepository implements AgentDefinitionRepositoryPort {
        private final Map<String, AgentDefinition> definitions = new LinkedHashMap<>();
        private final Map<String, AgentVersion> versions = new LinkedHashMap<>();

        void save(AgentDefinition definition) {
            definitions.put(definition.agentId(), definition);
        }

        @Override
        public void create(AgentDefinition definition) {
            save(definition);
        }

        @Override
        public void update(AgentDefinition definition) {
            save(definition);
        }

        @Override
        public Optional<AgentDefinition> findById(String agentId) {
            return Optional.ofNullable(definitions.get(agentId));
        }

        @Override
        public AgentDefinitionPage page(String tenantId, long current, long size, String keyword) {
            return new AgentDefinitionPage(List.copyOf(definitions.values()), definitions.size(), size, current, 1);
        }

        @Override
        public long nextVersionNo(String agentId) {
            return 1L;
        }

        @Override
        public void saveVersion(AgentVersion version) {
            versions.put(versionKey(version.agentId(), version.versionId()), version);
        }

        @Override
        public Optional<AgentVersion> latestVersion(String agentId) {
            return Optional.empty();
        }

        @Override
        public Optional<AgentVersion> findVersion(String agentId, String versionId) {
            return Optional.ofNullable(versions.get(versionKey(agentId, versionId)));
        }

        private String versionKey(String agentId, String versionId) {
            return agentId + ":" + versionId;
        }
    }

    private static class MemoryAgentRunRepository implements AgentRunRepositoryPort {
        private final Map<String, AgentRun> runs = new LinkedHashMap<>();
        private final List<AgentStep> steps = new ArrayList<>();

        @Override
        public void createRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public void updateRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public void appendStep(AgentStep step) {
            steps.add(step);
        }

        @Override
        public List<AgentStep> listSteps(String runId) {
            return steps.stream()
                    .filter(step -> runId.equals(step.runId()))
                    .toList();
        }
    }
}

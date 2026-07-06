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

package com.miracle.ai.seahorse.agent.kernel.application.runcontext;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelRunContextSnapshotServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-06T00:00:00Z");

    @Test
    void shouldReturnSnapshotForOwningAgentRunUser() {
        MemorySnapshotRepository snapshotRepository = new MemorySnapshotRepository();
        snapshotRepository.save(snapshot("run-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("run-1", "user-1"));
        KernelRunContextSnapshotService service = new KernelRunContextSnapshotService(
                snapshotRepository,
                runRepository,
                currentUser("user-1", "user"));

        Optional<RunContextSnapshotRecord> snapshot = service.findByRunId("run-1");

        assertTrue(snapshot.isPresent());
        assertEquals("run-1", snapshot.orElseThrow().getRunId());
    }

    @Test
    void shouldReturnSnapshotForNumericWebUserIdOwner() {
        MemorySnapshotRepository snapshotRepository = new MemorySnapshotRepository();
        snapshotRepository.save(snapshot("run-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("run-1", "42"));
        KernelRunContextSnapshotService service = new KernelRunContextSnapshotService(
                snapshotRepository,
                runRepository,
                currentUser(42L, "owner", "user"));

        Optional<RunContextSnapshotRecord> snapshot = service.findByRunId("run-1");

        assertTrue(snapshot.isPresent());
        assertEquals("run-1", snapshot.orElseThrow().getRunId());
    }

    @Test
    void shouldDenySnapshotForUnrelatedAgentRunUser() {
        MemorySnapshotRepository snapshotRepository = new MemorySnapshotRepository();
        snapshotRepository.save(snapshot("run-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("run-1", "user-1"));
        KernelRunContextSnapshotService service = new KernelRunContextSnapshotService(
                snapshotRepository,
                runRepository,
                currentUser("user-2", "user"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.findByRunId("run-1"));

        assertEquals("鏉冮檺涓嶈冻", error.getMessage());
    }

    @Test
    void shouldAllowAdminSnapshotAcrossAgentRunUsers() {
        MemorySnapshotRepository snapshotRepository = new MemorySnapshotRepository();
        snapshotRepository.save(snapshot("run-1"));
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("run-1", "user-1"));
        KernelRunContextSnapshotService service = new KernelRunContextSnapshotService(
                snapshotRepository,
                runRepository,
                currentUser("admin-1", "admin"));

        Optional<RunContextSnapshotRecord> snapshot = service.findByRunId("run-1");

        assertTrue(snapshot.isPresent());
    }

    @Test
    void shouldKeepLegacyTaskSnapshotLookupWhenNoAgentRunExists() {
        MemorySnapshotRepository snapshotRepository = new MemorySnapshotRepository();
        snapshotRepository.save(snapshot("task-1"));
        KernelRunContextSnapshotService service = new KernelRunContextSnapshotService(
                snapshotRepository,
                new MemoryAgentRunRepository(),
                currentUser("user-2", "user"));

        Optional<RunContextSnapshotRecord> snapshot = service.findByRunId("task-1");

        assertTrue(snapshot.isPresent());
        assertEquals("task-1", snapshot.orElseThrow().getRunId());
    }

    @Test
    void shouldRedactSnapshotQueryProjectionWithoutMutatingRepositoryRecord() {
        MemorySnapshotRepository snapshotRepository = new MemorySnapshotRepository();
        RunContextSnapshotRecord stored = snapshot("run-1");
        stored.setExecutorConfigJson("{\"apiKey\":\"secret-api-key-value\",\"safe\":\"ok\"}");
        stored.setTraceContextJson("{\"authorization\":\"Bearer trace-secret-123456\",\"traceId\":\"trace-1\"}");
        stored.setSnapshotJson("{\"prompt\":\"Bearer prompt-secret-123456\","
                + "\"metadata\":{\"password\":\"hunter2\",\"safe\":\"ok\"}}");
        snapshotRepository.save(stored);
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("run-1", "user-1"));
        KernelRunContextSnapshotService service = new KernelRunContextSnapshotService(
                snapshotRepository,
                runRepository,
                currentUser("user-1", "user"));

        RunContextSnapshotRecord snapshot = service.findByRunId("run-1").orElseThrow();

        assertEquals("{\"apiKey\":\"[REDACTED]\",\"safe\":\"ok\"}", snapshot.getExecutorConfigJson());
        assertEquals("{\"authorization\":\"[REDACTED]\",\"traceId\":\"trace-1\"}", snapshot.getTraceContextJson());
        assertEquals("{\"prompt\":\"[REDACTED]\",\"metadata\":{\"password\":\"[REDACTED]\",\"safe\":\"ok\"}}",
                snapshot.getSnapshotJson());
        RunContextSnapshotRecord repositoryRecord = snapshotRepository.findByRunId("run-1").orElseThrow();
        assertTrue(repositoryRecord.getExecutorConfigJson().contains("secret-api-key-value"));
        assertTrue(repositoryRecord.getTraceContextJson().contains("trace-secret-123456"));
        assertTrue(repositoryRecord.getSnapshotJson().contains("prompt-secret-123456"));
        assertTrue(repositoryRecord.getSnapshotJson().contains("hunter2"));
    }

    private static RunContextSnapshotRecord snapshot(String runId) {
        RunContextSnapshotRecord record = new RunContextSnapshotRecord();
        record.setTenantId(AgentDefinition.DEFAULT_TENANT_ID);
        record.setRunId(runId);
        record.setSnapshotJson("{\"toolIds\":[\"echo\"]}");
        record.setTraceContextJson("{\"traceId\":\"trace-1\"}");
        record.setExecutorEngine("kernel");
        return record;
    }

    private static AgentRun run(String runId, String userId) {
        return new AgentRun(
                runId,
                "agent-1",
                "version-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                userId,
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "summary",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                NOW,
                null);
    }

    private static CurrentUserPort currentUser(String operator, String role) {
        return () -> Optional.of(new CurrentUser(1L, operator, role, null));
    }

    private static CurrentUserPort currentUser(Long userId, String operator, String role) {
        return () -> Optional.of(new CurrentUser(userId, operator, role, null));
    }

    private static final class MemorySnapshotRepository implements RunContextSnapshotRepositoryPort {
        private final Map<String, RunContextSnapshotRecord> snapshots = new LinkedHashMap<>();

        @Override
        public Long save(RunContextSnapshotRecord record) {
            snapshots.put(record.getRunId(), record);
            return 1L;
        }

        @Override
        public Optional<RunContextSnapshotRecord> findByRunId(String runId) {
            return Optional.ofNullable(snapshots.get(runId));
        }
    }

    private static final class MemoryAgentRunRepository implements AgentRunRepositoryPort {
        private final Map<String, AgentRun> runs = new LinkedHashMap<>();

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
        }

        @Override
        public List<AgentStep> listSteps(String runId) {
            return List.of();
        }
    }
}

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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelAgentCheckpointQueryServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-06T00:00:00Z");

    @Test
    void shouldMinimizePendingToolResourceRefsForQueryResults() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("user-1"));
        MemoryCheckpointRepository checkpointRepository = new MemoryCheckpointRepository();
        checkpointRepository.save(checkpoint(
                "{\"toolId\":\"memory-forget\",\"resourceRefs\":{\"knowledgeBaseId\":\"kb-secret-ref\","
                        + "\"secretResourceKey\":\"resource-secret-value\"}}"));
        KernelAgentCheckpointQueryService service = new KernelAgentCheckpointQueryService(
                runRepository,
                checkpointRepository,
                currentUser(1L, "user"));

        List<AgentCheckpoint> checkpoints = service.listByRunId(" run-1 ");

        assertEquals(1, checkpoints.size());
        String pendingToolCallJson = checkpoints.get(0).pendingToolCallJson();
        assertFalse(pendingToolCallJson.contains("resourceRefs"));
        assertFalse(pendingToolCallJson.contains("kb-secret-ref"));
        assertFalse(pendingToolCallJson.contains("secretResourceKey"));
        assertFalse(pendingToolCallJson.contains("resource-secret-value"));
        assertEquals(true, pendingToolCallJson.contains("\"resourceRefKeys\":[\"knowledgeBaseId\"]"));
        assertEquals(true, pendingToolCallJson.contains("\"resourceRefCount\":2"));
        assertEquals(true, pendingToolCallJson.contains("resourceRefHash"));
    }

    @Test
    void shouldFailClosedForMalformedPendingToolPayloads() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("user-1"));
        MemoryCheckpointRepository checkpointRepository = new MemoryCheckpointRepository();
        checkpointRepository.save(checkpoint("[\"resourceRefs\",{\"knowledgeBaseId\":\"kb-secret-ref\"}]"));
        KernelAgentCheckpointQueryService service = new KernelAgentCheckpointQueryService(
                runRepository,
                checkpointRepository,
                currentUser(1L, "user"));

        AgentCheckpoint checkpoint = service.listByRunId("run-1").get(0);

        assertEquals(null, checkpoint.pendingToolCallJson());
    }

    @Test
    void shouldDenyCheckpointQueryForUnrelatedUser() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("user-1"));
        MemoryCheckpointRepository checkpointRepository = new MemoryCheckpointRepository();
        checkpointRepository.save(checkpoint("{\"toolId\":\"memory-forget\"}"));
        KernelAgentCheckpointQueryService service = new KernelAgentCheckpointQueryService(
                runRepository,
                checkpointRepository,
                currentUser(2L, "user"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.listByRunId("run-1"));

        assertEquals("权限不足", error.getMessage());
    }

    @Test
    void shouldAllowAdminToQueryAnotherUsersCheckpoints() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("user-1"));
        MemoryCheckpointRepository checkpointRepository = new MemoryCheckpointRepository();
        checkpointRepository.save(checkpoint("{\"toolId\":\"memory-forget\"}"));
        KernelAgentCheckpointQueryService service = new KernelAgentCheckpointQueryService(
                runRepository,
                checkpointRepository,
                currentUser(9L, "admin"));

        List<AgentCheckpoint> checkpoints = service.listByRunId("run-1");

        assertEquals(1, checkpoints.size());
    }

    @Test
    void shouldFailClosedWhenRunDoesNotExist() {
        KernelAgentCheckpointQueryService service = new KernelAgentCheckpointQueryService(
                new MemoryAgentRunRepository(),
                new MemoryCheckpointRepository(),
                currentUser(1L, "user"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> service.listByRunId("run-1"));

        assertEquals("Agent run not found", error.getMessage());
    }

    private static AgentRun run(String userId) {
        return new AgentRun(
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                userId,
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "input",
                AgentRunStatus.WAITING_APPROVAL,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                NOW,
                null);
    }

    private static AgentCheckpoint checkpoint(String pendingToolCallJson) {
        return new AgentCheckpoint(
                "checkpoint-1",
                "run-1",
                "step-1",
                1L,
                AgentCheckpointType.WAITING_APPROVAL,
                "{\"state\":\"waiting\"}",
                null,
                null,
                pendingToolCallJson,
                NOW);
    }

    private static CurrentUserPort currentUser(Long userId, String role) {
        return () -> Optional.of(new CurrentUser(userId, role + "-" + userId, role, null));
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

        @Override
        public AgentRunPage page(AgentRunQuery query) {
            return new AgentRunPage(List.of(), 0L, query.size(), query.current(), 0L);
        }
    }

    private static final class MemoryCheckpointRepository implements AgentCheckpointRepositoryPort {

        private final List<AgentCheckpoint> checkpoints = new ArrayList<>();

        @Override
        public void save(AgentCheckpoint checkpoint) {
            checkpoints.add(checkpoint);
        }

        @Override
        public Optional<AgentCheckpoint> findLatestByRunId(String runId) {
            return checkpoints.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .max(Comparator.comparingLong(AgentCheckpoint::sequenceNo));
        }

        @Override
        public List<AgentCheckpoint> listByRunId(String runId) {
            return checkpoints.stream()
                    .filter(checkpoint -> runId.equals(checkpoint.runId()))
                    .sorted(Comparator.comparingLong(AgentCheckpoint::sequenceNo))
                    .toList();
        }
    }
}

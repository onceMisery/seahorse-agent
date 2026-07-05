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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class KernelAgentCheckpointQueryServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-06T00:00:00Z");

    @Test
    void shouldMinimizePendingToolResourceRefsForQueryResults() {
        MemoryCheckpointRepository checkpointRepository = new MemoryCheckpointRepository();
        checkpointRepository.save(checkpoint(
                "{\"toolId\":\"memory-forget\",\"resourceRefs\":{\"knowledgeBaseId\":\"kb-secret-ref\","
                        + "\"secretResourceKey\":\"resource-secret-value\"}}"));
        KernelAgentCheckpointQueryService service = new KernelAgentCheckpointQueryService(
                checkpointRepository,
                currentUser());

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
        MemoryCheckpointRepository checkpointRepository = new MemoryCheckpointRepository();
        checkpointRepository.save(checkpoint("[\"resourceRefs\",{\"knowledgeBaseId\":\"kb-secret-ref\"}]"));
        KernelAgentCheckpointQueryService service = new KernelAgentCheckpointQueryService(
                checkpointRepository,
                currentUser());

        AgentCheckpoint checkpoint = service.listByRunId("run-1").get(0);

        assertEquals(null, checkpoint.pendingToolCallJson());
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

    private static CurrentUserPort currentUser() {
        return () -> Optional.of(new CurrentUser(1L, "user-1", "user", null));
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

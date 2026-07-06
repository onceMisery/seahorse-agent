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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkflow;
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
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelAgentRunWorkflowServiceTests {

    private static final Instant NOW = Instant.parse("2026-07-06T00:00:00Z");

    @Test
    void shouldAllowNumericWebUserIdOwnerToReadWorkflow() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("42"));
        runRepository.appendStep(step("step-1", 1, AgentStepStatus.SUCCEEDED));
        runRepository.appendStep(step("step-2", 2, AgentStepStatus.RUNNING));
        KernelAgentRunWorkflowService service = new KernelAgentRunWorkflowService(
                runRepository,
                currentUser(42L, "owner"));

        AgentRunWorkflow workflow = service.getWorkflow("run-1");

        assertEquals("run-1", workflow.runId());
        assertEquals("step-2", workflow.currentStepId());
        assertEquals(List.of("step-1", "step-2"), workflow.nodes().stream().map(node -> node.id()).toList());
        assertEquals(1, workflow.edges().size());
    }

    @Test
    void shouldRejectUnrelatedUserWorkflow() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("42"));
        KernelAgentRunWorkflowService service = new KernelAgentRunWorkflowService(
                runRepository,
                currentUser(7L, "user"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getWorkflow("run-1"));

        assertEquals("Access denied", error.getMessage());
    }

    private static AgentRun run(String userId) {
        return new AgentRun(
                "run-1",
                "agent-1",
                "version-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                userId,
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "input",
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

    private static AgentStep step(String stepId, int stepNo, AgentStepStatus status) {
        return new AgentStep(
                stepId,
                "run-1",
                stepNo,
                AgentStepType.MODEL_TURN,
                status,
                "{\"input\":\"safe\"}",
                "{\"output\":\"ok\"}",
                null,
                null,
                NOW.plusSeconds(stepNo),
                status == AgentStepStatus.RUNNING ? null : NOW.plusSeconds(stepNo + 1L));
    }

    private static CurrentUserPort currentUser(Long userId, String role) {
        return () -> Optional.of(new CurrentUser(userId, role + "-" + userId, role, null));
    }

    private static final class MemoryAgentRunRepository implements AgentRunRepositoryPort {

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
                    .sorted(Comparator.comparingInt(AgentStep::stepNo))
                    .toList();
        }
    }
}

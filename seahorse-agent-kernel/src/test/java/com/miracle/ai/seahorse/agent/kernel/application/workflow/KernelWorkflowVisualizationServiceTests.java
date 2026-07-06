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

package com.miracle.ai.seahorse.agent.kernel.application.workflow;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.workflow.ExecutionStepAggregate;
import com.miracle.ai.seahorse.agent.ports.inbound.workflow.WorkflowVisualizationInboundPort.WorkflowVisualization;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.workflow.WorkflowVisualizationRepositoryPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelWorkflowVisualizationServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldReturnVisualizationForRunOwner() {
        RecordingWorkflowRepository workflowRepository = new RecordingWorkflowRepository();
        workflowRepository.steps.add(step("step-2", NOW.plusSeconds(2)));
        workflowRepository.steps.add(step("step-1", NOW));
        KernelWorkflowVisualizationService service = new KernelWorkflowVisualizationService(
                workflowRepository,
                new MemoryRunRepository(run("run-1", "alice")),
                currentUser("alice", "user"));

        WorkflowVisualization visualization = service.getVisualization(" run-1 ");

        assertEquals(List.of("step-1", "step-2"),
                visualization.nodes().stream().map(ExecutionStepAggregate::stepId).toList());
        assertEquals(1, visualization.edges().size());
        assertEquals(List.of("run-1"), workflowRepository.requestedRunIds);
    }

    @Test
    void shouldReturnVisualizationForNumericWebUserIdOwner() {
        RecordingWorkflowRepository workflowRepository = new RecordingWorkflowRepository();
        workflowRepository.steps.add(step("step-1", NOW));
        KernelWorkflowVisualizationService service = new KernelWorkflowVisualizationService(
                workflowRepository,
                new MemoryRunRepository(run("run-1", "42")),
                currentUser(42L, "owner", "user"));

        WorkflowVisualization visualization = service.getVisualization("run-1");

        assertEquals(1, visualization.nodes().size());
        assertEquals(List.of("run-1"), workflowRepository.requestedRunIds);
    }

    @Test
    void shouldReturnVisualizationForAdmin() {
        RecordingWorkflowRepository workflowRepository = new RecordingWorkflowRepository();
        workflowRepository.steps.add(step("step-1", NOW));
        KernelWorkflowVisualizationService service = new KernelWorkflowVisualizationService(
                workflowRepository,
                new MemoryRunRepository(run("run-1", "alice")),
                currentUser("root", "admin"));

        WorkflowVisualization visualization = service.getVisualization("run-1");

        assertEquals(1, visualization.nodes().size());
    }

    @Test
    void shouldDenyUnrelatedUserBeforeLoadingWorkflowSteps() {
        RecordingWorkflowRepository workflowRepository = new RecordingWorkflowRepository();
        workflowRepository.steps.add(step("step-1", NOW));
        KernelWorkflowVisualizationService service = new KernelWorkflowVisualizationService(
                workflowRepository,
                new MemoryRunRepository(run("run-1", "alice")),
                currentUser("bob", "user"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getVisualization("run-1"));

        assertEquals("\u6743\u9650\u4e0d\u8db3", error.getMessage());
        assertEquals(List.of(), workflowRepository.requestedRunIds);
    }

    @Test
    void shouldPreserveLegacyUngatedConstructor() {
        RecordingWorkflowRepository workflowRepository = new RecordingWorkflowRepository();
        workflowRepository.steps.add(step("step-1", NOW));
        KernelWorkflowVisualizationService service = new KernelWorkflowVisualizationService(workflowRepository);

        WorkflowVisualization visualization = service.getVisualization("run-1");

        assertEquals(1, visualization.nodes().size());
    }

    @Test
    void shouldRedactCredentialTextFromLegacyVisualizationResultData() {
        RecordingWorkflowRepository workflowRepository = new RecordingWorkflowRepository();
        workflowRepository.steps.add(new ExecutionStepAggregate(
                "step-1",
                "run-1",
                ExecutionStepAggregate.STEP_TYPE_HTTP_REQUEST,
                ExecutionStepAggregate.STATUS_FAILED,
                NOW,
                NOW.plusSeconds(1),
                1000L,
                Map.of(
                        "summary", "failed Authorization: Bearer abcdefghijklmnop",
                        "accessToken", "token-secret-value",
                        "nested", Map.of("cookie", "plain-cookie-header", "note", "kept"),
                        "items", List.of("api_key=secret-api-key-value", Map.of("password", "hunter2"))),
                null,
                null));
        KernelWorkflowVisualizationService service = new KernelWorkflowVisualizationService(
                workflowRepository,
                new MemoryRunRepository(run("run-1", "alice")),
                currentUser("alice", "user"));

        WorkflowVisualization visualization = service.getVisualization("run-1");

        Map<String, Object> resultData = visualization.nodes().get(0).resultData();
        assertEquals("failed [REDACTED]", resultData.get("summary"));
        assertEquals("[REDACTED]", resultData.get("accessToken"));
        assertEquals(Map.of("cookie", "[REDACTED]", "note", "kept"), resultData.get("nested"));
        assertEquals(List.of("[REDACTED]", Map.of("password", "[REDACTED]")), resultData.get("items"));
        assertEquals("failed Authorization: Bearer abcdefghijklmnop",
                workflowRepository.steps.get(0).resultData().get("summary"));
    }

    private static ExecutionStepAggregate step(String stepId, Instant startedAt) {
        return new ExecutionStepAggregate(
                stepId,
                "run-1",
                ExecutionStepAggregate.STEP_TYPE_RETRIEVAL,
                ExecutionStepAggregate.STATUS_SUCCESS,
                startedAt,
                startedAt.plusSeconds(1),
                1000L,
                Map.of("summary", stepId),
                null,
                null);
    }

    private static AgentRun run(String runId, String userId) {
        return new AgentRun(
                runId,
                "agent-1",
                "version-1",
                "tenant-1",
                userId,
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "input",
                AgentRunStatus.SUCCEEDED,
                "trace-1",
                0L,
                0L,
                BigDecimal.ZERO,
                null,
                null,
                NOW,
                NOW.plusSeconds(1));
    }

    private static CurrentUserPort currentUser(String operator, String role) {
        return () -> Optional.of(new CurrentUser(1L, operator, role, null));
    }

    private static CurrentUserPort currentUser(Long userId, String operator, String role) {
        return () -> Optional.of(new CurrentUser(userId, operator, role, null));
    }

    private static final class RecordingWorkflowRepository implements WorkflowVisualizationRepositoryPort {
        private final List<ExecutionStepAggregate> steps = new ArrayList<>();
        private final List<String> requestedRunIds = new ArrayList<>();

        @Override
        public List<ExecutionStepAggregate> findByRunId(String runId) {
            requestedRunIds.add(runId);
            return steps;
        }

        @Override
        public void saveStep(ExecutionStepAggregate step) {
            steps.add(step);
        }

        @Override
        public void updateStepStatus(String stepId, String status, Instant completedAt, Long durationMs) {
        }
    }

    private static final class MemoryRunRepository implements AgentRunRepositoryPort {
        private final AgentRun run;

        private MemoryRunRepository(AgentRun run) {
            this.run = run;
        }

        @Override
        public void createRun(AgentRun run) {
        }

        @Override
        public void updateRun(AgentRun run) {
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            if (run == null || !run.runId().equals(runId)) {
                return Optional.empty();
            }
            return Optional.of(run);
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

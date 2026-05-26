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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpointType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.tool.ToolRiskLevel;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunSnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ContextPackRepositoryPort;
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

class KernelAgentRunSnapshotServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-25T00:00:00Z");

    @Test
    void shouldBuildOwnerVisibleSnapshotFromExistingRunState() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("user-1", AgentRunStatus.WAITING_APPROVAL));
        runRepository.appendStep(step("step-1", 1, AgentStepType.MODEL_TURN, AgentStepStatus.SUCCEEDED));
        runRepository.appendStep(step("step-2", 2, AgentStepType.TOOL_CALL, AgentStepStatus.RUNNING));
        MemoryCheckpointRepository checkpointRepository = new MemoryCheckpointRepository();
        checkpointRepository.save(checkpoint("checkpoint-1", 1L, null));
        checkpointRepository.save(checkpoint("checkpoint-2", 2L, "context-pack-1"));
        MemoryContextPackRepository contextRepository = new MemoryContextPackRepository(contextPack("user-1"));
        MemoryApprovalQueryPort approvalQueryPort = new MemoryApprovalQueryPort(List.of(
                approval("approval-1", "user-1", ApprovalRequestStatus.PENDING),
                approval("approval-2", "user-1", ApprovalRequestStatus.APPROVED)));
        MemoryArtifactRepository artifactRepository = new MemoryArtifactRepository(List.of(
                artifact("artifact-1", "user-1", AgentArtifactScanStatus.CLEAN),
                artifact("artifact-2", "user-2", AgentArtifactScanStatus.CLEAN),
                artifact("artifact-3", "user-1", AgentArtifactScanStatus.BLOCKED)));
        KernelAgentRunSnapshotService service = new KernelAgentRunSnapshotService(
                runRepository,
                checkpointRepository,
                contextRepository,
                approvalQueryPort,
                artifactRepository,
                currentUser("user-1", "user"));

        AgentRunSnapshot snapshot = service.getSnapshot("run-1");

        assertEquals("run-1", snapshot.run().runId());
        assertEquals(AgentRunStatus.WAITING_APPROVAL, snapshot.run().status());
        assertEquals(2L, snapshot.lastEventSeq());
        assertEquals("checkpoint-2", snapshot.latestCheckpoint().orElseThrow().checkpointId());
        assertEquals("partial", snapshot.messageSnapshot().content());
        assertEquals("thinking", snapshot.messageSnapshot().thinking());
        assertEquals(List.of("step-1", "step-2"), snapshot.steps().stream().map(item -> item.stepId()).toList());
        assertEquals("step-2", snapshot.currentStepId());
        assertEquals(List.of("approval-1"),
                snapshot.pendingApprovals().stream().map(ApprovalRequest::approvalId).toList());
        assertEquals(List.of("item-1"), snapshot.sources().stream().map(item -> item.itemId()).toList());
        assertEquals("context-pack-1", snapshot.sources().get(0).contextPackId());
        assertEquals(ContextItemSourceType.RAG_CHUNK, snapshot.sources().get(0).sourceType());
        assertEquals("doc-1", snapshot.sources().get(0).sourceId());
        assertEquals(List.of("artifact-1", "artifact-3"),
                snapshot.artifacts().stream().map(AgentArtifact::artifactId).toList());
    }

    @Test
    void shouldAllowAdminToReadAnotherUsersSnapshot() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("user-1", AgentRunStatus.RUNNING));
        KernelAgentRunSnapshotService service = new KernelAgentRunSnapshotService(
                runRepository,
                new MemoryCheckpointRepository(),
                new MemoryContextPackRepository(null),
                new MemoryApprovalQueryPort(List.of(approval("approval-1", "user-1", ApprovalRequestStatus.PENDING))),
                new MemoryArtifactRepository(List.of(artifact("artifact-1", "user-1", AgentArtifactScanStatus.CLEAN))),
                currentUser("admin-1", "admin"));

        AgentRunSnapshot snapshot = service.getSnapshot("run-1");

        assertEquals("user-1", snapshot.run().userId());
        assertEquals(List.of("approval-1"),
                snapshot.pendingApprovals().stream().map(ApprovalRequest::approvalId).toList());
        assertEquals(List.of("artifact-1"),
                snapshot.artifacts().stream().map(AgentArtifact::artifactId).toList());
    }

    @Test
    void shouldDenyUnrelatedUserSnapshotAccess() {
        MemoryAgentRunRepository runRepository = new MemoryAgentRunRepository();
        runRepository.createRun(run("user-1", AgentRunStatus.RUNNING));
        KernelAgentRunSnapshotService service = new KernelAgentRunSnapshotService(
                runRepository,
                new MemoryCheckpointRepository(),
                new MemoryContextPackRepository(null),
                new MemoryApprovalQueryPort(List.of()),
                new MemoryArtifactRepository(List.of()),
                currentUser("user-2", "user"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getSnapshot("run-1"));

        assertEquals("权限不足", error.getMessage());
    }

    @Test
    void shouldReturnEmptyWhenSnapshotRunDoesNotExist() {
        KernelAgentRunSnapshotService service = new KernelAgentRunSnapshotService(
                new MemoryAgentRunRepository(),
                new MemoryCheckpointRepository(),
                new MemoryContextPackRepository(null),
                new MemoryApprovalQueryPort(List.of()),
                new MemoryArtifactRepository(List.of()),
                currentUser("user-1", "user"));

        assertFalse(service.findSnapshot("missing").isPresent());
    }

    private static AgentRun run(String userId, AgentRunStatus status) {
        return new AgentRun(
                "run-1",
                "agent-1",
                "version-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                userId,
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "summarized input",
                status,
                "trace-1",
                12L,
                24L,
                BigDecimal.ZERO,
                null,
                null,
                NOW,
                status.isFinished() ? NOW.plusSeconds(10) : null);
    }

    private static AgentStep step(String stepId, int stepNo, AgentStepType type, AgentStepStatus status) {
        return new AgentStep(
                stepId,
                "run-1",
                stepNo,
                type,
                status,
                "{\"input\":\"safe\"}",
                "{\"summary\":\"ok\"}",
                null,
                null,
                NOW.plusSeconds(stepNo),
                status == AgentStepStatus.RUNNING ? null : NOW.plusSeconds(stepNo + 1L));
    }

    private static AgentCheckpoint checkpoint(String checkpointId, long sequenceNo, String contextPackId) {
        return new AgentCheckpoint(
                checkpointId,
                "run-1",
                "step-2",
                sequenceNo,
                AgentCheckpointType.WAITING_APPROVAL,
                "{\"state\":\"waiting\"}",
                "[{\"role\":\"assistant\",\"content\":\"partial\",\"thinkingContent\":\"thinking\"}]",
                contextPackId,
                "{\"toolId\":\"memory-forget\"}",
                NOW.plusSeconds(sequenceNo));
    }

    private static ContextPack contextPack(String userId) {
        return new ContextPack(
                "context-pack-1",
                "run-1",
                "agent-1",
                "version-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                userId,
                "answer the question",
                300,
                List.of(contextItem()),
                NOW);
    }

    private static ContextItem contextItem() {
        return new ContextItem(
                "item-1",
                "context-pack-1",
                ContextItemSourceType.RAG_CHUNK,
                "doc-1",
                "source content",
                "source summary",
                0.91D,
                0.82D,
                ContextSensitivity.INTERNAL,
                "decision-1",
                "{\"title\":\"Doc One\",\"url\":\"https://example.com/doc\"}",
                20,
                null,
                NOW);
    }

    private static ApprovalRequest approval(String approvalId, String userId, ApprovalRequestStatus status) {
        return new ApprovalRequest(
                approvalId,
                "run-1",
                "step-2",
                "invocation-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                userId,
                "agent-1",
                "memory-forget",
                ApprovalType.TOOL_EXECUTION,
                ToolRiskLevel.HIGH,
                "Tool memory-forget requires approval",
                "{\"argumentKeys\":[\"input\"]}",
                status,
                NOW,
                null,
                status == ApprovalRequestStatus.PENDING ? null : "admin-1",
                status == ApprovalRequestStatus.PENDING ? null : NOW.plusSeconds(10),
                null);
    }

    private static AgentArtifact artifact(String artifactId, String userId, AgentArtifactScanStatus scanStatus) {
        return new AgentArtifact(
                artifactId,
                "run-1",
                "message-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                userId,
                AgentArtifactType.REPORT,
                "Research report",
                "text/markdown",
                "s3://agent-artifacts/" + artifactId,
                "preview",
                "{}",
                scanStatus,
                NOW);
    }

    private static CurrentUserPort currentUser(String userId, String role) {
        return () -> Optional.of(new CurrentUser(userId, userId, role, null));
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

    private static final class MemoryContextPackRepository implements ContextPackRepositoryPort {

        private final ContextPack pack;

        private MemoryContextPackRepository(ContextPack pack) {
            this.pack = pack;
        }

        @Override
        public void save(ContextPack pack) {
        }

        @Override
        public Optional<ContextPack> findById(String contextPackId) {
            if (pack == null || !pack.contextPackId().equals(contextPackId)) {
                return Optional.empty();
            }
            return Optional.of(pack);
        }

        @Override
        public List<ContextItem> listItems(String contextPackId) {
            if (pack == null || !pack.contextPackId().equals(contextPackId)) {
                return List.of();
            }
            return pack.items();
        }
    }

    private static final class MemoryApprovalQueryPort implements ApprovalRequestQueryPort {

        private final List<ApprovalRequest> approvals;

        private MemoryApprovalQueryPort(List<ApprovalRequest> approvals) {
            this.approvals = approvals;
        }

        @Override
        public Optional<ApprovalRequest> findById(String approvalId) {
            return approvals.stream()
                    .filter(approval -> approval.approvalId().equals(approvalId))
                    .findFirst();
        }

        @Override
        public ApprovalRequestPage page(ApprovalRequestQuery query) {
            List<ApprovalRequest> records = approvals.stream()
                    .filter(approval -> query.runId() == null || query.runId().equals(approval.runId()))
                    .filter(approval -> query.status() == null || query.status() == approval.status())
                    .toList();
            return new ApprovalRequestPage(records, records.size(), query.size(), query.current(), 1L);
        }
    }

    private static final class MemoryArtifactRepository implements AgentArtifactRepositoryPort {

        private final List<AgentArtifact> artifacts;

        private MemoryArtifactRepository(List<AgentArtifact> artifacts) {
            this.artifacts = artifacts;
        }

        @Override
        public AgentArtifact save(AgentArtifact artifact) {
            return artifact;
        }

        @Override
        public Optional<AgentArtifact> findById(String artifactId) {
            return artifacts.stream()
                    .filter(artifact -> artifact.artifactId().equals(artifactId))
                    .findFirst();
        }

        @Override
        public List<AgentArtifact> listByRunId(String runId) {
            return artifacts.stream()
                    .filter(artifact -> runId.equals(artifact.runId()))
                    .toList();
        }
    }
}

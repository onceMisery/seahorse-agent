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

package com.miracle.ai.seahorse.agent.kernel.application.agent.artifact;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactDisposition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifactType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentArtifactRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class KernelAgentArtifactQueryServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldListAndReadArtifactsForOwner() {
        MemoryRunRepository runRepository = new MemoryRunRepository(run("user-1"));
        MemoryArtifactRepository artifactRepository = new MemoryArtifactRepository(List.of(
                artifact("artifact-1", "user-1", AgentArtifactType.MARKDOWN, "text/markdown",
                        AgentArtifactScanStatus.CLEAN),
                artifact("artifact-2", "user-1", AgentArtifactType.HTML, "text/html",
                        AgentArtifactScanStatus.CLEAN)));
        KernelAgentArtifactQueryService service = new KernelAgentArtifactQueryService(
                artifactRepository,
                runRepository,
                currentUser(1L, "user"));

        List<AgentArtifact> artifacts = service.listByRunId("run-1");
        AgentArtifactDownloadDecision decision = service.downloadDecision("artifact-2");

        assertEquals(List.of("artifact-1", "artifact-2"),
                artifacts.stream().map(AgentArtifact::artifactId).toList());
        assertEquals(AgentArtifactDisposition.ATTACHMENT_DOWNLOAD, decision.disposition());
        assertEquals("text/html", decision.contentType());
        assertEquals("artifact-2.html", decision.filename());
    }

    @Test
    void shouldDenyUnrelatedUserArtifactAccess() {
        KernelAgentArtifactQueryService service = new KernelAgentArtifactQueryService(
                new MemoryArtifactRepository(List.of(artifact("artifact-1", "user-1", AgentArtifactType.REPORT,
                        "text/markdown", AgentArtifactScanStatus.CLEAN))),
                new MemoryRunRepository(run("user-1")),
                currentUser(2L, "user"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.getById("artifact-1"));

        assertEquals("鏉冮檺涓嶈冻", error.getMessage());
    }

    @Test
    void shouldBlockDownloadWhenScanIsNotClean() {
        KernelAgentArtifactQueryService service = new KernelAgentArtifactQueryService(
                new MemoryArtifactRepository(List.of(artifact("artifact-1", "user-1", AgentArtifactType.REPORT,
                        "text/markdown", AgentArtifactScanStatus.BLOCKED))),
                new MemoryRunRepository(run("user-1")),
                currentUser(1L, "user"));

        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> service.downloadDecision("artifact-1"));

        assertEquals("Artifact is not available for download", error.getMessage());
    }

    @Test
    void shouldReturnEmptyWhenArtifactDoesNotExist() {
        KernelAgentArtifactQueryService service = new KernelAgentArtifactQueryService(
                new MemoryArtifactRepository(List.of()),
                new MemoryRunRepository(run("user-1")),
                currentUser(1L, "user"));

        assertFalse(service.findById("missing").isPresent());
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
                AgentRunStatus.SUCCEEDED,
                "trace-1",
                10L,
                20L,
                BigDecimal.ZERO,
                null,
                null,
                NOW,
                NOW.plusSeconds(2));
    }

    private static AgentArtifact artifact(String artifactId,
                                          String userId,
                                          AgentArtifactType type,
                                          String mimeType,
                                          AgentArtifactScanStatus scanStatus) {
        return new AgentArtifact(
                artifactId,
                "run-1",
                "message-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                userId,
                type,
                "Research report",
                mimeType,
                "s3://agent-artifacts/" + artifactId,
                "preview",
                "{}",
                scanStatus,
                NOW);
    }

    private static CurrentUserPort currentUser(Long userId, String role) {
        return () -> Optional.of(new CurrentUser(userId, String.valueOf(userId), role, null));
    }

    private static final class MemoryArtifactRepository implements AgentArtifactRepositoryPort {

        private final Map<String, AgentArtifact> artifacts = new LinkedHashMap<>();

        private MemoryArtifactRepository(List<AgentArtifact> artifacts) {
            artifacts.forEach(artifact -> this.artifacts.put(artifact.artifactId(), artifact));
        }

        @Override
        public AgentArtifact save(AgentArtifact artifact) {
            artifacts.put(artifact.artifactId(), artifact);
            return artifact;
        }

        @Override
        public Optional<AgentArtifact> findById(String artifactId) {
            return Optional.ofNullable(artifacts.get(artifactId));
        }

        @Override
        public List<AgentArtifact> listByRunId(String runId) {
            return artifacts.values().stream()
                    .filter(artifact -> runId.equals(artifact.runId()))
                    .toList();
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

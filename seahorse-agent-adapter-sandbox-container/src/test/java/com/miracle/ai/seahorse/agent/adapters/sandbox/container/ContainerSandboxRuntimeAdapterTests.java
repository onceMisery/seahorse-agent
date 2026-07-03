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

package com.miracle.ai.seahorse.agent.adapters.sandbox.container;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ContainerSandboxRuntimeAdapterTests {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-07-02T00:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path tempDir;

    @Test
    void shouldRunCodeInterpreterThroughDockerWithNetworkDeniedWorkspaceMount() throws Exception {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                "hello from sandbox\n",
                Duration.ofMillis(250)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));
        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('hello from sandbox')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().reasonCode()).isEqualTo(SandboxPolicyReasonCode.VALID_REQUEST);
        assertThat(result.execution().resultSummary()).contains("hello from sandbox");
        assertThat(result.artifacts()).isEmpty();
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("docker", "run", "--rm")
                .containsSubsequence("--network", "none")
                .containsSubsequence("--memory", "128m")
                .containsSubsequence("--cpus", "0.5")
                .containsSubsequence("--pids-limit", "64")
                .containsSubsequence("python:3.11-alpine", "python", "/workspace/main.py");
        assertThat(runner.lastCommand.commandLine())
                .anySatisfy(argument -> assertThat(argument).endsWith(":/workspace:rw"));
        assertThat(Files.readString(runner.lastCommand.workingDirectory().resolve("main.py")))
                .isEqualTo("print('hello from sandbox')");
    }

    @Test
    void shouldCollectArtifactsCreatedBySuccessfulExecution() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("created artifacts\n", Duration.ofMillis(180)),
                command -> {
                    Files.writeString(command.workingDirectory().resolve("answer.txt"), "artifact text");
                    Path nested = command.workingDirectory().resolve("reports");
                    Files.createDirectories(nested);
                    Files.writeString(nested.resolve("summary.md"), "# Sandbox artifact");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('created artifacts')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.artifacts()).hasSize(2);
        assertThat(result.artifacts())
                .allSatisfy(artifact -> {
                    assertThat(artifact.artifactId()).startsWith("sandbox_artifact_container_");
                    assertThat(artifact.sessionId()).isEqualTo(session.sessionId());
                    assertThat(artifact.executionId()).isEqualTo(result.execution().executionId());
                    assertThat(artifact.objectUri()).startsWith("file:");
                    assertThat(artifact.scanStatus()).isEqualTo(SandboxArtifactScanStatus.PENDING);
                    assertThat(artifact.sensitivity()).isEqualTo(ContextSensitivity.INTERNAL);
                    assertThat(artifact.createdAt()).isEqualTo(CLOCK.instant());
                });
        assertThat(result.artifacts())
                .anySatisfy(artifact -> {
                    assertThat(artifact.objectUri()).contains("answer.txt");
                    assertThat(artifact.mediaType()).isEqualTo("text/plain");
                })
                .anySatisfy(artifact -> {
                    assertThat(artifact.objectUri()).contains("summary.md");
                    assertThat(artifact.mediaType()).isEqualTo("text/markdown");
                })
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"));
    }

    @Test
    void shouldRunFileConversionWithGeneratedConverterAndCollectOnlyOutputArtifact() throws Exception {
        RecordingRunner runner = new RecordingRunner(
                ContainerCommandResult.succeeded("converted 1 rows from csv to json\n", Duration.ofMillis(210)),
                command -> {
                    assertThat(Files.readString(command.workingDirectory().resolve("main.py")))
                            .contains("csv.DictReader", "converted.json")
                            .doesNotContain("Ada,42");
                    assertThat(Files.readString(command.workingDirectory().resolve("input.csv")))
                            .isEqualTo("name,score\nAda,42\n");
                    Files.writeString(command.workingDirectory().resolve("converted.json"),
                            "[{\"name\":\"Ada\",\"score\":\"42\"}]");
                });
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.FILE_CONVERSION));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                """
                        {"sourceFormat":"csv","targetFormat":"json","content":"name,score\\nAda,42\\n"}
                        """,
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(result.execution().reasonCode()).isEqualTo(SandboxPolicyReasonCode.VALID_REQUEST);
        assertThat(result.execution().resultSummary()).contains("converted 1 rows");
        assertThat(result.artifacts()).hasSize(1);
        assertThat(result.artifacts().getFirst().objectUri()).contains("converted.json");
        assertThat(result.artifacts().getFirst().mediaType()).isEqualTo("application/json");
        assertThat(result.artifacts())
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("main.py"))
                .noneSatisfy(artifact -> assertThat(artifact.objectUri()).contains("input.csv"));
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("docker", "run", "--rm")
                .containsSubsequence("--network", "none")
                .containsSubsequence("python:3.11-alpine", "python", "/workspace/main.py");
    }

    @Test
    void shouldFailClosedWhenContainerRunnerThrows() {
        RecordingRunner runner = new RecordingRunner(new IOException("docker missing"));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('nope')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED);
        assertThat(result.execution().resultSummary()).contains("docker missing");
    }

    @Test
    void shouldUseConfiguredMountSourceRootWhileWritingToWorkspaceRoot() throws Exception {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                "mount source smoke\n",
                Duration.ofMillis(100)));
        ContainerSandboxAdapterProperties properties = properties();
        Path containerWorkspaceRoot = tempDir.resolve("container-workspaces");
        Path hostMountSourceRoot = tempDir.resolve("host-workspaces");
        properties.setWorkspaceRoot(containerWorkspaceRoot.toString());
        properties.setWorkspaceMountSourceRoot(hostMountSourceRoot.toString());
        ContainerSandboxRuntimeAdapter adapter = new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);

        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));
        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "print('mount source smoke')",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.SUCCEEDED);
        assertThat(runner.lastCommand.workingDirectory())
                .isEqualTo(containerWorkspaceRoot.resolve(session.sessionId()).toAbsolutePath().normalize());
        assertThat(Files.readString(runner.lastCommand.workingDirectory().resolve("main.py")))
                .isEqualTo("print('mount source smoke')");
        assertThat(runner.lastCommand.commandLine())
                .contains(hostMountSourceRoot + "/" + session.sessionId() + ":/workspace:rw");
    }

    @Test
    void shouldMarkExecutionTimedOut() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.timedOut(
                "",
                "still running",
                Duration.ofSeconds(30)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "while True: pass",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.TIMED_OUT);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_TIMED_OUT);
        assertThat(result.execution().resultSummary()).contains("timed out", "still running");
    }

    @Test
    void shouldReturnUnsupportedForNonCodeInterpreterRuntime() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.SHELL));

        SandboxExecutionResult result = adapter.execute(new SandboxExecutionRequest(
                session,
                "echo unsafe",
                false,
                List.of()));

        assertThat(result.execution().status()).isEqualTo(SandboxExecutionStatus.FAILED);
        assertThat(result.reasonCode()).isEqualTo(SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED);
        assertThat(runner.lastCommand).isNull();
    }

    @Test
    void shouldDeleteSessionWorkspaceOnClose() throws Exception {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        SandboxSession session = adapter.createSession(sessionRequest(SandboxRuntimeType.CODE_INTERPRETER));
        Path workspace = tempDir.resolve(session.sessionId());
        assertThat(workspace).exists();

        SandboxSession closed = adapter.closeSession(session);

        assertThat(closed.status()).isEqualTo(SandboxExecutionStatus.CANCELLED);
        assertThat(workspace).doesNotExist();
    }

    @Test
    void shouldSweepOnlyOldOrphanedManagedWorkspaces() throws Exception {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded("", Duration.ZERO));
        ContainerSandboxAdapterProperties properties = properties();
        properties.setOrphanWorkspaceMinAge(Duration.ofMinutes(10));
        ContainerSandboxRuntimeAdapter adapter = new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);
        Path active = createWorkspace("sandbox_container_active", CLOCK.instant().minusSeconds(3600));
        Path orphan = createWorkspace("sandbox_container_orphan", CLOCK.instant().minusSeconds(3600));
        Path recent = createWorkspace("sandbox_container_recent", CLOCK.instant());
        Path unmanaged = createWorkspace("not_sandbox_container_orphan", CLOCK.instant().minusSeconds(3600));

        SandboxRuntimeCleanupResult result = adapter.sweepOrphanedResources(Set.of("sandbox_container_active"));

        assertThat(result.activeSessionCount()).isEqualTo(1);
        assertThat(result.inspectedWorkspaceCount()).isEqualTo(3);
        assertThat(result.skippedActiveWorkspaceCount()).isEqualTo(1);
        assertThat(result.skippedRecentWorkspaceCount()).isEqualTo(1);
        assertThat(result.removedWorkspaceCount()).isEqualTo(1);
        assertThat(result.failedWorkspaceCount()).isZero();
        assertThat(result.removedWorkspaceNames()).containsExactly("sandbox_container_orphan");
        assertThat(active).exists();
        assertThat(orphan).doesNotExist();
        assertThat(recent).exists();
        assertThat(unmanaged).exists();
    }

    @Test
    void shouldInspectManagedContainersAndClassifyOrphans() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                """
                        seahorse-sandbox-sandbox_container_active\tUp 10 seconds
                        seahorse-sandbox-orphan-live\tUp 2 minutes
                        unrelated-container\tUp 1 hour
                        """,
                Duration.ofMillis(80)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxRuntimeCleanupResult result = adapter.sweepOrphanedResources(Set.of("sandbox_container_active"));

        assertThat(result.inspectedContainerCount()).isEqualTo(2);
        assertThat(result.activeContainerCount()).isEqualTo(1);
        assertThat(result.orphanContainerCount()).isEqualTo(1);
        assertThat(result.failedContainerInspectionCount()).isZero();
        assertThat(result.activeContainerNames())
                .containsExactly("seahorse-sandbox-sandbox_container_active");
        assertThat(result.orphanContainerNames())
                .containsExactly("seahorse-sandbox-orphan-live");
        assertThat(runner.lastCommand.commandLine())
                .containsSubsequence("docker", "ps", "-a")
                .containsSubsequence("--filter", "name=seahorse-sandbox-")
                .containsSubsequence("--format", "{{.Names}}\t{{.Status}}");
    }

    @Test
    void shouldKeepWorkspaceSweepResultWhenContainerInspectionFails() throws Exception {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.failed(
                125,
                "",
                "Cannot connect to Docker daemon",
                Duration.ofMillis(50)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);
        Path orphan = createWorkspace("sandbox_container_orphan", CLOCK.instant().minusSeconds(3600));

        SandboxRuntimeCleanupResult result = adapter.sweepOrphanedResources(Set.of());

        assertThat(result.removedWorkspaceCount()).isEqualTo(1);
        assertThat(result.failedWorkspaceCount()).isZero();
        assertThat(result.failedContainerInspectionCount()).isEqualTo(1);
        assertThat(result.failedContainerInspectionMessages())
                .singleElement()
                .satisfies(message -> assertThat(message).contains("exitCode=125", "Cannot connect"));
        assertThat(orphan).doesNotExist();
    }

    @Test
    void shouldInspectRuntimeHealthFromWorkspaceAndManagedContainers() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                """
                        seahorse-sandbox-sandbox_container_active\tUp 10 seconds
                        seahorse-sandbox-orphan-live\tUp 2 minutes
                        """,
                Duration.ofMillis(80)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxRuntimeHealth health = adapter.inspectHealth(Set.of("sandbox_container_active"));

        assertThat(health.runtime()).isEqualTo("container");
        assertThat(health.engine()).isEqualTo("docker");
        assertThat(health.status()).isEqualTo(SandboxRuntimeHealth.STATUS_DEGRADED);
        assertThat(health.engineAvailable()).isTrue();
        assertThat(health.workspaceAvailable()).isTrue();
        assertThat(health.activeSessionCount()).isEqualTo(1);
        assertThat(health.activeSessionLimit()).isZero();
        assertThat(health.activeSessionRemaining()).isZero();
        assertThat(health.activeSessionCapacityAvailable()).isTrue();
        assertThat(health.capacityStatus()).isEqualTo(SandboxRuntimeHealth.CAPACITY_UNBOUNDED);
        assertThat(health.inspectedContainerCount()).isEqualTo(2);
        assertThat(health.activeContainerCount()).isEqualTo(1);
        assertThat(health.orphanContainerCount()).isEqualTo(1);
        assertThat(health.activeContainerNames())
                .containsExactly("seahorse-sandbox-sandbox_container_active");
        assertThat(health.orphanContainerNames())
                .containsExactly("seahorse-sandbox-orphan-live");
        assertThat(health.failureMessages()).isEmpty();
    }

    @Test
    void shouldReportRuntimeHealthUnavailableWhenContainerInspectionFails() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.failed(
                125,
                "",
                "Cannot connect to Docker daemon",
                Duration.ofMillis(50)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxRuntimeHealth health = adapter.inspectHealth(Set.of());

        assertThat(health.status()).isEqualTo(SandboxRuntimeHealth.STATUS_UNAVAILABLE);
        assertThat(health.engineAvailable()).isFalse();
        assertThat(health.workspaceAvailable()).isTrue();
        assertThat(health.failedContainerInspectionCount()).isEqualTo(1);
        assertThat(health.failureMessages())
                .singleElement()
                .satisfies(message -> assertThat(message).contains("exitCode=125", "Cannot connect"));
    }

    @Test
    void shouldReportRuntimeHealthCapacitySaturation() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                "seahorse-sandbox-sandbox_container_active\tUp 10 seconds\n",
                Duration.ofMillis(80)));
        ContainerSandboxAdapterProperties properties = properties();
        properties.setMaxActiveSessions(1);
        ContainerSandboxRuntimeAdapter adapter = new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);

        SandboxRuntimeHealth health = adapter.inspectHealth(Set.of("sandbox_container_active"));

        assertThat(health.status()).isEqualTo(SandboxRuntimeHealth.STATUS_DEGRADED);
        assertThat(health.activeSessionCount()).isEqualTo(1);
        assertThat(health.activeSessionLimit()).isEqualTo(1);
        assertThat(health.activeSessionRemaining()).isZero();
        assertThat(health.activeSessionCapacityAvailable()).isFalse();
        assertThat(health.capacityStatus()).isEqualTo(SandboxRuntimeHealth.CAPACITY_SATURATED);
        assertThat(health.failureMessages()).isEmpty();
    }

    @Test
    void shouldDryRunOrphanContainerReapWithoutRemovingContainers() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                """
                        seahorse-sandbox-sandbox_container_active\tUp 10 seconds
                        seahorse-sandbox-orphan-live\tUp 2 minutes
                        """,
                Duration.ofMillis(80)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxRuntimeContainerReapResult result = adapter.reapOrphanedContainers(
                Set.of("sandbox_container_active"),
                true);

        assertThat(result.dryRun()).isTrue();
        assertThat(result.activeSessionCount()).isEqualTo(1);
        assertThat(result.inspectedContainerCount()).isEqualTo(2);
        assertThat(result.activeContainerNames())
                .containsExactly("seahorse-sandbox-sandbox_container_active");
        assertThat(result.orphanContainerNames())
                .containsExactly("seahorse-sandbox-orphan-live");
        assertThat(result.reapedContainerCount()).isZero();
        assertThat(result.failedContainerCount()).isZero();
        assertThat(runner.commands).hasSize(1);
        assertThat(runner.commands.getFirst().commandLine())
                .containsSubsequence("docker", "ps", "-a");
    }

    @Test
    void shouldReapOnlyOrphanManagedContainers() {
        RecordingRunner runner = new RecordingRunner(ContainerCommandResult.succeeded(
                """
                        seahorse-sandbox-sandbox_container_active\tUp 10 seconds
                        seahorse-sandbox-orphan-live\tUp 2 minutes
                        """,
                Duration.ofMillis(80)));
        ContainerSandboxRuntimeAdapter adapter = adapter(runner);

        SandboxRuntimeContainerReapResult result = adapter.reapOrphanedContainers(
                Set.of("sandbox_container_active"),
                false);

        assertThat(result.dryRun()).isFalse();
        assertThat(result.reapedContainerCount()).isEqualTo(1);
        assertThat(result.failedContainerCount()).isZero();
        assertThat(result.reapedContainerNames())
                .containsExactly("seahorse-sandbox-orphan-live");
        assertThat(result.activeContainerNames())
                .containsExactly("seahorse-sandbox-sandbox_container_active");
        assertThat(runner.commands).hasSize(2);
        assertThat(runner.commands.get(1).commandLine())
                .containsExactly("docker", "rm", "-f", "seahorse-sandbox-orphan-live");
    }

    private Path createWorkspace(String name, Instant modifiedAt) throws IOException {
        Path workspace = tempDir.resolve(name);
        Files.createDirectories(workspace);
        Files.writeString(workspace.resolve("marker.txt"), name);
        FileTime fileTime = FileTime.from(modifiedAt);
        Files.setLastModifiedTime(workspace.resolve("marker.txt"), fileTime);
        Files.setLastModifiedTime(workspace, fileTime);
        return workspace;
    }

    private ContainerSandboxRuntimeAdapter adapter(RecordingRunner runner) {
        return new ContainerSandboxRuntimeAdapter(properties(), runner, CLOCK);
    }

    private ContainerSandboxAdapterProperties properties() {
        ContainerSandboxAdapterProperties properties = new ContainerSandboxAdapterProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        properties.setMemory("128m");
        properties.setCpus("0.5");
        properties.setPidsLimit(64L);
        properties.setExecutionTimeout(Duration.ofSeconds(3));
        return properties;
    }

    private SandboxSessionRequest sessionRequest(SandboxRuntimeType runtimeType) {
        return new SandboxSessionRequest("default", "run-1", runtimeType, false, List.of());
    }

    private static final class RecordingRunner implements ContainerCommandRunner {

        private final ContainerCommandResult result;
        private final IOException exception;
        private final OnRun onRun;
        private final List<ContainerCommand> commands = new java.util.ArrayList<>();
        private ContainerCommand lastCommand;

        private RecordingRunner(ContainerCommandResult result) {
            this.result = result;
            this.exception = null;
            this.onRun = null;
        }

        private RecordingRunner(ContainerCommandResult result, OnRun onRun) {
            this.result = result;
            this.exception = null;
            this.onRun = onRun;
        }

        private RecordingRunner(IOException exception) {
            this.result = null;
            this.exception = exception;
            this.onRun = null;
        }

        @Override
        public ContainerCommandResult run(ContainerCommand command) throws IOException {
            lastCommand = command;
            commands.add(command);
            if (exception != null) {
                throw exception;
            }
            if (onRun != null) {
                onRun.accept(command);
            }
            return result;
        }
    }

    private interface OnRun {

        void accept(ContainerCommand command) throws IOException;
    }
}

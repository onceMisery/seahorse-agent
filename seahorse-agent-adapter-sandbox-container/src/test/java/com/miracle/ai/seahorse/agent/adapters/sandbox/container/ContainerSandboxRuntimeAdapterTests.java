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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

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

    private ContainerSandboxRuntimeAdapter adapter(RecordingRunner runner) {
        ContainerSandboxAdapterProperties properties = new ContainerSandboxAdapterProperties();
        properties.setWorkspaceRoot(tempDir.toString());
        properties.setMemory("128m");
        properties.setCpus("0.5");
        properties.setPidsLimit(64L);
        properties.setExecutionTimeout(Duration.ofSeconds(3));
        return new ContainerSandboxRuntimeAdapter(properties, runner, CLOCK);
    }

    private SandboxSessionRequest sessionRequest(SandboxRuntimeType runtimeType) {
        return new SandboxSessionRequest("default", "run-1", runtimeType, false, List.of());
    }

    private static final class RecordingRunner implements ContainerCommandRunner {

        private final ContainerCommandResult result;
        private final IOException exception;
        private ContainerCommand lastCommand;

        private RecordingRunner(ContainerCommandResult result) {
            this.result = result;
            this.exception = null;
        }

        private RecordingRunner(IOException exception) {
            this.result = null;
            this.exception = exception;
        }

        @Override
        public ContainerCommandResult run(ContainerCommand command) throws IOException {
            lastCommand = command;
            if (exception != null) {
                throw exception;
            }
            return result;
        }
    }
}

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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

public class ContainerSandboxRuntimeAdapter implements SandboxRuntimePort {

    private static final String SESSION_ID_PREFIX = "sandbox_container_";
    private static final String EXECUTION_ID_PREFIX = "sandbox_exec_container_";
    private static final String SCRIPT_NAME = "main.py";
    private static final String CONTAINER_WORKSPACE = "/workspace";

    private final ContainerSandboxAdapterProperties properties;
    private final ContainerCommandRunner commandRunner;
    private final Clock clock;
    private final Path workspaceRoot;

    public ContainerSandboxRuntimeAdapter(ContainerSandboxAdapterProperties properties,
                                          ContainerCommandRunner commandRunner,
                                          Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.workspaceRoot = resolveWorkspaceRoot(properties.getWorkspaceRoot());
    }

    @Override
    public SandboxSession createSession(SandboxSessionRequest request) {
        SandboxSessionRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Instant now = clock.instant();
        String sessionId = SESSION_ID_PREFIX + SnowflakeIds.nextIdString();
        try {
            Files.createDirectories(workspaceForSession(sessionId));
            return SandboxSession.created(
                    sessionId,
                    safeRequest.tenantId(),
                    safeRequest.runId(),
                    safeRequest.runtimeType(),
                    now);
        } catch (IOException ex) {
            return SandboxSession.failed(
                    sessionId,
                    safeRequest.tenantId(),
                    safeRequest.runId(),
                    safeRequest.runtimeType(),
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    now);
        }
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionRequest request) {
        SandboxExecutionRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        SandboxSession session = safeRequest.session();
        Instant startedAt = clock.instant();
        String executionId = EXECUTION_ID_PREFIX + SnowflakeIds.nextIdString();
        if (session.runtimeType() != SandboxRuntimeType.CODE_INTERPRETER) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED,
                    "container sandbox supports CODE_INTERPRETER only");
        }
        try {
            Path workspace = workspaceForSession(session.sessionId());
            Files.createDirectories(workspace);
            Files.writeString(workspace.resolve(SCRIPT_NAME), safeRequest.input(), StandardCharsets.UTF_8);
            ContainerCommandResult commandResult = commandRunner.run(containerCommand(session, workspace));
            Instant finishedAt = clock.instant();
            if (commandResult.timedOut()) {
                SandboxExecution execution = new SandboxExecution(
                        executionId,
                        session.sessionId(),
                        session.runtimeType(),
                        SandboxExecutionStatus.TIMED_OUT,
                        summary("timed out", commandResult),
                        SandboxPolicyReasonCode.RUNTIME_TIMED_OUT,
                        startedAt,
                        finishedAt);
                return SandboxExecutionResult.failed(execution, SandboxPolicyReasonCode.RUNTIME_TIMED_OUT);
            }
            if (commandResult.exitCode() == 0) {
                SandboxExecution execution = new SandboxExecution(
                        executionId,
                        session.sessionId(),
                        session.runtimeType(),
                        SandboxExecutionStatus.SUCCEEDED,
                        summary("exitCode=0", commandResult),
                        SandboxPolicyReasonCode.VALID_REQUEST,
                        startedAt,
                        finishedAt);
                return SandboxExecutionResult.succeeded(execution, List.of());
            }
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    summary("exitCode=" + commandResult.exitCode(), commandResult));
        } catch (IOException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime io failure: " + nullToEmpty(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime interrupted");
        } catch (RuntimeException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime failure: " + nullToEmpty(ex.getMessage()));
        }
    }

    @Override
    public SandboxSession closeSession(SandboxSession session) {
        SandboxSession safeSession = Objects.requireNonNull(session, "session must not be null");
        deleteWorkspace(safeSession.sessionId());
        return safeSession.closed(clock.instant());
    }

    private ContainerCommand containerCommand(SandboxSession session, Path workspace) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("run");
        commandLine.add("--rm");
        commandLine.add("--name");
        commandLine.add(containerName(session.sessionId()));
        commandLine.add("--network");
        commandLine.add("none");
        commandLine.add("--memory");
        commandLine.add(properties.getMemory());
        commandLine.add("--cpus");
        commandLine.add(properties.getCpus());
        commandLine.add("--pids-limit");
        commandLine.add(Long.toString(properties.getPidsLimit()));
        commandLine.add("-v");
        commandLine.add(workspace.toAbsolutePath().normalize() + ":" + CONTAINER_WORKSPACE + ":rw");
        commandLine.add("-w");
        commandLine.add(CONTAINER_WORKSPACE);
        commandLine.add(properties.getPythonImage());
        commandLine.add("python");
        commandLine.add(CONTAINER_WORKSPACE + "/" + SCRIPT_NAME);
        return new ContainerCommand(
                commandLine,
                workspace,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private SandboxExecutionResult failedResult(String executionId,
                                                SandboxSession session,
                                                Instant startedAt,
                                                SandboxPolicyReasonCode reasonCode,
                                                String summary) {
        Instant finishedAt = clock.instant();
        SandboxExecution execution = new SandboxExecution(
                executionId,
                session.sessionId(),
                session.runtimeType(),
                SandboxExecutionStatus.FAILED,
                summary,
                reasonCode,
                startedAt,
                finishedAt);
        return SandboxExecutionResult.failed(execution, reasonCode);
    }

    private String summary(String prefix, ContainerCommandResult result) {
        return "%s; durationMs=%d; stdout=%s; stderr=%s".formatted(
                prefix,
                Math.max(0L, result.duration().toMillis()),
                oneLinePreview(result.stdout()),
                oneLinePreview(result.stderr()));
    }

    private String oneLinePreview(String value) {
        String preview = nullToEmpty(value)
                .replace('\r', '\n')
                .lines()
                .limit(8)
                .reduce((left, right) -> left + "\\n" + right)
                .orElse("");
        if (preview.length() <= 512) {
            return preview;
        }
        return preview.substring(0, 512);
    }

    private Path workspaceForSession(String sessionId) {
        String safeName = safeFilesystemName(sessionId);
        Path workspace = workspaceRoot.resolve(safeName).toAbsolutePath().normalize();
        if (!workspace.startsWith(workspaceRoot) || workspace.equals(workspaceRoot)) {
            throw new IllegalArgumentException("invalid sandbox session workspace");
        }
        return workspace;
    }

    private void deleteWorkspace(String sessionId) {
        Path workspace = workspaceForSession(sessionId);
        if (!Files.exists(workspace)) {
            return;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                if (path.equals(workspaceRoot)) {
                    return;
                }
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup; session close still records a terminal state.
                }
            });
        } catch (IOException ignored) {
            // Best-effort cleanup; session close still records a terminal state.
        }
    }

    private String containerName(String sessionId) {
        String base = "seahorse-sandbox-" + safeFilesystemName(sessionId).toLowerCase(Locale.ROOT);
        if (base.length() <= 96) {
            return base;
        }
        return base.substring(0, 96);
    }

    private String safeFilesystemName(String value) {
        String safe = nullToEmpty(value).replaceAll("[^A-Za-z0-9_.-]", "_");
        if (safe.isBlank()) {
            throw new IllegalArgumentException("sandbox session id must not be blank");
        }
        return safe;
    }

    private Path resolveWorkspaceRoot(String configuredRoot) {
        try {
            Path root = hasText(configuredRoot)
                    ? Path.of(configuredRoot)
                    : Path.of(System.getProperty("java.io.tmpdir"), "seahorse-sandbox-container");
            Path normalized = root.toAbsolutePath().normalize();
            Files.createDirectories(normalized);
            return normalized;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create sandbox workspace root", ex);
        }
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public class ContainerSandboxRuntimeAdapter implements SandboxRuntimePort {

    private static final String SESSION_ID_PREFIX = "sandbox_container_";
    private static final String EXECUTION_ID_PREFIX = "sandbox_exec_container_";
    private static final String ARTIFACT_ID_PREFIX = "sandbox_artifact_container_";
    private static final String SCRIPT_NAME = "main.py";
    private static final String CONTAINER_WORKSPACE = "/workspace";
    private static final String CONTAINER_NAME_PREFIX = "seahorse-sandbox-";

    private final ContainerSandboxAdapterProperties properties;
    private final ContainerCommandRunner commandRunner;
    private final Clock clock;
    private final Path workspaceRoot;
    private final String workspaceMountSourceRoot;

    public ContainerSandboxRuntimeAdapter(ContainerSandboxAdapterProperties properties,
                                          ContainerCommandRunner commandRunner,
                                          Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.workspaceRoot = resolveWorkspaceRoot(properties.getWorkspaceRoot());
        this.workspaceMountSourceRoot = trimToNull(properties.getWorkspaceMountSourceRoot());
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
                    safeRequest.profileId(),
                    safeRequest.expiresAt(),
                    now);
        } catch (IOException ex) {
            return SandboxSession.failed(
                    sessionId,
                    safeRequest.tenantId(),
                    safeRequest.runId(),
                    safeRequest.runtimeType(),
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    safeRequest.profileId(),
                    safeRequest.expiresAt(),
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
                return SandboxExecutionResult.succeeded(
                        execution,
                        collectArtifacts(session, execution.executionId(), workspace, finishedAt));
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

    @Override
    public SandboxRuntimeCleanupResult sweepOrphanedResources(Set<String> activeSessionIds) {
        Set<String> safeActiveSessionIds = normalizeActiveSessionIds(activeSessionIds);
        Set<String> activeContainerNames = normalizeActiveContainerNames(safeActiveSessionIds);
        Instant sweptAt = clock.instant();
        Instant cutoff = sweptAt.minus(properties.getOrphanWorkspaceMinAge());
        int inspected = 0;
        int skippedActive = 0;
        int skippedRecent = 0;
        int removed = 0;
        int failed = 0;
        List<String> removedNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        try (var paths = Files.list(workspaceRoot)) {
            List<Path> candidates = paths
                    .filter(path -> Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(this::isManagedWorkspaceDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path candidate : candidates) {
                inspected++;
                String workspaceName = candidate.getFileName().toString();
                if (safeActiveSessionIds.contains(workspaceName)) {
                    skippedActive++;
                    continue;
                }
                if (isRecentWorkspace(candidate, cutoff)) {
                    skippedRecent++;
                    continue;
                }
                if (deleteWorkspacePath(candidate)) {
                    removed++;
                    removedNames.add(workspaceName);
                } else {
                    failed++;
                    failedNames.add(workspaceName);
                }
            }
        } catch (IOException ex) {
            ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
            return new SandboxRuntimeCleanupResult(
                    sweptAt,
                    safeActiveSessionIds.size(),
                    inspected,
                    skippedActive,
                    skippedRecent,
                    removed,
                    failed + 1,
                    removedNames,
                    failedNames,
                    containerSummary.inspectedCount(),
                    containerSummary.activeCount(),
                    containerSummary.orphanCount(),
                    containerSummary.failedInspectionCount(),
                    containerSummary.activeNames(),
                    containerSummary.orphanNames(),
                    containerSummary.failureMessages());
        }
        ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
        return new SandboxRuntimeCleanupResult(
                sweptAt,
                safeActiveSessionIds.size(),
                inspected,
                skippedActive,
                skippedRecent,
                removed,
                failed,
                removedNames,
                failedNames,
                containerSummary.inspectedCount(),
                containerSummary.activeCount(),
                containerSummary.orphanCount(),
                containerSummary.failedInspectionCount(),
                containerSummary.activeNames(),
                containerSummary.orphanNames(),
                containerSummary.failureMessages());
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
        commandLine.add(mountSourceForSession(session.sessionId(), workspace) + ":" + CONTAINER_WORKSPACE + ":rw");
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

    private ContainerCommand containerInspectionCommand() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("ps");
        commandLine.add("-a");
        commandLine.add("--filter");
        commandLine.add("name=" + CONTAINER_NAME_PREFIX);
        commandLine.add("--format");
        commandLine.add("{{.Names}}\t{{.Status}}");
        return new ContainerCommand(
                commandLine,
                workspaceRoot,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private ContainerInspectionSummary inspectManagedContainers(Set<String> activeContainerNames) {
        Set<String> safeActiveContainerNames = activeContainerNames == null
                ? Set.of()
                : Set.copyOf(activeContainerNames);
        try {
            ContainerCommandResult result = commandRunner.run(containerInspectionCommand());
            if (result.timedOut()) {
                return ContainerInspectionSummary.failed("container inspection timed out");
            }
            if (result.exitCode() != 0) {
                return ContainerInspectionSummary.failed(
                        "container inspection exitCode=" + result.exitCode() + "; stderr="
                                + oneLinePreview(result.stderr()));
            }
            List<String> activeNames = new ArrayList<>();
            List<String> orphanNames = new ArrayList<>();
            int inspectedCount = 0;
            for (String line : result.stdout().lines().toList()) {
                String name = containerNameFromPsLine(line);
                if (!hasText(name) || !isManagedContainerName(name)) {
                    continue;
                }
                inspectedCount++;
                if (safeActiveContainerNames.contains(name)) {
                    activeNames.add(name);
                } else {
                    orphanNames.add(name);
                }
            }
            activeNames.sort(String::compareTo);
            orphanNames.sort(String::compareTo);
            return new ContainerInspectionSummary(
                    inspectedCount,
                    activeNames.size(),
                    orphanNames.size(),
                    0,
                    activeNames,
                    orphanNames,
                    List.of());
        } catch (IOException ex) {
            return ContainerInspectionSummary.failed(
                    "container inspection io failure: " + nullToEmpty(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ContainerInspectionSummary.failed("container inspection interrupted");
        } catch (RuntimeException ex) {
            return ContainerInspectionSummary.failed(
                    "container inspection failure: " + nullToEmpty(ex.getMessage()));
        }
    }

    private List<SandboxArtifact> collectArtifacts(SandboxSession session,
                                                   String executionId,
                                                   Path workspace,
                                                   Instant createdAt) throws IOException {
        if (!Files.exists(workspace)) {
            return List.of();
        }
        Path safeWorkspace = workspace.toAbsolutePath().normalize();
        try (var paths = Files.walk(safeWorkspace)) {
            return paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> path.startsWith(safeWorkspace))
                    .filter(path -> !path.equals(safeWorkspace.resolve(SCRIPT_NAME)))
                    .sorted(Comparator.comparing(path -> safeWorkspace.relativize(path).toString()))
                    .map(path -> artifact(session, executionId, path, createdAt))
                    .toList();
        }
    }

    private SandboxArtifact artifact(SandboxSession session, String executionId, Path path, Instant createdAt) {
        return new SandboxArtifact(
                ARTIFACT_ID_PREFIX + SnowflakeIds.nextIdString(),
                session.sessionId(),
                executionId,
                path.toUri().toString(),
                mediaType(path),
                SandboxArtifactScanStatus.PENDING,
                ContextSensitivity.INTERNAL,
                createdAt);
    }

    private String mediaType(Path path) {
        String known = knownMediaType(path);
        if (known != null) {
            return known;
        }
        try {
            String probed = Files.probeContentType(path);
            if (hasText(probed)) {
                return probed.trim();
            }
        } catch (IOException ignored) {
            // Fall through to a conservative binary type.
        }
        return "application/octet-stream";
    }

    private String knownMediaType(Path path) {
        String name = path.getFileName() == null
                ? ""
                : path.getFileName().toString().toLowerCase(Locale.ROOT);
        int dot = name.lastIndexOf('.');
        String extension = dot >= 0 ? name.substring(dot + 1) : "";
        return switch (extension) {
            case "txt", "log" -> "text/plain";
            case "md", "markdown" -> "text/markdown";
            case "csv" -> "text/csv";
            case "tsv" -> "text/tab-separated-values";
            case "html", "htm" -> "text/html";
            case "py" -> "text/x-python";
            case "yaml", "yml" -> "text/yaml";
            case "json" -> "application/json";
            case "xml" -> "application/xml";
            case "pdf" -> "application/pdf";
            case "gif" -> "image/gif";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            default -> null;
        };
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

    private String mountSourceForSession(String sessionId, Path workspace) {
        if (workspaceMountSourceRoot == null) {
            return workspace.toAbsolutePath().normalize().toString();
        }
        return stripTrailingSeparators(workspaceMountSourceRoot) + "/" + safeFilesystemName(sessionId);
    }

    private void deleteWorkspace(String sessionId) {
        Path workspace = workspaceForSession(sessionId);
        if (!Files.exists(workspace)) {
            return;
        }
        deleteWorkspacePath(workspace);
    }

    private boolean deleteWorkspacePath(Path workspace) {
        Path safeWorkspace = workspace.toAbsolutePath().normalize();
        if (!safeWorkspace.startsWith(workspaceRoot) || safeWorkspace.equals(workspaceRoot)) {
            return false;
        }
        try (var paths = Files.walk(workspace)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                Path safePath = path.toAbsolutePath().normalize();
                if (!safePath.startsWith(workspaceRoot) || safePath.equals(workspaceRoot)) {
                    return;
                }
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // Best-effort cleanup; session close still records a terminal state.
                }
            });
            return !Files.exists(safeWorkspace);
        } catch (IOException ignored) {
            return false;
        }
    }

    private boolean isManagedWorkspaceDirectory(Path path) {
        Path safePath = path.toAbsolutePath().normalize();
        if (!safePath.startsWith(workspaceRoot) || safePath.equals(workspaceRoot)) {
            return false;
        }
        Path filename = safePath.getFileName();
        return filename != null && filename.toString().startsWith(SESSION_ID_PREFIX);
    }

    private boolean isManagedContainerName(String value) {
        return hasText(value) && value.startsWith(CONTAINER_NAME_PREFIX);
    }

    private String containerNameFromPsLine(String line) {
        if (!hasText(line)) {
            return "";
        }
        String[] parts = line.split("\\t", 2);
        return parts[0].trim();
    }

    private boolean isRecentWorkspace(Path workspace, Instant cutoff) {
        try {
            FileTime modifiedTime = Files.getLastModifiedTime(workspace, LinkOption.NOFOLLOW_LINKS);
            return modifiedTime.toInstant().isAfter(cutoff);
        } catch (IOException ex) {
            return true;
        }
    }

    private Set<String> normalizeActiveSessionIds(Set<String> activeSessionIds) {
        if (activeSessionIds == null || activeSessionIds.isEmpty()) {
            return Set.of();
        }
        Set<String> safeNames = new HashSet<>();
        for (String sessionId : activeSessionIds) {
            if (hasText(sessionId)) {
                safeNames.add(safeFilesystemName(sessionId.trim()));
            }
        }
        return Set.copyOf(safeNames);
    }

    private Set<String> normalizeActiveContainerNames(Set<String> activeSessionIds) {
        if (activeSessionIds == null || activeSessionIds.isEmpty()) {
            return Set.of();
        }
        Set<String> safeNames = new HashSet<>();
        for (String sessionId : activeSessionIds) {
            if (hasText(sessionId)) {
                safeNames.add(containerName(sessionId));
            }
        }
        return Set.copyOf(safeNames);
    }

    private String containerName(String sessionId) {
        String base = CONTAINER_NAME_PREFIX + safeFilesystemName(sessionId).toLowerCase(Locale.ROOT);
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

    private static String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private static String stripTrailingSeparators(String value) {
        String result = Objects.requireNonNull(value, "value must not be null").trim();
        while (result.endsWith("/") || result.endsWith("\\")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("workspaceMountSourceRoot must not be empty");
        }
        return result;
    }

    private record ContainerInspectionSummary(int inspectedCount,
                                              int activeCount,
                                              int orphanCount,
                                              int failedInspectionCount,
                                              List<String> activeNames,
                                              List<String> orphanNames,
                                              List<String> failureMessages) {

        private ContainerInspectionSummary {
            activeNames = activeNames == null ? List.of() : List.copyOf(activeNames);
            orphanNames = orphanNames == null ? List.of() : List.copyOf(orphanNames);
            failureMessages = failureMessages == null ? List.of() : List.copyOf(failureMessages);
        }

        private static ContainerInspectionSummary failed(String message) {
            return new ContainerInspectionSummary(
                    0,
                    0,
                    0,
                    1,
                    List.of(),
                    List.of(),
                    List.of(nullToEmpty(message)));
        }
    }
}

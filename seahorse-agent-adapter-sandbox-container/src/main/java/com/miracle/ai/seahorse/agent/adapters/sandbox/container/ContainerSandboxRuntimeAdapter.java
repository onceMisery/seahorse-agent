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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeSessionOwnership;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;

import java.io.IOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
/**
 * 容器沙箱运行时适配器（SandboxRuntimePort 实现）。
 *
 * <p>按 §7 收敛原则，跨职责逻辑已外提为 package-private 协作者：
 * {@link ContainerWorkspaceManager}（工作区/容器名）、{@link ContainerFileConversionSupport}（文件转换）、
 * {@link ContainerBrowserAutomationSupport}（浏览器自动化）、{@link ContainerNetworkBoundarySupport}（网络边界）、
 * {@link ContainerArtifactCollector}（产物收集）。本类保留 public API、会话生命周期（A）、
 * 命令执行（I）与巡检/健康检查编排。
 */
public class ContainerSandboxRuntimeAdapter implements SandboxRuntimePort {

    private static final String LEGACY_SESSION_ID_PREFIX = "sandbox_container_";
    private static final String EXECUTION_ID_PREFIX = "sandbox_exec_container_";
    private static final String CONTAINER_WORKSPACE = "/workspace";
    private static final String SCRIPT_NAME = "main.py";

    private final ContainerSandboxAdapterProperties properties;
    private final ContainerCommandRunner commandRunner;
    private final Clock clock;
    private final Path workspaceRoot;
    private final ContainerNetworkBoundarySupport networkBoundarySupport;
    private final ContainerFileConversionSupport fileConversionSupport;
    private final ContainerBrowserAutomationSupport browserAutomationSupport;
    private final ContainerWorkspaceManager workspaceManager;
    private final ContainerArtifactCollector artifactCollector;

    public ContainerSandboxRuntimeAdapter(ContainerSandboxAdapterProperties properties,
                                          ContainerCommandRunner commandRunner,
                                          Clock clock) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.commandRunner = Objects.requireNonNull(commandRunner, "commandRunner must not be null");
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.workspaceRoot = resolveWorkspaceRoot(properties.getWorkspaceRoot());
        ContainerNetworkBoundarySupport networkBoundarySupport = new ContainerNetworkBoundarySupport();
        ObjectMapper objectMapper = new ObjectMapper();
        ContainerFileConversionSupport fileConversionSupport =
                new ContainerFileConversionSupport(properties, objectMapper);
        ContainerBrowserAutomationSupport browserAutomationSupport = new ContainerBrowserAutomationSupport(
                properties, objectMapper, new AtomicInteger(), networkBoundarySupport);
        this.networkBoundarySupport = networkBoundarySupport;
        this.fileConversionSupport = fileConversionSupport;
        this.browserAutomationSupport = browserAutomationSupport;
        this.workspaceManager = new ContainerWorkspaceManager(
                workspaceRoot,
                ContainerSandboxTextSupport.trimToNull(properties.getWorkspaceMountSourceRoot()),
                fileConversionSupport,
                browserAutomationSupport,
                networkBoundarySupport);
        this.artifactCollector = new ContainerArtifactCollector(properties, browserAutomationSupport);
    }

    public SandboxSession createSession(SandboxSessionRequest request) {
        SandboxSessionRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        Instant now = clock.instant();
        String sessionId = safeRequest.sessionId() == null
                ? LEGACY_SESSION_ID_PREFIX + SnowflakeIds.nextIdString()
                : safeRequest.sessionId();
        try {
            Files.createDirectories(workspaceManager.workspaceForSession(sessionId));
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

    public SandboxExecutionResult execute(SandboxExecutionRequest request) {
        SandboxExecutionRequest safeRequest = Objects.requireNonNull(request, "request must not be null");
        SandboxSession session = safeRequest.session();
        Instant startedAt = clock.instant();
        String executionId = EXECUTION_ID_PREFIX + SnowflakeIds.nextIdString();
        if (session.runtimeType() != SandboxRuntimeType.CODE_INTERPRETER
                && session.runtimeType() != SandboxRuntimeType.FILE_CONVERSION
                && session.runtimeType() != SandboxRuntimeType.BROWSER_AUTOMATION) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED,
                    "container sandbox supports CODE_INTERPRETER, FILE_CONVERSION, and BROWSER_AUTOMATION only");
        }
        try {
            Path workspace = workspaceManager.workspaceForSession(session.sessionId());
            Files.createDirectories(workspace);
            workspaceManager.prepareWorkspacePermissions(workspace);
            String executionImage = fileConversionSupport.imageForExecution(session.runtimeType(), safeRequest.input());
            networkBoundarySupport.validateContainerNetworkBoundary(session.runtimeType(), safeRequest.networkRequested());
            Set<Path> excludedArtifacts = workspaceManager.prepareWorkspace(
                    session.runtimeType(),
                    safeRequest.input(),
                    workspace,
                    safeRequest.networkRequested(),
                    safeRequest.requestedHosts(),
                    safeRequest.browserPrivateNetworkAllowedHosts());
            ContainerCommandResult commandResult = commandRunner.run(
                    containerCommand(session, workspace, safeRequest.networkRequested(), safeRequest.requestedHosts(), executionImage));
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
                        artifactCollector.collectArtifacts(session, execution.executionId(), workspace, finishedAt, excludedArtifacts));
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
                    "container runtime io failure: " + ContainerSandboxTextSupport.nullToEmpty(ex.getMessage()));
        } catch (UnsupportedFileConversionException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED,
                    ex.getMessage());
        } catch (UnsupportedBrowserAutomationException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED,
                    ex.getMessage());
        } catch (IllegalArgumentException ex) {
            return failedResult(
                    executionId,
                    session,
                    startedAt,
                    SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                    "container runtime invalid request: " + ContainerSandboxTextSupport.nullToEmpty(ex.getMessage()));
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
                    "container runtime failure: " + ContainerSandboxTextSupport.nullToEmpty(ex.getMessage()));
        }
    }

    public SandboxSession closeSession(SandboxSession session) {
        SandboxSession safeSession = Objects.requireNonNull(session, "session must not be null");
        workspaceManager.deleteWorkspace(safeSession.sessionId());
        return safeSession.closed(clock.instant());
    }

    public SandboxRuntimeSessionOwnership inspectSessionOwnership(String sessionId) {
        Path workspace = workspaceManager.workspaceForSession(sessionId);
        return Files.isDirectory(workspace, LinkOption.NOFOLLOW_LINKS)
                ? SandboxRuntimeSessionOwnership.OWNED
                : SandboxRuntimeSessionOwnership.ABSENT;
    }

    public SandboxRuntimeCleanupResult sweepOrphanedResources(Set<String> activeSessionIds) {
        Set<String> safeActiveSessionIds = workspaceManager.normalizeActiveSessionIds(activeSessionIds);
        Set<String> activeContainerNames = workspaceManager.normalizeActiveContainerNames(safeActiveSessionIds);
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
                    .filter(workspaceManager::isManagedWorkspaceDirectory)
                    .sorted(Comparator.comparing(path -> path.getFileName().toString()))
                    .toList();
            for (Path candidate : candidates) {
                inspected++;
                String workspaceName = candidate.getFileName().toString();
                if (safeActiveSessionIds.contains(workspaceName)) {
                    skippedActive++;
                    continue;
                }
                if (workspaceManager.isRecentWorkspace(candidate, cutoff)) {
                    skippedRecent++;
                    continue;
                }
                if (workspaceManager.deleteWorkspacePath(candidate)) {
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

    public SandboxRuntimeHealth inspectHealth(Set<String> activeSessionIds) {
        Set<String> safeActiveSessionIds = workspaceManager.normalizeActiveSessionIds(activeSessionIds);
        Set<String> activeContainerNames = workspaceManager.normalizeActiveContainerNames(safeActiveSessionIds);
        ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
        OciRuntimeAvailability ociRuntimeAvailability = inspectConfiguredOciRuntime();
        boolean workspaceAvailable = Files.isDirectory(workspaceRoot, LinkOption.NOFOLLOW_LINKS)
                && Files.isWritable(workspaceRoot);
        List<String> failureMessages = new ArrayList<>(containerSummary.failureMessages());
        failureMessages.addAll(ociRuntimeAvailability.failureMessages());
        if (!workspaceAvailable) {
            failureMessages.add("sandbox workspace root is not available");
        }
        WorkspaceDiskSummary diskSummary = workspaceDiskSummary(workspaceAvailable);
        failureMessages.addAll(diskSummary.failureMessages());
        boolean engineAvailable = containerSummary.failedInspectionCount() == 0;
        int ownedActiveSessionCount = workspaceManager.ownedActiveSessionCount(safeActiveSessionIds);
        CapacitySummary capacitySummary = capacitySummary(ownedActiveSessionCount);
        return new SandboxRuntimeHealth(
                clock.instant(),
                "container",
                properties.getEngine(),
                properties.getNodeId(),
                properties.isAdmissionEnabled(),
                healthStatus(
                        engineAvailable,
                        ociRuntimeAvailability.available(),
                        workspaceAvailable,
                        diskSummary.available(),
                        capacitySummary.available(),
                        containerSummary.orphanCount(),
                        failureMessages),
                engineAvailable,
                workspaceAvailable,
                diskSummary.freeBytes(),
                diskSummary.minFreeBytes(),
                diskSummary.available(),
                diskSummary.status(),
                ownedActiveSessionCount,
                capacitySummary.limit(),
                capacitySummary.remaining(),
                capacitySummary.available(),
                capacitySummary.status(),
                containerSummary.inspectedCount(),
                containerSummary.activeCount(),
                containerSummary.orphanCount(),
                containerSummary.failedInspectionCount(),
                containerSummary.activeNames(),
                containerSummary.orphanNames(),
                browserAutomationSupport.normalizedBrowserPrivateNetworkAllowedHosts(),
                properties.isDropAllCapabilities(),
                properties.isNoNewPrivileges(),
                properties.isReadOnlyRootFilesystem(),
                properties.getMaxSessionFileBytes(),
                ContainerArtifactCollector.MAX_SESSION_WORKSPACE_FILES,
                failureMessages,
                properties.getOciRuntime(),
                ociRuntimeAvailability.available());
    }

    public SandboxRuntimeContainerReapResult reapOrphanedContainers(Set<String> activeSessionIds, boolean dryRun) {
        Set<String> safeActiveSessionIds = workspaceManager.normalizeActiveSessionIds(activeSessionIds);
        Set<String> activeContainerNames = workspaceManager.normalizeActiveContainerNames(safeActiveSessionIds);
        Instant reapedAt = clock.instant();
        ContainerInspectionSummary containerSummary = inspectManagedContainers(activeContainerNames);
        List<String> reapedNames = new ArrayList<>();
        List<String> failedNames = new ArrayList<>();
        List<String> failureMessages = new ArrayList<>(containerSummary.failureMessages());
        if (containerSummary.failedInspectionCount() == 0 && !dryRun) {
            for (String containerName : containerSummary.orphanNames()) {
                if (removeManagedContainer(containerName)) {
                    reapedNames.add(containerName);
                } else {
                    failedNames.add(containerName);
                    failureMessages.add("failed to remove sandbox container " + containerName);
                }
            }
        }
        return new SandboxRuntimeContainerReapResult(
                reapedAt,
                dryRun,
                safeActiveSessionIds.size(),
                containerSummary.inspectedCount(),
                containerSummary.activeCount(),
                containerSummary.orphanCount(),
                containerSummary.failedInspectionCount(),
                reapedNames.size(),
                failedNames.size(),
                containerSummary.activeNames(),
                containerSummary.orphanNames(),
                reapedNames,
                failedNames,
                failureMessages);
    }

    private ContainerCommand containerCommand(SandboxSession session,
                                              Path workspace,
                                              boolean networkRequested,
                                              List<String> requestedHosts,
                                              String executionImage) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("run");
        if (!properties.getOciRuntime().isBlank()) {
            commandLine.add("--runtime");
            commandLine.add(properties.getOciRuntime());
        }
        commandLine.add("--rm");
        commandLine.add("--name");
        commandLine.add(workspaceManager.containerName(session.sessionId()));
        if (networkRequested) {
            for (String host : dockerInternalHosts(requestedHosts)) {
                commandLine.add("--add-host");
                commandLine.add(host + ":host-gateway");
            }
        } else {
            commandLine.add("--network");
            commandLine.add("none");
        }
        commandLine.add("--memory");
        commandLine.add(fileConversionSupport.memoryForRuntime(session.runtimeType()));
        commandLine.add("--cpus");
        commandLine.add(properties.getCpus());
        commandLine.add("--pids-limit");
        commandLine.add(Long.toString(properties.getPidsLimit()));
        if (properties.isDropAllCapabilities()) {
            commandLine.add("--cap-drop");
            commandLine.add("ALL");
        }
        if (properties.isNoNewPrivileges()) {
            commandLine.add("--security-opt");
            commandLine.add("no-new-privileges:true");
        }
        if (properties.isReadOnlyRootFilesystem()) {
            commandLine.add("--read-only");
            commandLine.add("--tmpfs");
            commandLine.add("/tmp:rw,noexec,nosuid,size=64m");
        }
        commandLine.add("--ulimit");
        commandLine.add("fsize=" + fileConversionSupport.maxSessionFileLimit() + ":" + fileConversionSupport.maxSessionFileLimit());
        commandLine.add("--user");
        commandLine.add(properties.getRunAsUser());
        commandLine.add("-e");
        commandLine.add("HOME=/tmp");
        commandLine.add("-e");
        commandLine.add("XDG_CACHE_HOME=/tmp/.cache");
        commandLine.add("-v");
        commandLine.add(workspaceManager.mountSourceForSession(session.sessionId(), workspace) + ":" + CONTAINER_WORKSPACE + ":rw");
        commandLine.add("-w");
        commandLine.add(CONTAINER_WORKSPACE);
        commandLine.add(executionImage);
        commandLine.add("python");
        commandLine.add(CONTAINER_WORKSPACE + "/" + SCRIPT_NAME);
        return new ContainerCommand(
                commandLine,
                workspace,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private List<String> dockerInternalHosts(List<String> requestedHosts) {
        LinkedHashSet<String> hosts = new LinkedHashSet<>();
        hosts.add("host.docker.internal");
        for (String browserProxyHost : browserAutomationSupport.browserProxyHosts()) {
            if (browserProxyHost.endsWith(".docker.internal")) {
                hosts.add(browserProxyHost);
            }
        }
        if (requestedHosts != null) {
            for (String requestedHost : requestedHosts) {
                String host = ContainerSandboxTextSupport.nullToEmpty(requestedHost).trim().toLowerCase(Locale.ROOT);
                if (host.endsWith(".docker.internal")) {
                    hosts.add(host);
                }
            }
        }
        return List.copyOf(hosts);
    }

    private ContainerCommand containerInspectionCommand() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("ps");
        commandLine.add("-a");
        commandLine.add("--filter");
        commandLine.add("name=" + ContainerWorkspaceManager.CONTAINER_NAME_PREFIX);
        commandLine.add("--format");
        commandLine.add("{{.Names}}\t{{.Status}}");
        return new ContainerCommand(
                commandLine,
                workspaceRoot,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private ContainerCommand ociRuntimeInspectionCommand() {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("info");
        commandLine.add("--format");
        commandLine.add("docker".equalsIgnoreCase(properties.getEngine())
                ? "{{range $name, $_ := .Runtimes}}{{println $name}}{{end}}"
                : "{{.Host.OCIRuntime.Name}}");
        return new ContainerCommand(
                commandLine,
                workspaceRoot,
                properties.getExecutionTimeout(),
                properties.getStdoutLimitBytes(),
                properties.getStderrLimitBytes());
    }

    private ContainerCommand containerRemoveCommand(String containerName) {
        List<String> commandLine = new ArrayList<>();
        commandLine.add(properties.getEngine());
        commandLine.add("rm");
        commandLine.add("-f");
        commandLine.add(containerName);
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
                String name = workspaceManager.containerNameFromPsLine(line);
                if (!ContainerSandboxTextSupport.hasText(name) || !workspaceManager.isManagedContainerName(name)) {
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
                    "container inspection io failure: " + ContainerSandboxTextSupport.nullToEmpty(ex.getMessage()));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return ContainerInspectionSummary.failed("container inspection interrupted");
        } catch (RuntimeException ex) {
            return ContainerInspectionSummary.failed(
                    "container inspection failure: " + ContainerSandboxTextSupport.nullToEmpty(ex.getMessage()));
        }
    }

    private OciRuntimeAvailability inspectConfiguredOciRuntime() {
        String configuredRuntime = properties.getOciRuntime();
        if (!ContainerSandboxTextSupport.hasText(configuredRuntime)) {
            return OciRuntimeAvailability.confirmed();
        }
        try {
            ContainerCommandResult result = commandRunner.run(ociRuntimeInspectionCommand());
            if (result.timedOut() || result.exitCode() != 0) {
                return OciRuntimeAvailability.unavailable("OCI runtime availability inspection failed");
            }
            boolean available = result.stdout().lines()
                    .map(String::trim)
                    .anyMatch(configuredRuntime::equals);
            return available
                    ? OciRuntimeAvailability.confirmed()
                    : OciRuntimeAvailability.unavailable("configured OCI runtime is not available");
        } catch (IOException ex) {
            return OciRuntimeAvailability.unavailable("OCI runtime availability inspection failed");
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return OciRuntimeAvailability.unavailable("OCI runtime availability inspection interrupted");
        } catch (RuntimeException ex) {
            return OciRuntimeAvailability.unavailable("OCI runtime availability inspection failed");
        }
    }

    private boolean removeManagedContainer(String containerName) {
        if (!workspaceManager.isManagedContainerName(containerName)) {
            return false;
        }
        try {
            ContainerCommandResult result = commandRunner.run(containerRemoveCommand(containerName));
            return !result.timedOut() && result.exitCode() == 0;
        } catch (IOException ex) {
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private CapacitySummary capacitySummary(int activeSessionCount) {
        int limit = Math.max(properties.getMaxActiveSessions(), 0);
        if (limit == 0) {
            return new CapacitySummary(0, 0, true, SandboxRuntimeHealth.CAPACITY_UNBOUNDED);
        }
        int remaining = Math.max(limit - Math.max(activeSessionCount, 0), 0);
        if (remaining == 0) {
            return new CapacitySummary(limit, 0, false, SandboxRuntimeHealth.CAPACITY_SATURATED);
        }
        return new CapacitySummary(limit, remaining, true, SandboxRuntimeHealth.CAPACITY_AVAILABLE);
    }

    private WorkspaceDiskSummary workspaceDiskSummary(boolean workspaceAvailable) {
        long minFreeBytes = Math.max(properties.getMinWorkspaceFreeBytes(), 0L);
        if (!workspaceAvailable) {
            return new WorkspaceDiskSummary(
                    -1L,
                    minFreeBytes,
                    false,
                    SandboxRuntimeHealth.DISK_UNKNOWN,
                    List.of());
        }
        try {
            FileStore store = Files.getFileStore(workspaceRoot);
            long freeBytes = Math.max(store.getUsableSpace(), 0L);
            if (minFreeBytes == 0L) {
                return new WorkspaceDiskSummary(
                        freeBytes,
                        0L,
                        true,
                        SandboxRuntimeHealth.DISK_UNBOUNDED,
                        List.of());
            }
            if (freeBytes >= minFreeBytes) {
                return new WorkspaceDiskSummary(
                        freeBytes,
                        minFreeBytes,
                        true,
                        SandboxRuntimeHealth.DISK_AVAILABLE,
                        List.of());
            }
            return new WorkspaceDiskSummary(
                    freeBytes,
                    minFreeBytes,
                    false,
                    SandboxRuntimeHealth.DISK_LOW,
                    List.of());
        } catch (IOException | RuntimeException ex) {
            if (minFreeBytes == 0L) {
                return new WorkspaceDiskSummary(
                        -1L,
                        0L,
                        true,
                        SandboxRuntimeHealth.DISK_UNBOUNDED,
                        List.of());
            }
            return new WorkspaceDiskSummary(
                    -1L,
                    minFreeBytes,
                    false,
                    SandboxRuntimeHealth.DISK_UNKNOWN,
                    List.of("workspace disk inspection failed"));
        }
    }

    private String healthStatus(boolean engineAvailable,
                                boolean ociRuntimeAvailable,
                                boolean workspaceAvailable,
                                boolean workspaceDiskAvailable,
                                boolean capacityAvailable,
                                int orphanContainerCount,
                                List<String> failureMessages) {
        if (!engineAvailable || !ociRuntimeAvailable || !workspaceAvailable) {
            return SandboxRuntimeHealth.STATUS_UNAVAILABLE;
        }
        if (!workspaceDiskAvailable || !capacityAvailable || orphanContainerCount > 0 || !failureMessages.isEmpty()) {
            return SandboxRuntimeHealth.STATUS_DEGRADED;
        }
        return SandboxRuntimeHealth.STATUS_HEALTHY;
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
        String preview = ContainerSandboxTextSupport.nullToEmpty(value)
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

    private Path resolveWorkspaceRoot(String configuredRoot) {
        try {
            Path root = ContainerSandboxTextSupport.hasText(configuredRoot)
                    ? Path.of(configuredRoot)
                    : Path.of(System.getProperty("java.io.tmpdir"), "seahorse-sandbox-container");
            Path normalized = root.toAbsolutePath().normalize();
            Files.createDirectories(normalized);
            return normalized;
        } catch (IOException ex) {
            throw new IllegalStateException("Cannot create sandbox workspace root", ex);
        }
    }
}

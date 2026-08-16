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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Instant;
import java.util.Base64;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

/**
 * 沙箱工作区生命周期与容器名/安全路径工具协作者（从 {@link ContainerSandboxRuntimeAdapter} 提取）。
 * 按 §7 收敛原则外提：沙箱工作区生命周期与容器名/安全路径工具协作者。
 */
final class ContainerWorkspaceManager {

    static final String COORDINATOR_SESSION_ID_PREFIX = "sandbox_";
    static final String CONTAINER_NAME_PREFIX = "seahorse-sandbox-";
    static final String SCRIPT_NAME = "main.py";

    private final Path workspaceRoot;
    private final String workspaceMountSourceRoot;
    private final ContainerFileConversionSupport fileConversionSupport;
    private final ContainerBrowserAutomationSupport browserAutomationSupport;
    private final ContainerNetworkBoundarySupport networkBoundarySupport;

    ContainerWorkspaceManager(Path workspaceRoot,
                              String workspaceMountSourceRoot,
                              ContainerFileConversionSupport fileConversionSupport,
                              ContainerBrowserAutomationSupport browserAutomationSupport,
                              ContainerNetworkBoundarySupport networkBoundarySupport) {
        this.workspaceRoot = Objects.requireNonNull(workspaceRoot, "workspaceRoot must not be null");
        this.workspaceMountSourceRoot = workspaceMountSourceRoot;
        this.fileConversionSupport = Objects.requireNonNull(fileConversionSupport,
                "fileConversionSupport must not be null");
        this.browserAutomationSupport = Objects.requireNonNull(browserAutomationSupport,
                "browserAutomationSupport must not be null");
        this.networkBoundarySupport = Objects.requireNonNull(networkBoundarySupport,
                "networkBoundarySupport must not be null");
    }

    Set<Path> prepareWorkspace(SandboxRuntimeType runtimeType,
                                String input,
                                Path workspace,
                                boolean networkRequested,
                                List<String> requestedHosts,
                                List<String> browserPrivateNetworkAllowedHosts) throws IOException {
        Path safeWorkspace = workspace.toAbsolutePath().normalize();
        if (runtimeType == SandboxRuntimeType.CODE_INTERPRETER) {
            Files.writeString(safeWorkspace.resolve(SCRIPT_NAME), input, StandardCharsets.UTF_8);
            return Set.of(safeWorkspace.resolve(SCRIPT_NAME));
        }
        if (runtimeType == SandboxRuntimeType.FILE_CONVERSION) {
            FileConversionRequest request = fileConversionSupport.parseFileConversionRequest(input);
            byte[] binaryContent = null;
            if (ContainerSandboxTextSupport.BASE64_ENCODING.equals(request.contentEncoding())) {
                binaryContent = decodeBase64Content(request.content());
                fileConversionSupport.validateBinaryFileConversionInput(request.sourceFormat(), binaryContent);
            }
            Path inputPath = safeWorkspace.resolve(fileConversionSupport.fileConversionInputName(request.sourceFormat()));
            Files.writeString(safeWorkspace.resolve(SCRIPT_NAME),
                    fileConversionSupport.fileConversionScript(request), StandardCharsets.UTF_8);
            if (binaryContent != null) {
                Files.write(inputPath, binaryContent);
            } else {
                Files.writeString(inputPath, request.content(), StandardCharsets.UTF_8);
            }
            return Set.of(safeWorkspace.resolve(SCRIPT_NAME), inputPath);
        }
        if (runtimeType == SandboxRuntimeType.BROWSER_AUTOMATION) {
            BrowserAutomationRequest request = browserAutomationSupport.parseBrowserAutomationRequest(
                    input, browserPrivateNetworkAllowedHosts);
            networkBoundarySupport.validateBrowserNetworkBoundary(request, networkRequested, requestedHosts);
            Path inputPath = safeWorkspace.resolve(browserAutomationSupport.browserInputName());
            Path cookiesPath = safeWorkspace.resolve(browserAutomationSupport.browserCookiesName());
            Path sessionStateInputPath = safeWorkspace.resolve(browserAutomationSupport.browserSessionStateInputName());
            Files.writeString(safeWorkspace.resolve(SCRIPT_NAME),
                    browserAutomationSupport.browserAutomationScript(request), StandardCharsets.UTF_8);
            LinkedHashSet<Path> excluded = new LinkedHashSet<>();
            excluded.add(safeWorkspace.resolve(SCRIPT_NAME));
            if (!request.cookies().isEmpty()) {
                Files.writeString(cookiesPath, browserAutomationSupport.jsonForScript(request.cookies()),
                        StandardCharsets.UTF_8);
                excluded.add(cookiesPath);
            }
            if (ContainerSandboxTextSupport.hasText(request.sessionStateJson())) {
                Files.writeString(sessionStateInputPath, request.sessionStateJson(), StandardCharsets.UTF_8);
                excluded.add(sessionStateInputPath);
            }
            if (!ContainerSandboxTextSupport.hasText(request.url())) {
                Files.writeString(inputPath, request.html(), StandardCharsets.UTF_8);
                excluded.add(inputPath);
                return Set.copyOf(excluded);
            }
            return Set.copyOf(excluded);
        }
        throw new IllegalArgumentException("unsupported sandbox runtime type: " + runtimeType);
    }
    static void prepareWorkspacePermissions(Path workspace) throws IOException {
        try {
            Files.setPosixFilePermissions(workspace, EnumSet.allOf(PosixFilePermission.class));
            return;
        } catch (UnsupportedOperationException ignored) {
            // Windows filesystems do not expose POSIX permissions through NIO.
        }
        java.io.File directory = workspace.toFile();
        boolean readable = directory.setReadable(true, false) || directory.canRead();
        boolean writable = directory.setWritable(true, false) || directory.canWrite();
        boolean executable = directory.setExecutable(true, false) || directory.canExecute();
        if (!readable || !writable || !executable) {
            throw new IOException("sandbox workspace permissions could not be prepared");
        }
    }
    Path workspaceForSession(String sessionId) {
        String safeName = safeFilesystemName(sessionId);
        Path workspace = workspaceRoot.resolve(safeName).toAbsolutePath().normalize();
        if (!workspace.startsWith(workspaceRoot) || workspace.equals(workspaceRoot)) {
            throw new IllegalArgumentException("invalid sandbox session workspace");
        }
        return workspace;
    }
    String mountSourceForSession(String sessionId, Path workspace) {
        if (workspaceMountSourceRoot == null) {
            return workspace.toAbsolutePath().normalize().toString();
        }
        String root = stripTrailingSeparators(workspaceMountSourceRoot);
        if (root.length() >= 3 && Character.isLetter(root.charAt(0)) && root.charAt(1) == ':'
                && (root.charAt(2) == '/' || root.charAt(2) == '\\')) {
            root = "/run/desktop/mnt/host/"
                    + Character.toLowerCase(root.charAt(0))
                    + root.substring(2).replace('\\', '/');
        }
        return root + "/" + safeFilesystemName(sessionId);
    }
    void deleteWorkspace(String sessionId) {
        Path workspace = workspaceForSession(sessionId);
        if (!Files.exists(workspace)) {
            return;
        }
        if (!deleteWorkspacePath(workspace)) {
            throw new IllegalStateException("sandbox workspace could not be deleted");
        }
    }
    boolean deleteWorkspacePath(Path workspace) {
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
                    // The final existence check keeps close fail-closed when any deletion fails.
                }
            });
            return !Files.exists(safeWorkspace);
        } catch (IOException ignored) {
            return false;
        }
    }
    boolean isManagedWorkspaceDirectory(Path path) {
        Path safePath = path.toAbsolutePath().normalize();
        if (!safePath.startsWith(workspaceRoot) || safePath.equals(workspaceRoot)) {
            return false;
        }
        Path filename = safePath.getFileName();
        return filename != null && filename.toString().startsWith(COORDINATOR_SESSION_ID_PREFIX);
    }
    boolean isManagedContainerName(String value) {
        return ContainerSandboxTextSupport.hasText(value) && value.startsWith(CONTAINER_NAME_PREFIX);
    }
    String containerNameFromPsLine(String line) {
        if (!ContainerSandboxTextSupport.hasText(line)) {
            return "";
        }
        String[] parts = line.split("\\t", 2);
        return parts[0].trim();
    }
    boolean isRecentWorkspace(Path workspace, Instant cutoff) {
        try {
            FileTime modifiedTime = Files.getLastModifiedTime(workspace, LinkOption.NOFOLLOW_LINKS);
            return modifiedTime.toInstant().isAfter(cutoff);
        } catch (IOException ex) {
            return true;
        }
    }
    Set<String> normalizeActiveSessionIds(Set<String> activeSessionIds) {
        if (activeSessionIds == null || activeSessionIds.isEmpty()) {
            return Set.of();
        }
        Set<String> safeNames = new HashSet<>();
        for (String sessionId : activeSessionIds) {
            if (ContainerSandboxTextSupport.hasText(sessionId)) {
                safeNames.add(safeFilesystemName(sessionId.trim()));
            }
        }
        return Set.copyOf(safeNames);
    }
    Set<String> normalizeActiveContainerNames(Set<String> activeSessionIds) {
        if (activeSessionIds == null || activeSessionIds.isEmpty()) {
            return Set.of();
        }
        Set<String> safeNames = new HashSet<>();
        for (String sessionId : activeSessionIds) {
            if (ContainerSandboxTextSupport.hasText(sessionId)) {
                safeNames.add(containerName(sessionId));
            }
        }
        return Set.copyOf(safeNames);
    }
    String containerName(String sessionId) {
        String base = CONTAINER_NAME_PREFIX + safeFilesystemName(sessionId).toLowerCase(Locale.ROOT);
        if (base.length() <= 96) {
            return base;
        }
        return base.substring(0, 96);
    }
    String safeFilesystemName(String value) {
        String safe = ContainerSandboxTextSupport.nullToEmpty(value).replaceAll("[^A-Za-z0-9_.-]", "_");
        if (safe.isBlank()) {
            throw new IllegalArgumentException("sandbox session id must not be blank");
        }
        return safe;
    }
    int ownedActiveSessionCount(Set<String> activeSessionIds) {
        return (int) activeSessionIds.stream()
                .filter(sessionId -> Files.isDirectory(workspaceForSession(sessionId), LinkOption.NOFOLLOW_LINKS))
                .count();
    }
    static String stripTrailingSeparators(String value) {
        String result = Objects.requireNonNull(value, "value must not be null").trim();
        while (result.endsWith("/") || result.endsWith("\\")) {
            result = result.substring(0, result.length() - 1);
        }
        if (result.isEmpty()) {
            throw new IllegalArgumentException("workspaceMountSourceRoot must not be empty");
        }
        return result;
    }
    static byte[] decodeBase64Content(String value) {
        try {
            return Base64.getDecoder().decode(ContainerSandboxTextSupport.nullToEmpty(value).trim());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("file conversion content is not valid base64", ex);
        }
    }
}

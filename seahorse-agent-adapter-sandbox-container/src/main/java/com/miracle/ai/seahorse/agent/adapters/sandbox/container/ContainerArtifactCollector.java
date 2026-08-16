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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 沙箱执行产物收集与类型/敏感度推导协作者（从 {@link ContainerSandboxRuntimeAdapter} 提取）。
 * 按 §7 收敛原则外提：沙箱执行产物收集与类型/敏感度推导协作者。
 */
final class ContainerArtifactCollector {

    static final String ARTIFACT_ID_PREFIX = "sandbox_artifact_container_";
    static final int MAX_SESSION_WORKSPACE_FILES = 256;

    private final ContainerSandboxAdapterProperties properties;
    private final ContainerBrowserAutomationSupport browserAutomationSupport;

    ContainerArtifactCollector(ContainerSandboxAdapterProperties properties,
                                ContainerBrowserAutomationSupport browserAutomationSupport) {
        this.properties = Objects.requireNonNull(properties, "properties must not be null");
        this.browserAutomationSupport = Objects.requireNonNull(browserAutomationSupport,
                "browserAutomationSupport must not be null");
    }

    long maxSessionFileLimit() {
        return Math.max(1L, properties.getMaxSessionFileBytes());
    }

    List<SandboxArtifact> collectArtifacts(SandboxSession session,
                                                   String executionId,
                                                   Path workspace,
                                                   Instant createdAt,
                                                   Set<Path> excludedArtifacts) throws IOException {
        if (!Files.exists(workspace)) {
            return List.of();
        }
        Path safeWorkspace = workspace.toAbsolutePath().normalize();
        Set<Path> safeExcludedArtifacts = excludedArtifacts == null
                ? Set.of()
                : excludedArtifacts.stream()
                .filter(Objects::nonNull)
                .map(path -> path.toAbsolutePath().normalize())
                .collect(java.util.stream.Collectors.toUnmodifiableSet());
        try (var paths = Files.walk(safeWorkspace)) {
            List<Path> files = paths
                    .filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .map(path -> path.toAbsolutePath().normalize())
                    .filter(path -> path.startsWith(safeWorkspace))
                    .limit(MAX_SESSION_WORKSPACE_FILES + 1L)
                    .toList();
            if (files.size() > MAX_SESSION_WORKSPACE_FILES) {
                throw new IOException("sandbox workspace exceeds session file count limit");
            }
            long workspaceBytes = 0L;
            for (Path path : files) {
                workspaceBytes = Math.addExact(workspaceBytes, Files.size(path));
                if (workspaceBytes > maxSessionFileLimit()) {
                    throw new IOException("sandbox workspace exceeds session file limit");
                }
            }
            return files.stream()
                    .filter(path -> !safeExcludedArtifacts.contains(path))
                    .sorted(Comparator.comparing(path -> safeWorkspace.relativize(path).toString()))
                    .map(path -> artifact(session, executionId, path, createdAt))
                    .toList();
        }
    }
    SandboxArtifact artifact(SandboxSession session, String executionId, Path path, Instant createdAt) {
        return new SandboxArtifact(
                ARTIFACT_ID_PREFIX + SnowflakeIds.nextIdString(),
                session.sessionId(),
                executionId,
                path.toUri().toString(),
                mediaType(path),
                SandboxArtifactScanStatus.PENDING,
                artifactSensitivity(path),
                createdAt);
    }
    ContextSensitivity artifactSensitivity(Path path) {
        String name = path.getFileName() == null
                ? ""
                : path.getFileName().toString();
        if (browserAutomationSupport.browserSessionStateName().equals(name)) {
            return ContextSensitivity.SECRET;
        }
        return ContextSensitivity.INTERNAL;
    }
    String mediaType(Path path) {
        String known = knownMediaType(path);
        if (known != null) {
            return known;
        }
        try {
            String probed = Files.probeContentType(path);
            if (ContainerSandboxTextSupport.hasText(probed)) {
                return probed.trim();
            }
        } catch (IOException ignored) {
            // Fall through to a conservative binary type.
        }
        return "application/octet-stream";
    }
    String knownMediaType(Path path) {
        String name = path.getFileName() == null
                ? ""
                : path.getFileName().toString().toLowerCase(Locale.ROOT);
        if (name.endsWith(".tar.gz") || name.endsWith(".tgz")) {
            return "application/gzip";
        }
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
            case "har" -> "application/har+json";
            case "xml" -> "application/xml";
            case "pdf" -> "application/pdf";
            case "tar" -> "application/x-tar";
            case "zip" -> "application/zip";
            case "docx" -> "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "ods" -> "application/vnd.oasis.opendocument.spreadsheet";
            case "odp" -> "application/vnd.oasis.opendocument.presentation";
            case "xlsx" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "pptx" -> "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "docm" -> "application/vnd.ms-word.document.macroenabled.12";
            case "xlsm" -> "application/vnd.ms-excel.sheet.macroenabled.12";
            case "pptm" -> "application/vnd.ms-powerpoint.presentation.macroenabled.12";
            case "gif" -> "image/gif";
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "webp" -> "image/webp";
            case "webm" -> "video/webm";
            default -> null;
        };
    }
}

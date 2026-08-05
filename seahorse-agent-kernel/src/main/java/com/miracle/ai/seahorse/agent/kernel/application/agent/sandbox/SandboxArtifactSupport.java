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

package com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactRedactionSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDetailDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScannerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * 沙箱工件管理协作者（从 {@link KernelSandboxRuntimeService} 提取）。
 * 按 §7 收敛原则外提：负责工件的查询、扫描、持久化与下载治理，会话读取权限通过函数接口注入。
 */
final class SandboxArtifactSupport {

    /** 会话读取访问（由宿主服务提供，避免反向依赖主流程类）。 */
    interface SandboxSessionAccess {
        SandboxSession findSessionOrThrow(String sessionId);

        SandboxSession requireReadableSession(SandboxSession session);
    }

    private static final String SANDBOX_ARTIFACT_BUCKET = "sandbox-artifacts";
    private static final String ARTIFACT_SCAN_FAILED_SUMMARY = "artifact scanner failed";
    private static final String ARTIFACT_FILE_UNAVAILABLE_SUMMARY = "artifact file unavailable before storage copy";
    private static final String ARTIFACT_STORAGE_COPY_FAILED_SUMMARY = "artifact storage copy failed";
    private static final String DOWNLOAD_BLOCKED = "Sandbox artifact is not available for download";
    private static final String UNSAFE_STORAGE_REF_BLOCKED =
            "Sandbox artifact storage reference is not available through the download endpoint";
    private static final int MAX_BROWSER_SESSION_STATE_ARTIFACT_BYTES = 128 * 1024;
    private static final String BROWSER_SESSION_STATE_ARTIFACT_NAME = "browser-session-state.json";
    private static final Map<String, String> FILE_EXTENSIONS = Map.ofEntries(
            Map.entry("text/html", ".html"),
            Map.entry("text/markdown", ".md"),
            Map.entry("text/plain", ".txt"),
            Map.entry("text/csv", ".csv"),
            Map.entry("application/gzip", ".tar.gz"),
            Map.entry("application/json", ".json"),
            Map.entry("application/pdf", ".pdf"),
            Map.entry("application/x-gzip", ".tar.gz"),
            Map.entry("application/x-tar", ".tar"),
            Map.entry("application/vnd.ms-excel.sheet.macroenabled.12", ".xlsm"),
            Map.entry("application/vnd.ms-powerpoint.presentation.macroenabled.12", ".pptm"),
            Map.entry("application/vnd.ms-word.document.macroenabled.12", ".docm"),
            Map.entry("application/vnd.openxmlformats-officedocument.presentationml.presentation", ".pptx"),
            Map.entry("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", ".xlsx"),
            Map.entry("application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"),
            Map.entry("image/png", ".png"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/svg+xml", ".svg"),
            Map.entry("video/webm", ".webm"));

    private final SandboxArtifactPort artifactPort;
    private final SandboxArtifactScannerPort artifactScannerPort;
    private final ObjectStoragePort artifactStoragePort;
    private final SandboxArtifactQueryPort artifactQueryPort;
    private final SandboxPathValidator pathValidator;
    private final SandboxSessionAccess sessionAccess;

    SandboxArtifactSupport(SandboxArtifactPort artifactPort,
                           SandboxArtifactScannerPort artifactScannerPort,
                           ObjectStoragePort artifactStoragePort,
                           SandboxArtifactQueryPort artifactQueryPort,
                           SandboxPathValidator pathValidator,
                           SandboxSessionAccess sessionAccess) {
        this.artifactPort = Objects.requireNonNull(artifactPort, "artifactPort must not be null");
        this.artifactScannerPort = Objects.requireNonNull(artifactScannerPort, "artifactScannerPort must not be null");
        this.artifactStoragePort = artifactStoragePort;
        this.artifactQueryPort = Objects.requireNonNull(artifactQueryPort, "artifactQueryPort must not be null");
        this.pathValidator = Objects.requireNonNull(pathValidator, "pathValidator must not be null");
        this.sessionAccess = Objects.requireNonNull(sessionAccess, "sessionAccess must not be null");
    }

    List<SandboxArtifact> listArtifacts(String sessionId) {
        String safeSessionId = requireText(sessionId, "sessionId must not be blank");
        sessionAccess.requireReadableSession(sessionAccess.findSessionOrThrow(safeSessionId));
        return artifactQueryPort.listArtifactsBySession(safeSessionId);
    }

    SandboxArtifactDetailDecision describeArtifact(String artifactId) {
        SandboxArtifact artifact = findArtifactWithSession(artifactId);
        SandboxArtifactDownloadPolicy policy = downloadPolicy(artifact);
        return new SandboxArtifactDetailDecision(
                artifact,
                artifact.mediaType(),
                artifactFilename(artifact),
                policy.downloadable(),
                policy.blockedReason());
    }

    SandboxArtifactDownloadDecision downloadArtifact(String artifactId) {
        SandboxArtifact artifact = findArtifactWithSession(artifactId);
        SandboxArtifactDownloadPolicy policy = downloadPolicy(artifact);
        if (!policy.downloadable()) {
            throw new IllegalStateException(policy.blockedReason());
        }
        return new SandboxArtifactDownloadDecision(
                artifact,
                artifact.mediaType(),
                artifactFilename(artifact),
                artifact.objectUri());
    }

    String readBrowserSessionStateArtifact(String artifactId) {
        SandboxArtifact artifact = findArtifactWithSession(artifactId);
        return readBrowserSessionStateArtifact(artifact);
    }

    SandboxArtifact requireBrowserProfileArtifact(String tenantId, String artifactId) {
        SandboxArtifact artifact = artifactQueryPort.findArtifactById(
                        requireText(artifactId, "sessionStateArtifactId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("Sandbox browser session-state artifact not found"));
        SandboxSession session = sessionAccess.findSessionOrThrow(artifact.sessionId());
        if (!tenantId.equals(session.tenantId())) {
            throw new IllegalArgumentException("Sandbox browser profile artifact belongs to another tenant");
        }
        requireGovernedBrowserSessionStateArtifact(artifact);
        return artifact;
    }

    String readBrowserSessionStateArtifact(SandboxArtifact artifact) {
        requireGovernedBrowserSessionStateArtifact(artifact);
        try (InputStream input = openArtifactObjectStream(artifact)) {
            byte[] bytes = input.readNBytes(MAX_BROWSER_SESSION_STATE_ARTIFACT_BYTES + 1);
            if (bytes.length > MAX_BROWSER_SESSION_STATE_ARTIFACT_BYTES) {
                throw new IllegalStateException("Sandbox browser session-state artifact exceeds replay budget");
            }
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Sandbox browser session-state artifact could not be read", ex);
        }
    }

    private void requireGovernedBrowserSessionStateArtifact(SandboxArtifact artifact) {
        if (!isBrowserSessionStateArtifact(artifact)) {
            throw new IllegalArgumentException("Sandbox browser session-state artifact not found");
        }
        if (!"application/json".equals(normalizedMediaType(artifact.mediaType()))
                || artifact.scanStatus() != SandboxArtifactScanStatus.BLOCKED
                || artifact.sensitivity() != ContextSensitivity.SECRET) {
            throw new IllegalStateException("Sandbox browser session-state artifact is not governed for replay");
        }
    }

    private SandboxArtifact findArtifactWithSession(String artifactId) {
        SandboxArtifact artifact = artifactQueryPort.findArtifactById(
                        requireText(artifactId, "artifactId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("Sandbox artifact not found"));
        sessionAccess.requireReadableSession(sessionAccess.findSessionOrThrow(artifact.sessionId()));
        return artifact;
    }

    private SandboxArtifactDownloadPolicy downloadPolicy(SandboxArtifact artifact) {
        if (!artifact.downloadable()) {
            return SandboxArtifactDownloadPolicy.blocked(DOWNLOAD_BLOCKED);
        }
        if (isUnsafeDownloadReference(artifact.objectUri())) {
            return SandboxArtifactDownloadPolicy.blocked(UNSAFE_STORAGE_REF_BLOCKED);
        }
        return SandboxArtifactDownloadPolicy.allowed();
    }

    SandboxArtifact persistArtifact(SandboxArtifact artifact) {
        SandboxArtifact scanned = scanArtifact(artifact);
        SandboxArtifact prepared = copyDownloadableFileArtifact(scanned);
        try {
            return artifactPort.save(prepared);
        } catch (RuntimeException ex) {
            cleanupCopiedArtifact(scanned, prepared);
            throw ex;
        }
    }

    private SandboxArtifact scanArtifact(SandboxArtifact artifact) {
        try {
            SandboxArtifactScanResult result = Objects.requireNonNull(
                    artifactScannerPort.scan(new SandboxArtifactScanRequest(artifact)),
                    "artifact scan result must not be null");
            return artifact.withScanDecision(
                    result.scanStatus(),
                    result.sensitivity(),
                    result.summary(),
                    result.redactionSummaryJson());
        } catch (RuntimeException ex) {
            return artifact.withScanDecision(
                    SandboxArtifactScanStatus.BLOCKED,
                    ContextSensitivity.SECRET,
                    ARTIFACT_SCAN_FAILED_SUMMARY,
                    SandboxArtifactRedactionSummary.blocked(
                            ARTIFACT_SCAN_FAILED_SUMMARY,
                            false,
                            List.of("SCAN_ERROR")));
        }
    }

    private SandboxArtifact copyDownloadableFileArtifact(SandboxArtifact artifact) {
        if (artifactStoragePort == null
                || (!artifact.downloadable() && !isBrowserSessionStateArtifact(artifact))
                || !isFileUri(artifact.objectUri())) {
            return artifact;
        }
        try {
            Path path = Path.of(URI.create(artifact.objectUri())).toAbsolutePath().normalize();
            pathValidator.validate(path.toString());
            if (!Files.isRegularFile(path)) {
                return artifact.withScanDecision(
                        SandboxArtifactScanStatus.BLOCKED,
                        ContextSensitivity.SECRET,
                        ARTIFACT_FILE_UNAVAILABLE_SUMMARY,
                        SandboxArtifactRedactionSummary.blocked(
                                ARTIFACT_FILE_UNAVAILABLE_SUMMARY,
                                false,
                                List.of("CONTENT_UNAVAILABLE")));
            }
            long size = Files.size(path);
            artifactStoragePort.ensureBucket(SANDBOX_ARTIFACT_BUCKET);
            try (InputStream input = Files.newInputStream(path)) {
                StoredObject stored = artifactStoragePort.reliableUpload(
                        SANDBOX_ARTIFACT_BUCKET,
                        input,
                        size,
                        artifactFilename(artifact, path),
                        artifact.mediaType());
                return artifact.withObjectUri(stored.url());
            }
        } catch (IOException | RuntimeException ex) {
            return artifact.withScanDecision(
                    SandboxArtifactScanStatus.BLOCKED,
                    ContextSensitivity.SECRET,
                    ARTIFACT_STORAGE_COPY_FAILED_SUMMARY,
                    SandboxArtifactRedactionSummary.blocked(
                            ARTIFACT_STORAGE_COPY_FAILED_SUMMARY,
                            false,
                            List.of("STORAGE_COPY_FAILED")));
        }
    }

    private InputStream openArtifactObjectStream(SandboxArtifact artifact) throws IOException {
        if (artifactStoragePort != null && !isFileUri(artifact.objectUri())) {
            return artifactStoragePort.openStream(artifact.objectUri());
        }
        if (!isFileUri(artifact.objectUri())) {
            throw new IllegalStateException("Sandbox browser session-state artifact storage is not available");
        }
        Path path = Path.of(URI.create(artifact.objectUri())).toAbsolutePath().normalize();
        pathValidator.validate(path.toString());
        return Files.newInputStream(path);
    }

    private boolean isBrowserSessionStateArtifact(SandboxArtifact artifact) {
        try {
            String path = URI.create(artifact.objectUri()).getPath();
            if (path == null) {
                return false;
            }
            int slashIndex = path.lastIndexOf('/');
            String name = slashIndex >= 0 ? path.substring(slashIndex + 1) : path;
            return BROWSER_SESSION_STATE_ARTIFACT_NAME.equals(name)
                    || name.endsWith("-" + BROWSER_SESSION_STATE_ARTIFACT_NAME);
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private void cleanupCopiedArtifact(SandboxArtifact scanned, SandboxArtifact prepared) {
        if (artifactStoragePort == null || Objects.equals(scanned.objectUri(), prepared.objectUri())) {
            return;
        }
        try {
            artifactStoragePort.deleteByUrl(prepared.objectUri());
        } catch (RuntimeException ignored) {
            // Preserve the original persistence failure.
        }
    }

    private String artifactFilename(SandboxArtifact artifact, Path path) {
        Path filename = path.getFileName();
        if (filename != null && hasText(filename.toString())) {
            return filename.toString();
        }
        return artifact.artifactId() + ".bin";
    }

    private boolean isFileUri(String objectUri) {
        try {
            return "file".equalsIgnoreCase(URI.create(objectUri).getScheme());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isUnsafeDownloadReference(String objectUri) {
        try {
            String scheme = URI.create(objectUri).getScheme();
            if (!hasText(scheme)) {
                return true;
            }
            String normalized = scheme.toLowerCase(Locale.ROOT);
            return "file".equals(normalized) || "http".equals(normalized) || "https".equals(normalized);
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private String artifactFilename(SandboxArtifact artifact) {
        String safeBase = artifact.artifactId().replaceAll("[^A-Za-z0-9._-]", "_");
        String extension = FILE_EXTENSIONS.getOrDefault(normalizedMediaType(artifact.mediaType()), ".bin");
        if (safeBase.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return safeBase;
        }
        return safeBase + extension;
    }

    private String normalizedMediaType(String mediaType) {
        int separator = mediaType.indexOf(';');
        String base = separator >= 0 ? mediaType.substring(0, separator) : mediaType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record SandboxArtifactDownloadPolicy(boolean downloadable, String blockedReason) {

        static SandboxArtifactDownloadPolicy allowed() {
            return new SandboxArtifactDownloadPolicy(true, null);
        }

        static SandboxArtifactDownloadPolicy blocked(String reason) {
            return new SandboxArtifactDownloadPolicy(false, reason);
        }
    }
}

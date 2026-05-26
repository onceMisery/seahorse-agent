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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;

public record AgentArtifact(String artifactId,
                            String runId,
                            String messageId,
                            String tenantId,
                            String userId,
                            AgentArtifactType artifactType,
                            String title,
                            String mimeType,
                            String storageRef,
                            String previewText,
                            String provenanceJson,
                            AgentArtifactScanStatus scanStatus,
                            Instant createdAt) {

    private static final Set<String> PASSIVE_PREVIEW_MIME_TYPES = Set.of(
            "text/plain",
            "text/markdown",
            "text/csv",
            "application/json");
    private static final Set<String> ACTIVE_MIME_TYPES = Set.of(
            "text/html",
            "image/svg+xml",
            "application/javascript",
            "text/javascript",
            "application/x-javascript");

    public AgentArtifact {
        artifactId = requireText(artifactId, "artifactId must not be blank");
        runId = requireText(runId, "runId must not be blank");
        messageId = normalizeOptional(messageId);
        tenantId = requireText(tenantId, "tenantId must not be blank");
        userId = requireText(userId, "userId must not be blank");
        artifactType = Objects.requireNonNull(artifactType, "artifactType must not be null");
        title = requireText(title, "title must not be blank");
        mimeType = requireText(mimeType, "mimeType must not be blank");
        storageRef = requireText(storageRef, "storageRef must not be blank");
        previewText = normalizeOptional(previewText);
        provenanceJson = normalizeOptional(provenanceJson);
        scanStatus = Objects.requireNonNullElse(scanStatus, AgentArtifactScanStatus.PENDING);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean canPreview() {
        return scanStatus == AgentArtifactScanStatus.CLEAN
                && hasText(previewText)
                && isPassivePreviewContent();
    }

    public AgentArtifactDisposition disposition() {
        return canPreview() ? AgentArtifactDisposition.INLINE_PREVIEW : AgentArtifactDisposition.ATTACHMENT_DOWNLOAD;
    }

    public boolean downloadable() {
        return scanStatus == AgentArtifactScanStatus.CLEAN;
    }

    private boolean isPassivePreviewContent() {
        String normalizedMimeType = normalizedMimeType();
        if (ACTIVE_MIME_TYPES.contains(normalizedMimeType)) {
            return false;
        }
        if (artifactType == AgentArtifactType.HTML) {
            return false;
        }
        return PASSIVE_PREVIEW_MIME_TYPES.contains(normalizedMimeType)
                || artifactType == AgentArtifactType.MARKDOWN
                || artifactType == AgentArtifactType.REPORT
                || artifactType == AgentArtifactType.TABLE;
    }

    private String normalizedMimeType() {
        int separator = mimeType.indexOf(';');
        String base = separator >= 0 ? mimeType.substring(0, separator) : mimeType;
        return base.trim().toLowerCase(Locale.ROOT);
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeOptional(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private static boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }
}

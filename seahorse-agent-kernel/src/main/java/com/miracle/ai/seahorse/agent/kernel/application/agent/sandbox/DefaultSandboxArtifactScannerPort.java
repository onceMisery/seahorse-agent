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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScannerPort;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public class DefaultSandboxArtifactScannerPort implements SandboxArtifactScannerPort {

    private static final int MAX_CONTENT_SCAN_BYTES = 256 * 1024;
    private static final Set<String> PROMPT_SAFE_EXACT_MEDIA_TYPES = Set.of(
            "application/json",
            "application/pdf",
            "application/xml",
            "image/gif",
            "image/jpeg",
            "image/png",
            "image/webp");
    private static final Set<String> DOWNLOAD_ONLY_EXACT_MEDIA_TYPES = Set.of(
            "video/webm");
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "api_key",
            "credential",
            "private-key",
            "private_key",
            "secret",
            "token");
    private static final Pattern PRIVATE_KEY_PATTERN = Pattern.compile(
            "-----BEGIN [A-Z0-9 ]*PRIVATE KEY-----",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern ASSIGNED_SECRET_PATTERN = Pattern.compile(
            "\\b(api[_-]?key|access[_-]?token|auth[_-]?token|secret|password)\\b\\s*[:=]\\s*['\"]?[^\\s'\";,]{8,}",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern OPENAI_STYLE_TOKEN_PATTERN = Pattern.compile(
            "\\bsk-[A-Za-z0-9_-]{16,}\\b");
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "\\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\\.[A-Z]{2,}\\b",
            Pattern.CASE_INSENSITIVE);
    private static final Pattern US_SSN_PATTERN = Pattern.compile(
            "\\b\\d{3}-\\d{2}-\\d{4}\\b");

    @Override
    public SandboxArtifactScanResult scan(SandboxArtifactScanRequest request) {
        SandboxArtifact artifact = request.artifact();
        if (artifact.scanStatus() == SandboxArtifactScanStatus.BLOCKED
                || artifact.sensitivity() == ContextSensitivity.SECRET
                || containsSensitiveMarker(artifact.objectUri())) {
            return SandboxArtifactScanResult.blocked(
                    ContextSensitivity.SECRET,
                    "sensitive artifact metadata",
                    false,
                    List.of("SENSITIVE_METADATA"));
        }
        if (!isPromptSafeMediaType(artifact.mediaType())) {
            if (isDownloadOnlyMediaType(artifact.mediaType())) {
                return SandboxArtifactScanResult.clean(artifact.sensitivity(), "metadata scan passed", false);
            }
            return SandboxArtifactScanResult.blocked(
                    artifact.sensitivity(),
                    "unsupported prompt media type",
                    false,
                    List.of("UNSUPPORTED_MEDIA_TYPE"));
        }
        SandboxArtifactScanResult contentScan = scanLocalTextContent(artifact);
        if (contentScan != null && contentScan.scanStatus() == SandboxArtifactScanStatus.BLOCKED) {
            return contentScan;
        }
        if (artifact.sensitivity() == ContextSensitivity.CONFIDENTIAL) {
            return SandboxArtifactScanResult.redacted(
                    ContextSensitivity.CONFIDENTIAL,
                    "confidential artifact metadata",
                    contentScan != null,
                    List.of("CONFIDENTIAL_METADATA"));
        }
        if (contentScan != null) {
            return contentScan;
        }
        return SandboxArtifactScanResult.clean(artifact.sensitivity(), "metadata scan passed", false);
    }

    private static boolean isPromptSafeMediaType(String mediaType) {
        if (!hasText(mediaType)) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return normalized.startsWith("text/")
                || normalized.endsWith("+json")
                || PROMPT_SAFE_EXACT_MEDIA_TYPES.contains(normalized);
    }

    private static boolean isDownloadOnlyMediaType(String mediaType) {
        if (!hasText(mediaType)) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return DOWNLOAD_ONLY_EXACT_MEDIA_TYPES.contains(normalized);
    }

    private static SandboxArtifactScanResult scanLocalTextContent(SandboxArtifact artifact) {
        if (!isLocalFileReference(artifact.objectUri()) || !isContentScannableMediaType(artifact.mediaType())) {
            return null;
        }
        try {
            Path path = Path.of(URI.create(artifact.objectUri())).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                return SandboxArtifactScanResult.blocked(
                        ContextSensitivity.SECRET,
                        "artifact content unavailable",
                        true,
                        List.of("CONTENT_UNAVAILABLE"));
            }
            if (Files.size(path) > MAX_CONTENT_SCAN_BYTES) {
                return SandboxArtifactScanResult.blocked(
                        ContextSensitivity.CONFIDENTIAL,
                        "artifact content exceeds scanner limit",
                        true,
                        List.of("CONTENT_TOO_LARGE"));
            }
            String content = Files.readString(path, StandardCharsets.UTF_8);
            if (PRIVATE_KEY_PATTERN.matcher(content).find()) {
                return SandboxArtifactScanResult.blocked(
                        ContextSensitivity.SECRET,
                        "sensitive artifact content",
                        true,
                        List.of("PRIVATE_KEY"));
            }
            if (ASSIGNED_SECRET_PATTERN.matcher(content).find()
                    || OPENAI_STYLE_TOKEN_PATTERN.matcher(content).find()) {
                return SandboxArtifactScanResult.blocked(
                        ContextSensitivity.SECRET,
                        "sensitive artifact content",
                        true,
                        List.of("SECRET"));
            }
            if (EMAIL_PATTERN.matcher(content).find() || US_SSN_PATTERN.matcher(content).find()) {
                return SandboxArtifactScanResult.blocked(
                        ContextSensitivity.CONFIDENTIAL,
                        "personal data artifact content",
                        true,
                        List.of("PERSONAL_DATA"));
            }
            return SandboxArtifactScanResult.clean(artifact.sensitivity(), "metadata scan passed", true);
        } catch (IOException | RuntimeException ex) {
            return SandboxArtifactScanResult.blocked(
                    ContextSensitivity.SECRET,
                    "artifact content scan failed",
                    true,
                    List.of("SCAN_ERROR"));
        }
    }

    private static boolean isLocalFileReference(String value) {
        if (!hasText(value)) {
            return false;
        }
        try {
            return "file".equalsIgnoreCase(URI.create(value).getScheme());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private static boolean isContentScannableMediaType(String mediaType) {
        if (!hasText(mediaType)) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return normalized.startsWith("text/")
                || "application/json".equals(normalized)
                || normalized.endsWith("+json")
                || "application/xml".equals(normalized);
    }

    private static boolean containsSensitiveMarker(String value) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = value.toLowerCase(Locale.ROOT);
        return SENSITIVE_MARKERS.stream().anyMatch(normalized::contains);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

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

import java.util.Locale;
import java.util.Set;

public class DefaultSandboxArtifactScannerPort implements SandboxArtifactScannerPort {

    private static final Set<String> SAFE_EXACT_MEDIA_TYPES = Set.of(
            "application/json",
            "application/pdf",
            "application/xml",
            "image/gif",
            "image/jpeg",
            "image/png",
            "image/webp");
    private static final Set<String> SENSITIVE_MARKERS = Set.of(
            "api_key",
            "credential",
            "private-key",
            "private_key",
            "secret",
            "token");

    @Override
    public SandboxArtifactScanResult scan(SandboxArtifactScanRequest request) {
        SandboxArtifact artifact = request.artifact();
        if (artifact.scanStatus() == SandboxArtifactScanStatus.BLOCKED
                || artifact.sensitivity() == ContextSensitivity.SECRET
                || containsSensitiveMarker(artifact.objectUri())) {
            return SandboxArtifactScanResult.blocked(ContextSensitivity.SECRET, "sensitive artifact metadata");
        }
        if (!isPromptSafeMediaType(artifact.mediaType())) {
            return SandboxArtifactScanResult.blocked(artifact.sensitivity(), "unsupported prompt media type");
        }
        if (artifact.sensitivity() == ContextSensitivity.CONFIDENTIAL) {
            return SandboxArtifactScanResult.redacted(ContextSensitivity.CONFIDENTIAL, "confidential artifact metadata");
        }
        return SandboxArtifactScanResult.clean(artifact.sensitivity(), "metadata scan passed");
    }

    private static boolean isPromptSafeMediaType(String mediaType) {
        if (!hasText(mediaType)) {
            return false;
        }
        String normalized = mediaType.toLowerCase(Locale.ROOT).split(";", 2)[0].trim();
        return normalized.startsWith("text/") || SAFE_EXACT_MEDIA_TYPES.contains(normalized);
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

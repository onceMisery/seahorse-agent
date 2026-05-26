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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;

import java.time.Instant;
import java.util.Objects;

public record SandboxArtifact(String artifactId,
                              String sessionId,
                              String executionId,
                              String objectUri,
                              String mediaType,
                              SandboxArtifactScanStatus scanStatus,
                              ContextSensitivity sensitivity,
                              Instant createdAt) {

    public SandboxArtifact {
        artifactId = requireText(artifactId, "artifactId must not be blank");
        sessionId = requireText(sessionId, "sessionId must not be blank");
        executionId = requireText(executionId, "executionId must not be blank");
        objectUri = requireText(objectUri, "objectUri must not be blank");
        mediaType = requireText(mediaType, "mediaType must not be blank");
        scanStatus = Objects.requireNonNullElse(scanStatus, SandboxArtifactScanStatus.PENDING);
        sensitivity = Objects.requireNonNullElse(sensitivity, ContextSensitivity.SECRET);
        createdAt = Objects.requireNonNull(createdAt, "createdAt must not be null");
    }

    public boolean promptVisible() {
        return scanStatus == SandboxArtifactScanStatus.CLEAN && sensitivity != ContextSensitivity.SECRET;
    }

    private static String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

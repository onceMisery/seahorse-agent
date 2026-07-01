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

package com.miracle.ai.seahorse.agent.ports.outbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;

import java.util.Objects;

public record SandboxArtifactScanResult(SandboxArtifactScanStatus scanStatus,
                                        ContextSensitivity sensitivity,
                                        String summary) {

    public SandboxArtifactScanResult {
        scanStatus = Objects.requireNonNullElse(scanStatus, SandboxArtifactScanStatus.BLOCKED);
        if (scanStatus == SandboxArtifactScanStatus.PENDING) {
            scanStatus = SandboxArtifactScanStatus.BLOCKED;
        }
        sensitivity = Objects.requireNonNullElse(sensitivity, ContextSensitivity.SECRET);
        summary = hasText(summary) ? summary.trim() : scanStatus.name();
    }

    public static SandboxArtifactScanResult clean(ContextSensitivity sensitivity, String summary) {
        return new SandboxArtifactScanResult(SandboxArtifactScanStatus.CLEAN, sensitivity, summary);
    }

    public static SandboxArtifactScanResult redacted(ContextSensitivity sensitivity, String summary) {
        return new SandboxArtifactScanResult(SandboxArtifactScanStatus.REDACTED, sensitivity, summary);
    }

    public static SandboxArtifactScanResult blocked(ContextSensitivity sensitivity, String summary) {
        return new SandboxArtifactScanResult(SandboxArtifactScanStatus.BLOCKED, sensitivity, summary);
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

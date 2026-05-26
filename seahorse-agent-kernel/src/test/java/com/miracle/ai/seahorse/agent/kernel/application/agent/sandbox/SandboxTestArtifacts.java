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

import java.time.Instant;

final class SandboxTestArtifacts {

    private static final Instant CREATED_AT = Instant.parse("2026-05-26T00:00:00Z");

    private SandboxTestArtifacts() {
    }

    static SandboxArtifact clean(String artifactId) {
        return artifact(artifactId, SandboxArtifactScanStatus.CLEAN, ContextSensitivity.INTERNAL);
    }

    static SandboxArtifact secret(String artifactId) {
        return artifact(artifactId, SandboxArtifactScanStatus.CLEAN, ContextSensitivity.SECRET);
    }

    private static SandboxArtifact artifact(String artifactId,
                                            SandboxArtifactScanStatus scanStatus,
                                            ContextSensitivity sensitivity) {
        return new SandboxArtifact(
                artifactId,
                "session-1",
                "exec-1",
                "object://sandbox/" + artifactId,
                "text/plain",
                scanStatus,
                sensitivity,
                CREATED_AT);
    }
}

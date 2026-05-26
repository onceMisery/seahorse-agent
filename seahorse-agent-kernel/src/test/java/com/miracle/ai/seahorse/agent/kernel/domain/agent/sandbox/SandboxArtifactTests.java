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
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SandboxArtifactTests {

    @Test
    void shouldOnlyExposeScannedNonSecretArtifactsToPrompt() {
        SandboxArtifact publicArtifact = artifact("artifact-1", SandboxArtifactScanStatus.CLEAN, ContextSensitivity.INTERNAL);
        SandboxArtifact unscannedArtifact = artifact("artifact-2", SandboxArtifactScanStatus.PENDING, ContextSensitivity.INTERNAL);
        SandboxArtifact secretArtifact = artifact("artifact-3", SandboxArtifactScanStatus.CLEAN, ContextSensitivity.SECRET);

        assertTrue(publicArtifact.promptVisible());
        assertFalse(unscannedArtifact.promptVisible());
        assertFalse(secretArtifact.promptVisible());
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
                Instant.parse("2026-05-26T00:00:00Z"));
    }
}

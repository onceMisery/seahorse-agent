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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;

class DefaultSandboxArtifactScannerPortTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private final DefaultSandboxArtifactScannerPort scanner = new DefaultSandboxArtifactScannerPort();

    @Test
    void shouldPassCleanLocalTextArtifact(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("answer.txt");
        Files.writeString(output, "plain artifact marker", StandardCharsets.UTF_8);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output)));

        assertEquals(SandboxArtifactScanStatus.CLEAN, result.scanStatus());
        assertEquals(ContextSensitivity.INTERNAL, result.sensitivity());
    }

    @Test
    void shouldBlockLocalTextArtifactWithAssignedSecret(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("answer.txt");
        Files.writeString(output, "api_key = 'sk-seahorse-secret-1234567890'", StandardCharsets.UTF_8);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output)));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.SECRET, result.sensitivity());
    }

    @Test
    void shouldBlockLocalTextArtifactWithPersonalData(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("profile.txt");
        Files.writeString(output, "owner email: user@example.com", StandardCharsets.UTF_8);

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(output)));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.CONFIDENTIAL, result.sensitivity());
    }

    @Test
    void shouldFailClosedWhenLocalTextArtifactCannotBeRead(@TempDir Path tempDir) {
        Path missing = tempDir.resolve("missing.txt");

        SandboxArtifactScanResult result = scanner.scan(new SandboxArtifactScanRequest(fileArtifact(missing)));

        assertEquals(SandboxArtifactScanStatus.BLOCKED, result.scanStatus());
        assertEquals(ContextSensitivity.SECRET, result.sensitivity());
    }

    private static SandboxArtifact fileArtifact(Path path) {
        return new SandboxArtifact(
                "artifact-1",
                "session-1",
                "exec-1",
                path.toUri().toString(),
                "text/plain",
                SandboxArtifactScanStatus.PENDING,
                ContextSensitivity.INTERNAL,
                NOW);
    }
}

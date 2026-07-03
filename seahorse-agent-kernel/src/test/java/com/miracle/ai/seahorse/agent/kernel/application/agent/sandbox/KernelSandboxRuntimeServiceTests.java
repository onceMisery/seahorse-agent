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

import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScannerPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDetailDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeProfilePolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionSweepResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KernelSandboxRuntimeServiceTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @Test
    void shouldDenySessionBeforeCallingRuntimeWhenPolicyRejectsRequest() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.deny(SandboxPolicyReasonCode.NETWORK_DENIED_BY_DEFAULT),
                runtime,
                new MemoryArtifactPort(),
                CLOCK);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                true,
                List.of("api.example.com")));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.NETWORK_DENIED_BY_DEFAULT, session.reasonCode());
        assertFalse(runtime.createSessionCalled);
    }

    @Test
    void shouldRejectSessionBeforeCallingRuntimeWhenCapacityIsSaturated() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-active",
                "tenant-1",
                "run-active",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        runtime.healthResponse = new SandboxRuntimeHealth(
                NOW,
                "container",
                "docker",
                SandboxRuntimeHealth.STATUS_DEGRADED,
                true,
                true,
                1024L,
                0L,
                true,
                SandboxRuntimeHealth.DISK_UNBOUNDED,
                1,
                1,
                0,
                false,
                SandboxRuntimeHealth.CAPACITY_SATURATED,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of());
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                CLOCK);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-2",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(Set.of("session-active"), runtime.healthActiveSessionIds);
        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_CAPACITY_EXCEEDED, session.reasonCode());
        assertFalse(runtime.createSessionCalled);
        assertEquals(session, sessionRepository.findSessionById(session.sessionId()).orElseThrow());
    }

    @Test
    void shouldRejectSessionBeforeCallingRuntimeWhenWorkspaceDiskThresholdIsNotMet() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-active",
                "tenant-1",
                "run-active",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        runtime.healthResponse = new SandboxRuntimeHealth(
                NOW,
                "container",
                "docker",
                SandboxRuntimeHealth.STATUS_DEGRADED,
                true,
                true,
                4096L,
                Long.MAX_VALUE,
                false,
                SandboxRuntimeHealth.DISK_LOW,
                1,
                0,
                0,
                true,
                SandboxRuntimeHealth.CAPACITY_UNBOUNDED,
                0,
                0,
                0,
                0,
                List.of(),
                List.of(),
                List.of());
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                CLOCK);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-low-disk",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(Set.of("session-active"), runtime.healthActiveSessionIds);
        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_WORKSPACE_DISK_LOW, session.reasonCode());
        assertFalse(runtime.createSessionCalled);
        assertEquals(session, sessionRepository.findSessionById(session.sessionId()).orElseThrow());
    }

    @Test
    void shouldExposeRuntimeNodeHealthFromCurrentRuntimeHealth() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-active",
                "tenant-1",
                "run-active",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                CLOCK);

        List<SandboxRuntimeNodeHealth> nodes = service.inspectRuntimeNodes();

        assertEquals(Set.of("session-active"), runtime.healthActiveSessionIds);
        assertEquals(1, nodes.size());
        SandboxRuntimeNodeHealth node = nodes.get(0);
        assertEquals("local-container-docker", node.nodeId());
        assertEquals("container", node.runtime());
        assertEquals("docker", node.engine());
        assertEquals(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, node.admissionStatus());
        assertTrue(node.admissionAvailable());
        assertEquals(1, node.activeSessionCount());
        assertEquals(SandboxRuntimeHealth.CAPACITY_UNBOUNDED, node.capacityStatus());
    }

    @Test
    void shouldNotRejectWorkspaceDiskUnavailableWhenNoThresholdIsConfigured() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        runtime.healthResponse = SandboxRuntimeHealth.unsupported(NOW, 0);
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                CLOCK);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-no-threshold",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.CREATED, session.status());
        assertTrue(runtime.createSessionCalled);
    }

    @Test
    void shouldApplyRuntimeProfileAndTtlWhenCreatingSession() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                CLOCK);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals("python-small", session.profileId());
        assertEquals(NOW.plusSeconds(3600), session.expiresAt());
        assertEquals("python-small", runtime.createSessionRequest.profileId());
        assertEquals(NOW.plusSeconds(3600), runtime.createSessionRequest.expiresAt());
    }

    @Test
    void shouldApplyRuntimeProfilePolicyTtlWhenCreatingSession() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                CLOCK);
        SandboxRuntimeProfilePolicy policy = service.upsertRuntimeProfilePolicy(
                new SandboxRuntimeProfilePolicyUpsertCommand(
                        null,
                        "tenant-1",
                        SandboxRuntimeType.CODE_INTERPRETER,
                        "python-small",
                        SandboxRuntimeProfilePolicyStatus.ACTIVE,
                        120L,
                        false));

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals("sandbox-runtime-profile-tenant-1-code_interpreter", policy.policyId());
        assertEquals(NOW.plusSeconds(120), session.expiresAt());
        assertEquals(NOW.plusSeconds(120), runtime.createSessionRequest.expiresAt());
        assertEquals("python-small", runtime.createSessionRequest.profileId());
    }

    @Test
    void shouldDenySessionBeforeCallingRuntimeWhenRuntimeProfilePolicyIsDisabled() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                CLOCK);
        service.upsertRuntimeProfilePolicy(new SandboxRuntimeProfilePolicyUpsertCommand(
                null,
                "tenant-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                "python-small",
                SandboxRuntimeProfilePolicyStatus.DISABLED,
                120L,
                false));

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_PROFILE_DISABLED, session.reasonCode());
        assertEquals(NOW.plusSeconds(120), session.expiresAt());
        assertFalse(runtime.createSessionCalled);
    }

    @Test
    void shouldRejectRuntimeProfilePolicyThatEnablesNetworkForNonBrowserRuntime() {
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                CLOCK);

        assertThrows(IllegalArgumentException.class, () -> service.upsertRuntimeProfilePolicy(
                new SandboxRuntimeProfilePolicyUpsertCommand(
                        null,
                        "tenant-1",
                        SandboxRuntimeType.CODE_INTERPRETER,
                        "python-small",
                        SandboxRuntimeProfilePolicyStatus.ACTIVE,
                        120L,
                        true)));
    }

    @Test
    void shouldRejectBrowserNetworkRequestWhenRuntimeProfileDisallowsNetwork() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                CLOCK);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.BROWSER_AUTOMATION,
                true,
                List.of("example.test")));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.NETWORK_DENIED_BY_DEFAULT, session.reasonCode());
        assertFalse(runtime.createSessionCalled);
    }

    @Test
    void shouldAllowBrowserNetworkRequestWhenRuntimeProfileAllowsNetwork() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                CLOCK);
        service.upsertRuntimeProfilePolicy(new SandboxRuntimeProfilePolicyUpsertCommand(
                null,
                "tenant-1",
                SandboxRuntimeType.BROWSER_AUTOMATION,
                "browser-readonly",
                SandboxRuntimeProfilePolicyStatus.ACTIVE,
                3600L,
                true));

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.BROWSER_AUTOMATION,
                true,
                List.of("example.test")));

        assertEquals(SandboxExecutionStatus.CREATED, session.status());
        assertTrue(runtime.createSessionCalled);
        assertTrue(runtime.createSessionRequest.networkRequested());
        assertEquals(List.of("example.test"), runtime.createSessionRequest.requestedHosts());
    }

    @Test
    void shouldFailClosedWhenExecutingDeniedSession() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> request.networkRequested()
                        ? SandboxPolicyDecision.deny(SandboxPolicyReasonCode.NETWORK_DENIED_BY_DEFAULT)
                        : SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                CLOCK);
        SandboxSession deniedSession = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                true,
                List.of("api.example.com")));

        SandboxExecutionResult result = service.execute(new SandboxExecutionCommand(
                deniedSession.sessionId(),
                "println('hello')",
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.FAILED, result.execution().status());
        assertEquals(SandboxPolicyReasonCode.NETWORK_DENIED_BY_DEFAULT, result.reasonCode());
        assertFalse(runtime.executeCalled);
    }

    @Test
    void shouldFailClosedWhenDefaultRuntimeIsUnsupported() {
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                SandboxRuntimePort.unsupported(),
                new MemoryArtifactPort(),
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.SHELL,
                false,
                List.of()));

        SandboxExecutionResult result = service.execute(new SandboxExecutionCommand(
                session.sessionId(),
                "println('hello')",
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.FAILED, result.execution().status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED, result.reasonCode());
        assertEquals(0, result.artifacts().size());
    }

    @Test
    void shouldScanAndSaveAllArtifactsButReturnOnlyPromptVisibleArtifacts() {
        MemoryArtifactPort artifactPort = new MemoryArtifactPort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                artifactPort,
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.FILE_CONVERSION,
                false,
                List.of()));

        SandboxExecutionResult result = service.execute(new SandboxExecutionCommand(
                session.sessionId(),
                "convert",
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.SUCCEEDED, result.execution().status());
        assertEquals(1, result.artifacts().size());
        assertEquals(2, artifactPort.saved.size());
        assertEquals("artifact-clean", artifactPort.saved.get(0).artifactId());
        assertEquals("artifact-secret", artifactPort.saved.get(1).artifactId());
        assertEquals("metadata scan passed", artifactPort.saved.get(0).scanSummary());
        assertEquals(SandboxArtifactScanStatus.BLOCKED, artifactPort.saved.get(1).scanStatus());
        assertEquals("sensitive artifact metadata", artifactPort.saved.get(1).scanSummary());
        assertTrue(artifactPort.saved.get(0).redactionSummaryJson().contains("\"decision\":\"CLEAN\""));
        assertTrue(artifactPort.saved.get(1).redactionSummaryJson().contains("SENSITIVE_METADATA"));
    }

    @Test
    void shouldFailClosedWhenArtifactScannerFails() {
        MemoryArtifactPort artifactPort = new MemoryArtifactPort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                artifactPort,
                new MemorySandboxSessionRepository(),
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                KernelSandboxRuntimeServiceTests::throwScannerFailure,
                null,
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.FILE_CONVERSION,
                false,
                List.of()));

        SandboxExecutionResult result = service.execute(new SandboxExecutionCommand(
                session.sessionId(),
                "convert",
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.SUCCEEDED, result.execution().status());
        assertEquals(0, result.artifacts().size());
        assertEquals(2, artifactPort.saved.size());
        assertTrue(artifactPort.saved.stream()
                .allMatch(artifact -> artifact.scanStatus() == SandboxArtifactScanStatus.BLOCKED));
        assertTrue(artifactPort.saved.stream()
                .allMatch(artifact -> "artifact scanner failed".equals(artifact.scanSummary())));
        assertTrue(artifactPort.saved.stream()
                .allMatch(artifact -> artifact.redactionSummaryJson().contains("SCAN_ERROR")));
    }

    @Test
    void shouldCopyPromptVisibleFileArtifactToObjectStorageBeforeSaving(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("answer.txt");
        Files.writeString(output, "artifact marker", StandardCharsets.UTF_8);
        MemoryArtifactPort artifactPort = new MemoryArtifactPort();
        RecordingObjectStoragePort objectStorage = new RecordingObjectStoragePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(List.of(fileArtifact("artifact-file", output))),
                artifactPort,
                new MemorySandboxSessionRepository(),
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                new DefaultSandboxArtifactScannerPort(),
                objectStorage,
                null,
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        SandboxExecutionResult result = service.execute(new SandboxExecutionCommand(
                session.sessionId(),
                "print('hello')",
                false,
                List.of()));

        assertEquals(1, result.artifacts().size());
        assertEquals(1, artifactPort.saved.size());
        assertEquals("sandbox-artifacts", objectStorage.buckets.get(0));
        assertEquals("artifact marker", new String(objectStorage.uploadedBytes, StandardCharsets.UTF_8));
        assertEquals("local://sandbox-artifacts/answer.txt", artifactPort.saved.get(0).objectUri());
        assertEquals("metadata scan passed", artifactPort.saved.get(0).scanSummary());
        assertTrue(artifactPort.saved.get(0).redactionSummaryJson().contains("\"contentScanned\":true"));
        assertEquals("local://sandbox-artifacts/answer.txt", result.artifacts().get(0).objectUri());
    }

    @Test
    void shouldCopyDownloadOnlyVideoArtifactToObjectStorageWithoutPromptExposure(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("browser-video.webm");
        Files.write(output, new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3});
        MemoryArtifactPort artifactPort = new MemoryArtifactPort();
        RecordingObjectStoragePort objectStorage = new RecordingObjectStoragePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(List.of(fileArtifact("artifact-video", output, "video/webm"))),
                artifactPort,
                new MemorySandboxSessionRepository(),
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                new DefaultSandboxArtifactScannerPort(),
                objectStorage,
                null,
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.BROWSER_AUTOMATION,
                false,
                List.of()));

        SandboxExecutionResult result = service.execute(new SandboxExecutionCommand(
                session.sessionId(),
                "{}",
                false,
                List.of()));

        assertEquals(0, result.artifacts().size());
        assertEquals(1, artifactPort.saved.size());
        SandboxArtifact saved = artifactPort.saved.get(0);
        assertEquals(SandboxArtifactScanStatus.CLEAN, saved.scanStatus());
        assertEquals("video/webm", saved.mediaType());
        assertFalse(saved.promptVisible());
        assertTrue(saved.downloadable());
        assertEquals(1, objectStorage.uploadCount);
        assertArrayEquals(new byte[]{0x1A, 0x45, (byte) 0xDF, (byte) 0xA3}, objectStorage.uploadedBytes);
        assertEquals("local://sandbox-artifacts/browser-video.webm", saved.objectUri());
    }

    @Test
    void shouldFailClosedWhenPromptVisibleFileArtifactCannotBeCopied(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("answer.txt");
        Files.writeString(output, "artifact marker", StandardCharsets.UTF_8);
        MemoryArtifactPort artifactPort = new MemoryArtifactPort();
        RecordingObjectStoragePort objectStorage = new RecordingObjectStoragePort();
        objectStorage.failUpload = true;
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(List.of(fileArtifact("artifact-file", output))),
                artifactPort,
                new MemorySandboxSessionRepository(),
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                new DefaultSandboxArtifactScannerPort(),
                objectStorage,
                null,
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        SandboxExecutionResult result = service.execute(new SandboxExecutionCommand(
                session.sessionId(),
                "print('hello')",
                false,
                List.of()));

        assertEquals(0, result.artifacts().size());
        assertEquals(1, artifactPort.saved.size());
        assertEquals(SandboxArtifactScanStatus.BLOCKED, artifactPort.saved.get(0).scanStatus());
        assertEquals(ContextSensitivity.SECRET, artifactPort.saved.get(0).sensitivity());
        assertEquals("artifact storage copy failed", artifactPort.saved.get(0).scanSummary());
        assertTrue(artifactPort.saved.get(0).redactionSummaryJson().contains("STORAGE_COPY_FAILED"));
    }

    @Test
    void shouldNotCopyScannerBlockedFileArtifacts(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("answer.txt");
        Files.writeString(output, "api_key = 'sk-seahorse-secret-1234567890'", StandardCharsets.UTF_8);
        MemoryArtifactPort artifactPort = new MemoryArtifactPort();
        RecordingObjectStoragePort objectStorage = new RecordingObjectStoragePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(List.of(fileArtifact("artifact-secret-file", output))),
                artifactPort,
                new MemorySandboxSessionRepository(),
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                new DefaultSandboxArtifactScannerPort(),
                objectStorage,
                null,
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        SandboxExecutionResult result = service.execute(new SandboxExecutionCommand(
                session.sessionId(),
                "print('hello')",
                false,
                List.of()));

        assertEquals(0, result.artifacts().size());
        assertEquals(1, artifactPort.saved.size());
        assertEquals(0, objectStorage.uploadCount);
        assertEquals(SandboxArtifactScanStatus.BLOCKED, artifactPort.saved.get(0).scanStatus());
        assertEquals(ContextSensitivity.SECRET, artifactPort.saved.get(0).sensitivity());
        assertEquals("sensitive artifact content", artifactPort.saved.get(0).scanSummary());
        assertTrue(artifactPort.saved.get(0).redactionSummaryJson().contains("SECRET"));
        assertFalse(artifactPort.saved.get(0).redactionSummaryJson().contains("sk-seahorse-secret"));
    }

    @Test
    void shouldListPersistedExecutionsForSession() {
        MemorySandboxExecutionRepository executionRepository = new MemorySandboxExecutionRepository();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                new MemorySandboxSessionRepository(),
                executionRepository,
                new EmptySandboxArtifactQueryPort(),
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        SandboxExecutionResult result = service.execute(new SandboxExecutionCommand(
                session.sessionId(),
                "print('hello')",
                false,
                List.of()));

        List<SandboxExecution> executions = service.listExecutions(session.sessionId());

        assertEquals(1, executions.size());
        assertEquals(result.execution(), executions.get(0));
        assertEquals(result.execution(), executionRepository.findExecutionById("exec-1").orElseThrow());
    }

    @Test
    void shouldListRecentSessionsForTenant() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-old",
                "tenant-1",
                "run-old",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW.minusSeconds(60)));
        sessionRepository.saveSession(SandboxSession.created(
                "session-other-tenant",
                "tenant-2",
                "run-other",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW.plusSeconds(60)));
        sessionRepository.saveSession(SandboxSession.created(
                "session-new",
                "tenant-1",
                "run-new",
                SandboxRuntimeType.FILE_CONVERSION,
                NOW.plusSeconds(30)));
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                CLOCK);

        List<SandboxSession> sessions = service.listSessions("tenant-1", 1);

        assertEquals(1, sessions.size());
        assertEquals("session-new", sessions.get(0).sessionId());
    }

    @Test
    void shouldSweepExpiredActiveSessionsAsTimedOut() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        SandboxSession expired = new SandboxSession(
                "session-expired",
                "tenant-1",
                "run-expired",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxExecutionStatus.CREATED,
                SandboxPolicyReasonCode.VALID_REQUEST,
                "python-small",
                NOW.minusSeconds(60),
                NOW.minusSeconds(3600),
                NOW.minusSeconds(3600));
        sessionRepository.saveSession(expired);
        sessionRepository.saveSession(new SandboxSession(
                "session-active",
                "tenant-1",
                "run-active",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxExecutionStatus.CREATED,
                SandboxPolicyReasonCode.VALID_REQUEST,
                "python-small",
                NOW.plusSeconds(60),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)));
        sessionRepository.saveSession(new SandboxSession(
                "session-terminal",
                "tenant-1",
                "run-terminal",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxExecutionStatus.CANCELLED,
                SandboxPolicyReasonCode.VALID_REQUEST,
                "python-small",
                NOW.minusSeconds(30),
                NOW.minusSeconds(3600),
                NOW.minusSeconds(30)));
        sessionRepository.saveSession(new SandboxSession(
                "session-other-tenant",
                "tenant-2",
                "run-other",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxExecutionStatus.CREATED,
                SandboxPolicyReasonCode.VALID_REQUEST,
                "python-small",
                NOW.minusSeconds(30),
                NOW.minusSeconds(3600),
                NOW.minusSeconds(3600)));
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                CLOCK);

        SandboxSessionSweepResult result = service.sweepExpiredSessions("tenant-1", 20);

        assertEquals("tenant-1", result.tenantId());
        assertEquals(NOW, result.sweptAt());
        assertEquals(1, result.matchedCount());
        assertEquals(1, result.closedCount());
        assertEquals(0, result.failedCount());
        assertEquals(List.of("session-expired"), runtime.closedSessionIds);
        SandboxSession saved = sessionRepository.findSessionById("session-expired").orElseThrow();
        assertEquals(SandboxExecutionStatus.TIMED_OUT, saved.status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_TIMED_OUT, saved.reasonCode());
        assertEquals(expired.expiresAt(), saved.expiresAt());
        assertEquals(NOW, saved.updatedAt());
        assertEquals(saved, result.closedSessions().get(0));
        assertEquals(SandboxExecutionStatus.CREATED,
                sessionRepository.findSessionById("session-active").orElseThrow().status());
        assertEquals(SandboxExecutionStatus.CANCELLED,
                sessionRepository.findSessionById("session-terminal").orElseThrow().status());
    }

    @Test
    void shouldSweepOrphanedRuntimeResourcesWithActiveSessionIds() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(new SandboxSession(
                "session-active",
                "tenant-1",
                "run-active",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxExecutionStatus.CREATED,
                SandboxPolicyReasonCode.VALID_REQUEST,
                "python-small",
                NOW.plusSeconds(60),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)));
        sessionRepository.saveSession(new SandboxSession(
                "session-terminal",
                "tenant-1",
                "run-terminal",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxExecutionStatus.CANCELLED,
                SandboxPolicyReasonCode.VALID_REQUEST,
                "python-small",
                NOW.minusSeconds(30),
                NOW.minusSeconds(3600),
                NOW.minusSeconds(30)));
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                CLOCK);

        SandboxRuntimeCleanupResult result = service.sweepOrphanedRuntimeResources();

        assertEquals(Set.of("session-active"), runtime.orphanSweepActiveSessionIds);
        assertEquals(1, result.activeSessionCount());
        assertEquals(1, result.removedWorkspaceCount());
    }

    @Test
    void shouldInspectRuntimeHealthWithActiveSessionIds() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(new SandboxSession(
                "session-active",
                "tenant-1",
                "run-active",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxExecutionStatus.CREATED,
                SandboxPolicyReasonCode.VALID_REQUEST,
                "python-small",
                NOW.plusSeconds(60),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)));
        sessionRepository.saveSession(new SandboxSession(
                "session-terminal",
                "tenant-1",
                "run-terminal",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxExecutionStatus.CANCELLED,
                SandboxPolicyReasonCode.VALID_REQUEST,
                "python-small",
                NOW.minusSeconds(30),
                NOW.minusSeconds(3600),
                NOW.minusSeconds(30)));
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                CLOCK);

        SandboxRuntimeHealth health = service.inspectRuntimeHealth();

        assertEquals(Set.of("session-active"), runtime.healthActiveSessionIds);
        assertEquals(SandboxRuntimeHealth.STATUS_HEALTHY, health.status());
        assertEquals(1, health.activeSessionCount());
        assertEquals(SandboxRuntimeHealth.CAPACITY_UNBOUNDED, health.capacityStatus());
        assertTrue(health.activeSessionCapacityAvailable());
        assertEquals("container", health.runtime());
    }

    @Test
    void shouldExposeDefaultArtifactScannerPolicy() {
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                CLOCK);

        SandboxArtifactScannerPolicy policy = service.inspectArtifactScannerPolicy();

        assertEquals("default-local-bounded", policy.scannerId());
        assertEquals("LOCAL_METADATA_AND_BOUNDED_CONTENT", policy.scannerMode());
        assertTrue(policy.failClosed());
        assertFalse(policy.rawFindingValuesPersisted());
        assertEquals(128, policy.maxArchiveScanEntries());
        assertTrue(policy.blockedCategories().contains("OFFICE_MACRO"));
        assertTrue(policy.unsupportedCapabilities().contains("external virus scanning"));
    }

    @Test
    void shouldReapOrphanedRuntimeContainersWithActiveSessionIdsAndDryRunFlag() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(new SandboxSession(
                "session-active",
                "tenant-1",
                "run-active",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxExecutionStatus.CREATED,
                SandboxPolicyReasonCode.VALID_REQUEST,
                "python-small",
                NOW.plusSeconds(60),
                NOW.minusSeconds(60),
                NOW.minusSeconds(60)));
        sessionRepository.saveSession(new SandboxSession(
                "session-terminal",
                "tenant-1",
                "run-terminal",
                SandboxRuntimeType.CODE_INTERPRETER,
                SandboxExecutionStatus.TIMED_OUT,
                SandboxPolicyReasonCode.RUNTIME_TIMED_OUT,
                "python-small",
                NOW.minusSeconds(30),
                NOW.minusSeconds(3600),
                NOW.minusSeconds(30)));
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                CLOCK);

        SandboxRuntimeContainerReapResult result = service.reapOrphanedRuntimeContainers(false);

        assertFalse(runtime.containerReapDryRun);
        assertEquals(Set.of("session-active"), runtime.containerReapActiveSessionIds);
        assertEquals(1, result.activeSessionCount());
        assertEquals(1, result.reapedContainerCount());
        assertEquals(List.of("seahorse-sandbox-orphan-live"), result.reapedContainerNames());
    }

    @Test
    void shouldAllowPromptVisibleObjectArtifactDownloadDecision() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        SandboxSession session = sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        MemorySandboxArtifactQueryPort artifactQueryPort = new MemorySandboxArtifactQueryPort(storedArtifact(
                "artifact-clean",
                "local://sandbox-artifacts/artifact-clean"));
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                artifactQueryPort,
                CLOCK);

        SandboxArtifactDownloadDecision decision = service.downloadArtifact("artifact-clean");

        assertEquals(session.sessionId(), decision.artifact().sessionId());
        assertEquals("text/plain", decision.contentType());
        assertEquals("artifact-clean.txt", decision.filename());
        assertEquals("local://sandbox-artifacts/artifact-clean", decision.storageRef());
    }

    @Test
    void shouldDescribePromptVisibleObjectArtifactWithDownloadPolicy() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        SandboxSession session = sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new MemorySandboxArtifactQueryPort(storedArtifact(
                        "artifact-clean",
                        "local://sandbox-artifacts/artifact-clean")),
                CLOCK);

        SandboxArtifactDetailDecision decision = service.describeArtifact("artifact-clean");

        assertEquals(session.sessionId(), decision.artifact().sessionId());
        assertEquals("text/plain", decision.contentType());
        assertEquals("artifact-clean.txt", decision.filename());
        assertTrue(decision.downloadable());
        assertNull(decision.downloadBlockedReason());
    }

    @Test
    void shouldDescribeDownloadOnlyVideoArtifactWithDownloadPolicy() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        SandboxSession session = sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.BROWSER_AUTOMATION,
                NOW));
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new MemorySandboxArtifactQueryPort(storedArtifact(
                        "artifact-video",
                        "local://sandbox-artifacts/browser-video.webm",
                        "video/webm")),
                CLOCK);

        SandboxArtifactDetailDecision decision = service.describeArtifact("artifact-video");

        assertEquals(session.sessionId(), decision.artifact().sessionId());
        assertEquals("video/webm", decision.contentType());
        assertEquals("artifact-video.webm", decision.filename());
        assertFalse(decision.artifact().promptVisible());
        assertTrue(decision.downloadable());
        assertNull(decision.downloadBlockedReason());
    }

    @Test
    void shouldDescribeSecretSandboxArtifactAsBlockedForDownload() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new MemorySandboxArtifactQueryPort(storedArtifact(
                        "artifact-secret",
                        "local://sandbox-artifacts/artifact-secret")
                        .withScanDecision(SandboxArtifactScanStatus.CLEAN, ContextSensitivity.SECRET)),
                CLOCK);

        SandboxArtifactDetailDecision decision = service.describeArtifact("artifact-secret");

        assertFalse(decision.downloadable());
        assertEquals("Sandbox artifact is not available for download", decision.downloadBlockedReason());
    }

    @Test
    void shouldDescribeRawFileSandboxArtifactAsBlockedForDownload(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("answer.txt");
        Files.writeString(output, "artifact marker", StandardCharsets.UTF_8);
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new MemorySandboxArtifactQueryPort(fileArtifact("artifact-file", output)
                        .withScanDecision(SandboxArtifactScanStatus.CLEAN, ContextSensitivity.INTERNAL)),
                CLOCK);

        SandboxArtifactDetailDecision decision = service.describeArtifact("artifact-file");

        assertFalse(decision.downloadable());
        assertEquals("Sandbox artifact storage reference is not available through the download endpoint",
                decision.downloadBlockedReason());
    }

    @Test
    void shouldRejectSecretSandboxArtifactDownloadDecision() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new MemorySandboxArtifactQueryPort(storedArtifact(
                        "artifact-secret",
                        "local://sandbox-artifacts/artifact-secret")
                        .withScanDecision(SandboxArtifactScanStatus.CLEAN, ContextSensitivity.SECRET)),
                CLOCK);

        assertThrows(IllegalStateException.class, () -> service.downloadArtifact("artifact-secret"));
    }

    @Test
    void shouldRejectRawFileSandboxArtifactDownloadDecision(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("answer.txt");
        Files.writeString(output, "artifact marker", StandardCharsets.UTF_8);
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new MemorySandboxArtifactQueryPort(fileArtifact("artifact-file", output)
                        .withScanDecision(SandboxArtifactScanStatus.CLEAN, ContextSensitivity.INTERNAL)),
                CLOCK);

        assertThrows(IllegalStateException.class, () -> service.downloadArtifact("artifact-file"));
    }

    @Test
    void shouldRejectSandboxArtifactDownloadDecisionWhenSessionIsMissing() {
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                new MemorySandboxSessionRepository(),
                new MemorySandboxExecutionRepository(),
                new MemorySandboxArtifactQueryPort(storedArtifact(
                        "artifact-clean",
                        "local://sandbox-artifacts/artifact-clean")),
                CLOCK);

        assertThrows(IllegalArgumentException.class, () -> service.downloadArtifact("artifact-clean"));
    }

    @Test
    void shouldDelegateCloseToRuntimeAndPersistClosedSession() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        SandboxSession closed = service.close(session.sessionId());

        assertTrue(runtime.closeSessionCalled);
        assertEquals(SandboxExecutionStatus.CANCELLED, closed.status());
        assertEquals(NOW.plusSeconds(5), closed.updatedAt());
        assertEquals(closed, sessionRepository.findSessionById(session.sessionId()).orElseThrow());
    }

    @Test
    void shouldNotDelegateCloseForTerminalSession() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.deny(SandboxPolicyReasonCode.NETWORK_DENIED_BY_DEFAULT),
                runtime,
                new MemoryArtifactPort(),
                CLOCK);
        SandboxSession failedSession = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                true,
                List.of("api.example.com")));

        SandboxSession closed = service.close(failedSession.sessionId());

        assertFalse(runtime.closeSessionCalled);
        assertEquals(SandboxExecutionStatus.FAILED, closed.status());
    }

    @Test
    void shouldWriteRedactedAuditEventsForSessionAndTerminalExecution() {
        RecordingAuditEventRepository auditRepository = new RecordingAuditEventRepository();
        KernelAuditLedgerService auditLedger = new KernelAuditLedgerService(
                auditRepository,
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED);
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                new MemorySandboxSessionRepository(),
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                auditLedger,
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        service.execute(new SandboxExecutionCommand(
                session.sessionId(),
                "print('secret-token')",
                false,
                List.of()));

        List<AuditEventType> eventTypes = auditRepository.events.stream()
                .map(AuditEvent::eventType)
                .toList();
        assertTrue(eventTypes.contains(AuditEventType.SANDBOX_SESSION_CREATED));
        assertTrue(eventTypes.contains(AuditEventType.SANDBOX_EXECUTION_FINISHED));
        assertTrue(auditRepository.events.stream()
                .map(AuditEvent::redactedPayload)
                .noneMatch(payload -> payload.contains("secret-token")));
    }

    @Test
    void shouldWriteSessionClosedAuditEventWhenClosingRuntimeSession() {
        RecordingAuditEventRepository auditRepository = new RecordingAuditEventRepository();
        KernelAuditLedgerService auditLedger = new KernelAuditLedgerService(
                auditRepository,
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED);
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                new MemorySandboxSessionRepository(),
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                auditLedger,
                CLOCK);
        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        service.close(session.sessionId());

        List<AuditEventType> eventTypes = auditRepository.events.stream()
                .map(AuditEvent::eventType)
                .toList();
        assertTrue(eventTypes.contains(AuditEventType.SANDBOX_SESSION_CREATED));
        assertTrue(eventTypes.contains(AuditEventType.SANDBOX_SESSION_CLOSED));
    }

    private static final class RecordingSandboxRuntimePort implements SandboxRuntimePort {

        private final List<SandboxArtifact> artifacts;
        private boolean createSessionCalled;
        private boolean executeCalled;
        private boolean closeSessionCalled;
        private SandboxSessionRequest createSessionRequest;
        private final List<String> closedSessionIds = new ArrayList<>();
        private Set<String> orphanSweepActiveSessionIds = Set.of();
        private Set<String> healthActiveSessionIds = Set.of();
        private Set<String> containerReapActiveSessionIds = Set.of();
        private boolean containerReapDryRun = true;
        private SandboxRuntimeHealth healthResponse;

        private RecordingSandboxRuntimePort() {
            this(List.of(
                    SandboxTestArtifacts.clean("artifact-clean"),
                    SandboxTestArtifacts.secret("artifact-secret")));
        }

        private RecordingSandboxRuntimePort(List<SandboxArtifact> artifacts) {
            this.artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        }

        @Override
        public SandboxSession createSession(SandboxSessionRequest request) {
            createSessionCalled = true;
            createSessionRequest = request;
            return SandboxSession.created(
                    "session-1",
                    request.tenantId(),
                    request.runId(),
                    request.runtimeType(),
                    request.profileId(),
                    request.expiresAt(),
                    NOW);
        }

        @Override
        public SandboxExecutionResult execute(SandboxExecutionRequest request) {
            executeCalled = true;
            SandboxExecution execution = SandboxExecution.created(
                    "exec-1",
                    request.session().sessionId(),
                    request.session().runtimeType(),
                    NOW)
                    .markRunning(NOW)
                    .markSucceeded(NOW, "converted");
            return SandboxExecutionResult.succeeded(
                    execution,
                    artifacts);
        }

        @Override
        public SandboxSession closeSession(SandboxSession session) {
            closeSessionCalled = true;
            closedSessionIds.add(session.sessionId());
            return session.closed(NOW.plusSeconds(5));
        }

        @Override
        public SandboxRuntimeCleanupResult sweepOrphanedResources(Set<String> activeSessionIds) {
            orphanSweepActiveSessionIds = activeSessionIds == null ? Set.of() : Set.copyOf(activeSessionIds);
            return new SandboxRuntimeCleanupResult(
                    NOW,
                    orphanSweepActiveSessionIds.size(),
                    1,
                    0,
                    0,
                    1,
                    0,
                    List.of("sandbox_container_orphan"),
                    List.of());
        }

        @Override
        public SandboxRuntimeHealth inspectHealth(Set<String> activeSessionIds) {
            healthActiveSessionIds = activeSessionIds == null ? Set.of() : Set.copyOf(activeSessionIds);
            if (healthResponse != null) {
                return healthResponse;
            }
            return new SandboxRuntimeHealth(
                    NOW,
                    "container",
                    "docker",
                    SandboxRuntimeHealth.STATUS_HEALTHY,
                    true,
                    true,
                    1024L,
                    0L,
                    true,
                    SandboxRuntimeHealth.DISK_UNBOUNDED,
                    healthActiveSessionIds.size(),
                    0,
                    0,
                    true,
                    SandboxRuntimeHealth.CAPACITY_UNBOUNDED,
                    0,
                    0,
                    0,
                    0,
                    List.of(),
                    List.of(),
                    List.of());
        }

        @Override
        public SandboxRuntimeContainerReapResult reapOrphanedContainers(Set<String> activeSessionIds, boolean dryRun) {
            containerReapActiveSessionIds = activeSessionIds == null ? Set.of() : Set.copyOf(activeSessionIds);
            containerReapDryRun = dryRun;
            return new SandboxRuntimeContainerReapResult(
                    NOW,
                    dryRun,
                    containerReapActiveSessionIds.size(),
                    1,
                    0,
                    1,
                    0,
                    dryRun ? 0 : 1,
                    0,
                    List.of(),
                    List.of("seahorse-sandbox-orphan-live"),
                    dryRun ? List.of() : List.of("seahorse-sandbox-orphan-live"),
                    List.of(),
                    List.of());
        }
    }

    private static SandboxArtifact fileArtifact(String artifactId, Path path) {
        return fileArtifact(artifactId, path, "text/plain");
    }

    private static SandboxArtifact fileArtifact(String artifactId, Path path, String mediaType) {
        return new SandboxArtifact(
                artifactId,
                "session-1",
                "exec-1",
                path.toUri().toString(),
                mediaType,
                SandboxArtifactScanStatus.PENDING,
                ContextSensitivity.INTERNAL,
                NOW);
    }

    private static SandboxArtifact storedArtifact(String artifactId, String objectUri) {
        return storedArtifact(artifactId, objectUri, "text/plain");
    }

    private static SandboxArtifact storedArtifact(String artifactId, String objectUri, String mediaType) {
        return new SandboxArtifact(
                artifactId,
                "session-1",
                "exec-1",
                objectUri,
                mediaType,
                SandboxArtifactScanStatus.CLEAN,
                ContextSensitivity.INTERNAL,
                NOW);
    }

    private static final class MemoryArtifactPort implements SandboxArtifactPort {

        private final List<SandboxArtifact> saved = new ArrayList<>();

        @Override
        public SandboxArtifact save(SandboxArtifact artifact) {
            saved.add(artifact);
            return artifact;
        }
    }

    private static final class RecordingObjectStoragePort implements ObjectStoragePort {

        private final List<String> buckets = new ArrayList<>();
        private byte[] uploadedBytes = new byte[0];
        private int uploadCount;
        private boolean failUpload;

        @Override
        public void ensureBucket(String bucketName) {
            buckets.add(bucketName);
        }

        @Override
        public StoredObject upload(String bucketName,
                                   InputStream content,
                                   long size,
                                   String originalFilename,
                                   String contentType) {
            if (failUpload) {
                throw new IllegalStateException("object storage unavailable");
            }
            try {
                uploadCount++;
                uploadedBytes = content.readAllBytes();
                return new StoredObject(
                        "local://" + bucketName + "/" + originalFilename,
                        contentType,
                        size,
                        originalFilename);
            } catch (Exception ex) {
                throw new IllegalStateException("read upload content failed", ex);
            }
        }

        @Override
        public InputStream openStream(String url) {
            return new ByteArrayInputStream(uploadedBytes);
        }

        @Override
        public void deleteByUrl(String url) {
        }
    }

    private static final class RecordingAuditEventRepository implements AuditEventRepositoryPort {

        private final List<AuditEvent> events = new ArrayList<>();

        @Override
        public AuditEvent save(AuditEvent event) {
            events.add(event);
            return event;
        }

        @Override
        public Optional<AuditEvent> findById(String auditId) {
            return events.stream()
                    .filter(event -> event.auditId().equals(auditId))
                    .findFirst();
        }

        @Override
        public AuditEventPage page(AuditEventQuery query) {
            return new AuditEventPage(events, events.size(), query.size(), query.current(), events.isEmpty() ? 0 : 1);
        }
    }

    private static SandboxArtifactScanResult throwScannerFailure(SandboxArtifactScanRequest request) {
        throw new IllegalStateException("scanner unavailable");
    }

    private static final class MemorySandboxSessionRepository implements SandboxSessionRepositoryPort {

        private final Map<String, SandboxSession> store = new ConcurrentHashMap<>();

        @Override
        public SandboxSession saveSession(SandboxSession session) {
            store.put(session.sessionId(), session);
            return session;
        }

        @Override
        public Optional<SandboxSession> findSessionById(String sessionId) {
            return Optional.ofNullable(store.get(sessionId));
        }

        @Override
        public List<SandboxSession> listSessionsByTenant(String tenantId, int limit) {
            return store.values().stream()
                    .filter(session -> session.tenantId().equals(tenantId))
                    .sorted(Comparator.comparing(SandboxSession::updatedAt)
                            .thenComparing(SandboxSession::createdAt)
                            .thenComparing(SandboxSession::sessionId)
                            .reversed())
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<SandboxSession> listExpiredActiveSessions(String tenantId, Instant now, int limit) {
            return store.values().stream()
                    .filter(session -> session.tenantId().equals(tenantId))
                    .filter(session -> !session.status().isTerminal())
                    .filter(session -> !session.expiresAt().isAfter(now))
                    .sorted(Comparator.comparing(SandboxSession::expiresAt)
                            .thenComparing(SandboxSession::createdAt)
                            .thenComparing(SandboxSession::sessionId))
                    .limit(limit)
                    .toList();
        }

        @Override
        public Set<String> listActiveSessionIds() {
            return store.values().stream()
                    .filter(session -> !session.status().isTerminal())
                    .map(SandboxSession::sessionId)
                    .collect(java.util.stream.Collectors.toUnmodifiableSet());
        }
    }

    private static final class MemorySandboxExecutionRepository implements SandboxExecutionRepositoryPort {

        private final Map<String, SandboxExecution> store = new ConcurrentHashMap<>();

        @Override
        public SandboxExecution saveExecution(SandboxExecution execution) {
            store.put(execution.executionId(), execution);
            return execution;
        }

        @Override
        public Optional<SandboxExecution> findExecutionById(String executionId) {
            return Optional.ofNullable(store.get(executionId));
        }

        @Override
        public List<SandboxExecution> listExecutionsBySession(String sessionId) {
            return store.values().stream()
                    .filter(execution -> execution.sessionId().equals(sessionId))
                    .sorted(Comparator.comparing(SandboxExecution::createdAt)
                            .thenComparing(SandboxExecution::executionId))
                    .toList();
        }
    }

    private static final class MemorySandboxArtifactQueryPort implements SandboxArtifactQueryPort {

        private final Map<String, SandboxArtifact> artifacts = new ConcurrentHashMap<>();

        private MemorySandboxArtifactQueryPort(SandboxArtifact... artifacts) {
            for (SandboxArtifact artifact : artifacts) {
                this.artifacts.put(artifact.artifactId(), artifact);
            }
        }

        @Override
        public Optional<SandboxArtifact> findArtifactById(String artifactId) {
            return Optional.ofNullable(artifacts.get(artifactId));
        }

        @Override
        public List<SandboxArtifact> listArtifactsBySession(String sessionId) {
            return artifacts.values().stream()
                    .filter(artifact -> artifact.sessionId().equals(sessionId))
                    .sorted(Comparator.comparing(SandboxArtifact::createdAt)
                            .thenComparing(SandboxArtifact::artifactId))
                    .toList();
        }

        @Override
        public List<SandboxArtifact> listPromptVisibleBySession(String sessionId) {
            return listArtifactsBySession(sessionId).stream()
                    .filter(SandboxArtifact::promptVisible)
                    .toList();
        }
    }

    private static final class EmptySandboxArtifactQueryPort implements SandboxArtifactQueryPort {

        @Override
        public Optional<SandboxArtifact> findArtifactById(String artifactId) {
            return Optional.empty();
        }

        @Override
        public List<SandboxArtifact> listArtifactsBySession(String sessionId) {
            return List.of();
        }

        @Override
        public List<SandboxArtifact> listPromptVisibleBySession(String sessionId) {
            return List.of();
        }
    }
}

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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDetailDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
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
import java.util.concurrent.ConcurrentHashMap;

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
        assertEquals(SandboxArtifactScanStatus.BLOCKED, artifactPort.saved.get(1).scanStatus());
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
        assertEquals("local://sandbox-artifacts/answer.txt", result.artifacts().get(0).objectUri());
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
    }

    @Test
    void shouldNotCopyScannerBlockedFileArtifacts(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("secret-token.txt");
        Files.writeString(output, "secret marker", StandardCharsets.UTF_8);
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
            return SandboxSession.created(
                    "session-1",
                    request.tenantId(),
                    request.runId(),
                    request.runtimeType(),
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
            return session.closed(NOW.plusSeconds(5));
        }
    }

    private static SandboxArtifact fileArtifact(String artifactId, Path path) {
        return new SandboxArtifact(
                artifactId,
                "session-1",
                "exec-1",
                path.toUri().toString(),
                "text/plain",
                SandboxArtifactScanStatus.PENDING,
                ContextSensitivity.INTERNAL,
                NOW);
    }

    private static SandboxArtifact storedArtifact(String artifactId, String objectUri) {
        return new SandboxArtifact(
                artifactId,
                "session-1",
                "exec-1",
                objectUri,
                "text/plain",
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

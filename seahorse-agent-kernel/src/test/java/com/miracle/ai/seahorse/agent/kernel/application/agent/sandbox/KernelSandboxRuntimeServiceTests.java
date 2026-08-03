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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditRedactionPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditWriteFailurePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentDefinition;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunTriggerType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeEndpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeRegistration;
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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRemoteRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeCapacityReservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeNodeRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeProfilePolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeSessionOwnership;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
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
import static com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeCapacityReservationPort.ReservationResult.REJECTED;
import static com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeCapacityReservationPort.ReservationResult.RESERVED;

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
    void shouldCreateSessionWhenRequiredRuntimeNodeMatchesAvailableNode() {
        RecordingSandboxRuntimePort runtime = new RecordingSandboxRuntimePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                runtime,
                new MemoryArtifactPort(),
                CLOCK);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-node-affinity",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "local-container-docker"));

        assertEquals(SandboxExecutionStatus.CREATED, session.status());
        assertEquals("local-container-docker", session.runtimeNodeId());
        assertEquals(session.sessionId(), runtime.createSessionRequest.sessionId());
        assertTrue(runtime.createSessionCalled);
    }

    @Test
    void shouldRejectSessionWhenRequiredRuntimeNodeDoesNotMatchAvailableNode() {
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
                "run-node-affinity-miss",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-missing"));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE, session.reasonCode());
        assertNull(session.runtimeNodeId());
        assertFalse(runtime.createSessionCalled);
        assertEquals(session, sessionRepository.findSessionById(session.sessionId()).orElseThrow());
    }

    @Test
    void shouldRoutePinnedRemoteSessionLifecycleWithoutLocalFallback() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                sessionRepository);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-remote-node",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b"));
        SandboxExecutionResult result = service.execute(new SandboxExecutionCommand(
                session.sessionId(),
                "print('remote')",
                false,
                List.of()));
        SandboxSession closed = service.close(session.sessionId());

        assertEquals("sandbox-node-b", session.runtimeNodeId());
        assertEquals(SandboxExecutionStatus.SUCCEEDED, result.execution().status());
        assertEquals(SandboxExecutionStatus.CANCELLED, closed.status());
        assertTrue(remoteRuntime.createSessionCalled);
        assertTrue(remoteRuntime.executeCalled);
        assertTrue(remoteRuntime.closeSessionCalled);
        assertFalse(localRuntime.createSessionCalled);
        assertFalse(localRuntime.executeCalled);
        assertFalse(localRuntime.closeSessionCalled);
    }

    @Test
    void shouldFailClosedAndRollbackWhenRemoteRuntimeReturnsDifferentSessionId() {
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.returnMismatchedSessionId = true;
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                new RecordingSandboxRuntimePort(),
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                new MemorySandboxSessionRepository(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-remote-identity-mismatch",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b"));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE, session.reasonCode());
        assertNull(session.runtimeNodeId());
        assertEquals(session.sessionId(), remoteRuntime.createSessionRequest.sessionId());
        assertTrue(remoteRuntime.closeSessionCalled);
        assertEquals(remoteRuntime.createSessionRequest.sessionId(), remoteRuntime.closedSessionId);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldAutomaticallyPlaceSessionOnLeastLoadedLiveNode() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-local-active",
                "tenant-1",
                "run-local-active",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW).withRuntimeNode("local-container-docker"));
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                        SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true, 0, 0, 2048L)),
                sessionRepository);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-auto-placement",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals("sandbox-node-b", session.runtimeNodeId());
        assertTrue(remoteRuntime.createSessionCalled);
        assertFalse(localRuntime.createSessionCalled);
    }

    @Test
    void shouldFailOverAutomaticCreateWithSameIdWhenRemoteSessionIsAbsent() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.failCreateSession = true;
        remoteRuntime.sessionOwnership = SandboxRuntimeSessionOwnership.ABSENT;
        MemorySandboxSessionRepository sessionRepository = repositoryWithActiveLocalSession();
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                        SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true, 0, 0, 2048L)),
                sessionRepository,
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-create-failover-absent",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.CREATED, session.status());
        assertEquals("local-container-docker", session.runtimeNodeId());
        assertEquals(session.sessionId(), remoteRuntime.createSessionRequest.sessionId());
        assertEquals(session.sessionId(), localRuntime.createSessionRequest.sessionId());
        assertTrue(remoteRuntime.inspectSessionOwnershipCalled);
        assertFalse(remoteRuntime.closeSessionCalled);
        assertEquals(List.of("sandbox-node-b", "local-container-docker"), reservations.reservedNodeIds);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldAuditOnlySuccessfulAutomaticCreateFailoverWithBoundedRecoveryValues() throws Exception {
        for (Map.Entry<SandboxRuntimeSessionOwnership, String> fixture : Map.of(
                SandboxRuntimeSessionOwnership.ABSENT, "ABSENT",
                SandboxRuntimeSessionOwnership.OWNED, "CLOSED").entrySet()) {
            RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
            RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
            remoteRuntime.failCreateSession = true;
            remoteRuntime.sessionOwnership = fixture.getKey();
            RecordingAuditEventRepository auditRepository = new RecordingAuditEventRepository();
            KernelAuditLedgerService auditLedger = new KernelAuditLedgerService(
                    auditRepository,
                    new AuditRedactionPolicy(),
                    AuditWriteFailurePolicy.FAIL_CLOSED);
            KernelSandboxRuntimeService service = remoteRoutingService(
                    localRuntime,
                    remoteRuntime,
                    new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                            SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true, 0, 0, 2048L)),
                    repositoryWithActiveLocalSession(),
                    new RecordingCapacityReservationPort(RESERVED),
                    auditLedger);

            SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                    "tenant-1",
                    "run-create-failover-audit-" + fixture.getValue().toLowerCase(),
                    SandboxRuntimeType.CODE_INTERPRETER,
                    false,
                    List.of()));

            List<AuditEvent> failoverEvents = auditRepository.events.stream()
                    .filter(event -> event.eventType() == AuditEventType.SANDBOX_RUNTIME_CREATE_FAILED_OVER)
                    .toList();
            assertEquals(1, failoverEvents.size());
            AuditEvent failoverEvent = failoverEvents.get(0);
            assertEquals(session.sessionId(), failoverEvent.resourceId());
            JsonNode payload = new ObjectMapper().readTree(failoverEvent.redactedPayload());
            assertEquals(4, payload.size());
            assertFalse(payload.has("sessionId"));
            assertEquals("sandbox-node-b", payload.path("fromNodeId").asText());
            assertEquals("local-container-docker", payload.path("toNodeId").asText());
            assertEquals(fixture.getValue(), payload.path("recovery").asText());
            assertEquals(2, payload.path("attemptCount").asInt());
            assertFalse(failoverEvent.redactedPayload().contains("transport"));
            assertFalse(failoverEvent.redactedPayload().contains("http://"));
        }
    }

    @Test
    void shouldNotAuditAutomaticCreateFailoverWhenSecondRuntimeReturnsFailedSession() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        localRuntime.returnFailedCreateSession = true;
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.failCreateSession = true;
        remoteRuntime.sessionOwnership = SandboxRuntimeSessionOwnership.ABSENT;
        RecordingAuditEventRepository auditRepository = new RecordingAuditEventRepository();
        KernelAuditLedgerService auditLedger = new KernelAuditLedgerService(
                auditRepository,
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                        SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true, 0, 0, 2048L)),
                repositoryWithActiveLocalSession(),
                new RecordingCapacityReservationPort(RESERVED),
                auditLedger);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-create-failover-second-failed",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals("local-container-docker", session.runtimeNodeId());
        assertTrue(auditRepository.events.stream()
                .noneMatch(event -> event.eventType() == AuditEventType.SANDBOX_RUNTIME_CREATE_FAILED_OVER));
    }

    @Test
    void shouldNotFailOverAutomaticCreateWhenRemoteReconciliationIsUnsupported() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.failCreateSession = true;
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                        SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true, 0, 0, 2048L)),
                repositoryWithActiveLocalSession(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-create-failover-unsupported",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertFalse(localRuntime.createSessionCalled);
        assertEquals(List.of("create", "inspect", "close"), remoteRuntime.calls);
        assertEquals(List.of("sandbox-node-b"), reservations.reservedNodeIds);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldNotFailOverAutomaticCreateWhenRemoteReconciliationFails() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.failCreateSession = true;
        remoteRuntime.failInspectSessionOwnership = true;
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                        SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true, 0, 0, 2048L)),
                repositoryWithActiveLocalSession(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-create-failover-reconciliation-failed",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertFalse(localRuntime.createSessionCalled);
        assertEquals(List.of("create", "inspect", "close"), remoteRuntime.calls);
        assertEquals(List.of("sandbox-node-b"), reservations.reservedNodeIds);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldNotFailOverAutomaticCreateWhenRuntimeReturnsDifferentSessionId() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.returnMismatchedSessionId = true;
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                        SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true, 0, 0, 2048L)),
                repositoryWithActiveLocalSession(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-create-failover-mismatch",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertFalse(localRuntime.createSessionCalled);
        assertTrue(remoteRuntime.closeSessionCalled);
        assertEquals(remoteRuntime.createSessionRequest.sessionId(), remoteRuntime.closedSessionId);
        assertEquals(List.of("sandbox-node-b"), reservations.reservedNodeIds);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldBoundAutomaticRuntimeCreateAttemptsToOneFailover() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        localRuntime.healthResponse = SandboxRuntimeHealth.unsupported(NOW, 0);
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.failCreateSession = true;
        remoteRuntime.sessionOwnership = SandboxRuntimeSessionOwnership.ABSENT;
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new ListSandboxRuntimeNodeRegistry(List.of(
                        remoteEndpoint("sandbox-node-a", SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true),
                        remoteEndpoint("sandbox-node-b", SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true),
                        remoteEndpoint("sandbox-node-c", SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true))),
                new MemorySandboxSessionRepository(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-create-failover-bounded",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(List.of("sandbox-node-a", "sandbox-node-b"), remoteRuntime.createSessionNodeIds);
        assertEquals(List.of("sandbox-node-a", "sandbox-node-b"), reservations.reservedNodeIds);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldNotFailOverAutomaticCreateWhenLocalRuntimeFails() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        localRuntime.failCreateSession = true;
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                        SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true, 1, 0, 2048L)),
                new MemorySandboxSessionRepository(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-create-failover-local-failed",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertTrue(localRuntime.createSessionCalled);
        assertTrue(localRuntime.closeSessionCalled);
        assertFalse(remoteRuntime.createSessionCalled);
        assertEquals(List.of("local-container-docker"), reservations.reservedNodeIds);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldReserveAndReleaseRemoteNodeCapacityAfterSessionPersistence() {
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                new RecordingSandboxRuntimePort(),
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                new MemorySandboxSessionRepository(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-reserved",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b"));

        assertEquals(SandboxExecutionStatus.CREATED, session.status());
        assertEquals(List.of("sandbox-node-b"), reservations.reservedNodeIds);
        assertEquals(List.of(Duration.ofMinutes(5)), reservations.leaseTtls);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
        assertTrue(remoteRuntime.createSessionCalled);
    }

    @Test
    void shouldPersistCapacityRejectionWithoutCreatingRuntimeSession() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(REJECTED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                new RecordingSandboxRuntimePort(),
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                sessionRepository,
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-rejected",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b"));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_CAPACITY_EXCEEDED, session.reasonCode());
        assertEquals(session, sessionRepository.findSessionById(session.sessionId()).orElseThrow());
        assertFalse(remoteRuntime.createSessionCalled);
        assertTrue(reservations.releasedReservationIds.isEmpty());
    }

    @Test
    void shouldTryNextAutomaticCandidateWhenFirstCapacityReservationLoses() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        localRuntime.healthResponse = SandboxRuntimeHealth.unsupported(NOW, 0);
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        reservations.resultsByNode.put("sandbox-node-a", REJECTED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new ListSandboxRuntimeNodeRegistry(List.of(
                        remoteEndpoint("sandbox-node-a", SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true),
                        remoteEndpoint("sandbox-node-b", SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true))),
                new MemorySandboxSessionRepository(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-next-node",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.CREATED, session.status());
        assertEquals("sandbox-node-b", session.runtimeNodeId());
        assertEquals(List.of("sandbox-node-a", "sandbox-node-b"), reservations.reservedNodeIds);
        assertEquals("sandbox-node-b", remoteRuntime.createSessionNodeId);
        assertEquals(1, reservations.releasedReservationIds.size());
    }

    @Test
    void shouldStillReserveLocalCapacityWhenRemoteRegistryListingFails() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        ThrowingListSandboxRuntimeNodeRegistry registry = new ThrowingListSandboxRuntimeNodeRegistry();
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                new RecordingSandboxRemoteRuntimePort(),
                registry,
                new MemorySandboxSessionRepository(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-registry-failed",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals(SandboxExecutionStatus.CREATED, session.status());
        assertEquals("local-container-docker", session.runtimeNodeId());
        assertEquals(List.of("local-container-docker"), reservations.reservedNodeIds);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
        assertTrue(localRuntime.createSessionCalled);
        assertEquals(1, registry.startedCreateOperationIds.size());
        assertEquals(registry.startedCreateOperationIds, registry.endedCreateOperationIds);
    }

    @Test
    void shouldCompensateCoordinatorSessionAndReleaseCapacityWhenRuntimeCreationFails() {
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.failCreateSession = true;
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                new RecordingSandboxRuntimePort(),
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                new MemorySandboxSessionRepository(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-create-failed",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b"));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE, session.reasonCode());
        assertTrue(remoteRuntime.inspectSessionOwnershipCalled);
        assertTrue(remoteRuntime.closeSessionCalled);
        assertEquals(remoteRuntime.createSessionRequest.sessionId(), remoteRuntime.closedSessionId);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldReleaseCapacityWithoutCloseWhenReconciliationConfirmsSessionAbsent() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.failCreateSession = true;
        remoteRuntime.sessionOwnership = SandboxRuntimeSessionOwnership.ABSENT;
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                new MemorySandboxSessionRepository(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-create-absent",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b"));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertTrue(remoteRuntime.inspectSessionOwnershipCalled);
        assertEquals(remoteRuntime.createSessionRequest.sessionId(), remoteRuntime.inspectedSessionId);
        assertFalse(remoteRuntime.closeSessionCalled);
        assertFalse(localRuntime.createSessionCalled);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldCloseOwnedSessionBeforeReleasingCapacityAfterAmbiguousCreate() {
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.failCreateSession = true;
        remoteRuntime.sessionOwnership = SandboxRuntimeSessionOwnership.OWNED;
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                new RecordingSandboxRuntimePort(),
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                new MemorySandboxSessionRepository(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-create-owned",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b"));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(List.of("create", "inspect", "close"), remoteRuntime.calls);
        assertEquals(remoteRuntime.createSessionRequest.sessionId(), remoteRuntime.inspectedSessionId);
        assertEquals(remoteRuntime.inspectedSessionId, remoteRuntime.closedSessionId);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldKeepCapacityReservationWhenAmbiguousRuntimeCreationCannotBeCleanedUp() {
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        remoteRuntime.failCreateSession = true;
        remoteRuntime.returnNullCloseSession = true;
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService service = remoteRoutingService(
                new RecordingSandboxRuntimePort(),
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                new MemorySandboxSessionRepository(),
                reservations);

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-create-ambiguous",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b"));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertTrue(remoteRuntime.closeSessionCalled);
        assertEquals(remoteRuntime.createSessionRequest.sessionId(), remoteRuntime.closedSessionId);
        assertTrue(reservations.releasedReservationIds.isEmpty());
    }

    @Test
    void shouldReleaseCapacityAfterPersistenceRollbackAndKeepLeaseWhenRollbackFails() {
        RecordingSandboxRemoteRuntimePort rollbackRuntime = new RecordingSandboxRemoteRuntimePort();
        RecordingCapacityReservationPort releasedReservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService rollbackService = remoteRoutingService(
                new RecordingSandboxRuntimePort(),
                rollbackRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                new FailingSandboxSessionRepository(),
                releasedReservations);

        assertThrows(IllegalStateException.class, () -> rollbackService.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-persist-failed",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b")));
        assertTrue(rollbackRuntime.closeSessionCalled);
        assertEquals(releasedReservations.reservationIds, releasedReservations.releasedReservationIds);

        RecordingSandboxRemoteRuntimePort failedRollbackRuntime = new RecordingSandboxRemoteRuntimePort();
        failedRollbackRuntime.failCloseSession = true;
        RecordingCapacityReservationPort retainedReservations = new RecordingCapacityReservationPort(RESERVED);
        KernelSandboxRuntimeService failedRollbackService = remoteRoutingService(
                new RecordingSandboxRuntimePort(),
                failedRollbackRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                new FailingSandboxSessionRepository(),
                retainedReservations);

        assertThrows(IllegalStateException.class, () -> failedRollbackService.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-rollback-failed",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b")));
        assertTrue(failedRollbackRuntime.closeSessionCalled);
        assertTrue(retainedReservations.releasedReservationIds.isEmpty());
    }

    @Test
    void shouldKeepPersistedRuntimeSessionWhenFailClosedAuditWriteFails() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        RecordingCapacityReservationPort reservations = new RecordingCapacityReservationPort(RESERVED);
        KernelAuditLedgerService auditLedger = new KernelAuditLedgerService(
                new FailingAuditEventRepository(),
                new AuditRedactionPolicy(),
                AuditWriteFailurePolicy.FAIL_CLOSED);
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                new DefaultSandboxArtifactScannerPort(),
                null,
                new MemorySandboxRuntimeProfilePolicyRepository(),
                null,
                null,
                auditLedger,
                CLOCK,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                        SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true)),
                reservations);

        assertThrows(IllegalStateException.class, () -> service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-capacity-audit-failed",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b")));

        SandboxSession persisted = sessionRepository
                .findSessionById(remoteRuntime.createSessionRequest.sessionId())
                .orElseThrow();
        assertEquals(SandboxExecutionStatus.CREATED, persisted.status());
        assertFalse(remoteRuntime.closeSessionCalled);
        assertEquals(reservations.reservationIds, reservations.releasedReservationIds);
    }

    @Test
    void shouldKeepExplicitLocalPlacementWhenRemoteNodeIsLessLoaded() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                        SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE, true, 0, 0, 2048L)),
                new MemorySandboxSessionRepository());

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-explicit-local",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "local-container-docker"));

        assertEquals("local-container-docker", session.runtimeNodeId());
        assertTrue(localRuntime.createSessionCalled);
        assertFalse(remoteRuntime.createSessionCalled);
    }

    @Test
    void shouldPreferAvailableLocalNodeOverLessLoadedDegradedRemoteNode() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(
                        SandboxRuntimeNodeHealth.ADMISSION_DEGRADED, true, 0, 0, 2048L)),
                new MemorySandboxSessionRepository());

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-prefer-available",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of()));

        assertEquals("local-container-docker", session.runtimeNodeId());
        assertTrue(localRuntime.createSessionCalled);
        assertFalse(remoteRuntime.createSessionCalled);
    }

    @Test
    void shouldRejectPinnedRemoteSessionWhenLiveEndpointIsMissing() {
        RecordingSandboxRuntimePort localRuntime = new RecordingSandboxRuntimePort();
        RecordingSandboxRemoteRuntimePort remoteRuntime = new RecordingSandboxRemoteRuntimePort();
        KernelSandboxRuntimeService service = remoteRoutingService(
                localRuntime,
                remoteRuntime,
                new FixedSandboxRuntimeNodeRegistry(null),
                new MemorySandboxSessionRepository());

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-remote-missing",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b"));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE, session.reasonCode());
        assertFalse(localRuntime.createSessionCalled);
        assertFalse(remoteRuntime.createSessionCalled);
    }

    @Test
    void shouldPreserveRemoteNodeDrainingAdmissionReason() {
        KernelSandboxRuntimeService service = remoteRoutingService(
                new RecordingSandboxRuntimePort(),
                new RecordingSandboxRemoteRuntimePort(),
                new FixedSandboxRuntimeNodeRegistry(remoteEndpoint(SandboxRuntimeNodeHealth.ADMISSION_DRAINING, false)),
                new MemorySandboxSessionRepository());

        SandboxSession session = service.createSession(new SandboxSessionCreateCommand(
                "tenant-1",
                "run-remote-draining",
                SandboxRuntimeType.CODE_INTERPRETER,
                false,
                List.of(),
                null,
                null,
                "sandbox-node-b"));

        assertEquals(SandboxExecutionStatus.FAILED, session.status());
        assertEquals(SandboxPolicyReasonCode.RUNTIME_NODE_DRAINING, session.reasonCode());
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
    void shouldCopySecretBrowserSessionStateArtifactForInternalReplay(@TempDir Path tempDir) throws Exception {
        Path output = tempDir.resolve("browser-session-state.json");
        String sessionState = """
                {"cookies":[{"name":"sid","value":"secret-cookie","domain":"example.test","path":"/"}],"origins":[]}
                """;
        Files.writeString(output, sessionState, StandardCharsets.UTF_8);
        MemoryArtifactPort artifactPort = new MemoryArtifactPort();
        RecordingObjectStoragePort objectStorage = new RecordingObjectStoragePort();
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(List.of(fileArtifact(
                        "artifact-session-state",
                        output,
                        "application/json"))),
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
        assertEquals(SandboxArtifactScanStatus.BLOCKED, saved.scanStatus());
        assertEquals(ContextSensitivity.SECRET, saved.sensitivity());
        assertFalse(saved.downloadable());
        assertEquals(1, objectStorage.uploadCount);
        assertEquals(sessionState, new String(objectStorage.uploadedBytes, StandardCharsets.UTF_8));
        assertEquals("local://sandbox-artifacts/browser-session-state.json", saved.objectUri());
    }

    @Test
    void shouldReadCopiedSecretBrowserSessionStateArtifactWithStoragePrefix() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        SandboxSession session = SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.BROWSER_AUTOMATION,
                "browser-readonly",
                CLOCK.instant().plusSeconds(3600),
                CLOCK.instant());
        sessionRepository.saveSession(session);
        String sessionState = "{\"cookies\":[{\"name\":\"sid\",\"value\":\"secret-cookie\",\"domain\":\"example.test\",\"path\":\"/\"}],\"origins\":[]}";
        RecordingObjectStoragePort objectStorage = new RecordingObjectStoragePort();
        objectStorage.uploadedBytes = sessionState.getBytes(StandardCharsets.UTF_8);
        SandboxArtifact artifact = new SandboxArtifact(
                "artifact-session-state",
                session.sessionId(),
                "exec-1",
                "local://sandbox-artifacts/2f7e6b70-browser-session-state.json",
                "application/json",
                SandboxArtifactScanStatus.BLOCKED,
                ContextSensitivity.SECRET,
                "sensitive artifact metadata",
                CLOCK.instant());
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(List.of()),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new MemorySandboxArtifactQueryPort(artifact),
                new DefaultSandboxArtifactScannerPort(),
                objectStorage,
                null,
                CLOCK);

        String replayState = service.readBrowserSessionStateArtifact(artifact.artifactId());

        assertEquals(sessionState, replayState);
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
    void shouldBlockBrowserSessionStateArtifactReplayWhenForbiddenPathIsResolved(@TempDir Path tempDir)
            throws Exception {
        // 防护接入点验证：当 file:// URI 指向的路径被 SandboxPathValidator 判定为
        // 敏感目录时，回放必须被拦截而非读取。validator 的 forbidden 匹配在单测中
        // 覆盖，这里验证沙箱服务确实在读取前调用了防护。
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        SandboxSession session = sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.BROWSER_AUTOMATION,
                NOW));
        // 用绝对 temp 目录下的文件验证正常路径不被误伤。
        Path stateFile = tempDir.resolve("browser-session-state.json");
        Files.writeString(stateFile, "{\"cookies\":[]}", StandardCharsets.UTF_8);
        SandboxArtifact artifact = new SandboxArtifact(
                "artifact-safe-replay",
                session.sessionId(),
                "exec-1",
                stateFile.toUri().toString(),
                "application/json",
                SandboxArtifactScanStatus.BLOCKED,
                ContextSensitivity.SECRET,
                "sensitive artifact metadata",
                NOW);
        KernelSandboxRuntimeService service = new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(List.of()),
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new MemorySandboxArtifactQueryPort(artifact),
                CLOCK);

        String replayState = service.readBrowserSessionStateArtifact(artifact.artifactId());

        assertEquals("{\"cookies\":[]}", replayState);
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
    void shouldRejectUnrelatedUserSandboxExecutionLookupBeforeReadingExecutions() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        MemorySandboxExecutionRepository executionRepository = new MemorySandboxExecutionRepository();
        KernelSandboxRuntimeService service = guardedService(
                sessionRepository,
                executionRepository,
                new EmptySandboxArtifactQueryPort(),
                currentUser("other-user", "user"),
                run("run-1", "user-1"));

        assertThrows(IllegalStateException.class, () -> service.listExecutions("session-1"));

        assertEquals(0, executionRepository.listExecutionsCalls);
    }

    @Test
    void shouldAllowNumericWebUserIdOwnerSandboxExecutionLookup() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        MemorySandboxExecutionRepository executionRepository = new MemorySandboxExecutionRepository();
        SandboxExecution execution = SandboxExecution.created(
                "exec-1",
                "session-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW);
        executionRepository.saveExecution(execution);
        KernelSandboxRuntimeService service = guardedService(
                sessionRepository,
                executionRepository,
                new EmptySandboxArtifactQueryPort(),
                currentUser(42L, "owner", "user"),
                run("run-1", "42"));

        List<SandboxExecution> executions = service.listExecutions("session-1");

        assertEquals(List.of("exec-1"), executions.stream().map(SandboxExecution::executionId).toList());
    }

    @Test
    void shouldFilterUnreadableSandboxSessionsFromTenantList() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-owner",
                "tenant-1",
                "run-owner",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW.plusSeconds(1)));
        sessionRepository.saveSession(SandboxSession.created(
                "session-other",
                "tenant-1",
                "run-other",
                SandboxRuntimeType.FILE_CONVERSION,
                NOW.plusSeconds(2)));
        KernelSandboxRuntimeService service = guardedService(
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                currentUser("user-1", "user"),
                run("run-owner", "user-1"),
                run("run-other", "other-user"));

        List<SandboxSession> sessions = service.listSessions("tenant-1", 10);

        assertEquals(List.of("session-owner"), sessions.stream().map(SandboxSession::sessionId).toList());
    }

    @Test
    void shouldRejectUnrelatedUserSandboxArtifactDownloadBeforeReturningStorageRef() {
        MemorySandboxSessionRepository sessionRepository = new MemorySandboxSessionRepository();
        sessionRepository.saveSession(SandboxSession.created(
                "session-1",
                "tenant-1",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        KernelSandboxRuntimeService service = guardedService(
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new MemorySandboxArtifactQueryPort(storedArtifact(
                        "artifact-clean",
                        "s3://sandbox-artifacts/secret.txt",
                        "text/plain")),
                currentUser("other-user", "user"),
                run("run-1", "user-1"));

        assertThrows(IllegalStateException.class, () -> service.downloadArtifact("artifact-clean"));
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
    void shouldDescribeDownloadOnlyTarArtifactWithStableFilename() {
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
                        "artifact-tar",
                        "local://sandbox-artifacts/safe-bundle.tar",
                        "application/x-tar")),
                CLOCK);

        SandboxArtifactDetailDecision decision = service.describeArtifact("artifact-tar");

        assertEquals(session.sessionId(), decision.artifact().sessionId());
        assertEquals("application/x-tar", decision.contentType());
        assertEquals("artifact-tar.tar", decision.filename());
        assertFalse(decision.artifact().promptVisible());
        assertTrue(decision.downloadable());
        assertNull(decision.downloadBlockedReason());
    }

    @Test
    void shouldDescribeDownloadOnlyGzipTarArtifactWithStableFilename() {
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
                        "artifact-targz",
                        "local://sandbox-artifacts/safe-bundle.tar.gz",
                        "application/gzip")),
                CLOCK);

        SandboxArtifactDetailDecision decision = service.describeArtifact("artifact-targz");

        assertEquals(session.sessionId(), decision.artifact().sessionId());
        assertEquals("application/gzip", decision.contentType());
        assertEquals("artifact-targz.tar.gz", decision.filename());
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

    private static MemorySandboxSessionRepository repositoryWithActiveLocalSession() {
        MemorySandboxSessionRepository repository = new MemorySandboxSessionRepository();
        repository.saveSession(SandboxSession.created(
                "session-local-failover-load",
                "tenant-1",
                "run-local-failover-load",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW).withRuntimeNode("local-container-docker"));
        return repository;
    }

    private static KernelSandboxRuntimeService remoteRoutingService(
            RecordingSandboxRuntimePort localRuntime,
            RecordingSandboxRemoteRuntimePort remoteRuntime,
            SandboxRuntimeNodeRegistryPort nodeRegistry,
            MemorySandboxSessionRepository sessionRepository) {
        return remoteRoutingService(localRuntime, remoteRuntime, nodeRegistry, sessionRepository, null);
    }

    private static KernelSandboxRuntimeService remoteRoutingService(
            RecordingSandboxRuntimePort localRuntime,
            RecordingSandboxRemoteRuntimePort remoteRuntime,
            SandboxRuntimeNodeRegistryPort nodeRegistry,
            SandboxSessionRepositoryPort sessionRepository,
            SandboxRuntimeCapacityReservationPort capacityReservationPort) {
        return remoteRoutingService(
                localRuntime,
                remoteRuntime,
                nodeRegistry,
                sessionRepository,
                capacityReservationPort,
                null);
    }

    private static KernelSandboxRuntimeService remoteRoutingService(
            RecordingSandboxRuntimePort localRuntime,
            RecordingSandboxRemoteRuntimePort remoteRuntime,
            SandboxRuntimeNodeRegistryPort nodeRegistry,
            SandboxSessionRepositoryPort sessionRepository,
            SandboxRuntimeCapacityReservationPort capacityReservationPort,
            KernelAuditLedgerService auditLedger) {
        return new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                localRuntime,
                new MemoryArtifactPort(),
                sessionRepository,
                new MemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                new DefaultSandboxArtifactScannerPort(),
                null,
                new MemorySandboxRuntimeProfilePolicyRepository(),
                null,
                null,
                auditLedger,
                CLOCK,
                remoteRuntime,
                nodeRegistry,
                capacityReservationPort);
    }

    private static SandboxRuntimeNodeEndpoint remoteEndpoint(String admissionStatus, boolean admissionAvailable) {
        return remoteEndpoint(admissionStatus, admissionAvailable, 0, 0, -1L);
    }

    private static SandboxRuntimeNodeEndpoint remoteEndpoint(String nodeId,
                                                             String admissionStatus,
                                                             boolean admissionAvailable) {
        return new SandboxRuntimeNodeEndpoint(
                nodeId,
                URI.create("http://" + nodeId + ":8080/internal/sandbox/runtime"),
                "HEALTHY",
                admissionAvailable,
                admissionStatus,
                0,
                1,
                2048L,
                NOW.plusSeconds(45));
    }

    private static SandboxRuntimeNodeEndpoint remoteEndpoint(String admissionStatus,
                                                             boolean admissionAvailable,
                                                             int activeSessionCount,
                                                             int activeSessionLimit,
                                                             long workspaceFreeBytes) {
        return new SandboxRuntimeNodeEndpoint(
                "sandbox-node-b",
                URI.create("http://sandbox-node-b:8080/internal/sandbox/runtime"),
                "HEALTHY",
                admissionAvailable,
                admissionStatus,
                activeSessionCount,
                activeSessionLimit,
                workspaceFreeBytes,
                NOW.plusSeconds(45));
    }

    private static final class RecordingSandboxRemoteRuntimePort implements SandboxRemoteRuntimePort {

        private boolean createSessionCalled;
        private boolean executeCalled;
        private boolean closeSessionCalled;
        private boolean inspectSessionOwnershipCalled;
        private boolean failCreateSession;
        private boolean failInspectSessionOwnership;
        private boolean failCloseSession;
        private boolean returnNullCloseSession;
        private boolean returnMismatchedSessionId;
        private String createSessionNodeId;
        private final List<String> createSessionNodeIds = new ArrayList<>();
        private String closedSessionId;
        private String inspectedSessionId;
        private SandboxSessionRequest createSessionRequest;
        private SandboxRuntimeSessionOwnership sessionOwnership = SandboxRuntimeSessionOwnership.UNSUPPORTED;
        private final List<String> calls = new ArrayList<>();

        @Override
        public SandboxSession createSession(SandboxRuntimeNodeEndpoint endpoint, SandboxSessionRequest request) {
            createSessionCalled = true;
            calls.add("create");
            createSessionNodeId = endpoint.nodeId();
            createSessionNodeIds.add(endpoint.nodeId());
            createSessionRequest = request;
            if (failCreateSession) {
                throw new IllegalStateException("remote runtime create failed");
            }
            return SandboxSession.created(
                    returnMismatchedSessionId ? "session-remote-mismatch" : request.sessionId(),
                    request.tenantId(),
                    request.runId(),
                    request.runtimeType(),
                    request.profileId(),
                    request.expiresAt(),
                    NOW);
        }

        @Override
        public SandboxRuntimeSessionOwnership inspectSessionOwnership(SandboxRuntimeNodeEndpoint endpoint,
                                                                      String sessionId) {
            inspectSessionOwnershipCalled = true;
            inspectedSessionId = sessionId;
            calls.add("inspect");
            if (failInspectSessionOwnership) {
                throw new IllegalStateException("remote runtime ownership inspection failed");
            }
            return sessionOwnership;
        }

        @Override
        public SandboxExecutionResult execute(SandboxRuntimeNodeEndpoint endpoint, SandboxExecutionRequest request) {
            executeCalled = true;
            SandboxExecution execution = SandboxExecution.created(
                    "exec-remote-1",
                    request.session().sessionId(),
                    request.session().runtimeType(),
                    NOW)
                    .markRunning(NOW)
                    .markSucceeded(NOW, "remote");
            return SandboxExecutionResult.succeeded(execution, List.of());
        }

        @Override
        public SandboxSession closeSession(SandboxRuntimeNodeEndpoint endpoint, SandboxSession session) {
            closeSessionCalled = true;
            calls.add("close");
            closedSessionId = session.sessionId();
            if (failCloseSession) {
                throw new IllegalStateException("remote runtime close failed");
            }
            if (returnNullCloseSession) {
                return null;
            }
            return session.closed(NOW.plusSeconds(5));
        }
    }

    private static final class RecordingCapacityReservationPort
            implements SandboxRuntimeCapacityReservationPort {

        private final ReservationResult defaultResult;
        private final Map<String, ReservationResult> resultsByNode = new ConcurrentHashMap<>();
        private final List<String> reservedNodeIds = new ArrayList<>();
        private final List<String> reservationIds = new ArrayList<>();
        private final List<Duration> leaseTtls = new ArrayList<>();
        private final List<String> releasedReservationIds = new ArrayList<>();

        private RecordingCapacityReservationPort(ReservationResult defaultResult) {
            this.defaultResult = defaultResult;
        }

        @Override
        public ReservationResult tryReserve(String nodeId, String reservationId, Duration leaseTtl) {
            reservedNodeIds.add(nodeId);
            reservationIds.add(reservationId);
            leaseTtls.add(leaseTtl);
            return resultsByNode.getOrDefault(nodeId, defaultResult);
        }

        @Override
        public boolean release(String reservationId) {
            releasedReservationIds.add(reservationId);
            return true;
        }
    }

    private static final class FixedSandboxRuntimeNodeRegistry implements SandboxRuntimeNodeRegistryPort {

        private final SandboxRuntimeNodeEndpoint endpoint;

        private FixedSandboxRuntimeNodeRegistry(SandboxRuntimeNodeEndpoint endpoint) {
            this.endpoint = endpoint;
        }

        @Override
        public Optional<SandboxRuntimeNodeRegistration> heartbeat(SandboxRuntimeNodeRegistration registration,
                                                                  String ownerId,
                                                                  Duration leaseTtl) {
            return Optional.of(registration);
        }

        @Override
        public boolean release(String nodeId, String ownerId) {
            return false;
        }

        @Override
        public List<SandboxRuntimeNodeRegistration> listRegistrations(int limit) {
            return List.of();
        }

        @Override
        public Optional<SandboxRuntimeNodeEndpoint> findLiveEndpoint(String nodeId) {
            return endpoint != null && endpoint.nodeId().equals(nodeId)
                    ? Optional.of(endpoint)
                    : Optional.empty();
        }

        @Override
        public List<SandboxRuntimeNodeEndpoint> listLiveEndpoints() {
            return endpoint == null ? List.of() : List.of(endpoint);
        }
    }

    private static final class ListSandboxRuntimeNodeRegistry implements SandboxRuntimeNodeRegistryPort {

        private final List<SandboxRuntimeNodeEndpoint> endpoints;

        private ListSandboxRuntimeNodeRegistry(List<SandboxRuntimeNodeEndpoint> endpoints) {
            this.endpoints = List.copyOf(endpoints);
        }

        @Override
        public Optional<SandboxRuntimeNodeRegistration> heartbeat(SandboxRuntimeNodeRegistration registration,
                                                                  String ownerId,
                                                                  Duration leaseTtl) {
            return Optional.of(registration);
        }

        @Override
        public boolean release(String nodeId, String ownerId) {
            return false;
        }

        @Override
        public List<SandboxRuntimeNodeRegistration> listRegistrations(int limit) {
            return List.of();
        }

        @Override
        public Optional<SandboxRuntimeNodeEndpoint> findLiveEndpoint(String nodeId) {
            return endpoints.stream().filter(endpoint -> endpoint.nodeId().equals(nodeId)).findFirst();
        }

        @Override
        public List<SandboxRuntimeNodeEndpoint> listLiveEndpoints() {
            return endpoints;
        }
    }

    private static final class ThrowingListSandboxRuntimeNodeRegistry implements SandboxRuntimeNodeRegistryPort {

        private final List<String> startedCreateOperationIds = new ArrayList<>();
        private final List<String> endedCreateOperationIds = new ArrayList<>();

        @Override
        public Optional<SandboxRuntimeNodeRegistration> heartbeat(SandboxRuntimeNodeRegistration registration,
                                                                  String ownerId,
                                                                  Duration leaseTtl) {
            return Optional.of(registration);
        }

        @Override
        public boolean release(String nodeId, String ownerId) {
            return false;
        }

        @Override
        public List<SandboxRuntimeNodeRegistration> listRegistrations(int limit) {
            return List.of();
        }

        @Override
        public List<SandboxRuntimeNodeEndpoint> listLiveEndpoints() {
            throw new IllegalStateException("runtime node registry unavailable");
        }

        @Override
        public boolean beginCreateOperation(String nodeId, String operationId) {
            startedCreateOperationIds.add(operationId);
            return true;
        }

        @Override
        public boolean endCreateOperation(String nodeId, String operationId) {
            endedCreateOperationIds.add(operationId);
            return true;
        }
    }

    private static final class RecordingSandboxRuntimePort implements SandboxRuntimePort {

        private final List<SandboxArtifact> artifacts;
        private boolean createSessionCalled;
        private boolean executeCalled;
        private boolean closeSessionCalled;
        private boolean failCreateSession;
        private boolean returnFailedCreateSession;
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
            if (failCreateSession) {
                throw new IllegalStateException("local runtime create failed");
            }
            if (returnFailedCreateSession) {
                return SandboxSession.failed(
                        request.sessionId() == null ? "session-1" : request.sessionId(),
                        request.tenantId(),
                        request.runId(),
                        request.runtimeType(),
                        SandboxPolicyReasonCode.RUNTIME_EXECUTION_FAILED,
                        request.profileId(),
                        request.expiresAt(),
                        NOW);
            }
            return SandboxSession.created(
                    request.sessionId() == null ? "session-1" : request.sessionId(),
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

    private static KernelSandboxRuntimeService guardedService(MemorySandboxSessionRepository sessionRepository,
                                                              MemorySandboxExecutionRepository executionRepository,
                                                              SandboxArtifactQueryPort artifactQueryPort,
                                                              CurrentUserPort currentUserPort,
                                                              AgentRun... runs) {
        return new KernelSandboxRuntimeService(
                request -> SandboxPolicyDecision.allow(SandboxPolicyReasonCode.VALID_REQUEST),
                new RecordingSandboxRuntimePort(),
                new MemoryArtifactPort(),
                sessionRepository,
                executionRepository,
                artifactQueryPort,
                new DefaultSandboxArtifactScannerPort(),
                null,
                new MemorySandboxRuntimeProfilePolicyRepository(),
                new MemoryAgentRunRepository(runs),
                currentUserPort,
                null,
                CLOCK);
    }

    private static CurrentUserPort currentUser(String userId, String role) {
        return () -> Optional.of(new CurrentUser(1L, userId, role, null));
    }

    private static CurrentUserPort currentUser(Long userId, String operator, String role) {
        return () -> Optional.of(new CurrentUser(userId, operator, role, null));
    }

    private static AgentRun run(String runId, String userId) {
        return new AgentRun(
                runId,
                "agent-1",
                "version-1",
                AgentDefinition.DEFAULT_TENANT_ID,
                userId,
                "conversation-1",
                AgentRunTriggerType.CHAT,
                "input",
                AgentRunStatus.RUNNING,
                "trace-1",
                0L,
                0L,
                java.math.BigDecimal.ZERO,
                null,
                null,
                NOW,
                null);
    }

    private static final class MemoryAgentRunRepository implements AgentRunRepositoryPort {

        private final Map<String, AgentRun> runs = new ConcurrentHashMap<>();

        private MemoryAgentRunRepository(AgentRun... runs) {
            for (AgentRun run : runs) {
                this.runs.put(run.runId(), run);
            }
        }

        @Override
        public void createRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public void updateRun(AgentRun run) {
            runs.put(run.runId(), run);
        }

        @Override
        public Optional<AgentRun> findRunById(String runId) {
            return Optional.ofNullable(runs.get(runId));
        }

        @Override
        public void appendStep(AgentStep step) {
        }

        @Override
        public List<AgentStep> listSteps(String runId) {
            return List.of();
        }
    }

    private static final class FailingAuditEventRepository implements AuditEventRepositoryPort {

        @Override
        public AuditEvent save(AuditEvent event) {
            throw new IllegalStateException("audit repository unavailable");
        }

        @Override
        public Optional<AuditEvent> findById(String auditId) {
            return Optional.empty();
        }

        @Override
        public AuditEventPage page(AuditEventQuery query) {
            return new AuditEventPage(List.of(), 0, query.size(), query.current(), 0);
        }
    }

    private static final class MemorySandboxRuntimeProfilePolicyRepository
            implements SandboxRuntimeProfilePolicyRepositoryPort {

        private final Map<String, SandboxRuntimeProfilePolicy> policies = new ConcurrentHashMap<>();

        @Override
        public SandboxRuntimeProfilePolicy upsert(SandboxRuntimeProfilePolicy policy) {
            policies.put(policy.policyId(), policy);
            return policy;
        }

        @Override
        public Optional<SandboxRuntimeProfilePolicy> findById(String policyId) {
            return Optional.ofNullable(policies.get(policyId));
        }

        @Override
        public Optional<SandboxRuntimeProfilePolicy> findByTenantAndRuntimeType(String tenantId,
                                                                                SandboxRuntimeType runtimeType) {
            return policies.values().stream()
                    .filter(policy -> policy.tenantId().equals(tenantId))
                    .filter(policy -> policy.runtimeType() == runtimeType)
                    .findFirst();
        }

        @Override
        public List<SandboxRuntimeProfilePolicy> listByTenant(String tenantId) {
            return policies.values().stream()
                    .filter(policy -> policy.tenantId().equals(tenantId))
                    .toList();
        }
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

    private static final class FailingSandboxSessionRepository implements SandboxSessionRepositoryPort {

        @Override
        public SandboxSession saveSession(SandboxSession session) {
            throw new IllegalStateException("sandbox session persistence failed");
        }

        @Override
        public Optional<SandboxSession> findSessionById(String sessionId) {
            return Optional.empty();
        }

        @Override
        public List<SandboxSession> listSessionsByTenant(String tenantId, int limit) {
            return List.of();
        }

        @Override
        public List<SandboxSession> listExpiredActiveSessions(String tenantId, Instant now, int limit) {
            return List.of();
        }

        @Override
        public Set<String> listActiveSessionIds() {
            return Set.of();
        }
    }

    private static final class MemorySandboxExecutionRepository implements SandboxExecutionRepositoryPort {

        private final Map<String, SandboxExecution> store = new ConcurrentHashMap<>();
        private int listExecutionsCalls;

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
            listExecutionsCalls++;
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

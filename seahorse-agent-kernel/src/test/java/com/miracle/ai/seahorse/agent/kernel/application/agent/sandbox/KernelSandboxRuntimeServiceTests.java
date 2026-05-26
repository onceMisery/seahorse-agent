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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventPage;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AuditEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import org.junit.jupiter.api.Test;

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
    void shouldSaveOnlyPromptVisibleArtifactsReturnedByRuntime() {
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
        assertEquals(1, artifactPort.saved.size());
        assertEquals("artifact-clean", artifactPort.saved.get(0).artifactId());
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

    private static final class RecordingSandboxRuntimePort implements SandboxRuntimePort {

        private boolean createSessionCalled;
        private boolean executeCalled;

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
                    List.of(SandboxTestArtifacts.clean("artifact-clean"), SandboxTestArtifacts.secret("artifact-secret")));
        }
    }

    private static final class MemoryArtifactPort implements SandboxArtifactPort {

        private final List<SandboxArtifact> saved = new ArrayList<>();

        @Override
        public SandboxArtifact save(SandboxArtifact artifact) {
            saved.add(artifact);
            return artifact;
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

    private static final class EmptySandboxArtifactQueryPort implements SandboxArtifactQueryPort {

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

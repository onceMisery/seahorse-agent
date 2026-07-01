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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public class KernelSandboxRuntimeService implements SandboxRuntimeInboundPort {

    private static final String SESSION_ID_PREFIX = "sandbox_";
    private static final String EXECUTION_ID_PREFIX = "sandbox_exec_";
    private static final String AUDIT_ID_PREFIX = "audit_sandbox_";
    private static final String AUDIT_ACTOR_ID = "sandbox-runtime";
    private static final String RESOURCE_TYPE_SANDBOX_SESSION = "SANDBOX_SESSION";
    private static final String RESOURCE_TYPE_SANDBOX_EXECUTION = "SANDBOX_EXECUTION";

    private final SandboxPolicyPort policyPort;
    private final SandboxRuntimePort runtimePort;
    private final SandboxArtifactPort artifactPort;
    private final SandboxSessionRepositoryPort sessionRepositoryPort;
    private final SandboxExecutionRepositoryPort executionRepositoryPort;
    private final SandboxArtifactQueryPort artifactQueryPort;
    private final KernelAuditLedgerService auditLedger;
    private final Clock clock;
    private final Map<String, SandboxSession> sessions = new ConcurrentHashMap<>();

    public KernelSandboxRuntimeService(SandboxPolicyPort policyPort,
                                       SandboxRuntimePort runtimePort,
                                       SandboxArtifactPort artifactPort,
                                       Clock clock) {
        this(policyPort,
                runtimePort,
                artifactPort,
                new InMemorySandboxSessionRepository(),
                new InMemorySandboxExecutionRepository(),
                new EmptySandboxArtifactQueryPort(),
                null,
                clock);
    }

    public KernelSandboxRuntimeService(SandboxPolicyPort policyPort,
                                       SandboxRuntimePort runtimePort,
                                       SandboxArtifactPort artifactPort,
                                       SandboxSessionRepositoryPort sessionRepositoryPort,
                                       SandboxExecutionRepositoryPort executionRepositoryPort,
                                       SandboxArtifactQueryPort artifactQueryPort,
                                       Clock clock) {
        this(policyPort,
                runtimePort,
                artifactPort,
                sessionRepositoryPort,
                executionRepositoryPort,
                artifactQueryPort,
                null,
                clock);
    }

    public KernelSandboxRuntimeService(SandboxPolicyPort policyPort,
                                       SandboxRuntimePort runtimePort,
                                       SandboxArtifactPort artifactPort,
                                       SandboxSessionRepositoryPort sessionRepositoryPort,
                                       SandboxExecutionRepositoryPort executionRepositoryPort,
                                       SandboxArtifactQueryPort artifactQueryPort,
                                       KernelAuditLedgerService auditLedger,
                                       Clock clock) {
        this.policyPort = Objects.requireNonNull(policyPort, "policyPort must not be null");
        this.runtimePort = Objects.requireNonNull(runtimePort, "runtimePort must not be null");
        this.artifactPort = Objects.requireNonNull(artifactPort, "artifactPort must not be null");
        this.sessionRepositoryPort = Objects.requireNonNull(sessionRepositoryPort,
                "sessionRepositoryPort must not be null");
        this.executionRepositoryPort = Objects.requireNonNull(executionRepositoryPort,
                "executionRepositoryPort must not be null");
        this.artifactQueryPort = Objects.requireNonNull(artifactQueryPort, "artifactQueryPort must not be null");
        this.auditLedger = auditLedger;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
    }

    @Override
    public SandboxSession createSession(SandboxSessionCreateCommand command) {
        SandboxSessionCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        SandboxPolicyDecision decision = policyPort.decide(new SandboxPolicyRequest(
                safeCommand.tenantId(),
                safeCommand.runId(),
                safeCommand.runtimeType(),
                safeCommand.networkRequested(),
                safeCommand.requestedHosts()));
        if (!decision.allowsExecution()) {
            SandboxSession denied = SandboxSession.failed(
                    sessionId(),
                    safeCommand.tenantId(),
                    safeCommand.runId(),
                    safeCommand.runtimeType(),
                    decision.reasonCode(),
                    clock.instant());
            return saveSession(denied, AuditEventType.SANDBOX_SESSION_CREATED);
        }
        SandboxSession session = runtimePort.createSession(new SandboxSessionRequest(
                safeCommand.tenantId(),
                safeCommand.runId(),
                safeCommand.runtimeType(),
                safeCommand.networkRequested(),
                safeCommand.requestedHosts()));
        return saveSession(session, AuditEventType.SANDBOX_SESSION_CREATED);
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionCommand command) {
        SandboxExecutionCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        SandboxSession session = findSessionOrThrow(safeCommand.sessionId());
        if (session.status().isTerminal()) {
            return failedResult(session, session.reasonCode());
        }
        SandboxPolicyDecision decision = policyPort.decide(new SandboxPolicyRequest(
                session.tenantId(),
                session.runId(),
                session.runtimeType(),
                safeCommand.networkRequested(),
                safeCommand.requestedHosts()));
        if (!decision.allowsExecution()) {
            return failedResult(session, decision.reasonCode());
        }
        SandboxExecutionResult result = runtimePort.execute(new SandboxExecutionRequest(
                session,
                safeCommand.input(),
                safeCommand.networkRequested(),
                safeCommand.requestedHosts()));
        SandboxExecution savedExecution = executionRepositoryPort.saveExecution(result.execution());
        List<SandboxArtifact> visibleArtifacts = result.artifacts().stream()
                .filter(SandboxArtifact::promptVisible)
                .map(artifactPort::save)
                .toList();
        appendExecutionAudit(session, savedExecution, visibleArtifacts.size(), result.reasonCode());
        return new SandboxExecutionResult(savedExecution, visibleArtifacts, result.reasonCode());
    }

    @Override
    public SandboxSession close(String sessionId) {
        SandboxSession session = findSessionOrThrow(requireText(sessionId, "sessionId must not be blank"));
        if (session.status().isTerminal()) {
            return session;
        }
        SandboxSession closed = Objects.requireNonNull(
                runtimePort.closeSession(session),
                "runtime closeSession result must not be null");
        return saveSession(closed, AuditEventType.SANDBOX_SESSION_CLOSED);
    }

    @Override
    public List<SandboxArtifact> listArtifacts(String sessionId) {
        String safeSessionId = requireText(sessionId, "sessionId must not be blank");
        findSessionOrThrow(safeSessionId);
        return artifactQueryPort.listPromptVisibleBySession(safeSessionId);
    }

    private SandboxExecutionResult failedResult(SandboxSession session, SandboxPolicyReasonCode reasonCode) {
        SandboxExecution execution = executionRepositoryPort.saveExecution(failedExecution(session, reasonCode));
        appendExecutionAudit(session, execution, 0, reasonCode);
        return SandboxExecutionResult.failed(execution, reasonCode);
    }

    private SandboxSession saveSession(SandboxSession session, AuditEventType auditEventType) {
        SandboxSession saved = sessionRepositoryPort.saveSession(session);
        sessions.put(saved.sessionId(), saved);
        appendSessionAudit(saved, auditEventType);
        return saved;
    }

    private SandboxSession findSessionOrThrow(String sessionId) {
        return findSession(sessionId).orElseThrow(() -> new IllegalArgumentException("Sandbox session not found"));
    }

    private Optional<SandboxSession> findSession(String sessionId) {
        if (!hasText(sessionId)) {
            return Optional.empty();
        }
        String safeSessionId = sessionId.trim();
        SandboxSession cached = sessions.get(safeSessionId);
        if (cached != null) {
            return Optional.of(cached);
        }
        Optional<SandboxSession> loaded = sessionRepositoryPort.findSessionById(safeSessionId);
        loaded.ifPresent(session -> sessions.put(session.sessionId(), session));
        return loaded;
    }

    private SandboxExecution failedExecution(SandboxSession session, SandboxPolicyReasonCode reasonCode) {
        Instant now = clock.instant();
        return SandboxExecution.failed(executionId(), session.sessionId(), session.runtimeType(), now, reasonCode);
    }

    private String sessionId() {
        return SESSION_ID_PREFIX + nextId();
    }

    private String executionId() {
        return EXECUTION_ID_PREFIX + nextId();
    }

    private String nextId() {
        return SnowflakeIds.nextIdString();
    }

    private void appendSessionAudit(SandboxSession session, AuditEventType auditEventType) {
        if (auditLedger == null) {
            return;
        }
        Instant now = clock.instant();
        auditLedger.append(new AuditEvent(
                auditId(),
                session.tenantId(),
                auditEventType,
                AuditActorType.SYSTEM,
                AUDIT_ACTOR_ID,
                session.runId(),
                null,
                RESOURCE_TYPE_SANDBOX_SESSION,
                session.sessionId(),
                """
                        {"sessionId":"%s","runtimeType":"%s","status":"%s","reasonCode":"%s"}
                        """.formatted(session.sessionId(),
                        session.runtimeType().name(),
                        session.status().name(),
                        session.reasonCode().name()),
                now));
    }

    private void appendExecutionAudit(SandboxSession session,
                                      SandboxExecution execution,
                                      int artifactCount,
                                      SandboxPolicyReasonCode reasonCode) {
        if (auditLedger == null) {
            return;
        }
        Instant now = clock.instant();
        auditLedger.append(new AuditEvent(
                auditId(),
                session.tenantId(),
                AuditEventType.SANDBOX_EXECUTION_FINISHED,
                AuditActorType.SYSTEM,
                AUDIT_ACTOR_ID,
                session.runId(),
                null,
                RESOURCE_TYPE_SANDBOX_EXECUTION,
                execution.executionId(),
                """
                        {"sessionId":"%s","executionId":"%s","runtimeType":"%s","status":"%s","reasonCode":"%s","artifactCount":%d}
                        """.formatted(session.sessionId(),
                        execution.executionId(),
                        execution.runtimeType().name(),
                        execution.status().name(),
                        reasonCode.name(),
                        artifactCount),
                now));
    }

    private String auditId() {
        return AUDIT_ID_PREFIX + nextId();
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static final class InMemorySandboxSessionRepository implements SandboxSessionRepositoryPort {

        private final Map<String, SandboxSession> store = new ConcurrentHashMap<>();

        @Override
        public SandboxSession saveSession(SandboxSession session) {
            SandboxSession safeSession = Objects.requireNonNull(session, "session must not be null");
            store.put(safeSession.sessionId(), safeSession);
            return safeSession;
        }

        @Override
        public Optional<SandboxSession> findSessionById(String sessionId) {
            if (!hasText(sessionId)) {
                return Optional.empty();
            }
            return Optional.ofNullable(store.get(sessionId.trim()));
        }
    }

    private static final class InMemorySandboxExecutionRepository implements SandboxExecutionRepositoryPort {

        private final Map<String, SandboxExecution> store = new ConcurrentHashMap<>();

        @Override
        public SandboxExecution saveExecution(SandboxExecution execution) {
            SandboxExecution safeExecution = Objects.requireNonNull(execution, "execution must not be null");
            store.put(safeExecution.executionId(), safeExecution);
            return safeExecution;
        }

        @Override
        public Optional<SandboxExecution> findExecutionById(String executionId) {
            if (!hasText(executionId)) {
                return Optional.empty();
            }
            return Optional.ofNullable(store.get(executionId.trim()));
        }

        @Override
        public List<SandboxExecution> listExecutionsBySession(String sessionId) {
            if (!hasText(sessionId)) {
                return List.of();
            }
            String safeSessionId = sessionId.trim();
            List<SandboxExecution> records = new ArrayList<>(store.values().stream()
                    .filter(execution -> execution.sessionId().equals(safeSessionId))
                    .toList());
            records.sort(Comparator.comparing(SandboxExecution::createdAt)
                    .thenComparing(SandboxExecution::executionId));
            return List.copyOf(records);
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

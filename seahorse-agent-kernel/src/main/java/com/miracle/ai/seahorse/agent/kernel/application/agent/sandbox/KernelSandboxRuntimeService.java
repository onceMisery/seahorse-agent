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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.kernel.application.agent.audit.KernelAuditLedgerService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactRedactionSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScannerPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScannerHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxBrowserProfile;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxBrowserProfileStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxEgressPolicy;
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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxEgressPolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDetailDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxBrowserProfileUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeProfilePolicyUpsertCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionSweepResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScannerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxBrowserProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxEgressPolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeProfilePolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeCapacityReservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRemoteRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeNodeRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeSessionOwnership;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.SandboxRuntimeAdmissionSupport.MAX_RUNTIME_CREATE_ATTEMPTS;
import static com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.SandboxRuntimeAdmissionSupport.RuntimeAdmissionDecision;
import static com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.SandboxRuntimeAdmissionSupport.RuntimeCreateFailoverAudit;
import static com.miracle.ai.seahorse.agent.kernel.application.agent.sandbox.SandboxRuntimeAdmissionSupport.RuntimeCreateRecovery;

public class KernelSandboxRuntimeService implements SandboxRuntimeInboundPort {

    private static final String SESSION_ID_PREFIX = "sandbox_";
    private static final String EXECUTION_ID_PREFIX = "sandbox_exec_";
    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "权限不足";
    private static final int DEFAULT_SESSION_LIST_LIMIT = 20;
    private static final int MAX_SESSION_LIST_LIMIT = 100;

    private final SandboxPolicyPort policyPort;
    private final SandboxRuntimePort runtimePort;
    private final SandboxRemoteRuntimePort remoteRuntimePort;
    private final SandboxRuntimeNodeRegistryPort runtimeNodeRegistryPort;
    private final SandboxRuntimeCapacityReservationPort capacityReservationPort;
    private final SandboxArtifactPort artifactPort;
    private final SandboxArtifactScannerPort artifactScannerPort;
    private final ObjectStoragePort artifactStoragePort;
    private final SandboxSessionRepositoryPort sessionRepositoryPort;
    private final SandboxExecutionRepositoryPort executionRepositoryPort;
    private final SandboxArtifactQueryPort artifactQueryPort;
    private final SandboxRuntimeProfilePolicyRepositoryPort runtimeProfilePolicyRepositoryPort;
    private final SandboxEgressPolicyRepositoryPort egressPolicyRepositoryPort;
    private final SandboxBrowserProfileRepositoryPort browserProfileRepositoryPort;
    private final AgentRunRepositoryPort runRepository;
    private final CurrentUserPort currentUserPort;
    private final KernelAuditLedgerService auditLedger;
    private final Clock clock;
    private final SandboxPathValidator pathValidator;
    private final SandboxArtifactSupport artifactSupport;
    private final SandboxPolicySupport policySupport;
    private final SandboxAuditSupport auditSupport;
    private final SandboxRuntimeAdmissionSupport admissionSupport;
    private final Map<String, SandboxSession> sessions = new ConcurrentHashMap<>();

    /**
     * 构造器重载已折叠为 Builder：{@code KernelSandboxRuntimeService.builder().policyPort(...).runtimePort(...).build()}。
     * 仓库类端口均有 InMemory 默认实现，按需覆盖即可；可选端口为 null。
     */
    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private SandboxPolicyPort policyPort;
        private SandboxRuntimePort runtimePort;
        private SandboxArtifactPort artifactPort;
        private SandboxSessionRepositoryPort sessionRepositoryPort = new InMemorySandboxSessionRepository();
        private SandboxExecutionRepositoryPort executionRepositoryPort = new InMemorySandboxExecutionRepository();
        private SandboxArtifactQueryPort artifactQueryPort = new EmptySandboxArtifactQueryPort();
        private SandboxArtifactScannerPort artifactScannerPort = new DefaultSandboxArtifactScannerPort();
        private ObjectStoragePort artifactStoragePort;
        private SandboxRuntimeProfilePolicyRepositoryPort runtimeProfilePolicyRepositoryPort =
                new InMemorySandboxRuntimeProfilePolicyRepository();
        private SandboxEgressPolicyRepositoryPort egressPolicyRepositoryPort = new InMemorySandboxEgressPolicyRepository();
        private SandboxBrowserProfileRepositoryPort browserProfileRepositoryPort =
                new InMemorySandboxBrowserProfileRepository();
        private AgentRunRepositoryPort runRepository;
        private CurrentUserPort currentUserPort;
        private KernelAuditLedgerService auditLedger;
        private Clock clock;
        private SandboxRemoteRuntimePort remoteRuntimePort;
        private SandboxRuntimeNodeRegistryPort runtimeNodeRegistryPort;
        private SandboxRuntimeCapacityReservationPort capacityReservationPort;

        public Builder policyPort(SandboxPolicyPort policyPort) {
            this.policyPort = Objects.requireNonNull(policyPort, "policyPort must not be null");
            return this;
        }

        public Builder runtimePort(SandboxRuntimePort runtimePort) {
            this.runtimePort = Objects.requireNonNull(runtimePort, "runtimePort must not be null");
            return this;
        }

        public Builder artifactPort(SandboxArtifactPort artifactPort) {
            this.artifactPort = Objects.requireNonNull(artifactPort, "artifactPort must not be null");
            return this;
        }

        public Builder sessionRepositoryPort(SandboxSessionRepositoryPort sessionRepositoryPort) {
            this.sessionRepositoryPort = Objects.requireNonNullElseGet(
                    sessionRepositoryPort,
                    InMemorySandboxSessionRepository::new);
            return this;
        }

        public Builder executionRepositoryPort(SandboxExecutionRepositoryPort executionRepositoryPort) {
            this.executionRepositoryPort = Objects.requireNonNullElseGet(
                    executionRepositoryPort,
                    InMemorySandboxExecutionRepository::new);
            return this;
        }

        public Builder artifactQueryPort(SandboxArtifactQueryPort artifactQueryPort) {
            this.artifactQueryPort = Objects.requireNonNullElseGet(artifactQueryPort, EmptySandboxArtifactQueryPort::new);
            return this;
        }

        public Builder artifactScannerPort(SandboxArtifactScannerPort artifactScannerPort) {
            this.artifactScannerPort = Objects.requireNonNullElseGet(
                    artifactScannerPort,
                    DefaultSandboxArtifactScannerPort::new);
            return this;
        }

        public Builder artifactStoragePort(ObjectStoragePort artifactStoragePort) {
            this.artifactStoragePort = artifactStoragePort;
            return this;
        }

        public Builder runtimeProfilePolicyRepositoryPort(
                SandboxRuntimeProfilePolicyRepositoryPort runtimeProfilePolicyRepositoryPort) {
            this.runtimeProfilePolicyRepositoryPort = Objects.requireNonNullElseGet(
                    runtimeProfilePolicyRepositoryPort,
                    InMemorySandboxRuntimeProfilePolicyRepository::new);
            return this;
        }

        public Builder egressPolicyRepositoryPort(SandboxEgressPolicyRepositoryPort egressPolicyRepositoryPort) {
            this.egressPolicyRepositoryPort = Objects.requireNonNullElseGet(
                    egressPolicyRepositoryPort,
                    InMemorySandboxEgressPolicyRepository::new);
            return this;
        }

        public Builder browserProfileRepositoryPort(SandboxBrowserProfileRepositoryPort browserProfileRepositoryPort) {
            this.browserProfileRepositoryPort = Objects.requireNonNullElseGet(
                    browserProfileRepositoryPort,
                    InMemorySandboxBrowserProfileRepository::new);
            return this;
        }

        public Builder runRepository(AgentRunRepositoryPort runRepository) {
            this.runRepository = runRepository;
            return this;
        }

        public Builder currentUserPort(CurrentUserPort currentUserPort) {
            this.currentUserPort = currentUserPort;
            return this;
        }

        public Builder auditLedger(KernelAuditLedgerService auditLedger) {
            this.auditLedger = auditLedger;
            return this;
        }

        public Builder clock(Clock clock) {
            this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
            return this;
        }

        public Builder remoteRuntimePort(SandboxRemoteRuntimePort remoteRuntimePort) {
            this.remoteRuntimePort = remoteRuntimePort;
            return this;
        }

        public Builder runtimeNodeRegistryPort(SandboxRuntimeNodeRegistryPort runtimeNodeRegistryPort) {
            this.runtimeNodeRegistryPort = runtimeNodeRegistryPort;
            return this;
        }

        public Builder capacityReservationPort(SandboxRuntimeCapacityReservationPort capacityReservationPort) {
            this.capacityReservationPort = capacityReservationPort;
            return this;
        }

        public KernelSandboxRuntimeService build() {
            return new KernelSandboxRuntimeService(
                    Objects.requireNonNull(policyPort, "policyPort must not be null"),
                    Objects.requireNonNull(runtimePort, "runtimePort must not be null"),
                    Objects.requireNonNull(artifactPort, "artifactPort must not be null"),
                    sessionRepositoryPort,
                    executionRepositoryPort,
                    artifactQueryPort,
                    artifactScannerPort,
                    artifactStoragePort,
                    runtimeProfilePolicyRepositoryPort,
                    egressPolicyRepositoryPort,
                    browserProfileRepositoryPort,
                    runRepository,
                    currentUserPort,
                    auditLedger,
                    clock,
                    remoteRuntimePort,
                    runtimeNodeRegistryPort,
                    capacityReservationPort);
        }
    }

    private KernelSandboxRuntimeService(SandboxPolicyPort policyPort,
                                       SandboxRuntimePort runtimePort,
                                       SandboxArtifactPort artifactPort,
                                       SandboxSessionRepositoryPort sessionRepositoryPort,
                                       SandboxExecutionRepositoryPort executionRepositoryPort,
                                       SandboxArtifactQueryPort artifactQueryPort,
                                       SandboxArtifactScannerPort artifactScannerPort,
                                       ObjectStoragePort artifactStoragePort,
                                       SandboxRuntimeProfilePolicyRepositoryPort runtimeProfilePolicyRepositoryPort,
                                       SandboxEgressPolicyRepositoryPort egressPolicyRepositoryPort,
                                       SandboxBrowserProfileRepositoryPort browserProfileRepositoryPort,
                                       AgentRunRepositoryPort runRepository,
                                       CurrentUserPort currentUserPort,
                                       KernelAuditLedgerService auditLedger,
                                       Clock clock,
                                       SandboxRemoteRuntimePort remoteRuntimePort,
                                       SandboxRuntimeNodeRegistryPort runtimeNodeRegistryPort,
                                       SandboxRuntimeCapacityReservationPort capacityReservationPort) {
        this.policyPort = Objects.requireNonNull(policyPort, "policyPort must not be null");
        this.runtimePort = Objects.requireNonNull(runtimePort, "runtimePort must not be null");
        this.remoteRuntimePort = remoteRuntimePort;
        this.runtimeNodeRegistryPort = runtimeNodeRegistryPort;
        this.capacityReservationPort = capacityReservationPort;
        this.artifactPort = Objects.requireNonNull(artifactPort, "artifactPort must not be null");
        this.artifactScannerPort = Objects.requireNonNull(artifactScannerPort,
                "artifactScannerPort must not be null");
        this.artifactStoragePort = artifactStoragePort;
        this.sessionRepositoryPort = Objects.requireNonNull(sessionRepositoryPort,
                "sessionRepositoryPort must not be null");
        this.executionRepositoryPort = Objects.requireNonNull(executionRepositoryPort,
                "executionRepositoryPort must not be null");
        this.artifactQueryPort = Objects.requireNonNull(artifactQueryPort, "artifactQueryPort must not be null");
        this.runtimeProfilePolicyRepositoryPort = Objects.requireNonNull(runtimeProfilePolicyRepositoryPort,
                "runtimeProfilePolicyRepositoryPort must not be null");
        this.egressPolicyRepositoryPort = Objects.requireNonNull(egressPolicyRepositoryPort,
                "egressPolicyRepositoryPort must not be null");
        this.browserProfileRepositoryPort = Objects.requireNonNull(browserProfileRepositoryPort,
                "browserProfileRepositoryPort must not be null");
        this.runRepository = runRepository;
        this.currentUserPort = currentUserPort;
        this.auditLedger = auditLedger;
        this.clock = Objects.requireNonNullElseGet(clock, Clock::systemUTC);
        this.pathValidator = new SandboxPathValidator();
        this.artifactSupport = new SandboxArtifactSupport(
                artifactPort,
                artifactScannerPort,
                artifactStoragePort,
                artifactQueryPort,
                pathValidator,
                new SandboxArtifactSupport.SandboxSessionAccess() {
                    @Override
                    public SandboxSession findSessionOrThrow(String sessionId) {
                        return KernelSandboxRuntimeService.this.findSessionOrThrow(sessionId);
                    }

                    @Override
                    public SandboxSession requireReadableSession(SandboxSession session) {
                        return KernelSandboxRuntimeService.this.requireReadableSession(session);
                    }
                });
        this.policySupport = new SandboxPolicySupport(
                policyPort,
                runtimeProfilePolicyRepositoryPort,
                egressPolicyRepositoryPort,
                clock);
        this.auditSupport = new SandboxAuditSupport(auditLedger, clock);
        this.admissionSupport = new SandboxRuntimeAdmissionSupport(
                runtimePort,
                remoteRuntimePort,
                runtimeNodeRegistryPort,
                capacityReservationPort,
                sessionRepositoryPort);
    }

    @Override
    public List<SandboxRuntimeProfilePolicy> listRuntimeProfilePolicies(String tenantId) {
        return policySupport.listRuntimeProfilePolicies(tenantId);
    }

    @Override
    public SandboxRuntimeProfilePolicy upsertRuntimeProfilePolicy(SandboxRuntimeProfilePolicyUpsertCommand command) {
        return policySupport.upsertRuntimeProfilePolicy(command);
    }

    @Override
    public SandboxEgressPolicy inspectSandboxEgressPolicy(String tenantId) {
        return policySupport.inspectSandboxEgressPolicy(tenantId);
    }

    @Override
    public SandboxEgressPolicy upsertSandboxEgressPolicy(SandboxEgressPolicyUpsertCommand command) {
        return policySupport.upsertSandboxEgressPolicy(command);
    }

    @Override
    public List<SandboxArtifact> listArtifacts(String sessionId) {
        return artifactSupport.listArtifacts(sessionId);
    }

    @Override
    public SandboxArtifactDetailDecision describeArtifact(String artifactId) {
        return artifactSupport.describeArtifact(artifactId);
    }

    @Override
    public SandboxArtifactDownloadDecision downloadArtifact(String artifactId) {
        return artifactSupport.downloadArtifact(artifactId);
    }

    @Override
    public String readBrowserSessionStateArtifact(String artifactId) {
        return artifactSupport.readBrowserSessionStateArtifact(artifactId);
    }

    @Override
    public SandboxSession createSession(SandboxSessionCreateCommand command) {
        SandboxSessionCreateCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        SandboxRuntimeProfilePolicy profilePolicy = policySupport.effectiveRuntimeProfilePolicy(
                safeCommand.tenantId(),
                safeCommand.runtimeType());
        Instant now = clock.instant();
        String profileId = profilePolicy.profileId();
        Instant expiresAt = profilePolicy.effectiveExpiresAt(safeCommand.expiresAt(), now);
        if (!profilePolicy.allowsExecution()) {
            SandboxSession denied = SandboxSession.failed(
                    sessionId(),
                    safeCommand.tenantId(),
                    safeCommand.runId(),
                    safeCommand.runtimeType(),
                    SandboxPolicyReasonCode.RUNTIME_PROFILE_DISABLED,
                    profileId,
                    expiresAt,
                    now);
            return saveSession(denied, AuditEventType.SANDBOX_SESSION_CREATED);
        }
        if (!profilePolicy.networkAllowed() && safeCommand.networkRequested()) {
            SandboxSession denied = SandboxSession.failed(
                    sessionId(),
                    safeCommand.tenantId(),
                    safeCommand.runId(),
                    safeCommand.runtimeType(),
                    SandboxPolicyReasonCode.NETWORK_DENIED_BY_DEFAULT,
                    profileId,
                    expiresAt,
                    now);
            return saveSession(denied, AuditEventType.SANDBOX_SESSION_CREATED);
        }
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
                    profileId,
                    expiresAt,
                    now);
            return saveSession(denied, AuditEventType.SANDBOX_SESSION_CREATED);
        }
        List<RuntimeAdmissionDecision> runtimeCandidates = admissionSupport.runtimeAdmissionCandidates(
                safeCommand.requiredRuntimeNodeId());
        boolean automaticPlacement = !hasText(safeCommand.requiredRuntimeNodeId());
        String runtimeSessionId = sessionId();
        SandboxSessionRequest runtimeRequest = new SandboxSessionRequest(
                safeCommand.tenantId(),
                safeCommand.runId(),
                safeCommand.runtimeType(),
                safeCommand.networkRequested(),
                safeCommand.requestedHosts(),
                profileId,
                expiresAt,
                runtimeSessionId);
        SandboxPolicyReasonCode lastAdmissionRejection = null;
        int runtimeCreateAttempts = 0;
        RuntimeCreateFailoverAudit pendingFailoverAudit = null;
        for (int candidateIndex = 0; candidateIndex < runtimeCandidates.size(); candidateIndex++) {
            RuntimeAdmissionDecision runtimeAdmission = admissionSupport.reserveRuntimeAdmission(runtimeCandidates.get(candidateIndex));
            boolean hasNextCandidate = candidateIndex + 1 < runtimeCandidates.size();
            if (runtimeAdmission.rejectionReason() != null) {
                lastAdmissionRejection = runtimeAdmission.rejectionReason();
                if (automaticPlacement
                        && hasNextCandidate
                        && runtimeAdmission.rejectionReason() == SandboxPolicyReasonCode.RUNTIME_CAPACITY_EXCEEDED) {
                    continue;
                }
                return saveFailedRuntimeSession(
                        safeCommand, runtimeSessionId, runtimeAdmission.rejectionReason(), profileId, expiresAt, now);
            }
            runtimeCreateAttempts++;
            SandboxSession cleanupSession = SandboxSession.created(
                    runtimeSessionId,
                    safeCommand.tenantId(),
                    safeCommand.runId(),
                    safeCommand.runtimeType(),
                    profileId,
                    expiresAt,
                    now).withRuntimeNode(runtimeAdmission.runtimeNodeId());
            SandboxSession session;
            try {
                session = Objects.requireNonNull(runtimeAdmission.remoteEndpoint() == null
                        ? admissionSupport.createLocalRuntimeSession(runtimeAdmission.runtimeNodeId(), runtimeRequest)
                        : remoteRuntimePort.createSession(runtimeAdmission.remoteEndpoint(), runtimeRequest),
                        "runtime createSession result must not be null");
            } catch (RuntimeException ex) {
                RuntimeCreateRecovery recovery = admissionSupport.recoverFailedRuntimeCreate(runtimeAdmission, cleanupSession);
                if (recovery.releaseReservation()) {
                    admissionSupport.releaseCapacityReservation(runtimeAdmission);
                }
                if (automaticPlacement
                        && hasNextCandidate
                        && runtimeCreateAttempts < MAX_RUNTIME_CREATE_ATTEMPTS
                        && recovery.failoverAllowed()) {
                    pendingFailoverAudit = new RuntimeCreateFailoverAudit(
                            runtimeAdmission.runtimeNodeId(),
                            recovery.failoverRecovery());
                    continue;
                }
                return saveFailedRuntimeSession(
                        safeCommand,
                        runtimeSessionId,
                        SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE,
                        profileId,
                        expiresAt,
                        now);
            }
            if (!runtimeSessionId.equals(session.sessionId())) {
                if (admissionSupport.rollbackCreatedRuntimeSession(runtimeAdmission, cleanupSession)) {
                    admissionSupport.releaseCapacityReservation(runtimeAdmission);
                }
                return saveFailedRuntimeSession(
                        safeCommand,
                        runtimeSessionId,
                        SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE,
                        profileId,
                        expiresAt,
                        now);
            }
            SandboxSession governed = session.withRuntimeGovernance(
                    profileId,
                    expiresAt).withRuntimeNode(runtimeAdmission.runtimeNodeId());
            SandboxSession saved;
            try {
                saved = persistSession(governed);
            } catch (RuntimeException ex) {
                if (admissionSupport.rollbackCreatedRuntimeSession(runtimeAdmission, governed)) {
                    admissionSupport.releaseCapacityReservation(runtimeAdmission);
                }
                throw ex;
            }
            admissionSupport.releaseCapacityReservation(runtimeAdmission);
            if (pendingFailoverAudit != null && saved.status() == SandboxExecutionStatus.CREATED) {
                auditSupport.appendRuntimeCreateFailoverAudit(saved, pendingFailoverAudit, runtimeCreateAttempts);
            }
            auditSupport.appendSessionAudit(saved, AuditEventType.SANDBOX_SESSION_CREATED);
            return saved;
        }
        return saveFailedRuntimeSession(
                safeCommand,
                runtimeSessionId,
                Objects.requireNonNullElse(lastAdmissionRejection, SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE),
                profileId,
                expiresAt,
                now);
    }
    private SandboxSession saveFailedRuntimeSession(SandboxSessionCreateCommand command,
                                                    String sessionId,
                                                    SandboxPolicyReasonCode reasonCode,
                                                    String profileId,
                                                    Instant expiresAt,
                                                    Instant now) {
        SandboxSession failed = SandboxSession.failed(
                sessionId,
                command.tenantId(),
                command.runId(),
                command.runtimeType(),
                reasonCode,
                profileId,
                expiresAt,
                now);
        return saveSession(failed, AuditEventType.SANDBOX_SESSION_CREATED);
    }

    @Override
    public SandboxExecutionResult execute(SandboxExecutionCommand command) {
        SandboxExecutionCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        SandboxSession session = requireReadableSession(findSessionOrThrow(safeCommand.sessionId()));
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
        SandboxExecutionRequest runtimeRequest = new SandboxExecutionRequest(
                session,
                safeCommand.input(),
                safeCommand.networkRequested(),
                safeCommand.requestedHosts(),
                policyPort.browserPrivateNetworkAllowedHosts(session.tenantId()));
        SandboxExecutionResult result;
        Optional<SandboxRuntimeNodeEndpoint> remoteEndpoint;
        try {
            remoteEndpoint = admissionSupport.remoteEndpointFor(session);
            result = remoteEndpoint.isPresent()
                    ? remoteRuntimePort.execute(remoteEndpoint.get(), runtimeRequest)
                    : runtimePort.execute(runtimeRequest);
        } catch (RuntimeException ex) {
            return failedResult(session, SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE);
        }
        List<SandboxArtifact> savedArtifacts = new ArrayList<>();
        try {
            SandboxExecution savedExecution = executionRepositoryPort.saveExecution(result.execution());
            for (SandboxArtifact artifact : result.artifacts()) {
                savedArtifacts.add(artifactSupport.persistArtifact(artifact));
            }
            List<SandboxArtifact> visibleArtifacts = savedArtifacts.stream()
                    .filter(SandboxArtifact::promptVisible)
                    .toList();
            auditSupport.appendExecutionAudit(session,
                    savedExecution,
                    savedArtifacts.size(),
                    visibleArtifacts.size(),
                    result.reasonCode());
            return new SandboxExecutionResult(savedExecution, visibleArtifacts, result.reasonCode());
        } finally {
            if (remoteEndpoint.isPresent()) {
                remoteRuntimePort.releaseArtifacts(session, result.artifacts(), List.copyOf(savedArtifacts));
            }
        }
    }

    @Override
    public SandboxSession close(String sessionId) {
        SandboxSession session = requireReadableSession(findSessionOrThrow(requireText(sessionId,
                "sessionId must not be blank")));
        if (session.status().isTerminal()) {
            return session;
        }
        Optional<SandboxRuntimeNodeEndpoint> remoteEndpoint = admissionSupport.remoteEndpointFor(session);
        SandboxSession runtimeClosed =
                remoteEndpoint.isPresent()
                        ? remoteRuntimePort.closeSession(remoteEndpoint.get(), session)
                        : runtimePort.closeSession(session);
        SandboxRuntimeAdmissionSupport.requireConfirmedRuntimeClose(session, runtimeClosed);
        return saveSession(runtimeClosed, AuditEventType.SANDBOX_SESSION_CLOSED);
    }

    @Override
    public List<SandboxSession> listSessions(String tenantId, int limit) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        int safeLimit = normalizeSessionListLimit(limit);
        List<SandboxSession> records = sessionRepositoryPort.listSessionsByTenant(safeTenantId, safeLimit);
        records.forEach(session -> sessions.put(session.sessionId(), session));
        return records.stream()
                .filter(this::canReadSession)
                .toList();
    }

    @Override
    public SandboxSessionSweepResult sweepExpiredSessions(String tenantId, int limit) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        int safeLimit = normalizeSessionListLimit(limit);
        Instant now = clock.instant();
        List<SandboxSession> expiredSessions = sessionRepositoryPort.listExpiredActiveSessions(
                safeTenantId,
                now,
                safeLimit);
        List<SandboxSession> closedSessions = new ArrayList<>();
        int failedCount = 0;
        for (SandboxSession session : expiredSessions) {
            sessions.put(session.sessionId(), session);
            if (session.status().isTerminal() || session.expiresAt().isAfter(now)) {
                continue;
            }
            try {
                Optional<SandboxRuntimeNodeEndpoint> remoteEndpoint = admissionSupport.remoteEndpointFor(session);
                SandboxSession runtimeClosed = remoteEndpoint.isPresent()
                        ? remoteRuntimePort.closeSession(remoteEndpoint.get(), session)
                        : runtimePort.closeSession(session);
                SandboxRuntimeAdmissionSupport.requireConfirmedRuntimeClose(session, runtimeClosed);
                SandboxSession timedOut = session.timedOut(now);
                closedSessions.add(saveSession(timedOut, AuditEventType.SANDBOX_SESSION_EXPIRED));
            } catch (RuntimeException ex) {
                failedCount++;
            }
        }
        return new SandboxSessionSweepResult(
                safeTenantId,
                now,
                expiredSessions.size(),
                closedSessions.size(),
                failedCount,
                closedSessions);
    }

    @Override
    public SandboxRuntimeCleanupResult sweepOrphanedRuntimeResources() {
        Set<String> activeSessionIds = sessionRepositoryPort.listActiveSessionIds();
        return runtimePort.sweepOrphanedResources(activeSessionIds);
    }

    @Override
    public SandboxRuntimeHealth inspectRuntimeHealth() {
        Set<String> activeSessionIds = sessionRepositoryPort.listActiveSessionIds();
        return runtimePort.inspectHealth(activeSessionIds);
    }

    @Override
    public List<SandboxRuntimeNodeHealth> inspectRuntimeNodes() {
        return List.of(SandboxRuntimeNodeHealth.fromHealth(inspectRuntimeHealth()));
    }

    @Override
    public SandboxArtifactScannerPolicy inspectArtifactScannerPolicy() {
        return artifactScannerPort.describePolicy();
    }

    @Override
    public SandboxArtifactScannerHealth inspectArtifactScannerHealth() {
        return artifactScannerPort.describeHealth();
    }

    @Override
    public SandboxRuntimeContainerReapResult reapOrphanedRuntimeContainers(boolean dryRun) {
        Set<String> activeSessionIds = sessionRepositoryPort.listActiveSessionIds();
        return runtimePort.reapOrphanedContainers(activeSessionIds, dryRun);
    }
    @Override
    public List<SandboxBrowserProfile> listSandboxBrowserProfiles(String tenantId, int limit) {
        return browserProfileRepositoryPort.listByTenant(
                requireText(tenantId, "tenantId must not be blank"),
                Math.max(1, Math.min(limit, 100)));
    }

    @Override
    public SandboxBrowserProfile upsertSandboxBrowserProfile(SandboxBrowserProfileUpsertCommand command) {
        SandboxBrowserProfileUpsertCommand safeCommand =
                Objects.requireNonNull(command, "command must not be null");
        String tenantId = requireText(safeCommand.tenantId(), "tenantId must not be blank");
        String profileId = requireText(safeCommand.profileId(), "profileId must not be blank");
        String artifactId = requireText(safeCommand.sessionStateArtifactId(), "sessionStateArtifactId must not be blank");
        Instant now = clock.instant();
        Instant expiresAt = Objects.requireNonNull(safeCommand.expiresAt(), "expiresAt must not be null");
        if (!expiresAt.isAfter(now)) {
            throw new IllegalArgumentException("expiresAt must be in the future");
        }
        artifactSupport.requireBrowserProfileArtifact(tenantId, artifactId);
        Optional<SandboxBrowserProfile> existing = browserProfileRepositoryPort
                .findByTenantAndProfileId(tenantId, profileId);
        return browserProfileRepositoryPort.save(new SandboxBrowserProfile(
                profileId,
                tenantId,
                safeCommand.name(),
                artifactId,
                Objects.requireNonNullElse(safeCommand.status(), SandboxBrowserProfileStatus.ACTIVE),
                expiresAt,
                existing.map(SandboxBrowserProfile::createdAt).orElse(now),
                now));
    }

    @Override
    public SandboxBrowserProfile disableSandboxBrowserProfile(String tenantId, String profileId) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        SandboxBrowserProfile existing = browserProfileRepositoryPort
                .findByTenantAndProfileId(safeTenantId, requireText(profileId, "profileId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("Sandbox browser profile not found"));
        return browserProfileRepositoryPort.save(new SandboxBrowserProfile(
                existing.profileId(),
                existing.tenantId(),
                existing.name(),
                existing.sessionStateArtifactId(),
                SandboxBrowserProfileStatus.DISABLED,
                existing.expiresAt(),
                existing.createdAt(),
                clock.instant()));
    }

    @Override
    public String readSandboxBrowserProfileSessionState(String tenantId, String profileId) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        SandboxBrowserProfile profile = browserProfileRepositoryPort
                .findByTenantAndProfileId(safeTenantId, requireText(profileId, "profileId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("Sandbox browser profile not found"));
        if (!profile.usableAt(clock.instant())) {
            throw new IllegalStateException("Sandbox browser profile is disabled or expired");
        }
        return artifactSupport.readBrowserSessionStateArtifact(artifactSupport.requireBrowserProfileArtifact(
                safeTenantId,
                profile.sessionStateArtifactId()));
    }

    @Override
    public List<SandboxExecution> listExecutions(String sessionId) {
        String safeSessionId = requireText(sessionId, "sessionId must not be blank");
        requireReadableSession(findSessionOrThrow(safeSessionId));
        return executionRepositoryPort.listExecutionsBySession(safeSessionId);
    }
    private SandboxExecutionResult failedResult(SandboxSession session, SandboxPolicyReasonCode reasonCode) {
        SandboxExecution execution = executionRepositoryPort.saveExecution(failedExecution(session, reasonCode));
        auditSupport.appendExecutionAudit(session, execution, 0, 0, reasonCode);
        return SandboxExecutionResult.failed(execution, reasonCode);
    }
    private SandboxSession saveSession(SandboxSession session, AuditEventType auditEventType) {
        SandboxSession saved = persistSession(session);
        auditSupport.appendSessionAudit(saved, auditEventType);
        return saved;
    }

    private SandboxSession persistSession(SandboxSession session) {
        SandboxSession saved = sessionRepositoryPort.saveSession(session);
        sessions.put(saved.sessionId(), saved);
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

    private SandboxSession requireReadableSession(SandboxSession session) {
        if (runRepository == null || currentUserPort == null) {
            return session;
        }
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        runRepository.findRunById(session.runId())
                .map(run -> {
                    if (isAdmin(currentUser) || ownsRun(run, currentUser)) {
                        return run;
                    }
                    throw new IllegalStateException(ACCESS_DENIED);
                })
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found"));
        return session;
    }

    private boolean canReadSession(SandboxSession session) {
        try {
            requireReadableSession(session);
            return true;
        } catch (IllegalArgumentException | IllegalStateException ex) {
            return false;
        }
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.hasRole(ADMIN_ROLE);
    }

    private String currentUserId(CurrentUser currentUser) {
        return currentUser == null ? null : currentUser.operator();
    }

    private boolean ownsRun(AgentRun run, CurrentUser currentUser) {
        if (currentUser == null) {
            return false;
        }
        String numericUserId = currentUser.userId() == null ? null : String.valueOf(currentUser.userId());
        return Objects.equals(run.userId(), numericUserId)
                || Objects.equals(run.userId(), currentUserId(currentUser));
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
    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static int normalizeSessionListLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SESSION_LIST_LIMIT;
        }
        return Math.min(limit, MAX_SESSION_LIST_LIMIT);
    }
}

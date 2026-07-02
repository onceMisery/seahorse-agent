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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeContainerReapResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeCleanupResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxExecutionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDetailDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxRuntimeInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.SandboxSessionSweepResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScanResult;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactScannerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxPolicyRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.ObjectStoragePort;
import com.miracle.ai.seahorse.agent.ports.outbound.storage.StoredObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class KernelSandboxRuntimeService implements SandboxRuntimeInboundPort {

    private static final String SESSION_ID_PREFIX = "sandbox_";
    private static final String EXECUTION_ID_PREFIX = "sandbox_exec_";
    private static final String AUDIT_ID_PREFIX = "audit_sandbox_";
    private static final String AUDIT_ACTOR_ID = "sandbox-runtime";
    private static final String RESOURCE_TYPE_SANDBOX_SESSION = "SANDBOX_SESSION";
    private static final String RESOURCE_TYPE_SANDBOX_EXECUTION = "SANDBOX_EXECUTION";
    private static final String SANDBOX_ARTIFACT_BUCKET = "sandbox-artifacts";
    private static final int DEFAULT_SESSION_LIST_LIMIT = 20;
    private static final int MAX_SESSION_LIST_LIMIT = 100;
    private static final String DOWNLOAD_BLOCKED = "Sandbox artifact is not available for download";
    private static final String UNSAFE_STORAGE_REF_BLOCKED =
            "Sandbox artifact storage reference is not available through the download endpoint";
    private static final Map<String, String> FILE_EXTENSIONS = Map.ofEntries(
            Map.entry("text/html", ".html"),
            Map.entry("text/markdown", ".md"),
            Map.entry("text/plain", ".txt"),
            Map.entry("text/csv", ".csv"),
            Map.entry("application/json", ".json"),
            Map.entry("application/pdf", ".pdf"),
            Map.entry("image/png", ".png"),
            Map.entry("image/jpeg", ".jpg"),
            Map.entry("image/svg+xml", ".svg"));

    private final SandboxPolicyPort policyPort;
    private final SandboxRuntimePort runtimePort;
    private final SandboxArtifactPort artifactPort;
    private final SandboxArtifactScannerPort artifactScannerPort;
    private final ObjectStoragePort artifactStoragePort;
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
                new DefaultSandboxArtifactScannerPort(),
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
                new DefaultSandboxArtifactScannerPort(),
                null,
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
        this(policyPort,
                runtimePort,
                artifactPort,
                sessionRepositoryPort,
                executionRepositoryPort,
                artifactQueryPort,
                new DefaultSandboxArtifactScannerPort(),
                null,
                auditLedger,
                clock);
    }

    public KernelSandboxRuntimeService(SandboxPolicyPort policyPort,
                                       SandboxRuntimePort runtimePort,
                                       SandboxArtifactPort artifactPort,
                                       SandboxSessionRepositoryPort sessionRepositoryPort,
                                       SandboxExecutionRepositoryPort executionRepositoryPort,
                                       SandboxArtifactQueryPort artifactQueryPort,
                                       SandboxArtifactScannerPort artifactScannerPort,
                                       KernelAuditLedgerService auditLedger,
                                       Clock clock) {
        this(policyPort,
                runtimePort,
                artifactPort,
                sessionRepositoryPort,
                executionRepositoryPort,
                artifactQueryPort,
                artifactScannerPort,
                null,
                auditLedger,
                clock);
    }

    public KernelSandboxRuntimeService(SandboxPolicyPort policyPort,
                                       SandboxRuntimePort runtimePort,
                                       SandboxArtifactPort artifactPort,
                                       SandboxSessionRepositoryPort sessionRepositoryPort,
                                       SandboxExecutionRepositoryPort executionRepositoryPort,
                                       SandboxArtifactQueryPort artifactQueryPort,
                                       SandboxArtifactScannerPort artifactScannerPort,
                                       ObjectStoragePort artifactStoragePort,
                                       KernelAuditLedgerService auditLedger,
                                       Clock clock) {
        this.policyPort = Objects.requireNonNull(policyPort, "policyPort must not be null");
        this.runtimePort = Objects.requireNonNull(runtimePort, "runtimePort must not be null");
        this.artifactPort = Objects.requireNonNull(artifactPort, "artifactPort must not be null");
        this.artifactScannerPort = Objects.requireNonNull(artifactScannerPort,
                "artifactScannerPort must not be null");
        this.artifactStoragePort = artifactStoragePort;
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
        String profileId = SandboxSession.profileIdOrDefault(safeCommand.profileId(), safeCommand.runtimeType());
        Instant expiresAt = safeCommand.expiresAt() == null
                ? SandboxSession.defaultExpiresAt(clock.instant())
                : safeCommand.expiresAt();
        if (!decision.allowsExecution()) {
            Instant now = clock.instant();
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
        SandboxSession session = runtimePort.createSession(new SandboxSessionRequest(
                safeCommand.tenantId(),
                safeCommand.runId(),
                safeCommand.runtimeType(),
                safeCommand.networkRequested(),
                safeCommand.requestedHosts(),
                profileId,
                expiresAt));
        SandboxSession governed = session.withRuntimeGovernance(
                profileId,
                expiresAt);
        return saveSession(governed, AuditEventType.SANDBOX_SESSION_CREATED);
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
        List<SandboxArtifact> savedArtifacts = result.artifacts().stream()
                .map(this::persistArtifact)
                .toList();
        List<SandboxArtifact> visibleArtifacts = savedArtifacts.stream()
                .filter(SandboxArtifact::promptVisible)
                .toList();
        appendExecutionAudit(session,
                savedExecution,
                savedArtifacts.size(),
                visibleArtifacts.size(),
                result.reasonCode());
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
    public List<SandboxSession> listSessions(String tenantId, int limit) {
        String safeTenantId = requireText(tenantId, "tenantId must not be blank");
        int safeLimit = normalizeSessionListLimit(limit);
        List<SandboxSession> records = sessionRepositoryPort.listSessionsByTenant(safeTenantId, safeLimit);
        records.forEach(session -> sessions.put(session.sessionId(), session));
        return records;
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
                runtimePort.closeSession(session);
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
    public SandboxRuntimeContainerReapResult reapOrphanedRuntimeContainers(boolean dryRun) {
        Set<String> activeSessionIds = sessionRepositoryPort.listActiveSessionIds();
        return runtimePort.reapOrphanedContainers(activeSessionIds, dryRun);
    }

    @Override
    public List<SandboxExecution> listExecutions(String sessionId) {
        String safeSessionId = requireText(sessionId, "sessionId must not be blank");
        findSessionOrThrow(safeSessionId);
        return executionRepositoryPort.listExecutionsBySession(safeSessionId);
    }

    @Override
    public List<SandboxArtifact> listArtifacts(String sessionId) {
        String safeSessionId = requireText(sessionId, "sessionId must not be blank");
        findSessionOrThrow(safeSessionId);
        return artifactQueryPort.listArtifactsBySession(safeSessionId);
    }

    @Override
    public SandboxArtifactDetailDecision describeArtifact(String artifactId) {
        SandboxArtifact artifact = findArtifactWithSession(artifactId);
        SandboxArtifactDownloadPolicy policy = downloadPolicy(artifact);
        return new SandboxArtifactDetailDecision(
                artifact,
                artifact.mediaType(),
                artifactFilename(artifact),
                policy.downloadable(),
                policy.blockedReason());
    }

    @Override
    public SandboxArtifactDownloadDecision downloadArtifact(String artifactId) {
        SandboxArtifact artifact = findArtifactWithSession(artifactId);
        SandboxArtifactDownloadPolicy policy = downloadPolicy(artifact);
        if (!policy.downloadable()) {
            throw new IllegalStateException(policy.blockedReason());
        }
        return new SandboxArtifactDownloadDecision(
                artifact,
                artifact.mediaType(),
                artifactFilename(artifact),
                artifact.objectUri());
    }

    private SandboxArtifact findArtifactWithSession(String artifactId) {
        SandboxArtifact artifact = artifactQueryPort.findArtifactById(
                        requireText(artifactId, "artifactId must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("Sandbox artifact not found"));
        findSessionOrThrow(artifact.sessionId());
        return artifact;
    }

    private SandboxArtifactDownloadPolicy downloadPolicy(SandboxArtifact artifact) {
        if (!artifact.promptVisible()) {
            return SandboxArtifactDownloadPolicy.blocked(DOWNLOAD_BLOCKED);
        }
        if (isUnsafeDownloadReference(artifact.objectUri())) {
            return SandboxArtifactDownloadPolicy.blocked(UNSAFE_STORAGE_REF_BLOCKED);
        }
        return SandboxArtifactDownloadPolicy.allowed();
    }

    private SandboxExecutionResult failedResult(SandboxSession session, SandboxPolicyReasonCode reasonCode) {
        SandboxExecution execution = executionRepositoryPort.saveExecution(failedExecution(session, reasonCode));
        appendExecutionAudit(session, execution, 0, 0, reasonCode);
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

    private SandboxArtifact scanArtifact(SandboxArtifact artifact) {
        try {
            SandboxArtifactScanResult result = Objects.requireNonNull(
                    artifactScannerPort.scan(new SandboxArtifactScanRequest(artifact)),
                    "artifact scan result must not be null");
            return artifact.withScanDecision(result.scanStatus(), result.sensitivity());
        } catch (RuntimeException ex) {
            return artifact.withScanDecision(SandboxArtifactScanStatus.BLOCKED, ContextSensitivity.SECRET);
        }
    }

    private SandboxArtifact persistArtifact(SandboxArtifact artifact) {
        SandboxArtifact scanned = scanArtifact(artifact);
        SandboxArtifact prepared = copyPromptVisibleFileArtifact(scanned);
        try {
            return artifactPort.save(prepared);
        } catch (RuntimeException ex) {
            cleanupCopiedArtifact(scanned, prepared);
            throw ex;
        }
    }

    private SandboxArtifact copyPromptVisibleFileArtifact(SandboxArtifact artifact) {
        if (artifactStoragePort == null || !artifact.promptVisible() || !isFileUri(artifact.objectUri())) {
            return artifact;
        }
        try {
            Path path = Path.of(URI.create(artifact.objectUri())).toAbsolutePath().normalize();
            if (!Files.isRegularFile(path)) {
                return artifact.withScanDecision(SandboxArtifactScanStatus.BLOCKED, ContextSensitivity.SECRET);
            }
            long size = Files.size(path);
            artifactStoragePort.ensureBucket(SANDBOX_ARTIFACT_BUCKET);
            try (InputStream input = Files.newInputStream(path)) {
                StoredObject stored = artifactStoragePort.reliableUpload(
                        SANDBOX_ARTIFACT_BUCKET,
                        input,
                        size,
                        artifactFilename(artifact, path),
                        artifact.mediaType());
                return artifact.withObjectUri(stored.url());
            }
        } catch (IOException | RuntimeException ex) {
            return artifact.withScanDecision(SandboxArtifactScanStatus.BLOCKED, ContextSensitivity.SECRET);
        }
    }

    private void cleanupCopiedArtifact(SandboxArtifact scanned, SandboxArtifact prepared) {
        if (artifactStoragePort == null || Objects.equals(scanned.objectUri(), prepared.objectUri())) {
            return;
        }
        try {
            artifactStoragePort.deleteByUrl(prepared.objectUri());
        } catch (RuntimeException ignored) {
            // Preserve the original persistence failure.
        }
    }

    private String artifactFilename(SandboxArtifact artifact, Path path) {
        Path filename = path.getFileName();
        if (filename != null && hasText(filename.toString())) {
            return filename.toString();
        }
        return artifact.artifactId() + ".bin";
    }

    private boolean isFileUri(String objectUri) {
        try {
            return "file".equalsIgnoreCase(URI.create(objectUri).getScheme());
        } catch (RuntimeException ex) {
            return false;
        }
    }

    private boolean isUnsafeDownloadReference(String objectUri) {
        try {
            String scheme = URI.create(objectUri).getScheme();
            if (!hasText(scheme)) {
                return true;
            }
            String normalized = scheme.toLowerCase(Locale.ROOT);
            return "file".equals(normalized) || "http".equals(normalized) || "https".equals(normalized);
        } catch (RuntimeException ex) {
            return true;
        }
    }

    private String artifactFilename(SandboxArtifact artifact) {
        String safeBase = artifact.artifactId().replaceAll("[^A-Za-z0-9._-]", "_");
        String extension = FILE_EXTENSIONS.getOrDefault(normalizedMediaType(artifact.mediaType()), ".bin");
        if (safeBase.toLowerCase(Locale.ROOT).endsWith(extension)) {
            return safeBase;
        }
        return safeBase + extension;
    }

    private String normalizedMediaType(String mediaType) {
        int separator = mediaType.indexOf(';');
        String base = separator >= 0 ? mediaType.substring(0, separator) : mediaType;
        return base.trim().toLowerCase(Locale.ROOT);
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
                        {"sessionId":"%s","runtimeType":"%s","profileId":"%s","expiresAt":"%s","status":"%s","reasonCode":"%s"}
                        """.formatted(session.sessionId(),
                        session.runtimeType().name(),
                        session.profileId(),
                        session.expiresAt(),
                        session.status().name(),
                        session.reasonCode().name()),
                now));
    }

    private void appendExecutionAudit(SandboxSession session,
                                      SandboxExecution execution,
                                      int artifactCount,
                                      int promptVisibleArtifactCount,
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
                        {"sessionId":"%s","executionId":"%s","runtimeType":"%s","status":"%s","reasonCode":"%s","artifactCount":%d,"promptVisibleArtifactCount":%d}
                        """.formatted(session.sessionId(),
                        execution.executionId(),
                        execution.runtimeType().name(),
                        execution.status().name(),
                        reasonCode.name(),
                        artifactCount,
                        promptVisibleArtifactCount),
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

    private static int normalizeSessionListLimit(int limit) {
        if (limit <= 0) {
            return DEFAULT_SESSION_LIST_LIMIT;
        }
        return Math.min(limit, MAX_SESSION_LIST_LIMIT);
    }

    private record SandboxArtifactDownloadPolicy(boolean downloadable, String blockedReason) {

        private static SandboxArtifactDownloadPolicy allowed() {
            return new SandboxArtifactDownloadPolicy(true, null);
        }

        private static SandboxArtifactDownloadPolicy blocked(String reason) {
            return new SandboxArtifactDownloadPolicy(false, reason);
        }
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

        @Override
        public List<SandboxSession> listSessionsByTenant(String tenantId, int limit) {
            if (!hasText(tenantId)) {
                return List.of();
            }
            String safeTenantId = tenantId.trim();
            int safeLimit = normalizeSessionListLimit(limit);
            return store.values().stream()
                    .filter(session -> session.tenantId().equals(safeTenantId))
                    .sorted(Comparator.comparing(SandboxSession::updatedAt)
                            .thenComparing(SandboxSession::createdAt)
                            .thenComparing(SandboxSession::sessionId)
                            .reversed())
                    .limit(safeLimit)
                    .toList();
        }

        @Override
        public List<SandboxSession> listExpiredActiveSessions(String tenantId, Instant now, int limit) {
            if (!hasText(tenantId) || now == null) {
                return List.of();
            }
            String safeTenantId = tenantId.trim();
            int safeLimit = normalizeSessionListLimit(limit);
            return store.values().stream()
                    .filter(session -> session.tenantId().equals(safeTenantId))
                    .filter(session -> !session.status().isTerminal())
                    .filter(session -> !session.expiresAt().isAfter(now))
                    .sorted(Comparator.comparing(SandboxSession::expiresAt)
                            .thenComparing(SandboxSession::createdAt)
                            .thenComparing(SandboxSession::sessionId))
                    .limit(safeLimit)
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

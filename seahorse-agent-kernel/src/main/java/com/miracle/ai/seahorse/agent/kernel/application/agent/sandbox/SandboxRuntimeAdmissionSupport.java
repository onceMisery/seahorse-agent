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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeEndpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeHealth;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRemoteRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeCapacityReservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeNodeRegistryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimePort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeSessionOwnership;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRequest;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 沙箱运行时准入/放置协作者（从 {@link KernelSandboxRuntimeService} 提取）。
 * 按 §7 收敛原则外提：只负责运行时候选枚举、容量预留、创建故障转移与远端端点解析。
 */
final class SandboxRuntimeAdmissionSupport {

    static final int MAX_RUNTIME_CREATE_ATTEMPTS = 2;

    private static final String CAPACITY_RESERVATION_ID_PREFIX = "sandbox_res_";
    private static final String CREATE_OPERATION_ID_PREFIX = "sandbox_create_";
    private static final Duration CAPACITY_RESERVATION_LEASE_TTL = Duration.ofMinutes(5);

    private final SandboxRuntimePort runtimePort;
    private final SandboxRemoteRuntimePort remoteRuntimePort;
    private final SandboxRuntimeNodeRegistryPort runtimeNodeRegistryPort;
    private final SandboxRuntimeCapacityReservationPort capacityReservationPort;
    private final SandboxSessionRepositoryPort sessionRepositoryPort;

    SandboxRuntimeAdmissionSupport(SandboxRuntimePort runtimePort,
                                   SandboxRemoteRuntimePort remoteRuntimePort,
                                   SandboxRuntimeNodeRegistryPort runtimeNodeRegistryPort,
                                   SandboxRuntimeCapacityReservationPort capacityReservationPort,
                                   SandboxSessionRepositoryPort sessionRepositoryPort) {
        this.runtimePort = Objects.requireNonNull(runtimePort, "runtimePort must not be null");
        this.remoteRuntimePort = remoteRuntimePort;
        this.runtimeNodeRegistryPort = runtimeNodeRegistryPort;
        this.capacityReservationPort = capacityReservationPort;
        this.sessionRepositoryPort = Objects.requireNonNull(sessionRepositoryPort,
                "sessionRepositoryPort must not be null");
    }

    List<RuntimeAdmissionDecision> runtimeAdmissionCandidates(String requiredRuntimeNodeId) {
        Set<String> activeSessionIds = sessionRepositoryPort.listActiveSessionIds();
        SandboxRuntimeHealth health = Objects.requireNonNull(
                runtimePort.inspectHealth(activeSessionIds),
                "runtime health result must not be null");
        if (SandboxRuntimeHealth.STATUS_UNSUPPORTED.equals(health.status())) {
            return hasText(requiredRuntimeNodeId)
                    ? List.of(remoteRuntimeAdmissionDecision(requiredRuntimeNodeId))
                    : automaticRuntimeAdmissionCandidates(null, RuntimeAdmissionDecision.allowedLocal(null));
        }
        SandboxRuntimeNodeHealth node = SandboxRuntimeNodeHealth.fromHealth(health);
        if (hasText(requiredRuntimeNodeId) && !requiredRuntimeNodeId.equals(node.nodeId())) {
            return List.of(remoteRuntimeAdmissionDecision(requiredRuntimeNodeId));
        }
        RuntimeAdmissionDecision localDecision = switch (node.admissionStatus()) {
            case SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE,
                 SandboxRuntimeNodeHealth.ADMISSION_DEGRADED -> RuntimeAdmissionDecision.allowedLocal(node.nodeId());
            case SandboxRuntimeNodeHealth.ADMISSION_DRAINING ->
                    RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_NODE_DRAINING);
            case SandboxRuntimeNodeHealth.ADMISSION_DISK_LOW ->
                    RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_WORKSPACE_DISK_LOW);
            case SandboxRuntimeNodeHealth.ADMISSION_SATURATED ->
                    RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_CAPACITY_EXCEEDED);
            default -> RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE);
        };
        return hasText(requiredRuntimeNodeId)
                ? List.of(localDecision)
                : automaticRuntimeAdmissionCandidates(node, localDecision);
    }

    private List<RuntimeAdmissionDecision> automaticRuntimeAdmissionCandidates(SandboxRuntimeNodeHealth localNode,
                                                                               RuntimeAdmissionDecision localDecision) {
        List<RuntimePlacementCandidate> candidates = new ArrayList<>();
        Set<String> candidateNodeIds = new HashSet<>();
        if (localNode != null && localDecision.rejectionReason() == null) {
            candidates.add(RuntimePlacementCandidate.local(localNode));
            candidateNodeIds.add(localNode.nodeId());
        }
        if (remoteRuntimePort != null && runtimeNodeRegistryPort != null) {
            List<SandboxRuntimeNodeEndpoint> endpoints;
            try {
                endpoints = runtimeNodeRegistryPort.listLiveEndpoints();
            } catch (RuntimeException ex) {
                return List.of(localDecision);
            }
            String localNodeId = localNode == null ? null : localNode.nodeId();
            endpoints.stream()
                    .filter(endpoint -> !endpoint.nodeId().equals(localNodeId))
                    .filter(SandboxRuntimeAdmissionSupport::allowsAutomaticPlacement)
                    .filter(endpoint -> candidateNodeIds.add(endpoint.nodeId()))
                    .map(RuntimePlacementCandidate::remote)
                    .forEach(candidates::add);
        }
        if (candidates.isEmpty()) {
            return List.of(localDecision);
        }
        return candidates.stream()
                .sorted(RuntimePlacementCandidate.ORDER)
                .map(RuntimePlacementCandidate::admissionDecision)
                .toList();
    }

    RuntimeAdmissionDecision reserveRuntimeAdmission(RuntimeAdmissionDecision decision) {
        if (decision.rejectionReason() != null
                || !hasText(decision.runtimeNodeId())
                || capacityReservationPort == null) {
            return decision;
        }
        String reservationId = CAPACITY_RESERVATION_ID_PREFIX + SnowflakeIds.nextIdString();
        try {
            return switch (capacityReservationPort.tryReserve(
                    decision.runtimeNodeId(),
                    reservationId,
                    CAPACITY_RESERVATION_LEASE_TTL)) {
                case RESERVED -> decision.withCapacityReservation(reservationId);
                case NOT_REQUIRED -> decision;
                case REJECTED -> RuntimeAdmissionDecision.rejected(
                        SandboxPolicyReasonCode.RUNTIME_CAPACITY_EXCEEDED);
            };
        } catch (RuntimeException ex) {
            return RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE);
        }
    }

    void releaseCapacityReservation(RuntimeAdmissionDecision decision) {
        if (capacityReservationPort == null || !hasText(decision.capacityReservationId())) {
            return;
        }
        try {
            capacityReservationPort.release(decision.capacityReservationId());
        } catch (RuntimeException ignored) {
            // The bounded database lease remains the recovery path when an explicit release fails.
        }
    }

    boolean rollbackCreatedRuntimeSession(RuntimeAdmissionDecision decision, SandboxSession session) {
        try {
            SandboxSession runtimeClosed = decision.remoteEndpoint() == null
                    ? runtimePort.closeSession(session)
                    : remoteRuntimePort.closeSession(decision.remoteEndpoint(), session);
            requireConfirmedRuntimeClose(session, runtimeClosed);
            return true;
        } catch (RuntimeException ignored) {
            return false;
        }
    }

    RuntimeCreateRecovery recoverFailedRuntimeCreate(RuntimeAdmissionDecision decision, SandboxSession session) {
        if (decision.remoteEndpoint() == null) {
            return RuntimeCreateRecovery.releaseOnly(rollbackCreatedRuntimeSession(decision, session));
        }
        try {
            SandboxRuntimeSessionOwnership ownership = Objects.requireNonNull(
                    remoteRuntimePort.inspectSessionOwnership(decision.remoteEndpoint(), session.sessionId()),
                    "runtime session ownership result must not be null");
            return switch (ownership) {
                case ABSENT -> RuntimeCreateRecovery.safeToFailOver(RuntimeCreateFailoverRecovery.ABSENT);
                case OWNED -> RuntimeCreateRecovery.safeToFailOver(
                        rollbackCreatedRuntimeSession(decision, session),
                        RuntimeCreateFailoverRecovery.CLOSED);
                case UNSUPPORTED -> RuntimeCreateRecovery.releaseOnly(
                        rollbackCreatedRuntimeSession(decision, session));
            };
        } catch (RuntimeException ignored) {
            return RuntimeCreateRecovery.releaseOnly(rollbackCreatedRuntimeSession(decision, session));
        }
    }

    SandboxSession createLocalRuntimeSession(String runtimeNodeId, SandboxSessionRequest request) {
        if (runtimeNodeRegistryPort == null) {
            return runtimePort.createSession(request);
        }
        String operationId = CREATE_OPERATION_ID_PREFIX + SnowflakeIds.nextIdString();
        if (!runtimeNodeRegistryPort.beginCreateOperation(runtimeNodeId, operationId)) {
            throw new IllegalStateException("Sandbox local runtime create operation could not be tracked");
        }
        try {
            return runtimePort.createSession(request);
        } finally {
            if (!runtimeNodeRegistryPort.endCreateOperation(runtimeNodeId, operationId)) {
                throw new IllegalStateException("Sandbox local runtime create operation tracking could not be released");
            }
        }
    }

    Optional<SandboxRuntimeNodeEndpoint> remoteEndpointFor(SandboxSession session) {
        if (!hasText(session.runtimeNodeId())) {
            return Optional.empty();
        }
        SandboxRuntimeHealth localHealth = Objects.requireNonNull(
                runtimePort.inspectHealth(sessionRepositoryPort.listActiveSessionIds()),
                "runtime health result must not be null");
        if (!SandboxRuntimeHealth.STATUS_UNSUPPORTED.equals(localHealth.status())
                && session.runtimeNodeId().equals(SandboxRuntimeNodeHealth.fromHealth(localHealth).nodeId())) {
            return Optional.empty();
        }
        if (remoteRuntimePort == null || runtimeNodeRegistryPort == null) {
            throw new IllegalStateException("Sandbox remote runtime transport is unavailable");
        }
        return Optional.of(runtimeNodeRegistryPort.findLiveEndpoint(session.runtimeNodeId())
                .orElseThrow(() -> new IllegalStateException("Sandbox remote runtime node is unavailable")));
    }

    RuntimeAdmissionDecision remoteRuntimeAdmissionDecision(String requiredRuntimeNodeId) {
        if (remoteRuntimePort == null || runtimeNodeRegistryPort == null) {
            return RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE);
        }
        Optional<SandboxRuntimeNodeEndpoint> endpoint = runtimeNodeRegistryPort.findLiveEndpoint(
                requiredRuntimeNodeId);
        if (endpoint.isEmpty()) {
            return RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE);
        }
        return switch (endpoint.get().observedAdmissionStatus()) {
            case SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE,
                 SandboxRuntimeNodeHealth.ADMISSION_DEGRADED -> endpoint.get().observedAdmissionAvailable()
                    ? RuntimeAdmissionDecision.allowedRemote(endpoint.get())
                    : RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE);
            case SandboxRuntimeNodeHealth.ADMISSION_DRAINING ->
                    RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_NODE_DRAINING);
            case SandboxRuntimeNodeHealth.ADMISSION_DISK_LOW ->
                    RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_WORKSPACE_DISK_LOW);
            case SandboxRuntimeNodeHealth.ADMISSION_SATURATED ->
                    RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_CAPACITY_EXCEEDED);
            default -> RuntimeAdmissionDecision.rejected(SandboxPolicyReasonCode.RUNTIME_NODE_UNAVAILABLE);
        };
    }

    private static boolean allowsAutomaticPlacement(SandboxRuntimeNodeEndpoint endpoint) {
        return endpoint.observedAdmissionAvailable()
                && (SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE.equals(endpoint.observedAdmissionStatus())
                || SandboxRuntimeNodeHealth.ADMISSION_DEGRADED.equals(endpoint.observedAdmissionStatus()));
    }

    static void requireConfirmedRuntimeClose(SandboxSession expected, SandboxSession actual) {
        if (actual == null
                || !expected.sessionId().equals(actual.sessionId())
                || actual.status() != SandboxExecutionStatus.CANCELLED) {
            throw new IllegalStateException("runtime closeSession result did not confirm cleanup");
        }
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    record RuntimeAdmissionDecision(String runtimeNodeId,
                                            SandboxRuntimeNodeEndpoint remoteEndpoint,
                                            SandboxPolicyReasonCode rejectionReason,
                                            String capacityReservationId) {

        private static RuntimeAdmissionDecision allowedLocal(String runtimeNodeId) {
            return new RuntimeAdmissionDecision(runtimeNodeId, null, null, null);
        }

        private static RuntimeAdmissionDecision allowedRemote(SandboxRuntimeNodeEndpoint endpoint) {
            return new RuntimeAdmissionDecision(endpoint.nodeId(), endpoint, null, null);
        }

        private static RuntimeAdmissionDecision rejected(SandboxPolicyReasonCode reasonCode) {
            return new RuntimeAdmissionDecision(
                    null,
                    null,
                    Objects.requireNonNull(reasonCode, "reasonCode must not be null"),
                    null);
        }

        private RuntimeAdmissionDecision withCapacityReservation(String reservationId) {
            return new RuntimeAdmissionDecision(runtimeNodeId, remoteEndpoint, rejectionReason, reservationId);
        }
    }

    enum RuntimeCreateFailoverRecovery {
        ABSENT,
        CLOSED
    }

    record RuntimeCreateFailoverAudit(String fromNodeId, RuntimeCreateFailoverRecovery recovery) {

        RuntimeCreateFailoverAudit {
            fromNodeId = requireText(fromNodeId, "fromNodeId must not be blank");
            recovery = Objects.requireNonNull(recovery, "recovery must not be null");
        }
    }

    record RuntimeCreateRecovery(boolean releaseReservation,
                                         boolean failoverAllowed,
                                         RuntimeCreateFailoverRecovery failoverRecovery) {

        private static RuntimeCreateRecovery releaseOnly(boolean cleanupConfirmed) {
            return new RuntimeCreateRecovery(cleanupConfirmed, false, null);
        }

        private static RuntimeCreateRecovery safeToFailOver(RuntimeCreateFailoverRecovery recovery) {
            return new RuntimeCreateRecovery(true, true, recovery);
        }

        private static RuntimeCreateRecovery safeToFailOver(boolean cleanupConfirmed,
                                                            RuntimeCreateFailoverRecovery recovery) {
            return new RuntimeCreateRecovery(cleanupConfirmed, cleanupConfirmed,
                    cleanupConfirmed ? recovery : null);
        }
    }

    private record RuntimePlacementCandidate(String nodeId,
                                             SandboxRuntimeNodeEndpoint remoteEndpoint,
                                             String admissionStatus,
                                             int activeSessionCount,
                                             int activeSessionLimit,
                                             long workspaceFreeBytes) {

        private static final Comparator<RuntimePlacementCandidate> ORDER = Comparator
                .comparingInt(RuntimePlacementCandidate::admissionRank)
                .thenComparingDouble(RuntimePlacementCandidate::utilization)
                .thenComparingInt(RuntimePlacementCandidate::activeSessionCount)
                .thenComparing(Comparator.comparingLong(RuntimePlacementCandidate::workspaceFreeBytes).reversed())
                .thenComparing(RuntimePlacementCandidate::nodeId);

        private static RuntimePlacementCandidate local(SandboxRuntimeNodeHealth node) {
            return new RuntimePlacementCandidate(
                    node.nodeId(),
                    null,
                    node.admissionStatus(),
                    node.activeSessionCount(),
                    node.activeSessionLimit(),
                    node.workspaceFreeBytes());
        }

        private static RuntimePlacementCandidate remote(SandboxRuntimeNodeEndpoint endpoint) {
            return new RuntimePlacementCandidate(
                    endpoint.nodeId(),
                    endpoint,
                    endpoint.observedAdmissionStatus(),
                    endpoint.observedActiveSessionCount(),
                    endpoint.observedActiveSessionLimit(),
                    endpoint.observedWorkspaceFreeBytes());
        }

        private double utilization() {
            return activeSessionLimit <= 0 ? 0D : (double) activeSessionCount / activeSessionLimit;
        }

        private int admissionRank() {
            return SandboxRuntimeNodeHealth.ADMISSION_AVAILABLE.equals(admissionStatus) ? 0 : 1;
        }

        private RuntimeAdmissionDecision admissionDecision() {
            return remoteEndpoint == null
                    ? RuntimeAdmissionDecision.allowedLocal(nodeId)
                    : RuntimeAdmissionDecision.allowedRemote(remoteEndpoint);
        }
    }

    private static String requireText(String value, String message) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

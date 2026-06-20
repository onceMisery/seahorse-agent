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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryHealthReport;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReadinessReport;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReadinessReport.MemoryReadinessCapability;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;

import java.util.List;
import java.util.Locale;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.time.Instant;

public class KernelMemoryManagementService implements MemoryManagementInboundPort {

    private static final int DEFAULT_LIMIT = 20;

    private final MemoryManagementServicePorts ports;

    public KernelMemoryManagementService(MemoryManagementServicePorts ports) {
        this.ports = Objects.requireNonNull(ports, "ports must not be null");
    }

    @Override
    public MemoryPage listMemories(String userId, String layer, String conversationId, int limit) {
        MemoryStorePort storePort = store(layer);
        int safeLimit = limit <= 0 ? DEFAULT_LIMIT : limit;
        List<MemoryRecord> records = hasText(conversationId)
                ? storePort.listByConversation(conversationId.trim(), safeLimit)
                : storePort.listByUser(requireText(userId, "userId"), safeLimit);
        return new MemoryPage(normalizeLayer(layer), records);
    }

    @Override
    public Optional<MemoryRecord> findMemory(String layer, String memoryId) {
        return store(layer).findById(requireText(memoryId, "memoryId"));
    }

    @Override
    public boolean deleteMemory(String layer, String memoryId) {
        return store(layer).deleteById(requireText(memoryId, "memoryId"));
    }

    @Override
    public List<MemoryQualitySnapshot> listQualitySnapshots(String userId, int limit) {
        return ports.qualitySnapshotRepositoryPort()
                .listByUser(requireText(userId, "userId"), limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public List<MemoryConflictRecord> listConflicts(String userId, String status, int limit) {
        return ports.conflictLogRepositoryPort()
                .listByUser(requireText(userId, "userId"), status, limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public boolean resolveConflict(String conflictId, String action, String resolvedBy) {
        return ports.conflictLogRepositoryPort()
                .resolve(requireText(conflictId, "conflictId"), requireText(action, "action"),
                        Objects.requireNonNullElse(resolvedBy, "").trim());
    }

    @Override
    public List<ProfileFact> listProfileFacts(String userId, String tenantId, int limit) {
        return ports.profileMemoryPort()
                .listActive(requireText(userId, "userId"), tenantId, limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public boolean disableProfileFact(String userId, String tenantId, String slotKey, String operator) {
        return ports.profileMemoryPort()
                .disable(requireText(userId, "userId"), defaultTenant(tenantId),
                        requireText(slotKey, "slotKey"), Instant.now());
    }

    @Override
    public List<CorrectionRule> listCorrectionRules(String userId, String tenantId, int limit) {
        return ports.correctionLedgerPort()
                .listActive(requireText(userId, "userId"), tenantId, limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public List<MemoryOperationRecord> listOperations(String userId, String tenantId, String status, int limit) {
        return ports.operationLogPort()
                .listByUser(requireText(userId, "userId"), tenantId, status, limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public List<MemoryOutboxPort.MemoryOutboxTask> listOutboxTasks(int limit) {
        return ports.outboxPort().pollPending(limit <= 0 ? DEFAULT_LIMIT : limit);
    }

    @Override
    public MemoryHealthReport memoryHealth(String userId, String tenantId) {
        String safeUserId = requireText(userId, "userId");
        String safeTenantId = defaultTenant(tenantId);
        int sampleLimit = 200;
        List<ProfileFact> profileFacts = ports.profileMemoryPort().listActive(safeUserId, safeTenantId, sampleLimit);
        List<CorrectionRule> correctionRules = ports.correctionLedgerPort()
                .listActive(safeUserId, safeTenantId, sampleLimit);
        List<MemoryConflictRecord> pendingConflicts = ports.conflictLogRepositoryPort()
                .listByUser(safeUserId, "PENDING", sampleLimit);
        List<MemoryOutboxPort.MemoryOutboxTask> outboxTasks = ports.outboxPort().pollPending(sampleLimit);
        long pendingReviews = ports.reviewRepositoryPort()
                .pageReviewCandidates(new MemoryReviewQuery(
                        safeTenantId, safeUserId, MemoryReviewStatus.PENDING, 1L, 1L))
                .total();
        List<MemoryOperationRecord> operations = ports.operationLogPort()
                .listByUser(safeUserId, safeTenantId, null, sampleLimit);
        List<MemoryQualitySnapshot> snapshots = ports.qualitySnapshotRepositoryPort().listByUser(safeUserId, 1);
        List<MemoryTraceEvent> traceEvents = ports.traceRecorder()
                .listByUser(safeUserId, safeTenantId, sampleLimit);

        Map<String, Long> operationCounts = operationCounts(operations);
        int operationTotal = operations.size();
        int acceptedCount = countStatus(operations, "SUCCEEDED");
        int rejectedCount = countStatus(operations, "REJECTED");
        int schemaFailures = countReason(operations, "schema");
        int policyBlocks = countReason(operations, "high_risk") + countReason(operations, "policy");
        Map<String, Object> latestSnapshot = snapshots.isEmpty() ? Map.of() : snapshots.get(0).snapshot();

        MemoryPolicyConfig policy = ports.policyConfigPort().current();
        double profileCompleteness = profileCompleteness(profileFacts);
        List<String> alerts = alerts(outboxTasks.size(), schemaFailures, profileCompleteness, policy);
        return new MemoryHealthReport(
                safeUserId,
                safeTenantId,
                profileFacts.size(),
                correctionRules.size(),
                pendingConflicts.size(),
                outboxTasks.size(),
                safeCount(pendingReviews),
                operationCounts,
                rate(acceptedCount, operationTotal),
                rate(rejectedCount, operationTotal),
                schemaFailures,
                policyBlocks,
                profileCompleteness,
                conflictDensity(latestSnapshot, pendingConflicts.size()),
                latestSnapshot,
                traceEvents.size(),
                countTraceFailures(traceEvents),
                traceComponentCounts(traceEvents),
                alerts,
                Instant.now());
    }

    @Override
    public MemoryReadinessReport memoryReadiness(String userId, String tenantId) {
        String safeUserId = requireText(userId, "userId");
        String safeTenantId = defaultTenant(tenantId);
        int sampleLimit = 200;
        List<MemoryOperationRecord> operations = ports.operationLogPort()
                .listByUser(safeUserId, safeTenantId, null, sampleLimit);
        List<MemoryTraceEvent> userTraceEvents = ports.traceRecorder()
                .listByUser(safeUserId, safeTenantId, sampleLimit);
        List<MemoryTraceEvent> tenantTraceEvents = ports.traceRecorder()
                .listRecent(sampleLimit).stream()
                .filter(e -> e != null && safeTenantId.equals(e.tenantId()))
                .toList();
        MemoryPolicyConfig policy = ports.policyConfigPort().current();
        List<MemoryReadinessCapability> capabilities = List.of(
                operationEvidenceCapability(
                        "capture_write_loop",
                        operations,
                        "capture_write_loop has no recent accepted write operation"),
                traceEvidenceCapability(
                        "recall_loop",
                        userTraceEvents,
                        "memory-recall",
                        "recall_loop has no recent memory-recall trace"),
                traceEvidenceCapability(
                        "context_injection",
                        userTraceEvents,
                        "memory-context-weaver",
                        "context_injection has no recent memory-context-weaver trace"),
                reviewCapability(safeUserId, safeTenantId, policy),
                traceEvidenceCapability(
                        "derived_index_sync",
                        userTraceEvents,
                        "memory-outbox",
                        "derived_index_sync has no recent memory-outbox trace"),
                traceEvidenceCapability(
                        "maintenance_loop",
                        tenantTraceEvents,
                        "memory-maintenance",
                        "maintenance_loop has no recent memory-maintenance trace"),
                selfTrainingCapability());
        List<String> gaps = readinessGaps(capabilities);
        return new MemoryReadinessReport(
                safeUserId,
                safeTenantId,
                readinessStatus(capabilities),
                capabilities,
                gaps,
                Instant.now());
    }

    @Override
    public MemoryPolicyConfig memoryPolicyConfig() {
        return ports.policyConfigPort().current();
    }

    @Override
    public MemoryPolicyConfig updatePolicyConfig(MemoryPolicyConfig config) {
        return ports.policyConfigPort().update(config);
    }

    private MemoryStorePort store(String layer) {
        return switch (normalizeLayer(layer)) {
            case "working" -> ports.workingMemoryPort();
            case "short_term" -> ports.shortTermMemoryPort();
            case "long_term" -> ports.longTermMemoryPort();
            case "semantic" -> ports.semanticMemoryPort();
            default -> throw new IllegalArgumentException("unsupported memory layer: " + layer);
        };
    }

    private String normalizeLayer(String layer) {
        return requireText(layer, "layer")
                .toLowerCase(Locale.ROOT)
                .replace("-", "_");
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String defaultTenant(String tenantId) {
        return hasText(tenantId) ? tenantId.trim() : "default";
    }

    private List<MemoryTraceEvent> userTenantTraceEvents(List<MemoryTraceEvent> traceEvents,
                                                         String userId,
                                                         String tenantId) {
        return traceEvents.stream()
                .filter(Objects::nonNull)
                .filter(event -> tenantId.equals(event.tenantId()))
                .filter(event -> userId.equals(event.userId()))
                .toList();
    }

    private List<MemoryTraceEvent> tenantTraceEvents(List<MemoryTraceEvent> traceEvents, String tenantId) {
        return traceEvents.stream()
                .filter(Objects::nonNull)
                .filter(event -> tenantId.equals(event.tenantId()))
                .toList();
    }

    private Map<String, Long> operationCounts(List<MemoryOperationRecord> operations) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MemoryOperationRecord operation : operations) {
            String status = hasText(operation.status()) ? operation.status() : "UNKNOWN";
            counts.put(status, counts.getOrDefault(status, 0L) + 1L);
        }
        return counts;
    }

    private int countStatus(List<MemoryOperationRecord> operations, String status) {
        int count = 0;
        for (MemoryOperationRecord operation : operations) {
            if (status.equals(operation.status())) {
                count++;
            }
        }
        return count;
    }

    private int countReason(List<MemoryOperationRecord> operations, String needle) {
        int count = 0;
        String normalizedNeedle = Objects.requireNonNullElse(needle, "").toLowerCase(Locale.ROOT);
        for (MemoryOperationRecord operation : operations) {
            String error = Objects.requireNonNullElse(operation.errorMessage(), "").toLowerCase(Locale.ROOT);
            String decision = operation.decision().toString().toLowerCase(Locale.ROOT);
            if (error.contains(normalizedNeedle) || decision.contains(normalizedNeedle)) {
                count++;
            }
        }
        return count;
    }

    private double rate(int count, int total) {
        return total <= 0 ? 0D : (double) count / total;
    }

    private double profileCompleteness(List<ProfileFact> profileFacts) {
        if (profileFacts.isEmpty()) {
            return 0D;
        }
        int targetSlots = 4;
        return Math.min(1D, (double) profileFacts.size() / targetSlots);
    }

    private double conflictDensity(Map<String, Object> snapshot, int pendingConflictCount) {
        int totalMemories = number(snapshot.get("shortTermCount"))
                + number(snapshot.get("longTermCount"))
                + number(snapshot.get("semanticCount"));
        return totalMemories <= 0 ? 0D : (double) pendingConflictCount / totalMemories;
    }

    private int number(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return Integer.parseInt(text);
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private int safeCount(long count) {
        return count > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) Math.max(0L, count);
    }

    private int countTraceFailures(List<MemoryTraceEvent> traceEvents) {
        int count = 0;
        for (MemoryTraceEvent event : traceEvents) {
            if (event != null && isFailureStatus(event.status())) {
                count++;
            }
        }
        return count;
    }

    private Map<String, Long> traceComponentCounts(List<MemoryTraceEvent> traceEvents) {
        Map<String, Long> counts = new LinkedHashMap<>();
        for (MemoryTraceEvent event : traceEvents) {
            if (event == null) {
                continue;
            }
            String component = hasText(event.component()) ? event.component() : "memory";
            counts.put(component, counts.getOrDefault(component, 0L) + 1L);
        }
        return counts;
    }

    private MemoryReadinessCapability operationEvidenceCapability(String name,
                                                                  List<MemoryOperationRecord> operations,
                                                                  String missingGap) {
        List<MemoryOperationRecord> evidence = operations.stream()
                .filter(operation -> "SUCCEEDED".equals(operation.status()) || "APPLIED".equals(operation.status()))
                .toList();
        return readinessCapability(
                name,
                true,
                evidence.size(),
                latestOperationTime(evidence),
                missingGap,
                Map.of("operationStatuses", operationCounts(operations)));
    }

    private MemoryReadinessCapability traceEvidenceCapability(String name,
                                                              List<MemoryTraceEvent> traceEvents,
                                                              String component,
                                                              String missingGap) {
        List<MemoryTraceEvent> evidence = traceEvents.stream()
                .filter(event -> component.equals(event.component()))
                .filter(event -> MemoryTraceEvent.STATUS_SUCCESS.equals(event.status()))
                .toList();
        return readinessCapability(
                name,
                true,
                evidence.size(),
                latestTraceTime(evidence),
                missingGap,
                Map.of("component", component));
    }

    private MemoryReadinessCapability reviewCapability(String userId,
                                                       String tenantId,
                                                       MemoryPolicyConfig policy) {
        if (!policy.reviewEnabled()) {
            return new MemoryReadinessCapability(
                    "review_loop",
                    MemoryReadinessReport.STATUS_DISABLED,
                    false,
                    0,
                    Instant.EPOCH,
                    List.of("review_loop is disabled by memory policy"),
                    Map.of("reviewEnabled", false));
        }
        List<MemoryReviewRecord> pending = reviewRecords(userId, tenantId, MemoryReviewStatus.PENDING);
        List<MemoryReviewRecord> applied = reviewRecords(userId, tenantId, MemoryReviewStatus.APPLIED);
        List<MemoryReviewRecord> rejected = reviewRecords(userId, tenantId, MemoryReviewStatus.REJECTED);
        int evidenceCount = pending.size() + applied.size() + rejected.size();
        List<MemoryReviewRecord> evidence = new ArrayList<>();
        evidence.addAll(pending);
        evidence.addAll(applied);
        evidence.addAll(rejected);
        return readinessCapability(
                "review_loop",
                true,
                evidenceCount,
                latestReviewTime(evidence),
                "review_loop is enabled but has no review staging or decision evidence",
                Map.of(
                        "reviewEnabled", true,
                        "pendingCount", pending.size(),
                        "appliedCount", applied.size(),
                        "rejectedCount", rejected.size()));
    }

    private MemoryReadinessCapability selfTrainingCapability() {
        return new MemoryReadinessCapability(
                "self_training_loop",
                MemoryReadinessReport.STATUS_MANUAL_EXPORT_ONLY,
                true,
                0,
                Instant.EPOCH,
                List.of("review feedback can be exported, but automatic SFT/DPO is not part of runtime"),
                Map.of("automaticTraining", false));
    }

    private MemoryReadinessCapability readinessCapability(String name,
                                                          boolean enabled,
                                                          int evidenceCount,
                                                          Instant lastEvidenceAt,
                                                          String missingGap,
                                                          Map<String, Object> details) {
        if (evidenceCount > 0) {
            return new MemoryReadinessCapability(
                    name,
                    MemoryReadinessReport.STATUS_READY,
                    enabled,
                    evidenceCount,
                    lastEvidenceAt,
                    List.of(),
                    details);
        }
        return new MemoryReadinessCapability(
                name,
                MemoryReadinessReport.STATUS_NO_EVIDENCE,
                enabled,
                0,
                Instant.EPOCH,
                List.of(missingGap),
                details);
    }

    private List<MemoryReviewRecord> reviewRecords(String userId, String tenantId, MemoryReviewStatus status) {
        return ports.reviewRepositoryPort()
                .pageReviewCandidates(new MemoryReviewQuery(tenantId, userId, status, 1L, 100L))
                .records();
    }

    private Instant latestOperationTime(List<MemoryOperationRecord> operations) {
        Instant latest = Instant.EPOCH;
        for (MemoryOperationRecord operation : operations) {
            Instant candidate = operation.updatedAt().isAfter(operation.createdAt())
                    ? operation.updatedAt()
                    : operation.createdAt();
            if (candidate.isAfter(latest)) {
                latest = candidate;
            }
        }
        return latest;
    }

    private Instant latestTraceTime(List<MemoryTraceEvent> traceEvents) {
        Instant latest = Instant.EPOCH;
        for (MemoryTraceEvent event : traceEvents) {
            if (event.occurredAt().isAfter(latest)) {
                latest = event.occurredAt();
            }
        }
        return latest;
    }

    private Instant latestReviewTime(List<MemoryReviewRecord> records) {
        Instant latest = Instant.EPOCH;
        for (MemoryReviewRecord record : records) {
            Instant candidate = record.updatedAt().isAfter(record.createdAt())
                    ? record.updatedAt()
                    : record.createdAt();
            if (candidate.isAfter(latest)) {
                latest = candidate;
            }
        }
        return latest;
    }

    private List<String> readinessGaps(List<MemoryReadinessCapability> capabilities) {
        List<String> gaps = new ArrayList<>();
        for (MemoryReadinessCapability capability : capabilities) {
            if (MemoryReadinessReport.STATUS_MANUAL_EXPORT_ONLY.equals(capability.status())) {
                continue;
            }
            gaps.addAll(capability.gaps());
        }
        return gaps;
    }

    private String readinessStatus(List<MemoryReadinessCapability> capabilities) {
        boolean hasRequiredNoEvidence = false;
        boolean hasOptionalGap = false;
        for (MemoryReadinessCapability capability : capabilities) {
            if (MemoryReadinessReport.STATUS_MANUAL_EXPORT_ONLY.equals(capability.status())) {
                continue;
            }
            if (MemoryReadinessReport.STATUS_DISABLED.equals(capability.status())) {
                hasOptionalGap = true;
                continue;
            }
            if (!MemoryReadinessReport.STATUS_READY.equals(capability.status())) {
                if (isRequiredReadinessCapability(capability.name())) {
                    hasRequiredNoEvidence = true;
                } else {
                    hasOptionalGap = true;
                }
            }
        }
        if (hasRequiredNoEvidence) {
            return MemoryReadinessReport.STATUS_NO_EVIDENCE;
        }
        return hasOptionalGap ? MemoryReadinessReport.STATUS_DEGRADED : MemoryReadinessReport.STATUS_READY;
    }

    private boolean isRequiredReadinessCapability(String name) {
        return "capture_write_loop".equals(name)
                || "recall_loop".equals(name)
                || "context_injection".equals(name);
    }

    private boolean isFailureStatus(String status) {
        String normalized = Objects.requireNonNullElse(status, "").trim().toUpperCase(Locale.ROOT);
        return "FAILED".equals(normalized) || "ERROR".equals(normalized);
    }

    private List<String> alerts(int outboxBacklog,
                                int schemaFailures,
                                double profileCompleteness,
                                MemoryPolicyConfig policy) {
        List<String> alerts = new ArrayList<>();
        if (outboxBacklog > policy.outboxBacklogAlertThreshold()) {
            alerts.add("memory.outbox.backlog");
        }
        if (schemaFailures > policy.schemaFailureAlertThreshold()) {
            alerts.add("memory.schema.failures");
        }
        if (profileCompleteness > 0D && profileCompleteness < 0.5D) {
            alerts.add("memory.profile.low-completeness");
        }
        return alerts;
    }
}

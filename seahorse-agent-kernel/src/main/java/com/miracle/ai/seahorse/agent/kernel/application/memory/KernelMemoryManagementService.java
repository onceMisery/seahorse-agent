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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryConflictResolutionCommand;
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
import java.util.UUID;

public class KernelMemoryManagementService implements MemoryManagementInboundPort {

    private static final int DEFAULT_LIMIT = 20;
    private static final ObjectMapper AUDIT_OBJECT_MAPPER = new ObjectMapper();
    private static final String STATUS_RESOLVED = "RESOLVED";
    private static final String ACTION_KEEP_A = "keep_a";
    private static final String ACTION_KEEP_B = "keep_b";
    private static final String ACTION_MERGE = "merge";
    private static final String ACTION_DISCARD = "discard";

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
        return resolveConflict(MemoryConflictResolutionCommand.manual(conflictId, action, resolvedBy));
    }

    @Override
    public boolean resolveConflict(MemoryConflictResolutionCommand command) {
        MemoryConflictResolutionCommand safeCommand = Objects.requireNonNullElseGet(command,
                () -> MemoryConflictResolutionCommand.manual("", "", ""));
        String conflictId = requireText(safeCommand.conflictId(), "conflictId");
        String action = requireText(safeCommand.action(), "action");
        String resolvedBy = Objects.requireNonNullElse(safeCommand.resolvedBy(), "").trim();
        Optional<MemoryConflictRecord> conflict = ports.conflictLogRepositoryPort().findById(conflictId);
        if (conflict.isEmpty()) {
            recordConflictTrace(safeCommand, null, MemoryTraceEvent.STATUS_FAILED,
                    Map.of("reason", "conflict_not_found"));
            return false;
        }

        ConflictResolutionOutcome outcome = applyConflictAction(conflict.get(), safeCommand);
        boolean resolved = ports.conflictLogRepositoryPort()
                .resolve(conflictId, normalizedResolvedAction(action), resolvedBy);
        Map<String, Object> details = conflictDetails(safeCommand, conflict.get(), outcome, resolved);
        recordConflictTrace(safeCommand, conflict.get(),
                resolved ? MemoryTraceEvent.STATUS_SUCCESS : MemoryTraceEvent.STATUS_FAILED,
                details);
        if (resolved) {
            recordConflictAudit(safeCommand, conflict.get(), details);
        }
        return resolved;
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

    private ConflictResolutionOutcome applyConflictAction(MemoryConflictRecord conflict,
                                                          MemoryConflictResolutionCommand command) {
        String action = normalizeAction(command.action());
        ResolvedMemory memoryA = findMemoryInAnyLayer(conflict.memoryId1()).orElse(null);
        ResolvedMemory memoryB = findMemoryInAnyLayer(conflict.memoryId2()).orElse(null);
        List<String> retiredMemoryIds = new ArrayList<>();
        String mergedMemoryId = "";
        String underlyingAction = "log_only";

        switch (action) {
            case ACTION_KEEP_A -> {
                if (deleteResolvedMemory(memoryB)) {
                    retiredMemoryIds.add(memoryB.record().id());
                }
                underlyingAction = "retired_memory_b";
            }
            case ACTION_KEEP_B -> {
                if (deleteResolvedMemory(memoryA)) {
                    retiredMemoryIds.add(memoryA.record().id());
                }
                underlyingAction = "retired_memory_a";
            }
            case ACTION_DISCARD -> {
                if (deleteResolvedMemory(memoryA)) {
                    retiredMemoryIds.add(memoryA.record().id());
                }
                if (deleteResolvedMemory(memoryB)) {
                    retiredMemoryIds.add(memoryB.record().id());
                }
                underlyingAction = "retired_both";
            }
            case ACTION_MERGE -> {
                mergedMemoryId = saveMergedMemory(conflict, command, memoryA, memoryB);
                if (deleteResolvedMemory(memoryA)) {
                    retiredMemoryIds.add(memoryA.record().id());
                }
                if (deleteResolvedMemory(memoryB)) {
                    retiredMemoryIds.add(memoryB.record().id());
                }
                underlyingAction = "merged_and_retired_originals";
            }
            default -> {
                underlyingAction = "unsupported_action_log_only";
            }
        }
        return new ConflictResolutionOutcome(underlyingAction, retiredMemoryIds, mergedMemoryId);
    }

    private String saveMergedMemory(MemoryConflictRecord conflict,
                                    MemoryConflictResolutionCommand command,
                                    ResolvedMemory memoryA,
                                    ResolvedMemory memoryB) {
        ResolvedMemory target = memoryA != null ? memoryA : memoryB;
        if (target == null) {
            return "";
        }
        String mergedContent = firstText(
                command.mergedContent(),
                command.updatedContent(),
                mergedContent(memoryA == null ? null : memoryA.record(), memoryB == null ? null : memoryB.record()));
        String mergedMemoryId = "merged-" + conflict.id();
        Map<String, Object> metadata = new LinkedHashMap<>(target.record().metadata());
        metadata.put("conflictId", conflict.id());
        metadata.put("conflictResolutionAction", ACTION_MERGE);
        metadata.put("sourceMemoryIds", List.of(conflict.memoryId1(), conflict.memoryId2()));
        metadata.put("tenantId", firstText(command.tenantId(), string(metadata.get("tenantId")), "default"));
        if (hasText(string(metadata.get("semanticKey")))) {
            metadata.put("semanticKey", string(metadata.get("semanticKey")) + ":merged:" + conflict.id());
        }
        target.store().save(new MemoryRecord(
                mergedMemoryId,
                target.record().layer(),
                target.record().type(),
                mergedContent,
                metadata,
                Instant.now()));
        return mergedMemoryId;
    }

    private String mergedContent(MemoryRecord memoryA, MemoryRecord memoryB) {
        String contentA = memoryA == null ? "" : memoryA.content();
        String contentB = memoryB == null ? "" : memoryB.content();
        if (!hasText(contentA)) {
            return contentB;
        }
        if (!hasText(contentB) || contentA.equals(contentB)) {
            return contentA;
        }
        return contentA + "\n" + contentB;
    }

    private Optional<ResolvedMemory> findMemoryInAnyLayer(String memoryId) {
        if (!hasText(memoryId)) {
            return Optional.empty();
        }
        for (MemoryStorePort memoryStorePort : List.of(
                ports.workingMemoryPort(),
                ports.shortTermMemoryPort(),
                ports.longTermMemoryPort(),
                ports.semanticMemoryPort())) {
            try {
                Optional<MemoryRecord> record = memoryStorePort.findById(memoryId);
                if (record.isPresent()) {
                    return Optional.of(new ResolvedMemory(memoryStorePort, record.get()));
                }
            } catch (RuntimeException ignored) {
                // Keep conflict resolution fail-open across independent memory layers.
            }
        }
        return Optional.empty();
    }

    private boolean deleteResolvedMemory(ResolvedMemory resolvedMemory) {
        if (resolvedMemory == null) {
            return false;
        }
        return resolvedMemory.store().deleteById(resolvedMemory.record().id());
    }

    private Map<String, Object> conflictDetails(MemoryConflictResolutionCommand command,
                                                MemoryConflictRecord conflict,
                                                ConflictResolutionOutcome outcome,
                                                boolean resolved) {
        Map<String, Object> details = new LinkedHashMap<>();
        details.put("source", command.source());
        details.put("operator", command.resolvedBy());
        details.put("action", normalizedResolvedAction(command.action()));
        details.put("conflictId", conflict.id());
        details.put("memoryId1", conflict.memoryId1());
        details.put("memoryId2", conflict.memoryId2());
        details.put("retiredMemoryIds", outcome.retiredMemoryIds());
        details.put("mergedMemoryId", outcome.mergedMemoryId());
        details.put("underlyingAction", outcome.underlyingAction());
        details.put("resolved", resolved);
        return details;
    }

    private void recordConflictTrace(MemoryConflictResolutionCommand command,
                                     MemoryConflictRecord conflict,
                                     String status,
                                     Map<String, Object> details) {
        try {
            ports.traceRecorder().record(new MemoryTraceEvent(
                    "",
                    command.tenantId(),
                    conflict == null ? "" : conflict.userId(),
                    "",
                    "",
                    "memory-conflict",
                    interactiveResolve(command) ? "interactive-resolve" : "resolve",
                    status,
                    conflict == null ? command.conflictId() : conflict.id(),
                    "memory_conflict",
                    details,
                    Instant.now()));
        } catch (RuntimeException ignored) {
            // Trace must not block the user's conflict-resolution action.
        }
    }

    private void recordConflictAudit(MemoryConflictResolutionCommand command,
                                     MemoryConflictRecord conflict,
                                     Map<String, Object> details) {
        if (ports.auditLedgerService() == null) {
            return;
        }
        try {
            ports.auditLedgerService().append(new AuditEvent(
                    UUID.randomUUID().toString(),
                    command.tenantId(),
                    AuditEventType.MEMORY_CONFLICT_RESOLVED,
                    actorType(command.resolvedBy()),
                    firstText(command.resolvedBy(), "system"),
                    null,
                    null,
                    "memory_conflict",
                    conflict.id(),
                    auditPayload(details),
                    Instant.now()));
        } catch (RuntimeException ignored) {
            // Audit is best-effort for chat-side conflict resolution.
        }
    }

    private AuditActorType actorType(String resolvedBy) {
        String actor = Objects.requireNonNullElse(resolvedBy, "").trim();
        return actor.isBlank() || "system".equalsIgnoreCase(actor) ? AuditActorType.SYSTEM : AuditActorType.USER;
    }

    private String auditPayload(Map<String, Object> details) {
        try {
            return AUDIT_OBJECT_MAPPER.writeValueAsString(details);
        } catch (JsonProcessingException ex) {
            return AuditEvent.EMPTY_PAYLOAD;
        }
    }

    private boolean interactiveResolve(MemoryConflictResolutionCommand command) {
        return "chat-ui".equalsIgnoreCase(command.source())
                || command.resolvedBy().toLowerCase(Locale.ROOT).startsWith("interactive:");
    }

    private String normalizedResolvedAction(String action) {
        String normalized = normalizeAction(action);
        return normalized.isBlank() ? action.trim() : normalized;
    }

    private String normalizeAction(String action) {
        String normalized = Objects.requireNonNullElse(action, "").trim().toLowerCase(Locale.ROOT);
        return switch (normalized) {
            case ACTION_KEEP_A, ACTION_KEEP_B, ACTION_MERGE, ACTION_DISCARD -> normalized;
            default -> "";
        };
    }

    private String firstText(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String string(Object value) {
        return value == null ? "" : value.toString();
    }

    private record ResolvedMemory(MemoryStorePort store, MemoryRecord record) {
    }

    private record ConflictResolutionOutcome(String underlyingAction,
                                             List<String> retiredMemoryIds,
                                             String mergedMemoryId) {

        private ConflictResolutionOutcome {
            retiredMemoryIds = List.copyOf(Objects.requireNonNullElse(retiredMemoryIds, List.of()));
            mergedMemoryId = Objects.requireNonNullElse(mergedMemoryId, "");
        }
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

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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewQuery;
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
        List<MemoryTraceEvent> traceEvents = ports.traceRecorder().listRecent(sampleLimit);

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

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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.InferredMemory;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.HashSet;

public class KernelMemoryGovernanceService implements MemoryGovernanceInboundPort {

    private static final Logger LOG = LoggerFactory.getLogger(KernelMemoryGovernanceService.class);

    private static final int PROMOTION_SCAN_LIMIT = 500;
    private static final int CONFLICT_SCAN_LIMIT = 500;
    private static final double DEFAULT_PROMOTION_THRESHOLD = 0.6D;
    private static final double INFERENCE_CONFIDENCE_THRESHOLD = 0.7D;
    private static final String GOVERNANCE_POLICY_VERSION = "memory-governance-v1";
    private static final String STATUS_PENDING = "PENDING";

    private final MemoryGovernanceServicePorts ports;
    private final double promotionThreshold;
    private final boolean inferenceEnabled;
    private final MemoryDecayOptions decayOptions;

    public KernelMemoryGovernanceService(MemoryGovernanceServicePorts ports, double promotionThreshold) {
        this(ports, promotionThreshold, false);
    }

    public KernelMemoryGovernanceService(MemoryGovernanceServicePorts ports,
                                         double promotionThreshold,
                                         boolean inferenceEnabled) {
        this(ports, promotionThreshold, inferenceEnabled, MemoryDecayOptions.defaults());
    }

    public KernelMemoryGovernanceService(MemoryGovernanceServicePorts ports,
                                         double promotionThreshold,
                                         boolean inferenceEnabled,
                                         MemoryDecayOptions decayOptions) {
        this.ports = Objects.requireNonNull(ports, "ports must not be null");
        this.promotionThreshold = promotionThreshold <= 0D ? DEFAULT_PROMOTION_THRESHOLD : promotionThreshold;
        this.inferenceEnabled = inferenceEnabled;
        this.decayOptions = Objects.requireNonNullElseGet(decayOptions, MemoryDecayOptions::defaults);
    }

    @Override
    public MemoryGovernanceRunResult runGovernance(String userId, String reason, boolean assessQuality) {
        String safeUserId = requireText(userId, "userId");
        List<String> errors = new ArrayList<>();
        int promoted = 0;
        int semanticUpserted = 0;

        List<MemoryRecord> shortTermRecords = ports.shortTermMemoryPort().listByUser(safeUserId, PROMOTION_SCAN_LIMIT);

        // 晋升：短期 → 长期/语义
        for (MemoryRecord record : shortTermRecords) {
            if (!shouldPromote(record)) {
                continue;
            }
            try {
                ports.longTermMemoryPort().save(toLongTerm(record));
                promoted++;
                if (isSemanticCandidate(record)) {
                    ports.semanticMemoryPort().save(toSemantic(record));
                    semanticUpserted++;
                }
            } catch (RuntimeException ex) {
                errors.add(record.id() + ":" + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            }
        }

        // 推理：从短期记忆中推理新的长期/语义记忆
        int inferred = 0;
        if (inferenceEnabled) {
            inferred = runInference(safeUserId, shortTermRecords, errors);
        }

        recordConflicts(safeUserId, shortTermRecords, errors);

        if (assessQuality) {
            try {
                MemoryQualityReport report = ports.memoryEnginePort().assessMemoryQuality(safeUserId);
                ports.qualitySnapshotRepositoryPort().save(new MemoryQualitySnapshot(
                        "", safeUserId, qualitySnapshot(report), Instant.now()));
            } catch (RuntimeException ex) {
                errors.add("quality:" + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            }
        }
        return new MemoryGovernanceRunResult(safeUserId, Objects.requireNonNullElse(reason, "manual"),
                promoted, semanticUpserted, inferred, false, assessQuality, errors, Instant.now());
    }

    private int runInference(String userId, List<MemoryRecord> shortTermRecords, List<String> errors) {
        int inferred = 0;
        try {
            List<MemoryRecord> semanticRecords = ports.semanticMemoryPort().listByUser(userId, 100);
            List<InferredMemory> candidates = ports.memoryInferencePort().infer(
                    userId, shortTermRecords, semanticRecords);
            for (InferredMemory candidate : candidates) {
                if (candidate.confidence() < INFERENCE_CONFIDENCE_THRESHOLD) {
                    continue;
                }
                try {
                    MemoryRecord record = toInferredRecord(userId, candidate);
                    if ("semantic".equals(candidate.targetLayer())) {
                        ports.semanticMemoryPort().save(record);
                    } else {
                        ports.longTermMemoryPort().save(record);
                    }
                    inferred++;
                } catch (RuntimeException ex) {
                    errors.add("inference:" + candidate.content() + ":" +
                            Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
                }
            }
            if (inferred > 0) {
                LOG.info("跨会话推理完成: userId={}, inferred={}", userId, inferred);
            }
        } catch (RuntimeException ex) {
            errors.add("inference:" + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
        return inferred;
    }

    private void recordConflicts(String userId, List<MemoryRecord> records, List<String> errors) {
        try {
            Set<String> existingPairs = new HashSet<>();
            for (MemoryConflictRecord conflict : ports.conflictLogRepositoryPort()
                    .listByUser(userId, STATUS_PENDING, CONFLICT_SCAN_LIMIT)) {
                existingPairs.add(conflictPairKey(conflict.memoryId1(), conflict.memoryId2()));
            }
            Map<String, MemoryRecord> seenBySemanticKey = new LinkedHashMap<>();
            for (MemoryRecord record : records) {
                String key = semanticConflictKey(record);
                if (!hasText(key)) {
                    continue;
                }
                MemoryRecord existing = seenBySemanticKey.putIfAbsent(key, record);
                if (existing == null || sameContent(existing, record)) {
                    continue;
                }
                String pairKey = conflictPairKey(existing.id(), record.id());
                if (!existingPairs.add(pairKey)) {
                    continue;
                }
                ports.conflictLogRepositoryPort().save(new MemoryConflictRecord(
                        "",
                        userId,
                        existing.id(),
                        record.id(),
                        "SEMANTIC_KEY_CONFLICT",
                        severity(existing, record),
                        STATUS_PENDING,
                        "",
                        "",
                        Instant.EPOCH,
                        Instant.now()));
            }
        } catch (RuntimeException ex) {
            errors.add("conflict:" + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private Map<String, Object> qualitySnapshot(MemoryQualityReport report) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("governancePolicyVersion", GOVERNANCE_POLICY_VERSION);
        snapshot.put("userId", Objects.requireNonNullElse(report.getUserId(), ""));
        snapshot.put("shortTermCount", report.getShortTermCount());
        snapshot.put("longTermCount", report.getLongTermCount());
        snapshot.put("semanticCount", report.getSemanticCount());
        snapshot.put("conflictCount", report.getConflictCount());
        snapshot.put("contradictionConflictCount", report.getContradictionConflictCount());
        snapshot.put("preferencePolarityConflictCount", report.getPreferencePolarityConflictCount());
        snapshot.put("singularProfileConflictCount", report.getSingularProfileConflictCount());
        snapshot.put("multiValueProfileOverloadCount", report.getMultiValueProfileOverloadCount());
        snapshot.put("autoDowngradedConflictCount", report.getAutoDowngradedConflictCount());
        List<Map<String, Object>> cleanupSuggestions = cleanupSuggestions(report);
        snapshot.put("cleanupSuggestionCount", cleanupSuggestions.size());
        snapshot.put("cleanupSuggestions", cleanupSuggestions);
        snapshot.put("createdBy", "KernelMemoryGovernanceService");
        return snapshot;
    }

    private List<Map<String, Object>> cleanupSuggestions(MemoryQualityReport report) {
        List<Map<String, Object>> suggestions = new ArrayList<>();
        if (report.getConflictCount() > 0) {
            suggestions.add(cleanupSuggestion(
                    "MERGE_OR_CORRECT_CONFLICTS",
                    "memory_conflict_log",
                    report.getConflictCount(),
                    "Pending memory conflicts require manual merge, correction, or forget decision."));
        }
        if (report.getMultiValueProfileOverloadCount() > 0) {
            suggestions.add(cleanupSuggestion(
                    "REVIEW_PROFILE_OVERLOAD",
                    "user_profile_fact",
                    report.getMultiValueProfileOverloadCount(),
                    "Profile slots have too many active values and need manual pruning."));
        }
        if (report.getAutoDowngradedConflictCount() > 0) {
            suggestions.add(cleanupSuggestion(
                    "REVIEW_DOWNGRADED_FACTS",
                    "memory_correction_ledger",
                    report.getAutoDowngradedConflictCount(),
                    "Auto-downgraded conflicts need human confirmation before cleanup."));
        }
        return List.copyOf(suggestions);
    }

    private Map<String, Object> cleanupSuggestion(String action,
                                                  String target,
                                                  int candidateCount,
                                                  String reason) {
        Map<String, Object> suggestion = new LinkedHashMap<>();
        suggestion.put("action", action);
        suggestion.put("target", target);
        suggestion.put("candidateCount", candidateCount);
        suggestion.put("reason", reason);
        suggestion.put("requiresManualConfirmation", true);
        return suggestion;
    }

    private MemoryRecord toInferredRecord(String userId, InferredMemory candidate) {
        Map<String, Object> metadata = Map.of(
                "userId", userId,
                "importanceScore", candidate.confidence(),
                "confidenceLevel", candidate.confidence(),
                "sourceType", "inferred",
                "sourceIds", candidate.sourceIds().toString(),
                "reasoning", candidate.reasoning(),
                "semanticKey", candidate.semanticKey());
        return new MemoryRecord("", candidate.targetLayer(), candidate.type(), candidate.content(),
                metadata, Instant.now());
    }

    @Override
    public MemoryGovernanceRunResult runDecay(String reason) {
        List<String> errors = new ArrayList<>();
        try {
            List<MemoryRecord> candidates = ports.shortTermMemoryMaintenancePort().scanExpiredOrDecayed(
                    Instant.now(), decayOptions.decayThreshold(), decayOptions.scanLimit());
            if (!candidates.isEmpty() && !decayOptions.dryRun()) {
                // 维护端口负责批量软删，避免治理服务了解具体数据库更新细节。
                ports.shortTermMemoryMaintenancePort().markDeleted(candidates.stream()
                        .map(MemoryRecord::id)
                        .filter(this::hasText)
                        .toList());
            }
        } catch (RuntimeException ex) {
            errors.add(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
        return new MemoryGovernanceRunResult("", Objects.requireNonNullElse(reason, "manual-decay"),
                0, 0, 0, true, false, errors, Instant.now());
    }

    @Override
    public MemoryQualityReport assessQuality(String userId) {
        return ports.memoryEnginePort().assessMemoryQuality(requireText(userId, "userId"));
    }

    private MemoryRecord toLongTerm(MemoryRecord record) {
        return new MemoryRecord("", "long_term", record.type(), record.content(),
                merge(record.metadata(), Map.of(
                        "sourceMemoryId", record.id(),
                        "sourceLayer", record.layer()
                )), Instant.now());
    }

    private MemoryRecord toSemantic(MemoryRecord record) {
        return new MemoryRecord("", "semantic", record.type(), record.content(),
                merge(record.metadata(), Map.of(
                        "semanticKey", semanticKey(record),
                        "sourceMemoryId", record.id()
                )), Instant.now());
    }

    private boolean shouldPromote(MemoryRecord record) {
        if (record == null || !hasText(record.content()) || !hasText(record.type())) {
            return false;
        }
        double score = number(record.metadata().get("importanceScore"), 0D);
        double confidence = number(record.metadata().get("confidenceLevel"), 0D);
        double weightedScore = Math.min(1D, score + confidence * 0.08D + typeWeight(record.type()));
        return weightedScore >= promotionThreshold;
    }

    private double typeWeight(String type) {
        return switch (Objects.requireNonNullElse(type, "").toUpperCase()) {
            case "PROFILE" -> 0.08D;
            case "PREFERENCE" -> 0.06D;
            case "SUMMARY" -> 0.03D;
            case "FACT" -> 0.02D;
            case "TODO" -> -0.03D;
            default -> 0D;
        };
    }

    private boolean isSemanticCandidate(MemoryRecord record) {
        return "PROFILE".equalsIgnoreCase(record.type()) || "PREFERENCE".equalsIgnoreCase(record.type());
    }

    private String semanticKey(MemoryRecord record) {
        Object explicit = record.metadata().get("semanticKey");
        if (explicit != null && hasText(explicit.toString())) {
            return explicit.toString().trim();
        }
        String profileKey = semanticProfileKey(record);
        if (hasText(profileKey)) {
            return profileKey;
        }
        return record.type().toLowerCase() + ":" + record.content().trim().toLowerCase().replaceAll("\\s+", "_");
    }

    private String semanticProfileKey(MemoryRecord record) {
        if (!"PROFILE".equalsIgnoreCase(record.type()) && !"FACT".equalsIgnoreCase(record.type())) {
            return "";
        }
        String normalized = normalizeContent(record.content());
        if (containsAny(normalized, "occupation", "profession", "job", "student", "teacher")
                || containsAny(record.content(), "职业", "身份", "工作", "学生", "老师", "教师")) {
            return "profile:occupation";
        }
        if (containsAny(normalized, "name") || containsAny(record.content(), "名字", "昵称")) {
            return "profile:name";
        }
        if (containsAny(normalized, "school", "university", "major")
                || containsAny(record.content(), "学校", "大学", "专业")) {
            return "profile:education";
        }
        if (containsAny(normalized, "company", "organization")
                || containsAny(record.content(), "公司", "组织")) {
            return "profile:organization";
        }
        return "";
    }

    private String semanticConflictKey(MemoryRecord record) {
        if (record == null || !hasText(record.id()) || !hasText(record.type()) || !hasText(record.content())) {
            return "";
        }
        Object explicit = record.metadata().get("semanticKey");
        if (explicit == null || !hasText(explicit.toString())) {
            return "";
        }
        return record.type().trim().toUpperCase(Locale.ROOT)
                + ":" + explicit.toString().trim().toLowerCase(Locale.ROOT);
    }

    private boolean sameContent(MemoryRecord left, MemoryRecord right) {
        return normalizeContent(left.content()).equals(normalizeContent(right.content()));
    }

    private String normalizeContent(String content) {
        return Objects.requireNonNullElse(content, "")
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("\\s+", " ");
    }

    private boolean containsAny(String content, String... needles) {
        if (!hasText(content) || needles == null) {
            return false;
        }
        for (String needle : needles) {
            if (hasText(needle) && content.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String conflictPairKey(String leftId, String rightId) {
        String left = Objects.requireNonNullElse(leftId, "");
        String right = Objects.requireNonNullElse(rightId, "");
        return left.compareTo(right) <= 0 ? left + "|" + right : right + "|" + left;
    }

    private String severity(MemoryRecord left, MemoryRecord right) {
        if ("PROFILE".equalsIgnoreCase(left.type()) || "PROFILE".equalsIgnoreCase(right.type())) {
            return "HIGH";
        }
        if ("PREFERENCE".equalsIgnoreCase(left.type()) || "PREFERENCE".equalsIgnoreCase(right.type())) {
            return "MEDIUM";
        }
        return "LOW";
    }

    private Map<String, Object> merge(Map<String, Object> source, Map<String, Object> overlay) {
        java.util.LinkedHashMap<String, Object> merged = new java.util.LinkedHashMap<>(
                Objects.requireNonNullElse(source, Map.of()));
        merged.putAll(overlay);
        return merged;
    }

    private double number(Object value, double fallback) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value != null) {
            try {
                return Double.parseDouble(value.toString());
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
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
}

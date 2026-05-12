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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class KernelMemoryGovernanceService implements MemoryGovernanceInboundPort {

    private static final int PROMOTION_SCAN_LIMIT = 500;
    private static final double DEFAULT_PROMOTION_THRESHOLD = 0.6D;

    private final MemoryGovernanceServicePorts ports;
    private final double promotionThreshold;

    public KernelMemoryGovernanceService(MemoryGovernanceServicePorts ports, double promotionThreshold) {
        this.ports = Objects.requireNonNull(ports, "ports must not be null");
        this.promotionThreshold = promotionThreshold <= 0D ? DEFAULT_PROMOTION_THRESHOLD : promotionThreshold;
    }

    @Override
    public MemoryGovernanceRunResult runGovernance(String userId, String reason, boolean assessQuality) {
        String safeUserId = requireText(userId, "userId");
        List<String> errors = new ArrayList<>();
        int promoted = 0;
        int semanticUpserted = 0;
        for (MemoryRecord record : ports.shortTermMemoryPort().listByUser(safeUserId, PROMOTION_SCAN_LIMIT)) {
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
        if (assessQuality) {
            try {
                ports.memoryEnginePort().assessMemoryQuality(safeUserId);
            } catch (RuntimeException ex) {
                errors.add("quality:" + Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
            }
        }
        return new MemoryGovernanceRunResult(safeUserId, Objects.requireNonNullElse(reason, "manual"),
                promoted, semanticUpserted, false, assessQuality, errors, Instant.now());
    }

    @Override
    public MemoryGovernanceRunResult runDecay(String reason) {
        List<String> errors = new ArrayList<>();
        try {
            ports.memoryEnginePort().executeMemoryDecay();
        } catch (RuntimeException ex) {
            errors.add(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
        return new MemoryGovernanceRunResult("", Objects.requireNonNullElse(reason, "manual-decay"),
                0, 0, true, false, errors, Instant.now());
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
        return record.type().toLowerCase() + ":" + record.content().trim().toLowerCase().replaceAll("\\s+", "_");
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

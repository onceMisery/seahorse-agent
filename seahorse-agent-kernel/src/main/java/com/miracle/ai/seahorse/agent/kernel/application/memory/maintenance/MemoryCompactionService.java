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

package com.miracle.ai.seahorse.agent.kernel.application.memory.maintenance;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionFragment;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummarizerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryCompactionSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public class MemoryCompactionService {

    private static final String MASTER_TYPE = "COMPACTED_SUMMARY";
    private static final String SOURCE_TYPE = "memory_compaction";
    private static final String METADATA_IMPORTANCE_SCORE = "importanceScore";
    private static final String METADATA_CONFIDENCE_LEVEL = "confidenceLevel";
    private static final double DEFAULT_MASTER_IMPORTANCE_SCORE = 0.8D;
    private static final double DEFAULT_MASTER_CONFIDENCE_LEVEL = 0.8D;
    static final String OBSERVATION_RUN_EVENT = "memory-compaction-run";
    static final String OBSERVATION_GROUP_EVENT = "memory-compaction-group";
    static final String OBSERVATION_ATTR_OUTCOME = "outcome";
    static final String OBSERVATION_ATTR_STRATEGY = "strategy";
    static final String OBSERVATION_OUTCOME_SUCCESS = "success";
    static final String OBSERVATION_OUTCOME_EMPTY = "empty";
    static final String OBSERVATION_OUTCOME_ERROR = "error";
    static final String OBSERVATION_OUTCOME_SKIPPED = "skipped";

    private final MemoryCompactionPort compactionPort;
    private final LongTermMemoryPort longTermMemoryPort;
    private final MemoryOutboxPort outboxPort;
    private final MemoryCompactionSummarizerPort summarizerPort;
    private final MemoryCompactionOptions options;
    private final ObservationPort observationPort;

    public MemoryCompactionService() {
        this(MemoryCompactionPort.noop(), null, MemoryOutboxPort.noop(),
                MemoryCompactionSummarizerPort.noop(), MemoryCompactionOptions.defaults(),
                ObservationPort.noop());
    }

    public MemoryCompactionService(MemoryCompactionPort compactionPort,
                                   LongTermMemoryPort longTermMemoryPort,
                                   MemoryOutboxPort outboxPort,
                                   MemoryCompactionOptions options) {
        this(compactionPort, longTermMemoryPort, outboxPort, MemoryCompactionSummarizerPort.noop(), options,
                ObservationPort.noop());
    }

    public MemoryCompactionService(MemoryCompactionPort compactionPort,
                                   LongTermMemoryPort longTermMemoryPort,
                                   MemoryOutboxPort outboxPort,
                                   MemoryCompactionSummarizerPort summarizerPort,
                                   MemoryCompactionOptions options) {
        this(compactionPort, longTermMemoryPort, outboxPort, summarizerPort, options, ObservationPort.noop());
    }

    public MemoryCompactionService(MemoryCompactionPort compactionPort,
                                   LongTermMemoryPort longTermMemoryPort,
                                   MemoryOutboxPort outboxPort,
                                   MemoryCompactionSummarizerPort summarizerPort,
                                   MemoryCompactionOptions options,
                                   ObservationPort observationPort) {
        this.compactionPort = Objects.requireNonNullElseGet(compactionPort, MemoryCompactionPort::noop);
        this.longTermMemoryPort = longTermMemoryPort;
        this.outboxPort = Objects.requireNonNullElseGet(outboxPort, MemoryOutboxPort::noop);
        this.summarizerPort = Objects.requireNonNullElseGet(summarizerPort, MemoryCompactionSummarizerPort::noop);
        this.options = Objects.requireNonNullElseGet(options, MemoryCompactionOptions::defaults);
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
    }

    public MemoryCompactionResult run(String reason) {
        Instant now = Instant.now();
        List<String> errors = new ArrayList<>();
        List<MemoryCompactionCandidate> candidates = scan(errors);
        int compactedGroups = 0;
        int compactedFragments = 0;
        for (MemoryCompactionCandidate candidate : candidates) {
            if (candidate.fragments().size() < options.minGroupSize()) {
                emitGroupMetric(candidate, OBSERVATION_OUTCOME_SKIPPED);
                continue;
            }
            try {
                MemoryRecord master = masterMemory(candidate, now);
                saveMaster(master);
                compactedFragments += compactionPort.markCompacted(candidate, master.id(), now);
                enqueueMasterUpserts(master, candidate, errors);
                enqueueFragmentDeletes(candidate, errors);
                compactedGroups++;
                emitGroupMetric(candidate, OBSERVATION_OUTCOME_SUCCESS);
            } catch (RuntimeException ex) {
                errors.add(candidate.groupKey() + ":" + errorMessage(ex));
                emitGroupMetric(candidate, OBSERVATION_OUTCOME_ERROR);
            }
        }
        emitRunMetric(candidates, compactedGroups, errors);
        return new MemoryCompactionResult(
                Objects.requireNonNullElse(reason, "manual-compaction"),
                candidates.size(),
                compactedGroups,
                compactedFragments,
                errors,
                now);
    }

    private List<MemoryCompactionCandidate> scan(List<String> errors) {
        try {
            return compactionPort.scanCandidates(options.scanLimit(), options.minGroupSize());
        } catch (RuntimeException ex) {
            errors.add("scan:" + errorMessage(ex));
            return List.of();
        }
    }

    private MemoryRecord masterMemory(MemoryCompactionCandidate candidate, Instant now) {
        String masterId = "mem-compact-" + UUID.randomUUID();
        MemoryCompactionSummary summary = summarize(candidate);
        List<String> sourceMemoryIds = candidate.fragments().stream()
                .map(MemoryCompactionFragment::memoryId)
                .filter(id -> !id.isBlank())
                .distinct()
                .toList();
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("userId", candidate.userId());
        metadata.put("tenantId", candidate.tenantId());
        metadata.put("sourceType", SOURCE_TYPE);
        metadata.put("sourceIds", sourceMemoryIds);
        metadata.put("sourceMemoryIds", sourceMemoryIds);
        metadata.put("compactionGroupKey", candidate.groupKey());
        metadata.put("compactionStrategy", candidate.strategy());
        metadata.put("compactionSummaryStrategy", summaryStrategy(summary, candidate));
        if (!summary.metadata().isEmpty()) {
            metadata.put("compactionSummaryMetadata", summary.metadata());
        }
        metadata.put("compactionGenerationId", "compaction-" + UUID.randomUUID());
        metadata.put("compactedAt", now.toString());
        metadata.put(METADATA_IMPORTANCE_SCORE,
                summaryScore(summary, METADATA_IMPORTANCE_SCORE, DEFAULT_MASTER_IMPORTANCE_SCORE));
        metadata.put(METADATA_CONFIDENCE_LEVEL,
                summaryScore(summary, METADATA_CONFIDENCE_LEVEL, DEFAULT_MASTER_CONFIDENCE_LEVEL));
        return new MemoryRecord(masterId, "long_term", MASTER_TYPE, content(candidate, summary), metadata, now);
    }

    private MemoryCompactionSummary summarize(MemoryCompactionCandidate candidate) {
        try {
            MemoryCompactionSummary summary = summarizerPort.summarize(candidate);
            return summary == null ? MemoryCompactionSummary.empty() : summary;
        } catch (RuntimeException ignored) {
            return MemoryCompactionSummary.empty();
        }
    }

    private String content(MemoryCompactionCandidate candidate, MemoryCompactionSummary summary) {
        if (summary != null && !summary.content().isBlank()) {
            return summary.content();
        }
        return candidate.fragments().stream()
                .map(MemoryCompactionFragment::content)
                .filter(content -> content != null && !content.isBlank())
                .distinct()
                .reduce((left, right) -> left + "\n" + right)
                .orElse(candidate.groupKey());
    }

    private String summaryStrategy(MemoryCompactionSummary summary, MemoryCompactionCandidate candidate) {
        if (summary != null && !summary.strategy().isBlank()) {
            return summary.strategy();
        }
        return "rule:" + candidate.strategy();
    }

    private double summaryScore(MemoryCompactionSummary summary, String key, double fallback) {
        if (summary == null || summary.metadata().isEmpty()) {
            return fallback;
        }
        Object value = summary.metadata().get(key);
        if (value instanceof Number number) {
            return boundedScore(number.doubleValue(), fallback);
        }
        if (value instanceof String text && !text.isBlank()) {
            try {
                return boundedScore(Double.parseDouble(text), fallback);
            } catch (NumberFormatException ignored) {
                return fallback;
            }
        }
        return fallback;
    }

    private double boundedScore(double value, double fallback) {
        if (Double.isNaN(value) || Double.isInfinite(value) || value < 0D || value > 1D) {
            return fallback;
        }
        return value;
    }

    private void saveMaster(MemoryRecord master) {
        if (longTermMemoryPort == null) {
            throw new IllegalStateException("longTermMemoryPort unavailable");
        }
        longTermMemoryPort.save(master);
    }

    private void enqueueMasterUpserts(MemoryRecord master, MemoryCompactionCandidate candidate, List<String> errors) {
        if (options.vectorIndexEnabled()) {
            enqueue(MemoryOutboxPort.MemoryOutboxTask.vectorUpsert(
                    master,
                    candidate.userId(),
                    candidate.tenantId(),
                    options.embeddingModel(),
                    ""), errors);
        }
        if (options.keywordIndexEnabled()) {
            enqueue(MemoryOutboxPort.MemoryOutboxTask.keywordUpsert(master, candidate.userId(), candidate.tenantId()),
                    errors);
        }
        if (options.graphIndexEnabled()) {
            enqueue(MemoryOutboxPort.MemoryOutboxTask.graphUpsert(master, candidate.userId(), candidate.tenantId()),
                    errors);
        }
    }

    private void enqueueFragmentDeletes(MemoryCompactionCandidate candidate, List<String> errors) {
        for (MemoryCompactionFragment fragment : candidate.fragments()) {
            if (options.vectorIndexEnabled()) {
                enqueue(MemoryOutboxPort.MemoryOutboxTask.vectorDelete(
                        fragment.memoryId(), candidate.userId(), candidate.tenantId()), errors);
            }
            if (options.keywordIndexEnabled()) {
                enqueue(MemoryOutboxPort.MemoryOutboxTask.keywordDelete(
                        fragment.memoryId(), candidate.userId(), candidate.tenantId()), errors);
            }
            if (options.graphIndexEnabled()) {
                enqueue(MemoryOutboxPort.MemoryOutboxTask.graphDelete(
                        fragment.memoryId(), candidate.userId(), candidate.tenantId()), errors);
            }
        }
    }

    private void enqueue(MemoryOutboxPort.MemoryOutboxTask task, List<String> errors) {
        try {
            outboxPort.enqueue(task);
        } catch (RuntimeException ex) {
            errors.add(task.targetId() + ":" + task.taskType() + ":" + errorMessage(ex));
        }
    }

    private String errorMessage(RuntimeException ex) {
        return ex.getMessage() == null ? ex.getClass().getName() : ex.getMessage();
    }

    private void emitRunMetric(List<MemoryCompactionCandidate> candidates,
                               int compactedGroups,
                               List<String> errors) {
        String outcome;
        if (!errors.isEmpty() && compactedGroups == 0) {
            outcome = OBSERVATION_OUTCOME_ERROR;
        } else if (candidates.isEmpty() || compactedGroups == 0) {
            outcome = OBSERVATION_OUTCOME_EMPTY;
        } else {
            outcome = OBSERVATION_OUTCOME_SUCCESS;
        }
        try {
            observationPort.recordEvent(new ObservationEvent(
                    OBSERVATION_RUN_EVENT,
                    Instant.now(),
                    ObservationEvent.DEFAULT_AMOUNT,
                    Map.of(OBSERVATION_ATTR_OUTCOME, outcome)));
        } catch (RuntimeException ignored) {
            // Observation emission is best-effort and must not change compaction semantics.
        }
    }

    private void emitGroupMetric(MemoryCompactionCandidate candidate, String outcome) {
        try {
            Map<String, String> attributes = new LinkedHashMap<>();
            attributes.put(OBSERVATION_ATTR_OUTCOME, outcome);
            attributes.put(OBSERVATION_ATTR_STRATEGY, normalizeStrategy(candidate));
            observationPort.recordEvent(new ObservationEvent(
                    OBSERVATION_GROUP_EVENT,
                    Instant.now(),
                    ObservationEvent.DEFAULT_AMOUNT,
                    Map.copyOf(attributes)));
        } catch (RuntimeException ignored) {
            // Observation emission is best-effort and must not change compaction semantics.
        }
    }

    private String normalizeStrategy(MemoryCompactionCandidate candidate) {
        if (candidate == null) {
            return "unknown";
        }
        String strategy = candidate.strategy();
        if (strategy == null || strategy.isBlank()) {
            return "unknown";
        }
        return strategy;
    }
}

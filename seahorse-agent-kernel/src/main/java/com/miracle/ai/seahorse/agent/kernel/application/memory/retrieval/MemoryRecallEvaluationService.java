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

package com.miracle.ai.seahorse.agent.kernel.application.memory.retrieval;

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallEvaluationResult;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryRecallGoldenCase;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryContextAttribution;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public class MemoryRecallEvaluationService implements MemoryRecallEvaluationInboundPort {

    private static final int DEFAULT_TOP_K = 10;
    static final String OBSERVATION_EVALUATE_EVENT = "memory-recall-evaluate";
    static final String OBSERVATION_ATTR_OUTCOME = "outcome";
    static final String OBSERVATION_OUTCOME_SUCCESS = "success";
    static final String OBSERVATION_OUTCOME_EMPTY = "empty";

    private final MemoryRetrievalPipelinePort retrievalPipelinePort;
    private final ObservationPort observationPort;

    public MemoryRecallEvaluationService(MemoryRetrievalPipelinePort retrievalPipelinePort) {
        this(retrievalPipelinePort, ObservationPort.noop());
    }

    public MemoryRecallEvaluationService(MemoryRetrievalPipelinePort retrievalPipelinePort,
                                         ObservationPort observationPort) {
        this.retrievalPipelinePort = Objects.requireNonNull(retrievalPipelinePort,
                "retrievalPipelinePort must not be null");
        this.observationPort = Objects.requireNonNullElseGet(observationPort, ObservationPort::noop);
    }

    @Override
    public MemoryRecallEvaluationReport evaluate(MemoryRecallEvaluationCommand command) {
        MemoryRecallEvaluationCommand safeCommand = command == null
                ? new MemoryRecallEvaluationCommand(DEFAULT_TOP_K, List.of())
                : command;
        return evaluate(safeCommand.cases(), safeCommand.topK());
    }

    public MemoryRecallEvaluationReport evaluate(List<MemoryRecallGoldenCase> cases, int topK) {
        List<MemoryRecallGoldenCase> goldenCases = List.copyOf(Objects.requireNonNullElse(cases, List.of()));
        int effectiveTopK = topK > 0 ? topK : DEFAULT_TOP_K;
        List<MemoryRecallEvaluationResult> results = goldenCases.stream()
                .filter(Objects::nonNull)
                .map(goldenCase -> evaluateCase(goldenCase, effectiveTopK))
                .toList();
        int scoredCaseCount = (int) results.stream().filter(MemoryRecallEvaluationResult::scored).count();
        int hitCount = (int) results.stream().filter(MemoryRecallEvaluationResult::hit).count();
        double reciprocalRankSum = results.stream()
                .filter(MemoryRecallEvaluationResult::scored)
                .mapToDouble(MemoryRecallEvaluationResult::reciprocalRank)
                .sum();
        double recallSum = results.stream()
                .filter(MemoryRecallEvaluationResult::scored)
                .mapToDouble(MemoryRecallEvaluationResult::recall)
                .sum();
        double precisionSum = results.stream()
                .filter(MemoryRecallEvaluationResult::scored)
                .mapToDouble(MemoryRecallEvaluationResult::precision)
                .sum();
        double noiseRateSum = results.stream()
                .filter(MemoryRecallEvaluationResult::scored)
                .mapToDouble(MemoryRecallEvaluationResult::noiseRate)
                .sum();
        Map<String, Integer> aggregatedChannelHitCounts = aggregateChannelHitCounts(results);
        MemoryRecallEvaluationReport report = new MemoryRecallEvaluationReport(
                results.size(),
                scoredCaseCount,
                hitCount,
                average(hitCount, scoredCaseCount),
                average(reciprocalRankSum, scoredCaseCount),
                average(recallSum, scoredCaseCount),
                average(precisionSum, scoredCaseCount),
                average(noiseRateSum, scoredCaseCount),
                results,
                aggregatedChannelHitCounts);
        emitEvaluationMetric(scoredCaseCount);
        return report;
    }

    private Map<String, Integer> aggregateChannelHitCounts(List<MemoryRecallEvaluationResult> results) {
        if (results.isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> aggregated = new LinkedHashMap<>();
        for (MemoryRecallEvaluationResult result : results) {
            Map<String, Integer> caseChannels = result.channelHitCounts();
            if (caseChannels.isEmpty()) {
                continue;
            }
            caseChannels.forEach((channel, count) -> aggregated.merge(channel, count, Integer::sum));
        }
        return aggregated;
    }

    private void emitEvaluationMetric(int scoredCaseCount) {
        try {
            observationPort.recordEvent(new ObservationEvent(
                    OBSERVATION_EVALUATE_EVENT,
                    Instant.now(),
                    ObservationEvent.DEFAULT_AMOUNT,
                    Map.of(OBSERVATION_ATTR_OUTCOME,
                            scoredCaseCount > 0 ? OBSERVATION_OUTCOME_SUCCESS : OBSERVATION_OUTCOME_EMPTY)));
        } catch (RuntimeException ignored) {
            // Observation emission is best-effort and must not change evaluation semantics.
        }
    }

    private MemoryRecallEvaluationResult evaluateCase(MemoryRecallGoldenCase goldenCase, int topK) {
        MemoryContextAttribution attribution = retrievalPipelinePort.loadWithAttribution(MemoryLoadRequest.builder()
                .conversationId(goldenCase.conversationId())
                .userId(goldenCase.userId())
                .currentQuestion(goldenCase.query())
                .build());
        MemoryContext context = attribution.context();
        List<String> retrievedIds = rankedMemoryIds(context, topK);
        List<String> expectedIds = goldenCase.expectedMemoryIds();
        Map<String, Integer> channelHitCounts = countChannelHits(attribution.channelCandidateIds(), expectedIds);
        if (expectedIds.isEmpty()) {
            return new MemoryRecallEvaluationResult(
                    goldenCase.caseId(),
                    goldenCase.query(),
                    expectedIds,
                    retrievedIds,
                    List.of(),
                    false,
                    false,
                    0D,
                    0D,
                    0D,
                    0D,
                    channelHitCounts);
        }
        Set<String> retrievedSet = new LinkedHashSet<>(retrievedIds);
        List<String> missing = expectedIds.stream()
                .filter(expectedId -> !retrievedSet.contains(expectedId))
                .toList();
        int firstHitRank = firstHitRank(retrievedIds, expectedIds);
        int matchedCount = expectedIds.size() - missing.size();
        double precision = retrievedIds.isEmpty() ? 0D : (double) matchedCount / retrievedIds.size();
        double noiseRate = retrievedIds.isEmpty() ? 0D : (double) (retrievedIds.size() - matchedCount) / retrievedIds.size();
        return new MemoryRecallEvaluationResult(
                goldenCase.caseId(),
                goldenCase.query(),
                expectedIds,
                retrievedIds,
                missing,
                true,
                firstHitRank > 0,
                firstHitRank > 0 ? 1D / firstHitRank : 0D,
                (double) matchedCount / expectedIds.size(),
                precision,
                noiseRate,
                channelHitCounts);
    }

    private Map<String, Integer> countChannelHits(Map<String, List<String>> channelCandidateIds,
                                                  List<String> expectedMemoryIds) {
        if (channelCandidateIds == null || channelCandidateIds.isEmpty()) {
            return Map.of();
        }
        if (expectedMemoryIds == null || expectedMemoryIds.isEmpty()) {
            return Map.of();
        }
        Set<String> expectedSet = new LinkedHashSet<>(expectedMemoryIds);
        Map<String, Integer> counts = new LinkedHashMap<>();
        channelCandidateIds.forEach((channel, candidateIds) -> {
            if (channel == null || channel.isBlank() || candidateIds == null) {
                return;
            }
            int hits = (int) candidateIds.stream()
                    .filter(id -> id != null && expectedSet.contains(id))
                    .count();
            counts.put(channel, hits);
        });
        return counts;
    }

    private List<String> rankedMemoryIds(MemoryContext context, int topK) {
        return rankedItems(context).stream()
                .map(MemoryItem::getId)
                .limit(topK)
                .toList();
    }

    private List<MemoryItem> rankedItems(MemoryContext context) {
        if (context == null) {
            return List.of();
        }
        Map<String, MemoryItem> winners = new LinkedHashMap<>();
        collect(winners, context.getProfileMemories());
        collect(winners, context.getCorrectionMemories());
        collect(winners, context.getShortTermMemories());
        collect(winners, context.getLongTermMemories());
        collect(winners, context.getSemanticMemories());
        collect(winners, context.getBusinessDocumentMemories());
        return winners.values().stream()
                .sorted(Comparator.comparingDouble(this::relevanceScore).reversed()
                        .thenComparing(MemoryItem::getCreateTime, Comparator.nullsLast(LocalDateTime::compareTo))
                        .thenComparing(MemoryItem::getId))
                .toList();
    }

    private void collect(Map<String, MemoryItem> winners, List<MemoryItem> items) {
        if (items == null || items.isEmpty()) {
            return;
        }
        for (MemoryItem item : items) {
            if (item == null || isBlank(item.getId())) {
                continue;
            }
            winners.merge(item.getId(), item, this::prefer);
        }
    }

    private MemoryItem prefer(MemoryItem current, MemoryItem next) {
        int byRelevance = Double.compare(relevanceScore(next), relevanceScore(current));
        if (byRelevance != 0) {
            return byRelevance > 0 ? next : current;
        }
        int byTime = Comparator.nullsFirst(LocalDateTime::compareTo)
                .compare(next.getCreateTime(), current.getCreateTime());
        return byTime > 0 ? next : current;
    }

    private int firstHitRank(List<String> retrievedIds, List<String> expectedIds) {
        Set<String> expected = new LinkedHashSet<>(expectedIds);
        for (int index = 0; index < retrievedIds.size(); index++) {
            if (expected.contains(retrievedIds.get(index))) {
                return index + 1;
            }
        }
        return 0;
    }

    private double relevanceScore(MemoryItem item) {
        return item.getRelevanceScore() == null ? 0D : item.getRelevanceScore();
    }

    private double average(double numerator, int denominator) {
        return denominator > 0 ? numerator / denominator : 0D;
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}

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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;

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

    private final MemoryRetrievalPipelinePort retrievalPipelinePort;

    public MemoryRecallEvaluationService(MemoryRetrievalPipelinePort retrievalPipelinePort) {
        this.retrievalPipelinePort = Objects.requireNonNull(retrievalPipelinePort,
                "retrievalPipelinePort must not be null");
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
        return new MemoryRecallEvaluationReport(
                results.size(),
                scoredCaseCount,
                hitCount,
                average(hitCount, scoredCaseCount),
                average(reciprocalRankSum, scoredCaseCount),
                average(recallSum, scoredCaseCount),
                results);
    }

    private MemoryRecallEvaluationResult evaluateCase(MemoryRecallGoldenCase goldenCase, int topK) {
        MemoryContext context = retrievalPipelinePort.load(MemoryLoadRequest.builder()
                .conversationId(goldenCase.conversationId())
                .userId(goldenCase.userId())
                .currentQuestion(goldenCase.query())
                .build());
        List<String> retrievedIds = rankedMemoryIds(context, topK);
        List<String> expectedIds = goldenCase.expectedMemoryIds();
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
                    0D);
        }
        Set<String> retrievedSet = new LinkedHashSet<>(retrievedIds);
        List<String> missing = expectedIds.stream()
                .filter(expectedId -> !retrievedSet.contains(expectedId))
                .toList();
        int firstHitRank = firstHitRank(retrievedIds, expectedIds);
        int matchedCount = expectedIds.size() - missing.size();
        return new MemoryRecallEvaluationResult(
                goldenCase.caseId(),
                goldenCase.query(),
                expectedIds,
                retrievedIds,
                missing,
                true,
                firstHitRank > 0,
                firstHitRank > 0 ? 1D / firstHitRank : 0D,
                (double) matchedCount / expectedIds.size());
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

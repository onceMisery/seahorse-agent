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

package com.miracle.ai.seahorse.agent.kernel.application.retrieval;

import com.miracle.ai.seahorse.agent.kernel.application.trace.KernelRagTraceRecorder;
import com.miracle.ai.seahorse.agent.kernel.domain.intent.SubQuestionIntent;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievalOptions;
import com.miracle.ai.seahorse.agent.kernel.domain.retrieval.RetrievedChunk;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunScope;
import com.miracle.ai.seahorse.agent.kernel.domain.trace.TraceRunStartCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCase;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCaseDiagnostics;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCaseResult;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationChunkDiagnostic;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationStrategy;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationStrategyDelta;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 检索评测内核服务。
 *
 * <p>该服务只编排现有检索入口并计算离线指标，不保存评测集，不引入外部评测框架。
 */
public class KernelRetrievalEvaluationService implements RetrievalEvaluationInboundPort {

    private static final String TRACE_NAME_EVALUATION_CASE = "retrieval-evaluation-case";
    private static final String TRACE_ENTRY_EVALUATE_CASE = "KernelRetrievalEvaluationService.evaluateCase";

    private static final String STATUS_SUCCESS = "SUCCESS";
    private static final String STATUS_MISS = "MISS";
    private static final String STATUS_EMPTY = "EMPTY";
    private static final String STATUS_NO_EXPECTED_TARGETS = "NO_EXPECTED_TARGETS";
    private static final String STATUS_FAILED = "FAILED";

    private final KernelRetrievalEngine retrievalEngine;
    private final KernelRagTraceRecorder traceRecorder;

    public KernelRetrievalEvaluationService(KernelRetrievalEngine retrievalEngine) {
        this(retrievalEngine, KernelRagTraceRecorder.noop());
    }

    public KernelRetrievalEvaluationService(KernelRetrievalEngine retrievalEngine,
                                            KernelRagTraceRecorder traceRecorder) {
        this.retrievalEngine = Objects.requireNonNull(retrievalEngine, "retrievalEngine must not be null");
        this.traceRecorder = Objects.requireNonNullElseGet(traceRecorder, KernelRagTraceRecorder::noop);
    }

    @Override
    public RetrievalEvaluationReport evaluate(RetrievalEvaluationCommand command) {
        RetrievalEvaluationCommand safeCommand = command == null
                ? new RetrievalEvaluationCommand("", 5, null, List.of())
                : command;
        int topK = safeCommand.topK();
        RetrievalOptions defaultOptions = safeCommand.options() == null
                ? RetrievalOptions.defaults(topK)
                : safeCommand.options();
        List<RetrievalEvaluationCaseResult> results = safeCommand.cases().stream()
                .map(evaluationCase -> evaluateCase(evaluationCase, topK, defaultOptions))
                .toList();
        List<RetrievalEvaluationCaseResult> evaluableResults = results.stream()
                .filter(result -> !STATUS_NO_EXPECTED_TARGETS.equals(result.status()))
                .toList();
        int evaluableCount = evaluableResults.size();
        return new RetrievalEvaluationReport(
                safeCommand.strategyName(),
                topK,
                results.size(),
                evaluableCount,
                average(evaluableResults, RetrievalEvaluationCaseResult::recallAtK),
                average(evaluableResults, RetrievalEvaluationCaseResult::precisionAtK),
                average(evaluableResults, RetrievalEvaluationCaseResult::reciprocalRank),
                average(evaluableResults, RetrievalEvaluationCaseResult::ndcgAtK),
                ratio(evaluableResults.stream().filter(result -> result.retrievedCount() == 0).count(), evaluableCount),
                averageLatency(results),
                p95Latency(results),
                results);
    }

    @Override
    public RetrievalEvaluationComparisonReport compare(RetrievalEvaluationComparisonCommand command) {
        RetrievalEvaluationComparisonCommand safeCommand = command == null
                ? new RetrievalEvaluationComparisonCommand("", 5, List.of(), List.of())
                : command;
        List<RetrievalEvaluationReport> reports = safeCommand.strategies().stream()
                .map(strategy -> evaluate(strategyCommand(strategy, safeCommand)))
                .toList();
        if (reports.isEmpty()) {
            return new RetrievalEvaluationComparisonReport("", "", List.of(), List.of());
        }
        RetrievalEvaluationReport baseline = baselineReport(safeCommand.baselineStrategyName(), reports);
        RetrievalEvaluationReport winner = reports.stream()
                .max(this::compareReportQuality)
                .orElse(baseline);
        List<RetrievalEvaluationStrategyDelta> deltas = reports.stream()
                .map(report -> deltaFromBaseline(report, baseline))
                .toList();
        return new RetrievalEvaluationComparisonReport(
                baseline.strategyName(),
                winner.strategyName(),
                reports,
                deltas);
    }

    private RetrievalEvaluationCommand strategyCommand(RetrievalEvaluationStrategy strategy,
                                                       RetrievalEvaluationComparisonCommand command) {
        RetrievalEvaluationStrategy safeStrategy = strategy == null
                ? new RetrievalEvaluationStrategy("", 0, null)
                : strategy;
        int strategyTopK = safeStrategy.topK() > 0 ? safeStrategy.topK() : command.topK();
        return new RetrievalEvaluationCommand(
                safeStrategy.strategyName(),
                strategyTopK,
                safeStrategy.options(),
                command.cases());
    }

    private RetrievalEvaluationReport baselineReport(String baselineStrategyName,
                                                     List<RetrievalEvaluationReport> reports) {
        if (hasText(baselineStrategyName)) {
            return reports.stream()
                    .filter(report -> baselineStrategyName.equals(report.strategyName()))
                    .findFirst()
                    .orElse(reports.get(0));
        }
        return reports.get(0);
    }

    private RetrievalEvaluationStrategyDelta deltaFromBaseline(RetrievalEvaluationReport report,
                                                               RetrievalEvaluationReport baseline) {
        return new RetrievalEvaluationStrategyDelta(
                report.strategyName(),
                report.recallAtK() - baseline.recallAtK(),
                report.precisionAtK() - baseline.precisionAtK(),
                report.mrr() - baseline.mrr(),
                report.ndcgAtK() - baseline.ndcgAtK(),
                report.emptyRecallRate() - baseline.emptyRecallRate(),
                report.averageLatencyMs() - baseline.averageLatencyMs(),
                report.p95LatencyMs() - baseline.p95LatencyMs());
    }

    private int compareReportQuality(RetrievalEvaluationReport left,
                                     RetrievalEvaluationReport right) {
        // winner 判定优先看排序质量，再看召回与延迟，避免只因耗时更低选中低质量策略。
        int byNdcg = Double.compare(left.ndcgAtK(), right.ndcgAtK());
        if (byNdcg != 0) {
            return byNdcg;
        }
        int byRecall = Double.compare(left.recallAtK(), right.recallAtK());
        if (byRecall != 0) {
            return byRecall;
        }
        int byPrecision = Double.compare(left.precisionAtK(), right.precisionAtK());
        if (byPrecision != 0) {
            return byPrecision;
        }
        int byMrr = Double.compare(left.mrr(), right.mrr());
        if (byMrr != 0) {
            return byMrr;
        }
        int byEmptyRecall = Double.compare(right.emptyRecallRate(), left.emptyRecallRate());
        if (byEmptyRecall != 0) {
            return byEmptyRecall;
        }
        return Double.compare(right.averageLatencyMs(), left.averageLatencyMs());
    }

    private RetrievalEvaluationCaseResult evaluateCase(RetrievalEvaluationCase evaluationCase,
                                                       int topK,
                                                       RetrievalOptions defaultOptions) {
        RetrievalEvaluationCase safeCase = evaluationCase == null
                ? new RetrievalEvaluationCase("", "", List.of(), List.of(), List.of(), null, null)
                : evaluationCase;
        Set<String> expectedTargets = expectedTargets(safeCase);
        long started = System.nanoTime();
        TraceRunScope traceRunScope = traceRecorder.startRun(new TraceRunStartCommand(
                TRACE_NAME_EVALUATION_CASE,
                TRACE_ENTRY_EVALUATE_CASE,
                null,
                safeCase.caseId(),
                null));
        try {
            List<RetrievedChunk> chunks = retrievalEngine.retrieveKnowledgeChannels(
                    List.of(new SubQuestionIntent(safeCase.question(), List.of())),
                    topK,
                    safeCase.filter(),
                    safeCase.options() == null ? defaultOptions : safeCase.options(),
                    traceRunScope);
            List<RetrievedChunk> topChunks = Objects.requireNonNullElse(chunks, List.<RetrievedChunk>of()).stream()
                    .limit(topK)
                    .toList();
            List<String> negativeHitChunkIds = negativeHitChunkIds(topChunks, safeCase);
            RetrievalEvaluationCaseResult result;
            if (expectedTargets.isEmpty()) {
                result = result(safeCase, topChunks, 0, expectedTargets, 0D, 0D, 0D, 0D, negativeHitChunkIds,
                        elapsedMs(started), STATUS_NO_EXPECTED_TARGETS, "", traceId(traceRunScope));
                traceRecorder.finishRun(traceRunScope);
                return result;
            }

            Set<String> matchedTargets = new LinkedHashSet<>();
            int positiveHitChunkCount = 0;
            double reciprocalRank = 0D;
            double dcg = 0D;
            for (int index = 0; index < topChunks.size(); index++) {
                Set<String> matchedAtRank = matchedTargets(topChunks.get(index), expectedTargets);
                if (!matchedAtRank.isEmpty()) {
                    positiveHitChunkCount++;
                    matchedTargets.addAll(matchedAtRank);
                    int rank = index + 1;
                    if (reciprocalRank == 0D) {
                        reciprocalRank = 1D / rank;
                    }
                    dcg += 1D / log2(rank + 1D);
                }
            }
            double recall = ratio(matchedTargets.size(), expectedTargets.size());
            double precision = ratio(positiveHitChunkCount, topK);
            double ndcg = idealDcg(Math.min(expectedTargets.size(), topK)) == 0D
                    ? 0D
                    : dcg / idealDcg(Math.min(expectedTargets.size(), topK));
            String status = topChunks.isEmpty() ? STATUS_EMPTY : matchedTargets.isEmpty() ? STATUS_MISS : STATUS_SUCCESS;
            result = result(safeCase, topChunks, matchedTargets.size(), expectedTargets, recall, reciprocalRank, ndcg, precision,
                    negativeHitChunkIds, elapsedMs(started), status, "", traceId(traceRunScope));
            traceRecorder.finishRun(traceRunScope);
            return result;
        } catch (RuntimeException ex) {
            traceRecorder.finishRun(traceRunScope, ex);
            return failedResult(safeCase, elapsedMs(started), traceId(traceRunScope), ex);
        }
    }

    private RetrievalEvaluationCaseResult failedResult(RetrievalEvaluationCase evaluationCase,
                                                       long latencyMs,
                                                       String traceId,
                                                       RuntimeException ex) {
        return new RetrievalEvaluationCaseResult(
                evaluationCase.caseId(),
                evaluationCase.question(),
                List.of(),
                List.of(),
                0,
                0,
                0D,
                0D,
                0D,
                latencyMs,
                STATUS_FAILED,
                ex.getClass().getSimpleName(),
                0D,
                0,
                List.of(),
                diagnostics(evaluationCase, List.of(), expectedTargets(evaluationCase), List.of(), traceId));
    }

    private RetrievalEvaluationCaseResult result(RetrievalEvaluationCase evaluationCase,
                                                 List<RetrievedChunk> chunks,
                                                 int hitCount,
                                                 Set<String> expectedTargets,
                                                 double recall,
                                                 double reciprocalRank,
                                                 double ndcg,
                                                 double precision,
                                                 List<String> negativeHitChunkIds,
                                                 long latencyMs,
                                                 String status,
                                                 String errorMessage,
                                                 String traceId) {
        return new RetrievalEvaluationCaseResult(
                evaluationCase.caseId(),
                evaluationCase.question(),
                chunks.stream()
                        .map(RetrievedChunk::getId)
                        .filter(this::hasText)
                        .toList(),
                chunks.stream()
                        .map(RetrievedChunk::getDocId)
                        .filter(this::hasText)
                        .distinct()
                        .toList(),
                chunks.size(),
                hitCount,
                recall,
                reciprocalRank,
                ndcg,
                latencyMs,
                status,
                errorMessage,
                precision,
                negativeHitChunkIds.size(),
                negativeHitChunkIds,
                diagnostics(evaluationCase, chunks, expectedTargets, negativeHitChunkIds, traceId));
    }

    private RetrievalEvaluationCaseDiagnostics diagnostics(RetrievalEvaluationCase evaluationCase,
                                                           List<RetrievedChunk> chunks,
                                                           Set<String> expectedTargets,
                                                           List<String> negativeHitChunkIds,
                                                           String traceId) {
        Set<String> retrievedChunkIds = new LinkedHashSet<>();
        Set<String> retrievedDocIds = new LinkedHashSet<>();
        Set<String> retrievedKbIds = new LinkedHashSet<>();
        for (RetrievedChunk chunk : chunks) {
            if (chunk == null) {
                continue;
            }
            addIfText(retrievedChunkIds, chunk.getId());
            addIfText(retrievedDocIds, chunk.getDocId());
            addIfText(retrievedKbIds, chunk.getKbId());
        }
        Set<String> negativeHits = new LinkedHashSet<>(Objects.requireNonNullElse(negativeHitChunkIds, List.of()));
        List<RetrievalEvaluationChunkDiagnostic> retrievedChunks = new ArrayList<>();
        for (int index = 0; index < chunks.size(); index++) {
            RetrievedChunk chunk = chunks.get(index);
            if (chunk == null) {
                continue;
            }
            retrievedChunks.add(new RetrievalEvaluationChunkDiagnostic(
                    index + 1,
                    chunk.getId(),
                    chunk.getDocId(),
                    chunk.getKbId(),
                    chunk.getScore() == null ? null : chunk.getScore().doubleValue(),
                    new ArrayList<>(matchedTargets(chunk, expectedTargets)),
                    hasText(chunk.getId()) && negativeHits.contains(chunk.getId())));
        }
        return new RetrievalEvaluationCaseDiagnostics(
                evaluationCase.expectedChunkIds(),
                evaluationCase.expectedDocIds(),
                evaluationCase.expectedKbIds(),
                missing(evaluationCase.expectedChunkIds(), retrievedChunkIds),
                missing(evaluationCase.expectedDocIds(), retrievedDocIds),
                missing(evaluationCase.expectedKbIds(), retrievedKbIds),
                retrievedChunks,
                traceId);
    }

    private List<String> missing(List<String> expectedIds, Set<String> retrievedIds) {
        if (expectedIds == null || expectedIds.isEmpty()) {
            return List.of();
        }
        return expectedIds.stream()
                .filter(this::hasText)
                .filter(id -> !retrievedIds.contains(id))
                .distinct()
                .toList();
    }

    private void addIfText(Set<String> values, String value) {
        if (hasText(value)) {
            values.add(value);
        }
    }

    private Set<String> expectedTargets(RetrievalEvaluationCase evaluationCase) {
        Set<String> targets = new LinkedHashSet<>();
        evaluationCase.expectedChunkIds().forEach(id -> targets.add("chunk:" + id));
        evaluationCase.expectedDocIds().forEach(id -> targets.add("doc:" + id));
        evaluationCase.expectedKbIds().forEach(id -> targets.add("kb:" + id));
        return targets;
    }

    private List<String> negativeHitChunkIds(List<RetrievedChunk> chunks, RetrievalEvaluationCase evaluationCase) {
        if (chunks.isEmpty() || evaluationCase.negativeChunkIds().isEmpty()) {
            return List.of();
        }
        Set<String> negativeChunkIds = new LinkedHashSet<>(evaluationCase.negativeChunkIds());
        return chunks.stream()
                .map(RetrievedChunk::getId)
                .filter(this::hasText)
                .filter(negativeChunkIds::contains)
                .distinct()
                .toList();
    }

    private Set<String> matchedTargets(RetrievedChunk chunk, Set<String> expectedTargets) {
        if (chunk == null || expectedTargets.isEmpty()) {
            return Set.of();
        }
        Set<String> matched = new LinkedHashSet<>();
        addIfMatched(matched, expectedTargets, "chunk:", chunk.getId());
        addIfMatched(matched, expectedTargets, "doc:", chunk.getDocId());
        addIfMatched(matched, expectedTargets, "kb:", chunk.getKbId());
        return matched;
    }

    private void addIfMatched(Set<String> matched, Set<String> expectedTargets, String prefix, String value) {
        if (!hasText(value)) {
            return;
        }
        String key = prefix + value;
        if (expectedTargets.contains(key)) {
            matched.add(key);
        }
    }

    private double average(List<RetrievalEvaluationCaseResult> results,
                           MetricExtractor extractor) {
        if (results.isEmpty()) {
            return 0D;
        }
        return results.stream()
                .mapToDouble(extractor::extract)
                .average()
                .orElse(0D);
    }

    private double averageLatency(List<RetrievalEvaluationCaseResult> results) {
        if (results.isEmpty()) {
            return 0D;
        }
        return results.stream()
                .mapToLong(RetrievalEvaluationCaseResult::latencyMs)
                .average()
                .orElse(0D);
    }

    private double p95Latency(List<RetrievalEvaluationCaseResult> results) {
        if (results.isEmpty()) {
            return 0D;
        }
        List<Long> latencies = new ArrayList<>(results.stream()
                .map(RetrievalEvaluationCaseResult::latencyMs)
                .sorted(Comparator.naturalOrder())
                .toList());
        int index = Math.max(0, (int) Math.ceil(latencies.size() * 0.95D) - 1);
        return latencies.get(index);
    }

    private double ratio(double numerator, double denominator) {
        return denominator <= 0D ? 0D : numerator / denominator;
    }

    private double idealDcg(int relevantCount) {
        double value = 0D;
        for (int rank = 1; rank <= relevantCount; rank++) {
            value += 1D / log2(rank + 1D);
        }
        return value;
    }

    private double log2(double value) {
        return Math.log(value) / Math.log(2D);
    }

    private long elapsedMs(long startedNanos) {
        return TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedNanos);
    }

    private String traceId(TraceRunScope traceRunScope) {
        return traceRunScope != null && traceRunScope.active() ? traceRunScope.traceId() : "";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    @FunctionalInterface
    private interface MetricExtractor {

        double extract(RetrievalEvaluationCaseResult result);
    }
}

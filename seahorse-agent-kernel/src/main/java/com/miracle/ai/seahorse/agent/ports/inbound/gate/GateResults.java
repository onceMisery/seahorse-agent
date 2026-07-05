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

package com.miracle.ai.seahorse.agent.ports.inbound.gate;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileProductionGateCheck;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public final class GateResults {

    private GateResults() {
    }

    public static GateResult fromAgentReport(ProductionGateReport report) {
        Objects.requireNonNull(report, "report must not be null");
        List<String> blockingCodes = report.items().stream()
                .filter(item -> item.status() == ProductionGateStatus.FAIL)
                .map(item -> item.code().name())
                .toList();
        return new GateResult(
                "AGENT",
                report.agentId(),
                report.status().name(),
                report.status() != ProductionGateStatus.FAIL,
                blockingCodes,
                report.items().stream()
                        .map(GateResults::fromAgentItem)
                        .toList(),
                report.checkedAt(),
                "ProductionGateReport",
                report.reportId());
    }

    public static GateResult fromRunProfileCheck(RunProfileProductionGateCheck check) {
        Objects.requireNonNull(check, "check must not be null");
        List<RunProfileProductionGateCheck.CheckItem> items = Objects.requireNonNullElse(
                check.getCheckItems(),
                List.<RunProfileProductionGateCheck.CheckItem>of());
        List<String> blockingCodes = Objects.requireNonNullElse(
                check.getBlockingCodes(),
                List.<String>of());
        return new GateResult(
                "RUN_PROFILE",
                String.valueOf(check.getRunProfileId()),
                check.isPassed() ? "PASS" : "FAIL",
                check.isPassed(),
                blockingCodes,
                items.stream()
                        .filter(Objects::nonNull)
                        .map(GateResults::fromRunProfileItem)
                        .toList(),
                Instant.now(),
                "RunProfileProductionGateCheck",
                String.valueOf(check.getRunProfileId()));
    }

    public static GateResult fromRetrievalStrategyComparison(RetrievalEvaluationComparisonRecord comparison) {
        Objects.requireNonNull(comparison, "comparison must not be null");
        RetrievalEvaluationComparisonReport report = comparison.report();
        List<GateResultItem> items = retrievalStrategyItems(report);
        List<String> blockingCodes = items.stream()
                .filter(item -> "FAIL".equals(item.status()))
                .map(GateResultItem::code)
                .toList();
        String winner = trimToNull(report.winnerStrategyName());
        return new GateResult(
                "RAG_STRATEGY",
                comparison.knowledgeBaseId() + ":" + Objects.requireNonNullElse(winner, "unknown"),
                blockingCodes.isEmpty() ? "PASS" : "FAIL",
                blockingCodes.isEmpty(),
                blockingCodes,
                items,
                comparison.createTime(),
                "RetrievalEvaluationComparisonRecord",
                comparison.comparisonId());
    }

    private static GateResultItem fromAgentItem(ProductionGateCheckItem item) {
        return new GateResultItem(item.code().name(), item.status().name(), item.message());
    }

    private static GateResultItem fromRunProfileItem(RunProfileProductionGateCheck.CheckItem item) {
        return new GateResultItem(item.getCode(), item.getStatus(), item.getMessage());
    }

    private static List<GateResultItem> retrievalStrategyItems(RetrievalEvaluationComparisonReport report) {
        RetrievalEvaluationComparisonReport safeReport = Objects.requireNonNull(report, "report must not be null");
        String baselineName = trimToNull(safeReport.baselineStrategyName());
        String winnerName = trimToNull(safeReport.winnerStrategyName());
        java.util.ArrayList<GateResultItem> items = new java.util.ArrayList<>();
        if (baselineName == null) {
            items.add(new GateResultItem("RAG_BASELINE_PRESENT", "FAIL",
                    "Comparison baseline strategy is missing"));
        } else {
            items.add(new GateResultItem("RAG_BASELINE_PRESENT", "PASS",
                    "Comparison baseline strategy is " + baselineName));
        }
        if (winnerName == null) {
            items.add(new GateResultItem("RAG_WINNER_PRESENT", "FAIL",
                    "Comparison winner strategy is missing"));
        } else {
            items.add(new GateResultItem("RAG_WINNER_PRESENT", "PASS",
                    "Comparison winner strategy is " + winnerName));
        }
        RetrievalEvaluationReport baseline = reportFor(safeReport, baselineName);
        RetrievalEvaluationReport winner = reportFor(safeReport, winnerName);
        if (baseline == null || winner == null) {
            items.add(new GateResultItem("RAG_STRATEGY_METRICS_PRESENT", "FAIL",
                    "Comparison report is missing baseline or winner metrics"));
            return List.copyOf(items);
        }
        items.add(metricItem("RAG_EVALUABLE_CASES_PRESENT",
                winner.evaluableCaseCount() > 0,
                "Winner evaluable cases: " + winner.evaluableCaseCount()));
        items.add(metricItem("RAG_RECALL_NOT_REGRESSED",
                winner.recallAtK() >= baseline.recallAtK(),
                "Winner recall@k " + winner.recallAtK() + " vs baseline " + baseline.recallAtK()));
        items.add(metricItem("RAG_PRECISION_NOT_REGRESSED",
                winner.precisionAtK() >= baseline.precisionAtK(),
                "Winner precision@k " + winner.precisionAtK() + " vs baseline " + baseline.precisionAtK()));
        items.add(metricItem("RAG_MRR_NOT_REGRESSED",
                winner.mrr() >= baseline.mrr(),
                "Winner MRR " + winner.mrr() + " vs baseline " + baseline.mrr()));
        items.add(metricItem("RAG_NDCG_NOT_REGRESSED",
                winner.ndcgAtK() >= baseline.ndcgAtK(),
                "Winner NDCG@k " + winner.ndcgAtK() + " vs baseline " + baseline.ndcgAtK()));
        items.add(metricItem("RAG_EMPTY_RECALL_NOT_REGRESSED",
                winner.emptyRecallRate() <= baseline.emptyRecallRate(),
                "Winner empty recall rate " + winner.emptyRecallRate()
                        + " vs baseline " + baseline.emptyRecallRate()));
        return List.copyOf(items);
    }

    private static GateResultItem metricItem(String code, boolean passed, String message) {
        return new GateResultItem(code, passed ? "PASS" : "FAIL", message);
    }

    private static RetrievalEvaluationReport reportFor(RetrievalEvaluationComparisonReport report, String strategyName) {
        if (strategyName == null) {
            return null;
        }
        return report.reports().stream()
                .filter(strategyReport -> strategyName.equals(strategyReport.strategyName()))
                .findFirst()
                .orElse(null);
    }

    private static String trimToNull(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }
}

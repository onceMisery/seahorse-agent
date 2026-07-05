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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateCheckItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.gate.ProductionGateStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonRecord;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationComparisonReport;
import com.miracle.ai.seahorse.agent.ports.inbound.retrieval.RetrievalEvaluationReport;
import com.miracle.ai.seahorse.agent.ports.inbound.runprofile.RunProfileProductionGateCheck;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateResultsTests {

    @Test
    void shouldProjectAgentProductionGateReport() {
        Instant checkedAt = Instant.parse("2026-07-05T00:00:00Z");
        ProductionGateReport report = new ProductionGateReport(
                "gate-1",
                "agent-1",
                "version-1",
                ProductionGateStatus.WARN,
                List.of(
                        ProductionGateCheckItem.pass(ProductionGateCheckCode.AUDIT_LEDGER_ENABLED, "audit ok"),
                        ProductionGateCheckItem.warn(ProductionGateCheckCode.EVAL_PASSING, "eval missing")),
                checkedAt);

        GateResult result = GateResults.fromAgentReport(report);

        assertEquals("AGENT", result.subjectType());
        assertEquals("agent-1", result.subjectId());
        assertEquals("WARN", result.status());
        assertTrue(result.passed());
        assertEquals(List.of(), result.blockingCodes());
        assertEquals(checkedAt, result.checkedAt());
        assertEquals("ProductionGateReport", result.sourceType());
        assertEquals("gate-1", result.sourceId());
        assertEquals("EVAL_PASSING", result.items().get(1).code());
        assertEquals("WARN", result.items().get(1).status());
    }

    @Test
    void shouldProjectRunProfileProductionGateCheck() {
        RunProfileProductionGateCheck check = RunProfileProductionGateCheck.builder()
                .runProfileId(12L)
                .passed(false)
                .riskLevel("HIGH")
                .blockingCodes(List.of("APPROVAL_NOT_ENFORCED"))
                .checkItems(List.of(RunProfileProductionGateCheck.CheckItem.builder()
                        .code("APPROVAL_NOT_ENFORCED")
                        .status("BLOCK")
                        .message("High-risk tool approval must be enabled before production")
                        .build()))
                .build();

        GateResult result = GateResults.fromRunProfileCheck(check);

        assertEquals("RUN_PROFILE", result.subjectType());
        assertEquals("12", result.subjectId());
        assertEquals("FAIL", result.status());
        assertFalse(result.passed());
        assertEquals(List.of("APPROVAL_NOT_ENFORCED"), result.blockingCodes());
        assertEquals("RunProfileProductionGateCheck", result.sourceType());
        assertEquals("12", result.sourceId());
        assertEquals("FAIL", result.items().get(0).status());
    }

    @Test
    void shouldProjectPassingRetrievalStrategyComparison() {
        Instant checkedAt = Instant.parse("2026-07-05T01:00:00Z");
        RetrievalEvaluationComparisonRecord comparison = comparison(
                checkedAt,
                report("baseline", 5, 4, 0.6D, 0.4D, 0.5D, 0.55D, 0.2D),
                report("candidate", 5, 4, 0.7D, 0.5D, 0.6D, 0.65D, 0.1D));

        GateResult result = GateResults.fromRetrievalStrategyComparison(comparison);

        assertEquals("RAG_STRATEGY", result.subjectType());
        assertEquals("kb-1:candidate", result.subjectId());
        assertEquals("PASS", result.status());
        assertTrue(result.passed());
        assertEquals(List.of(), result.blockingCodes());
        assertEquals(checkedAt, result.checkedAt());
        assertEquals("RetrievalEvaluationComparisonRecord", result.sourceType());
        assertEquals("comparison-1", result.sourceId());
        assertEquals("RAG_NDCG_NOT_REGRESSED", result.items().get(6).code());
        assertEquals("PASS", result.items().get(6).status());
    }

    @Test
    void shouldProjectFailingRetrievalStrategyComparison() {
        RetrievalEvaluationComparisonRecord comparison = comparison(
                Instant.parse("2026-07-05T01:00:00Z"),
                report("baseline", 5, 4, 0.6D, 0.4D, 0.5D, 0.55D, 0.1D),
                report("candidate", 5, 0, 0.7D, 0.5D, 0.6D, 0.65D, 0.2D));

        GateResult result = GateResults.fromRetrievalStrategyComparison(comparison);

        assertEquals("FAIL", result.status());
        assertFalse(result.passed());
        assertEquals(
                List.of("RAG_EVALUABLE_CASES_PRESENT", "RAG_EMPTY_RECALL_NOT_REGRESSED"),
                result.blockingCodes());
    }

    private static RetrievalEvaluationComparisonRecord comparison(
            Instant checkedAt,
            RetrievalEvaluationReport baseline,
            RetrievalEvaluationReport candidate) {
        return new RetrievalEvaluationComparisonRecord(
                "comparison-1",
                "kb-1",
                "dataset-1",
                new RetrievalEvaluationComparisonReport(
                        "baseline",
                        "candidate",
                        List.of(baseline, candidate),
                        List.of()),
                checkedAt);
    }

    private static RetrievalEvaluationReport report(String strategyName,
                                                    int caseCount,
                                                    int evaluableCaseCount,
                                                    double recallAtK,
                                                    double precisionAtK,
                                                    double mrr,
                                                    double ndcgAtK,
                                                    double emptyRecallRate) {
        return new RetrievalEvaluationReport(
                strategyName,
                5,
                caseCount,
                evaluableCaseCount,
                recallAtK,
                precisionAtK,
                mrr,
                ndcgAtK,
                emptyRecallRate,
                10D,
                20D,
                List.of());
    }
}

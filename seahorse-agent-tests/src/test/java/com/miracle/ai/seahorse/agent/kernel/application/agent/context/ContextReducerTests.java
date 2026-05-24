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

package com.miracle.ai.seahorse.agent.kernel.application.agent.context;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextReductionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextReductionResult.DropReason;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Slice 1e：ContextReducer 核心降维路径覆盖。
 */
class ContextReducerTests {

    private static final Instant FIXED_NOW = Instant.parse("2026-05-24T00:00:00Z");

    private final ContextReducer reducer = new ContextReducer();

    @Test
    void contextWithinBudgetIsReturnedUnchanged() {
        ContextPack pack = pack(1024,
                item("i-1", 0.8, 200),
                item("i-2", 0.6, 200),
                item("i-3", 0.5, 200));

        ContextReductionResult result = reducer.reduce(pack, 1024);

        assertThat(result.reduced()).isFalse();
        assertThat(result.keptItems()).extracting(ContextItem::itemId)
                .containsExactly("i-1", "i-2", "i-3");
        assertThat(result.keptTokens()).isEqualTo(600);
        assertThat(result.omittedSummary()).contains("fits within budget");
    }

    @Test
    void itemsAboveBudgetAreDroppedByLowestScoreFirst() {
        ContextPack pack = pack(2048,
                item("a", 0.95, 400),
                item("b", 0.6, 400),
                item("c", 0.5, 400),
                item("d", 0.4, 400));

        ContextReductionResult result = reducer.reduce(pack, 800);

        // must-keep "a" (score >= 0.9) + highest-scoring "b" fit; "c"/"d" dropped.
        assertThat(result.keptItems()).extracting(ContextItem::itemId).containsExactly("a", "b");
        assertThat(result.droppedItems()).extracting(d -> d.item().itemId(), d -> d.reason())
                .containsExactly(
                        tuple("c", DropReason.BUDGET_EXCEEDED),
                        tuple("d", DropReason.BUDGET_EXCEEDED));
        assertThat(result.keptTokens()).isEqualTo(800);
        assertThat(result.omittedSummary())
                .startsWith("omitted 2 context items")
                .contains("MEMORY:2")
                .contains("totaling 800 tokens");
    }

    @Test
    void mustKeepItemsAreRetainedEvenWhenOverBudget() {
        ContextPack pack = pack(2048,
                item("must-1", 0.95, 700),
                item("must-2", 0.92, 700),
                item("regular", 0.5, 400));

        ContextReductionResult result = reducer.reduce(pack, 1000);

        assertThat(result.keptItems()).extracting(ContextItem::itemId)
                .containsExactly("must-1", "must-2");
        assertThat(result.keptTokens()).isEqualTo(1400);
        assertThat(result.droppedItems()).extracting(d -> d.item().itemId(), d -> d.reason())
                .containsExactly(tuple("regular", DropReason.BUDGET_EXCEEDED));
    }

    @Test
    void itemLargerThanBudgetIsReportedSeparately() {
        ContextPack pack = pack(4096,
                item("oversized", 0.6, 5000),
                item("normal", 0.5, 200));

        ContextReductionResult result = reducer.reduce(pack, 1000);

        assertThat(result.keptItems()).extracting(ContextItem::itemId).containsExactly("normal");
        assertThat(result.droppedItems()).extracting(d -> d.item().itemId(), d -> d.reason())
                .containsExactly(tuple("oversized", DropReason.TOO_LARGE_FOR_BUDGET));
    }

    @Test
    void scoreFloorDropsLowQualityItemsBeforeBudgetCheck() {
        ContextReducer custom = new ContextReducer(0.9d, 0.4d);
        ContextPack pack = pack(2048,
                item("k", 0.95, 200),
                item("borderline", 0.4, 200),
                item("low", 0.39, 200));

        ContextReductionResult result = custom.reduce(pack, 1000);

        assertThat(result.keptItems()).extracting(ContextItem::itemId).containsExactly("k", "borderline");
        assertThat(result.droppedItems()).extracting(d -> d.item().itemId(), d -> d.reason())
                .containsExactly(tuple("low", DropReason.BELOW_SCORE_FLOOR));
    }

    @Test
    void omittedSummaryAggregatesSourceTypesAcrossDrops() {
        ContextPack pack = ContextPack(
                "pack-1",
                "run-1",
                "agent-1",
                "v1",
                "tenant-1",
                "user-1",
                "demonstrate aggregation",
                4096,
                List.of(
                        item("a", 0.95, 400, ContextItemSourceType.MEMORY),
                        item("b", 0.5, 400, ContextItemSourceType.RAG_CHUNK),
                        item("c", 0.4, 400, ContextItemSourceType.RAG_CHUNK)));

        ContextReductionResult result = reducer.reduce(pack, 600);

        assertThat(result.omittedSummary()).contains("RAG_CHUNK:2");
        assertThat(result.droppedItems()).hasSize(2);
    }

    @Test
    void zeroOrNegativeBudgetIsRejected() {
        ContextPack pack = pack(2048, item("a", 0.5, 100));
        assertThatBudgetIsRejected(pack, 0);
        assertThatBudgetIsRejected(pack, -1);
    }

    private void assertThatBudgetIsRejected(ContextPack pack, int budget) {
        try {
            reducer.reduce(pack, budget);
            assertThat(false).as("expected IllegalArgumentException for budget " + budget).isTrue();
        } catch (IllegalArgumentException expected) {
            // ok
        }
    }

    private static ContextItem item(String id, double score, int tokens) {
        return item(id, score, tokens, ContextItemSourceType.MEMORY);
    }

    private static ContextItem item(String id, double score, int tokens, ContextItemSourceType sourceType) {
        return new ContextItem(
                id,
                "pack-1",
                sourceType,
                "src-" + id,
                "content of " + id,
                null,
                score,
                0.7d,
                null,
                "acl-" + id,
                "{}",
                tokens,
                null,
                FIXED_NOW);
    }

    private static ContextPack pack(int budgetTokens, ContextItem... items) {
        return ContextPack(
                "pack-1",
                "run-1",
                "agent-1",
                "v1",
                "tenant-1",
                "user-1",
                "test reduction",
                budgetTokens,
                List.of(items));
    }

    private static ContextPack ContextPack(String packId,
                                            String runId,
                                            String agentId,
                                            String versionId,
                                            String tenantId,
                                            String userId,
                                            String taskGoal,
                                            int budgetTokens,
                                            List<ContextItem> items) {
        return new ContextPack(packId, runId, agentId, versionId, tenantId, userId, taskGoal,
                budgetTokens, items, FIXED_NOW);
    }

    private static org.assertj.core.groups.Tuple tuple(Object a, Object b) {
        return org.assertj.core.groups.Tuple.tuple(a, b);
    }
}

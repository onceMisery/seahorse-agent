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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextReductionResult.DroppedContextItem;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Slice 1e：Phase D 阶段间上下文降维。
 *
 * <p>策略（pure function、deterministic、不调 LLM）：
 * <ol>
 *     <li>分数 ≥ {@link #mustKeepScoreThreshold} 的 item 强制保留（即便累计超出 budget，也保留并在
 *         omitted summary 中标注 "must-keep over-budget"）。</li>
 *     <li>剩余 item 按 {@code score desc, estimatedTokens asc, itemId asc} 排序后贪心装入剩余预算。</li>
 *     <li>装不下的 item 被舍弃，舍弃原因填 {@link DropReason#BUDGET_EXCEEDED}；
 *         单项 token 已超 budget 时填 {@link DropReason#TOO_LARGE_FOR_BUDGET}。</li>
 *     <li>分数 < {@link #scoreFloor} 的 item 直接舍弃，原因 {@link DropReason#BELOW_SCORE_FLOOR}。</li>
 *     <li>kept 集按原 ContextPack 顺序输出，方便模型读取保持局部性。</li>
 * </ol>
 *
 * <p>omitted summary 形如：{@code "omitted 3 context items (MEMORY:2, KB:1) totaling 1234 tokens"}，
 * 由调用方注入到 model system prompt 或运行 trace，告诉模型"上下文已被压缩"。
 */
public final class ContextReducer {

    public static final double DEFAULT_MUST_KEEP_SCORE_THRESHOLD = 0.9d;
    public static final double DEFAULT_SCORE_FLOOR = 0.0d;

    private final double mustKeepScoreThreshold;
    private final double scoreFloor;

    public ContextReducer() {
        this(DEFAULT_MUST_KEEP_SCORE_THRESHOLD, DEFAULT_SCORE_FLOOR);
    }

    public ContextReducer(double mustKeepScoreThreshold, double scoreFloor) {
        if (mustKeepScoreThreshold < 0.0d || mustKeepScoreThreshold > 1.0d) {
            throw new IllegalArgumentException("mustKeepScoreThreshold must be in [0,1]");
        }
        if (scoreFloor < 0.0d || scoreFloor > 1.0d) {
            throw new IllegalArgumentException("scoreFloor must be in [0,1]");
        }
        this.mustKeepScoreThreshold = mustKeepScoreThreshold;
        this.scoreFloor = scoreFloor;
    }

    /**
     * 对 ContextPack 进行降维。
     *
     * @param pack            上下文包；不可为 null
     * @param budgetTokens    阶段 token 预算；必须 &gt; 0
     */
    public ContextReductionResult reduce(ContextPack pack, int budgetTokens) {
        Objects.requireNonNull(pack, "pack must not be null");
        if (budgetTokens <= 0) {
            throw new IllegalArgumentException("budgetTokens must be > 0");
        }
        List<ContextItem> items = pack.items();
        List<ContextItem> kept = new ArrayList<>();
        List<DroppedContextItem> dropped = new ArrayList<>();
        Set<String> keptIds = new HashSet<>();
        int keptTokens = 0;

        for (ContextItem item : items) {
            if (item.score() < scoreFloor) {
                dropped.add(new DroppedContextItem(item, DropReason.BELOW_SCORE_FLOOR));
            } else if (item.score() >= mustKeepScoreThreshold) {
                kept.add(item);
                keptIds.add(item.itemId());
                keptTokens += item.estimatedTokens();
            }
        }

        List<ContextItem> candidates = new ArrayList<>();
        for (ContextItem item : items) {
            if (keptIds.contains(item.itemId())) {
                continue;
            }
            if (item.score() < scoreFloor) {
                continue;
            }
            candidates.add(item);
        }
        candidates.sort(Comparator.<ContextItem>comparingDouble(ContextItem::score).reversed()
                .thenComparingInt(ContextItem::estimatedTokens)
                .thenComparing(ContextItem::itemId));

        for (ContextItem item : candidates) {
            if (item.estimatedTokens() > budgetTokens) {
                dropped.add(new DroppedContextItem(item, DropReason.TOO_LARGE_FOR_BUDGET));
                continue;
            }
            if (keptTokens + item.estimatedTokens() <= budgetTokens) {
                kept.add(item);
                keptIds.add(item.itemId());
                keptTokens += item.estimatedTokens();
            } else {
                dropped.add(new DroppedContextItem(item, DropReason.BUDGET_EXCEEDED));
            }
        }

        List<ContextItem> orderedKept = new ArrayList<>();
        for (ContextItem item : items) {
            if (keptIds.contains(item.itemId())) {
                orderedKept.add(item);
            }
        }

        String summary = buildOmittedSummary(dropped, kept.size(), keptTokens, budgetTokens);
        return new ContextReductionResult(orderedKept, dropped, summary, keptTokens, budgetTokens);
    }

    private String buildOmittedSummary(List<DroppedContextItem> dropped,
                                        int keptCount,
                                        int keptTokens,
                                        int budgetTokens) {
        if (dropped.isEmpty()) {
            return "context fits within budget: kept " + keptCount + " items (" + keptTokens + " / "
                    + budgetTokens + " tokens)";
        }
        int droppedTokens = 0;
        Map<ContextItemSourceType, Integer> bySource = new EnumMap<>(ContextItemSourceType.class);
        for (DroppedContextItem entry : dropped) {
            droppedTokens += entry.item().estimatedTokens();
            bySource.merge(entry.item().sourceType(), 1, Integer::sum);
        }
        StringBuilder builder = new StringBuilder();
        builder.append("omitted ")
                .append(dropped.size())
                .append(" context items (");
        boolean first = true;
        for (Map.Entry<ContextItemSourceType, Integer> entry : bySource.entrySet()) {
            if (!first) {
                builder.append(", ");
            }
            builder.append(entry.getKey()).append(":").append(entry.getValue());
            first = false;
        }
        builder.append(") totaling ")
                .append(droppedTokens)
                .append(" tokens; kept ")
                .append(keptCount)
                .append(" items (")
                .append(keptTokens)
                .append(" / ")
                .append(budgetTokens)
                .append(" tokens)");
        return builder.toString();
    }
}

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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.context;

import java.util.List;
import java.util.Objects;

/**
 * Slice 1e：ContextReducer 输出结果。
 *
 * <p>用于阶段间上下文降维：当 {@link ContextPack} 总 token 超出阶段 budget 时，
 * 部分 {@link ContextItem} 会被舍弃。本对象同时保留：
 * <ul>
 *     <li>{@link #keptItems()} 进入下一阶段的 items（保持原顺序）。</li>
 *     <li>{@link #droppedItems()} 被舍弃的 items（按 drop 顺序）。</li>
 *     <li>{@link #omittedSummary()} 人类可读摘要，用于 trace / 日志 / 模型 system prompt 提示。</li>
 *     <li>{@link #keptTokens()} 实际保留 token 数。</li>
 *     <li>{@link #budgetTokens()} 输入约束。</li>
 * </ul>
 */
public record ContextReductionResult(List<ContextItem> keptItems,
                                     List<DroppedContextItem> droppedItems,
                                     String omittedSummary,
                                     int keptTokens,
                                     int budgetTokens) {

    public ContextReductionResult {
        keptItems = keptItems == null ? List.of() : List.copyOf(keptItems);
        droppedItems = droppedItems == null ? List.of() : List.copyOf(droppedItems);
        omittedSummary = Objects.requireNonNullElse(omittedSummary, "");
        if (keptTokens < 0) {
            throw new IllegalArgumentException("keptTokens must be >= 0");
        }
        if (budgetTokens <= 0) {
            throw new IllegalArgumentException("budgetTokens must be > 0");
        }
    }

    public boolean reduced() {
        return !droppedItems.isEmpty();
    }

    /**
     * 单个被舍弃 item 的轻量记录，含舍弃原因。
     */
    public record DroppedContextItem(ContextItem item, DropReason reason) {

        public DroppedContextItem {
            Objects.requireNonNull(item, "item must not be null");
            Objects.requireNonNull(reason, "reason must not be null");
        }
    }

    /**
     * 舍弃原因枚举。
     */
    public enum DropReason {
        /** 已超出 token 预算。 */
        BUDGET_EXCEEDED,
        /** 单项 token 超过预算，无法装入。 */
        TOO_LARGE_FOR_BUDGET,
        /** 分数低于保留下限。 */
        BELOW_SCORE_FLOOR
    }
}

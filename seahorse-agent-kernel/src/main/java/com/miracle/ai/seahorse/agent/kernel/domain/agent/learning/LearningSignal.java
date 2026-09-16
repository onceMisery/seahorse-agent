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

package com.miracle.ai.seahorse.agent.kernel.domain.agent.learning;

import java.util.List;
import java.util.Objects;

/**
 * 自学习闭环在一个"目标"上累积到的不认可证据。
 *
 * <p>两类信号被折算成同一件事——"这条记忆/这类输出没有被认可"：
 * 用户的点踩（{@code negativeVotes}）与人工复核的拒绝（{@code rejectedReviews}）。
 * 它们量纲不同但方向一致，合起来才够样本量；单看一类在灰度期几乎没有统计意义。
 *
 * @param targetKey  信号归属的目标（记忆 targetKey、反馈 reason，或全局聚合的哨兵值）
 * @param targetKind 目标类型（记忆层/类别，或反馈）
 * @param evidenceIds 支撑该信号的原始记录 id，便于人工复核时回溯
 */
public record LearningSignal(String targetKey,
                             String targetKind,
                             int positiveVotes,
                             int negativeVotes,
                             int approvedReviews,
                             int rejectedReviews,
                             List<String> evidenceIds) {

    public static final String GLOBAL_TARGET = "*";

    public LearningSignal {
        targetKey = Objects.requireNonNullElse(targetKey, "").trim();
        targetKind = Objects.requireNonNullElse(targetKind, "").trim();
        evidenceIds = List.copyOf(Objects.requireNonNullElse(evidenceIds, List.of()));
    }

    public int sampleCount() {
        return positiveVotes + negativeVotes + approvedReviews + rejectedReviews;
    }

    public int disapprovalCount() {
        return negativeVotes + rejectedReviews;
    }

    /**
     * @return 不认可占比；无样本时为 0，调用方必须先检查 {@link #actionable(int)}
     */
    public double disapprovalRate() {
        int total = sampleCount();
        return total == 0 ? 0.0D : (double) disapprovalCount() / total;
    }

    /**
     * @return 样本量是否足以支撑结论。样本不足时宁可不给建议，也不给噪声建议。
     */
    public boolean actionable(int minSamples) {
        return !targetKey.isBlank() && sampleCount() >= minSamples;
    }
}

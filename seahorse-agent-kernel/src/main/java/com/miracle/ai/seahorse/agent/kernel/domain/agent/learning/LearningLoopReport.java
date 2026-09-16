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

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 一次自学习闭环运行的产出。
 *
 * <p>结构是刻意"看得见证据"的：每个建议都带着触发它的信号、样本量和原始记录 id。
 * 运维在决定是否落地之前，应该能顺着 {@code evidenceIds} 回到原始点踩与复核记录。
 *
 * @param generatedAt   生成时间
 * @param signals       按目标聚合出的信号（含全局聚合）
 * @param proposals     排序后的建议，最多 {@code maxProposals} 条
 * @param previousOutcome 上一次采纳建议的验证结论
 * @param summary        一句话摘要，用于日志与看板
 */
public record LearningLoopReport(Instant generatedAt,
                                 List<LearningSignal> signals,
                                 List<PolicyAdjustmentProposal> proposals,
                                 PreviousProposalOutcome previousOutcome,
                                 String summary) {

    public LearningLoopReport {
        generatedAt = Objects.requireNonNullElseGet(generatedAt, Instant::now);
        signals = List.copyOf(Objects.requireNonNullElse(signals, List.of()));
        proposals = List.copyOf(Objects.requireNonNullElse(proposals, List.of()));
        previousOutcome = Objects.requireNonNullElseGet(previousOutcome, PreviousProposalOutcome::noProposal);
        summary = Objects.requireNonNullElse(summary, "");
    }

    public boolean hasProposals() {
        return !proposals.isEmpty();
    }

    public boolean regressed() {
        return previousOutcome.status() == PreviousProposalOutcome.Status.REGRESSED;
    }

    /**
     * 可观测性用的紧凑 JSON。只输出规模可控的结构信息，不回灌原始内容。
     */
    public String toJson() {
        return "{\"generatedAtEpochMillis\":" + generatedAt.toEpochMilli()
                + ",\"signalCount\":" + signals.size()
                + ",\"proposalCount\":" + proposals.size()
                + ",\"previousOutcome\":\"" + previousOutcome.status().name()
                + "\",\"summary\":\"" + escape(summary) + "\"}";
    }

    private static String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}

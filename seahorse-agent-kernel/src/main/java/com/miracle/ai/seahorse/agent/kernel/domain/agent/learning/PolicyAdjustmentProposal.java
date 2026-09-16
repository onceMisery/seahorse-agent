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
import java.util.Locale;
import java.util.Objects;

/**
 * 一条策略调整建议。
 *
 * <p><b>只是建议，不自我执行。</b>让 Agent 无人值守地改生产配置是这条路上最危险的捷径：
 * 阈值一改，记忆写入/复核的流量分布立刻变化，而反馈信号要几天才追得上。
 * 因此这里只产出"改哪个键、往哪改、改多少、凭什么"，由人决定落不落地。
 *
 * @param targetKey  作用目标；全局阈值为 {@link LearningSignal#GLOBAL_TARGET}
 * @param policyKey  配置键，与 {@code MemoryPolicyConfig} 的字段一一对应
 * @param direction  调整方向
 * @param suggestedDelta 建议的变化量（相对值，正负由 direction 决定）
 * @param disapprovalRate 触发该建议的不认可占比
 * @param sampleCount 支撑样本量
 * @param rationale  人话理由，直接进运营看板
 * @param evidenceIds 回溯用的原始记录 id
 */
public record PolicyAdjustmentProposal(String targetKey,
                                       String policyKey,
                                       Direction direction,
                                       double suggestedDelta,
                                       double disapprovalRate,
                                       int sampleCount,
                                       String rationale,
                                       List<String> evidenceIds) {

    /** 与 {@code MemoryPolicyConfig.refinerAutoCommitConfidenceThreshold} 对应。 */
    public static final String POLICY_AUTO_COMMIT_CONFIDENCE =
            "memory.refiner.autoCommitConfidenceThreshold";
    /** 与 {@code MemoryPolicyConfig.captureAcceptThreshold} 对应。 */
    public static final String POLICY_CAPTURE_ACCEPT_THRESHOLD =
            "memory.captureAcceptThreshold";

    public enum Direction {
        /** 提高阈值：更少内容自动放行，把边界样本送人工复核。 */
        TIGHTEN,
        /** 降低阈值：人工复核已经长期"全通过"时，把人力释放出来。 */
        LOOSEN,
        /** 隔离具体目标：某条记忆持续被拒绝且被点踩，先停止它参与召回。 */
        QUARANTINE_TARGET
    }

    public PolicyAdjustmentProposal {
        targetKey = Objects.requireNonNullElse(targetKey, "").trim();
        policyKey = Objects.requireNonNullElse(policyKey, "").trim();
        direction = Objects.requireNonNull(direction, "direction must not be null");
        rationale = Objects.requireNonNullElse(rationale, "");
        evidenceIds = List.copyOf(Objects.requireNonNullElse(evidenceIds, List.of()));
    }

    public static PolicyAdjustmentProposal quarantine(LearningSignal signal, double threshold) {
        return new PolicyAdjustmentProposal(
                signal.targetKey(),
                signal.targetKey(),
                Direction.QUARANTINE_TARGET,
                0.0D,
                signal.disapprovalRate(),
                signal.sampleCount(),
                "目标 " + signal.targetKey() + " 的不认可率 " + percent(signal.disapprovalRate())
                        + " 超过阈值 " + percent(threshold) + "（" + signal.sampleCount() + " 条样本），建议先隔离再复核。",
                signal.evidenceIds());
    }

    public static PolicyAdjustmentProposal tighten(LearningSignal signal, String policyKey, double step) {
        return new PolicyAdjustmentProposal(
                signal.targetKey(),
                policyKey,
                Direction.TIGHTEN,
                Math.abs(step),
                signal.disapprovalRate(),
                signal.sampleCount(),
                "整体不认可率 " + percent(signal.disapprovalRate()) + "（" + signal.sampleCount()
                        + " 条样本）偏高，建议把 " + policyKey + " 上调 " + percent(Math.abs(step)) + "。",
                signal.evidenceIds());
    }

    public static PolicyAdjustmentProposal loosen(LearningSignal signal, String policyKey, double step) {
        return new PolicyAdjustmentProposal(
                signal.targetKey(),
                policyKey,
                Direction.LOOSEN,
                -Math.abs(step),
                signal.disapprovalRate(),
                signal.sampleCount(),
                "整体不认可率仅 " + percent(signal.disapprovalRate()) + "（" + signal.sampleCount()
                        + " 条样本），复核人力可能空转，建议把 " + policyKey + " 下调 " + percent(Math.abs(step)) + "。",
                signal.evidenceIds());
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100);
    }
}

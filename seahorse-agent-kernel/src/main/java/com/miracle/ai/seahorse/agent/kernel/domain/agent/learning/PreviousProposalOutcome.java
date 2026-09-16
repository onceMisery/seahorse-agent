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

import java.util.Locale;
import java.util.Objects;

/**
 * 上一次采纳的建议到底有没有用——闭环的"验证"环节。
 *
 * <p>没有这一步，所谓自学习就只是"不断产生新建议"；有了它，每一次迭代都会用评测通过率
 * 回访上一次的改动，把"猜"变成"测得出来"。
 *
 * @param status 结论
 * @param detail 人话说明，含实际变化量
 */
public record PreviousProposalOutcome(Status status, String detail) {

    public enum Status {
        /** 本次之前没有采纳过任何建议。 */
        NO_PROPOSAL,
        /** 有建议但还没有可比的评测数据（缺基线或缺当前值）。 */
        AWAITING_MEASUREMENT,
        /** 通过率提升超过判定阈值。 */
        VERIFIED_IMPROVED,
        /** 通过率下降超过判定阈值——应当回滚。 */
        REGRESSED,
        /** 变化在噪声范围内，本次不足以判定。 */
        INCONCLUSIVE
    }

    public PreviousProposalOutcome {
        status = Objects.requireNonNull(status, "status must not be null");
        detail = Objects.requireNonNullElse(detail, "");
    }

    public static PreviousProposalOutcome noProposal() {
        return new PreviousProposalOutcome(Status.NO_PROPOSAL, "此前没有采纳过建议。");
    }

    public static PreviousProposalOutcome awaitingMeasurement() {
        return new PreviousProposalOutcome(Status.AWAITING_MEASUREMENT,
                "缺少基线或当前评测通过率，无法判定上一次改动的效果。");
    }

    public static PreviousProposalOutcome of(double delta, double threshold) {
        if (delta >= threshold) {
            return new PreviousProposalOutcome(Status.VERIFIED_IMPROVED,
                    "上一次改动后通过率提升 " + percent(delta) + "，建议保持。");
        }
        if (delta <= -threshold) {
            return new PreviousProposalOutcome(Status.REGRESSED,
                    "上一次改动后通过率下降 " + percent(Math.abs(delta)) + "，建议回滚。");
        }
        return new PreviousProposalOutcome(Status.INCONCLUSIVE,
                "上一次改动后通过率变化 " + percent(delta) + "，在判定阈值 ±" + percent(threshold) + " 之内，继续观察。");
    }

    private static String percent(double ratio) {
        return String.format(Locale.ROOT, "%.1f%%", ratio * 100);
    }
}

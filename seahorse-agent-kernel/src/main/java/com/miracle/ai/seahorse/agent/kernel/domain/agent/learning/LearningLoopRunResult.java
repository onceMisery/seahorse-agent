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
 * 一次"跑闭环并开工单"的结果。
 *
 * <p>报告与工单一并返回，是因为只有工单的话调用方无法解释"为什么这次一条都没生成"——
 * 是本来就没信号，还是信号不够、被阈值挡掉了。报告里的 {@code summary} 与
 * {@code signals} 就是给这个场景看的。
 *
 * @param report  本次闭环报告，永远不为 null（数据源缺失时是空报告）
 * @param tickets 本次新建的工单；因指纹重复而未新建的不包含在内
 * @param review  本次顺带执行的回访结果；未执行回访时为 null
 */
public record LearningLoopRunResult(LearningLoopReport report,
                                    List<PolicyAdjustmentTicket> tickets,
                                    PolicyAdjustmentTicketReviewRunResult review) {

    public LearningLoopRunResult(LearningLoopReport report, List<PolicyAdjustmentTicket> tickets) {
        this(report, tickets, null);
    }

    public LearningLoopRunResult {
        report = Objects.requireNonNullElseGet(report,
                () -> new LearningLoopReport(null, List.of(), List.of(), null, ""));
        tickets = List.copyOf(Objects.requireNonNullElse(tickets, List.of()));
    }

    public int createdCount() {
        return tickets.size();
    }

    /** 跑出了信号但没开出工单，通常意味着这些建议上一轮已经开过了。 */
    public boolean signalWithoutNewTicket() {
        return !report.signals().isEmpty() && tickets.isEmpty();
    }
}

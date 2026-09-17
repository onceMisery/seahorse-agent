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
import java.util.Objects;

/**
 * 一条"上一次改动到底有没有用"的回访记录。
 *
 * <p>此前闭环的回访只活在 {@link LearningLoopReport#previousOutcome()} 里——算完就丢，
 * 下一次运行无法知道"这张工单上次回访的结论是什么"。工单落到 APPLIED 之后就停在那里，
 * 闭环事实上是断的：有建议、有落地、没有人回头看落地的效果。
 *
 * <p>这里是补上的那一环：每次回访对每张处于 {@link PolicyAdjustmentTicketStatus#APPLIED}
 * 的工单留一条记录，可被下一次回访读到（用于冷却期判断），也能被前端直接展示。
 *
 * <p><b>刻意不自动改工单状态。</b>判定为 {@link PreviousProposalOutcome.Status#REGRESSED}
 * 时本记录打上 {@link #rollbackRecommended()}，但工单仍然停在 APPLIED——
 * 系统并没有真的还原任何配置，把它自动迁到 ROLLED_BACK 等于让工单撒谎。
 * 由人在配置侧还原后再点回滚。
 *
 * @param batchId 回访批次，与 ticketId 一起构成幂等键
 */
public record PolicyAdjustmentTicketReview(String reviewId,
                                           String ticketId,
                                           String tenantId,
                                           String batchId,
                                           PreviousProposalOutcome.Status outcome,
                                           String detail,
                                           Double baselinePassRate,
                                           Double currentPassRate,
                                           String source,
                                           Instant reviewedAt) {

    public PolicyAdjustmentTicketReview {
        reviewId = Objects.requireNonNullElse(reviewId, "").trim();
        ticketId = Objects.requireNonNullElse(ticketId, "").trim();
        tenantId = Objects.requireNonNullElse(tenantId, "").trim();
        batchId = Objects.requireNonNullElse(batchId, "").trim();
        outcome = Objects.requireNonNull(outcome, "outcome must not be null");
        detail = Objects.requireNonNullElse(detail, "");
        source = Objects.requireNonNullElse(source, "");
        reviewedAt = Objects.requireNonNullElseGet(reviewedAt, Instant::now);
    }

    public static PolicyAdjustmentTicketReview of(String reviewId,
                                                  PolicyAdjustmentTicket ticket,
                                                  String batchId,
                                                  ReviewMeasurement measurement,
                                                  PreviousProposalOutcome outcome,
                                                  Instant reviewedAt) {
        Objects.requireNonNull(ticket, "ticket must not be null");
        Objects.requireNonNull(measurement, "measurement must not be null");
        return new PolicyAdjustmentTicketReview(
                reviewId,
                ticket.ticketId(),
                ticket.tenantId(),
                batchId,
                outcome.status(),
                outcome.detail(),
                measurement.baselinePassRate(),
                measurement.currentPassRate(),
                measurement.source(),
                reviewedAt);
    }

    /** 判定为回归——建议在配置侧还原，由人工确认后走回滚。 */
    public boolean rollbackRecommended() {
        return outcome == PreviousProposalOutcome.Status.REGRESSED;
    }
}

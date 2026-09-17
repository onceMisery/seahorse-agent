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
 * 一次回访批次的执行结果。
 *
 * <p>只回一个 {@code List} 会让"跑了但一条都没写"看起来和"没跑"一模一样。
 * 触发回访的入口（手动按钮、定时任务、闭环顺带）都需要能解释
 * "这次为什么没有回访记录"，所以把扫描量与跳过量一并带出来。
 *
 * @param reviews 本次实际落库的回访记录
 * @param scanned 扫描到的 APPLIED 工单数
 * @param skipped 因冷却期或未接数据源而跳过的工单数
 * @param note    人话说明，尤其是"为什么一条都没写"
 */
public record PolicyAdjustmentTicketReviewRunResult(List<PolicyAdjustmentTicketReview> reviews,
                                                    int scanned,
                                                    int skipped,
                                                    String note) {

    public PolicyAdjustmentTicketReviewRunResult {
        reviews = List.copyOf(Objects.requireNonNullElse(reviews, List.of()));
        note = Objects.requireNonNullElse(note, "");
    }

    public static PolicyAdjustmentTicketReviewRunResult empty(String note) {
        return new PolicyAdjustmentTicketReviewRunResult(List.of(), 0, 0, note);
    }

    public static PolicyAdjustmentTicketReviewRunResult skipped(int scanned, int skipped, String note) {
        return new PolicyAdjustmentTicketReviewRunResult(List.of(), scanned, skipped, note);
    }

    /** 有没有任何一张工单被判定为"应当回滚"。 */
    public boolean hasRollbackRecommendation() {
        return reviews.stream().anyMatch(PolicyAdjustmentTicketReview::rollbackRecommended);
    }
}

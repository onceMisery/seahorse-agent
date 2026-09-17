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
 * 批量审批的结果。
 *
 * <p>批量操作天然会<b>部分失败</b>：工单数在"前端勾选"与"后端执行"之间可能已经被别人
 * 审批掉（乐观更新的 409）、也可能处在不可迁移的状态。整批回滚会让前面成功的白做，
 * 整批失败则让一次误操作毁掉整批；因此逐条执行、成功与失败都返回。
 *
 * <p>失败原因给的是人话而非异常堆栈：这是直接展示给运营看的文本。
 */
public record PolicyAdjustmentTicketBatchDecisionResult(PolicyAdjustmentTicketStatus targetStatus,
                                                        List<String> succeededTicketIds,
                                                        List<Failure> failed) {

    public PolicyAdjustmentTicketBatchDecisionResult {
        targetStatus = Objects.requireNonNull(targetStatus, "targetStatus must not be null");
        succeededTicketIds = List.copyOf(Objects.requireNonNullElse(succeededTicketIds, List.of()));
        failed = List.copyOf(Objects.requireNonNullElse(failed, List.of()));
    }

    public record Failure(String ticketId, String reason) {
        public Failure {
            ticketId = Objects.requireNonNullElse(ticketId, "").trim();
            reason = Objects.requireNonNullElse(reason, "").trim();
        }
    }

    public int total() {
        return succeededTicketIds.size() + failed.size();
    }

    public boolean allSucceeded() {
        return failed.isEmpty();
    }

    public boolean allFailed() {
        return succeededTicketIds.isEmpty() && !failed.isEmpty();
    }

    /** 给前端 toast 用的一行摘要，刻意区分全成、全败、部分成功三种语气。 */
    public String summary() {
        if (allSucceeded()) {
            return String.format(Locale.ROOT, "已%s %d 张工单", verb(), succeededTicketIds.size());
        }
        if (allFailed()) {
            return String.format(Locale.ROOT, "%d 张工单%s失败，请逐张查看原因", failed.size(), verb());
        }
        return String.format(Locale.ROOT, "%s：成功 %d 张、失败 %d 张",
                verb(), succeededTicketIds.size(), failed.size());
    }

    private String verb() {
        return switch (targetStatus) {
            case APPROVED -> "通过";
            case REJECTED -> "驳回";
            case APPLIED -> "标记为已落地";
            case ROLLED_BACK -> "回滚";
            case PENDING -> "退回待审批";
        };
    }
}

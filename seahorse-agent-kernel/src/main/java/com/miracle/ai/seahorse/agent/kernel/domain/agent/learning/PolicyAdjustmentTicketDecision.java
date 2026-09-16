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

import java.util.Objects;

/**
 * 对落地工单的一次状态迁移请求。
 *
 * @param targetStatus 目标状态；合法性由 {@link PolicyAdjustmentTicketStatus#canTransitionTo} 判定
 * @param operator     操作人，进入审计记录
 * @param comment      决策说明；拒绝与回滚时建议必填，否则工单只剩一个结论没有理由
 */
public record PolicyAdjustmentTicketDecision(String ticketId,
                                             PolicyAdjustmentTicketStatus targetStatus,
                                             String operator,
                                             String comment) {

    public PolicyAdjustmentTicketDecision {
        ticketId = Objects.requireNonNullElse(ticketId, "").trim();
        targetStatus = Objects.requireNonNull(targetStatus, "targetStatus must not be null");
        operator = Objects.requireNonNullElse(operator, "").trim();
        comment = Objects.requireNonNullElse(comment, "").trim();
    }
}

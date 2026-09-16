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

/**
 * 落地工单的状态机。
 *
 * <pre>
 *   PENDING ──approve──▶ APPROVED ──apply──▶ APPLIED ──rollBack──▶ ROLLED_BACK
 *      │                    │
 *      └────reject──────▶ REJECTED
 * </pre>
 *
 * <p>刻意没有 {@code APPROVED → APPLIED} 的自动通道：本服务不改配置，
 * 只记录"人确认已经在配置侧落地了"。把落地动作自动化，等于让一个靠几天延迟的反馈信号
 * 驱动的闭环去直接改生产流量——那会震荡。
 */
public enum PolicyAdjustmentTicketStatus {

    /** 已生成，等待人工决定是否采纳。 */
    PENDING,
    /** 采纳，等待在配置侧落地。 */
    APPROVED,
    /** 不采纳；终态。 */
    REJECTED,
    /** 已在配置侧落地；闭环下一轮会用评测通过率回访。 */
    APPLIED,
    /** 落地后判定为回归，已回滚；终态。 */
    ROLLED_BACK;

    public boolean terminal() {
        return this == REJECTED || this == ROLLED_BACK;
    }

    /**
     * @return 该迁移是否被允许；调用方据此给出明确错误，而不是让非法状态落库
     */
    public boolean canTransitionTo(PolicyAdjustmentTicketStatus target) {
        if (target == null || target == this) {
            return false;
        }
        return switch (this) {
            case PENDING -> target == APPROVED || target == REJECTED;
            case APPROVED -> target == APPLIED || target == REJECTED;
            case APPLIED -> target == ROLLED_BACK;
            case REJECTED, ROLLED_BACK -> false;
        };
    }
}

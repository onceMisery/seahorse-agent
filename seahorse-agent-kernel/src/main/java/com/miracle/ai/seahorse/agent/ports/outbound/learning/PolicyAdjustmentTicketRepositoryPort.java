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

package com.miracle.ai.seahorse.agent.ports.outbound.learning;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;

/**
 * 落地工单的写入端口。
 */
public interface PolicyAdjustmentTicketRepositoryPort {

    /** 新建或整体覆盖一条工单。 */
    void save(PolicyAdjustmentTicket ticket);

    /**
     * 按期望的当前状态乐观更新；状态已被别人改走时返回 false。
     *
     * <p>工单是多人可审批的对象，用 compare-and-set 而不是"先读后写"，
     * 否则两个审批人同时点下去会有一个人的决策被静默覆盖。
     */
    boolean updateIfStatus(String ticketId,
                           PolicyAdjustmentTicketStatus expectedStatus,
                           PolicyAdjustmentTicket ticket);

    /**
     * 未配置持久化实现时的空实现：写入被静默丢弃，查询一律返回空。
     * 闭环的产出本来就允许不落库（它同时也能直接进日志与看板），
     * 因此这里选择降级而不是让整个装配失败。
     */
    static PolicyAdjustmentTicketRepositoryPort empty() {
        return new PolicyAdjustmentTicketRepositoryPort() {
            @Override
            public void save(PolicyAdjustmentTicket ticket) {
                // no-op
            }

            @Override
            public boolean updateIfStatus(String ticketId,
                                          PolicyAdjustmentTicketStatus expectedStatus,
                                          PolicyAdjustmentTicket ticket) {
                return false;
            }
        };
    }
}

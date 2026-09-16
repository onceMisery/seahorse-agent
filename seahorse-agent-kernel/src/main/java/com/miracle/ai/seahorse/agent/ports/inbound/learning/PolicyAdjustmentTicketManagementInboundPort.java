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

package com.miracle.ai.seahorse.agent.ports.inbound.learning;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketPage;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQuery;

import java.util.List;
import java.util.Optional;

/**
 * 落地工单的管理入站端口：把闭环的建议变成可追责、可审批、可回访的工单。
 */
public interface PolicyAdjustmentTicketManagementInboundPort {

    /**
     * 把一次闭环报告里的建议转成待审批工单。
     *
     * <p>同一个建议（指纹相同）只会开一次工单——闭环每轮都会重算，
     * 不去重的话待办列表会被同一条建议刷满，反而没人看了。
     *
     * @return 本次新建的工单；因重复或报告无建议而跳过时返回空列表
     */
    List<PolicyAdjustmentTicket> submitProposals(LearningLoopReport report,
                                                 String tenantId,
                                                 String createdBy);

    PolicyAdjustmentTicketPage page(String tenantId, PolicyAdjustmentTicketStatus status, long current, long size);

    Optional<PolicyAdjustmentTicket> findById(String ticketId);

    /**
     * 对工单做一次状态迁移；非法迁移抛 {@link IllegalArgumentException}。
     */
    PolicyAdjustmentTicket decide(PolicyAdjustmentTicketDecision decision);

    /**
     * 跑一次自学习闭环并把产出的建议转成工单。
     *
     * <p>把它放在工单端口而不是单独开一个"闭环端口"，是因为对调用方而言这就是
     * "生成工单"这一个动作：闭环只是取样的来源，不是另一个需要被管理的对象。
     *
     * @param tenantId    租户；复核侧按此过滤，反馈侧暂无该维度
     * @param createdBy   申请人，进审计记录
     * @param sampleLimit 单侧取样上限
     * @return 本次闭环报告与新建工单；数据源未配置时报告为空且无新建工单，不抛异常
     */
    LearningLoopRunResult runAndGenerateTickets(String tenantId, String createdBy, int sampleLimit);

    /**
     * 只查待审批工单的便捷入口，给运营看板的"待我处理"用。
     */
    default List<PolicyAdjustmentTicket> listPending(String tenantId) {
        PolicyAdjustmentTicketPage pending = page(tenantId, PolicyAdjustmentTicketStatus.PENDING,
                PolicyAdjustmentTicketQuery.DEFAULT_CURRENT, PolicyAdjustmentTicketQuery.DEFAULT_PAGE_SIZE);
        return pending.records();
    }
}

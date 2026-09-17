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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketBatchDecisionResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReview;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketReviewRunResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.ReviewMeasurement;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketPage;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQuery;

import java.util.List;
import java.util.Map;
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
     * 跑闭环并顺带回访已落地工单（事件驱动，默认的回访触发方式）。
     *
     * @param measurement 本次评测测量结果；不可测量时回访会被跳过，工单生成不受影响
     */
    default LearningLoopRunResult runAndGenerateTickets(String tenantId,
                                                        String createdBy,
                                                        int sampleLimit,
                                                        ReviewMeasurement measurement) {
        return runAndGenerateTickets(tenantId, createdBy, sampleLimit);
    }

    /**
     * 回访所有处于 {@link PolicyAdjustmentTicketStatus#APPLIED} 的工单。
     *
     * <p>幂等由三道防线保证，缺一不可：
     * <ol>
     *   <li><b>冷却期</b>（{@code LearningLoopPolicy#reviewCooldown()}）：距上次回访太近的工单直接跳过。
     *       反馈信号要好几天才追得上一次配置调整，而"运行闭环"可以被人连点。</li>
     *   <li><b>批次去重</b>：{@code (ticketId, batchId)} 唯一索引，重复批次在数据库层被忽略。</li>
     *   <li><b>不可测量就不落库</b>：缺基线或缺当前通过率时不写记录，避免每轮都落一条
     *       内容完全相同的 {@code AWAITING_MEASUREMENT}。</li>
     * </ol>
     *
     * <p>注意：<b>回访不会改变工单状态</b>。判定为回归时记录打上建议回滚标记，
     * 由人在配置侧还原后再点回滚——系统没有真的还原任何配置，自动迁移只会让工单撒谎。
     *
     * @param tenantId    租户
     * @param batchId     回访批次；同一批次重复提交不会产生第二条记录
     * @param measurement 评测测量结果
     */
    default PolicyAdjustmentTicketReviewRunResult reviewAppliedTickets(String tenantId,
                                                                       String batchId,
                                                                       ReviewMeasurement measurement) {
        return PolicyAdjustmentTicketReviewRunResult.empty("回访能力未装配");
    }

    /** 某张工单的回访历史，按时间倒序。 */
    default List<PolicyAdjustmentTicketReview> listReviews(String ticketId, int limit) {
        return List.of();
    }

    /**
     * 批量取"每张工单最近一次回访"，给列表页用——逐张查会变成 N+1。
     *
     * @return ticketId → 最近一次回访；从未回访过的工单不在 map 里
     */
    default Map<String, PolicyAdjustmentTicketReview> latestReviews(List<String> ticketIds) {
        return Map.of();
    }

    /**
     * 批量审批。
     *
     * <p>逐条执行并分别收集成功与失败，不做整批事务——部分失败时前端需要知道
     * 是哪几张失败了、为什么失败，而不是看到一个"整批失败"然后重做一遍。
     *
     * @param ticketIds 工单 id 列表，去重后按顺序执行
     * @param target    目标状态
     * @return 成功与失败明细；不会因单张失败而抛异常
     */
    default PolicyAdjustmentTicketBatchDecisionResult decideBatch(List<String> ticketIds,
                                                                  PolicyAdjustmentTicketStatus target,
                                                                  String operator,
                                                                  String comment) {
        return new PolicyAdjustmentTicketBatchDecisionResult(target, List.of(), List.of());
    }

    /**
     * 只查待审批工单的便捷入口，给运营看板的"待我处理"用。
     */
    default List<PolicyAdjustmentTicket> listPending(String tenantId) {
        PolicyAdjustmentTicketPage pending = page(tenantId, PolicyAdjustmentTicketStatus.PENDING,
                PolicyAdjustmentTicketQuery.DEFAULT_CURRENT, PolicyAdjustmentTicketQuery.DEFAULT_PAGE_SIZE);
        return pending.records();
    }
}

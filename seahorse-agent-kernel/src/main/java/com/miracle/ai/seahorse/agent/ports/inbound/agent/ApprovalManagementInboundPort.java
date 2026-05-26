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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ApprovalRequestPage;

import java.util.List;
import java.util.Optional;

/**
 * 人工审批管理入站端口，供管理端查询和决策审批请求。
 */
public interface ApprovalManagementInboundPort {

    /**
     * 分页查询审批请求；未指定状态时默认查询 PENDING。
     */
    ApprovalRequestPage page(String tenantId, ApprovalRequestStatus status, long current, long size);

    /**
     * 查询当前用户可处理的 run 内待确认请求，用于 C 端聊天内确认卡片。
     */
    List<ApprovalRequest> listPendingByRunId(String runId);

    /**
     * 查看审批详情。
     */
    Optional<ApprovalRequest> findById(String approvalId);

    /**
     * 通过待审批请求。真实工具执行必须由后续 checkpoint resume 负责。
     */
    ApprovalRequest approve(String approvalId, ApprovalDecisionCommand command);

    /**
     * 拒绝待审批请求。
     */
    ApprovalRequest reject(String approvalId, ApprovalDecisionCommand command);

    /**
     * 修改参数预览后通过待审批请求。真实参数快照由后续 checkpoint 切片负责。
     */
    ApprovalRequest modify(String approvalId, ApprovalModifyCommand command);
}


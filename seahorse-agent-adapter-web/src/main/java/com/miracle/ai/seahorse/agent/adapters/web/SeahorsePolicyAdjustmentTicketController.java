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

package com.miracle.ai.seahorse.agent.adapters.web;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.LearningLoopReport;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicket;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketDecision;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.learning.PolicyAdjustmentTicketManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.learning.PolicyAdjustmentTicketQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 策略调整落地工单的查询与审批 API。
 *
 * <p>刻意没有"一键应用"：{@code /apply} 表示"人已经在配置侧改完了，工单登记一下"，
 * 服务端不会去改任何阈值。把这一步自动化，等于让一个靠几天延迟的反馈信号驱动的闭环
 * 直接调生产流量。
 */
@RestController
public class SeahorsePolicyAdjustmentTicketController {

    private final ObjectProvider<PolicyAdjustmentTicketManagementInboundPort> ticketPortProvider;

    @Autowired
    public SeahorsePolicyAdjustmentTicketController(
            ObjectProvider<PolicyAdjustmentTicketManagementInboundPort> ticketPortProvider) {
        this.ticketPortProvider = ticketPortProvider;
    }

    @GetMapping("/api/policy-adjustment-tickets")
    public ApiResponse<Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false) PolicyAdjustmentTicketStatus status,
                                    @RequestParam(required = false) Long current,
                                    @RequestParam(required = false) Long size) {
        long safeCurrent = current == null ? PolicyAdjustmentTicketQuery.DEFAULT_CURRENT : current;
        long safeSize = size == null ? PolicyAdjustmentTicketQuery.DEFAULT_PAGE_SIZE : size;
        return ApiResponses.requireService(ticketPortProvider,
                port -> port.page(tenantId, status, safeCurrent, safeSize));
    }

    @GetMapping("/api/policy-adjustment-tickets/{ticketId}")
    public ApiResponse<Object> findById(@PathVariable String ticketId) {
        return ApiResponses.requireService(ticketPortProvider, port -> port.findById(ticketId)
                .orElseThrow(() -> new ResourceNotFoundException("Policy adjustment ticket not found")));
    }

    /**
     * 把一次闭环报告的建议转成待审批工单。指纹相同的建议不会重复开工单。
     */
    @PostMapping("/api/policy-adjustment-tickets/from-report")
    public ApiResponse<Object> submitProposals(@RequestBody(required = false) SubmitProposalsRequest request) {
        SubmitProposalsRequest safe = request == null ? new SubmitProposalsRequest(null, null, null) : request;
        return ApiResponses.requireService(ticketPortProvider, port -> port.submitProposals(
                safe.report(), safe.tenantId(), safe.createdBy()));
    }

    @PostMapping("/api/policy-adjustment-tickets/{ticketId}/approve")
    public ApiResponse<Object> approve(@PathVariable String ticketId,
                                       @RequestBody(required = false) DecisionRequest request) {
        return decide(ticketId, PolicyAdjustmentTicketStatus.APPROVED, request);
    }

    @PostMapping("/api/policy-adjustment-tickets/{ticketId}/reject")
    public ApiResponse<Object> reject(@PathVariable String ticketId,
                                      @RequestBody(required = false) DecisionRequest request) {
        return decide(ticketId, PolicyAdjustmentTicketStatus.REJECTED, request);
    }

    @PostMapping("/api/policy-adjustment-tickets/{ticketId}/apply")
    public ApiResponse<Object> apply(@PathVariable String ticketId,
                                     @RequestBody(required = false) DecisionRequest request) {
        return decide(ticketId, PolicyAdjustmentTicketStatus.APPLIED, request);
    }

    @PostMapping("/api/policy-adjustment-tickets/{ticketId}/roll-back")
    public ApiResponse<Object> rollBack(@PathVariable String ticketId,
                                        @RequestBody(required = false) DecisionRequest request) {
        return decide(ticketId, PolicyAdjustmentTicketStatus.ROLLED_BACK, request);
    }

    private ApiResponse<Object> decide(String ticketId,
                                       PolicyAdjustmentTicketStatus target,
                                       DecisionRequest request) {
        DecisionRequest safe = request == null ? new DecisionRequest(null, null) : request;
        PolicyAdjustmentTicketDecision decision =
                new PolicyAdjustmentTicketDecision(ticketId, target, safe.operator(), safe.comment());
        return ApiResponses.requireService(ticketPortProvider, port -> port.decide(decision));
    }

    public record DecisionRequest(String operator, String comment) {
    }

    public record SubmitProposalsRequest(LearningLoopReport report, String tenantId, String createdBy) {
    }
}

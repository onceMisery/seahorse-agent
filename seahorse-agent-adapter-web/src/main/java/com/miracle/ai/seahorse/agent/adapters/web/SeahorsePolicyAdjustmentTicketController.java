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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.learning.PolicyAdjustmentTicketNotFoundException;
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
 *
 * <h2>接口契约</h2>
 * <p>所有响应统一包在 {@code {code, message, data}} 里，成功码为 {@code "0"}。
 * 错误码由 {@code SeahorseWebExceptionHandler} 统一映射：
 * <ul>
 *   <li>{@code 400 INVALID_ARGUMENT} —— 参数不合法，含"状态迁移不被允许"
 *       （如 PENDING 直接跳 APPLIED）、"拒绝/落地必须填写意见"。</li>
 *   <li>{@code 404 RESOURCE_NOT_FOUND} —— 工单不存在。</li>
 *   <li>{@code 409 CONFLICT} —— 工单已被其他审批人改动，需重新加载后再决策。</li>
 * </ul>
 *
 * <p>字段命名：全部 camelCase，与 {@code PolicyAdjustmentTicket} record 一致，
 * 未启用任何 Jackson 命名策略，前端可直接使用。
 */
@RestController
public class SeahorsePolicyAdjustmentTicketController {

    private static final int MAX_SAMPLE_LIMIT = 1000;
    private static final int DEFAULT_SAMPLE_LIMIT = 200;

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

    /**
     * 跑一次自学习闭环，把产出的建议转成待审批工单。
     *
     * <p>这是页面"生成工单"按钮背后的接口：闭环此前只能被代码调用，没有入口，
     * 前端只能看到空列表。取样上限默认 200，最大 1000。
     */
    @PostMapping("/api/policy-adjustment-tickets/generate")
    public ApiResponse<Object> generate(@RequestBody(required = false) GenerateRequest request) {
        GenerateRequest safe = request == null ? new GenerateRequest(null, null, null) : request;
        int limit = safe.sampleLimit() == null ? DEFAULT_SAMPLE_LIMIT
                : Math.min(Math.max(safe.sampleLimit(), 1), MAX_SAMPLE_LIMIT);
        return ApiResponses.requireService(ticketPortProvider,
                port -> port.runAndGenerateTickets(safe.tenantId(), safe.createdBy(), limit));
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
        try {
            return ApiResponses.requireService(ticketPortProvider, port -> port.decide(decision));
        } catch (PolicyAdjustmentTicketNotFoundException ex) {
            // 内核不知道 HTTP，"找不到"要在这里翻译成 404，否则会变成 400 让前端误以为参数错了
            throw new ResourceNotFoundException(ex.getMessage());
        }
    }

    public record DecisionRequest(String operator, String comment) {
    }

    public record SubmitProposalsRequest(LearningLoopReport report, String tenantId, String createdBy) {
    }

    public record GenerateRequest(String tenantId, String createdBy, Integer sampleLimit) {
    }
}

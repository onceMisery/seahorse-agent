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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequestStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ApprovalModifyCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Human-in-the-loop 审批查询和决策 API。
 */
@RestController
public class SeahorseApprovalController {

    private static final String DEFAULT_CURRENT = "1";
    private static final String DEFAULT_SIZE = "10";

    private final ObjectProvider<ApprovalManagementInboundPort> approvalPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseApprovalController(ObjectProvider<ApprovalManagementInboundPort> approvalPortProvider) {
        this(approvalPortProvider, AdvancedFeatureGate.allEnabledForTests());
    }

    @Autowired
    public SeahorseApprovalController(ObjectProvider<ApprovalManagementInboundPort> approvalPortProvider,
                                      ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(approvalPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseApprovalController(ObjectProvider<ApprovalManagementInboundPort> approvalPortProvider,
                                      AdvancedFeatureGate advancedFeatureGate) {
        this.approvalPortProvider = approvalPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @GetMapping("/api/approvals")
    public ApiResponse<Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false) ApprovalRequestStatus status,
                                    @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                    @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        return ApiResponses.requireService(approvalPortProvider, port -> port.page(tenantId, status, current, size));
    }

    @GetMapping("/api/agent-runs/{runId}/pending-approvals")
    public ApiResponse<Object> listPendingByRunId(@PathVariable String runId) {
        return ApiResponses.requireService(approvalPortProvider, port -> port.listPendingByRunId(runId));
    }

    @GetMapping("/api/approvals/{approvalId}")
    public ApiResponse<Object> findById(@PathVariable String approvalId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_RUN_MANAGEMENT);
        return ApiResponses.requireService(approvalPortProvider, port -> port.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found")));
    }

    @PostMapping("/api/approvals/{approvalId}/approve")
    public ApiResponse<Object> approve(@PathVariable String approvalId,
                                       @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ApiResponses.requireService(approvalPortProvider,
                port -> port.approve(approvalId, toDecisionCommand(request)));
    }

    @PostMapping("/api/approvals/{approvalId}/reject")
    public ApiResponse<Object> reject(@PathVariable String approvalId,
                                      @RequestBody(required = false) ApprovalDecisionRequest request) {
        return ApiResponses.requireService(approvalPortProvider,
                port -> port.reject(approvalId, toDecisionCommand(request)));
    }

    @PostMapping("/api/approvals/{approvalId}/modify")
    public ApiResponse<Object> modify(@PathVariable String approvalId,
                                      @RequestBody ApprovalModifyRequest request) {
        ApprovalModifyRequest safeRequest = request == null ? new ApprovalModifyRequest(null, null) : request;
        return ApiResponses.requireService(approvalPortProvider, port -> port.modify(approvalId,
                new ApprovalModifyCommand(safeRequest.argumentsPreviewJson(), safeRequest.decisionComment())));
    }

    private ApprovalDecisionCommand toDecisionCommand(ApprovalDecisionRequest request) {
        ApprovalDecisionRequest safeRequest = request == null ? new ApprovalDecisionRequest(null) : request;
        return new ApprovalDecisionCommand(safeRequest.decisionComment());
    }

    public record ApprovalDecisionRequest(String decisionComment) {
    }

    public record ApprovalModifyRequest(String argumentsPreviewJson, String decisionComment) {
    }
}

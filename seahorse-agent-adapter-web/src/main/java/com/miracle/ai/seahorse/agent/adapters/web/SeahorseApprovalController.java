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
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Human-in-the-loop 审批查询和决策 API。
 */
@RestController
public class SeahorseApprovalController {

    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String SERVICE_NOT_AVAILABLE = "Service not available";
    private static final String DEFAULT_CURRENT = "1";
    private static final String DEFAULT_SIZE = "10";

    private final ObjectProvider<ApprovalManagementInboundPort> approvalPortProvider;

    public SeahorseApprovalController(ObjectProvider<ApprovalManagementInboundPort> approvalPortProvider) {
        this.approvalPortProvider = approvalPortProvider;
    }

    @GetMapping("/api/approvals")
    public Map<String, Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false) ApprovalRequestStatus status,
                                    @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                    @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size) {
        ApprovalManagementInboundPort port = requirePort();
        return ok(port.page(tenantId, status, current, size));
    }

    @GetMapping("/api/approvals/{approvalId}")
    public Map<String, Object> findById(@PathVariable String approvalId) {
        ApprovalManagementInboundPort port = requirePort();
        return ok(port.findById(approvalId)
                .orElseThrow(() -> new IllegalArgumentException("Approval not found")));
    }

    @PostMapping("/api/approvals/{approvalId}/approve")
    public Map<String, Object> approve(@PathVariable String approvalId,
                                       @RequestBody(required = false) ApprovalDecisionRequest request) {
        ApprovalManagementInboundPort port = requirePort();
        return ok(port.approve(approvalId, toDecisionCommand(request)));
    }

    @PostMapping("/api/approvals/{approvalId}/reject")
    public Map<String, Object> reject(@PathVariable String approvalId,
                                      @RequestBody(required = false) ApprovalDecisionRequest request) {
        ApprovalManagementInboundPort port = requirePort();
        return ok(port.reject(approvalId, toDecisionCommand(request)));
    }

    @PostMapping("/api/approvals/{approvalId}/modify")
    public Map<String, Object> modify(@PathVariable String approvalId,
                                      @RequestBody ApprovalModifyRequest request) {
        ApprovalManagementInboundPort port = requirePort();
        ApprovalModifyRequest safeRequest = request == null ? new ApprovalModifyRequest(null, null) : request;
        return ok(port.modify(approvalId,
                new ApprovalModifyCommand(safeRequest.argumentsPreviewJson(), safeRequest.decisionComment())));
    }

    private ApprovalManagementInboundPort requirePort() {
        ApprovalManagementInboundPort port = approvalPortProvider.getIfAvailable();
        if (port == null) {
            throw new IllegalStateException(SERVICE_NOT_AVAILABLE);
        }
        return port;
    }

    private ApprovalDecisionCommand toDecisionCommand(ApprovalDecisionRequest request) {
        ApprovalDecisionRequest safeRequest = request == null ? new ApprovalDecisionRequest(null) : request;
        return new ApprovalDecisionCommand(safeRequest.decisionComment());
    }

    private Map<String, Object> ok(Object data) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data == null ? Map.of() : data);
    }

    public record ApprovalDecisionRequest(String decisionComment) {
    }

    public record ApprovalModifyRequest(String argumentsPreviewJson, String decisionComment) {
    }
}

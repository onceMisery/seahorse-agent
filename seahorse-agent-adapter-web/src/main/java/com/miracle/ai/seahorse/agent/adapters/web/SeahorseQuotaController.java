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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.definition.AgentRiskLevel;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaPolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.quota.QuotaUsage;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.QuotaPolicyUpsertCommand;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class SeahorseQuotaController {

    private final ObjectProvider<QuotaManagementInboundPort> quotaManagementPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseQuotaController(ObjectProvider<QuotaManagementInboundPort> quotaManagementPortProvider) {
        this(quotaManagementPortProvider, AdvancedFeatureGate.allEnabledForTests());
    }

    @Autowired
    public SeahorseQuotaController(ObjectProvider<QuotaManagementInboundPort> quotaManagementPortProvider,
                                   ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(quotaManagementPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseQuotaController(ObjectProvider<QuotaManagementInboundPort> quotaManagementPortProvider,
                                   AdvancedFeatureGate advancedFeatureGate) {
        this.quotaManagementPortProvider = quotaManagementPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/api/quotas/policies")
    public ApiResponse<Object> upsertPolicy(@RequestBody QuotaPolicyRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.QUOTA_MANAGEMENT);
        QuotaPolicyRequest safeRequest = request == null
                ? new QuotaPolicyRequest(null, null, null, null, null, null, null, null, null, null, null)
                : request;
        return ApiResponses.requireService(quotaManagementPortProvider,
                port -> port.upsertPolicy(new QuotaPolicyUpsertCommand(
                        safeRequest.policyId(),
                        safeRequest.tenantId(),
                        safeRequest.scope(),
                        safeRequest.subjectId(),
                        safeRequest.status(),
                        safeRequest.tokenLimit(),
                        safeRequest.callLimit(),
                        safeRequest.costLimit(),
                        safeRequest.warnRatio(),
                        safeRequest.createdAt(),
                        safeRequest.updatedAt())));
    }

    @PostMapping("/api/quotas/policies/{policyId}/disable")
    public ApiResponse<Object> disablePolicy(@PathVariable String policyId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.QUOTA_MANAGEMENT);
        return ApiResponses.requireService(quotaManagementPortProvider, port -> {
            port.disablePolicy(policyId);
            return null;
        });
    }

    @PostMapping("/api/quotas/decisions:evaluate")
    public ApiResponse<Object> evaluate(@RequestBody QuotaDecisionRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.QUOTA_MANAGEMENT);
        QuotaDecisionRequest safeRequest = request == null
                ? new QuotaDecisionRequest(null, null, null, null, null, null, null, 0L, 0L, 0d)
                : request;
        return ApiResponses.requireService(quotaManagementPortProvider,
                port -> port.evaluate(new QuotaDecisionCommand(
                        safeRequest.tenantId(),
                        safeRequest.agentId(),
                        safeRequest.userId(),
                        safeRequest.toolId(),
                        safeRequest.modelId(),
                        safeRequest.runId(),
                        safeRequest.riskLevel(),
                        new QuotaUsage(safeRequest.tokens(), safeRequest.calls(), safeRequest.cost()))));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuotaPolicyRequest(String policyId,
                                     String tenantId,
                                     QuotaScope scope,
                                     String subjectId,
                                     QuotaPolicyStatus status,
                                     Long tokenLimit,
                                     Long callLimit,
                                     Double costLimit,
                                     Double warnRatio,
                                     Instant createdAt,
                                     Instant updatedAt) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record QuotaDecisionRequest(String tenantId,
                                       String agentId,
                                       String userId,
                                       String toolId,
                                       String modelId,
                                       String runId,
                                       AgentRiskLevel riskLevel,
                                       long tokens,
                                       long calls,
                                       double cost) {
    }
}

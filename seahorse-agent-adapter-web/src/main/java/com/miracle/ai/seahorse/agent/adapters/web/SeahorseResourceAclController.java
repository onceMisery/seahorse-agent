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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessDecisionEffect;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.AccessSubjectType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAction;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportMode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclImportResult;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleScope;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ResourceAclRuleStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclImportCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclImportDryRunCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResourceAclManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.ResourceAclQuery;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@RestController
public class SeahorseResourceAclController {

    private static final String DEFAULT_CURRENT = "1";
    private static final String DEFAULT_SIZE = "10";

    private final ObjectProvider<ResourceAclManagementInboundPort> resourceAclManagementPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseResourceAclController(
            ObjectProvider<ResourceAclManagementInboundPort> resourceAclManagementPortProvider) {
        this(resourceAclManagementPortProvider, AdvancedFeatureGate.allEnabledForTests());
    }

    @Autowired
    public SeahorseResourceAclController(
            ObjectProvider<ResourceAclManagementInboundPort> resourceAclManagementPortProvider,
            ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(resourceAclManagementPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseResourceAclController(
            ObjectProvider<ResourceAclManagementInboundPort> resourceAclManagementPortProvider,
            AdvancedFeatureGate advancedFeatureGate) {
        this.resourceAclManagementPortProvider = resourceAclManagementPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/api/resource-acl-rules")
    public ApiResponse<Object> create(@RequestBody ResourceAclCreateRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.RESOURCE_ACL_MANAGEMENT);
        ResourceAclCreateRequest safeRequest = request == null
                ? new ResourceAclCreateRequest(null, null, null, null, null, null, null, 0, null)
                : request;
        return ApiResponses.requireService(resourceAclManagementPortProvider,
                port -> port.create(new ResourceAclCreateCommand(
                        safeRequest.tenantId(),
                        safeRequest.resourceType(),
                        safeRequest.resourceId(),
                        safeRequest.subjectType(),
                        safeRequest.subjectId(),
                        safeRequest.action(),
                        safeRequest.effect(),
                        safeRequest.priority(),
                        safeRequest.expiresAt())));
    }

    @PostMapping("/api/resource-acl-rules:dry-run-import")
    public ApiResponse<Object> dryRunImport(@RequestBody ResourceAclImportDryRunRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.RESOURCE_ACL_MANAGEMENT);
        ResourceAclImportDryRunRequest safeRequest = request == null
                ? new ResourceAclImportDryRunRequest(List.of())
                : request;
        return ApiResponses.requireService(resourceAclManagementPortProvider,
                port -> port.dryRunImport(new ResourceAclImportDryRunCommand(safeRequest.toItems())));
    }

    @PostMapping("/api/resource-acl-rules:import")
    public ApiResponse<Object> importRules(@RequestBody ResourceAclImportRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.RESOURCE_ACL_MANAGEMENT);
        ResourceAclImportRequest safeRequest = request == null
                ? new ResourceAclImportRequest(null, List.of())
                : request;
        return ApiResponses.requireService(resourceAclManagementPortProvider,
                port -> ResourceAclImportResponse.from(
                        port.importRules(new ResourceAclImportCommand(safeRequest.toItems(), safeRequest.mode()))));
    }

    @GetMapping("/api/resource-acl-rules")
    public ApiResponse<Object> page(@RequestParam(required = false) String tenantId,
                                    @RequestParam(required = false) String resourceType,
                                    @RequestParam(required = false) String resourceId,
                                    @RequestParam(required = false) AccessSubjectType subjectType,
                                    @RequestParam(required = false) String subjectId,
                                    @RequestParam(required = false) ResourceAclRuleStatus status,
                                    @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                    @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.RESOURCE_ACL_MANAGEMENT);
        return ApiResponses.requireService(resourceAclManagementPortProvider,
                port -> port.page(new ResourceAclQuery(
                        tenantId,
                        resourceType,
                        resourceId,
                        subjectType,
                        subjectId,
                        status,
                        current,
                        size)));
    }

    @PostMapping("/api/resource-acl-rules/{ruleId}/disable")
    public ApiResponse<Object> disable(@PathVariable String ruleId) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.RESOURCE_ACL_MANAGEMENT);
        return ApiResponses.requireService(resourceAclManagementPortProvider, port -> port.disable(ruleId));
    }

    public record ResourceAclCreateRequest(String tenantId,
                                           String resourceType,
                                           String resourceId,
                                           AccessSubjectType subjectType,
                                           String subjectId,
                                           ResourceAction action,
                                           AccessDecisionEffect effect,
                                           int priority,
                                           Instant expiresAt) {
    }

    public record ResourceAclImportDryRunRequest(List<ResourceAclImportItemRequest> items) {

        private List<ResourceAclImportItem> toItems() {
            if (items == null) {
                return List.of();
            }
            return items.stream()
                    .filter(Objects::nonNull)
                    .map(ResourceAclImportItemRequest::toItem)
                    .toList();
        }
    }

    public record ResourceAclImportRequest(ResourceAclImportMode mode,
                                           List<ResourceAclImportItemRequest> items) {

        private List<ResourceAclImportItem> toItems() {
            if (items == null) {
                return List.of();
            }
            return items.stream()
                    .filter(Objects::nonNull)
                    .map(ResourceAclImportItemRequest::toItem)
                    .toList();
        }
    }

    public record ResourceAclImportResponse(ResourceAclImportMode mode,
                                            Object dryRunReport,
                                            List<String> createdRuleIds,
                                            Map<ResourceAclImportReasonCode, Integer> reasonCounts,
                                            int createdCount,
                                            int skippedCount,
                                            boolean failed) {

        private static ResourceAclImportResponse from(ResourceAclImportResult result) {
            return new ResourceAclImportResponse(
                    result.mode(),
                    result.dryRunReport(),
                    result.createdRuleIds(),
                    result.reasonCounts(),
                    result.createdCount(),
                    result.skippedCount(),
                    result.failed());
        }
    }

    public record ResourceAclImportItemRequest(String tenantId,
                                               ResourceAclRuleScope scope,
                                               String resourceType,
                                               String resourceId,
                                               AccessSubjectType subjectType,
                                               String subjectId,
                                               ResourceAction action,
                                               AccessDecisionEffect effect,
                                               int priority,
                                               Instant expiresAt) {

        private ResourceAclImportItem toItem() {
            return new ResourceAclImportItem(
                    tenantId,
                    scope,
                    resourceType,
                    resourceId,
                    subjectType,
                    subjectId,
                    action,
                    effect,
                    priority,
                    expiresAt);
        }
    }
}

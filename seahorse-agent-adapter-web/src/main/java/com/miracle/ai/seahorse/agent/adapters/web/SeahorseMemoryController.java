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

import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryManagementInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SeahorseMemoryController {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String DEFAULT_OPERATOR = "system";
    private static final String INTERACTIVE_OPERATOR_PREFIX = "interactive:";
    private static final int CONFLICT_RESOLVED_BY_MAX_LENGTH = 32;

    private final ObjectProvider<MemoryManagementInboundPort> managementPortProvider;
    private final ObjectProvider<MemoryGovernanceInboundPort> governancePortProvider;

    public SeahorseMemoryController(ObjectProvider<MemoryManagementInboundPort> managementPortProvider,
                                    ObjectProvider<MemoryGovernanceInboundPort> governancePortProvider) {
        this.managementPortProvider = managementPortProvider;
        this.governancePortProvider = governancePortProvider;
    }

    @GetMapping("/memories")
    public ApiResponse<Object> list(@RequestParam(required = false) String userId,
                                    @RequestParam(defaultValue = "short_term") String layer,
                                    @RequestParam(required = false) String conversationId,
                                    @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> port.listMemories(userIdOrDefault(userId), layer, conversationId, limit));
    }

    @GetMapping("/memories/{layer}/{memoryId}")
    public ApiResponse<Object> detail(@PathVariable String layer, @PathVariable String memoryId) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> port.findMemory(layer, memoryId).orElse(null));
    }

    @DeleteMapping("/memories/{layer}/{memoryId}")
    public ApiResponse<Object> delete(@PathVariable String layer, @PathVariable String memoryId) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> Map.of("deleted", port.deleteMemory(layer, memoryId)));
    }

    @GetMapping("/memories/quality-snapshots")
    public ApiResponse<Object> qualitySnapshots(@RequestParam(required = false) String userId,
                                                @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> port.listQualitySnapshots(userIdOrDefault(userId), limit));
    }

    @GetMapping("/memories/conflicts")
    public ApiResponse<Object> conflicts(@RequestParam(required = false) String userId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> port.listConflicts(userIdOrDefault(userId), status, limit));
    }

    @GetMapping("/memories/profile-facts")
    public ApiResponse<Object> profileFacts(@RequestParam(required = false) String userId,
                                            @RequestParam(defaultValue = "default") String tenantId,
                                            @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> port.listProfileFacts(userIdOrDefault(userId), tenantId, limit));
    }

    @PostMapping("/memories/profile-facts/{slotKey}/disable")
    public ApiResponse<Object> disableProfileFact(@PathVariable String slotKey,
                                                  @RequestParam(required = false) String userId,
                                                  @RequestParam(defaultValue = "default") String tenantId,
                                                  @RequestHeader(value = HEADER_USER_ID, required = false)
                                                  String operatorUserId) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> Map.of("disabled", port.disableProfileFact(
                        userIdOrDefault(userId), tenantId, slotKey, operator(operatorUserId))));
    }

    @GetMapping("/memories/corrections")
    public ApiResponse<Object> correctionRules(@RequestParam(required = false) String userId,
                                               @RequestParam(defaultValue = "default") String tenantId,
                                               @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> port.listCorrectionRules(userIdOrDefault(userId), tenantId, limit));
    }

    @GetMapping("/memories/operations")
    public ApiResponse<Object> operations(@RequestParam(required = false) String userId,
                                          @RequestParam(defaultValue = "default") String tenantId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> port.listOperations(userIdOrDefault(userId), tenantId, status, limit));
    }

    @GetMapping("/memories/outbox")
    public ApiResponse<Object> outbox(@RequestParam(defaultValue = "20") int limit) {
        return ApiResponses.requireServiceOrError(managementPortProvider, port -> port.listOutboxTasks(limit));
    }

    @GetMapping("/memories/health")
    public ApiResponse<Object> health(@RequestParam(required = false) String userId,
                                      @RequestParam(defaultValue = "default") String tenantId) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> port.memoryHealth(userIdOrDefault(userId), tenantId));
    }

    @GetMapping("/memories/readiness")
    public ApiResponse<Object> readiness(@RequestParam(required = false) String userId,
                                         @RequestParam(defaultValue = "default") String tenantId) {
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> port.memoryReadiness(userIdOrDefault(userId), tenantId));
    }

    @GetMapping("/memories/policy-config")
    public ApiResponse<Object> policyConfig() {
        return ApiResponses.requireServiceOrError(managementPortProvider, MemoryManagementInboundPort::memoryPolicyConfig);
    }

    @PostMapping("/memories/policy-config")
    public ApiResponse<Object> updatePolicyConfig(@RequestBody(required = false) MemoryPolicyConfig request) {
        return ApiResponses.requireServiceOrError(managementPortProvider, port -> port.updatePolicyConfig(request));
    }

    @PostMapping("/memories/conflicts/{conflictId}/resolve")
    public ApiResponse<Object> resolveConflict(@PathVariable String conflictId,
                                               @RequestBody(required = false) MemoryConflictResolveRequest request,
                                               @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        String action = request == null ? "manual-resolve" : request.action();
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> Map.of("resolved", port.resolveConflict(conflictId, action, operator(userId))));
    }

    @PostMapping("/memories/conflicts/interactive-resolve")
    public ApiResponse<Object> resolveConflictInteractively(
            @RequestBody(required = false) MemoryConflictResolveRequest request,
            @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        String conflictId = request == null ? "" : request.conflictId();
        String action = request == null ? "manual-resolve" : request.action();
        String resolvedBy = interactiveOperator(userId);
        return ApiResponses.requireServiceOrError(managementPortProvider,
                port -> Map.of("resolved", port.resolveConflict(conflictId, action, resolvedBy)));
    }

    @PostMapping("/memories/governance/run")
    public ApiResponse<Object> runGovernance(@RequestParam(required = false) String userId,
                                             @RequestParam(defaultValue = "manual") String reason,
                                             @RequestParam(defaultValue = "true") boolean assessQuality) {
        return ApiResponses.requireServiceOrError(governancePortProvider,
                port -> port.runGovernance(userIdOrDefault(userId), reason, assessQuality));
    }

    @PostMapping("/memories/governance/decay")
    public ApiResponse<Object> runDecay(@RequestParam(defaultValue = "manual-decay") String reason) {
        return ApiResponses.requireServiceOrError(governancePortProvider, port -> port.runDecay(reason));
    }

    @PostMapping("/memories/governance/quality")
    public ApiResponse<Object> assessQuality(@RequestParam(required = false) String userId) {
        return ApiResponses.requireServiceOrError(governancePortProvider, port -> port.assessQuality(userIdOrDefault(userId)));
    }

    private String userIdOrDefault(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }

    private String interactiveOperator(String userId) {
        String operator = operator(userId);
        int maxOperatorLength = CONFLICT_RESOLVED_BY_MAX_LENGTH - INTERACTIVE_OPERATOR_PREFIX.length();
        if (operator.length() > maxOperatorLength) {
            operator = operator.substring(operator.length() - maxOperatorLength);
        }
        return INTERACTIVE_OPERATOR_PREFIX + operator;
    }
}

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
    private static final String KEY_CODE = "code";
    private static final String KEY_DATA = "data";
    private static final String SUCCESS_CODE = "0";
    private static final String DEFAULT_OPERATOR = "system";

    private final ObjectProvider<MemoryManagementInboundPort> managementPortProvider;
    private final ObjectProvider<MemoryGovernanceInboundPort> governancePortProvider;

    public SeahorseMemoryController(ObjectProvider<MemoryManagementInboundPort> managementPortProvider,
                                    ObjectProvider<MemoryGovernanceInboundPort> governancePortProvider) {
        this.managementPortProvider = managementPortProvider;
        this.governancePortProvider = governancePortProvider;
    }

    @GetMapping("/memories")
    public Map<String, Object> list(@RequestParam String userId,
                                    @RequestParam(defaultValue = "short_term") String layer,
                                    @RequestParam(required = false) String conversationId,
                                    @RequestParam(defaultValue = "20") int limit) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().listMemories(userId, layer, conversationId, limit));
    }

    @GetMapping("/memories/{layer}/{memoryId}")
    public Map<String, Object> detail(@PathVariable String layer, @PathVariable String memoryId) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().findMemory(layer, memoryId).orElse(null));
    }

    @DeleteMapping("/memories/{layer}/{memoryId}")
    public Map<String, Object> delete(@PathVariable String layer, @PathVariable String memoryId) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(Map.of("deleted", managementPortProvider.getIfAvailable().deleteMemory(layer, memoryId)));
    }

    @GetMapping("/memories/quality-snapshots")
    public Map<String, Object> qualitySnapshots(@RequestParam String userId,
                                                @RequestParam(defaultValue = "20") int limit) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().listQualitySnapshots(userId, limit));
    }

    @GetMapping("/memories/conflicts")
    public Map<String, Object> conflicts(@RequestParam String userId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "20") int limit) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().listConflicts(userId, status, limit));
    }

    @GetMapping("/memories/profile-facts")
    public Map<String, Object> profileFacts(@RequestParam String userId,
                                            @RequestParam(defaultValue = "default") String tenantId,
                                            @RequestParam(defaultValue = "20") int limit) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().listProfileFacts(userId, tenantId, limit));
    }

    @GetMapping("/memories/corrections")
    public Map<String, Object> correctionRules(@RequestParam String userId,
                                               @RequestParam(defaultValue = "default") String tenantId,
                                               @RequestParam(defaultValue = "20") int limit) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().listCorrectionRules(userId, tenantId, limit));
    }

    @GetMapping("/memories/operations")
    public Map<String, Object> operations(@RequestParam String userId,
                                          @RequestParam(defaultValue = "default") String tenantId,
                                          @RequestParam(required = false) String status,
                                          @RequestParam(defaultValue = "20") int limit) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().listOperations(userId, tenantId, status, limit));
    }

    @GetMapping("/memories/outbox")
    public Map<String, Object> outbox(@RequestParam(defaultValue = "20") int limit) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().listOutboxTasks(limit));
    }

    @GetMapping("/memories/health")
    public Map<String, Object> health(@RequestParam String userId,
                                      @RequestParam(defaultValue = "default") String tenantId) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().memoryHealth(userId, tenantId));
    }

    @GetMapping("/memories/policy-config")
    public Map<String, Object> policyConfig() {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().memoryPolicyConfig());
    }

    @PostMapping("/memories/policy-config")
    public Map<String, Object> updatePolicyConfig(@RequestBody(required = false) MemoryPolicyConfig request) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(managementPortProvider.getIfAvailable().updatePolicyConfig(request));
    }

    @PostMapping("/memories/conflicts/{conflictId}/resolve")
    public Map<String, Object> resolveConflict(@PathVariable String conflictId,
                                               @RequestBody(required = false) MemoryConflictResolveRequest request,
                                               @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        if (managementPortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        String action = request == null ? "manual-resolve" : request.action();
        return ok(Map.of("resolved", managementPortProvider.getIfAvailable().resolveConflict(conflictId, action, operator(userId))));
    }

    @PostMapping("/memories/governance/run")
    public Map<String, Object> runGovernance(@RequestParam String userId,
                                             @RequestParam(defaultValue = "manual") String reason,
                                             @RequestParam(defaultValue = "true") boolean assessQuality) {
        if (governancePortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(governancePortProvider.getIfAvailable().runGovernance(userId, reason, assessQuality));
    }

    @PostMapping("/memories/governance/decay")
    public Map<String, Object> runDecay(@RequestParam(defaultValue = "manual-decay") String reason) {
        if (governancePortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(governancePortProvider.getIfAvailable().runDecay(reason));
    }

    @PostMapping("/memories/governance/quality")
    public Map<String, Object> assessQuality(@RequestParam String userId) {
        if (governancePortProvider.getIfAvailable() == null) return Map.of("code", "1", "message", "Service not available");
        return ok(governancePortProvider.getIfAvailable().assessQuality(userId));
    }

    private Map<String, Object> ok(Object data) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data == null ? Map.of() : data);
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }
}

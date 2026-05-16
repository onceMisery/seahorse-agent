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

    private final MemoryManagementInboundPort managementPort;
    private final MemoryGovernanceInboundPort governancePort;

    public SeahorseMemoryController(ObjectProvider<MemoryManagementInboundPort> managementPortProvider,
                                    ObjectProvider<MemoryGovernanceInboundPort> governancePortProvider) {
        this.managementPort = managementPortProvider.getIfAvailable();
        this.governancePort = governancePortProvider.getIfAvailable();
    }

    @GetMapping("/memories")
    public Map<String, Object> list(@RequestParam String userId,
                                    @RequestParam(defaultValue = "short_term") String layer,
                                    @RequestParam(required = false) String conversationId,
                                    @RequestParam(defaultValue = "20") int limit) {
        return ok(managementPort.listMemories(userId, layer, conversationId, limit));
    }

    @GetMapping("/memories/{layer}/{memoryId}")
    public Map<String, Object> detail(@PathVariable String layer, @PathVariable String memoryId) {
        return ok(managementPort.findMemory(layer, memoryId).orElse(null));
    }

    @DeleteMapping("/memories/{layer}/{memoryId}")
    public Map<String, Object> delete(@PathVariable String layer, @PathVariable String memoryId) {
        return ok(Map.of("deleted", managementPort.deleteMemory(layer, memoryId)));
    }

    @GetMapping("/memories/quality-snapshots")
    public Map<String, Object> qualitySnapshots(@RequestParam String userId,
                                                @RequestParam(defaultValue = "20") int limit) {
        return ok(managementPort.listQualitySnapshots(userId, limit));
    }

    @GetMapping("/memories/conflicts")
    public Map<String, Object> conflicts(@RequestParam String userId,
                                         @RequestParam(required = false) String status,
                                         @RequestParam(defaultValue = "20") int limit) {
        return ok(managementPort.listConflicts(userId, status, limit));
    }

    @PostMapping("/memories/conflicts/{conflictId}/resolve")
    public Map<String, Object> resolveConflict(@PathVariable String conflictId,
                                               @RequestBody(required = false) MemoryConflictResolveRequest request,
                                               @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
        String action = request == null ? "manual-resolve" : request.action();
        return ok(Map.of("resolved", managementPort.resolveConflict(conflictId, action, operator(userId))));
    }

    @PostMapping("/memories/governance/run")
    public Map<String, Object> runGovernance(@RequestParam String userId,
                                             @RequestParam(defaultValue = "manual") String reason,
                                             @RequestParam(defaultValue = "true") boolean assessQuality) {
        return ok(governancePort.runGovernance(userId, reason, assessQuality));
    }

    @PostMapping("/memories/governance/decay")
    public Map<String, Object> runDecay(@RequestParam(defaultValue = "manual-decay") String reason) {
        return ok(governancePort.runDecay(reason));
    }

    @PostMapping("/memories/governance/quality")
    public Map<String, Object> assessQuality(@RequestParam String userId) {
        return ok(governancePort.assessQuality(userId));
    }

    private Map<String, Object> ok(Object data) {
        return Map.of(KEY_CODE, SUCCESS_CODE, KEY_DATA, data == null ? Map.of() : data);
    }

    private String operator(String userId) {
        return userId == null || userId.isBlank() ? DEFAULT_OPERATOR : userId.trim();
    }
}

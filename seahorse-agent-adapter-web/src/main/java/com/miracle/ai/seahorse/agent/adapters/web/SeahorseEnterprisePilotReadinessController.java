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

import com.miracle.ai.seahorse.agent.ports.inbound.agent.EnterprisePilotReadinessGenerateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.EnterprisePilotReadinessInboundPort;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class SeahorseEnterprisePilotReadinessController {

    private final ObjectProvider<EnterprisePilotReadinessInboundPort> readinessPortProvider;

    public SeahorseEnterprisePilotReadinessController(
            ObjectProvider<EnterprisePilotReadinessInboundPort> readinessPortProvider) {
        this.readinessPortProvider = readinessPortProvider;
    }

    @PostMapping("/api/agents/{agentId}/versions/{versionId}/pilot-readiness/generate")
    public ApiResponse<Object> generate(@PathVariable String agentId,
                                        @PathVariable String versionId,
                                        @RequestBody EnterprisePilotReadinessGenerateRequest request) {
        EnterprisePilotReadinessGenerateRequest safeRequest =
                request == null ? new EnterprisePilotReadinessGenerateRequest(null, null) : request;
        return ApiResponses.requireService(readinessPortProvider,
                port -> port.generate(new EnterprisePilotReadinessGenerateCommand(
                        safeRequest.tenantId(),
                        agentId,
                        versionId,
                        safeRequest.operator())));
    }

    @GetMapping("/api/agents/{agentId}/versions/{versionId}/pilot-readiness/latest")
    public ApiResponse<Object> latest(@PathVariable String agentId,
                                      @PathVariable String versionId,
                                      @RequestParam String tenantId) {
        return ApiResponses.requireService(readinessPortProvider,
                port -> port.latest(tenantId, agentId, versionId)
                        .orElseThrow(() -> new IllegalArgumentException("Enterprise pilot readiness report not found")));
    }

    public record EnterprisePilotReadinessGenerateRequest(String tenantId,
                                                          String operator) {
    }
}

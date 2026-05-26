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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageSource;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.CostUsageInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

@RestController
public class SeahorseCostUsageController {

    private final ObjectProvider<CostUsageInboundPort> costUsagePortProvider;

    public SeahorseCostUsageController(ObjectProvider<CostUsageInboundPort> costUsagePortProvider) {
        this.costUsagePortProvider = costUsagePortProvider;
    }

    @PostMapping("/api/cost-usage-records")
    public ApiResponse<Object> append(@RequestBody CostUsageAppendRequest request) {
        CostUsageAppendRequest safeRequest = request == null
                ? new CostUsageAppendRequest(null, null, null, null, null, null, null, null, 0L, 0L, 0d, null, null)
                : request;
        return ApiResponses.requireService(costUsagePortProvider,
                port -> port.append(new CostUsageRecord(
                        safeRequest.usageId(),
                        safeRequest.tenantId(),
                        safeRequest.agentId(),
                        safeRequest.runId(),
                        safeRequest.userId(),
                        safeRequest.toolId(),
                        safeRequest.modelId(),
                        safeRequest.source(),
                        safeRequest.tokens(),
                        safeRequest.calls(),
                        safeRequest.cost(),
                        safeRequest.reasonRef(),
                        safeRequest.createdAt())));
    }

    @GetMapping("/api/cost-usage:aggregate")
    public ApiResponse<Object> aggregate(@RequestParam String tenantId,
                                         @RequestParam(required = false) String agentId,
                                         @RequestParam(required = false) String runId,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
                                         @RequestParam(required = false)
                                         @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to) {
        return ApiResponses.requireService(costUsagePortProvider,
                port -> port.aggregate(new CostUsageQuery(tenantId, agentId, runId, from, to)));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CostUsageAppendRequest(String usageId,
                                         String tenantId,
                                         String agentId,
                                         String runId,
                                         String userId,
                                         String toolId,
                                         String modelId,
                                         CostUsageSource source,
                                         long tokens,
                                         long calls,
                                         double cost,
                                         String reasonRef,
                                         Instant createdAt) {
    }
}

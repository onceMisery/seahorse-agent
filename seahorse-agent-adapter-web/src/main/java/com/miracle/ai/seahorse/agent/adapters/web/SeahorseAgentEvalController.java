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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.eval.AgentEvalType;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalSummaryHistoryQuery;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentEvalSummarySaveCommand;
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

@RestController
public class SeahorseAgentEvalController {

    private static final String DEFAULT_CURRENT = "1";
    private static final String DEFAULT_SIZE = "20";

    private final ObjectProvider<AgentEvalInboundPort> agentEvalPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseAgentEvalController(ObjectProvider<AgentEvalInboundPort> agentEvalPortProvider) {
        this(agentEvalPortProvider, AdvancedFeatureGate.consumerWebDefaults());
    }

    @Autowired
    public SeahorseAgentEvalController(ObjectProvider<AgentEvalInboundPort> agentEvalPortProvider,
                                       ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this(agentEvalPortProvider,
                advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::consumerWebDefaults));
    }

    public SeahorseAgentEvalController(ObjectProvider<AgentEvalInboundPort> agentEvalPortProvider,
                                       AdvancedFeatureGate advancedFeatureGate) {
        this.agentEvalPortProvider = agentEvalPortProvider;
        this.advancedFeatureGate = advancedFeatureGate == null
                ? AdvancedFeatureGate.consumerWebDefaults()
                : advancedFeatureGate;
    }

    @PostMapping("/api/agents/{agentId}/versions/{versionId}/eval-summaries")
    public ApiResponse<Object> save(@PathVariable String agentId,
                                    @PathVariable String versionId,
                                    @RequestBody AgentEvalSummarySaveRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_EVALUATION);
        AgentEvalSummarySaveRequest safeRequest = request == null
                ? new AgentEvalSummarySaveRequest(
                null, null, null, null, 0d, 0d, 0d, 0, null, null, List.of(), null, null)
                : request;
        return ApiResponses.requireService(agentEvalPortProvider,
                port -> port.saveSummary(new AgentEvalSummarySaveCommand(
                        safeRequest.summaryId(),
                        safeRequest.tenantId(),
                        agentId,
                        versionId,
                        safeRequest.evalType(),
                        safeRequest.status(),
                        safeRequest.score(),
                        safeRequest.passThreshold(),
                        safeRequest.warnThreshold(),
                        safeRequest.caseCount(),
                        safeRequest.datasetRef(),
                        safeRequest.evalRunRef(),
                        safeRequest.evidenceRefs(),
                        safeRequest.createdBy(),
                        safeRequest.createdAt())));
    }

    @GetMapping("/api/agents/{agentId}/versions/{versionId}/eval-summaries/latest")
    public ApiResponse<Object> latest(@PathVariable String agentId,
                                      @PathVariable String versionId,
                                      @RequestParam String tenantId,
                                      @RequestParam AgentEvalType evalType) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_EVALUATION);
        return ApiResponses.requireService(agentEvalPortProvider, port -> port.latestSummary(
                        tenantId,
                        agentId,
                        versionId,
                        evalType)
                .orElseThrow(() -> new IllegalArgumentException("Agent eval summary not found")));
    }

    @GetMapping("/api/agents/{agentId}/versions/{versionId}/eval-summaries")
    public ApiResponse<Object> history(@PathVariable String agentId,
                                       @PathVariable String versionId,
                                       @RequestParam String tenantId,
                                       @RequestParam(required = false) AgentEvalType evalType,
                                       @RequestParam(required = false, defaultValue = DEFAULT_CURRENT) long current,
                                       @RequestParam(required = false, defaultValue = DEFAULT_SIZE) long size) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_EVALUATION);
        return ApiResponses.requireService(agentEvalPortProvider,
                port -> port.history(new AgentEvalSummaryHistoryQuery(
                        tenantId,
                        agentId,
                        versionId,
                        evalType,
                        current,
                        size)));
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AgentEvalSummarySaveRequest(String summaryId,
                                              String tenantId,
                                              AgentEvalType evalType,
                                              AgentEvalStatus status,
                                              double score,
                                              double passThreshold,
                                              double warnThreshold,
                                              int caseCount,
                                              String datasetRef,
                                              String evalRunRef,
                                              List<String> evidenceRefs,
                                              String createdBy,
                                              Instant createdAt) {

        public AgentEvalSummarySaveRequest {
            evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
        }
    }
}

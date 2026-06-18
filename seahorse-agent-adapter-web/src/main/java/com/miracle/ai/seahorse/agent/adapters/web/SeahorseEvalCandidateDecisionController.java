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

import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalCandidateDecisionService;
import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalRegressionService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class SeahorseEvalCandidateDecisionController {

    private final ObjectProvider<KernelEvalCandidateDecisionService> decisionServiceProvider;
    private final ObjectProvider<KernelEvalRegressionService> regressionServiceProvider;
    private final AdvancedFeatureGate advancedFeatureGate;

    public SeahorseEvalCandidateDecisionController(
            ObjectProvider<KernelEvalCandidateDecisionService> decisionServiceProvider,
            ObjectProvider<KernelEvalRegressionService> regressionServiceProvider,
            ObjectProvider<AdvancedFeatureGate> advancedFeatureGateProvider) {
        this.decisionServiceProvider = decisionServiceProvider;
        this.regressionServiceProvider = regressionServiceProvider;
        this.advancedFeatureGate = advancedFeatureGateProvider.getIfAvailable(AdvancedFeatureGate::demoDefaults);
    }

    @PostMapping("/api/eval-candidates/{candidateId}/accept")
    public ApiResponse<Object> accept(@PathVariable String candidateId,
                                      @RequestBody(required = false) DecisionRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_EVALUATION);
        String note = request != null ? request.note() : null;
        return ApiResponses.requireService(decisionServiceProvider, service -> {
            service.acceptCandidate(candidateId, note);
            return Map.of("candidateId", candidateId, "decision", "ACCEPTED");
        });
    }

    @PostMapping("/api/eval-candidates/{candidateId}/reject")
    public ApiResponse<Object> reject(@PathVariable String candidateId,
                                      @RequestBody(required = false) DecisionRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_EVALUATION);
        String reason = request != null ? request.note() : null;
        return ApiResponses.requireService(decisionServiceProvider, service -> {
            service.rejectCandidate(candidateId, reason);
            return Map.of("candidateId", candidateId, "decision", "REJECTED");
        });
    }

    @PostMapping("/api/eval-datasets/{datasetId}/regression")
    public ApiResponse<Object> runRegression(@PathVariable String datasetId,
                                             @RequestBody(required = false) RegressionRequest request) {
        advancedFeatureGate.requireEnabled(AdvancedFeature.AGENT_EVALUATION);
        String modelId = request != null ? request.modelId() : null;
        Double baselinePassRate = request != null ? request.baselinePassRate() : null;
        return ApiResponses.requireService(regressionServiceProvider,
                service -> service.runRegression(datasetId, modelId, baselinePassRate));
    }

    public record DecisionRequest(String note) {}

    public record RegressionRequest(String modelId, Double baselinePassRate) {}
}

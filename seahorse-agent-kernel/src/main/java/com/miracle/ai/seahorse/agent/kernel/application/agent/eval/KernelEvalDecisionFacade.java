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

package com.miracle.ai.seahorse.agent.kernel.application.agent.eval;

import com.miracle.ai.seahorse.agent.kernel.application.agent.eval.KernelEvalRegressionService.EvalReport;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.EvalCandidateDecisionInboundPort;

import java.util.Objects;

/**
 * Agent 评估候选决策与回归的组合入站实现。
 *
 * <p>将候选决策（{@link KernelEvalCandidateDecisionService}）与回归
 * （{@link KernelEvalRegressionService}）聚合到同一入站用例端口，供 Web 适配器
 * 通过契约依赖，避免 Web 直接依赖具体 Kernel 服务实现。</p>
 */
public class KernelEvalDecisionFacade implements EvalCandidateDecisionInboundPort {

    private final KernelEvalCandidateDecisionService decisionService;
    private final KernelEvalRegressionService regressionService;

    public KernelEvalDecisionFacade(KernelEvalCandidateDecisionService decisionService,
                                    KernelEvalRegressionService regressionService) {
        this.decisionService = Objects.requireNonNull(decisionService, "decisionService must not be null");
        this.regressionService = Objects.requireNonNull(regressionService, "regressionService must not be null");
    }

    @Override
    public void acceptCandidate(String candidateId, String reviewerNote) {
        decisionService.acceptCandidate(candidateId, reviewerNote);
    }

    @Override
    public void rejectCandidate(String candidateId, String reason) {
        decisionService.rejectCandidate(candidateId, reason);
    }

    @Override
    public EvalReport runRegression(String datasetId, String modelId, Double baselinePassRate) {
        return regressionService.runRegression(datasetId, modelId, baselinePassRate);
    }
}

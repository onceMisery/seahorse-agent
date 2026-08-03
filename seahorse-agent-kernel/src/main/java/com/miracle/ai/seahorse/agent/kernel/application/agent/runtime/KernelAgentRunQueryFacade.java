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

package com.miracle.ai.seahorse.agent.kernel.application.agent.runtime;

import com.miracle.ai.seahorse.agent.kernel.application.agent.cost.KernelAgentRunCostSummaryService;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunQueryInboundPort;

import java.util.List;
import java.util.Objects;

/**
 * Agent Run 查询与恢复的组合入站实现。
 *
 * <p>将 checkpoint 查询（{@link KernelAgentCheckpointQueryService}）、成本汇总
 * （{@link KernelAgentRunCostSummaryService}）与恢复（{@link KernelAgentRunResumeService}）
 * 聚合到同一入站用例端口，供 Web 适配器通过契约依赖。</p>
 */
public class KernelAgentRunQueryFacade implements AgentRunQueryInboundPort {

    private final KernelAgentCheckpointQueryService checkpointQueryService;
    private final KernelAgentRunCostSummaryService costSummaryService;
    private final KernelAgentRunResumeService resumeService;

    public KernelAgentRunQueryFacade(KernelAgentCheckpointQueryService checkpointQueryService,
                                     KernelAgentRunCostSummaryService costSummaryService,
                                     KernelAgentRunResumeService resumeService) {
        this.checkpointQueryService = Objects.requireNonNull(
                checkpointQueryService, "checkpointQueryService must not be null");
        this.costSummaryService = Objects.requireNonNull(costSummaryService, "costSummaryService must not be null");
        this.resumeService = resumeService;
    }

    public static KernelAgentRunQueryFacade withoutResume(KernelAgentCheckpointQueryService checkpointQueryService,
                                                          KernelAgentRunCostSummaryService costSummaryService) {
        return new KernelAgentRunQueryFacade(checkpointQueryService, costSummaryService, null);
    }

    @Override
    public List<AgentCheckpoint> listByRunId(String runId) {
        return checkpointQueryService.listByRunId(runId);
    }

    @Override
    public CostUsageAggregate getCostSummary(String runId) {
        return costSummaryService.getCostSummary(runId);
    }

    @Override
    public AgentRun resume(String runId) {
        if (resumeService == null) {
            throw new IllegalStateException("Agent run resume service is not available");
        }
        return resumeService.resume(runId);
    }
}

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

package com.miracle.ai.seahorse.agent.kernel.application.agent.rollout;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentRolloutCostSummary;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.rollout.AgentVersionRollout;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutActionCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutCreateCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRolloutRollbackCommand;

import java.util.Objects;
import java.util.Optional;

/**
 * Agent 发布操作与成本汇总的组合入站实现。
 *
 * <p>将发布操作（{@link KernelAgentRolloutService}）与发布成本汇总
 * （{@link KernelAgentRolloutCostSummaryService}）聚合到同一入站用例端口，
 * 供 Web 适配器通过契约依赖。</p>
 */
public class KernelAgentRolloutFacade implements AgentRolloutInboundPort {

    private final KernelAgentRolloutService rolloutService;
    private final KernelAgentRolloutCostSummaryService costSummaryService;

    public KernelAgentRolloutFacade(KernelAgentRolloutService rolloutService,
                                    KernelAgentRolloutCostSummaryService costSummaryService) {
        this.rolloutService = Objects.requireNonNull(rolloutService, "rolloutService must not be null");
        this.costSummaryService = Objects.requireNonNull(costSummaryService, "costSummaryService must not be null");
    }

    @Override
    public AgentVersionRollout createCanary(AgentRolloutCreateCommand command) {
        return rolloutService.createCanary(command);
    }

    @Override
    public AgentVersionRollout pause(AgentRolloutActionCommand command) {
        return rolloutService.pause(command);
    }

    @Override
    public AgentVersionRollout promote(AgentRolloutActionCommand command) {
        return rolloutService.promote(command);
    }

    @Override
    public AgentVersionRollout rollback(AgentRolloutRollbackCommand command) {
        return rolloutService.rollback(command);
    }

    @Override
    public Optional<AgentVersionRollout> latest(String tenantId, String agentId, String versionId) {
        return rolloutService.latest(tenantId, agentId, versionId);
    }

    @Override
    public AgentRolloutCostSummary getCostSummary(String tenantId, String agentId, String rolloutId) {
        return costSummaryService.getCostSummary(tenantId, agentId, rolloutId);
    }
}

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

package com.miracle.ai.seahorse.agent.kernel.application.agent.artifact;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactDownloadDecision;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentArtifactUpdateCommand;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Agent 工件查询与更新的组合入站实现。
 *
 * <p>将工件查询（{@link KernelAgentArtifactQueryService}）与工件内容更新
 * （{@link KernelAgentArtifactUpdateService}）聚合到同一入站用例端口，
 * 供 Web 适配器通过契约依赖。</p>
 */
public class KernelAgentArtifactFacade implements AgentArtifactInboundPort {

    private final KernelAgentArtifactQueryService queryService;
    private final KernelAgentArtifactUpdateService updateService;

    public KernelAgentArtifactFacade(KernelAgentArtifactQueryService queryService,
                                     KernelAgentArtifactUpdateService updateService) {
        this.queryService = Objects.requireNonNull(queryService, "queryService must not be null");
        this.updateService = Objects.requireNonNull(updateService, "updateService must not be null");
    }

    @Override
    public Optional<AgentArtifact> findById(String artifactId) {
        return queryService.findById(artifactId);
    }

    @Override
    public List<AgentArtifact> listByRunId(String runId) {
        return queryService.listByRunId(runId);
    }

    @Override
    public AgentArtifactDownloadDecision downloadDecision(String artifactId) {
        return queryService.downloadDecision(artifactId);
    }

    @Override
    public AgentArtifact updateContent(String artifactId, AgentArtifactUpdateCommand command) {
        return updateService.updateContent(artifactId, command);
    }
}

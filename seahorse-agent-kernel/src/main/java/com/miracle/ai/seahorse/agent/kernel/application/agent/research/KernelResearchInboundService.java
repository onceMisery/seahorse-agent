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

package com.miracle.ai.seahorse.agent.kernel.application.agent.research;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.research.ResearchTaskProfile;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResearchInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.ResearchStartCommand;

import java.util.Objects;

public class KernelResearchInboundService implements ResearchInboundPort {

    private final ResearchRunOrchestrator orchestrator;

    public KernelResearchInboundService(ResearchRunOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator, "orchestrator must not be null");
    }

    @Override
    public String startResearch(ResearchStartCommand command) {
        Objects.requireNonNull(command, "command must not be null");
        return orchestrator.startResearch(
                command.runId(),
                ResearchTaskProfile.defaultProfile(),
                command.query(),
                command.tenantId(),
                command.userId());
    }
}

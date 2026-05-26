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

package com.miracle.ai.seahorse.agent.ports.inbound.agent;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.approval.ApprovalRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.artifact.AgentArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;

import java.util.List;
import java.util.Optional;

public record AgentRunSnapshot(AgentRun run,
                               List<AgentRunSnapshotStep> steps,
                               Optional<AgentCheckpoint> latestCheckpoint,
                               AgentRunMessageSnapshot messageSnapshot,
                               String currentStepId,
                               List<AgentRunSnapshotSource> sources,
                               List<AgentArtifact> artifacts,
                               List<ApprovalRequest> pendingApprovals,
                               long lastEventSeq,
                               boolean canResume,
                               boolean canRetry) {

    public AgentRunSnapshot {
        steps = steps == null ? List.of() : List.copyOf(steps);
        latestCheckpoint = latestCheckpoint == null ? Optional.empty() : latestCheckpoint;
        messageSnapshot = messageSnapshot == null ? AgentRunMessageSnapshot.empty() : messageSnapshot;
        sources = sources == null ? List.of() : List.copyOf(sources);
        artifacts = artifacts == null ? List.of() : List.copyOf(artifacts);
        pendingApprovals = pendingApprovals == null ? List.of() : List.copyOf(pendingApprovals);
    }
}

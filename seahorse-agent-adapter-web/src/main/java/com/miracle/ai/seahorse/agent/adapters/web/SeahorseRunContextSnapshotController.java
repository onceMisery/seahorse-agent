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

import com.miracle.ai.seahorse.agent.ports.inbound.runcontext.RunContextSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * Web adapter for run context snapshot queries.
 */
@RestController
@RequiredArgsConstructor
public class SeahorseRunContextSnapshotController {

    @NonNull
    private final ObjectProvider<RunContextSnapshotInboundPort> snapshotPortProvider;

    @GetMapping({"/run-context-snapshots/by-run/{runId}", "/api/run-context-snapshots/by-run/{runId}"})
    public ApiResponse<RunContextSnapshotRecord> findByRunId(@PathVariable String runId) {
        return ApiResponses.requireService(snapshotPortProvider, port -> port.findByRunId(runId)
                .orElseThrow(() -> new ResourceNotFoundException("Run context snapshot not found")));
    }

    @GetMapping({"/agent-runs/{runId}/context-snapshot", "/api/agent-runs/{runId}/context-snapshot"})
    public ApiResponse<RunContextSnapshotRecord> findAgentRunContextSnapshot(@PathVariable String runId) {
        return findByRunId(runId);
    }
}

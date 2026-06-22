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

package com.miracle.ai.seahorse.agent.kernel.application.runcontext;

import com.miracle.ai.seahorse.agent.ports.inbound.runcontext.RunContextSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import lombok.RequiredArgsConstructor;

import java.util.Optional;

/**
 * Kernel query service for persisted run context snapshots.
 */
@RequiredArgsConstructor
public class KernelRunContextSnapshotService implements RunContextSnapshotInboundPort {

    private final RunContextSnapshotRepositoryPort repositoryPort;

    @Override
    public Optional<RunContextSnapshotRecord> findByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        return repositoryPort.findByRunId(runId.trim());
    }
}

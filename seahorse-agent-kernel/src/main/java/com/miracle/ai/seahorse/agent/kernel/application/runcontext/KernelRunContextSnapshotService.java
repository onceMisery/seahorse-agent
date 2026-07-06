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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.ports.inbound.runcontext.RunContextSnapshotInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;

import java.util.Objects;
import java.util.Optional;

/**
 * Kernel query service for persisted run context snapshots.
 */
public class KernelRunContextSnapshotService implements RunContextSnapshotInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "鏉冮檺涓嶈冻";

    private final RunContextSnapshotRepositoryPort repositoryPort;
    private final AgentRunRepositoryPort runRepository;
    private final CurrentUserPort currentUserPort;

    public KernelRunContextSnapshotService(RunContextSnapshotRepositoryPort repositoryPort) {
        this(repositoryPort, null, null);
    }

    public KernelRunContextSnapshotService(RunContextSnapshotRepositoryPort repositoryPort,
                                           AgentRunRepositoryPort runRepository,
                                           CurrentUserPort currentUserPort) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.runRepository = runRepository;
        this.currentUserPort = currentUserPort;
    }

    @Override
    public Optional<RunContextSnapshotRecord> findByRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        String safeRunId = runId.trim();
        requireReadableAgentRunIfPresent(safeRunId);
        return repositoryPort.findByRunId(safeRunId);
    }

    private void requireReadableAgentRunIfPresent(String runId) {
        if (runRepository == null || currentUserPort == null) {
            return;
        }
        Optional<AgentRun> maybeRun = runRepository.findRunById(runId);
        if (maybeRun.isEmpty()) {
            return;
        }
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        AgentRun run = maybeRun.orElseThrow();
        if (isAdmin(currentUser) || ownsRun(run, currentUser)) {
            return;
        }
        throw new IllegalStateException(ACCESS_DENIED);
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.hasRole(ADMIN_ROLE);
    }

    private String currentUserId(CurrentUser currentUser) {
        return currentUser == null ? null : currentUser.operator();
    }

    private boolean ownsRun(AgentRun run, CurrentUser currentUser) {
        if (currentUser == null) {
            return false;
        }
        String numericUserId = currentUser.userId() == null ? null : String.valueOf(currentUser.userId());
        return Objects.equals(run.userId(), numericUserId)
                || Objects.equals(run.userId(), currentUserId(currentUser));
    }
}

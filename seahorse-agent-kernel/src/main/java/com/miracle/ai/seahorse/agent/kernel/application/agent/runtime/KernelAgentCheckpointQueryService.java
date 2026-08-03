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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentCheckpoint;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunQueryInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentCheckpointRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.util.List;
import java.util.Objects;

public class KernelAgentCheckpointQueryService {

    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "\u6743\u9650\u4e0d\u8db3";

    private final AgentRunRepositoryPort runRepository;
    private final AgentCheckpointRepositoryPort checkpointRepository;
    private final CurrentUserPort currentUserPort;
    private final AgentCheckpointViewSanitizer checkpointViewSanitizer;

    public KernelAgentCheckpointQueryService(AgentRunRepositoryPort runRepository,
                                             AgentCheckpointRepositoryPort checkpointRepository,
                                             CurrentUserPort currentUserPort) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.checkpointRepository = Objects.requireNonNull(
                checkpointRepository,
                "checkpointRepository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
        this.checkpointViewSanitizer = new AgentCheckpointViewSanitizer(null);
    }
    public List<AgentCheckpoint> listByRunId(String runId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeRunId = requireText(runId, "runId must not be blank");
        AgentRun run = runRepository.findRunById(safeRunId)
                .orElseThrow(() -> new IllegalArgumentException("Agent run not found"));
        requireReadable(run, currentUser);
        return checkpointRepository.listByRunId(safeRunId).stream()
                .map(checkpointViewSanitizer::checkpointForView)
                .toList();
    }

    private void requireReadable(AgentRun run, CurrentUser currentUser) {
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

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

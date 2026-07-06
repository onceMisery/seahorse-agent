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

package com.miracle.ai.seahorse.agent.kernel.application.workflow;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.workflow.ExecutionStepAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.ports.inbound.workflow.WorkflowVisualizationInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;
import com.miracle.ai.seahorse.agent.ports.outbound.workflow.WorkflowVisualizationRepositoryPort;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Kernel service implementing workflow visualization.
 *
 * <p>Loads execution steps from the repository, sorts them by
 * {@code startedAt} timestamp, and derives sequential edges between
 * consecutive steps.
 */
public class KernelWorkflowVisualizationService implements WorkflowVisualizationInboundPort {

    private static final Logger log = LoggerFactory.getLogger(KernelWorkflowVisualizationService.class);
    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "\u6743\u9650\u4e0d\u8db3";
    private static final String RUN_NOT_FOUND = "Agent run not found";
    private static final String EDGE_TYPE_SEQUENTIAL = "SEQUENTIAL";

    private final WorkflowVisualizationRepositoryPort repository;
    private final AgentRunRepositoryPort runRepository;
    private final CurrentUserPort currentUserPort;

    /**
     * Create the service with the given repository port.
     *
     * @param repository the outbound port for step persistence
     */
    public KernelWorkflowVisualizationService(WorkflowVisualizationRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.runRepository = null;
        this.currentUserPort = null;
    }

    public KernelWorkflowVisualizationService(WorkflowVisualizationRepositoryPort repository,
                                              AgentRunRepositoryPort runRepository,
                                              CurrentUserPort currentUserPort) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.runRepository = runRepository;
        this.currentUserPort = currentUserPort;
    }

    @Override
    public WorkflowVisualization getVisualization(String runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        String safeRunId = requireText(runId, "runId must not be blank");
        requireReadableRunIfConfigured(safeRunId);
        List<ExecutionStepAggregate> steps = repository.findByRunId(safeRunId);
        if (steps == null || steps.isEmpty()) {
            log.debug("No execution steps found for runId [{}]", safeRunId);
            return new WorkflowVisualization(List.of(), List.of());
        }

        List<ExecutionStepAggregate> sortedSteps = steps.stream()
                .sorted(Comparator.comparing(
                        s -> s.startedAt() != null ? s.startedAt() : java.time.Instant.EPOCH))
                .toList();

        List<StepEdge> edges = buildSequentialEdges(sortedSteps);

        log.debug("Built visualization for runId [{}]: {} nodes, {} edges",
                safeRunId, sortedSteps.size(), edges.size());
        return new WorkflowVisualization(sortedSteps, edges);
    }

    private void requireReadableRunIfConfigured(String runId) {
        if (runRepository == null || currentUserPort == null) {
            return;
        }
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        runRepository.findRunById(runId)
                .map(run -> {
                    if (isAdmin(currentUser) || ownsRun(run, currentUser)) {
                        return run;
                    }
                    throw new IllegalStateException(ACCESS_DENIED);
                })
                .orElseThrow(() -> new IllegalArgumentException(RUN_NOT_FOUND));
    }

    private List<StepEdge> buildSequentialEdges(List<ExecutionStepAggregate> sortedSteps) {
        if (sortedSteps.size() < 2) {
            return List.of();
        }
        List<StepEdge> edges = new ArrayList<>(sortedSteps.size() - 1);
        for (int i = 0; i < sortedSteps.size() - 1; i++) {
            edges.add(new StepEdge(
                    sortedSteps.get(i).stepId(),
                    sortedSteps.get(i + 1).stepId(),
                    EDGE_TYPE_SEQUENTIAL));
        }
        return List.copyOf(edges);
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

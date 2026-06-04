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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRun;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStep;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentStepStatus;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkflow;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkflowEdge;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkflowInboundPort;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkflowNode;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkflowNodeData;
import com.miracle.ai.seahorse.agent.ports.inbound.agent.AgentRunWorkflowPosition;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUser;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.CurrentUserPort;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class KernelAgentRunWorkflowService implements AgentRunWorkflowInboundPort {

    private static final String ADMIN_ROLE = "admin";
    private static final String ACCESS_DENIED = "Access denied";
    private static final String NODE_TYPE = "workflowStep";
    private static final String EDGE_TYPE = "smoothstep";
    private static final double NODE_WIDTH = 220D;
    private static final double NODE_HEIGHT = 96D;
    private static final double HORIZONTAL_GAP = 72D;
    private static final double VERTICAL_GAP = 72D;
    private static final int COLUMNS = 4;

    private final AgentRunRepositoryPort runRepository;
    private final CurrentUserPort currentUserPort;

    public KernelAgentRunWorkflowService(AgentRunRepositoryPort runRepository, CurrentUserPort currentUserPort) {
        this.runRepository = Objects.requireNonNull(runRepository, "runRepository must not be null");
        this.currentUserPort = Objects.requireNonNull(currentUserPort, "currentUserPort must not be null");
    }

    @Override
    public Optional<AgentRunWorkflow> findWorkflow(String runId) {
        CurrentUser currentUser = currentUserPort.requireCurrentUser();
        String safeRunId = requireText(runId, "runId must not be blank");
        return runRepository.findRunById(safeRunId)
                .map(run -> workflowFor(requireReadable(run, currentUser)));
    }

    private AgentRunWorkflow workflowFor(AgentRun run) {
        List<AgentStep> steps = runRepository.listSteps(run.runId()).stream()
                .sorted(Comparator.comparingInt(AgentStep::stepNo))
                .toList();
        List<AgentRunWorkflowNode> nodes = new ArrayList<>(steps.size());
        for (int i = 0; i < steps.size(); i++) {
            nodes.add(nodeFor(steps.get(i), i));
        }
        return new AgentRunWorkflow(run.runId(), currentStepId(steps), nodes, edgesFor(nodes));
    }

    private AgentRunWorkflowNode nodeFor(AgentStep step, int index) {
        int row = index / COLUMNS;
        int column = index % COLUMNS;
        return new AgentRunWorkflowNode(
                step.stepId(),
                NODE_TYPE,
                new AgentRunWorkflowPosition(
                        column * (NODE_WIDTH + HORIZONTAL_GAP),
                        row * (NODE_HEIGHT + VERTICAL_GAP)),
                new AgentRunWorkflowNodeData(
                        labelFor(step),
                        normalizeStatus(step.status()),
                        firstText(step.errorMessage(), step.outputJson(), step.inputJson()),
                        durationMs(step),
                        step.stepType().name(),
                        step.stepNo(),
                        step.errorMessage(),
                        step.startedAt(),
                        step.finishedAt()));
    }

    private List<AgentRunWorkflowEdge> edgesFor(List<AgentRunWorkflowNode> nodes) {
        if (nodes.size() < 2) {
            return List.of();
        }
        List<AgentRunWorkflowEdge> edges = new ArrayList<>(nodes.size() - 1);
        for (int i = 0; i < nodes.size() - 1; i++) {
            AgentRunWorkflowNode source = nodes.get(i);
            AgentRunWorkflowNode target = nodes.get(i + 1);
            edges.add(new AgentRunWorkflowEdge(
                    source.id() + "-" + target.id(),
                    source.id(),
                    target.id(),
                    EDGE_TYPE,
                    "RUNNING".equalsIgnoreCase(target.data().status()),
                    null));
        }
        return edges;
    }

    private String currentStepId(List<AgentStep> steps) {
        return steps.stream()
                .filter(step -> step.status() == AgentStepStatus.RUNNING)
                .map(AgentStep::stepId)
                .findFirst()
                .or(() -> steps.stream()
                        .max(Comparator.comparingInt(AgentStep::stepNo))
                        .map(AgentStep::stepId))
                .orElse(null);
    }

    private String labelFor(AgentStep step) {
        String summary = firstText(step.outputJson(), step.inputJson(), step.errorMessage());
        if (summary != null && summary.length() <= 80) {
            return summary;
        }
        return step.stepType().name();
    }

    private String normalizeStatus(AgentStepStatus status) {
        if (status == AgentStepStatus.SUCCEEDED) {
            return "COMPLETED";
        }
        return status.name();
    }

    private Long durationMs(AgentStep step) {
        if (step.startedAt() == null || step.finishedAt() == null) {
            return null;
        }
        long millis = Duration.between(step.startedAt(), step.finishedAt()).toMillis();
        return millis < 0L ? null : millis;
    }

    private AgentRun requireReadable(AgentRun run, CurrentUser currentUser) {
        if (isAdmin(currentUser) || run.userId().equals(currentUserId(currentUser))) {
            return run;
        }
        throw new IllegalStateException(ACCESS_DENIED);
    }

    private boolean isAdmin(CurrentUser currentUser) {
        return currentUser != null && currentUser.hasRole(ADMIN_ROLE);
    }

    private String currentUserId(CurrentUser currentUser) {
        return currentUser == null ? null : currentUser.operator();
    }

    private String firstText(String... values) {
        for (String value : values) {
            if (value != null && !value.trim().isEmpty()) {
                return value.trim();
            }
        }
        return null;
    }

    private String requireText(String value, String message) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }
}

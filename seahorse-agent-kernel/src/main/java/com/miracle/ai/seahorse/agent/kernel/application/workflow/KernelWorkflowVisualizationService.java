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
import com.miracle.ai.seahorse.agent.ports.inbound.workflow.WorkflowVisualizationInboundPort;
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
    private static final String EDGE_TYPE_SEQUENTIAL = "SEQUENTIAL";

    private final WorkflowVisualizationRepositoryPort repository;

    /**
     * Create the service with the given repository port.
     *
     * @param repository the outbound port for step persistence
     */
    public KernelWorkflowVisualizationService(WorkflowVisualizationRepositoryPort repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    @Override
    public WorkflowVisualization getVisualization(String runId) {
        Objects.requireNonNull(runId, "runId must not be null");
        List<ExecutionStepAggregate> steps = repository.findByRunId(runId);
        if (steps == null || steps.isEmpty()) {
            log.debug("No execution steps found for runId [{}]", runId);
            return new WorkflowVisualization(List.of(), List.of());
        }

        List<ExecutionStepAggregate> sortedSteps = steps.stream()
                .sorted(Comparator.comparing(
                        s -> s.startedAt() != null ? s.startedAt() : java.time.Instant.EPOCH))
                .toList();

        List<StepEdge> edges = buildSequentialEdges(sortedSteps);

        log.debug("Built visualization for runId [{}]: {} nodes, {} edges",
                runId, sortedSteps.size(), edges.size());
        return new WorkflowVisualization(sortedSteps, edges);
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
}

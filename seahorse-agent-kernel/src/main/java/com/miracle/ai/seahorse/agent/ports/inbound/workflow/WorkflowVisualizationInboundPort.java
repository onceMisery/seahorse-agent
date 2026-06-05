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

package com.miracle.ai.seahorse.agent.ports.inbound.workflow;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.workflow.ExecutionStepAggregate;

import java.util.List;

/**
 * Inbound port for workflow visualization queries.
 *
 * <p>Provides the ability to retrieve a complete visualization graph
 * (nodes + edges) for a given workflow run.
 */
public interface WorkflowVisualizationInboundPort {

    /**
     * Retrieve the full visualization for a workflow run.
     *
     * @param runId the unique workflow run identifier
     * @return a visualization containing all step nodes and their connecting edges
     */
    WorkflowVisualization getVisualization(String runId);

    /**
     * Complete workflow visualization result.
     *
     * @param nodes the execution steps rendered as graph nodes
     * @param edges the directed connections between steps
     */
    record WorkflowVisualization(
            List<ExecutionStepAggregate> nodes,
            List<StepEdge> edges) {
    }

    /**
     * A directed edge between two execution steps.
     *
     * @param sourceStepId the originating step
     * @param targetStepId the destination step
     * @param edgeType     the relationship type (e.g. SEQUENTIAL, PARALLEL, ERROR_FALLBACK)
     */
    record StepEdge(
            String sourceStepId,
            String targetStepId,
            String edgeType) {
    }
}

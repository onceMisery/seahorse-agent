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

package com.miracle.ai.seahorse.agent.ports.outbound.workflow;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.workflow.ExecutionStepAggregate;

import java.time.Instant;
import java.util.List;

/**
 * Outbound port for persisting and querying workflow execution steps.
 *
 * <p>Implementations typically delegate to a relational database
 * (e.g. via JDBC) to store step data for workflow visualization.
 */
public interface WorkflowVisualizationRepositoryPort {

    /**
     * Find all execution steps for the given workflow run.
     *
     * @param runId the workflow run identifier
     * @return the list of steps ordered by start time, or an empty list
     */
    List<ExecutionStepAggregate> findByRunId(String runId);

    /**
     * Persist a new execution step.
     *
     * @param step the step to save
     */
    void saveStep(ExecutionStepAggregate step);

    /**
     * Update the status and completion metadata of an existing step.
     *
     * @param stepId      the step identifier
     * @param status      the new status value
     * @param completedAt the completion timestamp (may be null if still running)
     * @param durationMs  the elapsed duration in milliseconds (may be null)
     */
    void updateStepStatus(String stepId, String status, Instant completedAt, Long durationMs);
}

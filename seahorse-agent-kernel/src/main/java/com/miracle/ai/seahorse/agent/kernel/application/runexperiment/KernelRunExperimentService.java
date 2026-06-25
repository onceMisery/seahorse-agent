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

package com.miracle.ai.seahorse.agent.kernel.application.runexperiment;

import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentInboundPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialRecord;
import lombok.NonNull;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Supplier;

public class KernelRunExperimentService implements RunExperimentInboundPort {

    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_SUCCEEDED = "SUCCEEDED";
    private static final String STATUS_FAILED = "FAILED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    @NonNull
    private final RunExperimentRepositoryPort repositoryPort;
    @NonNull
    private final Supplier<RunExperimentTrialExecutorPort> trialExecutorPortSupplier;

    public KernelRunExperimentService(RunExperimentRepositoryPort repositoryPort) {
        this(repositoryPort, RunExperimentTrialExecutorPort.noop());
    }

    public KernelRunExperimentService(
            RunExperimentRepositoryPort repositoryPort,
            RunExperimentTrialExecutorPort trialExecutorPort) {
        this(repositoryPort, () -> trialExecutorPort);
    }

    public KernelRunExperimentService(
            RunExperimentRepositoryPort repositoryPort,
            Supplier<RunExperimentTrialExecutorPort> trialExecutorPortSupplier) {
        this.repositoryPort = Objects.requireNonNull(repositoryPort, "repositoryPort must not be null");
        this.trialExecutorPortSupplier = Objects.requireNonNullElseGet(
                trialExecutorPortSupplier,
                () -> RunExperimentTrialExecutorPort::noop);
    }

    @Override
    public RunExperimentDetails create(RunExperimentCommand command) {
        RunExperimentCommand safeCommand = Objects.requireNonNull(command, "command must not be null");
        String userId = requireText(safeCommand.getUserId(), "userId must not be blank");
        String name = requireText(safeCommand.getName(), "name must not be blank");
        if (safeCommand.getConversationId() == null) {
            throw new IllegalArgumentException("conversationId must not be null");
        }
        List<Long> runProfileIds = Objects.requireNonNullElse(safeCommand.getRunProfileIds(), List.<Long>of())
                .stream()
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (runProfileIds.isEmpty()) {
            throw new IllegalArgumentException("runProfileIds must not be empty");
        }
        RunExperimentRecord experiment = RunExperimentRecord.builder()
                .userId(userId)
                .conversationId(safeCommand.getConversationId())
                .baseLeafMessageId(safeCommand.getBaseLeafMessageId())
                .name(name)
                .status(STATUS_PENDING)
                .deleted(0)
                .build();
        List<RunExperimentTrialRecord> trials = runProfileIds.stream()
                .map(runProfileId -> RunExperimentTrialRecord.builder()
                        .runProfileId(runProfileId)
                        .status(STATUS_PENDING)
                        .deleted(0)
                        .build())
                .toList();
        RunExperimentDetails created = repositoryPort.create(experiment, trials);
        RunExperimentTrialExecutorPort trialExecutorPort = resolveTrialExecutor();
        if (!trialExecutorPort.enabled()) {
            return created;
        }
        return executeCreatedExperiment(userId, created, trialExecutorPort);
    }

    @Override
    public Optional<RunExperimentDetails> findById(String userId, Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return repositoryPort.findById(requireText(userId, "userId must not be blank"), id);
    }

    @Override
    public RunExperimentDetails cancel(String userId, Long id) {
        if (id == null) {
            throw new IllegalArgumentException("experimentId must not be null");
        }
        return repositoryPort.updateExperimentStatus(
                        requireText(userId, "userId must not be blank"),
                        id,
                        STATUS_CANCELLED)
                .orElseThrow(() -> new IllegalArgumentException("run experiment not found"));
    }

    @Override
    public RunExperimentDetails scoreTrial(String userId, Long experimentId, Long trialId, String scoreJson) {
        if (experimentId == null) {
            throw new IllegalArgumentException("experimentId must not be null");
        }
        if (trialId == null) {
            throw new IllegalArgumentException("trialId must not be null");
        }
        return repositoryPort.updateTrialScore(
                        requireText(userId, "userId must not be blank"),
                        experimentId,
                        trialId,
                        requireText(scoreJson, "scoreJson must not be blank"))
                .orElseThrow(() -> new IllegalArgumentException("run experiment trial not found"));
    }

    private String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private RunExperimentTrialExecutorPort resolveTrialExecutor() {
        return Objects.requireNonNullElseGet(
                trialExecutorPortSupplier.get(),
                RunExperimentTrialExecutorPort::noop);
    }

    private RunExperimentDetails executeCreatedExperiment(
            String userId,
            RunExperimentDetails created,
            RunExperimentTrialExecutorPort trialExecutorPort) {
        RunExperimentRecord experiment = created.getExperiment();
        repositoryPort.updateExperimentOnlyStatus(userId, experiment.getId(), STATUS_RUNNING);
        boolean failed = false;
        for (RunExperimentTrialRecord trial : created.getTrials()) {
            RunExperimentTrialExecutionResult result = executeTrial(userId, experiment, trial, trialExecutorPort);
            String trialStatus = normalizeTrialStatus(result.getStatus());
            if (STATUS_FAILED.equals(trialStatus)) {
                failed = true;
            }
            repositoryPort.updateTrialExecution(
                    userId,
                    experiment.getId(),
                    trial.getId(),
                    trialStatus,
                    result.getRunId(),
                    result.getOutputMessageId(),
                    result.getMetricJson(),
                    result.getErrorMessage());
        }
        String finalStatus = failed ? STATUS_FAILED : STATUS_SUCCEEDED;
        return repositoryPort.updateExperimentOnlyStatus(userId, experiment.getId(), finalStatus)
                .orElseGet(() -> repositoryPort.findById(userId, experiment.getId()).orElse(created));
    }

    private RunExperimentTrialExecutionResult executeTrial(
            String userId,
            RunExperimentRecord experiment,
            RunExperimentTrialRecord trial,
            RunExperimentTrialExecutorPort trialExecutorPort) {
        try {
            return Objects.requireNonNullElseGet(
                    trialExecutorPort.execute(RunExperimentTrialExecutionRequest.builder()
                            .userId(userId)
                            .experimentId(experiment.getId())
                            .trialId(trial.getId())
                            .conversationId(experiment.getConversationId())
                            .baseLeafMessageId(experiment.getBaseLeafMessageId())
                            .runProfileId(trial.getRunProfileId())
                            .experimentName(experiment.getName())
                            .build()),
                    () -> RunExperimentTrialExecutionResult.builder()
                            .status(STATUS_FAILED)
                            .errorMessage("trial executor returned null")
                            .build());
        } catch (RuntimeException ex) {
            return RunExperimentTrialExecutionResult.builder()
                    .status(STATUS_FAILED)
                    .errorMessage(Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getSimpleName()))
                    .build();
        }
    }

    private String normalizeTrialStatus(String status) {
        if (status == null || status.isBlank()) {
            return STATUS_SUCCEEDED;
        }
        String normalized = status.trim().toUpperCase();
        return switch (normalized) {
            case STATUS_PENDING, STATUS_RUNNING, STATUS_SUCCEEDED, STATUS_FAILED, STATUS_CANCELLED -> normalized;
            default -> STATUS_FAILED;
        };
    }
}

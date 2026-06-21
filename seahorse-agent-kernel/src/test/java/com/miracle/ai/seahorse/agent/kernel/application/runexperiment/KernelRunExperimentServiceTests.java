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
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class KernelRunExperimentServiceTests {

    @Test
    void shouldCreateExperimentWithPendingTrialsForEachRunProfile() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        KernelRunExperimentService service = new KernelRunExperimentService(repository);

        RunExperimentDetails details = service.create(RunExperimentCommand.builder()
                .userId("100")
                .conversationId(101L)
                .baseLeafMessageId(202L)
                .name("Profile compare")
                .runProfileIds(List.of(12L, 13L))
                .build());

        assertEquals("100", details.getExperiment().getUserId());
        assertEquals(101L, details.getExperiment().getConversationId());
        assertEquals(202L, details.getExperiment().getBaseLeafMessageId());
        assertEquals("PENDING", details.getExperiment().getStatus());
        assertIterableEquals(List.of(12L, 13L), details.getTrials()
                .stream()
                .map(RunExperimentTrialRecord::getRunProfileId)
                .toList());
        assertIterableEquals(List.of("PENDING", "PENDING"), details.getTrials()
                .stream()
                .map(RunExperimentTrialRecord::getStatus)
                .toList());
    }

    @Test
    void shouldCancelExperimentAndScoreTrial() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        KernelRunExperimentService service = new KernelRunExperimentService(repository);
        RunExperimentDetails created = service.create(RunExperimentCommand.builder()
                .userId("100")
                .conversationId(101L)
                .name("Profile compare")
                .runProfileIds(List.of(12L))
                .build());

        RunExperimentDetails scored = service.scoreTrial(
                "100",
                created.getExperiment().getId(),
                created.getTrials().get(0).getId(),
                "{\"rating\":5}");
        RunExperimentDetails cancelled = service.cancel("100", created.getExperiment().getId());

        assertEquals("{\"rating\":5}", scored.getTrials().get(0).getScoreJson());
        assertEquals("CANCELLED", cancelled.getExperiment().getStatus());
        assertEquals("CANCELLED", cancelled.getTrials().get(0).getStatus());
    }

    @Test
    void shouldExecuteTrialsAndPersistOutputWhenExecutorIsAvailable() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        AtomicReference<RunExperimentTrialExecutionRequest> captured = new AtomicReference<>();
        RunExperimentTrialExecutorPort executor = request -> {
            captured.set(request);
            return RunExperimentTrialExecutionResult.builder()
                    .status("SUCCEEDED")
                    .runId("run-exp-1-trial-10")
                    .outputMessageId(301L)
                    .metricJson("{\"elapsedMs\":12}")
                    .build();
        };
        KernelRunExperimentService service = new KernelRunExperimentService(repository, executor);

        RunExperimentDetails details = service.create(RunExperimentCommand.builder()
                .userId("100")
                .conversationId(101L)
                .baseLeafMessageId(202L)
                .name("Profile compare")
                .runProfileIds(List.of(12L))
                .build());

        assertEquals("SUCCEEDED", details.getExperiment().getStatus());
        assertEquals("SUCCEEDED", details.getTrials().get(0).getStatus());
        assertEquals("run-exp-1-trial-10", details.getTrials().get(0).getRunId());
        assertEquals(301L, details.getTrials().get(0).getOutputMessageId());
        assertEquals("{\"elapsedMs\":12}", details.getTrials().get(0).getMetricJson());
        assertEquals(12L, captured.get().getRunProfileId());
        assertEquals(202L, captured.get().getBaseLeafMessageId());
    }

    private static final class InMemoryRunExperimentRepository implements RunExperimentRepositoryPort {

        private RunExperimentDetails details;

        @Override
        public RunExperimentDetails create(RunExperimentRecord experiment, List<RunExperimentTrialRecord> trials) {
            experiment.setId(1L);
            List<RunExperimentTrialRecord> savedTrials = new ArrayList<>();
            long trialId = 10L;
            for (RunExperimentTrialRecord trial : trials) {
                trial.setId(trialId++);
                trial.setExperimentId(experiment.getId());
                savedTrials.add(trial);
            }
            details = RunExperimentDetails.builder()
                    .experiment(experiment)
                    .trials(savedTrials)
                    .build();
            return details;
        }

        @Override
        public Optional<RunExperimentDetails> findById(String userId, Long id) {
            return Optional.ofNullable(details);
        }

        @Override
        public Optional<RunExperimentDetails> updateExperimentStatus(String userId, Long id, String status) {
            details.getExperiment().setStatus(status);
            details.getTrials().forEach(trial -> trial.setStatus(status));
            return Optional.of(details);
        }

        @Override
        public Optional<RunExperimentDetails> updateTrialScore(
                String userId,
                Long experimentId,
                Long trialId,
                String scoreJson) {
            details.getTrials().stream()
                    .filter(trial -> trialId.equals(trial.getId()))
                    .findFirst()
                    .orElseThrow()
                    .setScoreJson(scoreJson);
            return Optional.of(details);
        }

        @Override
        public Optional<RunExperimentDetails> updateExperimentOnlyStatus(String userId, Long id, String status) {
            details.getExperiment().setStatus(status);
            return Optional.of(details);
        }

        @Override
        public Optional<RunExperimentDetails> updateTrialExecution(
                String userId,
                Long experimentId,
                Long trialId,
                String status,
                String runId,
                Long outputMessageId,
                String metricJson,
                String errorMessage) {
            RunExperimentTrialRecord trial = details.getTrials().stream()
                    .filter(candidate -> trialId.equals(candidate.getId()))
                    .findFirst()
                    .orElseThrow();
            trial.setStatus(status);
            trial.setRunId(runId);
            trial.setOutputMessageId(outputMessageId);
            trial.setMetricJson(metricJson);
            trial.setErrorMessage(errorMessage);
            return Optional.of(details);
        }
    }
}

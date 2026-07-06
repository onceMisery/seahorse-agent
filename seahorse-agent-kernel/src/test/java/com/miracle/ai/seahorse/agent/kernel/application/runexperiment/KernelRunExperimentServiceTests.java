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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageAggregate;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.cost.CostUsageRecord;
import com.miracle.ai.seahorse.agent.kernel.application.agent.ReActExecutorPort;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.AgentLoopResult;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCallback;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.StreamCancellationHandle;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.CostUsageRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentCommand;
import com.miracle.ai.seahorse.agent.ports.inbound.runexperiment.RunExperimentReport;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchCursor;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentDetails;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialExecutorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runexperiment.RunExperimentTrialRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void shouldRedactCredentialTextBeforePersistingExperimentName() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        KernelRunExperimentService service = new KernelRunExperimentService(repository);

        RunExperimentDetails details = service.create(RunExperimentCommand.builder()
                .userId("100")
                .conversationId(101L)
                .name("Profile compare api_key=experiment-name-secret")
                .runProfileIds(List.of(12L))
                .build());

        assertEquals("Profile compare [REDACTED]", details.getExperiment().getName());
        assertEquals("Profile compare [REDACTED]", repository.details.getExperiment().getName());
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

    @Test
    void shouldResolveTrialExecutorLazilyWhenExperimentIsCreated() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        AtomicReference<RunExperimentTrialExecutorPort> executorRef =
                new AtomicReference<>(RunExperimentTrialExecutorPort.noop());
        KernelRunExperimentService service = new KernelRunExperimentService(repository, executorRef::get);
        executorRef.set(request -> RunExperimentTrialExecutionResult.builder()
                .status("SUCCEEDED")
                .runId("run-exp-1-trial-10")
                .outputMessageId(301L)
                .metricJson("{\"elapsedMs\":12}")
                .build());

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
    }

    @Test
    void shouldKeepSuccessfulTrialEvidenceWhenAnotherTrialFails() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        RunExperimentTrialExecutorPort executor = request -> {
            if (Long.valueOf(12L).equals(request.getRunProfileId())) {
                return RunExperimentTrialExecutionResult.builder()
                        .status("SUCCEEDED")
                        .runId("run-exp-1-trial-10")
                        .outputMessageId(301L)
                        .metricJson("{\"cost\":0.11,\"traceId\":\"trace-success\"}")
                        .build();
            }
            throw new IllegalStateException("AgentScope timeout");
        };
        KernelRunExperimentService service = new KernelRunExperimentService(repository, executor);

        RunExperimentDetails details = service.create(RunExperimentCommand.builder()
                .userId("100")
                .conversationId(101L)
                .baseLeafMessageId(202L)
                .name("Profile compare")
                .runProfileIds(List.of(12L, 13L))
                .build());

        assertEquals("FAILED", details.getExperiment().getStatus());
        assertIterableEquals(List.of("SUCCEEDED", "FAILED"), details.getTrials()
                .stream()
                .map(RunExperimentTrialRecord::getStatus)
                .toList());
        RunExperimentTrialRecord succeeded = details.getTrials().get(0);
        assertEquals("run-exp-1-trial-10", succeeded.getRunId());
        assertEquals(301L, succeeded.getOutputMessageId());
        assertEquals("{\"cost\":0.11,\"traceId\":\"trace-success\"}", succeeded.getMetricJson());
        RunExperimentTrialRecord failed = details.getTrials().get(1);
        assertEquals("AgentScope timeout", failed.getErrorMessage());
    }

    @Test
    void shouldExportMarkdownReportWithTrialOutputsScoresAndFailureEvidence() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        InMemoryBranchRepository branchRepository = new InMemoryBranchRepository();
        RunExperimentTrialExecutorPort executor = request -> {
            if (Long.valueOf(12L).equals(request.getRunProfileId())) {
                return RunExperimentTrialExecutionResult.builder()
                        .status("SUCCEEDED")
                        .runId("run-exp-1-trial-10")
                        .outputMessageId(301L)
                        .metricJson("{\"cost\":0.11,\"traceId\":\"trace-success\"}")
                        .build();
            }
            throw new IllegalStateException("AgentScope timeout");
        };
        InMemoryRunContextSnapshotRepository snapshotRepository = new InMemoryRunContextSnapshotRepository();
        FixedCostUsageRepository costRepository = new FixedCostUsageRepository();
        KernelRunExperimentService service = new KernelRunExperimentService(
                repository,
                () -> executor,
                branchRepository,
                snapshotRepository,
                costRepository);

        RunExperimentDetails details = service.create(RunExperimentCommand.builder()
                .userId("100")
                .conversationId(101L)
                .baseLeafMessageId(202L)
                .name("Profile compare")
                .runProfileIds(List.of(12L, 13L))
                .build());
        RunContextSnapshotRecord snapshot = new RunContextSnapshotRecord();
        snapshot.setTenantId("default");
        snapshot.setRunId("run-exp-1-trial-10");
        snapshot.setTraceContextJson("""
                {"traceId":"trace-success","studioTraceId":"studio-success","studioUrl":"http://studio.local"}
                """);
        snapshotRepository.snapshots.put("run-exp-1-trial-10", snapshot);
        branchRepository.add(message("301", "101", "100", "assistant", "Kernel output with audit trail",
                202L, 202L, 1));
        service.scoreTrial("100", details.getExperiment().getId(), details.getTrials().get(0).getId(),
                "{\"rating\":4,\"verdict\":\"smoke-pass\"}");

        RunExperimentReport report = service.exportReport("100", details.getExperiment().getId());

        assertEquals("profile-compare-1.md", report.fileName());
        assertEquals("text/markdown; charset=UTF-8", report.contentType());
        assertTrue(report.markdown().contains("# Run Experiment Report: Profile compare"));
        assertTrue(report.markdown().contains("Template Version: run-experiment-report-v1"));
        assertTrue(report.markdown().contains("## Executive Summary"));
        assertTrue(report.markdown().contains("Recommended trial: trial 10 score=4"));
        assertTrue(report.markdown().contains("### Score Leaderboard"));
        assertTrue(report.markdown().contains("| Rank | Trial | Run Profile | Score | Status | Score Evidence |"));
        assertTrue(report.markdown().contains("| 1 | 10 | 12 | 4 | SUCCEEDED | verdict=smoke-pass |"));
        assertTrue(report.markdown().contains("## Cost Summary"));
        assertTrue(report.markdown().contains("- Costed trials: 1"));
        assertTrue(report.markdown().contains("- Total cost: 0.42"));
        assertTrue(report.markdown().contains("- Total tokens: 123"));
        assertTrue(report.markdown().contains("- Total calls: 2"));
        assertTrue(report.markdown().contains("- Cost records: 1"));
        assertTrue(report.markdown().contains("## Trace Summary"));
        assertTrue(report.markdown().contains("- Traced trials: 1"));
        assertTrue(report.markdown().contains("| Trial | Run ID | Trace Evidence |"));
        assertTrue(report.markdown().contains(
                "| 10 | run-exp-1-trial-10 | studio=[studio-success](http://studio.local/traces/studio-success) |"));
        assertTrue(report.markdown().contains("## Evidence Index"));
        assertTrue(report.markdown().contains("## Reproduction Appendix"));
        assertTrue(report.markdown().contains("run-exp-1-trial-10"));
        assertTrue(report.markdown().contains("message:301"));
        assertTrue(report.markdown().contains("Message Branch"));
        assertTrue(report.markdown().contains("leaf=301 parent=202 root=202 sibling=1"));
        assertTrue(report.markdown().contains("Trial branch leaves: trial 10 -> leaf=301 parent=202 root=202 sibling=1"));
        assertTrue(report.markdown().contains("smoke-pass"));
        assertTrue(report.markdown().contains("studio=[studio-success](http://studio.local/traces/studio-success)"));
        assertTrue(report.markdown().contains("sa_cost_usage_record cost=0.42 tokens=123 calls=2 records=1"));
        assertTrue(report.markdown().contains("Kernel output with audit trail"));
        assertTrue(report.markdown().contains(
                "Diff vs first trial: same as first trial; chars baseline=30 current=30 delta=0; lines baseline=1 current=1 delta=0"));
        assertTrue(report.markdown().contains(
                "Diff vs first trial: output not available"));
        assertTrue(report.markdown().contains("## Failure Reason Summary"));
        assertTrue(report.markdown().contains("| Reason | Count | Trials |"));
        assertTrue(report.markdown().contains("| AgentScope timeout | 1 | 11 |"));
        assertTrue(report.markdown().contains("AgentScope timeout"));
        assertNotNull(costRepository.query);
        assertEquals("default", costRepository.query.tenantId());
        assertEquals("run-exp-1-trial-10", costRepository.query.runId());
    }

    @Test
    void shouldExplainFailedTrialEvenWhenExecutorDoesNotReturnErrorMessage() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        RunExperimentTrialExecutorPort executor = request -> RunExperimentTrialExecutionResult.builder()
                .status("FAILED")
                .runId("run-exp-1-trial-10")
                .build();
        KernelRunExperimentService service = new KernelRunExperimentService(repository, executor);

        RunExperimentDetails details = service.create(RunExperimentCommand.builder()
                .userId("100")
                .conversationId(101L)
                .name("Profile compare")
                .runProfileIds(List.of(12L))
                .build());

        RunExperimentReport report = service.exportReport("100", details.getExperiment().getId());

        assertTrue(report.markdown().contains("- Failed: 1"));
        assertTrue(report.markdown().contains("## Failure Reason Summary"));
        assertTrue(report.markdown().contains("| FAILED - no failure message recorded | 1 | 10 |"));
        assertTrue(report.markdown().contains("Trial 10: FAILED - no failure message recorded"));
    }

    @Test
    void shouldExportFailureReportWhenRealExecutorCannotResolveBaseLeaf() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        InMemoryBranchRepository branchRepository = new InMemoryBranchRepository();
        KernelRunExperimentTrialExecutor trialExecutor = new KernelRunExperimentTrialExecutor(
                new FailingIfCalledExecutor(),
                branchRepository,
                new StaticRunProfileRepository());
        KernelRunExperimentService service = new KernelRunExperimentService(
                repository,
                () -> trialExecutor,
                branchRepository);

        RunExperimentDetails details = service.create(RunExperimentCommand.builder()
                .userId("100")
                .conversationId(101L)
                .baseLeafMessageId(999L)
                .name("Missing base leaf")
                .runProfileIds(List.of(12L))
                .build());

        assertEquals("FAILED", details.getExperiment().getStatus());
        assertEquals("FAILED", details.getTrials().get(0).getStatus());
        assertEquals("base leaf message not found", details.getTrials().get(0).getErrorMessage());

        RunExperimentReport report = service.exportReport("100", details.getExperiment().getId());

        assertTrue(report.markdown().contains("- Failed: 1"));
        assertTrue(report.markdown().contains("## Failure Reason Summary"));
        assertTrue(report.markdown().contains("| base leaf message not found | 1 | 10 |"));
        assertTrue(report.markdown().contains("Trial 10: base leaf message not found"));
        assertTrue(report.markdown().contains("- Output message ID: -"));
        assertTrue(report.markdown().contains("- Message branch: not resolved"));
    }

    @Test
    void shouldRedactCredentialTextFromExportedReport() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        InMemoryBranchRepository branchRepository = new InMemoryBranchRepository();
        RunExperimentTrialExecutorPort executor = request -> RunExperimentTrialExecutionResult.builder()
                .status("FAILED")
                .runId("run-exp-1-trial-10")
                .outputMessageId(301L)
                .metricJson("{\"traceId\":\"Bearer metric-secret-123456\",\"cost\":0.11}")
                .errorMessage("failed with api_key=trial-error-secret")
                .build();
        InMemoryRunContextSnapshotRepository snapshotRepository = new InMemoryRunContextSnapshotRepository();
        KernelRunExperimentService service = new KernelRunExperimentService(
                repository,
                () -> executor,
                branchRepository,
                snapshotRepository,
                null);

        RunExperimentDetails details = service.create(RunExperimentCommand.builder()
                .userId("100")
                .conversationId(101L)
                .baseLeafMessageId(202L)
                .name("Profile compare api_key=experiment-name-secret")
                .runProfileIds(List.of(12L))
                .build());
        RunContextSnapshotRecord snapshot = new RunContextSnapshotRecord();
        snapshot.setTenantId("default");
        snapshot.setRunId("run-exp-1-trial-10");
        snapshot.setTraceContextJson("""
                {"traceId":"Bearer trace-secret-123456","studioUrl":"http://studio.local"}
                """);
        snapshotRepository.snapshots.put("run-exp-1-trial-10", snapshot);
        branchRepository.add(message("301", "101", "100", "assistant",
                "output contains password=output-secret-value", 202L, 202L, 1));
        service.scoreTrial("100", details.getExperiment().getId(), details.getTrials().get(0).getId(),
                "{\"verdict\":\"Bearer score-secret-123456\"}");

        RunExperimentReport report = service.exportReport("100", details.getExperiment().getId());

        assertTrue(report.fileName().contains("redacted"));
        assertTrue(report.markdown().contains("[REDACTED]"));
        assertTrue(report.markdown().contains("cost"));
        assertTrue(report.markdown().contains("studio="));
        assertTrue(report.markdown().contains("Trial 10"));
        assertTrue(report.markdown().contains("output contains [REDACTED]"));
        assertTrue(report.markdown().contains("failed with [REDACTED]"));
        assertTrue(report.markdown().contains("{\"verdict\":\"[REDACTED]\"}"));
        assertTrue(report.markdown().contains("{\"traceId\":\"[REDACTED]\",\"cost\":0.11}"));
        assertTrue(report.markdown().contains("studio=[[REDACTED]]"));
        assertTrue(report.markdown().contains("# Run Experiment Report: Profile compare [REDACTED]"));
        assertTrue(report.markdown().contains("Trial branch leaves: trial 10 -> leaf=301 parent=202 root=202 sibling=1"));
        assertTrue(report.markdown().contains("- Recommended trial: not available"));
        assertEquals("profile-compare-redacted-1.md", report.fileName());
    }

    @Test
    void shouldRedactCredentialTextFromInboundDetailsAndTrialExecutionWrites() {
        InMemoryRunExperimentRepository repository = new InMemoryRunExperimentRepository();
        RunExperimentTrialExecutorPort executor = request -> RunExperimentTrialExecutionResult.builder()
                .status("FAILED")
                .runId("run-exp-1-trial-10")
                .metricJson("""
                        {"traceId":"Bearer metric-secret-123456","nested":{"apiKey":"nested-secret-value"}}
                        """)
                .errorMessage("failed with password=trial-error-secret")
                .build();
        KernelRunExperimentService service = new KernelRunExperimentService(repository, executor);

        RunExperimentDetails created = service.create(RunExperimentCommand.builder()
                .userId("100")
                .conversationId(101L)
                .name("Profile compare api_key=experiment-name-secret")
                .runProfileIds(List.of(12L))
                .build());
        RunExperimentDetails scored = service.scoreTrial(
                "100",
                created.getExperiment().getId(),
                created.getTrials().get(0).getId(),
                "{\"verdict\":\"Bearer score-secret-123456\",\"cost\":0.11}");
        RunExperimentDetails found = service.findById("100", created.getExperiment().getId()).orElseThrow();

        assertEquals("Profile compare [REDACTED]", created.getExperiment().getName());
        assertEquals("failed with [REDACTED]", created.getTrials().get(0).getErrorMessage());
        assertEquals("{\"traceId\":\"[REDACTED]\",\"nested\":{\"apiKey\":\"[REDACTED]\"}}",
                created.getTrials().get(0).getMetricJson());
        assertEquals("{\"verdict\":\"[REDACTED]\",\"cost\":0.11}", scored.getTrials().get(0).getScoreJson());
        assertEquals("Profile compare [REDACTED]", found.getExperiment().getName());
        assertEquals("{\"verdict\":\"[REDACTED]\",\"cost\":0.11}", found.getTrials().get(0).getScoreJson());

        assertEquals("Profile compare [REDACTED]", repository.details.getExperiment().getName());
        assertEquals("{\"traceId\":\"[REDACTED]\",\"nested\":{\"apiKey\":\"[REDACTED]\"}}",
                repository.details.getTrials().get(0).getMetricJson());
        assertEquals("failed with [REDACTED]", repository.details.getTrials().get(0).getErrorMessage());
        assertEquals("{\"verdict\":\"[REDACTED]\",\"cost\":0.11}", repository.details.getTrials().get(0).getScoreJson());
    }

    @Test
    void shouldRedactCredentialTextBeforePersistingTrialScore() {
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
                """
                        {"verdict":"Bearer score-secret-123456","nested":{"apiKey":"nested-score-secret"},"rating":5}
                        """);

        String expected = "{\"verdict\":\"[REDACTED]\",\"nested\":{\"apiKey\":\"[REDACTED]\"},\"rating\":5}";
        assertEquals(expected, scored.getTrials().get(0).getScoreJson());
        assertEquals(expected, repository.details.getTrials().get(0).getScoreJson());
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

    private static final class InMemoryBranchRepository implements ConversationBranchRepositoryPort {

        private final Map<Long, ConversationMessageRecord> messages = new LinkedHashMap<>();

        void add(ConversationMessageRecord record) {
            messages.put(Long.parseLong(record.getId()), record);
        }

        @Override
        public Long appendMessage(ConversationMessageRecord record) {
            Long id = Long.parseLong(record.getId());
            messages.put(id, record);
            return id;
        }

        @Override
        public List<ConversationMessageRecord> listSiblings(String conversationId, String userId, Long parentId) {
            return List.of();
        }

        @Override
        public List<ConversationMessageRecord> listTree(String conversationId, String userId) {
            return messages.values().stream()
                    .filter(message -> conversationId.equals(message.getConversationId()))
                    .filter(message -> userId.equals(message.getUserId()))
                    .toList();
        }

        @Override
        public void setActivePath(String conversationId, String userId, Set<Long> activeIds) {
        }

        @Override
        public ConversationBranchCursor upsertCursor(String conversationId, String userId, Long leafMessageId) {
            return ConversationBranchCursor.builder()
                    .conversationId(conversationId)
                    .userId(userId)
                    .leafMessageId(leafMessageId)
                    .build();
        }

        @Override
        public Optional<ConversationBranchCursor> findCursor(String conversationId, String userId) {
            return Optional.empty();
        }
    }

    private static final class InMemoryRunContextSnapshotRepository implements RunContextSnapshotRepositoryPort {

        private final Map<String, RunContextSnapshotRecord> snapshots = new LinkedHashMap<>();

        @Override
        public Long save(RunContextSnapshotRecord record) {
            snapshots.put(record.getRunId(), record);
            return 1L;
        }

        @Override
        public Optional<RunContextSnapshotRecord> findByRunId(String runId) {
            return Optional.ofNullable(snapshots.get(runId));
        }
    }

    private static final class FixedCostUsageRepository implements CostUsageRepositoryPort {

        private CostUsageQuery query;

        @Override
        public CostUsageRecord append(CostUsageRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public CostUsageAggregate aggregate(CostUsageQuery query) {
            this.query = query;
            if (!"run-exp-1-trial-10".equals(query.runId())) {
                return new CostUsageAggregate(query.tenantId(), query.agentId(), query.runId(), 0, 0, 0, 0);
            }
            return new CostUsageAggregate(query.tenantId(), query.agentId(), query.runId(), 123, 2, 0.42, 1);
        }
    }

    private static final class StaticRunProfileRepository implements RunProfileRepositoryPort {

        @Override
        public List<RunProfileRecord> listByUser(String userId) {
            return List.of();
        }

        @Override
        public Optional<RunProfileRecord> findById(String userId, Long id) {
            RunProfileRecord record = new RunProfileRecord();
            record.setId(id);
            record.setUserId(userId);
            record.setName("Kernel experiment profile");
            record.setExecutorEngine("kernel");
            record.setTenantId("default");
            return Optional.of(record);
        }

        @Override
        public Long save(RunProfileRecord record) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void replaceTools(Long profileId, List<RunProfileToolBindingRecord> tools) {
            throw new UnsupportedOperationException();
        }

        @Override
        public List<RunProfileToolBindingRecord> listTools(Long profileId) {
            return List.of();
        }

        @Override
        public void disableAll(String userId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setEnabled(String userId, Long id, boolean enabled) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String userId, Long id) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class FailingIfCalledExecutor implements ReActExecutorPort {

        @Override
        public AgentLoopResult execute(AgentLoopRequest request) {
            throw new AssertionError("executor should not run when base leaf is missing");
        }

        @Override
        public StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public String engineId() {
            return "kernel";
        }
    }

    private static ConversationMessageRecord message(String id, String conversationId, String userId,
                                                     String role, String content) {
        return message(id, conversationId, userId, role, content, null, null, null);
    }

    private static ConversationMessageRecord message(String id, String conversationId, String userId,
                                                     String role, String content, Long parentId,
                                                     Long branchRootId, Integer siblingSeq) {
        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setId(id);
        record.setConversationId(conversationId);
        record.setUserId(userId);
        record.setRole(role);
        record.setContent(content);
        record.setParentId(parentId);
        record.setBranchRootId(branchRootId);
        record.setSiblingSeq(siblingSeq);
        return record;
    }
}

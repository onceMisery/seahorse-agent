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

package com.miracle.ai.seahorse.agent.kernel.application.memory;

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryReviewDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidate;
import org.junit.jupiter.api.Test;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewApplyDirective;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class KernelMemoryReviewServiceTests {

    @Test
    void shouldApprovePendingCandidateThroughIngestionWorkflow() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.PENDING, "remember original project fact"));
        RecordingIngestionWorkflow workflow = new RecordingIngestionWorkflow(
                MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE")));
        RecordingReviewFeedbackRepository feedbackRepository = new RecordingReviewFeedbackRepository();
        KernelMemoryReviewService service = new KernelMemoryReviewService(repository, workflow, feedbackRepository);

        MemoryReviewRecord approved = service.approve("review-1",
                new MemoryReviewDecisionCommand("auditor", "approve", "", Map.of()));

        assertThat(approved.reviewStatus()).isEqualTo(MemoryReviewStatus.APPLIED);
        assertThat(approved.reviewedMemoryId()).isEqualTo("memory-review-apply-review-1");
        assertThat(approved.reviewedLayer()).isEqualTo("SHORT_TERM");
        assertThat(workflow.commands).hasSize(1);
        MemoryIngestionCommand command = workflow.commands.get(0);
        assertThat(command.operationId()).isEqualTo("memory-review-apply-review-1");
        assertThat(command.tenantId()).isEqualTo("tenant-1");
        assertThat(command.source()).isEqualTo("memory-review-approve");
        assertThat(command.writeRequest().userId()).isEqualTo("user-1");
        assertThat(command.writeRequest().conversationId()).isEqualTo("conv-1");
        assertThat(command.writeRequest().messageId()).isEqualTo("review-msg-1");
        assertThat(command.writeRequest().message().getRole()).isEqualTo(ChatRole.USER);
        assertThat(command.writeRequest().message().getContent()).isEqualTo("remember original project fact");
        MemoryReviewApplyDirective directive = command.reviewApplyDirective();
        assertThat(directive).isNotNull();
        assertThat(directive.requestedAction().name()).isEqualTo("REVIEW");
        assertThat(directive.targetLayer()).isEqualTo("SHORT_TERM");
        assertThat(directive.targetKind()).isEqualTo("PROJECT_FACT");
        assertThat(directive.targetKey()).isEqualTo("project.fact");
        assertThat(feedbackRepository.samples).hasSize(1);
        MemoryReviewFeedbackSample sample = feedbackRepository.samples.get(0);
        assertThat(sample.sampleId()).isEqualTo("memory-review-feedback-review-1");
        assertThat(sample.reviewStatus()).isEqualTo(MemoryReviewStatus.APPLIED);
        assertThat(sample.rejectedContent()).isEqualTo("remember original project fact");
        assertThat(sample.chosenContent()).isEqualTo("remember original project fact");
        assertThat(sample.reviewedMemoryId()).isEqualTo("memory-review-apply-review-1");
    }

    @Test
    void shouldModifyCandidateContentBeforeApplyingThroughIngestionWorkflow() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.PENDING, "original"));
        RecordingIngestionWorkflow workflow = new RecordingIngestionWorkflow(
                MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE")));
        RecordingReviewFeedbackRepository feedbackRepository = new RecordingReviewFeedbackRepository();
        KernelMemoryReviewService service = new KernelMemoryReviewService(repository, workflow, feedbackRepository);

        MemoryReviewRecord corrected = service.modify("review-1",
                new MemoryReviewDecisionCommand(
                        "auditor",
                        "fix wording",
                        "remember corrected project fact",
                        Map.of("reviewReason", "manual_fix")));

        assertThat(corrected.reviewStatus()).isEqualTo(MemoryReviewStatus.APPLIED);
        assertThat(corrected.reviewerId()).isEqualTo("auditor");
        assertThat(corrected.reviewComment()).isEqualTo("fix wording");
        assertThat(corrected.chosenMetadata()).containsEntry("reviewReason", "manual_fix");
        assertThat(workflow.commands).hasSize(1);
        assertThat(workflow.commands.get(0).source()).isEqualTo("memory-review-modify");
        assertThat(workflow.commands.get(0).writeRequest().message().getContent())
                .isEqualTo("remember corrected project fact");
        assertThat(workflow.commands.get(0).reviewApplyDirective().metadata())
                .containsEntry("reviewReason", "manual_fix");
        assertThat(feedbackRepository.samples).hasSize(1);
        assertThat(feedbackRepository.samples.get(0).rejectedContent()).isEqualTo("original");
        assertThat(feedbackRepository.samples.get(0).chosenContent()).isEqualTo("remember corrected project fact");
        assertThat(feedbackRepository.samples.get(0).chosenMetadata()).containsEntry("reviewReason", "manual_fix");
    }

    @Test
    void shouldApproveDeleteCandidateWithTargetKeyAsApplyContent() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review(
                "review-delete",
                MemoryReviewStatus.PENDING,
                "",
                "DELETE",
                "SHORT_TERM_MEMORY",
                "stm-old-memory"));
        RecordingIngestionWorkflow workflow = new RecordingIngestionWorkflow(
                MemoryIngestionResult.accepted(MemoryIngestionAction.DELETE, List.of("SHORT_TERM_DELETE")));
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                workflow,
                new RecordingReviewFeedbackRepository());

        MemoryReviewRecord approved = service.approve("review-delete",
                new MemoryReviewDecisionCommand("auditor", "delete confirmed", "", Map.of()));

        assertThat(approved.reviewStatus()).isEqualTo(MemoryReviewStatus.APPLIED);
        assertThat(approved.chosenContent()).isEqualTo("stm-old-memory");
        assertThat(workflow.commands).hasSize(1);
        assertThat(workflow.commands.get(0).writeRequest().message().getContent()).isEqualTo("stm-old-memory");
        assertThat(workflow.commands.get(0).reviewApplyDirective().requestedAction())
                .isEqualTo(MemoryIngestionAction.DELETE);
        assertThat(workflow.commands.get(0).reviewApplyDirective().targetKey()).isEqualTo("stm-old-memory");
    }

    @Test
    void shouldRejectCandidateWithoutCallingIngestionWorkflow() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.PENDING, "do not write"));
        RecordingIngestionWorkflow workflow = new RecordingIngestionWorkflow(
                MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE")));
        RecordingReviewFeedbackRepository feedbackRepository = new RecordingReviewFeedbackRepository();
        KernelMemoryReviewService service = new KernelMemoryReviewService(repository, workflow, feedbackRepository);

        MemoryReviewRecord rejected = service.reject("review-1",
                new MemoryReviewDecisionCommand("auditor", "not true", "", Map.of()));

        assertThat(rejected.reviewStatus()).isEqualTo(MemoryReviewStatus.REJECTED);
        assertThat(rejected.reviewerId()).isEqualTo("auditor");
        assertThat(rejected.reviewComment()).isEqualTo("not true");
        assertThat(workflow.commands).isEmpty();
        assertThat(feedbackRepository.samples).hasSize(1);
        assertThat(feedbackRepository.samples.get(0).reviewStatus()).isEqualTo(MemoryReviewStatus.REJECTED);
        assertThat(feedbackRepository.samples.get(0).rejectedContent()).isEqualTo("do not write");
        assertThat(feedbackRepository.samples.get(0).chosenContent()).isEmpty();
    }

    @Test
    void shouldListFeedbackSamplesForReviewCandidate() {
        RecordingReviewFeedbackRepository feedbackRepository = new RecordingReviewFeedbackRepository();
        feedbackRepository.save(new MemoryReviewFeedbackSample(
                "sample-1",
                "review-1",
                "op-1",
                "tenant-1",
                "user-1",
                "REVIEW",
                MemoryReviewStatus.APPLIED,
                "auditor",
                "approved",
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.fact",
                "old",
                "chosen",
                Map.of(),
                Map.of("reviewReason", "human"),
                List.of("msg-1"),
                "memory-1",
                "SHORT_TERM",
                Instant.EPOCH));
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                new InMemoryReviewRepository(),
                new RecordingIngestionWorkflow(MemoryIngestionResult.ignored("noop")),
                feedbackRepository);

        List<MemoryReviewFeedbackSample> samples = service.listFeedbackSamples("review-1", 5);

        assertThat(samples).hasSize(1);
        assertThat(samples.get(0).sampleId()).isEqualTo("sample-1");
        assertThat(samples.get(0).chosenMetadata()).containsEntry("reviewReason", "human");
    }

    @Test
    void shouldRecordTraceEventsForReviewDecisions() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.PENDING, "original"));
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                new RecordingIngestionWorkflow(MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE"))),
                new RecordingReviewFeedbackRepository(),
                traceRecorder);

        service.approve("review-1",
                new MemoryReviewDecisionCommand("auditor", "approve", "", Map.of()));

        assertThat(traceRecorder.events).isNotEmpty();
        assertThat(traceRecorder.events)
                .anyMatch(event -> "memory-review".equals(event.component())
                        && "approve".equals(event.eventType())
                        && MemoryReviewStatus.APPLIED.name().equals(event.status()));
    }

    @Test
    void shouldKeepReviewDecisionWhenFeedbackRecordingFails() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.PENDING, "still apply"));
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                new RecordingIngestionWorkflow(MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE"))),
                new FailingReviewFeedbackRepository());

        MemoryReviewRecord approved = service.approve("review-1",
                new MemoryReviewDecisionCommand("auditor", "approve", "", Map.of()));

        assertThat(approved.reviewStatus()).isEqualTo(MemoryReviewStatus.APPLIED);
        assertThat(repository.findReviewItem("review-1").orElseThrow().reviewStatus())
                .isEqualTo(MemoryReviewStatus.APPLIED);
    }

    @Test
    void shouldNotApplyAlreadyReviewedCandidateAgain() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.APPLIED, "already applied"));
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                new RecordingIngestionWorkflow(MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE"))));

        assertThatThrownBy(() -> service.approve("review-1",
                new MemoryReviewDecisionCommand("auditor", "approve", "", Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("review candidate is not pending");
    }

    @Test
    void shouldKeepCandidatePendingWhenIngestionDoesNotAccept() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.PENDING, "unsafe"));
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                new RecordingIngestionWorkflow(MemoryIngestionResult.rejected("sensitive_credential")));

        assertThatThrownBy(() -> service.approve("review-1",
                new MemoryReviewDecisionCommand("auditor", "approve", "", Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("review ingestion was not accepted");

        assertThat(repository.findReviewItem("review-1").orElseThrow().reviewStatus())
                .isEqualTo(MemoryReviewStatus.PENDING);
    }

    private static MemoryReviewRecord review(String id, MemoryReviewStatus status, String content) {
        return review(id, status, content, "REVIEW", "PROJECT_FACT", "project.fact");
    }

    private static MemoryReviewRecord review(String id,
                                             MemoryReviewStatus status,
                                             String content,
                                             String requestedAction,
                                             String targetKind,
                                             String targetKey) {
        return new MemoryReviewRecord(
                id,
                "operation-1",
                "tenant-1",
                "user-1",
                "conv-1",
                "msg-1",
                requestedAction,
                "SHORT_TERM",
                targetKind,
                targetKey,
                content,
                0.7D,
                0.8D,
                0.8D,
                0.2D,
                "needs_review",
                List.of("msg-1"),
                Map.of("source", "test"),
                status,
                "",
                "",
                "",
                Map.of(),
                "",
                "",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static final class InMemoryReviewRepository implements MemoryReviewManagementRepositoryPort {

        private final Map<String, MemoryReviewRecord> records = new LinkedHashMap<>();

        void put(MemoryReviewRecord record) {
            records.put(record.candidateId(), record);
        }

        @Override
        public void save(MemoryReviewCandidate candidate) {
            put(MemoryReviewRecord.pending(candidate));
        }

        @Override
        public MemoryReviewPage pageReviewCandidates(MemoryReviewQuery query) {
            return new MemoryReviewPage(List.copyOf(records.values()), records.size(), query.size(), query.current(), 1);
        }

        @Override
        public Optional<MemoryReviewRecord> findReviewItem(String candidateId) {
            return Optional.ofNullable(records.get(candidateId));
        }

        @Override
        public MemoryReviewRecord applyReviewDecision(MemoryReviewDecision decision) {
            MemoryReviewRecord current = findReviewItem(decision.candidateId()).orElseThrow();
            MemoryReviewRecord updated = new MemoryReviewRecord(
                    current.candidateId(),
                    current.operationId(),
                    current.tenantId(),
                    current.userId(),
                    current.conversationId(),
                    current.messageId(),
                    current.requestedAction(),
                    current.targetLayer(),
                    current.targetKind(),
                    current.targetKey(),
                    current.content(),
                    current.confidence(),
                    current.importance(),
                    current.valueScore(),
                    current.riskScore(),
                    current.reason(),
                    current.sourceMessageIds(),
                    current.metadata(),
                    decision.reviewStatus(),
                    decision.reviewerId(),
                    decision.reviewComment(),
                    decision.chosenContent(),
                    decision.chosenMetadata(),
                    decision.reviewedMemoryId(),
                    decision.reviewedLayer(),
                    current.createdAt(),
                    Instant.EPOCH);
            records.put(updated.candidateId(), updated);
            return updated;
        }
    }

    private static final class RecordingIngestionWorkflow implements MemoryIngestionWorkflowPort {

        private final MemoryIngestionResult result;
        private final List<MemoryIngestionCommand> commands = new java.util.ArrayList<>();

        private RecordingIngestionWorkflow(MemoryIngestionResult result) {
            this.result = result;
        }

        @Override
        public MemoryIngestionResult ingest(MemoryIngestionCommand command) {
            commands.add(command);
            assertThat(command.writeRequest().message().getRole()).isEqualTo(ChatRole.USER);
            return result;
        }
    }

    private static final class RecordingReviewFeedbackRepository implements MemoryReviewFeedbackRepositoryPort {

        private final List<MemoryReviewFeedbackSample> samples = new ArrayList<>();

        @Override
        public void save(MemoryReviewFeedbackSample sample) {
            samples.add(sample);
        }

        @Override
        public List<MemoryReviewFeedbackSample> listByCandidate(String candidateId, int limit) {
            return samples.stream()
                    .filter(sample -> sample.candidateId().equals(candidateId))
                    .limit(limit)
                    .toList();
        }
    }

    private static final class FailingReviewFeedbackRepository implements MemoryReviewFeedbackRepositoryPort {

        @Override
        public void save(MemoryReviewFeedbackSample sample) {
            throw new IllegalStateException("feedback store down");
        }

        @Override
        public List<MemoryReviewFeedbackSample> listByCandidate(String candidateId, int limit) {
            return List.of();
        }
    }

    private static final class RecordingTraceRecorder implements MemoryTraceRecorder {

        private final List<MemoryTraceEvent> events = new ArrayList<>();

        @Override
        public void record(MemoryTraceEvent event) {
            events.add(event);
        }

        @Override
        public List<MemoryTraceEvent> listRecent(int limit) {
            return List.copyOf(events);
        }
    }
}

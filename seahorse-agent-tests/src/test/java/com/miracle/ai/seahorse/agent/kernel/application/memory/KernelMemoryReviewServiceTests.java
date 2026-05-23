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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolution;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewPendingSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidate;
import org.junit.jupiter.api.Test;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewFeedbackSample;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerFeedbackExportRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewApplyDirective;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;

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
    void shouldSummarizePendingReviewQueueWithSinglePreviewCandidate() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.PENDING, "pending project fact"));
        repository.put(review("review-2", MemoryReviewStatus.APPLIED, "already applied"));
        repository.put(review(
                "review-3",
                MemoryReviewStatus.PENDING,
                "pending preference",
                "REVIEW",
                "PREFERENCE",
                "preference.editor"));
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                new RecordingIngestionWorkflow(MemoryIngestionResult.ignored("noop")));

        MemoryReviewPendingSummary summary = service.pendingSummary(
                "tenant-1", "user-1", "PROJECT_FACT", "project.fact");

        assertThat(summary.pendingCount()).isEqualTo(1L);
        assertThat(summary.hasPending()).isTrue();
        assertThat(summary.latestPendingCandidate()).isNotNull();
        assertThat(summary.latestPendingCandidate().candidateId()).isEqualTo("review-1");
        assertThat(repository.queries).hasSize(1);
        MemoryReviewQuery query = repository.queries.get(0);
        assertThat(query.reviewStatus()).isEqualTo(MemoryReviewStatus.PENDING);
        assertThat(query.current()).isEqualTo(1L);
        assertThat(query.size()).isEqualTo(1L);
        assertThat(query.targetKind()).isEqualTo("PROJECT_FACT");
        assertThat(query.targetKey()).isEqualTo("project.fact");
    }

    @Test
    void shouldReturnEmptyPendingSummaryWhenReviewQueueHasNoPendingCandidate() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.REJECTED, "rejected"));
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                new RecordingIngestionWorkflow(MemoryIngestionResult.ignored("noop")));

        MemoryReviewPendingSummary summary = service.pendingSummary("tenant-1", "user-1", "", "");

        assertThat(summary.pendingCount()).isZero();
        assertThat(summary.hasPending()).isFalse();
        assertThat(summary.latestPendingCandidate()).isNull();
    }

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
    void shouldApproveAliasCandidateThroughAliasPortWithoutMemoryIngestion() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review(
                "review-alias",
                MemoryReviewStatus.PENDING,
                "K8s",
                "REVIEW",
                "ALIAS",
                "K8s",
                Map.of(
                        "canonicalEntityId", "ent-core-k8s",
                        "canonicalName", "Kubernetes",
                        "entityType", "TECHNOLOGY",
                        "confidenceLevel", 0.96D,
                        "sourceMemoryIds", List.of("memory-1"))));
        RecordingIngestionWorkflow workflow = new RecordingIngestionWorkflow(
                MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE")));
        RecordingAliasPort aliasPort = new RecordingAliasPort();
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                workflow,
                new RecordingReviewFeedbackRepository(),
                MemoryTraceRecorder.noop(),
                aliasPort);

        MemoryReviewRecord approved = service.approve("review-alias",
                new MemoryReviewDecisionCommand("auditor", "alias confirmed", "", Map.of()));

        assertThat(approved.reviewStatus()).isEqualTo(MemoryReviewStatus.APPLIED);
        assertThat(approved.reviewedMemoryId()).isEqualTo("alias:ent-core-k8s:K8s");
        assertThat(approved.reviewedLayer()).isEqualTo("ALIAS");
        assertThat(workflow.commands).isEmpty();
        assertThat(aliasPort.commands).hasSize(1);
        MemoryAliasCommand command = aliasPort.commands.get(0);
        assertThat(command.userId()).isEqualTo("user-1");
        assertThat(command.tenantId()).isEqualTo("tenant-1");
        assertThat(command.aliasText()).isEqualTo("K8s");
        assertThat(command.canonicalEntityId()).isEqualTo("ent-core-k8s");
        assertThat(command.canonicalName()).isEqualTo("Kubernetes");
        assertThat(command.entityType()).isEqualTo("TECHNOLOGY");
        assertThat(command.confidenceLevel()).isEqualTo(0.96D);
        assertThat(command.sourceType()).isEqualTo("memory-review-alias");
        assertThat(command.sourceMemoryIds()).containsExactly("memory-1");
        assertThat(command.metadata()).containsEntry("reviewCandidateId", "review-alias");
    }

    @Test
    void shouldModifyAliasCandidateBeforeApplyingThroughAliasPort() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review(
                "review-alias",
                MemoryReviewStatus.PENDING,
                "K8s",
                "REVIEW",
                "ALIAS",
                "K8s",
                Map.of(
                        "canonicalEntityId", "ent-core-k8s",
                        "canonicalName", "Kubernetes",
                        "entityType", "TECHNOLOGY",
                        "confidenceLevel", 0.72D)));
        RecordingIngestionWorkflow workflow = new RecordingIngestionWorkflow(
                MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE")));
        RecordingAliasPort aliasPort = new RecordingAliasPort();
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                workflow,
                new RecordingReviewFeedbackRepository(),
                MemoryTraceRecorder.noop(),
                aliasPort);

        MemoryReviewRecord modified = service.modify("review-alias",
                new MemoryReviewDecisionCommand(
                        "auditor",
                        "alias canonical target fixed",
                        "Kubernetes",
                        Map.of(
                                "canonicalEntityId", "ent-platform-kubernetes",
                                "canonicalName", "Kubernetes Platform",
                                "entityType", "PLATFORM",
                                "confidenceLevel", 0.91D)));

        assertThat(modified.reviewStatus()).isEqualTo(MemoryReviewStatus.APPLIED);
        assertThat(modified.chosenContent()).isEqualTo("Kubernetes");
        assertThat(workflow.commands).isEmpty();
        assertThat(aliasPort.commands).hasSize(1);
        MemoryAliasCommand command = aliasPort.commands.get(0);
        assertThat(command.aliasText()).isEqualTo("Kubernetes");
        assertThat(command.canonicalEntityId()).isEqualTo("ent-platform-kubernetes");
        assertThat(command.canonicalName()).isEqualTo("Kubernetes Platform");
        assertThat(command.entityType()).isEqualTo("PLATFORM");
        assertThat(command.confidenceLevel()).isEqualTo(0.91D);
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
    void shouldEmitObservationDecisionEventOnApproveModifyAndReject() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-approve", MemoryReviewStatus.PENDING, "approve content"));
        repository.put(review("review-modify", MemoryReviewStatus.PENDING, "modify content"));
        repository.put(review("review-reject", MemoryReviewStatus.PENDING, "reject content"));
        RecordingIngestionWorkflow workflow = new RecordingIngestionWorkflow(
                MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE")));
        RecordingObservationPort observationPort = new RecordingObservationPort();
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                workflow,
                MemoryReviewFeedbackRepositoryPort.empty(),
                MemoryTraceRecorder.noop(),
                MemoryAliasPort.noop(),
                observationPort);

        service.approve("review-approve",
                new MemoryReviewDecisionCommand("auditor", "ok", "", Map.of()));
        service.modify("review-modify",
                new MemoryReviewDecisionCommand("auditor", "tweak", "tweaked content", Map.of()));
        service.reject("review-reject",
                new MemoryReviewDecisionCommand("auditor", "no thanks", "", Map.of()));

        assertThat(observationPort.events).hasSize(3);
        assertThat(observationPort.events)
                .extracting(event -> event.attributes()
                        .get(KernelMemoryReviewService.OBSERVATION_ATTR_DECISION))
                .containsExactly("approve", "modify", "reject");
        assertThat(observationPort.events)
                .extracting(event -> event.attributes()
                        .get(KernelMemoryReviewService.OBSERVATION_ATTR_STATUS))
                .containsExactly(
                        MemoryReviewStatus.APPLIED.name(),
                        MemoryReviewStatus.APPLIED.name(),
                        MemoryReviewStatus.REJECTED.name());
        assertThat(observationPort.events)
                .extracting(ObservationEvent::name)
                .containsOnly(KernelMemoryReviewService.OBSERVATION_DECISION_EVENT);
        assertThat(observationPort.events)
                .allMatch(event -> event.amount() == ObservationEvent.DEFAULT_AMOUNT);
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
    void shouldListFeedbackSamplesForDatasetExport() {
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
        feedbackRepository.save(new MemoryReviewFeedbackSample(
                "sample-2",
                "review-2",
                "op-2",
                "tenant-1",
                "user-2",
                "REVIEW",
                MemoryReviewStatus.REJECTED,
                "auditor",
                "rejected",
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.fact",
                "bad",
                "",
                Map.of(),
                Map.of(),
                List.of("msg-2"),
                "",
                "",
                Instant.EPOCH.plusSeconds(1)));
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                new InMemoryReviewRepository(),
                new RecordingIngestionWorkflow(MemoryIngestionResult.ignored("noop")),
                feedbackRepository);

        List<MemoryReviewFeedbackSample> samples = service.listFeedbackSamples(
                "tenant-1", "user-1", MemoryReviewStatus.APPLIED, "PROJECT_FACT", "project.fact", 10);

        assertThat(samples).extracting(MemoryReviewFeedbackSample::sampleId)
                .containsExactly("sample-1");
    }

    @Test
    void shouldExportReviewFeedbackAsRefinerTrainingRecords() {
        RecordingReviewFeedbackRepository feedbackRepository = new RecordingReviewFeedbackRepository();
        feedbackRepository.save(new MemoryReviewFeedbackSample(
                "sample-modify",
                "review-modify",
                "op-modify",
                "tenant-1",
                "user-1",
                "REVIEW",
                MemoryReviewStatus.APPLIED,
                "auditor",
                "fixed wording",
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.fact",
                "old fact",
                "corrected fact",
                Map.of("source", "llm_refiner"),
                Map.of("reviewReason", "manual_fix"),
                List.of("msg-1"),
                "memory-1",
                "SHORT_TERM",
                Instant.EPOCH));
        feedbackRepository.save(new MemoryReviewFeedbackSample(
                "sample-reject",
                "review-reject",
                "op-reject",
                "tenant-1",
                "user-1",
                "REVIEW",
                MemoryReviewStatus.REJECTED,
                "auditor",
                "hallucinated",
                "SHORT_TERM",
                "PROJECT_FACT",
                "project.fact",
                "bad fact",
                "",
                Map.of(),
                Map.of(),
                List.of("msg-2"),
                "",
                "",
                Instant.EPOCH.plusSeconds(1)));
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                new InMemoryReviewRepository(),
                new RecordingIngestionWorkflow(MemoryIngestionResult.ignored("noop")),
                feedbackRepository);

        List<MemoryRefinerFeedbackExportRecord> records = service.exportRefinerFeedbackSamples(
                "tenant-1", "user-1", null, "PROJECT_FACT", "project.fact", 10);

        assertThat(records).extracting(MemoryRefinerFeedbackExportRecord::sampleId)
                .containsExactly("sample-modify", "sample-reject");
        MemoryRefinerFeedbackExportRecord modified = records.get(0);
        assertThat(modified.feedbackType()).isEqualTo("MODIFY");
        assertThat(modified.promptInput())
                .containsEntry("tenantId", "tenant-1")
                .containsEntry("userId", "user-1")
                .containsEntry("targetKey", "project.fact");
        assertThat(modified.rejectedOutput())
                .containsEntry("action", "REVIEW")
                .containsEntry("content", "old fact");
        assertThat(modified.chosenOutput())
                .containsEntry("action", "ADD")
                .containsEntry("content", "corrected fact");
        assertThat(modified.metadata()).containsEntry("reviewerId", "auditor");
        MemoryRefinerFeedbackExportRecord rejected = records.get(1);
        assertThat(rejected.feedbackType()).isEqualTo("REJECT");
        assertThat(rejected.chosenOutput())
                .containsEntry("action", "IGNORE")
                .containsEntry("content", "");
    }

    @Test
    void shouldPreserveAppliedReviewActionWhenExportingRefinerTrainingRecords() {
        RecordingReviewFeedbackRepository feedbackRepository = new RecordingReviewFeedbackRepository();
        feedbackRepository.save(new MemoryReviewFeedbackSample(
                "sample-update",
                "review-update",
                "op-update",
                "tenant-1",
                "user-1",
                MemoryIngestionAction.UPDATE.name(),
                MemoryReviewStatus.APPLIED,
                "auditor",
                "updated stale memory",
                "SHORT_TERM",
                "SHORT_TERM_MEMORY",
                "memory-1",
                "old content",
                "updated content",
                Map.of("targetMemoryId", "memory-1"),
                Map.of("targetMemoryId", "memory-1"),
                List.of("msg-1"),
                "memory-1",
                "SHORT_TERM",
                Instant.EPOCH));
        feedbackRepository.save(new MemoryReviewFeedbackSample(
                "sample-delete",
                "review-delete",
                "op-delete",
                "tenant-1",
                "user-1",
                MemoryIngestionAction.DELETE.name(),
                MemoryReviewStatus.APPLIED,
                "auditor",
                "deleted stale memory",
                "SHORT_TERM",
                "SHORT_TERM_MEMORY",
                "memory-2",
                "obsolete content",
                "",
                Map.of("targetMemoryId", "memory-2"),
                Map.of("targetMemoryId", "memory-2"),
                List.of("msg-2"),
                "memory-2",
                "SHORT_TERM",
                Instant.EPOCH.plusSeconds(1)));
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                new InMemoryReviewRepository(),
                new RecordingIngestionWorkflow(MemoryIngestionResult.ignored("noop")),
                feedbackRepository);

        List<MemoryRefinerFeedbackExportRecord> records = service.exportRefinerFeedbackSamples(
                "tenant-1", "user-1", MemoryReviewStatus.APPLIED, "SHORT_TERM_MEMORY", "", 10);

        assertThat(records).extracting(MemoryRefinerFeedbackExportRecord::sampleId)
                .containsExactly("sample-update", "sample-delete");
        assertThat(records.get(0).chosenOutput())
                .containsEntry("action", MemoryIngestionAction.UPDATE.name())
                .containsEntry("content", "updated content");
        assertThat(records.get(1).chosenOutput())
                .containsEntry("action", MemoryIngestionAction.DELETE.name())
                .containsEntry("content", "");
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
    void shouldRecordReviewTraceExplanationWithoutRawCandidateContent() {
        String rawOriginal = "User private original database note";
        String rawCorrected = "User corrected database fact";
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.PENDING, rawOriginal));
        RecordingTraceRecorder traceRecorder = new RecordingTraceRecorder();
        KernelMemoryReviewService service = new KernelMemoryReviewService(
                repository,
                new RecordingIngestionWorkflow(MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE"))),
                new RecordingReviewFeedbackRepository(),
                traceRecorder);

        service.modify("review-1",
                new MemoryReviewDecisionCommand("auditor", "fixed wording", rawCorrected, Map.of()));

        assertThat(traceRecorder.events)
                .filteredOn(event -> "memory-review".equals(event.component())
                        && "modify".equals(event.eventType()))
                .singleElement()
                .satisfies(event -> {
                    assertThat(event.details())
                            .containsEntry("candidateId", "review-1")
                            .containsEntry("candidateOperationId", "operation-1")
                            .containsEntry("applyOperationId", "memory-review-apply-review-1")
                            .containsEntry("feedbackSampleId", "memory-review-feedback-review-1")
                            .containsEntry("reviewerId", "auditor")
                            .containsEntry("requestedAction", "REVIEW")
                            .containsEntry("targetLayer", "SHORT_TERM")
                            .containsEntry("targetKind", "PROJECT_FACT")
                            .containsEntry("targetKey", "project.fact")
                            .containsEntry("source", "memory-review-modify")
                            .containsEntry("sourceMessageIds", List.of("msg-1"))
                            .containsEntry("reviewStatus", MemoryReviewStatus.APPLIED.name())
                            .containsEntry("reviewedMemoryId", "memory-review-apply-review-1")
                            .containsEntry("reviewedLayer", "SHORT_TERM");
                    assertThat(event.details().toString())
                            .doesNotContain(rawOriginal)
                            .doesNotContain(rawCorrected);
                });
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
    void shouldNotIngestWhenReviewCandidateIsClaimedByAnotherDecision() {
        StaleClaimReviewRepository repository = new StaleClaimReviewRepository();
        repository.put(review("review-1", MemoryReviewStatus.PENDING, "do not apply stale review"));
        RecordingIngestionWorkflow workflow = new RecordingIngestionWorkflow(
                MemoryIngestionResult.accepted(List.of("SHORT_TERM_SAVE")));
        KernelMemoryReviewService service = new KernelMemoryReviewService(repository, workflow);

        assertThatThrownBy(() -> service.approve("review-1",
                new MemoryReviewDecisionCommand("auditor", "approve", "", Map.of())))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("review candidate is not pending");

        assertThat(workflow.commands).isEmpty();
        assertThat(repository.findReviewItem("review-1").orElseThrow().reviewStatus())
                .isEqualTo(MemoryReviewStatus.REJECTED);
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
        return review(id, status, content, requestedAction, targetKind, targetKey, Map.of("source", "test"));
    }

    private static MemoryReviewRecord review(String id,
                                             MemoryReviewStatus status,
                                             String content,
                                             String requestedAction,
                                             String targetKind,
                                             String targetKey,
                                             Map<String, Object> metadata) {
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
                metadata,
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

    private static class InMemoryReviewRepository implements MemoryReviewManagementRepositoryPort {

        private final Map<String, MemoryReviewRecord> records = new LinkedHashMap<>();
        private final List<MemoryReviewQuery> queries = new ArrayList<>();

        void put(MemoryReviewRecord record) {
            records.put(record.candidateId(), record);
        }

        @Override
        public void save(MemoryReviewCandidate candidate) {
            put(MemoryReviewRecord.pending(candidate));
        }

        @Override
        public MemoryReviewPage pageReviewCandidates(MemoryReviewQuery query) {
            queries.add(query);
            List<MemoryReviewRecord> filtered = records.values().stream()
                    .filter(record -> query.tenantId().isBlank() || record.tenantId().equals(query.tenantId()))
                    .filter(record -> query.userId().isBlank() || record.userId().equals(query.userId()))
                    .filter(record -> query.reviewStatus() == null || record.reviewStatus() == query.reviewStatus())
                    .filter(record -> query.targetKind().isBlank() || record.targetKind().equals(query.targetKind()))
                    .filter(record -> query.targetKey().isBlank() || record.targetKey().equals(query.targetKey()))
                    .toList();
            List<MemoryReviewRecord> pageRecords = filtered.stream()
                    .skip(query.offset())
                    .limit(query.size())
                    .toList();
            long pages = filtered.isEmpty() ? 0L : (filtered.size() + query.size() - 1L) / query.size();
            return new MemoryReviewPage(pageRecords, filtered.size(), query.size(), query.current(), pages);
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

    private static final class StaleClaimReviewRepository extends InMemoryReviewRepository {

        @Override
        public MemoryReviewRecord applyReviewDecision(MemoryReviewDecision decision) {
            MemoryReviewRecord current = findReviewItem(decision.candidateId()).orElseThrow();
            put(new MemoryReviewRecord(
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
                    MemoryReviewStatus.REJECTED,
                    "auditor-2",
                    "concurrent reject",
                    "",
                    Map.of(),
                    "",
                    "",
                    current.createdAt(),
                    Instant.EPOCH));
            throw new IllegalStateException("review candidate is not pending: " + decision.candidateId());
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

        @Override
        public List<MemoryReviewFeedbackSample> listSamples(MemoryReviewFeedbackQuery query) {
            return samples.stream()
                    .filter(sample -> query.tenantId().isBlank() || sample.tenantId().equals(query.tenantId()))
                    .filter(sample -> query.userId().isBlank() || sample.userId().equals(query.userId()))
                    .filter(sample -> query.reviewStatus() == null || sample.reviewStatus() == query.reviewStatus())
                    .filter(sample -> query.targetKind().isBlank() || sample.targetKind().equals(query.targetKind()))
                    .filter(sample -> query.targetKey().isBlank() || sample.targetKey().equals(query.targetKey()))
                    .limit(query.limit())
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

    private static final class RecordingAliasPort implements MemoryAliasPort {

        private final List<MemoryAliasCommand> commands = new ArrayList<>();

        @Override
        public Optional<MemoryAliasResolution> resolveAlias(String userId, String tenantId, String aliasText) {
            return Optional.empty();
        }

        @Override
        public void upsertAlias(MemoryAliasCommand command) {
            commands.add(command);
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

    private static final class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new ObservationScope() {
                @Override
                public void recordEvent(ObservationEvent event) {
                    events.add(event);
                }

                @Override
                public void close() {
                }
            };
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }
}

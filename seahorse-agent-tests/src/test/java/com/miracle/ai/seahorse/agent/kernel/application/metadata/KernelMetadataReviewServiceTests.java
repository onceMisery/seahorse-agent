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

package com.miracle.ai.seahorse.agent.kernel.application.metadata;

import com.miracle.ai.seahorse.agent.ports.inbound.metadata.MetadataReviewDecisionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataCanonicalWritePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataIndexCompensationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantineItem;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataQuarantinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewAuditRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMetadataReviewServiceTests {

    @Test
    void shouldWriteApprovedReviewMetadataBackToCanonicalDocument() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MetadataReviewStatus.PENDING, Map.of()));
        CapturingCanonicalWritePort canonicalWritePort = new CapturingCanonicalWritePort();
        CapturingIndexCompensationPort compensationPort = new CapturingIndexCompensationPort();
        KernelMetadataReviewService service = new KernelMetadataReviewService(
                repository, canonicalWritePort, MetadataQuarantinePort.noop(), compensationPort);

        MetadataReviewRecord approved = service.approve("review-1",
                new MetadataReviewDecisionCommand("auditor", "通过", Map.of()));

        assertThat(approved.reviewStatus()).isEqualTo(MetadataReviewStatus.APPROVED);
        assertThat(compensationPort.tenantId).isEqualTo("tenant-1");
        assertThat(compensationPort.knowledgeBaseId).isEqualTo(1L);
        assertThat(canonicalWritePort.documentId).isEqualTo("1");
        assertThat(canonicalWritePort.metadata).containsEntry("department", "hr");
        assertThat(compensationPort.documentId).isEqualTo(1L);
    }

    @Test
    void shouldRecordReviewDecisionObservationAfterApprove() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MetadataReviewStatus.PENDING, Map.of()));
        CapturingCanonicalWritePort canonicalWritePort = new CapturingCanonicalWritePort();
        CapturingIndexCompensationPort compensationPort = new CapturingIndexCompensationPort();
        RecordingObservationPort observationPort = new RecordingObservationPort();
        KernelMetadataReviewService service = new KernelMetadataReviewService(
                repository,
                canonicalWritePort,
                MetadataQuarantinePort.noop(),
                compensationPort,
                MetadataReviewReExtractPort.noop(),
                observationPort);

        service.approve("review-1", new MetadataReviewDecisionCommand("auditor", "通过", Map.of()));

        assertThat(observationPort.events)
                .filteredOn(event -> event.name().equals("metadata.review.decision.completed"))
                .singleElement()
                .satisfies(event -> assertThat(event.attributes())
                        .containsEntry("tenantId", "tenant-1")
                        .containsEntry("knowledgeBaseId", "1")
                        .containsEntry("action", "APPROVE")
                        .containsEntry("reviewStatus", "APPROVED")
                        .containsEntry("reasonCode", "LOW_CONFIDENCE")
                        .containsEntry("suggestedFieldCount", "2")
                        .containsEntry("correctedFieldCount", "2")
                        .containsEntry("canonicalWritten", "true")
                        .containsEntry("indexCompensationRequested", "true")
                        .containsEntry("quarantined", "false")
                        .containsEntry("reExtractRequested", "false"));
    }

    @Test
    void shouldRequestCompensationWithTenantAndKnowledgeBaseAfterCorrect() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MetadataReviewStatus.PENDING, Map.of()));
        CapturingCanonicalWritePort canonicalWritePort = new CapturingCanonicalWritePort();
        CapturingIndexCompensationPort compensationPort = new CapturingIndexCompensationPort();
        KernelMetadataReviewService service = new KernelMetadataReviewService(
                repository, canonicalWritePort, MetadataQuarantinePort.noop(), compensationPort);

        MetadataReviewRecord corrected = service.correct("review-1",
                new MetadataReviewDecisionCommand("auditor", "修正", Map.of("department", "legal")));

        assertThat(corrected.reviewStatus()).isEqualTo(MetadataReviewStatus.CORRECTED);
        assertThat(canonicalWritePort.metadata).containsEntry("department", "legal");
        assertThat(compensationPort.tenantId).isEqualTo("tenant-1");
        assertThat(compensationPort.knowledgeBaseId).isEqualTo(1L);
        assertThat(compensationPort.documentId).isEqualTo(1L);
    }

    @Test
    void shouldMoveReviewItemToQuarantineWithoutCanonicalWrite() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MetadataReviewStatus.PENDING, Map.of()));
        CapturingCanonicalWritePort canonicalWritePort = new CapturingCanonicalWritePort();
        List<MetadataQuarantineItem> quarantines = new java.util.ArrayList<>();
        KernelMetadataReviewService service = new KernelMetadataReviewService(
                repository, canonicalWritePort, quarantines::add);

        MetadataReviewRecord quarantined = service.quarantine("review-1",
                new MetadataReviewDecisionCommand("auditor", "转隔离", Map.of()));

        assertThat(quarantined.reviewStatus()).isEqualTo(MetadataReviewStatus.QUARANTINED);
        assertThat(canonicalWritePort.metadata).isEmpty();
        assertThat(quarantines).hasSize(1);
        assertThat(quarantines.get(0).reasonCode()).isEqualTo("REVIEW_QUARANTINED");
    }

    @Test
    void shouldIgnoreSelectedFieldAndWriteRemainingMetadata() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MetadataReviewStatus.PENDING, Map.of()));
        CapturingCanonicalWritePort canonicalWritePort = new CapturingCanonicalWritePort();
        CapturingIndexCompensationPort compensationPort = new CapturingIndexCompensationPort();
        KernelMetadataReviewService service = new KernelMetadataReviewService(
                repository, canonicalWritePort, MetadataQuarantinePort.noop(), compensationPort);

        MetadataReviewRecord corrected = service.ignoreField("review-1",
                new MetadataReviewDecisionCommand("auditor", "忽略非关键字段", Map.of(), List.of("owner")));

        assertThat(corrected.reviewStatus()).isEqualTo(MetadataReviewStatus.CORRECTED);
        assertThat(corrected.correctedMetadata()).containsEntry("department", "hr");
        assertThat(corrected.correctedMetadata()).doesNotContainKey("owner");
        assertThat(canonicalWritePort.metadata).containsEntry("department", "hr");
        assertThat(canonicalWritePort.metadata).doesNotContainKey("owner");
        assertThat(compensationPort.tenantId).isEqualTo("tenant-1");
        assertThat(compensationPort.knowledgeBaseId).isEqualTo(1L);
        assertThat(compensationPort.documentId).isEqualTo(1L);
    }

    @Test
    void shouldScheduleReExtractWithoutCanonicalWrite() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MetadataReviewStatus.PENDING, Map.of()));
        CapturingCanonicalWritePort canonicalWritePort = new CapturingCanonicalWritePort();
        CapturingIndexCompensationPort compensationPort = new CapturingIndexCompensationPort();
        CapturingReExtractPort reExtractPort = new CapturingReExtractPort();
        KernelMetadataReviewService service = new KernelMetadataReviewService(
                repository, canonicalWritePort, MetadataQuarantinePort.noop(), compensationPort, reExtractPort);

        MetadataReviewRecord reExtracting = service.reExtract("review-1",
                new MetadataReviewDecisionCommand(
                        "auditor", "重新抽取", Map.of(), List.of(), "extractor-v2", "pipe-2",
                        "llm-v2", "prompt-v2"));

        assertThat(reExtracting.reviewStatus()).isEqualTo(MetadataReviewStatus.RE_EXTRACTING);
        assertThat(canonicalWritePort.metadata).isEmpty();
        assertThat(compensationPort.documentId).isNull();
        assertThat(reExtracting.correctedMetadata())
                .containsEntry("reExtractJobId", "backfill-job-1")
                .containsEntry("extractorVersion", "extractor-v2")
                .containsEntry("pipelineId", "pipe-2")
                .containsEntry("llmExtractorVersion", "llm-v2")
                .containsEntry("llmPromptVersion", "prompt-v2")
                .containsEntry("documentId", "1");
        assertThat(reExtractPort.request)
                .extracting(MetadataReviewReExtractRequest::tenantId,
                        MetadataReviewReExtractRequest::knowledgeBaseId,
                        MetadataReviewReExtractRequest::documentId,
                        MetadataReviewReExtractRequest::reviewItemId,
                        MetadataReviewReExtractRequest::extractorVersion,
                        MetadataReviewReExtractRequest::pipelineId,
                        MetadataReviewReExtractRequest::llmExtractorVersion,
                        MetadataReviewReExtractRequest::llmPromptVersion,
                        MetadataReviewReExtractRequest::operator)
                .containsExactly("tenant-1", 1L, 1L, "review-1", "extractor-v2", "pipe-2",
                        "llm-v2", "prompt-v2", "auditor");
    }

    @Test
    void shouldQuarantineIndexCompensationFailureAfterApprove() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MetadataReviewStatus.PENDING, Map.of()));
        CapturingCanonicalWritePort canonicalWritePort = new CapturingCanonicalWritePort();
        List<MetadataQuarantineItem> quarantines = new java.util.ArrayList<>();
        KernelMetadataReviewService service = new KernelMetadataReviewService(
                repository, canonicalWritePort, quarantines::add, (tenantId, kbId, docId) -> {
                    throw new IllegalStateException("keyword rebuild failed");
                });

        MetadataReviewRecord approved = service.approve("review-1",
                new MetadataReviewDecisionCommand("auditor", "通过", Map.of()));

        assertThat(approved.reviewStatus()).isEqualTo(MetadataReviewStatus.APPROVED);
        assertThat(canonicalWritePort.metadata).containsEntry("department", "hr");
        assertThat(quarantines).hasSize(1);
        MetadataQuarantineItem quarantine = quarantines.get(0);
        assertThat(quarantine.stage()).isEqualTo("INDEX");
        assertThat(quarantine.reasonCode()).isEqualTo("METADATA_INDEX_COMPENSATION_FAILED");
        assertThat(quarantine.taskId()).isEqualTo("result-1");
        assertThat(quarantine.sourceSnapshot()).containsEntry("reviewItemId", "review-1");
    }

    @Test
    void shouldListReviewAuditsWithoutSideEffects() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MetadataReviewStatus.CORRECTED, Map.of("department", "legal")));
        repository.addAudit(reviewAudit("audit-1"));
        CapturingCanonicalWritePort canonicalWritePort = new CapturingCanonicalWritePort();
        CapturingIndexCompensationPort compensationPort = new CapturingIndexCompensationPort();
        KernelMetadataReviewService service = new KernelMetadataReviewService(
                repository, canonicalWritePort, MetadataQuarantinePort.noop(), compensationPort);

        List<MetadataReviewAuditRecord> audits = service.listAudits("review-1");

        assertThat(audits).hasSize(1);
        assertThat(audits.get(0).updatedMetadata()).containsEntry("department", "legal");
        assertThat(canonicalWritePort.metadata).isEmpty();
        assertThat(compensationPort.documentId).isNull();
    }

    private static MetadataReviewRecord review(String id,
                                               MetadataReviewStatus status,
                                               Map<String, Object> correctedMetadata) {
        return new MetadataReviewRecord(
                id,
                "tenant-1",
                1L,
                "1",
                "result-1",
                status,
                0,
                "LOW_CONFIDENCE",
                "字段置信度低",
                Map.of("department", "hr", "owner", "alice"),
                correctedMetadata,
                "auditor",
                "comment",
                Instant.EPOCH,
                Instant.EPOCH);
    }

    private static MetadataReviewAuditRecord reviewAudit(String id) {
        return new MetadataReviewAuditRecord(
                id,
                "review-1",
                "tenant-1",
                1L,
                "1",
                "result-1",
                "PENDING",
                "CORRECTED",
                "auditor",
                "修正部门",
                Map.of("department", "hr"),
                Map.of("department", "legal"),
                Map.of("department", "legal"),
                Instant.EPOCH);
    }

    private static final class InMemoryReviewRepository implements MetadataReviewManagementRepositoryPort {

        private final Map<String, MetadataReviewRecord> records = new LinkedHashMap<>();
        private final List<MetadataReviewAuditRecord> audits = new java.util.ArrayList<>();

        void put(MetadataReviewRecord record) {
            records.put(record.id(), record);
        }

        void addAudit(MetadataReviewAuditRecord audit) {
            audits.add(audit);
        }

        @Override
        public MetadataReviewPage pageReviewItems(MetadataReviewQuery query) {
            return new MetadataReviewPage(List.copyOf(records.values()), records.size(), query.size(),
                    query.current(), 1);
        }

        @Override
        public Optional<MetadataReviewRecord> findReviewItem(String itemId) {
            return Optional.ofNullable(records.get(itemId));
        }

        @Override
        public List<MetadataReviewAuditRecord> listReviewAudits(String itemId) {
            return audits.stream()
                    .filter(audit -> audit.reviewItemId().equals(itemId))
                    .toList();
        }

        @Override
        public MetadataReviewRecord applyReviewDecision(MetadataReviewDecision decision) {
            MetadataReviewRecord current = findReviewItem(decision.itemId()).orElseThrow();
            MetadataReviewRecord updated = new MetadataReviewRecord(
                    current.id(),
                    current.tenantId(),
                    current.knowledgeBaseId(),
                    current.documentId(),
                    current.resultId(),
                    decision.reviewStatus(),
                    current.priority(),
                    current.reasonCode(),
                    current.reasonMessage(),
                    current.suggestedMetadata(),
                    decision.correctedMetadata(),
                    decision.reviewerId(),
                    decision.reviewComment(),
                    current.createTime(),
                    Instant.EPOCH);
            records.put(updated.id(), updated);
            return updated;
        }
    }

    private static final class CapturingCanonicalWritePort implements MetadataCanonicalWritePort {

        private String documentId = "";
        private Map<String, Object> metadata = Map.of();

        @Override
        public void writeDocumentMetadata(String documentId, Map<String, Object> acceptedMetadata) {
            this.documentId = documentId;
            this.metadata = Map.copyOf(acceptedMetadata);
        }
    }

    private static final class CapturingIndexCompensationPort implements MetadataIndexCompensationPort {

        private String tenantId = "";
        private Long knowledgeBaseId = 0L;
        private Long documentId = null;

        @Override
        public void rebuildDocument(Long documentId) {
            this.documentId = documentId;
        }

        @Override
        public void rebuildDocument(String tenantId, Long knowledgeBaseId, Long documentId) {
            this.tenantId = tenantId;
            this.knowledgeBaseId = knowledgeBaseId;
            this.documentId = documentId;
        }
    }

    private static final class CapturingReExtractPort implements MetadataReviewReExtractPort {

        private MetadataReviewReExtractRequest request;

        @Override
        public String requestReExtract(MetadataReviewReExtractRequest request) {
            this.request = request;
            return "backfill-job-1";
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
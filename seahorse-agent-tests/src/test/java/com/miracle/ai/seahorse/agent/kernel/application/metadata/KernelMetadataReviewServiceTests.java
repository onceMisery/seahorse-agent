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
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewDecision;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewManagementRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewPage;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractPort;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewReExtractRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.metadata.MetadataReviewStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
        assertThat(canonicalWritePort.documentId).isEqualTo("doc-1");
        assertThat(canonicalWritePort.metadata).containsEntry("department", "hr");
        assertThat(compensationPort.documentId).isEqualTo("doc-1");
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
        assertThat(compensationPort.documentId).isEqualTo("doc-1");
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
                        "auditor", "重新抽取", Map.of(), List.of(), "extractor-v2", "pipe-2"));

        assertThat(reExtracting.reviewStatus()).isEqualTo(MetadataReviewStatus.RE_EXTRACTING);
        assertThat(canonicalWritePort.metadata).isEmpty();
        assertThat(compensationPort.documentId).isEmpty();
        assertThat(reExtracting.correctedMetadata())
                .containsEntry("reExtractJobId", "backfill-job-1")
                .containsEntry("extractorVersion", "extractor-v2")
                .containsEntry("pipelineId", "pipe-2")
                .containsEntry("documentId", "doc-1");
        assertThat(reExtractPort.request)
                .extracting(MetadataReviewReExtractRequest::tenantId,
                        MetadataReviewReExtractRequest::knowledgeBaseId,
                        MetadataReviewReExtractRequest::documentId,
                        MetadataReviewReExtractRequest::reviewItemId,
                        MetadataReviewReExtractRequest::extractorVersion,
                        MetadataReviewReExtractRequest::pipelineId,
                        MetadataReviewReExtractRequest::operator)
                .containsExactly("tenant-1", "kb-1", "doc-1", "review-1", "extractor-v2", "pipe-2", "auditor");
    }

    @Test
    void shouldQuarantineIndexCompensationFailureAfterApprove() {
        InMemoryReviewRepository repository = new InMemoryReviewRepository();
        repository.put(review("review-1", MetadataReviewStatus.PENDING, Map.of()));
        CapturingCanonicalWritePort canonicalWritePort = new CapturingCanonicalWritePort();
        List<MetadataQuarantineItem> quarantines = new java.util.ArrayList<>();
        KernelMetadataReviewService service = new KernelMetadataReviewService(
                repository, canonicalWritePort, quarantines::add, documentId -> {
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

    private static MetadataReviewRecord review(String id,
                                               MetadataReviewStatus status,
                                               Map<String, Object> correctedMetadata) {
        return new MetadataReviewRecord(
                id,
                "tenant-1",
                "kb-1",
                "doc-1",
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

    private static final class InMemoryReviewRepository implements MetadataReviewManagementRepositoryPort {

        private final Map<String, MetadataReviewRecord> records = new LinkedHashMap<>();

        void put(MetadataReviewRecord record) {
            records.put(record.id(), record);
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

        private String documentId = "";

        @Override
        public void rebuildDocument(String documentId) {
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
}

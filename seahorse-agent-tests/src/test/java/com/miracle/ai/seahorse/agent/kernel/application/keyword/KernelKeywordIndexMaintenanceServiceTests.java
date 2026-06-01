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

package com.miracle.ai.seahorse.agent.kernel.application.keyword;

import com.miracle.ai.seahorse.agent.kernel.domain.vector.VectorChunk;
import com.miracle.ai.seahorse.agent.ports.inbound.keyword.KeywordIndexRebuildResult;
import com.miracle.ai.seahorse.agent.ports.outbound.keyword.KeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeChunkRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentDetail;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentPage;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.observation.ObservationScope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelKeywordIndexMaintenanceServiceTests {

    @Test
    void shouldRebuildDocumentFromEnabledChunkSnapshot() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        repository.addDocument(document(1L, 1L, true));
        repository.addChunks(1L, List.of(
                chunk("1", 0, "元数据治理", Map.of("department", "研发", "file_type", "stale")),
                chunk("2", 1, "混合检索")));
        RecordingKeywordIndexPort keywordIndex = new RecordingKeywordIndexPort();
        KernelKeywordIndexMaintenanceService service = new KernelKeywordIndexMaintenanceService(repository, keywordIndex);

        KeywordIndexRebuildResult result = service.rebuildDocument(1L);

        assertThat(result.success()).isTrue();
        assertThat(result.processedDocuments()).isEqualTo(1);
        assertThat(result.indexedDocuments()).isEqualTo(1);
        assertThat(result.indexedChunks()).isEqualTo(2);
        assertThat(keywordIndex.operations).containsExactly("delete:1:1", "index:1:1:2");
        assertThat(keywordIndex.lastChunks.get(0).getMetadata())
                .containsEntry("kb_id", "1")
                .containsEntry("doc_id", "1")
                .containsEntry("collection_name", "collection-a")
                .containsEntry("department", "研发")
                .containsEntry("file_type", "text/plain")
                .containsEntry("source_type", "file");
    }

    @Test
    void shouldDeleteOnlyWhenDocumentDisabled() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        repository.addDocument(document(1L, 1L, false));
        repository.addChunks(1L, List.of(chunk("1", 0, "禁用文档")));
        RecordingKeywordIndexPort keywordIndex = new RecordingKeywordIndexPort();
        KernelKeywordIndexMaintenanceService service = new KernelKeywordIndexMaintenanceService(repository, keywordIndex);

        KeywordIndexRebuildResult result = service.rebuildDocument(1L);

        assertThat(result.indexedDocuments()).isZero();
        assertThat(result.skippedDocuments()).isEqualTo(1);
        assertThat(keywordIndex.operations).containsExactly("delete:1:1");
    }

    @Test
    void shouldRebuildKnowledgeBaseByPagesAndKeepFailureSummary() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        repository.addDocument(document(1L, 1L, true));
        repository.addDocument(document(2L, 1L, true));
        repository.addChunks(1L, List.of(chunk("1", 0, "成功文档")));
        repository.addChunks(2L, List.of(chunk("2", 0, "失败文档")));
        RecordingKeywordIndexPort keywordIndex = new RecordingKeywordIndexPort();
        keywordIndex.failOnDocId = "2";
        RecordingObservationPort observationPort = new RecordingObservationPort();
        KernelKeywordIndexMaintenanceService service = new KernelKeywordIndexMaintenanceService(
                repository, keywordIndex, observationPort);

        KeywordIndexRebuildResult result = service.rebuildKnowledgeBase(1L, 1);

        assertThat(result.processedDocuments()).isEqualTo(2);
        assertThat(result.indexedDocuments()).isEqualTo(1);
        assertThat(result.indexedChunks()).isEqualTo(1);
        assertThat(result.failedDocuments()).isEqualTo(1);
        assertThat(result.failures()).singleElement().asString().contains("2");
        assertThat(observationPort.events)
                .extracting(ObservationEvent::name)
                .contains("keyword.index.rebuild.failure");
    }

    private static KnowledgeDocumentDetail document(Long docId, Long kbId, boolean enabled) {
        KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
        detail.setId(docId);
        detail.setKbId(kbId);
        detail.setDocName("设计文档");
        detail.setCollectionName("collection-a");
        detail.setEnabled(enabled);
        detail.setFileType("text/plain");
        detail.setSourceType("file");
        return detail;
    }

    private static KnowledgeChunkRecord chunk(String chunkId, int index, String content) {
        return chunk(chunkId, index, content, Map.of());
    }

    private static KnowledgeChunkRecord chunk(String chunkId, int index, String content, Map<String, Object> metadata) {
        KnowledgeChunkRecord record = new KnowledgeChunkRecord();
        record.setId(Long.parseLong(chunkId));
        record.setChunkIndex(index);
        record.setContent(content);
        record.setEnabled(1);
        record.setMetadata(metadata);
        return record;
    }

    private static final class InMemoryDocumentRepository implements KnowledgeDocumentRepositoryPort {

        private final Map<Long, KnowledgeDocumentDetail> documents = new LinkedHashMap<>();
        private final Map<Long, List<KnowledgeChunkRecord>> chunks = new LinkedHashMap<>();

        void addDocument(KnowledgeDocumentDetail document) {
            documents.put(document.getId(), document);
        }

        void addChunks(Long docId, List<KnowledgeChunkRecord> records) {
            chunks.put(docId, records);
        }

        @Override
        public Optional<KnowledgeDocumentDetail> findDetailById(Long docId) {
            return Optional.ofNullable(documents.get(docId));
        }

        @Override
        public KnowledgeDocumentPage page(Long kbId, long current, long size, String status, String keyword) {
            List<KnowledgeDocumentDetail> matched = documents.values().stream()
                    .filter(document -> kbId.equals(document.getKbId()))
                    .toList();
            int from = (int) Math.min((current - 1) * size, matched.size());
            int to = (int) Math.min(from + size, matched.size());
            long pages = (long) Math.ceil(matched.size() / (double) size);
            return new KnowledgeDocumentPage(matched.subList(from, to), matched.size(), size, current, pages);
        }

        @Override
        public List<KnowledgeChunkRecord> listEnabledChunks(Long docId) {
            return chunks.getOrDefault(docId, List.of()).stream()
                    .filter(record -> record.getEnabled() == null || record.getEnabled() == 1)
                    .toList();
        }

        @Override
        public com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord createPendingDocument(
                com.miracle.ai.seahorse.agent.ports.outbound.knowledge.CreateKnowledgeDocumentCommand command) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeDocumentRecord> findById(
                Long docId) {
            return Optional.empty();
        }

        @Override
        public boolean markRunning(Long docId, String operator) {
            return false;
        }

        @Override
        public void markSuccess(Long docId, int chunkCount, String operator) {
        }

        @Override
        public void markFailed(Long docId, String operator, String errorMessage) {
        }

        @Override
        public boolean updateEnabled(Long docId, boolean enabled, String operator) {
            return false;
        }

        @Override
        public boolean delete(Long docId, String operator) {
            return false;
        }
    }

    private static final class RecordingKeywordIndexPort implements KeywordIndexPort {

        private final List<String> operations = new ArrayList<>();
        private List<VectorChunk> lastChunks = List.of();
        private String failOnDocId;

        @Override
        public void indexDocumentChunks(String kbId, String docId, List<VectorChunk> chunks) {
            operations.add("index:" + kbId + ":" + docId + ":" + chunks.size());
            if (docId.equals(failOnDocId)) {
                throw new IllegalStateException("index failed");
            }
            lastChunks = chunks;
        }

        @Override
        public void deleteDocumentChunks(String kbId, String docId) {
            operations.add("delete:" + kbId + ":" + docId);
        }
    }

    private static final class RecordingObservationPort implements ObservationPort {

        private final List<ObservationEvent> events = new ArrayList<>();

        @Override
        public ObservationScope start(ObservationCommand command) {
            return new RecordingObservationScope(events);
        }

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }
    }

    private record RecordingObservationScope(List<ObservationEvent> events) implements ObservationScope {

        @Override
        public void recordEvent(ObservationEvent event) {
            events.add(event);
        }

        @Override
        public void close() {
        }
    }
}
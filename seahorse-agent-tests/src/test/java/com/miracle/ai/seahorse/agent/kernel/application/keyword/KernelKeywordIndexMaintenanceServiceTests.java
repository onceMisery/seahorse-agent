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
        repository.addDocument(document("doc-1", "kb-1", true));
        repository.addChunks("doc-1", List.of(chunk("chunk-1", 0, "元数据治理"), chunk("chunk-2", 1, "混合检索")));
        RecordingKeywordIndexPort keywordIndex = new RecordingKeywordIndexPort();
        KernelKeywordIndexMaintenanceService service = new KernelKeywordIndexMaintenanceService(repository, keywordIndex);

        KeywordIndexRebuildResult result = service.rebuildDocument("doc-1");

        assertThat(result.success()).isTrue();
        assertThat(result.processedDocuments()).isEqualTo(1);
        assertThat(result.indexedDocuments()).isEqualTo(1);
        assertThat(result.indexedChunks()).isEqualTo(2);
        assertThat(keywordIndex.operations).containsExactly("delete:kb-1:doc-1", "index:kb-1:doc-1:2");
        assertThat(keywordIndex.lastChunks.get(0).getMetadata())
                .containsEntry("kb_id", "kb-1")
                .containsEntry("doc_id", "doc-1")
                .containsEntry("collection_name", "collection-a");
    }

    @Test
    void shouldDeleteOnlyWhenDocumentDisabled() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        repository.addDocument(document("doc-1", "kb-1", false));
        repository.addChunks("doc-1", List.of(chunk("chunk-1", 0, "禁用文档")));
        RecordingKeywordIndexPort keywordIndex = new RecordingKeywordIndexPort();
        KernelKeywordIndexMaintenanceService service = new KernelKeywordIndexMaintenanceService(repository, keywordIndex);

        KeywordIndexRebuildResult result = service.rebuildDocument("doc-1");

        assertThat(result.indexedDocuments()).isZero();
        assertThat(result.skippedDocuments()).isEqualTo(1);
        assertThat(keywordIndex.operations).containsExactly("delete:kb-1:doc-1");
    }

    @Test
    void shouldRebuildKnowledgeBaseByPagesAndKeepFailureSummary() {
        InMemoryDocumentRepository repository = new InMemoryDocumentRepository();
        repository.addDocument(document("doc-1", "kb-1", true));
        repository.addDocument(document("doc-2", "kb-1", true));
        repository.addChunks("doc-1", List.of(chunk("chunk-1", 0, "成功文档")));
        repository.addChunks("doc-2", List.of(chunk("chunk-2", 0, "失败文档")));
        RecordingKeywordIndexPort keywordIndex = new RecordingKeywordIndexPort();
        keywordIndex.failOnDocId = "doc-2";
        KernelKeywordIndexMaintenanceService service = new KernelKeywordIndexMaintenanceService(repository, keywordIndex);

        KeywordIndexRebuildResult result = service.rebuildKnowledgeBase("kb-1", 1);

        assertThat(result.processedDocuments()).isEqualTo(2);
        assertThat(result.indexedDocuments()).isEqualTo(1);
        assertThat(result.indexedChunks()).isEqualTo(1);
        assertThat(result.failedDocuments()).isEqualTo(1);
        assertThat(result.failures()).singleElement().asString().contains("doc-2");
    }

    private static KnowledgeDocumentDetail document(String docId, String kbId, boolean enabled) {
        KnowledgeDocumentDetail detail = new KnowledgeDocumentDetail();
        detail.setId(docId);
        detail.setKbId(kbId);
        detail.setDocName("设计文档");
        detail.setCollectionName("collection-a");
        detail.setEnabled(enabled);
        return detail;
    }

    private static KnowledgeChunkRecord chunk(String chunkId, int index, String content) {
        KnowledgeChunkRecord record = new KnowledgeChunkRecord();
        record.setId(chunkId);
        record.setChunkIndex(index);
        record.setContent(content);
        record.setEnabled(1);
        return record;
    }

    private static final class InMemoryDocumentRepository implements KnowledgeDocumentRepositoryPort {

        private final Map<String, KnowledgeDocumentDetail> documents = new LinkedHashMap<>();
        private final Map<String, List<KnowledgeChunkRecord>> chunks = new LinkedHashMap<>();

        void addDocument(KnowledgeDocumentDetail document) {
            documents.put(document.getId(), document);
        }

        void addChunks(String docId, List<KnowledgeChunkRecord> records) {
            chunks.put(docId, records);
        }

        @Override
        public Optional<KnowledgeDocumentDetail> findDetailById(String docId) {
            return Optional.ofNullable(documents.get(docId));
        }

        @Override
        public KnowledgeDocumentPage page(String kbId, long current, long size, String status, String keyword) {
            List<KnowledgeDocumentDetail> matched = documents.values().stream()
                    .filter(document -> kbId.equals(document.getKbId()))
                    .toList();
            int from = (int) Math.min((current - 1) * size, matched.size());
            int to = (int) Math.min(from + size, matched.size());
            long pages = (long) Math.ceil(matched.size() / (double) size);
            return new KnowledgeDocumentPage(matched.subList(from, to), matched.size(), size, current, pages);
        }

        @Override
        public List<KnowledgeChunkRecord> listEnabledChunks(String docId) {
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
                String docId) {
            return Optional.empty();
        }

        @Override
        public boolean markRunning(String docId, String operator) {
            return false;
        }

        @Override
        public void markSuccess(String docId, int chunkCount, String operator) {
        }

        @Override
        public void markFailed(String docId, String operator, String errorMessage) {
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
}

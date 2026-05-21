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

package com.miracle.ai.seahorse.agent.kernel.application.memory.outbox;

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDeleteCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDocument;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryGraphIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskTypes;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryDerivedIndexOutboxTaskHandlerTests {

    @Test
    void shouldConvertVectorDeleteTaskIntoVectorDeleteCommand() {
        RecordingVectorPort vectorPort = new RecordingVectorPort();
        VectorMemoryOutboxTaskHandler handler = new VectorMemoryOutboxTaskHandler(
                vectorPort,
                MemoryOutboxTaskTypes.VECTOR_DELETE);

        handler.handle(MemoryOutboxPort.MemoryOutboxTask.vectorDelete("mem-1", "user-1", "tenant-1"));

        assertThat(vectorPort.deletes).containsExactly("mem-1|user-1|tenant-1");
    }

    @Test
    void shouldFailVectorDeleteWhenPortDoesNotSupportDelete() {
        VectorMemoryOutboxTaskHandler handler = new VectorMemoryOutboxTaskHandler(
                new UpsertOnlyVectorPort(),
                MemoryOutboxTaskTypes.VECTOR_DELETE);

        assertThatThrownBy(() -> handler.handle(
                MemoryOutboxPort.MemoryOutboxTask.vectorDelete("mem-1", "user-1", "tenant-1")))
                .isInstanceOf(UnsupportedOperationException.class)
                .hasMessageContaining("delete");
    }

    @Test
    void shouldConvertKeywordUpsertTaskIntoIndexDocument() {
        RecordingKeywordIndexPort indexPort = new RecordingKeywordIndexPort();
        KeywordMemoryOutboxTaskHandler handler = new KeywordMemoryOutboxTaskHandler(
                indexPort,
                MemoryOutboxTaskTypes.KEYWORD_UPSERT);
        MemoryRecord record = memoryRecord();

        handler.handle(MemoryOutboxPort.MemoryOutboxTask.keywordUpsert(record, "user-1", "tenant-1"));

        assertThat(indexPort.upserts).hasSize(1);
        MemoryDerivedIndexDocument document = indexPort.upserts.get(0);
        assertThat(document.memoryId()).isEqualTo("mem-1");
        assertThat(document.userId()).isEqualTo("user-1");
        assertThat(document.tenantId()).isEqualTo("tenant-1");
        assertThat(document.layer()).isEqualTo("LONG_TERM");
        assertThat(document.type()).isEqualTo("PROJECT_FACT");
        assertThat(document.content()).isEqualTo("User is migrating storage to OceanBase.");
        assertThat(document.metadata()).containsEntry("semanticKey", "project.storage");
        assertThat(document.updatedAt()).isEqualTo(Instant.parse("2026-05-21T08:00:00Z"));
    }

    @Test
    void shouldConvertKeywordDeleteTaskIntoDeleteCommand() {
        RecordingKeywordIndexPort indexPort = new RecordingKeywordIndexPort();
        KeywordMemoryOutboxTaskHandler handler = new KeywordMemoryOutboxTaskHandler(
                indexPort,
                MemoryOutboxTaskTypes.KEYWORD_DELETE);

        handler.handle(MemoryOutboxPort.MemoryOutboxTask.keywordDelete("mem-1", "user-1", "tenant-1"));

        assertThat(indexPort.deletes)
                .extracting(MemoryDerivedIndexDeleteCommand::memoryId)
                .containsExactly("mem-1");
    }

    @Test
    void shouldConvertGraphUpsertTaskIntoIndexDocument() {
        RecordingGraphIndexPort indexPort = new RecordingGraphIndexPort();
        GraphMemoryOutboxTaskHandler handler = new GraphMemoryOutboxTaskHandler(
                indexPort,
                MemoryOutboxTaskTypes.GRAPH_UPSERT);

        handler.handle(MemoryOutboxPort.MemoryOutboxTask.graphUpsert(memoryRecord(), "user-1", "tenant-1"));

        assertThat(indexPort.upserts)
                .extracting(MemoryDerivedIndexDocument::memoryId)
                .containsExactly("mem-1");
    }

    @Test
    void shouldConvertGraphDeleteTaskIntoDeleteCommand() {
        RecordingGraphIndexPort indexPort = new RecordingGraphIndexPort();
        GraphMemoryOutboxTaskHandler handler = new GraphMemoryOutboxTaskHandler(
                indexPort,
                MemoryOutboxTaskTypes.GRAPH_DELETE);

        handler.handle(MemoryOutboxPort.MemoryOutboxTask.graphDelete("mem-1", "user-1", "tenant-1"));

        assertThat(indexPort.deletes)
                .extracting(MemoryDerivedIndexDeleteCommand::memoryId)
                .containsExactly("mem-1");
    }

    private MemoryRecord memoryRecord() {
        return new MemoryRecord(
                "mem-1",
                "LONG_TERM",
                "PROJECT_FACT",
                "User is migrating storage to OceanBase.",
                Map.of("semanticKey", "project.storage"),
                Instant.parse("2026-05-21T08:00:00Z"));
    }

    private static class RecordingVectorPort implements MemoryVectorPort {

        private final List<String> deletes = new ArrayList<>();

        @Override
        public void upsert(String memoryId, String userId, String content, String embeddingModel) {
        }

        @Override
        public List<String> search(String userId, String query, int topK) {
            return List.of();
        }

        @Override
        public void delete(String memoryId, String userId, String tenantId) {
            deletes.add(memoryId + "|" + userId + "|" + tenantId);
        }
    }

    private static class UpsertOnlyVectorPort implements MemoryVectorPort {

        @Override
        public void upsert(String memoryId, String userId, String content, String embeddingModel) {
        }

        @Override
        public List<String> search(String userId, String query, int topK) {
            return List.of();
        }
    }

    private static class RecordingKeywordIndexPort implements MemoryKeywordIndexPort {

        private final List<MemoryDerivedIndexDocument> upserts = new ArrayList<>();
        private final List<MemoryDerivedIndexDeleteCommand> deletes = new ArrayList<>();

        @Override
        public void upsert(MemoryDerivedIndexDocument document) {
            upserts.add(document);
        }

        @Override
        public void delete(MemoryDerivedIndexDeleteCommand command) {
            deletes.add(command);
        }
    }

    private static class RecordingGraphIndexPort implements MemoryGraphIndexPort {

        private final List<MemoryDerivedIndexDocument> upserts = new ArrayList<>();
        private final List<MemoryDerivedIndexDeleteCommand> deletes = new ArrayList<>();

        @Override
        public void upsert(MemoryDerivedIndexDocument document) {
            upserts.add(document);
        }

        @Override
        public void delete(MemoryDerivedIndexDeleteCommand command) {
            deletes.add(command);
        }
    }
}

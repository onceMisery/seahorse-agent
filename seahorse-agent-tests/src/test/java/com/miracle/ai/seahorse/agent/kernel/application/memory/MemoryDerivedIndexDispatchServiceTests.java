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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskTypes;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

class MemoryDerivedIndexDispatchServiceTests {

    private static final String USER_ID = "user-1";
    private static final String TENANT_ID = "tenant-1";
    private static final String EMBEDDING_MODEL = "default";

    private final RecordingVectorPort vectorPort = new RecordingVectorPort();
    private final RecordingOutboxPort outboxPort = new RecordingOutboxPort();

    @Test
    void dispatchUpsertWritesVectorAndReturnsExpectedOperationStrings() {
        MemoryDerivedIndexDispatchService service = newService(true, true);
        MemoryRecord record = newRecord("memory-1", "hello");

        List<String> operations = service.dispatchUpsert(record, USER_ID, TENANT_ID);

        Assertions.assertEquals(
                List.of(
                        MemoryDerivedIndexDispatchService.OPERATION_VECTOR_UPSERT,
                        MemoryDerivedIndexDispatchService.OPERATION_KEYWORD_OUTBOX_ENQUEUE,
                        MemoryDerivedIndexDispatchService.OPERATION_GRAPH_OUTBOX_ENQUEUE),
                operations);
        Assertions.assertEquals(1, vectorPort.upserts.size());
        Assertions.assertEquals(2, outboxPort.tasks.size());
        Assertions.assertEquals(MemoryOutboxTaskTypes.KEYWORD_UPSERT, outboxPort.tasks.get(0).taskType());
        Assertions.assertEquals(MemoryOutboxTaskTypes.GRAPH_UPSERT, outboxPort.tasks.get(1).taskType());
    }

    @Test
    void dispatchUpsertFallsBackToOutboxOnVectorFailure() {
        vectorPort.upsertException = new IllegalStateException("milvus down");
        MemoryDerivedIndexDispatchService service = newService(false, false);
        MemoryRecord record = newRecord("memory-2", "fail");

        List<String> operations = service.dispatchUpsert(record, USER_ID, TENANT_ID);

        Assertions.assertEquals(
                List.of(MemoryDerivedIndexDispatchService.OPERATION_VECTOR_OUTBOX_ENQUEUE),
                operations);
        Assertions.assertEquals(1, outboxPort.tasks.size());
        Assertions.assertEquals(MemoryOutboxTaskTypes.VECTOR_UPSERT, outboxPort.tasks.get(0).taskType());
        Assertions.assertEquals("milvus down", outboxPort.tasks.get(0).errorMessage());
    }

    @Test
    void dispatchDeleteEmitsAllOpsWhenFlagsEnabled() {
        MemoryDerivedIndexDispatchService service = newService(true, true);

        List<String> operations = service.dispatchDelete("memory-3", USER_ID, TENANT_ID);

        Assertions.assertEquals(
                List.of(
                        MemoryDerivedIndexDispatchService.OPERATION_VECTOR_DELETE,
                        MemoryDerivedIndexDispatchService.OPERATION_KEYWORD_DELETE_OUTBOX_ENQUEUE,
                        MemoryDerivedIndexDispatchService.OPERATION_GRAPH_DELETE_OUTBOX_ENQUEUE),
                operations);
        Assertions.assertEquals(1, vectorPort.deletes.size());
        Assertions.assertEquals(2, outboxPort.tasks.size());
        Assertions.assertEquals(MemoryOutboxTaskTypes.KEYWORD_DELETE, outboxPort.tasks.get(0).taskType());
        Assertions.assertEquals(MemoryOutboxTaskTypes.GRAPH_DELETE, outboxPort.tasks.get(1).taskType());
    }

    @Test
    void dispatchDeleteFallsBackToOutboxOnVectorFailure() {
        vectorPort.deleteException = new IllegalStateException("milvus delete down");
        MemoryDerivedIndexDispatchService service = newService(true, false);

        List<String> operations = service.dispatchDelete("memory-4", USER_ID, TENANT_ID);

        Assertions.assertEquals(
                List.of(
                        MemoryDerivedIndexDispatchService.OPERATION_VECTOR_DELETE_OUTBOX_ENQUEUE,
                        MemoryDerivedIndexDispatchService.OPERATION_KEYWORD_DELETE_OUTBOX_ENQUEUE),
                operations);
        Assertions.assertEquals(2, outboxPort.tasks.size());
        Assertions.assertEquals(MemoryOutboxTaskTypes.VECTOR_DELETE, outboxPort.tasks.get(0).taskType());
        Assertions.assertEquals(MemoryOutboxTaskTypes.KEYWORD_DELETE, outboxPort.tasks.get(1).taskType());
    }

    @Test
    void disablingBothDerivedFlagsSkipsOutboxOnSuccessPath() {
        MemoryDerivedIndexDispatchService service = newService(false, false);

        List<String> upsertOps = service.dispatchUpsert(newRecord("m-5", "x"), USER_ID, TENANT_ID);
        List<String> deleteOps = service.dispatchDelete("m-5", USER_ID, TENANT_ID);

        Assertions.assertEquals(List.of(MemoryDerivedIndexDispatchService.OPERATION_VECTOR_UPSERT), upsertOps);
        Assertions.assertEquals(List.of(MemoryDerivedIndexDispatchService.OPERATION_VECTOR_DELETE), deleteOps);
        Assertions.assertEquals(0, outboxPort.tasks.size());
    }

    private MemoryDerivedIndexDispatchService newService(boolean keywordEnabled, boolean graphEnabled) {
        return new MemoryDerivedIndexDispatchService(vectorPort, outboxPort, keywordEnabled, graphEnabled,
                EMBEDDING_MODEL);
    }

    private static MemoryRecord newRecord(String id, String content) {
        return new MemoryRecord(id, "SHORT_TERM", "FACT", content, Map.of(), Instant.parse("2026-05-24T00:00:00Z"));
    }

    private static final class RecordingVectorPort implements MemoryVectorPort {
        private final List<String> upserts = new ArrayList<>();
        private final List<String> deletes = new ArrayList<>();
        private RuntimeException upsertException;
        private RuntimeException deleteException;

        @Override
        public void upsert(String memoryId, String userId, String content, String embeddingModel) {
            if (upsertException != null) {
                throw upsertException;
            }
            upserts.add(memoryId);
        }

        @Override
        public void delete(String memoryId, String userId, String tenantId) {
            if (deleteException != null) {
                throw deleteException;
            }
            deletes.add(memoryId);
        }

        @Override
        public List<String> search(String userId, String query, int topK) {
            return List.of();
        }
    }

    private static final class RecordingOutboxPort implements MemoryOutboxPort {
        private final List<MemoryOutboxTask> tasks = new ArrayList<>();

        @Override
        public void enqueue(MemoryOutboxTask task) {
            tasks.add(task);
        }
    }
}

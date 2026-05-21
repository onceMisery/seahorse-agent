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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskTypes;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.kernel.application.memory.outbox.VectorMemoryOutboxTaskHandler;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MemoryOutboxRelayServiceTests {

    @Test
    void shouldRelayVectorUpsertAndMarkTaskSucceeded() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of(task(
                "outbox-1",
                "VECTOR_UPSERT",
                Map.of("memoryId", "stm-1", "content", "prefers concise answers", "embeddingModel", "default"))));
        RecordingVectorPort vectorPort = new RecordingVectorPort();
        MemoryOutboxRelayService service = new MemoryOutboxRelayService(outboxPort, vectorPort);

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(vectorPort.upserts).containsExactly("stm-1|user-1|prefers concise answers|default");
        assertThat(outboxPort.succeeded).containsExactly("outbox-1");
        assertThat(outboxPort.failed).isEmpty();
    }

    @Test
    void shouldRelayVectorDeleteAndMarkTaskSucceeded() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of(task(
                "outbox-1",
                "VECTOR_DELETE",
                Map.of("memoryId", "stm-1"))));
        RecordingVectorPort vectorPort = new RecordingVectorPort();
        MemoryOutboxRelayService service = new MemoryOutboxRelayService(outboxPort, vectorPort);

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(vectorPort.deletes).containsExactly("stm-1|user-1|default");
        assertThat(outboxPort.succeeded).containsExactly("outbox-1");
        assertThat(outboxPort.failed).isEmpty();
    }

    @Test
    void shouldMarkFailedAndContinueWhenVectorUpsertFails() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of(
                task("outbox-1", "VECTOR_UPSERT",
                        Map.of("memoryId", "stm-fail", "content", "first", "embeddingModel", "default")),
                task("outbox-2", "VECTOR_UPSERT",
                        Map.of("memoryId", "stm-ok", "content", "second", "embeddingModel", "default"))));
        RecordingVectorPort vectorPort = new RecordingVectorPort();
        vectorPort.failMemoryIds.add("stm-fail");
        MemoryOutboxRelayService service = new MemoryOutboxRelayService(outboxPort, vectorPort);

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(2);
        assertThat(vectorPort.upserts).containsExactly(
                "stm-fail|user-1|first|default",
                "stm-ok|user-1|second|default");
        assertThat(outboxPort.succeeded).containsExactly("outbox-2");
        assertThat(outboxPort.failed).containsKey("outbox-1");
        assertThat(outboxPort.failed.get("outbox-1")).contains("vector down");
    }

    @Test
    void shouldFailUnsupportedTaskWithoutBlockingFollowingTasks() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of(
                task("outbox-unsupported", "KEYWORD_UPSERT", Map.of("memoryId", "stm-1")),
                task("outbox-vector", "VECTOR_UPSERT",
                        Map.of("memoryId", "stm-2", "content", "second", "embeddingModel", "default"))));
        RecordingVectorPort vectorPort = new RecordingVectorPort();
        MemoryOutboxRelayService service = new MemoryOutboxRelayService(outboxPort, vectorPort);

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(2);
        assertThat(outboxPort.failed).containsKey("outbox-unsupported");
        assertThat(outboxPort.failed.get("outbox-unsupported")).contains("unsupported task type");
        assertThat(outboxPort.succeeded).containsExactly("outbox-vector");
    }

    @Test
    void shouldDispatchTasksToRegisteredHandlersByTaskType() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of(
                task("outbox-keyword", "KEYWORD_UPSERT",
                        Map.of("memoryId", "stm-1", "content", "domain-specific phrase")),
                task("outbox-vector", "VECTOR_UPSERT",
                        Map.of("memoryId", "stm-2", "content", "second", "embeddingModel", "default"))));
        RecordingTaskHandler keywordHandler = new RecordingTaskHandler("KEYWORD_UPSERT");
        RecordingTaskHandler vectorHandler = new RecordingTaskHandler("VECTOR_UPSERT");
        MemoryOutboxRelayService service = new MemoryOutboxRelayService(
                outboxPort,
                List.of(keywordHandler, vectorHandler));

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(2);
        assertThat(keywordHandler.handledTaskIds).containsExactly("outbox-keyword");
        assertThat(vectorHandler.handledTaskIds).containsExactly("outbox-vector");
        assertThat(outboxPort.succeeded).containsExactly("outbox-keyword", "outbox-vector");
        assertThat(outboxPort.failed).isEmpty();
    }

    @Test
    void shouldMarkTaskFailedWhenRegisteredHandlerFails() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of(
                task("outbox-keyword", "KEYWORD_UPSERT", Map.of("memoryId", "stm-1")),
                task("outbox-vector", "VECTOR_UPSERT",
                        Map.of("memoryId", "stm-2", "content", "second", "embeddingModel", "default"))));
        RecordingTaskHandler keywordHandler = new RecordingTaskHandler("KEYWORD_UPSERT");
        keywordHandler.fail = true;
        RecordingTaskHandler vectorHandler = new RecordingTaskHandler("VECTOR_UPSERT");
        MemoryOutboxRelayService service = new MemoryOutboxRelayService(
                outboxPort,
                List.of(keywordHandler, vectorHandler));

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(2);
        assertThat(outboxPort.failed).containsKey("outbox-keyword");
        assertThat(outboxPort.failed.get("outbox-keyword")).contains("handler down");
        assertThat(outboxPort.succeeded).containsExactly("outbox-vector");
    }

    @Test
    void shouldPreferCustomHandlerOverBuiltInHandlerForSameTaskType() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of(
                task("outbox-vector-delete", MemoryOutboxTaskTypes.VECTOR_DELETE, Map.of("memoryId", "stm-1"))));
        RecordingVectorPort vectorPort = new RecordingVectorPort();
        RecordingTaskHandler customDeleteHandler = new RecordingTaskHandler(MemoryOutboxTaskTypes.VECTOR_DELETE);
        MemoryOutboxRelayService service = new MemoryOutboxRelayService(
                outboxPort,
                List.of(
                        new VectorMemoryOutboxTaskHandler(vectorPort, MemoryOutboxTaskTypes.VECTOR_DELETE),
                        customDeleteHandler));

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(customDeleteHandler.handledTaskIds).containsExactly("outbox-vector-delete");
        assertThat(vectorPort.deletes).isEmpty();
        assertThat(outboxPort.succeeded).containsExactly("outbox-vector-delete");
        assertThat(outboxPort.failed).isEmpty();
    }

    @Test
    void shouldKeepCustomHandlerWhenBuiltInHandlerIsRegisteredLaterForSameTaskType() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of(
                task("outbox-vector-delete", MemoryOutboxTaskTypes.VECTOR_DELETE, Map.of("memoryId", "stm-1"))));
        RecordingVectorPort vectorPort = new RecordingVectorPort();
        RecordingTaskHandler customDeleteHandler = new RecordingTaskHandler(MemoryOutboxTaskTypes.VECTOR_DELETE);
        MemoryOutboxRelayService service = new MemoryOutboxRelayService(
                outboxPort,
                List.of(
                        customDeleteHandler,
                        new VectorMemoryOutboxTaskHandler(vectorPort, MemoryOutboxTaskTypes.VECTOR_DELETE)));

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(customDeleteHandler.handledTaskIds).containsExactly("outbox-vector-delete");
        assertThat(vectorPort.deletes).isEmpty();
        assertThat(outboxPort.succeeded).containsExactly("outbox-vector-delete");
        assertThat(outboxPort.failed).isEmpty();
    }

    @Test
    void shouldPreferCustomHandlerOverAnyHandlerMarkedBuiltInForSameTaskType() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of(
                task("outbox-custom", "CUSTOM_DELETE", Map.of("memoryId", "stm-1"))));
        RecordingTaskHandler builtInHandler = new RecordingTaskHandler("CUSTOM_DELETE");
        builtInHandler.builtIn = true;
        RecordingTaskHandler customHandler = new RecordingTaskHandler("CUSTOM_DELETE");
        MemoryOutboxRelayService service = new MemoryOutboxRelayService(
                outboxPort,
                List.of(builtInHandler, customHandler));

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(builtInHandler.handledTaskIds).isEmpty();
        assertThat(customHandler.handledTaskIds).containsExactly("outbox-custom");
        assertThat(outboxPort.succeeded).containsExactly("outbox-custom");
        assertThat(outboxPort.failed).isEmpty();
    }

    @Test
    void shouldRejectDuplicateCustomHandlersForSameTaskType() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of());

        assertThatThrownBy(() -> new MemoryOutboxRelayService(
                outboxPort,
                List.of(
                        new RecordingTaskHandler(MemoryOutboxTaskTypes.VECTOR_DELETE),
                        new RecordingTaskHandler(MemoryOutboxTaskTypes.VECTOR_DELETE))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate memory outbox task handler");
    }

    @Test
    void shouldIgnoreHandlersWithoutTaskType() {
        RecordingOutboxPort outboxPort = new RecordingOutboxPort(List.of(
                task("outbox-vector", "VECTOR_UPSERT",
                        Map.of("memoryId", "stm-1", "content", "first", "embeddingModel", "default"))));
        RecordingVectorPort vectorPort = new RecordingVectorPort();
        MemoryOutboxRelayService service = new MemoryOutboxRelayService(
                outboxPort,
                List.of(
                        new RecordingTaskHandler(null),
                        new RecordingTaskHandler(""),
                        new VectorMemoryOutboxTaskHandler(vectorPort, MemoryOutboxTaskTypes.VECTOR_UPSERT)));

        int processed = service.processBatch(10);

        assertThat(processed).isEqualTo(1);
        assertThat(vectorPort.upserts).containsExactly("stm-1|user-1|first|default");
        assertThat(outboxPort.succeeded).containsExactly("outbox-vector");
        assertThat(outboxPort.failed).isEmpty();
    }

    private MemoryOutboxPort.MemoryOutboxTask task(String id, String type, Map<String, Object> payload) {
        return new MemoryOutboxPort.MemoryOutboxTask(
                id,
                type,
                payload.getOrDefault("memoryId", "").toString(),
                "user-1",
                "default",
                payload,
                "",
                null,
                Instant.EPOCH);
    }

    private static class RecordingOutboxPort implements MemoryOutboxPort {

        private final List<MemoryOutboxTask> tasks;
        private final List<String> succeeded = new ArrayList<>();
        private final Map<String, String> failed = new java.util.LinkedHashMap<>();

        private RecordingOutboxPort(List<MemoryOutboxTask> tasks) {
            this.tasks = tasks;
        }

        @Override
        public void enqueue(MemoryOutboxTask task) {
            tasks.add(task);
        }

        @Override
        public List<MemoryOutboxTask> pollPending(int limit) {
            return tasks.stream().limit(limit).toList();
        }

        @Override
        public void markSucceeded(String taskId) {
            succeeded.add(taskId);
        }

        @Override
        public void markFailed(String taskId, String errorMessage) {
            failed.put(taskId, errorMessage);
        }
    }

    private static class RecordingVectorPort implements MemoryVectorPort {

        private final List<String> upserts = new ArrayList<>();
        private final List<String> deletes = new ArrayList<>();
        private final List<String> failMemoryIds = new ArrayList<>();

        @Override
        public void upsert(String memoryId, String userId, String content, String embeddingModel) {
            upserts.add(memoryId + "|" + userId + "|" + content + "|" + embeddingModel);
            if (failMemoryIds.contains(memoryId)) {
                throw new RuntimeException("vector down");
            }
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

    private static class RecordingTaskHandler implements MemoryOutboxTaskHandler {

        private final String taskType;
        private final List<String> handledTaskIds = new ArrayList<>();
        private boolean builtIn;
        private boolean fail;

        private RecordingTaskHandler(String taskType) {
            this.taskType = taskType;
        }

        @Override
        public String taskType() {
            return taskType;
        }

        @Override
        public boolean builtIn() {
            return builtIn;
        }

        @Override
        public void handle(MemoryOutboxPort.MemoryOutboxTask task) {
            handledTaskIds.add(task.id());
            if (fail) {
                throw new RuntimeException("handler down");
            }
        }
    }
}

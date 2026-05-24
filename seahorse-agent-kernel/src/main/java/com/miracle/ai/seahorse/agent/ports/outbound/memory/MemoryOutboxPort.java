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

package com.miracle.ai.seahorse.agent.ports.outbound.memory;

import com.miracle.ai.seahorse.agent.ports.common.NoopFallback;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public interface MemoryOutboxPort {

    void enqueue(MemoryOutboxTask task);

    default List<MemoryOutboxTask> pollPending(int limit) {
        return List.of();
    }

    default void markSucceeded(String taskId) {
    }

    default void markFailed(String taskId, String errorMessage) {
    }

    static MemoryOutboxPort noop() {
        return NoopMemoryOutbox.INSTANCE;
    }

    final class NoopMemoryOutbox implements MemoryOutboxPort, NoopFallback {

        private static final NoopMemoryOutbox INSTANCE = new NoopMemoryOutbox();

        private NoopMemoryOutbox() {
        }

        @Override
        public void enqueue(MemoryOutboxTask task) {
            // intentionally empty: production adapters override to enqueue outbox tasks.
        }
    }

    record MemoryOutboxTask(
            String id,
            String taskType,
            String targetId,
            String userId,
            String tenantId,
            Map<String, Object> payload,
            String errorMessage,
            Instant nextRetryAt,
            Instant createdAt) {

        public MemoryOutboxTask {
            id = Objects.requireNonNullElseGet(id, () -> "mem-outbox-" + UUID.randomUUID());
            taskType = Objects.requireNonNullElse(taskType, "");
            targetId = Objects.requireNonNullElse(targetId, "");
            userId = Objects.requireNonNullElse(userId, "");
            tenantId = Objects.requireNonNullElse(tenantId, "default");
            payload = Map.copyOf(Objects.requireNonNullElse(payload, Map.of()));
            errorMessage = Objects.requireNonNullElse(errorMessage, "");
            createdAt = Objects.requireNonNullElseGet(createdAt, Instant::now);
        }

        public static MemoryOutboxTask vectorUpsert(MemoryRecord record,
                                                    String userId,
                                                    String tenantId,
                                                    String embeddingModel,
                                                    String errorMessage) {
            return new MemoryOutboxTask(
                    null,
                    MemoryOutboxTaskTypes.VECTOR_UPSERT,
                    record == null ? "" : record.id(),
                    userId,
                    tenantId,
                    Map.of(
                            "memoryId", record == null ? "" : record.id(),
                            "content", record == null ? "" : record.content(),
                            "embeddingModel", Objects.requireNonNullElse(embeddingModel, "default")),
                    errorMessage,
                    null,
                    Instant.now());
        }

        public static MemoryOutboxTask vectorDelete(String memoryId, String userId, String tenantId) {
            return derivedIndexTask(
                    MemoryOutboxTaskTypes.VECTOR_DELETE,
                    memoryId,
                    userId,
                    tenantId,
                    Map.of("memoryId", Objects.requireNonNullElse(memoryId, "")));
        }

        public static MemoryOutboxTask keywordUpsert(MemoryRecord record, String userId, String tenantId) {
            return derivedIndexTask(
                    MemoryOutboxTaskTypes.KEYWORD_UPSERT,
                    record == null ? "" : record.id(),
                    userId,
                    tenantId,
                    Map.of(
                            "memoryId", record == null ? "" : record.id(),
                            "content", record == null ? "" : record.content(),
                            "layer", record == null ? "" : record.layer(),
                            "type", record == null ? "" : record.type(),
                            "metadata", record == null ? Map.of() : record.metadata(),
                            "updatedAt", record == null ? Instant.EPOCH.toString() : record.updatedAt().toString()));
        }

        public static MemoryOutboxTask keywordDelete(String memoryId, String userId, String tenantId) {
            return derivedIndexTask(
                    MemoryOutboxTaskTypes.KEYWORD_DELETE,
                    memoryId,
                    userId,
                    tenantId,
                    Map.of("memoryId", Objects.requireNonNullElse(memoryId, "")));
        }

        public static MemoryOutboxTask graphUpsert(MemoryRecord record, String userId, String tenantId) {
            return derivedIndexTask(
                    MemoryOutboxTaskTypes.GRAPH_UPSERT,
                    record == null ? "" : record.id(),
                    userId,
                    tenantId,
                    Map.of(
                            "memoryId", record == null ? "" : record.id(),
                            "content", record == null ? "" : record.content(),
                            "layer", record == null ? "" : record.layer(),
                            "type", record == null ? "" : record.type(),
                            "metadata", record == null ? Map.of() : record.metadata(),
                            "updatedAt", record == null ? Instant.EPOCH.toString() : record.updatedAt().toString()));
        }

        public static MemoryOutboxTask graphDelete(String memoryId, String userId, String tenantId) {
            return derivedIndexTask(
                    MemoryOutboxTaskTypes.GRAPH_DELETE,
                    memoryId,
                    userId,
                    tenantId,
                    Map.of("memoryId", Objects.requireNonNullElse(memoryId, "")));
        }

        private static MemoryOutboxTask derivedIndexTask(String taskType,
                                                         String memoryId,
                                                         String userId,
                                                         String tenantId,
                                                         Map<String, Object> payload) {
            return new MemoryOutboxTask(
                    null,
                    taskType,
                    memoryId,
                    userId,
                    tenantId,
                    payload,
                    "",
                    null,
                    Instant.now());
        }
    }
}

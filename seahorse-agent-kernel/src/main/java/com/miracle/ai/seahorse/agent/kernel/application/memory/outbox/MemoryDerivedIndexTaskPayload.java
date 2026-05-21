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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;

import java.time.Instant;
import java.util.Map;

final class MemoryDerivedIndexTaskPayload {

    private static final String PAYLOAD_MEMORY_ID = "memoryId";
    private static final String PAYLOAD_LAYER = "layer";
    private static final String PAYLOAD_TYPE = "type";
    private static final String PAYLOAD_CONTENT = "content";
    private static final String PAYLOAD_METADATA = "metadata";
    private static final String PAYLOAD_UPDATED_AT = "updatedAt";

    private MemoryDerivedIndexTaskPayload() {
    }

    static MemoryDerivedIndexDocument document(MemoryOutboxPort.MemoryOutboxTask task) {
        Map<String, Object> payload = task.payload();
        return new MemoryDerivedIndexDocument(
                text(payload.get(PAYLOAD_MEMORY_ID)),
                task.userId(),
                task.tenantId(),
                text(payload.get(PAYLOAD_LAYER)),
                text(payload.get(PAYLOAD_TYPE)),
                text(payload.get(PAYLOAD_CONTENT)),
                metadata(payload.get(PAYLOAD_METADATA)),
                instant(payload.get(PAYLOAD_UPDATED_AT), task.createdAt()));
    }

    static MemoryDerivedIndexDeleteCommand deleteCommand(MemoryOutboxPort.MemoryOutboxTask task) {
        return new MemoryDerivedIndexDeleteCommand(
                text(task.payload().get(PAYLOAD_MEMORY_ID)),
                task.userId(),
                task.tenantId());
    }

    private static String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private static Map<String, Object> metadata(Object value) {
        if (value instanceof Map<?, ?> map) {
            return map.entrySet().stream()
                    .filter(entry -> entry.getKey() != null)
                    .collect(java.util.stream.Collectors.toUnmodifiableMap(
                            entry -> entry.getKey().toString(),
                            Map.Entry::getValue,
                            (left, right) -> right));
        }
        return Map.of();
    }

    private static Instant instant(Object value, Instant fallback) {
        if (value instanceof Instant instant) {
            return instant;
        }
        if (value != null) {
            try {
                return Instant.parse(value.toString());
            } catch (RuntimeException ignored) {
                return fallback == null ? Instant.EPOCH : fallback;
            }
        }
        return fallback == null ? Instant.EPOCH : fallback;
    }
}

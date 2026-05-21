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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskTypes;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;

import java.util.Map;
import java.util.Objects;

public class VectorMemoryOutboxTaskHandler implements MemoryOutboxTaskHandler {

    private static final String PAYLOAD_MEMORY_ID = "memoryId";
    private static final String PAYLOAD_CONTENT = "content";
    private static final String PAYLOAD_EMBEDDING_MODEL = "embeddingModel";
    private static final String DEFAULT_EMBEDDING_MODEL = "default";

    private final MemoryVectorPort vectorPort;
    private final String taskType;

    public VectorMemoryOutboxTaskHandler(MemoryVectorPort vectorPort) {
        this(vectorPort, MemoryOutboxTaskTypes.VECTOR_UPSERT);
    }

    public VectorMemoryOutboxTaskHandler(MemoryVectorPort vectorPort, String taskType) {
        this.vectorPort = Objects.requireNonNull(vectorPort, "vectorPort must not be null");
        this.taskType = Objects.requireNonNullElse(taskType, "");
    }

    @Override
    public String taskType() {
        return taskType;
    }

    @Override
    public boolean builtIn() {
        return true;
    }

    @Override
    public void handle(MemoryOutboxPort.MemoryOutboxTask task) {
        Objects.requireNonNull(task, "task must not be null");
        if (MemoryOutboxTaskTypes.VECTOR_DELETE.equals(taskType)) {
            vectorPort.delete(memoryId(task), task.userId(), task.tenantId());
            return;
        }
        if (!MemoryOutboxTaskTypes.VECTOR_UPSERT.equals(taskType)) {
            throw new IllegalArgumentException("unsupported vector memory outbox task type: " + taskType);
        }
        Map<String, Object> payload = task.payload();
        String content = text(payload.get(PAYLOAD_CONTENT));
        String embeddingModel = defaultText(text(payload.get(PAYLOAD_EMBEDDING_MODEL)), DEFAULT_EMBEDDING_MODEL);
        vectorPort.upsert(memoryId(task), task.userId(), content, embeddingModel);
    }

    private String memoryId(MemoryOutboxPort.MemoryOutboxTask task) {
        String payloadMemoryId = text(task.payload().get(PAYLOAD_MEMORY_ID));
        return payloadMemoryId.isBlank() ? task.targetId() : payloadMemoryId;
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

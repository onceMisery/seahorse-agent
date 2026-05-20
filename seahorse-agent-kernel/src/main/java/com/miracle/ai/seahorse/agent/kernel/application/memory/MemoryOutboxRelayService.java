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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MemoryOutboxRelayService {

    private static final String TASK_VECTOR_UPSERT = "VECTOR_UPSERT";

    private final MemoryOutboxPort outboxPort;
    private final MemoryVectorPort vectorPort;

    public MemoryOutboxRelayService(MemoryOutboxPort outboxPort, MemoryVectorPort vectorPort) {
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort must not be null");
        this.vectorPort = Objects.requireNonNull(vectorPort, "vectorPort must not be null");
    }

    public int processBatch(int limit) {
        int safeLimit = limit <= 0 ? 50 : limit;
        List<MemoryOutboxPort.MemoryOutboxTask> tasks = outboxPort.pollPending(safeLimit);
        for (MemoryOutboxPort.MemoryOutboxTask task : tasks) {
            processTask(task);
        }
        return tasks.size();
    }

    private void processTask(MemoryOutboxPort.MemoryOutboxTask task) {
        if (task == null) {
            return;
        }
        try {
            if (!TASK_VECTOR_UPSERT.equals(task.taskType())) {
                throw new IllegalArgumentException("unsupported task type: " + task.taskType());
            }
            Map<String, Object> payload = task.payload();
            String memoryId = text(payload.get("memoryId"));
            String content = text(payload.get("content"));
            String embeddingModel = defaultText(text(payload.get("embeddingModel")), "default");
            vectorPort.upsert(memoryId, task.userId(), content, embeddingModel);
            outboxPort.markSucceeded(task.id());
        } catch (RuntimeException ex) {
            outboxPort.markFailed(task.id(), Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private String text(Object value) {
        return value == null ? "" : value.toString();
    }

    private String defaultText(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}

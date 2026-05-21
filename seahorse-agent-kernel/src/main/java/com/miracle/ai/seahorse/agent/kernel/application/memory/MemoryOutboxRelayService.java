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

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class MemoryOutboxRelayService {

    private final MemoryOutboxPort outboxPort;
    private final Map<String, MemoryOutboxTaskHandler> taskHandlers;

    public MemoryOutboxRelayService(MemoryOutboxPort outboxPort, MemoryVectorPort vectorPort) {
        this(outboxPort, List.of(
                new VectorMemoryOutboxTaskHandler(vectorPort, MemoryOutboxTaskTypes.VECTOR_UPSERT),
                new VectorMemoryOutboxTaskHandler(vectorPort, MemoryOutboxTaskTypes.VECTOR_DELETE)));
    }

    public MemoryOutboxRelayService(MemoryOutboxPort outboxPort, List<MemoryOutboxTaskHandler> taskHandlers) {
        this.outboxPort = Objects.requireNonNull(outboxPort, "outboxPort must not be null");
        this.taskHandlers = registerHandlers(taskHandlers);
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
            MemoryOutboxTaskHandler handler = taskHandlers.get(task.taskType());
            if (handler == null) {
                throw new IllegalArgumentException("unsupported task type: " + task.taskType());
            }
            handler.handle(task);
            outboxPort.markSucceeded(task.id());
        } catch (RuntimeException ex) {
            outboxPort.markFailed(task.id(), Objects.requireNonNullElse(ex.getMessage(), ex.getClass().getName()));
        }
    }

    private Map<String, MemoryOutboxTaskHandler> registerHandlers(List<MemoryOutboxTaskHandler> handlers) {
        Map<String, MemoryOutboxTaskHandler> registered = new LinkedHashMap<>();
        for (MemoryOutboxTaskHandler handler : Objects.requireNonNullElse(handlers, List.<MemoryOutboxTaskHandler>of())) {
            if (handler == null) {
                continue;
            }
            String taskType = Objects.requireNonNullElse(handler.taskType(), "");
            if (taskType.isBlank()) {
                continue;
            }
            MemoryOutboxTaskHandler existing = registered.get(taskType);
            if (existing == null) {
                registered.put(taskType, handler);
                continue;
            }
            boolean existingBuiltIn = existing.builtIn();
            boolean handlerBuiltIn = handler.builtIn();
            if (existingBuiltIn && !handlerBuiltIn) {
                registered.put(taskType, handler);
                continue;
            }
            if (!existingBuiltIn && handlerBuiltIn) {
                continue;
            }
            throw new IllegalArgumentException("duplicate memory outbox task handler: " + taskType);
        }
        return Map.copyOf(registered);
    }
}

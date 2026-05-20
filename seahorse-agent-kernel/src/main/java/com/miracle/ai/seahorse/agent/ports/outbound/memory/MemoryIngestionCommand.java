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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;

import java.util.Objects;
import java.util.UUID;

public record MemoryIngestionCommand(
        String operationId,
        String tenantId,
        String source,
        MemoryWriteRequest writeRequest
) {

    public MemoryIngestionCommand {
        operationId = Objects.requireNonNullElse(operationId, "").trim();
        tenantId = Objects.requireNonNullElse(tenantId, "default").trim();
        if (tenantId.isBlank()) {
            tenantId = "default";
        }
        source = Objects.requireNonNullElse(source, "").trim();
    }

    public MemoryIngestionCommand(MemoryWriteRequest writeRequest) {
        this(defaultOperationId(writeRequest), "default", "memory-engine-write", writeRequest);
    }

    public static MemoryIngestionCommand chatCompleted(MemoryWriteRequest writeRequest) {
        return new MemoryIngestionCommand(defaultOperationId(writeRequest), "default", "chat-completed", writeRequest);
    }

    public static MemoryIngestionCommand toolWrite(String toolCallId, MemoryWriteRequest writeRequest) {
        String id = toolCallId == null || toolCallId.isBlank()
                ? defaultOperationId(writeRequest)
                : "tool-memory-write-" + toolCallId.trim();
        return new MemoryIngestionCommand(id, "default", "agent-memory-write", writeRequest);
    }

    private static String defaultOperationId(MemoryWriteRequest writeRequest) {
        if (writeRequest != null && writeRequest.messageId() != null && !writeRequest.messageId().isBlank()) {
            return "memory-write-" + writeRequest.messageId().trim();
        }
        return "memory-write-" + UUID.randomUUID();
    }
}

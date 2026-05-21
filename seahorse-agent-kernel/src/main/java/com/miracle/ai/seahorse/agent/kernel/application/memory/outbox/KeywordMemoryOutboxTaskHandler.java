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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordIndexPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskHandler;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxTaskTypes;

import java.util.Objects;

public class KeywordMemoryOutboxTaskHandler implements MemoryOutboxTaskHandler {

    private final MemoryKeywordIndexPort keywordIndexPort;
    private final String taskType;

    public KeywordMemoryOutboxTaskHandler(MemoryKeywordIndexPort keywordIndexPort, String taskType) {
        this.keywordIndexPort = Objects.requireNonNull(keywordIndexPort, "keywordIndexPort must not be null");
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
        if (MemoryOutboxTaskTypes.KEYWORD_UPSERT.equals(taskType)) {
            keywordIndexPort.upsert(MemoryDerivedIndexTaskPayload.document(task));
            return;
        }
        if (MemoryOutboxTaskTypes.KEYWORD_DELETE.equals(taskType)) {
            keywordIndexPort.delete(MemoryDerivedIndexTaskPayload.deleteCommand(task));
            return;
        }
        throw new IllegalArgumentException("unsupported keyword memory outbox task type: " + taskType);
    }
}

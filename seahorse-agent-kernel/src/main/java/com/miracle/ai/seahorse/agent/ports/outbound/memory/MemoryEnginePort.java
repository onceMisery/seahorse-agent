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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;

import java.util.Collections;
import java.util.List;

/**
 * 记忆引擎端口。
 * <p>
 * Seahorse 记忆模型端口，覆盖 working、short-term、long-term、semantic 分层记忆能力。
 */
public interface MemoryEnginePort {

    MemoryContext loadMemory(MemoryLoadRequest request);

    void writeMemory(MemoryWriteRequest request);

    List<MemoryItem> retrieveMemories(MemoryLoadRequest request);

    void executeMemoryDecay();

    MemoryQualityReport assessMemoryQuality(String userId);

    static MemoryEnginePort noop() {
        return new MemoryEnginePort() {

            @Override
            public MemoryContext loadMemory(MemoryLoadRequest request) {
                return MemoryContext.builder()
                        .workingMemory(Collections.emptyList())
                        .correctionMemories(Collections.emptyList())
                        .profileMemories(Collections.emptyList())
                        .shortTermMemories(Collections.emptyList())
                        .businessDocumentMemories(Collections.emptyList())
                        .longTermMemories(Collections.emptyList())
                        .semanticMemories(Collections.emptyList())
                        .promptMessages(Collections.emptyList())
                        .build();
            }

            @Override
            public void writeMemory(MemoryWriteRequest request) {
            }

            @Override
            public List<MemoryItem> retrieveMemories(MemoryLoadRequest request) {
                return Collections.emptyList();
            }

            @Override
            public void executeMemoryDecay() {
            }

            @Override
            public MemoryQualityReport assessMemoryQuality(String userId) {
                return MemoryQualityReport.builder()
                        .userId(userId)
                        .build();
            }
        };
    }
}

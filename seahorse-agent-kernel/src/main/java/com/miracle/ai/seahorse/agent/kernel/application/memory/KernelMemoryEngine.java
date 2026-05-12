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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;

import java.util.List;
import java.util.Objects;

/**
 * L1 记忆内核门面。
 * <p>
 * 当前阶段保持旧记忆引擎的激活、写入、衰减和质量评估语义；后续分阶段把治理策略迁入 L2 Feature。
 */
public class KernelMemoryEngine {

    private final MemoryEnginePort memoryEnginePort;

    public KernelMemoryEngine(MemoryEnginePort memoryEnginePort) {
        this.memoryEnginePort = Objects.requireNonNull(memoryEnginePort, "记忆引擎端口不能为空");
    }

    public MemoryContext loadMemory(MemoryLoadRequest request) {
        return memoryEnginePort.loadMemory(request);
    }

    public void writeMemory(MemoryWriteRequest request) {
        memoryEnginePort.writeMemory(request);
    }

    public List<MemoryItem> retrieveMemories(MemoryLoadRequest request) {
        return memoryEnginePort.retrieveMemories(request);
    }

    public void executeMemoryDecay() {
        memoryEnginePort.executeMemoryDecay();
    }

    public MemoryQualityReport assessMemoryQuality(String userId) {
        return memoryEnginePort.assessMemoryQuality(userId);
    }
}

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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * 记忆内核门面契约测试。
 */
class KernelMemoryEngineTests {

    @Test
    void shouldDelegateMemoryOperationsToPort() {
        MemoryEnginePort memoryEnginePort = mock(MemoryEnginePort.class);
        MemoryLoadRequest loadRequest = mock(MemoryLoadRequest.class);
        MemoryWriteRequest writeRequest = mock(MemoryWriteRequest.class);
        KernelMemoryEngine kernelMemoryEngine = new KernelMemoryEngine(memoryEnginePort);

        kernelMemoryEngine.loadMemory(loadRequest);
        kernelMemoryEngine.retrieveMemories(loadRequest);
        kernelMemoryEngine.writeMemory(writeRequest);
        kernelMemoryEngine.executeMemoryDecay();
        kernelMemoryEngine.assessMemoryQuality("user-1");

        verify(memoryEnginePort).loadMemory(loadRequest);
        verify(memoryEnginePort).retrieveMemories(loadRequest);
        verify(memoryEnginePort).writeMemory(writeRequest);
        verify(memoryEnginePort).executeMemoryDecay();
        verify(memoryEnginePort).assessMemoryQuality("user-1");
    }
}

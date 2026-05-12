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

import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.ports.inbound.memory.MemoryGovernanceRunResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryStorePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMemoryGovernanceServiceTests {

    @Test
    void shouldPromoteImportantShortTermMemoryAndUpsertSemanticProfile() {
        RecordingShortTermMemoryPort shortTerm = new RecordingShortTermMemoryPort();
        RecordingMemoryPort longTerm = new RecordingMemoryPort();
        RecordingMemoryPort semantic = new RecordingMemoryPort();
        RecordingMemoryEnginePort memoryEngine = new RecordingMemoryEnginePort();
        shortTerm.records.add(new MemoryRecord("m1", "short_term", "PROFILE", "Name is Alice",
                Map.of("userId", "user-1", "importanceScore", 0.55D, "confidenceLevel", 0.9D),
                Instant.now()));

        KernelMemoryGovernanceService service = new KernelMemoryGovernanceService(
                new MemoryGovernanceServicePorts(shortTerm, longTerm, semantic, memoryEngine), 0.6D);

        MemoryGovernanceRunResult result = service.runGovernance("user-1", "manual", true);

        assertThat(result.promotedCount()).isEqualTo(1);
        assertThat(result.semanticUpsertCount()).isEqualTo(1);
        assertThat(result.errors()).isEmpty();
        assertThat(longTerm.records).hasSize(1);
        assertThat(semantic.records).hasSize(1);
        assertThat(memoryEngine.assessedUsers).containsExactly("user-1");
    }

    @Test
    void shouldDelegateDecayToMemoryEngine() {
        RecordingShortTermMemoryPort shortTerm = new RecordingShortTermMemoryPort();
        RecordingMemoryPort longTerm = new RecordingMemoryPort();
        RecordingMemoryPort semantic = new RecordingMemoryPort();
        RecordingMemoryEnginePort memoryEngine = new RecordingMemoryEnginePort();
        KernelMemoryGovernanceService service = new KernelMemoryGovernanceService(
                new MemoryGovernanceServicePorts(shortTerm, longTerm, semantic, memoryEngine), 0.6D);

        MemoryGovernanceRunResult result = service.runDecay("manual-decay");

        assertThat(result.decayExecuted()).isTrue();
        assertThat(memoryEngine.decayExecuted).isTrue();
    }

    private static class RecordingShortTermMemoryPort extends RecordingMemoryPort implements ShortTermMemoryPort {
    }

    private static class RecordingMemoryPort implements LongTermMemoryPort, SemanticMemoryPort {

        final List<MemoryRecord> records = new ArrayList<>();

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return records.stream().filter(record -> record.id().equals(id)).findFirst();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return records.stream()
                    .filter(record -> conversationId.equals(record.metadata().get("conversationId")))
                    .limit(limit)
                    .toList();
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            return records.stream()
                    .filter(record -> userId.equals(record.metadata().get("userId")))
                    .limit(limit)
                    .toList();
        }

        @Override
        public void save(MemoryRecord record) {
            records.add(record);
        }

        @Override
        public boolean deleteById(String id) {
            return records.removeIf(record -> record.id().equals(id));
        }
    }

    private static class RecordingMemoryEnginePort implements MemoryEnginePort {

        final List<String> assessedUsers = new ArrayList<>();
        boolean decayExecuted;

        @Override
        public com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext loadMemory(
                com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest request) {
            return MemoryEnginePort.noop().loadMemory(request);
        }

        @Override
        public void writeMemory(com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest request) {
        }

        @Override
        public List<com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem> retrieveMemories(
                com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest request) {
            return List.of();
        }

        @Override
        public void executeMemoryDecay() {
            decayExecuted = true;
        }

        @Override
        public MemoryQualityReport assessMemoryQuality(String userId) {
            assessedUsers.add(userId);
            return MemoryQualityReport.builder().userId(userId).build();
        }
    }
}

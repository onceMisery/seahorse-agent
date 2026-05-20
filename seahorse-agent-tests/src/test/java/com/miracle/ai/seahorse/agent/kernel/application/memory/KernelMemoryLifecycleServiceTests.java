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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class KernelMemoryLifecycleServiceTests {

    private static final String USER_ID = "user-1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldObsoleteProfileSlotFragmentsWhenProfileFactChanges() {
        RecordingLifecyclePort lifecyclePort = new RecordingLifecyclePort();
        DefaultMemoryEnginePort engine = engine(new RecordingShortTermMemoryPort(), lifecyclePort);

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .messageId("msg-profile")
                .message(ChatMessage.user("我是学生"))
                .build());

        assertThat(lifecyclePort.obsoleteCommands)
                .hasSize(1)
                .first()
                .satisfies(command -> {
                    assertThat(command.userId).isEqualTo(USER_ID);
                    assertThat(command.tenantId).isEqualTo("default");
                    assertThat(command.profileSlot).isEqualTo("identity.occupation");
                    assertThat(command.activeGenerationId).startsWith("identity.occupation:");
                    assertThat(command.reason).isEqualTo("profile slot updated");
                });
    }

    @Test
    void shouldRecordReadFeedbackForLoadedLayeredMemories() {
        RecordingShortTermMemoryPort shortTermPort = new RecordingShortTermMemoryPort();
        shortTermPort.records.add(new MemoryRecord("stm-1", "short_term", "SUMMARY", "project note",
                Map.of("userId", USER_ID), Instant.now()));
        RecordingLifecyclePort lifecyclePort = new RecordingLifecyclePort();
        DefaultMemoryEnginePort engine = engine(shortTermPort, lifecyclePort);

        engine.loadMemory(MemoryLoadRequest.builder()
                .userId(USER_ID)
                .currentQuestion("What did I discuss about the project?")
                .build());

        assertThat(lifecyclePort.readReferences).containsExactly("short_term:stm-1");
    }

    private DefaultMemoryEnginePort engine(RecordingShortTermMemoryPort shortTermPort,
                                           RecordingLifecyclePort lifecyclePort) {
        return new DefaultMemoryEnginePort(
                shortTermPort,
                new RecordingLongTermMemoryPort(),
                new RecordingSemanticMemoryPort(),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                MemoryOperationLogPort.noop(),
                MemoryVectorPort.noop(),
                MemoryOutboxPort.noop(),
                MemoryBusinessDocumentRetrieverPort.noop(),
                lifecyclePort);
    }

    private record ObsoleteCommand(String userId,
                                   String tenantId,
                                   String profileSlot,
                                   String activeGenerationId,
                                   String reason) {
    }

    private static class RecordingLifecyclePort implements MemoryLifecyclePort {

        final List<ObsoleteCommand> obsoleteCommands = new ArrayList<>();
        final List<String> readReferences = new ArrayList<>();

        @Override
        public int markObsoleteByProfileSlot(String userId,
                                             String tenantId,
                                             String profileSlot,
                                             String activeGenerationId,
                                             String reason) {
            obsoleteCommands.add(new ObsoleteCommand(userId, tenantId, profileSlot, activeGenerationId, reason));
            return 1;
        }

        @Override
        public void recordRead(String layer, String memoryId, Instant referencedAt) {
            readReferences.add(layer + ":" + memoryId);
        }
    }

    private static class RecordingShortTermMemoryPort implements ShortTermMemoryPort {

        final List<MemoryRecord> records = new ArrayList<>();
        final List<MemoryRecord> savedRecords = new ArrayList<>();

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return records.stream().filter(record -> id.equals(record.id())).findFirst();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return records.stream().limit(limit).toList();
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
            savedRecords.add(record);
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }
    }

    private static class RecordingLongTermMemoryPort implements LongTermMemoryPort {

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return Optional.empty();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            return List.of();
        }

        @Override
        public void save(MemoryRecord record) {
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }
    }

    private static class RecordingSemanticMemoryPort implements SemanticMemoryPort {

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return Optional.empty();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return List.of();
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            return List.of();
        }

        @Override
        public void save(MemoryRecord record) {
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }
    }
}

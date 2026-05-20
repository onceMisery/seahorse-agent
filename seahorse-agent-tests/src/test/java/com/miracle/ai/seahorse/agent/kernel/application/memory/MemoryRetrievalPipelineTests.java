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
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
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

class MemoryRetrievalPipelineTests {

    @Test
    void shouldLoadProfileTrackAndRecordProfileReadFeedback() {
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        profilePort.upsert(new ProfileFactUpdate(
                "user-1",
                "default",
                "identity.occupation",
                "student",
                0.95D,
                "explicit_user_memory",
                List.of("msg-1"),
                "identity.occupation:g1"));
        MemoryRetrievalPipelinePort pipeline = new DefaultMemoryRetrievalPipeline(
                new EmptyShortTermMemoryPort(),
                new EmptyLongTermMemoryPort(),
                new EmptySemanticMemoryPort(),
                new ObjectMapper(),
                MemoryEngineOptions.defaults(),
                profilePort,
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                MemoryVectorPort.noop(),
                MemoryBusinessDocumentRetrieverPort.noop(),
                MemoryLifecyclePort.noop());

        MemoryContext context = pipeline.load(MemoryLoadRequest.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .currentQuestion("who am i?")
                .build());

        assertThat(context.getProfileMemories())
                .extracting(memory -> memory.getContent())
                .containsExactly("student");
        assertThat(profilePort.readSlots).containsExactly("identity.occupation");
    }

    private static class RecordingProfileMemoryPort implements ProfileMemoryPort {

        private final Map<String, ProfileFact> activeFacts = new java.util.LinkedHashMap<>();
        private final List<String> readSlots = new ArrayList<>();

        @Override
        public Optional<ProfileFact> findActive(String userId, String tenantId, String slotKey) {
            return Optional.ofNullable(activeFacts.get(slotKey));
        }

        @Override
        public List<ProfileFact> listActive(String userId, String tenantId, int limit) {
            return activeFacts.values().stream().limit(limit).toList();
        }

        @Override
        public void upsert(ProfileFactUpdate update) {
            activeFacts.put(update.slotKey(), new ProfileFact(
                    "profile-" + activeFacts.size(),
                    update.userId(),
                    update.tenantId(),
                    update.slotKey(),
                    update.valueText(),
                    update.confidenceLevel(),
                    update.sourceType(),
                    update.generationId(),
                    "ACTIVE",
                    Instant.EPOCH));
        }

        @Override
        public void recordRead(String userId, String tenantId, String slotKey, Instant referencedAt) {
            readSlots.add(slotKey);
        }
    }

    private static class EmptyShortTermMemoryPort implements ShortTermMemoryPort {

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

    private static class EmptyLongTermMemoryPort extends EmptyShortTermMemoryPort implements LongTermMemoryPort {
    }

    private static class EmptySemanticMemoryPort extends EmptyShortTermMemoryPort implements SemanticMemoryPort {
    }
}

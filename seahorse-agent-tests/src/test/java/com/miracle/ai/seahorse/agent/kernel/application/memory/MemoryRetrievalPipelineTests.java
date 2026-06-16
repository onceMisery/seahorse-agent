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
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionCommand;
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

    @Test
    void shouldKeepBusinessDocumentResultsOnDedicatedTrack() {
        MemoryItem businessRule = MemoryItem.builder()
                .id("doc-1#chunk-1")
                .type("BUSINESS_DOCUMENT")
                .content("报销金额超过 5000 元需要直属主管审批")
                .metadataJson("{\"docId\":\"expense-policy\",\"version\":\"2026.05\"}")
                .build();
        MemoryRetrievalPipelinePort pipeline = new DefaultMemoryRetrievalPipeline(
                new EmptyShortTermMemoryPort(),
                new EmptyLongTermMemoryPort(),
                new EmptySemanticMemoryPort(),
                new ObjectMapper(),
                MemoryEngineOptions.defaults(),
                new RecordingProfileMemoryPort(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                MemoryVectorPort.noop(),
                (tenantId, query, topK) -> List.of(businessRule),
                MemoryLifecyclePort.noop());

        MemoryContext context = pipeline.load(MemoryLoadRequest.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .currentQuestion("知识库里的报销规则是什么？")
                .build());

        assertThat(context.getBusinessDocumentMemories())
                .extracting(MemoryItem::getContent)
                .containsExactly("报销金额超过 5000 元需要直属主管审批");
        assertThat(context.getSemanticMemories()).isEmpty();
    }

    @Test
    void shouldPassKnowledgeBaseScopeToBusinessDocumentRetriever() {
        RecordingBusinessDocumentRetrieverPort businessDocumentRetrieverPort =
                new RecordingBusinessDocumentRetrieverPort();
        MemoryRetrievalPipelinePort pipeline = new DefaultMemoryRetrievalPipeline(
                new EmptyShortTermMemoryPort(),
                new EmptyLongTermMemoryPort(),
                new EmptySemanticMemoryPort(),
                new ObjectMapper(),
                MemoryEngineOptions.defaults(),
                new RecordingProfileMemoryPort(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                MemoryVectorPort.noop(),
                businessDocumentRetrieverPort,
                MemoryLifecyclePort.noop());

        pipeline.load(MemoryLoadRequest.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .currentQuestion("policy document")
                .knowledgeBaseIds(List.of("42", "43"))
                .build());

        assertThat(businessDocumentRetrieverPort.lastKnowledgeBaseIds).containsExactly("42", "43");
    }

    @Test
    void shouldSuppressProfileSlotWhenCorrectionTargetsSameSlot() {
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
        RecordingCorrectionLedgerPort correctionPort = new RecordingCorrectionLedgerPort();
        correctionPort.upsert(new CorrectionCommand(
                "user-1",
                "default",
                "PROFILE_CORRECTION",
                "PROFILE_SLOT",
                "identity.occupation",
                "student",
                "teacher",
                "用户纠正职业画像：不是 student，而是 teacher",
                List.of("msg-2"),
                "identity.occupation:g2"));
        MemoryRetrievalPipelinePort pipeline = new DefaultMemoryRetrievalPipeline(
                new EmptyShortTermMemoryPort(),
                new EmptyLongTermMemoryPort(),
                new EmptySemanticMemoryPort(),
                new ObjectMapper(),
                MemoryEngineOptions.defaults(),
                profilePort,
                correctionPort,
                new DefaultMemoryRouter(),
                MemoryVectorPort.noop(),
                MemoryBusinessDocumentRetrieverPort.noop(),
                MemoryLifecyclePort.noop());

        MemoryContext context = pipeline.load(MemoryLoadRequest.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .currentQuestion("我的职业是什么？")
                .build());

        assertThat(context.getCorrectionMemories())
                .extracting(MemoryItem::getContent)
                .containsExactly("用户纠正职业画像：不是 student，而是 teacher");
        assertThat(context.getProfileMemories()).isEmpty();
        assertThat(profilePort.readSlots).isEmpty();
    }

    private static class RecordingBusinessDocumentRetrieverPort implements MemoryBusinessDocumentRetrieverPort {

        private List<String> lastKnowledgeBaseIds = List.of();

        @Override
        public List<MemoryItem> retrieve(String tenantId, String query, int topK) {
            return List.of();
        }

        @Override
        public List<MemoryItem> retrieve(String tenantId, String query, int topK, List<String> knowledgeBaseIds) {
            lastKnowledgeBaseIds = knowledgeBaseIds;
            return List.of();
        }
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

    private static class RecordingCorrectionLedgerPort implements CorrectionLedgerPort {

        private final List<com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule> rules =
                new ArrayList<>();

        @Override
        public List<com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule> listActive(
                String userId,
                String tenantId,
                int limit) {
            return rules.stream().limit(limit).toList();
        }

        @Override
        public void upsert(CorrectionCommand command) {
            rules.add(new com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule(
                    "correction-" + rules.size(),
                    command.userId(),
                    command.tenantId(),
                    command.correctionType(),
                    command.targetKind(),
                    command.targetKey(),
                    command.incorrectValue(),
                    command.correctValue(),
                    command.ruleText(),
                    "HARD_RULE",
                    command.generationId(),
                    "ACTIVE",
                    Instant.EPOCH));
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

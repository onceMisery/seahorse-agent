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
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextBudget;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ContextWeaverPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionWorkflowPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRoutePlan;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRouterPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTrack;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

class MemoryWorkflowRoutingTests {

    @Test
    void shouldRouteProfileQuestionsToStrongFactTracks() {
        MemoryRouterPort router = new DefaultMemoryRouter();

        MemoryRoutePlan plan = router.route(new MemoryRouteRequest(
                "user-1",
                "default",
                "我的职业是什么？"));

        Assertions.assertTrue(plan.isActive(MemoryTrack.CORRECTION));
        Assertions.assertTrue(plan.isActive(MemoryTrack.PROFILE));
        Assertions.assertTrue(plan.isActive(MemoryTrack.SHORT_WINDOW));
        Assertions.assertFalse(plan.isActive(MemoryTrack.EPISODIC));
        Assertions.assertFalse(plan.isActive(MemoryTrack.BUSINESS_DOCUMENT));
    }

    @Test
    void shouldRouteGeneralChatToShortWindowWithoutProfileOrEpisodicTracks() {
        MemoryRouterPort router = new DefaultMemoryRouter();

        MemoryRoutePlan plan = router.route(new MemoryRouteRequest(
                "user-1",
                "default",
                "帮我把这句话润色一下"));

        Assertions.assertTrue(plan.isActive(MemoryTrack.CORRECTION));
        Assertions.assertTrue(plan.isActive(MemoryTrack.SHORT_WINDOW));
        Assertions.assertFalse(plan.isActive(MemoryTrack.PROFILE));
        Assertions.assertFalse(plan.isActive(MemoryTrack.EPISODIC));
        Assertions.assertFalse(plan.isActive(MemoryTrack.BUSINESS_DOCUMENT));
    }

    @Test
    void shouldRouteBusinessRuleQuestionsToBusinessDocumentTrack() {
        MemoryRouterPort router = new DefaultMemoryRouter();

        MemoryRoutePlan plan = router.route(new MemoryRouteRequest(
                "user-1",
                "default",
                "知识库里的报销规则是什么？"));

        Assertions.assertTrue(plan.isActive(MemoryTrack.BUSINESS_DOCUMENT));
        Assertions.assertTrue(plan.isActive(MemoryTrack.CORRECTION));
        Assertions.assertTrue(plan.isActive(MemoryTrack.EPISODIC));
    }

    @Test
    void shouldWeaveCorrectionBeforeProfile() {
        ContextWeaverPort weaver = new DefaultContextWeaver();
        MemoryContext context = MemoryContext.builder()
                .correctionMemories(List.of(MemoryItem.builder().content("用户纠正职业画像：学生 -> 老师").build()))
                .profileMemories(List.of(MemoryItem.builder().content("老师").build()))
                .build();

        String prompt = weaver.weave(context, ContextBudget.defaults());

        Assertions.assertTrue(prompt.indexOf("[Correction Ledger]") < prompt.indexOf("[Profile KV]"));
        Assertions.assertTrue(prompt.contains("用户纠正职业画像：学生 -> 老师"));
        Assertions.assertTrue(prompt.contains("老师"));
    }

    @Test
    void shouldWeavePriorityZonesWithinBudget() {
        ContextWeaverPort weaver = new DefaultContextWeaver();
        MemoryContext context = MemoryContext.builder()
                .correctionMemories(List.of(MemoryItem.builder()
                        .content("user corrected occupation: student -> teacher")
                        .build()))
                .profileMemories(List.of(MemoryItem.builder().content("teacher").build()))
                .shortTermMemories(List.of(MemoryItem.builder().content("prefers concise answers").build()))
                .longTermMemories(List.of(
                        MemoryItem.builder().content("low-priority long memory one").build(),
                        MemoryItem.builder().content("low-priority long memory two").build()))
                .build();

        String prompt = weaver.weave(context, new ContextBudget(3, 180));

        Assertions.assertTrue(prompt.indexOf("[Correction Ledger]") < prompt.indexOf("[Profile KV]"));
        Assertions.assertTrue(prompt.contains("user corrected occupation: student -> teacher"));
        Assertions.assertTrue(prompt.contains("teacher"));
        Assertions.assertTrue(prompt.contains("[Short Window]"));
        Assertions.assertTrue(prompt.contains("prefers concise answers"));
        Assertions.assertFalse(prompt.contains("low-priority long memory"));
        Assertions.assertTrue(prompt.length() <= 180);
    }

    @Test
    void shouldExposeDeterministicIngestionWorkflowForCompatibleEngineWrites() {
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                new StubShortTermMemoryPort(),
                new StubLongTermMemoryPort(),
                new StubSemanticMemoryPort(),
                new ObjectMapper(),
                MemoryEngineOptions.defaults(),
                profilePort,
                CorrectionLedgerPort.noop());
        MemoryIngestionWorkflowPort workflow = engine;

        var result = workflow.ingest(new MemoryIngestionCommand(MemoryWriteRequest.builder()
                .userId("user-1")
                .conversationId("conv-1")
                .messageId("msg-1")
                .message(ChatMessage.user("我是一名学生"))
                .build()));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertTrue(result.operations().contains("SHORT_TERM_SAVE"));
        Assertions.assertTrue(result.operations().contains("PROFILE_UPSERT"));
        Assertions.assertEquals(1, profilePort.updates.size());
        Assertions.assertEquals("学生", profilePort.updates.get(0).valueText());
    }

    private static class RecordingProfileMemoryPort implements ProfileMemoryPort {

        private final List<ProfileFactUpdate> updates = new ArrayList<>();

        @Override
        public Optional<ProfileFact> findActive(String userId, String tenantId, String slotKey) {
            return Optional.empty();
        }

        @Override
        public List<ProfileFact> listActive(String userId, String tenantId, int limit) {
            return List.of();
        }

        @Override
        public void upsert(ProfileFactUpdate update) {
            updates.add(update);
        }
    }

    private static class StubShortTermMemoryPort implements ShortTermMemoryPort {

        private final List<MemoryRecord> saved = new ArrayList<>();

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
            saved.add(record);
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }
    }

    private static class StubLongTermMemoryPort implements LongTermMemoryPort {

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

    private static class StubSemanticMemoryPort implements SemanticMemoryPort {

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

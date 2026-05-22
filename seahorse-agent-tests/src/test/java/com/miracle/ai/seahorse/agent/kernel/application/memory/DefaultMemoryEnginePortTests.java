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
import com.miracle.ai.seahorse.agent.kernel.domain.chat.MemoryPromptFormatter;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryContext;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryItem;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryLoadRequest;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryWriteRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionLedgerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.CorrectionRule;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryEnginePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryLifecyclePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationType;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOutboxPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfig;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryPolicyConfigPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementResult;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinementMemory;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRefinerPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewApplyDirective;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryReviewCandidatePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRetrievalPipelinePort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.RefinedMemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryVectorPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBusinessDocumentRetrieverPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link DefaultMemoryEnginePort} 契约测试。
 */
class DefaultMemoryEnginePortTests {

    private static final String USER_ID = "user-1";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldLoadMemoryFromAllLayers() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of(
                record("stm-1", "SUMMARY", "用户询问了入职流程", 0.5D)));
        StubLongTermMemoryPort longTermPort = new StubLongTermMemoryPort(List.of(
                record("ltm-1", "PROFILE", "用户是后端工程师", 0.8D)));
        StubSemanticMemoryPort semanticPort = new StubSemanticMemoryPort(List.of(
                record("sem-1", "PROFILE", "用户使用 Java 开发", 0.9D)));

        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort, longTermPort, semanticPort, OBJECT_MAPPER);

        MemoryLoadRequest request = MemoryLoadRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .currentQuestion("入职流程是什么")
                .build();
        MemoryContext context = engine.loadMemory(request);

        Assertions.assertEquals(USER_ID, context.getUserId());
        Assertions.assertEquals(1, context.getShortTermMemories().size());
        Assertions.assertEquals(1, context.getLongTermMemories().size());
        Assertions.assertEquals(1, context.getSemanticMemories().size());
        Assertions.assertEquals("stm-1", context.getShortTermMemories().get(0).getId());
        Assertions.assertEquals("ltm-1", context.getLongTermMemories().get(0).getId());
        Assertions.assertEquals("sem-1", context.getSemanticMemories().get(0).getId());
    }

    @Test
    void shouldReturnEmptyContextWhenUserIdIsBlank() {
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                new StubShortTermMemoryPort(List.of()),
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        MemoryLoadRequest request = MemoryLoadRequest.builder()
                .userId("")
                .build();
        MemoryContext context = engine.loadMemory(request);

        Assertions.assertTrue(context.getShortTermMemories().isEmpty());
        Assertions.assertTrue(context.getLongTermMemories().isEmpty());
        Assertions.assertTrue(context.getSemanticMemories().isEmpty());
    }

    @Test
    void shouldReturnEmptyContextWhenRequestIsNull() {
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                new StubShortTermMemoryPort(List.of()),
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        MemoryContext context = engine.loadMemory(null);

        Assertions.assertTrue(context.getShortTermMemories().isEmpty());
    }

    @Test
    void noopShouldReturnNullSafeMemoryContextLists() {
        MemoryContext context = MemoryEnginePort.noop().loadMemory(null);

        Assertions.assertNotNull(context.getWorkingMemory());
        Assertions.assertNotNull(context.getCorrectionMemories());
        Assertions.assertNotNull(context.getProfileMemories());
        Assertions.assertNotNull(context.getShortTermMemories());
        Assertions.assertNotNull(context.getBusinessDocumentMemories());
        Assertions.assertNotNull(context.getLongTermMemories());
        Assertions.assertNotNull(context.getSemanticMemories());
        Assertions.assertNotNull(context.getPromptMessages());
    }

    @Test
    void shouldUseConfiguredLayerLimitsWhenLoadingMemory() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of(
                record("stm-1", "SUMMARY", "A", 0.5D),
                record("stm-2", "SUMMARY", "B", 0.5D)));
        StubLongTermMemoryPort longTermPort = new StubLongTermMemoryPort(List.of(
                record("ltm-1", "PROFILE", "C", 0.8D),
                record("ltm-2", "PROFILE", "D", 0.8D)));
        StubSemanticMemoryPort semanticPort = new StubSemanticMemoryPort(List.of(
                record("sem-1", "PROFILE", "E", 0.9D),
                record("sem-2", "PROFILE", "F", 0.9D)));
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                longTermPort,
                semanticPort,
                OBJECT_MAPPER,
                new MemoryEngineOptions(1, 1, 1, true));

        MemoryContext context = engine.loadMemory(MemoryLoadRequest.builder()
                .userId(USER_ID)
                .build());

        Assertions.assertEquals(1, shortTermPort.lastListByUserLimit);
        Assertions.assertEquals(1, longTermPort.lastListByUserLimit);
        Assertions.assertEquals(1, semanticPort.lastListByUserLimit);
        Assertions.assertEquals(1, context.getShortTermMemories().size());
        Assertions.assertEquals(1, context.getLongTermMemories().size());
        Assertions.assertEquals(1, context.getSemanticMemories().size());
    }

    @Test
    void shouldDeduplicateLongTermMemoriesById() {
        StubLongTermMemoryPort longTermPort = new StubLongTermMemoryPort(List.of(
                record("ltm-1", "PROFILE", "内容A", 0.8D),
                record("ltm-1", "PROFILE", "内容A重复", 0.7D),
                record("ltm-2", "PREFERENCE", "内容B", 0.6D)));

        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                new StubShortTermMemoryPort(List.of()),
                longTermPort,
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        MemoryContext context = engine.loadMemory(MemoryLoadRequest.builder()
                .userId(USER_ID).build());

        Assertions.assertEquals(2, context.getLongTermMemories().size());
        Assertions.assertEquals("ltm-1", context.getLongTermMemories().get(0).getId());
        Assertions.assertEquals("ltm-2", context.getLongTermMemories().get(1).getId());
    }

    @Test
    void shouldKeepNewestProfileOccupationMemoryPerLayer() {
        StubSemanticMemoryPort semanticPort = new StubSemanticMemoryPort(List.of(
                semanticRecord("sem-old", "PROFILE", "I am a teacher", 0.9D,
                        Instant.parse("2026-05-19T00:00:00Z"), "profile:occupation"),
                semanticRecord("sem-new", "PROFILE", "我是学生", 0.8D,
                        Instant.parse("2026-05-20T00:00:00Z"), "profile:occupation")));

        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                new StubShortTermMemoryPort(List.of()),
                new StubLongTermMemoryPort(List.of()),
                semanticPort,
                OBJECT_MAPPER);

        MemoryContext context = engine.loadMemory(MemoryLoadRequest.builder()
                .userId(USER_ID)
                .build());

        Assertions.assertEquals(1, context.getSemanticMemories().size());
        Assertions.assertEquals("sem-new", context.getSemanticMemories().get(0).getId());
        Assertions.assertEquals("我是学生", context.getSemanticMemories().get(0).getContent());
    }

    @Test
    void shouldGracefullyDegradeWhenLayerThrowsException() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of(
                record("stm-1", "SUMMARY", "正常数据", 0.5D)));
        StubLongTermMemoryPort failingPort = new StubLongTermMemoryPort(List.of()) {
            @Override
            public List<MemoryRecord> listByUser(String userId, int limit) {
                throw new RuntimeException("数据库连接失败");
            }
        };

        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort, failingPort, new StubSemanticMemoryPort(List.of()), OBJECT_MAPPER);

        MemoryContext context = engine.loadMemory(MemoryLoadRequest.builder()
                .userId(USER_ID).build());

        Assertions.assertEquals(1, context.getShortTermMemories().size());
        Assertions.assertTrue(context.getLongTermMemories().isEmpty());
    }

    @Test
    void shouldWriteExplicitUserMemoryToShortTermLayer() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .messageId("msg-1")
                .message(ChatMessage.user("请记住：我喜欢使用 Java 编写后端服务"))
                .build());

        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        MemoryRecord saved = shortTermPort.savedRecords.get(0);
        Assertions.assertEquals("stm-msg-1", saved.id());
        Assertions.assertEquals("SHORT_TERM", saved.layer());
        Assertions.assertEquals("PREFERENCE", saved.type());
        Assertions.assertEquals("我喜欢使用 Java 编写后端服务", saved.content());
        Assertions.assertEquals(USER_ID, saved.metadata().get("userId"));
        Assertions.assertEquals("explicit_user_memory", saved.metadata().get("capturePolicy"));
    }

    @Test
    void shouldWriteProfileMemoryWithWhitespaceAndTrimSocialTail() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .messageId("msg-profile")
                .message(ChatMessage.user("我 是一名学生，很高兴认识你"))
                .build());

        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        MemoryRecord saved = shortTermPort.savedRecords.get(0);
        Assertions.assertEquals("PROFILE", saved.type());
        Assertions.assertEquals("我是一名学生", saved.content());
        Assertions.assertEquals("high_precision_rule_v1", saved.metadata().get("capturePolicyVersion"));
    }

    @Test
    void shouldWriteProfileFactWhenProfileMemoryCaptured() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                profilePort,
                CorrectionLedgerPort.noop());

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-profile")
                .messageId("msg-profile-kv")
                .message(ChatMessage.user("我 是一名学生，很高兴认识你"))
                .build());

        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        Assertions.assertEquals(1, profilePort.updates.size());
        ProfileFactUpdate update = profilePort.updates.get(0);
        Assertions.assertEquals(USER_ID, update.userId());
        Assertions.assertEquals("default", update.tenantId());
        Assertions.assertEquals("identity.occupation", update.slotKey());
        Assertions.assertEquals("学生", update.valueText());
        Assertions.assertEquals("explicit_user_memory", update.sourceType());
    }

    @Test
    void shouldWriteProfileFactsForNameTechStackAndResponseStyle() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                profilePort,
                CorrectionLedgerPort.noop());

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-profile-slots")
                .messageId("msg-name")
                .message(ChatMessage.user("My name is Alice"))
                .build());
        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-profile-slots")
                .messageId("msg-stack")
                .message(ChatMessage.user("My tech stack is Java and Spring"))
                .build());
        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-profile-slots")
                .messageId("msg-style")
                .message(ChatMessage.user("I prefer concise answers"))
                .build());

        Assertions.assertEquals(3, profilePort.updates.size(), profilePort.updates::toString);
        Assertions.assertEquals("identity.name", profilePort.updates.get(0).slotKey());
        Assertions.assertEquals("Alice", profilePort.updates.get(0).valueText());
        Assertions.assertEquals("skills.tech_stack", profilePort.updates.get(1).slotKey());
        Assertions.assertEquals("Java and Spring", profilePort.updates.get(1).valueText());
        Assertions.assertEquals("preferences.response_style", profilePort.updates.get(2).slotKey());
        Assertions.assertEquals("concise answers", profilePort.updates.get(2).valueText());
    }

    @Test
    void shouldWriteChineseProfileFactsForNameTechStackAndResponseStyle() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                profilePort,
                CorrectionLedgerPort.noop());

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-profile-slots-cn")
                .messageId("msg-cn-name")
                .message(ChatMessage.user("\u6211\u53eb\u5f20\u4e09"))
                .build());
        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-profile-slots-cn")
                .messageId("msg-cn-stack")
                .message(ChatMessage.user("\u6211\u7684\u6280\u672f\u6808\u662f Java \u548c Spring"))
                .build());
        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-profile-slots-cn")
                .messageId("msg-cn-style")
                .message(ChatMessage.user("\u6211\u559c\u6b22\u7b80\u77ed\u56de\u7b54"))
                .build());

        Assertions.assertEquals(3, profilePort.updates.size());
        Assertions.assertEquals("identity.name", profilePort.updates.get(0).slotKey());
        Assertions.assertEquals("\u5f20\u4e09", profilePort.updates.get(0).valueText());
        Assertions.assertEquals("skills.tech_stack", profilePort.updates.get(1).slotKey());
        Assertions.assertEquals("Java \u548c Spring", profilePort.updates.get(1).valueText());
        Assertions.assertEquals("preferences.response_style", profilePort.updates.get(2).slotKey());
        Assertions.assertEquals("\u7b80\u77ed\u56de\u7b54", profilePort.updates.get(2).valueText());
    }

    @Test
    void shouldStoreCorrectionAndPreferCorrectedProfileWhenLoadingMemory() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of(
                semanticRecord("stm-old", "PROFILE", "我是一名学生", 0.8D,
                        Instant.parse("2026-05-19T00:00:00Z"), "identity.occupation")));
        StubSemanticMemoryPort semanticPort = new StubSemanticMemoryPort(List.of(
                semanticRecord("sem-old", "PROFILE", "我是一名学生", 0.8D,
                        Instant.parse("2026-05-19T00:00:00Z"), "identity.occupation")));
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        RecordingCorrectionLedgerPort correctionPort = new RecordingCorrectionLedgerPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                semanticPort,
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                profilePort,
                correctionPort);

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-correction")
                .messageId("msg-correction")
                .message(ChatMessage.user("我不是学生了，我现在是老师"))
                .build());
        MemoryContext context = engine.loadMemory(MemoryLoadRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-read")
                .currentQuestion("我的职业是什么？")
                .build());

        Assertions.assertEquals(1, correctionPort.commands.size());
        CorrectionCommand command = correctionPort.commands.get(0);
        Assertions.assertEquals("PROFILE_SLOT", command.targetKind());
        Assertions.assertEquals("identity.occupation", command.targetKey());
        Assertions.assertEquals("学生", command.incorrectValue());
        Assertions.assertEquals("老师", command.correctValue());
        Assertions.assertEquals(1, profilePort.updates.size());
        Assertions.assertEquals("老师", profilePort.updates.get(0).valueText());
        Assertions.assertEquals(1, context.getCorrectionMemories().size());
        Assertions.assertTrue(context.getProfileMemories().isEmpty());
        String promptMemory = MemoryPromptFormatter.format(context);
        Assertions.assertTrue(promptMemory.contains("用户纠错本："));
        Assertions.assertTrue(promptMemory.contains("老师"));
        Assertions.assertFalse(promptMemory.contains("- 我是一名学生"));
    }

    @Test
    void shouldOnlySuppressLegacyProfileSlotMemoriesWhenProfileFactIsActive() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of(
                semanticRecord("stm-profile", "PROFILE", "我是一名学生", 0.8D,
                        Instant.parse("2026-05-19T00:00:00Z"), "identity.occupation"),
                record("stm-history", "SUMMARY", "用户问过学生优惠政策", 0.5D)));
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        profilePort.upsert(new ProfileFactUpdate(
                USER_ID,
                "default",
                "identity.occupation",
                "老师",
                0.95D,
                "explicit_user_correction",
                List.of("msg-correction"),
                "identity.occupation:test"));
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                profilePort,
                CorrectionLedgerPort.noop());

        MemoryContext context = engine.loadMemory(MemoryLoadRequest.builder()
                .userId(USER_ID)
                .currentQuestion("我的职业是什么？")
                .build());

        Assertions.assertEquals(1, context.getProfileMemories().size());
        Assertions.assertEquals("老师", context.getProfileMemories().get(0).getContent());
        Assertions.assertEquals(1, context.getShortTermMemories().size());
        Assertions.assertEquals("stm-history", context.getShortTermMemories().get(0).getId());
    }

    @Test
    void shouldWritePreferenceMemoryWithWhitespace() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .messageId("msg-pref")
                .message(ChatMessage.user("我  喜欢 简短回答"))
                .build());

        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        MemoryRecord saved = shortTermPort.savedRecords.get(0);
        Assertions.assertEquals("PREFERENCE", saved.type());
        Assertions.assertEquals("我喜欢简短回答", saved.content());
    }

    @Test
    void shouldWriteHighValuePersonalFactMemory() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .messageId("msg-fact")
                .message(ChatMessage.user("我的职业是学生"))
                .build());

        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        MemoryRecord saved = shortTermPort.savedRecords.get(0);
        Assertions.assertEquals("FACT", saved.type());
        Assertions.assertEquals("我的职业是学生", saved.content());
    }

    @Test
    void shouldScoreExplicitImportantMemoryHigherThanPlainPreference() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .messageId("msg-important")
                .message(ChatMessage.user("以后都按这个来：我喜欢简短回答"))
                .build());

        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        MemoryRecord saved = shortTermPort.savedRecords.get(0);
        Assertions.assertEquals("PREFERENCE", saved.type());
        Assertions.assertEquals("我喜欢简短回答", saved.content());
        Assertions.assertTrue((Double) saved.metadata().get("importanceScore") >= 0.75D);
        Assertions.assertTrue((Double) saved.metadata().get("confidenceLevel") >= 0.85D);
    }

    @Test
    void shouldSkipNoisyQuestionWhenWritingMemory() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .messageId("msg-2")
                .message(ChatMessage.user("入职流程是什么？"))
                .build());

        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
    }

    @Test
    void shouldSkipLowValuePersonalExpressionWhenWritingMemory() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .messageId("msg-low-value")
                .message(ChatMessage.user("我的天这个太难了"))
                .build());

        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
    }

    @Test
    void shouldSkipSensitiveExplicitMemoryWhenWritingMemory() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .messageId("msg-sensitive")
                .message(ChatMessage.user("请记住：我的密码是 123456"))
                .build());

        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
    }

    @Test
    void shouldUseOperationLogToAvoidDuplicateIngestionWrites() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                profilePort,
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                operationLogPort);
        MemoryWriteRequest writeRequest = MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-idempotent")
                .messageId("msg-idempotent")
                .message(ChatMessage.user("我的职业是学生"))
                .build();

        var first = engine.ingest(new MemoryIngestionCommand("op-idempotent", "default", "test", writeRequest));
        var second = engine.ingest(new MemoryIngestionCommand("op-idempotent", "default", "test", writeRequest));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, first.status());
        Assertions.assertEquals(MemoryIngestionStatus.IGNORED, second.status());
        Assertions.assertEquals("duplicate_operation", second.reason());
        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        Assertions.assertEquals(1, profilePort.updates.size());
        Assertions.assertEquals(1, operationLogPort.started.size());
        Assertions.assertEquals(MemoryOperationType.ADD, operationLogPort.started.get(0).operationType());
        Assertions.assertEquals(MemoryOperationStatus.SUCCEEDED, operationLogPort.statusById.get("op-idempotent"));
    }

    @Test
    void shouldRejectSensitiveContentBeforeAnyDurableMemoryWrite() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        RecordingCorrectionLedgerPort correctionPort = new RecordingCorrectionLedgerPort();
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                profilePort,
                correctionPort,
                new DefaultMemoryRouter(),
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-sensitive", "default", "test",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-sensitive")
                        .messageId("msg-sensitive-op")
                        .message(ChatMessage.user("请记住：我的密码是 123456"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.REJECTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.IGNORE, result.action());
        Assertions.assertTrue(result.reason().contains("sensitive"));
        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
        Assertions.assertTrue(profilePort.updates.isEmpty());
        Assertions.assertTrue(correctionPort.commands.isEmpty());
        Assertions.assertEquals(MemoryOperationStatus.REJECTED, operationLogPort.statusById.get("op-sensitive"));
        Assertions.assertEquals(MemoryOperationType.IGNORE, operationLogPort.started.get(0).operationType());
    }

    @Test
    void shouldIgnoreLowValueChatAndRecordDecision() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-low-value", "default", "chat-completed",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-low")
                        .messageId("msg-low-op")
                        .message(ChatMessage.user("谢谢，收到"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.IGNORED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.IGNORE, result.action());
        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
        Assertions.assertEquals(MemoryOperationStatus.IGNORED, operationLogPort.statusById.get("op-low-value"));
        Assertions.assertEquals("no_high_value_signal",
                operationLogPort.decisionById.get("op-low-value").get("reason"));
    }

    @Test
    void shouldRecordAddOperationForExplicitPreferenceMemory() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-preference", "default", "agent-memory-write",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-pref-op")
                        .messageId("msg-pref-op")
                        .message(ChatMessage.user("请记住：我喜欢简短回答"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.ADD, result.action());
        Assertions.assertTrue(result.operations().contains("SHORT_TERM_SAVE"));
        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        Assertions.assertEquals(MemoryOperationType.ADD, operationLogPort.started.get(0).operationType());
        Assertions.assertEquals("SHORT_TERM_MEMORY", operationLogPort.started.get(0).targetKind());
        Assertions.assertEquals(MemoryOperationStatus.SUCCEEDED, operationLogPort.statusById.get("op-preference"));
        Assertions.assertEquals("PREFERENCE", operationLogPort.decisionById.get("op-preference").get("memoryType"));
    }

    @Test
    void shouldNotCallRefinerWhenRefinerIsDisabled() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(
                MemoryRefinementResult.empty("disabled_by_test"));
        DefaultMemoryEnginePort engine = engineWithRefiner(shortTermPort, refinerPort, false);

        var result = engine.ingest(new MemoryIngestionCommand("op-refiner-disabled", "default", "chat-completed",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-disabled")
                        .messageId("msg-refiner-disabled")
                        .message(ChatMessage.user("i prefer concise answers"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        Assertions.assertEquals(0, refinerPort.requests.size());
        Assertions.assertEquals("PREFERENCE", shortTermPort.savedRecords.get(0).type());
    }

    @Test
    void shouldUseEnabledRefinerStructuredAddBeforeSchemaValidationAndWrite() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(MemoryRefinementResult.refined(
                "refined",
                List.of(RefinedMemoryOperation.add(
                        "PROJECT_FACT",
                        "project.thread_pool.reject_policy",
                        "用户正在调优 Dubbo 消费端线程池拒绝策略，关注 CallerRuns 反压效果。",
                        0.86D,
                        0.72D,
                        List.of("msg-refiner-add"),
                        List.of("llm_refiner"))),
                Map.of("model", "test-refiner")));
        DefaultMemoryEnginePort engine = engineWithRefiner(
                shortTermPort,
                refinerPort,
                true,
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-refiner-add", "default", "memory-aggregation-flush",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-add")
                        .messageId("msg-refiner-add")
                        .message(ChatMessage.user("刚才讨论 Dubbo 线程池拒绝策略，我想改成 CallerRuns。"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.ADD, result.action());
        Assertions.assertEquals(1, refinerPort.requests.size());
        Assertions.assertEquals("op-refiner-add", refinerPort.requests.get(0).operationId());
        Assertions.assertEquals("memory-aggregation-flush", refinerPort.requests.get(0).source());
        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        MemoryRecord saved = shortTermPort.savedRecords.get(0);
        Assertions.assertEquals("PROJECT_FACT", saved.type());
        Assertions.assertEquals("用户正在调优 Dubbo 消费端线程池拒绝策略，关注 CallerRuns 反压效果。", saved.content());
        Assertions.assertEquals("project.thread_pool.reject_policy", saved.metadata().get("targetKey"));
        Assertions.assertEquals("llm_refiner", saved.metadata().get("capturePolicy"));
        Assertions.assertEquals("enabled", operationLogPort.decisionById.get("op-refiner-add").get("refinerStatus"));
        Assertions.assertEquals("ADD", operationLogPort.decisionById.get("op-refiner-add").get("refinerAction"));
        Assertions.assertEquals("project.thread_pool.reject_policy",
                operationLogPort.decisionById.get("op-refiner-add").get("targetKey"));
    }

    @Test
    void shouldExecuteAllSupportedRefinerAddOperationsInOneBatch() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(MemoryRefinementResult.refined(
                "multi_refined",
                List.of(
                        RefinedMemoryOperation.add(
                                "PROJECT_FACT",
                                "project.runtime",
                                "User's project runtime is Java 17.",
                                0.90D,
                                0.70D,
                                List.of("msg-refiner-multi"),
                                List.of("llm_refiner")),
                        RefinedMemoryOperation.add(
                                "PREFERENCE",
                                "preferences.response_style",
                                "User prefers implementation-first answers.",
                                0.88D,
                                0.65D,
                                List.of("msg-refiner-multi"),
                                List.of("llm_refiner"))),
                Map.of("model", "test-refiner")));
        DefaultMemoryEnginePort engine = engineWithRefiner(
                shortTermPort,
                refinerPort,
                true,
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-refiner-multi", "default", "memory-aggregation-flush",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-multi")
                        .messageId("msg-refiner-multi")
                        .message(ChatMessage.user("Remember Java 17 and implementation-first answers."))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertEquals(2, shortTermPort.savedRecords.size());
        Assertions.assertEquals(List.of("stm-msg-refiner-multi-r0", "stm-msg-refiner-multi-r1"),
                shortTermPort.savedRecords.stream().map(MemoryRecord::id).toList());
        Assertions.assertEquals(List.of("project.runtime", "preferences.response_style"),
                shortTermPort.savedRecords.stream().map(record -> record.metadata().get("targetKey")).toList());
        Assertions.assertEquals(2, result.details().get("acceptedRefinerOperations"));
        Assertions.assertEquals(2, result.details().get("refinerOperationCount"));
        Assertions.assertEquals(2, operationLogPort.decisionById.get("op-refiner-multi")
                .get("refinerOperationCount"));
        Assertions.assertFalse(operationLogPort.decisionById.get("op-refiner-multi").containsKey("refinerBatch"));
    }

    @Test
    void shouldCircuitBreakOversizedRefinerBatchToReviewWithoutWritingMemory() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        RecordingMemoryReviewCandidatePort reviewCandidatePort = new RecordingMemoryReviewCandidatePort();
        List<RefinedMemoryOperation> operations = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            operations.add(RefinedMemoryOperation.add(
                    "PROJECT_FACT",
                    "project.fact." + i,
                    "Fact " + i,
                    0.80D,
                    0.70D,
                    List.of("msg-refiner-oversized"),
                    List.of("llm_refiner")));
        }
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(MemoryRefinementResult.refined(
                "oversized_batch",
                operations,
                Map.of("model", "test-refiner")));
        DefaultMemoryEnginePort engine = engineWithRefinerAndReview(
                shortTermPort,
                refinerPort,
                reviewCandidatePort,
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-refiner-oversized", "default",
                "memory-aggregation-flush",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-oversized")
                        .messageId("msg-refiner-oversized")
                        .message(ChatMessage.user("Remember these nine project facts."))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.REJECTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.REVIEW, result.action());
        Assertions.assertEquals("refiner_batch_circuit_breaker", result.reason());
        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
        Assertions.assertEquals(1, reviewCandidatePort.candidates.size());
        MemoryReviewCandidate candidate = reviewCandidatePort.candidates.get(0);
        Assertions.assertEquals(MemoryIngestionAction.REVIEW, candidate.requestedAction());
        Assertions.assertEquals("REFINER_BATCH", candidate.targetKind());
        Assertions.assertEquals(9, candidate.metadata().get("refinerBatchOperationCount"));
        Assertions.assertEquals("operation_count_exceeded", candidate.metadata().get("refinerBatchCircuitReason"));
        Assertions.assertEquals(MemoryOperationStatus.REVIEW,
                operationLogPort.statusById.get("op-refiner-oversized"));
        Assertions.assertEquals("circuit_breaker",
                operationLogPort.decisionById.get("op-refiner-oversized").get("refinerStatus"));
    }

    @Test
    void shouldCircuitBreakHighDeleteRatioRefinerBatchToReviewWithoutStagingDeletes() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        RecordingMemoryReviewCandidatePort reviewCandidatePort = new RecordingMemoryReviewCandidatePort();
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(MemoryRefinementResult.refined(
                "delete_heavy_batch",
                List.of(
                        new RefinedMemoryOperation(
                                MemoryIngestionAction.DELETE,
                                "SHORT_TERM_MEMORY",
                                "stm-old-1",
                                "",
                                0.90D,
                                0.60D,
                                0.50D,
                                0.10D,
                                List.of("msg-refiner-delete-heavy"),
                                List.of("llm_refiner"),
                                Map.of()),
                        new RefinedMemoryOperation(
                                MemoryIngestionAction.DELETE,
                                "SHORT_TERM_MEMORY",
                                "stm-old-2",
                                "",
                                0.90D,
                                0.60D,
                                0.50D,
                                0.10D,
                                List.of("msg-refiner-delete-heavy"),
                                List.of("llm_refiner"),
                                Map.of()),
                        new RefinedMemoryOperation(
                                MemoryIngestionAction.DELETE,
                                "SHORT_TERM_MEMORY",
                                "stm-old-3",
                                "",
                                0.90D,
                                0.60D,
                                0.50D,
                                0.10D,
                                List.of("msg-refiner-delete-heavy"),
                                List.of("llm_refiner"),
                                Map.of()),
                        RefinedMemoryOperation.add(
                                "PROJECT_FACT",
                                "project.current",
                                "User is working on Seahorse memory alignment.",
                                0.80D,
                                0.70D,
                                List.of("msg-refiner-delete-heavy"),
                                List.of("llm_refiner"))),
                Map.of("model", "test-refiner")));
        DefaultMemoryEnginePort engine = engineWithRefinerAndReview(
                shortTermPort,
                refinerPort,
                reviewCandidatePort,
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-refiner-delete-heavy", "default",
                "memory-aggregation-flush",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-delete-heavy")
                        .messageId("msg-refiner-delete-heavy")
                        .message(ChatMessage.user("Clean up old memories and keep the current project fact."))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.REJECTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.REVIEW, result.action());
        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
        Assertions.assertEquals(1, reviewCandidatePort.candidates.size());
        MemoryReviewCandidate candidate = reviewCandidatePort.candidates.get(0);
        Assertions.assertEquals(MemoryIngestionAction.REVIEW, candidate.requestedAction());
        Assertions.assertEquals(4, candidate.metadata().get("refinerBatchOperationCount"));
        Assertions.assertEquals(0.75D, (Double) candidate.metadata().get("refinerBatchDeleteRatio"), 0.001D);
        Assertions.assertEquals("delete_ratio_exceeded", candidate.metadata().get("refinerBatchCircuitReason"));
        Assertions.assertEquals(MemoryOperationStatus.REVIEW,
                operationLogPort.statusById.get("op-refiner-delete-heavy"));
        Assertions.assertEquals("DELETE_RATIO",
                operationLogPort.decisionById.get("op-refiner-delete-heavy").get("refinerBatchCircuitType"));
    }

    @Test
    void shouldPassCurrentExistingMemoriesToRefinerReadMask() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of(new MemoryRecord(
                "stm-existing",
                "SHORT_TERM",
                "PREFERENCE",
                "User prefers concise answers.",
                Map.of("targetKind", "PREFERENCE",
                        "targetKey", "preferences.response_style",
                        "importanceScore", 0.80D,
                        "confidenceLevel", 0.90D),
                Instant.now())));
        StubLongTermMemoryPort longTermPort = new StubLongTermMemoryPort(List.of(new MemoryRecord(
                "ltm-existing",
                "LONG_TERM",
                        "PROJECT_FACT",
                        "User's project currently uses MySQL.",
                        Map.of("targetKind", "PROJECT_FACT",
                                "targetKey", "project.database",
                                "generationId", "project.database:old",
                                "status", "ACTIVE",
                                "importanceScore", 0.92D,
                                "confidenceLevel", 0.95D),
                        Instant.now())));
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(MemoryRefinementResult.empty(
                "no_refined_delta"));
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                longTermPort,
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                new MemoryEngineOptions(5, 3, 10, true, true, true),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                MemoryOperationLogPort.noop(),
                MemoryVectorPort.noop(),
                MemoryOutboxPort.noop(),
                MemoryBusinessDocumentRetrieverPort.noop(),
                MemoryLifecyclePort.noop(),
                MemoryPolicyConfigPort.defaults(),
                (MemoryRetrievalPipelinePort) null,
                refinerPort);

        engine.ingest(new MemoryIngestionCommand("op-refiner-read-mask", "default", "chat-completed",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-read-mask")
                        .messageId("msg-refiner-read-mask")
                        .message(ChatMessage.user("Actually the project has moved to OceanBase."))
                        .build()));

        Assertions.assertEquals(1, refinerPort.requests.size());
        List<MemoryRefinementMemory> existingMemories = refinerPort.requests.get(0).existingMemories();
        Assertions.assertEquals(List.of("stm-existing", "ltm-existing"),
                existingMemories.stream().map(MemoryRefinementMemory::memoryId).toList());
        Assertions.assertEquals(List.of("SHORT_TERM", "LONG_TERM"),
                existingMemories.stream().map(MemoryRefinementMemory::layer).toList());
        Assertions.assertEquals("project.database", existingMemories.get(1).targetKey());
        Assertions.assertEquals("project.database:old", existingMemories.get(1).generationId());
        Assertions.assertEquals(List.of("ltm-existing"),
                refinerPort.requests.get(0).stickyAnchors().stream()
                        .map(MemoryRefinementMemory::memoryId)
                        .toList());
    }

    @Test
    void shouldPassReferenceAndTargetZonesForAggregatedContextBlockToRefiner() {
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(MemoryRefinementResult.empty(
                "no_refined_delta"));
        DefaultMemoryEnginePort engine = engineWithRefiner(new StubShortTermMemoryPort(List.of()), refinerPort, true);

        engine.ingest(new MemoryIngestionCommand("op-refiner-zones", "default", "memory-aggregation-flush",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-zones")
                        .messageId("msg-refiner-zones")
                        .message(ChatMessage.user(memoryContextBlock(5)))
                        .build()));

        Assertions.assertEquals(1, refinerPort.requests.size());
        MemoryRefinementRequest request = refinerPort.requests.get(0);
        Assertions.assertTrue(request.referenceZone().contains("turn_1:"));
        Assertions.assertTrue(request.referenceZone().contains("turn_2:"));
        Assertions.assertFalse(request.referenceZone().contains("turn_5:"));
        Assertions.assertTrue(request.targetZone().contains("turn_3:"));
        Assertions.assertTrue(request.targetZone().contains("turn_5:"));
        Assertions.assertTrue(request.referenceZone().contains("[source_spans]"), request.referenceZone());
        Assertions.assertTrue(request.referenceZone().contains("span_1: msg-1 -> assistant-1"), request.referenceZone());
        Assertions.assertFalse(request.referenceZone().contains("span_5: msg-5 -> assistant-5"), request.referenceZone());
        Assertions.assertTrue(request.targetZone().contains("[source_spans]"), request.targetZone());
        Assertions.assertTrue(request.targetZone().contains("span_3: msg-3 -> assistant-3"), request.targetZone());
        Assertions.assertTrue(request.targetZone().contains("span_5: msg-5 -> assistant-5"), request.targetZone());
        Assertions.assertFalse(request.targetZone().contains("span_1: msg-1 -> assistant-1"), request.targetZone());
    }

    @Test
    void shouldFailOpenToRuleClassificationWhenEnabledRefinerThrows() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        ThrowingMemoryRefinerPort refinerPort = new ThrowingMemoryRefinerPort();
        DefaultMemoryEnginePort engine = engineWithRefiner(
                shortTermPort,
                refinerPort,
                true,
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-refiner-fail-open", "default", "chat-completed",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-fail-open")
                        .messageId("msg-refiner-fail-open")
                        .message(ChatMessage.user("i prefer concise answers"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertEquals(1, refinerPort.requests.size());
        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        Assertions.assertEquals("PREFERENCE", shortTermPort.savedRecords.get(0).type());
        Assertions.assertEquals("failed_open",
                operationLogPort.decisionById.get("op-refiner-fail-open").get("refinerStatus"));
        Assertions.assertTrue(operationLogPort.decisionById.get("op-refiner-fail-open")
                .get("refinerReason").toString().contains("refiner_down"));
    }

    @Test
    void shouldRejectRefinedSensitiveAddBeforeDurableWrite() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(MemoryRefinementResult.refined(
                "refined_sensitive",
                List.of(RefinedMemoryOperation.add(
                        "PROJECT_FACT",
                        "project.secret",
                        "remember that my api key is sk-test-secret",
                        0.90D,
                        0.80D,
                        List.of("msg-refiner-sensitive"),
                        List.of("llm_refiner"))),
                Map.of("model", "test-refiner")));
        DefaultMemoryEnginePort engine = engineWithRefiner(
                shortTermPort,
                refinerPort,
                true,
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-refiner-sensitive", "default", "chat-completed",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-sensitive")
                        .messageId("msg-refiner-sensitive")
                        .message(ChatMessage.user("i prefer concise answers"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.REJECTED, result.status());
        Assertions.assertEquals("sensitive_credential", result.reason());
        Assertions.assertEquals(1, refinerPort.requests.size());
        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
        Assertions.assertEquals(MemoryOperationStatus.REJECTED,
                operationLogPort.statusById.get("op-refiner-sensitive"));
        Assertions.assertEquals("ADD",
                operationLogPort.decisionById.get("op-refiner-sensitive").get("refinerAction"));
        Assertions.assertEquals("sensitive_credential",
                operationLogPort.decisionById.get("op-refiner-sensitive").get("reason"));
    }

    @Test
    void shouldStageRefinedDeleteForReviewWithoutDeletingOrWritingMemory() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        RecordingMemoryReviewCandidatePort reviewCandidatePort = new RecordingMemoryReviewCandidatePort();
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(MemoryRefinementResult.refined(
                "delete_requested",
                List.of(new RefinedMemoryOperation(
                        MemoryIngestionAction.DELETE,
                        "SHORT_TERM_MEMORY",
                        "stm-old-memory",
                        "",
                        0.92D,
                        0.50D,
                        0.50D,
                        0.10D,
                        List.of("msg-refiner-delete"),
                        List.of("llm_refiner"),
                        Map.of())),
                Map.of("model", "test-refiner")));
        DefaultMemoryEnginePort engine = engineWithRefinerAndReview(
                shortTermPort,
                refinerPort,
                reviewCandidatePort,
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-refiner-delete", "default", "memory-aggregation-flush",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-delete")
                        .messageId("msg-refiner-delete")
                        .message(ChatMessage.user("delete the old memory about my previous project"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.REJECTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.REVIEW, result.action());
        Assertions.assertEquals(1, refinerPort.requests.size());
        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
        Assertions.assertEquals(1, reviewCandidatePort.candidates.size());
        MemoryReviewCandidate candidate = reviewCandidatePort.candidates.get(0);
        Assertions.assertEquals(MemoryIngestionAction.DELETE, candidate.requestedAction());
        Assertions.assertEquals("SHORT_TERM_MEMORY", candidate.targetKind());
        Assertions.assertEquals("stm-old-memory", candidate.targetKey());
        Assertions.assertEquals(MemoryOperationStatus.REVIEW,
                operationLogPort.statusById.get("op-refiner-delete"));
        Assertions.assertEquals("delete_requested",
                operationLogPort.decisionById.get("op-refiner-delete").get("refinerReason"));
        Assertions.assertEquals("pending_review",
                operationLogPort.decisionById.get("op-refiner-delete").get("refinerStatus"));
        Assertions.assertEquals("DELETE",
                operationLogPort.decisionById.get("op-refiner-delete").get("refinerAction"));
    }

    @Test
    void shouldStageRefinedUpdateForReviewWithoutUpdatingDurableMemory() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        RecordingMemoryReviewCandidatePort reviewCandidatePort = new RecordingMemoryReviewCandidatePort();
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(MemoryRefinementResult.refined(
                "update_requested",
                List.of(new RefinedMemoryOperation(
                        MemoryIngestionAction.UPDATE,
                        "PROFILE_SLOT",
                        "preferences.response_style",
                        "user now prefers detailed answers",
                        0.88D,
                        0.70D,
                        0.80D,
                        0.20D,
                        List.of("msg-refiner-update"),
                        List.of("llm_refiner"),
                        Map.of("previousValue", "concise answers"))),
                Map.of("model", "test-refiner")));
        DefaultMemoryEnginePort engine = engineWithRefinerAndReview(
                shortTermPort,
                refinerPort,
                reviewCandidatePort,
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-refiner-update", "default", "memory-aggregation-flush",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-update")
                        .messageId("msg-refiner-update")
                        .message(ChatMessage.user("actually I now prefer detailed answers"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.REJECTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.REVIEW, result.action());
        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
        Assertions.assertEquals(1, reviewCandidatePort.candidates.size());
        MemoryReviewCandidate candidate = reviewCandidatePort.candidates.get(0);
        Assertions.assertEquals(MemoryIngestionAction.UPDATE, candidate.requestedAction());
        Assertions.assertEquals("PROFILE_SLOT", candidate.targetKind());
        Assertions.assertEquals("preferences.response_style", candidate.targetKey());
        Assertions.assertEquals("user now prefers detailed answers", candidate.content());
        Assertions.assertEquals("concise answers", candidate.metadata().get("previousValue"));
        Assertions.assertEquals(MemoryOperationStatus.REVIEW,
                operationLogPort.statusById.get("op-refiner-update"));
        Assertions.assertEquals("UPDATE",
                operationLogPort.decisionById.get("op-refiner-update").get("refinerAction"));
    }

    @Test
    void shouldKeepRefinedReviewAsPendingReviewWithoutActiveMemoryWrite() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        RecordingMemoryReviewCandidatePort reviewCandidatePort = new RecordingMemoryReviewCandidatePort();
        RecordingMemoryRefinerPort refinerPort = new RecordingMemoryRefinerPort(MemoryRefinementResult.refined(
                "needs_review",
                List.of(new RefinedMemoryOperation(
                        MemoryIngestionAction.REVIEW,
                        "PROJECT_FACT",
                        "project.ambiguous",
                        "user might be changing the project stack",
                        0.62D,
                        0.70D,
                        0.70D,
                        0.20D,
                        List.of("msg-refiner-review"),
                        List.of("llm_refiner", "low_confidence"),
                        Map.of("reviewReason", "low_confidence"))),
                Map.of("model", "test-refiner")));
        DefaultMemoryEnginePort engine = engineWithRefinerAndReview(
                shortTermPort,
                refinerPort,
                reviewCandidatePort,
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-refiner-review", "default", "memory-aggregation-flush",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-refiner-review")
                        .messageId("msg-refiner-review")
                        .message(ChatMessage.user("maybe we are moving the project from Dubbo to something else"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.REJECTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.REVIEW, result.action());
        Assertions.assertEquals("needs_review", result.reason());
        Assertions.assertEquals(1, refinerPort.requests.size());
        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
        Assertions.assertEquals(1, reviewCandidatePort.candidates.size());
        MemoryReviewCandidate candidate = reviewCandidatePort.candidates.get(0);
        Assertions.assertEquals("op-refiner-review", candidate.operationId());
        Assertions.assertEquals("PROJECT_FACT", candidate.targetKind());
        Assertions.assertEquals("project.ambiguous", candidate.targetKey());
        Assertions.assertEquals("user might be changing the project stack", candidate.content());
        Assertions.assertEquals(MemoryIngestionAction.REVIEW, candidate.requestedAction());
        Assertions.assertEquals("low_confidence", candidate.metadata().get("reviewReason"));
        Assertions.assertEquals(MemoryOperationStatus.REVIEW,
                operationLogPort.statusById.get("op-refiner-review"));
        Assertions.assertEquals("REVIEW",
                operationLogPort.decisionById.get("op-refiner-review").get("refinerAction"));
    }

    @Test
    void shouldApplyReviewDirectiveTargetProfileSlotWithoutReclassification() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingProfileMemoryPort profileMemoryPort = new RecordingProfileMemoryPort();
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                profileMemoryPort,
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                operationLogPort);

        var result = engine.ingest(MemoryIngestionCommand.reviewApply(
                "op-review-apply",
                "tenant-1",
                "memory-review-modify",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-review-apply")
                        .messageId("msg-review-apply")
                        .message(ChatMessage.user("detailed answers"))
                        .build(),
                new MemoryReviewApplyDirective(
                        MemoryIngestionAction.UPDATE,
                        "SHORT_TERM",
                        "PROFILE_SLOT",
                        "preferences.response_style",
                        0.91D,
                        0.70D,
                        0.80D,
                        0.10D,
                        List.of("msg-original"),
                        Map.of("reviewReason", "manual_fix"))));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.UPDATE, result.action());
        Assertions.assertTrue(result.operations().contains("SHORT_TERM_SAVE"));
        Assertions.assertTrue(result.operations().contains("PROFILE_UPSERT"));
        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        MemoryRecord saved = shortTermPort.savedRecords.get(0);
        Assertions.assertEquals("SHORT_TERM", saved.layer());
        Assertions.assertEquals("PROFILE_SLOT", saved.metadata().get("targetKind"));
        Assertions.assertEquals("preferences.response_style", saved.metadata().get("targetKey"));
        Assertions.assertEquals("UPDATE", saved.metadata().get("refinerAction"));
        Assertions.assertEquals("review_applied", saved.metadata().get("refinerStatus"));
        Assertions.assertEquals(1, profileMemoryPort.updates.size());
        ProfileFactUpdate profileUpdate = profileMemoryPort.updates.get(0);
        Assertions.assertEquals("preferences.response_style", profileUpdate.slotKey());
        Assertions.assertEquals("detailed answers", profileUpdate.valueText());
        Assertions.assertEquals(MemoryOperationType.UPDATE, operationLogPort.started.get(0).operationType());
        Assertions.assertEquals("PROFILE_SLOT", operationLogPort.started.get(0).targetKind());
        Assertions.assertEquals("preferences.response_style", operationLogPort.started.get(0).targetKey());
    }

    @Test
    void shouldApplyReviewDirectiveTargetSemanticLayerWithoutReclassification() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        StubSemanticMemoryPort semanticPort = new StubSemanticMemoryPort(List.of());
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                semanticPort,
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                operationLogPort);

        var result = engine.ingest(MemoryIngestionCommand.reviewApply(
                "op-review-semantic",
                "tenant-1",
                "memory-review-approve",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-review-semantic")
                        .messageId("msg-review-semantic")
                        .message(ChatMessage.user("project Seahorse uses a four-layer memory model"))
                        .build(),
                new MemoryReviewApplyDirective(
                        MemoryIngestionAction.ADD,
                        "SEMANTIC",
                        "PROJECT_FACT",
                        "project.memory.layers",
                        0.93D,
                        0.80D,
                        0.85D,
                        0.05D,
                        List.of("msg-original"),
                        Map.of("reviewReason", "stable_fact"))));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertTrue(result.operations().contains("SEMANTIC_SAVE"));
        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
        Assertions.assertEquals(1, semanticPort.savedRecords.size());
        MemoryRecord saved = semanticPort.savedRecords.get(0);
        Assertions.assertEquals("SEMANTIC", saved.layer());
        Assertions.assertEquals("PROJECT_FACT", saved.type());
        Assertions.assertEquals("project.memory.layers", saved.metadata().get("targetKey"));
        Assertions.assertEquals("review_applied", saved.metadata().get("refinerStatus"));
        Assertions.assertEquals(MemoryOperationType.ADD, operationLogPort.started.get(0).operationType());
        Assertions.assertEquals("PROJECT_FACT", operationLogPort.started.get(0).targetKind());
        Assertions.assertEquals("project.memory.layers", operationLogPort.started.get(0).targetKey());
    }

    @Test
    void shouldApplyReviewDirectiveDeleteTargetMemoryAndDerivedIndexes() {
        MemoryRecord existing = new MemoryRecord(
                "stm-old-memory",
                "SHORT_TERM",
                "SHORT_TERM_MEMORY",
                "obsolete memory",
                Map.of("userId", USER_ID, "tenantId", "tenant-1"),
                Instant.now());
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of(existing));
        RecordingMemoryOutboxPort outboxPort = new RecordingMemoryOutboxPort();
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                new MemoryEngineOptions(5, 3, 10, true, false, true, true, true),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                operationLogPort,
                new ThrowingMemoryVectorPort(),
                outboxPort,
                MemoryBusinessDocumentRetrieverPort.noop());

        var result = engine.ingest(MemoryIngestionCommand.reviewApply(
                "op-review-delete",
                "tenant-1",
                "memory-review-approve",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-review-delete")
                        .messageId("msg-review-delete")
                        .message(ChatMessage.user(""))
                        .build(),
                new MemoryReviewApplyDirective(
                        MemoryIngestionAction.DELETE,
                        "SHORT_TERM",
                        "SHORT_TERM_MEMORY",
                        "stm-old-memory",
                        0.95D,
                        0.40D,
                        0.50D,
                        0.10D,
                        List.of("msg-original"),
                        Map.of())));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.DELETE, result.action());
        Assertions.assertTrue(result.operations().contains("SHORT_TERM_DELETE"));
        Assertions.assertTrue(result.operations().contains("VECTOR_DELETE_OUTBOX_ENQUEUE"));
        Assertions.assertTrue(result.operations().contains("KEYWORD_DELETE_OUTBOX_ENQUEUE"));
        Assertions.assertTrue(result.operations().contains("GRAPH_DELETE_OUTBOX_ENQUEUE"));
        Assertions.assertEquals(List.of("stm-old-memory"), shortTermPort.deletedIds);
        Assertions.assertEquals(List.of("VECTOR_DELETE", "KEYWORD_DELETE", "GRAPH_DELETE"),
                outboxPort.tasks.stream().map(MemoryOutboxPort.MemoryOutboxTask::taskType).toList());
        Assertions.assertEquals(MemoryOperationType.DELETE, operationLogPort.started.get(0).operationType());
        Assertions.assertEquals("SHORT_TERM_MEMORY", operationLogPort.started.get(0).targetKind());
        Assertions.assertEquals("stm-old-memory", operationLogPort.started.get(0).targetKey());
        Assertions.assertEquals(MemoryOperationStatus.SUCCEEDED,
                operationLogPort.statusById.get("op-review-delete"));
    }

    @Test
    void shouldRejectReviewDirectiveDeleteWhenTargetMemoryIsMissing() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOutboxPort outboxPort = new RecordingMemoryOutboxPort();
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                operationLogPort,
                MemoryVectorPort.noop(),
                outboxPort,
                MemoryBusinessDocumentRetrieverPort.noop());

        var result = engine.ingest(MemoryIngestionCommand.reviewApply(
                "op-review-delete-missing",
                "tenant-1",
                "memory-review-approve",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-review-delete")
                        .messageId("msg-review-delete")
                        .message(ChatMessage.user("delete missing memory"))
                        .build(),
                new MemoryReviewApplyDirective(
                        MemoryIngestionAction.DELETE,
                        "SHORT_TERM",
                        "SHORT_TERM_MEMORY",
                        "stm-missing",
                        0.95D,
                        0.40D,
                        0.50D,
                        0.10D,
                        List.of("msg-original"),
                        Map.of())));

        Assertions.assertEquals(MemoryIngestionStatus.REJECTED, result.status());
        Assertions.assertEquals("review_delete_target_not_found", result.reason());
        Assertions.assertTrue(shortTermPort.deletedIds.isEmpty());
        Assertions.assertTrue(outboxPort.tasks.isEmpty());
        Assertions.assertEquals(MemoryOperationStatus.REJECTED,
                operationLogPort.statusById.get("op-review-delete-missing"));
    }

    @Test
    void shouldEnqueueOutboxWhenVectorIndexingFailsWithoutRollingBackMemoryWrite() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOutboxPort outboxPort = new RecordingMemoryOutboxPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                MemoryOperationLogPort.noop(),
                new ThrowingMemoryVectorPort(),
                outboxPort,
                MemoryBusinessDocumentRetrieverPort.noop());

        var result = engine.ingest(new MemoryIngestionCommand("op-vector-fail", "default", "chat-completed",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-vector")
                        .messageId("msg-vector")
                        .message(ChatMessage.user("请记住：我喜欢简短回答"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        Assertions.assertTrue(result.operations().contains("VECTOR_OUTBOX_ENQUEUE"));
        Assertions.assertEquals(1, outboxPort.tasks.size());
        Assertions.assertEquals("VECTOR_UPSERT", outboxPort.tasks.get(0).taskType());
    }

    @Test
    void shouldEnqueueDerivedIndexOutboxTasksWhenConfigured() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        RecordingMemoryOutboxPort outboxPort = new RecordingMemoryOutboxPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                new MemoryEngineOptions(5, 3, 10, true, false, true, true, true),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                MemoryOperationLogPort.noop(),
                new RecordingMemoryVectorPort(List.of()),
                outboxPort,
                MemoryBusinessDocumentRetrieverPort.noop());

        var result = engine.ingest(new MemoryIngestionCommand("op-derived-index", "default", "chat-completed",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-derived-index")
                        .messageId("msg-derived-index")
                        .message(ChatMessage.user("请记住：我喜欢简短回答"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        Assertions.assertTrue(result.operations().contains("VECTOR_UPSERT"));
        Assertions.assertTrue(result.operations().contains("KEYWORD_OUTBOX_ENQUEUE"));
        Assertions.assertTrue(result.operations().contains("GRAPH_OUTBOX_ENQUEUE"));
        Assertions.assertEquals(List.of("KEYWORD_UPSERT", "GRAPH_UPSERT"),
                outboxPort.tasks.stream().map(MemoryOutboxPort.MemoryOutboxTask::taskType).toList());
        Assertions.assertEquals(shortTermPort.savedRecords.get(0).id(), outboxPort.tasks.get(0).targetId());
        Assertions.assertEquals(shortTermPort.savedRecords.get(0).id(), outboxPort.tasks.get(1).targetId());
    }

    @Test
    void shouldRecallVectorHitMemoriesAndFilterObsoleteProfileGeneration() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of(
                semanticRecord("stm-old-profile", "PROFILE", "我是一名学生", 0.9D,
                        Instant.parse("2026-05-19T00:00:00Z"), "identity.occupation", "identity.occupation:old"),
                record("stm-project", "SUMMARY", "用户在 Seahorse 项目中调试记忆召回", 0.8D)));
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        profilePort.upsert(new ProfileFactUpdate(
                USER_ID,
                "default",
                "identity.occupation",
                "老师",
                0.95D,
                "explicit_user_correction",
                List.of("msg-correction"),
                "identity.occupation:new"));
        RecordingMemoryVectorPort vectorPort = new RecordingMemoryVectorPort(List.of("stm-old-profile", "stm-project"));
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                profilePort,
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                MemoryOperationLogPort.noop(),
                vectorPort,
                MemoryOutboxPort.noop(),
                MemoryBusinessDocumentRetrieverPort.noop());

        MemoryContext context = engine.loadMemory(MemoryLoadRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-read")
                .currentQuestion("之前 Seahorse 项目里调试过什么？")
                .build());

        Assertions.assertEquals(List.of("之前 Seahorse 项目里调试过什么？"), vectorPort.queries);
        Assertions.assertTrue(context.getShortTermMemories().stream()
                .anyMatch(item -> "stm-project".equals(item.getId())));
        Assertions.assertTrue(context.getShortTermMemories().stream()
                .noneMatch(item -> "stm-old-profile".equals(item.getId())));
    }

    @Test
    void shouldAppendBusinessDocumentCandidatesForBusinessRuleQuestions() {
        RecordingBusinessDocumentRetrieverPort businessPort = new RecordingBusinessDocumentRetrieverPort(List.of(
                MemoryItem.builder()
                        .id("biz-1")
                        .type("BUSINESS_DOCUMENT")
                        .content("报销规则：超过 5000 元需要审批")
                        .metadataJson("{\"docId\":\"policy-1\",\"generationId\":\"doc:v2\"}")
                        .build()));
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                new StubShortTermMemoryPort(List.of()),
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                MemoryOperationLogPort.noop(),
                MemoryVectorPort.noop(),
                MemoryOutboxPort.noop(),
                businessPort);

        MemoryContext context = engine.loadMemory(MemoryLoadRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-biz")
                .currentQuestion("知识库里的报销规则是什么？")
                .build());

        Assertions.assertEquals(List.of("知识库里的报销规则是什么？"), businessPort.queries);
        Assertions.assertTrue(context.getBusinessDocumentMemories().stream()
                .anyMatch(item -> item.getContent().contains("超过 5000 元需要审批")));
        Assertions.assertTrue(context.getSemanticMemories().isEmpty());
    }

    @Test
    void shouldRecordUpdateOperationForExplicitCorrection() {
        RecordingProfileMemoryPort profilePort = new RecordingProfileMemoryPort();
        RecordingCorrectionLedgerPort correctionPort = new RecordingCorrectionLedgerPort();
        RecordingMemoryOperationLogPort operationLogPort = new RecordingMemoryOperationLogPort();
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                new StubShortTermMemoryPort(List.of()),
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                MemoryEngineOptions.defaults(),
                profilePort,
                correctionPort,
                new DefaultMemoryRouter(),
                operationLogPort);

        var result = engine.ingest(new MemoryIngestionCommand("op-correction", "default", "chat-completed",
                MemoryWriteRequest.builder()
                        .userId(USER_ID)
                        .conversationId("conv-correction-op")
                        .messageId("msg-correction-op")
                        .message(ChatMessage.user("我不是学生了，我现在是老师"))
                        .build()));

        Assertions.assertEquals(MemoryIngestionStatus.ACCEPTED, result.status());
        Assertions.assertEquals(MemoryIngestionAction.UPDATE, result.action());
        Assertions.assertEquals(List.of("CORRECTION_UPSERT", "PROFILE_UPSERT"), result.operations());
        Assertions.assertEquals(1, correctionPort.commands.size());
        Assertions.assertEquals(1, profilePort.updates.size());
        Assertions.assertEquals(MemoryOperationType.UPDATE, operationLogPort.started.get(0).operationType());
        Assertions.assertEquals("PROFILE_SLOT", operationLogPort.started.get(0).targetKind());
        Assertions.assertEquals("identity.occupation", operationLogPort.started.get(0).targetKey());
        Assertions.assertEquals(MemoryOperationStatus.SUCCEEDED, operationLogPort.statusById.get("op-correction"));
        Assertions.assertEquals("老师", operationLogPort.decisionById.get("op-correction").get("correctValue"));
    }

    @Test
    void shouldSkipMemoryCaptureWhenDisabled() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of());
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                new MemoryEngineOptions(5, 3, 10, false));

        engine.writeMemory(MemoryWriteRequest.builder()
                .userId(USER_ID)
                .conversationId("conv-1")
                .messageId("msg-3")
                .message(ChatMessage.user("请记住：我喜欢 Java"))
                .build());

        Assertions.assertTrue(shortTermPort.savedRecords.isEmpty());
    }

    @Test
    void shouldAssessQualityWithBasicCounts() {
        StubShortTermMemoryPort shortTermPort = new StubShortTermMemoryPort(List.of(
                record("stm-1", "SUMMARY", "A", 0.5D),
                record("stm-2", "FACT", "B", 0.3D)));
        StubLongTermMemoryPort longTermPort = new StubLongTermMemoryPort(List.of(
                record("ltm-1", "PROFILE", "C", 0.8D)));
        StubSemanticMemoryPort semanticPort = new StubSemanticMemoryPort(List.of());

        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                shortTermPort, longTermPort, semanticPort, OBJECT_MAPPER);

        MemoryQualityReport report = engine.assessMemoryQuality(USER_ID);

        Assertions.assertEquals(USER_ID, report.getUserId());
        Assertions.assertEquals(2, report.getShortTermCount());
        Assertions.assertEquals(1, report.getLongTermCount());
        Assertions.assertEquals(0, report.getSemanticCount());
    }

    @Test
    void shouldRetrieveAllMemoriesAcrossLayers() {
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                new StubShortTermMemoryPort(List.of(record("stm-1", "SUMMARY", "A", 0.5D))),
                new StubLongTermMemoryPort(List.of(record("ltm-1", "PROFILE", "B", 0.8D))),
                new StubSemanticMemoryPort(List.of(record("sem-1", "PROFILE", "C", 0.9D))),
                OBJECT_MAPPER);

        List<MemoryItem> all = engine.retrieveMemories(MemoryLoadRequest.builder()
                .userId(USER_ID).build());

        Assertions.assertEquals(3, all.size());
    }

    // ========== 辅助方法 ==========

    private MemoryRecord record(String id, String type, String content, double importanceScore) {
        return new MemoryRecord(id, "layer", type, content,
                Map.of("userId", USER_ID, "importanceScore", importanceScore,
                        "confidenceLevel", importanceScore),
                Instant.now());
    }

    // ========== Stub 实现 ==========

    private MemoryRecord semanticRecord(String id,
                                        String type,
                                        String content,
                                        double importanceScore,
                                        Instant updatedAt,
                                        String semanticKey) {
        return semanticRecord(id, type, content, importanceScore, updatedAt, semanticKey, "");
    }

    private MemoryRecord semanticRecord(String id,
                                        String type,
                                        String content,
                                        double importanceScore,
                                        Instant updatedAt,
                                        String semanticKey,
                                        String generationId) {
        return new MemoryRecord(id, "semantic", type, content,
                Map.of("userId", USER_ID,
                        "importanceScore", importanceScore,
                        "confidenceLevel", importanceScore,
                        "semanticKey", semanticKey,
                        "generationId", generationId),
                updatedAt);
    }

    private DefaultMemoryEnginePort engineWithRefiner(ShortTermMemoryPort shortTermPort,
                                                      MemoryRefinerPort refinerPort,
                                                      boolean enabled) {
        return engineWithRefiner(shortTermPort, refinerPort, enabled, MemoryOperationLogPort.noop());
    }

    private DefaultMemoryEnginePort engineWithRefiner(ShortTermMemoryPort shortTermPort,
                                                      MemoryRefinerPort refinerPort,
                                                      boolean enabled,
                                                      MemoryOperationLogPort operationLogPort) {
        return new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                new MemoryEngineOptions(5, 3, 10, true, enabled, true),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                operationLogPort,
                MemoryVectorPort.noop(),
                MemoryOutboxPort.noop(),
                MemoryBusinessDocumentRetrieverPort.noop(),
                MemoryLifecyclePort.noop(),
                MemoryPolicyConfigPort.defaults(),
                (MemoryRetrievalPipelinePort) null,
                refinerPort);
    }

    private DefaultMemoryEnginePort engineWithRefinerAndReview(ShortTermMemoryPort shortTermPort,
                                                               MemoryRefinerPort refinerPort,
                                                               MemoryReviewCandidatePort reviewCandidatePort,
                                                               MemoryOperationLogPort operationLogPort) {
        return new DefaultMemoryEnginePort(
                shortTermPort,
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER,
                new MemoryEngineOptions(5, 3, 10, true, true, true),
                ProfileMemoryPort.noop(),
                CorrectionLedgerPort.noop(),
                new DefaultMemoryRouter(),
                operationLogPort,
                MemoryVectorPort.noop(),
                MemoryOutboxPort.noop(),
                MemoryBusinessDocumentRetrieverPort.noop(),
                MemoryLifecyclePort.noop(),
                new InMemoryMemoryPolicyConfigPort(MemoryPolicyConfig.defaults().withReviewEnabled(true)),
                (MemoryRetrievalPipelinePort) null,
                refinerPort,
                reviewCandidatePort);
    }

    private String memoryContextBlock(int turnCount) {
        StringBuilder builder = new StringBuilder();
        builder.append("MEMORY_CONTEXT_BLOCK: v1\n");
        builder.append("turn_count: ").append(turnCount).append("\n\n");
        builder.append("[turns]\n");
        for (int i = 1; i <= turnCount; i++) {
            builder.append("turn_").append(i).append(":\n");
            builder.append("  user: turn ").append(i).append(" user text\n");
            builder.append("  assistant: turn ").append(i).append(" assistant text\n");
        }
        builder.append("\n[source_spans]\n");
        for (int i = 1; i <= turnCount; i++) {
            builder.append("span_").append(i).append(": msg-").append(i).append(" -> assistant-").append(i).append("\n");
        }
        return builder.toString();
    }

    private static class StubShortTermMemoryPort implements ShortTermMemoryPort {
        private final List<MemoryRecord> records;
        private final List<MemoryRecord> savedRecords = new java.util.ArrayList<>();
        private final List<String> deletedIds = new ArrayList<>();
        private int lastListByUserLimit;

        StubShortTermMemoryPort(List<MemoryRecord> records) {
            this.records = records;
        }

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return records.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return records;
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            lastListByUserLimit = limit;
            return records.stream().limit(limit).toList();
        }

        @Override
        public void save(MemoryRecord record) {
            savedRecords.add(record);
        }

        @Override
        public boolean deleteById(String id) {
            boolean exists = records.stream().anyMatch(r -> r.id().equals(id));
            if (exists) {
                deletedIds.add(id);
            }
            return exists;
        }
    }

    private static class StubLongTermMemoryPort implements LongTermMemoryPort {
        private final List<MemoryRecord> records;
        private final List<String> deletedIds = new ArrayList<>();
        private int lastListByUserLimit;

        StubLongTermMemoryPort(List<MemoryRecord> records) {
            this.records = records;
        }

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return records.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return records;
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            lastListByUserLimit = limit;
            return records.stream().limit(limit).toList();
        }

        @Override
        public void save(MemoryRecord record) {
        }

        @Override
        public boolean deleteById(String id) {
            boolean exists = records.stream().anyMatch(r -> r.id().equals(id));
            if (exists) {
                deletedIds.add(id);
            }
            return exists;
        }
    }

    private static class StubSemanticMemoryPort implements SemanticMemoryPort {
        private final List<MemoryRecord> records;
        private final List<MemoryRecord> savedRecords = new java.util.ArrayList<>();
        private final List<String> deletedIds = new ArrayList<>();
        private int lastListByUserLimit;

        StubSemanticMemoryPort(List<MemoryRecord> records) {
            this.records = records;
        }

        @Override
        public Optional<MemoryRecord> findById(String id) {
            return records.stream().filter(r -> r.id().equals(id)).findFirst();
        }

        @Override
        public List<MemoryRecord> listByConversation(String conversationId, int limit) {
            return records;
        }

        @Override
        public List<MemoryRecord> listByUser(String userId, int limit) {
            lastListByUserLimit = limit;
            return records.stream().limit(limit).toList();
        }

        @Override
        public void save(MemoryRecord record) {
            savedRecords.add(record);
        }

        @Override
        public boolean deleteById(String id) {
            boolean exists = records.stream().anyMatch(r -> r.id().equals(id));
            if (exists) {
                deletedIds.add(id);
            }
            return exists;
        }
    }

    private static class RecordingProfileMemoryPort implements ProfileMemoryPort {

        private final List<ProfileFactUpdate> updates = new ArrayList<>();
        private final Map<String, ProfileFact> activeFacts = new java.util.LinkedHashMap<>();

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
            updates.add(update);
            activeFacts.put(update.slotKey(), new ProfileFact(
                    "profile-" + updates.size(),
                    update.userId(),
                    update.tenantId(),
                    update.slotKey(),
                    update.valueText(),
                    update.confidenceLevel(),
                    update.sourceType(),
                    update.generationId(),
                    "ACTIVE",
                    Instant.now()));
        }
    }

    private static class RecordingCorrectionLedgerPort implements CorrectionLedgerPort {

        private final List<CorrectionCommand> commands = new ArrayList<>();
        private final List<CorrectionRule> rules = new ArrayList<>();

        @Override
        public List<CorrectionRule> listActive(String userId, String tenantId, int limit) {
            return rules.stream().limit(limit).toList();
        }

        @Override
        public void upsert(CorrectionCommand command) {
            commands.add(command);
            rules.add(new CorrectionRule(
                    "corr-" + commands.size(),
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
                    Instant.now()));
        }
    }

    private static class RecordingMemoryOperationLogPort implements MemoryOperationLogPort {

        private final List<MemoryOperation> started = new ArrayList<>();
        private final Map<String, MemoryOperationStatus> statusById = new LinkedHashMap<>();
        private final Map<String, Map<String, Object>> decisionById = new LinkedHashMap<>();

        @Override
        public boolean tryStart(MemoryOperation operation) {
            if (statusById.containsKey(operation.operationId())) {
                return false;
            }
            started.add(operation);
            statusById.put(operation.operationId(), MemoryOperationStatus.STARTED);
            return true;
        }

        @Override
        public void markCompleted(String operationId, MemoryOperationStatus status, Map<String, Object> decision) {
            statusById.put(operationId, status);
            decisionById.put(operationId, decision);
        }

        @Override
        public void markFailed(String operationId, String errorMessage) {
            statusById.put(operationId, MemoryOperationStatus.FAILED);
            decisionById.put(operationId, Map.of("errorMessage", errorMessage));
        }
    }

    private static class RecordingMemoryRefinerPort implements MemoryRefinerPort {

        private final MemoryRefinementResult result;
        private final List<MemoryRefinementRequest> requests = new ArrayList<>();

        RecordingMemoryRefinerPort(MemoryRefinementResult result) {
            this.result = result;
        }

        @Override
        public MemoryRefinementResult refine(MemoryRefinementRequest request) {
            requests.add(request);
            return result;
        }
    }

    private static class RecordingMemoryReviewCandidatePort implements MemoryReviewCandidatePort {

        private final List<MemoryReviewCandidate> candidates = new ArrayList<>();

        @Override
        public void save(MemoryReviewCandidate candidate) {
            candidates.add(candidate);
        }
    }

    private static class ThrowingMemoryRefinerPort implements MemoryRefinerPort {

        private final List<MemoryRefinementRequest> requests = new ArrayList<>();

        @Override
        public MemoryRefinementResult refine(MemoryRefinementRequest request) {
            requests.add(request);
            throw new IllegalStateException("refiner_down");
        }
    }

    private static class ThrowingMemoryVectorPort implements MemoryVectorPort {

        @Override
        public void upsert(String memoryId, String userId, String content, String embeddingModel) {
            throw new IllegalStateException("vector down");
        }

        @Override
        public List<String> search(String userId, String query, int topK) {
            return List.of();
        }
    }

    private static class RecordingMemoryVectorPort implements MemoryVectorPort {

        private final List<String> hits;
        private final List<String> queries = new ArrayList<>();

        RecordingMemoryVectorPort(List<String> hits) {
            this.hits = hits;
        }

        @Override
        public void upsert(String memoryId, String userId, String content, String embeddingModel) {
        }

        @Override
        public List<String> search(String userId, String query, int topK) {
            queries.add(query);
            return hits.stream().limit(topK).toList();
        }
    }

    private static class RecordingMemoryOutboxPort implements MemoryOutboxPort {

        private final List<MemoryOutboxPort.MemoryOutboxTask> tasks = new ArrayList<>();

        @Override
        public void enqueue(MemoryOutboxPort.MemoryOutboxTask task) {
            tasks.add(task);
        }
    }

    private static class RecordingBusinessDocumentRetrieverPort implements MemoryBusinessDocumentRetrieverPort {

        private final List<MemoryItem> items;
        private final List<String> queries = new ArrayList<>();

        RecordingBusinessDocumentRetrieverPort(List<MemoryItem> items) {
            this.items = items;
        }

        @Override
        public List<MemoryItem> retrieve(String tenantId, String query, int topK) {
            queries.add(query);
            return items.stream().limit(topK).toList();
        }
    }
}

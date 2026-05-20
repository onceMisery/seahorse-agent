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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionAction;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryIngestionStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationType;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFact;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileFactUpdate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ProfileMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
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
        Assertions.assertEquals(1, context.getProfileMemories().size());
        Assertions.assertEquals("老师", context.getProfileMemories().get(0).getContent());
        String promptMemory = MemoryPromptFormatter.format(context);
        Assertions.assertTrue(promptMemory.indexOf("用户纠错本：") < promptMemory.indexOf("用户画像："));
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
        Assertions.assertEquals(List.of("SHORT_TERM_SAVE"), result.operations());
        Assertions.assertEquals(1, shortTermPort.savedRecords.size());
        Assertions.assertEquals(MemoryOperationType.ADD, operationLogPort.started.get(0).operationType());
        Assertions.assertEquals("SHORT_TERM_MEMORY", operationLogPort.started.get(0).targetKind());
        Assertions.assertEquals(MemoryOperationStatus.SUCCEEDED, operationLogPort.statusById.get("op-preference"));
        Assertions.assertEquals("PREFERENCE", operationLogPort.decisionById.get("op-preference").get("memoryType"));
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
        return new MemoryRecord(id, "semantic", type, content,
                Map.of("userId", USER_ID,
                        "importanceScore", importanceScore,
                        "confidenceLevel", importanceScore,
                        "semanticKey", semanticKey),
                updatedAt);
    }

    private static class StubShortTermMemoryPort implements ShortTermMemoryPort {
        private final List<MemoryRecord> records;
        private final List<MemoryRecord> savedRecords = new java.util.ArrayList<>();
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
            return false;
        }
    }

    private static class StubLongTermMemoryPort implements LongTermMemoryPort {
        private final List<MemoryRecord> records;
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
            return false;
        }
    }

    private static class StubSemanticMemoryPort implements SemanticMemoryPort {
        private final List<MemoryRecord> records;
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
        }

        @Override
        public boolean deleteById(String id) {
            return false;
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
}

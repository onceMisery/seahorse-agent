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
import com.miracle.ai.seahorse.agent.kernel.domain.memory.MemoryQualityReport;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.LongTermMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.SemanticMemoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.ShortTermMemoryPort;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.Instant;
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
    void shouldNotWriteMemoryInPhaseOne() {
        DefaultMemoryEnginePort engine = new DefaultMemoryEnginePort(
                new StubShortTermMemoryPort(List.of()),
                new StubLongTermMemoryPort(List.of()),
                new StubSemanticMemoryPort(List.of()),
                OBJECT_MAPPER);

        // writeMemory 应该是 no-op，不抛异常
        engine.writeMemory(null);
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

    private static class StubShortTermMemoryPort implements ShortTermMemoryPort {
        private final List<MemoryRecord> records;

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
            return records;
        }

        @Override
        public void save(MemoryRecord record) {
        }

        @Override
        public boolean deleteById(String id) {
            return false;
        }
    }

    private static class StubLongTermMemoryPort implements LongTermMemoryPort {
        private final List<MemoryRecord> records;

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
            return records;
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
            return records;
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

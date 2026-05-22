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

package com.miracle.ai.seahorse.agent.adapters.repository.jdbc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryAggregationBufferAdapterTests {

    private JdbcMemoryAggregationBufferAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:memory-aggregation-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        new JdbcChatSchemaUpgrade(dataSource).upgrade();
        adapter = new JdbcMemoryAggregationBufferAdapter(dataSource, new ObjectMapper(),
                new MemoryAggregationPolicy(true, 40_000, 3, 120, 32, 86_400_000, false));
    }

    @Test
    void shouldAppendTurnsAndFlushIdleSnapshot() {
        Instant firstAt = Instant.parse("2026-05-22T08:00:00Z");
        adapter.appendTurn(turn("msg-1", "hello", "hi", 20, firstAt));
        var state = adapter.appendTurn(turn("msg-2", "remember java", "ok", 30, firstAt.plusSeconds(5)));

        assertThat(state.turnCount()).isEqualTo(2);
        assertThat(state.totalTokens()).isEqualTo(50);
        assertThat(state.forceFlushRequired()).isFalse();

        assertThat(adapter.flushReady("session-1", "tenant-1", MemoryFlushTrigger.IDLE_TIMEOUT,
                firstAt.plusSeconds(20))).isEmpty();

        var snapshot = adapter.flushReady("session-1", "tenant-1", MemoryFlushTrigger.IDLE_TIMEOUT,
                firstAt.plusSeconds(50));

        assertThat(snapshot).hasValueSatisfying(value -> {
            assertThat(value.tenantId()).isEqualTo("tenant-1");
            assertThat(value.userId()).isEqualTo("user-1");
            assertThat(value.conversationId()).isEqualTo("conv-1");
            assertThat(value.sessionId()).isEqualTo("session-1");
            assertThat(value.trigger()).isEqualTo(MemoryFlushTrigger.IDLE_TIMEOUT);
            assertThat(value.turns()).extracting(MemoryTurnEvent::userText)
                    .containsExactly("hello", "remember java");
            assertThat(value.totalTokens()).isEqualTo(50);
            assertThat(value.from()).isEqualTo(firstAt);
            assertThat(value.to()).isEqualTo(firstAt.plusSeconds(5));
        });
        assertThat(adapter.state("session-1", "tenant-1")).isEmpty();
    }

    @Test
    void shouldMarkForceFlushRequiredWhenTokenLimitIsReached() {
        Instant completedAt = Instant.parse("2026-05-22T09:00:00Z");
        adapter.appendTurn(turn("msg-1", "first", "ok", 60, completedAt));

        var state = adapter.appendTurn(turn("msg-2", "second", "ok", 70, completedAt.plusSeconds(1)));

        assertThat(state.forceFlushRequired()).isTrue();
        assertThat(state.forceFlushTrigger()).isEqualTo(MemoryFlushTrigger.FORCE_TOKENS);
        assertThat(adapter.flushReady("session-1", "tenant-1", MemoryFlushTrigger.FORCE_TOKENS,
                completedAt.plusSeconds(1))).hasValueSatisfying(snapshot ->
                assertThat(snapshot.turns()).hasSize(2));
    }

    @Test
    void shouldListStatesOrderedByLastActivity() {
        adapter.appendTurn(turn("msg-1", "older", "ok", 1, Instant.parse("2026-05-22T08:00:00Z")));
        adapter.appendTurn(new MemoryTurnEvent(
                "tenant-1",
                "user-1",
                "conv-2",
                "session-2",
                "msg-2",
                "assistant-2",
                "newer",
                "ok",
                Instant.parse("2026-05-22T08:01:00Z"),
                1));

        assertThat(adapter.listStates(10)).extracting(state -> state.sessionId())
                .containsExactly("session-1", "session-2");
    }

    private MemoryTurnEvent turn(String messageId,
                                 String userText,
                                 String assistantText,
                                 int tokens,
                                 Instant completedAt) {
        return new MemoryTurnEvent(
                "tenant-1",
                "user-1",
                "conv-1",
                "session-1",
                messageId,
                "assistant-" + messageId,
                userText,
                assistantText,
                completedAt,
                tokens);
    }
}

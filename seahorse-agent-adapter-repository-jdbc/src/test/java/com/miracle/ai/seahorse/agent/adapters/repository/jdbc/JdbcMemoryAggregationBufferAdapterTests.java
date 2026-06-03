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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryAggregationBufferAdapterTests {

    private JdbcMemoryAggregationBufferAdapter adapter;
    private JdbcTemplate jdbcTemplate;
    private DriverManagerDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:memory-aggregation-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        new JdbcChatSchemaUpgrade(dataSource).upgrade();
        adapter = new JdbcMemoryAggregationBufferAdapter(dataSource, new ObjectMapper(),
                new MemoryAggregationPolicy(true, 40_000, 3, 120, 32, 86_400_000, false));
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Test
    void shouldAppendTurnsAndFlushIdleSnapshot() {
        Instant firstAt = Instant.parse("2026-05-22T08:00:00Z");
        adapter.appendTurn(turn("msg-1", "hello", "hi", 20, firstAt));
        var state = adapter.appendTurn(turn("msg-2", "remember java", "ok", 30, firstAt.plusSeconds(5)));

        assertThat(state.turnCount()).isEqualTo(2);
        assertThat(state.totalTokens()).isEqualTo(50);
        assertThat(state.forceFlushRequired()).isFalse();

        assertThat(adapter.flushReady("1", "session-1", "tenant-1", MemoryFlushTrigger.IDLE_TIMEOUT,
                firstAt.plusSeconds(20))).isEmpty();

        var snapshot = adapter.flushReady("1", "session-1", "tenant-1", MemoryFlushTrigger.IDLE_TIMEOUT,
                firstAt.plusSeconds(50));

        assertThat(snapshot).hasValueSatisfying(value -> {
            assertThat(value.tenantId()).isEqualTo("tenant-1");
            assertThat(value.userId()).isEqualTo("1");
            assertThat(value.conversationId()).isEqualTo("101");
            assertThat(value.sessionId()).isEqualTo("session-1");
            assertThat(value.trigger()).isEqualTo(MemoryFlushTrigger.IDLE_TIMEOUT);
            assertThat(value.turns()).extracting(MemoryTurnEvent::userText)
                    .containsExactly("hello", "remember java");
            assertThat(value.totalTokens()).isEqualTo(50);
            assertThat(value.from()).isEqualTo(firstAt);
            assertThat(value.to()).isEqualTo(firstAt.plusSeconds(5));
        });
        assertThat(adapter.state("1", "session-1", "tenant-1")).isEmpty();
    }

    @Test
    void shouldMarkForceFlushRequiredWhenTokenLimitIsReached() {
        Instant completedAt = Instant.parse("2026-05-22T09:00:00Z");
        adapter.appendTurn(turn("msg-1", "first", "ok", 60, completedAt));

        var state = adapter.appendTurn(turn("msg-2", "second", "ok", 70, completedAt.plusSeconds(1)));

        assertThat(state.forceFlushRequired()).isTrue();
        assertThat(state.forceFlushTrigger()).isEqualTo(MemoryFlushTrigger.FORCE_TOKENS);
        assertThat(adapter.flushReady("1", "session-1", "tenant-1", MemoryFlushTrigger.FORCE_TOKENS,
                completedAt.plusSeconds(1))).hasValueSatisfying(snapshot ->
                assertThat(snapshot.turns()).hasSize(2));
    }

    @Test
    void shouldTrackVersionForOptimisticConcurrency() {
        Instant completedAt = Instant.parse("2026-05-22T09:30:00Z");

        adapter.appendTurn(turn("msg-1", "first", "ok", 10, completedAt));
        Long insertedVersion = jdbcTemplate.queryForObject("""
                SELECT version
                FROM t_memory_aggregation_buffer
                WHERE tenant_id = ? AND session_id = ?
                """, Long.class, "tenant-1", "session-1");

        adapter.appendTurn(turn("msg-2", "second", "ok", 10, completedAt.plusSeconds(1)));
        Long updatedVersion = jdbcTemplate.queryForObject("""
                SELECT version
                FROM t_memory_aggregation_buffer
                WHERE tenant_id = ? AND session_id = ?
                """, Long.class, "tenant-1", "session-1");

        assertThat(insertedVersion).isEqualTo(1L);
        assertThat(updatedVersion).isEqualTo(2L);
    }

    @Test
    void shouldIsolateBuffersByUserWhenSessionIdMatches() {
        Instant completedAt = Instant.parse("2026-05-22T09:45:00Z");

        adapter.appendTurn(turnFor("1", "101", "session-1", "user-1-msg-1", "first", completedAt));
        adapter.appendTurn(turnFor("2", "202", "session-1", "user-2-msg-1", "other", completedAt.plusSeconds(1)));
        adapter.appendTurn(turnFor("1", "101", "session-1", "user-1-msg-2", "second", completedAt.plusSeconds(2)));

        assertThat(adapter.state("1", "session-1", "tenant-1"))
                .hasValueSatisfying(state -> {
                    assertThat(state.userId()).isEqualTo("1");
                    assertThat(state.conversationId()).isEqualTo("101");
                    assertThat(state.turnCount()).isEqualTo(2);
                });
        assertThat(adapter.state("2", "session-1", "tenant-1"))
                .hasValueSatisfying(state -> {
                    assertThat(state.userId()).isEqualTo("2");
                    assertThat(state.conversationId()).isEqualTo("202");
                    assertThat(state.turnCount()).isEqualTo(1);
                });
    }

    @Test
    void shouldPreserveConcurrentAppendsForSameSession() throws Exception {
        Instant completedAt = Instant.parse("2026-05-22T10:00:00Z");
        adapter.appendTurn(turn("seed", "seed", "ok", 1, completedAt));

        int appendCount = 24;
        ExecutorService executor = Executors.newFixedThreadPool(appendCount);
        CountDownLatch ready = new CountDownLatch(appendCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<?>> futures = new ArrayList<>();
        for (int i = 0; i < appendCount; i++) {
            int index = i;
            futures.add(executor.submit(() -> {
                ready.countDown();
                await(start);
                adapter.appendTurn(turn(
                        "concurrent-" + index,
                        "concurrent-" + index,
                        "ok",
                        1,
                        completedAt.plusMillis(index + 1L)));
            }));
        }

        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();
        for (Future<?> future : futures) {
            future.get(5, TimeUnit.SECONDS);
        }
        executor.shutdownNow();

        assertThat(adapter.state("1", "session-1", "tenant-1"))
                .hasValueSatisfying(state -> assertThat(state.turnCount()).isEqualTo(appendCount + 1));
        assertThat(adapter.flushReady("1", "session-1", "tenant-1", MemoryFlushTrigger.MANUAL, completedAt.plusSeconds(1)))
                .hasValueSatisfying(snapshot -> assertThat(snapshot.turns())
                        .extracting(MemoryTurnEvent::userMessageId)
                        .contains("seed", "concurrent-0", "concurrent-23")
                        .hasSize(appendCount + 1));
    }

    @Test
    void shouldPostponeIdleFlushWhenConcurrentAppendRefreshesActivity() {
        Instant firstAt = Instant.parse("2026-05-22T10:30:00Z");
        HookedJdbcMemoryAggregationBufferAdapter hookedAdapter = hookedAdapter();
        adapter = hookedAdapter;
        adapter.appendTurn(turn("msg-1", "older", "ok", 1, firstAt));
        AtomicBoolean appended = new AtomicBoolean();
        hookedAdapter.beforeDelete = () -> {
            if (appended.compareAndSet(false, true)) {
                adapter.appendTurn(turn("msg-2", "newer", "ok", 1, firstAt.plusSeconds(45)));
            }
        };

        assertThat(adapter.flushReady("1", "session-1", "tenant-1", MemoryFlushTrigger.IDLE_TIMEOUT,
                firstAt.plusSeconds(50))).isEmpty();

        assertThat(adapter.state("1", "session-1", "tenant-1"))
                .hasValueSatisfying(state -> {
                    assertThat(state.turnCount()).isEqualTo(2);
                    assertThat(state.lastActivityAt()).isEqualTo(firstAt.plusSeconds(45));
                });
    }

    @Test
    void shouldRetryForceFlushAfterConcurrentAppendConflict() {
        Instant firstAt = Instant.parse("2026-05-22T11:00:00Z");
        HookedJdbcMemoryAggregationBufferAdapter hookedAdapter = hookedAdapter();
        adapter = hookedAdapter;
        adapter.appendTurn(turn("msg-1", "first", "ok", 1, firstAt));
        adapter.appendTurn(turn("msg-2", "second", "ok", 1, firstAt.plusSeconds(1)));
        adapter.appendTurn(turn("msg-3", "third", "ok", 1, firstAt.plusSeconds(2)));
        AtomicBoolean appended = new AtomicBoolean();
        hookedAdapter.beforeDelete = () -> {
            if (appended.compareAndSet(false, true)) {
                adapter.appendTurn(turn("msg-4", "fourth", "ok", 1, firstAt.plusSeconds(3)));
            }
        };

        assertThat(adapter.flushReady("1", "session-1", "tenant-1", MemoryFlushTrigger.FORCE_TURNS,
                firstAt.plusSeconds(3)))
                .hasValueSatisfying(snapshot -> assertThat(snapshot.turns())
                        .extracting(MemoryTurnEvent::userMessageId)
                        .containsExactly("msg-1", "msg-2", "msg-3", "msg-4"));
        assertThat(adapter.state("1", "session-1", "tenant-1")).isEmpty();
    }

    @Test
    void shouldListStatesOrderedByLastActivity() {
        adapter.appendTurn(turn("msg-1", "older", "ok", 1, Instant.parse("2026-05-22T08:00:00Z")));
        adapter.appendTurn(new MemoryTurnEvent(
                "tenant-1",
                "1",
                "202",
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
        return turnFor("1", "101", "session-1", messageId, userText, assistantText, tokens, completedAt);
    }

    private MemoryTurnEvent turnFor(String userId,
                                    String conversationId,
                                    String sessionId,
                                    String messageId,
                                    String userText,
                                    Instant completedAt) {
        return turnFor(userId, conversationId, sessionId, messageId, userText, "ok", 1, completedAt);
    }

    private MemoryTurnEvent turnFor(String userId,
                                    String conversationId,
                                    String sessionId,
                                    String messageId,
                                    String userText,
                                    String assistantText,
                                    int tokens,
                                    Instant completedAt) {
        return new MemoryTurnEvent(
                "tenant-1",
                userId,
                conversationId,
                sessionId,
                messageId,
                "assistant-" + messageId,
                userText,
                assistantText,
                completedAt,
                tokens);
    }

    private HookedJdbcMemoryAggregationBufferAdapter hookedAdapter() {
        return new HookedJdbcMemoryAggregationBufferAdapter(dataSource, new ObjectMapper(),
                new MemoryAggregationPolicy(true, 40_000, 3, 120, 32, 86_400_000, false));
    }

    private void await(CountDownLatch latch) {
        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(ex);
        }
    }

    private static final class HookedJdbcMemoryAggregationBufferAdapter extends JdbcMemoryAggregationBufferAdapter {

        private Runnable beforeDelete = () -> {
        };

        private HookedJdbcMemoryAggregationBufferAdapter(DriverManagerDataSource dataSource,
                                                        ObjectMapper objectMapper,
                                                        MemoryAggregationPolicy policy) {
            super(dataSource, objectMapper, policy);
        }

        @Override
        protected void beforeFlushDeleteAttempt(String tenantId,
                                                String sessionId,
                                                long expectedVersion,
                                                MemoryFlushTrigger trigger) {
            beforeDelete.run();
        }
    }
}

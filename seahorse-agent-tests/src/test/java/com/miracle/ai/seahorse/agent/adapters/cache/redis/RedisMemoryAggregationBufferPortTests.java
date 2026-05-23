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

package com.miracle.ai.seahorse.agent.adapters.cache.redis;

import com.miracle.ai.seahorse.agent.kernel.application.memory.aggregation.MemoryAggregationPolicy;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferSnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferState;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.redisson.api.RBucket;
import org.redisson.api.RKeys;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisMemoryAggregationBufferPortTests {

    private static final Instant BASE_TIME = Instant.parse("2026-05-24T08:00:00Z");

    @Test
    void shouldAppendTurnsAndPersistAcrossInvocations() {
        FakeRedis fake = new FakeRedis();
        RedisMemoryAggregationBufferPort port = new RedisMemoryAggregationBufferPort(
                fake.client, policy(5, 30_000L));

        port.appendTurn(turn("conv-1", "user msg 1", "assistant msg 1", 12, BASE_TIME));
        MemoryBufferState afterSecond = port.appendTurn(
                turn("conv-1", "user msg 2", "assistant msg 2", 18, BASE_TIME.plusSeconds(5)));

        assertThat(afterSecond.turnCount()).isEqualTo(2);
        assertThat(afterSecond.totalTokens()).isEqualTo(30);
        assertThat(afterSecond.lastActivityAt()).isEqualTo(BASE_TIME.plusSeconds(5));
        assertThat(port.state("conv-1", "default"))
                .hasValueSatisfying(state -> assertThat(state.turnCount()).isEqualTo(2));
        assertThat(fake.lockNames).contains(
                "seahorse:agent:memory:aggregation:lock:default:conv-1");
    }

    @Test
    void shouldFlushReadyAndDeleteBufferAfterForceTurnsTrigger() {
        FakeRedis fake = new FakeRedis();
        RedisMemoryAggregationBufferPort port = new RedisMemoryAggregationBufferPort(
                fake.client, policy(2, 30_000L));

        port.appendTurn(turn("conv-1", "u1", "a1", 5, BASE_TIME));
        port.appendTurn(turn("conv-1", "u2", "a2", 7, BASE_TIME.plusSeconds(1)));

        Optional<MemoryBufferSnapshot> snapshot = port.flushReady(
                "conv-1", "default", MemoryFlushTrigger.FORCE_TURNS, BASE_TIME.plusSeconds(2));

        assertThat(snapshot).isPresent();
        assertThat(snapshot.get().turns()).hasSize(2);
        assertThat(snapshot.get().totalTokens()).isEqualTo(12);
        assertThat(snapshot.get().trigger()).isEqualTo(MemoryFlushTrigger.FORCE_TURNS);
        assertThat(port.state("conv-1", "default")).isEmpty();
    }

    @Test
    void shouldNotFlushBeforeIdleWindowElapses() {
        FakeRedis fake = new FakeRedis();
        RedisMemoryAggregationBufferPort port = new RedisMemoryAggregationBufferPort(
                fake.client, policy(10, 30_000L));

        port.appendTurn(turn("conv-1", "u1", "a1", 5, BASE_TIME));

        Optional<MemoryBufferSnapshot> early = port.flushReady(
                "conv-1", "default", MemoryFlushTrigger.IDLE_TIMEOUT, BASE_TIME.plusSeconds(10));
        Optional<MemoryBufferSnapshot> ready = port.flushReady(
                "conv-1", "default", MemoryFlushTrigger.IDLE_TIMEOUT, BASE_TIME.plusSeconds(35));

        assertThat(early).isEmpty();
        assertThat(ready).isPresent();
        assertThat(ready.get().trigger()).isEqualTo(MemoryFlushTrigger.IDLE_TIMEOUT);
    }

    @Test
    void shouldListStatesOrderedByLastActivity() {
        FakeRedis fake = new FakeRedis();
        RedisMemoryAggregationBufferPort port = new RedisMemoryAggregationBufferPort(
                fake.client, policy(10, 30_000L));

        port.appendTurn(turn("conv-1", "u1", "a1", 4, BASE_TIME.plusSeconds(2)));
        port.appendTurn(turn("conv-2", "u1", "a1", 4, BASE_TIME.plusSeconds(1)));

        List<MemoryBufferState> states = port.listStates(10);

        assertThat(states).extracting(MemoryBufferState::sessionId)
                .containsExactly("conv-2", "conv-1");
    }

    @Test
    void shouldDiscardCorruptDocumentInsteadOfFailing() {
        FakeRedis fake = new FakeRedis();
        String bufferKey = "seahorse:agent:memory:aggregation:buffer:default:conv-corrupt";
        fake.store.put(bufferKey, "this-is-not-json");
        RedisMemoryAggregationBufferPort port = new RedisMemoryAggregationBufferPort(
                fake.client, policy(10, 30_000L));

        Optional<MemoryBufferState> state = port.state("conv-corrupt", "default");

        assertThat(state).isEmpty();
        assertThat(fake.store).doesNotContainKey(bufferKey);
    }

    private MemoryAggregationPolicy policy(int maxTurns, long idleFlushMillis) {
        return new MemoryAggregationPolicy(true, idleFlushMillis, maxTurns, 1_000, 32, 60_000L, false, false);
    }

    private MemoryTurnEvent turn(String sessionId, String userText, String assistantText,
                                 int estimatedTokens, Instant completedAt) {
        return new MemoryTurnEvent(
                "default",
                "user-1",
                sessionId,
                sessionId,
                "msg-u-" + Math.abs(userText.hashCode()),
                "msg-a-" + Math.abs(assistantText.hashCode()),
                userText,
                assistantText,
                completedAt,
                estimatedTokens);
    }

    /**
     * Mockito-driven in-memory facade for the Redisson surface used by the buffer adapter.
     * Each invocation reads from / writes to a shared {@link #store}, while {@link #lockNames}
     * captures which lock keys the adapter requested so tests can verify per-session locking.
     */
    private static final class FakeRedis {

        private final Map<String, String> store = new LinkedHashMap<>();
        private final List<String> lockNames = new ArrayList<>();
        private final RedissonClient client = mock(RedissonClient.class);

        @SuppressWarnings({"unchecked", "rawtypes"})
        FakeRedis() {
            when(client.getBucket(anyString(), any(Codec.class))).thenAnswer((InvocationOnMock invocation) -> {
                String key = invocation.getArgument(0);
                return bucket(key);
            });
            when(client.getLock(anyString())).thenAnswer((InvocationOnMock invocation) -> {
                String name = invocation.getArgument(0);
                lockNames.add(name);
                return lock();
            });
            RKeys preparedKeys = keys();
            when(client.getKeys()).thenReturn(preparedKeys);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private RBucket bucket(String key) {
            RBucket bucket = mock(RBucket.class);
            when(bucket.get()).thenAnswer(invocation -> store.get(key));
            when(bucket.delete()).thenAnswer(invocation -> store.remove(key) != null);
            org.mockito.Mockito.doAnswer(invocation -> {
                store.put(key, invocation.getArgument(0));
                return null;
            }).when(bucket).set(any(), any(java.time.Duration.class));
            return bucket;
        }

        private RLock lock() {
            RLock lock = mock(RLock.class);
            try {
                when(lock.tryLock(anyLong(), anyLong(), any(TimeUnit.class))).thenReturn(true);
            } catch (InterruptedException ex) {
                throw new IllegalStateException(ex);
            }
            when(lock.isHeldByCurrentThread()).thenReturn(true);
            return lock;
        }

        private RKeys keys() {
            RKeys rkeys = mock(RKeys.class);
            when(rkeys.getKeysByPattern(anyString())).thenAnswer(invocation -> {
                String pattern = invocation.getArgument(0);
                Pattern compiled = Pattern.compile(pattern.replace("*", ".*"));
                List<String> matched = new ArrayList<>();
                for (String key : store.keySet()) {
                    if (compiled.matcher(key).matches()) {
                        matched.add(key);
                    }
                }
                return matched;
            });
            return rkeys;
        }
    }
}

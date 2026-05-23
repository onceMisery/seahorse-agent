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

import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RedisMemoryAggregationSchedulerPortTests {

    private static final String SCHEDULE_KEY = "seahorse:agent:memory:aggregation:scheduler";
    private static final Instant BASE_TIME = Instant.parse("2026-05-24T08:00:00Z");

    @Test
    void shouldStoreSessionWithEpochScoreOnScheduleIdleCheck() {
        FakeScoredSet fake = new FakeScoredSet();
        RedisMemoryAggregationSchedulerPort port = new RedisMemoryAggregationSchedulerPort(fake.client);

        port.scheduleIdleCheck("conv-1", "tenant-a", BASE_TIME.plusSeconds(10));

        assertThat(fake.entries).hasSize(1);
        assertThat(fake.entries.get("tenant-a::conv-1"))
                .isEqualTo((double) BASE_TIME.plusSeconds(10).toEpochMilli());
    }

    @Test
    void shouldDefaultTenantToDefaultWhenBlank() {
        FakeScoredSet fake = new FakeScoredSet();
        RedisMemoryAggregationSchedulerPort port = new RedisMemoryAggregationSchedulerPort(fake.client);

        port.scheduleIdleCheck("conv-1", "  ", BASE_TIME);

        assertThat(fake.entries).containsKey("default::conv-1");
    }

    @Test
    void shouldIgnoreScheduleCallsWithBlankSessionId() {
        FakeScoredSet fake = new FakeScoredSet();
        RedisMemoryAggregationSchedulerPort port = new RedisMemoryAggregationSchedulerPort(fake.client);

        port.scheduleIdleCheck("  ", "tenant-a", BASE_TIME);

        assertThat(fake.entries).isEmpty();
    }

    @Test
    void shouldUseNowWhenRunAtIsNull() {
        FakeScoredSet fake = new FakeScoredSet();
        RedisMemoryAggregationSchedulerPort port = new RedisMemoryAggregationSchedulerPort(fake.client);

        port.scheduleIdleCheck("conv-1", "tenant-a", null);

        assertThat(fake.entries).hasSize(1);
        assertThat(fake.entries.get("tenant-a::conv-1")).isPositive();
    }

    @Test
    void shouldUpdateScoreWhenReSchedulingSameSession() {
        FakeScoredSet fake = new FakeScoredSet();
        RedisMemoryAggregationSchedulerPort port = new RedisMemoryAggregationSchedulerPort(fake.client);

        port.scheduleIdleCheck("conv-1", "tenant-a", BASE_TIME);
        port.scheduleIdleCheck("conv-1", "tenant-a", BASE_TIME.plusSeconds(5));

        assertThat(fake.entries).hasSize(1);
        assertThat(fake.entries.get("tenant-a::conv-1"))
                .isEqualTo((double) BASE_TIME.plusSeconds(5).toEpochMilli());
    }

    @Test
    void shouldPollAndRemoveDueEntriesUpToLimit() {
        FakeScoredSet fake = new FakeScoredSet();
        RedisMemoryAggregationSchedulerPort port = new RedisMemoryAggregationSchedulerPort(fake.client);

        port.scheduleIdleCheck("conv-1", "tenant-a", BASE_TIME);
        port.scheduleIdleCheck("conv-2", "tenant-a", BASE_TIME.plusSeconds(1));
        port.scheduleIdleCheck("conv-future", "tenant-a", BASE_TIME.plusSeconds(60));

        List<RedisMemoryAggregationSchedulerPort.DueEntry> drained =
                port.pollDueEntries(BASE_TIME.plusSeconds(2), 5);

        assertThat(drained).extracting(RedisMemoryAggregationSchedulerPort.DueEntry::sessionId)
                .containsExactlyInAnyOrder("conv-1", "conv-2");
        assertThat(drained).extracting(RedisMemoryAggregationSchedulerPort.DueEntry::tenantId)
                .containsOnly("tenant-a");
        assertThat(fake.entries).containsOnlyKeys("tenant-a::conv-future");
    }

    @Test
    void shouldReturnEmptyListWhenNoEntriesAreDue() {
        FakeScoredSet fake = new FakeScoredSet();
        RedisMemoryAggregationSchedulerPort port = new RedisMemoryAggregationSchedulerPort(fake.client);

        port.scheduleIdleCheck("conv-1", "tenant-a", BASE_TIME.plusSeconds(60));

        List<RedisMemoryAggregationSchedulerPort.DueEntry> drained =
                port.pollDueEntries(BASE_TIME, 5);

        assertThat(drained).isEmpty();
        assertThat(fake.entries).containsKey("tenant-a::conv-1");
    }

    @Test
    void shouldDecodeDefaultTenantWhenSeparatorMissingFromMember() {
        FakeScoredSet fake = new FakeScoredSet();
        fake.entries.put("legacy-only-session", (double) BASE_TIME.toEpochMilli());
        RedisMemoryAggregationSchedulerPort port = new RedisMemoryAggregationSchedulerPort(fake.client);

        List<RedisMemoryAggregationSchedulerPort.DueEntry> drained =
                port.pollDueEntries(BASE_TIME, 5);

        assertThat(drained).singleElement().satisfies(entry -> {
            assertThat(entry.tenantId()).isEqualTo("default");
            assertThat(entry.sessionId()).isEqualTo("legacy-only-session");
        });
    }

    /**
     * Mockito-driven in-memory facade for the Redisson {@link RScoredSortedSet} surface used by
     * the scheduler. Records member→score pairs in {@link #entries} and routes Redisson method
     * calls to the subset matching the adapter's usage.
     */
    private static final class FakeScoredSet {

        private final Map<String, Double> entries = new LinkedHashMap<>();
        private final RedissonClient client = mock(RedissonClient.class);

        @SuppressWarnings({"unchecked", "rawtypes"})
        FakeScoredSet() {
            RScoredSortedSet set = scoredSet();
            when(client.getScoredSortedSet(eq(SCHEDULE_KEY), any(Codec.class))).thenReturn(set);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        private RScoredSortedSet scoredSet() {
            RScoredSortedSet set = mock(RScoredSortedSet.class);
            when(set.add(anyDouble(), anyString())).thenAnswer((InvocationOnMock invocation) -> {
                double score = invocation.getArgument(0);
                String member = invocation.getArgument(1);
                boolean isNew = !entries.containsKey(member);
                entries.put(member, score);
                return isNew;
            });
            when(set.remove(anyString())).thenAnswer((InvocationOnMock invocation) -> {
                String member = invocation.getArgument(0);
                return entries.remove(member) != null;
            });
            when(set.valueRangeReversed(anyDouble(), anyBoolean(), anyDouble(), anyBoolean(),
                    anyInt(), anyInt()))
                    .thenAnswer(this::collectByScore);
            return set;
        }

        private Collection<String> collectByScore(InvocationOnMock invocation) {
            double maxScore = invocation.getArgument(2);
            int limit = invocation.getArgument(5);
            List<String> ordered = new ArrayList<>();
            for (Map.Entry<String, Double> entry : entries.entrySet()) {
                if (entry.getValue() <= maxScore) {
                    ordered.add(entry.getKey());
                }
            }
            if (ordered.size() <= limit) {
                return ordered;
            }
            return ordered.subList(0, limit);
        }
    }
}

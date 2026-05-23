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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationSchedulerPort;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Redis-backed scheduler that records pending idle-flush checks in a Redisson scored set.
 *
 * <p>Each {@code (tenantId, sessionId)} pair is stored as a single member keyed by
 * {@code tenant:session}; the score is the desired execution time (epoch millis). Re-scheduling
 * an existing pair simply updates the score, mimicking the behaviour of an in-memory delayed
 * queue.
 *
 * <p>Background workers can call {@link #pollDueEntries(Instant, int)} to drain entries whose
 * scheduled time has passed and then ask the aggregation service to flush the corresponding
 * buffers; that polling loop lives outside the port contract so the scheduler stays focused on
 * storage.
 */
public class RedisMemoryAggregationSchedulerPort implements MemoryAggregationSchedulerPort {

    private static final String SCHEDULE_KEY = "seahorse:agent:memory:aggregation:scheduler";
    private static final String DEFAULT_TENANT_ID = "default";
    private static final String MEMBER_SEPARATOR = "::";

    private final RedissonClient redissonClient;

    public RedisMemoryAggregationSchedulerPort(RedissonClient redissonClient) {
        this.redissonClient = Objects.requireNonNull(redissonClient, "redissonClient must not be null");
    }

    @Override
    public void scheduleIdleCheck(String sessionId, String tenantId, Instant runAt) {
        String member = member(sessionId, tenantId);
        if (member.isEmpty()) {
            return;
        }
        Instant safeRunAt = Objects.requireNonNullElseGet(runAt, Instant::now);
        scheduleSet().add(safeRunAt.toEpochMilli(), member);
    }

    /**
     * Drain at most {@code limit} entries whose scheduled time is less than or equal to {@code now}.
     *
     * <p>The returned list preserves the scheduler's ordering: earliest-due first. Drained entries
     * are removed atomically so two workers polling concurrently will not double-flush the same
     * buffer.
     */
    public List<DueEntry> pollDueEntries(Instant now, int limit) {
        Instant safeNow = Objects.requireNonNullElseGet(now, Instant::now);
        int safeLimit = limit <= 0 ? 1 : limit;
        RScoredSortedSet<String> set = scheduleSet();
        Collection<String> dueMembers = set.valueRangeReversed(0, true, safeNow.toEpochMilli(), true, 0, safeLimit);
        if (dueMembers == null || dueMembers.isEmpty()) {
            return List.of();
        }
        List<DueEntry> entries = new ArrayList<>(dueMembers.size());
        for (String member : dueMembers) {
            if (set.remove(member)) {
                entries.add(parseMember(member));
            }
        }
        return List.copyOf(entries);
    }

    private RScoredSortedSet<String> scheduleSet() {
        return redissonClient.getScoredSortedSet(SCHEDULE_KEY, StringCodec.INSTANCE);
    }

    private static String member(String sessionId, String tenantId) {
        String safeTenant = normalize(tenantId, DEFAULT_TENANT_ID);
        String safeSession = normalize(sessionId, "");
        if (safeSession.isBlank()) {
            return "";
        }
        return safeTenant + MEMBER_SEPARATOR + safeSession;
    }

    private static DueEntry parseMember(String member) {
        int separator = member.indexOf(MEMBER_SEPARATOR);
        if (separator < 0) {
            return new DueEntry(DEFAULT_TENANT_ID, member);
        }
        String tenant = member.substring(0, separator);
        String session = member.substring(separator + MEMBER_SEPARATOR.length());
        return new DueEntry(tenant, session);
    }

    private static String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        if (normalized.isBlank()) {
            return Objects.requireNonNullElse(fallback, "");
        }
        return normalized;
    }

    public record DueEntry(String tenantId, String sessionId) {
    }
}

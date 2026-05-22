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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAggregationBufferPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferSnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryBufferState;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryFlushTrigger;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class JdbcMemoryAggregationBufferAdapter implements MemoryAggregationBufferPort {

    private static final String DEFAULT_TENANT_ID = "default";
    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_CAS_RETRIES = 64;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;
    private final MemoryAggregationPolicy policy;

    public JdbcMemoryAggregationBufferAdapter(DataSource dataSource,
                                              ObjectMapper objectMapper,
                                              MemoryAggregationPolicy policy) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.policy = Objects.requireNonNullElseGet(policy, MemoryAggregationPolicy::defaults);
    }

    @Override
    public MemoryBufferState appendTurn(MemoryTurnEvent event) {
        MemoryTurnEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
        for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
            Optional<StoredBuffer> existing = findBuffer(safeEvent.sessionId(), safeEvent.tenantId());
            if (existing.isPresent()) {
                StoredBuffer current = existing.get();
                StoredBuffer updated = current.append(safeEvent);
                if (update(updated, current.version())) {
                    return updated.state(policy);
                }
                continue;
            }
            StoredBuffer created = StoredBuffer.from(safeEvent);
            try {
                insert(created);
                return created.state(policy);
            } catch (DuplicateKeyException ex) {
                // Another writer created the session buffer between read and insert; retry and merge.
            }
        }
        throw new IllegalStateException("memory aggregation buffer append conflict did not converge");
    }

    @Override
    public Optional<MemoryBufferSnapshot> flushReady(String sessionId,
                                                     String tenantId,
                                                     MemoryFlushTrigger trigger,
                                                     Instant now) {
        MemoryFlushTrigger safeTrigger = Objects.requireNonNullElse(trigger, MemoryFlushTrigger.MANUAL);
        Instant safeNow = Objects.requireNonNullElseGet(now, Instant::now);
        for (int attempt = 0; attempt < MAX_CAS_RETRIES; attempt++) {
            Optional<StoredBuffer> existing = findBuffer(sessionId, tenantId);
            if (existing.isEmpty()) {
                return Optional.empty();
            }
            StoredBuffer buffer = existing.get();
            if (!isReady(buffer, safeTrigger, safeNow)) {
                return Optional.empty();
            }
            beforeFlushDeleteAttempt(buffer.tenantId(), buffer.sessionId(), buffer.version(), safeTrigger);
            int deleted = jdbcTemplate.update("""
                    DELETE FROM t_memory_aggregation_buffer
                    WHERE tenant_id = ? AND session_id = ? AND version = ?
                    """, buffer.tenantId(), buffer.sessionId(), buffer.version());
            if (deleted == 1) {
                return Optional.of(buffer.snapshot(safeTrigger));
            }
        }
        return Optional.empty();
    }

    protected void beforeFlushDeleteAttempt(String tenantId,
                                            String sessionId,
                                            long expectedVersion,
                                            MemoryFlushTrigger trigger) {
    }

    @Override
    public Optional<MemoryBufferState> state(String sessionId, String tenantId) {
        return findBuffer(sessionId, tenantId).map(buffer -> buffer.state(policy));
    }

    @Override
    public List<MemoryBufferState> listStates(int limit) {
        int safeLimit = limit > 0 ? limit : DEFAULT_LIMIT;
        return jdbcTemplate.query("""
                SELECT *
                FROM t_memory_aggregation_buffer
                ORDER BY last_activity_at ASC, create_time ASC
                LIMIT ?
                """, this::mapBuffer, safeLimit).stream()
                .map(buffer -> buffer.state(policy))
                .toList();
    }

    private Optional<StoredBuffer> findBuffer(String sessionId, String tenantId) {
        return jdbcTemplate.query("""
                SELECT *
                FROM t_memory_aggregation_buffer
                WHERE tenant_id = ? AND session_id = ?
                """, this::mapBuffer, safeTenantId(tenantId), normalize(sessionId, ""))
                .stream()
                .findFirst();
    }

    private void insert(StoredBuffer buffer) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO t_memory_aggregation_buffer
                    (id, tenant_id, user_id, conversation_id, session_id, turn_count, total_tokens, turns_json,
                     version, first_activity_at, last_activity_at, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                buffer.id(),
                buffer.tenantId(),
                buffer.userId(),
                buffer.conversationId(),
                buffer.sessionId(),
                buffer.turns().size(),
                buffer.totalTokens(),
                writeTurns(buffer.turns()),
                buffer.version(),
                JdbcMemorySupport.timestamp(buffer.firstActivityAt()),
                JdbcMemorySupport.timestamp(buffer.lastActivityAt()),
                JdbcMemorySupport.timestamp(now),
                JdbcMemorySupport.timestamp(now));
    }

    private boolean update(StoredBuffer buffer, long expectedVersion) {
        int updated = jdbcTemplate.update("""
                UPDATE t_memory_aggregation_buffer
                SET user_id = ?,
                    conversation_id = ?,
                    turn_count = ?,
                    total_tokens = ?,
                    turns_json = ?,
                    first_activity_at = ?,
                    last_activity_at = ?,
                    version = ?,
                    update_time = ?
                WHERE tenant_id = ? AND session_id = ? AND version = ?
                """,
                buffer.userId(),
                buffer.conversationId(),
                buffer.turns().size(),
                buffer.totalTokens(),
                writeTurns(buffer.turns()),
                JdbcMemorySupport.timestamp(buffer.firstActivityAt()),
                JdbcMemorySupport.timestamp(buffer.lastActivityAt()),
                buffer.version(),
                JdbcMemorySupport.timestamp(Instant.now()),
                buffer.tenantId(),
                buffer.sessionId(),
                expectedVersion);
        return updated == 1;
    }

    private boolean isReady(StoredBuffer buffer, MemoryFlushTrigger trigger, Instant now) {
        return switch (trigger) {
            case IDLE_TIMEOUT -> !now.isBefore(buffer.lastActivityAt().plusMillis(policy.idleFlushMillis()));
            case FORCE_TURNS -> buffer.turns().size() >= policy.maxTurns();
            case FORCE_TOKENS -> buffer.totalTokens() >= policy.maxTokens();
            case TOPIC_SHIFT, MANUAL, SESSION_CLOSED -> true;
        };
    }

    private StoredBuffer mapBuffer(ResultSet rs, int rowNum) throws SQLException {
        return new StoredBuffer(
                rs.getString("id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("conversation_id"),
                rs.getString("session_id"),
                readTurns(rs.getString("turns_json")),
                rs.getInt("total_tokens"),
                rs.getLong("version"),
                JdbcMemorySupport.instant(rs.getTimestamp("first_activity_at")),
                JdbcMemorySupport.instant(rs.getTimestamp("last_activity_at")));
    }

    private String writeTurns(List<MemoryTurnEvent> turns) {
        List<Map<String, Object>> values = Objects.requireNonNullElse(turns, List.<MemoryTurnEvent>of()).stream()
                .map(turn -> Map.<String, Object>of(
                        "tenantId", turn.tenantId(),
                        "userId", turn.userId(),
                        "conversationId", turn.conversationId(),
                        "sessionId", turn.sessionId(),
                        "userMessageId", turn.userMessageId(),
                        "assistantMessageId", turn.assistantMessageId(),
                        "userText", turn.userText(),
                        "assistantText", turn.assistantText(),
                        "completedAt", turn.completedAt().toString(),
                        "estimatedTokens", turn.estimatedTokens()))
                .toList();
        return JdbcMemorySupport.writeJson(objectMapper, Map.of("turns", values));
    }

    private List<MemoryTurnEvent> readTurns(String json) {
        Object values = JdbcMemorySupport.parseJson(objectMapper, json).get("turns");
        if (!(values instanceof List<?> list)) {
            return List.of();
        }
        List<MemoryTurnEvent> turns = new ArrayList<>();
        for (Object item : list) {
            if (item instanceof Map<?, ?> map) {
                turns.add(new MemoryTurnEvent(
                        text(map.get("tenantId")),
                        text(map.get("userId")),
                        text(map.get("conversationId")),
                        text(map.get("sessionId")),
                        text(map.get("userMessageId")),
                        text(map.get("assistantMessageId")),
                        text(map.get("userText")),
                        text(map.get("assistantText")),
                        instant(text(map.get("completedAt"))),
                        integer(map.get("estimatedTokens"))));
            }
        }
        return turns;
    }

    private Instant instant(String value) {
        if (!JdbcMemorySupport.hasText(value)) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (RuntimeException ignored) {
            return Instant.now();
        }
    }

    private int integer(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(value.toString());
            } catch (NumberFormatException ignored) {
                return 0;
            }
        }
        return 0;
    }

    private String text(Object value) {
        return Objects.requireNonNullElse(value, "").toString();
    }

    private String safeTenantId(String tenantId) {
        return normalize(tenantId, DEFAULT_TENANT_ID);
    }

    private String normalize(String value, String fallback) {
        String normalized = Objects.requireNonNullElse(value, fallback).trim();
        return normalized.isBlank() ? Objects.requireNonNullElse(fallback, "") : normalized;
    }

    private record StoredBuffer(String id,
                                String tenantId,
                                String userId,
                                String conversationId,
                                String sessionId,
                                List<MemoryTurnEvent> turns,
                                int totalTokens,
                                long version,
                                Instant firstActivityAt,
                                Instant lastActivityAt) {

        static StoredBuffer from(MemoryTurnEvent event) {
            return new StoredBuffer(
                    "memory-buffer-" + JdbcMemorySupport.nextId(),
                    event.tenantId(),
                    event.userId(),
                    event.conversationId(),
                    event.sessionId(),
                    List.of(event),
                    event.estimatedTokens(),
                    1L,
                    event.completedAt(),
                    event.completedAt());
        }

        StoredBuffer append(MemoryTurnEvent event) {
            List<MemoryTurnEvent> updatedTurns = new ArrayList<>(turns);
            updatedTurns.add(event);
            return new StoredBuffer(
                    id,
                    tenantId,
                    event.userId(),
                    event.conversationId(),
                    sessionId,
                    updatedTurns,
                    totalTokens + event.estimatedTokens(),
                    version + 1,
                    firstActivityAt,
                    event.completedAt());
        }

        MemoryBufferState state(MemoryAggregationPolicy policy) {
            boolean forceTokens = totalTokens >= policy.maxTokens();
            boolean forceTurns = turns.size() >= policy.maxTurns();
            MemoryFlushTrigger trigger = forceTokens
                    ? MemoryFlushTrigger.FORCE_TOKENS
                    : forceTurns ? MemoryFlushTrigger.FORCE_TURNS : null;
            return new MemoryBufferState(
                    tenantId,
                    userId,
                    conversationId,
                    sessionId,
                    turns.size(),
                    totalTokens,
                    lastActivityAt,
                    forceTokens || forceTurns,
                    trigger);
        }

        MemoryBufferSnapshot snapshot(MemoryFlushTrigger trigger) {
            return new MemoryBufferSnapshot(
                    "memory-snapshot-" + JdbcMemorySupport.nextId(),
                    tenantId,
                    userId,
                    conversationId,
                    sessionId,
                    trigger,
                    turns,
                    totalTokens,
                    firstActivityAt,
                    lastActivityAt);
        }
    }
}

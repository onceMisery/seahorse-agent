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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTraceRecorder;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JdbcMemoryTraceRecorderAdapter implements MemoryTraceRecorder {

    private static final int DEFAULT_LIMIT = 100;
    private static final int MAX_LIMIT = 1000;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryTraceRecorderAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void record(MemoryTraceEvent event) {
        MemoryTraceEvent safeEvent = Objects.requireNonNullElseGet(event,
                () -> new MemoryTraceEvent("memory", "event", MemoryTraceEvent.STATUS_IGNORED, "", "default", "",
                        Map.of(), Instant.now()));
        long id = JdbcMemorySupport.nextId();
        String traceId = JdbcMemorySupport.hasText(safeEvent.traceId()) ? safeEvent.traceId() : String.valueOf(id);
        jdbcTemplate.update("""
                INSERT INTO t_memory_trace_event
                    (id, trace_id, tenant_id, user_id, conversation_id, session_id, component, event_type,
                     status, subject_id, subject_type, details_json, occurred_at, create_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, ?)
                """,
                id,
                traceId,
                safeEvent.tenantId(),
                safeEvent.userId(),
                safeEvent.conversationId(),
                safeEvent.sessionId(),
                safeEvent.component(),
                safeEvent.eventType(),
                safeEvent.status(),
                safeEvent.subjectId(),
                safeEvent.subjectType(),
                JdbcMemorySupport.writeJson(objectMapper, safeEvent.details()),
                JdbcMemorySupport.timestamp(safeEvent.occurredAt()),
                JdbcMemorySupport.timestamp(Instant.now()));
    }

    @Override
    public List<MemoryTraceEvent> listRecent(int limit) {
        int safeLimit = limit > 0 ? Math.min(limit, MAX_LIMIT) : DEFAULT_LIMIT;
        return jdbcTemplate.query("""
                SELECT trace_id, tenant_id, user_id, conversation_id, session_id, component, event_type,
                       status, subject_id, subject_type, details_json, occurred_at
                FROM t_memory_trace_event
                ORDER BY occurred_at DESC, create_time DESC, id DESC
                LIMIT ?
                """, this::mapEvent, safeLimit);
    }

    private MemoryTraceEvent mapEvent(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryTraceEvent(
                rs.getString("trace_id"),
                rs.getString("tenant_id"),
                rs.getString("user_id"),
                rs.getString("conversation_id"),
                rs.getString("session_id"),
                rs.getString("component"),
                rs.getString("event_type"),
                rs.getString("status"),
                rs.getString("subject_id"),
                rs.getString("subject_type"),
                JdbcMemorySupport.parseJson(objectMapper, rs.getString("details_json")),
                JdbcMemorySupport.instant(rs.getTimestamp("occurred_at")));
    }
}

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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventEnvelope;
import com.miracle.ai.seahorse.agent.kernel.domain.stream.StreamEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunEventBufferPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentRunEventBufferAdapter implements AgentRunEventBufferPort {

    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_run_event_buffer (run_id, event_seq, event_id, event_type, step_id, payload, created_at)
            VALUES (?, ?, ?, ?, ?, ?::jsonb, ?)
            """;

    private static final String SQL_GET_AFTER = """
            SELECT event_id, event_seq, event_type, run_id, step_id, payload, created_at
            FROM sa_agent_run_event_buffer
            WHERE run_id = ? AND event_seq > ?
            ORDER BY event_seq ASC
            """;

    private static final String SQL_GET_LATEST_SEQ = """
            SELECT MAX(event_seq) FROM sa_agent_run_event_buffer WHERE run_id = ?
            """;

    private static final String SQL_EXPIRE = """
            DELETE FROM sa_agent_run_event_buffer WHERE run_id = ?
            """;

    private static final String SQL_EXPIRE_OLD = """
            DELETE FROM sa_agent_run_event_buffer WHERE created_at < ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcAgentRunEventBufferAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void append(String runId, StreamEventEnvelope event) {
        Objects.requireNonNull(runId, "runId must not be null");
        Objects.requireNonNull(event, "event must not be null");
        String payloadJson = serializePayload(event.typedPayload());
        jdbcTemplate.update(SQL_INSERT,
                runId,
                event.eventSeq(),
                event.eventId(),
                event.eventType().value(),
                event.stepId(),
                payloadJson,
                Timestamp.from(event.timestamp()));
    }

    @Override
    public List<StreamEventEnvelope> getAfter(String runId, long afterSeq) {
        if (runId == null || runId.isBlank()) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_GET_AFTER, this::mapEnvelope, runId.trim(), afterSeq);
    }

    @Override
    public Optional<Long> getLatestSeq(String runId) {
        if (runId == null || runId.isBlank()) {
            return Optional.empty();
        }
        Long result = jdbcTemplate.queryForObject(SQL_GET_LATEST_SEQ, Long.class, runId.trim());
        return Optional.ofNullable(result);
    }

    @Override
    public void expire(String runId) {
        if (runId == null || runId.isBlank()) {
            return;
        }
        jdbcTemplate.update(SQL_EXPIRE, runId.trim());
    }

    public void expireOlderThan(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff must not be null");
        jdbcTemplate.update(SQL_EXPIRE_OLD, Timestamp.from(cutoff));
    }

    private StreamEventEnvelope mapEnvelope(ResultSet rs, int rowNum) throws SQLException {
        String eventTypeValue = rs.getString("event_type");
        StreamEventType eventType = resolveEventType(eventTypeValue);
        String payloadJson = rs.getString("payload");
        Object payload = deserializePayload(payloadJson);
        Timestamp createdAt = rs.getTimestamp("created_at");
        return new StreamEventEnvelope(
                rs.getString("event_id"),
                rs.getLong("event_seq"),
                eventType,
                rs.getString("run_id"),
                rs.getString("step_id"),
                createdAt != null ? createdAt.toInstant() : Instant.now(),
                payload);
    }

    private StreamEventType resolveEventType(String value) {
        for (StreamEventType type : StreamEventType.values()) {
            if (type.value().equals(value)) {
                return type;
            }
        }
        return StreamEventType.MESSAGE;
    }

    private String serializePayload(Object payload) {
        if (payload == null) {
            return "{}";
        }
        if (payload instanceof String s) {
            return s;
        }
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private Object deserializePayload(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return objectMapper.readValue(json, Object.class);
        } catch (JsonProcessingException e) {
            return json;
        }
    }
}

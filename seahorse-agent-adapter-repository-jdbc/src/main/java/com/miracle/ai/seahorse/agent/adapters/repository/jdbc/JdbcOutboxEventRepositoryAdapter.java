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

import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEvent;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEvent.OutboxEventDelivery;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventStatus;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 基于 {@code t_outbox_event} 的 JDBC Outbox 仓储 adapter。
 *
 * <p>该实现复用旧表结构，确保 seahorse-agent 原生可靠发布不会引入额外迁移成本。
 */
public class JdbcOutboxEventRepositoryAdapter implements OutboxEventRepositoryPort {

    private static final int DEFAULT_BATCH_SIZE = 50;
    private static final String SQL_INSERT_EVENT = """
            INSERT INTO t_outbox_event
            (id, topic, message_key, event_type, payload_json, status, retry_count,
             next_retry_time, last_error, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String SQL_CLAIM_PENDING = """
            SELECT id, topic, message_key, event_type, payload_json, status, retry_count,
                   next_retry_time, last_error
            FROM t_outbox_event
            WHERE deleted = 0
              AND status IN (?, ?)
              AND (next_retry_time IS NULL OR next_retry_time <= ?)
            ORDER BY create_time ASC
            LIMIT ?
            """;
    private static final String SQL_MARK_SENDING = """
            UPDATE t_outbox_event
            SET status = ?, update_time = ?
            WHERE id = ? AND deleted = 0 AND status IN (?, ?)
            """;
    private static final String SQL_MARK_SENT = """
            UPDATE t_outbox_event
            SET status = ?, last_error = NULL, update_time = ?
            WHERE id = ? AND deleted = 0
            """;
    private static final String SQL_MARK_FAILED = """
            UPDATE t_outbox_event
            SET status = ?, retry_count = ?, next_retry_time = ?, last_error = ?, update_time = ?
            WHERE id = ? AND deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcOutboxEventRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void append(OutboxEvent event) {
        OutboxEvent safeEvent = Objects.requireNonNull(event, "event must not be null");
        OutboxEventDelivery delivery = safeEvent.delivery();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_INSERT_EVENT,
                resolveId(safeEvent.id()),
                safeEvent.topic(),
                safeEvent.messageKey(),
                safeEvent.eventType(),
                safeEvent.payloadJson(),
                delivery.status(),
                delivery.retryCount(),
                resolveNextRetryTime(delivery),
                delivery.lastError(),
                now,
                now);
    }

    @Override
    public List<OutboxEvent> claimPending(int batchSize, Instant now) {
        int actualBatchSize = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        Timestamp actualNow = Timestamp.from(now == null ? Instant.now() : now);
        return jdbcTemplate.query(SQL_CLAIM_PENDING, this::mapEvent,
                OutboxEventStatus.NEW, OutboxEventStatus.FAILED, actualNow, actualBatchSize);
    }

    @Override
    public boolean markSending(String eventId) {
        if (!hasText(eventId)) {
            return false;
        }
        int updated = jdbcTemplate.update(SQL_MARK_SENDING,
                OutboxEventStatus.SENDING,
                Timestamp.from(Instant.now()),
                eventId,
                OutboxEventStatus.NEW,
                OutboxEventStatus.FAILED);
        return updated > 0;
    }

    @Override
    public void markSent(String eventId) {
        if (!hasText(eventId)) {
            return;
        }
        jdbcTemplate.update(SQL_MARK_SENT, OutboxEventStatus.SENT, Timestamp.from(Instant.now()), eventId);
    }

    @Override
    public void markFailed(String eventId, int retryCount, Instant nextRetryTime, String lastError) {
        if (!hasText(eventId)) {
            return;
        }
        Instant safeNextRetryTime = nextRetryTime == null ? Instant.now() : nextRetryTime;
        jdbcTemplate.update(SQL_MARK_FAILED,
                OutboxEventStatus.FAILED,
                Math.max(0, retryCount),
                Timestamp.from(safeNextRetryTime),
                lastError,
                Timestamp.from(Instant.now()),
                eventId);
    }

    private OutboxEvent mapEvent(ResultSet resultSet, int rowNum) throws SQLException {
        OutboxEventDelivery delivery = OutboxEventDelivery.of(
                resultSet.getString("status"),
                resultSet.getInt("retry_count"),
                toInstant(resultSet.getTimestamp("next_retry_time")),
                resultSet.getString("last_error"));
        return OutboxEvent.builder()
                .id(resultSet.getString("id"))
                .topic(resultSet.getString("topic"))
                .messageKey(resultSet.getString("message_key"))
                .eventType(resultSet.getString("event_type"))
                .payloadJson(resultSet.getString("payload_json"))
                .delivery(delivery)
                .build();
    }

    private Instant toInstant(Timestamp timestamp) {
        if (timestamp == null) {
            return Instant.now();
        }
        return timestamp.toInstant();
    }

    private Timestamp resolveNextRetryTime(OutboxEventDelivery delivery) {
        if (OutboxEventStatus.NEW.equals(delivery.status()) && delivery.retryCount() == 0) {
            return null;
        }
        return Timestamp.from(delivery.nextRetryTime());
    }

    private String resolveId(String id) {
        if (hasText(id)) {
            return id;
        }
        long millis = System.currentTimeMillis();
        int suffix = ThreadLocalRandom.current().nextInt(100_000, 1_000_000);
        return Long.toString(millis, 36) + suffix;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

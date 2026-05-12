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
import com.miracle.ai.seahorse.agent.ports.outbound.mq.OutboxEventStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcOutboxEventRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcOutboxEventRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:outbox-event;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcOutboxEventRepositoryAdapter(dataSource);
    }

    @Test
    void shouldAppendAndClaimPendingEvent() {
        adapter.append(sampleEvent());

        List<OutboxEvent> events = adapter.claimPending(10, Instant.now());

        assertThat(events).hasSize(1);
        assertThat(events.get(0).topic()).isEqualTo("topic-a");
        assertThat(events.get(0).messageKey()).isEqualTo("key-a");
        assertThat(events.get(0).delivery().status()).isEqualTo(OutboxEventStatus.NEW);
    }

    @Test
    void shouldMoveEventAcrossDeliveryStates() {
        adapter.append(sampleEvent());
        String eventId = adapter.claimPending(10, Instant.now()).get(0).id();

        boolean marked = adapter.markSending(eventId);
        adapter.markSent(eventId);

        assertThat(marked).isTrue();
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM t_outbox_event WHERE id = ?", String.class, eventId);
        assertThat(status).isEqualTo(OutboxEventStatus.SENT);
    }

    @Test
    void shouldMarkFailedWithRetryTime() {
        adapter.append(sampleEvent());
        String eventId = adapter.claimPending(10, Instant.now()).get(0).id();
        Instant retryTime = Instant.now().plusSeconds(30);

        adapter.markFailed(eventId, 2, retryTime, "send failed");

        Integer retryCount = jdbcTemplate.queryForObject(
                "SELECT retry_count FROM t_outbox_event WHERE id = ?", Integer.class, eventId);
        String status = jdbcTemplate.queryForObject(
                "SELECT status FROM t_outbox_event WHERE id = ?", String.class, eventId);
        assertThat(retryCount).isEqualTo(2);
        assertThat(status).isEqualTo(OutboxEventStatus.FAILED);
    }

    private OutboxEvent sampleEvent() {
        return OutboxEvent.builder()
                .topic("topic-a")
                .messageKey("key-a")
                .eventType("SamplePayload")
                .payloadJson("{\"key\":\"key-a\",\"eventType\":\"SamplePayload\",\"payloadJson\":\"{}\"}")
                .build();
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_outbox_event");
        jdbcTemplate.execute("""
                CREATE TABLE t_outbox_event (
                    id VARCHAR(20) PRIMARY KEY,
                    topic VARCHAR(128) NOT NULL,
                    message_key VARCHAR(128) NOT NULL,
                    event_type VARCHAR(128) NOT NULL,
                    payload_json TEXT NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    retry_count INTEGER NOT NULL DEFAULT 0,
                    next_retry_time TIMESTAMP,
                    last_error TEXT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }
}

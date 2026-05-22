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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryTraceRecorderAdapterTests {

    private JdbcMemoryTraceRecorderAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:memory-trace-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new JdbcChatSchemaUpgrade(dataSource).upgrade();
        adapter = new JdbcMemoryTraceRecorderAdapter(dataSource, new ObjectMapper());
    }

    @Test
    void shouldRecordAndListRecentTraceEvents() {
        adapter.record(event("trace-1", "ingest", Instant.parse("2026-05-22T08:00:00Z")));
        adapter.record(event("trace-2", "review", Instant.parse("2026-05-22T09:00:00Z")));

        var events = adapter.listRecent(10);

        assertThat(events).hasSize(2);
        assertThat(events).extracting(MemoryTraceEvent::traceId)
                .containsExactly("trace-2", "trace-1");
        assertThat(events.get(0).details()).containsEntry("source", "jdbc-test");
    }

    private MemoryTraceEvent event(String traceId, String eventType, Instant occurredAt) {
        return new MemoryTraceEvent(
                traceId,
                "tenant-1",
                "user-1",
                "conv-1",
                "session-1",
                "memory-review",
                eventType,
                MemoryTraceEvent.STATUS_SUCCESS,
                traceId + "-subject",
                "memory",
                Map.of("source", "jdbc-test"),
                occurredAt);
    }
}

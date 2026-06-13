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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTurnEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "seahorse.postgres.contract", matches = "true")
class JdbcMemoryAggregationBufferAdapterPostgresContractTests {

    @Test
    void shouldPersistTurnsAsPostgresJsonb() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getProperty("seahorse.postgres.url", "jdbc:postgresql://localhost:5432/seahorse"),
                System.getProperty("seahorse.postgres.username", "seahorse"),
                System.getProperty("seahorse.postgres.password", "seahorse"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcMemoryAggregationBufferAdapter adapter = new JdbcMemoryAggregationBufferAdapter(
                dataSource,
                new ObjectMapper(),
                new MemoryAggregationPolicy(true, 40_000, 10, 2_000, 32, 86_400_000, false));

        String userId = Long.toString(800_000_000_000_000_000L + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000L));
        String conversationId = Long.toString(801_000_000_000_000_000L + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 1_000_000L));
        String sessionId = "postgres-jsonb-contract-" + UUID.randomUUID();
        try {
            adapter.appendTurn(new MemoryTurnEvent(
                    "default",
                    userId,
                    conversationId,
                    sessionId,
                    "user-message-1",
                    "assistant-message-1",
                    "remember that I prefer concise Chinese answers",
                    "ok",
                    Instant.parse("2026-06-13T00:00:00Z"),
                    16));

            String jsonType = jdbcTemplate.queryForObject("""
                    SELECT jsonb_typeof(turns_json)
                    FROM t_memory_aggregation_buffer
                    WHERE tenant_id = ? AND user_id = ? AND session_id = ?
                    """, String.class, "default", Long.parseLong(userId), sessionId);
            String userText = jdbcTemplate.queryForObject("""
                    SELECT turns_json -> 'turns' -> 0 ->> 'userText'
                    FROM t_memory_aggregation_buffer
                    WHERE tenant_id = ? AND user_id = ? AND session_id = ?
                    """, String.class, "default", Long.parseLong(userId), sessionId);

            assertThat(jsonType).isEqualTo("object");
            assertThat(userText).isEqualTo("remember that I prefer concise Chinese answers");
        } finally {
            jdbcTemplate.update("""
                    DELETE FROM t_memory_aggregation_buffer
                    WHERE tenant_id = ? AND user_id = ? AND session_id = ?
                    """, "default", Long.parseLong(userId), sessionId);
        }
    }
}

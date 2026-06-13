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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDocument;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@EnabledIfSystemProperty(named = "seahorse.postgres.contract", matches = "true")
class JdbcMemoryKeywordIndexRepositoryAdapterPostgresContractTests {

    @Test
    void shouldPersistMetadataAsPostgresJsonb() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                System.getProperty("seahorse.postgres.url", "jdbc:postgresql://localhost:5432/seahorse"),
                System.getProperty("seahorse.postgres.username", "seahorse"),
                System.getProperty("seahorse.postgres.password", "seahorse"));
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcMemoryKeywordIndexRepositoryAdapter adapter =
                new JdbcMemoryKeywordIndexRepositoryAdapter(dataSource, new ObjectMapper());

        String userId = Long.toString(810_000_000_000_000_000L
                + Math.abs(UUID.randomUUID().getMostSignificantBits() % 1_000_000L));
        String memoryId = "keyword-jsonb-contract-" + UUID.randomUUID();
        try {
            adapter.upsert(new MemoryDerivedIndexDocument(
                    memoryId,
                    userId,
                    "default",
                    "SHORT_TERM",
                    "PREFERENCE",
                    "I prefer concise Chinese answers.",
                    Map.of("profileSlot", "preferences.response_style"),
                    Instant.parse("2026-06-13T00:00:00Z")));

            String jsonType = jdbcTemplate.queryForObject("""
                    SELECT jsonb_typeof(metadata_json)
                    FROM t_memory_keyword_index
                    WHERE user_id = ? AND tenant_id = ? AND memory_id = ?
                    """, String.class, Long.parseLong(userId), "default", memoryId);
            String profileSlot = jdbcTemplate.queryForObject("""
                    SELECT metadata_json ->> 'profileSlot'
                    FROM t_memory_keyword_index
                    WHERE user_id = ? AND tenant_id = ? AND memory_id = ?
                    """, String.class, Long.parseLong(userId), "default", memoryId);

            assertThat(jsonType).isEqualTo("object");
            assertThat(profileSlot).isEqualTo("preferences.response_style");
        } finally {
            jdbcTemplate.update("""
                    DELETE FROM t_memory_keyword_index
                    WHERE user_id = ? AND tenant_id = ? AND memory_id = ?
                    """, Long.parseLong(userId), "default", memoryId);
        }
    }
}

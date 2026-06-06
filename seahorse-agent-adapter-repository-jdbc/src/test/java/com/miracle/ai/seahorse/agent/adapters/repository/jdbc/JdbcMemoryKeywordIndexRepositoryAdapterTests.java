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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDeleteCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryDerivedIndexDocument;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryKeywordSearchPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryKeywordIndexRepositoryAdapterTests {

    private JdbcMemoryKeywordIndexRepositoryAdapter adapter;
    private JdbcMemoryKeywordSearchRepositoryAdapter searchAdapter;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:memory-keyword-index-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        new JdbcChatSchemaUpgrade(dataSource).upgrade();
        jdbcTemplate = new JdbcTemplate(dataSource);
        ObjectMapper objectMapper = new ObjectMapper();
        adapter = new JdbcMemoryKeywordIndexRepositoryAdapter(dataSource, objectMapper);
        searchAdapter = new JdbcMemoryKeywordSearchRepositoryAdapter(dataSource, objectMapper);
    }

    @Test
    void shouldUpsertMemoryKeywordDocument() {
        adapter.upsert(new MemoryDerivedIndexDocument(
                "memory-1",
                "user-1",
                "tenant-1",
                "LONG_TERM",
                "PROJECT_FACT",
                "OceanBase backup window is 03:00.",
                Map.of("semanticKey", "project:oceanbase"),
                Instant.parse("2026-05-22T08:00:00Z")));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT memory_id, user_id, tenant_id, layer_name, memory_type, content, status, deleted
                FROM t_memory_keyword_index
                WHERE memory_id = 'memory-1'
                """);

        assertThat(row)
                .containsEntry("MEMORY_ID", "memory-1")
                .containsEntry("USER_ID", JdbcMemorySupport.toLongId("user-1"))
                .containsEntry("TENANT_ID", "tenant-1")
                .containsEntry("LAYER_NAME", "LONG_TERM")
                .containsEntry("MEMORY_TYPE", "PROJECT_FACT")
                .containsEntry("CONTENT", "OceanBase backup window is 03:00.")
                .containsEntry("STATUS", "ACTIVE");
        assertThat(((Number) row.get("DELETED")).intValue()).isZero();
    }

    @Test
    void shouldReactivateDeletedRowOnUpsert() {
        adapter.upsert(new MemoryDerivedIndexDocument(
                "memory-1",
                "user-1",
                "tenant-1",
                "SHORT_TERM",
                "FACT",
                "Old content",
                Map.of(),
                Instant.now()));
        adapter.delete(new MemoryDerivedIndexDeleteCommand("memory-1", "user-1", "tenant-1"));

        adapter.upsert(new MemoryDerivedIndexDocument(
                "memory-1",
                "user-1",
                "tenant-1",
                "SHORT_TERM",
                "FACT",
                "New content",
                Map.of(),
                Instant.now()));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT content, status, deleted
                FROM t_memory_keyword_index
                WHERE memory_id = 'memory-1'
                """);
        assertThat(row).containsEntry("CONTENT", "New content")
                .containsEntry("STATUS", "ACTIVE");
        assertThat(((Number) row.get("DELETED")).intValue()).isZero();
    }

    @Test
    void shouldSoftDeleteMemoryKeywordDocument() {
        adapter.upsert(new MemoryDerivedIndexDocument(
                "memory-1",
                "user-1",
                "tenant-1",
                "SEMANTIC",
                "PROFILE",
                "{\"project\":\"OceanBase\"}",
                Map.of(),
                Instant.now()));

        adapter.delete(new MemoryDerivedIndexDeleteCommand("memory-1", "user-1", "tenant-1"));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT status, deleted
                FROM t_memory_keyword_index
                WHERE memory_id = 'memory-1'
                """);
        assertThat(row).containsEntry("STATUS", "DELETED");
        assertThat(((Number) row.get("DELETED")).intValue()).isEqualTo(1);
    }

    @Test
    void shouldSearchKeywordIndexDocumentWithoutLayeredSourceTables() {
        adapter.upsert(new MemoryDerivedIndexDocument(
                "memory-1",
                "user-1",
                "tenant-1",
                "LONG_TERM",
                "PROJECT_FACT",
                "Pulsar PIP-459 rollback plan",
                Map.of("semanticKey", "project:pulsar"),
                Instant.now()));

        var hits = searchAdapter.search("user-1", "tenant-1", "PIP-459", 10);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).memoryId()).isEqualTo("memory-1");
        assertThat(hits.get(0).layer()).isEqualTo("LONG_TERM");
        assertThat(hits.get(0).metadata())
                .containsEntry("type", "PROJECT_FACT")
                .containsEntry("status", "ACTIVE");
    }

    @Test
    void shouldRankKeywordIndexHitsBySparseTermCoverage() {
        Instant indexedAt = Instant.parse("2026-05-22T08:00:00Z");
        adapter.upsert(new MemoryDerivedIndexDocument(
                "memory-pip",
                "user-1",
                "tenant-1",
                "LONG_TERM",
                "PROJECT_FACT",
                "Pulsar PIP-459 rollback plan",
                Map.of("semanticKey", "project:pulsar"),
                indexedAt));
        adapter.upsert(new MemoryDerivedIndexDocument(
                "memory-seatunnel",
                "user-1",
                "tenant-1",
                "LONG_TERM",
                "PROJECT_FACT",
                "SeaTunnel connector rollout note",
                Map.of("semanticKey", "project:seatunnel"),
                indexedAt.plusSeconds(30)));

        var hits = searchAdapter.search("user-1", "tenant-1", "Pulsar PIP-459 SeaTunnel", 10);

        assertThat(hits).extracting(MemoryKeywordSearchPort.MemoryKeywordHit::memoryId)
                .containsExactly("memory-pip", "memory-seatunnel");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }

    @Test
    void shouldRankRareExactKeywordAboveFrequentCommonTerm() {
        Instant indexedAt = Instant.parse("2026-05-22T08:00:00Z");
        adapter.upsert(new MemoryDerivedIndexDocument(
                "z-rare-pip",
                "user-1",
                "tenant-1",
                "LONG_TERM",
                "PROJECT_FACT",
                "Pulsar PIP-459 compatibility rollback note",
                Map.of("semanticKey", "project:pulsar-pip"),
                indexedAt));
        adapter.upsert(new MemoryDerivedIndexDocument(
                "a-common-seatunnel",
                "user-1",
                "tenant-1",
                "LONG_TERM",
                "PROJECT_FACT",
                "SeaTunnel connector deployment note",
                Map.of("semanticKey", "project:seatunnel-a"),
                indexedAt.plusSeconds(10)));
        adapter.upsert(new MemoryDerivedIndexDocument(
                "b-common-seatunnel",
                "user-1",
                "tenant-1",
                "LONG_TERM",
                "PROJECT_FACT",
                "SeaTunnel CDC connector rollout",
                Map.of("semanticKey", "project:seatunnel-b"),
                indexedAt.plusSeconds(20)));
        adapter.upsert(new MemoryDerivedIndexDocument(
                "c-common-seatunnel",
                "user-1",
                "tenant-1",
                "LONG_TERM",
                "PROJECT_FACT",
                "SeaTunnel pipeline monitoring checklist",
                Map.of("semanticKey", "project:seatunnel-c"),
                indexedAt.plusSeconds(30)));

        var hits = searchAdapter.search("user-1", "tenant-1", "PIP-459 SeaTunnel", 3);

        assertThat(hits).extracting(MemoryKeywordSearchPort.MemoryKeywordHit::memoryId)
                .startsWith("z-rare-pip");
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }
}

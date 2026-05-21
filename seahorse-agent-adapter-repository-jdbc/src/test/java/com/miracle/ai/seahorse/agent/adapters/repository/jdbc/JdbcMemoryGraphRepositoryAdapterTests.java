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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecallRequest;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryTrack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryGraphRepositoryAdapterTests {

    private JdbcMemoryGraphRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:memory-graph;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new JdbcChatSchemaUpgrade(dataSource).upgrade();
        ObjectMapper objectMapper = new ObjectMapper();
        new JdbcMemoryAliasRepositoryAdapter(dataSource, objectMapper)
                .upsertAlias(com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCommand.of(
                        "user-1",
                        "tenant-1",
                        "OB",
                        "entity-oceanbase",
                        "OceanBase",
                        "PROJECT"));
        adapter = new JdbcMemoryGraphRepositoryAdapter(dataSource, objectMapper);
    }

    @Test
    void shouldRecallOneHopMemoriesThroughAliasCanonicalEntity() {
        adapter.upsert(new MemoryDerivedIndexDocument(
                "memory-1",
                "user-1",
                "tenant-1",
                "long_term",
                "PROJECT_FACT",
                "OceanBase backup window is 03:00.",
                Map.of(
                        "canonicalEntityId", "entity-oceanbase",
                        "canonicalName", "OceanBase",
                        "relatedEntityIds", java.util.List.of("entity-backup"),
                        "relationType", "MENTIONS"),
                Instant.now()));

        var hits = adapter.recallNeighborhood(new MemoryRecallRequest(
                "user-1",
                "tenant-1",
                "OB backup plan",
                Set.of(MemoryTrack.EPISODIC),
                5,
                Map.of()), 1);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).memoryId()).isEqualTo("memory-1");
        assertThat(hits.get(0).channel()).isEqualTo("graph");
        assertThat(hits.get(0).metadata())
                .containsEntry("canonicalEntityId", "entity-oceanbase")
                .containsEntry("graphMatch", "alias");
    }

    @Test
    void shouldStopRecallAfterGraphDelete() {
        adapter.upsert(new MemoryDerivedIndexDocument(
                "memory-1",
                "user-1",
                "tenant-1",
                "long_term",
                "PROJECT_FACT",
                "OceanBase backup window is 03:00.",
                Map.of("canonicalEntityId", "entity-oceanbase", "canonicalName", "OceanBase"),
                Instant.now()));

        adapter.delete(new MemoryDerivedIndexDeleteCommand("memory-1", "user-1", "tenant-1"));

        assertThat(adapter.recallNeighborhood(new MemoryRecallRequest(
                "user-1",
                "tenant-1",
                "OB",
                Set.of(MemoryTrack.EPISODIC),
                5,
                Map.of()), 1)).isEmpty();
    }
}

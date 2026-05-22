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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCandidate;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class JdbcMemoryAliasRepositoryAdapterTests {

    private JdbcMemoryAliasRepositoryAdapter adapter;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:memory-alias-" + UUID.randomUUID() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        new JdbcChatSchemaUpgrade(dataSource).upgrade();
        jdbcTemplate = new JdbcTemplate(dataSource);
        adapter = new JdbcMemoryAliasRepositoryAdapter(dataSource, new ObjectMapper());
    }

    @Test
    void shouldResolveAliasWithTrimAndCaseNormalization() {
        adapter.upsertAlias(new MemoryAliasCommand(
                "user-1",
                "tenant-1",
                " K8s ",
                "entity-kubernetes",
                "Kubernetes",
                "TECH",
                0.95D,
                "dictionary",
                List.of("memory-1"),
                Map.of("source", "test")));

        assertThat(adapter.resolveAlias("user-1", "tenant-1", "k8s"))
                .hasValueSatisfying(resolution -> {
                    assertThat(resolution.canonicalEntityId()).isEqualTo("entity-kubernetes");
                    assertThat(resolution.canonicalName()).isEqualTo("Kubernetes");
                    assertThat(resolution.normalizedAlias()).isEqualTo("k8s");
                });

        adapter.upsertAlias(new MemoryAliasCommand(
                "user-1",
                "tenant-1",
                "k8S",
                "entity-kubernetes-v2",
                "Kubernetes Platform",
                "TECH",
                0.96D,
                "dictionary",
                List.of("memory-2"),
                Map.of()));

        assertThat(adapter.resolveAlias("user-1", "tenant-1", " K8S "))
                .map(MemoryAliasResolution::canonicalEntityId)
                .hasValue("entity-kubernetes-v2");
        assertThat(jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM t_memory_entity_alias
                WHERE user_id = 'user-1'
                  AND tenant_id = 'tenant-1'
                  AND normalized_alias = 'k8s'
                  AND deleted = 0
                """, Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldFindActiveMergeCandidatesWithTenantScopeLimitAndStableTieBreaks() {
        upsert("user-1", "tenant-1", "Stale High", "entity-high", 0.95D);
        upsert("user-1", "tenant-1", "Fresh Low", "entity-low", 0.20D);
        upsert("user-2", "tenant-2", "Other tenant", "entity-other-tenant", 1.00D);
        upsert("user-1", "tenant-1", "Inactive", "entity-inactive", 1.00D);
        upsert("user-1", "tenant-1", "Deleted", "entity-deleted", 1.00D);
        jdbcTemplate.update("""
                UPDATE t_memory_entity_alias
                SET update_time = ?, create_time = ?
                WHERE canonical_entity_id = 'entity-high'
                """, JdbcMemorySupport.timestamp(Instant.parse("2026-05-21T10:15:30Z")),
                JdbcMemorySupport.timestamp(Instant.parse("2026-05-21T10:15:30Z")));
        jdbcTemplate.update("""
                UPDATE t_memory_entity_alias
                SET update_time = ?, create_time = ?
                WHERE canonical_entity_id = 'entity-low'
                """, JdbcMemorySupport.timestamp(Instant.parse("2026-05-21T11:15:30Z")),
                JdbcMemorySupport.timestamp(Instant.parse("2026-05-21T11:15:30Z")));
        jdbcTemplate.update("UPDATE t_memory_entity_alias SET status = 'INACTIVE' WHERE canonical_entity_id = 'entity-inactive'");
        jdbcTemplate.update("UPDATE t_memory_entity_alias SET deleted = 1 WHERE canonical_entity_id = 'entity-deleted'");

        List<MemoryAliasCandidate> candidates = adapter.findMergeCandidates("user-1", "tenant-1", 2);

        assertThat(candidates)
                .extracting(MemoryAliasCandidate::aliasText)
                .containsExactly("Fresh Low", "Stale High");
        assertThat(candidates)
                .extracting(MemoryAliasCandidate::canonicalEntityId)
                .containsExactly("entity-low", "entity-high");
        assertThat(candidates.get(0).sourceMemoryIds()).containsExactly("memory-low-1", "memory-low-2");
        assertThat(candidates)
                .extracting(MemoryAliasCandidate::userId, MemoryAliasCandidate::tenantId)
                .containsExactly(tuple("user-1", "tenant-1"), tuple("user-1", "tenant-1"));
    }

    @Test
    void shouldFindActiveMergeCandidatesAcrossAllUsersAndTenantsWhenScopeIsOmitted() {
        upsert("user-1", "tenant-1", "Scope A", "entity-a", 0.65D);
        upsert("user-2", "tenant-2", "Scope B", "entity-b", 0.75D);
        upsert("user-3", "tenant-3", "Scope C", "entity-c", 0.85D);
        upsert("user-4", "tenant-4", "Inactive", "entity-inactive", 1.00D);
        upsert("user-5", "tenant-5", "Deleted", "entity-deleted", 1.00D);
        jdbcTemplate.update("""
                UPDATE t_memory_entity_alias
                SET update_time = ?, create_time = ?
                WHERE canonical_entity_id = 'entity-a'
                """, JdbcMemorySupport.timestamp(Instant.parse("2026-05-21T10:15:30Z")),
                JdbcMemorySupport.timestamp(Instant.parse("2026-05-21T10:15:30Z")));
        jdbcTemplate.update("""
                UPDATE t_memory_entity_alias
                SET update_time = ?, create_time = ?
                WHERE canonical_entity_id = 'entity-b'
                """, JdbcMemorySupport.timestamp(Instant.parse("2026-05-21T11:15:30Z")),
                JdbcMemorySupport.timestamp(Instant.parse("2026-05-21T11:15:30Z")));
        jdbcTemplate.update("""
                UPDATE t_memory_entity_alias
                SET update_time = ?, create_time = ?
                WHERE canonical_entity_id = 'entity-c'
                """, JdbcMemorySupport.timestamp(Instant.parse("2026-05-21T12:15:30Z")),
                JdbcMemorySupport.timestamp(Instant.parse("2026-05-21T12:15:30Z")));
        jdbcTemplate.update("UPDATE t_memory_entity_alias SET status = 'INACTIVE' WHERE canonical_entity_id = 'entity-inactive'");
        jdbcTemplate.update("UPDATE t_memory_entity_alias SET deleted = 1 WHERE canonical_entity_id = 'entity-deleted'");

        List<MemoryAliasCandidate> candidates = adapter.findMergeCandidates(10);

        assertThat(candidates)
                .extracting(MemoryAliasCandidate::userId, MemoryAliasCandidate::tenantId, MemoryAliasCandidate::aliasText)
                .containsExactly(
                        tuple("user-3", "tenant-3", "Scope C"),
                        tuple("user-2", "tenant-2", "Scope B"),
                        tuple("user-1", "tenant-1", "Scope A"));
        assertThat(candidates)
                .extracting(MemoryAliasCandidate::canonicalEntityId)
                .containsExactly("entity-c", "entity-b", "entity-a");
    }

    private void upsert(String userId, String tenantId, String aliasText, String canonicalEntityId, double confidenceLevel) {
        List<String> sourceMemoryIds = "entity-low".equals(canonicalEntityId)
                ? List.of("memory-low-1", "memory-low-2")
                : List.of();
        adapter.upsertAlias(new MemoryAliasCommand(
                userId,
                tenantId,
                aliasText,
                canonicalEntityId,
                aliasText,
                "ENTITY",
                confidenceLevel,
                "maintenance-test",
                sourceMemoryIds,
                Map.of()));
    }
}

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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasCommand;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryAliasResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryAliasRepositoryAdapterTests {

    private JdbcMemoryAliasRepositoryAdapter adapter;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:memory-alias;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
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
}

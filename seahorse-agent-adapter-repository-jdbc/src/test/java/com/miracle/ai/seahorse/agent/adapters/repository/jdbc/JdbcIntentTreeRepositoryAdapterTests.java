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
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodePayload;
import com.miracle.ai.seahorse.agent.ports.outbound.intent.IntentNodeTree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcIntentTreeRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcIntentTreeRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:intent-tree;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcIntentTreeRepositoryAdapter(dataSource, new ObjectMapper());
    }

    @Test
    void shouldCreateUpdateEnableAndDeleteIntentNode() {
        IntentNodePayload payload = new IntentNodePayload();
        payload.setKbId("1");
        payload.setIntentCode("domain");
        payload.setName("Domain");
        payload.setLevel(0);
        payload.setExamples(List.of("hello"));

        String id = adapter.create(payload, "100");
        IntentNodePayload update = new IntentNodePayload();
        update.setName("Updated");
        update.setEnabled(0);

        boolean updated = adapter.update(id, update, "100");
        IntentNodeTree record = adapter.findById(id).orElseThrow();
        boolean enabled = adapter.updateEnabled(List.of(id), 1, "100");
        boolean deleted = adapter.deleteByIds(List.of(id));

        assertThat(updated).isTrue();
        assertThat(record.getName()).isEqualTo("Updated");
        assertThat(record.getExamples()).isEqualTo("[\"hello\"]");
        assertThat(enabled).isTrue();
        assertThat(deleted).isTrue();
        assertThat(adapter.findById(id)).isEmpty();
    }

    @Test
    void shouldListNodesBySortOrder() {
        insertNode("2", "child", "root", 2);
        insertNode("1", "root", null, 1);

        List<IntentNodeTree> nodes = adapter.listActiveNodes();

        assertThat(nodes).extracting(IntentNodeTree::getIntentCode).containsExactly("root", "child");
    }

    private void insertNode(String id, String intentCode, String parentCode, int sortOrder) {
        jdbcTemplate.update("""
                INSERT INTO t_intent_node
                (id, intent_code, name, level, parent_code, sort_order, enabled, kind, create_time, update_time, deleted)
                VALUES (?, ?, ?, 0, ?, ?, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, id, intentCode, intentCode, parentCode, sortOrder);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_intent_node");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_knowledge_base");
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_base (
                    id BIGINT PRIMARY KEY,
                    collection_name VARCHAR(128),
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_intent_node (
                    id BIGINT PRIMARY KEY,
                    kb_id BIGINT,
                    intent_code VARCHAR(64) NOT NULL,
                    name VARCHAR(64) NOT NULL,
                    level SMALLINT NOT NULL,
                    parent_code VARCHAR(64),
                    description VARCHAR(512),
                    examples TEXT,
                    collection_name VARCHAR(128),
                    top_k INTEGER,
                    mcp_tool_id VARCHAR(128),
                    kind SMALLINT DEFAULT 0,
                    prompt_snippet TEXT,
                    prompt_template TEXT,
                    param_prompt_template TEXT,
                    sort_order INTEGER DEFAULT 0,
                    enabled SMALLINT DEFAULT 1,
                    create_by BIGINT,
                    update_by BIGINT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.update("""
                INSERT INTO t_knowledge_base (id, collection_name, deleted)
                VALUES (1, 'collection_1', 0)
                """);
    }
}

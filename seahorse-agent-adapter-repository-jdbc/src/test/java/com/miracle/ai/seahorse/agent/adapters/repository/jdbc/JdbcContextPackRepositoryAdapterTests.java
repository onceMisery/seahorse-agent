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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextItemSourceType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextPack;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcContextPackRepositoryAdapterTests {

    private static final Instant CREATED_AT = Instant.parse("2026-05-24T00:00:00Z");

    @Test
    void shouldSaveFindAndListContextPackItems() {
        DriverManagerDataSource dataSource = dataSource("context-pack");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createContextPackSchema(jdbcTemplate);
        JdbcContextPackRepositoryAdapter adapter = new JdbcContextPackRepositoryAdapter(dataSource);

        ContextPack pack = pack(List.of(item("item-1", "doc-1", 0.91), item("item-2", "mem-1", 0.81)));

        adapter.save(pack);

        Optional<ContextPack> found = adapter.findById("context-pack-1");
        List<ContextItem> items = adapter.listItems("context-pack-1");

        assertThat(found).contains(pack);
        assertThat(items).containsExactlyElementsOf(pack.items());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT item_count FROM sa_context_pack WHERE context_pack_id = ?",
                Integer.class,
                "context-pack-1")).isEqualTo(2);
    }

    @Test
    void shouldPersistContextItemProvenanceAndAclColumns() {
        DriverManagerDataSource dataSource = dataSource("context-item-columns");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createContextPackSchema(jdbcTemplate);
        JdbcContextPackRepositoryAdapter adapter = new JdbcContextPackRepositoryAdapter(dataSource);

        adapter.save(pack(List.of(item("item-1", "doc-1", 0.91))));

        Map<String, Object> row = jdbcTemplate.queryForMap("""
                SELECT source_type, source_id, content, summary, score, confidence, sensitivity, acl_decision_id,
                       citation_json, estimated_tokens, created_at
                FROM sa_context_item
                WHERE item_id = ?
                """, "item-1");

        assertThat(row.get("SOURCE_TYPE")).isEqualTo(ContextItemSourceType.RAG_CHUNK.name());
        assertThat(row.get("SOURCE_ID")).isEqualTo("doc-1");
        assertThat(row.get("CONTENT")).isEqualTo("content for doc-1");
        assertThat(row.get("SUMMARY")).isEqualTo("summary for doc-1");
        assertThat(row.get("SCORE")).isEqualTo(0.91);
        assertThat(row.get("CONFIDENCE")).isEqualTo(0.88);
        assertThat(row.get("SENSITIVITY")).isEqualTo(ContextSensitivity.INTERNAL.name());
        assertThat(row.get("ACL_DECISION_ID")).isEqualTo("decision-doc-1");
        assertThat(row.get("CITATION_JSON")).isEqualTo("{\"sourceId\":\"doc-1\"}");
        assertThat(row.get("ESTIMATED_TOKENS")).isEqualTo(64);
        assertThat(row.get("CREATED_AT")).isNotNull();
    }

    private DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }

    static void createContextPackSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE sa_context_pack (
                    context_pack_id VARCHAR(64) PRIMARY KEY,
                    run_id VARCHAR(64) NOT NULL,
                    agent_id VARCHAR(64),
                    version_id VARCHAR(64),
                    tenant_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    task_goal VARCHAR(1000) NOT NULL,
                    budget_tokens INT NOT NULL,
                    item_count INT NOT NULL,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_context_item (
                    item_id VARCHAR(64) PRIMARY KEY,
                    context_pack_id VARCHAR(64) NOT NULL,
                    source_type VARCHAR(32) NOT NULL,
                    source_id VARCHAR(128) NOT NULL,
                    content CLOB NOT NULL,
                    summary VARCHAR(1000),
                    score DOUBLE,
                    confidence DOUBLE,
                    sensitivity VARCHAR(32) NOT NULL,
                    acl_decision_id VARCHAR(64) NOT NULL,
                    citation_json CLOB NOT NULL,
                    estimated_tokens INT NOT NULL,
                    expires_at TIMESTAMP,
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX idx_sa_context_item_pack
                ON sa_context_item(context_pack_id)
                """);
    }

    private static ContextPack pack(List<ContextItem> items) {
        return new ContextPack(
                "context-pack-1",
                "run-1",
                "agent-1",
                "version-1",
                "tenant-1",
                "user-1",
                "answer customer question",
                300,
                items,
                CREATED_AT);
    }

    private static ContextItem item(String itemId, String sourceId, double score) {
        return new ContextItem(
                itemId,
                "context-pack-1",
                ContextItemSourceType.RAG_CHUNK,
                sourceId,
                "content for " + sourceId,
                "summary for " + sourceId,
                score,
                0.88,
                ContextSensitivity.INTERNAL,
                "decision-" + sourceId,
                "{\"sourceId\":\"" + sourceId + "\"}",
                64,
                null,
                CREATED_AT);
    }
}

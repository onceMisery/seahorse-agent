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
import com.miracle.ai.seahorse.agent.ports.outbound.cache.KeyValueCachePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcQueryTermExpansionAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private TestCachePort cachePort;
    private JdbcQueryTermExpansionAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:query-term-expansion;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        cachePort = new TestCachePort();
        adapter = new JdbcQueryTermExpansionAdapter(dataSource, cachePort, new ObjectMapper(),
                JdbcQueryTermExpansionAdapter.Options.defaults());
    }

    @Test
    void shouldExpandEnabledMappingsByMatchTypeAndPriority() {
        insert("1", "LLM", "大语言模型", 1, 20, true);
        insert("2", "LLM", "Large Language Model", 1, 5, true);
        insert("3", "RAG", "检索增强生成", 2, 10, true);
        insert("4", "禁用词", "不应出现", 2, 1, false);
        insertDeleted("5", "企业", "不应出现", 2, 1);

        var expansions = adapter.expand("请解释 LLM 在企业 RAG 平台中的作用");

        assertThat(expansions).containsOnlyKeys("LLM", "RAG");
        assertThat(expansions.get("LLM")).containsExactly("Large Language Model", "大语言模型");
        assertThat(expansions.get("RAG")).containsExactly("检索增强生成");
    }

    @Test
    void shouldSkipRegexMappingsWhenRegexIsDisabled() {
        insert("1", "RAG-[0-9]+", "正则扩展词", 3, 1, true);

        var expansions = adapter.expand("RAG-2026");

        assertThat(expansions).isEmpty();
    }

    @Test
    void shouldLoadRulesFromCacheAfterFirstQuery() {
        insert("1", "Agent", "智能体", 1, 1, true);

        assertThat(adapter.expand("Agent 平台")).containsKey("Agent");
        jdbcTemplate.update("DELETE FROM t_query_term_mapping");

        assertThat(adapter.expand("Agent 平台")).containsKey("Agent");
        assertThat(cachePort.setCount()).isEqualTo(1);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_query_term_mapping");
        jdbcTemplate.execute("""
                CREATE TABLE t_query_term_mapping (
                    id VARCHAR(20) PRIMARY KEY,
                    source_term VARCHAR(128) NOT NULL,
                    target_term VARCHAR(128) NOT NULL,
                    match_type SMALLINT DEFAULT 1,
                    priority INTEGER DEFAULT 100,
                    enabled SMALLINT DEFAULT 1,
                    remark VARCHAR(255),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }

    private void insert(String id, String sourceTerm, String targetTerm, int matchType, int priority, boolean enabled) {
        jdbcTemplate.update("""
                INSERT INTO t_query_term_mapping
                (id, source_term, target_term, match_type, priority, enabled, create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, id, sourceTerm, targetTerm, matchType, priority, enabled ? 1 : 0);
    }

    private void insertDeleted(String id, String sourceTerm, String targetTerm, int matchType, int priority) {
        jdbcTemplate.update("""
                INSERT INTO t_query_term_mapping
                (id, source_term, target_term, match_type, priority, enabled, create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 1)
                """, id, sourceTerm, targetTerm, matchType, priority);
    }

    private static final class TestCachePort implements KeyValueCachePort {

        private final AtomicInteger setCount = new AtomicInteger();
        private String value;

        @Override
        public Optional<String> get(String key) {
            return Optional.ofNullable(value);
        }

        @Override
        public void set(String key, String value, Duration ttl) {
            this.value = value;
            setCount.incrementAndGet();
        }

        @Override
        public boolean delete(String key) {
            value = null;
            return true;
        }

        int setCount() {
            return setCount.get();
        }
    }
}

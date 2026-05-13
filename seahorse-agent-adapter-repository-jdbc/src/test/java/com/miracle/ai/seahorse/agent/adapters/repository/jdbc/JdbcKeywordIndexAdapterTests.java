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

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcKeywordIndexAdapterTests {

    @Test
    void shouldBuildPostgresFtsUpdateSqlForChunkIds() {
        JdbcKeywordIndexAdapter adapter = new JdbcKeywordIndexAdapter(dataSource());

        String sql = adapter.indexSql(2);

        assertThat(sql).contains("SET search_text = to_tsvector('simple', COALESCE(content, ''))");
        assertThat(sql).contains("WHERE kb_id = ? AND doc_id = ? AND id IN (?, ?)");
    }

    @Test
    void shouldBuildDeleteSqlByDocument() {
        JdbcKeywordIndexAdapter adapter = new JdbcKeywordIndexAdapter(dataSource());

        assertThat(adapter.deleteSql())
                .contains("SET search_text = NULL")
                .contains("WHERE kb_id = ? AND doc_id = ?");
    }

    @Test
    void shouldSkipWhenSearchTextColumnMissing() {
        DriverManagerDataSource dataSource = dataSource();
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE t_knowledge_chunk (
                    id VARCHAR(32) PRIMARY KEY,
                    kb_id VARCHAR(32),
                    doc_id VARCHAR(32),
                    content VARCHAR(512)
                )
                """);
        JdbcKeywordIndexAdapter adapter = new JdbcKeywordIndexAdapter(dataSource);

        adapter.indexDocumentChunks("kb-1", "doc-1", java.util.List.of());
        adapter.deleteDocumentChunks("kb-1", "doc-1");
    }

    private DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource("jdbc:h2:mem:keyword-index-" + System.nanoTime()
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
    }
}

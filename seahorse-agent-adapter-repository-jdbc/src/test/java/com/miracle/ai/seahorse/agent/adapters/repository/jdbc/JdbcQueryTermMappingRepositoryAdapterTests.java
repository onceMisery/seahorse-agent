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

import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingPage;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingPayload;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcQueryTermMappingRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcQueryTermMappingRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:query-term-mapping;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcQueryTermMappingRepositoryAdapter(dataSource);
    }

    @Test
    void shouldCreatePageUpdateAndDeleteMapping() {
        String id = adapter.create(new QueryTermMappingPayload("LLM", "大模型", 1, 5, true, "remark"));
        boolean updated = adapter.update(id, new QueryTermMappingPayload(null, "模型", null, 1, false, null));

        QueryTermMappingPage page = adapter.page(1, 10, "模型");
        boolean deleted = adapter.delete(id);

        assertThat(updated).isTrue();
        assertThat(page.total()).isEqualTo(1);
        assertThat(page.records().get(0).getTargetTerm()).isEqualTo("模型");
        assertThat(page.records().get(0).getEnabled()).isFalse();
        assertThat(deleted).isTrue();
        assertThat(adapter.findById(id)).isEmpty();
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
}

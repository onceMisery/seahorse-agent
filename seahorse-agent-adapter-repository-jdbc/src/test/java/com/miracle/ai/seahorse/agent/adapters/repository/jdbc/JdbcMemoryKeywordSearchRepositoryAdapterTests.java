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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import javax.sql.DataSource;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryKeywordSearchRepositoryAdapterTests {

    @Test
    void shouldCastJsonColumnsBeforeLoweringFallbackKeywordSearch() {
        DriverManagerDataSource delegate = new DriverManagerDataSource(
                "jdbc:h2:mem:memory-keyword-search-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        RecordingDataSource dataSource = new RecordingDataSource(delegate);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createFallbackSchema(jdbcTemplate);

        new JdbcMemoryKeywordSearchRepositoryAdapter(dataSource, new ObjectMapper())
                .search("user-1", "default", "profile", 10);

        String sql = String.join("\n", dataSource.sql());
        assertThat(sql)
                .contains("LOWER(CAST(metadata_json AS VARCHAR)) LIKE ?")
                .contains("LOWER(CAST(tags AS VARCHAR)) LIKE ?")
                .contains("LOWER(CAST(value_json AS VARCHAR)) LIKE ?");
        assertThat(sql)
                .doesNotContain("LOWER(metadata_json) LIKE ?")
                .doesNotContain("LOWER(tags) LIKE ?")
                .doesNotContain("LOWER(value_json) LIKE ?");
    }

    private static void createFallbackSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
                CREATE TABLE t_short_term_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
                    tenant_id VARCHAR(64) DEFAULT 'default',
                    memory_type VARCHAR(32),
                    content TEXT,
                    metadata_json TEXT,
                    status VARCHAR(32) DEFAULT 'ACTIVE',
                    generation_id VARCHAR(64),
                    last_referenced_at TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_long_term_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
                    tenant_id VARCHAR(64) DEFAULT 'default',
                    memory_category VARCHAR(32),
                    title VARCHAR(128),
                    content TEXT,
                    tags TEXT,
                    importance_score DOUBLE,
                    status VARCHAR(32) DEFAULT 'ACTIVE',
                    generation_id VARCHAR(64),
                    last_referenced_at TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_semantic_memory (
                    id VARCHAR(64) PRIMARY KEY,
                    user_id VARCHAR(64),
                    tenant_id VARCHAR(64) DEFAULT 'default',
                    semantic_key VARCHAR(128),
                    semantic_type VARCHAR(32),
                    value_json TEXT,
                    status VARCHAR(32) DEFAULT 'ACTIVE',
                    generation_id VARCHAR(64),
                    last_referenced_at TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }

    private static final class RecordingDataSource extends AbstractDataSource {

        private final DataSource delegate;
        private final List<String> sql = new CopyOnWriteArrayList<>();

        private RecordingDataSource(DataSource delegate) {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException {
            return record(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException {
            return record(delegate.getConnection(username, password));
        }

        private List<String> sql() {
            return sql;
        }

        private Connection record(Connection connection) {
            return (Connection) Proxy.newProxyInstance(
                    connection.getClass().getClassLoader(),
                    new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        if (args != null
                                && args.length > 0
                                && args[0] instanceof String statementSql
                                && method.getName().startsWith("prepare")) {
                            sql.add(statementSql);
                        }
                        try {
                            return method.invoke(connection, args);
                        } catch (InvocationTargetException ex) {
                            throw ex.getTargetException();
                        }
                    });
        }
    }
}

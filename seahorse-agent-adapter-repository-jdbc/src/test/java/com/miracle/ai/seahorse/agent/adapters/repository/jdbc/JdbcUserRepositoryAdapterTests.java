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

import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserPage;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserUpdateValues;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcUserRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcUserRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:user-repository;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcUserRepositoryAdapter(dataSource);
    }

    @Test
    void shouldFindPageCreateUpdateAndDeleteUsers() {
        insertUser("1", "alice", "admin");

        assertThat(adapter.findByUsername("alice")).isPresent();
        UserPage page = adapter.page(1, 10, "ali");
        String id = adapter.create(new UserCreateValues("bob", "pw", "user", null));
        boolean updated = adapter.update(id, new UserUpdateValues("bobby", null, null, null));
        boolean deleted = adapter.delete(id);

        assertThat(page.total()).isEqualTo(1);
        assertThat(adapter.usernameExists("alice", null)).isTrue();
        assertThat(updated).isTrue();
        assertThat(deleted).isTrue();
        assertThat(adapter.findById(id)).isEmpty();
    }

    private void insertUser(String id, String username, String role) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_user (id, username, password, avatar, role, create_time, update_time, deleted)
                VALUES (?, ?, 'pw', null, ?, ?, ?, 0)
                """, id, username, role, now, now);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_user");
        jdbcTemplate.execute("""
                CREATE TABLE t_user (
                    id VARCHAR(64) PRIMARY KEY,
                    username VARCHAR(128) NOT NULL,
                    password VARCHAR(128) NOT NULL,
                    avatar VARCHAR(512),
                    role VARCHAR(32),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }
}

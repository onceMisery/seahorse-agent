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

import com.miracle.ai.seahorse.agent.ports.outbound.auth.RefreshTokenRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcRefreshTokenRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcRefreshTokenRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:refresh-token;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcRefreshTokenRepositoryAdapter(dataSource);
    }

    @Test
    void shouldSaveFindAndRevokeRefreshToken() {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        insertUser(1L, "alice", "admin");

        adapter.save(1L, "refresh-1", now.plusSeconds(60));
        RefreshTokenRecord record = adapter.findValid("refresh-1", now).orElseThrow();
        adapter.revoke("refresh-1");

        assertThat(record.userId()).isEqualTo(1L);
        assertThat(record.role()).isEqualTo("admin");
        assertThat(record.tenantId()).isEqualTo("default");
        assertThat(adapter.findValid("refresh-1", now)).isEmpty();
    }

    @Test
    void shouldIgnoreExpiredRefreshToken() {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        insertUser(1L, "alice", "admin");
        jdbcTemplate.update("""
                UPDATE t_user SET refresh_token = ?, refresh_token_expires_at = ? WHERE id = ?
                """, "expired", Timestamp.from(now.minusSeconds(1)), 1L);

        assertThat(adapter.findValid("expired", now)).isEmpty();
    }

    private void insertUser(Long id, String username, String role) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_user (id, username, password, avatar, role, create_time, update_time, deleted, tenant_id)
                VALUES (?, ?, 'pw', null, ?, ?, ?, 0, 'default')
                """, id, username, role, now, now);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_user");
        jdbcTemplate.execute("""
                CREATE TABLE t_user (
                    id BIGINT PRIMARY KEY,
                    username VARCHAR(128) NOT NULL,
                    password VARCHAR(128) NOT NULL,
                    avatar VARCHAR(512),
                    role VARCHAR(32),
                    refresh_token VARCHAR(255),
                    refresh_token_expires_at TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'
                )
                """);
    }
}

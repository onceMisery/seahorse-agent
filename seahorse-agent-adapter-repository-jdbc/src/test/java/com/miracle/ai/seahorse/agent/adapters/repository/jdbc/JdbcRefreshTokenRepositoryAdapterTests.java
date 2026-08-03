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
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

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
    void shouldSaveRotateAndRevokeRefreshToken() {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        insertUser(1L, "alice", "admin", "tenant-a");

        adapter.save(1L, "tenant-a", "refresh-1", now.plusSeconds(60));
        RefreshTokenRecord record = adapter
                .rotate("refresh-1", "refresh-2", now.plusSeconds(120), now)
                .orElseThrow();

        assertThat(record.userId()).isEqualTo(1L);
        assertThat(record.tenantId()).isEqualTo("tenant-a");
        assertThat(storedRefreshToken(1L)).isEqualTo("refresh-2");
        assertThat(adapter.rotate("refresh-1", "refresh-3", now.plusSeconds(180), now)).isEmpty();

        adapter.revoke("refresh-2");
        assertThat(storedRefreshToken(1L)).isNull();
    }

    @Test
    void shouldIgnoreExpiredRefreshToken() {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        insertUser(1L, "alice", "admin", "default");
        jdbcTemplate.update("""
                UPDATE t_user SET refresh_token = ?, refresh_token_expires_at = ? WHERE id = ?
                """, "expired", Timestamp.from(now.minusSeconds(1)), 1L);

        assertThat(adapter.rotate("expired", "next", now.plusSeconds(60), now)).isEmpty();
    }

    @Test
    void shouldAllowOnlyOneConcurrentRotation() throws Exception {
        Instant now = Instant.parse("2026-06-06T00:00:00Z");
        insertUser(1L, "alice", "admin", "default");
        adapter.save(1L, "default", "refresh-current", now.plusSeconds(60));
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch start = new CountDownLatch(1);
        try {
            Future<Optional<RefreshTokenRecord>> first = executor.submit(
                    () -> rotateAfter(start, "refresh-next-a", now));
            Future<Optional<RefreshTokenRecord>> second = executor.submit(
                    () -> rotateAfter(start, "refresh-next-b", now));
            start.countDown();

            List<Optional<RefreshTokenRecord>> outcomes = List.of(first.get(), second.get());
            assertThat(outcomes).filteredOn(Optional::isPresent).hasSize(1);
            assertThat(storedRefreshToken(1L)).isIn("refresh-next-a", "refresh-next-b");
        } finally {
            executor.shutdownNow();
        }
    }

    private Optional<RefreshTokenRecord> rotateAfter(CountDownLatch start, String nextToken, Instant now)
            throws InterruptedException {
        start.await();
        return adapter.rotate("refresh-current", nextToken, now.plusSeconds(120), now);
    }

    private String storedRefreshToken(Long userId) {
        return jdbcTemplate.queryForObject(
                "SELECT refresh_token FROM t_user WHERE id = ?", String.class, userId);
    }

    private void insertUser(Long id, String username, String role, String tenantId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_user (id, username, password, avatar, role, create_time, update_time, deleted, tenant_id)
                VALUES (?, ?, 'pw', null, ?, ?, ?, 0, ?)
                """, id, username, role, now, now, tenantId);
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

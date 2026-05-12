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

import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardOverview;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardPerformance;
import com.miracle.ai.seahorse.agent.ports.outbound.dashboard.DashboardTrends;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcDashboardRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcDashboardRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:dashboard;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcDashboardRepositoryAdapter(dataSource);
    }

    @Test
    void shouldLoadOverviewAndPerformance() {
        insertBaseRows();

        DashboardOverview overview = adapter.overview("24h");
        DashboardPerformance performance = adapter.performance("24h");

        assertThat(overview.kpis().totalUsers().value()).isEqualTo(1);
        assertThat(overview.kpis().totalSessions().value()).isEqualTo(1);
        assertThat(overview.kpis().totalMessages().value()).isEqualTo(2);
        assertThat(performance.getAvgLatencyMs()).isEqualTo(1000);
        assertThat(performance.getSuccessRate()).isEqualTo(33.3);
        assertThat(performance.getErrorRate()).isEqualTo(66.7);
    }

    @Test
    void shouldLoadMessageTrends() {
        insertBaseRows();

        DashboardTrends trends = adapter.trends("messages", "24h", "hour");

        assertThat(trends.granularity()).isEqualTo("hour");
        assertThat(trends.series()).hasSize(1);
        assertThat(trends.series().get(0).data())
                .anySatisfy(point -> assertThat(point.value()).isEqualTo(2.0));
    }

    private void insertBaseRows() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_user (id, username, create_time, update_time, deleted)
                VALUES ('u1', 'user', ?, ?, 0)
                """, now, now);
        jdbcTemplate.update("""
                INSERT INTO t_conversation
                (id, conversation_id, user_id, title, create_time, update_time, deleted)
                VALUES ('c1', 'conv-1', 'u1', 'title', ?, ?, 0)
                """, now, now);
        insertMessage("m1", "user", "question", now);
        insertMessage("m2", "assistant", "answer", now);
        insertTraceRun("r1", "SUCCESS", 1000, now);
        insertTraceRun("r2", "ERROR", 0, now);
        insertTraceRun("r3", "FAILED", 0, now);
    }

    private void insertMessage(String id, String role, String content, Timestamp now) {
        jdbcTemplate.update("""
                INSERT INTO t_message
                (id, conversation_id, user_id, role, content, create_time, update_time, deleted)
                VALUES (?, 'conv-1', 'u1', ?, ?, ?, ?, 0)
                """, id, role, content, now, now);
    }

    private void insertTraceRun(String id, String status, long duration, Timestamp now) {
        jdbcTemplate.update("""
                INSERT INTO t_rag_trace_run
                (id, trace_id, status, start_time, end_time, duration_ms, create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """, id, "trace-" + id, status, now, now, duration, now, now);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_rag_trace_run");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_message");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_conversation");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_user");
        jdbcTemplate.execute("""
                CREATE TABLE t_user (
                    id VARCHAR(20) PRIMARY KEY,
                    username VARCHAR(64),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_conversation (
                    id VARCHAR(20) PRIMARY KEY,
                    conversation_id VARCHAR(20),
                    user_id VARCHAR(20),
                    title VARCHAR(128),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_message (
                    id VARCHAR(20) PRIMARY KEY,
                    conversation_id VARCHAR(20),
                    user_id VARCHAR(20),
                    role VARCHAR(16),
                    content TEXT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_rag_trace_run (
                    id VARCHAR(20) PRIMARY KEY,
                    trace_id VARCHAR(64),
                    status VARCHAR(16),
                    start_time TIMESTAMP,
                    end_time TIMESTAMP,
                    duration_ms BIGINT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }
}

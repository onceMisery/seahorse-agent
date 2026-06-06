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

import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConversationMemoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcConversationMemoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:conversation-memory;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcConversationMemoryAdapter(dataSource, 4);
    }

    @Test
    void shouldLoadHistoryAndAppendUserMessage() {
        Timestamp sameMoment = Timestamp.from(Instant.parse("2026-05-19T12:00:00Z"));
        insertMessage("1", "1", "1", "user", "hello", sameMoment);
        insertMessage("2", "1", "1", "assistant", "hi", sameMoment);

        List<ChatMessage> history = adapter.loadAndAppend("1", "1", ChatMessage.user("next"));

        assertThat(history).extracting(ChatMessage::getContent).containsExactly("hello", "hi");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_message", Integer.class)).isEqualTo(3);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_conversation", Integer.class)).isEqualTo(1);
    }

    @Test
    void shouldTrimLeadingAssistantFromLimitedHistory() {
        adapter.append("1", "1", ChatMessage.assistant("orphan"));
        adapter.append("1", "1", ChatMessage.user("hello"));

        List<ChatMessage> history = adapter.loadAndAppend("1", "1", ChatMessage.user("next"));

        assertThat(history).hasSize(1);
        assertThat(history.get(0).getRole()).isEqualTo(ChatRole.USER);
        assertThat(history.get(0).getContent()).isEqualTo("hello");
    }

    @Test
    void shouldPersistAgentRunIdWhenAppendingAssistantMessage() {
        adapter.append("1", "1", ChatMessage.assistant("answer"), "run-1");

        String runId = jdbcTemplate.queryForObject("""
                SELECT agent_run_id
                FROM t_message
                WHERE conversation_id = ? AND user_id = ? AND role = ?
                """, String.class, 1L, 1L, "assistant");

        assertThat(runId).isEqualTo("run-1");
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_message");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_conversation");
        jdbcTemplate.execute("""
                CREATE TABLE t_conversation (
                    id VARCHAR(20) PRIMARY KEY,
                    conversation_id VARCHAR(20) NOT NULL,
                    user_id VARCHAR(20) NOT NULL,
                    title VARCHAR(128) NOT NULL,
                    last_time TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_message (
                    id VARCHAR(20) PRIMARY KEY,
                    conversation_id VARCHAR(20) NOT NULL,
                    user_id VARCHAR(20) NOT NULL,
                    role VARCHAR(16) NOT NULL,
                    content TEXT NOT NULL,
                    thinking_content TEXT,
                    thinking_duration INTEGER,
                    agent_run_id VARCHAR(64),
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'
                )
                """);
    }

    private void insertMessage(String id, String conversationId, String userId, String role, String content,
                               Timestamp timestamp) {
        jdbcTemplate.update("""
                INSERT INTO t_message
                (id, conversation_id, user_id, role, content, thinking_content, thinking_duration,
                 agent_run_id, create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, NULL, NULL, NULL, ?, ?, 0)
                """, id, conversationId, userId, role, content, timestamp, timestamp);
    }
}

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

import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConversationRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcConversationRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:conversation-management;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcConversationRepositoryAdapter(dataSource);
    }

    @Test
    void shouldListRenameAndDeleteConversation() {
        insertConversation();

        List<ConversationRecord> conversations = adapter.listConversations("1");
        boolean renamed = adapter.rename("1", "1", "renamed");
        boolean deleted = adapter.delete("1", "1");

        assertThat(conversations).extracting(ConversationRecord::conversationId).containsExactly("1");
        assertThat(renamed).isTrue();
        assertThat(deleted).isTrue();
        assertThat(adapter.listConversations("1")).isEmpty();
    }

    @Test
    void shouldListMessagesWithFeedbackVote() {
        insertConversation();
        insertMessage("msg-1", "user", null);
        insertMessage("msg-2", "assistant", 1, "run-1");

        List<ConversationMessageRecord> messages = adapter.listMessages("1", "1");

        assertThat(messages).extracting(ConversationMessageRecord::getId).containsExactly("1", "2");
        assertThat(messages.get(1).getVote()).isEqualTo(1);
        assertThat(messages.get(1).getAgentRunId()).isEqualTo("run-1");
    }

    private void insertConversation() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_conversation
                (id, conversation_id, user_id, title, last_time, create_time, update_time, deleted, tenant_id)
                VALUES (1, 1, 1, 'title', ?, ?, ?, 0, 'default')
                """, now, now, now);
    }

    private void insertMessage(String id, String role, Integer vote) {
        insertMessage(id, role, vote, null);
    }

    private void insertMessage(String id, String role, Integer vote, String agentRunId) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_message
                (id, conversation_id, user_id, role, content, thinking_content, thinking_duration,
                 agent_run_id, create_time, update_time, deleted, tenant_id)
                VALUES (?, 1, 1, ?, ?, null, null, ?, ?, ?, 0, 'default')
                """, Long.parseLong(id.replace("msg-", "")), role, "content-" + id, agentRunId, now, now);
        if (vote == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO t_message_feedback
                (id, message_id, conversation_id, user_id, vote, create_time, update_time, deleted)
                VALUES (?, ?, 1, 1, ?, ?, ?, 0)
                """, Long.parseLong(id.replace("msg-", "")), Long.parseLong(id.replace("msg-", "")), vote, now, now);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_message_feedback");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_message");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_conversation_summary");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_conversation");
        jdbcTemplate.execute("""
                CREATE TABLE t_conversation (
                    id BIGINT PRIMARY KEY,
                    conversation_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    title VARCHAR(128) NOT NULL,
                    last_time TIMESTAMP,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_conversation_summary (
                    id BIGINT PRIMARY KEY,
                    conversation_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default'
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_message (
                    id BIGINT PRIMARY KEY,
                    conversation_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
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
        jdbcTemplate.execute("""
                CREATE TABLE t_message_feedback (
                    id BIGINT PRIMARY KEY,
                    message_id BIGINT NOT NULL,
                    conversation_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    vote SMALLINT NOT NULL,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }
}

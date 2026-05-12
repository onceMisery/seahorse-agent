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

        List<ConversationRecord> conversations = adapter.listConversations("user-1");
        boolean renamed = adapter.rename("conv-1", "user-1", "renamed");
        boolean deleted = adapter.delete("conv-1", "user-1");

        assertThat(conversations).extracting(ConversationRecord::conversationId).containsExactly("conv-1");
        assertThat(renamed).isTrue();
        assertThat(deleted).isTrue();
        assertThat(adapter.listConversations("user-1")).isEmpty();
    }

    @Test
    void shouldListMessagesWithFeedbackVote() {
        insertConversation();
        insertMessage("msg-1", "user", null);
        insertMessage("msg-2", "assistant", 1);

        List<ConversationMessageRecord> messages = adapter.listMessages("conv-1", "user-1");

        assertThat(messages).extracting(ConversationMessageRecord::getId).containsExactly("msg-1", "msg-2");
        assertThat(messages.get(1).getVote()).isEqualTo(1);
    }

    private void insertConversation() {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_conversation
                (id, conversation_id, user_id, title, last_time, create_time, update_time, deleted)
                VALUES ('1', 'conv-1', 'user-1', 'title', ?, ?, ?, 0)
                """, now, now, now);
    }

    private void insertMessage(String id, String role, Integer vote) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_message
                (id, conversation_id, user_id, role, content, thinking_content, thinking_duration,
                 create_time, update_time, deleted)
                VALUES (?, 'conv-1', 'user-1', ?, ?, null, null, ?, ?, 0)
                """, id, role, "content-" + id, now, now);
        if (vote == null) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO t_message_feedback
                (id, message_id, conversation_id, user_id, vote, create_time, update_time, deleted)
                VALUES (?, ?, 'conv-1', 'user-1', ?, ?, ?, 0)
                """, "fb-" + id, id, vote, now, now);
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_message_feedback");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_message");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_conversation_summary");
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
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_conversation_summary (
                    id VARCHAR(20) PRIMARY KEY,
                    conversation_id VARCHAR(20) NOT NULL,
                    user_id VARCHAR(20) NOT NULL,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
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
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE t_message_feedback (
                    id VARCHAR(20) PRIMARY KEY,
                    message_id VARCHAR(20) NOT NULL,
                    conversation_id VARCHAR(20) NOT NULL,
                    user_id VARCHAR(20) NOT NULL,
                    vote SMALLINT NOT NULL,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }
}

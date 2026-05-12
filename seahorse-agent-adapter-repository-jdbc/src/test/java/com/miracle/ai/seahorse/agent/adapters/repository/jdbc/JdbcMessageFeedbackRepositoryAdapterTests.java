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

import com.miracle.ai.seahorse.agent.ports.outbound.feedback.MessageFeedbackSubmission;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcMessageFeedbackRepositoryAdapterTests {

    private JdbcTemplate jdbcTemplate;
    private JdbcMessageFeedbackRepositoryAdapter adapter;

    @BeforeEach
    void setUp() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:message-feedback;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", "");
        jdbcTemplate = new JdbcTemplate(dataSource);
        createSchema();
        adapter = new JdbcMessageFeedbackRepositoryAdapter(dataSource);
    }

    @Test
    void shouldInsertAndUpdateFeedbackForAssistantMessage() {
        insertMessage("msg-1", "conv-1", "user-1", "assistant");

        adapter.upsert(new MessageFeedbackSubmission("msg-1", "user-1", 1, "good", "useful", Instant.now()));
        adapter.upsert(new MessageFeedbackSubmission("msg-1", "user-1", -1, "bad", "wrong", Instant.now()));

        Integer count = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_message_feedback", Integer.class);
        Integer vote = jdbcTemplate.queryForObject("SELECT vote FROM t_message_feedback WHERE message_id = ?",
                Integer.class, "msg-1");
        Map<String, Integer> votes = adapter.findUserVotes("user-1", List.of("msg-1", "missing"));
        assertThat(count).isEqualTo(1);
        assertThat(vote).isEqualTo(-1);
        assertThat(votes).containsEntry("msg-1", -1);
    }

    @Test
    void shouldRejectFeedbackForUserMessage() {
        insertMessage("msg-2", "conv-1", "user-1", "user");

        assertThatThrownBy(() -> adapter.upsert(
                new MessageFeedbackSubmission("msg-2", "user-1", 1, "", "", Instant.now())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("assistant message not found");
    }

    private void createSchema() {
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_message_feedback");
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_message");
        jdbcTemplate.execute("""
                CREATE TABLE t_message (
                    id VARCHAR(20) PRIMARY KEY,
                    conversation_id VARCHAR(20) NOT NULL,
                    user_id VARCHAR(20) NOT NULL,
                    role VARCHAR(16) NOT NULL,
                    content TEXT NOT NULL,
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
                    vote INTEGER NOT NULL,
                    reason VARCHAR(255),
                    comment TEXT,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
    }

    private void insertMessage(String messageId, String conversationId, String userId, String role) {
        jdbcTemplate.update("""
                INSERT INTO t_message
                (id, conversation_id, user_id, role, content, create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, 'content', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """, messageId, conversationId, userId, role);
    }
}

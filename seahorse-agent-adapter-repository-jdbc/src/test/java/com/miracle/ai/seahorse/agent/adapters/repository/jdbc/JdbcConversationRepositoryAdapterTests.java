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

    @Test
    void shouldListOnlyActiveMessagesAndMapBranchFields() {
        insertConversation();
        insertMessage("msg-1", "user", null, null, null, 1, 0);
        insertMessage("msg-2", "assistant", null, null, 1L, 0, 0);
        insertMessage("msg-3", "assistant", null, null, 1L, 1, 1);

        List<ConversationMessageRecord> activeMessages = adapter.listMessages("1", "1");
        List<ConversationMessageRecord> siblings = adapter.listSiblings("1", "1", 1L);

        assertThat(activeMessages).extracting(ConversationMessageRecord::getId).containsExactly("1", "3");
        assertThat(activeMessages.get(1).getParentId()).isEqualTo(1L);
        assertThat(activeMessages.get(1).getSiblingSeq()).isEqualTo(1);
        assertThat(siblings).extracting(ConversationMessageRecord::getId).containsExactly("2", "3");
    }

    @Test
    void shouldAppendMessageAndSetActivePath() {
        insertConversation();
        insertMessage("msg-1", "user", null, null, null, 1, 0);

        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setConversationId("1");
        record.setUserId("1");
        record.setRole("assistant");
        record.setContent("fresh");
        record.setParentId(1L);
        record.setActive(1);
        record.setSiblingSeq(0);
        Long newId = adapter.appendMessage(record);
        adapter.setActivePath("1", "1", java.util.Set.of(1L, newId));

        List<ConversationMessageRecord> tree = adapter.listTree("1", "1");

        assertThat(tree).extracting(ConversationMessageRecord::getId).contains(String.valueOf(newId));
        assertThat(adapter.listMessages("1", "1")).extracting(ConversationMessageRecord::getId)
                .containsExactly("1", String.valueOf(newId));
    }

    @Test
    void shouldRetryAppendWithNextSiblingSeqWhenRequestedSeqAlreadyExists() {
        insertConversation();
        insertMessage("msg-1", "user", null, null, null, 1, 0);
        insertMessage("msg-2", "assistant", null, null, 1L, 0, 0);

        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setConversationId("1");
        record.setUserId("1");
        record.setRole("assistant");
        record.setContent("fresh branch");
        record.setParentId(1L);
        record.setActive(1);
        record.setSiblingSeq(0);

        Long newId = adapter.appendMessage(record);

        Integer siblingSeq = jdbcTemplate.queryForObject("""
                SELECT sibling_seq
                FROM t_message
                WHERE id = ?
                """, Integer.class, newId);
        assertThat(siblingSeq).isEqualTo(1);
    }

    @Test
    void shouldUpsertAndFindBranchCursor() {
        insertConversation();
        insertMessage("msg-1", "user", null, null, null, 1, 0);
        insertMessage("msg-2", "assistant", null, null, 1L, 1, 0);

        adapter.upsertCursor("1", "1", 1L);
        adapter.upsertCursor("1", "1", 2L);

        assertThat(adapter.findCursor("1", "1")).hasValueSatisfying(cursor -> {
            assertThat(cursor.getTenantId()).isEqualTo("default");
            assertThat(cursor.getConversationId()).isEqualTo("1");
            assertThat(cursor.getUserId()).isEqualTo("1");
            assertThat(cursor.getLeafMessageId()).isEqualTo(2L);
        });
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
        insertMessage(id, role, vote, agentRunId, null, 1, 0);
    }

    private void insertMessage(String id, String role, Integer vote, String agentRunId, Long parentId,
                               int active, int siblingSeq) {
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_message
                (id, conversation_id, user_id, role, content, thinking_content, thinking_duration,
                 agent_run_id, parent_id, active, branch_root_id, sibling_seq, create_time, update_time, deleted, tenant_id)
                VALUES (?, 1, 1, ?, ?, null, null, ?, ?, ?, null, ?, ?, ?, 0, 'default')
                """, Long.parseLong(id.replace("msg-", "")), role, "content-" + id, agentRunId,
                parentId, active, siblingSeq, now, now);
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
        jdbcTemplate.execute("DROP TABLE IF EXISTS t_conversation_branch_cursor");
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
                    parent_id BIGINT,
                    active SMALLINT NOT NULL DEFAULT 1,
                    branch_root_id BIGINT,
                    sibling_seq INTEGER NOT NULL DEFAULT 0,
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
        jdbcTemplate.execute("""
                CREATE TABLE t_conversation_branch_cursor (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    conversation_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL,
                    leaf_message_id BIGINT NOT NULL,
                    create_time TIMESTAMP,
                    update_time TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX uk_conversation_branch_cursor_user
                ON t_conversation_branch_cursor (tenant_id, conversation_id, user_id)
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX uk_t_message_sibling_seq
                ON t_message (tenant_id, conversation_id, user_id, parent_id, sibling_seq)
                """);
    }
}

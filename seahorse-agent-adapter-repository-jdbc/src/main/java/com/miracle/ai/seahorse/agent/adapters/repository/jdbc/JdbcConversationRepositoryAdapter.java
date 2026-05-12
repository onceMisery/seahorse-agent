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
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 基于旧会话表的 JDBC 会话管理 adapter。
 */
public class JdbcConversationRepositoryAdapter implements ConversationRepositoryPort {

    private static final String SQL_LIST_CONVERSATIONS = """
            SELECT conversation_id, title, last_time
            FROM t_conversation
            WHERE user_id = ? AND deleted = 0
            ORDER BY last_time DESC
            """;
    private static final String SQL_RENAME = """
            UPDATE t_conversation
            SET title = ?, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_CONVERSATION = """
            UPDATE t_conversation
            SET deleted = 1, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_MESSAGES = """
            UPDATE t_message
            SET deleted = 1, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_SUMMARY = """
            UPDATE t_conversation_summary
            SET deleted = 1, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0
            """;
    private static final String SQL_LIST_MESSAGES = """
            SELECT m.id, m.conversation_id, m.role, m.content, m.thinking_content,
                   m.thinking_duration, m.create_time, f.vote
            FROM t_message m
            LEFT JOIN t_message_feedback f
              ON f.message_id = m.id AND f.user_id = m.user_id AND f.deleted = 0
            WHERE m.conversation_id = ? AND m.user_id = ? AND m.deleted = 0
            ORDER BY m.create_time ASC
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcConversationRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public List<ConversationRecord> listConversations(String userId) {
        if (!hasText(userId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_CONVERSATIONS, this::mapConversation, userId);
    }

    @Override
    public boolean rename(String conversationId, String userId, String title) {
        if (!hasText(conversationId) || !hasText(userId) || !hasText(title)) {
            return false;
        }
        int updated = jdbcTemplate.update(SQL_RENAME, title, Timestamp.from(Instant.now()), conversationId, userId);
        return updated > 0;
    }

    @Override
    public boolean delete(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return false;
        }
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbcTemplate.update(SQL_DELETE_CONVERSATION, now, conversationId, userId);
        jdbcTemplate.update(SQL_DELETE_MESSAGES, now, conversationId, userId);
        jdbcTemplate.update(SQL_DELETE_SUMMARY, now, conversationId, userId);
        return updated > 0;
    }

    @Override
    public List<ConversationMessageRecord> listMessages(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_MESSAGES, this::mapMessage, conversationId, userId);
    }

    private ConversationRecord mapConversation(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp lastTime = resultSet.getTimestamp("last_time");
        return new ConversationRecord(
                resultSet.getString("conversation_id"),
                resultSet.getString("title"),
                lastTime == null ? null : lastTime.toInstant());
    }

    private ConversationMessageRecord mapMessage(ResultSet resultSet, int rowNum) throws SQLException {
        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setId(resultSet.getString("id"));
        record.setConversationId(resultSet.getString("conversation_id"));
        record.setRole(resultSet.getString("role"));
        record.setContent(resultSet.getString("content"));
        record.setThinkingContent(resultSet.getString("thinking_content"));
        record.setThinkingDuration(resultSet.getObject("thinking_duration", Integer.class));
        record.setVote(resultSet.getObject("vote", Integer.class));
        Timestamp createTime = resultSet.getTimestamp("create_time");
        record.setCreateTime(createTime == null ? null : createTime.toInstant());
        return record;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

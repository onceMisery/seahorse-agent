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

import static com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcMemorySupport.toLongId;

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcTenantSupport;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatMessage;
import com.miracle.ai.seahorse.agent.kernel.domain.chat.ChatRole;
import com.miracle.ai.seahorse.agent.ports.outbound.chat.ConversationMemoryPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * 基于旧会话表的原生会话记忆 adapter。
 *
 * <p>复用 {@code t_conversation}/{@code t_message}，保证 seahorse-agent 切换为 kernel 模式后仍能读取和写入旧历史。
 */
public class JdbcConversationMemoryAdapter implements ConversationMemoryPort {

    private static final int APPEND_RETRY_LIMIT = 3;
    private static final int DEFAULT_HISTORY_LIMIT = 20;
    private static final int TITLE_MAX_LENGTH = 128;
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String DEFAULT_TITLE = "New conversation";
    private static final String SQL_LIST_MESSAGES = """
            SELECT role, content, thinking_content, thinking_duration
            FROM t_message
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ? AND active = 1
            ORDER BY create_time DESC, id DESC
            LIMIT ?
            """;
    private static final String SQL_INSERT_MESSAGE = """
            INSERT INTO t_message
            (id, conversation_id, user_id, role, content, thinking_content, thinking_duration,
             agent_run_id, parent_id, active, branch_root_id, sibling_seq, create_time, update_time, deleted, tenant_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
            """;
    private static final String SQL_FIND_ACTIVE_LEAF = """
            SELECT m.id
            FROM t_message m
            WHERE m.conversation_id = ? AND m.user_id = ? AND m.deleted = 0 AND m.tenant_id = ? AND m.active = 1
              AND NOT EXISTS (
                  SELECT 1
                  FROM t_message child
                  WHERE child.conversation_id = m.conversation_id
                    AND child.user_id = m.user_id
                    AND child.tenant_id = m.tenant_id
                    AND child.deleted = 0
                    AND child.active = 1
                    AND child.parent_id = m.id
              )
            ORDER BY m.create_time DESC, m.id DESC
            LIMIT 1
            """;
    private static final String SQL_COUNT_ROOT_SIBLINGS = """
            SELECT COUNT(1)
            FROM t_message
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ? AND parent_id IS NULL
            """;
    private static final String SQL_COUNT_CHILD_SIBLINGS = """
            SELECT COUNT(1)
            FROM t_message
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ? AND parent_id = ?
            """;
    private static final String SQL_NEXT_ROOT_SIBLING_SEQ = """
            SELECT COALESCE(MAX(sibling_seq), -1) + 1
            FROM t_message
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?
              AND parent_id IS NULL
            """;
    private static final String SQL_NEXT_CHILD_SIBLING_SEQ = """
            SELECT COALESCE(MAX(sibling_seq), -1) + 1
            FROM t_message
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?
              AND parent_id = ?
            """;
    private static final String SQL_COUNT_CONVERSATION = """
            SELECT COUNT(1) FROM t_conversation
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_INSERT_CONVERSATION = """
            INSERT INTO t_conversation
            (id, conversation_id, user_id, title, last_time, create_time, update_time, deleted, tenant_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
            """;
    private static final String SQL_UPDATE_CONVERSATION = """
            UPDATE t_conversation
            SET last_time = ?, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;
    private final int historyLimit;

    public JdbcConversationMemoryAdapter(DataSource dataSource) {
        this(dataSource, DEFAULT_HISTORY_LIMIT);
    }

    public JdbcConversationMemoryAdapter(DataSource dataSource, int historyLimit) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.historyLimit = historyLimit <= 0 ? DEFAULT_HISTORY_LIMIT : historyLimit;
    }

    @Override
    public List<ChatMessage> loadAndAppend(String conversationId, String userId, ChatMessage message) {
        List<ChatMessage> history = loadHistory(conversationId, userId);
        append(conversationId, userId, message);
        return history;
    }

    @Override
    public void append(String conversationId, String userId, ChatMessage message) {
        append(conversationId, userId, message, null);
    }

    @Override
    public void append(String conversationId, String userId, ChatMessage message, String agentRunId) {
        if (!hasText(conversationId) || !hasText(userId) || message == null || !hasText(message.getContent())) {
            return;
        }
        long convId = toLongId(conversationId);
        long uid = toLongId(userId);
        String tenantId = JdbcTenantSupport.resolveTenantId();
        Long parentId = activeLeafId(convId, uid, tenantId);
        int siblingSeq = siblingCount(convId, uid, tenantId, parentId);
        Timestamp now = Timestamp.from(Instant.now());
        long messageId = JdbcMemorySupport.nextId();
        for (int attempt = 0; attempt < APPEND_RETRY_LIMIT; attempt++) {
            try {
                jdbcTemplate.update(SQL_INSERT_MESSAGE,
                        messageId,
                        convId,
                        uid,
                        roleValue(message.getRole()),
                        message.getContent(),
                        message.getThinkingContent(),
                        message.getThinkingDuration(),
                        trimToNull(agentRunId),
                        parentId,
                        1,
                        null,
                        siblingSeq,
                        now,
                        now,
                        tenantId);
                break;
            } catch (DuplicateKeyException ex) {
                if (attempt + 1 >= APPEND_RETRY_LIMIT) {
                    throw ex;
                }
                siblingSeq = nextSiblingSeq(convId, uid, tenantId, parentId);
            }
        }
        upsertConversation(conversationId, userId, message, now);
    }

    private List<ChatMessage> loadHistory(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return List.of();
        }
        List<ChatMessage> messages = jdbcTemplate.query(SQL_LIST_MESSAGES, this::mapMessage,
                toLongId(conversationId), toLongId(userId), JdbcTenantSupport.resolveTenantId(), historyLimit);
        Collections.reverse(messages);
        return trimLeadingAssistant(messages);
    }

    private Long activeLeafId(long conversationId, long userId, String tenantId) {
        List<Long> ids = jdbcTemplate.queryForList(SQL_FIND_ACTIVE_LEAF, Long.class, conversationId, userId, tenantId);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private int siblingCount(long conversationId, long userId, String tenantId, Long parentId) {
        Integer count;
        if (parentId == null) {
            count = jdbcTemplate.queryForObject(
                    SQL_COUNT_ROOT_SIBLINGS, Integer.class, conversationId, userId, tenantId);
        } else {
            count = jdbcTemplate.queryForObject(
                    SQL_COUNT_CHILD_SIBLINGS, Integer.class, conversationId, userId, tenantId, parentId);
        }
        return Objects.requireNonNullElse(count, 0);
    }

    private int nextSiblingSeq(long conversationId, long userId, String tenantId, Long parentId) {
        Integer next = parentId == null
                ? jdbcTemplate.queryForObject(SQL_NEXT_ROOT_SIBLING_SEQ, Integer.class, conversationId, userId, tenantId)
                : jdbcTemplate.queryForObject(
                        SQL_NEXT_CHILD_SIBLING_SEQ, Integer.class, conversationId, userId, tenantId, parentId);
        return Objects.requireNonNullElse(next, 0);
    }

    private ChatMessage mapMessage(ResultSet resultSet, int rowNum) throws SQLException {
        ChatRole role = role(resultSet.getString("role"));
        String content = resultSet.getString("content");
        String thinkingContent = resultSet.getString("thinking_content");
        Integer thinkingDuration = (Integer) resultSet.getObject("thinking_duration");
        if (ChatRole.ASSISTANT.equals(role)) {
            return ChatMessage.assistant(content, thinkingContent, thinkingDuration);
        }
        return new ChatMessage(role, content);
    }

    private List<ChatMessage> trimLeadingAssistant(List<ChatMessage> messages) {
        int start = 0;
        while (start < messages.size() && ChatRole.ASSISTANT.equals(messages.get(start).getRole())) {
            start++;
        }
        if (start >= messages.size()) {
            return List.of();
        }
        return List.copyOf(messages.subList(start, messages.size()));
    }

    private void upsertConversation(String conversationId, String userId, ChatMessage message, Timestamp now) {
        long convId = toLongId(conversationId);
        long uid = toLongId(userId);
        String tenantId = JdbcTenantSupport.resolveTenantId();
        Integer count = jdbcTemplate.queryForObject(SQL_COUNT_CONVERSATION, Integer.class, convId, uid, tenantId);
        if (count != null && count > 0) {
            jdbcTemplate.update(SQL_UPDATE_CONVERSATION, now, now, convId, uid, tenantId);
            return;
        }
        jdbcTemplate.update(SQL_INSERT_CONVERSATION,
                JdbcMemorySupport.nextId(),
                convId,
                uid,
                title(message.getContent()),
                now,
                now,
                now,
                tenantId);
    }

    private String title(String question) {
        String content = Objects.requireNonNullElse(question, "").strip();
        if (content.isEmpty()) {
            return DEFAULT_TITLE;
        }
        if (content.length() <= TITLE_MAX_LENGTH) {
            return content;
        }
        return content.substring(0, TITLE_MAX_LENGTH);
    }

    private ChatRole role(String value) {
        if (ROLE_ASSISTANT.equalsIgnoreCase(value)) {
            return ChatRole.ASSISTANT;
        }
        return ChatRole.USER;
    }

    private String roleValue(ChatRole role) {
        if (ChatRole.ASSISTANT.equals(role)) {
            return ROLE_ASSISTANT;
        }
        return ROLE_USER;
    }

    

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String trimToNull(String value) {
        if (!hasText(value)) {
            return null;
        }
        return value.trim();
    }
}

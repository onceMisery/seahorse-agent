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

import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcTenantSupport;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchCursor;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationBranchRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationMessageRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.conversation.ConversationRepositoryPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.StringJoiner;

/**
 * JDBC conversation repository adapter.
 */
public class JdbcConversationRepositoryAdapter implements ConversationRepositoryPort, ConversationBranchRepositoryPort {

    private static final int APPEND_RETRY_LIMIT = 3;

    private static final String SQL_INSERT_CONVERSATION = """
            INSERT INTO t_conversation
            (id, conversation_id, user_id, title, create_time, update_time, last_time, deleted, tenant_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
            """;
    private static final String SQL_LIST_CONVERSATIONS = """
            SELECT conversation_id, title, last_time
            FROM t_conversation
            WHERE user_id = ? AND deleted = 0 AND tenant_id = ?
            ORDER BY last_time DESC
            """;
    private static final String SQL_RENAME = """
            UPDATE t_conversation
            SET title = ?, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_DELETE_CONVERSATION = """
            UPDATE t_conversation
            SET deleted = 1, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_DELETE_MESSAGES = """
            UPDATE t_message
            SET deleted = 1, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_DELETE_SUMMARY = """
            UPDATE t_conversation_summary
            SET deleted = 1, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_LIST_MESSAGES = """
            SELECT m.id, m.conversation_id, m.user_id, m.role, m.content, m.agent_run_id, m.thinking_content,
                   m.thinking_duration, m.parent_id, m.active, m.branch_root_id, m.sibling_seq, m.create_time, f.vote
            FROM t_message m
            LEFT JOIN t_message_feedback f
              ON f.message_id = m.id AND f.user_id = m.user_id AND f.deleted = 0
            WHERE m.conversation_id = ? AND m.user_id = ? AND m.deleted = 0 AND m.tenant_id = ?
              AND m.active = 1
            ORDER BY m.create_time ASC
            """;
    private static final String SQL_LIST_TREE = """
            SELECT m.id, m.conversation_id, m.user_id, m.role, m.content, m.agent_run_id, m.thinking_content,
                   m.thinking_duration, m.parent_id, m.active, m.branch_root_id, m.sibling_seq, m.create_time, f.vote
            FROM t_message m
            LEFT JOIN t_message_feedback f
              ON f.message_id = m.id AND f.user_id = m.user_id AND f.deleted = 0
            WHERE m.conversation_id = ? AND m.user_id = ? AND m.deleted = 0 AND m.tenant_id = ?
            ORDER BY m.create_time ASC, m.id ASC
            """;
    private static final String SQL_LIST_ROOT_SIBLINGS = """
            SELECT m.id, m.conversation_id, m.user_id, m.role, m.content, m.agent_run_id, m.thinking_content,
                   m.thinking_duration, m.parent_id, m.active, m.branch_root_id, m.sibling_seq, m.create_time, f.vote
            FROM t_message m
            LEFT JOIN t_message_feedback f
              ON f.message_id = m.id AND f.user_id = m.user_id AND f.deleted = 0
            WHERE m.conversation_id = ? AND m.user_id = ? AND m.deleted = 0 AND m.tenant_id = ?
              AND m.parent_id IS NULL
            ORDER BY m.sibling_seq ASC, m.create_time ASC, m.id ASC
            """;
    private static final String SQL_LIST_CHILD_SIBLINGS = """
            SELECT m.id, m.conversation_id, m.user_id, m.role, m.content, m.agent_run_id, m.thinking_content,
                   m.thinking_duration, m.parent_id, m.active, m.branch_root_id, m.sibling_seq, m.create_time, f.vote
            FROM t_message m
            LEFT JOIN t_message_feedback f
              ON f.message_id = m.id AND f.user_id = m.user_id AND f.deleted = 0
            WHERE m.conversation_id = ? AND m.user_id = ? AND m.deleted = 0 AND m.tenant_id = ?
              AND m.parent_id = ?
            ORDER BY m.sibling_seq ASC, m.create_time ASC, m.id ASC
            """;
    private static final String SQL_INSERT_MESSAGE = """
            INSERT INTO t_message
            (id, conversation_id, user_id, role, content, agent_run_id, thinking_content, thinking_duration,
             parent_id, active, branch_root_id, sibling_seq, create_time, update_time, deleted, tenant_id)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?)
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
    private static final String SQL_CLEAR_ACTIVE_PATH = """
            UPDATE t_message
            SET active = 0, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_UPDATE_BRANCH_CURSOR = """
            UPDATE t_conversation_branch_cursor
            SET leaf_message_id = ?, update_time = ?, deleted = 0
            WHERE tenant_id = ? AND conversation_id = ? AND user_id = ?
            """;
    private static final String SQL_INSERT_BRANCH_CURSOR = """
            INSERT INTO t_conversation_branch_cursor
            (id, tenant_id, conversation_id, user_id, leaf_message_id, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, 0)
            """;
    private static final String SQL_FIND_BRANCH_CURSOR = """
            SELECT id, tenant_id, conversation_id, user_id, leaf_message_id, create_time, update_time
            FROM t_conversation_branch_cursor
            WHERE tenant_id = ? AND conversation_id = ? AND user_id = ? AND deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcConversationRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Long create(String userId) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        long conversationId = JdbcMemorySupport.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_INSERT_CONVERSATION, conversationId, conversationId, Long.parseLong(userId),
                "New conversation", now, now, now, JdbcTenantSupport.resolveTenantId());
        return conversationId;
    }

    @Override
    public List<ConversationRecord> listConversations(String userId) {
        if (!hasText(userId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_CONVERSATIONS, this::mapConversation,
                Long.parseLong(userId), JdbcTenantSupport.resolveTenantId());
    }

    @Override
    public boolean rename(String conversationId, String userId, String title) {
        if (!hasText(conversationId) || !hasText(userId) || !hasText(title)) {
            return false;
        }
        int updated = jdbcTemplate.update(SQL_RENAME, title, Timestamp.from(Instant.now()),
                Long.parseLong(conversationId), Long.parseLong(userId), JdbcTenantSupport.resolveTenantId());
        return updated > 0;
    }

    @Override
    public boolean delete(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return false;
        }
        Timestamp now = Timestamp.from(Instant.now());
        long cid = Long.parseLong(conversationId);
        long uid = Long.parseLong(userId);
        String tenantId = JdbcTenantSupport.resolveTenantId();
        int updated = jdbcTemplate.update(SQL_DELETE_CONVERSATION, now, cid, uid, tenantId);
        jdbcTemplate.update(SQL_DELETE_MESSAGES, now, cid, uid, tenantId);
        jdbcTemplate.update(SQL_DELETE_SUMMARY, now, cid, uid, tenantId);
        return updated > 0;
    }

    @Override
    public List<ConversationMessageRecord> listMessages(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_MESSAGES, this::mapMessage,
                Long.parseLong(conversationId), Long.parseLong(userId), JdbcTenantSupport.resolveTenantId());
    }

    @Override
    public Long appendMessage(ConversationMessageRecord record) {
        ConversationMessageRecord safeRecord = Objects.requireNonNull(record, "record must not be null");
        if (!hasText(safeRecord.getConversationId()) || !hasText(safeRecord.getUserId())
                || !hasText(safeRecord.getRole()) || !hasText(safeRecord.getContent())) {
            throw new IllegalArgumentException("conversationId, userId, role and content must not be blank");
        }
        long id = JdbcMemorySupport.nextId();
        long conversationId = Long.parseLong(safeRecord.getConversationId());
        long userId = Long.parseLong(safeRecord.getUserId());
        String tenantId = JdbcTenantSupport.resolveTenantId();
        int siblingSeq = Objects.requireNonNullElse(safeRecord.getSiblingSeq(), 0);
        Timestamp now = Timestamp.from(Instant.now());
        for (int attempt = 0; attempt < APPEND_RETRY_LIMIT; attempt++) {
            try {
                jdbcTemplate.update(SQL_INSERT_MESSAGE,
                        id,
                        conversationId,
                        userId,
                        safeRecord.getRole(),
                        safeRecord.getContent(),
                        safeRecord.getAgentRunId(),
                        safeRecord.getThinkingContent(),
                        safeRecord.getThinkingDuration(),
                        safeRecord.getParentId(),
                        Objects.requireNonNullElse(safeRecord.getActive(), 1),
                        safeRecord.getBranchRootId(),
                        siblingSeq,
                        now,
                        now,
                        tenantId);
                return id;
            } catch (DuplicateKeyException ex) {
                if (attempt + 1 >= APPEND_RETRY_LIMIT) {
                    throw ex;
                }
                siblingSeq = nextSiblingSeq(conversationId, userId, tenantId, safeRecord.getParentId());
            }
        }
        return id;
    }

    @Override
    public List<ConversationMessageRecord> listSiblings(String conversationId, String userId, Long parentId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return List.of();
        }
        if (parentId == null) {
            return jdbcTemplate.query(SQL_LIST_ROOT_SIBLINGS, this::mapMessage,
                    Long.parseLong(conversationId), Long.parseLong(userId), JdbcTenantSupport.resolveTenantId());
        }
        return jdbcTemplate.query(SQL_LIST_CHILD_SIBLINGS, this::mapMessage,
                Long.parseLong(conversationId), Long.parseLong(userId), JdbcTenantSupport.resolveTenantId(), parentId);
    }

    @Override
    public List<ConversationMessageRecord> listTree(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_TREE, this::mapMessage,
                Long.parseLong(conversationId), Long.parseLong(userId), JdbcTenantSupport.resolveTenantId());
    }

    @Override
    public void setActivePath(String conversationId, String userId, Set<Long> activeIds) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return;
        }
        Timestamp now = Timestamp.from(Instant.now());
        long cid = Long.parseLong(conversationId);
        long uid = Long.parseLong(userId);
        String tenantId = JdbcTenantSupport.resolveTenantId();
        jdbcTemplate.update(SQL_CLEAR_ACTIVE_PATH, now, cid, uid, tenantId);
        if (activeIds == null || activeIds.isEmpty()) {
            return;
        }
        List<Object> args = new ArrayList<>();
        args.add(now);
        args.add(cid);
        args.add(uid);
        args.add(tenantId);
        StringJoiner placeholders = new StringJoiner(",");
        for (Long activeId : activeIds) {
            placeholders.add("?");
            args.add(activeId);
        }
        jdbcTemplate.update("""
                UPDATE t_message
                SET active = 1, update_time = ?
                WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?
                  AND id IN (%s)
                """.formatted(placeholders), args.toArray());
    }

    @Override
    public ConversationBranchCursor upsertCursor(String conversationId, String userId, Long leafMessageId) {
        if (!hasText(conversationId) || !hasText(userId) || leafMessageId == null) {
            throw new IllegalArgumentException("conversationId, userId and leafMessageId must not be blank");
        }
        long cid = Long.parseLong(conversationId);
        long uid = Long.parseLong(userId);
        String tenantId = JdbcTenantSupport.resolveTenantId();
        Timestamp now = Timestamp.from(Instant.now());
        int updated = jdbcTemplate.update(SQL_UPDATE_BRANCH_CURSOR, leafMessageId, now, tenantId, cid, uid);
        if (updated == 0) {
            jdbcTemplate.update(
                    SQL_INSERT_BRANCH_CURSOR,
                    JdbcMemorySupport.nextId(),
                    tenantId,
                    cid,
                    uid,
                    leafMessageId,
                    now,
                    now);
        }
        return findCursor(conversationId, userId).orElseThrow();
    }

    @Override
    public Optional<ConversationBranchCursor> findCursor(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return Optional.empty();
        }
        List<ConversationBranchCursor> cursors = jdbcTemplate.query(
                SQL_FIND_BRANCH_CURSOR,
                this::mapCursor,
                JdbcTenantSupport.resolveTenantId(),
                Long.parseLong(conversationId),
                Long.parseLong(userId));
        return cursors.stream().findFirst();
    }

    private int nextSiblingSeq(long conversationId, long userId, String tenantId, Long parentId) {
        Integer next = parentId == null
                ? jdbcTemplate.queryForObject(SQL_NEXT_ROOT_SIBLING_SEQ, Integer.class, conversationId, userId, tenantId)
                : jdbcTemplate.queryForObject(
                        SQL_NEXT_CHILD_SIBLING_SEQ, Integer.class, conversationId, userId, tenantId, parentId);
        return Objects.requireNonNullElse(next, 0);
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
        record.setUserId(resultSet.getString("user_id"));
        record.setRole(resultSet.getString("role"));
        record.setContent(resultSet.getString("content"));
        record.setAgentRunId(resultSet.getString("agent_run_id"));
        record.setThinkingContent(resultSet.getString("thinking_content"));
        record.setThinkingDuration(resultSet.getObject("thinking_duration", Integer.class));
        record.setParentId(resultSet.getObject("parent_id", Long.class));
        record.setActive(resultSet.getObject("active", Integer.class));
        record.setBranchRootId(resultSet.getObject("branch_root_id", Long.class));
        record.setSiblingSeq(resultSet.getObject("sibling_seq", Integer.class));
        record.setVote(resultSet.getObject("vote", Integer.class));
        Timestamp createTime = resultSet.getTimestamp("create_time");
        record.setCreateTime(createTime == null ? null : createTime.toInstant());
        return record;
    }

    private ConversationBranchCursor mapCursor(ResultSet resultSet, int rowNum) throws SQLException {
        Timestamp createTime = resultSet.getTimestamp("create_time");
        Timestamp updateTime = resultSet.getTimestamp("update_time");
        return ConversationBranchCursor.builder()
                .id(resultSet.getLong("id"))
                .tenantId(resultSet.getString("tenant_id"))
                .conversationId(resultSet.getString("conversation_id"))
                .userId(resultSet.getString("user_id"))
                .leafMessageId(resultSet.getLong("leaf_message_id"))
                .createTime(createTime == null ? null : createTime.toInstant())
                .updateTime(updateTime == null ? null : updateTime.toInstant())
                .build();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

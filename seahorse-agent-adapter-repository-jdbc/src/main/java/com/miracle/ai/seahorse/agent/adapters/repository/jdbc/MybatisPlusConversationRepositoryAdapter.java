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

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.entity.ConversationDO;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.mapper.ConversationMapper;
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
 * MyBatis Plus + JdbcTemplate 混合会话仓储适配器。
 *
 * <p>CRUD 操作已迁移到 MyBatis Plus，复杂 JOIN 查询保留 JdbcTemplate。
 */
public class MybatisPlusConversationRepositoryAdapter implements ConversationRepositoryPort {

    private static final String SQL_LIST_MESSAGES = """
            SELECT m.id, m.conversation_id, m.role, m.content, m.agent_run_id, m.thinking_content,
                   m.thinking_duration, m.create_time, f.vote
            FROM t_message m
            LEFT JOIN t_message_feedback f
              ON f.message_id = m.id AND f.user_id = m.user_id AND f.deleted = 0
            WHERE m.conversation_id = ? AND m.user_id = ? AND m.deleted = 0 AND m.tenant_id = ?
            ORDER BY m.create_time ASC
            """;

    private final ConversationMapper conversationMapper;
    private final JdbcTemplate jdbcTemplate;

    public MybatisPlusConversationRepositoryAdapter(ConversationMapper conversationMapper, DataSource dataSource) {
        this.conversationMapper = Objects.requireNonNull(conversationMapper);
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource));
    }

    @Override
    public Long create(String userId) {
        if (!hasText(userId)) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        long conversationId = JdbcMemorySupport.nextId();
        Timestamp now = Timestamp.from(Instant.now());

        ConversationDO entity = new ConversationDO();
        entity.setId(conversationId);
        entity.setConversationId(conversationId);
        entity.setUserId(Long.parseLong(userId));
        entity.setTitle("New conversation");
        entity.setTenantId(JdbcTenantSupport.resolveTenantId());
        entity.setLastTime(now);
        entity.setCreateTime(now);
        entity.setUpdateTime(now);
        entity.setDeleted(0);
        entity.setVersion(0);

        conversationMapper.insert(entity);
        return conversationId;
    }

    @Override
    public List<ConversationRecord> listConversations(String userId) {
        if (!hasText(userId)) {
            return List.of();
        }
        LambdaQueryWrapper<ConversationDO> wrapper = new LambdaQueryWrapper<ConversationDO>()
                .eq(ConversationDO::getUserId, Long.parseLong(userId))
                .eq(ConversationDO::getTenantId, JdbcTenantSupport.resolveTenantId())
                .select(ConversationDO::getConversationId, ConversationDO::getTitle, ConversationDO::getLastTime)
                .orderByDesc(ConversationDO::getLastTime);

        return conversationMapper.selectList(wrapper).stream()
                .map(entity -> new ConversationRecord(
                        String.valueOf(entity.getConversationId()),
                        entity.getTitle(),
                        entity.getLastTime() == null ? null : entity.getLastTime().toInstant()))
                .toList();
    }

    @Override
    public boolean rename(String conversationId, String userId, String title) {
        if (!hasText(conversationId) || !hasText(userId) || !hasText(title)) {
            return false;
        }
        LambdaUpdateWrapper<ConversationDO> wrapper = new LambdaUpdateWrapper<ConversationDO>()
                .eq(ConversationDO::getConversationId, Long.parseLong(conversationId))
                .eq(ConversationDO::getUserId, Long.parseLong(userId))
                .eq(ConversationDO::getTenantId, JdbcTenantSupport.resolveTenantId())
                .set(ConversationDO::getTitle, title)
                .set(ConversationDO::getUpdateTime, Timestamp.from(Instant.now()));

        return conversationMapper.update(null, wrapper) > 0;
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

        // Soft-delete conversation using MyBatis Plus logical delete
        LambdaUpdateWrapper<ConversationDO> wrapper = new LambdaUpdateWrapper<ConversationDO>()
                .eq(ConversationDO::getConversationId, cid)
                .eq(ConversationDO::getUserId, uid)
                .eq(ConversationDO::getTenantId, tenantId)
                .set(ConversationDO::getUpdateTime, now);

        int updated = conversationMapper.update(null, wrapper);

        // Delete related messages and summaries (still using JdbcTemplate for cross-table operations)
        jdbcTemplate.update("UPDATE t_message SET deleted = 1, update_time = ? WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?",
                now, cid, uid, tenantId);
        jdbcTemplate.update("UPDATE t_conversation_summary SET deleted = 1, update_time = ? WHERE conversation_id = ? AND user_id = ? AND deleted = 0 AND tenant_id = ?",
                now, cid, uid, tenantId);

        return updated > 0;
    }

    @Override
    public List<ConversationMessageRecord> listMessages(String conversationId, String userId) {
        if (!hasText(conversationId) || !hasText(userId)) {
            return List.of();
        }
        // Complex JOIN query stays with JdbcTemplate
        return jdbcTemplate.query(SQL_LIST_MESSAGES, this::mapMessage,
                Long.parseLong(conversationId), Long.parseLong(userId), JdbcTenantSupport.resolveTenantId());
    }

    private ConversationMessageRecord mapMessage(ResultSet resultSet, int rowNum) throws SQLException {
        ConversationMessageRecord record = new ConversationMessageRecord();
        record.setId(resultSet.getString("id"));
        record.setConversationId(resultSet.getString("conversation_id"));
        record.setRole(resultSet.getString("role"));
        record.setContent(resultSet.getString("content"));
        record.setAgentRunId(resultSet.getString("agent_run_id"));
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

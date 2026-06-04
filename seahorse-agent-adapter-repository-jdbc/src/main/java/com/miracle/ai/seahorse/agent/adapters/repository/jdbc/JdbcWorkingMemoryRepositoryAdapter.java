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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.WorkingMemoryPort;
import com.miracle.ai.seahorse.agent.adapters.repository.jdbc.JdbcTenantSupport;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class JdbcWorkingMemoryRepositoryAdapter implements WorkingMemoryPort {

    private final JdbcTemplate jdbcTemplate;

    public JdbcWorkingMemoryRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    @Override
    public Optional<MemoryRecord> findById(String id) {
        List<MemoryRecord> records = jdbcTemplate.query("""
                SELECT id, conversation_id, role, content, update_time
                FROM t_message
                WHERE id = ? AND deleted = 0 AND tenant_id = ?
                """, this::mapRecord, JdbcMemorySupport.toLongId(id), JdbcTenantSupport.resolveTenantId());
        return records.stream().findFirst();
    }

    @Override
    public List<MemoryRecord> listByConversation(String conversationId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, conversation_id, role, content, update_time
                FROM t_message
                WHERE conversation_id = ? AND deleted = 0 AND tenant_id = ?
                ORDER BY create_time DESC
                LIMIT ?
                """, this::mapRecord, JdbcMemorySupport.toLongId(conversationId), JdbcTenantSupport.resolveTenantId(), safeLimit(limit));
    }

    @Override
    public List<MemoryRecord> listByUser(String userId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, conversation_id, role, content, update_time
                FROM t_message
                WHERE user_id = ? AND deleted = 0 AND tenant_id = ?
                ORDER BY create_time DESC
                LIMIT ?
                """, this::mapRecord, JdbcMemorySupport.toLongId(userId), JdbcTenantSupport.resolveTenantId(), safeLimit(limit));
    }

    @Override
    public void save(MemoryRecord record) {
        throw new UnsupportedOperationException("working memory is written through ConversationMemoryPort");
    }

    @Override
    public boolean deleteById(String id) {
        return jdbcTemplate.update("UPDATE t_message SET deleted = 1 WHERE id = ? AND deleted = 0 AND tenant_id = ?",
                JdbcMemorySupport.toLongId(id), JdbcTenantSupport.resolveTenantId()) > 0;
    }

    private MemoryRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryRecord(
                rs.getString("id"),
                "working",
                rs.getString("role"),
                rs.getString("content"),
                Map.of("conversationId", rs.getString("conversation_id")),
                JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
    }

    private int safeLimit(int limit) {
        return limit <= 0 ? 20 : limit;
    }
}

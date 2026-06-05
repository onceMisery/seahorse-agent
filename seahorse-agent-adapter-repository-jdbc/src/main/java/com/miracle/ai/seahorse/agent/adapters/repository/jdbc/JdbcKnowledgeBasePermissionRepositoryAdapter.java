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

import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBasePermission;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBasePermissionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcKnowledgeBasePermissionRepositoryAdapter implements KnowledgeBasePermissionRepositoryPort {

    private static final String COLUMNS = """
            id, kb_id, tenant_id, user_id, permission, granted_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO t_kb_permission
            (id, kb_id, tenant_id, user_id, permission, granted_at)
            VALUES (?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_BY_KB_AND_USER = """
            SELECT %s
            FROM t_kb_permission
            WHERE kb_id = ? AND user_id = ? AND tenant_id = ?
            """.formatted(COLUMNS);
    private static final String SQL_FIND_BY_KB_ID = """
            SELECT %s
            FROM t_kb_permission
            WHERE kb_id = ? AND tenant_id = ?
            ORDER BY granted_at ASC
            """.formatted(COLUMNS);
    private static final String SQL_DELETE_BY_ID = """
            DELETE FROM t_kb_permission
            WHERE id = ? AND tenant_id = ?
            """;
    private static final String SQL_DELETE_BY_KB_ID = """
            DELETE FROM t_kb_permission
            WHERE kb_id = ? AND tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeBasePermissionRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Long save(KnowledgeBasePermission permission) {
        KnowledgeBasePermission safe = Objects.requireNonNull(permission, "permission must not be null");
        Long id = SnowflakeIds.nextId();
        String tenantId = JdbcTenantSupport.resolveTenantId();
        jdbcTemplate.update(SQL_INSERT,
                id,
                safe.kbId(),
                tenantId,
                safe.userId(),
                safe.permission(),
                toTimestamp(safe.grantedAt()));
        return id;
    }

    @Override
    public Optional<KnowledgeBasePermission> findByKbIdAndUserId(Long kbId, Long userId) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        return jdbcTemplate.query(SQL_FIND_BY_KB_AND_USER, this::mapPermission, kbId, userId, tenantId)
                .stream().findFirst();
    }

    @Override
    public List<KnowledgeBasePermission> findByKbId(Long kbId) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        return jdbcTemplate.query(SQL_FIND_BY_KB_ID, this::mapPermission, kbId, tenantId);
    }

    @Override
    public boolean deleteById(Long id) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        return jdbcTemplate.update(SQL_DELETE_BY_ID, id, tenantId) > 0;
    }

    @Override
    public int deleteByKbId(Long kbId) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        return jdbcTemplate.update(SQL_DELETE_BY_KB_ID, kbId, tenantId);
    }

    private KnowledgeBasePermission mapPermission(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeBasePermission(
                rs.getLong("id"),
                rs.getLong("kb_id"),
                rs.getString("tenant_id"),
                rs.getLong("user_id"),
                rs.getString("permission"),
                toInstant(rs.getTimestamp("granted_at")));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

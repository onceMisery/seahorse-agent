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

import com.miracle.ai.seahorse.agent.ports.outbound.admin.AdminRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.admin.ResourceSummary;
import com.miracle.ai.seahorse.agent.ports.outbound.admin.TenantDetail;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Admin 仓储适配器，绕过 TenantContext 进行跨租户管理查询。
 *
 * <p>注意：此适配器不使用 {@link JdbcTenantSupport#resolveTenantId()}，
 * 因为管理员操作需要访问所有租户的数据。
 */
public class JdbcAdminRepositoryAdapter implements AdminRepositoryPort {

    private static final String SQL_FIND_ALL_TENANTS = """
            SELECT tenant_id,
                   MIN(create_time) AS created_at,
                   MAX(update_time) AS updated_at,
                   COUNT(*) AS user_count
            FROM t_user
            WHERE deleted = 0
            GROUP BY tenant_id
            ORDER BY created_at ASC
            LIMIT ? OFFSET ?
            """;
    private static final String SQL_COUNT_TENANTS = """
            SELECT COUNT(DISTINCT tenant_id)
            FROM t_user
            WHERE deleted = 0
            """;
    private static final String SQL_FIND_TENANT_DETAIL = """
            SELECT tenant_id,
                   MIN(create_time) AS created_at,
                   MAX(update_time) AS updated_at,
                   COUNT(*) AS user_count
            FROM t_user
            WHERE deleted = 0 AND tenant_id = ?
            GROUP BY tenant_id
            """;
    private static final String SQL_FIND_USERS_BY_TENANT = """
            SELECT id, username, role, avatar, create_time, update_time
            FROM t_user
            WHERE deleted = 0 AND tenant_id = ?
            ORDER BY create_time ASC
            LIMIT ? OFFSET ?
            """;
    private static final String SQL_COUNT_USERS_BY_TENANT = """
            SELECT COUNT(1)
            FROM t_user
            WHERE deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_COUNT_KB_BY_TENANT = """
            SELECT COUNT(1)
            FROM t_knowledge_base
            WHERE deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_COUNT_AGENT_BY_TENANT = """
            SELECT COUNT(1)
            FROM sa_agent_definition
            WHERE deleted = 0 AND tenant_id = ?
            """;
    private static final String SQL_COUNT_DOC_BY_TENANT = """
            SELECT COUNT(1)
            FROM t_knowledge_document doc
            JOIN t_knowledge_base kb ON kb.id = doc.kb_id
            WHERE kb.deleted = 0 AND kb.tenant_id = ? AND doc.deleted = 0
            """;
    private static final String SQL_SUM_TOKEN_BY_TENANT = """
            SELECT COALESCE(SUM(token_used), 0)
            FROM sa_usage_rollup
            WHERE tenant_id = ?
            """;
    private static final String SQL_UPDATE_USER_STATUS = """
            UPDATE t_user
            SET deleted = 1, update_time = ?
            WHERE id = ? AND tenant_id = ? AND deleted = 0
            """;
    private static final String SQL_RESET_PASSWORD = """
            UPDATE t_user
            SET password = ?, update_time = ?
            WHERE id = ? AND tenant_id = ? AND deleted = 0
            """;
    private static final String SQL_DELETE_TENANT_USERS = """
            DELETE FROM t_user WHERE tenant_id = ?
            """;
    private static final String SQL_DELETE_TENANT_KB = """
            DELETE FROM t_knowledge_base WHERE tenant_id = ?
            """;
    private static final String SQL_DELETE_TENANT_AGENTS = """
            DELETE FROM sa_agent_definition WHERE tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcAdminRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public List<TenantDetail> findAllTenants(int page, int size, String statusFilter) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int offset = (safePage - 1) * safeSize;
        return jdbcTemplate.query(SQL_FIND_ALL_TENANTS, this::mapTenantSummary, safeSize, offset);
    }

    @Override
    public long countTenants(String statusFilter) {
        Long count = jdbcTemplate.queryForObject(SQL_COUNT_TENANTS, Long.class);
        return count != null ? count : 0L;
    }

    @Override
    public Optional<TenantDetail> findTenantDetail(String tenantId) {
        return jdbcTemplate.query(SQL_FIND_TENANT_DETAIL, this::mapTenantSummary, tenantId)
                .stream().findFirst();
    }

    @Override
    public List<Map<String, Object>> findUsersByTenant(String tenantId, int page, int size) {
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int offset = (safePage - 1) * safeSize;
        return jdbcTemplate.queryForList(SQL_FIND_USERS_BY_TENANT, tenantId, safeSize, offset);
    }

    @Override
    public long countUsersByTenant(String tenantId) {
        Long count = jdbcTemplate.queryForObject(SQL_COUNT_USERS_BY_TENANT, Long.class, tenantId);
        return count != null ? count : 0L;
    }

    @Override
    public ResourceSummary getTenantResourceSummary(String tenantId) {
        long kbCount = countForTenant(SQL_COUNT_KB_BY_TENANT, tenantId);
        long agentCount = countForTenant(SQL_COUNT_AGENT_BY_TENANT, tenantId);
        long userCount = countForTenant(SQL_COUNT_USERS_BY_TENANT, tenantId);
        long docCount = countForTenant(SQL_COUNT_DOC_BY_TENANT, tenantId);
        long tokenUsed = countForTenant(SQL_SUM_TOKEN_BY_TENANT, tenantId);
        return new ResourceSummary(kbCount, agentCount, userCount, docCount, tokenUsed);
    }

    @Override
    public boolean updateTenantStatus(String tenantId, String newStatus) {
        // 当前无独立租户表，状态更新通过对 t_user 的批量操作模拟
        if ("SUSPENDED".equalsIgnoreCase(newStatus)) {
            return jdbcTemplate.update("""
                    UPDATE t_user SET deleted = 1, update_time = ?
                    WHERE tenant_id = ? AND deleted = 0
                    """, Timestamp.from(Instant.now()), tenantId) >= 0;
        }
        // ACTIVE：恢复所有用户
        return jdbcTemplate.update("""
                UPDATE t_user SET deleted = 0, update_time = ?
                WHERE tenant_id = ? AND deleted = 1
                """, Timestamp.from(Instant.now()), tenantId) >= 0;
    }

    @Override
    public boolean deleteTenant(String tenantId) {
        jdbcTemplate.update(SQL_DELETE_TENANT_AGENTS, tenantId);
        jdbcTemplate.update(SQL_DELETE_TENANT_KB, tenantId);
        jdbcTemplate.update(SQL_DELETE_TENANT_USERS, tenantId);
        return true;
    }

    @Override
    public boolean banUser(String tenantId, Long userId) {
        return jdbcTemplate.update(SQL_UPDATE_USER_STATUS,
                Timestamp.from(Instant.now()), userId, tenantId) > 0;
    }

    @Override
    public boolean resetUserPassword(String tenantId, Long userId, String newPasswordHash) {
        return jdbcTemplate.update(SQL_RESET_PASSWORD,
                newPasswordHash, Timestamp.from(Instant.now()), userId, tenantId) > 0;
    }

    @Override
    public boolean forceLogout(String tenantId, Long userId) {
        // 当前系统使用 JWT 无状态会话，无服务端 session 表
        // 强制登出通过禁用用户实现，配合前端 token 过期
        return banUser(tenantId, userId);
    }

    private TenantDetail mapTenantSummary(ResultSet rs, int rowNum) throws SQLException {
        String tid = rs.getString("tenant_id");
        long userCount = rs.getLong("user_count");
        Instant createdAt = toInstant(rs.getTimestamp("created_at"));
        Instant updatedAt = toInstant(rs.getTimestamp("updated_at"));
        long kbCount = countForTenant(SQL_COUNT_KB_BY_TENANT, tid);
        long agentCount = countForTenant(SQL_COUNT_AGENT_BY_TENANT, tid);
        return new TenantDetail(tid, tid, "ACTIVE", "FREE", "",
                userCount, agentCount, kbCount, createdAt, updatedAt);
    }

    private long countForTenant(String sql, String tenantId) {
        try {
            Long value = jdbcTemplate.queryForObject(sql, Long.class, tenantId);
            return value != null ? value : 0L;
        } catch (Exception e) {
            return 0L;
        }
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

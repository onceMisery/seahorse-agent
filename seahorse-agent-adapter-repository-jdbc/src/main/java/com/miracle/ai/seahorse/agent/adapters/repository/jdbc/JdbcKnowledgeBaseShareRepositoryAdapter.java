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

import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBaseShare;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseShareRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcKnowledgeBaseShareRepositoryAdapter implements KnowledgeBaseShareRepositoryPort {

    private static final String COLUMNS = """
            id, kb_id, tenant_id, share_token, password_hash, expires_at,
            max_access_count, current_access_count, created_at
            """;
    private static final String SQL_INSERT = """
            INSERT INTO t_kb_share
            (id, kb_id, tenant_id, share_token, password_hash, expires_at,
             max_access_count, current_access_count, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_BY_TOKEN = """
            SELECT %s
            FROM t_kb_share
            WHERE share_token = ?
            """.formatted(COLUMNS);
    private static final String SQL_FIND_BY_KB_ID = """
            SELECT %s
            FROM t_kb_share
            WHERE kb_id = ? AND tenant_id = ?
            ORDER BY created_at DESC
            """.formatted(COLUMNS);
    private static final String SQL_INCREMENT_ACCESS_COUNT = """
            UPDATE t_kb_share
            SET current_access_count = current_access_count + 1
            WHERE id = ?
            """;
    private static final String SQL_DELETE_EXPIRED = """
            DELETE FROM t_kb_share
            WHERE expires_at < ?
            """;
    private static final String SQL_DELETE_BY_ID = """
            DELETE FROM t_kb_share
            WHERE id = ? AND tenant_id = ?
            """;
    private static final String SQL_DELETE_BY_KB_ID = """
            DELETE FROM t_kb_share
            WHERE kb_id = ? AND tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeBaseShareRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Long save(KnowledgeBaseShare share) {
        KnowledgeBaseShare safe = Objects.requireNonNull(share, "share must not be null");
        Long id = SnowflakeIds.nextId();
        String tenantId = JdbcTenantSupport.resolveTenantId();
        jdbcTemplate.update(SQL_INSERT,
                id,
                safe.kbId(),
                tenantId,
                safe.shareToken(),
                safe.passwordHash(),
                toTimestamp(safe.expiresAt()),
                safe.maxAccessCount(),
                safe.currentAccessCount(),
                toTimestamp(safe.createdAt()));
        return id;
    }

    @Override
    public Optional<KnowledgeBaseShare> findByToken(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_TOKEN, this::mapShare, token.trim())
                .stream().findFirst();
    }

    @Override
    public List<KnowledgeBaseShare> findByKbId(Long kbId) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        return jdbcTemplate.query(SQL_FIND_BY_KB_ID, this::mapShare, kbId, tenantId);
    }

    @Override
    public boolean incrementAccessCount(Long id) {
        return jdbcTemplate.update(SQL_INCREMENT_ACCESS_COUNT, id) > 0;
    }

    @Override
    public int deleteExpired(Instant now) {
        return jdbcTemplate.update(SQL_DELETE_EXPIRED, toTimestamp(now));
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

    private KnowledgeBaseShare mapShare(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeBaseShare(
                rs.getLong("id"),
                rs.getLong("kb_id"),
                rs.getString("tenant_id"),
                rs.getString("share_token"),
                rs.getString("password_hash"),
                toInstant(rs.getTimestamp("expires_at")),
                rs.getInt("max_access_count"),
                rs.getInt("current_access_count"),
                toInstant(rs.getTimestamp("created_at")));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

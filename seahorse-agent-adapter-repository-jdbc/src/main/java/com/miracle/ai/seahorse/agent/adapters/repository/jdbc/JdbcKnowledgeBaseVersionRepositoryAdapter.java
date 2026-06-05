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

import com.miracle.ai.seahorse.agent.kernel.domain.knowledge.KnowledgeBaseVersion;
import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.knowledge.KnowledgeBaseVersionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcKnowledgeBaseVersionRepositoryAdapter implements KnowledgeBaseVersionRepositoryPort {

    private static final String COLUMNS = """
            id, kb_id, tenant_id, version_number, snapshot_json, created_by, created_at, change_description
            """;
    private static final String SQL_INSERT = """
            INSERT INTO t_kb_version
            (id, kb_id, tenant_id, version_number, snapshot_json, created_by, created_at, change_description)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_FIND_BY_KB_ID = """
            SELECT %s
            FROM t_kb_version
            WHERE kb_id = ? AND tenant_id = ?
            ORDER BY version_number DESC
            LIMIT ? OFFSET ?
            """.formatted(COLUMNS);
    private static final String SQL_COUNT_BY_KB_ID = """
            SELECT COUNT(1)
            FROM t_kb_version
            WHERE kb_id = ? AND tenant_id = ?
            """;
    private static final String SQL_FIND_BY_KB_ID_AND_VERSION = """
            SELECT %s
            FROM t_kb_version
            WHERE kb_id = ? AND version_number = ? AND tenant_id = ?
            """.formatted(COLUMNS);
    private static final String SQL_MAX_VERSION = """
            SELECT MAX(version_number)
            FROM t_kb_version
            WHERE kb_id = ? AND tenant_id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcKnowledgeBaseVersionRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Long save(KnowledgeBaseVersion version) {
        KnowledgeBaseVersion safe = Objects.requireNonNull(version, "version must not be null");
        Long id = SnowflakeIds.nextId();
        String tenantId = JdbcTenantSupport.resolveTenantId();
        jdbcTemplate.update(SQL_INSERT,
                id,
                safe.kbId(),
                tenantId,
                safe.versionNumber(),
                safe.snapshotJson(),
                safe.createdBy(),
                toTimestamp(safe.createdAt()),
                safe.changeDescription());
        return id;
    }

    @Override
    public List<KnowledgeBaseVersion> findByKbId(Long kbId, int page, int size) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, size);
        int offset = (safePage - 1) * safeSize;
        return jdbcTemplate.query(SQL_FIND_BY_KB_ID, this::mapVersion, kbId, tenantId, safeSize, offset);
    }

    @Override
    public long countByKbId(Long kbId) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        Long count = jdbcTemplate.queryForObject(SQL_COUNT_BY_KB_ID, Long.class, kbId, tenantId);
        return count != null ? count : 0L;
    }

    @Override
    public Optional<KnowledgeBaseVersion> findByKbIdAndVersion(Long kbId, int versionNumber) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        return jdbcTemplate.query(SQL_FIND_BY_KB_ID_AND_VERSION, this::mapVersion, kbId, versionNumber, tenantId)
                .stream().findFirst();
    }

    @Override
    public Optional<Integer> findMaxVersionNumber(Long kbId) {
        String tenantId = JdbcTenantSupport.resolveTenantId();
        Integer max = jdbcTemplate.queryForObject(SQL_MAX_VERSION, Integer.class, kbId, tenantId);
        return Optional.ofNullable(max);
    }

    private KnowledgeBaseVersion mapVersion(ResultSet rs, int rowNum) throws SQLException {
        return new KnowledgeBaseVersion(
                rs.getLong("id"),
                rs.getLong("kb_id"),
                rs.getString("tenant_id"),
                rs.getInt("version_number"),
                rs.getString("snapshot_json"),
                rs.getString("created_by"),
                toInstant(rs.getTimestamp("created_at")),
                rs.getString("change_description"));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}

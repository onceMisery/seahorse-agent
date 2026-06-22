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

import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.rolecard.RoleCardRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC repository adapter for runtime role cards.
 */
public class JdbcRoleCardRepositoryAdapter implements RoleCardRepositoryPort {

    private static final String COLUMNS = """
            id, tenant_id, user_id, name, definition, avatar_ref, higher_perm, enabled,
            share_scope, approval_status, published,
            asset_source, preset_key, preset_version, readonly,
            create_time, update_time, deleted
            """;

    private static final String SQL_INSERT = """
            INSERT INTO sa_role_card
            (id, tenant_id, user_id, name, definition, avatar_ref, higher_perm, enabled,
             share_scope, approval_status, published, asset_source, preset_key, preset_version, readonly,
             create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;

    private static final String SQL_UPDATE = """
            UPDATE sa_role_card
            SET name = ?,
                definition = ?,
                avatar_ref = ?,
                higher_perm = ?,
                enabled = ?,
                share_scope = ?,
                approval_status = ?,
                published = ?,
                asset_source = ?,
                preset_key = ?,
                preset_version = ?,
                readonly = ?,
                update_time = ?,
                deleted = 0
            WHERE id = ? AND user_id = ? AND tenant_id = ?
            """;

    private static final String SQL_LIST_BY_USER = """
            SELECT %s
            FROM sa_role_card
            WHERE (user_id = ? OR asset_source = 'SYSTEM') AND tenant_id = ? AND deleted = 0
            ORDER BY asset_source DESC, create_time ASC, id ASC
            """.formatted(COLUMNS);

    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_role_card
            WHERE id = ? AND (user_id = ? OR asset_source = 'SYSTEM') AND tenant_id = ? AND deleted = 0
            """.formatted(COLUMNS);

    private static final String SQL_FIND_ENABLED = """
            SELECT %s
            FROM sa_role_card
            WHERE user_id = ? AND tenant_id = ? AND enabled = 1 AND deleted = 0
            ORDER BY update_time DESC, id DESC
            LIMIT 1
            """.formatted(COLUMNS);

    private static final String SQL_EXISTS = """
            SELECT COUNT(1)
            FROM sa_role_card
            WHERE id = ? AND user_id = ? AND tenant_id = ?
            """;

    private static final String SQL_DISABLE_ALL = """
            UPDATE sa_role_card
            SET enabled = 0, update_time = ?
            WHERE user_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String SQL_SET_ENABLED = """
            UPDATE sa_role_card
            SET enabled = ?, update_time = ?
            WHERE id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String SQL_DELETE = """
            UPDATE sa_role_card
            SET deleted = 1, enabled = 0, update_time = ?
            WHERE id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcRoleCardRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public List<RoleCardRecord> listByUser(String userId) {
        return jdbcTemplate.query(SQL_LIST_BY_USER, this::mapRecord, requireText(userId, "userId"), tenantId());
    }

    @Override
    public Optional<RoleCardRecord> findById(String userId, Long id) {
        if (id == null || !hasText(userId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::mapRecord, id, userId.trim(), tenantId())
                .stream()
                .findFirst();
    }

    @Override
    public Optional<RoleCardRecord> findEnabled(String userId) {
        if (!hasText(userId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_ENABLED, this::mapRecord, userId.trim(), tenantId())
                .stream()
                .findFirst();
    }

    @Override
    public Long save(RoleCardRecord record) {
        RoleCardRecord safeRecord = Objects.requireNonNull(record, "record must not be null");
        Long id = safeRecord.getId() == null ? JdbcMemorySupport.nextId() : safeRecord.getId();
        String userId = requireText(safeRecord.getUserId(), "userId");
        String tenantId = hasText(safeRecord.getTenantId()) ? safeRecord.getTenantId().trim() : tenantId();
        String name = requireText(safeRecord.getName(), "name");
        String definition = requireText(safeRecord.getDefinition(), "definition");
        Integer higherPerm = flag(safeRecord.getHigherPerm());
        Integer enabled = flag(safeRecord.getEnabled());
        String shareScope = enumText(safeRecord.getShareScope(), "PRIVATE");
        String approvalStatus = enumText(safeRecord.getApprovalStatus(), "PENDING");
        Integer published = flag(safeRecord.getPublished());
        String assetSource = enumText(safeRecord.getAssetSource(), "USER");
        String presetKey = blankToNull(safeRecord.getPresetKey());
        Integer presetVersion = safeRecord.getPresetVersion() == null ? 1 : safeRecord.getPresetVersion();
        Integer readonly = flag(safeRecord.getReadonly());
        Timestamp now = Timestamp.from(Instant.now());

        if (exists(id, userId, tenantId)) {
            jdbcTemplate.update(SQL_UPDATE,
                    name,
                    definition,
                    blankToNull(safeRecord.getAvatarRef()),
                    higherPerm,
                    enabled,
                    shareScope,
                    approvalStatus,
                    published,
                    assetSource,
                    presetKey,
                    presetVersion,
                    readonly,
                    now,
                    id,
                    userId,
                    tenantId);
        } else {
            jdbcTemplate.update(SQL_INSERT,
                    id,
                    tenantId,
                    userId,
                    name,
                    definition,
                    blankToNull(safeRecord.getAvatarRef()),
                    higherPerm,
                    enabled,
                    shareScope,
                    approvalStatus,
                    published,
                    assetSource,
                    presetKey,
                    presetVersion,
                    readonly,
                    now,
                    now);
        }
        safeRecord.setId(id);
        safeRecord.setTenantId(tenantId);
        safeRecord.setHigherPerm(higherPerm);
        safeRecord.setEnabled(enabled);
        safeRecord.setShareScope(shareScope);
        safeRecord.setApprovalStatus(approvalStatus);
        safeRecord.setPublished(published);
        safeRecord.setAssetSource(assetSource);
        safeRecord.setPresetKey(presetKey);
        safeRecord.setPresetVersion(presetVersion);
        safeRecord.setReadonly(readonly);
        safeRecord.setDeleted(0);
        return id;
    }

    @Override
    public void disableAll(String userId) {
        jdbcTemplate.update(SQL_DISABLE_ALL, Timestamp.from(Instant.now()), requireText(userId, "userId"), tenantId());
    }

    @Override
    public void setEnabled(String userId, Long id, boolean enabled) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update(SQL_SET_ENABLED, enabled ? 1 : 0, Timestamp.from(Instant.now()),
                id, requireText(userId, "userId"), tenantId());
    }

    @Override
    public void delete(String userId, Long id) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update(SQL_DELETE, Timestamp.from(Instant.now()), id, requireText(userId, "userId"), tenantId());
    }

    private boolean exists(Long id, String userId, String tenantId) {
        Long count = jdbcTemplate.queryForObject(SQL_EXISTS, Long.class, id, userId, tenantId);
        return count != null && count > 0;
    }

    private RoleCardRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        RoleCardRecord record = new RoleCardRecord();
        record.setId(resultSet.getLong("id"));
        record.setTenantId(resultSet.getString("tenant_id"));
        record.setUserId(resultSet.getString("user_id"));
        record.setName(resultSet.getString("name"));
        record.setDefinition(resultSet.getString("definition"));
        record.setAvatarRef(resultSet.getString("avatar_ref"));
        record.setHigherPerm(resultSet.getInt("higher_perm"));
        record.setEnabled(resultSet.getInt("enabled"));
        record.setShareScope(resultSet.getString("share_scope"));
        record.setApprovalStatus(resultSet.getString("approval_status"));
        record.setPublished(resultSet.getInt("published"));
        record.setAssetSource(resultSet.getString("asset_source"));
        record.setPresetKey(resultSet.getString("preset_key"));
        record.setPresetVersion(resultSet.getInt("preset_version"));
        record.setReadonly(resultSet.getInt("readonly"));
        record.setCreateTime(toInstant(resultSet.getTimestamp("create_time")));
        record.setUpdateTime(toInstant(resultSet.getTimestamp("update_time")));
        record.setDeleted(resultSet.getInt("deleted"));
        return record;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String tenantId() {
        return JdbcTenantSupport.resolveTenantId();
    }

    private Integer flag(Integer value) {
        return Integer.valueOf(1).equals(value) ? 1 : 0;
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private String enumText(String value, String defaultValue) {
        return hasText(value) ? value.trim().toUpperCase() : defaultValue;
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

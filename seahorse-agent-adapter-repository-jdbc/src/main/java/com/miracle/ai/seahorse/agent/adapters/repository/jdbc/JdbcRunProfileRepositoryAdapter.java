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

import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.runprofile.RunProfileToolBindingRecord;
import lombok.RequiredArgsConstructor;
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
 * JDBC repository adapter for reusable run profiles.
 */
@RequiredArgsConstructor
public class JdbcRunProfileRepositoryAdapter implements RunProfileRepositoryPort {

    private static final String PROFILE_COLUMNS = """
            id, tenant_id, user_id, name, description, role_card_id, executor_engine,
            executor_config_json, model_config_json, memory_scope_json, guardrail_config_json,
            enabled, create_time, update_time, deleted
            """;

    private static final String TOOL_COLUMNS = """
            id, tenant_id, profile_id, tool_id, provider, enabled, create_time, update_time, deleted
            """;

    private static final String SQL_INSERT_PROFILE = """
            INSERT INTO sa_run_profile
            (id, tenant_id, user_id, name, description, role_card_id, executor_engine,
             executor_config_json, model_config_json, memory_scope_json, guardrail_config_json,
             enabled, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;

    private static final String SQL_UPDATE_PROFILE = """
            UPDATE sa_run_profile
            SET name = ?,
                description = ?,
                role_card_id = ?,
                executor_engine = ?,
                executor_config_json = ?,
                model_config_json = ?,
                memory_scope_json = ?,
                guardrail_config_json = ?,
                enabled = ?,
                update_time = ?,
                deleted = 0
            WHERE id = ? AND user_id = ? AND tenant_id = ?
            """;

    private static final String SQL_EXISTS_PROFILE = """
            SELECT COUNT(1)
            FROM sa_run_profile
            WHERE id = ? AND user_id = ? AND tenant_id = ?
            """;

    private static final String SQL_LIST_BY_USER = """
            SELECT %s
            FROM sa_run_profile
            WHERE user_id = ? AND tenant_id = ? AND deleted = 0
            ORDER BY enabled DESC, update_time DESC, id DESC
            """.formatted(PROFILE_COLUMNS);

    private static final String SQL_FIND_BY_ID = """
            SELECT %s
            FROM sa_run_profile
            WHERE id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0
            """.formatted(PROFILE_COLUMNS);

    private static final String SQL_DISABLE_ALL = """
            UPDATE sa_run_profile
            SET enabled = 0, update_time = ?
            WHERE user_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String SQL_SET_ENABLED = """
            UPDATE sa_run_profile
            SET enabled = ?, update_time = ?
            WHERE id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String SQL_DELETE = """
            UPDATE sa_run_profile
            SET deleted = 1, enabled = 0, update_time = ?
            WHERE id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String SQL_SOFT_DELETE_TOOLS = """
            UPDATE sa_run_profile_tool
            SET deleted = 1, enabled = 0, update_time = ?
            WHERE profile_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String SQL_INSERT_TOOL = """
            INSERT INTO sa_run_profile_tool
            (id, tenant_id, profile_id, tool_id, provider, enabled, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;

    private static final String SQL_LIST_TOOLS = """
            SELECT %s
            FROM sa_run_profile_tool
            WHERE profile_id = ? AND tenant_id = ? AND deleted = 0
            ORDER BY create_time ASC, id ASC
            """.formatted(TOOL_COLUMNS);

    private static final String SQL_APPLY_TO_CONVERSATION = """
            UPDATE t_conversation
            SET run_profile_id = ?, update_time = ?
            WHERE conversation_id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private static final String SQL_FIND_APPLIED_PROFILE = """
            SELECT run_profile_id
            FROM t_conversation
            WHERE conversation_id = ? AND user_id = ? AND tenant_id = ? AND deleted = 0
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcRunProfileRepositoryAdapter(DataSource dataSource) {
        this(new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null")));
    }

    @Override
    public List<RunProfileRecord> listByUser(String userId) {
        return jdbcTemplate.query(SQL_LIST_BY_USER, this::mapProfile, requireUserId(userId), tenantId());
    }

    @Override
    public Optional<RunProfileRecord> findById(String userId, Long id) {
        if (id == null || !hasText(userId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_ID, this::mapProfile, id, userId.trim(), tenantId())
                .stream()
                .findFirst();
    }

    @Override
    public Long save(RunProfileRecord record) {
        RunProfileRecord safeRecord = Objects.requireNonNull(record, "record must not be null");
        Long id = safeRecord.getId() == null ? JdbcMemorySupport.nextId() : safeRecord.getId();
        String userId = requireUserId(safeRecord.getUserId());
        String tenantId = hasText(safeRecord.getTenantId()) ? safeRecord.getTenantId().trim() : tenantId();
        String name = requireText(safeRecord.getName(), "name");
        String executorEngine = hasText(safeRecord.getExecutorEngine())
                ? safeRecord.getExecutorEngine().trim()
                : "kernel";
        Integer enabled = flag(safeRecord.getEnabled());
        Timestamp now = Timestamp.from(Instant.now());

        if (exists(id, userId, tenantId)) {
            jdbcTemplate.update(SQL_UPDATE_PROFILE,
                    name,
                    blankToNull(safeRecord.getDescription()),
                    safeRecord.getRoleCardId(),
                    executorEngine,
                    blankToNull(safeRecord.getExecutorConfigJson()),
                    blankToNull(safeRecord.getModelConfigJson()),
                    blankToNull(safeRecord.getMemoryScopeJson()),
                    blankToNull(safeRecord.getGuardrailConfigJson()),
                    enabled,
                    now,
                    id,
                    userId,
                    tenantId);
        } else {
            jdbcTemplate.update(SQL_INSERT_PROFILE,
                    id,
                    tenantId,
                    userId,
                    name,
                    blankToNull(safeRecord.getDescription()),
                    safeRecord.getRoleCardId(),
                    executorEngine,
                    blankToNull(safeRecord.getExecutorConfigJson()),
                    blankToNull(safeRecord.getModelConfigJson()),
                    blankToNull(safeRecord.getMemoryScopeJson()),
                    blankToNull(safeRecord.getGuardrailConfigJson()),
                    enabled,
                    now,
                    now);
        }

        safeRecord.setId(id);
        safeRecord.setTenantId(tenantId);
        safeRecord.setUserId(userId);
        safeRecord.setName(name);
        safeRecord.setExecutorEngine(executorEngine);
        safeRecord.setEnabled(enabled);
        safeRecord.setDeleted(0);
        return id;
    }

    @Override
    public void replaceTools(Long profileId, List<RunProfileToolBindingRecord> tools) {
        if (profileId == null) {
            return;
        }
        String tenantId = tenantId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_SOFT_DELETE_TOOLS, now, profileId, tenantId);
        List<RunProfileToolBindingRecord> safeTools = tools == null ? List.of() : tools;
        for (RunProfileToolBindingRecord tool : safeTools) {
            if (tool == null || !hasText(tool.getToolId()) || !hasText(tool.getProvider())) {
                continue;
            }
            Long id = tool.getId() == null ? JdbcMemorySupport.nextId() : tool.getId();
            jdbcTemplate.update(SQL_INSERT_TOOL,
                    id,
                    tenantId,
                    profileId,
                    tool.getToolId().trim(),
                    tool.getProvider().trim(),
                    flag(tool.getEnabled()),
                    now,
                    now);
            tool.setId(id);
            tool.setTenantId(tenantId);
            tool.setProfileId(profileId);
            tool.setDeleted(0);
        }
    }

    @Override
    public List<RunProfileToolBindingRecord> listTools(Long profileId) {
        if (profileId == null) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_TOOLS, this::mapTool, profileId, tenantId());
    }

    @Override
    public void disableAll(String userId) {
        jdbcTemplate.update(SQL_DISABLE_ALL, Timestamp.from(Instant.now()), requireUserId(userId), tenantId());
    }

    @Override
    public void setEnabled(String userId, Long id, boolean enabled) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update(SQL_SET_ENABLED, enabled ? 1 : 0, Timestamp.from(Instant.now()),
                id, requireUserId(userId), tenantId());
    }

    @Override
    public void delete(String userId, Long id) {
        if (id == null) {
            return;
        }
        jdbcTemplate.update(SQL_DELETE, Timestamp.from(Instant.now()), id, requireUserId(userId), tenantId());
    }

    @Override
    public void applyToConversation(String userId, String conversationId, Long profileId) {
        if (profileId == null) {
            throw new IllegalArgumentException("profileId must not be null");
        }
        Long convIdLong = parseLongOrNull(conversationId);
        Long userIdLong = parseLongOrNull(userId);
        if (convIdLong == null || userIdLong == null) {
            return; // non-numeric IDs cannot match BIGINT columns
        }
        jdbcTemplate.update(SQL_APPLY_TO_CONVERSATION,
                profileId,
                Timestamp.from(Instant.now()),
                convIdLong,
                userIdLong,
                tenantId());
    }

    @Override
    public Optional<Long> findAppliedProfileId(String userId, String conversationId) {
        if (!hasText(userId) || !hasText(conversationId)) {
            return Optional.empty();
        }
        Long convIdLong = parseLongOrNull(conversationId);
        Long userIdLong = parseLongOrNull(userId);
        if (convIdLong == null || userIdLong == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_APPLIED_PROFILE,
                        (rs, rowNum) -> nullableLong(rs, "run_profile_id"),
                        convIdLong,
                        userIdLong,
                        tenantId())
                .stream()
                .filter(Objects::nonNull)
                .findFirst();
    }

    private boolean exists(Long id, String userId, String tenantId) {
        Long count = jdbcTemplate.queryForObject(SQL_EXISTS_PROFILE, Long.class, id, userId, tenantId);
        return count != null && count > 0;
    }

    private static Long parseLongOrNull(String value) {
        if (value == null || value.isBlank()) return null;
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private RunProfileRecord mapProfile(ResultSet resultSet, int rowNumber) throws SQLException {
        RunProfileRecord record = new RunProfileRecord();
        record.setId(resultSet.getLong("id"));
        record.setTenantId(resultSet.getString("tenant_id"));
        record.setUserId(resultSet.getString("user_id"));
        record.setName(resultSet.getString("name"));
        record.setDescription(resultSet.getString("description"));
        record.setRoleCardId(nullableLong(resultSet, "role_card_id"));
        record.setExecutorEngine(resultSet.getString("executor_engine"));
        record.setExecutorConfigJson(resultSet.getString("executor_config_json"));
        record.setModelConfigJson(resultSet.getString("model_config_json"));
        record.setMemoryScopeJson(resultSet.getString("memory_scope_json"));
        record.setGuardrailConfigJson(resultSet.getString("guardrail_config_json"));
        record.setEnabled(resultSet.getInt("enabled"));
        record.setCreateTime(toInstant(resultSet.getTimestamp("create_time")));
        record.setUpdateTime(toInstant(resultSet.getTimestamp("update_time")));
        record.setDeleted(resultSet.getInt("deleted"));
        return record;
    }

    private RunProfileToolBindingRecord mapTool(ResultSet resultSet, int rowNumber) throws SQLException {
        return RunProfileToolBindingRecord.builder()
                .id(resultSet.getLong("id"))
                .tenantId(resultSet.getString("tenant_id"))
                .profileId(resultSet.getLong("profile_id"))
                .toolId(resultSet.getString("tool_id"))
                .provider(resultSet.getString("provider"))
                .enabled(resultSet.getInt("enabled"))
                .createTime(toInstant(resultSet.getTimestamp("create_time")))
                .updateTime(toInstant(resultSet.getTimestamp("update_time")))
                .deleted(resultSet.getInt("deleted"))
                .build();
    }

    private Long nullableLong(ResultSet resultSet, String column) throws SQLException {
        long value = resultSet.getLong(column);
        return resultSet.wasNull() ? null : value;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String tenantId() {
        return JdbcTenantSupport.resolveTenantId();
    }

    private String requireUserId(String value) {
        return requireText(value, "userId");
    }

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private Integer flag(Integer value) {
        return Integer.valueOf(1).equals(value) ? 1 : 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

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
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserCreateValues;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserPage;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.auth.UserUpdateValues;
import org.springframework.dao.EmptyResultDataAccessException;
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
public class JdbcUserRepositoryAdapter implements UserRepositoryPort {

    private static final String SELECT_COLUMNS = "id, username, password, role, avatar, tenant_id, create_time, update_time";

    private final JdbcTemplate jdbcTemplate;

    public JdbcUserRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Optional<UserRecord> findById(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT " + SELECT_COLUMNS + " FROM t_user WHERE id = ? AND deleted = 0 AND tenant_id = ?",
                    this::mapUser, id, JdbcTenantSupport.resolveTenantId()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<UserRecord> findByUsername(String username) {
        if (!hasText(username)) {
            return Optional.empty();
        }
        try {
            return Optional.ofNullable(jdbcTemplate.queryForObject(
                    "SELECT " + SELECT_COLUMNS + " FROM t_user WHERE username = ? AND deleted = 0 AND tenant_id = ?",
                    this::mapUser, username, JdbcTenantSupport.resolveTenantId()));
        } catch (EmptyResultDataAccessException ex) {
            return Optional.empty();
        }
    }

    @Override
    public boolean usernameExists(String username, Long excludedId) {
        if (!hasText(username)) {
            return false;
        }
        StringBuilder sql = new StringBuilder("SELECT COUNT(1) FROM t_user WHERE username = ? AND deleted = 0");
        List<Object> args = new ArrayList<>();
        args.add(username);
        if (excludedId != null) {
            sql.append(" AND id <> ?");
            args.add(excludedId);
        }
        sql.append(" AND tenant_id = ?");
        args.add(JdbcTenantSupport.resolveTenantId());
        Integer count = jdbcTemplate.queryForObject(sql.toString(), Integer.class, args.toArray());
        return count != null && count > 0;
    }

    @Override
    public UserPage page(long current, long size, String keyword) {
        long actualCurrent = Math.max(current, 1);
        long actualSize = size <= 0 ? 10 : size;
        String normalizedKeyword = trimToNull(keyword);
        List<Object> args = new ArrayList<>();
        String whereSql = buildWhereSql(normalizedKeyword, args);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_user " + whereSql, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(actualSize);
        pageArgs.add((actualCurrent - 1) * actualSize);
        List<UserRecord> records = jdbcTemplate.query(
                "SELECT " + SELECT_COLUMNS + " FROM t_user " + whereSql
                        + " ORDER BY update_time DESC LIMIT ? OFFSET ?",
                this::mapUser, pageArgs.toArray());
        long safeTotal = total == null ? 0 : total;
        long pages = safeTotal == 0 ? 0 : (safeTotal + actualSize - 1) / actualSize;
        return new UserPage(records, safeTotal, actualSize, actualCurrent, pages);
    }

    @Override
    public Long create(UserCreateValues values) {
        UserCreateValues safeValues = Objects.requireNonNull(values, "values must not be null");
        long id = JdbcMemorySupport.nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update("""
                INSERT INTO t_user (id, username, password, avatar, role, create_time, update_time, deleted, tenant_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)
                """, id, safeValues.username(), safeValues.password(), safeValues.avatar(),
                safeValues.role(), now, now, JdbcTenantSupport.resolveTenantId());
        return id;
    }

    @Override
    public boolean update(Long id, UserUpdateValues values) {
        if (id == null) {
            return false;
        }
        UserUpdateValues safeValues = Objects.requireNonNull(values, "values must not be null");
        List<String> sets = new ArrayList<>();
        List<Object> args = new ArrayList<>();
        addSet(sets, args, "username", safeValues.username());
        addSet(sets, args, "password", safeValues.password());
        addSet(sets, args, "role", safeValues.role());
        if (safeValues.avatar() != null) {
            sets.add("avatar = ?");
            args.add(trimToNull(safeValues.avatar()));
        }
        sets.add("update_time = ?");
        args.add(Timestamp.from(Instant.now()));
        args.add(id);
        args.add(JdbcTenantSupport.resolveTenantId());
        int updated = jdbcTemplate.update("UPDATE t_user SET " + String.join(", ", sets)
                + " WHERE id = ? AND deleted = 0 AND tenant_id = ?", args.toArray());
        return updated > 0;
    }

    @Override
    public boolean delete(Long id) {
        if (id == null) {
            return false;
        }
        int updated = jdbcTemplate.update("""
                UPDATE t_user SET deleted = 1, update_time = ? WHERE id = ? AND deleted = 0 AND tenant_id = ?
                """, Timestamp.from(Instant.now()), id, JdbcTenantSupport.resolveTenantId());
        return updated > 0;
    }

    private String buildWhereSql(String keyword, List<Object> args) {
        StringBuilder where = new StringBuilder("WHERE deleted = 0");
        if (hasText(keyword)) {
            where.append(" AND (username LIKE ? OR role LIKE ?)");
            String pattern = "%" + keyword + "%";
            args.add(pattern);
            args.add(pattern);
        }
        where.append(" AND tenant_id = ?");
        args.add(JdbcTenantSupport.resolveTenantId());
        return where.toString();
    }

    private void addSet(List<String> sets, List<Object> args, String column, String value) {
        if (value == null) {
            return;
        }
        sets.add(column + " = ?");
        args.add(value);
    }

    private UserRecord mapUser(ResultSet resultSet, int rowNum) throws SQLException {
        return new UserRecord(
                resultSet.getLong("id"),
                resultSet.getString("username"),
                resultSet.getString("password"),
                resultSet.getString("role"),
                resultSet.getString("avatar"),
                resultSet.getString("tenant_id"),
                toInstant(resultSet.getTimestamp("create_time")),
                toInstant(resultSet.getTimestamp("update_time")));
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String trimToNull(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        return value.trim();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

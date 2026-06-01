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

import com.miracle.ai.seahorse.agent.kernel.support.SnowflakeIds;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingPage;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingPayload;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.mapping.QueryTermMappingRepositoryPort;
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

/**
 * 基于旧 {@code t_query_term_mapping} 表的 JDBC 术语映射 adapter。
 */
public class JdbcQueryTermMappingRepositoryAdapter implements QueryTermMappingRepositoryPort {

    private static final String SQL_COLUMNS = """
            id, source_term, target_term, match_type, priority, enabled, remark, create_time, update_time
            """;
    private static final String SQL_FIND = "SELECT " + SQL_COLUMNS + """
            FROM t_query_term_mapping
            WHERE id = ? AND deleted = 0
            LIMIT 1
            """;
    private static final String SQL_INSERT = """
            INSERT INTO t_query_term_mapping
            (id, source_term, target_term, match_type, priority, enabled, remark, create_time, update_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcQueryTermMappingRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public QueryTermMappingPage page(long current, long size, String keyword) {
        long offset = (current - 1L) * size;
        List<Object> args = new ArrayList<>();
        String whereClause = buildWhereClause(keyword, args);
        Long total = jdbcTemplate.queryForObject("SELECT COUNT(1) FROM t_query_term_mapping " + whereClause,
                Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(size);
        pageArgs.add(offset);
        List<QueryTermMappingRecord> records = jdbcTemplate.query("""
                SELECT %s
                FROM t_query_term_mapping
                %s
                ORDER BY priority ASC, update_time DESC
                LIMIT ? OFFSET ?
                """.formatted(SQL_COLUMNS, whereClause), this::mapRecord, pageArgs.toArray());
        long safeTotal = total == null ? 0L : total;
        return new QueryTermMappingPage(records, safeTotal, size, current, calculatePages(safeTotal, size));
    }

    @Override
    public Optional<QueryTermMappingRecord> findById(String id) {
        if (!hasText(id)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND, this::mapRecord, toLongId(id)).stream().findFirst();
    }

    @Override
    public String create(QueryTermMappingPayload payload) {
        QueryTermMappingPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        String id = nextId();
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_INSERT,
                toLongId(id),
                safePayload.sourceTerm(),
                safePayload.targetTerm(),
                safePayload.matchType() == null ? 1 : safePayload.matchType(),
                safePayload.priority() == null ? 0 : safePayload.priority(),
                Boolean.FALSE.equals(safePayload.enabled()) ? 0 : 1,
                safePayload.remark(),
                now,
                now);
        return id;
    }

    @Override
    public boolean update(String id, QueryTermMappingPayload payload) {
        QueryTermMappingPayload safePayload = Objects.requireNonNull(payload, "payload must not be null");
        if (!hasText(id)) {
            return false;
        }
        List<Object> args = new ArrayList<>();
        String setClause = buildUpdateSetClause(safePayload, args);
        args.add(toLongId(id));
        int updated = jdbcTemplate.update("""
                UPDATE t_query_term_mapping
                SET %s
                WHERE id = ? AND deleted = 0
                """.formatted(setClause), args.toArray());
        return updated > 0;
    }

    @Override
    public boolean delete(String id) {
        if (!hasText(id)) {
            return false;
        }
        int updated = jdbcTemplate.update("""
                UPDATE t_query_term_mapping
                SET deleted = 1, update_time = ?
                WHERE id = ? AND deleted = 0
                """, Timestamp.from(Instant.now()), toLongId(id));
        return updated > 0;
    }

    private String buildWhereClause(String keyword, List<Object> args) {
        if (!hasText(keyword)) {
            return "WHERE deleted = 0";
        }
        String pattern = "%" + keyword.trim() + "%";
        args.add(pattern);
        args.add(pattern);
        return "WHERE deleted = 0 AND (source_term LIKE ? OR target_term LIKE ?)";
    }

    private String buildUpdateSetClause(QueryTermMappingPayload payload, List<Object> args) {
        List<String> fragments = new ArrayList<>();
        addIfPresent(fragments, args, "source_term", payload.sourceTerm());
        addIfPresent(fragments, args, "target_term", payload.targetTerm());
        addIfPresent(fragments, args, "match_type", payload.matchType());
        addIfPresent(fragments, args, "priority", payload.priority());
        if (payload.enabled() != null) {
            fragments.add("enabled = ?");
            args.add(payload.enabled() ? 1 : 0);
        }
        addIfPresent(fragments, args, "remark", payload.remark());
        fragments.add("update_time = ?");
        args.add(Timestamp.from(Instant.now()));
        return String.join(", ", fragments);
    }

    private void addIfPresent(List<String> fragments, List<Object> args, String column, Object value) {
        if (value == null) {
            return;
        }
        fragments.add(column + " = ?");
        args.add(value);
    }

    private QueryTermMappingRecord mapRecord(ResultSet resultSet, int rowNum) throws SQLException {
        QueryTermMappingRecord record = new QueryTermMappingRecord();
        record.setId(resultSet.getString("id"));
        record.setSourceTerm(resultSet.getString("source_term"));
        record.setTargetTerm(resultSet.getString("target_term"));
        record.setMatchType(resultSet.getObject("match_type", Integer.class));
        record.setPriority(resultSet.getObject("priority", Integer.class));
        Integer enabled = resultSet.getObject("enabled", Integer.class);
        record.setEnabled(enabled != null && enabled == 1);
        record.setRemark(resultSet.getString("remark"));
        Timestamp createTime = resultSet.getTimestamp("create_time");
        Timestamp updateTime = resultSet.getTimestamp("update_time");
        record.setCreateTime(createTime == null ? null : createTime.toInstant());
        record.setUpdateTime(updateTime == null ? null : updateTime.toInstant());
        return record;
    }

    private long calculatePages(long total, long size) {
        if (total <= 0L || size <= 0L) {
            return 0L;
        }
        return (total + size - 1L) / size;
    }

    private String nextId() {
        return SnowflakeIds.nextIdString();
    }

    private long toLongId(String id) {
        try {
            return Long.parseLong(id.trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("query term mapping id must be numeric: " + id, ex);
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

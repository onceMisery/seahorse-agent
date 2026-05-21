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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunPage;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunQuery;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryMaintenanceRunRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JdbcMemoryMaintenanceRunRepositoryAdapter implements MemoryMaintenanceRunRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryMaintenanceRunRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public void save(MemoryMaintenanceRunRecord record) {
        Objects.requireNonNull(record, "record must not be null");
        jdbcTemplate.update("""
                INSERT INTO t_memory_maintenance_run
                (id, reason, status, compaction_requested, alias_requested, gc_requested,
                 gc_scanned_count, gc_enqueued_count, gc_marked_count, gc_dry_run,
                 skipped_tasks, errors, create_time, update_time)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                record.runId(),
                record.reason(),
                record.status(),
                flag(record.compactionRequested()),
                flag(record.aliasRequested()),
                flag(record.garbageCollectionRequested()),
                record.gcScannedCount(),
                record.gcEnqueuedCount(),
                record.gcMarkedCount(),
                flag(record.gcDryRun()),
                JdbcMemorySupport.writeJson(objectMapper, listJson(record.skippedTasks())),
                JdbcMemorySupport.writeJson(objectMapper, listJson(record.errors())),
                JdbcMemorySupport.timestamp(record.createTime()),
                JdbcMemorySupport.timestamp(record.updateTime()));
    }

    @Override
    public MemoryMaintenanceRunPage pageMaintenanceRuns(MemoryMaintenanceRunQuery query) {
        Objects.requireNonNull(query, "query must not be null");
        List<Object> args = new ArrayList<>();
        String where = whereClause(query, args);
        Long total = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM t_memory_maintenance_run WHERE " + where,
                Long.class,
                args.toArray());
        List<Object> pageArgs = new ArrayList<>(args);
        pageArgs.add(query.size());
        pageArgs.add(query.offset());
        List<MemoryMaintenanceRunRecord> records = jdbcTemplate.query(
                "SELECT * FROM t_memory_maintenance_run WHERE " + where
                        + " ORDER BY update_time DESC, create_time DESC LIMIT ? OFFSET ?",
                this::mapRecord,
                pageArgs.toArray());
        long safeTotal = total == null ? 0L : total;
        long pages = safeTotal == 0L ? 0L : (safeTotal + query.size() - 1L) / query.size();
        return new MemoryMaintenanceRunPage(records, safeTotal, query.size(), query.current(), pages);
    }

    private String whereClause(MemoryMaintenanceRunQuery query, List<Object> args) {
        if (!JdbcMemorySupport.hasText(query.status())) {
            return "1 = 1";
        }
        args.add(query.status());
        return "status = ?";
    }

    private MemoryMaintenanceRunRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryMaintenanceRunRecord(
                text(rs.getString("id")),
                text(rs.getString("reason")),
                text(rs.getString("status")),
                flag(rs.getInt("compaction_requested")),
                flag(rs.getInt("alias_requested")),
                flag(rs.getInt("gc_requested")),
                rs.getInt("gc_scanned_count"),
                rs.getInt("gc_enqueued_count"),
                rs.getInt("gc_marked_count"),
                flag(rs.getInt("gc_dry_run")),
                list(rs.getString("skipped_tasks")),
                list(rs.getString("errors")),
                JdbcMemorySupport.instant(rs.getTimestamp("create_time")),
                JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
    }

    private Map<String, Object> listJson(List<String> values) {
        return Map.of("items", Objects.requireNonNullElse(values, List.of()));
    }

    private List<String> list(String json) {
        Object items = JdbcMemorySupport.parseJson(objectMapper, json).get("items");
        if (items instanceof List<?> list) {
            return list.stream().filter(Objects::nonNull).map(Object::toString).toList();
        }
        return List.of();
    }

    private int flag(boolean value) {
        return value ? 1 : 0;
    }

    private boolean flag(int value) {
        return value != 0;
    }

    private String text(String value) {
        return Objects.requireNonNullElse(value, "").trim();
    }
}

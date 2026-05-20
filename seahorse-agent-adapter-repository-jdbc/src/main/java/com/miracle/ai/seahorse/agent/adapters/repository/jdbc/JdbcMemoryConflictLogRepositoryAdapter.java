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

import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictLogRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryConflictRecord;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public class JdbcMemoryConflictLogRepositoryAdapter implements MemoryConflictLogRepositoryPort {

    private static final String STATUS_RESOLVED = "RESOLVED";

    private final JdbcTemplate jdbcTemplate;

    public JdbcMemoryConflictLogRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public void save(MemoryConflictRecord record) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO t_memory_conflict_log
                (id, user_id, memory_id_1, memory_id_2, conflict_type, severity, resolution_status,
                 resolution_action, resolved_by, resolved_at, create_time, update_time, deleted)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
                """,
                JdbcMemorySupport.hasText(record.id()) ? record.id() : JdbcMemorySupport.nextId(),
                record.userId(),
                record.memoryId1(),
                record.memoryId2(),
                record.conflictType(),
                record.severity(),
                JdbcMemorySupport.hasText(record.resolutionStatus()) ? record.resolutionStatus() : "PENDING",
                record.resolutionAction(),
                record.resolvedBy(),
                record.resolvedAt().equals(Instant.EPOCH) ? null : JdbcMemorySupport.timestamp(record.resolvedAt()),
                JdbcMemorySupport.timestamp(record.createTime().equals(Instant.EPOCH) ? now : record.createTime()),
                JdbcMemorySupport.timestamp(now));
    }

    @Override
    public List<MemoryConflictRecord> listByUser(String userId, String status, int limit) {
        if (JdbcMemorySupport.hasText(status)) {
            return jdbcTemplate.query("""
                    SELECT * FROM t_memory_conflict_log
                    WHERE user_id = ? AND resolution_status = ? AND deleted = 0
                    ORDER BY create_time DESC
                    LIMIT ?
                    """, this::mapRecord, userId, status.trim(), safeLimit(limit));
        }
        return jdbcTemplate.query("""
                SELECT * FROM t_memory_conflict_log
                WHERE user_id = ? AND deleted = 0
                ORDER BY create_time DESC
                LIMIT ?
                """, this::mapRecord, userId, safeLimit(limit));
    }

    @Override
    public boolean resolve(String conflictId, String action, String resolvedBy) {
        return jdbcTemplate.update("""
                UPDATE t_memory_conflict_log
                SET resolution_status = ?, resolution_action = ?, resolved_by = ?,
                    resolved_at = ?, update_time = ?
                WHERE id = ? AND deleted = 0
                """, STATUS_RESOLVED, action, resolvedBy,
                JdbcMemorySupport.timestamp(Instant.now()),
                JdbcMemorySupport.timestamp(Instant.now()),
                conflictId) > 0;
    }

    private MemoryConflictRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryConflictRecord(
                rs.getString("id"),
                rs.getString("user_id"),
                rs.getString("memory_id_1"),
                rs.getString("memory_id_2"),
                rs.getString("conflict_type"),
                rs.getString("severity"),
                rs.getString("resolution_status"),
                rs.getString("resolution_action"),
                rs.getString("resolved_by"),
                JdbcMemorySupport.instant(rs.getTimestamp("resolved_at")),
                JdbcMemorySupport.instant(rs.getTimestamp("create_time")));
    }

    private int safeLimit(int limit) {
        return limit <= 0 ? 20 : limit;
    }
}

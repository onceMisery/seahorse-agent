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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshot;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryQualitySnapshotRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;

public class JdbcMemoryQualitySnapshotRepositoryAdapter implements MemoryQualitySnapshotRepositoryPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryQualitySnapshotRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(dataSource);
        this.objectMapper = objectMapper;
    }

    @Override
    public void save(MemoryQualitySnapshot snapshot) {
        Instant now = Instant.now();
        jdbcTemplate.update("""
                INSERT INTO t_memory_quality_snapshot (id, user_id, snapshot_json, create_time)
                VALUES (?, ?, CAST(? AS JSON), ?)
                """,
                JdbcMemorySupport.hasText(snapshot.id()) ? snapshot.id() : JdbcMemorySupport.nextId(),
                snapshot.userId(),
                JdbcMemorySupport.writeJson(objectMapper, snapshot.snapshot()),
                JdbcMemorySupport.timestamp(snapshot.createTime().equals(Instant.EPOCH) ? now : snapshot.createTime()));
    }

    @Override
    public List<MemoryQualitySnapshot> listByUser(String userId, int limit) {
        return jdbcTemplate.query("""
                SELECT id, user_id, snapshot_json, create_time
                FROM t_memory_quality_snapshot
                WHERE user_id = ?
                ORDER BY create_time DESC
                LIMIT ?
                """, this::mapSnapshot, userId, limit <= 0 ? 20 : limit);
    }

    private MemoryQualitySnapshot mapSnapshot(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryQualitySnapshot(
                rs.getString("id"),
                rs.getString("user_id"),
                JdbcMemorySupport.parseJson(objectMapper, rs.getString("snapshot_json")),
                JdbcMemorySupport.instant(rs.getTimestamp("create_time")));
    }
}

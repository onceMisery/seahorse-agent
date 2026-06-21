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

import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.runcontext.RunContextSnapshotRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * JDBC repository adapter for run context snapshots.
 */
public class JdbcRunContextSnapshotRepositoryAdapter implements RunContextSnapshotRepositoryPort {

    private static final String COLUMNS = """
            id, tenant_id, run_id, conversation_id, branch_leaf_message_id, role_card_id,
            run_profile_id, executor_engine, executor_config_json, trace_context_json,
            snapshot_json, create_time, deleted
            """;

    private static final String SQL_INSERT = """
            INSERT INTO t_run_context_snapshot
            (id, tenant_id, run_id, conversation_id, branch_leaf_message_id, role_card_id,
             run_profile_id, executor_engine, executor_config_json, trace_context_json,
             snapshot_json, create_time, deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0)
            """;

    private static final String SQL_FIND_BY_RUN_ID = """
            SELECT %s
            FROM t_run_context_snapshot
            WHERE tenant_id = ? AND run_id = ? AND deleted = 0
            ORDER BY create_time DESC, id DESC
            LIMIT 1
            """.formatted(COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcRunContextSnapshotRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Long save(RunContextSnapshotRecord record) {
        RunContextSnapshotRecord safeRecord = Objects.requireNonNull(record, "record must not be null");
        Long id = safeRecord.getId() == null ? JdbcMemorySupport.nextId() : safeRecord.getId();
        String tenantId = hasText(safeRecord.getTenantId()) ? safeRecord.getTenantId().trim() : tenantId();
        String runId = requireText(safeRecord.getRunId(), "runId");
        String executorEngine = hasText(safeRecord.getExecutorEngine())
                ? safeRecord.getExecutorEngine().trim()
                : "kernel";
        String snapshotJson = requireText(safeRecord.getSnapshotJson(), "snapshotJson");
        Timestamp now = Timestamp.from(Instant.now());

        jdbcTemplate.update(SQL_INSERT,
                id,
                tenantId,
                runId,
                safeRecord.getConversationId(),
                safeRecord.getBranchLeafMessageId(),
                safeRecord.getRoleCardId(),
                safeRecord.getRunProfileId(),
                executorEngine,
                blankToNull(safeRecord.getExecutorConfigJson()),
                blankToNull(safeRecord.getTraceContextJson()),
                snapshotJson,
                now);

        safeRecord.setId(id);
        safeRecord.setTenantId(tenantId);
        safeRecord.setRunId(runId);
        safeRecord.setExecutorEngine(executorEngine);
        safeRecord.setSnapshotJson(snapshotJson);
        safeRecord.setCreateTime(now.toInstant());
        safeRecord.setDeleted(0);
        return id;
    }

    @Override
    public Optional<RunContextSnapshotRecord> findByRunId(String runId) {
        if (!hasText(runId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_RUN_ID, this::mapRecord, tenantId(), runId.trim())
                .stream()
                .findFirst();
    }

    private RunContextSnapshotRecord mapRecord(ResultSet resultSet, int rowNumber) throws SQLException {
        RunContextSnapshotRecord record = new RunContextSnapshotRecord();
        record.setId(resultSet.getLong("id"));
        record.setTenantId(resultSet.getString("tenant_id"));
        record.setRunId(resultSet.getString("run_id"));
        record.setConversationId(nullableLong(resultSet, "conversation_id"));
        record.setBranchLeafMessageId(nullableLong(resultSet, "branch_leaf_message_id"));
        record.setRoleCardId(nullableLong(resultSet, "role_card_id"));
        record.setRunProfileId(nullableLong(resultSet, "run_profile_id"));
        record.setExecutorEngine(resultSet.getString("executor_engine"));
        record.setExecutorConfigJson(resultSet.getString("executor_config_json"));
        record.setTraceContextJson(resultSet.getString("trace_context_json"));
        record.setSnapshotJson(resultSet.getString("snapshot_json"));
        record.setCreateTime(toInstant(resultSet.getTimestamp("create_time")));
        record.setDeleted(resultSet.getInt("deleted"));
        return record;
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

    private String requireText(String value, String name) {
        if (!hasText(value)) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value.trim();
    }

    private String blankToNull(String value) {
        return hasText(value) ? value.trim() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

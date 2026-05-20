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
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperation;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationLogPort;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationRecord;
import com.miracle.ai.seahorse.agent.ports.outbound.memory.MemoryOperationStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public class JdbcMemoryOperationLogRepositoryAdapter implements MemoryOperationLogPort {

    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public JdbcMemoryOperationLogRepositoryAdapter(DataSource dataSource, ObjectMapper objectMapper) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    @Override
    public boolean tryStart(MemoryOperation operation) {
        Objects.requireNonNull(operation, "operation must not be null");
        try {
            jdbcTemplate.update("""
                    INSERT INTO t_memory_operation_log
                    (operation_id, user_id, tenant_id, operation_type, target_kind, target_key,
                     request_json, decision_json, status, policy_version, error_message, create_time, update_time)
                    VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSON), NULL, ?, ?, NULL, ?, ?)
                    """,
                    operation.operationId(),
                    operation.userId(),
                    operation.tenantId(),
                    operation.operationType().name(),
                    operation.targetKind(),
                    operation.targetKey(),
                    JdbcMemorySupport.writeJson(objectMapper, operation.request()),
                    MemoryOperationStatus.STARTED.name(),
                    operation.policyVersion(),
                    JdbcMemorySupport.timestamp(operation.createdAt()),
                    JdbcMemorySupport.timestamp(Instant.now()));
            return true;
        } catch (DuplicateKeyException ex) {
            return false;
        }
    }

    @Override
    public void markCompleted(String operationId, MemoryOperationStatus status, Map<String, Object> decision) {
        jdbcTemplate.update("""
                UPDATE t_memory_operation_log
                SET decision_json = CAST(? AS JSON),
                    status = ?,
                    error_message = NULL,
                    update_time = ?
                WHERE operation_id = ?
                """,
                JdbcMemorySupport.writeJson(objectMapper, decision),
                Objects.requireNonNullElse(status, MemoryOperationStatus.SUCCEEDED).name(),
                JdbcMemorySupport.timestamp(Instant.now()),
                operationId);
    }

    @Override
    public void markFailed(String operationId, String errorMessage) {
        jdbcTemplate.update("""
                UPDATE t_memory_operation_log
                SET status = ?,
                    error_message = ?,
                    update_time = ?
                WHERE operation_id = ?
                """,
                MemoryOperationStatus.FAILED.name(),
                Objects.requireNonNullElse(errorMessage, ""),
                JdbcMemorySupport.timestamp(Instant.now()),
                operationId);
    }

    @Override
    public List<MemoryOperationRecord> listByUser(String userId, String tenantId, String status, int limit) {
        String safeTenantId = JdbcMemorySupport.hasText(tenantId) ? tenantId : "default";
        int safeLimit = limit > 0 ? limit : 20;
        if (JdbcMemorySupport.hasText(status)) {
            return jdbcTemplate.query("""
                    SELECT *
                    FROM t_memory_operation_log
                    WHERE user_id = ?
                      AND tenant_id = ?
                      AND status = ?
                    ORDER BY create_time DESC
                    LIMIT ?
                    """, this::mapRecord, userId, safeTenantId, status, safeLimit);
        }
        return jdbcTemplate.query("""
                SELECT *
                FROM t_memory_operation_log
                WHERE user_id = ?
                  AND tenant_id = ?
                ORDER BY create_time DESC
                LIMIT ?
                """, this::mapRecord, userId, safeTenantId, safeLimit);
    }

    private MemoryOperationRecord mapRecord(ResultSet rs, int rowNum) throws SQLException {
        return new MemoryOperationRecord(
                rs.getString("operation_id"),
                rs.getString("user_id"),
                rs.getString("tenant_id"),
                rs.getString("operation_type"),
                rs.getString("target_kind"),
                rs.getString("target_key"),
                JdbcMemorySupport.parseJson(objectMapper, rs.getString("request_json")),
                JdbcMemorySupport.parseJson(objectMapper, rs.getString("decision_json")),
                rs.getString("status"),
                rs.getString("policy_version"),
                rs.getString("error_message"),
                JdbcMemorySupport.instant(rs.getTimestamp("create_time")),
                JdbcMemorySupport.instant(rs.getTimestamp("update_time")));
    }
}

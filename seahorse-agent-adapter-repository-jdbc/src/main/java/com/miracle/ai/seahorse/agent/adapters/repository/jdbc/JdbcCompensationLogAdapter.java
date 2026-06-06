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

import com.miracle.ai.seahorse.agent.kernel.application.consistency.CompensationLog;
import com.miracle.ai.seahorse.agent.kernel.application.consistency.CompensationLogPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * 补偿日志 JDBC 适配器。
 *
 * <p>基于 {@code sa_compensation_log} 表实现 {@link CompensationLogPort} 接口。
 */
public class JdbcCompensationLogAdapter implements CompensationLogPort {

    private static final String SQL_INSERT = """
            INSERT INTO sa_compensation_log
            (tenant_id, operation_type, operation_id, payload, status,
             retry_count, max_retries, last_error, next_retry_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;

    private static final String SQL_FIND_PENDING = """
            SELECT id, tenant_id, operation_type, operation_id, payload, status,
                   retry_count, max_retries, last_error, next_retry_at, created_at, updated_at, completed_at
            FROM sa_compensation_log
            WHERE status IN ('PENDING', 'RETRYING')
              AND (next_retry_at IS NULL OR next_retry_at <= ?)
            ORDER BY created_at ASC
            LIMIT ?
            """;

    private static final String SQL_UPDATE_STATUS = """
            UPDATE sa_compensation_log
            SET status = ?, last_error = ?, updated_at = ?,
                completed_at = CASE WHEN ? IN ('SUCCESS', 'FAILED') THEN ? ELSE NULL END
            WHERE id = ?
            """;

    private static final String SQL_INCREMENT_RETRY = """
            UPDATE sa_compensation_log
            SET retry_count = retry_count + 1,
                next_retry_at = ?,
                status = 'RETRYING',
                updated_at = ?
            WHERE id = ?
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcCompensationLogAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public void save(CompensationLog log) {
        Objects.requireNonNull(log, "CompensationLog must not be null");
        Timestamp now = Timestamp.from(Instant.now());
        jdbcTemplate.update(SQL_INSERT,
                log.getTenantId(),
                log.getOperationType(),
                log.getOperationId(),
                log.getPayload(),
                log.getStatus() != null ? log.getStatus().name() : CompensationLog.CompensationStatus.PENDING.name(),
                log.getRetryCount(),
                log.getMaxRetries() > 0 ? log.getMaxRetries() : 3,
                log.getLastError(),
                null,
                now,
                now);
    }

    @Override
    public List<CompensationLog> findPendingRetries(int limit) {
        Timestamp now = Timestamp.from(Instant.now());
        return jdbcTemplate.query(SQL_FIND_PENDING, this::mapRow, now, limit);
    }

    @Override
    public void updateStatus(Long id, CompensationLog.CompensationStatus status, String lastError) {
        Objects.requireNonNull(id, "id must not be null");
        Objects.requireNonNull(status, "status must not be null");
        Timestamp now = Timestamp.from(Instant.now());
        String statusName = status.name();
        jdbcTemplate.update(SQL_UPDATE_STATUS,
                statusName,
                lastError,
                now,
                statusName,
                now,
                id);
    }

    @Override
    public void incrementRetryCount(Long id) {
        Objects.requireNonNull(id, "id must not be null");
        Timestamp now = Timestamp.from(Instant.now());
        // 指数退避：retryCount * 2 分钟
        Timestamp nextRetry = Timestamp.from(Instant.now().plusSeconds(120));
        jdbcTemplate.update(SQL_INCREMENT_RETRY, nextRetry, now, id);
    }

    private CompensationLog mapRow(ResultSet rs, int rowNum) throws SQLException {
        CompensationLog log = new CompensationLog();
        log.setId(rs.getLong("id"));
        log.setTenantId(rs.getString("tenant_id"));
        log.setOperationType(rs.getString("operation_type"));
        log.setOperationId(rs.getString("operation_id"));
        log.setPayload(rs.getString("payload"));
        String statusStr = rs.getString("status");
        if (statusStr != null) {
            log.setStatus(CompensationLog.CompensationStatus.valueOf(statusStr));
        }
        log.setRetryCount(rs.getInt("retry_count"));
        log.setMaxRetries(rs.getInt("max_retries"));
        log.setLastError(rs.getString("last_error"));
        return log;
    }
}

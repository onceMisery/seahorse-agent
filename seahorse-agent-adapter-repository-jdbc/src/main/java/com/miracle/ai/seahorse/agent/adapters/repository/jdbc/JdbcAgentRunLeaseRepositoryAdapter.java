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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.runtime.AgentRunLease;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.AgentRunLeaseRepositoryPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

public class JdbcAgentRunLeaseRepositoryAdapter implements AgentRunLeaseRepositoryPort {

    private static final String LEASE_COLUMNS = "run_id, worker_id, lease_until, heartbeat_at";
    private static final String SQL_INSERT = """
            INSERT INTO sa_agent_run_lease (run_id, worker_id, lease_until, heartbeat_at)
            VALUES (?, ?, ?, ?)
            """;
    private static final String SQL_ACQUIRE_EXISTING = """
            UPDATE sa_agent_run_lease
            SET worker_id = ?, lease_until = ?, heartbeat_at = ?
            WHERE run_id = ?
              AND (worker_id = ? OR lease_until <= ?)
            """;
    private static final String SQL_HEARTBEAT = """
            UPDATE sa_agent_run_lease
            SET lease_until = ?, heartbeat_at = ?
            WHERE run_id = ?
              AND worker_id = ?
              AND lease_until > ?
            """;
    private static final String SQL_RELEASE = """
            DELETE FROM sa_agent_run_lease
            WHERE run_id = ?
              AND worker_id = ?
            """;
    private static final String SQL_FIND_BY_RUN_ID = """
            SELECT %s
            FROM sa_agent_run_lease
            WHERE run_id = ?
            """.formatted(LEASE_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcAgentRunLeaseRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public boolean acquire(String runId, String workerId, Instant leaseUntil, Instant now) {
        AgentRunLease lease = new AgentRunLease(runId, workerId, leaseUntil, now);
        try {
            return jdbcTemplate.update(SQL_INSERT,
                    lease.runId(),
                    lease.workerId(),
                    toTimestamp(lease.leaseUntil()),
                    toTimestamp(lease.heartbeatAt())) == 1;
        } catch (DuplicateKeyException ex) {
            return jdbcTemplate.update(SQL_ACQUIRE_EXISTING,
                    lease.workerId(),
                    toTimestamp(lease.leaseUntil()),
                    toTimestamp(lease.heartbeatAt()),
                    lease.runId(),
                    lease.workerId(),
                    toTimestamp(lease.heartbeatAt())) == 1;
        }
    }

    @Override
    public boolean heartbeat(String runId, String workerId, Instant leaseUntil, Instant now) {
        AgentRunLease lease = new AgentRunLease(runId, workerId, leaseUntil, now);
        return jdbcTemplate.update(SQL_HEARTBEAT,
                toTimestamp(lease.leaseUntil()),
                toTimestamp(lease.heartbeatAt()),
                lease.runId(),
                lease.workerId(),
                toTimestamp(lease.heartbeatAt())) == 1;
    }

    @Override
    public boolean release(String runId, String workerId) {
        if (!hasText(runId) || !hasText(workerId)) {
            return false;
        }
        return jdbcTemplate.update(SQL_RELEASE, runId.trim(), workerId.trim()) == 1;
    }

    @Override
    public Optional<AgentRunLease> findByRunId(String runId) {
        if (!hasText(runId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BY_RUN_ID, this::mapLease, runId.trim()).stream().findFirst();
    }

    private AgentRunLease mapLease(ResultSet resultSet, int rowNum) throws SQLException {
        return new AgentRunLease(
                resultSet.getString("run_id"),
                resultSet.getString("worker_id"),
                toInstant(resultSet.getTimestamp("lease_until")),
                toInstant(resultSet.getTimestamp("heartbeat_at")));
    }

    private Timestamp toTimestamp(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}

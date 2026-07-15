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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeRegistration;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeEndpoint;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeNodeRegistryPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcSandboxRuntimeNodeRegistryAdapter implements SandboxRuntimeNodeRegistryPort {

    private static final String REGISTRATION_COLUMNS = """
            node_id, runtime, engine, health_status, admission_available, admission_status,
            active_session_count, active_session_limit, workspace_free_bytes,
            observed_at, heartbeat_at, expires_at
            """;
    private static final String SQL_UPDATE_HEARTBEAT = """
            UPDATE sa_sandbox_runtime_node
            SET owner_id = ?, runtime = ?, engine = ?, health_status = ?, admission_available = ?,
                admission_status = ?, active_session_count = ?, active_session_limit = ?,
                workspace_free_bytes = ?, observed_at = ?, heartbeat_at = ?, expires_at = ?, transport_uri = ?
            WHERE node_id = ? AND (owner_id = ? OR expires_at <= ?)
            """;
    private static final String SQL_INSERT_HEARTBEAT = """
            INSERT INTO sa_sandbox_runtime_node
            (node_id, owner_id, runtime, engine, health_status, admission_available, admission_status,
             active_session_count, active_session_limit, workspace_free_bytes,
             observed_at, heartbeat_at, expires_at, registered_at, transport_uri)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_LIST_REGISTRATIONS = """
            SELECT %s,
                   CASE WHEN expires_at > CURRENT_TIMESTAMP THEN 'LIVE' ELSE 'STALE' END AS registration_status
            FROM sa_sandbox_runtime_node
            ORDER BY heartbeat_at DESC, node_id ASC
            LIMIT ?
            """.formatted(REGISTRATION_COLUMNS);
    private static final String SQL_RELEASE = """
            UPDATE sa_sandbox_runtime_node SET expires_at = ? WHERE node_id = ? AND owner_id = ?
            """;
    private static final String SQL_FIND_OWNED_HEARTBEAT = """
            SELECT heartbeat_at FROM sa_sandbox_runtime_node WHERE node_id = ? AND owner_id = ?
            """;
    private static final String SQL_DATABASE_NOW = "SELECT CURRENT_TIMESTAMP";
    private static final String SQL_FIND_LIVE_ENDPOINT = """
            SELECT node_id, transport_uri, health_status, admission_available, admission_status, expires_at
            FROM sa_sandbox_runtime_node
            WHERE node_id = ? AND expires_at > CURRENT_TIMESTAMP AND transport_uri <> ''
            """;

    private final JdbcTemplate jdbcTemplate;

    public JdbcSandboxRuntimeNodeRegistryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public Optional<SandboxRuntimeNodeRegistration> heartbeat(SandboxRuntimeNodeRegistration registration,
                                                              String ownerId,
                                                              Duration leaseTtl) {
        return heartbeat(registration, ownerId, "", leaseTtl);
    }

    @Override
    public Optional<SandboxRuntimeNodeRegistration> heartbeat(SandboxRuntimeNodeRegistration registration,
                                                              String ownerId,
                                                              String transportUri,
                                                              Duration leaseTtl) {
        SandboxRuntimeNodeRegistration safeRegistration = Objects.requireNonNull(
                registration,
                "registration must not be null");
        String safeOwnerId = requireText(ownerId, "ownerId must not be blank");
        String safeTransportUri = normalizeTransportUri(transportUri);
        Duration safeLeaseTtl = Objects.requireNonNull(leaseTtl, "leaseTtl must not be null");
        Instant databaseNow = databaseNow();
        SandboxRuntimeNodeRegistration persisted = persistedAt(safeRegistration, databaseNow, safeLeaseTtl);
        int updated = jdbcTemplate.update(
                SQL_UPDATE_HEARTBEAT,
                safeOwnerId,
                safeRegistration.runtime(),
                safeRegistration.engine(),
                safeRegistration.observedHealthStatus(),
                safeRegistration.observedAdmissionAvailable(),
                safeRegistration.observedAdmissionStatus(),
                safeRegistration.observedActiveSessionCount(),
                safeRegistration.observedActiveSessionLimit(),
                safeRegistration.observedWorkspaceFreeBytes(),
                toTimestamp(safeRegistration.observedAt()),
                toTimestamp(persisted.heartbeatAt()),
                toTimestamp(persisted.expiresAt()),
                safeTransportUri,
                safeRegistration.nodeId(),
                safeOwnerId,
                toTimestamp(databaseNow));
        if (updated > 0) {
            return Optional.of(persisted);
        }
        try {
            jdbcTemplate.update(
                    SQL_INSERT_HEARTBEAT,
                    safeRegistration.nodeId(),
                    safeOwnerId,
                    safeRegistration.runtime(),
                    safeRegistration.engine(),
                    safeRegistration.observedHealthStatus(),
                    safeRegistration.observedAdmissionAvailable(),
                    safeRegistration.observedAdmissionStatus(),
                    safeRegistration.observedActiveSessionCount(),
                    safeRegistration.observedActiveSessionLimit(),
                    safeRegistration.observedWorkspaceFreeBytes(),
                    toTimestamp(safeRegistration.observedAt()),
                    toTimestamp(persisted.heartbeatAt()),
                    toTimestamp(persisted.expiresAt()),
                    toTimestamp(persisted.heartbeatAt()),
                    safeTransportUri);
            return Optional.of(persisted);
        } catch (DuplicateKeyException ignored) {
            return Optional.empty();
        }
    }

    @Override
    public Optional<SandboxRuntimeNodeEndpoint> findLiveEndpoint(String nodeId) {
        if (nodeId == null || nodeId.isBlank()) {
            return Optional.empty();
        }
        List<SandboxRuntimeNodeEndpoint> endpoints = jdbcTemplate.query(
                SQL_FIND_LIVE_ENDPOINT,
                (rs, rowNum) -> new SandboxRuntimeNodeEndpoint(
                        rs.getString("node_id"),
                        URI.create(rs.getString("transport_uri")),
                        rs.getString("health_status"),
                        rs.getBoolean("admission_available"),
                        rs.getString("admission_status"),
                        toInstant(rs.getTimestamp("expires_at"))),
                nodeId.trim());
        return endpoints.stream().findFirst();
    }

    @Override
    public List<SandboxRuntimeNodeRegistration> listRegistrations(int limit) {
        if (limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_REGISTRATIONS, this::mapRegistration, limit);
    }

    @Override
    public boolean release(String nodeId, String ownerId) {
        if (nodeId == null || nodeId.isBlank() || ownerId == null || ownerId.isBlank()) {
            return false;
        }
        String safeNodeId = nodeId.trim();
        String safeOwnerId = ownerId.trim();
        List<Instant> heartbeats = jdbcTemplate.query(
                SQL_FIND_OWNED_HEARTBEAT,
                (rs, rowNum) -> toInstant(rs.getTimestamp("heartbeat_at")),
                safeNodeId,
                safeOwnerId);
        if (heartbeats.isEmpty()) {
            return false;
        }
        Instant releasedAt = databaseNow();
        Instant heartbeatAt = heartbeats.get(0);
        if (!releasedAt.isAfter(heartbeatAt)) {
            releasedAt = heartbeatAt.plusMillis(1L);
        }
        return jdbcTemplate.update(SQL_RELEASE, toTimestamp(releasedAt), safeNodeId, safeOwnerId) > 0;
    }

    private SandboxRuntimeNodeRegistration mapRegistration(ResultSet rs, int rowNum) throws SQLException {
        return new SandboxRuntimeNodeRegistration(
                rs.getString("node_id"),
                rs.getString("runtime"),
                rs.getString("engine"),
                rs.getString("health_status"),
                rs.getBoolean("admission_available"),
                rs.getString("admission_status"),
                rs.getInt("active_session_count"),
                rs.getInt("active_session_limit"),
                rs.getLong("workspace_free_bytes"),
                toInstant(rs.getTimestamp("observed_at")),
                toInstant(rs.getTimestamp("heartbeat_at")),
                toInstant(rs.getTimestamp("expires_at")),
                rs.getString("registration_status"));
    }

    private SandboxRuntimeNodeRegistration persistedAt(SandboxRuntimeNodeRegistration registration,
                                                        Instant heartbeatAt,
                                                        Duration leaseTtl) {
        return new SandboxRuntimeNodeRegistration(
                registration.nodeId(),
                registration.runtime(),
                registration.engine(),
                registration.observedHealthStatus(),
                registration.observedAdmissionAvailable(),
                registration.observedAdmissionStatus(),
                registration.observedActiveSessionCount(),
                registration.observedActiveSessionLimit(),
                registration.observedWorkspaceFreeBytes(),
                registration.observedAt(),
                heartbeatAt,
                heartbeatAt.plus(leaseTtl),
                SandboxRuntimeNodeRegistration.REGISTRATION_LIVE);
    }

    private Instant databaseNow() {
        Timestamp timestamp = jdbcTemplate.queryForObject(SQL_DATABASE_NOW, Timestamp.class);
        return Objects.requireNonNull(timestamp, "database current timestamp must not be null").toInstant();
    }

    private static Timestamp toTimestamp(Instant instant) {
        return Timestamp.from(instant);
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp.toInstant();
    }

    private static String requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value.trim();
    }

    private static String normalizeTransportUri(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return new SandboxRuntimeNodeEndpoint(
                "transport-validation",
                URI.create(value.trim()),
                Instant.MAX).transportUri().toString();
    }
}

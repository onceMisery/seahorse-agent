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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeAdmissionOverride;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeAdmissionChange;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditActorType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEventType;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeCapacityReservationPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeNodeRegistryPort;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

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

public class JdbcSandboxRuntimeNodeRegistryAdapter implements SandboxRuntimeNodeRegistryPort,
        SandboxRuntimeCapacityReservationPort {

    private static final String REGISTRATION_COLUMNS = """
            n.node_id, n.runtime, n.engine, n.health_status,
            n.admission_available, n.admission_status,
            n.active_session_count, n.active_session_limit, n.workspace_free_bytes,
            n.observed_at, n.heartbeat_at, n.expires_at,
            COALESCE(o.draining, FALSE) AS operator_draining,
            COALESCE(o.operator_id, '') AS operator_id,
            o.updated_at AS operator_updated_at
            """;
    private static final String SQL_UPDATE_HEARTBEAT = """
            UPDATE sa_sandbox_runtime_node
            SET owner_id = ?, runtime = ?, engine = ?, health_status = ?, admission_available = ?,
                admission_status = ?, active_session_count = ?, active_session_limit = ?,
                workspace_free_bytes = ?, observed_at = ?, heartbeat_at = ?,
                expires_at = CASE WHEN owner_id = ? AND expires_at > ? THEN expires_at ELSE ? END,
                transport_uri = ?
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
                   CASE WHEN n.expires_at > CURRENT_TIMESTAMP THEN 'LIVE' ELSE 'STALE' END AS registration_status
            FROM sa_sandbox_runtime_node n
            LEFT JOIN sa_sandbox_runtime_node_admission_override o ON o.node_id = n.node_id
            ORDER BY n.heartbeat_at DESC, n.node_id ASC
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
            SELECT n.node_id, n.owner_id, n.transport_uri, n.health_status,
                   CASE WHEN COALESCE(o.draining, FALSE) THEN FALSE ELSE n.admission_available END
                     AS admission_available,
                   CASE WHEN COALESCE(o.draining, FALSE) THEN 'DRAINING' ELSE n.admission_status END
                     AS admission_status,
                   n.active_session_count, n.active_session_limit, n.workspace_free_bytes, n.expires_at
            FROM sa_sandbox_runtime_node n
            LEFT JOIN sa_sandbox_runtime_node_admission_override o ON o.node_id = n.node_id
            WHERE n.node_id = ? AND n.expires_at > CURRENT_TIMESTAMP AND n.transport_uri <> ''
            """;
    private static final String SQL_LIST_LIVE_ENDPOINTS = """
            SELECT n.node_id, n.owner_id, n.transport_uri, n.health_status,
                   CASE WHEN COALESCE(o.draining, FALSE) THEN FALSE ELSE n.admission_available END
                     AS admission_available,
                   CASE WHEN COALESCE(o.draining, FALSE) THEN 'DRAINING' ELSE n.admission_status END
                     AS admission_status,
                   n.active_session_count, n.active_session_limit, n.workspace_free_bytes, n.expires_at
            FROM sa_sandbox_runtime_node n
            LEFT JOIN sa_sandbox_runtime_node_admission_override o ON o.node_id = n.node_id
            WHERE n.expires_at > CURRENT_TIMESTAMP AND n.transport_uri <> ''
            ORDER BY n.node_id ASC
            """;
    private static final String SQL_IS_LIVE_OWNER = """
            SELECT COUNT(*) FROM sa_sandbox_runtime_node
            WHERE node_id = ? AND owner_id = ? AND expires_at > CURRENT_TIMESTAMP
            """;
    private static final String SQL_RESERVE_OPERATION_LEASE = """
            UPDATE sa_sandbox_runtime_node
            SET expires_at = CASE WHEN expires_at > ? THEN expires_at ELSE ? END
            WHERE node_id = ? AND owner_id = ? AND expires_at > ?
            """;
    private static final String SQL_LIST_STALE_NODE_IDS = """
            SELECT node_id FROM sa_sandbox_runtime_node
            WHERE expires_at <= ?
            ORDER BY expires_at ASC, node_id ASC
            LIMIT ?
            """;
    private static final String SQL_DELETE_STALE_NODE = """
            DELETE FROM sa_sandbox_runtime_node WHERE node_id = ? AND expires_at <= ?
            """;
    private static final String SQL_DELETE_EXPIRED_CAPACITY_RESERVATIONS = """
            DELETE FROM sa_sandbox_runtime_capacity_reservation WHERE node_id = ? AND expires_at <= ?
            """;
    private static final String SQL_LOCK_CAPACITY_NODE = """
            SELECT admission_available, admission_status, active_session_count,
                   active_session_limit, expires_at
            FROM sa_sandbox_runtime_node
            WHERE node_id = ?
            FOR UPDATE
            """;
    private static final String SQL_COUNT_CAPACITY_USAGE = """
            SELECT
              (SELECT COUNT(*) FROM sa_sandbox_session
               WHERE runtime_node_id = ? AND status NOT IN (?, ?, ?, ?)) AS persisted_active,
              (SELECT COUNT(*) FROM sa_sandbox_runtime_capacity_reservation
               WHERE node_id = ? AND expires_at > ?) AS pending_reservations
            """;
    private static final String SQL_INSERT_CAPACITY_RESERVATION = """
            INSERT INTO sa_sandbox_runtime_capacity_reservation
            (reservation_id, node_id, expires_at, created_at)
            VALUES (?, ?, ?, ?)
            """;
    private static final String SQL_RELEASE_CAPACITY_RESERVATION = """
            DELETE FROM sa_sandbox_runtime_capacity_reservation WHERE reservation_id = ?
            """;
    private static final String SQL_DELETE_NODE_CAPACITY_RESERVATIONS = """
            DELETE FROM sa_sandbox_runtime_capacity_reservation WHERE node_id = ?
            """;
    private static final String SQL_FIND_OPERATOR_DRAINING = """
            SELECT draining FROM sa_sandbox_runtime_node_admission_override WHERE node_id = ?
            """;
    private static final String SQL_LOCK_NODE_FOR_ADMISSION_OVERRIDE = """
            SELECT node_id FROM sa_sandbox_runtime_node WHERE node_id = ? FOR UPDATE
            """;
    private static final String SQL_LOCK_ADMISSION_OVERRIDE = """
            SELECT node_id FROM sa_sandbox_runtime_node_admission_override WHERE node_id = ? FOR UPDATE
            """;
    private static final String SQL_UPDATE_ADMISSION_OVERRIDE = """
            UPDATE sa_sandbox_runtime_node_admission_override
            SET draining = ?, operator_id = ?, updated_at = ?
            WHERE node_id = ?
            """;
    private static final String SQL_INSERT_ADMISSION_OVERRIDE = """
            INSERT INTO sa_sandbox_runtime_node_admission_override
            (node_id, draining, operator_id, updated_at)
            VALUES (?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final JdbcAuditEventRepositoryAdapter auditEventRepository;

    public JdbcSandboxRuntimeNodeRegistryAdapter(DataSource dataSource) {
        this(dataSource, null);
    }

    public JdbcSandboxRuntimeNodeRegistryAdapter(DataSource dataSource,
                                                JdbcAuditEventRepositoryAdapter auditEventRepository) {
        DataSource safeDataSource = Objects.requireNonNull(dataSource, "dataSource must not be null");
        this.jdbcTemplate = new JdbcTemplate(safeDataSource);
        this.transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(safeDataSource));
        this.auditEventRepository = auditEventRepository;
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
                safeOwnerId,
                toTimestamp(persisted.expiresAt()),
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
                this::mapEndpoint,
                nodeId.trim());
        return endpoints.stream().findFirst();
    }

    @Override
    public List<SandboxRuntimeNodeEndpoint> listLiveEndpoints() {
        return jdbcTemplate.query(SQL_LIST_LIVE_ENDPOINTS, this::mapEndpoint);
    }

    @Override
    public boolean isLiveOwner(String nodeId, String ownerId) {
        if (nodeId == null || nodeId.isBlank() || ownerId == null || ownerId.isBlank()) {
            return false;
        }
        Integer count = jdbcTemplate.queryForObject(
                SQL_IS_LIVE_OWNER,
                Integer.class,
                nodeId.trim(),
                ownerId.trim());
        return count != null && count == 1;
    }

    @Override
    public boolean reserveOperationLease(String nodeId, String ownerId, Duration leaseTtl) {
        if (nodeId == null || nodeId.isBlank() || ownerId == null || ownerId.isBlank()) {
            return false;
        }
        Duration safeLeaseTtl = Objects.requireNonNull(leaseTtl, "leaseTtl must not be null");
        if (safeLeaseTtl.isNegative() || safeLeaseTtl.isZero()
                || safeLeaseTtl.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("leaseTtl must be between 1ms and 10 minutes");
        }
        Instant now = databaseNow();
        Instant operationDeadline = now.plus(safeLeaseTtl);
        return jdbcTemplate.update(
                SQL_RESERVE_OPERATION_LEASE,
                toTimestamp(operationDeadline),
                toTimestamp(operationDeadline),
                nodeId.trim(),
                ownerId.trim(),
                toTimestamp(now)) == 1;
    }

    @Override
    public int deleteStaleRegistrations(Duration retention, int limit) {
        Duration safeRetention = Objects.requireNonNull(retention, "retention must not be null");
        if (safeRetention.isNegative() || safeRetention.isZero() || limit <= 0) {
            return 0;
        }
        Instant cutoff = databaseNow().minus(safeRetention);
        List<String> nodeIds = jdbcTemplate.queryForList(
                SQL_LIST_STALE_NODE_IDS,
                String.class,
                toTimestamp(cutoff),
                Math.min(limit, 1_000));
        int removed = 0;
        for (String nodeId : nodeIds) {
            Boolean nodeRemoved = transactionTemplate.execute(status -> {
                int deleted = jdbcTemplate.update(SQL_DELETE_STALE_NODE, nodeId, toTimestamp(cutoff));
                if (deleted == 1) {
                    jdbcTemplate.update(SQL_DELETE_NODE_CAPACITY_RESERVATIONS, nodeId);
                    return true;
                }
                return false;
            });
            if (Boolean.TRUE.equals(nodeRemoved)) {
                removed++;
            }
        }
        return removed;
    }

    @Override
    public Optional<SandboxRuntimeNodeAdmissionOverride> setOperatorDraining(
            SandboxRuntimeNodeAdmissionChange change) {
        SandboxRuntimeNodeAdmissionChange safeChange = Objects.requireNonNull(change, "change must not be null");
        if (auditEventRepository == null) {
            throw new UnsupportedOperationException(
                    "atomic sandbox runtime node admission control requires JDBC audit event persistence");
        }
        Optional<SandboxRuntimeNodeAdmissionOverride> result = transactionTemplate.execute(status -> {
            List<String> nodes = jdbcTemplate.queryForList(
                    SQL_LOCK_NODE_FOR_ADMISSION_OVERRIDE,
                    String.class,
                    safeChange.nodeId());
            List<String> overrides = jdbcTemplate.queryForList(
                    SQL_LOCK_ADMISSION_OVERRIDE,
                    String.class,
                    safeChange.nodeId());
            // A retained override is the durable identity of a previously registered node;
            // stale-registration cleanup must not turn that known node into an unknown node.
            if (nodes.isEmpty() && overrides.isEmpty()) {
                return Optional.empty();
            }
            Instant changedAt = databaseNow();
            if (overrides.isEmpty()) {
                jdbcTemplate.update(
                        SQL_INSERT_ADMISSION_OVERRIDE,
                        safeChange.nodeId(),
                        safeChange.draining(),
                        safeChange.operatorId(),
                        toTimestamp(changedAt));
            } else {
                jdbcTemplate.update(
                        SQL_UPDATE_ADMISSION_OVERRIDE,
                        safeChange.draining(),
                        safeChange.operatorId(),
                        toTimestamp(changedAt),
                        safeChange.nodeId());
            }
            auditEventRepository.save(new AuditEvent(
                    safeChange.auditId(),
                    safeChange.tenantId(),
                    AuditEventType.SANDBOX_RUNTIME_NODE_ADMISSION_CHANGED,
                    AuditActorType.USER,
                    safeChange.operatorId(),
                    null,
                    null,
                    "SANDBOX_RUNTIME_NODE",
                    safeChange.nodeId(),
                    "{\"draining\":" + safeChange.draining() + "}",
                    changedAt));
            return Optional.of(new SandboxRuntimeNodeAdmissionOverride(
                    safeChange.nodeId(),
                    safeChange.draining(),
                    safeChange.operatorId(),
                    changedAt));
        });
        return Objects.requireNonNullElse(result, Optional.empty());
    }

    @Override
    public ReservationResult tryReserve(String nodeId, String reservationId, Duration leaseTtl) {
        String safeNodeId = requireText(nodeId, "nodeId must not be blank");
        String safeReservationId = requireText(reservationId, "reservationId must not be blank");
        Duration safeLeaseTtl = Objects.requireNonNull(leaseTtl, "leaseTtl must not be null");
        if (safeLeaseTtl.isNegative() || safeLeaseTtl.isZero()
                || safeLeaseTtl.compareTo(Duration.ofMinutes(10)) > 0) {
            throw new IllegalArgumentException("leaseTtl must be between 1ms and 10 minutes");
        }
        ReservationResult result = transactionTemplate.execute(status -> reserveCapacity(
                safeNodeId,
                safeReservationId,
                safeLeaseTtl));
        return Objects.requireNonNullElse(result, ReservationResult.REJECTED);
    }

    @Override
    public boolean release(String reservationId) {
        if (reservationId == null || reservationId.isBlank()) {
            return false;
        }
        return jdbcTemplate.update(SQL_RELEASE_CAPACITY_RESERVATION, reservationId.trim()) == 1;
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
        boolean operatorDraining = rs.getBoolean("operator_draining");
        String observedAdmissionStatus = rs.getString("admission_status");
        boolean observedAdmissionAvailable = rs.getBoolean("admission_available");
        return new SandboxRuntimeNodeRegistration(
                rs.getString("node_id"),
                rs.getString("runtime"),
                rs.getString("engine"),
                rs.getString("health_status"),
                observedAdmissionAvailable,
                observedAdmissionStatus,
                rs.getInt("active_session_count"),
                rs.getInt("active_session_limit"),
                rs.getLong("workspace_free_bytes"),
                toInstant(rs.getTimestamp("observed_at")),
                toInstant(rs.getTimestamp("heartbeat_at")),
                toInstant(rs.getTimestamp("expires_at")),
                rs.getString("registration_status"),
                operatorDraining ? false : observedAdmissionAvailable,
                operatorDraining ? "DRAINING" : observedAdmissionStatus,
                operatorDraining,
                rs.getString("operator_id"),
                toNullableInstant(rs.getTimestamp("operator_updated_at")));
    }

    private SandboxRuntimeNodeEndpoint mapEndpoint(ResultSet rs, int rowNum) throws SQLException {
        return new SandboxRuntimeNodeEndpoint(
                rs.getString("node_id"),
                rs.getString("owner_id"),
                URI.create(rs.getString("transport_uri")),
                rs.getString("health_status"),
                rs.getBoolean("admission_available"),
                rs.getString("admission_status"),
                rs.getInt("active_session_count"),
                rs.getInt("active_session_limit"),
                rs.getLong("workspace_free_bytes"),
                toInstant(rs.getTimestamp("expires_at")));
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

    private ReservationResult reserveCapacity(String nodeId, String reservationId, Duration leaseTtl) {
        Instant now = databaseNow();
        List<CapacityNode> nodes = jdbcTemplate.query(
                SQL_LOCK_CAPACITY_NODE,
                (rs, rowNum) -> new CapacityNode(
                        rs.getBoolean("admission_available"),
                        rs.getString("admission_status"),
                        rs.getInt("active_session_count"),
                        rs.getInt("active_session_limit"),
                        toInstant(rs.getTimestamp("expires_at"))),
                nodeId);
        if (nodes.isEmpty()) {
            jdbcTemplate.update(SQL_DELETE_EXPIRED_CAPACITY_RESERVATIONS, nodeId, toTimestamp(now));
            return ReservationResult.NOT_REQUIRED;
        }
        CapacityNode node = nodes.get(0);
        jdbcTemplate.update(SQL_DELETE_EXPIRED_CAPACITY_RESERVATIONS, nodeId, toTimestamp(now));
        boolean operatorDraining = jdbcTemplate.query(
                        SQL_FIND_OPERATOR_DRAINING,
                        (rs, rowNum) -> rs.getBoolean("draining"),
                        nodeId)
                .stream()
                .findFirst()
                .orElse(false);
        if (!node.expiresAt().isAfter(now)
                || operatorDraining
                || !node.admissionAvailable()
                || !("AVAILABLE".equals(node.admissionStatus()) || "DEGRADED".equals(node.admissionStatus()))) {
            return ReservationResult.REJECTED;
        }
        if (node.activeSessionLimit() <= 0) {
            return ReservationResult.NOT_REQUIRED;
        }
        CapacityUsage usage = jdbcTemplate.queryForObject(
                SQL_COUNT_CAPACITY_USAGE,
                (rs, rowNum) -> new CapacityUsage(
                        rs.getInt("persisted_active"),
                        rs.getInt("pending_reservations")),
                nodeId,
                "SUCCEEDED",
                "FAILED",
                "TIMED_OUT",
                "CANCELLED",
                nodeId,
                toTimestamp(now));
        CapacityUsage safeUsage = Objects.requireNonNull(usage, "capacity usage must not be null");
        int effectiveActive = Math.max(
                node.observedActiveSessionCount(),
                safeUsage.persistedActive())
                + safeUsage.pendingReservations();
        if (effectiveActive >= node.activeSessionLimit()) {
            return ReservationResult.REJECTED;
        }
        jdbcTemplate.update(
                SQL_INSERT_CAPACITY_RESERVATION,
                reservationId,
                nodeId,
                toTimestamp(now.plus(leaseTtl)),
                toTimestamp(now));
        return ReservationResult.RESERVED;
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

    private static Instant toNullableInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
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

    private record CapacityNode(boolean admissionAvailable,
                                String admissionStatus,
                                int observedActiveSessionCount,
                                int activeSessionLimit,
                                Instant expiresAt) {
    }

    private record CapacityUsage(int persistedActive, int pendingReservations) {
    }
}

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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.audit.AuditEvent;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeAdmissionChange;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeNodeRegistration;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeCapacityReservationPort.ReservationResult.NOT_REQUIRED;
import static com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeCapacityReservationPort.ReservationResult.REJECTED;
import static com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeCapacityReservationPort.ReservationResult.RESERVED;

class JdbcSandboxRuntimeNodeRegistryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-07-15T12:00:00Z");

    @Test
    void shouldProtectLiveNodeLeaseAndAllowExpiredTakeover() {
        DriverManagerDataSource dataSource = dataSource();
        createSchema(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcSandboxRuntimeNodeRegistryAdapter adapter = new JdbcSandboxRuntimeNodeRegistryAdapter(dataSource);
        SandboxRuntimeNodeRegistration first = registration(NOW, NOW.plusSeconds(45));

        assertThat(adapter.heartbeat(
                first,
                "owner-a",
                "http://runtime-a:8080/internal/sandbox/runtime",
                Duration.ofSeconds(45))).isPresent();
        assertThat(adapter.findLiveEndpoint("local-container-docker"))
                .get()
                .extracting(endpoint -> endpoint.transportUri())
                .isEqualTo(URI.create("http://runtime-a:8080/internal/sandbox/runtime"));
        assertThat(adapter.findLiveEndpoint("local-container-docker")).get()
                .extracting(endpoint -> endpoint.ownerId())
                .isEqualTo("owner-a");
        assertThat(adapter.listLiveEndpoints()).singleElement()
                .satisfies(endpoint -> {
                    assertThat(endpoint.nodeId()).isEqualTo("local-container-docker");
                    assertThat(endpoint.observedActiveSessionCount()).isZero();
                    assertThat(endpoint.observedActiveSessionLimit()).isZero();
                    assertThat(endpoint.observedWorkspaceFreeBytes()).isEqualTo(1024L);
                });
        assertThat(adapter.isLiveOwner("local-container-docker", "owner-a")).isTrue();
        assertThat(adapter.isLiveOwner("local-container-docker", "owner-b")).isFalse();
        assertThat(adapter.reserveOperationLease(
                "local-container-docker", "owner-a", Duration.ofSeconds(90))).isTrue();
        Timestamp operationLeaseExpiry = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM sa_sandbox_runtime_node WHERE node_id = 'local-container-docker'",
                Timestamp.class);
        assertThat(adapter.reserveOperationLease(
                "local-container-docker", "owner-b", Duration.ofSeconds(90))).isFalse();
        assertThat(adapter.heartbeat(
                registration(NOW.plusSeconds(1), NOW.plusSeconds(46)),
                "owner-b",
                "http://runtime-b:8080/internal/sandbox/runtime",
                Duration.ofSeconds(45))).isEmpty();
        assertThat(adapter.findLiveEndpoint("local-container-docker"))
                .get()
                .extracting(endpoint -> endpoint.transportUri())
                .isEqualTo(URI.create("http://runtime-a:8080/internal/sandbox/runtime"));
        assertThat(adapter.heartbeat(
                registration(NOW.plusSeconds(2), NOW.plusSeconds(47)),
                "owner-a",
                "http://runtime-a-new:8080/internal/sandbox/runtime/",
                Duration.ofSeconds(45))).isPresent();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT expires_at FROM sa_sandbox_runtime_node WHERE node_id = 'local-container-docker'",
                Timestamp.class)).isEqualTo(operationLeaseExpiry);
        assertThat(adapter.findLiveEndpoint("local-container-docker"))
                .get()
                .extracting(endpoint -> endpoint.transportUri())
                .isEqualTo(URI.create("http://runtime-a-new:8080/internal/sandbox/runtime"));
        Timestamp databaseNow = jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        jdbcTemplate.update(
                "UPDATE sa_sandbox_runtime_node SET expires_at = ?",
                Timestamp.from(databaseNow.toInstant().minusSeconds(1L)));
        assertThat(adapter.heartbeat(
                registration(NOW.plusSeconds(48), NOW.plusSeconds(93)),
                "owner-b",
                "http://runtime-b:8080/internal/sandbox/runtime",
                Duration.ofSeconds(45))).isPresent();
        assertThat(adapter.findLiveEndpoint("local-container-docker"))
                .get()
                .extracting(endpoint -> endpoint.transportUri())
                .isEqualTo(URI.create("http://runtime-b:8080/internal/sandbox/runtime"));

        List<SandboxRuntimeNodeRegistration> registrations = adapter.listRegistrations(10);
        assertThat(registrations).hasSize(1);
        assertThat(registrations.get(0).nodeId()).isEqualTo("local-container-docker");
        assertThat(registrations.get(0).registrationStatus()).isEqualTo("LIVE");
        assertThat(adapter.release("local-container-docker", "owner-a")).isFalse();
        assertThat(adapter.release("local-container-docker", "owner-b")).isTrue();
        assertThat(adapter.findLiveEndpoint("local-container-docker")).isEmpty();
        assertThat(adapter.isLiveOwner("local-container-docker", "owner-b")).isFalse();
        assertThat(adapter.listRegistrations(10)).singleElement()
                .satisfies(registration -> assertThat(registration.registrationStatus()).isEqualTo("STALE"));
        assertThat(adapter.deleteStaleRegistrations(Duration.ofDays(1), 10)).isZero();
        Timestamp cleanupNow = jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        jdbcTemplate.update(
                "UPDATE sa_sandbox_runtime_node SET expires_at = ? WHERE node_id = 'local-container-docker'",
                Timestamp.from(cleanupNow.toInstant().minus(Duration.ofDays(2))));
        assertThat(adapter.deleteStaleRegistrations(Duration.ofDays(1), 10)).isEqualTo(1);
        assertThat(adapter.listRegistrations(10)).isEmpty();
    }

    @Test
    void shouldReserveFiniteNodeCapacityAgainstPendingAndPersistedSessions() {
        DriverManagerDataSource dataSource = dataSource();
        createSchema(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcSandboxRuntimeNodeRegistryAdapter adapter = new JdbcSandboxRuntimeNodeRegistryAdapter(dataSource);

        assertThat(adapter.tryReserve("unregistered-node", "reservation-unmanaged", Duration.ofMinutes(1)))
                .isEqualTo(NOT_REQUIRED);
        assertThat(adapter.heartbeat(
                registration(NOW, NOW.plusSeconds(45), 1),
                "owner-a",
                "http://runtime-a:8080/internal/sandbox/runtime",
                Duration.ofSeconds(45))).isPresent();
        assertThat(adapter.tryReserve("local-container-docker", "reservation-a", Duration.ofMinutes(1)))
                .isEqualTo(RESERVED);
        assertThat(adapter.tryReserve("local-container-docker", "reservation-b", Duration.ofMinutes(1)))
                .isEqualTo(REJECTED);
        assertThat(adapter.release("reservation-a")).isTrue();

        jdbcTemplate.update("""
                INSERT INTO sa_sandbox_session
                (session_id, tenant_id, run_id, runtime_type, status, reason_code, profile_id,
                 runtime_node_id, expires_at, created_at, updated_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                "session-a",
                "default",
                "run-a",
                "CODE_INTERPRETER",
                "CREATED",
                "VALID_REQUEST",
                "python-small",
                "local-container-docker",
                Timestamp.from(NOW.plusSeconds(3600)),
                Timestamp.from(NOW),
                Timestamp.from(NOW));
        assertThat(adapter.tryReserve("local-container-docker", "reservation-b", Duration.ofMinutes(1)))
                .isEqualTo(REJECTED);

        jdbcTemplate.update("UPDATE sa_sandbox_session SET status = 'CANCELLED' WHERE session_id = 'session-a'");
        assertThat(adapter.tryReserve("local-container-docker", "reservation-b", Duration.ofMinutes(1)))
                .isEqualTo(RESERVED);
        jdbcTemplate.update(
                "UPDATE sa_sandbox_runtime_capacity_reservation SET expires_at = ? WHERE reservation_id = ?",
                Timestamp.from(Instant.now().minusSeconds(1)),
                "reservation-b");
        assertThat(adapter.tryReserve("local-container-docker", "reservation-c", Duration.ofMinutes(1)))
                .isEqualTo(RESERVED);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM sa_sandbox_runtime_capacity_reservation",
                Integer.class)).isEqualTo(1);
        assertThat(adapter.release("reservation-c")).isTrue();
    }

    @Test
    void shouldPersistOperatorDrainAcrossHeartbeatsAndResumeObservedAdmission() {
        DriverManagerDataSource dataSource = dataSource();
        createSchema(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcSandboxRuntimeNodeRegistryAdapter adapter = auditedAdapter(dataSource);

        assertThat(adapter.setOperatorDraining(change("audit-unknown", "unknown-node", true, "admin"))).isEmpty();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sa_audit_event", Integer.class)).isZero();
        assertThat(adapter.heartbeat(
                registration(NOW, NOW.plusSeconds(45), 1),
                "owner-a",
                "http://runtime-a:8080/internal/sandbox/runtime",
                Duration.ofSeconds(45))).isPresent();

        assertThat(adapter.setOperatorDraining(change("audit-drain", "local-container-docker", true, "admin")))
                .get()
                .satisfies(override -> {
                    assertThat(override.draining()).isTrue();
                    assertThat(override.operatorId()).isEqualTo("admin");
                    Map<String, Object> audit = jdbcTemplate.queryForMap(
                            "SELECT * FROM sa_audit_event WHERE audit_id = 'audit-drain'");
                    assertThat(audit.get("TENANT_ID")).isEqualTo("tenant-a");
                    assertThat(audit.get("EVENT_TYPE"))
                            .isEqualTo("SANDBOX_RUNTIME_NODE_ADMISSION_CHANGED");
                    assertThat(audit.get("ACTOR_TYPE")).isEqualTo("USER");
                    assertThat(audit.get("ACTOR_ID")).isEqualTo("admin");
                    assertThat(audit.get("RESOURCE_TYPE")).isEqualTo("SANDBOX_RUNTIME_NODE");
                    assertThat(audit.get("RESOURCE_ID")).isEqualTo("local-container-docker");
                    assertThat(audit.get("REDACTED_PAYLOAD")).isEqualTo("{\"draining\":true}");
                    assertThat(((Timestamp) audit.get("OCCURRED_AT")).toInstant())
                            .isEqualTo(override.updatedAt());
                });
        assertThat(adapter.listRegistrations(10)).singleElement()
                .satisfies(registration -> {
                    assertThat(registration.observedAdmissionAvailable()).isTrue();
                    assertThat(registration.observedAdmissionStatus()).isEqualTo("AVAILABLE");
                    assertThat(registration.effectiveAdmissionAvailable()).isFalse();
                    assertThat(registration.effectiveAdmissionStatus()).isEqualTo("DRAINING");
                    assertThat(registration.operatorDraining()).isTrue();
                    assertThat(registration.operatorId()).isEqualTo("admin");
                    assertThat(registration.operatorUpdatedAt()).isNotNull();
                });
        assertThat(adapter.findLiveEndpoint("local-container-docker"))
                .get()
                .satisfies(endpoint -> {
                    assertThat(endpoint.observedAdmissionAvailable()).isFalse();
                    assertThat(endpoint.observedAdmissionStatus()).isEqualTo("DRAINING");
                });
        assertThat(adapter.tryReserve(
                "local-container-docker", "reservation-drained", Duration.ofMinutes(1)))
                .isEqualTo(REJECTED);

        assertThat(adapter.heartbeat(
                registration(NOW.plusSeconds(1), NOW.plusSeconds(46), 1),
                "owner-a",
                "http://runtime-a:8080/internal/sandbox/runtime",
                Duration.ofSeconds(45))).isPresent();
        assertThat(adapter.listRegistrations(10)).singleElement()
                .satisfies(registration -> {
                    assertThat(registration.observedAdmissionStatus()).isEqualTo("AVAILABLE");
                    assertThat(registration.effectiveAdmissionStatus()).isEqualTo("DRAINING");
                });

        adapter = auditedAdapter(dataSource);
        assertThat(adapter.listRegistrations(10)).singleElement()
                .satisfies(registration -> assertThat(registration.effectiveAdmissionStatus())
                        .isEqualTo("DRAINING"));
        Timestamp cleanupNow = jdbcTemplate.queryForObject("SELECT CURRENT_TIMESTAMP", Timestamp.class);
        jdbcTemplate.update(
                "UPDATE sa_sandbox_runtime_node SET expires_at = ? WHERE node_id = 'local-container-docker'",
                Timestamp.from(cleanupNow.toInstant().minus(Duration.ofMinutes(2))));
        assertThat(adapter.deleteStaleRegistrations(Duration.ofMinutes(1), 10)).isEqualTo(1);
        assertThat(adapter.listRegistrations(10)).isEmpty();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draining FROM sa_sandbox_runtime_node_admission_override "
                        + "WHERE node_id = 'local-container-docker'",
                Boolean.class)).isTrue();

        adapter = auditedAdapter(dataSource);
        assertThat(adapter.heartbeat(
                registration(NOW.plusSeconds(2), NOW.plusSeconds(47), 1),
                "owner-b",
                "http://runtime-b:8080/internal/sandbox/runtime",
                Duration.ofSeconds(45))).isPresent();
        assertThat(adapter.listRegistrations(10)).singleElement()
                .satisfies(registration -> {
                    assertThat(registration.observedAdmissionStatus()).isEqualTo("AVAILABLE");
                    assertThat(registration.effectiveAdmissionStatus()).isEqualTo("DRAINING");
                    assertThat(registration.operatorDraining()).isTrue();
                });
        assertThat(adapter.tryReserve(
                "local-container-docker", "reservation-reregistered-drained", Duration.ofMinutes(1)))
                .isEqualTo(REJECTED);

        assertThat(adapter.setOperatorDraining(change("audit-resume", "local-container-docker", false, "admin")))
                .get()
                .satisfies(override -> assertThat(override.draining()).isFalse());
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sa_audit_event", Integer.class)).isEqualTo(2);
        assertThat(adapter.listRegistrations(10)).singleElement()
                .satisfies(registration -> {
                    assertThat(registration.effectiveAdmissionAvailable()).isTrue();
                    assertThat(registration.effectiveAdmissionStatus()).isEqualTo("AVAILABLE");
                    assertThat(registration.operatorDraining()).isFalse();
                    assertThat(registration.operatorId()).isEqualTo("admin");
                });
        assertThat(adapter.tryReserve(
                "local-container-docker", "reservation-resumed", Duration.ofMinutes(1)))
                .isEqualTo(RESERVED);
        assertThat(adapter.release("reservation-resumed")).isTrue();
    }

    @Test
    void shouldRollbackAdmissionOverrideWhenAuditPersistenceFails() {
        DriverManagerDataSource dataSource = dataSource();
        createSchema(dataSource);
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        JdbcSandboxRuntimeNodeRegistryAdapter adapter = auditedAdapter(dataSource);

        assertThat(adapter.heartbeat(
                registration(NOW, NOW.plusSeconds(45), 1),
                "owner-a",
                "http://runtime-a:8080/internal/sandbox/runtime",
                Duration.ofSeconds(45))).isPresent();
        adapter.setOperatorDraining(change("audit-drain", "local-container-docker", true, "admin"));

        JdbcAuditEventRepositoryAdapter failingAuditRepository = new JdbcAuditEventRepositoryAdapter(dataSource) {
            @Override
            public AuditEvent save(AuditEvent event) {
                throw new IllegalStateException("audit persistence unavailable");
            }
        };
        JdbcSandboxRuntimeNodeRegistryAdapter failingAdapter =
                new JdbcSandboxRuntimeNodeRegistryAdapter(dataSource, failingAuditRepository);

        assertThatThrownBy(() -> failingAdapter.setOperatorDraining(
                change("audit-resume", "local-container-docker", false, "other-admin")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("audit persistence unavailable");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT draining FROM sa_sandbox_runtime_node_admission_override "
                        + "WHERE node_id = 'local-container-docker'",
                Boolean.class)).isTrue();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT operator_id FROM sa_sandbox_runtime_node_admission_override "
                        + "WHERE node_id = 'local-container-docker'",
                String.class)).isEqualTo("admin");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM sa_audit_event", Integer.class)).isEqualTo(1);
    }

    private static JdbcSandboxRuntimeNodeRegistryAdapter auditedAdapter(DriverManagerDataSource dataSource) {
        return new JdbcSandboxRuntimeNodeRegistryAdapter(
                dataSource,
                new JdbcAuditEventRepositoryAdapter(dataSource));
    }

    private static SandboxRuntimeNodeAdmissionChange change(String auditId,
                                                            String nodeId,
                                                            boolean draining,
                                                            String operatorId) {
        return new SandboxRuntimeNodeAdmissionChange(auditId, nodeId, draining, operatorId, "tenant-a");
    }

    private static SandboxRuntimeNodeRegistration registration(Instant heartbeatAt, Instant expiresAt) {
        return registration(heartbeatAt, expiresAt, 0);
    }

    private static SandboxRuntimeNodeRegistration registration(Instant heartbeatAt,
                                                               Instant expiresAt,
                                                               int activeSessionLimit) {
        return new SandboxRuntimeNodeRegistration(
                "local-container-docker",
                "container",
                "docker",
                "HEALTHY",
                true,
                "AVAILABLE",
                0,
                activeSessionLimit,
                1024L,
                heartbeatAt,
                heartbeatAt,
                expiresAt,
                SandboxRuntimeNodeRegistration.REGISTRATION_LIVE);
    }

    private static DriverManagerDataSource dataSource() {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:sandbox-runtime-node-registry-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }

    private static void createSchema(DriverManagerDataSource dataSource) {
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE sa_sandbox_runtime_node (
                  node_id VARCHAR(64) PRIMARY KEY,
                  owner_id VARCHAR(64) NOT NULL,
                  transport_uri VARCHAR(512) NOT NULL DEFAULT '',
                  runtime VARCHAR(32) NOT NULL,
                  engine VARCHAR(32) NOT NULL,
                  health_status VARCHAR(32) NOT NULL,
                  admission_available BOOLEAN NOT NULL,
                  admission_status VARCHAR(32) NOT NULL,
                  active_session_count INTEGER NOT NULL,
                  active_session_limit INTEGER NOT NULL,
                  workspace_free_bytes BIGINT NOT NULL,
                  observed_at TIMESTAMP NOT NULL,
                  heartbeat_at TIMESTAMP NOT NULL,
                  expires_at TIMESTAMP NOT NULL,
                  registered_at TIMESTAMP NOT NULL
                )
                """);
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE sa_sandbox_runtime_capacity_reservation (
                  reservation_id VARCHAR(64) PRIMARY KEY,
                  node_id VARCHAR(64) NOT NULL,
                  expires_at TIMESTAMP NOT NULL,
                  created_at TIMESTAMP NOT NULL
                )
                """);
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE sa_sandbox_runtime_node_admission_override (
                  node_id VARCHAR(64) PRIMARY KEY,
                  draining BOOLEAN NOT NULL,
                  operator_id VARCHAR(128) NOT NULL,
                  updated_at TIMESTAMP NOT NULL
                )
                """);
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE sa_sandbox_session (
                  session_id VARCHAR(64) PRIMARY KEY,
                  tenant_id VARCHAR(64) NOT NULL,
                  run_id VARCHAR(64) NOT NULL,
                  runtime_type VARCHAR(32) NOT NULL,
                  status VARCHAR(32) NOT NULL,
                  reason_code VARCHAR(64) NOT NULL,
                  profile_id VARCHAR(64) NOT NULL,
                  runtime_node_id VARCHAR(128),
                  expires_at TIMESTAMP NOT NULL,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NOT NULL
                )
                """);
        new JdbcTemplate(dataSource).execute("""
                CREATE TABLE sa_audit_event (
                  audit_id VARCHAR(64) PRIMARY KEY,
                  tenant_id VARCHAR(64) NOT NULL,
                  event_type VARCHAR(64) NOT NULL,
                  actor_type VARCHAR(32) NOT NULL,
                  actor_id VARCHAR(128) NOT NULL,
                  run_id VARCHAR(64),
                  agent_id VARCHAR(64),
                  resource_type VARCHAR(64),
                  resource_id VARCHAR(128),
                  redacted_payload CLOB NOT NULL,
                  occurred_at TIMESTAMP NOT NULL
                )
                """);
    }
}

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
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Duration;
import java.time.Instant;
import java.net.URI;
import java.sql.Timestamp;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

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
    }

    private static SandboxRuntimeNodeRegistration registration(Instant heartbeatAt, Instant expiresAt) {
        return new SandboxRuntimeNodeRegistration(
                "local-container-docker",
                "container",
                "docker",
                "HEALTHY",
                true,
                "AVAILABLE",
                0,
                0,
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
    }
}

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

import com.miracle.ai.seahorse.agent.kernel.domain.agent.context.ContextSensitivity;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifact;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxArtifactScanStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcSandboxRepositoryAdapterTests {

    private static final Instant NOW = Instant.parse("2026-05-26T00:00:00Z");

    @Test
    void shouldSaveFindAndListSandboxSessionsExecutionsAndVisibleArtifacts() {
        DriverManagerDataSource dataSource = dataSource("sandbox-repository");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSandboxSchema(jdbcTemplate);
        JdbcSandboxRepositoryAdapter adapter = new JdbcSandboxRepositoryAdapter(dataSource);

        SandboxSession session = adapter.saveSession(SandboxSession.created(
                "session-1",
                "tenant-a",
                "run-1",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW));
        SandboxSession newerSession = adapter.saveSession(SandboxSession.created(
                "session-2",
                "tenant-a",
                "run-2",
                SandboxRuntimeType.FILE_CONVERSION,
                NOW.plusSeconds(90)));
        adapter.saveSession(SandboxSession.created(
                "session-other-tenant",
                "tenant-b",
                "run-other",
                SandboxRuntimeType.CODE_INTERPRETER,
                NOW.plusSeconds(120)));
        SandboxSession closed = new SandboxSession(
                session.sessionId(),
                session.tenantId(),
                session.runId(),
                session.runtimeType(),
                SandboxExecutionStatus.CANCELLED,
                SandboxPolicyReasonCode.VALID_REQUEST,
                session.createdAt(),
                NOW.plusSeconds(30));
        SandboxExecution execution = adapter.saveExecution(SandboxExecution.created(
                        "exec-1",
                        session.sessionId(),
                        session.runtimeType(),
                        NOW.plusSeconds(1))
                .markRunning(NOW.plusSeconds(2))
                .markSucceeded(NOW.plusSeconds(3), "converted"));
        SandboxExecution failed = adapter.saveExecution(SandboxExecution.failed(
                "exec-2",
                session.sessionId(),
                session.runtimeType(),
                NOW.plusSeconds(4),
                SandboxPolicyReasonCode.RUNTIME_UNSUPPORTED));
        SandboxArtifact clean = adapter.save(artifact(
                "artifact-clean",
                execution.executionId(),
                SandboxArtifactScanStatus.CLEAN,
                ContextSensitivity.INTERNAL));
        adapter.save(artifact(
                "artifact-secret",
                execution.executionId(),
                SandboxArtifactScanStatus.CLEAN,
                ContextSensitivity.SECRET));
        adapter.save(artifact(
                "artifact-pending",
                execution.executionId(),
                SandboxArtifactScanStatus.PENDING,
                ContextSensitivity.INTERNAL));
        SandboxArtifact redacted = adapter.save(artifact(
                "artifact-redacted",
                execution.executionId(),
                SandboxArtifactScanStatus.REDACTED,
                ContextSensitivity.CONFIDENTIAL));
        adapter.saveSession(closed);

        assertThat(adapter.findSessionById("session-1")).contains(closed);
        assertThat(adapter.findSessionById("session-1").orElseThrow().profileId()).isEqualTo("python-small");
        assertThat(adapter.findSessionById("session-1").orElseThrow().expiresAt()).isEqualTo(NOW.plusSeconds(3600));
        assertThat(adapter.listSessionsByTenant("tenant-a", 2)).containsExactly(newerSession, closed);
        assertThat(adapter.listSessionsByTenant("tenant-b", 10))
                .extracting(SandboxSession::sessionId)
                .containsExactly("session-other-tenant");
        assertThat(adapter.listActiveSessionIds())
                .containsExactlyInAnyOrder("session-2", "session-other-tenant");
        assertThat(adapter.findExecutionById("exec-2")).contains(failed);
        assertThat(adapter.listExecutionsBySession("session-1"))
                .extracting(SandboxExecution::executionId)
                .containsExactly("exec-1", "exec-2");
        assertThat(adapter.listArtifactsBySession("session-1"))
                .extracting(SandboxArtifact::artifactId)
                .containsExactly("artifact-clean", "artifact-pending", "artifact-redacted", "artifact-secret");
        assertThat(adapter.listPromptVisibleBySession("session-1")).containsExactly(clean, redacted);
        assertThat(adapter.findArtifactById("artifact-clean")).contains(clean);
        assertThat(adapter.findArtifactById("artifact-clean").orElseThrow().scanSummary())
                .isEqualTo("scan summary for artifact-clean");
        assertThat(adapter.findArtifactById("artifact-clean").orElseThrow().redactionSummaryJson())
                .contains("\"decision\":\"CLEAN\"");
        assertThat(adapter.findArtifactById(" ")).isEmpty();

        SandboxRuntimeProfilePolicy runtimeProfilePolicy = adapter.upsert(new SandboxRuntimeProfilePolicy(
                null,
                "tenant-a",
                SandboxRuntimeType.CODE_INTERPRETER,
                "python-small",
                SandboxRuntimeProfilePolicyStatus.ACTIVE,
                120,
                false,
                NOW,
                NOW));
        SandboxRuntimeProfilePolicy updatedRuntimeProfilePolicy = adapter.upsert(new SandboxRuntimeProfilePolicy(
                runtimeProfilePolicy.policyId(),
                "tenant-a",
                SandboxRuntimeType.CODE_INTERPRETER,
                "python-small",
                SandboxRuntimeProfilePolicyStatus.DISABLED,
                300,
                false,
                runtimeProfilePolicy.createdAt(),
                NOW.plusSeconds(10)));
        assertThat(adapter.findById(runtimeProfilePolicy.policyId())).contains(updatedRuntimeProfilePolicy);
        assertThat(adapter.findByTenantAndRuntimeType("tenant-a", SandboxRuntimeType.CODE_INTERPRETER))
                .contains(updatedRuntimeProfilePolicy);
        assertThat(adapter.listByTenant("tenant-a")).containsExactly(updatedRuntimeProfilePolicy);
    }

    @Test
    void shouldListExpiredActiveSandboxSessionsByTenantAndExpirationOrder() {
        DriverManagerDataSource dataSource = dataSource("sandbox-repository-expired");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        createSandboxSchema(jdbcTemplate);
        JdbcSandboxRepositoryAdapter adapter = new JdbcSandboxRepositoryAdapter(dataSource);

        SandboxSession laterExpired = adapter.saveSession(session(
                "session-expired-later",
                "tenant-a",
                SandboxExecutionStatus.CREATED,
                NOW.minusSeconds(60),
                NOW.minusSeconds(3600)));
        SandboxSession earlierExpired = adapter.saveSession(session(
                "session-expired-earlier",
                "tenant-a",
                SandboxExecutionStatus.RUNNING,
                NOW.minusSeconds(120),
                NOW.minusSeconds(3600)));
        adapter.saveSession(session(
                "session-not-expired",
                "tenant-a",
                SandboxExecutionStatus.CREATED,
                NOW.plusSeconds(60),
                NOW.minusSeconds(60)));
        adapter.saveSession(session(
                "session-terminal",
                "tenant-a",
                SandboxExecutionStatus.TIMED_OUT,
                NOW.minusSeconds(180),
                NOW.minusSeconds(3600)));
        adapter.saveSession(session(
                "session-other-tenant",
                "tenant-b",
                SandboxExecutionStatus.CREATED,
                NOW.minusSeconds(180),
                NOW.minusSeconds(3600)));

        assertThat(adapter.listExpiredActiveSessions("tenant-a", NOW, 10))
                .containsExactly(earlierExpired, laterExpired);
        assertThat(adapter.listExpiredActiveSessions("tenant-a", NOW, 1))
                .containsExactly(earlierExpired);
        assertThat(adapter.listExpiredActiveSessions("tenant-b", NOW, 10))
                .extracting(SandboxSession::sessionId)
                .containsExactly("session-other-tenant");
    }

    private static SandboxArtifact artifact(String artifactId,
                                            String executionId,
                                            SandboxArtifactScanStatus scanStatus,
                                            ContextSensitivity sensitivity) {
        return new SandboxArtifact(
                artifactId,
                "session-1",
                executionId,
                "s3://sandbox/" + artifactId,
                "text/plain",
                scanStatus,
                sensitivity,
                "scan summary for " + artifactId,
                NOW.plusSeconds(5));
    }

    private static SandboxSession session(String sessionId,
                                          String tenantId,
                                          SandboxExecutionStatus status,
                                          Instant expiresAt,
                                          Instant createdAt) {
        return new SandboxSession(
                sessionId,
                tenantId,
                "run-" + sessionId,
                SandboxRuntimeType.CODE_INTERPRETER,
                status,
                SandboxPolicyReasonCode.VALID_REQUEST,
                "python-small",
                expiresAt,
                createdAt,
                createdAt);
    }

    private static DriverManagerDataSource dataSource(String name) {
        return new DriverManagerDataSource(
                "jdbc:h2:mem:" + name + "-" + System.nanoTime() + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
    }

    static void createSandboxSchema(JdbcTemplate jdbcTemplate) {
        jdbcTemplate.execute("""
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
        jdbcTemplate.execute("""
                CREATE TABLE sa_sandbox_execution (
                    execution_id VARCHAR(64) PRIMARY KEY,
                    session_id VARCHAR(64) NOT NULL,
                    runtime_type VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    result_summary VARCHAR(1000),
                    reason_code VARCHAR(64) NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_sandbox_artifact (
                    artifact_id VARCHAR(64) PRIMARY KEY,
                    session_id VARCHAR(64) NOT NULL,
                    execution_id VARCHAR(64) NOT NULL,
                    object_uri VARCHAR(1000) NOT NULL,
                    media_type VARCHAR(128) NOT NULL,
                    scan_status VARCHAR(32) NOT NULL,
                    sensitivity VARCHAR(32) NOT NULL,
                    scan_summary VARCHAR(256),
                    redaction_summary_json VARCHAR(2048),
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE sa_sandbox_runtime_profile_policy (
                    policy_id VARCHAR(96) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    runtime_type VARCHAR(32) NOT NULL,
                    profile_id VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    session_ttl_seconds BIGINT NOT NULL,
                    network_allowed BOOLEAN NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("CREATE INDEX idx_sa_sandbox_execution_session ON sa_sandbox_execution(session_id, created_at)");
        jdbcTemplate.execute("CREATE INDEX idx_sa_sandbox_session_tenant_updated ON sa_sandbox_session(tenant_id, updated_at, created_at)");
        jdbcTemplate.execute("CREATE INDEX idx_sa_sandbox_session_expires ON sa_sandbox_session(tenant_id, expires_at)");
        jdbcTemplate.execute("CREATE INDEX idx_sa_sandbox_artifact_session ON sa_sandbox_artifact(session_id, created_at)");
        jdbcTemplate.execute("CREATE UNIQUE INDEX uk_sa_sandbox_runtime_profile_policy_runtime ON sa_sandbox_runtime_profile_policy(tenant_id, runtime_type)");
    }
}

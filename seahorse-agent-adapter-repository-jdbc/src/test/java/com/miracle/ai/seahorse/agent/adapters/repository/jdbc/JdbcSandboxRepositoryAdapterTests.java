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
        assertThat(adapter.findExecutionById("exec-2")).contains(failed);
        assertThat(adapter.listExecutionsBySession("session-1"))
                .extracting(SandboxExecution::executionId)
                .containsExactly("exec-1", "exec-2");
        assertThat(adapter.listArtifactsBySession("session-1"))
                .extracting(SandboxArtifact::artifactId)
                .containsExactly("artifact-clean", "artifact-pending", "artifact-redacted", "artifact-secret");
        assertThat(adapter.listPromptVisibleBySession("session-1")).containsExactly(clean, redacted);
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
                NOW.plusSeconds(5));
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
                    created_at TIMESTAMP NOT NULL
                )
                """);
        jdbcTemplate.execute("CREATE INDEX idx_sa_sandbox_execution_session ON sa_sandbox_execution(session_id, created_at)");
        jdbcTemplate.execute("CREATE INDEX idx_sa_sandbox_artifact_session ON sa_sandbox_artifact(session_id, created_at)");
    }
}

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
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class JdbcSandboxRepositoryAdapter implements SandboxSessionRepositoryPort,
        SandboxExecutionRepositoryPort,
        SandboxArtifactPort,
        SandboxArtifactQueryPort {

    private static final String SESSION_COLUMNS = """
            session_id, tenant_id, run_id, runtime_type, status, reason_code, created_at, updated_at
            """;
    private static final String EXECUTION_COLUMNS = """
            execution_id, session_id, runtime_type, status, result_summary, reason_code, created_at, updated_at
            """;
    private static final String ARTIFACT_COLUMNS = """
            artifact_id, session_id, execution_id, object_uri, media_type, scan_status, sensitivity, created_at
            """;

    private static final String SQL_INSERT_SESSION = """
            INSERT INTO sa_sandbox_session
            (session_id, tenant_id, run_id, runtime_type, status, reason_code, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_SESSION = """
            UPDATE sa_sandbox_session
            SET tenant_id = ?,
                run_id = ?,
                runtime_type = ?,
                status = ?,
                reason_code = ?,
                created_at = ?,
                updated_at = ?
            WHERE session_id = ?
            """;
    private static final String SQL_FIND_SESSION = """
            SELECT %s
            FROM sa_sandbox_session
            WHERE session_id = ?
            """.formatted(SESSION_COLUMNS);

    private static final String SQL_INSERT_EXECUTION = """
            INSERT INTO sa_sandbox_execution
            (execution_id, session_id, runtime_type, status, result_summary, reason_code, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_EXECUTION = """
            UPDATE sa_sandbox_execution
            SET session_id = ?,
                runtime_type = ?,
                status = ?,
                result_summary = ?,
                reason_code = ?,
                created_at = ?,
                updated_at = ?
            WHERE execution_id = ?
            """;
    private static final String SQL_FIND_EXECUTION = """
            SELECT %s
            FROM sa_sandbox_execution
            WHERE execution_id = ?
            """.formatted(EXECUTION_COLUMNS);
    private static final String SQL_LIST_EXECUTIONS_BY_SESSION = """
            SELECT %s
            FROM sa_sandbox_execution
            WHERE session_id = ?
            ORDER BY created_at ASC, execution_id ASC
            """.formatted(EXECUTION_COLUMNS);

    private static final String SQL_INSERT_ARTIFACT = """
            INSERT INTO sa_sandbox_artifact
            (artifact_id, session_id, execution_id, object_uri, media_type, scan_status, sensitivity, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_ARTIFACT = """
            UPDATE sa_sandbox_artifact
            SET session_id = ?,
                execution_id = ?,
                object_uri = ?,
                media_type = ?,
                scan_status = ?,
                sensitivity = ?,
                created_at = ?
            WHERE artifact_id = ?
            """;
    private static final String SQL_FIND_ARTIFACT = """
            SELECT %s
            FROM sa_sandbox_artifact
            WHERE artifact_id = ?
            """.formatted(ARTIFACT_COLUMNS);
    private static final String SQL_LIST_ARTIFACTS_BY_SESSION = """
            SELECT %s
            FROM sa_sandbox_artifact
            WHERE session_id = ?
            ORDER BY created_at ASC
            """.formatted(ARTIFACT_COLUMNS);
    private static final String SQL_LIST_PROMPT_VISIBLE_BY_SESSION = """
            SELECT %s
            FROM sa_sandbox_artifact
            WHERE session_id = ?
              AND scan_status = ?
              AND sensitivity <> ?
            ORDER BY created_at ASC
            """.formatted(ARTIFACT_COLUMNS);

    private final JdbcTemplate jdbcTemplate;

    public JdbcSandboxRepositoryAdapter(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    @Override
    public SandboxSession saveSession(SandboxSession session) {
        SandboxSession safeSession = Objects.requireNonNull(session, "session must not be null");
        if (findSessionById(safeSession.sessionId()).isPresent()) {
            updateSession(safeSession);
            return safeSession;
        }
        insertSession(safeSession);
        return safeSession;
    }

    @Override
    public Optional<SandboxSession> findSessionById(String sessionId) {
        if (!hasText(sessionId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_SESSION, this::mapSession, sessionId.trim()).stream().findFirst();
    }

    @Override
    public SandboxExecution saveExecution(SandboxExecution execution) {
        SandboxExecution safeExecution = Objects.requireNonNull(execution, "execution must not be null");
        if (findExecutionById(safeExecution.executionId()).isPresent()) {
            updateExecution(safeExecution);
            return safeExecution;
        }
        insertExecution(safeExecution);
        return safeExecution;
    }

    @Override
    public Optional<SandboxExecution> findExecutionById(String executionId) {
        if (!hasText(executionId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_EXECUTION, this::mapExecution, executionId.trim()).stream().findFirst();
    }

    @Override
    public List<SandboxExecution> listExecutionsBySession(String sessionId) {
        if (!hasText(sessionId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_EXECUTIONS_BY_SESSION, this::mapExecution, sessionId.trim());
    }

    @Override
    public SandboxArtifact save(SandboxArtifact artifact) {
        SandboxArtifact safeArtifact = Objects.requireNonNull(artifact, "artifact must not be null");
        if (findArtifactById(safeArtifact.artifactId()).isPresent()) {
            updateArtifact(safeArtifact);
            return safeArtifact;
        }
        insertArtifact(safeArtifact);
        return safeArtifact;
    }

    @Override
    public List<SandboxArtifact> listArtifactsBySession(String sessionId) {
        if (!hasText(sessionId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_ARTIFACTS_BY_SESSION, this::mapArtifact, sessionId.trim());
    }

    @Override
    public List<SandboxArtifact> listPromptVisibleBySession(String sessionId) {
        if (!hasText(sessionId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_PROMPT_VISIBLE_BY_SESSION,
                this::mapArtifact,
                sessionId.trim(),
                SandboxArtifactScanStatus.CLEAN.name(),
                ContextSensitivity.SECRET.name());
    }

    private void insertSession(SandboxSession session) {
        jdbcTemplate.update(SQL_INSERT_SESSION,
                session.sessionId(),
                session.tenantId(),
                session.runId(),
                session.runtimeType().name(),
                session.status().name(),
                session.reasonCode().name(),
                toTimestamp(session.createdAt()),
                toTimestamp(session.updatedAt()));
    }

    private void updateSession(SandboxSession session) {
        jdbcTemplate.update(SQL_UPDATE_SESSION,
                session.tenantId(),
                session.runId(),
                session.runtimeType().name(),
                session.status().name(),
                session.reasonCode().name(),
                toTimestamp(session.createdAt()),
                toTimestamp(session.updatedAt()),
                session.sessionId());
    }

    private void insertExecution(SandboxExecution execution) {
        jdbcTemplate.update(SQL_INSERT_EXECUTION,
                execution.executionId(),
                execution.sessionId(),
                execution.runtimeType().name(),
                execution.status().name(),
                execution.resultSummary(),
                execution.reasonCode().name(),
                toTimestamp(execution.createdAt()),
                toTimestamp(execution.updatedAt()));
    }

    private void updateExecution(SandboxExecution execution) {
        jdbcTemplate.update(SQL_UPDATE_EXECUTION,
                execution.sessionId(),
                execution.runtimeType().name(),
                execution.status().name(),
                execution.resultSummary(),
                execution.reasonCode().name(),
                toTimestamp(execution.createdAt()),
                toTimestamp(execution.updatedAt()),
                execution.executionId());
    }

    private Optional<SandboxArtifact> findArtifactById(String artifactId) {
        if (!hasText(artifactId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_ARTIFACT, this::mapArtifact, artifactId.trim()).stream().findFirst();
    }

    private void insertArtifact(SandboxArtifact artifact) {
        jdbcTemplate.update(SQL_INSERT_ARTIFACT,
                artifact.artifactId(),
                artifact.sessionId(),
                artifact.executionId(),
                artifact.objectUri(),
                artifact.mediaType(),
                artifact.scanStatus().name(),
                artifact.sensitivity().name(),
                toTimestamp(artifact.createdAt()));
    }

    private void updateArtifact(SandboxArtifact artifact) {
        jdbcTemplate.update(SQL_UPDATE_ARTIFACT,
                artifact.sessionId(),
                artifact.executionId(),
                artifact.objectUri(),
                artifact.mediaType(),
                artifact.scanStatus().name(),
                artifact.sensitivity().name(),
                toTimestamp(artifact.createdAt()),
                artifact.artifactId());
    }

    private SandboxSession mapSession(ResultSet resultSet, int rowNum) throws SQLException {
        return new SandboxSession(
                resultSet.getString("session_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("run_id"),
                SandboxRuntimeType.valueOf(resultSet.getString("runtime_type")),
                SandboxExecutionStatus.valueOf(resultSet.getString("status")),
                SandboxPolicyReasonCode.valueOf(resultSet.getString("reason_code")),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private SandboxExecution mapExecution(ResultSet resultSet, int rowNum) throws SQLException {
        return new SandboxExecution(
                resultSet.getString("execution_id"),
                resultSet.getString("session_id"),
                SandboxRuntimeType.valueOf(resultSet.getString("runtime_type")),
                SandboxExecutionStatus.valueOf(resultSet.getString("status")),
                resultSet.getString("result_summary"),
                SandboxPolicyReasonCode.valueOf(resultSet.getString("reason_code")),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private SandboxArtifact mapArtifact(ResultSet resultSet, int rowNum) throws SQLException {
        return new SandboxArtifact(
                resultSet.getString("artifact_id"),
                resultSet.getString("session_id"),
                resultSet.getString("execution_id"),
                resultSet.getString("object_uri"),
                resultSet.getString("media_type"),
                SandboxArtifactScanStatus.valueOf(resultSet.getString("scan_status")),
                ContextSensitivity.valueOf(resultSet.getString("sensitivity")),
                toInstant(resultSet.getTimestamp("created_at")));
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

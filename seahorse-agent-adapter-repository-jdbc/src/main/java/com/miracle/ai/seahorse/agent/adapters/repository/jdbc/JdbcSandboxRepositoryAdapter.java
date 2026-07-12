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
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxBrowserProfile;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxBrowserProfileStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxEgressPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecution;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxExecutionStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxNetworkPolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxPolicyReasonCode;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicy;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeProfilePolicyStatus;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxRuntimeType;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sandbox.SandboxSession;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxArtifactQueryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxBrowserProfileRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxEgressPolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxExecutionRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxRuntimeProfilePolicyRepositoryPort;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SandboxSessionRepositoryPort;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

public class JdbcSandboxRepositoryAdapter implements SandboxSessionRepositoryPort,
        SandboxExecutionRepositoryPort,
        SandboxArtifactPort,
        SandboxArtifactQueryPort,
        SandboxBrowserProfileRepositoryPort,
        SandboxRuntimeProfilePolicyRepositoryPort,
        SandboxEgressPolicyRepositoryPort {

    private static final String SESSION_COLUMNS = """
            session_id, tenant_id, run_id, runtime_type, status, reason_code, profile_id, expires_at, created_at, updated_at
            """;
    private static final String EXECUTION_COLUMNS = """
            execution_id, session_id, runtime_type, status, result_summary, reason_code, created_at, updated_at
            """;
    private static final String ARTIFACT_COLUMNS = """
            artifact_id, session_id, execution_id, object_uri, media_type, scan_status, sensitivity,
            scan_summary, redaction_summary_json, created_at
            """;
    private static final String RUNTIME_PROFILE_POLICY_COLUMNS = """
            policy_id, tenant_id, runtime_type, profile_id, status, session_ttl_seconds,
            network_allowed, created_at, updated_at
            """;
    private static final String EGRESS_POLICY_COLUMNS = """
            policy_id, tenant_id, network_policy, allowlisted_hosts, browser_private_network_allowed_hosts, created_at, updated_at
            """;
    private static final String BROWSER_PROFILE_COLUMNS = """
            profile_id, tenant_id, name, session_state_artifact_id, status, expires_at, created_at, updated_at
            """;

    private static final String SQL_INSERT_SESSION = """
            INSERT INTO sa_sandbox_session
            (session_id, tenant_id, run_id, runtime_type, status, reason_code, profile_id, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_SESSION = """
            UPDATE sa_sandbox_session
            SET tenant_id = ?,
                run_id = ?,
                runtime_type = ?,
                status = ?,
                reason_code = ?,
                profile_id = ?,
                expires_at = ?,
                created_at = ?,
                updated_at = ?
            WHERE session_id = ?
            """;
    private static final String SQL_FIND_SESSION = """
            SELECT %s
            FROM sa_sandbox_session
            WHERE session_id = ?
            """.formatted(SESSION_COLUMNS);
    private static final String SQL_LIST_SESSIONS_BY_TENANT = """
            SELECT %s
            FROM sa_sandbox_session
            WHERE tenant_id = ?
            ORDER BY updated_at DESC, created_at DESC, session_id DESC
            LIMIT ?
            """.formatted(SESSION_COLUMNS);
    private static final String SQL_LIST_EXPIRED_ACTIVE_SESSIONS = """
            SELECT %s
            FROM sa_sandbox_session
            WHERE tenant_id = ?
              AND expires_at <= ?
              AND status NOT IN (?, ?, ?, ?)
            ORDER BY expires_at ASC, created_at ASC, session_id ASC
            LIMIT ?
            """.formatted(SESSION_COLUMNS);
    private static final String SQL_LIST_ACTIVE_SESSION_IDS = """
            SELECT session_id
            FROM sa_sandbox_session
            WHERE status NOT IN (?, ?, ?, ?)
            """;

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
            (artifact_id, session_id, execution_id, object_uri, media_type, scan_status, sensitivity,
             scan_summary, redaction_summary_json, created_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_ARTIFACT = """
            UPDATE sa_sandbox_artifact
            SET session_id = ?,
                execution_id = ?,
                object_uri = ?,
                media_type = ?,
                scan_status = ?,
                sensitivity = ?,
                scan_summary = ?,
                redaction_summary_json = ?,
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
            ORDER BY created_at ASC, artifact_id ASC
            """.formatted(ARTIFACT_COLUMNS);
    private static final String SQL_LIST_PROMPT_VISIBLE_BY_SESSION = """
            SELECT %s
            FROM sa_sandbox_artifact
            WHERE session_id = ?
              AND scan_status IN (?, ?)
              AND sensitivity <> ?
            ORDER BY created_at ASC, artifact_id ASC
            """.formatted(ARTIFACT_COLUMNS);
    private static final String SQL_INSERT_RUNTIME_PROFILE_POLICY = """
            INSERT INTO sa_sandbox_runtime_profile_policy
            (policy_id, tenant_id, runtime_type, profile_id, status, session_ttl_seconds,
             network_allowed, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_RUNTIME_PROFILE_POLICY = """
            UPDATE sa_sandbox_runtime_profile_policy
            SET tenant_id = ?,
                runtime_type = ?,
                profile_id = ?,
                status = ?,
                session_ttl_seconds = ?,
                network_allowed = ?,
                created_at = ?,
                updated_at = ?
            WHERE policy_id = ?
            """;
    private static final String SQL_FIND_RUNTIME_PROFILE_POLICY_BY_ID = """
            SELECT %s
            FROM sa_sandbox_runtime_profile_policy
            WHERE policy_id = ?
            """.formatted(RUNTIME_PROFILE_POLICY_COLUMNS);
    private static final String SQL_FIND_RUNTIME_PROFILE_POLICY_BY_RUNTIME = """
            SELECT %s
            FROM sa_sandbox_runtime_profile_policy
            WHERE tenant_id = ?
              AND runtime_type = ?
            ORDER BY updated_at DESC, policy_id DESC
            LIMIT 1
            """.formatted(RUNTIME_PROFILE_POLICY_COLUMNS);
    private static final String SQL_LIST_RUNTIME_PROFILE_POLICIES_BY_TENANT = """
            SELECT %s
            FROM sa_sandbox_runtime_profile_policy
            WHERE tenant_id = ?
            ORDER BY runtime_type ASC, updated_at DESC, policy_id ASC
            """.formatted(RUNTIME_PROFILE_POLICY_COLUMNS);
    private static final String SQL_INSERT_EGRESS_POLICY = """
            INSERT INTO sa_sandbox_egress_policy
            (policy_id, tenant_id, network_policy, allowlisted_hosts, browser_private_network_allowed_hosts, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_EGRESS_POLICY = """
            UPDATE sa_sandbox_egress_policy
            SET tenant_id = ?,
                network_policy = ?,
                allowlisted_hosts = ?,
                browser_private_network_allowed_hosts = ?,
                created_at = ?,
                updated_at = ?
            WHERE policy_id = ?
            """;
    private static final String SQL_FIND_EGRESS_POLICY_BY_TENANT = """
            SELECT %s
            FROM sa_sandbox_egress_policy
            WHERE tenant_id = ?
            ORDER BY updated_at DESC, policy_id DESC
            LIMIT 1
            """.formatted(EGRESS_POLICY_COLUMNS);
    private static final String SQL_INSERT_BROWSER_PROFILE = """
            INSERT INTO sa_sandbox_browser_profile
            (profile_id, tenant_id, name, session_state_artifact_id, status, expires_at, created_at, updated_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final String SQL_UPDATE_BROWSER_PROFILE = """
            UPDATE sa_sandbox_browser_profile
            SET name = ?, session_state_artifact_id = ?, status = ?, expires_at = ?, created_at = ?, updated_at = ?
            WHERE tenant_id = ? AND profile_id = ?
            """;
    private static final String SQL_FIND_BROWSER_PROFILE = """
            SELECT %s FROM sa_sandbox_browser_profile WHERE tenant_id = ? AND profile_id = ?
            """.formatted(BROWSER_PROFILE_COLUMNS);
    private static final String SQL_LIST_BROWSER_PROFILES = """
            SELECT %s FROM sa_sandbox_browser_profile WHERE tenant_id = ?
            ORDER BY updated_at DESC, profile_id ASC LIMIT ?
            """.formatted(BROWSER_PROFILE_COLUMNS);

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
    public List<SandboxSession> listSessionsByTenant(String tenantId, int limit) {
        if (!hasText(tenantId) || limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_SESSIONS_BY_TENANT, this::mapSession, tenantId.trim(), limit);
    }

    @Override
    public List<SandboxSession> listExpiredActiveSessions(String tenantId, Instant now, int limit) {
        if (!hasText(tenantId) || now == null || limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_EXPIRED_ACTIVE_SESSIONS,
                this::mapSession,
                tenantId.trim(),
                toTimestamp(now),
                SandboxExecutionStatus.SUCCEEDED.name(),
                SandboxExecutionStatus.FAILED.name(),
                SandboxExecutionStatus.TIMED_OUT.name(),
                SandboxExecutionStatus.CANCELLED.name(),
                limit);
    }

    @Override
    public Set<String> listActiveSessionIds() {
        return jdbcTemplate.queryForList(SQL_LIST_ACTIVE_SESSION_IDS,
                        String.class,
                        SandboxExecutionStatus.SUCCEEDED.name(),
                        SandboxExecutionStatus.FAILED.name(),
                        SandboxExecutionStatus.TIMED_OUT.name(),
                        SandboxExecutionStatus.CANCELLED.name())
                .stream()
                .filter(this::hasText)
                .map(String::trim)
                .collect(Collectors.toUnmodifiableSet());
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
                SandboxArtifactScanStatus.REDACTED.name(),
                ContextSensitivity.SECRET.name());
    }

    @Override
    public SandboxRuntimeProfilePolicy upsert(SandboxRuntimeProfilePolicy policy) {
        SandboxRuntimeProfilePolicy safePolicy = Objects.requireNonNull(policy, "policy must not be null");
        if (findById(safePolicy.policyId()).isPresent()) {
            updateRuntimeProfilePolicy(safePolicy);
            return safePolicy;
        }
        insertRuntimeProfilePolicy(safePolicy);
        return safePolicy;
    }

    @Override
    public Optional<SandboxRuntimeProfilePolicy> findById(String policyId) {
        if (!hasText(policyId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_RUNTIME_PROFILE_POLICY_BY_ID, this::mapRuntimeProfilePolicy,
                policyId.trim()).stream().findFirst();
    }

    @Override
    public Optional<SandboxRuntimeProfilePolicy> findByTenantAndRuntimeType(String tenantId,
                                                                           SandboxRuntimeType runtimeType) {
        if (!hasText(tenantId) || runtimeType == null) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_RUNTIME_PROFILE_POLICY_BY_RUNTIME,
                        this::mapRuntimeProfilePolicy,
                        tenantId.trim(),
                        runtimeType.name())
                .stream()
                .findFirst();
    }

    @Override
    public List<SandboxRuntimeProfilePolicy> listByTenant(String tenantId) {
        if (!hasText(tenantId)) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_RUNTIME_PROFILE_POLICIES_BY_TENANT,
                this::mapRuntimeProfilePolicy,
                tenantId.trim());
    }

    @Override
    public SandboxEgressPolicy upsert(SandboxEgressPolicy policy) {
        SandboxEgressPolicy safePolicy = Objects.requireNonNull(policy, "policy must not be null");
        if (findByTenant(safePolicy.tenantId()).isPresent()) {
            updateEgressPolicy(safePolicy);
            return safePolicy;
        }
        insertEgressPolicy(safePolicy);
        return safePolicy;
    }

    @Override
    public Optional<SandboxEgressPolicy> findByTenant(String tenantId) {
        if (!hasText(tenantId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_EGRESS_POLICY_BY_TENANT, this::mapEgressPolicy,
                tenantId.trim()).stream().findFirst();
    }

    @Override
    public SandboxBrowserProfile save(SandboxBrowserProfile profile) {
        SandboxBrowserProfile safeProfile = Objects.requireNonNull(profile, "profile must not be null");
        if (findByTenantAndProfileId(safeProfile.tenantId(), safeProfile.profileId()).isPresent()) {
            jdbcTemplate.update(SQL_UPDATE_BROWSER_PROFILE,
                    safeProfile.name(), safeProfile.sessionStateArtifactId(), safeProfile.status().name(),
                    toTimestamp(safeProfile.expiresAt()), toTimestamp(safeProfile.createdAt()),
                    toTimestamp(safeProfile.updatedAt()), safeProfile.tenantId(), safeProfile.profileId());
        } else {
            jdbcTemplate.update(SQL_INSERT_BROWSER_PROFILE,
                    safeProfile.profileId(), safeProfile.tenantId(), safeProfile.name(),
                    safeProfile.sessionStateArtifactId(), safeProfile.status().name(), toTimestamp(safeProfile.expiresAt()),
                    toTimestamp(safeProfile.createdAt()), toTimestamp(safeProfile.updatedAt()));
        }
        return safeProfile;
    }

    @Override
    public Optional<SandboxBrowserProfile> findByTenantAndProfileId(String tenantId, String profileId) {
        if (!hasText(tenantId) || !hasText(profileId)) {
            return Optional.empty();
        }
        return jdbcTemplate.query(SQL_FIND_BROWSER_PROFILE, this::mapBrowserProfile, tenantId.trim(), profileId.trim())
                .stream().findFirst();
    }

    @Override
    public List<SandboxBrowserProfile> listByTenant(String tenantId, int limit) {
        if (!hasText(tenantId) || limit <= 0) {
            return List.of();
        }
        return jdbcTemplate.query(SQL_LIST_BROWSER_PROFILES, this::mapBrowserProfile, tenantId.trim(), limit);
    }

    private void insertSession(SandboxSession session) {
        jdbcTemplate.update(SQL_INSERT_SESSION,
                session.sessionId(),
                session.tenantId(),
                session.runId(),
                session.runtimeType().name(),
                session.status().name(),
                session.reasonCode().name(),
                session.profileId(),
                toTimestamp(session.expiresAt()),
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
                session.profileId(),
                toTimestamp(session.expiresAt()),
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

    @Override
    public Optional<SandboxArtifact> findArtifactById(String artifactId) {
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
                artifact.scanSummary(),
                artifact.redactionSummaryJson(),
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
                artifact.scanSummary(),
                artifact.redactionSummaryJson(),
                toTimestamp(artifact.createdAt()),
                artifact.artifactId());
    }

    private void insertRuntimeProfilePolicy(SandboxRuntimeProfilePolicy policy) {
        jdbcTemplate.update(SQL_INSERT_RUNTIME_PROFILE_POLICY,
                policy.policyId(),
                policy.tenantId(),
                policy.runtimeType().name(),
                policy.profileId(),
                policy.status().name(),
                policy.sessionTtlSeconds(),
                policy.networkAllowed(),
                toTimestamp(policy.createdAt()),
                toTimestamp(policy.updatedAt()));
    }

    private void updateRuntimeProfilePolicy(SandboxRuntimeProfilePolicy policy) {
        jdbcTemplate.update(SQL_UPDATE_RUNTIME_PROFILE_POLICY,
                policy.tenantId(),
                policy.runtimeType().name(),
                policy.profileId(),
                policy.status().name(),
                policy.sessionTtlSeconds(),
                policy.networkAllowed(),
                toTimestamp(policy.createdAt()),
                toTimestamp(policy.updatedAt()),
                policy.policyId());
    }

    private void insertEgressPolicy(SandboxEgressPolicy policy) {
        jdbcTemplate.update(SQL_INSERT_EGRESS_POLICY,
                policy.policyId(),
                policy.tenantId(),
                policy.networkPolicy().name(),
                encodeHosts(policy.allowlistedHosts()),
                encodeHosts(policy.browserPrivateNetworkAllowedHosts()),
                toTimestamp(policy.createdAt()),
                toTimestamp(policy.updatedAt()));
    }

    private void updateEgressPolicy(SandboxEgressPolicy policy) {
        jdbcTemplate.update(SQL_UPDATE_EGRESS_POLICY,
                policy.tenantId(),
                policy.networkPolicy().name(),
                encodeHosts(policy.allowlistedHosts()),
                encodeHosts(policy.browserPrivateNetworkAllowedHosts()),
                toTimestamp(policy.createdAt()),
                toTimestamp(policy.updatedAt()),
                policy.policyId());
    }

    private SandboxSession mapSession(ResultSet resultSet, int rowNum) throws SQLException {
        return new SandboxSession(
                resultSet.getString("session_id"),
                resultSet.getString("tenant_id"),
                resultSet.getString("run_id"),
                SandboxRuntimeType.valueOf(resultSet.getString("runtime_type")),
                SandboxExecutionStatus.valueOf(resultSet.getString("status")),
                SandboxPolicyReasonCode.valueOf(resultSet.getString("reason_code")),
                resultSet.getString("profile_id"),
                toInstant(resultSet.getTimestamp("expires_at")),
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
                resultSet.getString("scan_summary"),
                resultSet.getString("redaction_summary_json"),
                toInstant(resultSet.getTimestamp("created_at")));
    }

    private SandboxRuntimeProfilePolicy mapRuntimeProfilePolicy(ResultSet resultSet, int rowNum) throws SQLException {
        return new SandboxRuntimeProfilePolicy(
                resultSet.getString("policy_id"),
                resultSet.getString("tenant_id"),
                SandboxRuntimeType.valueOf(resultSet.getString("runtime_type")),
                resultSet.getString("profile_id"),
                SandboxRuntimeProfilePolicyStatus.valueOf(resultSet.getString("status")),
                resultSet.getLong("session_ttl_seconds"),
                resultSet.getBoolean("network_allowed"),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private SandboxEgressPolicy mapEgressPolicy(ResultSet resultSet, int rowNum) throws SQLException {
        return new SandboxEgressPolicy(
                resultSet.getString("policy_id"),
                resultSet.getString("tenant_id"),
                SandboxNetworkPolicy.valueOf(resultSet.getString("network_policy")),
                decodeHosts(resultSet.getString("allowlisted_hosts")),
                decodeHosts(resultSet.getString("browser_private_network_allowed_hosts")),
                toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private SandboxBrowserProfile mapBrowserProfile(ResultSet resultSet, int rowNum) throws SQLException {
        return new SandboxBrowserProfile(
                resultSet.getString("profile_id"), resultSet.getString("tenant_id"), resultSet.getString("name"),
                resultSet.getString("session_state_artifact_id"),
                SandboxBrowserProfileStatus.valueOf(resultSet.getString("status")),
                toInstant(resultSet.getTimestamp("expires_at")), toInstant(resultSet.getTimestamp("created_at")),
                toInstant(resultSet.getTimestamp("updated_at")));
    }

    private String encodeHosts(List<String> hosts) {
        return String.join(",", SandboxEgressPolicy.normalizeHosts(hosts));
    }

    private List<String> decodeHosts(String value) {
        if (!hasText(value)) {
            return List.of();
        }
        return SandboxEgressPolicy.normalizeHosts(Arrays.asList(value.split(",")));
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

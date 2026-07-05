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

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.sql.Timestamp;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcTenantSchemaUpgradeTests {

    @Test
    void shouldBackfillSandboxSessionRuntimeGovernanceForExistingTable() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:tenant-schema-upgrade-sandbox-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
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
        jdbcTemplate.update("""
                INSERT INTO sa_sandbox_session (
                    session_id, tenant_id, run_id, runtime_type, status, reason_code, created_at, updated_at
                )
                VALUES
                    ('session-python', 'tenant-a', 'run-1', 'CODE_INTERPRETER', 'CREATED', 'VALID_REQUEST',
                     TIMESTAMP '2026-05-26 00:00:00', TIMESTAMP '2026-05-26 00:00:00'),
                    ('session-browser', 'tenant-a', 'run-2', 'BROWSER_AUTOMATION', 'CREATED', 'VALID_REQUEST',
                     TIMESTAMP '2026-05-26 00:01:00', TIMESTAMP '2026-05-26 00:01:00'),
                    ('session-file', 'tenant-a', 'run-3', 'FILE_CONVERSION', 'CREATED', 'VALID_REQUEST',
                     TIMESTAMP '2026-05-26 00:02:00', TIMESTAMP '2026-05-26 00:02:00'),
                    ('session-shell', 'tenant-a', 'run-4', 'SHELL', 'CREATED', 'VALID_REQUEST',
                     TIMESTAMP '2026-05-26 00:03:00', TIMESTAMP '2026-05-26 00:03:00')
                """);

        JdbcTenantSchemaUpgrade upgrade = new JdbcTenantSchemaUpgrade(dataSource);
        upgrade.upgrade();
        upgrade.upgrade();

        assertThat(columnExists(jdbcTemplate, "sa_sandbox_session", "profile_id")).isTrue();
        assertThat(columnExists(jdbcTemplate, "sa_sandbox_session", "expires_at")).isTrue();
        assertThat(isNullable(jdbcTemplate, "sa_sandbox_session", "profile_id")).isEqualTo("NO");
        assertThat(isNullable(jdbcTemplate, "sa_sandbox_session", "expires_at")).isEqualTo("NO");
        assertThat(indexExists(jdbcTemplate, "sa_sandbox_session", "idx_sa_sandbox_session_expires")).isTrue();
        assertThat(profileId(jdbcTemplate, "session-python")).isEqualTo("python-small");
        assertThat(profileId(jdbcTemplate, "session-browser")).isEqualTo("browser-readonly");
        assertThat(profileId(jdbcTemplate, "session-file")).isEqualTo("file-conversion");
        assertThat(profileId(jdbcTemplate, "session-shell")).isEqualTo("shell-restricted");
        assertThat(expiresAt(jdbcTemplate, "session-python"))
                .isEqualTo(LocalDateTime.parse("2026-05-26T01:00:00"));
    }

    @Test
    void shouldAddSandboxArtifactScanSummariesForExistingTable() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:tenant-schema-upgrade-sandbox-artifact-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
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

        JdbcTenantSchemaUpgrade upgrade = new JdbcTenantSchemaUpgrade(dataSource);
        upgrade.upgrade();
        upgrade.upgrade();

        assertThat(columnExists(jdbcTemplate, "sa_sandbox_artifact", "scan_summary")).isTrue();
        assertThat(columnExists(jdbcTemplate, "sa_sandbox_artifact", "redaction_summary_json")).isTrue();
        assertThat(isNullable(jdbcTemplate, "sa_sandbox_artifact", "scan_summary")).isEqualTo("YES");
        assertThat(isNullable(jdbcTemplate, "sa_sandbox_artifact", "redaction_summary_json")).isEqualTo("YES");
    }

    @Test
    void shouldAddToolInvocationRolloutAttributionForExistingTable() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:tenant-schema-upgrade-tool-invocation-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE sa_tool_invocation (
                    invocation_id VARCHAR(64) PRIMARY KEY,
                    run_id VARCHAR(64) NOT NULL,
                    step_id VARCHAR(64) NOT NULL,
                    agent_id VARCHAR(64),
                    version_id VARCHAR(64),
                    tenant_id VARCHAR(64) NOT NULL,
                    user_id VARCHAR(64) NOT NULL,
                    tool_id VARCHAR(128) NOT NULL,
                    idempotency_key VARCHAR(128),
                    status VARCHAR(32) NOT NULL,
                    started_at TIMESTAMP NOT NULL
                )
                """);

        JdbcTenantSchemaUpgrade upgrade = new JdbcTenantSchemaUpgrade(dataSource);
        upgrade.upgrade();
        upgrade.upgrade();

        assertThat(columnExists(jdbcTemplate, "sa_tool_invocation", "rollout_id")).isTrue();
        assertThat(isNullable(jdbcTemplate, "sa_tool_invocation", "rollout_id")).isEqualTo("YES");
        assertThat(indexExists(jdbcTemplate, "sa_tool_invocation", "idx_sa_tool_invocation_rollout")).isTrue();
    }

    @Test
    void shouldAddAgentHandoffContextPackReferenceForExistingTable() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:tenant-schema-upgrade-agent-handoff-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE sa_agent_handoff (
                    handoff_id VARCHAR(64) PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL,
                    parent_run_id VARCHAR(64) NOT NULL,
                    child_run_id VARCHAR(64),
                    source_agent_id VARCHAR(64) NOT NULL,
                    target_agent_id VARCHAR(64) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    failure_code VARCHAR(64),
                    handoff_reason VARCHAR(1000),
                    input_summary_json TEXT NOT NULL,
                    context_summary_json TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    finished_at TIMESTAMP
                )
                """);

        JdbcTenantSchemaUpgrade upgrade = new JdbcTenantSchemaUpgrade(dataSource);
        upgrade.upgrade();
        upgrade.upgrade();

        assertThat(columnExists(jdbcTemplate, "sa_agent_handoff", "context_pack_id")).isTrue();
        assertThat(isNullable(jdbcTemplate, "sa_agent_handoff", "context_pack_id")).isEqualTo("YES");
    }

    @Test
    void shouldCreateSandboxRuntimeProfilePolicyTableWhenMissing() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:tenant-schema-upgrade-sandbox-runtime-profile-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);

        JdbcTenantSchemaUpgrade upgrade = new JdbcTenantSchemaUpgrade(dataSource);
        upgrade.upgrade();
        upgrade.upgrade();

        assertThat(tableExists(jdbcTemplate, "sa_sandbox_runtime_profile_policy")).isTrue();
        assertThat(columnExists(jdbcTemplate, "sa_sandbox_runtime_profile_policy", "runtime_type")).isTrue();
        assertThat(columnExists(jdbcTemplate, "sa_sandbox_runtime_profile_policy", "session_ttl_seconds")).isTrue();
        assertThat(columnExists(jdbcTemplate, "sa_sandbox_runtime_profile_policy", "network_allowed")).isTrue();
        assertThat(indexExists(jdbcTemplate,
                "sa_sandbox_runtime_profile_policy",
                "uk_sa_sandbox_runtime_profile_policy_runtime")).isTrue();
    }

    @Test
    void shouldRelaxSandboxRuntimeProfilePolicyNetworkConstraintForBrowserAutomation() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:tenant-schema-upgrade-sandbox-runtime-profile-network-" + System.nanoTime()
                        + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "sa",
                "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("""
                CREATE TABLE sa_sandbox_runtime_profile_policy (
                  pk_id BIGSERIAL PRIMARY KEY,
                  policy_id VARCHAR(96) NOT NULL UNIQUE,
                  tenant_id VARCHAR(64) NOT NULL,
                  runtime_type VARCHAR(32) NOT NULL,
                  profile_id VARCHAR(64) NOT NULL,
                  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                  session_ttl_seconds BIGINT NOT NULL DEFAULT 3600,
                  network_allowed BOOLEAN NOT NULL DEFAULT FALSE,
                  created_at TIMESTAMP NOT NULL,
                  updated_at TIMESTAMP NOT NULL,
                  CONSTRAINT chk_sa_sandbox_runtime_profile_policy_network
                    CHECK (network_allowed = FALSE)
                )
                """);

        JdbcTenantSchemaUpgrade upgrade = new JdbcTenantSchemaUpgrade(dataSource);
        upgrade.upgrade();
        upgrade.upgrade();

        jdbcTemplate.update("""
                INSERT INTO sa_sandbox_runtime_profile_policy (
                  policy_id, tenant_id, runtime_type, profile_id, status,
                  session_ttl_seconds, network_allowed, created_at, updated_at
                )
                VALUES (
                  'policy-browser', 'default', 'BROWSER_AUTOMATION', 'browser-readonly', 'ACTIVE',
                  3600, TRUE, TIMESTAMP '2026-07-03 00:00:00', TIMESTAMP '2026-07-03 00:00:00'
                )
                """);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT network_allowed FROM sa_sandbox_runtime_profile_policy WHERE policy_id = 'policy-browser'",
                Boolean.class)).isTrue();
        assertThatThrownBy(() -> jdbcTemplate.update("""
                INSERT INTO sa_sandbox_runtime_profile_policy (
                  policy_id, tenant_id, runtime_type, profile_id, status,
                  session_ttl_seconds, network_allowed, created_at, updated_at
                )
                VALUES (
                  'policy-python', 'default', 'CODE_INTERPRETER', 'python-small', 'ACTIVE',
                  3600, TRUE, TIMESTAMP '2026-07-03 00:00:00', TIMESTAMP '2026-07-03 00:00:00'
                )
                """))
                .isInstanceOf(DataAccessException.class);
    }

    private static boolean tableExists(JdbcTemplate jdbcTemplate, String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE lower(table_name) = lower(?)
                """,
                Integer.class,
                tableName);
        return count != null && count > 0;
    }

    private static boolean columnExists(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE lower(table_name) = lower(?) AND lower(column_name) = lower(?)
                """,
                Integer.class,
                tableName,
                columnName);
        return count != null && count > 0;
    }

    private static boolean indexExists(JdbcTemplate jdbcTemplate, String tableName, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                """
                SELECT COUNT(*)
                FROM information_schema.indexes
                WHERE lower(table_name) = lower(?) AND lower(index_name) = lower(?)
                """,
                Integer.class,
                tableName,
                indexName);
        return count != null && count > 0;
    }

    private static String isNullable(JdbcTemplate jdbcTemplate, String tableName, String columnName) {
        return jdbcTemplate.queryForObject(
                """
                SELECT is_nullable
                FROM information_schema.columns
                WHERE lower(table_name) = lower(?) AND lower(column_name) = lower(?)
                """,
                String.class,
                tableName,
                columnName);
    }

    private static String profileId(JdbcTemplate jdbcTemplate, String sessionId) {
        return jdbcTemplate.queryForObject(
                "SELECT profile_id FROM sa_sandbox_session WHERE session_id = ?",
                String.class,
                sessionId);
    }

    private static LocalDateTime expiresAt(JdbcTemplate jdbcTemplate, String sessionId) {
        Timestamp timestamp = jdbcTemplate.queryForObject(
                "SELECT expires_at FROM sa_sandbox_session WHERE session_id = ?",
                Timestamp.class,
                sessionId);
        return timestamp.toLocalDateTime();
    }
}

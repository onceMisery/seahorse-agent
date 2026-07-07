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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/**
 * 幂等执行多租户 schema 升级：为 P0 核心表添加 tenant_id 列并启用 RLS。
 * <p>
 * 该升级类遵循 {@link JdbcChatSchemaUpgrade} 的模式，在应用启动时自动执行。
 * 所有 DDL 操作都是幂等的（IF NOT EXISTS / 先检查再执行），可安全重复运行。
 */
public class JdbcTenantSchemaUpgrade {

    private static final Logger log = LoggerFactory.getLogger(JdbcTenantSchemaUpgrade.class);

    private final JdbcTemplate jdbcTemplate;

    /**
     * P0 阶段需要新增 tenant_id 列的表（当前无此列）。
     */
    private static final List<String> TABLES_NEEDING_TENANT_ID = List.of(
            "t_user",
            "t_conversation",
            "t_conversation_summary",
            "t_message",
            "sa_conversation_attachment",
            "t_message_feedback",
            "t_knowledge_base",
            "t_knowledge_document",
            "t_knowledge_chunk",
            "t_knowledge_document_chunk_log",
            "t_knowledge_vector",
            "t_intent_node",
            "t_query_term_mapping",
            "t_rag_trace_run",
            "t_rag_trace_node",
            "t_sample_question",
            "sa_ai_model_config"
    );

    /**
     * P0 阶段需要启用 RLS 的所有表（包括新增 tenant_id 的和已有 tenant_id 的）。
     */
    private static final List<String> TABLES_NEEDING_RLS = List.of(
            "t_user",
            "t_conversation",
            "t_conversation_summary",
            "t_message",
            "sa_conversation_attachment",
            "t_message_feedback",
            "t_knowledge_base",
            "t_knowledge_document",
            "t_knowledge_chunk",
            "t_knowledge_document_chunk_log",
            "t_knowledge_vector",
            "t_intent_node",
            "t_query_term_mapping",
            "t_rag_trace_run",
            "t_rag_trace_node",
            "t_sample_question",
            "sa_agent_definition",
            "sa_quota_policy",
            "sa_sandbox_runtime_profile_policy"
    );

    public JdbcTenantSchemaUpgrade(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    /**
     * 执行多租户 schema 升级。
     * <p>
     * 分三阶段：1) 加列 → 2) 建索引 → 3) 启用 RLS。
     * 每一步都是幂等的，可安全重复执行。
     */
    public void upgrade() {
        log.info("[TenantSchema] 开始多租户 schema 升级...");
        addTenantIdColumns();
        upgradeAuthRefreshTokenColumns();
        upgradeSandboxSessionRuntimeGovernance();
        upgradeSandboxArtifactScanSummary();
        upgradeSandboxArtifactRedactionSummary();
        upgradeToolInvocationRolloutAttribution();
        repairRemoteA2AToolCatalogGovernance();
        upgradeAgentHandoffContextPackReference();
        upgradeSandboxRuntimeProfilePolicy();
        upgradeAiModelConfigUniqueness();
        enableRowLevelSecurity();
        log.info("[TenantSchema] 多租户 schema 升级完成");
    }

    private void addTenantIdColumns() {
        for (String table : TABLES_NEEDING_TENANT_ID) {
            if (!tableExists(table)) {
                log.debug("[TenantSchema] 表 {} 不存在，跳过", table);
                continue;
            }
            addColumnIfMissing(table, "tenant_id", "VARCHAR(64) NOT NULL DEFAULT 'default'");
        }
    }

    private void enableRowLevelSecurity() {
        for (String table : TABLES_NEEDING_RLS) {
            if (!tableExists(table)) {
                log.debug("[TenantSchema] 表 {} 不存在，跳过 RLS", table);
                continue;
            }
            enableRls(table);
        }
    }

    private void upgradeAiModelConfigUniqueness() {
        if (!tableExists("sa_ai_model_config")) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE sa_ai_model_config DROP CONSTRAINT IF EXISTS sa_ai_model_config_config_key_key");
            jdbcTemplate.execute("DROP INDEX IF EXISTS idx_sa_ai_model_config_key");
            Integer count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_constraint WHERE conname = 'uk_sa_ai_model_config_tenant_key'",
                    Integer.class);
            if (count == null || count == 0) {
                jdbcTemplate.execute(
                        "ALTER TABLE sa_ai_model_config ADD CONSTRAINT uk_sa_ai_model_config_tenant_key UNIQUE (tenant_id, config_key)");
            }
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_sa_ai_model_config_tenant_key ON sa_ai_model_config(tenant_id, config_key, deleted)");
        } catch (Exception e) {
            log.warn("[TenantSchema] 升级 sa_ai_model_config 租户唯一键失败（可能不是 PostgreSQL）: {}", e.getMessage());
        }
    }

    private void upgradeAuthRefreshTokenColumns() {
        if (!tableExists("t_user")) {
            return;
        }
        addColumnIfMissing("t_user", "refresh_token", "VARCHAR(255)");
        addColumnIfMissing("t_user", "refresh_token_expires_at", "TIMESTAMP");
        try {
            jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_user_refresh_token ON t_user(refresh_token)");
        } catch (Exception e) {
            log.warn("[TenantSchema] 创建 t_user refresh token 索引失败（可能不是 PostgreSQL）: {}", e.getMessage());
        }
    }

    private void upgradeSandboxSessionRuntimeGovernance() {
        if (!tableExists("sa_sandbox_session")) {
            return;
        }
        addColumnIfMissing("sa_sandbox_session", "profile_id", "VARCHAR(64)");
        addColumnIfMissing("sa_sandbox_session", "expires_at", "TIMESTAMP");
        jdbcTemplate.update("""
                UPDATE sa_sandbox_session
                SET profile_id = CASE runtime_type
                    WHEN 'CODE_INTERPRETER' THEN 'python-small'
                    WHEN 'BROWSER_AUTOMATION' THEN 'browser-readonly'
                    WHEN 'FILE_CONVERSION' THEN 'file-conversion'
                    WHEN 'SHELL' THEN 'shell-restricted'
                    ELSE 'shell-restricted'
                END
                WHERE profile_id IS NULL OR TRIM(profile_id) = ''
                """);
        backfillSandboxSessionExpiresAt();
        setColumnNotNull("sa_sandbox_session", "profile_id");
        setColumnNotNull("sa_sandbox_session", "expires_at");
        try {
            jdbcTemplate.execute(
                    "CREATE INDEX IF NOT EXISTS idx_sa_sandbox_session_expires ON sa_sandbox_session(tenant_id, expires_at)");
        } catch (Exception e) {
            log.warn("[TenantSchema] 创建 sa_sandbox_session expires 索引失败: {}", e.getMessage());
        }
    }

    private void upgradeSandboxArtifactScanSummary() {
        if (!tableExists("sa_sandbox_artifact")) {
            return;
        }
        addColumnIfMissing("sa_sandbox_artifact", "scan_summary", "VARCHAR(256)");
    }

    private void upgradeSandboxArtifactRedactionSummary() {
        if (!tableExists("sa_sandbox_artifact")) {
            return;
        }
        addColumnIfMissing("sa_sandbox_artifact", "redaction_summary_json", "VARCHAR(2048)");
    }

    private void upgradeToolInvocationRolloutAttribution() {
        if (!tableExists("sa_tool_invocation")) {
            return;
        }
        addColumnIfMissing("sa_tool_invocation", "rollout_id", "VARCHAR(64)");
        try {
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_sa_tool_invocation_rollout
                      ON sa_tool_invocation(tenant_id, agent_id, rollout_id, started_at)
                    """);
        } catch (Exception e) {
            log.warn("[TenantSchema] repair sa_tool_invocation rollout index failed: {}", e.getMessage());
        }
    }

    private void repairRemoteA2AToolCatalogGovernance() {
        if (!tableExists("sa_tool_catalog")) {
            return;
        }
        try {
            jdbcTemplate.update("""
                    UPDATE sa_tool_catalog
                    SET provider = 'BUILTIN',
                        risk_level = 'HIGH',
                        action_type = 'EXECUTE',
                        resource_type = 'REMOTE_AGENT',
                        owner_team = 'kernel-agent',
                        requires_approval = TRUE,
                        updated_at = CURRENT_TIMESTAMP
                    WHERE tool_id = 'invoke_remote_a2a_agent'
                    """);
        } catch (Exception e) {
            log.warn("[TenantSchema] repair invoke_remote_a2a_agent catalog governance failed: {}",
                    e.getMessage());
        }
    }

    private void upgradeAgentHandoffContextPackReference() {
        if (!tableExists("sa_agent_handoff")) {
            return;
        }
        addColumnIfMissing("sa_agent_handoff", "context_pack_id", "VARCHAR(64)");
    }

    private void upgradeSandboxRuntimeProfilePolicy() {
        try {
            jdbcTemplate.execute("""
                    CREATE TABLE IF NOT EXISTS sa_sandbox_runtime_profile_policy (
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
                      CONSTRAINT chk_sa_sandbox_runtime_profile_policy_runtime
                        CHECK (runtime_type IN ('CODE_INTERPRETER', 'FILE_CONVERSION', 'BROWSER_AUTOMATION', 'SHELL')),
                      CONSTRAINT chk_sa_sandbox_runtime_profile_policy_status
                        CHECK (status IN ('ACTIVE', 'DISABLED')),
                      CONSTRAINT chk_sa_sandbox_runtime_profile_policy_ttl
                        CHECK (session_ttl_seconds >= 60 AND session_ttl_seconds <= 7200),
                      CONSTRAINT chk_sa_sandbox_runtime_profile_policy_network
                        CHECK (network_allowed = FALSE OR runtime_type = 'BROWSER_AUTOMATION')
                    )
                    """);
            relaxSandboxRuntimeProfilePolicyNetworkConstraint();
            jdbcTemplate.execute("""
                    CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_sandbox_runtime_profile_policy_runtime
                      ON sa_sandbox_runtime_profile_policy(tenant_id, runtime_type)
                    """);
            jdbcTemplate.execute("""
                    CREATE INDEX IF NOT EXISTS idx_sa_sandbox_runtime_profile_policy_tenant
                      ON sa_sandbox_runtime_profile_policy(tenant_id, updated_at DESC, policy_id DESC)
                    """);
        } catch (Exception e) {
            log.warn("[TenantSchema] 升级 sa_sandbox_runtime_profile_policy 失败: {}", e.getMessage());
        }
    }

    private void relaxSandboxRuntimeProfilePolicyNetworkConstraint() {
        try {
            jdbcTemplate.execute("""
                    ALTER TABLE sa_sandbox_runtime_profile_policy
                      DROP CONSTRAINT IF EXISTS chk_sa_sandbox_runtime_profile_policy_network
                    """);
            jdbcTemplate.execute("""
                    ALTER TABLE sa_sandbox_runtime_profile_policy
                      ADD CONSTRAINT chk_sa_sandbox_runtime_profile_policy_network
                      CHECK (network_allowed = FALSE OR runtime_type = 'BROWSER_AUTOMATION')
                    """);
        } catch (Exception e) {
            log.warn("[TenantSchema] repair sa_sandbox_runtime_profile_policy network constraint failed: {}",
                    e.getMessage());
        }
    }

    private void backfillSandboxSessionExpiresAt() {
        try {
            jdbcTemplate.update("""
                    UPDATE sa_sandbox_session
                    SET expires_at = created_at + INTERVAL '1 hour'
                    WHERE expires_at IS NULL
                    """);
        } catch (Exception ex) {
            jdbcTemplate.update("""
                    UPDATE sa_sandbox_session
                    SET expires_at = DATEADD('HOUR', 1, created_at)
                    WHERE expires_at IS NULL
                    """);
        }
    }

    private void enableRls(String table) {
        try {
            // ENABLE ROW LEVEL SECURITY 是幂等的（PostgreSQL 不会报错）
            jdbcTemplate.execute("ALTER TABLE " + table + " ENABLE ROW LEVEL SECURITY");
            jdbcTemplate.execute("ALTER TABLE " + table + " FORCE ROW LEVEL SECURITY");

            // 创建 RLS 策略（先检查是否已存在同名策略）
            Integer policyCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM pg_policies WHERE tablename = ? AND policyname = 'rls_tenant_isolation'",
                    Integer.class, table);
            if (policyCount == null || policyCount == 0) {
                jdbcTemplate.execute(
                        "CREATE POLICY rls_tenant_isolation ON " + table
                        + " USING (tenant_id = current_setting('app.current_tenant_id', true))");
                log.debug("[TenantSchema] 为表 {} 创建 RLS 策略", table);
            }
        } catch (Exception e) {
            log.warn("[TenantSchema] 为表 {} 启用 RLS 失败（可能不是 PostgreSQL）: {}", table, e.getMessage());
        }
    }

    private boolean tableExists(String tableName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables WHERE lower(table_name) = lower(?)",
                Integer.class, tableName);
        return count != null && count > 0;
    }

    private void addColumnIfMissing(String table, String column, String definition) {
        if (columnExists(table, column)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        log.info("[TenantSchema] 为表 {} 添加列 {}", table, column);
    }

    private boolean columnExists(String table, String column) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.columns WHERE lower(table_name) = lower(?) AND lower(column_name) = lower(?)",
                Integer.class, table, column);
        return count != null && count > 0;
    }

    private void setColumnNotNull(String table, String column) {
        try {
            jdbcTemplate.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " SET NOT NULL");
        } catch (Exception e) {
            log.warn("[TenantSchema] 设置 {}.{} NOT NULL 失败: {}", table, column, e.getMessage());
        }
    }
}

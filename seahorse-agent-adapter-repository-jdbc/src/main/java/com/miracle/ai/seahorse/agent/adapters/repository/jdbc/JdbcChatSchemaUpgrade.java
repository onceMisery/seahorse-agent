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

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.BadSqlGrammarException;

import javax.sql.DataSource;
import java.util.List;
import java.util.Objects;

/**
 * Upgrades legacy chat tables so existing Docker volumes can store modern IDs.
 */
public class JdbcChatSchemaUpgrade {

    private static final int TARGET_LENGTH = 64;
    private static final String SYSTEM_TENANT_ID = "default";
    private static final String SYSTEM_USER_ID = "system";

    private static final List<SystemRoleCardPreset> SYSTEM_ROLE_CARD_PRESETS = List.of(
            new SystemRoleCardPreset(
                    -9001L,
                    "通用助手",
                    "你是 Seahorse 的通用助手。回答要准确、简洁，遇到不确定信息先说明边界，再给出可执行建议。",
                    "role.general-assistant"),
            new SystemRoleCardPreset(
                    -9002L,
                    "需求分析师",
                    "你是需求分析师。先澄清目标、用户、约束和验收标准，再输出结构化方案、风险和下一步。",
                    "role.requirement-analyst"),
            new SystemRoleCardPreset(
                    -9003L,
                    "代码开发助手",
                    "你是代码开发助手。优先阅读现有实现，遵循项目风格，小步修改并说明验证结果。",
                    "role.code-developer"),
            new SystemRoleCardPreset(
                    -9004L,
                    "测试质量审查",
                    "你是测试和质量审查助手。重点发现边界条件、回归风险、缺失测试和可验证的修复建议。",
                    "role.quality-reviewer"),
            new SystemRoleCardPreset(
                    -9005L,
                    "文档知识库助手",
                    "你是文档和知识库助手。优先整理来源、术语、结论和引用关系，输出可维护的文档结构。",
                    "role.knowledge-writer"),
            new SystemRoleCardPreset(
                    -9006L,
                    "数据分析助手",
                    "你是数据分析助手。先明确指标口径和数据来源，再给出分析步骤、异常点和结论置信度。",
                    "role.data-analyst"),
            new SystemRoleCardPreset(
                    -9007L,
                    "AgentScope 调试助手",
                    "你是 AgentScope 调试助手。关注执行引擎、Nacos 配置、trace 链路、工具调用和可复现验证。",
                    "role.agentscope-debugger"));

    private static final List<SystemRunProfilePreset> SYSTEM_RUN_PROFILE_PRESETS = List.of(
            new SystemRunProfilePreset(
                    -9101L,
                    "默认轻量方案",
                    "适合日常问答和低风险任务，使用 kernel 执行引擎。",
                    -9001L,
                    "kernel",
                    null,
                    "{\"temperature\":0.3}",
                    "{\"longTerm\":false}",
                    "{\"highRiskToolApproval\":false}",
                    "plan.default-light"),
            new SystemRunProfilePreset(
                    -9102L,
                    "研发执行方案",
                    "适合代码理解、实现和小步验证，默认绑定代码开发助手。",
                    -9003L,
                    "kernel",
                    null,
                    "{\"temperature\":0.2}",
                    "{\"longTerm\":true}",
                    "{\"highRiskToolApproval\":true}",
                    "plan.code-development"),
            new SystemRunProfilePreset(
                    -9103L,
                    "深度研究方案",
                    "适合需求分析、资料整理和多轮研究，启用长期记忆范围。",
                    -9002L,
                    "kernel",
                    null,
                    "{\"temperature\":0.4}",
                    "{\"longTerm\":true,\"knowledgeBase\":true}",
                    "{\"highRiskToolApproval\":false}",
                    "plan.deep-research"),
            new SystemRunProfilePreset(
                    -9104L,
                    "AgentScope 观测方案",
                    "适合 AgentScope 引擎调试和 trace 验证，默认开启 Studio trace。",
                    -9007L,
                    "agentscope",
                    "{\"studioTraceEnabled\":true}",
                    "{\"temperature\":0.2}",
                    "{\"longTerm\":true}",
                    "{\"highRiskToolApproval\":true}",
                    "plan.agentscope-observe"),
            new SystemRunProfilePreset(
                    -9105L,
                    "安全审批方案",
                    "适合高风险工具或生产前验证，要求高风险工具审批。",
                    -9004L,
                    "kernel",
                    null,
                    "{\"temperature\":0.2}",
                    "{\"longTerm\":false}",
                    "{\"highRiskToolApproval\":true,\"outputReview\":true}",
                    "plan.safety-approval"));

    private final JdbcTemplate jdbcTemplate;

    public JdbcChatSchemaUpgrade(DataSource dataSource) {
        this.jdbcTemplate = new JdbcTemplate(Objects.requireNonNull(dataSource, "dataSource must not be null"));
    }

    public void upgrade() {
        ensureConversationAttachmentTable();
        ensureMemoryProfileTables();
        ensureTaskTable();
        ensureRoleCardGovernanceColumns();
        ensureRunProfileGovernanceColumns();
        ensureRunExperimentTables();
        ensureSystemPresetAssets();
        widenColumns("t_conversation", List.of("id", "conversation_id", "user_id"));
        widenColumns("t_conversation_summary", List.of("id", "conversation_id", "user_id", "last_message_id"));
        widenColumns("t_message", List.of("id", "conversation_id", "user_id"));
        ensureMessageRunLinkage();
        ensureAgentSkillTables();
        widenColumns("t_message_feedback", List.of("id", "message_id", "conversation_id", "user_id"));
        alterColumnToVarcharIfExists("t_rag_trace_run", "conversation_id", TARGET_LENGTH);
        alterColumnToVarcharIfExists("t_rag_trace_run", "task_id", TARGET_LENGTH);
        widenColumns("t_rag_trace_run", List.of("id", "trace_id", "conversation_id", "task_id", "user_id"));
        widenColumns("t_rag_trace_node", List.of("id", "trace_id", "node_id", "parent_node_id", "node_type"));
        widenColumns("t_short_term_memory", List.of("id", "user_id", "conversation_id"));
        ensureLayeredMemoryLifecycleColumns();
    }

    private void ensureTaskTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_task (
                    task_id         VARCHAR(64)  PRIMARY KEY,
                    tenant_id       VARCHAR(64)  NOT NULL DEFAULT 'default',
                    type            VARCHAR(32)  NOT NULL,
                    status          VARCHAR(32)  NOT NULL DEFAULT 'PENDING',
                    user_id         VARCHAR(64)  NOT NULL,
                    conversation_id VARCHAR(64),
                    run_id          VARCHAR(64),
                    agent_id        VARCHAR(64),
                    title           VARCHAR(512),
                    question        TEXT,
                    created_at      TIMESTAMP    NOT NULL DEFAULT NOW(),
                    started_at      TIMESTAMP,
                    finished_at     TIMESTAMP
                )
                """);
        // 兼容旧 volume：表已存在但缺 tenant_id 时补列
        addColumnIfMissing("sa_task", "tenant_id", "VARCHAR(64) NOT NULL DEFAULT 'default'");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sa_task_user ON sa_task(user_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sa_task_status ON sa_task(status)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sa_task_conv ON sa_task(conversation_id)");
        jdbcTemplate.execute("CREATE INDEX IF NOT EXISTS idx_sa_task_tenant ON sa_task(tenant_id)");
    }

    private void ensureRoleCardGovernanceColumns() {
        if (!tableExists("sa_role_card")) {
            return;
        }
        addColumnIfMissing("sa_role_card", "share_scope", "VARCHAR(32) NOT NULL DEFAULT 'PRIVATE'");
        addColumnIfMissing("sa_role_card", "approval_status", "VARCHAR(32) NOT NULL DEFAULT 'PENDING'");
        addColumnIfMissing("sa_role_card", "published", "SMALLINT NOT NULL DEFAULT 0");
        addColumnIfMissing("sa_role_card", "asset_source", "VARCHAR(32) NOT NULL DEFAULT 'USER'");
        addColumnIfMissing("sa_role_card", "preset_key", "VARCHAR(128)");
        addColumnIfMissing("sa_role_card", "preset_version", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing("sa_role_card", "readonly", "SMALLINT NOT NULL DEFAULT 0");
        createBestEffortIndex("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_role_card_system_preset
                ON sa_role_card (tenant_id, preset_key)
                WHERE preset_key IS NOT NULL AND deleted = 0
                """, """
                CREATE INDEX IF NOT EXISTS uk_sa_role_card_system_preset
                ON sa_role_card (tenant_id, preset_key)
                """);
    }

    private void ensureRunProfileGovernanceColumns() {
        if (!tableExists("sa_run_profile")) {
            return;
        }
        addColumnIfMissing("sa_run_profile", "approval_status", "VARCHAR(32) NOT NULL DEFAULT 'DRAFT'");
        addColumnIfMissing("sa_run_profile", "approval_operator", "VARCHAR(64)");
        addColumnIfMissing("sa_run_profile", "approval_comment", "TEXT");
        addColumnIfMissing("sa_run_profile", "approval_time", "TIMESTAMP");
        addColumnIfMissing("sa_run_profile", "asset_source", "VARCHAR(32) NOT NULL DEFAULT 'USER'");
        addColumnIfMissing("sa_run_profile", "preset_key", "VARCHAR(128)");
        addColumnIfMissing("sa_run_profile", "preset_version", "INTEGER NOT NULL DEFAULT 1");
        addColumnIfMissing("sa_run_profile", "readonly", "SMALLINT NOT NULL DEFAULT 0");
        createIndexIfTableExists("sa_run_profile", """
                CREATE INDEX IF NOT EXISTS idx_run_profile_approval
                ON sa_run_profile (tenant_id, approval_status, deleted)
                """);
        createBestEffortIndex("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_run_profile_system_preset
                ON sa_run_profile (tenant_id, preset_key)
                WHERE preset_key IS NOT NULL AND deleted = 0
                """, """
                CREATE INDEX IF NOT EXISTS uk_sa_run_profile_system_preset
                ON sa_run_profile (tenant_id, preset_key)
                """);
    }

    private void ensureRunExperimentTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_run_experiment (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    user_id BIGINT NOT NULL,
                    conversation_id BIGINT NOT NULL,
                    base_leaf_message_id BIGINT,
                    name VARCHAR(128) NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_run_experiment_trial (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    experiment_id BIGINT NOT NULL,
                    run_profile_id BIGINT NOT NULL,
                    run_id VARCHAR(128),
                    output_message_id BIGINT,
                    score_json TEXT,
                    metric_json TEXT,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    error_message TEXT,
                    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        createBestEffortIndex("""
                CREATE INDEX IF NOT EXISTS idx_run_experiment_user_status
                ON sa_run_experiment (tenant_id, user_id, status, update_time DESC)
                WHERE deleted = 0
                """, """
                CREATE INDEX IF NOT EXISTS idx_run_experiment_user_status
                ON sa_run_experiment (tenant_id, user_id, status, update_time DESC)
                """);
        createBestEffortIndex("""
                CREATE INDEX IF NOT EXISTS idx_run_experiment_conversation
                ON sa_run_experiment (tenant_id, conversation_id, base_leaf_message_id)
                WHERE deleted = 0
                """, """
                CREATE INDEX IF NOT EXISTS idx_run_experiment_conversation
                ON sa_run_experiment (tenant_id, conversation_id, base_leaf_message_id)
                """);
        createBestEffortIndex("""
                CREATE INDEX IF NOT EXISTS idx_run_experiment_trial_experiment
                ON sa_run_experiment_trial (tenant_id, experiment_id, id)
                WHERE deleted = 0
                """, """
                CREATE INDEX IF NOT EXISTS idx_run_experiment_trial_experiment
                ON sa_run_experiment_trial (tenant_id, experiment_id, id)
                """);
        createBestEffortIndex("""
                CREATE INDEX IF NOT EXISTS idx_run_experiment_trial_profile
                ON sa_run_experiment_trial (tenant_id, run_profile_id, status)
                WHERE deleted = 0
                """, """
                CREATE INDEX IF NOT EXISTS idx_run_experiment_trial_profile
                ON sa_run_experiment_trial (tenant_id, run_profile_id, status)
                """);
    }

    private void ensureSystemPresetAssets() {
        if (tableExists("sa_role_card")) {
            for (SystemRoleCardPreset preset : SYSTEM_ROLE_CARD_PRESETS) {
                upsertSystemRoleCard(preset);
            }
        }
        if (tableExists("sa_run_profile")) {
            for (SystemRunProfilePreset preset : SYSTEM_RUN_PROFILE_PRESETS) {
                upsertSystemRunProfile(preset);
            }
        }
    }

    private void upsertSystemRoleCard(SystemRoleCardPreset preset) {
        int updated = jdbcTemplate.update("""
                UPDATE sa_role_card
                SET tenant_id = ?,
                    user_id = ?,
                    name = ?,
                    definition = ?,
                    avatar_ref = NULL,
                    higher_perm = 0,
                    enabled = 0,
                    share_scope = 'ORG',
                    approval_status = 'APPROVED',
                    published = 1,
                    asset_source = 'SYSTEM',
                    preset_key = ?,
                    preset_version = 1,
                    readonly = 1,
                    update_time = CURRENT_TIMESTAMP,
                    deleted = 0
                WHERE id = ?
                """,
                SYSTEM_TENANT_ID,
                SYSTEM_USER_ID,
                preset.name(),
                preset.definition(),
                preset.presetKey(),
                preset.id());
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sa_role_card (
                    id, tenant_id, user_id, name, definition, avatar_ref, higher_perm, enabled,
                    share_scope, approval_status, published, asset_source, preset_key, preset_version,
                    readonly, create_time, update_time, deleted
                )
                VALUES (?, ?, ?, ?, ?, NULL, 0, 0, 'ORG', 'APPROVED', 1, 'SYSTEM', ?, 1, 1,
                        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                preset.id(),
                SYSTEM_TENANT_ID,
                SYSTEM_USER_ID,
                preset.name(),
                preset.definition(),
                preset.presetKey());
    }

    private void upsertSystemRunProfile(SystemRunProfilePreset preset) {
        int updated = jdbcTemplate.update("""
                UPDATE sa_run_profile
                SET tenant_id = ?,
                    user_id = ?,
                    name = ?,
                    description = ?,
                    role_card_id = ?,
                    executor_engine = ?,
                    executor_config_json = ?,
                    model_config_json = ?,
                    memory_scope_json = ?,
                    guardrail_config_json = ?,
                    approval_status = 'APPROVED',
                    approval_operator = ?,
                    approval_comment = 'system preset',
                    approval_time = CURRENT_TIMESTAMP,
                    asset_source = 'SYSTEM',
                    preset_key = ?,
                    preset_version = 1,
                    readonly = 1,
                    enabled = 0,
                    update_time = CURRENT_TIMESTAMP,
                    deleted = 0
                WHERE id = ?
                """,
                SYSTEM_TENANT_ID,
                SYSTEM_USER_ID,
                preset.name(),
                preset.description(),
                preset.roleCardId(),
                preset.executorEngine(),
                preset.executorConfigJson(),
                preset.modelConfigJson(),
                preset.memoryScopeJson(),
                preset.guardrailConfigJson(),
                SYSTEM_USER_ID,
                preset.presetKey(),
                preset.id());
        if (updated > 0) {
            return;
        }
        jdbcTemplate.update("""
                INSERT INTO sa_run_profile (
                    id, tenant_id, user_id, name, description, role_card_id, executor_engine,
                    executor_config_json, model_config_json, memory_scope_json, guardrail_config_json,
                    approval_status, approval_operator, approval_comment, approval_time,
                    asset_source, preset_key, preset_version, readonly, enabled,
                    create_time, update_time, deleted
                )
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 'APPROVED', ?, 'system preset',
                        CURRENT_TIMESTAMP, 'SYSTEM', ?, 1, 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
                """,
                preset.id(),
                SYSTEM_TENANT_ID,
                SYSTEM_USER_ID,
                preset.name(),
                preset.description(),
                preset.roleCardId(),
                preset.executorEngine(),
                preset.executorConfigJson(),
                preset.modelConfigJson(),
                preset.memoryScopeJson(),
                preset.guardrailConfigJson(),
                SYSTEM_USER_ID,
                preset.presetKey());
    }

    private void ensureAgentSkillTables() {
        if (tableExists("sa_agent_version")) {
            addColumnIfMissing("sa_agent_version", "skill_set_json", "TEXT NOT NULL DEFAULT '{}'");
        }
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_agent_skill (
                    pk_id BIGSERIAL PRIMARY KEY,
                    skill_name VARCHAR(128) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    category VARCHAR(32) NOT NULL,
                    source VARCHAR(32) NOT NULL,
                    status VARCHAR(32) NOT NULL,
                    enabled SMALLINT NOT NULL DEFAULT 1,
                    latest_revision_id VARCHAR(128),
                    description TEXT NOT NULL,
                    tags_json TEXT NOT NULL DEFAULT '[]',
                    allowed_tools_json TEXT NOT NULL DEFAULT '[]',
                    created_by VARCHAR(64),
                    updated_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    updated_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0,
                    UNIQUE(tenant_id, skill_name)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sa_agent_skill_tenant_status
                ON sa_agent_skill (tenant_id, status, enabled)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_agent_skill_revision (
                    pk_id BIGSERIAL PRIMARY KEY,
                    revision_id VARCHAR(128) NOT NULL UNIQUE,
                    skill_name VARCHAR(128) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    revision_no BIGINT NOT NULL,
                    content_hash VARCHAR(128) NOT NULL,
                    content TEXT NOT NULL,
                    frontmatter_json TEXT NOT NULL,
                    scan_decision VARCHAR(32) NOT NULL,
                    scan_result_json TEXT NOT NULL,
                    created_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0,
                    UNIQUE(tenant_id, skill_name, revision_no)
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sa_agent_skill_revision_skill
                ON sa_agent_skill_revision (tenant_id, skill_name, revision_no)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_agent_skill_binding (
                    pk_id BIGSERIAL PRIMARY KEY,
                    agent_id VARCHAR(64) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    skill_name VARCHAR(128) NOT NULL,
                    revision_id VARCHAR(128) NOT NULL,
                    inject_mode VARCHAR(32) NOT NULL DEFAULT 'METADATA_AND_BODY',
                    created_by VARCHAR(64),
                    created_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sa_agent_skill_binding_agent
                ON sa_agent_skill_binding (tenant_id, agent_id, deleted)
                """);
    }

    private void ensureConversationAttachmentTable() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS sa_conversation_attachment (
                    pk_id BIGSERIAL PRIMARY KEY,
                    attachment_id VARCHAR(64) NOT NULL UNIQUE,
                    conversation_id VARCHAR(64) NOT NULL,
                    message_id VARCHAR(64),
                    user_id VARCHAR(64) NOT NULL,
                    file_name VARCHAR(256) NOT NULL,
                    mime_type VARCHAR(128) NOT NULL,
                    size_bytes BIGINT NOT NULL,
                    storage_ref VARCHAR(1000) NOT NULL,
                    parse_status VARCHAR(32) NOT NULL,
                    resource_ref_json TEXT NOT NULL,
                    created_at TIMESTAMP NOT NULL,
                    deleted SMALLINT NOT NULL DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_sa_conversation_attachment_user
                ON sa_conversation_attachment (conversation_id, user_id, created_at)
                """);
    }

    private void ensureMessageRunLinkage() {
        if (!tableExists("t_message")) {
            return;
        }
        addColumnIfMissing("t_message", "agent_run_id", "VARCHAR(64)");
        createIndexIfTableExists("t_message", """
                CREATE INDEX IF NOT EXISTS idx_message_agent_run
                ON t_message (agent_run_id, user_id, create_time)
                """);
    }

    private void ensureMemoryProfileTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_operation_log (
                    pk_id BIGSERIAL PRIMARY KEY,
                    operation_id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    operation_type VARCHAR(32) NOT NULL,
                    target_kind VARCHAR(32) NOT NULL,
                    target_key VARCHAR(128),
                    request_json JSONB NOT NULL,
                    decision_json JSONB,
                    status VARCHAR(32) NOT NULL,
                    policy_version VARCHAR(64) NOT NULL,
                    error_message TEXT,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_operation_user_time
                ON t_memory_operation_log (user_id, tenant_id, create_time)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_outbox (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    task_type VARCHAR(64) NOT NULL,
                    target_id VARCHAR(64),
                    payload_json JSONB NOT NULL,
                    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    attempt_count INTEGER NOT NULL DEFAULT 0,
                    last_error TEXT,
                    next_retry_time TIMESTAMP,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_outbox_status
                ON t_memory_outbox (status, next_retry_time, create_time)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_review_candidate (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    operation_id VARCHAR(128),
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    conversation_id VARCHAR(64),
                    message_id VARCHAR(64),
                    requested_action VARCHAR(32) NOT NULL,
                    target_layer VARCHAR(32) NOT NULL,
                    target_kind VARCHAR(64),
                    target_key VARCHAR(128),
                    candidate_content TEXT NOT NULL,
                    confidence_level NUMERIC(5, 4) DEFAULT 0,
                    importance_score NUMERIC(5, 4) DEFAULT 0,
                    value_score NUMERIC(5, 4) DEFAULT 0,
                    risk_score NUMERIC(5, 4) DEFAULT 0,
                    reason TEXT,
                    source_message_ids JSONB,
                    candidate_metadata JSONB,
                    review_status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
                    reviewer_id VARCHAR(64),
                    reviewer_comment TEXT,
                    chosen_content TEXT,
                    chosen_metadata JSONB,
                    reviewed_memory_id VARCHAR(128),
                    reviewed_layer VARCHAR(32),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        ensureMemoryReviewCandidateColumns();
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_review_queue
                ON t_memory_review_candidate (tenant_id, user_id, review_status, update_time)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_review_operation
                ON t_memory_review_candidate (operation_id)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_maintenance_run (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    reason VARCHAR(128),
                    status VARCHAR(32) NOT NULL,
                    compaction_requested SMALLINT NOT NULL DEFAULT 0,
                    alias_requested SMALLINT NOT NULL DEFAULT 0,
                    gc_requested SMALLINT NOT NULL DEFAULT 0,
                    compaction_scanned_count INTEGER NOT NULL DEFAULT 0,
                    compaction_group_count INTEGER NOT NULL DEFAULT 0,
                    compaction_fragment_count INTEGER NOT NULL DEFAULT 0,
                    alias_scanned_count INTEGER NOT NULL DEFAULT 0,
                    alias_normalized_count INTEGER NOT NULL DEFAULT 0,
                    alias_dictionary_match_count INTEGER NOT NULL DEFAULT 0,
                    alias_skipped_count INTEGER NOT NULL DEFAULT 0,
                    gc_scanned_count INTEGER NOT NULL DEFAULT 0,
                    gc_enqueued_count INTEGER NOT NULL DEFAULT 0,
                    gc_marked_count INTEGER NOT NULL DEFAULT 0,
                    gc_dry_run SMALLINT NOT NULL DEFAULT 0,
                    skipped_tasks TEXT,
                    errors TEXT,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_maintenance_run_status_time
                ON t_memory_maintenance_run (status, update_time)
                """);
        addColumnIfMissing("t_memory_maintenance_run", "compaction_scanned_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "compaction_group_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "compaction_fragment_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "alias_scanned_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "alias_normalized_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "alias_dictionary_match_count", "INTEGER NOT NULL DEFAULT 0");
        addColumnIfMissing("t_memory_maintenance_run", "alias_skipped_count", "INTEGER NOT NULL DEFAULT 0");
        ensureMemoryEntityAliasTables();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_review_feedback_sample (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    candidate_id VARCHAR(128) NOT NULL,
                    operation_id VARCHAR(128),
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    requested_action VARCHAR(32) NOT NULL,
                    review_status VARCHAR(32) NOT NULL,
                    reviewer_id VARCHAR(64),
                    reviewer_comment TEXT,
                    target_layer VARCHAR(32),
                    target_kind VARCHAR(64),
                    target_key VARCHAR(128),
                    rejected_content TEXT,
                    chosen_content TEXT,
                    rejected_metadata JSONB,
                    chosen_metadata JSONB,
                    source_message_ids JSONB,
                    reviewed_memory_id VARCHAR(128),
                    reviewed_layer VARCHAR(32),
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_review_feedback_candidate
                ON t_memory_review_feedback_sample (candidate_id, create_time)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_trace_event (
                    id BIGINT PRIMARY KEY,
                    trace_id VARCHAR(128) NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    user_id BIGINT,
                    conversation_id BIGINT,
                    session_id VARCHAR(128),
                    component VARCHAR(64) NOT NULL,
                    event_type VARCHAR(64) NOT NULL,
                    status VARCHAR(32),
                    subject_id VARCHAR(128),
                    subject_type VARCHAR(64),
                    details_json JSONB,
                    occurred_at TIMESTAMP NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_trace_recent
                ON t_memory_trace_event (occurred_at, create_time)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_trace_filters
                ON t_memory_trace_event (tenant_id, user_id, component, status, occurred_at)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_aggregation_buffer (
                    id BIGINT PRIMARY KEY,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    user_id BIGINT NOT NULL,
                    conversation_id BIGINT NOT NULL,
                    session_id VARCHAR(128) NOT NULL,
                    turn_count INTEGER NOT NULL DEFAULT 0,
                    total_tokens INTEGER NOT NULL DEFAULT 0,
                    turns_json JSONB NOT NULL,
                    version BIGINT NOT NULL DEFAULT 1,
                    first_activity_at TIMESTAMP NOT NULL,
                    last_activity_at TIMESTAMP NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_aggregation_session
                ON t_memory_aggregation_buffer (tenant_id, user_id, session_id)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_aggregation_scan
                ON t_memory_aggregation_buffer (last_activity_at, update_time)
                """);
        ensureMemoryAggregationBufferColumns();
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_user_profile_fact (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    slot_key VARCHAR(128) NOT NULL,
                    value_text TEXT NOT NULL,
                    value_json JSONB,
                    confidence_level NUMERIC(4, 3) DEFAULT 0,
                    source_type VARCHAR(64),
                    source_ids JSONB,
                    generation_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    version BIGINT NOT NULL DEFAULT 1,
                    valid_from TIMESTAMP,
                    valid_until TIMESTAMP,
                    last_referenced_at TIMESTAMP,
                    access_count INTEGER NOT NULL DEFAULT 0,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        ensureProfileFactColumns();
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_user_profile_active
                ON t_user_profile_fact (user_id, tenant_id, status, slot_key)
                """);
        executePostgresPartialIndexOrPlainIndex("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_user_profile_active_slot
                ON t_user_profile_fact (user_id, tenant_id, slot_key)
                WHERE status = 'ACTIVE' AND deleted = 0
                """, """
                CREATE INDEX IF NOT EXISTS uk_user_profile_active_slot
                ON t_user_profile_fact (user_id, tenant_id, slot_key)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_correction_ledger (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    correction_type VARCHAR(32) NOT NULL,
                    target_kind VARCHAR(32) NOT NULL,
                    target_key VARCHAR(128) NOT NULL,
                    incorrect_value TEXT,
                    correct_value TEXT,
                    rule_text TEXT NOT NULL,
                    priority VARCHAR(32) NOT NULL DEFAULT 'HARD_RULE',
                    source_message_ids JSONB,
                    effective_generation_id VARCHAR(64),
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_correction_active
                ON t_memory_correction_ledger (user_id, tenant_id, status, target_kind, target_key)
                """);
        executePostgresPartialIndexOrPlainIndex("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_correction_active_target
                ON t_memory_correction_ledger (user_id, tenant_id, target_kind, target_key)
                WHERE status = 'ACTIVE' AND deleted = 0
                """, """
                CREATE INDEX IF NOT EXISTS uk_memory_correction_active_target
                ON t_memory_correction_ledger (user_id, tenant_id, target_kind, target_key)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_conflict_log (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    memory_id_1 VARCHAR(64) NOT NULL,
                    memory_id_2 VARCHAR(64) NOT NULL,
                    conflict_type VARCHAR(32) NOT NULL,
                    severity VARCHAR(16) NOT NULL,
                    resolution_status VARCHAR(16) NOT NULL,
                    resolution_action VARCHAR(32),
                    resolved_by VARCHAR(32),
                    resolved_at TIMESTAMP,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_conflict_user_status
                ON t_memory_conflict_log (user_id, resolution_status, create_time DESC)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_quality_snapshot (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    snapshot_json JSONB NOT NULL,
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_quality_snapshot_user_time
                ON t_memory_quality_snapshot (user_id, create_time DESC)
                """);
        ensureMemoryBusinessIdColumnTypes();
    }

    private void ensureMemoryEntityAliasTables() {
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_entity_alias (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    alias_text VARCHAR(256) NOT NULL,
                    normalized_alias VARCHAR(256) NOT NULL,
                    canonical_entity_id VARCHAR(128) NOT NULL,
                    canonical_name VARCHAR(256) NOT NULL,
                    entity_type VARCHAR(64) NOT NULL DEFAULT 'ENTITY',
                    confidence_level NUMERIC(4, 3) DEFAULT 0,
                    source_type VARCHAR(64),
                    source_memory_ids JSONB,
                    metadata_json JSONB,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_alias_lookup
                ON t_memory_entity_alias (user_id, tenant_id, normalized_alias, status)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_entity_relation (
                    pk_id BIGSERIAL PRIMARY KEY,
                    id VARCHAR(128) NOT NULL UNIQUE,
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    memory_id VARCHAR(64) NOT NULL,
                    layer_name VARCHAR(32),
                    memory_type VARCHAR(64),
                    content TEXT,
                    source_entity_id VARCHAR(128) NOT NULL,
                    target_entity_id VARCHAR(128) NOT NULL,
                    relation_type VARCHAR(64) NOT NULL DEFAULT 'MENTIONS',
                    weight NUMERIC(6, 4) DEFAULT 1,
                    confidence_level NUMERIC(4, 3) DEFAULT 1,
                    metadata_json JSONB,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_relation_source
                ON t_memory_entity_relation (user_id, tenant_id, source_entity_id, status)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_relation_target
                ON t_memory_entity_relation (user_id, tenant_id, target_entity_id, status)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_relation_memory
                ON t_memory_entity_relation (user_id, tenant_id, memory_id, status)
                """);
        jdbcTemplate.execute("""
                CREATE TABLE IF NOT EXISTS t_memory_keyword_index (
                    id BIGINT PRIMARY KEY,
                    user_id BIGINT NOT NULL,
                    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
                    memory_id VARCHAR(64) NOT NULL,
                    layer_name VARCHAR(32),
                    memory_type VARCHAR(64),
                    content TEXT,
                    metadata_json JSONB,
                    source_update_time TIMESTAMP,
                    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
                    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                    deleted SMALLINT DEFAULT 0
                )
                """);
        jdbcTemplate.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS uk_memory_keyword_memory
                ON t_memory_keyword_index (user_id, tenant_id, memory_id)
                """);
        jdbcTemplate.execute("""
                CREATE INDEX IF NOT EXISTS idx_memory_keyword_lookup
                ON t_memory_keyword_index (user_id, tenant_id, status, update_time)
                """);
    }

    private void ensureProfileFactColumns() {
        if (!tableExists("t_user_profile_fact")) {
            return;
        }
        addColumnIfMissing("t_user_profile_fact", "version", "BIGINT NOT NULL DEFAULT 1");
        addColumnIfMissing("t_user_profile_fact", "last_referenced_at", "TIMESTAMP");
        addColumnIfMissing("t_user_profile_fact", "access_count", "INTEGER NOT NULL DEFAULT 0");
    }

    private void ensureMemoryReviewCandidateColumns() {
        if (!tableExists("t_memory_review_candidate")) {
            return;
        }
        addColumnIfMissing("t_memory_review_candidate", "chosen_content", "TEXT");
        addColumnIfMissing("t_memory_review_candidate", "chosen_metadata", "JSONB");
    }

    private void ensureMemoryAggregationBufferColumns() {
        if (!tableExists("t_memory_aggregation_buffer")) {
            return;
        }
        addColumnIfMissing("t_memory_aggregation_buffer", "version", "BIGINT NOT NULL DEFAULT 1");
    }

    private void ensureMemoryBusinessIdColumnTypes() {
        alterColumnToVarcharIfExists("t_memory_operation_log", "operation_id", 128);
        alterColumnToVarcharIfExists("t_memory_outbox", "id", 128);
        alterColumnToVarcharIfExists("t_memory_outbox", "target_id", 64);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "id", 128);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "operation_id", 128);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "conversation_id", 64);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "message_id", 64);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "reviewer_id", 64);
        alterColumnToVarcharIfExists("t_memory_review_candidate", "reviewed_memory_id", 128);
        alterColumnToVarcharIfExists("t_memory_maintenance_run", "id", 128);
        alterColumnToVarcharIfExists("t_memory_review_feedback_sample", "id", 128);
        alterColumnToVarcharIfExists("t_memory_review_feedback_sample", "candidate_id", 128);
        alterColumnToVarcharIfExists("t_memory_review_feedback_sample", "operation_id", 128);
        alterColumnToVarcharIfExists("t_memory_review_feedback_sample", "reviewer_id", 64);
        alterColumnToVarcharIfExists("t_memory_review_feedback_sample", "reviewed_memory_id", 128);
        alterColumnToVarcharIfExists("t_memory_trace_event", "trace_id", 128);
        alterColumnToVarcharIfExists("t_memory_trace_event", "subject_id", 128);
        alterColumnToVarcharIfExists("t_user_profile_fact", "generation_id", 64);
        alterColumnToVarcharIfExists("t_memory_correction_ledger", "effective_generation_id", 64);
        alterColumnToVarcharIfExists("t_memory_entity_alias", "id", 128);
        alterColumnToVarcharIfExists("t_memory_entity_alias", "canonical_entity_id", 128);
        alterColumnToVarcharIfExists("t_memory_entity_relation", "id", 128);
        alterColumnToVarcharIfExists("t_memory_entity_relation", "memory_id", 64);
        alterColumnToVarcharIfExists("t_memory_entity_relation", "source_entity_id", 128);
        alterColumnToVarcharIfExists("t_memory_entity_relation", "target_entity_id", 128);
        alterColumnToVarcharIfExists("t_memory_keyword_index", "memory_id", 64);
        alterColumnToVarcharIfExists("t_memory_conflict_log", "id", 128);
        alterColumnToVarcharIfExists("t_memory_conflict_log", "memory_id_1", 64);
        alterColumnToVarcharIfExists("t_memory_conflict_log", "memory_id_2", 64);
        alterColumnToVarcharIfExists("t_memory_quality_snapshot", "id", 128);
    }

    private void executePostgresPartialIndexOrPlainIndex(String postgresSql, String fallbackSql) {
        try {
            jdbcTemplate.execute(postgresSql);
        } catch (BadSqlGrammarException ex) {
            jdbcTemplate.execute(fallbackSql);
        }
    }

    private void createBestEffortIndex(String postgresSql, String fallbackSql) {
        try {
            executePostgresPartialIndexOrPlainIndex(postgresSql, fallbackSql);
        } catch (Exception ignored) {
            // Existing volumes may contain duplicate legacy data; seeding still repairs the visible presets.
        }
    }

    private record SystemRoleCardPreset(long id, String name, String definition, String presetKey) {
    }

    private record SystemRunProfilePreset(
            long id,
            String name,
            String description,
            long roleCardId,
            String executorEngine,
            String executorConfigJson,
            String modelConfigJson,
            String memoryScopeJson,
            String guardrailConfigJson,
            String presetKey) {
    }

    private void ensureLayeredMemoryLifecycleColumns() {
        ensureLifecycleColumns("t_short_term_memory");
        ensureLifecycleColumns("t_long_term_memory");
        ensureLifecycleColumns("t_semantic_memory");
        ensureVectorLifecycleColumns();
        createLifecycleIndexes();
    }

    private void ensureLifecycleColumns(String tableName) {
        if (!tableExists(tableName)) {
            return;
        }
        addColumnIfMissing(tableName, "tenant_id", "VARCHAR(64) DEFAULT 'default'");
        addColumnIfMissing(tableName, "status", "VARCHAR(32) DEFAULT 'ACTIVE'");
        addColumnIfMissing(tableName, "generation_id", "VARCHAR(64)");
        addColumnIfMissing(tableName, "valid_from", "TIMESTAMP");
        addColumnIfMissing(tableName, "valid_until", "TIMESTAMP");
        addColumnIfMissing(tableName, "last_referenced_at", "TIMESTAMP");
        addColumnIfMissing(tableName, "schema_version", "VARCHAR(32)");
        addColumnIfMissing(tableName, "policy_version", "VARCHAR(64)");
        addColumnIfMissing(tableName, "sensitivity_level", "VARCHAR(32)");
        addColumnIfMissing(tableName, "obsolete_reason", "TEXT");
        addColumnIfMissing(tableName, "derived_indexes_deleted_at", "TIMESTAMP");
    }

    private void ensureVectorLifecycleColumns() {
        if (!tableExists("t_long_term_memory_vector")) {
            return;
        }
        addColumnIfMissing("t_long_term_memory_vector", "tenant_id", "VARCHAR(64) DEFAULT 'default'");
        addColumnIfMissing("t_long_term_memory_vector", "generation_id", "VARCHAR(64)");
        addColumnIfMissing("t_long_term_memory_vector", "status", "VARCHAR(32) DEFAULT 'ACTIVE'");
        addColumnIfMissing("t_long_term_memory_vector", "last_referenced_at", "TIMESTAMP");
        addColumnIfMissing("t_long_term_memory_vector", "access_count", "INTEGER NOT NULL DEFAULT 0");
    }

    private void createLifecycleIndexes() {
        createIndexIfTableExists("t_short_term_memory", """
                CREATE INDEX IF NOT EXISTS idx_stm_lifecycle_user_status
                ON t_short_term_memory (user_id, tenant_id, status, update_time)
                """);
        createIndexIfTableExists("t_long_term_memory", """
                CREATE INDEX IF NOT EXISTS idx_ltm_lifecycle_user_status
                ON t_long_term_memory (user_id, tenant_id, status, update_time)
                """);
        createIndexIfTableExists("t_semantic_memory", """
                CREATE INDEX IF NOT EXISTS idx_sem_lifecycle_user_status
                ON t_semantic_memory (user_id, tenant_id, status, update_time)
                """);
        createIndexIfTableExists("t_short_term_memory", """
                CREATE INDEX IF NOT EXISTS idx_stm_gc_derived_indexes
                ON t_short_term_memory (status, derived_indexes_deleted_at, update_time)
                """);
        createIndexIfTableExists("t_long_term_memory", """
                CREATE INDEX IF NOT EXISTS idx_ltm_gc_derived_indexes
                ON t_long_term_memory (status, derived_indexes_deleted_at, update_time)
                """);
        createIndexIfTableExists("t_semantic_memory", """
                CREATE INDEX IF NOT EXISTS idx_sem_gc_derived_indexes
                ON t_semantic_memory (status, derived_indexes_deleted_at, update_time)
                """);
        createIndexIfTableExists("t_long_term_memory_vector", """
                CREATE INDEX IF NOT EXISTS idx_ltm_vector_lifecycle
                ON t_long_term_memory_vector (user_id, tenant_id, status, update_time)
                """);
    }

    private void createIndexIfTableExists(String tableName, String sql) {
        if (tableExists(tableName)) {
            jdbcTemplate.execute(sql);
        }
    }

    private void addColumnIfMissing(String tableName, String columnName, String definition) {
        if (columnExists(tableName, columnName)) {
            return;
        }
        jdbcTemplate.execute("ALTER TABLE " + tableName + " ADD COLUMN " + columnName + " " + definition);
    }

    private void alterColumnToVarcharIfExists(String tableName, String columnName, int length) {
        if (!columnExists(tableName, columnName)) {
            return;
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName
                    + " TYPE VARCHAR(" + length + ") USING " + columnName + "::VARCHAR");
            return;
        } catch (Exception ignored) {
            // Some embedded databases do not support PostgreSQL USING syntax; try their simpler type syntax.
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName
                    + " TYPE VARCHAR(" + length + ")");
            return;
        } catch (Exception ignored) {
            // H2 supports SET DATA TYPE instead of PostgreSQL TYPE syntax.
        }
        try {
            jdbcTemplate.execute("ALTER TABLE " + tableName + " ALTER COLUMN " + columnName
                    + " SET DATA TYPE VARCHAR(" + length + ")");
        } catch (Exception ignored) {
            // Leave init SQL as the source of truth when the database cannot alter this column online.
        }
    }

    private boolean tableExists(String tableName) {
        return !jdbcTemplate.query(
                        """
                        SELECT table_name
                        FROM information_schema.tables
                        WHERE lower(table_name) = lower(?)
                        """,
                        (rs, rowNum) -> rs.getString(1),
                        tableName)
                .isEmpty();
    }

    private boolean columnExists(String tableName, String columnName) {
        return !jdbcTemplate.query(
                        """
                        SELECT column_name
                        FROM information_schema.columns
                        WHERE lower(table_name) = lower(?) AND lower(column_name) = lower(?)
                        """,
                        (rs, rowNum) -> rs.getString(1),
                        tableName,
                        columnName)
                .isEmpty();
    }

    private void widenColumns(String tableName, List<String> columns) {
        for (String column : columns) {
            try {
                List<Integer> results = jdbcTemplate.query(
                        """
                        SELECT character_maximum_length
                        FROM information_schema.columns
                        WHERE table_name = ? AND column_name = ?
                        """,
                        (rs, rowNum) -> rs.getObject(1, Integer.class),
                        tableName,
                        column);
                
                if (results.isEmpty()) {
                    continue;
                }
                
                Integer currentLength = results.get(0);
                if (currentLength == null || currentLength >= TARGET_LENGTH) {
                    continue;
                }
                jdbcTemplate.execute(
                        "ALTER TABLE " + tableName + " ALTER COLUMN " + column + " TYPE VARCHAR(" + TARGET_LENGTH + ")");
            } catch (Exception e) {
                // Skip if column is not VARCHAR type (e.g., BIGINT) or any other error
                continue;
            }
        }
    }
}

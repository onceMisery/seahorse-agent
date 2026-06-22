-- ============================================================================
-- V44: system preset asset metadata
-- ============================================================================

ALTER TABLE sa_role_card ADD COLUMN IF NOT EXISTS asset_source VARCHAR(32) NOT NULL DEFAULT 'USER';
ALTER TABLE sa_role_card ADD COLUMN IF NOT EXISTS preset_key VARCHAR(128);
ALTER TABLE sa_role_card ADD COLUMN IF NOT EXISTS preset_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE sa_role_card ADD COLUMN IF NOT EXISTS readonly SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN sa_role_card.asset_source IS '资产来源，USER 表示用户自建，SYSTEM 表示系统内置预设';
COMMENT ON COLUMN sa_role_card.preset_key IS '系统预设稳定标识，用于内置角色卡幂等初始化和版本升级';
COMMENT ON COLUMN sa_role_card.preset_version IS '系统预设版本号，用于判断内置角色卡是否需要升级';
COMMENT ON COLUMN sa_role_card.readonly IS '是否只读，1 表示系统预设不可直接编辑或删除';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_role_card_system_preset
    ON sa_role_card (tenant_id, preset_key)
    WHERE preset_key IS NOT NULL AND deleted = 0;

ALTER TABLE sa_run_profile ADD COLUMN IF NOT EXISTS asset_source VARCHAR(32) NOT NULL DEFAULT 'USER';
ALTER TABLE sa_run_profile ADD COLUMN IF NOT EXISTS preset_key VARCHAR(128);
ALTER TABLE sa_run_profile ADD COLUMN IF NOT EXISTS preset_version INTEGER NOT NULL DEFAULT 1;
ALTER TABLE sa_run_profile ADD COLUMN IF NOT EXISTS readonly SMALLINT NOT NULL DEFAULT 0;

COMMENT ON COLUMN sa_run_profile.asset_source IS '资产来源，USER 表示用户自建，SYSTEM 表示系统内置预设';
COMMENT ON COLUMN sa_run_profile.preset_key IS '系统预设稳定标识，用于内置运行方案幂等初始化和版本升级';
COMMENT ON COLUMN sa_run_profile.preset_version IS '系统预设版本号，用于判断内置运行方案是否需要升级';
COMMENT ON COLUMN sa_run_profile.readonly IS '是否只读，1 表示系统预设不可直接编辑或删除';

CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_run_profile_system_preset
    ON sa_run_profile (tenant_id, preset_key)
    WHERE preset_key IS NOT NULL AND deleted = 0;

INSERT INTO sa_role_card (
    id, tenant_id, user_id, name, definition, avatar_ref, higher_perm, enabled,
    share_scope, approval_status, published, asset_source, preset_key, preset_version,
    readonly, create_time, update_time, deleted
) VALUES
    (-9001, 'default', 'system', '通用助手',
     '你是 Seahorse 的通用助手。回答要准确、简洁，遇到不确定信息先说明边界，再给出可执行建议。',
     NULL, 0, 0, 'ORG', 'APPROVED', 1, 'SYSTEM', 'role.general-assistant', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (-9002, 'default', 'system', '需求分析师',
     '你是需求分析师。先澄清目标、用户、约束和验收标准，再输出结构化方案、风险和下一步。',
     NULL, 0, 0, 'ORG', 'APPROVED', 1, 'SYSTEM', 'role.requirement-analyst', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (-9003, 'default', 'system', '代码开发助手',
     '你是代码开发助手。优先阅读现有实现，遵循项目风格，小步修改并说明验证结果。',
     NULL, 0, 0, 'ORG', 'APPROVED', 1, 'SYSTEM', 'role.code-developer', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (-9004, 'default', 'system', '测试质量审查',
     '你是测试和质量审查助手。重点发现边界条件、回归风险、缺失测试和可验证的修复建议。',
     NULL, 0, 0, 'ORG', 'APPROVED', 1, 'SYSTEM', 'role.quality-reviewer', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (-9005, 'default', 'system', '文档知识库助手',
     '你是文档和知识库助手。优先整理来源、术语、结论和引用关系，输出可维护的文档结构。',
     NULL, 0, 0, 'ORG', 'APPROVED', 1, 'SYSTEM', 'role.knowledge-writer', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (-9006, 'default', 'system', '数据分析助手',
     '你是数据分析助手。先明确指标口径和数据来源，再给出分析步骤、异常点和结论置信度。',
     NULL, 0, 0, 'ORG', 'APPROVED', 1, 'SYSTEM', 'role.data-analyst', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (-9007, 'default', 'system', 'AgentScope 调试助手',
     '你是 AgentScope 调试助手。关注执行引擎、Nacos 配置、trace 链路、工具调用和可复现验证。',
     NULL, 0, 0, 'ORG', 'APPROVED', 1, 'SYSTEM', 'role.agentscope-debugger', 1, 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (tenant_id, preset_key) WHERE preset_key IS NOT NULL AND deleted = 0
DO UPDATE SET
    name = EXCLUDED.name,
    definition = EXCLUDED.definition,
    share_scope = EXCLUDED.share_scope,
    approval_status = EXCLUDED.approval_status,
    published = EXCLUDED.published,
    asset_source = EXCLUDED.asset_source,
    preset_version = EXCLUDED.preset_version,
    readonly = EXCLUDED.readonly,
    update_time = CURRENT_TIMESTAMP;

INSERT INTO sa_run_profile (
    id, tenant_id, user_id, name, description, role_card_id, executor_engine,
    executor_config_json, model_config_json, memory_scope_json, guardrail_config_json,
    approval_status, approval_operator, approval_comment, approval_time,
    asset_source, preset_key, preset_version, readonly, enabled,
    create_time, update_time, deleted
) VALUES
    (-9101, 'default', 'system', '默认轻量方案', '适合日常问答和低风险任务，使用 kernel 执行引擎。',
     -9001, 'kernel', NULL, '{"temperature":0.3}', '{"longTerm":false}', '{"highRiskToolApproval":false}',
     'APPROVED', 'system', 'system preset', CURRENT_TIMESTAMP, 'SYSTEM', 'plan.default-light', 1, 1, 0,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (-9102, 'default', 'system', '研发执行方案', '适合代码理解、实现和小步验证，默认绑定代码开发助手。',
     -9003, 'kernel', NULL, '{"temperature":0.2}', '{"longTerm":true}', '{"highRiskToolApproval":true}',
     'APPROVED', 'system', 'system preset', CURRENT_TIMESTAMP, 'SYSTEM', 'plan.code-development', 1, 1, 0,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (-9103, 'default', 'system', '深度研究方案', '适合需求分析、资料整理和多轮研究，启用长期记忆范围。',
     -9002, 'kernel', NULL, '{"temperature":0.4}', '{"longTerm":true,"knowledgeBase":true}', '{"highRiskToolApproval":false}',
     'APPROVED', 'system', 'system preset', CURRENT_TIMESTAMP, 'SYSTEM', 'plan.deep-research', 1, 1, 0,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (-9104, 'default', 'system', 'AgentScope 观测方案', '适合 AgentScope 引擎调试和 trace 验证，默认开启 Studio trace。',
     -9007, 'agentscope', '{"studioTraceEnabled":true}', '{"temperature":0.2}', '{"longTerm":true}', '{"highRiskToolApproval":true}',
     'APPROVED', 'system', 'system preset', CURRENT_TIMESTAMP, 'SYSTEM', 'plan.agentscope-observe', 1, 1, 0,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0),
    (-9105, 'default', 'system', '安全审批方案', '适合高风险工具或生产前验证，要求高风险工具审批。',
     -9004, 'kernel', NULL, '{"temperature":0.2}', '{"longTerm":false}', '{"highRiskToolApproval":true,"outputReview":true}',
     'APPROVED', 'system', 'system preset', CURRENT_TIMESTAMP, 'SYSTEM', 'plan.safety-approval', 1, 1, 0,
     CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 0)
ON CONFLICT (tenant_id, preset_key) WHERE preset_key IS NOT NULL AND deleted = 0
DO UPDATE SET
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    role_card_id = EXCLUDED.role_card_id,
    executor_engine = EXCLUDED.executor_engine,
    executor_config_json = EXCLUDED.executor_config_json,
    model_config_json = EXCLUDED.model_config_json,
    memory_scope_json = EXCLUDED.memory_scope_json,
    guardrail_config_json = EXCLUDED.guardrail_config_json,
    approval_status = EXCLUDED.approval_status,
    approval_operator = EXCLUDED.approval_operator,
    approval_comment = EXCLUDED.approval_comment,
    approval_time = EXCLUDED.approval_time,
    asset_source = EXCLUDED.asset_source,
    preset_version = EXCLUDED.preset_version,
    readonly = EXCLUDED.readonly,
    update_time = CURRENT_TIMESTAMP;

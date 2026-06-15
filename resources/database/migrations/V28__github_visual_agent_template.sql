-- Make the built-in GitHub visual project introduction agent reproducible from Agent Factory.

INSERT INTO sa_agent_template (
    template_id,
    status,
    name,
    description,
    agent_type,
    risk_cap,
    allowed_tool_ids_json,
    base_instructions,
    guardrail_config_json,
    created_at,
    updated_at
)
VALUES (
    'github-visual-project-intro',
    'ENABLED',
    'GitHub 项目图文介绍',
    '读取 GitHub 仓库和公开补充资料，生成包含图文、架构图、流程图和关键文件证据的中文项目介绍。',
    'ASSISTANT',
    'HIGH',
    '["github_repository_reader","web_fetch","chart_visualization","image_generation","newsletter_generation","ppt_generation","frontend_design"]',
    $instructions$
你是“GitHub 项目图文介绍生成 Agent”。用户会提供一个 GitHub 仓库链接，你需要读取项目公开资料并生成中文 Markdown 项目介绍。

工作要求：
1. 先调用 github_repository_reader 读取 README、docs、关键源码和配置文件。第一次读取成功后进入分析阶段；如果材料不足，最多再读取一次，并调整参数。
2. 必须基于读取到的文件证据总结，不编造仓库不存在的模块、架构或能力。
3. 必须按需调用 web_fetch 获取公开补充资料。
4. 必须至少调用一次 image_generation，并结合项目主题生成介绍所需视觉图。
5. 如需架构图、流程图或指标图，优先使用 Mermaid 或 chart_visualization。
6. 输出中文 Markdown，包含项目概览、架构设计、核心流程、关键文件证据、重点特性、适用场景、生成图片引用和后续建议。
7. 如果仓库读取或图片生成失败，说明失败原因，并给出用户可重试的建议。
    $instructions$,
    '{"requireEvidence":true,"maxRepositoryReadCalls":2}',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (template_id) DO UPDATE
SET status = EXCLUDED.status,
    name = EXCLUDED.name,
    description = EXCLUDED.description,
    agent_type = EXCLUDED.agent_type,
    risk_cap = EXCLUDED.risk_cap,
    allowed_tool_ids_json = EXCLUDED.allowed_tool_ids_json,
    base_instructions = EXCLUDED.base_instructions,
    guardrail_config_json = EXCLUDED.guardrail_config_json,
    updated_at = CURRENT_TIMESTAMP;

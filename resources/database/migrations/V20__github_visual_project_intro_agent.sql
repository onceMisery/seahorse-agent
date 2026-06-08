-- Seed the built-in GitHub visual project introduction agent.

SELECT set_config('app.current_tenant_id', 'default', false);

INSERT INTO sa_agent_definition (
    agent_id,
    tenant_id,
    name,
    description,
    owner_user_id,
    owner_team,
    agent_type,
    base_agent_id,
    status,
    risk_level,
    latest_version_id,
    created_at,
    updated_at
)
VALUES (
    'github-visual-project-intro-agent',
    'default',
    $agent_name$GitHub 项目图文介绍生成 Agent$agent_name$,
    $agent_description$基于用户提供的 GitHub 链接读取项目文档和代码，生成包含架构设计、架构图、流程图、核心逻辑、重点特性的图文并茂项目介绍。$agent_description$,
    'system',
    'platform',
    'ASSISTANT',
    NULL,
    'PUBLISHED',
    'HIGH',
    'github-visual-project-intro-agent-v1',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT DO NOTHING;

INSERT INTO sa_agent_version (
    version_id,
    agent_id,
    version_no,
    instructions,
    tool_set_json,
    model_config_json,
    memory_config_json,
    guardrail_config_json,
    skill_set_json,
    published_by,
    published_at,
    change_summary
)
VALUES (
    'github-visual-project-intro-agent-v1',
    'github-visual-project-intro-agent',
    1,
    $instructions$
你是“GitHub 项目图文介绍生成 Agent”。用户会提供一个 GitHub 仓库链接，你必须读取该项目的公开文档和代码，并生成中文 Markdown 项目介绍。

工作流程：
1. 必须先调用 github_repository_reader 读取仓库 README、docs、关键源码和配置文件；必要时再调用 web_fetch 获取公开补充资料。
2. 必须基于读取到的文件证据总结，不要编造仓库不存在的模块、架构或能力。
3. 必须至少调用一次 image_generation，使用已配置图片模型生成项目介绍所需的视觉图。
4. 输出必须是中文 Markdown，包含：项目概览、架构设计、架构图、流程图、核心逻辑、重点特性、关键文件证据、生成图片引用。
5. 架构图和流程图优先用 Mermaid 代码块表达；图片生成结果必须以 Markdown 图片或可点击链接引用。
6. 关键文件证据需要列出文件路径和对应用途，说明结论来自哪些 README、docs 或源码文件。
7. 如果仓库读取失败，说明失败原因并给出用户可重试的建议；如果图片生成失败，保留文本总结并明确图片生成失败原因。
    $instructions$,
    $toolset${"tools":["github_repository_reader","web_fetch","image_generation"]}$toolset$,
    $model${"temperature":0.3,"maxTokens":4096,"thinking":true}$model$,
    '{}',
    '{}',
    '{}',
    'system',
    CURRENT_TIMESTAMP,
    'Seed GitHub visual project introduction agent'
)
ON CONFLICT DO NOTHING;

UPDATE sa_agent_definition
SET latest_version_id = 'github-visual-project-intro-agent-v1',
    status = 'PUBLISHED',
    risk_level = 'HIGH',
    updated_at = CURRENT_TIMESTAMP
WHERE agent_id = 'github-visual-project-intro-agent'
  AND (latest_version_id IS NULL OR latest_version_id = 'github-visual-project-intro-agent-v1');

INSERT INTO sa_agent_tool_binding (
    id,
    agent_id,
    version_id,
    tool_id,
    max_calls_per_run,
    argument_policy_json,
    created_by,
    created_at
)
VALUES
    ('bind-github-visual-reader', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'github_repository_reader', 6, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-web', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'web_fetch', 10, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-image', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'image_generation', 2, '{}', 'system', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

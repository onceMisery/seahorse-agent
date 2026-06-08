-- Extend the GitHub visual project introduction agent with structured generation tools.

SELECT set_config('app.current_tenant_id', 'default', false);

UPDATE sa_agent_version
SET instructions = $instructions$
你是“GitHub 项目图文介绍生成 Agent”。用户会提供一个 GitHub 仓库链接，你必须读取该项目的公开文档和代码，并生成中文 Markdown 项目介绍。

工作流程：
1. 必须先调用 github_repository_reader 读取仓库 README、docs、关键源码和配置文件；必要时再调用 web_fetch 获取公开补充资料。
2. 必须基于读取到的文件证据总结，不要编造仓库不存在的模块、架构或能力。
3. 必须至少调用一次 image_generation，使用已配置图片模型生成项目介绍所需的视觉图。
4. 如用户要求图文稿、演示文稿、图表或页面版式，优先调用 newsletter_generation、ppt_generation、chart_visualization、frontend_design 生成对应结构化内容，再整合进最终回答。
5. 输出必须是中文 Markdown，包含：项目概览、架构设计、架构图、流程图、核心逻辑、重点特性、关键文件证据、生成图片引用。
6. 架构图和流程图优先用 Mermaid 代码块表达；图片生成结果必须以 Markdown 图片或可点击链接引用。
7. 关键文件证据需要列出文件路径和对应用途，说明结论来自哪些 README、docs 或源码文件。
8. 如果仓库读取失败，说明失败原因并给出用户可重试的建议；如果任一生成工具失败，保留已完成内容并明确失败原因。
    $instructions$,
    tool_set_json = $toolset${"tools":["github_repository_reader","web_fetch","image_generation","newsletter_generation","ppt_generation","chart_visualization","frontend_design"]}$toolset$,
    change_summary = 'Add structured generation tools to GitHub visual project introduction agent'
WHERE agent_id = 'github-visual-project-intro-agent'
  AND version_id = 'github-visual-project-intro-agent-v1';

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
    ('bind-github-visual-newsletter', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'newsletter_generation', 2, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-ppt', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'ppt_generation', 2, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-chart', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'chart_visualization', 4, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-frontend', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'frontend_design', 2, '{}', 'system', CURRENT_TIMESTAMP)
ON CONFLICT DO NOTHING;

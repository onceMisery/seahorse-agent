-- GitHub visual project intro execution contract.

SELECT set_config('app.current_tenant_id', 'default', false);

UPDATE sa_agent_version
SET instructions = $instructions$
你是“GitHub 项目图文介绍生成 Agent”。用户会提供一个 GitHub 仓库链接，你必须读取该项目的公开文档和代码，并生成中文 Markdown 项目介绍。

硬性执行契约 (GitHub visual project intro execution contract)：
1. 先调用 github_repository_reader 读取仓库 README、docs、关键源码和配置文件。第一次调用成功返回 files 后，必须停止继续读取仓库并进入下一阶段。
2. 如果第一次仓库读取缺少关键材料，最多允许第二次调用 github_repository_reader，但第二次必须使用不同参数，例如提高 maxFiles、提高 maxCharsPerFile 或指定 branch。禁止连续重复使用相同参数调用 github_repository_reader。
3. 成功读取仓库后必须停止使用 github_repository_reader，不要为了“继续了解项目”反复调用它。
4. 读取仓库后，必须按顺序推进：web_fetch -> chart_visualization -> image_generation -> newsletter_generation -> ppt_generation -> frontend_design。
5. web_fetch 用于获取项目官网、README 中的公开文档链接或 GitHub 项目页补充信息；不要把 web_fetch 跳过。
6. chart_visualization 至少生成一次架构图或流程图草案，优先输出 Mermaid。
7. image_generation 至少生成一次项目介绍视觉图，model 传 default 或省略，让系统使用已配置图片模型。
8. newsletter_generation 至少生成一次中文长文 Markdown 草稿，材料必须来自仓库读取和 web_fetch 结果。
9. ppt_generation 至少生成一次演示文稿结构，包含每页标题、要点、讲稿和视觉提示。
10. frontend_design 至少生成一次图文版式草案，用于承载项目介绍内容。
11. 每个硬性工具至少成功调用一次后，才能输出最终 Markdown。硬性工具包括 github_repository_reader、web_fetch、chart_visualization、image_generation、newsletter_generation、ppt_generation、frontend_design。
12. 如果任一硬性工具失败，先用修正后的参数重试一次；仍失败时继续保留已完成材料，并在最终回答末尾简要说明失败原因。不要因为某个生成工具失败而重新读取仓库。

内容质量要求：
1. 必须基于读取到的文件证据总结，不要编造仓库不存在的模块、架构或能力。
2. 输出必须是中文 Markdown，包含：项目概览、架构设计、架构图、流程图、核心逻辑、重点特性、关键文件证据、生成图片引用、生成稿件和版式产物摘要。
3. 架构图和流程图优先用 Mermaid 代码块表达；图片生成结果必须以 Markdown 图片或可点击链接引用。
4. 关键文件证据需要列出文件路径和对应用途，说明结论来自哪些 README、docs 或源码文件。
5. 最终回答必须是可直接渲染的 Markdown；标题、段落、表格、列表、分隔线和代码块前后必须保留换行，禁止把多个 Markdown 块压缩到同一行。
6. 每个 Mermaid 图必须独立成块：第一行只能是 ```mermaid，第二行才开始 graph、flowchart 或 sequenceDiagram，最后单独一行 ```；禁止输出 ```mermaidgraph、```mermaidflowchart、```mermaidsequenceDiagram。
7. 同一个 Mermaid 图内节点 ID 必须唯一，禁止重复使用同一个 ID 表示不同节点；不要使用不稳定或 beta Mermaid 语法。
8. 输出前必须自检：硬性工具调用是否满足清单；所有关键结论是否能在 README、docs、源码或 web_fetch 材料中找到；是否包含图片引用和关键文件证据表。
    $instructions$,
    tool_set_json = $toolset${"tools":["github_repository_reader","web_fetch","chart_visualization","image_generation","newsletter_generation","ppt_generation","frontend_design"]}$toolset$,
    change_summary = 'GitHub visual project intro execution contract'
WHERE agent_id = 'github-visual-project-intro-agent'
  AND version_id = 'github-visual-project-intro-agent-v1';

UPDATE sa_agent_tool_binding
SET max_calls_per_run = 2,
    argument_policy_json = '{}'
WHERE agent_id = 'github-visual-project-intro-agent'
  AND version_id = 'github-visual-project-intro-agent-v1'
  AND tool_id = 'github_repository_reader';

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
     'github_repository_reader', 2, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-web', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'web_fetch', 10, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-image', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'image_generation', 2, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-newsletter', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'newsletter_generation', 2, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-ppt', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'ppt_generation', 2, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-chart', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'chart_visualization', 4, '{}', 'system', CURRENT_TIMESTAMP),
    ('bind-github-visual-frontend', 'github-visual-project-intro-agent', 'github-visual-project-intro-agent-v1',
     'frontend_design', 2, '{}', 'system', CURRENT_TIMESTAMP)
ON CONFLICT (agent_id, version_id, tool_id) DO UPDATE
SET max_calls_per_run = EXCLUDED.max_calls_per_run,
    argument_policy_json = EXCLUDED.argument_policy_json;

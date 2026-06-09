-- Require the GitHub visual project intro agent to render the whole document as a web preview.

SELECT set_config('app.current_tenant_id', 'default', false);

UPDATE sa_agent_version
SET instructions = replace(
        instructions,
        '该 artifact 用于 Web 端渲染预览，不替代最终 Markdown 正文。',
        '该 artifact 用于 Web 端完整阅读预览，不替代最终 Markdown 正文。HTML 预览 artifact 必须覆盖整篇项目介绍文档，包含所有章节、图片引用、Mermaid 图说明、关键文件证据和第九章产物摘要，而不是只渲染第九章或局部摘要。'
    ),
    change_summary = 'Require whole-document HTML preview for GitHub visual project intro agent'
WHERE agent_id = 'github-visual-project-intro-agent'
  AND version_id = 'github-visual-project-intro-agent-v1'
  AND instructions LIKE '%该 artifact 用于 Web 端渲染预览，不替代最终 Markdown 正文。%';

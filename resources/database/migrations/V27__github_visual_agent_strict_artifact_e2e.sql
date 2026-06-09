-- Tighten the GitHub visual project intro agent contract so E2E validation can
-- prove real generated artifacts, not only final-document summaries.

SELECT set_config('app.current_tenant_id', 'default', false);

UPDATE sa_agent_version
SET instructions = instructions || $append$

严格产物要求（用于真实 E2E 验证）：
1. newsletter_generation、ppt_generation、frontend_design 的工具 observation 是真实产物来源；最终第九章必须基于这些实际 observation 写摘要，不得只声称“已生成”。
2. newsletter_generation 的真实输出会由系统保存为 newsletter.md；ppt_generation 的真实输出会由系统保存为 presentation.md；frontend_design 的真实输出会由系统保存为 frontend-design-tool-output.html。
3. 你仍然必须在最终回答末尾输出整篇项目介绍的 HTML 预览 artifact：<artifact language="html" title="project-intro-web-preview.html"> ... </artifact>。这个 HTML 预览用于整篇文档的 Web 端复制、预览和下载。
4. 如果任一生成工具没有成功返回内容，最终回答必须明确标注该产物缺失；不得把摘要、计划或占位文本当成已生成产物。
$append$,
    change_summary = 'Require strict artifact evidence for GitHub visual project intro E2E'
WHERE agent_id = 'github-visual-project-intro-agent'
  AND version_id = 'github-visual-project-intro-agent-v1'
  AND instructions NOT LIKE '%严格产物要求（用于真实 E2E 验证）%';

# E2E 验证报告 — 对照 DeerFlow Web Alignment Plan

**运行时间**: 2026-06-09 23:42-23:47
**conversationId**: codex-redis-latest-e2e-20260609234230
**Agent**: github-visual-project-intro-agent v1
**目标仓库**: https://github.com/redis/redis
**部署**: 全新构建 backend(36.8s 全模块 SUCCESS)+ frontend 容器，已清理 2 个多余后端容器

---

## 一、部署与清理结果

| 项 | 结果 |
|----|------|
| 删除 seahorse-backend-current-tip-72173bfa (19104) | ✅ 已删除 |
| 删除 seahorse-backend-current-branch (19094) | ✅ 已删除 |
| 删除临时镜像 current-tip:72173bfa (568MB) | ✅ 已回收 |
| 重建并部署 seahorse-backend (9090) | ✅ Up healthy |
| 重建并部署 seahorse-frontend (80) | ✅ Up |
| /auth/login 探活 | ✅ 返回 token |

剩余唯一后端容器：`seahorse-backend`（compose 正牌 service）。

---

## 二、E2E 自动检查（21 项中 18 PASS / 3 FAIL）

### PASS（18）
- rawSseExists / finalMarkdownExists / artifactManifestExists
- htmlArtifactExists / htmlArtifactCoversWholeDocument / htmlArtifactPreservesCssHashes
- **section21HasMermaidFlowchart = PASS**（2.1 节为合规 mermaid flowchart）
- **section21HasNoAsciiBoxDiagram = PASS**（无 ASCII 框图，修复了历史缺陷）
- **imageReferencesAreWebSafe = PASS**（唯一图片为 https://platform-outputs.agnes-ai.space/...png）
- chapter9HasNewsletterSummary / chapter9HasPresentationSummary / chapter9HasWebLayoutSummary
- **requiredToolsWereCalled = PASS**（7 工具全部调用）
- **requiredToolsFinished = PASS**（7 工具全部 FINISHED）

### FAIL（3）— 均为测试脚本字段名过时，非产品缺陷
- newsletterArtifactExists / presentationArtifactExists / frontendDesignArtifactExists

**根因**：`extract-github-visual-agent-e2e.ps1` 从 `typedPayload.message` 提取工件内容，
但当前后端把工件 JSON 放在 `typedPayload.summary`，`message` 仅为状态字符串 "SUCCEEDED"。

**字节级复核（绕过脚本，直接解析 summary）**：
- newsletter_generation: artifactType=newsletter, 1500 字符, U+FFFD=0, 真实内容 ✅
- frontend_design: artifactType=frontend_design, 7154 字符, U+FFFD=0, 真实内容 ✅
- ppt_generation: artifactType=presentation, summary 在 8206 字符处被截断（`...[truncated]`），导致 JSON 不闭合 ⚠️

---

## 三、对照计划文档逐条判定

| 计划任务 | 验证信号 | 判定 |
|---------|---------|------|
| **Task 1** 实时事件绑定 | SSE 含 STEP_STARTED×12 / STEP_FINISHED×12 / TOOL_CALL×11 流式事件 | ✅ 事件流完整 |
| **Task 4** Artifact 生命周期 | AGENT_ARTIFACT 事件带 artifactId/scanStatus=CLEAN/storageRef/disposition/canPreview/previewText 完整契约 | ✅ 契约已落地 |
| **Task 5** 生成工具输出闭环 | image_generation → 持久化 AGENT_ARTIFACT(IMAGE/image/png/CLEAN)✅；newsletter/ppt/chart/frontend 仅走 summary 字段，**未发布 AGENT_ARTIFACT 事件** | ⚠️ 部分落地 |
| **Task 6** 渐进式 skill 加载 | SKILL_LOADED×3 事件，运行时确实加载 skill 资源 | ✅ 已生效 |
| **Task 2** 编码守护 | final-document.md：6431 字符 / 2637 中文 / **U+FFFD=0** | ✅ 输出编码干净 |
| Section 2.1 mermaid（历史缺陷） | 旧 final-doc 有 ASCII 框图；本次为合规 mermaid flowchart | ✅ 已修复 |
| 图片 web-safe | 唯一图片 https，无 localhost/minio/file:// | ✅ |

---

## 四、结论

**部署 + 清理 + E2E 重跑：全部完成。新增功能整体符合计划预期，有 1 个明确缺口 + 1 个测试脚本 bug。**

### 已验证符合预期
1. 7 个工具（github_repository_reader / web_fetch / chart_visualization / image_generation /
   newsletter_generation / ppt_generation / frontend_design）全部成功调用并完成。
2. image_generation 完整闭环为持久化 AgentArtifact，带 scanStatus=CLEAN、web-safe storageRef、
   disposition、canPreview、previewText —— 印证 Task 4/Task 5 的 artifact 契约。
3. 渐进式 skill 加载（Task 6）运行时生效（SKILL_LOADED×3）。
4. 最终面向用户的 Markdown 编码干净（0 替换字符），Section 2.1 为合规 mermaid，
   图片 web-safe，HTML 覆盖全文 —— 印证 Task 2 编码守护与历史 ASCII 缺陷修复。

### 缺口（需后续修复，非本次部署引入）
1. **[产品] Task 5 未完全闭环**：newsletter / ppt / chart / frontend_design 四个生成工具
   未发布持久化 AGENT_ARTIFACT 事件，内容仅经 SSE `summary` 字段传递。
   后果：这些产物不进入 artifact 工作台、不可下载、不可预览、无安全扫描状态。
2. **[产品] summary 字段截断**：ppt_generation 的 8206 字符产出在 summary 中被截断，
   长内容会丢失。应改走 artifact 持久化通道而非 summary 内联。
3. **[测试] 验证脚本字段名过时**：extract 脚本应从 `summary`（而非 `message`）提取工件，
   否则三类工件永远误报 FAIL。

### 建议
- 优先修复缺口 1+2：让 newsletter/ppt/chart/frontend 复用 image_generation 的
  AgentArtifact 发布路径（计划 Task 5 正是此意图，需扩展
  AbstractChatContentGenerationToolPortAdapter 在 invoke 后持久化 artifact + emit AGENT_ARTIFACT）。
- 同步修复测试脚本字段名（summary），使 21 项检查能真实反映工件状态。

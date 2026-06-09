# Agent Workspace Runtime

最后更新：2026-06-09

本文是 Seahorse Agent Web 端运行时的维护入口。它描述聊天消息、工具调用、skill 调用、artifact、SSE replay 和治理视图的当前 owner，避免后续实现把状态拆回多个互相竞争的 store 或 controller。

## 当前结论

Agent Workspace Runtime 已具备 focused acceptance 覆盖：live stream event、run snapshot、artifact publication、tool-call rendering、skill diagnostics、event backfill、cost/quota governance 都已接入同一套消息工作台语义。最终目标仍要求一次真实 E2E 验证后才能宣称完整闭环。

## Canonical Owners

| 层 | Owner | 责任 |
| --- | --- | --- |
| Stream envelope | `StreamEventEnvelope` / `StreamEventType` | 提供 `eventId`、`eventSeq`、`eventType`、`runId`、`stepId?`、`timestamp`、`typedPayload`；不携带 `messageId` |
| Frontend merge | `frontend/src/stores/chatStreamHandlers.ts` | 将 live event 和 snapshot 幂等合并到当前 assistant message |
| SSE hook | `frontend/src/hooks/useStreamResponse.ts` | 处理 plain text delta、`stream_event` envelope、`resumeRunId` + `lastEventSeq` reconnect URL |
| Snapshot | `KernelAgentRunSnapshotService`、`refreshRunSnapshot(messageId, runId)` | 刷新中断/历史 run 的 timeline、sources、artifacts、approvals、tool calls、skills、quota |
| Tool gateway | `LocalToolGatewayPort` | 策略裁决、审批、审计、输出脱敏和 artifact publication hook |
| Artifact publication | `ToolArtifactPublicationPort` / `GenerationToolArtifactPublicationPort` | 从成功工具观察值持久化 `AgentArtifact` 并追加 `agent.artifact` 事件 |
| Skill runtime | `ChatSelectedSkillResolver`、`SkillRuntimeComposer`、`load_skill_resource` | 校验、注入、metadata-only progressive loading 和运行时诊断事件 |
| Tool discovery | `ToolSearchToolPortAdapter` | 在服务端注入的 allowlist 内返回工具元数据，不返回 schema/secrets |
| Admin replay | `AgentRunEventBufferPort`、`SeahorseAgentRunController`、`AgentInspectorPage` | 事件列表、排序、去重、快照和成本摘要 |

## Event Merge Contract

- `StreamEventEnvelope` 不包含 `messageId`。前端 live event 通过当前 streaming assistant message 定位目标消息；snapshot hydration 可以显式传入 `messageId`。
- 每个消息维护 `lastEventSeq`。旧 event 或旧 snapshot 不得覆盖更新的 live state。
- 数组按稳定 id merge：timeline、sources、artifacts、serverArtifacts、approvals、quota、memory、toolCalls、skills 都不能 wholesale replace。
- `agent.artifact.content` 支持 append 语义；server-side `AgentArtifact` 仍是持久化 authority。
- 未知 event type 必须可忽略，不能中断 plain chat。

## Tool Runtime

Seahorse 保留 `ToolPort.invoke(toolCallId, toolId, arguments)` 的稳定签名。运行时上下文在 gateway 边界由 `ToolInvocationRequest` 承载，只有明确需要模型不可伪造上下文的 helper tool 才使用服务端注入的隐藏参数：

| Tool | Hidden argument | 来源 | 约束 |
| --- | --- | --- | --- |
| `load_skill_resource` | `_seahorseSkillRuntimeBlocks` | `AgentLoopRequest.skillRuntimeBlocks()` | 只能加载当前已选/已绑定 skill 的 `SKILL.md` |
| `tool_search` | `_seahorseAllowedToolIds` | `KernelAgentLoop` 计算后的有效业务工具 allowlist | 缺失 snapshot 时 fail closed，只返回 `toolId`、`name`、`description` |

不要为单个 slice 引入 `ExecutionContext`、`ExecutionMetadata` 或 ThreadLocal 全局上下文。若未来必须改变 `ToolPort` 签名，需同一变更更新所有 implementation、gateway、auto-configuration、registrar 和测试。

## Skill Runtime

内置 public skill 的真实目录是：

`seahorse-agent-spring-boot-autoconfigure/src/main/resources/skills/public/`

当前共有 21 个 `SKILL.md`：

`academic-paper-review`, `bootstrap`, `chart-visualization`, `claude-to-deerflow`, `code-documentation`, `consulting-analysis`, `data-analysis`, `deep-research`, `find-skills`, `frontend-design`, `github-deep-research`, `image-generation`, `newsletter-generation`, `podcast-generation`, `ppt-generation`, `skill-creator`, `surprise-me`, `systematic-literature-review`, `vercel-deploy-claimable`, `video-generation`, `web-design-guidelines`.

运行时路径：

1. 前端传 `selectedSkillNames`。
2. `ChatSelectedSkillResolver` 做服务端校验，最多 5 个，fail fast。
3. `KernelChatInboundService` 合并 per-turn selections 和 version-bound skills。
4. `SkillRuntimeComposer` 直接注入小 skill；较大或 metadata-only skill 提供加载说明。
5. `KernelAgentLoop` 暴露 `load_skill_resource` 并注入 runtime skill snapshot。
6. 前端 Workbench Skills tab 渲染 selected、metadata-only、loaded、skipped、resource-loaded 诊断。

## Artifact And Generation Runtime

内容生成工具仍向模型返回 JSON observation；artifact 持久化是 gateway side effect，不是 controller 或 SSE sender 的职责。

已支持持久化的生成工具：

| Tool id | 输出 | Artifact 行为 |
| --- | --- | --- |
| `image_generation` | 图片 | 默认 `b64_json`，存储为 `AgentArtifactType.IMAGE`，观察值/audit 脱敏 `b64Json` |
| `newsletter_generation` | 文稿 | 存储为文本 artifact，带 preview/provenance |
| `ppt_generation` | 演示内容 | 存储为 artifact，供 Workbench 预览/下载 |
| `chart_visualization` | 图表内容 | 存储为 artifact；A2UI/图表预览继续由前端按 mime/preview 处理 |
| `frontend_design` | 前端设计结构 | 存储为 artifact；A2UI Lite 预览需 `scanStatus=CLEAN` 且 `canPreview=true` |

下载安全边界：

- `AgentArtifact.canPreview()` 和 `disposition()` 由 domain 层根据 `scanStatus` 与 mime/type 决定。
- 后端 `downloadDecision` 会阻止非 clean artifact 下载。
- 前端 `ArtifactInspectorTab` 对非 clean artifact 禁用下载，并显示“文件未通过安全扫描”。

## Replay And Governance

- Chat reconnect 复用 `/rag/v3/chat?resumeRunId=...&lastEventSeq=...`。
- Admin replay 使用 `/api/agent-runs/{runId}/events?afterSeq=...`。
- `AgentInspectorPage` 对 replay event 按 `eventSeq` 排序并去重。
- `CostQuotaInspectorTab` 从 message-bound state 渲染运行成本、配额压力、resume 和 retry 操作，不创建第二个成本 store owner。

## Verification Pointers

Focused tests currently cover the runtime surface:

- Frontend: `chatStreamHandlers`, `chatStore`, `useStreamResponse`, `AgentInspectorPage`, `CostQuotaInspectorTab`, `ToolCallsInspectorTab`, `WorkspaceInspector`, `ArtifactInspectorTab`
- Backend: `GenerationToolArtifactPublicationPortTests`, `LoadSkillResourceToolPortAdapterTests`, `ToolSearchToolPortAdapterTests`, `KernelAgentLoopToolGatewayTests`, `BuiltInAgentToolRegistrarTests`, `SeahorseChatControllerReplayTests`, `SpringSseEventSenderTests`

真实完成门槛仍是计划要求的 E2E：启动后端和前端，走一次带 skill、tool call、生成 artifact、Workbench 渲染、SSE resume/admin replay 的端到端流程。

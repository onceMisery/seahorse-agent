# Phase 1：Web 任务可用闭环

## 1. 阶段目标

让 C 端用户可以在 Web 聊天中发起一个长任务，并稳定看到任务进度、工具确认、来源、产物和失败恢复入口。

本阶段解决的问题不是“让 Agent 更聪明”，而是让已有 AgentRun、AgentStep、Checkpoint、Approval、ContextPack 等后端骨架真正成为用户可感知的 Web 任务体验。

## 2. 当前基础

已具备：

- `SeahorseChatController` 提供 `/rag/v3/chat` SSE 流式响应和 `/rag/v3/stop` 停止接口。
- `chatStore.ts` 支持 message/thinking/finish/done/cancel/reject/title 事件。
- `AgentRun`、`AgentStep`、`AgentCheckpoint`、`ApprovalRequest`、`ContextPack` 已有领域和部分 API。
- 前端已有消息内 Artifact 解析，但 Artifact 还不是服务端一等资源。

主要缺口：

- SSE 事件缺少任务级 step/progress/source/artifact 协议。
- 刷新页面后不能按 runId 恢复正在运行的任务。
- 用户即时确认还没有进入聊天主流程。
- 来源和产物还嵌在消息文本里，缺少可追溯模型。

## 3. 范围

### 3.1 本阶段做

1. 扩展 SSE 事件协议。
2. 新增用户可见任务时间线。
3. 实现 SSE 断线恢复最小闭环。
4. 将 Approval 映射为聊天内用户确认卡片。
5. 新增 AgentArtifact 最小模型和前端产物面板。
6. 新增来源引用卡片的最小展示协议。

### 3.2 本阶段不做

- 不新增本地沙箱。
- 不实现 A2A 或 Agent Mesh。
- 不开放用户自定义 MCP。
- 不做完整研究 agent 的 planner/researcher/writer 拆分。
- 不实现复杂 artifact 协同编辑。

## 4. 领域设计

### 4.1 StreamEventType 扩展

建议在 kernel 或 web 层统一定义事件名常量，不在前端和后端复制字符串。

| 事件 | 触发时机 | Payload |
| --- | --- | --- |
| `run_started` | run 创建后 | `runId`、`conversationId`、`taskId`、`startedAt` |
| `run_snapshot` | 恢复连接时 | run 状态、已完成 step、当前消息快照、artifact/source 列表 |
| `step_started` | step 开始 | `runId`、`stepId`、`stepType`、`title`、`startedAt` |
| `step_progress` | step 进度变化 | `stepId`、`progressPercent`、`summary` |
| `step_finished` | step 结束 | `stepId`、`status`、`finishedAt`、`durationMs` |
| `tool_call_started` | 工具调用前 | `toolInvocationId`、`toolId`、`riskLevel`、`summary` |
| `tool_call_waiting_user` | 需要用户确认 | `approvalId`、`toolId`、`summary`、`argumentsPreview` |
| `source_found` | 新来源进入 ContextPack | `sourceId`、`sourceType`、`title`、`url`、`confidence` |
| `artifact_created` | 新产物落库 | `artifactId`、`artifactType`、`title`、`previewText` |
| `recoverable_error` | 单步可恢复失败 | `stepId`、`errorCode`、`message`、`retryable` |

已有 `message`、`thinking`、`finish`、`done`、`cancel` 继续保留。

### 4.2 AgentTaskTimeline

新增前端视图模型，不要求第一阶段新增持久化表，优先由 AgentRun/AgentStep 映射。

字段建议：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `runId` | string | 关联 AgentRun |
| `status` | enum | `CREATED`、`RUNNING`、`WAITING_USER`、`SUCCEEDED`、`FAILED`、`CANCELLED` |
| `currentStepId` | string | 当前 step |
| `steps` | array | 时间线节点 |
| `sources` | array | 来源卡片 |
| `artifacts` | array | 产物卡片 |
| `canResume` | boolean | 是否可恢复 |
| `canRetry` | boolean | 是否可重试 |

### 4.3 AgentArtifact

新增领域模型，建议包路径：

```text
kernel.domain.agent.artifact
kernel.application.agent.artifact
ports.inbound.agent.AgentArtifactQueryInboundPort
ports.outbound.agent.AgentArtifactRepositoryPort
ports.outbound.storage.ObjectStoragePort
```

字段建议：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `artifactId` | string | 稳定 ID |
| `runId` | string | 所属 run |
| `messageId` | string | 可为空，关联最终消息 |
| `artifactType` | enum | `REPORT`、`TABLE`、`CHART`、`HTML`、`IMAGE`、`FILE`、`MARKDOWN` |
| `title` | string | 展示标题 |
| `mimeType` | string | 内容类型 |
| `storageRef` | string | 对象存储引用 |
| `previewText` | string | 脱敏预览 |
| `provenanceJson` | string | 来源和 step 引用 |
| `scanStatus` | enum | `PENDING`、`CLEAN`、`BLOCKED` |
| `createdAt` | instant | 创建时间 |

与现有 `SandboxArtifact` 的关系：`AgentArtifact` 是 C 端 Web 的通用产物模型；`SandboxArtifact` 如果保留，只能作为 Phase 4 可选扩展内部产物，不应直接暴露给 C 端。

### 4.4 用户即时确认

复用 ApprovalRequest，但新增确认来源：

| 字段 | 建议 |
| --- | --- |
| `approvalType` | 继续使用 enum，增加或复用 `TOOL_EXECUTION`、`DATA_ACCESS`、`EXTERNAL_SEND`、`COST_LIMIT` |
| `triggerSource` | 新增 enum：`ADMIN_REVIEW`、`USER_INLINE_CONFIRMATION`、`POLICY_GATE` |
| `decisionActorType` | 新增 enum：`USER`、`ADMIN`、`SYSTEM` |

聊天内确认卡片只展示脱敏摘要：

- 工具名称。
- 为什么需要。
- 将访问的数据范围。
- 预计消耗或风险等级。
- 可修改参数。
- 允许、拒绝、修改后允许。

## 5. API 设计

### 5.1 Run snapshot

```http
GET /api/agent-runs/{runId}/snapshot
```

返回：

```json
{
  "runId": "run_1",
  "status": "RUNNING",
  "conversationId": "conv_1",
  "messageSnapshot": {
    "assistantMessageId": "msg_1",
    "content": "partial text",
    "thinking": "partial thinking"
  },
  "steps": [],
  "sources": [],
  "artifacts": [],
  "lastEventSeq": 42
}
```

### 5.2 Stream resume

第一阶段可复用 `/rag/v3/chat` 查询参数，新增：

```http
GET /rag/v3/chat?resumeRunId={runId}&lastEventSeq={seq}
```

约束：

- `resumeRunId` 与当前登录用户必须匹配。
- `lastEventSeq` 缺失时返回 run snapshot。
- 已终态 run 不重新执行，只返回 snapshot 和 done。

### 5.3 Artifact

```http
GET /api/agent-artifacts/{artifactId}
GET /api/agent-runs/{runId}/artifacts
GET /api/agent-artifacts/{artifactId}/download
```

约束：

- HTML、SVG、JS 类主动内容默认 attachment 下载。
- 只有 `scanStatus=CLEAN` 的 artifact 可预览。
- 所有查询必须校验 userId/tenantId 归属。

### 5.4 Inline approval

沿用已有：

```http
POST /api/approvals/{approvalId}/approve
POST /api/approvals/{approvalId}/reject
POST /api/approvals/{approvalId}/modify
```

补充：

```http
GET /api/agent-runs/{runId}/pending-approvals
```

## 6. 前端设计

### 6.1 ChatStore 状态扩展

新增状态：

```ts
type RunTimelineStatus = "CREATED" | "RUNNING" | "WAITING_USER" | "SUCCEEDED" | "FAILED" | "CANCELLED";

type RunTimeline = {
  runId: string;
  status: RunTimelineStatus;
  steps: TimelineStep[];
  sources: SourceCard[];
  artifacts: ArtifactCard[];
  lastEventSeq: number | null;
};
```

### 6.2 UI 组件

| 组件 | 职责 |
| --- | --- |
| `TaskTimelinePanel` | 展示 step 进度、当前状态、失败和重试入口 |
| `InlineApprovalCard` | 展示用户确认动作 |
| `SourceCardList` | 展示来源引用 |
| `ArtifactPanel` | 展示产物预览、下载、复制 |
| `RunRecoveryBanner` | 刷新或断线后提示可恢复任务 |

### 6.3 交互规则

- 时间线默认折叠，只展示当前 step；用户可展开详情。
- 需要用户确认时，输入框保持可用但提示当前任务等待确认。
- 产物面板出现在消息右侧或消息下方，移动端改为底部抽屉。
- 来源卡片点击后显示摘要、来源类型、链接、抓取时间和可信度。

## 7. 后端实施切片

### Task 1.1：SSE 事件协议扩展

- 新增事件 enum/常量。
- `ChatStreamCallbackFactoryPort` 支持发送 typed event。
- `KernelAgentLoop` 或 runtime step recorder 在关键节点发事件。

验收：

- 单元测试覆盖每种事件序列化。
- 前端能接收未知事件并安全忽略。

### Task 1.2：Run snapshot 与事件序号

- 新增 StreamEventRecord 或最小 run event buffer。
- 写入 eventSeq。
- 新增 run snapshot query service。

验收：

- 给定 runId 可以返回当前消息、step、source、artifact 快照。
- 断线后 lastEventSeq 能补发事件或返回 snapshot。

### Task 1.3：Inline approval

- ApprovalRequest 增加触发来源和决策人类型。
- Tool Gateway 收到 `APPROVAL_REQUIRED` 时发 `tool_call_waiting_user`。
- 前端 InlineApprovalCard 调用已有审批 API。

验收：

- 高风险工具未确认前不执行。
- 拒绝后 run 进入明确状态。

### Task 1.4：AgentArtifact 最小闭环

- 新增领域模型、repository port、JDBC adapter、Web API。
- 先支持 `MARKDOWN`、`REPORT`、`FILE` 三类。
- 前端 ArtifactPanel 消费 `/api/agent-runs/{runId}/artifacts`。

验收：

- 产物可落库、查询、下载。
- 主动内容不可内联执行。

### Task 1.5：来源卡片

- ContextPack item 中的 citation 映射为 source card。
- SSE 发送 `source_found`。
- 前端渲染 SourceCardList。

验收：

- RAG 来源能在消息旁展示。
- 来源卡片与 context item 可追溯。

## 8. 测试计划

后端：

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am test
```

前端：

```powershell
npm run build
```

建议新增测试：

- `StreamEventProtocolTests`
- `AgentRunSnapshotServiceTests`
- `InlineApprovalFlowTests`
- `AgentArtifactRepositoryAdapterTests`
- `AgentArtifactControllerTests`
- `chatStoreTimelineEvents.test.ts`

## 9. 退出标准

1. 用户能在 Web 聊天中看到长任务时间线。
2. 刷新页面后能恢复 run snapshot。
3. 高风险工具触发用户确认卡片。
4. 至少一种 Artifact 可落库、展示和下载。
5. RAG 来源能以卡片展示并关联 ContextPack。
6. 所有新增状态、类型、事件均为 enum 或具名常量。


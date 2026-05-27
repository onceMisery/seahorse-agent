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

## 10. 实现指南

本章节为接手开发者提供逐步实现路径，按 Step A（事件协议和断线恢复）和 Step B（Artifact Panel 和 Source/Citation UX）两个阶段组织。

### Step A：事件协议和断线恢复

#### A.1 后端：StreamEventEnvelope

新增文件位置：`seahorse-agent-kernel/src/main/java/.../kernel/domain/stream/StreamEventEnvelope.java`

```java
public record StreamEventEnvelope(
    String eventId,
    long eventSeq,
    StreamEventType eventType,
    String runId,
    String stepId,
    Instant timestamp,
    Object typedPayload
) {
    public static StreamEventEnvelope of(long seq, StreamEventType type, String runId, Object payload) {
        return new StreamEventEnvelope(
            UUID.randomUUID().toString(), seq, type, runId, null, Instant.now(), payload);
    }
}
```

设计要点：

- `eventId` 使用 UUID，保证全局唯一，便于幂等去重。
- `eventSeq` 为 run 内递增序号，前端据此判断是否有 missed event。
- `typedPayload` 使用 Object 类型，序列化时由 Jackson 根据 `eventType` 多态处理。
- `stepId` 可为 null（run 级事件不关联 step）。

#### A.2 后端：AgentRunEventBufferPort

新增文件位置：`seahorse-agent-kernel/src/main/java/.../ports/outbound/agent/AgentRunEventBufferPort.java`

```java
public interface AgentRunEventBufferPort {
    void append(String runId, StreamEventEnvelope event);
    List<StreamEventEnvelope> getAfter(String runId, long afterSeq);
    Optional<Long> getLatestSeq(String runId);
    void expire(String runId);
}
```

JDBC adapter 位置：`seahorse-agent-adapter-repository-jdbc/src/main/java/.../jdbc/JdbcAgentRunEventBufferAdapter.java`

建表 DDL：

```sql
CREATE TABLE agent_run_event_buffer (
    id          BIGSERIAL PRIMARY KEY,
    run_id      VARCHAR(64) NOT NULL,
    event_seq   BIGINT NOT NULL,
    event_id    VARCHAR(64) NOT NULL,
    event_type  VARCHAR(64) NOT NULL,
    payload     JSONB NOT NULL,
    created_at  TIMESTAMP NOT NULL DEFAULT now(),
    UNIQUE (run_id, event_seq)
);

CREATE INDEX idx_event_buffer_run_seq ON agent_run_event_buffer(run_id, event_seq);
```

TTL 策略：使用定时任务（`@Scheduled`）每分钟清理 `created_at + interval '5 minutes' < now()` 的记录。生产环境可改用 PostgreSQL 分区表或 Redis Sorted Set。

#### A.3 后端：SeahorseChatController 增强

修改文件：`seahorse-agent-adapter-web/src/main/java/.../web/SeahorseChatController.java`

增加请求参数：

- `resumeRunId`（String，可选）：恢复指定 run
- `lastEventSeq`（Long，可选）：从此序号之后开始补发

处理逻辑：

1. 如果 `resumeRunId` 存在且 `lastEventSeq` 存在：
   - 查询 `AgentRunEventBufferPort.getAfter(resumeRunId, lastEventSeq)`
   - 如果有结果 → 先批量发送 missed events，然后继续正常流式推送
   - 如果 buffer 已过期（无结果且 run 仍在运行）→ 调用 `KernelAgentRunSnapshotService` 返回 `RUN_SNAPSHOT` 事件
2. 如果 `resumeRunId` 存在但 `lastEventSeq` 缺失 → 直接返回 `RUN_SNAPSHOT`
3. 如果 run 已终态 → 返回 snapshot + `done` 事件，不重新执行
4. 无 `resumeRunId` → 正常新建 run 流程

#### A.4 后端：LocalChatStreamCallbackFactory 增强

修改文件：`seahorse-agent-adapter-web/src/main/java/.../local/LocalChatStreamCallbackFactory.java`

改造要点：

1. 在 callback 实例中维护 `AtomicLong seqCounter`（per run）
2. 每次发射事件时：
   - 调用 `seqCounter.incrementAndGet()` 分配递增 eventSeq
   - 构造 `StreamEventEnvelope.of(seq, type, runId, payload)`
   - 调用 `agentRunEventBufferPort.append(runId, envelope)` 写入 buffer
   - 将 envelope 序列化为 JSON 写入 SSE（`data: {json}\n\n`）
3. SSE 输出格式变更：

```
event: stream_event
data: {"eventId":"...","eventSeq":1,"eventType":"MESSAGE","runId":"...","stepId":null,"timestamp":"...","typedPayload":{...}}

```

4. 旧事件（message/thinking/finish/done）保持兼容：同时发送旧格式 `event: message` 和新格式 `event: stream_event`，过渡期后移除旧格式。

#### A.5 前端：chatStore 增强

修改文件：`frontend/src/stores/chatStore.ts`

- 新增状态字段 `lastEventSeq: number | null`
- EventSource 创建时，如果存在 `lastEventSeq`，URL 拼接 `&lastEventSeq=${lastEventSeq}`
- 页面刷新时，从 localStorage 读取 `{conversationId}_lastRunId` 和 `{conversationId}_lastEventSeq`
- 收到 `run_started` 时保存 runId 到 localStorage
- 收到任意事件后更新 `lastEventSeq`

修改文件：`frontend/src/stores/chatStreamUtils.ts`

- 新增 `parseStreamEvent(raw: string): StreamEventEnvelope | null` 函数
- 解析 envelope 格式：如果 JSON 包含 `eventSeq` 字段则按新协议处理
- 兼容旧格式：如果没有 `eventSeq` 字段则走原有解析逻辑
- 未知 `eventType` 安全忽略（`console.debug` 记录，不报错不中断）

#### A.6 测试清单

后端测试：

| 测试类 | 验证点 |
| --- | --- |
| `StreamEventEnvelopeTests` | 序列化/反序列化、eventSeq 递增、null stepId 处理 |
| `AgentRunEventBufferPortTests` | append/getAfter/expire、并发写入、TTL 过期 |
| `SeahorseChatControllerReplayTests` | lastEventSeq 命中补发、buffer 过期返回 snapshot、已终态 run 不重执行、未知事件安全忽略 |

前端测试：

- `npm run build` 通过（无 TypeScript 编译错误）
- 手动测试场景：
  - 正常聊天流程不受影响
  - 刷新页面后能看到当前 run snapshot
  - 网络断开 5 秒内重连能补发 missed events
  - 网络断开超过 5 分钟重连返回 snapshot

### Step B：Artifact Panel 和 Source/Citation UX

#### B.1 后端：Artifact 流式事件

已有 `ARTIFACT_CREATED` 事件类型。需要补充增量流式事件：

- `ARTIFACT_CONTENT`：增量内容 delta（用于边生成边渲染长文档/报告）
- `ARTIFACT_COMPLETE`：标记产物生成完成，携带最终 metadata

修改文件：`StreamEventType.java` — 新增枚举值 `ARTIFACT_CONTENT`、`ARTIFACT_COMPLETE`

修改文件：`AgentStreamTimelineEvents.java` — 新增方法：

```java
public StreamEventEnvelope artifactContent(String artifactId, String delta) { ... }
public StreamEventEnvelope artifactComplete(String artifactId, ArtifactMetadata metadata) { ... }
```

Payload 设计：

```json
// ARTIFACT_CONTENT
{"artifactId": "art_1", "delta": "新增的文本片段", "offset": 1024}

// ARTIFACT_COMPLETE
{"artifactId": "art_1", "title": "分析报告", "mimeType": "text/markdown", "size": 4096, "scanStatus": "CLEAN"}
```

#### B.2 前端：独立 ArtifactPanel

新增文件：`frontend/src/components/chat/ArtifactPanel.tsx`

功能需求：

- 接收 `artifacts: ArtifactCard[]` 列表
- Tab 切换多个产物（超过 5 个时显示下拉选择）
- 每个产物支持操作：preview / download / fullscreen / copy
- 流式产物显示 streaming 状态指示器（脉冲动画 + 已接收字节数）
- 主动内容（HTML/JS/SVG）默认 attachment 下载，不内联执行
- 安全产物（Markdown/纯文本/图片）可内联预览

集成方式：

- 在 `ChatPage.tsx` 中使用 `react-resizable-panels` 实现 Chat | ArtifactPanel 双栏布局
- 移动端（`< 768px`）改为底部抽屉（Drawer）
- 无产物时 ArtifactPanel 不渲染，聊天区占满宽度

组件层次：

```
ChatPage
├── ChatPanel (flex: 1)
│   ├── MessageList
│   └── ChatInput
├── ResizeHandle (可拖拽)
└── ArtifactPanel (flex: 0.6, min: 300px)
    ├── ArtifactTabs
    ├── ArtifactPreview (Markdown renderer / image / iframe sandbox)
    └── ArtifactToolbar (download / copy / fullscreen)
```

#### B.3 前端：Source 卡片增强

修改文件：`frontend/src/components/chat/SourceList.tsx`（如不存在则新建 `SourceCardList.tsx`）

增加展示信息：

| 字段 | 展示方式 |
| --- | --- |
| 引用编号 | `[1]`、`[2]`... 与消息正文中的上标对应 |
| 抓取时间 | 相对时间（"3 分钟前"）或绝对时间 |
| 可信度标签 | 彩色 badge：HIGH(绿) / MEDIUM(黄) / LOW(红) / UNKNOWN(灰) |
| 支撑结论 | 1-2 句摘要，说明该来源支撑了哪个结论 |
| 原文链接 | 点击展开，显示 URL + favicon |

交互：

- 默认折叠，只显示编号 + 标题 + 可信度
- 点击展开显示完整信息
- 消息正文中的 `[1]` 上标可 hover 预览对应来源

#### B.4 后端：Source 统一映射

确保 RAG source 和 Web source 都映射为统一的 `AgentRunSnapshotSource` 格式：

```java
public record AgentRunSnapshotSource(
    String sourceId,
    String sourceType,    // "RAG" | "WEB_SEARCH" | "WEB_CRAWL"
    String title,
    String url,
    String snippet,
    String confidence,    // "HIGH" | "MEDIUM" | "LOW" | "UNKNOWN"
    String supportingConclusion,
    Instant fetchedAt,
    int citationIndex
) {}
```

修改文件：`KernelAgentRunSnapshotService.java`

- 从 `ContextPack` 中提取 RAG 来源（向量检索结果）
- 从 `WebSearchResult` / `WebCrawlResult` 中提取 Web 来源
- 统一映射为 `AgentRunSnapshotSource`，按 citationIndex 排序
- 前端不需要区分来源类型，统一渲染 SourceCard

映射规则：

| 来源 | sourceType | confidence 计算 |
| --- | --- | --- |
| 向量检索 | `RAG` | score >= 0.85 → HIGH, >= 0.7 → MEDIUM, else LOW |
| Web 搜索 | `WEB_SEARCH` | 默认 MEDIUM，可由 LLM 评估覆盖 |
| Web 爬取 | `WEB_CRAWL` | 默认 MEDIUM，可由 LLM 评估覆盖 |

---

### 实施顺序建议

```
Step A.1 → A.2 → A.4 → A.3 → A.5 → A.6（测试验证）
Step B.4 → B.1 → B.2 → B.3
```

Step A 和 Step B 可并行开发（后端/前端分工），但 B.1 依赖 A.1 的 StreamEventEnvelope 基础设施。

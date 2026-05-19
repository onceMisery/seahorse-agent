# Phase A Design Review Response

> 日期：2026-05-19  
> 回应文档：`docs/aegis/work/2026-05-19-phase-a-design-review.md`  
> 被修订文档：`docs/aegis/specs/2026-05-19-phase-a-design.md`、`docs/aegis/plans/2026-05-19-phase-a-agent-loop.md`  
> 作者：执行方对 reviewer 意见的逐条核实与处置

---

## 1. 总体处置

**全盘接受。** 4 个事实性错误必须修正，1 个架构性洞察（`ChatMessage`/`ChatRole` 缺 tool-calling 表达能力）是阻塞 A9/A11 的核心问题，新增 **Task A7.5** 是必要的。安全/语义建议（`maxParallelTools=1`、`toolId=function name`、`allowedToolIds`、MCP allowlist）全部采纳。

---

## 2. 逐条核实

### 2.1 事实性论断（已落地源码核实）

| 论断 | 核实位置 | 结论 |
|---|---|---|
| `ChatRole` 只有 SYSTEM/USER/ASSISTANT，无 TOOL | `@/seahorse-agent-kernel/.../ChatRole.java:23-28` | ✅ 成立 |
| `ChatMessage` 字段 = role/content/thinkingContent/thinkingDuration，无 toolCallId/toolCalls | `@/seahorse-agent-kernel/.../ChatMessage.java:30-43` | ✅ 成立 |
| `McpToolExecutionResult` 字段 = toolId/status/content/message；无 `payload()` | `@/seahorse-agent-kernel/.../McpToolExecutionResult.java:33-58` | ✅ 成立。spec §5.1 伪代码 `r.payload()` 是错的 |
| SSE：`onContent` → message+type=response；`onThinking` → message+type=think；完成走 FINISH+DONE；`onError` 走 `sender.fail` | `@/seahorse-agent-adapter-web/.../LocalChatStreamCallbackFactory.java:93-128` | ✅ 成立。spec §3.2/§5.3 的事件映射表错误 |
| `ChatRequest.enableTools` 与新 `tools/toolChoice` 双开关 | A6 commit `b41ec7b`，现状 | ✅ 我自己造的双开关 |
| noop 的 `streamChatWithTools` 只 `onComplete()`，未调 collector，与"空列表=最终回答"约定不一致 | A7 草稿（已回滚） | ✅ 成立 |
| `traceRecorder.markFallback(...)` 不存在 | `@/seahorse-agent-kernel/.../KernelRagTraceRecorder.java` | ✅ 我捏造的 API |

### 2.2 接受度

| Review 建议 | 接受？ | 备注 |
|---|---|---|
| 新增 Task A7.5：扩展 `ChatMessage/ChatRole` 支持 tool-calling | ✅ 完全接受 | 这是 A9/A11 的真正阻塞点 |
| `ToolDescriptor.toolId` = OpenAI function name；`name` 仅展示 | ✅ 接受 | 减少映射表，简化 A11 |
| `AgentLoopRequest.tools` 改名 `allowedToolIds`，空 = 全部可用 | ✅ 接受 | 默认放权符合 Agent 模式预期 |
| `ChatRequest.enableTools` 标记 `@Deprecated`，新逻辑只看 `tools.isEmpty()` | ✅ 接受 | 单一信号源 |
| `maxParallelTools` 默认 `1` | ✅ 接受 | 副作用工具默认安全 |
| MCP 不全量自动注册，走 allowlist | ✅ 接受 | Phase A 不承诺真实 MCP tool 自动可用 |
| `traceRecorder.markFallback` 改为 warn 日志或现有 `recordNode` | ✅ 接受 | 不在 A12 临时造 trace API |
| `onThinking` 文档口径修正为 `event: message` + `type=think` | ✅ 接受 | 已核实源码 |
| `ChatMessage` 多种角色 / `ChatMessage.assistantToolCalls(...)` / `ChatMessage.tool(...)` | ✅ 接受 | 见 A7.5 设计 |
| `ToolInvocationResult.content` 不强制 JSON | ✅ 接受 | content 是"喂回 LLM 的观察文本"，由 Tool 自定 |
| `Collector` 契约：必须且只调一次；空 = 最终回答；在 `onComplete` 之前；解析失败走 `onError` 不调 collector | ✅ 接受 | A7 必须重写 |
| A9：并发执行但回填顺序按 toolCalls 原序 | ✅ 接受 | 维护 LLM 上下文一致性 |
| A9：本轮 content + tool_calls 共存时，content 作为 thought，不输出给用户 | ✅ 接受 | 避免误把中间思考当最终回答 |
| A11 工作量扩到 6+ 项 + 4 类测试 | ✅ 接受 | 我之前低估 |
| 失败模式补充 12 项 | ✅ 接受 | 见 spec §7 修订 |

### 2.3 唯一例外

无。所有 review 条目接受。

---

## 3. 已执行动作（本次）

1. ✅ 回滚 A7 草稿（删除 `ToolCallCollector.java`、`StreamingChatModelPortToolsTests.java`、还原 `StreamingChatModelPort.java`）
2. ✅ 写本 review-response（即本文）
3. 🔜 修订 spec `2026-05-19-phase-a-design.md`
4. 🔜 修订 plan `2026-05-19-phase-a-agent-loop.md`：插入 **Task A7.5**，调整 A2/A3/A6/A7/A8/A11/A12
5. 🔜 列出"已 commit 但需要后续修订的代码点"（A2/A3/A6 的小修，待 A7.5 落地时合并到该 commit）

---

## 4. 代码修订点清单（待执行）

这些修订**不创建独立 commit**，而是合并到接下来对应 Task 的 commit 中：

### 4.1 合并到 Task A7.5（扩展 ChatRole/ChatMessage）

- 在 `ChatRole` 增加 `TOOL` 枚举值
- 在 `ChatMessage` 增加 `toolCallId: String`、`toolCalls: List<AgentToolCall>` 字段
- 新增 `ChatMessage.assistantToolCalls(content, toolCalls)`、`ChatMessage.tool(toolCallId, content)` 工厂
- 测试覆盖：4 个新工厂 + 序列化语义

### 4.2 合并到 Task A7（重写 ToolCallCollector + streamChatWithTools）

- `StreamingChatModelPort.streamChatWithTools` 契约文档明确：
  - **每次调用必须且只调一次** `ToolCallCollector.onToolCalls(...)`
  - 模型给最终回答时调 `List.of()`
  - collector 必须在 `callback.onComplete()` 之前调用
  - 解析失败走 `callback.onError(...)`，**不**再调 collector
- `StreamingChatModelPort.noop()` 重写：先调 `collector.onToolCalls(List.of())`，再 `callback.onComplete()`
- `ToolCallCollector.noop()` Javadoc 注明"用于测试 / 不需要工具的场景"
- 测试新增 4 类：
  1. 适配器不支持 → `UnsupportedOperationException` + 中文消息
  2. noop 适配器 → 收到 `List.of()` 后 `onComplete`
  3. （A11 落地）真实适配器分片聚合
  4. （A11 落地）非法 arguments → `onError`

### 4.3 合并到 Task A8（McpToolPortAdapter）

- 伪代码改用真实字段：
  ```java
  McpToolExecutionResult r = orchestrator.execute(new McpToolExecutionRequest(toolId, "", arguments));
  return r.success()
      ? ToolInvocationResult.ok(r.content())
      : ToolInvocationResult.failed(r.message());
  ```
- **不**对 `r` 整体做 JSON 序列化；只取 `content()` 喂 LLM、`message()` 作错误说明

### 4.4 修订已 commit 的小问题（合并到 A7.5 commit）

| 项 | 原 commit | 修订 |
|---|---|---|
| `ToolDescriptor` Javadoc | A3 `727dbec` | 注明 `toolId` 是派发 key + OpenAI function name；`name` 仅 UI 展示 |
| `AgentLoopRequest.tools` 改名 `allowedToolIds` | A2 `272b3b0` | 字段重命名 + 空表示全部可用（注释 + 单测） |
| `KernelAgentLoopOptions.maxParallelTools` 默认 `4` → `1` | A5 `25cfff8` | 改默认 + 单测调整 |
| `ChatRequest.enableTools` 标 `@Deprecated` | A6 `b41ec7b` | `@Deprecated(forRemoval = false)` + Javadoc 注明"新逻辑只看 tools 是否空" |

### 4.5 合并到 A12 / A13 修订

| 项 | 修订 |
|---|---|
| A12 `traceRecorder.markFallback(...)` | 改为 `LOG.warn("agent-loop-missing: chatMode=AGENT 但未装配 KernelAgentLoop，降级 RAG")` |
| A13 MCP 注册策略 | 不全量同步；按配置 `seahorse-agent.chat.agent.tools.mcp.include=<csv>` 显式 allowlist。默认空 → 不注册任何 MCP 工具 |

### 4.6 修订 A14（Web）

- 文档明确：不新增 SSE event type；前端通过请求参数和 UI 状态自行标识 Agent 模式

---

## 5. Plan 调整摘要

| Task | 状态 | 调整 |
|---|---|---|
| A1 | 已 commit `0751b1f` | 无 |
| A2 | 已 commit `272b3b0` | 字段 `tools` → `allowedToolIds`（合并到 A7.5 commit） |
| A3 | 已 commit `727dbec` | `ToolDescriptor` Javadoc 修订（合并到 A7.5 commit） |
| A4 | 已 commit `e8be170` | 无 |
| A5 | 已 commit `25cfff8` | `maxParallelTools` 默认 `1`（合并到 A7.5 commit） |
| A6 | 已 commit `b41ec7b` | `enableTools` `@Deprecated`（合并到 A7.5 commit） |
| **A7.5（新）** | 待落地 | 扩展 `ChatRole/ChatMessage` 支持 tool-calling |
| A7 | 草稿已回滚 | 重写 collector 契约（每轮必调一次 + 空列表语义 + 顺序约束） |
| A8 | 待落地 | `McpToolPortAdapter` 改用真实字段 |
| A9 | 待落地 | 并发执行 + 顺序回填；content+toolCalls 共存时 content 作 thought |
| A10 | 待落地 | SSE 事件映射改写：thinking 走 message+type=think |
| A11 | 待落地 | 任务范围扩到 6+ 项 + 4 类测试 |
| A12 | 待落地 | 删除 `markFallback`，改 warn |
| A13 | 待落地 | MCP allowlist 配置；删除"自动暴露"假设 |
| A14 | 待落地 | 明确不新增 SSE event |

---

## 6. 推进顺序（修订后）

1. **本次**：写完本 response + 修订 spec + 修订 plan + 提交（合 1 个 docs commit）
2. **下次开工**（用户启动后）：执行 A7.5 → A7 → A8 → A9 → A10 → A11 → A12 → A13 → A14
3. A7.5 commit 顺带修订 A2/A3/A5/A6 的微调点（避免散碎 commit 历史）

---

## 7. 待用户确认

修订后的 spec/plan 提交前，请确认：

- 是否同意 **A2/A3/A5/A6 的修订合并到 A7.5 commit**？（不影响功能，仅 commit 历史）
- 是否同意 **`maxParallelTools` 默认改 `1`**（影响 A9 行为：默认串行）？
- 是否同意 **MCP 工具 allowlist 配置默认空**（Phase A 端到端无真实工具，需用户配置才生效）？

如果都同意，我立刻落地 spec/plan 修订。

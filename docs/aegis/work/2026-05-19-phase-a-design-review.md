# Phase A Design Review: LLM-Driven Agent 编排层

> 日期：2026-05-19  
> 范围：`feat/phase-a-agent-loop` worktree 中 Phase A 设计文档与 A1-A7 当前代码草稿  
> 被审文档：`docs/aegis/specs/2026-05-19-phase-a-design.md`  
> 结论类型：设计审查记录，不是最终 gate decision

## 1. 总体结论

Phase A 的主方向成立：通过 `chatMode=agent` 在现有 RAG 主链路之外新增 `KernelAgentLoop`，并以 `ToolPort` / `ToolRegistryPort` / `StreamingChatModelPort` 端口隔离模型与工具执行，是符合当前架构边界的。

但当前设计还不能直接进入 A8-A14 实现，主要阻塞在 OpenAI function-calling 的消息契约没有补齐。现有 `ChatMessage` / `ChatRole` 只能表达 `system/user/assistant + content`，无法表达 assistant tool_calls、tool role message、tool_call_id。若不先补这层契约，A9 的 observation 回填和 A11 的 OpenAI 适配都会写到一半卡住。

建议先做一次小型契约修正，再继续 A8-A14。

## 2. 验证记录

已在 worktree 执行目标测试：

```powershell
.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=ChatModeTests,AgentDomainTests,ToolPortContractTests,InMemoryToolRegistryTests,KernelAgentLoopOptionsTests,ChatRequestToolsTests,StreamingChatModelPortToolsTests" "-Dsurefire.failIfNoSpecifiedTests=false" test
```

结果：

```text
Tests run: 28, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

说明：A1-A6 已提交契约和 A7 草稿的当前测试可以通过；但这些测试尚未覆盖真正的 function-calling 多轮消息拼接。

## 3. A1-A6 Commit 审查

| Task | Commit | 结论 | 需要调整 |
| --- | --- | --- | --- |
| A1 | `0751b1f` | 接受 | 无。`ChatMode` 与 `StreamChatCommand` 兼容构造合理。 |
| A2 | `272b3b0` | 接受，需澄清语义 | `AgentLoopRequest.tools` 建议改名或文档化为 `allowedToolIds`。空列表到底表示“允许全部工具”还是“禁用工具”必须固定。 |
| A3 | `727dbec` | 小改后接受 | `ToolDescriptor.toolId/name` 语义有歧义。建议规定 `toolId` 就是 OpenAI function name / 派发 key，`name` 仅为展示名；或者删除 `name`，只保留 `toolId/description/jsonSchema`。 |
| A4 | `e8be170` | 接受 | 无。`InMemoryToolRegistry` 作为第一版启动期注册中心足够。 |
| A5 | `25cfff8` | 小改后接受 | `maxParallelTools` 默认值建议从 `4` 改为 `1`。有副作用工具默认并发风险较高，后续增加 `readOnly/sideEffect` 标记后再放开并发。 |
| A6 | `b41ec7b` | 接受，需补兼容说明 | `enableTools` 与 `tools/toolChoice` 并存。建议新逻辑只认 `tools` 非空；`enableTools` 标记为兼容旧字段或 `@Deprecated`，避免 A11 出现双开关语义。 |

## 4. 已落地契约调整建议

### 4.1 `ToolDescriptor`

当前：

```java
record ToolDescriptor(String toolId, String name, String description, String jsonSchema)
```

建议：

- `toolId`：唯一派发 key，同时作为 OpenAI function `name`。
- `name`：只作为 UI 展示名；不能再描述为 OpenAI function name。
- `jsonSchema`：可以继续用 `String`，但注册或发送请求前必须解析成 JSON object。非法 schema 应在注册/启动阶段失败，不建议到模型请求时才失败。

原因：OpenAI tool call 返回的是 function `name`，不是独立 `toolId`。如果 `toolId` 和 `name` 可以不同，A11 需要额外映射表，复杂度不值得。

### 4.2 `ToolInvocationResult`

`content` 不必强制要求 JSON。OpenAI tool role 的 `content` 本质是字符串，工具可以返回 plain text，也可以返回 JSON 字符串。建议契约改为：

- 成功：`success=true`，`content` 为可喂回 LLM 的观察文本。
- 失败：`success=false`，`error` 为可喂回 LLM 的失败说明。

### 4.3 `AgentLoopRequest.tools`

当前字段是 `List<String> tools`，语义不清晰。建议改为：

```java
List<String> allowedToolIds
```

并明确：

- `null` 或空列表：使用 registry 中全部可用工具。
- 非空列表：只暴露 allowlist 中的工具。

如果希望空列表表示禁用工具，也可以，但必须在文档和测试里固定。我的建议是“空 = 全部可用”，因为 Agent 模式默认应能使用 registry。

### 4.4 `ChatRequest.enableTools`

当前 `ChatRequest` 同时有：

```java
private Boolean enableTools;
private List<ToolDescriptor> tools;
private String toolChoice;
```

建议：

- A11 新逻辑只根据 `tools` 是否为空决定是否发送 `tools` 字段。
- `toolChoice` 仅在 `tools` 非空时发送。
- `enableTools` 保留为旧字段，不参与 function-calling 分支，或标记 `@Deprecated`。

## 5. A7 草稿审查

当前 A7 增加：

```java
default StreamCancellationHandle streamChatWithTools(
        ChatRequest request,
        StreamCallback callback,
        ToolCallCollector toolCallCollector)
```

方向可以接受，但当前草稿不建议直接 commit，需先修正 collector 契约。

### 5.1 必须固定回调时序

建议明确：

1. 每次 `streamChatWithTools` 调用必须且只调用一次 `ToolCallCollector.onToolCalls(...)`。
2. 若模型给最终回答，无工具调用，必须回调 `List.of()`。
3. collector 必须在 `callback.onComplete()` 之前调用。
4. 若解析失败，走 `callback.onError(...)`，不得同时再调用 collector。

当前 `StreamingChatModelPort.noop().streamChatWithTools(...)` 只调用 `onComplete()`，没有调用 collector。它和 `ToolCallCollector` 注释“空列表表示最终回答”不一致。

### 5.2 异步流式对 AgentLoop 的影响

AgentLoop 需要“本轮模型输出结束后，拿到 toolCalls，再执行工具，再进入下一轮”。异步流式接口可行，但 A9 必须有一个内部同步等待机制，例如 `CompletableFuture<ModelTurn>`，把一轮 stream 收敛成：

```java
record ModelTurn(String content, String thinking, List<AgentToolCall> toolCalls)
```

否则 execute 版本很难可靠实现。

## 6. A8-A14 设计审查

### A8: `McpToolPortAdapter`

方向合理，但伪代码需要修正。

当前设计写：

```java
return ToolInvocationResult.ok(objectMapper.writeValueAsString(r.payload()));
```

实际 `McpToolExecutionResult` 没有 `payload()`，字段是 `success()/content()/message()/status()`。建议实现：

```java
McpToolExecutionResult r = orchestrator.execute(new McpToolExecutionRequest(toolId, "", arguments));
if (r.success()) {
    return ToolInvocationResult.ok(r.content());
}
return ToolInvocationResult.failed(r.message());
```

是否序列化完整结果要谨慎。第一版建议只把 `content()` 喂回 LLM，失败喂 `message()`。

### A9: `KernelAgentLoop` 核心循环

状态机整体合理，但有三个必须补充点：

1. 先补 `ChatMessage` 的 tool-calling 表达能力。
2. 多个 toolCalls 即使并发执行，回填到 messages 时也必须保持 LLM 原始 toolCalls 顺序。
3. 模型本轮既有 content 又有 toolCalls 时，content 不应作为最终回答输出给用户，应作为 thought/assistant tool-call message 进入中间步骤。

当前文档中的 `ChatMessage.tool(...)` 尚不存在。

### A10: SSE 流式接入

方向合理，但事件映射要修正文档口径。

项目当前没有独立 `thinking` SSE event type。`onThinking` 实际通过 `message` 事件发送，payload type 为 `think`。所以文档应写为：

| Loop 内部事件 | 实际 SSE |
| --- | --- |
| thinking / 工具摘要 | `event: message`，payload `type=think` |
| 最终回答 token | `event: message`，payload `type=response` |
| 完成 | `event: finish` + `event: done` |
| 异常 | `SseEmitter.completeWithError(...)`，不是业务 `error` event |

### A11: `OpenAiCompatibleModelAdapter` function-calling

此任务工作量被低估。需要包括：

- 请求 payload 增加 `tools` 和 `tool_choice`。
- `ToolDescriptor.jsonSchema` 从字符串解析为 JSON object。
- `messages` payload 从 `Map<String,String>` 改为 `Map<String,Object>`。
- 支持 assistant `tool_calls`。
- 支持 tool role message，包括 `tool_call_id`。
- SSE 解析 `delta.tool_calls`，按 `index` 聚合 id/name/arguments。
- 处理 arguments 非法 JSON、缺 function name、缺 id 等失败路径。

建议 A11 增加至少 4 类测试：

1. 发送 tools 时 body 格式正确。
2. SSE tool_calls 分片聚合正确。
3. 纯 content 流 collector 收到空列表。
4. 非法 arguments 触发 `onError` 或结构化失败。

### A12: `KernelChatInboundService` 路由

路由方向合理：

- `chatMode=RAG`：完全走旧 `KernelChatPipeline`。
- `chatMode=AGENT` 且 loop 存在：走 `KernelAgentLoop`。
- `chatMode=AGENT` 但 loop 未装配：可以降级 RAG。

但伪代码中的 `traceRecorder.markFallback(...)` 不存在。建议先不新增 trace API，改成：

- 记录 warn 日志。
- 或用现有 `recordNode(...)` 记录一个短节点。
- 如果确实需要 fallback metadata，应作为单独 trace schema 增强，不要在 A12 临时造方法。

### A13: Spring 自动装配

方向合理，但 Phase A 可用性需要说清楚。

当前设计只创建 `McpToolPortAdapter` Bean，不把 MCP tools 注册进 `ToolRegistryPort`。这意味着即使开启 agent mode，默认 registry 仍可能为空，端到端没有真实工具可用。

建议二选一：

1. Phase A 明确只是 loop 骨架，不承诺真实 MCP tool 自动可用。
2. 增加显式 allowlist registrar，例如 `seahorse-agent.chat.agent.tools.mcp.include=weather,time`，只注册白名单工具。

不建议全量自动注册 MCP 工具。

### A14: Web 入口透传 `chatMode`

合理。非法值回退 RAG 可以接受。权限不在 Phase A 范围内。

补充建议：响应里不需要新增 SSE event type；前端如果要显示 Agent 模式，只需要在请求参数和 UI 状态上标识即可。

## 7. Q1-Q6 明确反馈

### Q1: MCP 工具的 ToolDescriptor 从哪来？

不建议全量自动导入 MCP registry。

建议 Phase A 用显式注册或 allowlist 注册：

- 默认不暴露任何 MCP 工具。
- 配置 allowlist 后从 `McpToolRegistryPort.findTool(toolId)` 读取描述并转换为 `ToolDescriptor`。
- 后续再考虑工具分类、权限、租户隔离。

原因：MCP 工具并不天然适合全部暴露给 LLM，全量暴露会增加提示词噪声，也可能暴露高风险工具。

### Q2: Adapter 不支持 function-calling 时降级还是报错？

建议：

- Agent loop 未启用或未装配：降级 RAG。
- 已进入 AgentLoop 后，模型适配器不支持 function-calling：报错，不自动降级。

原因：用户显式选择 agent 后，静默退回 RAG 会掩盖配置错误。

### Q3: 工具并发还是串行？

默认串行。

建议 `maxParallelTools=1`，后续满足以下条件再并发：

- 工具声明 `readOnly=true`。
- 或用户显式配置调高并接受副作用风险。

即使并发执行，observation 回填顺序也必须按 toolCalls 原顺序。

### Q4: `onThinking` 是否前端默认展示？

不阻塞 Phase A，也不新增 SSE event。

当前前端链路已经能接收 `onThinking`，但它实际是 `message` event + `type=think`。建议文档修正，而不是新增 `thinking/title/error` event type。

### Q5: AgentLoop 是否复用 RAG Pipeline 前处理？

不复用整条 `KernelChatPipeline`。

建议只做一个窄的 `AgentLoopRequestAssembler`：

- 携带 question。
- 携带 history。
- 可选携带 memoryContext。
- 不调用 retrieval / intent / rewrite 整条 RAG 私有链。

原因：AgentLoop 是工具循环编排，不应继承 RAG pipeline 的查询改写、检索、空检索 fallback 等行为。后续 memory/search 应工具化进入 loop。

### Q6: Trace 节点粒度？

每轮一个 step 节点、每个 tool 一个子节点是合理的。

但需要注意：

- 当前 trace schema 没有 metadata 字段。
- 不要把完整 arguments/result 写入 trace。
- 节点数不是固定 `2 * steps`，一轮多个 toolCalls 时会更多。
- 建议记录摘要：toolId、success、duration、error class/message 截断版。

## 8. 失败模式补充

文档 §7 建议补充以下路径：

| 场景 | 建议行为 |
| --- | --- |
| collector 未调用 | 本轮模型调用视为协议错误，AgentLoop 超时或报错，不进入下一轮。 |
| collector 重复调用 | 记录错误并拒绝第二次结果。 |
| collector 晚于 `onComplete` | 视为 adapter 协议错误。 |
| LLM 同时返回 content 和 tool_calls | content 作为 thought，不作为最终 answer。 |
| arguments 分片拼接后不是合法 JSON object | 该 toolCall 失败，或整轮模型协议错误；需固定一种策略。 |
| function name 无法映射 toolId | observation failed，提示工具未注册。 |
| 多 toolCalls 并发完成顺序不同 | 回填顺序必须按原 toolCalls 顺序。 |
| `tool_choice=required` 但 tools 为空 | 构造请求前失败。 |
| registry 为空但进入 Agent 模式 | 明确是普通聊天、报错，还是提示无工具；建议报错或提示无工具，不要无限 loop。 |
| tool result 过大 | 截断并标记，避免下一轮上下文爆 token。 |
| 用户取消但 tool 不响应 interrupt | cancel future 后仍需释放 loop，不等待不可中断工具。 |
| provider 返回非标准 SSE chunk | adapter 解析失败并 `onError`。 |
| stream 中途网络断开 | `onError`，trace run 标 failed。 |

## 9. 建议下一步

建议后续顺序调整为：

1. 修正 A7 collector 契约：每轮必须回调一次，空列表表示无工具调用。
2. 增加 A7.5：扩展 `ChatMessage/ChatRole` 支持 function-calling messages。
3. 修正 A3/A6 文档语义：`toolId` 是派发 key，`enableTools` 退为兼容字段。
4. 将 `maxParallelTools` 默认值改为 `1`。
5. 再开始 A8-A14。

最小可落地的 A7.5 设计：

```java
enum ChatRole {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL
}

class ChatMessage {
    private ChatRole role;
    private String content;
    private String toolCallId;
    private List<AgentToolCall> toolCalls;
    private String thinkingContent;
    private Integer thinkingDuration;

    static ChatMessage assistantToolCalls(String content, List<AgentToolCall> toolCalls);
    static ChatMessage tool(String toolCallId, String content);
}
```

这一步完成后，A9/A11 才具备稳定实现基础。

# Phase A 详细设计文档：LLM-Driven Agent 编排层（v2）

> **状态**：v2，已吸收 reviewer 反馈与用户决策。  
> **范围**：仅 Phase A（KernelAgentLoop + ToolPort + ChatMode 路由 + OpenAI function-calling 适配）。  
> **关联**：
> - 上一版：v1（已删除，对应 review 文档 `@/docs/aegis/work/2026-05-19-phase-a-design-review.md`）
> - response：`@/docs/aegis/work/2026-05-19-phase-a-design-review-response.md`
> - plan：`@/docs/aegis/plans/2026-05-19-phase-a-agent-loop.md`
> - 基线：`@/docs/agent-vs-rag-capability-baseline.md`

## v2 相比 v1 的实质变更

| 变更 | 原因 |
|---|---|
| **新增 Task A7.5**：扩展 `ChatRole`（增 `TOOL`）+ `ChatMessage`（增 `toolCallId/toolCalls`）+ `assistantToolCalls(...)/tool(...)` 工厂 | A9/A11 真正阻塞点：原 `ChatMessage` 无法表达 function-calling 消息 |
| 修正 `McpToolExecutionResult` 字段：用 `r.success()/r.content()/r.message()`，不再用虚构的 `r.payload()` | 事实性错误修正 |
| 修正 SSE 事件映射：`onContent` → `message+type=response`、`onThinking` → `message+type=think`、完成走 `FINISH+DONE`、`onError` 走 `sender.fail` | 已核实 `@/seahorse-agent-adapter-web/.../LocalChatStreamCallbackFactory.java` |
| 删除虚构的 `traceRecorder.markFallback(...)` | 改为 `LOG.warn(...)` |
| `KernelAgentLoopOptions.maxParallelTools` 默认 `4` → **`1`** | 副作用工具默认安全，需 `readOnly` 或显式开启才并发 |
| `ToolDescriptor.toolId` 显式 = OpenAI function name；`name` 仅 UI 展示 | 避免 A11 需要额外映射表 |
| `AgentLoopRequest.tools` 改名 **`allowedToolIds`**；空 = 全部可用 | 命名歧义消除 |
| `ChatRequest.enableTools` 标 `@Deprecated`，新逻辑只看 `tools.isEmpty()` | 单一信号源 |
| `ToolInvocationResult.content` **不强制** JSON | OpenAI tool message content 是字符串，工具自决 |
| MCP 默认 **不暴露**；通过 `seahorse-agent.chat.agent.tools.mcp.include=<csv>` allowlist 注册 | Phase A 不承诺 MCP 真实可用，需用户显式配置 |
| `ToolCallCollector` 契约严格化：每轮必调一次；空 = 最终回答；在 `onComplete` 之前；解析失败走 `onError` 不调 collector | A7 阻塞点 |
| A9 工具并发执行但 observation 回填按原 toolCalls 顺序；content + toolCalls 共存时 content 作 thought 不输出给用户 | 维护 LLM 上下文一致性 |
| §7 失败模式补 12 项（Collector 协议错误、tool result 过大、provider 非标 SSE、stream 中途断开等） | 完备性 |

---

## 0. 文档导读

| 章节 | 内容 |
|---|---|
| §1 背景 | Phase A 定位 |
| §2 已完成清单 | A1–A6 commit 映射 + 后续修订点 |
| §3 完整组件视图 | 包结构、目标态时序、不变量 |
| §4 端口契约详解（修订） | ToolDescriptor 语义、ToolInvocationResult 不强制 JSON、ToolCallCollector 严格契约 |
| §5 待落地组件设计（v2） | **A7.5 / 重写的 A7 / 修正的 A8 / 升级的 A9 / 修正的 A10/A11/A12/A13/A14** |
| §6 兼容性矩阵 | 旧 API/Bean/Config 保留确认 |
| §7 失败模式与降级（21 项） | 9 原项 + 12 新增 |
| §8 配置一览（修订） | 5 个 config key |
| §9 设计抉择固化（Q1–Q6 已定） | 不再为待 review，已落地 |
| §10 推进顺序 | A7.5 → A7 → A8 → ... |

---

## 1. 背景与定位

### 1.1 项目现状
80% 代码量在做 RAG 工程化增强（已稳定）；Agent 骨架（`AgentSPI` / `Feature` / 四层记忆 / MCP 端口 / 模型路由）就绪但**缺 LLM-Driven 编排层**——所有"工具调用"由意图打分驱动，本质仍是"工具型 RAG"。

### 1.2 Phase A 目标
在 kernel 中新增 **`KernelAgentLoop`**（ReAct 风格）。聊天入口通过 `chatMode=rag|agent` 切换：
- `rag`（默认）：走原 `KernelChatPipeline`，零变化
- `agent`：走 `KernelAgentLoop`

### 1.3 不在 Phase A 范围
- B：把检索做成 Tool
- E：把记忆做成 Tool
- C/D/F：状态机、输出自愈、企业治理

---

## 2. 已完成清单

### 2.1 已 commit（6 个，A1–A6）

| Task | commit | 产物 |
|---|---|---|
| A1 | `0751b1f` | `ChatMode { RAG, AGENT }` + `StreamChatCommand` 6 参 + 5 参兼容 |
| A2 | `272b3b0` | `AgentToolCall/AgentObservation/AgentStep/AgentLoopRequest/AgentLoopResult` |
| A3 | `727dbec` | `ToolDescriptor/ToolInvocationResult/ToolPort/ToolRegistryPort` |
| A4 | `e8be170` | `InMemoryToolRegistry` |
| A5 | `25cfff8` | `KernelAgentLoopOptions` |
| A6 | `b41ec7b` | `ChatRequest` 增加 `tools/toolChoice` |

**A7 草稿已回滚**（contract 不对，重写后并入 A7 commit）。

### 2.2 已 commit 的微修订（合并到 A7.5 commit）

| 项 | 修订 |
|---|---|
| `AgentLoopRequest.tools` | 字段重命名为 `allowedToolIds`；空 = 全部可用 |
| `ToolDescriptor` Javadoc | 注明 `toolId` = 派发 key + OpenAI function name；`name` 仅 UI 展示 |
| `KernelAgentLoopOptions.maxParallelTools` | 默认 `4` → `1`；含单测调整 |
| `ChatRequest.enableTools` | 标 `@Deprecated(forRemoval=false)` + Javadoc 注明"新逻辑只看 tools 是否空" |

### 2.3 未启动（8 个）

A7.5（新）/ A7（重写）/ A8 / A9 / A10 / A11 / A12 / A13 / A14。详见 §5。

---

## 3. 完整组件视图

### 3.1 包结构（Phase A 完成态）

```
seahorse-agent-kernel
├── domain
│   ├── agent/
│   │   ├── AgentToolCall   (record, 防御性拷贝)
│   │   ├── AgentObservation (record, ok/failed)
│   │   ├── AgentStep        (record, thought/finalAnswer)
│   │   ├── AgentLoopRequest (final, Builder, allowedToolIds)  ← v2
│   │   └── AgentLoopResult  (record, truncated 标记)
│   └── chat/
│       ├── ChatMode         (enum)
│       ├── ChatRole         (扩展: SYSTEM/USER/ASSISTANT/TOOL)  ← A7.5 新增
│       ├── ChatMessage      (扩展: toolCallId/toolCalls + 2 工厂)  ← A7.5 新增
│       └── ChatRequest      (扩展: tools/toolChoice, enableTools @Deprecated)
└── ports/outbound
    ├── agent/
    │   ├── ToolDescriptor   (toolId = function name)
    │   ├── ToolInvocationResult (content 不强制 JSON)
    │   ├── ToolPort
    │   └── ToolRegistryPort
    └── model/
        ├── StreamingChatModelPort   ← A7 扩展 (streamChatWithTools default)
        └── ToolCallCollector        ← A7 SAM
└── application/agent
    ├── InMemoryToolRegistry
    ├── KernelAgentLoopOptions   (maxParallelTools 默认 1)
    ├── KernelAgentLoop          ← A9/A10
    ├── McpToolPortAdapter       ← A8
    └── McpToolAllowlistRegistrar ← A13（按 allowlist 注册到 Registry）
```

### 3.2 调用时序（目标态，修订后）

```
Web (chatMode=agent)
   │
   ▼
KernelChatInboundService.streamChat   ← A12
   │
   ├─ chatMode==RAG  → KernelChatPipeline.execute (不变)
   │
   └─ chatMode==AGENT
        │
        ├─ Optional<KernelAgentLoop> 缺失 → LOG.warn + 降级 RAG
        │
        └─ 派发 KernelAgentLoop.streamExecute(req, callback)
              │
        ┌─────┴────────────────────────────────────────────────┐
        │ loop step=1..maxSteps                                │
        │                                                       │
        │ 1. 调 StreamingChatModelPort.streamChatWithTools(req, │
        │       innerCallback, collector)                       │
        │    - innerCallback 聚合本轮 content / thinking         │
        │    - collector.onToolCalls(...) **必须且仅一次**       │
        │      在 onComplete 之前调用                            │
        │                                                       │
        │ 2. 收敛为 ModelTurn(content, thinking, toolCalls)     │
        │                                                       │
        │ 3. if toolCalls.isEmpty()                            │
        │       → callback.onContent(content), onComplete()     │
        │       → return FinalAnswer                            │
        │                                                       │
        │ 4. else (有工具调用)                                   │
        │    a. callback.onThinking("[工具调用] ...")            │
        │    b. content 非空时也作 thought 流到 onThinking      │
        │       （不进 onContent，避免误判为最终回答）           │
        │    c. messages.append(assistantToolCalls(content,     │
        │         toolCalls))                                   │
        │    d. 派发工具：                                       │
        │       - 默认串行（maxParallelTools=1）                │
        │       - 并发开启时按 maxParallelTools 控制             │
        │       - 每个工具 perToolTimeout                       │
        │       - **observation 回填顺序按原 toolCalls 顺序**    │
        │    e. for each tc in toolCalls (按原顺序):            │
        │         messages.append(ChatMessage.tool(             │
        │             tc.id, observation.content_or_error))    │
        │                                                       │
        │ 5. step++; if step > maxSteps                        │
        │       → callback.onContent(fallbackText), onComplete()│
        │       → return truncated=true                         │
        │                                                       │
        └───────────────────────────────────────────────────────┘
```

### 3.3 关键不变量

1. **`ChatMode.RAG` 路径 100% 与今日一致**
2. **`KernelAgentLoop` 只依赖端口**
3. **不增 SSE event 类型**：`onContent → message+type=response`、`onThinking → message+type=think`
4. **截断 ≠ 异常**：触达 `maxSteps` → fallback 文案 + 正常 `onComplete`
5. **工具失败 ≠ 流终止**：observation `failed` 作为消息喂回 LLM，由 LLM 决定下一步

---

## 4. 端口契约详解（修订版）

### 4.1 `ToolDescriptor` (A3, 修订 Javadoc)

```java
/**
 * Agent 工具元数据。
 *
 * @param toolId     工具唯一 ID。**同时作为 OpenAI function name** 直接序列化给模型。
 *                   必须满足 OpenAI function name 规则：^[a-zA-Z0-9_-]{1,64}$
 * @param name       仅用于 UI / 日志的展示名；**不作为 OpenAI function name**
 * @param description 喂给 LLM 的工具说明（影响 LLM 选择决策）
 * @param jsonSchema OpenAI parameters 字段对应的 JSON Schema 字符串。
 *                   注册时由 InMemoryToolRegistry 解析校验；非法 schema 在启动期失败
 */
record ToolDescriptor(String toolId, String name, String description, String jsonSchema)
```

### 4.2 `ToolInvocationResult` (A3, 修订语义)

```java
/**
 * 工具调用结果。
 * - success(content): content 是**可喂回 LLM 的观察文本**，由 Tool 自定（plain text 或 JSON string）
 * - failed(error):    error 同样是文本，作为失败消息进入 tool role message
 */
record ToolInvocationResult(boolean success, String content, String error)
```

### 4.3 `ToolPort` / `ToolRegistryPort` (A3)

不变。但**注册时机点新增校验**（A4 修订）：`InMemoryToolRegistry.register` 内部尝试解析 `descriptor.jsonSchema()` 为 JSON，失败 → `IllegalArgumentException`。

### 4.4 `ChatMode` + `StreamChatCommand` (A1)

不变。

### 4.5 `ChatRequest` (A6, 修订)

```java
class ChatRequest {
    private List<ChatMessage> messages;
    private ChatSamplingOptions samplingOptions;

    @Deprecated(forRemoval = false)  // ← 新增 @Deprecated
    private Boolean enableTools;     // 历史字段，新逻辑不再消费

    private List<ToolDescriptor> tools = List.of();  // 单一信号源
    private String toolChoice = "auto";              // 仅当 tools 非空才有意义
}
```

**约定**：`tools.isEmpty()` → OpenAI 适配器**不发送** `tools` / `tool_choice` 字段。

### 4.6 `KernelAgentLoopOptions` (A5, 默认值修订)

```java
class KernelAgentLoopOptions {
    int maxSteps          = 6;
    Duration perToolTimeout = 30s;
    int maxParallelTools  = 1;   // ← v2: 默认串行
}
```

### 4.7 `ChatRole` + `ChatMessage` (A7.5 新增)

```java
public enum ChatRole {
    SYSTEM, USER, ASSISTANT,
    TOOL                          // ← v2 新增
}

@Data
public class ChatMessage {
    private ChatRole role;
    private String content;
    private String thinkingContent;
    private Integer thinkingDuration;

    private String toolCallId;            // ← v2: TOOL role 必填；ASSISTANT 不用
    private List<AgentToolCall> toolCalls; // ← v2: ASSISTANT role 可选；其他角色禁用

    public static ChatMessage system(String content)    { ... }
    public static ChatMessage user(String content)      { ... }
    public static ChatMessage assistant(String content) { ... }
    public static ChatMessage assistant(String content, String thinkingContent) { ... }
    public static ChatMessage assistant(String content, String thinkingContent, Integer thinkingDuration) { ... }

    /** ASSISTANT 携带工具调用决策（OpenAI 协议中 assistant.tool_calls） */
    public static ChatMessage assistantToolCalls(String content, List<AgentToolCall> toolCalls) {
        ChatMessage m = new ChatMessage();
        m.role = ChatRole.ASSISTANT;
        m.content = content;        // 可能为 null/空
        m.toolCalls = List.copyOf(toolCalls);
        return m;
    }

    /** TOOL role：工具执行回填给 LLM，必须携带 toolCallId */
    public static ChatMessage tool(String toolCallId, String content) {
        if (toolCallId == null || toolCallId.isBlank()) {
            throw new IllegalArgumentException("tool message 必须携带 toolCallId");
        }
        ChatMessage m = new ChatMessage();
        m.role = ChatRole.TOOL;
        m.content = content;
        m.toolCallId = toolCallId;
        return m;
    }
}
```

### 4.8 `StreamingChatModelPort.streamChatWithTools` (A7 重写)

```java
public interface StreamingChatModelPort {

    StreamCancellationHandle streamChat(ChatRequest request, StreamCallback callback);

    /**
     * 带工具调用的流式对话（OpenAI 兼容 function-calling）。
     *
     * **回调契约**（实现方必须保证）：
     * 1. {@link ToolCallCollector#onToolCalls(List)} 在本次调用过程中**有且仅有一次** invocation。
     * 2. 若模型本轮未决定调用工具（即给最终回答），collector 必须收到 {@code List.of()}。
     * 3. collector 调用**必须**在 {@code callback.onComplete()} 之前。
     * 4. 若 SSE 解析失败：调 {@code callback.onError(throwable)}，**不**再调 collector。
     * 5. 适配器不支持工具调用时抛 {@link UnsupportedOperationException}（中文消息）。
     *
     * @param request          模型请求（tools 非空时携带 OpenAI tools/tool_choice 字段）
     * @param callback         流式回调；content → onContent，reasoning → onThinking
     * @param toolCallCollector 模型决策后收集工具调用列表
     */
    default StreamCancellationHandle streamChatWithTools(
            ChatRequest request,
            StreamCallback callback,
            ToolCallCollector toolCallCollector) {
        throw new UnsupportedOperationException("当前模型适配器尚未支持工具调用（function-calling）");
    }

    static StreamingChatModelPort noop() {
        return new StreamingChatModelPort() {
            @Override
            public StreamCancellationHandle streamChat(ChatRequest req, StreamCallback cb) {
                if (cb != null) cb.onComplete();
                return () -> {};
            }
            @Override
            public StreamCancellationHandle streamChatWithTools(
                    ChatRequest req, StreamCallback cb, ToolCallCollector collector) {
                // v2 修订：必须先调 collector 满足契约
                if (collector != null) collector.onToolCalls(List.of());
                if (cb != null) cb.onComplete();
                return () -> {};
            }
        };
    }
}

@FunctionalInterface
public interface ToolCallCollector {
    void onToolCalls(List<AgentToolCall> toolCalls);

    static ToolCallCollector noop() { return calls -> {}; }
}
```

### 4.9 `AgentLoopRequest.allowedToolIds` (A2, 字段重命名)

```java
class AgentLoopRequest {
    // ...
    private final List<String> allowedToolIds;  // 原 tools

    public List<String> allowedToolIds() { return allowedToolIds; }
}
```

**语义**：
- `null` 或空 → 使用 registry 中**全部**可用工具
- 非空 → 仅暴露 allowlist 中的工具

---

## 5. 待落地组件设计（v2）

### 5.1 Task A7.5（新增）— 扩展 ChatRole/ChatMessage 支持 tool-calling

**目标**：让 `ChatMessage` 能表达 assistant 携带 toolCalls 决策，以及 tool role 携带 toolCallId 的工具回填消息。

**改动**：见 §4.7。

**测试** `ChatMessageToolCallingTests`：
1. `ChatRole.TOOL` 存在
2. `ChatMessage.assistantToolCalls("", List.of(call))` → role=ASSISTANT、toolCalls 不可变拷贝
3. `ChatMessage.tool("c1", "{...}")` → role=TOOL、toolCallId 必填校验
4. 序列化字段顺序与 OpenAI 兼容（按 role 选字段集）

**附带**：本 commit 顺带落地 §2.2 的 4 个微修订（`allowedToolIds`、`ToolDescriptor` Javadoc、`maxParallelTools=1`、`enableTools @Deprecated`），调整对应的已通过测试。

### 5.2 Task A7（重写）— ToolCallCollector + streamChatWithTools

按 §4.8 落地。`StreamingChatModelPortToolsTests` 重写：
1. 适配器只实现 `streamChat` → `streamChatWithTools` 抛 `UnsupportedOperationException`，中文消息含"工具调用"
2. `noop()` 调 → `collector.onToolCalls(List.of())` 先于 `onComplete`
3. `ToolCallCollector.noop()` 不抛错

### 5.3 Task A8（修正）— McpToolPortAdapter

```java
class McpToolPortAdapter implements ToolPort {
    private final KernelMcpOrchestrator orchestrator;

    public McpToolPortAdapter(KernelMcpOrchestrator orchestrator) {
        this.orchestrator = Objects.requireNonNull(orchestrator);
    }

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String,Object> arguments) {
        try {
            McpToolExecutionResult r = orchestrator.execute(
                new McpToolExecutionRequest(toolId, "", arguments));
            return r.success()
                ? ToolInvocationResult.ok(r.content())          // ← v2: 用真实字段
                : ToolInvocationResult.failed(r.message());     // ← v2: 失败用 message
        } catch (Exception ex) {
            return ToolInvocationResult.failed(ex.getMessage());
        }
    }
}
```

**关键决策（v2）**：
- 移除 v1 的 `ObjectMapper` 序列化整体结果——只取 `content()` 喂 LLM、`message()` 作错误说明
- `McpToolExecutionRequest.question` 仍传 `""`（Agent 模式 LLM 已自决 arguments）

**测试** `McpToolPortAdapterTests` 3 个：
1. `success()=true` → `ok(r.content())`
2. `success()=false` → `failed(r.message())`
3. orchestrator 抛异常 → `failed(ex.getMessage())`

### 5.4 Task A9（升级）— KernelAgentLoop 核心循环

**类签名（v2）**：
```java
class KernelAgentLoop {
    KernelAgentLoop(StreamingChatModelPort modelPort,
                    ToolRegistryPort toolRegistry,
                    KernelAgentLoopOptions options);

    AgentLoopResult execute(AgentLoopRequest request);
}
```

**核心收敛对象**：
```java
record ModelTurn(String content, String thinking, List<AgentToolCall> toolCalls) {}
```

每轮通过内部 `BufferingCallback` + `CompletableFuture<ModelTurn> turnFuture`，把"异步 SSE 流"收敛为"同步一轮"。

**状态机（v2 修订）**：
```
ENTER → step=0, messages=[system, user]
  │
  ▼
LOOP:
  step++
  if step > maxSteps:
      return Result(fallbackText, steps, truncated=true)
  
  turnFuture = new CompletableFuture<>()
  innerCb = BufferingCallback(turnFuture)
  collector = (calls) -> {
      // 协议校验：collector 只能被调一次
      if (already invoked) → throw IllegalStateException
      mark invoked
      // 完成 turnFuture 待 onComplete 触发
  }
  
  modelPort.streamChatWithTools(req(messages, tools, toolChoice), innerCb, collector)
  
  ModelTurn turn = turnFuture.get(modelTurnTimeout)
  if (!collector.invoked && turn.successful):
      throw AgentLoopException("协议错误：collector 未被调用")
  
  if (turn.toolCalls.isEmpty()):
      // 最终回答
      return Result(turn.content, steps, false)
  
  // 工具调用：先 thought
  if (turn.content not blank):
      // content 与 toolCalls 共存时，content 作 thought（不输出给用户）
      thoughts.append(turn.content)
  
  // 派发工具
  observations = executeWithOrdering(turn.toolCalls, toolRegistry, options)
  
  // 拼回 messages（按原 toolCalls 顺序）
  messages.append(ChatMessage.assistantToolCalls(turn.content, turn.toolCalls))
  for each (tc, obs) in zip(turn.toolCalls, observations):
      messages.append(ChatMessage.tool(tc.id, obs.success ? obs.content : obs.error))
  
  steps.append(AgentStep.thought(turn.thinking, turn.toolCalls, observations))
  
  goto LOOP
```

**`executeWithOrdering`**：
- 默认 `maxParallelTools=1` → 严格串行
- 并发开启时用 `CompletableFuture` 并发，但**索引数组**保证按原顺序返回
- 单工具超时 → `Observation.failed("工具超时")`
- 工具未注册 → `Observation.failed("Tool 未注册: " + toolId)`
- 工具抛异常 → `Observation.failed(ex.getMessage())`
- `allowedToolIds` 非空 → 仅查 allowlist 内工具；外部 toolId → `failed("Tool 不在 allowlist 中")`

**测试** `KernelAgentLoopTests`（v2 7 个场景）：
1. 一步给最终回答
2. 一步工具 → 第二步最终回答（验证 messages 拼接顺序：user → assistantToolCalls → tool → assistant）
3. content + toolCalls 共存 → content 进 thought，不在 callback.onContent
4. maxSteps 截断 → truncated=true
5. 工具未注册 → observation `failed`，循环继续
6. 工具抛异常 → observation `failed`，循环继续
7. collector 未调用 → `AgentLoopException("协议错误")`

### 5.5 Task A10（修正）— SSE 流式接入

新增 `streamExecute(req, callback): StreamCancellationHandle`。

**事件映射（v2 修正）**：
| Loop 内部事件 | 实际 SSE |
|---|---|
| thinking / 工具摘要 | `event: message`、payload `type=think` |
| 最终回答 token | `event: message`、payload `type=response` |
| 完成 | `event: finish` + `event: done` |
| 异常 | `SseEmitter.completeWithError(...)`（即 `sender.fail()`），不是业务 error event |

**取消**：返回 handle 触发 → 设置 `volatile cancelled` → 当前模型流中断 + in-flight tool futures `cancel(true)`。

### 5.6 Task A11（扩范围）— OpenAiCompatibleModelAdapter function-calling

**工作项（v2 完整列表）**：
1. 请求 payload 增加 `tools` 和 `tool_choice`（仅当 `request.tools` 非空时）
2. `ToolDescriptor.jsonSchema` 从字符串解析为 JSON object 嵌入 `function.parameters`
3. `messages` payload 从 `Map<String,String>` 升级为 `Map<String,Object>` 以承载：
   - `assistant.tool_calls: [{id,type,function:{name,arguments}}]`
   - `tool.tool_call_id: <id>`
4. SSE 解析 `delta.tool_calls`，按 `index` 聚合 `id/function.name/function.arguments` 增量
5. 流结束时：emit `collector.onToolCalls(aggregated)` 一次（或空列表）
6. 错误路径：
   - `function.arguments` 拼完不是合法 JSON → 该 toolCall 失败（A9 处理为 observation.failed）或 collector emit 空 + 流端 onError（二选一，**v2 选前者**：尽量给 LLM 一次机会）
   - 缺 `id` 或缺 `function.name` → 跳过该 call，记 warn
7. 不支持的 provider（无 `delta.tool_calls`） → 仅 content 流；collector 收到 `List.of()`

**测试** `OpenAiCompatibleStreamingChatToolsTests`（v2 4 类，每类 1 用例，共 5）：
1. 发送 tools 时 body 格式正确（含 `type=function`、`function.name`、`function.parameters` 为对象）
2. SSE tool_calls 分片聚合正确（3 个 chunk 拼出完整 `{"city":"SH"}`）
3. 纯 content 流 → collector 收到 `List.of()`
4. 非法 arguments JSON → 该 toolCall `function.arguments` 设为原始字符串，emit 给 collector，A9 兜底处理（断言 collector 仍被调一次）
5. tools 为空 → body 不携带 `tools` / `tool_choice` 字段

### 5.7 Task A12（修正）— KernelChatInboundService 路由

```java
class KernelChatInboundService {
    // 旧构造：兼容
    public KernelChatInboundService(KernelChatPipeline pipeline, StreamTaskPort streamTaskPort) {
        this(pipeline, streamTaskPort, Optional.empty(), KernelRagTraceRecorder.noop());
    }

    public KernelChatInboundService(KernelChatPipeline pipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<KernelAgentLoop> agentLoop,
                                    KernelRagTraceRecorder traceRecorder) { ... }

    public void streamChat(StreamChatCommand cmd, StreamCallback cb) {
        if (cmd.chatMode() == ChatMode.AGENT) {
            if (agentLoop.isPresent()) {
                AgentLoopRequest req = buildAgentLoopRequest(cmd);
                agentLoop.get().streamExecute(req, cb);
                return;
            }
            // v2: 降级 RAG + warn（不调虚构的 markFallback）
            LOG.warn("chatMode=AGENT 但 KernelAgentLoop 未装配，降级 RAG: taskId={}, userId={}",
                    cmd.taskId(), cmd.userId());
        }
        pipeline.execute(buildStreamChatContext(cmd, cb));
    }
}
```

**`buildAgentLoopRequest`**（v2 决定不复用 RAG pipeline 前处理）：
- `question` = cmd.question()
- `history` = `memoryPort.loadAndAppend(...)` 仅做最薄的会话记忆加载（与 RAG 一致）
- `allowedToolIds` = null（用 registry 全部）
- `samplingOptions` = 默认温度 0.3（介于 RAG 的 0 与系统 prompt 的 0.7 之间）
- `memoryContext` = null（Phase E 接入）

**测试** `KernelChatInboundServiceAgentModeTests`：
1. `chatMode=AGENT` + 注入 Loop → 调 `streamExecute`，不调 pipeline
2. `chatMode=AGENT` + 未注入 → 调 pipeline（降级），不抛错（断言 warn 日志可选）
3. 既有 4 个 RAG 测试不变

### 5.8 Task A13（修正）— Spring 自动装配

新文件 `SeahorseAgentKernelAgentAutoConfiguration`：
```java
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "seahorse-agent.chat", name = "agent-mode-enabled", havingValue = "true")
public class SeahorseAgentKernelAgentAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ToolRegistryPort seahorseToolRegistryPort() {
        return new InMemoryToolRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(StreamingChatModelPort.class)
    public KernelAgentLoop seahorseKernelAgentLoop(
            StreamingChatModelPort modelPort,
            ToolRegistryPort toolRegistry,
            ObjectProvider<KernelAgentLoopOptions> options) {
        return new KernelAgentLoop(modelPort, toolRegistry,
                options.getIfAvailable(KernelAgentLoopOptions::defaults));
    }

    @Bean
    @ConditionalOnBean(KernelMcpOrchestrator.class)
    public McpToolPortAdapter seahorseMcpToolPortAdapter(KernelMcpOrchestrator orchestrator) {
        return new McpToolPortAdapter(orchestrator);
    }

    /**
     * 按 allowlist 配置注册 MCP 工具到 ToolRegistry。
     * 默认 allowlist 空 → 无 MCP 工具暴露给 LLM。
     */
    @Bean
    @ConditionalOnBean({McpToolPortAdapter.class, McpToolRegistryPort.class, ToolRegistryPort.class})
    public McpToolAllowlistRegistrar seahorseMcpToolAllowlistRegistrar(
            McpToolPortAdapter adapter,
            McpToolRegistryPort mcpRegistry,
            ToolRegistryPort toolRegistry,
            @Value("${seahorse-agent.chat.agent.tools.mcp.include:}") String includeCsv) {
        return new McpToolAllowlistRegistrar(adapter, mcpRegistry, toolRegistry, parseCsv(includeCsv));
    }
}
```

`McpToolAllowlistRegistrar`：
- 实现 `ApplicationRunner`，启动时按 allowlist 从 `McpToolRegistryPort.findTool(toolId)` 读 descriptor，转 `ToolDescriptor`，注册到 `ToolRegistryPort`
- allowlist 中工具不存在 → warn 日志，不阻塞启动

`application.properties` 新增：
```properties
seahorse-agent.chat.agent-mode-enabled=false
seahorse-agent.chat.agent.max-steps=6
seahorse-agent.chat.agent.per-tool-timeout=30s
seahorse-agent.chat.agent.max-parallel-tools=1
seahorse-agent.chat.agent.tools.mcp.include=
```

### 5.9 Task A14（确认）— Web 入口透传 chatMode

`SeahorseChatController` 增加 `@RequestParam(required=false) String chatMode`；安全转换：null/非法 → `RAG`。

**确认**：不新增 SSE event type；前端通过请求参数和 UI 状态自行标识 Agent 模式。

---

## 6. 兼容性矩阵

| 项目 | 旧值 | 新值 | 兼容性 |
|---|---|---|---|
| `StreamChatCommand` 构造 | 5 参 | 6 参 + 5 参兼容 | ✅ 源码兼容 |
| `ChatRole` | 3 值 | 4 值（加 TOOL） | ✅ 添加枚举值，旧 switch 编译警告但不破坏 |
| `ChatMessage` | 4 字段 + 5 工厂 | 6 字段 + 7 工厂 | ✅ 加字段默认 null，旧工厂保留 |
| `ChatRequest` | 3 字段 | 5 字段（含 @Deprecated） | ✅ Builder + 默认值 |
| `StreamingChatModelPort` | 1 抽象 | 1 抽象 + 1 default | ✅ 旧实现零修改 |
| `KernelChatInboundService` 构造 | 2 参 | 4 参 + 2 参兼容 | ✅ |
| `KernelChatPipeline` | 不变 | 不变 | ✅ |
| `KernelMcpOrchestrator` | 不变 | 不变 | ✅ 仅被 Adapter 读 |
| SSE event 类型 | 9 个 | 9 个 | ✅ 不增 |
| Config key | — | 5 个新增（默认全保守） | ✅ |
| 数据库 schema | 不变 | 不变 | ✅ |

---

## 7. 失败模式与降级（21 项）

### 原 9 项（v1）

| # | 场景 | 行为 |
|---|---|---|
| F1 | `chatMode=agent` + 未装配 Loop | warn 日志 + 降级 RAG |
| F2 | `streamChatWithTools` 抛 `UnsupportedOperationException` | `onError` → SSE `sender.fail`，不降级 |
| F3 | Tool 未注册 | `Observation.failed("Tool 未注册")`，循环继续 |
| F4 | Tool 抛异常 | `failed(ex.getMessage())`，循环继续 |
| F5 | Tool 超时 | `failed("工具超时")`，循环继续 |
| F6 | LLM 给非法 toolCall（缺 id/toolId） | warn + 跳过 |
| F7 | 达到 maxSteps | `truncated=true` + fallback 文案 + `onComplete` |
| F8 | 用户取消 | 取消模型流 + tool futures `cancel(true)` + 释放 emitter |
| F9 | 工具 JSON Schema 不符 | Adapter 异常 → F4 兜底 |

### v2 新增 12 项

| # | 场景 | 行为 |
|---|---|---|
| F10 | collector 未调用（onComplete 之前） | `AgentLoopException("协议错误：collector 未被调用")` → `onError` |
| F11 | collector 重复调用 | 拒绝第二次结果，warn 日志 |
| F12 | collector 晚于 `onComplete` | 视为 adapter 协议错误，结果丢弃 + warn |
| F13 | LLM 同时返回 content + toolCalls | content 作 thought（流到 `onThinking`），不作最终回答 |
| F14 | toolCall.arguments 拼接后不是合法 JSON | Adapter 把 raw string 当 arguments emit；A9 兜底 → `failed("arguments 不是合法 JSON")` |
| F15 | function name 无法映射 toolId | `Observation.failed("Tool 未注册: <name>")` |
| F16 | 多 toolCalls 并发完成顺序不同 | observation 数组按原 toolCalls index 回填 |
| F17 | `tool_choice=required` 但 tools 为空 | A11 适配器构造请求前校验 → 抛 `IllegalArgumentException` |
| F18 | registry 为空但进入 Agent 模式 | LLM 拿到 `tools=[]`；模型应给最终回答；若死循环 → F7 兜底 |
| F19 | tool result 过大（默认 > 8KB） | 截断到 8KB + 末尾追加 `"...[truncated]"`，避免下一轮 token 爆炸 |
| F20 | 用户取消但 tool 不响应 interrupt | future `cancel(true)` 后不等待，loop 释放，下一步 `onError(CancelledException)` |
| F21 | provider 非标 SSE chunk / 中途断开 | adapter 解析失败 → `onError`；trace run 标 failed |

---

## 8. 配置一览（v2）

| Key | 默认 | 含义 |
|---|---|---|
| `seahorse-agent.chat.agent-mode-enabled` | `false` | 关：不装配 AgentLoop。开：装配但仍需 `?chatMode=agent` 触发 |
| `seahorse-agent.chat.agent.max-steps` | `6` | 覆盖 `KernelAgentLoopOptions.maxSteps` |
| `seahorse-agent.chat.agent.per-tool-timeout` | `30s` | `Duration` 格式 |
| `seahorse-agent.chat.agent.max-parallel-tools` | `1` | v2 改默认 1（串行） |
| `seahorse-agent.chat.agent.tools.mcp.include` | （空） | CSV，allowlist 注册 MCP 工具到 ToolRegistry；空 = 不暴露任何 MCP 工具 |

---

## 9. 设计抉择固化（Q1–Q6 已定）

| # | 抉择 | v2 决定 |
|---|---|---|
| Q1 | MCP 工具如何到 ToolRegistry？ | **allowlist**：`seahorse-agent.chat.agent.tools.mcp.include`，默认空 |
| Q2 | Adapter 不支持 function-calling 时 | **报错**（仅未装配 Loop 时降级 RAG） |
| Q3 | 工具并发还是串行？ | **默认串行** `maxParallelTools=1`；observation 顺序保证 |
| Q4 | `onThinking` 是否前端默认展示？ | **不阻塞**，文档已修正实际映射（message + type=think） |
| Q5 | AgentLoop 是否复用 RAG pipeline 前处理？ | **不复用**：只读 history（最薄会话记忆） |
| Q6 | Trace 节点粒度 | **每 step 1 节点 + 每 tool 1 子节点**；只记摘要（toolId/success/duration/error 截断） |

---

## 10. 推进顺序（v2）

1. **A7.5**：扩展 `ChatRole/ChatMessage`，顺带落 §2.2 的 4 个微修订
2. **A7（重写）**：`ToolCallCollector` + `streamChatWithTools`（严格契约）
3. **A8**：`McpToolPortAdapter`（用真实 `r.success()/r.content()/r.message()`）
4. **A9**：`KernelAgentLoop.execute(...)` 核心循环（带 `ModelTurn` 收敛 + 顺序回填）
5. **A10**：SSE 流式 `streamExecute(...)`
6. **A11**：`OpenAiCompatibleModelAdapter` function-calling（扩范围 7 项 + 5 测试）
7. **A12**：`KernelChatInboundService` 路由（warn 日志 + 不复用 pipeline 前处理）
8. **A13**：Spring 装配 + `McpToolAllowlistRegistrar`
9. **A14**：Web 透传 `chatMode`

---

## 11. v2 推进前的最终检查

| 项 | 状态 |
|---|---|
| review 全部事实性错误已修正 | ✅ |
| review 全部架构性建议已接受 | ✅ |
| Q1–Q6 已固化 | ✅ |
| 用户 3 个决策点已采纳（A2/A3/A5/A6 合并到 A7.5；并发默认 1；MCP allowlist 默认空） | ✅ |
| v1 已删除（避免双源） | ✅（本 commit 完成时） |
| A7 草稿已回滚 | ✅ |
| plan 文件同步修订 | 🔜（本 commit） |

修订完成后可启动 A7.5 → A14 串行执行。

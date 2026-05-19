# Phase A 详细设计文档：LLM-Driven Agent 编排层

> **状态**：草案 v1，等待用户 review。  
> **范围**：仅 Phase A（KernelAgentLoop + ToolPort + ChatMode 路由 + OpenAI function-calling 适配）。  
> **关联**：plan `@/docs/aegis/plans/2026-05-19-phase-a-agent-loop.md`、基线 `@/docs/agent-vs-rag-capability-baseline.md`。

---

## 0. 文档导读

| 章节 | 你 review 时关注什么 |
|---|---|
| §1 背景 | 是否与你心智模型一致 |
| §2 已完成 6 + 1 个 Task 的产物 | 已 commit 的代码是否能接受 |
| §3 完整组件视图 | 整体形状是否合理 |
| §4 端口契约详解 | 已落地的契约语义是否需要调整 |
| §5 待落地 A8–A14 设计 | **重点** 决策抉择是否同意 |
| §6 兼容性矩阵 | 是否漏掉破坏点 |
| §7 失败模式与降级 | Loop 异常路径是否齐全 |
| §8 配置一览 | 开关名称/默认值 |
| §9 待 review 的关键抉择 | **必须给反馈** 才能继续动手 |

---

## 1. 背景与定位

### 1.1 项目当前形态
Seahorse Agent 当前 80% 代码量在做 RAG 工程化增强（多通道检索、RRF、Rerank、入库 Pipeline、Trace、Eval）。Agent 骨架（`AgentSPI` / `Feature` / 四层记忆 / MCP 端口 / 模型路由）已就绪，**但没有 LLM-Driven 编排层**——所有"工具调用"都被"意图打分"驱动，本质仍是"工具型 RAG"。

### 1.2 Phase A 目标
在 kernel 中新增 **`KernelAgentLoop`**，实现 ReAct 风格的多步推理：
```
Question → LLM(tools) → ToolCalls → 派发执行 → Observations → 喂回 LLM → ... → FinalAnswer
```
聊天入口通过 **`chatMode` 参数** 切换：
- `chatMode=rag`（默认）：走原 `KernelChatPipeline`，零变化
- `chatMode=agent`：走 `KernelAgentLoop`

### 1.3 不在 Phase A 范围
- B：把检索做成 Tool（`search_knowledge_base` 等）—— 后续 Phase
- E：记忆作为 Tool（`memory_read/write/forget`）—— 后续 Phase
- 状态机 / Human-in-the-Loop / 快照（C） —— 你已明确跳过
- 输出自愈 / RAG Evaluator（D） —— 后续 Phase
- 企业治理（F） —— 后续 Phase

---

## 2. 已完成清单（实际产物）

### 2.1 已 commit（6 个）

| Task | commit | 产物 | 测试 |
|---|---|---|---|
| **A1** | `0751b1f` | `ChatMode { RAG, AGENT }`、`StreamChatCommand` 新 6 参构造 + 旧 5 参兼容、null 归一化 | `ChatModeTests` 4/4 |
| **A2** | `272b3b0` | `AgentToolCall` / `AgentObservation` / `AgentStep` / `AgentLoopRequest` (Builder) / `AgentLoopResult` | `AgentDomainTests` 8/8 |
| **A3** | `727dbec` | `ToolDescriptor` / `ToolInvocationResult` / `ToolPort` (`notFound` 工厂) / `ToolRegistryPort` (`empty` 工厂) | `ToolPortContractTests` 4/4 |
| **A4** | `e8be170` | `InMemoryToolRegistry`：`ConcurrentHashMap`，重复 toolId → `IllegalStateException`，null 防御 | `InMemoryToolRegistryTests` 4/4 |
| **A5** | `25cfff8` | `KernelAgentLoopOptions`：`maxSteps=6` / `perToolTimeout=30s` / `maxParallelTools=4`；Builder + 非法值校验 | `KernelAgentLoopOptionsTests` 3/3 |
| **A6** | `b41ec7b` | `ChatRequest` 增加 `tools: List<ToolDescriptor>`、`toolChoice: String`（默认 `"auto"`），旧 Builder 100% 兼容 | `ChatRequestToolsTests` 3/3 |

**累计**：6 文件新建 + 1 文件扩展，22 个测试用例全 GREEN，反应堆 24 个模块编译通过。

### 2.2 工作目录草稿（未 commit）

| Task | 状态 | 已写文件 | 验证 |
|---|---|---|---|
| **A7** | 草稿，未跑测试 | `ToolCallCollector.java`（新建 SAM）<br>`StreamingChatModelPort.java`（新增 `streamChatWithTools` default + 重写 `noop` 单例）<br>`StreamingChatModelPortToolsTests.java`（新建） | ⏸ 待 review 通过后跑 |

> A7 草稿不会被回滚；review 完毕你确认后我会跑测试并 commit。

### 2.3 未启动（7 个）

A8 → A14。详见 §5。

---

## 3. 完整组件视图

### 3.1 包结构（Phase A 完成态）

```
seahorse-agent-kernel
├── domain
│   ├── agent/                              ← A2 新增
│   │   ├── AgentToolCall   (record, 防御性拷贝)
│   │   ├── AgentObservation (record, ok/failed)
│   │   ├── AgentStep        (record, thought/finalAnswer)
│   │   ├── AgentLoopRequest (final, Builder)
│   │   └── AgentLoopResult  (record, truncated 标记)
│   └── chat/
│       ├── ChatMode         ← A1 新增 (enum)
│       └── ChatRequest      ← A6 扩展 (tools/toolChoice)
└── ports/outbound
    ├── agent/                              ← A3 新增
    │   ├── ToolDescriptor   (record)
    │   ├── ToolInvocationResult (record, ok/failed)
    │   ├── ToolPort         (interface, notFound)
    │   └── ToolRegistryPort (interface, empty)
    └── model/
        ├── StreamingChatModelPort   ← A7 扩展 (streamChatWithTools default)
        └── ToolCallCollector        ← A7 新增 (SAM)
└── application/agent                       ← 持续扩展
    ├── InMemoryToolRegistry     ← A4
    ├── KernelAgentLoopOptions   ← A5
    ├── KernelAgentLoop          ← A9/A10 待落地
    └── McpToolPortAdapter       ← A8 待落地
```

### 3.2 调用时序（目标态）

```
Web (chatMode=agent)
   │
   ▼
KernelChatInboundService.streamChat   ← A12 改造路由
   │
   ├─ chatMode==RAG  → KernelChatPipeline.execute (不变)
   │
   └─ chatMode==AGENT → KernelAgentLoop.streamExecute
                          │
                  ┌───────┴────────┐
                  │ loop ≤ maxSteps │
                  └───────┬────────┘
                          ▼
                StreamingChatModelPort.streamChatWithTools
                  ├─ messages + tools(描述) + toolChoice
                  └─ 回调：
                      ├─ onContent / onThinking (流式 token)
                      └─ ToolCallCollector.onToolCalls(List<AgentToolCall>)
                                │
                                ▼
                  ┌── 若有 toolCalls ──┐
                  │                    │
                  ▼                    ▼
            ToolRegistryPort.find  并发派发，每个 timeout
                  ▼                    │
            ToolPort.invoke ──────────→ ToolInvocationResult
                  │
                  ▼
            ChatMessage.tool(...)  ← 把 observation 拼回 messages
                  │
                  └─ 进入下一轮循环
                  
                  ┌── 若无 toolCalls ──┐
                  │ (模型给最终回答)    │
                  ▼                    
            StreamCallback.onContent + onComplete → SSE 结束
```

### 3.3 关键不变量

1. **`ChatMode.RAG` 路径 100% 与今日一致**：所有现有 `KernelChatPipelineTests` / `KernelChatInboundServiceTests` 不变更。
2. **`KernelAgentLoop` 只依赖端口**：`ToolRegistryPort` / `StreamingChatModelPort` / `KernelRagTraceRecorder`，不直接 `new` 任何适配器。
3. **`StreamCallback` 协议不增类型**：工具调用过程走 `onThinking`，最终回答走 `onContent` + `onComplete`，前端无需修改。
4. **截断（`truncated=true`）≠ 异常**：达到 `maxSteps` 仍未拿到最终回答时，给出 fallback 文案并正常 `onComplete`。

---

## 4. 端口契约详解（已落地部分）

### 4.1 `ToolDescriptor` (A3)

```java
record ToolDescriptor(String toolId, String name, String description, String jsonSchema)
```

| 字段 | 校验 | 用途 |
|---|---|---|
| `toolId` | 非空 | LLM tool_call 的唯一 key |
| `name` | 非空 | OpenAI function name |
| `description` | null → `""` | 喂给 LLM 选择工具时的说明 |
| `jsonSchema` | null → `"{}"` | OpenAI `parameters` 字段（必须合法 JSON） |

**决策**：`jsonSchema` 用 `String` 而非 `JsonNode`，让具体 Tool 决定模式（避免 kernel 依赖 Jackson）。

### 4.2 `ToolInvocationResult` (A3)

```java
record ToolInvocationResult(boolean success, String content, String error)
//   ok(content)     → (true, content, null)
//   failed(error)   → (false, null, error)
```

**决策**：`content` 必须是字符串，目的是直接以 OpenAI 兼容 `"tool"` role 消息回填给 LLM；具体 Tool 自己用 Jackson 序列化。

### 4.3 `ToolPort` (A3)

```java
interface ToolPort {
    ToolInvocationResult invoke(String toolCallId, String toolId, Map<String,Object> arguments);
    static ToolPort notFound(String toolId);  // 缺省"未注册"实现
}
```

**契约**：实现方**必须捕获所有异常**并返回 `failed(...)`，不抛给 Loop。原因：让 Loop 把失败当 observation 喂回 LLM，由 LLM 决定下一步——这是 Agent 容错的核心。

### 4.4 `ToolRegistryPort` (A3) + `InMemoryToolRegistry` (A4)

```java
interface ToolRegistryPort {
    List<ToolDescriptor> listTools();
    Optional<ToolPort> find(String toolId);
    static ToolRegistryPort empty();
}

class InMemoryToolRegistry implements ToolRegistryPort {
    void register(ToolDescriptor d, ToolPort p);  // 重复 toolId → IllegalStateException
    ...
}
```

**决策**：注册以"启动时一次性注册"为主，第一版没做"运行时增删"——上线后如需要，加个 `unregister` 即可，不阻塞 Phase A。

### 4.5 `ChatMode` (A1) + `StreamChatCommand` (A1)

```java
enum ChatMode { RAG, AGENT }

record StreamChatCommand(
    String question, String conversationId, String taskId,
    String userId, boolean deepThinking, ChatMode chatMode)
{
    // 旧 5 参构造：内部转调，chatMode=RAG
    // null chatMode：归一化为 RAG
}
```

**关键兼容性**：所有现有 `new StreamChatCommand(...)` 五参用法继续可用，单元测试已覆盖。

### 4.6 `ChatRequest.tools/toolChoice` (A6)

```java
class ChatRequest {                            // 沿用 Lombok @Data @Builder
    List<ToolDescriptor> tools = new ArrayList<>();  // 默认空
    String toolChoice = "auto";                       // 默认 auto
    // ... 原有字段不变
}
```

**约定**：`tools.isEmpty()` 时 OpenAI 适配器**不发送** `tools` 字段（避免与不支持工具调用的 provider 冲突）。

### 4.7 `KernelAgentLoopOptions` (A5)

```java
final class KernelAgentLoopOptions {
    int maxSteps;          // 默认 6
    Duration perToolTimeout;  // 默认 30s
    int maxParallelTools;  // 默认 4
}
```

**为什么 6 步**：参考主流 ReAct 实践，6 步足以覆盖 90% 的"先检索→再生成"或"先 rewrite→再检索→再生成"场景；过大会让幻觉式工具循环失控。

### 4.8 `StreamingChatModelPort.streamChatWithTools` (A7 草稿)

```java
default StreamCancellationHandle streamChatWithTools(
        ChatRequest request,
        StreamCallback callback,
        ToolCallCollector toolCallCollector) {
    throw new UnsupportedOperationException("当前模型适配器尚未支持工具调用");
}
```

**为什么是 default 方法**：旧适配器（noop、未来其他 provider）无需改动即可继续编译；不支持时给出明确中文错误，由 Loop 在 `streamExecute` 入口检测并降级。

`ToolCallCollector` 是 SAM 接口：
```java
@FunctionalInterface
interface ToolCallCollector {
    void onToolCalls(List<AgentToolCall> toolCalls);  // 每轮决策一次
}
```

---

## 5. 待落地组件设计（A8–A14）

### 5.1 Task A8 — `McpToolPortAdapter`

**职责**：把现有 `KernelMcpOrchestrator.execute(McpToolExecutionRequest)` 包装成 `ToolPort`，让 LLM 把 MCP 工具当成普通 function 来调用。

**类签名**：
```java
class McpToolPortAdapter implements ToolPort {
    McpToolPortAdapter(KernelMcpOrchestrator orchestrator, ObjectMapper objectMapper);
    
    ToolInvocationResult invoke(String toolCallId, String toolId, Map<String,Object> arguments) {
        try {
            McpToolExecutionResult r = orchestrator.execute(
                new McpToolExecutionRequest(toolId, "", arguments));
            return ToolInvocationResult.ok(objectMapper.writeValueAsString(r.payload()));
        } catch (Exception ex) {
            return ToolInvocationResult.failed(ex.getMessage());
        }
    }
}
```

**关键决策**：
- `McpToolExecutionRequest` 的 `question` 字段传 `""`：原 MCP 路径的 question 是"原始用户问题"，但在 Agent 模式下 LLM 已经自主拆分出 arguments，question 字段失去意义；保留为空字符串避免 NPE。
- 序列化只取 `payload()`：避免把内部 metadata（traceId、duration 等）泄给 LLM 污染上下文。
- **MCP 工具如何注册到 ToolRegistry**？在 Phase A 不做"自动批量注册"——A13 的 starter 配置只装配 Adapter 本身；具体 MCP 工具的 `ToolDescriptor` 由后续 Phase B 或显式 Bean 提供。这保持 Phase A 的范围干净。

**测试**：`McpToolPortAdapterTests`（mock orchestrator，验证成功路径 + 异常映射）。

### 5.2 Task A9 — `KernelAgentLoop` 核心 ReAct 循环（无 SSE，先打底）

**职责**：实现纯函数式 ReAct 循环，便于单元测试覆盖各种边界情况。

**类签名（草案）**：
```java
class KernelAgentLoop {
    KernelAgentLoop(StreamingChatModelPort modelPort,
                    ToolRegistryPort toolRegistry,
                    KernelAgentLoopOptions options);
    
    AgentLoopResult execute(AgentLoopRequest request);
}
```

**状态机**：
```
ENTER → step=0, messages=[system, user]
   │
   ▼
┌─ LOOP ─────────────────────────────────────────────┐
│ step++                                             │
│                                                    │
│ if step > maxSteps:                                │
│     return AgentLoopResult(fallbackText, steps, true)
│                                                    │
│ call modelPort.streamChatWithTools(messages, ...)  │
│  ├─ collect toolCalls via ToolCallCollector        │
│  └─ collect content via StreamCallback (聚合)       │
│                                                    │
│ if toolCalls.isEmpty():                            │
│     return AgentLoopResult(content, steps, false)  │
│                                                    │
│ for each toolCall in toolCalls (并发 ≤ maxParallel):│
│     execute via ToolRegistry.find(toolId)          │
│       ├─ found → invoke with perToolTimeout        │
│       └─ not found → Observation.failed("工具未注册")│
│                                                    │
│ append AgentStep.thought(content, calls, obs)      │
│ append tool messages 到 messages                    │
│                                                    │
└────────────────────────────────────────────────────┘
```

**失败模式**：
| 场景 | 行为 |
|---|---|
| LLM 流式异常 | `StreamCallback.onError` → 抛 `AgentLoopException` 给 Inbound（最终走 SSE error 事件） |
| 单个 Tool 抛异常 | 兜底 `ToolInvocationResult.failed(message)`，Loop 继续 |
| 单个 Tool 超时 | `CompletableFuture.get(perToolTimeout)` 超时 → `failed("工具超时")` |
| ToolCallCollector 给的 toolCall 缺 id | 视为 LLM 输出错误，跳过该 call 并记 warn |
| 达到 maxSteps | `truncated=true`，fallback 文案 = `"任务步骤已达上限，请缩小问题范围或检查工具配置。"` |
| `streamChatWithTools` 抛 `UnsupportedOperationException` | Loop 在构造时检测；运行时仍可能抛——直接抛出由 Inbound 降级 RAG（A12 实现） |

**临时聚合内容**：本任务用一个内部 `BufferingStreamCallback` 把每轮 LLM 文本累积成 String，A10 再换成真正的 SSE 流式。

**测试**：`KernelAgentLoopTests`（5 个场景）：
1. 一步给最终回答
2. 一步工具 → 第二步给最终回答（验证 messages 拼接顺序）
3. maxSteps 截断 → `truncated=true`
4. 工具未注册 → observation `failed`，循环继续
5. 工具抛异常 → observation `failed`，循环继续

### 5.3 Task A10 — `KernelAgentLoop` 接入 SSE 流式

**新增方法**：
```java
StreamCancellationHandle streamExecute(AgentLoopRequest request, StreamCallback callback);
```

**SSE 事件映射**：
| Loop 内部事件 | SSE 事件 |
|---|---|
| 模型每轮 token（中间步骤） | `onThinking("...")`（前端可选展示，默认折叠） |
| 工具调用摘要 | `onThinking("[工具调用] toolId(arg_summary) → ok/err")` |
| 最终步骤模型 token | `onContent("...")`（真正展示给用户） |
| 完成 | `onComplete()` |
| 异常 | `onError(throwable)` |

**取消**：返回的 `StreamCancellationHandle` 触发 → 设置 `volatile cancelled` 标志 → 当前 Tool `CompletableFuture.cancel(true)` + 中断模型流。

**测试**：在 A9 测试之上新增 1 个 SSE 用例。

### 5.4 Task A11 — `OpenAiCompatibleModelAdapter` 支持 function-calling

> ⚠️ **plan 校正点**：plan 原写 `OpenAiCompatibleStreamingChatModelAdapter`，实际类名是 **`OpenAiCompatibleModelAdapter`**（同一个类实现多个 Port）。已在 worktree 的 plan 文件中修正。

**改造点**：
1. **请求序列化**：`request.getTools()` 非空时，写入 OpenAI 兼容 body：
   ```json
   "tools": [{"type":"function","function":{"name":"weather","description":"...","parameters":{...}}}],
   "tool_choice": "auto"
   ```
2. **SSE 解析**：识别 `delta.tool_calls` 增量，按 `index` 聚合：
   ```
   chunk1: tool_calls[0]={id:"c1", function:{name:"weather"}}
   chunk2: tool_calls[0]={function:{arguments:"{\"ci"}}
   chunk3: tool_calls[0]={function:{arguments:"ty\":\"SH\"}"}}
   ↓ aggregated
   AgentToolCall(id="c1", toolId="weather", arguments={"city":"SH"})
   ```
3. **回调路由**：
   - `delta.content` → `callback.onContent(chunk)`
   - `delta.reasoning_content` → `callback.onThinking(chunk)`（如 provider 支持）
   - `delta.tool_calls` → 累积到内部缓冲；流结束时 emit 给 `ToolCallCollector.onToolCalls(list)`

**测试**：用 MockWebServer 模拟 SSE 两条情况：
- 仅 tool_calls 流 → collector 收到一次、content 为空
- 仅 content 流 → collector 收到 `List.of()`、content 完整

### 5.5 Task A12 — `KernelChatInboundService` 按 chatMode 路由

**改造点**：
```java
class KernelChatInboundService {
    // 旧构造：兼容
    public KernelChatInboundService(KernelChatPipeline pipeline, StreamTaskPort streamTaskPort) {
        this(pipeline, streamTaskPort, Optional.empty(), KernelRagTraceRecorder.noop());
    }
    
    // 新构造
    public KernelChatInboundService(KernelChatPipeline pipeline,
                                    StreamTaskPort streamTaskPort,
                                    Optional<KernelAgentLoop> agentLoop,
                                    KernelRagTraceRecorder traceRecorder);
    
    public void streamChat(StreamChatCommand cmd, StreamCallback cb) {
        ChatMode mode = cmd.chatMode();
        if (mode == ChatMode.AGENT && agentLoop.isPresent()) {
            agentLoop.get().streamExecute(buildRequest(cmd), cb);
            return;
        }
        if (mode == ChatMode.AGENT) {
            // 注入缺失 → 降级 RAG，记 trace
            traceRecorder.markFallback(cmd.taskId(), "agent-loop-missing");
        }
        pipeline.execute(buildStreamChatContext(cmd, cb));  // 原路径
    }
}
```

**关键决策**：`Optional<KernelAgentLoop>` 而非必填，让 starter 在 `agent-mode-enabled=false` 时不装配 Loop Bean，整链路保持轻量。

**测试**：
- chatMode=AGENT + 注入 Loop → 调 `streamExecute`，不调 pipeline
- chatMode=AGENT + 未注入 → 调 pipeline（降级），不抛错
- 既有 4 个 RAG 测试不变（含 `shouldFallbackToGenericChatWhenRetrievalIsEmpty`）

### 5.6 Task A13 — Spring 自动装配

**新文件**：`SeahorseAgentKernelAgentAutoConfiguration`（独立于 chat AutoConfig，便于条件装配）

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
    @ConditionalOnBean({KernelMcpOrchestrator.class, ToolRegistryPort.class})
    public McpToolPortAdapter seahorseMcpToolPortAdapter(
            KernelMcpOrchestrator orchestrator,
            ObjectMapper objectMapper) {
        return new McpToolPortAdapter(orchestrator, objectMapper);
    }
}
```

修改 `SeahorseAgentKernelChatAutoConfiguration.seahorseChatInboundPort` 注入 `ObjectProvider<KernelAgentLoop>`。

`application.properties` 新增：
```properties
# 默认关闭 Agent 模式
seahorse-agent.chat.agent-mode-enabled=false
```

### 5.7 Task A14 — Web 入口透传 `chatMode`

**改造点**：`SeahorseChatController` 增加 `@RequestParam(required=false) String chatMode`，安全转换为枚举：
```java
ChatMode mode = parseChatMode(chatModeParam);  // 容错：null/非法 → RAG
StreamChatCommand cmd = new StreamChatCommand(..., mode);
```

**注意**：HTTP 层不做权限校验；权限随 Phase F 落地。

---

## 6. 兼容性矩阵

| 项目 | 旧值 | 新值 | 兼容性 |
|---|---|---|---|
| `StreamChatCommand` 构造方法 | 5 参 | 6 参为主 + 5 参兼容 | ✅ 源码兼容 |
| `ChatRequest` 字段 | 3 个 | 5 个 | ✅ Builder + 默认值 |
| `StreamingChatModelPort` | 1 抽象方法 | 1 抽象 + 1 default | ✅ 旧实现零修改 |
| `KernelChatInboundService` 构造 | 2 参 | 4 参 + 2 参兼容 | ✅ 源码兼容 |
| `KernelChatPipeline` | 不变 | 不变 | ✅ |
| `KernelMcpOrchestrator` | 不变 | 不变 | ✅ 仅被 Adapter 读 |
| SSE 事件类型 | `meta/message/thinking/finish/done/cancel/reject/title/error` | 不变 | ✅ 前端零改 |
| Config key | — | `seahorse-agent.chat.agent-mode-enabled=false` 默认关 | ✅ 默认行为不变 |
| `application.properties` | 不变 | 新增一行 | ✅ |
| 数据库 schema | 不变 | 不变 | ✅ |

---

## 7. 失败模式与降级

| # | 场景 | 检测点 | 行为 |
|---|---|---|---|
| F1 | `chatMode=agent` 但未启用 / 未注入 Loop | `KernelChatInboundService` | 降级 RAG，trace 标 `agent-fallback` |
| F2 | `StreamingChatModelPort` 抛 `UnsupportedOperationException` | `KernelAgentLoop.execute` | `onError` → SSE `error` 事件，**不**自动降级 RAG（避免静默切换让运维难以察觉）|
| F3 | Tool 未注册 | `ToolRegistryPort.find` | `Observation.failed("Tool 未注册")`，循环继续 |
| F4 | Tool 抛异常 | `McpToolPortAdapter` / 其他 Tool | `failed(ex.getMessage())`，循环继续 |
| F5 | Tool 超时 | `CompletableFuture.get(perToolTimeout)` | `failed("工具超时")`，循环继续 |
| F6 | LLM 返回非法 toolCall（缺 id / 缺 toolId） | `KernelAgentLoop` 内部 | warn 日志，跳过该 call |
| F7 | LLM 死循环（一直产 toolCalls） | `step > maxSteps` | `truncated=true`，fallback 文案 + `onComplete` |
| F8 | 用户取消任务 | `StreamCancellationHandle.cancel()` | 停止当前模型流 + 取消 in-flight tool futures + `onError(CancelledException)` |
| F9 | 工具 JSON Schema 与 LLM 输出不符 | `objectMapper.convertValue` | Adapter 层抛异常 → F4 路径兜底 |

---

## 8. 配置一览

| Key | 默认 | 含义 |
|---|---|---|
| `seahorse-agent.chat.agent-mode-enabled` | `false` | 关：不装配 AgentLoop。开：装配但仍需 `?chatMode=agent` 触发 |
| `seahorse-agent.chat.agent.max-steps` | `6` | 覆盖 `KernelAgentLoopOptions.maxSteps` |
| `seahorse-agent.chat.agent.per-tool-timeout` | `30s` | 覆盖 `perToolTimeout`（Duration 格式） |
| `seahorse-agent.chat.agent.max-parallel-tools` | `4` | 覆盖 `maxParallelTools` |

A13 会通过 `@ConfigurationProperties` 把 3 个细分配置绑定到 `KernelAgentLoopOptions` Bean。

---

## 9. 待 review 的关键设计抉择

以下 6 个点是我在写设计文档过程中**主观做的选择**，强烈建议你过目后给反馈。每个点都标了"如果你不同意时的备选方案"。

### Q1：MCP 工具的 ToolDescriptor 从哪来？

**当前抉择**：A8 只装配 `McpToolPortAdapter` 这个执行器；具体 MCP 工具的 ToolDescriptor（toolId / jsonSchema）**不自动**从 `McpToolRegistryPort` 批量导入到 `ToolRegistryPort`。

- **原因**：MCP 注册中心里的工具语义未必都希望暴露给 LLM；强制全量暴露会让 toolList 太长，影响 LLM 决策质量。
- **备选**：增加一个配置开关 `seahorse-agent.chat.agent.tools.mcp.auto-register=true`，开启后启动时全量同步。

### Q2：当 Adapter 不支持 function-calling 时，是降级 RAG 还是直接报错？

**当前抉择**：**报错**（F2）。LLM 适配器声称支持但实际抛 `UnsupportedOperationException` 时，让用户看到错误，而不是静默退回 RAG。

- **原因**：静默降级会让"为什么我开了 Agent 还是不工作"难以排查。
- **备选**：增加 `seahorse-agent.chat.agent.fallback-to-rag-on-error=true`，配置驱动降级。

### Q3：工具并发执行是按 LLM 一轮的多个 toolCalls 并发，还是串行？

**当前抉择**：**并发**（`maxParallelTools=4`）。一轮 LLM 输出多个 toolCalls（OpenAI 协议允许）时同时跑。

- **原因**：性能；典型 ReAct 实践如此。
- **风险**：工具有副作用（写库等）时并发可能冲突。
- **备选**：默认 1（串行），用户显式调高才并发。

### Q4：`StreamCallback.onThinking` 是否前端默认展示？

**当前抉择**：不强制前端改造；`onThinking` 默认走前端原有"折叠思考过程"分支。

- **影响**：用户看不到工具调用过程，可能困惑"为什么慢"。
- **备选**：前端在 Agent 模式开个"展开思考链"开关；这属于 UI 层，不阻塞 Phase A。

### Q5：`KernelAgentLoop` 是否复用 `KernelChatPipeline` 的 memory / queryOptimizer / rewriteQuery 等前处理？

**当前抉择**：**不复用**。Phase A 的 `AgentLoopRequest` 直接接 `question` + `history` + `memoryContext`，由 `KernelChatInboundService` 在 A12 构造时显式拼。

- **原因**：保持 AgentLoop 单一职责（只负责工具循环），前处理由调用方决定。
- **后续**：Phase E 把 memory 做成 Tool 后，AgentLoop 在循环中通过 `memory_read` Tool 主动拉记忆，反而比 RAG 模式的"一次性 activate"更灵活。
- **备选**：在 A12 也跑一遍 `KernelChatPreparationSupport.optimizeQuery/rewriteQuery`，再喂给 AgentLoop——这样 Agent 模式也享受查询改写。**我倾向后者**，但想听你意见。

### Q6：Trace 节点粒度？

**当前抉择**：Loop 每轮记 1 个 trace node（`agent-step-N`），每次 tool 执行记 1 个子节点（`agent-tool-N-toolId`）。

- **影响**：trace 节点数量 = `2 * steps`，6 步循环最多 12 个节点。
- **备选**：每轮只记 1 个聚合节点，tool 详情塞 metadata；trace 表更精简但调试更难。

---

## 10. 推进建议

我建议你按以下顺序回我：

1. **A1–A6 的 6 个 commit 是否接受？**（已落地的契约设计是否需要调整）
2. **A7 草稿（StreamingChatModelPort 扩展）是否接受？**（接受则我跑测试 + commit；否则回滚）
3. **Q1–Q6 六个设计抉择各自的选择**（特别是 Q5，影响 A12 的代码量）
4. **后续是否继续 inline 执行 A8–A14？还是先改文档再开干？**

如果你想看具体某个待落地 Task 的更细伪代码（比如 A9 完整的 messages 拼接流程、A11 的 OpenAI body 序列化），告诉我具体哪个，我单独写。

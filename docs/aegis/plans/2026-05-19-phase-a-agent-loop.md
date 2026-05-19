# Phase A 实施计划：补齐 LLM-Driven Agent 编排层

> 该计划为 Seahorse Agent 由 RAG 系统进化到 Agent 平台的 **关键基础**。
> 完成后将解锁 Phase B（Agentic Search）、Phase D（输出治理）、Phase E（记忆闭环）的实现路径。

## Goal

在不破坏现有 RAG 主链路的前提下，在 kernel 中引入 **LLM-Driven 工具调用编排器** `KernelAgentLoop`，实现 ReAct 风格的多步推理与工具调用（Thought → Action → Observation → ...）。聊天入口通过新的 `chatMode` 参数选择 `rag`（默认，保持原行为）或 `agent`（启用 AgentLoop）。

## Architecture

```
        Client (chatMode=rag|agent)
              │
   ChatInboundPort.streamChat
              │
              ▼
   KernelChatInboundService
              │
        ┌─────┴─────┐
   chatMode==rag  chatMode==agent
        │             │
        ▼             ▼
KernelChatPipeline  KernelAgentLoop  ←─ new
  (RAG 主链路)        │
                      ├── ToolPort 注册中心 (ToolRegistry)
                      │     ├─ search_knowledge_base (B 阶段再串)
                      │     ├─ call_mcp_tool
                      │     ├─ memory_read / memory_write (E 阶段再串)
                      │     └─ ... extension
                      ├── StreamingChatModelPort.streamChatWithTools()
                      └── ReAct loop:
                            while !done && step<maxSteps:
                              1. 调 LLM with tools
                              2. 若 tool_calls 非空 → 执行 → 拼回消息
                              3. 否 → 直接流式输出最终回答
```

## Tech Stack

- Java 17 + Spring Boot 3.5.7（保持现状）
- 复用 `StreamingChatModelPort`，扩展支持 OpenAI 兼容 `tools` / `tool_choice` 参数
- 复用 `KernelMcpOrchestrator` 作为 `call_mcp_tool` Tool 的底层执行器
- 复用 `StreamCallback` / `StreamTaskPort` / `KernelRagTraceRecorder`，**不引入新的 SSE 协议**

## Baseline / Authority Refs

- `@C:/user-data/code/ai/seahorse-agent/docs/agent-vs-rag-capability-baseline.md` — Phase A 路线图与判定口径
- `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/chat/KernelChatPipeline.java` — 已存在的 RAG 主链路（保持不动）
- `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/mcp/KernelMcpOrchestrator.java` — MCP 工具执行器（被 Tool 适配复用）
- `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-adapter-ai-openai-compatible` — function-calling 协议适配点

## Compatibility Boundary

**必须保持稳定**：

- `ChatInboundPort.streamChat` 默认行为（`chatMode` 缺省 → `rag`，链路 100% 不变）
- `KernelChatPipeline` / `KernelChat{Preparation,Response}Support` 源码与字节码兼容
- `StreamCallback` SSE 事件类型 `meta` / `message` / `thinking` / `finish` / `done` / `cancel` / `reject` / `title` / `error`（**不引入新事件类型**，工具调用过程作为 `thinking` 流出）
- `StreamingChatModelPort` 旧方法签名保留（新增 default 方法不破坏既有实现）
- 前端不强制改动；前端可在后续 PR 增加 `chatMode` 入参

**允许变化**：
- 新增包 `kernel/application/agent/`、`kernel/feature/agent/`、`ports/outbound/agent/`
- `StreamChatCommand` 增加 `chatMode` 可选字段（带默认值，向后兼容）

## Verification

每个任务尾部给出 `mvn` 命令，最终 Phase A 通过后整体：

```bash
mvn -pl seahorse-agent-tests -am -DfailIfNoTests=false test
```

期望：8/8 已有 + 14 个新增测试全绿，且 `KernelChatInboundServiceTests.shouldFallbackToGenericChatWhenRetrievalIsEmpty`（RAG 默认模式）仍通过。

## Risks / Rollback

| 风险 | 缓解 |
|---|---|
| OpenAI 兼容 tool_calls 协议各家实现差异 | 第一版只对接 `function` type，提供 `OpenAiCompatibleToolCallingAdapter` 单点切换；其它 provider 退化为单步对话 |
| 死循环 | 强制 `maxSteps`（默认 6）+ `KernelAgentLoopOptions` 配置 |
| 工具阻塞 SSE | Tool 执行包 `CompletableFuture` + 总超时 `30s`，超时返回结构化错误观察值 |
| 一次性大改 | 全程开关 `seahorse-agent.chat.agent-mode-enabled=false` 默认关闭，灰度 |

## Retirement

无需替换/删除既有路径。Phase A 完成后 `KernelChatPipeline` 与 `KernelAgentLoop` 并存；后续若 `agent` 完全替代 `rag`，再走单独的下线计划。

---

## File Map

**新建**：
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/AgentStep.java`
- `.../kernel/domain/agent/AgentToolCall.java`
- `.../kernel/domain/agent/AgentObservation.java`
- `.../kernel/domain/agent/AgentLoopRequest.java`
- `.../kernel/domain/agent/AgentLoopResult.java`
- `.../ports/outbound/agent/ToolPort.java`
- `.../ports/outbound/agent/ToolDescriptor.java`
- `.../ports/outbound/agent/ToolInvocationResult.java`
- `.../ports/outbound/agent/ToolRegistryPort.java`
- `.../kernel/application/agent/InMemoryToolRegistry.java`
- `.../kernel/application/agent/KernelAgentLoop.java`
- `.../kernel/application/agent/KernelAgentLoopOptions.java`
- `.../kernel/application/agent/McpToolPortAdapter.java`（把现有 `KernelMcpOrchestrator` 包成 `ToolPort`）
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/chat/ChatMode.java`
- `seahorse-agent-adapter-ai-openai-compatible/.../OpenAiCompatibleStreamingChatModelAdapter.java`（**扩展**已有适配器，新增 `streamChatWithTools`）
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentKernelAgentAutoConfiguration.java`
- 单元测试：
  - `seahorse-agent-tests/src/test/java/.../kernel/application/agent/KernelAgentLoopTests.java`
  - `seahorse-agent-tests/src/test/java/.../kernel/application/agent/InMemoryToolRegistryTests.java`
  - `seahorse-agent-tests/src/test/java/.../kernel/application/agent/McpToolPortAdapterTests.java`
  - `seahorse-agent-tests/src/test/java/.../kernel/application/chat/KernelChatInboundServiceAgentModeTests.java`

**修改**：
- `seahorse-agent-kernel/.../ports/inbound/chat/StreamChatCommand.java` — 增加 `ChatMode chatMode` 字段（默认 `RAG`）
- `seahorse-agent-kernel/.../kernel/application/chat/KernelChatInboundService.java` — 路由到 RAG 或 Agent
- `seahorse-agent-kernel/.../ports/outbound/model/StreamingChatModelPort.java` — 新增 `default streamChatWithTools(...)`（默认抛 `UnsupportedOperationException`，noop 同步返回空）
- `seahorse-agent-kernel/.../kernel/domain/chat/ChatRequest.java` — 增加 `tools` / `toolChoice` 可选字段
- `seahorse-agent-adapter-web/.../SeahorseChatController.java` — 解析查询参数 `chatMode`
- `seahorse-agent-spring-boot-starter/src/main/resources/application.properties` — 增加默认 `seahorse-agent.chat.agent-mode-enabled=false`

---

## Tasks

### Task A1 — 引入 ChatMode 枚举与 StreamChatCommand 兼容字段

**Files**:
- create: `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/chat/ChatMode.java`
- modify: `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/chat/StreamChatCommand.java`
- create test: `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/domain/chat/ChatModeTests.java`

**Why**: 为后续路由提供类型安全的开关；保持旧调用方零成本兼容（旧构造方法不变，新构造方法接受 `ChatMode`）。

**Compatibility**: `new StreamChatCommand(question, conversationId, taskId, userId, deepThinking)` 仍合法，等价于 `ChatMode.RAG`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=ChatModeTests test`

**Steps** (TDD):
1. **Write test** — `ChatModeTests`：断言枚举值 `RAG`、`AGENT`；`StreamChatCommand` 旧 5 参构造 → `chatMode()==RAG`；新 6 参构造 → 传入值生效；`null` 入参 → `RAG`。
2. **Verify RED** — `mvn -pl seahorse-agent-tests -am -Dtest=ChatModeTests test` 应失败（类不存在）。
3. **Minimal code** — 新建 `ChatMode { RAG, AGENT }`；`StreamChatCommand` 增加 `ChatMode chatMode` 字段；为兼容旧 5 参构造保留无 `chatMode` 重载，内部转调新构造并填 `RAG`；`null` → `RAG`。
4. **Verify GREEN** — 同上命令绿。
5. **Commit** — `feat(chat): 引入 ChatMode 枚举与 StreamChatCommand 兼容字段`

---

### Task A2 — 新增 Agent 领域模型（AgentStep / AgentToolCall / AgentObservation）

**Files**:
- create: `kernel/domain/agent/AgentStep.java`
- create: `kernel/domain/agent/AgentToolCall.java`
- create: `kernel/domain/agent/AgentObservation.java`
- create: `kernel/domain/agent/AgentLoopRequest.java`
- create: `kernel/domain/agent/AgentLoopResult.java`
- create test: `seahorse-agent-tests/.../kernel/domain/agent/AgentDomainTests.java`

**Why**: Loop 各步骤需要不可变值对象作为 trace 节点的数据载体，便于日后回放/审计。

**Compatibility**: 全新包，无下游消费者。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=AgentDomainTests test`

**Steps** (TDD):
1. **Write test** — `AgentDomainTests`：
   - `AgentStep.thought(text, toolCalls)` / `AgentStep.finalAnswer(text)` 工厂；`isFinal()` 判定正确。
   - `AgentToolCall(id, toolId, arguments)` 必填校验；`arguments` 为 `Map<String,Object>` 不可变拷贝。
   - `AgentObservation(toolCallId, success, content, error)` 配对工厂 `ok(...)` / `failed(...)`。
   - `AgentLoopRequest` builder 必填：`question`、`history`、`tools`、`samplingOptions`；可选：`maxSteps`（默认 6）、`memoryContext`。
   - `AgentLoopResult` 包含 `finalAnswer`、`List<AgentStep> steps`、`boolean truncated`。
2. **Verify RED** — `mvn -pl seahorse-agent-tests -am -Dtest=AgentDomainTests test` 失败。
3. **Minimal code** — 写 5 个 record/类。`AgentToolCall` 构造里用 `Map.copyOf(arguments)`。
4. **Verify GREEN** — 命令绿。
5. **Commit** — `feat(agent): 新增 Agent 领域模型(AgentStep/ToolCall/Observation/LoopRequest/Result)`

---

### Task A3 — 引入 ToolPort / ToolDescriptor / ToolInvocationResult 出站契约

**Files**:
- create: `ports/outbound/agent/ToolPort.java`
- create: `ports/outbound/agent/ToolDescriptor.java`
- create: `ports/outbound/agent/ToolInvocationResult.java`
- create: `ports/outbound/agent/ToolRegistryPort.java`
- create test: `seahorse-agent-tests/.../ports/outbound/agent/ToolPortContractTests.java`

**Why**: AgentLoop 仅依赖端口；具体工具实现作为适配器（MCP / 检索 / 记忆）注册。

**Compatibility**: 新包，零影响。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=ToolPortContractTests test`

**Steps** (TDD):
1. **Write test** — `ToolPortContractTests`：
   - `ToolDescriptor(toolId, name, description, jsonSchema)` 字段非空校验。
   - `ToolInvocationResult.ok(content)` / `ToolInvocationResult.failed(message)` 状态正确；`content` 必须是字符串（JSON 序列化由 Adapter 完成）。
   - `ToolPort.invoke(toolCallId, toolId, arguments)` 默认实现：`ToolPort.notFound(toolId)` 返回 `failed` 结果。
   - `ToolRegistryPort.empty().listTools()` 空；`ToolRegistryPort.empty().find("x").isEmpty()` 为 true。
2. **Verify RED** — 失败。
3. **Minimal code** — 4 个接口/记录类，含 `static` 工厂。`ToolPort` 用 `record` 不行（要带行为），改为 `interface` + 静态工具方法。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): 新增 ToolPort/ToolDescriptor/ToolRegistryPort 出站契约`

---

### Task A4 — InMemoryToolRegistry：本地注册中心 + 注册时校验

**Files**:
- create: `kernel/application/agent/InMemoryToolRegistry.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/InMemoryToolRegistryTests.java`

**Why**: 第一版以单进程内存注册中心承载 Tool，无需引入分布式存储。

**Compatibility**: 新建。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=InMemoryToolRegistryTests test`

**Steps** (TDD):
1. **Write test** — 注册一个 Tool，`find(toolId)` 返回对应 Port；`listTools()` 返回 descriptor 列表；重复注册同 toolId 抛 `IllegalStateException`；`null` 参数抛 NPE。
2. **Verify RED** — 失败。
3. **Minimal code** — `ConcurrentHashMap<String, Registration>`；`Registration(descriptor, port)`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): 实现 InMemoryToolRegistry`

---

### Task A5 — KernelAgentLoopOptions：循环参数与默认值

**Files**:
- create: `kernel/application/agent/KernelAgentLoopOptions.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/KernelAgentLoopOptionsTests.java`

**Why**: 隔离循环上限、单工具超时、并发度等配置。

**Compatibility**: 新建。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=KernelAgentLoopOptionsTests test`

**Steps** (TDD):
1. **Write test** — `KernelAgentLoopOptions.defaults()`：`maxSteps==6`、`perToolTimeout==Duration.ofSeconds(30)`、`maxParallelTools==4`；builder 可覆盖；非法值（≤0 / null）抛 `IllegalArgumentException`。
2. **Verify RED** — 失败。
3. **Minimal code** — record + 校验。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): 新增 KernelAgentLoopOptions`

---

### Task A6 — 扩展 ChatRequest：tools / toolChoice 可选字段

**Files**:
- modify: `kernel/domain/chat/ChatRequest.java`
- create test: `seahorse-agent-tests/.../kernel/domain/chat/ChatRequestToolsTests.java`

**Why**: 让 `ChatRequest` 能承载 function-calling 协议参数，不动 builder 必填项。

**Compatibility**: 旧构造路径所有字段保持；`tools` 默认空集合；`toolChoice` 默认 `"auto"`，无 tools 时不序列化到下游 provider。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=ChatRequestToolsTests test`

**Steps** (TDD):
1. **Write test** — `ChatRequest.builder().messages(...).build()` 旧用法 `tools()` 返回空 List、`toolChoice()=="auto"`；`builder().tools(List.of(descriptor)).toolChoice("required").build()` 字段生效；`tools` 入参为 null 时归一化为空 List。
2. **Verify RED** — 失败。
3. **Minimal code** — 增加两字段；builder 增加 setter；构造时 `Objects.requireNonNullElse`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(chat): ChatRequest 增加 tools/toolChoice 可选字段`

---

### Task A7 — StreamingChatModelPort 新增 streamChatWithTools 默认方法

**Files**:
- modify: `ports/outbound/model/StreamingChatModelPort.java`
- create test: `seahorse-agent-tests/.../ports/outbound/model/StreamingChatModelPortToolsTests.java`

**Why**: 让 AgentLoop 通过同一端口拿"含 tool_calls 的回答"，老 provider 不实现也不会编译错。

**Compatibility**: `default` 方法，无破坏；默认实现：抛 `UnsupportedOperationException("当前模型适配器尚未支持工具调用")`。`StreamingChatModelPort.noop()` 重写为同步空回调。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=StreamingChatModelPortToolsTests test`

**Steps** (TDD):
1. **Write test** — 自定义实现仅覆盖 `streamChat`，调用 `streamChatWithTools` 应抛 `UnsupportedOperationException`，异常信息含中文提示；`StreamingChatModelPort.noop().streamChatWithTools(...)` 不抛，回调 `onComplete`。
2. **Verify RED** — 失败。
3. **Minimal code** — 新增 `default StreamCancellationHandle streamChatWithTools(ChatRequest, StreamCallback, ToolCallCollector)`（`ToolCallCollector` 为接收 `List<AgentToolCall>` 的简单回调函数式接口，置同包内）。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(model): StreamingChatModelPort 默认方法 streamChatWithTools`

> 备注：`ToolCallCollector` 同步建在 `ports/outbound/model/ToolCallCollector.java`，作为 SAM 接口。

---

### Task A8 — 把 KernelMcpOrchestrator 包装成 ToolPort（McpToolPortAdapter）

**Files**:
- create: `kernel/application/agent/McpToolPortAdapter.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/McpToolPortAdapterTests.java`

**Why**: 复用既有 MCP 客户端做第一个真正可调用的 Tool，验证端到端。

**Compatibility**: `KernelMcpOrchestrator` 源码完全不动；Adapter 只调用其 `execute(McpToolExecutionRequest)` 单工具方法。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=McpToolPortAdapterTests test`

**Steps** (TDD):
1. **Write test** — Mock `KernelMcpOrchestrator.execute(...)` 返回成功结果；调 `McpToolPortAdapter.invoke("call-1","weather", Map.of("city","Shanghai"))`：
   - 返回 `ToolInvocationResult.ok(content)`，`content` 为 orchestrator 返回值的 JSON 字符串。
   - Mock 抛异常时 → `ToolInvocationResult.failed(...)`，错误消息保留异常 message。
2. **Verify RED** — 失败。
3. **Minimal code** — 构造注入 `KernelMcpOrchestrator` + `ObjectMapper`；`invoke` 内部 `new McpToolExecutionRequest(toolId, "", arguments)`；序列化 `result.payload()` 为字符串。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): McpToolPortAdapter 将 MCP 工具暴露为 ToolPort`

---

### Task A9 — KernelAgentLoop 核心 ReAct 循环（无流式输出，先打底）

**Files**:
- create: `kernel/application/agent/KernelAgentLoop.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/KernelAgentLoopTests.java`

**Why**: 先实现可单元测试的纯函数式循环，不引入 SSE 复杂性。

**Compatibility**: 新建。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=KernelAgentLoopTests test`

**Steps** (TDD):
1. **Write test** — 用 Fake `StreamingChatModelPort`（驱动 ToolCallCollector）+ Fake `ToolRegistryPort`：
   - **场景 1**：LLM 第一步直接给最终回答（无 tool_calls）→ `result.steps().size()==1`，`finalAnswer` 正确。
   - **场景 2**：LLM 第一步 tool_call `weather`，第二步给最终回答 → `steps().size()==2`，第二步 observations 含工具结果。
   - **场景 3**：LLM 一直产 tool_calls 触达 `maxSteps=3` → `result.truncated()==true`，`finalAnswer` 为 fallback 文案。
   - **场景 4**：找不到的 tool → 该步骤 observation `success==false`，循环继续。
   - **场景 5**：单工具执行抛异常 → observation 失败，循环继续。
2. **Verify RED** — 失败。
3. **Minimal code** — 内核：`execute(AgentLoopRequest req): AgentLoopResult`；循环维护 messages（包含 tool messages）；超步截断时返回最后一次模型可读文本或 `"任务步骤已达上限，请缩小问题范围或检查工具配置。"`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): 实现 KernelAgentLoop ReAct 核心循环`

---

### Task A10 — KernelAgentLoop 接入 SSE 流式输出

**Files**:
- modify: `kernel/application/agent/KernelAgentLoop.java`（新增 `streamExecute`）
- modify test: `seahorse-agent-tests/.../kernel/application/agent/KernelAgentLoopTests.java`（新增 `shouldStreamFinalAnswerViaCallback`）

**Why**: 与现有 SSE 协议对齐：工具调用过程以 `thinking` 事件流出，最终回答以 `message` 事件流出，结束发 `done`。

**Compatibility**: 不引入新事件类型；通过 `StreamCallback.onThinking(String)`（如不存在则降级为 `onContent`）输出工具调用摘要。先检查现有接口能力。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=KernelAgentLoopTests test`

**Steps** (TDD):
1. **Write test** — `shouldStreamFinalAnswerViaCallback`：当 LLM 第二步走最终回答时，`StreamCallback` 至少收到一次 `onContent(non-empty)`、一次 `onComplete()`；工具调用阶段 `onContent` 不应携带 raw JSON（断言不包含 `"arguments":`）。
2. **Verify RED** — 失败。
3. **Minimal code** — 新增 `streamExecute(AgentLoopRequest req, StreamCallback callback): StreamCancellationHandle`。中间步骤可通过 `onThinking` 推送 `[工具调用] toolId(arguments_summary) -> ok|err`；若 `StreamCallback` 无 `onThinking`，则跳过中间提示，仅推最终回答。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): KernelAgentLoop 接入 SSE 流式输出`

---

### Task A11 — OpenAiCompatibleStreamingChatModelAdapter 支持 streamChatWithTools

**Files**:
- modify: `seahorse-agent-adapter-ai-openai-compatible/src/main/java/.../OpenAiCompatibleStreamingChatModelAdapter.java`
- create test: `seahorse-agent-tests/.../adapters/ai/openai/OpenAiCompatibleStreamingChatToolsTests.java`

**Why**: 让 `KernelAgentLoop` 在真实模型上跑得通；使用 MockWebServer 验证 wire format。

**Compatibility**: 仅扩展，不改既有 `streamChat`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=OpenAiCompatibleStreamingChatToolsTests test`

**Steps** (TDD):
1. **Write test** — MockWebServer 模拟 OpenAI 兼容 `/v1/chat/completions` 的 SSE：
   - 第一次返回 `delta.tool_calls`，断言 `ToolCallCollector` 收到 `AgentToolCall(toolId="weather", arguments={"city":"Shanghai"})`，`StreamCallback.onContent` 不收 tool_call JSON。
   - 第二次返回 `delta.content`，断言 `onContent` 收到拼接文本。
   - 请求 body 包含 `tools` 数组（每个 element `type=="function"`），不含 `tools` 时不携带该字段。
2. **Verify RED** — 失败。
3. **Minimal code** — 在 builder 序列化里：当 `request.tools()` 非空 → 写入 `tools` + `tool_choice`。在 SSE 解析里识别 `tool_calls` 增量，按 `index` 聚合 id/name/arguments，行结束时 emit 给 `ToolCallCollector`；其余 chunk 仍走 `onContent`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(model-openai): 适配器支持 OpenAI 兼容 function-calling`

---

### Task A12 — KernelChatInboundService 按 chatMode 路由

**Files**:
- modify: `kernel/application/chat/KernelChatInboundService.java`
- modify: `seahorse-agent-tests/.../kernel/application/chat/KernelChatInboundServiceTests.java`
- create test: `seahorse-agent-tests/.../kernel/application/chat/KernelChatInboundServiceAgentModeTests.java`

**Why**: 把 Agent 入口接到外部，但 RAG 默认路径完全不变。

**Compatibility**: 旧构造保留，新增构造接受 `Optional<KernelAgentLoop>`（缺失时 `chatMode==AGENT` 走 fallback：复用 RAG，并在 trace 标记 `agent-fallback`）。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=KernelChatInboundServiceTests,KernelChatInboundServiceAgentModeTests test`

**Steps** (TDD):
1. **Write test** — 新文件 `KernelChatInboundServiceAgentModeTests`：
   - `chatMode==AGENT` + 注入 `KernelAgentLoop` → 仅调用 `agentLoop.streamExecute(...)`，不调用 `pipeline.execute(...)`。
   - `chatMode==AGENT` + 未注入 `KernelAgentLoop` → 调用 `pipeline.execute(...)`（fallback），`StreamCallback.onContent` 不含异常堆栈。
   - 原有 `shouldFallbackToGenericChatWhenRetrievalIsEmpty` 等 4 个 RAG 用例**保持绿色**。
2. **Verify RED** — 失败。
3. **Minimal code** — `KernelChatInboundService` 新增构造接受 `Optional<KernelAgentLoop>`；旧两参构造内部转调，并传 `Optional.empty()`；`streamChat` 内根据 `command.chatMode()` 路由。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(chat): KernelChatInboundService 按 chatMode 路由到 AgentLoop`

---

### Task A13 — Spring Boot 自动装配 + 配置开关

**Files**:
- create: `seahorse-agent-spring-boot-starter/src/main/java/.../SeahorseAgentKernelAgentAutoConfiguration.java`
- modify: `seahorse-agent-spring-boot-starter/src/main/java/.../SeahorseAgentKernelChatAutoConfiguration.java`（注入 `ObjectProvider<KernelAgentLoop>`）
- modify: `seahorse-agent-spring-boot-starter/src/main/resources/application.properties`
- create test: `seahorse-agent-tests/.../adapters/spring/SeahorseAgentKernelAgentAutoConfigurationTests.java`

**Why**: 默认关闭，开启后自动暴露 ToolRegistry + AgentLoop Bean。

**Compatibility**: `seahorse-agent.chat.agent-mode-enabled=false` 时新配置类完全不生效，整链路与今日一致。

**Verification**:
```bash
mvn -pl seahorse-agent-tests -am -Dtest=SeahorseAgentKernelAgentAutoConfigurationTests test
```

**Steps** (TDD):
1. **Write test** — `ApplicationContextRunner`：
   - 默认（无属性）→ 无 `KernelAgentLoop` Bean。
   - `seahorse-agent.chat.agent-mode-enabled=true` + 提供 `StreamingChatModelPort` → 有 `KernelAgentLoop`、`ToolRegistryPort` Bean；若同时存在 `KernelMcpOrchestrator` Bean → 自动注册 `McpToolPortAdapter`。
2. **Verify RED** — 失败。
3. **Minimal code** — 用 `@ConditionalOnProperty(name="seahorse-agent.chat.agent-mode-enabled", havingValue="true")` 装配 `KernelAgentLoop` + `InMemoryToolRegistry`；`@ConditionalOnBean(KernelMcpOrchestrator.class)` 注册 `McpToolPortAdapter`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(starter): 新增 Agent 模式自动装配与配置开关`

---

### Task A14 — Web 入口透传 chatMode 查询参数

**Files**:
- modify: `seahorse-agent-adapter-web/src/main/java/.../SeahorseChatController.java`
- modify test: `seahorse-agent-tests/.../adapters/web/SeahorseChatControllerTests.java`（如不存在则 create）

**Why**: 让前端通过 `GET /rag/v3/chat?chatMode=agent` 切到 Agent 模式。

**Compatibility**: `chatMode` 为可选参数；缺省 / 非法值 / 空字符串 → `RAG`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=SeahorseChatControllerTests test`

**Steps** (TDD):
1. **Write test** — 用 MockMvc：
   - 不带 `chatMode` → 下游 `streamChat` 收到 `ChatMode.RAG`。
   - `?chatMode=agent` → `ChatMode.AGENT`。
   - `?chatMode=foo` → `ChatMode.RAG`（容错），不返回 4xx。
2. **Verify RED** — 失败。
3. **Minimal code** — 解析 `@RequestParam(required=false) String chatMode`，安全转换为枚举。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(web): /rag/v3/chat 支持 chatMode 查询参数`

---

## Self-Review

- **Spec coverage** — 矩阵 5 项（LLM-Driven 编排 / Tool Loop / Tool 端口 / OpenAI function-calling / chatMode 路由）全部映射到 Task A1–A14。
- **Placeholder scan** — 无 TBD/TODO。`ToolCallCollector` SAM 在 A7 步骤注释中明确位置。
- **Type consistency** — `AgentToolCall`、`ToolDescriptor`、`ToolInvocationResult` 字段类型在多个 Task 中保持一致；`ChatMode` 枚举名一处定义、全链路引用。
- **Compatibility** — 旧 `StreamChatCommand` 5 参构造、`StreamingChatModelPort` 既有签名、`KernelChatPipeline` / `KernelChat{Preparation,Response}Support` 完全保留；新 Bean 仅在 `agent-mode-enabled=true` 时装配。
- **Verification** — 每个 Task 均有 `mvn -pl seahorse-agent-tests -am -Dtest=... test` 验证命令；A11 用 MockWebServer 覆盖 wire format。
- **Dual-track** — 旧 `KernelMcpOrchestrator` 路径保持（仍服务 RAG），同时被 `McpToolPortAdapter` 适配进 Agent，后续如要把 MCP 完全收归 Agent，再开 retirement 计划。
- **Risks** — 已列；配置默认关闭兜底。

---

## Retirement Track

- **None for this phase.** Phase A 是纯新增能力；`KernelChatPipeline` 与 RAG 主链路完整保留。
- 待 Phase B 把检索做成 Tool 后，再决定是否将 RAG 模式标记为"快路径"或合并入 Agent 模式。

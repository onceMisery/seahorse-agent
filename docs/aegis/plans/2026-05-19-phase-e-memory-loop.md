# Phase E 实施计划：记忆闭环（Memory-as-Tools）

> 依赖 Phase A 已完成。把现有的"在 Pipeline 起点一次性 activate"模型，升级为
> **由 LLM 在运行时通过 Tool 主动 read / write / forget 记忆**，闭合记忆的"被消费 → 被沉淀 → 被晋升"链路。

## Goal

把 `MemoryEnginePort` 暴露成 3 个 ToolPort：
- `memory_read(scope, query, topK)`
- `memory_write(layer, content, ttl?, importance?)`
- `memory_forget(memoryId)`（仅短期/长期）

并在 Agent 模式收尾阶段（`KernelAgentLoop` 完成最后一步后）自动 enqueue 一次 **MemoryReflectionTask**，由 `MemoryEngine` 的写入侧自决是否生成长期记忆 / 触发冲突检测。

## Architecture

```
KernelAgentLoop (Phase A)
        │
        ├─ invoke memory_read  ──► MemoryReadTool   ──► MemoryEnginePort.retrieveMemories
        ├─ invoke memory_write ──► MemoryWriteTool  ──► MemoryEnginePort.writeMemory
        └─ invoke memory_forget──► MemoryForgetTool ──► ShortTermMemoryMaintenancePort + LongTermMemoryPort
                  │
                  └─(after final answer)─► MemoryReflectionTask
                                              ├─ 提取高价值候选 (RuleBasedMemoryCandidateExtractor)
                                              ├─ 冲突检测 (MemoryConflictLogRepositoryPort)
                                              └─ enqueue 通过 MessageQueuePort
```

## Tech Stack

- 复用：`MemoryEnginePort` / `MemoryLoadRequest` / `MemoryWriteRequest` / `RuleBasedMemoryCandidateExtractor` / `MemoryConflictLogRepositoryPort`
- 异步：`MessageQueuePort`（已有 direct/pulsar 适配）

## Baseline / Authority Refs

- `@C:/user-data/code/ai/seahorse-agent/docs/agent-vs-rag-capability-baseline.md` — Phase E
- `@C:/user-data/code/ai/seahorse-agent/docs/Agent_Memory_系统改进设计方案.md`
- `@C:/user-data/code/ai/seahorse-agent/docs/memory-system-improvement-plan.md`
- `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`

## Compatibility Boundary

**必须稳定**：
- `MemoryEnginePort` 既有方法签名
- `KernelChatPipeline` 中的 `activateMemory` 阶段（RAG 模式继续读 4 层记忆）
- `MemoryGovernanceFeature` 离线治理任务

**允许变化**：
- 新增 `kernel/application/agent/tools/memory/*`
- 新增 `kernel/application/memory/MemoryReflectionTask.java`
- `MemoryEnginePort` 允许新增 `default` 方法（不破坏既有实现）

## Verification

```bash
mvn -pl seahorse-agent-tests -am -Dtest='*MemoryTool*,*MemoryReflection*' test
```

## Risks / Rollback

| 风险 | 缓解 |
|---|---|
| LLM 误调 `memory_forget` 删掉用户重要记忆 | Tool 内强制 `confirm=true` 参数；删除前先 read 出原内容做校验；保留软删除标记 |
| Reflection 任务挤占聊天链路 | 异步走 MQ；同步链路 fire-and-forget |
| 写入风暴 | `memory_write` Tool 内单次调用每层最多 5 条；超额返回 `failed("exceeded write quota")` |

## Retirement

无；Phase A 之前的"启动 activate"路径继续保留。

---

## File Map

**新建**：
- `kernel/application/agent/tools/memory/MemoryReadTool.java`
- `kernel/application/agent/tools/memory/MemoryWriteTool.java`
- `kernel/application/agent/tools/memory/MemoryForgetTool.java`
- `kernel/application/memory/MemoryReflectionTask.java`
- `kernel/application/memory/MemoryReflectionDispatcher.java`
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentMemoryToolsAutoConfiguration.java`
- 测试 6 个

**修改**：
- `kernel/application/agent/KernelAgentLoop.java` — 在 `streamExecute` 完成时调 `MemoryReflectionDispatcher.dispatchAsync(...)`
- `application.properties` — `seahorse-agent.chat.agent.tools.memory-enabled=true`、`seahorse-agent.memory.reflection.enabled=true`

---

## Tasks

### Task E1 — MemoryReadTool

**Files**:
- create: `kernel/application/agent/tools/memory/MemoryReadTool.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/tools/memory/MemoryReadToolTests.java`

**Why**: 让 LLM 在多步推理中按需读记忆，而非依赖 Pipeline 起点一次性 dump。

**Compatibility**: 复用 `MemoryEnginePort.retrieveMemories(MemoryLoadRequest)`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=MemoryReadToolTests test`

**Steps**:
1. **Write test** —
   - JSON Schema：`scope`(enum: `WORKING|SHORT|LONG|SEMANTIC|ALL`, required)、`query`(string, optional)、`topK`(integer, default 5, max 20)；
   - 调用 Mock `MemoryEnginePort.retrieveMemories(req)` 返回 2 条 `MemoryItem` → Tool 输出 JSON 含 `memories[].layer/content/score`；
   - `scope==ALL` 时入参 `MemoryLoadRequest` 不带 layer 限制；其它枚举映射到对应 layer 过滤。
2. **Verify RED** — 失败。
3. **Minimal code** — 简单封装，注入 `MemoryEnginePort` + `ObjectMapper`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(memory-tool): 新增 memory_read Tool`

---

### Task E2 — MemoryWriteTool（写配额 + 必要字段）

**Files**:
- create: `kernel/application/agent/tools/memory/MemoryWriteTool.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/tools/memory/MemoryWriteToolTests.java`

**Why**: 让 LLM 把对话中明确的"请记住 X"沉淀到指定层。

**Compatibility**: 复用 `MemoryEnginePort.writeMemory(MemoryWriteRequest)`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=MemoryWriteToolTests test`

**Steps**:
1. **Write test** —
   - 入参 `layer`(`SHORT|LONG|SEMANTIC`, required)、`content`(string, required, non-blank)、`importance`(int 1..10, default 5)、`ttlSeconds`(optional)；
   - 一次调用成功 → Mock 端口收到的 `MemoryWriteRequest` 字段一致；
   - 同一 invoke 内调用 5 次以上 → 第 6 次 `failed("exceeded write quota")`；计数器按 toolCallId 重置；
   - `layer==WORKING` → `failed("WorkingMemory 只能由系统写入")`。
2. **Verify RED** — 失败。
3. **Minimal code** — `ConcurrentHashMap<String,AtomicInteger>` 计数；toolCallId 入 KEY。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(memory-tool): 新增 memory_write Tool（带写配额）`

---

### Task E3 — MemoryForgetTool（强制 confirm + 软删除）

**Files**:
- create: `kernel/application/agent/tools/memory/MemoryForgetTool.java`
- create test: `seahorse-agent-tests/.../kernel/application/agent/tools/memory/MemoryForgetToolTests.java`

**Why**: 允许 LLM 在"用户撤回承诺"等场景遗忘特定记忆，但不允许误删。

**Compatibility**: 复用 `ShortTermMemoryMaintenancePort` / `LongTermMemoryPort`（如不存在 forget 方法，则增加 `default` 方法默认抛 `UnsupportedOperationException`）。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=MemoryForgetToolTests test`

**Steps**:
1. **Write test** —
   - 入参 `memoryId`(required)、`confirm`(boolean, required, must `true`)；
   - `confirm==false` → `failed("forget 操作必须显式设置 confirm=true")`，不调端口；
   - 端口抛 `UnsupportedOperationException` → `failed("当前存储后端不支持遗忘")`；
   - 成功 → ok，且记录 trace `memory-forget`。
2. **Verify RED** — 失败。
3. **Minimal code** — 简单分支 + 异常映射。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(memory-tool): 新增 memory_forget Tool`

---

### Task E4 — MemoryReflectionTask 领域对象

**Files**:
- create: `kernel/application/memory/MemoryReflectionTask.java`
- create test: `seahorse-agent-tests/.../kernel/application/memory/MemoryReflectionTaskTests.java`

**Why**: 把"对话结束后做记忆反思"做成可序列化任务，方便走 MQ。

**Compatibility**: 新增。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=MemoryReflectionTaskTests test`

**Steps**:
1. **Write test** —
   - 必填：`conversationId`、`userId`、`finalAnswer`、`messages`；
   - `messages` 必须不可变；
   - `MemoryReflectionTask.from(StreamChatContext)` 静态工厂正确取值。
2. **Verify RED** — 失败。
3. **Minimal code** — record + 校验。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(memory): 新增 MemoryReflectionTask`

---

### Task E5 — MemoryReflectionDispatcher（同步直执行 + MQ 异步双模式）

**Files**:
- create: `kernel/application/memory/MemoryReflectionDispatcher.java`
- create test: `seahorse-agent-tests/.../kernel/application/memory/MemoryReflectionDispatcherTests.java`

**Why**: 抽象触发；本地开发走同步，生产走 Pulsar。

**Compatibility**: 注入 `MessageQueuePort`、`RuleBasedMemoryCandidateExtractor`、`MemoryEnginePort`、`MemoryConflictLogRepositoryPort`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=MemoryReflectionDispatcherTests test`

**Steps**:
1. **Write test** —
   - **场景 1**：`mode==SYNC` → 直接调 extractor + 写入 + 冲突检测，断言 `MemoryEnginePort.writeMemory` 被调用次数等于 extractor 产出候选数；
   - **场景 2**：`mode==ASYNC` → 调 `MessageQueuePort.publish` 一次，payload 为 task 的 JSON；不直接写入；
   - 异常时 fire-and-forget，不抛给调用方。
2. **Verify RED** — 失败。
3. **Minimal code** — switch on enum + try/catch warn。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(memory): 实现 MemoryReflectionDispatcher`

---

### Task E6 — KernelAgentLoop 完成时调度 Reflection

**Files**:
- modify: `kernel/application/agent/KernelAgentLoop.java`
- modify: `seahorse-agent-tests/.../kernel/application/agent/KernelAgentLoopTests.java`（新增用例）

**Why**: 把 reflection 接入 Agent 收尾，但仅在配置开启且 dispatcher 存在时启用。

**Compatibility**: `KernelAgentLoop` 接收 `Optional<MemoryReflectionDispatcher>`（缺省时跳过）；旧 4 参构造保留。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=KernelAgentLoopTests test`

**Steps**:
1. **Write test** —
   - 注入 dispatcher → final answer 后 `dispatcher.dispatch(task)` 被调 1 次，task 字段一致；
   - 未注入 → 无副作用、不抛错；
   - LLM 触达 `maxSteps` 截断 → 不触发 reflection（避免不完整状态污染记忆）。
2. **Verify RED** — 失败。
3. **Minimal code** — 新增构造接受 `Optional<MemoryReflectionDispatcher>`；在 `streamExecute` 的 final-answer 分支收尾调度。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(agent): KernelAgentLoop 收尾调度 MemoryReflection`

---

### Task E7 — Spring 自动装配记忆 Tools 与 Reflection

**Files**:
- create: `seahorse-agent-spring-boot-starter/.../SeahorseAgentMemoryToolsAutoConfiguration.java`
- modify: `seahorse-agent-spring-boot-starter/src/main/resources/application.properties`
- create test: `seahorse-agent-tests/.../adapters/spring/SeahorseAgentMemoryToolsAutoConfigurationTests.java`

**Why**: 默认开启（与 Phase A 的 agent-mode-enabled 共生）。

**Compatibility**: `@ConditionalOnProperty("seahorse-agent.chat.agent-mode-enabled", havingValue="true")` + 子开关 `seahorse-agent.chat.agent.tools.memory-enabled`（默认 `true`）、`seahorse-agent.memory.reflection.enabled`（默认 `false`，需显式打开）。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=SeahorseAgentMemoryToolsAutoConfigurationTests test`

**Steps**:
1. **Write test** —
   - 默认 agent off → 全部不装配；
   - agent on + memory-tools on → 3 个 Tool Bean 存在并注册到 ToolRegistry；
   - reflection on + 提供 `MessageQueuePort` → `MemoryReflectionDispatcher` Bean 存在；
   - reflection on + 无 `MessageQueuePort` → dispatcher 走 `SYNC` 模式且不抛错。
2. **Verify RED** — 失败。
3. **Minimal code** — 三组条件 Bean + `ApplicationRunner` 注册。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(starter): 装配记忆工具与反思调度`

---

## Self-Review

- **Spec coverage** — read/write/forget Tool / Reflection 任务 / 调度器 / Loop 联动 / 装配全部映射。
- **Placeholder scan** — 无。
- **Type consistency** — `MemoryReflectionTask`、`MemoryLoadRequest`、`MemoryWriteRequest` 跨任务一致。
- **Compatibility** — `MemoryEnginePort` 不破坏；KernelAgentLoop 新构造保留旧构造；所有 Bean 走条件装配且默认值保守。
- **Verification** — 每 Task 自带 mvn 命令；端到端可在 Phase A B8 类用例上叠加（如需在本 Phase 加 E2E，后续 Task E8 单开）。
- **Dual-track** — 旧 `activateMemory` 阶段保留服务 RAG；Agent 模式额外有 Tool 调用 + Reflection；不互相替换。
- **Decision hygiene** — Tool 三件、Reflection 两件，各自单一职责；Dispatcher 同步/异步两模式由配置切换，不引入新 owner。

## Retirement Track

无。

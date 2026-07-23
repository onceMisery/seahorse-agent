# VCP 生产运行时方案在 Seahorse Agent 的落地分析

日期：2026-07-23

## 1. 结论摘要

VCP 文档讨论的不是某个框架 API，而是 Agent 上线后必须守住的五个运行时不变量：

1. 模型请求不能越过上下文窗口。
2. 原始事实不能因为压缩、截断或重启而永久丢失。
3. 工具调用不能悬空、重复执行或失去审计链路。
4. 排障时必须能解释模型实际获得了哪些证据，以及运行时做过哪些预算和降级决策。
5. 能力供给、并发和热更新不能绕过权限、审批、配额与资源访问治理。

Seahorse 当前并不是从零开始：

- **已经领先 VCP**：通用 Tool Result Spill、受治理范围回读、统一 `run -> step -> model/tool` trace、OTEL/Jaeger/AgentScope Studio 生产联调、Tool Gateway 治理。
- **已经基本对齐**：Skill 按需加载、工具搜索、MCP 刷新/重启、OpenAPI/A2A 能力接入、轮内工具并发与保序。
- **仍是主要缺口**：统一动态上下文预算、结构化会话折叠、通用进程重启恢复、工具幂等执行闭环。
- **需要立即补证据**：Spill 已有实现，但仓库没有对应的真实 full-Docker E2E，也没有直接覆盖 Spill/回读服务的聚焦测试。
- **需要数据治理修正**：运行步骤和审批 checkpoint 会持久化 `thinking`/`thinkingContent`；现有凭据脱敏不会删除原始推理文本。

因此，不应把 VCP 的 Node.js 插件体系或文本工具协议移植进来。推荐复用 Seahorse 已有的 PostgreSQL、Artifact/Object Storage、Tool Gateway、Run/Checkpoint、OTEL 和 Studio，在现有 owner 上补齐预算、折叠、恢复与证据。

## 2. 第一性原理与 owner

### 2.1 不可妥协目标

- 原始消息、原始工具结果和关键执行状态有持久化事实源。
- 发给模型的是受预算约束的 working view，而不是把持久化事实源直接裁掉。
- 每个降级动作都能回答：为什么触发、删除或替换了什么、原文在哪里、如何恢复。
- 对有副作用的工具，重试和恢复最多产生一次外部效果。
- 不持久化隐藏 chain-of-thought；只记录可审计的输入摘要、决策元数据、证据引用和结果。

### 2.2 推荐 canonical owner

| 关注点 | canonical owner | 复用能力 |
| --- | --- | --- |
| 工具大结果 | `LocalToolGatewayPort` 后置输出治理 | `ToolResultSpillPort`、`AgentArtifactRepositoryPort`、`ObjectStoragePort` |
| 模型上下文预算 | `AgentLoopModelTurns` 调用模型前的单一 Context Envelope | `TokenCounterPort`、模型配置、`ContextWeaverPort` |
| 会话折叠 | Kernel 会话上下文装配层 | `t_message`、分支路径、Context Pack、Artifact evidence |
| 工具恢复与幂等 | Agent Runtime + Tool Gateway | `AgentCheckpointRepositoryPort`、Tool audit、run lease、idempotency key |
| 认知可观测 | 现有 trace/run-step/snapshot 体系 | `KernelRagTraceRecorder`、OTEL、Studio、RunContextSnapshot |
| 能力供给 | 现有 Skill/Tool Catalog/provider lifecycle | Skill revision、MCP、OpenAPI、A2A、Tool Gateway |

不要新增第二套 Tool Result 表、会话账本、trace 后台或插件运行时。

## 3. 七类问题对照

| 文档问题 | VCP 方案 | Seahorse 现状 | 真实缺口 | 推荐优先级 |
| --- | --- | --- | --- | --- |
| 短期记忆 | OneRing 完整持久化；ContextFoldingV2 按深度和语义折叠；OneRingMemo 递归摘要；硬裁剪兜底 | `t_message` 完整保存会话和分支；普通历史固定读取最近 20 条；分支路径可完整读取；Context Pack/Memory 使用固定字符预算 | 没有统一 token envelope；普通路径按条数而非 token；分支路径无预算上限；没有版本化、可恢复的结构化会话摘要 | P0/P1 |
| 可观测性 | ToolCallRecordStore、最终请求快照、实时日志；无统一 span 树；刻意不落盘 reasoning | 已有 run/step/model/tool span 树、RAG trace、Tool audit、Run step、RunContextSnapshot、Jaeger 和 Studio | 未保存每轮最终有效上下文的安全指纹/预算决策；`thinking` 仍会进入 step/checkpoint 持久化 | P1 |
| Spill | 仅规范和 Base64 剔除，未落地 | 超 8192 字符结果在 Gateway 脱敏后进入对象存储和 Artifact，模型收到 preview/pointer；`read_tool_result` 按 run/tenant/user 范围回读 | 缺真实 Docker E2E；缺直接测试；只支持 UTF-8 文本，未记录内容 hash，缺 TTL/保留策略和按 tool 配置 | P0 验证与硬化 |
| 能力供给 | 六类插件、热重载、Skill/Tool 按需加载 | Skill revision/runtime block、`load_skill_resource`、`tool_search`、MCP refresh/restart、OpenAPI import、A2A、统一 Catalog/Gateway | 在途调用的 provider 引用计数/延迟关闭没有明确证据；需要持续做 provider 覆盖而非新体系 | P2 |
| 并行工具 | `Promise.all` 并发、保序、轮间串行 | `invokeAll` 分批并发，按 Future 列表保序；有并发上限、单工具超时、取消和独立 trace | full Docker 默认 `maxParallelTools=1`；没有基于 READ/WRITE/DELETE/EXTERNAL_SEND 的副作用并行资格策略 | P1/P2 |
| 动态预算 | 字符近似；OneRingMemo 先扣输出；真 tokenizer 只观测 | `ContextBudget.defaults()` 固定 20 项/4000 字符；`TokenCounterPort` 是字符/4 近似，主要用于模型路由查询，Agent Loop 不使用 | 没有扣除 system、skill、tool schema、当前输入、输出预留和安全缓冲；没有压缩触发决策和预算证据 | P0 |
| 悬空调用 | 文本协议规避原生 FC 配对问题 | 使用原生 structured tool calls；WAITING_APPROVAL 有 durable checkpoint/resume；run lease 和 worker 已存在 | `MODEL_TURN/BEFORE_TOOL/AFTER_TOOL` checkpoint 类型未接主循环；恢复只覆盖审批；idempotency key 只传递/审计，未在 Gateway 强制去重 | P1 |

## 4. 关键代码证据

### 4.1 Spill 已实现，但验收未闭环

- `KernelToolResultSpillService` 在 Tool Gateway 脱敏后处理成功结果，默认阈值 8192 字符、预览 800 字符、单次回读 4096 字符。
- 完整文本写入 `agent-artifacts` bucket，并保存 `AgentArtifact`；模型只拿到 `artifactId`、长度、preview 和回读指令。
- `ToolResultReadToolPortAdapter` 要求 artifact 同时匹配当前 run、tenant、user，并限制 `provenance.kind=tool_result_spill`。
- `LocalToolGatewayPort` 的顺序是：真实执行 -> artifact side effect -> 输出脱敏 -> Spill -> 审计完成。
- 仓库中未找到 Spill 专属 E2E 脚本，也未找到直接覆盖两个实现类的测试。

判断：功能代码已完成，当前状态应标记为 **implemented, not production-accepted**，而不是“尚未实现”。

### 4.2 Trace 基座已完成，认知语义仍不足

- `KernelAgentLoop` 创建 `AGENT_STEP`、`AGENT_MODEL`、`AGENT_TOOL` 节点。
- `MicrometerTraceTelemetryAdapter` 将其映射为 `agent.run`、`agent.step`、`model.call`、`tool.call` span。
- 2026-07-22 full-Docker E2E 已验证 kernel/AgentScope SSE、PostgreSQL snapshot、Jaeger trace、Studio SQLite 和 Studio 页面，结果为 11/11。
- RunContextSnapshot 记录执行器、角色卡、Run Profile、模型配置、工具、知识库和 trace 链接，但不是每个模型 turn 的最终消息体。
- `AgentRunStepRecorder.modelTurnInput()` 目前只记录 message/tool 数量，无法还原“模型当时看到了什么”。

推荐只补安全元数据：`effectiveContextHash`、各分区 token、tool schema token、output reserve、fold/spill decisions、evidence ids。不要把完整敏感 prompt 或隐藏 reasoning 直接塞进 OTEL tag。

### 4.3 动态预算是最明确的运行时缺口

- 普通会话历史固定取最近 20 条，与模型上下文窗口和单条消息大小无关。
- 分支路径通过递归 SQL 返回完整链路，没有统一 budget gate。
- Context Pack/Memory weaving 使用固定 `maxItems=20, maxChars=4000`。
- `AgentLoopModelTurns` 把历史、runtime context、Skill body 和工具 schema 组装后直接请求模型。
- OpenAI-compatible adapter 的 token counter 仍是 `codePointCount / 4`，并非 provider tokenizer。

这意味着系统提示、Skill 正文、工具 schema 或超长分支任一增长，都可能使固定“20 条历史”失效。

### 4.4 恢复机制只覆盖审批等待

- `AgentCheckpointType` 已定义 `MODEL_TURN/BEFORE_TOOL/AFTER_TOOL/WAITING_APPROVAL`。
- 生产代码只创建 `WAITING_APPROVAL` checkpoint；其余类型只在 repository 测试中出现。
- Resume 会从 checkpoint 重建 pending tool request、重新调用 Tool Gateway，再做一次无工具的最终模型调用。
- Tool request 带稳定 idempotency key，但 Tool Gateway 没有使用现有 `IdempotencyService` 或 durable invocation result 做去重。

因此，进程在外部工具已产生副作用、但 AFTER_TOOL 状态未落库时重启，仍存在重复执行风险。

### 4.5 原始 thinking 持久化需要收敛

- `KernelAgentLoop.modelTurnOutputJson()` 写入 `thinking`。
- `RepositoryAgentApprovalWaitHandler` 将每条消息的 `thinkingContent` 写入 checkpoint。
- `RepositoryAgentRunStepRecorder` 的安全处理会做凭据模式脱敏，但不会按字段删除 `thinking`。

推荐默认不持久化原始 thinking，只保留：是否启用 thinking、持续时间、字符/token 数、提供方、结束原因和可选的用户可见 reasoning 摘要。若产品确需保存用户可见 reasoning，必须由显式租户策略、保留期和访问权限控制。

## 5. 推荐开发顺序

### P0-A：先补 Spill 真实 E2E 和最小硬化

不新增抽象，先证明现有实现可用：

- 新增 full-Docker smoke，调用真实 Gateway 工具产生超过阈值的文本。
- 验证 PostgreSQL Artifact、MinIO 完整对象、pointer preview 和中后段 marker 回读。
- 验证跨 run、跨用户、跨租户读取均失败。
- 验证 Tool audit、API 和模型 observation 不泄露 `storageRef`、密钥或完整大结果。
- 增加 SHA-256、原始字符/字节数；为文本 Spill 保留明确 MIME。
- 保留现有 Artifact 作为 owner，不新建表。

### P0-B：建立唯一的 Model Context Envelope

在 `AgentLoopModelTurns` 发起模型请求前增加一个统一决策点：

```text
effectiveWindow = modelContextWindow - outputReserve - safetyBuffer
fixedCost = system + runtimeContext + currentInput + toolSchemas
historyBudget = max(0, effectiveWindow - fixedCost)
```

决策顺序：

1. 计算不可裁剪固定开销。
2. Tool result 已由 Spill 变成有界 observation。
3. 在历史预算内保留最新 turn、当前目标锚点和未闭合 tool pair。
4. 若仍超限，使用已就绪结构化摘要替换较老 turn。
5. 摘要不可用时做显式硬裁剪，并记录原因与被移除消息指纹。
6. 最终请求再次计数，超限则 fail closed，不把 provider 的 context-length 错误当正常失败。

`TokenCounterPort` 继续作为端口，但需要支持 model-aware 实现；近似实现可保留为 fallback，并使用中英文/JSON 校准和安全余量。

### P1-A：结构化、可恢复的会话折叠

不要复制 VCP 的 SQLite/embedding/fuzzy cache。第一版只做：

- 原始 `t_message` 永不覆盖。
- 摘要是独立版本化记录，状态为 `PENDING/READY/FAILED/SUPERSEDED`。
- 摘要结构固定包含 `User Intent`、`Current State`、`Decisions`、`Open Items`、`Constraints`、`Evidence Refs`。
- 异步生成，失败不得替换原文。
- working view 只使用 READY 版本，并记录 summary id/hash。
- 分支摘要必须绑定 branch range，不能污染其他分支。

只有真实长会话数据证明单纯结构化摘要不足时，再评估语义相似度折叠。

### P1-B：通用 Tool Invocation checkpoint 与幂等恢复

- 模型返回 tool calls 后先写 `BEFORE_TOOL` checkpoint 和 durable invocation 状态。
- Tool Gateway 以 `(tenantId, runId, toolCallId/idempotencyKey)` 为唯一执行键。
- 状态至少包括 `PENDING/RUNNING/SUCCEEDED/FAILED/UNKNOWN`，成功结果或 Artifact pointer 可重放。
- 工具完成后原子写审计完成和 `AFTER_TOOL` checkpoint。
- 重启恢复时：SUCCEEDED 直接重放 observation；RUNNING/UNKNOWN 按 tool side-effect policy 决定查询、补偿或人工处理；不能盲目重调。
- 原生 Function Calling 的 assistant/tool 配对由 checkpoint working view 重建，不改成 VCP 文本协议。

### P1-C：认知 trace 元数据与 thinking 治理

- 每个 model span 记录预算分区、最终 token 数、上下文 hash、summary/spill evidence ids 和裁剪原因。
- 每个 tool span 关联 Tool Gateway invocation id、policy decision id、approval id、artifact id。
- 默认删除 durable step/checkpoint 中的原始 thinking；Studio/前端只展示允许公开的 reasoning 摘要或统计。

### P2：并发与 provider 生命周期硬化

- 仅允许明确声明 `READ`、无副作用且支持并发的工具进入同批并行。
- `WRITE/DELETE/EXTERNAL_SEND` 默认串行；不确定时串行。
- full Docker 将测试配置设为 `maxParallelTools>1`，验证总耗时接近最长单项、返回顺序不变、span 不串线。
- MCP/OpenAPI/A2A provider 刷新增加 generation/ref-count 或 drain 语义，避免关闭仍有在途调用的 client。

## 6. 真实 E2E 验收矩阵

| 切片 | 真实场景 | 必须查询的证据 |
| --- | --- | --- |
| Spill | 真实 Tool Gateway 返回大文本，含头/中/尾唯一 marker | API observation、`sa_agent_artifact`、MinIO object、`sa_tool_invocation`、范围回读与越权失败 |
| Context Envelope | 真实模型 + 中文/英文/JSON 长历史 + 多工具 schema | 模型请求成功、预算记录、最新意图保留、上下文 hash、无 provider overflow |
| Folding | 50+ turn 分支会话，摘要成功与失败各一次 | 原始 `t_message` 不变、摘要版本状态、working view、分支隔离、Trace evidence ids |
| Recovery | 工具产生可计数副作用后强制停止 backend，再重启 worker | checkpoint/invocation 状态、外部副作用次数为 1、重放 observation、无悬空 tool pair |
| Parallel | 三个延迟 READ 工具 + 一个 WRITE 工具 | READ 总耗时、结果顺序、独立 Jaeger spans、WRITE 串行策略 |
| Thinking 治理 | thinking 模型执行一次 kernel 和 AgentScope run | DB/快照/OTEL 无原始 thinking，保留统计和允许公开的摘要 |

## 7. 明确不做

- 不引入 VCP 的 `[TOOL_REQUEST]` 文本协议替代原生 Function Calling。
- 不复制 OneRing、ToolCallRecordStore、AdminPanel 或新的插件 manager。
- 不把 raw chain-of-thought 当作可观测性数据落盘。
- 不先做向量 fuzzy summary cache、递归摘要图或复杂语义动力学。
- 不为每个 provider 分别实现 Spill、预算或恢复；统一边界分别是 Tool Gateway、Model Context Envelope 和 Agent Runtime。
- 不继续使用固定“历史占窗口百分比”或固定消息条数作为最终预算策略。

## 8. 最终路线图

推荐从现在开始按以下顺序提交独立切片，并在每个切片后运行真实 full-Docker E2E：

1. **Spill full-Docker E2E + hash/MIME 最小硬化**。
2. **Model Context Envelope + 预算 trace + 长上下文 E2E**。
3. **原始 thinking 持久化收敛**。
4. **BEFORE_TOOL/AFTER_TOOL checkpoint + Gateway 幂等恢复 + 重启 E2E**。
5. **版本化结构化会话折叠 + 长会话/分支 E2E**。
6. **副作用感知并发 + provider drain/ref-count**。

这条路线先关闭已经存在但未验收的风险，再建立预算和恢复两个运行时基础设施，最后才增加语义折叠复杂度。它保留 Seahorse 的现有架构优势，也吸收了 VCP 在渐进降级和能力按需供给上的有效经验。

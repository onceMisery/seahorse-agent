# Java Agent 生产运行时建议吸收方案

## 1. 目标与边界

本文对 `docs/temp/Java Agent 生产的那些坑，我们帮你填了.md` 的建议做项目现状审计，并确定首个落地切片。

本次目标是解决普通工具结果超过 Agent Loop 上下文上限后被静默截断的问题：

- 结果不再因为上下文预算而永久丢失；
- 模型只接收有界预览和受治理的证据引用；
- 模型可以通过内部工具按范围读取原始结果；
- 完整结果沿用现有对象存储和 `sa_agent_artifact` 记录；
- 现有审批、审计、脱敏、租户和用户边界继续有效。

本切片不实现会话摘要、语义折叠、TTL 清理、JSONPath 查询、动态工具自治或新的记忆图。它们需要独立的质量数据和运行时契约，不能借由本问题扩大范围。

## 2. 现状审计矩阵

| 建议 | 当前状态 | 决定 | 依据与边界 |
| --- | --- | --- | --- |
| 渐进式短期记忆压缩 | 已有长期记忆压缩基座，未等同于会话历史压缩 | 暂缓 | 需要先明确消息持久化、摘要注入和回放契约 |
| 工具结果微压缩、证据指针 | 普通工具结果在 8 KiB 后直接截断 | 采纳 | 本文首个完整切片；先持久化和范围回读，不做语义摘要 |
| 动态上下文预算 | `DefaultContextWeaver` 已有分区和字符/条数预算 | 保持 | 后续可接入 Spill 元数据，不重复造预算器 |
| 悬空工具调用修复 | Agent Loop 已有超时、取消和失败观察处理 | 保持 | 通过真实并发/超时 E2E 继续观察，不新增协议 |
| Span + Event 认知可观测 | RAG trace、Observation、run step 已存在 | 增量采纳 | Spill 复用现有工具 trace 和审计，不另建观测体系 |
| 大结果 Spill、预览、范围回读 | 生成类工具已有 Artifact，普通工具没有 | 采纳 | 统一在 Gateway 脱敏结果之后处理 |
| Skill 按需加载 | 动态 Skill、revision、加载工具已存在 | 保持 | 不再增加第二套加载协议 |
| MCP 动态热更新、差量刷新、延迟关闭 | MCP 和动态目录已有较完整基础 | 暂缓增强 | 与本问题没有直接故障闭环，另行压测和契约评审 |
| 并行工具调用、顺序保持、trace 隔离 | `invokeAll`、原始顺序和独立 trace 已实现 | 保持 | Spill 必须是每个调用独立、结果顺序不变 |
| 语义模型路由/容灾 | 已有 Run Profile、Model Routing 和 rollout/gate | 暂缓 | 避免创建第二个模型路由 owner |
| 语义感知上下文折叠 | 尚无稳定 embedding、摘要和质量门禁 | 暂缓 | 先用可验证的引用回读解决信息丢失 |
| 异步长任务工具协议 | 已有任务/运行时能力，但协议边界不同 | 暂缓 | 需另行定义状态机、恢复和幂等契约 |
| 环境感知上下文注入 | 可注入信息可能包含运维敏感数据 | 不默认采纳 | 仅由明确 operator policy 控制的字段才可进入上下文 |
| 标签共现图联想记忆 | 已有向量、关键词和图索引基础 | 暂缓 | 先建立真实 recall 数据集再决定是否引入 spreading activation |

## 3. 首个切片设计

### 3.1 数据流

```text
ToolPort
  -> LocalToolGatewayPort
  -> 既有生成类 Artifact 发布（仅生成工具）
  -> 既有输出脱敏
  -> ToolResultSpillPort
       小结果: 原样返回
       大结果: 完整写入 ObjectStoragePort + AgentArtifactRepositoryPort
               返回 preview + artifactId + 长度 + 回读提示
  -> Tool audit completion
  -> Agent Loop observation
```

Spill 放在 Gateway 的脱敏之后，保证对象存储、模型观察和审计摘要都不绕过现有凭据脱敏。失败时不返回大结果，也不退回截断文本，而是返回明确的工具失败结果；上传成功但数据库保存失败时尽力删除对象并失败关闭。

### 3.2 复用的数据模型

使用现有 `AgentArtifact` 的 `FILE` 类型，不增加数据库枚举和迁移。`provenanceJson` 标记：

```json
{
  "kind": "tool_result_spill",
  "toolId": "...",
  "toolCallId": "...",
  "stepId": "...",
  "contentChars": 12345
}
```

`storageRef` 只存在内部领域对象和存储适配器调用中，不进入 Spill 的模型观察、回读返回值、审计摘要或 Web response。现有 Artifact Web API 的 response 已不包含 `storageRef`。

### 3.3 内部回读工具

新增 `read_tool_result`，使用 `ToolInvocationRequestAwarePort` 获取当前 run、tenant 和 user 上下文：

- 参数：`artifactId`、`offset`、`limit`；
- `offset` 和 `limit` 按 UTF-16 字符计数，避免模型处理字节偏移；
- `limit` 不得超过配置的 `maxReadChars`；
- 仅允许读取 `provenance.kind=tool_result_spill` 的 Artifact；
- 必须匹配当前请求的 run、tenant、user；
- 不返回 `storageRef`，不存在、越权和非 Spill Artifact 使用同一错误文案；
- 返回 `content`、`offset`、`returnedChars`、`nextOffset`、`hasMore` 和 `artifactId`；
- 回读工具自身不再触发 Spill，避免递归。

工具注册沿用现有 `DescribedToolPort` + `BuiltInAgentToolRegistrar`。Agent Loop 像现有 Skill/Tool Search 一样自动加入注册成功的回读工具，并在 Gateway 中补入有效 allowlist；不改变 operator 对外部高风险工具的审批策略。

### 3.4 配置与兼容性

配置前缀为 `seahorse-agent.chat.agent.tools.result-spill`：

| 配置 | 默认值 | 含义 |
| --- | ---: | --- |
| `enabled` | `true` | 是否启用普通工具结果 Spill |
| `threshold-chars` | `8192` | 超过该字符数才持久化 |
| `preview-chars` | `800` | 模型观察中保留的预览长度 |
| `max-read-chars` | `4096` | 单次回读上限 |

兼容性边界：8 KiB 以内结果内容保持原样；超限结果不再使用旧的 `...[truncated]` 语义。未提供对象存储或 Artifact repository 时保持现有 no-op Gateway 构造器行为，生产自动配置在两项依赖存在时启用 Spill。

生成类工具仍由 `GenerationToolArtifactPublicationPort` 处理，不重复 Spill；普通 MCP、OpenAPI、A2A、Web 和自定义 Tool 均按统一规则处理。

## 4. 分片实施与验收

### Slice 1：设计文档

提交本文件。验收是矩阵、数据流、失败策略、权限边界、兼容性和非目标均明确。

### Slice 2：通用 Spill 服务与 Gateway 接入

新增 kernel port、默认实现、配置 options 和 Spring wiring；只改变成功结果在脱敏后的封装方式。使用现有 `AgentArtifactRepositoryPort` 和 `ObjectStoragePort`，不新增表。

验证重点：真实工具超限时对象和数据库记录同时存在；上传/保存失败时返回失败结果；小结果、失败结果、并行调用顺序保持不变。

### Slice 3：受治理范围回读工具

新增描述工具、Agent Loop 内建注册和范围读取；回读使用流式 skip/read，内存上限由 `max-read-chars` 控制。

验证重点：同一用户同一 run 能读中后段 marker；不同用户、不同租户、不同 run、非 Spill Artifact 均失败；响应不含 storageRef。

### Slice 4：真实 full-Docker E2E

在全量 Docker 环境调用真实 Agent/API 和真实工具返回超过阈值的数据，核对：

1. 模型收到 preview、字符数和 artifactId；
2. PostgreSQL 有对应 Artifact，租户、用户、run、toolCall provenance 正确；
3. 对象存储存在完整原文；
4. `read_tool_result` 读取中后段 marker；
5. 越权读取失败；
6. API、audit、tool observation 不泄露 storageRef；
7. run/session 最终收敛，未留下临时对象和悬空状态。

## 5. 风险、回滚与后续触发条件

- **存储成本**：首版只对超阈值结果保存一次，不做压缩、分页 DSL 或 TTL；后续依据真实命中率和对象增长决定治理策略。
- **脱敏正确性**：Spill 必须位于脱敏之后；如果脱敏器异常，Gateway 现有失败路径继续生效。
- **读性能**：回读按流跳过并限制字符数；如果真实数据证明频繁大 offset 读取成本过高，再评估对象索引或字节范围 API。
- **回滚**：关闭 `enabled` 即停止新 Spill；现有 Artifact 和对象不受影响。代码级回滚只涉及新增 port、服务、工具和 Gateway wiring。
- **清理触发条件**：只有在确认所有调用方不再依赖旧的截断后缀，并有生产数据证明引用回读稳定，才删除旧截断辅助逻辑。

## 6. 决策记录

选择 Gateway 脱敏后的统一处理点，而不是把逻辑塞进 `AgentLoopToolExecutor`，因为 Gateway 是所有本地工具调用的共同边界，且能覆盖直接调用、MCP、OpenAPI 和 A2A。选择现有 Artifact，而不是新建 Tool Result 表，因为现有表已包含 run、tenant、user、preview、provenance 和对象引用，新增模型会制造重复 owner。选择确定性 preview + 范围回读，而不是语义摘要，因为它能用真实 E2E 精确证明“数据没有丢失”。

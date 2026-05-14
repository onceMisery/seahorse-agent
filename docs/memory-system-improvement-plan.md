# 记忆子系统改进方案：先打通主链路

> **目标**: 将四层记忆从“存储与治理 API 已存在”推进到“聊天主链路可读取、Prompt 可消费、失败可降级”。
> **原则**: 先做最小可验证闭环，避免把衰减、质量评估、向量检索、跨会话推理一次性塞进首轮改造。
> **边界**: 本文只覆盖主链路接入与基础写入闭环；QueryOptimizer、跨会话推理见 `memory-query-optimizer-cross-session-design.md`。

---

## 1. 当前事实

当前聊天链路只加载会话消息历史：

```text
KernelChatPipeline.loadMemory()
  -> ConversationMemoryPort.loadAndAppend()
  -> JdbcConversationMemoryAdapter
  -> t_message
```

四层记忆相关端口和表已经存在，但没有被聊天主链路消费：

```text
MemoryEnginePort
  -> 当前默认 noop

ShortTermMemoryPort   -> JdbcShortTermMemoryRepositoryAdapter   -> t_short_term_memory
LongTermMemoryPort    -> JdbcLongTermMemoryRepositoryAdapter    -> t_long_term_memory
SemanticMemoryPort    -> JdbcSemanticMemoryRepositoryAdapter    -> t_semantic_memory
MemoryVectorPort      -> 当前仅端口，未接入真实向量后端
```

关键断裂点：

| 断裂点 | 当前位置 | 影响 |
| --- | --- | --- |
| `ChatPreparationPorts` 不含 `MemoryEnginePort` | `kernel/application/chat` | `KernelChatPipeline` 无法激活四层记忆 |
| `StreamChatContext` 不含 `MemoryContext` | `kernel/domain/chat` | 主链路中间阶段无法传递记忆上下文 |
| `PromptContext` 不含 `MemoryContext` | `kernel/domain/chat` | `RagPromptPort` 无法把记忆写入 Prompt |
| `LocalRagPromptAdapter` 只消费 KB/MCP 上下文 | `adapter-web` | 即使加载了记忆，也不会进入模型输入 |
| `MemoryEnginePort` 默认 noop | `starter` | 记忆治理中的衰减/质量评估实际无法执行 |

---

## 2. 第一阶段范围

第一阶段只做“读路径闭环”：

```text
用户提问
  -> loadMemory()           读取 t_message 会话历史
  -> activateMemory()       读取短期/长期/语义记忆
  -> rewriteQuery()
  -> resolveIntents()
  -> retrieve()
  -> streamRagResponse()
       -> PromptContext.memoryContext
       -> LocalRagPromptAdapter 注入用户记忆
```

第一阶段明确不做：

- 不做全量记忆衰减扫描。
- 不做 MemoryVectorPort 的真实向量检索。
- 不做 LLM 摘要、事实抽取、跨会话推理。
- 不把用户原始问题无条件当作“记忆”写入。
- 不新增数据库表。

---

## 3. 设计调整

### 3.1 `MemoryEnginePort` 实现位置

不要把首个实现命名为 `JdbcMemoryEngineAdapter` 并放进 repository adapter。它不是 JDBC 细节，而是对多个记忆端口的编排策略。

建议新增内核侧组合实现：

```text
seahorse-agent-kernel/.../application/memory/DefaultMemoryEnginePort.java
```

职责：

- 实现 `MemoryEnginePort`。
- 组合 `ShortTermMemoryPort`、`LongTermMemoryPort`、`SemanticMemoryPort`、可选 `MemoryVectorPort`。
- `loadMemory()` 只做多层读取、限量、转换、去重。
- `writeMemory()` 第一阶段保持 no-op 或只接受明确的结构化 `MemoryWriteRequest`。
- `executeMemoryDecay()` 第一阶段不在这里“假实现”全量扫描。
- `assessMemoryQuality()` 可返回基础计数，但不得声称具备冲突检测能力。

这样可以避免把策略逻辑绑定到 JDBC，同时复用现有 JDBC 分层仓储。

### 3.2 读取策略

`loadMemory(MemoryLoadRequest request)` 的第一阶段行为：

| 层级 | 数据来源 | 策略 |
| --- | --- | --- |
| working | `ConversationMemoryPort` 已在 `loadMemory()` 阶段处理 | 不在 `MemoryEnginePort` 中重复加载 |
| short_term | `ShortTermMemoryPort.listByUser(userId, limit)` | 取 Top 5，沿用现有 `importance_score DESC, create_time DESC` |
| long_term | `LongTermMemoryPort.listByUser(userId, limit)` | 取 Top 3 |
| semantic | `SemanticMemoryPort.listByUser(userId, limit)` | 取 Top 10 |
| vector | `MemoryVectorPort` | 第一阶段默认关闭；只有真实实现后再启用 |

转换约束：

- 当前 `MemoryRecord` 是 Java record，没有 builder，必须使用构造器或单独的 mapper。
- `MemoryItem` 已有 Lombok builder，可作为 Prompt 侧展示模型。
- `metadataJson` 需要使用 Jackson 序列化，不能用 `Map.toString()` 伪 JSON。
- `record.updatedAt()` 可能是 `Instant.EPOCH`，转换时间时要兜底。

### 3.3 主链路集成

修改 `ChatPreparationPorts`：

```java
public record ChatPreparationPorts(
        ConversationMemoryPort memoryPort,
        MemoryEnginePort memoryEnginePort,
        QueryRewritePort queryRewritePort,
        IntentResolutionPort intentResolutionPort,
        IntentGuidancePort intentGuidancePort,
        RetrievalContextPort retrievalContextPort) {
}
```

兼容要求：

- 自动配置中用 `memoryEnginePort.getIfAvailable(MemoryEnginePort::noop)`。
- 如测试或调用点较多，可保留旧构造函数委托到新构造函数。

修改 `StreamChatContext`：

```java
private MemoryContext memoryContext;
```

修改 `PromptContext`：

```java
private MemoryContext memoryContext;

public boolean hasMemory() {
    return memoryContext != null
            && (notEmpty(memoryContext.getShortTermMemories())
            || notEmpty(memoryContext.getLongTermMemories())
            || notEmpty(memoryContext.getSemanticMemories()));
}
```

注意：当前 `MemoryContext` 是 Lombok `@Value`，访问器是 `getShortTermMemories()`，不是 record 风格的 `shortTermMemories()`。

修改 `KernelChatPipeline.execute()`：

```text
loadMemory
activateMemory
rewriteQuery
resolveIntents
handleGuidance
handleSystemOnly
retrieve
handleEmptyRetrieval
streamRagResponse
```

`activateMemory()` 必须满足：

- 捕获异常并降级为空记忆上下文。
- 不改变 `context.question`。
- 不阻塞主链路；如未来接入向量检索或 LLM 能力，必须加超时。
- Trace 节点记录成功/失败即可，不把失败升级成聊天失败。

### 3.4 Prompt 消费是必做项

`PromptContext.memoryContext` 只有字段没有价值，`LocalRagPromptAdapter` 必须消费它。

建议在 system prompt 中追加受控长度的记忆区块：

```text
用户记忆上下文：

用户画像：
- ...

长期记忆：
- ...

近期记忆：
- ...
```

约束：

- 每层最多输出配置化条数，第一阶段可固定为语义 10、长期 3、短期 5。
- 单条内容需要截断，避免挤占 KB 上下文。
- 记忆只能作为辅助上下文，system prompt 要说明“若与知识库上下文冲突，以知识库上下文为准，除非问题明确询问用户偏好或历史”。
- 不要把 `metadataJson` 直接暴露给模型。

---

## 4. 第二阶段：写入闭环

第一阶段不要无条件写入用户原始问题。原始问题通常是检索输入，不等同于可复用记忆。

第二阶段写入策略：

| 写入对象 | 是否写入 | 说明 |
| --- | --- | --- |
| 用户原始问题 | 默认不写 | 避免把噪声写进短期记忆 |
| 助手完整回答 | 默认不写 | 容易造成模型自我污染 |
| 对话摘要 | 可写 | 需要基于完整问答生成摘要 |
| 明确事实 | 可写 | 如“用户使用 Java 21” |
| 明确偏好 | 可写 | 如“用户偏好中文回答” |

实现建议：

- 用 `StreamCallback` 装饰器收集助手输出，在 `onComplete()` 后异步触发候选记忆提取。
- 提取器可以先做规则版：只识别明确的用户偏好/事实标记，不调用 LLM。
- 写入 `ShortTermMemoryPort` 时必须设置：
  - `userId`
  - `conversationId`
  - `importanceScore`
  - `confidenceLevel`
  - `sourceMessageIds`
  - `decayScore`
- 晋升到长期/语义仍交给现有 `KernelMemoryGovernanceService`。

---

## 5. 衰减与质量评估处理

不要在第一阶段伪实现 `executeMemoryDecay()`：

```java
shortTermPort.listByUser("", DEFAULT_DECAY_BATCH)
```

这个做法不能扫描全量短期记忆，因为现有 JDBC 实现按 `user_id = ?` 查询。

正确路径：

1. 新增仓储能力，例如 `ShortTermMemoryMaintenancePort.scanExpiredOrDecayed(limit)`。
2. 或在 `ShortTermMemoryPort` 增加维护型方法，但这会扩大公共端口，需要单独评审。
3. `executeMemoryDecay()` 只调用维护型端口，不通过空 userId 规避接口。

质量评估同理：

- 基础计数可以做。
- 冲突检测、偏好极性检测、画像唯一性检测不能标记为“已实现”，除非写入 `t_memory_conflict_log` 或 `t_memory_quality_snapshot`。

---

## 6. 自动配置

新增 `MemoryEnginePort` Bean：

```java
@Bean
@ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
@ConditionalOnMissingBean(MemoryEnginePort.class)
public MemoryEnginePort seahorseDefaultMemoryEnginePort(
        ShortTermMemoryPort shortTermMemoryPort,
        LongTermMemoryPort longTermMemoryPort,
        SemanticMemoryPort semanticMemoryPort,
        ObjectProvider<MemoryVectorPort> memoryVectorPort,
        ObjectMapper objectMapper) {
    return new DefaultMemoryEnginePort(
            shortTermMemoryPort,
            longTermMemoryPort,
            semanticMemoryPort,
            memoryVectorPort.getIfAvailable(MemoryVectorPort::noop),
            objectMapper);
}
```

注意 Bean 顺序：

- 该 Bean 必须早于 `KernelMemoryEngine` 和 `MemoryGovernanceServicePorts` 被解析。
- `ChatPreparationPorts` 必须注入 `MemoryEnginePort`，而不是 `KernelMemoryEngine`，保持主链路依赖端口集合的现有风格。

---

## 7. 验收标准

第一阶段完成后必须满足：

1. 预置 `t_short_term_memory` 后，聊天请求的 Prompt 中包含近期记忆。
2. 预置 `t_long_term_memory` 后，聊天请求的 Prompt 中包含长期记忆。
3. 预置 `t_semantic_memory` 后，聊天请求的 Prompt 中包含用户画像。
4. `MemoryEnginePort.loadMemory()` 抛异常时，聊天仍能正常走 KB/MCP 回答。
5. 空记忆场景下 Prompt 与现有行为基本一致。
6. 不新增数据库表，不引入新依赖。
7. 不声称已经实现向量记忆检索、仿生衰减、冲突检测、跨会话推理。

建议测试：

- `DefaultMemoryEnginePortTests`
- `KernelChatPipelineMemoryActivationTests`
- `LocalRagPromptAdapterMemoryTests`
- 自动配置 Bean 装配测试

---

## 8. 后续阶段

| 阶段 | 内容 | 前置条件 |
| --- | --- | --- |
| Phase 1 | 记忆读路径接入主链路与 Prompt | 当前文档 |
| Phase 2 | 规则版记忆写入闭环 | Phase 1 验收通过 |
| Phase 3 | QueryNormalizer / QueryOptimizer | 有可观测的检索失败样本 |
| Phase 4 | 跨会话推理 | 有稳定短期记忆数据和活跃用户扫描能力 |
| Phase 5 | 衰减、质量评估、冲突治理 | 维护型仓储端口与治理策略明确 |


# 记忆子系统改进方案：先打通主链路

> **目标**: 将四层记忆从"存储与治理 API 已存在"推进到"聊天主链路可读取、Prompt 可消费、失败可降级"。
> **原则**: 先做最小可验证闭环，避免把衰减、质量评估、向量检索、跨会话推理一次性塞进首轮改造。
> **边界**: 本文只覆盖主链路接入与基础写入闭环；QueryOptimizer、跨会话推理见 `memory-query-optimizer-cross-session-design.md`。
> **状态**: Phase 1 已完成并合并。

---

## 1. 当前事实（Phase 1 完成前）

Phase 1 实施前，聊天链路只加载会话消息历史：

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

## 2. Phase 1 范围与实施结果

Phase 1 做"读路径闭环"：

```text
用户提问
  -> loadMemory()           读取 t_message 会话历史
  -> activateMemory()       读取短期/长期/语义记忆
  -> optimizeQuery()        查询优化（Phase 3A 同步实施）
  -> rewriteQuery()
  -> resolveIntents()
  -> retrieve()
  -> streamRagResponse()
       -> PromptContext.memoryContext
       -> LocalRagPromptAdapter 注入用户记忆
```

Phase 1 明确不做：

- 不做全量记忆衰减扫描。
- 不做 MemoryVectorPort 的真实向量检索。
- 不做 LLM 摘要、事实抽取、跨会话推理。
- 不把用户原始问题无条件当作"记忆"写入。
- 不新增数据库表。

### 2.1 已实施的文件变更

**新增文件：**

| 文件 | 说明 |
| --- | --- |
| `DefaultMemoryEnginePort.java` | 编排 ShortTerm/LongTerm/Semantic 三层记忆读取 |
| `DefaultMemoryEnginePortTests.java` | 8 个测试覆盖多层加载、去重、降级 |

**修改文件：**

| 文件 | 变更 |
| --- | --- |
| `ChatPreparationPorts.java` | 增加 `MemoryEnginePort` 字段，保留旧 5 参数构造函数向后兼容 |
| `StreamChatContext.java` | 增加 `MemoryContext memoryContext` 字段 |
| `PromptContext.java` | 增加 `MemoryContext memoryContext` 字段 + `hasMemory()` 方法 |
| `KernelChatPipeline.java` | 增加 `activateMemory()` 阶段，memoryContext 传递到 PromptContext |
| `LocalRagPromptAdapter.java` | system prompt 注入用户画像/长期/近期记忆，附冲突优先级说明 |
| `SeahorseAgentKernelAutoConfiguration.java` | 装配 DefaultMemoryEnginePort bean |
| `KernelChatPipelineTests.java` | 更新 trace 断言包含 activate-memory 节点 |

---

## 3. 设计调整（已实施）

### 3.1 `MemoryEnginePort` 实现位置

实际实现位于内核侧组合实现：

```text
seahorse-agent-kernel/.../application/memory/DefaultMemoryEnginePort.java
```

职责：

- 实现 `MemoryEnginePort`。
- 组合 `ShortTermMemoryPort`、`LongTermMemoryPort`、`SemanticMemoryPort`。
- `loadMemory()` 做多层读取、限量、转换、去重。
- `writeMemory()` Phase 1 保持 no-op。
- `executeMemoryDecay()` Phase 1 不实现全量扫描。
- `assessMemoryQuality()` 返回基础计数，不声称具备冲突检测能力。

### 3.2 读取策略

`loadMemory(MemoryLoadRequest request)` 的实际行为：

| 层级 | 数据来源 | 策略 |
| --- | --- | --- |
| working | `ConversationMemoryPort` 已在 `loadMemory()` 阶段处理 | 不在 `MemoryEnginePort` 中重复加载 |
| short_term | `ShortTermMemoryPort.listByUser(userId, limit)` | 取 Top 5，沿用现有 `importance_score DESC, create_time DESC` |
| long_term | `LongTermMemoryPort.listByUser(userId, limit)` | 取 Top 3 |
| semantic | `SemanticMemoryPort.listByUser(userId, limit)` | 取 Top 10 |
| vector | `MemoryVectorPort` | Phase 1 未接入 |

转换约束：

- `MemoryRecord` 是 Java record，使用构造器。
- `MemoryItem` 使用 Lombok builder。
- `metadataJson` 使用 Jackson 序列化，不用 `Map.toString()`。
- `record.updatedAt()` 为 `Instant.EPOCH` 时兜底处理。

### 3.3 主链路集成

实际 `ChatPreparationPorts` 签名：

```java
public record ChatPreparationPorts(
        ConversationMemoryPort memoryPort,
        MemoryEnginePort memoryEnginePort,
        QueryOptimizerPort queryOptimizerPort,
        QueryRewritePort queryRewritePort,
        IntentResolutionPort intentResolutionPort,
        IntentGuidancePort intentGuidancePort,
        RetrievalContextPort retrievalContextPort) {
}
```

兼容：保留旧 5 参数构造函数委托到新 7 参数构造函数。

`StreamChatContext` 实际字段：

```java
private String originalQuestion;                    // 用户原始输入
private MemoryContext memoryContext;                 // 四层记忆上下文
private QueryOptimizationResult queryOptimizationResult;  // 查询优化结果
```

`PromptContext` 实际字段：

```java
private MemoryContext memoryContext;

public boolean hasMemory() {
    return memoryContext != null
            && (notEmpty(memoryContext.getShortTermMemories())
            || notEmpty(memoryContext.getLongTermMemories())
            || notEmpty(memoryContext.getSemanticMemories()));
}
```

### 3.4 Prompt 消费

`LocalRagPromptAdapter` 在 system prompt 中追加记忆区块：

```text
用户记忆上下文：
用户画像：
- ...
长期记忆：
- ...
近期记忆：
- ...
注意：若用户记忆与知识库上下文冲突，以知识库上下文为准，除非问题明确询问用户偏好或历史。
```

约束：

- 每层限制：语义 10、长期 3、短期 5（由 DefaultMemoryEnginePort 控制）。
- 单条内容截断 200 字符。
- 不暴露 `metadataJson`。

---

## 4. 第二阶段：写入闭环

Phase 1 不无条件写入用户原始问题。第二阶段写入策略：

| 写入对象 | 是否写入 | 说明 |
| --- | --- | --- |
| 用户原始问题 | 默认不写 | 避免把噪声写进短期记忆 |
| 助手完整回答 | 默认不写 | 容易造成模型自我污染 |
| 对话摘要 | 可写 | 需要基于完整问答生成摘要 |
| 明确事实 | 可写 | 如"用户使用 Java 21" |
| 明确偏好 | 可写 | 如"用户偏好中文回答" |

实现建议：

- 用 `StreamCallback` 装饰器收集助手输出，在 `onComplete()` 后异步触发候选记忆提取。
- 提取器可以先做规则版：只识别明确的用户偏好/事实标记，不调用 LLM。
- 写入 `ShortTermMemoryPort` 时必须设置：`userId`、`conversationId`、`importanceScore`、`confidenceLevel`、`sourceMessageIds`、`decayScore`。
- 晋升到长期/语义仍交给现有 `KernelMemoryGovernanceService`。

---

## 5. 衰减与质量评估处理

Phase 1 不伪实现 `executeMemoryDecay()`。正确路径：

1. 新增仓储能力，例如 `ShortTermMemoryMaintenancePort.scanExpiredOrDecayed(limit)`。
2. 或在 `ShortTermMemoryPort` 增加维护型方法，但这会扩大公共端口，需要单独评审。
3. `executeMemoryDecay()` 只调用维护型端口，不通过空 userId 规避接口。

质量评估同理：

- 基础计数可以做。
- 冲突检测、偏好极性检测、画像唯一性检测不能标记为"已实现"，除非写入 `t_memory_conflict_log` 或 `t_memory_quality_snapshot`。

---

## 6. 自动配置（已实施）

实际 `DefaultMemoryEnginePort` Bean：

```java
@Bean
@ConditionalOnBean({ShortTermMemoryPort.class, LongTermMemoryPort.class, SemanticMemoryPort.class})
@ConditionalOnMissingBean(MemoryEnginePort.class)
public MemoryEnginePort seahorseDefaultMemoryEnginePort(
        ShortTermMemoryPort shortTermMemoryPort,
        LongTermMemoryPort longTermMemoryPort,
        SemanticMemoryPort semanticMemoryPort,
        ObjectProvider<ObjectMapper> objectMapperProvider) {
    ObjectMapper objectMapper = objectMapperProvider.getIfAvailable(ObjectMapper::new);
    return new DefaultMemoryEnginePort(
            shortTermMemoryPort,
            longTermMemoryPort,
            semanticMemoryPort,
            objectMapper);
}
```

注意：`ObjectMapper` 使用 `ObjectProvider<ObjectMapper>` + fallback `ObjectMapper::new`，避免无 Jackson 自动配置时启动失败。

---

## 7. 验收标准（已满足）

| # | 标准 | 状态 |
| --- | --- | --- |
| 1 | 预置 `t_short_term_memory` 后，聊天 Prompt 包含近期记忆 | ✅ |
| 2 | 预置 `t_long_term_memory` 后，聊天 Prompt 包含长期记忆 | ✅ |
| 3 | 预置 `t_semantic_memory` 后，聊天 Prompt 包含用户画像 | ✅ |
| 4 | `MemoryEnginePort.loadMemory()` 抛异常时，聊天仍能走 KB/MCP 回答 | ✅ |
| 5 | 空记忆场景下 Prompt 与现有行为基本一致 | ✅ |
| 6 | 不新增数据库表，不引入新依赖 | ✅ |
| 7 | 不声称已实现向量记忆检索、仿生衰减、冲突检测、跨会话推理 | ✅ |

测试覆盖：

- `DefaultMemoryEnginePortTests` — 8 个测试
- `KernelChatPipelineTests` — 3 个测试（含 activate-memory trace 节点）
- `SeahorseAgentKernelAutoConfigurationTests` — 17 个测试

---

## 8. 阶段状态

| 阶段 | 内容 | 状态 |
| --- | --- | --- |
| Phase 1 | 记忆读路径接入主链路与 Prompt | ✅ 已完成 |
| Phase 2 | 规则版记忆写入闭环 | 待实施 |
| Phase 3A | QueryNormalizer | ✅ 已完成（见 design doc） |
| Phase 3B | LLM QueryOptimizer | ✅ 已完成，默认关闭（见 design doc） |
| Phase 4A | 规则版候选记忆提取 | ✅ 已完成，默认关闭（见 design doc） |
| Phase 4B | 跨会话推理基础设施 | ✅ 已完成，默认关闭（见 design doc） |
| Phase 5 | 衰减、质量评估、冲突治理 | 待实施 |

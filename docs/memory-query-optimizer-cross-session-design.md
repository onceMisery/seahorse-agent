# QueryOptimizer 与跨会话推理设计

> **定位**: 记忆主链路接入后的上层智能能力，不属于第一阶段必做项。
> **前置依赖**: `memory-system-improvement-plan.md` 的 Phase 1 已验收，聊天 Prompt 已能稳定消费 `MemoryContext`。
> **原则**: 默认先做确定性、低延迟、可观测的能力；LLM 优化与跨会话推理必须显式开关、可降级、可回滚。
> **状态**: Phase 3A/3B/4A/4B 均已完成并合并。

---

## 1. 分阶段结论

不要一次性实现 LLM QueryOptimizer、Feature 扩展点、跨会话推理定时任务。

推荐顺序：

| 阶段 | 名称 | 目标 | 是否默认开启 | 状态 |
| --- | --- | --- | --- | --- |
| Phase 3A | QueryNormalizer | 术语映射、专有名词保护、保留查询状态 | 是 | ✅ 已完成 |
| Phase 3B | LLM QueryOptimizer | 指代消解、多语言保护、复杂查询修正 | 否 | ✅ 已完成 |
| Phase 4A | 规则版候选记忆提取 | 从完整对话中提取明确事实/偏好 | 否 | ✅ 已完成 |
| Phase 4B | 跨会话推理 | 批量分析短期记忆，写入长期/语义记忆 | 否 | ✅ 已完成 |

核心约束：

- `QueryRewritePort` 已经承担"结合历史改写并拆分"职责，不能无依据再加一次默认 LLM 调用。
- `QueryOptimizerPort` 和 `QueryOptimizationFeature` 不应同时作为 owner。第一版只保留端口，不引入 Feature。
- 不能直接覆盖 `context.question`，必须保留原始问题、优化问题、改写问题三段状态。
- 跨会话推理必须解决活跃用户扫描、幂等写入、`semanticKey`、冲突治理后才能自动运行。

---

## 2. QueryNormalizer（Phase 3A）— 已完成

### 2.1 目标

用确定性逻辑解决低成本问题：

- 术语映射：如 "消息队列" -> "MQ / Pulsar / Kafka"。
- 专有名词保护：如 `911`、`HNSW`、`pgvector`、`MCP` 不被后续改写破坏。
- 查询状态可观测：debug log 中能看到 protectedTerms / expandedTerms。

不做：

- 不调用 LLM。
- 不做复杂指代消解。
- 不做时间解析。
- 不做跨会话推理。

### 2.2 领域对象

已新增：

```text
kernel/domain/chat/QueryOptimizationResult.java
```

实际字段：

```java
public record QueryOptimizationResult(
        String originalQuestion,
        String optimizedQuestion,
        Map<String, String> protectedTerms,
        List<String> expandedTerms,
        List<String> appliedRules
) {
}
```

语义：

- `originalQuestion`: 用户原始输入，不可丢。
- `optimizedQuestion`: 给 `QueryRewritePort` 使用的输入。Phase 3A 中与 originalQuestion 相同。
- `protectedTerms`: 后续 rewrite prompt 必须尊重的保护词。Phase 3A 中通过 debug log 输出，不被 rewriteQuery 消费。
- `expandedTerms`: 检索侧可选消费，不应直接污染用户问题。Phase 3A 中通过 debug log 输出。
- `appliedRules`: debug log 使用。

### 2.3 端口

已新增：

```text
kernel/ports/outbound/chat/QueryOptimizerPort.java
```

```java
public interface QueryOptimizerPort {

    QueryOptimizationResult optimize(String originalQuestion,
                                     List<ChatMessage> history,
                                     MemoryContext memoryContext);

    static QueryOptimizerPort passthrough() {
        return (question, history, memoryContext) -> new QueryOptimizationResult(
                Objects.requireNonNullElse(question, ""),
                Objects.requireNonNullElse(question, ""),
                Map.of(),
                List.of(),
                List.of("passthrough"));
    }
}
```

实际实现：

```text
kernel/application/chat/RuleBasedQueryOptimizerPort.java
```

### 2.4 术语映射接入

已新增独立出站端口：

```text
kernel/ports/outbound/mapping/QueryTermExpansionPort.java
```

```java
public interface QueryTermExpansionPort {
    Map<String, List<String>> expand(String queryText);
    static QueryTermExpansionPort noop() { return queryText -> Map.of(); }
}
```

当前无 JDBC 实现，默认退化为 noop。需要后续实现 `JdbcQueryTermExpansionAdapter` 才能让术语映射真正生效。

### 2.5 Pipeline 集成

`StreamChatContext` 已增加字段：

```java
private String originalQuestion;                    // 用户原始输入
private QueryOptimizationResult queryOptimizationResult;  // 查询优化结果
```

构建上下文时 `originalQuestion` 自动从 `question` 设置。

`KernelChatPipeline.execute()` 实际顺序：

```text
loadMemory
activateMemory
optimizeQuery
rewriteQuery
resolveIntents
handleGuidance
handleSystemOnly
retrieve
handleEmptyRetrieval
streamRagResponse
```

`optimizeQuery()` 规则：

- 输入使用 `context.getOriginalQuestion()`，不用已被改写过的字段。
- 输出存入 `context.queryOptimizationResult`。
- `rewriteQuery()` 通过 `resolveRewriteInput()` 使用 `optimizedQuestion` 作为输入。
- `ConversationMemoryPort.loadAndAppend()` 仍保存原始用户问题。

### 2.6 保护词如何生效

Phase 3A 行为边界：`protectedTerms` 和 `expandedTerms` 通过 `LOG.debug` 输出，不进入 `KernelRagTraceRecorder` 节点数据，不被 `rewriteQuery` 消费。

可选路径：

- 在 `QueryRewritePort` 的实现 prompt 中加入保护词列表。
- 或新增 `QueryRewriteRequest`，包含 `question/history/protectedTerms/expandedTerms`，但这是接口改造，需单独评审。

当前状态：

- Phase 3A 不声称"多语言保护已实现"。
- 需要 Phase 3B（LLM 优化器）或 QueryRewritePort 实现改造才能让保护词和扩展词真正生效。

---

## 3. LLM QueryOptimizer（Phase 3B）— 已完成，默认关闭

### 3.1 启用条件

已实现，通过配置开关控制：

```properties
seahorse-agent.query-optimizer.llm-enabled=false
```

### 3.2 实际实现

已新增：

```text
seahorse-agent-adapter-ai-openai-compatible/.../LlmQueryOptimizerAdapter.java
seahorse-agent-adapter-ai-openai-compatible/src/main/resources/prompt/query-optimizer.st
```

降级策略：LLM 超时、解析失败或置信度低于 0.6 时返回 passthrough 结果。

自动配置装配：

```java
@Bean
@ConditionalOnBean(ChatModelPort.class)
@ConditionalOnProperty(prefix = "seahorse-agent.query-optimizer", name = "llm-enabled", havingValue = "true")
@ConditionalOnMissingBean(QueryOptimizerPort.class)
public QueryOptimizerPort seahorseLlmQueryOptimizer(...) { ... }
```

当 `llm-enabled=false` 时，自动退化为 `RuleBasedQueryOptimizerPort`。

### 3.3 LLM 输出约束

LLM 输出必须包含：

```json
{
  "optimizedQuestion": "...",
  "protectedTerms": { "HNSW": "technical_term" },
  "expandedTerms": ["..."],
  "confidence": 0.82,
  "changed": true
}
```

落地规则：

- `confidence < 0.6` 时不替换查询，只记录候选。
- `optimizedQuestion` 为空或过长时降级。
- 不允许 LLM 删除用户原始实体。

---

## 4. 跨会话推理（Phase 4）— 已完成，默认关闭

### 4.1 目标

跨会话推理不是实时聊天步骤。它的目标是把多次对话中的稳定事实、偏好、画像提取成长期/语义记忆。

正确方向：

```text
短期记忆积累
  -> 治理任务扫描用户近期短期记忆
  -> 提取候选事实/偏好/画像
  -> 校验置信度、冲突、幂等
  -> 写入 long_term 或 semantic
```

不要在每次 `streamChat` 后同步调用 LLM 推理。

### 4.2 实际实现

已集成到 `KernelMemoryGovernanceService`，通过配置开关控制：

```properties
seahorse-agent.memory.inference-enabled=false
seahorse-agent.memory.inference.confidence-threshold=0.7
```

已新增：

```text
kernel/ports/outbound/memory/MemoryInferencePort.java
kernel/domain/memory/InferredMemory.java
kernel/application/memory/RuleBasedMemoryCandidateExtractor.java
```

`KernelMemoryGovernanceService.runGovernance()` 在 `inferenceEnabled=true` 时调用 `MemoryInferencePort.infer()`。

### 4.3 活跃用户扫描

当前推理集成在 `runGovernance(userId)` 中，由管理 API 或定时任务按用户触发。自动全用户扫描需要 `UserRepositoryPort` 提供活跃用户分页，尚未实现。

### 4.4 写入语义记忆

`InferredMemory` 必须提供 `semanticKey`。当前 `RuleBasedMemoryCandidateExtractor` 生成的 semanticKey 格式：

| 类型 | semanticKey 示例 |
| --- | --- |
| PROFILE | `profile:后端工程师` |
| PREFERENCE | 无 semanticKey（写入 long_term） |

### 4.5 `isUserMessage` 逻辑

`RuleBasedMemoryCandidateExtractor.isUserMessage()` 检查逻辑：

```java
Object role = record.metadata().get("role");
if (role != null) {
    return "user".equalsIgnoreCase(role.toString());
}
// metadata 中无 role 字段时，按 type 兜底
return "CONVERSATION".equalsIgnoreCase(record.type());
```

当 `role` 存在时直接按 `role` 判定，避免 assistant CONVERSATION 被误当用户消息。

### 4.6 置信度阈值

| 来源 | 置信度 | 阈值 | 结果 |
| --- | --- | --- | --- |
| 规则提取 PROFILE | 0.75D | 0.7D | ✅ 通过 |
| 规则提取 PREFERENCE | 0.7D | 0.7D | ✅ 通过 |
| LLM 推理（未来） | 可配置 | 0.7D | 待实现 |

---

## 5. 文件清单（已实施）

### Phase 3A：QueryNormalizer

已新增：

| 文件 | 说明 |
| --- | --- |
| `kernel/ports/outbound/chat/QueryOptimizerPort.java` | 查询优化端口 |
| `kernel/domain/chat/QueryOptimizationResult.java` | 优化结果 record |
| `kernel/ports/outbound/mapping/QueryTermExpansionPort.java` | 术语扩展端口 |
| `kernel/application/chat/RuleBasedQueryOptimizerPort.java` | 规则版实现 |
| `seahorse-agent-tests/.../RuleBasedQueryOptimizerPortTests.java` | 8 个测试 |

已修改：

| 文件 | 改动 |
| --- | --- |
| `ChatPreparationPorts.java` | 增加 `QueryOptimizerPort`，7 参数构造函数 |
| `StreamChatContext.java` | 增加 `originalQuestion` 和 `queryOptimizationResult` |
| `KernelChatPipeline.java` | 增加 `optimizeQuery()` + `resolveRewriteInput()` |
| `SeahorseAgentKernelAutoConfiguration.java` | 装配 RuleBasedQueryOptimizerPort |

### Phase 3B：LLM QueryOptimizer

已新增：

| 文件 | 说明 |
| --- | --- |
| `adapter-ai-openai-compatible/.../LlmQueryOptimizerAdapter.java` | LLM 实现，默认关闭 |
| `adapter-ai-openai-compatible/.../resources/prompt/query-optimizer.st` | Prompt 模板 |

已修改：

| 文件 | 改动 |
| --- | --- |
| `SeahorseAgentKernelAutoConfiguration.java` | 装配 LLM optimizer，`@ConditionalOnProperty` 门控 |

### Phase 4：跨会话推理

已新增：

| 文件 | 说明 |
| --- | --- |
| `kernel/ports/outbound/memory/MemoryInferencePort.java` | 推理端口 |
| `kernel/domain/memory/InferredMemory.java` | 推理候选结果 |
| `kernel/application/memory/RuleBasedMemoryCandidateExtractor.java` | 规则版实现 |

已修改：

| 文件 | 改动 |
| --- | --- |
| `KernelMemoryGovernanceService.java` | 增加 `inferenceEnabled` 标志，`runGovernance()` 调用推理 |
| `MemoryGovernanceServicePorts.java` | 增加 `MemoryInferencePort`，5 参数构造函数 |
| `MemoryGovernanceRunResult.java` | 增加 `inferredCount` 字段 |
| `SeahorseAgentKernelAutoConfiguration.java` | 装配 RuleBasedMemoryCandidateExtractor + inference-enabled 配置 |

---

## 6. 验收标准（已满足）

### QueryNormalizer

| # | 标准 | 状态 |
| --- | --- | --- |
| 1 | 原始问题不会丢失（originalQuestion 三段状态） | ✅ |
| 2 | `rewriteQuery()` 使用优化后的问题 | ✅ |
| 3 | debug log 中能看到 protectedTerms / expandedTerms | ✅ |
| 4 | 没有术语映射命中时行为等同 passthrough | ✅ |
| 5 | 规则实现不调用 LLM | ✅ |

### LLM QueryOptimizer

| # | 标准 | 状态 |
| --- | --- | --- |
| 1 | 默认关闭（`llm-enabled=false`） | ✅ |
| 2 | 超时或解析失败时返回 passthrough | ✅ |
| 3 | 短指代问题不会被错误跳过 | ✅ |
| 4 | 低置信度结果不替换查询（threshold 0.6） | ✅ |
| 5 | 保护词通过 debug log 输出，不宣称保护生效 | ✅ |

### 跨会话推理

| # | 标准 | 状态 |
| --- | --- | --- |
| 1 | 推理集成在 `runGovernance(userId)` 中 | ✅ |
| 2 | 推理结果写入长期记忆 | ✅ |
| 3 | 写入语义记忆必须提供 `semanticKey` | ✅ |
| 4 | 置信度 < 0.7 不写入 | ✅ |
| 5 | 默认关闭（`inference-enabled=false`） | ✅ |


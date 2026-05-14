# QueryOptimizer 与跨会话推理设计

> **定位**: 记忆主链路接入后的上层智能能力，不属于第一阶段必做项。
> **前置依赖**: `memory-system-improvement-plan.md` 的 Phase 1 已验收，聊天 Prompt 已能稳定消费 `MemoryContext`。
> **原则**: 默认先做确定性、低延迟、可观测的能力；LLM 优化与跨会话推理必须显式开关、可降级、可回滚。

---

## 1. 分阶段结论

不要一次性实现 LLM QueryOptimizer、Feature 扩展点、跨会话推理定时任务。

推荐顺序：

| 阶段 | 名称 | 目标 | 是否默认开启 |
| --- | --- | --- | --- |
| Phase 3A | QueryNormalizer | 术语映射、专有名词保护、保留查询状态 | 是 |
| Phase 3B | LLM QueryOptimizer | 指代消解、多语言保护、复杂查询修正 | 否 |
| Phase 4A | 规则版候选记忆提取 | 从完整对话中提取明确事实/偏好 | 否 |
| Phase 4B | 跨会话推理 | 批量分析短期记忆，写入长期/语义记忆 | 否 |

核心约束：

- `QueryRewritePort` 已经承担“结合历史改写并拆分”职责，不能无依据再加一次默认 LLM 调用。
- `QueryOptimizerPort` 和 `QueryOptimizationFeature` 不应同时作为 owner。第一版只保留端口，不引入 Feature。
- 不能直接覆盖 `context.question`，必须保留原始问题、优化问题、改写问题三段状态。
- 跨会话推理必须解决活跃用户扫描、幂等写入、`semanticKey`、冲突治理后才能自动运行。

---

## 2. QueryNormalizer（Phase 3A）

### 2.1 目标

用确定性逻辑解决低成本问题：

- 术语映射：如 “消息队列” -> “MQ / Pulsar / Kafka”。
- 专有名词保护：如 `911`、`HNSW`、`pgvector`、`MCP` 不被后续改写破坏。
- 查询状态可观测：trace 中能看到 raw / normalized / rewritten。

不做：

- 不调用 LLM。
- 不做复杂指代消解。
- 不做时间解析。
- 不做跨会话推理。

### 2.2 领域对象

新增：

```text
kernel/domain/chat/QueryOptimizationResult.java
```

建议字段：

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
- `optimizedQuestion`: 给 `QueryRewritePort` 使用的输入。
- `protectedTerms`: 后续 rewrite prompt 必须尊重的保护词。
- `expandedTerms`: 检索侧可选消费，不应直接污染用户问题。
- `appliedRules`: trace/debug 使用。

### 2.3 端口

新增：

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

第一版实现建议命名：

```text
kernel/application/chat/RuleBasedQueryOptimizerPort.java
```

不要放在 OpenAI adapter 中。它不依赖 LLM，也不是模型适配器。

### 2.4 术语映射接入

现有项目已有 `QueryTermMappingRepositoryPort`，但它主要面向管理分页，不是检索时的高效匹配接口。

推荐两种方式，二选一：

1. 新增轻量查询方法到 `QueryTermMappingRepositoryPort`：

```java
List<QueryTermMappingRecord> findEnabledByKeyword(String keyword, int limit);
```

2. 新增独立出站端口：

```java
QueryTermExpansionPort.expand(String question);
```

第二种更干净，避免管理分页仓储和在线查询耦合。

第一阶段不要把术语映射表整表塞给 LLM。

### 2.5 Pipeline 集成

`StreamChatContext` 增加字段：

```java
private String originalQuestion;
private QueryOptimizationResult queryOptimizationResult;
```

构建上下文时：

```text
originalQuestion = question
question = originalQuestion
```

`KernelChatPipeline.execute()` 顺序：

```text
loadMemory
activateMemory
optimizeQuery
rewriteQuery
resolveIntents
...
```

`optimizeQuery()` 规则：

- 输入使用 `context.getOriginalQuestion()`，不要用已经被改写过的字段。
- 输出存入 `context.queryOptimizationResult`。
- `rewriteQuery()` 使用 `optimizedQuestion` 作为输入。
- `ConversationMemoryPort.loadAndAppend()` 仍保存原始用户问题，不能保存优化后的内部查询。

伪流程：

```java
private void optimizeQuery(StreamChatContext context) {
    QueryOptimizationResult result = preparationPorts.queryOptimizerPort().optimize(
            context.getOriginalQuestion(),
            safeHistory(context),
            context.getMemoryContext());
    context.setQueryOptimizationResult(result);
}

private void rewriteQuery(StreamChatContext context) {
    String input = Optional.ofNullable(context.getQueryOptimizationResult())
            .map(QueryOptimizationResult::optimizedQuestion)
            .filter(q -> !q.isBlank())
            .orElse(context.getOriginalQuestion());
    RewriteResult rewriteResult = preparationPorts.queryRewritePort()
            .rewriteWithSplit(input, safeHistory(context));
    context.setRewriteResult(rewriteResult);
}
```

### 2.6 保护词如何生效

只把 `protectedTerms` 存在结果对象里不够，后续必须消费它。

可选路径：

- 在 `QueryRewritePort` 的实现 prompt 中加入保护词列表。
- 或新增 `QueryRewriteRequest`，包含 `question/history/protectedTerms/expandedTerms`，但这是接口改造，需单独评审。

短期建议：

- 先在 trace 中记录 `protectedTerms`。
- 若当前 `QueryRewritePort` 实现无法消费保护词，则不要声称“多语言保护已实现”。

---

## 3. LLM QueryOptimizer（Phase 3B）

### 3.1 启用条件

只有满足以下条件才做 LLM 版：

- 已有可复现样本证明 `QueryRewritePort` 无法处理指代/多语言保护。
- 有指标可衡量优化前后检索召回或用户反馈变化。
- 有超时、降级、开关和 trace。

配置建议：

```properties
seahorse-agent.query-optimizer.llm-enabled=false
seahorse-agent.query-optimizer.timeout-ms=1500
seahorse-agent.query-optimizer.max-history-chars=500
seahorse-agent.query-optimizer.max-memory-chars=300
```

### 3.2 设计修正

原设计中 `isSimpleQuery()` 不能按“长度小于 10”直接跳过。短问题往往最需要指代消解，例如：

- “这个呢”
- “怎么做”
- “那上次的呢”

正确的跳过逻辑应基于风险：

| 条件 | 动作 |
| --- | --- |
| 无历史、无记忆、无术语映射命中、无指代词 | 跳过 |
| 有指代词 | 不跳过 |
| 有保护词风险 | 不跳过或至少走规则保护 |
| LLM 超时/失败 | 返回 passthrough |

### 3.3 LLM 输出约束

LLM 输出必须包含：

```json
{
  "optimizedQuestion": "...",
  "protectedTerms": {
    "HNSW": "technical_term"
  },
  "expandedTerms": ["..."],
  "confidence": 0.82,
  "changed": true
}
```

落地规则：

- `confidence < 0.6` 时不替换查询，只记录候选。
- `optimizedQuestion` 为空或过长时降级。
- 不允许 LLM 删除用户原始实体。
- 不允许 LLM 直接增加未在历史、记忆、术语映射中出现的事实性约束。

---

## 4. 跨会话推理（Phase 4）

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

### 4.2 不建议单独新建重复 Job

当前已有 `KernelMemoryGovernanceService` 和 `SeahorseMemoryGovernanceJob`。跨会话推理应优先作为记忆治理的一部分，而不是另起一个并行治理系统。

建议新增治理端口：

```text
kernel/ports/outbound/memory/MemoryInferencePort.java
```

```java
public interface MemoryInferencePort {
    List<InferredMemory> infer(String userId,
                               List<MemoryRecord> shortTermMemories,
                               List<MemoryRecord> semanticMemories);

    static MemoryInferencePort noop() {
        return (userId, shortTerm, semantic) -> List.of();
    }
}
```

然后由 `KernelMemoryGovernanceService` 在显式配置开启时调用。

配置建议：

```properties
seahorse-agent.memory.inference-enabled=false
seahorse-agent.memory.inference.min-short-term-count=5
seahorse-agent.memory.inference.confidence-threshold=0.7
```

### 4.3 活跃用户扫描是前置条件

自动跨会话推理必须先解决“扫描哪些用户”。

可选策略：

- `UserRepositoryPort` 提供活跃用户分页。
- `ShortTermMemoryMaintenancePort` 提供近期有短期记忆的 userId 分页。
- 管理 API 手动触发单用户治理。

在没有这个能力前，不能声称定时任务闭环完成。

### 4.4 写入长期记忆

写入 `LongTermMemoryPort` 时必须带：

- `userId`
- `sourceType = inferred`
- `sourceIds`
- `importanceScore`
- `confidenceLevel`
- `reasoning`

长期记忆可以追加，但需要幂等策略：

- 用内容 hash 或 `(userId, type, normalizedContent)` 去重。
- 或先查询近期相同内容，避免重复插入。

### 4.5 写入语义记忆

写入 `SemanticMemoryPort` 必须提供 `semanticKey`。当前 JDBC 实现缺少 `semanticKey` 会直接失败。

建议 key 规则：

| 类型 | semanticKey 示例 |
| --- | --- |
| PROFILE | `profile:role`、`profile:tech_stack` |
| PREFERENCE | `preference:language`、`preference:answer_style` |
| FACT | 默认不写 semantic，先写 long_term |

语义记忆写入必须处理冲突：

- 新旧内容一致：更新置信度和 sourceIds。
- 新旧内容互斥：写入 `t_memory_conflict_log`，不直接覆盖。
- 新内容置信度低：丢弃或进入待审核状态。

### 4.6 LLM 推理输出

建议输出：

```json
[
  {
    "targetLayer": "semantic",
    "semanticKey": "profile:tech_stack",
    "type": "PROFILE",
    "content": "用户主要使用 Java 开发后端服务",
    "confidence": 0.84,
    "sourceIds": ["..."],
    "reasoning": "多条短期记忆均提到 Java 后端开发"
  }
]
```

落地规则：

- `semantic` 目标必须有 `semanticKey`。
- `confidence < threshold` 不写入。
- `sourceIds` 为空不写入。
- `content` 不能包含“可能、也许、大概”等低确定性措辞，除非目标层是待审核。

---

## 5. 文件清单

### Phase 3A：QueryNormalizer

新增：

| 文件 | 说明 |
| --- | --- |
| `QueryOptimizerPort.java` | 查询优化端口，第一版 passthrough + 规则实现 |
| `QueryOptimizationResult.java` | 保存 raw/optimized/protected/expanded/appliedRules |
| `RuleBasedQueryOptimizerPort.java` | 内核侧规则实现 |

修改：

| 文件 | 改动 |
| --- | --- |
| `ChatPreparationPorts.java` | 增加 `QueryOptimizerPort` |
| `StreamChatContext.java` | 增加 `originalQuestion` 和 `queryOptimizationResult` |
| `KernelChatPipeline.java` | 增加 `optimizeQuery()`，`rewriteQuery()` 使用 optimized 输入 |
| `SeahorseAgentKernelAutoConfiguration.java` | 装配规则版 `QueryOptimizerPort` |

不新增：

- 不新增 `QueryOptimizationFeature`。
- 不新增 OpenAI adapter 实现。
- 不新增 Prompt 模板。

### Phase 3B：LLM QueryOptimizer

仅在 Phase 3A 验收后新增：

| 文件 | 说明 |
| --- | --- |
| `LlmQueryOptimizerAdapter.java` | LLM 实现，默认关闭 |
| `query-optimizer.st` | Prompt 模板 |

### Phase 4：跨会话推理

仅在写入闭环和活跃用户扫描可用后新增：

| 文件 | 说明 |
| --- | --- |
| `MemoryInferencePort.java` | 治理侧推理端口 |
| `InferredMemory.java` | 推理候选结果，必须含 `semanticKey` |
| `LlmMemoryInferenceAdapter.java` | LLM 推理实现，默认关闭 |
| `memory-inference.st` | Prompt 模板 |

优先修改：

| 文件 | 改动 |
| --- | --- |
| `KernelMemoryGovernanceService.java` | 在配置开启时调用 `MemoryInferencePort` |
| `MemoryGovernanceServicePorts.java` | 增加 `MemoryInferencePort` |
| `SeahorseAgentKernelAutoConfiguration.java` | 装配 noop/LLM 推理端口 |

不建议新增：

- 不建议新增独立 `SeahorseCrossSessionReasoningJob`，除非后续证明治理任务需要拆分调度。

---

## 6. 验收标准

### QueryNormalizer

1. 原始问题不会丢失。
2. `rewriteQuery()` 使用优化后的问题。
3. trace 中能看到原始问题、优化问题、应用规则。
4. 没有术语映射命中时行为等同 passthrough。
5. 规则实现不调用 LLM。

### LLM QueryOptimizer

1. 默认关闭。
2. 超时或解析失败时返回 passthrough。
3. 短指代问题不会被错误跳过。
4. 低置信度结果不替换查询。
5. 保护词被后续 rewrite 消费，否则只记录不宣称保护生效。

### 跨会话推理

1. 有明确活跃用户扫描来源。
2. 推理结果写入长期记忆具备幂等策略。
3. 写入语义记忆必须提供 `semanticKey`。
4. 冲突不直接覆盖，进入冲突日志或待审核。
5. 默认关闭，可按用户或配置灰度启用。


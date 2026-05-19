# Phase D 实施计划：输出可信度治理（自愈重试 / RAG Evaluator / 视图切片）

> 该计划独立于 Phase A/B/E，但与它们组合使用收益更高。
> 核心目标：把 LLM 输出的不可信片段（坏 JSON / 错 Mermaid / 噪声 RAG 上下文 / 巨型 JSON）治理为可消费的产物。

## Goal

在 kernel 中引入三组治理组件：
1. **Output Validator + Self-Healing Loop**：对结构化输出（JSON / Mermaid / DDL）做语法校验并触发 LLM 反哺重试。
2. **RAG Evaluator**：检索结果在喂给主模型前，先由廉价模型打分过滤，避免"陈旧规范污染"。
3. **Context Reducer / View Slicing**：长 JSON / 长 RAG 上下文按下游消费者意图降维，缩小 token 与噪声。

## Architecture

```
┌─────────────── 输出治理（产出后） ───────────────┐
│ LLM 流式输出 → OutputValidatorPort.validate()    │
│   ├─ ok → 直通 SSE                              │
│   └─ fail → SelfHealingLoop.retry(prompt+error) │
└──────────────────────────────────────────────────┘

┌─────────────── 检索治理（喂模型前） ───────────────┐
│ RetrievedChunks → RagEvaluatorPort.score()        │
│   ├─ score >= threshold → 进入 Prompt              │
│   └─ score <  threshold → 丢弃 + 记 trace          │
└────────────────────────────────────────────────────┘

┌─────────────── 上下文降维（跨阶段） ───────────────┐
│ Phase N 全量 JSON → ContextReducerPort.reduce()    │
│   按下游意图返回摘要视图（API 树 / 表名清单 / etc.) │
└────────────────────────────────────────────────────┘
```

## Tech Stack

- Java 17
- 校验：Jackson `ObjectMapper.readTree()`、Mermaid 用括号 / 引号配对的轻量解析（不引入 mermaid-java）
- 评分：复用 `ChatModelPort`（廉价模型）+ Prompt 模板
- 降维：纯 Java + Jackson Tree

## Baseline / Authority Refs

- `@C:/user-data/code/ai/seahorse-agent/docs/agent-vs-rag-capability-baseline.md` — Phase D
- `@C:/user-data/code/ai/seahorse-agent/自定义AGENT接入.md` — 模块二 / 三 / 一 草案
- `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/retrieval/KernelRetrievalPostProcessorChain.java` — Evaluator 接入点
- `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/retrieval/SearchResultPostProcessorFeature.java` — Feature 扩展

## Compatibility Boundary

**必须稳定**：
- 现有 SSE 协议、`StreamCallback` 事件类型
- `KernelChatPipeline` 主链路
- `SearchResultPostProcessorFeature` SPI

**允许变化**：
- 新增包 `kernel/application/output/`、`kernel/application/eval/`、`kernel/application/context/`
- `kernel/feature/retrieval/RagEvaluatorPostProcessorFeature.java` 作为新 PostProcessor 注册点（可选）

## Verification

```bash
mvn -pl seahorse-agent-tests -am -Dtest='*Validator*,*SelfHealing*,*RagEvaluator*,*ContextReducer*' test
```

## Risks / Rollback

| 风险 | 缓解 |
|---|---|
| Validator 误判把合法 JSON 当坏的 | Validator 失败仅 warn + log，不强制重试；`maxRetries=0` 时回退 |
| 评估模型贵 / 慢 | 仅在通道返回 chunks > N（默认 5）时启用；提供 `seahorse-agent.rag.evaluator.enabled` 开关 |
| Reducer 把关键字段切掉 | 视图模板对应业务 DTO，一类一类加，不做"通用通吃" |

## Retirement

无。Phase D 全部新增；不替换既有路径。

---

## File Map

**新建**：
- `kernel/ports/outbound/output/OutputValidatorPort.java`
- `kernel/application/output/JsonOutputValidator.java`
- `kernel/application/output/MermaidOutputValidator.java`
- `kernel/application/output/SelfHealingLoop.java`
- `kernel/application/output/SelfHealingOptions.java`
- `kernel/ports/outbound/eval/RagEvaluatorPort.java`
- `kernel/application/eval/LlmRagEvaluator.java`
- `kernel/feature/retrieval/RagEvaluatorPostProcessorFeature.java`
- `kernel/ports/outbound/context/ContextReducerPort.java`
- `kernel/application/context/JsonViewSliceReducer.java`
- 测试 11 个

**修改**：
- `seahorse-agent-spring-boot-starter` — 三个自动装配条件 Bean
- `application.properties` — 三个开关默认值

---

## Tasks

### Task D1 — OutputValidatorPort 出站契约

**Files**:
- create: `kernel/ports/outbound/output/OutputValidatorPort.java`
- create test: `seahorse-agent-tests/.../ports/outbound/output/OutputValidatorPortContractTests.java`

**Why**: 抽象不同输出格式校验，可被 SelfHealingLoop 与未来 PostProcessor 复用。

**Compatibility**: 新增。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=OutputValidatorPortContractTests test`

**Steps**:
1. **Write test** —
   - `OutputValidationResult.ok()` / `OutputValidationResult.invalid("expected }",2,15)` 字段；
   - `OutputValidatorPort.alwaysOk()` 返回 ok；
   - `OutputValidatorPort.failing("bad")` 返回 invalid。
2. **Verify RED** — 失败。
3. **Minimal code** — `interface OutputValidatorPort { OutputValidationResult validate(String content); }` + 静态工厂 + record。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(output): 新增 OutputValidatorPort 契约`

---

### Task D2 — JsonOutputValidator

**Files**:
- create: `kernel/application/output/JsonOutputValidator.java`
- create test: `seahorse-agent-tests/.../kernel/application/output/JsonOutputValidatorTests.java`

**Why**: 最常见的结构化输出校验。

**Compatibility**: 新增。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=JsonOutputValidatorTests test`

**Steps**:
1. **Write test** —
   - 合法 JSON object/array → ok；
   - 多写 `,` / 缺 `}` → invalid，`message()` 含 Jackson 报错；
   - 提取代码块（输入 `"```json\n{...}\n```"`）也能识别。
2. **Verify RED** — 失败。
3. **Minimal code** — 预处理代码围栏 → `objectMapper.readTree(...)` → 捕获 `JsonProcessingException`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(output): 实现 JsonOutputValidator`

---

### Task D3 — MermaidOutputValidator（轻量括号配对）

**Files**:
- create: `kernel/application/output/MermaidOutputValidator.java`
- create test: `seahorse-agent-tests/.../kernel/application/output/MermaidOutputValidatorTests.java`

**Why**: Mermaid 解析失败前端会白屏；做轻量校验阻断坏输出。

**Compatibility**: 新增。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=MermaidOutputValidatorTests test`

**Steps**:
1. **Write test** —
   - 合法 `graph TD; A-->B` → ok；
   - 括号失配 `A[未闭合` → invalid；
   - 引号失配 `A["x]` → invalid；
   - 空字符串 → invalid；
   - 必须以 `graph|flowchart|sequenceDiagram|classDiagram|stateDiagram` 开头，否则 invalid。
2. **Verify RED** — 失败。
3. **Minimal code** — 用栈做 `(){}[]"` 配对扫描；首行白名单。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(output): 实现 MermaidOutputValidator`

---

### Task D4 — SelfHealingOptions 配置对象

**Files**:
- create: `kernel/application/output/SelfHealingOptions.java`
- create test: `seahorse-agent-tests/.../kernel/application/output/SelfHealingOptionsTests.java`

**Why**: 隔离最大重试次数、reprompt 模板、降级策略。

**Compatibility**: 新增。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=SelfHealingOptionsTests test`

**Steps**:
1. **Write test** —
   - `defaults()`：`maxRetries==2`、`fallback==RETURN_LAST`、`repromptTemplate` 非空；
   - `maxRetries<0` 抛 `IllegalArgumentException`。
2. **Verify RED** — 失败。
3. **Minimal code** — record + 校验。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(output): 新增 SelfHealingOptions`

---

### Task D5 — SelfHealingLoop 自愈重试环

**Files**:
- create: `kernel/application/output/SelfHealingLoop.java`
- create test: `seahorse-agent-tests/.../kernel/application/output/SelfHealingLoopTests.java`

**Why**: 调度 ChatModelPort + Validator + Reprompt 实现重试。

**Compatibility**: 新增；接受 `ChatModelPort` 注入，便于测试。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=SelfHealingLoopTests test`

**Steps**:
1. **Write test** —
   - **场景 1**：第一次输出合法 → 不调用模型重试，返回原文。
   - **场景 2**：第一次失败、第二次合法 → 调用模型 1 次，返回第二次内容；reprompt 包含上一次错误信息（断言子串）。
   - **场景 3**：达到 `maxRetries=2` 仍失败 → `fallback==RETURN_LAST` 返回最后一次；`fallback==THROW` 抛 `OutputUnhealableException`。
   - **场景 4**：Validator 抛异常 → 不掩盖，向外抛。
2. **Verify RED** — 失败。
3. **Minimal code** — `String heal(String initial, OutputValidatorPort validator, ReprompCallback reprompt, SelfHealingOptions opts)`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(output): 实现 SelfHealingLoop`

---

### Task D6 — RagEvaluatorPort 出站契约

**Files**:
- create: `kernel/ports/outbound/eval/RagEvaluatorPort.java`
- create test: `seahorse-agent-tests/.../ports/outbound/eval/RagEvaluatorPortContractTests.java`

**Why**: 抽象"问句 + 候选文本 → 0~10 分"。

**Compatibility**: 新增。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=RagEvaluatorPortContractTests test`

**Steps**:
1. **Write test** —
   - `RelevanceScore(0..10)` 越界抛；
   - `RagEvaluatorPort.alwaysHigh()` 返回 10；`alwaysLow()` 返回 0；`passthrough()` 不评分（特殊 `-1` 标记保留所有）。
2. **Verify RED** — 失败。
3. **Minimal code** — interface + record + 静态工厂。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(eval): 新增 RagEvaluatorPort 契约`

---

### Task D7 — LlmRagEvaluator 实现

**Files**:
- create: `kernel/application/eval/LlmRagEvaluator.java`
- create test: `seahorse-agent-tests/.../kernel/application/eval/LlmRagEvaluatorTests.java`

**Why**: 默认实现，使用 ChatModelPort + Prompt 模板"问→片段→打分"。

**Compatibility**: 新增。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=LlmRagEvaluatorTests test`

**Steps**:
1. **Write test** —
   - Mock `ChatModelPort.chat` 返回 `"7"` → 分数 7；
   - 返回 `"score: 8/10"` → 解析 8；非数字字符 → 解析失败 → 默认 5（保守通过）；
   - 返回多行（包含解释）→ 取首行第一个数字。
2. **Verify RED** — 失败。
3. **Minimal code** — Prompt 模板内置常量；正则 `(\d+)` 抓首匹配。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(eval): 实现 LlmRagEvaluator`

---

### Task D8 — RagEvaluatorPostProcessorFeature

**Files**:
- create: `kernel/feature/retrieval/RagEvaluatorPostProcessorFeature.java`
- create test: `seahorse-agent-tests/.../kernel/feature/retrieval/RagEvaluatorPostProcessorFeatureTests.java`

**Why**: 把评估器作为现有 PostProcessor 链节点，而非侵入主链路。

**Compatibility**: 实现 `SearchResultPostProcessorFeature`；注册时排在 RRF 之后、Rerank 之前（顺序常量在 Feature 里声明）。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=RagEvaluatorPostProcessorFeatureTests test`

**Steps**:
1. **Write test** —
   - 输入 5 个 `RetrievedChunk`，Mock evaluator 分别返回 `[9,8,3,7,2]`，阈值 5 → 输出仅 3 个；
   - 阈值 0（不过滤）→ 输出原 5 个；
   - chunks 数 ≤ `minChunksForEvaluation`（默认 3）→ 不评估直接返回。
2. **Verify RED** — 失败。
3. **Minimal code** — Feature `apply(context, chunks)` 内并发评分（用 `mcpExecutor` 一样的 `Executor`），返回过滤后列表；trace 记录 `evaluator-filtered` 节点。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(retrieval): 新增 RagEvaluatorPostProcessorFeature`

---

### Task D9 — ContextReducerPort 出站契约

**Files**:
- create: `kernel/ports/outbound/context/ContextReducerPort.java`
- create test: `seahorse-agent-tests/.../ports/outbound/context/ContextReducerPortContractTests.java`

**Why**: 抽象"全量数据 + 视图意图 → 摘要数据"。

**Compatibility**: 新增。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=ContextReducerPortContractTests test`

**Steps**:
1. **Write test** —
   - `ReductionRequest(fullPayload, view)` 非空校验；
   - `ContextReducerPort.identity()` 返回原 payload；
   - 不识别的 view → `ReductionResult.unchanged(payload)`，并 `warning` 字段非空。
2. **Verify RED** — 失败。
3. **Minimal code** — interface + record。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(context): 新增 ContextReducerPort 契约`

---

### Task D10 — JsonViewSliceReducer

**Files**:
- create: `kernel/application/context/JsonViewSliceReducer.java`
- create test: `seahorse-agent-tests/.../kernel/application/context/JsonViewSliceReducerTests.java`

**Why**: 第一个具体降维实现：从 Phase 3 类输出（含 tables / apis / detail）抽取 `tables[].name` / `apis[].path` / 不带 detail。

**Compatibility**: 新增。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=JsonViewSliceReducerTests test`

**Steps**:
1. **Write test** —
   - 输入 JSON `{"tables":[{"name":"users","ddl":"CREATE TABLE..."},...],"apis":[{"path":"/u","schema":{...}}]}`；
   - `view="schema-summary"` → 输出 `{"tables":[{"name":"users"}],"apis":[{"path":"/u"}]}`；
   - `view="api-tree"` → 输出仅 `{"apis":[...method/path/desc]}`；
   - 未识别 view → `unchanged` + `warning="未识别的视图: foo"`。
2. **Verify RED** — 失败。
3. **Minimal code** — Jackson Tree 编程。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(context): 实现 JsonViewSliceReducer`

---

### Task D11 — Spring 自动装配 D 系列三组件

**Files**:
- create: `seahorse-agent-spring-boot-starter/.../SeahorseAgentOutputGovernanceAutoConfiguration.java`
- modify: `seahorse-agent-spring-boot-starter/src/main/resources/application.properties`
- create test: `seahorse-agent-tests/.../adapters/spring/SeahorseAgentOutputGovernanceAutoConfigurationTests.java`

**Why**: 三个开关默认关；开启后 Bean 才存在。

**Compatibility**: `seahorse-agent.output.self-healing.enabled` / `.rag.evaluator.enabled` / `.context.reducer.enabled` 全部默认 `false`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=SeahorseAgentOutputGovernanceAutoConfigurationTests test`

**Steps**:
1. **Write test** —
   - 默认 → 0 个治理 Bean；
   - `output.self-healing.enabled=true` → `JsonOutputValidator` + `MermaidOutputValidator` + `SelfHealingLoop` 存在；
   - `rag.evaluator.enabled=true` 且存在 `ChatModelPort` → `LlmRagEvaluator` + `RagEvaluatorPostProcessorFeature` 存在；
   - `context.reducer.enabled=true` → `JsonViewSliceReducer` 存在。
2. **Verify RED** — 失败。
3. **Minimal code** — 三个 `@ConditionalOnProperty` Bean group。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(starter): 装配 Phase D 输出治理三件套`

---

## Self-Review

- **Spec coverage** — Validator / SelfHealing / RagEvaluator / Reducer 全部映射。
- **Placeholder scan** — 无。
- **Type consistency** — `OutputValidationResult`、`RelevanceScore`、`ReductionRequest/Result` 跨任务一致。
- **Compatibility** — 全是新包；三个开关默认 `false`，加入工程不影响运行时行为；评分 PostProcessor 仅在显式启用且接入 PostProcessor 链时生效。
- **Verification** — 每 Task 自带 mvn 命令。
- **Dual-track** — N/A，Phase D 不替换任何既有 owner。
- **Decision hygiene** — Validator/Evaluator/Reducer 三组各自单一职责，端口与实现分离。

## Retirement Track

无。

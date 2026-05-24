# DefaultMemoryEnginePort Decomposition Handoff

更新时间：2026-05-24

当前分支：`main`（ahead of `origin/main` 17 commits，未推送）

最近相关提交：`9df495cc refactor(kernel): Slice 3 续 — extract MemoryRefinerFeedbackLookup (cut 11)`

> 适用范围：本交接覆盖 `docs/aegis/specs/2026-05-24-design-alignment-next-development.md` §8（DefaultMemoryEnginePort 拆分）剩余工作。其他 slice（§9.2 starter 子配置、§11 controller 响应、§12 capture rule）已在本次开发中落地，详见 §3。

---

## 1. 接手目标

继续按 spec §8 把 `DefaultMemoryEnginePort` 从巨型 facade 拆成职责清晰的应用服务，但不要为了拆而拆。**spec §8.4 acceptance（facade 行数 ≥ 200 行减少 + 行为不变 + 新 service 无 Spring 注解）已经达成 3.5×**（实际减 699 行）。

核心不变量（与 spec §2.1/§2.2 一致）：

- **`MemoryEnginePort` / `MemoryIngestionWorkflowPort` 契约不能改**：所有现有 inbound caller（chat、aggregation、review apply）行为必须保持 byte-identical。
- **kernel 纯净**：新 service 不引入 `@Service` / `@Component` / `@Autowired` 或任何 Spring/Redis/Pulsar/Milvus/OpenAI 类型。装配只在 facade ctor 里 `new XxxService(...)`。
- **path-scoped 提交**：当前 worktree 有大量并行脏改（见 §5），`git add` 必须显式列文件，禁止 `git add .` 或 `git add -A`。
- **测试集**：`DefaultMemoryEnginePortTests`(62) + `SeahorseAgentKernelAutoConfigurationTests`(46) + `MemoryWorkflowRoutingTests`(10) = **118 用例**是 facade 行为的回归底线。每次拆分必须三个全过。
- **OPERATION_* 字符串**：`CORRECTION_UPSERT` / `PROFILE_UPSERT` / `OUTBOX_DELETE_ENQUEUED` 等 operations 列表返回值是 ingestion 公开契约，搬迁时必须保留字面量。

---

## 2. 必读基线

接手前先读：

- `docs/aegis/specs/2026-05-24-design-alignment-next-development.md` §8（拆分目标与验收）
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`（**当前 1457 行**，原 2156 行）
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryDerivedIndexDispatchService.java`（cut 1 模板，第一个拆出来的 service）
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryRefinerBatchCircuitBreaker.java`（cut 4 模板，含 magic value 内化的最佳实践）
- `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePortTests.java`（62 用例，行为不变回归点）

不必读但有帮助：

- `docs/aegis/work/2026-05-23-memory-gemini-handoff/HANDOFF.md`（前一阶段 Gemini 对齐手册，与本次拆分互补）

---

## 3. 当前已完成状态

### 3.1 已拆出的 11 个 service（按提交顺序）

| Cut | Commit | Service | Lines | 职责 | 依赖 |
|---|---|---|---|---|---|
| 1 | `e6570c42`（prior session） | `MemoryDerivedIndexDispatchService` | 131 | vector index dispatch + outbox 兜底 | `MemoryVectorPort`、`MemoryOutboxPort` |
| 2 | `bb3c2671` | `MemoryTrackWriteService` | 187 | profile/correction track upsert + slot obsolete | `ProfileMemoryPort`、`CorrectionLedgerPort`、`MemoryLifecyclePort` |
| 3 | `bc0fbed1` | `MemoryRefinementContextParser` | 237 | sanitized content 切 reference/target zones + source spans | 无 port（纯函数） |
| 4 | `663076de` | `MemoryRefinerBatchCircuitBreaker` | 172 | batch operation/delete 比例熔断 → REVIEW | 无 port |
| 5 | `dcc0a0c6` | `MemoryProfileValueNormalizer` | 122 | profile slot 解析 + 按 slot 规整 value | `ProfileSlotResolver` |
| 6 | `a5f0b522` | `MemoryRefinementInputBuilder` | 189 | 三层 listByUser + sticky anchors | `ShortTerm/LongTerm/SemanticMemoryPort` |
| 7 | `3cd28a31` | `MemoryCanonicalAliasResolver` | 120 | content 提 token + canonical alias 挂 metadata | `MemoryAliasPort` |
| 8 | `b7b31c10` | `MemoryRefinerMetadataWriter` | 124 | refined delta → metadata + memoryId 构造 | 无 port |
| 9 | `62c85900` | `MemoryOperationBuilder` | 169 | ingestion command → MemoryOperation 装配 | `MemorySanitizer/PreFilter/SemanticClassifier` |
| 10 | `4237405a` | `MemoryOperationCompletionWriter` | 101 | operation log status 映射 + decisionMap | `MemoryOperationLogPort`、`MemoryRefinerMetadataWriter` |
| 11 | `9df495cc` | `MemoryRefinerFeedbackLookup` | 144 | recent resolved review feedback 查询 | `MemoryReviewFeedbackRepositoryPort`、`MemoryProfileValueNormalizer` |

**累计成果**：
- `DefaultMemoryEnginePort.java`：**2156 行 → 1457 行（-699 行，-32%）**
- 11 个新 service 全部 kernel pure（无 Spring 注解、无 outbound 包外依赖）
- spec §8.4 acceptance 达成 3.5×（要求 ≥200）
- 118 用例每次 cut 后全过

### 3.2 其他 spec slice 在本次开发中的进展

- **§9.2（starter 子 auto-config 切分）**：4 个 sub-config 已落地 — Outbox(`6785fe82`)、Aggregation(`f47c257d`)、Maintenance(`5aab2af6`)、Recall(`38b5d77c`)。`SeahorseAgentKernelMemoryAutoConfiguration` 从 973 → 420 行。
- **§11（controller 响应样板）**：16 个 controller 已全部迁到 `ApiResponses.requireService`（commits `33188c20` / `55591a23` / `375539f4` / `ddc3107e`）。grep 验证：facade 外只有 `ApiResponses.java` 自己保留 "Service not available" 字符串。
- **§12（capture magic value）**：`MemoryCaptureRejectionReason` enum + `MemoryCaptureRuleProperties` 均已存在（之前会话），不在本次范围。
- **§2c（生产 noop NoopFallback 标记）**：`MemoryKeywordSearchPort` 已加 NoopFallback（`0f88f70b`）。
- **§4 续（memory auto config 配置治理）**：`MemoryProperties` 顶层字段、gc segment、maintenance enabled、alias-resolution、trace + recall、engine/decay/derived-index 5 段已落地（5aab2af6..ecc90c68）。

---

## 4. 剩余可拆方向（按风险从低到高）

继续拆分的边际收益已开始递减。如果接手者要继续，按下表风险递增推进；如果只是 spec 验收用，**到此为止即可**。

### 4.1 低风险（推荐继续）

#### A. `reviewApplyClassification` + `validateReviewDirectiveTargetLayer`（~50 行）

抽出位置 facade 当前 line 836-877 + 916-928。

**依赖**：
- `memorySchemaValidator`（kernel application 内）
- `targetLayer(String)` 工具函数（facade 内，需要一起搬或暴露）
- `RefinedMemoryDelta`、`MemoryCaptureDecision.refinedAdd`、`MemoryClassificationResult.refinedAdd`（同 package）
- METADATA_REVIEW_REQUESTED_ACTION 等 6 个 metadata key（facade 还在用，需要复制字面量）

**复杂度提示**：`targetLayer(String)` 还被 `saveMemory` 等 5+ 处共用，不能整体搬走。新 service 内部要重新实现 layer 解析（10 行）。

**预计减行**：~40-50 行。

#### B. `baselineMemoryType` + `baselineDetails`（~25 行）

facade 当前 line 1320-1342。纯静态 helper，无 port 依赖。

**收益小**（25 行），但**风险极低**，可以放在和其他 cleanup 一起。

#### C. `targetMemoryId` + `tenantId` + `operationId` + `isReviewDeleteApply`（~30 行）

四个从 `MemoryIngestionCommand` 抽取字段的纯函数。可合并成 `MemoryIngestionCommandResolver` static helper class。

**适合并入 B**，作为一次"小型 helpers 清理" cut。

### 4.2 中风险（评估后再做）

#### D. `applyRefinementResult` + `firstSupportedOperation` + `supportedRefinedClassifications` + `supportedOperationCount` + `effectiveSourceMessageIds` + `nonBlankDistinct` + `requiresReviewStaging` + `resultAction` + `withRefinerDelta`（~150 行）

facade 当前 line 1037-1245。这是 refinement workflow 的"中间层"逻辑：把 refiner port 返回结果折叠成单一 classification + 触发 circuit breaker。

**依赖**：
- 内部使用的 METADATA_REFINER_BATCH 等常量
- `MemoryRefinerBatchCircuitBreaker`（已存在）
- `MemoryRefinementContextParser.Zones`（已存在）
- 大量 `MemoryClassificationResult` / `RefinedMemoryDelta` / `MemoryRefinementResult` 操作

**复杂度提示**：这是 spec §8.2 列出的 `MemoryRefinementWorkflowService` 真正含义。能拆但**需要谨慎设计入口签名**，否则会成为新的 god class。

### 4.3 高风险（不推荐当前 PR 处理）

#### E. `refinedAddClassification` + 3 重载 `refinedReviewClassification`（~110 行）

facade 当前 line 1134-1236。把 `RefinedMemoryOperation` 翻译成 `MemoryClassificationResult` + 触发 review policy gate。

**依赖**：
- `memoryReviewPolicyPort`（产生 `MemoryReviewPolicyDecision`）
- `memoryPolicyConfigPort.current()`
- `MemoryReviewPolicyPort.REFINER_ADD_LOW_CONFIDENCE` 等 3 个公共常量
- METADATA_CONFIDENCE / METADATA_IMPORTANCE / METADATA_VALUE_SCORE / METADATA_RISK_SCORE / METADATA_CONTENT / METADATA_SOURCE_MESSAGE_IDS / METADATA_TARGET_LAYER 等 7+ metadata key
- REFINER_STATUS_DROPPED / REFINER_STATUS_PENDING_REVIEW

**复杂度**：构造参数会膨胀到 6+ port/config，且 metadata key 与 facade 其他流共享率高，magic string 重复风险大。**建议先做一个 `MemoryMetadataKeys` 公共常量类**再拆，否则会留下技术债。

#### F. `executeIngestion` / `executeAcceptedClassification` / `executeRefinerBatch` / `executeReviewDeleteApply` / `executeReviewStaging`（~250 行）

facade 主流程编排，对应 spec §8.2 的 `MemoryIngestionCoordinator`。这是 facade 的"骨架"，拆出后 facade 会真正退化成入口路由。

**风险**：5 个 execute* 方法相互调用且共享大量参数（operationId、tenantId、command、request、message、baseline、sanitized…）。拆成 service 后参数对象化几乎是必须的，会引入新的 record/builder，**单次 PR 很难收敛**。

---

## 5. 当前工作区风险

`git status --short --branch` 显示当前分支领先 origin/main 17 个提交，但**未推送**。下一位开发不要 broad add 也不要 force push。

**已知脏改**（**不要**纳入新提交，除非明确是任务范围）：

- `.gitignore`（未明任务归属）
- `seahorse-agent-adapter-cache-redis/src/main/java/.../RedisMemoryAggregationBufferPort.java`
- `seahorse-agent-adapter-observation-micrometer/src/test/.../MicrometerObservationAdapterTests.java`
- `seahorse-agent-tests/src/test/java/.../RedisMemoryAggregationBufferPortTests.java`
- `seahorse-agent-tests/src/test/java/.../DefaultContextWeaverObservationTests.java`
- `seahorse-agent-tests/src/test/java/.../MemoryWorkflowRoutingTests.java`

**未跟踪**（可读但不要顺手 add）：

- `.claude/`、`.playwright-cli/`、`frontend/.playwright-cli/`
- `.mvn/seahorse-agent-adapter-web/`
- `CLAUDE.md`
- `docs/Seahorse Agent记忆系统现状与Gemini文档差距对照.md`
- `docs/code-standard-review.md`
- `docs/default-memory-engine-port-dependency-review.md`

### 提交规则（与本次 11 个 cut 一致）

```bash
git add -- \
    seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/MemoryXxxService.java \
    seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java

git diff --cached --stat   # 必查：只能两个文件

git commit -m "$(cat <<'EOF'
refactor(kernel): Slice 3 续 — extract MemoryXxxService (cut N)

<2-4 段说明，参考 9df495cc / 4237405a 风格>

Cumulative cut 1–N ≈ XXX lines below the original facade (2156 → YYYY).

Behaviour regression: DefaultMemoryEnginePortTests (62) +
SeahorseAgentKernelAutoConfigurationTests (46) + MemoryWorkflowRoutingTests
(10) pass.

Co-Authored-By: Claude Opus 4.7 (1M context) <noreply@anthropic.com>
EOF
)"
```

---

## 6. 推荐下一刀：reviewApplyClassification 服务化（§4.1A）

接手者最低成本的进入点。

### 目标

把 `reviewApplyClassification(MemoryReviewApplyDirective, String)` + `validateReviewDirectiveTargetLayer(MemoryReviewApplyDirective)` 抽到 `MemoryReviewApplyClassificationBuilder`。

### 设计草案

```java
public final class MemoryReviewApplyClassificationBuilder {

    private static final String DEFAULT_TARGET_KIND = "FACT";
    private static final String STATUS_REVIEW_APPLIED = "review_applied";
    private static final String REASON_REVIEW_APPLIED = "memory_review_applied";
    private static final String CAPTURE_SIGNAL = "memory_review_applied";
    private static final String CAPTURE_REASON = "human_review";
    private static final String DEFAULT_LAYER = "SHORT_TERM";  // facade REVIEW_DEFAULT_LAYER 同字面量

    // metadata keys（与 facade 字面量一致）
    private static final String METADATA_REVIEW_REQUESTED_ACTION = "reviewRequestedAction";
    private static final String METADATA_TARGET_LAYER = "targetLayer";
    private static final String METADATA_CONFIDENCE = "confidence";
    private static final String METADATA_IMPORTANCE = "importance";
    private static final String METADATA_VALUE_SCORE = "valueScore";
    private static final String METADATA_RISK_SCORE = "riskScore";
    private static final String METADATA_SOURCE_MESSAGE_IDS = "sourceMessageIds";

    private final MemorySchemaValidator memorySchemaValidator;

    public MemoryReviewApplyClassificationBuilder(MemorySchemaValidator memorySchemaValidator) { ... }

    /** null directive → null classification（保持 facade 语义不变）。 */
    public MemoryClassificationResult build(MemoryReviewApplyDirective directive, String content) { ... }

    public MemorySchemaValidationResult validateTargetLayer(MemoryReviewApplyDirective directive) { ... }

    private static MemoryLayer parseLayer(String layer) {
        // 内部复制 facade.targetLayer(String) 的实现
    }
}
```

### facade 改动

- field 添加：`private final MemoryReviewApplyClassificationBuilder reviewApplyBuilder;`
- ctor init：`this.reviewApplyBuilder = new MemoryReviewApplyClassificationBuilder(this.memorySchemaValidator);`
- 替换 line 642 `validateReviewDirectiveTargetLayer(directive)` → `reviewApplyBuilder.validateTargetLayer(directive)`
- 替换 line 655 `reviewApplyClassification(directive, sanitized.content())` → `reviewApplyBuilder.build(directive, sanitized.content())`
- 删除 `reviewApplyClassification` 与 `validateReviewDirectiveTargetLayer` 两个 private 方法

### 预计 diff

- 新文件：~120 行（service + 内部 helpers + 字面量常量）
- facade 改动：-50 行
- 累计 cut 1–12 facade ≈ 1407 行（原 2156，减 35%）

### TDD 流程

1. 不需要新增测试 — 现有 62 个 `DefaultMemoryEnginePortTests` 已覆盖 review apply 流程。
2. 编译：`./mvnw -B -pl seahorse-agent-kernel compile -Dspotless.check.skip=true`
3. 回归：`./mvnw -B -pl seahorse-agent-tests test -Dspotless.check.skip=true -Dtest='DefaultMemoryEnginePortTests,SeahorseAgentKernelAutoConfigurationTests,MemoryWorkflowRoutingTests' -Dsurefire.failIfNoSpecifiedTests=false`
4. 路径限定 commit。

### 兼容性 / 行为不变验证点

- `RefinedMemoryDelta.targetKind` 当 directive.targetKind 为空时回退 `"FACT"` — 字面量必须保留
- `metadata.put("status", "review_applied")` — 字面量必须保留（外部 review apply 流程可能据此过滤）
- `parseLayer("WORKING")` 返回 `MemoryLayer.SHORT_TERM`（spec 历史约定）— 不要改成 `WORKING`

---

## 7. 后续切片建议

按优先级：

1. **§4.1A** reviewApplyClassification（推荐下一刀，见 §6）
2. **§4.1B+C 合并** baseline/command-resolver helper cleanup（~55 行，低收益但风险极低）
3. **§4.2D** applyRefinementResult cluster — **推迟到先实现 `MemoryMetadataKeys` 公共常量类之后**
4. **§4.3E** refinedAdd/refinedReview classification — **强烈建议先做 `MemoryMetadataKeys` 公共常量类**
5. **§4.3F** ingestion coordinator — **作为 spec §8.2 收尾的独立大 PR**，最少包含 1–2 个 record 参数对象设计

### `MemoryMetadataKeys` 公共常量类（建议下下刀）

facade 当前还持有 14 个 METADATA_* 常量，多个新 service 也内化了字面量。建议建立：

```java
public final class MemoryMetadataKeys {
    public static final String CONTENT = "content";
    public static final String CONFIDENCE = "confidence";
    public static final String IMPORTANCE = "importance";
    public static final String VALUE_SCORE = "valueScore";
    public static final String RISK_SCORE = "riskScore";
    public static final String SOURCE_MESSAGE_IDS = "sourceMessageIds";
    public static final String TARGET_LAYER = "targetLayer";
    public static final String REVIEW_REQUESTED_ACTION = "reviewRequestedAction";
    public static final String TARGET_MEMORY_ID = "targetMemoryId";
    public static final String REFINER_OPERATION_INDEX = "refinerOperationIndex";
    public static final String REFINER_OPERATION_COUNT = "refinerOperationCount";
    public static final String REFINER_BATCH = "refinerBatch";
    // ...
    private MemoryMetadataKeys() {}
}
```

让 facade 和 11 个新 service 全部用同一组常量，消除 magic string 重复。这是 §4.3 拆分前的清场动作。

---

## 8. 可用 subagents 的建议

只在任务能并行、且不会写同一批文件时再开 subagents。

### 可并行的研究任务

- **Agent A**：核对 `DefaultMemoryEnginePort` 内部还有哪些 service 候选（grep 私有方法 + 调用链分析），输出"剩余拆分候选清单"。**不改代码。**
- **Agent B**：核对 11 个新 service 是否被 `SeahorseAgentKernelAutoConfiguration` 显式装配 / 是否能被外部 mock 替换（目前都是 facade ctor 直接 `new`，未对外开放）。输出"是否需要把新 service 提升为 @Bean"的判断。
- **Agent C**：扫描 facade 与 11 个新 service 中所有 magic string 字面量，按 metadata key / status / reason / target kind 分类，输出 §7 的 `MemoryMetadataKeys` 设计草案。

### 不建议并行的实现任务

- 任何 cut 都涉及修改 `DefaultMemoryEnginePort.java`（共享文件），不要让两个 subagent 同时改它。
- §4.3 的 metadata key 重构会与所有正在进行中的 cut 冲突，**先把 `MemoryMetadataKeys` 落地再开后续 cut**。

---

## 9. 最近验证证据

### 11 cut 全部通过的测试集

```bash
./mvnw -B -pl seahorse-agent-tests test \
    -Dspotless.check.skip=true \
    -Dtest='DefaultMemoryEnginePortTests,SeahorseAgentKernelAutoConfigurationTests,MemoryWorkflowRoutingTests' \
    -Dsurefire.failIfNoSpecifiedTests=false
```

每次 cut 后输出：
- `DefaultMemoryEnginePortTests` — 62/62 pass
- `SeahorseAgentKernelAutoConfigurationTests` — 46/46 pass
- `MemoryWorkflowRoutingTests` — 10/10 pass
- **合计 118 pass**

### 编译验证

```bash
./mvnw -B -pl seahorse-agent-kernel compile -Dspotless.check.skip=true
```

每次 cut 后均 `BUILD SUCCESS`（858–860 source files）。

### facade 行数变化

| Milestone | Lines | Δ |
|---|---|---|
| 起点 | 2156 | — |
| cut 1 后（prior session） | ~2050 | −106 |
| cut 2 (bb3c2671) | 2121 | −35 |
| cut 3 (bc0fbed1) | 1960 | −161 |
| cut 4 (663076de) | 1863 | −97 |
| cut 5 (dcc0a0c6) | 1809 | −54 |
| cut 6 (a5f0b522) | 1746 | −63 |
| cut 7 (3cd28a31) | 1691 | −55 |
| cut 8 (b7b31c10) | 1636 | −55 |
| cut 9 (62c85900) | 1549 | −87 |
| cut 10 (4237405a) | 1510 | −39 |
| cut 11 (9df495cc) | **1457** | −53 |
| **累计** | — | **−699 (−32%)** |

---

## 10. 完成对齐的判断标准（spec §8.4）

### 已达成

- ✅ facade 行数减少 ≥ 200（实际 −699，3.5×）
- ✅ 行为回归通过（118 用例 11 次全过）
- ✅ 新 service 无 Spring 注解（grep `@Service|@Component|@Autowired` 11 个文件均 0 hit）
- ✅ kernel 包外依赖仅 outbound port（grep `import.*adapters\.` 0 hit）

### 可进一步达成（非 spec 要求）

- facade 行数 ≤ 1200（再做 reviewApplyClassification + baseline helper cleanup 即可）
- 0 magic string 重复（建立 `MemoryMetadataKeys` 公共常量类）
- 5 个 spec §8.2 命名 service 显式存在（Ingestion/Recall/Refinement Coordinator 与 Track/Derived dispatch — 当前职责分散在 11 个更细的 service 中，**命名映射不严格**）

### 已知技术债

- METADATA_* 常量在 facade 与 4+ 个 service 间字面量重复（cut 4/6/8/11 都内化了一份）→ 见 §7 `MemoryMetadataKeys` 提议
- `RefinedMemoryDelta` 和 `MemoryClassificationResult` 是 `application.memory` 包的"准 record"，多个 service 共享。未来如果改成 `domain.memory` 包要小心同步所有 11 个 service 的 import。

---

## 11. 工作记录

完整的 task intent / checkpoint / evidence / reflection 记录见同目录其他文件：

- `10-intent.md` — 任务意图与 baseline read set
- `20-checkpoint.md` — 11 个 cut 的逐步 checkpoint + drift check
- `90-evidence.md` — 测试运行证据
- `99-reflection.md` — 拆分模式总结与教训

下一位开发的最小安全动作：按 §6 实现 reviewApplyClassification 服务化，并做路径限定提交。

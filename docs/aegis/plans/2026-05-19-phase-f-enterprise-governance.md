# Phase F 实施计划：企业治理（权限 / 知识图谱 / 在线评测回流）

> 该计划独立于 A/B/D/E，但建议在 A/B 完成后再启动，因为评测回流以 Trace 为数据源。
> 目标：让 RAG/Agent 能在多租户企业场景安全可用，并形成数据驱动的优化闭环。

## Goal

三件事：
1. **检索权限 / 数据范围**：把 user/部门/标签级的访问控制下沉到检索通道与 chunk 元数据。
2. **知识图谱 / 条款层级 / 冲突优先级**：扩展 chunk 关系模型支持父子层级、交叉引用、冲突标记。
3. **在线评测自动回流**：以 RAG Trace 为数据源，自动产出评测数据集，把生产问题回流到离线评测、再回到 PostProcessor 权重。

## Architecture

```
┌── 权限层 ───────────────────────────────────────────┐
│ Chat / Agent 请求携带 authContext                    │
│      → ChatInboundPort → KernelChatInboundService   │
│      → SecurityContextHolder（Thread/线程透传）       │
│ KernelMultiChannelRetrievalEngine                   │
│   ├─ AuthorityAwareSearchOptions（注入 user/scope）  │
│   └─ AuthorityGuardPostProcessor（chunk 级强制过滤）  │
└──────────────────────────────────────────────────────┘

┌── 图谱层 ───────────────────────────────────────────┐
│ KnowledgeChunk + 新表 chunk_relations                │
│   relationType: PARENT_OF | REFERENCES | CONFLICTS  │
│ ParentChildExpansionPostProcessor（命中子 → 拉父）   │
│ ConflictAwarePostProcessor（冲突优先 → 标记 + 加权)  │
└──────────────────────────────────────────────────────┘

┌── 评测回流 ─────────────────────────────────────────┐
│ RagTraceRepositoryPort（已有）→                      │
│ TraceToEvalSampleProjector（新增）                   │
│   → KernelRetrievalEvaluationDatasetService（已有）  │
│   → KernelRetrievalEvaluationService（已有）         │
│   → MetricPort / FeedbackPort                       │
│ RetrievalWeightPolicyFeature（按指标调整 PostProcessor 权重） │
└──────────────────────────────────────────────────────┘
```

## Tech Stack

- Sa-Token（已有）
- PostgreSQL + 新增 `chunk_relations` 表
- 复用 `KernelRetrievalEvaluation*Service`、`KernelRagTraceRecorder`

## Baseline / Authority Refs

- `@C:/user-data/code/ai/seahorse-agent/docs/agent-vs-rag-capability-baseline.md` — Phase F
- `@C:/user-data/code/ai/seahorse-agent/缺少的功能.md` — 权限 / 知识图谱 / 评测 backlog
- `@C:/user-data/code/ai/seahorse-agent/seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/retrieval/KernelRetrievalEvaluationService.java`

## Compatibility Boundary

**必须稳定**：
- `RetrievedChunk` 字段（新增字段 default 值，向后兼容）
- 现有数据库表结构（仅新增表，不改既有列）
- `KernelMultiChannelRetrievalEngine` 公共 API

**允许变化**：
- 新增 PostProcessor Feature
- 新增 `ChunkRelationRepositoryPort` 端口
- `AuthorityContext` 通过 ThreadLocal（兼容 Sa-Token）

## Verification

```bash
mvn -pl seahorse-agent-tests -am -Dtest='*Authority*,*ChunkRelation*,*TraceToEval*,*WeightPolicy*' test
```

## Risks / Rollback

| 风险 | 缓解 |
|---|---|
| ThreadLocal 在异步任务里丢上下文 | 复用项目已有 `TransmittableThreadLocal` 包装 |
| 跨租户串数据 | AuthorityGuardPostProcessor 作为"硬过滤"放在所有 PostProcessor 末位，且单元测试覆盖跨租户场景 |
| 图谱关系扩散 | 默认 `maxHops=1`；超出截断 |
| 评测回流抖动 | 抽样比例可配（默认 1%），按用户哈希均匀抽样 |

## Retirement

无；Phase F 全部新增。

---

## File Map

**新建**：
- 权限：
  - `kernel/domain/auth/AuthorityContext.java`
  - `kernel/application/auth/AuthorityContextHolder.java`
  - `kernel/feature/retrieval/AuthorityGuardPostProcessorFeature.java`
- 图谱：
  - `kernel/domain/knowledge/ChunkRelation.java`
  - `kernel/domain/knowledge/ChunkRelationType.java`
  - `kernel/ports/outbound/knowledge/ChunkRelationRepositoryPort.java`
  - `seahorse-agent-adapter-repository-jdbc/.../JdbcChunkRelationRepositoryAdapter.java`
  - `kernel/feature/retrieval/ParentChildExpansionPostProcessorFeature.java`
  - `kernel/feature/retrieval/ConflictAwarePostProcessorFeature.java`
  - DDL：`resources/database/migration/V20260520__chunk_relations.sql`
- 评测回流：
  - `kernel/application/eval/TraceToEvalSampleProjector.java`
  - `kernel/application/eval/TraceSamplingOptions.java`
  - `kernel/feature/retrieval/RetrievalWeightPolicyFeature.java`
- 自动装配：`SeahorseAgentEnterpriseGovernanceAutoConfiguration.java`
- 测试 9 个

**修改**：
- `kernel/domain/retrieval/RetrievedChunk.java` — 新增 `ownerScope`、`relationParentId`、`conflictPriority` 可空字段
- `feature/retrieval/MetadataGuardPostProcessorFeature.java` — 注释顺序（auth 在 metadata 之后）

---

## Tasks

### Task F1 — AuthorityContext + Holder

**Files**:
- create: `kernel/domain/auth/AuthorityContext.java`
- create: `kernel/application/auth/AuthorityContextHolder.java`
- create test: `seahorse-agent-tests/.../kernel/application/auth/AuthorityContextHolderTests.java`

**Why**: 检索层需要稳定地拿到 user/scope，不耦合 Sa-Token。

**Compatibility**: 新增；Web 适配器在请求入口填充 holder。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=AuthorityContextHolderTests test`

**Steps**:
1. **Write test** —
   - `AuthorityContext(userId, tenantId, scopes)` 必填校验；
   - Holder 默认 `current().userId().isEmpty()`；
   - `Holder.setCurrent(ctx)` → `current()` 返回设置值；`reset()` 清空；
   - 不同线程相互不可见，但通过 `TransmittableThreadLocal` 包装传到子线程能取到。
2. **Verify RED** — 失败。
3. **Minimal code** — `record AuthorityContext(...)` + `TransmittableThreadLocal` Holder。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(auth): 新增 AuthorityContext 与 Holder`

---

### Task F2 — RetrievedChunk 增加可选 owner/relation/conflict 字段

**Files**:
- modify: `kernel/domain/retrieval/RetrievedChunk.java`
- create test: `seahorse-agent-tests/.../kernel/domain/retrieval/RetrievedChunkExtensionTests.java`

**Why**: 让权限、图谱、冲突 PostProcessor 都基于同一份 chunk 模型，无需 metadata 字符串 hack。

**Compatibility**: 新字段默认 null / 空集合，旧构造（如果是 builder）保持。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=RetrievedChunkExtensionTests test`

**Steps**:
1. **Write test** —
   - 旧 builder 不写新字段 → `ownerScope().isEmpty()`、`relationParentId()==null`、`conflictPriority()==0`；
   - 新 builder 写完整字段 → 字段读取一致；
   - `withOwnerScope(...)` 返回新实例，原对象不变（不可变）。
2. **Verify RED** — 失败。
3. **Minimal code** — 加 3 字段 + 默认值；如非 record，确保不可变（`Collections.unmodifiableSet`）。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(retrieval): RetrievedChunk 增加 owner/relation/conflict 字段`

---

### Task F3 — AuthorityGuardPostProcessor

**Files**:
- create: `kernel/feature/retrieval/AuthorityGuardPostProcessorFeature.java`
- create test: `seahorse-agent-tests/.../kernel/feature/retrieval/AuthorityGuardPostProcessorFeatureTests.java`

**Why**: 在 PostProcessor 链末位强制按 `AuthorityContext.scopes` 过滤；任何上游漏过滤都被它兜底。

**Compatibility**: 实现 `SearchResultPostProcessorFeature`，顺序常量 `Integer.MAX_VALUE - 10`（末位之一）。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=AuthorityGuardPostProcessorFeatureTests test`

**Steps**:
1. **Write test** —
   - `AuthorityContext` 未设置 → 直通；
   - `scopes={"hr"}` 且 chunk `ownerScope` 含 `"hr"` → 保留；
   - chunk `ownerScope={"finance"}` → 丢弃且 trace 记录 `authority-rejected`；
   - chunk `ownerScope` 为空（公共文档） → 保留。
2. **Verify RED** — 失败。
3. **Minimal code** — `apply(ctx, chunks)` filter；通过 `Holder.current()` 读上下文。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(retrieval): 新增 AuthorityGuardPostProcessorFeature`

---

### Task F4 — ChunkRelation 领域模型 + 端口 + 表

**Files**:
- create: `kernel/domain/knowledge/ChunkRelation.java`
- create: `kernel/domain/knowledge/ChunkRelationType.java`
- create: `kernel/ports/outbound/knowledge/ChunkRelationRepositoryPort.java`
- create: `resources/database/migration/V20260520__chunk_relations.sql`
- create test: `seahorse-agent-tests/.../kernel/domain/knowledge/ChunkRelationTests.java`

**Why**: 父子层级 / 交叉引用 / 冲突标记的存储与契约。

**Compatibility**: 新增表 `chunk_relations(id, source_chunk_id, target_chunk_id, relation_type, priority, created_at)`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=ChunkRelationTests test`

**Steps**:
1. **Write test** —
   - `ChunkRelationType` 枚举 4 值：`PARENT_OF`、`REFERENCES`、`CONFLICTS`、`SUPERSEDES`；
   - `ChunkRelation(source, target, type, priority)` 必填校验，`priority` 必须 0..10；
   - `ChunkRelationRepositoryPort.empty()` 静态实现：`findByChunkId(id, type)` 返回空 List；`save(rel)` 抛 `UnsupportedOperationException`（防误用）。
2. **Verify RED** — 失败。
3. **Minimal code** — 2 类 + 1 接口；SQL 文件含 4 列索引 `(source_chunk_id, relation_type)`、`(target_chunk_id, relation_type)`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(knowledge): 新增 ChunkRelation 模型与端口`

---

### Task F5 — JdbcChunkRelationRepositoryAdapter

**Files**:
- create: `seahorse-agent-adapter-repository-jdbc/src/main/java/.../JdbcChunkRelationRepositoryAdapter.java`
- create test: `seahorse-agent-tests/.../adapters/repository/jdbc/JdbcChunkRelationRepositoryAdapterTests.java`

**Why**: 落地 F4 端口。

**Compatibility**: 新表，独立 Mapper。

**Verification**: 使用 H2/Postgres-Testcontainers 或 Mock JdbcTemplate；`mvn -pl seahorse-agent-tests -am -Dtest=JdbcChunkRelationRepositoryAdapterTests test`

**Steps**:
1. **Write test** —
   - `save(rel)` 持久化字段一致；重复 `(source,target,type)` 触发唯一约束 → 抛 `DuplicateChunkRelationException`；
   - `findByChunkId("c1", PARENT_OF)` 返回 source=c1 的相关行；
   - 查不到 → 空 List。
2. **Verify RED** — 失败。
3. **Minimal code** — `JdbcTemplate` + 3 SQL 字符串。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(repository): 实现 JdbcChunkRelationRepositoryAdapter`

---

### Task F6 — ParentChildExpansionPostProcessor

**Files**:
- create: `kernel/feature/retrieval/ParentChildExpansionPostProcessorFeature.java`
- create test: `seahorse-agent-tests/.../kernel/feature/retrieval/ParentChildExpansionPostProcessorFeatureTests.java`

**Why**: 命中子条款时把父条款拉进来一并作为上下文，提升回答完整性。

**Compatibility**: PostProcessor Feature，可配置 `maxHops`（默认 1，最大 3）。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=ParentChildExpansionPostProcessorFeatureTests test`

**Steps**:
1. **Write test** —
   - 输入 chunks `[c2,c3]`，Mock repo `findByChunkId("c2", PARENT_OF)` 返回 `c1`、`c3` 同；输出含 `c1,c2,c3`，去重；
   - `maxHops=2` 时支持祖父；
   - 父子环 `c1→c2→c1` 不死循环，访问集合截断。
2. **Verify RED** — 失败。
3. **Minimal code** — BFS + `visited`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(retrieval): 新增 ParentChildExpansionPostProcessorFeature`

---

### Task F7 — ConflictAwarePostProcessor

**Files**:
- create: `kernel/feature/retrieval/ConflictAwarePostProcessorFeature.java`
- create test: `seahorse-agent-tests/.../kernel/feature/retrieval/ConflictAwarePostProcessorFeatureTests.java`

**Why**: 多条规范冲突时把"优先级更高的"或"SUPERSEDES 关系上游"放前面，并在 metadata 中打 `conflictWith=[...]` 标记，让 Prompt 层提示模型。

**Compatibility**: PostProcessor Feature；不改 RetrievedChunk 已有顺序，只重排 + 注 metadata。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=ConflictAwarePostProcessorFeatureTests test`

**Steps**:
1. **Write test** —
   - 输入 chunks `[c1(priority=3), c2(priority=8, CONFLICTS c1)]` → 输出 `[c2, c1]`；`c1` metadata 含 `conflictWith=["c2"]`；
   - 不存在冲突时输出顺序、metadata 不变；
   - `SUPERSEDES` 关系：`c3 SUPERSEDES c1` → `c3` 在前，`c1` 被标记 `superseded=true`。
2. **Verify RED** — 失败。
3. **Minimal code** — 拉关系 → 拓扑/优先级排序 → 注 metadata。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(retrieval): 新增 ConflictAwarePostProcessorFeature`

---

### Task F8 — TraceToEvalSampleProjector

**Files**:
- create: `kernel/application/eval/TraceToEvalSampleProjector.java`
- create: `kernel/application/eval/TraceSamplingOptions.java`
- create test: `seahorse-agent-tests/.../kernel/application/eval/TraceToEvalSampleProjectorTests.java`

**Why**: 把生产 Trace 投影为评测样本，自动喂给 `KernelRetrievalEvaluationDatasetService`。

**Compatibility**: 新增；依赖 `RagTraceRepositoryPort.pageRuns`。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=TraceToEvalSampleProjectorTests test`

**Steps**:
1. **Write test** —
   - `TraceSamplingOptions(samplingRate=0.01, sinceMinutes=60, maxSamplesPerRun=200)` 校验；
   - 输入 100 条 trace，`samplingRate=0.1` 且固定随机种子 → 输出约 10 条；
   - 投影包含 `query/topK/chunks/finalAnswer/userFeedback?`；
   - `userFeedback==DOWN` 的强制保留（不抽样过滤）。
2. **Verify RED** — 失败。
3. **Minimal code** — `Random` with seed、`Stream.filter`。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(eval): 新增 TraceToEvalSampleProjector`

---

### Task F9 — RetrievalWeightPolicyFeature

**Files**:
- create: `kernel/feature/retrieval/RetrievalWeightPolicyFeature.java`
- create test: `seahorse-agent-tests/.../kernel/feature/retrieval/RetrievalWeightPolicyFeatureTests.java`

**Why**: 根据评测指标调整 RRF / Reranker / KeywordChannel 的权重；闭环回流。

**Compatibility**: 实现 `AgentFeature`，被 `KernelMultiChannelRetrievalEngine` 在 channel/postprocessor 入参侧读取。

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=RetrievalWeightPolicyFeatureTests test`

**Steps**:
1. **Write test** —
   - Feature 通过端口 `RetrievalEvaluationMetricsPort.latest()` 拿到 `recall@5`、`mrr` 等；
   - `recall@5 < 0.4` → 关键词权重 +0.1（最大 1.0）；
   - `mrr < 0.3` → Reranker 权重 +0.1；
   - 指标缺失 → 不调整（保守）。
2. **Verify RED** — 失败。
3. **Minimal code** — 简单阈值表 + `WeightTable` 返回结构体。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(retrieval): 新增 RetrievalWeightPolicyFeature`

---

### Task F10 — Spring 自动装配企业治理

**Files**:
- create: `seahorse-agent-spring-boot-starter/.../SeahorseAgentEnterpriseGovernanceAutoConfiguration.java`
- modify: `application.properties`
- modify: `seahorse-agent-adapter-web` 请求拦截器中填充 `AuthorityContextHolder.setCurrent(...)`
- create test: `seahorse-agent-tests/.../adapters/spring/SeahorseAgentEnterpriseGovernanceAutoConfigurationTests.java`

**Why**: 三组功能各自带开关，避免一次性强制改造。

**Compatibility**: 开关：
- `seahorse-agent.governance.authority.enabled=true`（默认开，安全为先）
- `seahorse-agent.governance.knowledge-graph.enabled=false`
- `seahorse-agent.governance.eval-loop.enabled=false`

**Verification**: `mvn -pl seahorse-agent-tests -am -Dtest=SeahorseAgentEnterpriseGovernanceAutoConfigurationTests test`

**Steps**:
1. **Write test** —
   - 默认 → `AuthorityGuardPostProcessor` Bean 存在，其它 6 个不存在；
   - `knowledge-graph.enabled=true` + `JdbcChunkRelationRepositoryAdapter` Bean → `ParentChild/ConflictAware` 两 Feature 存在；
   - `eval-loop.enabled=true` + `RagTraceRepositoryPort` Bean → `TraceToEvalSampleProjector` + `RetrievalWeightPolicyFeature` 存在；
   - Web 拦截器在 `@WebMvcTest`-like 环境下能填充 `AuthorityContextHolder`，并在响应后 `reset()`。
2. **Verify RED** — 失败。
3. **Minimal code** — 三组条件 Bean + `HandlerInterceptor` 注入 holder。
4. **Verify GREEN** — 绿。
5. **Commit** — `feat(starter): 装配企业治理 Phase F 组件`

---

## Self-Review

- **Spec coverage** — 权限 (F1–F3) / 图谱 (F4–F7) / 评测回流 (F8–F9) / 装配 (F10) 全部映射。
- **Placeholder scan** — 无。
- **Type consistency** — `ChunkRelation`、`AuthorityContext`、`RetrievedChunk` 跨任务一致。
- **Compatibility** — `RetrievedChunk` 仅加可选字段；新表独立；旧 PostProcessor 链顺序保留；权限 PostProcessor 默认开启但 `AuthorityContext` 不存在时直通，开发环境零摩擦。
- **Verification** — 每 Task 自带 mvn 命令；F5/F10 用 Mock + 条件装配测试避免依赖真实 DB / Web 容器。
- **Dual-track** — 不存在替换；老 PostProcessor 链与新增 Feature 并存。
- **Decision hygiene** — 三大块各自独立装配开关；权限 owner = `AuthorityGuardPostProcessor`、图谱 owner = `ChunkRelationRepositoryPort`、评测回流 owner = `TraceToEvalSampleProjector`，无重复 owner、无 fallback、无 compat-only 路径。

## Retirement Track

无。Phase F 全部新增；既有 RAG/Agent 链路保持不变。

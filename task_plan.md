# Seahorse Agent 元数据治理与混合检索实现计划

## 目标

根据 `handoff-frnn1m.md` 与两份架构设计文档，优先落地 M1 元数据治理最小闭环，并继续推进 M2 动态过滤与向量闭环。

## 阶段

- [complete] M1 元数据治理最小闭环
- [complete] M2 向量检索过滤闭环
- [complete] M3 关键词检索
- [complete] M4 RRF 与 Reranker
- [complete] M5 回填与治理运维
- [complete] M6 检索后处理增强收口
- [complete] P5 检索质量评测最小闭环

## 当前落地范围

- M1 已完成：元数据治理领域模型、入库节点、Tika parser metadata、JDBC 治理仓储、chunk metadata 写入、starter 自动装配和基础测试。
- M2 已完成：`RetrievalFilter`、`RetrievalOptions`、Filter AST、`MetadataFilterCompiler`、`MetadataGuardPostProcessorFeature`、query embedding、向量适配器 metadata 返回和基础过滤下推，并接入多通道检索入口与 starter 自动装配。
- M3 已完成：新增 `KeywordSearchPort`、`KeywordIndexPort`、`KeywordSearchRequest` 和 `KeywordSearchChannelFeature`，starter 在存在 `KeywordSearchPort` 时注册关键词通道；已提供 JDBC/PostgreSQL FTS 轻量关键词 fallback、关键词索引 Outbox 异步化、Elasticsearch 生产适配器、kernel 级重建编排入口、Web 管理触发入口、索引失败观测和计划型 Job 触发入口。
- M3 尚未完成：无。
- M4 已完成最小闭环：新增 `RrfFusionPostProcessorFeature`、`RerankPostProcessorFeature` 和 `FinalTruncatePostProcessorFeature`，支持通道排名融合、重复 chunk 去重、融合分记录、Rerank 候选截断、异常/空结果降级、`rerankScore` 回写和 finalTopK 截断。
- M4 后续增强进入 M6 收口：Rerank 超时隔离、通道权重配置化和观测指标。
- P5 已完成最小闭环：新增检索评测入站端口、内核评测服务和 Web 触发接口，支持临时评测集计算 Recall@K、MRR、nDCG@K、空召回率和延迟指标；评测集与评测运行历史已具备知识库级持久化入口。

## 硬约束

- 全程中文沟通，中文文件使用 UTF-8 without BOM。
- 不恢复 `docs/zh/content/架构设计/混合检索.txt`。
- 动态 metadata 进入检索前必须经过 Schema 与 Filter Compiler。
- kernel 只放领域模型、端口、Feature 与编排；外部实现放 adapter。
- 新增 DDL 表和字段必须有注释。
# 2026-05-13 本轮续做

- [complete] M3 文档管理索引联动：文档禁用/删除会清理关键词索引，重新启用会把启用分片同步回关键词索引。
- [complete] M3 Outbox 异步索引最小闭环：新增关键词索引 Outbox 发布器、消息事件和订阅器，支持通过配置切换到 outbox 模式。
- [complete] M3 关键词索引维护入口：`KeywordIndexPort` 增加按文档/知识库重建的默认方法，JDBC fallback 已实现重算 `search_text`。
- [complete] M3 JDBC/PostgreSQL `KeywordIndexPort` fallback：维护 `t_knowledge_chunk.search_text`，并在 starter 中注册默认 JDBC 索引适配器。
- [complete] M3 生产级关键词索引：生产适配器、Outbox、重建补偿、观测和计划型触发入口已落地。

## 2026-05-13 Elasticsearch adapter 继续推进

- [complete] M3 Elasticsearch 生产级关键词检索/索引适配器：新增 `seahorse-agent-adapter-search-elasticsearch` 模块，基于 OkHttp REST API 实现 BM25 检索、bulk 索引写入和按文档 delete_by_query 清理。
- [complete] M3 Elasticsearch 与 starter 接入：支持 `seahorse-agent.adapters.keyword-search.type=elasticsearch` 和 `seahorse-agent.adapters.keyword-index.type=elasticsearch`，outbox 消费端优先委托 ES adapter，缺省回退 JDBC adapter。
- [complete] M3 kernel 级重建编排入口：新增 `KeywordIndexMaintenanceInboundPort` 和 `KernelKeywordIndexMaintenanceService`，按文档/知识库从文档仓储拉取启用分片快照，先删除残留再调用 `indexDocumentChunks` 重建。
- [complete] M3 Web 管理触发入口：新增按文档和按知识库触发关键词索引重建的 HTTP API。
- [complete] M3 计划型 Job 触发入口：新增默认关闭的 `SeahorseKeywordIndexMaintenanceJob`，显式配置后可按 docId/kbId 定时调用 `KeywordIndexMaintenanceInboundPort`。
- [complete] M3 索引失败观测与补偿：重建编排和 outbox 消费端已接入 `ObservationPort`，outbox 消费失败继续抛给 MQ/Outbox 重试链路，Web 管理 API 可按文档/知识库补偿。

## 2026-05-13 M5 元数据回填与治理运维

- [complete] M5 历史回填最小闭环：新增 kernel 级 `MetadataBackfillInboundPort` 与 `KernelMetadataBackfillService`，支持创建任务、分页推进、checkpoint、暂停/恢复/取消、单文档失败隔离和 Review/Quarantine 计数。
- [complete] M5 回填任务持久化端口：新增 `MetadataBackfillJobRepositoryPort`，并由 JDBC 元数据治理适配器实现。
- [complete] M5 DDL：新增 `t_metadata_extraction_job` 回填任务表，表与字段均补充 COMMENT。
- [complete] M5 管理 API：任务创建、批次推进、暂停/恢复/取消、任务详情。
- [complete] M5 质量报表：字段覆盖率、低置信度比例、待复核数量、隔离原因 TopN。
- [complete] M5 Review/Quarantine 管理 API：复核列表/详情/通过/修正/拒绝/转隔离，隔离列表/详情/标记处理/重试调度。

## 2026-05-13 M6 检索后处理增强收口

- [complete] RRF 通道权重配置化：从 `RetrievalOptions.channelSettings` 读取 RRF k 值和通道权重。
- [complete] Rerank 超时隔离：按 `RetrievalOptions.rerankTimeout` 控制模型精排耗时，超时回退原候选。
- [complete] 检索后处理观测：通过 `ObservationPort` 记录 RRF/Rerank 成功、降级和候选规模等低基数字段。
- [complete] FinalTruncate 最终截断观测：记录 `retrieval.final`，只包含租户、输入/输出规模、`finalTopK` 与是否截断等低基数字段。

## 2026-05-13 M5 Metadata Schema 管理 API

- [complete] M5 Schema 字段管理 API：新增字段列表、创建、更新、删除入站端口与 Web 契约。
- [complete] M5 Schema 管理仓储：JDBC 治理仓储支持字段 CRUD，并保证 `loadSchema()` 可读取新注册字段。
- [complete] M5 Schema 自动装配：starter 暴露 `MetadataSchemaManagementRepositoryPort` 和 `MetadataSchemaInboundPort`，供管理端和后续 Filter Compiler 入口复用。

## 2026-05-13 P2 LLM 元数据抽取增强

- [complete] LLM 抽取候选源：`MetadataExtractorNodeFeature` 可选复用 `ChatModelPort`，按 Schema 生成抽取提示并解析 JSON 候选。
- [complete] LLM 安全边界：默认关闭；启用后仍只接受已注册业务字段，系统/权限字段和未注册字段只记录治理问题，不进入候选集。
- [complete] starter 接入：元数据抽取节点可注入 `ChatModelPort`，未配置模型时保持 noop 兼容。

## 2026-05-13 M5 Review 审计闭环

- [complete] 复核决策审计：新增 `t_metadata_review_audit`，记录人工通过、修正、拒绝、转隔离的前后状态、操作人、备注和决策元数据。
- [complete] JDBC 适配器写审计：`applyReviewDecision` 在更新复核项时同步写入审计记录，并兼容旧库未迁移审计表的场景。

## 2026-05-13 P3 Schema 驱动 Elasticsearch Mapping

- [complete] Schema 索引同步端口：新增 `MetadataSchemaIndexSyncPort`，由 `KernelMetadataSchemaService` 在字段创建/更新后触发。
- [complete] Elasticsearch mapping 适配器：新增 `ElasticsearchMetadataSchemaIndexAdapter`，通过 REST `_mapping` 为已注册且可搜索索引的动态字段生成严格 mapping。
- [complete] 查询字段路径对齐：默认 `BackendFieldMapping` 下，Elasticsearch 关键词过滤统一查询 `metadata.<fieldKey>`，避免 mapping 与查询错位。

## 2026-05-13 M5 回填幂等收口

- [complete] 回填幂等：按 `schemaVersion/extractorVersion` 查询已 ACCEPT 抽取结果，命中时跳过对应文档。
- [complete] 回填幂等验证：覆盖同版本跳过、Schema 版本变化重跑、starter 自动装配和 JDBC 查询路径。
- [complete] 提交回填幂等改动，提交号 `1141169`，并继续推进下一项设计文档待办。

## 2026-05-13 M5 回填任务列表查询

- [complete] kernel 新增回填任务分页查询契约，管理端可按租户、知识库和状态查看任务。
- [complete] JDBC 治理适配器实现 `t_metadata_extraction_job` 分页查询，Web 管理 API 暴露知识库维度任务列表。
- [complete] 补充 kernel、JDBC 和 Web 契约测试，并完成受影响模块验证。

## 2026-05-13 M5 质量报表复核通过率

- [complete] 质量报表模型新增 `reviewPassRate`，补齐设计文档“复核通过率可查询”的质量验收项。
- [complete] JDBC 治理适配器按已处理复核项统计通过率，待处理项不进入分母。
- [complete] Web 契约与 JDBC 报表测试覆盖复核通过率字段。

## 2026-05-13 P5 检索质量评测最小闭环

- [complete] 新增 `RetrievalEvaluationInboundPort`、评测命令/样本/结果/报表模型和 `KernelRetrievalEvaluationService`。
- [complete] 支持按评测样本运行现有检索编排，并计算 Recall@K、MRR、nDCG@K、空召回率、平均耗时和 P95 耗时。
- [complete] Web 新增 `POST /knowledge-base/{kb-id}/retrieval-quality/evaluate`，请求会构造强类型 `RetrievalFilter`，动态 metadata 仍交给 Filter Compiler。

## 2026-05-13 P5 检索策略 A/B 对比

- [complete] 基于现有检索质量评测端口扩展多策略对比命令与报表，不新增 DDL，不引入外部评测 SDK。
- [complete] 支持多个策略复用同一评测集，输出每个策略的 Recall@K、MRR、nDCG@K、空召回率与延迟指标，并给出相对 baseline 的差值。
- [complete] Web 新增 `POST /knowledge-base/{kb-id}/retrieval-quality/compare`，请求只构造强类型 `RetrievalFilter` 与 `RetrievalOptions`，动态 metadata 仍由内核 Filter Compiler 校验。

## 2026-05-13 P5 知识库检索策略模板

- [complete] 新增无 DDL 的默认检索策略模板端口与内核服务，提供向量召回、混合 RRF、混合精排三类模板。
- [complete] starter 自动暴露 `RetrievalStrategyTemplateInboundPort`，后续可替换为持久化或知识库级覆盖实现。
- [complete] Web 新增 `GET /knowledge-base/{kb-id}/retrieval-strategy-templates`，管理端可直接读取模板中的强类型 `RetrievalOptions`。

## 2026-05-15 合并到 `main` 后的下一阶段开发方案

### 计划依据

- 基线分支：`main`
- 合并基线：`3b64c75 Merge branch 'codex/metadata-review-audit-history'`
- 参考文档：
  - `handoff-frnn1m.md`
  - `findings.md`
  - `progress.md`
  - `docs/zh/content/架构设计/企业级元数据抽取与治理管道设计.md`
  - `docs/zh/content/架构设计/混合检索与重排完善设计方案.md`

### 当前差距判断

- 总体完成度约 `75% ~ 85%`，已完成“可用最小闭环”，但离设计文档中的企业级闭环还有最后一段平台化收口。
- 检索侧完成度约 `85% ~ 90%`，主要差距不在召回链路，而在 Schema 联动补偿、使用观测沉淀和版本化运营能力。
- 元数据治理侧完成度约 `65% ~ 75%`，主要差距在 LLM 抽取效果闭环、跨版本质量对比、复核反馈反哺和运维报表。

### 范围边界

- 继续遵守现有微内核边界：`seahorse-agent-kernel` 只扩展领域模型、端口、Feature 与编排，不引入外部搜索或模型 SDK。
- 动态 metadata 在进入 Elasticsearch / PostgreSQL / Milvus / PGVector 前，仍必须先经过 `MetadataSchemaRegistryPort` 与 `MetadataFilterCompiler`。
- 本阶段先做内核、JDBC、Web API、观测与报表闭环，不新增前端页面工程。
- 不恢复 `docs/zh/content/架构设计/混合检索.txt`，不处理 `.claude/` 未跟踪目录。

### 阶段 A：LLM 抽取治理闭环

目标：把现有 `LLM 抽取 -> Review/Quarantine -> Re-Extract` 能力，从“能跑”收口为“可比较、可反哺、可运营”。

现状缺口：

- 已有 `extractorVersion`、`llmPromptVersion`、复核审计和重抽取入口，但缺少按字段、原因码、版本维度的效果聚合。
- 复核结果已经写回 canonical metadata，但还没有形成“哪些字段被人工修正最多、哪些 prompt/extractor 版本效果更差”的统一报表。
- 设计文档要求的“基于 review 结果持续优化规则、字典、prompt”的闭环，目前仍主要依赖人工读明细。

实施切片：

- [complete] A1 扩展元数据质量报表，支持按 `schemaVersion`、`extractorVersion`、`llmPromptVersion` 输出字段级通过率、低置信度率、复核修正率。
- [complete] A2 新增抽取效果对比报表接口，支持比较不同 `schemaVersion/extractorVersion/llmPromptVersion` 的质量差异，而不是只看单次快照。
- [complete] A3 新增复核反馈聚合能力，按 `fieldKey`、`reasonCode`、`decisionAction` 聚合“最常被修正/拒绝/转隔离”的字段与原因。
- [complete] A4 将复核反馈与重抽取任务关联，支持从反馈聚合结果直接定位对应抽取结果、审计轨迹和回填任务。

涉及模块 / 文件：

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/metadata/KernelMetadataQualityService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/metadata/KernelMetadataReviewService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/metadata/KernelMetadataExtractionResultService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/metadata/*`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/metadata/*`
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcMetadataGovernanceRepositoryAdapter.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseMetadataQualityController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseMetadataReviewController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseMetadataExtractionResultController.java`

验证方式：

- `mvn -pl seahorse-agent-tests -am "-Dtest=KernelMetadataQualityServiceTests,KernelMetadataReviewServiceTests,SeahorseWebApiContractTests,JdbcMetadataQualityReportAdapterTests,JdbcMetadataReviewQuarantineAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- 报表接口至少覆盖：版本筛选、字段聚合、原因码聚合、空数据降级和审计链路定位。

### 阶段 B：Schema 驱动索引联动深化

目标：把当前“字段创建/更新时同步 mapping/索引”的最小能力，升级为“Schema 变更 -> 索引结构 -> 补偿重建 -> 下推边界”完整联动。

现状缺口：

- 当前 `MetadataSchemaIndexSyncPort` 已能处理创建/更新，但删除、禁用、`indexed/filterable/pushdown` 能力变化后的补偿策略还不完整。
- Elasticsearch mapping 已接通，JDBC 表达式索引已接通，但“Schema 变更后需要重建哪些关键词/向量数据”的编排还没有统一出口。
- 向量侧虽然支持过滤下推，但缺少一份明确的“哪些字段允许下推到向量库、哪些字段只能 guard-only”的运维视图。

实施切片：

- [complete] B1 扩展 `MetadataSchemaIndexSyncPort` 语义，覆盖字段软删除、禁用、索引能力降级和 guard-only 切换。
- [complete] B2 新增 Schema 变更补偿编排，统一触发关键词索引重建、向量 metadata 补偿和必要的 canonical metadata 重放。
- [complete] B3 为 Elasticsearch / JDBC / 向量适配器补齐 Schema 变更后的兼容策略和失败观测，明确哪些失败只记观测，哪些失败需要补偿任务。
- [complete] B4 新增“字段索引能力视图”，展示 `indexed / pushdownToKeyword / pushdownToVector / guardOnly` 的当前生效状态和最近一次同步结果。

涉及模块 / 文件：

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/metadata/KernelMetadataSchemaService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/metadata/MetadataSchemaIndexSyncPort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/metadata/MetadataIndexCompensationPort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/metadata/KernelMetadataBackfillService.java`
- `seahorse-agent-adapter-search-elasticsearch/src/main/java/com/miracle/ai/seahorse/agent/adapters/search/elasticsearch/ElasticsearchMetadataSchemaIndexAdapter.java`
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcMetadataSchemaIndexAdapter.java`
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcMetadataGovernanceRepositoryAdapter.java`
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelAutoConfiguration.java`
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentNativeAdapterAutoConfiguration.java`

验证方式：

- `mvn -pl seahorse-agent-tests -am "-Dtest=KernelMetadataSchemaServiceTests,KernelMetadataBackfillServiceTests,SeahorseAgentKernelAutoConfigurationTests,JdbcMetadataSchemaIndexAdapterTests,ElasticsearchMetadataSchemaIndexAdapterTests,MetadataRetrievalFilterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- 至少覆盖：字段删除/禁用后的补偿、guard-only 字段不下推、mapping 同步失败观测、Schema 版本切换后的重建入口。

### 阶段 C：治理报表与平台化运维

目标：把当前零散的质量报表、回填任务、抽取结果和检索观测，收敛成可用于日常运营和上线决策的平台化接口。

现状缺口：

- 设计文档中提到的 Schema 使用情况报表，当前只有 `retrieval.metadata.filter.compiled` 观测事件，还没有聚合查询出口。
- 已支持回填任务列表、质量报表和评测接口，但缺少“跨版本对比”和“失败画像”两类管理视角。
- 检索侧与治理侧已有丰富观测事件，但还没有统一沉淀成面向管理端的查询模型。

实施切片：

- [complete] C1 新增 Schema 使用情况报表：通过专用 `MetadataSchemaUsageReportRepositoryPort` 在过滤编译成功/拒绝时写入轻量事件快照，JDBC 聚合输出字段使用频次、guard-only 命中率、拒绝率，并暴露管理查询接口。
- [complete] C2 新增回填运维视图，聚合任务状态、失败原因、待补偿文档数、Review/Quarantine 流向和最近一次重抽取结果。
- [complete] C3 新增跨版本质量对比接口，新增统一 `VersionQualityComparisonInboundPort` 与 `POST /knowledge-base/{kb-id}/version-quality/compare`，复用既有 `metadata-quality/compare` 与 `retrieval-quality/compare`，并列输出治理报表与检索评测对比结果用于上线前比较 `baseline` 与候选版本。
- [complete] C4 为关键观测补充低基数标签约束与统一命名，避免后续 Micrometer/日志平台接入时标签爆炸。

涉及模块 / 文件：

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/retrieval/KernelMultiChannelRetrievalEngine.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/retrieval/KernelRetrievalEvaluationService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/metadata/KernelMetadataBackfillService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/metadata/KernelMetadataQualityService.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/observation/*`
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcMetadataGovernanceRepositoryAdapter.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseMetadataBackfillController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseMetadataQualityController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseRetrievalEvaluationController.java`

验证方式：

- `mvn -pl seahorse-agent-tests -am "-Dtest=MetadataRetrievalFilterTests,KernelRetrievalEvaluationServiceTests,KernelMetadataBackfillServiceTests,KernelMetadataQualityServiceTests,SeahorseWebApiContractTests,JdbcMetadataQualityReportAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
- 至少覆盖：Schema 使用统计、版本对比报表、失败画像聚合、低基数观测字段约束。

### 建议执行顺序

1. 先做阶段 A，优先把 `review -> feedback -> re-extract` 的数据闭环补齐。
2. 再做阶段 B，把 Schema 变更对 Elasticsearch / JDBC / 向量侧的补偿编排统一起来。
3. 最后做阶段 C，把已有观测和质量数据平台化，形成管理 API 收口。

### 每阶段完成标准

- 阶段 A 完成标准：能按 `schemaVersion/extractorVersion/llmPromptVersion` 比较抽取治理效果，并能定位到字段和审计轨迹。
- 阶段 B 完成标准：Schema 字段的增删改与 `indexed/pushdown/guardOnly` 变化都能触发明确的同步或补偿动作。
- 阶段 C 完成标准：管理端可查询 Schema 使用情况、回填失败画像、跨版本质量对比，并能复用现有检索评测接口做上线前比对。

### 阶段 D：检索质量评测集平台化

目标：把临时评测请求升级为可复用、可持久化的知识库评测集，支撑长期策略回归和上线前验收。

实施切片：

- [complete] D1 新增检索评测集管理最小闭环：内核端口/服务、JDBC 持久化、Web 管理 API 和按评测集运行既有评测服务。
- [complete] D2 新增评测运行历史持久化：按已保存评测集运行后沉淀 `RetrievalEvaluationReport`，提供运行列表/详情 API 和 JDBC 汇总指标查询。
- [complete] D3 新增按已保存评测集做多策略 A/B 对比：复用既有 compare 口径，并将对比中的单策略报告继续沉淀到运行历史。

验证方式：

- `mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am "-Dtest=KernelRetrievalEvaluationDatasetServiceTests,JdbcRetrievalEvaluationDatasetRepositoryAdapterTests,SeahorseRetrievalEvaluationDatasetControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`

### 风险与兼容性说明

- 版本维度扩展优先复用现有 `schemaVersion`、`extractorVersion`、`llmPromptVersion` 字段，避免新增一套并行治理模型。
- Schema 联动补偿必须坚持“先观测、再补偿、最后重建”的顺序，避免字段变更直接阻断线上检索。
- 报表与运维接口优先做查询聚合，不先引入新前端或外部 BI 依赖，避免把下一阶段目标扩散为平台重构。

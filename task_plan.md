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
- P5 已完成最小闭环：新增检索评测入站端口、内核评测服务和 Web 触发接口，支持临时评测集计算 Recall@K、MRR、nDCG@K、空召回率和延迟指标。

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

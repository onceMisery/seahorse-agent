# Seahorse Agent 元数据治理与混合检索功能开发交接文档

## 1. 下一阶段目标

下一阶段希望基于以下两份设计文档继续落地功能开发：

- `docs/zh/content/架构设计/企业级元数据抽取与治理管道设计.md`
- `docs/zh/content/架构设计/混合检索与重排完善设计方案.md`

目标不是重新设计，而是按现有 Seahorse Agent 微内核、Clean Architecture、端口适配器、Feature 插件机制逐步实现：

- 企业级 Metadata Extraction & Governance Pipeline
- 动态元数据 Schema 治理与过滤编译
- 向量检索闭环和元数据过滤下推
- BM25 / 关键词检索，默认优先 Elasticsearch，PostgreSQL FTS 作为轻量 fallback
- RRF 融合与 Reranker 后处理链
- PostgreSQL/Elasticsearch/向量库索引联动
- 历史数据回填、Review、Quarantine、质量观测

## 2. 当前会话已完成事项

已完成文档工作：

- 新增 `docs/zh/content/架构设计/企业级元数据抽取与治理管道设计.md`
  - 明确目标链路：`Fetcher -> Parser -> MetadataExtractor -> MetadataNormalizer -> MetadataValidator -> Review/Quarantine -> Chunker -> Embedder -> Indexer`
  - 设计了新增节点、端口、领域模型、DDL、回填机制、索引联动、配置示例、验收标准和测试清单。
- 修改/完善过 `docs/zh/content/架构设计/混合检索与重排完善设计方案.md`
  - 体现 Elasticsearch + PostgreSQL 优先策略。
  - OpenSearch/Lucene 定位为按需引入的低优先级可插拔适配器。
  - DDL 示例要求新增表和字段都有注释。
  - 明确 RRF、BM25、向量、元数据过滤、Reranker 属于规划/扩展落地方向。
- 用户已删除 `docs/zh/content/架构设计/混合检索.txt`，不要恢复该文件。

当前 git 状态包含：

- `D docs/zh/content/架构设计/混合检索.txt`，这是用户主动删除。
- `M docs/zh/content/架构设计/混合检索与重排完善设计方案.md`
- `?? docs/zh/content/架构设计/企业级元数据抽取与治理管道设计.md`

## 3. 必须遵守的开发规范

仓库/用户硬性要求：

- 全程中文沟通。
- 中文文件使用 UTF-8 without BOM。
- 不恢复用户删除的 `混合检索.txt`。
- 不随意回滚用户改动。
- 新增 DDL 必须为新增表和新增字段补充 `COMMENT ON TABLE` / `COMMENT ON COLUMN`。
- 技术栈偏好：Elasticsearch + PostgreSQL；OpenSearch 只作为可插拔的低优先级实现。
- 开发时优先保持当前项目架构一致，不要把外部 SDK 泄漏进 `seahorse-agent-kernel`。

架构约束：

- `seahorse-agent-kernel` 只放领域对象、端口、Feature、编排逻辑。
- 外部实现放到 `seahorse-agent-adapter-*`。
- Spring Boot 装配放到 `seahorse-agent-spring-boot-starter` 或对应 adapter auto-configuration。
- 入库节点必须通过 `IngestionNodeFeature` 接入，不直接改成硬编码流程。
- 检索通道通过 `SearchChannelFeature` 接入。
- 检索后处理通过 `SearchResultPostProcessorFeature` 接入，并应支持明确排序。
- 动态 metadata 不允许直接透传用户原始 `Map<String,Object>` 到 Milvus/PGVector/Elasticsearch/PostgreSQL 查询；必须经过 Schema 校验和 Filter Compiler。

## 4. 现有相关代码入口

入库与元数据链路：

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/ingestion/KernelIngestionEngine.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/ingestion/FetcherNodeFeature.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/ingestion/ParserNodeFeature.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/ingestion/ChunkerNodeFeature.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/ingestion/EmbedderNodeFeature.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/ingestion/IndexerNodeFeature.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/ingestion/EnhancerNodeFeature.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/ingestion/EnricherNodeFeature.java`
- `seahorse-agent-adapter-parser-tika/src/main/java/com/miracle/ai/seahorse/agent/adapters/parser/tika/TikaDocumentParserAdapter.java`

检索链路：

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/retrieval/KernelMultiChannelRetrievalEngine.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/retrieval/KernelRetrievalEngine.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/retrieval/SearchContext.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/retrieval/RetrievedChunk.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/retrieval/SearchChannelResult.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/retrieval/IntentDirectedSearchFeature.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/feature/retrieval/VectorGlobalSearchFeature.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/vector/VectorSearchPort.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/model/RerankModelPort.java`

自动装配：

- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelAutoConfiguration.java`
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentNativeAdapterAutoConfiguration.java`

## 5. 建议落地顺序

建议不要一口气实现所有能力，按下面阶段推进。

### P0：元数据治理基础与入库节点

优先实现 `企业级元数据抽取与治理管道设计.md` 的 P0：

- 在 kernel 新增元数据领域模型：`MetadataSchema`、`MetadataFieldDescriptor`、`MetadataFieldCandidate`、`MetadataValidationResult` 等。
- 新增端口：`MetadataSchemaRegistryPort`、`MetadataDictionaryPort`、`MetadataExtractionResultRepositoryPort`、`MetadataReviewQueuePort`、`MetadataQuarantinePort`。
- 新增入库节点 Feature：
  - `MetadataExtractorNodeFeature`
  - `MetadataNormalizerNodeFeature`
  - `MetadataValidatorNodeFeature`
  - 可选：`MetadataReviewRouterNodeFeature`
- 扩展 Tika adapter 返回 parser metadata。
- JDBC adapter 新增表结构和 repository 实现。
- Starter 中注册新节点 Bean，并通过配置控制启用。
- 验证一条最小流水线能把 accepted metadata 写入 PostgreSQL JSONB。

### P1：动态元数据过滤与向量闭环

对应 `混合检索与重排完善设计方案.md` 的 P1：

- 扩展 `SearchContext`、`RetrievedChunk`、`VectorSearchRequest` 支持 filter/options/metadata。
- 新增 `RetrievalFilter`、`SystemRetrievalFilter`、`MetadataCondition`。
- 新增 `MetadataFilterCompiler` 和后端无关 Filter AST。
- 改造 Milvus/PGVector adapter 支持过滤下推和 metadata 返回。
- 新增 `MetadataGuardPostProcessorFeature`，作为权限与元数据兜底过滤。
- 补齐 query embedding，避免向量通道传空 vector。

### P2：BM25 / 关键词检索

默认优先 Elasticsearch，PostgreSQL FTS 作为轻量 fallback。

- 新增 `KeywordSearchPort`、`KeywordIndexPort`。
- 新增 `KeywordSearchChannelFeature`。
- 新增 `seahorse-agent-adapter-search-elasticsearch`。
- 新增 `seahorse-agent-adapter-search-postgres` 或在 JDBC adapter 中实现 PostgreSQL FTS fallback。
- OpenSearch 不作为默认实现，可后续以 `seahorse-agent-adapter-search-opensearch` 接入同一端口。
- 入库阶段优先使用 Outbox 异步同步 Elasticsearch，避免影响主入库链路。

### P3：RRF 与 Reranker

- 新增 `RrfFusionPostProcessorFeature`。
- 新增 `RerankPostProcessorFeature`，复用已有 `RerankModelPort`。
- 新增 `FinalTruncatePostProcessorFeature`。
- 调整 `KernelMultiChannelRetrievalEngine` 后处理链排序，避免后处理顺序不稳定。

### P4：历史回填、Review、Quarantine 与质量观测

- 新增 `MetadataBackfillService`。
- 支持 checkpoint、分片、暂停/恢复、幂等、重试。
- 新增 Review/Quarantine 管理 API 和基础页面/接口。
- 增加指标：字段覆盖率、低置信度比例、复核待处理数、隔离原因 TopN、索引同步失败。

## 6. 建议下一会话使用的技能

如果下一会话是实际开发实现，建议：

- 使用仓库当前开发规范，先读代码再改。
- 如果用户明确要求使用 superpower，再使用 `writing-plans`、`test-driven-development`、`verification-before-completion`。
- 如果任务拆分很大，可考虑 `planning-with-files` 维护进度，但不要把设计文档重复复制进去，只引用现有文档路径。
- 不需要前端技能，除非开始做 Review/Quarantine 管理界面。

## 7. 验收关注点

实现过程中每个阶段至少要验证：

- 编译通过：优先运行受影响 Maven 模块，例如 `mvn -pl seahorse-agent-kernel,seahorse-agent-spring-boot-starter -am test`，根据实际改动扩大范围。
- 新增节点能通过 `IngestionNodeFeature` 注册和查找。
- 新增端口有 noop 或可配置 fallback，避免破坏本地启动。
- 新增 DDL 每个表和列都有注释。
- Elasticsearch/PostgreSQL/OpenSearch 的优先级符合用户偏好。
- 未注册 metadata 字段不能参与检索过滤。
- 权限过滤必须同时支持下推和后处理兜底。

## 8. 风险提醒

- 当前代码已有 `EnhancerNodeFeature` 和 `EnricherNodeFeature` 的 LLM metadata 钩子，但它们只是把 JSON 合并进 metadata，不具备 Schema、置信度、来源、复核、隔离能力。实现时不要误认为现有 enhancer/enricher 已满足企业级治理。
- Tika adapter 当前主要抽取文本，需要扩展才能返回 parser metadata。
- `VectorSearchRequest.filters` 当前存在但适配器未完整消费，必须检查 Milvus/PGVector 具体实现后再改端口契约。
- 关键词/BM25 不要直接写进 kernel；Elasticsearch/PostgreSQL/OpenSearch 都必须走端口适配器。
- `混合检索与重排详细设计.md` 仍可参考，但最新偏好以 `混合检索与重排完善设计方案.md` 为准。
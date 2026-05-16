# Seahorse Agent 元数据治理与混合检索实现进度

## 2026-05-13

- 创建执行计划、发现记录与进度记录。
- 开始 M1 元数据治理最小闭环实现。
- 新增 kernel 元数据治理领域模型、端口和三个入库节点。
- 运行 `mvn -pl seahorse-agent-kernel -DskipTests compile`，通过。
- 完成 M1 元数据治理最小闭环：Tika parser metadata、JDBC 治理仓储、DDL、chunk metadata 注入、starter 自动装配与 `MetadataGovernanceNodeFeatureTests`。
- 推进 M2 第一段：新增检索过滤领域模型、Filter AST、`DefaultMetadataFilterCompiler`、`MetadataGuardPostProcessorFeature`、`SearchContext`/`RetrievedChunk`/`VectorSearchRequest` 扩展。
- 修复向量通道空 query vector：`VectorGlobalSearchFeature` 与 `IntentDirectedSearchFeature` 通过 `EmbeddingModelPort` 生成 query embedding。
- 改造 PGVector/Milvus 适配器：返回 metadata，并基于编译后的过滤表达式和系统字段做基础下推。
- 根据用户提醒，为新增核心代码补充中文职责注释和安全边界说明。
- 第一次测试命令未给 `-Dtest` 加引号，PowerShell 将逗号解析为参数列表导致失败；改为引号包裹后通过。
- 运行 `mvn -pl seahorse-agent-kernel,seahorse-agent-adapter-vector-pgvector,seahorse-agent-adapter-vector-milvus,seahorse-agent-spring-boot-starter -am -DskipTests compile`，通过。
- 运行 `mvn -pl seahorse-agent-tests -am "-Dtest=MetadataRetrievalFilterTests,VectorGlobalSearchFeatureTests,IntentDirectedSearchFeatureTests,KernelRetrievalEngineTests,MetadataGovernanceNodeFeatureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过，13 个测试成功。
- 补充 PGVector/Milvus 过滤翻译安全注释后，运行 `mvn -pl seahorse-agent-adapter-vector-milvus,seahorse-agent-adapter-vector-pgvector -am -DskipTests compile`，通过。
- 继续推进 M2 收口：`KernelMultiChannelRetrievalEngine` 新增带 `RetrievalFilter/RetrievalOptions` 的检索入口，内部加载 Schema 并调用 `MetadataFilterCompiler`，`KernelRetrievalEngine` 增加对应透传方法。
- 在 starter 中注册 `MetadataFilterCompiler`，并把 `MetadataSchemaRegistryPort` 与 compiler 注入多通道检索引擎。
- 新增编排级测试，验证通道执行前已完成过滤编译，guard-only 条件由 `MetadataGuardPostProcessorFeature` 兜底过滤。
- 运行 `mvn -pl seahorse-agent-tests -am "-Dtest=MetadataRetrievalFilterTests,KernelRetrievalEngineTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过，7 个测试成功。
- 开始 M3 关键词检索：新增 `KeywordSearchPort`、`KeywordIndexPort`、`KeywordSearchRequest`、`KeywordSearchChannelFeature`，starter 在存在 `KeywordSearchPort` 时注册关键词通道，默认仍由 `RetrievalOptions.enableKeyword=false` 关闭。
- 首次运行关键词测试时，`KeywordSearchRequest` compact constructor 中 lambda 捕获重写后的 `topK` 导致编译失败；改为普通 `if` 分支后修复。
- 运行 `mvn -pl seahorse-agent-tests -am "-Dtest=KeywordSearchChannelFeatureTests,MetadataRetrievalFilterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过，6 个测试成功。
- 运行 `git diff --check` 时发现任务外重命名文档 `缺少的功能.md` 第 3 行存在 trailing whitespace，未擅自修改该用户工作树改动。
- 新增 `JdbcKeywordSearchAdapter` 作为 PostgreSQL/JDBC 轻量关键词检索 fallback，基础按 chunk content 匹配，系统字段 `kb_id/doc_id` 可下推，`metadata_json` 返回后交由 Guard 兜底。
- starter 在 JDBC repository 类型下自动装配 `JdbcKeywordSearchAdapter` 为 `KeywordSearchPort`，关键词通道仍受 `RetrievalOptions.enableKeyword` 控制。
- DDL 为 `t_knowledge_chunk` 补充 `search_text TSVECTOR`、GIN 索引和字段注释，给后续 PostgreSQL FTS 排序优化留位。
- 运行 `mvn -pl seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-starter,seahorse-agent-tests -am "-Dtest=KeywordSearchChannelFeatureTests,MetadataRetrievalFilterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过，6 个测试成功。
- 开始 M4 RRF：新增 `RrfFusionPostProcessorFeature`，按通道内排名融合分数，按 id/docId+chunkIndex/text hash 去重，并写入 `channelRanks`、`channelScores`、`fusionScore`。
- 新增 `FinalTruncatePostProcessorFeature`，在后处理链末尾按 `RetrievalOptions.finalTopK` 截断。
- starter 注册 RRF 与 FinalTruncate 后处理器；二者只在显式传入 `RetrievalOptions` 时启用，避免改变旧检索入口默认行为。
- 新增 `RrfFusionPostProcessorFeatureTests`，覆盖重复 chunk 融合、通道排名记录和 finalTopK 截断。
- 运行 `mvn -pl seahorse-agent-tests -am "-Dtest=RrfFusionPostProcessorFeatureTests,KeywordSearchChannelFeatureTests,MetadataRetrievalFilterTests,KernelRetrievalEngineTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过，10 个测试成功。
- 新增 `RerankPostProcessorFeatureTests`，先验证缺少 `RerankPostProcessorFeature` 的失败，再实现后处理器。
- 新增 `RerankPostProcessorFeature`：仅在显式 `RetrievalOptions.enableRerank=true` 且配置 `rerankModel` 时启用，按 `fusionTopK/rerankTopK` 收窄候选，模型异常或空结果时原样降级，并把 rerank 得分写入 `rerankScore` 与 `score`。
- starter 在同时存在 `ExtensionRegistry` 与 `RerankModelPort` 时注册 Rerank 后处理器，执行顺序位于 RRF 之后、FinalTruncate 之前。
- 运行 `mvn -pl seahorse-agent-tests -am "-Dtest=RerankPostProcessorFeatureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过，4 个测试成功。
- 运行 `mvn -pl seahorse-agent-tests -am "-Dtest=RrfFusionPostProcessorFeatureTests,RerankPostProcessorFeatureTests,KeywordSearchChannelFeatureTests,MetadataRetrievalFilterTests,KernelRetrievalEngineTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过，14 个测试成功；既有故障降级测试会输出通道失败 ERROR 堆栈。
- 新增 `JdbcKeywordSearchAdapterTests`，先验证当前 `content LIKE` 查询不满足 PostgreSQL FTS 预期，再将 JDBC 关键词 fallback 改为 `websearch_to_tsquery`、`@@` 命中和 `ts_rank_cd` 排序。
- 运行 `mvn -pl seahorse-agent-adapter-repository-jdbc -am -Dtest=JdbcKeywordSearchAdapterTests "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过，1 个测试成功。
- 新增 `IndexerNodeFeatureTests.shouldWriteKeywordIndexThroughPort`，先验证缺少四参构造器失败，再让 `IndexerNodeFeature` 在写入 chunk repository 与 vector index 后调用 `KeywordIndexPort`。
- starter 的 indexer 自动装配新增 `ObjectProvider<KeywordIndexPort>`，未配置时继续使用 noop，避免影响现有部署。
- 运行 `mvn -pl seahorse-agent-tests -am "-Dtest=IndexerNodeFeatureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过，5 个测试成功。
- 回归 `SeahorseAgentKernelAutoConfigurationTests` 时发现既有断言仍只期望 7 个入库节点，未包含 M1 已注册的 metadata extractor/normalizer/validator；已同步测试期望。
- 运行 `mvn -pl seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-starter,seahorse-agent-tests -am "-Dtest=JdbcKeywordSearchAdapterTests,IndexerNodeFeatureTests,SeahorseAgentKernelAutoConfigurationTests,RrfFusionPostProcessorFeatureTests,RerankPostProcessorFeatureTests,KeywordSearchChannelFeatureTests,MetadataRetrievalFilterTests,KernelRetrievalEngineTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过，35 个测试成功；既有通道故障降级测试仍会输出 ERROR 堆栈。
- 运行 `git diff --check -- . ':!缺少的功能.md' ':!元数据过滤：RAG与Agentic Search.md'`，通过；仅有 Git 提示部分工作区文件下一次触碰时 LF 会替换为 CRLF。
# 2026-05-13 追加进度

- 补齐文档管理侧索引联动：`KnowledgeDocumentVectorPorts` 扩展 `KeywordIndexPort`，文档禁用/删除时清理关键词索引，重新启用时复用启用分片快照同步关键词索引。
- `SeahorseAgentKernelAutoConfiguration` 为文档服务注入 `KeywordIndexPort`，未配置时继续使用 noop，保持本地和旧部署兼容。
- 新增 `KernelKnowledgeDocumentServiceTests.shouldSyncKeywordIndexWhenEnableOrDeleteDocument`，运行 `mvn -pl seahorse-agent-tests -am "-Dtest=KernelKnowledgeDocumentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过：4 个测试成功。
- 新增 `KeywordIndexOutboxAdapter`、`KeywordIndexEvent`、`KeywordIndexMessageSubscriber`，在 `seahorse-agent.adapters.keyword-index.mode=outbox` 时把入库侧关键词索引写入可靠消息，再由订阅器调用实际索引 adapter。
- starter 增加 outbox 模式装配：`KeywordIndexOutboxAdapter` 作为主 `KeywordIndexPort`，JDBC adapter 作为当前默认消费端 delegate，后续 ES adapter 可替换同一端口。
- 新增 `KeywordIndexOutboxAdapterTests`，覆盖发布 Outbox、relay、订阅消费到实际 `KeywordIndexPort` 的最小闭环；运行 `mvn -pl seahorse-agent-tests -am "-Dtest=KeywordIndexOutboxAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过：1 个测试成功。
- 扩展 `KeywordIndexPort`，新增 `rebuildDocument` 与 `rebuildKnowledgeBase` 默认方法，作为历史回填、索引失败补偿和后续管理任务的统一入口。
- `JdbcKeywordIndexAdapter` 实现按文档/知识库重算 `search_text`，继续保留列不存在时安全跳过的兼容策略。
- 扩展 `JdbcKeywordIndexAdapterTests`，覆盖文档/知识库重建 SQL 和老库跳过行为；运行 `mvn -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcKeywordIndexAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过：4 个测试成功。
- 继续推进 M3 关键词检索索引维护：新增 `JdbcKeywordIndexAdapter`，实现 `KeywordIndexPort`，在 JDBC/PostgreSQL fallback 下同步维护 `t_knowledge_chunk.search_text`。
- `JdbcKeywordIndexAdapter` 会先探测 `search_text` 列是否存在；老库未迁移或测试替身不支持 `information_schema` 时安全跳过，避免关键词索引维护影响主入库链路。
- starter 在 JDBC repository 类型且存在 `DataSource` 时默认注册 `JdbcKeywordIndexAdapter`，同时保留 `@ConditionalOnMissingBean(KeywordIndexPort.class)` 方便生产环境替换 Elasticsearch 实现。
- 运行 `mvn -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcKeywordIndexAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过：3 个测试成功。

## 2026-05-13 继续推进 M3 Elasticsearch adapter

- 新增 `seahorse-agent-adapter-search-elasticsearch` Maven 模块，并加入根 `pom.xml` modules。
- 实现 `ElasticsearchKeywordProperties`、`ElasticsearchKeywordSearchAdapter`、`ElasticsearchKeywordIndexAdapter` 和 REST 封装 `ElasticsearchKeywordHttpClient`。
- ES 搜索请求基于 `multi_match` + `bool.filter`；系统字段下推 `enabled/tenant_id/kb_id/doc_id/collection_name` 等，动态 metadata 只消费 `CompiledMetadataFilter.expression()`，不解析用户原始过滤 Map。
- ES 索引写入使用 `_bulk`，删除使用 `_delete_by_query`；chunk 文档保存 `chunk_id/kb_id/doc_id/chunk_index/content/metadata/tenant_id/collection_name/enabled`。
- starter 新增 ES 关键词搜索与索引自动装配，配置入口为 `seahorse-agent.adapters.keyword-search.type=elasticsearch` 和 `seahorse-agent.adapters.keyword-index.type=elasticsearch`。
- outbox 订阅器委托选择改为优先 `ElasticsearchKeywordIndexAdapter`，不存在时回退 `JdbcKeywordIndexAdapter`，最后回退 noop。
- 首次运行 starter 测试时发现 `@Value` 无法在轻量 `ApplicationContextRunner` 中把 `10s` 转为 `Duration`；已改为字符串注入并在配置类内解析 `ms/s/m/PT...`。
- 验证通过：
  - `mvn -pl seahorse-agent-adapter-search-elasticsearch -am test`
  - `mvn -pl seahorse-agent-spring-boot-starter,seahorse-agent-tests -am "-Dtest=SeahorseAgentNativeAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - `mvn -pl seahorse-agent-adapter-search-elasticsearch,seahorse-agent-spring-boot-starter,seahorse-agent-tests -am "-Dtest=ElasticsearchKeywordSearchAdapterTests,ElasticsearchKeywordIndexAdapterTests,SeahorseAgentNativeAdapterAutoConfigurationTests,KeywordIndexOutboxAdapterTests,KeywordSearchChannelFeatureTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`
  - `git diff --check -- . ':!缺少的功能.md' ':!元数据过滤：RAG与Agentic Search.md'` 通过，仅有 LF/CRLF warning。

## 2026-05-13 继续推进 M3 关键词索引重建编排

- 新增 `KeywordIndexMaintenanceInboundPort` 与 `KeywordIndexRebuildResult`，作为按文档/知识库重建关键词索引的 kernel 入站入口。
- 新增 `KernelKeywordIndexMaintenanceService`，统一从 `KnowledgeDocumentRepositoryPort` 拉取文档详情与启用分片快照，先清理后端残留，再通过 `KeywordIndexPort.indexDocumentChunks` 写入关键词索引。
- 重建编排跳过禁用文档和空分片文档，保留处理、删除、写入、跳过和失败计数，便于后续管理端展示与补偿。
- starter 在存在 `KnowledgeDocumentRepositoryPort` 时自动装配 `KernelKeywordIndexMaintenanceService`，并通过 `KeywordIndexMaintenanceInboundPort` 暴露。
- 新增 `KernelKeywordIndexMaintenanceServiceTests`，覆盖单文档重建、禁用文档只删除、知识库分页重建与失败摘要。
- 验证通过：`mvn -pl seahorse-agent-spring-boot-starter,seahorse-agent-tests -am "-Dtest=KernelKeywordIndexMaintenanceServiceTests,SeahorseAgentKernelAutoConfigurationTests,KeywordIndexOutboxAdapterTests,KernelKnowledgeDocumentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，24 个测试成功。
- 验证通过：`git diff --check -- . ':!缺少的功能.md' ':!元数据过滤：RAG与Agentic Search.md'`。

## 2026-05-13 继续推进 M3 Web 管理触发入口

- 新增 `SeahorseKeywordIndexMaintenanceController`，按文档暴露 `POST /knowledge-base/docs/{doc-id}/keyword-index/rebuild`，按知识库暴露 `POST /knowledge-base/{kb-id}/keyword-index/rebuild`。
- Web 层只调用 `KeywordIndexMaintenanceInboundPort`，不直接访问 chunk repository 或 Elasticsearch，保持重建数据来源仍由 kernel 编排。
- 扩展 `SeahorseWebApiContractTests`，覆盖两个重建触发接口的 `{code,data}` 响应契约。
- 验证通过：`mvn -pl seahorse-agent-adapter-web,seahorse-agent-tests -am "-Dtest=SeahorseWebApiContractTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，7 个测试成功。

## 2026-05-13 继续推进 M3 索引失败观测与补偿

- `KernelKeywordIndexMaintenanceService` 接入 `ObservationPort`，按文档/知识库重建会记录 `keyword.index.rebuild.success/failure` 事件。
- `KeywordIndexMessageSubscriber` 接入 `ObservationPort`，outbox 消费成功、失败和未知事件跳过分别记录观测事件；失败仍继续抛出，交给 MQ negative ack 或 Outbox relay 标记失败并重试。
- starter 自动装配将可用的 `ObservationPort` 注入关键词索引重建服务与 outbox 消费端，未配置观测端口时保持无副作用。
- 扩展 `KernelKeywordIndexMaintenanceServiceTests` 与 `KeywordIndexOutboxAdapterTests`，覆盖重建部分失败观测、delegate 写入失败观测和 outbox retry 状态保留。
- 验证通过：`mvn -pl seahorse-agent-spring-boot-starter,seahorse-agent-tests -am "-Dtest=KernelKeywordIndexMaintenanceServiceTests,KeywordIndexOutboxAdapterTests,SeahorseAgentKernelAutoConfigurationTests,SeahorseAgentNativeAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，24 个测试成功；失败补偿测试会输出预期的 outbox relay ERROR 堆栈。

## 2026-05-13 继续推进 M3 计划型 Job 触发入口

- 新增 `SeahorseKeywordIndexMaintenanceJob`，默认不注册，只有 `seahorse-agent.keyword-index.maintenance.scheduler-enabled=true` 时启用。
- Job 支持通过 `seahorse-agent.keyword-index.maintenance.doc-ids` 与 `kb-ids` 配置补偿目标，按固定延迟调用 `KeywordIndexMaintenanceInboundPort`；知识库级重建使用可配置 `batch-size`。
- Job 使用 `DistributedLockPort` 防止多实例重复执行，单个目标失败只记录 warn 并继续处理后续目标。
- 扩展 `SeahorseAgentKernelAutoConfigurationTests`，验证默认关闭和显式开启注册；新增 `SeahorseKeywordIndexMaintenanceJobTests` 覆盖目标去重与调用顺序。
- 验证通过：`mvn -pl seahorse-agent-spring-boot-starter,seahorse-agent-tests -am "-Dtest=SeahorseKeywordIndexMaintenanceJobTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，18 个测试成功。

## 2026-05-13 继续推进 M5 元数据回填

- 新增 `MetadataBackfillCommand`、`MetadataBackfillInboundPort`、`MetadataBackfillRunResult`，作为管理端和调度端触发历史治理回填的 kernel 入站契约。
- 新增 `MetadataBackfillJobRecord`、`MetadataBackfillJobStatus`、`MetadataBackfillJobRepositoryPort`，用于保存回填任务状态、checkpoint、计数和失败摘要。
- 新增 `KernelMetadataBackfillService`：按知识库分页扫描文档，复用 `KernelIngestionEngine` 重新执行入库治理流水线；每个文档失败只记录摘要并继续后续文档；每处理一个文档刷新 checkpoint，批次结束推进页游标。
- 回填服务支持暂停、恢复、取消；禁用文档、运行中文档和缺少必要信息的文档会计入 skipped，不直接污染索引。
- JDBC 元数据治理适配器实现回填任务仓储端口，并在 `metadata-governance-postgresql.sql` 新增 `t_metadata_extraction_job` 表和完整 COMMENT。
- starter 自动装配 `MetadataBackfillInboundPort`，并在内核/原生适配器装配测试中覆盖。
- 聚焦测试覆盖分页 checkpoint、Review/Quarantine 计数、单文档失败不中断、暂停恢复和自动装配。首次运行输出显示 23 个测试通过且 reactor `BUILD SUCCESS`，但外层命令超时返回 124，后续需用更长超时重跑作为提交凭据。

## 2026-05-13 继续推进 M5 管理 API

- 新增 `MetadataBackfillCreateRequest`，承接创建回填任务时的租户、pipeline、批次大小和附加 metadata。
- 新增 `SeahorseMetadataBackfillController`，暴露任务创建、任务详情、批次推进、暂停、恢复和取消接口。
- Web 控制器只调用 `MetadataBackfillInboundPort`，不直接访问文档仓储、JDBC 或治理节点，保持回填编排由 kernel 统一处理。
- 扩展 `SeahorseWebApiContractTests.shouldKeepMetadataBackfillManagementContracts`，覆盖 `{code,data}` 响应结构和状态流转契约。
- 验证通过：`git diff --check -- . ':!缺少的功能.md' ':!元数据过滤：RAG与Agentic Search.md'` 无输出。
- 验证通过：`mvn -pl seahorse-agent-adapter-web,seahorse-agent-tests -am "-Dtest=SeahorseWebApiContractTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，8 个测试成功且 reactor `BUILD SUCCESS`。

## 2026-05-13 继续推进 M5 质量报表

- 新增 `MetadataQualityInboundPort`、`KernelMetadataQualityService`、`MetadataQualityReportRepositoryPort` 以及字段覆盖率、隔离原因 TopN 等报表记录。
- `JdbcMetadataGovernanceRepositoryAdapter` 实现质量报表查询：基于最新抽取结果计算字段覆盖率和低置信度比例，基于复核/隔离表统计待复核数量、未处理隔离数量和隔离原因 TopN。
- `MetadataExtractionRecord` 增加字段级质量列表，`MetadataValidatorNodeFeature` 将 normalizer 产生的 `MetadataFieldQuality` 持久化到 `field_quality`，支撑低置信度报表口径。
- 新增 `SeahorseMetadataQualityController`，暴露 `GET /knowledge-base/{kb-id}/metadata-quality/report`，Web 层只调用 `MetadataQualityInboundPort`。
- starter 自动装配新增 `MetadataQualityReportRepositoryPort` 和 `MetadataQualityInboundPort`。
- 验证通过：`mvn -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcMetadataQualityReportAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，2 个测试成功。
- 验证通过：`mvn -pl seahorse-agent-spring-boot-starter,seahorse-agent-adapter-web,seahorse-agent-tests -am "-Dtest=SeahorseWebApiContractTests,SeahorseAgentKernelAutoConfigurationTests,SeahorseAgentNativeAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，29 个测试成功。
- 验证通过：`mvn -pl seahorse-agent-tests -am "-Dtest=MetadataGovernanceNodeFeatureTests,KernelMetadataBackfillServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，5 个测试成功。
- 验证通过：`git diff --check -- . ':!缺少的功能.md' ':!元数据过滤：RAG与Agentic Search.md'` 无输出。

## 2026-05-13 继续推进 M6 检索后处理增强

- `RrfFusionPostProcessorFeature` 支持从 `RetrievalOptions.channelSettings` 读取 `rrfK` 和 `channelWeights`，保留意图通道默认 1.2 权重作为未配置 fallback。
- `RerankPostProcessorFeature` 支持 `RetrievalOptions.rerankTimeout`，超时后取消 future 并返回原候选，避免精排模型拖慢整条检索链路。
- RRF/Rerank 后处理器接入 `ObservationPort`，记录 `retrieval.rrf` 与 `retrieval.rerank` 事件，只包含 status、候选规模、输出规模、耗时和超时等低基数字段。
- starter 自动装配把可用 `ObservationPort` 注入 RRF/Rerank 后处理器；无观测适配器时保持原行为。
- 扩展 `RrfFusionPostProcessorFeatureTests` 覆盖配置权重和观测事件；扩展 `RerankPostProcessorFeatureTests` 覆盖超时降级和观测事件。
- 验证通过：`mvn -pl seahorse-agent-tests -am "-Dtest=RrfFusionPostProcessorFeatureTests,RerankPostProcessorFeatureTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，24 个测试成功。

## 2026-05-13 继续推进 M5 Review/Quarantine 管理 API

- 新增 `MetadataReviewInboundPort`、`MetadataQuarantineInboundPort`、复核/隔离分页与记录模型，以及 `KernelMetadataReviewService`、`KernelMetadataQuarantineService`。
- Review 管理支持列表、详情、通过、修正、拒绝和转隔离；通过/修正会写回文档 canonical metadata，转隔离只写隔离项，避免未通过复核的数据进入索引。
- Quarantine 管理支持列表、详情、标记已处理和重试调度；重试会增加 `retry_count`、刷新 `next_retry_time` 并重新标记为未处理。
- `JdbcMetadataGovernanceRepositoryAdapter` 实现复核/隔离管理仓储端口，并同步复核通过/修正后的 `t_metadata_extraction_result.approved_metadata/approved_by/approved_time`。
- DDL 为复核队列和隔离区补充按租户、知识库、状态与文档维度查询的索引，支撑管理端分页筛选。
- 新增 `SeahorseMetadataReviewController` 和 `SeahorseMetadataQuarantineController`，Web 层只调用入站端口，不直接访问 JDBC。
- starter 自动装配新增 `MetadataReviewManagementRepositoryPort`、`MetadataQuarantineManagementRepositoryPort`、`MetadataReviewInboundPort` 和 `MetadataQuarantineInboundPort`。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc -am "-Dtest=SeahorseWebApiContractTests,KernelMetadataReviewServiceTests,KernelMetadataQuarantineServiceTests,JdbcMetadataReviewQuarantineAdapterTests,SeahorseAgentKernelAutoConfigurationTests,SeahorseAgentNativeAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，33 个测试成功。

## 2026-05-13 继续推进 M5 Metadata Schema 管理 API

- 新增 `MetadataSchemaInboundPort`、`MetadataSchemaManagementRepositoryPort`、`MetadataSchemaFieldPayload`、`MetadataSchemaFieldRecord` 和 `KernelMetadataSchemaService`，作为管理端注册动态 metadata 字段的 kernel 入口。
- `JdbcMetadataGovernanceRepositoryAdapter` 实现 Schema 字段列表、详情、创建、更新和软删除；新增 JDBC 测试覆盖 CRUD 后 `loadSchema()` 可读到注册字段。
- 新增 `MetadataSchemaFieldRequest` 和 `SeahorseMetadataSchemaController`，暴露 `GET/POST /knowledge-base/{kb-id}/metadata-schema/fields`、`PUT/DELETE /metadata-schema/fields/{field-id}`。
- starter 自动装配新增 `MetadataSchemaManagementRepositoryPort` 暴露和 `MetadataSchemaInboundPort` 注册，保证 JDBC 治理仓储可同时供 Schema Registry 与管理 API 使用。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc -am "-Dtest=SeahorseWebApiContractTests,JdbcMetadataSchemaManagementAdapterTests,SeahorseAgentKernelAutoConfigurationTests,SeahorseAgentNativeAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，31 个测试成功。

## 2026-05-13 继续推进 P2 LLM 元数据抽取增强

- `MetadataExtractorNodeFeature` 新增可选 LLM 抽取路径，复用 kernel 已有 `ChatModelPort`，通过 `llmEnabled/llmModel/llmConfidence/llmMaxTextChars` 节点配置控制。
- LLM Prompt 明确只返回 JSON，并包含 Schema 字段清单、解析元数据、来源元数据和截断后的文档文本；返回值支持 `{"field":{"value":...,"confidence":...,"evidence":...}}` 结构。
- LLM 输出不会直接写入 canonical metadata，只作为候选进入后续 Normalizer/Validator；未注册字段、系统字段和权限字段会记录治理问题并被忽略。
- starter 自动装配为 `MetadataExtractorNodeFeature` 注入可用 `ChatModelPort`，未配置模型时使用 noop，默认不开启 LLM 抽取。
- 验证通过：`mvn -pl seahorse-agent-tests -am "-Dtest=MetadataGovernanceNodeFeatureTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，20 个测试成功。

## 2026-05-13 继续推进 M5 Review 审计闭环

- DDL 新增 `t_metadata_review_audit` 复核决策审计表，表和字段均补充 COMMENT，并按复核项、租户/知识库/文档维度建立查询索引。
- `JdbcMetadataGovernanceRepositoryAdapter.applyReviewDecision` 在更新复核项后写入审计记录，记录 from/to 状态、复核人、备注和本次采纳/修正 metadata。
- 审计写入对旧库兼容：如果审计表尚未迁移，主复核决策仍可继续执行，避免管理端操作被迁移窗口阻断。
- 验证通过：`mvn -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcMetadataReviewQuarantineAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，2 个测试成功。

## 2026-05-13 继续推进 P3 Schema 驱动 Elasticsearch Mapping

- 新增 `MetadataSchemaIndexSyncPort`，保持 kernel 只依赖端口，不直接感知 Elasticsearch/OpenSearch/PostgreSQL 的索引实现。
- `KernelMetadataSchemaService` 在 Schema 字段创建和更新后调用索引结构同步端口；未配置同步端口时走 noop，保持现有部署兼容。
- 新增 `ElasticsearchMetadataSchemaIndexAdapter`，仅同步 `indexed=true` 且 `SEARCH_KEYWORD/SEARCH_TEXT` 的字段，通过 `PUT /{index}/_mapping` 生成 `dynamic=strict` 的动态 metadata 字段 mapping。
- starter 支持显式开启 `seahorse-agent.adapters.metadata-schema-index.type=elasticsearch`，避免未声明时 Schema 管理 API 产生额外 Elasticsearch 调用。
- 修正 Elasticsearch 关键词过滤默认字段路径：当 Schema 使用默认 `BackendFieldMapping` 时，查询下推使用 `metadata.<fieldKey>`，与写入文档和 mapping 结构保持一致。
- 验证通过：`mvn -pl seahorse-agent-adapter-search-elasticsearch -am "-Dtest=ElasticsearchKeywordSearchAdapterTests,ElasticsearchMetadataSchemaIndexAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，4 个测试成功。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-search-elasticsearch -am "-Dtest=KernelMetadataSchemaServiceTests,ElasticsearchMetadataSchemaIndexAdapterTests,SeahorseAgentNativeAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，5 个测试成功。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-search-elasticsearch -am "-Dtest=KernelMetadataSchemaServiceTests,ElasticsearchMetadataSchemaIndexAdapterTests,ElasticsearchKeywordSearchAdapterTests,SeahorseAgentNativeAdapterAutoConfigurationTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，22 个测试成功。

## 2026-05-13 继续推进 M5 回填幂等收口

- `MetadataExtractionResultRepositoryPort` 新增 `hasAcceptedResult` 查询入口，默认 noop 返回 false，保持未实现仓储的兼容性。
- `KernelMetadataBackfillService` 在创建任务时把 `schemaVersion/extractorVersion` 和 `forceRerun/force` 写入 checkpoint；批次推进 checkpoint 时保留这些幂等控制字段。
- 回填处理单文档前先查询同一租户、知识库、文档、Schema 版本和抽取器版本是否已有 ACCEPT 结果；命中则计入 skipped，不再触发入库治理流水线。
- JDBC 治理适配器基于 `t_metadata_extraction_result` 查询 `ACCEPT/ACCEPTED` 状态，旧库或查询异常时返回 false，避免幂等检查阻断主回填流程。
- starter 为回填服务注入可用的 `MetadataExtractionResultRepositoryPort`，未配置时继续使用 noop。
- 测试补充同版本已 ACCEPT 跳过、Schema 版本变化重跑两条场景，并新增 JDBC 仓储级 `hasAcceptedResult` 版本匹配测试。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc -am "-Dtest=KernelMetadataBackfillServiceTests,JdbcMetadataQualityReportAdapterTests,SeahorseAgentKernelAutoConfigurationTests,SeahorseAgentNativeAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，JDBC 仓储测试 3 个用例和回填/自动装配测试 26 个用例成功，reactor `BUILD SUCCESS`。

## 2026-05-13 继续推进 M5 回填任务列表查询

- 回填幂等改动已提交：`1141169 feat: make metadata backfill idempotent`。
- 新增 `MetadataBackfillJobQuery` 与 `MetadataBackfillJobPage`，并在 `MetadataBackfillInboundPort` / `MetadataBackfillJobRepositoryPort` 增加分页查询入口；仓储端口提供默认空分页以兼容旧实现。
- `KernelMetadataBackfillService.pageJobs` 透传查询到任务仓储，保持回填任务列表仍由 kernel 端口统一暴露。
- `JdbcMetadataGovernanceRepositoryAdapter` 支持按 `tenant_id/kb_id/status` 查询 `t_metadata_extraction_job`，按更新时间倒序分页返回任务快照。
- `SeahorseMetadataBackfillController` 新增 `GET /knowledge-base/{kb-id}/metadata-backfill/jobs`，支持 `tenantId/status/current/size` 参数。
- 新增 `JdbcMetadataBackfillJobAdapterTests`，扩展 `KernelMetadataBackfillServiceTests` 与 `SeahorseWebApiContractTests` 覆盖任务列表查询。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=KernelMetadataBackfillServiceTests,JdbcMetadataBackfillJobAdapterTests,SeahorseWebApiContractTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，JDBC 1 个、Web 11 个、kernel 6 个用例成功，reactor `BUILD SUCCESS`。

## 2026-05-13 继续推进 M5 质量报表复核通过率

- `MetadataQualityReport` 新增 `reviewPassRate` 字段，使用与覆盖率、低置信度一致的 0 到 1 比率口径。
- `JdbcMetadataGovernanceRepositoryAdapter` 统计已处理复核项中的通过率：`APPROVED/CORRECTED` 作为通过，`REJECTED/QUARANTINED` 作为未通过，`PENDING` 不进入分母。
- `JdbcMetadataQualityReportAdapterTests` 增加 APPROVED、CORRECTED、REJECTED 样本，验证 `reviewPassRate = 2/3`；`SeahorseWebApiContractTests` 验证 Web 响应包含 `reviewPassRate`。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=JdbcMetadataQualityReportAdapterTests,SeahorseWebApiContractTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，JDBC 3 个和 Web 11 个用例成功，reactor `BUILD SUCCESS`。

## 2026-05-13 继续推进 M6 FinalTruncate 观测收口

- `FinalTruncatePostProcessorFeature` 接入可选 `ObservationPort`，在最终截断后记录 `retrieval.final` 事件；保留无参构造，兼容旧调用路径。
- 观测属性只记录 `tenant`、`inputCount`、`outputCount`、`finalTopK` 和 `truncated`，不记录 `chunkId/docId/question` 等高基数字段。
- starter 自动装配把可用 `ObservationPort` 注入 FinalTruncate 后处理器；没有观测适配器时保持原行为。
- 新增 `FinalTruncatePostProcessorFeatureTests`，覆盖截断事件与缺省租户上下文下的稳定观测记录。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-spring-boot-starter -am "-Dtest=FinalTruncatePostProcessorFeatureTests,RrfFusionPostProcessorFeatureTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，21 个测试成功，reactor `BUILD SUCCESS`。

## 2026-05-13 继续推进 P5 检索质量评测最小闭环

- 采用 TDD 补齐检索质量评测：先新增内核指标测试、Web 契约测试和 starter 自动装配断言，确认缺少 `RetrievalEvaluation` 端口/服务时编译失败。
- 新增 `RetrievalEvaluationInboundPort` 以及评测命令、样本、样本结果和汇总报表模型，保留 `strategyName` 用于后续 A/B 或基线对比。
- 新增 `KernelRetrievalEvaluationService`，逐条评测样本调用现有 `KernelRetrievalEngine.retrieveKnowledgeChannels`，计算 Recall@K、MRR、nDCG@K、空召回率、平均耗时和 P95 耗时。
- 新增 `RetrievalEvaluationRequest` 和 `SeahorseRetrievalEvaluationController`，暴露 `POST /knowledge-base/{kb-id}/retrieval-quality/evaluate`；Web 层只把租户、知识库和 ACL 转为强类型 `RetrievalFilter`，不绕过 Filter Compiler。
- starter 自动装配 `KernelRetrievalEvaluationService` 并暴露 `RetrievalEvaluationInboundPort`，只依赖内核检索引擎，不新增外部 SDK 或 DDL。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am "-Dtest=KernelRetrievalEvaluationServiceTests,SeahorseWebApiContractTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，30 个测试成功，reactor `BUILD SUCCESS`。

## 2026-05-13 继续推进 P5 检索策略 A/B 对比

- 按用户要求，本轮不采用 TDD 红绿流程；直接实现后补充定向回归测试。
- 新增 `RetrievalEvaluationComparisonCommand`、`RetrievalEvaluationStrategy`、`RetrievalEvaluationComparisonReport` 和 `RetrievalEvaluationStrategyDelta`，复用单策略评测逻辑运行多套策略。
- `KernelRetrievalEvaluationService.compare` 支持按 baseline 输出指标差值，并按 nDCG、Recall、MRR、空召回率和平均延迟的优先级选择 winner。
- 新增 `RetrievalEvaluationComparisonRequest` 和 `POST /knowledge-base/{kb-id}/retrieval-quality/compare`；Web 层仍只构造强类型过滤与策略参数，不直接接触检索后端。
- 补充 `KernelRetrievalEvaluationServiceTests` 和 `SeahorseWebApiContractTests`，覆盖 winner/delta 与 compare API 契约。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am "-Dtest=KernelRetrievalEvaluationServiceTests,SeahorseWebApiContractTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，32 个测试成功，reactor `BUILD SUCCESS`。

## 2026-05-13 继续推进 P5 知识库检索策略模板

- 新增 `RetrievalStrategyTemplateInboundPort` 和 `RetrievalStrategyTemplate`，模板只携带强类型 `RetrievalOptions`，不承载动态 metadata 条件。
- 新增 `KernelRetrievalStrategyTemplateService`，以内置模板方式提供 `vector_only`、`hybrid_rrf` 和 `hybrid_rerank`，暂不新增表结构或仓储依赖。
- 新增 `SeahorseRetrievalStrategyTemplateController`，暴露 `GET /knowledge-base/{kb-id}/retrieval-strategy-templates`。
- starter 自动装配 `KernelRetrievalStrategyTemplateService` 并暴露 `RetrievalStrategyTemplateInboundPort`。
- 补充 `KernelRetrievalStrategyTemplateServiceTests`、`SeahorseWebApiContractTests` 和 `SeahorseAgentKernelAutoConfigurationTests` 覆盖模板内容、Web 契约和自动装配。
- 首次验证因沙箱无法写入 `C:\user-data\.m2\repo\org\bouncycastle\bcutil-jdk18on\resolver-status.properties` 失败；提升权限后同一 Maven 命令通过，32 个测试成功，reactor `BUILD SUCCESS`。

## 2026-05-16 继续推进 C1 Schema 使用情况报表

- 新增 `MetadataSchemaUsageInboundPort`、`KernelMetadataSchemaUsageService`、`MetadataSchemaUsageReportRepositoryPort` 以及字段级/总览报表模型，作为管理端查询 Schema 使用情况的统一入口。
- `KernelMultiChannelRetrievalEngine` 在 metadata filter 编译成功或拒绝时，除保留原有 `ObservationPort` 事件外，同步写入专用 Schema 使用快照；统计写入失败不会反向影响检索主链路。
- `JdbcMetadataGovernanceRepositoryAdapter` 新增 `t_metadata_schema_usage_log` 写入与聚合查询实现，按请求维度统计总编译次数、拒绝次数、guard-only 请求数，并按字段输出使用频次、guard-only 比例和拒绝率。
- `metadata-governance-postgresql.sql` 新增 `t_metadata_schema_usage_log` 表、索引以及完整 `COMMENT ON TABLE/COLUMN` 注释，满足治理 DDL 约束。
- 新增 `SeahorseMetadataSchemaUsageController`，暴露 `GET /knowledge-base/{kb-id}/metadata-schema/usage-report?tenantId=...&schemaVersion=...`。
- 补充 `MetadataRetrievalFilterTests`、`KernelMetadataSchemaUsageServiceTests`、`JdbcMetadataSchemaUsageReportAdapterTests`、`SeahorseWebApiContractTests`、`SeahorseAgentKernelAutoConfigurationTests`、`SeahorseAgentNativeAdapterAutoConfigurationTests`，覆盖快照写入、JDBC 聚合、Web 契约和 starter 自动装配。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am "-Dtest=MetadataRetrievalFilterTests,KernelMetadataSchemaUsageServiceTests,JdbcMetadataSchemaUsageReportAdapterTests,SeahorseWebApiContractTests,SeahorseAgentKernelAutoConfigurationTests,SeahorseAgentNativeAdapterAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，53 个测试成功，reactor `BUILD SUCCESS`。

## 2026-05-16 继续推进 C3 跨版本质量对比接口

- 新增 `VersionQualityComparisonInboundPort`、`VersionQualityComparisonCommand`、`VersionQualityComparisonReport` 与 `KernelVersionQualityComparisonService`，统一组合治理侧 `MetadataQualityInboundPort.compare(...)` 和检索侧 `RetrievalEvaluationInboundPort.compare(...)`。
- 新增 `VersionQualityComparisonRequest` 与 `SeahorseVersionQualityComparisonController`，暴露 `POST /knowledge-base/{kb-id}/version-quality/compare`；Web 层继续复用现有检索评测请求模型，把 ACL / metadata 条件构造成强类型过滤对象后交给 kernel。
- starter 自动装配在同时存在 `MetadataQualityInboundPort` 与 `RetrievalEvaluationInboundPort` 时暴露 `VersionQualityComparisonInboundPort`，避免控制器或管理端自行拼接两套口径。
- 组合对比服务补充低基数字段观测 `version.quality.compare.generated`，只记录 schema 版本与策略/样本数量，不记录策略名或问题内容，避免提前放大标签基数风险。
- 校正计划漂移：确认 `MetadataBackfillInboundPort.overview(...)` 与 `/knowledge-base/{kb-id}/metadata-backfill/overview` 已经存在并通过测试，因此将 `task_plan.md` 中的 C2 状态改为 complete。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am "-Dtest=KernelVersionQualityComparisonServiceTests,SeahorseWebApiContractTests,SeahorseAgentKernelAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，38 个测试成功，reactor `BUILD SUCCESS`。

## 2026-05-16 继续推进 C4 关键观测低基数标签约束

- 收口 `retrieval.metadata.filter.compiled/rejected` 观测属性：不再把 `fieldKeys`、`guardOnlyFieldKeys` 作为 tag 输出，只保留 `fieldCount`、`guardOnlyCount`、`warningCount`、拒绝原因和异常类型；字段清单继续写入专用 Schema usage 日志。
- 收口回填观测 scope 标签：`metadata.backfill` 不再暴露 `jobId`、`pipelineId` 与具体 `schemaTriggerFieldKey`，改为 `schemaTriggerFieldSpecified` 布尔摘要；批次级事件保留页码、计数和失败数量。
- 统一检索后处理观测命名：RRF、Rerank、FinalTruncate 从 `tenant` 改为 `tenantId`，并补充 `knowledgeBaseId`；RRF 不再输出权重摘要，改为 `customWeightsConfigured`；Rerank 不再输出模型名、耗时和超时毫秒，改为 `modelConfigured` 与 `timeoutEnabled`。
- 跨版本质量对比观测字段从 `retrievalStrategyCount/retrievalCaseCount` 收敛为 `strategyCount/caseCount`，保持统一短命名。
- 补充并更新定向测试断言，覆盖高基数字段不再出现在观测属性、统一标签名和低基数摘要字段。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-spring-boot-starter -am "-Dtest=MetadataRetrievalFilterTests,KernelMetadataBackfillServiceTests,RerankPostProcessorFeatureTests,RrfFusionPostProcessorFeatureTests,FinalTruncatePostProcessorFeatureTests,KernelVersionQualityComparisonServiceTests,KernelMetadataQualityServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，45 个测试成功，reactor `BUILD SUCCESS`。
- 验证通过：`git diff --check` 无空白错误；仅提示部分 Java 文件下一次 Git 触碰时 LF 会替换为 CRLF。

## 2026-05-16 继续推进阶段 B Schema 索引联动收口

- 同步计划状态：确认 `6802ef1 feat: complete metadata schema compensation and capability view` 已完成 B1-B4，包括 `MetadataSchemaIndexSyncPort` 删除/更新语义、Schema 变更补偿编排、JDBC/Elasticsearch 索引兼容策略和字段索引能力视图。
- 继续收口 Schema 索引同步观测：JDBC 与 Elasticsearch 的 `metadata.schema.index.sync.*` 事件不再输出 `fieldKey` 和 `errorMessage`，只保留 backend、action、租户/知识库、schemaVersion、valueType、索引能力布尔值、outcome 和 errorType。
- 字段名与错误详情仍保存在 `MetadataSchemaIndexStatusPort` 的同步状态记录里，供字段能力视图查询，避免在 Micrometer tag 中放大基数。
- 验证通过：`mvn -pl seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-search-elasticsearch -am "-Dtest=JdbcMetadataSchemaIndexAdapterTests,ElasticsearchMetadataSchemaIndexAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，14 个测试成功，reactor `BUILD SUCCESS`。

## 2026-05-16 继续推进 D1 检索评测集管理

- 新增 `RetrievalEvaluationDatasetInboundPort`、评测集记录/摘要/载荷/运行命令模型和 `KernelRetrievalEvaluationDatasetService`，支持知识库维度评测集列表、详情、保存、删除和按已保存样本运行既有 `RetrievalEvaluationInboundPort`。
- 新增 `RetrievalEvaluationDatasetRepositoryPort` 与 `JdbcRetrievalEvaluationDatasetRepositoryAdapter`，以 `t_retrieval_evaluation_dataset.cases_json` 保存强类型 `RetrievalEvaluationCase` 列表，避免 Web 或仓储层绕过 Filter Compiler 直接操作动态 metadata。
- `retrieval-governance-postgresql.sql` 新增 `t_retrieval_evaluation_dataset` 表、索引和完整 COMMENT，满足新增 DDL 注释约束。
- 新增 `SeahorseRetrievalEvaluationDatasetController`，暴露评测集 CRUD 与 `POST /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/evaluate`，运行时复用现有评测指标口径。
- Spring Boot 自动装配 JDBC 仓储和内核评测集服务；无仓储时不暴露管理入口。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am "-Dtest=KernelRetrievalEvaluationDatasetServiceTests,JdbcRetrievalEvaluationDatasetRepositoryAdapterTests,SeahorseRetrievalEvaluationDatasetControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，3 个新测试类通过，reactor `BUILD SUCCESS`。

## 2026-05-16 继续推进 D2 检索评测运行历史

- 新增 `RetrievalEvaluationRunRecord`、`RetrievalEvaluationRunSummary` 和 `RetrievalEvaluationRunRepositoryPort`，保存已运行评测的汇总指标与完整 `RetrievalEvaluationReport`。
- `KernelRetrievalEvaluationDatasetService` 在按评测集运行后写入运行历史，并新增运行列表/详情查询；历史写入失败不会反向阻断即时评测报告返回。
- `JdbcRetrievalEvaluationDatasetRepositoryAdapter` 同时实现评测集仓储与运行历史仓储，新增 `t_retrieval_evaluation_run` 写入、列表和详情查询。
- `retrieval-governance-postgresql.sql` 新增 `t_retrieval_evaluation_run` 表、索引和完整 COMMENT，保留 Recall@K、MRR、nDCG@K、空召回率与延迟指标列，完整明细保存在 `report_json`。
- `SeahorseRetrievalEvaluationDatasetController` 新增 `GET /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/runs` 和 `GET /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/runs/{run-id}`。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am "-Dtest=KernelRetrievalEvaluationDatasetServiceTests,JdbcRetrievalEvaluationDatasetRepositoryAdapterTests,SeahorseRetrievalEvaluationDatasetControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，JDBC 1 个、Web 1 个、Kernel 1 个测试类通过，reactor `BUILD SUCCESS`。

## 2026-05-16 继续推进 D3 已保存评测集 A/B 对比

- 新增 `RetrievalEvaluationDatasetComparisonCommand`，支持基于已保存评测集直接复用既有 `RetrievalEvaluationInboundPort.compare(...)` 口径。
- `KernelRetrievalEvaluationDatasetService.compareDataset(...)` 会读取已保存 `RetrievalEvaluationCase` 列表组装多策略对比命令，并将对比结果中的每个单策略 `RetrievalEvaluationReport` 继续写入运行历史。
- `SeahorseRetrievalEvaluationDatasetController` 新增 `POST /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/compare`，管理端无需重复上传完整评测样本即可做策略对比。
- 验证通过：`mvn -pl seahorse-agent-tests,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-starter -am "-Dtest=KernelRetrievalEvaluationDatasetServiceTests,JdbcRetrievalEvaluationDatasetRepositoryAdapterTests,SeahorseRetrievalEvaluationDatasetControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，reactor `BUILD SUCCESS`。

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

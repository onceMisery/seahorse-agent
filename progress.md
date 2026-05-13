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

- 扩展 `KeywordIndexPort`，新增 `rebuildDocument` 与 `rebuildKnowledgeBase` 默认方法，作为历史回填、索引失败补偿和后续管理任务的统一入口。
- `JdbcKeywordIndexAdapter` 实现按文档/知识库重算 `search_text`，继续保留列不存在时安全跳过的兼容策略。
- 扩展 `JdbcKeywordIndexAdapterTests`，覆盖文档/知识库重建 SQL 和老库跳过行为；运行 `mvn -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcKeywordIndexAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过：4 个测试成功。
- 继续推进 M3 关键词检索索引维护：新增 `JdbcKeywordIndexAdapter`，实现 `KeywordIndexPort`，在 JDBC/PostgreSQL fallback 下同步维护 `t_knowledge_chunk.search_text`。
- `JdbcKeywordIndexAdapter` 会先探测 `search_text` 列是否存在；老库未迁移或测试替身不支持 `information_schema` 时安全跳过，避免关键词索引维护影响主入库链路。
- starter 在 JDBC repository 类型且存在 `DataSource` 时默认注册 `JdbcKeywordIndexAdapter`，同时保留 `@ConditionalOnMissingBean(KeywordIndexPort.class)` 方便生产环境替换 Elasticsearch 实现。
- 运行 `mvn -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcKeywordIndexAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`，通过：3 个测试成功。

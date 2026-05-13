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

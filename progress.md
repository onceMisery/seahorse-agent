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

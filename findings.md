# Seahorse Agent 元数据治理与混合检索实现发现

## 代码现状

- 入库流水线通过 `IngestionNodeFeature.nodeType()` 动态查找节点，节点失败会中断，节点可以主动终止。
- `IngestionContext` 当前只有通用 `metadata`，没有元数据治理过程态字段。
- `ParserNodeFeature` 已把 `DocumentParseResult.metadata()` 写入 `metadata.parseMetadata`。
- Tika adapter 当前只返回文本，没有返回 Tika 元数据。
- `SearchContext` 当前没有强类型过滤条件，`RetrievedChunk` 只包含 `id/text/score`。
- Milvus/PGVector 写入时保存 chunk metadata，但检索时不返回 metadata，也未消费 `VectorSearchRequest.filters`。
- 向量通道当前传空 vector，导致 Milvus/PGVector 返回空结果。

## 实施决策

- 先实现 M1：Schema、抽取、标准化、校验、节点注册、JDBC 仓储基础。
- Review/Quarantine 首轮以端口和 repository 形式落地，不做前端页面。
- M2+ 后续在 M1 编译和测试通过后继续推进。
- `SearchResultPostProcessorFeature` 原先没有顺序方法，M2 后处理链需要新增默认 `order()` 并在 `KernelMultiChannelRetrievalEngine` 中排序。
- 向量通道已有 `EmbeddingModelPort` 可直接复用，不需要新增 QueryEmbeddingPort；未配置模型时 noop 仍返回空向量，保持降级兼容。
- PGVector/Milvus 均已在写入 metadata 时保留 `collection_name/doc_id/chunk_index`，适配器检索返回时可以从 metadata 反填 `RetrievedChunk` 的结构化字段。
- PowerShell 下 Maven `-Dtest=A,B` 必须整体加引号，否则逗号会触发参数解析错误。
- 多通道检索入口原先只能由意图和 topK 构造 `SearchContext`，没有过滤编译入口；已通过重载方法接入 `RetrievalFilter/RetrievalOptions`。
- 关键词检索通道应默认关闭，只有上层显式设置 `RetrievalOptions.enableKeyword=true` 时才执行，避免未配置关键词适配器时改变现有召回行为。
- JDBC 关键词 fallback 先采用 content `LIKE` 兼容实现；DDL 已预留 `search_text TSVECTOR`，后续可升级为 PostgreSQL FTS 排序。
- RRF/FinalTruncate 后处理器虽然已注册，但通过 `context.getOptions() != null` 控制启用，旧入口不传 options 时保持原有合并行为。
- Rerank 后处理器应同样保持显式启用语义：旧入口不传 options 时不启用；即使存在 `RerankModelPort`，也需要 `enableRerank=true` 且 `rerankModel` 非空才会调用模型。
- Rerank 模型返回结果必须归一化到已检索候选集，避免模型端口返回候选集外内容进入最终上下文。
- JDBC 关键词 fallback 已从 `content LIKE` 升级到 PostgreSQL FTS：通过 `websearch_to_tsquery('simple', ?)` 构造查询、`@@` 过滤命中、`ts_rank_cd` 作为关键词分；有 `search_text` 列时优先使用预计算向量，兼容历史空值时退回 `content` 动态向量。
- 入库 `IndexerNodeFeature` 现在同步调用 `KeywordIndexPort`，为 Elasticsearch 生产适配器和后续 Outbox 异步化预留统一写入点；默认 noop 保持原有向量入库行为兼容。
# 2026-05-13 追加发现

- `JdbcKeywordSearchAdapter` 已优先读取 `search_text`，因此 JDBC 关键词索引维护只需要更新同表 `tsvector`，无需新增 kernel 端口或跨模块依赖。
- `KeywordIndexPort` 当前由 `IndexerNodeFeature` 同步调用；JDBC fallback 保持轻量实现，生产级 ES 写入和 Outbox 异步化仍应作为后续适配器能力推进。
- 老库兼容性需要保留：`search_text` 列不存在时索引维护应跳过，避免还未执行 DDL 的部署在文档入库阶段失败。

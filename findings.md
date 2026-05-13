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


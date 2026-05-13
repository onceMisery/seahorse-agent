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

- 文档管理的启用/禁用/删除是索引一致性的关键入口；仅维护向量索引会导致关键词检索后端残留已禁用或已删除文档。
- 重新启用文档时，向量索引和关键词索引可以共用 `listEnabledChunks` 生成的分片快照，避免两套索引看到不同的分片集合。
- 关键词索引 Outbox 化放在 starter/spring adapter 层更合适：kernel 仍只依赖 `KeywordIndexPort`，消息可靠性、订阅生命周期和具体 delegate 选择留在外层装配。
- 异步事件需要携带 `VectorChunk` 快照；JDBC fallback 只用 `chunkId`，但 Elasticsearch adapter 后续会需要正文和 metadata。
- 关键词索引重建不应绑定具体后端实现；放在 `KeywordIndexPort` 默认方法上可以让 ES、PostgreSQL FTS 和后续 OpenSearch/Lucene adapter 共享同一补偿入口。
- JDBC fallback 的重建可以直接在 `t_knowledge_chunk` 表内重算 `search_text`，不需要额外拉取 chunk 内容，适合小规模部署和本地开发环境。
- `JdbcKeywordSearchAdapter` 已优先读取 `search_text`，因此 JDBC 关键词索引维护只需要更新同表 `tsvector`，无需新增 kernel 端口或跨模块依赖。
- `KeywordIndexPort` 当前由 `IndexerNodeFeature` 同步调用；JDBC fallback 保持轻量实现，生产级 ES 写入和 Outbox 异步化仍应作为后续适配器能力推进。
- 老库兼容性需要保留：`search_text` 列不存在时索引维护应跳过，避免还未执行 DDL 的部署在文档入库阶段失败。

## 2026-05-13 Elasticsearch adapter 发现

- 使用 OkHttp + Elasticsearch REST API 可以满足当前关键词检索/索引契约，不需要引入 Elasticsearch Java SDK，也不会污染 kernel 依赖边界。
- 搜索 adapter 必须只消费 `CompiledMetadataFilter` 的 AST。当前实现把 `FieldEq/FieldIn/FieldRange/FieldContains/FieldExists/FilterAnd` 翻译为 ES `term/terms/range/match/exists/bool filter`。
- 系统过滤与动态 metadata 过滤要分层处理：`enabled/tenant_id/kb_id/doc_id/collection_name` 等系统边界由 adapter 固定字段下推，动态字段使用 `MetadataFieldDescriptor.backendMapping().searchFieldName()`。
- `KeywordIndexPort.rebuildDocument/rebuildKnowledgeBase` 只有 kb/doc 参数，ES adapter 无法凭空重建正文索引；后续重建任务应由管理端或应用服务从 chunk repository 拉取分片快照，再调用 `indexDocumentChunks`。
- Spring `ApplicationContextRunner` 不一定提供 Boot Binder 的 `Duration` 转换能力；自动装配中简单配置值更稳妥的做法是接收字符串并在配置类本地解析。

## 2026-05-13 关键词索引重建编排发现

- 生产级 ES/OpenSearch 后端不能根据 `kbId/docId` 自行恢复正文与 metadata，重建的数据来源必须留在 kernel 编排层，从文档仓储拉取已经治理过的启用分片快照。
- 重建前先调用 `deleteDocumentChunks` 可以清理历史残留，避免被禁用、删除或分片变化后的旧 chunk 继续参与关键词召回。
- `KeywordIndexMaintenanceInboundPort` 只暴露编排能力，不绑定 web、调度或具体后端；管理端/API/Job 后续只需要调用该入站端口即可。
- 重建结果需要保留跳过与失败摘要。对于知识库级批量重建，单文档失败不应中断整个批次，后续补偿可以根据失败列表继续处理。

## 2026-05-13 Web 管理触发入口发现

- 关键词索引重建 HTTP 接口应复用现有知识库 URL 前缀，便于前端管理页按文档详情或知识库批量操作接入。
- Web adapter 只负责触发与响应包装，不应在控制器里重新拼装分片或解析具体搜索后端，避免绕过 kernel 重建编排。

## 2026-05-13 索引失败观测与补偿发现

- Outbox relay 已具备失败状态、重试次数和下一次重试时间；关键词索引消费端应在失败时继续抛出异常，避免观测代码吞掉异常导致 MQ/Outbox 误判为成功。
- `ObservationPort` 的属性会落到 Micrometer tag，关键词索引观测只记录 operation、scope、status、exception 这类低基数字段，避免把 docId/kbId 放进指标标签。
- Web 重建 API 与 outbox retry 形成两类补偿路径：自动重试处理短暂后端波动，管理端按文档/知识库重建处理历史回填和人工补偿。

## 2026-05-13 计划型 Job 触发入口发现

- 关键词索引定时重建必须默认关闭，否则生产环境可能在未明确配置目标时触发大范围重复写入。
- 定时补偿入口适合配置少量明确 docId/kbId 目标；常规全量历史回填仍应优先通过管理端按需触发并观察结果。
- Job 层只负责调度、锁和错误隔离，不持有重建细节，避免绕过 `KeywordIndexMaintenanceInboundPort` 的数据治理边界。

## 2026-05-13 M5 元数据回填发现

- 回填服务如果只调用 `KnowledgeDocumentInboundPort.executeChunk`，无法直接知道文档是否进入 Review 或 Quarantine；复用 `KernelIngestionEngine` 并检查 `IngestionContext.metadataValidationResult` 更适合作为 kernel 级治理回填编排。
- 回填任务的 checkpoint 至少需要页游标和最后处理文档 ID。当前实现按批次页游标续跑，并在每个文档处理后刷新 checkpoint，后续如果要支持更细粒度断点，可在仓储查询中增加“从 lastDocumentId 之后继续”的能力。
- 单文档失败不能把整个任务置为 FAILED；FAILED 更适合作为任务级不可恢复异常。普通文档处理失败保存在 `failure_summary`，批次继续推进，最终由 failed_count 暴露补偿范围。
- Review/Quarantine 的计数应该来自治理节点的 `MetadataValidationDecision`，不能通过解析适配器异常或索引结果推断。

## 2026-05-13 M5 管理 API 发现

- 元数据回填管理 API 应保持“触发/查询”职责，不在 Web 层重建治理上下文；创建、运行、暂停、恢复、取消都只转发给 `MetadataBackfillInboundPort`。
- `X-User-Id` 只作为 operator 传入 kernel；缺省时使用稳定的系统操作者，避免 API 调用方未传 header 时产生空操作人。
- 批次推进接口返回 `MetadataBackfillRunResult` 比只返回任务记录更适合管理端展示本次处理数、失败数和 Review/Quarantine 变化。

## 2026-05-13 M5 质量报表发现

- 字段覆盖率应以 Metadata Schema 字段为基准，而不是遍历任意动态 metadata key；这样能避免非治理字段污染报表口径。
- 低置信度比例需要依赖 normalizer 产生的字段级质量数据，不能只看最终 `accepted_metadata`，否则无法区分字段是高置信自动通过还是低置信进入复核。
- 报表查询应使用每个文档最新一条抽取结果作为统计样本，避免历史回填多次运行导致同一文档被重复计入覆盖率。
- 待复核数量和隔离原因 TopN 应分别来自 `t_metadata_review_item` 与 `t_metadata_quarantine_item`，只统计待处理状态，避免已处理历史问题继续影响运维看板。

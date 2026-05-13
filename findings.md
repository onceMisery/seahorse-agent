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

## 2026-05-13 M6 检索后处理增强发现

- RRF 权重适合先复用 `RetrievalOptions.channelSettings`，避免新增全局配置类；后续若要按知识库模板化，可再把该设置上移到策略模板。
- Rerank 超时只能保证检索链路按时降级，不能强制所有底层 HTTP/SDK 调用立即停止；生产适配器仍应设置自身请求超时。
- 检索观测事件不要包含 chunkId/docId/question 等高基数字段，RRF/Rerank 当前只记录 status、候选数、输出数、耗时和异常类型。

## 2026-05-13 M5 Review/Quarantine 管理 API 发现

- Review API 不能只改 `review_status`；通过和修正还需要把可信结果写回文档 canonical metadata，否则后续过滤、索引重建和质量报表会看到旧值。
- 转隔离动作应保留 review 快照并写入 Quarantine，而不是把修正值写回文档；这是防止低质量元数据污染检索后端的最后一道治理边界。
- Quarantine 的“重试”本轮先落为调度字段更新，不直接在 Web 层重跑入库；具体重放仍应由回填/调度编排读取 `next_retry_time` 后执行。
- `t_metadata_review_item.result_id` 当前写入链路仍可能保存 taskId 而非抽取结果 id；管理仓储会在能匹配到抽取结果时同步 `approved_metadata`，文档 canonical metadata 写回由 kernel 服务保证。

## 2026-05-13 M5 Metadata Schema 管理 API 发现

- Schema 管理 API 是动态 metadata 进入过滤编译与索引映射前的注册入口；字段必须先落到 `t_metadata_field_schema`，后续 `MetadataSchemaRegistryPort.loadSchema()` 才能让 Filter Compiler 和后端 adapter 消费。
- 本轮先实现字段 CRUD 与自动装配，不做索引模板自动生成；Elasticsearch/OpenSearch/Milvus 等后端的物理字段模板仍应放在 adapter 或运维迁移层处理，避免 kernel 依赖外部 SDK。
- 更新字段采用完整载荷覆盖语义，避免局部更新时在 Web 层重新拼装旧值；管理端调用 PUT 时需要携带 `tenantId`、`fieldKey` 等 Schema 必填信息。

## 2026-05-13 P2 LLM 元数据抽取增强发现

- LLM 抽取不应成为新的可信写入口；它只能补充 `MetadataFieldCandidate`，最终是否进入 canonical metadata 仍由 Normalizer 和 Validator 决定。
- Schema 白名单不足以保护系统字段，因为用户可能误把权限字段注册为业务字段；LLM 路径需要硬拒绝 `tenantId/kbId/docId/aclSubjects/securityLevel` 等系统和权限字段。
- LLM 抽取默认必须关闭，避免未配置模型或提示词不稳定时改变现有确定性抽取链路；启用应由节点配置显式控制。

## 2026-05-13 M5 Review 审计闭环发现

- 复核项上的 `review_status/reviewer_id/review_comment` 只能表达当前态，无法满足“人工修正保留审计记录”的验收要求；需要独立审计表保存每次决策。
- 审计写入不能替代 canonical metadata 写回；APPROVED/CORRECTED 仍需同步抽取结果和文档 metadata，审计表只承担追溯职责。
- 旧库兼容需要谨慎处理：审计表缺失时不应阻断人工复核主流程，但新 DDL 必须包含完整 COMMENT，方便生产迁移后开启审计查询。

## 2026-05-13 P3 Schema 驱动 Elasticsearch Mapping 发现

- Schema 管理服务是动态字段进入检索后端的自然收口点；在这里触发索引结构同步，可以避免 Web/API 或具体 adapter 绕过 Schema 边界。
- Elasticsearch mapping 同步需要显式开启，否则企业环境在尚未配置 ES 或迁移窗口中调用 Schema API 可能被外部搜索集群阻断。
- 默认 `BackendFieldMapping.searchFieldName=fieldKey` 与当前 ES 文档结构不完全一致；ES adapter 需要把这种默认值解释为 `metadata.<fieldKey>`，显式配置的 `metadata.xxx.keyword` 则按配置透传。
- `metadata.xxx.keyword` 查询路径要求 mapping 建立 `text + keyword sub-field`，而不是把 `keyword` 当作普通对象层级；否则 term 查询会找不到子字段。

## 2026-05-13 M5 回填幂等收口发现

- 回填幂等必须绑定 `schemaVersion` 与 `extractorVersion`，只按文档维度跳过会掩盖 Schema 升级或抽取器升级后的必要重跑。
- 幂等跳过只能针对已 ACCEPT 的可信抽取结果；REVIEW、QUARANTINE、REJECTED 或失败结果仍需要后续治理或补偿流程继续处理。
- `forceRerun/force` 适合作为 checkpoint 内的任务级开关，方便管理端临时覆盖幂等检查，同时不会改变仓储查询契约。
- JDBC 幂等查询需要兼容历史状态值 `ACCEPT` 与可能的 `ACCEPTED`，并在旧库或迁移窗口异常时降级为不跳过，避免回填任务被幂等检查阻断。

## 2026-05-13 M5 回填任务列表查询发现

- 回填任务是治理运维对象，只有单任务详情不足以支撑管理端查看历史批次、失败范围和暂停中的任务；需要知识库维度分页列表。
- 列表查询应继续复用 `MetadataBackfillJobRecord` 快照，不在 Web 层重新拼装进度字段，避免控制器绕过 kernel 口径。
- 仓储分页筛选先限制在租户、知识库和状态三类低复杂度条件；更复杂的时间范围、操作者和失败关键字过滤后续可在不改变核心回填流程的情况下扩展。

## 2026-05-13 M5 质量报表复核通过率发现

- 复核通过率应只统计已处理的人工复核样本，待处理 `PENDING` 表示队列积压，不应进入质量通过率分母。
- `APPROVED` 和 `CORRECTED` 都代表人工复核后可接受；`REJECTED` 和 `QUARANTINED` 代表未通过治理门禁，可作为复核质量的未通过样本。
- 将复核通过率放在质量报表模型中比单独新增接口更合适，管理端可以一次拿到覆盖率、低置信度、复核通过率和隔离原因 TopN。

## 2026-05-13 M6 FinalTruncate 观测收口发现

- FinalTruncate 是检索后处理链路的最终收口点，单独记录 `retrieval.final` 能让运维侧区分“融合/精排输出规模”和“最终进入上下文规模”。
- 最终截断观测仍需遵守低基数原则，只记录规模、阈值和是否截断；`chunkId/docId/question` 等明细应留在日志或追踪上下文，不进入指标标签。
- 租户上下文可能为空，观测属性需要稳定降级为空字符串，避免 `Map.of` 因空值失败后丢失事件。

## 2026-05-13 P5 检索质量评测发现

- P5 最小闭环不需要先落持久化评测集；内核先提供“临时评测集运行 + 指标计算”端口，可以让管理端或离线任务复用同一指标口径。
- Recall@K、MRR 和 nDCG@K 应基于 expectedChunkIds/expectedDocIds/expectedKbIds 三类目标匹配，允许评测集按不同粒度标注答案来源。
- Web 评测接口不能把原始 metadata Map 直接送入检索后端；请求层只构造 `RetrievalFilter`/`MetadataCondition`，后续仍由内核 Filter Compiler 做 Schema 校验。
- 评测服务只依赖 `KernelRetrievalEngine`，不引入外部评测框架或搜索 SDK，后续持久化评测集、策略 A/B 和知识库策略模板可在该端口之上扩展。

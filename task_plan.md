# Seahorse Agent 元数据治理与混合检索实现计划

## 目标

根据 `handoff-frnn1m.md` 与两份架构设计文档，优先落地 M1 元数据治理最小闭环，并继续推进 M2 动态过滤与向量闭环。

## 阶段

- [complete] M1 元数据治理最小闭环
- [complete] M2 向量检索过滤闭环
- [in_progress] M3 关键词检索
- [complete] M4 RRF 与 Reranker
- [pending] M5 回填与治理运维

## 当前落地范围

- M1 已完成：元数据治理领域模型、入库节点、Tika parser metadata、JDBC 治理仓储、chunk metadata 写入、starter 自动装配和基础测试。
- M2 已完成：`RetrievalFilter`、`RetrievalOptions`、Filter AST、`MetadataFilterCompiler`、`MetadataGuardPostProcessorFeature`、query embedding、向量适配器 metadata 返回和基础过滤下推，并接入多通道检索入口与 starter 自动装配。
- M3 已完成第二段：新增 `KeywordSearchPort`、`KeywordIndexPort`、`KeywordSearchRequest` 和 `KeywordSearchChannelFeature`，starter 在存在 `KeywordSearchPort` 时注册关键词通道，并提供 JDBC/PostgreSQL FTS 轻量关键词 fallback；入库 indexer 已同步调用 `KeywordIndexPort`，默认 noop。
- M3 尚未完成：Elasticsearch 生产适配器、关键词索引 Outbox 异步化、索引重建任务。
- M4 已完成最小闭环：新增 `RrfFusionPostProcessorFeature`、`RerankPostProcessorFeature` 和 `FinalTruncatePostProcessorFeature`，支持通道排名融合、重复 chunk 去重、融合分记录、Rerank 候选截断、异常/空结果降级、`rerankScore` 回写和 finalTopK 截断。
- M4 后续增强：Rerank 超时隔离、通道权重配置化和观测指标。

## 硬约束

- 全程中文沟通，中文文件使用 UTF-8 without BOM。
- 不恢复 `docs/zh/content/架构设计/混合检索.txt`。
- 动态 metadata 进入检索前必须经过 Schema 与 Filter Compiler。
- kernel 只放领域模型、端口、Feature 与编排；外部实现放 adapter。
- 新增 DDL 表和字段必须有注释。
# 2026-05-13 本轮续做

- [complete] M3 文档管理索引联动：文档禁用/删除会清理关键词索引，重新启用会把启用分片同步回关键词索引。
- [complete] M3 Outbox 异步索引最小闭环：新增关键词索引 Outbox 发布器、消息事件和订阅器，支持通过配置切换到 outbox 模式。
- [complete] M3 关键词索引维护入口：`KeywordIndexPort` 增加按文档/知识库重建的默认方法，JDBC fallback 已实现重算 `search_text`。
- [complete] M3 JDBC/PostgreSQL `KeywordIndexPort` fallback：维护 `t_knowledge_chunk.search_text`，并在 starter 中注册默认 JDBC 索引适配器。
- [pending] M3 生产级关键词索引：Elasticsearch adapter、管理端重建任务编排、异步索引失败观测与补偿策略仍待后续落地。

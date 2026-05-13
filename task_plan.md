# Seahorse Agent 元数据治理与混合检索实现计划

## 目标

根据 `handoff-frnn1m.md` 与两份架构设计文档，优先落地 M1 元数据治理最小闭环，并继续推进 M2 动态过滤与向量闭环。

## 阶段

- [complete] M1 元数据治理最小闭环
- [complete] M2 向量检索过滤闭环
- [in_progress] M3 关键词检索
- [in_progress] M4 RRF 与 Reranker
- [pending] M5 回填与治理运维

## 当前落地范围

- M1 已完成：元数据治理领域模型、入库节点、Tika parser metadata、JDBC 治理仓储、chunk metadata 写入、starter 自动装配和基础测试。
- M2 已完成：`RetrievalFilter`、`RetrievalOptions`、Filter AST、`MetadataFilterCompiler`、`MetadataGuardPostProcessorFeature`、query embedding、向量适配器 metadata 返回和基础过滤下推，并接入多通道检索入口与 starter 自动装配。
- M3 已完成第一段：新增 `KeywordSearchPort`、`KeywordIndexPort`、`KeywordSearchRequest` 和 `KeywordSearchChannelFeature`，starter 在存在 `KeywordSearchPort` 时注册关键词通道，并提供 JDBC 轻量关键词 fallback。
- M3 尚未完成：Elasticsearch 生产适配器、真正 PostgreSQL FTS 排序表达式、关键词索引 Outbox 同步、索引重建任务。
- M4 已完成第一段：新增 `RrfFusionPostProcessorFeature` 和 `FinalTruncatePostProcessorFeature`，支持通道排名融合、重复 chunk 去重、融合分记录和 finalTopK 截断。
- M4 尚未完成：`RerankPostProcessorFeature`、Rerank 输入截断/超时降级、通道权重配置化和观测指标。

## 硬约束

- 全程中文沟通，中文文件使用 UTF-8 without BOM。
- 不恢复 `docs/zh/content/架构设计/混合检索.txt`。
- 动态 metadata 进入检索前必须经过 Schema 与 Filter Compiler。
- kernel 只放领域模型、端口、Feature 与编排；外部实现放 adapter。
- 新增 DDL 表和字段必须有注释。

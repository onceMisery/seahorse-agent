# Seahorse Agent 元数据治理与混合检索实现计划

## 目标

根据 `handoff-frnn1m.md` 与两份架构设计文档，优先落地 M1 元数据治理最小闭环，并继续推进 M2 动态过滤与向量闭环。

## 阶段

- [complete] M1 元数据治理最小闭环
- [in_progress] M2 向量检索过滤闭环
- [pending] M3 关键词检索
- [pending] M4 RRF 与 Reranker
- [pending] M5 回填与治理运维

## 当前落地范围

- M1 已完成：元数据治理领域模型、入库节点、Tika parser metadata、JDBC 治理仓储、chunk metadata 写入、starter 自动装配和基础测试。
- M2 已完成第一段：`RetrievalFilter`、`RetrievalOptions`、Filter AST、`MetadataFilterCompiler`、`MetadataGuardPostProcessorFeature`、query embedding、向量适配器 metadata 返回和基础过滤下推。
- M2 尚未完成：完整后端能力声明、复杂 ACL/时间范围语义、关键词检索、RRF、Rerank、检索配置属性和更多适配器级集成测试。

## 硬约束

- 全程中文沟通，中文文件使用 UTF-8 without BOM。
- 不恢复 `docs/zh/content/架构设计/混合检索.txt`。
- 动态 metadata 进入检索前必须经过 Schema 与 Filter Compiler。
- kernel 只放领域模型、端口、Feature 与编排；外部实现放 adapter。
- 新增 DDL 表和字段必须有注释。

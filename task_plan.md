# Seahorse Agent 元数据治理与混合检索实现计划

## 目标

根据 `handoff-frnn1m.md` 与两份架构设计文档，优先落地 M1 元数据治理最小闭环，并为 M2 动态过滤与向量闭环预留兼容接口。

## 阶段

- [in_progress] M1 元数据治理最小闭环
- [pending] M2 向量检索过滤闭环
- [pending] M3 关键词检索
- [pending] M4 RRF 与 Reranker
- [pending] M5 回填与治理运维

## 硬约束

- 全程中文沟通，中文文件使用 UTF-8 without BOM。
- 不恢复 `docs/zh/content/架构设计/混合检索.txt`。
- 动态 metadata 进入检索前必须经过 Schema 与 Filter Compiler。
- kernel 只放领域模型、端口、Feature 与编排；外部实现放 adapter。
- 新增 DDL 表和字段必须有注释。


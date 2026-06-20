# Seahorse Agent 文档导航

本文档是 `docs/` 目录的当前入口。根目录 `README.md` 负责项目总览；这里负责说明哪些文档适合直接照着操作，哪些文档属于历史架构参考。

## 当前事实来源

遇到文档互相冲突时，按下面优先级判断：

1. `README.md`
2. `docker-compose.yml`、`docker-compose.full.yml`
3. `.env.example`、`.env.full.example`
4. `resources/database/seahorse_init.sql` 与 `resources/database/migrations/`
5. 后端 Controller、端口和前端路由源码
6. `docs/` 下的专题文档

`docs/zh/content/` 中有大量早期 RepoWiki 风格文档，适合理解模块背景和设计意图，不应直接作为本地部署命令、默认账号、端口和接口路径的唯一依据。

## 推荐阅读顺序

| 场景 | 文档 |
|---|---|
| 先跑起来 | `README.md`、`docs/PRE_EXECUTION_CHECKLIST.md` |
| 用前端和 API 验证功能 | `docs/USER_GUIDE.md` |
| 排查登录、RAG、记忆、Docker 问题 | `docs/TROUBLESHOOTING_GUIDE.md` |
| 理解当前代码架构 | `docs/architecture/current-code-architecture.md` |
| 看近期/中期/远期路线图和愿景 | `docs/roadmap/architecture-roadmap-and-vision.md` |
| 配本地 Embedding / Ollama | `docs/deployment/local-embedding-model-guide.md`、`docs/deployment/ollama-quick-start.md` |
| 全量企业模式 | `docs/deployment/enterprise-mode.md` |
| 入库 Pipeline 示例 | `docs/examples/pdf-ingestion-example.md` |
| 中文概览 | `docs/zh/content/项目概述.md`、`docs/zh/content/快速开始.md` |
| API 总览 | `docs/zh/content/API 接口文档/API 接口文档.md` |

## 部署模式边界

| 模式 | 编排文件 | 适用范围 | 重要差异 |
|---|---|---|---|
| 轻量部署 | `docker-compose.yml` | 页面开发、登录和基础 API 冒烟 | 向量适配器默认 `noop`，缓存为本地，消息为 direct，不代表完整 RAG 质量 |
| 全量部署 | `docker-compose.full.yml` | 真实 RAG、记忆、索引、观测和企业功能验证 | 使用 Milvus、Ollama、Redis、Elasticsearch、Pulsar、MinIO、Prometheus、Grafana |

当前默认管理员账号来自 `resources/database/seahorse_init.sql`：

```text
admin / admin123
```

前端默认入口：

```text
http://localhost
```

后端直连入口：

```text
http://localhost:9090
```

## 真实闭环验证边界

完整 RAG 闭环需要全量部署，并至少确认：

- Chat 模型环境变量有效。
- Ollama 已拉取 `nomic-embed-text`。
- Milvus 健康，向量维度为 768。
- Elasticsearch 健康，关键词索引适配器启用。
- 知识库文档完成分块与索引。
- `/rag/traces/runs` 或 `t_rag_trace_*` 能看到 retrieval 节点（前端页面路径为 `/admin/traces`）。

完整记忆与用户画像闭环需要确认：

- `SEAHORSE_AGENT_MEMORY_AGGREGATION_ENABLED=true`。
- `t_short_term_memory` 写入对话轮次。
- `t_memory_aggregation_buffer`、`t_memory_outbox` 有聚合和派生任务记录。
- `t_user_profile_fact` 生成 active 画像事实。
- 后续新会话能召回画像和长期记忆。

## 文档维护规则

- 新的可操作命令优先写入 `docs/USER_GUIDE.md`、`docs/PRE_EXECUTION_CHECKLIST.md` 或部署专题文档。
- 端口、账号、环境变量、接口路径变更后，要同步 `README.md` 和本目录入口文档。
- 架构事实变更后，同步 `docs/architecture/current-code-architecture.md`；规划口径变更后，同步 `docs/roadmap/architecture-roadmap-and-vision.md`。
- 历史设计文档可以保留背景，但顶部应写明当前运行态和已知差异。

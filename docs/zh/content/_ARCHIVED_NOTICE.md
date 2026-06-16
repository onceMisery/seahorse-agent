# 历史文档归档声明

> **本目录下的所有文档均来自早期 RepoWiki 自动生成，属于历史参考文档。**
>
> **事实源优先级**（以 `docs/architecture/current-code-architecture.md` Section 8 为准）：
> 1. Controller 注解、配置类、端口和 adapter 代码
> 2. `docker-compose.yml`、`docker-compose.full.yml`
> 3. `.env.example`、`.env.full.example` 中的可覆盖变量
> 4. `resources/database/seahorse_init.sql` 与 migrations
> 5. `docs/README.md`、`docs/architecture/current-code-architecture.md`、`docs/USER_GUIDE.md`
>
> **本目录不应作为部署、配置或 API 调用的依据。** 若内容与上述事实源冲突，以事实源为准。
>
> 归档日期：2026-06-16

## 常见过期引用

本目录中可能包含以下已失效的引用，请勿使用：

- `file://resources/docker/*.compose.yaml` — 独立 compose 文件已不是部署入口
- `file://docs/quick-start` — 已被 `docs/USER_GUIDE.md` 取代
- `qwen-plus`、`qwen-emb-8b` — 当前全量默认模型为 `nomic-embed-text`
- `/admin/traces` — 当前 RAG Trace 端点为 `/rag/traces/runs`
- `MILVUS_DIMENSION=1024` — 已弃用，向量维度由 embedding 模型解析

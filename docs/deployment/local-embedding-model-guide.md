# 本地 Embedding 模型部署指南

本文说明 Seahorse Agent 当前如何使用本地 Embedding，以及切换模型时需要同步修改哪些配置。

## 当前运行态

全量部署默认不是 mock，也不是 1024 维。当前事实如下：

| 项 | 当前值 |
|---|---|
| Embedding 类型 | OpenAI-compatible |
| 默认模型 | `nomic-embed-text` |
| 服务 | 容器内 Ollama |
| Base URL | `http://ollama:11434/v1` |
| 向量维度 | 768 |
| 向量库 | Milvus |
| 关键词检索 | Elasticsearch |

轻量部署默认使用 `noop` 向量适配器，仅适合页面和基础 API 冒烟。

## 推荐方案

### 方案 A：使用全量部署内置 Ollama

适合本地开发和闭环验证。

```bash
docker compose -f docker-compose.full.yml up -d --build
docker exec seahorse-ollama ollama list
```

验证维度：

```bash
curl -s http://localhost:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"nomic-embed-text","prompt":"test"}' \
  | jq '.embedding | length'
```

预期为 `768`。

### 方案 B：使用外部 OpenAI 兼容 Embedding 服务

适合生产或希望统一模型网关的环境。

需要修改：

```env
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_TYPE=openai-compatible
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_BASE_URL=https://your-embedding-endpoint/v1
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_API_KEY=your-key
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=your-embedding-model
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL_DIMENSIONS=your-embedding-model=<模型输出维度>
```

已知模型会自动解析维度；自定义模型用 `SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL_DIMENSIONS` 声明。`SEAHORSE_AGENT_ADAPTERS_VECTOR_DIMENSION` 仍可作为显式覆盖，但不建议作为默认事实源。切换模型后需要重建 Milvus collection、知识库配置和已有向量数据。

### 方案 C：使用宿主机 Ollama

适合已经在宿主机管理 Ollama 的情况。容器内后端访问宿主机可用：

```env
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_BASE_URL=http://host.docker.internal:11434/v1
```

同时确认 compose 网络和 Docker Desktop 支持 `host.docker.internal`。

## 模型选择

| 模型 | 维度 | 特点 |
|---|---:|---|
| `nomic-embed-text` | 768 | 当前默认，小、快，适合本地验证 |
| `bge-base-zh` | 768 | 中文效果更稳，资源需求适中 |
| `bge-m3` | 1024 | 多语言，质量较好，资源需求更高 |
| `text-embedding-3-small` | 1536 | 外部 API，稳定，按量计费 |

模型一旦切换，旧向量索引通常不能复用。

## 切换模型步骤

1. 拉取或准备模型。
2. 修改 Embedding 模型名、Base URL、API Key。
3. 如果是自定义模型，配置 `SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL_DIMENSIONS=模型名=维度`。
4. 清理或重建 Milvus collection。
5. 重新处理知识库文档。
6. 用 `/rag/traces/runs` 或 `t_rag_trace_*` 验证 retrieval 节点。

示例：

```bash
docker exec seahorse-ollama ollama pull bge-m3
```

```env
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=bge-m3
```

## 排错

### 维度不匹配

症状：

```text
dimension mismatch
```

处理：

- 确认模型输出维度。
- 确认后端向量维度配置。
- 清理旧 collection 或重新建库。
- 重新向量化文档。

### 模型下载慢

```bash
docker logs -f seahorse-ollama-init
docker exec seahorse-ollama ollama pull nomic-embed-text
```

如需代理，配置 Docker 代理或容器内代理。

### RAG 仍无上下文

Embedding 正常不代表 RAG 完整闭环已经完成。还要检查：

- 文档是否有 `t_knowledge_chunk`。
- Milvus 是否有对应 collection。
- Elasticsearch 是否健康。
- RAG Trace 中 retrieval 节点是否成功。

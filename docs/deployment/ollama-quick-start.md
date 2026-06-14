# Ollama 本地 Embedding 快速开始

全量部署默认使用容器内 Ollama 提供 Embedding，Chat 模型仍由 `SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL` 指向的 OpenAI 兼容服务提供。

## 当前默认配置

| 项 | 值 |
|---|---|
| 容器 | `seahorse-ollama` |
| 宿主机端口 | `11434` |
| 模型 | `nomic-embed-text` |
| 维度 | 768 |
| 后端 Embedding Base URL | `http://ollama:11434/v1` |
| 向量库 | Milvus |

`docker-compose.full.yml` 中的 `ollama-init` 会自动拉取 `nomic-embed-text`。

## 启动

```bash
docker compose -f docker-compose.full.yml up -d --build
```

首次启动需要等待模型下载：

```bash
docker logs -f seahorse-ollama-init
```

## 验证

查看模型：

```bash
docker exec seahorse-ollama ollama list
```

测试 Embedding：

```bash
curl -s http://localhost:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"nomic-embed-text","prompt":"测试文本"}' \
  | jq '.embedding | length'
```

预期输出：

```text
768
```

检查后端健康：

```bash
curl http://localhost:9090/actuator/health
```

## 切换模型

切换模型时必须同时处理三件事：

1. 拉取新模型。
2. 修改后端 Embedding 模型；已知模型维度会自动解析。
3. 清理或重建旧文档向量索引。

示例：切换到 `bge-m3`：

```bash
docker exec seahorse-ollama ollama pull bge-m3
```

然后同步修改 compose 或环境变量：

```env
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=bge-m3
```

自定义模型需要额外声明维度映射：

```env
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL_DIMENSIONS=your-embed-model=1024
```

重启后端：

```bash
docker compose -f docker-compose.full.yml restart backend
```

已有知识库需要重新向量化，否则会出现维度不匹配或召回异常。

## 常见问题

### 模型没有自动下载

```bash
docker logs seahorse-ollama-init
docker exec seahorse-ollama ollama pull nomic-embed-text
```

如果本机需要代理，先配置 Docker 或在容器内临时设置代理后再拉取。

### 请求超时

首次调用模型可能较慢。确认 Docker Desktop 内存充足，并重试：

```bash
time curl -s http://localhost:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"nomic-embed-text","prompt":"test"}' \
  | jq '.embedding | length'
```

### CPU-only 是否可用

可以。`nomic-embed-text` 体积较小，适合本地开发和小规模验证。大批量文档或高并发建议使用更强机器、独立 Ollama 服务或外部 Embedding 服务。

### 如何清理模型数据

```bash
docker exec seahorse-ollama ollama rm nomic-embed-text
```

删除全部全量部署数据卷前务必确认不需要旧数据：

```bash
docker compose -f docker-compose.full.yml down -v
```

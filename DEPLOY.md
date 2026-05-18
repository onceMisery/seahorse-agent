# Seahorse Agent 部署指南

## 部署模式对比

| | 最小化部署 | 全量部署 |
|---|---|---|
| **文件** | `docker-compose.yml` | `docker-compose.full.yml` |
| **向量库** | pgvector（复用 PostgreSQL） | Milvus 2.6.6 |
| **缓存** | local（进程内） | Redis 7 |
| **对象存储** | local（本地文件系统） | MinIO（S3 兼容） |
| **消息队列** | direct（进程内同步） | Apache Pulsar 3.1.3 |
| **关键词搜索** | Lucene（内嵌） | Elasticsearch 8 |
| **监控** | noop | Micrometer |
| **内存需求** | ≥ 4GB | ≥ 8GB |
| **适用场景** | 开发/测试 | 生产环境 |

## 环境要求

- Docker Desktop（含 Docker Compose v2）
- 至少 8GB RAM（Docker Desktop → Settings → Resources）
- 可用的 OpenAI 兼容 API

## 快速开始

### 最小化部署

```bash
cp .env.example .env
# 编辑 .env 填入 AI API 配置
docker compose up -d --build
```

### 全量部署

**Linux / macOS：**
```bash
chmod +x deploy.sh
./deploy.sh
```

**Windows：**
```powershell
.\deploy.ps1
```

**手动部署：**
```bash
cp .env.full.example .env
# 编辑 .env 填入 AI API 配置
docker compose -f docker-compose.full.yml up -d --build
```

## 服务端口一览

| 服务 | 端口 | 说明 |
|------|------|------|
| 前端 | 80 | React SPA |
| 后端 API | 9090 | Spring Boot |
| PostgreSQL | 5432 | 数据库 |
| Redis | 6379 | 缓存 |
| Elasticsearch | 9200 | 关键词搜索 |
| MinIO API | 9000 | S3 对象存储 |
| MinIO 控制台 | 9001 | MinIO Web UI |
| Milvus | 19530 | 向量数据库 |
| Milvus Attu | 8000 | Milvus Web UI |
| Pulsar Broker | 6650 | 消息队列 |
| Pulsar 管理台 | 8080 | Pulsar Web UI |
| ZooKeeper | 2181 | Pulsar 元数据 |

## 配置说明

### AI 模型（必填）

在 `.env` 中配置：

```env
SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL=https://api.openai.com/v1
SEAHORSE_AGENT_ADAPTERS_AI_API_KEY=sk-your-key
SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL=gpt-4o-mini
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=text-embedding-3-small
```

支持任何 OpenAI 兼容 API（OpenAI、DeepSeek、通义千问、Ollama 等）。

### 向量维度

根据使用的 Embedding 模型设置：

| 模型 | 维度 |
|------|------|
| text-embedding-3-small | 1536 |
| text-embedding-3-large | 3072 |
| text-embedding-ada-002 | 1536 |
| bge-m3 | 1024 |
| bge-large-zh | 1024 |

在 `.env` 中设置 `MILVUS_DIMENSION=1024`。

## 健康检查

```bash
# 查看所有服务状态
docker compose -f docker-compose.full.yml ps

# 查看后端日志
docker compose -f docker-compose.full.yml logs -f backend

# 测试后端 API
curl http://localhost:9090/api/user/me

# 测试 Elasticsearch
curl http://localhost:9200/_cluster/health

# 测试 Milvus
curl http://localhost:9091/healthz

# 测试 Pulsar
curl http://localhost:8080/admin/v2/brokers/healthcheck
```

## 管理命令

```bash
# 停止所有服务
docker compose -f docker-compose.full.yml down

# 停止并清理数据（危险！会删除所有数据）
docker compose -f docker-compose.full.yml down -v

# 重新构建后端
docker compose -f docker-compose.full.yml up -d --build backend

# 查看某个服务的日志
docker compose -f docker-compose.full.yml logs -f redis
```

## 常见问题

### 1. 内存不足

全量部署需要至少 8GB RAM。在 Docker Desktop → Settings → Resources 中调整。

### 2. Milvus 启动慢

Milvus 首次启动需要 60-90 秒。后端有健康检查重试机制，会自动等待。

### 3. Elasticsearch 启动慢

ES 8.x 首次启动需要 30-60 秒，已配置 `start_period: 30s`。

### 4. 端口冲突

如果端口被占用，修改 `.env` 中的端口映射或停止占用端口的服务。

### 5. Windows 行尾符问题

如果遇到 `mvnw` 执行错误，运行：
```bash
sed -i 's/\r$//' mvnw
```

### 6. 清理所有数据重来

```bash
docker compose -f docker-compose.full.yml down -v
docker compose -f docker-compose.full.yml up -d --build
```

## 架构图

```
┌──────────────────────────────────────────────────────────────────────┐
│                    Docker Network (seahorse-net)                     │
├──────────┬──────────┬──────────┬──────────┬──────────┬──────────────┤
│ Postgres │  Redis   │  Milvus  │  Pulsar  │  ES 8.x  │    MinIO     │
│ +pgvector│  7.x     │  2.6.6   │  3.1.3   │          │  (S3 兼容)   │
│  :5432   │  :6379   │  :19530  │  :6650   │  :9200   │  :9000       │
├──────────┴──────────┴──────────┴──────────┴──────────┴──────────────┤
│                       Backend (Java 17)             :9090           │
│                       Frontend (nginx + React)      :80             │
└──────────────────────────────────────────────────────────────────────┘
```

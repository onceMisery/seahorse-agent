# 大模型配置指南

## 概述

Seahorse Agent 支持配置多种 OpenAI 兼容的大模型服务，包括对话模型、向量化模型和重排序模型。

## 配置方式

### 1. 通过环境变量配置（推荐）

编辑项目根目录的 `.env` 文件：

```bash
# AI 模型配置
SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL=https://api.siliconflow.cn/v1
SEAHORSE_AGENT_ADAPTERS_AI_API_KEY=sk-your-api-key-here
SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL=deepseek-ai/DeepSeek-V3.2
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=BAAI/bge-m3
SEAHORSE_AGENT_ADAPTERS_AI_RERANK_MODEL=Qwen/Qwen3-Reranker-8B
```

### 2. 配置项说明

| 配置项 | 说明 | 示例值 |
|--------|------|--------|
| `SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL` | API 基础地址 | `https://api.siliconflow.cn/v1` |
| `SEAHORSE_AGENT_ADAPTERS_AI_API_KEY` | API 密钥 | `sk-xxx` |
| `SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL` | 对话模型 | `deepseek-ai/DeepSeek-V3.2` |
| `SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL` | 向量化模型 | `BAAI/bge-m3` |
| `SEAHORSE_AGENT_ADAPTERS_AI_RERANK_MODEL` | 重排序模型 | `Qwen/Qwen3-Reranker-8B` |

## 支持的模型服务商

### 1. SiliconFlow（推荐）

```bash
SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL=https://api.siliconflow.cn/v1
SEAHORSE_AGENT_ADAPTERS_AI_API_KEY=sk-your-siliconflow-key
SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL=deepseek-ai/DeepSeek-V3.2
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=BAAI/bge-m3
SEAHORSE_AGENT_ADAPTERS_AI_RERANK_MODEL=Qwen/Qwen3-Reranker-8B
```

**推荐模型组合：**
- 对话：`deepseek-ai/DeepSeek-V3.2`（性价比高）
- 向量化：`BAAI/bge-m3`（多语言支持）
- 重排序：`Qwen/Qwen3-Reranker-8B`（中文优化）

### 2. OpenAI

```bash
SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL=https://api.openai.com/v1
SEAHORSE_AGENT_ADAPTERS_AI_API_KEY=sk-your-openai-key
SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL=gpt-4o
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=text-embedding-3-large
SEAHORSE_AGENT_ADAPTERS_AI_RERANK_MODEL=gpt-4o-mini
```

### 3. 阿里云百炼

```bash
SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
SEAHORSE_AGENT_ADAPTERS_AI_API_KEY=sk-your-dashscope-key
SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL=qwen-plus
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=text-embedding-v3
SEAHORSE_AGENT_ADAPTERS_AI_RERANK_MODEL=gte-rerank
```

### 4. 智谱 AI

```bash
SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL=https://open.bigmodel.cn/api/paas/v4
SEAHORSE_AGENT_ADAPTERS_AI_API_KEY=your-zhipu-key
SEAHORSE_AGENT_ADAPTERS_AI_CHAT_MODEL=glm-4-plus
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL=embedding-3
SEAHORSE_AGENT_ADAPTERS_AI_RERANK_MODEL=glm-4-flash
```

## 应用配置

### Docker 部署

修改 `.env` 文件后，重启后端容器：

```bash
docker compose -f docker-compose.full.yml restart backend
```

### 本地开发

修改 `.env` 文件后，重启 Spring Boot 应用即可。

## Web 界面配置

访问管理后台的 **模型配置** 页面（`/admin/model-config`）可以查看当前配置。

**注意：** 当前版本暂不支持在线编辑，需要手动修改 `.env` 文件并重启后端。

## 验证配置

1. 重启后端后，访问 `/admin/settings` 查看系统配置
2. 在 **模型服务提供方** 部分确认配置已生效
3. 在聊天界面测试对话功能

## 常见问题

### Q: 如何切换模型服务商？

A: 修改 `.env` 文件中的 `SEAHORSE_AGENT_ADAPTERS_AI_BASE_URL` 和 `SEAHORSE_AGENT_ADAPTERS_AI_API_KEY`，然后重启后端。

### Q: 支持同时配置多个模型服务商吗？

A: 当前版本仅支持单一服务商配置。未来版本将支持多服务商负载均衡。

### Q: 向量维度如何配置？

A: 向量维度通过 `SEAHORSE_AGENT_ADAPTERS_VECTOR_DIMENSION` 配置，默认为 1024。需要与 Embedding 模型的输出维度匹配。

### Q: 如何查看模型调用日志？

A: 查看后端容器日志：`docker logs seahorse-backend -f`

## 性能优化建议

1. **对话模型**：选择响应速度快的模型，如 DeepSeek-V3.2
2. **向量化模型**：选择维度适中的模型（512-1024），平衡精度和性能
3. **重排序模型**：可选配置，用于提升检索精度

## 成本优化

1. 使用国内服务商（如 SiliconFlow）降低延迟和成本
2. 合理设置 `top_k` 参数，避免过多的重排序调用
3. 启用缓存机制，减少重复调用

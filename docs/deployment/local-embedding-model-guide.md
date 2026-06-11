# Seahorse Agent 本地向量化模型部署指南

## 当前状态

**嵌入模型类型**: `mock` (测试用,不进行真实向量化)  
**向量维度**: 1024  
**向量数据库**: Milvus

## 本地部署选项

### 方案1: 使用OpenAI API (推荐,无需本地GPU)

**优势**: 无需本地硬件,按量付费  
**配置**:
```yaml
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_TYPE: openai-compatible
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL: text-embedding-3-small
SEAHORSE_AGENT_ADAPTERS_AI_OPENAI_API_KEY: sk-your-key
SEAHORSE_AGENT_ADAPTERS_AI_OPENAI_BASE_URL: https://api.openai.com/v1
```

**成本**: 
- text-embedding-3-small: $0.02 / 1M tokens
- text-embedding-3-large: $0.13 / 1M tokens

**硬件需求**: 无

---

### 方案2: 本地部署开源嵌入模型 (需要GPU)

#### 推荐模型

| 模型 | 维度 | 显存需求 | 性能 | 适用场景 |
|------|------|---------|------|---------|
| **bge-small-zh-v1.5** | 512 | 1-2GB | 中文优化 | **推荐首选** |
| **bge-base-zh-v1.5** | 768 | 2-3GB | 中文优化 | 平衡性能 |
| **bge-large-zh-v1.5** | 1024 | 4-6GB | 中文最佳 | 高精度需求 |
| all-MiniLM-L6-v2 | 384 | 0.5-1GB | 英文通用 | 资源受限 |
| gte-large-zh | 1024 | 4-6GB | 中文通用 | 高精度 |

#### 最小硬件配置 (bge-small-zh-v1.5)

**显卡**:
- **最低**: GTX 1050 Ti (4GB显存) - 勉强可用,较慢
- **推荐**: GTX 1660 / RTX 3050 (6GB显存) - 流畅运行
- **最佳**: RTX 3060 / RTX 4060 (8-12GB显存) - 高性能

**CPU**: 
- 最低: 4核 (Intel i5 / AMD Ryzen 5)
- 推荐: 8核以上

**内存**:
- 最低: 8GB
- 推荐: 16GB (考虑到其他容器)
- 理想: 32GB (企业级,多并发)

**磁盘**:
- 模型文件: 1-2GB
- Milvus数据: 根据文档量,10GB起步
- 总计推荐: 50GB+ SSD

#### 完整系统推荐 (bge-base-zh-v1.5)

```
显卡: RTX 3060 12GB / RTX 4060 8GB
CPU: 8核 (Ryzen 7 / i7)
内存: 16GB DDR4
存储: 256GB SSD + 1TB HDD
```

**预估成本**: ¥6000-8000 (仅GPU部分)

---

### 方案3: 使用本地Ollama部署 (简化版)

**优势**: 一键部署,自动管理模型

**硬件需求**:
```
显卡: 6GB+ (如RTX 3060)
内存: 16GB
存储: 20GB
```

**配置步骤**:
```bash
# 1. 安装Ollama
curl -fsSL https://ollama.com/install.sh | sh

# 2. 下载嵌入模型
ollama pull nomic-embed-text  # 英文,274M
ollama pull bge-large-zh      # 中文,1.3GB

# 3. 配置Seahorse
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_TYPE: openai-compatible
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL: bge-large-zh
SEAHORSE_AGENT_ADAPTERS_AI_OPENAI_BASE_URL: http://localhost:11434/v1
```

---

### 方案4: CPU-only部署 (无GPU)

**可行性**: ✅ 可以,但较慢  
**适用场景**: 测试环境,低并发

**硬件需求**:
```
CPU: 8核以上 (推荐16核)
内存: 32GB (模型会占用大量内存)
存储: SSD必须
```

**性能预估**:
- 单文档嵌入: 2-5秒 (GPU约0.1秒)
- 批量100文档: 3-10分钟 (GPU约5-30秒)

**建议**: 仅用于开发测试,生产环境必须使用GPU

---

## 性能对比

| 配置 | 单文档嵌入 | 1000文档批量 | 并发10用户 | 月成本 |
|------|-----------|-------------|-----------|--------|
| OpenAI API | 0.5s | 30s | 流畅 | ¥100-500 |
| 本地RTX 3060 | 0.1s | 10s | 流畅 | ¥0 (电费忽略) |
| 本地GTX 1050 Ti | 0.3s | 45s | 卡顿 | ¥0 |
| CPU-only (16核) | 3s | 300s | 排队 | ¥0 |

---

## 推荐方案总结

### 个人开发/测试
→ **Ollama + bge-small-zh-v1.5**  
硬件: GTX 1660 6GB / RTX 3050 8GB + 16GB内存

### 小团队 (5-20人)
→ **本地GPU + bge-base-zh-v1.5**  
硬件: RTX 3060 12GB + 32GB内存

### 企业生产 (20+人)
→ **OpenAI API** 或 **专用GPU服务器**  
硬件: RTX 4090 / A4000 + 64GB内存 + 集群

### 极简/无GPU
→ **OpenAI API**  
硬件: 无要求,按量付费

---

## 当前修改为真实嵌入模型

### 使用OpenAI (最简单)

修改 `docker-compose.full.yml`:
```yaml
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_TYPE: openai-compatible
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL: text-embedding-3-small
SEAHORSE_AGENT_ADAPTERS_AI_OPENAI_API_KEY: sk-your-key
```

### 使用本地Ollama

1. 启动Ollama容器:
```yaml
ollama:
  image: ollama/ollama:latest
  ports:
    - "11434:11434"
  volumes:
    - ollama-data:/root/.ollama
  deploy:
    resources:
      reservations:
        devices:
          - driver: nvidia
            count: 1
            capabilities: [gpu]
```

2. 配置Seahorse:
```yaml
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_TYPE: openai-compatible
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL: bge-large-zh
SEAHORSE_AGENT_ADAPTERS_AI_OPENAI_BASE_URL: http://ollama:11434/v1
```

---

## 常见问题

**Q: 没有GPU可以运行吗?**  
A: 可以,但推荐使用OpenAI API,性价比最高。

**Q: RTX 3060能跑多大的模型?**  
A: bge-large-zh (1024维) 轻松,支持并发5-10个请求。

**Q: 嵌入模型可以更换吗?**  
A: 可以,但更换后需要重新向量化所有已存储文档。

**Q: Milvus占用多少资源?**  
A: 内存2-4GB,CPU 1-2核,磁盘视数据量而定。

---

**更新时间**: 2026-06-10  
**适用版本**: Seahorse Agent 0.0.1-SNAPSHOT

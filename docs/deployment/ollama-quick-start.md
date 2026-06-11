# Seahorse Agent - Ollama 本地向量模型快速部署

## 已配置内容

### 向量模型
- **模型**: nomic-embed-text (英文优化)
- **大小**: 274MB
- **维度**: 768
- **性能**: 快速,适合小型部署

### Docker服务
- **Ollama容器**: seahorse-ollama (端口11434)
- **自动初始化**: 启动时自动下载模型

## 启动命令

```bash
# 启动所有服务(包括Ollama)
docker compose -f docker-compose.full.yml up -d

# 首次启动会自动下载模型,需等待2-5分钟
```

## 验证部署

```bash
# 1. 检查Ollama容器状态
docker ps | grep ollama

# 2. 查看已安装模型
docker exec seahorse-ollama ollama list

# 3. 测试向量化API
curl http://localhost:11434/api/embeddings -d '{
  "model": "nomic-embed-text",
  "prompt": "测试文本"
}'

# 4. 验证backend连接
docker logs seahorse-backend | grep -i "embedding"
```

## 硬件需求

### CPU-only部署 (已配置)
- CPU: 4核以上
- 内存: 8GB (推荐16GB)
- 存储: 5GB (模型+数据)
- 显卡: **不需要**

### 性能预估
- 单文档嵌入: 0.5-2秒
- 批量100文档: 1-3分钟
- 适用场景: 个人开发,小团队测试

## 升级到中文模型

如果需要更好的中文支持,可以更换模型:

```bash
# 1. 拉取中文模型
docker exec seahorse-ollama ollama pull bge-m3

# 2. 修改 docker-compose.full.yml
SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL: bge-m3
SEAHORSE_AGENT_ADAPTERS_VECTOR_DIMENSION: 1024

# 3. 重启backend
docker compose -f docker-compose.full.yml restart backend

# 注意: 更换模型后需要重新向量化所有文档!
```

### 推荐中文模型

| 模型名 | 大小 | 维度 | 语言 | 备注 |
|--------|------|------|------|------|
| nomic-embed-text | 274MB | 768 | 英文 | **当前使用,速度快** |
| bge-m3 | 2.2GB | 1024 | 中英 | 多语言,推荐 |
| bge-small-zh | 95MB | 512 | 中文 | 超小模型 |
| bge-base-zh | 400MB | 768 | 中文 | 平衡性能 |

## 常见问题

**Q: CPU部署够用吗?**  
A: 个人使用够用,并发多时建议升级GPU。

**Q: 如何添加GPU支持?**  
A: 修改docker-compose.yml添加:
```yaml
ollama:
  deploy:
    resources:
      reservations:
        devices:
          - driver: nvidia
            count: 1
            capabilities: [gpu]
```

**Q: 模型存储在哪里?**  
A: Docker卷 `ollama-data`,可用 `docker volume inspect seahorse-agent_ollama-data` 查看。

**Q: 如何清理模型?**  
```bash
# 删除模型
docker exec seahorse-ollama ollama rm nomic-embed-text

# 清理所有数据
docker compose -f docker-compose.full.yml down -v
```

**Q: Ollama占用多少资源?**  
- 闲时: 内存 ~200MB, CPU 0%
- 推理时: 内存 ~1-2GB, CPU 50-200%

## 监控与调试

```bash
# 查看Ollama日志
docker logs -f seahorse-ollama

# 查看模型下载进度
docker logs -f seahorse-ollama-init

# 测试嵌入性能
time curl -X POST http://localhost:11434/api/embeddings \
  -d '{"model":"nomic-embed-text","prompt":"测试"}'
```

## 生产环境建议

**小团队 (5-10人)**:
- 继续使用nomic-embed-text
- 升级到16GB内存
- 考虑独立Ollama服务器

**中型团队 (10-50人)**:
- 升级到bge-m3或bge-large-zh
- 添加GPU (RTX 3060+)
- 启用Ollama集群

**大型企业 (50+人)**:
- 使用OpenAI API或专业向量服务
- 或部署GPU集群 (A4000/V100+)

---

**配置时间**: 2026-06-10  
**模型版本**: nomic-embed-text latest  
**文档版本**: v1.0

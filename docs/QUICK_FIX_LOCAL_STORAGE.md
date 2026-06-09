# 快速切换到本地存储

## 步骤

```bash
# 1. 停止后端
docker stop seahorse-backend && docker rm seahorse-backend

# 2. 修改 docker-compose.full.yml
# 找到 backend 服务的环境变量，将：
#   SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE: s3
# 改为：
#   SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE: local
#   SEAHORSE_AGENT_ADAPTERS_STORAGE_LOCAL_ROOT: /app/uploads

# 3. 重新启动
docker-compose -f docker-compose.full.yml up -d backend

# 4. 等待启动
sleep 40

# 5. 测试上传
```

## 优点
- ✅ 立即可用
- ✅ 无外部依赖
- ✅ 适合开发和小规模部署

## 后续优化
生产环境建议切换回 MinIO/S3。

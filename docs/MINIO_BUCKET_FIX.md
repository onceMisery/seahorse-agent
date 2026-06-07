# MinIO Bucket 问题诊断与修复

**问题**：后端上传文档时报 `NoSuchBucketException`
**时间**：2026-06-07

---

## 快速修复方案

执行以下命令：

```bash
# 1. 配置 MinIO 客户端
docker exec seahorse-minio mc alias set myminio http://localhost:9000 minioadmin minioadmin

# 2. 删除旧 bucket（如果存在）
docker exec seahorse-minio mc rb --force myminio/seahorse 2>/dev/null || true

# 3. 创建新 bucket
docker exec seahorse-minio mc mb myminio/seahorse

# 4. 设置匿名访问策略（用于测试）
docker exec seahorse-minio mc anonymous set download myminio/seahorse

# 5. 验证 bucket
docker exec seahorse-minio mc ls myminio/

# 6. 重启后端
docker restart seahorse-backend

# 7. 等待启动
sleep 30

# 8. 测试上传（替换为你的知识库 ID 和文件）
# curl -X POST http://localhost:9090/knowledge-base/YOUR_KB_ID/docs/upload \
#   -H "Authorization: Bearer YOUR_TOKEN" \
#   -F "file=@test.txt"
```

---

## 如果还不行，尝试切换到本地存储

临时解决方案：使用本地文件存储代替 MinIO

```bash
# 停止后端
docker stop seahorse-backend

# 使用本地存储重启
docker run -d \
  --name seahorse-backend-temp \
  --network seahorse-agent_default \
  -p 9090:9090 \
  -e SERVER_PORT=9090 \
  -e SPRING_DATA_REDIS_HOST=redis \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/seahorse \
  -e SPRING_DATASOURCE_USERNAME=seahorse \
  -e SPRING_DATASOURCE_PASSWORD=seahorse \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE=local \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_LOCAL_BASE_PATH=/app/uploads \
  -e SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar \
  -e SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL=pulsar://pulsar-broker:6650 \
  seahorse-agent-backend:latest

# 然后测试上传
```

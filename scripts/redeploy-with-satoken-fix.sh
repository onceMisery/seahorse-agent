#!/bin/bash
# 修复sa-token Redis持久化后重新部署

set -e

echo "=== 1. 重新构建Docker镜像 ==="
docker build -t seahorse-agent-backend:latest -f Dockerfile.backend.simplified .

echo ""
echo "=== 2. 清理Redis中的旧数据 ==="
docker exec seahorse-redis redis-cli FLUSHDB

echo ""
echo "=== 3. 重启backend ==="
docker compose -f docker-compose.full.yml up -d --force-recreate backend

echo ""
echo "=== 4. 等待backend健康 ==="
for i in {1..30}; do
  status=$(docker inspect seahorse-backend --format='{{.State.Health.Status}}' 2>/dev/null)
  if [[ "$status" == "healthy" ]]; then
    echo "✅ Backend健康!"
    break
  fi
  echo "等待 $i/30..."
  sleep 2
done

echo ""
echo "=== 5. 验证Redis持久化 ==="
echo "登录前Redis keys数量:"
docker exec seahorse-redis redis-cli DBSIZE

curl -s -X POST "http://localhost:9090/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' > /dev/null

sleep 2

echo "登录后Redis keys数量:"
docker exec seahorse-redis redis-cli DBSIZE

echo ""
echo "sa-token keys:"
docker exec seahorse-redis redis-cli KEYS "satoken:*" | head -5

echo ""
echo "✅ 部署完成!"

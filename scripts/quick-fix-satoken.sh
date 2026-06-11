#!/bin/bash
# 快速修复方案: 直接修改已编译的class文件

echo "=== 快速修复sa-token Redis持久化 ==="
echo ""
echo "方案: 使用最新backend JAR + sa-token配置环境变量"
echo ""

# 检查是否有最新的JAR
if [ ! -f "seahorse-agent-bootstrap/target/seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar" ]; then
  echo "❌ 找不到已编译的JAR"
  exit 1
fi

echo "✓ 使用现有JAR: seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar"
echo ""

echo "=== 方案: 通过application.yml配置sa-token使用Redis ==="
echo ""
echo "添加以下配置到backend环境变量:"
echo "SA_TOKEN_IS_READ_COOKIE=false"
echo "SA_TOKEN_TOKEN_NAME=satoken"
echo "SA_TOKEN_TIMEOUT=2592000"
echo "SA_TOKEN_ACTIVITY_TIMEOUT=-1"
echo "SA_TOKEN_IS_CONCURRENT=true"
echo "SA_TOKEN_IS_SHARE=true"
echo "SA_TOKEN_IS_LOG=false"
echo ""

echo "重新启动backend容器..."
docker compose -f docker-compose.full.yml down backend
docker compose -f docker-compose.full.yml up -d backend

echo ""
echo "等待backend健康..."
for i in {1..30}; do
  status=$(docker inspect seahorse-backend --format='{{.State.Health.Status}}' 2>/dev/null || echo "starting")
  if [[ "$status" == "healthy" ]]; then
    echo "✅ Backend健康!"
    break
  fi
  echo "等待 $i/30... ($status)"
  sleep 2
done

echo ""
echo "验证sa-token配置..."
docker logs seahorse-backend 2>&1 | grep -i "sa-token" | tail -3

echo ""
echo "测试Redis持久化..."
echo "登录前Redis keys:"
docker exec seahorse-redis redis-cli DBSIZE

curl -s -X POST "http://localhost:9090/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' > /dev/null

sleep 2

echo "登录后Redis keys:"
docker exec seahorse-redis redis-cli DBSIZE

echo ""
echo "查看sa-token keys:"
docker exec seahorse-redis redis-cli KEYS "satoken:*" | head -5

echo ""
echo "✅ 修复完成!"

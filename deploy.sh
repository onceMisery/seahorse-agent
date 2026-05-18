#!/usr/bin/env bash
set -euo pipefail

# Seahorse Agent 全量部署脚本
# 用法: ./deploy.sh

COMPOSE_FILE="docker-compose.full.yml"
ENV_FILE=".env"
ENV_EXAMPLE=".env.full.example"

echo "============================================"
echo "  Seahorse Agent 全量部署"
echo "============================================"
echo ""

# 检查 Docker
if ! command -v docker &> /dev/null; then
  echo "错误: 未找到 Docker，请先安装 Docker Desktop"
  exit 1
fi

# 检查 Docker Compose
if ! docker compose version &> /dev/null; then
  echo "错误: 未找到 Docker Compose，请升级 Docker Desktop"
  exit 1
fi

echo "Docker: $(docker --version)"
echo "Compose: $(docker compose version)"
echo ""

# 检查 .env 文件
if [ ! -f "$ENV_FILE" ]; then
  echo "未找到 .env 文件，从 $ENV_EXAMPLE 复制..."
  if [ ! -f "$ENV_EXAMPLE" ]; then
    echo "错误: 未找到 $ENV_EXAMPLE"
    exit 1
  fi
  cp "$ENV_EXAMPLE" "$ENV_FILE"
  echo "已创建 $ENV_FILE，请编辑填入你的 AI API 配置后重新运行此脚本"
  echo ""
  echo "  vim $ENV_FILE"
  echo ""
  exit 0
fi

# 检查必填项
if grep -q "sk-your-api-key-here" "$ENV_FILE"; then
  echo "错误: 请先编辑 $ENV_FILE，填入你的 AI API Key"
  exit 1
fi

echo "配置文件: $ENV_FILE"
echo ""

# 构建并启动
echo "正在构建并启动所有服务..."
echo "（首次构建约 5-10 分钟，请耐心等待）"
echo ""

docker compose -f "$COMPOSE_FILE" up -d --build

echo ""
echo "============================================"
echo "  服务启动中..."
echo "============================================"
echo ""

# 等待关键服务健康
echo "等待服务健康检查..."
services=("seahorse-postgres" "seahorse-redis" "seahorse-elasticsearch" "seahorse-milvus" "seahorse-pulsar-broker" "seahorse-backend")
for svc in "${services[@]}"; do
  printf "  等待 %-30s" "$svc"
  for i in $(seq 1 60); do
    status=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "missing")
    if [ "$status" = "healthy" ]; then
      echo " ✓"
      break
    fi
    if [ "$i" -eq 60 ]; then
      echo " ⚠ 超时（服务可能仍在启动中）"
    fi
    sleep 5
  done
done

echo ""
echo "============================================"
echo "  部署完成！"
echo "============================================"
echo ""
echo "  前端:          http://localhost"
echo "  后端 API:      http://localhost:9090"
echo "  MinIO 控制台:  http://localhost:9001"
echo "  Milvus Attu:   http://localhost:8000"
echo "  Pulsar 管理台: http://localhost:8080"
echo "  Elasticsearch:  http://localhost:9200"
echo ""
echo "  默认账号: admin / admin"
echo ""
echo "  管理命令:"
echo "    查看状态:  docker compose -f $COMPOSE_FILE ps"
echo "    查看日志:  docker compose -f $COMPOSE_FILE logs -f backend"
echo "    停止服务:  docker compose -f $COMPOSE_FILE down"
echo "    清理数据:  docker compose -f $COMPOSE_FILE down -v"
echo ""

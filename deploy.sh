#!/usr/bin/env bash
set -euo pipefail

# Seahorse Agent 一键部署脚本
# 用法: ./deploy.sh [minimal|full]

MODE="${1:-full}"

echo "============================================"
echo "  Seahorse Agent 部署"
echo "============================================"
echo ""

# 检查 Docker
if ! command -v docker &> /dev/null; then
  echo "错误: 未找到 Docker，请先安装 Docker Desktop"
  exit 1
fi

if ! docker compose version &> /dev/null; then
  echo "错误: 未找到 Docker Compose，请升级 Docker Desktop"
  exit 1
fi

echo "Docker: $(docker --version)"
echo "Compose: $(docker compose version)"
echo ""

deploy_minimal() {
  echo ">>> 最小化部署: PostgreSQL + pgvector"
  echo ""

  COMPOSE_FILE="docker-compose.yml"
  ENV_FILE=".env"
  ENV_EXAMPLE=".env.example"

  if [ ! -f "$ENV_FILE" ]; then
    if [ -f "$ENV_EXAMPLE" ]; then
      cp "$ENV_EXAMPLE" "$ENV_FILE"
      echo "已创建 $ENV_FILE，请编辑填入 AI API 配置后重新运行"
      echo "  vim $ENV_FILE"
      exit 0
    fi
  fi

  echo "启动服务..."
  docker compose -f "$COMPOSE_FILE" up -d --build

  echo ""
  echo "============================================"
  echo "  部署完成！"
  echo "============================================"
  echo ""
  echo "  前端:     http://localhost"
  echo "  后端 API: http://localhost:9090"
  echo ""
  echo "  默认账号: admin / admin"
  echo ""
  echo "  管理命令:"
  echo "    查看状态: docker compose -f $COMPOSE_FILE ps"
  echo "    查看日志: docker compose -f $COMPOSE_FILE logs -f backend"
  echo "    停止服务: docker compose -f $COMPOSE_FILE down"
  echo "    清理数据: docker compose -f $COMPOSE_FILE down -v"
}

deploy_full() {
  echo ">>> 全量部署: PostgreSQL, Redis, Milvus, Pulsar, Elasticsearch, MinIO"
  echo ""

  COMPOSE_FILE="docker-compose.full.yml"
  ENV_FILE=".env"
  ENV_EXAMPLE=".env.full.example"

  if [ ! -f "$ENV_FILE" ]; then
    if [ -f "$ENV_EXAMPLE" ]; then
      cp "$ENV_EXAMPLE" "$ENV_FILE"
      echo "已创建 $ENV_FILE，请编辑填入 AI API 配置后重新运行"
      echo "  vim $ENV_FILE"
      exit 0
    fi
  fi

  if grep -q "sk-your-api-key-here" "$ENV_FILE"; then
    echo "错误: 请先编辑 $ENV_FILE，填入你的 AI API Key"
    exit 1
  fi

  echo "启动服务（首次构建约 5-10 分钟）..."
  docker compose -f "$COMPOSE_FILE" up -d --build

  echo ""
  echo "等待服务健康检查..."
  services=("seahorse-postgres" "seahorse-redis" "seahorse-elasticsearch" "seahorse-milvus" "seahorse-pulsar-broker" "seahorse-backend")
  for svc in "${services[@]}"; do
    printf "  等待 %-30s" "$svc"
    for i in $(seq 1 60); do
      status=$(docker inspect --format='{{.State.Health.Status}}' "$svc" 2>/dev/null || echo "missing")
      if [ "$status" = "healthy" ]; then
        echo " OK"
        break
      fi
      if [ "$i" -eq 60 ]; then
        echo " 超时"
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
  echo "  MinIO 控制台:  http://localhost:9001  (minioadmin / minioadmin)"
  echo "  Milvus Attu:   http://localhost:8000"
  echo "  Pulsar 管理台: http://localhost:8080"
  echo "  Elasticsearch: http://localhost:9200"
  echo ""
  echo "  默认账号: admin / admin"
  echo ""
  echo "  管理命令:"
  echo "    查看状态: docker compose -f $COMPOSE_FILE ps"
  echo "    查看日志: docker compose -f $COMPOSE_FILE logs -f backend"
  echo "    停止服务: docker compose -f $COMPOSE_FILE down"
  echo "    清理数据: docker compose -f $COMPOSE_FILE down -v"
}

case "$MODE" in
  minimal|min|m)
    deploy_minimal
    ;;
  full|f)
    deploy_full
    ;;
  *)
    echo "用法: ./deploy.sh [minimal|full]"
    echo ""
    echo "  minimal  最小化部署（仅 PostgreSQL + pgvector）"
    echo "  full     全量部署（所有中间件，默认）"
    exit 1
    ;;
esac

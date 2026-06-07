#!/bin/bash
set -euo pipefail

echo "=== Seahorse Agent 前端重新构建 ==="

# 1. 构建前端
echo ">>> 步骤 1/5: 构建前端"
cd frontend
npm run build
cd ..

# 2. 复制前端文件到后端资源目录
echo ">>> 步骤 2/5: 复制前端文件"
mkdir -p seahorse-agent-bootstrap/src/main/resources/static
rm -rf seahorse-agent-bootstrap/src/main/resources/static/*
cp -r frontend/dist/* seahorse-agent-bootstrap/src/main/resources/static/

# 3. 编译后端
echo ">>> 步骤 3/5: 编译后端"
./mvnw clean package -B -pl seahorse-agent-bootstrap -am -DskipTests -Dspotless.check.skip=true

# 4. 构建 Docker 镜像
echo ">>> 步骤 4/5: 构建 Docker 镜像"
docker build -t seahorse-agent-backend:latest -f - . <<'EOF'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY seahorse-agent-bootstrap/target/seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# 5. 重启容器
echo ">>> 步骤 5/5: 重启容器"
docker stop seahorse-backend 2>/dev/null || true
docker rm seahorse-backend 2>/dev/null || true
docker run -d --name seahorse-backend \
  --network seahorse-agent_seahorse-net \
  --network seahorse-agent_default \
  -p 9090:9090 \
  -e SERVER_PORT=9090 \
  -e SPRING_DATA_REDIS_HOST=redis \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/seahorse \
  -e SPRING_DATASOURCE_USERNAME=seahorse \
  -e SPRING_DATASOURCE_PASSWORD=seahorse \
  -e SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE=milvus \
  -e SEAHORSE_AGENT_ADAPTERS_VECTOR_MILVUS_HOST=milvus-standalone \
  -e SEAHORSE_AGENT_ADAPTERS_VECTOR_MILVUS_PORT=19530 \
  -e SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE=redis \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE=s3 \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_ENDPOINT=http://minio:9000 \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_ACCESS_KEY=minioadmin \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_SECRET_KEY=minioadmin \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_BUCKET=seahorse \
  -e SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar \
  -e SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL=pulsar://pulsar-broker:6650 \
  -e SEAHORSE_AGENT_ADAPTERS_SEARCH_TYPE=elasticsearch \
  -e SEAHORSE_AGENT_ADAPTERS_SEARCH_ELASTICSEARCH_HOST=elasticsearch \
  -e SEAHORSE_AGENT_ADAPTERS_SEARCH_ELASTICSEARCH_PORT=9200 \
  -e SEAHORSE_AGENT_ADAPTERS_OBSERVATION_TYPE=micrometer \
  -e SEAHORSE_AGENT_ADAPTERS_AI_TYPE=mock \
  seahorse-agent-backend:latest

echo ""
echo "=== 等待应用启动 (50秒) ==="
sleep 50
docker logs seahorse-backend 2>&1 | grep "Started SeahorseAgentApplication" || echo "应用可能仍在启动中..."

echo ""
echo "=== 完成！==="
echo "前端: http://localhost:9090"
echo "管理后台: http://localhost:9090/admin"
echo ""
echo "⚠️  重要: 请清除浏览器缓存后访问"
echo "  Chrome: Ctrl + Shift + Delete"
echo "  或按 Ctrl + F5 硬刷新"

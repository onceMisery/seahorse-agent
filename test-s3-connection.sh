#!/bin/bash
# 测试S3连接是否工作

echo "=== 测试S3连接 ==="

# 测试1: MinIO健康检查
echo "1. MinIO健康检查..."
curl -sf http://localhost:9001/minio/health/live && echo "✅ MinIO存活" || echo "❌ MinIO不可达"

# 测试2: 使用AWS CLI测试连接
echo -e "\n2. AWS CLI测试..."
docker run --rm --network seahorse-agent_default \
  -e AWS_ACCESS_KEY_ID=minioadmin \
  -e AWS_SECRET_ACCESS_KEY=minioadmin \
  amazon/aws-cli \
  --endpoint-url http://minio:9000 \
  s3 ls 2>&1 | head -5

# 测试3: 创建测试bucket
echo -e "\n3. 创建测试bucket..."
docker run --rm --network seahorse-agent_default \
  -e AWS_ACCESS_KEY_ID=minioadmin \
  -e AWS_SECRET_ACCESS_KEY=minioadmin \
  amazon/aws-cli \
  --endpoint-url http://minio:9000 \
  s3 mb s3://seahorse 2>&1 || echo "(bucket可能已存在)"

# 测试4: 验证bucket存在
echo -e "\n4. 验证seahorse bucket..."
docker run --rm --network seahorse-agent_default \
  -e AWS_ACCESS_KEY_ID=minioadmin \
  -e AWS_SECRET_ACCESS_KEY=minioadmin \
  amazon/aws-cli \
  --endpoint-url http://minio:9000 \
  s3 ls | grep seahorse && echo "✅ seahorse bucket存在" || echo "❌ seahorse bucket不存在"

echo -e "\n=== 测试完成 ==="

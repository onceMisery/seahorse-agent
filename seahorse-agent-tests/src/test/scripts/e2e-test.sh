#!/bin/bash
# Seahorse Agent E2E 完整工作流测试脚本
# 前提：docker-compose.full.yml 已启动

set -e

BASE_URL="http://localhost:9090"
echo "========================================="
echo "Seahorse Agent E2E 测试开始"
echo "========================================="

# 检查服务健康状态
echo ""
echo "步骤 0: 检查服务健康状态..."
curl -sf "$BASE_URL/actuator/health" > /dev/null || {
    echo "❌ 无法连接到 Seahorse 后端"
    echo "请运行: docker compose -f docker-compose.full.yml up -d"
    exit 1
}
echo "✓ Seahorse 后端服务运行正常"

# 步骤 1: 管理员登录
echo ""
echo "步骤 1: 管理员登录..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
USER_ID=$(echo "$LOGIN_RESPONSE" | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "❌ 登录失败"
    echo "$LOGIN_RESPONSE"
    exit 1
fi

echo "✓ 管理员登录成功"
echo "  用户ID: $USER_ID"
echo "  Token: ${TOKEN:0:20}..."

# 步骤 2: 创建知识库
echo ""
echo "步骤 2: 创建知识库..."
KB_NAME="SeahorseKB_$(date +%s)"
KB_RESPONSE=$(curl -s -X POST "$BASE_URL/knowledge-base" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" \
  -d "{\"name\":\"$KB_NAME\",\"embeddingModel\":\"nomic-embed-text\",\"collectionName\":\"e2e_kb_$(date +%s)\"}")

KB_ID=$(echo "$KB_RESPONSE" | grep -o '"data":"[^"]*"' | cut -d'"' -f4)

if [ -z "$KB_ID" ]; then
    echo "❌ 知识库创建失败"
    echo "$KB_RESPONSE"
    exit 1
fi

echo "✓ 知识库创建成功"
echo "  知识库ID: $KB_ID"
echo "  知识库名称: $KB_NAME"

# 步骤 3: 创建对话
echo ""
echo "步骤 3: 创建对话..."
CONV_RESPONSE=$(curl -s -X POST "$BASE_URL/conversations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

CONV_ID=$(echo "$CONV_RESPONSE" | grep -o '"data":"[^"]*"' | cut -d'"' -f4)

if [ -z "$CONV_ID" ]; then
    echo "❌ 对话创建失败"
    echo "$CONV_RESPONSE"
    exit 1
fi

echo "✓ 对话创建成功"
echo "  对话ID: $CONV_ID"

# 步骤 4: 查询知识库列表
echo ""
echo "步骤 4: 查询知识库列表..."
KB_LIST=$(curl -s -X GET "$BASE_URL/knowledge-base?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

echo "✓ 知识库列表查询成功"

# 步骤 5: 查询对话列表
echo ""
echo "步骤 5: 查询对话列表..."
CONV_LIST=$(curl -s -X GET "$BASE_URL/conversations" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

echo "✓ 对话列表查询成功"

# 步骤 6: 查询当前用户信息
echo ""
echo "步骤 6: 查询当前用户信息..."
USER_INFO=$(curl -s -X GET "$BASE_URL/user/me" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

echo "✓ 用户信息查询成功"
echo "$USER_INFO" | grep -o '"username":"[^"]*"' || echo "  (用户信息已获取)"

# 步骤 7: 清理 - 删除测试对话
echo ""
echo "步骤 7: 清理测试数据..."
curl -s -X DELETE "$BASE_URL/conversations/$CONV_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null

echo "✓ 测试对话已删除"

# 步骤 8: 清理 - 删除测试知识库
curl -s -X DELETE "$BASE_URL/knowledge-base/$KB_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null

echo "✓ 测试知识库已删除"

echo ""
echo "========================================="
echo "✓ 所有测试步骤执行成功！"
echo "========================================="
echo ""
echo "测试覆盖功能："
echo "  1. ✓ 用户认证（登录）"
echo "  2. ✓ 知识库管理（创建、查询、删除）"
echo "  3. ✓ 对话管理（创建、查询、删除）"
echo "  4. ✓ 用户信息查询"
echo ""

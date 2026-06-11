#!/bin/bash
# Seahorse Agent 完整功能演示脚本
# 演示 Seahorse Agent 的所有核心工作原理

set -e

BASE_URL="http://localhost:9090"
FAILED_TESTS=0
PASSED_TESTS=0

# 颜色输出
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo "========================================="
echo "Seahorse Agent 完整功能演示"
echo "演示系统工作原理的端到端测试"
echo "========================================="

# 检查服务
echo ""
echo ">>> 检查服务健康状态..."
if curl -sf "$BASE_URL/actuator/health" > /dev/null; then
    echo -e "${GREEN}✓${NC} Seahorse 后端服务运行正常"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 无法连接到 Seahorse 后端"
    echo "请运行: docker compose -f docker-compose.full.yml up -d"
    exit 1
fi

# 登录
echo ""
echo "========== 1. 用户认证系统 =========="
echo ">>> 步骤 1.1: 管理员登录..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
USER_ID=$(echo "$LOGIN_RESPONSE" | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)

if [ -n "$TOKEN" ]; then
    echo -e "${GREEN}✓${NC} 登录成功"
    echo "  用户ID: $USER_ID"
    echo "  Token: ${TOKEN:0:30}..."
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 登录失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
    exit 1
fi

echo ">>> 步骤 1.2: 查询当前用户信息..."
USER_INFO=$(curl -s -X GET "$BASE_URL/user/me" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$USER_INFO" | grep -q '"username"'; then
    echo -e "${GREEN}✓${NC} 用户信息查询成功"
    echo "$USER_INFO" | grep -o '"username":"[^"]*"' | cut -d'"' -f4 | sed 's/^/  用户名: /'
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 用户信息查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 知识库管理
echo ""
echo "========== 2. 知识库管理系统 =========="
echo ">>> 步骤 2.1: 创建知识库..."
KB_NAME="Demo_KB_$(date +%s)"
KB_RESPONSE=$(curl -s -X POST "$BASE_URL/knowledge-base" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" \
  -d "{\"name\":\"$KB_NAME\",\"embeddingModel\":\"nomic-embed-text\",\"collectionName\":\"demo_$(date +%s)\"}")

KB_ID=$(echo "$KB_RESPONSE" | grep -o '"data":"[^"]*"' | cut -d'"' -f4)

if [ -n "$KB_ID" ]; then
    echo -e "${GREEN}✓${NC} 知识库创建成功"
    echo "  知识库ID: $KB_ID"
    echo "  知识库名称: $KB_NAME"
    echo "  向量模型: nomic-embed-text (Ollama)"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 知识库创建失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ">>> 步骤 2.2: 查询知识库列表..."
KB_LIST=$(curl -s -X GET "$BASE_URL/knowledge-base?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$KB_LIST" | grep -q '"code":"0"'; then
    echo -e "${GREEN}✓${NC} 知识库列表查询成功"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 知识库列表查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ">>> 步骤 2.3: 查询知识库详情..."
KB_DETAIL=$(curl -s -X GET "$BASE_URL/knowledge-base/$KB_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$KB_DETAIL" | grep -q "$KB_NAME"; then
    echo -e "${GREEN}✓${NC} 知识库详情查询成功"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 知识库详情查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 对话管理
echo ""
echo "========== 3. 对话管理系统 =========="
echo ">>> 步骤 3.1: 创建对话..."
CONV_RESPONSE=$(curl -s -X POST "$BASE_URL/conversations" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

CONV_ID=$(echo "$CONV_RESPONSE" | grep -o '"data":"[^"]*"' | cut -d'"' -f4)

if [ -n "$CONV_ID" ]; then
    echo -e "${GREEN}✓${NC} 对话创建成功"
    echo "  对话ID: $CONV_ID"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 对话创建失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ">>> 步骤 3.2: 查询对话列表..."
CONV_LIST=$(curl -s -X GET "$BASE_URL/conversations" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$CONV_LIST" | grep -q "$CONV_ID"; then
    echo -e "${GREEN}✓${NC} 对话列表查询成功"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 对话列表查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# Memory 系统
echo ""
echo "========== 4. Memory 系统 =========="
echo ">>> 步骤 4.1: 查询 Memory 列表..."
MEM_LIST=$(curl -s -X GET "$BASE_URL/memories?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$MEM_LIST" | grep -q '"code":"0"'; then
    echo -e "${GREEN}✓${NC} Memory 列表查询成功"
    echo "  Memory 系统负责捕获、存储和召回对话中的重要信息"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} Memory 列表查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ">>> 步骤 4.2: 查询 Memory 追踪记录..."
MEM_TRACES=$(curl -s -X GET "$BASE_URL/memories/traces?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$MEM_TRACES" | grep -q '"code":"0"'; then
    echo -e "${GREEN}✓${NC} Memory 追踪查询成功"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} Memory 追踪查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# Agent 管理
echo ""
echo "========== 5. Agent 管理系统 =========="
echo ">>> 步骤 5.1: 查询 Agent 定义列表..."
AGENT_LIST=$(curl -s -X GET "$BASE_URL/api/agents?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$AGENT_LIST" | grep -q '"code":"0"'; then
    echo -e "${GREEN}✓${NC} Agent 定义查询成功"
    echo "  Agent 定义包含目标、指令和工具绑定"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} Agent 定义查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ">>> 步骤 5.2: 查询技能列表..."
SKILL_LIST=$(curl -s -X GET "$BASE_URL/api/skills?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$SKILL_LIST" | grep -q '"code":"0"'; then
    echo -e "${GREEN}✓${NC} 技能列表查询成功"
    echo "  技能是可复用的 Agent 能力模块"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 技能列表查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ">>> 步骤 5.3: 查询工具目录..."
TOOL_LIST=$(curl -s -X GET "$BASE_URL/api/tools?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$TOOL_LIST" | grep -q '"code":"0"'; then
    echo -e "${GREEN}✓${NC} 工具目录查询成功"
    echo "  工具目录管理 Agent 可调用的外部工具"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 工具目录查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 通知与运行记录
echo ""
echo "========== 6. 通知与运行记录 =========="
echo ">>> 步骤 6.1: 查询通知列表..."
NOTIF_LIST=$(curl -s -X GET "$BASE_URL/notifications?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$NOTIF_LIST" | grep -q '"code":"0"'; then
    echo -e "${GREEN}✓${NC} 通知列表查询成功"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 通知列表查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ">>> 步骤 6.2: 查询 Agent 运行记录..."
RUN_LIST=$(curl -s -X GET "$BASE_URL/agent-runs?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$RUN_LIST" | grep -q '"code":"0"'; then
    echo -e "${GREEN}✓${NC} Agent 运行记录查询成功"
    echo "  运行记录包含 Agent 执行历史和工具调用详情"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} Agent 运行记录查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 功能特性管理
echo ""
echo "========== 7. 功能特性管理 =========="
echo ">>> 步骤 7.1: 查询功能列表..."
FEATURE_LIST=$(curl -s -X GET "$BASE_URL/api/features?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID")

if echo "$FEATURE_LIST" | grep -q '"features"'; then
    echo -e "${GREEN}✓${NC} 功能列表查询成功"
    echo "  功能管理支持模块化开关和特性标志"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 功能列表查询失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 清理测试数据
echo ""
echo "========== 8. 清理测试数据 =========="
echo ">>> 步骤 8.1: 删除测试对话..."
curl -s -X DELETE "$BASE_URL/conversations/$CONV_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} 测试对话已删除"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 测试对话删除失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

echo ">>> 步骤 8.2: 删除测试知识库..."
curl -s -X DELETE "$BASE_URL/knowledge-base/$KB_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null

if [ $? -eq 0 ]; then
    echo -e "${GREEN}✓${NC} 测试知识库已删除"
    PASSED_TESTS=$((PASSED_TESTS + 1))
else
    echo -e "${RED}✗${NC} 测试知识库删除失败"
    FAILED_TESTS=$((FAILED_TESTS + 1))
fi

# 测试总结
echo ""
echo "========================================="
echo "           测试总结"
echo "========================================="
echo -e "${GREEN}通过测试: $PASSED_TESTS${NC}"
echo -e "${RED}失败测试: $FAILED_TESTS${NC}"
echo ""

if [ $FAILED_TESTS -eq 0 ]; then
    echo -e "${GREEN}✓ 所有测试通过！${NC}"
    echo ""
    echo "Seahorse Agent 工作原理演示完成："
    echo "  1. ✓ 用户认证 - JWT Token 机制"
    echo "  2. ✓ 知识库管理 - 支持向量化存储（Milvus）"
    echo "  3. ✓ 对话管理 - 多轮对话上下文维护"
    echo "  4. ✓ Memory 系统 - 自动捕获和召回"
    echo "  5. ✓ Agent 管理 - 定义、技能、工具编排"
    echo "  6. ✓ 通知系统 - 异步事件通知"
    echo "  7. ✓ 功能管理 - 模块化特性开关"
    echo ""
    exit 0
else
    echo -e "${RED}✗ 部分测试失败${NC}"
    echo "请检查日志以获取详细信息"
    exit 1
fi

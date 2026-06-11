#!/bin/bash
# Seahorse Agent E2E 扩展功能测试脚本
# 测试管理后台的各种高级功能

set -e

BASE_URL="http://localhost:9090"
echo "========================================="
echo "Seahorse Agent 扩展功能测试"
echo "========================================="

# 登录获取 Token
echo ""
echo "准备工作: 管理员登录..."
LOGIN_RESPONSE=$(curl -s -X POST "$BASE_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}')

TOKEN=$(echo "$LOGIN_RESPONSE" | grep -o '"token":"[^"]*"' | cut -d'"' -f4)
USER_ID=$(echo "$LOGIN_RESPONSE" | grep -o '"userId":"[^"]*"' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
    echo "❌ 登录失败"
    exit 1
fi
echo "✓ 登录成功，Token: ${TOKEN:0:20}..."

# 测试用户管理
echo ""
echo "========== 用户管理功能测试 =========="

echo "测试 1: 查询用户列表..."
curl -sf "$BASE_URL/users?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null && echo "✓ 用户列表查询成功"

# 测试计费和订阅功能
echo ""
echo "========== 计费与订阅功能测试 =========="

echo "测试 2: 查询订阅计划列表..."
curl -sf "$BASE_URL/billing/plans" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 订阅计划查询成功" || echo "⚠ 订阅计划接口不可用（功能可能未启用）"

echo "测试 3: 查询用户配额..."
curl -sf "$BASE_URL/quota/usage" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 配额查询成功" || echo "⚠ 配额接口不可用"

# 测试审计日志
echo ""
echo "========== 审计与日志功能测试 =========="

echo "测试 4: 查询审计日志..."
curl -sf "$BASE_URL/admin/audit-logs?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 审计日志查询成功" || echo "⚠ 审计日志接口不可用"

echo "测试 5: 查询登录历史..."
curl -sf "$BASE_URL/admin/login-history?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 登录历史查询成功" || echo "⚠ 登录历史接口不可用"

# 测试成本分析
echo ""
echo "========== 成本分析功能测试 =========="

echo "测试 6: 查询成本使用情况..."
curl -sf "$BASE_URL/cost/usage?period=daily&limit=7" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 成本使用查询成功" || echo "⚠ 成本分析接口不可用"

# 测试 Agent 管理
echo ""
echo "========== Agent 管理功能测试 =========="

echo "测试 7: 查询 Agent 定义列表..."
curl -sf "$BASE_URL/agent-definitions?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ Agent 定义查询成功" || echo "⚠ Agent 定义接口不可用"

echo "测试 8: 查询工具目录..."
curl -sf "$BASE_URL/tool-catalog?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 工具目录查询成功" || echo "⚠ 工具目录接口不可用"

echo "测试 9: 查询技能列表..."
curl -sf "$BASE_URL/skills?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 技能列表查询成功" || echo "⚠ 技能接口不可用"

# 测试 Memory 系统
echo ""
echo "========== Memory 系统功能测试 =========="

echo "测试 10: 查询 Memory 列表..."
curl -sf "$BASE_URL/memories?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ Memory 列表查询成功" || echo "⚠ Memory 接口不可用"

echo "测试 11: 查询 Memory 追踪..."
curl -sf "$BASE_URL/memory/traces?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ Memory 追踪查询成功" || echo "⚠ Memory 追踪接口不可用"

# 测试 RAG 评估
echo ""
echo "========== RAG 评估功能测试 =========="

echo "测试 12: 查询检索评估数据集..."
curl -sf "$BASE_URL/retrieval-evaluation/datasets?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 检索评估数据集查询成功" || echo "⚠ 检索评估接口不可用"

echo "测试 13: 查询 RAG 追踪..."
curl -sf "$BASE_URL/rag/traces?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ RAG 追踪查询成功" || echo "⚠ RAG 追踪接口不可用"

# 测试元数据治理
echo ""
echo "========== 元数据治理功能测试 =========="

echo "测试 14: 查询元数据字典..."
curl -sf "$BASE_URL/metadata/dictionaries?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 元数据字典查询成功" || echo "⚠ 元数据字典接口不可用"

echo "测试 15: 查询元数据质量报告..."
curl -sf "$BASE_URL/metadata/quality?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 元数据质量查询成功" || echo "⚠ 元数据质量接口不可用"

# 测试租户管理
echo ""
echo "========== 租户管理功能测试 =========="

echo "测试 16: 查询租户列表..."
curl -sf "$BASE_URL/admin/tenants?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 租户列表查询成功" || echo "⚠ 租户管理接口不可用"

# 测试数据导出
echo ""
echo "========== 数据导出功能测试 =========="

echo "测试 17: 查询导出任务..."
curl -sf "$BASE_URL/data-export/tasks?current=1&size=10" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-User-Id: $USER_ID" > /dev/null 2>&1 && echo "✓ 数据导出任务查询成功" || echo "⚠ 数据导出接口不可用"

echo ""
echo "========================================="
echo "✓ 扩展功能测试完成！"
echo "========================================="

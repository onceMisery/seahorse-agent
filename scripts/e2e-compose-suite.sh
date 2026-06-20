#!/usr/bin/env bash
# Seahorse Agent 全闭环 E2E 验证脚本
# 用法: ./scripts/e2e-compose-suite.sh [base_url]
# 默认 base_url: http://localhost:9090
#
# 覆盖四条主闭环:
#   1. 认证登录
#   2. RAG (知识库 + 问答 + Trace)
#   3. 记忆与画像
#   4. Agent 运行

set -euo pipefail

BASE_URL="${1:-http://localhost:9090}"
PASS=0
FAIL=0
SKIP=0

# ── 工具函数 ──

log() { echo -e "\n\033[1;36m[TEST]\033[0m $*"; }
ok()  { echo -e "\033[1;32m  PASS\033[0m $*"; ((PASS++)); }
fail(){ echo -e "\033[1;31m  FAIL\033[0m $*"; ((FAIL++)); }
skip(){ echo -e "\033[1;33m  SKIP\033[0m $*"; ((SKIP++)); }

api() {
  local method=$1 path=$2; shift 2
  curl -s -X "$method" "${BASE_URL}${path}" \
    -H "Authorization: Bearer ${TOKEN:-}" \
    -H "Content-Type: application/json" \
    "$@"
}

# ── 1. 认证闭环 ──

log "1. 认证闭环"

LOGIN_RESP=$(curl -s -X POST "${BASE_URL}/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' 2>/dev/null || echo "")

TOKEN=$(echo "$LOGIN_RESP" | jq -r '.data.token // empty' 2>/dev/null || echo "")

if [[ -n "$TOKEN" ]]; then
  ok "登录成功，获取 token"
else
  fail "登录失败: $(echo "$LOGIN_RESP" | jq -r '.message // "unknown error"' 2>/dev/null)"
  echo "无法继续，退出。"
  exit 1
fi

# 受保护接口
KB_RESP=$(api GET "/knowledge-base?pageNo=1&pageSize=1" 2>/dev/null || echo "")
if echo "$KB_RESP" | jq -e '.code == "0"' >/dev/null 2>&1; then
  ok "受保护接口 /knowledge-base 可访问"
else
  fail "/knowledge-base 返回异常"
fi

# ── 2. RAG 闭环 ──

log "2. RAG 闭环"

# 知识库列表
KB_LIST=$(api GET "/knowledge-base?pageNo=1&pageSize=5" 2>/dev/null || echo "")
KB_COUNT=$(echo "$KB_LIST" | jq -r '.data.total // 0' 2>/dev/null || echo "0")
if [[ "$KB_COUNT" -gt 0 ]]; then
  ok "知识库存在 (count=$KB_COUNT)"
  KB_ID=$(echo "$KB_LIST" | jq -r '.data.records[0].id' 2>/dev/null || echo "")
  
  # 文档列表
  DOC_RESP=$(api GET "/knowledge-base/${KB_ID}/docs?pageNo=1&pageSize=5" 2>/dev/null || echo "")
  if echo "$DOC_RESP" | jq -e '.code == "0"' >/dev/null 2>&1; then
    ok "知识库文档可查询"
  else
    fail "知识库文档查询失败"
  fi
else
  skip "无知识库，跳过 RAG 文档和问答验证"
fi

# RAG Trace 查询
TRACE_RESP=$(api GET "/rag/traces/runs?pageNo=1&pageSize=5" 2>/dev/null || echo "")
if echo "$TRACE_RESP" | jq -e '.code == "0"' >/dev/null 2>&1; then
  ok "RAG Trace 端点 /rag/traces/runs 可用"
else
  fail "RAG Trace 查询失败"
fi

# ── 3. 记忆与画像闭环 ──

log "3. 记忆与画像闭环"

# Readiness
READY_RESP=$(api GET "/memories/readiness" 2>/dev/null || echo "")
if echo "$READY_RESP" | jq -e '.code == "0"' >/dev/null 2>&1; then
  ok "记忆 readiness 端点可用"
else
  fail "记忆 readiness 查询失败"
fi

# Health
HEALTH_RESP=$(api GET "/memories/health" 2>/dev/null || echo "")
if echo "$HEALTH_RESP" | jq -e '.code == "0"' >/dev/null 2>&1; then
  ok "记忆 health 端点可用"
else
  fail "记忆 health 查询失败"
fi

# Profile facts
PF_RESP=$(api GET "/memories/profile-facts?limit=5" 2>/dev/null || echo "")
if echo "$PF_RESP" | jq -e '.code == "0"' >/dev/null 2>&1; then
  ok "画像事实端点可用"
else
  fail "画像事实查询失败"
fi

# ── 4. Agent 闭环 ──

log "4. Agent 闭环"

# Agent 列表
AGENT_RESP=$(api GET "/api/agents?pageNo=1&pageSize=5" 2>/dev/null || echo "")
if echo "$AGENT_RESP" | jq -e '.code == "0"' >/dev/null 2>&1; then
  ok "Agent 列表端点可用"
else
  fail "Agent 列表查询失败"
fi

# Agent runs
RUNS_RESP=$(api GET "/api/agent-runs?pageNo=1&pageSize=5" 2>/dev/null || echo "")
if echo "$RUNS_RESP" | jq -e '.code == "0"' >/dev/null 2>&1; then
  ok "Agent runs 端点可用"
else
  fail "Agent runs 查询失败"
fi

# ── 5. 基础设施检查 ──

log "5. 基础设施检查"

# Actuator health
ACT_RESP=$(curl -s "${BASE_URL}/actuator/health" 2>/dev/null || echo "")
if echo "$ACT_RESP" | jq -e '.status == "UP"' >/dev/null 2>&1; then
  ok "actuator/health 状态 UP"
else
  fail "actuator/health 非 UP"
fi

# Prometheus 指标（如果有）
PROM_RESP=$(curl -s -o /dev/null -w "%{http_code}" "${BASE_URL}/actuator/prometheus" 2>/dev/null || echo "000")
if [[ "$PROM_RESP" == "200" ]]; then
  ok "Prometheus 指标端点可用"
else
  skip "Prometheus 指标不可用 (HTTP $PROM_RESP)"
fi

# ── 6. RAG 评测冒烟 ──

log "6. RAG 评测冒烟"

# 使用第一个知识库进行评测
FIRST_KB=$(api GET "/knowledge-base?page=1&size=1" | jq -r '.data.records[0].id // empty' 2>/dev/null || echo "")
if [[ -z "$FIRST_KB" ]]; then
  skip "无可用知识库，跳过 RAG 评测"
else
  # 创建评测数据集
  DS_RESP=$(api POST "/knowledge-base/${FIRST_KB}/retrieval-evaluation-datasets" \
    -d '{"datasetId":"","name":"ci-smoke","description":"CI smoke","enabled":true,"cases":[{"caseId":"s1","question":"Seahorse Agent embedding model","expectedKbIds":["'"$FIRST_KB"'"],"expectedDocIds":[],"expectedChunkIds":[],"negativeChunkIds":[],"tags":["smoke"],"minRecall":0.5}]}' 2>/dev/null || echo "")
  DS_ID=$(echo "$DS_RESP" | jq -r '.data.datasetId // empty' 2>/dev/null || echo "")
  if [[ -n "$DS_ID" ]]; then
    ok "评测数据集创建成功 ($DS_ID)"
    # 运行评测
    EVAL_RESP=$(api POST "/knowledge-base/${FIRST_KB}/retrieval-evaluation-datasets/${DS_ID}/evaluate" \
      -d '{"strategyName":"ci-smoke","topK":5}' 2>/dev/null || echo "")
    EVAL_RECALL=$(echo "$EVAL_RESP" | jq -r '.data.recallAtK // empty' 2>/dev/null || echo "")
    EVAL_CASES=$(echo "$EVAL_RESP" | jq -r '.data.evaluableCaseCount // empty' 2>/dev/null || echo "")
    if [[ -n "$EVAL_RECALL" && "$EVAL_CASES" -gt 0 ]] 2>/dev/null; then
      ok "评测运行成功: recall@k=$EVAL_RECALL, cases=$EVAL_CASES"
    else
      fail "评测运行失败或无结果"
    fi
  else
    fail "评测数据集创建失败"
  fi
  # 策略模板检查
  TMPL_RESP=$(api GET "/knowledge-base/${FIRST_KB}/retrieval-strategy-templates" 2>/dev/null || echo "")
  TMPL_COUNT=$(echo "$TMPL_RESP" | jq '.data | length' 2>/dev/null || echo "0")
  if [[ "$TMPL_COUNT" -gt 0 ]]; then
    ok "策略模板可用: ${TMPL_COUNT} 个"
  else
    fail "策略模板为空"
  fi
fi

# ── 结果汇总 ──

echo ""
echo "========================================"
echo "  E2E 验证结果"
echo "========================================"
echo -e "  通过: \033[1;32m${PASS}\033[0m"
echo -e "  失败: \033[1;31m${FAIL}\033[0m"
echo -e "  跳过: \033[1;33m${SKIP}\033[0m"
echo "========================================"

if [[ "$FAIL" -gt 0 ]]; then
  exit 1
fi
exit 0

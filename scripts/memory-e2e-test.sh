#!/bin/bash

# Seahorse Agent 记忆闭环 E2E 测试脚本
# 使用真实chat流程触发记忆聚合

set -e

echo "========================================="
echo "Seahorse Agent 记忆闭环 E2E 测试"
echo "========================================="
echo

# 配置
BACKEND_URL="${BACKEND_URL:-http://localhost:9090}"
USERNAME="${TEST_USERNAME:-admin}"
PASSWORD="${TEST_PASSWORD:-admin123}"

# 登录
echo "=== 1. 登录系统 ==="
TOKEN=$(curl -s -X POST "$BACKEND_URL/auth/login" \
  -H "Content-Type: application/json" \
  -d "{\"username\":\"$USERNAME\",\"password\":\"$PASSWORD\"}" | \
  grep -o '"token":"[^"]*' | cut -d'"' -f4)

if [ -z "$TOKEN" ]; then
  echo "❌ 登录失败"
  exit 1
fi

echo "✅ 登录成功: ${TOKEN:0:20}..."
echo

# 创建对话并发送多轮消息
echo "=== 2. 通过真实chat流程创建对话 ==="
echo "提示: 使用/rag/v3/chat API进行流式对话"
echo

# 第1轮对话
echo "发送第1轮对话..."
RESPONSE1=$(curl -s -X GET "$BACKEND_URL/rag/v3/chat?query=你好，我叫张三。我是一名Python开发者，喜欢机器学习。" \
  -H "Authorization: Bearer $TOKEN")

CONV_ID=$(echo "$RESPONSE1" | grep -o 'conversationId[^,}]*' | head -1 | grep -o '[0-9]*' | head -1)
echo "Conversation ID: $CONV_ID"

if [ -z "$CONV_ID" ]; then
  echo "❌ 未获取到对话ID"
  echo "Response: $RESPONSE1"
  exit 1
fi

sleep 3

# 第2轮对话
echo "发送第2轮对话..."
curl -s -X GET "$BACKEND_URL/rag/v3/chat?conversationId=$CONV_ID&query=我最近在研究TensorFlow和PyTorch框架。" \
  -H "Authorization: Bearer $TOKEN" > /dev/null
sleep 3

# 第3轮对话
echo "发送第3轮对话..."
curl -s -X GET "$BACKEND_URL/rag/v3/chat?conversationId=$CONV_ID&query=你能推荐一些深度学习的学习资源吗？" \
  -H "Authorization: Bearer $TOKEN" > /dev/null
sleep 3

# 第4轮对话
echo "发送第4轮对话..."
curl -s -X GET "$BACKEND_URL/rag/v3/chat?conversationId=$CONV_ID&query=我特别关注NLP领域。" \
  -H "Authorization: Bearer $TOKEN" > /dev/null
sleep 3

# 第5轮对话
echo "发送第5轮对话..."
curl -s -X GET "$BACKEND_URL/rag/v3/chat?conversationId=$CONV_ID&query=对Transformer架构很感兴趣。" \
  -H "Authorization: Bearer $TOKEN" > /dev/null
sleep 3

# 第6轮对话 (达到maxTurns=5的阈值)
echo "发送第6轮对话 (触发聚合)..."
curl -s -X GET "$BACKEND_URL/rag/v3/chat?conversationId=$CONV_ID&query=谢谢你的建议！" \
  -H "Authorization: Bearer $TOKEN" > /dev/null

echo "✅ 已发送6轮对话"
echo

# 等待聚合处理
echo "=== 3. 等待记忆聚合处理 (35秒) ==="
for i in {35..1}; do
  echo -ne "\r等待中... $i 秒 "
  sleep 1
done
echo
echo

# 验证短期记忆
echo "=== 4. 验证短期记忆 (t_message) ==="
MESSAGE_COUNT=$(docker exec seahorse-postgres psql -U seahorse -d seahorse -t -c \
  "SELECT COUNT(*) FROM t_message WHERE conversation_id=$CONV_ID AND deleted=0;")
echo "对话消息数: $MESSAGE_COUNT"
if [ "$MESSAGE_COUNT" -ge "6" ]; then
  echo "✅ 短期记忆正常"
else
  echo "⚠️ 短期记忆数量不足"
fi
echo

# 验证聚合buffer
echo "=== 5. 验证聚合缓冲区 (t_memory_aggregation_buffer) ==="
BUFFER_DATA=$(docker exec seahorse-postgres psql -U seahorse -d seahorse -t -c \
  "SELECT user_id, turn_count, status FROM t_memory_aggregation_buffer LIMIT 5;")
if [ -n "$BUFFER_DATA" ]; then
  echo "Buffer记录:"
  echo "$BUFFER_DATA"
  echo "✅ 聚合buffer有数据"
else
  echo "⚠️ Buffer为空 (可能已处理或未触发)"
fi
echo

# 验证长期记忆
echo "=== 6. 验证长期记忆 (t_long_term_memory) ==="
LTM_COUNT=$(docker exec seahorse-postgres psql -U seahorse -d seahorse -t -c \
  "SELECT COUNT(*) FROM t_long_term_memory WHERE deleted=0;")
echo "长期记忆数量: $LTM_COUNT"

if [ "$LTM_COUNT" -gt "0" ]; then
  echo "✅ 长期记忆已生成"
  echo
  echo "长期记忆内容 (前3条):"
  docker exec seahorse-postgres psql -U seahorse -d seahorse -c \
    "SELECT id, memory_category, title, LEFT(content, 50) as content_preview
     FROM t_long_term_memory
     WHERE deleted=0
     ORDER BY create_time DESC
     LIMIT 3;"
else
  echo "❌ 长期记忆未生成"
fi
echo

# 验证用户画像
echo "=== 7. 验证用户画像 (t_user_profile_fact) ==="
PROFILE_COUNT=$(docker exec seahorse-postgres psql -U seahorse -d seahorse -t -c \
  "SELECT COUNT(*) FROM t_user_profile_fact WHERE deleted=0;")
echo "用户画像数量: $PROFILE_COUNT"

if [ "$PROFILE_COUNT" -gt "0" ]; then
  echo "✅ 用户画像已生成"
  echo
  echo "用户画像内容:"
  docker exec seahorse-postgres psql -U seahorse -d seahorse -c \
    "SELECT slot_key, value_text, confidence_level, status
     FROM t_user_profile_fact
     WHERE deleted=0
     ORDER BY create_time DESC
     LIMIT 5;"
else
  echo "❌ 用户画像未生成"
fi
echo

# 测试跨会话记忆召回
echo "=== 8. 测试跨会话记忆召回 ==="
echo "创建新会话并提问..."
RESPONSE_NEW=$(curl -s -X GET "$BACKEND_URL/rag/v3/chat?query=根据你对我的了解，推荐适合我的学习路径。" \
  -H "Authorization: Bearer $TOKEN")

echo "AI回答片段:"
echo "$RESPONSE_NEW" | grep -o 'content[^}]*' | head -c 200
echo "..."
echo

if echo "$RESPONSE_NEW" | grep -qi "python\|tensorflow\|machine.*learning\|nlp"; then
  echo "✅ AI回答中提到了用户偏好 (跨会话记忆生效)"
else
  echo "⚠️ AI回答未明确提到用户偏好"
fi
echo

# 总结
echo "========================================="
echo "测试完成摘要"
echo "========================================="
echo "对话ID: $CONV_ID"
echo "短期记忆: $MESSAGE_COUNT 条"
echo "长期记忆: $LTM_COUNT 条"
echo "用户画像: $PROFILE_COUNT 条"
echo
echo "预期结果:"
echo "- 短期记忆: ≥6条 ✅"
echo "- 长期记忆: ≥1条 (包含用户偏好摘要)"
echo "- 用户画像: ≥1条 (包含language_preference等)"
echo "- 跨会话召回: AI能基于长期记忆回答"
echo
echo "如果长期记忆/用户画像为0，可能原因:"
echo "1. 聚合Job未运行 (检查后台日志)"
echo "2. 聚合条件未满足 (需要5轮+30秒空闲)"
echo "3. AI调用失败 (检查AI模型配置)"
echo "========================================="

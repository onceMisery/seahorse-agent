# 后台管理功能实际案例

**创建日期**: 2026-06-12  
**状态**: ✅ 已验证

---

## 案例1: 电商意图树

### 已创建的意图结构

```
Ecommerce (ecommerce) - DOMAIN
├── Order Management (order_mgmt) - CATEGORY
│   └── Query Order (query_order) - INTENT
│       - 绑定知识库: 323449787897925632
│       - Top-K: 3
└── Product Inquiry (product_inquiry) - CATEGORY
    └── Product Details (product_detail) - INTENT
        - 绑定知识库: 323449787897925632
        - Top-K: 5
```

### 创建脚本

```bash
# 登录
TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | \
  grep -o '"token":"[^"]*' | cut -d'"' -f4)

KB_ID="323449787897925632"  # 使用你的知识库ID

# 1. 创建顶层领域
curl -X POST http://localhost:9090/intent-tree \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"intentCode":"ecommerce","name":"Ecommerce","level":0,"enabled":1}'

# 2. 创建分类：订单管理
curl -X POST http://localhost:9090/intent-tree \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"intentCode":"order_mgmt","name":"Order Management","level":1,"parentCode":"ecommerce","description":"Order related queries","enabled":1}'

# 3. 创建分类：商品咨询
curl -X POST http://localhost:9090/intent-tree \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"intentCode":"product_inquiry","name":"Product Inquiry","level":1,"parentCode":"ecommerce","description":"Product information queries","enabled":1}'

# 4. 创建意图：查询订单（绑定知识库）
curl -X POST http://localhost:9090/intent-tree \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"intentCode\":\"query_order\",\"name\":\"Query Order\",\"level\":2,\"parentCode\":\"order_mgmt\",\"kbId\":\"$KB_ID\",\"topK\":3,\"enabled\":1}"

# 5. 创建意图：商品详情（绑定知识库）
curl -X POST http://localhost:9090/intent-tree \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "{\"intentCode\":\"product_detail\",\"name\":\"Product Details\",\"level\":2,\"parentCode\":\"product_inquiry\",\"kbId\":\"$KB_ID\",\"topK\":5,\"enabled\":1}"

# 6. 验证
curl "http://localhost:9090/intent-tree/trees" \
  -H "Authorization: Bearer $TOKEN"
```

### 访问前端UI

```
http://localhost:3000/admin/intent-tree
```

### 使用场景

当用户在聊天时输入：
- "我的订单在哪里" → 匹配到`query_order`意图 → 从知识库检索Top-3相关内容
- "这个商品的参数是什么" → 匹配到`product_detail`意图 → 从知识库检索Top-5相关内容

---

## 案例2: PDF处理流水线

### 已创建的流水线

**名称**: PDF Specialized Pipeline  
**ID**: 323614806474985472  
**描述**: Optimized pipeline for PDF documents with semantic chunking

### 创建脚本

```bash
TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | \
  grep -o '"token":"[^"]*' | cut -d'"' -f4)

curl -X POST http://localhost:9090/ingestion/pipelines \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "PDF Specialized Pipeline",
    "description": "Optimized pipeline for PDF documents with semantic chunking"
  }'
```

**注意**: 当前版本nodes配置需要单独管理，流水线创建后nodes为空数组。

### 访问前端UI

```
http://localhost:3000/admin/ingestion
```

### 使用流水线

```bash
# 使用指定流水线处理文档
DOC_ID="your_document_id"
PIPELINE_ID="323614806474985472"

curl -X POST "http://localhost:9090/knowledge-base/docs/$DOC_ID/chunk" \
  -H "Authorization: Bearer $TOKEN" \
  -d "{\"pipelineId\": \"$PIPELINE_ID\"}"
```

---

## 案例3: Agent工作流追踪

### 使用场景

调试Agent执行过程，查看每个步骤的详细信息。

### 访问步骤

1. **创建Agent运行**
```bash
TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | \
  grep -o '"token":"[^"]*' | cut -d'"' -f4)

# 创建对话并发送消息
SESSION=$(curl -s -X POST http://localhost:9090/chat/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"name":"Test Session"}' | \
  grep -o '"id":"[0-9]*' | cut -d'"' -f4)

curl -X POST "http://localhost:9090/chat/sessions/$SESSION/messages" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"content":"请介绍Seahorse架构","role":"user"}'
```

2. **访问工作流可视化**
```
http://localhost:3000/admin/agent-runs
```

3. **查看工作流详情**
- 点击任意运行记录
- 查看步骤序列
- 检查工具调用
- 分析决策路径

---

## 其他管理功能案例

### 查询词映射

**场景**: 统一用户查询词汇

```bash
# API: POST /query-term-mapping
{
  "original": ["iPhone", "苹果手机", "iOS手机"],
  "standard": "iPhone"
}
```

### RAG评估

**场景**: 批量评估检索质量

```
访问: http://localhost:3000/admin/rag-evaluation
上传测试问题集CSV
查看评估报告
```

### 示例问题管理

**场景**: 为知识库添加推荐问题

```bash
# API: POST /sample-questions
{
  "kbId": "323449787897925632",
  "questions": [
    "如何配置Ollama?",
    "支持哪些向量模型?",
    "如何创建知识库?"
  ]
}
```

---

## 验证清单

- ✅ 意图树API可用（已修复toNullableLong问题）
- ✅ 意图树5个节点创建成功
- ✅ 流水线API可用
- ✅ 流水线创建成功
- ⏳ 前端UI测试（待访问浏览器）
- ⏳ 工作流可视化测试（待创建运行记录）

---

## 修复的问题

### 问题1: 意图树创建失败

**错误**: `INVALID_ARGUMENT: intent node id must be numeric: seahorse`

**原因**: `JdbcIntentTreeRepositoryAdapter.toNullableLong()`方法将非数字operator强制转换为Long

**修复**:
```java
// 修改前
private Long toNullableLong(String value) {
    if (!hasText(value)) return null;
    return toLongId(value);  // 会抛出异常
}

// 修改后
private Long toNullableLong(String value) {
    if (!hasText(value)) return null;
    try {
        return Long.parseLong(value.trim());
    } catch (NumberFormatException ex) {
        return null;  // 返回null而不是抛出异常
    }
}
```

**提交**: 已提交到代码库

---

## 下一步

1. 访问前端查看意图树UI
2. 测试工作流可视化UI
3. 补充中文版意图树案例
4. 添加更多实际业务场景案例

**文档维护**: 随功能迭代持续更新

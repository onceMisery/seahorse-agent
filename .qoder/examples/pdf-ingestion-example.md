# PDF文档摄取完整示例

本文档演示如何使用 **nextNodeId** 创建PDF摄取流水线并上传文档。

## 📋 流程说明

```
上传PDF → fetcher-1 → parser-1 → enhancer-1 → chunker-1 → indexer-1
          (获取)     (解析)     (AI增强)    (分块)      (向量化)
```

**架构特点**：
- ✅ 使用 `nextNodeId` 明确连线关系
- ✅ 链式执行，简单清晰
- ✅ 自动检测循环依赖

---

## 🚀 完整操作步骤

### Step 1: 创建流水线

**请求**:
```bash
curl -X POST "http://localhost:8080/api/seahorse-agent/ingestion/pipelines" \
  -H "Content-Type: application/json" \
  -d @pdf-pipeline-request.json
```

**请求体** (`pdf-pipeline-request.json`):

**说明**:
- `nextNodeId`: 指向下一个要执行的节点
- 最后一个节点 (indexer-1) 不需要 `nextNodeId` 字段
- 引擎自动找到起始节点（没有被引用的节点）

**响应**:
```json
{
  "success": true,
  "data": {
    "id": 1,
    "name": "pdf-ingestion-pipeline",
    "description": "PDF文档摄取流水线 - 解析、AI增强、分块、向量化",
    "nodeCount": 5
  }
}
```

---

### Step 2: 上传PDF文档

**请求**:
```bash
curl -X POST "http://localhost:8080/api/seahorse-agent/ingestion/tasks/upload" \
  -F "pipelineId=1" \
  -F "file=@/path/to/your/document.pdf" \
  -F "metadata={\"category\":\"manual\",\"department\":\"IT\"}"
```

**说明**:
- `file`: 本地PDF文件路径
- `metadata`: 自定义元数据（可选）
- `pipelineId`: 第一步返回的流水线 ID（若已设置默认流水线可省略）

**响应**:
```json
{
  "success": true,
  "data": {
    "taskId": 123,
    "status": "RUNNING",
    "pipelineId": 1
  }
}
```

---

### Step 3: 查看任务状态

**请求**:
```bash
curl "http://localhost:8080/api/seahorse-agent/ingestion/tasks/123"
```

**响应（执行中）**:
```json
{
  "success": true,
  "data": {
    "taskId": 123,
    "pipelineId": 1,
    "status": "RUNNING",
    "startTime": "2026-01-22T14:30:00"
  }
}
```

**响应（完成）**:
```json
{
  "success": true,
  "data": {
    "taskId": 123,
    "pipelineId": 1,
    "status": "COMPLETED",
    "startTime": "2026-01-22T14:30:00",
    "completeTime": "2026-01-22T14:30:45",
    "chunks": 35
  }
}
```

---

## 🎯 节点连线说明

### 核心概念

**nextNodeId**: 下一个节点ID
- 每个节点通过 `nextNodeId` 指向下一个要执行的节点
- 最后一个节点不需要设置 `nextNodeId`
- 形成链式执行: A → B → C → D

**执行流程**:
1. 引擎自动找到起始节点（fetcher-1，没有被任何节点引用）
2. 执行 fetcher-1，完成后查看 `nextNodeId`
3. 执行 parser-1，完成后查看 `nextNodeId`
4. 依次执行后续节点，直到没有 `nextNodeId`

---

## 📊 配置字段说明

### NodeConfig 字段

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `nodeId` | String | ✅ | 节点唯一ID |
| `nodeType` | String | ✅ | 节点类型 (FETCHER/PARSER/ENHANCER等) |
| `nextNodeId` | String | - | 下一个节点ID（最后一个节点不需要） |
| `settings` | Object | - | 节点配置 |
| `condition` | String | - | 条件表达式 |

---

## ⚠️ 常见错误

### 错误1: 循环依赖

```json
{
  "nodes": [
    {"nodeId": "a", "nextNodeId": "b"},
    {"nodeId": "b", "nextNodeId": "c"},
    {"nodeId": "c", "nextNodeId": "a"}  // ❌ 循环!
  ]
}
```

**错误信息**:
```
Pipeline contains cycle: a
```

### 错误2: 引用不存在的节点

```json
{
  "nodes": [
    {"nodeId": "parser-1", "nextNodeId": "enhancer-999"}  // ❌ 节点不存在
  ]
}
```

**错误信息**:
```
Next node not found: enhancer-999 referenced by parser-1
```

### 错误3: 没有起始节点

```json
{
  "nodes": [
    {"nodeId": "a", "nextNodeId": "b"},
    {"nodeId": "b", "nextNodeId": "a"}  // ❌ 所有节点都被引用
  ]
}
```

**错误信息**:
```
No start node found in pipeline
```

---

## 🧪 快速测试脚本

**完整自动化测试**:
```bash
#!/bin/bash

API_BASE="http://localhost:8080/api/seahorse-agent"

# 1. 创建流水线
echo "📝 Creating pipeline..."
PIPELINE_RESPONSE=$(curl -s -X POST "${API_BASE}/ingestion/pipelines" \
  -H "Content-Type: application/json" \
  -d @pdf-pipeline-request.json)

PIPELINE_ID=$(echo $PIPELINE_RESPONSE | jq -r '.data.id')
echo "✅ Pipeline created: ID=${PIPELINE_ID}"

# 2. 上传PDF
echo "📤 Uploading PDF..."
TASK_RESPONSE=$(curl -s -X POST "${API_BASE}/ingestion/tasks/upload" \
  -F "file=@test.pdf" \
  -F "metadata={\"test\":true}")

TASK_ID=$(echo $TASK_RESPONSE | jq -r '.data.taskId')
echo "✅ Task created: ID=${TASK_ID}"

# 3. 等待完成
echo "⏳ Waiting for completion..."
while true; do
  STATUS_RESPONSE=$(curl -s "${API_BASE}/ingestion/tasks/${TASK_ID}")
  STATUS=$(echo $STATUS_RESPONSE | jq -r '.data.status')

  if [ "$STATUS" == "COMPLETED" ]; then
    echo "✅ Task completed!"
    break
  elif [ "$STATUS" == "FAILED" ]; then
    echo "❌ Task failed!"
    exit 1
  fi

  sleep 2
done

# 4. 查看结果
echo "📊 Task summary:"
echo $STATUS_RESPONSE | jq '.data'

echo "📋 Node details:"
curl -s "${API_BASE}/ingestion/tasks/${TASK_ID}/nodes" | jq '.data[] | {nodeType, status, durationMs}'

echo "🎉 Test completed successfully!"
```

---

## 📝 一键创建命令

**简化版（单行）**:
```bash
curl -X POST "http://localhost:8080/api/seahorse-agent/ingestion/pipelines" \
  -H "Content-Type: application/json" \
  -d '{
    "name": "pdf-pipeline",
    "description": "PDF摄取流水线",
    "nodes": [
      {"nodeId": "fetcher-1", "nodeType": "FETCHER", "nextNodeId": "parser-1"},
      {"nodeId": "parser-1", "nodeType": "PARSER", "settings": {"rules": [{"mimeType": "PDF"}]}, "nextNodeId": "enhancer-1"},
      {"nodeId": "enhancer-1", "nodeType": "ENHANCER", "settings": {"modelId": "qwen-max", "tasks": [{"type": "CONTEXT_ENHANCE"}]}, "nextNodeId": "chunker-1"},
      {"nodeId": "chunker-1", "nodeType": "CHUNKER", "settings": {"strategy": "FIXED_SIZE", "chunkSize": 512, "overlapSize": 128}, "nextNodeId": "indexer-1"},
      {"nodeId": "indexer-1", "nodeType": "INDEXER", "settings": {"collectionName": "pdf_documents", "includeEnhancedContent": true}}
    ]
  }'
```

---

## 📚 相关文档

- [配置指南](../ingestion-pipeline-config-guide.md)
- [执行示例](../pipeline-execution-guide.md)
- [领域提示词库](../domain-specific-prompts-guide.md)

---

**创建时间**: 2026-01-22
**更新时间**: 2026-01-22
**维护者**: Seahorse Agent Team

# PDF 文档摄取示例

本文演示如何通过当前后端入库接口创建 Pipeline 并上传 PDF。接口路径以 `seahorse-agent-adapter-web` 中的 Controller 为准。

示例请求中的 `modelId` 为空字符串，表示使用当前运行时默认 Chat 模型；`embeddingModel` 使用全量部署默认的 `nomic-embed-text`，对应 768 维向量。

## 前置条件

- 使用全量部署或已开启入库高级能力。
- 后端地址：`http://localhost:9090`
- 已登录并拿到 token。

```bash
TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')
```

## 流程

```text
上传 PDF -> fetcher-1 -> parser-1 -> enhancer-1 -> chunker-1 -> indexer-1
```

节点通过 `nextNodeId` 串联。最后一个节点不需要 `nextNodeId`。

## 1. 创建 Pipeline

```bash
PIPELINE_ID=$(curl -s -X POST "http://localhost:9090/ingestion/pipelines" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @docs/examples/pdf-pipeline-request.json \
  | jq -r '.data.id')

echo "$PIPELINE_ID"
```

响应形状：

```json
{
  "code": "0",
  "data": {
    "id": "pipeline-id",
    "name": "pdf-ingestion-pipeline"
  }
}
```

## 2. 上传 PDF

```bash
TASK_ID=$(curl -s -X POST "http://localhost:9090/ingestion/tasks/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "pipelineId=$PIPELINE_ID" \
  -F "file=@/path/to/document.pdf" \
  | jq -r '.data.taskId // .data.id')

echo "$TASK_ID"
```

当前上传接口接收：

| 字段 | 说明 |
|---|---|
| `pipelineId` | Pipeline ID |
| `file` | 上传文件 |

如需传入业务元数据，可使用 `POST /ingestion/tasks` 创建任务，并在 JSON 中提供 `metadata`。

## 3. 查询任务

```bash
curl "http://localhost:9090/ingestion/tasks/$TASK_ID" \
  -H "Authorization: Bearer $TOKEN"

curl "http://localhost:9090/ingestion/tasks/$TASK_ID/nodes" \
  -H "Authorization: Bearer $TOKEN"
```

## 4. 常见错误

### 循环依赖

```json
{
  "nodes": [
    {"nodeId": "a", "nextNodeId": "b"},
    {"nodeId": "b", "nextNodeId": "a"}
  ]
}
```

Pipeline 中不能出现环。

### 引用不存在的节点

```json
{
  "nodes": [
    {"nodeId": "parser-1", "nextNodeId": "enhancer-999"}
  ]
}
```

`nextNodeId` 必须指向同一个 Pipeline 内存在的节点。

### 高级能力未开启

如果返回能力不可用，检查：

```env
SEAHORSE_AGENT_ADVANCED_INGESTION_PIPELINE_MANAGEMENT_ENABLED=true
SEAHORSE_AGENT_ADVANCED_INGESTION_TASK_MANAGEMENT_ENABLED=true
```

## 快速脚本

```bash
#!/usr/bin/env bash
set -euo pipefail

API_BASE="http://localhost:9090"

TOKEN=$(curl -s -X POST "$API_BASE/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' \
  | jq -r '.data.token')

PIPELINE_ID=$(curl -s -X POST "$API_BASE/ingestion/pipelines" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @docs/examples/pdf-pipeline-request.json \
  | jq -r '.data.id')

TASK_ID=$(curl -s -X POST "$API_BASE/ingestion/tasks/upload" \
  -H "Authorization: Bearer $TOKEN" \
  -F "pipelineId=$PIPELINE_ID" \
  -F "file=@test.pdf" \
  | jq -r '.data.taskId // .data.id')

curl -s "$API_BASE/ingestion/tasks/$TASK_ID" \
  -H "Authorization: Bearer $TOKEN" | jq .

curl -s "$API_BASE/ingestion/tasks/$TASK_ID/nodes" \
  -H "Authorization: Bearer $TOKEN" | jq .
```

## 相关文件

- `docs/examples/pdf-pipeline-request.json`
- `docs/USER_GUIDE.md`
- `docs/TROUBLESHOOTING_GUIDE.md`

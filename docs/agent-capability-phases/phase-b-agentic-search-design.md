# Phase B 详细设计：Agentic Search 工具化检索

> 上游总方案：`docs/agent-capability-phased-implementation-plan.md`  
> 前置依赖：Phase A `KernelAgentLoop`、`ToolPort`、`ToolRegistryPort` 已可用。  
> 本阶段目标：把现有 RAG 检索能力包装为 Agent 可调用工具，让 LLM 在运行时决定是否检索、如何检索、是否继续检索。

---

## 1. 范围与原则

### 1.1 范围

- 新增 `search_knowledge_base` 工具。
- 新增 `query_metadata` 工具，帮助模型理解可用 metadata 字段。
- 复用 `KernelRetrievalEngine`、`KernelMultiChannelRetrievalEngine`、metadata filter compiler、RRF/Rerank/PostProcessor。
- 将工具调用纳入 trace 和可选 audit。

### 1.2 非目标

- 不改变 `KernelChatPipeline` 的 RAG 默认链路。
- 不直接访问 vector、keyword、Milvus、Lucene adapter。
- 不实现租户权限强治理；权限强约束在 Phase F 收敛，本阶段预留 `DataScopeContext` 参数。

---

## 2. 类设计与接口定义

### 2.1 新增类

| 类 | 包 | 职责 |
|---|---|---|
| `SearchKnowledgeBaseToolPortAdapter` | `kernel/application/agent/tool` | `search_knowledge_base` 工具实现 |
| `QueryMetadataToolPortAdapter` | `kernel/application/agent/tool` | 查询 metadata schema / dictionary |
| `AgentSearchToolProperties` | `spring-boot-starter` | topK、最大结果、超时、是否启用 |
| `AgentSearchToolRegistrar` | `spring-boot-starter` | 启动时注册 search tools |
| `AgentToolJsonSupport` | `kernel/application/agent/tool` | 工具参数和 observation JSON 序列化 |
| `AgentSearchObservation` | `kernel/domain/agent/tool` | 检索工具 observation DTO |
| `AgentSearchRequest` | `kernel/domain/agent/tool` | 强类型工具参数 |

### 2.2 `search_knowledge_base` descriptor

```java
public final class SearchKnowledgeBaseToolDescriptors {
    public static ToolDescriptor searchKnowledgeBase() {
        return new ToolDescriptor(
                "search_knowledge_base",
                "Search Knowledge Base",
                "Search enterprise knowledge bases using Seahorse RAG retrieval pipeline.",
                SEARCH_SCHEMA);
    }
}
```

JSON Schema：

```json
{
  "type": "object",
  "required": ["query"],
  "properties": {
    "query": { "type": "string", "minLength": 1 },
    "topK": { "type": "integer", "minimum": 1, "maximum": 20 },
    "knowledgeBaseIds": {
      "type": "array",
      "items": { "type": "string" }
    },
    "filters": {
      "type": "object",
      "additionalProperties": true
    },
    "searchMode": {
      "type": "string",
      "enum": ["AUTO", "VECTOR", "KEYWORD", "HYBRID"]
    },
    "rewriteHint": { "type": "string" }
  }
}
```

### 2.3 `SearchKnowledgeBaseToolPortAdapter`

```java
public class SearchKnowledgeBaseToolPortAdapter implements ToolPort {
    private final KernelRetrievalEngine retrievalEngine;
    private final AgentToolJsonSupport jsonSupport;
    private final AgentSearchToolProperties properties;
    private final KernelRagTraceRecorder traceRecorder;

    @Override
    public ToolInvocationResult invoke(String toolCallId, String toolId, Map<String, Object> arguments) {
        try {
            AgentSearchRequest request = jsonSupport.convert(arguments, AgentSearchRequest.class);
            AgentSearchObservation observation = search(toolCallId, request);
            return ToolInvocationResult.ok(jsonSupport.write(observation));
        } catch (Exception ex) {
            return ToolInvocationResult.failed("search_knowledge_base failed: " + safeMessage(ex));
        }
    }
}
```

关键约束：

- 必须捕获异常并返回 `ToolInvocationResult.failed`。
- `topK` 超限时归一化为配置上限。
- 空 query 直接返回 failed observation，不进入检索。
- filters 只能转成现有 `RetrievalFilter` 支持的字段。

### 2.4 依赖接口

```java
public interface MetadataToolSchemaPort {
    List<MetadataFieldDescriptor> listSearchableFields(List<String> knowledgeBaseIds);
}
```

第一阶段可以由现有 metadata schema service 或 noop 实现支撑；缺失时 `query_metadata` 返回空字段列表和提示。

---

## 3. 数据库表结构设计

Phase B 默认不新增核心表，复用：

| 表 | 用途 |
|---|---|
| `t_rag_trace_run` / `t_rag_trace_node` | 记录 Agent search tool trace |
| `t_retrieval_evaluation_dataset` | 复用现有检索评测集 |
| `t_retrieval_evaluation_run` | 保存检索评测运行结果 |

如果需要对 Agentic Search 单独沉淀评测样本，新增：

```sql
CREATE TABLE IF NOT EXISTS t_agentic_search_eval_case (
    id VARCHAR(64) PRIMARY KEY,
    kb_id VARCHAR(64),
    case_name VARCHAR(128) NOT NULL,
    question TEXT NOT NULL,
    expected_sources JSONB NOT NULL DEFAULT '[]'::jsonb,
    expected_keywords JSONB NOT NULL DEFAULT '[]'::jsonb,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agentic_search_eval_case_kb
ON t_agentic_search_eval_case (kb_id, enabled, deleted, update_time);

COMMENT ON TABLE t_agentic_search_eval_case IS 'Agentic Search 评测样本表';
COMMENT ON COLUMN t_agentic_search_eval_case.expected_sources IS '期望命中的文档或 chunk 来源';
COMMENT ON COLUMN t_agentic_search_eval_case.expected_keywords IS '期望答案或检索结果包含的关键词';
```

该表不是首个 PR 的硬依赖；优先使用现有 retrieval evaluation。

---

## 4. API 接口规范

Phase B 不新增对外业务 API；能力通过 `chatMode=agent` 触发。

### 4.1 Agent 聊天调用

```http
GET /rag/v3/chat?question=查询公司报销流程&chatMode=agent
Accept: text/event-stream
```

预期内部 tool call：

```json
{
  "tool_id": "search_knowledge_base",
  "arguments": {
    "query": "公司报销流程",
    "topK": 5,
    "searchMode": "HYBRID"
  }
}
```

工具 observation：

```json
{
  "query": "公司报销流程",
  "topK": 5,
  "resultCount": 3,
  "chunks": [
    {
      "chunkId": "ck_001",
      "documentId": "doc_001",
      "title": "报销制度",
      "score": 0.87,
      "content": "员工报销需...",
      "metadata": {
        "department": "finance"
      }
    }
  ],
  "qualitySignals": {
    "empty": false,
    "maxScore": 0.87
  }
}
```

### 4.2 管理调试 API（可选）

```http
POST /api/seahorse-agent/agent-tools/search-knowledge-base/_debug
Content-Type: application/json
```

仅在 `seahorse-agent.web.debug-agent-tools-enabled=true` 时启用，用于本地调试，不对生产开放。

---

## 5. 实现步骤

1. 在 `kernel/domain/agent/tool` 增加 `AgentSearchRequest`、`AgentSearchObservation`。
2. 在 `kernel/application/agent/tool` 增加 `AgentToolJsonSupport`。
3. 实现 `SearchKnowledgeBaseToolPortAdapter`。
4. 实现 `QueryMetadataToolPortAdapter`。
5. 在 starter 增加 `AgentSearchToolRegistrar`：
   - 条件：`seahorse-agent.chat.agent-mode-enabled=true`
   - 条件：存在 `ToolRegistryPort` 和 `KernelRetrievalEngine`
6. 添加配置：
   - `seahorse-agent.chat.agent.tools.search.enabled=true`
   - `seahorse-agent.chat.agent.tools.search.default-top-k=5`
   - `seahorse-agent.chat.agent.tools.search.max-top-k=20`
7. 将 search tool 调用写入 trace node。
8. 增加单元测试和 auto configuration 测试。

---

## 6. 异常处理与边界情况

| 场景 | 处理 |
|---|---|
| query 为空 | 返回 failed observation：`query is required` |
| topK 非法 | 归一化到 `[1,maxTopK]` |
| filters 非法 | 忽略非法字段并在 observation warnings 中返回 |
| 检索为空 | success=true，`resultCount=0`，让模型决定是否改写再搜 |
| 检索异常 | failed observation，不抛出到 loop |
| metadata schema 不可用 | `query_metadata` 返回空列表和 warning |
| LLM 多次重复检索同一 query | 本阶段只记录；Phase F 可加预算 guard |

---

## 7. 测试用例设计

| 测试类 | 用例 |
|---|---|
| `SearchKnowledgeBaseToolPortAdapterTests` | query 正常时调用 `KernelRetrievalEngine` 并返回 JSON |
| `SearchKnowledgeBaseToolPortAdapterTests` | 空 query 返回 failed |
| `SearchKnowledgeBaseToolPortAdapterTests` | topK 超限被裁剪 |
| `SearchKnowledgeBaseToolPortAdapterTests` | 检索异常转 failed observation |
| `QueryMetadataToolPortAdapterTests` | schema port 可用时返回字段 |
| `AgentSearchToolRegistrarTests` | agent 开启时注册工具 |
| `KernelAgentLoopTests` | 模型调用 search tool 后继续回答 |
| `KernelRetrievalEngineTests` | RAG 原检索行为不变 |

回归命令：

```powershell
.\mvnw.cmd -pl seahorse-agent-tests -am "-DfailIfNoTests=false" test
```

---

## 8. 性能指标与监控方案

| 指标 | 目标 |
|---|---|
| `agent.search.latency.p95` | 小于 2s，不含 LLM |
| `agent.search.empty.rate` | 持续监控，异常升高告警 |
| `agent.search.result.count.avg` | 1-8 |
| `agent.search.tool.failure.rate` | 小于 3% |
| `agent.search.repeated.query.rate` | 小于 10% |

监控实现：

- `KernelRagTraceRecorder`：记录 `AGENT_TOOL/search_knowledge_base`。
- `ObservationPort`：timer `seahorse.agent.tool.search.duration`。
- 日志字段：`taskId`、`toolCallId`、`queryHash`、`topK`、`resultCount`、`latencyMs`。


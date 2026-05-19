# Phase F 详细设计：企业治理、权限审计与评测回流

> 上游总方案：`docs/agent-capability-phased-implementation-plan.md`  
> 前置依赖：Phase B 工具化检索、Phase C 任务运行时、Phase D 输出治理、Phase E 记忆工具。  
> 本阶段目标：补齐企业级多租户、权限范围、工具审计、知识图谱端口和在线评测回流。

---

## 1. 范围与原则

### 1.1 范围

- 统一 `DataScopeContext`。
- 所有 Agent tool call 写审计。
- 检索和记忆工具强制服务端 scope。
- 新增在线评测候选样本。
- 定义知识图谱查询端口和工具。

### 1.2 非目标

- 不在第一阶段绑定具体图数据库。
- 不把权限判断交给 LLM。
- 不保存完整敏感工具参数，只保存 hash 和摘要。

---

## 2. 类设计与接口定义

### 2.1 治理领域对象

```java
public record DataScopeContext(
        String userId,
        String tenantId,
        Set<String> roles,
        Set<String> knowledgeBaseIds,
        Map<String, Object> attributes) {
}

public record ToolAuditEvent(
        String auditId,
        String taskId,
        String phaseId,
        String toolCallId,
        String toolId,
        String argumentHash,
        DataScopeContext scope,
        boolean success,
        long latencyMs,
        Map<String, Object> resultSummary,
        String errorMessage) {
}
```

### 2.2 端口

```java
public interface DataScopePolicyPort {
    DataScopeContext resolve(DataScopeRequest request);
}

public interface AgentToolAuditPort {
    void record(ToolAuditEvent event);
}

public interface AgentEvaluationCandidatePort {
    void save(AgentEvaluationCandidate candidate);
}

public interface KnowledgeGraphQueryPort {
    KnowledgeGraphResult query(KnowledgeGraphQuery query);
}
```

### 2.3 实现类

| 类 | 职责 |
|---|---|
| `DefaultDataScopePolicyPort` | 从 userId/tenantId/roles 解析数据范围 |
| `AuditedToolPortDecorator` | 包装所有 `ToolPort`，统一审计 |
| `ScopedSearchKnowledgeBaseToolPortAdapter` | 在 Phase B search tool 上强制注入 scope |
| `ScopedMemoryToolPolicy` | 在 Phase E memory tools 上强制 user/tenant scope |
| `AgentEvaluationFeedbackCollector` | 从差评、空检索、修复失败、人工大改收集候选 |
| `KnowledgeGraphQueryToolPortAdapter` | 暴露 `query_knowledge_graph` |

---

## 3. 数据库表结构设计

### 3.1 工具审计表

```sql
CREATE TABLE IF NOT EXISTS t_agent_tool_audit (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64),
    phase_id VARCHAR(128),
    tool_call_id VARCHAR(128),
    tool_id VARCHAR(128) NOT NULL,
    user_id VARCHAR(64),
    tenant_id VARCHAR(64),
    argument_hash VARCHAR(128) NOT NULL,
    scope_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    success SMALLINT NOT NULL,
    latency_ms BIGINT NOT NULL,
    result_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    error_message TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_tool_audit_task
ON t_agent_tool_audit (task_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_agent_tool_audit_tool
ON t_agent_tool_audit (tool_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_agent_tool_audit_tenant
ON t_agent_tool_audit (tenant_id, create_time DESC);

COMMENT ON TABLE t_agent_tool_audit IS 'Agent 工具调用审计表';
```

### 3.2 在线评测候选表

```sql
CREATE TABLE IF NOT EXISTS t_agent_evaluation_candidate (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64),
    phase_id VARCHAR(128),
    conversation_id VARCHAR(64),
    user_id VARCHAR(64),
    tenant_id VARCHAR(64),
    source_type VARCHAR(64) NOT NULL,
    question TEXT,
    answer TEXT,
    context_summary JSONB NOT NULL DEFAULT '{}'::jsonb,
    labels JSONB NOT NULL DEFAULT '[]'::jsonb,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_agent_eval_candidate_status
ON t_agent_evaluation_candidate (status, source_type, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_agent_eval_candidate_tenant
ON t_agent_evaluation_candidate (tenant_id, status, create_time DESC);

COMMENT ON TABLE t_agent_evaluation_candidate IS 'Agent 在线评测候选样本表';
```

### 3.3 数据范围策略表（可选）

```sql
CREATE TABLE IF NOT EXISTS t_agent_data_scope_policy (
    id VARCHAR(64) PRIMARY KEY,
    tenant_id VARCHAR(64),
    policy_key VARCHAR(128) NOT NULL,
    policy_json JSONB NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_agent_data_scope_policy
ON t_agent_data_scope_policy (COALESCE(tenant_id, ''), policy_key)
WHERE deleted = 0;

COMMENT ON TABLE t_agent_data_scope_policy IS 'Agent 数据范围策略表';
```

---

## 4. API 接口规范

### 4.1 查询工具审计

```http
GET /api/seahorse-agent/agent-tool-audits?taskId=agt_xxx&toolId=search_knowledge_base
```

响应：

```json
{
  "code": "0",
  "data": {
    "records": [
      {
        "toolId": "search_knowledge_base",
        "success": true,
        "latencyMs": 320,
        "scopeSummary": {"tenantId": "t1", "kbCount": 2}
      }
    ],
    "total": 1
  }
}
```

### 4.2 查询评测候选

```http
GET /api/seahorse-agent/agent-evaluation-candidates?status=PENDING
```

### 4.3 标记候选样本

```http
POST /api/seahorse-agent/agent-evaluation-candidates/{id}/actions
```

```json
{
  "action": "ACCEPT",
  "labels": ["empty-retrieval", "needs-human-review"]
}
```

---

## 5. 实现步骤

1. 新增 governance domain DTO。
2. 实现 `DataScopePolicyPort` 默认版本。
3. 实现 `AuditedToolPortDecorator`，在工具注册时包装所有 `ToolPort`。
4. 修改 search/memory/mcp tool adapter，接收服务端 scope，不信任 LLM 传入 scope。
5. 新增 `JdbcAgentToolAuditAdapter`。
6. 新增 `AgentEvaluationFeedbackCollector`：
   - 订阅 message feedback。
   - 记录 empty retrieval。
   - 记录 output validation failed。
   - 记录人工大幅 revise。
7. 定义 `KnowledgeGraphQueryPort` 和 `query_knowledge_graph` tool。
8. 新增 Web 管理接口和前端管理页。

---

## 6. 异常处理与边界情况

| 场景 | 处理 |
|---|---|
| 无 tenantId | 按单租户默认 scope 处理，但记录 scope warning |
| LLM 请求未授权 KB | 服务端 scope 覆盖，未授权 ID 被忽略 |
| 审计写入失败 | 不阻断主流程，记录 error；可配置 fail-closed |
| evaluation candidate 重复 | 基于 taskId/sourceType/question hash 去重 |
| 图谱端口不可用 | `query_knowledge_graph` 不注册或返回 failed observation |
| 参数含敏感信息 | 审计只写 hash 和脱敏摘要 |

---

## 7. 测试用例设计

| 测试类 | 用例 |
|---|---|
| `DefaultDataScopePolicyPortTests` | user/tenant/roles 解析 scope |
| `AuditedToolPortDecoratorTests` | 成功/失败 tool call 均写审计 |
| `ScopedSearchKnowledgeBaseToolTests` | 未授权 KB 被过滤 |
| `ScopedMemoryToolPolicyTests` | 跨用户 memory 操作被拒绝 |
| `AgentEvaluationFeedbackCollectorTests` | 差评生成候选样本 |
| `AgentEvaluationFeedbackCollectorTests` | empty retrieval 生成候选样本 |
| `JdbcAgentToolAuditAdapterTests` | JSONB scope/result 保存读取 |
| `SeahorseAgentToolAuditControllerTests` | 分页查询审计 |

---

## 8. 性能指标与监控方案

| 指标 | 目标 |
|---|---|
| `agent.tool.audit.coverage` | 100% |
| `agent.scope.violation.count` | 0 |
| `agent.tool.audit.write.latency.p95` | 小于 50ms |
| `agent.eval.candidate.created.count` | 可观测 |
| `agent.unauthorized.retrieval.count` | 0 |
| `agent.knowledge_graph.query.latency.p95` | 视后端配置 |

监控：

- 所有 tool call 记录 auditId。
- 权限拒绝以 warn 日志记录，含 taskId、toolId、scopeSummary。
- 评测候选按 sourceType 聚合，用于后续 RAG/Agent 质量看板。


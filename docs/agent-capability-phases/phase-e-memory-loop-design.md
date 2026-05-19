# Phase E 详细设计：Agent 记忆读写工具化闭环

> 上游总方案：`docs/agent-capability-phased-implementation-plan.md`  
> 前置依赖：Phase A Agent Loop、Phase C Task Runtime、Phase D 输出治理。  
> 本阶段目标：让 Agent 在运行时主动调用记忆工具，实现 read/write/forget 闭环，并保留质量、冲突、权限和审计边界。

---

## 1. 范围与原则

### 1.1 范围

- 新增 `memory_read`、`memory_write`、`memory_forget` 三个工具。
- 复用现有四层记忆和治理服务。
- 将 taskId、phaseId、snapshotId 写入 memory metadata。
- 新增记忆工具动作日志。

### 1.2 非目标

- 不重写现有 `KernelMemoryEngine`。
- 不改变 RAG pipeline 的 `activateMemory` 行为。
- 不允许 LLM 绕过 user/tenant scope 操作记忆。

---

## 2. 类设计与接口定义

### 2.1 新增工具类

| 类 | 职责 |
|---|---|
| `MemoryReadToolPortAdapter` | 读取短期/长期/语义记忆 |
| `MemoryWriteToolPortAdapter` | 写入记忆并执行质量和冲突校验 |
| `MemoryForgetToolPortAdapter` | 软删除指定记忆 |
| `AgentMemoryToolRegistrar` | 注册三类 memory tools |
| `AgentMemoryPolicy` | 控制哪些 layer 可读写，是否需要 HITL |
| `AgentMemoryActionRecorder` | 记录工具动作 |

### 2.2 工具参数

`memory_read`：

```json
{
  "type": "object",
  "required": ["query"],
  "properties": {
    "query": {"type": "string"},
    "layers": {
      "type": "array",
      "items": {"type": "string", "enum": ["WORKING", "SHORT", "LONG", "SEMANTIC"]}
    },
    "limit": {"type": "integer", "minimum": 1, "maximum": 20}
  }
}
```

`memory_write`：

```json
{
  "type": "object",
  "required": ["content", "layer", "reason"],
  "properties": {
    "content": {"type": "string"},
    "layer": {"type": "string", "enum": ["SHORT", "LONG", "SEMANTIC"]},
    "tags": {"type": "array", "items": {"type": "string"}},
    "reason": {"type": "string"},
    "sourceSnapshotId": {"type": "string"}
  }
}
```

`memory_forget`：

```json
{
  "type": "object",
  "required": ["memoryId", "reason"],
  "properties": {
    "memoryId": {"type": "string"},
    "layer": {"type": "string"},
    "reason": {"type": "string"}
  }
}
```

### 2.3 依赖现有服务

| 现有对象 | 用途 |
|---|---|
| `KernelMemoryEngine` | 加载和写入四层记忆 |
| `KernelMemoryManagementService` | 管理查询和删除 |
| `KernelMemoryGovernanceService` | 质量快照、冲突检测、衰减 |
| `MemoryConflictLogRepositoryPort` | 冲突记录 |

---

## 3. 数据库表结构设计

### 3.1 复用表

| 表 | 用途 |
|---|---|
| `t_short_term_memory` | 短期记忆 |
| `t_long_term_memory` | 长期记忆 |
| `t_semantic_memory` | 语义记忆 |
| `t_memory_conflict_log` | 冲突记录 |
| `t_memory_quality_snapshot` | 质量快照 |

记忆写入时，统一在现有 metadata JSON 中追加：

```json
{
  "source": "agent_tool",
  "taskId": "agt_xxx",
  "phaseId": "technical_design",
  "sourceSnapshotId": "snap_xxx",
  "writeReason": "用户确认了项目命名规范"
}
```

### 3.2 新增动作日志表

```sql
CREATE TABLE IF NOT EXISTS t_agent_memory_action (
    id VARCHAR(64) PRIMARY KEY,
    task_id VARCHAR(64),
    phase_id VARCHAR(128),
    snapshot_id VARCHAR(64),
    user_id VARCHAR(64) NOT NULL,
    tenant_id VARCHAR(64),
    action_type VARCHAR(32) NOT NULL,
    memory_layer VARCHAR(32),
    memory_id VARCHAR(64),
    content_hash VARCHAR(128),
    success SMALLINT NOT NULL,
    policy_decision VARCHAR(32) NOT NULL,
    error_message TEXT,
    create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_agent_memory_action_task
ON t_agent_memory_action (task_id, phase_id, create_time DESC);

CREATE INDEX IF NOT EXISTS idx_agent_memory_action_user
ON t_agent_memory_action (user_id, action_type, create_time DESC);

COMMENT ON TABLE t_agent_memory_action IS 'Agent 记忆工具动作日志表';
```

---

## 4. API 接口规范

Phase E 不强制新增用户态 API；工具通过 Agent Loop 调用。提供管理查询接口：

```http
GET /api/seahorse-agent/agent-tasks/{taskId}/memory-actions
```

响应：

```json
{
  "code": "0",
  "data": [
    {
      "actionType": "WRITE",
      "memoryLayer": "LONG",
      "memoryId": "mem_001",
      "success": true,
      "policyDecision": "ALLOW"
    }
  ]
}
```

---

## 5. 实现步骤

1. 新增 memory tool request/observation DTO。
2. 实现 `MemoryReadToolPortAdapter`。
3. 实现 `MemoryWriteToolPortAdapter`：
   - scope 校验。
   - PII / 敏感内容检查。
   - 质量评分。
   - 冲突检测。
   - 写入现有 memory repository。
4. 实现 `MemoryForgetToolPortAdapter`，只允许软删除当前 scope 记忆。
5. 实现 `AgentMemoryActionRecorder` 和 JDBC adapter。
6. 在 starter 中注册 memory tools。
7. 在 RequirementAnalysisSkill 中允许 `memory_read`，默认不允许自动 long-term write，除非配置开启。

---

## 6. 异常处理与边界情况

| 场景 | 处理 |
|---|---|
| LLM 请求写 LONG 但策略禁止 | 返回 failed observation：`long-term write requires confirmation` |
| memoryId 不存在 | forget 返回 failed，但不抛异常 |
| 用户越权删除他人记忆 | 返回 failed 并记录 warn |
| 内容疑似敏感 | policy decision `REQUIRE_HUMAN` |
| 冲突记忆 | 写入 conflict log，返回 warning observation |
| read 结果过多 | 按 limit 和 token budget 截断 |

---

## 7. 测试用例设计

| 测试类 | 用例 |
|---|---|
| `MemoryReadToolPortAdapterTests` | 正常读取多层记忆 |
| `MemoryReadToolPortAdapterTests` | limit 超限裁剪 |
| `MemoryWriteToolPortAdapterTests` | 写入 metadata 包含 task/phase/snapshot |
| `MemoryWriteToolPortAdapterTests` | long-term 写入被策略拦截 |
| `MemoryWriteToolPortAdapterTests` | 冲突检测产生 conflict log |
| `MemoryForgetToolPortAdapterTests` | 软删除当前用户记忆 |
| `MemoryForgetToolPortAdapterTests` | 越权删除失败 |
| `AgentMemoryActionRecorderTests` | 成功/失败动作均记录 |

---

## 8. 性能指标与监控方案

| 指标 | 目标 |
|---|---|
| `agent.memory.read.latency.p95` | 小于 300ms，不含向量语义检索 |
| `agent.memory.write.latency.p95` | 小于 500ms |
| `agent.memory.conflict.rate` | 可观测 |
| `agent.memory.policy.block.rate` | 可观测 |
| `agent.memory.cross_scope_violation` | 0 |

监控：

- tool observation 记录 read/write/forget 结果摘要。
- action log 记录 content hash，不保存敏感原文。
- 冲突率异常升高时进入治理告警。


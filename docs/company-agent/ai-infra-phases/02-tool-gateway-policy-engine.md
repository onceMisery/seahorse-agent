# Phase 2：Tool Gateway 与 Policy Engine

## 1. 阶段目标

把所有工具调用收敛到统一 Tool Gateway。任何 Agent 不允许直接调用 `ToolPort.invoke`。Tool Gateway 负责工具目录、风险分级、Agent-tool 绑定、策略决策、审批判断、参数校验、脱敏、审计和限流。

## 2. 当前问题

当前 `KernelAgentLoop` 直接通过 `ToolRegistryPort.find(toolId)` 找到工具并执行。已有 allowlist 和 timeout，但缺少：

- 工具风险等级。
- 工具 read/write/delete/external-send 分类。
- 用户/Agent/租户/资源级动态授权。
- 策略决策日志。
- 高风险工具审批中断。
- 输入输出脱敏。

## 3. 新增模型

### 3.1 ToolCatalogEntry

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `toolId` | String | 稳定 ID |
| `provider` | enum | `BUILTIN`、`MCP`、`OPENAPI`、`INTERNAL`、`REMOTE_AGENT` |
| `name` | String | 名称 |
| `description` | String | 描述 |
| `schemaJson` | String | 输入 schema |
| `outputSchemaJson` | String | 输出 schema，可空 |
| `riskLevel` | enum | `LOW`、`MEDIUM`、`HIGH`、`CRITICAL` |
| `actionType` | enum | `READ`、`WRITE`、`DELETE`、`EXECUTE`、`EXTERNAL_SEND` |
| `resourceType` | String | 如 `KNOWLEDGE_BASE`、`MEMORY`、`EMAIL` |
| `ownerTeam` | String | 工具 owner |
| `enabled` | boolean | 是否启用 |
| `requiresApproval` | boolean | 默认是否审批 |

### 3.2 AgentToolBinding

| 字段 | 说明 |
| --- | --- |
| `agentId/versionId` | 绑定到版本，避免发布后工具集漂移 |
| `toolId` | 工具 |
| `maxCallsPerRun` | 单 run 最大调用数 |
| `argumentPolicyJson` | 参数约束 |
| `createdBy/createdAt` | 记录 |

### 3.3 ToolInvocationRequest

必须包含：

- `runId`
- `stepId`
- `agentId`
- `versionId`
- `tenantId`
- `userId`
- `agentIdentityId`
- `toolId`
- `arguments`
- `resourceRefs`
- `idempotencyKey`

### 3.4 PolicyDecision

| 字段 | 说明 |
| --- | --- |
| `decisionId` | 决策 ID |
| `effect` | `ALLOW`、`DENY`、`APPROVAL_REQUIRED`、`REDACT`、`SANDBOX_REQUIRED` |
| `reasonCode` | 原因码 |
| `reasonMessage` | 说明 |
| `policyId/policyVersion` | 策略来源 |
| `approvalPolicyId` | 需要审批时填写 |
| `redactionRulesJson` | 脱敏规则 |

## 4. 端口设计

```text
ToolCatalogRepositoryPort
  save(ToolCatalogEntry entry)
  findById(String toolId)
  page(ToolCatalogQuery query)
  setEnabled(String toolId, boolean enabled)

AgentToolBindingRepositoryPort
  saveBindings(String agentId, String versionId, List<AgentToolBinding> bindings)
  listBindings(String agentId, String versionId)
  isBound(String agentId, String versionId, String toolId)

ToolGatewayPort
  ToolInvocationResult invoke(ToolInvocationRequest request)

ToolPolicyPort
  PolicyDecision decide(ToolPolicyRequest request)

ToolInvocationAuditPort
  recordRequested(...)
  recordDecision(...)
  recordCompleted(...)
```

## 5. 策略最小实现

先做内置规则，不引入 DSL：

| 规则 | 决策 |
| --- | --- |
| 工具不存在 | `DENY: TOOL_NOT_FOUND` |
| 工具 disabled | `DENY: TOOL_DISABLED` |
| Agent version 未绑定工具 | `DENY: TOOL_NOT_BOUND` |
| 超过 `maxCallsPerRun` | `DENY: TOOL_CALL_LIMIT_EXCEEDED` |
| `riskLevel=CRITICAL` | `APPROVAL_REQUIRED` |
| `actionType=DELETE` | `APPROVAL_REQUIRED` |
| `actionType=EXTERNAL_SEND` | `APPROVAL_REQUIRED` |
| 用户不具备 resource ACL | `DENY: RESOURCE_FORBIDDEN` |
| 输出匹配 secret pattern | `REDACT` |

## 6. 数据库表

```sql
CREATE TABLE sa_tool_catalog (
  tool_id VARCHAR(128) PRIMARY KEY,
  provider VARCHAR(32) NOT NULL,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1000),
  schema_json TEXT NOT NULL,
  output_schema_json TEXT,
  risk_level VARCHAR(32) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64),
  owner_team VARCHAR(128),
  enabled BOOLEAN NOT NULL,
  requires_approval BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE sa_agent_tool_binding (
  id VARCHAR(64) PRIMARY KEY,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  tool_id VARCHAR(128) NOT NULL,
  max_calls_per_run INT NOT NULL,
  argument_policy_json TEXT,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  UNIQUE(agent_id, version_id, tool_id)
);

CREATE TABLE sa_tool_invocation (
  invocation_id VARCHAR(64) PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL,
  step_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  tool_id VARCHAR(128) NOT NULL,
  idempotency_key VARCHAR(128),
  status VARCHAR(32) NOT NULL,
  policy_decision_id VARCHAR(64),
  arguments_summary TEXT,
  result_summary TEXT,
  error_message VARCHAR(1000),
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);
```

## 7. 与现有代码整合

### 7.1 改造 `KernelAgentLoop`

当前路径：

```text
KernelAgentLoop -> ToolRegistryPort.find -> ToolPort.invoke
```

目标路径：

```text
KernelAgentLoop -> ToolGatewayPort.invoke -> ToolPolicyPort.decide -> ToolRegistryPort.find -> ToolPort.invoke
```

兼容策略：

- 若未配置 `ToolGatewayPort`，starter 注入 `LocalToolGatewayPort`。
- `LocalToolGatewayPort` 内部仍使用现有 `ToolRegistryPort`。
- 所有工具调用都必须写 `sa_tool_invocation`。

### 7.2 改造 MCP 注册

`McpToolAllowlistRegistrar` 注册工具时：

1. 写 `ToolCatalogEntry(provider=MCP)`。
2. risk 默认 `MEDIUM`。
3. action 默认 `EXECUTE`，后续可配置覆盖。
4. 只 allowlist 中的 MCP 工具进入 catalog。

### 7.3 内置工具元数据

| 工具 | risk | action | approval |
| --- | --- | --- | --- |
| 日期时间 | LOW | READ | false |
| 知识库搜索 | LOW | READ | false |
| 元数据查询 | LOW | READ | false |
| 记忆读取 | MEDIUM | READ | false |
| 记忆写入 | MEDIUM | WRITE | true |
| 记忆遗忘 | HIGH | DELETE | true |

## 8. API 设计

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/tools` | 工具目录 |
| `GET` | `/api/tools/{toolId}` | 工具详情 |
| `POST` | `/api/tools/{toolId}/enable` | 启用 |
| `POST` | `/api/tools/{toolId}/disable` | 禁用 |
| `PUT` | `/api/agents/{agentId}/versions/{versionId}/tools` | 设置版本工具绑定 |
| `GET` | `/api/tool-invocations` | 调用审计 |

## 9. 测试清单

```powershell
./mvnw -pl seahorse-agent-kernel -Dtest=*ToolGateway*Test test
./mvnw -pl seahorse-agent-tests -Dtest=*AgentToolPolicy*Tests test
```

必须覆盖：

1. 未绑定工具被拒绝。
2. disabled 工具被拒绝。
3. 低风险 read 工具允许执行。
4. 记忆写入触发 approval required。
5. 工具执行失败仍记录 invocation。
6. MCP allowlist 工具写入 catalog。

## 10. 退出条件

1. Agent Runtime 中无直接 `ToolPort.invoke`。
2. 每次工具调用都有 `PolicyDecision`。
3. 每个工具都有 risk/action/resource 元数据。
4. 高风险工具不会直接执行。

## 11. 回滚策略

- 保留 `ToolRegistryPort`，但只允许 Tool Gateway 内部使用。
- 配置项可临时将所有 policy 降级为 warn-only，但必须记录告警，不建议生产开启。

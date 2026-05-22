# Phase 1：Agent Registry 与 Run Store

## 1. 阶段目标

让 Agent 成为 Seahorse 平台的一等实体，并让每次 Agent 执行都有可查询、可审计、可扩展的运行记录。Phase 1 完成后，开发者可以创建 Agent 定义、发布不可变版本、启动 run、查看 step 时间线。

## 2. 代码边界

新增核心文件：

```text
seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/definition/AgentDefinition.java
seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/definition/AgentVersion.java
seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/definition/AgentStatus.java
seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/definition/AgentRiskLevel.java
seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/runtime/AgentRun.java
seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/runtime/AgentStep.java
seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/runtime/AgentRunStatus.java
seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/runtime/AgentStepType.java
seahorse-agent-kernel/src/main/java/.../ports/inbound/agent/AgentDefinitionInboundPort.java
seahorse-agent-kernel/src/main/java/.../ports/inbound/agent/AgentRunInboundPort.java
seahorse-agent-kernel/src/main/java/.../ports/outbound/agent/AgentDefinitionRepositoryPort.java
seahorse-agent-kernel/src/main/java/.../ports/outbound/agent/AgentRunRepositoryPort.java
```

新增适配：

```text
seahorse-agent-adapter-repository-jdbc/.../JdbcAgentDefinitionRepositoryAdapter.java
seahorse-agent-adapter-repository-jdbc/.../JdbcAgentRunRepositoryAdapter.java
seahorse-agent-adapter-web/.../SeahorseAgentDefinitionController.java
seahorse-agent-adapter-web/.../SeahorseAgentRunController.java
```

## 3. 领域模型

### 3.1 AgentDefinition

必填字段：

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `agentId` | String | 非空，全局稳定 |
| `tenantId` | String | 非空，默认 `default` |
| `name` | String | 1-80 字符 |
| `description` | String | 可空，最多 500 字符 |
| `ownerUserId` | String | 非空 |
| `ownerTeam` | String | 可空 |
| `agentType` | enum | `ASSISTANT`、`WORKFLOW`、`DOMAIN`、`REMOTE` |
| `baseAgentId` | String | 派生 Agent 可填写 |
| `status` | enum | `DRAFT`、`PUBLISHED`、`DISABLED`、`ARCHIVED` |
| `riskLevel` | enum | `LOW`、`MEDIUM`、`HIGH`、`CRITICAL` |
| `latestVersionId` | String | 发布后写入 |
| `createdAt/updatedAt` | Instant | 非空 |

### 3.2 AgentVersion

版本必须不可变。发布后不能更新，只能发布新版本。

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `versionId` | String | 非空 |
| `agentId` | String | 非空 |
| `versionNo` | long | 同 agent 内递增 |
| `instructions` | String | 非空 |
| `toolSetJson` | String | JSON object |
| `modelConfigJson` | String | JSON object |
| `memoryConfigJson` | String | JSON object |
| `guardrailConfigJson` | String | JSON object |
| `publishedBy` | String | 非空 |
| `publishedAt` | Instant | 非空 |
| `changeSummary` | String | 非空 |

### 3.3 AgentRun

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `runId` | String | 非空 |
| `agentId/versionId` | String | 可为空，兼容 legacy chat agent |
| `tenantId/userId/conversationId` | String | 非空 |
| `triggerType` | enum | `CHAT`、`API`、`SCHEDULE`、`EVENT`、`A2A` |
| `inputSummary` | String | 不存完整敏感输入 |
| `status` | enum | `CREATED`、`RUNNING`、`WAITING_APPROVAL`、`SUCCEEDED`、`FAILED`、`CANCELLED` |
| `traceId` | String | 与 RagTrace 关联 |
| `tokenInput/tokenOutput` | long | 默认 0 |
| `costTotal` | BigDecimal | 默认 0 |
| `startedAt/finishedAt` | Instant | finished 可空 |

### 3.4 AgentStep

| 字段 | 类型 | 约束 |
| --- | --- | --- |
| `stepId` | String | 非空 |
| `runId` | String | 非空 |
| `stepNo` | int | 从 1 开始 |
| `stepType` | enum | `MODEL_TURN`、`TOOL_CALL`、`APPROVAL`、`HANDOFF`、`CHECKPOINT` |
| `status` | enum | `RUNNING`、`SUCCEEDED`、`FAILED`、`SKIPPED` |
| `inputJson/outputJson` | String | 摘要 JSON |
| `errorCode/errorMessage` | String | 可空 |
| `startedAt/finishedAt` | Instant | finished 可空 |

## 4. 数据库表

### 4.1 `sa_agent_definition`

```sql
CREATE TABLE sa_agent_definition (
  agent_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  name VARCHAR(80) NOT NULL,
  description VARCHAR(500),
  owner_user_id VARCHAR(64) NOT NULL,
  owner_team VARCHAR(128),
  agent_type VARCHAR(32) NOT NULL,
  base_agent_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  latest_version_id VARCHAR(64),
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_sa_agent_definition_tenant_status ON sa_agent_definition(tenant_id, status);
```

### 4.2 `sa_agent_version`

```sql
CREATE TABLE sa_agent_version (
  version_id VARCHAR(64) PRIMARY KEY,
  agent_id VARCHAR(64) NOT NULL,
  version_no BIGINT NOT NULL,
  instructions TEXT NOT NULL,
  tool_set_json TEXT NOT NULL,
  model_config_json TEXT NOT NULL,
  memory_config_json TEXT NOT NULL,
  guardrail_config_json TEXT NOT NULL,
  published_by VARCHAR(64) NOT NULL,
  published_at TIMESTAMP NOT NULL,
  change_summary VARCHAR(500) NOT NULL,
  UNIQUE(agent_id, version_no)
);
CREATE INDEX idx_sa_agent_version_agent ON sa_agent_version(agent_id, version_no);
```

### 4.3 `sa_agent_run`

```sql
CREATE TABLE sa_agent_run (
  run_id VARCHAR(64) PRIMARY KEY,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  conversation_id VARCHAR(64),
  trigger_type VARCHAR(32) NOT NULL,
  input_summary VARCHAR(1000),
  status VARCHAR(32) NOT NULL,
  trace_id VARCHAR(64),
  token_input BIGINT NOT NULL DEFAULT 0,
  token_output BIGINT NOT NULL DEFAULT 0,
  cost_total DECIMAL(18,6) NOT NULL DEFAULT 0,
  error_code VARCHAR(128),
  error_message VARCHAR(1000),
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);
CREATE INDEX idx_sa_agent_run_agent_status ON sa_agent_run(agent_id, status, started_at);
CREATE INDEX idx_sa_agent_run_user ON sa_agent_run(tenant_id, user_id, started_at);
```

### 4.4 `sa_agent_step`

```sql
CREATE TABLE sa_agent_step (
  step_id VARCHAR(64) PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL,
  step_no INT NOT NULL,
  step_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  input_json TEXT,
  output_json TEXT,
  error_code VARCHAR(128),
  error_message VARCHAR(1000),
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP,
  UNIQUE(run_id, step_no)
);
CREATE INDEX idx_sa_agent_step_run ON sa_agent_step(run_id, step_no);
```

## 5. API 设计

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/agents` | 创建 draft |
| `GET` | `/api/agents` | 分页查询 |
| `GET` | `/api/agents/{agentId}` | Agent 详情 |
| `PUT` | `/api/agents/{agentId}/draft` | 更新 draft |
| `POST` | `/api/agents/{agentId}/publish` | 发布版本 |
| `POST` | `/api/agents/{agentId}/disable` | 禁用 |
| `POST` | `/api/agents/{agentId}/runs` | 启动 run |
| `GET` | `/api/agent-runs/{runId}` | run 详情 |
| `GET` | `/api/agent-runs/{runId}/steps` | step 列表 |
| `POST` | `/api/agent-runs/{runId}/cancel` | 取消 run |

## 6. 任务切片

### Task 1.1：写领域模型和枚举

验证：

```powershell
./mvnw -pl seahorse-agent-kernel -am test -DskipTests=false
```

### Task 1.2：写 Repository Port

要求：

- `AgentDefinitionRepositoryPort` 必须支持 create、update、find、page、saveVersion、latestVersion。
- `AgentRunRepositoryPort` 必须支持 createRun、updateRunStatus、appendStep、finishStep、listSteps。

### Task 1.3：写 JDBC adapter 和 mapper

要求：

- 所有 insert/update 都使用显式字段。
- JSON 字段先按 String 处理，不在 Phase 1 引入复杂 JSON 类型。
- `version_no` 获取必须在事务内完成。

### Task 1.4：写应用服务

服务：

- `KernelAgentDefinitionService`
- `KernelAgentRunService`

规则：

- Draft 可改，Published version 不可改。
- Disabled agent 不允许启动新 run。
- 启动 run 时必须绑定 versionId；legacy chat agent 可使用 `agentId=legacy-react-agent`。

### Task 1.5：接入 `KernelChatInboundService`

改造策略：

- 保留原 `ChatMode.RAG`。
- `ChatMode.AGENT` 创建 `AgentRun`。
- `KernelAgentLoop` 每轮模型 turn 和工具调用写 `AgentStep`。
- 若 repository 不存在，允许 fallback 到 noop port，兼容当前启动。

### Task 1.6：写 Web Controller

要求：

- 复用当前 `Seahorse*Controller` 风格。
- 返回结构保持 `Map<String,Object>` 或现有统一响应风格。
- 管理 API 只允许 admin。

## 7. 测试清单

单元测试：

```powershell
./mvnw -pl seahorse-agent-kernel -Dtest=*AgentDefinition*Test test
./mvnw -pl seahorse-agent-kernel -Dtest=*AgentRun*Test test
```

集成测试：

```powershell
./mvnw -pl seahorse-agent-adapter-repository-jdbc -Dtest=*Agent*Repository*Test test
./mvnw -pl seahorse-agent-tests -Dtest=*AgentRun*Tests test
```

必须覆盖：

1. 创建 draft。
2. 发布版本后不可修改。
3. 禁用 Agent 后不能启动 run。
4. run 成功后状态为 `SUCCEEDED`。
5. 模型 turn 和工具调用写 step。
6. cancel 幂等。

## 8. 退出条件

1. `/api/agents` 能管理 Agent。
2. `/api/agent-runs/{runId}` 能查询 run。
3. 所有 Agent mode 请求都有 runId。
4. `KernelAgentLoop` 不再是唯一运行事实来源。

## 9. 回滚策略

- 保留 `ToolRegistryPort.empty()` 和 noop `AgentRunRepositoryPort`。
- 若新 run store 出现问题，可通过配置让 `ChatMode.AGENT` 回退到旧内存循环，但必须记录告警。

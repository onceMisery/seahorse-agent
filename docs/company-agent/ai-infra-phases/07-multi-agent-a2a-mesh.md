# Phase 7：Multi-Agent / A2A / Agent Mesh

## 1. 阶段目标

支持本地和远程业务 Agent 协作，并让协作过程可授权、可观测、可限流、可熔断、可审计。Phase 7 不追求“多个 Agent 自由聊天”，而是建立受治理的 handoff 和 agent-as-tool 协议。

## 2. 本地 Multi-Agent 模式

| 模式 | 说明 | 优先级 |
| --- | --- | --- |
| Agent-as-Tool | Agent B 作为 Agent A 的工具 | P0 |
| Supervisor | supervisor 分派任务给多个 sub-agent | P1 |
| Workflow Team | 固定 DAG 中多个 Agent 协作 | P1 |
| Debate | 多 Agent 互评 | P3，不建议早做 |

## 3. Handoff 模型

### 3.1 AgentHandoffRequest

| 字段 | 说明 |
| --- | --- |
| `handoffId` | ID |
| `parentRunId` | 父 run |
| `sourceAgentId` | 发起 Agent |
| `targetAgentId` | 接收 Agent |
| `handoffReason` | 原因 |
| `inputJson` | 裁剪后输入 |
| `contextPolicyJson` | 上下文传递策略 |
| `requiredCapabilitiesJson` | 能力要求 |
| `status` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED` |

### 3.2 上下文传递规则

- 默认只传摘要，不传完整 message history。
- 不传 private memory，除非目标 Agent 有 delegated access。
- 不传 secret context item。
- handoff 前重新执行 policy check。

## 4. A2A 支持

### 4.1 A2A Client

能力：

1. 注册远程 Agent Card。
2. 拉取 capability。
3. 创建远程 task。
4. 接收 streaming/event。
5. 远程结果写入本地 AgentStep。

### 4.2 A2A Server

能力：

1. Seahorse Agent 发布为远程 Agent。
2. 暴露 Agent Card。
3. 接收外部 task 后创建本地 run。
4. 外部身份映射到 tenant policy。

## 5. Agent Mesh 控制面

| 能力 | 实现 |
| --- | --- |
| discovery | Agent Catalog + Remote Agent Card |
| routing | capability/risk/health/cost |
| policy | source->target 授权 |
| quota | agent-to-agent 调用限额 |
| tracing | parentRunId/childRunId |
| circuit breaker | 下游失败率熔断 |
| version compatibility | remote agent version |

## 6. 数据库表

```sql
CREATE TABLE sa_agent_handoff (
  handoff_id VARCHAR(64) PRIMARY KEY,
  parent_run_id VARCHAR(64) NOT NULL,
  child_run_id VARCHAR(64),
  source_agent_id VARCHAR(64) NOT NULL,
  target_agent_id VARCHAR(64) NOT NULL,
  handoff_reason VARCHAR(500) NOT NULL,
  input_json TEXT NOT NULL,
  context_policy_json TEXT,
  status VARCHAR(32) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);

CREATE TABLE sa_remote_agent (
  remote_agent_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  endpoint_url VARCHAR(512) NOT NULL,
  agent_card_json TEXT NOT NULL,
  auth_policy_id VARCHAR(64),
  enabled BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

## 7. API

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/agent-handoffs` | 创建 handoff |
| `GET` | `/api/agent-runs/{runId}/handoffs` | run 下 handoff |
| `POST` | `/api/remote-agents` | 注册远程 Agent |
| `GET` | `/api/remote-agents` | 列表 |
| `POST` | `/api/remote-agents/{id}/health-check` | 健康检查 |

## 8. 冲突仲裁

规则优先级：

1. Compliance Agent > 普通业务 Agent。
2. Security policy > cost policy > efficiency policy。
3. Human approval > Agent decision。
4. 高可信数据源 > 低可信数据源。
5. 无法判断时进入审批。

## 9. 测试清单

```powershell
./mvnw -pl seahorse-agent-tests -Dtest=*AgentHandoff*Tests test
./mvnw -pl seahorse-agent-tests -Dtest=*RemoteAgent*Tests test
```

必须覆盖：

1. Agent A handoff 到 Agent B。
2. 目标 Agent 权限重新校验。
3. private context 不被传递。
4. 远程 Agent 失败写入失败 step。
5. 下游 Agent 熔断。
6. parent/child trace 串联。

## 10. 退出条件

1. 本地 Agent-as-Tool 可用。
2. 远程 Agent 有注册、鉴权和审计。
3. 多 Agent 调用可限流、可熔断。

## 11. 风险控制

- 不允许循环 handoff 超过配置深度。
- 不允许未授权上下文跨 Agent 传播。
- 远程 Agent 默认 disabled，需管理员启用。

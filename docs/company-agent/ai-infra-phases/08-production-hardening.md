# Phase 8：企业生产化硬化

## 1. 阶段目标

让 Seahorse AI Infra 达到可企业试点、可内控、可运维、可评估的标准。Phase 8 是持续阶段，覆盖评估、红队、安全、审计、成本、SRE、发布和企业准入。

## 2. Evaluation 平台

### 2.1 评估类型

| 类型 | 指标 |
| --- | --- |
| RAG Eval | recall、precision、citation accuracy、faithfulness |
| Agent Trajectory Eval | 工具选择正确率、步骤数、任务成功率 |
| Tool Eval | 参数正确率、幂等性、失败恢复 |
| Safety Eval | prompt injection、越权、敏感信息泄露 |
| HITL Eval | 审批命中率、误拦截率、审批耗时 |
| Cost Eval | token/run、cost/run、cache hit |

### 2.2 表结构

```sql
CREATE TABLE sa_eval_dataset (
  dataset_id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  eval_type VARCHAR(32) NOT NULL,
  owner_team VARCHAR(128),
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE sa_eval_case (
  case_id VARCHAR(64) PRIMARY KEY,
  dataset_id VARCHAR(64) NOT NULL,
  input_json TEXT NOT NULL,
  expected_json TEXT NOT NULL,
  tags_json TEXT,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE sa_eval_run (
  eval_run_id VARCHAR(64) PRIMARY KEY,
  dataset_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  score DOUBLE,
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);

CREATE TABLE sa_eval_result (
  result_id VARCHAR(64) PRIMARY KEY,
  eval_run_id VARCHAR(64) NOT NULL,
  case_id VARCHAR(64) NOT NULL,
  passed BOOLEAN NOT NULL,
  score DOUBLE,
  detail_json TEXT,
  created_at TIMESTAMP NOT NULL
);
```

## 3. Audit Ledger

### 3.1 审计事件类型

| 事件 | 必填字段 |
| --- | --- |
| `AGENT_PUBLISHED` | agentId、versionId、operator |
| `RUN_STARTED` | runId、agentId、userId |
| `MODEL_CALLED` | runId、model、token、cost |
| `TOOL_POLICY_DECIDED` | runId、toolId、decision |
| `TOOL_INVOKED` | runId、toolId、status |
| `APPROVAL_DECIDED` | approvalId、decision、operator |
| `CONTEXT_ACCESSED` | contextPackId、resource、decision |
| `SECRET_USED` | secretRef、toolId、agentId |
| `REMOTE_AGENT_CALLED` | remoteAgentId、parentRunId |

### 3.2 表结构

```sql
CREATE TABLE sa_audit_event (
  audit_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  event_type VARCHAR(64) NOT NULL,
  actor_type VARCHAR(32) NOT NULL,
  actor_id VARCHAR(64) NOT NULL,
  run_id VARCHAR(64),
  agent_id VARCHAR(64),
  resource_type VARCHAR(64),
  resource_id VARCHAR(128),
  event_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_sa_audit_event_run ON sa_audit_event(run_id, created_at);
CREATE INDEX idx_sa_audit_event_resource ON sa_audit_event(resource_type, resource_id, created_at);
```

## 4. 成本与配额

### 4.1 配额维度

| 维度 | 限制 |
| --- | --- |
| tenant | daily/monthly cost |
| agent | runs/hour、tokens/day |
| user | cost/day |
| tool | calls/minute |
| model | tokens/minute |
| run | maxSteps、maxCost、maxDuration |

### 4.2 超预算行为

| 场景 | 行为 |
| --- | --- |
| run cost 超限 | 暂停 run，进入 approval |
| tenant quota 超限 | 拒绝新 run |
| expensive model 超限 | 降级模型或拒绝 |
| tool call 超限 | policy deny |

## 5. SRE 面板

必须展示：

- run backlog。
- approval backlog。
- worker health。
- model latency/error。
- tool latency/error。
- MCP server health。
- cost burn rate。
- eval pass rate。
- policy deny top list。

## 6. 发布流程

Agent 发布必须经过：

1. 配置校验。
2. 权限校验。
3. 工具风险校验。
4. eval run。
5. 安全红队 case。
6. owner approval。
7. canary。
8. full publish。

## 7. 企业试点准入

业务 Agent 进入试点必须满足：

1. 有 owner 和 fallback owner。
2. 有 published version。
3. 有工具风险清单。
4. 有资源 ACL。
5. 有 eval dataset。
6. 高风险动作有 HITL。
7. 有成本 quota。
8. 有 audit 查询。
9. 有 disable 开关。
10. 有回滚版本。

## 8. API

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/eval-runs` | 启动评估 |
| `GET` | `/api/eval-runs/{id}` | 评估详情 |
| `GET` | `/api/audit-events` | 审计查询 |
| `GET` | `/api/cost/usage` | 成本使用 |
| `PUT` | `/api/quotas/{scope}` | 配额 |
| `GET` | `/api/sre/health` | SRE 健康 |

## 9. 测试清单

```powershell
./mvnw -pl seahorse-agent-tests -Dtest=*Evaluation*Tests test
./mvnw -pl seahorse-agent-tests -Dtest=*AuditLedger*Tests test
./mvnw -pl seahorse-agent-tests -Dtest=*Quota*Tests test
cd frontend; npm run build
```

必须覆盖：

1. 发布前 eval 不通过则阻止发布。
2. audit event 可按 runId 查询。
3. token/cost 累加。
4. quota 超限阻止新 run。
5. canary version 可回滚。
6. SRE health 聚合依赖状态。

## 10. 退出条件

1. 企业试点 Agent 有完整准入记录。
2. 所有关键动作有 audit event。
3. 评估结果参与发布门禁。
4. 成本和配额可视化。
5. 运维可以停用高风险 Agent 或工具。

## 11. 风险控制

- audit event 不能记录明文 secret。
- eval case 不应包含真实敏感数据。
- quota 默认保守。
- canary 失败自动停止扩量。

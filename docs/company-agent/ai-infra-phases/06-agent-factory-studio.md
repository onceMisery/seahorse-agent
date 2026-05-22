# Phase 6：Agent Factory 与 Agent Studio

## 1. 阶段目标

让业务团队可以基于 Seahorse 创建、派生、测试、发布和回滚业务 Agent。Phase 6 的重点是把 Phase 1-5 的基础能力包装成可运营的 Agent Factory。

## 2. Agent 模板

内置模板：

| 模板 ID | 说明 | 默认工具 | 风险 |
| --- | --- | --- | --- |
| `knowledge-assistant` | 企业知识问答 | search, memory-read | LOW |
| `knowledge-curator` | 知识库治理 | search, metadata-review | MEDIUM |
| `workflow-assistant` | 多步骤流程助手 | search, approval | MEDIUM |
| `data-analyst` | 数据解释 | query, chart | MEDIUM |
| `tool-operator` | 工具执行 Agent | custom tools | HIGH |
| `compliance-reviewer` | 合规检查 | policy, audit | HIGH |
| `remote-agent-wrapper` | 远程 Agent 包装 | A2A/MCP | MEDIUM |

## 3. 派生规则

业务 Agent 派生时只能收窄权限，不能扩大基础模板的安全边界。

| 配置 | 规则 |
| --- | --- |
| tools | derived tools 必须是 base tools 子集，管理员可例外审批 |
| memory | 可缩小 user/tenant 范围，不可扩大 |
| model | 可降级便宜模型，不可提高高风险模型权限 |
| approval | 可更严格，不可更宽松 |
| quota | derived quota <= base quota |
| guardrails | derived guardrails 包含 base guardrails |

## 4. 发布门禁

发布 AgentVersion 前必须检查：

1. instructions 非空。
2. 工具都存在且 enabled。
3. 高风险工具有 approval policy。
4. 资源 ACL 可解析。
5. eval set 通过。
6. 成本 quota 已配置。
7. owner 和 fallback owner 存在。
8. changeSummary 非空。

## 5. Agent Studio 页面

### 5.1 页面结构

| 页面 | 路由 | 说明 |
| --- | --- | --- |
| Agent Catalog | `/admin/ai-infra/agents` | Agent 列表、状态、owner、风险 |
| Agent Builder | `/admin/ai-infra/agents/new` | 从模板创建 |
| Agent Detail | `/admin/ai-infra/agents/:agentId` | 配置、版本、运行、评估 |
| Version Diff | `/admin/ai-infra/agents/:agentId/versions/:versionId` | 版本快照 |
| Run Timeline | `/admin/ai-infra/runs/:runId` | step、tool、approval、cost |
| Approval Inbox | `/admin/ai-infra/approvals` | 审批 |
| Tool Catalog | `/admin/ai-infra/tools` | 工具目录 |

### 5.2 表单字段

创建 Agent：

- 名称。
- 描述。
- 模板。
- owner。
- 风险等级。
- 默认知识源。
- 工具集。
- 模型策略。
- 记忆策略。
- 审批策略。
- 成本 quota。

## 6. API

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-templates` | 模板 |
| `POST` | `/api/agents/from-template` | 从模板创建 |
| `POST` | `/api/agents/{agentId}/derive` | 派生 |
| `POST` | `/api/agents/{agentId}/validate` | 发布前校验 |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/rollback` | 回滚 |
| `POST` | `/api/agents/{agentId}/test-runs` | 测试运行 |

## 7. 数据库表

```sql
CREATE TABLE sa_agent_template (
  template_id VARCHAR(64) PRIMARY KEY,
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1000),
  default_config_json TEXT NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  enabled BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE sa_agent_publish_check (
  check_id VARCHAR(64) PRIMARY KEY,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  result_json TEXT NOT NULL,
  checked_by VARCHAR(64) NOT NULL,
  checked_at TIMESTAMP NOT NULL
);
```

## 8. 测试清单

```powershell
./mvnw -pl seahorse-agent-tests -Dtest=*AgentFactory*Tests test
cd frontend; npm run build
```

必须覆盖：

1. 从模板创建 draft。
2. 派生 Agent 不能扩大工具权限。
3. 高风险工具无审批策略时发布校验失败。
4. 发布版本后不可变。
5. 回滚后新 run 使用回滚版本。
6. 前端 Agent Studio 页面构建通过。

## 9. 退出条件

1. 业务团队能不写代码创建低风险 Agent。
2. Agent 发布有门禁。
3. Agent Catalog 能展示可调用 Agent。
4. 版本可回滚。

## 10. 风险控制

- UI 不允许编辑已发布 version。
- 派生默认最小权限。
- 试运行默认使用 mock/dry-run 工具。

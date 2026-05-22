# Phase 4：Context DB 与资源 ACL

## 1. 阶段目标

把 Seahorse 已有 RAG、Memory、Tool result 和用户输入统一成 ContextPack。业务 Agent 只能通过 ContextPack 获取上下文。每个上下文项必须有来源、ACL 决策、敏感级别、置信度、引用和 token 预算。

## 2. 新增模型

### 2.1 ContextPack

| 字段 | 说明 |
| --- | --- |
| `contextPackId` | 上下文包 ID |
| `runId/agentId/versionId` | 关联 |
| `tenantId/userId` | 归属 |
| `taskGoal` | 当前目标 |
| `budgetTokens` | token 预算 |
| `itemCount` | 条目数 |
| `createdAt` | 时间 |

### 2.2 ContextItem

| 字段 | 说明 |
| --- | --- |
| `itemId` | ID |
| `contextPackId` | 包 ID |
| `sourceType` | `RAG_CHUNK`、`MEMORY`、`TOOL_RESULT`、`USER_INPUT`、`SYSTEM_STATE` |
| `sourceId` | 来源 ID |
| `content` | 内容 |
| `summary` | 摘要 |
| `score` | 相关性 |
| `confidence` | 置信度 |
| `sensitivity` | `PUBLIC`、`INTERNAL`、`CONFIDENTIAL`、`SECRET` |
| `aclDecisionId` | ACL 决策 |
| `citationJson` | 引用 |
| `expiresAt` | 过期时间 |

### 2.3 ResourceRef

统一资源引用：

| 字段 | 示例 |
| --- | --- |
| `resourceType` | `DOCUMENT` |
| `resourceId` | `doc_123` |
| `tenantId` | `default` |
| `ownerUserId` | `u_1` |
| `attributes` | JSON |

### 2.4 AccessDecision

| 字段 | 说明 |
| --- | --- |
| `decisionId` | ID |
| `effect` | `ALLOW`、`DENY`、`MASK` |
| `subjectType` | `USER`、`AGENT`、`USER_DELEGATED_AGENT` |
| `subjectId` | ID |
| `action` | `READ`、`WRITE`、`DELETE`、`EXECUTE` |
| `resourceType/resourceId` | 资源 |
| `reasonCode` | 原因 |

## 3. 数据库表

```sql
CREATE TABLE sa_context_pack (
  context_pack_id VARCHAR(64) PRIMARY KEY,
  run_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  version_id VARCHAR(64),
  tenant_id VARCHAR(64) NOT NULL,
  user_id VARCHAR(64) NOT NULL,
  task_goal VARCHAR(1000) NOT NULL,
  budget_tokens INT NOT NULL,
  item_count INT NOT NULL,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE sa_context_item (
  item_id VARCHAR(64) PRIMARY KEY,
  context_pack_id VARCHAR(64) NOT NULL,
  source_type VARCHAR(32) NOT NULL,
  source_id VARCHAR(128) NOT NULL,
  content TEXT NOT NULL,
  summary VARCHAR(1000),
  score DOUBLE,
  confidence DOUBLE,
  sensitivity VARCHAR(32) NOT NULL,
  acl_decision_id VARCHAR(64),
  citation_json TEXT,
  expires_at TIMESTAMP,
  created_at TIMESTAMP NOT NULL
);

CREATE TABLE sa_access_decision_log (
  decision_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_id VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(128) NOT NULL,
  effect VARCHAR(32) NOT NULL,
  reason_code VARCHAR(128),
  created_at TIMESTAMP NOT NULL
);
```

## 4. 端口

```text
ContextPackBuilderPort
  build(ContextBuildRequest request) -> ContextPack

ContextPackRepositoryPort
  save(ContextPack pack)
  findById(String contextPackId)
  listItems(String contextPackId)

ResourceAccessPolicyPort
  decide(ResourceAccessRequest request) -> AccessDecision

ContextBudgetPort
  allocate(ContextBudgetRequest request) -> ContextBudgetPlan
```

## 5. 与现有 RAG/Memory 整合

### 5.1 RAG

改造点：

- `KernelRetrievalEngine` 输出 `RetrievedChunk` 后进入 ContextPackBuilder。
- `MetadataGuardPostProcessorFeature` 扩展为 ACL guard。
- 每个 chunk 写入 citation。
- 无 ACL decision 的 chunk 不允许进入 prompt。

### 5.2 Memory

改造点：

- `HybridMemoryRecallPipeline` 输出的 memory item 进入 ContextPack。
- `MemoryItem` 增加 provenance 和 sensitivity 映射。
- 私有 memory 默认只允许 `USER_DELEGATED_AGENT` 读取。
- 记忆删除后 ContextPack 不能再引用。

### 5.3 Prompt 构造

目标：

```text
AgentRun -> ContextPackBuilder -> ContextPack -> ContextWeaver -> Model Prompt
```

`DefaultContextWeaver` 不再直接只处理 `MemoryContext`，应新增支持 `ContextPack` 的 formatter。

## 6. 资源 ACL 最小实现

规则：

| 场景 | 规则 |
| --- | --- |
| admin | 可读所有知识库管理资源 |
| 普通用户 | 只读公开知识库和自己有授权的知识库 |
| 用户私有记忆 | 只允许本人和 delegated agent |
| 工具结果 | 继承工具调用时的 subject 和 resource |
| secret sensitivity | 不进入模型，除非 policy 显式 allow |

## 7. API 设计

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/context-packs/{contextPackId}` | ContextPack |
| `GET` | `/api/context-packs/{contextPackId}/items` | 上下文项 |
| `GET` | `/api/access-decisions` | ACL 决策日志 |
| `POST` | `/api/resources/{type}/{id}/acl` | 设置资源 ACL |

## 8. 测试清单

```powershell
./mvnw -pl seahorse-agent-tests -Dtest=*ContextPack*Tests test
./mvnw -pl seahorse-agent-tests -Dtest=*ResourceAccess*Tests test
```

必须覆盖：

1. 无权限文档不进入 ContextPack。
2. 用户 A 无法读取用户 B 私有 memory。
3. ContextPack 每项都有 source/citation/aclDecisionId。
4. token budget 会截断低分项。
5. secret sensitivity 项默认不进入 prompt。

## 9. 退出条件

1. Agent prompt 不再直接拼接裸 RAG/Memory。
2. 每个上下文项可解释来源和权限。
3. 审计能回答“Agent 为什么看到这段内容”。

## 10. 风险控制

- 不一次性重写现有 RAG pipeline，先做 ContextPack adapter。
- 保留旧 `MemoryContext` 兼容路径，但新 Agent Runtime 默认走 ContextPack。
- ACL 默认 deny，不确定时不进入上下文。

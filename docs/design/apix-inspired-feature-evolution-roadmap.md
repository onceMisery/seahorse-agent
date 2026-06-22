# APIX Inspired Feature Evolution Roadmap

> 本文承接 `APIX借鉴特性落地设计方案.md` 的已落地能力，目标是把消息分支、MCP stdio、角色卡从单点功能继续演进为 Seahorse Agent 的可复现、可治理、可评测运行体系。

## 1. 背景与结论

当前已经落地的三项能力分别解决了三个关键缺口：

- 消息树和分支切换：让同一会话支持编辑、重试、fork、切换分支。
- MCP stdio：让 Seahorse 可以接入本地 MCP 生态，补足工具扩展能力。
- 角色卡运行时切换：让用户可以在 AgentVersion 之外叠加轻量人格和行为指令。

这些能力不应长期作为三条孤立功能线维护。推荐演进方向是：

```text
消息分支 + 角色卡 + 工具集 + MCP 工具 + A2A 远端 Agent + 模型配置 + 记忆范围 + 安全策略 + 执行引擎
  -> RunContextSnapshot
  -> Run Profile
  -> Agent 实验、评测、发布门禁、组织级治理
```

近中期重点不是继续堆 UI 按钮，而是建立统一的运行上下文模型。远期目标是形成一个可追踪、可复现、可比较、可发布治理的 Agent 工作台。

## 2. 设计原则

1. 先闭环，后平台化。
   近期先把现有能力补齐为用户可用闭环，不直接追求完整实验平台。

2. 先快照，后画像。
   近期优先记录 `RunContextSnapshot`，确保每次运行可追溯。中期再抽象可复用的 `Run Profile`。

3. 保留六边形边界。
   kernel 只表达运行上下文、快照、画像、分支等领域模型；MCP、JDBC、Web、前端均保持 adapter 边界。

4. 安全能力默认收紧。
   MCP stdio 和高权限角色卡都属于高风险能力，必须逐步引入 allowlist、审计、审批、隔离和脱敏。

5. 新增 Java 样板代码优先使用 Lombok。
   新增服务、配置、简单 DTO 在不破坏项目风格的前提下优先使用 `@RequiredArgsConstructor`、`@Data`、`@Getter`、`@Builder` 等简化。

6. AgentScope 作为可插拔执行面。
   RunContextSnapshot 和 Run Profile 必须记录 `executorEngine`，Seahorse 继续拥有产品控制面、工具治理、审批、审计和 SSE/API 契约；AgentScope 只通过 `ReActExecutorPort` 承担 `engine=agentscope` 下的执行、A2A、Nacos 和 Studio/Trace 适配。

## 3. 近端落地方案：2 到 3 个迭代

### 3.1 目标

把已落地能力从“接口可用”推进到“产品闭环可用”：

- 用户能在 UI 中稳定编辑消息、重新生成、切换分支。
- 用户能创建、启用、临时选择角色卡。
- 管理员能看到 MCP stdio server 的工具发现状态和诊断信息。
- 每次 chat 或 Agent run 都能记录当时的运行上下文快照。

### 3.2 P0：RunContextSnapshot

这是近期最重要的地基。每次运行开始时记录当时使用的上下文，而不是只依赖当前表里的可变配置。

建议新增表：

```sql
CREATE TABLE IF NOT EXISTS t_run_context_snapshot (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    run_id VARCHAR(64) NOT NULL,
    conversation_id BIGINT,
    branch_leaf_message_id BIGINT,
    role_card_id BIGINT,
    run_profile_id BIGINT,
    executor_engine VARCHAR(32) NOT NULL DEFAULT 'kernel',
    executor_config_json TEXT,
    trace_context_json TEXT,
    snapshot_json TEXT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE t_run_context_snapshot IS '运行上下文快照表，记录每次 Chat 或 Agent Run 启动时实际生效的角色、工具、模型、记忆、安全策略和执行引擎，用于历史复现、审计和评测';
COMMENT ON COLUMN t_run_context_snapshot.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN t_run_context_snapshot.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN t_run_context_snapshot.run_id IS '运行 ID，对应 Chat 请求或 Agent Run 的稳定标识';
COMMENT ON COLUMN t_run_context_snapshot.conversation_id IS '会话 ID，记录本次运行所属会话，可为空';
COMMENT ON COLUMN t_run_context_snapshot.branch_leaf_message_id IS '运行时选中的消息分支叶子节点 ID，用于复现当时的对话路径';
COMMENT ON COLUMN t_run_context_snapshot.role_card_id IS '运行时选中的角色卡 ID，可为空';
COMMENT ON COLUMN t_run_context_snapshot.run_profile_id IS '运行时选中的运行画像 ID，可为空';
COMMENT ON COLUMN t_run_context_snapshot.executor_engine IS '执行引擎标识，例如 kernel 或 agentscope';
COMMENT ON COLUMN t_run_context_snapshot.executor_config_json IS '执行引擎配置快照 JSON，例如 AgentScope 的 Nacos、A2A、Studio 配置摘要';
COMMENT ON COLUMN t_run_context_snapshot.trace_context_json IS '链路追踪上下文 JSON，例如 traceId、spanId、Studio traceId';
COMMENT ON COLUMN t_run_context_snapshot.snapshot_json IS '运行上下文快照 JSON，保存角色卡副本、工具集、MCP 工具、模型配置、记忆范围和安全策略';
COMMENT ON COLUMN t_run_context_snapshot.create_time IS '创建时间';
COMMENT ON COLUMN t_run_context_snapshot.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE INDEX IF NOT EXISTS idx_run_context_snapshot_run
    ON t_run_context_snapshot (tenant_id, run_id);
```

快照内容建议包含：

```json
{
  "conversationId": "326827222507700224",
  "branchLeafMessageId": "326827666353143808",
  "runProfileId": "326827700000000099",
  "executorEngine": "agentscope",
  "roleCard": {
    "id": "326827218426642432",
    "name": "E2E Role",
    "definition": "Answer as an E2E verification role.",
    "higherPerm": false
  },
  "toolIds": ["echo", "get_current_datetime"],
  "mcpToolIds": ["echo"],
  "a2aAgentIds": ["seahorse-researcher"],
  "modelConfig": {},
  "memoryScope": {},
  "guardrailConfig": {},
  "agentScope": {
    "version": "2.0.0-RC3",
    "nacosNamespace": "public",
    "nacosGroup": "DEFAULT_GROUP",
    "studioTraceId": "trace-001"
  },
  "createdAt": "2026-06-21T00:00:00Z"
}
```

端口建议：

- `RunContextSnapshotRepositoryPort`
  - `save(RunContextSnapshot snapshot)`
  - `findByRunId(String runId)`
- `RunContextSnapshotInboundPort`
  - `findByRunId(String runId)`

接入点：

- 普通 chat：在创建流式 chat 上下文时生成快照。
- Agent loop：在创建 `AgentLoopRequest` 或 run store 前生成快照。
- 工具调用：后续工具审计可以引用 `runId` 反查快照。

验收：

- 发起一次普通 chat 后可以按 runId 查到快照。
- 选择角色卡后，快照保存角色卡内容副本。
- 启用 MCP 工具后，快照保存 MCP tool id。
- 修改角色卡后，历史 run 快照不变。

### 3.3 P0：消息树产品化

当前消息树已具备后端基础，近端需要补齐 UI 和会话视图语义。

后端增强：

- 保留 `t_message.parent_id`、`active`、`branch_root_id`、`sibling_seq`。
- 新增会话视图游标，避免未来多窗口共享一个全局 active 路径。

建议新增表：

```sql
CREATE TABLE IF NOT EXISTS t_conversation_branch_cursor (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    conversation_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    leaf_message_id BIGINT NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE t_conversation_branch_cursor IS '会话分支游标表，记录用户在某个会话中最后选中的消息分支叶子节点，避免多个窗口共享全局 active 路径';
COMMENT ON COLUMN t_conversation_branch_cursor.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN t_conversation_branch_cursor.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN t_conversation_branch_cursor.conversation_id IS '会话 ID';
COMMENT ON COLUMN t_conversation_branch_cursor.user_id IS '用户 ID';
COMMENT ON COLUMN t_conversation_branch_cursor.leaf_message_id IS '当前视图选中的分支叶子消息 ID';
COMMENT ON COLUMN t_conversation_branch_cursor.create_time IS '创建时间';
COMMENT ON COLUMN t_conversation_branch_cursor.update_time IS '更新时间';
COMMENT ON COLUMN t_conversation_branch_cursor.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE UNIQUE INDEX IF NOT EXISTS uk_conversation_branch_cursor_user
    ON t_conversation_branch_cursor (tenant_id, conversation_id, user_id)
    WHERE deleted = 0;
```

近期兼容策略：

- `active` 继续作为默认可见路径，保障现有接口不破坏。
- `branch_cursor` 作为用户视图状态，先在新接口中使用。
- 等前端完全切到 cursor 后，再评估是否弱化全局 `active`。

前端增强：

- 消息气泡底部显示 `branchIndex / branchTotal`。
- 左右按钮切换 sibling。
- 编辑用户消息时 fork 出新的 user message，不覆盖旧消息。
- 重新生成 assistant 回复时，在同一 user message 下新建 assistant sibling。
- 分支切换后刷新 active tree，保留输入框状态。

验收：

- 同一条 user message 编辑两次后，UI 显示 `1 / 3`、`2 / 3`、`3 / 3`。
- 切换分支后后续消息路径同步变化。
- 刷新页面后仍停留在该用户最后选择的分支。
- 并发 fork 不产生 `sibling_seq` 冲突。

### 3.4 P1：角色卡产品化

角色卡应从“输入框旁选择项”升级为可治理的运行配置。

后端增强：

- 保存时继续校验 name、definition。
- 高权限角色卡继续走 guardrail。
- 高权限角色卡启用、停用、更新写入审计。
- chat 和 Agent run 均记录 role card snapshot。

API 建议：

```text
GET    /api/role-cards
POST   /api/role-cards
PUT    /api/role-cards/{id}
PUT    /api/role-cards/{id}/activate
DELETE /api/role-cards/{id}
GET    /api/role-cards/effective?conversationId={cid}
```

前端增强：

- 角色卡管理页：列表、创建、编辑、删除、启用。
- 聊天输入区：本次对话角色选择。
- 会话头部：显示当前生效角色。
- 高权限开关：明确风险提示。

作用域优先级：

```text
请求显式 roleCardId
  > 会话绑定 roleCardId
  > 用户启用 roleCardId
  > AgentVersion.instructions
```

验收：

- 启用角色卡后，新对话默认带入。
- 单次请求指定角色卡不改变全局启用状态。
- 高权限角色卡保存危险内容时被拒绝。
- 历史 run 可看到当时使用的角色卡快照。

### 3.5 P1：MCP stdio 管理闭环

近期不要急着开放任意 stdio 命令编辑。先做可见、可诊断、可测试。

后端增强：

- 暴露 MCP server runtime 状态。
- 暴露工具发现结果。
- 暴露最近 stderr tail。
- 提供固定 safe test call，例如 `echo`。

API 建议：

```text
GET  /api/mcp/servers
GET  /api/mcp/servers/{serverName}
POST /api/mcp/servers/{serverName}/refresh-tools
POST /api/mcp/servers/{serverName}/test
```

返回示例：

```json
{
  "name": "local-echo",
  "transport": "stdio",
  "enabled": true,
  "status": "UP",
  "toolCount": 1,
  "lastDiscoveryAt": "2026-06-21T00:00:00Z",
  "stderrTail": "",
  "tools": [
    {
      "toolId": "echo",
      "provider": "MCP",
      "enabled": true
    }
  ]
}
```

前端增强：

- 插件/MCP 管理页展示 server 状态。
- 工具目录标识 provider=`MCP`。
- 支持 echo 测试调用。
- 展示 stderr tail 和最近一次发现时间。

验收：

- Docker full compose 下能看到 `local-echo`。
- `/api/tools?keyword=echo` 返回 provider=`MCP`。
- MCP server 异常时 UI 能看到 stderr tail。
- MCP 工具启用、禁用不影响非 MCP 工具。

## 4. 中期落地方案：4 到 8 个迭代

### 4.1 目标

把三项能力统一成可复用的 `Run Profile`，形成稳定的运行配置体系。

`Run Profile` 表达一组可复用运行配置：

```text
角色卡 + 工具集 + MCP 工具白名单 + A2A 远端 Agent + 执行引擎 + 模型配置 + 记忆范围 + 安全策略
```

### 4.2 Run Profile 数据模型

建议新增表：

```sql
CREATE TABLE IF NOT EXISTS sa_run_profile (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    description TEXT,
    role_card_id BIGINT,
    executor_engine VARCHAR(32) NOT NULL DEFAULT 'kernel',
    executor_config_json TEXT,
    model_config_json TEXT,
    memory_scope_json TEXT,
    guardrail_config_json TEXT,
    enabled SMALLINT NOT NULL DEFAULT 0,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sa_run_profile IS '运行画像表，保存用户或团队可复用的 Agent 运行配置，包括角色卡、执行引擎、模型配置、记忆范围、安全策略和默认启用状态';
COMMENT ON COLUMN sa_run_profile.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN sa_run_profile.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN sa_run_profile.user_id IS '创建或归属用户 ID';
COMMENT ON COLUMN sa_run_profile.name IS '运行画像名称';
COMMENT ON COLUMN sa_run_profile.description IS '运行画像描述';
COMMENT ON COLUMN sa_run_profile.role_card_id IS '默认绑定的角色卡 ID，可为空';
COMMENT ON COLUMN sa_run_profile.executor_engine IS '默认执行引擎，例如 kernel 或 agentscope';
COMMENT ON COLUMN sa_run_profile.executor_config_json IS '执行引擎配置 JSON，例如 AgentScope 的 Nacos、A2A、Studio 配置';
COMMENT ON COLUMN sa_run_profile.model_config_json IS '模型配置 JSON，例如模型名称、温度、最大输出长度等';
COMMENT ON COLUMN sa_run_profile.memory_scope_json IS '记忆范围 JSON，例如是否启用长期记忆、知识库范围、用户画像范围等';
COMMENT ON COLUMN sa_run_profile.guardrail_config_json IS '安全策略 JSON，例如高风险工具限制、输出过滤、审批策略等';
COMMENT ON COLUMN sa_run_profile.enabled IS '是否为当前用户默认启用画像，0 表示否，1 表示是';
COMMENT ON COLUMN sa_run_profile.create_time IS '创建时间';
COMMENT ON COLUMN sa_run_profile.update_time IS '更新时间';
COMMENT ON COLUMN sa_run_profile.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE INDEX IF NOT EXISTS idx_run_profile_user
    ON sa_run_profile (tenant_id, user_id, enabled);

CREATE TABLE IF NOT EXISTS sa_run_profile_tool (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    profile_id BIGINT NOT NULL,
    tool_id VARCHAR(128) NOT NULL,
    provider VARCHAR(32) NOT NULL,
    enabled SMALLINT NOT NULL DEFAULT 1,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sa_run_profile_tool IS '运行画像工具绑定表，记录某个 Run Profile 可使用的工具目录项，包括内置工具、MCP 工具和其他 provider 工具';
COMMENT ON COLUMN sa_run_profile_tool.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN sa_run_profile_tool.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN sa_run_profile_tool.profile_id IS '运行画像 ID，对应 sa_run_profile.id';
COMMENT ON COLUMN sa_run_profile_tool.tool_id IS '工具 ID，对应工具目录中的稳定 toolId';
COMMENT ON COLUMN sa_run_profile_tool.provider IS '工具提供方，例如 BUILT_IN、MCP、OPENAPI、A2A';
COMMENT ON COLUMN sa_run_profile_tool.enabled IS '该工具在当前运行画像中是否启用，0 表示禁用，1 表示启用';
COMMENT ON COLUMN sa_run_profile_tool.create_time IS '创建时间';
COMMENT ON COLUMN sa_run_profile_tool.update_time IS '更新时间';
COMMENT ON COLUMN sa_run_profile_tool.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE UNIQUE INDEX IF NOT EXISTS uk_run_profile_tool
    ON sa_run_profile_tool (tenant_id, profile_id, tool_id)
    WHERE deleted = 0;
```

### 4.3 Run Profile 服务和端口

Kernel inbound：

- `RunProfileInboundPort`
  - `list(userId)`
  - `create(command)`
  - `update(command)`
  - `activate(userId, profileId)`
  - `delete(userId, profileId)`
  - `resolve(userId, requestedProfileId, conversationId)`

Kernel outbound：

- `RunProfileRepositoryPort`
- `RunProfileToolRepositoryPort`

Web API：

```text
GET    /api/run-profiles
POST   /api/run-profiles
PUT    /api/run-profiles/{id}
POST   /api/run-profiles/{id}/activate
DELETE /api/run-profiles/{id}
POST   /api/conversations/{cid}/run-profile/{profileId}/apply
```

### 4.4 Run Profile 运行时解析

运行时优先级：

```text
请求显式 runProfileId
  > 会话绑定 runProfileId
  > 用户启用 runProfileId
  > 请求显式 roleCardId/toolIds/modelConfig
  > AgentVersion 默认配置
```

解析结果不直接传散字段，而是形成一个运行上下文对象：

```java
public record ResolvedRunContext(
        String userId,
        String conversationId,
        Long runProfileId,
        Long roleCardId,
        Long branchLeafMessageId,
        List<String> toolIds,
        String modelConfigJson,
        String memoryScopeJson,
        String guardrailConfigJson) {
}
```

该对象负责生成 `RunContextSnapshot`，后续 chat、Agent loop、tool gateway 都基于它工作。

### 4.5 对话实验和 Profile 对比

中期可以把消息树和 Run Profile 结合起来，形成实验能力。

核心能力：

- 选择一个 anchor message。
- 选择多个 Run Profile。
- 每个 profile 生成一条新分支。
- 每个分支记录独立 `RunContextSnapshot`。
- UI 展示回答内容、工具调用、耗时、成本、检索命中、错误。

API 建议：

```text
POST /api/conversations/{cid}/experiments
GET  /api/conversations/{cid}/experiments/{experimentId}
POST /api/conversations/{cid}/messages/{messageId}/compare
```

实验请求示例：

```json
{
  "anchorMessageId": 326827666353143808,
  "profileIds": [1001, 1002, 1003],
  "prompt": "请解释这段代码的性能问题"
}
```

验收：

- 同一条消息可以用多个 profile 生成多个 sibling 分支。
- 每个分支可以反查对应 profile 和 snapshot。
- UI 可以并排比较多个分支。
- 失败分支不会影响其他分支。

### 4.6 MCP 安全治理升级

中期再逐步开放 MCP server 配置能力，但必须带治理边界。

建议能力：

- command allowlist。
- args 参数校验。
- server 配置变更审计。
- MCP tool 风险等级自动标记。
- 高风险 MCP tool 默认 disabled。
- 工具调用进入审批、审计、限流。
- stdout、stderr、tool result 截断和脱敏。

推荐运行方式：

```text
近端：backend 内 ProcessBuilder 启动受控 stdio server
中期：独立 MCP runner 进程
远期：容器化或沙箱化 MCP runner
```

### 4.7 角色卡治理升级

中期把角色卡从个人配置升级为团队资产。

能力：

- 个人角色卡。
- 团队角色卡。
- 官方模板。
- 高权限角色卡审批。
- 角色卡版本历史。
- 角色卡效果评测。

建议新增字段：

- `scope`: `PERSONAL`、`TEAM`、`SYSTEM`
- `version`
- `approval_status`
- `published`

## 5. 远期规划：8 个迭代之后

### 5.1 目标形态

远期目标是 Agent Workbench：

```text
设计 Profile
  -> 用 Profile 运行 Agent
  -> 在消息树中形成分支
  -> 对分支做评测和对比
  -> 通过发布门禁
  -> 沉淀为团队模板或市场资产
```

### 5.2 Agent 实验平台

能力：

- Profile A/B test。
- 模型 A/B test。
- 工具集 A/B test。
- 角色卡 A/B test。
- 自动评分和人工评分结合。
- 成本、时延、质量三维对比。

关键输出：

- 最优分支。
- 最优 profile。
- 可复现 run snapshot。
- 可进入发布门禁的评测报告。

### 5.3 发布门禁和回归评测

将 Run Profile 接入 Agent 发布流程。

发布前必须回答：

- 当前 AgentVersion 使用哪些 profile 验证过。
- 每个 profile 的评测集通过率是多少。
- 高风险工具是否有审批策略。
- 高权限角色卡是否通过审核。
- 历史关键 case 是否回归通过。

远期 API：

```text
POST /api/agents/{agentId}/versions/{versionId}/profile-evaluations
GET  /api/agents/{agentId}/versions/{versionId}/profile-evaluations/latest
POST /api/agents/{agentId}/versions/{versionId}/production-gate/run
```

### 5.4 MCP Marketplace

MCP 可以进化成插件市场的一部分。

能力：

- MCP server 模板。
- 一键安装。
- 版本管理。
- 权限声明。
- 风险评级。
- 组织级启用。
- 回滚。
- 健康检查。

安全要求：

- 默认禁用高风险能力。
- 安装前展示权限声明。
- 运行在隔离 runner。
- 每次调用进入审计。

### 5.5 Profile Marketplace

Run Profile 也可以成为组织资产。

能力：

- 团队模板。
- 官方推荐 profile。
- 行业 profile。
- 评分和使用统计。
- 从成功分支一键保存为 profile。

典型例子：

- 法务审阅 profile。
- 医学问答 profile。
- 代码审查 profile。
- 数据分析 profile。
- 客服 SOP profile。

### 5.6 自学习闭环

长期可以从分支和评测结果反推配置优化建议。

示例：

- 某角色卡在知识问答场景得分长期低，建议调整定义。
- 某 MCP 工具高失败率，建议禁用或更换 server。
- 某 profile 成本高但质量提升有限，建议切换模型。
- 某类问题总是从同一分支胜出，建议把该分支配置固化为团队 profile。

## 6. 分阶段里程碑

| 阶段 | 时间 | 重点 | 交付物 |
| --- | --- | --- | --- |
| M1 | 近端第 1 迭代 | RunContextSnapshot | 快照表、端口、chat/Agent run 接入、查询 API |
| M2 | 近端第 2 迭代 | 消息树 UI 产品化 | 分支切换器、编辑 fork、重新生成、cursor 预留 |
| M3 | 近端第 3 迭代 | 角色卡和 MCP 管理闭环 | 角色卡管理页、MCP 状态页、echo 测试调用 |
| M4 | 中期第 1 到 2 迭代 | Run Profile | profile 数据模型、CRUD、启用、运行时解析 |
| M5 | 中期第 3 到 4 迭代 | Profile 运行接入 | chat/Agent loop/tool gateway 统一使用 ResolvedRunContext |
| M6 | 中期第 5 到 6 迭代 | 对话实验和分支对比 | 多 profile 分支生成、对比 UI、实验 API |
| M7 | 中期第 7 到 8 迭代 | MCP 和角色卡治理 | MCP runner 规划、审批、审计、团队角色卡 |
| M8 | 远期 | Agent Workbench | 实验平台、发布门禁、marketplace、自学习闭环 |

## 7. 推荐实施顺序

推荐顺序：

```text
RunContextSnapshot
  -> 消息树 UI 产品化
  -> 角色卡治理增强
  -> MCP 状态诊断和测试调用
  -> Run Profile
  -> Profile 运行接入
  -> 多 Profile 对话实验
  -> MCP runner 隔离
  -> 发布门禁和 marketplace
```

不建议的顺序：

```text
先开放任意 MCP stdio 配置 UI
  -> 再补安全
```

原因是 stdio MCP 本质上是服务端本地进程执行能力，安全边界必须先于大规模 UI 开放。

## 8. 风险与控制

| 风险 | 影响 | 控制策略 |
| --- | --- | --- |
| active 路径全局化 | 多窗口或多用户视图互相覆盖 | 引入 `t_conversation_branch_cursor`，逐步从全局 active 迁移到用户视图 cursor |
| 角色卡内容变更导致历史 run 不可复现 | 无法审计和回放 | 每次 run 保存 role card snapshot |
| MCP stdio 任意命令执行 | 服务端安全风险 | command allowlist、runner 隔离、审计、审批 |
| Run Profile 抽象过早膨胀 | 迭代变慢 | 近期只做 snapshot，中期再做 profile |
| UI 复杂度上升 | 用户学习成本增加 | 先把分支、角色、工具状态放在上下文面板，不一次性做复杂实验台 |
| 工具和 profile 权限耦合 | 难以治理 | 工具目录继续作为工具元数据 owner，profile 只保存引用和启用策略 |

## 9. 验收矩阵

### 9.1 近端验收

- Docker full compose 后端 healthy，前端 HTTP 200。
- `/api/tools?keyword=echo` 返回 provider=`MCP`。
- MCP server 状态 API 能返回 `local-echo` 和 tool count。
- 创建角色卡、启用角色卡、单次请求覆盖角色卡均通过。
- 编辑消息生成 sibling 分支，UI 可切换。
- 重新生成 assistant 回复生成 sibling 分支。
- 每次 chat 或 Agent run 生成 `RunContextSnapshot`。
- 历史快照不因角色卡后续编辑而变化。

### 9.2 中期验收

- 用户能创建并启用 Run Profile。
- Run Profile 能绑定角色卡、工具集、MCP 工具、模型配置、记忆范围和安全策略。
- chat 和 Agent loop 都通过统一 `ResolvedRunContext` 执行。
- 同一 anchor message 能用多个 profile 生成多个分支。
- UI 能对比分支内容、工具调用、耗时、成本和错误。
- 高风险 MCP 工具默认禁用，并进入审批和审计。

### 9.3 远期验收

- Agent 发布前可以选择 profile eval suite。
- 发布门禁展示 profile 维度评测结果。
- MCP server 可以作为 marketplace 资产安装和回滚。
- Run Profile 可以作为团队模板复用。
- 系统能基于历史分支和评测结果给出 profile 优化建议。

## 10. 总结

近端最关键的落点是 `RunContextSnapshot`。它成本不高，但会决定后续能否做到复现、审计、对比和评测。

中期最关键的抽象是 `Run Profile`。它把角色卡、消息分支、MCP 工具、模型、记忆和安全策略统一到一个可复用配置里。

远期最有价值的形态是 Agent Workbench。用户不只是聊天，而是在设计、运行、比较、评测、发布和复用 Agent 工作流。

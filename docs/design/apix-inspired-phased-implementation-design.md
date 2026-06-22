# APIX 借鉴能力分期落地详细设计

> 承接 `APIX借鉴特性落地设计方案.md` 与 `docs/design/apix-inspired-feature-evolution-roadmap.md`。本文把近端、中期、远期拆成可执行设计，重点说明 UI 和后端交互，并补齐 AgentScope 引入后的兼容边界。

## 1. AgentScope 冲突结论

AgentScope 的引入与本设计不冲突，但必须明确边界：

- Seahorse 继续作为产品控制面，负责租户、用户、会话、消息分支、角色卡、Run Profile、工具治理、审批、配额、审计、记忆、SSE/API 契约。
- AgentScope 作为可插拔执行面，挂在 `ReActExecutorPort` 后面，负责 `engine=agentscope` 下的 ReAct 执行、A2A、Nacos 发现、Studio/Trace 能力。
- 前端不直接依赖 AgentScope 内部对象，只展示 Seahorse 统一的运行上下文、工具、分支、画像和诊断信息。
- `RunContextSnapshot` 和 `Run Profile` 必须记录 `executorEngine`，否则 kernel 与 agentscope 的复现、评测、回滚没有统一证据。
- MCP stdio 仍属于 Seahorse 工具注册和工具网关能力；AgentScope 需要工具时通过 Tool Bridge 包装 Seahorse 工具，不重复实现 MCP 生命周期。

推荐实现方式：

```text
UI / Web API
  -> Seahorse Kernel Application Service
  -> RunContextResolver
  -> RunContextSnapshot
  -> ReActExecutorPort
      -> Kernel ReAct Executor
      -> AgentScope ReAct Executor
```

## 2. 通用设计约束

### 2.1 统一运行上下文

所有期次围绕一个运行时对象收敛：

```java
@Builder
public record ResolvedRunContext(
        String tenantId,
        Long userId,
        Long conversationId,
        Long branchLeafMessageId,
        Long roleCardId,
        Long runProfileId,
        String executorEngine,
        Map<String, Object> executorConfig,
        Map<String, Object> roleCardSnapshot,
        List<String> toolIds,
        List<String> mcpToolIds,
        List<String> a2aAgentIds,
        Map<String, Object> modelConfig,
        Map<String, Object> memoryScope,
        Map<String, Object> guardrailConfig,
        Map<String, Object> traceContext
) {
}
```

新增 Java DTO、command、response、entity 优先使用 Lombok，例如 `@Data`、`@Builder`、`@RequiredArgsConstructor`、`@Getter`，简单不可变值对象可继续使用 record。

### 2.2 后端分层

- `seahorse-agent-kernel`：领域对象、inbound/outbound port、运行上下文解析、快照生成。
- `seahorse-agent-adapter-web`：Controller、Request/Response、当前用户和租户解析。
- `seahorse-agent-adapter-repository-jdbc`：MyBatis/JDBC repository、迁移脚本。
- `seahorse-agent-adapter-mcp-http` 与后续 stdio adapter：MCP 工具发现、调用、诊断。
- `seahorse-agent-adapter-agent-agentscope`：只实现 `ReActExecutorPort`、A2A、Studio、Nacos 适配，不拥有 Run Profile。
- `frontend`：ChatPage、AgentInspector、Admin Tools、后续 Run Profile 页面。

### 2.3 前端交互原则

- Chat 主路径只暴露用户完成任务所需的控件：分支切换、角色选择、画像选择、重新生成。
- 管理和诊断信息放在 Admin 或 Inspector：MCP server 状态、执行引擎、快照 JSON、AgentScope Studio trace link。
- `engine=kernel|agentscope` 默认不打扰普通用户；管理员和实验场景可以显式选择。
- 所有危险能力使用确认或审批：高权限角色卡、stdio MCP、跨 Agent A2A 调用。

## 3. 近端一期：RunContextSnapshot

### 3.1 目标

每次 chat 或 Agent run 启动时保存当时实际生效的上下文，支持历史复现、审计、评测和 AgentScope/kernel 对比。

### 3.2 UI

入口：

- `frontend/src/pages/ChatPage.tsx`：消息完成后在消息操作菜单增加“查看运行上下文”。
- `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.tsx`：Agent run 详情中增加“上下文”页签。
- `frontend/src/pages/admin/traces/RagTraceDetailPage.tsx`：trace 详情展示关联 snapshot id 和 executor engine。

展示内容：

- 基础信息：runId、conversationId、branchLeafMessageId、executorEngine、createTime。
- 角色：roleCardId、name、higherPerm。
- 工具：Built-in、MCP、OpenAPI、A2A 分组列表。
- 模型与记忆：modelConfig、memoryScope。
- 安全：guardrailConfig、审批状态、配额策略摘要。
- AgentScope 扩展：agentScopeVersion、nacosNamespace、nacosGroup、studioTraceId、a2aRoute。

前端服务：

```ts
// frontend/src/services/runContextSnapshotService.ts
export interface RunContextSnapshot {
  id: string;
  runId: string;
  conversationId?: string;
  branchLeafMessageId?: string;
  roleCardId?: string;
  executorEngine: 'kernel' | 'agentscope';
  snapshot: Record<string, unknown>;
  createTime: string;
}

export async function getRunContextSnapshotByRunId(runId: string) {
  return api.get<RunContextSnapshot>(`/api/run-context-snapshots/by-run/${encodeURIComponent(runId)}`);
}
```

### 3.3 后端 API

```text
GET /api/run-context-snapshots/by-run/{runId}
GET /api/agent-runs/{runId}/context-snapshot
```

返回：

```json
{
  "id": "326827700000000001",
  "runId": "run-20260621-001",
  "conversationId": "326827222507700224",
  "branchLeafMessageId": "326827666353143808",
  "roleCardId": "326827218426642432",
  "executorEngine": "agentscope",
  "snapshot": {
    "runProfileId": "326827700000000099",
    "toolIds": ["get_current_datetime"],
    "mcpToolIds": ["filesystem.read_file"],
    "a2aAgentIds": ["seahorse-researcher"],
    "modelConfig": {"model": "gpt-4.1-mini"},
    "memoryScope": {"longTerm": true},
    "guardrailConfig": {"approvalRequiredForHighRiskTool": true},
    "agentScope": {
      "version": "2.0.0-RC3",
      "nacosNamespace": "public",
      "nacosGroup": "DEFAULT_GROUP",
      "studioTraceId": "trace-001"
    }
  },
  "createTime": "2026-06-21T10:00:00"
}
```

### 3.4 数据模型

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
COMMENT ON COLUMN t_run_context_snapshot.snapshot_json IS '运行上下文完整快照 JSON，保存角色卡副本、工具集、MCP 工具、模型配置、记忆范围和安全策略';
COMMENT ON COLUMN t_run_context_snapshot.create_time IS '创建时间';
COMMENT ON COLUMN t_run_context_snapshot.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE INDEX IF NOT EXISTS idx_run_context_snapshot_run
    ON t_run_context_snapshot (tenant_id, run_id);
```

### 3.5 后端交互

```text
ChatPage / Agent API
  -> SeahorseChatController 或 SeahorseAgentRunController
  -> KernelChatInboundService 或 AgentRunService
  -> RunContextResolver.resolve(...)
  -> RunContextSnapshotRepositoryPort.save(...)
  -> ReActExecutorPort.streamExecute(...)
```

AgentScope 兼容点：

- `executorEngine` 从配置、Run Profile 或灰度路由解析，最终写入 snapshot。
- `AgentScopeReActExecutor` 只读取 `AgentLoopRequest`，不重新解析角色卡、工具白名单和记忆范围。
- AgentScope 的 Studio traceId 通过 `traceContext` 回写 snapshot 或 run 详情。

### 3.6 验收

- 普通 chat、Agent mode、`engine=agentscope` 三类运行均生成快照。
- 通过 runId 可查询到唯一有效快照。
- 切回 `engine=kernel` 后快照仍记录 `executorEngine=kernel`。
- 快照里角色卡、工具、MCP、A2A、模型、记忆、安全策略字段完整。

## 4. 近端二期：消息分支产品化

### 4.1 目标

把消息树从后端能力变成稳定 UI 工作流：编辑、重试、fork、切换分支、按分支继续对话。

### 4.2 UI

入口：

- `frontend/src/pages/ChatPage.tsx`
- `frontend/src/stores/chatStore.ts`
- `frontend/src/services/chatService.ts`

交互：

- 每条用户消息显示编辑按钮；编辑后创建新分支，不覆盖原消息。
- 每条 assistant 消息显示重新生成按钮；重新生成创建新的 assistant sibling。
- 消息气泡上显示分支计数，例如 `2/4`，左右箭头切换 sibling。
- 侧栏增加“分支路径”轻量视图，展示当前 leaf 到 root 的路径。
- 用户切换分支后，后续发送消息携带 `branchLeafMessageId`。

状态规则：

- 当前窗口使用 `t_conversation_branch_cursor` 保存 leaf，避免多窗口互相覆盖。
- 分支切换只改变视图和下一次请求上下文，不删除历史消息。
- 正在流式输出时禁用分支切换，避免上下文漂移。

### 4.3 后端 API

```text
GET  /api/conversations/{conversationId}/messages/tree
POST /api/conversations/{conversationId}/messages/{messageId}/edit
POST /api/conversations/{conversationId}/messages/{messageId}/regenerate
POST /api/conversations/{conversationId}/branch-cursor
GET  /api/conversations/{conversationId}/branch-cursor
```

请求示例：

```json
{
  "leafMessageId": "326827666353143808"
}
```

发送 chat 时扩展：

```json
{
  "conversationId": "326827222507700224",
  "branchLeafMessageId": "326827666353143808",
  "message": "沿当前分支继续"
}
```

### 4.4 数据模型

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

### 4.5 后端交互

```text
ChatPage 切换分支
  -> POST /api/conversations/{cid}/branch-cursor
  -> ConversationBranchCursorInboundPort.saveCursor(...)
  -> ConversationBranchCursorRepositoryPort.upsert(...)

ChatPage 发送消息
  -> POST /api/chat/stream
  -> StreamChatCommand.branchLeafMessageId
  -> ChatHistoryResolver.resolvePath(conversationId, branchLeafMessageId)
  -> RunContextResolver.resolve(...)
  -> RunContextSnapshotRepositoryPort.save(...)
```

AgentScope 兼容点：

- AgentScope 不参与消息树管理，只消费已经解析好的 history。
- `branchLeafMessageId` 写入 `RunContextSnapshot`，确保 kernel 与 agentscope 复现同一历史路径。

### 4.6 验收

- 编辑旧消息会创建新分支，原分支可切回。
- 重新生成 assistant 消息会创建 sibling，分支计数正确。
- 切换分支后发送新消息，后端使用选中 leaf 的路径。
- 多浏览器窗口切换同一会话不会互相污染当前 leaf。

## 5. 近端三期：角色卡与 MCP stdio 产品化

### 5.1 目标

让用户能稳定创建、启用、临时选择角色卡；让管理员能管理 MCP stdio server、查看工具发现状态和诊断日志。

### 5.2 角色卡 UI

入口：

- `frontend/src/services/roleCardService.ts`
- `frontend/src/pages/ChatPage.tsx`
- 可新增 `frontend/src/pages/admin/agents/components/RoleCardPicker.tsx`

交互：

- Chat 输入区上方提供角色卡选择器，默认展示当前启用卡。
- 角色卡抽屉支持新建、编辑、启用、停用、删除。
- 高权限注入 `higherPerm=true` 时展示风险确认，并调用后端校验。
- 本次会话临时选择通过请求参数 `roleCardId` 传入，不强制改默认启用卡。

后端 API：

```text
GET    /api/role-cards
POST   /api/role-cards
PUT    /api/role-cards/{id}
POST   /api/role-cards/{id}/activate
DELETE /api/role-cards/{id}
```

Chat 请求扩展：

```json
{
  "conversationId": "326827222507700224",
  "roleCardId": "326827218426642432",
  "message": "用这个角色继续"
}
```

后端交互：

```text
ChatPage 选择角色卡
  -> POST /api/chat/stream
  -> RunContextResolver.resolveRoleCard(userId, requestedRoleCardId)
  -> RoleCardGuardrail.validate(...)
  -> RunContextSnapshot.roleCardSnapshot
  -> AgentLoopRequest.instructions
```

AgentScope 兼容点：

- 角色卡由 Seahorse 解析为最终 instructions 或 system overlay。
- AgentScope Nacos prompt 可以提供基础 prompt，但角色卡叠加和高权限校验仍在 Seahorse 侧。
- 快照保存角色卡内容副本，而不是只保存 roleCardId。

### 5.3 MCP stdio UI

入口：

- `frontend/src/pages/admin/tools/ToolCatalogPage.tsx`
- `frontend/src/pages/admin/tools/ToolDetailPage.tsx`
- 可新增 `frontend/src/pages/admin/tools/McpServerPanel.tsx`

交互：

- 工具目录增加 provider 过滤：Built-in、MCP HTTP、MCP stdio、OpenAPI、A2A。
- MCP server 列表展示状态：starting、ready、failed、stopped。
- server 详情展示 command、args、env key 摘要、工具发现结果、stderr tail。
- 支持重启 server、刷新工具、启用/禁用单个工具。
- 高风险 stdio server 必须标记来源和 allowlist。

后端 API：

```text
GET  /api/mcp/servers
GET  /api/mcp/servers/{serverId}
POST /api/mcp/servers/{serverId}/restart
POST /api/mcp/servers/{serverId}/refresh-tools
GET  /api/mcp/servers/{serverId}/stderr-tail
POST /api/tools/{toolId}/enable
POST /api/tools/{toolId}/disable
```

后端交互：

```text
Admin Tools
  -> SeahorseToolCatalogController / McpServerController
  -> McpServerInboundPort
  -> NativeMcpToolRegistry
  -> McpStdioSessionManager
  -> single-thread executor per server
```

AgentScope 兼容点：

- AgentScope 需要工具时通过 `AgentScopeToolFactory` 包装 Seahorse `ToolPort`。
- MCP stdio 生命周期仍由 Seahorse adapter 管理，避免 AgentScope 和 Seahorse 各自启动同一个本地进程。
- 工具调用审计、审批、配额保持在 Seahorse 工具网关。

### 5.4 验收

- 用户可在 chat 中临时切换角色卡并生成快照。
- 高权限角色卡未通过校验时无法注入。
- 管理员能看到 MCP stdio server 状态和 stderr tail。
- MCP stdio 工具能被 AgentScope 工具桥使用，但不会绕过 Seahorse 审批。

## 6. 中期一期：Run Profile

### 6.1 目标

把角色卡、工具集、MCP、A2A、模型、记忆、安全策略、执行引擎组合成可复用运行画像。

### 6.2 UI

入口：

- 新增 `frontend/src/pages/admin/run-profiles/RunProfilePage.tsx`
- 新增 `frontend/src/services/runProfileService.ts`
- `frontend/src/pages/ChatPage.tsx` 增加 Run Profile 选择器
- `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.tsx` 展示 runProfileId

Run Profile 编辑器：

- 基础：名称、描述、默认启用。
- 角色：选择 roleCard。
- 执行引擎：`kernel`、`agentscope`，默认 `kernel`；普通用户可隐藏，管理员可配置。
- 工具：多选 Built-in、MCP、OpenAPI、A2A。
- 模型：模型、温度、max tokens、超时。
- 记忆：长期记忆、知识库范围、用户画像范围。
- 安全：高风险工具审批、输出过滤、A2A 允许范围。
- AgentScope 扩展：Nacos namespace/group/cluster、是否启用 Studio trace、A2A routing policy。

Chat 使用：

- 输入区上方提供画像选择器。
- 选择画像后展示摘要：角色、工具数量、模型、执行引擎。
- 用户仍可临时覆盖角色卡或分支 leaf；覆盖项写入本次 snapshot，不改画像。

### 6.3 数据模型

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

COMMENT ON TABLE sa_run_profile_tool IS '运行画像工具绑定表，记录某个 Run Profile 可使用的工具目录项，包括内置工具、MCP 工具、OpenAPI 工具和 A2A 远端 Agent 工具';
COMMENT ON COLUMN sa_run_profile_tool.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN sa_run_profile_tool.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN sa_run_profile_tool.profile_id IS '运行画像 ID，对应 sa_run_profile.id';
COMMENT ON COLUMN sa_run_profile_tool.tool_id IS '工具 ID，对应工具目录中的稳定 toolId 或 A2A agentId';
COMMENT ON COLUMN sa_run_profile_tool.provider IS '工具提供方，例如 BUILT_IN、MCP、OPENAPI、A2A';
COMMENT ON COLUMN sa_run_profile_tool.enabled IS '该工具在当前运行画像中是否启用，0 表示禁用，1 表示启用';
COMMENT ON COLUMN sa_run_profile_tool.create_time IS '创建时间';
COMMENT ON COLUMN sa_run_profile_tool.update_time IS '更新时间';
COMMENT ON COLUMN sa_run_profile_tool.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE UNIQUE INDEX IF NOT EXISTS uk_run_profile_tool
    ON sa_run_profile_tool (tenant_id, profile_id, tool_id)
    WHERE deleted = 0;
```

### 6.4 后端 API

```text
GET    /api/run-profiles
POST   /api/run-profiles
GET    /api/run-profiles/{id}
PUT    /api/run-profiles/{id}
POST   /api/run-profiles/{id}/activate
DELETE /api/run-profiles/{id}
POST   /api/conversations/{conversationId}/run-profile/{profileId}/apply
POST   /api/run-profiles/{id}/resolve-preview
```

创建请求：

```json
{
  "name": "研究助手 - AgentScope",
  "description": "启用 A2A researcher，适合长链路研究任务",
  "roleCardId": "326827218426642432",
  "executorEngine": "agentscope",
  "executorConfig": {
    "nacosNamespace": "public",
    "nacosGroup": "DEFAULT_GROUP",
    "studioTraceEnabled": true,
    "a2aRoutingPolicy": "same_tenant_first"
  },
  "toolBindings": [
    {"toolId": "get_current_datetime", "provider": "BUILT_IN", "enabled": true},
    {"toolId": "filesystem.read_file", "provider": "MCP", "enabled": true},
    {"toolId": "seahorse-researcher", "provider": "A2A", "enabled": true}
  ],
  "modelConfig": {"model": "gpt-4.1-mini", "temperature": 0.3},
  "memoryScope": {"longTerm": true, "knowledgeBaseIds": ["kb-001"]},
  "guardrailConfig": {"highRiskToolApproval": true}
}
```

### 6.5 后端交互

```text
RunProfilePage 保存画像
  -> SeahorseRunProfileController
  -> RunProfileInboundPort.save(...)
  -> RunProfileRepositoryPort.save(...)
  -> RunProfileToolRepositoryPort.replaceTools(...)

ChatPage 选择画像并发送
  -> POST /api/chat/stream {runProfileId, branchLeafMessageId, roleCardId}
  -> RunProfileInboundPort.resolve(...)
  -> RunContextResolver.merge(profile, requestOverrides)
  -> RunContextSnapshotRepositoryPort.save(...)
  -> ReActExecutorPort.streamExecute(...)
```

合并优先级：

```text
请求临时覆盖 > 会话当前选择 > Run Profile > 用户默认配置 > 系统默认配置
```

AgentScope 兼容点：

- `executorEngine=agentscope` 只影响 `ReActExecutorPort` 路由。
- `executorConfig` 保存 AgentScope 需要的 Nacos、A2A、Studio 配置摘要。
- 工具、记忆、安全策略仍先在 Seahorse resolve 和校验，再传入 AgentScope executor。

### 6.6 验收

- 用户能创建、编辑、启用、删除 Run Profile。
- Chat 选择 Run Profile 后生成的 snapshot 包含画像配置和临时覆盖项。
- `executorEngine=agentscope` 时可路由到 AgentScope executor；切回 kernel 后同一画像可回滚。
- A2A 工具绑定只允许同租户或授权范围内的远端 Agent。

## 7. 中期二期：对话实验与画像对比

### 7.1 目标

把消息分支和 Run Profile 结合，支持同一问题在不同画像、模型、工具、执行引擎下对比输出。

### 7.2 UI

入口：

- 新增 `frontend/src/pages/admin/run-profiles/RunExperimentPage.tsx`
- ChatPage 在消息菜单增加“用画像对比重新生成”
- AgentInspector 增加“实验结果”页签

交互：

- 选择一个基准消息或当前分支 leaf。
- 选择 2 到 4 个 Run Profile。
- 选择运行模式：并行运行、顺序运行。
- 展示对比表：回答质量评分、耗时、token、费用、工具调用次数、失败原因。
- 支持把某个实验结果 fork 成新分支。

### 7.3 数据模型

```sql
CREATE TABLE IF NOT EXISTS sa_run_experiment (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    user_id BIGINT NOT NULL,
    conversation_id BIGINT NOT NULL,
    base_leaf_message_id BIGINT NOT NULL,
    name VARCHAR(128) NOT NULL,
    status VARCHAR(32) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sa_run_experiment IS '运行实验表，记录基于同一会话分支发起的多个 Run Profile 对比实验';
COMMENT ON COLUMN sa_run_experiment.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN sa_run_experiment.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN sa_run_experiment.user_id IS '发起实验的用户 ID';
COMMENT ON COLUMN sa_run_experiment.conversation_id IS '实验所属会话 ID';
COMMENT ON COLUMN sa_run_experiment.base_leaf_message_id IS '实验基准分支叶子消息 ID';
COMMENT ON COLUMN sa_run_experiment.name IS '实验名称';
COMMENT ON COLUMN sa_run_experiment.status IS '实验状态，例如 PENDING、RUNNING、SUCCEEDED、FAILED、CANCELLED';
COMMENT ON COLUMN sa_run_experiment.create_time IS '创建时间';
COMMENT ON COLUMN sa_run_experiment.update_time IS '更新时间';
COMMENT ON COLUMN sa_run_experiment.deleted IS '软删除标记，0 表示有效，1 表示已删除';

CREATE TABLE IF NOT EXISTS sa_run_experiment_trial (
    id BIGINT NOT NULL PRIMARY KEY,
    tenant_id VARCHAR(64) NOT NULL DEFAULT 'default',
    experiment_id BIGINT NOT NULL,
    run_profile_id BIGINT NOT NULL,
    run_id VARCHAR(64) NOT NULL,
    output_message_id BIGINT,
    score_json TEXT,
    metric_json TEXT,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    deleted SMALLINT NOT NULL DEFAULT 0
);

COMMENT ON TABLE sa_run_experiment_trial IS '运行实验试验项表，记录某个 Run Profile 在一次实验中的运行结果、评分和成本指标';
COMMENT ON COLUMN sa_run_experiment_trial.id IS '主键 ID，雪花 ID';
COMMENT ON COLUMN sa_run_experiment_trial.tenant_id IS '租户 ID，用于多租户隔离';
COMMENT ON COLUMN sa_run_experiment_trial.experiment_id IS '运行实验 ID，对应 sa_run_experiment.id';
COMMENT ON COLUMN sa_run_experiment_trial.run_profile_id IS '参与实验的运行画像 ID';
COMMENT ON COLUMN sa_run_experiment_trial.run_id IS '本次试验对应的运行 ID';
COMMENT ON COLUMN sa_run_experiment_trial.output_message_id IS '本次试验生成的 assistant 消息 ID，可为空';
COMMENT ON COLUMN sa_run_experiment_trial.score_json IS '实验评分 JSON，例如人工评分、自动评分、偏好标签';
COMMENT ON COLUMN sa_run_experiment_trial.metric_json IS '运行指标 JSON，例如耗时、token、费用、工具调用次数';
COMMENT ON COLUMN sa_run_experiment_trial.status IS '试验项状态，例如 PENDING、RUNNING、SUCCEEDED、FAILED、CANCELLED';
COMMENT ON COLUMN sa_run_experiment_trial.error_message IS '失败原因或错误摘要';
COMMENT ON COLUMN sa_run_experiment_trial.create_time IS '创建时间';
COMMENT ON COLUMN sa_run_experiment_trial.update_time IS '更新时间';
COMMENT ON COLUMN sa_run_experiment_trial.deleted IS '软删除标记，0 表示有效，1 表示已删除';
```

### 7.4 后端 API

```text
POST /api/run-experiments
GET  /api/run-experiments/{id}
POST /api/run-experiments/{id}/cancel
POST /api/run-experiments/{id}/trials/{trialId}/fork-to-branch
POST /api/run-experiments/{id}/trials/{trialId}/score
```

### 7.5 后端交互

```text
RunExperimentPage 发起实验
  -> SeahorseRunExperimentController
  -> RunExperimentInboundPort.create(...)
  -> ChatHistoryResolver.resolvePath(baseLeafMessageId)
  -> 对每个 RunProfile 调用 RunContextResolver.resolve(...)
  -> RunContextSnapshotRepositoryPort.save(...)
  -> ReActExecutorPort.streamExecute(...) 或 execute(...)
  -> RunExperimentTrialRepositoryPort.updateMetrics(...)
```

AgentScope 兼容点：

- kernel 与 agentscope 可以作为两个不同 Run Profile 参与同一实验。
- 结果比较使用 Seahorse 的 runId、snapshot、metric，不直接比较 AgentScope 内部事件。
- AgentScope Studio trace 作为 trial 的外链诊断信息展示。

### 7.6 验收

- 同一分支 leaf 可同时用多个 Run Profile 生成多个结果。
- 每个 trial 都关联独立 runId 和 RunContextSnapshot。
- 对比表能展示质量评分、耗时、token、费用、工具调用次数。
- kernel 与 agentscope 的结果可以并列比较。

## 8. 中期三期：治理与发布门禁

### 8.1 目标

把 Run Profile 纳入组织治理：审批、模板、发布门禁、风险扫描、审计。

### 8.2 UI

入口：

- `frontend/src/pages/admin/agents/components/ProductionGatePanel.tsx`
- `frontend/src/pages/admin/security/AccessDecisionPage.tsx`
- `frontend/src/pages/admin/tools/ToolInvocationAuditPage.tsx`
- Run Profile 页面增加“治理”页签

交互：

- Run Profile 风险摘要：高权限角色、stdio MCP、A2A、外部 OpenAPI、长期记忆。
- 发布前检查：工具风险、配额策略、审计覆盖、AgentScope release gate 状态。
- 审批流：高风险画像提交审批后才能团队共享。
- 审计视图：按 runProfileId 查看历史运行、工具调用、成本、失败率。

### 8.3 后端 API

```text
POST /api/run-profiles/{id}/submit-approval
POST /api/run-profiles/{id}/approve
POST /api/run-profiles/{id}/reject
GET  /api/run-profiles/{id}/risk-summary
GET  /api/run-profiles/{id}/audit-summary
POST /api/run-profiles/{id}/production-gate/check
```

### 8.4 后端交互

```text
RunProfilePage 点击发布检查
  -> SeahorseRunProfileGovernanceController
  -> RunProfileGovernanceInboundPort.check(...)
  -> ToolRiskPolicyPort
  -> ApprovalPolicyPort
  -> CostQuotaPolicyPort
  -> AgentScopeReleaseGateStatusPort
```

AgentScope 兼容点：

- `engine=agentscope` 的画像需要额外检查 A2A 认证模式、Nacos namespace/group、Studio trace 开关和 release gate 结果。
- A2A 远端 Agent 进入工具风险模型，按远端 Agent Card 的租户、版本、能力和健康状态判断。
- 生产门禁只依赖 Seahorse 统一检查结果，不调用前端直连 Studio。

### 8.5 验收

- 高风险画像无法绕过审批直接团队共享。
- `engine=agentscope` 画像展示 AgentScope 专属检查项。
- 审计摘要能按 runProfileId 汇总运行次数、成本、失败率和高风险工具调用。
- 发布门禁失败时返回可操作的失败项编码和说明。

## 9. 远期：Agent Workbench

### 9.1 目标

把消息分支、Run Profile、实验、治理、A2A、Studio trace 整合成 Agent 工作台。

### 9.2 UI

主页面建议新增：

- `frontend/src/pages/admin/agent-workbench/AgentWorkbenchPage.tsx`

布局：

- 左侧：会话和实验树。
- 中间：当前分支对话和多结果对比。
- 右侧：上下文 Inspector，展示 Run Profile、snapshot、工具、记忆、审批、成本、trace。
- 顶部：画像选择、执行引擎、运行模式、发布门禁状态。

关键能力：

- 从一个分支发起多画像实验。
- 从实验结果生成候选 AgentVersion 或团队模板。
- 查看 kernel/agentscope 执行差异。
- 打开 AgentScope Studio trace 链接。
- 将稳定画像发布到团队或 marketplace。

### 9.3 后端交互

```text
AgentWorkbenchPage
  -> /api/conversations/{id}/messages/tree
  -> /api/run-profiles
  -> /api/run-experiments
  -> /api/run-context-snapshots/by-run/{runId}
  -> /api/agent-runs/{runId}/cost-summary
  -> /api/run-profiles/{id}/production-gate/check
```

长期新增能力：

- Run Profile marketplace：组织模板、评分、版本、回滚。
- 自动评测：把实验 trial 纳入 AgentEval 数据集。
- 自学习闭环：根据用户偏好和生产指标推荐画像调整。
- A2A marketplace：远端 Agent 作为可治理工具进入工具目录。

### 9.4 AgentScope 兼容点

- AgentScope Studio 是诊断入口，不替代 Seahorse Workbench。
- AgentScope A2A 远端 Agent 是工具目录 provider，不替代 Run Profile。
- AgentScope Nacos prompt/skill 是配置来源之一，最终运行仍保存 Seahorse snapshot。
- Workbench 通过 `executorEngine` 对比 kernel 与 agentscope，不直接暴露内部 executor API。

### 9.5 验收

- 用户能在一个工作台内完成分支、画像、实验、对比、发布门禁。
- 每个运行结果都有 snapshot、runId、成本、trace。
- kernel 和 agentscope 的差异能被展示和追溯。
- 团队模板可复用并可回滚。

## 10. 分期依赖关系

```text
RunContextSnapshot
  -> 消息分支产品化
  -> 角色卡与 MCP stdio 产品化
  -> Run Profile
  -> 对话实验与画像对比
  -> 治理与发布门禁
  -> Agent Workbench
```

AgentScope 引入不阻塞前三期，但从 Run Profile 开始必须纳入 `executorEngine`：

- 前三期：AgentScope 可以作为后台灰度 executor，不影响用户主路径。
- Run Profile：开始允许管理员选择 `engine=agentscope`。
- 实验期：支持 kernel 与 agentscope 画像对比。
- 治理期：AgentScope release gate、A2A 安全和 Nacos 配置进入生产门禁。

## 11. 测试策略

后端：

- `seahorse-agent-kernel`：RunContextResolver、RunProfileResolver、branch path resolver 单测。
- `seahorse-agent-adapter-web`：Controller contract tests，覆盖请求字段、租户隔离、错误响应。
- `seahorse-agent-adapter-repository-jdbc`：repository tests，覆盖软删除、唯一约束、中文注释迁移检查。
- `seahorse-agent-adapter-agent-agentscope`：`engine=agentscope` 语义等价、工具审批、A2A 租户隔离、Studio trace 传播。
- `seahorse-agent-tests`：chat/agent run 集成测试，覆盖 kernel 与 agentscope 两种 executor。

前端：

- service contract tests：新增 API 路径和响应结构。
- ChatPage tests：分支切换、角色卡、Run Profile 选择、流式中禁用切换。
- Admin page tests：MCP server 状态、Run Profile 编辑、实验对比、治理检查失败态。
- Playwright smoke：创建画像、选择画像运行、查看 snapshot、发起双画像对比。

## 12. 风险和处理

| 风险 | 影响 | 处理 |
|------|------|------|
| Run Profile 过早膨胀 | 交付变慢 | 先落 snapshot，再逐步把角色、工具、模型、记忆、安全纳入画像 |
| AgentScope 旁路治理 | 审批、审计、配额失效 | AgentScope 只在 `ReActExecutorPort` 后执行，工具和安全策略由 Seahorse 先解析和拦截 |
| MCP stdio 本地进程风险 | 可能访问敏感文件或命令 | allowlist、stderr 诊断、审批、租户隔离、工具风险标记 |
| 分支上下文漂移 | 复现失败 | `branchLeafMessageId` 进入 chat 请求和 snapshot |
| A2A 跨租户误调 | 数据泄露 | Agent Card、resolver、invoke 全链路校验 tenant 和签名 |
| Studio 与 Seahorse trace 割裂 | 排障困难 | snapshot 保存 traceContext，Workbench 链接 Studio trace |

## 13. 最小落地顺序

1. 新增 RunContextSnapshot 表、端口、查询 API 和 AgentInspector 展示。
2. Chat 请求打通 `branchLeafMessageId`、`roleCardId`、`runProfileId` 的运行上下文解析。
3. 消息分支 UI 和 branch cursor 落地。
4. 角色卡选择器和 MCP stdio 诊断面板落地。
5. Run Profile CRUD、选择器、运行时解析落地，并记录 `executorEngine`。
6. 实验对比、治理门禁、Workbench 依次推进。

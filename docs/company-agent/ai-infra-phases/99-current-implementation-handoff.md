# 当前实现交接：HITL 审批请求最小切片

更新时间：2026-05-23

## 1. 背景与目标

本交接文档承接 `docs/company-agent/ai-infra-phases/` 下的企业级 AI Infra 分阶段规划，当前推进位置在 Phase 2 到 Phase 3 的交界：

- Phase 2：`02-tool-gateway-policy-engine.md`
- Phase 3：`03-durable-runtime-hitl.md`

本次最小改动点只解决一个问题：当 `ToolPolicyPort` 返回 `APPROVAL_REQUIRED` 时，`LocalToolGatewayPort` 必须在不执行真实工具的前提下创建并持久化一条 `PENDING` 的 `ApprovalRequest`，为后续审批列表、审批决策、checkpoint 和 resume 铺好数据入口。

## 2. 本次已完成内容

### 2.1 Kernel 领域与端口

新增审批领域模型：

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/approval/ApprovalRequest.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/approval/ApprovalRequestStatus.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/approval/ApprovalType.java`

新增出站端口：

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/ToolApprovalRequestRepositoryPort.java`

改造 `LocalToolGatewayPort`：

- 保留原有构造器，新增审批仓储依赖时使用 `ToolApprovalRequestRepositoryPort.noop()` 做兼容 fallback。
- 仍然先写 `ToolInvocationAuditPort.recordRequested(...)` 和 `recordDecision(...)`。
- 当策略效果为 `APPROVAL_REQUIRED`：
  - 不调用真实 `ToolPort`。
  - 创建 `ApprovalRequestStatus.PENDING` 审批请求。
  - 审批请求使用与审计兼容的有效 `runId/userId`。
  - `argumentsPreviewJson` 只保存参数 key、参数数量和资源引用，不保存完整敏感入参。
  - 审计完成状态仍写为 `APPROVAL_REQUIRED`。

### 2.2 JDBC 持久化

新增 JDBC 适配器：

- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcToolApprovalRequestRepositoryAdapter.java`

新增测试：

- `seahorse-agent-adapter-repository-jdbc/src/test/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcToolApprovalRequestRepositoryAdapterTests.java`

共享 PostgreSQL schema 增加表：

- `sa_approval_request`

新增索引：

- `idx_sa_approval_request_status`
- `idx_sa_approval_request_run`

### 2.3 Spring Boot Starter wiring

改造：

- `SeahorseAgentRegistryRepositoryAutoConfiguration`
  - 在 JDBC repository 模式下自动注册 `ToolApprovalRequestRepositoryPort`。
- `SeahorseAgentKernelAgentAutoConfiguration`
  - 将可选 `ToolApprovalRequestRepositoryPort` 注入 `LocalToolGatewayPort`。

新增/扩展验证：

- `SeahorseAgentRegistryAutoConfigurationTests`
  - 验证审批仓储 bean 被自动配置。
- `SeahorseAgentChatRunStoreAutoConfigurationTests`
  - 直接调用 `ToolGatewayPort`，验证审批仓储被注入、真实工具未执行、审批请求被保存。

## 3. 明确未完成范围

本切片没有实现以下内容，后续开发不要误判为已完成：

- 没有审批列表/详情/通过/拒绝/修改 API。
- 没有 `AgentCheckpoint` 模型、仓储和表。
- 没有把 `AgentRun` 状态切换为 `WAITING_APPROVAL`。
- 没有实现审批通过后的 resume。
- 没有保存完整 pending tool call 快照。
- 没有资源 ACL。
- 没有输出脱敏。
- 没有 worker lease。

## 4. 下一步推荐顺序

建议继续保持小切片，不要一次性吞掉整个 Phase 3。

1. Approval query/decision API

   建议先补查询和决策端口，而不是马上做 resume：

   - Kernel inbound：`ApprovalManagementInboundPort`
   - Kernel service：管理员可分页查询 `PENDING` 审批、查看详情、approve/reject/modify。
   - Repository port：扩展为 `findById`、`page`、`decide`，或新增 query/decision 分离端口。
   - JDBC：对 `sa_approval_request` 做分页查询和乐观状态更新。
   - Web API：实现 `GET /api/approvals`、`GET /api/approvals/{approvalId}`、`POST /api/approvals/{approvalId}/approve|reject|modify`。

2. AgentCheckpoint repository

   在审批可查可决策后，再新增 `AgentCheckpoint`：

   - 先实现 domain + repository port + JDBC adapter。
   - checkpoint 类型至少覆盖 `BEFORE_TOOL` 和 `WAITING_APPROVAL`。
   - `pending_tool_call_json` 必须能表达 toolId、toolCallId、arguments、resourceRefs、idempotencyKey、agent/version/run/user/tenant 上下文。
   - 不要在 checkpoint 中保存明文 secret。

3. Runtime WAITING_APPROVAL 中断

   在 Tool Gateway 或更高层 Durable Runtime 收到 `APPROVAL_REQUIRED` 后：

   - 保存 `WAITING_APPROVAL` checkpoint。
   - 将 run 状态更新为 `WAITING_APPROVAL`。
   - 释放当前执行权。
   - 对上层流式响应返回明确的等待审批状态。

4. Resume from checkpoint

   审批通过后必须从 checkpoint 恢复，不允许直接从审批 API 调用真实工具：

   - 使用原 `idempotencyKey`。
   - 使用审批通过或修改后的参数快照。
   - tool result 写回原 run step。
   - run 继续后续模型 turn。

## 5. 代码边界提醒

- Kernel 只放领域模型、端口和核心服务。
- JDBC adapter 只负责持久化，不承载审批业务决策。
- Starter 只做自动配置和 fallback wiring。
- Web/API 是下一切片，不要塞进本次提交。
- 现有工作区有不少非本切片脏文件，提交时只 stage 本文列出的审批切片相关文件。

## 6. 已运行验证命令

```powershell
.\mvnw -pl seahorse-agent-kernel '-Dtest=LocalToolGatewayPortAuditTests' test
```

覆盖：`APPROVAL_REQUIRED` 时创建 `PENDING` 审批请求，真实工具不执行，审计状态为 `APPROVAL_REQUIRED`。

```powershell
.\mvnw '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcToolApprovalRequestRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

覆盖：`sa_approval_request` 持久化字段落库。

```powershell
.\mvnw '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

覆盖：JDBC 审批仓储自动配置、Tool Gateway 注入审批仓储、审批场景真实工具不执行。

## 7. 已知风险

- `ApprovalRequest.riskLevel` 当前由 Gateway 使用保守默认 `HIGH`，后续应从 catalog/policy decision 中携带真实风险等级。
- `argumentsPreviewJson` 当前只做参数 key/count/resourceRefs 预览，足够避免保存完整敏感入参，但还不是完整脱敏引擎。
- 没有 checkpoint 时，审批请求只能展示和记录，不能恢复执行。
- broad `seahorse-agent-tests` 套件此前存在非本切片的 runtime classpath 问题，本切片只用 owner-boundary 聚焦测试收敛。

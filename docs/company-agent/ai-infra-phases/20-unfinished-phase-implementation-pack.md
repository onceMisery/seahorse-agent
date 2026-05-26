# 2026-05-26 AI Infra 未完成阶段实施包

本文是在重新阅读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、架构基线、测试基线、差距分析、两篇企业 Agent 背景文章、`99-current-implementation-handoff.md` 和 `09-unfinished-phase-design-development-plans.md` 后形成的最新实施入口。

旧交接文档里的 Approval query/decision API 判断已经过时。当前未完成重点不是 Phase 3 审批 API，而是把已经出现的 Agent Runtime、Factory、Audit、Connector、Resource ACL、Sandbox 基础能力推进到生产治理闭环。

本文件只补设计开发方案，不代表实现完成。Phase 0-3 与 Phase 6 当前作为依赖和回归范围处理，不再重复补“未完成阶段”方案。仍需补实施方案的阶段为 Phase 5、Phase 4、Phase 7、Phase 8B、Phase 8C、Phase 8D。

## 1. 最新执行顺序

| 顺序 | 阶段 | 目标切片 | 完成信号 |
| --- | --- | --- | --- |
| 1 | Phase 5 | Connector/Sandbox Security Closure | connector 与 sandbox 的审计、脱敏、凭据绑定、starter 注入和 focused regression 通过。 |
| 2 | Phase 4 | Resource ACL Import Commit + Audit Closure | dry-run 到 commit 一致、fail-closed、ACL 变更审计、DB enum 约束和 Context access 审计可验证。 |
| 3 | Phase 7 | Governed Local Agent-as-Tool | parent run 经 Tool Gateway 触发 child run，handoff 可查可取消，context handoff 重新授权。 |
| 4 | Phase 8B | Agent Eval Summary Gate | high-risk Agent 缺 eval fail closed，low-risk 缺 eval warn，eval summary snapshot 进入 Production Gate。 |
| 5 | Phase 8C | Quota/Cost/SRE Health | quota decision 不 silent allow，cost usage append-only，health contributor 异常至少 warn。 |
| 6 | Phase 8D | Canary/Pilot Gate | promote 前强制 gate，rollback 复用 Phase 6 activation owner，pilot readiness 覆盖九项检查。 |

共同实现边界：

1. 每个阶段先补 RED 测试并记录失败，再做最小 GREEN。
2. Kernel 只依赖 domain、ports、JDK 和同层 application service；不得依赖 Spring、JDBC、Web 或 HTTP client。
3. 所有 status、type、effect、risk、source、failure code、check code、event type 均使用 enum 或具名常量。
4. Repository port 保持小接口；Definition、Run、Tool、Policy、Approval、Connector、Context、Eval、Quota、Rollout 不合并成大服务。
5. audit、eval、cost、rollout、pilot report 使用 append-only 或 snapshot 语义；修正通过新记录表达。
6. 不引入工作流引擎、动态策略语言、真实容器平台、远程 A2A、Agent Card 或远程 Agent mesh。

## 2. Phase 5 实施方案：Connector/Sandbox Security Closure

### 2.1 目标

把外部系统入口从“可导入、可运行基础能力”推进到“可审计、可禁用、凭据引用安全、沙箱执行脱敏”的生产安全底线。

完成后必须满足：

1. sandbox session create 与 terminal execution 均写 audit event。
2. sandbox audit payload 不包含 raw input、secret、token、完整命令参数或 artifact 原文。
3. Spring starter 默认装配 `KernelAuditLedgerService` 到 `KernelSandboxRuntimeService`。
4. connector import、credential bind、operation enable、operation disable 都写 audit event。
5. 高风险 operation enable 必须有 approval policy 或显式确认字段；默认导入仍为 disabled。
6. credential binding replacement 只旋转 active binding，不覆盖历史绑定。

### 2.2 文件边界

| 层 | 文件 | 改动 |
| --- | --- | --- |
| Kernel test | `KernelSandboxRuntimeServiceTests.java` | 增加 RED：session/execution audit event、payload redaction、terminal failure audit。 |
| Kernel | `KernelSandboxRuntimeService.java` | 组合可选 `KernelAuditLedgerService`，在 save session 和 execution terminal 分支写 audit。 |
| Starter test | `SeahorseAgentSandboxAutoConfigurationTests.java` | 增加 RED：默认 `KernelAuditLedgerService` bean 注入 sandbox runtime。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration.java` | 用 `ObjectProvider<KernelAuditLedgerService>` 装配 sandbox service。 |
| Kernel test | `KernelOpenApiConnectorImportServiceTests.java` | 补齐 binding rotation、high-risk enable gate、connector audit redaction 断言。 |
| Kernel domain | `ConnectorCredentialBinding.java`、`ConnectorOperation.java` | 确认 active/disabled/high-risk 不变量在领域对象内，不复制到 controller。 |
| JDBC test | `JdbcConnectorCredentialBindingRepositoryAdapterTests.java`、`JdbcSandboxRepositoryAdapterTests.java` | 验证 active binding 查询、历史保留、sandbox terminal status 映射。 |
| Web test | `SeahorseOpenApiConnectorControllerTests.java`、`SeahorseSandboxControllerTests.java` | 验证 API 只暴露 secretRef、bindingId、sessionId、executionId。 |

### 2.3 第一批 RED 测试

1. `KernelSandboxRuntimeServiceTests#shouldWriteRedactedAuditEventsForSessionAndTerminalExecution`
   - `SANDBOX_SESSION_CREATED` 在 create session 后出现。
   - `SANDBOX_EXECUTION_FINISHED` 在 success、denied、runtime failure 三类 terminal 分支出现。
   - audit payload 不包含输入样例中的 `secret-token`、raw command、raw stdout。
2. `SeahorseAgentSandboxAutoConfigurationTests#shouldWireAuditLedgerIntoSandboxRuntime`
   - starter context 中 sandbox service 的 `auditLedger` 字段与 `KernelAuditLedgerService` bean 相同。
3. `KernelOpenApiConnectorImportServiceTests#shouldRequireApprovalEvidenceBeforeEnablingHighRiskOperation`
   - `WRITE`、`DELETE`、`EXTERNAL_SEND` 或 `HIGH/CRITICAL` operation 无审批证据时 enable 失败。
4. `KernelOpenApiConnectorImportServiceTests#shouldRotateCredentialBindingWithoutDeletingHistory`
   - 新 binding active 后旧 binding 为 rotated/replaced 状态，历史仍可查。
5. `KernelOpenApiConnectorImportServiceTests#shouldWriteRedactedConnectorAuditEvents`
   - audit event 只包含 connectorId、operationId、bindingId、secretRef、risk、status，不包含 secret material。

### 2.4 最小 GREEN 顺序

1. 在 sandbox service 增加可选 audit ledger 构造参数，保留旧构造器委托，避免破坏已有测试。
2. 增加私有方法 `appendSessionAudit`、`appendExecutionAudit`，只接受领域快照和统计字段，不接收 raw request input。
3. starter wiring 增加 `ObjectProvider<KernelAuditLedgerService>`，没有 audit ledger 时 service 仍可运行，但 Phase 5 验收不能声称 audit closure。
4. connector enable 分支复用现有 risk enum 和 approval evidence，不新增字符串风险判断。
5. credential binding replacement 通过 repository 查询 active binding 后逐个 rotate，再保存新 binding；同一 connector/operation 同时只允许一个 active binding。
6. connector audit payload 通过小型私有 builder 生成，字段白名单固定，避免 controller 和 service 分别拼 payload。

### 2.5 审计 payload 合同

| 事件 | resourceType | payload 字段白名单 |
| --- | --- | --- |
| `SANDBOX_SESSION_CREATED` | `SANDBOX_SESSION` | `sessionId`、`runtimeType`、`networkPolicy`、`status`、`createdAt`。 |
| `SANDBOX_EXECUTION_FINISHED` | `SANDBOX_EXECUTION` | `sessionId`、`executionId`、`runtimeType`、`status`、`reasonCode`、`artifactCount`。 |
| `CONNECTOR_IMPORTED` | `CONNECTOR` | `connectorId`、`provider`、`operationCount`、`defaultStatus`。 |
| `CONNECTOR_OPERATION_ENABLED` | `CONNECTOR_OPERATION` | `connectorId`、`operationId`、`toolId`、`riskLevel`、`approvalEvidenceRef`。 |
| `CONNECTOR_CREDENTIAL_BOUND` | `CONNECTOR_CREDENTIAL_BINDING` | `connectorId`、`operationId`、`bindingId`、`secretRef`、`bindingStatus`。 |

### 2.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelSandboxRuntimeServiceTests,KernelOpenApiConnectorImportServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-mcp-http -am '-Dtest=McpHttpOAuthCredentialTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcConnectorRepositoryAdapterTests,JdbcConnectorCredentialBindingRepositoryAdapterTests,JdbcSandboxRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseOpenApiConnectorControllerTests,SeahorseSandboxControllerTests,SeahorseAuditEventControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentCredentialAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests,SeahorseAgentSandboxAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

### 2.7 回滚与禁止事项

回滚时可关闭 sandbox controller 或 connector enable API，但保留 connector、binding、sandbox 和 audit 历史。不得把 secret 明文写入数据库、audit、日志、异常消息或 test expected string。不得在 kernel 中实现真实 HTTP connector execution 或本地任意脚本执行。

## 3. Phase 4 实施方案：Resource ACL Import Commit、Audit 与 DB Hardening

### 3.1 目标

把 ACL 从“可手工管理和 dry-run”推进到“可批量导入、可追责、可由数据库兜住状态语义”。本阶段不扩展 ACL 表达能力，不重写 ContextPack。

完成后必须满足：

1. import commit 复用 dry-run 判定；默认 `FAIL_ON_INVALID` 不产生部分写入。
2. `VALID_ONLY` 模式只写入 dry-run `VALID` item，并返回 skipped reason counts。
3. create、disable、import commit 写 `RESOURCE_ACL_CHANGED` audit。
4. Context access 写 `CONTEXT_ACCESSED` audit，payload 只包含 resource ref、effect、reason、decisionId。
5. DB 层对 status、scope、effect、action、subjectType 增加 check constraint 或适配器级 enum mapping 失败测试。
6. admin role、default page size、dry-run max batch size 使用具名常量。

### 3.2 文件边界

| 层 | 文件 | 改动 |
| --- | --- | --- |
| Domain | `ResourceAclImportMode.java` | 新增 `FAIL_ON_INVALID`、`VALID_ONLY`。 |
| Domain | `ResourceAclImportResult.java` | 新增 commit summary snapshot。 |
| Domain | `ResourceAclAuthorizationRoles.java`、`ResourceAclImportLimits.java` | 收敛 `admin`、batch size、默认分页。 |
| Inbound | `ResourceAclImportCommand.java` | 包含 items、mode、operatorId。 |
| Inbound | `ResourceAclManagementInboundPort.java` | 增加 `importRules`。 |
| Application | `KernelResourceAclManagementService.java` | dry-run reuse、commit、audit、fail-closed transaction orchestration。 |
| Application | `AuditedResourceAccessPolicyPort.java` | 只包装 audit，不复制 ACL 决策。 |
| JDBC | `JdbcResourceAclRepositoryAdapter.java` | 批量保存或循环保存的事务语义和 enum mapping。 |
| SQL | `agent-registry-run-store-postgresql.sql` | ACL check constraints、natural key index。 |
| Web | `SeahorseResourceAclController.java` | 新增 `POST /api/resource-acl-rules:import`。 |

### 3.3 第一批 RED 测试

1. `KernelResourceAclManagementServiceTests#shouldFailClosedImportWhenAnyItemIsInvalid`
   - 输入包含 valid 和 invalid item。
   - repository 没有保存任何规则。
   - reason 使用 `ResourceAclImportReasonCode`。
2. `KernelResourceAclManagementServiceTests#shouldCommitOnlyValidItemsWhenModeIsValidOnly`
   - 输入 valid、duplicate、unsupported scope。
   - createdCount、skippedCount、reasonCounts 与 dry-run 一致。
3. `KernelResourceAclManagementServiceTests#shouldWriteAuditForCreateImportAndDisable`
   - 三个事件均为 `RESOURCE_ACL_CHANGED`，payload 不包含原始批量导入全文。
4. `AuditedResourceAccessPolicyPortTests#shouldWriteContextAccessAuditWithoutChangingDecision`
   - fake delegate 返回 allow/deny/mask，wrapper 不修改 effect 和 reason。
5. `JdbcResourceAclRepositoryAdapterTests#shouldRejectUnknownEnumValuesAtDatabaseBoundary`
   - 未知 status/effect/action 不会被静默落库。
6. `SeahorseResourceAclControllerTests#shouldExposeDryRunAndCommitImportSeparately`
   - dry-run 不写库，commit 才写库。

### 3.4 最小 GREEN 顺序

1. 把 service 中重复的 item validation 抽到 `dryRun(command)` 可复用私有方法或领域 helper，commit 不再复制判断。
2. `FAIL_ON_INVALID` 在保存前检查 report，不满足时直接返回失败结果或抛领域异常，repository 不被调用。
3. `VALID_ONLY` 从 dry-run report 过滤 `VALID` items，再调用 `ResourceAclRule` factory，保存后写一条 summary audit。
4. audit payload 使用 reason counts 和 ruleIds 白名单，不写原始 CSV、JSON 原文或 subject 扩展属性。
5. SQL 约束追加到现有表后面，避免重建 context/access decision 表。
6. controller 只做 DTO 到 command/query 的转换，不能内嵌 ACL 业务判断。

### 3.5 API 与数据合同

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/resource-acl-rules:dry-run-import` | 返回每条 item status、reasonCode、naturalKey，不写库。 |
| `POST` | `/api/resource-acl-rules:import` | 默认 fail closed；`mode=VALID_ONLY` 时只写 valid items。 |
| `POST` | `/api/resource-acl-rules/{ruleId}/disable` | 幂等 disable，写 audit。 |

`ResourceAclImportResult` 必须包含 `dryRunReport`，让调用方能解释 skipped/failed，不需要再重新 dry-run。

### 3.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,AuditedResourceAccessPolicyPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseResourceAclControllerTests,SeahorseAuditEventControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

### 3.7 回滚与禁止事项

回滚时关闭 import commit API，保留 dry-run、create、disable 和 access decision 查询。不得物理删除已导入规则；错误导入通过 disable 或追加新规则修正。不得把 ACL 规则语言升级成通用策略引擎。

## 4. Phase 7 实施方案：Governed Local Agent-as-Tool 与 Handoff

### 4.1 目标

实现本地 Agent-as-Tool 最小闭环：一个已发布 Agent 可以作为另一个 Agent 的受治理工具被调用，调用链必须经过 Tool Gateway、Policy、Context ACL、Run Store 和 Audit。

完成后必须满足：

1. child run 由 `AgentRunInboundPort` 或专用 run creation port 创建，handoff 不复制 run 事实。
2. `LOCAL_AGENT` provider 的 tool entry 默认 disabled，管理员启用后才可调用。
3. handoff depth 和 cycle 使用具名常量与 enum reason code。
4. context handoff 默认只传 summary 和 citation，不传 private memory、secret item、raw tool result。
5. handoff lifecycle 写 audit，可按 parentRunId 查询，可幂等取消未完成 handoff。

### 4.2 文件边界

| 层 | 文件 | 改动 |
| --- | --- | --- |
| Domain | `AgentHandoff.java` | handoff 状态机和 parent/child/source/target 不变量。 |
| Enum | `AgentHandoffStatus.java` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。 |
| Enum | `AgentHandoffFailureCode.java` | `DEPTH_LIMIT_EXCEEDED`、`CYCLE_DETECTED`、`POLICY_DENIED`、`TARGET_DISABLED`、`CONTEXT_DENIED`。 |
| Constants | `AgentHandoffLimits.java` | `MAX_LOCAL_HANDOFF_DEPTH`、input/context summary length。 |
| Domain | `AgentHandoffContextPolicy.java` | context transfer rules and redaction summary。 |
| Outbound | `AgentHandoffRepositoryPort.java` | save、findById、listByParentRunId、updateTerminal。 |
| Outbound | `MeshPolicyPort.java` | source-to-target permission decision。 |
| Application | `KernelAgentHandoffService.java` | create child run、persist handoff、audit lifecycle、cancel。 |
| Application | `LocalAgentAsToolPort.java` | Tool Gateway adapter，不能被 Web 直接创建 handoff 绕过。 |
| JDBC | `JdbcAgentHandoffRepositoryAdapter.java` | parent list、status update、terminal idempotency。 |
| Web | `SeahorseAgentHandoffController.java` | 只开放 query/cancel，不开放 direct create。 |

### 4.3 第一批 RED 测试

1. `AgentHandoffTests#shouldKeepTerminalStatusImmutable`
2. `AgentHandoffContextPolicyTests#shouldStripSecretPrivateAndRawToolResultContext`
3. `DefaultMeshPolicyPortTests#shouldDenyDepthOverflowAndCycles`
4. `LocalAgentAsToolPortTests#shouldCreateChildRunOnlyThroughRunPort`
5. `KernelAgentHandoffServiceTests#shouldCancelPendingHandoffIdempotently`
6. `SeahorseAgentHandoffControllerTests#shouldListAndCancelHandoffButRejectDirectCreate`

### 4.4 调用流

```text
Parent Agent run
  -> ToolGateway.invoke(toolProvider=LOCAL_AGENT)
  -> PolicyEngine.decide(sourceAgent, targetAgent, action=EXECUTE)
  -> LocalAgentAsToolPort.invoke
  -> MeshPolicyPort.decide(depth, cycle, target status, context policy)
  -> AgentHandoffContextPolicy.reduce(ContextPack)
  -> AgentRunInboundPort.startRun(child agent, reduced input)
  -> KernelAgentHandoffService.save(handoff with childRunId)
  -> Audit Ledger append LOCAL_AGENT_HANDOFF event
```

### 4.5 数据库与 API

`sa_agent_handoff` 首版字段：

```sql
handoff_id, tenant_id, parent_run_id, child_run_id,
source_agent_id, target_agent_id, status, failure_code,
handoff_reason, input_summary_json, context_summary_json,
created_at, updated_at, finished_at
```

API：

| Method | Path | 行为 |
| --- | --- | --- |
| `GET` | `/api/agent-runs/{runId}/handoffs` | 按 createdAt 升序返回 parent run 下 handoff。 |
| `GET` | `/api/agent-handoffs/{handoffId}` | 返回 handoff snapshot，不返回 raw child prompt。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 幂等取消未完成 handoff。 |

### 4.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,AgentHandoffContextPolicyTests,DefaultMeshPolicyPortTests,LocalAgentAsToolPortTests,KernelAgentHandoffServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

### 4.7 回滚与禁止事项

回滚时 unregister `LOCAL_AGENT` tool provider，保留 handoff 和 child run 历史用于审计。不得实现远程 A2A、Agent Card、mesh routing、debate、supervisor DAG。不得开放 Web direct create 绕过 Tool Gateway。

## 5. Phase 8B 实施方案：Agent Eval Summary Gate

### 5.1 目标

把已有 retrieval/memory eval 或人工导入结果变成 Agent publish/canary gate 可读取的 snapshot。首版只做 summary ingestion 和 gate decision，不做 eval runner。

完成后必须满足：

1. 一个 Agent version 可保存多类型 eval summary。
2. latest summary 按 tenant、agent、version、evalType 隔离。
3. `FAIL` 阻断 gate；`STALE` 对 high-risk 视为 fail，对 low-risk 视为 warn。
4. high-risk Agent 或绑定写工具的 Agent 缺 eval fail closed。
5. low-risk read-only Agent 缺 eval warn，不 silent pass。

### 5.2 文件边界

| 层 | 文件 | 改动 |
| --- | --- | --- |
| Domain | `AgentEvalSummary.java` | summary snapshot，不保存 raw eval cases。 |
| Enum | `AgentEvalType.java` | `RAG`、`MEMORY`、`TRAJECTORY`、`SAFETY`、`HITL`、`COST`。 |
| Enum | `AgentEvalStatus.java` | `PASS`、`WARN`、`FAIL`、`STALE`。 |
| Constants | `AgentEvalDefaults.java` | `MAX_SUMMARY_AGE`、default threshold。 |
| Outbound | `AgentEvalSummaryRepositoryPort.java` | save、findLatest、listByVersion。 |
| Outbound | `AgentEvalStatusPort.java` | Production Gate 只依赖这个小 port。 |
| Application | `KernelAgentEvalQueryService.java` | save/latest/history。 |
| Application | `KernelProductionGateService.java` | 聚合 eval status，不拥有 eval facts。 |
| JDBC | `JdbcAgentEvalSummaryRepositoryAdapter.java` | latest query 和 version history。 |
| Web | `SeahorseAgentEvalController.java` | summary save/latest/history API。 |

### 5.3 第一批 RED 测试

1. `AgentEvalSummaryTests#shouldRejectNegativeScoreAndInvalidThreshold`
2. `AgentEvalSummaryTests#shouldMarkSummaryStaleByNamedMaxAge`
3. `KernelAgentEvalQueryServiceTests#shouldSaveLatestAndHistorySnapshots`
4. `KernelProductionGateServiceTests#shouldFailHighRiskAgentWithoutEval`
5. `KernelProductionGateServiceTests#shouldWarnLowRiskReadOnlyAgentWithoutEval`
6. `KernelProductionGateServiceTests#shouldBlockWhenLatestEvalFails`
7. `JdbcAgentEvalSummaryRepositoryAdapterTests#shouldFindLatestByTenantAgentVersionAndType`
8. `SeahorseAgentEvalControllerTests#shouldSaveAndReadLatestSummary`

### 5.4 API 与表

```sql
CREATE TABLE sa_agent_eval_summary (
  summary_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  eval_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  score DOUBLE,
  threshold DOUBLE,
  dataset_ref VARCHAR(128),
  eval_run_ref VARCHAR(128),
  created_at TIMESTAMP NOT NULL
);
```

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries` | 保存 snapshot。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries/latest?evalType=SAFETY` | 查询 latest。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries` | 查询历史。 |

### 5.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentEvalSummaryTests,KernelAgentEvalQueryServiceTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentEvalSummaryRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentEvalControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

### 5.6 回滚与禁止事项

回滚时移除 eval status port 注入，Production Gate 对 eval 缺失返回明确 warn/fail，不能伪造 pass。不得新增 eval runner、dataset editor、红队执行器或重型异步任务平台。

## 6. Phase 8C 实施方案：Quota、Cost Usage 与 SRE Health

### 6.1 目标

建立最小运行治理证据：quota decision、cost usage append-only、SRE health 聚合。首版只做 policy 和查询，不做真实扣费或分布式限流。

完成后必须满足：

1. quota policy 支持 tenant、agent、user、tool、model、run scope。
2. 无 policy 时 high-risk 不 silent allow。
3. cost usage 按 tenant/agent/run 聚合 token、cost、call count。
4. health 聚合中 `RED` 优先于 `WARN`，`WARN` 优先于 `GREEN`。
5. contributor exception 产生 `WARN` evidence，不会被吞掉成 green。

### 6.2 文件边界

| 层 | 文件 | 改动 |
| --- | --- | --- |
| Domain | `QuotaPolicy.java` | scope、window、limits、effect 不变量。 |
| Enum | `QuotaScopeType.java` | `TENANT`、`AGENT`、`USER`、`TOOL`、`MODEL`、`RUN`。 |
| Enum | `QuotaDecisionEffect.java` | `ALLOW`、`WARN`、`DENY`、`REQUIRE_APPROVAL`。 |
| Constants | `QuotaDefaults.java` | no-policy low/high-risk behavior。 |
| Domain | `CostUsageRecord.java` | append-only usage record。 |
| Enum | `CostUsageSource.java` | `MODEL_CALL`、`TOOL_CALL`、`SANDBOX_EXECUTION`、`REMOTE_AGENT_CALL`。 |
| Domain | `SreHealthSnapshot.java` | health status 和 contributor item。 |
| Enum | `SreHealthStatus.java` | `GREEN`、`WARN`、`RED`。 |
| Outbound | `QuotaPolicyRepositoryPort.java`、`QuotaDecisionPort.java`、`CostUsageRepositoryPort.java`、`SreHealthContributorPort.java` | 小接口隔离。 |
| Application | `KernelQuotaDecisionService.java`、`KernelCostUsageQueryService.java`、`KernelSreHealthQueryService.java` | 编排，不持有 adapter 细节。 |
| JDBC | `JdbcQuotaPolicyRepositoryAdapter.java`、`JdbcCostUsageRepositoryAdapter.java` | upsert、append、aggregate。 |
| Web | `SeahorseQuotaController.java`、`SeahorseCostUsageController.java`、`SeahorseSreHealthController.java` | 管理和查询 API。 |

### 6.3 第一批 RED 测试

1. `QuotaPolicyTests#shouldRejectPolicyWithoutAnyLimit`
2. `QuotaPolicyTests#shouldRejectNegativeLimits`
3. `KernelQuotaDecisionServiceTests#shouldWarnLowRiskWhenNoPolicy`
4. `KernelQuotaDecisionServiceTests#shouldRequireApprovalForHighRiskWhenNoPolicy`
5. `KernelQuotaDecisionServiceTests#shouldDenyWhenHardLimitExceeded`
6. `CostUsageRecordTests#shouldRejectNegativeTokenOrCost`
7. `KernelCostUsageQueryServiceTests#shouldAggregateUsageByTenantAgentAndRun`
8. `KernelSreHealthQueryServiceTests#shouldPrioritizeRedThenWarnThenGreen`
9. `KernelSreHealthQueryServiceTests#shouldWarnWhenContributorThrows`

### 6.4 API 与表

```sql
CREATE TABLE sa_quota_policy (
  policy_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  scope_type VARCHAR(32) NOT NULL,
  scope_id VARCHAR(128) NOT NULL,
  window_type VARCHAR(32) NOT NULL,
  token_limit BIGINT,
  cost_limit DECIMAL(18, 6),
  call_limit BIGINT,
  effect VARCHAR(32) NOT NULL,
  enabled BOOLEAN NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE sa_cost_usage_record (
  usage_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  run_id VARCHAR(64),
  source VARCHAR(32) NOT NULL,
  model VARCHAR(128),
  tool_id VARCHAR(128),
  input_tokens BIGINT NOT NULL,
  output_tokens BIGINT NOT NULL,
  cost_amount DECIMAL(18, 6) NOT NULL,
  occurred_at TIMESTAMP NOT NULL
);
```

| Method | Path | 行为 |
| --- | --- | --- |
| `PUT` | `/api/quotas/{scopeType}/{scopeId}` | upsert quota policy。 |
| `GET` | `/api/quotas/{scopeType}/{scopeId}` | 查询 quota policy。 |
| `GET` | `/api/cost/usage?tenantId=...&agentId=...&runId=...` | 聚合 usage。 |
| `GET` | `/api/sre/health` | 聚合 health snapshot。 |

### 6.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelCostUsageQueryServiceTests,KernelSreHealthQueryServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseQuotaControllerTests,SeahorseCostUsageControllerTests,SeahorseSreHealthControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

### 6.6 回滚与禁止事项

回滚时移除 quota decision 注入点，保留 cost usage 只读记录。不做账单结算、复杂滑动窗口、分布式限流、前端大屏或自动暂停 run；runtime 暂停可在后续小切片接入。

## 7. Phase 8D 实施方案：Canary Rollout 与 Enterprise Pilot Readiness

### 7.1 目标

建立 Agent version canary、promote、pause、rollback 和企业试点准入报告。Rollout 只记录事实和门禁结果，不做真实流量路由。

完成后必须满足：

1. canary percent 由 `AgentRolloutLimits` 限制。
2. promote 前读取 latest Production Gate report；缺失或非 pass 均 fail closed。
3. rollback 复用 Phase 6 `AgentVersionActivationRepositoryPort`。
4. terminal rollout status 不可回退。
5. pilot readiness 覆盖 owner、published version、tool risk、resource ACL、eval、quota、audit、rollback、disable switch。
6. report 只保存 check result 和 evidence refs，不复制 prompt、eval case、raw tool output。

### 7.2 文件边界

| 层 | 文件 | 改动 |
| --- | --- | --- |
| Domain | `AgentVersionRollout.java` | rollout 状态机和 percent 不变量。 |
| Enum | `AgentRolloutStatus.java` | `CREATED`、`RUNNING`、`PAUSED`、`PROMOTED`、`ROLLED_BACK`、`FAILED`。 |
| Enum | `AgentRolloutFailureCode.java` | `GATE_FAILED`、`GATE_MISSING`、`INVALID_PERCENT`、`ROLLBACK_FAILED`。 |
| Constants | `AgentRolloutLimits.java` | `MIN_PERCENT`、`MAX_PERCENT`、`DEFAULT_CANARY_PERCENT`。 |
| Domain | `EnterprisePilotReadinessReport.java` | 准入报告 snapshot。 |
| Enum | `EnterprisePilotReadinessCheckCode.java` | 九项检查 code。 |
| Outbound | `AgentRolloutRepositoryPort.java`、`EnterprisePilotReadinessRepositoryPort.java` | rollout/report 持久化。 |
| Application | `KernelAgentRolloutService.java` | canary、pause、promote、rollback。 |
| Application | `KernelEnterprisePilotReadinessService.java` | 聚合 readiness checks。 |
| JDBC | `JdbcAgentRolloutRepositoryAdapter.java`、`JdbcEnterprisePilotReadinessRepositoryAdapter.java` | 状态更新、latest report。 |
| Web | `SeahorseAgentRolloutController.java`、`SeahorseEnterprisePilotReadinessController.java` | API。 |

### 7.3 第一批 RED 测试

1. `AgentVersionRolloutTests#shouldRejectCanaryPercentOutsideLimits`
2. `AgentVersionRolloutTests#shouldKeepTerminalStatusImmutable`
3. `KernelAgentRolloutServiceTests#shouldFailPromoteWhenGateReportMissing`
4. `KernelAgentRolloutServiceTests#shouldFailPromoteWhenGateReportIsNotPass`
5. `KernelAgentRolloutServiceTests#shouldRollbackThroughVersionActivationPort`
6. `EnterprisePilotReadinessReportTests#shouldRequireAllCheckCodes`
7. `KernelEnterprisePilotReadinessServiceTests#shouldFailWhenHighRiskRequiredEvidenceMissing`
8. `KernelEnterprisePilotReadinessServiceTests#shouldWarnButNotPassForLowRiskMissingEvidence`
9. `SeahorseAgentRolloutControllerTests#shouldExposeCanaryPausePromoteRollback`

### 7.4 Pilot readiness 检查合同

| CheckCode | PASS 条件 | 缺失行为 |
| --- | --- | --- |
| `OWNER` | owner 和 fallback owner 均存在。 | 高风险 fail，低风险 warn。 |
| `PUBLISHED_VERSION` | version 已发布且未禁用。 | fail。 |
| `TOOL_RISK` | 高风险工具有 approval policy 或明确 disabled。 | fail。 |
| `RESOURCE_ACL` | 绑定资源有 ACL 证据。 | 高风险 fail，低风险 warn。 |
| `EVAL` | latest eval 非 fail，高风险不可 stale。 | 高风险 fail，低风险 warn。 |
| `QUOTA` | tenant 或 agent quota policy 存在。 | 高风险 fail，低风险 warn。 |
| `AUDIT` | audit repository 可查询关键事件。 | fail。 |
| `ROLLBACK` | 有 activation history 或可回滚版本。 | fail。 |
| `DISABLE_SWITCH` | agent 与高风险 tool 均可禁用。 | fail。 |

### 7.5 API 与表

```sql
CREATE TABLE sa_agent_version_rollout (
  rollout_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  canary_percent INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64),
  gate_report_id VARCHAR(64),
  started_by VARCHAR(64) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);

CREATE TABLE sa_enterprise_pilot_readiness_report (
  report_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  check_results_json TEXT NOT NULL,
  evidence_refs_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL
);
```

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/canary` | 创建 rollout。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停 rollout。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | gate pass 后推广。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | 复用 activation rollback。 |
| `POST` | `/api/agents/{agentId}/pilot-readiness` | 生成 readiness report。 |
| `GET` | `/api/agents/{agentId}/pilot-readiness/latest` | 查询 latest report。 |

### 7.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,KernelAgentRolloutServiceTests,EnterprisePilotReadinessReportTests,KernelEnterprisePilotReadinessServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests,JdbcAgentVersionActivationRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

### 7.7 回滚与禁止事项

回滚时停用 rollout 和 pilot readiness API，保留历史 report。普通 Phase 6 publish/rollback 不受影响。不得实现真实流量路由器、百分比分流器、前端发布向导或第二套 version activation owner。

## 8. 完成判定

本文件只完成未完成阶段的设计开发方案补充。后续实现完成至少需要：

1. Phase 5、Phase 4、Phase 7、Phase 8B、Phase 8C、Phase 8D 的 RED/GREEN 证据全部记录在 Aegis evidence 中。
2. 每个新增 API 均有 Web contract test。
3. 每个新增 repository 均有 JDBC adapter test。
4. 每个新增 starter wiring 均有 auto-configuration test。
5. audit payload redaction 覆盖 connector、sandbox、ACL、handoff、eval、quota、rollout、pilot readiness。
6. `git diff --check` 无错误。
7. kernel 依赖扫描不出现新增 Spring、JDBC、Web、HTTP client 依赖。
8. 未完成阶段的禁用开关、回滚边界和证据查询路径均可被测试证明。

## 9. 2026-05-26 深读补充：每个未完成阶段的具体开发方案

本节是在再次深读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、架构基线、测试基线、差距分析和企业 Agent 背景文章后追加的更细实施方案。它不扩大未完成阶段范围：Phase 0-3 与 Phase 6 继续作为依赖和回归范围处理，当前仍需实现的阶段保持为 Phase 5、Phase 4、Phase 7、Phase 8B、Phase 8C、Phase 8D。

共同落地约束：

1. 每个阶段先写最小 RED 测试，记录失败，再做 GREEN。
2. service 只编排，领域对象维护状态机和不变量，repository port 只表达持久化契约。
3. Web controller 只做 DTO 到 command/query 的转换，不复制领域判断。
4. 新增状态、原因、风险、事件、检查项全部用 enum 或具名常量。
5. audit、cost、eval、readiness 只存摘要、证据引用和白名单 payload，不存 prompt、secret、raw tool output、raw context。

### 9.1 Phase 5 具体方案：Connector 与 Sandbox 安全收口

#### 9.1.1 当前差距

Phase 5 已有 MCP OAuth、OpenAPI connector、credential binding、sandbox foundation 的实现痕迹；剩余风险集中在外部系统入口是否真的可运营：operation enable/disable 是否受风险门禁约束，credential binding replacement 是否保留历史，sandbox session/execution 是否都有脱敏审计，starter 默认 wiring 是否把 Audit Ledger 注入这些服务。

#### 9.1.2 领域与端口细化

新增或补齐以下领域语义，优先复用现有 connector/sandbox 类型：

| 对象或 enum | 责任 |
| --- | --- |
| `ConnectorOperationEnableEvidence` | 管理员启用 operation 的证据快照，包含 operatorId、approvalEvidenceRef、riskAcknowledged。 |
| `ConnectorOperationEnableFailureCode` | `APPROVAL_EVIDENCE_REQUIRED`、`OPERATION_NOT_FOUND`、`CONNECTOR_DISABLED`。 |
| `ConnectorCredentialBindingStatus` | 至少包含 `ACTIVE`、`REPLACED`、`DISABLED`，replacement 不能覆盖历史行。 |
| `ConnectorAuditPayloadFactory` | 只生成白名单 payload，避免 service/controller 多处拼接审计 JSON。 |
| `SandboxAuditPayloadFactory` | 只允许 sessionId、executionId、runtimeType、status、reasonCode、artifactCount。 |

port 侧补齐小接口，而不是扩展成大 connector service：

```text
ConnectorOperationManagementPort
  enableOperation(ConnectorOperationEnableCommand command) -> ConnectorOperation
  disableOperation(ConnectorOperationDisableCommand command) -> ConnectorOperation

ConnectorCredentialBindingRepositoryPort
  findActiveBinding(connectorId, operationId) -> Optional<ConnectorCredentialBinding>
  replaceActiveBinding(newBinding) -> ConnectorCredentialBinding
  listBindings(connectorId, operationId) -> List<ConnectorCredentialBinding>

SandboxRuntimeInboundPort
  createSession(SandboxSessionCreateCommand command) -> SandboxSession
  execute(SandboxExecutionCommand command) -> SandboxExecution
```

#### 9.1.3 服务编排

1. `enableOperation` 先读取 operation 和 connector 状态；connector disabled 时直接拒绝。
2. 对 `WRITE`、`DELETE`、`EXTERNAL_SEND` 或 `HIGH/CRITICAL` 风险 operation，要求 `approvalEvidenceRef` 或显式 `riskAcknowledged`，缺失时返回具名 failure code。
3. 启用成功后写 `CONNECTOR_OPERATION_ENABLED` audit；禁用写 `CONNECTOR_OPERATION_DISABLED` audit。
4. credential binding replacement 在同一 repository 事务内把旧 active 标记为 `REPLACED`，再插入新 active。
5. sandbox create session 后写 `SANDBOX_SESSION_CREATED` audit；execution 进入 terminal 状态后写 `SANDBOX_EXECUTION_FINISHED` audit。
6. sandbox audit payload 不接收 raw command、stdin、stdout、stderr、artifact content，只接收领域摘要。

#### 9.1.4 API 细化

| Method | Path | 约束 |
| --- | --- | --- |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/enable` | body 只允许 `operatorId`、`approvalEvidenceRef`、`riskAcknowledged`。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/disable` | 幂等禁用，保留 binding 和 operation 历史。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/credential-bindings` | body 只收 `secretRef` 和 auth metadata，不收 secret material。 |
| `POST` | `/api/sandbox/sessions` | 返回 session snapshot，不返回环境变量和凭据。 |
| `POST` | `/api/sandbox/sessions/{sessionId}/execute` | 返回执行摘要和 artifact refs，不返回 raw artifact。 |

#### 9.1.5 TDD 顺序

1. RED：`KernelOpenApiConnectorImportServiceTests#shouldRejectHighRiskEnableWithoutApprovalEvidence`。
2. RED：`KernelOpenApiConnectorImportServiceTests#shouldRotateCredentialBindingWithoutDeletingHistory`。
3. RED：`KernelOpenApiConnectorImportServiceTests#shouldWriteRedactedConnectorAuditEvents`。
4. GREEN：实现 enable/disable 门禁、binding replacement、connector audit payload factory。
5. RED：`KernelSandboxRuntimeServiceTests#shouldWriteRedactedAuditEventsForSessionAndTerminalExecution`。
6. RED：`SeahorseAgentSandboxAutoConfigurationTests#shouldWireAuditLedgerIntoSandboxRuntime`。
7. GREEN：实现 sandbox audit wiring 和 starter 注入。
8. RED/GREEN：补 Web contract 和 JDBC adapter tests。

#### 9.1.6 阶段完成信号

Phase 5 只在以下证据都存在时才算收口：connector enable/disable、credential replacement、sandbox session/execution 的 focused tests 通过；audit payload redaction tests 通过；starter wiring tests 通过；`git diff --check` 无错误。若 Audit Ledger 未注入，功能可以运行但不能宣称 Phase 5 security closure。

### 9.2 Phase 4 具体方案：Resource ACL Import、Audit 与 DB 约束

#### 9.2.1 当前差距

Phase 4 已能管理 Resource ACL 和 Context access decision，但企业可解释性还缺三件事：批量导入从 dry-run 到 commit 的一致性、ACL 变更与 Context access 的统一审计、数据库边界对 enum/status 的兜底约束。

#### 9.2.2 单一规则来源

`KernelResourceAclManagementService` 必须把 import 校验收敛到一个私有 validation path：

```text
ResourceAclImportDryRunReport dryRun(ResourceAclImportDryRunCommand command)
ResourceAclImportResult importRules(ResourceAclImportCommand command)
```

`importRules` 不重新复制校验逻辑，只消费 dry-run item：

| mode | 行为 |
| --- | --- |
| `FAIL_ON_INVALID` | 只要存在非 `VALID` item，直接失败，不调用 repository。 |
| `VALID_ONLY` | 只保存 `VALID` item，返回 skipped reason counts。 |

#### 9.2.3 领域和常量

| 名称 | 内容 |
| --- | --- |
| `ResourceAclImportMode` | `FAIL_ON_INVALID`、`VALID_ONLY`。 |
| `ResourceAclImportItemStatus` | `VALID`、`DUPLICATE`、`INVALID_SCOPE`、`INVALID_SUBJECT`、`INVALID_ACTION`。 |
| `ResourceAclImportReasonCode` | 所有 dry-run 和 commit 跳过原因。 |
| `ResourceAclAuthorizationRoles.ADMIN_ROLE` | 替代字符串 `admin`。 |
| `ResourceAclImportLimits.MAX_BATCH_SIZE` | 替代批量限制魔法数。 |
| `ResourceAclQueryDefaults.DEFAULT_PAGE_SIZE` | 替代分页默认值魔法数。 |

#### 9.2.4 审计合同

| 事件 | 触发点 | payload 白名单 |
| --- | --- | --- |
| `RESOURCE_ACL_CHANGED` | create、disable、import commit | ruleId、ruleIds、mode、createdCount、skippedCount、reasonCounts、operatorId。 |
| `CONTEXT_ACCESSED` | 每次 resource access decision | decisionId、resourceType、resourceId、effect、reasonCode、subjectType、subjectId。 |

`CONTEXT_ACCESSED` 只能由 `AuditedResourceAccessPolicyPort` 包装产生；wrapper 必须保持 delegate decision 不变。

#### 9.2.5 DB 约束

在 `sa_resource_acl_rule` 增加 check constraints 或 adapter enum mapping fail-fast tests：

```sql
CHECK (status IN ('ENABLED', 'DISABLED', 'EXPIRED')),
CHECK (scope IN ('EXACT_RESOURCE', 'RESOURCE_TYPE')),
CHECK (effect IN ('ALLOW', 'DENY', 'MASK')),
CHECK (action IN ('READ', 'WRITE', 'DELETE', 'EXECUTE')),
CHECK (subject_type IN ('USER', 'AGENT', 'USER_DELEGATED_AGENT'))
```

如果当前 schema 字段名不同，使用现有字段名，但约束语义不能放宽。

#### 9.2.6 TDD 顺序

1. RED：fail-closed import 有 invalid item 时 repository 无保存调用。
2. RED：`VALID_ONLY` 只保存 valid item，并返回 reasonCounts。
3. RED：create/import/disable 都写 `RESOURCE_ACL_CHANGED`。
4. RED：Context access wrapper 写 `CONTEXT_ACCESSED`，且不改变 allow/deny/mask。
5. GREEN：实现 import mode、result、audit factory、wrapper wiring。
6. RED/GREEN：JDBC unknown enum value fail-fast 或 DB check constraint tests。
7. RED/GREEN：Web `POST /api/resource-acl-rules:import` contract。

#### 9.2.7 阶段完成信号

Phase 4 完成信号不是“ACL 能授权”，而是能回答两类问题：某条上下文为什么被 Agent 看到或拒绝；某条 ACL 规则是谁、何时、以什么导入模式变更的。错误导入通过 disable 或追加规则修正，不物理删除历史。

### 9.3 Phase 7 具体方案：Governed Local Agent-as-Tool

#### 9.3.1 当前差距

Phase 7 不能直接跳到远程 A2A 或 Agent Mesh。当前最小闭环是本地 Agent-as-Tool：parent Agent 经 Tool Gateway 调用 `LOCAL_AGENT` tool provider，平台创建 child run，并留下 handoff 事实、context 裁剪证据、policy decision 和 audit event。

#### 9.3.2 领域模型

| 对象或 enum | 字段或语义 |
| --- | --- |
| `AgentHandoff` | handoffId、tenantId、parentRunId、childRunId、sourceAgentId、targetAgentId、status、failureCode、reason、createdAt、finishedAt。 |
| `AgentHandoffStatus` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`；terminal status 不可回退。 |
| `AgentHandoffFailureCode` | `DEPTH_LIMIT_EXCEEDED`、`CYCLE_DETECTED`、`POLICY_DENIED`、`TARGET_DISABLED`、`CONTEXT_DENIED`、`CHILD_RUN_FAILED`。 |
| `AgentHandoffLimits` | `MAX_LOCAL_HANDOFF_DEPTH`、`MAX_INPUT_SUMMARY_LENGTH`、`MAX_CONTEXT_ITEM_COUNT`。 |
| `AgentHandoffContextPolicy` | 默认只传 summary、citation 和授权 resource refs。 |

#### 9.3.3 Context handoff 规则

默认策略必须 fail closed：

1. `SECRET` sensitivity 不传。
2. private memory 不传，除非目标 Agent 有 delegated access 证据。
3. raw tool result 不传，只允许摘要和 artifact/resource refs。
4. 每个 resource ref 在 handoff 前重新调用 `ResourceAccessPolicyPort`。
5. 裁剪结果记录 droppedCount 和 droppedReasonCounts，不记录原文。

#### 9.3.4 Tool Gateway 集成

`ToolProvider.LOCAL_AGENT` 的 tool catalog entry 默认 disabled。启用后，Tool Gateway 调用路径是：

```text
ToolGateway.invoke(LOCAL_AGENT)
  -> ToolPolicyPort.decide(...)
  -> LocalAgentAsToolPort.invoke(...)
  -> MeshPolicyPort.decide(...)
  -> AgentHandoffContextPolicy.reduce(...)
  -> AgentRunInboundPort.startRun(child)
  -> AgentHandoffRepositoryPort.save(...)
  -> AuditLedger.append(LOCAL_AGENT_HANDOFF)
```

Web 只能开放 query/cancel，不开放 direct create，避免绕过 Tool Gateway。

#### 9.3.5 Repository 与 API

```text
AgentHandoffRepositoryPort
  save(AgentHandoff handoff)
  findById(String handoffId)
  listByParentRunId(String tenantId, String parentRunId)
  updateTerminal(String handoffId, AgentHandoffStatus status, AgentHandoffFailureCode failureCode)
```

API：

| Method | Path | 行为 |
| --- | --- | --- |
| `GET` | `/api/agent-runs/{runId}/handoffs` | 查询 parent run 下 handoff。 |
| `GET` | `/api/agent-handoffs/{handoffId}` | 查询 handoff snapshot。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 幂等取消非 terminal handoff。 |

#### 9.3.6 TDD 顺序

1. RED：`AgentHandoffTests#shouldKeepTerminalStatusImmutable`。
2. RED：`AgentHandoffContextPolicyTests#shouldStripSecretPrivateAndRawToolResultContext`。
3. RED：`DefaultMeshPolicyPortTests#shouldDenyDepthOverflowAndCycles`。
4. RED：`LocalAgentAsToolPortTests#shouldCreateChildRunOnlyThroughRunPort`。
5. GREEN：实现 domain、context policy、mesh policy、local tool adapter。
6. RED/GREEN：`KernelAgentHandoffServiceTests` 覆盖 create、cancel、terminal update、audit。
7. RED/GREEN：JDBC parent list、terminal idempotency。
8. RED/GREEN：Web query/cancel contract，并确认 `POST /api/agent-handoffs` 不存在。

#### 9.3.7 阶段完成信号

Phase 7 首版完成只代表本地 Agent-as-Tool 可治理，不代表远程 A2A、Agent Card、mesh routing、debate 或 supervisor DAG 完成。回滚方式是 unregister/disable `LOCAL_AGENT` provider；历史 handoff 和 child run 保留审计查询。

### 9.4 Phase 8B 具体方案：Agent Eval Summary Gate

#### 9.4.1 当前差距

Phase 8 已有部分 retrieval eval 和 production gate foundation，但 Agent publish/canary 还缺可读取的 Agent eval summary。首版不运行 eval job，只把已有或人工导入的 eval 结果保存为 version-level snapshot，并让 gate 读取。

#### 9.4.2 领域模型

| 对象或 enum | 语义 |
| --- | --- |
| `AgentEvalSummary` | summaryId、tenantId、agentId、versionId、evalType、status、score、threshold、datasetRef、evalRunRef、createdAt。 |
| `AgentEvalType` | `RAG`、`MEMORY`、`TRAJECTORY`、`SAFETY`、`HITL`、`COST`。 |
| `AgentEvalStatus` | `PASS`、`WARN`、`FAIL`、`STALE`。 |
| `AgentEvalDefaults.MAX_SUMMARY_AGE` | stale 判断的唯一时间常量。 |
| `ProductionGateCheckCode.EVAL` | gate report 中的 eval 检查项，不能用字符串。 |

#### 9.4.3 Gate 规则

1. latest summary `FAIL` 阻断 gate。
2. latest summary `STALE`：high-risk 视为 fail，low-risk 视为 warn。
3. high-risk Agent 或绑定写工具的 Agent 缺 eval summary：fail closed。
4. low-risk read-only Agent 缺 eval summary：warn，不 silent pass。
5. 多 eval type 时，`SAFETY` 和 `TRAJECTORY` 对高风险 Agent 是必需项；`RAG/MEMORY` 对 knowledge assistant 是必需项。

#### 9.4.4 Port 与 API

```text
AgentEvalSummaryRepositoryPort
  save(AgentEvalSummary summary)
  findLatest(tenantId, agentId, versionId, evalType)
  listByVersion(tenantId, agentId, versionId)

AgentEvalStatusPort
  summarizeForGate(tenantId, agentId, versionId, riskLevel, toolRiskProfile)
```

API：

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries` | 保存 snapshot。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries/latest?evalType=SAFETY` | 查询 latest。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries` | 查询历史。 |

#### 9.4.5 TDD 顺序

1. RED：`AgentEvalSummaryTests#shouldRejectNegativeScoreAndInvalidThreshold`。
2. RED：`AgentEvalSummaryTests#shouldMarkSummaryStaleByNamedMaxAge`。
3. RED：`KernelAgentEvalQueryServiceTests#shouldSaveLatestAndHistorySnapshots`。
4. RED：`KernelProductionGateServiceTests#shouldFailHighRiskAgentWithoutEval`。
5. RED：`KernelProductionGateServiceTests#shouldWarnLowRiskReadOnlyAgentWithoutEval`。
6. GREEN：实现 eval domain、repository port、query service、gate adapter。
7. RED/GREEN：JDBC latest-by-type isolation 和 Web save/latest/history contract。

#### 9.4.6 阶段完成信号

Phase 8B 完成后，publish/canary gate 必须能解释 eval 结论来自哪个 summary、哪个 datasetRef/evalRunRef、是否 stale。缺失 eval 不能被伪造成 pass。

### 9.5 Phase 8C 具体方案：Quota、Cost Usage 与 SRE Health

#### 9.5.1 当前差距

企业试点需要“能不能继续运行、花了多少、系统健康吗”的运营证据。首版只做 quota decision、append-only cost usage 和只读 health 聚合，不做分布式限流、真实扣费或运维大屏。

#### 9.5.2 Quota 领域

| 对象或 enum | 语义 |
| --- | --- |
| `QuotaPolicy` | tenantId、scopeType、scopeId、windowType、tokenLimit、costLimit、callLimit、effect、enabled。 |
| `QuotaScopeType` | `TENANT`、`AGENT`、`USER`、`TOOL`、`MODEL`、`RUN`。 |
| `QuotaDecisionEffect` | `ALLOW`、`WARN`、`DENY`、`REQUIRE_APPROVAL`。 |
| `QuotaDefaults` | no-policy low/high-risk 默认决策。 |

默认决策：

| 场景 | 决策 |
| --- | --- |
| no policy + low risk | `WARN`，允许继续但必须有 evidence。 |
| no policy + high/critical risk | `REQUIRE_APPROVAL`。 |
| hard limit exceeded | `DENY`。 |
| warn threshold exceeded | `WARN`。 |

#### 9.5.3 Cost 与 Health 领域

| 对象或 enum | 语义 |
| --- | --- |
| `CostUsageRecord` | usageId、tenantId、agentId、runId、source、model、toolId、inputTokens、outputTokens、costAmount、occurredAt。 |
| `CostUsageSource` | `MODEL_CALL`、`TOOL_CALL`、`SANDBOX_EXECUTION`、`REMOTE_AGENT_CALL`。 |
| `SreHealthSnapshot` | status、contributors、generatedAt。 |
| `SreHealthStatus` | `GREEN`、`WARN`、`RED`，聚合优先级 RED > WARN > GREEN。 |
| `SreHealthContributorPort` | contributor 失败必须转为 WARN item。 |

#### 9.5.4 Port 与 API

```text
QuotaPolicyRepositoryPort
  upsert(QuotaPolicy policy)
  findEnabled(tenantId, scopeType, scopeId)

QuotaDecisionPort
  decide(QuotaDecisionRequest request) -> QuotaDecision

CostUsageRepositoryPort
  append(CostUsageRecord record)
  aggregate(CostUsageQuery query) -> CostUsageSummary

SreHealthContributorPort
  contribute() -> SreHealthContributorSnapshot
```

API：

| Method | Path | 行为 |
| --- | --- | --- |
| `PUT` | `/api/quotas/{scopeType}/{scopeId}` | upsert policy。 |
| `GET` | `/api/quotas/{scopeType}/{scopeId}` | 查询 policy。 |
| `GET` | `/api/cost/usage` | 聚合 usage。 |
| `GET` | `/api/sre/health` | 聚合 health snapshot。 |

#### 9.5.5 TDD 顺序

1. RED：`QuotaPolicyTests#shouldRejectPolicyWithoutAnyLimit`。
2. RED：`KernelQuotaDecisionServiceTests#shouldRequireApprovalForHighRiskWhenNoPolicy`。
3. RED：`KernelQuotaDecisionServiceTests#shouldDenyWhenHardLimitExceeded`。
4. RED：`CostUsageRecordTests#shouldRejectNegativeTokenOrCost`。
5. RED：`KernelCostUsageQueryServiceTests#shouldAggregateUsageByTenantAgentAndRun`。
6. RED：`KernelSreHealthQueryServiceTests#shouldWarnWhenContributorThrows`。
7. GREEN：实现 quota、cost、sre domain、ports、services。
8. RED/GREEN：JDBC upsert/append/aggregate tests 和 Web contract tests。

#### 9.5.6 阶段完成信号

Phase 8C 完成后，运行治理至少能给出三类证据：某次调用为什么允许、警告或拒绝；某个 tenant/agent/run 的成本聚合；当前 SRE health 的 contributor 明细。Contributor exception 不能被吞成 green。

### 9.6 Phase 8D 具体方案：Canary Rollout 与企业试点准入

#### 9.6.1 当前差距

Phase 6 已有 version activation/rollback owner，Phase 8D 不能另建第二套发布事实。它只负责 rollout 事实、promote 前 gate 强制检查、pilot readiness report 和回滚编排。

#### 9.6.2 Rollout 领域

| 对象或 enum | 语义 |
| --- | --- |
| `AgentVersionRollout` | rolloutId、tenantId、agentId、versionId、canaryPercent、status、failureCode、gateReportId、startedBy、startedAt、finishedAt。 |
| `AgentRolloutStatus` | `CREATED`、`RUNNING`、`PAUSED`、`PROMOTED`、`ROLLED_BACK`、`FAILED`。 |
| `AgentRolloutFailureCode` | `GATE_MISSING`、`GATE_FAILED`、`INVALID_PERCENT`、`ROLLBACK_FAILED`。 |
| `AgentRolloutLimits` | `MIN_PERCENT`、`MAX_PERCENT`、`DEFAULT_CANARY_PERCENT`。 |

状态规则：

1. `PROMOTED`、`ROLLED_BACK`、`FAILED` 是 terminal status，不可回退。
2. `promote` 前必须读取 latest Production Gate report；缺失为 `GATE_MISSING`，非 pass 为 `GATE_FAILED`。
3. `rollback` 必须调用 `AgentVersionActivationRepositoryPort` 或 Phase 6 activation service，不写第二套 active version。
4. `pause` 对 terminal rollout 幂等返回当前状态。

#### 9.6.3 Readiness report

`EnterprisePilotReadinessReport` 必须覆盖九项 check code：

| CheckCode | 高风险缺失 | 低风险缺失 |
| --- | --- | --- |
| `OWNER` | FAIL | WARN |
| `PUBLISHED_VERSION` | FAIL | FAIL |
| `TOOL_RISK` | FAIL | WARN |
| `RESOURCE_ACL` | FAIL | WARN |
| `EVAL` | FAIL | WARN |
| `QUOTA` | FAIL | WARN |
| `AUDIT` | FAIL | FAIL |
| `ROLLBACK` | FAIL | FAIL |
| `DISABLE_SWITCH` | FAIL | FAIL |

report 只保存 check result、evidenceRefs、status、createdAt；不保存 prompt、eval case、raw tool output 或 raw ACL payload。

#### 9.6.4 Port 与 API

```text
AgentRolloutRepositoryPort
  save(AgentVersionRollout rollout)
  findById(rolloutId)
  update(AgentVersionRollout rollout)
  findLatest(tenantId, agentId, versionId)

EnterprisePilotReadinessRepositoryPort
  save(EnterprisePilotReadinessReport report)
  findLatest(tenantId, agentId, versionId)
```

API：

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/canary` | 创建 canary rollout。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | gate pass 后 promote。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | 调 Phase 6 rollback owner。 |
| `POST` | `/api/agents/{agentId}/pilot-readiness` | 生成 readiness report。 |
| `GET` | `/api/agents/{agentId}/pilot-readiness/latest` | 查询 latest report。 |

#### 9.6.5 TDD 顺序

1. RED：`AgentVersionRolloutTests#shouldRejectCanaryPercentOutsideLimits`。
2. RED：`AgentVersionRolloutTests#shouldKeepTerminalStatusImmutable`。
3. RED：`KernelAgentRolloutServiceTests#shouldFailPromoteWhenGateReportMissing`。
4. RED：`KernelAgentRolloutServiceTests#shouldRollbackThroughVersionActivationPort`。
5. RED：`EnterprisePilotReadinessReportTests#shouldRequireAllCheckCodes`。
6. RED：`KernelEnterprisePilotReadinessServiceTests#shouldFailWhenHighRiskRequiredEvidenceMissing`。
7. GREEN：实现 rollout/readiness domain、ports、services。
8. RED/GREEN：JDBC rollout/latest report tests 和 Web canary/pause/promote/rollback/readiness contracts。

#### 9.6.6 阶段完成信号

Phase 8D 完成后，企业试点准入必须能证明：版本已发布、风险工具可控、资源 ACL 有证据、eval/quota/audit/rollback/disable switch 都可查询。Canary 只记录 rollout 与 gate 事实，不实现真实百分比分流器。

## 10. 深读补充后的执行判定

本次补充仍然是设计开发方案，不是实现完成声明。下一步实现顺序保持：

1. Phase 5 Connector/Sandbox Security Closure。
2. Phase 4 Resource ACL Import Commit + Audit Closure。
3. Phase 7 Governed Local Agent-as-Tool。
4. Phase 8B Agent Eval Summary Gate。
5. Phase 8C Quota/Cost/SRE Health。
6. Phase 8D Canary/Pilot Gate。

每个阶段完成时，必须同时更新 `docs/aegis/work/2026-05-25-ai-infra-phase-plan-refresh/20-checkpoint.md` 与 `90-evidence.md`，记录 RED/GREEN 命令、漂移判断和仍未完成项。

## 11. 2026-05-26 当前实现校准后的剩余阶段设计开发方案

本节基于再次深读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、当前 Aegis checkpoint/evidence 以及代码表面后追加。它保留前文 Phase 4/5 的方案作为历史与回归依据，但当前实现状态已经变化：

1. Phase 5 Connector/Sandbox Security Closure 已有 focused regression evidence，后续只作为 Phase 7/8 的安全回归依赖。
2. Phase 4 Resource ACL Import Commit + Audit Closure 已有 focused regression evidence，后续只作为 Context/Handoff/Readiness 的授权与审计依赖。
3. Phase 7 Local Agent-as-Tool 已有 kernel、JDBC、Web、starter 代码表面，但 starter 回归仍需收口并记录最终证据。
4. Phase 8B、Phase 8C、Phase 8D 仍是主要未完成实现阶段。

因此，本节之后的未完成阶段主线为：Phase 7 收口、Phase 8B、Phase 8C、Phase 8D。Phase 4/5 不再作为下一步主实现阶段重复展开，只要求在每个后续阶段跑相应 focused regression，防止治理能力回退。

### 11.1 Phase 7 收口方案：Handoff Starter、Tool 注册与验收证据

#### 11.1.1 当前差距

当前代码表面已经出现 `AgentHandoff`、`AgentHandoffStatus`、`AgentHandoffFailureCode`、`AgentHandoffLimits`、`AgentHandoffContextPolicy`、`MeshPolicyPort`、`KernelAgentHandoffService`、`LocalAgentAsToolPort`、`JdbcAgentHandoffRepositoryAdapter` 和 `SeahorseAgentHandoffController`。剩余缺口不是重新设计 handoff，而是把本地 Agent-as-Tool 的装配闭环做实：

1. `KernelAgentHandoffService` 必须在 starter context 中稳定创建，并同时满足 `AgentHandoffInboundPort` 查询/取消和 `LocalAgentAsToolPort` 创建 child run 的需求。
2. `LocalAgentAsToolPort` 必须被 `BuiltInAgentToolRegistrar` 注册到 Tool Gateway 的 tool registry，provider 使用 `ToolProvider.LOCAL_AGENT`。
3. Web 只提供 query/cancel，不开放 direct create API，避免绕过 Tool Gateway。
4. JDBC adapter 必须保持 terminal status 不可变与 parent run list 稳定排序。
5. audit payload 只能写 handoff id、parent/child run id、status、failureCode、dropped counts、summary length 等白名单字段，不能写 raw context、raw tool result、secret、credential。

#### 11.1.2 文件边界

必须只在以下边界内收口：

| 层 | 文件 |
| --- | --- |
| Kernel domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/handoff/*` |
| Kernel app | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/handoff/*` |
| Inbound port | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/AgentHandoffInboundPort.java` |
| Outbound ports | `AgentHandoffRepositoryPort.java`、`MeshPolicyPort.java` |
| JDBC | `JdbcAgentHandoffRepositoryAdapter.java` 与 `sa_agent_handoff` schema |
| Web | `SeahorseAgentHandoffController.java` |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration.java`、`SeahorseAgentKernelAgentAutoConfiguration.java`、`SeahorseAgentRegistryRepositoryAutoConfiguration.java` |
| Tests | handoff kernel/JDBC/Web/starter focused tests |

不得把 handoff create 放到 Web controller；不得在 starter 中为了测试绕过 `AgentRunInboundPort`；不得让 `LocalAgentAsToolPort` 依赖 repository 直接创建 run。

#### 11.1.3 装配规则

1. `KernelAgentHandoffService` 的构造依赖保持为 `AgentHandoffRepositoryPort`、`AgentRunInboundPort`、`MeshPolicyPort`、可选 `KernelAuditLedgerService` 和 `Clock`。
2. `AgentHandoffInboundPort` bean 可以返回 `KernelAgentHandoffService` 实例，但 bean method 的 return type 必须仍让 `@ConditionalOnBean(KernelAgentHandoffService.class)` 能发现 exact service type。
3. `LocalAgentAsToolPort` 只能依赖 `KernelAgentHandoffService`，因为它需要 create local handoff，不应把 create 方法扩到 query/cancel inbound port。
4. 注册路径保持为 starter agent auto-configuration 创建 `LocalAgentAsToolPort`，再由现有 built-in registrar 注册，避免 controller 或 repository 介入 tool catalog。
5. 如果 starter test context 缺 `AgentRunInboundPort`，测试配置应补齐 run store 依赖或提供真实的 port test double；不应放宽生产条件到缺少 run port 也创建 handoff service。

#### 11.1.4 TDD 顺序

1. RED：保留当前 starter 失败证据，`SeahorseAgentChatRunStoreAutoConfigurationTests#shouldWireLocalAgentAsToolPortWhenHandoffDependenciesExist` 期望 `LocalAgentAsToolPort` bean 和 registry registration。
2. 诊断：在失败 context 中断言 `AgentRunInboundPort`、`MeshPolicyPort`、`AgentHandoffRepositoryPort`、`KernelAgentHandoffService` 哪个缺失，禁止猜测式放宽条件。
3. GREEN：最小修复 starter 条件或测试配置，使 `KernelAgentHandoffService` exact type bean 可见，`LocalAgentAsToolPort` 被创建并注册。
4. RED/GREEN：补或保留 Web contract，确认 `GET /api/agent-runs/{runId}/handoffs`、`GET /api/agent-handoffs/{handoffId}`、`POST /api/agent-handoffs/{handoffId}/cancel` 可用，`POST /api/agent-handoffs` 为 404。
5. RED/GREEN：补或保留 JDBC test，确认 `update` 不会让 terminal handoff 回退到 non-terminal。
6. GREEN 验证：按 11.1.6 命令串行跑完 focused regression。

#### 11.1.5 验收合同

Phase 7 首版验收只声明本地 `LOCAL_AGENT` provider 可治理，不声明远程 A2A 或 Agent Mesh 完成。验收必须同时满足：

1. parent Agent 只能经 Tool Gateway 调用 local agent tool。
2. child run 只能经 `AgentRunInboundPort` 创建。
3. handoff query/cancel API 不暴露 raw `inputSummaryJson` 或 `contextSummaryJson`。
4. depth overflow、cycle、policy denial 使用 `AgentHandoffFailureCode`。
5. starter 能在默认 registry/run-store/agent mode 下注册 `LocalAgentAsToolPort`。
6. kernel 依赖扫描不出现 Spring、JDBC、Web、HTTP client。

#### 11.1.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,AgentHandoffContextPolicyTests,DefaultMeshPolicyPortTests,LocalAgentAsToolPortTests,KernelAgentHandoffServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
rg -n "org.springframework|javax.sql|java.sql|RestTemplate|WebClient" seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports
git diff --check
```

### 11.2 Phase 8B 方案：Agent Eval Summary Gate

#### 11.2.1 当前差距

Phase 8A Audit Ledger 与 production gate foundation 已存在，retrieval eval 也有独立能力；缺口是 Agent version 级别的 eval summary 没有成为 publish/canary gate 的输入。首版只做 summary import/query/gate，不实现 eval job runner、case authoring UI 或自动红队。

#### 11.2.2 领域设计

| 对象 | 职责 |
| --- | --- |
| `AgentEvalSummary` | 保存某 tenant/agent/version/evalType 的一次评估摘要 snapshot。 |
| `AgentEvalType` | `SAFETY`、`TRAJECTORY`、`RAG`、`MEMORY`、`TOOL_USE`。 |
| `AgentEvalStatus` | `PASS`、`WARN`、`FAIL`、`STALE`。 |
| `AgentEvalThreshold` | score、passThreshold、warnThreshold、maxAgeDays 的不变量。 |
| `AgentEvalSummaryRepositoryPort` | append summary、latest by type、history page。 |
| `AgentEvalInboundPort` | Web 只通过它保存和查询 summary。 |

不变量：

1. score、threshold、caseCount 不允许负数。
2. `FAIL` 不能被 stale 规则降级。
3. latest 按 `tenantId + agentId + versionId + evalType` 隔离。
4. summary 只保存 `datasetRef`、`evalRunRef`、指标摘要和 evidence refs，不保存原始 case、prompt、tool output。
5. stale 判定使用 `AgentEvalLimits.DEFAULT_MAX_AGE_DAYS` 或 command 中的具名 max age，不写散落数字。

#### 11.2.3 Gate 集成

`KernelProductionGateService` 增加 eval contributor，而不是把 eval 规则写进 factory 或 rollout：

1. high-risk Agent 必须存在 `SAFETY` 和 `TRAJECTORY` latest summary，缺失为 `FAIL`。
2. low-risk read-only Agent 缺 eval 为 `WARN`。
3. 任意 latest summary 为 `FAIL` 时 gate `FAIL`。
4. high-risk latest summary 为 `STALE` 时 gate `FAIL`；low-risk stale 为 `WARN`。
5. gate item 使用 `ProductionGateCheckCode.EVAL` 或新增 enum code，不写字符串。

#### 11.2.4 文件边界

| 层 | 文件 |
| --- | --- |
| Kernel domain | `kernel/domain/agent/eval/*` |
| Kernel app | `kernel/application/agent/eval/KernelAgentEvalQueryService.java`，扩展 `KernelProductionGateService` |
| Ports | `AgentEvalInboundPort.java`、`AgentEvalSummaryRepositoryPort.java` |
| JDBC | `JdbcAgentEvalSummaryRepositoryAdapter.java`、`sa_agent_eval_summary` |
| Web | `SeahorseAgentEvalController.java` |
| Starter | registry/gate auto-configuration tests |

#### 11.2.5 API 与表

API：

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries` | 保存一条 summary snapshot。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries/latest?evalType=SAFETY` | 查询 latest summary。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries` | 分页查询 history。 |

表字段首版：

```text
sa_agent_eval_summary(
  id, tenant_id, agent_id, version_id, eval_type, status,
  score, pass_threshold, warn_threshold, case_count,
  dataset_ref, eval_run_ref, evidence_json, created_by, created_at
)
```

#### 11.2.6 TDD 顺序

1. RED：`AgentEvalSummaryTests` 覆盖 score/threshold/caseCount validation、stale 判定、`FAIL` 不降级。
2. RED：`KernelAgentEvalQueryServiceTests` 覆盖 append、latest by type、history。
3. RED：`KernelProductionGateServiceTests` 覆盖 high-risk missing eval fail、low-risk missing eval warn、failed eval blocks、stale high-risk fail。
4. GREEN：实现 domain、limits、repository port、query service、gate contributor。
5. RED：`JdbcAgentEvalSummaryRepositoryAdapterTests` 覆盖 latest 隔离、history 排序、enum mapping。
6. GREEN：实现 JDBC adapter 与 schema。
7. RED：`SeahorseAgentEvalControllerTests` 覆盖 save/latest/history 和 response 不含 raw case/prompt/tool output。
8. GREEN：实现 Web 与 starter wiring。

#### 11.2.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentEvalSummaryTests,KernelAgentEvalQueryServiceTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentEvalSummaryRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentEvalControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

### 11.3 Phase 8C 方案：Quota、Cost Usage 与 SRE Health

#### 11.3.1 当前差距

当前 production gate/factory 中已有 quota 未接入提示，但没有可查询的 quota policy、quota decision、cost usage 聚合和 SRE health 聚合。首版只做决策与证据闭环，不做真实 billing、预算自动扣款、Prometheus exporter 或运行时强制 kill。

#### 11.3.2 Quota 领域设计

| 对象 | 职责 |
| --- | --- |
| `QuotaPolicy` | tenant/agent/user/tool/model/run scope 的限额规则。 |
| `QuotaScopeType` | `TENANT`、`AGENT`、`USER`、`TOOL`、`MODEL`、`RUN`。 |
| `QuotaWindow` | `DAILY`、`MONTHLY`、`RUN_LIFETIME`。 |
| `QuotaDecisionEffect` | `ALLOW`、`WARN`、`REQUIRE_APPROVAL`、`DENY`。 |
| `QuotaDecisionReasonCode` | `NO_POLICY_LOW_RISK`、`NO_POLICY_HIGH_RISK`、`WARN_THRESHOLD_EXCEEDED`、`HARD_LIMIT_EXCEEDED`。 |

默认规则：

1. 无 policy 且 low-risk：`WARN`，允许继续但必须有 visible decision。
2. 无 policy 且 high/critical risk：`REQUIRE_APPROVAL`。
3. hard limit exceeded：`DENY`。
4. warn threshold exceeded：`WARN`。
5. 所有 limit 字段必须是非负，policy 至少包含一个 limit。

#### 11.3.3 Cost 与 Health 领域设计

| 对象 | 职责 |
| --- | --- |
| `CostUsageRecord` | append-only 记录 token、cost、call count、run/tool/model 维度。 |
| `CostUsageAggregate` | 按 tenant/agent/run 聚合。 |
| `SreHealthReport` | 聚合 contributor status。 |
| `SreHealthStatus` | `GREEN`、`WARN`、`RED`。 |
| `SreHealthContributorPort` | 小接口，后续 adapter 扩展。 |

聚合规则：

1. `RED` 优先级高于 `WARN`，`WARN` 高于 `GREEN`。
2. contributor 抛异常时产生 `WARN` item，不能吞成 `GREEN`。
3. health report 只存 component、status、reasonCode、evidenceRef，不存日志全文。
4. cost usage 不修改历史记录；修正通过追加 adjustment record 或后续专门切片处理。

#### 11.3.4 文件边界

| 层 | 文件 |
| --- | --- |
| Kernel domain | `kernel/domain/agent/quota/*`、`kernel/domain/agent/cost/*`、`kernel/domain/agent/sre/*` |
| Kernel app | `KernelQuotaDecisionService`、`KernelCostUsageQueryService`、`KernelSreHealthQueryService` |
| Ports | `QuotaPolicyRepositoryPort`、`CostUsageRepositoryPort`、`SreHealthContributorPort` 与对应 inbound ports |
| JDBC | `JdbcQuotaPolicyRepositoryAdapter`、`JdbcCostUsageRepositoryAdapter` |
| Web | `SeahorseQuotaController`、`SeahorseCostUsageController`、`SeahorseSreHealthController` |
| Starter | quota/cost/health auto-configuration tests |

#### 11.3.5 API 与表

API：

| Method | Path | 行为 |
| --- | --- | --- |
| `PUT` | `/api/quota-policies/{policyId}` | upsert quota policy。 |
| `POST` | `/api/quota-decisions:evaluate` | 返回 quota decision。 |
| `POST` | `/api/cost-usage-records` | 追加 usage record。 |
| `GET` | `/api/cost-usage:aggregate` | 按 tenant/agent/run 查询聚合。 |
| `GET` | `/api/sre/health` | 查询 health report。 |

表：

```text
sa_quota_policy(id, tenant_id, scope_type, scope_id, window_type, max_tokens, max_cost_minor, max_calls, warn_ratio, effect, status, updated_at)
sa_cost_usage_record(id, tenant_id, agent_id, run_id, tool_id, model_id, tokens_in, tokens_out, cost_minor, call_count, source, created_at)
```

#### 11.3.6 TDD 顺序

1. RED：`QuotaPolicyTests` 覆盖 empty limit、negative limit、scope/window enum。
2. RED：`KernelQuotaDecisionServiceTests` 覆盖 no-policy low-risk warn、no-policy high-risk require approval、hard limit deny。
3. RED：`CostUsageRecordTests` 覆盖 token/cost/call count 非负、append-only id 必填。
4. RED：`KernelCostUsageQueryServiceTests` 覆盖 tenant/agent/run 聚合。
5. RED：`KernelSreHealthQueryServiceTests` 覆盖 red/warn/green 优先级和 contributor exception warn。
6. GREEN：实现 domain、ports、services。
7. RED/GREEN：JDBC upsert、append、aggregate tests。
8. RED/GREEN：Web quota/cost/health contract tests。
9. GREEN：starter wiring tests。

#### 11.3.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelCostUsageQueryServiceTests,KernelSreHealthQueryServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseQuotaControllerTests,SeahorseCostUsageControllerTests,SeahorseSreHealthControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

### 11.4 Phase 8D 方案：Canary Rollout 与 Enterprise Pilot Readiness

#### 11.4.1 当前差距

Phase 6 已有 publish-ready、version activation 和 rollback owner。Phase 8D 不应再建立第二套 active version 事实；它只记录 rollout 事实、在 promote 前强制读取 latest Production Gate report，并生成企业试点准入 report。首版不实现真实流量分配、灰度网关或前端发布向导。

#### 11.4.2 Rollout 领域设计

| 对象 | 职责 |
| --- | --- |
| `AgentVersionRollout` | 某 agent version 的 canary/promotion/rollback 事实。 |
| `AgentRolloutStatus` | `CREATED`、`RUNNING`、`PAUSED`、`PROMOTED`、`ROLLED_BACK`、`FAILED`。 |
| `AgentRolloutFailureCode` | `GATE_MISSING`、`GATE_FAILED`、`INVALID_PERCENT`、`ROLLBACK_FAILED`。 |
| `AgentRolloutLimits` | `MIN_PERCENT`、`MAX_PERCENT`、`DEFAULT_CANARY_PERCENT`。 |
| `AgentRolloutRepositoryPort` | save、update、findById、findLatest。 |

规则：

1. canary percent 必须落在 `AgentRolloutLimits` 范围内。
2. `PROMOTED`、`ROLLED_BACK`、`FAILED` 为 terminal status，不可回退。
3. `promote` 前必须读取 latest Production Gate report；缺失为 `GATE_MISSING`，非 pass 为 `GATE_FAILED`。
4. `rollback` 只能调用 Phase 6 activation/rollback owner，不能直接更新 active version。
5. `pause` 对 terminal rollout 幂等返回当前状态。

#### 11.4.3 Enterprise Pilot Readiness 设计

`EnterprisePilotReadinessReport` 覆盖九个 check code：

| CheckCode | 高风险缺失 | 低风险缺失 |
| --- | --- | --- |
| `OWNER` | FAIL | WARN |
| `PUBLISHED_VERSION` | FAIL | FAIL |
| `TOOL_RISK` | FAIL | WARN |
| `RESOURCE_ACL` | FAIL | WARN |
| `EVAL` | FAIL | WARN |
| `QUOTA` | FAIL | WARN |
| `AUDIT` | FAIL | FAIL |
| `ROLLBACK` | FAIL | FAIL |
| `DISABLE_SWITCH` | FAIL | FAIL |

report 只保存 status、check code、result、reasonCode、evidenceRefs、createdAt，不保存 prompt、eval case、raw tool output、raw ACL payload 或 secret。

#### 11.4.4 文件边界

| 层 | 文件 |
| --- | --- |
| Kernel domain | `kernel/domain/agent/rollout/*`、`kernel/domain/agent/readiness/*` |
| Kernel app | `KernelAgentRolloutService`、`KernelEnterprisePilotReadinessService` |
| Ports | `AgentRolloutInboundPort`、`EnterprisePilotReadinessInboundPort`、repository ports |
| JDBC | `JdbcAgentRolloutRepositoryAdapter`、`JdbcEnterprisePilotReadinessRepositoryAdapter` |
| Web | `SeahorseAgentRolloutController`、`SeahorseEnterprisePilotReadinessController` |
| Starter | rollout/readiness auto-configuration tests |

#### 11.4.5 API 与表

API：

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/canary` | 创建 canary rollout。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停 rollout。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | gate pass 后 promote。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | 调 Phase 6 rollback owner。 |
| `GET` | `/api/agents/{agentId}/rollouts/latest?versionId=...` | 查询 latest rollout。 |
| `POST` | `/api/agents/{agentId}/pilot-readiness` | 生成 readiness report。 |
| `GET` | `/api/agents/{agentId}/pilot-readiness/latest?versionId=...` | 查询 latest readiness。 |

表：

```text
sa_agent_rollout(id, tenant_id, agent_id, version_id, percent, status, failure_code, gate_report_id, created_by, created_at, updated_at, terminal_at)
sa_agent_pilot_readiness_report(id, tenant_id, agent_id, version_id, status, checks_json, evidence_json, created_by, created_at)
```

#### 11.4.6 TDD 顺序

1. RED：`AgentVersionRolloutTests` 覆盖 percent 边界、terminal status、pause 幂等。
2. RED：`KernelAgentRolloutServiceTests` 覆盖 promote gate missing fail、gate failed fail、rollback uses activation owner。
3. RED：`EnterprisePilotReadinessReportTests` 覆盖九个 check code 完整性和 status 聚合。
4. RED：`KernelEnterprisePilotReadinessServiceTests` 覆盖 high-risk required evidence missing fail、low-risk optional evidence warn。
5. GREEN：实现 domain、ports、services。
6. RED/GREEN：JDBC rollout status update、latest rollout、latest readiness tests。
7. RED/GREEN：Web canary/pause/promote/rollback/readiness contract tests。
8. GREEN：starter wiring tests。

#### 11.4.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,KernelAgentRolloutServiceTests,EnterprisePilotReadinessReportTests,KernelEnterprisePilotReadinessServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

## 12. 当前最新执行判定

截至本节，当前未完成阶段按以下顺序推进：

1. Phase 7 Handoff starter/tool-registration closure：先修复并验证 `LocalAgentAsToolPort` starter wiring。
2. Phase 8B Agent Eval Summary Gate：让 production gate 能读取 version-level eval summary。
3. Phase 8C Quota、Cost Usage 与 SRE Health：补运行治理、成本聚合和健康证据。
4. Phase 8D Canary Rollout 与 Enterprise Pilot Readiness：补企业试点准入和 rollout 事实。

Phase 4 与 Phase 5 后续只作为回归范围进入 Phase 7/8 验收，不再作为当前主实现入口。每完成一个阶段，必须更新 Aegis checkpoint/evidence，记录 RED 失败、GREEN 通过、focused regression 命令、漂移判断和剩余风险。

## 13. 2026-05-26 Phase 7 收口后的剩余阶段执行级设计开发方案

本节是在再次深入阅读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、最新 Aegis checkpoint/evidence 以及当前代码表面后追加。与第 11/12 节相比，当前状态已经进一步变化：

1. Phase 5 Connector/Sandbox Security Closure 已有 focused regression evidence，继续作为后续阶段的安全回归依赖。
2. Phase 4 Resource ACL Import Commit + Audit Closure 已有 focused regression evidence，继续作为 Handoff、Readiness、Audit 相关验收依赖。
3. Phase 7 Handoff starter/tool-registration closure 已有 RED/GREEN 和 kernel/JDBC/Web/starter focused regression evidence，当前不再列为剩余主实现阶段。
4. 当前真正剩余的主实现阶段为 Phase 8B、Phase 8C、Phase 8D。

### 13.1 共同执行边界

本节所有阶段都必须遵守以下边界：

1. Kernel 只能依赖 domain、application service、port 和 JDK；不得引入 Spring、JDBC、Web、HTTP client。
2. 所有状态、类型、风险、窗口、来源、reason、check code、failure code 必须使用 enum 或具名常量。
3. Repository port 保持小接口，Eval、Quota、Cost、SRE、Rollout、Readiness 不合并为大一统 `AgentService`。
4. audit、eval、cost、rollout、readiness 均使用 append-only 或 snapshot 语义，修正通过追加记录表达。
5. Production Gate 只编排检查项，不拥有 eval、quota、rollout 的领域不变量。
6. 不引入工作流引擎、远程 Agent mesh、真实流量网关、真实 billing、Prometheus exporter 或自动红队 runner。
7. 每个阶段先写 RED 测试并记录失败，再做最小 GREEN，再跑 focused regression。
8. 每个阶段完成后必须更新 Aegis checkpoint/evidence，记录命令、结果、漂移判断和剩余风险。

### 13.2 Phase 8B 执行方案：Agent Eval Summary Gate

#### 13.2.1 目标与当前差距

Phase 8B 的目标是把 Agent version 级别的评估摘要纳入 Production Gate。当前 `KernelProductionGateService` 已有 gate foundation，但 `EVAL_PASSING` 仍是固定 warn 文案，无法读取真实 version-level eval evidence。首版只实现 eval summary 保存、latest/history 查询和 gate contributor，不实现 eval job runner、case authoring UI、自动红队、raw prompt/case/tool output 存储。

完成后必须满足：

1. 管理端可为某个 `tenantId + agentId + versionId + evalType` 追加 eval summary snapshot。
2. 可查询某一 eval type 的 latest summary 和按创建时间倒序的 history。
3. Production Gate 能读取 latest summary，并按风险等级 fail closed 或 warn。
4. high/critical risk Agent 缺少必需 eval 时 gate `FAIL`；low/medium risk 缺失时 gate `WARN`。
5. `FAIL` summary 永远不能被 stale 规则降级。
6. Web response 与 JDBC 表不包含 raw case、raw prompt、raw tool output 或 secret。

#### 13.2.2 领域模型与不变量

新增包：`seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/eval/`

| 类型 | 职责 |
| --- | --- |
| `AgentEvalSummary` | 单次 eval 摘要 snapshot，维护分数、阈值、case 数、创建时间和 evidence refs 不变量。 |
| `AgentEvalType` | `SAFETY`、`TRAJECTORY`、`RAG`、`MEMORY`、`TOOL_USE`。 |
| `AgentEvalStatus` | `PASS`、`WARN`、`FAIL`、`STALE`。 |
| `AgentEvalLimits` | `DEFAULT_MAX_AGE_DAYS`、`DEFAULT_HISTORY_LIMIT`、`MAX_HISTORY_LIMIT`。 |
| `AgentEvalGateRequirement` | 根据 `AgentRiskLevel` 给出必需 eval type 集合与缺失时 gate status。 |

领域规则：

1. `score`、`passThreshold`、`warnThreshold`、`caseCount` 不允许负数。
2. `passThreshold` 必须大于等于 `warnThreshold`。
3. `summaryId`、`tenantId`、`agentId`、`versionId`、`evalType`、`status`、`createdBy`、`createdAt` 必填。
4. `createdAt` 早于 `clock.now() - AgentEvalLimits.DEFAULT_MAX_AGE_DAYS` 时可投影为 `STALE`，但原始 `FAIL` 仍保持 `FAIL`。
5. `evidenceRefs` 只允许保存引用字符串、datasetRef、evalRunRef 和指标摘要，不保存原始样本。
6. latest 语义按 `tenantId + agentId + versionId + evalType` 隔离，不能只按 agentId 查询。

#### 13.2.3 Port、服务与 Gate 编排

新增或修改文件：

| 层 | 文件 | 操作 |
| --- | --- | --- |
| Inbound | `AgentEvalInboundPort.java` | 暴露 `saveSummary`、`latestSummary`、`history`。 |
| Command | `AgentEvalSummarySaveCommand.java` | Web 到 kernel 的保存命令，携带 tenant/version/evalType/status/score/threshold/case/evidence。 |
| Query | `AgentEvalSummaryHistoryQuery.java` | history 查询条件，包含 page/size 上限。 |
| Outbound | `AgentEvalSummaryRepositoryPort.java` | `append`、`findLatest`、`findHistory`。 |
| Application | `KernelAgentEvalQueryService.java` | 只负责编排保存和查询，不写 gate 规则。 |
| Application | `KernelProductionGateService.java` | 增加 eval contributor，调用 repository 查询 latest summary。 |
| Domain | `ProductionGateCheckCode.java` | 复用 `EVAL_PASSING`，不新增字符串 check code。 |

Gate contributor 规则：

1. `AgentRiskLevel.HIGH` 和 `AgentRiskLevel.CRITICAL` 必须具备 `SAFETY` 与 `TRAJECTORY` latest summary。
2. `AgentRiskLevel.LOW` 和 `AgentRiskLevel.MEDIUM` 缺 summary 返回 `WARN`，信息说明 eval evidence 缺失。
3. 任一必需 latest summary 为 `FAIL` 返回 `FAIL`。
4. 任一必需 latest summary 投影为 `STALE` 时，high/critical 返回 `FAIL`，low/medium 返回 `WARN`。
5. 所有必需 summary 为 `PASS` 时返回 `PASS`；存在 `WARN` 且无 `FAIL`/high-risk stale 时返回 `WARN`。
6. item message 必须包含 summaryId、evalType、status、datasetRef 或 evalRunRef 的引用信息，不包含原始评测内容。

#### 13.2.4 JDBC、Schema 与 Web 合同

新增表：

```sql
CREATE TABLE IF NOT EXISTS sa_agent_eval_summary (
  summary_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  eval_type VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  score DOUBLE PRECISION NOT NULL,
  pass_threshold DOUBLE PRECISION NOT NULL,
  warn_threshold DOUBLE PRECISION NOT NULL,
  case_count INT NOT NULL,
  dataset_ref VARCHAR(256),
  eval_run_ref VARCHAR(256),
  evidence_json TEXT NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_agent_eval_summary_type CHECK (eval_type IN ('SAFETY', 'TRAJECTORY', 'RAG', 'MEMORY', 'TOOL_USE')),
  CONSTRAINT chk_sa_agent_eval_summary_status CHECK (status IN ('PASS', 'WARN', 'FAIL', 'STALE')),
  CONSTRAINT chk_sa_agent_eval_summary_score CHECK (score >= 0),
  CONSTRAINT chk_sa_agent_eval_summary_threshold CHECK (pass_threshold >= warn_threshold AND warn_threshold >= 0),
  CONSTRAINT chk_sa_agent_eval_summary_case_count CHECK (case_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_sa_agent_eval_summary_latest
  ON sa_agent_eval_summary(tenant_id, agent_id, version_id, eval_type, created_at DESC, summary_id DESC);
```

Web API：

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries` | 追加 summary snapshot，返回保存后的摘要。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries/latest?tenantId=...&evalType=SAFETY` | 查询 latest。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries?tenantId=...&evalType=SAFETY&page=0&size=20` | 查询 history。 |

Web DTO 禁止字段：`rawCase`、`rawPrompt`、`rawToolOutput`、`secret`、`sampleInput`、`sampleOutput`。

#### 13.2.5 TDD 切片

1. RED：`AgentEvalSummaryTests#shouldRejectNegativeScoreAndInvalidThreshold`。
2. RED：`AgentEvalSummaryTests#shouldProjectStaleWithoutDowngradingFail`。
3. RED：`KernelAgentEvalQueryServiceTests#shouldAppendLatestAndHistoryByTenantAgentVersionAndType`。
4. RED：`KernelProductionGateServiceTests#shouldFailHighRiskAgentWithoutSafetyAndTrajectoryEval`。
5. RED：`KernelProductionGateServiceTests#shouldWarnLowRiskAgentWithoutEval`。
6. RED：`KernelProductionGateServiceTests#shouldFailWhenLatestEvalFailsOrHighRiskEvalIsStale`。
7. GREEN：实现 domain、port、service、gate contributor。
8. RED：`JdbcAgentEvalSummaryRepositoryAdapterTests#shouldFindLatestByTenantAgentVersionAndType`。
9. RED：`JdbcAgentEvalSummaryRepositoryAdapterTests#shouldKeepHistoryDescendingAndRejectUnknownEnums`。
10. GREEN：实现 schema 与 JDBC adapter。
11. RED：`SeahorseAgentEvalControllerTests#shouldSaveLatestAndHistoryWithoutRawEvaluationFields`。
12. GREEN：实现 Web controller 与 starter wiring。

#### 13.2.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentEvalSummaryTests,KernelAgentEvalQueryServiceTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentEvalSummaryRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentEvalControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
rg -n "rawCase|rawPrompt|rawToolOutput|secret-token|sampleInput|sampleOutput" seahorse-agent-kernel/src/main/java seahorse-agent-adapter-web/src/main/java seahorse-agent-adapter-repository-jdbc/src/main/java
```

#### 13.2.7 回滚与非目标

回滚时可以关闭 eval summary Web API 和 gate contributor，但保留 `sa_agent_eval_summary` 历史记录用于审计。不得删除 eval summary 表来“修正”错误数据；错误 summary 通过追加新 summary 覆盖 latest。首版不做 eval runner、case 管理、红队执行、前端 dashboard 或自动触发评估。

### 13.3 Phase 8C 执行方案：Quota、Cost Usage 与 SRE Health

#### 13.3.1 目标与当前差距

Phase 8C 的目标是建立运行治理证据闭环：quota decision 不再 silent allow，cost usage 可追加并聚合，SRE health 能汇总 contributor 状态并在异常时至少 warn。当前代码中只有 Production Gate 的 quota/SRE foundation warn 文案，没有可管理的 quota policy、可查询的 cost aggregate 或 health contributor port。

完成后必须满足：

1. 无 quota policy 时，low/medium risk 返回 `WARN`，high/critical risk 返回 `REQUIRE_APPROVAL`，不能 silent allow。
2. quota policy 至少包含 tokens、cost 或 call count 中的一个 limit。
3. hard limit 超出时返回 `DENY`，warn threshold 超出时返回 `WARN`。
4. cost usage record append-only，不更新历史记录。
5. cost aggregate 可按 tenant、agent、run 维度查询。
6. SRE health contributor 抛异常时生成 `WARN` item，不能吞成 `GREEN`。
7. Production Gate 的 `QUOTA_CONFIGURED` 和 `SRE_HEALTH_GREEN` 能读取真实 contributor 或 repository evidence。

#### 13.3.2 领域模型与不变量

新增包：

```text
kernel/domain/agent/quota/
kernel/domain/agent/cost/
kernel/domain/agent/sre/
```

| 类型 | 职责 |
| --- | --- |
| `QuotaPolicy` | 定义某 scope/window 下的 token/cost/call limit 与 warn ratio。 |
| `QuotaScopeType` | `TENANT`、`AGENT`、`USER`、`TOOL`、`MODEL`、`RUN`。 |
| `QuotaWindow` | `DAILY`、`MONTHLY`、`RUN_LIFETIME`。 |
| `QuotaPolicyStatus` | `ACTIVE`、`DISABLED`。 |
| `QuotaDecisionEffect` | `ALLOW`、`WARN`、`REQUIRE_APPROVAL`、`DENY`。 |
| `QuotaDecisionReasonCode` | `POLICY_MATCHED`、`NO_POLICY_LOW_RISK`、`NO_POLICY_HIGH_RISK`、`WARN_THRESHOLD_EXCEEDED`、`HARD_LIMIT_EXCEEDED`、`POLICY_DISABLED`。 |
| `CostUsageRecord` | append-only usage 事实，包含 token、cost、call count、source。 |
| `CostUsageSource` | `MODEL`、`TOOL`、`SANDBOX`、`HANDOFF`、`MANUAL_ADJUSTMENT`。 |
| `CostUsageAggregate` | 聚合结果 snapshot。 |
| `SreHealthReport` | health contributor 汇总 snapshot。 |
| `SreHealthStatus` | `GREEN`、`WARN`、`RED`。 |
| `SreHealthReasonCode` | `OK`、`CONTRIBUTOR_WARN`、`CONTRIBUTOR_RED`、`CONTRIBUTOR_EXCEPTION`、`NO_CONTRIBUTOR`。 |

不变量：

1. `maxTokens`、`maxCostMinor`、`maxCalls` 允许为空，但不能同时为空。
2. 所有 numeric limit、usage count、cost 均不能为负。
3. `warnRatio` 必须在 `QuotaLimits.MIN_WARN_RATIO` 到 `QuotaLimits.MAX_WARN_RATIO` 范围内。
4. `CostUsageRecord` 必须有 recordId、tenantId、source、createdAt；修正使用 `MANUAL_ADJUSTMENT` 追加负向或正向调整时也必须有 reasonRef。
5. `SreHealthReport` 总体状态聚合顺序为 `RED` 优先于 `WARN`，`WARN` 优先于 `GREEN`。

#### 13.3.3 Port、服务与 Gate 编排

新增或修改文件：

| 层 | 文件 | 操作 |
| --- | --- | --- |
| Inbound | `QuotaPolicyInboundPort.java` | upsert、disable、find。 |
| Inbound | `QuotaDecisionInboundPort.java` | evaluate decision。 |
| Inbound | `CostUsageInboundPort.java` | append record、aggregate query。 |
| Inbound | `SreHealthInboundPort.java` | generate current report。 |
| Outbound | `QuotaPolicyRepositoryPort.java` | upsert、findActiveByScope、findById。 |
| Outbound | `CostUsageRepositoryPort.java` | append、aggregate。 |
| Outbound | `SreHealthContributorPort.java` | `contribute()` 小接口。 |
| Application | `KernelQuotaDecisionService.java` | quota policy 匹配与 decision 编排。 |
| Application | `KernelCostUsageQueryService.java` | usage append 与 aggregate 编排。 |
| Application | `KernelSreHealthQueryService.java` | contributor 汇总与异常转 warn。 |
| Application | `KernelProductionGateService.java` | quota/SRE contributor 从真实 port 读取 evidence。 |

Quota decision 输入必须包含 `tenantId`、`agentId`、`riskLevel`、`scopeType`、`scopeId`、`window`、`requestedTokens`、`requestedCostMinor`、`requestedCalls`。service 匹配 policy 的优先级使用具名列表 `QuotaPolicyMatchOrder.DEFAULT_ORDER`，避免散落 if/else 复制。

#### 13.3.4 JDBC、Schema 与 Web 合同

新增表：

```sql
CREATE TABLE IF NOT EXISTS sa_quota_policy (
  policy_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  scope_type VARCHAR(32) NOT NULL,
  scope_id VARCHAR(128) NOT NULL,
  window_type VARCHAR(32) NOT NULL,
  max_tokens BIGINT,
  max_cost_minor BIGINT,
  max_calls BIGINT,
  warn_ratio DOUBLE PRECISION NOT NULL,
  status VARCHAR(32) NOT NULL,
  updated_by VARCHAR(64) NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_quota_policy_scope CHECK (scope_type IN ('TENANT', 'AGENT', 'USER', 'TOOL', 'MODEL', 'RUN')),
  CONSTRAINT chk_sa_quota_policy_window CHECK (window_type IN ('DAILY', 'MONTHLY', 'RUN_LIFETIME')),
  CONSTRAINT chk_sa_quota_policy_status CHECK (status IN ('ACTIVE', 'DISABLED')),
  CONSTRAINT chk_sa_quota_policy_limit CHECK (max_tokens IS NOT NULL OR max_cost_minor IS NOT NULL OR max_calls IS NOT NULL)
);

CREATE TABLE IF NOT EXISTS sa_cost_usage_record (
  record_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64),
  run_id VARCHAR(64),
  tool_id VARCHAR(128),
  model_id VARCHAR(128),
  tokens_in BIGINT NOT NULL,
  tokens_out BIGINT NOT NULL,
  cost_minor BIGINT NOT NULL,
  call_count BIGINT NOT NULL,
  source VARCHAR(32) NOT NULL,
  evidence_ref VARCHAR(256),
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_cost_usage_source CHECK (source IN ('MODEL', 'TOOL', 'SANDBOX', 'HANDOFF', 'MANUAL_ADJUSTMENT')),
  CONSTRAINT chk_sa_cost_usage_non_negative CHECK (tokens_in >= 0 AND tokens_out >= 0 AND cost_minor >= 0 AND call_count >= 0)
);
```

Web API：

| Method | Path | 行为 |
| --- | --- | --- |
| `PUT` | `/api/quota-policies/{policyId}` | upsert policy。 |
| `POST` | `/api/quota-policies/{policyId}/disable` | disable policy。 |
| `POST` | `/api/quota-decisions:evaluate` | 返回 decision effect/reason/evidence。 |
| `POST` | `/api/cost-usage-records` | append usage record。 |
| `GET` | `/api/cost-usage:aggregate?tenantId=...&agentId=...&runId=...` | 查询聚合。 |
| `GET` | `/api/sre/health` | 生成并返回 health report。 |

#### 13.3.5 TDD 切片

1. RED：`QuotaPolicyTests#shouldRejectPolicyWithoutAnyLimit`。
2. RED：`QuotaPolicyTests#shouldRejectNegativeLimitsAndOutOfRangeWarnRatio`。
3. RED：`KernelQuotaDecisionServiceTests#shouldWarnLowRiskWhenNoPolicyExists`。
4. RED：`KernelQuotaDecisionServiceTests#shouldRequireApprovalHighRiskWhenNoPolicyExists`。
5. RED：`KernelQuotaDecisionServiceTests#shouldDenyWhenHardLimitExceededAndWarnWhenWarnThresholdExceeded`。
6. RED：`CostUsageRecordTests#shouldRejectNegativeUsageValues`。
7. RED：`KernelCostUsageQueryServiceTests#shouldAggregateByTenantAgentAndRun`。
8. RED：`KernelSreHealthQueryServiceTests#shouldAggregateRedWarnGreenAndConvertContributorExceptionToWarn`。
9. GREEN：实现 domain、ports、services。
10. RED：`JdbcQuotaPolicyRepositoryAdapterTests#shouldUpsertDisableAndFindActivePolicyByScope`。
11. RED：`JdbcCostUsageRepositoryAdapterTests#shouldAppendAndAggregateUsageRecords`。
12. GREEN：实现 JDBC/schema。
13. RED：`SeahorseQuotaControllerTests`、`SeahorseCostUsageControllerTests`、`SeahorseSreHealthControllerTests`。
14. GREEN：实现 Web 与 starter wiring。

#### 13.3.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelCostUsageQueryServiceTests,KernelSreHealthQueryServiceTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseQuotaControllerTests,SeahorseCostUsageControllerTests,SeahorseSreHealthControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

#### 13.3.7 回滚与非目标

回滚时可关闭 quota/cost/SRE Web API 和 gate contributor，但保留 quota policy 与 cost usage 历史。首版不做真实费用扣款、预算审批流、Prometheus exporter、自动熔断 kill、模型动态降级或前端成本面板。若需要运行时强制拦截，应在后续阶段把 `QuotaDecisionInboundPort` 接入 Tool Gateway 或 model invocation owner，而不是在 cost repository 中做副作用。

### 13.4 Phase 8D 执行方案：Canary Rollout 与 Enterprise Pilot Readiness

#### 13.4.1 目标与当前差距

Phase 8D 的目标是补齐企业试点准入和 canary rollout 事实。Phase 6 已经拥有 publish-ready、version activation 和 rollback owner，Phase 8D 不能再建立第二套 active version 事实。当前仍缺 rollout 记录、promote 前 gate 强制检查、rollback 对 Phase 6 owner 的编排，以及可查询的 pilot readiness report。

完成后必须满足：

1. canary rollout 只记录事实和 percent，不实现真实流量分配。
2. promote 前必须读取 latest Production Gate report；缺失或非 `PASS` 均失败。
3. rollback 必须调用 Phase 6 activation/rollback port，不能直接改 active version。
4. pilot readiness report 覆盖九个 check code，并能按 high/critical 与 low/medium 风险区分 fail/warn。
5. readiness evidence refs 只保存引用，不保存 raw prompt、raw eval case、raw tool output、raw ACL payload 或 secret。
6. terminal rollout status 不可回退。

#### 13.4.2 Rollout 领域模型与不变量

新增包：`kernel/domain/agent/rollout/`

| 类型 | 职责 |
| --- | --- |
| `AgentVersionRollout` | 某 agent version 的 canary、pause、promote、rollback 事实。 |
| `AgentRolloutStatus` | `CREATED`、`RUNNING`、`PAUSED`、`PROMOTED`、`ROLLED_BACK`、`FAILED`。 |
| `AgentRolloutFailureCode` | `GATE_MISSING`、`GATE_FAILED`、`INVALID_PERCENT`、`ROLLBACK_FAILED`、`INVALID_STATUS_TRANSITION`。 |
| `AgentRolloutLimits` | `MIN_PERCENT`、`MAX_PERCENT`、`DEFAULT_CANARY_PERCENT`。 |
| `AgentRolloutEventType` | `CANARY_CREATED`、`CANARY_PAUSED`、`CANARY_PROMOTED`、`CANARY_ROLLED_BACK`、`CANARY_FAILED`。 |

规则：

1. `canaryPercent` 必须位于 `AgentRolloutLimits.MIN_PERCENT` 与 `AgentRolloutLimits.MAX_PERCENT` 之间。
2. `PROMOTED`、`ROLLED_BACK`、`FAILED` 为 terminal status，不可转换为 non-terminal。
3. `pause` 对 terminal rollout 幂等返回原状态。
4. `promote` 成功后只记录 `PROMOTED` 与 gate report ref，不直接激活版本；版本激活仍由 Phase 6 owner 执行。
5. `rollback` 成功后记录 `ROLLED_BACK`，并保存 activation rollback evidence ref。

#### 13.4.3 Readiness 领域模型与证据合同

新增包：`kernel/domain/agent/readiness/`

| 类型 | 职责 |
| --- | --- |
| `EnterprisePilotReadinessReport` | 某 agent version 的试点准入 snapshot。 |
| `EnterprisePilotReadinessCheckCode` | `OWNER`、`PUBLISHED_VERSION`、`TOOL_RISK`、`RESOURCE_ACL`、`EVAL`、`QUOTA`、`AUDIT`、`ROLLBACK`、`DISABLE_SWITCH`。 |
| `EnterprisePilotReadinessStatus` | `PASS`、`WARN`、`FAIL`。 |
| `EnterprisePilotReadinessReasonCode` | `EVIDENCE_PRESENT`、`EVIDENCE_MISSING`、`HIGH_RISK_REQUIRED_EVIDENCE_MISSING`、`LOW_RISK_OPTIONAL_EVIDENCE_MISSING`、`GATE_FAILED`。 |

九项检查：

| CheckCode | PASS evidence | 高风险缺失 | 低风险缺失 |
| --- | --- | --- | --- |
| `OWNER` | owner 与 fallback owner 存在 | FAIL | WARN |
| `PUBLISHED_VERSION` | version published 且未 disabled | FAIL | FAIL |
| `TOOL_RISK` | 高风险工具有 approval policy 或 disabled | FAIL | WARN |
| `RESOURCE_ACL` | 资源 ACL 或 Context access evidence 存在 | FAIL | WARN |
| `EVAL` | latest eval summary 非 `FAIL`，高风险非 stale | FAIL | WARN |
| `QUOTA` | active quota policy 或 quota decision evidence 存在 | FAIL | WARN |
| `AUDIT` | audit ledger 可查询关键事件 | FAIL | FAIL |
| `ROLLBACK` | activation history 或 rollback target 存在 | FAIL | FAIL |
| `DISABLE_SWITCH` | agent 与 high-risk tool 均可 disabled | FAIL | FAIL |

#### 13.4.4 Port、服务、JDBC 与 API

新增或修改文件：

| 层 | 文件 | 操作 |
| --- | --- | --- |
| Inbound | `AgentRolloutInboundPort.java` | canary、pause、promote、rollback、latest。 |
| Inbound | `EnterprisePilotReadinessInboundPort.java` | generate、latest。 |
| Outbound | `AgentRolloutRepositoryPort.java` | save、update、findById、findLatest。 |
| Outbound | `EnterprisePilotReadinessRepositoryPort.java` | save、findLatest。 |
| Application | `KernelAgentRolloutService.java` | 编排 gate check 与 Phase 6 rollback owner。 |
| Application | `KernelEnterprisePilotReadinessService.java` | 聚合 readiness evidence。 |
| JDBC | `JdbcAgentRolloutRepositoryAdapter.java` | rollout persistence。 |
| JDBC | `JdbcEnterprisePilotReadinessRepositoryAdapter.java` | readiness persistence。 |
| Web | `SeahorseAgentRolloutController.java` | rollout API。 |
| Web | `SeahorseEnterprisePilotReadinessController.java` | readiness API。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration.java`、`SeahorseAgentRegistryRepositoryAutoConfiguration.java` | wiring。 |

新增表：

```sql
CREATE TABLE IF NOT EXISTS sa_agent_rollout (
  rollout_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  canary_percent INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64),
  gate_report_id VARCHAR(64),
  evidence_json TEXT NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  terminal_at TIMESTAMP,
  CONSTRAINT chk_sa_agent_rollout_status CHECK (status IN ('CREATED', 'RUNNING', 'PAUSED', 'PROMOTED', 'ROLLED_BACK', 'FAILED')),
  CONSTRAINT chk_sa_agent_rollout_percent CHECK (canary_percent >= 0 AND canary_percent <= 100)
);

CREATE TABLE IF NOT EXISTS sa_enterprise_pilot_readiness_report (
  report_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  check_results_json TEXT NOT NULL,
  evidence_refs_json TEXT NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_enterprise_pilot_readiness_status CHECK (status IN ('PASS', 'WARN', 'FAIL'))
);
```

Web API：

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/canary` | 创建 canary rollout。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停 rollout。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | gate pass 后记录 promote。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | 调 Phase 6 rollback owner。 |
| `GET` | `/api/agents/{agentId}/rollouts/latest?tenantId=...&versionId=...` | 查询 latest rollout。 |
| `POST` | `/api/agents/{agentId}/pilot-readiness` | 生成 readiness report。 |
| `GET` | `/api/agents/{agentId}/pilot-readiness/latest?tenantId=...&versionId=...` | 查询 latest readiness。 |

#### 13.4.5 TDD 切片

1. RED：`AgentVersionRolloutTests#shouldRejectCanaryPercentOutsideLimits`。
2. RED：`AgentVersionRolloutTests#shouldKeepTerminalStatusImmutableAndPauseIdempotent`。
3. RED：`KernelAgentRolloutServiceTests#shouldFailPromoteWhenGateReportMissingOrFailed`。
4. RED：`KernelAgentRolloutServiceTests#shouldRollbackThroughAgentVersionActivationPort`。
5. RED：`EnterprisePilotReadinessReportTests#shouldRequireAllNineCheckCodesAndAggregateStatus`。
6. RED：`KernelEnterprisePilotReadinessServiceTests#shouldFailHighRiskMissingEvidenceAndWarnLowRiskMissingOptionalEvidence`。
7. GREEN：实现 domain、ports、services。
8. RED：`JdbcAgentRolloutRepositoryAdapterTests#shouldPersistLatestAndRejectTerminalRollback`。
9. RED：`JdbcEnterprisePilotReadinessRepositoryAdapterTests#shouldPersistAndFindLatestReport`。
10. GREEN：实现 JDBC/schema。
11. RED：`SeahorseAgentRolloutControllerTests#shouldExposeCanaryPausePromoteRollbackAndLatest`。
12. RED：`SeahorseEnterprisePilotReadinessControllerTests#shouldGenerateAndReadLatestReportWithoutRawEvidence`。
13. GREEN：实现 Web 与 starter wiring。

#### 13.4.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,KernelAgentRolloutServiceTests,EnterprisePilotReadinessReportTests,KernelEnterprisePilotReadinessServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests,JdbcAgentVersionActivationRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

#### 13.4.7 回滚与非目标

回滚时关闭 rollout/readiness API，保留 rollout 和 readiness 历史记录。普通 Phase 6 publish/rollback 不受影响。首版不做真实百分比分流器、灰度网关、前端发布向导、自动试点报告推送或跨 Agent mesh rollout。

## 14. Phase 7 收口后的当前最新执行判定

截至本节，当前剩余主实现阶段按以下顺序推进：

1. Phase 8B Agent Eval Summary Gate：让 Production Gate 读取 version-level eval summary，先完成 eval summary domain/repository/query，再接 gate contributor。
2. Phase 8C Quota、Cost Usage 与 SRE Health：补 quota decision、cost append/aggregate、health contributor 聚合，并让 gate 读取真实 evidence。
3. Phase 8D Canary Rollout 与 Enterprise Pilot Readiness：补 rollout 事实、promote gate、rollback 编排和九项 readiness report。

Phase 4、Phase 5、Phase 7 目前作为后续阶段的 focused regression 依赖，不再作为剩余主实现阶段。Phase 8B 完成前不得声称企业生产化硬化完成；Phase 8C 完成前不得声称运行成本和 SRE 治理完成；Phase 8D 完成前不得声称企业试点准入完成。

## 15. 2026-05-26 再次深读后的剩余阶段精细开发方案

本节基于再次阅读 `docs/company-agent/` 与 `docs/company-agent/ai-infra-phases/` 后追加，目标是给当前仍未完成的每个主实现阶段补一份更细的开发方案。旧的 `99-current-implementation-handoff.md` 仍只作为历史背景；它的 Approval query/decision API 顺序已经被后续 Phase 3/4/5/6/7 实现证据 supersede。当前权威状态是：Phase 4、Phase 5、Phase 7 作为回归依赖，Phase 8B、Phase 8C、Phase 8D 仍是主实现阶段。

### 15.1 共同收口规则

1. Phase 8B、8C、8D 每个阶段都必须先补缺失层的 RED 测试，再做最小 GREEN；不能因为 kernel 已有一部分实现就跳过 JDBC、Web、starter 的 RED。
2. 每个阶段结束时必须至少产生四类证据：kernel owner-boundary test、JDBC adapter test、Web contract test、starter wiring test。
3. Phase 8B/C/D 对 Production Gate 的修改只能添加 contributor 编排，不允许把 eval、quota、cost、rollout 或 readiness 的领域不变量塞进 gate service。
4. 所有 evidence 字段只保存引用、summary 或 counts；不得保存 raw prompt、raw eval case、raw tool output、secret、完整 ACL payload、完整 sandbox input。
5. 所有新增表都只追加，不重写已有 Phase 1-7 表；修正错误记录通过追加新 snapshot 或新 usage/rollout/readiness record 表达。
6. Phase 4/5/7 focused regression 必须在 Phase 8D 完成前至少各跑一次，证明 eval/quota/readiness 未破坏 ACL、connector/sandbox audit 和 handoff。
7. 不使用并行 Maven 命令跑同一个模块，避免多个进程同时写同一 `target/classes` 造成伪失败。

### 15.2 Phase 8B 精细方案：Eval Summary 外层收口与 Gate 可解释化

#### 15.2.1 当前实现校准

当前代码表面已经出现 kernel 级 `AgentEvalSummary`、`AgentEvalType`、`AgentEvalStatus`、`AgentEvalLimits`、`AgentEvalSummaryPage`、`AgentEvalInboundPort`、`AgentEvalSummaryRepositoryPort` 和 `KernelAgentEvalQueryService`。这说明 Phase 8B 不是从零开始，而是需要完成外层闭环：

1. JDBC 适配器和 `sa_agent_eval_summary` schema 尚未形成可验证闭环。
2. Web API 尚未暴露 summary append/latest/history。
3. starter 尚未证明 `AgentEvalInboundPort`、`AgentEvalSummaryRepositoryPort` 和 `KernelProductionGateService` 的 eval repository 注入关系。
4. `KernelProductionGateService` 的 eval status severity 必须使用显式 rank 或领域方法，不应依赖 enum ordinal。

#### 15.2.2 最小可交付范围

| 能力 | 交付 | 非目标 |
| --- | --- | --- |
| Summary append | 保存人工或外部 eval runner 产出的 version-level summary | 不启动 eval run，不创建 eval dataset/case 管理 UI |
| Latest/history | 按 tenant、agent、version、evalType 隔离查询 | 不做全文搜索和复杂指标分析 |
| Gate contributor | Production Gate 输出可解释 eval item | 不把 gate 变成 eval 平台 |
| Redaction | API/JDBC 不接收 raw 样本字段 | 不做通用脱敏引擎 |

#### 15.2.3 文件级开发顺序

1. 修正 kernel gate severity：
   - 修改 `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/gate/KernelProductionGateService.java`。
   - 新增私有方法 `private static int gateSeverity(ProductionGateStatus status)` 或在 gate domain 放具名 helper。
   - 测试覆盖 `WARN` 不会覆盖已记录的 `FAIL`，`PASS` 不会覆盖 `WARN`。
2. JDBC RED：
   - 新增 `JdbcAgentEvalSummaryRepositoryAdapterTests.java`。
   - 覆盖 append、findLatest、findHistory、tenant/version/evalType 隔离、history 倒序、enum mapping。
3. JDBC GREEN：
   - 新增 `JdbcAgentEvalSummaryRepositoryAdapter.java`。
   - 更新 `agent-registry-run-store-postgresql.sql`，追加 `sa_agent_eval_summary` 表与 latest index。
   - `evidence_json` 只序列化 `List<String>` 或轻量 evidence refs，不接受 raw 字段。
4. Web RED：
   - 新增 `SeahorseAgentEvalControllerTests.java`。
   - 覆盖 `POST /api/agents/{agentId}/versions/{versionId}/eval-summaries`。
   - 覆盖 latest/history 查询。
   - 断言响应和 request DTO 无 `rawCase`、`rawPrompt`、`rawToolOutput`、`secret`、`sampleInput`、`sampleOutput` 字段。
5. Web GREEN：
   - 新增 `SeahorseAgentEvalController.java`。
   - DTO 只转换 command/query，不复制 gate 判定。
6. Starter RED/GREEN：
   - 更新 `SeahorseAgentRegistryAutoConfigurationTests`。
   - repository auto-config 暴露 `AgentEvalSummaryRepositoryPort`。
   - kernel registry auto-config 暴露 `KernelAgentEvalQueryService` 并把可选 eval repository 注入 `KernelProductionGateService`。

#### 15.2.4 关键测试样例

1. `JdbcAgentEvalSummaryRepositoryAdapterTests#shouldFindLatestByTenantAgentVersionAndType`：
   - 保存同 agent 不同 version 的 `SAFETY` summary。
   - 保存同 version 不同 evalType 的 summary。
   - latest 查询只能返回完全匹配的一条。
2. `JdbcAgentEvalSummaryRepositoryAdapterTests#shouldKeepHistoryDescending`：
   - 保存三条不同 `createdAt`。
   - history 返回按 `createdAt DESC, summaryId DESC` 稳定排序。
3. `SeahorseAgentEvalControllerTests#shouldRejectOrIgnoreRawEvaluationFields`：
   - 用 JSON 提交包含 raw 字段的 payload。
   - 期望 API 不把 raw 字段落入 response；若项目已有未知字段拒绝策略，则期望 `4xx`。
4. `SeahorseAgentRegistryAutoConfigurationTests#shouldWireEvalRepositoryIntoProductionGate`：
   - 通过反射或实际 gate check 证明 `KernelProductionGateService` 使用 JDBC eval repository。

#### 15.2.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentEvalSummaryTests,KernelAgentEvalQueryServiceTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentEvalSummaryRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentEvalControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
rg -n "rawCase|rawPrompt|rawToolOutput|sampleInput|sampleOutput|secret-token" seahorse-agent-kernel/src/main/java seahorse-agent-adapter-web/src/main/java seahorse-agent-adapter-repository-jdbc/src/main/java
rg -n "org\.springframework|javax\.sql|java\.sql|RestTemplate|WebClient" seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports
git diff --check
```

#### 15.2.6 完成信号

Phase 8B 只在以下条件同时成立时完成：eval summary 可以从 Web 进入 JDBC；Gate 可以基于 latest summary 产生 PASS/WARN/FAIL；high-risk 缺失或 stale eval fail closed；low-risk 缺失或 stale eval warn；starter 自动装配可证明；无 raw eval 内容进入 API、JDBC 或 gate item。

### 15.3 Phase 8C 精细方案：Quota/Cost/SRE 三条证据链

#### 15.3.1 当前实现校准

当前 Production Gate 已经有 quota/SRE foundation 文案，但没有真实 quota policy、cost usage append/aggregate、SRE contributor 聚合。因此 Phase 8C 应拆成三条独立但可组合的证据链，最后再接 gate：

1. Quota：回答“这次请求为什么允许、警告、要求审批或拒绝”。
2. Cost：回答“某个 tenant/agent/run 已消耗多少 token/cost/calls”。
3. SRE：回答“当前依赖是否健康，异常 contributor 是否被暴露为 warn/red”。

#### 15.3.2 最小可交付范围

| 证据链 | 首版交付 | 非目标 |
| --- | --- | --- |
| Quota | policy upsert/disable/find，decision evaluate | 不接入真实扣费，不做分布式限流 |
| Cost | append usage record，aggregate by tenant/agent/run | 不生成账单，不做货币换算 |
| SRE | contributor port 聚合，exception 转 WARN | 不做 Prometheus exporter，不做自动扩缩容 |
| Gate | 读取 quota policy/decision 和 SRE report evidence | 不阻塞所有 run，不做自动 kill |

#### 15.3.3 领域分工

1. `QuotaPolicy` 维护 limit 不变量：tokens/cost/calls 至少一个非空，数值非负，warnRatio 在具名范围内。
2. `QuotaDecision` 或 `QuotaDecisionResult` 维护 effect、reasonCode、matchedPolicyId、requestedUsage、currentUsage、threshold 信息。
3. `CostUsageRecord` 维护 append-only 事实，不允许更新；手工修正使用 `MANUAL_ADJUSTMENT` source 追加记录。
4. `CostUsageAggregate` 只表示查询结果，不拥有 persistence 行为。
5. `SreHealthReport` 聚合 contributor item，status rank 显式定义为 `RED > WARN > GREEN`。
6. `SreHealthContributorPort` 是小接口，任何 adapter 抛异常都由 `KernelSreHealthQueryService` 转为 `WARN` item。

#### 15.3.4 文件级开发顺序

1. Kernel RED：
   - `QuotaPolicyTests` 覆盖无 limit、负值、warnRatio 越界。
   - `KernelQuotaDecisionServiceTests` 覆盖无 policy 默认 WARN/REQUIRE_APPROVAL、hard DENY、warn WARN。
   - `CostUsageRecordTests` 覆盖 usage 非负、必填字段、manual adjustment reasonRef。
   - `KernelCostUsageQueryServiceTests` 覆盖 append 后按 tenant/agent/run 聚合。
   - `KernelSreHealthQueryServiceTests` 覆盖 RED/WARN/GREEN rank、contributor exception 转 WARN。
2. Kernel GREEN：
   - 新增 `domain/agent/quota`、`domain/agent/cost`、`domain/agent/sre`。
   - 新增 inbound/outbound 小 port，保持 quota policy、quota decision、cost usage、SRE health 分离。
3. JDBC RED/GREEN：
   - `JdbcQuotaPolicyRepositoryAdapterTests` 覆盖 upsert、disable、find active。
   - `JdbcCostUsageRepositoryAdapterTests` 覆盖 append-only 和 aggregate。
   - schema 追加 `sa_quota_policy`、`sa_cost_usage_record`，用 check constraint 锁定 enum 和非负数。
4. Web RED/GREEN：
   - `SeahorseQuotaControllerTests` 覆盖 upsert/disable/evaluate。
   - `SeahorseCostUsageControllerTests` 覆盖 append/aggregate。
   - `SeahorseSreHealthControllerTests` 覆盖 contributor exception response。
5. Starter RED/GREEN：
   - 证明 quota/cost/SRE inbound beans 存在。
   - 证明 `KernelProductionGateService` 可以读取 quota/SRE evidence port。

#### 15.3.5 Quota decision 规则细化

| 输入状态 | risk | effect | reason |
| --- | --- | --- | --- |
| 无 active policy | LOW/MEDIUM | `WARN` | `NO_POLICY_LOW_RISK` |
| 无 active policy | HIGH/CRITICAL | `REQUIRE_APPROVAL` | `NO_POLICY_HIGH_RISK` |
| active policy disabled | 任意 | `WARN` | `POLICY_DISABLED` |
| 任一 hard limit 超出 | 任意 | `DENY` | `HARD_LIMIT_EXCEEDED` |
| 任一 warn threshold 超出 | 任意 | `WARN` | `WARN_THRESHOLD_EXCEEDED` |
| policy 匹配且未超限 | 任意 | `ALLOW` | `POLICY_MATCHED` |

Policy 匹配顺序必须用 `QuotaPolicyMatchOrder.DEFAULT_ORDER` 固化，建议首版顺序为 `RUN`、`AGENT`、`USER`、`TOOL`、`MODEL`、`TENANT`。如果同一 scope 有多条 active policy，repository 必须稳定返回最近 `updatedAt` 的一条，或 service 明确拒绝歧义；不能随机选择。

#### 15.3.6 Gate 集成细化

1. `QUOTA_CONFIGURED`：
   - 有 active tenant 或 agent quota policy 时 `PASS`。
   - 无 policy 且 high/critical risk 时 `FAIL` 或 `WARN + REQUIRE_APPROVAL` 需按现有 gate status 语义选一种，并写测试固定。
   - 无 policy 且 low/medium risk 时 `WARN`。
2. `SRE_HEALTH_GREEN`：
   - latest generated report 为 `GREEN` 时 `PASS`。
   - `WARN` 时 gate `WARN`。
   - `RED` 或 contributor repository 不可用时 gate `FAIL` 或 `WARN`，必须在测试中固定。
3. Gate item evidence 只包含 policyId、reportId、aggregate key、contributor name、reasonCode，不包含底层异常堆栈全文。

#### 15.3.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelCostUsageQueryServiceTests,KernelSreHealthQueryServiceTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseQuotaControllerTests,SeahorseCostUsageControllerTests,SeahorseSreHealthControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
rg -n "org\.springframework|javax\.sql|java\.sql|RestTemplate|WebClient" seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports
git diff --check
```

#### 15.3.8 完成信号

Phase 8C 完成时，系统必须能给出三类可查询证据：quota decision 的 effect/reason/policy evidence、cost usage 的 append-only 记录和 aggregate、SRE health 的 contributor 明细与异常降级记录。Production Gate 不能再只输出固定 foundation warn 文案。

### 15.4 Phase 8D 精细方案：Rollout 事实与试点准入报告

#### 15.4.1 当前实现校准

Phase 6 已经拥有 publish-ready、version activation 和 rollback owner。Phase 8D 的核心风险是重复建立第二套 active version 事实，因此本阶段必须把 rollout 限定为“事实记录 + gate 强制检查 + rollback 编排 + readiness report”，不实现真实百分比分流。

#### 15.4.2 最小可交付范围

| 能力 | 首版交付 | 非目标 |
| --- | --- | --- |
| Canary rollout | 创建、暂停、查询 latest rollout 事实 | 不做真实流量路由 |
| Promote | promote 前强制 latest Production Gate `PASS` | 不直接激活版本 |
| Rollback | 调用 Phase 6 activation/rollback owner | 不改写 active version 表 |
| Readiness | 生成九项企业试点准入 report | 不生成前端试点向导 |

#### 15.4.3 文件级开发顺序

1. Rollout domain RED：
   - `AgentVersionRolloutTests#shouldRejectCanaryPercentOutsideLimits`。
   - `AgentVersionRolloutTests#shouldKeepTerminalStatusImmutableAndPauseIdempotent`。
   - `AgentVersionRolloutTests#shouldRequireGateReportRefWhenPromoted`。
2. Rollout service RED：
   - `KernelAgentRolloutServiceTests#shouldFailPromoteWhenGateReportMissing`。
   - `KernelAgentRolloutServiceTests#shouldFailPromoteWhenGateReportNotPass`。
   - `KernelAgentRolloutServiceTests#shouldRollbackThroughAgentVersionActivationPort`。
3. Readiness domain RED：
   - `EnterprisePilotReadinessReportTests#shouldRequireAllNineCheckCodesAndAggregateStatus`。
   - `EnterprisePilotReadinessReportTests#shouldRejectRawEvidenceFields`。
4. Readiness service RED：
   - `KernelEnterprisePilotReadinessServiceTests#shouldFailHighRiskMissingRequiredEvidence`。
   - `KernelEnterprisePilotReadinessServiceTests#shouldWarnLowRiskMissingOptionalEvidence`。
5. JDBC RED/GREEN：
   - `JdbcAgentRolloutRepositoryAdapterTests` 覆盖 save、latest、terminal update 防回退。
   - `JdbcEnterprisePilotReadinessRepositoryAdapterTests` 覆盖 save、latest、JSON evidence refs。
6. Web RED/GREEN：
   - `SeahorseAgentRolloutControllerTests` 覆盖 canary/pause/promote/rollback/latest。
   - `SeahorseEnterprisePilotReadinessControllerTests` 覆盖 generate/latest 与 raw evidence 禁止。
7. Starter RED/GREEN：
   - 证明 rollout/readiness repository、service、inbound port 自动装配。

#### 15.4.4 Readiness evidence source 映射

| CheckCode | 读取来源 | 首版判定 |
| --- | --- | --- |
| `OWNER` | Agent Definition repository | owner/fallback owner 同时存在为 PASS |
| `PUBLISHED_VERSION` | Agent Version repository | published 且未 disabled 为 PASS |
| `TOOL_RISK` | Agent version tool snapshot 或 Tool Catalog | 高风险工具有 approval policy 或 disabled 为 PASS |
| `RESOURCE_ACL` | Resource ACL repository 或 context access audit | 有相关 ACL/access evidence 为 PASS |
| `EVAL` | Agent Eval Summary repository | high-risk 必须 latest PASS/WARN 且非 stale；FAIL 直接 FAIL |
| `QUOTA` | Quota policy repository 或 quota decision evidence | active tenant/agent policy 或最近 decision evidence 为 PASS |
| `AUDIT` | Audit event repository | 可查关键事件为 PASS |
| `ROLLBACK` | Version activation history | 有 rollback target 或 activation history 为 PASS |
| `DISABLE_SWITCH` | Agent status/tool status 管理能力 | agent 可 disable 且 high-risk tool 可 disable 为 PASS |

Readiness service 可以先通过小型 evidence ports 读取这些来源；若某来源当前还没有专用 port，应新增最小 query port，而不是让 readiness service 直接依赖 JDBC adapter 或 Web DTO。

#### 15.4.5 Promote 与 rollback 规则

1. `promote` 必须先查询 latest gate report：
   - report 缺失：rollout 标记 `FAILED`，failureCode=`GATE_MISSING`。
   - report status 非 `PASS`：rollout 标记 `FAILED`，failureCode=`GATE_FAILED`。
   - report `PASS`：rollout 标记 `PROMOTED`，保存 gateReportId。
2. `promote` 不调用 activation port；若后续需要正式激活，应由 Phase 6 publish/activation owner 接收 promoted evidence 后执行。
3. `rollback` 必须调用 Phase 6 activation/rollback port：
   - 成功：rollout 标记 `ROLLED_BACK`，保存 rollback evidence ref。
   - 失败：rollout 标记 `FAILED`，failureCode=`ROLLBACK_FAILED`。
4. `pause` 对 terminal rollout 返回原对象，不报错；对 running rollout 转 `PAUSED`。

#### 15.4.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,KernelAgentRolloutServiceTests,EnterprisePilotReadinessReportTests,KernelEnterprisePilotReadinessServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests,JdbcAgentVersionActivationRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,AgentHandoffContextPolicyTests,DefaultMeshPolicyPortTests,LocalAgentAsToolPortTests,KernelAgentHandoffServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,AuditedResourceAccessPolicyPortTests,ResourceAclImportDryRunTests' test
rg -n "rawPrompt|rawCase|rawToolOutput|rawAcl|secret-token|sampleInput|sampleOutput" seahorse-agent-kernel/src/main/java seahorse-agent-adapter-web/src/main/java seahorse-agent-adapter-repository-jdbc/src/main/java
git diff --check
```

#### 15.4.7 完成信号

Phase 8D 完成时，企业试点准入必须能通过 API 查询 latest readiness report，report 覆盖九项 check code，并能解释每项 PASS/WARN/FAIL 的 evidence ref。Canary rollout 必须能记录创建、暂停、promote、rollback 事实；promote 必须 fail closed；rollback 必须走 Phase 6 owner。

## 16. 再次深读后的当前最新执行判定

截至本节，当前剩余主实现阶段仍然只有 Phase 8B、Phase 8C、Phase 8D，但执行粒度更新为：

1. Phase 8B Eval Summary Gate 收口：先完成 JDBC/Web/starter 和 gate severity 显式化，再跑 Phase 8B focused regression。
2. Phase 8C Quota/Cost/SRE：先建立 quota decision、cost aggregate、SRE contributor 三条证据链，再替换 Production Gate 的固定 foundation warn。
3. Phase 8D Rollout/Readiness：先建立 rollout 事实和 readiness report，再通过 Phase 6 owner 编排 rollback，并补 Phase 4/5/7 回归。

Phase 4、Phase 5、Phase 7 不再新增主实现方案；它们是 Phase 8D 前必须保留的安全、授权、审计和 handoff 回归依赖。本文档仍是设计开发方案，不是完成声明；只有对应 RED/GREEN、focused regression、kernel dependency scan、raw evidence scan 和 Aegis evidence 更新全部完成后，才能对相应阶段做完成判断。

## 17. 2026-05-26 当前实现校准后的未完成阶段精细开发方案

本节基于再次深读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、当前 Aegis evidence 以及当前 worktree 代码面后的最新判定。旧交接文档中的 Approval query/decision API 已不是当前主线；Phase 4、Phase 5、Phase 7 已有 focused regression 证据，Phase 8B Eval Summary Gate 已完成外层 JDBC/Web/starter 收口并通过 focused regression。当前仍需补方案并继续实现的主阶段是 Phase 8C 与 Phase 8D。

### 17.1 当前阶段状态校准

| 阶段 | 当前状态 | 后续角色 |
| --- | --- | --- |
| Phase 4 Resource ACL | 已有 import commit、audit、DB 边界和 focused regression 证据 | Phase 8D 前的授权与上下文访问回归依赖 |
| Phase 5 Connector/Sandbox | 已有 connector/sandbox audit、credential rotation、OAuth、Web/JDBC/starter focused regression 证据 | Phase 8D 前的外部系统安全回归依赖 |
| Phase 7 Local Agent-as-Tool | 已有 handoff starter/tool-registration focused regression 证据 | Phase 8D 前的 multi-agent/handoff 回归依赖 |
| Phase 8B Eval Summary Gate | 已完成 eval summary JDBC/Web/starter 与 gate 集成 focused regression | Phase 8C/8D 的评估证据依赖 |
| Phase 8C Quota/Cost/SRE | kernel domain、ports、services 与 gate hook 已经出现并通过 kernel focused regression；JDBC/Web/starter 仍缺闭环 | 当前第一未完成阶段 |
| Phase 8D Rollout/Readiness | 未见 rollout/readiness domain、ports、JDBC、Web、starter owner 文件 | 当前第二未完成阶段 |

### 17.2 Phase 8C 详细方案：Quota/Cost/SRE 外层闭环

#### 17.2.1 当前差距

Phase 8C 的 kernel 层已经具备最小领域闭环：`QuotaPolicy`、`CostUsageRecord`、`SreHealthReport`、`KernelQuotaDecisionService`、`KernelCostUsageQueryService`、`KernelSreHealthQueryService` 以及 Production Gate quota/SRE hook 已经出现。阶段仍未完成，因为 quota policy 和 cost usage 还不能通过 JDBC 持久化、不能通过 Web API 管理/查询，也不能由 starter 自动装配到生产可用路径。

#### 17.2.2 开发目标

1. quota policy 支持 upsert、disable、find active，且按 tenant/scope/subject 稳定命中。
2. cost usage 支持 append-only 写入，按 tenant/agent/run 聚合 input tokens、output tokens、cost amount、call count。
3. SRE health 通过 Web 暴露聚合结果，contributor 抛异常时返回 `WARN` item，不泄露堆栈全文。
4. starter 默认装配 `QuotaPolicyRepositoryPort`、`CostUsageRepositoryPort`、`QuotaManagementInboundPort`、`CostUsageInboundPort`、`SreHealthInboundPort`。
5. `KernelProductionGateService` 在 starter 环境中能拿到 quota repository 与 SRE report provider，不再退回固定 foundation warning。

#### 17.2.3 文件边界

| 层 | 文件 | 动作 |
| --- | --- | --- |
| JDBC test | `seahorse-agent-adapter-repository-jdbc/src/test/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcQuotaPolicyRepositoryAdapterTests.java` | 新增 RED：upsert、disable、find active、同 scope 最新 active policy 稳定返回 |
| JDBC test | `seahorse-agent-adapter-repository-jdbc/src/test/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcCostUsageRepositoryAdapterTests.java` | 新增 RED：append 多条 usage、按 tenant/agent/run aggregate、无 update 覆盖语义 |
| JDBC main | `JdbcQuotaPolicyRepositoryAdapter.java` | 新增 adapter，实现 `QuotaPolicyRepositoryPort`，只做 SQL 映射和 enum 转换 |
| JDBC main | `JdbcCostUsageRepositoryAdapter.java` | 新增 adapter，实现 `CostUsageRepositoryPort`，保持 append-only |
| SQL | `agent-registry-run-store-postgresql.sql` | 追加 `sa_quota_policy`、`sa_cost_usage_record` 表、索引和 enum/非负 check constraint |
| Web test | `SeahorseQuotaControllerTests.java` | 新增 RED：policy upsert、disable、decision evaluate |
| Web test | `SeahorseCostUsageControllerTests.java` | 新增 RED：append usage、aggregate query |
| Web test | `SeahorseSreHealthControllerTests.java` | 新增 RED：health response、异常 contributor 降级 |
| Web main | `SeahorseQuotaController.java` | 新增 controller，只做 DTO 到 command/query 转换 |
| Web main | `SeahorseCostUsageController.java` | 新增 controller，禁止接收 raw prompt、raw tool output、secret 字段 |
| Web main | `SeahorseSreHealthController.java` | 新增 controller，返回 report snapshot |
| Starter test | `SeahorseAgentRegistryAutoConfigurationTests.java` | 扩展断言 quota/cost/SRE beans 与 gate 注入 |
| Starter main | `SeahorseAgentRegistryRepositoryAutoConfiguration.java` | 装配 JDBC quota/cost repository |
| Starter main | `SeahorseAgentKernelRegistryAutoConfiguration.java` | 装配 quota/cost/SRE services，并把 quota/SRE 注入 production gate |

#### 17.2.4 数据库合同

`sa_quota_policy`：

| 字段 | 约束 |
| --- | --- |
| `policy_id` | primary key，不可复用 |
| `tenant_id` | 必填，用于租户隔离 |
| `scope` | `TENANT`、`AGENT`、`USER`、`TOOL`、`MODEL`、`RUN` |
| `subject_id` | 必填；tenant scope 使用 tenant id |
| `status` | `ACTIVE`、`DISABLED` |
| `token_limit`、`cost_limit`、`call_limit` | 至少一个非空，均不得为负 |
| `warn_ratio` | 大于 `0` 且小于等于 `1` |
| `effect` | `WARN`、`DENY`、`REQUIRE_APPROVAL`，禁止自由文本 |
| `created_at`、`updated_at` | 必填；同 scope 多条 active 时按 `updated_at DESC, policy_id DESC` 稳定选择 |

`sa_cost_usage_record`：

| 字段 | 约束 |
| --- | --- |
| `usage_id` | primary key，append-only |
| `tenant_id` | 必填 |
| `agent_id`、`run_id` | 可空但参与聚合过滤 |
| `source` | `MODEL_CALL`、`TOOL_CALL`、`SANDBOX_EXECUTION`、`REMOTE_AGENT_CALL`、`MANUAL_ADJUSTMENT` |
| `input_tokens`、`output_tokens`、`cost_amount` | 非负 |
| `occurred_at` | 必填，aggregate 按时间窗过滤 |

JDBC adapter 不承担 quota 决策逻辑；超限、warn threshold、risk fallback 仍由 kernel service 判定。

#### 17.2.5 API 合同

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/quotas/policies` | upsert policy，request 中状态、scope、effect 都按 enum 解析 |
| `POST` | `/api/quotas/policies/{policyId}/disable` | 幂等禁用，返回禁用后的 policy snapshot |
| `POST` | `/api/quotas/decisions:evaluate` | 只返回 decision，不直接修改 run 状态 |
| `POST` | `/api/cost-usage-records` | append usage record，禁止覆盖已有 usage |
| `GET` | `/api/cost-usage:aggregate` | 按 tenant/agent/run/time window 返回 aggregate |
| `GET` | `/api/sre/health` | 返回聚合 health report 和 contributor items |

Web DTO 不复制业务判断。所有 reason code、scope、status、source、effect 使用已有 enum；如果 request 出现未知 enum，按项目现有 Web 错误响应方式返回 4xx。

#### 17.2.6 TDD 执行顺序

1. 写 `JdbcQuotaPolicyRepositoryAdapterTests` 和 `JdbcCostUsageRepositoryAdapterTests`，先运行确认 RED。
2. 实现 JDBC adapter 与 SQL schema，运行 JDBC focused GREEN。
3. 写 `SeahorseQuotaControllerTests`、`SeahorseCostUsageControllerTests`、`SeahorseSreHealthControllerTests`，先运行确认 RED。
4. 实现 Web controllers，运行 Web focused GREEN。
5. 扩展 starter auto-configuration tests，先运行确认 RED。
6. 实现 starter repository/service/gate wiring，运行 starter GREEN。
7. 串行运行 Phase 8C kernel、JDBC、Web、starter focused regression。
8. 运行 kernel forbidden dependency scan、raw evidence scan、`git diff --check`。
9. 更新 Aegis checkpoint/evidence，明确 Phase 8C 是 focused complete 还是仍需补缺。

#### 17.2.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelCostUsageQueryServiceTests,KernelSreHealthQueryServiceTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseQuotaControllerTests,SeahorseCostUsageControllerTests,SeahorseSreHealthControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
rg -n "org\.springframework|javax\.sql|java\.sql|RestTemplate|WebClient" seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports
rg -n "rawPrompt|rawToolOutput|secret-token|sampleInput|sampleOutput|stackTrace" seahorse-agent-kernel/src/main/java seahorse-agent-adapter-web/src/main/java seahorse-agent-adapter-repository-jdbc/src/main/java
git diff --check
```

#### 17.2.8 回滚与非目标

回滚时可关闭 quota/cost/SRE Web API 和 starter wiring，但保留 `sa_cost_usage_record` 历史记录作为运营证据。Phase 8C 不做真实扣费、不做分布式限流、不做 Prometheus exporter、不让 quota service 直接改写 run 状态。

### 17.3 Phase 8D 详细方案：Rollout 事实与 Enterprise Pilot Readiness

#### 17.3.1 当前差距

当前代码面未见 `rollout` 或 `readiness` domain、port、JDBC adapter、Web controller、starter wiring。Phase 6 已经拥有 publish-ready、version activation 与 rollback owner；Phase 8D 必须复用这些 owner，避免建立第二套 active version 事实。

#### 17.3.2 开发目标

1. Canary rollout 只记录事实：create、pause、promote、rollback、latest query，不做真实流量路由。
2. Promote 前必须读取 latest Production Gate report；缺失或非 `PASS` 时 fail closed。
3. Rollback 必须通过 Phase 6 activation/rollback port 编排，不能由 rollout repository 直接改 active version。
4. Enterprise Pilot Readiness report 覆盖九项 check code，并只保存 evidence refs，不保存 prompt、eval case、raw tool output 或 secret。
5. Web 和 JDBC 提供最小可查询闭环；starter 自动装配 rollout/readiness service。

#### 17.3.3 领域模型

| 对象 | 职责 |
| --- | --- |
| `AgentVersionRollout` | 维护 rolloutId、tenantId、agentId、versionId、canaryPercent、status、failureCode、gateReportId、startedBy、timestamps |
| `AgentRolloutStatus` | `CREATED`、`RUNNING`、`PAUSED`、`PROMOTED`、`ROLLED_BACK`、`FAILED` |
| `AgentRolloutFailureCode` | `GATE_MISSING`、`GATE_FAILED`、`INVALID_PERCENT`、`ROLLBACK_FAILED`、`ROLLBACK_TARGET_MISSING` |
| `AgentRolloutLimits` | `MIN_CANARY_PERCENT`、`MAX_CANARY_PERCENT`、`DEFAULT_CANARY_PERCENT` |
| `EnterprisePilotReadinessReport` | 保存九项 check result、overall status、evidence refs、createdAt |
| `EnterprisePilotReadinessCheckCode` | `OWNER`、`PUBLISHED_VERSION`、`TOOL_RISK`、`RESOURCE_ACL`、`EVAL`、`QUOTA`、`AUDIT`、`ROLLBACK`、`DISABLE_SWITCH` |
| `EnterprisePilotReadinessStatus` | `PASS`、`WARN`、`FAIL`，severity 显式排序 |

领域规则：

1. canaryPercent 必须在 `AgentRolloutLimits` 范围内。
2. `PROMOTED`、`ROLLED_BACK`、`FAILED` 是 terminal status，不能回退。
3. `pause` 对 terminal rollout 幂等返回当前对象，对 running rollout 转 `PAUSED`。
4. `promote` 成功必须有 gateReportId；失败必须有 failureCode。
5. readiness report 必须包含九项 check code；缺项不能构造成功。
6. readiness evidence 只能是 ref、id、status、reasonCode、timestamp；不能是 raw 内容。

#### 17.3.4 Port 与服务边界

| Port/Service | 职责 |
| --- | --- |
| `AgentRolloutInboundPort` | create canary、pause、promote、rollback、latest |
| `AgentRolloutRepositoryPort` | save、findById、findLatest、updateStatus，保持 terminal 不回退 |
| `EnterprisePilotReadinessInboundPort` | generate report、latest report |
| `EnterprisePilotReadinessRepositoryPort` | save、findLatest |
| `KernelAgentRolloutService` | 编排 gate report、rollout 状态、rollback owner，不拥有 publish/activation 事实 |
| `KernelEnterprisePilotReadinessService` | 聚合九项 evidence source，生成 report snapshot |

Readiness evidence source 按小接口读取。已有 repository port 能满足的直接复用；缺少专用查询能力时新增最小 query port，不能让 readiness service 依赖 JDBC adapter、Spring bean、Web DTO 或 SQL。

#### 17.3.5 Readiness 检查规则

| CheckCode | PASS 条件 | WARN 条件 | FAIL 条件 |
| --- | --- | --- | --- |
| `OWNER` | agent owner 与 fallback owner 均存在 | fallback owner 缺失且风险非高 | high-risk agent owner 缺失 |
| `PUBLISHED_VERSION` | version 已发布且未 disabled | version 已发布但缺少部分发布说明 | 无 published version |
| `TOOL_RISK` | 高风险工具都有 approval policy 或 disabled | 仅低风险工具缺少补充说明 | 高风险写操作工具无审批且 enabled |
| `RESOURCE_ACL` | 绑定资源有 ACL/access decision evidence | 低风险 knowledge agent 暂无资源绑定 | 高风险或企业数据资源缺 ACL evidence |
| `EVAL` | latest eval summary 非 `FAIL` 且高风险不 stale | 低风险缺 eval 或 stale | 高风险缺 eval、stale 或 `FAIL` |
| `QUOTA` | tenant/agent active quota policy 存在 | 低风险缺 quota | 高风险缺 quota |
| `AUDIT` | 关键事件 repository 可查询 | 非关键事件缺少样本 | audit repository 不可用 |
| `ROLLBACK` | 有 activation history 或 rollback target | 只有单版本但低风险 | 高风险无 rollback evidence |
| `DISABLE_SWITCH` | agent 与高风险 tool 均可 disable | 低风险 tool 缺 disable evidence | 高风险 tool 或 agent 不可 disable |

Overall status 聚合规则：任一 `FAIL` 则 overall `FAIL`；无 `FAIL` 且任一 `WARN` 则 overall `WARN`；全部 `PASS` 才是 overall `PASS`。

#### 17.3.6 JDBC 与 API 合同

`sa_agent_version_rollout`：

| 字段 | 约束 |
| --- | --- |
| `rollout_id` | primary key |
| `tenant_id`、`agent_id`、`version_id` | 必填 |
| `canary_percent` | `AgentRolloutLimits` 范围内 |
| `status` | `AgentRolloutStatus` enum |
| `failure_code` | nullable，但 failed 时必填 |
| `gate_report_id` | promoted 时必填 |
| `started_by`、`started_at`、`updated_at` | 必填 |
| `finished_at` | terminal 时写入 |

`sa_enterprise_pilot_readiness_report`：

| 字段 | 约束 |
| --- | --- |
| `report_id` | primary key |
| `tenant_id`、`agent_id`、`version_id` | 必填 |
| `status` | `PASS`、`WARN`、`FAIL` |
| `check_results_json` | 只保存 check code、status、reasonCode、evidenceRef |
| `evidence_refs_json` | 只保存引用，不保存 raw 内容 |
| `created_at` | 必填 |

API：

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/rollouts:canary` | 创建 canary rollout |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/rollouts/latest` | 查询 latest rollout |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停 rollout |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | gate pass 后标记 promoted |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | 通过 Phase 6 owner 回滚 |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/pilot-readiness:generate` | 生成 readiness report |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/pilot-readiness/latest` | 查询 latest readiness report |

#### 17.3.7 TDD 执行顺序

1. 写 `AgentVersionRolloutTests` 和 `EnterprisePilotReadinessReportTests`，确认 domain RED。
2. 实现 rollout/readiness domain、enum、limits、status severity，运行 domain GREEN。
3. 写 `KernelAgentRolloutServiceTests`，覆盖 gate missing、gate failed、promote pass、rollback owner success/failure。
4. 写 `KernelEnterprisePilotReadinessServiceTests`，覆盖九项 check、overall 聚合、高风险 fail closed、raw evidence 拒绝。
5. 实现 kernel inbound/outbound ports 与 services，运行 kernel focused GREEN。
6. 写 `JdbcAgentRolloutRepositoryAdapterTests` 和 `JdbcEnterprisePilotReadinessRepositoryAdapterTests`，确认 RED。
7. 实现 JDBC adapters 与 schema，运行 JDBC focused GREEN。
8. 写 `SeahorseAgentRolloutControllerTests` 和 `SeahorseEnterprisePilotReadinessControllerTests`，确认 RED。
9. 实现 Web controllers，运行 Web focused GREEN。
10. 扩展 starter tests，确认 RED 后实现 auto-configuration。
11. 运行 Phase 8D focused regression 与 Phase 4/5/7/8B/8C 回归依赖。
12. 运行 forbidden dependency scan、raw evidence scan、`git diff --check`，再更新 Aegis evidence。

#### 17.3.8 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,KernelAgentRolloutServiceTests,EnterprisePilotReadinessReportTests,KernelEnterprisePilotReadinessServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests,JdbcAgentVersionActivationRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ProductionGateStatusTests,AgentEvalSummaryTests,KernelAgentEvalQueryServiceTests,KernelProductionGateServiceTests,QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelCostUsageQueryServiceTests,KernelSreHealthQueryServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,AgentHandoffContextPolicyTests,DefaultMeshPolicyPortTests,LocalAgentAsToolPortTests,KernelAgentHandoffServiceTests,ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,AuditedResourceAccessPolicyPortTests,ResourceAclImportDryRunTests' test
rg -n "org\.springframework|javax\.sql|java\.sql|RestTemplate|WebClient" seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports
rg -n "rawPrompt|rawCase|rawToolOutput|rawAcl|secret-token|sampleInput|sampleOutput|stackTrace" seahorse-agent-kernel/src/main/java seahorse-agent-adapter-web/src/main/java seahorse-agent-adapter-repository-jdbc/src/main/java
git diff --check
```

#### 17.3.9 回滚与非目标

回滚时关闭 rollout/readiness controllers 和 starter wiring；保留 rollout/readiness report 表作为历史证据。Phase 8D 不做真实百分比分流、不做前端试点向导、不直接激活版本、不引入工作流引擎、不实现远程 Agent mesh。

## 18. 当前最新执行判定

截至本节，当前主实现顺序更新为：

1. Phase 8C Quota/Cost/SRE 外层闭环：在已有 kernel GREEN 基础上补 JDBC/Web/starter，完成 focused regression 与 evidence。
2. Phase 8D Rollout/Readiness：从 domain/ports/services 开始，完成 JDBC/Web/starter 与企业试点准入 report。
3. Final completion audit：串行跑 Phase 4、Phase 5、Phase 7、Phase 8B、Phase 8C、Phase 8D focused regression，补 kernel dependency scan、raw evidence scan、Aegis evidence bundle，再判断是否能声明 AI Infra 完成。

Phase 4、Phase 5、Phase 7、Phase 8B 现在不是主实现阶段，但它们仍是完成 Phase 8D 和最终完成判断的强制回归依赖。本文档仍是设计开发方案，不是完成声明。

## 19. 2026-05-26 RED 测试校准后的未完成阶段精细开发方案

本节基于再次深读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、Phase 8 生产化硬化文档、当前 Aegis evidence、以及当前 worktree 中 Phase 8C RED 测试文件后的校准结果。旧 `99-current-implementation-handoff.md` 中的 Approval query/decision API 已由后续 Phase 3 实现与回归证据覆盖，不再作为当前主线入口。当前真正未完成的主阶段仍是 Phase 8C 和 Phase 8D。

### 19.1 当前事实与不变量

| 阶段 | 当前事实 | 本节处理方式 |
| --- | --- | --- |
| Phase 4 Resource ACL | import commit、audit、DB hardening 和 focused regression 已有证据 | 保留为 Phase 8D readiness 的授权回归依赖 |
| Phase 5 Connector/Sandbox | credential binding、OAuth、sandbox audit、Web/JDBC/starter focused regression 已有证据 | 保留为外部系统安全回归依赖 |
| Phase 7 Local Agent-as-Tool | handoff、tool registration、starter focused regression 已有证据 | 保留为 multi-agent/handoff 回归依赖 |
| Phase 8B Eval Summary Gate | eval summary JDBC/Web/starter 与 production gate wiring 已有 focused regression | 保留为 Phase 8C/8D gate evidence 依赖 |
| Phase 8C Quota/Cost/SRE | kernel domain/service/ports 已存在；JDBC/Web RED 测试已写入；starter RED 仍需补 | 第一未完成阶段 |
| Phase 8D Rollout/Readiness | 未见 rollout/readiness domain、ports、JDBC、Web、starter owner 文件 | 第二未完成阶段 |

必须保持的不变量：

1. kernel 只依赖 port 抽象，不依赖 Spring、JDBC、Web、HTTP client 或 SQL。
2. quota、cost、SRE、rollout、readiness 使用独立小 port，不合并成 `AgentService`。
3. 状态、原因、风险、来源、check code 全部使用 enum 或具名常量。
4. cost usage、audit、eval summary、rollout/readiness report 都是证据记录；修正通过新记录或状态转换表达，不物理覆盖历史事实。
5. Phase 8D 不建立第二套 active version owner，rollback 必须复用 Phase 6 activation/rollback port。

### 19.2 Phase 8C 补充方案：Quota/Cost/SRE 外层闭环

#### 19.2.1 当前 RED 边界

当前 worktree 已存在以下 Phase 8C RED 测试，但尚未完成 GREEN 实现：

| 测试 | 当前期望 | 缺口 |
| --- | --- | --- |
| `JdbcQuotaPolicyRepositoryAdapterTests` | `JdbcQuotaPolicyRepositoryAdapter` 支持 upsert、find latest active、disable、DB enum/check constraint | adapter 和主 schema 表仍需实现 |
| `JdbcCostUsageRepositoryAdapterTests` | `JdbcCostUsageRepositoryAdapter` 支持 append-only、aggregate、重复主键失败、DB enum/check constraint | adapter 和主 schema 表仍需实现 |
| `SeahorseQuotaControllerTests` | `/api/quotas/policies`、`/disable`、`/decisions:evaluate` 可用 | Web controller 仍需实现 |
| `SeahorseCostUsageControllerTests` | append/aggregate API 可用，且不回显 `rawPrompt`、`rawToolOutput`、`secret-token` | Web controller 仍需实现 |
| `SeahorseSreHealthControllerTests` | `/api/sre/health` 可用，且不泄露 `stackTrace` | Web controller 仍需实现 |

starter 还需要补一个明确 RED：`SeahorseAgentRegistryAutoConfigurationTests` 必须断言 quota/cost/SRE repository、inbound service、report provider，以及 `KernelProductionGateService` 的 quota/SRE optional dependency 都被装配。

#### 19.2.2 真实代码合同

本阶段必须以当前 kernel 代码为事实来源，不能回退到早期文档里的过时命名。

| 合同点 | 当前事实 |
| --- | --- |
| quota scope enum | `QuotaScope.TENANT`、`AGENT`、`USER`、`TOOL`、`MODEL`、`RUN` |
| quota status enum | `QuotaPolicyStatus.ACTIVE`、`DISABLED` |
| quota decision enum | `QuotaDecisionEffect.ALLOW`、`WARN`、`REQUIRE_APPROVAL`、`DENY` |
| quota reason enum | 使用 `QuotaDecisionReasonCode`，禁止自由文本 reason |
| cost source enum | `CostUsageSource.MODEL`、`TOOL`、`SANDBOX`、`MANUAL_ADJUSTMENT` |
| SRE status enum | `SreHealthStatus.GREEN`、`WARN`、`RED`，severity 在 enum 内表达 |
| quota repository port | `upsert`、`findActive`、`disable`，repository 不做业务决策 |
| cost repository port | `append`、`aggregate`，append-only 由主键和无 update 路径保证 |
| SRE provider port | `KernelSreHealthQueryService` 同时实现 inbound 和 report provider |

#### 19.2.3 文件级开发方案

| 层 | 文件 | 具体动作 |
| --- | --- | --- |
| JDBC main | `JdbcQuotaPolicyRepositoryAdapter.java` | 新增 adapter；使用 `NamedParameterJdbcTemplate` 或现有 JDBC 模式；保存 enum `name()`；`findActive` 按 `updated_at DESC, policy_id DESC` 稳定选择 |
| JDBC main | `JdbcCostUsageRepositoryAdapter.java` | 新增 adapter；只提供 insert 和 aggregate query；重复 `usage_id` 交给数据库失败 |
| SQL | `agent-registry-run-store-postgresql.sql` | 追加 `sa_quota_policy`、`sa_cost_usage_record`；check constraint 使用当前 enum 名称 |
| Web main | `SeahorseQuotaController.java` | 只做 DTO 到 `QuotaPolicyUpsertCommand`、`QuotaDecisionCommand` 的映射；不复制 quota 决策逻辑 |
| Web main | `SeahorseCostUsageController.java` | 只接收 usage summary 字段；DTO 使用 `@JsonIgnoreProperties(ignoreUnknown = true)`；响应不包含 raw 字段 |
| Web main | `SeahorseSreHealthController.java` | 只返回 `SreHealthReport` snapshot；异常细节由 kernel contributor 降级策略处理 |
| Starter main | `SeahorseAgentRegistryRepositoryAutoConfiguration.java` | 在现有 repository property boundary 下装配 quota/cost JDBC adapter |
| Starter main | `SeahorseAgentKernelRegistryAutoConfiguration.java` | 装配 `KernelQuotaDecisionService`、`KernelCostUsageQueryService`、`KernelSreHealthQueryService`；production gate 注入 optional quota/SRE |
| Starter test | `SeahorseAgentRegistryAutoConfigurationTests.java` | 补 RED 断言所有 Phase 8C bean 与 private dependency injection |

#### 19.2.4 数据表精确合同

`sa_quota_policy`：

| 字段 | 约束 |
| --- | --- |
| `policy_id` | primary key |
| `tenant_id` | not null |
| `scope` | check in `TENANT`、`AGENT`、`USER`、`TOOL`、`MODEL`、`RUN` |
| `subject_id` | not null；tenant scope 使用 tenant id |
| `status` | check in `ACTIVE`、`DISABLED` |
| `token_limit`、`call_limit`、`cost_limit` | 至少一个非空；非空时必须大于等于 0 |
| `warn_ratio` | `> 0` 且 `<= 1` |
| `created_at`、`updated_at` | not null |

索引：`tenant_id, scope, subject_id, status, updated_at DESC, policy_id DESC`。

`sa_cost_usage_record`：

| 字段 | 约束 |
| --- | --- |
| `usage_id` | primary key，禁止 update 路径 |
| `tenant_id` | not null |
| `agent_id`、`run_id`、`user_id`、`tool_id`、`model_id` | 可空过滤维度 |
| `source` | check in `MODEL`、`TOOL`、`SANDBOX`、`MANUAL_ADJUSTMENT` |
| `tokens`、`calls`、`cost` | not null 且大于等于 0 |
| `reason_ref` | 可空，只保存引用，不保存 raw prompt/tool output |
| `created_at` | not null |

索引：`tenant_id, agent_id, run_id, created_at`。

#### 19.2.5 API 精确合同

| Method | Path | 输入 | 输出 |
| --- | --- | --- | --- |
| `POST` | `/api/quotas/policies` | policy id、tenant、scope、subject、limits、warnRatio、timestamps | policy snapshot |
| `POST` | `/api/quotas/policies/{policyId}/disable` | path policy id | project standard success response；disable 保持幂等 |
| `POST` | `/api/quotas/decisions:evaluate` | tenant/agent/user/tool/model/run、riskLevel、tokens/calls/cost | `QuotaDecisionResult` |
| `POST` | `/api/cost-usage-records` | usage id、tenant、维度、source、tokens/calls/cost、reasonRef、createdAt | usage snapshot |
| `GET` | `/api/cost-usage:aggregate` | tenantId、可选 agentId/runId、from、to | `CostUsageAggregate` |
| `GET` | `/api/sre/health` | 无 | `SreHealthReport` |

Web 层不引入运行状态修改。quota decision 只是决策结果；是否暂停 run 或创建 approval 留给后续明确 runtime 小切片。

#### 19.2.6 TDD 执行细节

1. 串行运行 JDBC RED：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

预期：编译失败，缺少 `JdbcQuotaPolicyRepositoryAdapter` 和 `JdbcCostUsageRepositoryAdapter`。

2. 实现 JDBC adapter 和 schema，再运行同一命令，预期 GREEN。
3. 串行运行 Web RED：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseQuotaControllerTests,SeahorseCostUsageControllerTests,SeahorseSreHealthControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

预期：编译失败，缺少三个 controller。

4. 实现 Web controller，再运行同一命令，预期 GREEN。
5. 扩展 starter test，运行 RED：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

预期：缺少 quota/cost/SRE beans 或 production gate dependency injection 断言失败。

6. 实现 starter wiring，再运行 starter GREEN。
7. 最终串行运行 Phase 8C focused regression、kernel forbidden dependency scan、raw evidence scan 和 `git diff --check`。

#### 19.2.7 完成判定

Phase 8C 只有在以下证据都记录到 Aegis evidence 后才可判定为 focused complete：

1. JDBC RED 和 GREEN 命令都有记录。
2. Web RED 和 GREEN 命令都有记录。
3. Starter RED 和 GREEN 命令都有记录。
4. Kernel quota/cost/SRE/gate regression 通过。
5. `KernelProductionGateService` 在 starter 上下文中拿到 quota repository 与 SRE provider。
6. raw scan 证明 Web/JDBC 不保存或回显 raw prompt、raw tool output、secret、stack trace。
7. kernel forbidden dependency scan 无 Spring/JDBC/Web/HTTP client 命中。

### 19.3 Phase 8D 补充方案：Rollout/Readiness 从零闭环

#### 19.3.1 当前缺口

当前 worktree 没有 rollout/readiness owner 文件。Phase 8D 必须从 domain tests 开始，按 kernel -> JDBC -> Web -> starter 的顺序推进。它依赖 Phase 8B eval、Phase 8C quota/SRE、Phase 4 ACL、Phase 5 connector/sandbox、Phase 6 activation/rollback、Phase 7 handoff 这些证据，但不能把这些 owner 的事实复制到 rollout/readiness 模块。

#### 19.3.2 领域设计

| 对象 | 字段与职责 | 不变量 |
| --- | --- | --- |
| `AgentVersionRollout` | rolloutId、tenantId、agentId、versionId、canaryPercent、status、failureCode、gateReportId、startedBy、startedAt、updatedAt、finishedAt | percent 在 `AgentRolloutLimits` 范围内；terminal status 不可回退；失败必须有 failureCode |
| `AgentRolloutStatus` | `CREATED`、`RUNNING`、`PAUSED`、`PROMOTED`、`ROLLED_BACK`、`FAILED` | `PROMOTED`、`ROLLED_BACK`、`FAILED` 为 terminal |
| `AgentRolloutFailureCode` | `GATE_MISSING`、`GATE_FAILED`、`INVALID_PERCENT`、`ROLLBACK_FAILED`、`ROLLBACK_TARGET_MISSING` | 不用字符串表达失败原因 |
| `AgentRolloutLimits` | `MIN_CANARY_PERCENT`、`MAX_CANARY_PERCENT`、`DEFAULT_CANARY_PERCENT` | 不在 service/controller 中散落数字 |
| `EnterprisePilotReadinessReport` | reportId、tenantId、agentId、versionId、overallStatus、checkResults、evidenceRefs、createdAt | 必须包含全部 check code；overall 由 severity 聚合 |
| `EnterprisePilotReadinessCheckCode` | `OWNER`、`PUBLISHED_VERSION`、`TOOL_RISK`、`RESOURCE_ACL`、`EVAL`、`QUOTA`、`AUDIT`、`ROLLBACK`、`DISABLE_SWITCH` | check code 完整性由领域对象校验 |
| `EnterprisePilotReadinessStatus` | `PASS`、`WARN`、`FAIL` | severity 明确，不能按字符串排序 |

#### 19.3.3 Port 与组合关系

| Port/Service | 责任 |
| --- | --- |
| `AgentRolloutInboundPort` | create canary、pause、promote、rollback、latest query |
| `AgentRolloutRepositoryPort` | save、findById、findLatest、updateStatus；遵守 terminal 不回退语义 |
| `EnterprisePilotReadinessInboundPort` | generate、latest query |
| `EnterprisePilotReadinessRepositoryPort` | save、findLatest |
| `KernelAgentRolloutService` | 编排 production gate report、rollout 状态和 Phase 6 rollback port |
| `KernelEnterprisePilotReadinessService` | 组合多个 evidence port 生成 readiness report |

Readiness service 可以新增极小 evidence query port，但每个 port 只能对应一个证据来源。例如 `ReadinessAgentDefinitionEvidencePort`、`ReadinessToolRiskEvidencePort`、`ReadinessRollbackEvidencePort`。禁止为了省事让 readiness service 直接依赖 JDBC adapter、Web DTO 或大 service。

#### 19.3.4 Rollout 流程合同

1. `createCanary`：创建 `CREATED` 或 `RUNNING` 初始状态，选择一种并固化测试；默认建议 `RUNNING`，因为首版不做异步流量路由。
2. `pause`：对 `RUNNING` 变为 `PAUSED`；对 terminal rollout 幂等返回当前状态。
3. `promote`：先读 latest production gate report；缺失则 rollout `FAILED/GATE_MISSING`；非 `PASS` 则 `FAILED/GATE_FAILED`；`PASS` 才允许 `PROMOTED` 并保存 `gateReportId`。
4. `rollback`：调用 Phase 6 activation/rollback port；成功后 `ROLLED_BACK`；失败后 `FAILED/ROLLBACK_FAILED`；repository 不直接改 active version。
5. `latest`：按 tenant/agent/version 维度返回最新 rollout；排序用 `updatedAt DESC, rolloutId DESC`。

#### 19.3.5 Readiness 九项检查合同

| CheckCode | Evidence source | PASS | WARN | FAIL |
| --- | --- | --- | --- | --- |
| `OWNER` | Agent definition/version evidence | owner 与 fallback owner 都存在 | fallback owner 缺失但非高风险 | 高风险 agent owner 缺失 |
| `PUBLISHED_VERSION` | Agent version evidence | version published 且未 disabled | 发布说明不完整 | 无可用 published version |
| `TOOL_RISK` | Tool catalog/policy evidence | 高风险工具有 approval policy 或 disabled | 低风险工具缺少补充说明 | 高风险写工具 enabled 且无审批 |
| `RESOURCE_ACL` | Resource ACL evidence | 资源绑定有 ACL/access evidence | 低风险 knowledge agent 无资源绑定 | 高风险或企业数据资源缺 ACL |
| `EVAL` | Eval summary evidence | latest eval 非 `FAIL`，高风险不 stale | 低风险缺 eval 或 stale | 高风险缺 eval、stale 或 `FAIL` |
| `QUOTA` | Quota policy evidence | tenant 或 agent active quota 存在 | 低风险缺 quota | 高风险缺 quota |
| `AUDIT` | Audit repository evidence | 关键事件可查询 | 非关键事件缺样本 | audit evidence 不可用 |
| `ROLLBACK` | Version activation/rollback evidence | 有 rollback target 或 activation history | 低风险单版本 | 高风险无 rollback evidence |
| `DISABLE_SWITCH` | Agent/tool disable evidence | agent 和高风险 tool 可 disable | 低风险 tool 缺 disable evidence | 高风险 tool 或 agent 不可 disable |

Readiness report 只能保存 `evidenceRef`、id、status、reasonCode、timestamp，不保存 prompt、eval case input/output、tool raw output、credential 或 stack trace。

#### 19.3.6 JDBC 与 Web 方案

JDBC 表：

1. `sa_agent_version_rollout`：保存 rollout fact，包含 `rollout_id`、`tenant_id`、`agent_id`、`version_id`、`canary_percent`、`status`、`failure_code`、`gate_report_id`、`started_by`、`started_at`、`updated_at`、`finished_at`。
2. `sa_enterprise_pilot_readiness_report`：保存 report snapshot，包含 `report_id`、`tenant_id`、`agent_id`、`version_id`、`status`、`check_results_json`、`evidence_refs_json`、`created_at`。

Web API：

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/rollouts:canary` | 创建 canary rollout |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/rollouts/latest` | 查询 latest rollout |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停 rollout |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | gate pass 后 promoted，否则 failed |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | 通过 Phase 6 owner 回滚 |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/pilot-readiness:generate` | 生成 readiness report |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/pilot-readiness/latest` | 查询 latest report |

#### 19.3.7 TDD 执行细节

1. 先写 domain RED：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,EnterprisePilotReadinessReportTests' test
```

预期：缺少 rollout/readiness domain。

2. 实现 domain、enum、limits、status severity，运行 domain GREEN。
3. 写 service RED：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentRolloutServiceTests,KernelEnterprisePilotReadinessServiceTests' test
```

预期：缺少 inbound/outbound ports 和 kernel services。

4. 实现 kernel ports/services，运行 kernel GREEN。
5. 写 JDBC RED，再实现 JDBC adapter/schema：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

6. 写 Web RED，再实现 Web controllers：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

7. 扩展 starter test，确认 RED 后装配 repository/service beans。
8. 串行运行 Phase 8D focused regression 和 Phase 4/5/7/8B/8C 回归依赖。不要并行跑同一 Maven module，避免 `target/classes` 写入竞争导致假失败。

#### 19.3.8 完成判定

Phase 8D 只有在以下证据全部成立时才可判定为 focused complete：

1. rollout domain 能阻止非法 percent 和 terminal 回退。
2. promote 缺 gate report 或 gate 非 `PASS` 时 fail closed。
3. rollback 通过 Phase 6 activation/rollback port，repository 不直接改 active version。
4. readiness report 包含全部九项 check code，overall severity 聚合正确。
5. readiness evidence 不保存 raw prompt、raw eval case、raw tool output、secret、stack trace。
6. JDBC/Web/starter focused regression 通过。
7. Phase 4/5/7/8B/8C regression 依赖在同一最终 evidence bundle 中通过。

### 19.4 最新执行判定

当前最新主实现顺序为：

1. Phase 8C：先把已写 RED 测试跑出失败证据，再补 JDBC/Web/starter GREEN。
2. Phase 8D：从 domain RED 开始实现 rollout/readiness，再补 JDBC/Web/starter。
3. Final completion audit：串行运行 Phase 4、Phase 5、Phase 7、Phase 8B、Phase 8C、Phase 8D focused regression，补 dependency scan、raw evidence scan、Aegis evidence bundle 后，再判断 AI Infra 是否可以声明完成。

本节是开发方案和执行判定，不是实现完成声明。

## 20. 2026-05-26 Phase 8C GREEN 后的未完成阶段详细开发方案

本节基于再次深入阅读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、旧 `99-current-implementation-handoff.md`、当前 Aegis checkpoint/evidence，以及当前 worktree 中 Phase 8C JDBC/Web/starter 代码面后的校准结果。旧交接文档里的 Approval query/decision API 已由后续 Phase 3 相关实现和回归证据覆盖，不再作为当前主线。当前未完成阶段必须按“主实现阶段”和“强制回归依赖”分开处理，避免把已收口能力重复设计成新 owner。

### 20.1 当前阶段校准

| 阶段 | 当前判定 | 本节处理方式 |
| --- | --- | --- |
| Phase 4 Resource ACL | import commit、audit、DB hardening、dry-run/provenance 已有 focused regression 证据 | 不新增主实现方案；作为 Phase 8D readiness 的授权与上下文回归依赖 |
| Phase 5 Connector/Sandbox | OAuth、connector import/operation、credential binding、sandbox JDBC/Web/starter 已有 focused regression 证据 | 不新增主实现方案；作为外部系统安全、凭据和 sandbox audit 回归依赖 |
| Phase 7 Local Agent-as-Tool | handoff、tool registration、starter wiring 已有 focused regression 证据 | 不新增主实现方案；作为 multi-agent/handoff 回归依赖 |
| Phase 8B Eval Summary Gate | eval summary JDBC/Web/starter 与 production gate wiring 已有 focused regression 证据 | 不新增主实现方案；作为 Phase 8C/8D gate evidence 依赖 |
| Phase 8C Quota/Cost/SRE | JDBC/Web/starter GREEN 已出现，但还缺串行 focused regression、kernel dependency scan、raw evidence scan、Aegis evidence 收口 | 当前第一未完成收口阶段，见 20.2 |
| Phase 8D Rollout/Readiness | 仍未见 rollout/readiness domain、ports、JDBC、Web、starter owner 文件 | 当前唯一从零实现阶段，见 20.3 |

### 20.2 Phase 8C 详细方案：Quota/Cost/SRE 证据收口与 Gate 验证

#### 20.2.1 目标

Phase 8C 的目标不是再扩展新的运行治理能力，而是把已经实现的 quota policy、cost usage、SRE health 三条证据链证明为生产可装配路径：

1. JDBC adapter 能持久化 active quota policy，cost usage 只能 append 和 aggregate。
2. Web API 能管理 quota、追加/查询 cost、查询 health，并且不回显 raw prompt、raw tool output、secret 或 stack trace。
3. Starter 能自动装配 `QuotaPolicyRepositoryPort`、`CostUsageRepositoryPort`、`QuotaManagementInboundPort`、`CostUsageInboundPort`、`SreHealthInboundPort`、`SreHealthReportProviderPort`，并把 quota repository 与 SRE provider 注入 `KernelProductionGateService`。
4. Production Gate 不再只输出固定 quota/SRE foundation warning；有真实 repository/provider 时必须读取真实证据。

#### 20.2.2 文件边界

| 层 | 文件 | 处理方式 |
| --- | --- | --- |
| Kernel regression | `seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/agent/quota/*`、`cost/*`、`sre/*`、`gate/*` | 只跑回归；除非失败证明领域不变量有缺口，否则不扩展领域 |
| JDBC | `JdbcQuotaPolicyRepositoryAdapter.java`、`JdbcCostUsageRepositoryAdapter.java`、`agent-registry-run-store-postgresql.sql` | 验证 enum 映射、insert/update 字段、aggregate 维度和 append-only 语义 |
| Web | `SeahorseQuotaController.java`、`SeahorseCostUsageController.java`、`SeahorseSreHealthController.java` | 验证 request/response DTO 只暴露治理字段，不返回敏感原文 |
| Starter | `SeahorseAgentRegistryRepositoryAutoConfiguration.java`、`SeahorseAgentKernelRegistryAutoConfiguration.java`、`SeahorseAgentRegistryAutoConfigurationTests.java` | 验证 repository/service/provider/gate dependency 全部在 Spring context 中可解析 |
| Aegis | `docs/aegis/work/2026-05-25-ai-infra-phase-plan-refresh/20-checkpoint.md`、`90-evidence.md` | 记录 RED/GREEN、回归、扫描、漂移和剩余风险 |

#### 20.2.3 执行步骤

1. 串行运行 kernel focused regression：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelCostUsageQueryServiceTests,KernelSreHealthQueryServiceTests,KernelProductionGateServiceTests' test
```

2. 串行运行 JDBC focused regression：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

3. 串行运行 Web focused regression：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseQuotaControllerTests,SeahorseCostUsageControllerTests,SeahorseSreHealthControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

4. 串行运行 starter focused regression：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

5. 运行 kernel forbidden dependency scan：

```powershell
rg -n "org\.springframework|javax\.sql|java\.sql|RestTemplate|WebClient" seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports
```

预期结果是无命中；若有命中，必须把具体依赖移回 adapter 层或用 port 抽象替代。

6. 运行 raw evidence scan：

```powershell
rg -n "rawPrompt|rawToolOutput|secret-token|sampleInput|sampleOutput|stackTrace" seahorse-agent-kernel/src/main/java seahorse-agent-adapter-web/src/main/java seahorse-agent-adapter-repository-jdbc/src/main/java
```

预期结果是不在 Phase 8C Web/JDBC/kernel 生产路径中保存或回显敏感原文；若出现测试 fixture 或历史无关命中，必须在 evidence 中注明文件和无关理由。

7. 运行文档与 diff hygiene：

```powershell
git diff --check -- docs/company-agent/ai-infra-phases/20-unfinished-phase-implementation-pack.md docs/company-agent/ai-infra-phases/README.md docs/aegis/work/2026-05-25-ai-infra-phase-plan-refresh/20-checkpoint.md docs/aegis/work/2026-05-25-ai-infra-phase-plan-refresh/90-evidence.md
```

#### 20.2.4 完成判定

Phase 8C 只有在以下证据全部写入 Aegis evidence 后，才可判定为 focused complete：

1. kernel、JDBC、Web、starter focused regression 都通过。
2. `KernelProductionGateService` 的 quota/SRE dependency 在 starter context 中被真实注入。
3. quota policy 支持 active/disabled 状态，cost usage 只能追加并可按维度聚合。
4. SRE health contributor 异常不会被吞成 green；至少降级为 warn 或 unhealthy。
5. forbidden dependency scan 无 kernel 依赖 Spring/JDBC/Web/HTTP client。
6. raw evidence scan 证明 Phase 8C 路径不保存或回显 raw prompt、raw tool output、secret、stack trace。
7. checkpoint/evidence 记录 Phase 8C 仍不做真实扣费、分布式限流、Prometheus exporter 或 run 状态改写。

### 20.3 Phase 8D 详细方案：Rollout 事实与 Enterprise Pilot Readiness 从零闭环

#### 20.3.1 目标

Phase 8D 的目标是让企业试点准入可查询、可解释、可回滚。它必须复用 Phase 6 的 publish-ready、version activation 和 rollback owner，不允许在 rollout/readiness 模块里建立第二套 active version 事实。首版只做 rollout fact、promote gate 强制检查、rollback 编排和 readiness report，不做真实百分比分流器、前端发布向导、远程 Agent mesh 或工作流引擎。

#### 20.3.2 领域对象

| 包 | 文件 | 不变量 |
| --- | --- | --- |
| `kernel/domain/agent/rollout` | `AgentVersionRollout.java` | `rolloutId/tenantId/agentId/versionId/startedBy/startedAt/updatedAt` 非空；`canaryPercent` 在 `AgentRolloutLimits` 范围内；terminal status 不可回退 |
| `kernel/domain/agent/rollout` | `AgentRolloutStatus.java` | `CREATED`、`RUNNING`、`PAUSED`、`PROMOTED`、`ROLLED_BACK`、`FAILED`，其中后三个是 terminal |
| `kernel/domain/agent/rollout` | `AgentRolloutFailureCode.java` | `GATE_MISSING`、`GATE_FAILED`、`ROLLBACK_TARGET_MISSING`、`ROLLBACK_FAILED`、`INVALID_TRANSITION` |
| `kernel/domain/agent/rollout` | `AgentRolloutLimits.java` | `MIN_CANARY_PERCENT`、`MAX_CANARY_PERCENT`、`DEFAULT_CANARY_PERCENT` 用具名常量表达，不在 service/controller 散落数字 |
| `kernel/domain/agent/readiness` | `EnterprisePilotReadinessReport.java` | 必须包含全部 readiness check code；overall status 由 check severity 聚合 |
| `kernel/domain/agent/readiness` | `EnterprisePilotReadinessCheckResult.java` | 每项 check 必须有 code、status、reasonCode、evidenceRef、checkedAt |
| `kernel/domain/agent/readiness` | `EnterprisePilotReadinessCheckCode.java` | `OWNER`、`PUBLISHED_VERSION`、`TOOL_RISK`、`RESOURCE_ACL`、`EVAL`、`QUOTA`、`AUDIT`、`ROLLBACK`、`DISABLE_SWITCH` |
| `kernel/domain/agent/readiness` | `EnterprisePilotReadinessStatus.java` | `PASS`、`WARN`、`FAIL`，severity 聚合由 enum 方法维护 |
| `kernel/domain/agent/readiness` | `EnterprisePilotReadinessReasonCode.java` | 使用具名原因码表达缺 owner、缺 eval、缺 quota、缺 rollback、gate failed 等情况 |

#### 20.3.3 Port 与服务

| 类型 | 文件 | 责任 |
| --- | --- | --- |
| Inbound | `AgentRolloutInboundPort.java` | create canary、pause、promote、rollback、latest |
| Inbound | `EnterprisePilotReadinessInboundPort.java` | generate report、latest |
| Outbound | `AgentRolloutRepositoryPort.java` | save、findById、findLatest、updateStatus；替代实现必须遵守 terminal 不回退语义 |
| Outbound | `EnterprisePilotReadinessRepositoryPort.java` | save、findLatest |
| Outbound evidence | `ReadinessAgentDefinitionEvidencePort.java` | 只提供 owner、fallback owner、published version、disable switch 证据 |
| Outbound evidence | `ReadinessToolRiskEvidencePort.java` | 只提供高风险工具、approval policy、tool disable evidence |
| Outbound evidence | `ReadinessResourceAclEvidencePort.java` | 只提供 ACL/access evidence 引用 |
| Outbound evidence | `ReadinessEvalEvidencePort.java` | 只提供 latest eval summary status 和 report id |
| Outbound evidence | `ReadinessQuotaEvidencePort.java` | 只提供 active quota policy 证据 |
| Outbound evidence | `ReadinessAuditEvidencePort.java` | 只提供 audit 可查询证据 |
| Outbound evidence | `ReadinessRollbackEvidencePort.java` | 只提供 rollback target 或 activation history 证据 |
| Service | `KernelAgentRolloutService.java` | 编排 production gate、rollout 状态和 Phase 6 rollback port |
| Service | `KernelEnterprisePilotReadinessService.java` | 组合 evidence ports 生成 readiness report |

Evidence port 必须保持小接口，不能为了省事新增大一统 `AgentService`。Kernel 只依赖这些 port 抽象，不依赖 Spring、JDBC、Web、HTTP client。

#### 20.3.4 Rollout 行为合同

1. `createCanary`：创建 `RUNNING` rollout，使用 `AgentRolloutLimits.DEFAULT_CANARY_PERCENT` 作为默认 canary 百分比，超出边界直接拒绝。
2. `pause`：`RUNNING` 变 `PAUSED`；对 terminal rollout 幂等返回当前状态，不抛业务无关异常。
3. `promote`：先读取 latest production gate report；无 report 则 `FAILED/GATE_MISSING`；report 非 `PASS` 则 `FAILED/GATE_FAILED`；只有 gate pass 才能 `PROMOTED`。
4. `rollback`：通过 Phase 6 activation/rollback port 编排；成功后 `ROLLED_BACK`；失败后 `FAILED/ROLLBACK_FAILED`；rollout repository 不直接改 active version。
5. `latest`：按 `tenantId/agentId/versionId` 查询，排序规则固定为 `updatedAt DESC, rolloutId DESC`。

#### 20.3.5 Readiness 九项检查合同

| CheckCode | PASS | WARN | FAIL |
| --- | --- | --- | --- |
| `OWNER` | owner 与 fallback owner 都存在 | 低风险 agent 缺 fallback owner | owner 缺失，或高风险 agent 缺 fallback owner |
| `PUBLISHED_VERSION` | version published 且 agent 未 disabled | change summary 不完整 | 无 published version 或 agent disabled |
| `TOOL_RISK` | 高风险工具 disabled 或存在 approval policy | 低风险工具缺 owner 说明 | 高风险写工具 enabled 且无审批 |
| `RESOURCE_ACL` | 企业数据资源有 ACL/access evidence | 低风险 knowledge agent 暂无绑定资源 | 高风险或企业数据资源缺 ACL |
| `EVAL` | latest eval summary 非 `FAIL` 且未 stale | 低风险缺 eval 或 eval stale | 高风险缺 eval、stale 或 `FAIL` |
| `QUOTA` | tenant 或 agent active quota 存在 | 低风险 agent 缺 quota | 高风险 agent 缺 quota |
| `AUDIT` | 关键事件 audit 可查询 | 非关键事件缺样本 | audit evidence 不可用 |
| `ROLLBACK` | 有 rollback target 或 activation history | 低风险单版本 agent | 高风险 agent 无 rollback evidence |
| `DISABLE_SWITCH` | agent 和高风险 tool 都可 disable | 低风险 tool 缺 disable evidence | 高风险 tool 或 agent 不可 disable |

Readiness report 只能保存 evidence id/ref、status、reasonCode、timestamp、简短说明；不得保存 raw prompt、eval case input/output、tool raw output、credential、token、secret 或 stack trace。

#### 20.3.6 JDBC 与 Web 方案

JDBC 表：

```sql
CREATE TABLE sa_agent_version_rollout (
  rollout_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  canary_percent INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64),
  gate_report_id VARCHAR(64),
  started_by VARCHAR(64) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);
CREATE INDEX idx_sa_agent_version_rollout_latest ON sa_agent_version_rollout(tenant_id, agent_id, version_id, updated_at);

CREATE TABLE sa_enterprise_pilot_readiness_report (
  report_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  check_results_json TEXT NOT NULL,
  evidence_refs_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL
);
CREATE INDEX idx_sa_enterprise_pilot_readiness_latest ON sa_enterprise_pilot_readiness_report(tenant_id, agent_id, version_id, created_at);
```

Web API：

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/rollouts/canary` | 创建 canary rollout |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/rollouts/latest` | 查询 latest rollout |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停 rollout |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | gate pass 后 promoted，否则 failed |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | 通过 Phase 6 owner 回滚 |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/pilot-readiness/generate` | 生成 readiness report |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/pilot-readiness/latest` | 查询 latest report |

#### 20.3.7 TDD 执行顺序

1. 写 domain RED：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,EnterprisePilotReadinessReportTests' test
```

2. 实现 domain、enum、limits、reason code，运行 domain GREEN。
3. 写 service RED：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentRolloutServiceTests,KernelEnterprisePilotReadinessServiceTests' test
```

4. 实现 inbound/outbound ports 与 kernel services，运行 kernel GREEN。
5. 写 JDBC RED 并实现 adapter/schema：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

6. 写 Web RED 并实现 controllers：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

7. 扩展 starter RED，再装配 repository/service/evidence fallback beans：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

8. Phase 8D GREEN 后串行运行 Phase 4、Phase 5、Phase 7、Phase 8B、Phase 8C、Phase 8D focused regression，最后运行 dependency scan、raw evidence scan 和 `git diff --check`。

#### 20.3.8 完成判定

Phase 8D 只有在以下证据全部写入 Aegis evidence 后，才可判定为 focused complete：

1. rollout domain 阻止非法 canary percent 和 terminal status 回退。
2. promote 缺 gate report 或 gate 非 `PASS` 时 fail closed。
3. rollback 通过 Phase 6 owner 编排，repository 不直接修改 active version。
4. readiness report 包含全部九项 check code，overall severity 聚合正确。
5. readiness evidence 不保存 raw prompt、raw eval case、raw tool output、secret、token、stack trace。
6. JDBC/Web/starter focused regression 通过。
7. Phase 4/5/7/8B/8C regression 依赖在最终 evidence bundle 中通过。

### 20.4 最新执行判定

当前最新顺序为：

1. Phase 8C evidence closure：先跑 kernel/JDBC/Web/starter focused regression，再跑 dependency/raw scans，更新 Aegis checkpoint/evidence。
2. Phase 8D implementation：按 domain -> service -> JDBC -> Web -> starter 的 TDD 顺序从零闭环 rollout/readiness。
3. Final completion audit：串行运行 Phase 4、Phase 5、Phase 7、Phase 8B、Phase 8C、Phase 8D focused regression，补 dependency scan、raw evidence scan、Aegis evidence bundle 后，再判断 AI Infra 是否可以声明完成。

本节是深读后的最新设计开发方案，不是完成声明。

## 21. 2026-05-26 当前代码面校准后的未完成阶段详细开发方案

本节基于再次深入阅读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、旧 `99-current-implementation-handoff.md`、历史 `09-unfinished-phase-design-development-plans.md`、当前 Aegis checkpoint/evidence，以及当前 worktree 的 rollout/readiness 代码面后追加。旧交接文档中的 Approval query/decision API 是 2026-05-23 的 Phase 3 历史下一步，不再作为当前主线。当前最新事实是：Phase 8C 已完成 evidence closure；Phase 8D 的 kernel domain、port、service、测试文件已经出现，但 service focused regression、JDBC、Web、starter、最终回归审计还没有闭环。

### 21.1 当前未完成阶段判定

| 阶段 | 当前事实 | 最新处理方式 |
| --- | --- | --- |
| Phase 4 Resource ACL | Resource ACL 管理、dry-run/provenance、DB hardening 已有 focused regression 证据 | 不再补新主实现方案；作为 readiness 的 `RESOURCE_ACL` 证据来源和最终回归依赖 |
| Phase 5 Connector/Sandbox | OAuth、OpenAPI connector、credential binding、sandbox runtime/JDBC/Web/starter 已有 focused regression 证据 | 不再补新主实现方案；作为 readiness 的 `TOOL_RISK`、凭据隔离、安全执行和最终回归依赖 |
| Phase 7 Local Agent-as-Tool | handoff、tool registration、starter wiring 已有 focused regression 证据 | 不再补新主实现方案；作为 rollback/handoff 可运营性的最终回归依赖 |
| Phase 8B Eval Summary Gate | eval summary JDBC/Web/starter 与 production gate wiring 已有 focused regression 证据 | 不再补新主实现方案；作为 rollout promote 和 readiness `EVAL` 证据依赖 |
| Phase 8C Quota/Cost/SRE | kernel/JDBC/Web/starter focused regression、dependency scan、raw evidence scan 已完成 evidence closure | 不再补新主实现方案；作为 readiness `QUOTA`、SRE 证据和最终回归依赖 |
| Phase 8D Rollout/Readiness | kernel domain/port/service/test 文件已出现；外层 JDBC/Web/starter 缺失；service GREEN 证据未记录 | 当前唯一未完成主实现阶段；见 21.2 到 21.6 |
| Final Completion Audit | 尚未串行运行 Phase 4/5/7/8B/8C/8D 总回归与全量扫描 | 当前最后验收阶段；见 21.7 |

### 21.2 Phase 8D-1 方案：Kernel Service GREEN 校准

#### 21.2.1 目标

把已经出现的 Phase 8D kernel 半成品收成可验证的内核闭环。该步骤不新增 JDBC、Web 或 starter；只确认 rollout/readiness 的领域不变量、port 组合、服务编排和测试类型一致。任何失败都先按最小改动修正测试或 kernel contract，不扩展功能范围。

#### 21.2.2 文件边界

| 类型 | 文件 | 处理方式 |
| --- | --- | --- |
| Domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/rollout/*` | 保留 `AgentVersionRollout`、`AgentRolloutStatus`、`AgentRolloutFailureCode`、`AgentRolloutLimits` 为领域不变量 owner |
| Domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/readiness/*` | 保留 readiness check code、status severity、reason code、sensitive evidence guard |
| Inbound ports | `AgentRolloutInboundPort.java`、`EnterprisePilotReadinessInboundPort.java` 与 command records | 只表达 use case 入参，不塞 Spring/Web DTO |
| Outbound ports | `AgentRolloutRepositoryPort.java`、`EnterprisePilotReadinessRepositoryPort.java`、`Readiness*EvidencePort.java` | 保持小接口；marker evidence port 要么在 service 构造器中保留强类型，要么统一收敛为 `ReadinessEvidencePort`，但测试与生产签名必须一致 |
| Services | `KernelAgentRolloutService.java`、`KernelEnterprisePilotReadinessService.java` | 只做编排；不直接访问 JDBC、Spring、HTTP client、Web DTO |
| Tests | `AgentVersionRolloutTests`、`EnterprisePilotReadinessReportTests`、`KernelAgentRolloutServiceTests`、`KernelEnterprisePilotReadinessServiceTests` | 作为 RED/GREEN 证据入口 |

#### 21.2.3 具体规则

1. `createCanary` 默认 `RUNNING`，默认 percent 使用 `AgentRolloutLimits.DEFAULT_CANARY_PERCENT`，非法 percent 由领域对象拒绝。
2. `promote` 必须 fail-closed：没有 production gate report 时 `FAILED/GATE_MISSING`；gate 非 `PASS` 或 version 不匹配时 `FAILED/GATE_FAILED`；只有同版本 `PASS` 才能 `PROMOTED` 并记录 `gateReportId`。
3. `rollback` 必须通过 `AgentFactoryInboundPort.rollback(...)` 编排，原因码固定使用 `AgentRollbackReasonCode.CANARY_FAILED`；repository 不允许直接改 active version。
4. `pause` 对 terminal rollout 幂等返回当前状态；对非 terminal 调用领域方法转换。
5. readiness report 必须包含全部九项 check code，overall status 由 `EnterprisePilotReadinessStatus` severity 聚合。
6. readiness evidence 只保存 `evidenceRef`、status、reasonCode、message、timestamp；`EnterprisePilotReadinessCheckResult` 继续拒绝 `rawPrompt`、`rawToolOutput`、`stackTrace`、`credential:`、`bearer `、`secret-token` 等敏感引用。

#### 21.2.4 TDD 执行步骤

1. 运行当前 service GREEN 候选：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentRolloutServiceTests,KernelEnterprisePilotReadinessServiceTests' test
```

2. 若出现 readiness evidence port 类型不匹配，优先修正测试 helper 或 service 构造器签名，使 marker port 与 `ReadinessEvidencePort` 的替代关系一致，不引入大接口。
3. 运行 domain + service 回归：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,EnterprisePilotReadinessReportTests,KernelAgentRolloutServiceTests,KernelEnterprisePilotReadinessServiceTests' test
```

4. 运行 kernel forbidden dependency scan：

```powershell
rg -n "org\.springframework|javax\.sql|java\.sql|RestTemplate|WebClient" seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports
```

#### 21.2.5 完成判定

该子阶段只有在 service tests、domain+service regression、kernel forbidden dependency scan 全部通过，并且 Aegis evidence 记录命令、结果、剩余风险后，才可进入 JDBC 外层闭环。

### 21.3 Phase 8D-2 方案：Rollout/Readiness JDBC 持久化闭环

#### 21.3.1 目标

把 rollout fact 和 readiness report snapshot 落到 `adapter-repository-jdbc`，并保持 repository 只负责持久化契约。JDBC adapter 不拥有 active version，不重新计算 readiness 规则，不解析 raw prompt/tool output。

#### 21.3.2 文件边界

| 文件 | 动作 |
| --- | --- |
| `JdbcAgentRolloutRepositoryAdapterTests.java` | 新增 RED 测试：save/findById/findLatest、status/failure/gate fields round-trip、latest 排序、terminal status 不被 mapper 破坏 |
| `JdbcEnterprisePilotReadinessRepositoryAdapterTests.java` | 新增 RED 测试：save/findLatest、九项 check round-trip、status/reason/evidence/message/checkedAt round-trip、raw evidence ref 不落库 |
| `JdbcAgentRolloutRepositoryAdapter.java` | 实现 `AgentRolloutRepositoryPort`，只映射 domain 字段 |
| `JdbcEnterprisePilotReadinessRepositoryAdapter.java` | 实现 `EnterprisePilotReadinessRepositoryPort`，使用 Jackson 读写 check result snapshot |
| `agent-registry-run-store-postgresql.sql` | 新增 `sa_agent_version_rollout`、`sa_enterprise_pilot_readiness_report` 与 enum check constraints/index |

#### 21.3.3 表设计

```sql
CREATE TABLE IF NOT EXISTS sa_agent_version_rollout (
  rollout_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  canary_percent INT NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64),
  gate_report_id VARCHAR(64),
  started_by VARCHAR(64) NOT NULL,
  started_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP,
  CONSTRAINT chk_sa_agent_version_rollout_percent CHECK (canary_percent >= 1 AND canary_percent <= 100),
  CONSTRAINT chk_sa_agent_version_rollout_status CHECK (status IN ('CREATED','RUNNING','PAUSED','PROMOTED','ROLLED_BACK','FAILED')),
  CONSTRAINT chk_sa_agent_version_rollout_failure CHECK (
    (status = 'FAILED' AND failure_code IS NOT NULL)
    OR (status <> 'FAILED' AND failure_code IS NULL)
  )
);
CREATE INDEX IF NOT EXISTS idx_sa_agent_version_rollout_latest
  ON sa_agent_version_rollout(tenant_id, agent_id, version_id, updated_at DESC, rollout_id DESC);

CREATE TABLE IF NOT EXISTS sa_enterprise_pilot_readiness_report (
  report_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  agent_id VARCHAR(64) NOT NULL,
  version_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  check_results_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  CONSTRAINT chk_sa_enterprise_pilot_readiness_status CHECK (status IN ('PASS','WARN','FAIL'))
);
CREATE INDEX IF NOT EXISTS idx_sa_enterprise_pilot_readiness_latest
  ON sa_enterprise_pilot_readiness_report(tenant_id, agent_id, version_id, created_at DESC, report_id DESC);
```

如果实现选择保留独立 `evidence_refs_json`，它只能存从 `check_results_json` 派生出的 evidence refs，不得成为第二套事实来源。

#### 21.3.4 TDD 执行步骤

1. 写 JDBC RED 后运行：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

预期：缺少 adapter 或 schema 时失败。

2. 实现 schema 与两个 adapter，沿用现有 `JdbcProductionGateRepositoryAdapter`、`JdbcAgentEvalSummaryRepositoryAdapter` 的 `JdbcTemplate + ObjectMapper` 风格。
3. 再运行同一命令，预期 GREEN。
4. 追加 raw evidence scan：

```powershell
rg -n "rawPrompt|rawToolOutput|secret-token|sampleInput|sampleOutput|stackTrace|credential:|bearer " seahorse-agent-adapter-repository-jdbc/src/main/java
```

#### 21.3.5 完成判定

JDBC 子阶段完成必须证明：rollout latest 使用 `updated_at DESC, rollout_id DESC`；readiness 九项 check round-trip 完整；DB constraint 使用 enum 字符串白名单；adapter 不保存 raw prompt、raw tool output、secret、credential、token 或 stack trace。

### 21.4 Phase 8D-3 方案：Rollout/Readiness Web API 闭环

#### 21.4.1 目标

为运营控制面提供最小可用 API：创建 canary、查询 latest、pause、promote、rollback、生成 readiness、查询 readiness latest。Web 层只做 DTO 到 inbound command 的转换，不能复制 gate/readiness 业务判断。

#### 21.4.2 文件边界

| 文件 | 动作 |
| --- | --- |
| `SeahorseAgentRolloutControllerTests.java` | 新增 RED/GREEN：canary 默认 percent、latest、pause、promote fail/pass response、rollback request |
| `SeahorseEnterprisePilotReadinessControllerTests.java` | 新增 RED/GREEN：generate/latest 返回九项 check、service unavailable 行为、响应不含 raw sensitive 字段 |
| `SeahorseAgentRolloutController.java` | 新增 rollout endpoints，使用 `ObjectProvider<AgentRolloutInboundPort>` 和 `ApiResponses.requireService` |
| `SeahorseEnterprisePilotReadinessController.java` | 新增 readiness endpoints，使用 `ObjectProvider<EnterprisePilotReadinessInboundPort>` |

#### 21.4.3 API 合同

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/rollouts/canary` | 创建 canary rollout；body 可选 `tenantId`、`canaryPercent`、`operator` |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/rollouts/latest` | 查询 latest rollout；未找到返回当前项目既有 not-found 异常形状 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停 rollout |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | 调用 service promote，失败也返回 domain 的 `FAILED/*` fact，不在 controller 重算 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | body 必须包含 `targetVersionId`、`tenantId`、`operator`，comment 可选 |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/pilot-readiness/generate` | 生成 readiness report |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/pilot-readiness/latest` | 查询 latest readiness report |

#### 21.4.4 TDD 执行步骤

1. 写 Web RED 后运行：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

2. 实现 controller，沿用当前 controller 的 `ApiResponse` 形状，不新增全局错误格式。
3. 再运行同一命令，预期 GREEN。
4. 运行 Web raw scan：

```powershell
rg -n "rawPrompt|rawToolOutput|secret-token|sampleInput|sampleOutput|stackTrace|credential:|bearer " seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web
```

#### 21.4.5 完成判定

Web 子阶段完成必须证明：controller 不依赖 repository adapter；DTO 字段只包含治理元数据；响应不回显敏感原文；service unavailable 语义与现有 controller 一致；promote/rollback 的业务状态来自 kernel service。

### 21.5 Phase 8D-4 方案：Starter Wiring 与保守 Evidence Provider

#### 21.5.1 目标

把 Phase 8D 的 repository、inbound service 和 readiness evidence ports 装配进 Spring Boot starter。缺少真实 evidence adapter 时必须保守返回 `WARN` 或 `FAIL`，不能伪造 `PASS`。starter 只做装配，不成为 readiness 规则 owner。

#### 21.5.2 文件边界

| 文件 | 动作 |
| --- | --- |
| `SeahorseAgentRegistryRepositoryAutoConfiguration.java` | 注册 `JdbcAgentRolloutRepositoryAdapter`、`JdbcEnterprisePilotReadinessRepositoryAdapter` |
| `SeahorseAgentKernelRegistryAutoConfiguration.java` | 注册 `KernelAgentRolloutService`、`KernelEnterprisePilotReadinessService` |
| `SeahorseAgentRegistryAutoConfigurationTests.java` | 扩展断言：repository ports、inbound ports、kernel services、readiness evidence fallbacks 可用 |
| 可选小 adapter | starter 内部或 kernel-neutral adapter 包下新增 default evidence provider | 只返回 evidence ref 和保守 status；不查 JDBC，不伪造真实证据 |

#### 21.5.3 默认 evidence 策略

| Evidence | 默认状态 | 原因码 |
| --- | --- | --- |
| Agent definition | 有 `AgentDefinitionRepositoryPort` 与 version evidence 时按真实状态；缺失时 `FAIL` | `OWNER_MISSING` 或 `PUBLISHED_VERSION_MISSING` |
| Tool risk | 缺真实 tool risk evidence 时 `WARN`，高风险无法确认时不得 `PASS` | `TOOL_RISK_UNREVIEWED` |
| Resource ACL | 缺 ACL evidence 时 `WARN` 或高风险 `FAIL` | `RESOURCE_ACL_MISSING` |
| Eval | 有 `AgentEvalSummaryRepositoryPort` 时读取 latest；缺失时 `FAIL` | `EVAL_MISSING` |
| Quota | 有 `QuotaPolicyRepositoryPort` 时读取 active quota；缺失时 `WARN` 或高风险 `FAIL` | `QUOTA_MISSING` |
| Audit | 有 `AuditEventRepositoryPort` 时返回可查询 evidence；缺失时 `FAIL` | `AUDIT_MISSING` |
| Rollback | 有 activation/rollback evidence 时 `PASS`；缺失时 `WARN` 或高风险 `FAIL` | `ROLLBACK_TARGET_MISSING` |

如果当前真实 repository port 缺少支撑某项 evidence 的查询方法，starter fallback 必须明确 `WARN/FAIL`，不得为了生成漂亮 report 写硬编码 `PASS`。

#### 21.5.4 TDD 执行步骤

1. 扩展 starter RED 后运行：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

2. 实现 repository/service/evidence wiring。
3. 再运行同一 starter 命令，预期 GREEN。
4. 运行包含 8B/8C/8D 的 starter regression，确保 production gate、quota/SRE、rollout/readiness beans 同时存在。

#### 21.5.5 完成判定

starter 子阶段完成必须证明：所有 Phase 8D ports/services 在 Spring context 中可解析；缺 evidence 时 readiness report 保守降级而非伪 pass；kernel 仍不依赖 Spring/JDBC/Web。

### 21.6 Phase 8D-5 方案：Focused Regression 与 Evidence Closure

#### 21.6.1 目标

把 Phase 8D 从“代码存在”收成“有证据的 focused complete”。该阶段不再写新功能，只串行运行 owner-boundary tests、扫描和 Aegis 记录。

#### 21.6.2 回归命令

Kernel：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,EnterprisePilotReadinessReportTests,KernelAgentRolloutServiceTests,KernelEnterprisePilotReadinessServiceTests' test
```

JDBC：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Web：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Starter：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

Scans：

```powershell
rg -n "org\.springframework|javax\.sql|java\.sql|RestTemplate|WebClient" seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports
rg -n "rawPrompt|rawToolOutput|secret-token|sampleInput|sampleOutput|stackTrace|credential:|bearer " seahorse-agent-kernel/src/main/java seahorse-agent-adapter-web/src/main/java seahorse-agent-adapter-repository-jdbc/src/main/java
git diff --check
```

#### 21.6.3 完成判定

Phase 8D focused complete 的证据必须覆盖：

1. rollout domain 阻止非法 canary percent 和 terminal 回退。
2. promote 对缺 gate、非 pass gate、version mismatch 全部 fail-closed。
3. rollback 通过 Phase 6 owner，不直接改 active version。
4. readiness report 九项 check 完整，overall severity 聚合正确。
5. JDBC/Web/starter 路径全部通过 focused tests。
6. raw scan 不出现敏感 evidence 保存或回显。
7. Aegis checkpoint/evidence 记录命令、结果、风险和下一步。

### 21.7 Final Completion Audit 方案：AI Infra 完成判定前总审计

#### 21.7.1 目标

Phase 8D 通过后，不能直接宣称 AI Infra 全部完成。必须把 Phase 4、Phase 5、Phase 7、Phase 8B、Phase 8C、Phase 8D 的 focused regression 串行跑一遍，并补齐 dependency scan、raw evidence scan、文档索引和 Aegis evidence bundle。该阶段只做验收，不新增功能。

#### 21.7.2 审计范围

| 范围 | 验证重点 |
| --- | --- |
| Phase 4 | ACL import commit、dry-run/provenance、access decision、audit bridge 不保存敏感原文 |
| Phase 5 | OAuth credential、connector import/disable/binding、sandbox session/execution/artifact、安全默认值 |
| Phase 7 | local Agent-as-Tool、handoff record、query/cancel、context handoff policy、starter wiring |
| Phase 8B | eval summary append/latest/history、production gate 读取 eval evidence |
| Phase 8C | quota decision、cost usage append/aggregate、SRE health aggregation、production gate 读取 quota/SRE evidence |
| Phase 8D | rollout/readiness kernel/JDBC/Web/starter 全链路 |

#### 21.7.3 完成判定

只有当以下条件全部成立，才可以把 active goal 标记为 complete：

1. 每个 focused regression command 都有 Aegis evidence 记录，且通过。
2. kernel forbidden dependency scan 无命中。
3. raw evidence scan 无生产路径敏感原文命中；若有历史无关命中，必须逐条解释。
4. `git diff --check` 没有 whitespace error；CRLF warning 若来自既有文件，需在 evidence 中注明。
5. README 与 implementation pack 指向最新 section 21，不再把旧 Approval API 或 section 20 当作当前主入口。
6. 未完成项只剩明确非目标：真实流量百分比分流、远程 A2A mesh、真实 secret vault、真实 sandbox container runtime、Prometheus exporter、前端发布向导、分布式限流和真实扣费。

### 21.8 最新执行判定

当前最新主线顺序为：

1. Phase 8D-1：先运行并修正 kernel service GREEN，记录证据。
2. Phase 8D-2：按 TDD 补 JDBC adapter/schema。
3. Phase 8D-3：按 TDD 补 Web API。
4. Phase 8D-4：补 starter wiring 与保守 evidence provider。
5. Phase 8D-5：跑 Phase 8D focused regression 与扫描。
6. Final Completion Audit：串行跑 Phase 4/5/7/8B/8C/8D 回归和全局扫描，再判断 AI Infra 是否完成。

本节是当前代码面校准后的最新设计开发方案，不是完成声明。

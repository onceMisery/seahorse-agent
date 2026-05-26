# AI Infra 未完成阶段详细设计开发方案

更新日期：2026-05-26

本文档基于 `docs/company-agent/` 与 `docs/company-agent/ai-infra-phases/` 的规划文档、当前代码结构和近期 Aegis 工作记录，补充每个未完成阶段的可执行设计开发方案。它不替代 Phase 0-8 原始阶段文档，而是把当前实现状态之后的剩余工作收敛成更具体的实施路线。

## 1. 判定依据与当前完成度

### 1.1 读取依据

- `docs/company-agent/Seahorse Agent 企业级 AI Infra 架构基线.md`
- `docs/company-agent/Seahorse Agent 企业级 AI Infra 测试基线.md`
- `docs/company-agent/Seahorse Agent 企业级 AI Infra 分阶段开发规划.md`
- `docs/company-agent/Seahorse Agent 与企业级 Agent 差距分析.md`
- `docs/company-agent/企业级Agent落地，你绕不开的 4 个工程问题-2026-05-22 23_34_00.md`
- `docs/company-agent/Agentic ERP：下一代企业操作系统架构全解析-2026-05-22 23_33_23.md`
- `docs/company-agent/ai-infra-phases/00-architecture-baseline.md` 到 `08-production-hardening.md`
- `docs/company-agent/ai-infra-phases/99-current-implementation-handoff.md`
- `docs/aegis/work/2026-05-23-ai-infra-approval-management/`
- `docs/aegis/work/2026-05-24-ai-infra-contextpack-runtime/`
- `docs/aegis/work/2026-05-24-ai-infra-contextpack-producer/`
- `docs/aegis/work/2026-05-25-ai-infra-contextpack-acl/`
- `docs/aegis/work/2026-05-25-ai-infra-access-decision-audit/`
- `docs/aegis/work/2026-05-25-ai-infra-mcp-static-bearer/`
- `docs/aegis/work/2026-05-25-ai-infra-secret-api/`
- `docs/aegis/work/2026-05-25-ai-infra-resource-acl-management/`
- `docs/aegis/work/2026-05-25-ai-infra-openapi-connector-import/`
- `docs/aegis/plans/2026-05-25-ai-infra-agent-run-retry.md`

### 1.2 代码证据摘要

| 阶段 | 当前状态 | 代码证据 | 本文是否补方案 |
| --- | --- | --- | --- |
| Phase 0 | 已形成架构基线 | `docs/company-agent/ai-infra-phases/00-architecture-baseline.md`、包结构已存在 | 否 |
| Phase 1 | 已形成 Agent Definition、Version、Run、Step、Run Store 主闭环 | `kernel/domain/agent/definition`、`kernel/domain/agent/runtime`、`AgentDefinitionInboundPort`、`AgentRunInboundPort`、JDBC registry/run store | 否 |
| Phase 2 | 已形成 Tool Catalog、Tool Gateway、Policy、Tool Audit 主闭环 | `kernel/domain/agent/tool`、`LocalToolGatewayPort`、`ToolCatalogRepositoryPort`、`ToolInvocationAuditQueryInboundPort` | 否 |
| Phase 3 | Approval、Checkpoint、WAITING_APPROVAL、Resume、Lease、Retry 已基本闭环 | `KernelApprovalManagementService`、`RepositoryAgentApprovalWaitHandler`、`KernelAgentRunResumeService`、`KernelAgentRunLeaseService`、`AgentRun.retry()` | 否，生产化 worker 硬化纳入 Phase 8 |
| Phase 4 | ContextPack、默认 ACL、AccessDecision audit/query 已完成；资源 ACL 持久化与变更 API 当前 worktree 已实现并通过聚焦验证，剩余是 DB 约束、批量导入和审计接入硬化 | `ContextPack`、`KernelContextPackBuilderService`、`DefaultResourceAccessPolicyPort`、`AclBackedResourceAccessPolicyPort`、`JdbcResourceAclRepositoryAdapter`、`SeahorseResourceAclController` | 是 |
| Phase 5 | Secret API 与 MCP static bearer 已完成；OpenAPI Connector kernel/parser/JDBC/Web/Starter 当前 worktree 已实现并通过聚焦验证；OAuth 与 Sandbox 未完成 | `CredentialAuthType` 当前生产闭环以 `NONE/STATIC_BEARER` 为主；`kernel/domain/agent/connector`、`seahorse-agent-adapter-openapi`、`JdbcConnectorRepositoryAdapter`、`SeahorseOpenApiConnectorController`、starter connector wiring 已出现；未见 OAuthToken/Sandbox 模块 | 是 |
| Phase 6 | Agent Factory/Studio 基本未开始 | 未见 `AgentTemplate`、`AgentFactory`、publish check、rollback 模块 | 是 |
| Phase 7 | Multi-Agent/A2A/Mesh 基本未开始 | 仅 `AgentRunTriggerType.A2A`、`ToolProvider` 文档性枚举基础；未见 Handoff/RemoteAgent 模块 | 是 |
| Phase 8 | Retrieval eval、RateLimiter、Feature health 有基础；Audit Ledger、Quota、通用 Eval、Canary、SRE 生产面未闭环 | `RetrievalEvaluation*`、`RateLimiterPort`、`FeatureHealthAggregator` 已存在；未见 `AuditLedger`、通用 `EvalDataset`、`QuotaPolicy`、Canary 模块 | 是 |

### 1.3 全局实施原则

1. Kernel 只依赖领域对象和 port；不得依赖 Spring、JDBC、Web、HTTP client 或容器运行时。
2. 所有状态、类型、风险、触发源、决策原因使用 enum 或具名常量，不新增字符串魔法值。
3. 通过 port、service、adapter 组合扩展能力，不新增继承式 Agent 能力树。
4. 领域对象维护不变量；应用服务编排授权、规则和仓储调用；Repository 只承诺持久化语义。
5. 先实现最小闭环：可测试、可回滚、可审计，再扩展 UI 或复杂策略语言。
6. 新 adapter 必须满足同一 port 的状态语义和版本不可变语义。
7. 新接口继续按 Definition、Run、Tool、Policy、Approval、Credential、Context、Connector、Sandbox、Evaluation、Audit、Quota 拆分，不新增大一统 `AgentService`。

## 2. Phase 4 剩余方案：资源 ACL 持久化与变更闭环

### 2.1 目标

补齐资源 ACL 的“可管理、可审计、可被 ContextPack 构建使用”闭环。当前默认 ACL 已能处理 owner/public 规则，AccessDecision 也可记录和查询；剩余缺口是业务资源级 ACL 规则没有持久模型和管理 API，导致 ContextPack 只能依赖硬编码默认规则或自定义 `ResourceAccessPolicyPort`。

### 2.2 非目标

- 不建设完整 IAM/RBAC 平台。
- 不引入复杂策略语言。
- 不改变 `ContextPack`、`ContextItem`、`AccessDecision` 的领域不变量。
- 不移除当前默认 owner/public 规则；新 ACL policy 以组合方式包裹默认规则。

### 2.3 设计

新增领域对象放在 `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/context/`：

| 对象 | 职责 |
| --- | --- |
| `ResourceAclRule` | 单条资源授权规则，维护 tenant、resource、subject、action、effect、status、priority、expiresAt 不变量 |
| `ResourceAclRuleStatus` | `ENABLED`、`DISABLED`、`EXPIRED` |
| `ResourceAclRuleScope` | `EXACT_RESOURCE`、`RESOURCE_TYPE`，首版只启用 `EXACT_RESOURCE` |
| `ResourceAclRuleConflictPolicy` | `DENY_WINS`，作为具名常量固定首版冲突策略 |

复用既有枚举：

- `AccessSubjectType`
- `ResourceAction`
- `ContextResourceType`
- `AccessDecisionEffect`
- `ResourceAccessReasonCodes`

新增 port：

```text
ResourceAclManagementInboundPort
  create(ResourceAclCreateCommand) -> ResourceAclRule
  disable(String ruleId) -> ResourceAclRule
  page(ResourceAclQuery) -> ResourceAclRulePage

ResourceAclRepositoryPort
  save(ResourceAclRule rule)
  findById(String ruleId)
  page(ResourceAclQuery query)
  findEffective(ResourceAclLookup lookup)
```

新增应用服务：

| 服务 | 职责 |
| --- | --- |
| `KernelResourceAclManagementService` | 管理员创建、禁用、分页查询 ACL 规则 |
| `AclBackedResourceAccessPolicyPort` | 先查持久 ACL；命中后生成 `AccessDecision`；未命中时委托现有 `DefaultResourceAccessPolicyPort` |

数据表：

```sql
CREATE TABLE sa_resource_acl_rule (
  rule_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  resource_type VARCHAR(64) NOT NULL,
  resource_id VARCHAR(128) NOT NULL,
  subject_type VARCHAR(32) NOT NULL,
  subject_id VARCHAR(64) NOT NULL,
  action VARCHAR(32) NOT NULL,
  effect VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  priority INT NOT NULL,
  expires_at TIMESTAMP,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_sa_resource_acl_lookup
  ON sa_resource_acl_rule(tenant_id, resource_type, resource_id, subject_type, subject_id, action, status);
```

Web API：

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/resource-acl-rules` | 创建资源 ACL 规则 |
| `GET` | `/api/resource-acl-rules` | 按 tenant、resource、subject、status 分页 |
| `POST` | `/api/resource-acl-rules/{ruleId}/disable` | 禁用规则 |

### 2.4 任务切片

1. 领域与端口切片
   新增 `ResourceAclRule`、命令、查询、分页对象和 port；测试规则字段不能为空、过期规则不可命中、deny 优先。

2. Kernel 管理服务切片
   `KernelResourceAclManagementService` 只允许 admin 调用；创建规则时规范化 subject/resource/action/effect；禁用幂等。

3. JDBC 适配器切片
   `JdbcResourceAclRepositoryAdapter` 持久化规则；`findEffective` 按 priority、deny 优先、createdAt 稳定排序。

4. Policy 组合切片
   `AclBackedResourceAccessPolicyPort` 组合 `ResourceAclRepositoryPort` 与当前默认 policy，不修改 ContextPack builder。

5. Web 与 starter 切片
   新增 controller 与 auto-configuration；当 JDBC repository 和默认 policy 可用时自动装配 ACL-backed policy。

### 2.5 验证

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=SeahorseWebApiContractTests#shouldKeepAgentRegistryAndRunStoreApiContracts' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

### 2.6 退出条件

1. 管理员能创建、查询、禁用资源 ACL 规则。
2. ContextPack builder 可通过持久 ACL 允许或拒绝资源进入 prompt。
3. 每次 ACL 决策仍写入 `sa_access_decision_log`。
4. 不确定或冲突场景默认 deny。
5. 自定义 `ResourceAccessPolicyPort` 仍可替换默认实现。

### 2.7 首个详细开发方案：Resource ACL 持久化管理闭环

本阶段首个落地切片只解决一个问题：把当前 owner/public 默认规则扩展为可运营的资源 ACL 规则，并让 ContextPack 构建继续通过同一个 `ResourceAccessPolicyPort` 得到决策。该切片不改变 `ContextPack`、`ContextItem`、`AccessDecision` 的不变量，也不把 ACL 规则语言做成通用策略引擎。

#### 2.7.1 方案基线

| 依据 | 结论 |
| --- | --- |
| Phase 4 原始文档 | 资源必须有 ACL 决策，ContextPack item 没有权限证据不得进入 prompt。 |
| 架构基线 | Context DB owner 负责来源、ACL、预算；kernel 不依赖 Spring/JDBC/Web。 |
| 测试基线 | 切片必须有 kernel owner-boundary 测试、JDBC adapter 测试、Web contract 测试。 |
| 当前实现状态 | 默认 ACL、AccessDecision audit/query 已有；缺少可持久管理的 `ResourceAclRule`。 |

#### 2.7.2 模块归属与文件边界

| 层 | 新增或修改位置 | 职责边界 |
| --- | --- | --- |
| Domain | `seahorse-agent-kernel/.../domain/agent/context/ResourceAclRule.java` 及 enum | 只维护规则字段、过期、禁用、冲突排序所需不变量。 |
| Inbound Port | `ports/inbound/agent/ResourceAclManagementInboundPort.java` | 管理 API 契约：create、disable、page。 |
| Outbound Port | `ports/outbound/agent/ResourceAclRepositoryPort.java` | 持久化契约：save、findById、page、findEffective。 |
| Application | `kernel/application/agent/context/KernelResourceAclManagementService.java` | admin 编排、幂等禁用、命令规范化。 |
| Application | `kernel/application/agent/context/AclBackedResourceAccessPolicyPort.java` | 组合持久 ACL 与默认 policy，命中 ACL 后返回 `AccessDecision`。 |
| JDBC | `adapter-repository-jdbc/JdbcResourceAclRepositoryAdapter.java` | SQL 映射、分页、有效规则排序，不承载业务判断。 |
| Web | `adapter-web/SeahorseResourceAclController.java` | HTTP DTO 转 command/query，错误响应复用现有 `ApiResponses`。 |
| Starter | registry repository 与 kernel registry auto-configuration | 条件装配 repository、management service、ACL-backed policy。 |

#### 2.7.3 领域规则

1. `ResourceAclRuleStatus` 只允许 `ENABLED`、`DISABLED`、`EXPIRED`。
2. `ResourceAclRuleScope` 首版只允许管理 API 创建 `EXACT_RESOURCE`；`RESOURCE_TYPE` 仅保留 enum，未开放 API。
3. `ResourceAclRuleConflictPolicy.DENY_WINS` 是首版唯一冲突策略。
4. 有效规则必须满足：tenant/resource/subject/action 全部匹配、status 为 `ENABLED`、`expiresAt` 为空或晚于当前时间。
5. 多条规则命中时，先按 `priority` 降序，再按 effect deny 优先，再按 `createdAt` 升序稳定排序。
6. 禁用是幂等操作；已禁用规则再次 disable 返回当前规则，不抛业务错误。
7. 管理服务创建规则时必须记录 `createdBy`，不得允许匿名 operator。

#### 2.7.4 API 合约

`POST /api/resource-acl-rules`

```json
{
  "tenantId": "default",
  "resourceType": "DOCUMENT",
  "resourceId": "doc_123",
  "subjectType": "USER",
  "subjectId": "u_1",
  "action": "READ",
  "effect": "ALLOW",
  "priority": 100,
  "expiresAt": "2026-06-30T00:00:00Z"
}
```

`GET /api/resource-acl-rules?tenantId=default&resourceType=DOCUMENT&resourceId=doc_123&status=ENABLED&page=0&size=20`

`POST /api/resource-acl-rules/{ruleId}/disable`

响应体直接返回规则快照；所有状态、action、effect、subjectType、resourceType 按 enum 字符串序列化，不新增自由文本状态。

#### 2.7.5 TDD 开发顺序

1. 写 `ResourceAclRuleTests`：覆盖必填字段、过期判断、disable 幂等、deny wins 排序辅助规则。
2. 跑 kernel RED：确认缺少 domain/enum/方法导致失败。
3. 实现 domain 与 port，不接 JDBC/Web。
4. 写 `KernelResourceAclManagementServiceTests`：admin 可创建/分页/禁用，非 admin 被拒绝。
5. 实现 management service。
6. 写 `AclBackedResourceAccessPolicyPortTests`：ACL allow、ACL deny、过期不命中、未命中回退默认 policy、deny wins。
7. 实现 ACL-backed policy。
8. 写 `JdbcResourceAclRepositoryAdapterTests`：insert、findById、page、findEffective 排序与过期过滤。
9. 实现 JDBC adapter 与 `sa_resource_acl_rule` schema。
10. 写 Web/starter 测试：controller contract、repository bean、management bean、默认 policy 被 ACL-backed 组合。
11. 实现 controller 与 auto-configuration。

#### 2.7.6 验收证据

必须能用测试证明：

1. `ContextPack` 构建路径不需要改调用方，只要注入 ACL-backed `ResourceAccessPolicyPort` 就能使用持久 ACL。
2. deny 规则优先于 allow 规则。
3. 过期、禁用、未命中规则都不会放行资源。
4. `sa_access_decision_log` 仍记录每次最终决策。
5. 用户自定义 `ResourceAccessPolicyPort` 时，starter 不强制覆盖。

#### 2.7.7 回滚边界

1. 新表 `sa_resource_acl_rule` 只追加，不改变既有 context/access decision 表。
2. 可通过 starter 条件装配关闭 ACL-backed policy，回退到 `DefaultResourceAccessPolicyPort`。
3. 已创建 ACL 规则保留用于审计，不在回滚脚本中删除。
4. Web API 首版不替换任何旧 API，关闭 controller bean 即可停止外部变更入口。

## 3. Phase 5 剩余方案：OAuth、OpenAPI Connector、Sandbox Runtime

### 3.1 目标

把外部系统接入从“静态 bearer + encrypted secret”推进到安全可运营的连接器层：MCP OAuth token、OpenAPI spec 导入成 ToolCatalog、沙箱执行环境三条线都必须经过 Tool Gateway、Credential、Policy 和 Audit。

### 3.2 非目标

- 不在首版实现浏览器 UI 连接器市场。
- 不在主 JVM 直接执行任意脚本。
- 不在 OpenAPI 导入切片执行真实 HTTP operation。
- 不实现完整 OAuth 授权码交互 UI；首版只做 client credentials 和 user delegated token 引用。

### 3.3 方案 5A：MCP OAuth Token Provider

扩展领域与端口：

```text
CredentialAuthType
  NONE
  STATIC_BEARER
  CLIENT_CREDENTIALS
  USER_DELEGATED

OAuthTokenPort
  getToken(OAuthTokenRequest request) -> OAuthToken
  refresh(OAuthRefreshCommand command) -> OAuthToken
  revoke(OAuthRevokeCommand command)

OAuthTokenCachePort
  get(OAuthTokenCacheKey key)
  put(OAuthTokenCacheKey key, OAuthToken token, Duration ttl)
  evict(OAuthTokenCacheKey key)
```

新增对象：

| 对象 | 职责 |
| --- | --- |
| `OAuthTokenRequest` | tenant、server、grantType、clientId、clientSecretRef、scopes、audience、resource |
| `OAuthToken` | accessToken、tokenType、expiresAt、scopeSet、refreshTokenRef |
| `OAuthGrantType` | `CLIENT_CREDENTIALS`、`USER_DELEGATED` |
| `OAuthScopeChallenge` | 从 401/403 challenge 中解析 required scopes |

MCP 配置扩展：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `authType` | `CredentialAuthType` | `NONE`、`STATIC_BEARER`、`CLIENT_CREDENTIALS`、`USER_DELEGATED` |
| `authorizationServerMetadataUrl` | String | OAuth metadata |
| `protectedResourceMetadataUrl` | String | MCP resource metadata |
| `clientId` | String | OAuth client id |
| `clientSecretRef` | String | secret 引用 |
| `scopes` | List<String> | 默认 scopes |
| `audience` | String | token audience |
| `resource` | String | resource indicator |
| `trustPolicyId` | String | 信任策略引用 |

实现路径：

1. `McpHttpAutoConfiguration` 根据 `CredentialAuthType` 构造 `CredentialRequest`。
2. `OAuthCredentialProvider` 组合 `SecretStorePort`、`OAuthTokenPort`、`OAuthTokenCachePort`，返回 `CredentialMaterial.bearer(...)`。
3. `StreamableHttpMcpClient` 只接收最终 bearer material，不知道 secret 和 OAuth 细节。
4. 401/403 scope challenge 只触发一次补 scope 重试；再次失败写 audit 并返回失败。

验证：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=OAuthCredentialProviderTests,CredentialMaterialTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-mcp-http -am '-Dtest=McpHttpAutoConfigurationCredentialTests,StreamableHttpMcpClientCredentialTests,McpHttpOAuthCredentialTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

### 3.4 方案 5B：OpenAPI Connector 导入

新增模块建议：

- Kernel 领域与 port 放在 `kernel/domain/agent/connector`、`ports/inbound/connector`、`ports/outbound/connector`。
- OpenAPI 解析 adapter 可先放在新模块 `seahorse-agent-adapter-openapi`，避免 kernel 依赖 parser。
- JDBC 持久化继续放在 `seahorse-agent-adapter-repository-jdbc`。

新增领域对象：

| 对象 | 职责 |
| --- | --- |
| `Connector` | 外部系统连接器主记录 |
| `ConnectorVersion` | 一份 OpenAPI spec 的不可变导入版本 |
| `ConnectorOperation` | operationId、method、path、toolId、risk、action、resource 映射 |
| `ConnectorStatus` | `DRAFT`、`IMPORTED`、`ENABLED`、`DISABLED` |
| `ConnectorProvider` | `OPENAPI`、后续可扩展 `MCP`、`INTERNAL` |
| `ConnectorOperationStatus` | `DISABLED`、`REVIEW_REQUIRED`、`ENABLED` |

新增 port：

```text
OpenApiConnectorInboundPort
  importSpec(OpenApiImportCommand command) -> ConnectorImportResult
  page(ConnectorQuery query) -> ConnectorPage
  listOperations(String connectorId) -> List<ConnectorOperation>
  enableOperation(ConnectorOperationEnableCommand command) -> ConnectorOperation

OpenApiSpecParserPort
  parse(OpenApiSpecParseRequest request) -> OpenApiSpecDocument

ConnectorRepositoryPort
  saveConnector(...)
  saveVersion(...)
  saveOperations(...)
  page(...)
  listOperations(...)
```

导入规则：

1. `operationId` 为空时生成稳定 toolId：`connector.{connectorId}.{method}.{normalizedPathHash}`。
2. `GET` 默认 `ToolActionType.READ` 和 `ToolRiskLevel.LOW`。
3. `POST/PATCH/PUT` 默认 `WRITE` 和 `MEDIUM`。
4. `DELETE` 默认 `DELETE` 和 `HIGH`，且 `requiresApproval=true`。
5. 所有导入的 operation 初始 `DISABLED`，管理员确认后才可启用。
6. spec 原文存版本记录，ToolCatalogEntry 只存工具元信息，不存 secret。

数据表：

```sql
CREATE TABLE sa_connector (
  connector_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  provider VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);

CREATE TABLE sa_connector_version (
  connector_version_id VARCHAR(64) PRIMARY KEY,
  connector_id VARCHAR(64) NOT NULL,
  version_no BIGINT NOT NULL,
  spec_hash VARCHAR(128) NOT NULL,
  spec_json TEXT NOT NULL,
  imported_by VARCHAR(64) NOT NULL,
  imported_at TIMESTAMP NOT NULL
);

CREATE TABLE sa_connector_operation (
  operation_id VARCHAR(64) PRIMARY KEY,
  connector_id VARCHAR(64) NOT NULL,
  connector_version_id VARCHAR(64) NOT NULL,
  tool_id VARCHAR(128) NOT NULL,
  method VARCHAR(16) NOT NULL,
  path VARCHAR(512) NOT NULL,
  operation_key VARCHAR(256) NOT NULL,
  action_type VARCHAR(32) NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  requires_approval BOOLEAN NOT NULL,
  metadata_json TEXT,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/connectors/openapi` | 导入 OpenAPI spec |
| `GET` | `/api/connectors` | connector 分页 |
| `GET` | `/api/connectors/{connectorId}/operations` | operation 列表 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/enable` | 启用 operation 并写 ToolCatalog |

验证：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelOpenApiConnectorImportServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcConnectorRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=SeahorseOpenApiConnectorControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

### 3.5 方案 5C：Sandbox Runtime 外部适配

新增领域对象：

| 对象 | 职责 |
| --- | --- |
| `SandboxSession` | run 隔离执行会话 |
| `SandboxExecution` | 单次执行记录 |
| `SandboxArtifact` | 出沙箱 artifact 元数据 |
| `SandboxRuntimeType` | `CODE_INTERPRETER`、`BROWSER_AUTOMATION`、`SHELL`、`FILE_CONVERSION` |
| `SandboxNetworkPolicy` | `DENY_ALL`、`ALLOWLISTED` |
| `SandboxExecutionStatus` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`TIMED_OUT`、`CANCELLED` |

新增 port：

```text
SandboxRuntimeInboundPort
  createSession(SandboxSessionCreateCommand command) -> SandboxSession
  execute(SandboxExecuteCommand command) -> SandboxExecutionResult
  close(String sessionId) -> SandboxSession

SandboxRuntimePort
  createSession(SandboxSessionRequest request) -> SandboxSession
  execute(SandboxExecutionRequest request) -> SandboxExecutionResult
  close(String sessionId)

SandboxPolicyPort
  decide(SandboxPolicyRequest request) -> SandboxPolicyDecision

SandboxArtifactPort
  saveArtifact(SandboxArtifactSaveCommand command) -> SandboxArtifact
```

首版实现：

1. `ExternalSandboxRuntimeAdapter` 调外部 sandbox service。
2. 主 JVM 只发请求、收结果、保存 artifact，不执行代码。
3. `SandboxPolicyPort` 默认网络 `DENY_ALL`，文件系统按 run/session 隔离。
4. artifact 出沙箱前经过敏感级别标记；`SECRET` 不返回模型 prompt。
5. sandbox 工具作为 ToolCatalogEntry，调用仍走 Tool Gateway。

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/sandbox/sessions` | 创建 session |
| `POST` | `/api/sandbox/sessions/{sessionId}/execute` | 执行命令 |
| `POST` | `/api/sandbox/sessions/{sessionId}/close` | 关闭 session |
| `GET` | `/api/sandbox/sessions/{sessionId}/artifacts` | 查询 artifact |

验证：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelSandboxRuntimeServiceTests,DefaultSandboxPolicyPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=SeahorseSandboxControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

### 3.6 Phase 5 退出条件

1. MCP OAuth token 可获取、缓存、刷新，且 token 不进入 prompt/log/trace。
2. OpenAPI spec 可导入并生成 disabled ToolCatalogEntry。
3. 高风险 OpenAPI operation 默认需要审批。
4. Sandbox 首版通过外部 adapter 执行，主 JVM 不执行任意脚本。
5. 所有 connector、credential、sandbox 调用都可关联 runId、agentId、toolId 和 audit。

### 3.7 首个详细开发方案：OpenAPI Connector 只导入不执行

Phase 5 的首个落地切片建议先做 OpenAPI Connector 导入，不做真实 HTTP 调用。原因是它能最小风险地补齐企业工具目录入口：把外部 API 解析成可审查、默认禁用、可进入 ToolCatalog 的 operation，同时不碰凭据执行、网络访问和远程系统副作用。

#### 3.7.1 方案基线

| 依据 | 结论 |
| --- | --- |
| Phase 5 原始文档 | OpenAPI、MCP、Sandbox 都必须经过 Tool Gateway、Credential、Policy、Audit。 |
| 差距分析 | 当前 MCP 接入有雏形，但外部企业 API 缺少连接器、风险分级和凭据隔离。 |
| 架构原则 | OpenAPI parser 不能进入 kernel；kernel 只接收解析后的抽象文档。 |
| 当前实现状态 | ToolCatalog/ToolPolicy 已存在，适合作为导入结果的落点。 |

#### 3.7.2 模块归属与文件边界

| 层 | 新增或修改位置 | 职责边界 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/connector` | `Connector`、`ConnectorVersion`、`ConnectorOperation` 及 enum。 |
| Inbound Port | `ports/inbound/agent/OpenApiConnectorInboundPort.java` | 导入 spec、分页 connector、列 operation、启用 operation。 |
| Outbound Port | `ports/outbound/agent/OpenApiSpecParserPort.java` | parser adapter 契约，kernel 不依赖 OpenAPI 库。 |
| Outbound Port | `ports/outbound/agent/ConnectorRepositoryPort.java` | connector/version/operation 持久化契约。 |
| Application | `kernel/application/agent/connector/KernelOpenApiConnectorImportService.java` | 导入编排、风险推断、ToolCatalog 写入前校验。 |
| Parser Adapter | `seahorse-agent-adapter-openapi` | 调用 OpenAPI parser，把 spec 转为 `OpenApiSpecDocument`。 |
| JDBC | `JdbcConnectorRepositoryAdapter` | 存储 connector、version、operation。 |
| Web | `SeahorseOpenApiConnectorController` | 管理 API，首版只允许 admin。 |

#### 3.7.3 导入领域规则

1. `ConnectorProvider` 首版只开放 `OPENAPI`。
2. `ConnectorStatus` 使用 `DRAFT`、`IMPORTED`、`ENABLED`、`DISABLED`。
3. `ConnectorOperationStatus` 初始一律为 `DISABLED`，不得导入后自动可执行。
4. `operationId` 缺失时生成稳定 `operationKey`：`{method} {normalizedPath}`；`toolId` 使用 connector id 与 hash 派生，避免路径魔法拼接扩散。
5. HTTP 方法到动作和风险的映射集中在 `ConnectorOperationRiskMapper` 私有组件：
   - `GET` -> `ToolActionType.READ` + `ToolRiskLevel.LOW`
   - `POST`、`PUT`、`PATCH` -> `ToolActionType.WRITE` + `ToolRiskLevel.MEDIUM`
   - `DELETE` -> `ToolActionType.DELETE` + `ToolRiskLevel.HIGH` + `requiresApproval=true`
6. spec hash 相同且 connector 相同的重复导入返回已有 version 摘要，不重复写 operation。
7. spec 原文只能存在 `sa_connector_version.spec_json`，ToolCatalogEntry 只保存执行所需元数据和 schema 摘要，不保存 secret。

#### 3.7.4 API 合约

`POST /api/connectors/openapi`

```json
{
  "tenantId": "default",
  "name": "crm-api",
  "specJson": "{ \"openapi\": \"3.0.3\", \"paths\": {} }",
  "importedBy": "admin"
}
```

响应：

```json
{
  "connectorId": "conn_123",
  "connectorVersionId": "connv_1",
  "status": "IMPORTED",
  "operationCount": 12,
  "disabledOperationCount": 12,
  "highRiskOperationCount": 2
}
```

`POST /api/connectors/{connectorId}/operations/{operationId}/enable`

启用时只把 operation 注册或更新到 ToolCatalog，真实执行仍由后续 Connector Tool Adapter 切片完成。首版启用操作要求 admin，并且高风险 operation 必须已经有 approval policy 或保持 `REVIEW_REQUIRED`。

#### 3.7.5 TDD 开发顺序

1. 写 `ConnectorOperationRiskMapperTests`：覆盖 GET/POST/PUT/PATCH/DELETE 和 unknown method 拒绝。
2. 写 `KernelOpenApiConnectorImportServiceTests`：parser 输出两个 operation，导入后 connector/version/operation 持久化且 operation 默认 disabled。
3. 写重复导入测试：相同 spec hash 不重复创建 version。
4. 实现 domain、mapper、service 和 ports。
5. 写 `OpenApiSpecParserAdapterTests`：最小 OpenAPI 3.0 spec 能解析 path、method、operationId、schema 摘要。
6. 实现 `seahorse-agent-adapter-openapi`，只暴露 parser adapter。
7. 写 `JdbcConnectorRepositoryAdapterTests`：save connector/version/operation、page、listOperations、状态更新。
8. 实现 JDBC 表与 adapter。
9. 写 `SeahorseOpenApiConnectorControllerTests`：admin 导入、分页、operation 列表、启用 operation。
10. 实现 Web 与 starter 装配。

#### 3.7.6 验收证据

1. 一个最小 OpenAPI 3.0 spec 可导入，并生成 connector/version/operation。
2. 所有 operation 默认 disabled，不会被 Agent 自动执行。
3. DELETE operation 自动标记 high risk 和 requires approval。
4. 启用 operation 后 ToolCatalog 可查询到对应工具元数据。
5. kernel 编译依赖中不出现 OpenAPI parser、HTTP client 或 Spring Web。

#### 3.7.7 回滚边界

1. 关闭 OpenAPI connector auto-configuration 后，不影响已有 ToolCatalog 和 MCP。
2. `sa_connector*` 表只追加；回滚不删除历史导入记录。
3. 导入生成的 ToolCatalogEntry 可通过 provider/status 禁用，不删除审计依据。
4. 因首版不执行真实 HTTP operation，回滚不涉及外部系统补偿。

## 4. Phase 6 剩余方案：Agent Factory 与 Agent Studio

### 4.1 目标

把 Phase 1-5 的底层能力包装成可运营的 Agent Factory：业务团队能从模板创建 draft、收窄权限派生 Agent、通过发布门禁、执行 dry-run、回滚到历史版本，并在 Studio 中完成主要配置。

### 4.2 非目标

- 不引入继承式 Agent 类树；派生是配置快照关系，不是 Java 继承。
- 不允许 UI 编辑已发布 `AgentVersion`。
- 不在首版提供可视化工作流引擎。
- 不在模板中存明文 secret。

### 4.3 领域设计

新增领域包：`kernel/domain/agent/factory`。

| 对象 | 职责 |
| --- | --- |
| `AgentTemplate` | 模板定义，包含默认 instructions、默认工具集合、默认风险、派生策略 |
| `AgentTemplateStatus` | `ENABLED`、`DISABLED` |
| `AgentTemplateSource` | `BUILT_IN`、`TENANT_CUSTOM` |
| `AgentDerivationPolicy` | 校验派生配置只能收窄权限 |
| `AgentPublishCheck` | 发布前校验结果 |
| `AgentPublishCheckStatus` | `PASSED`、`FAILED`、`WARNING` |
| `AgentRollbackRequest` | 回滚请求，指向历史 version |
| `AgentTestRunMode` | `DRY_RUN`、`LIVE_LOW_RISK`，首版默认 `DRY_RUN` |

模板首批内置：

| 模板 ID | 风险 | 默认工具边界 |
| --- | --- | --- |
| `knowledge-assistant` | `LOW` | search、memory-read |
| `knowledge-curator` | `MEDIUM` | search、metadata-review |
| `workflow-assistant` | `MEDIUM` | search、approval |
| `data-analyst` | `MEDIUM` | query、chart |
| `tool-operator` | `HIGH` | custom tools，默认 requiresApproval |
| `compliance-reviewer` | `HIGH` | policy、audit |
| `remote-agent-wrapper` | `MEDIUM` | A2A/MCP，默认 disabled |

### 4.4 Port 与服务

```text
AgentTemplateInboundPort
  listTemplates(boolean includeDisabled) -> List<AgentTemplate>
  findTemplate(String templateId) -> Optional<AgentTemplate>

AgentFactoryInboundPort
  createFromTemplate(AgentFromTemplateCommand command) -> AgentDefinition
  derive(AgentDeriveCommand command) -> AgentDefinition
  validateForPublish(AgentPublishValidationCommand command) -> AgentPublishCheckReport
  rollback(AgentRollbackCommand command) -> AgentVersion
  startTestRun(AgentTestRunCommand command) -> AgentRun

AgentTemplateRepositoryPort
  list(...)
  findById(...)
  upsertTenantTemplate(...)

AgentPublishCheckRepositoryPort
  save(AgentPublishCheckReport report)
  latest(String agentId)
```

应用服务：

| 服务 | 职责 |
| --- | --- |
| `KernelAgentTemplateService` | 暴露 built-in + tenant template |
| `KernelAgentFactoryService` | 从模板创建 draft、派生、回滚和 test-run 编排 |
| `KernelAgentPublishValidationService` | 组合发布检查规则 |
| `AgentDerivationPolicy` | 领域规则：tools 子集、quota 收窄、approval 更严格、guardrails 超集 |

发布检查规则首版：

1. instructions 非空。
2. owner 和 fallback owner 存在。
3. 工具存在且 enabled。
4. 高风险工具有 approval policy。
5. 资源 ACL 可解析或模板不需要企业资源。
6. quota 已配置。
7. eval dataset 存在；Phase 8 通用 eval 未完成前，先允许 retrieval eval 或 dry-run gate。
8. changeSummary 非空。

### 4.5 数据表

```sql
CREATE TABLE sa_agent_template (
  template_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64),
  name VARCHAR(128) NOT NULL,
  description VARCHAR(1000),
  source VARCHAR(32) NOT NULL,
  default_config_json TEXT NOT NULL,
  derivation_policy_json TEXT NOT NULL,
  risk_level VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
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

### 4.6 API 与 UI

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-templates` | 模板列表 |
| `POST` | `/api/agents/from-template` | 从模板创建 draft |
| `POST` | `/api/agents/{agentId}/derive` | 派生业务 Agent |
| `POST` | `/api/agents/{agentId}/validate` | 发布前校验 |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/rollback` | 基于历史版本创建回滚版本 |
| `POST` | `/api/agents/{agentId}/test-runs` | dry-run 测试 |

Studio 页面首版只做运维工具，不做营销页：

| 页面 | 路由 | 数据来源 |
| --- | --- | --- |
| Agent Catalog | `/admin/ai-infra/agents` | Agent Definition/Version/Run |
| Agent Builder | `/admin/ai-infra/agents/new` | Templates、ToolCatalog、ACL、Quota |
| Agent Detail | `/admin/ai-infra/agents/:agentId` | Definition、versions、runs、publish checks |
| Run Timeline | `/admin/ai-infra/runs/:runId` | AgentStep、ToolAudit、Approval、ContextPack |
| Approval Inbox | `/admin/ai-infra/approvals` | Approval API |
| Tool Catalog | `/admin/ai-infra/tools` | ToolCatalog API |

### 4.7 任务切片

1. 模板领域与只读 API
   先用内置模板实现 `GET /api/agent-templates`。

2. 从模板创建 draft
   `KernelAgentFactoryService.createFromTemplate` 生成 `AgentDefinition` draft 和工具绑定，不发布版本。

3. 派生规则
   实现 tools 子集、approval 更严格、quota 收窄、guardrails 超集校验。

4. 发布门禁
   `validateForPublish` 保存 `AgentPublishCheckReport`；先不阻断已有 publish API，下一切片再接入 publish 流程。

5. 回滚
   从历史 `AgentVersion` 创建新 draft 或新 version；不修改历史 version。

6. Dry-run test run
   使用 mock/dry-run ToolPort 组合验证 instructions、工具绑定、ContextPack、Approval 路径。

7. Agent Studio
   在 API 稳定后再接前端页面，先实现 Catalog、Builder、Detail、Approval Inbox。

### 4.8 验证

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentTemplateServiceTests,KernelAgentFactoryServiceTests,AgentDerivationPolicyTests,KernelAgentPublishValidationServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentTemplateRepositoryAdapterTests,JdbcAgentPublishCheckRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
cd frontend; npm run build
```

### 4.9 退出条件

1. 业务团队可从模板创建低风险 draft。
2. 派生 Agent 不能扩大模板权限。
3. 发布前校验能解释失败原因并持久化。
4. 回滚不破坏 version 不可变语义。
5. Studio 能覆盖创建、查看、校验、审批、运行时间线的基础操作。

### 4.10 首个详细开发方案：模板只读与 from-template draft

Phase 6 的首个落地切片不做完整 Agent Studio，也不接发布门禁阻断逻辑。它只提供两件事：业务方可查询内置 Agent 模板，管理员可基于模板创建一个 `DRAFT` 状态的 `AgentDefinition`。这样可以先把 Agent Factory 的入口和权限收窄语义固化下来，再逐步接派生、发布校验、回滚和 UI。

#### 4.10.1 方案基线

| 依据 | 结论 |
| --- | --- |
| Phase 6 原始文档 | 业务团队应能基于模板创建、派生、测试、发布和回滚业务 Agent。 |
| 架构基线 | Agent Registry 是 definition/version 的事实 owner；Factory 只能编排，不复制 registry owner。 |
| 差距分析 | 当前缺少系统化业务 Agent 模型与模板运营入口。 |
| 当前实现状态 | Agent Definition、Version、Run 主闭环已存在；适合把模板落为 draft 创建编排。 |

#### 4.10.2 模块归属与文件边界

| 层 | 新增或修改位置 | 职责边界 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/factory/AgentTemplate.java` 及 enum | 模板不变量、默认配置、风险边界、内置模板标识。 |
| Domain | `AgentTemplateDefaults` | 内置模板常量，避免模板 ID 和工具集合散落。 |
| Inbound Port | `AgentTemplateInboundPort` | 模板查询，不包含创建 Agent。 |
| Inbound Port | `AgentFactoryInboundPort` | from-template draft 创建。 |
| Application | `KernelAgentTemplateService` | 合并 built-in 与后续 tenant template；首版只返回 built-in。 |
| Application | `KernelAgentFactoryService` | 校验模板、构造 `AgentDefinitionCreateCommand`，委托现有 registry service。 |
| JDBC | 首版不新增 template 表 | 内置模板不需要持久化；`sa_agent_template` 延后到 tenant template 切片。 |
| Web | `SeahorseAgentFactoryController` | `GET /api/agent-templates`、`POST /api/agents/from-template`。 |
| Starter | kernel registry auto-configuration | 装配 template service 与 factory service。 |

#### 4.10.3 领域规则

1. 内置模板 ID 使用具名常量，禁止 controller/service 手写字符串。
2. `AgentTemplateStatus` 首版只返回 `ENABLED` 模板；`includeDisabled=true` 仅 admin 可用。
3. `AgentTemplateSource.BUILT_IN` 表示代码内置模板；tenant 自定义模板后续再接 repository。
4. from-template 创建的 Agent 必须是 `DRAFT`，不得自动 publish。
5. 模板默认工具集合只是上限；创建命令请求的工具必须是模板工具集合子集。
6. 模板风险等级是 draft 风险等级上限；请求风险等级不能高于模板风险。
7. 模板不保存 secret、token、credential material；只允许保存 secretRef 字段名或 credential binding 需求说明。
8. from-template 必须写 `createdFromTemplateId` 或等价派生来源字段；如果当前 `AgentDefinition` 没有字段，先放入定义 metadata JSON 中，后续版本模型再提升为显式字段。

#### 4.10.4 API 合约

`GET /api/agent-templates`

响应：

```json
{
  "items": [
    {
      "templateId": "knowledge-assistant",
      "name": "Knowledge Assistant",
      "riskLevel": "LOW",
      "source": "BUILT_IN",
      "status": "ENABLED",
      "defaultToolIds": ["search", "memory-read"]
    }
  ]
}
```

`POST /api/agents/from-template`

```json
{
  "templateId": "knowledge-assistant",
  "tenantId": "default",
  "name": "HR Policy Assistant",
  "description": "Answer HR policy questions from approved knowledge bases.",
  "ownerUserId": "u_owner",
  "requestedToolIds": ["search"],
  "riskLevel": "LOW",
  "instructionsOverlay": "Only answer from approved HR documents."
}
```

响应返回现有 `AgentDefinition` DTO，状态必须是 `DRAFT`。

#### 4.10.5 TDD 开发顺序

1. 写 `AgentTemplateTests`：内置模板字段非空、工具集合不可变、模板 ID 常量稳定。
2. 写 `KernelAgentTemplateServiceTests`：默认只返回 enabled built-in 模板，includeDisabled 非 admin 被拒绝。
3. 实现 domain 与 template service。
4. 写 `KernelAgentFactoryServiceTests`：from-template 创建 draft、工具子集校验、风险等级校验、instructions overlay 合并。
5. 实现 factory service，组合现有 `AgentDefinitionInboundPort` 或更小的 definition create port。
6. 写 `SeahorseAgentFactoryControllerTests`：模板列表、from-template 成功、越权/非法工具失败。
7. 实现 controller 与 starter 装配。
8. 如果前端已有 AI Infra 管理页，新增最小模板选择入口；否则只补 API contract，不做 UI。

#### 4.10.6 验收证据

1. `GET /api/agent-templates` 返回稳定的内置模板列表。
2. `POST /api/agents/from-template` 创建 `DRAFT` AgentDefinition，不创建 AgentVersion。
3. 请求工具超出模板工具上限时失败。
4. 请求风险等级高于模板风险时失败。
5. 非 admin 不能创建高风险模板 draft。
6. 现有 Agent Definition API 和发布流程不受影响。

#### 4.10.7 回滚边界

1. 首版不新增表；关闭 factory controller 和 service 即可回滚入口。
2. 已创建的 draft 是普通 AgentDefinition，可按现有 registry API 禁用或删除草稿。
3. 不改 `AgentVersion` 不可变语义，不影响已发布 Agent。
4. 内置模板通过代码常量提供，回滚不会留下 schema 兼容负担。

## 5. Phase 7 剩余方案：Agent-as-Tool、Handoff、A2A、Mesh Governance

### 5.1 目标

建立受治理的 multi-agent 协作能力。优先做本地 Agent-as-Tool 和 Handoff，再做远程 A2A client/server，最后做 mesh 治理。禁止让多个 Agent 自由互聊绕过 run、tool、policy、context 和 audit。

### 5.2 非目标

- 不引入远程 Agent mesh 作为 Phase 7 第一个切片。
- 不允许循环 handoff 无上限。
- 不把完整 message history 默认传给目标 Agent。
- 不允许远程 Agent 默认 enabled。

### 5.3 领域设计

新增领域包：`kernel/domain/agent/mesh`。

| 对象 | 职责 |
| --- | --- |
| `AgentHandoff` | parentRun、sourceAgent、targetAgent、contextPolicy、status、childRun |
| `AgentHandoffStatus` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED` |
| `AgentHandoffReason` | `CAPABILITY_REQUIRED`、`POLICY_ESCALATION`、`HUMAN_REQUESTED`、`REMOTE_REQUIRED` |
| `AgentHandoffContextPolicy` | 摘要、ContextPack item 类型、敏感级别、最大 token |
| `RemoteAgent` | 远程 Agent 注册记录 |
| `RemoteAgentStatus` | `DISABLED`、`ENABLED`、`UNHEALTHY` |
| `RemoteAgentTrustLevel` | `INTERNAL`、`PARTNER`、`EXTERNAL` |
| `MeshCallDecision` | `ALLOW`、`DENY`、`APPROVAL_REQUIRED`、`CIRCUIT_OPEN` |

### 5.4 Port 与服务

```text
AgentHandoffInboundPort
  create(AgentHandoffCreateCommand command) -> AgentHandoff
  listByRun(String runId) -> List<AgentHandoff>
  cancel(String handoffId) -> AgentHandoff

AgentAsToolPortFactory
  createTool(AgentDefinition targetAgent) -> ToolPort

RemoteAgentRegistryInboundPort
  register(RemoteAgentRegisterCommand command) -> RemoteAgent
  page(RemoteAgentQuery query) -> RemoteAgentPage
  healthCheck(String remoteAgentId) -> RemoteAgentHealth

A2AClientPort
  fetchAgentCard(RemoteAgent remoteAgent)
  createTask(A2ATaskCreateRequest request) -> A2ATaskHandle
  streamEvents(A2ATaskHandle handle) -> A2AEventStream

MeshPolicyPort
  decide(MeshPolicyRequest request) -> MeshPolicyDecision
```

应用服务：

| 服务 | 职责 |
| --- | --- |
| `KernelAgentHandoffService` | 创建 handoff、重校验 context policy、启动 child run |
| `AgentAsToolPort` | 将目标 Agent 包装成 ToolPort，仍走 Tool Gateway |
| `KernelRemoteAgentRegistryService` | 注册远程 agent card，默认 disabled |
| `KernelA2AClientService` | 远程 task 调用并写回本地 step |
| `MeshGovernanceService` | 深度限制、quota、circuit breaker、trace 串联 |

### 5.5 数据表

```sql
CREATE TABLE sa_agent_handoff (
  handoff_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  parent_run_id VARCHAR(64) NOT NULL,
  child_run_id VARCHAR(64),
  source_agent_id VARCHAR(64) NOT NULL,
  target_agent_id VARCHAR(64) NOT NULL,
  handoff_reason VARCHAR(64) NOT NULL,
  input_json TEXT NOT NULL,
  context_policy_json TEXT NOT NULL,
  required_capabilities_json TEXT,
  status VARCHAR(32) NOT NULL,
  failure_reason VARCHAR(1000),
  created_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);

CREATE TABLE sa_remote_agent (
  remote_agent_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  name VARCHAR(128) NOT NULL,
  endpoint_url VARCHAR(512) NOT NULL,
  agent_card_json TEXT NOT NULL,
  trust_level VARCHAR(32) NOT NULL,
  auth_policy_id VARCHAR(64),
  status VARCHAR(32) NOT NULL,
  created_by VARCHAR(64) NOT NULL,
  created_at TIMESTAMP NOT NULL,
  updated_at TIMESTAMP NOT NULL
);
```

### 5.6 执行顺序

1. Local Agent-as-Tool
   目标 Agent 作为 `ToolPort` 暴露，ToolCatalogEntry 的 provider 使用具名枚举，调用时创建 child run，parent step 写入 childRunId。

2. Handoff Repository 与 API
   写 `sa_agent_handoff`，支持 `/api/agent-handoffs` 和 `/api/agent-runs/{runId}/handoffs`。

3. Context 传递策略
   使用 `ContextPack` 重新裁剪：默认只传摘要，不传 private memory 和 secret item，目标 Agent 权限重新校验。

4. Remote Agent Registry
   注册 Agent Card，默认 disabled；health-check 不触发业务 task。

5. A2A Client
   只做出站调用；远程失败写本地 failed step；parent/child trace 串联。

6. A2A Server
   Seahorse Agent 对外暴露 Agent Card；外部身份映射到 tenant policy。

7. Mesh Governance
   深度限制、quota、下游失败熔断、版本兼容检查、SRE 指标。

### 5.7 API

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/agent-handoffs` | 创建本地 handoff |
| `GET` | `/api/agent-runs/{runId}/handoffs` | 查询 run 下 handoff |
| `POST` | `/api/remote-agents` | 注册远程 Agent |
| `GET` | `/api/remote-agents` | 远程 Agent 分页 |
| `POST` | `/api/remote-agents/{remoteAgentId}/health-check` | 健康检查 |
| `POST` | `/api/a2a/tasks` | 创建远程 task，首版仅 admin/internal 调用 |

### 5.8 验证

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentHandoffServiceTests,AgentAsToolPortTests,MeshPolicyServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests,JdbcRemoteAgentRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=SeahorseAgentHandoffControllerTests,SeahorseRemoteAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

### 5.9 退出条件

1. 本地 Agent-as-Tool 可创建 child run，且所有调用经过 Tool Gateway。
2. handoff 有持久记录、parent/child trace、失败状态。
3. Context 传递默认最小化并重新执行权限检查。
4. 远程 Agent 默认 disabled，启用前需管理员配置 trust/auth policy。
5. 多 Agent 调用有深度限制、quota、熔断和 audit。

### 5.10 首个详细开发方案：本地 Agent-as-Tool 与 Handoff 记录

Phase 7 的首个落地切片只做本地协作：把已发布的目标 Agent 包装成一个受 Tool Gateway 管理的工具，调用时创建 child run，并持久化 parent run 到 child run 的 handoff 记录。该切片不做远程 A2A、不做 supervisor 规划器、不做多 Agent 自由对话。

#### 5.10.1 方案基线

| 依据 | 结论 |
| --- | --- |
| Phase 7 原始文档 | Multi-Agent 必须可授权、可观测、可限流、可熔断、可审计。 |
| 差距分析 | 当前不能把 Multi-Agent 作为卖点；应先做受治理的本地 handoff。 |
| 架构基线 | 所有 Agent 调用必须挂到 run、tool、policy、context、audit 协议上。 |
| 当前实现状态 | Agent Definition/Version/Run、Tool Gateway、ContextPack、Approval 已有基础，可组合 child run。 |

#### 5.10.2 模块归属与文件边界

| 层 | 新增或修改位置 | 职责边界 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/mesh/AgentHandoff.java` 及 enum | handoff 不变量、状态迁移、深度字段、失败原因。 |
| Domain | `AgentHandoffContextPolicy` | 上下文传递预算、允许 source type、敏感级别上限。 |
| Inbound Port | `AgentHandoffInboundPort` | 创建 handoff、按 run 查询、取消。 |
| Outbound Port | `AgentHandoffRepositoryPort` | 保存 handoff、更新 childRun/status、按 parentRun 查询。 |
| Application | `KernelAgentHandoffService` | 权限校验、context policy 裁剪、child run 创建编排。 |
| Application | `AgentAsToolPort` | 实现 `ToolPort`，内部委托 handoff service，不直接调用目标 Agent internals。 |
| JDBC | `JdbcAgentHandoffRepositoryAdapter` | 持久化 `sa_agent_handoff`。 |
| Web | `SeahorseAgentHandoffController` | 管理 API；首版用于调试和运营查询。 |
| Starter | kernel/tool auto-configuration | 按配置注册 Agent-as-Tool provider，默认关闭远程能力。 |

#### 5.10.3 领域规则

1. `AgentHandoffStatus` 只允许 `CREATED` -> `RUNNING` -> `SUCCEEDED`/`FAILED`/`CANCELLED`。
2. 每个 handoff 必须有 `parentRunId`、`sourceAgentId`、`targetAgentId`、`handoffReason`、`contextPolicy`。
3. handoff 深度从 parent run metadata 或 handoff chain 计算，默认最大深度为具名常量 `MAX_LOCAL_HANDOFF_DEPTH`。
4. 目标 Agent 必须是 enabled 且有 published version；draft Agent 不可作为工具。
5. Agent-as-Tool 的 ToolCatalogEntry 必须标记 provider 为 `AGENT` 或等价 enum，不能伪装成 internal tool。
6. 创建 child run 前必须调用 `MeshPolicyPort` 或首版 `DefaultMeshPolicyPort`，默认同 tenant、enabled target、未超深度才 allow。
7. Context 传递默认只允许 summary 和非 secret ContextPack item；private memory 只有目标 Agent 通过 `ResourceAccessPolicyPort` 后才可进入 child run。
8. child run 失败必须回写 handoff status 和 parent step observation，不吞掉失败。

#### 5.10.4 API 合约

`POST /api/agent-handoffs`

```json
{
  "tenantId": "default",
  "parentRunId": "run_parent",
  "sourceAgentId": "agent_source",
  "targetAgentId": "agent_target",
  "handoffReason": "CAPABILITY_REQUIRED",
  "inputJson": "{\"task\":\"summarize approved context\"}",
  "contextPolicy": {
    "maxTokens": 1200,
    "allowedSourceTypes": ["RAG_CHUNK", "TOOL_RESULT"],
    "maxSensitivity": "INTERNAL"
  }
}
```

响应：

```json
{
  "handoffId": "handoff_123",
  "status": "RUNNING",
  "parentRunId": "run_parent",
  "childRunId": "run_child",
  "targetAgentId": "agent_target"
}
```

`GET /api/agent-runs/{runId}/handoffs` 返回 parent run 下所有 handoff，包含 child run 状态、失败原因和创建时间。

#### 5.10.5 TDD 开发顺序

1. 写 `AgentHandoffTests`：状态迁移、必填字段、深度限制、取消幂等。
2. 写 `DefaultMeshPolicyPortTests`：不同 tenant 拒绝、目标未发布拒绝、超深度拒绝、同 tenant enabled 允许。
3. 实现 domain 与 default mesh policy。
4. 写 `KernelAgentHandoffServiceTests`：创建 handoff 时裁剪 context、创建 child run、保存 handoff、失败回写。
5. 实现 handoff service，组合 `AgentRunInboundPort`、`ResourceAccessPolicyPort`、`AgentHandoffRepositoryPort`。
6. 写 `AgentAsToolPortTests`：工具调用创建 handoff，不绕过 Tool Gateway 的审计和 policy 外壳。
7. 实现 Agent-as-Tool adapter。
8. 写 `JdbcAgentHandoffRepositoryAdapterTests`：save、markRunning、markSucceeded、markFailed、listByRun。
9. 实现 JDBC 表与 adapter。
10. 写 `SeahorseAgentHandoffControllerTests`：创建、查询、取消。
11. 实现 Web 与 starter 装配，默认不注册任何远程 Agent。

#### 5.10.6 验收证据

1. Agent A 可把 Agent B 作为工具调用，并产生 child run。
2. parent step、handoff、child run 三者可通过 ID 串联。
3. Context 传递不包含 secret item，且目标 Agent 权限会重新校验。
4. 超过最大 handoff 深度时返回 policy deny，不创建 child run。
5. 目标 Agent 失败时 handoff 标记 `FAILED`，parent run 可看到失败 observation。

#### 5.10.7 回滚边界

1. 关闭 Agent-as-Tool provider 后，不影响普通 ToolGateway 和 AgentRun。
2. `sa_agent_handoff` 只追加记录，不改变 run/step 主表语义。
3. 已创建 child run 是普通 AgentRun，可按现有 run API 查询、取消。
4. 远程 A2A 表和 API 不在该切片创建，避免半成品远程协作入口。

## 6. Phase 8 剩余方案：Production Hardening

### 6.1 目标

把 AI Infra 从功能闭环推进到企业试点准入：通用 Evaluation、Audit Ledger、成本与配额、SRE 健康聚合、发布门禁、红队和 canary 必须进入可查询、可回滚、可度量的生产面。

### 6.2 非目标

- 不替换已有 retrieval evaluation；通用 eval 以组合方式复用 retrieval eval。
- 不在首版做复杂 FinOps 计费系统。
- 不把 audit event 当业务事实主表；audit 是追加式事实日志。
- 不让 canary 修改 `AgentVersion`；canary 只改变流量策略。

### 6.3 方案 8A：Audit Ledger

新增领域对象：

| 对象 | 职责 |
| --- | --- |
| `AuditEvent` | 追加式审计事件 |
| `AuditEventType` | `AGENT_PUBLISHED`、`RUN_STARTED`、`MODEL_CALLED`、`TOOL_POLICY_DECIDED`、`TOOL_INVOKED`、`APPROVAL_DECIDED`、`CONTEXT_ACCESSED`、`SECRET_USED`、`REMOTE_AGENT_CALLED` |
| `AuditActorType` | `USER`、`AGENT`、`SYSTEM`、`REMOTE_AGENT` |
| `AuditRedactionPolicy` | 统一过滤 secret/token/password 字段 |

Port：

```text
AuditLedgerPort
  append(AuditEvent event)

AuditQueryInboundPort
  page(AuditEventQuery query) -> AuditEventPage

AuditEventRepositoryPort
  append(AuditEvent event)
  page(AuditEventQuery query)
```

表：

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

首批接入点：

1. Agent publish。
2. Run start/finish。
3. Tool policy decision 和 invocation。
4. Approval decision。
5. ContextPack access。
6. Secret used。

### 6.4 方案 8B：通用 Evaluation 平台

当前已有 retrieval evaluation、memory recall evaluation。通用 eval 不重写这些能力，而是新增统一 dataset/run/result 抽象并通过 adapter 执行不同类型评估。

新增对象：

| 对象 | 职责 |
| --- | --- |
| `EvalDataset` | 通用评估集 |
| `EvalCase` | 输入、期望、标签、敏感级别 |
| `EvalRun` | 一次评估执行 |
| `EvalResult` | 单 case 结果 |
| `EvalType` | `RAG`、`AGENT_TRAJECTORY`、`TOOL`、`SAFETY`、`HITL`、`COST` |
| `EvalRunStatus` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED` |
| `EvalGateDecision` | `PASSED`、`FAILED`、`WAIVED` |

Port：

```text
EvaluationInboundPort
  createDataset(EvalDatasetCommand command) -> EvalDataset
  run(EvalRunCommand command) -> EvalRun
  getRun(String evalRunId) -> EvalRun

EvalRunnerPort
  supports(EvalType type)
  run(EvalRunContext context) -> EvalRunReport
```

首版 Runner：

1. `RetrievalEvalRunnerAdapter` 复用已有 `RetrievalEvaluationInboundPort`。
2. `SafetyEvalRunner` 做 prompt injection、越权访问、secret 泄露的离线 case。
3. `AgentTrajectoryEvalRunner` 先验证工具选择和 step 序列，不评判复杂自然语言质量。

### 6.5 方案 8C：成本与配额

新增对象：

| 对象 | 职责 |
| --- | --- |
| `UsageRecord` | token、cost、tool call、run duration 计量 |
| `QuotaRule` | tenant/agent/user/tool/model/run 级配额 |
| `QuotaScopeType` | `TENANT`、`AGENT`、`USER`、`TOOL`、`MODEL`、`RUN` |
| `QuotaMetric` | `RUNS`、`TOKENS`、`COST`、`TOOL_CALLS`、`DURATION` |
| `QuotaDecision` | `ALLOW`、`DENY`、`REQUIRE_APPROVAL`、`DOWNGRADE_MODEL` |

Port：

```text
UsageMeterPort
  record(UsageRecord record)
  query(UsageQuery query) -> UsageSummary

QuotaPolicyPort
  decide(QuotaRequest request) -> QuotaDecision

CostCalculatorPort
  estimate(ModelCostRequest request) -> CostEstimate
```

首版接入：

1. model call 后记录 token/cost。
2. tool invocation 后记录 tool call。
3. run start 前检查 tenant/agent/user run quota。
4. Tool Gateway 前检查 tool calls/minute；复用 `RateLimiterPort` 做短窗口限流。

### 6.6 方案 8D：SRE Health 与发布门禁

SRE 聚合服务：

| 指标 | 数据来源 |
| --- | --- |
| run backlog | `AgentRunRepositoryPort` |
| approval backlog | `ApprovalRequestQueryPort` |
| worker lease health | `AgentRunLeaseRepositoryPort` |
| model latency/error | ObservationPort/Micrometer |
| tool latency/error | ToolInvocationAudit |
| MCP health | MCP adapter health |
| eval pass rate | EvalRun |
| policy deny top list | ToolPolicy/Audit |
| cost burn rate | UsageMeter |

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/audit-events` | 审计查询 |
| `POST` | `/api/eval-runs` | 启动 eval |
| `GET` | `/api/eval-runs/{evalRunId}` | eval run 详情 |
| `GET` | `/api/cost/usage` | 使用量查询 |
| `PUT` | `/api/quotas/{scopeType}/{scopeId}` | 配额配置 |
| `GET` | `/api/sre/health` | SRE 健康聚合 |

发布门禁：

```text
Agent publish request
  -> config validation
  -> policy validation
  -> eval gate
  -> safety gate
  -> owner approval
  -> canary traffic policy
  -> full publish
```

Canary 对象：

| 对象 | 职责 |
| --- | --- |
| `AgentReleasePolicy` | full/canary 策略 |
| `AgentCanaryStatus` | `CREATED`、`RUNNING`、`PAUSED`、`PROMOTED`、`ROLLED_BACK` |
| `AgentTrafficRule` | versionId 到流量比例 |

### 6.7 任务切片

1. Audit Ledger foundation
   先落 append/query、JDBC、Web API、redaction。

2. Audit 接入关键事件
   publish/run/tool/approval/context/secret 六类事件接入。

3. Usage 与 Quota
   UsageRecord、QuotaRule、QuotaPolicyPort、run/tool/model 接入。

4. 通用 Eval foundation
   EvalDataset/EvalRun/EvalResult 表和 API；retrieval eval adapter 作为首个 runner。

5. Safety 与 trajectory eval
   增加安全和轨迹 runner；用于 Agent publish gate。

6. SRE health API
   聚合已有 FeatureHealthAggregator、run、approval、worker、tool、eval、quota。

7. Canary release
   版本流量策略，不修改 `AgentVersion`；canary 失败自动暂停扩量。

### 6.8 验证

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAuditLedgerServiceTests,AuditRedactionPolicyTests,KernelQuotaPolicyServiceTests,KernelEvaluationServiceTests,KernelSreHealthServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAuditEventRepositoryAdapterTests,JdbcQuotaRepositoryAdapterTests,JdbcEvalRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-tests -am '-Dtest=SeahorseProductionHardeningControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
cd frontend; npm run build
```

### 6.9 退出条件

1. 关键动作都有 append-only audit event，且不包含明文 secret/token。
2. Eval 结果可以作为 publish gate 输入。
3. Quota 超限能拒绝 run、拒绝 tool call 或进入 approval。
4. SRE API 能回答 backlog、health、error、cost、eval 状态。
5. Canary 可暂停、回滚、提升，不破坏 version 不可变语义。

### 6.10 首个详细开发方案：Audit Ledger foundation

Phase 8 的首个落地切片建议先做 Audit Ledger foundation。原因是后续 Eval、Quota、SRE、Canary、Agent Mesh 都需要同一条可查询的证据链；如果继续只依赖 RAG Trace、ToolInvocationAudit 或普通日志，多 Agent 和生产准入会出现多个事实来源。

#### 6.10.1 方案基线

| 依据 | 结论 |
| --- | --- |
| Phase 8 原始文档 | 关键动作必须有 audit event，可按 run/resource 查询。 |
| 架构基线 | Audit Ledger 是关键事件 append-only 与审计查询 owner，不等同普通调试日志。 |
| 差距分析 | 当前 Trace 不足以支撑审计级证据链，缺少不可变审计账本。 |
| 当前实现状态 | Tool audit、AccessDecision log、Approval、Run Store 已有局部证据，可先用 adapter 追加统一事件。 |

#### 6.10.2 模块归属与文件边界

| 层 | 新增或修改位置 | 职责边界 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/audit/AuditEvent.java` 及 enum | 事件类型、actor、resource、run/agent 关联和 redaction 后 payload 不变量。 |
| Inbound Port | `AuditQueryInboundPort` | 审计分页查询，不承载写入。 |
| Outbound Port | `AuditLedgerPort` | 追加审计事件的抽象入口。 |
| Outbound Port | `AuditEventRepositoryPort` | append-only 持久化与查询契约。 |
| Application | `KernelAuditLedgerService` | redaction、append、查询编排。 |
| Application | `AuditRedactionPolicy` | 统一脱敏 secret/token/password/apiKey 字段。 |
| JDBC | `JdbcAuditEventRepositoryAdapter` | 写 `sa_audit_event` 和分页查询。 |
| Web | `SeahorseAuditEventController` | `GET /api/audit-events` 查询。 |
| Starter | repository 与 kernel auto-configuration | 装配 audit repository、ledger service、noop fallback。 |

#### 6.10.3 领域规则

1. `AuditEventType` 首批只开放：`AGENT_PUBLISHED`、`RUN_STARTED`、`RUN_FINISHED`、`TOOL_POLICY_DECIDED`、`TOOL_INVOKED`、`APPROVAL_DECIDED`、`CONTEXT_ACCESSED`、`SECRET_USED`、`REMOTE_AGENT_CALLED`。
2. `AuditActorType` 只允许 `USER`、`AGENT`、`SYSTEM`、`REMOTE_AGENT`。
3. Audit event 是 append-only；repository 不提供 update/delete。
4. `eventJson` 必须经过 `AuditRedactionPolicy`，字段名命中 secret/token/password/apiKey/authorization 时替换为固定 redaction marker。
5. 事件必须包含 `tenantId`、`eventType`、`actorType`、`actorId`、`createdAt`。
6. 有 run 上下文时必须填 `runId`；有 agent 上下文时必须填 `agentId`；有资源上下文时必须填 `resourceType/resourceId`。
7. 查询默认按 `createdAt DESC`，分页 size 使用具名上限，避免无限导出。
8. `AuditLedgerPort.noop()` 只允许测试或关闭审计时作为 fallback；生产 profile 默认启用 JDBC adapter。

#### 6.10.4 首批接入点

| 接入点 | 事件类型 | payload 摘要 |
| --- | --- | --- |
| Agent publish 成功 | `AGENT_PUBLISHED` | agentId、versionId、operator、changeSummary hash |
| Run 创建 | `RUN_STARTED` | runId、agentId、versionId、triggerType |
| Run 终态 | `RUN_FINISHED` | runId、status、duration、failureCode |
| Tool policy 决策 | `TOOL_POLICY_DECIDED` | toolId、effect、riskLevel、reasonCode |
| Tool 调用完成 | `TOOL_INVOKED` | toolId、status、latency、resource refs 摘要 |
| Approval 决策 | `APPROVAL_DECIDED` | approvalId、decision、operator |
| ContextPack 构建 | `CONTEXT_ACCESSED` | contextPackId、itemCount、deniedCount |
| Secret 解析 | `SECRET_USED` | secretRef、credentialType、toolId，不记录 material |

首批接入优先选择已经有领域服务的边界，不直接在 JDBC adapter 里旁路写审计，避免 repository 变成业务 owner。

#### 6.10.5 API 合约

`GET /api/audit-events?tenantId=default&runId=run_123&eventType=TOOL_INVOKED&page=0&size=50`

响应：

```json
{
  "items": [
    {
      "auditId": "audit_123",
      "tenantId": "default",
      "eventType": "TOOL_INVOKED",
      "actorType": "AGENT",
      "actorId": "agent_123",
      "runId": "run_123",
      "agentId": "agent_123",
      "resourceType": "TOOL",
      "resourceId": "tool_search",
      "eventJson": "{\"status\":\"SUCCEEDED\",\"latencyMs\":42}",
      "createdAt": "2026-05-25T10:00:00Z"
    }
  ],
  "page": 0,
  "size": 50,
  "totalElements": 1
}
```

首版只提供查询 API，不提供 delete/update/export API。

#### 6.10.6 TDD 开发顺序

1. 写 `AuditEventTests`：必填字段、event type enum、actor enum、payload 不能为空。
2. 写 `AuditRedactionPolicyTests`：secret/token/password/apiKey/authorization 字段脱敏，普通字段保留。
3. 实现 domain 与 redaction policy。
4. 写 `KernelAuditLedgerServiceTests`：append 时执行 redaction、query 走 repository、noop fallback 不抛异常。
5. 实现 ledger service 与 ports。
6. 写 `JdbcAuditEventRepositoryAdapterTests`：append、按 runId 查询、按 resource 查询、按 eventType 查询、分页排序。
7. 实现表结构和 JDBC adapter。
8. 写 `SeahorseAuditEventControllerTests`：分页查询、按 run/eventType/resource filter。
9. 实现 Web 与 starter 装配。
10. 写首批接入点 focused tests：publish/run/tool/approval/context/secret 至少各一条事件；如果某接入点依赖尚未稳定，则记录为下一切片，不能用空实现冒充。

#### 6.10.7 验收证据

1. `sa_audit_event` 可按 runId 重建一次 run 的关键动作时间线。
2. audit event 中不出现明文 secret、token、authorization header。
3. Tool policy 与 tool invocation 至少各有一条统一审计事件。
4. Approval decision 可在 Audit Ledger 查询到 operator 和 decision。
5. 关闭 audit adapter 时业务流程不中断，但会使用明确的 noop fallback，并在 starter 测试中覆盖。

#### 6.10.8 回滚边界

1. 新表 `sa_audit_event` 只追加，不改变已有 trace、tool audit、access decision 表。
2. 接入点通过 `AuditLedgerPort` 组合，不把审计写入逻辑散落到 repository。
3. 回滚时先关闭 audit auto-configuration 或切换 noop fallback，保留历史 audit 数据。
4. Audit Ledger 首版不作为业务流程强依赖；写审计失败应被记录为运维错误，不应直接让低风险读流程失败。高风险写流程是否 fail-closed 放到后续生产策略切片。

## 7. 推荐执行顺序

1. Phase 5A：MCP OAuth Token Provider。
   在 Secret API 和 static bearer 已有基础上扩展认证，支撑真实企业 MCP。

2. Phase 5C：Sandbox Runtime 外部 adapter。
   在 connector 和 credential 稳定后接入高风险执行环境。

3. Phase 6：Agent Template、from-template、derive、publish validation。
   先 API 后 UI，先低风险模板后高风险模板。

4. Phase 8A：Audit Ledger foundation。
   在 Phase 7 前先补统一审计，避免多 Agent 调用没有统一证据链。

5. Phase 7：本地 Agent-as-Tool 与 Handoff。
   先本地、再远程；先 child run 和 trace，再 A2A。

6. Phase 8B/8C/8D：通用 Eval、Quota、SRE、Canary。
   用于 Phase 6 发布门禁和 Phase 7 mesh 治理的生产准入。

7. Phase 7 远程 A2A client/server 与 mesh governance。
   在 audit、quota、SRE、canary 具备基础后再开放远程协作。

Phase 4 资源 ACL 持久化与变更 API 在当前 worktree 已进入已验证状态，但还没有纳入全量回归，也没有完成 DB check constraint、批量导入和 Audit Ledger 接入。因此它不再作为下一实现顺序的第一项，而是作为后续硬化项进入下方补充方案。
Phase 5B OpenAPI Connector Web API、starter wiring 和 starter 依赖在当前 worktree 已进入已验证状态，不再作为下一实现顺序的第一项；后续只保留全量回归和 Audit Ledger 接入硬化。

## 8. 每阶段共同回滚策略

1. 新表只追加，不改写既有核心表语义。
2. 新 adapter 都通过 `@ConditionalOnMissingBean` 或等价组合方式接入，可通过配置关闭。
3. 新 API 首版不删除旧 API，不改变已发布 `AgentVersion` 结构。
4. 对生产风险高的能力默认 disabled：OpenAPI operation、Sandbox runtime、Remote Agent、Canary。
5. 回滚时先关闭 starter auto-configuration 或 feature flag，再保留表数据用于审计。

## 9. 架构审查清单

每个切片合并前必须逐条检查：

1. Kernel 没有 Spring/JDBC/Web/HTTP runtime 依赖。
2. 新状态、类型、风险、原因全部是 enum 或具名常量。
3. 没有新增大一统 service；port 按能力隔离。
4. 领域对象只维护不变量，应用服务只编排，Repository 不承载业务决策。
5. 新 adapter 可替换且遵守相同状态语义。
6. 没有明文 secret/token 进入 metadata、audit、trace、prompt。
7. 默认行为保守：deny、disabled、dry-run、approval-required。
8. 每个切片有 owner-boundary 测试、JDBC 测试、Web contract 测试或明确的不适用说明。

## 10. 当前 worktree 补充开发方案

本节基于 2026-05-26 当前 worktree 的实际推进状态补充。它用于后续继续开发时替代过时的“Phase 4 未开始、OpenAPI Connector 完全未实现”判断。所有方案仍保持小切片、TDD、ports/adapters、默认保守和可回滚。

### 10.1 Phase 4 补充方案：Resource ACL 生产化硬化

#### 10.1.1 当前状态

当前 worktree 已出现 `ResourceAclRule`、`ResourceAclRepositoryPort`、`KernelResourceAclManagementService`、`AclBackedResourceAccessPolicyPort`、`JdbcResourceAclRepositoryAdapter`、`SeahorseResourceAclController` 和 starter wiring。聚焦验证已覆盖 kernel、JDBC、Web、starter；deny 优先问题也已通过排序规则修复。

Phase 4 下一步不再是“实现资源 ACL”，而是把已实现的 ACL 管理闭环硬化到可进入后续生产化阶段的程度。

#### 10.1.2 目标

1. 给 `sa_resource_acl_rule` 增加可迁移、可审计的 enum 约束和唯一性策略。
2. 增加批量导入/导出设计，但只实现批量导入的 dry-run 校验，不直接落库。
3. 将 ACL 变更事件接入后续 `AuditLedgerPort`，在 Audit Ledger 未完成前保留清晰的接入点。
4. 补齐跨 subject、跨 priority、过期规则和自定义 `ResourceAccessPolicyPort` 的回归验证。

#### 10.1.3 非目标

- 不引入通用 IAM、RBAC/ABAC 策略语言或组织树。
- 不实现 resource type 通配匹配；`ResourceAclRuleScope.RESOURCE_TYPE` 继续保持未开放状态。
- 不让 ACL repository 承载业务决策；有效规则排序仍由领域规则和 SQL 排序共同表达。

#### 10.1.4 文件边界

| 层 | 文件 | 变更 |
| --- | --- | --- |
| Kernel domain | `ResourceAclRule.java`、`ResourceAclRuleConflictPolicy.java` | 保持不变量不变，只补测试覆盖，不扩展规则语言。 |
| Kernel app | `KernelResourceAclManagementService.java` | 增加批量 dry-run command 的编排入口，返回校验报告，不写库。 |
| Inbound port | `ResourceAclManagementInboundPort.java` | 新增 `dryRunImport(ResourceAclImportCommand)`，不影响现有 create/disable/page。 |
| JDBC schema | `agent-registry-run-store-postgresql.sql` | 为 ACL enum 字段补 `CHECK` 约束；增加防重复索引建议。 |
| Web | `SeahorseResourceAclController.java` | 增加 dry-run import endpoint；不开放真实批量写入。 |
| Tests | kernel/JDBC/Web/starter focused tests | 覆盖约束、dry-run、替换 policy、deny wins 全场景。 |

#### 10.1.5 API 草案

`POST /api/resource-acl-rules/import:dry-run`

```json
{
  "tenantId": "default",
  "rules": [
    {
      "resourceType": "DOCUMENT",
      "resourceId": "doc_123",
      "subjectType": "USER",
      "subjectId": "u_1",
      "action": "READ",
      "effect": "ALLOW",
      "priority": 100,
      "expiresAt": "2026-06-30T00:00:00Z"
    }
  ]
}
```

响应：

```json
{
  "validCount": 1,
  "invalidCount": 0,
  "duplicateCount": 0,
  "items": [
    {
      "index": 0,
      "status": "VALID",
      "reasonCode": "VALID_RULE"
    }
  ]
}
```

`status` 和 `reasonCode` 必须使用 enum 或具名常量，例如 `ResourceAclImportItemStatus.VALID`、`ResourceAclImportReasonCode.DUPLICATE_RULE`。

#### 10.1.6 TDD 顺序

1. 写 `ResourceAclImportDryRunTests`：合法规则、重复规则、无效 enum、过期时间早于当前时间、同批重复。
2. 验证 RED。
3. 实现 import command/result enum 和 dry-run 服务方法。
4. 写 `JdbcResourceAclRepositoryAdapterTests`：约束字段落库仍能正常读写；非法 enum 由 Java 映射阻断。
5. 写 Web contract test：dry-run 不写入 `sa_resource_acl_rule`。
6. 写 starter override test：用户自定义 `ResourceAccessPolicyPort` 时不被 ACL-backed policy 覆盖。
7. 跑 Phase 4 聚焦回归和 `git diff --check`。

#### 10.1.7 验收证据

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,ResourceAclImportDryRunTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 10.1.8 回滚边界

新增 dry-run API 可单独关闭；schema 约束只收紧新增表，不影响旧 run/context 表。若约束在现有测试库中暴露历史脏数据，先记录迁移风险，不用实现层绕过 enum 语义。

### 10.2 Phase 5 补充方案：Connector 安全接入闭环

#### 10.2.1 当前状态

当前 worktree 已实现 OpenAPI Connector 的 kernel domain、import service、parser adapter、JDBC repository、Web API、starter wiring 和 starter 依赖，并已通过 kernel、OpenAPI adapter、JDBC、Web、starter 聚焦验证。OAuth Token Provider 和 Sandbox Runtime 仍未实现。

#### 10.2.2 目标

OpenAPI spec 已能从 API 导入、分页、查看 operation、启用 operation 到 ToolCatalog；下一步推进 OAuth 与 Sandbox。Phase 5 的完整安全闭环按以下顺序完成：

1. MCP OAuth client credentials token provider。
2. Sandbox external runtime adapter。
3. OpenAPI Connector 全量回归与 Audit Ledger 接入。
4. Connector execution adapter 只在 credential、policy、audit、sandbox 边界完成后再启动。

#### 10.2.3 非目标

- OpenAPI Connector 收尾阶段不执行真实 HTTP operation。
- OAuth 首版不做浏览器授权码 UI，不做动态客户端注册。
- Sandbox 首版不在主 JVM 运行 shell、python 或浏览器自动化。
- 不把 connector secret 存进 `AgentVersion`、ToolCatalog metadata、trace 或 prompt。

#### 10.2.4 文件边界

| 切片 | 文件 | 变更 |
| --- | --- | --- |
| 5B Web | `SeahorseOpenApiConnectorController.java`、`SeahorseAgentControllerTests.java` | 增加 `/api/connectors/openapi`、分页、operation 列表、enable API。 |
| 5B Starter | `SeahorseAgentRegistryRepositoryAutoConfiguration.java`、`SeahorseAgentKernelRegistryAutoConfiguration.java` | 装配 `ConnectorRepositoryPort` 与 `OpenApiConnectorInboundPort`。 |
| 5B Dependency | root/starter/starter-all POM | 加入 `seahorse-agent-adapter-openapi`，保持 kernel 不依赖 parser。 |
| 5A Kernel | `kernel/domain/agent/credential`、`ports/outbound/agent/OAuthTokenPort.java` | 增加 OAuth token、grant type、cache key、scope challenge 模型。 |
| 5A MCP adapter | `seahorse-agent-adapter-mcp-http` | 组合 `CredentialProviderPort` 和 `OAuthTokenPort`，只向 client 注入 bearer material。 |
| 5C Kernel | `kernel/domain/agent/sandbox`、`SandboxRuntimeInboundPort` | session、execution、artifact、policy domain 和服务。 |
| 5C Adapter | 新增或复用外部 sandbox adapter 模块 | HTTP/gRPC 调用外部沙箱；主 JVM 不执行代码。 |

#### 10.2.5 OpenAPI 收尾 API

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/connectors/openapi` | admin 导入 spec，operation 默认 `DISABLED`。 |
| `GET` | `/api/connectors` | admin 分页查询 connector。 |
| `GET` | `/api/connectors/{connectorId}/operations` | admin 查询 operation 和风险。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/enable` | admin 启用 operation 并写入 ToolCatalog。 |

启用 high risk operation 时必须满足 `requiresApproval=true`，并且 ToolCatalogEntry 仍不可执行真实 HTTP。真实执行 adapter 后续必须重新经过 Tool Gateway、Credential、Policy、Audit。

#### 10.2.6 OAuth 首版规则

1. `OAuthGrantType` 只开放 `CLIENT_CREDENTIALS` 和 `USER_DELEGATED` enum；首个实现只做 `CLIENT_CREDENTIALS`。
2. token cache key 由 tenant、server、clientId、audience、resource、scopeSet 组成。
3. access token 只作为 `CredentialMaterial` 出现；不得进入日志、trace、audit payload、ToolCatalog metadata。
4. 401/403 scope challenge 首版只记录 `OAuthScopeChallenge` 并返回明确失败；自动补 scope 重试放到下一切片。
5. `clientSecretRef` 通过 `SecretStorePort` 解析，不允许配置明文 secret。

#### 10.2.7 Sandbox 首版规则

1. `SandboxNetworkPolicy.DENY_ALL` 是默认值。
2. session 必须绑定 tenant、runId、agentId、userId 和 runtimeType。
3. `SandboxExecutionStatus` 必须按 `CREATED -> RUNNING -> SUCCEEDED/FAILED/TIMED_OUT/CANCELLED` 迁移。
4. artifact 出沙箱前必须有 sensitivity scan 结果；未扫描 artifact 不可返回给 Agent prompt。
5. audit 未完成前先通过 `SandboxExecution` 持久记录保留审计输入，Audit Ledger 完成后再接入 `AuditLedgerPort`。

#### 10.2.8 TDD 顺序

1. 写 `SeahorseOpenApiConnectorControllerTests`：导入、分页、list operations、enable。
2. 验证 RED。
3. 实现 OpenAPI controller。
4. 写 starter auto-configuration tests：`ConnectorRepositoryPort`、`OpenApiConnectorInboundPort`、parser adapter 可用。
5. 实现 repository/kernel/parser adapter wiring 和 starter POM dependency。
6. 跑 kernel、OpenAPI adapter、JDBC、Web、starter 聚焦回归。
7. 写 OAuth kernel tests：token request 校验、cache key、material 不暴露 token。
8. 实现 OAuth domain/ports/provider，不接真实 MCP 自动重试。
9. 写 MCP adapter tests：OAuth material 注入、token 不进入摘要、missing secretRef fail-closed。
10. 写 Sandbox kernel tests：policy deny network、session 状态、execution 超时、artifact 未扫描拒绝。

#### 10.2.9 验收证据

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ConnectorOperationRiskMapperTests,KernelOpenApiConnectorImportServiceTests,OAuthCredentialProviderTests,KernelSandboxRuntimeServiceTests,DefaultSandboxPolicyPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-openapi -am '-Dtest=OpenApiSpecParserAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcConnectorRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 10.2.10 回滚边界

OpenAPI import 表和 ToolCatalogEntry 都是追加或启用状态变更；关闭 controller 和 auto-configuration 即可停止入口。OAuth/Sandbox 均通过 port 接入，失败时默认 fail-closed，不回退到无凭据或本地执行。

### 10.3 Phase 6 补充方案：Template 到发布校验最小闭环

#### 10.3.1 当前状态

Agent Definition、Version、Run 已有主闭环，但 Agent Factory/Studio 未开始。下一步不应先做完整 UI，而应先把模板、from-template draft、derive 和 publish validation 固化为可测试 API。

#### 10.3.2 目标

业务团队能从内置模板创建 `DRAFT` Agent，管理员能派生一个权限更窄的 Agent，并在发布前得到结构化校验报告。发布校验先只返回报告，不阻断现有 publish；真正 publish gate 在 Phase 8 Eval/Quota/Audit 接入后再启用。

#### 10.3.3 非目标

- 不实现完整 Agent Studio 页面。
- 不引入工作流引擎或图形化 DAG。
- 不允许派生扩大模板工具、memory、quota、approval 边界。
- 不修改已发布 `AgentVersion` 的不可变语义。

#### 10.3.4 文件边界

| 层 | 文件 | 职责 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/factory/AgentTemplate.java` | 模板 ID、默认风险、默认工具、派生边界不变量。 |
| Domain | `AgentPublishCheck.java`、`AgentPublishCheckStatus.java`、`AgentPublishCheckItem.java` | 发布校验结果快照。 |
| Inbound port | `AgentFactoryInboundPort.java` | `listTemplates`、`createFromTemplate`、`derive`、`validatePublish`。 |
| Outbound port | `AgentTemplateRepositoryPort.java`、`AgentPublishCheckRepositoryPort.java` | 模板读取与校验结果持久化。 |
| Application | `KernelAgentFactoryService.java` | 组合 AgentDefinition service、ToolCatalog、Policy、ACL、Quota/Eval optional ports。 |
| JDBC | `JdbcAgentTemplateRepositoryAdapter.java`、`JdbcAgentPublishCheckRepositoryAdapter.java` | 内置模板读取、校验结果保存。 |
| Web | `SeahorseAgentFactoryController.java` | API，不做 UI。 |

#### 10.3.5 模板与派生规则

1. `AgentTemplateId` 用 enum 或具名常量定义内置模板：`KNOWLEDGE_ASSISTANT`、`WORKFLOW_ASSISTANT`、`TOOL_OPERATOR`、`COMPLIANCE_REVIEWER`。
2. `template.allowedToolIds` 是上限；`createFromTemplate` 请求工具必须是子集。
3. 请求风险等级不能高于模板风险等级。
4. `derive` 必须引用 base agent 和 base version；派生后的 tools、memory scope、quota、approval policy 都只能收窄。
5. `instructionsOverlay` 只能追加业务说明，不能覆盖模板的安全边界段落。
6. 发布校验项使用 enum：`INSTRUCTIONS_PRESENT`、`TOOLS_ENABLED`、`HIGH_RISK_APPROVAL_POLICY`、`ACL_RESOLVABLE`、`OWNER_PRESENT`、`CHANGE_SUMMARY_PRESENT`、`EVAL_PRESENT`、`QUOTA_PRESENT`。
7. `EVAL_PRESENT`、`QUOTA_PRESENT` 在 Phase 8 前可返回 `WARN`，不能伪造通过。

#### 10.3.6 API 草案

| Method | Path | 行为 |
| --- | --- | --- |
| `GET` | `/api/agent-templates` | 查询内置模板。 |
| `POST` | `/api/agents/from-template` | 基于模板创建 draft。 |
| `POST` | `/api/agents/{agentId}/derive` | 从已发布版本派生新 draft。 |
| `POST` | `/api/agents/{agentId}/validate` | 生成发布校验报告。 |

`POST /api/agents/{agentId}/validate` 响应示例：

```json
{
  "checkId": "check_123",
  "agentId": "agent_123",
  "status": "FAILED",
  "items": [
    {
      "code": "HIGH_RISK_APPROVAL_POLICY",
      "severity": "ERROR",
      "message": "High risk tools require approval policy"
    },
    {
      "code": "EVAL_PRESENT",
      "severity": "WARN",
      "message": "Evaluation gate is not configured yet"
    }
  ]
}
```

#### 10.3.7 TDD 顺序

1. 写 `AgentTemplateTests`：工具子集、风险上限、overlay 合并。
2. 写 `KernelAgentFactoryServiceTests`：list templates、from-template 创建 draft、derive 收窄、扩大工具失败、publish validation 报告。
3. 实现 domain、ports、service。
4. 写 JDBC tests：内置模板 seed/read、publish check save/find。
5. 实现 schema 和 adapters。
6. 写 Web contract tests：四个 API。
7. 写 starter tests：repository 和 service 条件装配。

#### 10.3.8 验收证据

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentTemplateTests,KernelAgentFactoryServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentTemplateRepositoryAdapterTests,JdbcAgentPublishCheckRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

#### 10.3.9 回滚边界

模板表和 publish check 表只追加。关闭 `AgentFactoryInboundPort` auto-configuration 后，不影响已有 Agent Definition/Run。已创建 draft 是普通 AgentDefinition，可通过现有 disable/archive 流程处理。

### 10.4 Phase 7 补充方案：本地 Handoff 先于远程 A2A

#### 10.4.1 当前状态

当前代码仅有 A2A 触发类型和文档性枚举基础，还没有 handoff、remote agent、mesh policy。Phase 7 必须建立在 Phase 1-6 的 run/tool/policy/context 基础上，且建议在 Phase 8A Audit Ledger foundation 之后进入实现。

#### 10.4.2 目标

先实现本地 Agent-as-Tool 与 `AgentHandoff` 持久记录：Agent A 通过 Tool Gateway 调用 Agent B，系统创建 child run，并记录 parent run、child run、handoff status、context policy 和失败原因。远程 A2A 暂不实现。

#### 10.4.3 非目标

- 不实现多个 Agent 自由聊天。
- 不实现远程 Agent Card、A2A server/client。
- 不实现 supervisor 自动规划器。
- 不跨 tenant 传递上下文。

#### 10.4.4 文件边界

| 层 | 文件 | 职责 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/mesh/AgentHandoff.java` | parent/child run、source/target agent、status、depth、failure 不变量。 |
| Domain | `AgentHandoffContextPolicy.java` | 允许传递的 source type、敏感级别上限、token 预算。 |
| Inbound port | `AgentHandoffInboundPort.java` | create、listByRun、cancel。 |
| Outbound port | `AgentHandoffRepositoryPort.java` | append/update handoff 状态。 |
| Outbound port | `MeshPolicyPort.java` | source->target 授权、深度、tenant、target status 校验。 |
| Application | `KernelAgentHandoffService.java` | 裁剪 context、创建 child run、回写 handoff。 |
| Tool adapter | `AgentAsToolPort.java` | 把已发布 Agent 包装成 ToolPort，仍由 Tool Gateway 调用。 |
| JDBC/Web | `JdbcAgentHandoffRepositoryAdapter.java`、`SeahorseAgentHandoffController.java` | 持久化和运营查询。 |

#### 10.4.5 领域规则

1. `AgentHandoffStatus` 只允许 `CREATED -> RUNNING -> SUCCEEDED/FAILED/CANCELLED`。
2. 最大深度使用具名常量 `MeshPolicyLimits.MAX_LOCAL_HANDOFF_DEPTH`，默认 2。
3. 目标 Agent 必须同 tenant、enabled、且有 published version。
4. Context 默认只传 summary，不传完整 message history。
5. `ContextSensitivity.SECRET` 永不传递；`CONFIDENTIAL` 需要目标 Agent 通过 `ResourceAccessPolicyPort` 重新校验。
6. Agent-as-Tool 的 provider 必须是 `ToolProvider.AGENT` 或新增等价 enum，不能伪装成 internal tool。
7. child run 失败必须标记 handoff `FAILED`，并把 failure code 写入 parent step observation。
8. cancel handoff 幂等；child run 已终态时只返回当前状态。

#### 10.4.6 API 草案

| Method | Path | 行为 |
| --- | --- | --- |
| `POST` | `/api/agent-handoffs` | 创建本地 handoff，返回 child run。 |
| `GET` | `/api/agent-runs/{runId}/handoffs` | 查询 parent run 下的 handoff。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 取消 handoff 和未终态 child run。 |

#### 10.4.7 TDD 顺序

1. 写 `AgentHandoffTests`：状态迁移、必填字段、最大深度、cancel 幂等。
2. 写 `DefaultMeshPolicyPortTests`：跨 tenant 拒绝、目标 draft 拒绝、超深度拒绝、同 tenant published 允许。
3. 写 `KernelAgentHandoffServiceTests`：裁剪 context、创建 child run、保存 handoff、child fail 回写。
4. 实现 domain、policy、service。
5. 写 `AgentAsToolPortTests`：通过 Tool Gateway 外壳触发 handoff，不绕过 policy/audit。
6. 写 JDBC/Web/starter tests。
7. 在 Phase 8A Audit Ledger 完成后补 `REMOTE_AGENT_CALLED` 或 `AGENT_HANDOFF_CREATED` audit 接入测试。

#### 10.4.8 验收证据

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,DefaultMeshPolicyPortTests,KernelAgentHandoffServiceTests,AgentAsToolPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

#### 10.4.9 回滚边界

关闭 Agent-as-Tool provider 后，普通 run、tool、approval 不受影响。`sa_agent_handoff` 只追加协作记录，不改变 `sa_agent_run` 主语义。远程 A2A 表和 API 不在本切片创建。

### 10.5 Phase 8 补充方案：Audit Ledger 到生产准入骨架

#### 10.5.1 当前状态

项目已有 RAG Trace、ToolInvocationAudit、AccessDecision log、Approval、Run Store、Retrieval evaluation、RateLimiter、FeatureHealthAggregator 等局部证据源，但没有统一 Audit Ledger、通用 Eval、Quota、Canary、SRE health API。Phase 8 应先建立统一审计账本，再做 Eval/Quota/SRE。

#### 10.5.2 目标

用 Audit Ledger foundation 统一关键事件查询，并预留生产准入骨架：publish/run/tool/approval/context/secret 事件先接入；Eval、Quota、SRE、Canary 作为后续硬化切片依赖同一审计事实链。

#### 10.5.3 非目标

- Audit Ledger 不替代业务主表。
- 首版 audit 写失败不阻断低风险读流程。
- 不把普通 debug log 全部搬进 audit。
- 不实现完整 FinOps 计费、红队平台或发布 canary UI。

#### 10.5.4 文件边界

| 层 | 文件 | 职责 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/audit/AuditEvent.java` | 事件类型、actor、resource、payload 不变量。 |
| Domain | `AuditRedactionPolicy.java` | secret/token/password/apiKey/authorization 字段脱敏。 |
| Outbound port | `AuditLedgerPort.java` | append-only 审计入口。 |
| Inbound port | `AuditQueryInboundPort.java` | 查询 API 契约。 |
| Repository port | `AuditEventRepositoryPort.java` | append/page，不提供 update/delete。 |
| Application | `KernelAuditLedgerService.java` | redaction、append、query、noop fallback。 |
| JDBC/Web | `JdbcAuditEventRepositoryAdapter.java`、`SeahorseAuditEventController.java` | `sa_audit_event` 和查询 API。 |
| Integration | Agent publish/run/tool/approval/context/secret 服务边界 | 通过 `AuditLedgerPort` 追加事件，不在 repository 旁路写。 |

#### 10.5.5 事件规则

1. `AuditEventType` 首批开放：`AGENT_PUBLISHED`、`RUN_STARTED`、`RUN_FINISHED`、`TOOL_POLICY_DECIDED`、`TOOL_INVOKED`、`APPROVAL_DECIDED`、`CONTEXT_ACCESSED`、`SECRET_USED`。
2. `AuditActorType` 开放：`USER`、`AGENT`、`SYSTEM`、`REMOTE_AGENT`。
3. event 必须包含 tenantId、eventType、actorType、actorId、createdAt。
4. 有 run 上下文必须填 runId；有 agent 上下文必须填 agentId；有资源上下文必须填 resourceType/resourceId。
5. `eventJson` 必须先过 redaction；redaction marker 使用具名常量 `AuditRedactionPolicy.REDACTED_VALUE`。
6. query 默认 `createdAt DESC`，page size 使用具名上限。
7. Noop ledger 只能作为显式 fallback bean；生产 profile 默认 JDBC ledger。

#### 10.5.6 API 草案

| Method | Path | 行为 |
| --- | --- | --- |
| `GET` | `/api/audit-events` | 按 tenant、runId、agentId、eventType、resource 查询。 |
| `GET` | `/api/audit-events/{auditId}` | 查询单条审计事件，后续可选。 |

首版不提供 delete/update/export。导出涉及合规和脱敏策略，留到生产运营切片。

#### 10.5.7 TDD 顺序

1. 写 `AuditEventTests`：必填字段、enum、payload 不能为空。
2. 写 `AuditRedactionPolicyTests`：嵌套 JSON 中 secret/token/password/apiKey/authorization 脱敏。
3. 写 `KernelAuditLedgerServiceTests`：append 先 redaction、query 走 repository、noop fallback 不抛。
4. 实现 domain、ports、service。
5. 写 `JdbcAuditEventRepositoryAdapterTests`：append、按 run/resource/eventType 查询、分页排序。
6. 写 `SeahorseAuditEventControllerTests`：查询参数和分页。
7. 分批写接入点测试：publish、run、tool policy/tool invoked、approval、context、secret 至少各一条。
8. 完成 Phase 8A 后再启动 Eval foundation，不把 Eval/Quota 混入 Audit 首个切片。

#### 10.5.8 验收证据

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AuditEventTests,AuditRedactionPolicyTests,KernelAuditLedgerServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAuditEventControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

#### 10.5.9 后续硬化顺序

1. 通用 Eval foundation：`EvalDataset`、`EvalCase`、`EvalRun`、`EvalResult`，首个 runner 复用 retrieval evaluation。
2. Quota foundation：`UsageRecord`、`QuotaRule`、`QuotaPolicyPort`，先接 run start 和 tool call。
3. SRE health API：聚合 run backlog、approval backlog、worker lease、tool error、MCP health、eval pass、quota usage。
4. Canary release：只改变 traffic policy，不修改 `AgentVersion`。

#### 10.5.10 回滚边界

`sa_audit_event` 只追加。所有写入通过 `AuditLedgerPort`，关闭 auto-configuration 可切回 noop fallback。历史 audit 数据保留，不参与业务主流程回滚。

## 11. 2026-05-26 未完成阶段下一步开发卡

本节是在 2026-05-26 对当前 worktree 状态的再校准。它不是新增阶段，而是把 Phase 4-8 每个仍未完全完成的阶段各收敛成一个可直接进入 TDD 的下一步开发方案。后续实现应优先按本节顺序推进；若与上文旧状态冲突，以本节为准。

### 11.1 Phase 4 开发卡：Resource ACL 生产化硬化

#### 11.1.1 目标

把已实现的 Resource ACL 管理闭环从“功能可用”硬化到“可安全进入 Phase 8 Audit 和后续企业试点”的程度。当前不再新增 ACL 权限语义，而是补齐 dry-run 批量校验、数据库约束、自定义 policy 替换验证和后续 audit 接入点。

#### 11.1.2 文件边界

| 层 | 文件 | 具体改动 |
| --- | --- | --- |
| Kernel domain | `ResourceAclRule.java`、`ResourceAclRuleStatus.java`、`ResourceAclRuleScope.java`、`ResourceAclRuleConflictPolicy.java` | 不改不变量，只补测试覆盖和具名 reason code。 |
| Kernel app | `KernelResourceAclManagementService.java` | 增加 `dryRunImport` 编排；只返回校验报告，不写库。 |
| Inbound port | `ResourceAclManagementInboundPort.java` | 增加 `dryRunImport(ResourceAclImportCommand)`。 |
| Port DTO | `ResourceAclImportCommand`、`ResourceAclImportResult`、`ResourceAclImportItemStatus`、`ResourceAclImportReasonCode` | 用 enum 表达 `VALID`、`INVALID`、`DUPLICATE`、`EXPIRED_RULE`、`UNSUPPORTED_SCOPE` 等状态和原因。 |
| JDBC schema | `agent-registry-run-store-postgresql.sql` | 为 status、scope、subjectType、action、effect 增加 `CHECK` 约束建议；不修改旧表语义。 |
| Web | `SeahorseResourceAclController.java` | 增加 `POST /api/resource-acl-rules/import:dry-run`。 |
| Starter tests | `SeahorseAgentRegistryAutoConfigurationTests.java` | 验证用户自定义 `ResourceAccessPolicyPort` 不被 ACL-backed policy 覆盖。 |

#### 11.1.3 API 草案

`POST /api/resource-acl-rules/import:dry-run`

```json
{
  "tenantId": "default",
  "rules": [
    {
      "resourceType": "DOCUMENT",
      "resourceId": "doc_123",
      "subjectType": "USER",
      "subjectId": "u_1",
      "action": "READ",
      "effect": "ALLOW",
      "priority": 100,
      "expiresAt": "2026-06-30T00:00:00Z"
    }
  ]
}
```

响应：

```json
{
  "validCount": 1,
  "invalidCount": 0,
  "duplicateCount": 0,
  "items": [
    {
      "index": 0,
      "status": "VALID",
      "reasonCode": "VALID_RULE"
    }
  ]
}
```

#### 11.1.4 TDD 顺序

1. 写 `ResourceAclImportDryRunTests`：合法规则、同批重复、已存在重复、过期规则、空 subject、unsupported scope。
2. 跑 RED：确认缺少 command/result/service API。
3. 实现 import command/result enum 和 dry-run 服务方法。
4. 写 Web contract test：dry-run 只返回报告，不写入 `sa_resource_acl_rule`。
5. 写 starter override test：自定义 `ResourceAccessPolicyPort` 时不装配 ACL-backed policy。
6. 写 schema/JDBC 约束回归：合法 enum 正常写入，非法 enum 不通过 Java 映射进入 repository。
7. 跑 Phase 4 聚焦回归和 `git diff --check`。

#### 11.1.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,ResourceAclImportDryRunTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests#shouldExposeResourceAclDryRunImportApi' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 11.1.6 回滚边界

dry-run API 是只读校验入口，可单独关闭 controller 或 starter wiring；schema 约束只作用于新增 ACL 表；不删除历史 ACL 规则，不改变 `ContextPack` 构建语义。

### 11.2 Phase 5 开发卡：MCP OAuth Token Provider 最小闭环

#### 11.2.1 目标

在 Secret API 和 MCP `STATIC_BEARER` 已完成的基础上，增加 MCP `CLIENT_CREDENTIALS` token provider。首版只做 client credentials、token cache 和 bearer material 注入；不做浏览器授权码 UI、动态客户端注册、scope 自动补偿重试或真实 OpenAPI operation 执行。

#### 11.2.2 文件边界

| 层 | 文件 | 具体改动 |
| --- | --- | --- |
| Kernel credential | `CredentialAuthType.java` | 增加 `CLIENT_CREDENTIALS`、`USER_DELEGATED`，并用具名能力区分是否需要 `clientSecretRef`。 |
| Kernel credential | `CredentialRequest.java` | 从单一 `secretRef` 扩展为 OAuth 所需字段：tenantId、serverId、clientId、clientSecretRef、scopeSet、audience、resource。保留 `none()`、`staticBearer()` 工厂方法。 |
| Kernel credential | `CredentialMaterial.java` | 增加 `clientCredentialsBearer(...)` 工厂；token 仍通过 `SecretValue` 包裹，`toString()` 不泄露原文。 |
| Kernel OAuth port | `OAuthTokenPort.java`、`OAuthTokenCachePort.java` | 新增小接口；token 获取和缓存分离，保持 ISP。 |
| Kernel OAuth model | `OAuthGrantType`、`OAuthTokenRequest`、`OAuthToken`、`OAuthTokenCacheKey`、`OAuthTokenType`、`OAuthScopeChallenge` | 领域对象维护必填、过期、scope 归一化和 cache key 不变量。 |
| Kernel provider | `OAuthCredentialProvider.java` | 组合 `SecretStorePort`、`OAuthTokenPort`、`OAuthTokenCachePort`，返回最终 `CredentialMaterial`。 |
| MCP adapter | `McpHttpAdapterProperties.Server` | 增加 `authorizationServerMetadataUrl`、`protectedResourceMetadataUrl`、`clientId`、`scopes`、`audience`、`resource`，不允许明文 secret。 |
| MCP auto-config | `McpHttpAutoConfiguration.java` | 根据 `CredentialAuthType.CLIENT_CREDENTIALS` 构造 `CredentialRequest`；缺 secretRef/clientId 时 fail-closed。 |
| MCP client | `StreamableHttpMcpClient.java` | 只认最终 bearer material，不感知 OAuth 细节。 |
| Starter | `SeahorseAgentCredentialAutoConfiguration.java` | 条件装配默认 OAuth credential provider；自定义 `CredentialProviderPort` 时退让。 |

#### 11.2.3 领域规则

1. `OAuthGrantType.CLIENT_CREDENTIALS` 是首个可执行 grant；`USER_DELEGATED` 只保留 enum 和 fail-closed 语义。
2. `OAuthTokenCacheKey` 必须由 tenantId、serverId、clientId、audience、resource、scopeSet 组成；scopeSet 排序归一化。
3. token cache 写入 TTL 必须小于 token 剩余有效期，并预留具名安全窗口 `OAuthTokenTtlPolicy.EXPIRY_SKEW`。
4. token、client secret、authorization header 不进入日志、trace、audit payload、ToolCatalog metadata 或异常消息。
5. `clientSecretRef` 必须经 `SecretStorePort` 解析；配置中不接受明文 secret。
6. 401/403 scope challenge 首版只解析为 `OAuthScopeChallenge` 并返回失败原因；自动补 scope 重试进入后续切片。

#### 11.2.4 TDD 顺序

1. 写 `OAuthTokenRequestTests`：必填字段、scope 归一化、`USER_DELEGATED` 首版不可执行。
2. 写 `OAuthTokenCacheKeyTests`：同 scope 不同顺序生成同一 key，不同 audience/resource 生成不同 key。
3. 写 `OAuthCredentialProviderTests`：cache miss 获取 token、cache hit 不重复请求、missing secretRef fail-closed、material/toString 不泄露 token。
4. 跑 kernel RED。
5. 实现 OAuth model、ports、provider。
6. 写 `McpHttpOAuthCredentialTests`：server 配置转 `CredentialRequest`、OAuth material 注入 header、缺 clientId/clientSecretRef 不发现远程工具。
7. 写 starter test：默认 OAuth provider 条件装配，自定义 `CredentialProviderPort` 时退让。
8. 跑 Phase 5A 聚焦回归和 `git diff --check`。

#### 11.2.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=CredentialMaterialTests,OAuthTokenRequestTests,OAuthTokenCacheKeyTests,OAuthCredentialProviderTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-mcp-http -am '-Dtest=StreamableHttpMcpClientCredentialTests,McpHttpAutoConfigurationCredentialTests,McpHttpOAuthCredentialTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentCredentialAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 11.2.6 回滚边界

关闭 OAuth provider wiring 后，`NONE` 和 `STATIC_BEARER` 继续工作；OAuth cache 是派生数据，可清空；MCP client 构造函数保持兼容，不把 OAuth 细节扩散到 adapter 外部。

### 11.3 Phase 6 开发卡：模板只读与 from-template draft

#### 11.3.1 目标

先建立 Agent Factory 的最小 API 闭环：业务用户可查询内置模板，管理员可基于模板创建 `DRAFT` AgentDefinition。首版不做完整 Studio UI、不做发布阻断、不做 rollback UI；发布校验只返回结构化报告。

#### 11.3.2 文件边界

| 层 | 文件 | 具体改动 |
| --- | --- | --- |
| Domain | `AgentTemplate.java`、`AgentTemplateId.java`、`AgentTemplateRiskProfile.java` | 内置模板、工具上限、风险上限和说明边界。 |
| Domain | `AgentPublishCheck.java`、`AgentPublishCheckItem.java`、`AgentPublishCheckCode.java`、`AgentPublishCheckStatus.java` | 发布校验报告快照；首版只 report，不阻断 publish。 |
| Inbound port | `AgentFactoryInboundPort.java` | `listTemplates()`、`createFromTemplate(command)`、`validatePublish(command)`。 |
| Outbound port | `AgentTemplateRepositoryPort.java`、`AgentPublishCheckRepositoryPort.java` | 模板读取和校验报告持久化。 |
| Application | `KernelAgentFactoryService.java` | 组合现有 AgentDefinition 创建能力，不复制 Definition 规则。 |
| JDBC | `JdbcAgentTemplateRepositoryAdapter.java`、`JdbcAgentPublishCheckRepositoryAdapter.java` | 读取 seed 模板、保存 check。 |
| Web | `SeahorseAgentFactoryController.java` | `GET /api/agent-templates`、`POST /api/agents/from-template`、`POST /api/agents/{agentId}/validate`。 |
| Starter | registry/kernel auto-config | 条件装配 repository 和 factory service。 |

#### 11.3.3 领域规则

1. 内置模板 ID 使用 enum：`KNOWLEDGE_ASSISTANT`、`WORKFLOW_ASSISTANT`、`TOOL_OPERATOR`、`COMPLIANCE_REVIEWER`。
2. 请求工具必须是模板 `allowedToolIds` 子集；不能通过 from-template 扩大工具边界。
3. 请求风险等级不能高于模板风险等级。
4. `instructionsOverlay` 只能追加业务说明，不能替换模板安全边界。
5. from-template 只创建 `DRAFT` AgentDefinition，不直接发布版本。
6. 发布校验项使用 enum；Phase 8 前 `EVAL_PRESENT`、`QUOTA_PRESENT` 只能返回 `WARN`。

#### 11.3.4 TDD 顺序

1. 写 `AgentTemplateTests`：工具子集、风险上限、overlay 合并、安全边界不可覆盖。
2. 写 `KernelAgentFactoryServiceTests`：list templates、from-template 创建 draft、工具越界失败、风险越界失败、validate publish 返回报告。
3. 跑 kernel RED。
4. 实现 domain、ports、service。
5. 写 JDBC tests：seed 模板读取、publish check save/page。
6. 写 Web tests：三个 API 的请求/响应。
7. 写 starter tests：repository/service 条件装配。
8. 跑 Phase 6 聚焦回归。

#### 11.3.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentTemplateTests,KernelAgentFactoryServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentTemplateRepositoryAdapterTests,JdbcAgentPublishCheckRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 11.3.6 回滚边界

模板和 publish check 表只追加；关闭 factory controller 和 auto-configuration 后，现有 Agent Definition、Version、Run 不受影响；已创建 draft 作为普通 draft 保留。

### 11.4 Phase 7 开发卡：本地 Agent-as-Tool 与 Handoff 记录

#### 11.4.1 目标

先实现本地 Agent 协作，不开放远程 A2A。目标是把已发布目标 Agent 包装为一个受 Tool Gateway 管理的工具；调用时创建 child run，并持久化 parent run 到 child run 的 `AgentHandoff` 记录。

#### 11.4.2 文件边界

| 层 | 文件 | 具体改动 |
| --- | --- | --- |
| Domain | `AgentHandoff.java`、`AgentHandoffStatus.java`、`AgentHandoffFailureCode.java` | handoff 状态、不变量和失败原因。 |
| Domain | `AgentHandoffContextPolicy.java`、`MeshPolicyLimits.java` | 上下文裁剪、敏感级别、最大深度具名常量。 |
| Inbound port | `AgentHandoffInboundPort.java` | `create`、`listByRun`、`cancel`。 |
| Outbound port | `AgentHandoffRepositoryPort.java`、`MeshPolicyPort.java` | 持久化和 mesh policy 判定。 |
| Application | `KernelAgentHandoffService.java` | 校验目标 Agent、裁剪 context、创建 child run、更新 handoff。 |
| Tool adapter | `AgentAsToolPort.java` | 以 `ToolProvider.AGENT` 暴露目标 Agent，仍走 Tool Gateway。 |
| JDBC | `JdbcAgentHandoffRepositoryAdapter.java` | `sa_agent_handoff` append/update。 |
| Web | `SeahorseAgentHandoffController.java` | 查询和取消 handoff；创建可先只走 tool path。 |

#### 11.4.3 领域规则

1. `AgentHandoffStatus` 只允许 `CREATED -> RUNNING -> SUCCEEDED/FAILED/CANCELLED`。
2. 最大本地 handoff 深度用 `MeshPolicyLimits.MAX_LOCAL_HANDOFF_DEPTH`，默认 2。
3. 目标 Agent 必须同 tenant、enabled、且有 published version。
4. Context 默认只传 summary 和允许的 resource refs，不传完整 message history。
5. `ContextSensitivity.SECRET` 永不传递；`CONFIDENTIAL` 需要目标 Agent 重新通过 `ResourceAccessPolicyPort` 校验。
6. Agent-as-Tool 必须使用 `ToolProvider.AGENT`，不得伪装成 `INTERNAL` 或 `MCP`。
7. child run 失败必须回写 handoff `FAILED` 和 failure code；cancel 幂等。

#### 11.4.4 TDD 顺序

1. 写 `AgentHandoffTests`：状态迁移、必填字段、cancel 幂等、失败码。
2. 写 `DefaultMeshPolicyPortTests`：跨 tenant 拒绝、目标未发布拒绝、超深度拒绝、同 tenant published 允许。
3. 写 `KernelAgentHandoffServiceTests`：裁剪 context、创建 child run、保存 handoff、child fail 回写。
4. 写 `AgentAsToolPortTests`：Tool Gateway 触发 handoff，不绕过 policy/audit。
5. 实现 domain、policy、service、tool adapter。
6. 写 JDBC/Web/starter tests。
7. Phase 8A 完成后补 Audit Ledger 接入测试。

#### 11.4.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,DefaultMeshPolicyPortTests,KernelAgentHandoffServiceTests,AgentAsToolPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 11.4.6 回滚边界

关闭 Agent-as-Tool provider 后，普通 run、tool、approval 不受影响；`sa_agent_handoff` 只追加协作记录，不改变 `sa_agent_run` 主语义；远程 A2A 表和 API 不在本切片创建。

### 11.5 Phase 8 开发卡：Audit Ledger foundation

#### 11.5.1 目标

建立统一 Audit Ledger，先覆盖 publish、run、tool policy/tool invoked、approval、context、secret 等关键事件。首版不替代现有业务主表、不做完整 Eval/Quota/Canary，也不把普通 debug log 搬进 audit。

#### 11.5.2 文件边界

| 层 | 文件 | 具体改动 |
| --- | --- | --- |
| Domain | `AuditEvent.java`、`AuditEventType.java`、`AuditActorType.java`、`AuditResourceRef.java` | 事件快照和必填不变量。 |
| Domain | `AuditRedactionPolicy.java`、`AuditRedactionReason.java` | 对 secret/token/password/apiKey/authorization 做结构化脱敏。 |
| Outbound port | `AuditLedgerPort.java` | append-only 审计入口，提供 noop fallback。 |
| Repository port | `AuditEventRepositoryPort.java` | `append`、`page`、`findById`，不提供 update/delete。 |
| Inbound port | `AuditQueryInboundPort.java` | 查询 API 契约。 |
| Application | `KernelAuditLedgerService.java` | append 前 redaction，query 编排，写失败策略。 |
| JDBC | `JdbcAuditEventRepositoryAdapter.java` | `sa_audit_event` append/page。 |
| Web | `SeahorseAuditEventController.java` | `GET /api/audit-events`、可选 `GET /api/audit-events/{auditId}`。 |
| Integration | publish/run/tool/approval/context/secret 服务边界 | 通过 `AuditLedgerPort` 追加事件，不在 repository 旁路写。 |

#### 11.5.3 事件规则

1. `AuditEventType` 首批：`AGENT_PUBLISHED`、`RUN_STARTED`、`RUN_FINISHED`、`TOOL_POLICY_DECIDED`、`TOOL_INVOKED`、`APPROVAL_DECIDED`、`CONTEXT_ACCESSED`、`SECRET_USED`。
2. `AuditActorType` 首批：`USER`、`AGENT`、`SYSTEM`、`REMOTE_AGENT`。
3. event 必须包含 tenantId、eventType、actorType、actorId、createdAt。
4. 有 run、agent、resource 上下文时必须填对应 id，不允许半结构化字符串塞进 payload。
5. `eventJson` 写入前必须 redaction；marker 使用 `AuditRedactionPolicy.REDACTED_VALUE`。
6. Query 默认 `createdAt DESC`；page size 上限使用具名常量。
7. Noop ledger 只作为显式 fallback；生产默认 JDBC ledger。

#### 11.5.4 TDD 顺序

1. 写 `AuditEventTests`：必填字段、enum、payload 非空、resource ref 校验。
2. 写 `AuditRedactionPolicyTests`：嵌套 JSON 中 secret/token/password/apiKey/authorization 脱敏。
3. 写 `KernelAuditLedgerServiceTests`：append 先 redaction、query 走 repository、noop fallback 不抛、写失败策略明确。
4. 实现 domain、ports、service。
5. 写 `JdbcAuditEventRepositoryAdapterTests`：append、findById、按 run/resource/eventType 查询、分页排序。
6. 写 `SeahorseAuditEventControllerTests`：查询参数、分页、非法 page size。
7. 分批接入 publish/run/tool/approval/context/secret 事件，每类至少一个测试。
8. 完成后再启动 Eval/Quota/SRE，不把生产准入能力混入 Audit 首切片。

#### 11.5.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AuditEventTests,AuditRedactionPolicyTests,KernelAuditLedgerServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAuditEventControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 11.5.6 回滚边界

`sa_audit_event` 只追加；关闭 audit auto-configuration 可切回 noop fallback；历史 audit 数据保留；首版 audit 写失败不阻断低风险读流程，高风险写流程是否 fail-closed 放到后续生产策略切片。

## 12. 2026-05-26 深读后的阶段级详细补充方案

本节是在重新阅读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、当前 worktree 状态和近期 Aegis 记录后补充的阶段级方案。它与第 11 节的“下一步开发卡”关系是：第 11 节给出最近一个可执行切片，本节给出每个未完成阶段从当前状态推进到阶段退出条件的更完整设计开发方案。

### 12.1 Phase 4 补充方案：Resource Governance Hardening 闭环

#### 12.1.1 当前判断

Phase 4 的 ContextPack、AccessDecision、默认资源 ACL、ACL-backed policy、JDBC 资源 ACL 仓储和 Web 管理入口已经具备主闭环。剩余风险不是“能不能授权”，而是资源治理能否在企业场景中稳定运营：批量导入、重复规则处理、数据库约束、决策审计一致性、以及导入前的 dry-run 报告还不够完整。

#### 12.1.2 目标

把资源 ACL 从“单条管理 API 可用”推进到“可批量运营、可审计、可回滚”的生产化硬化状态。完成后，管理员可以先 dry-run 一批 ACL 规则，看到重复、冲突、过期、越界和格式错误，再选择写入；ContextPack 仍只通过 `ResourceAccessPolicyPort` 获取决策，不直接感知规则来源。

#### 12.1.3 文件边界

| 层 | 文件或包 | 具体设计 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/context/ResourceAclRule.java` | 保持规则不变量 owner，不加入导入流程状态。 |
| Domain | `ResourceAclImportItemStatus.java`、`ResourceAclImportReasonCode.java` | 新增导入结果枚举，覆盖 `VALID`、`INVALID`、`DUPLICATE_IN_BATCH`、`DUPLICATE_EXISTING`、`CONFLICT`。 |
| Inbound port | `ResourceAclManagementInboundPort.java` | 增加 `dryRunImport(ResourceAclImportDryRunCommand)`；首版只 dry-run，不直接批量 commit。 |
| Application | `KernelResourceAclManagementService.java` | 编排 dry-run、重复检测、现有规则查重、冲突报告；不复制 `ResourceAclRule` 不变量。 |
| Outbound port | `ResourceAclRepositoryPort.java` | 增加按 rule natural key 查询的方法，例如 tenant/resource/subject/action/effect/status。 |
| JDBC | `JdbcResourceAclRepositoryAdapter.java`、PostgreSQL schema | 增加唯一性辅助索引和 enum 字段 CHECK 约束；JDBC 只做映射和查询。 |
| Web | `SeahorseResourceAclController.java` | 增加 `POST /api/resource-acl-rules:dry-run-import`。 |
| Audit | Phase 8 `AuditLedgerPort` 完成后接入 | dry-run 只记录摘要，正式写入记录 `RESOURCE_ACL_CHANGED`，不把批量明细塞进普通日志。 |

#### 12.1.4 领域规则

1. dry-run 不写入 `sa_resource_acl_rule`，返回每条输入的结构化判定。
2. 同一批次中 natural key 相同的规则标为 `DUPLICATE_IN_BATCH`，保留第一条为候选。
3. 数据库中已存在同 natural key 且 `ENABLED` 的规则时标为 `DUPLICATE_EXISTING`。
4. `DENY` 与 `ALLOW` 对同一 tenant/resource/subject/action 同时出现时标为 `CONFLICT`，最终决策仍遵守 `ResourceAclRuleConflictPolicy.DENY_WINS`。
5. 过期时间早于当前时间的输入标为 `INVALID`，不转换为 `EXPIRED` 规则写入。
6. `RESOURCE_TYPE` scope 首版只允许在 dry-run 中报告 `UNSUPPORTED_SCOPE`，不写入。
7. 所有状态、原因和导入来源必须使用 enum 或具名常量，不能返回自由字符串状态。

#### 12.1.5 API 合约

`POST /api/resource-acl-rules:dry-run-import`

```json
{
  "tenantId": "default",
  "source": "ADMIN_UPLOAD",
  "rules": [
    {
      "resourceType": "DOCUMENT",
      "resourceId": "doc_123",
      "subjectType": "USER",
      "subjectId": "u_1",
      "action": "READ",
      "effect": "ALLOW",
      "priority": 100,
      "expiresAt": "2026-06-30T00:00:00Z"
    }
  ]
}
```

响应只包含报告，不包含可执行脚本：

```json
{
  "validCount": 1,
  "invalidCount": 0,
  "duplicateCount": 0,
  "conflictCount": 0,
  "items": [
    {
      "index": 0,
      "status": "VALID",
      "reasonCode": "VALID_RULE",
      "naturalKey": "default:DOCUMENT:doc_123:USER:u_1:READ"
    }
  ]
}
```

#### 12.1.6 TDD 顺序

1. 写 `ResourceAclImportDryRunTests`，覆盖合法规则、批内重复、库内重复、allow/deny 冲突、过期规则、unsupported scope。
2. 跑 kernel RED，确认 command/result/enum/service 方法缺失导致失败。
3. 实现 import command、result、status enum、reason enum 和 service dry-run 编排。
4. 写 `JdbcResourceAclRepositoryAdapterTests`，覆盖 natural key 查询、重复规则查找、enum CHECK 约束映射。
5. 实现 JDBC 查询和 schema 约束。
6. 写 Web contract test，覆盖 dry-run 不写库、非法 enum 由请求绑定拒绝、响应不含敏感字段。
7. Phase 8 Audit 完成后，补 `RESOURCE_ACL_CHANGED` 的审计接入测试。

#### 12.1.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,ResourceAclImportDryRunTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseResourceAclControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 12.1.8 回滚边界

新增 dry-run API 可单独关闭，不影响单条 ACL create/page/disable；新增索引和 CHECK 约束只约束新表 `sa_resource_acl_rule`；ContextPack builder 不改调用方式，回滚时仍可使用当前默认 ACL 或已实现的 ACL-backed policy。

### 12.2 Phase 5 补充方案：Secure Connector Runtime 闭环

#### 12.2.1 当前判断

Phase 5 已完成 Secret API、MCP static bearer 和 OpenAPI Connector import 主线；当前关键缺口集中在 OAuth token provider、MCP bearer material 注入、OpenAPI 导入后的安全启用边界，以及 sandbox runtime 的外部适配契约。Phase 5 不应扩展成远程 Agent mesh，也不应在主 JVM 中执行任意脚本。

#### 12.2.2 目标

形成一个“外部能力接入必须经过凭据、策略、工具网关和审计”的最小生产闭环。MCP 支持 `CLIENT_CREDENTIALS`，OpenAPI connector 导入后默认 disabled，sandbox 只定义外部运行时 port 与 fail-closed policy，不实现本地脚本执行器。

#### 12.2.3 文件边界

| 子域 | 文件或包 | 具体设计 |
| --- | --- | --- |
| Credential | `ports/outbound/credential/*OAuth*` | OAuth request、token、cache key、cache port、token port 和 provider 保持在 kernel port 层。 |
| MCP adapter | `seahorse-agent-adapter-mcp-http/.../McpHttpAdapterProperties.java` | 增加 OAuth 配置字段，不接受明文 secret。 |
| MCP adapter | `McpHttpAutoConfiguration.java` | 从 server 配置构造 `CredentialRequest.clientCredentials(...)`，缺少 clientId 或 clientSecretRef 时 fail-closed。 |
| MCP adapter | `StreamableHttpMcpClient.java` | 只识别最终 `CredentialMaterial` 是否可注入 bearer，不感知 OAuth grant 细节。 |
| Connector | `kernel/domain/agent/connector` | Connector、version、operation 继续维护导入不变量和默认 disabled。 |
| Sandbox | `ports/outbound/agent/SandboxRuntimePort.java`、`SandboxPolicyPort.java` | 新增小接口；首版只允许外部 adapter 实现，kernel 不执行脚本。 |
| Starter | `SeahorseAgentCredentialAutoConfiguration.java` | 条件装配 OAuth-capable credential provider，用户自定义 `CredentialProviderPort` 时退让。 |

#### 12.2.4 OAuth 领域规则

1. 首个可执行 grant 只支持 `OAuthGrantType.CLIENT_CREDENTIALS`；`USER_DELEGATED` 作为 enum 与 fail-closed 语义保留。
2. token cache key 由 tenantId、serverId、clientId、scopeSet、audience、resource 组成；scope 必须排序归一。
3. token cache TTL 使用 `OAuthTokenTtlPolicy` 计算，必须小于 token 剩余有效期并扣除安全窗口。
4. `CredentialMaterial.toString()`、异常消息、audit payload、ToolCatalog metadata 都不得包含 token 或 client secret。
5. MCP `CLIENT_CREDENTIALS` 配置缺少 clientId、clientSecretRef 或 serverId 时不注册远程工具。
6. `StreamableHttpMcpClient` 只注入 `Authorization: Bearer`，不持有 token 获取逻辑。

#### 12.2.5 OpenAPI 与 sandbox 规则

1. OpenAPI import 只生成 connector operation 和 ToolCatalog entry，不执行真实 HTTP operation。
2. 导入后的 connector operation 默认 `DISABLED`，管理员显式 enable 后才可被 Tool Gateway 发现。
3. DELETE/PATCH/POST 默认风险不低于 `HIGH` 或 `MEDIUM`，风险推断使用 enum，不用路径字符串魔法判断散落各处。
4. Sandbox 首版只支持 `SandboxRuntimePort` 契约和 policy 判断；默认 adapter 返回 unsupported 或 deny。
5. sandbox 网络默认 deny，文件系统按 run/session 隔离，artifact 出沙箱前必须有敏感信息扫描 port。
6. 所有 connector/sandbox 执行最终必须接入 Phase 8 Audit Ledger；Phase 5 首版保留 audit port 调用点。

#### 12.2.6 TDD 顺序

1. 完成 kernel OAuth RED/GREEN：request、cache key、credential provider、secretRef 缺失 fail-closed。
2. 写 `McpHttpOAuthCredentialTests`，覆盖 config 到 credential request、OAuth bearer 注入、缺字段不注册工具。
3. 写 starter credential auto-configuration tests，覆盖默认 provider 装配和自定义 provider 退让。
4. 为 OpenAPI connector 增加 `ConnectorOperationEnableCommand` 的风险边界测试，确保 import 后 disabled。
5. 写 sandbox port contract tests：默认 deny、unsupported adapter 不执行、policy decision reason 使用 enum。
6. 实现 MCP adapter 和 starter wiring。
7. 实现 sandbox kernel port/domain 最小对象与 noop/deny adapter，不接本地 shell、browser 或 code interpreter。

#### 12.2.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=CredentialMaterialTests,OAuthTokenRequestTests,OAuthTokenCacheKeyTests,OAuthCredentialProviderTests,SandboxPolicyDecisionTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-mcp-http -am '-Dtest=StreamableHttpMcpClientCredentialTests,McpHttpAutoConfigurationCredentialTests,McpHttpOAuthCredentialTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentCredentialAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 12.2.8 回滚边界

关闭 OAuth provider 后，`NONE` 与 `STATIC_BEARER` MCP 仍工作；connector import 表和 ToolCatalog entry 保留但可 disabled；sandbox 默认 deny adapter 可保持上线，不会引入执行能力；OAuth token cache 是派生数据，可清空。

### 12.3 Phase 6 补充方案：Agent Factory Publish-Ready 闭环

#### 12.3.1 当前判断

Phase 6 基本未开始。Phase 1-5 已经提供 Agent definition、run、tool、approval、context、credential 和 connector 的底座，因此 Phase 6 不应重建 Agent 定义模型，而应提供模板、派生、发布校验和回滚入口，把现有能力包装为业务团队可运营的 Agent Factory。

#### 12.3.2 目标

先实现“模板只读 -> from-template 创建 DRAFT -> 发布前校验报告 -> rollback 创建新版本指针”的闭环。首版不做完整 Studio UI，不让业务用户绕过已存在的 AgentDefinition 发布规则，也不把 eval/quota 缺失当作强阻断，先返回 `WARN`。

#### 12.3.3 文件边界

| 层 | 文件或包 | 具体设计 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/factory/AgentTemplate.java` | 内置模板快照，维护工具上限、风险上限、默认 guardrails、说明。 |
| Domain | `AgentTemplateId.java`、`AgentTemplateRiskProfile.java` | 使用 enum 管理模板 ID 和风险画像。 |
| Domain | `AgentPublishCheck.java`、`AgentPublishCheckItem.java` | 发布校验报告，不直接修改 AgentVersion。 |
| Inbound port | `AgentFactoryInboundPort.java` | `listTemplates`、`createFromTemplate`、`validatePublish`、`rollbackToVersion`。 |
| Outbound port | `AgentTemplateRepositoryPort.java` | 读取内置或 JDBC seed 模板。 |
| Outbound port | `AgentPublishCheckRepositoryPort.java` | 保存校验报告，供审计和 UI 展示。 |
| Application | `KernelAgentFactoryService.java` | 组合现有 AgentDefinition service，不复制发布和版本不变量。 |
| JDBC | `JdbcAgentTemplateRepositoryAdapter.java`、`JdbcAgentPublishCheckRepositoryAdapter.java` | seed 读取和报告保存。 |
| Web | `SeahorseAgentFactoryController.java` | 模板、from-template、validate、rollback API。 |

#### 12.3.4 模板与派生规则

1. 内置模板 ID 使用 enum：`KNOWLEDGE_ASSISTANT`、`WORKFLOW_ASSISTANT`、`TOOL_OPERATOR`、`COMPLIANCE_REVIEWER`。
2. from-template 创建的 AgentDefinition 状态只能是 `DRAFT`。
3. 请求工具集必须是模板 `allowedToolIds` 子集；越界返回结构化错误，不进入发布流程。
4. 请求风险等级不能高于模板风险上限。
5. `instructionsOverlay` 只能追加业务说明，不能覆盖模板安全边界；合并由领域对象完成。
6. 发布校验项使用 enum：`INSTRUCTIONS_PRESENT`、`TOOLS_ENABLED`、`HIGH_RISK_APPROVAL_PRESENT`、`RESOURCE_ACL_PRESENT`、`EVAL_PRESENT`、`QUOTA_PRESENT`、`OWNER_PRESENT`、`CHANGE_SUMMARY_PRESENT`。
7. Phase 8 前 `EVAL_PRESENT` 和 `QUOTA_PRESENT` 返回 `WARN`；高风险工具无审批返回 `FAIL`。
8. rollback 不修改旧版本，只创建或选择新的发布指针，遵守 AgentVersion 不可变语义。

#### 12.3.5 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-templates` | 返回 enabled 模板列表。 |
| `POST` | `/api/agents/from-template` | 基于模板创建 DRAFT AgentDefinition。 |
| `POST` | `/api/agents/{agentId}/validate` | 保存并返回发布校验报告。 |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/rollback` | 回滚到既有版本，不修改历史版本内容。 |

`validate` 响应使用结构化校验项：

```json
{
  "status": "WARN",
  "items": [
    {
      "code": "EVAL_PRESENT",
      "status": "WARN",
      "message": "Evaluation platform is not enabled for this agent yet."
    }
  ]
}
```

#### 12.3.6 TDD 顺序

1. 写 `AgentTemplateTests`，覆盖工具子集、风险上限、overlay 合并、安全边界不可覆盖。
2. 写 `KernelAgentFactoryServiceTests`，覆盖 list templates、from-template draft、工具越界、风险越界、validate publish。
3. 跑 kernel RED。
4. 实现 domain、ports、service，并通过现有 AgentDefinition inbound port 创建 draft。
5. 写 JDBC tests，覆盖 seed 模板读取和 publish check 保存/查询。
6. 写 Web tests，覆盖四个 API 的请求、响应和错误边界。
7. 写 starter tests，覆盖 repository/service 条件装配和用户自定义 bean 退让。

#### 12.3.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentTemplateTests,KernelAgentFactoryServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentTemplateRepositoryAdapterTests,JdbcAgentPublishCheckRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 12.3.8 回滚边界

模板表和发布校验表只追加；关闭 factory controller 后，既有 Agent Definition、Version、Run 不受影响；已创建 draft 仍作为普通 draft 保留；rollback 不删除版本，因此可再次切回原版本。

### 12.4 Phase 7 补充方案：Governed Local Handoff 闭环

#### 12.4.1 当前判断

Phase 7 目前只有规划和少量枚举基础，不具备多 Agent 能力。结合阶段文档和差距分析，最小可靠路径应先做本地 Agent-as-Tool 与 handoff 记录，不提前开放远程 A2A、Agent Card 注册或 Mesh 控制面。

#### 12.4.2 目标

把“目标 Agent 作为受 Tool Gateway 管理的工具”落地：父 run 调用 agent tool 时创建 handoff，裁剪上下文，校验目标 Agent，创建 child run，保存 parent/child 关系，并能查询 handoff 状态。所有调用仍经过 Tool Gateway、Policy、Approval 和 Audit，不允许 Agent 之间直接互调 service。

#### 12.4.3 文件边界

| 层 | 文件或包 | 具体设计 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/mesh/AgentHandoff.java` | 维护 handoff 快照、状态迁移和失败码。 |
| Domain | `AgentHandoffStatus.java` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。 |
| Domain | `AgentHandoffFailureCode.java` | `TARGET_NOT_FOUND`、`TARGET_DISABLED`、`TARGET_NOT_PUBLISHED`、`DEPTH_EXCEEDED`、`POLICY_DENIED`、`CHILD_RUN_FAILED`。 |
| Domain | `AgentHandoffContextPolicy.java` | 裁剪 summary、resource refs、sensitivity 上限。 |
| Inbound port | `AgentHandoffInboundPort.java` | 查询和取消 handoff；创建优先走 Tool Gateway。 |
| Outbound port | `AgentHandoffRepositoryPort.java` | append/update 状态，按 run 查询。 |
| Outbound port | `MeshPolicyPort.java` | 判断 tenant、深度、目标状态、上下文传递边界。 |
| Application | `KernelAgentHandoffService.java` | 校验目标 Agent、裁剪 context、创建 child run、更新 handoff。 |
| Tool | `AgentAsToolPort.java` | 以 `ToolProvider.AGENT` 暴露目标 Agent，不绕过 Tool Gateway。 |
| JDBC/Web | `JdbcAgentHandoffRepositoryAdapter.java`、`SeahorseAgentHandoffController.java` | 持久化和查询。 |

#### 12.4.4 领域规则

1. handoff 状态只能按 `CREATED -> RUNNING -> SUCCEEDED/FAILED/CANCELLED` 迁移。
2. 最大本地 handoff 深度使用 `MeshPolicyLimits.MAX_LOCAL_HANDOFF_DEPTH`，默认 2。
3. source agent 与 target agent 必须同 tenant；跨 tenant 只在未来 remote A2A adapter 中处理。
4. target agent 必须 enabled 且存在 published version。
5. context 默认只传 task summary、allowed resource refs 和必要 metadata；不传完整 message history。
6. `ContextSensitivity.SECRET` 永不传递；`CONFIDENTIAL` 传递前必须让目标 Agent 再次通过资源 ACL。
7. Agent-as-Tool 的 tool metadata 必须标识 `ToolProvider.AGENT` 和目标 agentId/versionId。
8. child run 失败必须回写 handoff failure code，不能只依赖 run 状态。

#### 12.4.5 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-runs/{runId}/handoffs` | 查询某个 run 的 handoff。 |
| `GET` | `/api/agent-handoffs/{handoffId}` | 查看 handoff 详情。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 幂等取消未完成 handoff。 |

创建 handoff 首版不开放独立 Web API，优先由 Tool Gateway 调用 `AgentAsToolPort` 触发，避免外部绕过工具策略。

#### 12.4.6 TDD 顺序

1. 写 `AgentHandoffTests`，覆盖必填、状态迁移、cancel 幂等、失败码。
2. 写 `DefaultMeshPolicyPortTests`，覆盖同 tenant published 允许、跨 tenant 拒绝、目标 disabled 拒绝、超深度拒绝。
3. 写 `AgentHandoffContextPolicyTests`，覆盖 summary-only、secret 丢弃、confidential 需要重新 ACL。
4. 写 `KernelAgentHandoffServiceTests`，覆盖创建 child run、保存 handoff、child fail 回写。
5. 写 `AgentAsToolPortTests`，证明调用路径经过 Tool Gateway policy/audit，而不是直接调用 child service。
6. 实现 domain、policy、service、tool adapter。
7. 写 JDBC/Web/starter tests，覆盖 repository 和查询 API。

#### 12.4.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,DefaultMeshPolicyPortTests,AgentHandoffContextPolicyTests,KernelAgentHandoffServiceTests,AgentAsToolPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 12.4.8 回滚边界

关闭 Agent-as-Tool provider 后，本地普通工具、MCP 工具、OpenAPI connector 和普通 run 不受影响；`sa_agent_handoff` 是协作记录表，不改变 `sa_agent_run` 主语义；远程 A2A、Agent Card 和 Mesh 控制面不在本方案中创建。

### 12.5 Phase 8 补充方案：Audit-Led Production Gate 闭环

#### 12.5.1 当前判断

Phase 8 已有 Retrieval eval、RateLimiter 和 Feature health 的基础，但尚未形成企业试点所需的统一审计、通用评估、配额、canary 和 SRE 准入闭环。考虑到 Phase 4-7 都需要审计证据，Phase 8 的第一阶段应以 Audit Ledger 为中心，再逐步把 eval、quota 和 health gate 接入发布流程。

#### 12.5.2 目标

先建立 append-only Audit Ledger，并定义 Production Gate 的骨架：任何 Agent 进入企业试点前，必须能查询 owner、版本、工具风险、资源 ACL、审批策略、审计事件、评估状态和配额状态。首版 Gate 允许部分项为 `WARN`，但高风险工具无审批、无审计入口、无 owner 必须 `FAIL`。

#### 12.5.3 文件边界

| 子域 | 文件或包 | 具体设计 |
| --- | --- | --- |
| Audit domain | `kernel/domain/agent/audit/AuditEvent.java` | 事件快照，不可变，append-only。 |
| Audit domain | `AuditEventType.java`、`AuditActorType.java`、`AuditResourceRef.java` | 事件类型、actor 类型、资源引用均用 enum/value object。 |
| Audit service | `KernelAuditLedgerService.java` | append 前 redaction，query 编排，写失败策略。 |
| Audit ports | `AuditLedgerPort.java`、`AuditEventRepositoryPort.java`、`AuditQueryInboundPort.java` | append、repository、query 拆分，避免大接口。 |
| Gate domain | `ProductionGateReport.java`、`ProductionGateCheckCode.java`、`ProductionGateCheckStatus.java` | 生产准入检查结果。 |
| Gate service | `KernelProductionGateService.java` | 组合 definition、tool、approval、ACL、audit、eval、quota、health port。 |
| Eval bridge | `EvaluationStatusPort.java` | 首版只查是否存在通过的 eval run，不实现完整 eval 平台。 |
| Quota bridge | `QuotaStatusPort.java` | 首版只查是否配置 quota，不实现复杂计费。 |
| JDBC/Web | `JdbcAuditEventRepositoryAdapter.java`、`SeahorseAuditEventController.java`、`SeahorseProductionGateController.java` | 审计查询和准入报告 API。 |

#### 12.5.4 Audit 事件规则

1. 首批事件：`AGENT_PUBLISHED`、`RUN_STARTED`、`RUN_FINISHED`、`TOOL_POLICY_DECIDED`、`TOOL_INVOKED`、`APPROVAL_DECIDED`、`CONTEXT_ACCESSED`、`SECRET_USED`、`CONNECTOR_IMPORTED`、`RESOURCE_ACL_CHANGED`。
2. `eventJson` 写入前必须经过 `AuditRedactionPolicy`，递归处理 `secret`、`token`、`password`、`apiKey`、`authorization` key。
3. 审计 payload 中可保留 secretRef，不可保留 secretValue。
4. repository port 不提供 update/delete。
5. query 默认按 `createdAt DESC`，page size 上限使用 `AuditQueryLimits.MAX_PAGE_SIZE`。
6. 低风险读流程 audit 写失败不阻断；高风险写流程的 fail-closed 策略由 `AuditWriteFailurePolicy` 枚举控制。

#### 12.5.5 Production Gate 规则

1. `OWNER_PRESENT`、`PUBLISHED_VERSION_PRESENT`、`TOOL_RISK_REVIEWED`、`HIGH_RISK_APPROVAL_PRESENT`、`AUDIT_LEDGER_ENABLED` 为首版强检查。
2. `RESOURCE_ACL_PRESENT` 在只读知识助手中为 `WARN`，在工具执行 Agent 中为 `FAIL`。
3. `EVAL_PASSING`、`QUOTA_CONFIGURED`、`SRE_HEALTH_GREEN` 首版可返回 `WARN`，但必须出现在报告中。
4. Gate service 只生成报告，不直接发布 Agent；发布是否阻断由 Phase 6 publish check 组合决定。
5. Gate 报告保存为快照，避免后续配置变化导致历史发布证据不可重建。

#### 12.5.6 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/audit-events` | 按 tenant、run、agent、resource、eventType、时间范围查询。 |
| `GET` | `/api/audit-events/{auditId}` | 查询单条审计事件。 |
| `POST` | `/api/agents/{agentId}/production-gate` | 生成生产准入报告。 |
| `GET` | `/api/agents/{agentId}/production-gate/latest` | 查询最近一次准入报告。 |

#### 12.5.7 TDD 顺序

1. 写 `AuditEventTests`，覆盖必填、不变性、resource ref 校验。
2. 写 `AuditRedactionPolicyTests`，覆盖嵌套 JSON 和大小写 key 的脱敏。
3. 写 `KernelAuditLedgerServiceTests`，覆盖 append redaction、noop fallback、写失败策略、query 上限。
4. 写 `JdbcAuditEventRepositoryAdapterTests`，覆盖 append、findById、分页、按 run/resource/eventType 查询。
5. 写 `ProductionGateReportTests`，覆盖强检查、warn 检查和状态汇总。
6. 写 `KernelProductionGateServiceTests`，使用 fake ports 验证 owner/tool/approval/audit/eval/quota/health 的组合逻辑。
7. 写 Web tests，覆盖 audit query 和 production gate API。
8. 分阶段接入 Phase 4-7 事件，不一次性改完所有服务。

#### 12.5.8 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AuditEventTests,AuditRedactionPolicyTests,KernelAuditLedgerServiceTests,ProductionGateReportTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAuditEventControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 12.5.9 回滚边界

`sa_audit_event` 和 production gate report 表只追加；关闭 gate controller 不影响 Agent run；audit ledger 可回退到 noop fallback，但 production gate 必须将 `AUDIT_LEDGER_ENABLED` 标为 `FAIL`；eval、quota、health bridge 首版只读状态，不改变对应系统行为。

## 13. 2026-05-26 当前实现后的未完成阶段详细方案

本节基于当前 worktree 的真实进展追加，优先级高于第 10-12 节中已经过时的判断。当前事实是：Phase 3 的 Approval query/decision API、checkpoint/resume/lease/retry 主闭环已经出现；Phase 4 Resource ACL 管理闭环已进入实现状态；Phase 5A OAuth、Phase 5B OpenAPI Connector、Phase 5C Sandbox kernel foundation 已经有代码和聚焦验证痕迹；Phase 6 Agent Factory kernel foundation 已完成，JDBC/Web/starter 集成仍在进行中；Phase 7 和 Phase 8 仍基本停留在规划层。

本节仍只补设计开发方案，不代表 AI Infra 已完成。后续实现必须继续按 TDD、ports/adapters、小接口、默认保守和可回滚原则推进。

### 13.1 Phase 4 详细方案：Resource Governance 生产化硬化

#### 13.1.1 当前状态

Phase 4 当前已经不再是从零实现 ContextPack 或 Resource ACL。已有能力包括 ContextPack 构建、AccessDecision 记录、默认资源策略、ACL-backed policy、`ResourceAclRule`、JDBC ACL 仓储、Web 管理入口和 starter wiring。剩余问题集中在生产运营质量：批量变更前校验、数据库约束、决策审计一致性、上下文 item 的可解释证据和后续 Audit Ledger 接入。

#### 13.1.2 目标

把 Resource ACL 从“单条规则可管理”推进到“批量变更可预检、可审计、可回滚”。完成后，管理员可以先对一批 ACL 规则执行 dry-run，得到重复、冲突、过期、unsupported scope 和库内已存在报告；ContextPack builder 仍只依赖 `ResourceAccessPolicyPort`，不感知规则来源。

#### 13.1.3 设计开发方案

| 层 | 变更点 | 设计 |
| --- | --- | --- |
| Domain | `ResourceAclImportDryRunReport`、`ResourceAclImportItem` | 新增 dry-run 报告对象，不改变 `ResourceAclRule` 不变量。 |
| Domain | `ResourceAclImportItemStatus`、`ResourceAclImportReasonCode`、`ResourceAclImportSource` | 用 enum 表达 `VALID`、`INVALID`、`DUPLICATE_IN_BATCH`、`DUPLICATE_EXISTING`、`CONFLICT`、`UNSUPPORTED_SCOPE`。 |
| Inbound port | `ResourceAclManagementInboundPort` | 增加 `dryRunImport(ResourceAclImportDryRunCommand)`，首版不提供批量 commit。 |
| Outbound port | `ResourceAclRepositoryPort` | 增加 natural key 查询，用于发现同 tenant/resource/subject/action/status 的已存在规则。 |
| Application | `KernelResourceAclManagementService` | 编排格式校验、批内查重、库内查重、allow/deny 冲突报告；不复制 ACL 命中和 deny-wins 决策。 |
| JDBC | `JdbcResourceAclRepositoryAdapter`、PostgreSQL schema | 增加 natural key 查询、enum `CHECK` 约束和查重索引；repository 不承载业务判断。 |
| Web | `SeahorseResourceAclController` | 增加 `POST /api/resource-acl-rules:dry-run-import`。 |
| Audit 接入点 | `AuditLedgerPort` 后续组合 | dry-run 记录摘要；真实 create/disable 事件在 Phase 8 接入 `RESOURCE_ACL_CHANGED`。 |

#### 13.1.4 关键规则

1. dry-run 永不写入 `sa_resource_acl_rule`。
2. 同一批次 natural key 相同的第二条及后续条目标记为 `DUPLICATE_IN_BATCH`。
3. 数据库中存在同 natural key 且 `ENABLED` 的规则时标记为 `DUPLICATE_EXISTING`。
4. 同一 tenant/resource/subject/action 同时出现 `ALLOW` 和 `DENY` 时标记为 `CONFLICT`，但运行时决策继续遵守 `ResourceAclRuleConflictPolicy.DENY_WINS`。
5. `expiresAt` 早于当前时间的输入标记为 `INVALID`，不自动转换成可写入的 `EXPIRED` 规则。
6. `ResourceAclRuleScope.RESOURCE_TYPE` 首版只允许报告 `UNSUPPORTED_SCOPE`，不开放写入。
7. 所有导入状态、原因和来源必须是 enum 或具名常量。

#### 13.1.5 API 合约

`POST /api/resource-acl-rules:dry-run-import`

```json
{
  "tenantId": "default",
  "source": "ADMIN_UPLOAD",
  "rules": [
    {
      "resourceType": "DOCUMENT",
      "resourceId": "doc_123",
      "subjectType": "USER",
      "subjectId": "u_1",
      "action": "READ",
      "effect": "ALLOW",
      "priority": 100,
      "expiresAt": "2026-06-30T00:00:00Z"
    }
  ]
}
```

响应返回报告快照，不返回可执行 SQL 或批量写入 token：

```json
{
  "validCount": 1,
  "invalidCount": 0,
  "duplicateCount": 0,
  "conflictCount": 0,
  "items": [
    {
      "index": 0,
      "status": "VALID",
      "reasonCode": "VALID_RULE",
      "naturalKey": "default:DOCUMENT:doc_123:USER:u_1:READ"
    }
  ]
}
```

#### 13.1.6 TDD 顺序

1. 写 `ResourceAclImportDryRunTests` 覆盖合法输入、批内重复、库内重复、allow/deny 冲突、过期输入和 unsupported scope。
2. 跑 kernel RED，确认 command/report/enum/service 方法缺失导致失败。
3. 实现 dry-run domain、command、report 和 service 编排。
4. 写 `JdbcResourceAclRepositoryAdapterTests` 覆盖 natural key 查询和 enum 约束映射。
5. 实现 JDBC 查询和 schema 约束。
6. 写 Web contract test，覆盖 dry-run 不写库、非法 enum 绑定失败、响应不含敏感字段。
7. Phase 8 Audit Ledger 完成后，补 ACL 变更事件审计测试。

#### 13.1.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,ResourceAclImportDryRunTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseResourceAclControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 13.1.8 回滚边界

新增 dry-run API 可单独关闭，不影响现有 create/page/disable；新增索引和 `CHECK` 约束只作用于 ACL 表；ContextPack builder 不改变调用方式；Audit Ledger 未接入前保留明确接入点，不让审计缺口阻断当前 ACL 主闭环。

### 13.2 Phase 5 详细方案：Secure Connector Runtime 集成闭环

#### 13.2.1 当前状态

Phase 5 当前已具备 Secret API、MCP static bearer、OAuth token provider 基础、OpenAPI Connector import、sandbox kernel foundation。剩余关键缺口是把这些能力接成可运营的外部工具运行闭环：MCP OAuth 装配与回归、OpenAPI operation 安全启用、Sandbox 的 JDBC/Web/starter 外层、artifact 扫描适配、以及统一审计接入。

#### 13.2.2 目标

让外部工具接入具备最小生产边界：凭据只通过 secretRef/OAuth provider 解析；OpenAPI import 后默认 disabled，启用必须显式管理；sandbox 只有外部 runtime adapter 能执行，默认 unsupported/fail-closed；所有能力最终都经过 Tool Gateway、Policy、Credential 和 Audit。

#### 13.2.3 设计开发方案

| 子域 | 变更点 | 设计 |
| --- | --- | --- |
| MCP OAuth | `McpHttpAdapterProperties`、`McpHttpAutoConfiguration`、`StreamableHttpMcpClient` | 配置只保存 `clientSecretRef`，auto-config 组合 `CredentialProviderPort`，client 只注入 bearer material。 |
| OAuth cache | `OAuthTokenCachePort` 实现 | 首版可用 in-memory；后续 Redis adapter 替换时必须遵守同一 TTL 语义。 |
| OpenAPI enable | `ConnectorOperationEnableCommand`、connector service | import 默认 disabled；enable 时重新校验 method risk、approval、credential binding。 |
| Sandbox persistence | `SandboxSessionRepositoryPort`、`SandboxExecutionRepositoryPort` | 将 session/execution 从内存服务快照推进到可查、可审计的仓储契约。 |
| Sandbox Web | `SeahorseSandboxController` | 增加 create session、execute、close、list artifacts API；首版只返回 unsupported/denied 也要可观察。 |
| Sandbox starter | registry/kernel auto-configuration | 默认装配 `DefaultSandboxPolicyPort` 与 `SandboxRuntimePort.unsupported()`，用户自定义 runtime adapter 时退让。 |
| Artifact scan | `SandboxArtifactScanPort` | 首版定义 port 和 noop-safe implementation，只有 `CLEAN` 且非 `SECRET` artifact 可 prompt-visible。 |
| Audit | Phase 8 bridge | 预留 `SANDBOX_SESSION_CREATED`、`SANDBOX_EXECUTION_FINISHED`、`CONNECTOR_OPERATION_ENABLED` 事件。 |

#### 13.2.4 关键规则

1. MCP `CLIENT_CREDENTIALS` 缺少 `serverId`、`clientId`、`clientSecretRef` 或 scopes 时 fail-closed，不注册对应远程工具。
2. `OAuthTokenCacheKey` 必须按 tenant/server/client/scope/audience/resource 归一，scope 排序后参与 key。
3. token、client secret、authorization header 不得进入 prompt、trace、metadata、audit 明文和异常消息。
4. OpenAPI import 只生成 connector operation 和 ToolCatalog entry，不执行真实 HTTP operation。
5. OpenAPI write-like method 默认风险不低于 `MEDIUM`，危险 method 默认不低于 `HIGH`；风险推断集中在领域策略对象。
6. Sandbox 网络默认 `DENY`，默认 runtime `unsupported()` 不得执行任何本地 shell、browser 或 code interpreter。
7. 终态 sandbox session 不得继续 execute。
8. Artifact 只有扫描状态 `CLEAN` 且 sensitivity 非 `SECRET` 时才允许进入 prompt。

#### 13.2.5 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/enable` | 显式启用 OpenAPI operation。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/disable` | 幂等停用 operation。 |
| `POST` | `/api/sandbox/sessions` | 创建 sandbox session；默认可返回 unsupported session。 |
| `POST` | `/api/sandbox/sessions/{sessionId}/execute` | 执行 sandbox command；默认 fail-closed。 |
| `POST` | `/api/sandbox/sessions/{sessionId}/close` | 关闭 session。 |
| `GET` | `/api/sandbox/sessions/{sessionId}/artifacts` | 查询可见 artifact。 |

#### 13.2.6 TDD 顺序

1. 跑 OAuth/MCP focused tests，确认当前实现仍覆盖 token 注入、缺配置 fail-closed 和 token 不泄露。
2. 写 `ConnectorOperationEnableTests` 覆盖默认 disabled、风险边界、缺 credential binding 不可启用。
3. 写 sandbox JDBC RED：session/execution/artifact save、find、page、terminal 状态查询。
4. 实现 sandbox repository ports 与 JDBC adapters。
5. 写 sandbox Web RED：create/execute/close/artifacts API，即使默认 unsupported 也返回结构化状态。
6. 实现 Web controller 和 starter wiring。
7. 写 artifact scan/redaction tests，验证只有 clean non-secret artifact 可进入 prompt。
8. Phase 8 Audit 完成后，补 connector/sandbox 审计事件接入测试。

#### 13.2.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=OAuthCredentialProviderTests,OAuthTokenCacheKeyTests,KernelSandboxRuntimeServiceTests,SandboxArtifactTests,ConnectorOperationEnableTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-mcp-http -am '-Dtest=McpHttpOAuthCredentialTests,StreamableHttpMcpClientCredentialTests,McpHttpAutoConfigurationCredentialTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcConnectorRepositoryAdapterTests,JdbcSandboxRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseOpenApiConnectorControllerTests,SeahorseSandboxControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 13.2.8 回滚边界

关闭 OAuth provider 后，`NONE` 和 `STATIC_BEARER` MCP 保持可用；关闭 connector operation enable API 后，已导入 operation 可保持 disabled；关闭 sandbox runtime adapter 后默认 unsupported/fail-closed，不引入执行能力；token cache 和 sandbox artifact 都是派生数据，可清理。

### 13.3 Phase 6 详细方案：Agent Factory Integration 与发布校验闭环

#### 13.3.1 当前状态

Phase 6 当前已有 kernel foundation：`AgentTemplate`、模板 ID/status、发布校验报告、`AgentFactoryInboundPort`、`AgentTemplateRepositoryPort`、`AgentPublishCheckRepositoryPort` 和 `KernelAgentFactoryService`。剩余缺口是 JDBC 仓储、Web API、starter wiring、rollback API 和把 Phase 8 production gate 接入 publish validation。

#### 13.3.2 目标

完成 “template list -> from-template draft -> validate publish -> latest check query -> rollback entry” 的集成闭环。Factory 只组合现有 `AgentDefinitionInboundPort`，不复制 AgentDefinition/version 发布不变量；业务方可以通过 API 创建低风险 draft，并获得发布前结构化校验报告。

#### 13.3.3 设计开发方案

| 层 | 变更点 | 设计 |
| --- | --- | --- |
| Kernel port | `AgentPublishCheckRepositoryPort` | 增加 `latest(String agentId)`，用于 UI 和审计查看最近校验报告。 |
| JDBC | `JdbcAgentTemplateRepositoryAdapter` | 读取 `sa_agent_template` seed；`list(false)` 只返回 enabled 模板。 |
| JDBC | `JdbcAgentPublishCheckRepositoryAdapter` | 保存 report 快照，并按 agentId 查询 latest。 |
| Web | `SeahorseAgentFactoryController` | 提供 `GET /api/agent-templates`、`POST /api/agents/from-template`、`POST /api/agents/{agentId}/validate`。 |
| Starter | `SeahorseAgentRegistryRepositoryAutoConfiguration` | 条件装配 template/check repository。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration` | 条件装配 `KernelAgentFactoryService`，用户自定义 `AgentFactoryInboundPort` 时退让。 |
| Rollback | 后续 `AgentFactoryInboundPort.rollbackToVersion` | 只创建新的发布指针或 draft，不修改历史 `AgentVersion`。 |
| Production Gate | Phase 8 `ProductionGatePort` | publish validation 可组合 gate report；在 Phase 8 前 eval/quota 仍返回 `WARN`。 |

#### 13.3.4 关键规则

1. from-template 创建的 AgentDefinition 状态只能是 `DRAFT`。
2. requested tools 必须是 template allowed tools 子集。
3. requested risk 不能高于 template risk cap。
4. `instructionsOverlay` 只能追加业务说明，不得覆盖模板安全边界。
5. 高风险工具缺少 approval policy 时 publish check 返回 `FAIL`。
6. Phase 8 前 `EVAL_PRESENT`、`QUOTA_PRESENT` 必须返回 `WARN`，不能被遗漏。
7. publish check report 是审计快照，后续配置变化不得改写历史 report。
8. rollback 不删除、不改写旧版本，遵守 AgentVersion 不可变语义。

#### 13.3.5 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-templates` | 返回 enabled templates。 |
| `GET` | `/api/agent-templates?includeDisabled=true` | 管理员查看所有模板。 |
| `POST` | `/api/agents/from-template` | 基于模板创建 draft AgentDefinition。 |
| `POST` | `/api/agents/{agentId}/validate` | 保存并返回 publish check report。 |
| `GET` | `/api/agents/{agentId}/publish-checks/latest` | 返回最近一次校验报告。 |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/rollback` | 后续切片实现，保持版本不可变。 |

#### 13.3.6 TDD 顺序

1. 扩展 kernel test memory repo，先为 `AgentPublishCheckRepositoryPort.latest` 写 RED。
2. 实现 port 方法并保持现有 `KernelAgentFactoryServiceTests` 通过。
3. 写 `JdbcAgentFactoryRepositoryAdapterTests`，覆盖 template list/find 和 publish check save/latest。
4. 实现 JDBC adapters 和 `sa_agent_template`、`sa_agent_publish_check` schema。
5. 写 `SeahorseAgentFactoryControllerTests`，覆盖 template list、from-template、validate、latest check。
6. 实现 Web controller。
7. 扩展 `SeahorseAgentRegistryAutoConfigurationTests`，覆盖 repository/service bean 装配和自定义 bean 退让。
8. 后续单独写 rollback RED/GREEN，不与当前集成闭环混在一个提交里。

#### 13.3.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentTemplateTests,KernelAgentFactoryServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentFactoryRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 13.3.8 回滚边界

关闭 factory controller 后，既有 Agent Definition、Version、Run 不受影响；模板和校验表只追加；已创建 draft 作为普通 draft 保留；自定义 `AgentFactoryInboundPort` 可以替换默认 kernel service；rollback 能力未完成前不暴露误导性 API。

### 13.4 Phase 7 详细方案：Governed Local Agent-as-Tool 闭环

#### 13.4.1 当前状态

Phase 7 仍基本未实现。当前代码中可作为前置能力的是 Agent Registry、Run Store、Tool Gateway、Approval、ContextPack、Connector 和 Sandbox port。下一步不应直接做远程 A2A 或 Agent Mesh 控制面，而应先落地本地 Agent-as-Tool 和 handoff 记录。

#### 13.4.2 目标

让一个已发布 Agent 能作为另一个 Agent 的受控工具被调用。调用必须经过 Tool Gateway、Policy、Approval、Context ACL 和 Audit；服务层不得直接互调 child Agent service。完成后，父 run 可以创建 child run，记录 handoff，查询状态，并在 child run 失败时回写结构化失败码。

#### 13.4.3 设计开发方案

| 层 | 变更点 | 设计 |
| --- | --- | --- |
| Domain | `AgentHandoff` | 维护 parent/child run、source/target agent、状态、输入摘要、失败码。 |
| Domain | `AgentHandoffStatus` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。 |
| Domain | `AgentHandoffFailureCode` | `TARGET_NOT_FOUND`、`TARGET_DISABLED`、`TARGET_NOT_PUBLISHED`、`DEPTH_EXCEEDED`、`POLICY_DENIED`、`CHILD_RUN_FAILED`。 |
| Domain | `AgentHandoffContextPolicy` | 裁剪 summary、allowed resource refs、sensitivity 上限。 |
| Outbound port | `AgentHandoffRepositoryPort` | append/update 状态，按 parentRunId/childRunId 查询。 |
| Outbound port | `MeshPolicyPort` | 判断 tenant、深度、target 状态、上下文传递边界。 |
| Application | `KernelAgentHandoffService` | 校验 target agent、裁剪 context、创建 child run、更新 handoff。 |
| Tool adapter | `AgentAsToolPort` | 以 `ToolProvider.AGENT` 暴露目标 Agent，作为普通 tool 被 gateway 调用。 |
| JDBC/Web | `JdbcAgentHandoffRepositoryAdapter`、`SeahorseAgentHandoffController` | 持久化和查询，不提供绕过 Tool Gateway 的创建 API。 |

#### 13.4.4 关键规则

1. handoff 状态只能按 `CREATED -> RUNNING -> SUCCEEDED/FAILED/CANCELLED` 迁移。
2. 最大本地 handoff 深度使用 `MeshPolicyLimits.MAX_LOCAL_HANDOFF_DEPTH`，首版默认 2。
3. source agent 与 target agent 必须同 tenant。
4. target agent 必须 enabled 且存在 published version。
5. context 默认只传 task summary、allowed resource refs 和必要 metadata，不传完整 message history。
6. `ContextSensitivity.SECRET` 永不传递；`CONFIDENTIAL` 传递前必须由 target subject 再次通过 ACL。
7. Agent-as-Tool metadata 必须标识 `ToolProvider.AGENT`、target agentId、target versionId 和风险等级。
8. child run 失败必须回写 `AgentHandoffFailureCode`，不能只依赖 run status。

#### 13.4.5 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-runs/{runId}/handoffs` | 查询 parent run 的 handoff。 |
| `GET` | `/api/agent-handoffs/{handoffId}` | 查询 handoff 详情。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 幂等取消未完成 handoff。 |

创建 handoff 首版不开放独立 Web API，只允许 `AgentAsToolPort` 经 Tool Gateway 触发。

#### 13.4.6 TDD 顺序

1. 写 `AgentHandoffTests` 覆盖必填、状态迁移、cancel 幂等和失败码。
2. 写 `DefaultMeshPolicyPortTests` 覆盖同 tenant published 允许、跨 tenant 拒绝、target disabled 拒绝、超深度拒绝。
3. 写 `AgentHandoffContextPolicyTests` 覆盖 summary-only、secret 丢弃、confidential 重新 ACL。
4. 写 `KernelAgentHandoffServiceTests` 覆盖 child run 创建、handoff 保存、child failure 回写。
5. 写 `AgentAsToolPortTests`，证明调用路径经过 Tool Gateway policy/audit，而不是直接调用 child service。
6. 实现 domain、policy、service、tool adapter。
7. 写 JDBC/Web/starter tests，覆盖 repository、查询 API 和条件装配。

#### 13.4.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,DefaultMeshPolicyPortTests,AgentHandoffContextPolicyTests,KernelAgentHandoffServiceTests,AgentAsToolPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 13.4.8 回滚边界

关闭 Agent-as-Tool provider 后，普通工具、MCP 工具、OpenAPI connector 和普通 run 不受影响；`sa_agent_handoff` 是协作记录表，不改变 `sa_agent_run` 主语义；远程 A2A、Agent Card、remote agent registry 和 Mesh 控制面不在本切片中创建。

### 13.5 Phase 8 详细方案：Audit Ledger 到 Production Gate 最小闭环

#### 13.5.1 当前状态

Phase 8 当前有 Retrieval eval、RateLimiter、Feature health 等分散基础，但缺少统一 append-only Audit Ledger、通用 Agent eval、quota、canary 和 production gate。由于 Phase 4-7 的每个能力都需要审计证据，Phase 8 的首个闭环应先做 Audit Ledger，再把 gate report 接入 Phase 6 publish validation。

#### 13.5.2 目标

建立最小可用的 Audit Ledger 和 Production Gate report：所有关键 Agent 行为都有可查询、脱敏、append-only 的审计事件；发布前可以生成准入报告，检查 owner、published version、工具风险、approval、ACL、audit、eval、quota 和 health。首版允许 eval/quota/health 为 `WARN`，但 owner 缺失、高风险工具无 approval、audit ledger 不可用必须 `FAIL`。

#### 13.5.3 设计开发方案

| 子域 | 变更点 | 设计 |
| --- | --- | --- |
| Audit domain | `AuditEvent` | 不可变事件快照；包含 tenant、actor、run、agent、resource 和 redacted payload。 |
| Audit enum | `AuditEventType`、`AuditActorType`、`AuditWriteFailurePolicy` | 所有事件类型、actor 和写失败策略使用 enum。 |
| Audit redaction | `AuditRedactionPolicy` | 递归脱敏 `secret`、`token`、`password`、`apiKey`、`authorization` 等 key。 |
| Audit ports | `AuditLedgerPort`、`AuditEventRepositoryPort`、`AuditQueryInboundPort` | append、repository、query 拆分，避免大一统接口。 |
| Gate domain | `ProductionGateReport`、`ProductionGateCheckCode`、`ProductionGateCheckStatus` | 生成可保存的准入报告快照。 |
| Gate service | `KernelProductionGateService` | 组合 definition、tool、approval、ACL、audit、eval、quota、health 小 ports。 |
| Bridge ports | `EvaluationStatusPort`、`QuotaStatusPort`、`FeatureHealthStatusPort` | 首版只读状态，不实现完整 eval/quota 平台。 |
| JDBC/Web | `JdbcAuditEventRepositoryAdapter`、`SeahorseAuditEventController`、`SeahorseProductionGateController` | 审计查询与准入报告 API。 |

#### 13.5.4 首批 Audit 事件

| 事件 | 来源 |
| --- | --- |
| `AGENT_PUBLISHED` | AgentDefinition publish/rollback。 |
| `RUN_STARTED`、`RUN_FINISHED` | AgentRun lifecycle。 |
| `TOOL_POLICY_DECIDED`、`TOOL_INVOKED` | Tool Gateway。 |
| `APPROVAL_DECIDED` | Approval Management。 |
| `CONTEXT_ACCESSED` | ContextPack builder / AccessDecision。 |
| `SECRET_USED` | Credential provider。 |
| `CONNECTOR_IMPORTED`、`CONNECTOR_OPERATION_ENABLED` | OpenAPI Connector。 |
| `SANDBOX_SESSION_CREATED`、`SANDBOX_EXECUTION_FINISHED` | Sandbox Runtime。 |
| `AGENT_HANDOFF_CREATED`、`AGENT_HANDOFF_FINISHED` | Phase 7 handoff。 |

#### 13.5.5 Production Gate 规则

1. `OWNER_PRESENT`、`PUBLISHED_VERSION_PRESENT`、`TOOL_RISK_REVIEWED`、`HIGH_RISK_APPROVAL_PRESENT`、`AUDIT_LEDGER_ENABLED` 是强检查。
2. `RESOURCE_ACL_PRESENT` 对只读知识助手可为 `WARN`，对工具执行 Agent 必须为 `FAIL`。
3. `EVAL_PASSING`、`QUOTA_CONFIGURED`、`SRE_HEALTH_GREEN` 首版可为 `WARN`，但必须出现在报告中。
4. Gate service 只生成报告，不直接发布 Agent。
5. Gate report 必须保存为快照，后续策略变化不得改写历史报告。

#### 13.5.6 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/audit-events` | 按 tenant、run、agent、resource、eventType、时间范围查询。 |
| `GET` | `/api/audit-events/{auditId}` | 查询单条审计事件。 |
| `POST` | `/api/agents/{agentId}/production-gate` | 生成 production gate report。 |
| `GET` | `/api/agents/{agentId}/production-gate/latest` | 查询最近一次 report。 |

#### 13.5.7 TDD 顺序

1. 写 `AuditEventTests` 覆盖必填、不变性、resource ref 校验和 append-only 语义。
2. 写 `AuditRedactionPolicyTests` 覆盖嵌套 JSON、数组、大小写 key、secretRef 保留和 secretValue 脱敏。
3. 写 `KernelAuditLedgerServiceTests` 覆盖 append redaction、noop fallback、写失败策略和 query page size 上限。
4. 写 `JdbcAuditEventRepositoryAdapterTests` 覆盖 append、findById、分页、按 run/resource/eventType 查询。
5. 写 `ProductionGateReportTests` 覆盖强检查、warn 检查和状态汇总。
6. 写 `KernelProductionGateServiceTests` 使用 fake ports 验证 owner/tool/approval/audit/eval/quota/health 组合逻辑。
7. 写 Web tests 覆盖 audit query 和 production gate API。
8. 分批把 Phase 3-7 事件接入 Audit Ledger，不一次性修改所有服务。

#### 13.5.8 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AuditEventTests,AuditRedactionPolicyTests,KernelAuditLedgerServiceTests,ProductionGateReportTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAuditEventControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 13.5.9 回滚边界

`sa_audit_event` 和 production gate report 表只追加；关闭 audit auto-configuration 可切回 noop fallback；关闭 production gate controller 不影响 Agent run；eval、quota、health bridge 首版只读状态，不改变对应系统行为；高风险写流程是否 audit fail-closed 由 `AuditWriteFailurePolicy` 控制，不在 repository 中硬编码。

### 13.6 最新推荐执行顺序

1. Phase 6 integration：完成 Agent Factory JDBC/Web/starter，因为 kernel foundation 已经存在，当前 RED 测试已经指向这个切片。
2. Phase 5 integration hardening：补 Sandbox persistence/Web/starter 和 OpenAPI operation enable 安全边界。
3. Phase 8A：Audit Ledger foundation，给 Phase 4-7 后续能力提供统一证据链。
4. Phase 4 Resource Governance hardening：dry-run import、DB constraints 和 ACL 变更审计。
5. Phase 6 publish-ready：rollback API、production gate 接入、Agent Studio API 形态收敛。
6. Phase 7 local Agent-as-Tool：先本地 handoff，再考虑远程 A2A。
7. Phase 8B/8C/8D：Eval、Quota、SRE、Canary 生产化。

### 13.7 架构审查重点

1. Kernel 仍只能依赖 domain、ports 和 JDK。
2. 新增状态、风险、原因、来源、事件类型全部使用 enum 或具名常量。
3. Factory、Sandbox、Audit、Handoff 都通过小 port 组合，不新增大一统 `AgentService`。
4. Repository 只表达持久化契约，不承载策略判断。
5. 新 adapter 必须遵守同一状态语义和版本不可变语义。
6. 默认行为保持保守：disabled、deny、unsupported、dry-run、approval-required。
7. 不引入远程 Agent mesh、复杂工作流引擎或主 JVM 任意脚本执行器。

## 14. 2026-05-26 深读与当前 worktree 校准后的未完成阶段执行方案

本节是在重新深读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、第 13 节、当前 worktree 文件状态和近期 Aegis 记录后追加的最新方案。它的优先级高于第 13 节中已经被实现进展更新掉的判断。

当前校准事实：

1. Phase 0-2 的基线、Agent Registry/Run Store、Tool Gateway/Policy 主干已经有实现基础，本节不再为它们新增“未完成阶段方案”，只在后续阶段引用这些 owner。
2. Phase 3 已不只是交接文档中的“Approval request 最小切片”：审批查询/决策 API、checkpoint repository、WAITING_APPROVAL checkpoint、resume、lease port/JDBC/starter 都已出现。剩余重点转为 worker 运营化、幂等恢复和状态一致性硬化。
3. Phase 4 的 Resource ACL 单条管理闭环已有实现痕迹，剩余重点是批量 dry-run、约束、provenance 与审计接入。
4. Phase 5 的 OAuth、OpenAPI import/operation enable 和 sandbox kernel foundation 已出现，剩余重点是 sandbox JDBC/Web/starter 外层与 artifact 安全出站。
5. Phase 6 的 Agent Factory kernel/JDBC/Web/starter integration 已出现，剩余重点不再是模板集成，而是 latest publish check 查询、rollback、production gate bridge 与 Studio API 收敛。
6. Phase 7 仍基本未实现，必须先做本地 Agent-as-Tool 与 handoff，不直接做远程 A2A mesh。
7. Phase 8 仍缺统一 Audit Ledger、Production Gate、Agent eval/quota/SRE/canary 闭环，应优先做 Audit Ledger foundation。

本节每个未完成阶段只给一个更具体的设计开发方案；每个方案仍遵守 ports/adapters、小接口、默认保守、TDD、enum/具名常量、kernel 不依赖 Spring/JDBC/Web 的边界。

### 14.1 Phase 3 方案：Durable Runtime Worker Hardening 闭环

#### 14.1.1 当前判断

Phase 3 已具备审批、checkpoint、resume 和 lease 的基础，但还不能宣称 Durable Runtime 完成。当前剩余风险集中在“后台 worker 如何稳定推进 run”：可运行 run 的选择、lease 抢占、审批恢复后的幂等执行、retry/cancel 状态一致性、以及 checkpoint/replay 证据是否足以诊断失败。

#### 14.1.2 目标

实现一个最小 worker tick 闭环：worker 每次只领取少量 `CREATED/RUNNING/RETRYING` run，先获取 lease，再执行一个 bounded step 或 resume 操作；遇到 `WAITING_APPROVAL` 立即释放执行权；审批通过后只能从 checkpoint 恢复，不允许审批 API 直接执行工具。完成后，多实例 worker 不能双执行同一 run，取消和重试具备幂等语义。

#### 14.1.3 模块与文件边界

| 层 | 文件/对象 | 设计 |
| --- | --- | --- |
| Domain | `AgentRunWorkerOutcome` | enum：`CLAIMED`、`NO_RUNNABLE_RUN`、`WAITING_APPROVAL`、`LEASE_CONFLICT`、`STEP_COMPLETED`、`RUN_FINISHED`、`RUN_FAILED`。 |
| Domain | `AgentRunDispatchDecision` | 小值对象，记录 worker 本轮为何执行、跳过或释放 run。 |
| Inbound port | `AgentRunWorkerInboundPort` | `tick(AgentRunWorkerCommand)`，不暴露大一统 runtime service。 |
| Inbound command | `AgentRunWorkerCommand` | `workerId`、`tenantId`、`maxRuns`、`leaseTtl`、`now`。 |
| Outbound port | `AgentRunQueueRepositoryPort` | 只负责查找可运行 run：`findRunnable(tenantId, limit, now)`；不承载状态机判断。 |
| Existing port | `AgentRunLeaseRepositoryPort` | 继续负责 acquire/heartbeat/release/find，不合并到 run repository。 |
| Application | `KernelAgentRunWorkerService` | 编排队列查询、lease、单步执行、checkpoint、status update。 |
| Application | `KernelAgentRunResumeService` | 保持审批恢复 owner；worker 只调用 resume port，不复制恢复逻辑。 |
| JDBC | `JdbcAgentRunQueueRepositoryAdapter` | 查询 `CREATED/RUNNING/RETRYING` 且无有效 lease 的 run。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration` | 条件装配 worker service；默认不自动开启后台线程。 |

#### 14.1.4 关键规则

1. `WAITING_APPROVAL`、`SUCCEEDED`、`FAILED`、`REJECTED`、`EXPIRED`、`CANCELLED` 不得被 worker 选为 runnable。
2. worker 执行前必须成功 acquire lease；lease 冲突只返回 `LEASE_CONFLICT`，不重试执行。
3. `AgentRunStatus.isWorkerRunnable()` 是领域层唯一可运行状态判断，查询 SQL 只做候选过滤。
4. 审批通过后的工具执行必须通过 `KernelAgentRunResumeService` 从 latest `WAITING_APPROVAL` checkpoint 恢复。
5. 拒绝、过期或已取消的审批不得恢复工具执行。
6. retry 只允许从 `FAILED` 到 `RETRYING`，并必须写新的 checkpoint 或 retry reason。
7. cancel 幂等；已终态 run 再 cancel 返回当前终态，不生成新 step。
8. 每次 worker tick 最多处理 `maxRuns`，默认值使用具名常量 `AgentRunWorkerLimits.DEFAULT_MAX_RUNS_PER_TICK`。

#### 14.1.5 TDD 顺序

1. 写 `AgentRunWorkerServiceTests`：无 runnable run、lease 冲突、成功 claim、遇到 WAITING_APPROVAL 不执行、approved checkpoint resume、failed retry。
2. 写 `AgentRunQueueRepositoryPortTests` 的内存 fake，先证明 service 不依赖 JDBC。
3. 写 `JdbcAgentRunQueueRepositoryAdapterTests`，覆盖 runnable 状态、lease 过期接管、tenant 过滤和排序。
4. 实现 `AgentRunWorkerInboundPort`、command、outcome enum 和 `KernelAgentRunWorkerService`。
5. 实现 JDBC queue adapter 与 starter wiring。
6. 扩展 Web 或 actuator 前先不做自动后台线程，只保留服务 bean 和测试驱动入口。
7. 跑 Phase 3 focused regression，确认既有 approval/checkpoint/resume/lease 不回退。

#### 14.1.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelApprovalManagementServiceTests,KernelAgentRunResumeServiceTests,AgentRunWorkerServiceTests,KernelAgentRunLeaseServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcToolApprovalRequestRepositoryAdapterTests,JdbcAgentCheckpointRepositoryAdapterTests,JdbcAgentRunLeaseRepositoryAdapterTests,JdbcAgentRunQueueRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 14.1.7 回滚边界

worker service 可单独不装配；approval、checkpoint、resume API 保持可用；新增 queue repository 只读候选 run，不改变 run/step 表主语义；如果 worker 被关闭，现有同步 AgentLoop 路径仍可继续返回 `WAITING_APPROVAL`。

### 14.2 Phase 4 方案：Resource ACL Dry-run 与 Context Provenance 硬化

#### 14.2.1 当前判断

Phase 4 已具备 `ResourceAclRule`、ACL-backed policy、JDBC 仓储、Web 管理入口和 audited resource access wrapper。剩余问题不在“能不能单条授权”，而在企业运营场景：批量规则导入前如何发现冲突，数据库如何阻止非法 enum/重复有效规则，ContextPack/AccessDecision 如何给审计账本提供稳定证据。

#### 14.2.2 目标

提供 Resource ACL 批量 dry-run 方案，不直接批量写库；管理员可以上传一组候选规则并获得逐条报告。与此同时补强 repository natural key 查询、DB `CHECK`/索引约束和 Context provenance 摘要，使后续 Audit Ledger 能回答“哪个 Agent 因为什么 ACL 决策看到了哪个资源”。

#### 14.2.3 模块与文件边界

| 层 | 文件/对象 | 设计 |
| --- | --- | --- |
| Domain | `ResourceAclImportDryRunReport` | 汇总 valid/invalid/duplicate/conflict/unsupported 数量和逐条结果。 |
| Domain | `ResourceAclImportItem` | 单条候选规则快照，不复用可持久化 `ResourceAclRule`。 |
| Enum | `ResourceAclImportItemStatus` | `VALID`、`INVALID`、`DUPLICATE_IN_BATCH`、`DUPLICATE_EXISTING`、`CONFLICT`、`UNSUPPORTED_SCOPE`。 |
| Enum | `ResourceAclImportReasonCode` | `VALID_RULE`、`EXPIRED_INPUT`、`MISSING_SUBJECT`、`NATURAL_KEY_DUPLICATE`、`DENY_ALLOW_CONFLICT` 等。 |
| Inbound port | `ResourceAclManagementInboundPort` | 增加 `dryRunImport(ResourceAclImportDryRunCommand)`。 |
| Outbound port | `ResourceAclRepositoryPort` | 增加 natural key 查询，不把冲突判断塞进 repository。 |
| Application | `KernelResourceAclManagementService` | 编排输入校验、批内查重、库内查重、冲突报告。 |
| JDBC | `JdbcResourceAclRepositoryAdapter` | natural key 查询、enum 约束、查重索引。 |
| Web | `SeahorseResourceAclController` | `POST /api/resource-acl-rules:dry-run-import`。 |
| Audit bridge | Phase 8 `AuditLedgerPort` | dry-run 只记录摘要，真实 create/disable 记录 `RESOURCE_ACL_CHANGED`。 |

#### 14.2.4 关键规则

1. dry-run 永不写入 `sa_resource_acl_rule`。
2. natural key 使用 tenant、scope、resourceType、resourceId、subjectType、subjectId、action，不包含 priority。
3. 批内重复优先于库内重复报告，便于用户先修输入文件。
4. 同一 natural key 出现 `ALLOW` 与 `DENY` 时报告 `CONFLICT`；运行时仍遵守现有 deny-wins policy。
5. `ResourceAclRuleScope.RESOURCE_TYPE` 首版只报告 `UNSUPPORTED_SCOPE`，不开放写入。
6. 过期输入不自动转换成 disabled rule，避免“导入成功但立即无效”的误导。
7. ContextPack 的每个 item 必须能关联 `aclDecisionId` 或明确的 deny/mask reason；没有决策证据的 item 不进入 prompt。

#### 14.2.5 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/resource-acl-rules:dry-run-import` | 对候选 ACL 规则做预检，不写库。 |
| `GET` | `/api/access-decisions` | 后续 Phase 8 前保持现有查询或只读 bridge，不强行扩展审计账本。 |

响应必须包含 `items[index,status,reasonCode,naturalKey]`，不返回 SQL、内部表名或批量 commit token。

#### 14.2.6 TDD 顺序

1. 写 `ResourceAclImportDryRunTests` 覆盖合法、批内重复、库内重复、allow/deny 冲突、过期输入、unsupported scope。
2. 写 `KernelResourceAclManagementServiceTests` 的 dry-run 分支，验证非 admin 拒绝、dry-run 不调用 save。
3. 写 `JdbcResourceAclRepositoryAdapterTests` 覆盖 natural key 查询和 enum/check 约束映射。
4. 写 `SeahorseResourceAclControllerTests` 覆盖 API 合约和无写入副作用。
5. 实现 domain/command/report、repository 查询、controller。
6. Phase 8 Audit Ledger 完成后，补 `RESOURCE_ACL_CHANGED` 和 `CONTEXT_ACCESSED` 接入测试。

#### 14.2.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests,AuditedResourceAccessPolicyPortTests,ResourceAclImportDryRunTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 14.2.8 回滚边界

dry-run API 可单独关闭，不影响现有 create/page/disable；新增 DB 约束只作用于 ACL 表；ContextPack 调用方仍依赖 `ResourceAccessPolicyPort`，不感知 ACL repository；Audit Ledger 未完成前保留 bridge，不阻塞 ACL 主闭环。

### 14.3 Phase 5 方案：Sandbox Persistence/Web/Starter 与 Connector 安全启用闭环

#### 14.3.1 当前判断

Phase 5 的 OAuth、OpenAPI import、operation enable 和 sandbox kernel foundation 已经出现。剩余最大的生产风险是 sandbox 仍停留在 kernel foundation：session/execution/artifact 需要外层持久化和查询；Web/starter 需要默认 fail-closed wiring；artifact 出站需要扫描状态和敏感级别双重判断。OpenAPI operation enable 还需要补 disable、credential binding 和审计接入。

#### 14.3.2 目标

把 Secure Connector Runtime 接成可运营闭环：OpenAPI operation import 后默认 disabled，启用/停用都显式可查；sandbox 默认 runtime 为 `unsupported()`，但 session/execution/artifact 即使失败也可持久化、查询和审计；只有外部 runtime adapter 可以提供真实执行能力，主 JVM 不执行 shell、browser 或 code。

#### 14.3.3 模块与文件边界

| 层 | 文件/对象 | 设计 |
| --- | --- | --- |
| Outbound port | `SandboxSessionRepositoryPort` | save/find/update status，记录 tenant/run/runtime/status。 |
| Outbound port | `SandboxExecutionRepositoryPort` | save/find/listBySession，记录 command summary、status、reason。 |
| Outbound port | `SandboxArtifactQueryPort` | listBySession/listPromptVisible，不和 `SandboxArtifactPort.save()` 混成大接口。 |
| Outbound port | `SandboxArtifactScanPort` | 首版 noop-safe scanner，返回 `PENDING` 或 `CLEAN`，不能默认放行 secret。 |
| Inbound port | `SandboxRuntimeInboundPort` | 增加 `close(sessionId)`、`listArtifacts(sessionId)`，保持 create/execute 小接口。 |
| Application | `KernelSandboxRuntimeService` | 组合 policy、runtime、repository、artifact port；默认 fail-closed。 |
| JDBC | `JdbcSandboxRepositoryAdapter` | 管理 `sa_sandbox_session`、`sa_sandbox_execution`、`sa_sandbox_artifact`。 |
| Web | `SeahorseSandboxController` | create/execute/close/list artifacts API。 |
| Starter | registry/kernel auto-config | 条件装配 sandbox repositories、`DefaultSandboxPolicyPort`、`SandboxRuntimePort.unsupported()`、runtime service。 |
| Connector | `ConnectorOperationDisableCommand` | 补齐 operation disable；enable 后仍必须走 Tool Gateway。 |

#### 14.3.4 关键规则

1. `SandboxNetworkPolicy.DENY` 是默认网络策略。
2. `SandboxRuntimePort.unsupported()` 是默认 runtime，必须返回结构化失败，不能执行本地命令。
3. terminal session 不能继续 execute。
4. execution 状态迁移只能按 domain enum 允许的路径进行。
5. artifact 只有 `SandboxArtifactScanStatus.CLEAN` 且 sensitivity 非 `SECRET` 才能 prompt-visible。
6. OpenAPI import 只注册 disabled operation；enable 必须重新校验 risk、approval 和 credential binding。
7. token、secret、authorization header 不得进入 connector/sandbox response、audit payload 或异常消息。
8. 所有 sandbox/connector 状态、原因、触发来源使用 enum 或具名常量。

#### 14.3.5 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/enable` | 显式启用 operation。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/disable` | 幂等停用 operation。 |
| `POST` | `/api/sandbox/sessions` | 创建 session；默认可能返回 denied/unsupported。 |
| `POST` | `/api/sandbox/sessions/{sessionId}/execute` | 执行 sandbox command；默认 fail-closed。 |
| `POST` | `/api/sandbox/sessions/{sessionId}/close` | 幂等关闭 session。 |
| `GET` | `/api/sandbox/sessions/{sessionId}/artifacts` | 只返回可见 artifact metadata，不返回 secret 内容。 |

#### 14.3.6 TDD 顺序

1. 写 `JdbcSandboxRepositoryAdapterTests`，覆盖 session/execution/artifact save/find/list、terminal 状态和 prompt-visible 查询。
2. 写 `SeahorseSandboxControllerTests`，覆盖 create/execute/close/artifacts API，默认 unsupported 也要结构化返回。
3. 写 starter auto-config tests，覆盖默认 `SandboxRuntimePort.unsupported()`、自定义 runtime 退让、repository bean 装配。
4. 扩展 `KernelSandboxRuntimeServiceTests`，验证 repository 持久化副作用和 artifact scan/query 规则。
5. 写 `ConnectorOperationDisableTests`，覆盖 enable/disable 幂等和未绑定 credential 不能启用。
6. 实现 sandbox repositories、controller、starter wiring 和 connector disable。
7. Phase 8 Audit Ledger 完成后，补 `SANDBOX_SESSION_CREATED`、`SANDBOX_EXECUTION_FINISHED`、`CONNECTOR_OPERATION_ENABLED` 审计测试。

#### 14.3.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=DefaultSandboxPolicyPortTests,KernelSandboxRuntimeServiceTests,SandboxPolicyDecisionTests,SandboxExecutionTests,SandboxArtifactTests,ConnectorOperationEnableTests,ConnectorOperationDisableTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcConnectorRepositoryAdapterTests,JdbcSandboxRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseOpenApiConnectorControllerTests,SeahorseSandboxControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentSandboxAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 14.3.8 回滚边界

sandbox controller 和 repository 可独立关闭；默认 runtime 仍 fail-closed；connector operation 可保持 disabled；artifact 表只存 metadata/object URI，不承载真实 secret；自定义 sandbox runtime adapter 可以替换默认 unsupported 实现，但必须遵守相同状态语义。

### 14.4 Phase 6 方案：Agent Factory Publish-ready、Rollback 与 Gate Bridge 闭环

#### 14.4.1 当前判断

Phase 6 的 template list、from-template draft、publish validation、JDBC/Web/starter integration 已出现。剩余任务不再是“打通模板仓储”，而是把 Factory 变成可发布运营能力：latest publish check 查询、rollback、Production Gate bridge、Agent Catalog/Studio API 形态收敛，以及模板 seed/禁用策略。

#### 14.4.2 目标

完成 “validate -> latest check query -> production gate bridge -> rollback -> catalog view” 最小闭环。Factory 仍只组合 `AgentDefinitionInboundPort`、tool/catalog/policy/gate ports，不复制 AgentDefinition/AgentVersion 不变量。rollback 不改写历史 `AgentVersion`，只生成新的激活记录或更新 published pointer，并写入审计事件。

#### 14.4.3 模块与文件边界

| 层 | 文件/对象 | 设计 |
| --- | --- | --- |
| Inbound command | `AgentVersionRollbackCommand` | agentId、versionId、operator、reason。 |
| Domain | `AgentRollbackResult` | 记录 previousVersionId、targetVersionId、status、reason。 |
| Enum | `AgentRollbackReasonCode` | `OPERATOR_REQUESTED`、`GATE_FAILED`、`CANARY_FAILED`、`INCIDENT_RESPONSE`。 |
| Outbound port | `AgentVersionActivationRepositoryPort` | 激活既有 immutable version，不修改 version 快照。 |
| Outbound port | `ProductionGatePort` | Phase 8 前使用 noop/warn bridge；Phase 8 完成后组合真实 report。 |
| Existing port | `AgentPublishCheckRepositoryPort` | latest 查询必须暴露到 Web。 |
| Application | `KernelAgentFactoryService` | 增加 latest check、rollback、gate report 组合。 |
| Web | `SeahorseAgentFactoryController` | 补 `GET /api/agents/{agentId}/publish-checks/latest` 和 rollback API。 |
| JDBC | `JdbcAgentPublishCheckRepositoryAdapter`、activation adapter | 保存 check/gate/rollback 快照或 pointer 变更。 |
| Starter | registry auto-config | 条件装配 gate noop bridge 和 activation adapter。 |

#### 14.4.4 关键规则

1. published `AgentVersion` 永不可变，rollback 不 update version 内容。
2. rollback 只能指向同 tenant、同 agent、已发布且未损坏的 version。
3. rollback 需要 operator 和 reason，不允许匿名系统调用。
4. latest publish check 查询只读，不触发重新校验。
5. Production Gate 在 Phase 8 前可以返回 `WARN`，但 `OWNER_PRESENT`、`HIGH_RISK_APPROVAL_PRESENT`、`TOOL_RISK_REVIEWED` 不能被跳过。
6. from-template 仍只能创建 `DRAFT`，不得直接发布。
7. 模板禁用后不影响已创建 draft，但不能再创建新 draft。
8. Agent Catalog 只展示用户/tenant 有权看到且 enabled/published 的 Agent。

#### 14.4.5 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-templates` | 已有，返回模板。 |
| `POST` | `/api/agents/from-template` | 已有，从模板创建 draft。 |
| `POST` | `/api/agents/{agentId}/validate` | 已有，保存 publish check。 |
| `GET` | `/api/agents/{agentId}/publish-checks/latest` | 新增，只读最近校验。 |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/rollback` | 新增，激活旧版本并记录原因。 |
| `GET` | `/api/agent-catalog` | 后续 Studio/API 收敛，只返回可调用 Agent。 |

#### 14.4.6 TDD 顺序

1. 扩展 `KernelAgentFactoryServiceTests`，覆盖 latest check 查询、gate WARN 组合、rollback 成功、rollback 目标不存在/非同 agent 拒绝。
2. 写 `AgentVersionActivationRepositoryPort` fake，证明 service 不依赖 JDBC。
3. 扩展 `JdbcAgentFactoryRepositoryAdapterTests`，覆盖 latest check 和 activation/pointer 保存。
4. 扩展 `SeahorseAgentFactoryControllerTests`，覆盖 latest API 和 rollback API。
5. 扩展 starter tests，覆盖 `ProductionGatePort` noop bridge、自定义 gate 退让和 activation adapter 装配。
6. 实现 command/domain/port/service/controller/JDBC。
7. Phase 8 完成 Audit Ledger 后，补 `AGENT_PUBLISHED`、`AGENT_ROLLED_BACK` 审计接入。

#### 14.4.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentTemplateTests,KernelAgentFactoryServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentFactoryRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 14.4.8 回滚边界

关闭 Factory controller 不影响普通 Agent Definition/Run；rollback 只改变激活指针或新增激活记录，不删除历史 version；Production Gate bridge 是可替换 port；模板表和 publish check 表只追加或软禁用。

### 14.5 Phase 7 方案：Governed Local Agent-as-Tool 与 Handoff 记录闭环

#### 14.5.1 当前判断

Phase 7 仍未形成代码主线。现有 `ToolProvider.REMOTE_AGENT` 只能作为未来兼容提示，不代表本地 Agent-as-Tool 或远程 A2A 已完成。按架构基线，Phase 7 必须先做本地受治理 handoff：父 run 通过 Tool Gateway 调用目标 Agent，创建 child run，保存 handoff 记录，裁剪上下文，并接受 Policy/Approval/Audit 约束。

#### 14.5.2 目标

让一个已发布目标 Agent 能被包装为普通 tool 调用。调用路径必须是 `AgentLoop -> ToolGateway -> AgentAsToolPort -> HandoffService -> AgentRunInboundPort`，不得由 service 直接互调绕过 Tool Gateway。完成后可以按 parent run 查询 handoff，child run 失败能回写结构化失败码。

#### 14.5.3 模块与文件边界

| 层 | 文件/对象 | 设计 |
| --- | --- | --- |
| Domain | `AgentHandoff` | parentRunId、childRunId、sourceAgentId、targetAgentId、status、reason、failureCode。 |
| Enum | `AgentHandoffStatus` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。 |
| Enum | `AgentHandoffFailureCode` | `TARGET_NOT_FOUND`、`TARGET_DISABLED`、`TARGET_NOT_PUBLISHED`、`DEPTH_EXCEEDED`、`POLICY_DENIED`、`CHILD_RUN_FAILED`。 |
| Domain | `AgentHandoffContextPolicy` | summary-only、allowed resource refs、sensitivity cap。 |
| Outbound port | `AgentHandoffRepositoryPort` | save/update/listByParent/findById。 |
| Outbound port | `MeshPolicyPort` | 只判断本地 source/target/tenant/depth/context，不接远程 mesh。 |
| Application | `KernelAgentHandoffService` | 校验 target、裁剪 context、创建 child run、更新 handoff。 |
| Tool adapter | `AgentAsToolPort` | 注册 `ToolProvider.REMOTE_AGENT` 或后续新增 `ToolProvider.AGENT` 前保持兼容；不直接调用子服务。 |
| JDBC | `JdbcAgentHandoffRepositoryAdapter` | `sa_agent_handoff`。 |
| Web | `SeahorseAgentHandoffController` | 查询与取消，不提供绕过 Gateway 的创建 API。 |

#### 14.5.4 关键规则

1. 创建 handoff 首版只允许由 Agent-as-Tool 触发，不开放 `POST /api/agent-handoffs`。
2. source/target 必须同 tenant。
3. target 必须 enabled 且有 published version。
4. 最大本地 handoff 深度使用 `MeshPolicyLimits.MAX_LOCAL_HANDOFF_DEPTH`，默认 2。
5. context 默认只传 task summary 和 allowed resource refs，不传完整 message history。
6. `SECRET` context 永不传递；`CONFIDENTIAL` 必须按 target subject 重新 ACL。
7. child run 失败要回写 `AgentHandoffFailureCode`，不能只靠 `AgentRunStatus.FAILED`。
8. 远程 A2A、Agent Card、remote registry、routing、circuit breaker 不在本方案实现。

#### 14.5.5 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-runs/{runId}/handoffs` | 查询 parent run 的 handoff。 |
| `GET` | `/api/agent-handoffs/{handoffId}` | 查询 handoff 详情。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 幂等取消未完成 handoff。 |

#### 14.5.6 TDD 顺序

1. 写 `AgentHandoffTests` 覆盖必填、状态迁移、cancel 幂等和失败码。
2. 写 `DefaultMeshPolicyPortTests` 覆盖同 tenant published 允许、跨 tenant 拒绝、target disabled、超深度拒绝。
3. 写 `AgentHandoffContextPolicyTests` 覆盖 summary-only、secret 丢弃、confidential 重新 ACL。
4. 写 `KernelAgentHandoffServiceTests` 覆盖 child run 创建、handoff 保存、child failure 回写。
5. 写 `AgentAsToolPortTests`，证明调用路径经过 Tool Gateway policy/audit。
6. 写 JDBC/Web/starter tests。
7. 实现 domain、policy、service、tool adapter、repository、controller。

#### 14.5.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,DefaultMeshPolicyPortTests,AgentHandoffContextPolicyTests,KernelAgentHandoffServiceTests,AgentAsToolPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 14.5.8 回滚边界

关闭 Agent-as-Tool provider 后，普通 tools/MCP/OpenAPI 不受影响；`sa_agent_handoff` 只是关系记录，不改变 `sa_agent_run` 主语义；远程 A2A 不创建任何外部入口；context 裁剪策略可以保守收紧而不破坏 run store。

### 14.6 Phase 8 方案：Audit Ledger Foundation 与 Production Gate Bridge 闭环

#### 14.6.1 当前判断

Phase 8 目前只有 retrieval eval、rate limiter、feature health 等分散基础，缺少统一 append-only Audit Ledger 和可保存的 Production Gate report。Phase 4-7 后续每个能力都依赖审计证据，因此 Phase 8 的首个方案应先建立审计账本，再把 gate bridge 接入 Phase 6 publish validation；完整 eval/quota/SRE/canary 分后续 B/C/D 切片。

#### 14.6.2 目标

建立最小可用 Audit Ledger：关键 Agent 行为都能 append、脱敏、查询；写失败策略明确；审计 event 不等同调试日志。然后建立 Production Gate bridge：发布前生成可保存 report，强检查 owner、published version、tool risk、approval、audit 可用性；eval/quota/health 首版可为 `WARN`，但必须出现在报告中。

#### 14.6.3 模块与文件边界

| 层 | 文件/对象 | 设计 |
| --- | --- | --- |
| Domain | `AuditEvent` | 不可变事件快照，包含 tenant、actor、run、agent、resource、redactedPayload。 |
| Enum | `AuditEventType` | `AGENT_PUBLISHED`、`RUN_STARTED`、`TOOL_INVOKED`、`APPROVAL_DECIDED`、`CONTEXT_ACCESSED` 等。 |
| Enum | `AuditActorType` | `USER`、`AGENT`、`SYSTEM`、`WORKER`。 |
| Enum | `AuditWriteFailurePolicy` | `FAIL_CLOSED`、`WARN_AND_CONTINUE`、`NOOP`。 |
| Domain service | `AuditRedactionPolicy` | 递归脱敏 `secret`、`token`、`password`、`apiKey`、`authorization` 等 key。 |
| Outbound port | `AuditLedgerPort` | append 单事件；不承载查询。 |
| Outbound port | `AuditEventRepositoryPort` | save/find/page，JDBC 专属持久化契约。 |
| Inbound port | `AuditQueryInboundPort` | 管理端查询。 |
| Domain | `ProductionGateReport` | 可保存的发布准入报告快照。 |
| Enum | `ProductionGateCheckCode` | `OWNER_PRESENT`、`HIGH_RISK_APPROVAL_PRESENT`、`AUDIT_LEDGER_ENABLED` 等。 |
| Outbound bridge | `EvaluationStatusPort`、`QuotaStatusPort`、`FeatureHealthStatusPort` | 首版只读状态，不实现完整平台。 |
| Application | `KernelProductionGateService` | 组合小 ports，生成 report，不直接发布 Agent。 |
| JDBC/Web/Starter | `JdbcAuditEventRepositoryAdapter`、controllers、auto-config | 审计查询、gate API、默认 noop bridge。 |

#### 14.6.4 首批接入事件

| 事件 | 接入来源 |
| --- | --- |
| `RUN_STARTED`、`RUN_FINISHED` | Agent run lifecycle。 |
| `TOOL_POLICY_DECIDED`、`TOOL_INVOKED` | Tool Gateway。 |
| `APPROVAL_DECIDED` | Approval service。 |
| `CONTEXT_ACCESSED`、`RESOURCE_ACL_CHANGED` | Phase 4。 |
| `SECRET_USED` | Credential provider，只记录 secretRef。 |
| `CONNECTOR_IMPORTED`、`CONNECTOR_OPERATION_ENABLED` | Phase 5 connector。 |
| `SANDBOX_SESSION_CREATED`、`SANDBOX_EXECUTION_FINISHED` | Phase 5 sandbox。 |
| `AGENT_PUBLISH_VALIDATED`、`AGENT_ROLLED_BACK` | Phase 6。 |
| `AGENT_HANDOFF_CREATED`、`AGENT_HANDOFF_FINISHED` | Phase 7。 |

#### 14.6.5 Production Gate 强弱规则

1. `OWNER_PRESENT`、`PUBLISHED_VERSION_PRESENT`、`TOOL_RISK_REVIEWED`、`HIGH_RISK_APPROVAL_PRESENT`、`AUDIT_LEDGER_ENABLED` 是 `FAIL` 级强检查。
2. `RESOURCE_ACL_PRESENT` 对只读低风险知识助手可为 `WARN`，对 tool-execution Agent 必须为 `FAIL`。
3. `EVAL_PASSING`、`QUOTA_CONFIGURED`、`SRE_HEALTH_GREEN` 首版可为 `WARN`，但不得缺失。
4. Gate service 只生成 report，不直接发布或回滚 Agent。
5. Gate report 是快照，策略变化不得改写历史 report。

#### 14.6.6 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/audit-events` | 按 tenant、run、agent、resource、eventType、时间范围查询。 |
| `GET` | `/api/audit-events/{auditId}` | 查询单条审计事件。 |
| `POST` | `/api/agents/{agentId}/production-gate` | 生成并保存 gate report。 |
| `GET` | `/api/agents/{agentId}/production-gate/latest` | 查询最近一次 gate report。 |

#### 14.6.7 TDD 顺序

1. 写 `AuditEventTests` 覆盖必填、不变性、resource ref 和 event type enum。
2. 写 `AuditRedactionPolicyTests` 覆盖嵌套 JSON、数组、大小写 key、secretRef 保留、secretValue 脱敏。
3. 写 `KernelAuditLedgerServiceTests` 覆盖 append、redaction、noop fallback、写失败策略。
4. 写 `JdbcAuditEventRepositoryAdapterTests` 覆盖 append/find/page/run/resource/eventType 查询。
5. 写 `ProductionGateReportTests` 覆盖强检查、warn 检查、总体状态。
6. 写 `KernelProductionGateServiceTests` 使用 fake ports 验证 owner/tool/approval/audit/eval/quota/health 组合。
7. 写 Web/starter tests。
8. 分批接入 Phase 3-7 事件，不一次性修改所有服务。

#### 14.6.8 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AuditEventTests,AuditRedactionPolicyTests,KernelAuditLedgerServiceTests,ProductionGateReportTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAuditEventRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAuditEventControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 14.6.9 回滚边界

Audit Ledger 表只追加；auto-config 可退回 noop/warn bridge；Production Gate controller 可独立关闭，不影响 run；eval/quota/health 首版是只读 bridge，不改变对应系统行为；高风险写流程是否 audit fail-closed 由 `AuditWriteFailurePolicy` 决定，repository 不硬编码策略。

### 14.7 最新推荐执行顺序

1. Phase 5 Sandbox persistence/Web/starter：当前最直接缺口，补外层闭环但保持默认 unsupported/fail-closed。
2. Phase 8A Audit Ledger foundation：为 Phase 3-7 后续硬化提供统一证据链。
3. Phase 4 Resource ACL dry-run 与 provenance：补批量运营质量和后续 `CONTEXT_ACCESSED` 审计接入。
4. Phase 6 Publish-ready：补 latest publish check、rollback 和 Production Gate bridge。
5. Phase 3 Worker hardening：补 worker tick、lease 接管、retry/cancel 幂等和恢复一致性。
6. Phase 7 Local Agent-as-Tool：先本地 handoff，再考虑远程 A2A。
7. Phase 8B/8C/8D：Agent eval、quota/cost、SRE health、canary 和企业试点准入面板。

### 14.8 本节执行约束

1. 每个阶段先写 RED 测试，再做最小 GREEN。
2. 每个阶段只实现当前验收所需最小闭环，不引入工作流引擎、远程 mesh 或复杂 JSON 类型。
3. 所有新状态、事件、原因、来源、风险等级必须是 enum 或具名常量。
4. Kernel 只依赖 domain、ports 和 JDK；Web/JDBC/Spring 只能作为 adapter。
5. Repository 只负责持久化契约，不承载策略判断。
6. 默认行为继续保守：deny、disabled、unsupported、dry-run、approval-required、warn bridge。
7. 任何阶段接入 Audit 时都必须先经过 `AuditRedactionPolicy`，不得记录明文 secret/token。

## 15. 2026-05-26 当前完成进度后的剩余阶段精细设计开发方案

本节是在深读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、当前 `09` 文档第 14 节、`99-current-implementation-handoff.md`、以及当前 worktree 代码与 Aegis 证据记录后补充的执行级方案。它不替换第 14 节，而是把已经完成的 Phase 4、Phase 5 Sandbox、Phase 8A 前置切片从剩余工作中剥离出来，避免后续开发重复设计。

### 15.0 当前完成度校准

| 阶段 | 当前判断 | 是否需要本节新增方案 |
| --- | --- | --- |
| Phase 0-2 | 架构基线、Agent Registry/Run Store、Tool Gateway/Policy 主干已有实现基础。 | 不需要。后续只作为 owner 引用。 |
| Phase 3 | Approval query/decision、checkpoint、WAITING_APPROVAL、resume、lease 已出现；worker tick、队列领取、retry/cancel 运营语义仍未闭环。 | 需要。见 15.1。 |
| Phase 4 | Resource ACL dry-run、natural key 查询、Context provenance floor 已完成当前验收边界。 | 不新增 Phase 4 方案；剩余审计事件接入归入 Phase 8B。 |
| Phase 5 | Sandbox persistence/Web/starter 已完成；connector disable、credential binding、connector/sandbox 审计接入仍是 Phase 5 余项。 | 需要。见 15.2。 |
| Phase 6 | Factory template/from-template/validate 基础已出现；latest check API、rollback、catalog view、gate bridge 收敛仍未完成。 | 需要。见 15.3。 |
| Phase 7 | 本地 Agent-as-Tool、handoff 记录、context handoff policy、mesh policy 基本未形成代码主线。 | 需要。见 15.4。 |
| Phase 8 | Audit Ledger foundation 和 Production Gate foundation 已出现；Agent eval、quota/cost、SRE health、canary/enterprise pilot gate 仍未完成。 | 需要。见 15.5。 |

本节执行顺序应为：Phase 6 Publish-ready -> Phase 3 Worker hardening -> Phase 5 connector residual -> Phase 7 Local Agent-as-Tool -> Phase 8B/C/D。若实施时发现 Phase 6 rollback 依赖未具备的 version activation/pointer 表，则先补 Phase 6 的 activation repository，不回退到修改 `AgentVersion` 快照。

### 15.1 Phase 3 精细方案：Durable Runtime Worker Hardening

#### 15.1.1 目标边界

把已有审批、checkpoint、resume、lease 组合成可运营的 worker tick。该方案只做数据库轮询 worker，不引入 MQ 编排、工作流引擎或后台线程自动调度框架；starter 只装配 service bean，是否定时调用由上层应用或运维入口控制。

完成后应满足：

1. worker 一次 tick 可以领取有限数量 run。
2. 同一 run 同一时刻只能被一个 worker 执行。
3. `WAITING_APPROVAL` run 不会被误执行。
4. approval approve 后通过 resume owner 从 checkpoint 恢复。
5. retry/cancel 只在领域允许状态迁移内执行，并且幂等。

#### 15.1.2 领域对象与端口

| 类型 | 名称 | 设计职责 |
| --- | --- | --- |
| enum | `AgentRunWorkerOutcome` | `NO_RUNNABLE_RUN`、`CLAIMED`、`LEASE_CONFLICT`、`WAITING_APPROVAL`、`STEP_COMPLETED`、`RUN_FINISHED`、`RUN_FAILED`、`CANCELLED`。 |
| enum | `AgentRunWorkerSkipReason` | `NOT_WORKER_RUNNABLE`、`LEASE_HELD`、`APPROVAL_PENDING`、`TERMINAL_STATUS`、`MISSING_CHECKPOINT`。 |
| record | `AgentRunWorkerCommand` | `tenantId`、`workerId`、`maxRuns`、`leaseTtl`、`now`。`maxRuns` 默认使用 `AgentRunWorkerLimits.DEFAULT_MAX_RUNS_PER_TICK`。 |
| record | `AgentRunWorkerTickResult` | processed count、outcome 列表、lease conflict count、failure summary。 |
| record | `AgentRunDispatchDecision` | runId、status、outcome、skipReason、message。用于测试和审计，不驱动状态机。 |
| inbound port | `AgentRunWorkerInboundPort` | 只暴露 `tick(AgentRunWorkerCommand)`。 |
| outbound port | `AgentRunQueueRepositoryPort` | `findRunnable(String tenantId, int limit, Instant now)`；只返回候选 run，不做业务判断。 |
| existing inbound | `AgentRunResumeInboundPort` | worker 遇到 approved waiting checkpoint 时调用它恢复，不复制 resume 逻辑。 |
| existing inbound | `AgentRunLeaseInboundPort` | worker 执行前 acquire，执行中按需 heartbeat，结束 release。 |

#### 15.1.3 应用服务编排

`KernelAgentRunWorkerService` 的单次 tick 顺序：

1. 校验 command：`workerId`、`tenantId` 非空，`leaseTtl` 为正，`maxRuns` 在 `AgentRunWorkerLimits.MAX_RUNS_PER_TICK` 内。
2. 调 `AgentRunQueueRepositoryPort.findRunnable` 获取候选 run。
3. 对每个候选 run 重新调用 `AgentRunStatus.isWorkerRunnable()`，避免 SQL 与领域规则分叉。
4. 先 acquire lease，失败记录 `LEASE_CONFLICT` 并处理下一个。
5. 如果最新状态是 `WAITING_APPROVAL`，release lease 并返回 `WAITING_APPROVAL`，不执行工具。
6. 如果 run 有 latest approved checkpoint，调用 `AgentRunResumeInboundPort.resume`；否则执行当前 runtime 的 bounded step。
7. 成功 step 后保存 checkpoint 和 step；失败时写 `RUN_FAILED` outcome，并按现有 run repository 更新状态。
8. release lease；release 失败只记录 outcome，不回滚已完成 step。

#### 15.1.4 JDBC 与 Web 边界

JDBC 新增 `JdbcAgentRunQueueRepositoryAdapter`：

1. 查询 `sa_agent_run` 中 tenant 匹配且状态属于 `CREATED/RUNNING/RETRYING` 的候选。
2. 左连接 `sa_agent_run_lease`，排除 `lease_until > now` 的 run。
3. 按 `created_at`、`updated_at` 或既有字段稳定排序。
4. SQL 中的状态枚举必须来自 `AgentRunStatus` 常量映射，测试覆盖新增状态时的失败。

Web 首版不新增“启动后台 worker”的接口。若需要手动诊断入口，只允许受管理端使用 `POST /api/agent-runs:worker-tick`，请求体映射为 `AgentRunWorkerCommand`，默认不暴露给普通业务用户。

#### 15.1.5 TDD 切片

1. Kernel RED：`AgentRunWorkerServiceTests.noRunnableRunReturnsNoop`。
2. Kernel RED：`leaseConflictDoesNotExecuteRun`。
3. Kernel RED：`waitingApprovalRunIsSkippedAndLeaseReleased`。
4. Kernel RED：`approvedCheckpointDelegatesToResumePort`。
5. Kernel RED：`cancelledOrSucceededRunNeverDispatchesEvenIfQueueReturnsIt`。
6. JDBC RED：`findRunnableExcludesActiveLeaseAndTerminalStatus`。
7. JDBC RED：`expiredLeaseCanBeReclaimedByQueueQuery`。
8. Starter RED：worker service bean 装配使用 ports，不创建自动后台线程。
9. GREEN：实现 domain/port/service/JDBC/starter。
10. Regression：跑 approval、checkpoint、resume、lease、worker focused tests。

#### 15.1.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelApprovalManagementServiceTests,KernelAgentRunResumeServiceTests,KernelAgentRunLeaseServiceTests,AgentRunWorkerServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRunQueueRepositoryAdapterTests,JdbcAgentRunLeaseRepositoryAdapterTests,JdbcAgentCheckpointRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 15.1.7 回滚与不变量

回滚时可只撤掉 worker service/JDBC queue/starter wiring；approval、checkpoint、resume、lease 原 owner 不变。不得通过 worker 直接修改 `ApprovalRequest` 决策，不得在 repository 中复刻 `AgentRunStatus` 的完整状态机。

### 15.2 Phase 5 精细方案：Connector Disable、Credential Binding 与审计接入

#### 15.2.1 目标边界

Phase 5 的 sandbox 外层已经补齐，本方案只处理剩余 connector 安全运营闭环：OpenAPI operation 的 disable、credential binding 查询与启用校验、connector/sandbox 关键动作写入 Audit Ledger。仍不实现真实 secret vault、真实容器 runtime、远程 agent mesh。

完成后应满足：

1. OpenAPI import 后 operation 默认 disabled。
2. enable 前必须满足 risk、approval policy、credential binding。
3. disable 幂等，disable 后 Tool Gateway 不得调用该 operation。
4. connector import、operation enable/disable、sandbox session/execution 进入统一 Audit Ledger。
5. 审计 payload 只记录 credential ref 和摘要，不记录 token/secret。

#### 15.2.2 领域对象与端口

| 类型 | 名称 | 设计职责 |
| --- | --- | --- |
| command | `ConnectorOperationDisableCommand` | connectorId、operationId、operator、reason。 |
| command | `ConnectorCredentialBindingCommand` | connectorId、operationId、credentialRef、authType、operator。 |
| enum | `ConnectorOperationChangeReason` | `OPERATOR_REQUESTED`、`CREDENTIAL_ROTATED`、`SECURITY_INCIDENT`、`POLICY_CHANGED`。 |
| enum | `ConnectorCredentialBindingStatus` | `ACTIVE`、`DISABLED`、`ROTATED`、`INVALID`。 |
| outbound port | `ConnectorCredentialBindingRepositoryPort` | save/findActive/disable，不合并到 connector repository。 |
| outbound port | `ConnectorAuditPort` | 小型 bridge，默认组合 `AuditLedgerPort`，不让 connector service 依赖 JDBC。 |
| application | `KernelOpenApiConnectorService` | 增加 disable/bind credential，enable 前读取 active binding。 |

#### 15.2.3 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/enable` | 已有入口扩展 credential binding 校验。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/disable` | 幂等停用 operation。 |
| `PUT` | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | 绑定或替换 credential ref。 |
| `GET` | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | 只返回 ref/status/authType，不返回 secret material。 |

#### 15.2.4 关键规则

1. `CredentialAuthType.NONE` 的 operation 可以没有 binding；其他 authType 必须存在 active binding。
2. 高风险 operation 启用时必须存在 approval policy 或 publish/gate 明确允许。
3. disable 已 disabled operation 返回当前状态，不创建重复状态迁移。
4. binding 替换只保存新 ref，旧 ref 标记 `ROTATED` 或 `DISABLED`，不删除历史。
5. connector service 只接触 `credentialRef`，不获取 credential material。
6. Audit event type 使用 `CONNECTOR_IMPORTED`、`CONNECTOR_OPERATION_ENABLED`、`CONNECTOR_OPERATION_DISABLED`、`SECRET_USED`，不得使用字符串魔法值。
7. Audit 写失败策略由 `AuditWriteFailurePolicy` 决定；connector repository 不处理审计失败策略。

#### 15.2.5 数据库边界

新增或补齐 `sa_connector_credential_binding`：

| 字段 | 说明 |
| --- | --- |
| `binding_id` | 主键。 |
| `tenant_id` | 租户。 |
| `connector_id` | Connector。 |
| `operation_id` | Operation；可为空表示 connector 默认绑定。 |
| `auth_type` | `CredentialAuthType`。 |
| `credential_ref` | secret 引用。 |
| `status` | `ConnectorCredentialBindingStatus`。 |
| `created_by/created_at` | 创建人和时间。 |
| `disabled_by/disabled_at` | 停用人和时间。 |

唯一约束：同一 tenant/connector/operation/authType 只能有一个 `ACTIVE` binding。PostgreSQL 可用 partial unique index；如果当前共享 schema 需要兼容 H2 测试，则用 adapter 测试覆盖冲突行为，PostgreSQL schema 单独声明 partial index。

#### 15.2.6 TDD 切片

1. Kernel RED：`disableOperationIsIdempotent`。
2. Kernel RED：`enableAuthenticatedOperationRequiresActiveCredentialBinding`。
3. Kernel RED：`enableHighRiskOperationRequiresApprovalPolicy`。
4. Kernel RED：`bindingSecretMaterialNeverLeavesCredentialPort`。
5. JDBC RED：`activeBindingUniquePerOperation`。
6. Web RED：disable/binding API 响应不包含 secret。
7. Audit RED：connector import/enable/disable 调用 `AuditLedgerPort.append`，payload 经过 redaction。
8. GREEN：实现 command/domain/port/service/JDBC/Web/starter wiring。

#### 15.2.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelOpenApiConnectorServiceTests,ConnectorOperationEnableTests,ConnectorOperationDisableTests,ConnectorCredentialBindingTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcConnectorRepositoryAdapterTests,JdbcConnectorCredentialBindingRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseOpenApiConnectorControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentCredentialAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 15.2.8 回滚与不变量

回滚可关闭 disable/binding API；已导入 operation 保持 disabled 默认。不得把 secret material 落到 connector 表或 audit payload；真实凭据访问仍只能通过 credential provider port。

### 15.3 Phase 6 精细方案：Publish-ready、Rollback、Catalog View

#### 15.3.1 目标边界

把 Agent Factory 从“能生成 draft 和 validate”推进到“可发布运营”：latest publish check 只读查询、Production Gate bridge、版本 rollback、Agent Catalog 最小查询。该方案不做完整前端 Studio，不实现 canary，也不把发布和回滚塞进 `AgentDefinitionRepositoryPort` 的内部细节。

完成后应满足：

1. latest publish check 可通过 API 查询，不触发重新 validate。
2. rollback 只激活旧 version 或更新 active pointer，不修改历史 `AgentVersion`。
3. rollback 必须有 operator 和 reason。
4. Production Gate 可从 Factory API 触发或查询，报告保存为快照。
5. Agent Catalog 只显示 published/enabled 且当前 subject 可调用的 Agent。

#### 15.3.2 领域对象与端口

| 类型 | 名称 | 设计职责 |
| --- | --- | --- |
| command | `AgentVersionRollbackCommand` | agentId、versionId、tenantId、operator、reasonCode、comment。 |
| record | `AgentRollbackResult` | rollbackId、agentId、previousVersionId、targetVersionId、status、reasonCode、rolledBackAt。 |
| enum | `AgentRollbackStatus` | `ROLLED_BACK`、`NOOP_ALREADY_ACTIVE`、`REJECTED`。 |
| enum | `AgentRollbackReasonCode` | `OPERATOR_REQUESTED`、`GATE_FAILED`、`CANARY_FAILED`、`INCIDENT_RESPONSE`。 |
| port | `AgentVersionActivationRepositoryPort` | `findActive(agentId)`、`activate(command)`、`history(agentId)`。 |
| port | `AgentCatalogQueryPort` | `pagePublished(AgentCatalogQuery)`，只读 catalog。 |
| inbound | `AgentFactoryInboundPort` | 增加 `latestPublishCheck`、`productionGate`、`rollback`、`catalog`，仍保持 Factory 小接口。 |
| application | `KernelAgentFactoryService` | 组合 Definition、PublishCheck、ProductionGate、Activation、Catalog ports。 |

#### 15.3.3 数据库边界

优先新增激活历史表，而不是更新 `sa_agent_version`：

| 表 | 用途 |
| --- | --- |
| `sa_agent_version_activation` | 每次 publish/rollback 激活记录，append-only。 |
| `sa_agent_publish_check` | 已有；latest 查询按 agentId、checkedAt 降序。 |
| `sa_production_gate_report` | 已有 foundation；Factory 只调用 gate port。 |

`sa_agent_version_activation` 字段：

| 字段 | 说明 |
| --- | --- |
| `activation_id` | 主键。 |
| `tenant_id` | 租户。 |
| `agent_id` | Agent。 |
| `version_id` | 被激活版本。 |
| `activation_type` | `PUBLISH`、`ROLLBACK`。 |
| `previous_version_id` | 回滚前 active version。 |
| `reason_code` | `AgentRollbackReasonCode` 或 publish reason。 |
| `operator` | 操作人。 |
| `created_at` | 激活时间。 |

当前 active version 可通过 latest activation 解析；如果现有 `AgentDefinition` 已有 latest/published pointer，可以在 adapter 中同步更新 pointer，但领域语义仍以 activation port 为 owner。

#### 15.3.4 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agents/{agentId}/publish-checks/latest` | 最近一次 publish validation，404 或 empty body 表示无记录。 |
| `POST` | `/api/agents/{agentId}/production-gate` | 生成并保存 gate report；可复用现有 ProductionGate controller，也可由 Factory controller bridge。 |
| `GET` | `/api/agents/{agentId}/production-gate/latest` | 最近 gate report。 |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/rollback` | 回滚到旧版本。 |
| `GET` | `/api/agent-catalog` | 查询可调用 Agent，支持 tenant、risk、capability、page。 |

#### 15.3.5 关键规则

1. rollback 目标必须属于同 tenant、同 agent。
2. rollback 目标必须是已发布过的 immutable version。
3. 当前 active version 等于目标 version 时返回 `NOOP_ALREADY_ACTIVE`，不写重复激活记录，除非 operator 明确要求审计 marker。
4. latest check 查询只读，不调用 validate 规则，不写 `sa_agent_publish_check`。
5. Production Gate 强检查失败不能被 Factory 忽略；Factory 只能返回 report，是否发布由发布流程 owner 决定。
6. Catalog query 必须先过滤 tenant/visibility，再过滤 enabled/published，最后应用 subject policy。
7. Audit Ledger 接入时，rollback 写 `AGENT_ROLLED_BACK`，payload 包含 previous/target version id，不包含 instructions 全文。

#### 15.3.6 TDD 切片

1. Kernel RED：`latestPublishCheckReturnsRepositoryValueWithoutSaving`。
2. Kernel RED：`rollbackActivatesPublishedVersionWithoutMutatingSnapshot`。
3. Kernel RED：`rollbackRejectsVersionFromAnotherAgent`。
4. Kernel RED：`rollbackRequiresOperatorAndReason`。
5. Kernel RED：`catalogReturnsOnlyPublishedEnabledAgents`。
6. JDBC RED：`activationHistoryResolvesLatestActiveVersion`。
7. Web RED：latest publish check API、rollback API、catalog API。
8. Starter RED：activation repository 和 custom gate port 装配。
9. GREEN：实现 command/domain/port/service/JDBC/Web/starter。

#### 15.3.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentTemplateTests,KernelAgentFactoryServiceTests,AgentRollbackResultTests,AgentCatalogTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentFactoryRepositoryAdapterTests,JdbcAgentVersionActivationRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 15.3.8 回滚与不变量

回滚功能本身可通过不装配 controller 暂停；历史 version 和 publish check 都保留。不得在 rollback 时重写 `AgentVersion.instructionsJson`、`toolsJson`、`modelPolicyJson` 等 snapshot 字段。

### 15.4 Phase 7 精细方案：Governed Local Agent-as-Tool

#### 15.4.1 目标边界

先实现本地 Agent-as-Tool 与 handoff 记录，不实现远程 A2A client/server、remote Agent Card、routing、circuit breaker。调用路径必须经过 Tool Gateway 和 Policy，不能由 `KernelAgentHandoffService` 直接绕过工具治理调用 child agent。

完成后应满足：

1. parent agent 可以通过一个 tool entry 调用 target agent。
2. 调用创建 `AgentHandoff` 记录和 child run。
3. context handoff 默认 summary-only，敏感上下文被裁剪。
4. handoff 可按 parent run 查询。
5. child run 完成/失败后 handoff 状态可回写。

#### 15.4.2 领域对象与端口

| 类型 | 名称 | 设计职责 |
| --- | --- | --- |
| domain | `AgentHandoff` | parentRunId、childRunId、sourceAgentId、targetAgentId、status、inputSummary、failureCode。 |
| enum | `AgentHandoffStatus` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。 |
| enum | `AgentHandoffFailureCode` | `TARGET_NOT_FOUND`、`TARGET_DISABLED`、`TARGET_NOT_PUBLISHED`、`DEPTH_EXCEEDED`、`POLICY_DENIED`、`CONTEXT_DENIED`、`CHILD_RUN_FAILED`。 |
| enum | `AgentHandoffTriggerSource` | `TOOL_GATEWAY`、`WORKER_RESUME`、`OPERATOR_CANCEL`。 |
| value object | `AgentHandoffContextPolicy` | mode、sensitivity cap、allowed resource refs、max summary chars。 |
| inbound | `AgentHandoffQueryInboundPort` | `listByParentRun`、`findById`。 |
| inbound | `AgentHandoffDecisionInboundPort` | `cancel`、`markChildFinished`。不开放 create 给 Web。 |
| outbound | `AgentHandoffRepositoryPort` | save/update/find/list。 |
| outbound | `LocalAgentAsToolPort` | Tool Gateway adapter 调用的端口，输入 tool request，输出 tool response。 |
| outbound | `MeshPolicyPort` | 同租户、深度、目标状态、上下文传播策略判断。 |

#### 15.4.3 调用流

```text
AgentLoop
  -> ToolGatewayPort.invoke(toolId=agent:<targetAgentId>)
  -> PolicyDecision(ALLOW or APPROVAL_REQUIRED)
  -> LocalAgentAsToolPort.invoke(...)
  -> KernelAgentHandoffService.createFromToolInvocation(...)
  -> AgentRunInboundPort.startRun(child command)
  -> AgentHandoffRepositoryPort.updateRunning(childRunId)
```

结果回写可分两步：

1. 首版同步返回 child run id 和 handoff id，parent run 等待或后续轮询。
2. worker hardening 完成后，child run terminal 时调用 `markChildFinished` 回写 `SUCCEEDED/FAILED`。

不在首版实现“父 run 阻塞等待子 run 流式完成”的复杂编排。

#### 15.4.4 Context handoff policy

默认策略：

| 输入类型 | 传播规则 |
| --- | --- |
| user task summary | 允许，限制长度。 |
| message history | 默认不传，只传摘要。 |
| ContextPack item `PUBLIC/INTERNAL` | 重新 ACL 后可传 resource ref 或摘要。 |
| ContextPack item `CONFIDENTIAL` | 只有 target subject ACL allow 时可传摘要。 |
| ContextPack item `SECRET` | 永不传。 |
| tool result | 只传 redacted summary，不传原始 payload。 |

该策略由 `AgentHandoffContextPolicy` 和 `ResourceAccessPolicyPort` 组合完成；不要在 handoff service 中复制 ACL 判断。

#### 15.4.5 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-runs/{runId}/handoffs` | 查询 parent run 的 handoff 列表。 |
| `GET` | `/api/agent-handoffs/{handoffId}` | 查询 handoff 详情。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 幂等取消未 terminal 的 handoff。 |

不提供 `POST /api/agent-handoffs`，避免绕过 Tool Gateway。

#### 15.4.6 数据库边界

`sa_agent_handoff` 字段：

| 字段 | 说明 |
| --- | --- |
| `handoff_id` | 主键。 |
| `tenant_id` | 租户。 |
| `parent_run_id` | 父 run。 |
| `child_run_id` | 子 run。 |
| `source_agent_id` | 发起 agent。 |
| `target_agent_id` | 目标 agent。 |
| `tool_invocation_id` | Tool Gateway invocation。 |
| `status` | `AgentHandoffStatus`。 |
| `failure_code` | `AgentHandoffFailureCode`。 |
| `input_summary` | 裁剪后摘要。 |
| `context_policy_json` | 策略快照。 |
| `created_at/finished_at` | 时间。 |

索引：`parent_run_id, created_at`、`child_run_id`、`tenant_id, target_agent_id, created_at`。

#### 15.4.7 TDD 切片

1. Domain RED：`AgentHandoff` 必填、状态迁移、terminal 不可回退。
2. Policy RED：跨 tenant、disabled target、unpublished target、depth exceeded 拒绝。
3. Context RED：secret 丢弃、confidential 重新 ACL、summary 长度限制。
4. Service RED：Tool Gateway 触发创建 handoff 和 child run。
5. Service RED：child run failure 回写 `CHILD_RUN_FAILED`。
6. Tool adapter RED：direct service create 不存在，只有 tool invocation path。
7. JDBC/Web/Starter RED。
8. Audit RED：`AGENT_HANDOFF_CREATED`、`AGENT_HANDOFF_FINISHED` payload 脱敏。

#### 15.4.8 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,DefaultMeshPolicyPortTests,AgentHandoffContextPolicyTests,KernelAgentHandoffServiceTests,LocalAgentAsToolPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 15.4.9 回滚与不变量

关闭 Agent-as-Tool provider 后，已有 Tool Gateway、MCP、OpenAPI 工具不受影响；handoff 表只是关系记录，不改变 `sa_agent_run` 主语义。远程 A2A 仍保持 disabled/nonexistent，不得为了本地 handoff 引入 remote mesh 抽象。

### 15.5 Phase 8 精细方案：Eval、Quota/SRE、Canary/Pilot Gate

#### 15.5.1 目标边界

Audit Ledger foundation 和 Production Gate foundation 已有基础，本方案把 Phase 8 剩余工作拆成 B/C/D 三个可验收切片：

1. Phase 8B：Agent Eval Gate，复用已有 retrieval/memory eval 能力，增加 Agent publish 视角的 eval summary port。
2. Phase 8C：Quota/Cost/SRE Health，建立只读聚合和最小运行时拦截点。
3. Phase 8D：Canary 与 Enterprise Pilot Gate，把 version rollout 和企业试点准入做成可查询、可回滚的运营记录。

不在本方案中建设完整评测平台 UI、复杂成本账单系统、Prometheus 替代品或自动扩量控制器；只实现发布准入所需的最小事实来源和 API。

#### 15.5.2 Phase 8B：Agent Eval Gate

领域与端口：

| 类型 | 名称 | 设计职责 |
| --- | --- | --- |
| enum | `AgentEvalType` | `RAG`、`MEMORY_RECALL`、`TRAJECTORY`、`TOOL_USE`、`SAFETY`、`HITL`。 |
| enum | `AgentEvalStatus` | `PASS`、`WARN`、`FAIL`、`NOT_RUN`。 |
| record | `AgentEvalSummary` | agentId、versionId、type、status、score、threshold、runRef。 |
| outbound | `AgentEvalStatusPort` | `latestForAgent(agentId, versionId)`，供 Production Gate 读取。 |
| inbound | `AgentEvalQueryInboundPort` | 查询 eval summary，不执行重型 eval。 |
| repository | `AgentEvalSummaryRepositoryPort` | 保存 publish 视角 summary，可从 retrieval/memory eval 同步生成。 |

关键规则：

1. Gate 读取 eval summary，不直接运行 eval。
2. 没有 eval summary 时 `AgentEvalStatus.NOT_RUN`，对高风险/tool-execution Agent 是 `FAIL`，对低风险知识助手可 `WARN`。
3. eval case 不保存真实 secret；敏感样本只存 ref。
4. summary 是快照，后续 eval 重跑不改写历史 gate report。

TDD：

1. `AgentEvalSummaryTests` 覆盖阈值、PASS/WARN/FAIL 聚合。
2. `KernelProductionGateServiceTests` 增加 eval port：高风险 `NOT_RUN` fail，低风险 warn。
3. `JdbcAgentEvalSummaryRepositoryAdapterTests` 覆盖 latest 查询。
4. `SeahorseAgentEvalControllerTests` 覆盖只读查询。

验收命令：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentEvalSummaryTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentEvalSummaryRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentEvalControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 15.5.3 Phase 8C：Quota、Cost 与 SRE Health

领域与端口：

| 类型 | 名称 | 设计职责 |
| --- | --- | --- |
| enum | `QuotaScopeType` | `TENANT`、`AGENT`、`USER`、`TOOL`、`MODEL`、`RUN`。 |
| enum | `QuotaDecisionEffect` | `ALLOW`、`WARN`、`DENY`、`REQUIRE_APPROVAL`。 |
| enum | `CostUsageSource` | `MODEL_CALL`、`TOOL_CALL`、`SANDBOX_EXECUTION`、`REMOTE_AGENT`。 |
| record | `QuotaPolicy` | scope、limit、window、effectWhenExceeded。 |
| record | `CostUsageRecord` | tenantId、agentId、runId、source、amount、unit、occurredAt。 |
| outbound | `QuotaDecisionPort` | `decide(QuotaDecisionRequest)`，供 Tool Gateway/Run start 组合。 |
| outbound | `CostUsageRepositoryPort` | append/query aggregate。 |
| outbound | `SreHealthContributorPort` | 小接口贡献 `FeatureHealthStatus`，不做大一统 SRE service。 |
| inbound | `SreHealthQueryInboundPort` | 聚合 DB、Tool Gateway、MCP、Sandbox、Audit、Worker backlog。 |

关键规则：

1. quota 默认保守：无策略时高风险工具 `WARN`，明确超限时按策略 `DENY` 或 `REQUIRE_APPROVAL`。
2. cost 记录只追加，不在同一表做账单结算。
3. SRE health 是只读聚合，不修改依赖系统状态。
4. Tool Gateway 接 quota 时不得绕过原有 PolicyDecision；quota 是额外约束。
5. Production Gate 中 `QUOTA_CONFIGURED` 和 `SRE_HEALTH_GREEN` 从 warn bridge 升级为真实 port。

TDD：

1. `QuotaPolicyTests` 覆盖窗口、超限 effect、默认策略。
2. `KernelQuotaDecisionServiceTests` 覆盖 agent/user/tool/model scope。
3. `CostUsageRecordTests` 覆盖 append-only 和聚合维度。
4. `KernelSreHealthQueryServiceTests` 使用 fake contributors 聚合 GREEN/WARN/RED。
5. Production Gate tests 覆盖 quota/health 真实 port。

验收命令：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelSreHealthQueryServiceTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseQuotaControllerTests,SeahorseSreHealthControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 15.5.4 Phase 8D：Canary 与 Enterprise Pilot Gate

领域与端口：

| 类型 | 名称 | 设计职责 |
| --- | --- | --- |
| enum | `AgentRolloutStatus` | `DRAFT`、`CANARY`、`PAUSED`、`PROMOTED`、`ROLLED_BACK`、`FAILED`。 |
| enum | `AgentRolloutFailureCode` | `EVAL_REGRESSION`、`ERROR_RATE_HIGH`、`COST_EXCEEDED`、`MANUAL_STOP`。 |
| record | `AgentVersionRollout` | agentId、versionId、status、trafficPercent、startedBy、startedAt。 |
| record | `EnterprisePilotReadinessReport` | agentId、versionId、checks、status、generatedAt。 |
| outbound | `AgentRolloutRepositoryPort` | save/update/latest/history。 |
| inbound | `AgentRolloutInboundPort` | start canary、pause、promote、rollback。 |
| inbound | `EnterprisePilotReadinessInboundPort` | generate/latest readiness report。 |

关键规则：

1. canary 不修改 `AgentVersion`，只写 rollout 记录和 active routing hint。
2. canary 流量百分比必须在具名常量范围内，默认 `AgentRolloutLimits.DEFAULT_CANARY_PERCENT`。
3. promote 前必须有 PASS 或可接受 WARN 的 Production Gate report。
4. canary rollback 复用 Phase 6 `AgentVersionActivationRepositoryPort`，不另建回滚 owner。
5. Enterprise Pilot Gate 只读检查 owner、published version、tool risk、ACL、eval、quota、audit、rollback、disable switch。

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/canary` | 开始 canary。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停 rollout。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | 提升为全量。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | 回滚 rollout。 |
| `POST` | `/api/agents/{agentId}/pilot-readiness` | 生成企业试点准入报告。 |
| `GET` | `/api/agents/{agentId}/pilot-readiness/latest` | 查询最新准入报告。 |

TDD：

1. `AgentVersionRolloutTests` 覆盖状态迁移和 traffic percent。
2. `KernelAgentRolloutServiceTests` 覆盖 start/pause/promote/rollback。
3. `EnterprisePilotReadinessReportTests` 覆盖必备检查聚合。
4. `KernelEnterprisePilotReadinessServiceTests` 组合 gate/eval/quota/audit/rollback/disable fake ports。
5. JDBC/Web/starter tests。

验收命令：

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,KernelAgentRolloutServiceTests,EnterprisePilotReadinessReportTests,KernelEnterprisePilotReadinessServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 15.5.5 回滚与不变量

Phase 8B/C/D 都必须以小 port 扩展 Production Gate，不得把 eval/quota/SRE/canary 逻辑塞进 `KernelProductionGateService` 的私有硬编码列表。Gate service 只聚合 ports；每个子系统维护自己的事实来源。Canary 和 Pilot Gate 可独立关闭；关闭后 Production Gate 回到 WARN/FAIL 明确状态，不伪造 PASS。

### 15.6 最新剩余工作推荐顺序

1. Phase 6 Publish-ready：补 latest publish check、rollback、catalog，因为它直接决定业务 Agent 是否可运营。
2. Phase 3 Worker hardening：补 worker tick、queue、lease 接管、retry/cancel，一旦 rollback/catalog 可用即可补运行可靠性。
3. Phase 5 connector residual：补 disable、credential binding、connector audit，让外部系统接入满足生产安全底线。
4. Phase 7 Local Agent-as-Tool：在运行、工具、审计、catalog 都有基础后再做本地 handoff。
5. Phase 8B Agent Eval Gate：把已有 retrieval/memory eval 升级为发布准入证据。
6. Phase 8C Quota/Cost/SRE：补运行成本与健康聚合。
7. Phase 8D Canary/Pilot Gate：最后做 rollout 和企业试点准入，避免在基础证据不足时过早引入发布复杂度。

### 15.7 本节执行约束

1. 每个阶段先写 RED 测试并记录失败，再实现 GREEN。
2. 新端口必须保持接口隔离：Run、Factory、Connector、Handoff、Eval、Quota、SRE、Rollout 不合成大服务。
3. 所有状态、原因、触发来源、风险等级、检查项使用 enum 或具名常量。
4. Kernel 不依赖 Spring、JDBC、Web、HTTP client 或具体数据库。
5. Adapter 可以失败，但必须把失败映射为领域可诊断结果，不泄露 secret。
6. 所有审计 payload 先过 `AuditRedactionPolicy`。
7. 任何“暂时 bridge/noop/warn”都要有明确退休触发：对应真实 port 和 repository 完成后移除或降级为测试 fake。

## 16. 2026-05-26 深读后的未完成阶段开发执行蓝图

本节面向后续实际开发人员，按“一个未完成阶段一个更具体方案”的粒度，把第 15 节进一步落到文件边界、接口边界、TDD 顺序、验收命令和回滚约束。它依据以下文档校准：

- `00-architecture-baseline.md`：owner、包路径、kernel 依赖方向。
- `01-agent-registry-run-store.md`：AgentDefinition、AgentVersion、AgentRun、AgentStep 的事实来源。
- `02-tool-gateway-policy-engine.md`：所有工具调用必须进入 Tool Gateway。
- `03-durable-runtime-hitl.md`：WAITING_APPROVAL、checkpoint、resume、lease 的状态机。
- `04-context-db-resource-acl.md`：上下文传播必须有 ACL、sensitivity、provenance。
- `05-connectors-credentials-sandbox.md`：MCP/OpenAPI/credential/sandbox 安全边界。
- `06-agent-factory-studio.md`：Factory 发布门禁、回滚、Catalog 目标。
- `07-multi-agent-a2a-mesh.md`：先做本地 Agent-as-Tool，再做远程 A2A。
- `08-production-hardening.md`：eval、audit、cost、SRE、canary、pilot gate。
- `99-current-implementation-handoff.md`：旧交接文档只作为历史背景；当前方案以 worktree 与第 15 节为准。

### 16.0 剩余阶段开发总顺序

| 顺序 | 阶段 | 为什么排在这里 | 完成后解锁 |
| --- | --- | --- | --- |
| 1 | Phase 6 Publish-ready 外层闭环 | Factory 已有 kernel 雏形，补 JDBC/Web/starter 后可让业务 Agent 进入可运营状态。 | 后续 worker、catalog、handoff、pilot gate 都能引用稳定 Agent 版本。 |
| 2 | Phase 3 Worker hardening | approval、checkpoint、resume、lease 已有基础，缺少可运营 tick 和 queue 领取。 | 高风险工具、rollback 后运行、handoff child run 都能可靠推进。 |
| 3 | Phase 5 connector residual | OpenAPI import 和 sandbox 已有基础，缺少 disable、credential binding、connector audit。 | 外部系统接入可满足生产安全底线。 |
| 4 | Phase 7 Local Agent-as-Tool | 依赖 Agent Catalog、Tool Gateway、Worker、Audit 和 Context ACL 的基础闭环。 | 本地多 Agent handoff 可用，远程 A2A 有本地协议基础。 |
| 5 | Phase 8B/C/D | 生产硬化需要前面阶段提供真实事实来源。 | 企业试点准入、canary、成本和健康面板形成闭环。 |

跨阶段共同约束：

1. 新增状态、类型、原因、检查项、事件类型全部使用 enum 或具名常量。
2. 每个新功能先写 RED 测试并记录失败，再做最小 GREEN。
3. 新 port 保持小接口：Factory、Run Worker、Connector Credential、Handoff、Eval、Quota、SRE、Rollout 不合并成统一 `AgentService`。
4. Repository 不承载业务判断；业务判断属于 domain 或 kernel application service。
5. 审计 payload 必须经过 `AuditRedactionPolicy`，只存 ref、摘要、状态、原因，不存 secret/token/material。
6. 任何 bridge/noop/warn 行为都要有清晰退休条件，不能长期伪装为完成能力。

### 16.1 Phase 6 方案：Publish-ready 外层闭环

#### 16.1.1 目标与退出条件

目标是把已有 Factory kernel 能力补成可通过 API 使用的运营闭环：查询最近发布检查、回滚版本、查询 Agent Catalog，并把 JDBC 与 starter wiring 补齐。

退出条件：

1. `GET /api/agents/{agentId}/publish-checks/latest` 返回最近一次 `AgentPublishCheckReport`，不触发新的 validate。
2. `POST /api/agents/{agentId}/versions/{versionId}/rollback` 只写 activation 历史并同步 active pointer，不改写 `AgentVersion` 快照。
3. `GET /api/agent-catalog` 只返回同 tenant、published/enabled 的 Agent。
4. starter 在 JDBC repository 模式下自动装配 `AgentVersionActivationRepositoryPort` 和 `AgentCatalogQueryPort`。
5. focused kernel、JDBC、Web、starter 测试全部通过。

#### 16.1.2 文件边界

Kernel 已出现的文件需要保持语义，不做重复 owner：

| 文件 | 动作 |
| --- | --- |
| `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/factory/KernelAgentFactoryService.java` | 只补必要组合逻辑和空 port 诊断，不把 JDBC/Web 细节放进来。 |
| `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/AgentFactoryInboundPort.java` | 保持 Factory 小接口，不合并 Definition/Run/Tool 管理。 |
| `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/AgentVersionActivationRepositoryPort.java` | 作为 rollback active owner。 |
| `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/AgentCatalogQueryPort.java` | 作为只读 Catalog owner。 |

需要补齐的文件：

| 模块 | 文件 |
| --- | --- |
| JDBC | `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentVersionActivationRepositoryAdapter.java` |
| JDBC | `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentCatalogQueryAdapter.java` |
| JDBC schema | `seahorse-agent-adapter-repository-jdbc/src/main/resources/META-INF/seahorse-agent/sql/agent-registry-run-store-postgresql.sql` |
| Web | `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentFactoryController.java` |
| Starter | `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentRegistryRepositoryAutoConfiguration.java` |
| Starter | `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelRegistryAutoConfiguration.java` |
| Tests | `seahorse-agent-adapter-repository-jdbc/src/test/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentFactoryRepositoryAdapterTests.java` |
| Tests | `seahorse-agent-adapter-web/src/test/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentFactoryControllerTests.java` |
| Tests | `seahorse-agent-spring-boot-starter/src/test/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentRegistryAutoConfigurationTests.java` |

#### 16.1.3 领域与数据库设计

`sa_agent_version_activation` 是 rollback/publish active version 的事实来源：

| 字段 | 类型 | 规则 |
| --- | --- | --- |
| `activation_id` | `VARCHAR(64)` | 主键，由 kernel 创建。 |
| `tenant_id` | `VARCHAR(64)` | 必填，rollback 时必须与 AgentDefinition 一致。 |
| `agent_id` | `VARCHAR(64)` | 必填。 |
| `version_id` | `VARCHAR(64)` | 必填，必须属于同 agent。 |
| `activation_type` | `VARCHAR(32)` | `AgentVersionActivationType.PUBLISH` 或 `ROLLBACK`。 |
| `previous_version_id` | `VARCHAR(64)` | 可空，rollback 时记录原 active version。 |
| `reason_code` | `VARCHAR(64)` | 使用 enum name。 |
| `operator` | `VARCHAR(64)` | 必填。 |
| `created_at` | `TIMESTAMP` | 必填。 |

索引：

```sql
CREATE INDEX idx_sa_agent_version_activation_active
ON sa_agent_version_activation(agent_id, created_at DESC, activation_id DESC);
```

Catalog 查询只读 SQL 规则：

1. 从 `sa_agent_definition d` 读取 tenant、status、risk、owner、latest_version_id。
2. 通过 `d.latest_version_id = v.version_id` join `sa_agent_version v`。
3. 只返回 `d.status = 'PUBLISHED'` 且 `d.latest_version_id IS NOT NULL`。
4. keyword 只匹配 `d.name`、`d.description`、`d.owner_team`，不匹配 instructions 全文。
5. 排序固定为 `d.updated_at DESC, d.agent_id ASC`。

#### 16.1.4 API 请求与响应

rollback 请求体：

```json
{
  "tenantId": "tenant-1",
  "operator": "admin-1",
  "reasonCode": "INCIDENT_RESPONSE",
  "comment": "rollback after failed production gate"
}
```

rollback 响应字段：

| 字段 | 来源 |
| --- | --- |
| `rollbackId` | `AgentRollbackResult.rollbackId` |
| `agentId` | command agentId |
| `previousVersionId` | latest activation 或 definition pointer |
| `targetVersionId` | command versionId |
| `status` | `AgentRollbackStatus` |
| `reasonCode` | `AgentRollbackReasonCode` |
| `rolledBackAt` | saved activation time |

Catalog 查询参数：

| 参数 | 规则 |
| --- | --- |
| `tenantId` | 必填或使用当前租户上下文；首版 API 显式必填。 |
| `keyword` | 可空，trim 后空字符串按无 keyword 处理。 |
| `current` | 默认 `1`。 |
| `size` | 默认 `20`，最大使用 `AgentCatalogQuery.MAX_PAGE_SIZE`。 |

#### 16.1.5 TDD 顺序

1. Kernel：确认 `latestPublishCheck` 不保存新 report，只调用 repository latest。
2. Kernel：确认 rollback 目标 version 属于同 agent、同 tenant。
3. Kernel：确认 rollback 写 activation，不修改 `AgentVersion` 快照字段。
4. Kernel：确认 operator blank 与 reason null 被拒绝。
5. JDBC：写 RED，编译失败应缺 `JdbcAgentVersionActivationRepositoryAdapter` 和 `JdbcAgentCatalogQueryAdapter`。
6. JDBC：实现 activation insert/findActive，按 `created_at DESC, activation_id DESC` 取最新。
7. JDBC：实现 catalog query，只返回 published/latest version。
8. Web：写 RED，覆盖 latest check、rollback、catalog 三个 endpoint。
9. Web：实现 controller 和 request record，controller 只做 HTTP 参数映射。
10. Starter：写 RED，断言新 repository port 和 Factory service wiring 存在。
11. Starter：补自动配置。
12. Regression：跑 focused tests 和 `git diff --check`。

#### 16.1.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentFactoryServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentFactoryRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentFactoryControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 16.1.7 回滚与不变量

1. 可通过移除 Web controller mapping 暂停 rollback API，不删除 activation history。
2. 回滚不得更新 `sa_agent_version` 的 instructions、toolSet、modelConfig、memoryConfig、guardrailConfig。
3. 如果同步 `AgentDefinition.latestVersionId`，只能作为兼容 pointer；active 语义仍归 `AgentVersionActivationRepositoryPort`。
4. Audit 后续接入 `AGENT_ROLLED_BACK` 时只记录 version id、operator、reason，不记录完整 prompt。

### 16.2 Phase 3 方案：Durable Runtime Worker Hardening

#### 16.2.1 目标与退出条件

目标是把已有 approval、checkpoint、resume、lease 组合为可运营的 worker tick。首版只做显式调用的 worker service，不做自动后台线程、不引入 MQ 工作流、不引入 Temporal/LangGraph。

退出条件：

1. worker 一次 tick 能领取有限数量 runnable run。
2. active lease 阻止双 worker 执行。
3. `WAITING_APPROVAL`、terminal status 不会被执行。
4. approved checkpoint 的恢复只委托 `AgentRunResumeInboundPort`，worker 不复制 resume 逻辑。
5. cancel/retry 的幂等语义继续由 `AgentRun`/`KernelAgentRunService` 维护。

#### 16.2.2 文件边界

| 模块 | 文件 |
| --- | --- |
| Kernel domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/runtime/AgentRunWorkerOutcome.java` |
| Kernel domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/runtime/AgentRunWorkerSkipReason.java` |
| Kernel domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/runtime/AgentRunWorkerLimits.java` |
| Inbound | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/AgentRunWorkerInboundPort.java` |
| Inbound | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/AgentRunWorkerCommand.java` |
| Inbound | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/AgentRunWorkerTickResult.java` |
| Outbound | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/AgentRunQueueRepositoryPort.java` |
| Kernel service | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/runtime/KernelAgentRunWorkerService.java` |
| JDBC | `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentRunQueueRepositoryAdapter.java` |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration` 和 `SeahorseAgentRegistryRepositoryAutoConfiguration` |

#### 16.2.3 Worker 编排规则

`KernelAgentRunWorkerService.tick` 顺序：

1. 校验 `tenantId`、`workerId`，并把 `maxRuns` clamp 到 `AgentRunWorkerLimits.MAX_RUNS_PER_TICK`。
2. 调 `AgentRunQueueRepositoryPort.findRunnable(tenantId, limit, now)` 取候选。
3. 对每个 run 重新读取最新状态，使用 `AgentRunStatus.isWorkerRunnable()` 判断。
4. 调 `AgentRunLeaseInboundPort.acquire`，失败记录 `LEASE_CONFLICT`。
5. 如果最新状态变为 `WAITING_APPROVAL`，release lease 并记录 `APPROVAL_PENDING`。
6. 如果存在可恢复 checkpoint 且 approval 已 `APPROVED` 或 `MODIFIED`，调用 `AgentRunResumeInboundPort.resume`。
7. 如果当前 slice 没有 bounded executor，worker 首版只完成 resume path 和状态诊断，不伪造 step success。
8. 无论成功失败都尝试 release lease；release 失败进入 result diagnostics，不回滚已保存 run 状态。

`AgentRunQueueRepositoryPort` 不判断业务状态机，只做候选筛选；最终判断在 service/domain。

#### 16.2.4 JDBC 查询规则

首版 SQL：

1. `sa_agent_run.status IN ('CREATED','RUNNING','RETRYING')`。
2. `tenant_id = ?`。
3. 不存在 active lease，或 `lease_until <= now`。
4. 排序 `started_at ASC, run_id ASC`。
5. limit 使用 command 的 safe maxRuns。

如果 schema 没有 `updated_at` 字段，不新增字段；使用现有 `started_at` 保持 KISS。

#### 16.2.5 可选诊断 API

只在管理端暴露：

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/agent-runs/worker-ticks` | 手动触发一次 worker tick，用于测试和运维诊断。 |

该 API 不启动常驻线程，不接受普通用户调用。请求体只允许 `tenantId`、`workerId`、`maxRuns`、`leaseTtlSeconds`。

#### 16.2.6 TDD 顺序

1. Kernel RED：`noRunnableRunReturnsNoRunOutcome`。
2. Kernel RED：`queueReturningTerminalRunIsSkippedByDomainStatus`。
3. Kernel RED：`leaseConflictDoesNotCallResume`。
4. Kernel RED：`waitingApprovalRunReleasesLeaseWithoutResume`。
5. Kernel RED：`approvedCheckpointDelegatesToResumePort`。
6. JDBC RED：`findRunnableExcludesActiveLease`。
7. JDBC RED：`findRunnableIncludesExpiredLease`。
8. Starter RED：worker service bean 只在 repository ports 存在时装配。
9. Web RED：如果实现诊断 API，覆盖权限外形和 response mapping。
10. GREEN：按 kernel -> JDBC -> starter -> web 顺序补实现。

#### 16.2.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelAgentRunWorkerServiceTests,KernelAgentRunLeaseServiceTests,KernelAgentRunResumeServiceTests,KernelAgentRunServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRunQueueRepositoryAdapterTests,JdbcAgentRunLeaseRepositoryAdapterTests,JdbcAgentCheckpointRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 16.2.8 回滚与不变量

1. 可撤掉 worker queue adapter 和 starter bean，已有 approval/checkpoint/resume/lease 不受影响。
2. Worker 不直接修改 `ApprovalRequest`，只消费 approval 决策结果。
3. Worker 不直接调用 `ToolPort`，恢复执行仍走 resume owner 和 Tool Gateway。
4. Repository 不复制 `AgentRunStatus` 状态机，SQL 只做候选筛选。

### 16.3 Phase 5 方案：Connector Disable、Credential Binding 与 Audit 接入

#### 16.3.1 目标与退出条件

目标是补齐 OpenAPI connector 的生产安全余项：operation disable、credential binding、启用校验、connector/sandbox 审计接入。首版不实现真实 secret vault、不执行真实容器 runtime、不引入远程 Agent mesh。

退出条件：

1. OpenAPI import 后 operation 默认 disabled 或需要显式 enable。
2. authenticated operation 没有 active credential binding 时不能 enable。
3. high risk operation 没有 approval policy 时不能 enable。
4. disable 幂等，disabled operation 不能被 Tool Gateway 调用。
5. connector import、enable、disable、credential binding、sandbox session/execution 关键动作写 Audit Ledger。

#### 16.3.2 文件边界

| 模块 | 文件 |
| --- | --- |
| Kernel domain | `kernel/domain/agent/connector/ConnectorCredentialBinding.java` |
| Kernel domain | `kernel/domain/agent/connector/ConnectorCredentialBindingStatus.java` |
| Kernel domain | `kernel/domain/agent/connector/ConnectorOperationChangeReason.java` |
| Inbound | `ports/inbound/agent/ConnectorOperationDisableCommand.java` |
| Inbound | `ports/inbound/agent/ConnectorCredentialBindingCommand.java` |
| Outbound | `ports/outbound/agent/ConnectorCredentialBindingRepositoryPort.java` |
| Kernel service | `kernel/application/agent/connector/KernelOpenApiConnectorImportService.java` 扩展 disable/bind/enable 校验 |
| JDBC | `JdbcConnectorCredentialBindingRepositoryAdapter.java` |
| Web | `SeahorseOpenApiConnectorController.java` 扩展 disable/binding endpoints |
| Starter | `SeahorseAgentCredentialAutoConfiguration.java` 和 repository auto config |

#### 16.3.3 数据库与状态

新增表 `sa_connector_credential_binding`：

| 字段 | 规则 |
| --- | --- |
| `binding_id` | 主键。 |
| `tenant_id` | 必填。 |
| `connector_id` | 必填。 |
| `operation_id` | 可空；为空表示 connector 默认绑定。 |
| `auth_type` | `CredentialAuthType` enum name。 |
| `credential_ref` | 必填，永远不存 material。 |
| `status` | `ACTIVE`、`DISABLED`、`ROTATED`、`INVALID`。 |
| `created_by/created_at` | 必填。 |
| `disabled_by/disabled_at` | 可空。 |

状态规则：

1. 一个 operation 同一 authType 只能有一个 `ACTIVE` binding。
2. 替换 binding 时旧记录标记 `ROTATED`，新记录为 `ACTIVE`。
3. disable operation 不删除 binding，只禁用 operation。
4. credential binding 失效后 enable 必须失败并返回具名 reason。

#### 16.3.4 API 合约

| Method | Path | 请求 | 响应约束 |
| --- | --- | --- | --- |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/disable` | `tenantId`、`operator`、`reason` | 返回 operation status，幂等。 |
| `PUT` | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | `tenantId`、`authType`、`credentialRef`、`operator` | 只返回 ref/status/authType。 |
| `GET` | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | query `tenantId` | 不返回 secret material。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/enable` | 扩展校验 | credential/policy 不满足时返回明确失败。 |

#### 16.3.5 Audit 设计

事件类型使用已有或新增 enum：

| 事件 | 触发点 | payload |
| --- | --- | --- |
| `CONNECTOR_IMPORTED` | OpenAPI spec 导入成功 | connectorId、versionId、operationCount、operator。 |
| `CONNECTOR_OPERATION_ENABLED` | operation enable 成功 | connectorId、operationId、risk、requiresApproval。 |
| `CONNECTOR_OPERATION_DISABLED` | operation disable 成功 | connectorId、operationId、reason、operator。 |
| `SECRET_USED` | credential provider resolve 时 | credentialRef、connectorId、operationId、agentId、runId。 |
| `SANDBOX_SESSION_CREATED` | sandbox session 创建 | sessionId、runtimeType、networkPolicy。 |
| `SANDBOX_EXECUTION_FINISHED` | sandbox execution terminal | executionId、status、artifactCount。 |

所有 payload 先经 `AuditRedactionPolicy.redact`；任何 key 包含 `secret`、`token`、`authorization`、`password` 时必须被遮蔽或替换为 ref。

#### 16.3.6 TDD 顺序

1. Kernel RED：`enableAuthenticatedOperationRequiresActiveBinding`。
2. Kernel RED：`enableHighRiskOperationRequiresApprovalPolicy`。
3. Kernel RED：`disableOperationIsIdempotent`。
4. Kernel RED：`replaceCredentialBindingRotatesPreviousBinding`。
5. JDBC RED：active binding uniqueness 与 findActive。
6. Web RED：binding response 不包含 material。
7. Audit RED：connector import/enable/disable 产生 redacted audit event。
8. Sandbox Audit RED：session/execution terminal 产生 redacted audit event。
9. GREEN：domain/port/service/JDBC/Web/starter。

#### 16.3.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelOpenApiConnectorServiceTests,ConnectorCredentialBindingTests,ConnectorAuditBridgeTests,KernelSandboxRuntimeServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcConnectorRepositoryAdapterTests,JdbcConnectorCredentialBindingRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseOpenApiConnectorControllerTests,SeahorseSandboxControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentCredentialAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 16.3.8 回滚与不变量

1. 可禁用 connector operation enable API，让已导入 operation 保持 disabled。
2. 不删除 credential binding 历史；停用用状态表达。
3. Connector service 不读取 secret material，只读取 `credentialRef` 与 binding status。
4. Sandbox 默认仍是 fail-closed/unsupported，不因 audit 接入而执行真实不安全代码。

### 16.4 Phase 7 方案：Governed Local Agent-as-Tool

#### 16.4.1 目标与退出条件

目标是实现本地 Agent-as-Tool 与 handoff 记录闭环。首版只做本地 target Agent 调用，不做远程 A2A client/server，不做 routing、circuit breaker、remote Agent Card。

退出条件：

1. target Agent 以 Tool Catalog entry 暴露，provider 使用既有 `REMOTE_AGENT` 或新增 `LOCAL_AGENT` enum 时必须兼容 Tool Gateway。
2. parent run 调用 target agent 必须经过 Tool Gateway 和 Policy。
3. handoff 记录包含 parentRunId、childRunId、sourceAgentId、targetAgentId、status、context policy snapshot。
4. context handoff 默认 summary-only，`SECRET` 不传播，`CONFIDENTIAL` 重新走 ACL。
5. 可按 parent run 查询 handoff 列表。

#### 16.4.2 文件边界

| 模块 | 文件 |
| --- | --- |
| Kernel domain | `kernel/domain/agent/handoff/AgentHandoff.java` |
| Kernel domain | `kernel/domain/agent/handoff/AgentHandoffStatus.java` |
| Kernel domain | `kernel/domain/agent/handoff/AgentHandoffFailureCode.java` |
| Kernel domain | `kernel/domain/agent/handoff/AgentHandoffContextPolicy.java` |
| Inbound | `ports/inbound/agent/AgentHandoffQueryInboundPort.java` |
| Inbound | `ports/inbound/agent/AgentHandoffDecisionInboundPort.java` |
| Outbound | `ports/outbound/agent/AgentHandoffRepositoryPort.java` |
| Outbound | `ports/outbound/agent/MeshPolicyPort.java` |
| Kernel service | `kernel/application/agent/handoff/KernelAgentHandoffService.java` |
| Kernel adapter | `kernel/application/agent/handoff/LocalAgentAsToolPort.java` |
| JDBC | `JdbcAgentHandoffRepositoryAdapter.java` |
| Web | `SeahorseAgentHandoffController.java` |
| Starter | Tool Catalog 注册与 handoff service wiring |

#### 16.4.3 调用流

```text
AgentLoop
  -> ToolGatewayPort.invoke(toolId = agent:<targetAgentId>)
  -> ToolPolicyPort.decide
  -> LocalAgentAsToolPort.invoke
  -> KernelAgentHandoffService.createFromToolInvocation
  -> AgentRunInboundPort.startRun(child run)
  -> AgentHandoffRepositoryPort.markRunning(childRunId)
```

禁止路径：

1. Web 不能提供 `POST /api/agent-handoffs` 来创建 handoff。
2. `KernelAgentHandoffService` 不能直接调用 `ToolPort.invoke`。
3. `LocalAgentAsToolPort` 不能绕过 `AgentRunInboundPort` 直接执行 `KernelAgentLoop`。

#### 16.4.4 Context 传播设计

`AgentHandoffContextPolicy` 字段：

| 字段 | 规则 |
| --- | --- |
| `mode` | `SUMMARY_ONLY`、`RESOURCE_REFS_ONLY`、`SUMMARY_WITH_ALLOWED_REFS`。首版默认 `SUMMARY_ONLY`。 |
| `maxSummaryChars` | 使用 `AgentHandoffLimits.DEFAULT_MAX_SUMMARY_CHARS`。 |
| `maxDepth` | 使用 `AgentHandoffLimits.DEFAULT_MAX_DEPTH`。 |
| `allowedSensitivity` | 最高 `INTERNAL`，除非 ACL 明确允许 `CONFIDENTIAL`。 |
| `includeToolResultSummary` | 默认 true，但必须 redacted。 |

传播规则：

1. `SECRET` item 永不传递。
2. `CONFIDENTIAL` item 必须调用 `ResourceAccessPolicyPort.decide` 重新判断 target subject。
3. message history 默认不传，只传 `inputSummary`。
4. tool result 只传 `resultSummary`，不传 raw payload。
5. provenance/citation 只传 resource ref，不传内部 credential。

#### 16.4.5 数据库与 API

`sa_agent_handoff`：

| 字段 | 规则 |
| --- | --- |
| `handoff_id` | 主键。 |
| `tenant_id` | 必填。 |
| `parent_run_id` | 必填。 |
| `child_run_id` | child run 创建后写入。 |
| `source_agent_id` | 必填。 |
| `target_agent_id` | 必填。 |
| `tool_invocation_id` | 必填或可空兼容历史；新路径必须写。 |
| `status` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。 |
| `failure_code` | 失败时使用 enum name。 |
| `input_summary` | 必填，已裁剪。 |
| `context_policy_json` | 策略快照。 |
| `created_at/finished_at` | 时间。 |

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-runs/{runId}/handoffs` | parent run 下 handoff 列表。 |
| `GET` | `/api/agent-handoffs/{handoffId}` | handoff 详情。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 幂等取消未 terminal handoff。 |

#### 16.4.6 TDD 顺序

1. Domain RED：terminal handoff 不可回退。
2. Policy RED：target disabled/unpublished/cross-tenant/depth exceeded 返回具名 failure。
3. Context RED：secret 丢弃、confidential 重新 ACL、summary 长度 clamp。
4. Service RED：tool invocation 创建 handoff 与 child run。
5. Service RED：child run terminal 回写 handoff status。
6. JDBC RED：parent run 查询按 created_at 排序。
7. Web RED：没有创建 API，只能查询/cancel。
8. Audit RED：handoff created/finished 事件 payload 不含 raw context。
9. GREEN：domain/ports/service/JDBC/Web/starter。

#### 16.4.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,AgentHandoffContextPolicyTests,DefaultMeshPolicyPortTests,KernelAgentHandoffServiceTests,LocalAgentAsToolPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 16.4.8 回滚与不变量

1. 关闭 Agent-as-Tool tool provider 后，handoff 记录保留为历史。
2. `AgentRun` 仍是 child run 事实来源，handoff 只是 parent-child 关系和上下文策略快照。
3. 远程 A2A 不在本阶段引入；不要提前创建 remote mesh 通用抽象。
4. 循环 handoff 由 `MeshPolicyPort` 和 depth limit 拦截。

### 16.5 Phase 8 方案：Eval Gate、Quota/SRE、Canary/Pilot Gate

#### 16.5.1 目标与退出条件

目标是把 Phase 8 剩余工作拆成三个可验收子切片，但仍作为一个阶段方案管理：

1. Phase 8B：Agent Eval Gate，把 retrieval/memory eval 结果转成 publish gate 可读的 Agent eval summary。
2. Phase 8C：Quota/Cost/SRE，补最小 quota 决策、成本追加记录和健康聚合。
3. Phase 8D：Canary/Pilot Gate，补 rollout 记录和企业试点准入报告。

退出条件：

1. Production Gate 不再只靠 warn bridge 判断 eval/quota/health。
2. cost usage append-only，可按 tenant/agent/run 聚合。
3. canary 不修改 `AgentVersion`，只写 rollout 和 activation/routing 记录。
4. pilot readiness report 能回答 owner、published version、tool risk、ACL、eval、quota、audit、rollback、disable switch 是否满足。

#### 16.5.2 Phase 8B 文件与规则

文件：

| 模块 | 文件 |
| --- | --- |
| Kernel domain | `kernel/domain/agent/eval/AgentEvalSummary.java` |
| Kernel domain | `kernel/domain/agent/eval/AgentEvalType.java` |
| Kernel domain | `kernel/domain/agent/eval/AgentEvalStatus.java` |
| Outbound | `ports/outbound/agent/AgentEvalSummaryRepositoryPort.java` |
| Outbound | `ports/outbound/agent/AgentEvalStatusPort.java` |
| Inbound | `ports/inbound/agent/AgentEvalQueryInboundPort.java` |
| Kernel service | `kernel/application/agent/eval/KernelAgentEvalQueryService.java` |
| JDBC | `JdbcAgentEvalSummaryRepositoryAdapter.java` |
| Web | `SeahorseAgentEvalController.java` |

规则：

1. Gate 读取 eval summary，不在 publish request 中同步跑重型 eval。
2. 高风险或含 write tool 的 Agent 没有 eval summary 时为 `FAIL`。
3. 低风险 knowledge assistant 没有 eval summary 时可为 `WARN`。
4. eval summary 是 snapshot，不因后续 eval 重跑改写历史 gate report。

TDD：

1. `AgentEvalSummaryTests` 覆盖 score/threshold/status。
2. `KernelProductionGateServiceTests` 覆盖 high-risk NOT_RUN fail 和 low-risk NOT_RUN warn。
3. `JdbcAgentEvalSummaryRepositoryAdapterTests` 覆盖 latest by agent/version/type。
4. `SeahorseAgentEvalControllerTests` 覆盖只读查询。

#### 16.5.3 Phase 8C 文件与规则

文件：

| 模块 | 文件 |
| --- | --- |
| Kernel domain | `kernel/domain/agent/quota/QuotaPolicy.java` |
| Kernel domain | `kernel/domain/agent/quota/QuotaScopeType.java` |
| Kernel domain | `kernel/domain/agent/quota/QuotaDecisionEffect.java` |
| Kernel domain | `kernel/domain/agent/cost/CostUsageRecord.java` |
| Kernel domain | `kernel/domain/agent/cost/CostUsageSource.java` |
| Kernel domain | `kernel/domain/agent/sre/SreHealthSnapshot.java` |
| Outbound | `QuotaPolicyRepositoryPort`、`QuotaDecisionPort`、`CostUsageRepositoryPort`、`SreHealthContributorPort` |
| Inbound | `QuotaManagementInboundPort`、`CostUsageQueryInboundPort`、`SreHealthQueryInboundPort` |
| Kernel service | `KernelQuotaDecisionService`、`KernelCostUsageQueryService`、`KernelSreHealthQueryService` |
| JDBC | `JdbcQuotaPolicyRepositoryAdapter.java`、`JdbcCostUsageRepositoryAdapter.java` |
| Web | `SeahorseQuotaController.java`、`SeahorseSreHealthController.java` |

规则：

1. quota 是 Policy 的附加约束，不替代 Tool Policy。
2. no policy 的默认结果必须保守：低风险 `WARN`，高风险 `REQUIRE_APPROVAL` 或 `DENY` 由 `QuotaDefaults` 常量控制。
3. cost usage 只追加，不做账单结算。
4. SRE health 只读聚合，不修改任何依赖系统状态。
5. Production Gate 的 `QUOTA_CONFIGURED`、`SRE_HEALTH_GREEN` 改为读取真实 port。

TDD：

1. `QuotaPolicyTests` 覆盖窗口与 effect。
2. `KernelQuotaDecisionServiceTests` 覆盖 tenant/agent/user/tool/model/run scope。
3. `CostUsageRecordTests` 覆盖 append-only 聚合字段。
4. `KernelSreHealthQueryServiceTests` 覆盖 contributors 聚合 GREEN/WARN/RED。
5. JDBC/Web tests 覆盖分页、聚合、状态映射。

#### 16.5.4 Phase 8D 文件与规则

文件：

| 模块 | 文件 |
| --- | --- |
| Kernel domain | `kernel/domain/agent/rollout/AgentVersionRollout.java` |
| Kernel domain | `kernel/domain/agent/rollout/AgentRolloutStatus.java` |
| Kernel domain | `kernel/domain/agent/rollout/AgentRolloutFailureCode.java` |
| Kernel domain | `kernel/domain/agent/rollout/AgentRolloutLimits.java` |
| Kernel domain | `kernel/domain/agent/pilot/EnterprisePilotReadinessReport.java` |
| Kernel domain | `kernel/domain/agent/pilot/EnterprisePilotReadinessCheckCode.java` |
| Outbound | `AgentRolloutRepositoryPort`、`EnterprisePilotReadinessRepositoryPort` |
| Inbound | `AgentRolloutInboundPort`、`EnterprisePilotReadinessInboundPort` |
| Kernel service | `KernelAgentRolloutService`、`KernelEnterprisePilotReadinessService` |
| JDBC | `JdbcAgentRolloutRepositoryAdapter.java`、`JdbcEnterprisePilotReadinessRepositoryAdapter.java` |
| Web | `SeahorseAgentRolloutController.java`、`SeahorseEnterprisePilotReadinessController.java` |

规则：

1. canary traffic percent 必须在 `AgentRolloutLimits.MIN_PERCENT` 和 `MAX_PERCENT` 内。
2. `PROMOTED`、`ROLLED_BACK`、`FAILED` 为 terminal status。
3. promote 前必须读取 latest Production Gate report。
4. rollback 复用 Phase 6 `AgentVersionActivationRepositoryPort`，不创建第二套 rollback owner。
5. pilot report 只保存检查结果和 ref，不复制大段 report payload 或 prompt。

API：

| Method | Path |
| --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/canary` |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` |
| `POST` | `/api/agents/{agentId}/pilot-readiness` |
| `GET` | `/api/agents/{agentId}/pilot-readiness/latest` |

#### 16.5.5 Phase 8 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentEvalSummaryTests,KernelProductionGateServiceTests,QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelSreHealthQueryServiceTests,AgentVersionRolloutTests,KernelAgentRolloutServiceTests,EnterprisePilotReadinessReportTests,KernelEnterprisePilotReadinessServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentEvalSummaryRepositoryAdapterTests,JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests,JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentEvalControllerTests,SeahorseQuotaControllerTests,SeahorseSreHealthControllerTests,SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 16.5.6 回滚与不变量

1. Eval、quota、SRE、rollout、pilot 都通过小 port 接入 Production Gate；Gate service 只聚合，不拥有这些事实。
2. Canary 关闭后不影响普通 publish/rollback。
3. Pilot report 是准入证据，不自动发布 Agent。
4. Cost usage 和 audit 都是 append-only，修正只能追加反向事件或新记录。

### 16.6 最终完成判定

AI Infra 不能只凭某个阶段测试通过就宣称完成。完整完成需要满足：

1. Phase 6、Phase 3、Phase 5、Phase 7、Phase 8B/C/D 的 focused tests 均通过。
2. `git diff --check` 无错误。
3. 所有新增 API 有 Web test 覆盖。
4. 所有新增 repository 有 JDBC adapter test 覆盖。
5. starter auto configuration 覆盖新 port wiring。
6. 审计接入场景有 redaction 测试。
7. 文档中的 bridge/noop/warn 退休条件被实现或明确保留为非生产路径。

## 17. 2026-05-26 Phase 3/6 完成后的未完成阶段设计开发方案

本节是在继续深读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、当前 worktree 代码证据和近期 Aegis 工作记录之后追加的最新版执行入口。它优先级高于第 10-16 节中已经被实现进度覆盖的旧判断。

当前校准结论：

| 阶段 | 最新判断 | 是否仍需补设计开发方案 |
| --- | --- | --- |
| Phase 0 | 架构基线已形成，后续只做 ADR/基线同步。 | 否。 |
| Phase 1 | Agent Registry、Version、Run、Step 主闭环已有实现基础。 | 否。 |
| Phase 2 | Tool Gateway、Tool Catalog、Policy、Tool audit 主闭环已有实现基础。 | 否。 |
| Phase 3 | Approval、checkpoint、resume、lease、worker tick 已有实现与聚焦验证记录。 | 不再作为下一阶段主线；保留回归与 worker 运行证据硬化。 |
| Phase 4 | Resource ACL 管理与 dry-run/provenance 已有实现痕迹。 | 只补“审计接入与 DB 约束收口”方案。 |
| Phase 5 | OAuth、OpenAPI import、Sandbox foundation 已有；connector disable、credential binding、统一审计仍缺。 | 是。 |
| Phase 6 | Agent Factory kernel/JDBC/Web/starter、publish-ready、rollback、catalog 已有实现与聚焦验证记录。 | 不再作为下一阶段主线；保留与 Phase 8 gate 的后续组合。 |
| Phase 7 | Local Agent-as-Tool、handoff、context handoff policy、mesh policy 仍未形成代码主线。 | 是。 |
| Phase 8 | Audit Ledger/Production Gate foundation 已有；Agent eval、quota/cost/SRE、canary/pilot gate 仍未闭环。 | 是。 |

### 17.1 Phase 4 收口方案：Resource ACL Audit 与 DB 约束硬化

#### 17.1.1 目标

Phase 4 不再新增权限语义。目标是把已经存在的 `ResourceAclRule`、ACL-backed policy、dry-run import 和 Context provenance 接到生产证据链上，补齐数据库层约束和 Audit Ledger 接入点。

退出条件：

1. `sa_resource_acl_rule` 对 enum 字段、自然键和状态有数据库约束或唯一索引保护。
2. `ResourceAclRepositoryPort` 的替代实现必须保持相同的 deny-wins、priority、expiresAt、status 语义。
3. create、disable、dry-run import 的摘要事件可写入 Phase 8 Audit Ledger。
4. `ContextPack` item 的 `aclDecisionId`、resource ref、source ref 能支持后续 `CONTEXT_ACCESSED` 事件。
5. 不改变 `ContextPack`、`ContextItem`、`AccessDecision` 的既有不变量。

#### 17.1.2 文件边界

| 层 | 文件 |
| --- | --- |
| Kernel domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/context/ResourceAclNaturalKey.java` |
| Kernel domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/context/ResourceAclImportDryRunReport.java` |
| Kernel application | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/context/KernelResourceAclManagementService.java` |
| Outbound port | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/ResourceAclRepositoryPort.java` |
| Future audit bridge | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/audit/AuditRedactionPolicy.java` |
| JDBC | `seahorse-agent-adapter-repository-jdbc/src/main/resources/META-INF/seahorse-agent/sql/agent-registry-run-store-postgresql.sql` |
| JDBC test | `seahorse-agent-adapter-repository-jdbc/src/test/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcResourceAclRepositoryAdapterTests.java` |

#### 17.1.3 关键规则

1. `ResourceAclRuleStatus`、`ResourceAclRuleScope`、`ResourceAclRuleConflictPolicy` 继续作为 enum；SQL 不使用自由文本状态。
2. 同一 tenant/resource/subject/action 下最多允许一个 active exact rule；历史 disabled/expired 记录可保留。
3. dry-run 只返回 `ALLOW`、`DENY`、`CONFLICT`、`DUPLICATE`、`INVALID` 等具名状态，不执行写入。
4. Audit payload 只保存 ruleId、naturalKey、effect、status、operator、reason、importSummary，不保存批量导入原始文件。
5. Phase 8 Audit 未接入前，Phase 4 不伪造审计成功；只保留可组合的事件创建方法或 bridge port。

#### 17.1.4 TDD 顺序

1. 扩展 `ResourceAclRuleTests`：验证 active natural key uniqueness 计算、disabled/expired 不参与 active 冲突。
2. 扩展 `KernelResourceAclManagementServiceTests`：验证 dry-run 报告、create/disable 生成可审计摘要。
3. 扩展 `JdbcResourceAclRepositoryAdapterTests`：验证 enum check、active unique index、findEffective 排序。
4. Phase 8 Audit Ledger 完成后，新增 `ResourceAclAuditBridgeTests`：验证 `RESOURCE_ACL_CHANGED` 和 `CONTEXT_ACCESSED` payload 被 redacted。
5. 保持 starter 自定义 `ResourceAccessPolicyPort` 替换语义不变。

#### 17.1.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclRuleTests,KernelResourceAclManagementServiceTests,AclBackedResourceAccessPolicyPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 17.1.6 回滚边界

新增约束只作用于 ACL 表；不修改 ContextPack schema。Audit bridge 可独立关闭，关闭后 ACL 管理功能继续可用但不能声明生产审计闭环完成。

### 17.2 Phase 5 方案：Connector Disable、Credential Binding 与 Audit 接入

#### 17.2.1 目标

Phase 5 当前不是从零实现 OpenAPI/OAuth/Sandbox，而是补齐外部系统接入的安全运营余项：operation disable、credential binding、启用前校验、connector/sandbox 关键动作写 Audit Ledger。

退出条件：

1. OpenAPI operation import 后默认不可直接执行，必须显式 enable。
2. authenticated operation 没有 active credential binding 时不能 enable。
3. high risk operation 没有 approval policy 或人工确认策略时不能 enable。
4. disabled operation 不能被 Tool Gateway 调用；disable 操作幂等。
5. credential binding 只保存 `credentialRef`，永远不保存 secret material。
6. connector import/enable/disable/binding、sandbox session/execution terminal 都有 redacted audit event。

#### 17.2.2 领域对象与端口

新增或确认以下对象，放在小接口边界内：

| 类型 | 文件 |
| --- | --- |
| Domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/connector/ConnectorCredentialBinding.java` |
| Domain enum | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/connector/ConnectorCredentialBindingStatus.java` |
| Domain enum | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/connector/ConnectorOperationChangeReason.java` |
| Inbound command | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/ConnectorOperationDisableCommand.java` |
| Inbound command | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/ConnectorCredentialBindingCommand.java` |
| Outbound port | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/ConnectorCredentialBindingRepositoryPort.java` |
| Application | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/connector/KernelOpenApiConnectorService.java` |
| JDBC | `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcConnectorCredentialBindingRepositoryAdapter.java` |
| Web | `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseOpenApiConnectorController.java` |

如果当前服务类名称与上表不同，按现有命名扩展，不能为了本方案重命名已通过测试的 owner。

#### 17.2.3 数据库设计

新增表 `sa_connector_credential_binding`：

| 字段 | 规则 |
| --- | --- |
| `binding_id` | 主键。 |
| `tenant_id` | 必填。 |
| `connector_id` | 必填。 |
| `operation_id` | 可空；为空表示 connector 默认 binding。 |
| `auth_type` | `CredentialAuthType` enum name。 |
| `credential_ref` | 必填；禁止保存 material/token。 |
| `status` | `ACTIVE`、`DISABLED`、`ROTATED`、`INVALID`。 |
| `created_by` / `created_at` | 必填。 |
| `disabled_by` / `disabled_at` | 可空。 |

索引要求：

1. active binding 唯一性：`tenant_id + connector_id + operation_id + auth_type + status=ACTIVE`。
2. 查询索引：`tenant_id + connector_id + operation_id + status`。
3. 不使用 JSON 字段保存 secret 或 OAuth token。

#### 17.2.4 API 合约

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/disable` | 幂等禁用 operation，返回 operation 快照。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/enable` | 执行 credential/policy/risk 校验后启用。 |
| `PUT` | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | 绑定或替换 credential ref。 |
| `GET` | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | 查询 active binding，不返回 material。 |

响应约束：

1. `credentialRef` 可以返回，`secret`、`token`、`authorization`、`password` 相关字段不得返回。
2. enable 失败必须返回具名 reason，例如 `MISSING_CREDENTIAL_BINDING`、`HIGH_RISK_APPROVAL_REQUIRED`、`OPERATION_DISABLED_BY_POLICY`。
3. disable 已禁用 operation 返回当前 disabled 快照，不报错。

#### 17.2.5 Audit 接入

事件类型使用 enum，不使用魔法字符串：

| 事件 | 触发点 | 最小 payload |
| --- | --- | --- |
| `CONNECTOR_IMPORTED` | import 成功 | connectorId、versionId、operationCount、operator。 |
| `CONNECTOR_OPERATION_ENABLED` | enable 成功 | connectorId、operationId、riskLevel、requiresApproval。 |
| `CONNECTOR_OPERATION_DISABLED` | disable 成功 | connectorId、operationId、reason、operator。 |
| `CONNECTOR_CREDENTIAL_BOUND` | binding 创建或替换 | connectorId、operationId、authType、credentialRef、status。 |
| `SANDBOX_SESSION_CREATED` | sandbox session 创建 | sessionId、runtimeType、networkPolicy。 |
| `SANDBOX_EXECUTION_FINISHED` | sandbox execution terminal | executionId、status、artifactCount。 |

所有 payload 先经过 `AuditRedactionPolicy.redact`；redaction policy 归 Phase 8 audit owner，不复制到 connector service。

#### 17.2.6 TDD 顺序

1. Kernel RED：`enableAuthenticatedOperationRequiresActiveBinding`。
2. Kernel RED：`enableHighRiskOperationRequiresApprovalPolicy`。
3. Kernel RED：`disableOperationIsIdempotent`。
4. Kernel RED：`replaceCredentialBindingRotatesPreviousBinding`。
5. JDBC RED：active binding 唯一性与 `findActive` 查询。
6. Web RED：credential binding 响应不包含 material/token。
7. Audit RED：connector import/enable/disable/binding 生成 redacted event。
8. Sandbox audit RED：session created 与 execution terminal 生成 redacted event。
9. GREEN：domain、port、service、JDBC、Web、starter。

#### 17.2.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelOpenApiConnectorServiceTests,ConnectorCredentialBindingTests,ConnectorAuditBridgeTests,KernelSandboxRuntimeServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcConnectorRepositoryAdapterTests,JdbcConnectorCredentialBindingRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseOpenApiConnectorControllerTests,SeahorseSandboxControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentCredentialAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 17.2.8 回滚边界

禁用 enable API 后，已导入 operation 保持 disabled 或现状；binding 历史不删除，只通过 status 表达。Connector service 不解析 secret material；真实 secret 仍由 Credential/SecretStore owner 管理。

### 17.3 Phase 7 方案：Governed Local Agent-as-Tool 与 Handoff

#### 17.3.1 目标

Phase 7 首个可执行目标是本地 Agent-as-Tool。不要直接做远程 A2A client/server、Agent Card 注册、Agent Mesh 控制面或自由多 Agent 聊天。一个 Agent 调用另一个 Agent 必须表现为一次受 Tool Gateway、Policy、Context ACL、Audit 约束的工具调用。

退出条件：

1. 目标 Agent 以 Tool Catalog entry 暴露，provider 使用 enum，例如 `LOCAL_AGENT` 或兼容现有 `REMOTE_AGENT`。
2. parent run 调用 target Agent 必须经过 Tool Gateway，不允许应用服务直接调用 target executor。
3. handoff 记录保存 parentRunId、childRunId、sourceAgentId、targetAgentId、status、context policy snapshot。
4. context handoff 默认 summary-only；`SECRET` 永不传播；`CONFIDENTIAL` 必须重新走 ACL。
5. 可按 parent run 查询 handoff 列表，可幂等取消未完成 handoff。

#### 17.3.2 文件边界

| 层 | 文件 |
| --- | --- |
| Kernel domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/handoff/AgentHandoff.java` |
| Kernel enum | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/handoff/AgentHandoffStatus.java` |
| Kernel enum | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/handoff/AgentHandoffFailureCode.java` |
| Kernel domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/handoff/AgentHandoffContextPolicy.java` |
| Inbound | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/AgentHandoffQueryInboundPort.java` |
| Outbound | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/AgentHandoffRepositoryPort.java` |
| Outbound | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/MeshPolicyPort.java` |
| Kernel service | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/handoff/KernelAgentHandoffService.java` |
| Tool adapter | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/handoff/LocalAgentAsToolPort.java` |
| JDBC | `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentHandoffRepositoryAdapter.java` |
| Web | `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentHandoffController.java` |

#### 17.3.3 调用流

```text
Parent Agent step
  -> ToolGatewayPort.invoke(toolId = agent:<targetAgentId>)
  -> ToolPolicyPort.decide(...)
  -> LocalAgentAsToolPort.invoke(...)
  -> MeshPolicyPort.decide(source, target, depth, tenant)
  -> KernelAgentHandoffService.create(...)
  -> AgentRunInboundPort.startRun(child run)
  -> AgentHandoffRepositoryPort.markRunning(childRunId)
```

禁止路径：

1. Web 不提供 `POST /api/agent-handoffs` 创建 handoff。
2. `KernelAgentHandoffService` 不直接调用 `ToolPort.invoke`。
3. `LocalAgentAsToolPort` 不绕过 `AgentRunInboundPort` 直接执行 Agent loop。
4. handoff 不复制 child run 的事实；child run 事实仍归 `AgentRun` owner。

#### 17.3.4 Context Handoff Policy

`AgentHandoffContextPolicy` 使用具名常量和 enum：

| 字段 | 规则 |
| --- | --- |
| `mode` | `SUMMARY_ONLY`、`RESOURCE_REFS_ONLY`、`SUMMARY_WITH_ALLOWED_REFS`；默认 `SUMMARY_ONLY`。 |
| `maxSummaryChars` | `AgentHandoffLimits.DEFAULT_MAX_SUMMARY_CHARS`。 |
| `maxDepth` | `AgentHandoffLimits.DEFAULT_MAX_DEPTH`。 |
| `allowedSensitivity` | 默认最高 `INTERNAL`。 |
| `includeToolResultSummary` | 默认 true，但必须 redacted。 |

传播规则：

1. `SECRET` item 直接丢弃。
2. `CONFIDENTIAL` item 对 target subject 重新调用 `ResourceAccessPolicyPort`。
3. message history 不传 raw，只传 input summary。
4. tool result 只传 result summary，不传 raw payload。
5. provenance 只传 resource ref，不传 credential 或内部 auth header。

#### 17.3.5 数据库与 API

`sa_agent_handoff`：

| 字段 | 规则 |
| --- | --- |
| `handoff_id` | 主键。 |
| `tenant_id` | 必填。 |
| `parent_run_id` | 必填。 |
| `child_run_id` | child run 创建后写入。 |
| `source_agent_id` | 必填。 |
| `target_agent_id` | 必填。 |
| `tool_invocation_id` | 新路径必填。 |
| `status` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。 |
| `failure_code` | 失败时使用 enum name。 |
| `input_summary` | 必填，已裁剪。 |
| `context_policy_json` | 策略快照，不存 raw context。 |
| `created_at` / `finished_at` | 时间。 |

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-runs/{runId}/handoffs` | parent run 下 handoff 列表。 |
| `GET` | `/api/agent-handoffs/{handoffId}` | handoff 详情。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 幂等取消未完成 handoff。 |

#### 17.3.6 TDD 顺序

1. Domain RED：terminal handoff 不可回退。
2. Mesh policy RED：target disabled/unpublished/cross-tenant/depth exceeded 返回具名 failure。
3. Context RED：secret 丢弃、confidential 重新 ACL、summary 长度 clamp。
4. Service RED：tool invocation 创建 handoff 和 child run。
5. Service RED：child run terminal 回写 handoff status。
6. JDBC RED：按 parent run 查询并按 created_at、handoff_id 稳定排序。
7. Web RED：没有创建 API，只能查询和 cancel。
8. Audit RED：handoff created/finished payload 不含 raw context。
9. GREEN：domain、ports、service、JDBC、Web、starter。

#### 17.3.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,AgentHandoffContextPolicyTests,DefaultMeshPolicyPortTests,KernelAgentHandoffServiceTests,LocalAgentAsToolPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 17.3.8 回滚边界

关闭 Agent-as-Tool provider 后，handoff 历史保留；child run 仍作为普通 run 可查询。远程 A2A 不在本切片引入，因此 rollback 不涉及远程协议兼容。

### 17.4 Phase 8 方案：Eval、Quota/SRE、Canary/Pilot Gate

#### 17.4.1 目标

Phase 8 已有 Audit Ledger 和 Production Gate foundation 痕迹，但企业试点仍缺三类生产能力：

1. Agent Eval Gate：把 retrieval/memory eval 或人工导入 eval summary 转成 publish gate 可读证据。
2. Quota/Cost/SRE：补最小 quota 决策、成本 append-only 记录和健康聚合 API。
3. Canary/Pilot Gate：补 version rollout 记录和企业试点准入报告。

退出条件：

1. Production Gate 不再只靠 warn bridge 判断 eval/quota/health。
2. eval summary 是 snapshot，不被后续评估重跑改写。
3. cost usage append-only，可按 tenant/agent/run 聚合。
4. canary 不修改 `AgentVersion`，只写 rollout 和 activation/routing 记录。
5. pilot readiness report 能回答 owner、published version、tool risk、ACL、eval、quota、audit、rollback、disable switch 是否满足。

#### 17.4.2 Phase 8B：Agent Eval Gate

文件边界：

| 层 | 文件 |
| --- | --- |
| Kernel domain | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/eval/AgentEvalSummary.java` |
| Kernel enum | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/eval/AgentEvalType.java` |
| Kernel enum | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/eval/AgentEvalStatus.java` |
| Outbound | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/AgentEvalSummaryRepositoryPort.java` |
| Outbound | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/AgentEvalStatusPort.java` |
| Inbound | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent/AgentEvalQueryInboundPort.java` |
| Application | `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/eval/KernelAgentEvalQueryService.java` |
| JDBC | `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcAgentEvalSummaryRepositoryAdapter.java` |
| Web | `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentEvalController.java` |

规则：

1. Gate 读取 eval summary，不在 publish request 中同步运行重型 eval。
2. 高风险或含 write/delete/external-send tool 的 Agent 没有 eval summary 时为 `FAIL`。
3. 低风险 knowledge assistant 没有 eval summary 时可为 `WARN`。
4. eval summary 只保存指标摘要、dataset/run ref 和阈值结果，不复制完整评估输入输出。

#### 17.4.3 Phase 8C：Quota、Cost 与 SRE Health

文件边界：

| 层 | 文件 |
| --- | --- |
| Kernel domain | `kernel/domain/agent/quota/QuotaPolicy.java`、`QuotaScopeType.java`、`QuotaDecisionEffect.java` |
| Kernel domain | `kernel/domain/agent/cost/CostUsageRecord.java`、`CostUsageSource.java` |
| Kernel domain | `kernel/domain/agent/sre/SreHealthSnapshot.java`、`SreHealthStatus.java` |
| Outbound | `QuotaPolicyRepositoryPort`、`QuotaDecisionPort`、`CostUsageRepositoryPort`、`SreHealthContributorPort` |
| Inbound | `QuotaManagementInboundPort`、`CostUsageQueryInboundPort`、`SreHealthQueryInboundPort` |
| Application | `KernelQuotaDecisionService`、`KernelCostUsageQueryService`、`KernelSreHealthQueryService` |
| JDBC | `JdbcQuotaPolicyRepositoryAdapter.java`、`JdbcCostUsageRepositoryAdapter.java` |
| Web | `SeahorseQuotaController.java`、`SeahorseSreHealthController.java` |

规则：

1. quota 是 policy 的附加约束，不替代 Tool Policy。
2. no policy 的默认结果由 `QuotaDefaults` 控制；高风险不能 silently allow。
3. cost usage 只追加，不做账单结算，不修改历史记录。
4. SRE health 只读聚合，不修改依赖系统状态。
5. Production Gate 的 `QUOTA_CONFIGURED`、`SRE_HEALTH_GREEN` 改为读 port。

#### 17.4.4 Phase 8D：Canary 与 Enterprise Pilot Gate

文件边界：

| 层 | 文件 |
| --- | --- |
| Kernel domain | `kernel/domain/agent/rollout/AgentVersionRollout.java` |
| Kernel enum | `kernel/domain/agent/rollout/AgentRolloutStatus.java`、`AgentRolloutFailureCode.java` |
| Kernel constants | `kernel/domain/agent/rollout/AgentRolloutLimits.java` |
| Kernel domain | `kernel/domain/agent/pilot/EnterprisePilotReadinessReport.java` |
| Kernel enum | `kernel/domain/agent/pilot/EnterprisePilotReadinessCheckCode.java` |
| Outbound | `AgentRolloutRepositoryPort`、`EnterprisePilotReadinessRepositoryPort` |
| Inbound | `AgentRolloutInboundPort`、`EnterprisePilotReadinessInboundPort` |
| Application | `KernelAgentRolloutService`、`KernelEnterprisePilotReadinessService` |
| JDBC | `JdbcAgentRolloutRepositoryAdapter.java`、`JdbcEnterprisePilotReadinessRepositoryAdapter.java` |
| Web | `SeahorseAgentRolloutController.java`、`SeahorseEnterprisePilotReadinessController.java` |

规则：

1. canary percent 必须在 `AgentRolloutLimits.MIN_PERCENT` 和 `MAX_PERCENT` 内。
2. `PROMOTED`、`ROLLED_BACK`、`FAILED` 是 terminal status。
3. promote 前必须读取 latest Production Gate report。
4. rollback 复用 Phase 6 `AgentVersionActivationRepositoryPort`，不创建第二套 rollback owner。
5. pilot report 只保存 check result 和 ref，不复制 prompt、raw eval case 或大段工具响应。

API：

| Method | Path |
| --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/canary` |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` |
| `POST` | `/api/agents/{agentId}/pilot-readiness` |
| `GET` | `/api/agents/{agentId}/pilot-readiness/latest` |

#### 17.4.5 TDD 顺序

1. Eval RED：`AgentEvalSummaryTests` 覆盖 score、threshold、status。
2. Gate RED：high-risk no eval fail，low-risk no eval warn。
3. JDBC RED：latest eval by tenant/agent/version/type。
4. Web RED：eval 查询只读。
5. Quota RED：scope/window/effect 决策。
6. Cost RED：append-only record 和聚合字段。
7. SRE RED：contributors 聚合 `GREEN/WARN/RED`。
8. Rollout RED：percent 边界、terminal status、promote 前 gate check。
9. Pilot RED：准入检查项完整覆盖 owner、version、tool risk、ACL、eval、quota、audit、rollback、disable switch。
10. GREEN：domain、ports、services、JDBC、Web、starter。

#### 17.4.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentEvalSummaryTests,KernelProductionGateServiceTests,QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelSreHealthQueryServiceTests,AgentVersionRolloutTests,KernelAgentRolloutServiceTests,EnterprisePilotReadinessReportTests,KernelEnterprisePilotReadinessServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentEvalSummaryRepositoryAdapterTests,JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests,JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentEvalControllerTests,SeahorseQuotaControllerTests,SeahorseSreHealthControllerTests,SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 17.4.7 回滚边界

Eval、quota、SRE、rollout、pilot 都通过小 port 接入 Production Gate；Gate service 只聚合，不拥有事实。关闭 canary 后不影响普通 publish/rollback。Cost usage 和 audit 都是 append-only，修正只能追加反向事件或新记录。

### 17.5 最新推荐实现顺序

1. Phase 5 connector residual：先补 disable、credential binding、connector/sandbox audit，外部系统接入才能满足生产安全底线。
2. Phase 4 audit/DB hardening：把已完成的 Resource ACL 能力接入统一证据链，并用数据库约束兜住状态语义。
3. Phase 7 Local Agent-as-Tool：在 run、tool、catalog、worker、audit 基础足够后，落地本地 handoff。
4. Phase 8B Agent Eval Gate：把已有 retrieval/memory eval 升级为发布准入证据。
5. Phase 8C Quota/Cost/SRE：补运行成本与健康聚合。
6. Phase 8D Canary/Pilot Gate：最后做 rollout 和企业试点准入，避免在基础证据不足时过早扩大发布复杂度。

### 17.6 最新完成判定

AI Infra 完整完成至少需要：

1. Phase 5、Phase 7、Phase 8B/C/D focused tests 全部通过。
2. Phase 4 ACL hardening focused tests 通过，并且 Audit bridge 不再是伪通过。
3. `git diff --check` 无错误。
4. 所有新增 API 有 Web test 覆盖。
5. 所有新增 repository 有 JDBC adapter test 覆盖。
6. starter auto configuration 覆盖新增 port wiring。
7. 审计 payload redaction 有测试覆盖。
8. 文档中所有 `WARN bridge`、`unsupported`、`noop` 都有明确保留理由或退休条件。

## 18. 2026-05-26 深入研读后的未完成阶段执行级设计开发方案

本节是在重新阅读 `docs/company-agent/` 与 `docs/company-agent/ai-infra-phases/` 全量规划、差距分析、架构基线、测试基线、两篇企业 Agent 背景文章、`99-current-implementation-handoff.md` 以及当前第 17 节后追加的最新入口。它不再沿用“交接文档中的下一步就是 Approval API”的过时判断；当前完成度已经越过 Phase 3/6 的主闭环，剩余重点应收敛到 Phase 4 收口、Phase 5 connector 安全运营、Phase 7 本地 Agent-as-Tool、Phase 8B/C/D 生产化硬化。

### 18.1 当前未完成阶段判定

| 阶段 | 深读后的判定 | 本节是否补方案 |
| --- | --- | --- |
| Phase 0 | 架构 owner、术语、包边界和原则已经明确，后续只需要 ADR/基线同步。 | 否。 |
| Phase 1 | Agent Definition、Version、Run、Step、Repository/Web/Starter 主闭环已有实现痕迹。 | 否。 |
| Phase 2 | Tool Gateway、Tool Catalog、Policy、Approval required 与 invocation audit 主闭环已有实现痕迹。 | 否。 |
| Phase 3 | Approval query/decision、checkpoint、resume、lease、retry、worker tick 已出现，后续只作为 Phase 8 运行证据硬化。 | 否。 |
| Phase 4 | Resource ACL 管理、dry-run/provenance 已有，剩余是 DB 约束和 Audit Ledger 接入收口。 | 是，见 18.2。 |
| Phase 5 | OAuth、OpenAPI import、Sandbox foundation 已有，剩余是 connector disable、credential binding、connector/sandbox audit。 | 是，见 18.3。 |
| Phase 6 | Agent Factory、publish-ready、rollback、catalog 已有实现痕迹，后续只和 Phase 8 gate 组合。 | 否。 |
| Phase 7 | Local Agent-as-Tool、handoff、context handoff policy、mesh policy 仍未形成代码主线。 | 是，见 18.4。 |
| Phase 8 | Audit Ledger/Production Gate foundation 已有，Agent eval、quota/cost/SRE、canary/pilot gate 未闭环。 | 是，见 18.5。 |

共同实现约束：

1. Kernel 只依赖 domain、port 和 JDK；Spring、JDBC、Web、HTTP client 只能出现在 adapter 或 starter。
2. 状态、风险、动作类型、触发来源、失败原因、审计事件类型全部使用 enum 或具名常量。
3. 领域对象维护不变量；应用服务只编排；Repository port 只承诺持久化语义。
4. 不引入工作流引擎、远程 Agent mesh、复杂 JSON 类型、真实容器平台或新的大一统 `AgentService`。
5. 每个阶段必须先写 RED 测试，验证失败，再最小实现 GREEN。
6. 任何 `noop`、`warn bridge`、`unsupported` 都必须写明保留理由和退休条件。

### 18.2 Phase 4 详细方案：Resource ACL Audit 与 DB 约束收口

#### 18.2.1 目标与退出条件

Phase 4 不再新增新的权限语义，不重写 ContextPack，也不扩大 Resource ACL 的表达能力。目标是把现有 Resource ACL 管理能力硬化到生产证据链可用：

1. ACL rule 的状态、scope、effect、action、subject type、resource type 在数据库层有约束或适配器级强校验。
2. 同一 tenant/resource/subject/action 下不能存在语义重复且同时生效的规则。
3. dry-run import 能稳定报告 duplicate、conflict、expired、invalid enum、would-create、would-disable。
4. `ResourceAclRule` create/disable/import summary 能写入统一 Audit Ledger，payload 不含大段原始导入内容。
5. ContextPack 或 AccessDecision 的查询证据能关联到 `CONTEXT_ACCESSED` 或 `RESOURCE_ACL_CHANGED` 审计事件。

完成判定不是“ACL 能授权”，而是“企业能解释某条上下文为什么被看到，以及某条 ACL 规则是谁在什么时候变更的”。

#### 18.2.2 文件边界

| 层 | 修改或新增文件 | 责任 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/context/ResourceAclRule.java` | 保持状态、过期、disable 幂等、natural key 不变量。 |
| Domain | `ResourceAclNaturalKey.java` | 作为重复规则检测的唯一业务 key，不把拼接字符串散落在服务/JDBC 中。 |
| Domain | `ResourceAclImportDryRunReport.java`、`ResourceAclImportDryRunItem.java` | 表达 dry-run 结果、冲突项和原因码。 |
| Enum | `ResourceAclImportReasonCode.java` | `DUPLICATE_ACTIVE_RULE`、`CONFLICTING_EFFECT`、`INVALID_ENUM`、`EXPIRED_AT_IMPORT`、`WOULD_CREATE`、`WOULD_DISABLE`。 |
| Inbound | `ResourceAclManagementInboundPort.java` | 增加或确认 `dryRunImport`、`importRules` 查询入口。 |
| Outbound | `ResourceAclRepositoryPort.java` | 增加或确认 `findByNaturalKey`、`existsActiveDuplicate`、`findEffective`。 |
| Application | `KernelResourceAclManagementService.java` | 只编排校验、dry-run、save、audit；不复制领域判断。 |
| Audit | 复用 `KernelAuditLedgerService` 或小型 audit bridge port | 创建 `RESOURCE_ACL_CHANGED`、`CONTEXT_ACCESSED` 事件，统一走 redaction policy。 |
| JDBC | `JdbcResourceAclRepositoryAdapter.java`、PostgreSQL schema | 显式字段 SQL、索引、check constraint、natural key 查询。 |
| Web | `SeahorseResourceAclController.java` | 只做 DTO 到 command/query 转换；导入响应不返回敏感明细。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration`、`SeahorseAgentRegistryRepositoryAutoConfiguration` | 条件装配 ACL repository、management service、audit bridge。 |

#### 18.2.3 数据与 API 设计

新增或硬化索引：

```sql
CREATE INDEX idx_sa_resource_acl_natural_key
  ON sa_resource_acl_rule(
    tenant_id,
    resource_type,
    resource_id,
    subject_type,
    subject_id,
    action,
    status
  );
```

PostgreSQL 环境优先补 check constraint：

```sql
ALTER TABLE sa_resource_acl_rule
  ADD CONSTRAINT chk_sa_resource_acl_status
  CHECK (status IN ('ENABLED', 'DISABLED', 'EXPIRED'));

ALTER TABLE sa_resource_acl_rule
  ADD CONSTRAINT chk_sa_resource_acl_effect
  CHECK (effect IN ('ALLOW', 'DENY', 'MASK'));
```

如果 H2 或测试数据库不支持部分索引，唯一性在 repository adapter 和 domain service 双层保证，不用引入数据库方言分支到 kernel。

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/resource-acl-rules/dry-run-import` | 只校验，不落库，不写正式变更审计。 |
| `POST` | `/api/resource-acl-rules/import` | 根据 dry-run 规则落库，返回摘要和变更数。 |
| `GET` | `/api/resource-acl-rules` | 保持分页查询。 |
| `POST` | `/api/resource-acl-rules/{ruleId}/disable` | 幂等禁用并写审计。 |

#### 18.2.4 TDD 顺序

1. RED：`ResourceAclNaturalKeyTests` 覆盖 key 等价、不同 action/resource/subject 不等价。
2. RED：`ResourceAclImportDryRunReportTests` 覆盖 duplicate、conflict、invalid enum、expired、would-create。
3. GREEN：实现 domain/value object 和 reason enum。
4. RED：`KernelResourceAclManagementServiceTests` 覆盖 dry-run 不落库、import 才保存、disable 幂等、非 admin 拒绝。
5. GREEN：服务只组合 repository、current user、clock、audit bridge。
6. RED：`JdbcResourceAclRepositoryAdapterTests` 覆盖 natural key 查询、重复 active 规则防护、非法 enum 映射失败。
7. GREEN：schema、adapter、mapper。
8. RED：`SeahorseResourceAclControllerTests` 覆盖 dry-run/import/disable API。
9. GREEN：Web DTO 和 starter wiring。
10. RED：`ResourceAclAuditBridgeTests` 覆盖 payload redaction 和事件类型。
11. GREEN：接入 `RESOURCE_ACL_CHANGED`、`CONTEXT_ACCESSED`。

#### 18.2.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ResourceAclNaturalKeyTests,ResourceAclImportDryRunReportTests,KernelResourceAclManagementServiceTests,ResourceAclAuditBridgeTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseResourceAclControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 18.2.6 回滚与不变量

新增 DB 约束只作用于 ACL 表，不修改 ContextPack schema。若 Audit Ledger 暂时关闭，ACL 功能仍可用，但不能宣称 Phase 4 生产审计闭环完成。已导入规则不物理删除；修正通过 disable 或追加新规则表达。

### 18.3 Phase 5 详细方案：Connector Disable、Credential Binding 与统一审计

#### 18.3.1 目标与退出条件

Phase 5 当前不再从零实现 OAuth、OpenAPI import 或 Sandbox foundation；重点是补齐外部系统接入后的安全运营闭环：

1. OpenAPI operation 支持 disable，且 disable 后 ToolCatalog entry 不可被调用。
2. 需要认证的 operation 必须存在 active credential binding 才能 enable。
3. 高风险或 `requiresApproval=true` 的 operation enable 必须携带 approval policy 或明确 operator confirmation。
4. Credential binding 只保存 `credentialRef`，不保存 secret material、token、bearer value。
5. connector import、credential binding、operation enable/disable、sandbox session/execution terminal 都进入 Audit Ledger。

退出条件是“外部系统写能力默认不可误启用”，不是“能导入更多 OpenAPI spec”。

#### 18.3.2 领域对象、端口与服务组合

| 类型 | 名称 | 设计规则 |
| --- | --- | --- |
| Domain | `ConnectorCredentialBinding` | 字段包含 tenantId、connectorId、operationId、authType、credentialRef、status、boundBy、boundAt、rotatedAt。 |
| Enum | `ConnectorCredentialBindingStatus` | `ACTIVE`、`ROTATED`、`DISABLED`、`INVALID`。 |
| Command | `ConnectorCredentialBindingCommand` | Web/API 入参转 command；不接受 credential material。 |
| Command | `ConnectorOperationDisableCommand` | disable reason 使用 enum 或具名常量。 |
| Command | `ConnectorOperationEnableCommand` | 增加 `approvalPolicyId` 或 `operatorConfirmedRisk`，兼容空 body 但高风险必须失败。 |
| Outbound | `ConnectorCredentialBindingRepositoryPort` | `save`、`findActive`、`rotateActive`、`pageByOperation`。 |
| Application | `KernelOpenApiConnectorImportService` | 继续作为 connector 编排 owner；不要创建第二个 connector service。 |
| Audit | `KernelAuditLedgerService` | connector service 只提交结构化 event payload，由 audit owner 负责 redaction。 |

`ConnectorOperation` 领域对象增加 `disable(Instant now)`，必须幂等。启用前校验放在应用服务：auth binding、approval policy、ToolCatalog entry 保存、audit append 顺序必须清晰。

#### 18.3.3 数据库与 API 合约

新增表：

```sql
CREATE TABLE sa_connector_credential_binding (
  binding_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  connector_id VARCHAR(64) NOT NULL,
  operation_id VARCHAR(64) NOT NULL,
  auth_type VARCHAR(32) NOT NULL,
  credential_ref VARCHAR(128) NOT NULL,
  status VARCHAR(32) NOT NULL,
  bound_by VARCHAR(64) NOT NULL,
  bound_at TIMESTAMP NOT NULL,
  rotated_at TIMESTAMP
);

CREATE INDEX idx_sa_connector_credential_binding_operation
  ON sa_connector_credential_binding(tenant_id, connector_id, operation_id, auth_type, status);
```

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `PUT` | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | 替换 active binding，旧 binding 置为 `ROTATED`。 |
| `GET` | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | 查询 active binding 摘要，不返回 secret material。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/enable` | 低风险可空 body；高风险必须提供 approval policy 或确认字段。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/disable` | 幂等禁用 operation 和 ToolCatalog entry。 |

审计事件：

| 事件类型 | 触发点 | Payload 摘要 |
| --- | --- | --- |
| `CONNECTOR_IMPORTED` | OpenAPI spec 导入成功 | connectorId、versionId、operationCount、operator。 |
| `CONNECTOR_CREDENTIAL_BOUND` | active binding 替换 | connectorId、operationId、authType、credentialRef hash/ref、operator。 |
| `CONNECTOR_OPERATION_ENABLED` | operation enable 成功 | connectorId、operationId、toolId、riskLevel、approvalPolicyId。 |
| `CONNECTOR_OPERATION_DISABLED` | operation disable 成功 | connectorId、operationId、toolId、reason。 |
| `SANDBOX_SESSION_CREATED` | session 创建 | sessionId、runtimeType、networkPolicy、runId。 |
| `SANDBOX_EXECUTION_FINISHED` | execution terminal | executionId、status、artifactCount、failureCode。 |

#### 18.3.4 TDD 顺序

1. RED：`ConnectorCredentialBindingTests` 覆盖 active、rotate、disable、credentialRef 非空、secret material 不入领域对象。
2. GREEN：实现 domain 和 enum。
3. RED：`KernelOpenApiConnectorImportServiceTests#enableAuthenticatedOperationRequiresActiveBinding`。
4. RED：`KernelOpenApiConnectorImportServiceTests#enableHighRiskOperationRequiresApprovalPolicy`。
5. RED：`KernelOpenApiConnectorImportServiceTests#disableOperationIsIdempotentAndDisablesToolCatalog`。
6. RED：`KernelOpenApiConnectorImportServiceTests#replaceCredentialBindingRotatesPreviousBinding`。
7. GREEN：扩展 command、port、service；保持既有 import/page/list/enable 兼容。
8. RED：`JdbcConnectorCredentialBindingRepositoryAdapterTests` 覆盖 active 查询、rotate、非法 enum 失败。
9. GREEN：JDBC table、adapter、starter bean。
10. RED：`SeahorseOpenApiConnectorControllerTests` 覆盖 binding/enable/disable API，响应不含 token。
11. GREEN：controller DTO 和 Web contract。
12. RED：`ConnectorAuditBridgeTests`、`KernelSandboxRuntimeServiceTests` 覆盖 redacted audit events。
13. GREEN：audit append，失败时不吞掉核心业务异常。

#### 18.3.5 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ConnectorCredentialBindingTests,KernelOpenApiConnectorImportServiceTests,ConnectorAuditBridgeTests,KernelSandboxRuntimeServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcConnectorRepositoryAdapterTests,JdbcConnectorCredentialBindingRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseOpenApiConnectorControllerTests,SeahorseSandboxControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentCredentialAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 18.3.6 回滚与不变量

禁用 connector operation 不删除 operation、binding 或 audit 历史。Binding 历史 append/rotate，不物理覆盖。Connector service 不解析 secret material；真实 secret 仍归 Credential/SecretStore owner。Audit 不可用时，enable/disable 不应伪造成功审计；要返回明确 warning 或让验收保持未完成。

### 18.4 Phase 7 详细方案：Governed Local Agent-as-Tool 与 Handoff

#### 18.4.1 目标与非目标

Phase 7 首个可落地能力是本地 Agent-as-Tool：一个已发布 Agent 作为另一个 Agent 的工具被调用，调用过程仍经过 Tool Gateway、Policy、Context ACL、Run Store 和 Audit Ledger。

非目标：

1. 不做远程 A2A client/server。
2. 不注册 Agent Card。
3. 不做 Agent Mesh 控制面。
4. 不做自由多 Agent 聊天、debate、动态 workflow engine。
5. 不让 application service 直接绕过 Tool Gateway 调用 target Agent。

退出条件：

1. target Agent 以 `ToolCatalogEntry(provider=LOCAL_AGENT)` 或等价 enum 暴露。
2. parent run 调用 target Agent 时创建 handoff 记录和 child run。
3. context handoff 默认 summary-only；`SECRET` 永不传播，`CONFIDENTIAL` 必须重新 ACL。
4. handoff 可按 parent run 查询，可幂等 cancel 未完成 handoff。
5. handoff created/finished 有 redacted audit event。

#### 18.4.2 领域模型与端口

| 层 | 文件或对象 | 责任 |
| --- | --- | --- |
| Domain | `AgentHandoff` | parentRunId、childRunId、sourceAgentId、targetAgentId、toolInvocationId、status、failureCode、inputSummary、contextPolicySnapshot。 |
| Enum | `AgentHandoffStatus` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。 |
| Enum | `AgentHandoffFailureCode` | `TARGET_DISABLED`、`TARGET_UNPUBLISHED`、`CROSS_TENANT_DENIED`、`DEPTH_EXCEEDED`、`CONTEXT_DENIED`、`CHILD_RUN_FAILED`。 |
| Domain | `AgentHandoffContextPolicy` | mode、maxSummaryChars、maxDepth、allowedSensitivity、includeToolResultSummary。 |
| Constants | `AgentHandoffLimits` | `DEFAULT_MAX_DEPTH`、`DEFAULT_MAX_SUMMARY_CHARS`，禁止魔法数字。 |
| Inbound | `AgentHandoffQueryInboundPort` | detail、listByParentRun、cancel。 |
| Outbound | `AgentHandoffRepositoryPort` | save、findById、listByParentRun、markRunning、markTerminal。 |
| Outbound | `MeshPolicyPort` | 本地 source->target 授权、深度、租户、发布态判断。 |
| Application | `KernelAgentHandoffService` | 创建 handoff、启动 child run、回写 terminal、cancel。 |
| Tool adapter | `LocalAgentAsToolPort` | 实现 ToolPort，但内部只调用 handoff service 和 AgentRunInboundPort。 |

#### 18.4.3 调用流与安全规则

```text
Parent Agent step
  -> ToolGatewayPort.invoke(toolId = agent:<targetAgentId>)
  -> ToolPolicyPort.decide(...)
  -> LocalAgentAsToolPort.invoke(...)
  -> MeshPolicyPort.decide(source, target, tenant, depth)
  -> AgentHandoffContextPolicy.filter(...)
  -> KernelAgentHandoffService.create(...)
  -> AgentRunInboundPort.startRun(child run)
  -> AgentHandoffRepositoryPort.markRunning(childRunId)
  -> child terminal callback/tick
  -> AgentHandoffRepositoryPort.markTerminal(...)
```

Context 传播：

1. message history 不传 raw，只传 parent step input summary。
2. tool result 不传 raw payload，只传 redacted result summary。
3. `SECRET` context item 丢弃。
4. `CONFIDENTIAL` context item 对 target subject 调用 `ResourceAccessPolicyPort`，未 allow 则丢弃。
5. provenance 只传 resource ref、source id、acl decision id，不传 credential/header。

#### 18.4.4 数据库与 API

```sql
CREATE TABLE sa_agent_handoff (
  handoff_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  parent_run_id VARCHAR(64) NOT NULL,
  child_run_id VARCHAR(64),
  source_agent_id VARCHAR(64) NOT NULL,
  target_agent_id VARCHAR(64) NOT NULL,
  tool_invocation_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64),
  input_summary VARCHAR(1000) NOT NULL,
  context_policy_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);

CREATE INDEX idx_sa_agent_handoff_parent
  ON sa_agent_handoff(tenant_id, parent_run_id, created_at, handoff_id);
```

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-runs/{runId}/handoffs` | 查询 parent run 下的 handoff。 |
| `GET` | `/api/agent-handoffs/{handoffId}` | 查询详情。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 幂等取消非终态 handoff。 |

禁止新增 `POST /api/agent-handoffs` 直接创建 API；handoff 只能由 Tool Gateway 路径触发。

#### 18.4.5 TDD 顺序

1. RED：`AgentHandoffTests` 覆盖 terminal status 不可回退、cancel 幂等、failureCode 必须来自 enum。
2. RED：`AgentHandoffContextPolicyTests` 覆盖 secret 丢弃、confidential 重新 ACL、summary clamp、depth limit。
3. RED：`DefaultMeshPolicyPortTests` 覆盖 target disabled/unpublished/cross-tenant/depth exceeded。
4. RED：`LocalAgentAsToolPortTests` 验证调用路径必须来自 ToolGateway request，不直接暴露 create handoff API。
5. RED：`KernelAgentHandoffServiceTests` 覆盖创建 handoff、启动 child run、terminal 回写、cancel。
6. GREEN：domain、ports、service、local tool adapter。
7. RED：`JdbcAgentHandoffRepositoryAdapterTests` 覆盖 parent run 查询稳定排序、terminal update 幂等。
8. GREEN：JDBC table、adapter、starter bean。
9. RED：`SeahorseAgentHandoffControllerTests` 覆盖 query/cancel，并确认没有 create API。
10. GREEN：Web controller。
11. RED：`AgentHandoffAuditBridgeTests` 覆盖 audit payload 不含 raw context。
12. GREEN：`AGENT_HANDOFF_CREATED`、`AGENT_HANDOFF_FINISHED`。

#### 18.4.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,AgentHandoffContextPolicyTests,DefaultMeshPolicyPortTests,KernelAgentHandoffServiceTests,LocalAgentAsToolPortTests,AgentHandoffAuditBridgeTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 18.4.7 回滚与不变量

关闭 `LOCAL_AGENT` provider 后，已有 handoff 历史和 child run 保留为查询证据。远程 A2A 不在本阶段引入，因此不产生远程协议兼容债务。`AgentRun` 仍是 child run 的事实 owner，handoff 只保存协作关系，不复制 run 事实。

### 18.5 Phase 8 详细方案：Agent Eval、Quota/Cost/SRE、Canary/Pilot Gate

#### 18.5.1 目标与切片边界

Phase 8 已有 Audit Ledger 和 Production Gate foundation，因此剩余生产化工作拆成三个顺序子切片：

1. Phase 8B Agent Eval Gate：把已有 retrieval/memory eval 或人工导入结果变成 Agent publish gate 可读的 snapshot。
2. Phase 8C Quota/Cost/SRE：建立最小 quota 决策、append-only cost usage 和只读 SRE health 聚合。
3. Phase 8D Canary/Pilot Gate：建立 version rollout 与 enterprise pilot readiness report。

共同规则：

1. Production Gate 只聚合 port，不拥有 eval/quota/SRE/canary 事实。
2. 所有 summary/report 是 snapshot，历史不被重跑覆盖。
3. 成本、审计、rollout 都 append-only；修正只能追加反向事件或新记录。
4. 高风险 Agent 没有 eval/quota/health 证据时不能 silently pass。

#### 18.5.2 Phase 8B：Agent Eval Gate

领域与端口：

| 类型 | 名称 | 规则 |
| --- | --- | --- |
| Domain | `AgentEvalSummary` | tenantId、agentId、versionId、evalType、status、score、threshold、datasetRef、evalRunRef、createdAt。 |
| Enum | `AgentEvalType` | `RAG`、`MEMORY`、`TRAJECTORY`、`SAFETY`、`HITL`、`COST`。 |
| Enum | `AgentEvalStatus` | `PASS`、`WARN`、`FAIL`、`STALE`。 |
| Outbound | `AgentEvalSummaryRepositoryPort` | save snapshot、findLatest。 |
| Outbound | `AgentEvalStatusPort` | 供 Production Gate 查询 eval gate effect。 |
| Inbound | `AgentEvalQueryInboundPort` | 查询 latest summary 和历史 summary。 |
| Application | `KernelAgentEvalQueryService` | 只读查询和 summary 导入，不运行重型 eval。 |

API：

| Method | Path | 说明 |
| --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries` | 导入或保存 eval summary snapshot。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries/latest` | 查询 latest。 |

Gate 规则：

1. 高风险或包含 `WRITE`、`DELETE`、`EXTERNAL_SEND` tool 的 Agent 无 eval summary 时 `FAIL`。
2. 低风险 knowledge assistant 无 eval summary 时 `WARN`。
3. `AgentEvalStatus.FAIL` 直接阻断 publish/canary。
4. `STALE` 的判定使用具名常量，例如 `AgentEvalDefaults.MAX_SUMMARY_AGE`。

TDD：

1. RED：`AgentEvalSummaryTests` 覆盖 score/threshold/status/stale。
2. RED：`KernelProductionGateServiceTests` 覆盖 high-risk no eval fail、low-risk no eval warn。
3. RED：`JdbcAgentEvalSummaryRepositoryAdapterTests` 覆盖 latest by tenant/agent/version/type。
4. RED：`SeahorseAgentEvalControllerTests` 覆盖 summary 保存和 latest 查询。
5. GREEN：domain、port、service、JDBC、Web、starter。

#### 18.5.3 Phase 8C：Quota、Cost 与 SRE Health

领域与端口：

| 子域 | 对象 | 规则 |
| --- | --- | --- |
| Quota | `QuotaPolicy`、`QuotaScopeType`、`QuotaDecisionEffect` | quota 是 policy 附加约束，不替代 Tool Policy。 |
| Cost | `CostUsageRecord`、`CostUsageSource` | append-only，支持 tenant/agent/run 聚合。 |
| SRE | `SreHealthSnapshot`、`SreHealthStatus` | 只读聚合，`RED` 优先于 `WARN`，`WARN` 优先于 `GREEN`。 |
| Port | `QuotaPolicyRepositoryPort`、`QuotaDecisionPort`、`CostUsageRepositoryPort`、`SreHealthContributorPort` | 小接口隔离，不新增大 service。 |
| Inbound | `QuotaManagementInboundPort`、`CostUsageQueryInboundPort`、`SreHealthQueryInboundPort` | 管理/查询分离。 |

默认策略：

1. 无 quota policy 时，低风险默认 `WARN`，高风险默认 `REQUIRE_APPROVAL` 或 `DENY`，由 `QuotaDefaults` 明确控制。
2. run cost 超限不直接忽略；首版可返回 policy decision 给 runtime，后续再接暂停/approval。
3. cost usage 不做账单结算，只提供运营证据。
4. SRE health contributor 失败不能让整体假绿；未知 contributor 返回 `WARN`。

API：

| Method | Path | 说明 |
| --- | --- |
| `PUT` | `/api/quotas/{scopeType}/{scopeId}` | upsert quota policy。 |
| `GET` | `/api/cost/usage` | 按 tenant/agent/run 查询成本聚合。 |
| `GET` | `/api/sre/health` | 聚合依赖健康。 |

TDD：

1. RED：`QuotaPolicyTests` 覆盖 window、scope、limit、effect。
2. RED：`KernelQuotaDecisionServiceTests` 覆盖 no-policy defaults、高风险不 silent allow、超额决策。
3. RED：`CostUsageRecordTests` 覆盖 append-only 和金额/token 非负。
4. RED：`KernelSreHealthQueryServiceTests` 覆盖 contributor 聚合和 unknown/warn/red 优先级。
5. RED：JDBC/Web/starter 测试。
6. GREEN：domain、ports、services、JDBC、Web、starter。

#### 18.5.4 Phase 8D：Canary 与 Enterprise Pilot Gate

领域与端口：

| 类型 | 名称 | 规则 |
| --- | --- | --- |
| Domain | `AgentVersionRollout` | canary percent、status、startedBy、startedAt、finishedAt、gateReportId。 |
| Enum | `AgentRolloutStatus` | `CREATED`、`RUNNING`、`PAUSED`、`PROMOTED`、`ROLLED_BACK`、`FAILED`。 |
| Constants | `AgentRolloutLimits` | `MIN_PERCENT`、`MAX_PERCENT`、`DEFAULT_CANARY_PERCENT`。 |
| Domain | `EnterprisePilotReadinessReport` | owner、version、tool risk、ACL、eval、quota、audit、rollback、disable switch 检查结果。 |
| Enum | `EnterprisePilotReadinessCheckCode` | 每个检查项使用 enum。 |
| Outbound | `AgentRolloutRepositoryPort`、`EnterprisePilotReadinessRepositoryPort` | rollout/report 持久化。 |
| Application | `KernelAgentRolloutService`、`KernelEnterprisePilotReadinessService` | 编排 gate、activation rollback、report。 |

规则：

1. canary percent 必须在 `AgentRolloutLimits` 边界内。
2. `PROMOTED`、`ROLLED_BACK`、`FAILED` 是 terminal status。
3. promote 前必须读取 latest Production Gate report；没有 gate report 时 fail closed。
4. rollback 复用 Phase 6 `AgentVersionActivationRepositoryPort`，不创建第二套 rollback owner。
5. pilot report 只保存 check result、refs 和摘要，不复制 prompt、raw eval case、raw tool result。

API：

| Method | Path | 说明 |
| --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/canary` | 创建 canary rollout。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | gate 通过后推广。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | 复用 version activation rollback。 |
| `POST` | `/api/agents/{agentId}/pilot-readiness` | 生成准入报告。 |
| `GET` | `/api/agents/{agentId}/pilot-readiness/latest` | 查询 latest report。 |

TDD：

1. RED：`AgentVersionRolloutTests` 覆盖 percent 边界、terminal status、pause/rollback 幂等。
2. RED：`KernelAgentRolloutServiceTests` 覆盖 promote 前 gate check、rollback 复用 activation port。
3. RED：`EnterprisePilotReadinessReportTests` 覆盖检查项完整性。
4. RED：`KernelEnterprisePilotReadinessServiceTests` 覆盖 owner/version/tool risk/ACL/eval/quota/audit/rollback/disable switch。
5. RED：JDBC/Web/starter 测试。
6. GREEN：domain、ports、services、JDBC、Web、starter。

#### 18.5.5 Phase 8 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentEvalSummaryTests,KernelProductionGateServiceTests,QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelSreHealthQueryServiceTests,AgentVersionRolloutTests,KernelAgentRolloutServiceTests,EnterprisePilotReadinessReportTests,KernelEnterprisePilotReadinessServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentEvalSummaryRepositoryAdapterTests,JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests,JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentEvalControllerTests,SeahorseQuotaControllerTests,SeahorseSreHealthControllerTests,SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 18.5.6 回滚与不变量

Eval、quota、SRE、rollout、pilot 都通过独立 port 接入 Production Gate，关闭任一子系统后 Gate 必须返回明确 `WARN` 或 `FAIL`，不能伪造 `PASS`。Canary 关闭不影响普通 publish/rollback。Cost usage 和 rollout/report 历史不物理删除，修正通过追加新记录表达。

### 18.6 最新推荐实现顺序

| 顺序 | 阶段 | 为什么排这里 | 完成后得到什么 |
| --- | --- | --- | --- |
| 1 | Phase 5 connector residual | OpenAPI import 和 sandbox 已有基础，但外部系统 operation 仍缺 disable、credential binding、审计闭环。 | 外部系统接入满足默认禁用、凭据引用、可追责的安全底线。 |
| 2 | Phase 4 audit/DB hardening | ACL 已能管理，先用约束和审计把数据面证据补稳。 | Context/ACL 能回答“为什么看到”和“谁改了规则”。 |
| 3 | Phase 7 Local Agent-as-Tool | 依赖 run、tool、policy、context、audit 的基础事实。 | 本地多 Agent handoff 可用，远程 A2A 有本地协议基础。 |
| 4 | Phase 8B Agent Eval Gate | 需要 Agent/Tool/Context 基础稳定后才有真实 eval 对象。 | 发布门禁有 eval 证据，不再只靠 warn bridge。 |
| 5 | Phase 8C Quota/Cost/SRE | 需要真实 run/tool/agent 调用事实产生后才有成本和健康聚合价值。 | 运行成本、配额和健康证据可查询。 |
| 6 | Phase 8D Canary/Pilot Gate | 依赖 gate、eval、quota、audit、rollback 证据。 | 企业试点准入、canary、rollback 形成运营闭环。 |

### 18.7 最新完成判定

AI Infra 不能凭某个模块测试通过宣称完成。当前完整完成至少需要：

1. Phase 5 connector residual、Phase 4 audit/DB hardening、Phase 7 Local Agent-as-Tool、Phase 8B/C/D 的 focused tests 全部通过。
2. 所有新增 API 有 Web contract test，所有新增 repository 有 JDBC adapter test，所有新增 starter wiring 有 auto-configuration test。
3. Audit payload redaction 覆盖 connector、sandbox、ACL、handoff、eval/quota/rollout 关键事件。
4. `git diff --check` 无错误。
5. Kernel 新增代码没有 Spring/JDBC/Web/HTTP client 依赖。
6. 所有新增状态、风险、动作、失败原因、触发来源均为 enum 或具名常量。
7. 文档中所有 `WARN bridge`、`noop`、`unsupported` 都有退休条件，并且不能被用于声称生产能力完成。

## 19. 2026-05-26 未完成阶段执行卡补充方案

本节是在再次深读 `docs/company-agent/`、`docs/company-agent/ai-infra-phases/`、最新第 18 节，以及当前 worktree 代码表面后追加的执行卡。第 18 节已经给出阶段级设计，本节把每个未完成阶段进一步拆到可直接进入 TDD 的开发方案粒度：目标、具体文件、领域/端口/API/表、红绿步骤、验收命令和不做事项。

第 19 节作为新的剩余阶段入口。Phase 0-3 与 Phase 6 当前不再作为“未完成阶段”重复设计，它们是后续实现的依赖和回归范围。仍需补执行卡的阶段为：

| 阶段 | 当前缺口 | 本节执行卡 |
| --- | --- | --- |
| Phase 4 | Resource ACL 已有 create/page/disable/dry-run，但缺 import commit、统一 audit 写入、DB 约束收口和权限常量化。 | 19.2 |
| Phase 5 | Connector binding/disable 代码表面已出现，仍需把 connector 与 sandbox 审计、风险启用门禁、领域单测和广义回归收成一个可验收闭环。 | 19.3 |
| Phase 7 | Local Agent-as-Tool、handoff repository、context handoff policy、query/cancel API 仍未形成主线。 | 19.4 |
| Phase 8B | Agent eval summary 还不能作为 publish/canary gate 的硬证据。 | 19.5 |
| Phase 8C | Quota、cost usage、SRE health 还没有统一最小闭环。 | 19.6 |
| Phase 8D | Canary rollout 与 enterprise pilot readiness 还没有形成准入和回滚闭环。 | 19.7 |

共同约束：

1. 不新增大一统 `AgentService`。继续按 Definition、Run、Tool、Policy、Approval、Connector、Context、Eval、Quota、Rollout 拆小 port。
2. Kernel 新增代码只能依赖 JDK、domain、ports 和同层 application service；不能引入 Spring、JDBC、Web、HTTP client。
3. 所有状态、失败原因、检查项、事件类型、风险等级必须使用 enum 或具名常量。现有硬编码 role、默认页大小、深度限制在触达文件时收敛为常量类。
4. 任何 import、cost、audit、rollout、eval summary 都按 append-only 或 snapshot 语义设计；修正通过新记录表达，不覆盖历史事实。
5. 每张执行卡先写 RED 测试并确认失败，再写最小 GREEN 代码；已有代码如果已经部分通过，也要先补缺失断言。
6. 不引入工作流引擎、远程 A2A、真实容器平台、复杂 JSON 类型、动态策略语言或远程 Agent mesh。

### 19.1 最新执行顺序

| 顺序 | 执行卡 | 先做理由 | 完成信号 |
| --- | --- | --- | --- |
| 1 | Phase 5 Connector/Sandbox Security Closure | 外部系统入口已经接近闭环，先完成生产安全底线和回归证据。 | connector/sandbox focused tests 和 audit redaction tests 通过。 |
| 2 | Phase 4 Resource ACL Import Commit + Audit Closure | ACL 是 Context 与 Agent-as-Tool 的权限地基，先把数据面证据收稳。 | import commit、disable、dry-run、access audit 均可追责。 |
| 3 | Phase 7 Local Agent-as-Tool | 依赖 Agent run、Tool Gateway、Context ACL、Audit 的事实链，前两项稳定后再接。 | parent run 能通过 ToolGateway 触发 child run，handoff 可查可取消。 |
| 4 | Phase 8B Agent Eval Gate | 依赖已发布 Agent、工具风险和生产门禁。 | high-risk Agent 无 eval 不能通过 gate。 |
| 5 | Phase 8C Quota/Cost/SRE | 需要真实 run/tool/eval 事实后聚合才有意义。 | quota 决策、cost 聚合、health 聚合 API 可用。 |
| 6 | Phase 8D Canary/Pilot Gate | 依赖 eval、quota、audit、rollback 证据。 | canary promote 前强制 gate，pilot readiness 报告完整。 |

### 19.2 Phase 4 执行卡：Resource ACL Import Commit、Audit 与 DB Hardening

#### 19.2.1 目标

把 Phase 4 从“能管理 ACL”推进到“ACL 变更和上下文访问可作为企业证据”。本卡只收口 Resource ACL，不重写 ContextPack，不扩展权限表达式。

完成后必须满足：

1. 管理员可先 dry-run，再 commit import；commit 只落 dry-run 判定为 `VALID` 的规则。
2. commit 遇到 invalid、conflict、duplicate 时默认 fail closed，不产生部分成功，除非命令显式选择 `VALID_ONLY`。
3. create、import commit、disable 都写 `RESOURCE_ACL_CHANGED` audit event。
4. `AuditedResourceAccessPolicyPort` 对 ContextPack/Tool resource access 写 `CONTEXT_ACCESSED` audit event，payload 只包含 resource ref、effect、reason、decisionId。
5. ACL 表状态、scope、effect、action、subject type 至少有适配器级 enum 映射校验；PostgreSQL schema 增加 check constraint 和 natural-key index。
6. `KernelResourceAclManagementService` 不再直接持有 `"admin"` 魔法字符串，改为具名常量。

#### 19.2.2 文件边界

| 层 | 文件 | 操作 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/context/ResourceAclImportMode.java` | 新增 enum：`FAIL_ON_INVALID`、`VALID_ONLY`。 |
| Domain | `kernel/domain/agent/context/ResourceAclImportResult.java` | 新增 import commit 结果，包含 createdCount、skippedCount、invalidCount、conflictCount、auditEventId。 |
| Domain | `kernel/domain/agent/context/ResourceAclAuthorizationRoles.java` | 新增具名常量 `ADMIN_ROLE`，替代 service 内硬编码 role。 |
| Inbound | `ports/inbound/agent/ResourceAclImportCommand.java` | 新增 command，字段为 dry-run items + mode。 |
| Inbound | `ResourceAclManagementInboundPort.java` | 增加 `importRules(ResourceAclImportCommand command)`。 |
| Application | `KernelResourceAclManagementService.java` | 注入 audit ledger，复用 dry-run 结果，新增 import commit，create/disable/import 写审计。 |
| Application | `AuditedResourceAccessPolicyPort.java` | 确认或补齐 access decision audit，不复制 ACL 判断。 |
| Audit | `AuditEventType.java` | 复用现有 `RESOURCE_ACL_CHANGED`、`CONTEXT_ACCESSED`，不新增同义事件。 |
| JDBC | `JdbcResourceAclRepositoryAdapter.java` | 增加批量 save 或循环 save 的稳定事务边界；保持 port 语义简单。 |
| SQL | `agent-registry-run-store-postgresql.sql` | 增加 ACL enum check constraint 和 natural-key index。 |
| Web | `SeahorseResourceAclController.java` | 新增 `POST /api/resource-acl-rules:import`，保留已有 `:dry-run-import` 风格。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration.java` | 给 ACL service 装配 audit ledger，保留旧构造兼容测试。 |

#### 19.2.3 领域与端口设计

`ResourceAclImportMode`：

```java
public enum ResourceAclImportMode {
    FAIL_ON_INVALID,
    VALID_ONLY
}
```

`ResourceAclImportCommand`：

```java
public record ResourceAclImportCommand(List<ResourceAclImportItem> items,
                                       ResourceAclImportMode mode) {
}
```

`ResourceAclImportResult`：

```java
public record ResourceAclImportResult(int createdCount,
                                      int skippedCount,
                                      int invalidCount,
                                      int conflictCount,
                                      String auditEventId,
                                      ResourceAclImportDryRunReport dryRunReport) {
}
```

Import 规则：

1. `FAIL_ON_INVALID` 是默认值。只要 dry-run report 里存在 `INVALID`、`CONFLICT`、`DUPLICATE_IN_BATCH`、`DUPLICATE_EXISTING`、`UNSUPPORTED_SCOPE`，commit 抛出 `IllegalStateException`，不保存任何规则。
2. `VALID_ONLY` 只保存 `VALID` item，其他 item 进入 skippedCount。
3. 每条新规则仍由 `ResourceAclRule` 维护不变量，service 不手写字段拼接。
4. audit payload 只包含 import summary、ruleIds、reason counts，不包含完整导入原文。

#### 19.2.4 数据库与 API

API：

| Method | Path | Request | Response |
| --- | --- | --- | --- |
| `POST` | `/api/resource-acl-rules:import` | `{ "mode": "FAIL_ON_INVALID", "items": [...] }` | `ResourceAclImportResult` |
| `POST` | `/api/resource-acl-rules:dry-run-import` | 维持已有合同 | `ResourceAclImportDryRunReport` |
| `POST` | `/api/resource-acl-rules/{ruleId}/disable` | 空 body | disabled rule |

PostgreSQL schema 追加：

```sql
CREATE INDEX idx_sa_resource_acl_rule_natural_key
  ON sa_resource_acl_rule (
    tenant_id,
    scope,
    resource_type,
    resource_id,
    subject_type,
    subject_id,
    action,
    status
  );

ALTER TABLE sa_resource_acl_rule
  ADD CONSTRAINT chk_sa_resource_acl_rule_status
  CHECK (status IN ('ENABLED', 'DISABLED'));

ALTER TABLE sa_resource_acl_rule
  ADD CONSTRAINT chk_sa_resource_acl_rule_scope
  CHECK (scope IN ('EXACT_RESOURCE'));
```

如果现有 schema 已含同名对象，实施时使用项目现有迁移约定处理，不在 kernel 引入数据库方言判断。

#### 19.2.5 TDD 步骤

1. RED：在 `KernelResourceAclManagementServiceTests` 增加 `importRulesFailsClosedWhenDryRunHasConflict`，断言 repository 没有保存。
2. RED：增加 `importRulesValidOnlySkipsInvalidItemsAndWritesAudit`，断言只保存 valid item，audit event type 为 `RESOURCE_ACL_CHANGED`。
3. RED：增加 `createAndDisableWriteResourceAclChangedAudit`，断言 create/disable 均有审计摘要。
4. RED：增加 `AuditedResourceAccessPolicyPortTests#writesContextAccessedEventWithoutRawContent`。
5. GREEN：新增 domain/command，扩展 inbound port 和 service。
6. RED：在 `JdbcResourceAclRepositoryAdapterTests` 增加非法 enum row 映射失败和 natural key 查询排序测试。
7. GREEN：更新 JDBC adapter/schema。
8. RED：在 `SeahorseResourceAclControllerTests` 增加 `/api/resource-acl-rules:import` 合同测试。
9. GREEN：更新 Web DTO 和 starter wiring。
10. GREEN 后执行 focused regression。

#### 19.2.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=KernelResourceAclManagementServiceTests,AuditedResourceAccessPolicyPortTests,AclBackedResourceAccessPolicyPortTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcResourceAclRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseResourceAclControllerTests,SeahorseAccessDecisionControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 19.2.7 不做事项与回滚

不新增 wildcard ACL、策略语言、字段级表达式或 ContextPack schema 迁移。回滚时只移除 import commit API 和 audit wiring，保留已有 create/page/disable/dry-run 能力。已写入的 ACL 规则不物理删除，通过 disable 表达撤销。

### 19.3 Phase 5 执行卡：Connector/Sandbox Security Closure

#### 19.3.1 目标

把已有 MCP OAuth、OpenAPI connector、credential binding、sandbox foundation 收成可验收的安全运营闭环。本卡不扩大 OpenAPI parser 能力，不新增 secret 存储方案，不实现真实容器沙箱。

完成后必须满足：

1. 需要凭据的 connector operation 没有 active binding 时不能 enable。
2. 高风险 operation enable 必须携带 `approvalPolicyId` 或 `operatorConfirmedRisk=true`，并写 `CONNECTOR_OPERATION_ENABLED` audit。
3. disable operation 幂等，禁用后 ToolCatalog 中对应 tool 不可被启用调用。
4. binding 替换会 rotate 旧 active binding，响应和 audit 均不含 token、secret、bearer value。
5. sandbox session created、execution terminal 都写 audit，artifact payload 只写数量和 ref 摘要。
6. Phase 5 focused regression 覆盖 kernel、JDBC、Web、starter、MCP OAuth adapter。

#### 19.3.2 文件边界

| 层 | 文件 | 操作 |
| --- | --- | --- |
| Domain | `ConnectorCredentialBinding.java` | 补齐独立领域单测；确认 secret material 不进入对象。 |
| Domain | `ConnectorOperation.java` | 确认 `requiresCredentialBinding()`、`disable()`、高风险判断语义只在领域对象或小私有方法中出现一次。 |
| Inbound | `ConnectorOperationEnableCommand.java` | 保留兼容构造，新增字段用具名访问；高风险空 body 不通过。 |
| Outbound | `ConnectorCredentialBindingRepositoryPort.java` | 如缺 rotate 方法，新增 `rotateActive(...)`，避免 service 扫描后手动保存多处规则。 |
| Application | `KernelOpenApiConnectorImportService.java` | 收敛 enable/bind/disable/audit 顺序，补失败语义。 |
| Application | `KernelSandboxRuntimeService.java` | session/execution terminal audit，payload redaction。 |
| JDBC | `JdbcConnectorCredentialBindingRepositoryAdapter.java` | active binding 唯一语义、rotate、enum 映射测试。 |
| JDBC | `JdbcConnectorRepositoryAdapter.java` | `auth_type`、operation status、toolId、risk/action 映射回归。 |
| SQL | `agent-registry-run-store-postgresql.sql` | connector operation、binding、sandbox 表约束和索引。 |
| Web | `SeahorseOpenApiConnectorController.java`、`SeahorseSandboxController.java` | 合同测试覆盖请求/响应不含 secret。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration.java`、`SeahorseAgentRegistryRepositoryAutoConfiguration.java` | binding repo、audit ledger、sandbox repo wiring 回归。 |

#### 19.3.3 关键规则

Connector enable 顺序：

1. 读取 connector operation，若不存在则失败。
2. 若 operation 已 enabled，返回当前状态并保持幂等，不重复写 ToolCatalog。
3. 若 `authType != NONE`，查询 active binding；没有 binding 直接失败。
4. 若 `riskLevel` 为 `HIGH` 或 `CRITICAL`，要求 approval policy 或 operator confirmation。
5. 保存 operation enabled 状态。
6. upsert ToolCatalog entry。
7. 写 `CONNECTOR_OPERATION_ENABLED` audit。

Credential binding 顺序：

1. 校验 `credentialRef` 非空且只作为 ref 处理。
2. 同一 tenant/connector/operation/authType 的旧 active binding 标记为 `ROTATED`。
3. 保存新 `ACTIVE` binding。
4. 写 `CONNECTOR_CREDENTIAL_BOUND` audit，payload 中只允许 credentialRef 的引用或 hash，不允许 secret value。

Sandbox audit 顺序：

1. create session 成功后写 `SANDBOX_SESSION_CREATED`。
2. execution 进入 `SUCCEEDED`、`FAILED`、`DENIED` 等终态后写 `SANDBOX_EXECUTION_FINISHED`。
3. audit 写失败遵守 `AuditWriteFailurePolicy`，不能吞掉 sandbox runtime 的业务失败。

#### 19.3.4 API 合同

| Method | Path | 规则 |
| --- | --- | --- |
| `PUT` | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | request 只接受 `authType`、`credentialRef`、`boundBy`；response 不返回 secret。 |
| `GET` | `/api/connectors/{connectorId}/operations/{operationId}/credential-binding` | 只返回 active binding 摘要。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/enable` | body 可为空；高风险必须带 `approvalPolicyId` 或 `operatorConfirmedRisk`。 |
| `POST` | `/api/connectors/{connectorId}/operations/{operationId}/disable` | 幂等。 |
| `POST` | `/api/sandbox/sessions` | 创建 session 后可查审计。 |
| `POST` | `/api/sandbox/sessions/{sessionId}/execute` | terminal 后可查审计。 |

#### 19.3.5 TDD 步骤

1. RED：新增 `ConnectorCredentialBindingTests`，覆盖 constructor validation、rotate idempotency、secret-like material rejected by shape。
2. RED：扩展 `KernelOpenApiConnectorImportServiceTests`，覆盖 authenticated operation without binding cannot enable。
3. RED：覆盖 high-risk enable without approval policy/confirmation fails。
4. RED：覆盖 binding replacement rotates previous active binding。
5. RED：覆盖 disable operation idempotent and disables catalog entry。
6. GREEN：最小修改 domain、repository port、service。
7. RED：扩展 `KernelSandboxRuntimeServiceTests`，覆盖 session/execution audit redaction。
8. GREEN：接入 sandbox audit。
9. RED：扩展 JDBC tests，覆盖 binding rotate、operation `auth_type`、非法 enum 映射失败。
10. GREEN：更新 JDBC/schema/starter。
11. RED：扩展 Web tests，覆盖 response 不含 secret/token/bearer。
12. GREEN：更新 controller DTO。
13. 运行 focused regression。

#### 19.3.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=ConnectorCredentialBindingTests,KernelOpenApiConnectorImportServiceTests,KernelSandboxRuntimeServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-mcp-http -am '-Dtest=McpHttpOAuthCredentialTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcConnectorRepositoryAdapterTests,JdbcConnectorCredentialBindingRepositoryAdapterTests,JdbcSandboxRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentControllerTests,SeahorseOpenApiConnectorControllerTests,SeahorseSandboxControllerTests,SeahorseAuditEventControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentCredentialAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests,SeahorseAgentSandboxAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 19.3.7 不做事项与回滚

不扩展 OpenAPI schema coverage，不新增远程 secret vault，不在主 JVM 执行任意代码。回滚时禁用 connector operation enable API 和 sandbox runtime auto-configuration，保留 connector/binding/sandbox 历史查询证据。

### 19.4 Phase 7 执行卡：Governed Local Agent-as-Tool 与 Handoff

#### 19.4.1 目标

实现本地 Agent-as-Tool 最小闭环：一个已发布 Agent 可以作为另一个 Agent 的工具被调用，但必须经过 Tool Gateway、Policy、Context ACL、Run Store 和 Audit。Phase 7 本卡不做远程 A2A、不做 Agent Card、不做 Agent Mesh 控制面。

完成后必须满足：

1. target Agent 通过 `ToolProvider.LOCAL_AGENT` 或同等 enum 进入 ToolCatalog。
2. parent run 调用 target Agent 时创建 `AgentHandoff` 和 child run。
3. handoff 只能由 ToolGateway 路径触发，不提供直接 create API。
4. context handoff 默认 summary-only，`SECRET` 丢弃，`CONFIDENTIAL` 对 target Agent 重新 ACL。
5. handoff 可按 parent run 查询，可幂等 cancel 未完成 handoff。
6. handoff created/finished 写 audit，不保存 raw context、raw message history、secret、credential。

#### 19.4.2 文件边界

| 层 | 文件 | 操作 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/handoff/AgentHandoff.java` | 新增 handoff 事实对象。 |
| Domain enum | `AgentHandoffStatus.java` | `CREATED`、`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELLED`。 |
| Domain enum | `AgentHandoffFailureCode.java` | `TARGET_DISABLED`、`TARGET_UNPUBLISHED`、`CROSS_TENANT_DENIED`、`DEPTH_EXCEEDED`、`CONTEXT_DENIED`、`CHILD_RUN_FAILED`、`CANCELLED_BY_OPERATOR`。 |
| Domain | `AgentHandoffContextPolicy.java` | 控制 summary length、depth、allowed sensitivity。 |
| Constants | `AgentHandoffLimits.java` | `DEFAULT_MAX_DEPTH`、`DEFAULT_MAX_SUMMARY_CHARS`。 |
| Inbound | `AgentHandoffQueryInboundPort.java` | `detail`、`listByParentRun`、`cancel`。 |
| Outbound | `AgentHandoffRepositoryPort.java` | `save`、`findById`、`listByParentRun`、`markRunning`、`markTerminal`。 |
| Outbound | `MeshPolicyPort.java` | 本地 source->target 授权，不实现远程 mesh。 |
| Application | `KernelAgentHandoffService.java` | 编排 handoff、child run、terminal/cancel。 |
| Tool adapter | `LocalAgentAsToolPort.java` | 实现 `ToolPort`，只桥接 ToolGateway 到 handoff service。 |
| JDBC | `JdbcAgentHandoffRepositoryAdapter.java` | 持久化 handoff。 |
| Web | `SeahorseAgentHandoffController.java` | 只提供 query/cancel API。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration.java` | 装配 handoff service、local-agent tool adapter。 |

#### 19.4.3 调用流

```text
Parent Agent model turn
  -> ToolGatewayPort.invoke(toolId = agent:<targetAgentId>)
  -> ToolPolicyPort.decide(...)
  -> LocalAgentAsToolPort.invoke(...)
  -> MeshPolicyPort.decide(sourceAgentId, targetAgentId, tenantId, depth)
  -> AgentHandoffContextPolicy.filter(parent context)
  -> AgentHandoffRepositoryPort.save(CREATED)
  -> AgentRunInboundPort.startRun(child input)
  -> AgentHandoffRepositoryPort.markRunning(childRunId)
  -> child run terminal signal
  -> AgentHandoffRepositoryPort.markTerminal(SUCCEEDED or FAILED)
  -> AuditEventType.AGENT_HANDOFF_FINISHED
```

Context filter 规则：

1. user raw message history 不跨 Agent 传递，只传 task summary。
2. tool result raw payload 不跨 Agent 传递，只传 redacted summary。
3. `ContextSensitivity.SECRET` 永远丢弃。
4. `ContextSensitivity.CONFIDENTIAL` 必须用 target Agent subject 调用 `ResourceAccessPolicyPort`；非 `ALLOW` 则丢弃并记录 reason。
5. `maxSummaryChars`、`maxDepth` 使用 `AgentHandoffLimits`。

#### 19.4.4 数据库与 API

```sql
CREATE TABLE sa_agent_handoff (
  handoff_id VARCHAR(64) PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  parent_run_id VARCHAR(64) NOT NULL,
  child_run_id VARCHAR(64),
  source_agent_id VARCHAR(64) NOT NULL,
  target_agent_id VARCHAR(64) NOT NULL,
  tool_invocation_id VARCHAR(64) NOT NULL,
  status VARCHAR(32) NOT NULL,
  failure_code VARCHAR(64),
  input_summary VARCHAR(1000) NOT NULL,
  context_policy_json TEXT NOT NULL,
  created_at TIMESTAMP NOT NULL,
  finished_at TIMESTAMP
);
```

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `GET` | `/api/agent-runs/{runId}/handoffs` | parent run 下 handoff 列表。 |
| `GET` | `/api/agent-handoffs/{handoffId}` | handoff 详情。 |
| `POST` | `/api/agent-handoffs/{handoffId}/cancel` | 幂等取消 `CREATED` 或 `RUNNING` handoff。 |

明确不新增 `POST /api/agent-handoffs`。

#### 19.4.5 TDD 步骤

1. RED：`AgentHandoffTests` 覆盖 terminal status 不可回退、cancel 幂等、failureCode 非空规则。
2. RED：`AgentHandoffContextPolicyTests` 覆盖 secret 丢弃、confidential 重新 ACL、summary clamp、depth limit。
3. RED：`DefaultMeshPolicyPortTests` 覆盖 target disabled、unpublished、cross-tenant、depth exceeded。
4. RED：`LocalAgentAsToolPortTests` 验证只能通过 ToolGateway request 执行，且不直接返回 raw child context。
5. RED：`KernelAgentHandoffServiceTests` 覆盖 create、markRunning、markTerminal、cancel、audit。
6. GREEN：实现 domain、ports、service、tool adapter。
7. RED：`JdbcAgentHandoffRepositoryAdapterTests` 覆盖 parent list 稳定排序、terminal update 幂等。
8. GREEN：实现 JDBC/schema/starter。
9. RED：`SeahorseAgentHandoffControllerTests` 覆盖 query/cancel，并确认 create path 404。
10. GREEN：实现 Web controller。
11. 运行 focused regression。

#### 19.4.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentHandoffTests,AgentHandoffContextPolicyTests,DefaultMeshPolicyPortTests,LocalAgentAsToolPortTests,KernelAgentHandoffServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentHandoffRepositoryAdapterTests,JdbcAuditEventRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentHandoffControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 19.4.7 不做事项与回滚

不做 remote A2A、Agent Card、remote health、mesh routing、debate、supervisor DAG。回滚时关闭 `LOCAL_AGENT` tool provider registration；handoff 历史和 child run 历史保留查询。

### 19.5 Phase 8B 执行卡：Agent Eval Summary Gate

#### 19.5.1 目标

把已有 retrieval/memory eval 或人工导入结果转为 Agent publish/canary gate 可读取的最小 snapshot。本卡不运行重型 eval job，不建设完整 eval 平台 UI。

完成后必须满足：

1. 每个 Agent version 可保存多类型 eval summary snapshot。
2. Production Gate 能读取 latest eval summary 并输出 `PASS`、`WARN`、`FAIL`。
3. 高风险 Agent 或绑定写工具的 Agent 没有 eval summary 时 fail closed。
4. 低风险 knowledge assistant 没有 eval summary 时 warn，不 silent pass。
5. eval summary stale 判定使用具名常量。

#### 19.5.2 文件边界

| 层 | 文件 | 操作 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/eval/AgentEvalSummary.java` | 新增 snapshot 对象。 |
| Enum | `AgentEvalType.java` | `RAG`、`MEMORY`、`TRAJECTORY`、`SAFETY`、`HITL`、`COST`。 |
| Enum | `AgentEvalStatus.java` | `PASS`、`WARN`、`FAIL`、`STALE`。 |
| Constants | `AgentEvalDefaults.java` | `MAX_SUMMARY_AGE`、默认 threshold。 |
| Outbound | `AgentEvalSummaryRepositoryPort.java` | `save`、`findLatest`、`listByVersion`。 |
| Outbound | `AgentEvalStatusPort.java` | Production Gate 只依赖这个小 port。 |
| Inbound | `AgentEvalQueryInboundPort.java` | 保存 summary、latest、history。 |
| Application | `KernelAgentEvalQueryService.java` | snapshot 保存和查询。 |
| Application | `KernelProductionGateService.java` | 聚合 eval status，不拥有 eval 事实。 |
| JDBC | `JdbcAgentEvalSummaryRepositoryAdapter.java` | latest 查询。 |
| Web | `SeahorseAgentEvalController.java` | summary save/latest/history API。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration.java` | eval service 和 gate port wiring。 |

#### 19.5.3 Gate 规则

1. `AgentEvalStatus.FAIL` 直接阻断 publish/canary。
2. `STALE` 对高风险 Agent 视为 `FAIL`，对低风险 Agent 视为 `WARN`。
3. 高风险 Agent、包含 `WRITE`、`DELETE`、`EXTERNAL_SEND` tool 的 Agent 缺 eval 视为 `FAIL`。
4. 低风险只读 Agent 缺 eval 视为 `WARN`。
5. Production Gate report item 使用 `ProductionGateCheckCode.EVAL` 或新增具名 check code，不写字符串。

#### 19.5.4 API 与表

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

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries` | 保存 summary snapshot。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries/latest` | 按 evalType 查询 latest。 |
| `GET` | `/api/agents/{agentId}/versions/{versionId}/eval-summaries` | 查询历史。 |

#### 19.5.5 TDD 步骤

1. RED：`AgentEvalSummaryTests` 覆盖 score 非负、threshold、status/stale 判定。
2. RED：`KernelAgentEvalQueryServiceTests` 覆盖 save/latest/history。
3. RED：`KernelProductionGateServiceTests` 覆盖 high-risk no eval fail、low-risk no eval warn、fail blocks gate。
4. GREEN：实现 domain、port、service 和 gate adapter。
5. RED：`JdbcAgentEvalSummaryRepositoryAdapterTests` 覆盖 latest 按 tenant/agent/version/type 隔离。
6. GREEN：实现 JDBC/schema/starter。
7. RED：`SeahorseAgentEvalControllerTests` 覆盖 save/latest/history。
8. GREEN：实现 Web。

#### 19.5.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentEvalSummaryTests,KernelAgentEvalQueryServiceTests,KernelProductionGateServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentEvalSummaryRepositoryAdapterTests,JdbcProductionGateRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentEvalControllerTests,SeahorseProductionGateControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 19.5.7 不做事项与回滚

不新增 eval runner、dataset editor、红队执行器。回滚时移除 eval summary gate adapter，Production Gate 对 eval 缺失返回明确 `WARN` 或 `FAIL`，不能伪造 `PASS`。

### 19.6 Phase 8C 执行卡：Quota、Cost Usage 与 SRE Health

#### 19.6.1 目标

建立最小成本和健康运营证据：quota policy 可配置，run/model/tool 使用量 append-only，SRE health 聚合可查询。本卡不做账单结算，不做复杂限流算法，不做前端运维大屏。

完成后必须满足：

1. quota policy 支持 tenant、agent、user、tool、model、run scope。
2. quota decision 对高风险无 policy 不能 silent allow。
3. cost usage 只追加记录，支持按 tenant/agent/run 聚合 token、cost、call count。
4. SRE health 聚合多个 contributor，`RED` 优先于 `WARN`，`WARN` 优先于 `GREEN`。
5. contributor 异常时整体至少 `WARN`，不能假绿。

#### 19.6.2 文件边界

| 层 | 文件 | 操作 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/quota/QuotaPolicy.java` | scope、window、limit、effect。 |
| Enum | `QuotaScopeType.java` | `TENANT`、`AGENT`、`USER`、`TOOL`、`MODEL`、`RUN`。 |
| Enum | `QuotaDecisionEffect.java` | `ALLOW`、`WARN`、`DENY`、`REQUIRE_APPROVAL`。 |
| Domain | `kernel/domain/agent/cost/CostUsageRecord.java` | append-only usage。 |
| Enum | `CostUsageSource.java` | `MODEL_CALL`、`TOOL_CALL`、`SANDBOX_EXECUTION`、`REMOTE_AGENT_CALL`。 |
| Domain | `kernel/domain/agent/sre/SreHealthSnapshot.java` | 聚合结果。 |
| Enum | `SreHealthStatus.java` | `GREEN`、`WARN`、`RED`。 |
| Outbound | `QuotaPolicyRepositoryPort.java`、`QuotaDecisionPort.java` | 管理和决策隔离。 |
| Outbound | `CostUsageRepositoryPort.java` | append、aggregate。 |
| Outbound | `SreHealthContributorPort.java` | 小 contributor 接口。 |
| Inbound | `QuotaManagementInboundPort.java`、`CostUsageQueryInboundPort.java`、`SreHealthQueryInboundPort.java` | Web 管理/查询入口。 |
| Application | `KernelQuotaDecisionService.java`、`KernelCostUsageQueryService.java`、`KernelSreHealthQueryService.java` | 编排。 |
| JDBC | `JdbcQuotaPolicyRepositoryAdapter.java`、`JdbcCostUsageRepositoryAdapter.java` | 持久化。 |
| Web | `SeahorseQuotaController.java`、`SeahorseCostUsageController.java`、`SeahorseSreHealthController.java` | API。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration.java` | wiring。 |

#### 19.6.3 默认决策

1. 无 policy 且 risk 为 `LOW`：`WARN`，允许继续，但 report 必须可见。
2. 无 policy 且 risk 为 `HIGH` 或 `CRITICAL`：`REQUIRE_APPROVAL`。
3. 超过 hard limit：`DENY`。
4. 超过 warn threshold：`WARN`。
5. run-level 超预算首版只返回 decision，不直接修改 run 状态；runtime 暂停接入留到后续小切片。

#### 19.6.4 API 与表

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

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `PUT` | `/api/quotas/{scopeType}/{scopeId}` | upsert quota policy。 |
| `GET` | `/api/quotas/{scopeType}/{scopeId}` | 查询 policy。 |
| `GET` | `/api/cost/usage` | 聚合 usage。 |
| `GET` | `/api/sre/health` | 聚合 health。 |

#### 19.6.5 TDD 步骤

1. RED：`QuotaPolicyTests` 覆盖 scope/window/limit/effect validation。
2. RED：`KernelQuotaDecisionServiceTests` 覆盖 no-policy defaults、高风险不 silent allow、hard limit deny。
3. RED：`CostUsageRecordTests` 覆盖 token/cost 非负、append-only id。
4. RED：`KernelCostUsageQueryServiceTests` 覆盖 tenant/agent/run 聚合。
5. RED：`KernelSreHealthQueryServiceTests` 覆盖 red/warn/green 优先级和 contributor exception。
6. GREEN：实现 domain、ports、services。
7. RED：JDBC tests 覆盖 upsert、append、aggregate。
8. GREEN：实现 JDBC/schema/starter。
9. RED：Web tests 覆盖 quota/cost/health API。
10. GREEN：实现 Web。

#### 19.6.6 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=QuotaPolicyTests,KernelQuotaDecisionServiceTests,CostUsageRecordTests,KernelCostUsageQueryServiceTests,KernelSreHealthQueryServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcQuotaPolicyRepositoryAdapterTests,JdbcCostUsageRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseQuotaControllerTests,SeahorseCostUsageControllerTests,SeahorseSreHealthControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 19.6.7 不做事项与回滚

不做真实扣费、复杂滑动窗口、分布式限流、前端图表。回滚时移除 quota decision 注入点，保留 cost usage 表作为只读运营证据。

### 19.7 Phase 8D 执行卡：Canary Rollout 与 Enterprise Pilot Readiness

#### 19.7.1 目标

建立 Agent version canary、promote、pause、rollback 以及企业试点准入报告。Canary 必须复用 Phase 6 的 version activation/rollback owner，不创建第二套发布事实。

完成后必须满足：

1. canary percent 有边界，使用 `AgentRolloutLimits`。
2. `PROMOTED`、`ROLLED_BACK`、`FAILED` 是 terminal status，不可回退。
3. promote 前必须读取 latest Production Gate report；没有 gate report 时 fail closed。
4. rollback 复用 `AgentVersionActivationRepositoryPort`。
5. pilot readiness report 覆盖 owner、published version、tool risk、resource ACL、eval、quota、audit、rollback、disable switch。
6. report 只保存 check result 和 evidence refs，不复制 prompt、eval case、tool raw output。

#### 19.7.2 文件边界

| 层 | 文件 | 操作 |
| --- | --- | --- |
| Domain | `kernel/domain/agent/rollout/AgentVersionRollout.java` | rollout 状态机。 |
| Enum | `AgentRolloutStatus.java` | `CREATED`、`RUNNING`、`PAUSED`、`PROMOTED`、`ROLLED_BACK`、`FAILED`。 |
| Enum | `AgentRolloutFailureCode.java` | `GATE_FAILED`、`GATE_MISSING`、`INVALID_PERCENT`、`ROLLBACK_FAILED`。 |
| Constants | `AgentRolloutLimits.java` | `MIN_PERCENT`、`MAX_PERCENT`、`DEFAULT_CANARY_PERCENT`。 |
| Domain | `EnterprisePilotReadinessReport.java` | 准入报告 snapshot。 |
| Enum | `EnterprisePilotReadinessCheckCode.java` | `OWNER`、`PUBLISHED_VERSION`、`TOOL_RISK`、`RESOURCE_ACL`、`EVAL`、`QUOTA`、`AUDIT`、`ROLLBACK`、`DISABLE_SWITCH`。 |
| Outbound | `AgentRolloutRepositoryPort.java` | rollout 持久化。 |
| Outbound | `EnterprisePilotReadinessRepositoryPort.java` | report 持久化。 |
| Inbound | `AgentRolloutInboundPort.java`、`EnterprisePilotReadinessInboundPort.java` | Web 入口。 |
| Application | `KernelAgentRolloutService.java` | canary/pause/promote/rollback。 |
| Application | `KernelEnterprisePilotReadinessService.java` | 聚合准入检查。 |
| JDBC | `JdbcAgentRolloutRepositoryAdapter.java`、`JdbcEnterprisePilotReadinessRepositoryAdapter.java` | 持久化。 |
| Web | `SeahorseAgentRolloutController.java`、`SeahorseEnterprisePilotReadinessController.java` | API。 |
| Starter | `SeahorseAgentKernelRegistryAutoConfiguration.java` | wiring。 |

#### 19.7.3 Rollout 规则

1. `createCanary` 初始状态为 `RUNNING` 或 `CREATED`，由实现选择一种并固化测试；不允许两个入口并存。
2. percent 必须在 `[MIN_PERCENT, MAX_PERCENT]`。
3. `pause` 对 terminal rollout 幂等返回当前状态。
4. `promote` 前查询 production gate；gate 非 `PASS` 时 rollout 标记 `FAILED`，failureCode 为 `GATE_FAILED`。
5. `rollback` 调用 Phase 6 activation rollback port；成功后 rollout 为 `ROLLED_BACK`。
6. rollout audit 可复用 `AGENT_PUBLISHED`/`AGENT_ROLLED_BACK` 相关事件或新增 rollout event；若新增，必须加入 `AuditEventType` enum。

#### 19.7.4 Pilot Readiness 检查

| CheckCode | PASS 条件 |
| --- | --- |
| `OWNER` | agent owner 和 fallback owner 均存在。 |
| `PUBLISHED_VERSION` | version 已发布且未禁用。 |
| `TOOL_RISK` | 高风险工具均有 approval policy 或明确禁用。 |
| `RESOURCE_ACL` | 绑定知识源/资源有 ACL 证据。 |
| `EVAL` | latest eval summary 非 `FAIL`，高风险不可 stale。 |
| `QUOTA` | tenant/agent quota policy 存在。 |
| `AUDIT` | audit repository 可查询关键事件。 |
| `ROLLBACK` | 有可回滚版本或 activation history。 |
| `DISABLE_SWITCH` | agent 和高风险 tool 均可禁用。 |

任一高风险必需项失败，报告总体 `FAIL`；低风险缺失项可 `WARN`，但不能 `PASS`。

#### 19.7.5 API 与表

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

API：

| Method | Path | 说明 |
| --- | --- | --- |
| `POST` | `/api/agents/{agentId}/versions/{versionId}/canary` | 创建 canary rollout。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/pause` | 暂停。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/promote` | gate pass 后推广。 |
| `POST` | `/api/agents/{agentId}/rollouts/{rolloutId}/rollback` | 回滚。 |
| `POST` | `/api/agents/{agentId}/pilot-readiness` | 生成准入报告。 |
| `GET` | `/api/agents/{agentId}/pilot-readiness/latest` | 查询 latest。 |

#### 19.7.6 TDD 步骤

1. RED：`AgentVersionRolloutTests` 覆盖 percent 边界、terminal status、pause/rollback 幂等。
2. RED：`KernelAgentRolloutServiceTests` 覆盖 promote gate missing fail、gate failed fail、rollback uses activation port。
3. RED：`EnterprisePilotReadinessReportTests` 覆盖 check code 完整性和总体状态聚合。
4. RED：`KernelEnterprisePilotReadinessServiceTests` 覆盖九个检查项。
5. GREEN：实现 domain、ports、services。
6. RED：JDBC tests 覆盖 rollout status update、latest report。
7. GREEN：实现 JDBC/schema/starter。
8. RED：Web tests 覆盖 canary/pause/promote/rollback/readiness API。
9. GREEN：实现 Web。

#### 19.7.7 验收命令

```powershell
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-kernel '-Dtest=AgentVersionRolloutTests,KernelAgentRolloutServiceTests,EnterprisePilotReadinessReportTests,KernelEnterprisePilotReadinessServiceTests' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-repository-jdbc -am '-Dtest=JdbcAgentRolloutRepositoryAdapterTests,JdbcEnterprisePilotReadinessRepositoryAdapterTests,JdbcAgentVersionActivationRepositoryAdapterTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-adapter-web -am '-Dtest=SeahorseAgentRolloutControllerTests,SeahorseEnterprisePilotReadinessControllerTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
mvn -nsu '-Dspotless.apply.skip=true' -pl seahorse-agent-spring-boot-starter -am '-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentAuditAutoConfigurationTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
git diff --check
```

#### 19.7.8 不做事项与回滚

不做流量路由器、不做真实百分比分流、不做前端发布向导。本卡只建立 rollout 事实和准入报告。回滚时停用 rollout API，保留 report 历史；普通 Phase 6 publish/rollback 不受影响。

### 19.8 Section 19 完成判定

第 19 节只是设计开发方案，不代表 AI Infra 已完成。后续实现完成至少需要：

1. 19.2 至 19.7 的 focused tests 全部通过。
2. 新增 API 均有 Web contract test。
3. 新增 repository 均有 JDBC adapter test。
4. starter auto-configuration 覆盖新增 port wiring。
5. audit redaction 覆盖 ACL、connector、sandbox、handoff、eval、quota、rollout、pilot readiness。
6. `git diff --check` 无错误。
7. `rg -n "org.springframework|javax.sql|java.sql|RestTemplate|WebClient" seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports` 不出现新增 kernel 违规依赖。

# Seahorse Agent 远期设计与未来展望 — 基座验证与剩余实施计划

> 验证日期：2026-06-20
> 基于代码库 main 分支全量搜索

---

## 第一部分：现有基座验证报告

### 验证方法

对远期设计 L1-L6 和未来展望 F1-F6 中声称的每个"现有基座"组件，在 Java 源码、SQL 初始化脚本、前端 TSX 文件三个维度进行精确搜索，给出文件路径和实现完整度。

### L1 Agent Factory — 全部存在 ✓

| 组件 | 状态 | 文件路径 | 完整度 |
|---|---|---|---|
| `AgentFactoryInboundPort` | ✓ 存在 | `seahorse-agent-kernel/.../ports/inbound/agent/AgentFactoryInboundPort.java` (42行) | 完整 |
| `KernelAgentFactoryService` | ✓ 存在 | `seahorse-agent-kernel/.../application/agent/factory/KernelAgentFactoryService.java` (393行) | 完整 |
| `SeahorseAgentFactoryController` | ✓ 存在 | `seahorse-agent-adapter-web/.../web/SeahorseAgentFactoryController.java` (169行) | 完整 |
| `sa_agent_template` 表 | ✓ 存在 | `resources/database/seahorse_init.sql` L1194 | 完整 |
| `sa_agent_publish_check` 表 | ✓ 存在 | `resources/database/seahorse_init.sql` L1259 | 完整 |
| 前端 AgentCreatePage | ✓ 存在 | `frontend/src/pages/admin/agents/AgentCreatePage.tsx` | 完整 |
| 前端 AgentEditorPage | ✓ 存在 | `frontend/src/pages/admin/agents/AgentEditorPage.tsx` | 完整 |
| 前端 AgentRolloutPage | ✓ 存在 | `frontend/src/pages/admin/agents/AgentRolloutPage.tsx` | 完整 |
| 单元测试 | ✓ 存在 | `KernelAgentFactoryServiceTests.java` | 完整 |

**结论：L1 声称基座 100% 真实存在。**

### L2 Sandbox Runtime — 全部存在 ✓

| 组件 | 状态 | 文件路径 | 完整度 |
|---|---|---|---|
| `SandboxRuntimeInboundPort` | ✓ 存在 | `seahorse-agent-kernel/.../ports/inbound/agent/SandboxRuntimeInboundPort.java` (35行) | 完整 |
| `KernelSandboxRuntimeService` | ✓ 存在 | `seahorse-agent-kernel/.../application/agent/sandbox/KernelSandboxRuntimeService.java` (390行) | 完整 |
| `DefaultSandboxPolicyPort` | ✓ 存在 | `seahorse-agent-kernel/.../application/agent/sandbox/DefaultSandboxPolicyPort.java` (74行) | 完整 |
| `SeahorseSandboxController` | ✓ 存在 | `seahorse-agent-adapter-web/.../web/SeahorseSandboxController.java` (164行) | 完整 |
| `sa_sandbox_session` 表 | ✓ 存在 | `seahorse_init.sql` L1462 | 完整 |
| `sa_sandbox_execution` 表 | ✓ 存在 | `seahorse_init.sql` L1477 | 完整 |
| `sa_sandbox_artifact` 表 | ✓ 存在 | `seahorse_init.sql` L1492 | 完整 |
| JDBC Adapter | ✓ 存在 | `JdbcSandboxRepositoryAdapter.java` (353行) | 完整 |
| 前端 SandboxPage | ✓ 存在 | `frontend/src/pages/admin/sandbox/SandboxPage.tsx` (190行) | 完整 |
| 单元测试 | ✓ 存在 | `KernelSandboxRuntimeServiceTests.java` + `DefaultSandboxPolicyPortTests.java` | 完整 |

**结论：L2 声称基座 100% 真实存在。**

### L3 Multi-Agent/A2A — 全部存在 ✓

| 组件 | 状态 | 文件路径 | 完整度 |
|---|---|---|---|
| `AgentHandoffInboundPort` | ✓ 存在 | `seahorse-agent-kernel/.../ports/inbound/agent/AgentHandoffInboundPort.java` (31行) | 完整 |
| `KernelAgentHandoffService` | ✓ 存在 | `seahorse-agent-kernel/.../application/agent/handoff/KernelAgentHandoffService.java` (220行) | 完整 |
| `LocalAgentAsToolPort` | ✓ 存在 | `seahorse-agent-kernel/.../application/agent/handoff/LocalAgentAsToolPort.java` (110行) | 完整 |
| `SeahorseAgentHandoffController` | ✓ 存在 | `seahorse-agent-adapter-web/.../web/SeahorseAgentHandoffController.java` (106行) | 完整 |
| `sa_agent_handoff` 表 | ✓ 存在 | `seahorse_init.sql` L1682 | 完整 |
| JDBC Adapter | ✓ 存在 | `JdbcAgentHandoffRepositoryAdapter.java` (196行) | 完整 |
| 单元测试 | ✓ 存在 | `KernelAgentHandoffServiceTests.java` + `LocalAgentAsToolPortTests.java` | 完整 |

**结论：L3 声称基座 100% 真实存在。**

### L4 Context Pack — 全部存在 ✓

| 组件 | 状态 | 文件路径 | 完整度 |
|---|---|---|---|
| `ContextPackBuilderInboundPort` | ✓ 存在 | `seahorse-agent-kernel/.../ports/inbound/agent/ContextPackBuilderInboundPort.java` (26行) | 完整 |
| `ContextPackQueryInboundPort` | ✓ 存在 | `seahorse-agent-kernel/.../ports/inbound/agent/ContextPackQueryInboundPort.java` (31行) | 完整 |
| `KernelContextPackBuilderService` | ✓ 存在 | `seahorse-agent-kernel/.../application/agent/context/KernelContextPackBuilderService.java` (160行) | 完整 |
| `KernelContextPackQueryService` | ✓ 存在 | `seahorse-agent-kernel/.../application/agent/context/KernelContextPackQueryService.java` (83行) | 完整 |
| `ContextReducer` | ✓ 存在 | `seahorse-agent-kernel/.../application/agent/context/ContextReducer.java` (179行) | 完整 |
| `sa_context_pack` 表 | ✓ 存在 | `seahorse_init.sql` L1376 | 完整 |
| `sa_context_item` 表 | ✓ 存在 | `seahorse_init.sql` L1393 | 完整 |
| JDBC Adapter | ✓ 存在 | `JdbcContextPackRepositoryAdapter.java` (237行) | 完整 |
| 前端 ContextPackPage | ✓ 存在 | `frontend/src/pages/admin/settings/ContextPackPage.tsx` (118行) | 完整 |
| 单元测试 | ✓ 存在 | `KernelContextPackBuilderServiceTests.java` + `KernelContextPackQueryServiceTests.java` | 完整 |

**结论：L4 声称基座 100% 真实存在。**

### L5 Enterprise Data Boundary — 全部存在 ✓

| 组件 | 状态 | 关键文件路径 | 完整度 |
|---|---|---|---|
| `TenantInterceptor` | ✓ 存在 | `seahorse-agent-adapter-web/.../web/TenantInterceptor.java` (79行) | 完整 |
| `TenantContext` | ✓ 存在 | `seahorse-agent-kernel/.../kernel/tenant/TenantContext.java` (125行) | 完整 |
| `ResourceAclManagementInboundPort` | ✓ 存在 | `KernelResourceAclManagementService.java` (411行) | 完整 |
| `QuotaManagementInboundPort` | ✓ 存在 | `KernelQuotaDecisionService.java` (181行) | 完整 |
| `CostUsageInboundPort` | ✓ 存在 | `KernelCostUsageQueryService.java` | 完整 |
| `AuditQueryInboundPort` | ✓ 存在 | `KernelAuditLedgerService.java` | 完整 |
| `BillingInboundPort` | ✓ 存在 | `KernelBillingService.java` (137行) | 完整 |
| `sa_resource_acl_rule` 表 | ✓ 存在 | `seahorse_init.sql` L1431 | 完整 |
| `sa_quota_policy` 表 | ✓ 存在 | `seahorse_init.sql` L1574 | 完整 |
| `sa_cost_usage_record` 表 | ✓ 存在 | `seahorse_init.sql` L1606 | 完整 |
| `sa_audit_event` 表 | ✓ 存在 | `seahorse_init.sql` L1507 | 完整 |
| Billing 表 (4张) | ✓ 存在 | `V5__billing_tables.sql` + `seahorse_init.sql` L2310-2373 | 完整 |
| JDBC Adapters | ✓ 存在 | `JdbcResourceAclRepositoryAdapter` (325行), `JdbcQuotaPolicyRepositoryAdapter` (190行), `JdbcCostUsageRepositoryAdapter` (141行), `JdbcAuditEventRepositoryAdapter` (179行) | 完整 |
| Web Controllers | ✓ 存在 | `SeahorseResourceAclController` (230行), `SeahorseQuotaController` (136行), `SeahorseCostUsageController` (96行), `SeahorseAuditEventController` (75行), `SeahorseBillingController` (120行) | 完整 |

**结论：L5 声称基座 100% 真实存在。**

### L6 Storage — 全部存在 ✓

| 组件 | 状态 | 文件路径 | 完整度 |
|---|---|---|---|
| `ObjectStoragePort` (接口) | ✓ 存在 | `seahorse-agent-kernel/.../ports/outbound/storage/ObjectStoragePort.java` (55行) | 完整 |
| `S3ObjectStorageAdapter` | ✓ 存在 | `seahorse-agent-adapter-storage-s3/.../S3ObjectStorageAdapter.java` (145行) | 完整 |
| `LocalObjectStorageAdapter` | ✓ 存在 | `seahorse-agent-adapter-storage-local/.../LocalObjectStorageAdapter.java` (122行) | 完整 |

> 注：文档中写 `StoragePort/StorageOutboundPort`，实际代码命名为 `ObjectStoragePort`，功能完全对应。

**结论：L6 声称基座 100% 真实存在（端口名微调）。**

### F1 RAG Trace / Metadata Review — 全部存在 ✓

| 组件 | 状态 | 文件路径 | 完整度 |
|---|---|---|---|
| `SeahorseRagTraceController` | ✓ 存在 | `seahorse-agent-adapter-web/.../SeahorseRagTraceController.java` (68行) | 完整 |
| `t_rag_trace_run` 表 | ✓ 存在 | `seahorse_init.sql` L289 | 完整 |
| `t_rag_trace_node` 表 | ✓ 存在 | `seahorse_init.sql` L311 | 完整 |
| MetadataGovernancePage | ✓ 存在 | `frontend/src/pages/admin/metadata-governance/MetadataGovernancePage.tsx` (358行) | 完整 |
| Review/Quarantine 组件 | ✓ 存在 | `MetadataReviewDetailDrawer.tsx` + JDBC metadata治理端口 | 完整 |

### F2 Memory Entity — 全部存在 ✓

| 组件 | 状态 | 文件路径 | 完整度 |
|---|---|---|---|
| `t_memory_entity_alias` 表 | ✓ 存在 | `seahorse_init.sql` L710 | 完整 |
| `t_memory_entity_relation` 表 | ✓ 存在 | `seahorse_init.sql` L732 | 完整 |
| `t_memory_correction_ledger` 表 | ✓ 存在 | `seahorse_init.sql` L804 | 完整 |

### F3 Gate Adapter — 部分存在 ⚠️

| 组件 | 状态 | 说明 |
|---|---|---|
| `GateResult` (统一类型) | ✗ 不存在 | 代码中无 `GateResult` 类 |
| `ProductionGateReport` | ✓ 存在 | `kernel/.../domain/agent/gate/ProductionGateReport.java` (71行) |
| `ProductionGateStatus/CheckItem/CheckCode` | ✓ 存在 | 同目录下 3 个领域对象 |
| `KernelProductionGateService` | ✓ 存在 | 388行，含测试 |
| `SeahorseProductionGateController` | ✓ 存在 | 65行 |

**结论：Agent 发布门禁已用 `ProductionGateReport` 实现，但统一跨对象类型的 `GateResult` 尚未泛化。约 75%。**

### F4 AI Model Config — 全部存在 ✓

| 组件 | 状态 | 文件路径 | 完整度 |
|---|---|---|---|
| `sa_ai_model_config` 表 | ✓ 存在 | `seahorse_init.sql` L2124 | 完整 |
| `AiModelConfigController` | ✓ 存在 | `seahorse-agent-adapter-web/.../AiModelConfigController.java` (166行) | 完整 |
| JDBC Adapter | ✓ 存在 | `JdbcAiModelConfigRepositoryAdapter.java` + 测试 | 完整 |

### F5 Marketplace — 全部存在 ✓

| 组件 | 状态 | 文件路径 | 完整度 |
|---|---|---|---|
| `sa_agent_publish_review` 表 | ✓ 存在 | `seahorse_init.sql` L2543 + `V9__agent_marketplace.sql` | 完整 |
| `sa_agent_subscription` 表 | ✓ 存在 | `seahorse_init.sql` L2560 | 完整 |
| `sa_agent_rating` 表 | ✓ 存在 | `seahorse_init.sql` L2575 | 完整 |
| `sa_agent_rating_summary` 表 | ✓ 存在 | `seahorse_init.sql` L2589 | 完整 |
| `sa_agent_popularity` 表 | ✓ 存在 | `seahorse_init.sql` L2602 | 完整 |
| `sa_revenue_share` 表 | ✓ 存在 | `seahorse_init.sql` L2661 | 完整 |
| JDBC Adapters | ✓ 存在 | `JdbcAgentPublishReviewRepositoryAdapter` (198行), `JdbcAgentSubscriptionRepositoryAdapter` (218行), `JdbcAgentRatingRepositoryAdapter` (205行) | 完整 |
| `KernelAgentMarketplaceService` | ✓ 存在 | 261行 | 完整 |
| `RevenueService` | ✓ 存在 | 130行 | 完整 |
| `SeahorseMarketplaceController` | ✓ 存在 | 含测试 | 完整 |
| 前端 MarketplacePage | ✓ 存在 | `frontend/src/pages/MarketplacePage.tsx` (421行) | 完整 |
| 前端 MarketplaceReviewPage | ✓ 存在 | `frontend/src/pages/admin/marketplace/MarketplaceReviewPage.tsx` (224行) | 完整 |

### F6 Notification/Webhook — 基本存在 ✓

| 组件 | 状态 | 文件路径 | 完整度 |
|---|---|---|---|
| `NotificationPort` | ✓ 存在 | `kernel/.../ports/outbound/notification/NotificationPort.java` (84行) | 完整 |
| `AlertNotifierPort` | ✓ 存在 | `kernel/.../ports/outbound/alert/AlertNotifierPort.java` (63行) | 完整 |
| `DingTalkAlertNotifierAdapter` | ✓ 存在 | 201行 (Webhook实现) | 完整 |
| `MybatisPlusNotificationAdapter` | ✓ 存在 | 132行 | 完整 |
| `SeahorseNotificationController` | ✓ 存在 | 118行 | 完整 |
| `sa_notification` 表 | ✓ 存在 | `seahorse_init.sql` L2739 + `V15__notification_center.sql` | 完整 |
| `sa_notification_template` 表 | ✓ 存在 | `seahorse_init.sql` L2756 | 完整 |
| `sa_notification_preference` 表 | ✓ 存在 | `seahorse_init.sql` L2769 | 完整 |
| 前端 Notification 页面 | ✗ 不存在 | 无独立 Notification 管理 TSX 页面 | 不存在 |

### 验证总结矩阵

| 层级 | 声称组件数 | 存在 | 部分存在 | 不存在 | 可信度 |
|---|---|---|---|---|---|
| L1 Agent Factory | 9 | 9 | 0 | 0 | **100%** |
| L2 Sandbox Runtime | 10 | 10 | 0 | 0 | **100%** |
| L3 Multi-Agent/A2A | 7 | 7 | 0 | 0 | **100%** |
| L4 Context Pack | 10 | 10 | 0 | 0 | **100%** |
| L5 Enterprise Boundary | 14 | 14 | 0 | 0 | **100%** |
| L6 Storage | 3 | 3 | 0 | 0 | **100%** |
| F1 RAG Trace | 5 | 5 | 0 | 0 | **100%** |
| F2 Memory Entity | 3 | 3 | 0 | 0 | **100%** |
| F3 Gate Adapter | 5 | 4 | 1 | 0 | **~80%** |
| F4 AI Model Config | 3 | 3 | 0 | 0 | **100%** |
| F5 Marketplace | 12 | 12 | 0 | 0 | **100%** |
| F6 Notification | 9 | 8 | 0 | 1 | **~90%** |

**整体可信度：约 97%** — 文档对代码库的描述高度准确。

---

## 第二部分：剩余实施计划

### 近期缺口修补（1-2 周）

#### N1. MCP OAuth2 安全增强

**基座状态**：MCP HTTP 适配器已有，但缺少 OAuth 2.1 支持

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P0 | `McpAuthStrategy` 接口 | 定义 NONE/STATIC_BEARER/OAUTH2/CLIENT_CREDENTIALS/USER_DELEGATED 策略 |
| P0 | `OAuth2McpAuthStrategy` 实现 | Client Credentials Flow + Token 缓存 |
| P0 | `McpTokenProvider` | Token 获取、刷新、撤销 |
| P0 | `McpCredentialVault` | 凭据加密存储，多租户隔离 |
| P1 | `McpScopeChallenge` | 解析 WWW-Authenticate scope challenge |
| P1 | `McpAuthAuditPort` | 认证失败、token 刷新、scope 不足事件审计 |
| P1 | Token 不进入 prompt/trace/log | 安全断言和测试 |

**新增表**：`sa_mcp_credential`、token cache 表或 Redis schema

**设计文档**：`docs/zh/content/架构设计/未实现功能详细设计/01-MCP-OAuth2-安全增强设计.md`

**预计工期**：6 周

#### N2. Sandbox Runtime 真实隔离执行

**基座状态**：端口/服务/策略/Controller/表全部存在，但无真实容器 runtime

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P0 | `ContainerSandboxRuntimeAdapter` | Docker/Podman runtime adapter |
| P0 | Runtime profile 配置 | python-small / node-small / browser-readonly / file-conversion |
| P0 | Execution history API 增强 | 补齐 session close 资源释放 |
| P1 | `ArtifactScannerPort` | 产物 MIME 检测、敏感内容扫描 |
| P1 | 前端 Artifact Browser | scan status、preview、download |
| P1 | UI 升级为 Sandbox Operations | Session 列表、Policy Preview、Execution Console、Audit Timeline |
| P2 | sandbox-backed tools | `sandbox_python`、`sandbox_browser`、`sandbox_file_convert` |
| P2 | Tool Gateway 集成 | sandbox-backed tool 走 policy/approval/quota |
| P3 | 生产加固 | egress proxy、gVisor profile、tenant quota、自动清理 |

**设计文档**：`docs/zh/content/架构设计/未实现功能详细设计/03-Sandbox-Runtime-设计.md`

**预计工期**：8 周（P0 = 3 周）

#### N3. OpenAPI Connector 产品化

**基座状态**：导入/解析/operation/凭据绑定/启停/Tool Catalog 已实现

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P0 | `OpenApiToolPortAdapter` | 真实 HTTP invocation，凭据注入，host allowlist，响应脱敏 |
| P1 | Version diff + operation review | spec diff 字段和 API |
| P1 | Dry-run API | dry-run history 和前端展示 |
| P1 | 前端导入向导增强 | 解析预览、风险复核、dry-run |
| P2 | Credential 运营化 | Secret picker、scope 校验、轮换提醒 |
| P3 | 连接器版本治理 | version diff 视图、批量禁用、回滚 |

**新增表**：`sa_connector_dry_run`、operation review/version diff 字段

**设计文档**：`docs/zh/content/架构设计/未实现功能详细设计/02-OpenAPI-Connector-设计.md`

**预计工期**：4 周（P0 = 1.5 周）

### 中期产品化（2-6 周）

#### N4. Agent Factory Studio

**基座状态**：Agent CRUD/发布校验/回滚/模板/前端页面全部存在

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P0 | AgentCreateWizard 五步向导 | 复用 `createAgentFromTemplate`，每步保存 draft |
| P0 | AgentEditorPage 结构化 | 基础字段结构化 + JSON 高级模式 |
| P0 | Publish gate summary 面板 | validate 后展示 blocking/warning/info |
| P1 | Test Run API + 前端 panel | 创建测试运行、timeline、metrics |
| P1 | Publish gate 联动 | 接入 tool/ACL/quota/eval/readiness |
| P1 | 发布弹窗增强 | blocking/warning/waiver + change summary + 高风险审批 |
| P2 | Version Diff 页面 | prompt/工具/Skill/预算/评测集版本差异 |
| P2 | 回滚前 diff 展示 | 强制显示 diff 再确认回滚 |
| P2 | Template Admin | 模板启停、推荐、复制、归档 + audit event |
| P3 | 运营化体验 | rollout/canary 控制、eval 趋势、成本 SRE、复制 Agent |

**设计文档**：`docs/zh/content/架构设计/未实现功能详细设计/04-Agent-Factory-UI-设计.md`

**预计工期**：6 周

#### N5. Multi-Agent/A2A 协作闭环

**基座状态**：handoff 服务/LocalAgentAsToolPort/Controller/表全部存在

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P0 | Child run terminal observer | 自动完成/失败 handoff 状态回写 |
| P0 | `AgentCollaborationPolicyPort` | source->target 授权矩阵 |
| P0 | JDBC Adapter | `sa_agent_collaboration_policy` 表 + adapter |
| P0 | 受控 create handoff API | `POST /api/agent-handoffs` |
| P0 | Agent Inspector handoff tree | 前端 parent/child run 关系可视化 |
| P1 | `AgentTeamDefinition` | Supervisor/Workflow Team DAG |
| P1 | Workflow DAG run | team run 启动与状态跟踪 |
| P1 | Teams Admin UI | `/admin/multi-agent` Teams tab |
| P2 | Remote Agent Registry | `sa_remote_agent` 表 + 注册/健康检查/启停 |
| P2 | A2A Client/Server | agent card endpoint + 远程 event 映射 |
| P3 | Mesh 控制面 | routing preview、circuit breaker、mesh health |

**新增表**：`sa_agent_collaboration_policy`、`sa_remote_agent`、`sa_agent_team`

**设计文档**：`docs/zh/content/架构设计/未实现功能详细设计/05-Multi-Agent-A2A-设计.md`

**预计工期**：10 周

#### N6. 记忆系统 Phase 5 — 衰减与质量治理

**基座状态**：四层记忆/混合召回/alias/GC/compaction 核心读路径已完成

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P0 | 仿生衰减算法 | `e^(-λt)` 时间衰减 + `ShortTermMemoryMaintenancePort` |
| P0 | 质量评估器 | 五维度评估（freshness/relevance/confidence/usage/consistency） |
| P1 | 冲突检测与修复 | `t_memory_conflict_log` 表 + 检测/修复服务 |
| P1 | 质量报表 | `t_memory_quality_snapshot` 表 + snapshot 服务 |
| P2 | MemoryVectorPort 闭环 | 接入 Milvus 实现语义记忆检索 |
| P2 | 多级摘要策略 | L2 会话级、L3 跨会话主题、L4 用户画像 |

**新增表**：`t_memory_conflict_log`、`t_memory_quality_snapshot`

**预计工期**：4 周

### 远期演进（3-6 个月）

#### N7. 企业数据边界联动产品化

**基座状态**：Tenant/ACL/Quota/Cost/Audit/Billing 全部存在

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P1 | 统一资源标识 | `resourceType/resourceId/action/subject` 所有模块复用 |
| P1 | 执行前决策联动 | Agent run/tool/context pack/sandbox 前评估 ACL+quota |
| P1 | 执行后记账 | cost usage + audit event + access decision log 统一写入 |
| P2 | 管理端联动 | 从任一 run 跳到权限/成本/审计/账单 |
| P2 | RLS 强化 | 所有资源表 tenant_id + 查询默认带 tenant |

#### N8. 存储生产化

**基座状态**：ObjectStoragePort + S3/Local adapter 存在

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P1 | 对象引用规范 | 业务表只存 object reference，不存本地路径 |
| P1 | 双写校验 | local/S3 双写，checksum 校验后切换读取 |
| P2 | 生命周期策略 | 临时产物 TTL、审计保留、用户删除、租户归档 |
| P2 | 迁移工具 | local -> S3 迁移 + 回切 local 限制说明 |
| P3 | E2E 验证 | 文档/artifact/sandbox 产物在 S3 模式端到端跑通 |

#### N9. 自适应知识运营 (F1 深化)

**基座状态**：RAG trace + metadata governance 已存在

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P1 | Knowledge Quality Issue 模型 | 复用 metadata review/quarantine + eval candidate |
| P1 | RAG trace 聚合 | 空召回/高延迟/差评/低置信答案聚合 |
| P2 | 修复建议引擎 | 重新入库/补 metadata/拆分文档/调整策略 |
| P2 | Agent 修复草案 | 接入 Agent run 生成修复草案 + 审核评测 |

#### N10. 可解释记忆网络 (F2 深化)

**基座状态**：entity_alias/relation/correction_ledger 表存在

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P1 | Memory lineage 查询端口 | 聚合来源和派生关系（不引入图数据库） |
| P2 | 前端记忆图谱 | "为什么你记得这个"解释入口 |
| P2 | 删除影响分析 | 展示将被影响的 profile fact/索引/召回 |
| P3 | 冲突视图 | 旧事实 superseded 而非静默覆盖 |

#### N11. 统一 GateResult (F3 深化)

**基座状态**：`ProductionGateReport` 已实现（Agent 级）

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P1 | 定义 `GateResult` | status/blockingIssues/warnings/metrics/evidenceRefs |
| P1 | RAG Strategy gate adapter | retrieval evaluation comparison |
| P2 | Model Config gate adapter | 成本/延迟/质量集达标 |
| P2 | Tool/Skill gate adapter | 安全扫描/权限/审计策略 |
| P2 | Ingestion Pipeline gate adapter | 测试文档入库完整性 |
| P3 | 灰度自动暂停 | 指标异常暂停 rollout + 建议回滚 |

#### N12. 多模型供应链治理 (F4 深化)

**基座状态**：`sa_ai_model_config` + `AiModelConfigController` 存在

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P1 | 模型配置扩展 | provider/capability/dimension/contextWindow/price/risk |
| P1 | 管理端统一 | 前端 `/admin/ai-config` 与后端口径统一 |
| P2 | Embedding 维度校验 | 阻止错误维度写入现有索引 |
| P2 | 模型质量基线 | Chat/Embedding/Rerank 分别建小型质量基线 |
| P3 | 路由策略 | 按任务/租户/预算/质量/延迟/合规选模型 |
| P3 | 模型版本回滚 | 配置变更保留版本，失败后快速回退 |

#### N13. 企业 Agent 市场运营化 (F5 深化)

**基座状态**：publish_review/subscription/rating/popularity/revenue_share 表和后端全部存在

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P1 | 发布包不可变引用 | Agent version + 评测报告不可变绑定 |
| P1 | 订阅授权 | 订阅后只授予使用权，不允许修改原始版本 |
| P2 | 市场安全检查 | 工具权限/Skill 扫描/模型成本/数据边界 |
| P2 | 收益报表 | 统计报表先行，自动结算后续 |
| P3 | 跨租户市场 | 内部共享 -> 多租户市场演进 |

#### N14. 人机协作控制面 (F6 深化)

**基座状态**：notification/webhook 后端存在，前端页面缺失

**剩余实施项**：

| 优先级 | 实施项 | 说明 |
|---|---|---|
| P0 | 前端 Notification 页面 | 通知列表/详情/已读标记/偏好设置 |
| P1 | 统一操作事件模型 | approval/audit/notification/run status 关联 |
| P1 | "待我处理"视图 | 按风险和 SLA 排序 |
| P2 | 人工接管 | checkpoint 写入 + Agent 恢复时看到人工输入 |
| P2 | Webhook 偏好 | 每个关键动作可配置 webhook 和通知偏好 |
| P3 | 产物验收 | artifact 人工验收/评论/退回/发布 |

### 未来阶段统一落地顺序

| 顺序 | 里程碑 | 完成标准 |
|---|---|---|
| 1 | 统一证据模型 | GateResult、AuditEvent、CostUsage、Trace、EvaluationReport 能互相引用 |
| 2 | 统一操作模型 | approve/pause/resume/cancel/rollback/publish/archive 语义一致 |
| 3 | 统一资源模型 | Agent/Tool/Skill/Knowledge/ContextPack/Artifact/Model 都有 resource identity |
| 4 | 统一风险模型 | 权限/隐私/成本/模型风险/工具风险/数据外发风险可组合评估 |
| 5 | 平台化发布 | 市场/模型供应链/持续评测/控制面可独立迭代但共享证据底座 |

### 技术债务与持续治理

| 项目 | 当前状态 | 后续行动 |
|---|---|---|
| Starter 依赖治理 | bootstrap 迁移 + Enforcer 完成 | all-starter 真实基础设施逐个验收 |
| 元数据 JDBC 拆分 | 默认端口 Bean 细粒度化完成 | 继续把 SQL/row mapper 迁出兼容门面 |
| 大类治理 | 部分超 500 行类 | 建立拆分触发器制度 |
| 测试覆盖率 | 核心链路有覆盖 | 目标 > 80%，关键 adapter 集成测试 |

---

## 附录：参考设计文档索引

| 文档 | 路径 |
|---|---|
| 架构路线图与愿景 | `docs/roadmap/architecture-roadmap-and-vision.md` |
| 未来规划审计 | `docs/zh/content/架构设计/未来规划审计与剩余设计.md` |
| 未实现功能设计汇总 | `docs/zh/content/架构设计/未实现功能详细设计/README.md` |
| MCP OAuth2 设计 | `docs/zh/content/架构设计/未实现功能详细设计/01-MCP-OAuth2-安全增强设计.md` |
| OpenAPI Connector 设计 | `docs/zh/content/架构设计/未实现功能详细设计/02-OpenAPI-Connector-设计.md` |
| Sandbox Runtime 设计 | `docs/zh/content/架构设计/未实现功能详细设计/03-Sandbox-Runtime-设计.md` |
| Agent Factory UI 设计 | `docs/zh/content/架构设计/未实现功能详细设计/04-Agent-Factory-UI-设计.md` |
| Multi-Agent/A2A 设计 | `docs/zh/content/架构设计/未实现功能详细设计/05-Multi-Agent-A2A-设计.md` |
| 当前代码架构 | `docs/architecture/current-code-architecture.md` |
| 路线图完成状态报告 | `docs/analysis/roadmap-completion-status-report.md` |

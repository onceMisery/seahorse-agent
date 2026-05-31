# Seahorse Agent 前端缺失功能审计

日期：2026-05-30

## 结论摘要

这个项目的后端已经不只是一个 RAG 问答系统，而是逐步长成了企业级 Agent 工程平台。当前前端覆盖了基础问答、知识库、文档分块、入库流水线、意图树、RAG Trace、模型配置、术语映射、样例问题、用户管理、记忆中心，以及一个聚合型 AI Infra/Agent 控制台。

主要缺口不是“没有管理台”，而是：

1. 后端很多企业级治理能力只有 API，没有稳定的一等前端页面。
2. 部分能力虽然在 AI Infra Console 里露出了入口，但只是列表、表单或 JSON 预览，没有形成真实工作流。
3. RAG 质量治理、记忆治理、Agent 生产发布治理、权限/密钥/连接器/沙箱等能力在产品信息架构上还没有被前端表达出来。

建议优先把前端从“基础知识库后台”升级为“Agent + RAG 运维治理控制台”：先补 Agent 生命周期和 RAG 质量闭环，再补安全权限与企业连接器，最后补高级记忆治理和沙箱调试。

## 当前前端已覆盖

| 前端区域 | 已覆盖能力 | 主要入口/文件 |
| --- | --- | --- |
| 聊天页 | 流式问答、停止任务、消息反馈、会话列表、附件、引用、Artifact/Approval 工作台组件 | `frontend/src/pages/ChatPage.tsx`、`frontend/src/components/chat/*` |
| 记忆中心 | 当前用户记忆列表、删除记忆、隐私模式 | `frontend/src/pages/MemoryCenterPage.tsx`、`frontend/src/services/userMemoryService.ts` |
| 管理后台框架 | 后台路由、侧边栏、知识库搜索、用户菜单 | `frontend/src/router.tsx`、`frontend/src/pages/admin/AdminLayout.tsx` |
| 仪表盘 | 总览、性能、趋势 | `frontend/src/pages/admin/dashboard/DashboardPage.tsx`、`frontend/src/services/dashboardService.ts` |
| 知识库 | 知识库 CRUD、文档上传/编辑/启停/删除、分块 CRUD、分块日志 | `frontend/src/pages/admin/knowledge/*`、`frontend/src/services/knowledgeService.ts` |
| 入库流水线 | Pipeline 和 Task 管理 | `frontend/src/pages/admin/ingestion/IngestionPage.tsx`、`frontend/src/services/ingestionService.ts` |
| 意图管理 | 意图树、意图列表、编辑、批量启停/删除 | `frontend/src/pages/admin/intent-tree/*`、`frontend/src/services/intentTreeService.ts` |
| 元数据治理 | Schema 字段只读、Review/Quarantine 列表与基础动作、质量报告 | `frontend/src/pages/admin/metadata-governance/MetadataGovernancePage.tsx`、`frontend/src/services/metadataGovernanceService.ts` |
| RAG Trace | Trace 列表、详情、节点瀑布/方法数据 | `frontend/src/pages/admin/traces/*`、`frontend/src/services/ragTraceService.ts` |
| 配置与运营 | 系统设置、模型配置、样例问题、术语映射、用户管理 | `frontend/src/pages/admin/settings/*`、`frontend/src/pages/admin/sample-questions/*`、`frontend/src/pages/admin/query-term-mapping/*`、`frontend/src/pages/admin/users/*` |
| Agent 聚合控制台 | Agent 列表、审批列表、工具列表、SRE 健康、成本聚合、反馈候选、就绪报告、灰度发布、评测回归的部分入口 | `frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx`、`frontend/src/services/aiInfraService.ts` |
| Agent 运行检视器 | 按 runId 查看事件、状态、上下文、工具调用 | `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.tsx`、`frontend/src/services/agentRunService.ts` |

## 高优先级缺失功能

### 1. Agent 定义与版本生命周期管理

后端已经提供 Agent 定义 CRUD、草稿更新、发布、禁用、模板创建、发布检查、版本回滚、Catalog 查询等能力，但前端目前主要是列表展示和少量运维动作，缺少完整的“创建 - 编辑 - 校验 - 发布 - 回滚 - 禁用”页面。

后端能力依据：

- `SeahorseAgentDefinitionController`：`/agents`、`/agents/{agentId}`、`/agents/{agentId}/draft`、`/agents/{agentId}/publish`、`/agents/{agentId}/disable`
- `SeahorseAgentFactoryController`：`/api/agent-templates`、`/api/agents/from-template`、`/api/agents/{agentId}/validate`、`/api/agents/{agentId}/publish-checks/latest`、`/api/agents/{agentId}/versions/{versionId}/rollback`、`/api/agent-catalog`
- 当前前端只在 `AiInfraConsolePage` 查询 `/agents`，没有 Agent 创建/编辑/发布向导。

建议前端落地：

- 新增 `Agent 管理` 页面：Agent 列表、状态、owner、风险等级、版本数、最近发布状态。
- 新增 Agent 编辑器：基础信息、instructions、模型策略、上下文策略、工具绑定、风险策略。
- 新增发布工作流：校验结果、发布检查、灰度入口、回滚入口。
- 新增从模板创建 Agent 的向导。

优先级：P0。它是 Agent 平台从“能跑”变成“可运营”的核心入口。

### 2. 工具目录与 Agent 工具绑定管理

后端已有工具目录启停、详情、Agent 版本工具绑定接口。前端 AI Infra Console 只展示工具列表，没有工具详情页、风险策略编辑、绑定配置、审批策略配置。

后端能力依据：

- `SeahorseToolCatalogController`：`GET /api/tools`、`GET /api/tools/{toolId}`、`POST /api/tools/{toolId}/enable`、`POST /api/tools/{toolId}/disable`
- `SeahorseAgentToolBindingController`：`PUT /api/agents/{agentId}/versions/{versionId}/tools`
- `SeahorseToolInvocationAuditController`：`GET /api/tool-invocations`

建议前端落地：

- 工具目录页：按 provider、resourceType、riskLevel、enabled 筛选。
- 工具详情页：参数 schema、输出 schema、风险等级、审批要求、最近调用记录。
- Agent 工具绑定面板：为指定 Agent version 选择工具、设置权限边界和审批策略。
- 工具调用审计页：按 run、tool、status、agent/version 查询。

优先级：P0。没有这个页面，Agent 工具能力很难安全开放给业务方。

### 3. 审批中心与 Human-in-the-Loop 工作流

前端已有聊天工作台里的 ApprovalCard 和 AI Infra Console 的审批列表，但缺少独立审批中心、审批详情、参数修改、历史决策、按风险聚合。

后端能力依据：

- `SeahorseApprovalController`：`GET /api/approvals`、`GET /api/agent-runs/{runId}/pending-approvals`、`GET /api/approvals/{approvalId}`、`approve/reject/modify`
- `approvalService.ts` 已封装 `modifyApprovalRequest`，但聚合控制台只使用 approve/reject。

建议前端落地：

- 新增 `审批中心` 页面：待审批、已处理、按风险/工具/Agent 过滤。
- 审批详情抽屉：原始参数、参数预览、上下文、风险说明、关联 run。
- 支持 modify 决策，而不只是 approve/reject。
- 与 Agent Inspector 打通：审批记录可跳转到运行事件和工具调用上下文。

优先级：P0。审批是高风险工具调用和企业落地的关键闭环。

### 4. RAG 检索质量评测与策略模板

后端已经有检索质量即时评测、策略对比、评测数据集 CRUD、运行记录、对比记录、策略模板 CRUD、版本质量对比等 API，但前端没有专门的 RAG 质量工作台。

后端能力依据：

- `SeahorseRetrievalEvaluationController`：`/knowledge-base/{kb-id}/retrieval-quality/evaluate`、`/compare`
- `SeahorseRetrievalEvaluationDatasetController`：评测数据集、evaluate、compare、runs、comparisons
- `SeahorseRetrievalStrategyTemplateController`：`/retrieval-strategy-templates`
- `SeahorseVersionQualityComparisonController`：`/version-quality/compare`
- 当前 `knowledgeService.ts` 没有封装这些接口。

建议前端落地：

- 新增 `RAG 评测` 页面：数据集列表、样本管理、单次评测、批量评测。
- 策略对比页：向量、关键词、RRF、rerank、metadata filter 等策略配置对比。
- 质量趋势页：命中率、MRR/NDCG、召回分布、失败样本。
- 知识库版本对比：导入前后、重分块前后、策略变更前后对比。

优先级：P0。没有评测闭环，知识库质量只能靠人工体感。

### 5. 高级记忆治理后台

当前用户侧只有“我的记忆”中心，但后端有完整的记忆治理、质量快照、冲突、画像事实、修正、操作日志、outbox、健康、readiness、策略配置、维护任务、review、trace、recall quality 等管理能力，前端基本未产品化。

后端能力依据：

- `SeahorseMemoryController`：`/memories`、`/memories/quality-snapshots`、`/memories/conflicts`、`/memories/profile-facts`、`/memories/corrections`、`/memories/operations`、`/memories/outbox`、`/memories/health`、`/memories/readiness`、`/memories/policy-config`
- `SeahorseMemoryMaintenanceController`：`/memories/maintenance/run`、`/maintenance-runs`、`/aggregate`
- `SeahorseMemoryReviewController`：review items、pending summary、feedback samples、approve/modify/reject
- `SeahorseMemoryTraceController`：`/memories/traces`
- `SeahorseMemoryRecallEvaluationController`、`SeahorseMemoryRecallGoldenHarnessController`
- 当前前端 `MemoryCenterPage` 只使用 `/api/me/memories`、删除和隐私模式。

建议前端落地：

- 管理后台新增 `记忆治理` 一级菜单。
- 子页包括：记忆检索、Review 队列、冲突处理、质量快照、维护任务、Recall 评测、Trace。
- 支持策略配置编辑、治理任务触发、冲突 resolve、review modify。

优先级：P1。适合在 Agent/RAG 主链路稳定后补齐。

## 中优先级缺失功能

### 6. 安全、权限和资源 ACL 管理

后端已有资源 ACL、访问决策查询、密钥管理、配额策略和配额决策 API。前端只有用户管理和个人配额摘要，没有资源级权限、密钥、配额策略工作台。

后端能力依据：

- `SeahorseResourceAclController`：规则创建、分页、禁用、dry-run import、import
- `SeahorseAccessDecisionController`：`GET /api/access-decisions`
- `SeahorseSecretController`：`POST /api/secrets`
- `SeahorseQuotaController`：配额策略创建、禁用、决策评估
- `SeahorseUserQuotaController`：个人 quota summary，前端已有 `quotaSummaryService.ts`

建议前端落地：

- `权限与安全` 页面：资源 ACL 规则、导入预检、访问决策查询。
- `密钥管理` 页面：创建密钥、查看绑定对象，不显示明文，仅展示脱敏摘要。
- `配额策略` 页面：租户/用户/Agent 维度策略、决策模拟、禁用。

优先级：P1。企业部署前必须补齐。

### 7. OpenAPI 连接器管理

后端具备 OpenAPI spec 导入、connector 列表、operation 列表、credential binding、operation 启停。前端没有对应入口。

后端能力依据：

- `SeahorseOpenApiConnectorController`：`POST /api/connectors/openapi`、`GET /api/connectors`、`GET /api/connectors/{connectorId}/operations`、credential binding、enable、disable
- `seahorse-agent-adapter-openapi` 提供 OpenAPI spec parser。

建议前端落地：

- `连接器` 页面：导入 OpenAPI spec、预览 operations、风险等级和启停状态。
- Operation 详情：参数、返回、风险映射、是否需要审批。
- 凭据绑定：选择已创建 secret，配置 authType、scope。
- 一键发布为 Agent Tool。

优先级：P1。它决定外部系统工具化能力能否被业务配置。

### 8. Agent 运行管理补全

前端 Agent Inspector 已能看 snapshot、events、cost summary，但缺少 run 启动、取消、retry/resume 操作按钮、checkpoints、steps、handoff、artifact 统一视图。

后端能力依据：

- `SeahorseAgentRunController`：`POST /agents/{agentId}/runs`、run 详情、steps、cancel、retry、resume、checkpoints、snapshot、cost-summary、events
- `SeahorseAgentHandoffController`：handoffs 列表、详情、cancel
- `SeahorseAgentArtifactController`：artifact 详情、run artifacts、download
- 前端 `agentRunService.ts` 已封装 snapshot/cost/resume/retry/events，但 Inspector 页面只加载 snapshot/cost/events。

建议前端落地：

- Agent 详情页提供“运行一次”入口。
- Inspector 增加 steps、checkpoints、handoffs、artifacts tab。
- 增加 cancel/retry/resume 操作按钮和状态保护。
- Artifact 下载和预览从聊天工作台复用到 Inspector。

优先级：P1。

### 9. 元数据治理补全

前端已有元数据治理页面，但后端能力更完整：schema 字段 CRUD、字段能力、抽取结果详情、review audit、correct/ignore/re-extract、backfill job 生命周期、质量 compare。

后端能力依据：

- `SeahorseMetadataSchemaController`：字段列表、字段能力、创建、更新、删除
- `SeahorseMetadataExtractionResultController`：抽取结果列表/详情
- `SeahorseMetadataReviewController`：详情、audits、approve/correct/ignore-field/re-extract/reject/quarantine
- `SeahorseMetadataBackfillController`：job 创建、列表、overview、详情、run-next、pause、resume、cancel
- `SeahorseMetadataQualityController`：report、compare
- 当前 `metadataGovernanceService.ts` 只覆盖 schema list、review list、approve/reject/quarantine、quarantine list、resolve/retry、quality report。

建议前端落地：

- Schema 管理支持字段 CRUD 和字段能力展示。
- Review 详情支持 correct、ignore-field、re-extract、audits。
- Backfill 工作台支持任务创建、暂停、恢复、取消、批次推进。
- 抽取结果详情页和质量 compare 页。

优先级：P1。

## 低优先级但有价值的缺失功能

### 10. 插件健康与扩展注册表

后端有插件健康、状态、注册表、状态更新接口；前端没有专门插件页。

后端能力依据：

- `SeahorsePluginController`：`/agent/plugins/health`、`/status`、`/registry`、`POST /status`

建议前端落地：

- `插件中心` 页面：按 adapter/feature/port 查看加载状态、健康、错误。
- 支持启停或状态标记时，需要明确是运行时控制还是治理标注，避免误导。

优先级：P2。

### 11. 沙箱运行台

后端有 sandbox session、execute、close、artifacts API。前端聊天页有 ArtifactSandbox 组件，但没有管理员沙箱控制台。

后端能力依据：

- `SeahorseSandboxController`：`POST /api/sandbox/sessions`、`execute`、`close`、`artifacts`

建议前端落地：

- `沙箱` 页面：创建 session、输入 payload、执行、查看输出和 artifacts。
- 与 Agent run 的 tool execution 关联，用于复现高风险工具调用。

优先级：P2。注意默认高级功能 gate 关闭，前端应显示能力未启用状态。

### 12. 审计日志与成本明细

后端有 audit events、cost usage records 和 aggregate；前端只用 aggregate 做指标，没有审计日志页和成本明细页。

后端能力依据：

- `SeahorseAuditEventController`：`GET /api/audit-events`、`GET /api/audit-events/{auditId}`
- `SeahorseCostUsageController`：`POST /api/cost-usage-records`、`GET /api/cost-usage:aggregate`

建议前端落地：

- `审计日志` 页面：按 tenant、agent、run、actor、eventType 查询。
- `成本分析` 页面：按时间、Agent、模型、工具、租户聚合。

优先级：P2。

### 13. 文档刷新和关键词索引维护

知识库页面已有上传、分块、启停，但后端还支持 URL/定时文档刷新、到期刷新、关键词索引重建，前端没有明显入口。

后端能力依据：

- `SeahorseDocumentRefreshController`：`/knowledge-base/docs/{doc-id}/refresh`、`/knowledge-base/docs/refresh-due`
- `SeahorseKeywordIndexMaintenanceController`：doc/kb keyword-index rebuild
- `KnowledgeDocument` 类型里已有 `scheduleEnabled`、`scheduleCron`、`sourceLocation` 字段。

建议前端落地：

- 文档详情页增加“立即刷新”“重建关键词索引”。
- 知识库页增加“重建全库关键词索引”。
- 定时刷新状态、最近刷新时间、失败原因展示。

优先级：P2。

## 产品模式和功能 Gate 缺口

前后端都有高级功能 gate，但两边的表达不完全对齐。

后端 `AdvancedFeature` 包含：Sandbox、Connector、MCP Tool、Secret、Agent Handoff、Remote/Local Agent、Intent、Ingestion、Tool Catalog、Agent Definition、Agent Factory、Tool Binding、Agent Run、Agent Evaluation、Production Gate、Readiness、Rollout、Quota、Resource ACL。

前端 `ADVANCED_ADMIN_FEATURES` 目前只有：

- `AI_INFRA_CONSOLE`
- `INTENT_MANAGEMENT`
- `INGESTION_MANAGEMENT`

这会导致前端无法精确表达“某个企业功能未启用”的状态，只能粗粒度地隐藏 AI Infra、意图、入库入口。建议：

- 前端补齐与后端 `AdvancedFeature` 对齐的 feature flags。
- 后端提供 `/api/features` 或 `/rag/settings` 扩展字段返回实际可用功能。
- 前端菜单根据后端能力显示：可用、未启用、无权限、服务缺失四种状态。

## 建议路线图

### 第一阶段：把 Agent 平台主链路补成闭环

目标：让管理员可以完整配置和运营 Agent。

包含：

- Agent 管理：定义、草稿、发布、禁用、模板创建、回滚。
- 工具目录：详情、启停、绑定到 Agent version。
- 审批中心：详情、approve/reject/modify。
- Agent Inspector 补齐 steps/checkpoints/handoffs/artifacts 和 cancel/retry/resume。

### 第二阶段：把 RAG 质量治理补成闭环

目标：让知识库质量可评测、可对比、可回归。

包含：

- 检索评测数据集管理。
- 单次评测、批量评测、策略对比。
- 检索策略模板 CRUD。
- 知识库版本质量对比。
- 文档刷新、关键词索引维护入口。

### 第三阶段：补企业安全与连接器

目标：让平台可以安全接入企业系统。

包含：

- 资源 ACL、访问决策查询、导入预检。
- 密钥管理与脱敏展示。
- 配额策略与决策模拟。
- OpenAPI 连接器导入、operation 管理、凭据绑定、启停。

### 第四阶段：补记忆治理与可观测性深水区

目标：让长期记忆、审计、成本、插件、沙箱具备运维面。

包含：

- 记忆治理后台：Review、冲突、质量、维护、Trace、Recall 评测。
- 审计日志和成本明细。
- 插件中心。
- 沙箱控制台。

## 建议的信息架构

建议把后台侧边栏重组为：

| 一级菜单 | 二级能力 |
| --- | --- |
| 总览 | Dashboard、SRE Health、成本概览 |
| 对话与记忆 | 会话、用户记忆、记忆治理、记忆评测 |
| 知识与 RAG | 知识库、文档、分块、入库流水线、RAG 评测、策略模板、Trace |
| Agent | Agent 管理、Agent 运行、Inspector、审批中心、工具目录、工具调用审计、灰度发布、生产门禁 |
| 集成 | OpenAPI 连接器、MCP/插件、密钥 |
| 安全治理 | 用户、资源 ACL、访问决策、配额策略、审计日志 |
| 系统配置 | 模型配置、系统设置、样例问题、术语映射 |

## 需要避免的前端落地误区

1. 不要把所有企业功能继续塞进一个 `AI Infra Console` 大页。现在这个页已经在承担 overview、审批、反馈、agents、tools、operations，继续堆会很难用。
2. 不要只做 JSON 预览。很多 API 是治理动作，应该有明确状态、风险、确认、回滚、跳转上下文。
3. 不要只按技术模块命名菜单。业务用户更关心“发布 Agent”“审批工具调用”“评估知识库质量”“处理记忆冲突”。
4. 不要隐藏高级能力但不给解释。功能被 product mode 或 advanced gate 关闭时，前端应展示“未启用/需配置”的空状态。

## 证据索引

后端 Controller 证据主要来自：

- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/*Controller.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeature.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeatureGate.java`

前端覆盖证据主要来自：

- `frontend/src/router.tsx`
- `frontend/src/pages/admin/AdminLayout.tsx`
- `frontend/src/services/*.ts`
- `frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx`
- `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.tsx`
- `frontend/src/pages/MemoryCenterPage.tsx`
- `frontend/src/pages/ChatPage.tsx`

本审计没有启动后端或前端运行时，也没有验证接口真实可用性；结论基于静态代码结构、路由、service 调用和 Controller 映射。

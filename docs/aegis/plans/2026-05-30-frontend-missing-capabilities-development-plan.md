# Seahorse Agent 前端缺失功能开发文档

日期：2026-05-30

## Goal

把 `docs/frontend-missing-capabilities-audit.md` 中识别出的前端缺失能力，转化为可排期、可拆票、可验收的前端开发文档。目标不是一次性把所有页面做完，而是定义清晰的信息架构、路由、服务层边界、模块交互、接口映射、验收标准与分阶段交付顺序，让 Seahorse Agent 前端从“知识库管理后台 + 聚合 Agent 控制台”升级为“Agent + RAG 运维治理控制台”。

## Architecture

当前前端架构是 Vite + React + TypeScript + React Router + Zustand + Radix UI + Tailwind 风格组件。管理后台由 `frontend/src/router.tsx` 注册路由，`frontend/src/pages/admin/AdminLayout.tsx` 维护侧边栏、面包屑、搜索与后台壳层。现有高级功能 gate 在 `frontend/src/config/productMode.ts`，只覆盖 `AI_INFRA_CONSOLE`、`INTENT_MANAGEMENT`、`INGESTION_MANAGEMENT` 三类能力。

本计划建议采用“一级领域菜单 + 独立页面模块 + service 按后端 Controller 拆分”的方式，不继续把企业能力堆进单个 `AiInfraConsolePage`。新的前端页面应复用现有 `api` 封装、分页模式、toast 反馈、Dialog/Drawer/Tabs/Table/Form 组件风格，并优先将高风险动作做成带确认、审计上下文和错误回显的工作流。

## Tech Stack

- 前端：React 18、TypeScript、Vite、React Router、Zustand、Radix UI、lucide-react、recharts、Vitest、Testing Library。
- 后端接口：Spring MVC Controller 暴露的 REST API，主要分布在 `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/*Controller.java`。
- 验证命令：`npm run lint`、`npm test`、`npm run build`，在 `frontend` 目录执行。

## Baseline/Authority Refs

- 需求来源：`docs/frontend-missing-capabilities-audit.md`
- 前端路由：`frontend/src/router.tsx`
- 管理后台壳层：`frontend/src/pages/admin/AdminLayout.tsx`
- 高级功能 gate：`frontend/src/config/productMode.ts`
- 聚合控制台：`frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx`
- Agent 检视器：`frontend/src/pages/admin/agent-inspector/AgentInspectorPage.tsx`
- 现有服务层：`frontend/src/services/aiInfraService.ts`、`frontend/src/services/agentRunService.ts`、`frontend/src/services/approvalService.ts`、`frontend/src/services/knowledgeService.ts`、`frontend/src/services/metadataGovernanceService.ts`
- 后端功能 gate：`seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeature.java`

## Compatibility Boundary

1. 保持现有 `/admin/dashboard`、`/admin/knowledge`、`/admin/ingestion`、`/admin/intent-tree`、`/admin/metadata-governance`、`/admin/traces`、`/admin/ai-infra`、`/admin/agent-inspector`、`/admin/users` 等路由可用。
2. `AiInfraConsolePage` 在新页面完全覆盖前作为概览页保留，不在第一阶段删除。
3. 新 service 优先新增独立文件，不把所有接口继续塞进 `aiInfraService.ts`。
4. 所有高风险动作必须显示确认、操作者输入或决策备注，并展示 API 错误原因。
5. 前端 feature gate 必须支持“可用、未启用、无权限、接口缺失”四类状态表达；在后端尚未提供 `/api/features` 前，允许前端先基于本地 env 与 403/404/501 响应降级。
6. 不改变后端 API 语义；如果发现接口字段不足，另开后端契约任务，不在前端伪造安全状态。

## Verification

每个实现阶段至少执行：

```powershell
cd frontend
npm run lint
npm test
npm run build
```

涉及具体页面时，补充浏览器手工验证：

1. 管理员登录后侧边栏菜单与面包屑正确。
2. feature gate 关闭时页面显示未启用状态，而不是空白或 404。
3. 列表筛选、分页、详情抽屉、表单提交、错误提示可用。
4. approve/reject/modify、publish/rollback/disable、enable/disable、pause/resume/cancel 等动作均有确认与结果反馈。

## 当前缺口总览

| 优先级 | 模块 | 当前前端状态 | 目标状态 |
| --- | --- | --- | --- |
| P0 | Agent 生命周期 | 只有聚合列表和少量运维入口 | 可创建、编辑草稿、校验、发布、回滚、禁用、从模板创建 |
| P0 | 工具目录与绑定 | 只在 AI Infra Console 展示工具列表 | 工具详情、启停、Agent version 绑定、调用审计 |
| P0 | 审批中心 | 聊天卡片和聚合列表，modify 未产品化 | 独立审批中心，详情上下文，approve/reject/modify 全闭环 |
| P0 | RAG 评测 | 无专门页面 | 数据集、样本、单次评测、批量评测、策略对比、版本质量对比 |
| P1 | Agent 运行管理 | Inspector 只看事件、状态、上下文、工具 | run 启动、取消、retry/resume、steps、checkpoints、handoffs、artifacts |
| P1 | 安全治理 | 用户管理和个人 quota 摘要 | ACL、访问决策、密钥、配额策略 |
| P1 | OpenAPI 连接器 | 无页面 | spec 导入、operation 管理、凭据绑定、发布为工具 |
| P1 | 元数据治理补全 | 已有基础 Review/Quarantine | schema CRUD、correct/ignore/re-extract、backfill、质量 compare |
| P1 | 记忆治理 | 用户侧我的记忆 | 管理侧 review、冲突、质量、维护、trace、recall 评测 |
| P2 | 审计、成本、插件、沙箱 | 局部指标或无入口 | 专门运维页和复现台 |
| P2 | 文档刷新与关键词索引 | 文档字段存在但无动作入口 | 文档刷新、到期刷新、doc/kb 关键词索引重建 |

## 信息架构

建议将后台菜单从“导航/设置”扩展为领域分组：

| 一级分组 | 页面 |
| --- | --- |
| 总览 | Dashboard、SRE Health、成本概览 |
| 知识与 RAG | 知识库、文档、分块、入库流水线、RAG 评测、策略模板、版本质量对比、Trace |
| Agent | Agent 管理、Agent 运行、Agent 检视器、审批中心、工具目录、工具调用审计、灰度发布、生产门禁 |
| 集成 | OpenAPI 连接器、MCP/插件、密钥 |
| 安全治理 | 用户、资源 ACL、访问决策、配额策略、审计日志 |
| 记忆治理 | 用户记忆、Review 队列、冲突处理、质量快照、维护任务、Recall 评测、Trace |
| 系统配置 | 模型配置、系统设置、示例问题、关键词映射 |

推荐首批路由：

| 路由 | 页面文件 | 说明 |
| --- | --- | --- |
| `/admin/agents` | `frontend/src/pages/admin/agents/AgentListPage.tsx` | Agent 列表与状态 |
| `/admin/agents/new` | `frontend/src/pages/admin/agents/AgentCreatePage.tsx` | 从空白或模板创建 |
| `/admin/agents/:agentId` | `frontend/src/pages/admin/agents/AgentDetailPage.tsx` | 详情、版本、发布检查 |
| `/admin/agents/:agentId/edit` | `frontend/src/pages/admin/agents/AgentEditorPage.tsx` | 草稿编辑 |
| `/admin/tools` | `frontend/src/pages/admin/tools/ToolCatalogPage.tsx` | 工具目录 |
| `/admin/tools/:toolId` | `frontend/src/pages/admin/tools/ToolDetailPage.tsx` | 工具详情和审计摘要 |
| `/admin/tool-invocations` | `frontend/src/pages/admin/tools/ToolInvocationAuditPage.tsx` | 工具调用审计 |
| `/admin/approvals` | `frontend/src/pages/admin/approvals/ApprovalCenterPage.tsx` | Human-in-the-loop 审批中心 |
| `/admin/rag-evaluation` | `frontend/src/pages/admin/rag-evaluation/RagEvaluationPage.tsx` | RAG 数据集与评测 |
| `/admin/rag-strategies` | `frontend/src/pages/admin/rag-evaluation/RetrievalStrategyTemplatePage.tsx` | 检索策略模板 |
| `/admin/security/resource-acl` | `frontend/src/pages/admin/security/ResourceAclPage.tsx` | 资源 ACL |
| `/admin/security/access-decisions` | `frontend/src/pages/admin/security/AccessDecisionPage.tsx` | 访问决策查询 |
| `/admin/security/quotas` | `frontend/src/pages/admin/security/QuotaPolicyPage.tsx` | 配额策略与模拟 |
| `/admin/integrations/connectors` | `frontend/src/pages/admin/integrations/OpenApiConnectorPage.tsx` | OpenAPI 连接器 |
| `/admin/secrets` | `frontend/src/pages/admin/integrations/SecretPage.tsx` | 密钥创建与脱敏列表 |
| `/admin/memory-governance` | `frontend/src/pages/admin/memory-governance/MemoryGovernancePage.tsx` | 管理侧记忆治理 |

## 共享前端约定

### Feature Gate

修改 `frontend/src/config/productMode.ts`：

- 扩展 `ADVANCED_ADMIN_FEATURES`，与后端 `AdvancedFeature` 保持语义对齐。
- 新增 `getAdvancedFeatureState(feature)`，返回 `{ visible, enabled, reason }`。
- 在后端能力接口落地前，状态来源为 env、产品模式、接口响应错误三者组合。

建议前端枚举：

```ts
AGENT_DEFINITION_MANAGEMENT
AGENT_FACTORY_MANAGEMENT
AGENT_TOOL_BINDING_MANAGEMENT
AGENT_RUN_MANAGEMENT
AGENT_EVALUATION
TOOL_CATALOG_MANAGEMENT
PRODUCTION_GATE
AGENT_ROLLOUT_MANAGEMENT
CONNECTOR_MANAGEMENT
SECRET_MANAGEMENT
RESOURCE_ACL_MANAGEMENT
QUOTA_MANAGEMENT
SANDBOX
MCP_TOOL
MEMORY_GOVERNANCE
RAG_EVALUATION
METADATA_GOVERNANCE
AUDIT_LOG
COST_ANALYTICS
```

验收标准：

- `AdminLayout` 菜单能按 feature 显示、隐藏或展示未启用入口。
- feature 关闭时，直接访问受控路由显示统一 `FeatureUnavailableState`。
- 现有三项高级功能 env 行为不回退。

### Service 分层

新增 service 文件：

| 文件 | 职责 |
| --- | --- |
| `frontend/src/services/agentDefinitionService.ts` | Agent 定义、草稿、发布、禁用 |
| `frontend/src/services/agentFactoryService.ts` | 模板、从模板创建、校验、发布检查、回滚、catalog |
| `frontend/src/services/toolCatalogService.ts` | 工具列表、详情、启停、绑定、调用审计 |
| `frontend/src/services/ragEvaluationService.ts` | 检索评测、数据集、策略模板、版本对比 |
| `frontend/src/services/memoryGovernanceService.ts` | 管理侧记忆治理、review、维护、trace |
| `frontend/src/services/securityGovernanceService.ts` | ACL、访问决策、quota、secret |
| `frontend/src/services/openApiConnectorService.ts` | OpenAPI connector 与 operation 管理 |
| `frontend/src/services/auditCostService.ts` | 审计日志、成本明细与聚合 |

接口返回类型优先定义最小必要字段，复杂未知结构用 `Record<string, unknown>` 承接，并在详情页用结构化 JSON Viewer 展示原始响应。

### 页面交互模式

1. 列表页：筛选条、批量状态、分页表格、空状态、错误重试。
2. 详情页：摘要区、关键风险 badge、Tabs、右侧操作区。
3. 动作类弹窗：展示影响对象、不可逆说明、备注输入、确认按钮 loading。
4. JSON/Schema 展示：提供格式化、复制、折叠，避免只把大 JSON 扔在普通 textarea。
5. 关联跳转：Agent、run、approval、tool invocation、artifact、trace 之间必须可互相跳转。

## P0 模块一：Agent 生命周期管理

### 后端接口

- `POST /agents`
- `GET /agents`
- `GET /agents/{agentId}`
- `PUT /agents/{agentId}/draft`
- `POST /agents/{agentId}/publish`
- `POST /agents/{agentId}/disable`
- `GET /api/agent-templates`
- `POST /api/agents/from-template`
- `POST /api/agents/{agentId}/validate`
- `GET /api/agents/{agentId}/publish-checks/latest`
- `POST /api/agents/{agentId}/versions/{versionId}/rollback`
- `GET /api/agent-catalog`

### 前端文件

- 新建 `frontend/src/services/agentDefinitionService.ts`
- 新建 `frontend/src/services/agentFactoryService.ts`
- 新建 `frontend/src/pages/admin/agents/AgentListPage.tsx`
- 新建 `frontend/src/pages/admin/agents/AgentCreatePage.tsx`
- 新建 `frontend/src/pages/admin/agents/AgentDetailPage.tsx`
- 新建 `frontend/src/pages/admin/agents/AgentEditorPage.tsx`
- 新建 `frontend/src/pages/admin/agents/components/AgentPublishDialog.tsx`
- 新建 `frontend/src/pages/admin/agents/components/AgentRollbackDialog.tsx`
- 修改 `frontend/src/router.tsx`
- 修改 `frontend/src/pages/admin/AdminLayout.tsx`

### 页面能力

Agent 列表：

- 支持 tenant、keyword、status、owner、riskLevel 筛选。
- 展示 Agent ID、名称、状态、当前版本、最近发布状态、工具数量、最近运行时间。
- 行动作：查看详情、编辑草稿、运行一次、禁用。

Agent 创建：

- 支持从模板创建和空白创建。
- 模板列表来自 `/api/agent-templates`。
- 创建成功后跳转详情页或编辑器。

Agent 编辑器：

- 分区编辑基础信息、instructions、模型策略、上下文策略、工具绑定摘要、风险策略。
- 保存调用 `PUT /agents/{agentId}/draft`。
- 保存后可调用 `/api/agents/{agentId}/validate` 展示校验结果。

发布工作流：

- 发布前展示 latest publish checks。
- 检查失败时禁止发布，显示失败项。
- 发布成功后刷新详情与版本列表。
- 版本回滚需要选择 versionId、填写原因，调用 rollback API。

### 验收标准

- 管理员能从模板创建 Agent，并看到详情页。
- 修改草稿后能保存、校验、发布。
- 发布检查失败时，发布按钮不可提交或提交后错误清晰展示。
- 禁用与回滚动作都有确认弹窗和操作备注。
- 直接访问不存在的 Agent 显示 404/空状态，不导致页面崩溃。

### 测试建议

- `AgentListPage`：筛选参数正确传给 service，空列表显示空状态。
- `AgentEditorPage`：保存草稿、校验、发布按钮状态。
- `AgentPublishDialog`：检查失败与成功两种状态。

## P0 模块二：工具目录、绑定与调用审计

### 后端接口

- `GET /api/tools`
- `GET /api/tools/{toolId}`
- `POST /api/tools/{toolId}/enable`
- `POST /api/tools/{toolId}/disable`
- `PUT /api/agents/{agentId}/versions/{versionId}/tools`
- `GET /api/tool-invocations`

### 前端文件

- 新建 `frontend/src/services/toolCatalogService.ts`
- 新建 `frontend/src/pages/admin/tools/ToolCatalogPage.tsx`
- 新建 `frontend/src/pages/admin/tools/ToolDetailPage.tsx`
- 新建 `frontend/src/pages/admin/tools/ToolInvocationAuditPage.tsx`
- 新建 `frontend/src/pages/admin/tools/components/AgentToolBindingPanel.tsx`
- 新建 `frontend/src/pages/admin/tools/components/ToolRiskBadge.tsx`

### 页面能力

工具目录：

- 按 keyword、provider、resourceType、riskLevel、enabled 筛选。
- 展示工具名称、provider、资源类型、风险等级、启用状态、审批要求。
- 启停工具时展示受影响 Agent 数量；如果后端没有该字段，前端展示“影响范围请在绑定页确认”。

工具详情：

- 展示参数 schema、输出 schema、风险等级、审批要求、最近调用记录。
- 提供启用/禁用入口。
- 链接到工具调用审计页。

Agent 工具绑定：

- 在 Agent 详情页或版本页嵌入 `AgentToolBindingPanel`。
- 支持勾选工具、设置权限边界、审批策略、保存绑定。
- 保存调用 `PUT /api/agents/{agentId}/versions/{versionId}/tools`。

工具调用审计：

- 按 runId、agentId、toolId、status、时间范围筛选。
- 展示调用参数摘要、结果状态、耗时、审批 ID、关联 run。

### 验收标准

- 工具列表可筛选、分页、启停。
- 工具详情能稳定展示 schema 和风险信息。
- Agent version 能绑定工具并保存。
- 工具调用审计可从 tool detail 和 Agent Inspector 跳转。

## P0 模块三：审批中心

### 后端接口

- `GET /api/approvals`
- `GET /api/agent-runs/{runId}/pending-approvals`
- `GET /api/approvals/{approvalId}`
- `POST /api/approvals/{approvalId}/approve`
- `POST /api/approvals/{approvalId}/reject`
- `POST /api/approvals/{approvalId}/modify`

### 前端文件

- 复用并扩展 `frontend/src/services/approvalService.ts`
- 新建 `frontend/src/pages/admin/approvals/ApprovalCenterPage.tsx`
- 新建 `frontend/src/pages/admin/approvals/components/ApprovalDetailDrawer.tsx`
- 新建 `frontend/src/pages/admin/approvals/components/ApprovalDecisionDialog.tsx`
- 修改 `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.tsx`，增加审批跳转与详情入口

### 页面能力

- 列表支持状态、风险等级、toolId、agentId、runId、提交人、时间范围筛选。
- 详情抽屉展示原始参数、参数预览、运行上下文、风险说明、关联 tool/run/agent。
- 决策支持 approve、reject、modify。
- modify 需要编辑 `argumentsPreviewJson`，并在提交前做 JSON 解析校验。
- 已处理审批展示决策人、决策时间、备注、修改后的参数。

### 验收标准

- 待审批列表能处理 approve/reject/modify 三类决策。
- 参数 JSON 非法时不能提交 modify。
- 从 Agent Inspector 能打开关联审批详情。
- 决策后列表状态刷新，不重复提交。

## P0 模块四：RAG 评测与策略模板

### 后端接口

- `POST /knowledge-base/{kb-id}/retrieval-quality/evaluate`
- `POST /knowledge-base/{kb-id}/retrieval-quality/compare`
- `GET /knowledge-base/{kb-id}/retrieval-evaluation-datasets`
- `GET /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}`
- `POST /knowledge-base/{kb-id}/retrieval-evaluation-datasets`
- `PUT /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}`
- `DELETE /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}`
- `POST /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/evaluate`
- `POST /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/compare`
- `GET /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/runs`
- `GET /knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/comparisons`
- `GET /knowledge-base/{kb-id}/retrieval-strategy-templates`
- `POST /knowledge-base/{kb-id}/retrieval-strategy-templates`
- `PUT /knowledge-base/{kb-id}/retrieval-strategy-templates/{template-key}`
- `DELETE /knowledge-base/{kb-id}/retrieval-strategy-templates/{template-key}`
- `POST /knowledge-base/{kb-id}/version-quality/compare`

### 前端文件

- 新建 `frontend/src/services/ragEvaluationService.ts`
- 新建 `frontend/src/pages/admin/rag-evaluation/RagEvaluationPage.tsx`
- 新建 `frontend/src/pages/admin/rag-evaluation/RetrievalDatasetDetailPage.tsx`
- 新建 `frontend/src/pages/admin/rag-evaluation/RetrievalStrategyTemplatePage.tsx`
- 新建 `frontend/src/pages/admin/rag-evaluation/VersionQualityComparePage.tsx`
- 新建 `frontend/src/pages/admin/rag-evaluation/components/StrategyEditor.tsx`
- 新建 `frontend/src/pages/admin/rag-evaluation/components/EvaluationResultPanel.tsx`

### 页面能力

RAG 评测首页：

- 选择知识库后展示评测数据集、最近运行、最近对比。
- 支持新增数据集，数据集包含 query、expectedDocumentIds、expectedChunkIds、tenantId、备注。

数据集详情：

- 样本列表支持新增、编辑、删除、批量导入 JSON。
- 支持对单个策略运行 evaluate。
- 支持多策略 compare。
- 展示命中率、MRR/NDCG、失败样本、每个 query 的命中文档与 chunk。

策略模板：

- 列表展示模板 key、名称、topK、rerank、metadata filter、options。
- 支持创建、编辑、删除。
- `StrategyEditor` 用表单编辑常见字段，用 JSON 编辑高级 options。

版本质量对比：

- 选择 base version 和 candidate version。
- 执行 compare 后展示质量差异、退化样本、收益样本。

### 验收标准

- 管理员能创建数据集并运行评测。
- 至少支持一个策略与两个策略对比工作流。
- 评测结果能定位到失败样本和对应 chunk。
- 策略模板 CRUD 可用，非法 JSON options 无法提交。

## P1 模块五：Agent 运行管理与 Inspector 补全

### 后端接口

- `POST /agents/{agentId}/runs`
- `GET /agent-runs/{runId}`
- `GET /agent-runs/{runId}/steps`
- `POST /agent-runs/{runId}/cancel`
- `POST /agent-runs/{runId}/retry`
- `POST /agent-runs/{runId}/resume`
- `GET /agent-runs/{runId}/checkpoints`
- `GET /api/agent-runs/{runId}/snapshot`
- `GET /api/agent-runs/{runId}/cost-summary`
- `GET /api/agent-runs/{runId}/events`
- `GET /api/agent-runs/{runId}/handoffs`
- `GET /api/agent-handoffs/{handoffId}`
- `POST /api/agent-handoffs/{handoffId}/cancel`
- `GET /api/agent-runs/{runId}/artifacts`
- `GET /api/agent-artifacts/{artifactId}`
- `GET /api/agent-artifacts/{artifactId}/download`

### 前端文件

- 扩展 `frontend/src/services/agentRunService.ts`
- 新建 `frontend/src/services/agentArtifactService.ts`
- 新建 `frontend/src/services/agentHandoffService.ts`
- 新建 `frontend/src/pages/admin/agent-runs/AgentRunListPage.tsx`
- 扩展 `frontend/src/pages/admin/agent-inspector/AgentInspectorPage.tsx`

### 页面能力

- Agent 详情页提供“运行一次”按钮，填写 input/context 后创建 run。
- Agent run 列表按 agentId、status、runId、时间范围筛选。
- Inspector 增加 steps、checkpoints、handoffs、artifacts tabs。
- run 状态允许时显示 cancel、retry、resume。
- artifacts 支持预览文本/JSON，二进制文件走 download。

### 验收标准

- 输入 runId 能看到 snapshot、events、steps、checkpoints、cost summary。
- 可从失败 run 执行 retry，从等待 run 执行 resume，从运行中 run 执行 cancel。
- artifact 下载链接可用，错误状态有提示。

## P1 模块六：安全治理、密钥与配额

### 后端接口

- `POST /api/resource-acl-rules`
- `GET /api/resource-acl-rules`
- `POST /api/resource-acl-rules:dry-run-import`
- `POST /api/resource-acl-rules:import`
- `POST /api/resource-acl-rules/{ruleId}/disable`
- `GET /api/access-decisions`
- `POST /api/secrets`
- `POST /api/quotas/policies`
- `POST /api/quotas/policies/{policyId}/disable`
- `POST /api/quotas/decisions:evaluate`

### 前端文件

- 新建 `frontend/src/services/securityGovernanceService.ts`
- 新建 `frontend/src/pages/admin/security/ResourceAclPage.tsx`
- 新建 `frontend/src/pages/admin/security/AccessDecisionPage.tsx`
- 新建 `frontend/src/pages/admin/security/QuotaPolicyPage.tsx`
- 新建 `frontend/src/pages/admin/integrations/SecretPage.tsx`

### 页面能力

资源 ACL：

- 规则列表支持 tenant、scope、resource、principal、effect、status 筛选。
- 创建规则时明确 allow/deny、优先级、过期时间、原因。
- 导入支持 dry-run，展示新增、跳过、冲突、错误统计后再执行 import。

访问决策：

- 按 tenant、subject、resource、action、agentId、runId 查询。
- 展示命中的 ACL 规则、决策结果和拒绝原因。

密钥：

- 只支持创建和展示脱敏摘要。
- 创建后不展示明文；如果后端返回一次性 secret，需要弹窗提示“只显示一次”。
- 绑定关系在连接器 credential binding 页面展示。

配额：

- 创建策略，禁用策略。
- 决策模拟输入 tenant/user/agent/resource/cost，展示 allow/deny 与命中规则。

### 验收标准

- ACL dry-run 结果可读，并能阻止有错误的导入。
- secret 不在列表、日志、toast 中泄露明文。
- quota 决策模拟能展示命中策略。

## P1 模块七：OpenAPI 连接器管理

### 后端接口

- `POST /api/connectors/openapi`
- `GET /api/connectors`
- `GET /api/connectors/{connectorId}/operations`
- `PUT /api/connectors/{connectorId}/operations/{operationId}/credential-binding`
- `GET /api/connectors/{connectorId}/operations/{operationId}/credential-binding`
- `POST /api/connectors/{connectorId}/operations/{operationId}/enable`
- `POST /api/connectors/{connectorId}/operations/{operationId}/disable`

### 前端文件

- 新建 `frontend/src/services/openApiConnectorService.ts`
- 新建 `frontend/src/pages/admin/integrations/OpenApiConnectorPage.tsx`
- 新建 `frontend/src/pages/admin/integrations/OpenApiConnectorDetailPage.tsx`
- 新建 `frontend/src/pages/admin/integrations/components/OpenApiImportDialog.tsx`
- 新建 `frontend/src/pages/admin/integrations/components/CredentialBindingDialog.tsx`

### 页面能力

- 支持上传 OpenAPI JSON/YAML 或粘贴 spec 文本。
- 导入后展示 connector、operation 数量、解析错误。
- operation 列表展示 method、path、operationId、风险等级、启用状态、是否绑定凭据。
- 凭据绑定选择 secretId、authType、scope。
- operation 启停必须确认影响。

### 验收标准

- 可导入 spec 并看到 operation 列表。
- operation 可启停。
- 凭据绑定保存后能再次读取展示脱敏信息。

## P1 模块八：元数据治理补全

### 后端接口

- `GET /knowledge-base/{kb-id}/metadata-schema/fields`
- `GET /knowledge-base/{kb-id}/metadata-schema/field-capabilities`
- `POST /knowledge-base/{kb-id}/metadata-schema/fields`
- `PUT /metadata-schema/fields/{field-id}`
- `DELETE /metadata-schema/fields/{field-id}`
- `GET /metadata-extraction/results`
- `GET /metadata-extraction/results/{result-id}`
- `GET /metadata-review/items/{item-id}`
- `GET /metadata-review/items/{item-id}/audits`
- `POST /metadata-review/items/{item-id}/correct`
- `POST /metadata-review/items/{item-id}/ignore-field`
- `POST /metadata-review/items/{item-id}/re-extract`
- `GET /knowledge-base/{kb-id}/metadata-backfill/jobs`
- `POST /knowledge-base/{kb-id}/metadata-backfill/jobs`
- `GET /knowledge-base/{kb-id}/metadata-backfill/overview`
- `POST /metadata-backfill/jobs/{job-id}/run-next`
- `POST /metadata-backfill/jobs/{job-id}/pause`
- `POST /metadata-backfill/jobs/{job-id}/resume`
- `POST /metadata-backfill/jobs/{job-id}/cancel`
- `GET /knowledge-base/{kb-id}/metadata-quality/compare`

### 前端文件

- 扩展 `frontend/src/services/metadataGovernanceService.ts`
- 拆分 `frontend/src/pages/admin/metadata-governance/MetadataGovernancePage.tsx`
- 新建 `frontend/src/pages/admin/metadata-governance/components/MetadataSchemaManager.tsx`
- 新建 `frontend/src/pages/admin/metadata-governance/components/MetadataReviewDetailDrawer.tsx`
- 新建 `frontend/src/pages/admin/metadata-governance/components/MetadataBackfillPanel.tsx`
- 新建 `frontend/src/pages/admin/metadata-governance/components/MetadataQualityComparePanel.tsx`

### 页面能力

- Schema 字段 CRUD 和字段能力展示。
- Review 详情支持 approve、reject、correct、ignore-field、re-extract、quarantine。
- Review audit 时间线。
- Backfill job 创建、列表、overview、run-next、pause、resume、cancel。
- 抽取结果详情展示字段、置信度、来源文档。
- 质量 compare 展示变更前后覆盖率和低置信度差异。

### 验收标准

- 元数据治理页面不再只有列表动作，能完成 schema、review、backfill 三条工作流。
- correct 表单能提交字段修正。
- backfill job 状态变化后自动刷新。

## P1 模块九：管理侧记忆治理

### 后端接口

- `GET /memories`
- `GET /memories/{layer}/{memoryId}`
- `DELETE /memories/{layer}/{memoryId}`
- `GET /memories/quality-snapshots`
- `GET /memories/conflicts`
- `POST /memories/conflicts/{conflictId}/resolve`
- `GET /memories/profile-facts`
- `GET /memories/corrections`
- `GET /memories/operations`
- `GET /memories/outbox`
- `GET /memories/health`
- `GET /memories/readiness`
- `GET /memories/policy-config`
- `POST /memories/policy-config`
- `POST /memories/governance/run`
- `POST /memories/governance/decay`
- `POST /memories/governance/quality`
- `POST /memories/maintenance/run`
- `GET /memories/maintenance-runs`
- `GET /memories/maintenance-runs/aggregate`
- `GET /memory-review/items`
- `GET /memory-review/pending-summary`
- `GET /memory-review/items/{item-id}`
- `GET /memory-review/items/{item-id}/feedback-samples`
- `POST /memory-review/items/{item-id}/approve`
- `POST /memory-review/items/{item-id}/modify`
- `POST /memory-review/items/{item-id}/reject`
- `GET /memories/traces`

### 前端文件

- 新建 `frontend/src/services/memoryGovernanceService.ts`
- 新建 `frontend/src/pages/admin/memory-governance/MemoryGovernancePage.tsx`
- 新建 `frontend/src/pages/admin/memory-governance/components/MemoryReviewQueue.tsx`
- 新建 `frontend/src/pages/admin/memory-governance/components/MemoryConflictPanel.tsx`
- 新建 `frontend/src/pages/admin/memory-governance/components/MemoryQualityPanel.tsx`
- 新建 `frontend/src/pages/admin/memory-governance/components/MemoryMaintenancePanel.tsx`
- 新建 `frontend/src/pages/admin/memory-governance/components/MemoryTracePanel.tsx`

### 页面能力

- 记忆检索：按 userId、tenantId、layer、keyword、quality 筛选。
- Review 队列：approve、modify、reject。
- 冲突处理：查看冲突双方，选择保留、合并或废弃，提交 resolve。
- 质量快照：展示质量趋势、低质量项、治理建议。
- 维护任务：触发 maintenance、查看 runs、aggregate。
- 策略配置：读取和保存 policy config。
- Trace：按 memoryId、runId、userId 查询。

### 验收标准

- 管理员能处理记忆 review 和 conflict。
- 维护任务触发后能看到运行记录。
- 策略配置保存失败时保留用户输入并展示错误。

## P2 模块十：审计、成本、插件、沙箱、文档维护

### 后端接口

- `GET /api/audit-events`
- `GET /api/audit-events/{auditId}`
- `POST /api/cost-usage-records`
- `GET /api/cost-usage:aggregate`
- `POST /api/sandbox/sessions`
- `POST /api/sandbox/sessions/{sessionId}/execute`
- `POST /api/sandbox/sessions/{sessionId}/close`
- `GET /api/sandbox/sessions/{sessionId}/artifacts`
- `POST /knowledge-base/docs/{doc-id}/refresh`
- `POST /knowledge-base/docs/refresh-due`
- `POST /knowledge-base/docs/{doc-id}/keyword-index/rebuild`
- `POST /knowledge-base/{kb-id}/keyword-index/rebuild`

### 前端文件

- 新建 `frontend/src/services/auditCostService.ts`
- 新建 `frontend/src/services/sandboxService.ts`
- 扩展 `frontend/src/services/knowledgeService.ts`
- 新建 `frontend/src/pages/admin/audit/AuditEventPage.tsx`
- 新建 `frontend/src/pages/admin/cost/CostAnalyticsPage.tsx`
- 新建 `frontend/src/pages/admin/sandbox/SandboxPage.tsx`
- 修改 `frontend/src/pages/admin/knowledge/KnowledgeDocumentsPage.tsx`
- 修改 `frontend/src/pages/admin/knowledge/KnowledgeChunksPage.tsx`

### 页面能力

- 审计日志按 actor、eventType、agentId、runId、tenant、时间范围筛选。
- 成本页按 tenant、agent、run、model、tool、时间范围聚合。
- 沙箱页支持创建 session、执行 payload、查看输出与 artifacts，用于复现工具调用。
- 文档详情支持立即刷新、查看刷新状态、重建单文档关键词索引。
- 知识库页支持刷新到期文档、重建全库关键词索引。

### 验收标准

- 审计详情能展示完整 event payload。
- 成本聚合图表和表格数值一致。
- 沙箱 session 可关闭，关闭后不能继续 execute。
- 文档刷新和索引重建动作有确认与结果反馈。

## 分阶段交付计划

### Phase 0：前端壳层与 gate 对齐

范围：

- 扩展 `ADVANCED_ADMIN_FEATURES`。
- 新增统一 `FeatureUnavailableState`。
- 重组 `AdminLayout` 菜单但保留旧路由。
- 新增基础 service 类型与空页面骨架。

验收：

- 旧页面可访问。
- 新菜单在 enterprise + advanced admin 下可见。
- gate 关闭时显示统一状态。

### Phase 1：Agent 主链路闭环

范围：

- Agent 生命周期。
- 工具目录与绑定。
- 审批中心。
- Agent Inspector 补全基础操作。

验收：

- 管理员能完成创建 Agent、绑定工具、发布、发起 run、处理审批、查看 run 的核心闭环。

### Phase 2：RAG 质量闭环

范围：

- RAG 评测数据集。
- 单次评测、批量评测、策略对比。
- 策略模板。
- 版本质量对比。
- 文档刷新与关键词索引维护。

验收：

- 管理员能用数据集评估知识库质量，并对比策略或版本变化。

### Phase 3：企业治理闭环

范围：

- 资源 ACL。
- 访问决策。
- Secret。
- Quota。
- OpenAPI 连接器。

验收：

- 管理员能安全接入外部系统，并用 ACL、密钥、配额控制 Agent 使用边界。

### Phase 4：深水区治理与可观测

范围：

- 记忆治理。
- 元数据治理补全。
- 审计日志。
- 成本明细。
- 沙箱与插件中心。

验收：

- 长期记忆、元数据、审计、成本、沙箱复现具备管理员工作台。

## 实施任务拆分

### Task 1：Feature Gate 与菜单骨架

Files：

- 修改 `frontend/src/config/productMode.ts`
- 修改 `frontend/src/router.tsx`
- 修改 `frontend/src/pages/admin/AdminLayout.tsx`
- 新建 `frontend/src/components/common/FeatureUnavailableState.tsx`

Why：先让前端能表达后端企业能力，避免后续页面只能靠一个大开关。

Verification：

```powershell
cd frontend
npm run lint
npm test
npm run build
```

验收点：

- 旧路由仍可访问。
- 新 feature enum 编译通过。
- 关闭 feature 时路由显示统一不可用状态。

### Task 2：Agent 生命周期页面

Files：

- 新建 `frontend/src/services/agentDefinitionService.ts`
- 新建 `frontend/src/services/agentFactoryService.ts`
- 新建 `frontend/src/pages/admin/agents/*`
- 修改 `frontend/src/router.tsx`
- 修改 `frontend/src/pages/admin/AdminLayout.tsx`

Why：补齐 Agent 从创建到发布的主工作流。

Verification：

```powershell
cd frontend
npm run lint
npm test
npm run build
```

验收点：

- 创建、编辑草稿、校验、发布、禁用、回滚都有 UI。
- 错误响应不会造成白屏。

### Task 3：工具目录、绑定与审批中心

Files：

- 新建 `frontend/src/services/toolCatalogService.ts`
- 扩展 `frontend/src/services/approvalService.ts`
- 新建 `frontend/src/pages/admin/tools/*`
- 新建 `frontend/src/pages/admin/approvals/*`
- 修改 Agent 详情页和 Inspector 跳转。

Why：让高风险工具调用具备配置、绑定、审批和审计闭环。

Verification：

```powershell
cd frontend
npm run lint
npm test
npm run build
```

验收点：

- 工具启停、绑定保存、审批 approve/reject/modify 可用。
- modify JSON 校验可阻止非法提交。

### Task 4：RAG 评测工作台

Files：

- 新建 `frontend/src/services/ragEvaluationService.ts`
- 新建 `frontend/src/pages/admin/rag-evaluation/*`
- 扩展知识库页面的评测入口。

Why：把知识库质量从人工体感变成可评测、可对比、可回归。

Verification：

```powershell
cd frontend
npm run lint
npm test
npm run build
```

验收点：

- 数据集 CRUD、evaluate、compare、runs/comparisons 展示可用。
- 策略模板 CRUD 可用。

### Task 5：安全治理与连接器

Files：

- 新建 `frontend/src/services/securityGovernanceService.ts`
- 新建 `frontend/src/services/openApiConnectorService.ts`
- 新建 `frontend/src/pages/admin/security/*`
- 新建 `frontend/src/pages/admin/integrations/*`

Why：支撑企业环境中的外部系统接入、资源权限、密钥和配额治理。

Verification：

```powershell
cd frontend
npm run lint
npm test
npm run build
```

验收点：

- ACL dry-run/import 可用。
- secret 不泄露明文。
- OpenAPI operation 可启停并绑定 credential。

### Task 6：记忆治理与元数据治理补全

Files：

- 新建 `frontend/src/services/memoryGovernanceService.ts`
- 扩展 `frontend/src/services/metadataGovernanceService.ts`
- 新建 `frontend/src/pages/admin/memory-governance/*`
- 扩展 `frontend/src/pages/admin/metadata-governance/*`

Why：补齐长期记忆和元数据抽取的治理工作台。

Verification：

```powershell
cd frontend
npm run lint
npm test
npm run build
```

验收点：

- Memory review/conflict/maintenance 可操作。
- Metadata schema/review/backfill/quality compare 可操作。

### Task 7：审计、成本、沙箱与文档维护

Files：

- 新建 `frontend/src/services/auditCostService.ts`
- 新建 `frontend/src/services/sandboxService.ts`
- 扩展 `frontend/src/services/knowledgeService.ts`
- 新建 `frontend/src/pages/admin/audit/*`
- 新建 `frontend/src/pages/admin/cost/*`
- 新建 `frontend/src/pages/admin/sandbox/*`
- 修改知识库文档页面。

Why：提供上线后的追踪、复现、成本分析和文档维护能力。

Verification：

```powershell
cd frontend
npm run lint
npm test
npm run build
```

验收点：

- 审计和成本可筛选。
- 沙箱 session 生命周期完整。
- 文档刷新和关键词索引重建可触发。

## 测试策略

单元与组件测试：

- service：mock `api`，验证 endpoint、method、params、body。
- 列表页：验证筛选、分页、空状态、错误状态。
- 动作弹窗：验证确认、loading、防重复提交、错误提示。
- JSON 编辑器：验证非法 JSON 阻止提交。

集成手工测试：

- 以管理员身份访问所有新增路由。
- 使用后端本地环境或 mock API 覆盖 200、400、403、404、501。
- 检查 feature 关闭、无权限、接口未注册三类降级状态。

回归测试：

- 聊天页、知识库、入库、意图、Trace、模型配置、用户管理旧入口不回退。
- `AiInfraConsolePage` 在迁移期仍能作为概览页访问。

## 风险与缓解

| 风险 | 影响 | 缓解 |
| --- | --- | --- |
| 后端接口字段未稳定 | 前端类型频繁变动 | service 使用最小字段 + raw payload 展示，关键字段与后端补契约 |
| 单页过大 | 维护成本高 | 按领域拆页面，复杂详情用子组件和抽屉 |
| 高风险动作误操作 | 生产事故 | 所有 publish/rollback/disable/approve/import/enable 使用确认弹窗和备注 |
| feature gate 不对齐 | 菜单空白或误导 | 先扩展前端枚举，再推动后端能力状态接口 |
| 权限语义不清 | 安全页面误导管理员 | 明确展示 API 返回的决策原因，不在前端推断权限结论 |
| RAG 评测结果复杂 | 用户看不懂 | 指标 + 失败样本 + 原始结果三层展示 |

## 迁移与退休策略

- `AiInfraConsolePage` 短期保留为总览页，Agent、审批、工具、评测迁移到独立页面后，逐步减少其操作能力，只保留摘要和跳转。
- `aiInfraService.ts` 中已经封装的 agents、approvals、tools、cost、readiness、rollout 方法，在新 service 稳定后迁移调用方；旧方法保留一轮发布周期。
- `AgentConsolePage` 如果是 AI Infra 的包装页，应在新信息架构落地后明确定位为总览或删除重复入口。
- `MemoryCenterPage` 保持用户侧“我的记忆”，新 `MemoryGovernancePage` 只面向管理员治理，不合并两者。

## 交付定义

一个模块可以标记为完成，需要满足：

1. 路由、菜单、feature gate、空状态完整。
2. service 覆盖模块内核心 API。
3. 列表、详情、主要动作、错误状态可用。
4. 关键动作有确认、备注或风险提示。
5. 组件测试覆盖 service 调用和核心交互。
6. `npm run lint`、`npm test`、`npm run build` 通过。
7. 文档中对应模块的验收标准逐条满足。

## 建议优先级

第一批开发优先只做 P0：

1. Feature Gate 与菜单骨架。
2. Agent 生命周期。
3. 工具目录与绑定。
4. 审批中心。
5. RAG 评测工作台。

这五项能最大程度把后端已有企业 Agent/RAG 能力露出来，并且形成“配置 Agent -> 绑定工具 -> 发布 -> 运行 -> 审批 -> 评测质量”的产品闭环。P1/P2 在 P0 稳定后分批推进，避免后台一次性膨胀成大量半成品入口。

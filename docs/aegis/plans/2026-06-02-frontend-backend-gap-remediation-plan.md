# Seahorse Agent 前后端功能缺口修复计划

日期：2026-06-02

## Goal

把当前前后端功能对齐审查中发现的缺口整理成可执行、可验收、可分批修复的开发文档。目标不是重新设计整个后台，而是让后续修复时能按优先级处理真正影响用户使用的缺口：功能开关、接口契约、乱码、模型配置源、知识库运维、ES/Milvus 运行态、Agent 工厂、元数据治理以及 AI Infra 聚合页的能力降级。

## Architecture

当前项目采用 Spring Boot Web adapter 暴露 REST API，前端采用 React + Vite + TypeScript + React Router + Zustand。后台能力由后端 `AdvancedFeatureGate` 和 `/api/features` 作为运行时来源，前端 `featureStore` 在启动时加载并驱动路由、菜单和不可用状态。

本计划要求继续保持“后端能力为准、前端按能力降级”的架构边界：

- 后端 controller 的 feature gate 是权限和能力真相。
- 前端路由、菜单、页面 tab 和按钮必须使用同一套后端能力状态。
- 前端 service 不直接假设接口存在，所有核心请求必须进入契约测试。
- 对 ES、Milvus、pgvector、AI provider 等运行态能力，前端只展示后端确认过的实际状态。

## Tech Stack

- Backend: Java 17, Spring Boot, Sa-Token, Maven, PostgreSQL, Elasticsearch, Milvus, pgvector
- Frontend: React 18, TypeScript, Vite, React Router, Zustand, Axios, Vitest, Testing Library
- Deployment: Docker Compose full deployment, `.env.full.example`, `docker-compose.full.yml`

## Baseline/Authority Refs

- `frontend/src/router.tsx`
- `frontend/src/pages/admin/AdminLayout.tsx`
- `frontend/src/config/productMode.ts`
- `frontend/src/stores/featureStore.ts`
- `frontend/src/services/backendEndpointManifest.ts`
- `frontend/src/services/frontendCapabilityContracts.test.ts`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeature.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/AdvancedFeatureGate.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseFeatureController.java`
- `docs/superpowers/plans/2026-05-31-frontend-backend-alignment.md`
- `docs/aegis/plans/2026-05-30-frontend-missing-capabilities-development-plan.md`
- `docs/deployment/enterprise-mode.md`

## Compatibility Boundary

1. 保持现有路由可访问，不做批量删除：`/admin/dashboard`、`/admin/knowledge`、`/admin/agents`、`/admin/agent-runs`、`/admin/agent-inspector`、`/admin/ai-infra`、`/admin/model-config`、`/admin/settings` 等。
2. 保持后端已有 legacy path 和 `/api/**` alias 兼容，不在本轮移除旧路径。
3. 不提交真实密钥，不把 `.env` 中的敏感值写入文档或测试。
4. 不把后端不可用能力伪造成可用；前端必须显示明确的不可用或未配置状态。
5. 不把 ES/Milvus 是否启用写死在前端，必须以后端健康/配置/adapter 状态为准。
6. 文案修复必须使用 UTF-8，避免继续产生乱码。

## Verification

后续执行修复时，每个批次至少运行：

```powershell
cd frontend
npm test
npm run build
```

涉及后端 controller、feature gate、契约或配置时运行：

```powershell
.\mvnw.cmd -pl seahorse-agent-adapter-web -am -DskipTests test-compile
.\mvnw.cmd -pl seahorse-agent-tests -am -DskipTests test-compile
```

涉及 JDBC、DDL、ID 类型或 init SQL 时运行：

```powershell
.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am -DskipTests test-compile
```

涉及全量部署时额外验证：

```powershell
docker compose -f docker-compose.full.yml config
docker compose -f docker-compose.full.yml up -d --build backend frontend
```

并在浏览器检查：

- `/api/features`
- `/admin/dashboard`
- `/admin/model-config`
- `/admin/settings`
- `/admin/knowledge`
- `/admin/agent-runs`
- `/admin/ai-infra`

## Current Alignment Summary

当前不是“前端完全没有承接后端”的状态。核心页面、服务和接口已经大量补齐，静态比对显示前端实际请求大约 165 个，后端 controller 映射大约 290 个。真正明显的 404 型缺口不多，但仍存在产品闭环缺口：

- 功能开关和后端 controller gate 不完全一致。
- 一些后端接口有 service，但页面没有入口。
- 一些页面能打开，但配置源或运行态数据没有打通。
- 契约测试覆盖过薄，`backendEndpointManifest.ts` 不能完全代表真实后端映射。
- 前端多处中文乱码，影响实际使用。

### 代码扫描统计（2026-06-02）

- **后端 Controller 总数**：67 个（基于 `seahorse-agent-adapter-web/.../adapters/web` 下 controller 类粗略统计）
- **后端端点映射数**：~269 个（基于 `@*Mapping` 注解粗略统计，待自动抽取脚本校准）
- **前端 Service 文件数**：42 个
- **backendEndpointManifest.ts 当前规模**：1,114 行（文件行数，不等同于真实端点数）
- **前端已验证调用端点数**：~165 个（根据契约测试和 service 调用估算）
- **Feature gate 不一致数**：3 类问题已确认，影响至少 5 个页面/Tab 入口
- **需要评估前端闭环的候选能力数**：15-20 个（P1/P2 优先级，需以自动抽取脚本复核）

## Priority Matrix

| 优先级 | 缺口 | 影响 | 修复目标 |
| --- | --- | --- | --- |
| P0 | 前端中文乱码 | 用户无法理解菜单、错误、设置说明 | 全量恢复 UTF-8 中文文案 |
| P0 | Feature gate 与后端不一致 | 页面可见但接口报禁用 | 路由、菜单、tab、按钮按真实后端 feature 控制 |
| P0 | 接口契约清单不可靠 | 无法提前发现前端请求不存在接口 | 自动抽取后端映射并覆盖前端请求测试 |
| P0 | 模型配置源不统一 | 模型配置页面保存后不一定真实生效 | DB 配置、环境配置、`/rag/settings`、AI adapter 形成单一事实源 |
| P1 | 知识库运维入口缺失 | 文档刷新、关键词索引重建不可操作 | 在知识库页面补齐刷新、重建、chunk logs |
| P1 | ES/Milvus 运行态不可见 | 全量部署后无法判断是否真实使用 | 增加搜索/向量健康、索引状态和 adapter 状态展示 |
| P1 | Agent 工厂模板详情缺口 | 前端 service 有请求但后端无详情接口 | 补后端详情接口或删除前端未使用 service |
| P1 | Metadata Governance service 未完全收口 | 页面直接拼动态路径，契约难覆盖 | 所有元数据治理请求集中到 service |
| P1 | AI Infra 聚合页能力降级不足 | 一个 feature 控制多类后端能力 | 按 tab/操作绑定真实 feature |
| P2 | 外部 GitHub API 直接由前端调用 | 后台依赖外部接口，违反系统边界 | 改为后端 about/repository stats 或移除 |
| P2 | 后端未被前端承接的能力较多 | 能力存在但不可被用户操作 | 按业务价值逐步补入口 |

## P0-1 修复前端中文乱码

### Evidence

当前多处前端文件出现编码错乱，用户可见文本不可读：

- `frontend/src/pages/admin/AdminLayout.tsx`
- `frontend/src/router.tsx`
- `frontend/src/config/productMode.ts`
- `frontend/src/stores/featureStore.ts`
- `frontend/src/services/api.ts`
- `frontend/src/pages/admin/settings/SystemSettingsPage.tsx`
- `frontend/src/pages/admin/settings/ModelConfigPage.tsx`
- `frontend/src/pages/admin/metadata-governance/components/MetadataQuarantinePanel.tsx`
- `frontend/src/services/agentFactoryService.ts`

### Required Fix

1. 用 UTF-8 重新写入所有用户可见中文文案。
2. 修复 JSX 中已经破损的标签、字符串和说明文本。
3. 对常见错误提示建立统一中文文案，例如：
   - 请求失败
   - 网络错误，请检查连接
   - 登录已过期，请重新登录
   - 功能未启用
   - 暂无可展示的数据
4. 禁止继续提交乱码文案。

### Acceptance

- 菜单、面包屑、FeatureUnavailableState、系统设置、模型配置、元数据隔离面板均显示正常中文。
- `npm run build` 不因破损 JSX 或字符串失败。
- 浏览器打开 `/admin/settings`、`/admin/model-config`、`/admin/metadata-governance` 时无乱码。

### Suggested Verification

```powershell
cd frontend
npm test
npm run build
```

手工检查：

- `/admin/dashboard`
- `/admin/settings`
- `/admin/model-config`
- `/admin/metadata-governance`

## P0-2 对齐 Feature Gate 与后端真实权限

### Evidence

前端已经从 `/api/features` 加载能力，但部分页面用错 feature：

- **审批中心**：前端路由 `router.tsx:144` 使用 `AGENT_DEFINITION_MANAGEMENT`，但后端 `SeahorseApprovalController.page():69` 实际要求 `AGENT_RUN_MANAGEMENT`。
- **AI Infra Console**：`AiInfraConsolePage` 聚合调用 agents、approvals、tools、cost、feedback、readiness、rollout，但路由只用 `LOCAL_AGENT` 控制。
- Agent 创建和模板能力同时依赖 Agent Definition 与 Agent Factory，前端没有清晰区分。

### Feature Gate 不一致清单

| 页面/功能 | 前端使用 Feature | 后端实际要求 | 后端 Controller | 影响 |
|---------|-----------------|-------------|---------------|------|
| 审批中心路由 | AGENT_DEFINITION_MANAGEMENT | AGENT_RUN_MANAGEMENT | SeahorseApprovalController | ❌ 路由 guard 失效 |
| AI Infra - approvals tab | 无（由 LOCAL_AGENT 控制整页） | AGENT_RUN_MANAGEMENT | SeahorseApprovalController | ❌ Tab 无法单独禁用 |
| AI Infra - agents tab | 无（由 LOCAL_AGENT 控制整页） | AGENT_DEFINITION_MANAGEMENT | SeahorseAgentDefinitionController | ❌ Tab 无法单独禁用 |
| AI Infra - tools tab | 无（由 LOCAL_AGENT 控制整页） | TOOL_CATALOG_MANAGEMENT | SeahorseToolCatalogController | ❌ Tab 无法单独禁用 |
| AI Infra - cost tab | 无（由 LOCAL_AGENT 控制整页） | COST_ANALYTICS | SeahorseCostUsageController | ❌ Tab 无法单独禁用 |

### Required Fix

1. 建立前端页面/操作到后端 `AdvancedFeature` 的映射表。
2. 修正审批中心：
   - route 使用 `AGENT_RUN_MANAGEMENT` 或单独引入审批管理 feature。
   - 页面内部在调用审批 API 前检查同一 feature。
3. 修正 Agent Factory：
   - Agent 列表/详情/草稿属于 `AGENT_DEFINITION_MANAGEMENT`。
   - 模板、从模板创建、validate、publish checks、rollback 属于 `AGENT_FACTORY_MANAGEMENT`。
4. 修正 AI Infra Console（详见 P1-5 Tab 级能力降级）：
   - overview 可用 `LOCAL_AGENT`。
   - approvals 使用 `AGENT_RUN_MANAGEMENT`。
   - agents 使用 `AGENT_DEFINITION_MANAGEMENT`。
   - tools 使用 `TOOL_CATALOG_MANAGEMENT`。
   - cost 使用 `COST_ANALYTICS`。
   - readiness 使用 `ENTERPRISE_PILOT_READINESS`。
   - rollout 使用 `AGENT_ROLLOUT_MANAGEMENT`。
5. `AdminLayout` 菜单显示和 `router.tsx` 路由 guard 必须一致。

### Acceptance

- 后端关闭某个 feature 时，前端对应菜单、tab 或按钮不可用，且不发起受保护请求。
- 直接访问受保护路由时显示统一不可用状态，而不是接口报错。
- full deployment 中所有启用 feature 的页面可进入并可正常请求。

### Suggested Verification

```powershell
cd frontend
npm test -- featureService frontendCapabilityContracts
npm run build
```

后端编译：

```powershell
.\mvnw.cmd -pl seahorse-agent-adapter-web -am -DskipTests test-compile
```

## P0-3 重建前后端接口契约测试

### Evidence

当前 `frontend/src/services/backendEndpointManifest.ts` 不是可靠的后端真相：

- 真实存在的 `/admin/ai-config` 没有被 manifest 完整识别。
- 真实存在的 `/admin/dashboard/overview`、`/admin/dashboard/performance`、`/admin/dashboard/trends` 没有被 manifest 完整识别。
- 真实存在的 `/api/agent-runs` 列表接口没有被 manifest 完整识别。
- **前端 `agentFactoryService.ts:41-44` 定义 `getAgentTemplate(templateId) -> GET /api/agent-templates/{templateId}`，但后端无对应 controller，且该方法未被任何页面调用（已验证）。**
- 页面中存在动态 action 拼接，例如 `/metadata-quarantine/items/${itemId}/${action}`，契约测试难以精确覆盖。

### Required Fix

1. 新增或修复自动抽取脚本，从 `seahorse-agent-adapter-web/.../*Controller.java` 抽取：
   - `@RequestMapping`
   - `@GetMapping`
   - `@PostMapping`
   - `@PutMapping`
   - `@DeleteMapping`
   - `@PatchMapping`
2. 抽取时支持：
   - 类级 `@RequestMapping`
   - 数组路径
   - 空 path
   - `{path-var}` 统一归一为 `{}`
3. 生成新的 `backendEndpointManifest.ts`。
4. 扩展 `frontendCapabilityContracts.test.ts`：
   - 扫描 service 和关键页面的 `api.get/post/put/delete/patch`。
   - 常量路径要能展开，例如 `AGENT_RUNS_API_BASE = "/api/agent-runs"`。
   - 动态 action 必须拆成明确 service 方法。
5. **对 `getAgentTemplate` 缺口采用方案 B（推荐）**：
   - **删除前端未使用的 `getAgentTemplate` 方法**（已确认无调用）。
   - 契约测试确认不再请求该路径。
   - Agent 创建页只使用列表返回的完整模板信息。
   - 如果未来确需单模板详情，再补后端接口。

### Acceptance

- 契约测试能发现前端请求不存在的后端接口。
- `backendEndpointManifest.ts` 与后端 controller 真实映射一致。
- 不再把 manifest 缺漏误判为后端接口缺失。

### Suggested Verification

```powershell
cd frontend
npm test -- frontendCapabilityContracts
```

```powershell
.\mvnw.cmd -pl seahorse-agent-adapter-web -am -DskipTests test-compile
```

## P0-4 统一模型配置事实源

### Evidence

当前存在至少两套模型配置入口：

- `ModelConfigPage` 读写 `/admin/ai-config`。
- `SystemSettingsPage` 读取 `/rag/settings`。
- `SeahorseRagSettingsController` 从 `seahorse-agent.adapters.ai` 环境配置绑定 providers；没有 providers 时返回空。
- `/admin/ai-config` 写入的 DB 配置不一定成为 AI adapter 的运行时事实源。

这会导致用户在模型配置页保存后，系统设置页仍显示 provider 为空，实际模型调用也不一定使用新值。

### Required Fix

1. 明确模型配置单一事实源：
   - 方案 A：DB 配置为主，环境变量只作初始值。
   - 方案 B：环境变量为主，模型配置页只读并明确说明不可在线修改。
2. 如果选择 DB 为主：
   - `/rag/settings` 必须读取 DB 配置或合并 DB 配置。
   - AI adapter 必须使用同一配置源。
   - `/admin/ai-config` 保存后应触发运行时刷新或明确需要重启。
3. 如果选择环境变量为主：
   - `ModelConfigPage` 改成只读。
   - 页面明确显示“由部署环境配置，不能在页面修改”。
   - 移除保存按钮或只允许保存非敏感展示偏好。
4. Provider 数据缺失时显示明确空状态：
   - 未配置模型服务提供方。
   - 请在 `.env.full.example` 或部署环境中配置。
5. API key 显示必须脱敏，不能把真实密钥返回给前端。

### Acceptance

- `/admin/model-config` 和 `/admin/settings` 显示同一套模型配置事实。
- 保存模型配置后，用户能知道是否立即生效。
- provider 为空时有清晰说明，而不是像页面坏掉。
- 前端和接口响应不泄露真实 API key。

### Suggested Verification

```powershell
cd frontend
npm run build
```

```powershell
.\mvnw.cmd -pl seahorse-agent-adapter-web -am -DskipTests test-compile
```

手工检查：

- `/admin/model-config`
- `/admin/settings`
- `/rag/settings`
- `/admin/ai-config`

## P1-1 补齐知识库运维入口

### Evidence

后端已有运维接口：

- `POST /knowledge-base/docs/{doc-id}/refresh`
- `POST /knowledge-base/docs/refresh-due`
- `POST /knowledge-base/docs/{doc-id}/keyword-index/rebuild`
- `POST /knowledge-base/{kb-id}/keyword-index/rebuild`
- `GET /knowledge-base/docs/{doc-id}/chunk-logs`

**前端 `knowledgeService.ts:332-355` 已有完整方法实现**：

- `refreshDocument()` ✓
- `refreshDueDocuments()` ✓
- `rebuildDocumentKeywordIndex()` ✓
- `rebuildKbKeywordIndex()` ✓
- `getDocumentChunkLogs()` ✓

**缺口在于页面集成不完整**。

### Required Fix

1. 在 `KnowledgeDocumentsPage` 增加文档行操作：
   - 刷新文档（调用 `knowledgeService.refreshDocument`）
   - 重建文档关键词索引（调用 `knowledgeService.rebuildDocumentKeywordIndex`）
   - 查看 chunk logs（调用 `knowledgeService.getDocumentChunkLogs`）
2. 在知识库文档页增加页面级操作：
   - 刷新到期文档（调用 `knowledgeService.refreshDueDocuments`）
   - 重建当前知识库关键词索引（调用 `knowledgeService.rebuildKbKeywordIndex`）
3. 在 `KnowledgeChunksPage` 或文档详情区域展示 chunk logs：
   - 状态
   - pipeline
   - 分块数
   - 耗时
   - 错误信息
   - 开始/结束时间
4. 高风险操作加确认弹窗。
5. 所有操作完成后刷新当前列表并显示 toast。

### Acceptance

- 管理员可以从页面触发单文档刷新。
- 管理员可以从页面触发到期文档刷新。
- 管理员可以从页面重建 doc/kb 关键词索引。
- 管理员可以查看 chunk logs 并定位失败原因。

### Suggested Verification

```powershell
cd frontend
npm run build
```

手工检查：

- `/admin/knowledge`
- `/admin/knowledge/:kbId`
- `/admin/knowledge/:kbId/docs/:docId`

## P1-2 展示 ES/Milvus/pgvector 运行态

### Evidence

项目存在以下模块和部署项：

- `seahorse-agent-adapter-search-elasticsearch`
- `seahorse-agent-adapter-vector-milvus`
- `seahorse-agent-adapter-vector-pgvector`
- `seahorse-agent-adapter-vector-noop`
- `resources/docker/milvus-stack-*.compose.yaml`
- `docker-compose.full.yml`
- init SQL 中存在 pgvector 表和索引。

但前端没有清楚展示当前实际使用的是 ES、Milvus、pgvector 还是 noop，也没有展示索引/collection 健康和同步状态。

**后端现状**：

- `SeahorseSreHealthController` 提供 `GET /api/sre/health`
- 前端 `aiInfraService.ts:121` 已定义 `getAiInfraSreHealth()`
- `SreHealthPanel.tsx` 和 `AiInfraConsolePage.tsx:374` 已调用

**缺口**：

- `/admin/dashboard` 上缺少运行态展示面板集成
- 知识库页面未展示当前 KB 使用的 collection、embedding model、dimension
- SRE Health 返回格式是否包含搜索库、向量库、模型服务等完整信息需验证

### Required Fix

1. 后端增强或验证 SRE health 运行态接口返回内容：
   - 当前 search adapter 类型。
   - 当前 vector adapter 类型。
   - ES 连接状态、索引名、文档数、最近错误。
   - Milvus 连接状态、collection、dimension、metric、索引状态。
   - pgvector 状态、表/索引状态。
   - noop 状态必须明确标记为不可用于生产检索。
2. 前端在 Dashboard 或 AI Infra Console 增加运行态面板：
   - 搜索服务
   - 向量数据库
   - 模型服务
   - 对象存储
3. 在知识库页面展示当前 KB 使用的 collection、embedding model、dimension。
4. 将索引重建操作和运行态状态关联展示。

### Acceptance

- 全量部署后，用户能在后台看到 ES/Milvus 是否连接正常。
- 如果实际使用 noop，页面明确提示“未启用真实向量库/搜索服务”。
- 知识库索引异常时能看到错误和最近更新时间。

### Suggested Verification

```powershell
.\mvnw.cmd -pl seahorse-agent-adapter-web,seahorse-agent-adapter-vector-milvus,seahorse-agent-adapter-search-elasticsearch -am -DskipTests test-compile
```

```powershell
cd frontend
npm run build
```

手工检查：

- `/admin/dashboard`
- `/admin/ai-infra`
- `/admin/knowledge`

## P1-3 修复 Agent 工厂模板详情缺口

### Evidence

前端存在：

- `listAgentTemplates() -> GET /api/agent-templates`
- `getAgentTemplate(templateId) -> GET /api/agent-templates/{templateId}`（定义于 `agentFactoryService.ts:41-44`）

后端当前存在：

- `GET /api/agent-templates`
- `POST /api/agents/from-template`
- `POST /api/agents/{agentId}/validate`
- `GET /api/agents/{agentId}/publish-checks/latest`
- `POST /api/agents/{agentId}/versions/{versionId}/rollback`
- `GET /api/agent-catalog`

但没有 `GET /api/agent-templates/{templateId}`。

**验证结果**：`getAgentTemplate()` 方法未被任何页面调用（grep 搜索结果为空）。

### Required Fix

**推荐方案 B：删除前端未使用详情方法**

- 删除 `agentFactoryService.ts` 中的 `getAgentTemplate(templateId)` 方法。
- 契约测试确认不再请求该路径。
- Agent 创建页只使用列表返回的完整模板信息。
- 如果未来确需单模板详情，再补后端接口。

备选方案 A（仅在未来确需时考虑）：

- 在 `SeahorseAgentFactoryController` 增加 `GET /api/agent-templates/{templateId}`。
- 在 inbound port 和 kernel service 中补查询方法。
- JDBC 或内存模板来源支持按 ID 查询。
- 前端保留 `getAgentTemplate`。

### Acceptance

- 前端不再存在请求不存在后端接口的方法。
- Agent 创建页模板选择流程可用。
- feature gate 同时尊重 `AGENT_DEFINITION_MANAGEMENT` 和 `AGENT_FACTORY_MANAGEMENT`。
- 契约测试通过，不再请求 `/api/agent-templates/{templateId}`。

### Suggested Verification

```powershell
cd frontend
npm test -- frontendCapabilityContracts
npm run build
```

验证删除后不再有调用：

```powershell
cd frontend
rg "getAgentTemplate" src/
```

## P1-4 收口 Metadata Governance 请求

### Evidence

`MetadataQuarantinePanel.tsx:33` 可能直接在页面中拼接动态 action：

- `/metadata-quarantine/items/${itemId}/${action}`

同时 `metadataGovernanceService.ts:101-106` 已有明确方法：

- `resolveMetadataQuarantineItem`
- `retryMetadataQuarantineItem`

页面直接拼路径会让契约测试难以覆盖，也会让字段类型、错误处理、toast 行为分散。

**需要验证**：`MetadataQuarantinePanel.tsx` 最新代码是否已改用 service 方法，或仍存在直接 api 调用。

### Required Fix

1. **首先验证**：检查 `MetadataQuarantinePanel.tsx` 是否仍直接调用 `api.post(/metadata-quarantine/...)`。
2. 如果仍直接调用：页面组件不得直接调用元数据治理 API。
3. 所有 metadata governance API 统一进入 `metadataGovernanceService.ts`。
4. 对 resolve/retry 使用明确方法，不使用动态 action path。
5. 为元数据 service 增加契约测试。
6. 审查以下组件是否仍直接调用 `api`：
   - `MetadataDictionaryPanel`
   - `MetadataExtractionResultDrawer`
   - `MetadataQuarantinePanel`
   - `MetadataBackfillPanel`
   - `MetadataSchemaManager`
   - `MetadataReviewDetailDrawer`

### Acceptance

- 元数据治理页面不再直接拼 API 路径。
- 契约测试能覆盖 resolve/retry/review/backfill/schema/dictionary。
- 页面行为不变，错误提示更一致。

### Suggested Verification

```powershell
cd frontend
npm test -- frontendCapabilityContracts
npm run build
```

## P1-5 拆分 AI Infra Console 的能力降级

### Evidence

`AiInfraConsolePage` 聚合多类后端能力，但路由只由 `LOCAL_AGENT` 控制。全量部署所有 feature 打开时不明显，一旦部分 feature 关闭，页面内部某些请求会失败。

### AI Infra Console Tab 级 Feature 定义表

| Tab | 功能 | 应使用 Feature | 对应后端 Controller | 当前状态 |
|-----|------|---------------|-------------------|---------|
| overview | AI 基础设施概览 | LOCAL_AGENT | 多个（聚合） | ✓ 正确 |
| approvals | 审批中心 | AGENT_RUN_MANAGEMENT | SeahorseApprovalController | ❌ 无单独 guard |
| feedback | 评估反馈 | AGENT_EVALUATION | SeahorseMessageFeedbackController / SeahorseEvalCandidateDecisionController | ❌ 无单独 guard |
| agents | Agent 定义管理 | AGENT_DEFINITION_MANAGEMENT | SeahorseAgentDefinitionController | ❌ 无单独 guard |
| tools | 工具目录 | TOOL_CATALOG_MANAGEMENT | SeahorseToolCatalogController | ❌ 无单独 guard |
| operations/readiness | 企业试点就绪度 | ENTERPRISE_PILOT_READINESS | SeahorseEnterprisePilotReadinessController | ❌ 无单独 guard |
| operations/rollout | Agent 发布管理 | AGENT_ROLLOUT_MANAGEMENT | SeahorseAgentRolloutController | ❌ 无单独 guard |
| cost summary | 成本分析 | COST_ANALYTICS | SeahorseCostUsageController | ❌ 无单独 guard |

### Required Fix

1. 给每个 tab 定义 feature（见上表）。
2. tab 不可用时显示局部 `FeatureUnavailableState` 或禁用 tab，不发请求。
3. 实现方式建议：
   - 使用条件渲染而非单一路由 guard
   - 每个 tab 内容组件检查对应 feature
   - 示例：`{featureStore.isEnabled('AGENT_RUN_MANAGEMENT') ? <ApprovalsTab /> : <FeatureUnavailableState feature="AGENT_RUN_MANAGEMENT" />}`
4. 将 AI Infra Console 中的复杂请求拆为更小 hook 或子组件，减少单文件复杂度。
5. 保留 `/admin/ai-infra` 作为聚合入口，不强制删除。

### Acceptance

- 关闭任一子能力时，AI Infra 页面仍可打开。
- 被关闭 tab 不发受保护 API。
- 启用的 tab 正常加载。
- 页面错误状态能区分“功能未启用”和“接口请求失败”。

### Suggested Verification

```powershell
cd frontend
npm run build
```

手工检查：

- `/admin/ai-infra`
- `/admin/approvals`
- `/admin/tools`
- `/admin/cost`

## P2-1 移除前端直接访问外部 GitHub API

### Evidence

前端布局中存在直接请求 GitHub API：

- `frontend/src/pages/admin/AdminLayout.tsx`
- `frontend/src/components/layout/Header.tsx`

这让管理后台依赖外部网络，并把系统信息来源放在前端。

### Required Fix

1. 后端新增 `/api/system/about` 或 `/admin/repository/stats`。
2. 后端统一读取版本、commit、构建时间、仓库信息。
3. 前端只调用本系统 API。
4. 如果该信息不是核心功能，可以移除 GitHub stats 展示。

### Acceptance

- 前端不直接请求 `https://api.github.com/...`。
- 离线或内网部署不会因为 GitHub 请求影响后台体验。

### Suggested Verification

```powershell
rg -n "api.github.com|github.com/.*/seahorse-agent" frontend/src
```

## P2-2 逐步承接后端未被前端使用的能力

### Evidence

后端约 290 个映射，前端实际请求约 165 个。扣除 legacy alias、重复路径、后端内部能力后，仍有不少接口没有入口或只在局部页面使用。

### Candidate Areas

- Context Pack 列表、详情、items 的管理入口。
- Agent artifact 详情、`previewText` 和 download 的统一入口。
- Agent handoff 详情和取消操作的入口完整性。
- Retrieval evaluation comparison/run 详情页。
- Memory feedback samples export。
- Plugin registry/status 写入操作。
- Sandbox artifacts 列表查看；如需下载单个 artifact，应先补后端下载接口。
- Resource ACL import/dry-run 结果可视化。
- Quota decision evaluation 的完整模拟表单。

### Required Fix

1. 按业务价值排序，不一次性铺满所有页面。
2. 优先补“已有后端接口 + 用户需要操作 + 失败会阻塞主流程”的入口。
3. 每补一个入口，补 service、页面、契约测试、浏览器验收。

### Acceptance

- 每个新增入口都有明确后端接口和验收路径。
- 不再新增只展示 mock 或无操作闭环的页面。

## Execution Order

建议分批修复：

1. **P0-1** 前端乱码（需文件编码扫描）。
2. **P0-2** Feature gate 对齐（补充具体代码行号和 Tab 级定义）。
3. **P0-3** 契约测试重建 + 删除 `getAgentTemplate` 未使用方法。
4. **P0-4** 模型配置事实源统一。
5. **P1-1** 知识库运维入口（service 已完成，补页面集成）。
6. **P1-4** 确认 Metadata Governance 组件是否已改用 service。
7. **P1-5** AI Infra Console tab 级能力降级实现。
8. **P1-2** ES/Milvus/pgvector 运行态完整性验证与补充。
9. **P1-3** （已合并到 P0-3：删除 getAgentTemplate）
10. **P2-1/P2-2** 清理外部依赖并逐步承接剩余后端能力。

## Review Checklist

每批修复完成后检查：

- 是否有乱码新增或残留。
- 前端是否请求了后端不存在的接口。
- 页面 feature gate 是否和 controller feature gate 一致。
- disabled feature 是否不会发受保护 API。
- 模型、ES、Milvus、pgvector 这类运行态信息是否来自后端。
- API key 是否全程脱敏。
- 页面是否有空状态、错误状态、loading 状态和成功反馈。
- 是否更新了契约测试。
- **是否存在未被使用的 service 方法**（使用 grep 搜索验证）。
- **Feature gate 不一致检查**：对比前端路由 guard vs 后端 controller 注解。
- **Tab 级 feature guard**：AI Infra Console 等聚合页面是否实现了细粒度能力降级。
- **组件直接调用 API**：元数据治理等组件是否已收口到 service。

## Risks

- 乱码修复可能触及大量 TSX 文件，容易引入 JSX 语法错误。
- Feature gate 修复会改变菜单可见性，需要确认 full deployment 的环境变量已启用所有目标功能。
- 模型配置事实源涉及运行时 adapter，不能只改页面。
- ES/Milvus 运行态需要后端真实健康检查，前端不能单独完成。
- 契约测试自动抽取 controller 映射时要处理 Spring 注解多种写法，否则会继续误报。
- **Tab 级 feature guard 实现需谨慎**：AI Infra Console 在某个 feature 禁用时不能让整个页面崩溃，需要条件渲染。
- **删除未使用方法需充分验证**：grep 搜索可能遗漏动态调用或反射调用，建议运行完整契约测试。
- **Metadata 组件验证需手工审查**：自动化工具可能无法准确判断是否已改用 service。
- **SRE Health 接口返回格式需验证**：现有接口是否包含搜索库、向量库等完整信息，可能需要后端扩展。

## Retirement

修复完成后应淘汰或收缩以下旧逻辑：

- 手写且不可靠的 `backendEndpointManifest.ts` 维护方式，改为脚本生成。
- 页面组件直接拼元数据治理 API 路径。
- AI Infra Console 用单一 `LOCAL_AGENT` 控制所有子能力的逻辑。
- 前端直接访问 GitHub API 的逻辑。
- 模型配置页与系统设置页读取不同事实源的逻辑。
- 所有乱码文案和破损 JSX。
- 前端未被使用的 service 方法（如 `getAgentTemplate`）。

## Appendix: 已核验的后端能力候选清单

以下清单只记录当前后端 controller 中已核验存在的路径，供后续评估前端是否已经形成完整操作闭环。它不是“全部未接入接口”列表；是否需要新增页面入口，还要结合现有 service、页面和契约测试逐项确认。

### A. 确定需要删除或修正的不存在请求

| 优先级 | 前端请求 | HTTP 方法 | 后端状态 | 处理建议 |
|------|------|---------|--------|----------|
| P0 | `/api/agent-templates/{templateId}` | GET | 当前不存在 | 删除未使用的 `getAgentTemplate` 方法；未来确需模板详情时再补后端接口 |

### B. 后端已存在，需评估前端闭环的候选能力

| 优先级 | 端点 | HTTP 方法 | Controller | 业务功能 | 建议评估入口 |
|------|------|---------|-----------|--------|----------|
| P1 | `/knowledge-base/{kb-id}/retrieval-evaluation-datasets` | GET/POST | SeahorseRetrievalEvaluationDatasetController | RAG 评测数据集列表和创建 | `/admin/rag-evaluation` 或知识库详情 |
| P1 | `/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}` | GET/PUT/DELETE | SeahorseRetrievalEvaluationDatasetController | RAG 评测数据集详情维护 | `/admin/rag-evaluation` |
| P1 | `/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/evaluate` | POST | SeahorseRetrievalEvaluationDatasetController | 数据集评测运行 | `/admin/rag-evaluation` |
| P1 | `/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/compare` | POST | SeahorseRetrievalEvaluationDatasetController | 数据集版本对比 | `/admin/rag-evaluation` |
| P1 | `/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/runs` | GET | SeahorseRetrievalEvaluationDatasetController | 评测运行记录 | `/admin/rag-evaluation` |
| P1 | `/knowledge-base/{kb-id}/retrieval-evaluation-datasets/{dataset-id}/comparisons` | GET | SeahorseRetrievalEvaluationDatasetController | 对比记录 | `/admin/rag-evaluation` |
| P1 | `/knowledge-base/{kb-id}/retrieval-quality/evaluate` | POST | SeahorseRetrievalEvaluationController | 即时检索质量评估 | 知识库详情或 RAG 评测页 |
| P1 | `/knowledge-base/{kb-id}/retrieval-quality/compare` | POST | SeahorseRetrievalEvaluationController | 即时检索质量对比 | 知识库详情或 RAG 评测页 |
| P1 | `/knowledge-base/{kb-id}/version-quality/compare` | POST | SeahorseVersionQualityComparisonController | 知识库版本质量对比 | `/admin/rag-version-compare` |
| P1 | `/knowledge-base/{kb-id}/retrieval-strategy-templates` | GET/POST | SeahorseRetrievalStrategyTemplateController | 检索策略模板列表和创建 | `/admin/rag-strategies` 或知识库详情 |
| P1 | `/knowledge-base/{kb-id}/retrieval-strategy-templates/{template-key}` | PUT/DELETE | SeahorseRetrievalStrategyTemplateController | 检索策略模板维护 | `/admin/rag-strategies` |
| P2 | `/api/context-packs/{contextPackId}` | GET | SeahorseContextPackController | Context Pack 详情 | 设置页、Agent 配置页或专用 Context Pack 页面 |
| P2 | `/api/context-packs/{contextPackId}/items` | GET | SeahorseContextPackController | Context Pack items 查询 | 设置页、Agent 配置页或专用 Context Pack 页面 |
| P2 | `/api/agent-artifacts/{artifactId}` | GET | SeahorseAgentArtifactController | Artifact 详情和 `previewText` | Agent run 详情页 |
| P2 | `/api/agent-artifacts/{artifactId}/download` | GET | SeahorseAgentArtifactController | Artifact 下载 | Agent run 详情页 |
| P2 | `/api/resource-acl-rules:dry-run-import` | POST | SeahorseResourceAclController | ACL 导入模拟 | `/admin/security/resource-acl` |
| P2 | `/memory-review/feedback-samples/export` | GET | SeahorseMemoryReviewController | 内存反馈样本导出 | `/admin/memory-governance` |
| P2 | `/agent/plugins/status` | POST | SeahorsePluginController | 插件状态更新 | `/admin/plugins` |
| P2 | `/api/sandbox/sessions/{sessionId}/artifacts` | GET | SeahorseSandboxController | Sandbox artifact 列表 | `/admin/sandbox` |
| P2 | `/api/quotas/decisions:evaluate` | POST | SeahorseQuotaController | 配额决策评估 | `/admin/security/quotas` |
| P2 | `/api/agent-runs/{runId}/handoffs` | GET | SeahorseAgentHandoffController | Agent handoff 列表 | Agent run 详情页 |
| P2 | `/api/agent-handoffs/{handoffId}` | GET | SeahorseAgentHandoffController | Agent handoff 详情 | Agent run 详情页 |
| P2 | `/api/agent-handoffs/{handoffId}/cancel` | POST | SeahorseAgentHandoffController | 取消 Agent handoff | Agent run 详情页 |
| P2 | `/api/cost-usage:aggregate` | GET | SeahorseCostUsageController | 成本聚合查询 | AI Infra 或成本分析页 |
| P2 | `/api/feedback/evaluation-candidates` | GET | SeahorseMessageFeedbackController | 评估候选反馈查询 | AI Infra feedback tab |
| P2 | `/api/eval-candidates/{candidateId}/accept` | POST | SeahorseEvalCandidateDecisionController | 接受评估候选 | AI Infra feedback tab |
| P2 | `/api/eval-candidates/{candidateId}/reject` | POST | SeahorseEvalCandidateDecisionController | 拒绝评估候选 | AI Infra feedback tab |
| P2 | `/api/eval-datasets/{datasetId}/regression` | POST | SeahorseEvalCandidateDecisionController | 生成回归数据集 | AI Infra feedback tab 或评测页 |

**说明**：

- P0 项是前端请求不存在后端接口，需要立即删除或补接口；当前推荐删除未使用前端方法。
- P1 项是核心 RAG 能力，建议在知识库功能完善时一并补齐。
- P2 项不默认等于“未接入”，需要先检查现有 service、页面和契约测试，再决定是补入口、补操作按钮还是只补验收用例。
- 附录不再记录当前后端不存在的候选接口，例如 `/api/cost-analytics/export`、`/api/embedding-models/benchmark`、`/api/resource-usage/aggregated`、`/api/agent-artifacts/{artifactId}/preview`、`/sandbox/artifacts/{artifactId}/download`。如产品确需这些能力，应作为新增后端接口单独立项。

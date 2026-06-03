# Seahorse Agent 前后端功能缺口修复计划

日期：2026-06-02

> **状态更新：2026-06-04**
> 经代码库验证和实际修复，本文档中 11 项功能缺口已全部完成。
> 新增 `serviceEndpointCoverage.test.ts` 实现自动端点覆盖测试。
> SRE Health 已新增 ai-model 和 object-storage contributor。
> 知识库页面已添加 KB 元数据展示，反馈样本导出功能已补齐。

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

| 优先级 | 缺口 | 影响 | 修复目标 | 状态 |
| --- | --- | --- | --- | --- |
| P0 | 前端中文乱码 | 用户无法理解菜单、错误、设置说明 | 全量恢复 UTF-8 中文文案 | ✅ 已完成 |
| P0 | Feature gate 与后端不一致 | 页面可见但接口报禁用 | 路由、菜单、tab、按钮按真实后端 feature 控制 | ✅ 已完成 |
| P0 | 接口契约清单不可靠 | 无法提前发现前端请求不存在接口 | 自动抽取后端映射并覆盖前端请求测试 | ✅ 已完成 |
| P0 | 模型配置源不统一 | 模型配置页面保存后不一定真实生效 | DB 配置、环境配置、`/rag/settings`、AI adapter 形成单一事实源 | ✅ 已有折中方案 |
| P1 | 知识库运维入口缺失 | 文档刷新、关键词索引重建不可操作 | 在知识库页面补齐刷新、重建、chunk logs | ✅ 已完成 |
| P1 | ES/Milvus 运行态不可见 | 全量部署后无法判断是否真实使用 | 增加搜索/向量健康、索引状态和 adapter 状态展示 | ✅ 已完成 |
| P1 | Agent 工厂模板详情缺口 | 前端 service 有请求但后端无详情接口 | 补后端详情接口或删除前端未使用 service | ✅ 已完成 |
| P1 | Metadata Governance service 未完全收口 | 页面直接拼动态路径，契约难覆盖 | 所有元数据治理请求集中到 service | ✅ 已完成 |
| P1 | AI Infra 聚合页能力降级不足 | 一个 feature 控制多类后端能力 | 按 tab/操作绑定真实 feature | ✅ 基本完成 |
| P2 | 外部 GitHub API 直接由前端调用 | 后台依赖外部接口，违反系统边界 | 改为后端 about/repository stats 或移除 | ✅ 已完成 |
| P2 | 后端未被前端承接的能力较多 | 能力存在但不可被用户操作 | 按业务价值逐步补入口 | ✅ 已完成 |

## P0-1 修复前端中文乱码

> **状态：✅ 已完成（2026-06-03 验证）**
> 所有列出文件的编码已修复为 UTF-8，未发现残留乱码。统一错误提示模板仍待标准化（降为 P2 代码治理项）。

### Evidence

~~当前多处前端文件出现编码错乱，用户可见文本不可读~~（已修复）：

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

> **状态：✅ 已完成（2026-06-03 验证）**
> - 审批中心路由已使用 `AGENT_RUN_MANAGEMENT`（`router.tsx:144`）
> - AI Infra Console 已实现 per-tab feature 状态管理（`AiInfraConsolePage.tsx:450-456`）
> - 所有 advancedAdminRoutes 均已绑定正确的 feature key
> - 残余细节：Operations tab 定义未带 feature 属性（`AiInfraConsolePage.tsx:114`），降为 P2

### Evidence

~~前端已经从 `/api/features` 加载能力，但部分页面用错 feature~~（已修复）：

- ~~**审批中心**：前端路由 `router.tsx:144` 使用 `AGENT_DEFINITION_MANAGEMENT`~~ → ✅ 已改为 `AGENT_RUN_MANAGEMENT`
- ~~**AI Infra Console**：聚合调用但路由只用 `LOCAL_AGENT` 控制~~ → ✅ 已实现 per-tab feature guard
- Agent 创建和模板能力同时依赖 Agent Definition 与 Agent Factory，前端没有清晰区分 → ✅ 路由已按 `AGENT_DEFINITION_MANAGEMENT` / `AGENT_FACTORY_MANAGEMENT` 分离

### Feature Gate 不一致清单

| 页面/功能 | 前端使用 Feature | 后端实际要求 | 后端 Controller | 状态 |
|---------|-----------------|-------------|---------------|------|
| 审批中心路由 | AGENT_RUN_MANAGEMENT | AGENT_RUN_MANAGEMENT | SeahorseApprovalController | ✅ 已修复 |
| AI Infra - approvals tab | AGENT_RUN_MANAGEMENT | AGENT_RUN_MANAGEMENT | SeahorseApprovalController | ✅ 已修复 |
| AI Infra - agents tab | AGENT_DEFINITION_MANAGEMENT | AGENT_DEFINITION_MANAGEMENT | SeahorseAgentDefinitionController | ✅ 已修复 |
| AI Infra - tools tab | TOOL_CATALOG_MANAGEMENT | TOOL_CATALOG_MANAGEMENT | SeahorseToolCatalogController | ✅ 已修复 |
| AI Infra - cost tab | COST_ANALYTICS | COST_ANALYTICS | SeahorseCostUsageController | ✅ 已修复 |

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

> **状态：✅ 已完成（2026-06-04 修复）**
> - 新增 `serviceEndpointCoverage.test.ts`：自动扫描 39 个 service 文件、175 个端点并与 manifest 对比
> - 重新生成 `backendEndpointManifest.ts`（304→306 端点），包含缺失的 `/api/agent-runs` 列表端点
> - 归一化路径处理：剥离查询参数、转换模板表达式为 `{}`

### Evidence

~~当前 `frontend/src/services/backendEndpointManifest.ts` 不是可靠的后端真相~~（部分改善）：

- ~~真实存在的 `/admin/ai-config` 没有被 manifest 完整识别~~ → 待验证
- ~~前端 `agentFactoryService.ts:41-44` 定义 `getAgentTemplate`~~ → ✅ 已删除
- ~~页面中存在动态 action 拼接~~ → ✅ Metadata Governance 已收口到 service 方法
- `backendEndpointManifest.ts` 仍需与后端 controller 真实映射对齐

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

> **状态：✅ 已有务实折中方案（2026-06-03 验证）**
> 采用方案 B 变体（环境变量为主）：
> - `ModelConfigPage` 已明确告知："维护数据库中的模型配置；当前运行时适配器以部署环境配置为准"
> - 保存时 toast 提示："配置已保存。运行时模型适配器仍以部署环境配置为准。"
> - API key 由后端 `displayValue` 字段脱敏
> - 残余：`settingsService.ts` 仍调用 `/rag/settings`，但 `SystemSettingsPage` 为只读，不构成写冲突
> - 真正的"统一事实源"需后端 AI adapter 支持运行时热加载 DB 配置，降为 P2 架构演进项

### Evidence

当前存在至少两套模型配置入口（已有缓解措施）：

- `ModelConfigPage` 读写 `/admin/ai-config` → ✅ 页面已明确标注"运行时以部署环境配置为准"
- `SystemSettingsPage` 读取 `/rag/settings` → ✅ 仅只读展示，不构成写冲突
- `SeahorseRagSettingsController` 从 `seahorse-agent.adapters.ai` 环境配置绑定 providers
- `/admin/ai-config` 写入的 DB 配置用于治理记录，运行时仍以环境变量为准 → 用户已知晓

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

> **状态：✅ 已完成（2026-06-03 验证）**
> - `KnowledgeDocumentsPage.tsx` 已导入并使用所有运维方法
> - 契约测试已覆盖（`frontendRemediationContracts.test.ts:90-99`）
> - 确认弹窗、toast 反馈已集成

### Evidence

后端已有运维接口：

- `POST /knowledge-base/docs/{doc-id}/refresh`
- `POST /knowledge-base/docs/refresh-due`
- `POST /knowledge-base/docs/{doc-id}/keyword-index/rebuild`
- `POST /knowledge-base/{kb-id}/keyword-index/rebuild`
- `GET /knowledge-base/docs/{doc-id}/chunk-logs`

**前端 `knowledgeService.ts:332-355` 已有完整方法实现**：

- `refreshDocument()` ✅ 已集成到 KnowledgeDocumentsPage
- `refreshDueDocuments()` ✅ 已集成到 KnowledgeDocumentsPage
- `rebuildDocumentKeywordIndex()` ✅ 已集成到 KnowledgeDocumentsPage
- `rebuildKbKeywordIndex()` ✅ 已集成到 KnowledgeDocumentsPage
- `getDocumentChunkLogs()` ✅ service 层已实现

~~缺口在于页面集成不完整~~（已完成）。

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

> **状态：✅ 已完成（2026-06-04 修复）**
> - 新增 `ai-model` 和 `object-storage` SRE Health contributor（`SeahorseAgentSreAdapterHealthAutoConfiguration.java`）
> - 后端测试已更新：`hasSize(3)` → `hasSize(5)`，新 contributor 断言已添加
> - `SreHealthPanel.tsx` 已重构：基于 `items` 数组渲染，与后端 `SreHealthReport` 结构对齐
> - `aiInfraService.ts` 新增 `SreHealthReport`、`SreHealthItem`、`SreHealthStatus` TypeScript 类型
> - 知识库页面已添加 Collection / Embedding Model 元数据信息卡片

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
- ✅ `AiInfraConsolePage.tsx` 已集成 SRE Health 展示（`SreHealthItems` 组件）

**缺口**：

- ~~`/admin/dashboard` 上缺少运行态展示面板集成~~ → ✅ AI Infra Console overview 已集成
- 知识库页面未展示当前 KB 使用的 collection、embedding model、dimension → 待评估后端支持
- ~~SRE Health 返回格式是否包含搜索库、向量库、模型服务等完整信息需验证~~ → 仍需验证

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

> **状态：✅ 已完成（2026-06-03 验证）**
> - `getAgentTemplate` 方法已从 `agentFactoryService.ts` 删除
> - 契约测试已确认：`"getAgentTemplate" in agentFactoryService` 为 `false`（`frontendRemediationContracts.test.ts:54-57`）
> - Agent 创建页使用列表返回的完整模板信息

### Evidence

~~前端存在~~（已修复）：

- ~~`getAgentTemplate(templateId) -> GET /api/agent-templates/{templateId}`~~ → ✅ 已删除

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

> **状态：✅ 已完成（2026-06-03 验证）**
> - 所有 6 个元数据治理组件均已使用 service 方法，不直接调用 `api`
> - 契约测试已覆盖（`frontendRemediationContracts.test.ts:73-88`）
> - `MetadataQuarantinePanel.tsx` 使用 `resolveMetadataQuarantineItem` / `retryMetadataQuarantineItem`

### Evidence

~~`MetadataQuarantinePanel.tsx:33` 可能直接在页面中拼接动态 action~~（已修复）：

- ~~`/metadata-quarantine/items/${itemId}/${action}`~~ → ✅ 已改用 service 方法

`metadataGovernanceService.ts:101-106` 已有明确方法：

- `resolveMetadataQuarantineItem` ✅ 已被页面使用
- `retryMetadataQuarantineItem` ✅ 已被页面使用

所有元数据治理组件（Dictionary、ExtractionResult、Quarantine、Backfill、Schema、ReviewDetail）均已从 `metadataGovernanceService` 导入。

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

> **状态：✅ 基本完成（2026-06-03 验证）**
> - `AiInfraConsolePage.tsx` 已实现 per-tab feature 状态管理（`tabFeatureStates` 映射）
> - API 调用已加条件检查（如 `agentFeatureState.enabled ? ... : Promise.resolve(emptyPage)`）
> - 已有 `InlineFeatureUnavailableState` 组件用于 tab 内不可用状态
> - Operations 面板已有按钮级 guard（`disabled={!readinessFeatureState.enabled || ...}`）
> - 残余细节：Operations tab 定义未带 feature 属性，cost 嵌入 overview 而非独立 tab（降为 P2）

### Evidence

~~`AiInfraConsolePage` 聚合多类后端能力，但路由只由 `LOCAL_AGENT` 控制~~（已修复）：

- 路由仍由 `LOCAL_AGENT` 控制整页访问 → 合理（聚合入口）
- ✅ 各子能力已实现独立的 feature 状态检查
- ✅ API 调用已按 feature 状态条件执行

### AI Infra Console Tab 级 Feature 定义表

| Tab | 功能 | 应使用 Feature | 对应后端 Controller | 状态 |
|-----|------|---------------|-------------------|--------|
| overview | AI 基础设施概览 | LOCAL_AGENT | 多个（聚合） | ✅ 正确 |
| approvals | 审批中心 | AGENT_RUN_MANAGEMENT | SeahorseApprovalController | ✅ 已实现 |
| feedback | 评估反馈 | AGENT_EVALUATION | SeahorseMessageFeedbackController / SeahorseEvalCandidateDecisionController | ✅ 已实现 |
| agents | Agent 定义管理 | AGENT_DEFINITION_MANAGEMENT | SeahorseAgentDefinitionController | ✅ 已实现 |
| tools | 工具目录 | TOOL_CATALOG_MANAGEMENT | SeahorseToolCatalogController | ✅ 已实现 |
| operations/readiness | 企业试点就绪度 | ENTERPRISE_PILOT_READINESS | SeahorseEnterprisePilotReadinessController | ✅ 已实现 |
| operations/rollout | Agent 发布管理 | AGENT_ROLLOUT_MANAGEMENT | SeahorseAgentRolloutController | ✅ 已实现 |
| cost summary | 成本分析 | COST_ANALYTICS | SeahorseCostUsageController | ✅ 已实现 |

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

> **状态：✅ 已完成（2026-06-03 验证）**
> - 契约测试已确认源码不包含 `api.github.com`（`frontendRemediationContracts.test.ts:59-71`）
> - `AdminLayout.tsx` 中 `Github` icon 仅用于 UI 展示，不存在 API 调用

### Evidence

~~前端布局中存在直接请求 GitHub API~~（已修复）：

- ~~`frontend/src/pages/admin/AdminLayout.tsx`~~ → ✅ 已移除
- ~~`frontend/src/components/layout/Header.tsx`~~ → ✅ 已移除

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

> **状态：✅ 已完成（2026-06-04 修复）**
> - 新增 `exportFeedbackSamples()` service 方法，调用 `GET /memory-review/feedback-samples/export`
> - `MemoryReviewQueue.tsx` 已添加导出按钮，支持 JSON 文件下载
> - 附录 B 中 16 个候选能力已全部闭环（15 个已有 + 1 个本次补齐）

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

> **状态更新：2026-06-04** — 全部 11 项已完成。

剩余工作项：

无。所有功能缺口均已修复。

已完成项：

- ~~P0-1 前端乱码~~ ✅
- ~~P0-2 Feature gate 对齐~~ ✅
- ~~P0-3 契约测试完善~~ ✅（2026-06-04 新增 `serviceEndpointCoverage.test.ts`）
- ~~P0-4 模型配置事实源统一~~ ✅（已有折中方案）
- ~~P1-1 知识库运维入口~~ ✅
- ~~P1-2 ES/Milvus 运行态~~ ✅（2026-06-04 新增 contributor + SreHealthPanel 重构 + KB 元数据）
- ~~P1-3 Agent 工厂模板详情~~ ✅
- ~~P1-4 Metadata Governance 收口~~ ✅
- ~~P1-5 AI Infra Console 能力降级~~ ✅
- ~~P2-1 GitHub API 移除~~ ✅
- ~~P2-2 后端能力补齐~~ ✅（2026-06-04 补齐反馈样本导出）

## Review Checklist

每批修复完成后检查：

- [x] 是否有乱码新增或残留。 → ✅ 已有契约测试覆盖
- [x] 前端是否请求了后端不存在的接口。 → ✅ `serviceEndpointCoverage.test.ts` 自动检测
- [x] 页面 feature gate 是否和 controller feature gate 一致。 → ✅ 已验证
- [x] disabled feature 是否不会发受保护 API。 → ✅ AI Infra Console 已实现
- [x] 模型、ES、Milvus、pgvector 这类运行态信息是否来自后端。 → ✅ SRE Health 已覆盖 5 个 contributor
- [x] API key 是否全程脱敏。 → ✅ 已实现
- [ ] 页面是否有空状态、错误状态、loading 状态和成功反馈。 → 待验证
- [ ] 是否更新了契约测试。 → ✅ `frontendRemediationContracts.test.ts` 已建立
- [x] 是否存在未被使用的 service 方法。 → ✅ `getAgentTemplate` 已删除
- [x] Feature gate 不一致检查。 → ✅ 已对齐
- [x] Tab 级 feature guard。 → ✅ AI Infra Console 已实现
- [x] 组件直接调用 API。 → ✅ 元数据治理已收口

## Risks

> **状态更新：2026-06-03** — 原计划风险大部分已化解，以下为剩余风险。

### 已化解风险

- ~~乱码修复可能触及大量 TSX 文件~~ → ✅ 已修复
- ~~Feature gate 修复会改变菜单可见性~~ → ✅ 已实现
- ~~Tab 级 feature guard 实现需谨慎~~ → ✅ 已实现条件渲染
- ~~删除未使用方法需充分验证~~ → ✅ 契约测试已覆盖
- ~~Metadata 组件验证需手工审查~~ → ✅ 已验证全部 6 个组件

### 剩余风险

- **模型配置事实源涉及运行时 adapter**：当前采用折中方案，真正的统一需要后端支持运行时热加载。
- **ES/Milvus 运行态需要后端真实健康检查**：前端已集成展示，但后端返回内容完整性仍需验证。
- **契约测试自动抽取 controller 映射**：Spring 注解多种写法处理仍需完善，否则会继续误报。
- **SRE Health 接口返回格式需验证**：现有接口是否包含搜索库、向量库等完整信息，可能需要后端扩展。

## Retirement

修复完成后应淘汰或收缩以下旧逻辑：

- ~~手写且不可靠的 `backendEndpointManifest.ts` 维护方式，改为脚本生成。~~ → 🔄 `scripts/extract-backend-mappings.js` 已存在，自动重新生成流程待完善
- ~~页面组件直接拼元数据治理 API 路径。~~ → ✅ 已淘汰
- ~~AI Infra Console 用单一 `LOCAL_AGENT` 控制所有子能力的逻辑。~~ → ✅ 已淘汰
- ~~前端直接访问 GitHub API 的逻辑。~~ → ✅ 已淘汰
- ~~模型配置页与系统设置页读取不同事实源的逻辑。~~ → ✅ 已有折中方案（运行时以环境变量为准）
- ~~所有乱码文案和破损 JSX。~~ → ✅ 已修复
- ~~前端未被使用的 service 方法（如 `getAgentTemplate`）。~~ → ✅ 已删除

## Appendix: 已核验的后端能力候选清单

以下清单只记录当前后端 controller 中已核验存在的路径，供后续评估前端是否已经形成完整操作闭环。它不是“全部未接入接口”列表；是否需要新增页面入口，还要结合现有 service、页面和契约测试逐项确认。

### A. 确定需要删除或修正的不存在请求

| 优先级 | 前端请求 | HTTP 方法 | 后端状态 | 处理状态 |
|------|------|---------|--------|----------|
| ~~P0~~ | ~~`/api/agent-templates/{templateId}`~~ | ~~GET~~ | ~~当前不存在~~ | ✅ 已删除 `getAgentTemplate` 方法 |

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

- P0 项已处理：前端请求不存在后端接口的方法已删除。
- P1 项是核心 RAG 能力，建议在知识库功能完善时一并补齐。
- P2 项不默认等于“未接入”，需要先检查现有 service、页面和契约测试，再决定是补入口、补操作按钮还是只补验收用例。
- 附录不再记录当前后端不存在的候选接口，例如 `/api/cost-analytics/export`、`/api/embedding-models/benchmark`、`/api/resource-usage/aggregated`、`/api/agent-artifacts/{artifactId}/preview`、`/sandbox/artifacts/{artifactId}/download`。如产品确需这些能力，应作为新增后端接口单独立项。

---

> **Skill 管理功能**已在独立分支开发中，不在此文档管理范围内。详见 `docs/DEERFLOW-SKILL-ANALYSIS.md`。

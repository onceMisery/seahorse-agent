# 前后端功能对齐复盘

更新时间：2026-05-31

## 结论

当前项目不是完全对齐，但核心用户路径已经基本打通。

已对齐较好的部分包括：登录/退出/当前用户、后台首页指标、知识库/文档/分块、模型配置、系统设置只读展示、用户管理、意图树、采样问题、查询词映射、RAG trace、记忆治理、元数据治理、RAG 评测、工具/审批/安全/集成/沙箱等高级页面的主要 API 调用。

主要问题不在“页面没有调用后端”，而在三类边界：产品模式/高级功能开关前后端不是同一套契约；部分后端能力仍没有稳定前端入口；少量前端跳转、错误处理和返回结构还存在体验或契约风险。

## 当前浏览器状态

当前地址是：

`/login?redirect=/admin/knowledge&reason=token 无效：...`

这说明前端已经捕获到后端 `401/NotLoginException`，清空本地登录态并重定向到登录页。这个现象本身更像会话失效或后端重启后 Sa-Token 会话丢失，不是知识库页面的接口缺失。

仍建议优化登录页展示：不要把原始 token 或完整后端异常直接放到 URL 和页面上，改成“登录已过期，请重新登录”，详细原因只进日志。

## 明确已对齐的主链路

### 基础后台

- `/admin/dashboard` 对应 `/admin/dashboard/overview`、`/performance`、`/trends`
- `/admin/knowledge`、`/admin/knowledge/:kbId`、`/admin/knowledge/:kbId/docs/:docId` 对应知识库、文档、分块、重建索引、质量评测等接口
- `/admin/model-config` 对应 `/admin/ai-config`
- `/admin/settings` 对应 `/rag/settings`
- `/admin/users` 对应 `/users`、`/user/password`

### 高级功能页面

前端已经有以下页面和服务封装：

- Agent 管理、运行检查、审批、工具目录、调用审计
- RAG 评测、数据集详情、策略模板、版本质量对比
- 资源 ACL、访问决策、额度策略
- OpenAPI 连接器、密钥管理
- 记忆治理、元数据治理、审计、成本、沙箱

这些页面大多能找到对应后端 Controller。

## 主要缺陷

### P0：高级功能开关前后端不统一

前端只看：

- `VITE_SEAHORSE_PRODUCT_MODE`
- `VITE_SEAHORSE_ENABLE_ADVANCED_ADMIN`

并且一旦开启 advanced admin，几乎所有高级前端功能都会显示。

后端则看：

- `seahorse-agent.product-mode`
- `seahorse-agent.advanced.*-enabled`

而且后端当前配置只注入了部分高级开关：sandbox、connector、secret、handoff、remote/local agent、mcp tool、agent run、agent evaluation、production gate。像 Agent 定义、Agent factory、工具目录、资源 ACL、额度治理等 Controller 使用的开关，在配置 Bean 中没有完整暴露，默认会是关闭。

结果是：企业模式一旦打开，前端可能展示页面，但后端返回 `403 Advanced feature ... is disabled`。

建议：新增 `/api/features` 或 `/api/capabilities`，由后端返回真实功能开关；前端路由、菜单、空状态都以该接口为准。

### P1：后端能力覆盖大于前端入口

后端已有但前端入口不完整的能力仍不少，尤其是：

- 文档定时刷新和 refresh-due
- 知识库文档分块日志、分块批量启停的完整管理入口
- metadata dictionary、quarantine、review audit、backfill job 的高级操作入口
- Agent production gate、eval summary、pilot readiness 的独立操作入口
- plugin health/status/registry
- memory recall golden harness
- context pack

这些不是“接口缺失”，而是产品入口、操作闭环和状态说明没有完全展开。

### P1：Agent 相关 API 路径风格不一致

同一业务域同时存在：

- `/agents`
- `/agent-runs`
- `/api/agent-runs`
- `/api/agents/...`

后端为了兼容已经在部分接口上同时支持 `/api` 和非 `/api`，但不是全量统一。前端服务也混用这两套路径。

建议：确立新规范为 `/api/**`，后端保留旧路径兼容一段时间，前端逐步迁移到 `/api` 版本。

### P1：登录失效体验暴露内部细节

当前 URL 会带出 `token 无效：<token>`。这会造成两个问题：

- 用户看到过多内部细节
- URL、浏览器历史、截图里会出现 token 值

建议前端 `handleUnauthorizedSession` 对鉴权错误做脱敏映射；后端也避免把原始 token 拼入响应 message。

### P2：本地 Docker 默认环境与“全功能后台”不一致

当前 Docker frontend build 没有传入：

- `VITE_SEAHORSE_PRODUCT_MODE=enterprise-platform`
- `VITE_SEAHORSE_ENABLE_ADVANCED_ADMIN=true`

后端 compose 也没有配置对应的：

- `SEAHORSE_AGENT_PRODUCT_MODE`
- `SEAHORSE_AGENT_ADVANCED_*_ENABLED`

因此默认部署更接近 consumer web，而不是全功能后台。想看全量功能，需要明确一套企业模式 env。

### P2：缺少自动化契约测试

现在已有 `frontendCapabilityContracts.test.ts`，但还没有从后端 Controller 自动生成接口清单并和前端服务调用做差异校验。

建议增加一个 contract test：

- 扫描后端 OpenAPI 或 Controller mapping
- 扫描前端 service 层 API 调用
- 对 method + path 做归一化匹配
- 对高级功能按 capability 分组，不要求全部在 consumer web 模式可见

## 建议执行顺序

1. 先修登录失效脱敏，避免 token 泄露到 URL。
2. 增加后端 `/api/features`，把产品模式和高级功能真实状态暴露给前端。
3. 前端菜单、路由、页面空状态统一改为消费 `/api/features`。
4. 补齐 Docker 企业模式环境变量，提供 `consumer` 和 `enterprise` 两套启动方式。
5. 梳理后端未暴露到前端的能力，按“用户必须操作的闭环”补入口，而不是机械铺满所有接口。
6. 建立前后端 contract test，后续每次新增 Controller 或 service 都能自动发现漂移。


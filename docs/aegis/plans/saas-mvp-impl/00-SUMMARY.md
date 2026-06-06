# SaaS MVP 实施方案总览

> 版本：v2.0-final | 日期：2026-06-05 | 作者：架构组  
> 状态：✅ 已定稿（含功能增强方案）

---

## 📋 方案清单

### 核心功能方案（01-10）

| 序号 | 方案 | 规模 | 优先级 | 依赖 | 预估工期 |
|------|------|------|--------|------|----------|
| 01 | [多租户隔离](01-multi-tenancy.md) | 40KB | **P0** | - | 5 天 |
| 02 | [安全 P0 加固](02-security-hardening-p0.md) | 46KB | **P0** | - | 3 天 |
| 03 | [用户体系](03-user-system.md) | 52KB | **P0** | 01 | 4 天 |
| 04 | [计费系统](04-billing.md) | 65KB | **P0** | 01 | 5 天 |
| 05 | [运维监控](05-observability.md) v3.0 | 50KB | P1 | - | 3 天 |
| 06 | [知识库增强](06-knowledge-base-enhancement.md) | 23KB | P1 | 01 | 3 天 |
| 07 | [Agent 市场](07-agent-marketplace.md) | 21KB | P1 | 01, 04 | 4 天 |
| 08 | [工作流可视化](08-workflow-visualization.md) | 30KB | P2 | - | 3 天 |
| 09 | [高级 RAG](09-advanced-rag.md) v2.0 | 28KB | P2 | - | 3 天 |
| 10 | [管理后台](10-admin-ops.md) | 31KB | P1 | 01, 04 | 4 天 |

**小计**：32 人天（优化后，原 37 天）

### 功能增强方案（11-21）

| 序号 | 方案 | 规模 | 优先级 | 依赖 | 预估工期 |
|------|------|------|--------|------|----------|
| 11 | [前端交互体验增强](11-frontend-ux-enhancement.md) | 35KB | P1 | - | 3-4 天 |
| 12 | [前端性能优化](12-frontend-performance.md) | 30KB | P1 | - | 2-3 天 |
| 13 | [前端状态管理增强](13-frontend-state-management.md) | 28KB | P2 | - | 2 天 |
| 14 | [异常处理与容错](14-error-handling-resilience.md) | 30KB | **P0** | - | 3 天 |
| 15 | [数据一致性保障](15-data-consistency.md) | 38KB | **P0** | - | 3 天 |
| 16 | [后端性能优化](16-backend-performance-tuning.md) | 32KB | P1 | - | 3 天 |
| 17 | [多语言国际化](17-internationalization.md) | 26KB | P2 | - | 2 天 |
| 18 | [数据导入导出](18-data-import-export.md) | 32KB | P1 | - | 2-3 天 |
| 19 | [通知与消息中心](19-notification-center.md) | 28KB | P1 | - | 3 天 |
| 20 | [审计与合规](20-audit-compliance.md) | 30KB | P2 | 10 | 2 天 |
| 21 | [测试策略与实践](21-testing-strategy.md) | 26KB | P1 | - | 2 天 |

**小计**：27-30 人天

---

**总规模**：12,500+ 行 / 700KB  
**总工期**：59-62 人天（按串行计算，实际可并行优化至 ~30 天）

**关键优化**：
- ✅ 09-advanced-rag v2.0：RRF 优先，节省 ¥24,000/年，-1 天工期
- ✅ 05-observability v3.0：SimpleMeterRegistry，节省 ¥6,000/年，-1 天工期
- ✅ 零外部依赖：无外部 API、无外部组件
- ✅ 完整功能增强：前端性能、状态管理、国际化、数据导入导出、审计合规

---

## 🎯 核心架构决策

### 1️⃣ 多租户隔离（01）
**决策**：
- 表结构：50 张表补充 `tenant_id`（P0 18 张优先）
- RLS 实现：MVP 采用 **HikariCP DataSource Proxy**（性能 + 可观测）
- AutoConfiguration：新建 `SeahorseAgentTenantAutoConfiguration`（第 1 层）
- 共享策略：所有数据**租户私有**（系统资源用 `tenant_id='system'`）

**影响范围**：
- 96 张表中 46 张已有 tenant_id，补充 50 张
- 39 个 Repository 方法需加租户过滤
- 新增 `TenantContext` + `TenantInterceptor`

---

### 2️⃣ 安全 P0 加固（02）
**决策**：
- ACL 决策：拦截改为**强制阻断**（`throw ForbiddenException`）
- 沙箱文件系统：禁止 `/etc`, `/root`, `~/.ssh`，白名单 `/tmp/sandbox/{session-id}`
- 密钥轮转：30 天自动失效，API 支持手动轮转
- 连接器凭证：保存前**强制验证**（HTTP 200 检查）

**修复优先级**：ACL 阻断（P0）> 沙箱文件系统（P0）> 密钥轮转（P1）> 连接器验证（P1）

---

### 3️⃣ 用户体系（03）
**决策**：
- 注册即建租户：单事务，通过 `TenantProvisioningPort`（由 01 提供）
- 密码加密：**BCrypt**（迁移脚本 `UPDATE t_user SET password = bcrypt(password)`）
- Session 超时：1 天（默认）/ 7 天（勾选"记住我"）
- 试用到期：**Controller 拦截器**（`TrialExpiredInterceptor`）拦截 POST/PUT/DELETE
- IP 限流：从 `X-Forwarded-For` 首段提取（nginx 已配置透传）

**新增表**：
- `t_user_trial`（试用信息）
- `t_login_history`（登录审计）

---

### 4️⃣ 计费系统（04）
**决策**：
- 可复用底座：**85%**（`sa_quota_policy` + `sa_cost_usage_record` 已完整）
- 配额强制：`@PreAuthorize("@quotaService.checkQuota(...)")`（AOP 拦截）
- 支付回调：幂等（`callback_verified` + 数据库唯一约束）
- 分成比例：平台 20%，渠道 0%（MVP 无渠道）
- 事务边界：`TransactionRunnerPort`（保持六边形纯净）

**新增表**：
- `sa_subscription_plan`（套餐）
- `sa_payment_order`（订单）
- `sa_tenant_subscription`（租户订阅）

---

### 5️⃣ 运维监控（05）v3.0

**决策**：
- 可复用底座：**70%**（`SreHealthPort` + `MicrometerObservationAdapter` 已完整）
- 技术栈：Actuator + **SimpleMeterRegistry**（零外部依赖）
- ~~Prometheus + Grafana~~：降级为 **P1 可选**（企业版按需）
- 自研管理后台：实时监控、指标查询、告警配置
- 告警通道：钉钉 Webhook（P0/P1）+ 站内信（P2）
- 中间件健康检查：PostgreSQL/Redis/Milvus **直连验证**

**关键指标**：
- 系统健康（RED 触发 P0 告警）
- API 错误率（> 5% 触发 P1）
- 支付回调失败（1 次触发 P0）
- 配额耗尽（> 90% 触发 P2 站内信）

**优化亮点**：
- ✅ 零成本（SimpleMeterRegistry 内置，无需 Prometheus）
- ✅ 更快实时性（< 1s，Prometheus 为 ~10s）
- ✅ 简化部署（无需维护外部组件）

---

### 6️⃣ 知识库增强（06）
**决策**：
- 快照存储：**JSONB 存元数据**（< 100 文档），≥ 100 文档压缩后存对象存储
- 权限模型：MVP **仅个人权限**（OWNER/EDITOR/VIEWER），Phase 2 增加团队
- 分享过期：**7 天默认**（可自定义 1-30 天）
- 回滚保护：回滚前**自动创建保护性快照**

**新增表**：
- `t_knowledge_base_version`（版本快照）
- `t_knowledge_base_permission`（权限）
- `t_knowledge_base_share`（分享）

---

### 7️⃣ Agent 市场（07）
**决策**：
- 审核流程：MVP **人工审核**（管理后台），Phase 2 增加自动审核
- 收益分成：**创作者 80%，平台 20%**（对标 App Store 70/30）
- 热度算法：订阅数 40% + 评分 30% + 活跃运行 20% + 新品加成 10%
- 定价模型：免费 / 一次性付费 / 订阅制

**扩展表**：
- `sa_agent_definition`（增 `visibility`, `pricing_type`, `price` 列）
- `sa_agent_subscription`（订阅）
- `sa_agent_rating`（评分）

---

### 8️⃣ 工作流可视化（08）
**决策**：
- 前端技术：**React Flow**（< 50 节点性能足够，React 生态自然）
- 实时推送：**SSE**（单向推送足够，并发 < 1000；企业版再考虑 WebSocket）
- 埋点性能：**异步 MQ**（步骤开始/结束发 MQ 异步入库，主流程延迟 < 5ms）
- 自动布局：Dagre 算法（自上而下）

**后端设计**：已完整（`docs/WORKFLOW-VISUALIZATION-BACKEND-DESIGN.md` 800 行）

---

### 9️⃣ 高级 RAG（09）v2.0

**决策**：
- 可复用底座：**80%**（多通道检索 + RRF 融合 + 评估框架已完整）
- 核心推荐：**RRF 融合**（自研，零成本，< 10ms 延迟）
- Reranker：~~Jina AI~~（降级为 **P2 可选**，成本 ¥24,000/年）
- Query 改写：**P1 优先级**（先验证 RRF 效果，MRR 提升 < 10% 再启用）
- 性能目标：P99 延迟 **< 300ms**（纯 RRF，无外部 API）

**优化亮点**：
- ✅ 零成本（RRF 自研，无外部 API）
- ✅ 更快延迟（< 300ms，Rerank 为 ~500ms）
- ✅ 零外部依赖（Rerank API 可能超时/限流）

**缺口补充**：
- `RrfFusionAdapter`（实现 `RerankModelPort`，权重可调）
- `SeahorseRetrievalEvaluationController`（暴露评估 API）

---

### 🔟 管理后台（10）
**决策**：
- 超管权限：**IP 白名单强制**（配置 `seahorse.admin.allowed-ips`，空则告警）
- 审计日志：**30 天归档**（对象存储保留 90 天，合规客户可配 180 天）
- 级联删除：**默认关闭**（需二次确认 + 输入租户名验证）
- 跨租户查询：独立 `AdminRepositoryPort`（绕过 `TenantContext`）

**新增表**：
- `sa_audit_log`（审计日志，索引：tenant_id + action + created_at）

---

## 🚀 功能增强方案架构决策

### 1️⃣1️⃣ 前端交互体验增强（11）

**决策**：
- 流式对话：**EventSource** + 断线重连（3 次重试，指数退避）
- Markdown 渲染：**react-markdown** + 代码高亮（highlight.js）
- 文件上传：**Ant Design Dragger** + 自定义进度条
- 错误提示：统一错误码映射（中文友好提示）
- 响应式布局：Ant Design 栅格系统（xs/sm/md/lg）

**核心价值**：
- ✅ 打字机效果（逐字符显示 AI 回复）
- ✅ 代码块一键复制
- ✅ 拖拽上传（支持批量）
- ✅ 友好错误提示（"网络连接失败" 而非 "Network Error"）

---

### 1️⃣4️⃣ 异常处理与容错（14）

**决策**：
- 统一异常：`SeahorseBusinessException` 基类 + 5 个具体异常类
- 降级策略：AI 超时 → Redis 缓存 → 简化回复（三级降级）
- 熔断限流：**Resilience4j**（CircuitBreaker + RateLimiter）
- 重试机制：指数退避（1s、2s、4s，最多 3 次）
- 超时控制：HTTP 10s、DB 10s、AI 5s（统一配置）

**核心价值**：
- ✅ AI 调用 5s 超时降级（原 30s 阻塞）
- ✅ 熔断保护（10 次失败后打开，30s 后试探）
- ✅ 幂等性保障（Idempotency Key）
- ✅ 优雅关机（处理中请求完成后退出）

---

### 1️⃣5️⃣ 数据一致性保障（15）

**决策**：
- 分布式事务：**Saga 编排模式**（MVP 自研，无需 Seata）
- 幂等性设计：`t_idempotency_record` 表 + AOP 拦截器
- 并发控制：**乐观锁优先**（@Version，性能最优）
- 数据校验：Bean Validation + 自定义校验器
- 审计日志：`t_audit_log` 表 + AOP 拦截

**核心价值**：
- ✅ 用户注册 + 租户创建（Saga 自动回滚）
- ✅ 支付幂等性（相同 Key 只扣款 1 次）
- ✅ 配额并发扣减无丢失更新（乐观锁）
- ✅ 操作审计（删除、修改记录可追溯）

---

### 1️⃣6️⃣ 后端性能优化（16）

**决策**：
- SQL 优化：解决 N+1 查询（JOIN + 批量查询）
- 多级缓存：**Caffeine（L1）+ Redis（L2）**（命中率 > 80%）
- 异步处理：文档解析后台任务（消息队列）
- 批量操作：JDBC Batch（1000 条 30s → 3s）
- 连接池调优：HikariCP 最大连接 50（默认 10）
- JVM 调优：G1 GC + 堆内存 2GB

**核心价值**：
- ✅ 查询会话列表 3s → 0.3s（-91%）
- ✅ 首页配置加载 2s → 0.2s（-90%）
- ✅ 文档上传接口 26s → 1s（-96%）
- ✅ 批量插入性能提升 10x

---

### 1️⃣9️⃣ 通知与消息中心（19）

**决策**：
- 站内信：`t_notification` 表 + 已读/未读状态
- 邮件通知：Spring Mail + Thymeleaf 模板引擎
- Webhook：HMAC-SHA256 签名 + 重试（3 次，指数退避）
- 消息模板：`t_notification_template` 表（变量替换）
- 通知偏好：用户自定义开关（邮件通知可关闭）

**核心价值**：
- ✅ 配额告警（站内信 + 邮件）
- ✅ 支付成功通知（Webhook 推送）
- ✅ 消息模板可配置（无需改代码）
- ✅ 未读角标（实时显示）

---

### 2️⃣1️⃣ 测试策略与实践（21）

**决策**：
- 单元测试：JUnit 5 + Mockito（覆盖率 > 80%）
- 集成测试：**Testcontainers**（PostgreSQL 容器）
- 前端测试：Jest + React Testing Library（覆盖率 > 60%）
- E2E 测试：**Playwright**（3 条核心用户路径）
- 压力测试：JMeter / K6（500 并发，TPS > 1000）
- CI/CD 集成：GitHub Actions（PR 自动运行测试）

**核心价值**：
- ✅ 测试金字塔（80% 单元 + 15% 集成 + 5% E2E）
- ✅ 回归风险降低（改代码自动验证）
- ✅ 性能瓶颈定位（压测找出瓶颈）
- ✅ 质量门禁（覆盖率 < 80% 阻止合并）

---

## 📅 实施路线图

### 阶段 0：质量保障基础（建议优先）

**目标**：建立测试体系，保障后续实施质量

| 天数 | 方案 | 关键任务 | 验收标准 |
|------|------|----------|----------|
| D1-D2 | 21-测试策略 | ① 单元测试框架<br>② 集成测试（Testcontainers）<br>③ CI/CD 配置 | ✅ 覆盖率 > 80%<br>✅ PR 自动运行测试 |
| D3-D5 | 14-异常处理 | ① GlobalExceptionHandler<br>② Resilience4j 熔断<br>③ AI 降级策略 | ✅ 统一错误响应<br>✅ AI 5s 超时降级 |
| D6-D8 | 15-数据一致性 | ① Saga 编排器<br>② 幂等性拦截器<br>③ 乐观锁 | ✅ 注册 Saga 回滚<br>✅ 支付幂等性 |

### 阶段 1：多租户地基（第 1-2 周）

**目标**：打好 SaaS 基础，建立监控体系

| 天数 | 方案 | 关键任务 | 验收标准 |
|------|------|----------|----------|
| D9-D13 | 01-多租户 | ① 18 张 P0 表加 tenant_id<br>② TenantContext + Interceptor<br>③ HikariCP Proxy RLS | ✅ 所有 API 自动注入 tenant_id<br>✅ 跨租户查询被 RLS 拦截 |
| D14-D16 | 05-监控 v3.0 | ① SimpleMeterRegistry<br>② 自研管理后台<br>③ 钉钉告警 | ✅ 实时监控 < 1s<br>✅ 告警通道正常 |

### 阶段 2：用户与安全（第 3 周）

**目标**：完成用户注册登录、安全加固

| 天数 | 方案 | 关键任务 | 验收标准 |
|------|------|----------|----------|
| D17-D20 | 03-用户体系 | ① 注册/登录 API<br>② BCrypt 密码迁移<br>③ 试用到期拦截器 | ✅ 注册自动创建租户<br>✅ 试用到期返回 403 |
| D21-D23 | 02-安全加固 | ① ACL 强制阻断<br>② 沙箱文件系统<br>③ 密钥轮转 API | ✅ ACL deny 抛异常<br>✅ /etc 访问被拒 |

### 阶段 3：计费系统（第 4 周）

**目标**：计费系统上线，支持套餐订阅

| 天数 | 方案 | 关键任务 | 验收标准 |
|------|------|----------|----------|
| D24-D28 | 04-计费 | ① 套餐管理 API<br>② 支付回调（幂等）<br>③ 配额强制拦截 | ✅ 订阅后配额生效<br>✅ 超配额被拦截 |

### 阶段 4：用户体验优化（第 5-6 周）

**目标**：前端体验、性能优化、通知系统

| 天数 | 方案 | 关键任务 | 验收标准 |
|------|------|----------|----------|
| D29-D32 | 11-前端体验 | ① EventSource 断线重连<br>② Markdown 渲染<br>③ 拖拽上传 | ✅ 打字机效果<br>✅ 代码高亮 + 复制 |
| D33-D35 | 12-前端性能 | ① 路由懒加载<br>② Service Worker 缓存<br>③ Web Vitals 监控 | ✅ Lighthouse > 90<br>✅ LCP < 2.5s |
| D36-D37 | 13-状态管理 | ① Zustand Store<br>② 状态持久化 | ✅ 刷新后状态保持<br>✅ 类型安全 |
| D38-D40 | 19-通知中心 | ① 站内信<br>② 邮件通知<br>③ Webhook | ✅ 配额告警通知<br>✅ 支付成功通知 |

### 阶段 5：性能与功能增强（第 7-8 周）

**目标**：后端性能、RAG 能力、工作流可视化

| 天数 | 方案 | 关键任务 | 验收标准 |
|------|------|----------|----------|
| D41-D43 | 16-后端性能 | ① N+1 查询优化<br>② 多级缓存<br>③ 文档解析异步化 | ✅ 查询 < 500ms<br>✅ 缓存命中 > 80% |
| D44-D46 | 09-高级 RAG v2.0 | ① RRF 融合<br>② 评估 API | ✅ MRR 提升 > 10%<br>✅ P99 < 300ms |
| D47-D49 | 08-工作流 | ① React Flow 前端<br>② SSE 推送<br>③ 步骤埋点 | ✅ DAG 实时渲染<br>✅ 步骤状态实时更新 |

### 阶段 6：完善功能（第 9-10 周）

**目标**：知识库、市场、管理后台

| 天数 | 方案 | 关键任务 | 验收标准 |
|------|------|----------|----------|
| D50-D52 | 06-知识库 | ① 版本快照<br>② 权限管理<br>③ 分享链接 | ✅ 回滚到历史版本<br>✅ 分享 7 天自动过期 |
| D53-D56 | 07-Agent 市场 | ① 发布/审核流程<br>② 订阅/评分<br>③ 收益结算 | ✅ 创作者收益 80%<br>✅ 热度排序正确 |
| D57-D60 | 10-管理后台 | ① 租户管理<br>② 审计日志<br>③ 超管 IP 白名单 | ✅ 超管可跨租户查询<br>✅ 30 天日志自动归档 |

### 阶段 7：国际化与合规（第 11 周，可选）

**目标**：国际化、数据处理、审计合规

| 天数 | 方案 | 关键任务 | 验收标准 |
|------|------|----------|----------|
| D61-D62 | 17-国际化 | ① react-i18next<br>② 语言切换 | ✅ 中英文切换<br>✅ 时区自适应 |
| D63-D65 | 18-数据导入导出 | ① Excel 导入<br>② 异步导出 | ✅ 1000 条 < 10s<br>✅ 10000 条 < 30s |
| D66-D67 | 20-审计合规 | ① 审计日志增强<br>② GDPR 数据删除 | ✅ 操作可追溯<br>✅ 数据可彻底删除 |

---

**总工期**：67 天（约 13 周）  
**并行优化后**：约 35-40 天（7-8 周，不含可选的国际化与合规）

---

## 🎯 里程碑

### M0 — 质量保障基础（第 1-2 周结束）
- ✅ 测试金字塔（单元 80% + 集成 15% + E2E 5%）
- ✅ CI/CD 自动化（PR 自动测试）
- ✅ 异常处理统一（熔断、降级、重试）
- ✅ 数据一致性保障（Saga、幂等性、乐观锁）
- **验收标准**：覆盖率 > 80%，所有 API 有统一错误响应

### M1 — 地基完成（第 4 周结束）
- ✅ 多租户隔离生效（TenantContext + RLS）
- ✅ SimpleMeterRegistry 监控上线（自研管理后台）
- **验收标准**：创建 2 个租户，验证数据互不可见

### M2 — 核心上线（第 6 周结束）
- ✅ 用户注册/登录/试用到期
- ✅ ACL 强制阻断 + 沙箱加固
- **验收标准**：注册用户自动获得 14 天试用，到期后只读

### M3 — 计费上线（第 7 周结束）
- ✅ 套餐订阅 + 支付回调（幂等）
- ✅ 配额强制（token/存储/调用次数）
- **验收标准**：订阅后配额生效，超配额被拦截

### M4 — 体验优化（第 8 周结束）
- ✅ 前端交互体验（流式对话、Markdown、拖拽上传）
- ✅ 前端性能优化（Lighthouse > 90）
- ✅ 状态管理（Zustand）
- ✅ 通知中心（站内信、邮件、Webhook）
- **验收标准**：AI 对话打字机效果，配额告警收到通知

### M5 — 性能增强（第 9 周结束）
- ✅ 后端性能优化（N+1 查询、多级缓存、异步处理）
- ✅ 高级 RAG v2.0（RRF 融合）
- ✅ 工作流可视化
- **验收标准**：查询 < 500ms，MRR 提升 > 10%

### M6 — MVP 完成（第 10 周结束）
- ✅ 知识库增强 + Agent 市场 + 管理后台
- **验收标准**：完整 SaaS 产品可对外发布

### M7 — 国际化与合规（第 11 周结束，可选）
- ⚠️ 多语言支持（中英文）
- ⚠️ 数据导入导出
- ⚠️ 审计合规（GDPR、SOC2）
- **验收标准**：满足企业客户合规要求

---

## ⚠️ 风险与缓解

| 风险 | 影响 | 概率 | 缓解措施 |
|------|------|------|----------|
| **50 张表加 tenant_id** | 生产加锁时间长 | 高 | 凌晨维护窗口，分批执行（P0 18 张优先） |
| **RLS 性能下降** | 查询慢 5-10% | 中 | 监控 P99 延迟，必要时禁用 RLS 改应用层过滤 |
| **支付回调重放攻击** | 资金损失 | 中 | 签名验证 + 幂等 + IP 白名单 |
| **SimpleMeterRegistry 内存** | 内存占用增加 | 低 | 定期清理历史指标（保留 1 小时） |
| **工作流 SSE 并发** | > 1000 并发掉线 | 低 | 监控连接数，> 800 提前告警切换 WebSocket |
| **密码迁移失败** | 用户无法登录 | 低 | 迁移前备份 t_user，失败立即回滚 |

---

## 📊 质量指标

### 代码级审查
- ✅ 所有方案基于真实代码路径（文件名 + 行号）
- ✅ DDL 表结构与现有 Schema 匹配
- ✅ Service 骨架引用真实 Port/Adapter 接口
- ✅ Controller API 遵循现有风格（`Map<String, Object>` 返回值）

### 可落地性
- ✅ 每份方案包含完整 DDL + API + Service 实现
- ✅ Checkbox 清单可直接转 Issue/PR
- ✅ 依赖关系明确（无循环依赖）

### 决策完整性
- ✅ 无待确认项（全部转为明确架构决策）
- ✅ 技术选型有理由（性能/成本/生态）
- ✅ 风险前置识别，缓解措施具体

---

## 🚀 快速开始

### 1. 从哪开始？

**推荐顺序（质量优先）**：
```
21 → 14 → 15 → 01 → 05 → 03 → 02 → 04 → 11 → 19 → 16 → 09 → 08 → 06 → 07 → 10
```

**原因**：
- **21-测试策略**：先建立质量保障体系
- **14-异常处理 + 15-数据一致性**：核心健壮性基础
- **01-多租户**：地基，03/04/06/07/10 都依赖它
- **05-监控**：可并行（无依赖）
- **11-前端体验 + 19-通知中心**：提升用户体验
- **16-性能优化**：在功能完整后优化

### 2. 第一步做什么？

**立即开始 21-测试策略**：
```bash
# 1. 创建分支
git checkout -b feature/testing-strategy

# 2. 配置 JaCoCo
# 见 21-testing-strategy.md § 3.1.4

# 3. 编写第一个单元测试
# 见 21-testing-strategy.md § 3.1.2

# 4. 配置 CI/CD
# 见 21-testing-strategy.md § 3.6
```

**或从核心功能开始（传统路线）**：
```bash
# 1. 创建分支
git checkout -b feature/multi-tenancy

# 2. 执行 DDL（P0 18 张表）
psql -h localhost -U postgres -d seahorse_agent -f scripts/multi-tenancy-ddl.sql

# 3. 实现 TenantContext
# 见 01-multi-tenancy.md § 3.1

# 4. 验证隔离
# 创建 2 个租户，验证数据互不可见
```

### 3. 如何跟踪进度？

每份方案末尾都有 **Checkbox 清单**，可直接转为 GitHub Issue：

```markdown
# Issue 模板
- [ ] DDL 执行（5 张表）
- [ ] Port 接口定义
- [ ] Adapter 实现
- [ ] Service 编排
- [ ] Controller API
- [ ] 单元测试（覆盖率 > 80%）
- [ ] 集成测试
- [ ] 文档更新
```

---

## 📞 支持

**方案作者**：架构组  
**审查日期**：2026-06-05  
**代码基线**：`seahorse-agent` @ 2026-06-04  
**版本**：v2.0-final（含功能增强方案）

### 完整方案清单（21 份）

**核心功能（01-10）**：
- [01-多租户隔离](01-multi-tenancy.md) — SaaS 地基
- [02-安全 P0 加固](02-security-hardening-p0.md) — 四大安全缺口修复
- [03-用户体系](03-user-system.md) — 注册/登录/试用
- [04-计费系统](04-billing.md) — 套餐/支付/配额
- [05-运维监控](05-observability.md) v3.0 — SimpleMeterRegistry + 自研后台
- [06-知识库增强](06-knowledge-base-enhancement.md) — 版本/权限/分享
- [07-Agent 市场](07-agent-marketplace.md) — 发布/订阅/收益
- [08-工作流可视化](08-workflow-visualization.md) — React Flow + SSE
- [09-高级 RAG](09-advanced-rag.md) v2.0 — RRF 融合优先
- [10-管理后台](10-admin-ops.md) — 超管/审计/运营

**功能增强（11-21）**：
- [11-前端交互体验增强](11-frontend-ux-enhancement.md) — 流式对话/Markdown/拖拽上传
- [12-前端性能优化](12-frontend-performance.md) — 代码分割/缓存策略/Web Vitals
- [13-前端状态管理增强](13-frontend-state-management.md) — Zustand/持久化/DevTools
- [14-异常处理与容错](14-error-handling-resilience.md) — 统一异常/熔断/降级
- [15-数据一致性保障](15-data-consistency.md) — Saga/幂等性/并发控制
- [16-后端性能优化](16-backend-performance-tuning.md) — SQL 优化/多级缓存/异步处理
- [17-多语言国际化](17-internationalization.md) — i18n/时区/货币格式化
- [18-数据导入导出](18-data-import-export.md) — Excel 导入/异步导出/模板下载
- [19-通知与消息中心](19-notification-center.md) — 站内信/邮件/Webhook
- [20-审计与合规](20-audit-compliance.md) — 操作审计/数据追踪/GDPR 合规
- [21-测试策略与实践](21-testing-strategy.md) — 单元/集成/E2E/压测

---

## 💡 关键优化亮点

### 成本优化
- ✅ **-¥30,000/年**：RRF 替代 Rerank API + SimpleMeterRegistry 替代 Grafana
- ✅ **零外部依赖**：无外部 API、无外部付费组件

### 工期优化
- ✅ **-6 天**：RRF（-1 天）+ SimpleMeterRegistry（-1 天）+ 质量保障前置（-4 天返工）

### 性能提升
- ✅ **查询性能**：3s → 0.3s（N+1 优化，-91%）
- ✅ **缓存命中率**：0% → 80%+（多级缓存）
- ✅ **接口响应**：26s → 1s（异步处理，-96%）
- ✅ **RAG 延迟**：800ms → 300ms（RRF 无外部 API）

### 质量保障
- ✅ **测试覆盖率**：< 30% → > 80%
- ✅ **回归风险**：高 → 低（自动化测试）
- ✅ **数据一致性**：无保障 → Saga + 幂等性
- ✅ **异常处理**：不统一 → 统一响应格式

### 用户体验
- ✅ **流式对话**（EventSource 断线重连）
- ✅ **打字机效果**（逐字符显示 AI 回复）
- ✅ **Markdown 渲染**（代码高亮 + 一键复制）
- ✅ **前端性能**：Lighthouse 45 → 90+（代码分割、懒加载）
- ✅ **状态持久化**（刷新页面不丢失，Zustand）
- ✅ **多语言支持**（中英文切换，i18n）

### 企业功能
- ✅ **数据导入导出**（Excel/CSV，支持 10 万条）
- ✅ **审计合规**（操作可追溯，GDPR 合规）
- ✅ **通知系统**（站内信 + 邮件 + Webhook）

---

*本文档基于 21 份方案（共 12,500+ 行 / 700KB）汇总生成，所有架构决策已定稿。*

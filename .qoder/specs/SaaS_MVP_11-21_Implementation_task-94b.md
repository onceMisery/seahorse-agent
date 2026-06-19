# SaaS MVP 功能增强 (11-21) + 数据持久化迁移

## Context

当前项目 `seahorse-agent` 是一个企业级智能体平台，采用六边形架构（Port/Adapter）。前端已完整实现流式对话、Markdown 渲染、Zustand 状态管理等核心能力。但后端在异常处理、数据一致性、性能优化等方面存在显著短板，且数据访问层使用 JdbcTemplate 存在工程化局限性。

**需要实现的内容**：
- 11-21 号方案中**真正缺失**的功能（已实现的 SSE/Markdown/Zustand 跳过）
- 将 `seahorse-agent-adapter-repository-jdbc` 模块从 JdbcTemplate 迁移到 MyBatis Plus

**用户决策**：
- 迁移范围：仅迁移 adapter-repository-jdbc 模块
- 已有功能：跳过已实现的，只补缺失
- 实施策略：按优先级分批实施

---

## Phase 1: P0 — 后端核心健壮性 (预估 8-9 天)

### Task 1.1: 异常处理与容错 (Plan 14)

**目标**：统一异常体系 + Resilience4j 熔断降级 + 超时控制

**修改文件**：
- `seahorse-agent-kernel/src/.../domain/common/` — 新增异常基类和子类
  - `SeahorseBusinessException.java` (抽象基类)
  - `ResourceNotFoundException.java`
  - `QuotaExceededException.java`
  - `InvalidInputException.java`
  - `ExternalServiceException.java`
  - `DatabaseTimeoutException.java`
- `seahorse-agent-kernel/src/.../domain/common/ErrorResponse.java` — 统一错误响应 record
- `seahorse-agent-adapter-web/src/.../web/SeahorseGlobalExceptionHandler.java` — 增强全局异常处理
- `seahorse-agent-adapter-ai-openai-compatible/src/...` — ResilientChatModelAdapter (装饰器)
- `seahorse-agent-bootstrap/src/main/resources/application.yml` — 统一超时 + Resilience4j 配置
- `seahorse-agent-kernel/pom.xml` — 添加 resilience4j-spring-boot3 依赖
- 新增 `AsyncExecutorConfiguration.java` — 统一线程池配置

**关键步骤**：
1. 创建异常类层级结构 (`kernel/domain/common/exception/`)
2. 创建 `ErrorResponse` record
3. 增强 `SeahorseGlobalExceptionHandler`（加 @Order(1)，处理 BusinessException/Timeout/SQL/Validation）
4. 引入 Resilience4j 依赖并配置 CircuitBreaker/TimeLimiter/Retry/RateLimiter
5. 实现 `ResilientChatModelAdapter`（三级降级：缓存→简化回复→快速失败）
6. 配置统一超时参数（application.yml）
7. 创建 `AsyncExecutorConfiguration`（aiExecutor/notificationExecutor/taskExecutor）

**验证**：
- Mock AI 超时 30s，验证 5s 内降级返回
- 触发 10 次故障后熔断器打开
- 所有 API 错误响应格式统一 `{code, message, timestamp, path}`

---

### Task 1.2: 数据一致性保障 (Plan 15)

**目标**：轻量级事务保障 + 幂等性 + 并发控制 + Bean Validation

**修改/新增文件**：
- `resources/database/` — DDL 迁移脚本
  - `V13__compensation_log.sql` — 补偿日志表
- `seahorse-agent-kernel/src/.../domain/` — 领域层增强
  - 实体类添加 `@Version` 乐观锁字段
- `seahorse-agent-adapter-repository-jdbc/src/...` — Repository 增强
  - 悲观锁查询方法 (`SELECT ... FOR UPDATE`)
- 新增 Service 层
  - `CompensationRetryJob.java` — 定时补偿重试
  - `IdempotencyService.java` — 幂等性检查（Redis SET NX）
- `seahorse-agent-adapter-web/src/...` — Controller 层
  - 所有写接口添加 `@Valid` 注解
  - Request DTO 添加 Bean Validation 注解

**关键步骤**：
1. 创建 `t_compensation_log` 表 DDL
2. 实现事务策略（DB操作事务化 + 外部调用异步化）
3. 实现幂等性服务（Redis SET NX + 数据库唯一索引双重保障）
4. 实现乐观锁（实体 version 字段 + UPDATE WHERE version = ?）
5. 实现悲观锁（配额扣减 SELECT FOR UPDATE）
6. 实现分布式锁（Redisson RLock for 支付等关键操作）
7. 添加 Bean Validation 到所有 Request DTO
8. 增强 GlobalExceptionHandler 处理 `MethodArgumentNotValidException`

**验证**：
- 注册场景：租户创建失败时用户记录自动回滚
- 支付场景：相同 Idempotency Key 调用 3 次只扣款 1 次
- 配额场景：10 并发同时扣减，最终余额准确

---

## Phase 2: P1 — 持久化迁移 + 后端性能 (预估 8-10 天)

### Task 2.1: JdbcTemplate → MyBatis Plus 迁移

**目标**：将 `seahorse-agent-adapter-repository-jdbc` 模块从 JdbcTemplate 替换为 MyBatis Plus

**修改文件**：
- `seahorse-agent-adapter-repository-jdbc/pom.xml` — 替换依赖
- `seahorse-agent-adapter-repository-jdbc/src/main/java/.../` — 所有 Adapter 实现类
- 新增 `src/main/java/.../mapper/` — MyBatis Plus Mapper 接口
- 新增 `src/main/java/.../entity/` — DO (Data Object) 实体类
- `seahorse-agent-bootstrap/src/main/resources/application.yml` — MyBatis Plus 配置

**关键步骤**：
1. 修改 pom.xml：移除手动 JdbcTemplate 依赖，添加 mybatis-plus-spring-boot3-starter
2. 为每张表创建 DO 实体类（@TableName, @TableId, @TableField）
3. 为每个实体创建 Mapper 接口（extends BaseMapper<XxxDO>）
4. 重写所有 Adapter 实现类：JdbcTemplate → Mapper 调用
5. 保持 Port 接口不变（六边形架构 adapter 层替换，不影响 domain）
6. 配置 MyBatis Plus（分页插件、乐观锁插件、字段自动填充）
7. 逐个 Adapter 验证功能正确性

**技术决策**：
- 选择 MyBatis Plus 原因：项目 pom 已声明、团队熟悉度高、与 JdbcTemplate 同属 SQL 优先方案迁移成本低、性能与 JdbcTemplate 持平
- 不选 JPA/Hibernate：项目已有大量手写 SQL、六边形架构 Adapter 层不适合 JPA 自动生成、控制粒度不够

**验证**：
- 全部现有 API 功能不变
- 执行 `mvn test` 通过所有单元测试
- 手动测试核心流程（创建知识库→上传文档→对话）

---

### Task 2.2: 后端性能优化 (Plan 16)

**目标**：N+1 查询优化 + 多级缓存 + 异步处理 + 批量操作

**修改/新增文件**：
- `seahorse-agent-adapter-repository-jdbc/src/...` — SQL 优化（JOIN/批量查询）
- `seahorse-agent-kernel/pom.xml` — 添加 caffeine 依赖
- 新增 `CaffeineConfiguration.java` — 本地缓存配置
- 新增 `MultiLevelCacheService.java` — 多级缓存封装（Caffeine L1 + Redis L2）
- 新增 `CacheWarmupJob.java` — 缓存预热
- `seahorse-agent-bootstrap/src/main/resources/application.yml` — HikariCP 调优参数
- 文档上传 Controller — 改为异步处理（消息队列）
- `resources/database/` — 新增索引 DDL

**关键步骤**：
1. 审查并修复 N+1 查询（改为 JOIN 或批量 IN 查询）
2. 新增复合索引（tenant_id + created_at 等高频查询条件）
3. 实现游标分页（替代 OFFSET 大分页）
4. 实现 Caffeine + Redis 多级缓存 + Pub/Sub 多实例同步失效
5. 文档上传接口异步化（立即返回 + 后台解析）
6. 批量插入优化（MyBatis Plus saveBatch）
7. HikariCP 连接池参数调优
8. 集成 datasource-proxy 慢 SQL 检测

**验证**：
- 会话列表查询 < 500ms
- 缓存命中率 > 80%（通过 Caffeine stats）
- 文档上传接口响应 < 1s

---

### Task 2.3: 测试策略与实践 (Plan 21)

**目标**：建立测试金字塔，覆盖率 > 80%

**修改/新增文件**：
- `pom.xml` (root) — 添加 JaCoCo 插件 + Testcontainers 依赖
- `seahorse-agent-tests/pom.xml` — 添加 testcontainers-postgresql
- `seahorse-agent-tests/src/test/java/` — 单元测试 + 集成测试
- `frontend/package.json` — 确认 vitest 配置
- `frontend/e2e/` — Playwright E2E 测试
- `.github/workflows/test.yml` — CI/CD 配置

**关键步骤**：
1. 配置 JaCoCo 插件（覆盖率 > 80% 强制检查）
2. 编写核心 Service 单元测试（QuotaService, UserService, ChatService）
3. 配置 Testcontainers（PostgreSQL 容器）
4. 编写 Repository 层集成测试
5. 补充前端 Hook/Store 测试
6. 编写 3 条 E2E 核心路径（Playwright）
7. 配置 GitHub Actions CI

**验证**：
- `mvn test` 全绿 + JaCoCo 报告生成
- 前端 `npm test` 通过
- CI pipeline 可运行

---

## Phase 3: P1 — 功能增强 (预估 8-9 天)

### Task 3.1: 数据导入导出 (Plan 18)

**目标**：Excel 批量导入 + 异步导出 + 模板下载

**新增文件**：
- DDL: `t_export_task` 表
- 后端: `DocumentImportController`, `DocumentExportController`, `DocumentImportService`, `DocumentImportValidator`, `ExportConfig`
- 前端: `DataImport.tsx`, `useExport.ts` hook

**关键步骤**：
1. 创建 `t_export_task` 表
2. 引入 Apache POI (已有 Tika 依赖，POI 可复用)
3. 实现导入：Excel 解析 → 校验 → 批量插入
4. 实现异步导出：创建任务 → 后台分页查询 → SXSSFWorkbook 流式写入 → 进度更新
5. 实现模板下载 API
6. 前端实现上传组件 + 进度轮询

---

### Task 3.2: 通知与消息中心 (Plan 19)

**目标**：站内信 + 邮件通知 + Webhook + 消息模板

**新增文件**：
- DDL: `t_notification`, `t_webhook`, `t_webhook_log`, `t_notification_template`, `t_notification_preference`
- 后端: `NotificationPort`, `DatabaseNotificationAdapter`, `EmailNotificationService`, `WebhookService`, `NotificationTemplateService`, `NotificationCleanupJob`
- 前端: `NotificationBell.tsx`

**关键步骤**：
1. 创建所有通知相关表
2. 定义 `NotificationPort` 接口
3. 实现站内信（CRUD + 已读/未读）
4. 实现邮件通知（Spring Mail + Thymeleaf 模板）
5. 实现 Webhook（HMAC-SHA256 签名 + 重试）
6. 实现消息模板引擎（变量替换）
7. 实现通知偏好设置
8. 实现站内信清理定时任务（90 天已读自动清理）
9. 前端实现通知铃铛组件（Badge + Dropdown）

---

### Task 3.3: 前端缺失功能补齐 (Plan 11/12 Gaps)

**目标**：路由懒加载 + Service Worker + Web Vitals + 骨架屏

**修改文件**：
- `frontend/src/router.tsx` — lazy() 路由懒加载
- `frontend/vite.config.ts` — vite-plugin-pwa 配置
- `frontend/src/utils/performance.ts` — Web Vitals 监控
- `frontend/src/components/common/PageSkeleton.tsx` — 骨架屏组件

**关键步骤**：
1. 路由懒加载：将 `router.tsx` 中的直接 import 改为 `React.lazy()`
2. 安装 `vite-plugin-pwa`，配置 Service Worker 和 Workbox 运行时缓存
3. 安装 `web-vitals`，实现 CLS/INP/FCP/LCP/TTFB 上报
4. 创建页面级骨架屏组件
5. 打包优化：配置 manualChunks 分包策略

**验证**：
- Lighthouse Performance > 80
- Service Worker 注册成功
- 路由按需加载（Network 面板验证）

---

## Phase 4: P2 — 国际化与合规 (预估 4-5 天)

### Task 4.1: 多语言国际化 (Plan 17)

**目标**：前端 react-i18next + 后端 MessageSource

**新增文件**：
- `frontend/src/i18n/` — i18next 配置 + 语言包 (zh-CN.json, en-US.json)
- `frontend/src/components/common/LanguageSwitcher.tsx`
- 后端 `resources/i18n/messages_zh_CN.properties`, `messages_en_US.properties`
- 后端 `I18nConfiguration.java` + `HeaderLocaleResolver.java`

**关键步骤**：
1. 安装 react-i18next + i18next-browser-languagedetector
2. 创建中英文语言包（提取所有硬编码文案）
3. 全局替换硬编码文案为 `t('key')`
4. 实现语言切换器组件
5. 后端 MessageSource 配置 + 自定义 HeaderLocaleResolver
6. 前端 dayjs 时区处理 + Intl.NumberFormat 货币格式化

---

### Task 4.2: 审计与合规 (Plan 20)

**目标**：AOP 审计 + 登录历史 + GDPR 数据删除

**新增文件**：
- DDL: ALTER `sa_audit_log` (增加 changes/user_agent/geo_location 列), `t_login_history`
- 后端: `@Auditable` 注解, `AuditAspect`, `LoginEventListener`, `AuditLogExportController`, `AuditLogArchiveJob`, `GdprService`

**关键步骤**：
1. 增强 `sa_audit_log` 表结构
2. 创建 `t_login_history` 表
3. 实现 `@Auditable` 注解 + AOP 拦截器
4. 在所有敏感操作 Controller 方法上标注 `@Auditable`
5. 实现登录历史记录（设备/IP/地理位置）
6. 实现审计日志导出（CSV）
7. 实现日志归档（90 天定时归档到 S3）
8. 实现 GDPR 数据删除（匿名化 + 彻底删除）

---

## 依赖关系

```
Phase 1 (P0): Task 1.1 → Task 1.2 (异常类被一致性方案引用)
Phase 2 (P1): Task 2.1 独立 | Task 2.2 依赖 Task 2.1 | Task 2.3 可并行
Phase 3 (P1): Task 3.1/3.2/3.3 独立可并行
Phase 4 (P2): Task 4.1/4.2 独立可并行
```

## 技术决策：MyBatis Plus vs JdbcTemplate

| 维度 | JdbcTemplate (现状) | MyBatis Plus (推荐) |
|------|-------|-------|
| 代码量 | 大量模板代码 | CRUD 零代码 |
| SQL 控制 | 完全手写 | 简单 CRUD 自动 + 复杂 SQL 手写 |
| 分页 | 手动实现 | 内置分页插件 |
| 乐观锁 | 手动实现 | 内置插件 @Version |
| 批量操作 | BatchPreparedStatementSetter | saveBatch() |
| 类型安全 | 无 | LambdaQueryWrapper |
| 迁移成本 | - | 低（同属 SQL 优先方案） |
| 与六边形架构兼容性 | 好 | 好（Mapper 在 adapter 层） |

**结论**：MyBatis Plus 在保持 SQL 灵活性的同时大幅降低模板代码量，且项目 pom 已声明依赖，迁移成本最低。

## 验证策略

### 每个 Task 完成后
1. `mvn compile` — 编译通过
2. `mvn test` — 单元测试通过
3. 手动测试受影响的 API 端点

### Phase 完成后
4. 全量集成测试（Testcontainers）
5. 前端 `npm run build` 无报错
6. 核心流程 E2E（注册→创建知识库→上传文档→对话）

### 最终验证
7. `mvn verify` — 全部测试 + 覆盖率检查
8. Lighthouse 评分 > 80
9. 压测：100 并发 TPS > 500

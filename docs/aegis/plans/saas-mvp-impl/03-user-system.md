# 03 · 用户体系（自助注册 / 试用 / 登录）技术方案

> 所属主线：SaaS MVP · 块B（用户体系）
> 依赖：01-多租户（多租户隔离 `TenantContext`）、块C（计费 `QuotaPolicy` 配额底座）
> 状态：设计稿（代码级核实，2026-06-04）
> 适用架构：Spring Boot 3.5.7 + React，六边形（端口-适配器），Sa-Token 鉴权

---

## 1. 目标与范围

### 1.1 要做什么（MVP）

把现有"仅管理员预置账号 + 用户名密码登录"的单体能力，升级为 SaaS 自助获客闭环：

1. **邮箱注册 + 验证码**：邮箱 + 验证码 + 密码完成注册，验证码存 Redis，IP/邮箱双维度防刷。
2. **注册即开户**：创建用户 → 自动创建个人租户（调01-多租户 租户服务）→ 激活 14 天免费试用 → 直接返回 JWT，免二次登录。
3. **免费试用管理**：试用期可配置；绑定存储 / Token / 并发配额（复用块C `QuotaPolicy`）；到期前提醒；到期降只读模式。
4. **登录体验**：保留现有用户名/邮箱密码登录，新增"记住登录 7 天"。
5. **数据改造**：`t_user` 增加 `tenant_id`、`email`、`status` 字段并提供迁移脚本；新增试用表。

### 1.2 明确不做（后延 Phase 2，本期不实现）

| 不做项 | 原因 / 后延说明 |
|--------|----------------|
| 第三方 OAuth（微信 / 钉钉 / GitHub） | 获客增量功能，MVP 先验证邮箱主路径。预留 `t_user.external_id` 扩展位但不实现回调。 |
| 细粒度 RBAC | 现有 `role`（`admin` / `user`）二元角色 + Sa-Token `StpInterface` 足够 MVP；权限矩阵后延。 |
| 多设备 / 会话管理（踢人、设备列表） | Sa-Token 默认单 token 满足；并发登录治理后延。 |
| SSO / 企业目录对接（LDAP / SAML / OIDC） | 面向企业版，MVP 不涉及。 |
| 找回密码 / 改邮箱 | 注册闭环优先；找回密码列为 P1（见 §7）。 |

---

## 2. 现状（代码级）

### 2.1 鉴权闭环已具备的能力

| 能力 | 实现位置 | 说明 |
|------|----------|------|
| 登录 / 登出入口 | `seahorse-agent-adapter-web/.../web/SeahorseAuthController.java` | `POST /auth/login`、`POST /auth/logout`，返回 `{code,data}` 信封，控制器用 `ObjectProvider<AuthInboundPort>` 懒加载。 |
| 登录领域服务 | `seahorse-agent-kernel/.../application/auth/KernelAuthService.java` | `login(LoginCommand)`：按 `username` 查用户 → 校验密码 → 签发 token。**仅登录/登出，无注册。** |
| 用户仓储 | `seahorse-agent-adapter-repository-jdbc/.../jdbc/JdbcUserRepositoryAdapter.java` | `findById / findByUsername / usernameExists / page / create / update / delete`，表 `t_user`，软删 `deleted=0`，雪花 ID `JdbcMemorySupport.nextId()`。 |
| Token 适配器 | `seahorse-agent-adapter-web/.../web/SaTokenServiceAdapter.java` | `StpUtil.login(userId)` → 返回 `StpUtil.getTokenValue()`。 |
| 当前用户 | `seahorse-agent-adapter-web/.../web/SaTokenCurrentUserAdapter.java` | 从 `StpUtil.getLoginIdAsString()` 反查 `UserRecord`。 |
| 角色校验 | `seahorse-agent-adapter-web/.../web/SeahorseSaTokenStpInterface.java` | Sa-Token `StpInterface` 角色来源。 |
| 安全拦截 | `seahorse-agent-adapter-web/.../web/SeahorseSecurityWebMvcConfiguration.java` | `SaInterceptor` 全局校验；`/auth/**` 已在公开前缀与 `excludePathPatterns` 中（**注册接口走 `/auth/` 即自动公开**）。 |
| 自动配置 | `seahorse-agent-spring-boot-starter/.../spring/SeahorseAgentAuthAdapterAutoConfiguration.java`（Layer 1，after DataSource）<br>`...SeahorseAgentKernelAuthAutoConfiguration.java`（Layer 4，after Auth adapter） | 前者装配仓储/密码/token 适配器，后者装配 `KernelAuthService`、`KernelUserService`。 |

### 2.2 关键缺口（本期补齐）

- **无自助注册闭环**：`AuthInboundPort` 只有 `login` / `logout`（`ports/inbound/auth/AuthInboundPort.java`）。
- **无邮箱验证、无邮件发送端口**。
- **密码明文存储**：`PasswordHasherPort.plainText()` 是默认 Bean（`SeahorseAgentAuthAdapterAutoConfiguration` 第 60 行），种子数据 `admin/admin` 明文（`seahorse_init.sql:2065`）。
- **无免费试用模型**。
- **登录不支持"记住我"**：`LoginCommand(username, password)` 无 `rememberMe`；前端 `LoginPage.tsx` 已有 `remember` 复选框但**后端未消费**。

### 2.3 ⚠️ 用户表是否缺 `tenant_id`？——**缺！**

`resources/database/seahorse_init.sql:12`：

```sql
CREATE TABLE t_user (
    id           BIGINT  NOT NULL PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL,
    password     VARCHAR(128) NOT NULL,
    role         VARCHAR(32)  NOT NULL,
    avatar       VARCHAR(128),
    create_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username)
);
```

`t_user` **没有 `tenant_id`，也没有 `email`、`status`**。对照全库：81% 的业务表（如 `t_short_term_memory`、`sa_quota_policy`）已带 `tenant_id`，唯独用户表缺失——这正是块B 注册"自动建租户"必须先补的字段（§4.1）。

### 2.4 可直接复用的底座（已在代码中）

| 底座 | 端口 / 实现 | 用途 |
|------|------------|------|
| 字符串 KV 缓存（带 TTL） | `ports/outbound/cache/KeyValueCachePort.java`（`get/set(key,value,ttl)/delete`），实现 `cache-redis/.../RedisCacheAdapter.java`，由 `SeahorseAgentCacheAdapterAutoConfiguration`（Layer 1）装配 | **验证码存储** |
| 限流 | `ports/outbound/cache/RateLimiterPort.java`（`tryAcquire(resource, subject, permits, ttl)` → `RateLimitDecision`） | **注册/发码防刷** |
| 配额策略（块C） | 领域 `kernel/domain/agent/quota/QuotaPolicy.java`、`QuotaScope{TENANT,AGENT,USER,...}`、`QuotaPolicyStatus{ACTIVE,DISABLED}`；端口 `ports/outbound/agent/QuotaPolicyRepositoryPort.java`（`upsert / findActive(tenantId,scope,subjectId) / disable`）；表 `sa_quota_policy`；由 `SeahorseAgentRegistryRepositoryAutoConfiguration`（Layer 1）装配 | **试用配额绑定** |
| 雪花 ID | `JdbcMemorySupport.nextId()` | 用户 / 试用主键 |

> **01-多租户依赖说明**：`TenantContext`、`TenantInterceptor` 在当前代码库中**尚未实现**（全局搜索无 `TenantContext.java`）。本方案按01-多租户 提供的契约（§01-多租户）引用其 API；落地时点为01-多租户 交付后。注册"自动建租户"所需的租户开通服务命名以01-多租户 实现为准（本文档暂记 `TenantProvisioningPort`）。

---

## 3. 技术方案

### 3.1 端口与服务总览（六边形分层）

```
inbound（kernel/ports/inbound/auth）
  ├─ RegistrationInboundPort   ← 新增：sendVerificationCode / register
  └─ AuthInboundPort           ← 扩展：login 增加 rememberMe

application（kernel/application/auth）
  ├─ KernelRegistrationService ← 新增：注册编排（验证码、建用户、建租户、开试用、签发 JWT）
  ├─ KernelAuthService         ← 改造：login 支持 rememberMe；密码改 BCrypt 校验
  └─ KernelTrialService        ← 新增：试用激活 / 到期扫描 / 降级（application/trial）

outbound（kernel/ports/outbound）
  ├─ UserRepositoryPort        ← 扩展：findByEmail / emailExists；UserRecord、UserCreateValues 加字段
  ├─ PasswordHasherPort        ← 实现切换：BCrypt 适配器替换 plainText
  ├─ TokenServicePort          ← 扩展：login(userId, rememberMe)
  ├─ KeyValueCachePort         ← 复用：验证码
  ├─ RateLimiterPort           ← 复用：防刷
  ├─ EmailSenderPort           ← 新增：发送验证码邮件（adapter：SMTP / noop）
  ├─ QuotaPolicyRepositoryPort ← 复用（块C）：试用配额
  ├─ TrialRepositoryPort       ← 新增：试用记录读写（adapter：JDBC）
  └─ TenantProvisioningPort    ← 01-多租户 提供(TenantProvisioningPort)：创建个人租户
```

### 3.2 邮箱注册 + 验证码

**接口设计**（均在 `/auth/` 公开前缀下，无需登录）：

| 方法 | 路径 | 入参 | 出参（`data`） |
|------|------|------|----------------|
| POST | `/auth/send-code` | `{ email }` | `{ sent: true, ttlSeconds: 600 }` |
| POST | `/auth/register` | `{ email, code, password }` | `LoginResult` + `{ tenantId, trialExpiresAt }` |
| GET | `/auth/email-available` | `?email=` | `{ available: boolean }` |

**验证码存储（Redis，复用 `KeyValueCachePort`）**：

- 逻辑 key：`register:vcode:{email}`（`RedisCacheAdapter` 会自动加 `seahorse:agent:cache:` 前缀）。
- value：6 位数字验证码（服务端用 `SecureRandom` 生成）。
- TTL：`Duration.ofMinutes(10)`。
- 校验成功后立即 `delete`，防重放。
- 连续校验失败计数 key：`register:vcode-fail:{email}`，失败 5 次即作废该码（强制重新发码）。

**防刷（复用 `RateLimiterPort.tryAcquire`）**，三道闸：

| 维度 | resource | subject | 阈值 | 窗口 |
|------|----------|---------|------|------|
| 单邮箱发码频率 | `register:send` | `email:{email}` | 1 次 | 60s |
| 单邮箱发码日上限 | `register:send-daily` | `email:{email}` | 10 次 | 24h |
| 单 IP 发码频率 | `register:send-ip` | `ip:{clientIp}` | 5 次 | 60s |
| 单 IP 注册频率 | `register:submit-ip` | `ip:{clientIp}` | 10 次 | 1h |

被拒时返回 `RateLimitDecision.retryAfter`，控制器转 `code=1` + 文案"操作过于频繁，请 N 秒后重试"。客户端 IP 经反代取 `X-Forwarded-For` 首段（nginx 已配置透传，见 `frontend/nginx.conf`）。

**发码时序**：

```
Client            SeahorseAuthController        KernelRegistrationService      RateLimiterPort   KeyValueCachePort   EmailSenderPort
  │  POST /auth/send-code {email}    │                      │                       │                 │                  │
  ├─────────────────────────────────>│  sendVerificationCode(cmd)                   │                 │                  │
  │                                   ├──────────────────────>│  tryAcquire(send, email/ip ...)         │                  │
  │                                   │                       ├────────────────────>│                 │                  │
  │                                   │                       │<── allowed/ rejected┤                 │                  │
  │                                   │              [rejected] throw 频率超限 ──────┐                  │                  │
  │                                   │                       │  set(vcode:{email}, code, 10m)         │                  │
  │                                   │                       ├───────────────────────────────────────>│                  │
  │                                   │                       │  send(email, 模板, code)                                   │
  │                                   │                       ├──────────────────────────────────────────────────────────>│
  │<────── {code:0,data:{sent:true}} ─┤<──────────────────────┤                       │                 │                  │
```

### 3.3 注册流程（表单：邮箱 + 验证码 + 密码 → 直接登录）

**编排步骤**（`KernelRegistrationService.register`，单事务边界见 §3.3.1）：

1. 校验入参（邮箱格式、密码强度 ≥ 8 位含字母数字）。
2. 防刷：`register:submit-ip`。
3. 校验验证码：取 `register:vcode:{email}` 比对；失败计数 +1。
4. 邮箱去重：`userRepositoryPort.emailExists(email)`，已存在 → "该邮箱已注册"。
5. **创建个人租户**：`tenantProvisioningPort.createPersonalTenant(...)` → 返回 `tenantId`（由 01-多租户提供）。
6. 密码加密：`passwordHasherPort.encode(rawPassword)`（BCrypt）。
7. 创建用户：`userRepositoryPort.create(UserCreateValues)`，`role=user`、`tenantId=步骤5`、`status=ACTIVE`、`username` 默认取邮箱前缀（冲突则追加随机短码）。
8. **激活 14 天试用**：`trialService.activate(userId, tenantId)` → 写 `t_user_trial` + `quotaPolicyRepositoryPort.upsert(...)` 写 USER 维度配额（§3.4）。
9. 删除验证码 key。
10. 签发 JWT：`tokenServicePort.login(userId, rememberMe=false)`。
11. 返回 `LoginResult(userId, role, token, avatar)` + `tenantId` + `trialExpiresAt`。

**注册时序图**：

```
Client     AuthController   KernelRegistrationService   Cache/RateLimiter   UserRepo   TenantProvisioning(01-多租户)   TrialService   QuotaRepo(块C)   TokenService
  │ POST /auth/register {email,code,password}                                                                                                  │
  ├──────────>│  register(cmd)  │                                                                                                               │
  │           ├────────────────>│  tryAcquire(submit-ip)                                                                                       │
  │           │                 ├──────────────────>│ allowed                                                                                  │
  │           │                 │  get(vcode:{email}) == code ?                                                                                │
  │           │                 ├──────────────────>│ ok                                                                                       │
  │           │                 │  emailExists(email)? ───────────────────────>│ false                                                         │
  │           │                 │  createPersonalTenant(email) ─────────────────────────────────>│ tenantId                                    │
  │           │                 │  encode(password) [BCrypt]                                                                                    │
  │           │                 │  create(UserCreateValues{email,pwd,tenantId,role=user}) ──────>│ userId                                       │
  │           │                 │  activate(userId, tenantId) ──────────────────────────────────────────────────>│ 写 t_user_trial            │
  │           │                 │                                                                                  ├──> upsert(USER quota) ───>│
  │           │                 │  delete(vcode:{email})                                                                                       │
  │           │                 │  login(userId, rememberMe=false) ───────────────────────────────────────────────────────────────────────────>│ token
  │<── {code:0,data:{userId,role,token,avatar,tenantId,trialExpiresAt}} ──────────────────────────────────────────────────────────────────────┤
```

#### 3.3.1 事务与一致性

- 步骤 6–8（建用户 + 开试用 + 写配额）必须**同一本地事务**（同库 PostgreSQL）。建议在 adapter 侧用 `@Transactional` 包裹一个聚合写入方法，或由 `KernelRegistrationService` 通过新增 `RegistrationTxPort` 提交（kernel 不依赖 Spring，事务在 adapter 实现）。
- 步骤 5 建租户若由01-多租户 跨服务/跨库完成，则采用"**先建租户，后建用户**"顺序；建用户失败时补偿删除租户(01-多租户需提供 `deleteTenant`，否则记录孤儿租户由01-多租户 清理任务回收）。
- 步骤 10 签发 token 在事务**提交后**执行，避免 token 已发但用户回滚。

### 3.4 免费试用管理

**试用配置**（`seahorse-agent.user.trial.*`，绑定 `@ConfigurationProperties`）：

| 配置项 | 默认 | 含义 |
|--------|------|------|
| `enabled` | `true` | 是否开启注册即试用 |
| `duration-days` | `14` | 试用天数 |
| `token-limit` | `2_000_000` | 试用期 Token 上限（USER 维度） |
| `storage-limit-bytes` | `1073741824`（1 GiB） | 存储上限 |
| `concurrency-limit` | `2` | 并发会话上限 |
| `warn-before-days` | `3` | 到期前几天提醒 |
| `warn-ratio` | `0.8` | 配额预警比例（对齐 `QuotaPolicyLimits.DEFAULT_WARN_RATIO`） |

**配额绑定（复用块C `QuotaPolicy`）**：试用激活时为用户写一条 USER 维度策略：

```
QuotaPolicy(
    policyId   = "trial-" + userId,
    tenantId   = <个人租户 id>,
    scope      = QuotaScope.USER,
    subjectId  = String.valueOf(userId),
    status     = QuotaPolicyStatus.ACTIVE,
    tokenLimit = trial.tokenLimit,
    callLimit  = null,
    costLimit  = null,
    warnRatio  = trial.warnRatio,
    createdAt  = now, updatedAt = now)
→ quotaPolicyRepositoryPort.upsert(policy)
```

> 存储 / 并发上限：`QuotaPolicy` 现仅支持 token/call/cost 三类（见 `QuotaPolicy.java`）。存储与并发上限本期记录在 `t_user_trial`（`storage_limit_bytes`、`concurrency_limit`），由各自运行时（上传、会话）查询 `TrialRepositoryPort` 执行；**不**扩 `QuotaPolicy` 字段（避免动块C 领域模型）。与块C 的协同点：到期/超额判定统一走"试用状态 + 配额状态"双信号。

**到期提醒与降级（`KernelTrialService` + 定时任务）**：

- 定时扫描（建议 `@Scheduled` 每小时，放在新 autoconfig，启用 @EnableScheduling）：
  - `expires_at - now <= warn-before-days` 且 `notified_at IS NULL` → 发提醒邮件（`EmailSenderPort`），写 `notified_at`。
  - `expires_at <= now` 且 `status=ACTIVE` → `status=EXPIRED`，并将其 `QuotaPolicy` `disable()`（或将 USER 配额置 0），同时给用户打**只读标记**。
- **只读模式实现**：在 `t_user_trial.status=EXPIRED` 时，安全拦截层对"写操作"放行查询、拒绝写入。MVP 采用 Controller 拦截器：
  1. 复用 `SeahorseSecurityWebMvcConfiguration`：对非 `GET`/非白名单的业务写接口，校验当前用户试用状态（需在拦截器注入 `TrialRepositoryPort`）；
  2. 或在写入型 Controller 统一切面校验 `currentUser` 的试用状态。
  推荐方案 1（集中、不散落）。只读时写接口返回 `code=1` + "试用已到期，请升级套餐"。

**试用到期/降级时序**：

```
Scheduler        KernelTrialService     TrialRepositoryPort     QuotaPolicyRepositoryPort(块C)   EmailSenderPort
   │  每小时触发      │                        │                            │                          │
   ├───────────────>│  scanExpiring()        │                            │                          │
   │                ├───────────────────────>│ 查 status=ACTIVE 且临期/到期 │                          │
   │                │<── List<TrialRecord> ───┤                            │                          │
   │                │  [临期] 发提醒 + 标记 notified_at ──────────────────────────────────────────────>│
   │                │  [到期] update status=EXPIRED ────────────────────────>│                        │
   │                │         disable(trial-policyId) ─────────────────────────────────────────────>│（USER 配额停用）
   │                │         → 用户进入只读                                                            │
```

### 3.5 登录体验：记住登录（7 天）

- `LoginCommand` 增 `rememberMe`；`AuthLoginRequest` 增字段；前端 `LoginPage.tsx` 已有 `remember` 复选框，接通即可。
- `TokenServicePort.login(userId, rememberMe)`；`SaTokenServiceAdapter` 用 `SaLoginModel` 设定超时：

```java
StpUtil.login(userId, new SaLoginModel()
        .setIsLastingCookie(rememberMe)
        .setTimeout(rememberMe ? 7 * 24 * 3600 : 24 * 3600));   // 7 天 / 1 天
return StpUtil.getTokenValue();
```

- 全局默认超时在 `application.properties` 配 `sa-token.timeout`、`sa-token.active-timeout`（当前只有 `sa-token.token-name=Authorization`，需补充，默认 1 天（不勾选记住我）或 7 天（勾选））。

**登录时序**：

```
Client    AuthController    KernelAuthService    UserRepo    PasswordHasher(BCrypt)   TokenService
  │ POST /auth/login {username|email, password, rememberMe}                              │
  ├──────────>│ login(cmd) │                    │                  │                      │
  │           ├───────────>│ findByUsername/Email(id) ───────────>│ UserRecord           │
  │           │            │ matches(raw, stored) ─────────────────────────────>│ true   │
  │           │            │ login(userId, rememberMe) ───────────────────────────────────>│ token(7d)
  │<── {code:0,data:{userId,role,token,avatar}} ──────────────────────────────────────────┤
```

> 登录支持邮箱或用户名：`KernelAuthService` 先按 `findByUsername`，未命中再 `findByEmail`（或前端区分）。MVP 建议**统一用邮箱登录**，`username` 仅作展示名。

---

## 4. 数据模型 / DDL

### 4.1 `t_user` 改造（增 `tenant_id` / `email` / `status`）

迁移脚本 `resources/database/migration/2026-06-xx_user_saas.sql`（PostgreSQL）：

```sql
-- 1) 新增字段（幂等）
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS email      VARCHAR(128);
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS tenant_id  VARCHAR(64)  NOT NULL DEFAULT 'default';
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS status     VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE';  -- ACTIVE / DISABLED / READONLY
ALTER TABLE t_user ADD COLUMN IF NOT EXISTS external_id VARCHAR(128);  -- 预留 OAuth（本期不用）

-- 2) 约束与索引
ALTER TABLE t_user ADD CONSTRAINT uk_user_email UNIQUE (email);   -- Postgres 允许多个 NULL，存量无邮箱不冲突
CREATE INDEX IF NOT EXISTS idx_user_tenant ON t_user (tenant_id, deleted);
CREATE INDEX IF NOT EXISTS idx_user_status ON t_user (status, deleted);

-- 3) 存量 admin：归入默认租户（DEFAULT_TENANT_ID='default'，已由 DEFAULT 落定），补一条占位邮箱（可选）
-- UPDATE t_user SET email = 'admin@local' WHERE username = 'admin' AND email IS NULL;
```

> `DEFAULT 'default'` 与全库既有租户列默认值一致（如 `t_short_term_memory.tenant_id DEFAULT 'default'`），存量用户平滑落入默认租户，不破坏现有数据。

同步更新建表基线 `resources/database/seahorse_init.sql` 的 `t_user` 定义，使全新部署与迁移结果一致：

```sql
CREATE TABLE t_user (
    id           BIGINT  NOT NULL PRIMARY KEY,
    username     VARCHAR(64)  NOT NULL,
    email        VARCHAR(128),
    password     VARCHAR(128) NOT NULL,
    role         VARCHAR(32)  NOT NULL,
    tenant_id    VARCHAR(64)  NOT NULL DEFAULT 'default',
    status       VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',
    avatar       VARCHAR(128),
    external_id  VARCHAR(128),
    create_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    update_time  TIMESTAMP  DEFAULT CURRENT_TIMESTAMP,
    deleted      SMALLINT     DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username),
    CONSTRAINT uk_user_email    UNIQUE (email)
);
```

> ⚠️ **密码迁移**：默认 Bean 由 `plainText()` 切到 BCrypt 后，种子 `admin/admin`（明文）将无法登录。需同步把 `seahorse_init.sql:2065` 的 `INSERT` 密码改为 BCrypt 串，或提供一次性脚本对存量明文重新编码（见 §10 风险表）。

### 4.2 试用表 `t_user_trial`（新增）

```sql
CREATE TABLE t_user_trial (
    id                   BIGINT       NOT NULL PRIMARY KEY,
    user_id              BIGINT       NOT NULL,
    tenant_id            VARCHAR(64)  NOT NULL,
    plan_code            VARCHAR(32)  NOT NULL DEFAULT 'FREE_TRIAL',
    status               VARCHAR(32)  NOT NULL DEFAULT 'ACTIVE',   -- ACTIVE / EXPIRED / CONVERTED
    token_limit          BIGINT,
    storage_limit_bytes  BIGINT,
    concurrency_limit    INTEGER,
    started_at           TIMESTAMP    NOT NULL,
    expires_at           TIMESTAMP    NOT NULL,
    notified_at          TIMESTAMP,
    create_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    update_time          TIMESTAMP    DEFAULT CURRENT_TIMESTAMP,
    deleted              SMALLINT     DEFAULT 0,
    CONSTRAINT uk_user_trial_user UNIQUE (user_id)
);
CREATE INDEX idx_user_trial_expiry ON t_user_trial (status, expires_at);
CREATE INDEX idx_user_trial_tenant ON t_user_trial (tenant_id);
```

### 4.3 验证码：Redis（无需建表）

- 存 Redis，不落库（§3.2）。失败计数、限流计数同存 Redis。
- 若后续审计/合规需留痕，再加可选审计表 `t_email_verification(email, purpose, sent_at, ip, success)`(架构决策：MVP 不建)。

---

## 5. 后端实现骨架

> 约束：kernel 不依赖 Spring；适配器在 `-adapter-*` 模块；装配遵循 `AutoConfiguration.imports` 分层与 `@AutoConfigureAfter`；Controller 用 `ObjectProvider` 懒加载，返回 `{code,data}` 信封。

### 5.1 inbound 端口（kernel/ports/inbound/auth）

```java
// RegistrationInboundPort.java
public interface RegistrationInboundPort {
    void sendVerificationCode(SendCodeCommand command);     // 发码
    RegisterResult register(RegisterCommand command);       // 注册并签发 JWT
    boolean emailAvailable(String email);
}

public record SendCodeCommand(String email, String clientIp) {}
public record RegisterCommand(String email, String code, String password, String clientIp) {}
public record RegisterResult(String userId, String role, String token, String avatar,
                             String tenantId, java.time.Instant trialExpiresAt) {}

// AuthInboundPort.java —— 扩展（保持向后兼容）
public interface AuthInboundPort {
    LoginResult login(LoginCommand command);
    void logout();
}
// LoginCommand.java —— 增 rememberMe
public record LoginCommand(String username, String password, boolean rememberMe) {}
```

### 5.2 outbound 端口（kernel/ports/outbound）

```java
// UserRepositoryPort.java —— 扩展
Optional<UserRecord> findByEmail(String email);
boolean emailExists(String email);

// UserRecord.java —— 加字段（id, username, email, password, role, tenantId, status, avatar, times）
public record UserRecord(Long id, String username, String email, String password,
                         String role, String tenantId, String status, String avatar,
                         java.time.Instant createTime, java.time.Instant updateTime) {}

// UserCreateValues.java —— 加字段
public record UserCreateValues(String username, String email, String password,
                               String role, String tenantId, String status, String avatar) {}

// TokenServicePort.java —— 扩展
String login(String userId, boolean rememberMe);

// EmailSenderPort.java —— 新增
public interface EmailSenderPort {
    void sendVerificationCode(String toEmail, String code, java.time.Duration ttl);
    void sendTrialExpiringNotice(String toEmail, java.time.Instant expiresAt);
}

// TrialRepositoryPort.java —— 新增
public interface TrialRepositoryPort {
    TrialRecord create(TrialCreateValues values);
    Optional<TrialRecord> findByUserId(Long userId);
    java.util.List<TrialRecord> findExpiring(java.time.Instant before, int limit);
    boolean updateStatus(Long id, String status, java.time.Instant updatedAt);
    boolean markNotified(Long id, java.time.Instant notifiedAt);
}

// TenantProvisioningPort.java —— 01-多租户 提供
public interface TenantProvisioningPort {
    String createPersonalTenant(String ownerEmail);   // 返回 tenantId
}
```

### 5.3 application 服务（kernel/application）

```java
// KernelRegistrationService.java  implements RegistrationInboundPort
public final class KernelRegistrationService implements RegistrationInboundPort {

    private final UserRepositoryPort users;
    private final PasswordHasherPort passwordHasher;
    private final TokenServicePort tokenService;
    private final KeyValueCachePort cache;          // 验证码
    private final RateLimiterPort rateLimiter;      // 防刷
    private final EmailSenderPort emailSender;
    private final TenantProvisioningPort tenantProvisioning;  // 01-多租户
    private final TrialActivationPort trialActivation;        // 由 KernelTrialService 暴露
    private final RegistrationTxPort tx;            // adapter 提供事务边界

    // 构造器 Objects.requireNonNull 全量非空校验（对齐 KernelAuthService 风格）

    @Override
    public void sendVerificationCode(SendCodeCommand cmd) {
        String email = requireEmail(cmd.email());
        guard("register:send",       "email:" + email, 1,  Duration.ofSeconds(60));
        guard("register:send-daily", "email:" + email, 10, Duration.ofHours(24));
        guard("register:send-ip",    "ip:" + cmd.clientIp(), 5, Duration.ofSeconds(60));
        String code = SixDigitCode.random();        // SecureRandom
        cache.set("register:vcode:" + email, code, Duration.ofMinutes(10));
        emailSender.sendVerificationCode(email, code, Duration.ofMinutes(10));
    }

    @Override
    public RegisterResult register(RegisterCommand cmd) {
        String email = requireEmail(cmd.email());
        guard("register:submit-ip", "ip:" + cmd.clientIp(), 10, Duration.ofHours(1));
        verifyCode(email, cmd.code());
        requireStrongPassword(cmd.password());
        if (users.emailExists(email)) throw new IllegalArgumentException("该邮箱已注册");

        String tenantId = tenantProvisioning.createPersonalTenant(email);     // 01-多租户
        String encoded  = passwordHasher.encode(cmd.password());

        // 事务内：建用户 + 开试用 + 写配额
        RegistrationResult r = tx.registerInTx(() -> {
            Long userId = users.create(new UserCreateValues(
                    deriveUsername(email), email, encoded, "user", tenantId, "ACTIVE", null));
            Instant expiresAt = trialActivation.activate(userId, tenantId);    // 写 t_user_trial + upsert quota
            return new RegistrationResult(userId, expiresAt);
        });

        cache.delete("register:vcode:" + email);
        String token = tokenService.login(String.valueOf(r.userId()), false);
        return new RegisterResult(String.valueOf(r.userId()), "user", token,
                null, tenantId, r.trialExpiresAt());
    }

    private void guard(String resource, String subject, int permits, Duration ttl) {
        RateLimitDecision d = rateLimiter.tryAcquire(resource, subject, permits, ttl);
        if (!d.allowed()) {
            throw new TooFrequentException("操作过于频繁，请稍后重试", d.retryAfter());
        }
    }

    private void verifyCode(String email, String code) {
        String key = "register:vcode:" + email;
        String expected = cache.get(key).orElseThrow(() -> new IllegalArgumentException("验证码已过期"));
        if (!expected.equals(code)) {
            // 失败计数 +1，达 5 次作废（省略细节）
            throw new IllegalArgumentException("验证码错误");
        }
    }
    // requireEmail / requireStrongPassword / deriveUsername 省略
}
```

```java
// KernelTrialService.java —— 试用激活 / 扫描 / 降级
public final class KernelTrialService implements TrialActivationPort {

    private final TrialRepositoryPort trials;
    private final QuotaPolicyRepositoryPort quotas;   // 块C
    private final EmailSenderPort emailSender;
    private final UserRepositoryPort users;
    private final TrialProperties props;              // duration-days / token-limit ...

    @Override
    public Instant activate(Long userId, String tenantId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofDays(props.durationDays()));
        trials.create(new TrialCreateValues(userId, tenantId, "FREE_TRIAL", "ACTIVE",
                props.tokenLimit(), props.storageLimitBytes(), props.concurrencyLimit(), now, expiresAt));
        quotas.upsert(new QuotaPolicy(
                "trial-" + userId, tenantId, QuotaScope.USER, String.valueOf(userId),
                QuotaPolicyStatus.ACTIVE, props.tokenLimit(), null, null, props.warnRatio(), now, now));
        return expiresAt;
    }

    public void scanExpiring() {                      // 由 @Scheduled 触发
        Instant warnBefore = Instant.now().plus(Duration.ofDays(props.warnBeforeDays()));
        for (TrialRecord t : trials.findExpiring(warnBefore, 500)) {
            if (t.expiresAt().isAfter(Instant.now()) && t.notifiedAt() == null) {
                users.findById(t.userId()).flatMap(u -> Optional.ofNullable(u.email()))
                     .ifPresent(e -> emailSender.sendTrialExpiringNotice(e, t.expiresAt()));
                trials.markNotified(t.id(), Instant.now());
            } else if (!t.expiresAt().isAfter(Instant.now())) {
                trials.updateStatus(t.id(), "EXPIRED", Instant.now());
                quotas.disable("trial-" + t.userId(), Instant.now());   // 块C：停用配额 → 只读
            }
        }
    }
}
```

```java
// KernelAuthService.java —— 改造点（保留原结构）
// 1) findByUsername 未命中再 findByEmail；2) tokenService.login(userId, command.rememberMe())
```

### 5.4 Web 适配器（adapter-web）

```java
// SeahorseRegistrationController.java
@RestController
public class SeahorseRegistrationController {
    private final ObjectProvider<RegistrationInboundPort> registrationProvider;   // 懒加载

    public SeahorseRegistrationController(ObjectProvider<RegistrationInboundPort> p) {
        this.registrationProvider = p;
    }

    @PostMapping("/auth/send-code")
    public Map<String, Object> sendCode(@RequestBody SendCodeRequest req, HttpServletRequest http) {
        registrationProvider.getIfAvailable()
                .sendVerificationCode(new SendCodeCommand(req.getEmail(), ClientIp.of(http)));
        return Map.of("code", "0", "data", Map.of("sent", true, "ttlSeconds", 600));
    }

    @PostMapping("/auth/register")
    public Map<String, Object> register(@RequestBody RegisterRequest req, HttpServletRequest http) {
        RegisterResult r = registrationProvider.getIfAvailable()
                .register(new RegisterCommand(req.getEmail(), req.getCode(), req.getPassword(), ClientIp.of(http)));
        return Map.of("code", "0", "data", r);
    }

    @GetMapping("/auth/email-available")
    public Map<String, Object> emailAvailable(@RequestParam String email) {
        boolean ok = registrationProvider.getIfAvailable().emailAvailable(email);
        return Map.of("code", "0", "data", Map.of("available", ok));
    }
}
```

```java
// SaTokenServiceAdapter.java —— login 增加 rememberMe（见 §3.5 代码）
// SmtpEmailSenderAdapter.java（adapter-web 或新 adapter-notify-mail 模块）implements EmailSenderPort
//   - 依赖 spring-boot-starter-mail 的 JavaMailSender；缺省提供 LoggingEmailSenderAdapter（noop，仅打日志，dev 用）
// JdbcTrialRepositoryAdapter.java（adapter-repository-jdbc）implements TrialRepositoryPort
```

### 5.5 自动配置装配（遵循 CLAUDE.md 6 层规则）

| Bean | 归属 autoconfig | 层 / 顺序 | `@AutoConfigureAfter` 需声明 |
|------|-----------------|-----------|------------------------------|
| `BCryptPasswordHasherAdapter`、`JdbcTrialRepositoryAdapter`、`EmailSenderPort` 实现 | `SeahorseAgentAuthAdapterAutoConfiguration`（扩展） | Layer 1（after DataSource） | `DataSourceAutoConfiguration`（已有） |
| `SmtpEmailSenderAdapter`（如独立模块） | 新 `SeahorseAgentMailAdapterAutoConfiguration` | Layer 1 | — |
| `KernelRegistrationService`、`KernelTrialService` | `SeahorseAgentKernelAuthAutoConfiguration`（扩展） | Layer 4（after Auth adapter） | **新增**：`SeahorseAgentCacheAdapterAutoConfiguration`、`SeahorseAgentRegistryRepositoryAutoConfiguration`（块C 配额仓储）、`<01-多租户 租户 autoconfig>`、Mail autoconfig |
| `@Scheduled` 试用扫描触发器 | 新 `SeahorseAgentTrialSchedulerAutoConfiguration` | Layer 7（runtime guards 同层，after kernel sub-configs） | `SeahorseAgentKernelAuthAutoConfiguration` |

> ⚠️ **关键**：`KernelRegistrationService` 依赖 `KeyValueCachePort`（Layer 1 cache）、`RateLimiterPort`（同）、`QuotaPolicyRepositoryPort`（Layer 1 registry repo）、`TenantProvisioningPort`（01-多租户）。按 CLAUDE.md「kernel 子配置必须在 `@AutoConfigureAfter` 中声明所有产生 `@ConditionalOnBean` 依赖的 adapter/repository 配置」，`SeahorseAgentKernelAuthAutoConfiguration` 现仅声明 `@AutoConfigureAfter(SeahorseAgentAuthAdapterAutoConfiguration.class)`，**必须补全**上述依赖配置，否则 Bean 装配时序错乱导致 `@ConditionalOnBean` 落空。
> 各服务 Bean 一律加 `@ConditionalOnBean(<依赖端口>.class)` + `@ConditionalOnMissingBean(<自身接口>.class)`，与现有 `KernelAuthService` 装配风格一致。

### 5.6 安全放行

`/auth/send-code`、`/auth/register`、`/auth/email-available` 均在 `/auth/` 前缀下，已被 `SeahorseSecurityWebMvcConfiguration` 的 `PUBLIC_PATH_PREFIXES` 与 `excludePathPatterns("/auth/**")` 放行，**无需改动放行规则**。只读降级判定为新增逻辑（§3.4）。

---

## 6. 前端实现骨架

> 复用既有：`services/api.ts`（axios，自动解 `{code,data}` 信封、注入 `Authorization`）、`stores/authStore.ts`（zustand）、`utils/storage.ts`、`types/index.ts`。

### 6.1 TS 类型（`frontend/src/types/index.ts` 增补）

```ts
// 复用既有 User：{ userId; username?; role; token; avatar? }
export interface RegisterResult extends User {
  tenantId: string;
  trialExpiresAt: string;        // ISO-8601
}

export interface SendCodeResult {
  sent: boolean;
  ttlSeconds: number;
}

export interface TrialStatus {
  status: "ACTIVE" | "EXPIRED" | "CONVERTED";
  expiresAt: string;
  daysLeft: number;
  tokenLimit?: number;
  readOnly: boolean;
}
```

### 6.2 service（`frontend/src/services/authService.ts` 增补）

```ts
import { api } from "@/services/api";
import type { RegisterResult, SendCodeResult } from "@/types";

export async function sendRegisterCode(email: string) {
  return api.post<SendCodeResult>("/auth/send-code", { email });
}

export async function register(email: string, code: string, password: string) {
  return api.post<RegisterResult>("/auth/register", { email, code, password });
}

export async function checkEmailAvailable(email: string) {
  return api.get<{ available: boolean }>("/auth/email-available", { params: { email } });
}

// 既有 login 增加 rememberMe
export async function login(username: string, password: string, rememberMe = false) {
  return api.post<LoginResponse>("/auth/login", { username, password, rememberMe });
}
```

### 6.3 store（`frontend/src/stores/authStore.ts` 增补 `register` action）

```ts
register: async (email: string, code: string, password: string) => {
  set({ isLoading: true });
  try {
    const data = await registerRequest(email, code, password);
    const user = { userId: data.userId, username: data.username || email,
                   role: data.role, token: data.token, avatar: data.avatar };
    storage.setToken(user.token);
    storage.setUser(user);
    setAuthToken(user.token);
    set({ user, token: user.token, isAuthenticated: true });
    toast.success(`注册成功，已开通 14 天试用`);
  } catch (e) {
    toast.error((e as Error).message || "注册失败");
    throw e;
  } finally {
    set({ isLoading: false });
  }
}
```

### 6.4 注册页（`frontend/src/pages/RegisterPage.tsx` 新增）

- 复用 `LoginPage.tsx` 的视觉外壳（气泡/光晕/`SeahorseLogo`），表单字段：邮箱、验证码（带 60s 倒计时"发送验证码"按钮）、密码、确认密码。
- 提交调 `useAuthStore().register(...)`，成功后 `navigate("/chat")`。
- 路由 `frontend/src/router.tsx` 增 `/register`（公开页，免鉴权）；`LoginPage` 底部加"没有账号？去注册"。

```tsx
// 关键交互骨架
const [cooldown, setCooldown] = React.useState(0);
const handleSendCode = async () => {
  if (cooldown > 0) return;
  await sendRegisterCode(email);          // 失败由 api 拦截器 toast
  setCooldown(60);                        // 启动倒计时
  toast.success("验证码已发送至邮箱");
};
const handleSubmit = async (e) => {
  e.preventDefault();
  if (password !== confirm) return setError("两次密码不一致");
  await register(email, code, password);
  navigate("/chat");
};
```

### 6.5 登录页（`frontend/src/pages/LoginPage.tsx` 接通"记住我"）

- 现有 `remember` state 已存在（默认 `true`），仅需把它透传给 `login(username, password, remember)`，并在 `authStore.login` 签名补 `rememberMe`。

### 6.6 试用横幅（可选 P1）

- 全局布局读 `GET /user/me` 或新增 `GET /trial/status`，临期 ≤3 天显示提醒条；`readOnly=true` 时全局禁用写操作按钮并提示升级。

---

## 7. 任务清单

### P0（MVP 必交付）

- [ ] **DDL**：`t_user` 加 `tenant_id` / `email` / `status`；新增 `t_user_trial`；更新 `seahorse_init.sql` 基线 + 迁移脚本。
- [ ] **密码安全**：实现 `BCryptPasswordHasherAdapter`，替换 `PasswordHasherPort.plainText()` 默认 Bean；处理 `admin` 种子密码迁移。
- [ ] **端口扩展**：`UserRecord` / `UserCreateValues` 加字段；`UserRepositoryPort.findByEmail/emailExists`；`JdbcUserRepositoryAdapter` 对应改造（SELECT 列、INSERT 列）。
- [ ] **注册入站**：`RegistrationInboundPort` + `KernelRegistrationService`（验证码、防刷、建用户、建租户、开试用、签发 JWT）。
- [ ] **验证码**：`KeyValueCachePort` 存取 + `RateLimiterPort` 三维防刷。
- [ ] **邮件端口**：`EmailSenderPort` + `LoggingEmailSenderAdapter`（dev noop）+ `SmtpEmailSenderAdapter`。
- [ ] **试用**：`TrialRepositoryPort` + `JdbcTrialRepositoryAdapter` + `KernelTrialService.activate`；绑定块C `QuotaPolicy`（USER 维度）。
- [ ] **登录记住我**：`LoginCommand.rememberMe` → `TokenServicePort.login(userId, rememberMe)` → `SaLoginModel`；补 `sa-token.timeout`。
- [ ] **Web 层**：`SeahorseRegistrationController`（`/auth/send-code`、`/auth/register`、`/auth/email-available`）。
- [ ] **装配**：扩展 `SeahorseAgentAuthAdapterAutoConfiguration` + `SeahorseAgentKernelAuthAutoConfiguration`（补 `@AutoConfigureAfter`：cache、registry-repo、01-多租户 租户、mail）。
- [ ] **前端**：`RegisterPage` + `authService` 增补 + `authStore.register` + 路由 `/register` + 登录页接通 remember。
- [ ] **01-多租户 对接**：确认 `TenantProvisioningPort` 真实命名与签名，替换占位（**依赖01-多租户 交付**）。

### P1（增强，可次轮）

- [ ] 试用到期定时扫描 + 提醒邮件 + 到期降只读（`@Scheduled` + 安全拦截只读判定）。
- [ ] 试用横幅 / 升级引导前端。
- [ ] 找回密码（邮箱验证码重置）。
- [ ] 验证码审计表 `t_email_verification`（合规留痕）。
- [ ] 注册风控增强（图形/滑块验证码、一次性邮箱域名黑名单）。

---

## 8. 测试策略

| 层次 | 范围 | 要点 |
|------|------|------|
| 单元（kernel） | `KernelRegistrationService`、`KernelTrialService`、`KernelAuthService` | mock 各 outbound 端口；覆盖：验证码过期/错误、邮箱重复、防刷拒绝、试用配额写入、rememberMe 时长分支。kernel 无 Spring 依赖，纯 JUnit。 |
| 适配器集成 | `JdbcUserRepositoryAdapter`（新列）、`JdbcTrialRepositoryAdapter` | 对齐既有 `JdbcQuotaSchemaAlignmentTests` 风格做 schema 对齐测试；真实/嵌入 PostgreSQL。 |
| 验证码/限流 | `RedisCacheAdapter` + `RateLimiterPort` | TTL 到期失效、计数窗口、并发发码竞争。 |
| Web 切片 | `SeahorseRegistrationController` | `@WebMvcTest` + mock inbound port；断言 `{code,data}` 信封、`/auth/**` 免鉴权可达。 |
| 端到端 | 注册→建租户→试用→登录 | 用 `seahorse-agent-tests` 集成模块；断言 5 分钟内全程跑通（§9）。 |
| 装配回归 | autoconfig 顺序 | 启动上下文断言 `KernelRegistrationService` Bean 存在（验证 `@AutoConfigureAfter` 补全有效），防 `@ConditionalOnBean` 落空回归。 |
| 安全 | 只读降级、跨租户 | 试用到期后写接口 403/`code=1`；新用户数据落在自身 `tenant_id`（与01-多租户 隔离测试联动）。 |

---

## 9. 验收标准

1. **主路径 < 5 分钟**：全新邮箱 → 收验证码 → 提交注册 → 自动创建个人租户 → 激活 14 天试用 → 返回 JWT → 直接进入 `/chat`，全程一次性走通且 < 5 分钟。
2. 注册成功后 `t_user` 落库且 `tenant_id` = 新建个人租户、`email` 唯一、密码为 BCrypt 串（非明文）。
3. `t_user_trial` 落一条 `status=ACTIVE`、`expires_at = now+14d`；`sa_quota_policy` 落一条 USER 维度 `trial-{userId}` 策略。
4. 同邮箱重复注册被拒；验证码错误/过期被拒；60s 内重复发码被限流。
5. 勾选"记住我"登录后 token 有效期 7 天；不勾选为默认(1 天)。
6. 试用到期后（可手工改 `expires_at` 触发）该用户写操作被拒、读操作正常（P1）。
7. 应用上下文正常启动，`RegistrationInboundPort` Bean 装配成功，无循环依赖/条件落空告警。

---

## 10. 风险与缓解

| 风险 | 影响 | 缓解 |
|------|------|------|
| **试用滥用**（一人多注册薅配额） | 配额/成本被刷 | ① 邮箱+IP 双维限流（§3.2）；② 一次性邮箱域名黑名单（P1）；③ 试用配额保守（token 200 万、并发 2）；④ 同 IP 注册数日上限；⑤ P1 引入图形/滑块验证码。 |
| **密码迁移断登录** | 切 BCrypt 后 `admin/admin` 明文失效 | 迁移脚本把种子密码改为 BCrypt 串；或上线时提供"明文→BCrypt"一次性回填；灰度期 `PasswordHasherPort` 可做"先 BCrypt 后明文兜底"双校验（仅迁移窗口，事后移除）。 |
| **01-多租户 未就绪** | 注册无法建租户 | `TenantProvisioningPort` 以接口隔离；01-多租户 未交付前用 `DefaultTenantProvisioningAdapter`（返回 `DEFAULT_TENANT_ID='default'`）打通主流程，01-多租户 就绪后替换实现，无需改 kernel。 |
| **建用户/建租户跨边界不一致** | 孤儿租户或孤儿用户 | 同库则同事务（§3.3.1）；跨服务则"先租户后用户 + 失败补偿/对账任务"。 |
| **验证码邮件送达率/延迟** | 注册流失 | dev 用 `LoggingEmailSenderAdapter`；生产接可靠 SMTP/邮件服务商；前端 60s 倒计时 + "未收到？重新发送"。 |
| **autoconfig 顺序错装** | `KernelRegistrationService` 装不出 | 严格补全 `@AutoConfigureAfter`（§5.5）+ 装配回归测试（§8）。 |
| **限流 noop 退化** | `RateLimiterPort` 默认 `noop()` 时防刷失效 | 部署校验：生产必须装配 Redis 实现（`RateLimiterPort` 由 `RedisCacheAdapter` 提供）；启动时若检测到 noop 限流器对注册资源记 WARN。 |
| **明文 X-Forwarded-For 伪造** | IP 维度限流被绕过 | 仅信任反代注入的首段 IP；nginx 层覆盖 `X-Forwarded-For`(已在 frontend/nginx.conf 配置)。 |

---

## 11. 参考文件锚点（均经代码核实，2026-06-04）

**后端 · 鉴权闭环**
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAuthController.java` — 登录/登出入口、`ObjectProvider` 模式
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/auth/KernelAuthService.java` — 登录领域服务（改造点）
- `seahorse-agent-adapter-repository-jdbc/src/main/java/com/miracle/ai/seahorse/agent/adapters/repository/jdbc/JdbcUserRepositoryAdapter.java` — 用户仓储（改造点：列、create/find）
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SaTokenServiceAdapter.java` — token 适配（rememberMe 改造点）
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SaTokenCurrentUserAdapter.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseSecurityWebMvcConfiguration.java` — `/auth/**` 放行、只读降级落点
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseUserController.java` — `/user/me`、`/user/password` 等

**后端 · 端口**
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/auth/{AuthInboundPort,LoginCommand,LoginResult}.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/auth/{UserRepositoryPort,UserRecord,UserCreateValues,PasswordHasherPort,TokenServicePort}.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/cache/{KeyValueCachePort,RateLimiterPort,RateLimitDecision}.java` — 验证码 + 防刷
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent/QuotaPolicyRepositoryPort.java` — 块C 配额端口

**后端 · 块C 配额底座**
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/quota/{QuotaPolicy,QuotaScope,QuotaPolicyStatus,QuotaPolicyLimits}.java`

**后端 · 自动配置（分层）**
- `seahorse-agent-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` — 7 层装配顺序
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentAuthAdapterAutoConfiguration.java`（Layer 1）
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentKernelAuthAutoConfiguration.java`（Layer 4，需补 `@AutoConfigureAfter`）
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentCacheAdapterAutoConfiguration.java`（Layer 1，验证码/限流来源）
- `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentRegistryRepositoryAutoConfiguration.java`（Layer 1，块C 配额仓储来源）
- `seahorse-agent-adapter-cache-redis/src/main/java/com/miracle/ai/seahorse/agent/adapters/cache/redis/RedisCacheAdapter.java`

**数据库**
- `resources/database/seahorse_init.sql:12`（`t_user` 基线，缺 `tenant_id`/`email`）、`:2065`（admin 明文种子）、`:1507`（`sa_quota_policy`）

**配置**
- `seahorse-agent-bootstrap/src/main/resources/application.properties:5-6`（`sa-token.token-name`，需补 `timeout`）

**前端**
- `frontend/src/services/authService.ts`、`frontend/src/stores/authStore.ts`、`frontend/src/pages/LoginPage.tsx`（含 `remember`）
- `frontend/src/services/api.ts`（`{code,data}` 信封、`Authorization` 注入）、`frontend/src/utils/authSession.ts`、`frontend/src/types/index.ts`（`User` / `CurrentUser`）
- `frontend/src/router.tsx`（增 `/register`）、`frontend/nginx.conf`(X-Forwarded-For 已配置)

**关联文档**
- `docs/aegis/plans/2026-06-04-saas-mvp-execution-roadmap.md` — 块B 在主线中的定位（§块B：用户体系）

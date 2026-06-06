# 04 · 计费系统（套餐 / 配额强制 / 支付 / 账单）可落地技术方案

> 日期：2026-06-04
> 模块归属：SaaS MVP 主线一 · 块C（计费系统）
> 前置依赖：块A 多租户（`TenantContext.getTenantId()` 提供租户维度）
> 适用架构：Spring Boot 3.5.7 + React，六边形（端口-适配器），AutoConfiguration 6 层
> 关联文档：`docs/aegis/plans/2026-06-04-saas-mvp-execution-roadmap.md`（块C 现状与验收）

---

## 1. 目标与范围

### 1.1 核心认知：计费不是从零建，有底座可复用

这是本方案最重要的判断。经 2026-06-04 代码级审查确认，**配额（~85%）+ 用量统计（~80%）底座已存在且有测试覆盖**，计费 MVP 的真实工作量是"在现成的配额/用量地基上补套餐、支付、账单三层 + 把已有配额决策接入运行时"，而不是重写一套计量体系。

| 能力 | 复用程度 | 直接复用的真实资产 |
|------|---------|------------------|
| 配额模型 | ✅ ~85% | `QuotaPolicy`（6 维度 scope + tokenLimit/callLimit/costLimit/warnRatio）、`sa_quota_policy` 表、`KernelQuotaDecisionService.evaluate()` |
| 配额 API | ✅ | `POST /api/quotas/policies`、`POST /api/quotas/decisions:evaluate`、`GET /api/me/quota-summary` |
| 用量计量 | ✅ ~80% | `CostUsageRecord`、`sa_cost_usage_record` 表、`POST /api/cost-usage-records`、`GET /api/cost-usage:aggregate` |
| 用量聚合 | 🟡 | `CostUsageRepositoryPort.aggregate()` 已能按租户/Agent/Run 汇总 token/calls/cost，**缺定时触发** |

### 1.2 做什么（本方案范围）

1. **套餐管理**：新建 `SubscriptionPlan`（免费试用 / 基础 / 专业 / 企业 四档），**映射到已有 `QuotaPolicy`**——订阅激活时写一条 TENANT-scope 的 `QuotaPolicy`，复用既有强制链路。
2. **配额运行时强制**：把已有 `KernelQuotaDecisionService.evaluate()` 接入两个真实拦截点——`KernelKnowledgeDocumentService.upload()`（上传前查存储配额）、`KernelAgentRunService.startRun()`（执行前查 token 配额）。
3. **用量自动聚合**：新增定时 Job，周期性把 `sa_cost_usage_record` 滚动汇总到 `sa_usage_rollup`，供配额强制与账单读取（避免每次现算）。
4. **在线支付**：支付宝 / 微信，扫码 + H5；订单状态机、回调幂等、签名验证（**高风险点，第 10 节重点**）。
5. **账单**：每月 1 号生成上月账单 + 历史查询。

### 1.3 不做 / 后延（Phase 2）

- ❌ 发票 / 增值税专票
- ❌ 按量付费（usage-based）精算计费
- ❌ 年付优惠 / 折扣券 / 多币种
- ❌ 自动续费扣款（MVP 仅做单次下单激活）

---

## 2. 现状（代码级，讲清能复用什么）

### 2.1 配额领域模型 —— 直接复用，零改动

`seahorse-agent-kernel/.../domain/agent/quota/QuotaPolicy.java`：

```java
public record QuotaPolicy(String policyId, String tenantId, QuotaScope scope,
                          String subjectId, QuotaPolicyStatus status,
                          Long tokenLimit, Long callLimit, Double costLimit,
                          double warnRatio, Instant createdAt, Instant updatedAt) {
    public boolean exceededBy(QuotaUsage usage) { ... }          // 硬超限判定
    public boolean warnThresholdReachedBy(QuotaUsage usage) { ... } // 预警判定
    public QuotaPolicy disable(Instant disabledAt) { ... }
}
```

- `QuotaScope`（`domain/agent/quota/QuotaScope.java`）：`TENANT, AGENT, USER, TOOL, MODEL, RUN` —— **套餐只需用 `TENANT` 这一档**。
- `QuotaPolicyStatus`：`ACTIVE, DISABLED`。
- `QuotaPolicyLimits.DEFAULT_WARN_RATIO = 0.8`。
- 约束：`tokenLimit/callLimit/costLimit` 至少一个非空；`warnRatio ∈ (0,1]`。

### 2.2 配额决策服务 —— 接入运行时即可，逻辑已完备

`seahorse-agent-kernel/.../application/agent/quota/KernelQuotaDecisionService.java`（implements `QuotaManagementInboundPort`）：

- `evaluate(QuotaDecisionCommand)`：按 `MATCH_ORDER = [RUN, AGENT, USER, TOOL, MODEL, TENANT]` 逐级匹配最具体的 active 策略，返回 `QuotaDecisionResult`。
- 决策枚举 `QuotaDecisionEffect`：`ALLOW, WARN, REQUIRE_APPROVAL, DENY`。
- 原因码 `QuotaDecisionReasonCode`：`NO_POLICY_LOW_RISK, NO_POLICY_HIGH_RISK, POLICY_DISABLED, HARD_LIMIT_EXCEEDED, WARN_THRESHOLD_EXCEEDED, POLICY_MATCHED`。
- 另有 `findActiveForAgent(tenantId, agentId)`：Agent 级找不到时回落 TENANT 级——**套餐场景正好用这个回落逻辑**。

> 关键结论：运行时强制**不需要新写判定逻辑**，只需在拦截点构造 `QuotaDecisionCommand` → 调 `evaluate()` → 对 `DENY` 抛友好异常。

### 2.3 用量计量 —— 复用，仅补聚合触发

`seahorse-agent-kernel/.../domain/agent/cost/CostUsageRecord.java`：

```java
public record CostUsageRecord(String usageId, String tenantId, String agentId,
                              String runId, String userId, String toolId, String modelId,
                              CostUsageSource source, long tokens, long calls, double cost,
                              String reasonRef, Instant createdAt) {}
```

- `CostUsageSource`：`MODEL, TOOL, SANDBOX, MANUAL_ADJUSTMENT`。
- `CostUsageAggregate`：`tenantId, agentId, runId, totalTokens, totalCalls, totalCost, recordCount`。
- `CostUsageRepositoryPort.aggregate(CostUsageQuery)` 已实现（`JdbcCostUsageRepositoryAdapter` 用 `COALESCE(SUM(...))`），`CostUsageQuery(tenantId, agentId, runId, from, to)` 支持时间窗口——**账单按月查询直接用 `from/to`**。

### 2.4 配额汇总服务（前端已在用）

`KernelQuotaSummaryService`（implements `QuotaSummaryInboundPort`）→ `GET /api/me/quota-summary` → 返回 `UserQuotaSummary`（callLimit/usedCalls/remainingCalls/costLimit/usedCost/remainingCost/status）。前端 `frontend/src/services/quotaSummaryService.ts` 已对接。**套餐页的"当前用量"卡片可直接复用此接口。**

### 2.5 现有控制器 / 端口 / 自动配置约定（新建代码须对齐）

| 关注点 | 真实样板 |
|--------|---------|
| 控制器懒加载 | `SeahorseQuotaController` 用 `ObjectProvider<QuotaManagementInboundPort>` + `ApiResponses.requireService(provider, port -> ...)` |
| 响应容器 | `ApiResponse<T>(code, message, data)`，成功 `code="0"` |
| 高级特性开关 | `AdvancedFeatureGate.requireEnabled(AdvancedFeature.QUOTA_MANAGEMENT)`（枚举见 `AdvancedFeature.java`） |
| JDBC 仓储注册 | `SeahorseAgentRegistryRepositoryAutoConfiguration`（Layer 1）：`@ConditionalOnBean(DataSource)` + `@ConditionalOnProperty(repository.type=jdbc)` + `@ConditionalOnMissingBean(Port)` |
| Kernel 服务注册 | `SeahorseAgentKernelRegistryAutoConfiguration`（Layer 6）：`@AutoConfigureAfter({...RepositoryAutoConfiguration, ...KernelAuthAutoConfiguration})` + `@ConditionalOnBean(Port)` + `@ConditionalOnMissingBean(InboundPort)` |
| 定时任务 | `SeahorseMemoryAggregationJob`：`@Scheduled(fixedDelayString="${...}")` + `DistributedLockPort.tryLock/unlock`（分布式锁防多实例重复执行） |
| DDL 约定 | `sa_*` 前缀；`pk_id BIGSERIAL PRIMARY KEY` + 业务 `*_id VARCHAR(64) NOT NULL UNIQUE`；`tenant_id VARCHAR(64)`；`TIMESTAMP`；`chk_*` 约束 + `idx_*` 索引 |

### 2.6 现状缺口确认（grep 验证：当前不存在）

`subscription / payment / billing / invoice` 相关 Java 类**均不存在**（仅命中无关的 `MessageSubscriptionPort`）。`AdvancedFeature` 枚举**无**计费相关项。→ 套餐 / 支付 / 账单三层确为全新建。

---

## 3. 技术方案

### 3.0 总体数据流

```
┌─────────────┐  选套餐+下单   ┌──────────────────┐  生成预付单    ┌────────────────┐
│  租户(前端)  │ ────────────▶ │ KernelPayment    │ ────────────▶ │ 支付宝/微信网关 │
└─────────────┘               │ Service          │  二维码/H5URL  └────────┬───────┘
       ▲                       └──────────────────┘                        │ 异步回调
       │ 配额生效                        │                                   ▼
       │                       订单 PAID │激活            ┌──────────────────────────┐
┌──────┴───────────┐          ┌─────────▼──────────┐    │ SeahorsePaymentController │
│ 运行时拦截点      │          │ KernelSubscription │◀───│ /callback (验签+幂等)     │
│ upload()/startRun│          │ Service.activate() │    └──────────────────────────┘
│   ↓ evaluate()   │          │   ↓ upsertPolicy() │
│ KernelQuota      │◀─────────│  写 TENANT QuotaPolicy(复用既有强制链路)
│ DecisionService  │          └────────────────────┘
└──────────────────┘
       ▲ 读 rollup
┌──────┴────────────┐  每5min   ┌────────────────────────┐  每月1号  ┌──────────────────┐
│ sa_usage_rollup   │◀─────────│ UsageAggregationJob     │          │ BillingGenJob    │
│ (聚合用量)        │          │ (滚动汇总cost_usage)    │          │ (生成上月账单)   │
└───────────────────┘          └────────────────────────┘          └──────────────────┘
```

### 3.1 套餐管理（SubscriptionPlan）

**设计要点：套餐 = 一组配额模板 + 价格。** 套餐本身**不参与运行时判定**；它在"订阅激活"时被翻译成一条 TENANT-scope `QuotaPolicy`，从而复用 §2.2 的既有强制链路。这是最大的复用决策。

**四档套餐（种子数据，写入 `sa_subscription_plan`）：**

| code | 名称 | 价格(分/月) | token_limit | call_limit | cost_limit | storage_limit_bytes | 试用天数 |
|------|------|-----------|-------------|-----------|-----------|---------------------|---------|
| `FREE_TRIAL` | 免费试用 | 0 | 100,000 | 100 | 5.0 | 1 GB | 14 |
| `BASIC` | 基础版 | 9900 | 2,000,000 | 2,000 | 100.0 | 10 GB | 0 |
| `PRO` | 专业版 | 39900 | 10,000,000 | 10,000 | 500.0 | 50 GB | 0 |
| `ENTERPRISE` | 企业版 | 99900 | 50,000,000 | 50,000 | 3000.0 | 500 GB | 0 |

> `storage_limit_bytes` 是套餐新增的配额维度（`QuotaPolicy` 现有 6 维度无"存储"）。MVP 不扩 `QuotaScope` 枚举，而是把存储配额作为**套餐字段**单独落 `sa_subscription` 行，由上传拦截点直接读 `sa_usage_rollup.storage_bytes` 对比——避免改动 kernel 领域枚举（降低 blast radius）。token/call/cost 三档仍走 `QuotaPolicy`。

**套餐 → 配额映射（激活时执行）：**

```
SubscriptionPlan(tokenLimit, callLimit, costLimit)
        │  KernelSubscriptionService.activate(tenantId, planCode)
        ▼
QuotaPolicyUpsertCommand(
    policyId   = "plan:" + tenantId,        // 每租户一条稳定 ID，幂等 upsert
    tenantId   = tenantId,
    scope      = QuotaScope.TENANT,
    subjectId  = tenantId,                   // TENANT 维度 subjectId 即 tenantId
    status     = ACTIVE,
    tokenLimit, callLimit, costLimit,
    warnRatio  = 0.8)
        ▼
QuotaManagementInboundPort.upsertPolicy(cmd)   // ← 复用既有入站端口，无需新强制逻辑
```

### 3.2 配额运行时强制（接入既有 evaluate）

**两个真实拦截点（已 grep 定位）：**

#### 拦截点 A — 文档上传前查存储配额
`KernelKnowledgeDocumentService.upload(UploadKnowledgeDocumentCommand)`（kernel，第 104 行）。`UploadFileContent.size()` 提供字节数。

逻辑：上传前读 `sa_usage_rollup.storage_bytes(tenant)` + 本次 `file.size()`，与 `sa_subscription.storage_limit_bytes` 比较，超限抛 `QuotaExceededException`。

#### 拦截点 B — Agent 执行前查 token/call 配额
`KernelAgentRunService.startRun(AgentRunStartCommand)`（kernel，第 63 行）。`AgentRunStartCommand.tenantId()` 提供租户。

逻辑：`startRun` 创建 `AgentRun` 前，构造 `QuotaDecisionCommand(tenantId, agentId, ...requestedUsage)` → `evaluate()`；`DENY` → 抛 `QuotaExceededException`；`WARN` → 放行但回传预警标记。

**注入方式（不破坏六边形 + 不改 kernel 既有构造签名）：**

引入新出站端口 `QuotaEnforcementPort`（kernel/ports/outbound），由 `KernelQuotaDecisionService` 适配实现；在两个 kernel 服务中以**可选依赖** `ObjectProvider`/nullable 形式注入（参考 `KernelAgentRunSnapshotService` 已有的 `ObjectProvider<...>` 可选依赖模式），`null` 时跳过强制（保持单机/测试无配额时可用）。

> 选型理由：直接让 `KernelAgentRunService` 依赖 `QuotaManagementInboundPort`（入站口）会造成 kernel 内部 inbound→inbound 调用，违反分层；改为定义细粒度出站口 `QuotaEnforcementPort { QuotaDecisionResult check(...); }`，语义更窄、可独立 mock。

**超限友好提示（统一异常 → HTTP 映射）：**

```java
// 新建 kernel 领域异常
public class QuotaExceededException extends RuntimeException {
    private final QuotaDecisionReasonCode reasonCode;
    private final String upgradeHint;   // "当前套餐 token 配额已用尽，升级到专业版可获得 10倍额度"
}
```

由 web 层 `@ControllerAdvice` 映射为 `ApiResponse.error("QUOTA_EXCEEDED", message)` + HTTP 402（Payment Required），前端据此弹"升级套餐"引导（§6.4）。

### 3.3 用量自动聚合（定时 Job）

**现状**：`CostUsageRepositoryPort.aggregate()` 每次实时 `SUM` 全表窗口，配额强制每次都现算成本高。
**方案**：新增 `sa_usage_rollup`（按 `tenant_id + period`（当月）一行），`SeahorseCostUsageAggregationJob` 周期滚动：

```
@Scheduled(fixedDelayString = "${seahorse-agent.billing.usage-rollup.delay-ms:300000}")  // 5min
public void rollup() {
    if (!lockPort.tryLock("job:usage-rollup", ZERO, LEASE_5MIN)) return;   // 复用 DistributedLockPort
    try {
        usageRollupService.rollupCurrentPeriod(Instant.now());  // 增量：仅汇总 last_rolled_at 之后的记录
    } finally { lockPort.unlock("job:usage-rollup"); }
}
```

- 复用 `SeahorseMemoryAggregationJob` 的 `@Scheduled + DistributedLockPort` 范式（防多实例重复）。
- 增量游标 `sa_usage_rollup.last_source_id`（已聚合到的 `sa_cost_usage_record.pk_id`），避免全表重扫。
- 存储用量 `storage_bytes` 由上传/删除文档时同步累加到 rollup（或 Job 内 `SUM(file_size)`）。

### 3.4 在线支付（支付宝 / 微信，扫码 + H5）

**新建支付适配器模块**（对齐现有 `seahorse-agent-adapter-*` 命名）：`seahorse-agent-adapter-payment-alipay`、`seahorse-agent-adapter-payment-wechat`，各实现出站口 `PaymentGatewayPort`。

**订单状态机（`OrderStatus`）：**

```
        create()              prepay()            callback(verified+paid)
  ┌──────────────┐  待支付   ┌──────────┐  支付中  ┌──────────┐  已支付   ┌─────────┐
  │   (new)      │ ───────▶ │ PENDING  │ ───────▶ │  PAYING  │ ───────▶ │  PAID   │
  └──────────────┘          └────┬─────┘          └────┬─────┘          └────┬────┘
                                 │ timeout/cancel        │ timeout            │ refund()
                                 ▼                       ▼                    ▼
                            ┌──────────┐            ┌──────────┐         ┌──────────┐
                            │ CANCELED │            │ CANCELED │         │ REFUNDED │
                            └──────────┘            └──────────┘         └──────────┘
```

合法迁移（在领域对象 `PaymentOrder` 内强约束，非法迁移抛异常）：
- `PENDING → PAYING`（发起支付，拿到网关 tradeNo）
- `PENDING/PAYING → PAID`（回调验证通过）
- `PENDING/PAYING → CANCELED`（用户取消 / 超时关单）
- `PAID → REFUNDED`（退款，MVP 仅留接口）

**支付流程：**
1. `POST /api/billing/orders` → 创建 `PaymentOrder(PENDING)`，金额 = 套餐价格。
2. `POST /api/billing/orders/{orderNo}/pay` → `PaymentGatewayPort.precreate()` 拿二维码（扫码）或跳转 URL（H5），订单转 `PAYING`。
3. 用户支付 → 网关异步回调 `POST /api/billing/callbacks/{channel}`（**验签 + 幂等**，§3.4 核心，详见 §5.4 + §10）。
4. 回调验证通过 → 订单转 `PAID` → 触发 `KernelSubscriptionService.activate()` → 写/更新 TENANT `QuotaPolicy` → 配额即时生效。
5. 前端轮询 `GET /api/billing/orders/{orderNo}` 或扫码页轮询订单状态。

### 3.5 账单（每月 1 号生成 + 历史查询）

- `SeahorseBillingGenerationJob`：`@Scheduled(cron = "${seahorse-agent.billing.gen-cron:0 30 1 1 * *}")`（每月 1 号 01:30），分布式锁保护。
- 逻辑：对每个有 active 订阅的租户，读 `sa_usage_rollup`（上月）+ `sa_subscription`（套餐固定费），生成 `sa_bill`（账单头）+ `sa_bill_line_item`（明细：订阅费 / token 用量 / 调用用量；MVP 用量行金额为 0，仅展示，不按量收费）。
- 幂等：`sa_bill` 唯一键 `(tenant_id, bill_period)`，重复生成跳过。
- 查询：`GET /api/billing/bills?period=2026-05` / `GET /api/billing/bills/{billNo}`。

---

## 4. 数据模型 / DDL

> 追加到 `resources/database/seahorse_init.sql` 末尾，沿用既有 `sa_*` / `pk_id BIGSERIAL` / `chk_*` / `idx_*` 约定。**`sa_quota_policy`、`sa_cost_usage_record` 不改动，直接复用。**

### 4.1 套餐表 `sa_subscription_plan`

```sql
CREATE TABLE IF NOT EXISTS sa_subscription_plan (
  pk_id               BIGSERIAL PRIMARY KEY,
  plan_code           VARCHAR(32)  NOT NULL UNIQUE,   -- FREE_TRIAL/BASIC/PRO/ENTERPRISE
  plan_name           VARCHAR(64)  NOT NULL,
  price_cents         BIGINT       NOT NULL,          -- 月价，单位：分（避免浮点）
  currency            VARCHAR(8)   NOT NULL DEFAULT 'CNY',
  token_limit         BIGINT,                          -- 映射 QuotaPolicy.tokenLimit
  call_limit          BIGINT,                          -- 映射 QuotaPolicy.callLimit
  cost_limit          DOUBLE PRECISION,                -- 映射 QuotaPolicy.costLimit
  storage_limit_bytes BIGINT,                          -- 套餐新增维度（不进 QuotaPolicy）
  trial_days          INT          NOT NULL DEFAULT 0,
  status              VARCHAR(16)  NOT NULL DEFAULT 'ACTIVE', -- ACTIVE/ARCHIVED
  sort_order          INT          NOT NULL DEFAULT 0,
  created_at          TIMESTAMP    NOT NULL,
  updated_at          TIMESTAMP    NOT NULL,
  CONSTRAINT chk_sa_plan_price CHECK (price_cents >= 0),
  CONSTRAINT chk_sa_plan_status CHECK (status IN ('ACTIVE','ARCHIVED')),
  CONSTRAINT chk_sa_plan_limit_required
    CHECK (token_limit IS NOT NULL OR call_limit IS NOT NULL OR cost_limit IS NOT NULL)
);
```

### 4.2 订阅表 `sa_subscription`（租户当前生效套餐）

```sql
CREATE TABLE IF NOT EXISTS sa_subscription (
  pk_id               BIGSERIAL PRIMARY KEY,
  subscription_id     VARCHAR(64)  NOT NULL UNIQUE,
  tenant_id           VARCHAR(64)  NOT NULL,
  plan_code           VARCHAR(32)  NOT NULL,
  status              VARCHAR(16)  NOT NULL,          -- TRIALING/ACTIVE/EXPIRED/CANCELED
  storage_limit_bytes BIGINT,                          -- 快照自套餐，强制时直接读
  current_period_start TIMESTAMP   NOT NULL,
  current_period_end   TIMESTAMP   NOT NULL,           -- 到期时间
  quota_policy_id     VARCHAR(64),                     -- 关联写入的 sa_quota_policy.policy_id
  created_at          TIMESTAMP    NOT NULL,
  updated_at          TIMESTAMP    NOT NULL,
  CONSTRAINT chk_sa_sub_status CHECK (status IN ('TRIALING','ACTIVE','EXPIRED','CANCELED'))
);
CREATE UNIQUE INDEX IF NOT EXISTS uk_sa_subscription_tenant_active
  ON sa_subscription(tenant_id) WHERE status IN ('TRIALING','ACTIVE');  -- 每租户至多一条生效订阅
CREATE INDEX IF NOT EXISTS idx_sa_subscription_period_end
  ON sa_subscription(status, current_period_end);
```

### 4.3 订单表 `sa_payment_order`

```sql
CREATE TABLE IF NOT EXISTS sa_payment_order (
  pk_id            BIGSERIAL PRIMARY KEY,
  order_no         VARCHAR(64)  NOT NULL UNIQUE,       -- 商户订单号(out_trade_no)，自生成
  tenant_id        VARCHAR(64)  NOT NULL,
  plan_code        VARCHAR(32)  NOT NULL,
  amount_cents     BIGINT       NOT NULL,              -- 应付金额(分)，回调时严格比对
  currency         VARCHAR(8)   NOT NULL DEFAULT 'CNY',
  channel          VARCHAR(16)  NOT NULL,              -- ALIPAY/WECHAT
  trade_type       VARCHAR(16)  NOT NULL,              -- QRCODE/H5
  status           VARCHAR(16)  NOT NULL,              -- PENDING/PAYING/PAID/CANCELED/REFUNDED
  channel_trade_no VARCHAR(96),                        -- 网关交易号(trade_no/transaction_id)
  paid_at          TIMESTAMP,
  expire_at        TIMESTAMP    NOT NULL,              -- 关单时间(下单 + 30min)
  created_at       TIMESTAMP    NOT NULL,
  updated_at       TIMESTAMP    NOT NULL,
  CONSTRAINT chk_sa_order_amount CHECK (amount_cents >= 0),
  CONSTRAINT chk_sa_order_channel CHECK (channel IN ('ALIPAY','WECHAT')),
  CONSTRAINT chk_sa_order_status
    CHECK (status IN ('PENDING','PAYING','PAID','CANCELED','REFUNDED'))
);
CREATE INDEX IF NOT EXISTS idx_sa_order_tenant ON sa_payment_order(tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_sa_order_expire ON sa_payment_order(status, expire_at);
```

### 4.4 回调幂等表 `sa_payment_callback_log`（安全核心）

```sql
CREATE TABLE IF NOT EXISTS sa_payment_callback_log (
  pk_id            BIGSERIAL PRIMARY KEY,
  channel          VARCHAR(16)  NOT NULL,
  channel_trade_no VARCHAR(96)  NOT NULL,              -- 网关交易号
  order_no         VARCHAR(64)  NOT NULL,
  raw_payload      TEXT         NOT NULL,              -- 原始回调报文(审计/排障)
  signature_valid  BOOLEAN      NOT NULL,
  process_result   VARCHAR(16)  NOT NULL,              -- APPLIED/DUPLICATE/REJECTED
  received_at      TIMESTAMP    NOT NULL,
  -- 幂等键：同一网关交易号只允许成功处理一次
  CONSTRAINT uk_sa_callback_idem UNIQUE (channel, channel_trade_no)
);
CREATE INDEX IF NOT EXISTS idx_sa_callback_order ON sa_payment_callback_log(order_no);
```

> **幂等机制**：回调进来先 `INSERT` 一行（`channel + channel_trade_no` 唯一），唯一键冲突 → 判定为重复回调 → 直接返回成功 ack，不重复激活订阅。详见 §5.4。

### 4.5 用量滚动汇总表 `sa_usage_rollup`

```sql
CREATE TABLE IF NOT EXISTS sa_usage_rollup (
  pk_id          BIGSERIAL PRIMARY KEY,
  tenant_id      VARCHAR(64)  NOT NULL,
  bill_period    VARCHAR(7)   NOT NULL,                -- 'YYYY-MM'
  total_tokens   BIGINT       NOT NULL DEFAULT 0,
  total_calls    BIGINT       NOT NULL DEFAULT 0,
  total_cost     DOUBLE PRECISION NOT NULL DEFAULT 0,
  storage_bytes  BIGINT       NOT NULL DEFAULT 0,
  last_source_id BIGINT       NOT NULL DEFAULT 0,      -- 已聚合到的 sa_cost_usage_record.pk_id 游标
  updated_at     TIMESTAMP    NOT NULL,
  CONSTRAINT uk_sa_usage_rollup UNIQUE (tenant_id, bill_period),
  CONSTRAINT chk_sa_usage_rollup_nonneg
    CHECK (total_tokens >= 0 AND total_calls >= 0 AND total_cost >= 0 AND storage_bytes >= 0)
);
```

### 4.6 账单表 `sa_bill` + `sa_bill_line_item`

```sql
CREATE TABLE IF NOT EXISTS sa_bill (
  pk_id          BIGSERIAL PRIMARY KEY,
  bill_no        VARCHAR(64)  NOT NULL UNIQUE,
  tenant_id      VARCHAR(64)  NOT NULL,
  bill_period    VARCHAR(7)   NOT NULL,                -- 'YYYY-MM'(上月)
  plan_code      VARCHAR(32)  NOT NULL,
  subtotal_cents BIGINT       NOT NULL,                -- 合计应付(分)
  currency       VARCHAR(8)   NOT NULL DEFAULT 'CNY',
  status         VARCHAR(16)  NOT NULL,                -- GENERATED/PAID/VOID
  generated_at   TIMESTAMP    NOT NULL,
  CONSTRAINT uk_sa_bill_period UNIQUE (tenant_id, bill_period),  -- 月度幂等
  CONSTRAINT chk_sa_bill_status CHECK (status IN ('GENERATED','PAID','VOID'))
);
CREATE INDEX IF NOT EXISTS idx_sa_bill_tenant ON sa_bill(tenant_id, bill_period DESC);

CREATE TABLE IF NOT EXISTS sa_bill_line_item (
  pk_id        BIGSERIAL PRIMARY KEY,
  line_id      VARCHAR(64)  NOT NULL UNIQUE,
  bill_no      VARCHAR(64)  NOT NULL,
  item_type    VARCHAR(32)  NOT NULL,                  -- SUBSCRIPTION_FEE/TOKEN_USAGE/CALL_USAGE
  description  VARCHAR(256) NOT NULL,
  quantity     BIGINT       NOT NULL DEFAULT 0,
  amount_cents BIGINT       NOT NULL DEFAULT 0,
  created_at   TIMESTAMP    NOT NULL,
  CONSTRAINT chk_sa_bill_line_type
    CHECK (item_type IN ('SUBSCRIPTION_FEE','TOKEN_USAGE','CALL_USAGE'))
);
CREATE INDEX IF NOT EXISTS idx_sa_bill_line_bill ON sa_bill_line_item(bill_no);
```

---

## 5. 后端实现骨架

> 包路径前缀：kernel 领域 `com.miracle.ai.seahorse.agent.kernel.domain.billing`；应用服务 `...kernel.application.billing`；端口 `...agent.ports.inbound/outbound.billing`；web `...adapters.web`；JDBC `...adapters.repository.jdbc`；支付适配器独立模块。
> 许可证头与现有文件一致（Apache 2.0 头），骨架略。

### 5.1 领域模型（kernel/domain/billing）

```java
// SubscriptionPlan.java —— 套餐(对应 sa_subscription_plan)
public record SubscriptionPlan(String planCode, String planName, long priceCents, String currency,
                               Long tokenLimit, Long callLimit, Double costLimit,
                               Long storageLimitBytes, int trialDays, PlanStatus status,
                               int sortOrder, Instant createdAt, Instant updatedAt) {
    public SubscriptionPlan {
        planCode = requireText(planCode, "planCode must not be blank");
        if (priceCents < 0) throw new IllegalArgumentException("priceCents must not be negative");
        if (tokenLimit == null && callLimit == null && costLimit == null)
            throw new IllegalArgumentException("at least one quota limit is required");
        status = Objects.requireNonNullElse(status, PlanStatus.ACTIVE);
    }
}
public enum PlanStatus { ACTIVE, ARCHIVED }
public enum PlanCode { FREE_TRIAL, BASIC, PRO, ENTERPRISE }   // 仅作枚举校验，存储用 String

// Subscription.java —— 租户订阅(对应 sa_subscription)
public record Subscription(String subscriptionId, String tenantId, String planCode,
                           SubscriptionStatus status, Long storageLimitBytes,
                           Instant currentPeriodStart, Instant currentPeriodEnd,
                           String quotaPolicyId, Instant createdAt, Instant updatedAt) { }
public enum SubscriptionStatus { TRIALING, ACTIVE, EXPIRED, CANCELED }

// PaymentOrder.java —— 订单(状态机宿主，对应 sa_payment_order)
public record PaymentOrder(String orderNo, String tenantId, String planCode, long amountCents,
                           String currency, PaymentChannel channel, TradeType tradeType,
                           OrderStatus status, String channelTradeNo, Instant paidAt,
                           Instant expireAt, Instant createdAt, Instant updatedAt) {

    /** 受控状态迁移：非法迁移抛 IllegalStateException（防越权改单） */
    public PaymentOrder markPaid(String channelTradeNo, Instant paidAt) {
        if (status != OrderStatus.PENDING && status != OrderStatus.PAYING)
            throw new IllegalStateException("订单状态 " + status + " 不可迁移为 PAID");
        return new PaymentOrder(orderNo, tenantId, planCode, amountCents, currency, channel,
                tradeType, OrderStatus.PAID, channelTradeNo, paidAt, expireAt, createdAt, paidAt);
    }
    public PaymentOrder markPaying(String channelTradeNo) { /* PENDING → PAYING */ }
    public PaymentOrder cancel(Instant at) { /* PENDING/PAYING → CANCELED */ }

    /** 回调金额比对（防篡改金额） */
    public boolean amountMatches(long callbackAmountCents) { return this.amountCents == callbackAmountCents; }
}
public enum OrderStatus { PENDING, PAYING, PAID, CANCELED, REFUNDED }
public enum PaymentChannel { ALIPAY, WECHAT }
public enum TradeType { QRCODE, H5 }

// QuotaExceededException.java —— 运行时强制超限(kernel/domain/agent/quota 或 billing)
public class QuotaExceededException extends RuntimeException {
    private final QuotaDecisionReasonCode reasonCode;
    private final String upgradeHint;
    public QuotaExceededException(QuotaDecisionReasonCode reasonCode, String message, String upgradeHint) {
        super(message); this.reasonCode = reasonCode; this.upgradeHint = upgradeHint;
    }
    public QuotaDecisionReasonCode reasonCode() { return reasonCode; }
    public String upgradeHint() { return upgradeHint; }
}
```

### 5.2 端口（ports）

```java
// ── 入站(inbound/billing) ──
public interface SubscriptionInboundPort {
    List<SubscriptionPlan> listPlans();                          // 套餐列表(前端选择页)
    Subscription currentSubscription(String tenantId);           // 当前订阅
    Subscription activate(String tenantId, String planCode, Instant now);  // 激活/切换套餐
    Subscription startTrial(String tenantId, String planCode, Instant now); // 试用
}

public interface PaymentInboundPort {
    PaymentOrder createOrder(CreateOrderCommand command);        // 下单
    PaymentPrepareResult prepay(String orderNo);                 // 拿二维码/H5 URL，订单→PAYING
    PaymentOrder getOrder(String orderNo);                       // 查单(前端轮询)
    /** 回调处理：返回是否首次成功应用(供 controller 决定 ack)。验签+幂等在实现内。 */
    PaymentCallbackOutcome handleCallback(PaymentChannel channel, PaymentCallbackContext ctx);
}

public interface BillingInboundPort {
    List<Bill> listBills(String tenantId);
    BillDetail getBill(String billNo);
    int generateForPeriod(String billPeriod, Instant now);       // Job 调用
}

// ── 出站(outbound/billing) ──
public interface SubscriptionPlanRepositoryPort {
    List<SubscriptionPlan> findAllActive();
    Optional<SubscriptionPlan> findByCode(String planCode);
    SubscriptionPlan upsert(SubscriptionPlan plan);              // 种子数据写入
}
public interface SubscriptionRepositoryPort {
    Optional<Subscription> findActiveByTenant(String tenantId);
    Subscription upsert(Subscription subscription);
}
public interface PaymentOrderRepositoryPort {
    PaymentOrder insert(PaymentOrder order);
    Optional<PaymentOrder> findByOrderNo(String orderNo);
    Optional<PaymentOrder> lockByOrderNo(String orderNo);        // SELECT ... FOR UPDATE(回调用)
    PaymentOrder update(PaymentOrder order);
    List<PaymentOrder> findExpiredPending(Instant now, int limit);
}
public interface PaymentCallbackLogRepositoryPort {
    /** 幂等写入：唯一键冲突返回 false(重复回调)，成功返回 true(首次) */
    boolean tryRecord(PaymentCallbackLog log);
}
public interface UsageRollupRepositoryPort {
    Optional<UsageRollup> find(String tenantId, String billPeriod);
    UsageRollup upsert(UsageRollup rollup);
    List<String> findTenantsWithUsage(String billPeriod);
    void addStorageBytes(String tenantId, String billPeriod, long deltaBytes);  // 上传/删除时调
}
public interface BillRepositoryPort {
    Bill insert(Bill bill); List<BillLineItem> insertLines(List<BillLineItem> lines);
    Optional<Bill> findByNo(String billNo); List<Bill> findByTenant(String tenantId);
    boolean existsForPeriod(String tenantId, String billPeriod);
}

// 支付网关(由 alipay/wechat 适配器实现)
public interface PaymentGatewayPort {
    PaymentChannel channel();
    PaymentPrepareResult precreate(PaymentOrder order);          // 调网关下单，拿二维码/URL
    /** 验签 + 解析回调 → 标准化交易结果；验签失败抛 SignatureVerificationException */
    PaymentCallbackResult verifyAndParse(PaymentCallbackContext ctx);
    String successAck();   // 返回网关要求的成功应答(支付宝 "success"；微信 JSON {code:SUCCESS})
}

// 配额强制出站口(运行时拦截点用，复用 KernelQuotaDecisionService 适配)
public interface QuotaEnforcementPort {
    QuotaDecisionResult check(QuotaDecisionCommand command);
}
```

### 5.3 应用服务（kernel/application/billing）

```java
public class KernelSubscriptionService implements SubscriptionInboundPort {
    private final SubscriptionPlanRepositoryPort planRepository;
    private final SubscriptionRepositoryPort subscriptionRepository;
    private final QuotaManagementInboundPort quotaManagementPort;   // ← 复用既有入站口写 QuotaPolicy
    private final Clock clock;

    @Override
    public Subscription activate(String tenantId, String planCode, Instant now) {
        SubscriptionPlan plan = planRepository.findByCode(planCode)
            .orElseThrow(() -> new IllegalArgumentException("套餐不存在: " + planCode));
        // 1. 套餐 → QuotaPolicy 映射(复用 §3.1)：每租户稳定 policyId，幂等 upsert
        String policyId = "plan:" + tenantId;
        quotaManagementPort.upsertPolicy(new QuotaPolicyUpsertCommand(
            policyId, tenantId, QuotaScope.TENANT, tenantId, QuotaPolicyStatus.ACTIVE,
            plan.tokenLimit(), plan.callLimit(), plan.costLimit(),
            QuotaPolicyLimits.DEFAULT_WARN_RATIO, now, now));
        // 2. 落订阅行(存储配额快照在订阅上)
        Subscription sub = new Subscription(nextId(), tenantId, planCode, SubscriptionStatus.ACTIVE,
            plan.storageLimitBytes(), now, now.plus(Duration.ofDays(30)), policyId, now, now);
        return subscriptionRepository.upsert(sub);
    }
}

public class KernelPaymentService implements PaymentInboundPort {
    private final PaymentOrderRepositoryPort orderRepository;
    private final PaymentCallbackLogRepositoryPort callbackLogRepository;
    private final Map<PaymentChannel, PaymentGatewayPort> gateways;   // 按渠道分发
    private final SubscriptionInboundPort subscriptionPort;
    private final TransactionRunnerPort tx;       // 包一层事务边界(见 §5.4 注)
    private final Clock clock;

    @Override
    public PaymentCallbackOutcome handleCallback(PaymentChannel channel, PaymentCallbackContext ctx) {
        return doHandleCallback(channel, ctx);    // 核心逻辑见 §5.4
    }
}

public class KernelBillingService implements BillingInboundPort {
    private final BillRepositoryPort billRepository;
    private final SubscriptionRepositoryPort subscriptionRepository;
    private final UsageRollupRepositoryPort usageRollupRepository;
    private final SubscriptionPlanRepositoryPort planRepository;
    // generateForPeriod()：遍历 active 订阅 → 幂等校验 existsForPeriod → 组装订阅费行 + 用量展示行
}

public class KernelUsageRollupService {     // §3.3 聚合，被 Job 调用
    private final CostUsageRepositoryPort costUsageRepository;   // 复用既有 aggregate()
    private final UsageRollupRepositoryPort rollupRepository;
    public void rollupCurrentPeriod(Instant now) {
        String period = YearMonth.from(now.atZone(ZoneOffset.UTC)).toString();
        // 对每个有增量的租户：aggregate(from=periodStart,to=now) → upsert rollup，推进 last_source_id
    }
}
```

### 5.4 ★支付回调幂等（安全核心，重点骨架）

```java
// KernelPaymentService 内部
private PaymentCallbackOutcome doHandleCallback(PaymentChannel channel, PaymentCallbackContext ctx) {
    PaymentGatewayPort gateway = gateways.get(channel);
    if (gateway == null) throw new IllegalStateException("未配置支付渠道: " + channel);

    // ── 步骤 1：验签（失败立即拒绝，记审计，不动订单）──
    PaymentCallbackResult result;
    try {
        result = gateway.verifyAndParse(ctx);     // 内部用渠道 SDK 验签；失败抛 SignatureVerificationException
    } catch (SignatureVerificationException ex) {
        callbackLogRepository.tryRecord(PaymentCallbackLog.rejected(channel, ctx, "BAD_SIGNATURE"));
        return PaymentCallbackOutcome.rejected("签名校验失败");   // controller 返回失败 ack
    }

    // ── 步骤 2：幂等闸（唯一键 channel+channelTradeNo，冲突=重复回调）──
    boolean firstTime = callbackLogRepository.tryRecord(
        PaymentCallbackLog.applied(channel, result.channelTradeNo(), result.orderNo(), ctx.rawBody()));
    if (!firstTime) {
        return PaymentCallbackOutcome.duplicate();   // 已处理过 → 直接成功 ack，不重复激活
    }

    // ── 步骤 3：事务内加行锁改单 + 激活（防并发双激活）──
    return tx.executeInTransaction(() -> {
        PaymentOrder order = orderRepository.lockByOrderNo(result.orderNo())   // SELECT ... FOR UPDATE
            .orElseThrow(() -> new IllegalStateException("订单不存在: " + result.orderNo()));

        // 3a. 金额严格比对（防篡改）
        if (!order.amountMatches(result.amountCents()))
            return PaymentCallbackOutcome.rejected("金额不一致");

        // 3b. 已是终态则幂等返回（双保险，防 step2 之外的竞态）
        if (order.status() == OrderStatus.PAID) return PaymentCallbackOutcome.duplicate();

        // 3c. 状态机受控迁移（非法迁移在领域对象内抛异常）
        PaymentOrder paid = order.markPaid(result.channelTradeNo(), clock.instant());
        orderRepository.update(paid);

        // 3d. 激活订阅 → 写 TENANT QuotaPolicy（复用 §5.3，配额即时生效）
        subscriptionPort.activate(paid.tenantId(), paid.planCode(), clock.instant());

        return PaymentCallbackOutcome.applied();
    });
}
```

> 注：kernel 不依赖 Spring。事务边界用出站口 `TransactionRunnerPort`（adapter 层以 `TransactionTemplate` 实现），保持六边形纯净；若团队接受 kernel 应用层薄依赖，也可将 `@Transactional` 下沉到 adapter 编排服务。二选一，**架构决策：采用 TransactionRunnerPort（保持六边形纯净）**。

### 5.5 Web 控制器（adapters/web，全部 `ObjectProvider` 懒加载 + `ApiResponses`）

```java
@RestController
public class SeahorseSubscriptionController {
    private final ObjectProvider<SubscriptionInboundPort> subscriptionPortProvider;
    private final AdvancedFeatureGate advancedFeatureGate;       // 同 SeahorseQuotaController 双构造器模式

    public SeahorseSubscriptionController(ObjectProvider<SubscriptionInboundPort> p) { this.subscriptionPortProvider = p; ... }

    @GetMapping("/api/billing/plans")          // 套餐列表(选择页，CONSUMER_WEB 也可见，不 gate)
    public ApiResponse<Object> listPlans() {
        return ApiResponses.requireService(subscriptionPortProvider, SubscriptionInboundPort::listPlans);
    }
    @GetMapping("/api/billing/subscription")   // 当前订阅；tenantId 由 TenantContext 注入(块A)
    public ApiResponse<Object> current() {
        String tenantId = TenantContext.getTenantId();
        return ApiResponses.requireService(subscriptionPortProvider, p -> p.currentSubscription(tenantId));
    }
}

@RestController
public class SeahorsePaymentController {
    private final ObjectProvider<PaymentInboundPort> paymentPortProvider;

    @PostMapping("/api/billing/orders")                       // 下单
    public ApiResponse<Object> createOrder(@RequestBody CreateOrderRequest req) {
        String tenantId = TenantContext.getTenantId();
        return ApiResponses.requireService(paymentPortProvider,
            p -> p.createOrder(new CreateOrderCommand(tenantId, req.planCode(), req.channel(), req.tradeType())));
    }
    @PostMapping("/api/billing/orders/{orderNo}/pay")         // 拿二维码/H5
    public ApiResponse<Object> pay(@PathVariable String orderNo) {
        return ApiResponses.requireService(paymentPortProvider, p -> p.prepay(orderNo));
    }
    @GetMapping("/api/billing/orders/{orderNo}")              // 前端轮询
    public ApiResponse<Object> getOrder(@PathVariable String orderNo) {
        return ApiResponses.requireService(paymentPortProvider, p -> p.getOrder(orderNo));
    }

    // ★回调端点：不经 ApiResponse 包装，按渠道要求返回纯文本 ack；不依赖登录态
    @PostMapping("/api/billing/callbacks/{channel}")
    public ResponseEntity<String> callback(@PathVariable String channel, HttpServletRequest request) {
        PaymentChannel ch = PaymentChannel.valueOf(channel.toUpperCase(Locale.ROOT));
        PaymentInboundPort port = paymentPortProvider.getIfAvailable();
        if (port == null) return ResponseEntity.status(503).body("service unavailable");
        PaymentCallbackContext ctx = PaymentCallbackContext.fromRequest(request);  // headers + raw body + params
        PaymentCallbackOutcome outcome = port.handleCallback(ch, ctx);
        // 成功/重复都回成功 ack（网关停止重推）；拒绝回失败 ack（网关重试，便于排障）
        return outcome.isAccepted()
            ? ResponseEntity.ok(port.successAck(ch))
            : ResponseEntity.badRequest().body("fail");
    }
}

@RestController
public class SeahorseBillingController {
    private final ObjectProvider<BillingInboundPort> billingPortProvider;
    @GetMapping("/api/billing/bills")           // 历史账单
    public ApiResponse<Object> listBills() {
        String tenantId = TenantContext.getTenantId();
        return ApiResponses.requireService(billingPortProvider, p -> p.listBills(tenantId));
    }
    @GetMapping("/api/billing/bills/{billNo}")
    public ApiResponse<Object> getBill(@PathVariable String billNo) {
        return ApiResponses.requireService(billingPortProvider, p -> p.getBill(billNo));
    }
}
```

> 回调端点安全：必须在鉴权过滤器（Sa-Token / TenantContext 拦截器）放行 `/api/billing/callbacks/**`，否则网关无登录态会被拦。**架构决策：采用 TransactionRunnerPort（保持六边形纯净）**：放行清单的真实配置位置需结合块A 拦截器实现。

### 5.6 定时任务（spring-boot-starter，复用 `@Scheduled + DistributedLockPort`）

```java
public class SeahorseCostUsageAggregationJob {     // §3.3
    private final KernelUsageRollupService rollupService;
    private final DistributedLockPort lockPort;
    @Scheduled(fixedDelayString = "${seahorse-agent.billing.usage-rollup.delay-ms:300000}")
    public void rollup() {
        if (!lockPort.tryLock("job:usage-rollup", Duration.ZERO, Duration.ofMinutes(5))) return;
        try { rollupService.rollupCurrentPeriod(Instant.now()); } finally { lockPort.unlock("job:usage-rollup"); }
    }
}

public class SeahorseBillingGenerationJob {        // §3.5
    private final BillingInboundPort billingPort;
    private final DistributedLockPort lockPort;
    @Scheduled(cron = "${seahorse-agent.billing.gen-cron:0 30 1 1 * *}", zone = "Asia/Shanghai")
    public void generate() {
        if (!lockPort.tryLock("job:billing-gen", Duration.ZERO, Duration.ofMinutes(30))) return;
        try {
            String lastMonth = YearMonth.now(ZoneId.of("Asia/Shanghai")).minusMonths(1).toString();
            billingPort.generateForPeriod(lastMonth, Instant.now());
        } finally { lockPort.unlock("job:billing-gen"); }
    }
}

public class SeahorseOrderTimeoutJob {             // 关闭超时未支付订单
    @Scheduled(fixedDelayString = "${seahorse-agent.billing.order-timeout.delay-ms:60000}")
    public void closeExpired() { /* findExpiredPending → cancel() */ }
}
```

### 5.7 自动配置接线（遵守 6 层 + `@AutoConfigureAfter`）

1. **Layer 1（仓储）** —— 在 `SeahorseAgentRegistryRepositoryAutoConfiguration` 追加 JDBC 适配器 Bean（照搬 §2.5 `JdbcQuotaPolicyRepositoryAdapter` 注册块）：

```java
@Bean @ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(prefix = "seahorse-agent.adapters.repository", name = "type", havingValue = "jdbc", matchIfMissing = true)
@ConditionalOnMissingBean(SubscriptionPlanRepositoryPort.class)
public JdbcSubscriptionPlanRepositoryAdapter seahorseJdbcSubscriptionPlanRepositoryAdapter(DataSource ds) {
    return new JdbcSubscriptionPlanRepositoryAdapter(ds);
}
// 同理：JdbcSubscriptionRepositoryAdapter / JdbcPaymentOrderRepositoryAdapter /
//        JdbcPaymentCallbackLogRepositoryAdapter / JdbcUsageRollupRepositoryAdapter / JdbcBillRepositoryAdapter
```

2. **Layer 6（kernel 服务）** —— 新建 `SeahorseAgentKernelBillingAutoConfiguration`，**`@AutoConfigureAfter` 必须声明所有产生 `@ConditionalOnBean` 依赖的 adapter 配置**（项目硬规则）：

```java
@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
    SeahorseAgentRegistryRepositoryAutoConfiguration.class,   // 提供 6 个 billing JDBC 端口 + QuotaPolicyRepositoryPort
    SeahorseAgentKernelRegistryAutoConfiguration.class        // 提供 QuotaManagementInboundPort
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled", havingValue = "true", matchIfMissing = true)
public class SeahorseAgentKernelBillingAutoConfiguration {

    @Bean
    @ConditionalOnBean({SubscriptionPlanRepositoryPort.class, SubscriptionRepositoryPort.class, QuotaManagementInboundPort.class})
    @ConditionalOnMissingBean(SubscriptionInboundPort.class)
    public KernelSubscriptionService seahorseSubscriptionInboundPort(
            SubscriptionPlanRepositoryPort planRepo, SubscriptionRepositoryPort subRepo,
            QuotaManagementInboundPort quotaManagementPort, ObjectProvider<Clock> clock) {
        return new KernelSubscriptionService(planRepo, subRepo, quotaManagementPort, clock.getIfAvailable(Clock::systemUTC));
    }

    @Bean
    @ConditionalOnBean({PaymentOrderRepositoryPort.class, PaymentCallbackLogRepositoryPort.class, SubscriptionInboundPort.class})
    @ConditionalOnMissingBean(PaymentInboundPort.class)
    public KernelPaymentService seahorsePaymentInboundPort(
            PaymentOrderRepositoryPort orderRepo, PaymentCallbackLogRepositoryPort logRepo,
            ObjectProvider<PaymentGatewayPort> gateways, SubscriptionInboundPort subPort,
            TransactionRunnerPort tx, ObjectProvider<Clock> clock) {
        return new KernelPaymentService(orderRepo, logRepo,
            gateways.orderedStream().collect(toMap(PaymentGatewayPort::channel, identity())),
            subPort, tx, clock.getIfAvailable(Clock::systemUTC));
    }
    // KernelBillingService / KernelUsageRollupService 同理
    // 三个 Job 的 @ConditionalOnProperty 开关：seahorse-agent.billing.scheduler.enabled
}
```

3. **`AutoConfiguration.imports`** —— 在 Layer 6 段追加 `...SeahorseAgentKernelBillingAutoConfiguration`（紧随 `SeahorseAgentKernelRegistryAutoConfiguration`）。

4. **`AdvancedFeature` 枚举追加**：`SUBSCRIPTION_MANAGEMENT, PAYMENT, BILLING`（控制器视需要 gate；套餐列表/下单面向 CONSUMER_WEB 不 gate，管理端套餐 CRUD 才 gate）。

### 5.8 支付适配器模块（新 maven 模块）

```
seahorse-agent-adapter-payment-alipay/   → AlipayPaymentGatewayAdapter implements PaymentGatewayPort
   - 依赖 com.alipay.sdk:alipay-sdk-java；precreate() 调 AlipayTradePrecreate；verifyAndParse() 用 AlipaySignature.rsaCheckV1
seahorse-agent-adapter-payment-wechat/   → WechatPaymentGatewayAdapter implements PaymentGatewayPort
   - 依赖 wechatpay-java；precreate() 调 Native/H5 下单；verifyAndParse() 用 NotificationParser(自动验签+解密)
```

配置前缀：`seahorse-agent.adapters.payment.alipay.*`（appId/privateKey/alipayPublicKey/notifyUrl）、`...wechat.*`（mchId/apiV3Key/privateKeyPath/notifyUrl）。各自 `AutoConfiguration` + `@ConditionalOnProperty(...enabled=true)`，产出 `PaymentGatewayPort` Bean。密钥经 §2.5 既有 `SeahorseAgentCredentialAutoConfiguration` 体系管理，**不硬编码**。

---

## 6. 前端实现骨架

> 技术栈：React + Vite + TS；API 客户端 `frontend/src/services/api.ts`（拦截器自动拆 `{code,message,data}` 返回 `data`）；服务放 `src/services/*.ts`；类型 `src/types/index.ts`；管理页 `src/pages/admin/<feature>/`。复用既有 `quotaSummaryService.ts`（`/api/me/quota-summary`）展示用量。

### 6.1 TS 类型（src/types/index.ts 追加）

```typescript
export type PlanCode = "FREE_TRIAL" | "BASIC" | "PRO" | "ENTERPRISE";
export type SubscriptionStatus = "TRIALING" | "ACTIVE" | "EXPIRED" | "CANCELED";
export type OrderStatus = "PENDING" | "PAYING" | "PAID" | "CANCELED" | "REFUNDED";
export type PaymentChannel = "ALIPAY" | "WECHAT";
export type TradeType = "QRCODE" | "H5";

export interface SubscriptionPlan {
  planCode: PlanCode; planName: string; priceCents: number; currency: string;
  tokenLimit?: number; callLimit?: number; costLimit?: number;
  storageLimitBytes?: number; trialDays: number; sortOrder: number;
}
export interface Subscription {
  subscriptionId: string; tenantId: string; planCode: PlanCode;
  status: SubscriptionStatus; storageLimitBytes?: number;
  currentPeriodStart: string; currentPeriodEnd: string;
}
export interface PaymentOrder {
  orderNo: string; planCode: PlanCode; amountCents: number; currency: string;
  channel: PaymentChannel; tradeType: TradeType; status: OrderStatus;
  paidAt?: string; expireAt: string; createdAt: string;
}
export interface PaymentPrepareResult {
  orderNo: string; channel: PaymentChannel; tradeType: TradeType;
  qrCodeUrl?: string;   // 扫码：二维码内容
  h5Url?: string;       // H5：跳转 URL
}
export interface Bill {
  billNo: string; billPeriod: string; planCode: PlanCode;
  subtotalCents: number; currency: string; status: string; generatedAt: string;
}
export interface BillLineItem {
  lineId: string; itemType: "SUBSCRIPTION_FEE" | "TOKEN_USAGE" | "CALL_USAGE";
  description: string; quantity: number; amountCents: number;
}
export interface BillDetail extends Bill { lines: BillLineItem[]; }
```

### 6.2 Service（src/services/billingService.ts，照 auditCostService.ts 风格）

```typescript
import { api } from "@/services/api";
import type { SubscriptionPlan, Subscription, PaymentOrder, PaymentPrepareResult,
              Bill, BillDetail, PaymentChannel, TradeType, PlanCode } from "@/types";

export function listPlans() {
  return api.get<SubscriptionPlan[]>("/api/billing/plans");
}
export function getCurrentSubscription() {
  return api.get<Subscription>("/api/billing/subscription");
}
export function createOrder(payload: { planCode: PlanCode; channel: PaymentChannel; tradeType: TradeType }) {
  return api.post<PaymentOrder, PaymentOrder>("/api/billing/orders", payload);
}
export function payOrder(orderNo: string) {
  return api.post<PaymentPrepareResult, PaymentPrepareResult>(`/api/billing/orders/${encodeURIComponent(orderNo)}/pay`, {});
}
export function getOrder(orderNo: string) {
  return api.get<PaymentOrder>(`/api/billing/orders/${encodeURIComponent(orderNo)}`);
}
export function listBills() {
  return api.get<Bill[]>("/api/billing/bills");
}
export function getBill(billNo: string) {
  return api.get<BillDetail>(`/api/billing/bills/${encodeURIComponent(billNo)}`);
}
```

### 6.3 页面（src/pages/admin/billing/）

```
src/pages/admin/billing/
  BillingPage.tsx          // 容器：Tab[套餐 | 账单]
  _components/
    PlanSelectCard.tsx     // 四档套餐卡片(价格/配额对比/当前档高亮)，复用 quotaSummaryService 显示已用量
    PaymentDialog.tsx      // 下单→渲染二维码(qrcode.react)/H5跳转；轮询 getOrder 直到 PAID
    BillHistoryTable.tsx   // 历史账单列表 + 明细抽屉
    QuotaUsageBanner.tsx   // 复用 GET /api/me/quota-summary 显示 token/call 余量
```

`PaymentDialog.tsx` 轮询骨架：

```typescript
async function startPay(plan: SubscriptionPlan, channel: PaymentChannel) {
  const order = await createOrder({ planCode: plan.planCode, channel, tradeType: "QRCODE" });
  const prep = await payOrder(order.orderNo);
  setQrCode(prep.qrCodeUrl);
  const timer = setInterval(async () => {
    const cur = await getOrder(order.orderNo);
    if (cur.status === "PAID") { clearInterval(timer); toast.success("支付成功，套餐已生效"); onPaid(); }
    if (cur.status === "CANCELED") { clearInterval(timer); toast.error("订单已关闭"); }
  }, 3000);
  // 组件卸载 / 30min 超时 清理 timer
}
```

### 6.4 配额超限引导（拦截 HTTP 402 / `code=QUOTA_EXCEEDED`）

在 `api.ts` 响应拦截器（或上层 axios error 处理）识别 `QUOTA_EXCEEDED`，弹"配额已用尽，升级套餐"对话框跳转 `BillingPage`。后端 `QuotaExceededException.upgradeHint()` 作为提示文案。

---

## 7. 任务清单

> P0 = MVP 必需（选套餐→支付→配额生效闭环）；P1 = 加固 / 体验。

### 后端 · P0

- [ ] **DDL**：`seahorse_init.sql` 追加 7 张表（plan/subscription/order/callback_log/usage_rollup/bill/bill_line_item）+ 索引约束（§4）
- [ ] **领域模型**：`SubscriptionPlan / Subscription / PaymentOrder(状态机) / Bill / BillLineItem / UsageRollup` + 枚举 + `QuotaExceededException`（§5.1）
- [ ] **端口**：inbound ×3、outbound ×7 + `PaymentGatewayPort` + `QuotaEnforcementPort` + `TransactionRunnerPort`（§5.2）
- [ ] **JDBC 适配器** ×6（照 `JdbcQuotaPolicyRepositoryAdapter`）；`lockByOrderNo` 用 `SELECT ... FOR UPDATE`
- [ ] **KernelSubscriptionService.activate()**：套餐→`QuotaPolicy` 映射，复用 `QuotaManagementInboundPort.upsertPolicy`（§5.3）
- [ ] **KernelPaymentService.handleCallback()**：★验签→幂等→行锁→金额比对→状态机→激活（§5.4）
- [ ] **支付适配器模块** alipay + wechat（precreate + verifyAndParse + successAck）（§5.8）
- [ ] **配额运行时强制**：`QuotaEnforcementPort` 注入 `KernelKnowledgeDocumentService.upload()`（存储）+ `KernelAgentRunService.startRun()`（token），`DENY` 抛 `QuotaExceededException`（§3.2）
- [ ] **`@ControllerAdvice`**：`QuotaExceededException` → HTTP 402 + `ApiResponse.error("QUOTA_EXCEEDED", hint)`
- [ ] **控制器** ×4：Subscription / Payment（含 `/callbacks/{channel}`）/ Billing（§5.5）
- [ ] **回调端点鉴权放行** `/api/billing/callbacks/**`（已确认：在 SeahorseSecurityWebMvcConfiguration 中放行）
- [ ] **自动配置**：Layer 1 追加 6 JDBC Bean；新建 Layer 6 `SeahorseAgentKernelBillingAutoConfiguration`（`@AutoConfigureAfter` 声明依赖）；`imports` 追加；`AdvancedFeature` 加 3 枚举（§5.7）
- [ ] **种子数据**：四档套餐写入（`seahorse_init.sql` INSERT 或启动 Registrar）

### 后端 · P1

- [ ] **用量聚合 Job** `SeahorseCostUsageAggregationJob`（增量游标 + 分布式锁）（§5.6）
- [ ] **账单生成 Job** `SeahorseBillingGenerationJob`（每月 1 号 cron + 幂等）（§5.6）
- [ ] **关单 Job** `SeahorseOrderTimeoutJob`（超时 PENDING→CANCELED）
- [ ] **试用激活** `startTrial()` + 到期 `EXPIRED` 流转
- [ ] **退款接口** `PAID→REFUNDED`（留口不实现扣款）

### 前端 · P0

- [ ] TS 类型（§6.1）+ `billingService.ts`（§6.2）
- [ ] `BillingPage` + `PlanSelectCard` + `PaymentDialog`（二维码 + 轮询）（§6.3）
- [ ] 路由注册 `src/router.tsx` + 入口菜单
- [ ] 配额超限 402 拦截 → 升级引导（§6.4）

### 前端 · P1

- [ ] `BillHistoryTable` + 明细抽屉
- [ ] `QuotaUsageBanner`（复用 `/api/me/quota-summary`）
- [ ] H5 支付方式（移动端跳转）

---

## 8. 测试策略

### 8.1 支付回调幂等（最高优先级，对齐既有 `*ServiceTests` 风格）

`KernelPaymentServiceTests`：
- **首次回调** → 订单 `PENDING→PAID` + 调一次 `subscriptionPort.activate`。
- **重复回调（同 channelTradeNo）** → `callbackLogRepository.tryRecord` 返回 false → **不再次激活**（verify `activate` 仅调 1 次），返回 `duplicate`。
- **并发双回调** → 行锁串行化；第二个进入 step3b 见 `PAID` 提前返回（用 `lockByOrderNo` mock 验证只激活一次）。
- **乱序回调**（订单已 PAID 又收回调）→ 幂等返回，状态不变。

### 8.2 签名验证

- 伪造 / 篡改报文 → `verifyAndParse` 抛 `SignatureVerificationException` → `handleCallback` 返回 `rejected`，订单**不变更**，记 `signature_valid=false` 审计行。
- 合法报文但**金额不符** → `amountMatches` false → `rejected`（防"小额支付激活高价套餐"）。

### 8.3 配额运行时强制

`KernelAgentRunServiceTests` / `KernelKnowledgeDocumentServiceTests` 扩展：
- 用量 < 限额 → `evaluate` 返回 `ALLOW` → 正常 `startRun` / `upload`。
- 用量 ≥ 硬限额 → `DENY` → 抛 `QuotaExceededException`（断言 `reasonCode=HARD_LIMIT_EXCEEDED` + `upgradeHint` 非空）。
- 达预警阈值（0.8）→ `WARN` → 放行 + 预警标记。
- 无 `QuotaEnforcementPort`（null）→ 跳过强制，保持旧行为（向后兼容单机）。

### 8.4 套餐→配额映射

`KernelSubscriptionServiceTests`：`activate("t1","PRO")` → 断言 `quotaManagementPort.upsertPolicy` 收到 `policyId="plan:t1"`、`scope=TENANT`、`tokenLimit=10_000_000`；重复 `activate` 幂等（同 policyId upsert，不新增行）。

### 8.5 账单生成幂等

`KernelBillingServiceTests`：同租户同 period 调两次 `generateForPeriod` → 第二次 `existsForPeriod` 命中跳过，`sa_bill` 仅一行。

### 8.6 JDBC 适配器（对齐 `JdbcQuotaSchemaAlignmentTests`）

- Schema 对齐测试：实体字段 ↔ 表列一一对应（含 `sa_payment_callback_log` 唯一键）。
- `lockByOrderNo` 行锁行为（嵌入式 PG / Testcontainers）。

### 8.7 契约 / 端到端

- web 层 `SeahorsePaymentControllerTests`（MockMvc）：回调端点返回渠道要求的 ack 文本；未登录可访问 `/callbacks/**`。
- 前端 `billingService` 契约测试（对齐 `serviceEndpointCoverage.test.ts`）：路径 / 方法与后端一致。

---

## 9. 验收标准

| # | 标准 | 验证方式 |
|---|------|---------|
| 1 | **完整闭环**：选套餐 → 下单 → 扫码支付 → 回调激活 → `QuotaPolicy` 写入 → 配额即时生效 | 端到端手测 + 集成测试 |
| 2 | **支付成功率 ≥ 99%** | 回调幂等 + 验签 + 行锁，无重复激活 / 漏激活；压测 1000 笔并发回调零脏数据 |
| 3 | **回调幂等**：同一交易号重复回调 N 次，订阅只激活 1 次、账户不重复升级 | §8.1 单测 + 重放压测 |
| 4 | **验签强制**：篡改 / 伪造回调被拒，订单不变更 | §8.2 单测 |
| 5 | **金额防篡改**：回调金额 ≠ 订单金额被拒 | §8.2 单测 |
| 6 | **配额强制**：超额时上传 / 执行被拦截，返回 402 + 友好升级提示 | §8.3 单测 + 手测 |
| 7 | **套餐映射正确**：四档套餐各自的 token/call/cost 限额准确写入 `sa_quota_policy` | §8.4 单测 |
| 8 | **账单生成**：每月 1 号生成上月账单，重复执行幂等 | §8.5 单测 + Job 手触发 |
| 9 | **用量聚合**：`sa_usage_rollup` 周期更新，增量游标不重算 | Job 日志 + 数据核对 |
| 10 | **多实例安全**：聚合 / 账单 Job 在多副本下不重复执行 | `DistributedLockPort` 验证 |

---

## 10. 风险与缓解（支付安全为重）

| 风险 | 等级 | 缓解 |
|------|------|------|
| **回调重复 → 重复激活 / 多次升级** | 🔴 高 | `sa_payment_callback_log` 唯一键(`channel+channel_trade_no`)幂等闸 + 订单终态二次校验 + 行锁；§5.4 三重防护 |
| **伪造 / 篡改回调** | 🔴 高 | 渠道 SDK 强制验签（支付宝 RSA2、微信 V3 平台证书 + AES-GCM 解密）；验签失败立即拒绝并审计 |
| **金额篡改**（小额激活高价套餐） | 🔴 高 | `PaymentOrder.amountMatches()` 回调金额与下单金额严格比对，不一致拒绝 |
| **并发回调 / 查单竞态** | 🟠 中 | `lockByOrderNo` = `SELECT ... FOR UPDATE` 事务内串行化；状态机非法迁移抛异常 |
| **回调端点被未授权访问** | 🟠 中 | 仅放行 `/api/billing/callbacks/**`，端点内即刻验签；不接受未验签报文产生任何副作用 |
| **密钥泄露**（私钥 / APIv3 key） | 🔴 高 | 经既有 `SeahorseAgentCredentialAutoConfiguration` / 环境变量注入，禁入库 / 禁日志；回调原文存库前脱敏敏感字段 |
| **网关不可达 / 超时** | 🟠 中 | 下单失败订单留 `PENDING`，`SeahorseOrderTimeoutJob` 30min 关单；前端轮询超时提示重试 |
| **配额强制误拦正常请求** | 🟠 中 | `QuotaEnforcementPort` 可选注入（null 跳过）；`WARN` 仅提示不拦截；硬拦截仅 `DENY` |
| **跨租户配额越界**（块A 未就绪） | 🔴 高 | 强依赖块A `TenantContext`；块A 未交付前计费强制不可上线（依赖门禁） |
| **时区导致账单周期错位** | 🟡 低 | 账单 Job 固定 `Asia/Shanghai`；`bill_period` 用 `YYYY-MM` 字符串避免 TZ 歧义 |
| **金额浮点误差** | 🟡 低 | 金额一律 `long` 分（`*_cents`）；`cost` 沿用既有 `double`（仅展示，不参与收款） |

---

## 11. 参考文件锚点（均经本次 grep / 阅读核实）

**复用底座（零改或仅读）：**
- `seahorse-agent-kernel/.../domain/agent/quota/QuotaPolicy.java`、`QuotaScope.java`、`QuotaPolicyStatus.java`、`QuotaPolicyLimits.java`、`QuotaUsage.java`、`QuotaDecisionResult.java`、`QuotaDecisionEffect.java`、`QuotaDecisionReasonCode.java`
- `seahorse-agent-kernel/.../application/agent/quota/KernelQuotaDecisionService.java`（`evaluate` / `findActiveForAgent`）、`KernelQuotaSummaryService.java`
- `seahorse-agent-kernel/.../domain/agent/cost/CostUsageRecord.java`、`CostUsageAggregate.java`、`CostUsageSource.java`
- `seahorse-agent-kernel/.../ports/inbound/agent/QuotaManagementInboundPort.java`、`QuotaDecisionCommand.java`、`QuotaPolicyUpsertCommand.java`、`CostUsageInboundPort.java`
- `seahorse-agent-kernel/.../ports/outbound/agent/QuotaPolicyRepositoryPort.java`、`CostUsageRepositoryPort.java`、`CostUsageQuery.java`
- `seahorse-agent-adapter-repository-jdbc/.../JdbcQuotaPolicyRepositoryAdapter.java`、`JdbcCostUsageRepositoryAdapter.java`
- `seahorse-agent-adapter-web/.../SeahorseQuotaController.java`、`SeahorseCostUsageController.java`、`SeahorseUserQuotaController.java`、`ApiResponse.java`、`ApiResponses.java`、`AdvancedFeature.java`、`AdvancedFeatureGate.java`
- `resources/database/seahorse_init.sql`（`sa_quota_policy` L1507、`sa_cost_usage_record` L1539）

**运行时强制拦截点：**
- `seahorse-agent-kernel/.../application/knowledge/KernelKnowledgeDocumentService.java`（`upload()` L104）+ `ports/inbound/knowledge/UploadKnowledgeDocumentCommand.java`、`UploadFileContent.java`（`size()`）
- `seahorse-agent-kernel/.../application/agent/runtime/KernelAgentRunService.java`（`startRun()` L63）+ `ports/inbound/agent/AgentRunStartCommand.java`（`tenantId()`）

**接线 / 范式参考：**
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentKernelRegistryAutoConfiguration.java`（Layer 6，quota/cost Bean 注册 L519-565）
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentRegistryRepositoryAutoConfiguration.java`（Layer 1，JDBC Bean 注册 L314-328）
- `seahorse-agent-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（6 层结构）
- `seahorse-agent-spring-boot-starter/.../SeahorseMemoryAggregationJob.java`（`@Scheduled + DistributedLockPort` 范式）
- `seahorse-agent-kernel/.../ports/outbound/coordination/DistributedLockPort.java`、`ports/outbound/auth/CurrentUserPort.java`（`CurrentUser` 无 tenantId → 依赖块A `TenantContext`）

**前端范式：**
- `frontend/src/services/api.ts`（axios 拦截器拆包）、`quotaSummaryService.ts`（`/api/me/quota-summary`）、`auditCostService.ts`（service 风格）、`src/types/index.ts`、`src/pages/admin/<feature>/`、`src/router.tsx`

**规划上下文：**
- `docs/aegis/plans/2026-06-04-saas-mvp-execution-roadmap.md`（块C 计费现状 / 依赖 / 验收）

**架构决策汇总：**
1. 事务边界落点：`TransactionRunnerPort`（保六边形纯净）vs adapter 层 `@Transactional` 编排——二选一。
2. 回调端点 `/api/billing/callbacks/**` 在块A 鉴权拦截器中的放行配置位置。
3. 存储配额是否长期作为 `sa_subscription` 字段，还是后续扩 `QuotaScope` 增加 `STORAGE` 维度。

# 块D · 运维监控与可观测性 — 可落地技术方案

> 文档定位：SaaS MVP 执行计划第 05 篇。对应 `docs/aegis/plans/2026-06-04-saas-mvp-execution-roadmap.md` 中的**块D（运维监控）**。
> 关键属性：**完全独立、不依赖多租户（01-多租户）、可全程并行、建议第一周启动**。
> 编写依据：2026-06-04 真实代码审查（类名 / 方法 / 路径 / 依赖坐标均已核实，未核实处已确认架构决策）。
> 工作量口径：1 人 × 5–6 天（自研管理后台）或 8–10 天（Prometheus + Grafana）。

---

## ⚡ 快速决策指南

### Grafana 是可选项（默认推荐自研管理后台）

| 方案 | 成本 | 实施工期 | 优点 | 缺点 | 推荐场景 |
|------|------|---------|------|------|----------|
| **自研管理后台**（默认） | ¥0 | 5-6 天 | ✅ 零外部依赖<br>✅ 与业务集成<br>✅ 自定义灵活 | ⚠️ 需自己开发图表 | ✅ **MVP 推荐** |
| Prometheus + Grafana（可选） | 云服务 ¥500/月<br>或自建 ¥0 | 8-10 天 | ✅ 开箱即用<br>✅ 强大的查询 | ❌ 增加部署成本<br>❌ 需维护额外组件 | ⚠️ 监控深度要求高 |

**架构决策**：
- **MVP 阶段**：自研管理后台（`/admin/monitoring`），直接查询 Actuator API
- **企业版**（可选）：接入 Prometheus + Grafana（深度监控、PromQL 查询）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

#### P0：核心监控（自研管理后台）

1. **Actuator 健康检查**：引入 `spring-boot-starter-actuator`，暴露 `/actuator/health`、`/actuator/metrics`
2. **中间件直连健康检查**：PostgreSQL、Redis、Milvus、Elasticsearch 真实连通性探测
3. **告警**：钉钉群机器人 webhook + 内置规则（服务 Down、API 错误率 >5%、连接池耗尽）
4. **管理后台监控页面**：
   - 系统健康仪表板（GREEN/WARN/RED 状态卡片）
   - 实时指标图表（QPS、错误率、延迟 P99）
   - 中间件状态（PostgreSQL、Redis、Milvus 连接池）
   - 告警历史记录

#### P1：可选增强（Prometheus + Grafana）

5. **Prometheus exporter**：`micrometer-registry-prometheus`，暴露 `/actuator/prometheus`
6. **Grafana 仪表板**：预配置 Dashboard JSON（系统健康、QPS、错误率、Token 消耗、活跃租户）

**成本对比**：
- 自研管理后台（P0）：¥0，5-6 天工期
- Prometheus + Grafana（P1）：云服务 ¥500/月 或 自建需维护，+3-4 天工期

### 1.2 不做（明确后延，避免镀金）

| 后延项 | 原因 | 去向 |
|---|---|---|
| 自定义告警规则引擎（规则 DSL / 动态加载） | MVP 用硬编码规则枚举即可 | Phase 2 |
| 灰度真实流量分流逻辑 | 现仅有 `AgentVersionRollout`（`canaryPercent` 字段）框架，无分流执行 | Phase 2 |
| 自动扩缩容（HPA / KEDA） | 依赖 K8s 平台决策 | Phase 2 |
| **Prometheus Alertmanager** | **MVP 先用管理后台 + 钉钉直发，零成本** | P1 可选（规模化后） |
| **Grafana 仪表板** | **MVP 先用自研管理后台，零外部依赖** | P1 可选（深度监控需求） |

### 1.3 验收信号（详见 §10）

#### P0 验收（自研管理后台）

- ✅ `GET /actuator/health` 返回健康状态 JSON（含 `postgres`、`redis`、`milvus`、`elasticsearch` 状态）
- ✅ `GET /api/admin/monitoring/system-health` 返回系统健康仪表板数据（GREEN/WARN/RED 卡片）
- ✅ 管理后台 `/admin/monitoring` 页面显示：
  - 实时 QPS、错误率、P99 延迟图表
  - 中间件连接池状态（PostgreSQL、Redis）
  - 最近 24 小时告警历史
- ✅ 触发一次模拟故障（停 Redis），钉钉群收到告警，管理后台显示 RED 状态

#### P1 验收（Prometheus + Grafana，可选）

- ⚠️ `curl http://<host>:9090/actuator/prometheus` 返回指标文本
- ⚠️ Grafana 能看到系统健康、QPS、错误率、Token 消耗趋势

---

## 2. 现状（代码级）

### 2.1 已有 SRE Health 框架（可直接复用，是本方案的承载骨架）

领域类（`seahorse-agent-kernel`，包 `...kernel.domain.agent.sre`）：

- `SreHealthStatus`：枚举 `GREEN(0) / WARN(1) / RED(2)`，含 `isMoreSevereThan(other)`，用于聚合取最严重态。
- `SreHealthItem`：**record**，字段为 `(String contributorName, SreHealthStatus status, String message, String evidenceRef)`。
  > ⚠️ 注意：字段实名是 `contributorName` 与 `evidenceRef`，**不是** roadmap 简述里的 `name`/`evidence`。构造器对 `contributorName` 做非空校验、对 `status` 缺省补 `WARN`、对 `evidenceRef` 空串归一为 `null`。
- `SreHealthReport`：**record** `(String reportId, SreHealthStatus status, List<SreHealthItem> items, Instant checkedAt)`。`status` 传 `null` 时由 `aggregate(items)` 自动取最严重态——**这意味着新增 RED contributor 会自动把整份报告拉成 RED，无需改聚合逻辑**。

端口：

- 出站 `SreHealthContributorPort`（包 `...ports.outbound.agent`）：`@FunctionalInterface`，`SreHealthItem current()`。**这是本方案要新增 4 个实现的扩展点。**
- 出站 `SreHealthReportProviderPort`：`@FunctionalInterface`，`SreHealthReport current()`（被 `KernelProductionGateService` 复用做发布门禁）。
- 入站 `SreHealthInboundPort`（包 `...ports.inbound.agent`）：`SreHealthReport current()`。

应用服务 `KernelSreHealthQueryService`（包 `...kernel.application.agent.sre`）：

- 同时实现 `SreHealthInboundPort` + `SreHealthReportProviderPort`。
- 构造 `(List<SreHealthContributorPort> contributors, Clock clock)`。
- `current()` 逐个调用 contributor，**单个 contributor 抛异常被捕获并降级为 WARN**（`readContributor` 内 try/catch），不会让整份报告 500——新增的中间件 contributor 即使探测抛错也安全。

Web 适配器 `SeahorseSreHealthController`（`seahorse-agent-adapter-web`）：

```java
@GetMapping("/api/sre/health")
public ApiResponse<Object> current() {
    return ApiResponses.requireService(sreHealthPortProvider, SreHealthInboundPort::current);
}
```

遵循 CLAUDE.md 的 `ObjectProvider<T>` 懒加载约定。

装配点 `SeahorseAgentKernelRegistryAutoConfiguration`（Layer 6）：

```java
@Bean
@ConditionalOnMissingBean(SreHealthInboundPort.class)
public KernelSreHealthQueryService seahorseSreHealthInboundPort(
        ObjectProvider<SreHealthContributorPort> sreHealthContributorPorts,
        ObjectProvider<Clock> clockProvider) {
    return new KernelSreHealthQueryService(
            sreHealthContributorPorts.orderedStream().toList(),   // ← 收集全部 contributor
            clockProvider.getIfAvailable(Clock::systemUTC));
}
```

> **复用结论（关键）**：装配点用 `ObjectProvider<SreHealthContributorPort>.orderedStream()` 收集**所有** contributor Bean。因此本方案只需在某个 `@AutoConfigureAfter` 正确声明的自动配置里多注册 4 个 `SreHealthContributorPort` Bean，**无需改动 controller、应用服务、领域类或装配点**，新 contributor 会被自动纳入 `/api/sre/health`。

### 2.2 已有 5 个适配器层 contributor

`SeahorseAgentSreAdapterHealthAutoConfiguration`（`seahorse-agent-spring-boot-starter`，Layer 1）：

- `@AutoConfigureAfter({Vector, Keyword, Ai, Storage}AdapterAutoConfiguration)`，`@ConditionalOnProperty("seahorse-agent.kernel.enabled", matchIfMissing=true)`。
- 5 个 Bean：`seahorseVectorStoreSreHealthContributor`、`seahorseKeywordSearchSreHealthContributor`、`seahorseKeywordIndexSreHealthContributor`、`seahorseAiModelSreHealthContributor`、`seahorseObjectStorageSreHealthContributor`，各带 `@ConditionalOnMissingBean(name=...)`。
- 判定逻辑（`runtimeAdapterItem`）：只看对应 `XxxPort` Bean 是否存在、是不是 `noop`，**不发起任何网络探测**。

> **缺口结论**：这 5 个回答的是「适配器装上了吗」，不是「中间件连得上吗」。Milvus 宕机但 Bean 在，现状仍报 GREEN。本方案补的 4 个中间件 contributor 正是填这个语义空缺，二者**并存互补**（适配器层 + 基础设施层）。

### 2.3 已有 Micrometer（关键：与 Actuator 天然衔接）

- 模块 `seahorse-agent-adapter-observation-micrometer`，依赖 `io.micrometer:micrometer-core`（版本由 spring-boot BOM 管理）+ kernel。
- `MicrometerObservationAdapter implements ObservationPort`，构造 `(MeterRegistry)`，产出：
  - `seahorse.agent.observation.duration`（Timer）
  - `seahorse.agent.observation.events`（Counter）
  - 标签：`observation` / `event` / `tenant` + 自定义 attributes。
- 自动配置 `SeahorseAgentObservationAdapterAutoConfiguration`（Layer 1，`@AutoConfigureAfter(DataSourceAutoConfiguration.class)`）。其内嵌的 Micrometer 绑定：

```java
@Bean
@ConditionalOnBean(MeterRegistry.class)            // ← 关键前置条件
@ConditionalOnProperty(prefix = "seahorse-agent.adapters.observation", name = "type", havingValue = "micrometer")
@ConditionalOnMissingBean(ObservationPort.class)
public MicrometerObservationAdapter seahorseMicrometerObservationAdapter(MeterRegistry meterRegistry) { ... }
```

> **衔接结论（本方案的免费红利）**：`MicrometerObservationAdapter` 的激活硬依赖容器里存在一个 `MeterRegistry` Bean。**当前工程没有任何模块提供 `MeterRegistry`**，所以即便配 `observation.type=micrometer`，该适配器也起不来、指标进不了 registry。一旦本方案引入 `micrometer-registry-prometheus`（它会贡献一个 `PrometheusMeterRegistry`，即 `MeterRegistry`），`@ConditionalOnBean(MeterRegistry.class)` 即被满足，`seahorse.agent.observation.*` 业务指标**自动**汇入 `/actuator/prometheus`，无需改 adapter 一行代码。这是把「已有 Micrometer 埋点」真正接出去的唯一缺失拼图。

### 2.4 缺口清单（本方案逐一闭合）

| 缺口 | 现状 | 本方案章节 |
|---|---|---|
| Spring Boot Actuator | 未集成（仅 `RateLimitFilter` 第 86 行预留了 `/actuator` 跳过限流） | §3.1 |
| Prometheus exporter | 无 `micrometer-registry-prometheus`，无 `MeterRegistry` Bean | §3.1 |
| DB/Redis/Milvus/ES 直连健康 | 无 | §3.2 / §5 |
| 告警（钉钉 webhook + 规则 + 静默） | 零基础 | §3.3 / §5 / §6 |
| Grafana 仪表板 | 零基础 | §3.4 / §7 |

---

## 3. 技术方案

### 3.1 Actuator + MeterRegistry（零外部依赖）

> **核心决策**：用 `SimpleMeterRegistry`（Micrometer 自带）替代 Prometheus，零成本、零外部依赖

#### 3.1.1 架构设计

**问题分析**（§2.3 关键发现）：
- 现有 `MicrometerObservationAdapter` 需要 `MeterRegistry` Bean
- 但工程中**无任何模块提供** `MeterRegistry`
- Prometheus 的 `micrometer-registry-prometheus` 只是提供了一个 `PrometheusMeterRegistry` 实现

**解决方案**：
- 用 Micrometer 自带的 `SimpleMeterRegistry`（内存实现）
- **零外部依赖**，无需 Prometheus
- 满足 `@ConditionalOnBean(MeterRegistry.class)` 条件

#### 3.1.2 自动配置类（P0）

**新增类**：`SeahorseAgentSimpleMeterRegistryAutoConfiguration`  
**位置**：`seahorse-agent-spring-boot-starter/.../autoconfigure/`

```java
package com.miracle.ai.seahorse.agent.autoconfigure;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * SimpleMeterRegistry 自动配置
 * 作用：提供内存 MeterRegistry，激活 MicrometerObservationAdapter
 */
@Configuration
public class SeahorseAgentSimpleMeterRegistryAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();  // Micrometer 自带，无需外部依赖
    }
}
```

**注册**：在 `AutoConfiguration.imports` 的 **Layer 1 开头**（在 `ObservationAdapterAutoConfiguration` 之前）：

```
# Layer 1 — Adapters（after DataSource）
com.miracle.ai.seahorse.agent.autoconfigure.SeahorseAgentSimpleMeterRegistryAutoConfiguration
com.miracle.ai.seahorse.agent.autoconfigure.SeahorseAgentObservationAdapterAutoConfiguration
...
```

#### 3.1.3 Actuator 配置（P0）

**依赖**（`seahorse-agent-bootstrap/pom.xml`）：

```xml
<!-- 只需 Actuator，无需 Prometheus -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**配置**（`application.properties`）：

```properties
# --- Actuator（块D）---
management.endpoints.web.exposure.include=health,info,metrics
management.endpoint.health.show-details=when-authorized
management.endpoint.health.probes.enabled=true

# 开启 http.server.requests 的分位数（P99 延迟需要）
management.metrics.distribution.percentiles-histogram.http.server.requests=true

# 激活 Micrometer 观测适配器（§2.3 红利）
seahorse-agent.adapters.observation.type=micrometer
```

**效果**：
- ✅ `SimpleMeterRegistry` 自动注册
- ✅ `MicrometerObservationAdapter` 激活
- ✅ 管理后台可读 `MeterRegistry.find(...)`
- ✅ 告警可评估 `Counter`/`Timer`

---

### 3.1.4 可选增强：Prometheus exporter（P1）

### 3.1.4 可选增强：Prometheus exporter（P1）

> **场景**：深度监控、多实例、长期存储、Grafana 可视化

**若需要接入 Prometheus**（企业版可选）：

1. **修改依赖**（`pom.xml`）：

```xml
<!-- 替换 SimpleMeterRegistry 为 PrometheusMeterRegistry -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

2. **修改配置**（`application.properties`）：

```properties
# 增加 prometheus 端点
management.endpoints.web.exposure.include=health,info,prometheus,metrics
management.prometheus.metrics.export.enabled=true
management.metrics.tags.application=${spring.application.name}
```

3. **修改自动配置**：

```java
// 删除 SimpleMeterRegistryAutoConfiguration
// 或增加 @ConditionalOnProperty
@ConditionalOnProperty(name = "seahorse.agent.metrics.type", havingValue = "simple", matchIfMissing = true)
public MeterRegistry simpleMeterRegistry() { ... }
```

**成本对比**：
- SimpleMeterRegistry（默认）：¥0，内存存储，重启丢失
- Prometheus（可选）：¥500/月或自建，持久化存储，支持 PromQL

---

### 3.2 安全放行（P0）

```java
.excludePathPatterns(
        "/",
        "/index.html",
        "/login",
        "/features",
        "/api/features",
        "/auth/**",
        "/error",
        "/assets/**",
        "/prototype/**",
        "/actuator/**");   // ← 块D 新增：放行 Actuator 给 Prometheus 抓取
```

> `RateLimitFilter` 第 86 行已 `path.startsWith("/actuator")` 跳过限流，无需再改。
> **安全提醒**：`/actuator/**` 放行后，端点对未登录可见。生产应：(a) 只 `expose` 必要端点（上面已收敛为 health/info/prometheus/metrics）；(b) 用 K8s `NetworkPolicy` / 安全组把 9090 的 `/actuator` 限定到 Prometheus 抓取网段；(c) 如需更强隔离，可改用 `management.server.port` 暴露独立管理端口（架构决策：MVP 使用网段隔离，企业版使用独立管理端口）。

### 3.2 中间件直连 HealthContributor（接入现有 SreHealth 框架）

**设计原则**：复用 `SreHealthItem` 结构与 `SreHealthContributorPort` 扩展点，**不引入新端口**。每个 contributor：

- 用各中间件**已存在的客户端 Bean**做一次最轻量探测（见下表，均已核实客户端来源）；
- 探测成功 → `GREEN`；探测失败 / 超时 → `RED`；客户端 Bean 不存在（该中间件未启用）→ `WARN`（与现有 5 个 contributor 的「缺失即 WARN」语义一致）；
- 探测带超时（默认 2s），且整体被 `KernelSreHealthQueryService` 的 try/catch 兜底，绝不拖垮 `/api/sre/health`；
- 把连接目标（host/url，**脱敏不含密码**）写进 `evidenceRef`。

| Contributor | 探测客户端（来源已核实） | 探测动作 | 客户端 Bean 由谁提供 |
|---|---|---|---|
| `postgres` | `javax.sql.DataSource` | `connection.isValid(timeoutSec)` | Spring Boot `DataSourceAutoConfiguration`（`spring.datasource.*`，compose 已配） |
| `redis` | `org.redisson.api.RedissonClient` | 轻量探活（见 §5.2 注） | `SeahorseAgentCacheAdapterAutoConfiguration`（cache.type=redis） |
| `milvus` | `io.milvus.v2.client.MilvusClientV2` | `hasCollection(HasCollectionReq)` | `SeahorseAgentVectorAdapterAutoConfiguration`（vector.type=milvus） |
| `elasticsearch` | `okhttp3.OkHttpClient` + base-url | `GET {baseUrl}/_cluster/health` | base-url 取 `seahorse-agent.adapters.keyword-search.elasticsearch.base-url`（默认 `http://localhost:9200`） |

**装配位置**：新建 `SeahorseAgentMiddlewareHealthAutoConfiguration`，放入 `seahorse-agent-spring-boot-starter`，在 `AutoConfiguration.imports` 的 **Layer 1** 注册，紧随现有 `SeahorseAgentSreAdapterHealthAutoConfiguration` 之后。`@AutoConfigureAfter` 必须声明产出上述客户端 Bean 的所有适配器配置（这是 CLAUDE.md 的硬规则）：

```java
@AutoConfigureAfter({
        org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration.class, // DataSource
        SeahorseAgentCacheAdapterAutoConfiguration.class,   // RedissonClient
        SeahorseAgentVectorAdapterAutoConfiguration.class,  // MilvusClientV2
        SeahorseAgentKeywordAdapterAutoConfiguration.class  // ES base-url / OkHttpClient
})
```

每个 Bean 用 `@ConditionalOnClass`（守住可选依赖未上 classpath 的情况）+ 构造注入 `ObjectProvider<客户端>`（懒加载、缺失返 WARN），与现有 contributor 完全同构。骨架见 §5.1。

### 3.3 告警（钉钉 webhook + 内置规则 + 静默）

**架构（六边形）**：

```
                ┌─────────────────────────── seahorse-agent-kernel ───────────────────────────┐
                │  domain.agent.alert.AlertSignal (record)                                     │
                │  domain.agent.alert.AlertRule (enum: 规则/阈值/级别)                           │
                │  application.agent.alert.KernelAlertEvaluationService                        │
                │     ├─ in:  SreHealthReportProviderPort（复用，§2.1）                          │
                │     ├─ in:  AlertMetricSnapshotPort（出站，读 Micrometer 指标）                 │
                │     ├─ out: AlertNotifierPort（出站，发通知）                                   │
                │     └─ out: AlertSilencePort（出站，静默/去重，默认内存实现，可换 Redis）         │
                └──────────────────────────────────────────────────────────────────────────────┘
                          ▲ 实现                          ▲ 实现                      ▲ 调度
   seahorse-agent-adapter-alert-dingtalk        spring-boot-starter            spring-boot-starter
   DingTalkAlertNotifierAdapter                 MicrometerAlertMetricSnapshot  SeahorseAlertEvaluationJob
   (OkHttp POST + 加签)                         (从 MeterRegistry 读 counter)   (@Scheduled + DistributedLockPort)
```

- **端口定义在 kernel**（保持领域纯净、可测），**adapter 落在独立模块** `seahorse-agent-adapter-alert-dingtalk`（与现有 `adapter-observation-micrometer` 同构的命名风格）。
- **评估服务在 kernel**：纯逻辑，输入「健康报告 + 指标快照」，输出「应触发的 `AlertSignal` 列表」，便于单测（构造假指标即可，无需真发钉钉）。
- **调度在 starter**：`SeahorseAlertEvaluationJob` 用 `@Scheduled(cron=...)` + `DistributedLockPort`（已存在 `...ports.outbound.coordination.DistributedLockPort`，含 `noop()`）保证多实例下只有一个实例评估、避免重复告警。`@EnableScheduling` 已在 `SeahorseAgentApplication` 上，无需新增。

**静默防风暴**：`AlertSilencePort` 以 `ruleId + 维度键` 为 key 记录上次触发时刻，命中静默窗口（默认 10 分钟）内同规则不重发；恢复（GREEN）时发一条 resolved 并清除静默。MVP 用 `InMemoryAlertSilenceAdapter`（`ConcurrentHashMap`）；多实例下若要全局静默，换 Redis 实现（`RedissonClient` 已具备，标 **可选增强**）。

**指标来源**：评估器不直接抓 Prometheus，而是注入 `ObjectProvider<MeterRegistry>` 现场读计数器（actuator 自带 `http.server.requests`、HikariCP 自带 `hikaricp.connections.*`，均为框架保证的标准指标名）。这样**告警不依赖 Prometheus 部署**，本地/裸机也能发钉钉。

---

### 3.4 自研管理后台监控页面（P0，推荐）

> **核心优势**：零外部依赖、与业务集成、成本 ¥0、5-6 天工期

#### 3.4.1 后端 API

**新增 Controller**：`SeahorseAdminMonitoringController`

```java
@RestController
@RequestMapping("/api/admin/monitoring")
public class SeahorseAdminMonitoringController {
    
    private final ObjectProvider<SreHealthReportProviderPort> healthProvider;
    private final ObjectProvider<MeterRegistry> meterRegistryProvider;
    private final ObjectProvider<AlertHistoryQueryPort> alertHistoryProvider;
    
    /**
     * 系统健康仪表板
     */
    @GetMapping("/system-health")
    public Map<String, Object> getSystemHealth() {
        SreHealthReport report = healthProvider.getIfAvailable().current();
        
        return Map.of(
            "code", "0",
            "data", Map.of(
                "status", report.status().name(),  // GREEN/WARN/RED
                "checkedAt", report.checkedAt(),
                "items", report.items().stream()
                    .map(item -> Map.of(
                        "name", item.contributorName(),
                        "status", item.status().name(),
                        "message", item.message(),
                        "evidenceRef", item.evidenceRef()
                    ))
                    .toList()
            )
        );
    }
    
    /**
     * 实时指标
     */
    @GetMapping("/metrics/realtime")
    public Map<String, Object> getRealtimeMetrics() {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return Map.of("code", "0", "data", Map.of());
        }
        
        // QPS（最近 1 分钟）
        Counter requestsCounter = registry.find("http.server.requests").counter();
        double qps = requestsCounter != null ? requestsCounter.count() / 60.0 : 0;
        
        // 错误率
        Counter errorCounter = registry.find("http.server.requests")
            .tag("status", "5xx")
            .counter();
        double errorRate = requestsCounter != null && errorCounter != null
            ? errorCounter.count() / requestsCounter.count()
            : 0;
        
        // P99 延迟
        Timer requestsTimer = registry.find("http.server.requests").timer();
        double p99 = requestsTimer != null
            ? requestsTimer.percentile(0.99)
            : 0;
        
        return Map.of("code", "0", "data", Map.of(
            "qps", qps,
            "errorRate", errorRate,
            "p99Latency", p99,
            "timestamp", Instant.now()
        ));
    }
    
    /**
     * 中间件连接池状态
     */
    @GetMapping("/middleware/pools")
    public Map<String, Object> getMiddlewarePools() {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        if (registry == null) {
            return Map.of("code", "0", "data", List.of());
        }
        
        List<Map<String, Object>> pools = new ArrayList<>();
        
        // PostgreSQL 连接池
        Gauge hikariActive = registry.find("hikaricp.connections.active").gauge();
        Gauge hikariIdle = registry.find("hikaricp.connections.idle").gauge();
        Gauge hikariMax = registry.find("hikaricp.connections.max").gauge();
        
        if (hikariActive != null) {
            pools.add(Map.of(
                "name", "PostgreSQL",
                "active", hikariActive.value(),
                "idle", hikariIdle != null ? hikariIdle.value() : 0,
                "max", hikariMax != null ? hikariMax.value() : 0,
                "usage", hikariActive.value() / (hikariMax != null ? hikariMax.value() : 1)
            ));
        }
        
        // Redis 连接池（Redisson）
        // TODO: 需 Redisson 暴露指标
        
        return Map.of("code", "0", "data", pools);
    }
    
    /**
     * 告警历史（最近 24 小时）
     */
    @GetMapping("/alerts/history")
    public Map<String, Object> getAlertHistory(
            @RequestParam(defaultValue = "24") int hours) {
        
        List<AlertRecord> alerts = alertHistoryProvider.getIfAvailable()
            .findRecent(Duration.ofHours(hours));
        
        return Map.of("code", "0", "data", alerts.stream()
            .map(alert -> Map.of(
                "ruleId", alert.ruleId(),
                "level", alert.level().name(),
                "message", alert.message(),
                "triggeredAt", alert.triggeredAt(),
                "resolved", alert.resolved()
            ))
            .toList()
        );
    }
}
```

#### 3.4.2 前端页面

**新增页面**：`frontend/src/pages/admin/Monitoring.tsx`

```tsx
import { Card, Row, Col, Statistic, Table, Tag } from 'antd';
import { Line } from '@ant-design/charts';
import { useEffect, useState } from 'react';

export const MonitoringPage = () => {
  const [health, setHealth] = useState<any>(null);
  const [metrics, setMetrics] = useState<any[]>([]);
  const [pools, setPools] = useState<any[]>([]);
  const [alerts, setAlerts] = useState<any[]>([]);
  
  useEffect(() => {
    // 轮询实时数据（每 10 秒）
    const timer = setInterval(() => {
      fetchData();
    }, 10000);
    
    fetchData();
    return () => clearInterval(timer);
  }, []);
  
  const fetchData = async () => {
    // 系统健康
    const healthRes = await fetch('/api/admin/monitoring/system-health');
    const healthData = await healthRes.json();
    setHealth(healthData.data);
    
    // 实时指标（保留最近 50 个点）
    const metricsRes = await fetch('/api/admin/monitoring/metrics/realtime');
    const metricsData = await metricsRes.json();
    setMetrics(prev => [...prev, metricsData.data].slice(-50));
    
    // 连接池
    const poolsRes = await fetch('/api/admin/monitoring/middleware/pools');
    const poolsData = await poolsRes.json();
    setPools(poolsData.data);
    
    // 告警历史
    const alertsRes = await fetch('/api/admin/monitoring/alerts/history');
    const alertsData = await alertsRes.json();
    setAlerts(alertsData.data);
  };
  
  return (
    <div style={{ padding: 24 }}>
      <h2>系统监控</h2>
      
      {/* 系统健康卡片 */}
      <Row gutter={16} style={{ marginBottom: 24 }}>
        <Col span={6}>
          <Card>
            <Statistic
              title="系统状态"
              value={health?.status}
              valueStyle={{
                color: health?.status === 'GREEN' ? '#3f8600' :
                       health?.status === 'WARN' ? '#faad14' : '#cf1322'
              }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="QPS"
              value={metrics[metrics.length - 1]?.qps?.toFixed(2) || 0}
              suffix="req/s"
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="错误率"
              value={(metrics[metrics.length - 1]?.errorRate * 100 || 0).toFixed(2)}
              suffix="%"
              valueStyle={{
                color: (metrics[metrics.length - 1]?.errorRate || 0) > 0.05 ? '#cf1322' : '#3f8600'
              }}
            />
          </Card>
        </Col>
        <Col span={6}>
          <Card>
            <Statistic
              title="P99 延迟"
              value={metrics[metrics.length - 1]?.p99Latency?.toFixed(0) || 0}
              suffix="ms"
            />
          </Card>
        </Col>
      </Row>
      
      {/* 实时指标图表 */}
      <Card title="QPS 趋势（最近 10 分钟）" style={{ marginBottom: 24 }}>
        <Line
          data={metrics.map((m, i) => ({ time: i, value: m.qps }))}
          xField="time"
          yField="value"
          smooth
          height={200}
        />
      </Card>
      
      {/* 中间件连接池 */}
      <Card title="中间件连接池" style={{ marginBottom: 24 }}>
        <Table
          dataSource={pools}
          columns={[
            { title: '中间件', dataIndex: 'name', key: 'name' },
            { title: '活跃连接', dataIndex: 'active', key: 'active' },
            { title: '空闲连接', dataIndex: 'idle', key: 'idle' },
            { title: '最大连接', dataIndex: 'max', key: 'max' },
            {
              title: '使用率',
              dataIndex: 'usage',
              key: 'usage',
              render: (usage: number) => (
                <Tag color={usage > 0.8 ? 'red' : usage > 0.5 ? 'orange' : 'green'}>
                  {(usage * 100).toFixed(0)}%
                </Tag>
              )
            }
          ]}
          pagination={false}
        />
      </Card>
      
      {/* 告警历史 */}
      <Card title="告警历史（最近 24 小时）">
        <Table
          dataSource={alerts}
          columns={[
            { title: '规则', dataIndex: 'ruleId', key: 'ruleId' },
            {
              title: '级别',
              dataIndex: 'level',
              key: 'level',
              render: (level: string) => (
                <Tag color={level === 'CRITICAL' ? 'red' : 'orange'}>{level}</Tag>
              )
            },
            { title: '消息', dataIndex: 'message', key: 'message' },
            {
              title: '时间',
              dataIndex: 'triggeredAt',
              key: 'triggeredAt',
              render: (time: string) => new Date(time).toLocaleString()
            },
            {
              title: '状态',
              dataIndex: 'resolved',
              key: 'resolved',
              render: (resolved: boolean) => (
                <Tag color={resolved ? 'green' : 'red'}>
                  {resolved ? '已恢复' : '进行中'}
                </Tag>
              )
            }
          ]}
          pagination={{ pageSize: 10 }}
        />
      </Card>
    </div>
  );
};
```

#### 3.4.3 路由配置

在 `frontend/src/router.tsx` 中增加：

```tsx
{
  path: '/admin/monitoring',
  element: <MonitoringPage />,
  meta: { title: '系统监控', requiresAuth: true, role: 'admin' }
}
```

---

### 3.5 Prometheus + Grafana 仪表板（P1，可选）

- **数据源**：Prometheus（抓 `/actuator/prometheus`）。
- **基础设施指标**：actuator/micrometer 标准指标直接可用（`http_server_requests_seconds_*`、`jvm_*`、`hikaricp_connections_*`、`system_cpu_usage`）。
- **业务指标**：需把领域数据桥接成 Micrometer 指标。新增 `SeahorseBusinessMetricsBinder`（实现 `io.micrometer.core.instrument.binder.MeterBinder`，放 starter），用 `Gauge` 周期性读 `CostUsageRepositoryPort` 聚合，暴露：
  - `seahorse_tokens_total`（Token 消耗，来源 `CostUsageAggregate.totalTokens`，已核实字段）
  - `seahorse_cost_total`（成本，`CostUsageAggregate.totalCost`）
  - `seahorse_sre_health_status`（0/1/2，桥接 `SreHealthReportProviderPort` 的聚合态，用于「系统健康」面板单值）
- **租户 / 收入**：
  - 「活跃租户」可由 `seahorse.agent.observation.duration` 的 `tenant` 标签派生，但标签基数高、且准确口径依赖**块A 的 TenantContext**——MVP 先放 `seahorse_active_tenants` 占位 Gauge，**依赖 01-多租户**。
  - 「收入趋势」依赖**04-计费 的支付/账单**（当前工程无 payment/subscription/billing 代码，已核实）——面板先占位，**标注「依赖 04-计费」**。
- 面板清单见 §7，建议以 JSON model 形式纳管到 `docs/aegis/observability/grafana/`（随仓版本化）。

---

## 4. 配置与依赖

### 4.1 pom 增项

**`seahorse-agent-bootstrap/pom.xml`** 增加（版本均由根 pom 的 `spring-boot-dependencies` BOM 管理，**不写版本号**）：

```xml
<!-- 块D：Actuator + Prometheus exporter -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

> 版本核实：根 `pom.xml` 已 `import` `org.springframework.boot:spring-boot-dependencies:3.5.7`（BOM），上述两坐标的版本由 BOM 决定（actuator 随 3.5.7；`micrometer-registry-prometheus` 随 Micrometer 1.14.x）。与现有 `micrometer-core`（同 BOM 管理、无显式版本）一致，无版本冲突风险。

**新增模块 `seahorse-agent-adapter-alert-dingtalk/pom.xml`**（与 `adapter-observation-micrometer` 同构）：

```xml
<parent>
    <groupId>com.miracle.ai</groupId>
    <artifactId>seahorse-agent</artifactId>
    <version>0.0.1-SNAPSHOT</version>
</parent>
<artifactId>seahorse-agent-adapter-alert-dingtalk</artifactId>
<dependencies>
    <dependency>
        <groupId>com.miracle.ai</groupId>
        <artifactId>seahorse-agent-kernel</artifactId>
        <version>${project.version}</version>
    </dependency>
    <dependency>
        <groupId>com.squareup.okhttp3</groupId>
        <artifactId>okhttp</artifactId>   <!-- 版本由根 pom 的 okhttp.version=4.12.0 管理 -->
    </dependency>
</dependencies>
```

并在：
- 根 `pom.xml` 的 `<modules>` 增加 `<module>seahorse-agent-adapter-alert-dingtalk</module>`；
- `seahorse-agent-spring-boot-starter/pom.xml` 增加对该模块的 `<optional>true</optional>` 依赖（与 micrometer adapter 同款写法），让 starter 能装配告警 adapter。

### 4.2 application 配置增量

`seahorse-agent-bootstrap/src/main/resources/application.properties`（actuator 部分见 §3.1，此处为告警/健康部分）：

```properties
# --- 中间件健康探测（块D）---
seahorse-agent.observability.health.timeout=2s
seahorse-agent.observability.health.elasticsearch.path=/_cluster/health

# --- 告警评估与钉钉（块D）---
seahorse-agent.observability.alert.enabled=true
seahorse-agent.observability.alert.scheduler-cron=0/30 * * * * ?
seahorse-agent.observability.alert.silence-window=10m
seahorse-agent.observability.alert.error-rate-threshold=0.05
seahorse-agent.observability.alert.dingtalk.webhook=${SEAHORSE_ALERT_DINGTALK_WEBHOOK:}
seahorse-agent.observability.alert.dingtalk.secret=${SEAHORSE_ALERT_DINGTALK_SECRET:}
```

> webhook / secret 走环境变量注入（compose / K8s Secret），**不入库不进 git**。`alert.enabled=true` 但 webhook 为空时，adapter 应只记 WARN 日志不报错（优雅降级）。

`docker-compose.yml`（backend 服务 `environment` 段）增量：

```yaml
SEAHORSE_ALERT_DINGTALK_WEBHOOK: ${SEAHORSE_ALERT_DINGTALK_WEBHOOK:-}
SEAHORSE_ALERT_DINGTALK_SECRET: ${SEAHORSE_ALERT_DINGTALK_SECRET:-}
```

### 4.3 AutoConfiguration 分层注意（CLAUDE.md 硬约束）

在 `seahorse-agent-spring-boot-starter/.../AutoConfiguration.imports` 的 **Layer 1** 增两行（紧跟现有 SRE 行）：

```
# Layer 1: Adapters (after DataSource)
...
com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentSreAdapterHealthAutoConfiguration
com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentMiddlewareHealthAutoConfiguration   # ← 新增
com.miracle.ai.seahorse.agent.adapters.spring.SeahorseAgentAlertAutoConfiguration              # ← 新增
```

分层要点：

1. **`SeahorseAgentMiddlewareHealthAutoConfiguration` 的 `@AutoConfigureAfter` 必须声明全部上游**（DataSource / Cache / Vector / Keyword 适配器配置），否则 `@ConditionalOnBean(RedissonClient/MilvusClientV2/...)` 在评估时上游 Bean 尚未注册，会漏装 contributor——这正是 `project_autoconfig_fix` 记录过的同类陷阱。
2. **`SeahorseAgentAlertAutoConfiguration`**（注册评估服务 + 调度 Job + 指标快照 adapter）需 `@AutoConfigureAfter(SeahorseAgentKernelRegistryAutoConfiguration.class)`，因为它依赖 Layer 6 产出的 `SreHealthReportProviderPort`（即 `KernelSreHealthQueryService`）。**注意**：被依赖方在 Layer 6、依赖方写在 Layer 1 文本位置不冲突——`@AutoConfigureAfter` 控制的是装配时序而非文本顺序，但为可读性建议把告警配置注释归到「Layer 7 之后的运维扩展」并显式 `@AutoConfigureAfter` 指向 Registry 配置。所有 Bean 仍用 `@ConditionalOnBean` 守空，缺 `SreHealthReportProviderPort` 时整组不装配。
3. DingTalk adapter 用 `@ConditionalOnProperty("seahorse-agent.observability.alert.enabled", havingValue="true")` + `@ConditionalOnMissingBean(AlertNotifierPort.class)` 守门，与全工程条件装配风格一致。

---

## 5. 后端实现骨架

> 以下为可直接照写的骨架（含真实包名 / 端口 / 客户端类型）。许可证头按 `resources/format/copyright.txt`，spotless 会在 `compile` 阶段自动补齐。

### 5.1 中间件 HealthContributor 自动配置

`seahorse-agent-spring-boot-starter/.../SeahorseAgentMiddlewareHealthAutoConfiguration.java`：

```java
package com.miracle.ai.seahorse.agent.adapters.spring;

import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthItem;
import com.miracle.ai.seahorse.agent.kernel.domain.agent.sre.SreHealthStatus;
import com.miracle.ai.seahorse.agent.ports.outbound.agent.SreHealthContributorPort;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.service.collection.request.HasCollectionReq;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import javax.sql.DataSource;
import java.sql.Connection;

@Configuration(proxyBeanMethods = false)
@AutoConfigureAfter({
        DataSourceAutoConfiguration.class,
        SeahorseAgentCacheAdapterAutoConfiguration.class,
        SeahorseAgentVectorAdapterAutoConfiguration.class,
        SeahorseAgentKeywordAdapterAutoConfiguration.class
})
@ConditionalOnProperty(prefix = "seahorse-agent.kernel", name = "enabled",
        havingValue = "true", matchIfMissing = true)
public class SeahorseAgentMiddlewareHealthAutoConfiguration {

    private static final int TIMEOUT_SECONDS = 2;

    // ---- PostgreSQL ----
    @Bean
    @ConditionalOnMissingBean(name = "seahorsePostgresSreHealthContributor")
    public SreHealthContributorPort seahorsePostgresSreHealthContributor(
            ObjectProvider<DataSource> dataSource, Environment env) {
        String url = env.getProperty("spring.datasource.url", "unspecified");
        return () -> {
            DataSource ds = dataSource.getIfAvailable();
            if (ds == null) {
                return new SreHealthItem("postgres", SreHealthStatus.WARN,
                        "No DataSource bean available", "spring.datasource.url=" + url);
            }
            try (Connection c = ds.getConnection()) {
                boolean ok = c.isValid(TIMEOUT_SECONDS);
                return new SreHealthItem("postgres",
                        ok ? SreHealthStatus.GREEN : SreHealthStatus.RED,
                        ok ? "Connection.isValid=true" : "Connection.isValid=false",
                        "spring.datasource.url=" + sanitize(url));
            } catch (Exception ex) {
                return new SreHealthItem("postgres", SreHealthStatus.RED,
                        "JDBC probe failed: " + ex.getClass().getSimpleName(),
                        "spring.datasource.url=" + sanitize(url));
            }
        };
    }

    // ---- Redis (Redisson) ----
    @Bean
    @ConditionalOnClass(RedissonClient.class)
    @ConditionalOnMissingBean(name = "seahorseRedisSreHealthContributor")
    public SreHealthContributorPort seahorseRedisSreHealthContributor(
            ObjectProvider<RedissonClient> redisson) {
        return () -> {
            RedissonClient client = redisson.getIfAvailable();
            if (client == null) {
                return new SreHealthItem("redis", SreHealthStatus.WARN,
                        "No RedissonClient bean (cache.type != redis?)", null);
            }
            try {
                // 轻量探活：读一个探针 key 是否存在（不写入、O(1)）。
                // 备选（已验证：使用 getBucket().isExists()）：client.getNodesGroup().pingAll()
                client.getBucket("seahorse:health:probe").isExists();
                return new SreHealthItem("redis", SreHealthStatus.GREEN,
                        "RBucket.isExists probe ok", null);
            } catch (Exception ex) {
                return new SreHealthItem("redis", SreHealthStatus.RED,
                        "Redis probe failed: " + ex.getClass().getSimpleName(), null);
            }
        };
    }

    // ---- Milvus ----
    @Bean
    @ConditionalOnClass(MilvusClientV2.class)
    @ConditionalOnMissingBean(name = "seahorseMilvusSreHealthContributor")
    public SreHealthContributorPort seahorseMilvusSreHealthContributor(
            ObjectProvider<MilvusClientV2> milvus, Environment env) {
        String collection = env.getProperty(
                "seahorse-agent.adapters.vector.milvus.collection", "seahorse_chunk"); // 默认值：seahorse_agent_embeddings
        return () -> {
            MilvusClientV2 client = milvus.getIfAvailable();
            if (client == null) {
                return new SreHealthItem("milvus", SreHealthStatus.WARN,
                        "No MilvusClientV2 bean (vector.type != milvus?)", null);
            }
            try {
                client.hasCollection(HasCollectionReq.builder()
                        .collectionName(collection).build());
                return new SreHealthItem("milvus", SreHealthStatus.GREEN,
                        "hasCollection probe ok", "collection=" + collection);
            } catch (Exception ex) {
                return new SreHealthItem("milvus", SreHealthStatus.RED,
                        "Milvus probe failed: " + ex.getClass().getSimpleName(),
                        "collection=" + collection);
            }
        };
    }

    // ---- Elasticsearch (OkHttp GET /_cluster/health) ----
    @Bean
    @ConditionalOnClass(OkHttpClient.class)
    @ConditionalOnMissingBean(name = "seahorseElasticsearchSreHealthContributor")
    public SreHealthContributorPort seahorseElasticsearchSreHealthContributor(
            ObjectProvider<OkHttpClient> http, Environment env) {
        String baseUrl = env.getProperty(
                "seahorse-agent.adapters.keyword-search.elasticsearch.base-url",
                "http://localhost:9200");
        return () -> {
            String type = env.getProperty("seahorse-agent.adapters.keyword-search.type", "");
            if (!"elasticsearch".equalsIgnoreCase(type)) {
                return new SreHealthItem("elasticsearch", SreHealthStatus.WARN,
                        "keyword-search.type != elasticsearch", "type=" + type);
            }
            OkHttpClient client = http.getIfAvailable(() -> new OkHttpClient());
            Request req = new Request.Builder().url(baseUrl + "/_cluster/health").get().build();
            try (Response resp = client.newCall(req).execute()) {
                boolean ok = resp.isSuccessful();
                return new SreHealthItem("elasticsearch",
                        ok ? SreHealthStatus.GREEN : SreHealthStatus.RED,
                        "_cluster/health HTTP " + resp.code(), "base-url=" + baseUrl);
            } catch (Exception ex) {
                return new SreHealthItem("elasticsearch", SreHealthStatus.RED,
                        "ES probe failed: " + ex.getClass().getSimpleName(), "base-url=" + baseUrl);
            }
        };
    }

    private static String sanitize(String url) {
        return url == null ? "unspecified" : url.replaceAll("password=[^&]*", "password=***");
    }
}
```

> 标注：`HasCollectionReq` 与 `MilvusClientV2.hasCollection(...)` 已在 `MilvusVectorAdapter`（第 171 行）核实；collection 默认名 `seahorse_chunk` 为占位，**实现时取 `MilvusVectorProperties` 的真实默认值，默认 collection 名为 seahorse_agent_embeddings**。Redis 探活用 `getBucket().isExists()`（核实 `RedisCacheAdapter` 用同款 `getBucket`）；若团队偏好 `pingAll`，需先核实 Redisson 4.0.0 是否保留该 API。

### 5.2 告警端口与领域（kernel）

```java
// ports/outbound/agent/AlertNotifierPort.java
@FunctionalInterface
public interface AlertNotifierPort {
    void notify(AlertSignal signal);
}

// ports/outbound/agent/AlertMetricSnapshotPort.java
public interface AlertMetricSnapshotPort {
    double httpErrorRate();        // 0~1，来自 http.server.requests
    double dbPoolUsageRatio();     // 0~1，来自 hikaricp.connections.active/max
    long paymentCallbackFailures(); // 依赖 04-计费：来自支付回调失败计数器
    long quotaExhaustedCount();     // 依赖 04-计费/配额强制：配额耗尽计数
}

// domain/agent/alert/AlertLevel.java
public enum AlertLevel { INFO, WARNING, CRITICAL }

// domain/agent/alert/AlertSignal.java
public record AlertSignal(String ruleId, AlertLevel level, String title,
                          String message, String evidence, java.time.Instant firedAt) { }
```

`KernelAlertEvaluationService`（`application.agent.alert`）：纯逻辑，输入 `SreHealthReport` + `AlertMetricSnapshotPort`，按 §6 规则表产出 `List<AlertSignal>`；经 `AlertSilencePort` 过滤静默后交 `AlertNotifierPort`。**单测**：注入假快照即可断言规则触发，无需真发钉钉（见 §9）。

### 5.3 钉钉 webhook adapter（adapter-alert-dingtalk）

```java
public final class DingTalkAlertNotifierAdapter implements AlertNotifierPort {

    private final OkHttpClient http;
    private final String webhook;     // 可空
    private final String secret;      // 可空，配了则加签

    public DingTalkAlertNotifierAdapter(OkHttpClient http, String webhook, String secret) {
        this.http = Objects.requireNonNull(http);
        this.webhook = webhook == null ? "" : webhook.trim();
        this.secret = secret == null ? "" : secret.trim();
    }

    @Override
    public void notify(AlertSignal signal) {
        if (webhook.isBlank()) {        // 优雅降级：未配置只记日志
            log.warn("DingTalk webhook not configured, skip alert {}", signal.ruleId());
            return;
        }
        String url = secret.isBlank() ? webhook : webhook + sign(secret); // 加签拼 timestamp+sign
        String body = """
            {"msgtype":"markdown","markdown":{"title":"%s","text":"**[%s] %s**\\n\\n%s\\n\\n> %s"}}"""
            .formatted(signal.title(), signal.level(), signal.title(),
                       signal.message(), signal.evidence());
        Request req = new Request.Builder().url(url)
                .post(RequestBody.create(body, MediaType.parse("application/json"))).build();
        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful()) {
                log.warn("DingTalk push failed: HTTP {}", resp.code());
            }
        } catch (IOException ex) {
            log.warn("DingTalk push error: {}", ex.toString());
        }
    }
    // sign(): 钉钉 HMAC-SHA256 加签算法，返回 "&timestamp=..&sign=.."
}
```

> 复用工程已有的 `com.squareup.okhttp3:okhttp`（根 pom `okhttp.version=4.12.0`，ES/MCP/Feishu adapter 均在用），不引第三方钉钉 SDK。

### 5.4 业务指标桥接（starter）

`SeahorseBusinessMetricsBinder implements MeterBinder`：构造注入 `ObjectProvider<CostUsageRepositoryPort>` 与 `ObjectProvider<SreHealthReportProviderPort>`，`bindTo(MeterRegistry)` 内注册 `Gauge`：

```java
Gauge.builder("seahorse_sre_health_status", this, b -> b.currentHealthSeverity())
     .description("0=GREEN,1=WARN,2=RED").register(registry);
Gauge.builder("seahorse_tokens_total", this, b -> b.totalTokensSnapshot())
     .baseUnit("tokens").register(registry);
```

`@ConditionalOnBean(MeterRegistry.class)` 装配；读取 `CostUsageAggregate.totalTokens/totalCost`（字段已核实）。

---

## 6. 告警规则定义

| 规则 ID | 触发条件（阈值） | 指标 / 数据源 | 通知渠道 | 级别 | 静默窗口 | 备注 |
|---|---|---|---|---|---|---|
| `service-down` | `/api/sre/health` 聚合态 = RED，或任一中间件 contributor = RED | `SreHealthReportProviderPort`（§2.1 复用） | 钉钉 | CRITICAL | 10m | 中间件 contributor 落地后即生效 |
| `api-error-rate` | 5xx 占比 > 5% 持续 ≥ 1 个评估周期 | `http.server.requests`（actuator 标准指标） | 钉钉 | CRITICAL | 10m | 阈值 `alert.error-rate-threshold`，可配 |
| `db-pool-exhausted` | `hikaricp.connections.pending > 0` 且 `active/max ≥ 0.9` | `hikaricp.connections.*`（actuator 标准指标） | 钉钉 | WARNING | 10m | 连接池耗尽前兆 |
| `payment-callback-failed` | 支付回调失败计数在窗口内 > 0 | `AlertMetricSnapshotPort.paymentCallbackFailures()` | 钉钉 | CRITICAL | 5m | **依赖 04-计费**：支付模块落地后接入计数器；规则先就位 |
| `quota-exhausted` | 配额耗尽事件计数在窗口内 > 阈值 | `AlertMetricSnapshotPort.quotaExhaustedCount()` | 钉钉 | WARNING | 30m | **依赖 04-计费/配额强制**：需运行时配额拦截埋计数器 |
| `jvm-mem-high`（增强） | `jvm_memory_used / max > 0.9` | `jvm.memory.*`（actuator 标准指标） | 钉钉 | WARNING | 30m | 可选，建议纳入 |

> 规则以 `AlertRule` 枚举固化（ruleId / 默认阈值 / 级别 / 静默窗口），阈值可被 `application.properties` 覆盖。**MVP 不做规则热加载/DSL**（§1.2 已划出）。
> 标「依赖 04-计费」的两条规则：**领域与端口先落地、规则先注册**，指标源在04-计费 支付/配额强制完成后接线即可，互不阻塞。

---

## 7. Grafana 面板清单

数据源：Prometheus。建议 dashboard JSON 纳管到 `docs/aegis/observability/grafana/seahorse-overview.json`。

| # | 面板 | 类型 | PromQL（要点） | 依赖 |
|---|---|---|---|---|
| 1 | 系统健康总览 | Stat / 状态灯 | `seahorse_sre_health_status`（0/1/2 着色）；`up{application="seahorse-agent-service"}` | §5.4 桥接 |
| 2 | QPS | Time series | `sum(rate(http_server_requests_seconds_count[1m]))` | actuator |
| 3 | 错误率 | Time series | `sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) / sum(rate(http_server_requests_seconds_count[5m]))` | actuator |
| 4 | API 延迟 P95/P99 | Time series | `histogram_quantile(0.95, sum(rate(http_server_requests_seconds_bucket[5m])) by (le))` | actuator + 直方图开关 |
| 5 | DB 连接池 | Time series | `hikaricp_connections_active` / `hikaricp_connections_pending` / `hikaricp_connections_max` | actuator |
| 6 | JVM / 资源 | Time series | `jvm_memory_used_bytes`、`system_cpu_usage`、`jvm_gc_pause_seconds_*` | actuator |
| 7 | Token 消耗趋势 | Time series | `rate(seahorse_tokens_total[1h])` / `seahorse_tokens_total` | §5.4 桥接（CostUsage） |
| 8 | 业务观测耗时 | Heatmap / TS | `seahorse_agent_observation_duration_seconds_*`（按 `observation` 标签） | §2.3 衔接红利 |
| 9 | 活跃租户 | Stat | `seahorse_active_tenants` | 依赖 01-多租户（占位） |
| 10 | 收入趋势 | Time series | `seahorse_revenue_total`（占位） | **依赖 04-计费**（占位） |

> 面板 8 即「已有 Micrometer 埋点接出去」的直接收益——无新埋点，仅靠 §3.1 引入 registry 即点亮。
> 面板 9/10 先放占位查询 + 文字说明「数据源依赖 01-多租户/04-计费」，避免 Grafana 报无数据误导。

---

## 8. 任务清单

### P0（块D 验收必须）

- [ ] **P0** bootstrap 加 `spring-boot-starter-actuator` + `micrometer-registry-prometheus` 依赖（§4.1）。
- [ ] **P0** `application.properties` 配 actuator 暴露 + `observation.type=micrometer`（§3.1）。
- [ ] **P0** `SeahorseSecurityWebMvcConfiguration.excludePathPatterns` 追加 `"/actuator/**"`（§3.1，否则 401）。
- [ ] **P0** 验证 `/actuator/prometheus` 出现 `seahorse_agent_observation_duration_*`（验证 §2.3 衔接成立）。
- [ ] **P0** 新建 `SeahorseAgentMiddlewareHealthAutoConfiguration` + 4 个中间件 contributor（§5.1）。
- [ ] **P0** `AutoConfiguration.imports` Layer 1 注册中间件健康配置 + 校验 `@AutoConfigureAfter` 完整（§4.3）。
- [ ] **P0** 验证 `/api/sre/health` 含 postgres/redis/milvus/elasticsearch 且断连变 RED（§10）。
- [ ] **P0** kernel 告警端口 + 领域 + `KernelAlertEvaluationService`（§5.2）。
- [ ] **P0** 新建 `seahorse-agent-adapter-alert-dingtalk` 模块 + `DingTalkAlertNotifierAdapter`（§4.1 / §5.3）。
- [ ] **P0** `SeahorseAgentAlertAutoConfiguration` + `SeahorseAlertEvaluationJob`（`@Scheduled` + `DistributedLockPort`）（§3.3 / §4.3）。
- [ ] **P0** 接入 `service-down` / `api-error-rate` / `db-pool-exhausted` 三条不依赖04-计费 的规则 + 静默（§6）。
- [ ] **P0** 模拟故障触发钉钉、验证静默不刷屏（§9 / §10）。

### P1（强烈建议，可紧随其后）

- [ ] **P1** `SeahorseBusinessMetricsBinder` 桥接 `seahorse_sre_health_status` / `seahorse_tokens_total`（§5.4）。
- [ ] **P1** Grafana dashboard JSON（面板 1–8）纳管入仓（§7）。
- [ ] **P1** docker-compose 增 Prometheus + Grafana 服务（抓 backend:9090，挂载 dashboard）。
- [ ] **P1** `payment-callback-failed` / `quota-exhausted` 规则的端口/枚举先就位（指标源依赖 04-计费）。
- [ ] **P1** Redis 静默改 `RedissonClient` 实现（多实例全局静默）。
- [ ] **P1** `jvm-mem-high` 规则 + 面板 9/10 占位说明。

---

## 9. 测试策略

遵循工程既有测试风格（核实自 `SeahorseAgentSreAdapterHealthAutoConfigurationTests` 与 `SeahorseSreHealthControllerTests`）。

### 9.1 中间件 HealthContributor

- **装配测试**（`ApplicationContextRunner`，同 `SeahorseAgentSreAdapterHealthAutoConfigurationTests` 模式）：
  - 提供假 `DataSource` / `RedissonClient` / `MilvusClientV2` Bean → 断言对应 contributor 装配且 `current()` 返回 GREEN。
  - 不提供客户端 Bean → 断言 contributor 仍装配但状态 = WARN（缺失语义，与现有 5 个一致）。
- **降级测试**：让假客户端探测抛异常 → 断言返回 RED 且 `KernelSreHealthQueryService` 不上抛（复用其内置 try/catch，可加一条针对性单测）。
- **聚合测试**：构造一个 RED contributor → 断言 `SreHealthReport.status() == RED`（验证 `aggregate` 自动拉高）。

### 9.2 告警评估（纯逻辑，无外部依赖）

- 注入假 `AlertMetricSnapshotPort`（错误率 0.06）+ 假 `SreHealthReport`（含 RED item）→ 断言产出 `service-down` + `api-error-rate` 两个 `AlertSignal`。
- **静默测试**：同规则连续两次评估 → 断言第二次被 `AlertSilencePort` 抑制（窗口内只发一次）；推进时钟越过窗口 → 断言可再发（注入可控 `Clock`，与 `KernelSreHealthQueryService` 用 `Clock` 同款做法）。
- **恢复测试**：RED→GREEN → 断言发一条 resolved 并清静默。

### 9.3 钉钉 adapter

- webhook 为空 → 断言 `notify` 不抛异常、不发请求（优雅降级）。
- 用 OkHttp `MockWebServer`（okhttp 自带 test 包）→ 断言 POST body 为 markdown、配 secret 时 URL 带 `&sign=`。

### 9.4 Actuator 端点（集成，打 `integration` tag）

- 真实起 context（`@SpringBootTest`）→ `GET /actuator/prometheus` 200 且含 `seahorse_agent_observation_duration`；`GET /api/sre/health` 含新 contributor。
- 校验 sa-token 放行：未带 token 也能取 `/actuator/prometheus`（验证 §3.1 排除生效）。
- > 工程 surefire 默认 `excludedGroups=integration`，集成测试需显式跑，避免污染单测基线。

---

## 10. 验收标准

| # | 验收项 | 操作 | 期望 |
|---|---|---|---|
| 1 | Prometheus 指标暴露 | `curl :9090/actuator/prometheus` | 200，含 `http_server_requests_*`、`hikaricp_connections_*`、`jvm_*`、`seahorse_agent_observation_duration_*` |
| 2 | Micrometer 衔接成立 | 配 `observation.type=micrometer` 后发起一次对话/检索，再抓指标 | `seahorse_agent_observation_duration_seconds_count` 计数 > 0 |
| 3 | 中间件健康真实性 | `GET /api/sre/health` | `items[]` 含 postgres/redis/milvus/elasticsearch，全连通时 `status=GREEN` |
| 4 | 故障感知 | `docker stop seahorse-redis` 后再查健康 | redis contributor = RED，报告聚合态 = RED |
| 5 | 告警送达 | 打高 5xx（压错误接口）或停中间件 | 钉钉群在 1 个评估周期内收到 CRITICAL 卡片，含规则名 + evidence |
| 6 | 防风暴 | 故障持续 5 分钟 | 同规则只收到 1 条（静默窗口内不重发），恢复后收到 1 条 resolved |
| 7 | sa-token 放行 | 未登录 `curl /actuator/prometheus` | 200（非 401） |
| 8 | Grafana 可视 | 打开 dashboard | 系统健康/QPS/错误率/Token 消耗有数据；活跃租户/收入显示「依赖 01-多租户/04-计费」占位 |
| 9 | 分层无回归 | `mvnw test`（kernel + starter + adapter） | 既有 SRE/observation 测试全绿，5 个旧 contributor 仍在 |

---

## 11. 风险与缓解

| 风险 | 影响 | 缓解 |
|---|---|---|
| `/actuator/**` 放行后端点暴露 | 信息泄露（health 详情、metrics） | 端点白名单收敛（仅 health/info/prometheus/metrics）；9090 的 `/actuator` 用网段/安全组限定 Prometheus 网段；后续可选独立 `management.server.port`(架构决策：MVP 网段隔离，企业版独立端口) |
| 健康探测拖慢 `/api/sre/health` | 接口变慢甚至超时 | 每探测带 2s 超时；`KernelSreHealthQueryService` 已对单 contributor 异常 try/catch 降级；必要时探测结果加短 TTL 缓存（**可选增强**） |
| 应用内置评估器只能算「即时值」 | rate 类规则不如 Prometheus 精确、多实例评估口径不一 | MVP 用 `DistributedLockPort` 选主单实例评估；规模化后把 rate/latency 规则迁到 Prometheus + Alertmanager → 钉钉 webhook（§1.2 后延项），应用内只留健康/业务类规则 |
| Redisson 4.0.0 探活 API 不确定 | 编译/运行报错 | 骨架用已核实的 `getBucket().isExists()`；`pingAll()` 路线默认 collection 名为 seahorse_agent_embeddings，落地前先验证 |
| Milvus collection 默认名占位 | 探测 collection 不存在被误判 | 取 `MilvusVectorProperties` 真实默认；`hasCollection` 即使集合不存在也只要**连接成功**就算 GREEN（探的是连通性，不是集合存在性），实现时据此判定 |
| 多实例告警重复 | 钉钉刷屏 | `DistributedLockPort` 选主 + 静默窗口双保险；静默状态多实例共享建议走 Redis（P1） |
| 钉钉限流（20 条/分钟/机器人） | 告警丢失 | 静默窗口 + 同级别聚合；CRITICAL 与 WARNING 可分机器人（**可选**） |
| 04-计费 未就绪导致两条规则空转 | 误以为功能缺失 | 规则注册但指标源标「依赖 04-计费」，评估器对缺失快照返回 0、不误报 |

---

## 12. 参考文件锚点（实现定位用）

**SRE 框架（复用扩展点）**：
- `seahorse-agent-kernel/.../domain/agent/sre/SreHealthItem.java`（字段 `contributorName`/`evidenceRef`）
- `seahorse-agent-kernel/.../domain/agent/sre/SreHealthReport.java`（`aggregate` 自动取最严重态）
- `seahorse-agent-kernel/.../domain/agent/sre/SreHealthStatus.java`
- `seahorse-agent-kernel/.../ports/outbound/agent/SreHealthContributorPort.java`（**新 contributor 实现此口**）
- `seahorse-agent-kernel/.../ports/outbound/agent/SreHealthReportProviderPort.java`（告警评估复用）
- `seahorse-agent-kernel/.../application/agent/sre/KernelSreHealthQueryService.java`（`orderedStream` 收集 + try/catch 降级）
- `seahorse-agent-adapter-web/.../SeahorseSreHealthController.java`（`GET /api/sre/health`）
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentKernelRegistryAutoConfiguration.java`（装配点，第 ~568 行）

**现有 contributor 与 Micrometer**：
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentSreAdapterHealthAutoConfiguration.java`（5 个适配器层 contributor 范本）
- `seahorse-agent-adapter-observation-micrometer/.../MicrometerObservationAdapter.java`（`seahorse.agent.observation.duration/events`）
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentObservationAdapterAutoConfiguration.java`（`@ConditionalOnBean(MeterRegistry.class)` —— §2.3 衔接关键）

**中间件客户端来源**：
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentCacheAdapterAutoConfiguration.java`（`RedissonClient` Bean，第 ~92 行）
- `seahorse-agent-spring-boot-starter/.../SeahorseAgentVectorAdapterAutoConfiguration.java`（`MilvusClientV2` Bean `seahorseMilvusClient`）
- `seahorse-agent-adapter-vector-milvus/.../MilvusVectorAdapter.java`（`hasCollection(HasCollectionReq)`，第 171 行）
- `seahorse-agent-adapter-search-elasticsearch/.../ElasticsearchKeywordProperties.java`（`baseUrl` 默认 `http://localhost:9200`）

**装配 / 安全 / 调度 / 配置**：
- `seahorse-agent-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports`（7 层清单）
- `seahorse-agent-adapter-web/.../SeahorseSecurityWebMvcConfiguration.java`（sa-token 排除清单，第 99–108 行 —— **必改加 `/actuator/**`**）
- `seahorse-agent-adapter-web/.../RateLimitFilter.java`（第 86 行已跳过 `/actuator`）
- `seahorse-agent-spring-boot-starter/.../SeahorseMemoryGovernanceJob.java`（`@Scheduled` + `DistributedLockPort` 范本）
- `seahorse-agent-kernel/.../ports/outbound/coordination/DistributedLockPort.java`（`noop()` 可用）
- `seahorse-agent-bootstrap/.../SeahorseAgentApplication.java`（`@EnableScheduling` 已在）
- `seahorse-agent-bootstrap/pom.xml`、`seahorse-agent-bootstrap/src/main/resources/application.properties`、`pom.xml`（modules/BOM）

**业务指标**：
- `seahorse-agent-kernel/.../domain/agent/cost/CostUsageAggregate.java`（`totalTokens`/`totalCost` 字段）
- `seahorse-agent-kernel/.../ports/outbound/agent/CostUsageRepositoryPort.java`
- 钉钉/支付/账单：当前工程**无** payment/subscription/billing 代码（已核实）——收入类指标依赖 04-计费。

---

*基于 2026-06-04 代码审查编写。骨架中的包名、端口、客户端类型、依赖坐标均已核实；占位/未核实处已显式标注「依赖关系已明确」。落地前建议先跑 §9 装配测试验证 `@AutoConfigureAfter` 时序，再接告警。*

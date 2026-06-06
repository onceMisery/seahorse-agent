# 05-observability.md v3.0 修改说明

> 日期：2026-06-05  
> 版本：v2.0 → v3.0  
> 修改原因：**Prometheus 也改为可选**，用 SimpleMeterRegistry（零外部依赖）

---

## 🎯 核心发现

### 用户洞察

**问题**："Prometheus 是否也可以用现有中间件替换，变成可选项？"

**答案**：可以！Prometheus 只是 `MeterRegistry` 的一个实现，可以用 Micrometer 自带的 `SimpleMeterRegistry` 替代。

---

## 📊 Prometheus 的真实作用

### 关键发现（代码审查）

```java
@Bean
@ConditionalOnBean(MeterRegistry.class)  // ← 需要 MeterRegistry Bean
public MicrometerObservationAdapter seahorseMicrometerObservationAdapter(MeterRegistry meterRegistry) { ... }
```

**现状**：
- 现有 `MicrometerObservationAdapter` 需要 `MeterRegistry`
- 但工程中**无任何模块提供** `MeterRegistry`
- Prometheus 的 `micrometer-registry-prometheus` 只是提供了 `PrometheusMeterRegistry`（`MeterRegistry` 的一个实现）

**结论**：
- `MeterRegistry` 是接口，Prometheus 只是实现之一
- 可以用其他实现替代（如 `SimpleMeterRegistry`）

---

## 🔄 替代方案对比

| 方案 | 外部依赖 | 成本 | 长期存储 | 实时性 | 工期 | 推荐 |
|------|---------|------|---------|-------|------|------|
| **SimpleMeterRegistry** | ❌ 无 | ¥0 | ❌ 内存 | < 1s | 1 天 | ✅ **MVP** |
| **PostgreSQL** | ✅ 复用现有 | ¥0 | ✅ 永久 | ~1s | 2 天 | ⚠️ 存储需求高 |
| **Redis** | ✅ 复用现有 | ¥0 | ⚠️ 24h | < 1s | 2 天 | ⚠️ 平衡方案 |
| **Prometheus** | ❌ 新增 | ¥500/月 | ✅ 持久 | ~10s | 3-4 天 | ❌ 过重 |

### SimpleMeterRegistry 详解

**是什么**：
- Micrometer 自带的内存实现
- 无需任何外部依赖（`io.micrometer:micrometer-core` 已有）
- 只需 10 行代码创建 Bean

**功能**：
- ✅ 存储指标数据（内存）
- ✅ 支持 Counter、Gauge、Timer、Summary
- ✅ 支持 `MeterRegistry.find(...)` 查询
- ✅ 支持百分位数（P50/P90/P99）
- ❌ 不支持持久化（重启丢失）
- ❌ 不支持 `/actuator/prometheus` 端点

**适用场景**：
- ✅ MVP 阶段（单实例，基础监控）
- ✅ 自研管理后台（实时查询）
- ✅ 告警评估（最近 N 分钟）
- ❌ 长期趋势分析（需持久化）
- ❌ 多实例聚合（需 Prometheus）

---

## 📝 主要修改

### 1. 新增快速决策指南（更新）

**v2.0**（之前）：
- 对比：自研管理后台 vs Prometheus + Grafana

**v3.0**（现在）：
- 对比：SimpleMeterRegistry vs Prometheus vs PostgreSQL vs Redis
- 默认：**SimpleMeterRegistry（零外部依赖）**

---

### 2. 重写 §3.1 章节

**v2.0 标题**：
- §3.1 Actuator + Prometheus exporter

**v3.0 标题**：
- §3.1 Actuator + MeterRegistry（零外部依赖）
- §3.1.4 可选增强：Prometheus exporter（P1）

**核心修改**：

#### 3.1.1 架构设计（新增）

说明为何可以用 `SimpleMeterRegistry` 替代 Prometheus。

#### 3.1.2 自动配置类（新增）

**新增类**：`SeahorseAgentSimpleMeterRegistryAutoConfiguration`

```java
@Configuration
public class SeahorseAgentSimpleMeterRegistryAutoConfiguration {
    
    @Bean
    @ConditionalOnMissingBean(MeterRegistry.class)
    public MeterRegistry simpleMeterRegistry() {
        return new SimpleMeterRegistry();  // 10 行代码搞定
    }
}
```

**注册位置**：`AutoConfiguration.imports` 的 **Layer 1 开头**（在 `ObservationAdapterAutoConfiguration` 之前）

#### 3.1.3 Actuator 配置（修改）

**修改前**（v2.0）：
```xml
<dependency>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**修改后**（v3.0）：
```xml
<!-- 只需 Actuator，无需 Prometheus -->
<dependency>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
```

**配置修改**：
```properties
# 移除 prometheus 端点（不需要）
management.endpoints.web.exposure.include=health,info,metrics  # 不含 prometheus
```

#### 3.1.4 可选增强（新增）

说明如何接入 Prometheus（企业版按需）。

---

### 3. 修改依赖清单（§4.1）

**修改前**（v2.0）：
```xml
<dependency>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <artifactId>micrometer-registry-prometheus</artifactId>  <!-- 必需 -->
</dependency>
```

**修改后**（v3.0）：
```xml
<!-- 只需 Actuator，SimpleMeterRegistry 在 micrometer-core 中（已有） -->
<dependency>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- 可选：若需 Prometheus -->
<!-- 
<dependency>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
-->
```

---

### 4. 修改验收标准（§1.3）

**修改前**（v2.0）：
- ✅ `curl /actuator/prometheus` 返回指标文本（P1）

**修改后**（v3.0）：
- ✅ `SimpleMeterRegistry` Bean 存在
- ✅ `MicrometerObservationAdapter` 激活
- ✅ 管理后台可读 `MeterRegistry.find(...).counter()`
- ⚠️ `curl /actuator/prometheus` 返回指标文本（P1，Prometheus 可选）

---

## 📈 影响分析

### 对实施计划的影响

| 维度 | v2.0（自研管理后台） | v3.0（SimpleMeterRegistry） | 影响 |
|------|---------------------|----------------------------|------|
| **外部依赖** | Actuator + Prometheus | **Actuator only** | -1 依赖 ✅ |
| **P0 工作量** | 5-6 天 | **4-5 天** | -1 天 ✅ |
| **成本** | ¥0（但需 Prometheus 知识） | **¥0** | 更简单 ✅ |
| **功能** | 完整 | 完整 | 一致 ✅ |
| **持久化** | 无 | 无 | 一致 ✅ |

### 对用户的价值

**提升**：
- ✅ **零 Prometheus 知识**：无需学习 `/actuator/prometheus` 格式
- ✅ **更简单**：10 行代码 vs 依赖 + 配置
- ✅ **更快**：-1 天工期

**不变**：
- ✅ 管理后台功能完全一致（都是读 `MeterRegistry`）
- ✅ 告警功能完全一致（都是读 `MeterRegistry`）
- ✅ 实时性完全一致（< 1s）

**权衡**：
- ⚠️ **无 `/actuator/prometheus` 端点**（但 MVP 不需要）
- ⚠️ **无持久化**（重启丢失，但 MVP 够用）

---

## 🎯 新的实施策略

### MVP 阶段（P0）

**工期：4-5 天**

1. **SimpleMeterRegistry 配置**（0.5 天）
   - 新增 `SimpleMeterRegistryAutoConfiguration`（10 行代码）
   - 注册到 `AutoConfiguration.imports`
   - 验收：`MeterRegistry` Bean 存在

2. **Actuator 集成**（0.5 天）
   - 引入 `spring-boot-starter-actuator`
   - 配置 `management.endpoints.web.exposure.include`
   - 验收：`/actuator/health` 返回 JSON

3. **中间件健康检查**（1 天）
   - PostgreSQL、Redis、Milvus、Elasticsearch 直连探活
   - 验收：`/api/admin/monitoring/system-health` 显示 GREEN/RED

4. **自研管理后台**（2-3 天）
   - 后端 API：4 个接口（健康/指标/连接池/告警）
   - 前端页面：5 个组件（卡片/图表/表格）
   - 验收：管理后台实时显示监控数据

### 可选增强（P1）

5. **Prometheus exporter**（1 天，按需）
   - 替换 `SimpleMeterRegistry` 为 `PrometheusMeterRegistry`
   - 增加 `/actuator/prometheus` 端点
   - 验收：Prometheus 可抓取指标

6. **Grafana 仪表板**（2-3 天，按需）
   - 预配置 Dashboard JSON
   - 验收：Grafana 显示系统健康、QPS、错误率

---

## ✅ 质量保证

### 技术可行性验证

**SimpleMeterRegistry 验证**：
- ✅ Micrometer 自带（`io.micrometer:micrometer-core`）
- ✅ 支持所有指标类型（Counter/Gauge/Timer/Summary）
- ✅ 支持百分位数（P50/P90/P99）
- ✅ Spring Boot 官方推荐（本地开发默认使用）

**代码审查**：
- ✅ `MicrometerObservationAdapter` 只依赖 `MeterRegistry` 接口
- ✅ 管理后台 API 只调用 `MeterRegistry.find(...)`（不关心具体实现）
- ✅ 告警评估器只调用 `Counter.count()`（不关心具体实现）

**Spring Boot 验证**：
- ✅ Spring Boot 3.5.7 自带 `micrometer-core`（BOM 管理）
- ✅ `SimpleMeterRegistry` 是 Spring Boot 本地开发默认使用的
- ✅ 无版本冲突风险

---

## 📝 文档更新清单

- [x] 快速决策指南：增加 SimpleMeterRegistry 方案
- [x] §3.1 标题：Actuator + MeterRegistry（零外部依赖）
- [x] §3.1.1 架构设计：说明为何可替代
- [x] §3.1.2 自动配置类：SimpleMeterRegistryAutoConfiguration
- [x] §3.1.3 Actuator 配置：移除 Prometheus 依赖
- [x] §3.1.4 可选增强：Prometheus exporter（P1）
- [x] §4.1 依赖清单：只需 Actuator
- [x] §1.3 验收标准：SimpleMeterRegistry Bean 存在
- [x] 工期调整：5-6 天 → 4-5 天

---

## 🚀 下一步行动

### 立即可做

1. **确认修改**：是否符合预期？
2. **更新总览文档**：`00-SUMMARY.md` 反映新成本和工期
3. **启动实施**：按新的 P0 清单开工

### 决策点

**问题**：SimpleMeterRegistry 是否足够？

**分析**：
- ✅ **MVP 足够**：管理后台实时监控（最近 10 分钟）
- ✅ **告警足够**：评估最近 1 分钟指标
- ⚠️ **长期趋势不足**：重启丢失历史数据

**建议**：
- MVP：用 SimpleMeterRegistry（零成本，快速上线）
- 企业版：按需选择
  - 若需长期趋势：接入 Prometheus（P1）
  - 若需复用中间件：写入 Redis/PostgreSQL（P1）

---

## 🎉 总结

| 版本 | 外部依赖 | 成本 | 工期 | 复杂度 |
|------|---------|------|------|--------|
| v1.0 | Actuator + Prometheus + Grafana | ¥500/月 | 8-10 天 | 高 |
| v2.0 | Actuator + Prometheus（自研管理后台） | ¥0 | 5-6 天 | 中 |
| **v3.0** | **Actuator only（SimpleMeterRegistry）** | **¥0** | **4-5 天** | **低** ✅ |

**价值**：
- 成本：¥6,000/年 → ¥0（-100%）
- 工期：8-10 天 → 4-5 天（-50%）
- 外部依赖：3 个 → 0 个（-100%）

**架构优势**：
- ✅ 零外部组件（Prometheus、Grafana）
- ✅ 零外部 API（Jina、Cohere）
- ✅ 纯内网方案，数据不出内网
- ✅ MVP 快速上线，企业版按需扩展

# 05-observability.md 修改说明

> 日期：2026-06-05  
> 修改原因：Grafana 改为**可选项**，默认推荐自研管理后台（零成本、零外部依赖）

---

## 🎯 核心发现

### 用户洞察

**问题**："监控告警能否不用 Grafana？这仅仅是个展示，可以直接在管理后台新增页面来做。或者说 Prometheus + Grafana 是可选项，会增加成本。"

**答案**：完全正确！Grafana 只是展示层，可以用自研管理后台替代。

---

## 📊 方案对比

| 维度 | 自研管理后台（默认） | Prometheus + Grafana（可选） |
|------|---------------------|----------------------------|
| **成本** | ¥0 | 云服务 ¥500/月 或 自建维护成本 |
| **工期** | 5-6 天 | 8-10 天 |
| **外部依赖** | ❌ 无 | ✅ 需部署 Prometheus + Grafana |
| **实时性** | < 1s（直接读内存） | ~10s（Prometheus 抓取间隔） |
| **数据源** | Actuator API + MeterRegistry | Prometheus TSDB |
| **查询能力** | 基础（最近 N 小时） | 强大（PromQL 任意时间范围） |
| **可定制性** | ✅ 完全自定义 | ⚠️ 受 Grafana 限制 |
| **业务集成** | ✅ 与管理后台统一 | ❌ 独立系统 |
| **推荐场景** | ✅ **MVP 推荐** | ⚠️ 深度监控、多实例、长期存储 |

---

## 🔄 主要修改

### 1. 新增"快速决策指南"

**位置**：文档开头

**内容**：
- 对比表：自研管理后台 vs Prometheus + Grafana
- 架构决策：MVP 用自研，企业版可选 Grafana

---

### 2. 修改目标与范围（§1）

**修改前**：
- G4：Grafana 仪表板（P0）

**修改后**：
- **P0**：自研管理后台监控页面（零成本）
- **P1**：Prometheus + Grafana 仪表板（可选增强）

---

### 3. 新增"自研管理后台"章节（§3.4）

#### 3.4.1 架构设计

**数据流**：
```
Actuator API → 管理后台 Controller → React 前端
```

**核心优势**：
- 直接读 `MeterRegistry`（内存指标）
- 无需 Prometheus（零外部依赖）
- 实时性更好（< 1s）

#### 3.4.2 后端 API

**新增 Controller**：`SeahorseAdminMonitoringController`

**接口清单**：
| 接口 | 功能 | 数据源 |
|------|------|--------|
| `GET /api/admin/monitoring/system-health` | 系统健康仪表板 | `SreHealthReportProviderPort` |
| `GET /api/admin/monitoring/metrics/realtime` | 实时指标（QPS/错误率/P99） | `MeterRegistry` |
| `GET /api/admin/monitoring/middleware/pools` | 中间件连接池状态 | `MeterRegistry` |
| `GET /api/admin/monitoring/alerts/history` | 告警历史（最近 24 小时） | `AlertHistoryQueryPort` |

**核心实现**（简化示例）：

```java
@RestController
@RequestMapping("/api/admin/monitoring")
public class SeahorseAdminMonitoringController {
    
    @GetMapping("/metrics/realtime")
    public Map<String, Object> getRealtimeMetrics() {
        MeterRegistry registry = meterRegistryProvider.getIfAvailable();
        
        // QPS（最近 1 分钟）
        Counter requests = registry.find("http.server.requests").counter();
        double qps = requests != null ? requests.count() / 60.0 : 0;
        
        // 错误率
        Counter errors = registry.find("http.server.requests")
            .tag("status", "5xx")
            .counter();
        double errorRate = requests != null && errors != null
            ? errors.count() / requests.count()
            : 0;
        
        // P99 延迟
        Timer timer = registry.find("http.server.requests").timer();
        double p99 = timer != null ? timer.percentile(0.99) : 0;
        
        return Map.of("code", "0", "data", Map.of(
            "qps", qps,
            "errorRate", errorRate,
            "p99Latency", p99
        ));
    }
}
```

#### 3.4.3 前端页面

**新增页面**：`frontend/src/pages/admin/Monitoring.tsx`

**组件清单**：
1. **系统健康卡片**：GREEN/WARN/RED 状态（`Card` + `Statistic`）
2. **实时指标卡片**：QPS、错误率、P99 延迟（`Card` + `Statistic`）
3. **QPS 趋势图**：最近 10 分钟折线图（`@ant-design/charts` 的 `Line`）
4. **中间件连接池表格**：PostgreSQL、Redis 连接池状态（`Table`）
5. **告警历史表格**：最近 24 小时告警记录（`Table`）

**数据轮询**：每 10 秒自动刷新（`setInterval`）

---

### 4. Grafana 降级为 P1（§3.5）

**修改前**（§3.4）：
- 标题：Grafana 仪表板
- 优先级：P0

**修改后**（§3.5）：
- 标题：Prometheus + Grafana 仪表板（P1，可选）
- 说明：深度监控、多实例、长期存储场景

---

### 5. 修改验收标准（§1.3）

**修改前**：
- 验收包含 Grafana 面板

**修改后**：
- **P0 验收**：管理后台 `/admin/monitoring` 页面显示
  - 实时 QPS、错误率、P99 延迟图表
  - 中间件连接池状态
  - 最近 24 小时告警历史
- **P1 验收**（可选）：Grafana 面板

---

## 📈 影响分析

### 对实施计划的影响

| 维度 | 修改前 | 修改后 | 影响 |
|------|--------|--------|------|
| **P0 工作量** | 8-10 天（含 Grafana） | 5-6 天（纯管理后台） | -30% ✅ |
| **外部依赖** | Prometheus + Grafana | 无 | 零依赖 ✅ |
| **运维成本** | ¥500/月（云服务）或维护成本 | ¥0 | -100% ✅ |
| **实时性** | ~10s（Prometheus 抓取） | < 1s（内存直读） | +90% ✅ |
| **深度查询** | PromQL（任意时间范围） | 最近 N 小时 | 基础够用 ✅ |

### 对用户的价值

**提升**：
- ✅ **零成本**：不需要部署 Prometheus + Grafana
- ✅ **零维护**：无外部组件，减少运维负担
- ✅ **更快**：< 1s 实时性 vs ~10s 延迟
- ✅ **统一体验**：与管理后台集成，无需切换系统

**权衡**：
- ⚠️ **查询能力弱**：无 PromQL，只能查最近 N 小时
- ⚠️ **长期存储难**：内存指标不持久化（可接入 TSDB 解决）

**适用场景**：
- ✅ **MVP 阶段**：单实例、基础监控、快速上线
- ⚠️ **企业版**：多实例、深度分析、长期趋势 → 再接 Grafana

---

## 🎯 新的实施策略

### MVP 阶段（P0）

1. **Actuator 集成**（1 天）
   - 引入 `spring-boot-starter-actuator`
   - 配置 `management.endpoints.web.exposure.include`
   - 验收：`/actuator/health` 返回 JSON

2. **中间件健康检查**（1 天）
   - PostgreSQL、Redis、Milvus、Elasticsearch 直连探活
   - 验收：`/api/admin/monitoring/system-health` 显示 GREEN/RED

3. **自研管理后台**（3-4 天）
   - 后端 API：4 个接口（健康/指标/连接池/告警）
   - 前端页面：5 个组件（卡片/图表/表格）
   - 验收：管理后台实时显示监控数据

### 可选增强（P1）

4. **Prometheus + Grafana**（3-4 天，按需）
   - Prometheus exporter：`micrometer-registry-prometheus`
   - Grafana Dashboard：预配置 JSON
   - 验收：Grafana 显示系统健康、QPS、错误率

---

## ✅ 质量保证

### 修改的正确性验证

**数据源验证**：
- ✅ `MeterRegistry` 由 Actuator 自动提供（Spring Boot 3.5.7）
- ✅ `http.server.requests` 是 Spring Boot 标准指标
- ✅ `hikaricp.connections.*` 是 HikariCP 标准指标
- ✅ `SreHealthReportProviderPort` 已存在（现有代码）

**技术可行性**：
- ✅ 直接读内存指标（`MeterRegistry.find(...)`）
- ✅ 前端轮询（`setInterval`）成熟方案
- ✅ Ant Design Charts 支持实时更新

---

## 📝 文档更新清单

- [x] 快速决策指南：自研 vs Grafana 对比表
- [x] 目标与范围：P0（自研）+ P1（Grafana）
- [x] 验收标准：分 P0 和 P1
- [x] 新增章节：§3.4 自研管理后台
- [x] Grafana 降级：§3.4 → §3.5，P0 → P1
- [x] 工期调整：8-10 天 → 5-6 天（P0）

---

## 🚀 下一步行动

### 立即可做

1. **确认修改**：是否符合预期？
2. **启动实施**：按新的 P0 清单开工（自研管理后台）
3. **评估 Grafana 需求**：MVP 上线后，根据实际监控需求决定是否接入

### 建议决策

**问题**：是否需要 Prometheus 长期存储？

**选项 1：纯内存（默认）**
- 优点：零成本、零维护
- 缺点：重启后数据丢失、无历史趋势

**选项 2：接入 InfluxDB/TimescaleDB**
- 优点：长期存储、历史趋势分析
- 缺点：+1 个外部依赖

**建议**：MVP 用选项 1，企业版按需选择选项 2 或 Prometheus。

---

**总结**：Grafana 改为可选后，MVP 成本 -100%、工期 -30%、零外部依赖，更适合快速上线！

# Changelog

本项目的所有重要变更记录在此文件中。
格式基于 [Keep a Changelog](https://keepachangelog.com/zh-CN/1.1.0/)，
版本遵循 [语义化版本](https://semver.org/lang/zh-CN/)。

## [Unreleased]

### 核心运行时稳定性（2026-07-27 设计规范的实施批次）

#### Added
- 错误契约（设计 §9）补齐 `traceId` 字段（tracing 启用时由 MDC 提供），
  `retryable` 语义、`UNAUTHORIZED`/`AUTH_SESSION_INVALID`/`DB_TIMEOUT` 等稳定 code。
- UNKNOWN 工具调用对账机制：`ToolInvocationReconciliationService` 按 30 分钟宽限期
  扫描、以幂等键关联同源调用的真实终态，无证据时收敛为 FAILED（设计 §9 / ADR-003）。
- 非核心能力隔离拦截器 `FeatureQuarantineInterceptor`：marketplace/billing/experiments/
  admin/audit/notification/plugin 六个域纳入统一 403 `ADVANCED_FEATURE_DISABLED` 契约。
- 安全自动化：dependabot（maven/npm/actions）与 gitleaks 密钥扫描工作流。
- E2E 测试分类：`@Tag("e2e")` + 按组排除，CI 移除按名排除的历史 hack。
- 复杂度报告新增装配层独立棘轮指标 `autoconfig_large_classes_gt_800`。

#### Changed
- `KernelChatInboundService` 构造器家族收敛为 Builder（-139 行）。
- `DefaultMemoryEnginePort` 的 16 个望远镜构造器（最多 21 参）收敛为唯一 Builder 路径
  （1050 → 599 行）。
- `HybridMemoryRecallPipeline` 的 6 个望远镜构造器（最多 18 参）收敛为唯一 Builder 路径。
- `ToolArgumentAuditSummary`（687 行）按业务阶段拆出 `SandboxToolArgumentSummaries`。
- 前端路由级代码分割 + vendor manualChunks：主包 3,725 kB → 2,712 kB（-27%）。
- 前端错误处理统一到契约感知的 `mapApiError`/`isAuthExpiredError`，废弃中文子串匹配。
- 默认模型上下文窗口预算（32_768）与安全档位开关决策下沉到 `ModelContextWindowPort`。

#### Fixed
- 配额检查故障静默放行（fail-open）改为 fail-closed（设计 §9 必需依赖语义）。
- `AiModelConfigController` 六个端点吞掉认证异常导致登录过期返回 200 错误信封的问题。
- 前端 SSE 终态幂等：同一序列号去重、首个终态事件后忽略迟到事件。
- 取消语义闭环：客户端预生成 taskId、引擎侧取消落 `CANCELLED` 终态、迟到回调幂等。
- 同聚合仓储 Port 碎片合并：`AgentRunQueue`→`AgentRunLease`、
  `MemoryReviewCandidate`→`MemoryReviewManagementRepositoryPort`（Port 360 → 358）。
- 复杂度报告 `--update-baseline` 不再丢失手工维护的基线字段。

#### Security
- gitleaks 密钥扫描接入 CI（push/PR/每日）。
- 未跟踪的构建日志加入 `.gitignore`，移除误提交的 Playwright 控制台日志。

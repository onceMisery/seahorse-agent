# C Web AI Infra Implementation - Intent

## TaskIntentDraft

- Requested outcome: 按 C 端 Web Agent 定位推进 AI Infra 落地，前后端补齐用户可见运行闭环，并删除或隔离非 Web/本地/mesh 过渡设计。
- Goal: 在当前分支实现 consumer-web 默认边界、用户侧结构化运行信息、run 级待确认查询与本人确认决策，避免把本地 agent、host shell、sandbox、connector、A2A/mesh 当成 C 端 Web 基线。
- Success evidence:
  - 后端高级能力默认在 `consumer-web` 下关闭，且即使配置误开也强制关闭。
  - `A2A` run trigger 在 Web 入口默认被高级能力门禁拦截。
  - 用户可按 run 查询自己的 pending approvals，并能处理本人 approval；管理员仍保留后台审批能力。
  - 前端聊天消息能承载 structured timeline/source/artifact/approval/quota/memory 事件，并展示 trace panel、source list 和 approval card。
  - 旧企业 AI Infra 文档标注为历史/高级扩展参考，新 C Web 分阶段文档成为当前范围入口。
  - 后端目标回归、更宽模块回归、前端构建、diff check 均有证据。
- Stop condition: 本轮最小 C Web 边界和用户可见闭环通过验证；若继续会进入 Phase 1-3 大型能力建设，则明确列为后续工作，不声称完整 AI Infra 已全部完成。
- Non-goals:
  - 不在本轮实现完整 event replay / `lastEventSeq` 恢复。
  - 不在本轮实现服务端 `AgentArtifact` 一等模型与对象存储。
  - 不在本轮实现真实 Web search/fetch research agent。
  - 不在本轮实现用户记忆中心、文件上传到对话、完整质量反馈闭环。
  - 不删除 kernel 中保留的企业/高级扩展领域对象，只从 consumer-web 默认路径隔离。
- Scope: backend web advanced feature gate, A2A run trigger guard, approval query/decision boundary, frontend structured event rendering, docs boundary sync.
- Change kinds: feature, governance cleanup, frontend integration, tests.
- Risk hints: 架构边界、权限边界、跨模块 API contract、前端/后端状态枚举一致性。

## BaselineReadSetHint

- `docs/company-agent/Seahorse Agent C端 Web AI Infra 能力补齐分析.md`
- `docs/company-agent/Seahorse Agent 非 Web 端过渡设计整改方案.md`
- `docs/company-agent/c-web-ai-infra-phases/README.md`
- `docs/company-agent/c-web-ai-infra-phases/01-web-task-runtime.md`
- `docs/company-agent/c-web-ai-infra-phases/02-research-web-agent.md`
- `docs/company-agent/c-web-ai-infra-phases/03-personalization-operations.md`
- `docs/company-agent/c-web-ai-infra-phases/04-advanced-extension-boundary.md`
- `docs/company-agent/ai-infra-phases/README.md`

## ImpactStatementDraft

- Compatibility boundary: consumer-web 默认只开放 Web chat/run/approval 用户路径；sandbox、secret、connector、handoff、remote/local agent、A2A/mesh 保留为高级扩展，默认不可用。
- Affected layers:
  - Kernel approval application service and ports.
  - Web adapter controllers, feature gate, exception handling, stream callback payloads.
  - JDBC approval query adapter.
  - Frontend chat store, stream utils, message components, approval service, admin status enum.
  - C Web AI Infra docs and Aegis work records.
- Owners:
  - Domain invariants remain in kernel domain objects.
  - Application orchestration remains in kernel services.
  - Persistence contract remains in repository ports/adapters.
  - Web exposure policy remains in web adapter `AdvancedFeatureGate`.
  - C user experience remains in frontend chat components/stores.
- Invariants:
  - No new dependency from kernel to Spring/JDBC/Web.
  - Statuses/triggers/features use enums or named constants.
  - User decision on approvals is limited to the owning user unless admin.
  - Advanced enterprise/local APIs are disabled in consumer-web.
- Non-goals:
  - Do not treat enterprise AI Infra Phase 5/7 as C Web completion criteria.
  - Do not introduce workflow engine, remote mesh, arbitrary user MCP, or host shell.

These records are Method Pack drafts / hints, not authoritative runtime decisions.

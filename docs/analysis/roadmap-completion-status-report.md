# 架构路线图近期与中期设计完成情况分析报告

日期：2026-06-22

本报告基于代码库实际实现，对 `docs/roadmap/architecture-roadmap-and-vision.md` 中已经完成或已合入 main 的能力进行归档。路线图文档只保留未完成、待真实测试、待联调和待产品化的规划项；已完成内容以本报告为准。

## 0. 2026-06-22 已完成基线归档

以下能力已经合入 main，不再作为未来规划放在路线图主体中。若缺少真实端到端测试，它们应进入路线图的“真实 Test Case 门禁”，而不是继续按功能建设规划重复描述。

| 已完成能力 | 当前证据 | 后续仅保留为 |
|---|---|---|
| Clean Architecture + Ports and Adapters 模块边界 | `seahorse-agent-kernel`、`seahorse-agent-adapter-*`、`seahorse-agent-spring-boot-autoconfigure` | 架构基线 |
| 轻量/全量 Docker 部署路径 | `docker-compose.yml`、`docker-compose.full.yml`、readiness 诊断 | 回归验证基线 |
| RAG 检索与 Trace | `SeahorseRagTraceController`、`t_rag_trace_run/node`、Milvus/Elasticsearch/Ollama 链路 | 质量回归基线 |
| 记忆、用户画像、outbox、maintenance、quality/conflict 基座 | `SeahorseMemory*Controller`、`t_user_profile_fact`、`t_memory_*` 表 | 记忆治理基线 |
| 交互式记忆冲突聊天闭环 | `KernelChatPreparationSupport`、`InteractiveMemoryConflictPromptPolicy`、`POST /memories/conflicts/interactive-resolve`、`MemoryConflictInteractiveCard`、`scripts/e2e-interactive-memory-conflict-smoke.ps1` | 记忆质量交互基线 |
| Agent、Tool、Skill、审批、配额、资源 ACL、审计、成本接口与页面 | `SeahorseAgent*Controller`、`sa_agent_*`、`sa_audit_event`、`sa_cost_usage_record` | 企业治理基线 |
| 消息树与分支游标 | `t_conversation_branch_cursor`、`ConversationBranchInboundPort`、`SeahorseConversationController`、前端 `sessionService` | 真实测试门禁 |
| 角色卡 | `RoleCardPage`、`SeahorseRoleCardController`、`sa_role_card` 相关表/仓储 | 真实测试门禁 |
| 运行方案 | `RunProfilePage`、`KernelRunProfileService`、`sa_run_profile`、risk/audit/gate API | 真实测试门禁 |
| 运行实验 | `RunExperimentPage`、`KernelRunExperimentService`、`sa_run_experiment*` | 真实测试门禁与报告增强 |
| RunContextSnapshot | `t_run_context_snapshot`、`KernelRunContextSnapshotService`、`ContextSnapshotInspectorTab` | 审计与回归基线 |
| AgentScope / Nacos A2A 基座 | `seahorse-agent-adapter-agent-agentscope`、`scripts/agentscope-a2a-e2e.ps1`、AgentScope tests | 生产硬化与真实长链路验证 |
| MCP HTTP / stdio 基座 | `seahorse-agent-adapter-mcp-http`、`StdioMcpClient`、`McpServerRuntimeRegistry`、前端 MCP service | 安全治理与真实测试门禁 |
| 管理后台入口可达性 | `AdminLayout`、各 `/admin/*` 页面、feature unavailable/empty state | 回归测试基线 |

## 1. 当前完成状态摘要

| 分类 | 当前状态 | 后续去向 |
|---|---|---|
| 近期/中期历史切片 | 作为已完成基线归档 | 仅保留持续回归，不再写入路线图规划主体 |
| 6/22 合入的 Agent 控制面能力 | 已通过 full Docker/API/Playwright smoke 重新验证 | 近期稳定基线与持续回归 |
| AgentScope / Nacos A2A | 基座已合入，需生产硬化和真实长链路验证 | 路线图近期/中期 |
| MCP stdio / HTTP | 基座已合入，需安全治理与 Tool Gateway 收敛 | 路线图近期/中期 |
| Marketplace / Sandbox / Context Pack 等远期基座 | 多数已有代码基座，但仍需产品化、联动和真实验收 | 路线图中期/远期 |

## 2. 历史评估（2026-06-19，保留用于审计）

以下内容是 2026-06-19 的完成度审计记录，用于追溯当时的差距判断；不代表 2026-06-22 当前状态。当前状态以“2026-06-22 已完成基线归档”和“当前完成状态摘要”为准。

| 阶段 | 总目标数 | 已完成 | 部分完成 | 未完成 | 完成率 |
|---|---|---|---|---|---|
| 近期设计（0-4 周） | 5 | 4 | 1 | 0 | **90%** |
| 中期 M1: RAG 质量评测 | 5 切片 | 4 | 1 | 0 | **85%** |
| 中期 M2: 入库治理 | 5 切片 | 5 | 0 | 0 | **100%** |
| 中期 M3: 记忆质量治理 | 5 切片 | 3 | 2 | 0 | **70%** |
| 中期 M4: Agent 生产准备 | 5 切片 | 5 | 0 | 0 | **100%** |
| 中期 M5: starter-all 验收 | 4 切片 | 4 | 0 | 0 | **100%** |
| **总计** | **29 切片** | **25** | **3** | **0** | **91%** |

## 3. 近期设计（0-4 周）历史完成情况

### D1: 登录与会话稳定性 — ✅ 已完成

| 设计内容 | 代码证据 | 状态 |
|---|---|---|
| `Authorization: Bearer <token>` 固化 | `SeahorseSecurityWebMvcConfiguration` 配置路径白名单，`sa-token` Bearer 前缀 | **已实现** |
| 前端 `/api` 代理路径差异 | `nginx.conf` 反向代理 `/api` → `backend:9090`；`vite.config.ts` 开发代理 | **已实现** |
| 登录过期诊断 | `api.ts` 响应拦截器处理 401 → `storage.clearAuth()` → 跳转 `/login?reason=...` | **已实现** |
| Redis/local token 边界 | `sa-token-redis-template` 依赖 + `SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE` 配置 | **已实现** |

**成功证据**：登录后直调 `/knowledge-base` 不再误报过期 ✅；Redis/local token 边界清楚 ✅

### D2: RAG 冒烟标准化 — ✅ 已完成

| 设计内容 | 代码证据 | 状态 |
|---|---|---|
| 知识库创建/上传/分块 | `SeahorseKnowledgeBaseController` + `SeahorseKnowledgeDocumentController` 全 CRUD | **已实现** |
| 向量化 | `KernelIngestionEngine` 节点链 + embed/vector-index 节点 | **已实现** |
| SSE 问答 | `SeahorseChatController` `GET /rag/v3/chat` SSE 端点 | **已实现** |
| Trace 检查 | `SeahorseRagTraceController` `GET /rag/traces/runs` + `GET /rag/traces/runs/{traceId}/nodes` | **已实现** |
| `t_knowledge_chunk` 表 | `seahorse_init.sql` 定义完整，含 vector_id、keyword_indexed 等字段 | **已实现** |

**成功证据**：`t_knowledge_chunk` 表结构完整 ✅；`/rag/traces/runs` 端点已实现 ✅

### D3: 记忆画像 E2E — ✅ 已完成

| 设计内容 | 代码证据 | 状态 |
|---|---|---|
| `/memories/readiness` | `SeahorseMemoryController` `@GetMapping("/memories/readiness")` | **已实现** |
| `/memories/profile-facts` | `SeahorseUserMemoryController` profile-facts 端点 | **已实现** |
| `/memories/maintenance/run` | `SeahorseMemoryMaintenanceController` maintenance 端点 | **已实现** |
| `/memories/health` | `SeahorseMemoryController` health 端点 | **已实现** |
| `t_user_profile_fact` 表 | `seahorse_init.sql` 含 slot_key、confidence、source_memory_id 等 | **已实现** |
| 聚合缓冲 + 捕获 | `MemoryCaptureStage` + aggregation buffer（local/Redis 双模式） | **已实现** |

**成功证据**：`/memories/readiness` 必需链路有证据 ✅；`t_user_profile_fact` 表已定义 ✅

### D4: 文档事实源收敛 — ✅ 已完成

| 设计内容 | 代码证据 | 状态 |
|---|---|---|
| 旧 RepoWiki 归档声明 | `docs/zh/content/_ARCHIVED_NOTICE.md` 已创建 | **已实现** |
| 架构基线文档 | `docs/architecture/current-code-architecture.md` 225 行 | **已实现** |
| 事实源优先级 | 架构文档 Section 8 明确 6 级优先级 | **已实现** |
| 旧端点引用清理 | `docs/deployment/local-embedding-model-guide.md` 已改为仅使用 `/rag/traces/runs` 或 `t_rag_trace_*` 验证 retrieval 节点 | **已实现** |

**成功证据**：`rg -n "/admin/traces" docs/deployment/local-embedding-model-guide.md` 无匹配；本地 Embedding 验证文档不再把前端页面路径混作 API 验证端点。

### D5: Embedding 配置清晰化 — ✅ 已完成

| 设计内容 | 代码证据 | 状态 |
|---|---|---|
| 全量默认 `nomic-embed-text` | `docker-compose.full.yml` `SEAHORSE_AGENT_ADAPTERS_AI_EMBEDDING_MODEL:-nomic-embed-text` | **已实现** |
| 向量维度由模型解析 | `SeahorseAgentVectorAdapterAutoConfiguration` 自动推导维度；已知模型映射表 | **已实现** |
| 切换模型警告 | `.env.full.example` + `TROUBLESHOOTING_GUIDE.md` Section 4 明确说明 | **已实现** |
| 口径一致 | 文档/compose/代码均使用 `nomic-embed-text` / 768 维 | **已实现** |

**成功证据**：文档、compose 和排错指南口径一致 ✅

## 4. 中期设计（1-3 个月）历史完成情况

### M1: RAG 质量评测与策略治理 — 85% 完成

#### 基础设施层

| 组件 | 代码证据 | 状态 |
|---|---|---|
| `SeahorseRetrievalEvaluationController` | 存在，含 evaluate/compare/run 端点 | **已实现** |
| `SeahorseRetrievalEvaluationDatasetController` | 存在，含 CRUD + evaluate 端点 | **已实现** |
| `SeahorseRetrievalStrategyTemplateController` | 存在 | **已实现** |
| `KernelRetrievalEvaluationService` | 实现 `RetrievalEvaluationInboundPort`，计算 recall@k/precision@k/MRR/NDCG/空召回率 | **已实现** |
| `KernelRetrievalEvaluationDatasetService` | 实现 `RetrievalEvaluationDatasetInboundPort`，含 upsert/delete/run | **已实现** |
| `t_retrieval_evaluation_dataset` | `seahorse_init.sql` 已定义 | **已实现** |
| `t_retrieval_evaluation_run` | `seahorse_init.sql` 已定义 | **已实现** |
| `t_retrieval_evaluation_comparison` | `seahorse_init.sql` 已定义 | **已实现** |
| `t_retrieval_strategy_template` | `seahorse_init.sql` 已定义 | **已实现** |
| 前端 `RagEvaluationPage` | 存在（4 个页面：列表/详情/策略/版本对比） | **已实现** |

#### 实施切片评估

| 切片 | 描述 | 状态 | 说明 |
|---|---|---|---|
| 1. 数据集治理 | 导入/导出/启停/标签/case 校验 | **已完成** | DatasetController 含 CRUD + enable/disable |
| 2. 运行稳定性 | 幂等 key/超时/最大 case 数 | **已完成** | `KernelRetrievalEvaluationService` 含 evaluable filter |
| 3. 对比报告 | 前端 baseline/candidate 指标差异 | **已完成** | `VersionQualityComparePage` + comparison 表 |
| 4. 策略推广 | 显式按钮 + audit event | **已完成并有真实验证** | `scripts/e2e-rag-strategy-promotion-smoke.ps1` 已在 full Docker 中验证策略对比、页面 promotion、`t_retrieval_strategy_template` 推荐模板和 `RETRIEVAL_STRATEGY_PROMOTED` audit 事件 |
| 5. CI 冒烟 | 内置 dataset + Docker full 模式验证 | **已完成并有真实验证** | RAG evaluation strict smoke 已创建 KB/doc/chunks/dataset，评测 2/2 cases，并要求非零 recall |

### M2: 入库治理与可恢复 Pipeline — 100% 完成

#### 基础设施层

| 组件 | 代码证据 | 状态 |
|---|---|---|
| `SeahorseIngestionPipelineController` | 存在，含 CRUD 端点 | **已实现** |
| `SeahorseIngestionTaskController` | 存在，含 create/upload/retry/rollback 端点 | **已实现** |
| `KernelIngestionPipelineService` | 实现 `IngestionPipelineInboundPort` | **已实现** |
| `KernelIngestionTaskService` | 实现 `IngestionTaskInboundPort`，含 execute/upload/retry/rollback | **已实现** |
| `KernelIngestionEngine` | 节点链执行，支持 startNode 恢复 | **已实现** |
| `IngestionTaskCompensationPort` | 补偿端口接口，含 rollback targets | **已实现** |
| `t_ingestion_pipeline` + `_node` | `seahorse_init.sql` 已定义 | **已实现** |
| `t_ingestion_task` + `_node` | `seahorse_init.sql` 已定义，含 error_code/retry_count 字段 | **已实现** |
| 前端 `IngestionPage` | 存在 | **已实现** |

#### 实施切片评估

| 切片 | 描述 | 状态 | 说明 |
|---|---|---|---|
| 1. Pipeline 版本化 | version/snapshot + 任务引用 | **已完成** | `V30__ingestion_pipeline_versions_and_task_snapshots.sql` 添加 version + pipeline_snapshot_json |
| 2. 节点日志增强 | 输入/输出摘要/耗时/重试/错误分类 | **已完成** | `V29__ingestion_task_node_governance_fields.sql` 添加 input_summary/output_summary/error_code/retry_count/downstream_impact |
| 3. 重放机制 | task retry API + 从失败节点重放 | **已完成** | `SeahorseIngestionTaskController` retry 端点 + `KernelIngestionTaskService.retry()` 支持 fromNodeId |
| 4. 隔离队列 | metadata review/quarantine 关联 | **已完成** | 入库失败状态 `failed`/`quarantined` + metadata governance 页面 |
| 5. 回滚策略 | document/chunk/vector/index 补偿 | **已完成** | `IngestionTaskCompensationPort` + rollback 测试覆盖（含 kbId/docId/collectionName） |

### M3: 记忆质量与用户画像可信度治理 — 100% 完成

#### 基础设施层

| 组件 | 代码证据 | 状态 |
|---|---|---|
| `SeahorseMemoryReviewController` | 存在，含 page/pending-summary/feedback-samples 端点 | **已实现** |
| `SeahorseMemoryRecallEvaluationController` | 存在 | **已实现** |
| `SeahorseMemoryTraceController` | 存在，`GET /memories/traces` 端点 | **已实现** |
| `SeahorseUserMemoryController` | 存在 | **已实现** |
| `KernelMemoryReviewService` | 实现 `MemoryReviewInboundPort` | **已实现** |
| `MemoryRecallEvaluationService` | 实现 `MemoryRecallEvaluationInboundPort`，含 golden cases + recall/precision/MRR | **已实现** |
| `MemoryRecallGoldenHarnessInboundPort` | 存在，runProfile 方法 | **已实现** |
| `t_memory_review_candidate` | `seahorse_init.sql` 已定义 | **已实现** |
| `t_memory_review_feedback_sample` | `seahorse_init.sql` 已定义 | **已实现** |
| `t_memory_conflict_log` | `seahorse_init.sql` 已定义 | **已实现** |
| `t_memory_quality_snapshot` | `seahorse_init.sql` 已定义 | **已实现** |
| `t_memory_correction_ledger` | `seahorse_init.sql` 已定义 | **已实现** |
| `t_user_profile_fact` | `seahorse_init.sql` 已定义 | **已实现** |
| 前端 `MemoryGovernancePage` | 存在 | **已实现** |
| 前端 `MemoryCenterPage` | 存在 | **已实现** |

#### 实施切片评估

| 切片 | 描述 | 状态 | 说明 |
|---|---|---|---|
| 1. 画像详情页 | 来源/置信度/冲突/版本/引用次数 | **已完成并有真实验证** | `scripts/e2e-memory-profile-facts-smoke.ps1` 已在 full Docker 中验证 `t_user_profile_fact.source_ids`、API `sourceIds`、置信度、版本、引用次数和治理页展开详情展示 |
| 2. 冲突工作台 | conflict_log + 候选 + 画像 + ledger 关联视图 | **已完成并有真实验证** | `scripts/e2e-memory-governance-smoke.ps1` 已在 full Docker 前端/API/PostgreSQL 中验证 PENDING 冲突展示、页面 resolve 和 `t_memory_conflict_log` 变为 RESOLVED；`scripts/e2e-interactive-memory-conflict-smoke.ps1` 已验证 `/chat` 内冲突卡片交互 resolve |
| 3. 召回评测 | golden cases 覆盖 | **已完成** | `MemoryRecallEvaluationService` + `MemoryRecallGoldenCase` + `MemoryRecallGoldenHarnessInboundPort` |
| 4. 低价值清理 | quality snapshot + accessCount 清理建议 | **已完成** | `t_memory_quality_snapshot` 表 + maintenance run 产出快照 |
| 5. 隐私闭环 | 记忆删除 → profile fact + 索引同步失效 | **已完成** | `KernelMemoryReviewService` 含 forget 操作 + 级联失效逻辑 |

### M4: Agent 生产准备与发布治理 — 100% 完成

#### 基础设施层

| 组件 | 代码证据 | 状态 |
|---|---|---|
| 9 个 Controller | 全部存在（Definition/Factory/Run/Rollout/Eval/Approval/ProductionGate/CostUsage/AuditEvent） | **已实现** |
| 11 张数据库表 | 全部在 `seahorse_init.sql` 中定义，含 CHECK 约束和索引 | **已实现** |
| 7 个 Kernel InboundPort | 全部存在 + 额外发现 RolloutCostSummary/RunCostSummary/Factory/RunInboundPort | **已实现** |
| 11 个前端页面 | 全部存在（AgentList/Create/Detail/Editor/Rollout/Eval + Approval/RunList/Cost/Audit/Sandbox） | **已实现** |

#### 实施切片评估

| 切片 | 描述 | 状态 | 说明 |
|---|---|---|---|
| 1. 发布前检查 | validate + publish-check + production-gate 组合报告 | **已完成** | `POST /api/agents/{id}/validate` + `POST /api/agents/{id}/production-gate` + `sa_production_gate_report` + `sa_agent_publish_check` |
| 2. Agent Eval | eval summary 绑定每个可发布版本 | **已完成** | `SeahorseAgentEvalController` CRUD + `sa_agent_eval_summary` 含 5 种 eval_type + 4 种 status |
| 3. 灰度面板 | rollout 比例/错误率/成本/回滚按钮 | **已完成并有真实验证** | `AgentRolloutPage` 含暂停/全量发布/回滚 + `AgentVersionRollout` 6 种状态 + cost-summary 端点；`scripts/e2e-agent-rollout-smoke.ps1` 已在 full Docker 中验证缺 gate 失败、页面创建 Canary、全量发布、DB 状态和 audit 事件 |
| 4. 成本治理 | per-run token/tool/model 成本汇总 | **已完成** | `GET /agent-runs/{id}/cost-summary` + `sa_cost_usage_record` 含 4 种 source + `sa_quota_policy` 6 种 scope |
| 5. 审计闭环 | publish/pause/upgrade/rollback/approval 写入 audit | **已完成** | `sa_audit_event` 含 19 种事件类型 + `AuditEventPage` 搜索/过滤/详情 |

### M5: starter-all 和完整部署验收 — 100% 完成

#### 基础设施层

| 组件 | 代码证据 | 状态 |
|---|---|---|
| starter-all pom.xml | 包含全部 14 个官方适配器依赖 | **已实现** |
| Auto-configuration | 16 个子配置，全面使用 `@ConditionalOnClass` + `@ConditionalOnProperty` + `@ConditionalOnBean` | **已实现** |
| SRE Health Check | `SeahorseAgentSreAdapterHealthAutoConfiguration` 覆盖 5 个维度 | **已实现** |
| 冒烟测试脚本 | 8 个 E2E 脚本（Shell + PowerShell），覆盖认证/RAG/记忆/Agent/Metrics | **已实现** |
| 集成测试类 | 6000+ 行测试代码（Kernel 2343 行 + Web 2265 行 + Adapter 562 行 + Agent 455 行 + Noop 275 行） | **已实现** |
| `TROUBLESHOOTING_GUIDE.md` | 319 行，8 大章节覆盖认证/Docker/DB/向量/RAG/记忆/路径/诊断 | **已实现** |

#### 实施切片评估

| 切片 | 描述 | 状态 | 说明 |
|---|---|---|---|
| 1. 适配器验证矩阵 | 配置项/依赖容器/健康检查/最小业务动作 | **已完成** | 8 个适配器类型全部具备 Bean 创建 + 条件装配 + SRE Health |
| 2. Classpath/Bean 条件测试 | starter-core 和 starter-all | **已完成** | `NativeAdapterAutoConfigTests` 562 行 + `KernelAutoConfigTests` 2343 行 |
| 3. Full Compose 冒烟 | login/ingestion/RAG/memory/agent run/metrics | **已完成** | `e2e-compose-suite.sh` + `e2e-full-test.sh` + `e2e-backend-smoke.ps1` 650 行 |
| 4. 故障项文档化 | 失败项写入 TROUBLESHOOTING_GUIDE | **已完成** | 319 行 8 大章节，含具体命令和迁移脚本引用 |

## 5. 规划与实现的差异分析（历史记录）

### 4.1 超出规划的实现

以下能力在路线图中未列为中期目标，但已在代码中完成：

| 额外实现 | 代码证据 |
|---|---|
| 产品模式封装（demo/rag/enterprise） | `ProductMode` 枚举 + `AdvancedFeatureGate` + `ReadinessController` |
| 任务 Facade API | `SeahorseTaskController` + `TaskOrchestrationService` |
| Workspace 工作台 | `WorkspaceHomePage` + `TaskRunPage` + `TaskListPage` |
| Readiness 诊断系统 | `ReadinessController` 13 项检查 + `ReadinessStatusBar` |
| 插件管理系统 | `SeahorsePluginController` + `ExtensionRegistry` + `PluginManagementPage` |
| Marketplace 市场 | `MarketplacePage` + `MarketplaceReviewPage` |

### 4.2 规划与实现的差异

| 差异项 | 规划描述 | 实际实现 | 原因分析 |
|---|---|---|---|
| M1 策略推广审计 | 推广动作写入 audit event | 已有 full Docker 页面/API/DB/audit 真实验证 | `scripts/e2e-rag-strategy-promotion-smoke.ps1` |
| M1 CI 冒烟 | 内置 dataset + Docker full 最小评测 | 已有严格 RAG evaluation smoke 运行证据 | RAG eval smoke 创建真实 dataset 并要求非零 recall |
| M3 画像详情页 | 来源对话/记忆/置信度/冲突/版本/引用次数 | 已有 full Docker DB/API/页面真实验证 | `scripts/e2e-memory-profile-facts-smoke.ps1` |
| M3 冲突工作台 | 统一处理视图 | 已有 full Docker 页面/API/PostgreSQL 真实验证 | `scripts/e2e-memory-governance-smoke.ps1` |

### 4.3 文档中描述的"现有基座"验证

路线图文档中 M1-M5 引用的所有"现有基座"组件均已验证存在：

- M1 基座 7 项：7/7 存在 ✅
- M2 基座 10 项：10/10 存在 ✅
- M3 基座 11 项：11/11 存在 ✅
- M4 基座 20 项：20/20 存在 ✅
- M5 基座 7 个适配器：7/7 已配置 ✅

## 6. 成功证据标准达成评估（历史记录）

### 近期设计

| 方向 | 成功证据标准 | 代码层面 | 运行层面 |
|---|---|---|---|
| D1 登录稳定性 | 登录后直调不再误报过期 | ✅ 代码完整 | ✅ E2E 验证通过 |
| D2 RAG 冒烟 | `t_knowledge_chunk` 有数据 + Trace 有 retrieval 节点 | ✅ 表/API 完整 | ✅ full Docker backend smoke 已产生 document chunk、RAG SSE 和 retrieval trace evidence |
| D3 记忆画像 | readiness 有证据 + profile_fact 有 active 事实 | ✅ 端点完整 | ✅ full Docker backend smoke 已产生 readiness、memory chat、maintenance 和 active profile facts |
| D4 文档收敛 | stale reference 扫描无旧引用 | ✅ 指定旧引用已清理 | — |
| D5 Embedding | 文档/compose/排错口径一致 | ✅ 口径一致 | — |

### 中期设计

| 模块 | 成功证据标准 | 代码层面 | 运行层面 |
|---|---|---|---|
| M1 RAG 评测 | evaluation API 产出可对比报告 | ✅ API/表/前端完整 | ✅ RAG evaluation strict smoke 与策略 promotion 页面/API/DB/audit smoke 已覆盖 |
| M2 入库治理 | 任务节点可追踪 + 失败可重放 | ✅ retry/rollback API 完整 | ✅ 单元测试覆盖 |
| M3 记忆治理 | conflicts/quality-snapshots 可解释 | ✅ 表和 API 完整 | ✅ MemoryGovernancePage 已用 full Docker 页面/API/PostgreSQL 验证冲突展示、resolve 和质量快照；聊天内交互式冲突卡片已用 full Docker Playwright/API/DB/trace/audit 验证 |
| M4 Agent 生产 | run 可追踪步骤/审批/产物/成本 | ✅ 全部 9 Controller + 11 表 | ✅ Agent rollout 页面/API/DB/audit 真实烟测已覆盖灰度发布主链路 |
| M5 starter-all | full compose smoke suite 通过 | ✅ 脚本/测试/文档完整 | ✅ full Docker backend/page/RAG eval/S3/Pulsar 真实验证已补齐当前 smoke baseline |

## 7. 历史总结

### 架构演进一致性评估

**整体一致性程度：高（91%）**

1. **近期设计**（5 个方向）：5 个方向的代码与文档基线已达标；真实运行证据仍按路线图门禁持续补齐。核心闭环（登录、RAG、记忆画像、Embedding）的代码基础设施已全部就位。

2. **中期设计**（24 个实施切片）：24 个已有完成或真实验证证据。代码层面的 Controller、Service、Port、数据库表、前端页面已经覆盖路线图规划的所有"现有基座"。

3. **超出规划**：代码库还额外实现了产品模式封装、任务 Facade API、Workspace 工作台、Readiness 诊断系统、插件管理、Marketplace 等路线图远期才提到的能力。

### 剩余工作优先级

| 优先级 | 工作项 | 所属模块 | 工作量 |
|---|---|---|---|
| — | 当前近期已开发能力无剩余 P0/P1/P2 真实验证项 | 近期稳定基线 | — |
### 结论

架构路线图中近期和中期设计的**代码基础设施已基本全部就位**。当前状态是"近期已开发能力的真实运行验证已补齐到当前基线"——Agent 控制面、RAG 评测/策略推广、记忆画像来源追溯、记忆冲突治理、交互式记忆冲突聊天闭环、S3 和 Pulsar 等近期已开发能力已经有 full Docker 证据；后续主要转向尚未产品化或仍需生产联调的 AgentScope/OTEL、统一 GateResult/Tool Gateway 等中长期能力。

### 2026-06-25 Runtime Evidence Update

M5 full-compose evidence now includes S3 adapter switching proof: `scripts/e2e-s3-storage-smoke.ps1` passed against real Docker backend/PostgreSQL/MinIO with upload, DB `s3://` storage ref, MinIO object stat, API list/delete, DB soft delete, and MinIO object removal.

M5/P2 full-compose evidence now also includes Pulsar consume-loop proof: `scripts/e2e-pulsar-mq-smoke.ps1` passed against the main running Docker backend and real Pulsar broker. It verified `SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar`, active topic subscription `seahorse-document-chunk-consumer`, knowledge-document chunk API trigger, PostgreSQL document success and marker chunk materialization, Pulsar `msgIn/msgOut` counter advance, zero backlog/unacked messages, and backend log completion for the same document id.

M3 profile detail/source-tracing evidence now includes `scripts/e2e-memory-profile-facts-smoke.ps1`: it seeds a real `t_user_profile_fact` row with `source_ids`, verifies `/api/memories/profile-facts` returns `sourceIds`, opens the deployed `/admin/memory-governance` operations/profile-facts view, expands the row, and verifies the source id is visible in the browser.

### 2026-06-25 Completion Audit Evidence Update

The real-verification work now has an explicit completion audit in `docs/aegis/work/2026-06-23-roadmap-real-verification/92-completion-audit.md`. Fresh full-Docker reruns covered backend smoke, page smoke, role cards, message tree, run profiles, run experiments, AgentScope, temporary A2A/Nacos live path, MCP stdio/HTTP, OpenAPI connector, governance page/error states, ingestion, RAG evaluation/strategy promotion, memory governance/profile source tracing, agent rollout, S3 switching, and Pulsar consume loop. Added `scripts/e2e-openapi-connector-smoke.ps1` as the repeatable OpenAPI connector smoke, and stabilized governance/memory page smokes for repeat runs.

### 2026-07-01 Interactive Memory Conflict Evidence Update

Interactive memory conflict handling now has full Docker chat-flow evidence through `scripts/e2e-interactive-memory-conflict-smoke.ps1 -BaseUrl http://127.0.0.1`. The smoke seeds two active short-term memories and a `PENDING` conflict, opens `/chat`, receives the `memory.conflict.prompt` card, resolves `keep_a` through `POST /api/memories/conflicts/interactive-resolve`, and verifies PostgreSQL/trace/audit results. Fresh run evidence: `codxic-conflict-1782865959770|RESOLVED|keep_a|interactive:2001523723396308993`, memory state `codxicA1782865959770|0` and `codxicB1782865959770|1`, trace `SUCCESS|chat-ui|interactive:2001523723396308993|keep_a`, audit `MEMORY_CONFLICT_RESOLVED|interactive:2001523723396308993|codxic-conflict-1782865959770|chat-ui|keep_a`, screenshot `output/playwright/artifacts/interactive-memory-conflict-CODX_INTERACTIVE_MEMORY_CONFLICT_1782865955904.png`.

### 2026-07-01 Agent Control Plane P0 Gate Evidence Update

The original P0 "已合入 Agent 控制面真实 test case" gate has fresh full-Docker evidence and is archived out of the roadmap planning body. Fresh reruns covered the control-plane normal paths, regression/error paths, and UI state paths:

| Scope | Fresh evidence |
|---|---|
| Message tree / branch cursor | `scripts/e2e-message-tree-branch-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 7/7, including fork, branch switch, cursor reload, and PostgreSQL branch state checks. |
| Role card chat context | `scripts/e2e-role-card-chat-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 5/5, including role card application to chat and `t_run_context_snapshot` verification. |
| Run profile inheritance | `scripts/e2e-run-profile-inheritance-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 7/7, including conversation profile binding and snapshot `runProfileId` / role card / tool allowlist checks. |
| Run experiment | `scripts/e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 10/10, including trial execution, scoring, branch fork, Markdown report export, DB state, snapshots, and output messages. |
| AgentScope / A2A boundary | `scripts/e2e-agentscope-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 10/10. The script now verifies both the AgentScope run profile path and the kernel fallback path, and handles the current A2A-enabled endpoint boundary. |
| MCP stdio | `scripts/e2e-mcp-stdio-smoke.ps1 -BaseUrl http://127.0.0.1:9093` passed 8/8 against a temporary MCP-enabled backend, including stdio server discovery, echo call, MCP tool catalog entry, restart, and stderr tail. |
| MCP HTTP | `scripts/e2e-mcp-http-smoke.ps1 -BaseUrl http://127.0.0.1:9096` passed 12/12 against temporary HTTP MCP server/backend containers, including direct JSON-RPC echo, catalog entry, restart, failed-server containment, and stderr tail. |
| OpenAPI connector / tool catalog | `scripts/e2e-openapi-connector-smoke.ps1 -BaseUrl http://127.0.0.1` passed with marker `CODX_OPENAPI_1782869430449`, imported 2 operations, enabled the low-risk GET tool, verified high-risk DELETE stayed blocked with HTTP 409, checked DB row state, and captured a Playwright screenshot. |
| Governance API error states | `scripts/e2e-governance-error-states-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 9/9, covering structured bad login, admin data envelopes, empty search, not-found, normal-user permission errors, and MCP-disabled service-unavailable envelope. The script now idempotently seeds/fixes `demo_user_001` in the full Docker PostgreSQL DB before testing normal-user access. |
| Governance page states | `scripts/e2e-governance-page-states-smoke.ps1 -BaseUrl http://127.0.0.1` passed 5/5 scenarios, covering admin data state, admin empty state, normal-user admin route guard, permission-denied API state, and backend-unavailable API state with screenshots in `output/playwright/artifacts`. |

Script stability fixes in this slice: governance page/error smokes now share `scripts/e2e-governance-user-seed.ps1` so fresh full-Docker databases no longer depend on a pre-existing normal user; AgentScope and MCP stdio smoke drift were stabilized in the preceding commit by aligning with the current A2A-enabled backend and enabling the MCP tool feature in the temporary stdio backend.

### 2026-07-01 Run Experiment Report P1 Evidence Update

The first P1 run experiment report slice is now covered by `GET /api/run-experiments/{id}/report` and the `RunExperimentPage` export action. The Markdown report includes trial export rows, run IDs, output message IDs, score JSON, metric JSON, trace/cost evidence, fork targets, output comparison, and failure notes. Output text is resolved through the conversation branch tree instead of being duplicated into a new table.

Fresh full-Docker evidence: after hot-deploying the rebuilt backend jar to `seahorse-backend`, `scripts/e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 10/10. The run verified the report endpoint and generated `e2e-run-experiment-20260701103802-330537506476589056.md` for experiment `330537506476589056`, including scored marker `smoke-pass`, fork output message `330537506971516928`, per-trial run IDs/output message IDs, output comparison, and fork target content.

That slice left Studio trace deep links, standardized cost records as the authoritative report cost source, and richer productized report templates as follow-up work.

### 2026-07-01 Run Experiment Report P1 Studio Trace/Cost Evidence Update

The second P1 report slice now resolves Studio trace and cost evidence from their authoritative runtime sources. `KernelRunExperimentService` loads `t_run_context_snapshot` by trial `runId` and renders Studio trace Markdown links from `studioTraceUrl`/`traceUrl`, or from `studioUrl`/`tracingUrl` plus `studioTraceId`/`traceId`. Cost evidence now prefers `sa_cost_usage_record` aggregation by `runId`, with the previous metric JSON keys kept only as compatibility fallback.

Fresh full-Docker evidence: after rebuilding `seahorse-agent-bootstrap` and hot-deploying the jar to `seahorse-backend`, `scripts/e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 11/11. The run generated `e2e-run-experiment-20260701223554-330718162879971328.md` for experiment `330718162879971328`, and verified report columns `Studio Trace`/`Cost Source`, Studio URL `http://studio.local/traces/studio-330718162888359936`, authoritative `sa_cost_usage_record`, `cost=0.42`, and `tokens=123`.

That slice left richer productized report templates as follow-up work.

### 2026-07-01 Run Experiment Report P1 Template Evidence Update

The P1 run experiment report template is now productized as `run-experiment-report-v1` while preserving the existing `RunExperimentReport` API and Markdown download contract. The report now includes an `Executive Summary`, recommended trial selection from numeric score fields, an `Evidence Index`, the existing full trial export, output comparison, failure notes, and a `Reproduction Appendix` with experiment/conversation/base leaf IDs and trial run IDs.

Fresh full-Docker evidence: after rebuilding `seahorse-agent-bootstrap` and hot-deploying the jar to `seahorse-backend`, `scripts/e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 11/11. The run generated `e2e-run-experiment-20260701224608-330720737293434880.md` for experiment `330720737293434880`, and verified `Template Version: run-experiment-report-v1`, `Executive Summary`, `Recommended trial`, `Evidence Index`, authoritative Studio trace/cost evidence, `Output Comparison`, `Fork Target`, and `Reproduction Appendix`.

The run experiment report P1 item is complete for the current roadmap acceptance scope and has been removed from the roadmap planning table. Future work in this area should enter a new roadmap item only if it adds a new product surface beyond the current Markdown report export.

### 2026-07-01 MCP Stdio Security P1 Evidence Update

The first P1 MCP stdio security slice now defaults stdio startup to deny unless `seahorse-agent.adapters.mcp.stdio-command-allowlist` explicitly contains the command. Blocked stdio servers are recorded as `FAILED` in `McpServerRuntimeRegistry` with a diagnostic stderr tail such as `stdio command not allowed: pwsh`, and MCP catalog registrations now default to `riskLevel=HIGH` and `requiresApproval=true`.

Fresh Docker evidence: `scripts/e2e-mcp-stdio-smoke.ps1 -BaseUrl http://127.0.0.1:9093 -BackendImage seahorse-agent-backend:mcp-stdio-allowlist -HostPort 9093` passed 8/8 against a temporary MCP-enabled backend image built from the freshly packaged jar. The run verified allowed `node` stdio echo, blocked `pwsh` startup, `/api/mcp/servers` failure diagnostics, MCP tool catalog HIGH/approval flags, refresh/restart, and stderr-tail endpoints.

Remaining roadmap work is narrowed to the product approval entry, unified Tool Gateway execution enforcement, runner isolation/sandbox policy, and deeper audit/desensitization for MCP stderr and tool calls.

### 2026-07-02 MCP Stdio Runner Isolation P1 Evidence Update

The next P1 MCP stdio security slice adds near-term `ProcessBuilder` runner isolation before a separate sandbox runtime exists. Isolation now defaults on, clears the inherited backend environment unless inheritance is explicitly enabled, keeps only allowlisted parent environment keys, still passes explicit per-server `env`, and fail-closes non-empty `workingDir` values unless they are under `stdio-runner-isolation.working-dir-allowlist`.

Fresh Docker evidence: `scripts/e2e-mcp-stdio-smoke.ps1 -BaseUrl http://127.0.0.1:9093 -BackendImage seahorse-agent-backend:mcp-stdio-allowlist -HostPort 9093` passed 11/11 against a temporary MCP-enabled backend image built from the freshly packaged jar. The run verified isolated `node` stdio echo startup, blocked `pwsh` startup, blocked `workingDir=/tmp` with registry status `FAILED` and reason `stdio workingDir not allowlisted`, approval-gated diagnostic calls, approved call audit, and redacted stderr diagnostics that did not expose the raw secret or parent-only environment marker.

Remaining roadmap work is narrowed to full independent/container sandbox runner productionization, product approval/UI hardening, unified Tool Gateway execution enforcement, and any deeper audit/desensitization requirements beyond the current stderr-tail and MCP tool-call coverage.

### 2026-07-02 MCP Diagnostic Approval Entry P1 Evidence Update

The MCP stdio diagnostic approval entry is now productized in the admin UI. `ToolCatalogPage` treats `APPROVAL_REQUIRED` diagnostic test results as a submitted approval rather than a generic failure, surfaces the returned `approvalId`, and links directly to `ApprovalCenterPage` with `?approvalId=...`. `ApprovalCenterPage` now opens that approval drawer directly from the query parameter and handles backend enum statuses such as `PENDING`/`APPROVED` in the list and drawer.

Fresh evidence: focused frontend tests passed 14/14 via `npm run test -- src/pages/admin/tools/ToolCatalogPage.test.tsx src/pages/admin/approvals/ApprovalCenterPage.test.tsx src/services/mcpServerService.test.ts`, and `npm run build` completed successfully. Fresh Docker evidence: `scripts/e2e-mcp-stdio-smoke.ps1 -BaseUrl http://127.0.0.1:9093 -BackendImage seahorse-agent-backend:mcp-stdio-allowlist -HostPort 9093` passed 11/11, including `APPROVAL_REQUIRED`, returned `approvalId`, `GET /api/approvals/{approvalId}` status `PENDING`, approval, approved diagnostic execution, and tool audit.

Remaining roadmap work is narrowed to unified Tool Gateway execution enforcement, full independent/container sandbox runner productionization, and any deeper audit/desensitization requirements beyond the current stderr-tail, approval, and MCP tool-call coverage.

### 2026-07-02 MCP Tool Gateway Enforcement P1 Evidence Update

The MCP server diagnostic execution path now fails closed when `GovernedToolExecutionPort` is unavailable. `McpServerRuntimeRegistry.testServer()` no longer stores or calls a raw `McpToolRegistryPort` executor fallback; ready servers with an echo tool return `TOOL_GATEWAY_UNAVAILABLE` unless the governed Tool Gateway is present. When the governed port is available, diagnostic preflight, approval-required responses, approved execution, and execution failures continue through the same governed invocation request used by the Tool Gateway path.

Fresh evidence: targeted backend tests passed 24/24 via `.\mvnw.cmd -pl seahorse-agent-adapter-mcp-http -am "-Dtest=StdioMcpRunnerPolicyTests,McpHttpAdapterPropertiesBindingTests,McpHttpAutoConfigurationCredentialTests,StdioMcpClientTests,McpServerRuntimeRegistryTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. `.\mvnw.cmd -pl seahorse-agent-bootstrap -am package "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the boot jar successfully, and `.\scripts\e2e-mcp-stdio-smoke.ps1 -BaseUrl http://127.0.0.1:9093 -BackendImage seahorse-agent-backend:mcp-stdio-allowlist -HostPort 9093` passed 11/11. The new registry test covers `TOOL_GATEWAY_UNAVAILABLE`, approval-required diagnostics, approved governed execution, and governed execution failure without calling a raw MCP executor; the Docker smoke verified `APPROVAL_REQUIRED`, approval, approved diagnostic `SUCCESS`, tool audit, tool catalog risk/approval flags, refresh/restart, and stderr-tail diagnostics.

Remaining roadmap work is narrowed to full independent/container sandbox runner productionization, cross-provider Tool Gateway audit/hardening beyond the current AgentLoop and MCP diagnostic coverage, and any deeper audit/desensitization requirements beyond the current stderr-tail, approval, and MCP tool-call coverage.

### 2026-07-02 OpenAPI Tool Gateway P1 Evidence Update

Enabled OpenAPI connector operations now enter the unified Tool Gateway as dynamic `ToolDescriptor`s. `OpenApiAwareToolRegistryPort` preserves built-in/MCP registrations from the existing registry while exposing enabled OpenAPI operations, and `OpenApiToolPortAdapter` executes them through a bounded HTTP adapter that uses the imported connector `baseUrl`, OpenAPI path/query/header parameters, optional JSON request bodies, existing static bearer credential bindings, and recursive response-field redaction.

Fresh evidence: targeted backend tests passed 15/15 via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-openapi,seahorse-agent-adapter-repository-jdbc -am "-Dtest=KernelOpenApiConnectorImportServiceTests,ConnectorAdminOnlyTests,OpenApiToolPortAdapterTests,OpenApiSpecParserAdapterTests,JdbcConnectorRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; Spring auto-configuration tests passed 26/26 via `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentRegistryAutoConfigurationTests,SeahorseAgentChatRunStoreAutoConfigurationTests,BuiltInAgentToolRegistrarTests,McpToolAllowlistRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; and `.\mvnw.cmd -pl seahorse-agent-bootstrap -am package "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the deployable boot jar successfully. Fresh full-Docker evidence: after applying `V45__openapi_connector_base_url.sql` to the local PostgreSQL container and hot-deploying the rebuilt jar to `seahorse-backend`, `.\scripts\e2e-openapi-connector-smoke.ps1 -BaseUrl http://127.0.0.1` passed with marker `CODX_OPENAPI_1782936512482`, connector `conn_a9d32ec26b9958f7`, enabled tool `openapi_db09aa0892cd009f`, `preflightEffect: ALLOW`, high-risk DELETE enable blocked with HTTP 409, and DB row `DELETE:HIGH:DISABLED, GET:LOW:ENABLED` with persisted `base_url=https://api.example.test/CODX_OPENAPI_1782936512482`.

Remaining roadmap work is narrowed to full independent/container sandbox runner productionization and deeper cross-provider Tool Gateway audit/hardening beyond the current AgentLoop, MCP diagnostic, and OpenAPI enabled-operation coverage.

### 2026-07-02 OpenAPI Tool Gateway Invoke/Audit Evidence Update

The governed tool execution API now exposes `POST /api/tools/{toolId}/invoke` alongside the existing preflight endpoint. The endpoint reuses `GovernedToolExecutionPort.invoke`, so external diagnostic/integration callers still pass through the same feature gates, policy/approval path, Tool Gateway execution, output redaction, artifact side effects, and `sa_tool_invocation` audit persistence instead of reaching a raw `ToolPort`.

Fresh evidence: the controller contract test first failed with `404` for `/api/tools/echo/invoke`, then passed 4/4 via `.\mvnw.cmd -pl seahorse-agent-adapter-web -am -Dtest=SeahorseGovernedToolExecutionControllerTests "-Dsurefire.failIfNoSpecifiedTests=false" test`. Frontend endpoint manifest contract tests passed 12/12 via `npm run test -- src/services/serviceEndpointCoverage.test.ts src/services/frontendCapabilityContracts.test.ts`. `node --check scripts\e2e-openapi-connector-smoke.mjs` passed, and `.\mvnw.cmd -pl seahorse-agent-bootstrap -am package "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the boot jar successfully.

Fresh full-Docker evidence: after hot-deploying the rebuilt jar to `seahorse-backend`, `.\scripts\e2e-openapi-connector-smoke.ps1 -BaseUrl http://127.0.0.1` passed with marker `CODX_OPENAPI_1782938292166`, connector `conn_7416535b780c7972`, enabled tool `openapi_abd14e40367d9096`, `preflightEffect: ALLOW`, `invocationSuccess: true`, `invocationStatusCode: 200`, `auditStatus: SUCCEEDED`, high-risk DELETE enable blocked with HTTP 409, and DB row `DELETE:HIGH:DISABLED, GET:LOW:ENABLED` with persisted `base_url=http://host.docker.internal:8884/CODX_OPENAPI_1782938292166`. The smoke starts a temporary real HTTP OpenAPI target, verifies the backend container performs `GET /{marker}/pets?status=available`, verifies sensitive response fields are redacted, and verifies the Tool Gateway audit query returns a `SUCCEEDED` record for the invocation run/tool.

Remaining roadmap work is narrowed to full independent/container sandbox runner productionization and deeper A2A/cross-provider Tool Gateway hardening beyond the current AgentLoop, MCP diagnostic, and OpenAPI invoke/audit coverage.

### 2026-07-02 Sandbox Runtime Lifecycle P1 Evidence Update

Sandbox runtime close now crosses the runtime adapter boundary instead of only mutating kernel state. `SandboxRuntimePort` exposes a default `closeSession(SandboxSession)` hook, `KernelSandboxRuntimeService.close(...)` delegates non-terminal sessions to that hook, persists the returned terminal session, and emits a distinct `SANDBOX_SESSION_CLOSED` audit event. The default unsupported runtime remains fail-closed for execution, while future Docker/Podman/gVisor adapters now have a stable lifecycle point to release containers, processes, and per-session workspaces.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -Dtest=KernelSandboxRuntimeServiceTests "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 8/8. The added tests verify that close delegates to the runtime and persists the returned `CANCELLED` session, terminal sessions do not call runtime close again, and close writes `SANDBOX_SESSION_CLOSED` alongside the existing create audit.

Remaining roadmap work is narrowed to the actual Docker/Podman/gVisor sandbox adapter, resource/runtime profile configuration, sandbox product UI hardening, sandbox-backed agent tools, and deeper A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Execution History P1 Evidence Update

Sandbox execution history is now available through the kernel inbound port and Web API. `SandboxRuntimeInboundPort` exposes `listExecutions(sessionId)`, `KernelSandboxRuntimeService` verifies the session before returning persisted `SandboxExecutionRepositoryPort.listExecutionsBySession(...)` records, and `SeahorseSandboxController` exposes `GET /api/sandbox/sessions/{sessionId}/executions` behind the existing SANDBOX advanced feature gate. The admin Sandbox page now refreshes and displays execution history, uses the backend `execution` response shape for the latest result, and keeps artifacts separate from execution records.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -Dtest=KernelSandboxRuntimeServiceTests "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 9/9, including the new persisted history query. `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SandboxArtifactToAgentArtifactPolicyTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 4/4 and covers the new endpoint plus demo-mode feature gating. `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am -Dtest=SeahorseAgentSandboxAutoConfigurationTests "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 2/2. Frontend endpoint/service contracts passed 12/12 via `npm run test -- src/services/frontendCapabilityContracts.test.ts src/services/serviceEndpointCoverage.test.ts`, and `npm run build` completed successfully with only the existing browserslist/chunk-size warnings.

Remaining roadmap work is narrowed to the actual Docker/Podman/gVisor sandbox adapter, resource/runtime profile configuration, artifact detail/policy preview/session list product hardening, sandbox-backed agent tools, and deeper A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Artifact Scanner P1 Evidence Update

Sandbox artifacts now pass through a kernel-owned scanner before they can become prompt visible. `SandboxArtifactScannerPort` and `SandboxArtifactScanResult` define the scanner boundary, `DefaultSandboxArtifactScannerPort` provides a conservative metadata scanner, and `KernelSandboxRuntimeService` now scans and persists all runtime-returned artifacts while returning only `promptVisible=true` artifacts in the execution response. Scanner failures fail closed by saving artifacts as `BLOCKED`/`SECRET`. `SandboxArtifactScanStatus` now includes `REDACTED`, and prompt visibility is limited to `CLEAN` or `REDACTED` artifacts that are not `SECRET`.

The artifact list endpoint now returns all persisted session artifacts for operations visibility, without exposing object storage URIs, and includes `promptVisible` in the response. The admin Sandbox page displays scan status, sensitivity, and prompt visibility for each artifact.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=KernelSandboxRuntimeServiceTests,SandboxArtifactTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 11/11, including scanner fail-closed and REDACTED prompt-visible coverage. `.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcSandboxRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 1/1 and verifies REDACTED prompt-visible artifact queries plus all-artifact session listing. `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SandboxArtifactToAgentArtifactPolicyTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 4/4. `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentSandboxAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 2/2 and verifies the default scanner bean is wired into the sandbox runtime service. Frontend endpoint/service contracts passed 12/12 via `npm run test -- src/services/frontendCapabilityContracts.test.ts src/services/serviceEndpointCoverage.test.ts`, and `npm run build` completed successfully with only the existing browserslist/chunk-size warnings.

No full-Docker sandbox artifact E2E was added in this slice because the real Docker/Podman/gVisor runtime adapter is still intentionally absent; the default runtime remains fail-closed and cannot produce real sandbox artifacts. The remaining roadmap work is narrowed to the actual container runtime adapter, resource/runtime profile configuration, content-level MIME/virus/PII scanning, artifact detail/download/policy preview/session list hardening, sandbox-backed agent tools, and deeper A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Container Runtime Adapter P1 Evidence Update

The first container-backed sandbox runtime adapter is now available in `seahorse-agent-adapter-sandbox-container`. It is opt-in through `seahorse-agent.adapters.sandbox.runtime=container`, so the default `SandboxRuntimePort.unsupported()` fail-closed behavior remains unchanged. When enabled, the adapter supports `CODE_INTERPRETER` by writing the input to a per-session `main.py`, running Docker or Podman CLI with `--rm`, `--network none`, memory/CPU/pids limits, bounded stdout/stderr previews, and a per-session workspace bind-mounted at `/workspace`. Non-`CODE_INTERPRETER` runtime types still fail closed with `RUNTIME_UNSUPPORTED`, and close deletes the session workspace.

The starter-all matrix and bootstrap module now include the adapter without enabling it by default. This gives local and deployment configurations a real Python container execution path while keeping broader production work explicit.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests,ContainerSandboxAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 8/8. With `SEAHORSE_SANDBOX_CONTAINER_E2E=true`, `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterDockerSmokeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 1/1 against the local Docker daemon. The starter/kernel aggregation check passed 19/19 via `.\mvnw.cmd -pl seahorse-agent-spring-boot-starter-all,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-kernel -am "-Dtest=SeahorseAgentStarterAllSmokeTests,SeahorseAgentSandboxAutoConfigurationTests,KernelSandboxRuntimeServiceTests,SandboxArtifactTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`.

Remaining roadmap work is narrowed to full-compose backend container Docker/Podman socket/tooling enablement, persisted runtime profiles and quotas, sandbox-backed agent tools, artifact generation/storage from real executions, content-level scanning, and stronger runtime isolation such as gVisor or Firecracker.

### 2026-07-02 Sandbox Python Tool P1 Evidence Update

The first sandbox-backed Agent tool is now available as `sandbox_python`. The tool is registered as a normal built-in `DescribedToolPort`, enters the existing Tool Gateway policy/audit/redaction path, and uses `SandboxRuntimeInboundPort` to create a `CODE_INTERPRETER` session, execute Python input, and close the session. The default sandbox runtime still fails closed unless a real runtime such as the opt-in container adapter is configured.

`LocalToolGatewayPort` now supports request-aware tools through `ToolInvocationRequestAwarePort`, so tools that need tenant/run/user context can receive the full `ToolInvocationRequest` without bypassing the legacy `ToolPort` contract. `sandbox_python` is cataloged as `HIGH`, `EXECUTE`, and `SANDBOX`, and can be disabled with `seahorse-agent.chat.agent.tools.sandbox.enabled=false`.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxPythonToolPortAdapterTests,LocalToolGatewayPortAuditTests,BuiltInAgentToolRegistrarTests,SandboxPythonToolAutoConfigurationTests,ContainerSandboxRuntimeAdapterTests,ContainerSandboxRuntimeAdapterDockerSmokeTest,SandboxPythonToolContainerDockerSmokeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` completed with reactor `BUILD SUCCESS`; the focused kernel tests passed 16/16 and the focused autoconfigure tests passed 3/3. With `SEAHORSE_SANDBOX_CONTAINER_E2E=true`, `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterDockerSmokeTest,SandboxPythonToolContainerDockerSmokeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 2/2 against the local Docker daemon, including the full `SandboxPythonToolPortAdapter -> KernelSandboxRuntimeService -> ContainerSandboxRuntimeAdapter` execution path.

Remaining roadmap work is narrowed to full-compose backend container Docker/Podman socket/tooling enablement, persisted runtime profiles and quotas, broader sandbox-backed tools such as browser automation and file conversion, artifact collection/storage from real executions, content-level scanning, and stronger runtime isolation such as gVisor or Firecracker.

### 2026-07-02 Sandbox Full-Compose Host-Socket P1 Evidence Update

The full-compose backend can now opt into the container sandbox runtime from inside the backend container. `Dockerfile.backend` includes the Docker CLI in the runtime image, and `docker-compose.sandbox.yml` mounts `/var/run/docker.sock` plus a daemon-visible sandbox workspace source. `ContainerSandboxRuntimeAdapter` now separates the backend-visible workspace root from `workspaceMountSourceRoot`, so code is written inside the backend container while nested Docker bind mounts use a host path that the Docker daemon can resolve. The bootstrap application imports the container adapter auto-configuration, and kernel auto-configuration now orders the sandbox registry before agent tool registration so `sandbox_python` is cataloged when `SandboxRuntimeInboundPort` is available.

Fresh evidence: the auto-configuration regression test first failed because `SandboxPythonToolPortAdapter` was missing after `SandboxRuntimeInboundPort` existed, then passed 3/3 via `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SandboxPythonToolAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Compose overlay validation passed with `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`, and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` recreated a healthy `seahorse-backend`. Fresh full-Docker Tool Gateway evidence: `.\scripts\e2e-sandbox-python-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-full-compose-sandbox-smoke` passed 3/3; `/api/tools?provider=BUILTIN` included `sandbox_python`, and the invocation returned `executionStatus=SUCCEEDED` with `stdout=seahorse-full-compose-sandbox-smoke`.

Remaining roadmap work is narrowed to persisted runtime profiles and quotas, broader sandbox-backed tools such as browser automation and file conversion, sandbox artifact object storage/download governance, content-level scanning, stronger runtime isolation such as gVisor or Firecracker, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Container Artifact Collection P1 Evidence Update

Container-backed sandbox executions now collect real files created inside the per-session workspace after successful container runs. `ContainerSandboxRuntimeAdapter` returns `SandboxArtifact` metadata for regular workspace files, excludes the generated `main.py` entry script, avoids symlink-following discovery, assigns deterministic media types for common text/JSON/image/PDF outputs, and leaves scanning/prompt-visibility decisions to the existing kernel-owned scanner by returning `PENDING`/`INTERNAL` `file://` artifact references.

Fresh evidence: focused adapter tests passed 10/10 via `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests,ContainerSandboxAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`, including artifact discovery, `main.py` exclusion, media type classification, and unchanged fail-closed paths. With `SEAHORSE_SANDBOX_CONTAINER_E2E=true`, `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterDockerSmokeTest,SandboxPythonToolContainerDockerSmokeTest" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 2/2 against the local Docker daemon and verified both direct runtime artifacts and `SandboxPythonToolPortAdapter -> KernelSandboxRuntimeService -> ContainerSandboxRuntimeAdapter` prompt-visible artifact output. Compose overlay validation passed after setting `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted a healthy backend; and `.\scripts\e2e-sandbox-python-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-full-compose-artifact-smoke` passed 3/3 with returned `artifactId=sandbox_artifact_container_*`, `mediaType=text/plain`, `scanStatus=CLEAN`, `sensitivity=INTERNAL`, and `promptVisible=true`.

Remaining roadmap work is narrowed to persisted runtime profiles and quotas, broader sandbox-backed tools such as browser automation and file conversion, sandbox artifact object storage/download governance beyond local `file://` references, content-level MIME/virus/PII scanning, stronger runtime isolation such as gVisor or Firecracker, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Tool Gateway Quota Preflight P1 Evidence Update

Tool Gateway policy evaluation now includes a quota preflight when `QuotaManagementInboundPort` is available. `CatalogBackedToolPolicyPort` evaluates each otherwise admissible tool call as `QuotaUsage(tokens=0, calls=1, cost=0)` before reaching the real `ToolPort`; quota `DENY` maps to `QUOTA_HARD_LIMIT_EXCEEDED`, quota `REQUIRE_APPROVAL` maps to `QUOTA_APPROVAL_REQUIRED`, and `WARN`/`ALLOW` continue through the existing catalog approval path. No-policy quota results remain advisory in Tool Gateway so high-risk built-ins such as `sandbox_python` are not unexpectedly blocked unless an explicit quota policy matches the run, agent, user, tool, model, or tenant.

Fresh evidence: kernel policy/quota/audit tests passed 32/32 via `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=CatalogBackedToolPolicyPortTests,KernelQuotaDecisionServiceTests,LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Spring auto-configuration tests passed 24/24 via `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentChatRunStoreAutoConfigurationTests,SeahorseAgentRegistryAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`, including quota port wiring into the catalog-backed policy. Compose overlay validation passed after setting `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS`; `.\scripts\e2e-tool-gateway-quota-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-quota-smoke` passed 4/4 and verified a real `RUN` quota policy with `callLimit=0` returns `QUOTA_HARD_LIMIT_EXCEEDED` before sandbox execution; and `.\scripts\e2e-sandbox-python-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-quota-regression-smoke` passed 3/3 to confirm the no-policy success path still executes and returns a prompt-visible clean artifact.

Remaining roadmap work is narrowed to persisted runtime profiles and quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, sandbox artifact object storage/download governance beyond local `file://` references, content-level MIME/virus/PII scanning, stronger runtime isolation such as gVisor or Firecracker, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Artifact Object Storage P1 Evidence Update

Prompt-visible sandbox file artifacts are now copied into the configured `ObjectStoragePort` before sandbox artifact metadata is persisted. `KernelSandboxRuntimeService` still scans runtime-returned artifacts first, persists all artifact metadata, returns only `promptVisible=true` artifacts, and keeps storage URIs out of the Web/API/tool observation surface. When object storage is available, clean or redacted `file://` artifacts are uploaded to the `sandbox-artifacts` bucket and saved with the resulting object URI; scanner-blocked or secret artifacts are not copied into durable object storage. If the copy fails, the artifact fails closed as `BLOCKED`/`SECRET` and is not returned to the prompt. Minimal deployments without an `ObjectStoragePort` keep the previous metadata-only behavior.

Spring auto-configuration now injects the existing optional `ObjectStoragePort` into `KernelSandboxRuntimeService`, and full-compose local storage now points at the mounted `/app/seahorse-agent-storage` volume instead of container-local `/tmp`, so local object references survive backend container recreation.

Fresh evidence: kernel sandbox tests passed 14/14 via `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=KernelSandboxRuntimeServiceTests,SandboxArtifactTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`, covering object-storage copy, scanner-blocked no-copy behavior, and copy-failure fail-closed behavior. Spring sandbox auto-configuration tests passed 2/2 via `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentSandboxAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`, including storage-port injection. Compose overlay validation passed with `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`, and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS`.

Fresh full-Docker evidence: `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-artifact-storage-clean-smoke` passed 6/6. The smoke invoked real `sandbox_python` through Tool Gateway, extracted `artifactId=sandbox_artifact_container_330909449231712256`, verified `sa_sandbox_artifact.object_uri=local://sandbox-artifacts/18d13daa-9172-4b06-a4c5-b7b5893b9131-answer-storage.txt` instead of `file://`, verified `scan_status=CLEAN` and `sensitivity=INTERNAL`, verified `GET /api/sandbox/sessions/{sessionId}/artifacts` does not expose `objectUri`/`storageRef`/raw URI values, and verified the stored object file exists in the backend storage volume with the marker content. Regression evidence: `.\scripts\e2e-sandbox-python-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-artifact-storage-regression-smoke` passed 3/3 and confirmed the normal prompt-visible artifact observation path still works.

Remaining roadmap work is narrowed to persisted runtime profiles and quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, governed sandbox artifact download/detail policy UX on top of the durable object references, content-level MIME/virus/PII scanning, stronger runtime isolation such as gVisor or Firecracker, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Artifact Download Governance P1 Evidence Update

Sandbox artifacts now have a governed download path on top of the durable object references. `SandboxRuntimeInboundPort.downloadArtifact(...)` returns a kernel-owned `SandboxArtifactDownloadDecision`, backed by `SandboxArtifactQueryPort.findArtifactById(...)` and `KernelSandboxRuntimeService` checks that the artifact exists, its session exists, it is prompt-visible, and its storage reference is not a raw `file://` path or direct `http(s)` URL. `SeahorseSandboxController` exposes `GET /api/sandbox/artifacts/{artifactId}/download` behind the existing SANDBOX advanced feature gate and streams bytes through `ObjectStoragePort.openStream(...)`; sandbox artifact list and execution JSON responses still omit `objectUri`/`storageRef`.

Frontend service contracts now include `downloadSandboxArtifact(artifactId)` and the backend endpoint manifest entry for `GET /api/sandbox/artifacts/{}/download`, so UI work can add controls without inventing a private URL shape.

Fresh evidence: focused Java tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,SandboxPythonToolPortAdapterTests,JdbcSandboxRepositoryAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SandboxArtifactToAgentArtifactPolicyTests,AdvancedFeatureControllerGateTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected test classes reported 38 tests, 0 failures, 0 errors. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`. Compose overlay validation passed after setting `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`, and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS`.

Fresh full-Docker evidence: `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-download-governance-smoke` passed 7/7. The smoke invoked real `sandbox_python` through Tool Gateway, verified `sa_sandbox_artifact.object_uri=local://sandbox-artifacts/1c238746-224e-4ca2-875a-353a81880fce-answer-storage.txt`, verified the session artifact API did not expose storage URI fields, downloaded `sandbox_artifact_container_330920053741113344` through the governed endpoint, confirmed the downloaded body contained the marker and no storage metadata, and verified the local object still existed in the backend storage volume.

Remaining roadmap work is narrowed to persisted runtime profiles and quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, artifact detail/policy preview UI on top of governed download decisions, content-level MIME/virus/PII scanning, stronger runtime isolation such as gVisor or Firecracker, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Artifact Detail Policy Preview P1 Evidence Update

Sandbox artifacts now have a governed detail view on top of the existing download decision. `SandboxRuntimeInboundPort.describeArtifact(...)` returns a kernel-owned `SandboxArtifactDetailDecision` with safe artifact metadata, filename/content type, `downloadable`, and `downloadBlockedReason`; `KernelSandboxRuntimeService` shares the same prompt-visibility and unsafe-storage-reference policy between detail preview and download execution. `SeahorseSandboxController` exposes `GET /api/sandbox/artifacts/{artifactId}` behind the existing SANDBOX advanced feature gate and omits `objectUri`/`storageRef` from JSON responses.

Frontend service contracts now include `getSandboxArtifact(artifactId)` and the backend endpoint manifest entry for `GET /api/sandbox/artifacts/{}`. The admin Sandbox page can load artifact details, show the download policy preview, and download prompt-visible artifacts through the governed blob endpoint instead of constructing private storage URLs.

Fresh evidence: focused Java tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,SandboxPythonToolPortAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SandboxArtifactToAgentArtifactPolicyTests,AdvancedFeatureControllerGateTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected test classes reported 40 tests, 0 failures, 0 errors. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing browserslist/chunk-size warnings. Compose overlay validation and backend rebuild passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend`; the in-image Maven build reported `BUILD SUCCESS`.

Fresh full-Docker evidence: `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-detail-policy-smoke` passed 8/8. The smoke invoked real `sandbox_python` through Tool Gateway, verified `sa_sandbox_artifact.object_uri=local://sandbox-artifacts/22ecd6e3-71ae-4222-a78b-6ae0135abb37-answer-storage.txt`, verified the session artifact API did not expose storage URI fields, verified artifact detail returned `promptVisible=true`, `downloadable=true`, `contentType=text/plain`, a filename, and no storage URI fields, downloaded `sandbox_artifact_container_330929682408009728` through the governed endpoint, and verified the local object still existed in the backend storage volume.

Remaining roadmap work is narrowed to persisted runtime profiles and quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, content-level MIME/virus/PII scanning, stronger runtime isolation such as gVisor or Firecracker, session-list product hardening, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Session List Product Hardening P1 Evidence Update

Sandbox sessions are now listable through the kernel inbound boundary and Web API. `SandboxRuntimeInboundPort.listSessions(tenantId, limit)` normalizes request limits in the kernel, `SandboxSessionRepositoryPort.listSessionsByTenant(...)` owns the repository query, and the JDBC adapter returns recent tenant sessions ordered by `updated_at DESC, created_at DESC, session_id DESC` without exposing sandbox artifact storage references. `SeahorseSandboxController` exposes `GET /api/sandbox/sessions?tenantId=...&limit=...` behind the existing SANDBOX advanced feature gate.

The admin Sandbox page now loads recent sessions, refreshes the list after create/execute/close actions, and lets operators select an older session to load its executions and artifacts through the existing governed APIs. Frontend service contracts and the backend endpoint manifest include the new session-list endpoint.

Fresh evidence: focused Java tests passed 42/42 via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,SandboxPythonToolPortAdapterTests,JdbcSandboxRepositoryAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SandboxArtifactToAgentArtifactPolicyTests,AdvancedFeatureControllerGateTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing browserslist/chunk-size warnings. `git diff --check` passed.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-session-list-smoke` passed 9/9. The smoke invoked real `sandbox_python` through Tool Gateway, verified `GET /api/sandbox/sessions?tenantId=default&limit=20` included the created session and did not expose `objectUri`/`storageRef`/raw URI values, verified `sa_sandbox_artifact.object_uri=local://sandbox-artifacts/c36a08e3-aeb7-4534-ba15-1bb712b8a737-answer-storage.txt`, verified artifact list/detail/download governance still omitted storage URI fields, downloaded `sandbox_artifact_container_330936683339776000`, and verified the local object existed in the backend storage volume.

Remaining roadmap work is narrowed to persisted runtime profiles and quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, content-level MIME/virus/PII scanning, stronger runtime isolation such as gVisor or Firecracker, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Artifact Content Scan P1 Evidence Update

The default sandbox artifact scanner now performs bounded content scanning for local `file://` text artifacts before prompt visibility or object-storage copy decisions. `DefaultSandboxArtifactScannerPort` still applies the existing metadata/media-type rules, then reads text/JSON/XML artifacts up to a conservative size limit and fail-closes on unreadable or oversized content. High-confidence private key, assigned secret/token/password, OpenAI-style token, email, and SSN patterns return `BLOCKED` with `SECRET` or `CONFIDENTIAL` sensitivity. `KernelSandboxRuntimeService` therefore keeps content-sensitive artifacts out of Tool Gateway observations, prompt-visible artifact lists, and durable object storage copies.

Fresh evidence: kernel scanner/runtime tests passed 26/26 via `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=DefaultSandboxArtifactScannerPortTests,KernelSandboxRuntimeServiceTests,SandboxArtifactTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Focused sandbox/Web/autoconfigure regression tests passed 46/46 via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=DefaultSandboxArtifactScannerPortTests,KernelSandboxRuntimeServiceTests,SandboxArtifactTests,SandboxPythonToolPortAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SandboxArtifactToAgentArtifactPolicyTests,AdvancedFeatureControllerGateTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. `git diff --check` passed.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-content-scan-smoke` passed 12/12. The smoke invoked real `sandbox_python` through Tool Gateway for both a clean artifact and a content-sensitive artifact, verified the clean artifact copied to `local://sandbox-artifacts/0f93d2f5-187b-4b3f-bee1-2a19bbd73705-answer-storage.txt`, verified the content-sensitive artifact did not appear in the Tool observation, verified the persisted artifact was `BLOCKED`/`SECRET` and not copied to object storage, and verified artifact list/detail APIs exposed only blocked metadata without storage URI or secret content leaks.

Remaining roadmap work is narrowed to persisted runtime profiles and quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, virus/binary/PDF deep scanning plus redaction summaries, stronger runtime isolation such as gVisor or Firecracker, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Session Profile TTL P1 Evidence Update

Sandbox sessions now carry persisted runtime governance metadata. `SandboxSession`, `SandboxSessionCreateCommand`, `SandboxSessionRequest`, `SandboxRuntimePort`, the container adapter, JDBC repository, Web API, and admin Sandbox page now preserve and display `profileId` plus `expiresAt`. The default profiles are `python-small` for `CODE_INTERPRETER`, `browser-readonly` for `BROWSER_AUTOMATION`, `file-conversion` for `FILE_CONVERSION`, and `shell-restricted` for `SHELL`; default TTL is one hour. Startup schema upgrade now repairs existing PostgreSQL volumes by adding/backfilling `sa_sandbox_session.profile_id` and `expires_at`, setting both non-null, and creating `idx_sa_sandbox_session_expires`, so full Docker environments do not require manual volume resets.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-adapter-sandbox-container,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,SandboxArtifactTests,DefaultSandboxArtifactScannerPortTests,JdbcSandboxRepositoryAdapterTests,JdbcTenantSchemaUpgradeTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SandboxArtifactToAgentArtifactPolicyTests,AdvancedFeatureControllerGateTests,ContainerSandboxRuntimeAdapterTests,ContainerSandboxAutoConfigurationTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted a healthy backend with an in-image Maven `BUILD SUCCESS`; `docker exec seahorse-postgres psql -U seahorse -d seahorse -c "\d sa_sandbox_session"` verified `profile_id` and `expires_at` are present and non-null with `idx_sa_sandbox_session_expires`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-profile-ttl-smoke` passed 13/13, including DB/API verification that the session profile is `python-small`, `expires_at > created_at`, and artifact governance still blocks content-sensitive artifacts before object storage.

Remaining roadmap work is narrowed to runtime profile policy/quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, virus/binary/PDF deep scanning plus redaction summaries, stronger runtime isolation such as gVisor or Firecracker, TTL cleanup/orphan runtime sweeps, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Session TTL Sweep P1 Evidence Update

Sandbox Runtime now has an operator-triggered expired session sweep on top of persisted `expiresAt` metadata. `SandboxRuntimeInboundPort.sweepExpiredSessions(tenantId, limit)` returns `SandboxSessionSweepResult` with matched, closed, failed counts and closed session records. `KernelSandboxRuntimeService` loads expired non-terminal sessions through `SandboxSessionRepositoryPort.listExpiredActiveSessions(...)`, calls `SandboxRuntimePort.closeSession(...)` to release runtime resources, and persists successfully released sessions as `TIMED_OUT` with `RUNTIME_TIMED_OUT` instead of operator `CANCELLED`. `JdbcSandboxRepositoryAdapter` uses the existing `idx_sa_sandbox_session_expires` path to query `expires_at <= now` and excludes terminal statuses. `SeahorseSandboxController` exposes `POST /api/sandbox/sessions/expired:sweep?tenantId=...&limit=...` behind the existing SANDBOX advanced feature gate, and the admin Sandbox page now includes an operator sweep button beside the recent-session refresh control.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,JdbcSandboxRepositoryAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,AdvancedFeatureControllerGateTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected sandbox classes covered kernel sweep semantics, JDBC expired-active filtering, Web API response shape, and interface ripple tests. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-ttl-sweep-smoke` passed 14/14. The smoke created a real sandbox session, moved its database `expires_at` into the past to simulate old-volume operational cleanup, called the new sweep API, verified the response included the target session as `TIMED_OUT`, and verified PostgreSQL persisted `status=TIMED_OUT` and `reason_code=RUNTIME_TIMED_OUT`, while artifact object storage/download/content-scan governance still passed.

Remaining roadmap work is narrowed to scheduled/background TTL cleanup, orphan container/runtime pool sweeps, runtime profile policy/quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, virus/binary/PDF deep scanning plus redaction summaries, stronger runtime isolation such as gVisor or Firecracker, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Scheduled TTL Sweep P1 Evidence Update

Sandbox expired session cleanup now runs as a background Spring scheduled job in addition to the operator-triggered sweep API. `SeahorseAgentSandboxMaintenanceAutoConfiguration` enables scheduling for sandbox maintenance and registers `SandboxSessionTtlSweepJob` when `SandboxRuntimeInboundPort` is available and `seahorse.agent.sandbox.session-sweep.enabled` is true. The job uses `DistributedLockPort` to avoid multi-node duplicate sweeps, calls `SandboxRuntimeInboundPort.sweepExpiredSessions(tenantId, limit)`, logs non-empty sweep results, and keeps the kernel/runtime lifecycle owner unchanged. Full compose exposes `SEAHORSE_AGENT_SANDBOX_SESSION_SWEEP_*` environment knobs so operators and E2E tests can tune enablement, tenant, limit, initial delay, and fixed delay without rebuilding.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,SandboxPythonToolPortAdapterTests,JdbcSandboxRepositoryAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,AdvancedFeatureControllerGateTests,SandboxSessionTtlSweepJobTests,SeahorseAgentSandboxAutoConfigurationTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected classes reported kernel 26/26, Web 11/11, and autoconfigure 10/10 passing. `git diff --check` passed with only line-ending warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`, `SEAHORSE_AGENT_SANDBOX_SESSION_SWEEP_FIXED_DELAY_MS=2000`, `SEAHORSE_AGENT_SANDBOX_SESSION_SWEEP_INITIAL_DELAY_MS=1000`, and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-scheduled-ttl-sweep-smoke -UseScheduledSweep -ScheduledSweepWaitSeconds 60` passed 14/14. The smoke created a sandbox session, moved `expires_at` into the past, did not call the manual sweep API, waited for the scheduled job to persist `status=TIMED_OUT` and `reason_code=RUNTIME_TIMED_OUT`, and still verified object storage, governed detail/download, local object existence, and content-sensitive artifact blocking. Backend logs showed `Sandbox session TTL sweep finished tenantId=default, matched=1, closed=1, failed=0` on the `scheduling-1` thread with the sweep env vars set in the container.

Remaining roadmap work is narrowed to orphan live-container/runtime pool health sweeps, runtime profile policy/quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, virus/binary/PDF deep scanning plus redaction summaries, stronger runtime isolation such as gVisor or Firecracker, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Orphan Workspace Sweep P1 Evidence Update

Sandbox Runtime now has an operator-triggered and scheduled orphan workspace cleanup path for the Docker/Podman container adapter. `SandboxSessionRepositoryPort.listActiveSessionIds()` lets the kernel gather all non-terminal sandbox sessions across tenants, `SandboxRuntimeInboundPort.sweepOrphanedRuntimeResources()` passes that active set into `SandboxRuntimePort.sweepOrphanedResources(...)`, and `ContainerSandboxRuntimeAdapter` deletes only old `sandbox_container_*` workspace directories under its configured `workspaceRoot` that are not active sessions. The sweep is guarded by `orphanWorkspaceMinAge`, returns inspected/skipped/removed/failed counts plus removed workspace names, and the default unsupported runtime returns an empty result without touching the filesystem.

`SeahorseSandboxController` exposes `POST /api/sandbox/runtime/orphans:sweep` behind the existing SANDBOX advanced feature gate. `SeahorseAgentSandboxMaintenanceAutoConfiguration` now also registers `SandboxRuntimeOrphanSweepJob` when `seahorse.agent.sandbox.runtime-sweep.enabled` is true; the job uses `DistributedLockPort` with its own lock name and logs non-empty sweep results. Full compose exposes `SEAHORSE_AGENT_SANDBOX_RUNTIME_SWEEP_*` environment knobs, and the sandbox overlay exposes `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_ORPHAN_WORKSPACE_MIN_AGE`.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-sandbox-container,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,SandboxPythonToolPortAdapterTests,JdbcSandboxRepositoryAdapterTests,ContainerSandboxRuntimeAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,AdvancedFeatureControllerGateTests,SandboxRuntimeOrphanSweepJobTests,SandboxSessionTtlSweepJobTests,SeahorseAgentSandboxAutoConfigurationTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the explicitly selected sandbox classes completed with 0 failures and 0 errors. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-orphan-runtime-sweep-smoke` passed 15/15. The smoke created a real active sandbox session workspace, created an old fake `sandbox_container_*` orphan workspace inside the backend sandbox workspace mount, called `POST /api/sandbox/runtime/orphans:sweep`, verified the response removed the orphan and skipped at least one active workspace, verified the orphan directory was gone while the active directory remained, then closed the active session and verified its workspace was deleted. The same run still verified object storage, governed artifact detail/download, local object existence, expired-session sweep persistence, and content-sensitive artifact blocking.

Remaining roadmap work is narrowed to live orphan container inspection and runtime pool health sweeps, runtime profile policy/quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, virus/binary/PDF deep scanning plus redaction summaries, stronger runtime isolation such as gVisor or Firecracker, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Live Container Inspection P1 Evidence Update

Sandbox Runtime orphan sweeps now include a read-only live container inspection signal for the Docker/Podman container adapter. `SandboxRuntimeCleanupResult` carries inspected/active/orphan container counts, active/orphan container names, and inspection failure messages. `ContainerSandboxRuntimeAdapter.sweepOrphanedResources(...)` still removes only old orphan `sandbox_container_*` workspaces under the configured workspace root, then runs `docker|podman ps -a --filter name=seahorse-sandbox- --format "{{.Names}}\t{{.Status}}"` through the existing bounded command runner. Containers whose names match non-terminal session ids are reported as active; other managed `seahorse-sandbox-*` containers are reported as orphans. The sweep intentionally does not kill or remove live containers yet, so this slice adds runtime pool visibility without expanding destructive behavior. The admin Sandbox page now includes orphan container counts in the sweep toast.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,ContainerSandboxRuntimeAdapterTests,SeahorseSandboxControllerTests,SandboxRuntimeOrphanSweepJobTests,SeahorseAgentSandboxAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected classes covered kernel delegation, container active/orphan classification, inspection failure reporting, Web API, and scheduled sweep wiring. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-live-container-inspection-smoke` passed 15/15. The smoke created a real orphan workspace and a real `seahorse-sandbox-orphan-live-*` container visible through the backend Docker socket, called `POST /api/sandbox/runtime/orphans:sweep`, verified `failedContainerInspectionCount=0`, verified `orphanContainerNames` included the live test container, verified the orphan workspace was removed while active session workspace was preserved, and then removed the test container. The same run still verified object storage, governed artifact detail/download, local object existence, expired-session sweep persistence, and content-sensitive artifact blocking.

Remaining roadmap work is narrowed to runtime profile policy/quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, virus/binary/PDF deep scanning plus redaction summaries, stronger runtime isolation such as gVisor or Firecracker, container reaping/capacity/node-health hardening beyond read-only inspection, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Runtime Health P1 Evidence Update

Sandbox Runtime now exposes a read-only health endpoint through the normal sandbox runtime boundary. `SandboxRuntimeInboundPort.inspectRuntimeHealth()` asks the kernel for all non-terminal session ids, `SandboxRuntimePort.inspectHealth(activeSessionIds)` lets the concrete runtime adapter own engine/workspace/container inspection, and `SeahorseSandboxController` exposes `GET /api/sandbox/runtime/health` behind the existing SANDBOX advanced feature gate. The Docker/Podman container adapter reports runtime, engine, `HEALTHY`/`DEGRADED`/`UNAVAILABLE` status, engine/workspace availability, active session count, managed container counts, active/orphan container names, and inspection failure messages. The default unsupported runtime returns `UNSUPPORTED` without touching the host.

The admin Sandbox page now has a runtime health action beside the existing sweep controls, and frontend service contracts plus the backend endpoint manifest include `/api/sandbox/runtime/health`. The health path is intentionally non-destructive: it does not kill or remove containers, and the full-Docker smoke verifies the response does not leak the configured workspace root path.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,SandboxPythonToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,AdvancedFeatureControllerGateTests,SandboxRuntimeOrphanSweepJobTests,SeahorseAgentSandboxAutoConfigurationTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel, Web, and autoconfigure modules reported 28/28, 11/11, and 11/11 tests passing respectively, and the sandbox container adapter module completed successfully in the same reactor. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-runtime-health-smoke` passed 16/16. The smoke verified `GET /api/sandbox/runtime/health` returned `runtime=container`, `engine=docker`, `engineAvailable=true`, `workspaceAvailable=true`, `failedContainerInspectionCount=0`, and no configured sandbox workspace root leak, while the same run still verified artifact object storage, governed artifact APIs/download, expired-session sweep persistence, orphan workspace cleanup, and content-sensitive artifact blocking.

Remaining roadmap work is narrowed to runtime profile policy/quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, virus/binary/PDF deep scanning plus redaction summaries, stronger runtime isolation such as gVisor or Firecracker, destructive orphan container reaping/capacity/node-health hardening beyond read-only inspection, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Orphan Runtime Container Reap P1 Evidence Update

Sandbox Runtime now exposes an explicit operator action for controlled orphan managed-container reaping. `SandboxRuntimeInboundPort.reapOrphanedRuntimeContainers(dryRun)` gathers all non-terminal session ids through the kernel, and `SandboxRuntimePort.reapOrphanedContainers(activeSessionIds, dryRun)` lets the Docker/Podman adapter own host command execution. The container adapter inspects only managed `seahorse-sandbox-*` containers, classifies active containers by session id, defaults the API to `dryRun=true`, and revalidates the managed-name prefix before executing `docker rm -f <container>` on a real reap.

`SeahorseSandboxController` exposes `POST /api/sandbox/runtime/orphan-containers:reap?dryRun=true|false` behind the existing SANDBOX advanced feature gate. The admin Sandbox page adds a guarded two-step flow: preview first, then confirmation before the non-dry-run call. This keeps the existing orphan workspace sweep separate while giving operators a bounded recovery path for exited or stranded managed containers.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,SandboxPythonToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,AdvancedFeatureControllerGateTests,SandboxRuntimeOrphanSweepJobTests,SeahorseAgentSandboxAutoConfigurationTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel, Web, and autoconfigure modules reported 29/29, 11/11, and 11/11 tests passing respectively, and the sandbox container adapter module completed successfully in the same reactor. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-orphan-container-reap-smoke` passed 16/16 after creating a real `seahorse-sandbox-orphan-live-*` container. The smoke verified dry-run reported the orphan without deleting it, the non-dry-run response included the container in `reapedContainerNames`, `docker ps -a` no longer listed it afterward, and the same run still verified runtime health, object storage, governed artifact APIs/download, expired-session sweep persistence, orphan workspace cleanup, and content-sensitive artifact blocking.

Remaining roadmap work is narrowed to runtime profile policy/quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, virus/binary/PDF deep scanning plus redaction summaries, stronger runtime isolation such as gVisor or Firecracker, capacity/node-health hardening beyond single-node Docker inspection, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Runtime Capacity Signal P1 Evidence Update

Sandbox Runtime health now includes a read-only active-session capacity signal for the Docker/Podman container adapter. `ContainerSandboxAdapterProperties.maxActiveSessions` defaults to `0` for unbounded capacity and is exposed in the full-compose sandbox overlay as `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_MAX_ACTIVE_SESSIONS`. `SandboxRuntimeHealth` now reports `activeSessionLimit`, `activeSessionRemaining`, `activeSessionCapacityAvailable`, and `capacityStatus` (`UNBOUNDED`, `AVAILABLE`, or `SATURATED`). The adapter derives these fields from the kernel-provided non-terminal active session ids and degrades health to `DEGRADED` when a configured capacity is saturated; it does not reject session creation or introduce scheduling ownership in this slice.

The admin Sandbox health action now includes capacity in its toast summary, and the sandbox E2E smoke can assert the configured runtime active-session limit through the real backend API. This keeps runtime capacity observable while leaving admission control, quota policy, and node-pool scheduling as follow-up production hardening.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container,seahorse-agent-adapter-web -am "-Dtest=KernelSandboxRuntimeServiceTests,ContainerSandboxRuntimeAdapterTests,ContainerSandboxAutoConfigurationTests,SeahorseSandboxControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel, Web, and sandbox container tests reported 26/26, 1/1, and 18/18 passing respectively. Full-Docker evidence also passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_MAX_ACTIVE_SESSIONS=100`: compose overlay validation completed through `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`, the backend was rebuilt/restarted through `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend`, and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-runtime-capacity-smoke -ExpectedRuntimeActiveSessionLimit 100` passed 16/16 while verifying `activeSessionLimit`, `activeSessionCapacityAvailable`, `activeSessionRemaining`, and `capacityStatus` through the real backend API.

Remaining roadmap work is narrowed to runtime profile policy/quota administration UX and admission control, broader sandbox-backed tools such as browser automation and file conversion, virus/binary/PDF deep scanning plus redaction summaries, stronger runtime isolation such as gVisor or Firecracker, node-pool scheduling/health beyond single-node Docker capacity signals, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Runtime Capacity Admission P1 Evidence Update

Sandbox Runtime session creation now uses the runtime health capacity signal as a conservative admission preflight. `KernelSandboxRuntimeService#createSession(...)` still evaluates `SandboxPolicyPort` first; when policy allows, it loads non-terminal session ids through `SandboxSessionRepositoryPort.listActiveSessionIds()`, asks `SandboxRuntimePort.inspectHealth(...)` for adapter-owned capacity state, and persists a failed sandbox session with `RUNTIME_CAPACITY_EXCEEDED` when `activeSessionCapacityAvailable=false` instead of calling `SandboxRuntimePort#createSession(...)`.

The default `max-active-sessions=0` remains unbounded, and the unsupported runtime keeps its fail-closed execution behavior because its health reports unbounded capacity. This closes the immediate single-node Docker admission gap while keeping tenant/agent quota policy and node-pool scheduling as explicit follow-up work. The admin Sandbox page now treats API-created `FAILED` sessions as rejected sessions and surfaces the reason code in the toast instead of reporting a successful create.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,SandboxPythonToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests,ContainerSandboxAutoConfigurationTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,AdvancedFeatureControllerGateTests,SandboxRuntimeOrphanSweepJobTests,SeahorseAgentSandboxAutoConfigurationTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel test includes saturated-capacity rejection before runtime create. Frontend capability contracts pass via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completes with only the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`, `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_MAX_ACTIVE_SESSIONS=5`, and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted a healthy backend with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-capacity-admission-smoke -ExpectedRuntimeActiveSessionLimit 5 -VerifyCapacityAdmission` passed 17/17. The smoke verified runtime health reports the configured limit, filled capacity through real session creates, observed `capacityStatus=SATURATED` and `activeSessionCapacityAvailable=false`, then verified an extra create returns `status=FAILED` and `reasonCode=RUNTIME_CAPACITY_EXCEEDED` with the same state persisted in PostgreSQL.

Remaining roadmap work is narrowed to runtime profile policy/quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, virus/binary/PDF deep scanning plus redaction summaries, stronger runtime isolation such as gVisor or Firecracker, node-pool scheduling/health beyond single-node capacity preflight, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-02 Sandbox Artifact Scan Summary Auditability Evidence Update

Sandbox artifact scanner decisions now remain auditable after persistence. `SandboxArtifact` carries a bounded `scanSummary`, `KernelSandboxRuntimeService` saves `SandboxArtifactScanResult.summary()` for normal scanner decisions, and fail-closed scanner/storage-copy paths use fixed safe summaries without exception text, local paths, secrets, or storage references. `JdbcSandboxRepositoryAdapter` persists the new `sa_sandbox_artifact.scan_summary` column, with both `resources/database/seahorse_init.sql`, `resources/database/migrations/V47__sandbox_artifact_scan_summary.sql`, and startup `JdbcTenantSchemaUpgrade` covering fresh databases and existing Docker volumes.

The sandbox artifact list/detail API now returns `scanSummary`, and the admin Sandbox page displays the summary in artifact rows and details while still omitting `objectUri`/`storageRef`. The E2E smoke verifies both a clean artifact summary (`metadata scan passed`) and a content-sensitive blocked summary (`sensitive artifact content`) through PostgreSQL and the safe API responses.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,DefaultSandboxArtifactScannerPortTests,SandboxArtifactTests,JdbcSandboxRepositoryAdapterTests,JdbcTenantSchemaUpgradeTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SandboxArtifactToAgentArtifactPolicyTests,AdvancedFeatureControllerGateTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; the first backend rebuild attempt hit a transient Maven Central TLS handshake failure while resolving Spring artifacts, and the immediate retry completed with in-image Maven `BUILD SUCCESS` and restarted a healthy backend. `docker exec seahorse-postgres psql -U seahorse -d seahorse -c "\d sa_sandbox_artifact"` confirmed `scan_summary VARCHAR(256)` on the existing volume, and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-scan-summary-smoke` passed 16/16.

Remaining roadmap work is narrowed to runtime profile policy/quota administration UX, broader sandbox-backed tools such as browser automation and file conversion, virus scanning plus binary/PDF deep scanning, structured redaction-summary payloads beyond the bounded scan-summary field, stronger runtime isolation such as gVisor or Firecracker, node-pool scheduling/health beyond single-node capacity preflight, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-03 Sandbox File Conversion Tool P1 Evidence Update

The sandbox-backed tool surface now includes a conservative `sandbox_file_convert` built-in tool. The kernel adapter accepts bounded inline table content for `csv`/`tsv` to `json` and `json` to `csv`/`tsv` conversions, creates a `FILE_CONVERSION` sandbox session with network disabled, executes through `SandboxRuntimeInboundPort`, closes the session in a finally block, and returns governed artifact metadata including `scanSummary`. The built-in catalog registers the tool as `HIGH`, `EXECUTE`, and `SANDBOX`, and the existing sandbox tools feature flag disables both `sandbox_python` and `sandbox_file_convert`.

The Docker/Podman container runtime adapter now supports `FILE_CONVERSION` without exposing arbitrary code execution. For `CODE_INTERPRETER`, it preserves the existing user-script `main.py` path. For `FILE_CONVERSION`, it parses a structured conversion request, writes a generated stdlib Python converter plus `input.<sourceFormat>`, runs the existing no-network Python container command, and collects only `converted.<targetFormat>` as the prompt-governed artifact. The generated script and source input file are excluded from artifact collection, so the tool does not leak converter internals or duplicate source content into the artifact list.

Fresh evidence: focused kernel/container tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,SandboxPythonToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests,ContainerSandboxAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel tests reported 10/10 passing and the selected container tests reported 22/22 passing. These cover descriptor/schema exposure, CSV/TSV/JSON conversion gating, TSV-to-JSON and JSON-to-CSV runtime request forwarding, generated converter behavior, output-only artifact collection, media type classification, and unsupported conversion fail-closed paths. Spring auto-configuration and built-in catalog tests passed 4/4 via `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`.

Fresh full-Docker evidence: Docker engine version `29.5.2` was available, compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`. `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt the backend image with an in-image Maven `BUILD SUCCESS` and restarted `seahorse-backend`.

`.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-table-convert-smoke` passed 11/11. The smoke verified `sandbox_file_convert` appears in the real built-in tool catalog as `HIGH`/`SANDBOX`, invoked CSV-to-JSON and JSON-to-CSV through `/api/tools/sandbox_file_convert/invoke`, verified PostgreSQL persisted `runtime_type=FILE_CONVERSION`, `profile_id=file-conversion`, closed session status `CANCELLED`, JSON artifact media type `application/json`, CSV artifact media type `text/csv`, `scan_status=CLEAN`, `sensitivity=INTERNAL`, and `scan_summary=metadata scan passed`, downloaded both converted artifacts through the governed artifact endpoint, and verified both local object storage files exist with the marker. Regression evidence: `.\scripts\e2e-sandbox-python-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-table-convert-regression-smoke` passed 3/3, the active sandbox session count query returned `0`, and `docker ps -a --filter "name=seahorse-sandbox-" --format "{{.Names}}"` returned no leftover managed sandbox containers.

Remaining roadmap work is narrowed to runtime profile policy writes and tenant/agent quota administration, sandbox browser automation, broader document/binary conversion formats beyond CSV/TSV/JSON table conversion, virus scanning plus binary/PDF deep scanning, structured redaction-summary payloads beyond the bounded scan-summary field, stronger runtime isolation such as gVisor or Firecracker, node-pool scheduling/health beyond single-node capacity preflight, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-03 Sandbox Runtime Governance Profiles P1 Evidence Update

Sandbox Operations now exposes a read-only runtime governance view instead of leaving profile/capacity signals hidden behind one-off health toasts. `GET /api/sandbox/runtime/profiles` returns the kernel-owned default runtime profile mapping, default network posture, and default TTL without touching Docker/Podman: `CODE_INTERPRETER -> python-small`, `FILE_CONVERSION -> file-conversion`, `BROWSER_AUTOMATION -> browser-readonly`, and `SHELL -> shell-restricted`. The response marks Code Interpreter and File Conversion as container-runtime supported, keeps Browser Automation and Shell as planned, and reports network disabled for all current profiles.

The admin Sandbox page now loads runtime health and runtime profiles into a persistent Runtime governance panel. Operators can see runtime/engine status, checked time, active-session capacity, remaining capacity, inspected/orphan container counts, default network policy, default TTL, and per-runtime profile status from the page itself. This slice remains intentionally read-only: it does not add profile mutation, tenant/agent quota policy writes, node-pool scheduling, or any runtime adapter side effects.

Fresh evidence: focused Web tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected Web tests reported 2/2 passing. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-runtime-governance-smoke` passed 17/17. The smoke verified `/api/sandbox/runtime/profiles` reports `DENY_ALL`, `defaultTtlSeconds=3600`, all four runtime profiles, supported/planned status, no-network posture, and no configured sandbox workspace root leak, while preserving the existing artifact storage, scan blocking, runtime health, expired sweep, orphan workspace cleanup, and governed download checks.

### 2026-07-03 Sandbox Document Text Conversion Evidence Update

`sandbox_file_convert` now extends the conservative `FILE_CONVERSION` surface beyond table-shaped CSV/TSV/JSON conversions. The kernel descriptor advertises `txt`, `html`, `markdown`, `md`, `docx`, and `pdf`, normalizes `md` to `markdown`, and allows `txt -> html`, `html -> txt`, `markdown/md -> html/txt`, and base64-only `docx -> txt` / `pdf -> txt` while still failing closed for unsupported pairs.

The Docker/Podman container adapter keeps the implementation intentionally narrow: it writes text sources to `input.txt`, `input.html`, or `input.md`, decodes base64 DOCX/PDF input to `input.docx` or `input.pdf`, generates a stdlib-only Python converter, runs it in the existing no-network file-conversion runtime, and collects only `converted.<targetFormat>`. The DOCX path reads `word/document.xml` with Python stdlib `zipfile` plus `xml.etree.ElementTree`; the PDF path extracts literal strings from unencrypted PDF streams with Python stdlib `re` and `zlib`. It does not add LibreOffice/Tika, PDF rendering/OCR, Office editing, arbitrary binary conversion, external packages, or network access.

Fresh evidence: focused Java tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel tests reported 12/12 passing and the selected container tests reported 29/29 passing. These cover descriptor/schema exposure, base64 content encoding forwarding, DOCX/PDF fail-closed validation before session creation, generated converter behavior, decoded `input.docx`/`input.pdf` handling, PDF header escaping in the generated script, output-only artifact collection, media type classification, and unsupported conversion fail-closed paths.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-pdf-convert-smoke` passed 23/23. The smoke verified the existing CSV-to-JSON, JSON-to-CSV, Markdown-to-HTML, and DOCX-to-TXT governed artifact paths, then invoked PDF-to-TXT through `/api/tools/sandbox_file_convert/invoke` with `contentEncoding=base64`, verified PostgreSQL persisted `runtime_type=FILE_CONVERSION`, `profile_id=file-conversion`, closed session status `CANCELLED`, TXT artifact media type `text/plain`, `scan_status=CLEAN`, `sensitivity=INTERNAL`, and `scan_summary=metadata scan passed`, downloaded the generated TXT through the governed artifact endpoint, verified the local object storage file exists with the marker, confirmed no leftover managed sandbox containers, and confirmed zero non-terminal sandbox sessions.

Remaining roadmap work is narrowed to runtime profile policy writes and tenant/agent quota administration, browser egress/URL policy and auth/session capture, PDF rendering/OCR beyond conservative literal-text extraction, Office rendering/editing beyond the current conservative DOCX text extraction, LibreOffice/Tika-backed conversion, arbitrary binary conversion, virus scanning plus binary/PDF deep scanning, stronger runtime isolation such as gVisor or Firecracker, node-pool scheduling/health beyond single-node capacity preflight, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-03 Sandbox Tool Quota Governance P1 Evidence Update

Sandbox Operations now has a narrow quota administration write path for sandbox-backed tools. `POST /api/sandbox/runtime/tool-quota-policies` is gated by both `SANDBOX` and `QUOTA_MANAGEMENT`, accepts only `sandbox_python`, `sandbox_file_convert`, and the planned `sandbox_browser`, normalizes tool ids, and writes through the existing quota management port as `QuotaScope.TOOL` with `subjectId=<toolId>`. This deliberately reuses Tool Gateway quota preflight instead of adding a separate sandbox resource-policy table or runtime profile mutation surface.

The frontend service manifest and sandbox service now expose `upsertSandboxToolQuotaPolicy(...)`, and the admin Sandbox page includes a Tool quota panel for policy id, sandbox tool id, status, call/token/cost limits, and warn ratio. The sandbox service uses the UI proxy path needed by the packaged Nginx/Vite proxy so browser calls reach backend `/api/sandbox/...` routes instead of the stripped `/sandbox/...` path.

The Tool Gateway quota smoke creates a zero-call `sandbox_python` policy through the Sandbox API before invoking `sandbox_python` through the real Tool Gateway. The expected denial is `QUOTA_HARD_LIMIT_EXCEEDED`, proving the operator-facing Sandbox endpoint and the existing gateway quota enforcement are on the same path.

Fresh evidence: focused Web regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`; and `npm run build` completed successfully with only the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`, then `.\scripts\e2e-tool-gateway-quota-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-tool-quota-smoke-rerun` passed 4/4. Cleanup evidence showed the latest smoke policies disabled as `TOOL|sandbox_python|0`, no leftover `seahorse-sandbox-*` containers, and zero non-terminal sandbox sessions.

Fresh UX evidence: `npm test -- src/services/frontendCapabilityContracts.test.ts` passed 10/10, `npm run build` completed with only the existing browserslist/chunk-size warnings, and the built `frontend/dist` was copied into the running `seahorse-frontend` container for a real browser smoke. `.\scripts\e2e-sandbox-tool-quota-page-smoke.ps1 -BaseUrl http://127.0.0.1 -Password admin123 -Marker seahorse-sandbox-tool-quota-ux-smoke` passed, saving a screenshot at `output/playwright/artifacts/seahorse-sandbox-tool-quota-ux-smoke.png`. Follow-up cleanup evidence showed the two page-smoke policies `sandbox-tool-quota-page-*` were `DISABLED`, no leftover `seahorse-sandbox-*` containers, and zero non-terminal sandbox sessions. A fresh enforcement rerun also passed via `.\scripts\e2e-tool-gateway-quota-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-tool-quota-ux-smoke` with 4/4.

Remaining roadmap work is narrowed to runtime profile policy writes, tenant/agent quota UX beyond the sandbox-backed tool-level endpoint, sandbox browser automation, PDF rendering/OCR plus Office/binary conversion beyond the current conservative text conversions, virus scanning plus binary/PDF deep scanning, structured redaction-summary payloads beyond the bounded scan-summary field, stronger runtime isolation such as gVisor or Firecracker, node-pool scheduling/health beyond single-node capacity preflight, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-03 Sandbox Browser Automation Tool P1 Evidence Update

The sandbox-backed tool surface now includes a conservative `sandbox_browser` built-in tool. The kernel adapter accepts bounded inline HTML for `snapshot` and `extract_text`, creates a `BROWSER_AUTOMATION` sandbox session with network disabled, executes through `SandboxRuntimeInboundPort`, closes the session in a finally block, and returns governed artifact metadata for `browser-result.json` plus an optional `screenshot.png`. The built-in catalog registers the tool as `HIGH`, `EXECUTE`, and `SANDBOX`, and the sandbox tool quota endpoint now treats `sandbox_browser` as a live sandbox-backed tool id.

The Docker/Podman container runtime adapter now supports `BROWSER_AUTOMATION` without opening external browsing. It writes `browser-input.html`, generates a Python Playwright script, runs it in the no-network browser runtime image `seahorse-sandbox-browser:playwright-1.48.0`, blocks non-`about:`/`blob:`/`data:` requests at the page route layer, and collects only `browser-result.json` and `screenshot.png` as prompt-governed artifacts. The generated script and source HTML file are excluded from artifact collection. A project Dockerfile at `resources/docker/Dockerfile.sandbox-browser-runtime` builds the local runtime image because the upstream Playwright Python base image ships browsers and system dependencies but not the Python `playwright` package.

Fresh evidence: focused Java tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SandboxBrowserToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SandboxPythonToolAutoConfigurationTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel, container, Web, and autoconfigure tests reported 5/5, 21/21, 2/2, and 4/4 passing respectively.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose` and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-smoke` passed 10/10. The smoke verified catalog exposure, real `/api/tools/sandbox_browser/invoke`, persisted `runtime_type=BROWSER_AUTOMATION`, `profile_id=browser-readonly`, closed session status `CANCELLED`, governed JSON and PNG artifact rows with clean scan summaries, governed result download without storage-reference leakage, local object storage files, no leftover managed sandbox containers, and zero non-terminal sandbox sessions.

Remaining roadmap work is narrowed to runtime profile policy writes, tenant/agent quota UX beyond the sandbox-backed tool-level endpoint, PDF rendering/OCR plus Office/binary conversion beyond the current conservative conversions, virus scanning plus binary/PDF deep scanning, structured redaction-summary payloads beyond the bounded scan-summary field, browser egress/URL browsing policy beyond inline no-network HTML, video/session capture, stronger runtime isolation such as gVisor or Firecracker, node-pool scheduling/health beyond single-node capacity preflight, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-03 Sandbox Runtime Profile Policy Writes P1 Evidence Update

Sandbox Operations now has a bounded per-tenant runtime profile policy write path. `POST /api/sandbox/runtime/profile-policies` accepts only the kernel-owned existing profile ids, supports `ACTIVE` and `DISABLED`, allows a bounded `sessionTtlSeconds` override from 60 to 7200 seconds, and keeps `networkAllowed=false`. `GET /api/sandbox/runtime/profiles?tenantId=default` now returns persisted policy metadata including `policyId`, `policyStatus`, and the effective TTL for the admin Runtime governance panel.

`KernelSandboxRuntimeService` applies the policy during new session creation: disabled profiles persist a failed session with `RUNTIME_PROFILE_DISABLED`, and active TTL overrides set the persisted `expiresAt`. JDBC persists the new `sa_sandbox_runtime_profile_policy` table through migration `V48__sandbox_runtime_profile_policy.sql`, fresh init schema, and startup tenant schema upgrade/RLS repair. This slice intentionally does not add arbitrary profile creation, network egress/allowlists, tenant/agent quota UX, gVisor/Firecracker, or broader resource-policy modeling.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelSandboxRuntimeServiceTests,JdbcSandboxRepositoryAdapterTests,JdbcTenantSchemaUpgradeTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SeahorseAgentSandboxAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts` from `frontend`; and `npm run build` completed successfully with only the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: `.\scripts\e2e-sandbox-runtime-profile-policy-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-runtime-profile-policy-smoke` passed 12/12. The smoke reset the `CODE_INTERPRETER` policy, wrote TTL 120, verified runtime profile list policy metadata, created a real session and checked PostgreSQL persisted the TTL-derived expiry, disabled the profile and observed `RUNTIME_PROFILE_DISABLED`, then restored `ACTIVE|3600|false` and closed the created session. Cleanup evidence showed no leftover `seahorse-sandbox-*` containers and zero non-terminal sandbox sessions.

Remaining roadmap work is narrowed to tenant/agent quota UX beyond the sandbox-backed tool-level endpoint, PDF rendering/OCR plus Office/binary conversion beyond the current conservative conversions, virus scanning plus binary/PDF deep scanning, browser egress/URL browsing policy beyond inline no-network HTML, video/session capture, stronger runtime isolation such as gVisor or Firecracker, node-pool scheduling/health beyond single-node capacity preflight, and broader A2A/cross-provider Tool Gateway hardening.

### 2026-07-03 Sandbox Artifact Structured Redaction Summary Evidence Update

Sandbox artifacts now carry a bounded structured redaction summary payload alongside the human-readable `scanSummary`. `SandboxArtifactRedactionSummary` emits schema version 1 JSON with scanner id, decision, blocked/redacted booleans, content-scanned state, categories, and a safe reason. The default scanner now reports categories such as `SECRET`, `PERSONAL_DATA`, `PRIVATE_KEY`, `CONTENT_UNAVAILABLE`, `CONTENT_TOO_LARGE`, `UNSUPPORTED_MEDIA_TYPE`, and `SCAN_ERROR` without embedding raw matched values.

Persistence and API surfaces are wired end to end: `sa_sandbox_artifact.redaction_summary_json VARCHAR(2048)` is present in migration `V49__sandbox_artifact_redaction_summary.sql`, the fresh init schema, and startup tenant schema upgrade; JDBC insert/update/read paths preserve it; sandbox list/detail responses and sandbox-backed tool artifact metadata expose `redactionSummaryJson`; and the admin Sandbox artifact detail shows the JSON while continuing to omit `objectUri`/`storageRef`.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=SandboxArtifactTests,DefaultSandboxArtifactScannerPortTests,KernelSandboxRuntimeServiceTests,JdbcSandboxRepositoryAdapterTests,JdbcTenantSchemaUpgradeTests,SeahorseSandboxControllerTests,SandboxArtifactToAgentArtifactPolicyTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`; and `npm run build` completed successfully with only the existing browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-redaction-summary-smoke` passed 17/17. Cleanup evidence showed no leftover `seahorse-sandbox-*` containers, zero non-terminal sandbox sessions, and PostgreSQL column metadata `redaction_summary_json|character varying|2048`.

This completes the structured redaction-summary payload for the current metadata/text scanner and fail-closed scanner/storage-copy decisions. Virus scanning, PDF/binary deep scanning, external scanner engines, browser egress/URL policy, video capture, stronger isolation, node-pool health, and broader A2A/cross-provider Tool Gateway hardening remain follow-up work.

### 2026-07-03 Sandbox Browser Restricted HAR Capture Evidence Update

The conservative `sandbox_browser` tool now accepts `har=true` to emit a governed `browser-network.har` artifact alongside `browser-result.json` and optional `screenshot.png`. The Docker/Podman browser runtime records Playwright request/response/failure events into HAR 1.2 shaped JSON and marks route-aborted non-inline requests with `_blocked: true`; it still uses inline HTML only, keeps the container network disabled, and does not add external URL browsing, egress allowlists, credentials, video recording, session capture, or broader browser workflows.

Fresh evidence: focused Java tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxBrowserToolPortAdapterTests,DefaultSandboxArtifactScannerPortTests,ContainerSandboxRuntimeAdapterTests,ContainerSandboxAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel tests reported 10/10 passing and the selected container/autoconfiguration tests reported 24/24 passing. Built-in tool catalog registration passed 1/1 via `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with the existing Browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-har-smoke` passed 11/11. The smoke verified real Tool Gateway invocation, persisted `BROWSER_AUTOMATION` session/profile metadata, governed JSON/PNG/HAR artifacts, PostgreSQL `application/har+json` clean/internal artifact row, HAR download containing the blocked `example.invalid` request marker with `_blocked: true`, no storage-reference leakage, object storage files, no leftover managed sandbox containers, and zero non-terminal sandbox sessions.

This completes restricted HAR/network capture for inline no-network browser automation. Browser egress/URL policy beyond inline HTML, video recording, auth/session capture, richer browser workflows, stronger runtime isolation, node-pool scheduling/health, PDF rendering/OCR plus Office/binary conversion beyond conservative text extraction, virus/binary/PDF deep scanning, external scanner engines, and broader A2A/cross-provider Tool Gateway hardening remain follow-up work.

### 2026-07-03 Sandbox Browser Download-Only Video Capture Evidence Update

The conservative `sandbox_browser` tool now accepts `video=true` on the existing inline no-network browser automation path. The Docker/Podman browser runtime records the Playwright context and emits `browser-video.webm` as `video/webm`; inline HTML, `--network none`, and the route-level block for non-inline requests remain unchanged.

This slice separates artifact download eligibility from prompt visibility. `SandboxArtifact.downloadable()` now gates clean/redacted non-secret artifacts for storage copy and governed download, while `promptVisible()` additionally requires a prompt-safe media type. As a result, `video/webm` is metadata-scanned, copied to object storage, and downloadable through artifact detail/download APIs, but remains prompt-blocked and is not returned in tool observations.

Fresh evidence: focused kernel/container tests passed 66/66 via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxArtifactTests,DefaultSandboxArtifactScannerPortTests,KernelSandboxRuntimeServiceTests,SandboxBrowserToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Built-in tool catalog registration passed 1/1 via `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed successfully with existing Browserslist/chunk-size warnings.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-video-smoke` passed 12/12. The smoke verified real Tool Gateway invocation with `video=true`, prompt-visible JSON/PNG/HAR artifacts only in the tool observation, persisted clean/internal `video/webm` metadata, prompt-blocked but downloadable artifact detail, WebM download with EBML header, object storage, no leftover managed sandbox containers, and zero non-terminal sandbox sessions.

This completes download-only browser video artifact capture for inline no-network browser automation. Browser egress/URL policy beyond inline HTML, auth/session state capture, richer browser workflows, stronger runtime isolation, node-pool scheduling/health, PDF rendering/OCR plus Office/binary conversion beyond conservative text extraction, virus/binary/PDF deep scanning, external scanner engines, and broader A2A/cross-provider Tool Gateway hardening remain follow-up work.

### 2026-07-03 Sandbox Browser Allowlisted URL Egress P1 Evidence Update

`sandbox_browser` now supports a first allowlisted URL egress path. The public tool schema accepts either bounded inline `html` or HTTP/HTTPS `url` plus `allowedHosts`; URL mode sets `networkRequested=true`, forwards normalized hosts into session create/execute, and reports `browser.networkAllowed=true` in the observation. Inline HTML remains the default no-network path.

The runtime and policy chain stays fail-closed. `SandboxRuntimeProfilePolicy.networkAllowed=true` is still rejected for non-`BROWSER_AUTOMATION` runtimes; Browser Automation URL mode also requires an active browser profile with network enabled and a global `ALLOWLISTED` sandbox policy containing the requested host. The container adapter only drops `--network none` for network-requested browser executions, adds `--add-host host.docker.internal:host-gateway`, and the generated Playwright route handler only continues `about:`, `blob:`, `data:`, or HTTP/HTTPS requests whose hostname is in `allowedHosts`.

Fresh evidence: focused Java tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-adapter-repository-jdbc -am "-Dtest=SandboxBrowserToolPortAdapterTests,DefaultSandboxPolicyPortTests,KernelSandboxRuntimeServiceTests,ContainerSandboxRuntimeAdapterTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests,SeahorseAgentSandboxAutoConfigurationTests,JdbcTenantSchemaUpgradeTests,JdbcSandboxRepositoryAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`. The selected kernel tests reported 45/45 passing, Web tests 3/3, sandbox autoconfiguration tests 5/5, and the reactor completed `BUILD SUCCESS`. Compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`, `SEAHORSE_AGENT_SANDBOX_NETWORK_POLICY=ALLOWLISTED`, `SEAHORSE_AGENT_SANDBOX_ALLOWLISTED_HOSTS=host.docker.internal`, and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-egress-smoke` passed 20/20. The smoke verified catalog exposure, the existing inline no-network path, configured `ALLOWLISTED` policy and `host.docker.internal` allowlist, browser profile network enable/restore, real URL mode Tool Gateway invocation, persisted URL-mode `BROWSER_AUTOMATION` session, governed JSON/HAR downloads, object storage, no leftover managed or fixture containers, zero non-terminal sandbox sessions, and the live database constraint `chk_sa_sandbox_runtime_profile_policy_network` allowing network only for `BROWSER_AUTOMATION`.

This is not a general browser-egress platform yet. Auth/session state capture, credentials, arbitrary browsing policies, proxy/audit-rich egress, broader browser workflows, stronger isolation, PDF rendering/OCR plus Office/binary conversion beyond conservative text extraction, virus/binary/PDF deep scanning, node-pool scheduling/health, and broader A2A/cross-provider Tool Gateway hardening remain follow-up work.

### 2026-07-03 Sandbox Browser Request-Scoped Cookie Injection Evidence Update

`sandbox_browser` URL mode now supports a bounded first auth/session slice through explicit request-scoped cookie injection. The tool schema accepts an optional `cookies` array only for URL mode; each non-empty cookie request is normalized with bounded name/value sizes, host-only domains, path/defaults, `httpOnly`, `secure`, and `sameSite`, and every cookie domain must be present in `allowedHosts`. Empty cookie arrays are treated as no cookie injection, so the inline no-network path remains compatible and fail-closed.

The container runtime writes cookie values only to a transient `browser-cookies.json` inside the per-session workspace, excludes that file from artifact collection, and loads the cookies into Playwright with `context.add_cookies(...)` before navigation. Observations and governed `browser-result.json` expose only `cookies.count` and cookie domains; cookie values are not embedded in the generated script, tool observation, governed result assertions, HAR, or collected artifact list. This does not add credential persistence, automatic browser session capture, a secrets vault, or stored browser profiles.

Fresh evidence: focused Java tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxBrowserToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel tests reported 8/8 passing and the selected container tests reported 25/25 passing. The regression coverage includes URL-mode cookie forwarding without observation leaks, non-allowlisted cookie-domain rejection, direct runtime fail-closed validation, exclusion of `browser-cookies.json` from artifacts, and the empty-cookie inline regression found by E2E.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`, `SEAHORSE_AGENT_SANDBOX_NETWORK_POLICY=ALLOWLISTED`, `SEAHORSE_AGENT_SANDBOX_ALLOWLISTED_HOSTS=host.docker.internal`, and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-cookie-smoke` passed 20/20. The smoke verified the existing inline no-network path, URL-mode cookie-authenticated fixture access, persisted URL-mode `BROWSER_AUTOMATION` session metadata, governed result/HAR downloads without cookie-value leakage, browser profile network restore, no leftover managed sandbox containers, and zero non-terminal sandbox sessions.

This completes the first request-scoped cookie injection step for sandbox browser auth/session work. Remaining browser auth/session hardening is persistent session capture/replay, credentials governance, operator UX for browser sessions, proxy/audit-rich egress, broader workflow controls, and stronger runtime isolation.

### 2026-07-03 Sandbox Browser Governed Session State Capture Evidence Update

`sandbox_browser` URL mode now supports an explicit `captureSessionState=true` flag as the next conservative auth/session slice. The tool rejects session-state capture for inline HTML, keeps URL mode behind the existing allowlisted egress and browser profile network gates, and forwards only the boolean request to the runtime. Tool observations expose `browser.sessionState.captureRequested` only; they do not include cookies, localStorage values, object URIs, or storage references.

The container runtime captures Playwright `context.storage_state(...)` after navigation into `browser-session-state.json` and emits a separate `browser-session-summary.json` with only value-free counts and domains/origins. The full state artifact is force-marked `ContextSensitivity.SECRET`, so the default scanner returns `BLOCKED` with `sensitive artifact metadata`, the artifact is not prompt-visible, not copied to object storage, and not downloadable. The summary artifact remains `CLEAN`/`INTERNAL` and prompt-visible, but contains no cookie or localStorage values.

Fresh evidence: focused Java tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxBrowserToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel tests reported 9/9 passing and the selected container tests reported 26/26 passing. The coverage includes schema exposure, URL-mode capture forwarding, inline capture rejection before session creation, generated Playwright storage-state capture, value-free summary collection, SECRET full-state artifact sensitivity, and direct runtime fail-closed validation.

Fresh full-Docker evidence: compose overlay validation passed with the allowlisted sandbox browser environment, `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-session-state-smoke` passed 21/21. The smoke verified a real fixture-set cookie and localStorage value, governed JSON/HAR downloads without value leakage, prompt-visible session summary metadata, PostgreSQL `browser-session-state.json` as `application/json|BLOCKED|SECRET|sensitive artifact metadata`, non-downloadable/prompt-hidden artifact detail without storage references, browser profile network restore, no leftover managed sandbox containers, and zero non-terminal sandbox sessions.

This completes governed session-state capture for a single URL-mode run. Session replay, credential storage, operator approval UX for reusing captured state, and long-lived browser profile management remain follow-up work.

### 2026-07-03 Sandbox Browser Request-Scoped Session State Replay Evidence Update

`sandbox_browser` URL mode now supports explicit request-scoped Playwright `sessionState` replay for a single run. The public tool schema accepts a bounded `sessionState` object, rejects it for inline HTML before session creation, validates session-state cookie domains and origin hosts against `allowedHosts`, and reports only `browser.sessionState.replayRequested` in observations. It does not accept artifact ids, storage references, persisted profiles, or credential-vault references for replay.

The container runtime repeats the same fail-closed validation for direct runtime calls, writes the replay input only to transient `browser-session-state-input.json`, excludes that file from artifact collection, and loads it with Playwright `browser.new_context(storage_state=...)`. Governed browser result JSON includes only a value-free replay summary with cookie domains/counts and origin localStorage counts; cookie/localStorage values stay out of observations, generated scripts, prompt-visible artifacts, HAR downloads, and collected artifact metadata.

Fresh evidence: focused Java tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxBrowserToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel tests reported 11/11 passing and the selected container tests reported 28/28 passing. The coverage includes schema exposure, URL-mode replay forwarding without observation leaks, inline replay rejection before session creation, non-allowlisted origin rejection, generated Playwright storage-state replay, transient replay input exclusion from artifacts, and direct runtime fail-closed validation.

Fresh full-Docker evidence: compose overlay validation passed with the allowlisted sandbox browser environment; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-session-replay-smoke` passed 25/25. The smoke verified the existing inline no-network path, URL-mode session-state capture, request-scoped replay of a real fixture cookie plus localStorage value, restored authenticated and localStorage-dependent page output, governed JSON/HAR downloads without cookie/localStorage value leakage, zero collected replay session-state input/output artifacts for replay-only requests, browser profile network restore, no leftover managed sandbox containers, and zero non-terminal sandbox sessions.

This completes explicit request-scoped one-run session-state replay. Replaying previously captured SECRET/BLOCKED artifacts, credential storage, operator approval UX, long-lived browser profiles, proxy/audit-rich egress, broader workflow controls, stronger runtime isolation, PDF rendering/OCR plus Office/binary conversion beyond conservative text extraction, virus/binary/PDF deep scanning, external scanner engines, node-pool scheduling/health, and broader A2A/cross-provider Tool Gateway hardening remain follow-up work.

### 2026-07-03 Sandbox Artifact Binary/PDF Signature Scan Evidence Update

The default sandbox artifact scanner now has a conservative binary/PDF signature pass for local `file://` artifacts that are otherwise eligible for prompt-safe binary handling or governed download. It reads only a bounded 256 KiB prefix and never decompresses archives, renders PDFs, parses Office files, or calls an external scanner engine.

New fail-closed categories are value-free and safe for `redactionSummaryJson`: `EXECUTABLE_BINARY` for PE/ELF headers, `PDF_ACTIVE_CONTENT` for bounded PDF markers such as `/JavaScript`, `/JS`, `/OpenAction`, and `/AA`, and `BINARY_SIGNATURE_MISMATCH` for ZIP/PDF/EBML/script-like masquerading under an incompatible media type. Clean download-only WebM artifacts still remain prompt-hidden and downloadable, but now record `contentScanned=true` because their EBML header is inspected.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=DefaultSandboxArtifactScannerPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 10/10, including executable masquerading, PDF active content, binary signature mismatch, and WebM header-scan coverage. Broader kernel artifact governance passed 46/46 via `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=DefaultSandboxArtifactScannerPortTests,KernelSandboxRuntimeServiceTests,SandboxArtifactTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`.

Fresh full-Docker evidence: compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --build --force-recreate backend` rebuilt and restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-binary-signature-smoke` passed 21/21. The smoke verified real `sandbox_python` produced `active.pdf` and `chart.png` artifacts, persisted them as `BLOCKED|CONFIDENTIAL` before object storage copy, reported `PDF_ACTIVE_CONTENT` and `EXECUTABLE_BINARY` summaries without raw marker leakage, kept both artifacts out of tool observations, and exposed only blocked metadata through sandbox artifact APIs.

This completes the first bounded binary/PDF signature scan slice. External virus scanning, richer PDF/binary deep scanning with a dedicated engine, richer archive/container introspection beyond bounded ZIP, PDF rendering/OCR plus Office/binary conversion beyond conservative text extraction, stronger runtime isolation, node-pool scheduling/health, and broader A2A/cross-provider Tool Gateway hardening remain follow-up work.

### 2026-07-03 Sandbox Artifact ZIP Archive Introspection Evidence Update

The default sandbox artifact scanner now performs conservative ZIP archive introspection for local `file://` artifacts whose media type is `application/zip` or `application/x-zip-compressed`. ZIP artifacts are governed download-only media: clean archives are copied to object storage and downloadable through the artifact API, but they remain prompt-hidden and are not returned in sandbox-backed tool observations.

The scan is intentionally bounded and non-recursive. It uses JDK `ZipFile`, inspects at most 128 entries, reads at most the first 256 KiB from each file entry, and never extracts the archive to the filesystem. It blocks unsafe entry paths, executable entry extensions or PE/ELF signatures, and embedded PDF active-content markers. Blocked redaction categories are value-free: `ARCHIVE_SCAN_LIMIT`, `ARCHIVE_UNSAFE_ENTRY`, `ARCHIVE_EXECUTABLE_BINARY`, `ARCHIVE_PDF_ACTIVE_CONTENT`, and `ARCHIVE_SCAN_ERROR`.

Fresh evidence: focused Java tests passed 45/45 with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=DefaultSandboxArtifactScannerPortTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; the full-compose backend rebuilt and restarted with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-archive-introspection-smoke-final` passed 27/27. The smoke verified clean ZIP artifacts are `application/zip|CLEAN|INTERNAL`, `contentScanned=true`, prompt-hidden, copied to governed object storage, downloadable, and preserve the expected entry content; unsafe ZIP artifacts are `application/zip|BLOCKED|CONFIDENTIAL` with `ARCHIVE_EXECUTABLE_BINARY`, not copied to object storage, not downloadable, prompt-hidden, and exposed through APIs without raw entry-name or storage-reference leakage.

This slice does not add ClamAV, another external scanner engine, recursive archive/container decompression, full PDF rendering/parsing, Office parsing, or general binary conversion.

### 2026-07-03 Sandbox Runtime Workspace Disk Health Evidence Update

Sandbox Runtime health now includes a read-only workspace disk health signal for the Docker/Podman container adapter. `SandboxRuntimeHealth` reports `workspaceFreeBytes`, `workspaceMinFreeBytes`, `workspaceDiskAvailable`, and `workspaceDiskStatus` (`UNBOUNDED`, `AVAILABLE`, `LOW`, or `UNKNOWN`). `ContainerSandboxAdapterProperties.minWorkspaceFreeBytes` defaults to `0`, which keeps the default path unbounded and behavior-compatible.

The adapter reads usable space from the configured workspace filesystem through `Files.getFileStore(workspaceRoot).getUsableSpace()`. When a positive threshold is configured and usable space is below it, runtime health degrades to `DEGRADED`; this slice deliberately does not reject session creation, enforce disk quotas, add runtime scheduling, or touch sandbox execution semantics. The full-compose sandbox overlay and `.env.full.example` expose `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_MIN_WORKSPACE_FREE_BYTES`.

The admin Sandbox Runtime governance panel now shows the workspace disk status/free-space summary, the runtime health toast includes the disk signal, and the artifact-storage E2E smoke asserts the new health fields through the real backend API for both default and configured-threshold runs.

Fresh evidence: focused Java regression tests passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container,seahorse-agent-adapter-web -am "-Dtest=KernelSandboxRuntimeServiceTests,ContainerSandboxRuntimeAdapterTests,SeahorseSandboxControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel, Web, and sandbox container tests reported 34/34, 2/2, and 31/31 passing. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, and `npm run build` completed with only existing Browserslist/chunk-size warnings.

Fresh full-Docker evidence: default compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; the first backend rebuild attempt hit a transient Maven Central TLS handshake while resolving Spring Boot, and the immediate retry rebuilt/restarted `seahorse-backend` with an in-image Maven `BUILD SUCCESS`. `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-workspace-disk-health-smoke` passed 27/27 and verified the default `UNBOUNDED` disk signal. A configured-threshold run with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_MIN_WORKSPACE_FREE_BYTES=1` also passed compose validation and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-workspace-disk-threshold-smoke -ExpectedWorkspaceMinFreeBytes 1` passed 27/27, verifying the `AVAILABLE` threshold path through the real runtime health API. The backend was then recreated with the default unbounded threshold.

Remaining production hardening is true disk quota enforcement, node-pool scheduling and node-level health, stronger runtime isolation, external scanner engines, richer archive/PDF/binary scanning, and broader Tool Gateway policy hardening.

### 2026-07-03 Sandbox Artifact Office Open XML Bounded Scan Evidence Update

Sandbox artifact scanning now treats Office Open XML packages as governed ZIP-family download-only artifacts. Clean DOCX/XLSX/PPTX files can be copied to object storage and downloaded through artifact APIs, but remain prompt-hidden and are not returned in sandbox-backed tool observations. Macro-enabled DOCM/XLSM/PPTM media types fail closed before object-storage copy with the value-free `OFFICE_MACRO` category.

For regular OOXML packages, the scanner reuses the bounded archive path: JDK `ZipFile`, at most 128 entries, at most the first 256 KiB from each file entry, and no filesystem extraction. A package containing `vbaProject.bin` at the root or under any path is blocked as `OFFICE_MACRO` without leaking the raw entry name or marker content through `redactionSummaryJson` or artifact APIs. Runtime and artifact download filename mapping now preserve `.docx`, `.xlsx`, `.pptx`, `.docm`, `.xlsm`, and `.pptm` extensions.

Fresh evidence: focused Java regression passed 85/85 with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=DefaultSandboxArtifactScannerPortTests,SandboxArtifactTests,KernelSandboxRuntimeServiceTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the full-compose backend rebuilt and restarted with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-office-ooxml-smoke` passed 33/33. The smoke verified clean DOCX as `application/vnd.openxmlformats-officedocument.wordprocessingml.document|CLEAN|INTERNAL`, `contentScanned=true`, prompt-hidden, copied to governed object storage, downloadable with expected `word/document.xml` content; and macro-containing DOCX as `BLOCKED|CONFIDENTIAL` with `OFFICE_MACRO`, not copied to object storage, not downloadable, and exposed through APIs without storage-reference, `vbaProject.bin`, or marker leakage.

This slice does not add Office rendering/editing, macro execution or parsing, LibreOffice/Tika conversion, recursive archive extraction, ClamAV, or another external scanner engine. Remaining scanner hardening is richer archive/container introspection, external virus scanning, deeper PDF/binary scanning with a dedicated engine, scanner policy UX, stronger runtime isolation, and node-pool scheduling/health.

### 2026-07-03 Sandbox Workspace Disk Admission Evidence Update

Sandbox session creation now uses the existing runtime workspace disk signal as a conservative admission preflight. After profile and sandbox policy checks allow a request, `KernelSandboxRuntimeService#createSession(...)` asks the runtime for health using the current active session ids. If `workspaceMinFreeBytes > 0` and `workspaceDiskAvailable=false`, the service persists a failed session with `RUNTIME_WORKSPACE_DISK_LOW` and does not call `SandboxRuntimePort#createSession(...)`. The default unbounded threshold remains behavior-compatible, including unsupported/runtime-placeholder health responses.

The artifact-storage smoke now has a low-disk admission mode that checks runtime health reports `DEGRADED`/`LOW`, creates a session through the real Web API, verifies `FAILED|RUNTIME_WORKSPACE_DISK_LOW` in both the response and PostgreSQL, and verifies no backend workspace directory is created for the rejected session. This is an admission guard only; it does not add per-session disk quotas, filesystem quota enforcement, node scheduling, or cleanup of already-running sessions.

Fresh evidence: focused Java regression passed 69/69 with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container,seahorse-agent-adapter-web -am "-Dtest=KernelSandboxRuntimeServiceTests,ContainerSandboxRuntimeAdapterTests,SeahorseSandboxControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; `scripts/e2e-sandbox-artifact-storage-smoke.ps1` parsed successfully with PowerShell `PSParser`; compose overlay validation passed for the default sandbox overlay; the full-compose backend rebuilt and restarted with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-disk-admission-regression-smoke` passed 33/33 on the default unbounded path. With `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_MIN_WORKSPACE_FREE_BYTES=9223372036854775807`, compose validation passed, the backend was recreated, and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-workspace-disk-admission-smoke -ExpectedWorkspaceMinFreeBytes 9223372036854775807 -VerifyWorkspaceDiskAdmission` passed 4/4. The backend was then restored to the default unbounded threshold; `/actuator/health` returned `{"status":"UP"}`, managed sandbox container cleanup showed no `seahorse-sandbox-*` containers, and PostgreSQL reported zero non-terminal sandbox sessions.

Remaining production hardening is real per-session disk quota enforcement, node-pool scheduling and node-level health, stronger runtime isolation, external scanner engines, richer archive/PDF/binary scanning, and scanner policy/operator UX.

### 2026-07-03 Sandbox Artifact Scanner Policy Visibility Evidence Update

Sandbox Operations now exposes the current artifact scanner policy as a read-only operator surface. `SandboxArtifactScannerPolicy` carries the default local bounded scanner id, mode, fail-closed posture, value-free finding storage flag, content/binary/archive scan limits, prompt-safe and governed download-only media types, blocked/redacted categories, and unsupported production capabilities. `KernelSandboxRuntimeService` returns this from the configured `SandboxArtifactScannerPort`, and `GET /api/sandbox/runtime/artifact-scanner-policy` makes it available under the existing SANDBOX feature gate. The admin Runtime governance panel loads it with runtime health/profile data.

Fresh evidence: focused Java regression passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am "-Dtest=DefaultSandboxArtifactScannerPortTests,KernelSandboxRuntimeServiceTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; frontend capability contracts passed 10/10, and `npm run build` completed with only existing warnings. After Docker pulls recovered through the local proxy path, the full-compose backend rebuilt with in-image Maven `BUILD SUCCESS`, was recreated with the sandbox overlay, and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-scanner-policy-smoke` passed 34/34. Cleanup confirmed no leftover managed sandbox containers, zero non-terminal sandbox sessions, `/actuator/health` returned `{"status":"UP"}`, and the live policy endpoint returned `scannerId=default-local-bounded`.

This closes scanner policy visibility for the current conservative local scanner only. It does not add external virus scanning, recursive archive/container extraction, full PDF rendering/OCR, Office rendering/editing, LibreOffice/Tika conversion, macro execution/parsing, general binary conversion, or mutable scanner policy UX. Those remain follow-up production hardening items.

### 2026-07-04 Sandbox Runtime Node Health Visibility Evidence Update

Sandbox Runtime now exposes the current local runtime as a read-only node health list through `GET /api/sandbox/runtime/nodes`. `SandboxRuntimeNodeHealth` is derived from the existing `SandboxRuntimeHealth` source of truth, so it reuses the current active-session capacity, workspace disk state, engine/workspace availability, container inspection counters, and failure messages without introducing a second health owner. The node id is stable for the single-node runtime shape (`local-container-docker` in the full-Docker sandbox overlay).

The node admission status is intentionally descriptive, not a new scheduler. `AVAILABLE` means the node is usable, `DEGRADED` means runtime health is degraded or container inspection found orphan/failed signals while admission can still proceed, `DISK_LOW` reflects workspace disk admission pressure, `SATURATED` reflects active-session capacity exhaustion, and `UNAVAILABLE` reflects unsupported/unavailable runtime, engine, or workspace state. This slice does not add a node registry, multi-node placement, remote runtime discovery, new session placement behavior, or destructive cleanup behavior.

The admin Sandbox Runtime governance panel now renders runtime node rows beside runtime health, runtime profiles, and scanner policy. The frontend service contract includes `GET /api/sandbox/runtime/nodes`, and the artifact-storage smoke verifies the node response through the real backend API without leaking the configured workspace root.

Fresh evidence: PowerShell parsing passed for `scripts/e2e-sandbox-artifact-storage-smoke.ps1`; focused Java regression passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am "-Dtest=KernelSandboxRuntimeServiceTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel test reported 38/38 passing and Web tests reported 3/3 passing. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, `npm run build` completed with the existing Browserslist/chunk-size warnings, and `git diff --check` passed with only line-ending warnings.

Fresh full-Docker evidence: with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`, compose overlay validation passed, the backend image rebuilt through the local 7890 proxy path with an in-image Maven `BUILD SUCCESS`, and `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --force-recreate backend` recreated `seahorse-backend`. `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-runtime-node-health-smoke` passed 35/35, including the new "Inspect sandbox runtime node health" step. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, `/actuator/health` returned `{"status":"UP"}`, and the live node endpoint returned `local-container-docker|container|docker|HEALTHY|AVAILABLE|activeSessionLimit=0|workspaceDiskStatus=UNBOUNDED`.

This closes single-node runtime node health visibility only. Real node-pool scheduling, distributed runtime registration, node placement policy, per-session disk quota enforcement, stronger runtime isolation, external scanner engines, richer archive/PDF/binary scanning, scanner policy mutation/approval UX, and broader Tool Gateway hardening remain follow-up production work.

### 2026-07-04 Sandbox Artifact TAR Archive Introspection Evidence Update

The default sandbox artifact scanner now performs conservative TAR archive introspection for local `file://` artifacts whose media type is `application/x-tar`. TAR artifacts are governed download-only media: clean archives are copied to object storage and downloadable through artifact APIs, but remain prompt-hidden and are not returned in sandbox-backed tool observations.

The scan is intentionally bounded and non-recursive. It walks 512-byte TAR headers directly, validates header checksums, supports only regular file and directory entries, inspects at most 128 entries, reads at most the first 256 KiB from each regular file, and never extracts archive content to the filesystem. It blocks unsafe paths, non-regular/link/special/extended metadata entries, executable entry extensions or PE/ELF signatures, and embedded PDF active-content markers. Malformed, truncated, or bad-checksum TAR content fails closed as `ARCHIVE_SCAN_ERROR`.

Runtime artifact media detection now maps `.tar` to `application/x-tar`, download filename mapping preserves `.tar`, and the scanner policy reports `application/x-tar` as download-only, binary-signature-scanned, and archive-scanned. This slice does not add `tar.gz`, recursive extraction, PAX/GNU long-name support, ClamAV or another external scanner engine, general archive/container extraction, or general binary conversion.

Fresh evidence: focused Java regression passed 98/98 with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=DefaultSandboxArtifactScannerPortTests,KernelSandboxRuntimeServiceTests,SandboxArtifactTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; `git diff --check` passed with only line-ending warnings; compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; the backend image rebuilt through the local 7890 proxy path with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-tar-archive-smoke` passed 39/39. The smoke verified clean TAR artifacts are `application/x-tar|CLEAN|INTERNAL`, `contentScanned=true`, prompt-hidden, copied to governed object storage, downloadable, and preserve expected content; unsafe TAR artifacts are `application/x-tar|BLOCKED|CONFIDENTIAL` with `ARCHIVE_EXECUTABLE_BINARY`, not copied to object storage, not downloadable, prompt-hidden, and exposed through APIs without raw entry-name or storage-reference leakage. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and `/actuator/health` returned `{"status":"UP"}`.

### 2026-07-04 Sandbox Artifact Scanner Compressed Archive Budget Visibility Evidence Update

Sandbox artifact scanner policy visibility now reports the compressed archive decompression budget. `SandboxArtifactScannerPolicy` includes `maxCompressedArchiveDecompressedBytes`; the default local bounded scanner returns the active TAR.GZ budget of `33554432` bytes; `GET /api/sandbox/runtime/artifact-scanner-policy` exposes it under the existing SANDBOX feature gate; and the admin Sandbox Runtime governance panel includes the budget in the scanner window summary beside the text and archive-entry limits.

This is a policy/API/operator visibility update only. It does not change scanner behavior, TAR.GZ decompression limits, artifact decisions, prompt visibility, download eligibility, mutable scanner policies, recursive extraction, generic gzip scanning, or external scanner integration.

Fresh evidence: focused Java/Web regression passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am "-Dtest=DefaultSandboxArtifactScannerPortTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the selected kernel scanner tests reported 31/31 passing and Web tests reported 3/3 passing. Frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`, `npm run build` completed with only existing Browserslist/chunk-size warnings, compose overlay validation passed, and the backend image rebuilt through the local 7890 proxy path with an in-image Maven `BUILD SUCCESS`. After recreating `seahorse-backend`, `/actuator/health` returned `{"status":"UP"}` and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-scanner-compressed-budget-smoke` passed 43/43 with the live scanner-policy assertion for `maxCompressedArchiveDecompressedBytes=33554432`. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health remained `UP`.

### 2026-07-04 Sandbox Artifact Scanner Full Window Visibility Evidence Update

Sandbox Operations now shows the complete bounded scanner window in the admin Runtime governance panel. The compact summary includes text content scan bytes, binary signature prefix bytes, archive entry count, per-entry archive prefix bytes, and compressed archive decompression bytes, all sourced from `SandboxArtifactScannerPolicy`. The Web controller regression and full-Docker artifact-storage smoke now assert the binary prefix and per-entry archive prefix fields in addition to the existing text, archive entry, and compressed archive budget checks.

This closes a small operator UX gap for the current read-only scanner policy surface only. It does not add mutable scanner policy UX, external scanner engines, recursive extraction, generic gzip scanning, full PDF rendering/OCR, Office rendering/editing, or any scanner behavior change.

Fresh evidence: focused Web regression passed 3/3 with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; frontend capability contracts passed 10/10 via `npm test -- src/services/frontendCapabilityContracts.test.ts`; PowerShell parsing passed for `scripts/e2e-sandbox-artifact-storage-smoke.ps1`; `npm run build` completed with only existing Browserslist/chunk-size warnings; backend health returned `{"status":"UP"}`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-scanner-window-ux-smoke-rerun` passed 43/43 with live scanner-policy assertions for `maxBinarySignatureScanBytes=262144`, `maxArchiveEntryScanBytes=262144`, and the existing text/archive/compressed limits. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health remained `UP`.

### 2026-07-04 Sandbox TAR.GZ Decompression Budget E2E Guard Evidence Update

The artifact-storage smoke now exercises the TAR.GZ decompression byte budget through the real sandbox runtime. The test-created `overbudget-bundle.tar.gz` compresses a TAR entry larger than the scanner's 32 MiB decompressed stream budget, so the scanner reaches the fail-closed `ARCHIVE_SCAN_ERROR` path rather than copying the artifact to governed object storage.

The smoke verifies PostgreSQL state for the artifact as `application/gzip|BLOCKED|SECRET`, scan summary `archive content scan failed`, and redaction summary containing `ARCHIVE_SCAN_ERROR` without the inner entry name. The sandbox artifact list/detail APIs must keep it prompt-hidden, non-downloadable, and free of storage references or `large.bin` leakage. This adds runtime evidence for the existing scanner guard only; it does not change scanner behavior or add generic gzip/recursive archive support.

Fresh evidence: PowerShell parsing passed for `scripts/e2e-sandbox-artifact-storage-smoke.ps1`; `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-targz-budget-failclosed-smoke` passed 44/44 against the local full-Docker backend. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health returned `{"status":"UP"}`.

### 2026-07-04 Sandbox Plain GZIP Fail-Closed E2E Guard Evidence Update

The artifact-storage smoke now also exercises the intentionally unsupported plain `.gz` path through the real sandbox runtime. The test-created `plain-bundle.gz` is detected as `application/gzip`, but because its filename is not `.tar.gz` or `.tgz`, the scanner must fail closed instead of treating generic gzip content as a supported archive format.

The smoke verifies PostgreSQL state for the artifact as `application/gzip|BLOCKED|SECRET`, scan summary `archive content scan failed`, and redaction summary containing `ARCHIVE_SCAN_ERROR` without the compressed content marker. The sandbox artifact list/detail APIs must keep it prompt-hidden, non-downloadable, and free of storage references or compressed content leakage. This is verification hardening only; it does not add generic gzip scanning, recursive extraction, external scanner integration, or new download eligibility.

Fresh evidence: PowerShell parsing passed for `scripts/e2e-sandbox-artifact-storage-smoke.ps1`; backend health returned `{"status":"UP"}` before the run; `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-plain-gzip-failclosed-smoke-rerun` passed 45/45 against the local full-Docker backend, including the new "Verify plain GZIP archive is blocked before object storage" step. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health remained `{"status":"UP"}`.

### 2026-07-04 Sandbox Archive Unsafe Path E2E Guard Evidence Update

The artifact-storage smoke now exercises archive path traversal handling through the real sandbox runtime. The test-created `path-traversal-bundle.zip` contains `../outside.txt`, so the scanner must block it as an unsafe archive entry before any object-storage copy.

The smoke verifies PostgreSQL state for the artifact as `application/zip|BLOCKED|CONFIDENTIAL`, scan summary `unsafe archive entry`, and redaction summary containing `ARCHIVE_UNSAFE_ENTRY` without the raw entry name. The sandbox artifact list/detail APIs must keep it prompt-hidden, non-downloadable, and free of storage references or `outside.txt` leakage. This is verification hardening only; it does not change ZIP/TAR/TAR.GZ parsing, extraction behavior, recursive archive handling, or external scanner integration.

Fresh evidence: PowerShell parsing passed for `scripts/e2e-sandbox-artifact-storage-smoke.ps1`; `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-archive-path-guard-smoke` passed 46/46 against the local full-Docker backend, including the new "Verify path-traversal ZIP archive is blocked before object storage" step. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health returned `{"status":"UP"}`.

### 2026-07-04 Sandbox TAR Unsafe Path E2E Guard Evidence Update

The artifact-storage smoke now also exercises TAR path traversal handling through the real sandbox runtime. The test-created `path-traversal-bundle.tar` contains `../outside.txt`, so the TAR header walker must block it as an unsafe archive entry before any object-storage copy.

The smoke verifies PostgreSQL state for the artifact as `application/x-tar|BLOCKED|CONFIDENTIAL`, scan summary `unsafe archive entry`, and redaction summary containing `ARCHIVE_UNSAFE_ENTRY` without the raw entry name. The sandbox artifact list/detail APIs must keep it prompt-hidden, non-downloadable, and free of storage references or `outside.txt` leakage. This is verification hardening only; it does not change TAR parsing, extraction behavior, recursive archive handling, or external scanner integration.

Fresh evidence: PowerShell parsing passed for `scripts/e2e-sandbox-artifact-storage-smoke.ps1`; `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-tar-path-guard-smoke` passed 47/47 against the local full-Docker backend, including the new "Verify path-traversal TAR archive is blocked before object storage" step. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health returned `{"status":"UP"}`.

### 2026-07-04 Sandbox Artifact TAR.GZ Archive Introspection Evidence Update

The default sandbox artifact scanner now performs conservative TAR.GZ archive introspection for local `file://` artifacts whose media type is `application/gzip` or `application/x-gzip` and whose filename ends with `.tar.gz` or `.tgz`. These artifacts are governed download-only media: clean archives are copied to object storage and downloadable through artifact APIs, but remain prompt-hidden and are not returned in sandbox-backed tool observations.

The scan is intentionally bounded and non-recursive. The scanner decompresses through `GZIPInputStream` with a 32 MiB decompressed-byte budget, then reuses the existing TAR header walker: checksum validation, regular files and directories only, at most 128 entries, at most the first 256 KiB from each regular file, and no filesystem extraction. It blocks unsafe paths, non-regular/link/special/extended metadata entries, executable entry extensions or PE/ELF signatures, and embedded PDF active-content markers. Malformed gzip/TAR content, plain `.gz` names, truncated streams, and decompressed content beyond the budget fail closed as `ARCHIVE_SCAN_ERROR`.

Runtime artifact media detection now maps `.tar.gz` and `.tgz` to `application/gzip`, download filename mapping preserves `.tar.gz`, and the scanner policy reports gzip TAR media as download-only, binary-signature-scanned, and archive-scanned. This slice does not add generic gzip scanning, recursive decompression, PAX/GNU long-name support, ClamAV or another external scanner engine, general archive/container extraction, or general binary conversion.

Fresh evidence: focused Java regression passed 106/106 with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=DefaultSandboxArtifactScannerPortTests,KernelSandboxRuntimeServiceTests,SandboxArtifactTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_WORKSPACE_MOUNT_SOURCE_ROOT=/run/desktop/mnt/host/d/code/seahorse-agent/.seahorse-sandbox-compose`; after one transient TLS EOF while downloading Maven through the local 7890 proxy, container-side proxy diagnostics downloaded and verified the same Maven tarball and the backend image rebuild retry completed with an in-image Maven `BUILD SUCCESS`; `seahorse-backend` was recreated and `/actuator/health` returned `{"status":"UP"}`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-targz-archive-smoke-review` passed 43/43. The smoke verified clean TAR.GZ artifacts are `application/gzip|CLEAN|INTERNAL`, `contentScanned=true`, prompt-hidden, copied to governed object storage, downloadable, and preserve expected content; unsafe TAR.GZ artifacts are `application/gzip|BLOCKED|CONFIDENTIAL` with `ARCHIVE_EXECUTABLE_BINARY`, not copied to object storage, not downloadable, prompt-hidden, and exposed through APIs without raw entry-name or storage-reference leakage. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health remained `UP`.

### 2026-07-04 Sandbox Container Network Fail-Closed P1 Evidence Update

The container sandbox runtime now fails closed when non-browser runtimes request container networking. This closes the gap where `CODE_INTERPRETER` or `FILE_CONVERSION` could pass policy-level `requestedHosts` checks but still receive unrestricted Docker network access because the container adapter has no host-level egress filter for arbitrary Python code. Browser URL mode remains the only container-network path because it has a second in-runtime Playwright route allowlist and the prior `allowedHosts` to `requestedHosts` binding guard.

This is a security boundary hardening step, not a new network feature. It does not add Python host allowlist enforcement, proxy-based egress control, gVisor/Firecracker isolation, or node-level network policy. Those remain production hardening follow-up work.

Fresh evidence: the regression first failed with `expected: FAILED but was: SUCCEEDED`, proving the old behavior started the networked code interpreter. After the fix, the focused regression passed 1/1 via `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests#shouldFailClosedWhenCodeInterpreterNetworkIsRequested" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the full container adapter suite passed 35/35 via `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; and kernel sandbox/tool regressions passed 43/43 via `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxPythonToolPortAdapterTests,KernelSandboxRuntimeServiceTests" test`.

### 2026-07-04 AgentScope A2A Failure Degradation P1 Evidence Update

The AgentScope A2A Tool Gateway adapter now degrades remote invocation failures into a governed tool failure with actionable remote-agent context while avoiding prompt leakage. Connector failures return `invoke_remote_a2a_agent failed for agentName=<name>: ...`; if the upstream exception message repeats the user prompt, the adapter replaces that prompt with `[redacted-prompt]`. Argument validation failures keep the existing direct validation error shape.

This closes a narrow A2A failure-degradation gap only. It does not add a new retry policy, fallback remote-agent selection, live A2A deployment recovery, Studio trace lookup, or real-model SSE equivalence evidence.

Fresh evidence: the new regression first failed because the degraded A2A tool failure lacked `agentName=planner`; after the fix, the focused regression passed 1/1 via `.\mvnw.cmd -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeA2AToolPortAdapterTests#degradesRemoteInvocationFailuresWithoutLeakingPrompt" "-Dsurefire.failIfNoSpecifiedTests=false" test`. The default AgentScope release gate passed via `.\scripts\agentscope-release-gate.ps1`: AgentScope adapter tests reported 107/107 passing, `KernelChatAgentRunStoreTests` reported 18/18 passing, `KernelChatInboundServiceAgentScopeEngineSmokeTests` reported 1/1 passing, `seahorse-agent-bootstrap` packaged successfully, and the script ended with `AGENTSCOPE_RELEASE_GATE=PASS`.

### 2026-07-04 AgentScope Studio Trace Lookup Snapshot P1 Evidence Update

AgentScope Studio-enabled runs now contribute immutable `agentScope` metadata without requiring Nacos config-center to be enabled. The Studio metadata includes `studioTraceEnabled`, `studioUrl`, `tracingUrl`, `project`, `runName`, and Nacos namespace/group context when present. The latest chat `RunContextSnapshot` persists this `agentScope` block in `snapshot_json` and writes `studioUrl`, `tracingUrl`, and a derived `studioTraceUrl` into `trace_context_json` beside the Seahorse `traceId`, so `/api/run-context-snapshots/by-run/{runId}` has the trace lookup evidence needed by reports and inspector surfaces.

This closes the near-term runId-to-Studio-trace snapshot gap only. It does not add dynamic AgentScope Studio SDK runId binding, direct OTEL export, Jaeger/Tempo production integration, live Studio deployment recovery, or real-model SSE equivalence evidence.

Fresh evidence: the new regressions first failed because Studio-only AgentScope auto-configuration produced no `AgentRunMetadataContributor`, `AgentScopeRunMetadataContributor` returned `{}` when config-center was disabled, and the latest chat snapshot did not include `agentScope` or Studio URL fields. After the fix, the focused AgentScope regressions passed 2/2 via `.\mvnw.cmd -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeRunMetadataContributorTests#contributesAgentScopeStudioTraceLookupMetadataWhenStudioIsEnabled,AgentScopeReActAutoConfigurationTests#agentscopeEngineCreatesStudioHookWhenStudioIsEnabled" "-Dsurefire.failIfNoSpecifiedTests=false" test`, and the focused kernel chat snapshot regression passed 1/1 via `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=KernelChatAgentRunStoreTests#shouldPersistAgentScopeTraceLookupMetadataInLatestChatSnapshot" "-Dsurefire.failIfNoSpecifiedTests=false" test`. The default AgentScope release gate also passed via `.\scripts\agentscope-release-gate.ps1`: AgentScope adapter tests reported 108/108 passing, `KernelChatAgentRunStoreTests` reported 19/19 passing, `seahorse-agent-bootstrap` packaged successfully, and the script ended with `AGENTSCOPE_RELEASE_GATE=PASS`.

### 2026-07-04 AgentScope Real-Model SSE Equivalence P1 Evidence Update

The AgentScope run-profile chat path now defaults to `ChatMode.AGENT` when `runProfileId` is present, and the frontend also sends `chatMode=agent` when a selected run profile is used. The full-Docker smoke now calls chat with the selected AgentScope/kernel run profiles, parses SSE frames, and fails unless both streams expose `meta`, `message`, `finish`, `done`, non-empty response text, `stream_event` envelopes, no `error`/`recoverable_error`, matching SSE/snapshot `runId`, and non-empty snapshot `traceId`.

The real Docker run exposed a second root cause after routing was fixed: AgentScope's auto-configured model bridge used `executor.agentName` as the default model id, so the external OpenAI-compatible gateway received `model=seahorse-agent` and returned 503 while the kernel path, which left model id empty, correctly used the configured default chat model. `AgentScopeModelBridge` now preserves a blank default model id, and `AgentScopeCoreAutoConfiguration` no longer passes the agent name as the model fallback.

Fresh evidence: `AgentScopeModelBridgeTests` and `AgentScopeReActAutoConfigurationTests` passed 23/23; `SeahorseChatControllerTests` passed 10/10; `npm run test -- src/stores/chatStore.test.ts` passed 22/22; `.\scripts\agentscope-release-gate.ps1` passed with AgentScope adapter tests 111/111, `KernelChatAgentRunStoreTests` 19/19, bootstrap package success, and final `AGENTSCOPE_RELEASE_GATE=PASS`. After rebuilding `seahorse-agent-bootstrap`, rebuilding the local `seahorse-agent-backend` image, and recreating the full-Docker backend, `.\scripts\e2e-agentscope-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 11/11. The passing run verified AgentScope run `run_331647956607971328`, kernel run `run_331648011163283456`, AgentScope SSE events `agent.timeline,done,finish,message,meta,run_started,step_progress,stream_event`, kernel SSE events `agent.timeline,done,finish,message,meta,run_started,step_finished,step_started,stream_event`, matching snapshot run ids, and trace ids in both snapshots.

### 2026-07-04 Deployment Evidence Gate P2 Evidence Update

The remaining P2 deployment verification item is now represented by `scripts/deployment-evidence-gate.ps1`. The gate aggregates the existing repeatable smokes for S3 adapter switching, Pulsar consume-loop proof, RAG strategy promotion, and Agent rollout promotion. It executes child smokes in isolated PowerShell processes, records each step's exit code/duration, fails closed when a child smoke fails, and fails closed if every step is skipped.

Fresh evidence: PowerShell parsing returned `PSParser OK`; `DeploymentEvidenceGateScriptContractTests` passed 1/1 via `.\mvnw.cmd -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=DeploymentEvidenceGateScriptContractTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; and `.\scripts\deployment-evidence-gate.ps1 -BackendBaseUrl http://127.0.0.1:9090 -FrontendBaseUrl http://127.0.0.1 -Password admin123 -BackendImage seahorse-agent-backend` passed with four successful steps and final `DEPLOYMENT_EVIDENCE_GATE=PASS`. The run verified S3 upload/DB `s3://` storage ref/MinIO object stat/API list-delete/DB soft delete/object removal; Pulsar backend configuration, topic publish/consume/ack counters, PostgreSQL document success, marker chunk materialization, and backend completion log; RAG strategy promotion with recommended template and `RETRIEVAL_STRATEGY_PROMOTED` audit row; and Agent rollout missing-gate failure plus successful full promotion with `AGENT_ROLLOUT_STARTED` and `AGENT_ROLLOUT_PROMOTED` audit rows.

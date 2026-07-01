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

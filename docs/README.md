# Seahorse Agent 文档索引与实现状态

版本：v1.5

最后更新：2026-06-08

本文用于说明 `docs/` 下核心设计文档的当前状态。旧版本文包含大量实施路径和工期估算；截至 2026-06-08，相关能力多数已经落地，因此本文不再作为待办计划使用，而是作为“读什么文档、哪些功能已实现、哪些仍属远期规划”的入口。

## 快速结论

| 模块 | 当前状态 | 代码证据 | 主要文档 |
| --- | --- | --- | --- |
| 工作流可视化 | 已实现 | `WorkflowCanvas`、`SeahorseAgentRunController`、`SeahorseWorkflowVisualizationController`、`KernelWorkflowVisualizationService`、`JdbcWorkflowVisualizationRepositoryAdapter` | `WORKFLOW-VISUALIZATION-DETAILED-DESIGN.md`、`WORKFLOW-BACKEND-DESIGN-SIMPLE.md` |
| Web 体验改进 | 已实现主干 | `frontend/src/components/ai-elements/`、`CodeEditor`、`MessageList` 虚拟滚动、附件上传、Artifact 面板、SSE stream parser | `WEB-IMPROVEMENTS-DETAILED-DESIGN.md`、`WEB-IMPROVEMENTS-BACKEND-SUPPORT.md` |
| 记忆系统修复 | 已实现主干 | `MemoryRefinementDepthGuard`、`HybridMemoryRecallPipeline`、`MemoryCompactionService`、`MemoryAliasResolutionService`、`MemoryGarbageCollectionService` | `MEMORY-FIX-SUMMARY.md`、`docs/aegis/specs/2026-05-25-gemini-memory-alignment-current-state.md` |
| 混合检索与重排 | 已实现主干 | `KeywordSearchChannelFeature`、`RrfFusionPostProcessorFeature`、`RerankPostProcessorFeature`、Lucene/Elasticsearch/JDBC keyword adapter | `docs/zh/content/架构设计/混合检索与重排详细设计.md` |
| 术语映射在线扩展 | 已实现 | `JdbcQueryTermExpansionAdapter`、`QueryTermExpansionPort` 自动配置、`KernelRetrievalEngine` 扩展词透传、`KeywordSearchChannelFeature` 消费扩展词 | `docs/zh/content/架构设计/未来规划审计与剩余设计.md` |

## 推荐阅读顺序

1. 项目现状：先读根目录 `README.md`，了解启动方式、主要入口和当前功能面。
2. 前端体验：读 `WEB-IMPROVEMENTS-DELIVERY-SUMMARY.md`，再按需看 `WEB-IMPROVEMENTS-DETAILED-DESIGN.md`。
3. 工作流可视化：读 `WORKFLOW-VISUALIZATION-DETAILED-DESIGN.md` 和 `WORKFLOW-BACKEND-DESIGN-SIMPLE.md`，但把工期和待办清单视为历史设计背景。
4. 记忆系统：以 `docs/aegis/specs/2026-05-25-gemini-memory-alignment-current-state.md` 为当前状态 source of truth。
5. 未来规划：读 `docs/zh/content/架构设计/未来规划审计与剩余设计.md`，其中 Starter 依赖治理已完成坐标方向翻转：`starter-core` 依赖内部 `autoconfigure` 模块，旧 `starter` 仅作为指向 `starter-core` 的兼容别名，`starter-all` 聚合官方重型 adapter；元数据治理 JDBC 默认端口 Bean 和 SQL owner 已拆到细粒度 repository adapter，旧兼容门面仅在显式开关下创建并只做委托。

## 文档状态

### 当前状态文档

| 文档 | 用途 |
| --- | --- |
| `README.md` | 项目运行、模块、功能入口和常用验证命令 |
| `docs/aegis/specs/2026-05-25-gemini-memory-alignment-current-state.md` | 记忆系统当前实现状态 |
| `docs/zh/content/架构设计/未来规划审计与剩余设计.md` | 过期规划与真实剩余项审计 |
| `docs/zh/content/架构设计/混合检索与重排详细设计.md` | 混合检索当前实现与设计背景 |

### 历史设计文档

以下文档仍有架构参考价值，但其中“待实现”“工期估算”“实施路径”不再代表当前代码状态：

| 文档 | 当前阅读方式 |
| --- | --- |
| `WORKFLOW-VISUALIZATION-DETAILED-DESIGN.md` | 前端工作流可视化设计背景；实现已进入 `frontend/src/components/ai-elements/workflow/` |
| `WORKFLOW-BACKEND-DESIGN-SIMPLE.md` | 后端工作流 API 与数据模型设计背景；实现已进入 Web/Kernel/JDBC 模块 |
| `WORKFLOW-VISUALIZATION-BACKEND-DESIGN.md` | 完整版 DDD 设计参考，不等同当前必做计划 |
| `WEB-IMPROVEMENTS-DETAILED-DESIGN.md` | Web 体验改进设计背景；主干能力已在前端组件和服务中落地 |
| `WEB-IMPROVEMENTS-BACKEND-SUPPORT.md` | 文件上传、Artifact、SSE 等后端支持设计背景 |
| `MEMORY-FIX-SUMMARY.md` | 记忆修复阶段总结；更完整状态以 Aegis current-state spec 为准 |

## 已落地能力摘要

### 工作流可视化

- 前端：`WorkflowCanvas`、`WorkflowStepNode`、`workflowStepsToGraph` 已接入 ReactFlow。
- 后端：`AgentRunWorkflowInboundPort`、`KernelAgentRunWorkflowService`、`WorkflowVisualizationInboundPort`、`KernelWorkflowVisualizationService` 已提供节点/边数据。
- API：`/agent-runs/{runId}/workflow`、`/api/agent-runs/{runId}/workflow`、`/api/workflows/{runId}/visualization`、`/api/workflows/runs/{runId}/visualization` 等路径已存在。
- SSE：`SeahorseWorkflowVisualizationController` 提供 workflow run stream 入口。

### Web 体验改进

- AI 元素组件已集中在 `frontend/src/components/ai-elements/`。
- 代码渲染/编辑已使用 `@uiw/react-codemirror`。
- 聊天列表已使用 `react-virtuoso` 做虚拟滚动。
- 前端支持附件上传、Artifact 解析、Artifact 预览/保存/下载、Agent 运行检查面板。
- 后端已有会话附件、知识库文档上传、ingestion task 上传、Agent Artifact API。

### 记忆系统

- 记忆细化深度保护已由 `MemoryRefinementDepthGuard` 接入。
- 记忆召回主干已覆盖 vector、keyword、graph、RRF 和 rerank。
- 记忆维护已覆盖 compaction、alias resolution、GC 和 review/g治理入口。
- 仍属于可选增强的内容包括生产级图数据库适配器、物理硬删除策略和更高级的 LLM summarizer adapter。

### 术语映射在线扩展

- 管理面：`/admin/mappings`、`KernelQueryTermMappingService`、`JdbcQueryTermMappingRepositoryAdapter` 已维护规则。
- 在线扩展：`JdbcQueryTermExpansionAdapter` 会读取启用且未删除的 `t_query_term_mapping` 规则，支持 exact、fuzzy/prefix、可选 regex。
- 自动配置：`seahorse-agent.query-term-expansion.enabled=true` 默认启用；可配置 `regex-enabled`、`max-rules`、`max-expanded-terms`、`max-source-term-length`、`cache-ttl`。
- 检索消费：`RuleBasedQueryOptimizerPort` 产出的 `expandedTerms` 已透传到 `KernelRetrievalEngine`，并由 `KeywordSearchChannelFeature` 扩展 keyword 查询；用户原始问题和向量查询不被直接污染。

## 专项治理与远期事项

以下事项不是 `docs/README.md` 旧版工作流/Web/记忆功能清单的一部分。已完成项作为状态记录保留，未完成项仍应按专门架构规划推进：

| 事项 | 当前状态 | 参考文档 |
| --- | --- | --- |
| Starter 依赖治理 | 已实现主干；新增 `seahorse-agent-spring-boot-autoconfigure` 承载自动配置实现，`starter-core` 指向 `autoconfigure`，旧 `starter` 仅作为 `starter-core` 兼容别名，`starter-all` 聚合官方重型 adapter，`bootstrap` 默认依赖 `starter-core` 并用 Enforcer 禁止回退到 `starter-all`；当前验证覆盖 core-only context、`starter-all` classpath/自动配置候选和依赖边界，尚未把每个重型 adapter 都放进真实基础设施上下文逐一验收 | `docs/zh/content/架构设计/未来规划审计与剩余设计.md` |
| 元数据治理 JDBC 拆分 | 已实现主干；默认 metadata 端口 Bean 直接使用 schema、dictionary、extraction result、review、quarantine、canonical write、backfill、quality report、schema usage report 等独立 JDBC adapter；旧 `JdbcMetadataGovernanceRepositoryAdapter` 仅在显式兼容开关下创建且已改为纯委托；`JdbcMetadataPortAdapters` 仅作为缺失端口 fallback/兼容辅助保留 | `docs/zh/content/架构设计/未来规划审计与剩余设计.md` |
| Agent Factory UI / A2A / Agent Mesh | 属于产品化与远期治理增强，不应从旧 README 推断为当前 P0 缺口 | `docs/zh/content/架构设计/未实现功能详细设计/` |

## 更新日志

### v1.5 (2026-06-08)

- 标记元数据治理 JDBC SQL/support owner 深层拆分已完成：默认 Spring metadata 端口 Bean 使用独立子域 adapter，旧兼容门面仅在显式开关下创建且只委托。
- 更新剩余项口径：`JdbcMetadataPortAdapters` 保留为 fallback/兼容辅助，不再代表默认执行路径仍复用大 adapter。

### v1.4 (2026-06-08)

- 标记 Starter 坐标方向翻转已完成：新增 `seahorse-agent-spring-boot-autoconfigure` 承载自动配置实现，`starter-core` 依赖该内部模块，旧 `starter` 改为指向 `starter-core` 的兼容 alias。
- 更新 Starter 依赖治理剩余项：不再把 `starter-core`/`starter` 方向翻转列为待办；仅保留全量 starter 在真实基础设施下逐个 adapter 建 Bean 的后续验收增强。

### v1.3 (2026-06-08)

- 补充 `starter-all` 依赖治理状态：新增 `SeahorseAgentStarterAllSmokeTests`，验证官方重型 adapter 类和相关 Spring Boot 自动配置候选可被全量 starter 测试类路径发现。
- 补充 `starter-core` core-only context 验证：新增 `SeahorseAgentStarterCoreContextTests`，验证轻量上下文可提供 local/direct/noop/JDBC 默认能力，且测试类路径不包含官方重型 adapter。
- 曾保留 Starter 依赖治理剩余项：`starter-core`/`starter` 坐标方向尚未彻底翻转；该项已在 v1.4 完成。

### v1.2 (2026-06-08)

- 标记元数据治理 JDBC 默认端口 Bean 已完成细粒度化，新增 `JdbcMetadataGovernanceRepositoryDelegate` 和 `JdbcMetadataPortAdapters` 状态说明。
- 收敛 Starter 依赖治理表述：重型 adapter 传递依赖已隔离，bootstrap 默认依赖已迁移到 `starter-core`，但 `starter-core`/`starter` 坐标语义仍未完全收口。

### v1.1 (2026-06-08)

- 将本文从实施计划索引改为当前实现状态索引。
- 标记工作流可视化、Web 体验改进、记忆系统主干为已实现。
- 补充术语映射在线扩展的 JDBC adapter、自动配置和 keyword 检索消费状态。
- 明确 Starter 依赖治理和元数据治理拆分仍是单独架构治理项。

### v1.0 (2026-06-02)

- 创建旧版文档索引。
- 汇总工作流可视化、Web 改进和记忆修复相关设计文档。

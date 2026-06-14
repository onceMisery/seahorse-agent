# 架构路线图与未来展望

日期：2026-06-14

本文基于当前代码基线规划 Seahorse Agent 后续演进。所有“未来”表述都表示设计方向，不表示当前已经完成；当前运行态以 `docs/architecture/current-code-architecture.md` 为准。

## 愿景

Seahorse Agent 的目标不是只做一个能聊天的 RAG Demo，而是形成一个可证据化、可治理、可持续演进的企业智能体平台：

- 面向知识工作流，能把文档、工具、记忆、画像和任务执行串成闭环。
- 面向企业治理，能把权限、审计、配额、成本、评测、观测和回滚纳入默认工程路径。
- 面向长期智能体，能把用户偏好、组织知识和执行经验沉淀成可解释、可校正、可遗忘的记忆系统。

## 设计原则

| 原则 | 含义 |
|---|---|
| 证据优先 | 能力是否生效，以 API、Trace、表数据、E2E 和运维指标判断，不以类名或文档承诺判断。 |
| 内核稳定 | 领域内核只依赖端口和领域对象，外部 SDK 始终停留在 adapter 层。 |
| 闭环优先 | 新能力必须说明“产生什么数据、谁消费、如何观察、如何失败恢复”。 |
| 默认可降级 | 模型、向量库、搜索、缓存、MQ 和存储都应保留清晰降级或替换路径。 |
| 治理内建 | 记忆、RAG、Agent、Tool、Skill 的权限、审计、配额和评测不是后补功能。 |

## 当前基线

当前代码已经提供：

- Clean Architecture + Ports and Adapters 模块边界。
- 轻量部署和全量部署两条 Docker 路径。
- 真实 RAG 所需的 Milvus、Ollama Embedding、Elasticsearch、RAG Trace。
- 记忆、用户画像、outbox、readiness、maintenance、quality/conflict 管理接口。
- Agent、Tool、Skill、审批、配额、资源 ACL、审计、成本等企业能力接口和前端入口。
- Spring Boot autoconfigure、starter-core、starter-all 的基础边界。

当前仍要谨慎看待的边界：

- 轻量部署不能代表真实 RAG 质量。
- 记忆闭环是否生效必须看运行证据，不能只看默认开关。
- 自训练仍是人工导出边界，不是自动训练闭环。
- S3/MinIO 已具备编排基础，但当前默认存储仍是 local。
- 企业 Agent 的部分页面和接口已有骨架，生产级能力还需要更多 E2E、审计和回滚证据。

## 近期设计（0-4 周）

目标：把本地全量部署、登录、RAG、记忆、画像和文档事实源收敛成稳定的日常开发基线。

| 方向 | 设计内容 | 成功证据 |
|---|---|---|
| 登录与会话稳定性 | 固化 `Authorization: Bearer <token>`、前端 `/api` 代理和后端直连路径差异；补充登录过期诊断。 | 登录后直调 `/knowledge-base` 不再误报过期；Redis/local token 边界清楚。 |
| RAG 冒烟标准化 | 固化知识库创建、上传、分块、向量化、SSE 问答和 Trace 检查步骤。 | `t_knowledge_chunk` 有数据，`/admin/traces` 有 retrieval 节点。 |
| 记忆画像 E2E | 固化个人事实输入、聚合等待或手动 maintenance、profile facts 查询、新会话召回。 | `/memories/readiness` 必需链路有证据，`t_user_profile_fact` 有 active 事实。 |
| 文档事实源收敛 | 持续把旧 RepoWiki 文档中的部署入口、端口、默认模型、账号和 API 路径对齐当前代码。 | stale reference 扫描无旧 compose 和不存在 quick-start 引用。 |
| Embedding 配置清晰化 | 明确全量默认 `nomic-embed-text` + 768；切换模型必须重建向量索引。 | 文档、compose 和排错指南口径一致。 |

近期不扩展新产品边界，优先让已有闭环可重复验证。

## 中期设计（1-3 个月）

目标：从“能跑通”升级到“能评估、能治理、能解释”。

| 方向 | 设计内容 | 成功证据 |
|---|---|---|
| RAG 质量评测 | 建立检索数据集、策略模板、版本对比、空召回率和命中质量指标。 | `/knowledge-base/{kb-id}/retrieval-evaluation-*` 能产出可对比报告。 |
| 入库治理 | 把 PDF/Feishu/OpenAPI 等来源统一到 ingestion pipeline，补齐失败重试、节点日志、隔离和回滚。 | 入库任务节点状态可追踪，失败任务可重放或人工修复。 |
| 记忆质量治理 | 强化冲突检测、质量快照、review feedback、纠错 ledger、低价值记忆清理。 | `/memories/conflicts`、`/memories/quality-snapshots` 和 maintenance run 可解释。 |
| 用户画像可信度 | 画像事实增加来源、置信度、冲突状态和撤销路径；前端提供更清晰的管理入口。 | 用户能查看、修正、停用画像事实，修正影响后续召回。 |
| Agent 生产准备 | Agent 定义、版本、rollout、approval、artifact、cost summary 形成可审核闭环。 | 每次 Agent run 可追踪步骤、审批、产物、成本和恢复动作。 |
| starter-all 验收 | 在真实 Redis、Pulsar、Milvus、Elasticsearch、S3/OpenAI-compatible 环境逐个验证重型 adapter Bean。 | `starter-all` 不只 classpath 冒烟，还能在真实依赖可用时创建关键 Bean。 |

中期重点不是堆功能，而是让 RAG 和记忆结果“可比较、可纠错、可治理”。

## 远期设计（3-6 个月）

目标：把单 Agent / 单知识库能力扩展为面向组织工作流的多 Agent 平台。

| 方向 | 设计内容 | 成功证据 |
|---|---|---|
| Agent Factory UI | 从模板、工具、Skill、权限、预算、评测集生成可发布 Agent。 | 非开发者可创建、验证、发布和回滚 Agent。 |
| Sandbox Runtime | 为工具执行、代码执行、网页抓取和文件处理提供隔离、审计、产物扫描。 | 高风险工具调用默认经过沙箱和审批策略。 |
| Multi-Agent / A2A | 支持 Agent 之间任务委托、交接、上下文包、责任边界和失败恢复。 | 一个复杂任务能拆分给多个 Agent，且每段都有 trace 和 ownership。 |
| Context Pack | 把知识库、记忆、工具权限、用户画像、任务目标打包成可复用上下文资产。 | Agent run 可以声明使用的 context pack，并记录版本。 |
| 企业数据边界 | 强化多租户、RLS、资源 ACL、审计、配额和成本策略的联动。 | 同一接口在不同租户/角色下有可验证的隔离和审计证据。 |
| 存储生产化 | 将 local storage 与 MinIO/S3 之间的切换、迁移、生命周期策略文档化和测试化。 | 附件、产物、入库原文可在 S3 模式下端到端验证。 |

远期重点是“组织级协作”，需要比单次问答更强的任务状态、权限和恢复模型。

## 未来展望（6 个月以上）

未来 Seahorse Agent 可以演进为一个企业智能运营底座：

- **自适应知识运营**：系统自动发现低质量知识、过期知识、冲突知识和高价值知识缺口，驱动人工或 Agent 修复。
- **可解释记忆网络**：用户画像、长期记忆、实体关系和任务历史能形成可视化、可校正、可遗忘的个人/组织记忆。
- **持续评测驱动发布**：RAG 策略、Agent 版本、模型供应商和工具链变更都经过自动评测、灰度和回滚。
- **多模型供应链治理**：Chat、Embedding、Rerank、多模态模型可按成本、质量、延迟和合规策略动态路由。
- **企业 Agent 市场**：Agent、Tool、Skill、Context Pack 可以被发布、订阅、评分、审计和计费。
- **人机协作控制面**：审批、异常接管、任务暂停/恢复、产物验收和审计成为默认交互，而不是事后补救。

这些方向都必须保持同一条底线：任何智能能力都要有证据、有边界、有退出机制。

## 路线图验收方法

每个阶段完成时，至少给出四类证据：

| 证据类型 | 示例 |
|---|---|
| 代码证据 | Controller、端口、adapter、自动配置和迁移脚本的位置 |
| 运行证据 | API 响应、Trace、数据库记录、消息/outbox 状态 |
| 测试证据 | 单元测试、契约测试、Docker E2E 或 Playwright 前端流 |
| 运维证据 | health/readiness、metrics、日志、失败恢复步骤 |

不满足运行证据的能力，只能写成“设计中”或“部分实现”，不能写成“完整闭环”。

# 架构路线图与未来展望

日期：2026-06-22

本文只记录 Seahorse Agent 后续仍需落地、真实验证、产品化或生产硬化的路线。已经完成或已合入 main 的能力不再作为规划展开，统一归档到 [路线图完成情况报告](../analysis/roadmap-completion-status-report.md)。

## 文档边界

- **已完成能力**：不在本文重复描述，只在完成情况报告中保留代码证据、运行证据和测试证据。
- **已合入但缺真实测试**：进入“真实 Test Case 门禁”，不按新功能规划重复建设。
- **代码基座存在但缺产品化/联调/治理闭环**：进入近期或中期路线。
- **只有设计文档、没有代码实现**：进入设计债务路线。

当前已归档的完成基线包括：RAG、记忆与用户画像基座、交互式记忆冲突闭环、Agent 治理基座、消息树、角色卡、运行方案、运行实验、RunContextSnapshot、AgentScope/Nacos A2A 基座、MCP HTTP/stdio 基座、OpenAPI 连接器真实 smoke、管理后台入口与状态页可达性等。详见完成情况报告的“2026-06-22 已完成基线归档”和后续 runtime evidence update。

## 愿景

Seahorse Agent 的目标是形成一个可证据化、可治理、可持续演进的企业智能体平台：

- 知识、记忆、画像、工具和任务执行能形成闭环。
- 权限、审计、配额、成本、评测、观测和回滚成为默认工程路径。
- Agent 能从单次对话演进为可复现、可比较、可发布、可人工接管的组织级工作流。

## 真实 Test Case 门禁

截至 2026-07-01，原 P0“已合入 Agent 控制面真实 test case”已通过 full Docker/API/Playwright smoke 重新验证，并归档到 [路线图完成情况报告](../analysis/roadmap-completion-status-report.md)。本路线图不再展开这些已完成项；后续若有新的“已合入但缺真实测试”能力，再进入本节。

新增门禁的最低标准：每个已合入特性至少有一条正常主路径 test case、一条可复现历史问题或潜在缺陷的 test case、一条降级或错误态 test case。

## 设计债务路线

`docs/design/` 是路线图输入源。设计文档中已经描述、但代码或真实验证证据尚未完成的内容，必须进入下表；已完成内容只保留在完成情况报告中。

| 设计来源 | 未完成规划 | 当前判断 | 路线图归属 | 进入稳定基线的证据 |
|---|---|---|---|---|
| `docs/design/agentscope-production-integration-plan.md` | 完整 Agent Card 删除或上游 registry deregister API、真实模型长链路 SSE 等价验证、直接 OTEL/Studio trace 展示联调、Nacos/AgentScope 精确 config revision | 已有单元、脚本和 A2A E2E 证据，但仍有上游能力和生产联调缺口 | 近期/中期：AgentScope 生产硬化 | release gate 覆盖 shared-secret/tenant-signed live E2E；真实模型 AgentScope 对话与 kernel 对话语义等价；Studio/OTEL trace 能从 runId 反查 |
| `docs/design/agentscope-integration-and-loop-refactor.md` | OpenTelemetry 桥接到 `micrometer-tracing-bridge-otel` 与 OTLP exporter；Studio trace 与 OTel traceId 统一展示 | `KernelAgentLoop` 拆分和 `ReActExecutorPort` 已归档为完成；OTEL 直接导出仍缺生产联调证据 | 中期：可观测性增强 | 开关开启后 Jaeger/Tempo 可看到 `agent.run -> step -> model/tool` span；关闭后回到现有 Micrometer/noop 行为 |
| `docs/design/apix-inspired-feature-evolution-roadmap.md`、`docs/design/apix-inspired-phased-implementation-design.md` | MCP stdio 治理升级：命令 allowlist、runner 隔离、风险自动标记、高风险默认禁用、审批/审计/脱敏、stderr tail 与诊断闭环 | 基础 stdio/HTTP MCP 已归档完成；命令 allowlist、近端 runner 环境隔离、HIGH/需审批默认标记、诊断审批入口、诊断执行网关 fail-closed、OpenAPI enabled operation 动态进入 Tool Gateway/真实 HTTP invoke/audit、Sandbox runtime lifecycle close 透传、Sandbox execution history API/UI、Sandbox artifact scanner/prompt visibility gate、Docker/Podman Code Interpreter 容器 adapter 最小闭环和 stderr/响应脱敏已有证据；剩余 full-compose 容器宿主接入、sandbox-backed tools 与更广的 A2A/跨 provider Tool Gateway 审计仍需补齐 | 近期/中期：Tool Gateway 与 MCP 安全治理 | 高风险 MCP 工具默认不可直接运行；审批通过后真实调用落审计；失败时 UI 展示 stderr/诊断且不影响普通聊天 |
| `docs/design/apix-inspired-phased-implementation-design.md` | Agent Workbench：把消息分支、运行方案、实验、发布门禁、A2A、Studio trace 聚合成一个调试和发布工作台 | Chat Workspace/Inspector 和 Admin 页面仍分散 | 远期：统一 Agent 工作台 | 一个入口完成分支选择、运行方案切换、实验对比、trace/Studio 跳转、发布门禁检查和回滚 |
| `docs/design/apix-inspired-feature-evolution-roadmap.md` | MCP Marketplace、Profile Marketplace、自学习闭环 | Agent Marketplace 基座已归档完成；MCP/Profile 市场和自学习闭环未落地 | 远期：企业资产市场与自学习 | MCP/Profile 能提交、审核、订阅、评分、下架；线上反馈能形成评测样本或策略建议，但不会无人值守改生产配置 |
| `docs/design/apix-inspired-feature-evolution-roadmap.md` | 发布门禁和回归评测从 Agent 扩展到 Run Profile、RAG Strategy、Model Config、Tool/Skill、Ingestion Pipeline | Agent 级 `ProductionGateReport` 已归档完成；统一 `GateResult<T>` 与逐对象 adapter 仍缺 | 中期：统一证据模型与发布门禁 | 所有高风险对象发布前都产出 GateResult，能追溯到 evaluation、trace、audit、cost 和配置快照 |
| `docs/design/apix-inspired-phased-implementation-design.md` | 运行实验报告增强：trial 导出、失败说明、成本/trace/分支对比报告、AgentScope Studio trace 外链 | 运行实验基础已归档完成；报告化和真实对比 test case 仍需补齐 | 近期/中期：运行实验产品化 | 同一会话下多个运行方案的 trial 可导出报告，报告包含输出差异、成本、评分、trace 和对应消息分支 |

## 近期路线（0-4 周）

近期只处理“已合入但未稳定”和“设计已明确但尚未实现”的工作。

| 优先级 | 工作项 | 范围 | 验收 |
|---|---|---|---|
| P1 | MCP stdio 安全治理第一阶段 | 已落地：命令 allowlist、近端 runner 环境隔离、MCP 工具 HIGH/需审批默认标记、blocked stdio stderr 诊断、诊断审批直达入口、MCP 诊断执行网关 fail-closed、OpenAPI enabled operation 动态注册到 Tool Gateway 并具备真实 HTTP invoke/audit、Sandbox runtime close lifecycle 透传与关闭审计、Sandbox execution history API/UI、Sandbox artifact scanner/prompt visibility gate、Docker/Podman Code Interpreter 容器 adapter 最小闭环；剩余：full-compose backend 容器内 Docker/Podman 接入、sandbox-backed tools、更广 A2A/跨 provider Tool Gateway 审计硬化 | 非 allowlist stdio 命令无法启动；高风险 MCP 工具默认进入审批/网关治理 |
| P1 | AgentScope 生产硬化第一阶段 | release gate、真实模型 SSE 等价、A2A 失败降级、Studio trace 反查 | AgentScope 失败不影响 kernel 普通聊天 |
| P2 | 已有部署能力补验证 | S3 adapter 切换、Pulsar 消费闭环、promote rollout 完整流程 | full compose 下有可重复脚本和结果证据 |

## 中期路线（1-3 个月）

中期重点是把已经分散存在的治理能力收敛成统一模型。

| 工作项 | 合并范围 | 为什么合并 | 验收 |
|---|---|---|---|
| 统一 Tool Gateway | MCP stdio/HTTP、OpenAPI、A2A、内置工具、凭证、审批、审计、限额、脱敏 | 这些能力都在解决“工具能不能被安全调用” | 任一工具调用都经过同一风险、凭证、审批、审计和成本链路 |
| 统一 GateResult | Agent ProductionGate、Run Profile gate、RAG Strategy evaluation、Model Config、Tool/Skill、Ingestion Pipeline | 发布门禁不应按对象各写一套报告模型 | 所有高风险对象发布前都返回统一 GateResult，并可追溯证据 |
| 统一资源与访问决策 | ACL、Quota、Audit、Cost、ToolPolicy、SandboxPolicy | `resourceType/resourceId/action/subject` 是控制面、审计和自动化的共同前提 | Agent run/tool/sandbox/marketplace 发布前都能做统一 access decision |
| OTEL/Studio 生产联调 | ObservationPort、Micrometer、OTLP exporter、AgentScope Studio trace、runId/traceId 关联 | 排障需要把 Seahorse run、AgentScope trace 和基础设施 trace 串起来 | Jaeger/Tempo/Studio 能从 runId 互相跳转或反查 |
| Sandbox Runtime 生产化 | Sandbox 策略端口、Docker/Podman Code Interpreter 最小 runtime、后续 gVisor/Firecracker、MIME/内容级扫描、egress 代理、artifact 下载与详情治理 | 高风险工具和代码执行需要隔离执行面 | 高风险执行默认进入 sandbox，产物扫描通过后才能下载或注入上下文 |
| Context Pack 产品化 | Pack Diff、Pack Explain、Pack Retention、handoff 上下文传递 | Multi-Agent 协作依赖可解释的上下文资产 | 每个 context item 都能解释入选原因，并可按租户策略保留/清理 |

## 远期路线（3-6 个月+）

远期不再拆成孤立页面，而按平台能力包推进。

| 能力包 | 合并项目 | 目标 |
|---|---|---|
| Agent Workbench | Chat Workspace、Agent Inspector、运行方案、运行实验、发布门禁、A2A、Studio trace | 一个入口完成调试、对比、发布前检查和回滚 |
| Multi-Agent Mesh | A2A、协作授权矩阵、team DAG、Context Pack、跨 Agent 成本聚合 | 多 Agent 能协作、可授权、可审计、可计费 |
| 企业资产市场 | Agent Marketplace、MCP Marketplace、Profile Marketplace、Context Pack Marketplace | 企业可复用资产能提交、审核、订阅、评分、下架 |
| 自学习闭环 | 运行实验、评测样本、用户反馈、质量报告、策略建议 | 线上反馈转为评测样本或策略建议，但不无人值守改生产配置 |
| 存储生命周期 | S3/local 双写校验、对象 TTL/归档/清理、local→S3 迁移、统一 object reference | 文档、artifact、sandbox 产物和导出任务使用统一对象生命周期 |
| 人机协作控制面 | Approval、Notification、Audit、Run status、Checkpoint、OperationsPanel | 管理员能在一个视图处理审批、失败任务、待验收产物和发布门禁 |

## 路线合并建议

以下原本分散的计划应合并推进：

| 原计划 | 合并后模块 | 原因 |
|---|---|---|
| MCP stdio 治理、OpenAPI 凭证、A2A 工具、内置工具审批 | 统一 Tool Gateway | 风险、凭证、审批、审计、成本链路一致 |
| Agent Gate、Run Profile Gate、RAG 策略发布、模型配置发布、Tool/Skill 发布 | 统一 GateResult | 都是“对象能不能进入生产”的证据模型 |
| Resource ACL、Access Decision、Quota、Audit、Cost | 统一资源与访问决策 | 需要共享 subject/resource/action 语义 |
| Chat Workspace、Agent Inspector、Run Experiment、Run Profile、Studio trace | Agent Workbench | 都服务于调试、比较和发布前检查 |
| Agent Marketplace、MCP Marketplace、Profile Marketplace | 企业资产市场 | 发布、审核、订阅、评分和下架流程相同 |
| Memory quality、交互式冲突处理、Profile fact 修正、Recall evaluation | 记忆质量交互闭环 | 都围绕“记忆能否被用户校正并影响后续召回” |

## 路线图验收方法

每个阶段完成时，至少给出四类证据：

| 证据类型 | 示例 |
|---|---|
| 代码证据 | Controller、端口、adapter、自动配置和迁移脚本的位置 |
| 运行证据 | API 响应、Trace、数据库记录、消息/outbox 状态 |
| 测试证据 | 单元测试、契约测试、Docker E2E 或 Playwright 前端流 |
| 运维证据 | health/readiness、metrics、日志、失败恢复步骤 |

不满足运行证据的能力，只能写成“设计中”或“部分实现”，不能写成“完整闭环”。完成后必须移动到 [路线图完成情况报告](../analysis/roadmap-completion-status-report.md)，并从本文规划主体移除。

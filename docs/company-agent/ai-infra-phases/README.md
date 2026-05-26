# Seahorse Agent 企业级 AI Infra 分阶段落地文档索引

本目录把 `Seahorse Agent 企业级 AI Infra 分阶段开发规划.md` 拆成可执行的阶段文档。每份文档面向开发者，固定包含目标、模块边界、领域模型、表结构、API、任务切片、测试命令、验收标准和风险控制。

## C 端 Web 范围声明

本目录是历史企业级 AI Infra 规划，不再作为 C 端 Web Agent 的默认完成标准。C 端当前基线以 `../c-web-ai-infra-phases/` 为准：云端可治理 Agent Runtime、Web 聊天内任务时间线、来源、产物、用户确认、个人数据安全、质量和成本闭环。

以下能力仅保留为专业版或企业版远期扩展，默认不得进入 consumer-web 主线：

- 本地安装 Agent、宿主机 shell、本地文件系统读写、本地 sandbox。
- 用户自定义任意 MCP server 并直接执行。
- 企业 OpenAPI/MCP connector、secret 管理和写操作工具。
- Local Agent-as-Tool、A2A、Remote Agent、Agent Mesh。

Phase 5 和 Phase 7 的连接器、沙箱、A2A、mesh 内容只作为企业扩展参考；默认实现必须通过 ProductMode/AdvancedFeatureGate 隔离，并在前端用户侧隐藏。

建议按顺序执行：

| 阶段 | 文档 | 目标 |
| --- | --- | --- |
| Phase 0 | [00-architecture-baseline.md](./00-architecture-baseline.md) | 固化 AI Infra 架构边界、术语、包路径和测试基线 |
| Phase 1 | [01-agent-registry-run-store.md](./01-agent-registry-run-store.md) | 建立 Agent Registry、Agent Version、Run Store |
| Phase 2 | [02-tool-gateway-policy-engine.md](./02-tool-gateway-policy-engine.md) | 所有工具调用进入 Tool Gateway 与 Policy Engine |
| Phase 3 | [03-durable-runtime-hitl.md](./03-durable-runtime-hitl.md) | Agent run 支持持久执行、checkpoint、审批中断与恢复 |
| Phase 4 | [04-context-db-resource-acl.md](./04-context-db-resource-acl.md) | RAG/Memory/ACL/Provenance 统一成 Context DB |
| Phase 5 | [05-connectors-credentials-sandbox.md](./05-connectors-credentials-sandbox.md) | MCP/OpenAPI/凭据/沙箱安全接入 |
| Phase 6 | [06-agent-factory-studio.md](./06-agent-factory-studio.md) | 业务 Agent 模板、派生、发布门禁与 Agent Studio |
| Phase 7 | [07-multi-agent-a2a-mesh.md](./07-multi-agent-a2a-mesh.md) | 本地 multi-agent、A2A 和 Agent Mesh 治理 |
| Phase 8 | [08-production-hardening.md](./08-production-hardening.md) | 评估、红队、审计、成本、SRE 与企业试点准入 |
| 当前剩余方案 | [20-unfinished-phase-implementation-pack.md](./20-unfinished-phase-implementation-pack.md) | 最新实施入口：第 21 节当前代码面校准后收敛到 Phase 8D kernel/JDBC/Web/starter 闭环与最终完成审计；Phase 4/5/7/8B/8C 作为回归依赖 |
| 历史剩余方案 | [09-unfinished-phase-design-development-plans.md](./09-unfinished-phase-design-development-plans.md) | 历史分析与执行卡记录，第 19 节保留为上一版入口 |

## 执行原则

1. Phase 0-3 是安全行动能力的地基，必须优先完成。
2. Phase 4 之前，不应把企业敏感数据广泛暴露给业务 Agent。
3. Phase 5 之前，不应把真实写操作系统接入 Agent 自动执行链路。
4. Phase 7 之前，不应把 Seahorse 对外宣传为多 Agent 平台。
5. 每个阶段完成时必须有测试、数据库迁移、API 文档和回滚策略。

## 第一批推荐实施 Epic

1. Agent Run 持久化闭环：见 Phase 1。
2. Tool Gateway 最小可用版：见 Phase 2。
3. Approval 中断与恢复：见 Phase 3。
4. Agent Definition 与发布版本：见 Phase 1 + Phase 6。
5. ContextPack 与资源 ACL：见 Phase 4。
6. 当前未完成阶段的下一批实施顺序：见 `20-unfinished-phase-implementation-pack.md`。

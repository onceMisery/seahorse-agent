# Seahorse Agent 企业级 AI Infra 架构基线

本文档固化 Seahorse Agent 向企业级 AI Infra 演进的第一层架构边界。后续业务 Agent、工具接入、审批、审计、上下文和多 Agent 编排都必须在这些 owner 边界内扩展。

## 分层与 Owner

| 层 | Canonical owner | 职责 | 不负责 |
| --- | --- | --- | --- |
| Agent Registry | `kernel.domain.agent.definition`、`kernel.application.agent.registry` | Agent 定义、版本、发布、禁用、派生来源 | 具体业务流程 |
| Agent Runtime | `kernel.domain.agent.runtime`、`kernel.application.agent.runtime` | run、step、cancel、checkpoint、interrupt、resume 的事实来源 | 工具内部实现 |
| Tool Gateway | `kernel.domain.agent.tool`、`kernel.application.agent.tool` | 工具目录、参数校验、策略决策、审计、脱敏 | 绕过策略执行业务工具 |
| Policy Engine | `kernel.domain.agent.policy` | RBAC/ABAC、risk rule、allow/deny/approval_required | UI 展示 |
| Approval | `kernel.domain.agent.approval` | Human-in-the-loop 请求、审批结果、恢复信号 | 单一业务审批表单 |
| Context DB | 后续 `context` owner | RAG、Memory、Tool result 的 ContextPack、来源、ACL、预算 | 模型生成本身 |
| Agent Identity | 后续 identity owner | Agent workload identity、用户委托、凭据绑定 | 用户密码认证 |
| Audit Ledger | 后续 audit owner | 关键事件 append-only 与审计查询 | 普通调试日志 |

## 术语

- Agent Definition：一个业务 Agent 的稳定定义，包含 owner、风险、状态、类型和派生来源。
- Agent Version：Agent 的不可变发布快照，包含 instructions、工具集、模型策略、记忆策略和 guardrail 配置。
- Agent Run：一次 Agent 执行实例，必须可查询、可审计、可取消。
- Agent Step：run 内部的一步执行，包括模型 turn、工具调用、审批、handoff 或 checkpoint。
- Tool Gateway：所有工具调用的统一入口；业务 Agent 不允许直接绕过它调用 `ToolPort`。
- Policy Decision：对一次访问或工具调用的 `allow`、`deny`、`approval_required` 决策。
- ContextPack：提供给模型的上下文包，含来源、ACL、预算和敏感级别。
- Agent Identity：Agent 自身身份，可与用户委托组合访问资源。
- Audit Event：面向合规和追责的关键事件，不等同普通日志。

## 边界规则

1. `KernelAgentLoop` 保留为现有 ReAct executor，不再作为平台级 Agent Runtime 的唯一事实来源。
2. Phase 1 新增的 `AgentRun` 和 `AgentStep` 是后续 Tool Gateway、Approval、Audit、ContextPack 的挂载点。
3. Phase 1-3 完成前，`ChatMode.AGENT` 不建议接入真实写操作系统。
4. Phase 2 完成后，新 Agent Runtime 中不得出现绕过 Tool Gateway 的工具调用。
5. Phase 4 完成前，不应把企业敏感数据广泛暴露给业务 Agent。
6. 新实现遵循现有六边形架构：kernel 定义领域、服务和 port；adapter-web、adapter-repository-jdbc、starter 只做适配和装配。

## 设计原则约束

- 组合优于继承：Agent 能力通过 port、service、adapter 组合，不通过继承树扩展。
- 不使用魔法值：状态、类型、风险、触发来源使用 enum 或具名常量。
- DRY：重复规则上移到领域对象或小型私有方法，避免复制业务判断。
- SRP：领域对象维护不变量，应用服务编排规则，Repository 负责持久化契约。
- OCP：后续 Tool Gateway、JDBC、Web、前端通过新 adapter 扩展，不修改领域不变量。
- LSP：port 的替代实现必须遵守相同的状态与不可变版本语义。
- ISP：Definition、Run、Tool、Policy、Approval 分成小接口，不做大一统 AgentService。
- DIP：kernel 依赖 port 抽象，不依赖 Spring、JDBC 或 Web。
- KISS：每个阶段只落当前验收需要的最小闭环。
- YAGNI：不在 Phase 1 引入工作流引擎、复杂 JSON 类型或远程 Agent mesh。

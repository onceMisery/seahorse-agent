# Phase 0：架构基线与边界固化

## 1. 阶段目标

把 Seahorse Agent 的长期方向固化为企业级 AI Infra，而不是单一聊天应用、单一业务 Agent 或临时工具调用框架。Phase 0 的价值在于建立后续所有开发的架构边界，避免 Agent Runtime、Tool Gateway、MCP、RAG、Memory、审批和审计各自形成重复 owner。

## 2. 当前基线

已有基础：

- `seahorse-agent-kernel`：RAG、Memory、AgentLoop、Trace 的核心实现。
- `seahorse-agent-adapter-web`：后台管理、聊天、认证、SSE。
- `seahorse-agent-adapter-mcp-http`：远程 MCP 工具发现与调用雏形。
- `seahorse-agent-adapter-repository-jdbc`：JDBC 持久化。
- `seahorse-agent-adapter-observation-micrometer`：观测适配。

主要问题：

- Agent 只是 `ChatMode.AGENT` 的请求分支，不是平台实体。
- `KernelAgentLoop` 同时承担运行循环、工具暴露、工具执行和上下文注入。
- 工具调用没有统一策略网关。
- 审批只存在于 memory/metadata 局部流程，不是 Agent Runtime 通用能力。
- Trace 适合调试，不等同于审计账本。

## 3. 目标架构边界

### 3.1 平台 owner

| Owner | 职责 | 不负责 |
| --- | --- | --- |
| Agent Registry | Agent 定义、版本、发布、禁用、派生来源 | 具体业务逻辑 |
| Agent Runtime | run、step、checkpoint、interrupt、resume、cancel、worker | 具体工具实现 |
| Tool Gateway | 工具目录、参数校验、策略决策、调用审计、脱敏 | 业务工具内部逻辑 |
| Policy Engine | RBAC/ABAC、risk rule、approval required、deny reason | UI 展示 |
| Context DB | RAG/Memory/Tool result 的上下文包、来源、ACL、预算 | 模型生成 |
| Agent Identity | Agent workload identity、用户委托、凭据绑定 | 用户密码认证 |
| Audit Ledger | 关键事件 append-only、审计查询 | 普通调试日志 |
| Evaluation | 回归集、安全评估、发布门禁 | 线上流量调度 |

### 3.2 包路径约定

新增代码优先使用以下包路径：

```text
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/definition
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/runtime
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/tool
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/policy
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/approval
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/registry
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/runtime
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/agent/tool
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/outbound/agent
```

现有 `kernel.application.agent.KernelAgentLoop` 保留，但逐步退为 `ReactAgentExecutor` 的内部执行策略。

## 4. 术语表

| 术语 | 定义 |
| --- | --- |
| Agent Definition | 一个业务 Agent 的稳定定义，包含 owner、风险、默认策略、状态 |
| Agent Version | Agent 的不可变版本快照，包含 instructions、工具集、模型策略、记忆策略 |
| Agent Run | 一次 Agent 执行实例，必须可查询、可审计、可取消 |
| Agent Step | run 内部一步执行，包括模型 turn、工具调用、审批、handoff |
| Checkpoint | run 可恢复状态快照 |
| Interrupt | Runtime 主动暂停等待外部动作，如审批或人工输入 |
| Tool Gateway | 所有工具调用的统一入口 |
| Policy Decision | 对一次访问或工具调用的 allow/deny/approval_required 决策 |
| ContextPack | 提供给模型的上下文包，含来源、ACL、预算、敏感级别 |
| Agent Identity | Agent 自身身份，可与用户委托组合访问资源 |
| Audit Event | 审计事件，不可等同普通日志 |

## 5. 开发任务切片

### Task 0.1：新增架构基线文档

文件：

- `docs/company-agent/Seahorse Agent 企业级 AI Infra 架构基线.md`

步骤：

1. 写入 AI Infra 分层、owner、术语表。
2. 明确 `KernelAgentLoop` 未来退为 executor。
3. 明确业务 Agent 不允许绕过 Tool Gateway。
4. 明确 Phase 1-3 前 Agent mode 不建议接真实写操作。

验收：

```powershell
rg -n "Agent Runtime|Tool Gateway|ContextPack|Agent Identity" docs/company-agent
```

### Task 0.2：建立包路径空壳与 README

文件：

- `seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/definition/package-info.java`
- `seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/runtime/package-info.java`
- `seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/tool/package-info.java`
- `seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/policy/package-info.java`
- `seahorse-agent-kernel/src/main/java/.../kernel/domain/agent/approval/package-info.java`

步骤：

1. 为每个包写一句职责说明。
2. 不新增业务逻辑。
3. 运行编译确认包路径合法。

验收：

```powershell
./mvnw -pl seahorse-agent-kernel -am test
```

### Task 0.3：记录测试基线

文件：

- `docs/company-agent/Seahorse Agent 企业级 AI Infra 测试基线.md`

步骤：

1. 记录当前推荐执行的 Maven 测试命令。
2. 记录前端构建命令。
3. 标记任何当前已知失败，不在 Phase 0 修复。

验收：

```powershell
./mvnw -pl seahorse-agent-tests -am test
cd frontend; npm run build
```

## 6. 退出条件

1. 文档中有明确 AI Infra 分层和 owner。
2. 所有后续新增 Agent 代码有固定包路径。
3. 团队接受 `KernelAgentLoop` 只是 executor，不再是平台边界。
4. 测试基线已记录。

## 7. 风险控制

- 不在 Phase 0 重构现有业务逻辑。
- 不移动现有类，避免扩大 diff。
- 不引入外部工作流引擎。
- 不新增数据库表。

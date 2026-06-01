# 《开源项目技术亮点吸收与改进方案》二次 Review 报告

创建日期：2026-05-27

Review 对象：`docs/company-agent/开源项目技术亮点吸收与改进方案.md`

基线文档：

- `docs/company-agent/c-web-ai-infra-phases/README.md`
- `docs/company-agent/c-web-ai-infra-phases/01-web-task-runtime.md`
- `docs/company-agent/c-web-ai-infra-phases/02-research-web-agent.md`
- `docs/company-agent/C端WebAIInfra后续开发交接文档.md`（已删除，内容合并至 `docs/HANDOVER.md`）

## 1. 总体结论

当前文档已经比上一版更接近 C 端 Web AI Infra 主线：明确了 Seahorse 是面向 C 端 Web 的云端 Agent 产品，也把企业级 Agent mesh、任意 MCP、工作流 Studio 放到了高级扩展边界。

但是，这份文档仍不建议直接作为后续开发执行计划。最大问题是：它一边声明不引入任意控制流 DSL，一边又把 Temporal、Workflow、YAML 技能/模板加载放进 Phase 2/Phase 3 的核心路径。这与现有 C 端 Web AI Infra 设计基线中的 KISS/YAGNI 约束冲突，也会把当前阶段从“Web 任务体验 + bounded research orchestrator”扩大成“持久化工作流平台建设”。

Review 结论：**需要修订后才能作为参考文档；不能作为当前阶段开发排期依据。**

## 2. 已改善点

### 2.1 产品定位已有明显收敛

文档开头明确写出当前主线是 C 端 Web 云端 Agent 产品，而不是企业级本地 Agent、Agent mesh 或工作流平台。这一点与 `c-web-ai-infra-phases/README.md` 的主线一致。

### 2.2 文档性质有所澄清

文档将自身定位为“外部项目能力吸收池 + 适配决策”，而不是直接工程设计。这能降低把开源项目形态照搬进 Seahorse 的风险。

### 2.3 部分高级能力已被延后

MCP 会话池化、Agent-as-Tool、动态 UI 注册表等能力被移动到 Phase 4+ 或企业扩展边界，方向是正确的。

### 2.4 安全边界已有补强

文档已经强调用户不能自定义任意 MCP、shell、文件系统，所有 action 必须有 canonical domain owner。这符合 C 端 Web 场景的安全基线。

## 3. 阻塞问题

### 3.1 Temporal 不应进入 Phase 2 默认路线

文档多处把 Temporal 作为 Phase 2 的基础设施引入，例如：

- “引入 Temporal 作为持久化执行引擎，替代自研 checkpoint”
- “Temporal 持久化研究步骤编排”
- “ResearchWorkflow / ResearchActivities / TemporalResearchAdapter”
- “TaskTemplate 绑定 Workflow”
- “Phase 2 完成标准：Temporal checkpoint 可恢复”

这与当前设计基线冲突：

- `c-web-ai-infra-phases/README.md` 明确要求第一轮实现不引入工作流引擎、远程 Agent mesh 或复杂 JSON DSL。
- `02-research-web-agent.md` 明确要求 Research Web Agent 使用 bounded steps，并映射到 `AgentStep`，不引入工作流引擎。
- `docs/HANDOVER.md` 明确建议使用 `ResearchRunOrchestrator`，并将通用工作流引擎列为当前阶段反模式。

Temporal 本身不是错误技术选项，但它不是当前阶段的最小充分路径。它会引入新的部署组件、worker 生命周期、重试幂等、版本迁移、运维监控、故障恢复和数据一致性问题。当前文档没有给出这些成本的 ADR、风险模型或迁移方案。

处理建议：

- 将 “Mastra 工作流引擎：采纳基础设施” 改为 “延后评估，需 ADR”。
- Phase 2 使用 `ResearchRunOrchestrator` + `AgentRun` + `AgentStep` + `AgentCheckpoint`。
- Temporal 只保留为 Phase 4+ 的平台基础设施候选项，触发条件是：已有 bounded orchestrator 无法满足长任务恢复、并行扇出、可观测性或跨进程 worker 诉求。

### 3.2 YAML 技能/模板加载仍存在 DSL 和插件面风险

文档虽然声明“不引入任意控制流 YAML DSL”，但后续仍提出：

- `skills/deep-research.yaml`
- 新增技能只需添加 YAML 文件，无需重启服务
- 模板库使用 YAML
- 新增模板只需添加 YAML，无需改代码

这会把 YAML 从“配置格式”演变成事实上的 DSL/plugin surface。尤其在 C 端 Web 场景下，如果 YAML 可以决定技能、工具、模板、工作流绑定或参数解析，那么它必须具备：

- 类型化 schema
- 版本兼容策略
- 审核发布流程
- 权限模型
- owner 归属
- 回滚机制
- 租户隔离
- 审计记录

当前文档没有定义这些治理边界。

处理建议：

- 当前阶段不做“添加 YAML 即新增技能/模板”的热加载。
- 任务模板优先使用类型化 `TaskTemplate`、枚举 ID、具名常量和受控 DB/config。
- 模板变更走发布或后台审核流程，不允许用户侧或非受控文件系统扩展。
- YAML 如需保留，只能作为内部 seed/import 格式，不作为运行时扩展协议。

### 3.3 Phase 顺序仍然漂移

现有 C 端 Web AI Infra 的合理顺序应是：

1. Phase 1：Web task runtime，让用户看到 run、step、artifact、source、approval、event replay。
2. Phase 2：Research Web Agent，基于 bounded orchestrator 打通搜索、抓取、证据、引用、报告。
3. Phase 3：附件、多模态、反馈/eval、个性化、运营和风控。
4. Phase 4+：高级扩展边界，例如 MCP pool、Agent-as-Tool、Temporal、动态 UI registry。

当前文档却把 Temporal 放进 Phase 2，把评测最小闭环也放进 Phase 2，同时又把部分 UX/高级能力放到 Phase 4+。这导致阶段重心倒置：重基础设施先行，用户可感知闭环反而被稀释。

处理建议：

- Phase 2 只保留 Research Web Agent 的必要闭环。
- 反馈/eval 如需提前，只能做非常小的“用户反馈入库 + 人审候选”能力，不应扩大为完整评测平台。
- Temporal、动态 Workflow、模板 Workflow 绑定全部移到 Phase 4+ 或 ADR 阶段。

### 3.4 新基础设施缺少运维、迁移和一致性分析

文档提出用 Temporal 替代自研 checkpoint，但没有说明：

- 当前 `AgentRun/AgentStep/AgentCheckpoint` 如何迁移。
- 已有 run 的恢复语义是否变化。
- Temporal retry 与业务幂等如何约束。
- Activity 失败与 `AgentStep` 状态如何映射。
- cancel/signal/query 与 Web SSE 事件如何一致。
- Temporal worker 如何部署、扩缩容和监控。
- Temporal 不可用时用户任务如何降级。
- 多版本 Workflow 如何兼容线上未完成任务。

这些不是实现细节，而是决定能否上线的基础契约。缺失这些内容时，不应把 Temporal 放进执行计划。

处理建议：

- 如果未来评估 Temporal，必须先产出 ADR。
- ADR 至少覆盖部署模型、数据归属、状态映射、幂等约束、版本兼容、降级策略和迁移策略。
- 在 ADR 通过前，继续以 kernel 内的 port 和应用服务编排为唯一执行模型。

### 3.5 “研究步骤” 与 “通用工作流平台” 的边界仍不清晰

文档使用了 `Workflow`、`Activity`、parallel activities、模板绑定 Workflow 等术语。这些概念会自然推动系统变成通用工作流平台，而不是 C 端 Web research agent。

当前阶段真正需要的是：

- 固定 step 枚举。
- 每个 step 写入 `AgentStep`。
- 每个 step 可被前端 timeline 展示。
- 搜索、抓取、分析、写作、校验都通过明确 port 接入。
- 失败、取消、重试、恢复语义保持在 Seahorse 自己的领域对象内。

处理建议：

- 用 `ResearchRunOrchestrator`、`ResearchStepType`、`ResearchStepHandler` 等命名替代 Workflow 叙事。
- 保持 bounded steps，不支持用户自定义 DAG、条件分支、并行拓扑。
- 后续如果需要 parallel fan-out，也应先在 orchestrator 内以受控 handler 实现，而不是直接引入通用 workflow abstraction。

### 3.6 Generated UI / Action Renderer 边界仍需收紧

文档对 UI 生成和 Action Renderer 的处理已有改善，但仍需要更明确：

- 当前阶段只允许内置 action renderer。
- 每个 action 必须绑定 canonical domain owner。
- action 参数必须类型化校验。
- renderer 不允许远程动态注册。
- 前端只渲染后端批准的 action descriptor，不解释任意 schema。

处理建议：

- 将动态 UI registry、第三方 renderer、远程 schema 解释全部放到 Phase 4+。
- Phase 1/2 只实现固定内置 renderer，例如 approval、artifact、source、research timeline。

### 3.7 MCP / Playwright / 有状态工具边界仍需更明确

文档已将 MCP 会话池化移动到 Phase 4+，这是正确方向。但仍应明确：

- C 端用户不能配置 MCP server。
- Playwright 这类有状态工具默认关闭。
- 企业/admin 管理的 MCP 也必须经过 allowlist、credential owner、审计和隔离。
- MCP pool 不属于当前 AI Infra 完成标准。

处理建议：

- 在文档中删除任何容易被理解为“C 端用户可接入任意 MCP/浏览器自动化工具”的表述。
- MCP 只作为企业扩展边界，并依赖单独 ADR。

## 4. 分项审查矩阵

| 文档主张 | Review 判断 | 处理建议 |
| --- | --- | --- |
| C 端 Web 云端 Agent 产品定位 | 可保留 | 作为全文前置约束保留 |
| 企业级 Agent mesh / 任意 MCP / 工作流 Studio 只作为高级扩展 | 可保留 | 增加“默认关闭、需 ADR、非当前完成标准” |
| Deer Flow 研究步骤清晰度 | 可采纳 | 映射到 `ResearchRunOrchestrator` 和 `AgentStep` |
| 通过 Temporal Activity 编排研究步骤 | 不建议当前采纳 | 改为 bounded orchestrator；Temporal 延后 |
| Mastra 工作流引擎“采纳基础设施” | 阻塞 | 改为“Phase 4+ 评估项，需 ADR” |
| Temporal 替代自研 checkpoint | 阻塞 | 当前继续使用 `AgentRun/AgentStep/AgentCheckpoint` |
| TaskTemplate 绑定预建 Temporal Workflow | 不建议 | 改为 `TaskTemplate` + 类型化 profile/handler enum |
| 新增技能只需添加 YAML，无需重启 | 高风险 | 改为受控发布/后台审核/类型化配置 |
| 新增模板只需添加 YAML，无需改代码 | 高风险 | YAML 只能作为内部 seed/import，不作为运行时 DSL |
| Phase 2 引入评测最小闭环 | 需降级 | 只做用户反馈入库或人审候选；完整 eval 放 Phase 3 |
| MCP 会话池化放 Phase 4+ | 可保留 | 补充默认关闭、admin-only、allowlist、审计 |
| Agent-as-Tool 需 ADR | 可保留 | 保持 Phase 4+ |
| 动态 UI registry 放 Phase 4+ | 可保留 | Phase 1/2 只做内置 renderer |

## 5. 建议改写后的阶段路线

### Phase 1：Web Task Runtime 补齐

目标：让已有 AgentRun、AgentStep、Checkpoint、Approval、ContextPack 成为用户可感知的 Web 任务体验。

范围：

- event envelope
- `lastEventSeq` replay
- snapshot fallback
- timeline
- approval query/decision
- artifact/source panel
- 内置 action renderer
- 前端断线重连恢复

不做：

- Temporal
- workflow engine
- 用户自定义 DSL
- 任意 MCP
- 本地文件系统或浏览器自动化能力

### Phase 2：Research Web Agent

目标：实现 C 端 Web 场景的研究任务最小闭环。

范围：

- `ResearchRunOrchestrator`
- 固定 `ResearchStepType`
- search/fetch/read/analyze/write/verify
- source citation
- report artifact
- step timeline
- 失败、取消、重试、恢复映射到 `AgentRun/AgentStep/Checkpoint`

不做：

- Temporal Workflow
- 用户定义 DAG
- YAML Workflow
- child agent identity
- MCP session pool

### Phase 3：Web 场景增强与运营闭环

目标：把 C 端 Web Agent 从“可运行”推进到“可运营、可评估、可个性化”。

范围：

- 附件解析和上下文装配
- 用户反馈入库
- eval dataset 候选管理
- 简单离线回归
- model routing 最小策略
- abuse/rate limit
- memory/context pack 增强

不做：

- 任意插件市场
- 用户自定义工具
- 复杂 workflow studio

### Phase 4+：高级扩展边界

目标：在 C 端 Web 主线稳定后，按 ADR 引入高复杂度平台能力。

候选项：

- Temporal/Inngest 等持久化执行引擎评估
- MCP session pool
- Agent-as-Tool
- 企业连接器
- dynamic UI registry
- 多 agent 协作
- admin-managed tool marketplace

准入条件：

- 有明确用户价值和线上瓶颈证据。
- 有 ADR。
- 有安全、运维、迁移、回滚和成本分析。
- 不破坏 kernel 依赖 port 抽象的架构边界。

## 6. 必须删除或降级的表述

| 当前表述 | 建议改写 |
| --- | --- |
| “引入 Temporal 作为持久化执行引擎，替代自研 checkpoint” | “Temporal 作为 Phase 4+ 候选执行引擎，需 ADR；当前阶段继续使用 `AgentRun/AgentStep/AgentCheckpoint`” |
| “Mastra 工作流引擎：采纳基础设施” | “Mastra 工作流引擎：延后评估，当前不采纳” |
| “通过 Temporal Activity 编排固定 research steps” | “通过 `ResearchRunOrchestrator` 编排固定 research steps，并写入 `AgentStep`” |
| “TaskTemplate 配置参数 + 绑定预建 Temporal Workflow” | “TaskTemplate 配置参数 + 绑定类型化 task profile/handler enum” |
| “新增技能只需添加 YAML 文件，无需重启服务” | “新增技能需走受控配置或发布流程，并经过 schema 校验、权限审核和审计” |
| “新增模板只需添加 YAML，无需改代码” | “模板优先使用类型化配置；YAML 仅可作为内部 seed/import 格式” |
| “Phase 2：Temporal 基础设施部署 + SDK 集成” | “Phase 2：ResearchRunOrchestrator + search/fetch/evidence/report 最小闭环” |
| “Phase 2 完成：Temporal checkpoint 可恢复” | “Phase 2 完成：research run 可通过现有 checkpoint/snapshot/event replay 恢复” |
| “步骤级并行通过 Temporal parallel activities 实现” | “步骤级并行暂不作为当前目标；如需 fan-out，先在 bounded orchestrator 内受控实现” |

## 7. 可保留并优先推进的内容

### 7.1 Deer Flow 的上下文工程思想

可以吸收：

- 研究过程分阶段。
- 搜索结果与来源分离。
- 报告生成必须保留 citation。
- 中间过程对用户可见。
- 长任务需要恢复体验。

需要适配：

- 不照搬子 Agent mesh。
- 不引入用户可配置 MCP。
- 不把 research step 变成通用 workflow。

### 7.2 Mastra 的 eval 思路

可以吸收：

- 用户反馈进入候选数据集。
- 人审后进入 eval dataset。
- 回归评测用于模型或 prompt 调整。

需要适配：

- Phase 2 只保留最小反馈闭环。
- 完整 eval framework 放 Phase 3。
- 不因为 eval 引入重型 workflow infra。

### 7.3 ChatDev 的模板化思路

可以吸收：

- 任务模板帮助用户降低输入成本。
- 模板参数类型化。
- 模板与能力边界绑定。

需要适配：

- 模板不是 YAML workflow。
- 模板不能表达任意控制流。
- 模板 owner、版本、权限、审计必须明确。

### 7.4 内置 Action Renderer

可以优先推进：

- approval renderer
- artifact renderer
- source renderer
- research timeline renderer
- task progress renderer

需要避免：

- 动态远程 renderer。
- 前端解释任意 schema。
- 用户上传 action definition。

## 8. 最终建议

这份文档适合作为“外部开源项目能力雷达”，但需要继续降低执行性表述。尤其是 Temporal、Workflow、YAML skill/template hot-loading 这些内容，不应出现在 Phase 2 的默认路线中。

推荐将文档拆成两层：

1. **能力吸收池**：记录 Deer Flow、Mastra、ChatDev 等项目可学习的模式。
2. **Seahorse 适配路线**：严格服从 C 端 Web AI Infra 阶段设计，只选择当前阶段最小闭环所需能力。

当前阶段最小充分路径是：

```text
Web task runtime
  -> event envelope / lastEventSeq replay / snapshot fallback
  -> approval / artifact / source / timeline
  -> bounded ResearchRunOrchestrator
  -> search/fetch/evidence/citation/report
  -> feedback/eval 在 Phase 3 扩展
```

Temporal、MCP pool、Agent-as-Tool、dynamic UI registry 可以保留为 Phase 4+ 候选项，但必须通过 ADR 和真实瓶颈证据进入实施计划。

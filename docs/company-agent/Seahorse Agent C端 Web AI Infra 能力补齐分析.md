# Seahorse Agent C端 Web AI Infra 能力补齐分析

> **历史文档**：本文写于 2026-05-26，用于初始定位纠偏和 Deer Flow 借鉴分析。当前项目进度和未完成清单请以 [`docs/HANDOVER.md`](../HANDOVER.md) 为准。本文仅保留其中「定位纠偏」「Deer Flow 借鉴」「阶段路线图动因」部分的参考价值。

创建日期：2026-05-26

## 1. 背景与定位纠偏

当前产品应被定义为 **C 端 Web Agent 产品**，不是本地安装型 Agent，也不是默认面向企业内网的多 Agent mesh 平台。因此 AI Infra 的主线不应继续围绕本地文件系统、宿主机 shell、本地沙箱、远程 A2A mesh 或桌面端 Agent 能力展开，而应收敛为：

> 云端可治理 Agent Runtime + C 端 Web 任务体验 + 用户数据与工具安全 + 质量、成本、观测闭环。

`docs/company-agent/ai-infra-phases/` 下的旧规划仍有工程价值，但其默认假设偏企业级 AI Infra。对 C 端 Web 场景，需要重新划分范围：

| 旧能力 | C 端 Web 基线定位 | 处理建议 |
| --- | --- | --- |
| Agent Registry / Run Store | 需要 | 保留，但优先服务单用户 Web 会话、任务历史和可恢复执行 |
| Tool Gateway / Policy / Approval | 需要 | 保留，但审批形态应前移到用户确认和风险提示 UI |
| Durable Runtime / Checkpoint / Resume | 需要 | 保留，并映射为用户可见的长任务进度、断线恢复和失败重试 |
| ContextPack / Resource ACL | 需要 | 保留，并强化来源引用、用户私有数据边界和可解释上下文 |
| MCP / OpenAPI Connector / Credential Vault | 有条件需要 | 只保留服务端受控工具接入；不作为 C 端首发主线 |
| Sandbox Runtime | 不作为默认基线 | 降级为云端受控代码解释器/文件转换的远期能力，不引入本地沙箱 |
| Local Agent-as-Tool / A2A / Agent Mesh | 非当前基线 | 降级为企业版或专业版扩展，不进入 C 端 MVP 验收 |
| 企业准入、灰度、SRE、成本 | 需要但口径调整 | 从企业试点准入调整为线上消费级服务稳定性、额度和质量治理 |

## 2. Deer Flow 可借鉴能力

参考项目：[bytedance/deer-flow](https://github.com/bytedance/deer-flow)。Deer Flow 2.0 的公开 README 将其定位为 super agent harness，核心能力包括 sub-agents、memory、sandboxes、skills、web search/fetch、file operations、bash execution、MCP、上下文压缩和长任务执行。它还强调 Gateway API、线程/会话、流式响应、上传文件、产物输出、LangSmith/Langfuse tracing，以及安全部署注意事项。

对 Seahorse 来说，可以借鉴的是 Web 产品和云端 runtime 模式，而不是照搬本地执行能力：

| Deer Flow 能力 | 可借鉴点 | Seahorse 适配方式 |
| --- | --- | --- |
| 长任务 harness | 任务可持续几分钟到数小时，用户需要知道当前在做什么 | 用 AgentRun、AgentStep、Checkpoint 映射成 Web 任务时间线 |
| Skills & Tools | 能力按需加载，工具可扩展 | 用 ToolCatalog + AgentTemplate + 任务类型模板，不引入本地 skill 文件系统 |
| Sub-agents | 复杂任务拆解、并行探索、汇总 | C 端先实现服务端内部 planner/researcher step，不开放自由 mesh |
| Context Engineering | 子任务摘要、上下文压缩、无关信息下沉 | 强化 ContextPack 的摘要、引用、token 预算和过期策略 |
| Long-Term Memory | 跨会话偏好和用户画像 | 做用户可见、可编辑、可删除、可关闭的个人记忆 |
| Web search / fetch | 深度研究的来源发现和证据链 | 增加服务端 Web 搜索/抓取 adapter、来源可信度和引用卡片 |
| Artifact outputs | 报告、网页、图片、文件等结果可交付 | 建立服务端 Artifact 模型和前端产物面板，而不只解析消息内代码块 |
| Gateway + streaming | 前端和 runtime 解耦，支持流式进度 | 强化 SSE 事件协议，支持 step/progress/artifact/source 等事件 |
| Observability | tracing 按 session/user/run 关联 | 把 Trace 与 C 端消息、任务、反馈、成本关联 |

不应照搬的能力：

1. 本地 host bash、宿主机文件读写、桌面工作区。
2. 每个任务暴露完整文件系统视图。
3. 面向可信本地网络的部署假设。
4. 任意用户可配置 MCP、shell、文件工具。
5. 以 A2A/mesh 作为默认产品主线。

## 3. Seahorse 当前已具备的基础

### 3.1 C 端 Web 对话基础

- 前端已有聊天页、会话列表、消息流式渲染、停止生成、反馈、Markdown 与消息内 Artifact 解析。
- 后端提供 `/rag/v3/chat` SSE 流式接口和 `/rag/v3/stop` 停止接口。
- 关键证据：
  - `frontend/src/pages/ChatPage.tsx`
  - `frontend/src/stores/chatStore.ts`
  - `frontend/src/hooks/useStreamResponse.ts`
  - `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseChatController.java`

### 3.2 RAG、知识库与 Trace

- 已有知识库、文档、chunk、ingestion、metadata governance、RAG trace 和检索评估相关管理面。
- 这部分对 C 端 Web Agent 很关键，但当前更多是管理后台能力，用户端引用展示与证据卡片还不完整。

### 3.3 Memory

- Kernel 和 Web API 已有 memory 管理、review、maintenance、recall evaluation、golden harness 等能力。
- C 端还需要把它变成用户可理解的个人记忆控制，而不是只作为后台治理对象。

### 3.4 Agent Runtime 与治理骨架

- 已有 AgentDefinition、AgentVersion、AgentRun、AgentStep、Checkpoint、Lease、Worker、Resume、Approval、Tool Gateway、Policy、Audit、ContextPack、Resource ACL、Quota、Cost、SRE、Rollout、Readiness 等领域和 API 骨架。
- 关键证据：
  - `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent`
  - `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/ports/inbound/agent`
  - `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseAgentRunController.java`
  - `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseApprovalController.java`
  - `frontend/src/services/aiInfraService.ts`
  - `frontend/src/pages/admin/ai-infra/AiInfraConsolePage.tsx`

判断：后端 AI Infra 骨架已经明显超过普通聊天应用，但 C 端 Web 产品闭环仍不完整。不能因为 domain/controller 存在，就宣称 AI Infra 已按新定位完成。

## 4. 关键缺口清单

### P0：C 端 Web Agent 最小产品闭环

#### 4.1 用户可见的任务运行时间线

当前 SSE 主要承载 meta、message、thinking、finish、done、cancel、reject、title。对长任务来说，用户缺少清晰的 step/progress 状态。

需要补齐：

- SSE 增加 `run_started`、`step_started`、`step_finished`、`tool_call_started`、`tool_call_waiting_user`、`artifact_created`、`source_found`、`recoverable_error` 等事件。
- 前端消息旁显示任务时间线：规划、检索、阅读、分析、生成、校验。
- 刷新页面后可通过 runId 恢复当前任务状态。
- 失败时显示可重试的 step，而不是只显示整条消息失败。

验收标准：

- 用户在 30 秒以上任务中能看到当前进度。
- 浏览器刷新后能恢复 run 状态、已生成内容和已完成 step。
- 后端 AgentRun/AgentStep 与前端 timeline 一一对应。

#### 4.2 SSE 断线恢复与增量持久化

当前前端有 retry 和 watchdog，但缺少基于事件序号的恢复协议。网络中断后，用户可能丢失中间输出。

需要补齐：

- 服务端为 stream event 分配递增 sequence。
- 消息 delta、thinking delta、artifact delta 写入持久化缓冲或事件表。
- 前端重连时携带 lastEventSeq。
- 服务端返回 missed events 或当前快照。

验收标准：

- 主动断网/刷新后，任务不中断或可明确恢复。
- 不重复渲染已接收 delta。
- Stop/cancel 与重连后的状态一致。

#### 4.3 用户侧工具确认与风险提示

当前审批更像后台 HITL。C 端 Web 场景中，用户本人应能确认高风险动作，例如联网检索、外部发送、读取私有记忆、生成可下载文件、消费高额度任务。

需要补齐：

- 将 Approval 分为后台管理员审批和用户即时确认。
- 前端聊天内展示确认卡片：动作、原因、数据范围、费用预估、可选参数。
- 用户可允许一次、拒绝、修改参数、记住本次选择。
- 后端仍通过 ApprovalRequest、PolicyDecision、Audit 保持可追踪。

验收标准：

- 高风险工具不会在无用户确认时执行。
- 确认卡片不暴露 secret 和完整敏感入参。
- 用户决策写入 audit，并能关联 run/step/toolInvocation。

#### 4.4 产物模型和产物面板

当前前端可以解析消息内 `<artifact>` 或代码围栏，但缺少服务端 Artifact 一等模型。C 端任务常见交付物包括报告、表格、图表、网页、图片、PDF、Markdown、引用清单。

需要补齐：

- 新增 `AgentArtifact` 领域模型：artifactId、runId、messageId、type、title、mimeType、storageRef、previewText、provenance、createdAt。
- 产物通过 ObjectStoragePort 保存，消息只引用 artifactId。
- 前端增加产物面板：预览、下载、复制、版本、来源。
- HTML/SVG 等主动内容默认下载或沙盒预览，不能直接内联执行。

验收标准：

- 长报告和文件不依赖单条消息文本承载。
- 每个产物能追溯来源、生成 step 和引用数据。
- 可下载产物经过类型和安全策略处理。

#### 4.5 来源引用与证据卡片

Deer Flow 的深度研究价值来自搜索、抓取、阅读、汇总和报告。Seahorse 已有 RAG trace 和 citation 基础，但 C 端还缺用户可见的来源链。

需要补齐：

- Web 搜索/抓取作为服务端 tool adapter，不引入本地浏览器自动化。
- ContextItem 的 citationJson 映射为前端来源卡片。
- 回答内引用标号可点击，打开来源摘要、原文链接、抓取时间、可信度。
- 对网页内容做 prompt injection 防护：网页文本只能作为不可信资料，不得覆盖系统指令。

验收标准：

- 研究类回答必须有可点击引用。
- 来源卡片能区分知识库、网页、用户上传文件、记忆。
- 不可信网页内容不会改变工具策略和系统约束。

### P1：C 端留存、质量与个性化

#### 4.6 用户可控记忆中心

当前 Memory 能力偏后台。C 端需要让用户知道系统记住了什么，并能控制它。

需要补齐：

- 用户侧记忆页：偏好、个人资料、长期事实、项目上下文。
- 单条记忆支持编辑、删除、禁用、来源追溯。
- 隐私模式：本轮不写记忆、不读取记忆。
- 记忆写入前的敏感信息过滤和重复事实合并。

验收标准：

- 用户能查看、修改、删除自己的记忆。
- 删除后新 ContextPack 不再引用已删除记忆。
- 隐私模式下无长期记忆写入。

#### 4.7 任务模板与技能目录

Deer Flow 的 skill 模式值得借鉴，但 Seahorse 不应引入本地 skill 文件系统。C 端更适合产品化任务模板。

需要补齐：

- 任务模板：深度研究、网页总结、文档问答、生成报告、学习计划、消费决策辅助等。
- 每个模板绑定默认工具、输出格式、风险策略、成本上限。
- 前端提供轻量模板入口，而不是要求用户理解 Agent 配置。

验收标准：

- 用户能从模板发起结构化任务。
- 模板不会绕过 Tool Gateway 和 Policy。
- 新模板通过 adapter/config 扩展，不改 runtime 不变量。

#### 4.8 多模态和文件上传到对话

当前文件上传主要在知识库/ingestion 管理面。C 端对话需要直接上传文件、图片，并让任务引用这些输入。

需要补齐：

- 聊天输入支持文件上传。
- 上传文件进入用户私有对象存储，形成 ResourceRef。
- 支持 PDF、文档、表格、图片的解析 adapter。
- 任务输出引用用户上传文件时显示来源。

验收标准：

- 用户上传文件后能在同一会话提问。
- 文件权限只对上传者和被授权任务可见。
- 大文件解析异步化，前端显示处理状态。

#### 4.9 反馈到评测闭环

已有 message feedback 和 eval 基础，但还需要把 C 端反馈变成可行动质量信号。

需要补齐：

- 用户反馈收集原因：不准确、没引用、太慢、格式差、未完成任务。
- 反馈关联 runId、traceId、contextPackId、artifactId。
- 自动生成评测样本候选，进入离线回归集。
- 建立 answer groundedness、source quality、task completion、latency、cost 指标。

验收标准：

- 每条差评能定位到检索、模型、工具、产物或 UI 问题。
- 重要反馈能进入评测数据集。
- 发布/模型切换前能跑核心回归。

### P2：规模化运营与高级能力

#### 4.10 模型路由、降级和成本透明

已有成本、quota、模型路由相关骨架，但 C 端需要产品化展示和自动降级策略。

需要补齐：

- 按任务类型选择模型：快速答复、深度研究、长报告、多模态。
- 成本上限：用户级、任务级、模板级。
- 排队和背压：高峰期展示等待、降级或稍后继续。
- 前端展示“预计耗时/消耗”而不是暴露内部 token 细节。

验收标准：

- 超限任务不会无限消耗。
- 模型失败时有明确降级路径和用户提示。
- 成本数据能按用户、任务、模板聚合。

#### 4.11 Web 安全与滥用防护

C 端公开 Web 产品的风险不同于本地 Agent。重点是账号滥用、内容安全、提示注入、XSS、越权访问和费用攻击。

需要补齐：

- 多维限流：IP、用户、任务类型、工具、模型。
- 内容安全：输入、检索内容、输出、产物下载前扫描。
- Prompt injection 防护：外部网页、上传文件、工具结果都标记为 untrusted content。
- Artifact 安全：HTML/SVG/JS 不直接内联执行。
- 资源越权测试：用户 A 不能访问用户 B 的文件、记忆、artifact、run。

验收标准：

- 关键接口有鉴权和资源归属校验。
- 恶意网页内容不能提升工具权限。
- 主动内容产物不会造成前端 XSS。

#### 4.12 移动端与可访问性

C 端 Web 场景必须考虑移动端使用。当前管理后台能力不等同于 C 端可用体验。

需要补齐：

- 移动端聊天布局、任务时间线、引用卡片、产物预览。
- 长任务后台运行和恢复入口。
- 键盘、屏幕阅读器、低视力模式的基础可访问性。

验收标准：

- 移动端可以完整发起、观察、停止、恢复任务。
- 引用和产物在窄屏不遮挡、不溢出。
- 基础键盘操作可用。

## 5. 推荐路线图

### 第一阶段：Web 任务可用闭环

目标：让 C 端用户能发起一个长任务，并可靠地看到进度、来源、结果和失败状态。

范围：

1. SSE 事件协议扩展。
2. AgentRun/Step 到前端 timeline 的映射。
3. 断线恢复最小闭环。
4. 用户即时确认卡片。
5. Artifact 模型最小闭环。
6. 来源引用卡片。

不做：

- 本地沙箱。
- A2A mesh。
- 自由 sub-agent 编排 UI。
- 任意 MCP 配置入口。

### 第二阶段：研究型 Web Agent

目标：借鉴 Deer Flow 的深度研究体验，但全部运行在服务端受控 adapter 内。

范围：

1. Web search/fetch adapter。
2. Planner/researcher/writer 内部 step。
3. 来源可信度、抓取时间、引用去重。
4. 报告 Artifact。
5. 反馈到评测集。

不做：

- 暴露 shell。
- 暴露本地文件系统。
- 让用户上传任意可执行脚本并运行。

### 第三阶段：个人化与规模化运营

目标：提升留存、质量、成本可控性。

范围：

1. 用户记忆中心。
2. 任务模板目录。
3. 文件上传到对话。
4. 模型路由和成本上限。
5. 滥用防护和安全回归。

### 第四阶段：可选高级扩展

目标：为专业版或企业版预留能力，但不污染 C 端基线。

范围：

1. 云端代码解释器。
2. 受控文件转换。
3. 企业连接器。
4. 企业 Agent-as-Tool。
5. A2A/remote agent。

进入条件：

- C 端核心任务闭环稳定。
- 安全策略和审计覆盖到位。
- 有明确付费或企业需求。

## 6. 对旧 AI Infra 文档的调整建议

旧文档不建议直接删除。建议新增一个范围声明，并在 README 或总规划中标记：

1. `Phase 0-4`：大部分仍适用于 C 端 Web，但验收口径要转为用户任务体验、引用、恢复、隐私和安全。
2. `Phase 5`：拆分为“服务端 Web 工具 adapter”和“企业连接器/云端沙箱扩展”。C 端基线只保留前者。
3. `Phase 6`：Agent Factory 改写为“任务模板与技能目录”，面向用户任务，不是企业业务团队自助派生 Agent。
4. `Phase 7`：降级为企业版/专业版远期扩展，不作为当前 AI Infra 完成标准。
5. `Phase 8`：保留生产化治理，但从企业试点准入改为公开 Web 产品的安全、成本、稳定性和质量指标。

建议新增文档状态：

| 文档 | 状态 |
| --- | --- |
| `Seahorse Agent 企业级 AI Infra 分阶段开发规划.md` | 历史企业版规划 |
| `Seahorse Agent 企业级 AI Infra 架构基线.md` | 作为后端治理基线参考 |
| `ai-infra-phases/05-connectors-credentials-sandbox.md` | C 端只采纳服务端工具和凭据安全部分 |
| `ai-infra-phases/07-multi-agent-a2a-mesh.md` | C 端非目标，企业版远期扩展 |
| 本文档 | C 端 Web AI Infra 当前范围基线 |

## 7. 当前结论

Seahorse 目前不能简单回答“AI Infra 已完全实现”。更准确的判断是：

1. **后端 AI Infra 骨架已经较完整**：Agent、Run、Tool、Approval、Checkpoint、Context、ACL、Quota、Cost、SRE 等关键对象和接口已经存在。
2. **C 端 Web Agent 产品闭环尚未完整**：长任务进度、断线恢复、用户确认、来源引用、产物模型、个人记忆控制、文件上传到对话、质量反馈闭环还需要补齐。
3. **旧企业级能力需要降级**：本地 Agent、本地沙箱、A2A mesh、远程 Agent Card 不应作为当前 C 端 AI Infra 的完成标准。
4. **下一步不应继续扩大 infra 范围**：应优先把已有后端骨架连接到 C 端 Web 体验，形成用户可感知、可恢复、可解释、可控成本的最小闭环。


# Seahorse Agent C端 Web AI Infra 阶段设计索引

创建日期：2026-05-26

## 定位

本目录承接 `docs/company-agent/Seahorse Agent C端 Web AI Infra 能力补齐分析.md`，把 C 端 Web Agent 的 AI Infra 路线拆成可直接进入开发排期的阶段设计。

这里的阶段不是旧企业级 `ai-infra-phases/00-08` 的逐字延续，而是按 C 端 Web 产品目标重新组织：

1. 先把用户可见的长任务闭环做扎实。
2. 再补研究型 Web Agent 的来源、引用、报告能力。
3. 再补个人化、模板、质量、成本和运营闭环。
4. 最后把代码解释器、企业连接器、A2A 等高级能力隔离成可选扩展。

## 阶段文档

| 阶段 | 文档 | 目标 |
| --- | --- | --- |
| Phase 1 | [01-web-task-runtime.md](./01-web-task-runtime.md) | Web 任务可用闭环：任务时间线、SSE 恢复、用户确认、Artifact、来源卡片 |
| Phase 2 | [02-research-web-agent.md](./02-research-web-agent.md) | 研究型 Web Agent：搜索/抓取 adapter、研究步骤、可信来源、报告产物 |
| Phase 3 | [03-personalization-operations.md](./03-personalization-operations.md) | 个人化与运营：记忆中心、任务模板、文件上传、反馈评测、模型路由、成本透明 |
| Phase 4 | [04-advanced-extension-boundary.md](./04-advanced-extension-boundary.md) | 高级扩展边界：云端代码解释器、企业连接器、Agent-as-Tool/A2A 的隔离准入 |

## 总体非目标

以下能力不进入 C 端 Web AI Infra 当前完成标准：

- 本地安装 Agent。
- 浏览器或桌面端直接读写本机文件系统。
- 宿主机 shell/bash 执行。
- 用户自定义任意 MCP server 并直接执行。
- 默认开放 A2A、Agent Mesh、Remote Agent Card。
- 以企业业务团队派生 Agent 作为 C 端 MVP 主线。

## 架构约束

- Kernel 只依赖 port 抽象，不依赖 Spring、JDBC、Web。
- Agent、Run、Tool、Policy、Approval、Context、Artifact 分小接口演进，不新增大一统 AgentService。
- 新能力优先通过 adapter 扩展，避免修改领域不变量。
- 状态、类型、风险等级、事件名必须使用 enum 或具名常量。
- 第一轮实现只做最小闭环，不能引入工作流引擎、远程 Agent mesh 或复杂 JSON DSL。


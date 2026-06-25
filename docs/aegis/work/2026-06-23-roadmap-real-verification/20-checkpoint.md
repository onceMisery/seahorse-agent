# Checkpoint

## TodoCheckpointDraft

- Current todo: 从路线图和完成情况报告提取真实验证清单，并优先执行 P0/P1 的 Docker/API/页面验证。
- Completed todos:
  - 已重读路线图和完成情况报告，确认“代码完成度高，运行验证待补齐”是当前主线。
  - 已验证并修复系统角色卡在旧 Docker 卷中缺失的问题。
  - 已用真实 API 和真实浏览器确认角色卡管理页可见，聊天页 Role card 下拉可选择系统预置角色卡。
- Active slice: 近期真实验证清单梳理 + 选择下一批现有脚本/页面验证。
- Next step: 运行现有 full Docker 环境下的 RAG/记忆/治理后台冒烟脚本，优先复测 `scripts/e2e-backend-smoke.ps1`、`scripts/e2e-rag-evaluation-smoke.ps1` 和管理页可达性。

## Verification Matrix

| 功能域 | 文档要求 | 当前证据 | 状态 |
|---|---|---|---|
| 角色卡 | 系统预设、管理页、聊天选择、上下文可追溯 | API + DB + Playwright 页面选择已通过；上下文快照影响仍需更深链路 | partial |
| 运行方案 | 创建/绑定角色卡/聊天继承 `runProfileId` | 仅已补系统运行方案 DB/API 证据；聊天继承待测 | needs-verification |
| 消息树/分支 | fork 分支、切换、刷新恢复 | 待真实页面/API 案例 | needs-verification |
| 运行实验 | 多运行方案 trial、评分、成本/trace、fork 分支 | 待真实页面/API 案例 | needs-verification |
| AgentScope/Nacos A2A | AgentScope 运行方案、trace、失败降级 | 容器中 MCP disabled 已知；AgentScope 待复测 | needs-verification |
| MCP stdio/HTTP | 发现、调用、异常、审计、高风险路径 | `/api/mcp/servers` 因配置关闭返回 409 属预期；stdio 示例待测 | needs-verification |
| 治理后台 | 页面无 404、空态/错误态/权限态、审计 | 早前页面 smoke 修过 404；需扩大到当前清单 | needs-verification |
| RAG/Trace | `t_knowledge_chunk` 有数据、trace 有 retrieval 节点 | 历史 full compose smoke 有证据；当前环境需重跑 | needs-verification |
| 记忆画像 | readiness 有证据、profile facts 有 active 事实 | 历史 full compose smoke 有证据；当前环境需重跑 | needs-verification |
| RAG 评测 | evaluation API 产出可对比报告 | 待运行 `e2e-rag-evaluation-smoke.ps1` | needs-verification |
| full compose | 登录、聊天、角色卡、运行方案、消息树、AgentScope 可选、MCP 示例 | 当前 Docker 全量运行中；待扩大 smoke | needs-verification |

## DriftCheckDraft

- 当前工作仍服务于原始目标：给已开发能力补真实案例验证。
- 当前不把角色卡修复扩大解释为全局完成。
- 没有清空 Docker 卷；选择启动自愈修复旧卷缺系统预置数据。
- 证据束已覆盖角色卡可见/可选择，但不足以覆盖角色卡影响真实回复和 run context snapshot。
- Decision: continue.

## Risk / Unknown

- 工作区已有多处未提交改动；本任务继续只碰必要文件，并保留用户/既有改动。
- 当前全量 Docker 中 MCP 适配器开关可能关闭，相关路径需要区分“配置关闭的预期降级”和真实缺陷。
- 部分前端源文件存在既有中文乱码问题，页面验证以实际渲染和功能行为为准，不在本 slice 扩展处理文案修复。

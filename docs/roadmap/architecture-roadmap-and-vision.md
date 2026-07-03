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
| `docs/design/apix-inspired-feature-evolution-roadmap.md`、`docs/design/apix-inspired-phased-implementation-design.md` | MCP stdio 治理升级：命令 allowlist、runner 隔离、风险自动标记、高风险默认禁用、审批/审计/脱敏、stderr tail 与诊断闭环 | 基础 stdio/HTTP MCP 已归档完成；命令 allowlist、近端 runner 环境隔离、HIGH/需审批默认标记、诊断审批入口、诊断执行网关 fail-closed、OpenAPI enabled operation 动态进入 Tool Gateway/真实 HTTP invoke/audit、Sandbox runtime lifecycle close 透传、Sandbox execution history API/UI、Sandbox artifact scanner/prompt visibility gate、Docker/Podman Code Interpreter 容器 adapter 最小闭环、`sandbox_python` Tool Gateway 工具链路、`sandbox_file_convert` CSV/TSV/JSON 表格转换、txt/html/markdown 文本文档转换与 base64 `docx -> txt`/`pdf -> txt` 保守文档文本提取工具链路、full-compose backend host-socket/tooling opt-in 接入、受限 inline no-network `sandbox_browser`、HAR artifact capture、download-only video capture、真实执行 artifact collection 和 stderr/响应脱敏已有证据；剩余 browser egress/URL policy、auth/session state capture、PDF 渲染/OCR、Office 渲染/编辑、LibreOffice/Tika、二进制格式转换与更广的 A2A/跨 provider Tool Gateway 审计仍需补齐 | 近期/中期：Tool Gateway 与 MCP 安全治理 | 高风险 MCP 工具默认不可直接运行；审批通过后真实调用落审计；失败时 UI 展示 stderr/诊断且不影响普通聊天 |
| `docs/design/apix-inspired-phased-implementation-design.md` | Agent Workbench：把消息分支、运行方案、实验、发布门禁、A2A、Studio trace 聚合成一个调试和发布工作台 | Chat Workspace/Inspector 和 Admin 页面仍分散 | 远期：统一 Agent 工作台 | 一个入口完成分支选择、运行方案切换、实验对比、trace/Studio 跳转、发布门禁检查和回滚 |
| `docs/design/apix-inspired-feature-evolution-roadmap.md` | MCP Marketplace、Profile Marketplace、自学习闭环 | Agent Marketplace 基座已归档完成；MCP/Profile 市场和自学习闭环未落地 | 远期：企业资产市场与自学习 | MCP/Profile 能提交、审核、订阅、评分、下架；线上反馈能形成评测样本或策略建议，但不会无人值守改生产配置 |
| `docs/design/apix-inspired-feature-evolution-roadmap.md` | 发布门禁和回归评测从 Agent 扩展到 Run Profile、RAG Strategy、Model Config、Tool/Skill、Ingestion Pipeline | Agent 级 `ProductionGateReport` 已归档完成；统一 `GateResult<T>` 与逐对象 adapter 仍缺 | 中期：统一证据模型与发布门禁 | 所有高风险对象发布前都产出 GateResult，能追溯到 evaluation、trace、audit、cost 和配置快照 |
| `docs/design/apix-inspired-phased-implementation-design.md` | 运行实验报告增强：trial 导出、失败说明、成本/trace/分支对比报告、AgentScope Studio trace 外链 | 运行实验基础已归档完成；报告化和真实对比 test case 仍需补齐 | 近期/中期：运行实验产品化 | 同一会话下多个运行方案的 trial 可导出报告，报告包含输出差异、成本、评分、trace 和对应消息分支 |

## 近期路线（0-4 周）

近期只处理“已合入但未稳定”和“设计已明确但尚未实现”的工作。

| 优先级 | 工作项 | 范围 | 验收 |
|---|---|---|---|
| P1 | MCP stdio 安全治理第一阶段 | 已落地：命令 allowlist、近端 runner 环境隔离、MCP 工具 HIGH/需审批默认标记、blocked stdio stderr 诊断、诊断审批直达入口、MCP 诊断执行网关 fail-closed、OpenAPI enabled operation 动态注册到 Tool Gateway 并具备真实 HTTP invoke/audit、Sandbox runtime close lifecycle 透传与关闭审计、Sandbox execution history API/UI、Sandbox artifact scanner/prompt visibility gate、Docker/Podman Code Interpreter 容器 adapter 最小闭环、`sandbox_python` Tool Gateway 工具链路、`sandbox_file_convert` CSV/TSV/JSON 表格转换、txt/html/markdown 文本文档转换与 base64 `docx -> txt`/`pdf -> txt` 保守文档文本提取工具链路、受限 inline no-network `sandbox_browser` 与 HAR/download-only video artifact capture、full-compose backend 容器内 Docker host-socket/CLI opt-in 接入、真实容器执行 artifact collection；剩余：browser egress/URL policy、auth/session state capture、PDF 渲染/OCR、Office 渲染/编辑、LibreOffice/Tika、二进制格式转换、更广 A2A/跨 provider Tool Gateway 审计硬化 | 非 allowlist stdio 命令无法启动；高风险 MCP 工具默认进入审批/网关治理 |
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

## 2026-07-03 Update: Sandbox Runtime Governance Visibility

Sandbox Runtime now exposes read-only runtime governance/profile visibility through `GET /api/sandbox/runtime/profiles` and the admin Sandbox Operations panel. The endpoint reports the kernel-owned default profile mapping, default `DENY_ALL` network posture, default TTL, and container-supported versus planned runtime types without touching Docker/Podman.

This moves runtime profile/capacity visibility out of one-off health toasts and into the operator surface. Remaining Sandbox productionization work is profile/policy mutation, tenant/agent quota UX beyond the tool-level endpoint, browser automation, PDF rendering/OCR plus Office/binary conversion beyond the current conservative document text conversions, deeper scanning/redaction, stronger isolation, and node-pool scheduling/health.

## 2026-07-03 Update: Sandbox Document Text Conversion

`sandbox_file_convert` now supports conservative document text conversions on top of the existing CSV/TSV/JSON table path: `txt -> html`, `html -> txt`, `markdown/md -> html/txt`, and base64 `docx -> txt` / `pdf -> txt`. The implementation stays inside the no-network `FILE_CONVERSION` container runtime with a generated Python stdlib converter and collects only the converted output artifact. The DOCX path is intentionally limited to `word/document.xml` text extraction via stdlib `zipfile` and `xml.etree.ElementTree`; the PDF path extracts literal text from unencrypted PDF streams with stdlib `re`/`zlib` helpers. It does not add LibreOffice/Tika, PDF rendering/OCR, Office editing, or general binary conversion.

Fresh evidence: focused kernel/container tests passed, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-pdf-convert-smoke` passed 23/23 against the local full-Docker backend, including CSV/JSON conversions, Markdown-to-HTML invoke, DOCX-to-TXT and PDF-to-TXT invokes, persisted `FILE_CONVERSION` session/profile metadata, governed artifact downloads, local object storage verification, no leftover managed sandbox containers, and zero non-terminal sandbox sessions.

## 2026-07-03 Update: Sandbox Tool Quota Governance

Sandbox Operations now exposes `POST /api/sandbox/runtime/tool-quota-policies` for sandbox-backed tool quota policy writes. The endpoint is gated by both `SANDBOX` and `QUOTA_MANAGEMENT`, accepts only `sandbox_python`, `sandbox_file_convert`, and the planned `sandbox_browser`, then writes an existing `QuotaScope.TOOL` policy so Tool Gateway quota preflight remains the enforcement owner.

The admin Sandbox Operations page now includes a compact Tool quota panel for policy id, sandbox tool id, status, calls, tokens, cost, and warn ratio. The frontend sandbox service uses the browser proxy path required by the packaged Nginx/Vite proxy so `/api/sandbox/...` backend routes are reached correctly from the UI.

Fresh evidence: focused Web tests and frontend capability contracts passed, the frontend build completed, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-tool-gateway-quota-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-tool-quota-smoke-rerun` passed 4/4 by creating a zero-call sandbox tool policy and observing `QUOTA_HARD_LIMIT_EXCEEDED` through the real `sandbox_python` Tool Gateway invocation. UX evidence now also includes `npm test -- src/services/frontendCapabilityContracts.test.ts` passing 10/10, `npm run build` completing with only existing warnings, `.\scripts\e2e-sandbox-tool-quota-page-smoke.ps1 -BaseUrl http://127.0.0.1 -Password admin123 -Marker seahorse-sandbox-tool-quota-ux-smoke` passing against the local full-Docker frontend, and cleanup confirming the page-smoke policies disabled, no leftover managed sandbox containers, and zero non-terminal sandbox sessions.

## 2026-07-03 Update: Sandbox Browser Automation

`sandbox_browser` now provides the first conservative browser automation path. It accepts bounded inline HTML for `snapshot` and `extract_text`, creates a no-network `BROWSER_AUTOMATION` sandbox session, runs a generated Python Playwright script in `seahorse-sandbox-browser:playwright-1.48.0`, and returns governed `browser-result.json` plus an optional `screenshot.png`.

Fresh evidence: focused kernel/container/Web/autoconfigure tests passed, the local browser runtime image built from `resources/docker/Dockerfile.sandbox-browser-runtime`, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-smoke` passed 10/10 against the local full-Docker backend. Remaining browser work is external URL/egress policy, video/session capture, richer browser workflows, and stronger isolation.

## 2026-07-03 Update: Sandbox Runtime Profile Policy Writes

Sandbox Operations now exposes bounded runtime profile policy writes through `POST /api/sandbox/runtime/profile-policies`. The endpoint is deliberately narrow: existing kernel-owned profiles only, `ACTIVE`/`DISABLED`, `sessionTtlSeconds` from 60 to 7200, and `networkAllowed=false`.

New session creation now enforces those policies. Disabled profiles persist `RUNTIME_PROFILE_DISABLED`; active TTL overrides change the persisted session expiry; the admin Runtime governance panel reads the effective policy back through `GET /api/sandbox/runtime/profiles?tenantId=default`.

Fresh evidence: focused Java regression tests, frontend capability contracts, and frontend build passed; `.\scripts\e2e-sandbox-runtime-profile-policy-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-runtime-profile-policy-smoke` passed 12/12 against the local full-Docker backend; cleanup confirmed no leftover managed sandbox containers, zero non-terminal sandbox sessions, and restored `CODE_INTERPRETER|ACTIVE|3600|false`. Remaining Sandbox productionization work is tenant/agent quota UX, external URL/egress policy, PDF rendering/OCR, Office/binary conversion, deeper virus/PDF/binary scanning, stronger isolation, and node-pool scheduling/health.

## 2026-07-03 Update: Sandbox Artifact Structured Redaction Summary

Sandbox artifact scanner decisions now include a bounded structured redaction summary JSON payload. The schema records `schemaVersion`, scanner id, decision, blocked/redacted booleans, `contentScanned`, categories, and a safe reason without storing raw secret or PII values. The initial implementation covered the default metadata/text scanner and kernel fail-closed paths; the later binary/PDF signature scanner update below extends those categories without adding external scanner engines.

The payload is persisted as `sa_sandbox_artifact.redaction_summary_json VARCHAR(2048)`, exposed through sandbox list/detail APIs and sandbox-backed tool artifact metadata, and rendered in the admin Sandbox artifact detail. Fresh evidence: focused Java regression tests passed, frontend capability contracts passed 10/10, frontend build passed with existing warnings, the full-compose backend rebuilt with in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-redaction-summary-smoke` passed 17/17 against the local full-Docker backend. Cleanup confirmed no leftover managed sandbox containers, zero non-terminal sandbox sessions, and PostgreSQL column metadata `redaction_summary_json|character varying|2048`.

## 2026-07-03 Update: Sandbox Browser Restricted HAR Capture

`sandbox_browser` now supports an opt-in `har=true` flag for the existing inline no-network browser path. When enabled, the browser runtime records a governed `browser-network.har` artifact with HAR 1.2 shaped JSON, marks route-aborted external requests with `_blocked: true`, and keeps the execution posture unchanged: inline HTML only, Docker/Podman `--network none`, and page routing that aborts non-`about:`/`blob:`/`data:` URLs.

Fresh evidence: focused kernel/container tests passed, built-in tool catalog registration passed, frontend capability contracts passed 10/10, and the frontend build completed with existing warnings. The full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-har-smoke` passed 11/11, verifying governed JSON/PNG/HAR artifacts, PostgreSQL `application/har+json` metadata, a blocked `example.invalid` request marker in the downloaded HAR, no storage-reference leakage, object storage, no leftover managed sandbox containers, and zero non-terminal sandbox sessions. This completes restricted HAR/network capture for inline no-network browser automation only; external URL browsing, egress allowlists/proxying, video recording, session/auth capture, richer browser workflows, stronger isolation, and node-pool hardening remain follow-up work.

## 2026-07-03 Update: Sandbox Browser Download-Only Video Capture

`sandbox_browser` now supports an opt-in `video=true` flag for the existing inline no-network browser path. The container runtime records the Playwright page context and emits a governed `browser-video.webm` artifact as `video/webm` while keeping inline HTML only, `--network none`, and the existing route-level block for non-inline requests.

This slice separates governed download eligibility from prompt visibility: `SandboxArtifact.downloadable()` gates storage copy/download for clean non-secret artifacts, while `promptVisible()` also checks prompt-safe media types. `video/webm` is metadata-scanned and downloadable, but remains prompt-blocked; the admin Sandbox artifact download action now follows the detail `downloadable` policy instead of treating prompt-blocked artifacts as automatically undownloadable. This does not add external URL browsing, egress allowlists/proxying, credentials, auth/session state capture, or broader browser workflows.

Fresh evidence: focused kernel/container tests passed 66/66, built-in tool catalog registration passed 1/1, frontend capability contracts passed 10/10, and the frontend build completed with existing warnings. The full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-video-smoke` passed 12/12, verifying real Tool Gateway invocation with `video=true`, governed JSON/PNG/HAR prompt-visible artifacts, persisted clean/internal `video/webm` artifact metadata, prompt-blocked but downloadable video detail, WebM download with EBML header, object storage, no leftover managed sandbox containers, and zero non-terminal sandbox sessions.

## 2026-07-03 Update: Sandbox Browser Allowlisted URL Egress

`sandbox_browser` now has the first allowlisted URL egress path in addition to the existing inline no-network HTML path. URL mode requires an HTTP/HTTPS `url` plus `allowedHosts`; the tool, kernel profile policy, global sandbox policy, container Docker network mode, and generated Playwright route handler all require the URL host to be explicitly allowlisted. Inline HTML continues to run with `--network none`.

This is deliberately narrow: it does not add credentials, auth/session state capture, arbitrary browsing policy UX, proxy/audit-rich egress, or general web automation workflows. Remaining browser work is those broader URL/session controls plus stronger isolation.

## 2026-07-03 Update: Sandbox Browser Request-Scoped Cookie Injection

`sandbox_browser` URL mode now accepts bounded, explicit `cookies` for the allowlisted target host. Cookie domains must match `allowedHosts`; values are passed only through the per-session browser runtime, loaded with Playwright `context.add_cookies(...)`, and excluded from observations, result metadata, HAR downloads, and artifact collection. Empty cookie arrays are treated as no cookie injection so inline no-network automation remains unaffected.

Fresh evidence: focused kernel/container tests passed 33/33, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-cookie-smoke` passed 20/20 against the local full-Docker backend. This is the first request-scoped auth/session step only; stored browser sessions, credential governance, session capture/replay, proxy-rich egress, and broader workflow UX remain follow-up work.

## 2026-07-03 Update: Sandbox Browser Governed Session State Capture

`sandbox_browser` URL mode now accepts `captureSessionState=true` to capture Playwright storage state after navigation. The runtime writes a full `browser-session-state.json` artifact plus a value-free `browser-session-summary.json`; the full state is forced to `SECRET/BLOCKED`, not prompt-visible, not copied to object storage, and not downloadable, while the summary exposes only cookie counts/domains and localStorage origin counts.

Fresh evidence: focused kernel/container tests passed 35/35, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-session-state-smoke` passed 21/21. This closes governed capture for one URL-mode run; replay, credential storage, operator approval UX, and long-lived browser profiles remain follow-up work.

## 2026-07-03 Update: Sandbox Browser Request-Scoped Session State Replay

`sandbox_browser` URL mode now accepts an explicit request-scoped Playwright `sessionState` object for one-run replay. The tool and runtime both reject replay for inline HTML, require every session-state cookie domain and localStorage origin host to be present in `allowedHosts`, and keep URL replay behind the existing browser profile network plus global allowlisted-egress gates.

The replay state is written only to transient `browser-session-state-input.json`, excluded from artifact collection, and loaded through Playwright `browser.new_context(storage_state=...)`. Observations expose only `browser.sessionState.replayRequested`; governed result JSON includes only a value-free replay summary with cookie domains/counts and origin localStorage counts. Cookie/localStorage values are not written to observations, prompt-visible artifacts, HAR downloads, object-storage references, or collected artifacts.

Fresh evidence: focused kernel/container tests passed 39/39, compose overlay validation passed, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-session-replay-smoke` passed 25/25. The smoke verified request-scoped replay of a real fixture cookie plus localStorage value, governed result/HAR downloads without value leakage, transient replay inputs staying out of `sa_sandbox_artifact`, browser profile network restore, no leftover managed sandbox containers, and zero non-terminal sandbox sessions.

This closes explicit one-run session-state replay. Replaying the previously captured SECRET/BLOCKED artifact, credential storage, operator approval UX, and long-lived browser profile management remain follow-up production work.

## 2026-07-03 Update: Sandbox Artifact Binary/PDF Signature Scan

`DefaultSandboxArtifactScannerPort` now performs a bounded local-file signature scan for existing prompt-safe binary artifacts (`application/pdf`, supported image media types) and download-only `video/webm` artifacts. The scanner reads only the first 256 KiB, blocks PE/ELF executable signatures, blocks ZIP/PDF/EBML/script-like masquerading when the media type does not match, and blocks PDF active-content markers such as `/JavaScript`, `/JS`, `/OpenAction`, and `/AA`.

Blocked decisions use value-free redaction categories including `EXECUTABLE_BINARY`, `PDF_ACTIVE_CONTENT`, and `BINARY_SIGNATURE_MISMATCH`. Clean WebM artifacts remain download-only and prompt-hidden, but now report `contentScanned=true` because their header was inspected. This is deliberately conservative: it does not add ClamAV or another virus engine, archive decompression, full PDF parsing/rendering, Office rendering/editing, or general binary conversion.

Fresh evidence: focused scanner tests passed 10/10 via `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=DefaultSandboxArtifactScannerPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; broader kernel artifact governance tests passed 46/46 via `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=DefaultSandboxArtifactScannerPortTests,KernelSandboxRuntimeServiceTests,SandboxArtifactTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; compose overlay validation passed; the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-binary-signature-smoke` passed 21/21. The smoke verified real `sandbox_python` PDF active-content and executable-masquerading artifacts are `BLOCKED|CONFIDENTIAL`, not copied to object storage, not prompt-visible, not downloadable, and exposed through APIs only as blocked metadata without raw `OpenAction` or storage-reference leakage.

Remaining scanning work is external virus scanning, richer binary/PDF deep scanning with a dedicated engine, richer archive/container introspection beyond bounded ZIP, and production policy UX for scanner findings.

## 2026-07-03 Update: Sandbox Artifact ZIP Archive Introspection

`DefaultSandboxArtifactScannerPort` now performs conservative bounded introspection for local `file://` ZIP artifacts. ZIP and `application/x-zip-compressed` artifacts are download-only media types, never prompt-visible by media type, and are scanned with JDK `ZipFile` without full extraction, recursive decompression, ClamAV, or another external scanner engine.

The scanner inspects at most 128 entries and reads at most the first 256 KiB from each file entry. It blocks unsafe entry paths, executable entry extensions or PE/ELF signatures, and embedded PDF active-content markers using value-free categories such as `ARCHIVE_SCAN_LIMIT`, `ARCHIVE_UNSAFE_ENTRY`, `ARCHIVE_EXECUTABLE_BINARY`, `ARCHIVE_PDF_ACTIVE_CONTENT`, and `ARCHIVE_SCAN_ERROR`. Clean ZIP artifacts are stored as governed downloadable artifacts with `contentScanned=true` while staying prompt-hidden.

Fresh evidence: focused kernel/container tests passed 45/45, compose overlay validation passed, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-archive-introspection-smoke-final` passed 27/27. Remaining scanning work is external virus scanning, richer PDF/binary deep scanning, archive/container introspection beyond bounded non-recursive ZIP, and production policy UX for scanner findings.

## 2026-07-03 Update: Sandbox Runtime Workspace Disk Health

Sandbox Runtime health now includes read-only workspace disk visibility. The container adapter reports usable workspace bytes, the configured minimum free-byte threshold, disk availability, and a disk status (`UNBOUNDED`, `AVAILABLE`, `LOW`, or `UNKNOWN`) through `GET /api/sandbox/runtime/health`. The default threshold remains `0`, so the default path is unbounded and does not alter session creation or execution behavior.

The full-compose sandbox overlay and `.env.full.example` expose `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_MIN_WORKSPACE_FREE_BYTES`. The admin Sandbox Runtime governance panel and health toast now include the disk signal, and the full-Docker smoke verifies both the default `UNBOUNDED` path and a configured threshold path. This is a visibility step only; actual disk quota enforcement, runtime pool scheduling, and node-level health remain follow-up production hardening.

## 2026-07-03 Update: Sandbox Artifact Office Open XML Bounded Scan

Sandbox artifact governance now covers Office Open XML packages as bounded ZIP-family artifacts. DOCX/XLSX/PPTX are clean download-only media when their bounded package scan passes: they are copied to governed object storage, downloadable through artifact APIs, but kept out of prompts and tool observations. DOCM/XLSM/PPTM are blocked by media type with `OFFICE_MACRO`; DOCX/XLSX/PPTX packages containing `vbaProject.bin` are blocked with the same value-free category before object storage copy.

Fresh evidence: focused kernel/container tests passed 85/85, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-office-ooxml-smoke` passed 33/33. This is a conservative package-governance slice only; Office rendering/editing, macro parsing/execution, LibreOffice/Tika-backed conversion, recursive extraction, external virus scanning, and richer scanner policy UX remain follow-up work.

## 2026-07-03 Update: Sandbox Workspace Disk Admission

Sandbox session creation now fails closed when a positive configured workspace free-space threshold is not met. `KernelSandboxRuntimeService` reuses runtime health as an admission preflight after policy/profile checks: when `workspaceMinFreeBytes > 0` and `workspaceDiskAvailable=false`, it persists `FAILED|RUNTIME_WORKSPACE_DISK_LOW` and skips runtime workspace creation. The default `min-workspace-free-bytes=0` path remains unbounded and behavior-compatible.

Fresh evidence: focused kernel/container/Web tests passed 69/69, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, the default artifact-storage smoke passed 33/33, and the high-threshold low-disk admission smoke passed 4/4 with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_MIN_WORKSPACE_FREE_BYTES=9223372036854775807`. This is admission-only; per-session disk quotas, filesystem quota enforcement, node scheduling, and node-level health remain follow-up production hardening.

## 2026-07-03 Update: Sandbox Artifact Scanner Policy Visibility

Sandbox Operations now has a read-only artifact scanner policy surface through `GET /api/sandbox/runtime/artifact-scanner-policy` and the admin Runtime governance panel. The policy reports the active scanner id/mode, fail-closed posture, value-free finding storage, bounded content/archive scan windows, prompt-safe/download-only media coverage, blocked/redacted categories, and explicitly unsupported capabilities.

Fresh evidence: Docker image pulls recovered through the local proxy path, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-scanner-policy-smoke` passed 34/34 against the recreated full-Docker backend. Cleanup confirmed no leftover managed sandbox containers, zero non-terminal sandbox sessions, `/actuator/health` returned `{"status":"UP"}`, and the live scanner-policy endpoint returned `scannerId=default-local-bounded`.

This is operator visibility only: it does not add ClamAV or another external virus scanner, recursive archive/container extraction, full PDF rendering/OCR, Office rendering/editing, LibreOffice/Tika conversion, macro parsing/execution, or general binary conversion. Remaining scanner hardening is external scanner engines, deeper PDF/binary/archive scanning, scanner policy mutation/approval UX, stronger isolation, and node-pool scheduling/health.

## 2026-07-04 Update: Sandbox Runtime Node Health Visibility

Sandbox Operations now exposes a read-only runtime node health surface through `GET /api/sandbox/runtime/nodes`. The current implementation deliberately returns the single local runtime node derived from `SandboxRuntimeHealth`, with stable node id `local-<runtime>-<engine>`, admission status (`AVAILABLE`, `DEGRADED`, `DISK_LOW`, `SATURATED`, or `UNAVAILABLE`), active-session capacity, workspace disk status, and container inspection counters.

The admin Runtime governance panel loads the node list beside runtime health, profiles, and scanner policy. This is the first operator-facing node-health shape only: it does not add a node registry, multi-node placement, scheduler changes, remote runtime discovery, or destructive runtime behavior.

Fresh evidence: focused Java regression passed (`KernelSandboxRuntimeServiceTests`, `SeahorseSandboxControllerTests`, and `SandboxApiDisabledByDefaultTests`), frontend capability contracts passed 10/10, `npm run build` completed with existing chunk-size/Browserslist warnings, `git diff --check` passed, the full-compose backend rebuilt with 7890 proxy support and an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-runtime-node-health-smoke` passed 35/35 against the local full-Docker backend. Cleanup confirmed no leftover managed sandbox containers, zero non-terminal sandbox sessions, backend health `UP`, and the live node endpoint returned `local-container-docker|container|docker|HEALTHY|AVAILABLE`.

Remaining Sandbox productionization work is real node-pool scheduling/health beyond this single-node visibility shape, per-session disk quota enforcement, stronger runtime isolation, external scanner engines, richer archive/PDF/binary scanning, scanner policy mutation/approval UX, and broader Tool Gateway hardening.

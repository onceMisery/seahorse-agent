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
| `docs/design/agentscope-production-integration-plan.md` | 完整 Agent Card 删除或上游 registry deregister API、直接 OTEL/Studio trace 展示联调、Nacos/AgentScope 精确 config revision | release gate 与 full-Docker smoke 已覆盖真实模型 AgentScope/kernel SSE 等价、runId/snapshot 对齐和 trace snapshot 字段；仍有上游 registry 与生产联调缺口 | 近期/中期：AgentScope 生产硬化 | release gate 覆盖 shared-secret/tenant-signed live E2E；Studio/OTEL trace 能从 runId 反查 |
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
| P1 | AgentScope 生产硬化第一阶段 | 已落地：release gate、A2A 失败降级、Studio trace runId 反查快照、真实模型 AgentScope/kernel SSE 等价；剩余：直接 Studio/OTEL 生产联调 | AgentScope 失败不影响 kernel 普通聊天 |

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

## 2026-07-06 Update: Sandbox PDF Text Conversion Encrypted-PDF Guard

The conservative `sandbox_file_convert` PDF-to-text runtime script now explicitly rejects PDFs with an `/Encrypt` marker in the bounded prefix before attempting literal text extraction. This keeps the stdlib-only converter aligned with its documented non-encrypted PDF scope and fails closed instead of producing misleading partial text for encrypted documents.

This is a narrow document-conversion guard. It does not add PDF rendering, OCR, password handling, full PDF parsing, LibreOffice/Tika integration, or a general binary conversion engine.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 43/43.

## 2026-07-06 Update: Sandbox PDF Text Conversion Flate Budget Guard

The conservative `sandbox_file_convert` PDF-to-text runtime script now bounds each PDF `FlateDecode` stream decompression to 1 MiB before literal text extraction. Over-budget compressed streams fail closed with a value-free error instead of letting the stdlib-only converter expand unbounded stream data inside the file-conversion sandbox.

This is a narrow resource-boundary guard for the existing PDF literal-text path. It does not add full PDF parsing, rendering/OCR, external scanner engines, password handling, LibreOffice/Tika integration, or a general binary conversion engine.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 43/43.

## 2026-07-06 Update: Sandbox DOCX Text Conversion Document XML Budget Guard

The conservative `sandbox_file_convert` DOCX-to-text runtime script now checks the uncompressed `word/document.xml` ZIP entry size before XML parsing and rejects entries over 1 MiB with a value-free error. This keeps the stdlib-only DOCX path bounded to the intended small document-text extraction scope instead of reading arbitrary package XML into memory.

This is a narrow DOCX resource-boundary guard. It does not add Office rendering/editing, macro parsing, LibreOffice/Tika integration, recursive package extraction, or a general binary conversion engine.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 43/43; `git diff --check` passed.

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

## 2026-07-06 Update: Sandbox Browser Tool Gateway Audit Summary

Tool Gateway request audit now emits a `sandbox_browser`-specific argument summary for browser governance evidence. The summary records value-free execution posture fields such as `mode`, `networkRequested`, `allowedHosts`, cookie count, session-state replay/capture flags, session-state cookie/origin counts, HAR, and video flags.

The audit summary deliberately omits URL credential material, cookie values, and session-state/localStorage values; those remain governed by the existing request/runtime validation and transient input handling. This is a narrow audit-hardening slice only. It does not add durable credential storage, replaying previously captured SECRET/BLOCKED artifacts, operator approval UX, long-lived browser profiles, or broader cross-provider audit schema changes.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 14/14, including regression coverage that sandbox browser audit summaries include URL/egress/session governance metadata while excluding cookie and localStorage secret values.

## 2026-07-06 Update: AgentScope A2A Signed Header Boundary Guard

AgentScope inbound A2A `tenant-signed` authentication now rejects malformed signed headers before signature comparison and nonce-cache mutation. Tenant, agent, timestamp, and nonce headers have bounded lengths and control-character rejection; body-hash and signature headers must be 64-character SHA-256 hex strings.

This is a narrow production hardening slice for the existing tenant-signed A2A path. It does not add durable distributed nonce storage, live Nacos/Studio/OTEL production infrastructure, or new A2A routing behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeA2aServerControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 15/15, including malformed body-hash and over-boundary nonce rejection while preserving normal shared-secret and tenant-signed flows.

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

## 2026-07-04 Update: Sandbox Artifact TAR Archive Introspection

`DefaultSandboxArtifactScannerPort` now treats `application/x-tar` as a governed download-only archive media type. Clean TAR artifacts are copied to governed object storage and downloadable through artifact APIs, but remain prompt-hidden and are not included in sandbox-backed tool observations.

The scanner walks TAR headers directly without filesystem extraction: 512-byte blocks, checksum validation, regular files and directories only, at most 128 entries, and at most the first 256 KiB per regular file. It blocks unsafe paths, non-regular/link/special/extended metadata entries, executable entry names or PE/ELF signatures, and embedded PDF active-content markers with the existing value-free archive categories. Malformed, truncated, or bad-checksum TAR content fails closed with `ARCHIVE_SCAN_ERROR`.

Runtime media detection and artifact download filename mapping now preserve `.tar`, and the scanner policy includes `application/x-tar` in download-only, binary-signature-scanned, and archive-scanned media coverage. This is deliberately narrow: it does not add `tar.gz`, recursive extraction, PAX/GNU long-name support, external scanner engines, general archive/container extraction, or general binary conversion.

Fresh evidence: focused kernel/container regression passed 98/98 with reactor `BUILD SUCCESS`; `git diff --check` passed with only line-ending warnings; compose overlay validation passed; the full-compose backend image rebuilt through the local 7890 proxy path with an in-image Maven `BUILD SUCCESS`; `seahorse-backend` was recreated; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-tar-archive-smoke` passed 39/39. Cleanup confirmed no `seahorse-sandbox-*` containers, zero non-terminal sandbox sessions, and backend health `UP`.

## 2026-07-04 Update: Sandbox Artifact TAR.GZ Archive Introspection

`DefaultSandboxArtifactScannerPort` now treats `application/gzip` and `application/x-gzip` as governed download-only archive media types only when the artifact filename ends with `.tar.gz` or `.tgz`. Clean TAR.GZ artifacts are copied to governed object storage and downloadable through artifact APIs, but remain prompt-hidden and are not included in sandbox-backed tool observations.

The scanner uses `GZIPInputStream` with a 32 MiB decompressed-byte budget, then reuses the existing bounded TAR header walker. It keeps the same TAR constraints: no filesystem extraction, regular files and directories only, at most 128 entries, at most the first 256 KiB per regular file, checksum validation, unsafe-path blocking, executable name/signature blocking, PDF active-content marker blocking, and fail-closed handling for malformed or truncated content. Plain `.gz` files remain unsupported and fail closed.

Runtime media detection now maps `.tar.gz` and `.tgz` to `application/gzip`, artifact download filename mapping preserves `.tar.gz`, and the scanner policy reports gzip TAR media as download-only, binary-signature-scanned, and archive-scanned. This is deliberately narrow: it does not add generic gzip introspection, recursive decompression, PAX/GNU long-name support, external scanner engines, general archive/container extraction, or general binary conversion.

Fresh evidence: focused kernel/container regression passed 106/106 with reactor `BUILD SUCCESS`; compose overlay validation passed; the first backend image rebuild attempt hit a transient TLS EOF while downloading Maven through the local 7890 proxy, container-side proxy diagnostics then downloaded and verified the same Maven tarball, and the retry rebuilt the full-compose backend with an in-image Maven `BUILD SUCCESS`; `seahorse-backend` was recreated and health returned `{"status":"UP"}`; `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-targz-archive-smoke-review` passed 43/43. The smoke verified clean TAR.GZ artifacts are `application/gzip|CLEAN|INTERNAL`, `contentScanned=true`, prompt-hidden, copied to governed object storage, downloadable, and preserve expected content; unsafe TAR.GZ artifacts are `application/gzip|BLOCKED|CONFIDENTIAL` with `ARCHIVE_EXECUTABLE_BINARY`, not copied to object storage, not downloadable, prompt-hidden, and exposed through APIs without raw entry-name or storage-reference leakage. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health remained `UP`.

## 2026-07-04 Update: Sandbox Artifact Scanner Compressed Archive Budget Visibility

Sandbox artifact scanner policy visibility now includes the compressed-archive decompression byte budget as `maxCompressedArchiveDecompressedBytes`. The default local bounded scanner reports the TAR.GZ decompression ceiling as `33554432` bytes, the Web API exposes it through `GET /api/sandbox/runtime/artifact-scanner-policy`, the frontend service contract models it, and the admin Sandbox Runtime governance panel includes it in the scanner window summary.

This is an operator visibility/API contract slice only. It does not change scanner behavior, mutable policy handling, archive recursion, generic gzip scanning, external scanner integration, or artifact download/prompt visibility rules.

Fresh evidence: focused Java/Web regression passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am "-Dtest=DefaultSandboxArtifactScannerPortTests,SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; frontend capability contracts passed 10/10 and `npm run build` completed with existing Browserslist/chunk-size warnings; compose overlay validation passed; the full-compose backend image rebuilt through the local 7890 proxy path with an in-image Maven `BUILD SUCCESS`; `seahorse-backend` was recreated and `/actuator/health` returned `{"status":"UP"}`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-scanner-compressed-budget-smoke` passed 43/43, including the live scanner-policy assertion for `maxCompressedArchiveDecompressedBytes=33554432`. Cleanup confirmed no `seahorse-sandbox-*` containers and PostgreSQL reported zero non-terminal sandbox sessions.

## 2026-07-04 Update: Sandbox Artifact Scanner Full Window Visibility

The admin Sandbox Runtime governance panel now renders the full bounded scanner window in one operator summary: text content bytes, binary signature prefix bytes, archive entry count, per-entry archive prefix bytes, and compressed archive decompression bytes. The Web/API contract and full-Docker smoke now assert the already-exposed binary and per-entry archive limits so the UI cannot silently drift from the scanner policy payload.

This is scanner policy UX and verification only. It does not change scan behavior, artifact decisions, byte budgets, archive parsing, mutable policy handling, external scanner integration, prompt visibility, or download eligibility.

Fresh evidence: focused Web regression passed 3/3 with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseSandboxControllerTests,SandboxApiDisabledByDefaultTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; frontend capability contracts passed 10/10; PowerShell parsing returned `PSParser OK`; `npm run build` completed with existing Browserslist/chunk-size warnings; live backend health returned `{"status":"UP"}`; and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-scanner-window-ux-smoke-rerun` passed 43/43, including scanner policy assertions for `maxBinarySignatureScanBytes=262144`, `maxArchiveEntryScanBytes=262144`, and the existing text/archive/compressed limits. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health remained `UP`.

## 2026-07-04 Update: Sandbox TAR.GZ Decompression Budget E2E Guard

The artifact-storage full-Docker smoke now verifies the TAR.GZ decompression budget as a real fail-closed path, not only as a unit-tested scanner behavior or policy value. The sandbox run creates an `overbudget-bundle.tar.gz` whose compressed object is small but whose decompressed TAR stream exceeds the 32 MiB scanner budget. The scanner blocks it before object storage copy with `BLOCKED|SECRET`, summary `archive content scan failed`, and value-free `ARCHIVE_SCAN_ERROR` metadata.

The archive artifact API check also verifies that the over-budget artifact stays prompt-hidden, non-downloadable, and does not leak the inner `large.bin` entry name or any storage reference. This is verification hardening only; it does not change scanner limits, TAR.GZ parsing, generic gzip support, recursive extraction, or external scanner integration.

Fresh evidence: PowerShell parsing returned `PSParser OK`; `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-targz-budget-failclosed-smoke` passed 44/44 against the local full-Docker backend, including the new "Verify over-budget TAR.GZ archive is blocked before object storage" step. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health remained `UP`.

## 2026-07-04 Update: Sandbox Plain GZIP Fail-Closed E2E Guard

The artifact-storage full-Docker smoke now verifies the generic `.gz` non-goal as a real fail-closed path. The sandbox run creates `plain-bundle.gz`; runtime media detection records it as `application/gzip`, but the scanner rejects it because only `.tar.gz` and `.tgz` gzip-wrapped TAR archives are supported.

The new smoke step verifies `BLOCKED|SECRET`, summary `archive content scan failed`, and value-free `ARCHIVE_SCAN_ERROR` metadata before any object-storage copy. The archive artifact API check also verifies that the plain GZIP artifact stays prompt-hidden, non-downloadable, and does not leak storage references or compressed content markers. This is verification hardening only; it does not add generic gzip scanning, recursive extraction, or external scanner integration.

Fresh evidence: PowerShell parsing returned `PSParser OK`; backend health returned `{"status":"UP"}` before the run; `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-plain-gzip-failclosed-smoke-rerun` passed 45/45 against the local full-Docker backend, including the new "Verify plain GZIP archive is blocked before object storage" step. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health remained `UP`.

## 2026-07-04 Update: Sandbox Archive Unsafe Path E2E Guard

The artifact-storage full-Docker smoke now verifies unsafe archive entry paths through the real runtime path. The sandbox run creates `path-traversal-bundle.zip` with `../outside.txt`; the scanner blocks it before object storage copy as `BLOCKED|CONFIDENTIAL`, summary `unsafe archive entry`, and value-free `ARCHIVE_UNSAFE_ENTRY` metadata.

The archive artifact API check also verifies that the path-traversal artifact stays prompt-hidden, non-downloadable, and does not leak the raw entry name or any storage reference. This is verification hardening only; it does not change archive parsing, add extraction, add recursive scanning, or introduce an external scanner engine.

Fresh evidence: PowerShell parsing returned `PSParser OK`; `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-archive-path-guard-smoke` passed 46/46 against the local full-Docker backend, including the new "Verify path-traversal ZIP archive is blocked before object storage" step. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health remained `UP`.

## 2026-07-04 Update: Sandbox TAR Unsafe Path E2E Guard

The artifact-storage full-Docker smoke now verifies unsafe TAR entry paths through the real runtime path. The sandbox run creates `path-traversal-bundle.tar` with `../outside.txt`; the scanner blocks it before object storage copy as `BLOCKED|CONFIDENTIAL`, summary `unsafe archive entry`, and value-free `ARCHIVE_UNSAFE_ENTRY` metadata.

The archive artifact API check also verifies that the path-traversal TAR artifact stays prompt-hidden, non-downloadable, and does not leak the raw entry name or any storage reference. This is verification hardening only; it does not change TAR parsing, add extraction, add recursive scanning, or introduce an external scanner engine.

Fresh evidence: PowerShell parsing returned `PSParser OK`; `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-tar-path-guard-smoke` passed 47/47 against the local full-Docker backend, including the new "Verify path-traversal TAR archive is blocked before object storage" step. Cleanup confirmed no `seahorse-sandbox-*` containers, PostgreSQL reported zero non-terminal sandbox sessions, and backend health remained `UP`.

## 2026-07-04 Update: Sandbox Container Network Fail-Closed Guard

The container sandbox runtime now rejects `networkRequested=true` for non-browser runtimes before starting Docker. This closes a P1 policy/runtime gap: arbitrary Python and file-conversion containers do not yet have host-level egress filtering, so honoring `requestedHosts` there would otherwise grant unrestricted container networking after a policy allowlist check.

Browser URL mode remains the only container network path for now because it has the additional Playwright route allowlist and `allowedHosts` to `requestedHosts` runtime binding guard. Python host allowlist enforcement, proxy-based egress control, stronger runtime isolation, and node-level network policy remain follow-up production hardening work.

Fresh evidence: the new regression first failed as `expected: FAILED but was: SUCCEEDED`; after the guard, the focused regression passed 1/1, the full container adapter suite passed 35/35, and kernel sandbox/tool regressions passed 43/43.

## 2026-07-04 Update: AgentScope A2A Failure Degradation

`invoke_remote_a2a_agent` now turns remote connector failures into a governed tool failure that includes the target `agentName` while redacting the request prompt if it appears in the upstream exception message. This makes A2A remote failures easier to diagnose without leaking user prompt content into the failure summary.

This is a narrow P1 failure-degradation hardening slice. It does not add retry policy, alternate remote-agent fallback, live A2A deployment recovery, Studio trace lookup, or real-model SSE equivalence evidence.

Fresh evidence: the new regression first failed because the failure lacked `agentName=planner`; after the fix, the focused regression passed 1/1. The default AgentScope release gate also passed: adapter tests 107/107, kernel run contract 18/18, application smoke 1/1, bootstrap package success, and final `AGENTSCOPE_RELEASE_GATE=PASS`.

## 2026-07-04 Update: AgentScope Studio Trace Lookup Snapshot

AgentScope Studio-enabled runs now contribute immutable `agentScope` metadata even when Nacos config-center is disabled. The latest chat `RunContextSnapshot` includes that `agentScope` block and writes `studioUrl`, `tracingUrl`, and a derived `studioTraceUrl` into `traceContextJson` beside the Seahorse `traceId`, so `/api/run-context-snapshots/by-run/{runId}` can reverse lookup the Studio trace entry from the run id.

This is a narrow P1 runId trace-link snapshot hardening slice. It does not add dynamic AgentScope Studio SDK runId binding, a direct OTEL exporter, Jaeger/Tempo production联调, or real-model SSE equivalence evidence.

Fresh evidence: the new regressions first failed because Studio-only AgentScope auto-configuration produced no `AgentRunMetadataContributor`, the contributor returned empty metadata when config-center was disabled, and the latest chat snapshot lacked `agentScope`/Studio URL fields. After the fix, the focused AgentScope regressions passed 2/2 and the focused kernel chat snapshot regression passed 1/1. The default AgentScope release gate also passed: adapter tests 108/108, kernel run contract 19/19, application smoke package success, and final `AGENTSCOPE_RELEASE_GATE=PASS`.

## 2026-07-04 Update: AgentScope Real-Model SSE Equivalence

AgentScope run-profile chat now enters `ChatMode.AGENT` by default, and the frontend sends `chatMode=agent` whenever a run profile is selected. The full-Docker smoke parses SSE events for both AgentScope and kernel runs, requires `meta`, `message`, `finish`, `done`, non-empty response text, `stream_event` envelopes, no `error`/`recoverable_error`, and matching SSE/snapshot run ids.

The root-cause fix for the real-model failure keeps AgentScope's auto-configured model bridge from treating `executor.agentName` as a chat model id. When no run/profile model is specified, the bridge now leaves `modelId` empty so the configured model adapter can use its default chat model, matching kernel behavior.

Fresh evidence: focused model bridge and auto-configuration regressions passed 23/23, `.\scripts\agentscope-release-gate.ps1` passed with AgentScope adapter tests 111/111, kernel run contract 19/19, bootstrap package success, and final `AGENTSCOPE_RELEASE_GATE=PASS`. After rebuilding and redeploying the local full-Docker backend, `.\scripts\e2e-agentscope-smoke.ps1 -BaseUrl http://127.0.0.1:9090` passed 11/11 with AgentScope run `run_331647956607971328`, kernel run `run_331648011163283456`, parsed AgentScope events `agent.timeline,done,finish,message,meta,run_started,step_progress,stream_event`, and parsed kernel events `agent.timeline,done,finish,message,meta,run_started,step_finished,step_started,stream_event`.

## 2026-07-04 Update: Deployment Evidence Gate

The P2 deployment verification item is now closed by `scripts/deployment-evidence-gate.ps1`, which aggregates the existing full-Docker deployment smokes for S3 storage switching, Pulsar consume-loop processing, RAG strategy promotion, and Agent rollout promotion. The gate runs each smoke in an isolated PowerShell process, records per-step exit codes and durations, and fails closed when no steps are selected or when any child smoke fails.

Fresh evidence: PowerShell parsing returned `PSParser OK`; the script contract regression passed 1/1; and `.\scripts\deployment-evidence-gate.ps1 -BackendBaseUrl http://127.0.0.1:9090 -FrontendBaseUrl http://127.0.0.1 -Password admin123 -BackendImage seahorse-agent-backend` passed with `DEPLOYMENT_EVIDENCE_GATE=PASS`. The run verified S3 attachment upload/list/delete against MinIO and PostgreSQL, Pulsar publish/consume/ack counters and document materialization, RAG strategy promotion with audit evidence, and Agent rollout missing-gate failure plus successful full promotion with audit evidence. The temporary S3 smoke backend was removed after the run.

## 2026-07-05 Update: Context Pack Retention Cleanup

Context Pack productization now has a narrow executable retention cleanup path. The existing `expiresAt` item contract is enforced by `POST /api/context-packs/{contextPackId}/items:cleanup-expired`, which deletes expired items, keeps manually retained items with no expiry, refreshes `item_count`, and reuses the existing admin/owner access guard. The admin Context Pack page exposes the cleanup action and refreshes the pack and item table after execution.

This advances the roadmap's Pack Retention acceptance surface only. Pack Diff, handoff context transfer, scheduled retention jobs, and tenant policy editing remain follow-up Context Pack productization work.

Fresh evidence: focused Java regression passed with reactor `BUILD SUCCESS` across kernel, JDBC repository, Web controller, and autoconfigure Context Pack coverage; frontend capability contracts passed 12/12; and `npm run build` completed with only existing Browserslist/chunk-size warnings.

## 2026-07-05 Update: Context Pack Diff

Context Pack productization now includes a Pack Diff path. `GET /api/context-packs/{contextPackId}/diff?rightContextPackId=...` compares readable packs by stable source key (`sourceType:sourceId`) and returns added, removed, changed, and unchanged counts with changed field names and left/right item payloads. The admin Context Pack page can query a right-side pack and render a compact diff summary plus changed field list.

This advances the roadmap's Pack Diff acceptance surface only. Handoff context transfer, richer side-by-side item visualization, scheduled retention cleanup, and tenant policy editing remain follow-up Context Pack productization work.

Fresh evidence: focused Java regression passed with reactor `BUILD SUCCESS` across kernel, Web controller, and autoconfigure Context Pack coverage; frontend capability contracts passed 12/12; and `npm run build` completed with only existing Browserslist/chunk-size warnings.

## 2026-07-05 Update: Context Pack Handoff Reference

Context Pack productization now includes a narrow handoff transfer reference. `local_agent_handoff` accepts `contextPackId`, `AgentHandoffCreateCommand` carries it into the kernel, `AgentHandoff` persists it, and child A2A runs receive handoff metadata containing `handoffId`, `parentRunId`, `contextPackId`, and the reduced `contextSummaryJson` snapshot. The JDBC handoff repository stores `context_pack_id` through migration `V53__agent_handoff_context_pack_reference.sql`, and the Web API returns `contextPackId` on handoff list/detail/cancel responses while still omitting raw `inputSummaryJson` and `contextSummaryJson`.

The admin Agent Inspector handoff table now uses the backend handoff contract directly, including `sourceAgentId`, `targetAgentId`, backend status values, `handoffReason`, and `contextPackId`. This advances the roadmap's handoff context transfer acceptance surface by making delegated child runs traceable back to the Context Pack asset used for reduced transfer.

This is a transfer-reference slice only. It does not clone Context Pack rows for child runs, add a full side-by-side handoff context viewer, introduce tenant-specific handoff transfer policies, or perform a full-Docker multi-agent handoff E2E.

Fresh evidence: focused Java regression passed with reactor `BUILD SUCCESS` via `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am "-Dtest=AgentHandoffTests,KernelAgentHandoffServiceTests,JdbcAgentHandoffRepositoryAdapterTests,SeahorseAgentHandoffControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; frontend capability contracts passed 13/13 with agent handoff endpoint coverage; and `npm run build` completed with only existing Browserslist/chunk-size warnings.

## 2026-07-05 Update: Context Pack Handoff Full-Docker Guard

The Context Pack handoff reference now has a repeatable full-Docker smoke guard through `scripts/e2e-context-pack-handoff-smoke.ps1`. The smoke logs in, verifies `local_agent_handoff` Tool Gateway visibility, invokes a handoff carrying `contextPackId`, checks handoff list/detail sanitization, verifies child A2A run metadata carries `handoffId`, `parentRunId`, `contextPackId`, and `contextSummaryJson`, and confirms Tool Gateway audit records the invocation without leaking the raw marker in `argumentsSummary`.

The full-Docker run exposed an upgrade gap for existing PostgreSQL databases: `sa_agent_handoff.context_pack_id` existed in fresh init SQL and migration `V53`, but startup schema repair did not add the column for the long-lived local full-Docker database. `JdbcTenantSchemaUpgrade` now repairs that column idempotently so upgraded deployments match the current handoff repository contract.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc -am "-Dtest=JdbcTenantSchemaUpgradeTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 6/6; `docker compose -f docker-compose.full.yml build --build-arg HTTP_PROXY=http://192.168.1.9:7890 --build-arg HTTPS_PROXY=http://192.168.1.9:7890 backend` rebuilt the backend with in-image Maven `BUILD SUCCESS`; recreating backend logged `[TenantSchema] 为表 sa_agent_handoff 添加列 context_pack_id`; PostgreSQL confirmed `context_pack_id|character varying|YES`; and `.\scripts\e2e-context-pack-handoff-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-context-pack-handoff-smoke-live` passed 8/8 with handoff `handoff_332170575459106816`, child run `run_332170575517827072`, and context pack `context-pack-handoff-smoke-262751a7`.
## 2026-07-05 Update: Unified GateResult Projection

The medium-term GateResult route now has a narrow shared projection for the two existing gate owners. `GateResult` and `GateResultItem` live in the inbound gate contract package, with `GateResults` adapters projecting Agent `ProductionGateReport` and Run Profile `RunProfileProductionGateCheck` into a shared evidence shape: `subjectType`, `subjectId`, normalized `status`, `passed`, `blockingCodes`, `items`, `checkedAt`, `sourceType`, and `sourceId`.

The Web API exposes this shape through `GET /api/agents/{agentId}/production-gate/gate-result` and `POST /api/run-profiles/{id}/production-gate/gate-result`, while preserving the existing object-specific production gate endpoints. This is a compatibility projection slice only. It does not persist unified gate results yet, and does not add RAG Strategy, Model Config, Tool/Skill, or Ingestion Pipeline gate adapters.

Fresh evidence: focused projection and Web contract tests passed through `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am "-Dtest=GateResultsTests,SeahorseProductionGateControllerTests,SeahorseRunProfileControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`, covering 2/2 projection tests and 14/14 controller tests.

## 2026-07-05 Update: RAG Strategy GateResult Adapter

The unified GateResult route now covers RAG Strategy comparison evidence. `GateResults.fromRetrievalStrategyComparison` projects a saved `RetrievalEvaluationComparisonRecord` into `subjectType=RAG_STRATEGY`, using the existing promotion gates: baseline present, winner present, metrics present, evaluable cases present, recall/precision/MRR/NDCG not regressed, and empty recall rate not regressed.

The Web API exposes this projection through `GET /knowledge-base/{kbId}/retrieval-evaluation-datasets/{datasetId}/comparisons/{comparisonId}/gate-result`. Existing comparison and promotion endpoints remain unchanged. This is a projection adapter slice only; it does not persist unified gate rows or add Model Config, Tool/Skill, or Ingestion Pipeline adapters.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=GateResultsTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 4/4 projection tests; `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseRetrievalAndMemoryControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 20/20 Web contract tests; and `npm test -- src/services/frontendCapabilityContracts.test.ts src/services/serviceEndpointCoverage.test.ts` passed 15/15 frontend manifest/coverage tests.

## 2026-07-05 Update: Skill GateResult Adapter

The unified GateResult route now covers Skill revision security evidence. `GateResults.fromSkillRevision` projects the latest `AgentSkillRevision.scanDecision` into `subjectType=SKILL`, preserving `ALLOW -> PASS`, `WARN -> WARN` with non-blocking `passed=true`, and `BLOCK -> FAIL` with `SKILL_SECURITY_SCAN` in `blockingCodes`.

The Web API exposes this projection through `GET /api/skills/{name}/gate-result?tenantId=...`, using the existing Skill management port to resolve the skill and its latest revision. Existing skill create/update/install/enable/disable/history endpoints remain unchanged. This is a projection adapter slice only; it does not persist unified gate rows or add Model Config or Ingestion Pipeline adapters.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=GateResultsTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 6/6 projection tests; `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseSkillControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 3/3 Web contract tests; and `npm test -- src/services/frontendCapabilityContracts.test.ts src/services/serviceEndpointCoverage.test.ts` passed 15/15 frontend manifest/coverage tests.

## 2026-07-05 Update: Ingestion Pipeline GateResult Adapter

The unified GateResult route now covers Ingestion Pipeline structure evidence. `GateResults.fromIngestionPipeline` projects an `IngestionPipelineRecord` into `subjectType=INGESTION_PIPELINE`, checking the same static safety assumptions used before execution: nodes exist, node ids and types are present, node ids are unique, `nextNodeId` references resolve, and the configured chain is acyclic.

The Web API exposes this projection through `GET /ingestion/pipelines/{id}/gate-result`, reusing the existing ingestion pipeline management port. Existing pipeline CRUD and ingestion task execution endpoints remain unchanged. This is a projection adapter slice only; it does not execute the pipeline, validate plugin availability for each node type, persist unified gate rows, or add the Model Config adapter.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=GateResultsTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 8/8 projection tests; `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseIngestionAndIntentControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 16/16 Web contract tests; and `npm test -- src/services/frontendCapabilityContracts.test.ts src/services/serviceEndpointCoverage.test.ts` passed 16/16 frontend manifest/coverage tests.

## 2026-07-05 Update: Model Config GateResult Adapter

The unified GateResult route now covers Model Config integrity evidence. `GateResults.fromAiModelConfig` projects an `AiModelConfig` into `subjectType=MODEL_CONFIG`, checking that the config key, value, and type are present, JSON-typed values parse as JSON, and sensitive config keys such as API keys, secrets, tokens, passwords, or credentials are encrypted before production use.

The Web API exposes this projection through `GET /admin/ai-config/{key}/gate-result?tenantId=...`, reusing the existing AI model config repository and login guard. Existing AI config CRUD endpoints remain unchanged. This is a configuration integrity projection only; it does not execute provider health checks, run model quality evaluation, or persist unified gate rows.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=GateResultsTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 10/10 projection tests; `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=AiModelConfigControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 1/1 Web contract test; and `npm test -- src/services/frontendCapabilityContracts.test.ts src/services/serviceEndpointCoverage.test.ts` passed 17/17 frontend manifest/coverage tests.

## 2026-07-06 Update: Tool Catalog GateResult Adapter

The unified GateResult route now covers Tool Catalog release evidence. `GateResults.fromToolCatalogEntry` projects a `ToolCatalogEntry` into `subjectType=TOOL`, checking that the tool is enabled, declares risk and action metadata, requires approval for HIGH/CRITICAL risk, has an owner team warning signal, and carries valid JSON input/output schemas.

The Web API exposes this projection through `GET /api/tools/{toolId}/gate-result` and the non-proxy `/tools/{toolId}/gate-result` alias, reusing the existing tool catalog management port. Existing tool catalog list/detail/enable/disable endpoints remain unchanged. This is a catalog integrity projection only; it does not persist unified gate rows, execute provider health checks, or replace runtime Tool Gateway policy enforcement.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=GateResultsTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 13/13 projection tests; `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseAgentControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 17/17 Web contract tests; and `npm test -- src/services/frontendCapabilityContracts.test.ts src/services/serviceEndpointCoverage.test.ts` passed 17/17 frontend manifest/coverage tests.

The admin Tool Detail page now renders the Tool GateResult evidence directly beside the catalog metadata, including status, blocking code count, source, checked time, blocking codes, and individual check items. This keeps the unified evidence projection visible to operators without changing tool enable/disable behavior or runtime Tool Gateway policy enforcement.

Fresh UX evidence: `npm test -- src/pages/admin/tools/ToolDetailPage.test.tsx src/pages/admin/tools/ToolCatalogPage.test.tsx src/services/frontendCapabilityContracts.test.ts src/services/serviceEndpointCoverage.test.ts` passed 25/25 focused frontend tests.

## 2026-07-05 Update: Sandbox Browser Localhost/IP Egress Guard

The sandbox browser URL path now rejects localhost-style and IPv4-literal targets before creating a browser sandbox session, even when the caller includes those values in `allowedHosts`. The same guard is duplicated in the container runtime adapter input parser so direct runtime calls also fail closed before a Docker command is built. This tightens the P1 browser egress policy from "caller-listed host" to "caller-listed DNS host plus runtime profile/global sandbox policy".

This is a narrow SSRF/host-probing guard only. It does not add a general outbound proxy, DNS pinning, CIDR/private-network egress classification, IPv6 handling, mutable operator URL policy UX, or browser credential governance.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 13/13; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 37/37; and `git diff --check` passed with only CRLF warnings.

## 2026-07-06 Update: Sandbox Browser Single-Label Host Egress Guard

The sandbox browser URL guard now also rejects single-label DNS names such as `metadata` or `backend` in URL mode. Browser URL egress must use a dotted DNS host that is also present in `allowedHosts`, approved by the runtime profile/global sandbox policy, and rechecked by the container runtime parser before Docker execution.

This is a narrow SSRF/container-network probing guard. It preserves existing dotted test and full-Docker paths such as `example.test` and `host.docker.internal`, and still does not add a general outbound proxy, DNS pinning, CIDR/private-network egress classification, IPv6 handling, mutable operator URL policy UX, or browser credential governance.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 14/14; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 38/38.

## 2026-07-06 Update: Sandbox Browser DNS Label Egress Guard

The sandbox browser URL guard now rejects malformed dotted DNS hosts before session creation and before container execution. Each host label must be non-empty, no longer than 63 characters, and must not start or end with `-`; this closes malformed-label cases that still satisfy allowlist/profile checks but should not be accepted as browser egress targets.

This remains a narrow SSRF/container-network probing guard. It does not add DNS pinning, CIDR/private-network egress classification, IPv6 handling, a general outbound proxy, mutable operator URL policy UX, or browser credential governance.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 15/15; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 39/39.

## 2026-07-06 Update: Sandbox Browser IPv6 Literal Egress Guard

The sandbox browser URL guard now rejects IPv6 literal targets such as `http://[::1]:8080/...` before session creation and before container execution. The policy remains DNS-host based: URL mode must use a valid dotted DNS host that passes allowedHosts, runtime profile, and global sandbox policy checks.

This is a narrow SSRF/container-network probing guard. It does not add DNS pinning, CIDR/private-network egress classification, a general outbound proxy, mutable operator URL policy UX, or browser credential governance.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 20/20; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 44/44.

## 2026-07-06 Update: Sandbox Browser URL Userinfo Credential Guard

The sandbox browser URL path now rejects HTTP/HTTPS URLs containing `userinfo` credentials such as `user:password@host` before session creation and before container execution. This prevents URL-embedded secrets from entering generated browser scripts, observations, HAR/event metadata, or target URL summaries while the broader credential/session governance model remains explicit-cookie and explicit-session-state only.

This is a narrow URL/auth hygiene guard. It does not add a secret manager-backed browser credential flow, stored browser sessions, proxy-rich egress audit, DNS pinning, CIDR/private-network classification, or mutable operator URL policy UX.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 16/16; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 40/40.

## 2026-07-06 Update: Sandbox Browser URL Fragment Guard

The sandbox browser URL path now rejects HTTP/HTTPS URLs containing fragment identifiers such as `#access_token=...` before session creation and before container execution. This keeps fragment-carried secrets out of generated runtime input, observations, result JSON, HAR/event metadata, and target URL summaries while preserving the explicit cookie/session-state credential paths.

This is a narrow URL/auth hygiene guard. It does not add a secret manager-backed browser credential flow, stored browser sessions, proxy-rich egress audit, DNS pinning, CIDR/private-network classification, or mutable operator URL policy UX.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 21/21; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 45/45; `git diff --check` passed.

## 2026-07-06 Update: Sandbox Browser URL Credential Query Guard

The sandbox browser URL path now rejects HTTP/HTTPS URLs whose query parameter names are credential-shaped, including `access_token`, `client_secret`, `password`, `api_key`, `session_id`, and related token/secret names. The guard runs before session creation and before container execution, checks URL-decoded parameter names, and keeps ordinary non-credential queries such as `?q=roadmap` valid.

This is a narrow URL/auth hygiene guard. It does not add a secret manager-backed browser credential flow, stored browser sessions, proxy-rich egress audit, DNS pinning, CIDR/private-network classification, or mutable operator URL policy UX.

The credential-query guard now also treats semicolon-delimited query components as separate parameters, so URLs such as `?q=roadmap;access_token=...` fail closed before session creation and before container execution without echoing the credential-bearing query.

The same guard now canonicalizes bracketed query parameter names before matching, so structured forms such as `access_token[]=...` and `session[id]=...` inherit the same fail-closed credential-query behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 23/23; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 47/47; `git diff --check` passed.

## 2026-07-06 Update: Sandbox Browser URL Route Same-Origin Guard

The sandbox browser URL runtime route guard now allows network requests only to the initial target URL origin, including scheme, host, and effective port. `allowedHosts` remains the policy/profile authorization input, but the generated Playwright route no longer treats that host allowlist as permission to probe every port on the same host after navigation, redirect, or subresource loading.

The runtime route guard also rejects same-origin browser requests that carry URL credential material in userinfo, fragments, or credential-shaped query parameter names, including URL-decoded, semicolon-delimited, and bracketed query forms. This keeps page-initiated token URLs from being continued after the initial target URL has passed kernel/runtime input validation.

Blocked credential-bearing route URLs are also redacted before entering the generated HAR event model, preserving only the safe origin/path shape plus value-free redaction markers for userinfo, query, and fragment parts.

HAR event URL recording now also shortens allowed internal `data:` and `blob:` pseudo-URLs to value-free markers, keeping inline data and blob identifiers out of governed network artifacts while preserving the route allow/abort decision.

The browser result and captured session summary now apply the same value-free URL redaction to the final `page.url`, so page-side History API changes cannot move credential query strings, fragments, `data:`, or `blob:` payloads into governed JSON artifacts after navigation.

This is a narrow runtime egress hardening slice. It does not add a general outbound proxy, per-path URL policy UX, DNS pinning, CIDR/private-network classification, or long-lived browser profile management.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 40/40.

## 2026-07-06 Update: Sandbox Browser Target-Host Session Replay Guard

The sandbox browser URL path now requires request-scoped cookies and replayed session-state cookies/origins to match the target URL host, not merely any host listed in `allowedHosts`. This keeps extra policy-authorized hosts from carrying unrelated cookie or localStorage secrets into the transient browser runtime input after the runtime route guard was narrowed to the initial target origin.

This is a narrow auth/session hygiene guard. It does not add stored browser sessions, credential vault integration, replay from previously captured SECRET/BLOCKED artifacts, proxy-rich egress audit, or mutable operator URL policy UX.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 18/18; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 42/42.

## 2026-07-06 Update: Sandbox Browser SessionState Origin Replay Guard

The sandbox browser URL path now requires replayed `sessionState.origins[].origin` entries to match the target URL origin, including scheme, host, and effective port. Cookie replay remains host-scoped, but localStorage replay is origin-scoped so a request to `example.test:8080` cannot carry localStorage values for `example.test:9090` into the transient browser context.

This is a narrow request-scoped session replay hardening slice. It does not add stored browser profiles, credential vault integration, replay from previously captured SECRET/BLOCKED artifacts, proxy-rich egress audit, or operator-managed URL policy UX.

The replay origin guard now also requires `sessionState.origins[].origin` to be a pure origin with no userinfo, path, query, or fragment. Malformed or credential-bearing origin strings fail closed before session creation and before container execution without echoing the embedded secret material.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 27/27; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 51/51.

## 2026-07-06 Update: Sandbox Browser SessionState Capture Budget Guard

The sandbox browser URL runtime script now bounds captured Playwright storage-state output to 128 KiB before reporting the `browser-session-state.json` artifact. Over-budget captures delete the full state and summary files and fail closed with a value-free runtime error instead of collecting an unbounded SECRET artifact from page-controlled storage.

This is a narrow resource-boundary guard for request-scoped session capture. It does not add stored browser profiles, credential vault integration, replay from previously captured SECRET/BLOCKED artifacts, proxy-rich egress audit, or operator-managed URL policy UX.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 44/44; `git diff --check` passed.

## 2026-07-06 Update: Run Experiment Failure Report Hardening

Run Experiment report export now handles failed trials that have no output message and no executor error message. The output comparison section no longer dereferences a null `outputMessageId`, and the failures section treats `FAILED` trial status as reportable evidence even when the executor returned an empty error, using a stable `FAILED - no failure message recorded` explanation.

This is a narrow P1 report hardening slice for the existing report export path. It does not add new report formats, frontend previews, persisted report metadata, or full-Docker report export evidence.

Fresh evidence: the new focused regression first failed with a report export `NullPointerException`; after the fix, `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=KernelRunExperimentServiceTests#shouldExplainFailedTrialEvenWhenExecutorDoesNotReturnErrorMessage" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 1/1, and `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=KernelRunExperimentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 7/7.

## 2026-07-06 Update: Run Experiment Report Preview

The Run Experiment admin page now keeps the latest exported report visible after download. Operators can inspect the report file name, content type, character count, and full Markdown preview directly beside the trial comparison table instead of treating export as a blind file-only action.

This is a narrow productization slice for the existing report export workflow. It does not add persisted report metadata, server-side report history, PDF/HTML report formats, or a new report editor.

Fresh UX evidence: `npm test -- src/pages/admin/run-profiles/RunExperimentPage.test.tsx` passed 3/3 focused frontend tests, covering experiment creation, trial actions, report download, and the new report preview metadata/content rendering. `git diff --check` passed with only CRLF warnings.

## 2026-07-06 Update: Run Experiment Branch Evidence Report

Run Experiment report export now includes resolved message-branch evidence for trial outputs. The Evidence Index and Trial Export tables include a `Message Branch` column, each output comparison entry lists the output leaf branch position, and the reproduction appendix summarizes trial branch leaves with leaf message id, parent id, branch root id, and sibling sequence.

This is a narrow report-evidence slice for the existing Markdown export. It does not add a new branch graph API, frontend branch visualization, persisted report history, or full-Docker report export rerun.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=KernelRunExperimentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 7/7, including assertions for `Message Branch`, `leaf=301 parent=202 root=202 sibling=1`, and `Trial branch leaves`. `git diff --check` passed with only CRLF warnings.

The full-Docker run experiment smoke now asserts the same branch evidence in the exported report. The smoke checks `Message Branch`, `Trial branch leaves`, each trial `leaf=<outputMessageId>`, and each reproduction appendix `trial <id> -> leaf=<outputMessageId>` entry after report export.

Fresh full-Docker evidence: `docker compose -f docker-compose.full.yml build --build-arg HTTP_PROXY=http://192.168.1.9:7890 --build-arg HTTPS_PROXY=http://192.168.1.9:7890 backend` rebuilt the backend image with in-image Maven `BUILD SUCCESS`; recreating backend returned healthy; and `.\scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123` passed 11/11 with report `e2e-run-experiment-20260706030925-332236547099926528.md`.

## 2026-07-06 Update: Run Experiment Missing-Leaf Failure Report

Run Experiment report export now remains available when the real trial executor fails before producing a `runId` or output message, such as when the requested `baseLeafMessageId` is not present in the conversation tree. Snapshot lookup is null-safe for failed trials, so the report still renders the failed trial, unresolved output message, unresolved branch evidence, and executor failure explanation instead of throwing a report export `NullPointerException`.

This is a narrow P1 failure-report hardening slice for the existing Run Experiment export path. It does not add API-level preflight validation, new report formats, persisted report history, or full-Docker negative-path smoke coverage.

Fresh evidence: the new regression first failed with a report export `NullPointerException` after a real `KernelRunExperimentTrialExecutor` returned `base leaf message not found`; after the fix, `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=KernelRunExperimentServiceTests,KernelRunExperimentTrialExecutorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 10/10.

The full-Docker run experiment smoke now also covers this negative path. After the normal multi-profile happy path report export, the smoke creates a second experiment with a missing `baseLeafMessageId`, asserts the experiment and trial become `FAILED`, exports the failure report, and checks that the Markdown includes `base leaf message not found`, `Output message ID: -`, and `Message branch: not resolved`.

Fresh full-Docker evidence: `docker compose -f docker-compose.full.yml build --build-arg HTTP_PROXY=http://192.168.1.9:7890 --build-arg HTTPS_PROXY=http://192.168.1.9:7890 backend` rebuilt the backend image with in-image Maven `BUILD SUCCESS`; recreating backend returned healthy; and `.\scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123` passed 12/12 with report `e2e-run-experiment-20260706032945-332241664076988416.md` and missing-leaf report `e2e-run-experiment-missing-leaf-20260706033002-332241734428049408.md`.

## 2026-07-06 Update: Ingestion Pipeline GateResult UX

The Ingestion Pipeline admin table now exposes the existing unified `GateResult` projection directly from each pipeline row. Operators can open a read-only `Pipeline GateResult` dialog to inspect subject, status, source, checked time, blocking codes, and per-check evidence from `GET /ingestion/pipelines/{id}/gate-result` without leaving the ingestion management page.

This is a narrow unified GateResult productization slice. It does not add new ingestion gate rules, persisted gate-result rows, release approval workflows, or full-Docker UI smoke coverage.

Fresh UX evidence: `npm test -- src/pages/admin/ingestion/IngestionPage.test.tsx` passed 1/1 focused frontend test, covering row action invocation of `getIngestionPipelineGateResult` and rendering of `INGESTION_PIPELINE`, blocking code, source, and check-message evidence. `npm run build` completed successfully with the existing Browserslist/chunk-size warnings.

## 2026-07-06 Update: Skill GateResult UX

The Skill Management admin page now exposes the existing unified `GateResult` projection directly from each skill row. Operators can open a read-only `Skill GateResult` dialog to inspect subject, status, source, checked time, blocking codes, and per-check evidence from `GET /api/skills/{name}/gate-result` without leaving skill management.

This is a narrow unified GateResult productization slice. It does not add new skill gate rules, persisted gate-result rows, release approval workflows, or full-Docker UI smoke coverage.

Fresh UX evidence: `npm test -- src/pages/admin/skills/SkillManagementPage.test.tsx` passed 2/2 focused frontend tests, covering row action invocation of `getSkillGateResult` and rendering of `SKILL`, blocking code, source, and check-message evidence.

## 2026-07-06 Update: Model Config GateResult UX

The Model Config admin page now exposes the existing unified `GateResult` projection from the model registry header. Operators can open a read-only `Model Config GateResult` dialog for `ai.models` to inspect subject, status, source, checked time, blocking codes, and per-check evidence from `GET /admin/ai-config/{key}/gate-result` without leaving model management.

This is a narrow unified GateResult productization slice. It does not add new model quality checks, provider health checks, persisted gate-result rows, release approval workflows, or full-Docker UI smoke coverage.

Fresh UX evidence: `npm test -- src/pages/admin/settings/ModelConfigPage.test.tsx` passed 1/1 focused frontend test, covering row/header action invocation of `getAiModelConfigGateResult` for `ai.models` with the active tenant and rendering of `MODEL_CONFIG`, blocking code, source, and check-message evidence. `npm run build` completed successfully with the existing Browserslist/chunk-size warnings.

## 2026-07-06 Update: Run Profile GateResult UX

The Run Profile admin table now exposes the existing unified `GateResult` projection directly from each run profile row. Operators can open a read-only `Run Profile GateResult` evidence panel to inspect subject, status, source, checked time, blocking codes, and per-check evidence from `POST /api/run-profiles/{id}/production-gate/gate-result` without replacing the existing object-specific production gate check and approval workflow.

This is a narrow unified GateResult productization slice. It does not add new run profile gate rules, persisted gate-result rows, release approval workflows, or full-Docker UI smoke coverage.

Fresh UX evidence: `npm test -- src/pages/admin/run-profiles/RunProfilePage.test.tsx src/services/runProfileService.test.ts` passed 21/21 focused frontend tests, covering the service endpoint, row action invocation of `getRunProfileGateResult`, rendering of `RUN_PROFILE`, blocking code, source, and check-message evidence, and isolated role-card form behavior.

## 2026-07-06 Update: Agent GateResult UX

The Agent detail page now exposes the existing unified `GateResult` projection from a dedicated `GateResult` tab. Operators can inspect subject, status, source, checked time, blocking codes, and per-check evidence from `GET /api/agents/{agentId}/production-gate/gate-result` without replacing the existing publish checks, validation, or production gate workflow.

This is a narrow unified GateResult productization slice. It does not add new agent gate rules, persisted gate-result rows, release approval workflows, or full-Docker UI smoke coverage.

Fresh UX evidence: `npm test -- src/pages/admin/agents/AgentDetailPage.test.tsx src/services/frontendCapabilityContracts.test.ts` passed 17/17 focused frontend tests, covering the service endpoint, Agent detail `GateResult` tab rendering of `AGENT`, blocking code, source, and check-message evidence. `npm run build` completed successfully with the existing Browserslist/chunk-size warnings.

## 2026-07-06 Update: RAG Strategy GateResult UX

The RAG evaluation dataset detail page now exposes the existing unified `GateResult` projection from each strategy comparison row. Operators can open a read-only `RAG Strategy GateResult` dialog to inspect subject, status, source, checked time, blocking codes, and per-check evidence from `GET /knowledge-base/{kbId}/retrieval-evaluation-datasets/{datasetId}/comparisons/{comparisonId}/gate-result` without replacing the existing comparison or promotion workflow.

This is a narrow unified GateResult productization slice. It does not add new RAG evaluation gates, persisted gate-result rows, release approval workflows, or full-Docker UI smoke coverage.

Fresh UX evidence: `npm test -- src/pages/admin/rag-evaluation/RetrievalDatasetDetailPage.test.tsx src/services/frontendCapabilityContracts.test.ts` passed 18/18 focused frontend tests, covering the service endpoint, comparison row action invocation of `getRetrievalComparisonGateResult`, rendering of `RAG_STRATEGY`, blocking code, source, and check-message evidence. `npm run build` completed successfully with the existing Browserslist/chunk-size warnings.

## 2026-07-06 Update: Remote A2A Tool Gateway Audit Hardening

`invoke_remote_a2a_agent` now bounds the cross-provider A2A request envelope before invoking the connector: `agentName`, `prompt`, metadata entry count, metadata key length, metadata value length, and disallowed control characters are rejected at the AgentScope tool adapter boundary. This keeps unbounded metadata from crossing the A2A connector while preserving the normal prompt payload path.

The Tool Gateway audit summary now has a dedicated value-free `invoke_remote_a2a_agent` projection. It records target agent name, prompt length, metadata keys/count, and requested version when present, without storing the raw prompt or metadata values in `argumentsSummary`.

This is a narrow A2A/Tool Gateway governance slice. It does not add cross-provider rate limits, remote content redaction, remote agent trust scoring, or full-Docker multi-agent smoke coverage.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeA2AToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 4/4; `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 15/15.

## 2026-07-06 Update: Sandbox File Conversion Active-Content Preflight

`sandbox_file_convert` now preflights base64 DOCX/PDF inputs in the container adapter before writing converter scripts, writing decoded input files, or starting Docker/Podman. DOCX inputs must be inspectable bounded ZIP packages with `word/document.xml`; unsafe paths, too many entries, macro projects, ActiveX, embedded OLE/object payloads, and external links fail closed. PDF inputs must have a PDF header and fail closed on encrypted or active-content markers such as JavaScript/OpenAction/AA in the bounded prefix.

This keeps the existing conservative text-extraction path aligned with its no-render/no-edit scope. It does not add LibreOffice/Tika, PDF rendering/OCR, Office rendering/editing, password handling, or general binary conversion.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 53/53, including regressions that DOCX/PDF active content is rejected before container command execution and before `main.py` or decoded input files are written.

## 2026-07-06 Update: Sandbox File Conversion Tool Gateway Audit Summary

Tool Gateway request audit now emits a `sandbox_file_convert`-specific value-free argument summary. The summary records `FILE_CONVERSION` runtime posture, source/target formats, content encoding, input content length, binary-input classification, network posture, and argument keys while excluding raw file content and base64 values from `argumentsSummary`.

This is a narrow cross-tool audit hardening slice. It does not change file conversion execution semantics, artifact scanning, approval policy, quota policy, or add broader binary conversion formats.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 16/16, including regression coverage that `sandbox_file_convert` audit summaries include governance metadata while excluding raw base64/content markers.

## 2026-07-06 Update: Sandbox Python Tool Gateway Audit Summary

Tool Gateway request audit now emits a `sandbox_python`-specific value-free argument summary. The summary records `CODE_INTERPRETER` runtime posture, code length, network request posture, requested host metadata, requested host count, and argument keys while excluding raw Python code from `argumentsSummary`.

This is a narrow Tool Gateway audit hardening slice for the existing Code Interpreter path. It does not change sandbox execution, network enforcement, artifact scanning, approval policy, or quota policy.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed after adding regression coverage that `sandbox_python` audit summaries include governance metadata while excluding raw code markers.

## 2026-07-06 Update: Sandbox Built-in Tool Catalog Approval Defaults

Built-in sandbox tools now enter the Tool Catalog with `requiresApproval=true` while keeping their existing `HIGH` risk level, `EXECUTE` action type, and `SANDBOX` resource type. This closes the gap where `sandbox_python`, `sandbox_file_convert`, and `sandbox_browser` were cataloged as high-risk but not explicitly approval-required, even though the policy layer only enforces approval from catalog flags, critical risk, or specific action types.

This is a catalog-registration hardening slice only. It does not change sandbox execution behavior, per-agent binding limits, quota policy semantics, MCP/OpenAPI registration, or non-sandbox built-in tool defaults.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed after updating regression coverage that all three built-in sandbox tools are saved with `requiresApproval=true`.

## 2026-07-06 Update: Remote A2A Built-in Tool Catalog Approval Defaults

The built-in Tool Catalog registrar now recognizes `invoke_remote_a2a_agent` as a remote-agent execution tool when the AgentScope A2A adapter contributes it as a `DescribedToolPort`. It is saved as `HIGH` risk, `EXECUTE`, `REMOTE_AGENT`, and `requiresApproval=true`, instead of falling back to the generic low-risk built-in metadata.

This closes a cross-provider governance gap at registration time only. It does not change AgentScope discovery, request signing, connector invocation, Tool Gateway audit summaries, or approval decision semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed after adding regression coverage for the remote A2A catalog projection.

## 2026-07-06 Update: OpenAPI Tool Gateway Audit Summary

Tool Gateway request audit now emits an `openapi_` dynamic-tool-specific value-free argument summary. The summary records the OpenAPI provider marker, argument keys/count, path/query/parameter/header key partitions, request body presence/type, and body field count or string length while excluding raw parameter, header, and body values from `argumentsSummary`.

This is a narrow cross-provider audit hardening slice. It does not change OpenAPI connector import, credential injection, HTTP invocation, response redaction, approval policy, or quota policy semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed after adding regression coverage that OpenAPI audit summaries include governance metadata while excluding path/query/parameter/header/body secret markers.

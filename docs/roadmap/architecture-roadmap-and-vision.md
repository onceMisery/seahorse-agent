# 架构路线图与未来展望

## 2026-07-14 Update: Non-Root Container Sandbox Execution

Container sandbox processes now run as the dedicated non-root `65532:65532` identity by default. Session workspace preparation grants only the per-session directory the access needed for that identity to write generated output; the existing read-only root filesystem, dropped capabilities, `no-new-privileges`, network boundary, and file-size limit remain in effect. The project browser runtime image now includes the same named system user, with temporary HOME/cache paths rooted in the existing writable `/tmp` tmpfs.

The bounded `sandbox_file_convert` path now also renders base64 `DOCX` and `PPTX` input to PDF through a dedicated non-root LibreOffice runtime image, tagged `seahorse-sandbox-office:libreoffice-7.4.7-bookworm` to match the packaged Debian runtime. Only those Office-backed conversions select that image; all other conversion formats retain the lightweight Python runtime. The generated PDF remains subject to the existing artifact scanner, storage, and governed-download flow. PDF initial-view `/OpenAction` entries are permitted because they are not executable active content; script, launch, remote-navigation, embedded-file, rich-media, and form-submit markers remain fail-closed.

The same dedicated runtime now renders the first page of a base64 PDF to PNG through Poppler. Rendering is fixed to a single page and a 2048px longest-edge bound; callers cannot select pages, scale, or arbitrary Poppler options. The output continues through the ordinary artifact scan and governed-download pipeline. This is rendering only, not OCR, PDF editing, or a general image-conversion engine.

`PDF -> ocr_txt` now provides a separate bounded OCR path. It rasterizes only the first page at the same 2048px bound and runs the fixed English Tesseract model; it does not change the existing conservative PDF literal-text extraction. The OCR output is persisted as a normal `text/plain` artifact and remains subject to scanning and governed download.

The Office runtime now also supports bounded `HTML -> DOCX` generation through LibreOffice's explicit `Office Open XML Text` export filter. The generated DOCX is scanned and persisted as a governed binary artifact. It is intentionally not prompt-visible, so the Tool Gateway retains the session reference while operators retrieve it through the normal artifact API; this keeps arbitrary document packages out of model context.

The same runtime supports bounded `CSV -> XLSX` creation through LibreOffice Calc's explicit `Calc MS Excel 2007 XML` export filter. It complements the existing conservative XLSX-to-CSV reader without exposing spreadsheet formulas, macros, or arbitrary LibreOffice options. Generated XLSX packages remain non-prompt-visible governed binary artifacts.

To prevent CSV formula injection from becoming executable workbook content, CSV-to-XLSX rejects cells whose trimmed value begins with `=`, `+`, or `@`, plus non-numeric `-` expressions, before LibreOffice starts. Ordinary signed numeric values remain supported. The rejection is value-free and does not emit a workbook artifact.

Calc-backed `XLSX -> PDF` rendering is also available through the same fixed Office export path used for documents and presentations. The generated PDF stays within the normal scanner and governed-download pipeline; this does not expose worksheet macro execution, print-configuration options, or arbitrary export arguments.

`PPTX -> PNG` now renders only the first slide by exporting through Impress to a temporary PDF and rasterizing it through Poppler at the fixed 2048px longest-edge bound. Callers cannot select slide indices, export options, or arbitrary command arguments.

The same bounded render path now produces a first-page or first-sheet PNG preview for supported Office packages: `DOCX -> PNG`, `XLSX -> PNG`, `PPTX -> PNG`, `ODT -> PNG`, `ODS -> PNG`, and `ODP -> PNG`. It always exports through the fixed LibreOffice PDF stage, rasterizes only page one through Poppler at the existing 2048px bound, and exposes no page, sheet, scale, or command arguments.

The same fixed LibreOffice PDF path now accepts OpenDocument text, spreadsheet, and presentation packages: `ODT -> PDF`, `ODS -> PDF`, and `ODP -> PDF`. They use the existing no-network `FILE_CONVERSION` runtime and Office image selection, expose no caller-controlled LibreOffice arguments, and retain the ordinary scanner, object-storage, governed-download, and audit controls.

Fresh real full-Docker evidence: the Office image was built through the local `7890` proxy, a direct read-only/no-network/capability-dropped UID `65532` DOCX render probe produced a valid `%PDF` artifact, and real Tool Gateway DOCX-to-PDF and PPTX-to-PDF flows completed after approval with `SUCCEEDED`, `application/pdf`, and ClamAV `CLEAN`; the DOCX path additionally verified persisted artifact storage and a governed download whose first bytes were `%PDF`. Docker Desktop host-drive mount sources are normalized to its daemon-visible `/run/desktop/mnt/host/<drive>/...` form before child-container execution so produced artifacts are collected from the same session workspace.

The Poppler-enabled Office image was rebuilt through the same local proxy and a real Tool Gateway PDF-to-PNG invocation, using a previously governed clean PDF as its real binary input, completed after approval with `SUCCEEDED`, `image/png`, and `CLEAN` scan status.

The OCR-enabled Office image was rebuilt through the same proxy and exposed the `eng` Tesseract model. A real full-Docker Tool Gateway PDF-to-`ocr_txt` invocation used a visibly rendered text PDF fixture, completed after approval with `SUCCEEDED` and a `CLEAN` `text/plain` artifact, and its governed download contained `SEAHORSE OCR E2E VISIBLE TEXT 314159`.

A real full-Docker Tool Gateway HTML-to-DOCX invocation completed after approval with `SUCCEEDED`; the generated DOCX was scanned `CLEAN`, persisted to object storage, downloaded through the governed artifact endpoint, and its `word/document.xml` contained `SEAHORSE HTML DOCX E2E`.

A real full-Docker Tool Gateway CSV-to-XLSX invocation completed after approval with `SUCCEEDED`; its XLSX artifact was scanned `CLEAN`, persisted, downloaded through the governed endpoint, and `xl/sharedStrings.xml` retained the submitted `Ada` and `Grace` cell values. This flow is now repeatable through `scripts/e2e-sandbox-csv-xlsx-tool-smoke.ps1`; its full-Docker run passed with real approval, agent-run binding, PostgreSQL artifact persistence, governed download, and XLSX package-content assertions.

The full sandbox overlay was recreated with the exact `libreoffice-7.4.7-bookworm` default tag and the same CSV-to-XLSX real smoke passed under that runtime configuration.

After the Office/PDF rendering and conversion extensions, `scripts/e2e-sandbox-file-convert-tool-smoke.ps1` passed `74/74` against the real full-Docker backend. The regression run covered governed approvals, document/table conversion paths, artifact scanning and local object storage, governed downloads, redacted Tool Gateway audit summaries, and session cleanup.

A real full-Docker formula-injection case submitted a CSV cell beginning with `=HYPERLINK(...)` through the approved Tool Gateway path. It failed closed before XLSX creation with the value-free formula-content error and no submitted formula text in the response.

A real full-Docker CSV-to-XLSX run containing the ordinary numeric value `-42` completed after approval with a `CLEAN` XLSX artifact, confirming the formula guard does not reject signed numeric data.

A real full-Docker Tool Gateway XLSX-to-PDF invocation, using a previously governed clean XLSX as its binary input, completed after approval with `SUCCEEDED`, `application/pdf`, and `CLEAN` scan status; the governed artifact download began with `%PDF`.

A real full-Docker Tool Gateway PPTX-to-PNG invocation completed after approval with `SUCCEEDED`, `image/png`, and `CLEAN` scan status; its governed artifact download had the PNG magic bytes.

Fresh real full-Docker evidence: `scripts/e2e-sandbox-file-convert-tool-smoke.ps1` now generates valid ODT and ODP fixtures with the same pinned LibreOffice runtime, then invokes both conversions through the real Tool Gateway. The full regression passed `82/82`, including approval, `SUCCEEDED`, `application/pdf`, ClamAV `CLEAN`, PostgreSQL persistence, local object storage, governed `%PDF` download, and value-free audit summaries for both ODF paths.

The same real full-Docker smoke now generates an ODS fixture through LibreOffice Calc from CSV and verifies `ODS -> PDF` through the Tool Gateway. The full regression passed `86/86`, including approval, `SUCCEEDED`, `application/pdf`, ClamAV `CLEAN`, PostgreSQL persistence, local object storage, governed `%PDF` download, and a value-free ODS audit summary.

Fresh real full-Docker evidence: the same smoke used valid LibreOffice-generated ODT, ODS, and ODP packages for all three bounded PNG previews. The full regression passed `98/98`, including approval, `SUCCEEDED`, `image/png`, ClamAV `CLEAN`, PostgreSQL persistence, local object storage, governed PNG-magic-byte downloads, and value-free Tool Gateway audit summaries.

The same real full-Docker smoke now creates valid DOCX and XLSX packages through the pinned LibreOffice runtime and verifies their bounded PNG previews. The full regression passed `106/106`, including approval, `SUCCEEDED`, `image/png`, ClamAV `CLEAN`, PostgreSQL persistence, local object storage, governed PNG-magic-byte downloads, and value-free Tool Gateway audit summaries.

ODF package preflight now reads bounded `content.xml`, `styles.xml`, `settings.xml`, and `meta.xml` entries before LibreOffice receives the file and rejects external `http`, `https`, `ftp`, or `file` link references without retaining the raw URL. A real Tool Gateway ODT negative case with an `xlink:href` URL in `styles.xml` failed closed before runtime execution, while the response and persisted audit summary excluded both the URL marker and document payload; the full Docker regression remained `106/106`.

Fresh real Docker evidence: the browser image was rebuilt through the local `7890` proxy, a direct non-root/read-only Chromium probe succeeded, `scripts/e2e-sandbox-python-tool-smoke.ps1` passed 5/5 with an in-sandbox effective-UID non-root assertion, and `scripts/e2e-sandbox-browser-tool-smoke.ps1 -SkipBrowserImageBuild` passed 37/37. The browser E2E covers inline and URL execution, DNS fail-closed behavior, session capture/replay, governed Profile lifecycle, HAR/video artifacts, audit summaries, and no leftover managed containers or non-terminal sessions. Its real 429 handling now uses bounded retry only for rate-limit responses.

## 2026-07-14 Update: Bounded PDF Tail Scanning and File Quota Correction

The local bounded scanner now checks both the leading and trailing `256 KiB` windows of a local PDF for active-content markers. This closes the simple bypass where a `/JavaScript`, `/OpenAction`, or other active action appeared only after the leading scan window. Both windows remain bounded; the scanner does not render or fully parse PDFs, extract attachments, perform OCR, or retain raw marker values.

This slice also corrects the container `fsize` ulimit unit passed to Docker. The adapter now supplies the configured byte value directly, so the default `max-session-file-bytes=67108864` produces a real 64 MiB process file limit instead of an unintended roughly 128 KiB limit. Runtime health and actual process enforcement now agree.

Fresh real Docker evidence: the full bootstrap reactor package completed successfully and `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -VerifyExternalVirusScanner` passed 54/54. A real Tool Gateway sandbox execution wrote a PDF with `/JavaScript` beyond the leading 256 KiB; the artifact was blocked before object storage as `PDF_ACTIVE_CONTENT`, with a value-free persisted/API summary.

The container runtime now applies `max-session-file-bytes` as a cumulative workspace budget before artifact publication, in addition to the existing per-process `fsize` ulimit. On successful container exit it sums every regular file in the session workspace, including internal inputs and scripts, and fails closed before scanner or object-storage work when the total exceeds the configured limit. A real full-Docker `sandbox_python` E2E created two separate 40 MiB files under the 64 MiB per-file process limit; the 80 MiB workspace was rejected with no observation content or artifact publication, and the matching Tool Gateway audit was `FAILED` without leaking the quota probe code or filenames.

Sandbox Operations now labels this same health value as `Session workspace quota`, making clear that the 64 MiB posture bounds the aggregate sandbox workspace rather than an individual uploaded file. Real Playwright E2E passed against the full Docker frontend/backend with the external ClamAV scanner enabled, covering the quota label and value, isolation posture, scanner health, and existing quota/egress policy save-and-restore flow.

## 2026-07-14 Update: Sandbox Artifact Scanner Health

Sandbox Operations now exposes `GET /api/sandbox/runtime/artifact-scanner-health`. The read-only health projection reports scanner id, mode, external-engine posture, and an `AVAILABLE` or `UNAVAILABLE` result without exposing scanner hostnames, ports, signatures, paths, or exception text. The external ClamAV adapter actively probes clamd with its `PING` protocol; the default bounded local scanner reports available without adding a network dependency.

Fresh real Docker evidence: `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -VerifyExternalVirusScanner` passed 53/53, including a live ClamAV health probe and the existing signed-malware artifact block, storage, API redaction, and node-assignment assertions.

## 2026-07-14 Update: Sandbox Session Node Assignment

Sandbox sessions now persist the runtime node selected at admission. The current scheduler deliberately selects the single healthy local container node (`local-container-docker`) only after the existing runtime admission checks pass; rejected sessions have no node assignment. `runtime_node_id` is stored on `sa_sandbox_session`, exposed through the existing session API, and retained through close and timeout transitions. This makes node assignment auditable today without claiming remote execution, multi-node placement, migration, or failover that do not exist yet.

Fresh real Docker evidence: the full bootstrap reactor rebuilt successfully and `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -VerifyExternalVirusScanner` passed 52/52. The E2E creates a real Tool Gateway sandbox session and verifies `runtime_node_id=local-container-docker` in PostgreSQL and `runtimeNodeId` through the session API alongside governed artifact storage and scanner checks.

## 2026-07-14 Update: Sandbox External ClamAV Artifact Scanning

The optional container sandbox overlay now supports an external ClamAV scanner. When `external-virus-scanner-enabled=true`, local `file://` artifacts stream to clamd using its INSTREAM protocol before the existing bounded local policy is applied. A malware hit is persisted only as `BLOCKED|CONFIDENTIAL` with the value-free `MALWARE` category; scanner unavailability, malformed responses, and unsupported sources fail closed as `EXTERNAL_SCAN_ERROR`. Raw signature names, paths, and storage references are not exposed in API responses.

`docker-compose.sandbox.yml --profile external-virus-scanner` provides the internal-only ClamAV service. The scanner remains opt-in and the default `default-local-bounded` scanner behavior is unchanged. Real Docker evidence passed 52/52 through `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -VerifyExternalVirusScanner`: a clean artifact was governed normally, while a real clamd custom test signature blocked the matching artifact before object storage, prompt visibility, and downloads. A custom harmless signature is used because the Docker Desktop host mount refuses EICAR file reads before clamd can inspect them.

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
| `docs/design/apix-inspired-feature-evolution-roadmap.md`、`docs/design/apix-inspired-phased-implementation-design.md` | MCP stdio 治理升级：命令 allowlist、runner 隔离、风险自动标记、高风险默认禁用、审批/审计/脱敏、stderr tail 与诊断闭环 | 基础 stdio/HTTP MCP 已归档完成；命令 allowlist、近端 runner 环境隔离、HIGH/需审批默认标记、诊断审批入口、诊断执行网关 fail-closed、OpenAPI enabled operation 动态进入 Tool Gateway/真实 HTTP invoke/audit、Sandbox runtime lifecycle close 透传、Sandbox execution history API/UI、Sandbox artifact scanner/prompt visibility gate、Docker/Podman Code Interpreter 容器 adapter 最小闭环、`sandbox_python` Tool Gateway 工具链路、`sandbox_file_convert` CSV/TSV/JSON 表格转换、txt/html/markdown 文本文档转换与 base64 `docx -> txt`/`pdf -> txt` 保守文档文本提取工具链路、full-compose backend host-socket/tooling opt-in 接入、受限 inline no-network `sandbox_browser`、请求级 URL allowlist egress、URL/host/secret guard、cookie/session-state capture/replay、session-state artifact replay、HAR artifact capture、download-only video capture、MCP HTTP 与远程 A2A 跨 provider Tool Gateway audit smoke、真实执行 artifact collection 和 stderr/响应脱敏已有 full-Docker 证据；剩余 proxy-rich egress、持久浏览器凭证/长生命周期 profile 与审批 UX、PDF 渲染/OCR、Office 渲染/编辑、LibreOffice/Tika、通用二进制转换、外部扫描引擎/更深 PDF-binary scanning、gVisor/Firecracker 等强隔离、node-pool 调度/健康和更广 Tool Gateway 产品化仍需推进 | 近期/中期：Tool Gateway 与 MCP 安全治理 | 高风险 MCP 工具默认不可直接运行；审批通过后真实调用落审计；失败时 UI 展示 stderr/诊断且不影响普通聊天 |
| `docs/design/apix-inspired-phased-implementation-design.md` | Agent Workbench：把消息分支、运行方案、实验、发布门禁、A2A、Studio trace 聚合成一个调试和发布工作台 | Chat Workspace/Inspector 和 Admin 页面仍分散 | 远期：统一 Agent 工作台 | 一个入口完成分支选择、运行方案切换、实验对比、trace/Studio 跳转、发布门禁检查和回滚 |
| `docs/design/apix-inspired-feature-evolution-roadmap.md` | MCP Marketplace、Profile Marketplace、自学习闭环 | Agent Marketplace 基座已归档完成；MCP/Profile 市场和自学习闭环未落地 | 远期：企业资产市场与自学习 | MCP/Profile 能提交、审核、订阅、评分、下架；线上反馈能形成评测样本或策略建议，但不会无人值守改生产配置 |
| `docs/design/apix-inspired-feature-evolution-roadmap.md` | 发布门禁和回归评测从 Agent 扩展到 Run Profile、RAG Strategy、Model Config、Tool/Skill、Ingestion Pipeline | Agent 级 `ProductionGateReport` 已归档完成；统一 `GateResult<T>` projection 已覆盖 Agent、Run Profile、RAG Strategy、Model Config、Tool、Skill 和 Ingestion Pipeline 的当前 API，并有 full-Docker smoke；剩余为 persisted unified gate rows 与未来退休对象专属 gate model | 中期：统一证据模型与发布门禁 | 所有高风险对象发布前都产出 GateResult，能追溯到 evaluation、trace、audit、cost 和配置快照 |
| `docs/design/apix-inspired-phased-implementation-design.md` | 运行实验报告增强：trial 导出、失败说明、成本/trace/分支对比报告、AgentScope Studio trace 外链 | Markdown 报告模板、trial/export、失败说明、成本/trace/分支对比、真实 UI 创建/导出 flow 已归档完成；剩余只作为 Agent Workbench 汇聚、报告历史/预览和更丰富模板的产品化方向 | 中期/远期：Agent Workbench 与报告产品化 | 同一会话下多个运行方案的 trial 可导出报告，报告包含输出差异、成本、评分、trace 和对应消息分支 |

## 近期路线（0-4 周）

近期只处理“已合入但未稳定”和“设计已明确但尚未实现”的工作。

| 优先级 | 工作项 | 范围 | 验收 |
|---|---|---|---|
| P1 | MCP stdio 安全治理第一阶段 | 已落地：命令 allowlist、近端 runner 环境隔离、MCP 工具 HIGH/需审批默认标记、blocked stdio stderr 诊断、诊断审批直达入口、MCP 诊断执行网关 fail-closed、OpenAPI enabled operation 动态注册到 Tool Gateway 并具备真实 HTTP invoke/audit、Sandbox runtime close lifecycle 透传与关闭审计、Sandbox execution history API/UI、Sandbox artifact scanner/prompt visibility gate、Docker/Podman Code Interpreter 容器 adapter 最小闭环、`sandbox_python`/`sandbox_file_convert`/`sandbox_browser` Tool Gateway 工具链路、URL allowlist/代理/DNS-CIDR/session-state/profile 治理、full-compose host-socket、真实执行 artifact collection、MCP HTTP 和 A2A Tool Gateway audit evidence；剩余：PDF 渲染/OCR、Office 渲染/编辑、LibreOffice/Tika、通用二进制格式转换、外部扫描引擎/更深 PDF-binary scanning、强隔离与 node-pool 调度/健康、更广 Tool Gateway 产品化 | 非 allowlist stdio 命令无法启动；高风险 MCP 工具默认进入审批/网关治理 |
| P1 | Sandbox egress 治理与浏览器 Profile | 已落地：浏览器 URL allowlist、proxy/auth/rotation、egress audit summary、DNS/CIDR pinning、可编辑 tenant egress/私网例外、持久受治理 Browser Profile、Linux capability/privilege drop，以及 Sandbox Operations 的真实 UI 流程；剩余：gVisor/Firecracker 等更强隔离与 node-pool 调度/健康 | Operator 能在真实 full-Docker 管理页管理 egress 与 Browser Profile；执行面仍由 runtime adapter 和 Tool Gateway 审计链路负责 |
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

## 2026-07-09 Update: Sandbox Browser Private-Network Exception Visibility

Sandbox Runtime health now carries the container runtime's configured `browser-private-network-allowed-hosts` as read-only posture data, and the admin Sandbox Operations panel shows its count plus a bounded preview beside the existing browser egress policy fields. This keeps the runtime adapter as the owner of private-network exception normalization while giving operators page-level evidence for local/private fixture exceptions such as Docker host aliases.

This is a visibility slice only. It does not add mutable private-network exception editing, caller-controlled bypasses, or a new enforcement owner; URL-mode browser enforcement still stays in the container runtime route guard and Tool Gateway audit path.

## 2026-07-09 Update: Sandbox Browser Runtime Network Policy UX

Sandbox Operations now lets operators toggle the existing `BROWSER_AUTOMATION` runtime profile `networkAllowed` policy from the Runtime governance panel. The UI keeps non-browser runtime types disabled for network because only browser automation sessions can request network, and saves still flow through `POST /api/sandbox/runtime/profile-policies` before being read back from `GET /api/sandbox/runtime/profiles?tenantId=default`.

This is an operator-control slice for an already-owned runtime profile policy. It does not add arbitrary egress editing, private-network exception editing, long-lived browser credentials, or a new enforcement owner; session creation and URL-mode enforcement remain in the kernel profile policy and container runtime guard path.

Fresh evidence: `npm run build` passed with existing Browserslist/chunk-size warnings; the packaged frontend image rebuilt through the local `192.168.1.9:7890` proxy and `seahorse-frontend` was recreated; `.\scripts\e2e-sandbox-tool-quota-page-smoke.ps1 -BaseUrl http://127.0.0.1 -Password admin123 -Marker seahorse-sandbox-runtime-network-policy-page-smoke` passed against the local full-Docker frontend/backend and reported `Browser runtime network policy: true`; a post-run live API query confirmed `BROWSER_AUTOMATION.networkAllowed=False`, showing the E2E restore path left the real environment back at its original closed posture.

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

Sandbox Operations now exposes bounded runtime profile policy writes through `POST /api/sandbox/runtime/profile-policies`. The endpoint is deliberately narrow: existing kernel-owned profiles only, `ACTIVE`/`DISABLED`, and `sessionTtlSeconds` from 60 to 7200. The initial UI wrote `networkAllowed=false`; the 2026-07-09 browser-specific UX update above exposes the existing `BROWSER_AUTOMATION` network flag while keeping non-browser profile network disabled.

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

## 2026-07-07 Update: Sandbox Browser Governed Session State Artifact Replay

`sandbox_browser` URL mode now accepts `sessionStateArtifactId` for one-run replay from a previously captured `browser-session-state.json` artifact. The tool reads the governed artifact internally through `SandboxRuntimeInboundPort`, validates it with the existing Playwright storage-state shape and allowed-host/origin checks, and forwards it only as transient browser runtime input.

The full session-state artifact remains `SECRET/BLOCKED`, non-downloadable, prompt-hidden, and excluded from tool observations. It is copied into governed object storage for internal replay durability, including local-object-store names with UUID prefixes, but cookie/localStorage values and the artifact id are omitted from observations, audit summaries, HAR downloads, result downloads, and collected replay-session artifacts.

This closes governed replay from a captured session-state artifact. It does not add a long-lived credential store, operator approval UX, arbitrary profile management, or cross-run browser profile persistence.

Fresh evidence: focused kernel tests passed 152/152 via `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelSandboxRuntimeServiceTests,SandboxBrowserToolPortAdapterTests,DefaultSandboxArtifactScannerPortTests,LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the bootstrap package rebuilt with reactor `BUILD SUCCESS`; the full-compose backend was hot-deployed and health returned `{"status":"UP"}`; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-artifact-replay-smoke` passed 30/30 against real Docker browser/runtime/fixture/database/object-storage flows; and `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20.

## 2026-07-06 Update: Sandbox Browser Tool Gateway Audit Summary

Tool Gateway request audit now emits a `sandbox_browser`-specific argument summary for browser governance evidence. The summary records value-free execution posture fields such as `mode`, `networkRequested`, allowed-host count/presence, cookie count, session-state replay/capture flags, session-state cookie/origin counts, HAR, and video flags.

The audit summary deliberately omits URL credential material, cookie values, and session-state/localStorage values; those remain governed by the existing request/runtime validation and transient input handling. This is a narrow audit-hardening slice only. It does not add durable credential storage, replaying previously captured SECRET/BLOCKED artifacts, operator approval UX, long-lived browser profiles, or broader cross-provider audit schema changes.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 14/14, including regression coverage that sandbox browser audit summaries include URL/egress/session governance metadata while excluding cookie and localStorage secret values.

The sandbox browser audit summary now also avoids echoing pre-validation `allowedHosts` and unsupported `action` values. It records only allowed-host count/presence and maps unknown actions to `unsupported`, so malformed host strings or action markers cannot enter request audit before the tool adapter performs its stricter URL/action validation.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 19/19.

The sandbox browser audit summary now also filters `argumentKeys` through the supported browser-tool argument set while recording total argument count. Unknown pre-validation parameter names are no longer echoed into audit metadata, so malicious key names cannot smuggle secret markers before adapter validation rejects or ignores them.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 19/19.

The full-Docker `sandbox_browser` smoke now verifies those Tool Gateway audit summaries through the real `/api/tool-invocations` API. It checks inline HTML, URL egress with cookie/session capture, request-scoped session replay, and captured session-state artifact replay records for value-free governance fields while asserting raw marker, URL, cookie, localStorage, auth marker, and session-state artifact id values are absent.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-audit-smoke -SkipBrowserImageBuild` passed 31/31 against the real full-Docker backend, including the new `Verify sandbox_browser Tool Gateway audit summaries` step; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. The same backend was `UP` on `127.0.0.1`; attempts through `localhost` were affected by a local IPv6 `::1:9090` listener unrelated to the Docker backend.

## 2026-07-06 Update: AgentScope A2A Signed Header Boundary Guard

AgentScope inbound A2A `tenant-signed` authentication now rejects malformed signed headers before signature comparison and nonce-cache mutation. Tenant, agent, timestamp, and nonce headers have bounded lengths and control-character rejection; body-hash and signature headers must be 64-character SHA-256 hex strings.

This is a narrow production hardening slice for the existing tenant-signed A2A path. It does not add durable distributed nonce storage, live Nacos/Studio/OTEL production infrastructure, or new A2A routing behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeA2aServerControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 15/15, including malformed body-hash and over-boundary nonce rejection while preserving normal shared-secret and tenant-signed flows.

## 2026-07-06 Update: Governed Tool Approval Preview Key Guard

Tool approval previews now filter `argumentKeys` and `resourceRefKeys` before persisting `ApprovalRequest.argumentsPreviewJson`. The preview only exposes short safe key names using alphanumeric, `_`, `-`, and `.` characters, while retaining argument/resource-ref counts and canonical hashes so approval matching and audit correlation remain stable without echoing malicious pre-validation key names or raw resource reference values.

Agent run snapshots now apply the same resource-ref minimization at the checkpoint boundary. Internal waiting-approval checkpoints still retain resumable `resourceRefs` for execution recovery, but `AgentRunSnapshot.latestCheckpoint.pendingToolCallJson` replaces raw `resourceRefs` with `resourceRefKeys`, `resourceRefCount`, and `resourceRefHash`, and fail-closes malformed pending-tool payloads instead of echoing unknown raw JSON.

This is a narrow approval-record and snapshot hardening slice. It does not change tool adapter validation, policy decisions, approval status semantics, argument/resource-ref hashing, ACL inputs, internal checkpoint recovery, or runtime invocation behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,LocalGovernedToolExecutionPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 26/26, including regression coverage that unsafe argument/resource-ref key names and raw resource reference values are excluded from approval previews on both governed preflight and direct Tool Gateway approval paths.

Additional snapshot evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentRunSnapshotServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 4/4, including regression coverage that waiting-approval snapshot checkpoints do not expose raw resource reference values while retaining resource-ref key/count/hash metadata.

The same safe-key boundary now also applies inside `LocalToolGatewayPort` approval previews and cross-provider Tool Gateway audit summaries. OpenAPI, remote A2A, `sandbox_python`, and `sandbox_file_convert` summaries retain value-free counts and posture metadata, but filter raw key-name previews through the shared short safe-key rule and suppress key names containing secret/token/password markers.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,LocalGovernedToolExecutionPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 23/23, including regression coverage for unsafe approval-preview keys and cross-provider audit key filtering.

Approval `MODIFIED` decisions now validate `argumentsPreviewJson` before persisting the replacement payload that resume can use as tool arguments. The payload must be a bounded JSON object, may only contain the established preview fields plus optional `arguments`, and requires `arguments` to be an object when present.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelApprovalManagementServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 10/10, including malformed JSON, unsupported field, and non-object `arguments` rejection.

Agent run resume now also fails closed for legacy or externally written `MODIFIED` approvals that do not carry an `arguments` object. Approved approvals still resume from the checkpoint arguments, while modified approvals must provide explicit replacement arguments and no longer silently fall back to the original pending tool call.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentRunResumeServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 4/4, covering normal approved resume, modified replacement arguments, rejected approval, and malformed modified approval rejection without tool invocation.

Audit redaction now also scans string values under otherwise safe keys for obvious credential shapes such as `Bearer ...`, `access_token=...`, `api_key=...`, `client_secret=...`, `password=...`, and `session_id=...`. Matching values are replaced wholesale with `[REDACTED]`, closing the case where upstream errors or URLs carried credential material in generic fields such as `message` or array entries.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=AuditRedactionPolicyTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 3/3, including nested field-name redaction, invalid JSON fail-closed behavior, and credential-shaped string-value redaction.

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

Sandbox artifact governance now covers Office Open XML packages as bounded ZIP-family artifacts. DOCX/XLSX/PPTX are clean download-only media when their bounded package scan passes: they are copied to governed object storage, downloadable through artifact APIs, but kept out of prompts and tool observations. DOCM/XLSM/PPTM are blocked by media type with `OFFICE_MACRO`; DOCX/XLSX/PPTX packages containing `vbaProject.bin`, ActiveX controls, embedded objects, external links, or OLE object entries are blocked with the same value-free category before object storage copy.

Fresh evidence: focused kernel/container tests passed 85/85, the full-compose backend rebuilt with an in-image Maven `BUILD SUCCESS`, and `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-office-ooxml-smoke` passed 33/33. Later scanner evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=DefaultSandboxArtifactScannerPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 37/37 after adding regression coverage that ActiveX and embedded OLE/object OOXML entries fail closed without persisting raw entry names in redaction summaries. This is a conservative package-governance slice only; Office rendering/editing, macro parsing/execution, LibreOffice/Tika-backed conversion, recursive extraction, external virus scanning, and richer scanner policy UX remain follow-up work.

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

## 2026-07-07 Update: Sandbox Nested Archive Fail-Closed Scan

`DefaultSandboxArtifactScannerPort` now fails closed when a scanned ZIP/TAR/TAR.GZ archive contains a nested archive entry. The scanner blocks nested archive filenames (`.zip`, `.tar`, `.tar.gz`, `.tgz`, `.gz`) and nested archive content signatures (ZIP, GZIP, TAR `ustar`) inside regular archive entries with value-free category `ARCHIVE_NESTED_ARCHIVE`.

Blocked nested archives are recorded as `BLOCKED|CONFIDENTIAL`, summary `nested archive content`, `contentScanned=true`, and are not copied to governed object storage or made downloadable. The redaction summary does not persist raw inner filenames such as `inner.zip` or test content markers. This is deliberately fail-closed and non-recursive: it does not add recursive extraction, generic container unpacking, external scanner engines, ClamAV, full PDF rendering/OCR, Office rendering/editing, LibreOffice/Tika conversion, or general binary conversion.

The artifact-storage full-Docker smoke was also updated to follow the current governed tool path: it creates a real persisted agent run, handles `sandbox_python` approval, binds tool invocations to that run, and reads session ids from persisted artifact/session state instead of relying on prompt observation leakage.

Fresh evidence: PowerShell parsing returned `ps1 parse ok`; focused scanner tests passed 41/41 via `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=DefaultSandboxArtifactScannerPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the bootstrap package rebuilt with reactor `BUILD SUCCESS`; `seahorse-backend` was hot-deployed and `/actuator/health` returned `{"status":"UP"}`; `.\scripts\e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-nested-archive-smoke` passed 49/49 against the local full-Docker backend, including "Verify nested ZIP archive is blocked before object storage"; and `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20.

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

The adapter-side, container-runtime input, and generated Playwright route/HAR query guards now also canonicalize common credential parameter variants by removing separators before matching, so forms such as `sessionToken=...`, `client-secret=...`, `auth-token=...`, and `oauth_token=...` fail closed before sandbox session creation, Docker command construction, or page-initiated route continuation without echoing the credential values.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 30/30; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 56/56; `git diff --check` passed.

## 2026-07-06 Update: Sandbox Browser URL Route Same-Origin Guard

The sandbox browser URL runtime route guard now allows network requests only to the initial target URL origin, including scheme, host, and effective port. `allowedHosts` remains the policy/profile authorization input, but the generated Playwright route no longer treats that host allowlist as permission to probe every port on the same host after navigation, redirect, or subresource loading.

The runtime route guard also rejects same-origin browser requests that carry URL credential material in userinfo, fragments, or credential-shaped query parameter names, including URL-decoded, semicolon-delimited, and bracketed query forms. This keeps page-initiated token URLs from being continued after the initial target URL has passed kernel/runtime input validation.

Blocked credential-bearing route URLs are also redacted before entering the generated HAR event model, preserving only the safe origin/path shape plus value-free redaction markers for userinfo, query, and fragment parts.

HAR event URL recording now also shortens allowed internal `data:` and `blob:` pseudo-URLs to value-free markers, keeping inline data and blob identifiers out of governed network artifacts while preserving the route allow/abort decision.

The browser result and captured session summary now apply the same value-free URL redaction to the final `page.url`, so page-side History API changes cannot move credential query strings, fragments, `data:`, or `blob:` payloads into governed JSON artifacts after navigation.

This is a narrow runtime egress hardening slice. It does not add a general outbound proxy, per-path URL policy UX, DNS pinning, CIDR/private-network classification, or long-lived browser profile management.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 40/40.

## 2026-07-07 Update: Sandbox Browser Allowlisted Host Subresource Egress

The sandbox browser URL runtime route guard now permits HTTP/HTTPS subresource requests to every host explicitly present in the request `allowedHosts`, rather than only the initial target origin. The kernel/global sandbox policy still gates the requested hosts before session creation, credential-bearing URLs remain rejected/redacted, inline HTML still runs with `--network none`, and the container adapter only adds Docker `host-gateway` aliases for requested `*.docker.internal` hosts needed by local full-Docker E2E fixtures.

This is a narrow browser-runtime egress correction for multi-host pages such as a target page plus allowlisted asset host. It does not add arbitrary private-network egress, non-browser networking for Python/file conversion, DNS pinning, CIDR classification, proxy-based egress mediation, stored browser profiles, or operator-managed URL policy UX.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 71/71. The real full-Docker browser smoke then ran against `http://127.0.0.1:9090` with `SEAHORSE_AGENT_SANDBOX_NETWORK_POLICY=ALLOWLISTED` and `SEAHORSE_AGENT_SANDBOX_ALLOWLISTED_HOSTS=host.docker.internal,assets.docker.internal`: `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-allowhost-smoke -SkipBrowserImageBuild` passed 26/26. That E2E created a real agent run, exercised the high-risk tool approval flow, started real `host.docker.internal` and `assets.docker.internal` HTTP fixture containers, verified URL-mode HAR entries for the main page and allowlisted asset host as unblocked 200 responses, verified a non-allowlisted `example.invalid` request as blocked, checked governed JSON/HAR/session-state/video artifacts through API and PostgreSQL, verified backend object storage files, restored browser runtime profile networking to deny, and confirmed no managed sandbox containers or non-terminal sandbox sessions remained. Backend regression smoke also passed 20/20 via `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose`.

## 2026-07-06 Update: Sandbox Browser Target-Host Session Replay Guard

The sandbox browser URL path now requires request-scoped cookies and replayed session-state cookies/origins to match the target URL host, not merely any host listed in `allowedHosts`. This keeps extra policy-authorized hosts from carrying unrelated cookie or localStorage secrets into the transient browser runtime input after the runtime route guard was narrowed to the initial target origin.

This is a narrow auth/session hygiene guard. It does not add stored browser sessions, credential vault integration, replay from previously captured SECRET/BLOCKED artifacts, proxy-rich egress audit, or mutable operator URL policy UX.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 18/18; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 42/42.

## 2026-07-06 Update: Sandbox Browser SessionState Origin Replay Guard

The sandbox browser URL path now requires replayed `sessionState.origins[].origin` entries to match the target URL origin, including scheme, host, and effective port. Cookie replay remains host-scoped, but localStorage replay is origin-scoped so a request to `example.test:8080` cannot carry localStorage values for `example.test:9090` into the transient browser context.

This is a narrow request-scoped session replay hardening slice. It does not add stored browser profiles, credential vault integration, replay from previously captured SECRET/BLOCKED artifacts, proxy-rich egress audit, or operator-managed URL policy UX.

The replay origin guard now also requires `sessionState.origins[].origin` to be a pure origin with no userinfo, path, query, or fragment. Malformed or credential-bearing origin strings fail closed before session creation and before container execution without echoing the embedded secret material.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 27/27; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 51/51.

## 2026-07-06 Update: Sandbox Browser Host-Only Session Cookies

The sandbox browser request-scoped `sessionState.cookies` replay path now rejects leading-dot cookie domains before kernel session creation and before container execution. This aligns replayed Playwright storage-state cookies with the existing host-only `cookies` argument, so `.example.test` is not normalized into `example.test` and cannot widen credential scope beyond the target host.

This is a narrow consistency hardening slice for request-scoped session replay. It does not add stored browser profiles, credential vault integration, replay from previously captured SECRET/BLOCKED artifacts, proxy-rich egress audit, or operator-managed URL policy UX.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 28/28; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 54/54; `git diff --check` passed with only CRLF warnings.

## 2026-07-06 Update: Sandbox Browser SessionState Replay Schema Guard

The sandbox browser request-scoped `sessionState` replay path now rejects unsupported fields at the top level, cookie level, origin level, and localStorage item level before kernel session creation and before container execution. Replay input is limited to the Playwright storage-state fields the runtime actually needs, so callers cannot smuggle extra storage references or ungoverned metadata into the transient `browser-session-state-input.json`.

This is a narrow request-scoped replay schema hardening slice. It does not add stored browser profiles, credential vault integration, replay from previously captured SECRET/BLOCKED artifacts, proxy-rich egress audit, or operator-managed URL policy UX.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 29/29; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 55/55.

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

The Tool Gateway audit summary now has a dedicated value-free `invoke_remote_a2a_agent` projection. It records target-agent presence/length, prompt length, metadata keys/count, and requested-version presence/length, without storing the raw prompt, target agent name, requested version, or metadata values in `argumentsSummary`.

This is a narrow A2A/Tool Gateway governance slice. It does not add cross-provider rate limits, remote content redaction, remote agent trust scoring, or full-Docker multi-agent smoke coverage.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeA2AToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 4/4; `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 21/21, including regression coverage that secret-like target agent names and metadata versions are reduced to presence/length metadata.

## 2026-07-06 Update: Sandbox File Conversion Active-Content Preflight

`sandbox_file_convert` now preflights base64 DOCX/PDF inputs in the container adapter before writing converter scripts, writing decoded input files, or starting Docker/Podman. DOCX inputs must be inspectable bounded ZIP packages with `word/document.xml`; unsafe paths, too many entries, macro projects, ActiveX, embedded OLE/object payloads, and external links fail closed. PDF inputs must have a PDF header and fail closed on encrypted or active-content markers such as JavaScript/OpenAction/AA plus embedded-content, form-import, external-GoTo, and rendition markers in the bounded prefix.

This keeps the existing conservative text-extraction path aligned with its no-render/no-edit scope. It does not add LibreOffice/Tika, PDF rendering/OCR, Office rendering/editing, password handling, or general binary conversion.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 56/56, including regressions that DOCX/PDF active content is rejected before container command execution and before `main.py` or decoded input files are written, with PDF `/OpenAction` and `/ImportData` covered.

## 2026-07-06 Update: Sandbox File Conversion Tool Gateway Audit Summary

Tool Gateway request audit now emits a `sandbox_file_convert`-specific value-free argument summary. The summary records `FILE_CONVERSION` runtime posture, source/target format presence and length, supported-format classification, content-encoding presence and length, supported-encoding classification, input content length, binary-input classification, network posture, and argument keys while excluding raw file content, base64 values, and pre-validation format/encoding values from `argumentsSummary`.

This is a narrow cross-tool audit hardening slice. It does not change file conversion execution semantics, artifact scanning, approval policy, quota policy, or add broader binary conversion formats.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 23/23, including regression coverage that `sandbox_file_convert` audit summaries include governance metadata while excluding raw base64/content markers and caller-controlled pre-validation format/encoding values.

## 2026-07-07 Update: Sandbox File Convert Full-Docker Audit E2E

The full-Docker `sandbox_file_convert` smoke now verifies Tool Gateway audit summaries through the real `/api/tool-invocations` API for plain CSV/JSON and Markdown paths plus base64 DOCX/PDF/XLSX/PPTX paths. The E2E guard asserts value-free `FILE_CONVERSION` governance fields, binary-input classification, network posture, and argument shape metadata while checking that raw marker text, document text, base64 payloads, and storage references do not appear in `argumentsSummary`.

The first real E2E run exposed an audit-projection drift: extended supported formats such as `xlsx` and `pptx` were executed successfully but summarized as `unsupported` because the Tool Gateway audit allowlist lagged behind the sandbox file-conversion tool schema/runtime. The audit allowlist now includes the supported ODF and Office spreadsheet/presentation formats (`odt`, `ods`, `odp`, `xlsx`, `pptx`) without loosening raw-value redaction.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 33/33, including regression coverage for extended file-conversion audit formats; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the full bootstrap reactor with 28/28 modules passing; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-audit-smoke` passed 73/73 against the real full-Docker backend, including the new `Verify sandbox_file_convert Tool Gateway audit summaries` step.

## 2026-07-06 Update: Sandbox Python Tool Gateway Audit Summary

Tool Gateway request audit now emits a `sandbox_python`-specific value-free argument summary. The summary records `CODE_INTERPRETER` runtime posture, code length, network request posture, requested-host presence, requested-host count, and argument keys while excluding raw Python code and pre-validation requested host values from `argumentsSummary`.

This is a narrow Tool Gateway audit hardening slice for the existing Code Interpreter path. It does not change sandbox execution, network enforcement, artifact scanning, approval policy, or quota policy.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 23/23 after adding regression coverage that `sandbox_python` audit summaries include governance metadata while excluding raw code markers and caller-controlled pre-validation host values.

## 2026-07-07 Update: Sandbox Python Full-Docker Audit E2E

The full-Docker `sandbox_python` smoke now exercises the same governed path as the other sandbox-backed tools: it creates a real persisted Agent run, handles the high-risk tool approval round trip, retries the Tool Gateway invocation with the same identity, executes the real Code Interpreter sandbox, and then verifies the real `/api/tool-invocations` audit record. The audit E2E asserts the value-free `CODE_INTERPRETER` posture fields, code length, network/requested-host posture, safe argument key, and argument value shape while checking that raw code, file names, print/write calls, artifact text, and the marker do not appear in `argumentsSummary`.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-python-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-python-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-python-audit-smoke` passed 5/5 against the real full-Docker backend, including real Agent run creation, approval, sandbox execution, and the new `Verify sandbox_python Tool Gateway audit summary` step.

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

## 2026-07-06 Update: OpenAPI Credential Binding Audit Minimization

OpenAPI connector credential-binding audit events now record `credentialRefPresent=true` instead of embedding the credential reference value in the audit payload. Runtime credential binding storage still preserves the reference for invocation-time resolution, but the governance audit source payload no longer carries the secret reference itself.

This is a narrow cross-provider audit minimization slice. It does not change credential binding rotation, credential provider resolution, connector operation enablement, Tool Gateway invocation, or redaction policy semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelOpenApiConnectorImportServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed after adding regression coverage that connector credential-binding audit events include the presence marker while excluding the raw `secret-ref` value.

## 2026-07-06 Update: Sandbox PDF Active Content Marker Guard

The bounded sandbox artifact scanner now treats additional PDF action and embedded-content markers as active content: `/Launch`, `/EmbeddedFile`, `/RichMedia`, `/SubmitForm`, `/ImportData`, `/GoToE`, `/GoToR`, and `/Rendition` are blocked alongside the existing JavaScript/OpenAction/AA markers. The same value-free `PDF_ACTIVE_CONTENT` and `ARCHIVE_PDF_ACTIVE_CONTENT` categories are reused for direct PDF artifacts and PDF entries inside scanned archives.

This is a narrow scanner hardening slice. It does not add PDF rendering/OCR, full PDF parsing, attachment extraction, external scanner engines, recursive archive scanning, or new artifact policy semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=DefaultSandboxArtifactScannerPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 35/35 after adding regression coverage that direct `/Launch` and `/ImportData` PDFs plus ZIP-embedded `/EmbeddedFile` and `/GoToE` PDFs fail closed without persisting raw marker values in redaction summaries.

## 2026-07-06 Update: Checkpoint Query Resource Reference Minimization

Agent checkpoint query results now share the same pending-tool-call view sanitizer used by run snapshots. External checkpoint reads replace raw `resourceRefs` with safe `resourceRefKeys`, `resourceRefCount`, and `resourceRefHash`, and malformed/non-object pending-tool payloads fail closed instead of echoing raw JSON. Internal checkpoint repository storage and run resume continue to use the original resumable payload.

This is a narrow checkpoint boundary hardening slice. It does not change approval wait persistence, resume semantics, checkpoint storage schema, or tool execution behavior.

Fresh evidence: the regression first failed because `KernelAgentCheckpointQueryService.listByRunId` returned raw `resourceRefs`; after the fix, `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentCheckpointQueryServiceTests,KernelAgentRunSnapshotServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 6/6, covering checkpoint query redaction, malformed payload fail-closed behavior, and existing snapshot redaction behavior.

## 2026-07-06 Update: Checkpoint Query Run Ownership Guard

Agent checkpoint query results now require the requested run to be readable by the current user before loading checkpoint rows. Owners can read their own checkpoint history, admins can read across users, unrelated users receive `权限不足`, and missing runs fail closed with `Agent run not found`.

This closes a checkpoint API authorization gap adjacent to the resource-reference minimization work. It does not change checkpoint persistence, resume behavior, worker access to internal checkpoints, or the sanitized checkpoint response shape.

Fresh evidence: the new regression first failed because `KernelAgentCheckpointQueryService` had no run repository dependency or owner/admin gate. After the fix, `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentCheckpointQueryServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 5/5, and `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-DskipTests" compile` completed with reactor `BUILD SUCCESS` after the Spring auto-configuration was updated to inject `AgentRunRepositoryPort`.

## 2026-07-06 Update: Agent Run Operation Ownership Guard

Agent run detail, step listing, cancellation, and retry operations now require the target run to be readable by the current user. Owners can inspect and operate on their own runs, admins can inspect across users, and unrelated users receive `权限不足` before run steps are listed or run state is mutated. Worker-owned terminal transitions such as `succeed` and `fail` remain internal and unchanged.

This closes the same run-boundary authorization class as the checkpoint query guard. It does not change run creation, worker execution, run queue handling, snapshots, workflow projection, or cost-summary behavior.

Fresh evidence: the new regression first failed because `KernelAgentRunService` only required login for run detail, step listing, cancel, and retry. After the fix, `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentRunServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 19/19, including unrelated-user denial for run detail, steps, cancel, and retry plus admin read access.

## 2026-07-06 Update: Agent Run Event Replay Ownership Guard

Agent run event replay now requires the target run to be readable before returning buffered stream events from `GET /agent-runs/{runId}/events` and `GET /api/agent-runs/{runId}/events`. The web endpoint reuses `AgentRunInboundPort.findRunById(runId)`, so the owner/admin authorization boundary stays in the kernel run service instead of being reimplemented at the event buffer.

This closes a run-boundary authorization gap for replayed stream events. It does not change chat SSE resume internals, event buffer persistence, event payload shape, snapshot/workflow/cost-summary authorization, or long-lived event retention semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseAgentControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 18/18 after adding regression coverage that unreadable runs return `权限不足` and do not call `AgentRunEventBufferPort.getAfter`.

## 2026-07-06 Update: Run Context Snapshot Ownership Guard

Run context snapshot queries now apply the agent-run owner/admin boundary when the requested `runId` exists in the AgentRun repository. The production auto-configuration injects `AgentRunRepositoryPort` and `CurrentUserPort` into `KernelRunContextSnapshotService`, so `GET /api/agent-runs/{runId}/context-snapshot` no longer returns Agent run prompt/tool/model/trace snapshot material to unrelated users. Legacy chat task snapshots that do not have an AgentRun record keep the existing lookup behavior.

This closes an Agent run context snapshot authorization gap. It does not change snapshot persistence, snapshot JSON shape, chat task snapshot compatibility, run experiment internal snapshot reads, or retention semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunContextSnapshotServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 4/4, covering owner access, unrelated-user denial, admin access, and legacy task snapshot compatibility. `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-DskipTests" compile` completed with reactor `BUILD SUCCESS` after the production bean was updated to inject the run repository and current-user port.

## 2026-07-06 Update: Agent Handoff Parent Run Ownership Guard

Agent handoff creation, detail lookup, parent-run listing, and cancellation now require the parent run to be readable before exposing or mutating handoff records. The kernel handoff service reuses `AgentRunInboundPort.findRunById`, so the owner/admin boundary remains centralized in the Agent run service and unreadable parent runs fail before child run cancellation or child A2A run creation is attempted.

This closes a handoff run-boundary authorization gap. It does not change mesh policy decisions, child run metadata shape, handoff audit payload shape, repository schema, or local Agent-as-Tool response contracts.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentHandoffServiceTests,LocalAgentAsToolPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 9/9, covering parent-run authorization for create/list/detail/cancel and preserving the local handoff tool success path when the parent run is readable.

## 2026-07-06 Update: Workflow Visualization Run Ownership Guard

The legacy workflow visualization service now applies the Agent run owner/admin boundary before loading persisted workflow steps. Production RAG/workflow auto-configuration injects `AgentRunRepositoryPort` and `CurrentUserPort` into `KernelWorkflowVisualizationService`, and the workflow SSE endpoint reuses the same visualization port as a gate before subscribing to run-specific step updates.

This closes an older workflow visualization authorization gap for `GET /api/workflows/runs/{runId}/visualization` and `/stream`. It does not change the newer Agent run workflow projection shape, workflow step storage, event payload shape, or workflow publisher semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=KernelWorkflowVisualizationServiceTests,SeahorseWorkflowVisualizationControllerTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` completed with reactor `BUILD SUCCESS`, covering owner access, admin access, unrelated-user denial before workflow steps load, legacy constructor compatibility, and SSE stream gating.

## 2026-07-06 Update: Pending Approval Run Ownership Guard

Pending approval lookup by Agent run now requires the requested run to be readable before querying approval records. Production auto-configuration injects `AgentRunRepositoryPort` into `KernelApprovalManagementService`, so `/api/agent-runs/{runId}/pending-approvals` no longer lets an unrelated user probe another user's run approval state before the existing approval-owner filter is applied.

This closes a narrow pending-approval run-boundary gap. It does not change admin approval paging, approval detail admin semantics, approve/reject/modify ownership checks, approval decision persistence, or modified-argument validation.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelApprovalManagementServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 12/12, covering readable-run pending approval lookup and denial before approval repository query for unreadable runs. `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-DskipTests" compile` completed with reactor `BUILD SUCCESS` after the production bean was updated to inject the run repository.

## 2026-07-06 Update: Sandbox Session Run Ownership Guard

Sandbox session execution lookup, artifact listing, artifact detail, artifact download, execute, and close paths now apply the Agent run owner/admin boundary when `AgentRunRepositoryPort` and `CurrentUserPort` are available. Production auto-configuration injects both ports into `KernelSandboxRuntimeService`, so `/api/sandbox/sessions/{sessionId}/executions`, `/artifacts`, and `/api/sandbox/artifacts/{artifactId}` no longer expose sandbox resources for sessions attached to another user's Agent run. Tenant session listing filters unreadable sessions instead of returning cross-user session metadata.

This closes a sandbox run-boundary authorization gap. It does not change sandbox policy admission, runtime profile governance, artifact scanner behavior, object-storage copy/download semantics, orphan cleanup, runtime health inspection, or legacy constructor compatibility for tests and embedded use.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelSandboxRuntimeServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 43/43, covering unrelated-user denial before execution repository reads, unreadable session filtering, and artifact download denial. `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-DskipTests" compile` completed with reactor `BUILD SUCCESS` after production wiring was updated to inject `AgentRunRepositoryPort` and `CurrentUserPort`.

## 2026-07-06 Update: Task Facade Ownership Guard

Task detail lookup, cancellation, event history replay, event subscription, and artifact listing now require the target task to be owned by the current user or readable by an admin. Production auto-configuration injects `CurrentUserPort` into `TaskOrchestrationService`, so Task Facade by-id APIs no longer expose or mutate another user's task state before reaching chat cancellation, event bus, or artifact query ports. The owner check accepts both numeric web user IDs and operator-style user IDs to preserve existing web and embedded integrations.

This closes a Task Facade authorization gap. It does not change task creation, user task listing, internal completion callbacks, conversation lookup, Agent run polling, task event payload shape, or legacy constructor compatibility for tests and embedded use.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=TaskOrchestrationServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 9/9, covering owner read, admin read, unrelated-user denial, and denial before downstream cancel/event/artifact access. `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-DskipTests" compile` completed with reactor `BUILD SUCCESS` after production wiring was updated to inject `CurrentUserPort`.

## 2026-07-06 Update: Agent Artifact Numeric Owner Compatibility

Agent artifact query and update ownership checks now accept both the current user's numeric primary-key string and operator username when comparing against persisted artifact/run `userId` values. This keeps the existing owner/admin boundary intact while matching the web adapter's `CurrentUser(userId=id, username=name)` shape and older operator-style artifact records.

This is a narrow authorization-compatibility slice. It does not change artifact persistence, download eligibility, scan status transitions, object storage behavior, admin access, or unrelated-user denial.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentArtifactQueryServiceTests,KernelAgentArtifactUpdateServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 8/8, covering numeric web user ID owners for artifact list/read/update while preserving unrelated-user denial and existing download guards.

## 2026-07-06 Update: Agent Run Numeric Owner Compatibility

Agent run by-id operations now accept both the current user's numeric primary-key string and operator username when comparing against persisted run `userId` values. This keeps the existing owner/admin boundary intact while allowing web-created or migrated runs whose owner was stored as a numeric user ID to be read, stepped, cancelled, and retried by the real owner.

This is a narrow authorization-compatibility slice for by-id run operations. It does not change run creation attribution, admin access, unrelated-user denial, worker terminal transitions, run paging query semantics, snapshot persistence, or repository schema.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentRunServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 22/22, covering numeric web user ID owners for run detail, step listing, and cancellation while preserving unrelated-user denial, admin read access, and current-user page scoping.

## 2026-07-06 Update: Agent Run Adjacent Numeric Owner Compatibility

Agent checkpoint history and run cost summary lookups now accept both the current user's numeric primary-key string and operator username when comparing against the owning Agent run `userId`. This extends the same owner/admin boundary compatibility from run detail and artifacts to two sensitive run-adjacent read surfaces.

This is a narrow authorization-compatibility slice. It does not change checkpoint persistence, checkpoint sanitization, cost aggregation dimensions, admin access, unrelated-user denial, run paging semantics, or repository schema.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentCheckpointQueryServiceTests,KernelAgentRunCostSummaryServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 10/10, covering numeric web user ID owners for checkpoint history and run cost summary while preserving unrelated-user denial and admin read access.

## 2026-07-06 Update: Agent Run Snapshot and Workflow Numeric Owner Compatibility

Agent run snapshot and run workflow lookups now accept both the current user's numeric primary-key string and operator username when comparing against the owning Agent run `userId`. Snapshot nested visibility for context-pack sources, pending approvals, and artifacts uses the same owner compatibility, so numeric-owner runs do not open with empty adjacent details.

This is a narrow authorization-compatibility slice. It does not change snapshot assembly, checkpoint sanitization, workflow graph layout, admin access, unrelated-user denial, artifact scan filtering, approval state transitions, or repository schema.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentRunSnapshotServiceTests,KernelAgentRunWorkflowServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 7/7, covering numeric web user ID owners for snapshot detail and workflow graph while preserving unrelated-user denial.

## 2026-07-06 Update: Run Context and Legacy Workflow Numeric Owner Compatibility

Run context snapshot lookup and the legacy workflow visualization service now accept both the current user's numeric primary-key string and operator username when comparing against the owning Agent run `userId`. This keeps the same owner/admin boundary while allowing web-created numeric-owner runs to expose their prompt/tool/model context snapshot and legacy workflow steps to the real owner.

This is a narrow authorization-compatibility slice. It does not change legacy task snapshot fallback, workflow step ordering, SSE gating, admin access, unrelated-user denial, snapshot persistence, or repository schema.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunContextSnapshotServiceTests,KernelWorkflowVisualizationServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 10/10, covering numeric web user ID owners for run context snapshots and legacy workflow visualization while preserving unrelated-user denial and legacy ungated constructor behavior.

## 2026-07-06 Update: Approval and Sandbox Numeric Owner Compatibility

Pending approval lookup/decision ownership and sandbox session read guards now accept both the current user's numeric primary-key string and operator username when comparing against Agent run or approval `userId` values. This keeps the existing owner/admin boundary while allowing web-created numeric-owner runs and older operator-style approval records to remain operable by the real owner.

This is a narrow authorization-compatibility slice. It does not change approval state transitions, approval paging admin semantics, sandbox execution policy, artifact download eligibility, scan status handling, tenant session ordering, or repository schema.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelApprovalManagementServiceTests,KernelSandboxRuntimeServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 58/58, covering operator-style approval ownership, numeric web user ID sandbox session ownership, unrelated-user denial, and the existing sandbox runtime behaviors.

## 2026-07-06 Update: Sandbox Browser URL Query Length Guard

The `sandbox_browser` tool adapter and container browser automation runtime now reject URL-mode requests whose raw query string exceeds 512 characters before creating a sandbox session or starting a container command. This keeps the existing 2048-character total URL cap while adding a tighter query-specific bound for prompt-visible tool input, runtime script generation, audit summaries, and HAR-adjacent URL handling.

This is a narrow URL hygiene slice. It does not change host allowlisting, egress policy, cookie/session-state replay, credential-parameter detection, inline HTML mode, screenshot/HAR/video capture, or browser action semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 31/31, and `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 57/57, covering tool-layer rejection before sandbox session creation and runtime-layer rejection before container command execution.

## 2026-07-06 Update: Sandbox Browser Observation URL Query Redaction

The `sandbox_browser` tool observation now redacts allowed URL query values before returning the browser metadata to the model-visible tool result. The sandbox runtime still receives the full validated URL, but `browser.url` in the observation keeps only scheme, host, optional port, path, and a value-free `<redacted-query>` marker when a query is present.

This is a narrow observation hardening slice. It does not change URL validation, runtime input, host allowlisting, egress policy, HAR capture, session-state replay/capture, or browser navigation behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=SandboxBrowserToolPortAdapterTests#shouldRedactAllowedUrlQueryFromObservation" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 1/1, and `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 32/32, covering full runtime URL preservation while excluding the allowed query value from the tool result observation.

## 2026-07-06 Update: Sandbox Browser Audit URL Shape Summary

Tool Gateway request audit now includes value-free URL shape evidence for `sandbox_browser`: URL presence, total URL length, query presence, and raw query length. The audit summary still excludes URL host/path/query values, cookies, localStorage values, pre-validation allowed host values, and unsupported action values.

This is a narrow audit-hardening slice. It does not change tool execution, URL validation, browser runtime input, approval policy, quota policy, or artifact governance.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 23/23, covering sandbox browser URL/query length metadata while excluding the allowed query marker and existing cookie/session-state secret values.

## 2026-07-06 Update: Sandbox Browser Audit Inline HTML Shape Summary

Tool Gateway request audit now includes value-free inline HTML shape evidence for `sandbox_browser`: HTML presence and character length. Inline browser audit summaries can distinguish no-network HTML mode from URL mode without persisting the raw HTML payload, script content, DOM text, cookies, localStorage values, or URL values.

This is a narrow audit-hardening slice. It does not change browser execution, inline HTML validation, URL mode, egress policy, approval policy, quota policy, or artifact governance.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 24/24, covering sandbox browser inline HTML length metadata while excluding the inline HTML marker value and preserving the existing URL/session audit redaction coverage.

## 2026-07-06 Update: Sandbox Browser Audit Capture Shape Summary

Tool Gateway request audit now includes value-free capture shape evidence for `sandbox_browser`: screenshot flag, HAR/video flags, viewport-width presence/value, and viewport-height presence/value. Invalid or absent viewport inputs are recorded as `0`, so the audit keeps bounded numeric posture evidence without persisting browser content, URL values, cookies, localStorage values, or raw HTML.

This is a narrow audit-hardening slice. It does not change browser execution, viewport validation, capture artifact collection, URL mode, egress policy, approval policy, quota policy, or artifact governance.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 24/24, covering screenshot and viewport metadata for URL mode plus default inline screenshot/absent viewport metadata while preserving existing URL, HTML, and session redaction coverage.

## 2026-07-06 Update: Sandbox Browser Audit Session Storage Shape Summary

Tool Gateway request audit now includes value-free request-scoped browser session replay storage evidence for `sandbox_browser`: replayed cookie count, origin count, and localStorage item count. The summary still excludes cookie values, localStorage keys and values, origin strings, URL values, and raw HTML.

This is a narrow auth/session audit-hardening slice. It does not change session replay validation, browser execution, capture artifacts, credential storage, approval policy, quota policy, or artifact governance.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 24/24, covering localStorage item count metadata while preserving the existing URL, HTML, cookie, and session value redaction assertions.

## 2026-07-06 Update: Remote A2A Audit Metadata Value Shape Summary

Tool Gateway request audit now includes value-free metadata value shape evidence for `invoke_remote_a2a_agent`: metadata value count, total value length, and maximum value length. The summary still excludes the raw prompt, target agent name, requested version value, metadata values, and unsafe argument keys.

This is a narrow cross-provider audit-hardening slice. It does not change A2A request validation, connector invocation, signing, approval policy, quota policy, or remote-agent execution behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 24/24, covering A2A metadata value shape metadata while preserving existing prompt, agent-name, version, and metadata value redaction assertions.

## 2026-07-06 Update: OpenAPI Audit Value Shape Summary

Tool Gateway request audit now includes value-free OpenAPI value shape evidence for dynamic `openapi_` tools: path/query/parameter/header value count, total value length, and maximum value length. The summary still excludes raw path/query/header/parameter/body values and unsafe argument keys.

This is a narrow cross-provider audit-hardening slice. It does not change OpenAPI connector import, credential injection, HTTP invocation, response redaction, approval policy, quota policy, or request execution behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 24/24, covering OpenAPI value shape metadata while preserving existing path, query, parameter, header, body value, and unsafe-key redaction assertions.

## 2026-07-07 Update: OpenAPI Flat Argument Audit E2E

Dynamic `openapi_` Tool Gateway audit summaries now include value-free top-level argument value shape metadata (`argumentValueCount`, total value length, and maximum value length). This covers the real OpenAPI tool invocation shape where operation parameters are passed as flat tool arguments, while the existing path/query/parameter/header/body partitions continue to cover structured calls.

The full-Docker OpenAPI connector smoke now verifies the real `/api/tool-invocations` `argumentsSummary` for an imported connector operation invoked through Tool Gateway. It asserts the OpenAPI provider marker, safe argument key, top-level value shape, and request-body absence while checking that the raw query value, target server URL, host name, and sensitive response token/secret values do not appear in the audit summary.

Fresh evidence: `node --check .\scripts\e2e-openapi-connector-smoke.mjs` passed; `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 34/34, including regression coverage for flat OpenAPI arguments; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the full bootstrap reactor with 28/28 modules passing; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-openapi-connector-smoke.ps1 -BaseUrl http://127.0.0.1 -Password admin123` passed against the real full-Docker backend with `auditStatus=SUCCEEDED` for the imported `openapi_` tool and the new audit-summary assertions.

## 2026-07-06 Update: OpenAPI Request Body Audit Value Shape Summary

Tool Gateway request audit now includes value-free object request body value shape evidence for dynamic `openapi_` tools: request body value count, total value length, and maximum value length. String request bodies keep the existing raw-length-only posture, while object bodies gain bounded shape evidence without persisting field values.

This is a narrow cross-provider audit-hardening slice. It does not change OpenAPI connector import, credential injection, HTTP invocation, response redaction, approval policy, quota policy, body serialization, or request execution behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 24/24, covering OpenAPI object request body value shape metadata while preserving existing path, query, parameter, header, body value, and unsafe-key redaction assertions.

## 2026-07-06 Update: Generic Tool Audit Value Shape Summary

Tool Gateway request audit now emits a value-free structured summary for non-specialized tools. The generic summary records tool id, safe argument keys, argument count, value count, total value length, and maximum value length while excluding raw argument values.

This is a narrow audit-hardening slice. It does not change tool execution, specialized sandbox/OpenAPI/A2A summaries, approval policy, quota policy, output redaction, or request routing behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 24/24, covering generic tool argument value shape metadata while preserving the existing specialized tool audit redaction coverage.

## 2026-07-06 Update: Approval Preview Argument Value Shape Summary

Tool approval request previews now include value-free argument shape evidence: argument value count, total value length, and maximum value length. The preview still records safe argument keys, argument count, resource-ref keys/count, and a resource-ref hash without storing raw argument values or raw resource references.

This is a narrow approval-governance hardening slice. It does not change approval decisions, pending approval persistence, resource-ref hashing, tool execution, Tool Gateway request audits, output redaction, or approval paging behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 24/24, covering approval preview argument value shape metadata while preserving existing secret argument and resource-reference minimization assertions.

## 2026-07-06 Update: Tool Completion Result Shape Summary

Tool Gateway completion audit summaries now record structured value-free result shape metadata for successful tool observations: content presence, content length, and coarse JSON type. The summary is derived after output redaction and still excludes raw tool output values, JSON field names, redacted secret placeholders, and binary payload values.

This is a narrow completion-audit hardening slice. It does not change tool execution, output redaction, artifact publication, failure error recording, approval policy, quota policy, or request audit summaries.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 24/24, covering JSON-object and redacted-text result shape summaries while preserving existing output redaction assertions.

## 2026-07-06 Update: Tool Completion Error Audit Redaction

Tool Gateway completion audit now redacts obvious credential-shaped substrings from failed tool error messages before persisting `errorMessage`. The tool result returned to the caller keeps its original error text for behavior compatibility, while the audit record replaces patterns such as `api_key=...`, bearer tokens, and OpenAI-style `sk-...` keys with `[REDACTED]`.

This is a narrow completion-audit hardening slice. It does not change tool execution, failure propagation to the Agent loop, output redaction for successful observations, approval decisions, quota policy, or request audit summaries.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 25/25, covering failed-tool credential-shaped error redaction in completion audit while preserving caller-visible failure text.

## 2026-07-06 Update: Tool Failure Error Output Redaction

`ToolOutputRedactionPort.basicSecretPatterns()` now redacts obvious credential-shaped substrings from failed tool errors as well as successful tool content. When the basic redactor is installed, caller-visible failed tool errors and completion audit errors both replace patterns such as `api_key=...`, bearer tokens, and OpenAI-style `sk-...` keys with `[REDACTED]`.

This is a narrow output-redaction hardening slice. It does not change the `ToolInvocationResult` contract, noop redaction behavior, approval/policy failures, tool execution, artifact publication, or success-content base64 redaction behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 25/25, covering caller-visible and audit-visible failed tool error redaction; `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=ToolPortContractTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 4/4, preserving the base `ToolInvocationResult.failed` contract.

## 2026-07-06 Update: Default Tool Output Redaction Wiring

Spring Boot auto-configuration now installs `ToolOutputRedactionPort.basicSecretPatterns()` by default for the agent runtime when no custom `ToolOutputRedactionPort` bean exists. This makes the production Tool Gateway use the basic successful-content and failed-error redactor without requiring application-level test wiring, while preserving user override behavior via `@ConditionalOnMissingBean`.

This is a narrow production-wiring hardening slice. It does not change redaction pattern semantics, Tool Gateway execution flow, approval policy, audit summaries, artifact publication, or the ability to provide a custom/noop redaction bean.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-spring-boot-autoconfigure -am "-Dtest=SeahorseAgentChatRunStoreAutoConfigurationTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 23/23, covering default redaction injection into `ToolGatewayPort` and custom redaction override behavior.

## 2026-07-06 Update: Tool Output JSON Secret Field Redaction

`ToolOutputRedactionPort.basicSecretPatterns()` now recursively redacts successful JSON tool output fields whose names match credential-shaped concepts such as `apiKey`, `access_token`, `clientSecret`, `password`, and `session_id`. Existing `b64Json`/`b64_json` redaction and credential-shaped text/error redaction remain in place, while non-sensitive JSON fields are preserved.

This is a narrow output-redaction hardening slice. It does not change Tool Gateway execution, result summary shape, approval policy, artifact publication ordering, noop redaction behavior, or the configured default redaction wiring.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 26/26, covering nested successful JSON secret-field redaction while preserving existing output, audit, approval, and artifact behaviors.

## 2026-07-06 Update: Tool Output Header and Key Field Redaction

The successful JSON output redactor now also treats cross-provider response fields such as `Authorization`, `setCookie`, `secretKey`, and `private_key` as sensitive fields. These values are replaced with `[REDACTED]` before the Tool Gateway returns the observation, while safe sibling fields remain intact.

This is a narrow output-redaction vocabulary hardening slice. It does not change output redaction wiring, text credential matching, failure error handling, Tool Gateway execution, audit result-summary shape, approval policy, or artifact publication.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 26/26, covering authorization, cookie, private-key, and secret-key JSON field redaction alongside the existing output/audit behaviors.

## 2026-07-06 Update: OpenAPI Provider Response Field Redaction

`OpenApiToolPortAdapter` now applies the broader credential-shaped JSON field vocabulary before returning provider observations, instead of relying only on the Tool Gateway fallback redactor. OpenAPI JSON responses now redact normalized/case-varied fields such as `Authorization`, `setCookie`, `clientSecret`, `private_key`, `sessionToken`, and `secretKey`, while preserving `secretRef` references and safe sibling fields.

This is a narrow provider-layer output-redaction hardening slice. It does not change OpenAPI HTTP invocation, credential injection, request argument handling, Tool Gateway policy/audit flow, generic output redaction wiring, or response truncation semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=OpenApiToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 3/3, covering direct adapter response redaction before any gateway-level fallback.

## 2026-07-06 Update: Audit Payload Header and Key Redaction

`AuditRedactionPolicy` now redacts additional credential-shaped audit payload fields such as `setCookie`, `private_key`, and `sessionId`, aligning the audit ledger redaction vocabulary with the Tool Gateway and OpenAPI response redactors while preserving `secretRef` references for traceability.

This is a narrow audit-ledger redaction hardening slice. It does not change audit event persistence, invalid-JSON fail-closed behavior, Tool Gateway request/completion summaries, output redaction wiring, approval policy, or provider execution.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=AuditRedactionPolicyTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 3/3, covering nested authorization, cookie, private-key, and session-id payload redaction while preserving `secretRef`.

## 2026-07-06 Update: OpenAPI Provider Text Response Redaction

`OpenApiToolPortAdapter` now redacts credential-shaped substrings from non-JSON and invalid-JSON provider response bodies before returning the tool observation. This closes the provider-layer path where text responses could otherwise carry `api_key=...`, bearer tokens, OpenAI-style `sk-...` keys, or malformed JSON credential fragments and rely only on the Tool Gateway fallback redactor.

This is a narrow provider-layer output-redaction hardening slice. It does not change HTTP invocation, JSON response field redaction, credential injection, response truncation, Tool Gateway policy/audit flow, or generic output redaction wiring.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=OpenApiToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 5/5, covering text/plain and invalid application/json response redaction before any gateway-level fallback.

## 2026-07-06 Update: Colon-Delimited Credential Text Redaction

Tool Gateway output redaction, OpenAPI provider text response redaction, and audit payload redaction now treat colon-delimited credential fragments such as `api_key: ...`, `access_token: ...`, and `password: ...` the same as existing equals-delimited fragments. This closes common header/log formatting paths while keeping JSON field redaction and safe `secretRef` handling unchanged.

This is a narrow credential-pattern hardening slice. It does not change tool execution, OpenAPI request construction, JSON response field vocabulary, audit persistence, approval policy, artifact publication, or the default redaction wiring.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,OpenApiToolPortAdapterTests,AuditRedactionPolicyTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 34/34, covering colon-delimited credential redaction in failed tool errors, OpenAPI text/invalid-JSON response bodies, and audit payload string values.

## 2026-07-06 Update: Header-Style Credential Text Redaction

Tool Gateway output redaction, OpenAPI provider text response redaction, and audit payload redaction now cover additional header/log credential fragments such as `Authorization: Bearer ...`, `secret_key: ...`, `private_key: ...`, `session_token: ...`, and `set-cookie: ...`. Authorization header fragments are replaced as a whole credential-shaped substring instead of leaving the header name visible with only the bearer value redacted.

This is a narrow text-redaction vocabulary hardening slice. It does not change JSON field redaction semantics, OpenAPI request construction, credential injection, audit persistence, approval policy, artifact publication, or default redaction wiring.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,OpenApiToolPortAdapterTests,AuditRedactionPolicyTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 34/34, covering failed tool errors, OpenAPI text/invalid-JSON provider responses, and audit payload string values with header-style credential fragments.

## 2026-07-06 Update: Cookie Header Text Redaction

Tool Gateway output redaction, OpenAPI provider text response redaction, and audit payload redaction now also cover exact `Cookie: ...` / `cookie: ...` text fragments. The pattern is intentionally limited to a standalone `cookie` key followed by `:` or `=`, so value-free governance metadata such as `cookieCount` and `sessionStateCookieCount` remains visible while raw cookie header values are removed.

This is a narrow text-redaction vocabulary hardening slice. It does not change sandbox browser cookie injection/replay, cookie count summaries, JSON field redaction, OpenAPI request construction, audit persistence, approval policy, artifact publication, or default redaction wiring.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,OpenApiToolPortAdapterTests,AuditRedactionPolicyTests,SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 66/66, covering Cookie header text redaction in Tool Gateway/OpenAPI/audit paths while preserving sandbox browser cookie/session metadata behavior.

## 2026-07-06 Update: Shared Credential Text Redactor

Credential-shaped text redaction is now centralized in the domain-level `CredentialTextRedactor` used by Tool Gateway output redaction, OpenAPI provider text response redaction, and audit payload credential detection. This removes three duplicated regex copies so future credential vocabulary changes land in one place instead of drifting across gateway, provider, and audit paths.

This is a narrow maintainability hardening slice. It does not change JSON field redaction semantics, OpenAPI request construction, credential injection, audit persistence, approval policy, artifact publication, sandbox browser cookie/session metadata, or default redaction wiring.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=CredentialTextRedactorTests,LocalToolGatewayPortAuditTests,OpenApiToolPortAdapterTests,AuditRedactionPolicyTests,SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 69/69, covering the shared redactor directly plus the three existing caller paths and sandbox browser cookie/session metadata behavior.

## 2026-07-06 Update: Cookie JSON Field Redaction

Tool Gateway JSON output redaction, OpenAPI provider JSON response redaction, and audit payload redaction now treat exact `Cookie` / `cookie` fields as sensitive while preserving value-free governance fields such as `cookieCount`. This closes the JSON response/audit path that could otherwise retain raw cookie header values when providers serialize headers as ordinary object fields.

This is a narrow JSON field vocabulary hardening slice. It does not change text credential redaction, sandbox browser cookie injection/replay, cookie count summaries, OpenAPI request construction, credential injection, audit persistence, approval policy, artifact publication, or default redaction wiring.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,OpenApiToolPortAdapterTests,AuditRedactionPolicyTests,SandboxBrowserToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 66/66, covering exact Cookie JSON field redaction in Tool Gateway/OpenAPI/audit paths while preserving cookie count governance metadata.

## 2026-07-06 Update: Tool Gateway Session Token JSON Field Redaction

Tool Gateway JSON output redaction now treats `sessionToken` / `session_token` fields as sensitive, aligning the fallback redactor with the OpenAPI provider and audit payload redaction vocabularies. The match is limited to session-token field names instead of every token-containing field, so value-free metadata such as `tokenCount` remains visible.

This is a narrow Tool Gateway JSON field vocabulary hardening slice. It does not change text credential redaction, OpenAPI provider redaction, audit payload redaction, sandbox browser token/query guards, result-summary shape, approval policy, artifact publication, or default redaction wiring.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 26/26, covering `sessionToken` JSON output redaction while preserving `tokenCount` metadata.

## 2026-07-06 Update: OpenAPI and Audit Token Metadata Preservation

OpenAPI provider JSON response redaction and audit payload redaction now treat exact `token` fields and concrete credential token fields such as `accessToken`, `refreshToken`, and `sessionToken` as sensitive without using a broad contains-`token` match. This preserves value-free metadata such as `tokenCount` while continuing to redact raw token fields.

This is a narrow JSON field vocabulary hardening slice. It does not change Tool Gateway fallback redaction, text credential redaction, OpenAPI request construction, credential injection, audit persistence, approval policy, artifact publication, or default redaction wiring.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=OpenApiToolPortAdapterTests,AuditRedactionPolicyTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 8/8, covering exact token redaction and `tokenCount` preservation in OpenAPI provider and audit payload paths.

## 2026-07-06 Update: Shared Credential JSON Field Classifier

Credential-shaped JSON field classification is now centralized in the domain-level `CredentialJsonFieldClassifier`. Tool Gateway output redaction uses the shared basic output-field vocabulary, while OpenAPI provider response redaction and audit payload redaction use the shared provider/audit extension that still covers broader `secret*` fields and preserves `secretRef`.

This is a narrow maintainability hardening slice. It does not change text credential redaction, OpenAPI request construction, credential injection, audit persistence, approval policy, artifact publication, sandbox browser metadata, or default redaction wiring.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=CredentialJsonFieldClassifierTests,CredentialTextRedactorTests,LocalToolGatewayPortAuditTests,OpenApiToolPortAdapterTests,AuditRedactionPolicyTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 40/40, covering the shared classifier directly plus Tool Gateway, OpenAPI, and audit caller paths.

## 2026-07-06 Update: Scoped Output Ignore Rule

The repository ignore rule for `output/` is now scoped to the repository root instead of every nested directory named `output`, and the frontend Playwright output directory is ignored explicitly. This prevents domain source and test packages such as `.../agent/output` from being silently excluded while keeping generated frontend evidence artifacts out of ordinary Git status noise.

This is a narrow repository-governance slice. It does not change runtime code, redaction semantics, Tool Gateway execution, audit persistence, approval policy, or frontend test behavior.

Fresh evidence: `git check-ignore -v -- output/example.tmp frontend/output/example.tmp seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/output/NewSourceExample.java seahorse-agent-kernel/src/test/java/com/miracle/ai/seahorse/agent/kernel/domain/agent/output/NewSourceExampleTests.java` ignored only root `output/` and `/frontend/output/` paths, while source/test `output` package paths were not ignored.

## 2026-07-06 Update: Sandbox Tool Argument Shape Audit

Tool Gateway request audit now includes value-free argument shape metadata for `sandbox_python` and `sandbox_file_convert`: argument count, value count, total value length, and maximum value length. This aligns the two sandbox-backed tools with the generic/OpenAPI/A2A audit summaries while still excluding raw code, file content, requested host values, and converted document payloads.

This is a narrow cross-provider audit-hardening slice. It does not change sandbox execution, file conversion behavior, egress policy, approval policy, quota policy, artifact publication, or output redaction.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 26/26, covering sandbox Python and file-conversion argument shape metadata while preserving assertions that raw code/content/host values are excluded from request audit summaries.

## 2026-07-06 Update: Remote A2A Argument Shape Audit

Tool Gateway request audit now includes value-free top-level argument shape metadata for `invoke_remote_a2a_agent`: argument count, value count, total value length, and maximum value length. This complements the existing A2A-specific agent-name, prompt-length, metadata-key, metadata-value, and version-shape fields without storing the raw prompt, target agent name, requested version value, or metadata values.

This is a narrow cross-provider audit-hardening slice. It does not change A2A request validation, signing, connector invocation, failure degradation, approval policy, quota policy, or remote-agent execution behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 26/26, covering remote A2A argument shape metadata while preserving raw prompt, agent-name, version, and metadata value redaction assertions.

## 2026-07-06 Update: Sandbox Browser Argument Shape Audit

Tool Gateway request audit now includes value-free top-level argument shape metadata for `sandbox_browser`: argument count, value count, total value length, and maximum value length. This aligns browser automation audit summaries with the generic, OpenAPI, A2A, Python sandbox, and file-conversion tool summaries while keeping raw URL, HTML, cookie, localStorage, session-state, and host values out of the audit payload.

This is a narrow cross-provider audit-hardening slice. It does not change browser execution, URL/session validation, egress policy, approval policy, quota policy, artifact publication, or output redaction.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 26/26, covering sandbox browser argument shape metadata while preserving existing raw URL/HTML/cookie/session/host redaction assertions.

## 2026-07-06 Update: Tool Gateway Failed Completion Shape Audit

Tool Gateway completion audit now emits value-free result summaries for failed, denied, and approval-required tool invocations. The summary records content absence, redacted-error presence, redacted-error length, and approval-id presence, so failed completion records have structured diagnostic shape without storing raw error text in the summary payload.

This is a narrow completion-audit hardening slice. It does not change policy decisions, approval request persistence, tool execution, caller-visible errors, output redaction, artifact publication, or successful result summaries.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 26/26, covering denied, approval-required, thrown-exception, and failed-tool completion summaries while preserving failed-error redaction assertions.

## 2026-07-06 Update: Tool Gateway Success JSON Value Shape Audit

Tool Gateway completion audit now adds value-free JSON leaf-value shape metadata for successful JSON tool observations: leaf value count, total leaf value length, and maximum leaf value length. The summary continues to omit raw tool output values and JSON field names while giving operators a little more diagnostic shape than content length alone.

This is a narrow completion-audit hardening slice. It does not change tool execution, output redaction, artifact publication, failure summaries, approval policy, quota policy, or caller-visible tool observations.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 26/26, covering successful JSON value-shape metadata while preserving existing result-summary redaction assertions.

## 2026-07-06 Update: Tool Gateway Empty Success Result Shape Audit

Tool Gateway completion audit now emits a value-free result summary for successful tool invocations that return no content. The summary records `contentPresent=false`, `contentLength=0`, and `contentJsonType=none`, closing the previous shape gap where a successful empty observation produced a null completion summary.

This is a narrow completion-audit hardening slice. It does not change tool execution, caller-visible tool results, output redaction, artifact publication, failure summaries, approval policy, or quota policy.

Fresh evidence: the regression first failed because successful null-content results produced a null `resultSummary`; after the fix, `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 27/27, covering successful empty-content completion summaries while preserving existing success/failure audit coverage.

## 2026-07-06 Update: Shared Failed-Error Credential Redaction

Tool Gateway failed completion audit now uses the shared `CredentialTextRedactor` for failed-tool error redaction instead of keeping a local regex copy. Failed tool observations, completion `errorMessage`, and result-summary error length now stay aligned with the shared credential-text vocabulary for header-style authorization, cookie, secret-key, and OpenAI-style key fragments.

This is a narrow maintainability and audit-hardening slice. It does not change tool execution, approval decisions, caller-visible non-credential errors, successful output redaction, artifact publication, result-summary schema, or the shared credential redaction vocabulary itself.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 30/30, covering shared failed-error credential redaction while preserving Tool Gateway audit and shared redactor behavior.

## 2026-07-06 Update: Tool Gateway JSON Top-Level Shape Audit

Tool Gateway successful completion audit now adds value-free top-level JSON shape metadata for successful tool observations: `contentJsonTopLevelFieldCount` for object responses and `contentJsonTopLevelElementCount` for array responses. This complements the existing JSON type and leaf-value length/count metadata without storing response field names or raw values.

This is a narrow completion-audit hardening slice. It does not change tool execution, output redaction, artifact publication, result-summary storage schema, failure summaries, approval policy, quota policy, or caller-visible tool observations.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 28/28, covering object and array top-level JSON shape metadata while preserving assertions that raw JSON field names and values are excluded from completion summaries.

## 2026-07-06 Update: Tool Gateway Text Result Shape Audit

Tool Gateway successful completion audit now adds value-free text shape metadata for non-JSON text observations: `contentTextLineCount` and `contentTextMaxLineLength`. This gives operators bounded diagnostic shape for plain-text tool output without storing any text snippets, line content, or credential-bearing values in the completion summary.

This is a narrow completion-audit hardening slice. It does not change tool execution, output redaction, JSON result summaries, artifact publication, failure summaries, approval policy, quota policy, or caller-visible tool observations.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 29/29, covering multi-line text shape metadata while preserving assertions that raw text values are excluded from completion summaries.

## 2026-07-06 Update: Tool Approval Summary Tool ID Preview

Tool approval request summaries now filter the human-readable tool-id preview through the same safe preview boundary used for argument and resource-ref keys. The persisted `ApprovalRequest.toolId` still retains the real tool id for matching and resume semantics, while the display `summary` falls back to `unsafe-tool-id` when a pre-validation tool id contains unsafe characters or credential-shaped markers.

This is a narrow approval-governance hardening slice. It does not change approval matching, pending approval persistence, tool execution, request audit summaries, arguments preview JSON, policy decisions, or resume behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 30/30, covering unsafe tool-id suppression in approval summaries while preserving the real persisted `toolId`.

## 2026-07-06 Update: Tool Approval Summary Reason Preview

Tool approval request summaries now also filter the human-readable policy reason-code preview through the safe preview boundary. Both direct Tool Gateway approvals and governed preflight approvals fall back to `unsafe-reason-code` when a policy adapter returns a reason code with unsafe characters or credential-shaped markers, while the underlying policy decision remains unchanged for machine handling.

This is a narrow approval-governance hardening slice. It does not change policy decisions, approval matching, pending approval persistence, tool execution, request audit summaries, arguments preview JSON, persisted tool ids, or resume behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,LocalGovernedToolExecutionPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 35/35, covering unsafe reason-code suppression in both direct Tool Gateway and governed preflight approval summaries.

## 2026-07-06 Update: Governed Tool Permission Reason Message Redaction

Governed tool preflight permissions now redact credential-shaped policy reason messages before returning them to pluggable agent executors. The machine-readable `reasonCode` remains unchanged for policy handling, while the human-readable `reasonMessage` uses the shared `CredentialTextRedactor` across ALLOW, DENY, and APPROVAL_REQUIRED effects.

This is a narrow governed-preflight display hardening slice. It does not change policy decisions, approval matching, pending approval persistence, approval summaries, Tool Gateway invocation, request audit summaries, arguments preview JSON, or resume behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalGovernedToolExecutionPortTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 5/5, covering reason-message credential redaction across allow, deny, and approval-required preflight outcomes.

## 2026-07-06 Update: Approval Decision Comment Redaction

Approval management now redacts credential-shaped decision comments before persisting approve, reject, or modified decisions. This protects the operator-visible `decisionComment` and resume-facing rejection/expiration comment path while preserving approval status transitions, decision timestamps, modified-argument validation, and ownership/admin authorization checks.

This is a narrow approval-management display hardening slice. It does not change approval request creation, policy decisions, approval matching, Tool Gateway invocation, arguments preview validation, request audit summaries, or frontend approval workflows.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelApprovalManagementServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 18/18, covering approve, reject, and modify decision-comment redaction plus shared credential text redactor behavior.

## 2026-07-06 Update: Approval Resume Decision Comment Defense

Agent run resume now defensively redacts credential-shaped approval decision comments when a waiting run transitions to REJECTED or EXPIRED. This protects historical or externally written approval records that predate the approval-management write-side redaction from being copied into `AgentRun.errorMessage`.

This is a narrow resume-boundary hardening slice. It does not mutate stored approval records, change approved/modified resume execution, alter approval matching, modify Tool Gateway invocation, or change normal approval-management write behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentRunResumeServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 8/8, covering rejected and expired resume transitions with historical credential-bearing decision comments while preserving approved/modified resume behavior.

## 2026-07-06 Update: Agent Run Snapshot Step Error Redaction

Agent run snapshots now redact credential-shaped step error text before exposing `AgentRunSnapshotStep.summary` and `AgentRunSnapshotStep.errorMessage`. This protects historical step records whose stored `errorMessage` contains bearer tokens or other credential fragments from leaking through the run status/detail projection.

This is a narrow snapshot-projection hardening slice. It does not mutate stored `AgentStep` records, change step recording, alter workflow ordering, modify checkpoint sanitization, or change run resume behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentRunSnapshotServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 9/9, covering snapshot summary/error redaction for credential-bearing historical step errors while preserving snapshot ownership, pending approval, source, artifact, and checkpoint minimization behavior.

## 2026-07-06 Update: Agent Run Workflow Node Text Redaction

Agent run workflow projections now redact credential-shaped node display text before exposing `AgentRunWorkflowNodeData.label`, `description`, and `errorMessage`. This closes the companion workflow graph path for historical step output/error text that may contain bearer tokens or credential fragments.

This is a narrow workflow-projection hardening slice. It does not mutate stored `AgentStep` records, change workflow graph layout, alter owner/admin authorization, modify snapshot projections, or change run execution/resume behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentRunWorkflowServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 6/6, covering workflow node label, description, and error-message redaction while preserving owner access and unrelated-user denial.

## 2026-07-06 Update: Legacy Workflow Visualization Result Data Redaction

Legacy workflow visualizations now redact credential-shaped `ExecutionStepAggregate.resultData` before returning nodes from `KernelWorkflowVisualizationService`. The projection recursively handles nested maps/lists, redacts sensitive field names such as `accessToken`, `cookie`, and `password`, and applies credential-pattern redaction to ordinary string values.

This is a narrow legacy visualization-boundary hardening slice. It does not mutate stored workflow step aggregates, change workflow ordering, alter sequential edge construction, modify owner/admin authorization, or affect the newer Agent run workflow projection.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelWorkflowVisualizationServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 9/9, covering result-data redaction for historical credential-bearing workflow aggregates while preserving owner/admin access, unrelated-user denial, numeric user-id compatibility, and legacy ungated constructor behavior.

## 2026-07-06 Update: Approval Query Projection Redaction

Approval management queries now defensively redact credential-shaped approval display text before returning records from `page`, `findById`, and `listPendingByRunId`. The projection covers historical or externally written `summary`, `argumentsPreviewJson`, and `decisionComment` values while leaving the underlying approval repository record unchanged.

Modified approval decisions also sanitize the allowed `argumentsPreviewJson` payload before persistence, so approved argument previews can keep value-free shape while suppressing sensitive fields and credential-shaped string values.

This is a narrow approval-query and modify-preview hardening slice. It does not change approval authorization, approval status transitions, approval matching, Tool Gateway invocation, resume execution, or the approval repository schema.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelApprovalManagementServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 20/20, covering historical query projection redaction, modified-preview write redaction, decision-comment write redaction, owner/admin access, and existing malformed-preview validation.

## 2026-07-06 Update: Audit Query Projection Redaction

Audit ledger queries now defensively reapply `AuditRedactionPolicy` before returning records from `findById` and `page`. This protects historical or externally written audit rows whose `redactedPayload` may still contain credential-shaped values, while keeping the repository record unchanged.

This is a narrow audit-query hardening slice. It does not change audit write failure policy, audit event metadata, query filters, repository schema, Tool Gateway invocation behavior, or the redaction vocabulary itself.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAuditLedgerServiceTests,AuditRedactionPolicyTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 6/6, covering append-time redaction, query-time projection redaction for historical payloads, write failure policies, and shared audit redaction behavior.

## 2026-07-06 Update: Agent Run Query Projection Redaction

Legacy Agent run queries now defensively redact credential-shaped run and step display text before returning records from `findRunById`, `page`, and `listSteps`. The projection covers historical `AgentRun.inputSummary`, `AgentRun.errorMessage`, `AgentRun.metadataJson`, and `AgentStep.inputJson` / `outputJson` / `errorMessage` values while leaving repository records unchanged.

Run failure writes now also redact credential-shaped `errorMessage` values before persisting the failed terminal state, aligning the older `AgentRunInboundPort.fail` path with the snapshot/workflow/resume display hardening.

This is a narrow legacy run-query and fail-write hardening slice. It does not change run ownership checks, paging filters, worker lifecycle transitions, retry/cancel/succeed semantics, snapshot assembly, workflow projections, or repository schema.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentRunServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 27/27, covering run detail/page/step projection redaction, fail-write error redaction, owner/admin authorization, numeric owner compatibility, and existing run lifecycle behavior.

## 2026-07-06 Update: Tool Invocation Audit Query Projection Redaction

Tool invocation audit queries now defensively redact credential-shaped display text before returning records from `KernelToolInvocationAuditQueryService.page`. The projection covers historical `argumentsSummary`, `resultSummary`, and `errorMessage` values while leaving the underlying audit query record unchanged.

This is a narrow Tool Gateway audit-query hardening slice. It does not change admin authorization, query filters, audit write paths, completion summary generation, policy decisions, Tool Gateway execution, or repository schema.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelToolInvocationAuditQueryServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 6/6, covering query-time redaction for historical credential-bearing tool invocation audit entries, admin-only access, query parameter propagation, and shared credential text redactor behavior.

## 2026-07-06 Update: Run Context Snapshot Query Projection Redaction

Run context snapshot queries now defensively redact credential-shaped JSON/text fields before returning `RunContextSnapshotRecord` from `KernelRunContextSnapshotService.findByRunId`. The projection covers historical `executorConfigJson`, `traceContextJson`, and `snapshotJson` values, recursively redacts sensitive JSON field names such as `apiKey`, `authorization`, and `password`, and applies shared credential-text redaction to ordinary string values while leaving the underlying snapshot repository record unchanged.

This is a narrow run-context query hardening slice. It does not change snapshot write paths, owner/admin authorization, legacy task snapshot lookup compatibility, run-profile selection, AgentScope trace metadata writing, or repository schema.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunContextSnapshotServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 9/9, covering query-time projection redaction for historical credential-bearing run context snapshots, repository immutability, owner/admin access, unrelated-user denial, numeric owner compatibility, and shared credential text redactor behavior.

## 2026-07-06 Update: Run Profile Web Projection Redaction

Run profile HTTP projections now defensively redact credential-shaped display/config fields before returning `list`, `detail`, `resolve-preview`, conversation apply, and applied conversation responses from `SeahorseRunProfileController`. The projection covers `executorConfigJson`, `modelConfigJson`, `memoryScopeJson`, `guardrailConfigJson`, profile name/description, and approval display text, recursively redacting sensitive JSON field names and credential-shaped string values while leaving the underlying `RunProfileInboundPort` objects unchanged.

This is a narrow Web adapter display-boundary hardening slice. It does not change run profile save/update semantics, kernel `RunProfileInboundPort` runtime reads, chat/agent-run execution config resolution, risk summary, production gate checks, repository schema, or frontend request contracts.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseRunProfileControllerTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 16/16, covering HTTP projection redaction for run profile list/detail/preview/apply/applied-profile responses, port-object immutability, existing run-profile controller routes, and shared credential text redactor behavior.

## 2026-07-06 Update: Run Profile Approval Comment Write Redaction

Run profile approval status updates now redact credential-shaped approval comments before persistence in `KernelRunProfileService.submitApproval`, `approve`, and `reject`. This protects newly written run-profile governance history even before Web projection redaction is applied, while preserving approval status transitions, operator recording, approval timestamps, audit-summary shape, and readonly profile protections.

This is a narrow run-profile governance write-boundary hardening slice. It does not change run-profile config persistence, Tool Gateway policy decisions, production-gate checks, conversation profile binding, runtime execution config resolution, repository schema, or the Web adapter projection defense.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunProfileServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 15/15, covering submit/approve/reject approval-comment write redaction, existing run-profile lifecycle behavior, governance summaries, and shared credential text redactor behavior.

## 2026-07-06 Update: Run Profile Summary Tool ID Preview Guard

Run profile risk and audit summaries now filter tool-id display values through the same safe-preview posture used by Tool Gateway approval summaries. Unsafe high-risk tool identifiers containing credential-shaped markers, whitespace, unsupported characters, or excessive length are rendered as `unsafe-tool-id` in `RunProfileRiskSummary` messages and `RunProfileAuditSummary.highRiskToolIds`, while the underlying run-profile tool bindings keep their real tool ids for execution and management semantics.

This is a narrow run-profile governance display-boundary hardening slice. It does not change tool binding persistence, resolve-preview tool allowlists, Tool Gateway invocation, approval matching, risk-code computation, production-gate blocking codes, repository schema, or Web adapter projection redaction.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunProfileServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 16/16, covering unsafe tool-id suppression in run-profile risk/audit summaries, binding immutability, existing run-profile lifecycle behavior, and shared credential text redactor behavior.

## 2026-07-06 Update: Run Experiment Report Credential Redaction

Run Experiment report export now defensively redacts credential-shaped display text before rendering Markdown or deriving the report file name in `KernelRunExperimentService.exportReport`. The report boundary covers experiment names, trial scores, metric JSON, trace evidence, failure messages, table cells, and output code blocks while leaving persisted experiment records, trial metrics/scores, run-context snapshots, branch messages, and executor results unchanged.

This is a narrow report-rendering hardening slice. It does not change Run Experiment execution semantics, report schema/sections, frontend preview behavior, score persistence, metric persistence, trace snapshot capture, repository schema, or API authorization.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 12/12, covering report-boundary redaction for credential-bearing experiment names, score JSON, metric JSON, trace context, failure text, output content, report file names, existing failure-report behavior, branch evidence rendering, and shared credential text redactor behavior.

## 2026-07-06 Update: Run Experiment HTTP Projection Redaction

Run Experiment HTTP responses now defensively redact credential-shaped display text before returning `RunExperimentDetails` from create, detail, cancel, and score endpoints in `SeahorseRunExperimentController`. The Web projection covers experiment names, trial `scoreJson`, trial `metricJson`, and trial failure messages, recursively redacts sensitive JSON field names, and applies shared credential-text redaction to string values while leaving the underlying inbound-port result objects unchanged.

This is a narrow Web adapter display-boundary hardening slice. It does not change Run Experiment command input semantics, kernel persistence, scoring writes, cancellation behavior, report export rendering, trial fork-to-branch behavior, repository schema, or frontend response contracts.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseRunExperimentControllerTests,CredentialTextRedactorTests,CredentialJsonFieldClassifierTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 12/12, covering HTTP projection redaction for credential-bearing experiment names, score JSON, metric JSON, failure text, projection immutability, existing Run Experiment controller routes, and shared credential redaction/classifier behavior.

## 2026-07-06 Update: Run Experiment Fork Branch Projection Redaction

Run Experiment trial fork-to-branch responses now defensively redact credential-shaped message display text before returning the switched branch tree from `SeahorseRunExperimentController`. The projection covers branch message `content` and `thinkingContent` for the trial output branch preview while preserving branch ids, parent ids, active flags, sibling metadata, and the underlying `ConversationBranchInboundPort` result objects.

This is a narrow Run Experiment Web adapter hardening slice. It does not change normal conversation tree APIs, branch switching semantics, persisted conversation messages, trial output message ids, Run Experiment detail/report projections, repository schema, or frontend response shape.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-web -am "-Dtest=SeahorseRunExperimentControllerTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 9/9, covering fork-to-branch message content/thinking-content redaction, branch projection immutability, existing Run Experiment controller routes, and shared credential text redactor behavior.

## 2026-07-06 Update: Run Experiment Kernel Projection Redaction

Run Experiment kernel inbound responses now defensively redact credential-shaped display text before returning `RunExperimentDetails` from `KernelRunExperimentService.create`, `findById`, `cancel`, and `scoreTrial`. The projection covers experiment names, trial `scoreJson`, trial `metricJson`, and trial failure messages, recursively redacts sensitive JSON field names, and applies shared credential-text redaction to ordinary string values while leaving repository records unchanged.

This is a narrow kernel-boundary hardening slice for non-Web callers of `RunExperimentInboundPort`. It does not change Run Experiment execution inputs, persistence semantics, score writes, metric writes, cancellation behavior, report format, Web adapter projections, trial fork-to-branch behavior, repository schema, or stored experiment/trial values.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests,CredentialTextRedactorTests,CredentialJsonFieldClassifierTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 16/16, covering kernel inbound projection redaction for credential-bearing experiment names, score JSON, metric JSON, failure text, repository immutability, existing Run Experiment execution/report behavior, and shared credential redaction/classifier behavior.

## 2026-07-06 Update: Run Experiment Trial Score Write Redaction

Run Experiment trial scoring now redacts credential-shaped `scoreJson` before persistence in `KernelRunExperimentService.scoreTrial`. The write boundary parses JSON when possible, recursively redacts sensitive field names such as `apiKey`, applies shared credential-text redaction to string values, and preserves ordinary scoring fields such as numeric ratings or costs.

This is a narrow score-write hardening slice. It does not change trial execution metrics, executor error persistence, experiment creation, cancellation behavior, report format, Web adapter projections, repository schema, or scoring endpoint request contracts.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests,CredentialTextRedactorTests,CredentialJsonFieldClassifierTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 17/17, covering score write redaction before repository persistence, inbound projection redaction, existing Run Experiment execution/report behavior, and shared credential redaction/classifier behavior.

## 2026-07-06 Update: Run Experiment Name Write Redaction

Run Experiment creation now redacts credential-shaped experiment names before persistence in `KernelRunExperimentService.create`. This protects the label stored on `RunExperimentRecord` at the source while preserving ordinary experiment names, conversation/base-leaf ids, run-profile trial creation, execution inputs after the safe label is stored, and existing downstream projection/report defenses.

This is a narrow experiment-name write hardening slice. It does not change trial score writes, trial metric/error persistence, execution status transitions, report format, Web adapter response shape, repository schema, or run-profile selection semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 15/15, covering experiment-name write redaction before repository persistence, existing Run Experiment execution/report behavior, score write redaction, and shared credential text redactor behavior.

## 2026-07-06 Update: Run Experiment Trial Execution Write Redaction

Run Experiment trial execution updates now redact credential-shaped executor output before persistence in `KernelRunExperimentService.executeCreatedExperiment`. The write boundary parses trial `metricJson` when possible, recursively redacts sensitive JSON field names, applies shared credential-text redaction to string values, and redacts trial `errorMessage` text before storing the trial record.

This is a narrow trial-execution write hardening slice. It does not change trial status normalization, run/output message ids, score writes, experiment-name writes, report format, Web adapter response shape, repository schema, or executor invocation inputs.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests,CredentialTextRedactorTests,CredentialJsonFieldClassifierTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 18/18, covering trial metric/error write redaction before repository persistence, inbound projection redaction, existing Run Experiment execution/report behavior, and shared credential redaction/classifier behavior.

## 2026-07-06 Update: Run Context Snapshot Write Redaction

Run context snapshot persistence now redacts credential-shaped snapshot fields before repository writes across chat agent runs, legacy/RAG chat snapshots, agent-run starts, and Run Experiment trial execution snapshots. The shared `RunContextSnapshotRedactor` copies snapshot records, recursively redacts sensitive JSON field names and credential-shaped string values in `executorConfigJson`, `traceContextJson`, and `snapshotJson`, and is reused by the query projection service so persisted-write and read-boundary defenses stay aligned.

This is a narrow run-context snapshot write-boundary hardening slice. It does not change agent run creation, chat streaming behavior, Run Experiment executor invocation, snapshot schema, repository ports, authorization checks, non-sensitive snapshot metadata, or downstream trace/profile lookup semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunContextSnapshotServiceTests,KernelAgentRunServiceTests,KernelChatAgentRunStoreTests,CredentialTextRedactorTests,CredentialJsonFieldClassifierTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 56/56, covering shared snapshot redaction, query projection immutability, agent-run snapshot write redaction, chat snapshot write redaction, existing run-context authorization behavior, and shared credential redaction/classifier behavior.

## 2026-07-06 Update: Agent Run Step Write Redaction

Agent run step recording now redacts credential-shaped step payloads before persisting `AgentStep` records from `RepositoryAgentRunStepRecorder`. The write boundary covers model-turn input/output JSON, model-turn error messages, tool-call argument snapshots, tool observation output JSON, and failed tool observation error messages, recursively redacting sensitive JSON field names and credential-shaped string values.

This is a narrow agent-run step write-boundary hardening slice. It does not change model/tool execution inputs, Tool Gateway invocation behavior, step numbering, status transitions, snapshot/workflow/query projection defenses, repository schema, or caller-visible observations during the active run loop.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=RepositoryAgentRunStepRecorderTests,KernelAgentRunServiceTests,KernelAgentRunSnapshotServiceTests,KernelAgentRunWorkflowServiceTests,CredentialTextRedactorTests,CredentialJsonFieldClassifierTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 41/41, covering model-turn and tool-call step write redaction, existing run query projection redaction, snapshot/workflow historical redaction, and shared credential redaction/classifier behavior.

## 2026-07-06 Update: Agent Run Resume Step Write Redaction

Agent run resume now redacts credential-shaped step payloads before persisting the direct `AgentStep` records written by `KernelAgentRunResumeService`. This covers resumed tool-call argument snapshots, tool result content/error JSON, resumed model-turn message history, and resumed final-answer output, while preserving the original modified approval arguments, tool result content, and model context used during execution.

This is a narrow resume-write hardening slice. It does not change approval matching, modified-argument execution semantics, Tool Gateway invocation, model resume context, run status transitions, repository schema, or the existing snapshot/workflow/query projection defenses.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentRunResumeServiceTests,KernelAgentRunSnapshotServiceTests,KernelAgentRunWorkflowServiceTests,CredentialTextRedactorTests,CredentialJsonFieldClassifierTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 21/21, covering resumed step write redaction, execution-input preservation, rejected/expired decision-comment redaction, snapshot/workflow historical redaction, and shared credential redaction/classifier behavior.

## 2026-07-06 Update: Agent Loop Degraded Answer Redaction

Agent Loop timeout recovery now redacts credential-shaped text before rendering degraded final answers from `KernelAgentLoop.degradedFinalAnswer`. This covers the timeout message, tool id display text, and successful tool observation content returned as Markdown when the model times out after tool execution, while preserving the existing completed-tool-results fallback behavior.

This is a narrow active-run display-boundary hardening slice. It does not change model turn timeout detection, tool execution inputs, successful observation storage, stream event ordering, output artifact emission, repository schema, or the existing agent-run step write redaction defenses.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentLoopToolGatewayTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 27/27, covering degraded final-answer redaction for credential-bearing tool observations, existing timeout fallback behavior, Tool Gateway loop behavior, and shared credential text redactor behavior.

## 2026-07-06 Update: Sandbox Browser Runtime Summary Redaction

`sandbox_browser` observations now redact credential-shaped runtime `resultSummary` text before returning successful or failed Tool Gateway results. This covers runtime stdout/stderr summaries that may echo bearer tokens, cookies, or browser session/localStorage values, while preserving raw cookie/session-state inputs passed to the request-scoped browser runtime.

This is a narrow browser-tool display-boundary hardening slice. It does not change URL validation, allowed-host enforcement, cookie/session-state validation, browser runtime input JSON, artifact collection, scanner decisions, object storage copy rules, or Tool Gateway audit write paths.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=SandboxBrowserToolPortAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 37/37, covering successful and failed browser runtime summary redaction, execution-input preservation for cookie/session-state values, existing URL/session validation, and shared credential text redactor behavior.

## 2026-07-06 Update: Sandbox Tool Runtime Summary Redaction

`sandbox_python` and `sandbox_file_convert` observations now redact credential-shaped runtime `resultSummary` text before returning successful or failed Tool Gateway results. This closes the same stdout/stderr display boundary as the browser tool for code interpreter and file-conversion sandbox runs, while preserving raw code/content inputs passed to the sandbox runtime for execution.

This is a narrow sandbox-backed tool display-boundary hardening slice. It does not change code/content validation, sandbox session creation, network policy enforcement, runtime execution input JSON, artifact collection, scanner decisions, object storage copy rules, or Tool Gateway audit write paths.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=SandboxPythonToolPortAdapterTests,SandboxFileConvertToolPortAdapterTests,SandboxBrowserToolPortAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 56/56, covering successful and failed runtime summary redaction for Python, file conversion, and browser tools, execution-input preservation, existing sandbox tool behavior, and shared credential text redactor behavior.

## 2026-07-06 Update: AgentScope A2A Tool Failure Redaction

`invoke_remote_a2a_agent` failure degradation now redacts credential-shaped text in upstream connector exception messages after replacing the request prompt with `[redacted-prompt]`. This keeps the existing agent-name diagnostic and remote failure context while preventing bearer tokens, API keys, or similar material from returning through the tool error channel.

This is a narrow AgentScope A2A tool hardening slice. It does not change remote agent discovery, tenant propagation, prompt/metadata validation, A2A request execution, shared-secret or tenant-signed authentication, release-gate coverage, or live Studio/OTEL integration behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-agent-agentscope -am "-Dtest=AgentScopeA2AToolPortAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 8/8, covering prompt redaction, credential-shaped failure-message redaction, existing metadata validation, and shared credential text redactor behavior.

## 2026-07-06 Update: Cross-Provider Tool Failure Redaction

MCP, Web Search, Web Fetch, and Knowledge Base Search tool adapters now redact credential-shaped text before returning failed tool errors from upstream execution results or exceptions. This closes the active-run failure display boundary for common cross-provider tools while preserving raw execution arguments and successful observations.

This is a narrow tool-adapter display-boundary hardening slice. It does not change MCP orchestration, Web fetch SSRF policy, search/retrieval execution, successful tool output redaction, Tool Gateway audit persistence, approval policy, quota policy, or base `ToolInvocationResult` semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=WebResearchToolPortAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 8/8; `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=McpToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 5/5; `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=AgentToolPortAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 11/11. Coverage includes MCP failed results, MCP exceptions, Web Search exceptions, Web Fetch exceptions, Knowledge Base retrieval exceptions, and shared credential text redaction behavior.

## 2026-07-06 Update: Memory Tool Failure Redaction

Memory Read, Memory Write, and Memory Forget tool adapters now redact credential-shaped text before returning failed tool errors from memory engine, ingestion, governance, or management exceptions. Memory Write also redacts credential-shaped text in model-visible `governanceErrors` and `governanceError` observation fields.

This is a narrow memory-tool display-boundary hardening slice. It does not change memory read/write/delete execution inputs, user-scope enforcement, ingestion workflow behavior, governance execution, memory persistence, successful memory content observations, Tool Gateway audit persistence, approval policy, quota policy, or base `ToolInvocationResult` semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=AgentToolPortAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `CredentialTextRedactorTests` 3/3 and `AgentToolPortAdapterTests` 16/16. Coverage includes Memory Read exception redaction, Memory Write exception redaction with raw execution input preservation, Memory Write governance error-list redaction, Memory Write governance exception redaction, Memory Forget exception redaction, and existing memory tool behavior.

## 2026-07-06 Update: Generation Tool Failure Redaction

Newsletter, PPT, chart visualization, frontend design, image generation, and GitHub repository reader tools now redact credential-shaped text before returning failed tool errors from model or repository provider exceptions. The raw generation prompt and repository URL are still passed unchanged to the execution ports, while only the model-visible failure text is minimized.

This is a narrow generation-tool display-boundary hardening slice. It does not change prompt construction, model selection, image request fields, GitHub repository fetch parameters, successful generation observations, artifact publication, Tool Gateway audit persistence, approval policy, quota policy, or base `ToolInvocationResult` semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=ContentGenerationToolPortAdapterTests,GitHubProjectGenerationToolPortAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 15/15. Coverage includes chat-backed generation exception redaction with raw prompt preservation, GitHub repository reader exception redaction with raw repository URL preservation, image generation exception redaction with raw prompt preservation, and shared credential text redactor behavior.

## 2026-07-06 Update: Local Agent-as-Tool Failure Redaction

Local Agent-as-Tool handoff failures now redact credential-shaped text before returning failed tool errors from child-run creation or handoff execution exceptions. This keeps the local delegation failure path diagnostic enough for active runs while preventing bearer tokens, API keys, or similar material from flowing back through the model-visible error channel.

This is a narrow local handoff display-boundary hardening slice. It does not change handoff policy, child run creation inputs, repository schema, audit ledger shape, successful handoff JSON, Tool Gateway audit persistence, approval/quota policy, or base `ToolInvocationResult` semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalAgentAsToolPortTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 6/6, covering local handoff failure redaction, raw input-summary preservation for child run creation, existing successful handoff behavior, and shared credential text redactor behavior.

## 2026-07-06 Update: OpenAPI Tool Failure Error Redaction

OpenAPI dynamic-tool failures now redact credential-shaped text in the shared error JSON message before returning failed tool results. This covers invalid request construction, credential resolution/provider failures, HTTP request failures, and runtime execution failures while preserving raw credential references and request arguments for the actual invocation path.

This is a narrow OpenAPI tool display-boundary hardening slice. It does not change OpenAPI import, operation enablement, URI/request construction, credential binding storage, credential provider request fields, HTTP execution, response payload redaction, Tool Gateway audit persistence, approval/quota policy, or base `ToolInvocationResult` semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=OpenApiToolPortAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 9/9, covering OpenAPI failure-message redaction, raw credential-reference preservation for provider resolution, existing bearer injection behavior, response JSON/text redaction, and shared credential text redactor behavior.

## 2026-07-06 Update: Tool Gateway Exception Observation Redaction

Tool Gateway execution failures now redact credential-shaped text before returning failed tool errors when a `ToolPort` throws, and the Agent Loop applies the same redaction before turning failed Tool Gateway results or custom gateway exceptions into model-visible observations and stream tool-call events. This closes the active-run catch-all failure boundary without changing successful observations or raw tool execution inputs.

This is a narrow Tool Gateway and Agent Loop display-boundary hardening slice. It does not change policy decisions, approval creation, idempotency, tool registry lookup, artifact publication, output redaction configuration, audit shape summaries, run-step persistence redaction, successful tool result content, or base `ToolInvocationResult` semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=LocalToolGatewayPortAuditTests,KernelAgentLoopToolGatewayTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 60/60, covering thrown ToolPort exception redaction before return/audit, Agent Loop failed-observation redaction for custom gateway results, stream tool-call event redaction, existing Tool Gateway audit behavior, and shared credential text redactor behavior.

## 2026-07-06 Update: MCP Orchestrator Failure Result Redaction

Kernel MCP orchestration now redacts credential-shaped executor exception messages before creating `McpToolExecutionResult.failed(...)`. This closes the MCP failure-result boundary for callers that consume the orchestrator directly, while the Agent ToolPort adapter and Tool Gateway still keep their downstream redaction defenses.

This is a narrow MCP orchestration display-boundary hardening slice. It does not change MCP registry lookup, parameter extraction, executor invocation, concurrent intent execution, successful MCP content, ToolPort adapter behavior, Tool Gateway audit persistence, approval/quota policy, or base MCP result semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests -am "-Dtest=KernelMcpOrchestratorTests,McpToolPortAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 10/10, covering MCP executor exception redaction, existing MCP adapter redaction, tool-not-found/success behavior, intent-tool execution, and shared credential text redactor behavior.

## 2026-07-06 Update: Task Event Failure Redaction

Task orchestration failure events now redact credential-shaped exception messages before publishing task failure `message` text or `error` metadata. This covers chat-backed Agent task start failures, asynchronous AgentRun start failures, and streaming Agent callback errors, keeping task history/subscription surfaces safe without suppressing server logs.

This is a narrow task-event display-boundary hardening slice. It does not change task creation, conversation routing, AgentRun command inputs, stream callback sequencing, task status transitions, artifact publication, event bus storage semantics, or raw exception logging for operators.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=TaskOrchestrationServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 14/14, covering task failure event redaction for chat start exceptions, stream callback errors, existing task orchestration behavior, access checks, artifact listing guards, and shared credential text redactor behavior.

## 2026-07-06 Update: Sandbox Browser Preflight Failure Redaction

`sandbox_browser` parameter preflight failures now pass URL, allowed-host, cookie, and session-state validation exception text through the same browser display redactor used for runtime summaries. Existing validation messages are intentionally value-free, and this keeps that failure boundary hardened if future validation diagnostics accidentally include credential-shaped input.

This is a narrow browser-tool preflight display-boundary hardening slice. It does not change URL policy, allowed-host enforcement, cookie/session-state validation rules, request-scoped runtime input preservation, artifact collection, scanner decisions, Tool Gateway audit summaries, or runtime execution behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=SandboxBrowserToolPortAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 38/38, covering preflight failure redaction, existing URL/query/cookie/session-state guards, runtime summary redaction, execution-input preservation, and shared credential text redactor behavior.

## 2026-07-06 Update: Knowledge Metadata Failure Persistence Redaction

Knowledge document chunk execution failures and metadata backfill document/batch failures now redact credential-shaped exception text before persisting repository failure messages, metadata backfill failure summaries, pause checkpoints, or quarantine snapshots/reasons. This closes the knowledge ingestion and metadata operations management surfaces without removing operator diagnostics.

This is a narrow persistence/display-boundary hardening slice. It does not change pipeline execution inputs, raw exception chains, server logs, document status transitions, retry/checkpoint behavior, quarantine schema, repository interfaces, metadata extraction semantics, or successful ingestion/backfill outputs.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=KernelKnowledgeDocumentServiceTests,KernelMetadataBackfillServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 28/28, covering knowledge chunk failure-message redaction, metadata backfill repository/job/quarantine failure redaction, existing ingestion/backfill behavior, and shared credential text redactor behavior.

## 2026-07-06 Update: Metadata Review Quarantine Failure Redaction

Metadata review index-compensation failures now redact credential-shaped exception text before writing quarantine `reasonMessage` or `sourceSnapshot.errorMessage`. This closes the review-side metadata operations surface that complements the knowledge-document and metadata-backfill failure persistence hardening.

This is a narrow metadata-review quarantine hardening slice. It does not change approve/correct/re-extract decision semantics, canonical metadata writes, index compensation execution, review audit records, quarantine schema, raw exception logging, or the rule that quarantine write failures cannot roll back a completed review decision.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=KernelMetadataReviewServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 11/11, covering metadata review compensation-failure quarantine redaction, existing review decision behavior, and shared credential text redactor behavior.

## 2026-07-06 Update: Document Refresh Failure State Redaction

Scheduled document refresh failures now redact credential-shaped exception text before returning `DocumentRefreshResult` or persisting refresh execution/schedule failure messages. This protects knowledge refresh operations surfaces when upstream fetchers, storage, metadata switching, or chunk execution errors include bearer tokens, API keys, or similar material.

This is a narrow document-refresh failure-state hardening slice. It does not change fetch requests, object storage uploads, document file switching, chunk execution inputs, schedule timing, lock behavior, repository interfaces, raw exception logging, or successful/skipped refresh outcomes.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=KernelDocumentRefreshServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 6/6, covering refresh result/schedule/execution failure-message redaction, existing refresh success/skip behavior, and shared credential text redactor behavior.

## 2026-07-06 Update: Keyword Index Rebuild Failure Redaction

Keyword index rebuild failures now redact credential-shaped exception text before returning `KeywordIndexRebuildResult.failures()`. This protects keyword-index maintenance and knowledge-operations surfaces when search backend failures accidentally include bearer tokens, API keys, or similar values in exception messages.

This is a narrow keyword maintenance result hardening slice. It does not change keyword index delete/index execution order, document/chunk snapshot loading, rebuild counters, observation event names, repository interfaces, raw exception logging, or successful/skip rebuild behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=KernelKeywordIndexMaintenanceServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 6/6, covering keyword rebuild failure-summary redaction, existing document/kb rebuild behavior, and shared credential text redactor behavior.

## 2026-07-06 Update: Memory Outbox Failure Redaction

Memory outbox relay failures now redact credential-shaped exception text before marking outbox tasks failed or writing failed relay-task trace details. This protects memory outbox operations and trace surfaces when vector or custom task handlers accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow memory-outbox relay hardening slice. It does not change outbox polling, handler dispatch precedence, vector upsert/delete execution, retry state semantics, observation counters, raw handler inputs, or successful task tracing.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=MemoryOutboxRelayServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new regression with raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 17/17 across kernel redactor and memory outbox relay coverage.

## 2026-07-06 Update: Memory Operation Failure Redaction

Memory ingestion failures now redact credential-shaped exception text before marking the memory operation log failed. This protects memory operation history and management surfaces when durable writes or downstream memory components accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow memory-operation persistence hardening slice. It does not change ingestion classification, durable memory write inputs, operation start/completion semantics, raw exception propagation, server logging, refiner fail-open/fail-closed decisions, or successful operation decision details.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=DefaultMemoryEnginePortTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the corrected regression because the operation failure reason lacked `[REDACTED]`, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 68/68 across kernel redactor and default memory engine coverage.

## 2026-07-06 Update: Memory Derived Index Failure Redaction

Memory derived-index dispatch failures now redact credential-shaped exception text before writing vector-upsert fallback outbox task `errorMessage` values and warn-log failure summaries. This protects memory outbox backlog and operational log surfaces when vector backends accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow derived-index dispatch hardening slice. It does not change vector upsert/delete inputs, memory record content, outbox task type selection, keyword/graph outbox flags, retry behavior, or successful derived-index operation strings.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=MemoryDerivedIndexDispatchServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new regression because the vector fallback outbox `errorMessage` lacked `[REDACTED]`, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 9/9 across kernel redactor and derived-index dispatch coverage.

## 2026-07-06 Update: Memory Governance Failure Redaction

Memory governance promotion, quality snapshot, inference, conflict detection, and decay failure lists now redact credential-shaped exception text before returning `MemoryGovernanceRunResult.errors()`. This protects memory governance operations surfaces when repository, inference, or maintenance failures accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow governance result-boundary hardening slice. It does not change promotion, semantic upsert, quality report generation, inference, conflict detection, decay logic, raw execution inputs, repository semantics, server logs beyond returned error text, or the quality snapshot schema.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=KernelMemoryGovernanceServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new regression because a promotion failure returned raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 9/9 across kernel redactor and memory governance coverage.

## 2026-07-06 Update: Memory Refiner Failure Redaction

Memory refiner fail-open and fail-closed paths now redact credential-shaped exception text before storing `RefinedMemoryDelta.reason` values that flow into operation decision metadata such as `refinerReason`. This protects memory ingestion operation history and refiner diagnostics when model/provider failures accidentally include bearer tokens, API keys, or similar material.

This is a narrow refiner failure-reason hardening slice. It does not change baseline classification, refiner request construction, raw memory/refiner inputs, fail-open versus fail-closed policy, schema validation, memory write behavior, operation status semantics, or server-side exception logging.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=DefaultMemoryEnginePortTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new fail-open/fail-closed regressions because `refinerReason` lacked `[REDACTED]`, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 70/70 across kernel redactor and default memory engine coverage.

## 2026-07-06 Update: Memory Aggregation Trace Failure Redaction

Memory aggregation flush failures now redact credential-shaped exception text before writing failed `submit` trace `details.message` values. This protects memory aggregation trace and operations surfaces when ingestion workflow failures accidentally include bearer tokens, API keys, or similar material.

This is a narrow aggregation trace-boundary hardening slice. It does not change turn buffering, explicit/idle/topic-shift/force flush policy, ingestion command construction, raw context-block content, failed `MemoryIngestionResult` reason, observation counters, or server-side warn logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=MemoryAggregationServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new regression because failed submit trace `details.message` returned raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 15/15 across kernel redactor and memory aggregation coverage.

## 2026-07-06 Update: Memory Alias Maintenance Failure Redaction

Memory alias maintenance scan and upsert failures now redact credential-shaped exception text before returning `MemoryAliasResolutionRunResult.errors()`. This protects memory maintenance result surfaces and upstream maintenance aggregation when alias repository failures accidentally include bearer tokens, API keys, or similar material.

This is a narrow alias-maintenance result-boundary hardening slice. It does not change alias candidate scanning, scoped/global scan selection, alias normalization, dictionary matching, auto-resolve thresholds, upsert command construction, missing user-scope handling, or raw repository exception logging.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=MemoryAliasResolutionServiceMaintenanceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new scan/upsert regressions because `MemoryAliasResolutionRunResult.errors()` returned raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 7/7 across kernel redactor and memory alias maintenance coverage.

## 2026-07-06 Update: Memory Garbage Collection Failure Redaction

Memory garbage collection scan, archive, physical-delete, outbox enqueue, and mark failures now redact credential-shaped exception text before returning `MemoryGarbageCollectionResult.errors()`. This protects memory maintenance result surfaces and upstream maintenance aggregation when lifecycle repositories or derived-index outbox writes accidentally include bearer tokens, API keys, or similar material.

This is a narrow garbage-collection result-boundary hardening slice. It does not change candidate scanning, lifecycle archive/delete semantics, physical delete enablement, outbox task construction, mark-deleted behavior, dry-run behavior, raw repository/outbox exception logging, or raw candidate/task inputs.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=MemoryGarbageCollectionServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new scan/outbox regressions because `MemoryGarbageCollectionResult.errors()` returned raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 12/12 across kernel redactor and memory garbage collection coverage.

## 2026-07-06 Update: Memory Compaction Failure Redaction

Memory compaction scan, group compaction, and derived-index outbox enqueue failures now redact credential-shaped exception text before returning `MemoryCompactionResult.errors()`. This protects memory maintenance result surfaces when compaction repositories, long-term memory writes, or derived-index outbox writes accidentally include bearer tokens, API keys, or similar material.

This is a narrow compaction result-boundary hardening slice. It does not change candidate scanning, compaction grouping, summary generation, master memory construction, source fragment metadata, mark-compacted behavior, outbox task construction, observation emission, raw compaction inputs, or raw repository/outbox exception logging.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=MemoryCompactionServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new scan/outbox regressions because `MemoryCompactionResult.errors()` returned raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 8/8 across kernel redactor and memory compaction coverage.

## 2026-07-06 Update: Memory Maintenance Aggregation Failure Redaction

Default memory maintenance service-level failures now redact credential-shaped exception text before returning aggregate `MemoryMaintenanceRunResult.errors()` or failed `MemoryMaintenanceTaskOutcome.reason` values. This protects maintenance run, trace, repository, and operations surfaces when compaction, alias resolution, or garbage collection services throw before producing their own redacted result objects.

This is a narrow maintenance aggregation result-boundary hardening slice. It does not change maintenance task selection, skip/not-requested outcome semantics, subservice execution order, result aggregation, run-record persistence, trace/observation schemas, raw subservice inputs, or raw exception logging.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=DefaultMemoryMaintenanceServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new compaction/garbage-collection/alias service-level regressions because aggregate errors returned raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 20/20 across kernel redactor and default memory maintenance coverage.

## 2026-07-06 Update: Memory Review Alias Apply Failure Redaction

Memory review alias apply failures now redact credential-shaped exception text before throwing review approval errors or writing failed `approve` trace `details.reason` values. This protects review operation and trace surfaces when alias store failures accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow review alias-apply hardening slice. It does not change review decision semantics, alias command construction, claim/release behavior, feedback persistence, raw alias command inputs, successful approve tracing, or server-side exception logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=KernelMemoryReviewServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new alias apply regression because the thrown review error returned raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 23/23 across kernel redactor and memory review coverage.

## 2026-07-06 Update: Research Task Failure Redaction

Research step retry and failure messages now redact credential-shaped exception text before writing durable task queue retry/fail reasons or streaming `RECOVERABLE_ERROR` event payload messages. This protects research task operations and subscriber surfaces when model, retrieval, artifact, or provider failures accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow research orchestration failure-boundary hardening slice. It does not change step ordering, retry eligibility, retry backoff, loop detection, handler execution, task ack/enqueue semantics, event sequencing, successful step events, or server-side exception logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=ResearchRunOrchestratorTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new retry/fail regressions because durable queue reasons lacked `[REDACTED]`, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 14/14 across kernel redactor and research orchestration coverage.

## 2026-07-06 Update: RAG Trace Failure Redaction

RAG trace run/node failure messages and retrieval channel failure `extraData.errorMessage` values now redact credential-shaped exception text before persisting trace records. This protects trace repository, trace query, and retrieval diagnostics surfaces when model, search, vector, or post-processing failures accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow trace persistence hardening slice. It does not change trace sampling, run/node lifecycle recording, retrieval fallback behavior, channel timeout policy, trace hit metadata, successful trace payloads, or server-side exception logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=KernelRagTraceRecorderTests,KernelMultiChannelRetrievalEngineTraceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new trace regressions because node/run failure errors and channel `extraData.errorMessage` lacked `[REDACTED]`, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 12/12 across kernel redactor, trace recorder, and retrieval trace coverage.

## 2026-07-06 Update: Ingestion Task Failure Redaction

Ingestion task failures now redact credential-shaped exception text before returning execution result messages, persisting task-level `errorMessage` values, persisting task log summaries, or replacing node log `message`/`errorMessage` values. This protects ingestion task repository, task detail, node diagnostics, and upload/execute response surfaces when parser, embedding, indexing, or pipeline failures accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow ingestion task failure-boundary hardening slice. It does not change pipeline execution, node result semantics, retry/rollback behavior, source metadata, node output payloads, raw engine exceptions, or server-side exception logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=KernelIngestionTaskServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new regression because the execution result returned raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 10/10 across kernel redactor and ingestion task service coverage.

## 2026-07-06 Update: Compensation Retry Failure Redaction

Compensation retry handler failures now redact credential-shaped exception text before updating durable `CompensationLog.lastError` values for pending or permanently failed retries. This protects compensation operations and retry backlog surfaces when repair handlers, downstream repositories, or external systems accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow compensation retry persistence-boundary hardening slice. It does not change distributed locking, retry batching, handler lookup, retry count semantics, pending versus failed status selection, static retry failure reasons, payload handling, or server-side exception logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=CompensationRetryServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new regression because `CompensationLog.lastError` captured raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 4/4 across kernel redactor and compensation retry coverage.

## 2026-07-06 Update: Outbox Relay Failure Redaction

Outbox relay failures now redact credential-shaped exception text before writing durable outbox `lastError` values, metadata quarantine `reasonMessage` values, or quarantine snapshot `error` fields. This protects retry backlog, metadata quarantine, and operations surfaces when MQ adapters or downstream brokers accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow outbox relay persistence-boundary hardening slice. It does not change relay batching, distributed locking, retry delay/status semantics, envelope parsing, message send behavior, quarantine identity extraction, or server-side exception logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-kernel -am "-Dtest=ReliableMessageQueueAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new regression because outbox `lastError` captured raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 5/5 across kernel redactor and reliable message relay coverage.

## 2026-07-06 Update: Metadata Governance Issue Redaction

Metadata extraction and normalization soft-failure diagnostics now redact credential-shaped exception text before adding `MetadataIssue.message` values or failed `MetadataFieldQuality.message` values. This protects ingestion task detail, metadata review/quarantine context, and governance diagnostics surfaces when LLM providers, regex/conversion logic, or source metadata values accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow metadata-governance diagnostics hardening slice. It does not change schema loading, candidate extraction, LLM prompt construction, normalization conversion semantics, accepted metadata, validation decisions, observation counters, or raw candidate values.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=MetadataGovernanceNodeFeatureTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new LLM extraction and normalization regressions because metadata issue/quality messages captured raw `Authorization: Bearer ... api_key=...` text, then passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 19/19 across kernel redactor and metadata governance node coverage.

Regex extraction failure diagnostics are now covered by the same boundary: invalid `ruleRegex`/`pathRegex` patterns can include raw pattern text in `PatternSyntaxException` messages, so `REGEX_FAILED` issue messages now pass through the shared credential redactor before entering metadata governance surfaces.

## 2026-07-06 Update: Agent Loop Streaming Error Redaction

Agent loop model-turn stream error events now redact credential-shaped exception text before emitting `RECOVERABLE_ERROR` or failed `STEP_FINISHED` payload messages. This protects frontend, SSE subscribers, and live run diagnostics when model adapters or streaming callbacks accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow streaming-event hardening slice. It does not change model retry semantics, step status transitions, tool observation handling, run persistence, trace recording, callback error propagation, or server-side exception logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentLoopToolGatewayTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` first failed on the new model-stream regression because the test initially inspected the optional summary field instead of the event message field, then passed with `BUILD SUCCESS`; targeted test classes ran 29/29 across kernel redactor and agent loop Tool Gateway streaming coverage.

## 2026-07-06 Update: Agent Run Serialization Fallback Redaction

Agent run runtime JSON serialization fallback messages now redact credential-shaped exception text before persisting `serializationError` payloads for recorded run steps, approval-wait checkpoints, or resumed run step payloads. This protects run detail, checkpoint, resume, and approval diagnostics surfaces when JSON serializers fail with exception messages that accidentally include bearer tokens, API keys, or similar material.

This is a narrow runtime persistence hardening slice. It does not change normal JSON payload shape, checkpoint sequencing, approval wait transitions, resume execution semantics, step status handling, or server-side exception logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=RepositoryAgentRunStepRecorderTests,RepositoryAgentApprovalWaitHandlerTests,KernelAgentRunResumeServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS`; targeted test classes ran 13/13 across kernel redactor, run step recorder serialization fallback, approval wait checkpoint serialization fallback, and existing resume behavior coverage.

## 2026-07-06 Update: Feature Health Failure Redaction

Feature health aggregation now redacts credential-shaped exception text before returning failed `FeatureHealth.message` values. This protects diagnostics, readiness-adjacent, and admin health surfaces when feature health probes accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow health diagnostics hardening slice. It does not change adapter health aggregation, feature health status semantics, health detail payloads, startup checks, or server-side exception logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=FeatureHealthAggregatorTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 6/6 across kernel redactor and feature health aggregation coverage.

## 2026-07-06 Update: Web Fetch Failure Reason Redaction

JDK HTTP web fetch runtime failure reasons now redact credential-shaped exception text before returning `WebFetchResult.reasonCode` values. This protects `web_fetch` observations, research diagnostics, and downstream run records when URI parsing, HTTP client adapters, proxy setup, or other runtime failures accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow fetch adapter failure-boundary hardening slice. It does not change SSRF safety decisions, DNS private-network blocking, HTTP status handling, MIME filtering, content normalization, truncation behavior, checked exception fallback codes, or server-side exception logging.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-kernel -am "-Dtest=JdkHttpAdaptersTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 7/7 across kernel redactor and JDK HTTP adapter coverage.

## 2026-07-06 Update: Resilient Model Failure Redaction

Resilient chat model wrapping now redacts credential-shaped exception text before creating `ExternalServiceException` messages for failed non-streaming model calls. This protects agent loop failures, streaming error derivation, retry diagnostics, and API error surfaces when provider SDKs or HTTP clients accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow model adapter failure-boundary hardening slice. It does not change retry, timeout, circuit breaker fallback, delegate call behavior, existing `ExternalServiceException` propagation, or server-side exception logging with the original exception chain.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-kernel -am "-Dtest=SeahorseAgentPhase1AutoConfigurationTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 7/7 across kernel redactor and resilience auto-configuration/model adapter coverage.

## 2026-07-06 Update: Eval Regression Failure Redaction

Eval regression replay failures now redact credential-shaped exception text before returning `EvalResult.error` values. This protects evaluation reports, gate evidence, and regression diagnostics when model providers or test harness adapters accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow eval replay failure-boundary hardening slice. It does not change sample loading, model invocation, citation scoring, semantic-overlap heuristics, report aggregation, baseline comparison, or successful sample outputs.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=KernelEvalRegressionServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 5/5 across kernel redactor and eval regression coverage.

## 2026-07-06 Update: Readiness Failure Redaction

Readiness probe failures now redact credential-shaped exception text before returning MQ probe, migration-check, or default-admin component messages. This protects readiness APIs, status pages, and operator diagnostics when MQ brokers, JDBC drivers, or infrastructure checks accidentally include bearer tokens, API keys, or similar material in exception messages.

This is a narrow readiness diagnostics hardening slice. It does not change component availability decisions, Pulsar probe send behavior, probe caching, adapter type reporting, migration table checks, default-admin SQL checks, or product-mode severity mapping.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-spring-boot-autoconfigure,seahorse-agent-kernel -am "-Dtest=SeahorseAgentAdapterCanonicalPropertyAutoConfigurationTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran 16/16 across kernel redactor and Spring readiness/canonical property coverage.

## 2026-07-06 Update: Date-Time Tool Failure Redaction

The built-in `get_current_datetime` tool now redacts credential-shaped exception text before returning failed `ToolInvocationResult.error` values. This protects tool observations, model-visible tool errors, and run diagnostics if time-zone resolution or runtime dependencies ever surface bearer tokens, API keys, cookies, or similar material in exception messages.

This is a narrow built-in tool failure-boundary hardening slice. It does not change the normal date/time response shape, tool descriptor, default `Asia/Shanghai` zone, tool registration, or agent loop execution semantics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=GetDateTimeToolPortAdapterTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes ran across kernel redactor and date-time tool success/failure coverage.

## 2026-07-06 Update: Output Governance Failure Redaction

Output governance validators now redact credential-shaped exception text before returning validation issues for JSON parse failures, invalid configured JSON schemas, invalid Markdown heading schemas, validator `supports` failures, or validator runtime failures. This protects validation records, output-governance diagnostics, gate evidence, and operator-facing validation issue surfaces when malformed content, schemas, or validator adapters accidentally include bearer tokens, API keys, cookies, or similar material in exception messages.

This is a narrow output-governance failure-boundary hardening slice. It does not change validator selection, PASS/BLOCK/WARN decisions, self-heal retry semantics, normalized content, observation event names, or the block fallback message.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=JsonSchemaOutputValidatorTests,MarkdownAndMermaidValidatorTests,OutputGovernanceServiceTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes covered kernel redactor plus JSON, Markdown, and output-governance validator failure boundaries.

## 2026-07-06 Update: Research Report Streaming Failure Redaction

Research report streaming failures now redact credential-shaped exception text before wrapping model callback errors in `RetryableResearchException` messages. This protects research retry reasons, task failure surfaces, streaming diagnostics, and downstream orchestration events when streaming model providers accidentally include bearer tokens, API keys, cookies, or similar material in exception messages.

This is a narrow write-report streaming failure-boundary hardening slice. It does not change report prompt construction, streaming lifecycle events, artifact persistence, retry classification, timeout handling, cancellation behavior, or the original exception cause retained for server-side diagnostics.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=WriteReportStepHandlerStreamingTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes covered kernel redactor plus write-report streaming success, blocking compatibility, and streaming failure redaction.

## 2026-07-06 Update: Agent Tool Future Failure Redaction

Agent loop tool-thread failures now redact credential-shaped exception text before converting `ExecutionException` causes into failed tool observations. This protects model-visible tool messages, run steps, and downstream stream/tool diagnostics if a tool execution thread fails outside the normal Tool Gateway result/exception handling path.

This is a narrow executor-boundary hardening slice. It does not change Tool Gateway invocation, successful observation content, timeout/interruption messages, trace recorder fail-open behavior, tool policy decisions, approval handling, or server-side exception causes.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelAgentLoopToolGatewayTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS`; targeted test classes ran 30/30 across kernel redactor and agent loop tool gateway/future-failure coverage.

## 2026-07-06 Update: Extension Loader Diagnostic Redaction

Extension loader failure diagnostics now redact credential-shaped text before retaining `ExtensionLoadDiagnostic.message` values. This protects plugin/extension startup diagnostics and downstream operations surfaces when descriptor values or reflection failures accidentally include bearer tokens, API keys, cookies, or similar material.

This is a narrow extension-loading diagnostics hardening slice. It does not change extension discovery, descriptor parsing semantics, activation ordering, managed-by-container handling, registry registration, thrown exception types, or server-side exception causes.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-kernel -am "-Dtest=ExtensionLoaderTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes covered kernel redactor plus successful classpath extension loading and failure diagnostic redaction.

## 2026-07-06 Update: Plugin Status Diagnostic Redaction

Plugin status management requests now redact credential-shaped diagnostic text before saving or returning `AgentExtensionStatus.message`, `lastError`, or nested `details` string values. This protects plugin operations APIs and persisted status records when manual status updates or adapter diagnostics accidentally include bearer tokens, API keys, cookies, refresh tokens, or similar material.

This is a narrow Web adapter boundary hardening slice. It does not change plugin health reporting, registry listing, status identity fields, capability sets, enabled/healthy semantics, repository persistence shape, or non-string diagnostic detail values.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-tests,seahorse-agent-adapter-web,seahorse-agent-kernel -am "-Dtest=SeahorseWebApiContractTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with the full reactor `BUILD SUCCESS`; targeted test classes covered kernel redactor plus plugin status POST response/persistence redaction and existing Web API contracts.

## 2026-07-06 Update: AI Model Config API Error Redaction

AI model config admin API failures now redact credential-shaped exception text before returning JSON `message` values from list, get, gate-result, update, create, or delete operations. This protects model configuration administration screens and API clients when repository or provider failures accidentally include bearer tokens, API keys, cookies, or similar material.

This is a narrow Web adapter error-response hardening slice. It does not change login checks, repository calls, successful response payloads, encrypted config masking, gate-result construction, tenant normalization, or server-side exception handling.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-web,seahorse-agent-kernel -am "-Dtest=AiModelConfigControllerTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS`; targeted test classes covered kernel redactor plus AI model config gate-result behavior and API failure-message redaction.

## 2026-07-06 Update: Web Error Response Redaction

Global Web adapter error responses now redact credential-shaped text before returning client-visible `ErrorResponse.message`, decoded `ErrorResponse.path`, and forbidden response `message` values. This protects frontend, API clients, negative-path diagnostics, and access-denied surfaces when validation, response-status, external-service, or authorization exceptions accidentally include bearer tokens, API keys, cookies, or secret-bearing request paths.

This is a narrow Web adapter boundary hardening slice. It does not change HTTP status selection, structured error codes, request-id propagation, tenant context, details payloads, server-side exception logging, or access-denied resource/action metadata.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-web,seahorse-agent-kernel -am "-Dtest=SeahorseWebExceptionHandlerTests,ForbiddenExceptionMapperTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS`; `docker compose -f docker-compose.full.yml build --build-arg HTTP_PROXY= --build-arg HTTPS_PROXY= backend` rebuilt the backend image with the in-image Maven reactor passing; a real HTTP negative-path request against `http://127.0.0.1:9090/knowledge-base/token%3Dsk-live-secret` returned `400` with `[REDACTED]` in both `message` and `path` and no raw secret; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-06 Update: SSE Error Event Redaction

Spring SSE stream failures now redact credential-shaped exception text before sending the client-visible `error` event payload. This protects browser subscribers, chat stream UI, and stream diagnostics when an upstream stream failure contains bearer tokens, API keys, cookies, or secret-bearing provider messages.

This is a narrow Web streaming boundary hardening slice. It does not change heartbeat scheduling, normal event payloads, `[DONE]` completion events, client-disconnect handling, emitter completion behavior, or server-side warning logs with the original exception chain.

Fresh evidence: the new regression first failed because `SpringSseEventSender.fail` emitted raw `Authorization: Bearer ... token=...` text in the SSE `error` event; after the fix, `.\mvnw.cmd -pl seahorse-agent-adapter-web,seahorse-agent-kernel -am "-Dtest=SpringSseEventSenderTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS`; `docker compose -f docker-compose.full.yml build --build-arg HTTP_PROXY= --build-arg HTTPS_PROXY= backend` rebuilt the backend image with the in-image Maven reactor passing; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks including the real RAG SSE chat smoke and trace follow-up.

## 2026-07-06 Update: Controller SSE Error Payload Redaction

Controller-owned SSE stream failures now redact credential-shaped exception text before sending client-visible `error` event payloads from chat and workflow visualization endpoints. This closes the remaining direct controller emitters that bypass `SpringSseEventSender`, protecting browser subscribers and stream clients if controller-level failures include bearer tokens, API keys, cookies, or provider messages with embedded secrets.

This is a narrow Web streaming boundary hardening slice. It does not change normal SSE payloads, event ordering, `[DONE]` completion events, workflow subscriber lifecycle, visualization availability gating, resume behavior, or server-side diagnostics with the original exception chain.

Fresh evidence: the new focused regressions first failed because `SeahorseChatController.emitSseError` and the workflow visualization stream failure path emitted raw `Authorization: Bearer ... token=...` text in SSE `error` payloads; after the fix, `.\mvnw.cmd -pl seahorse-agent-adapter-web,seahorse-agent-kernel -am "-Dtest=SeahorseChatControllerTests,SeahorseWorkflowVisualizationControllerTests,CredentialTextRedactorTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS`; `docker compose -f docker-compose.full.yml build --build-arg HTTP_PROXY= --build-arg HTTPS_PROXY= backend` rebuilt the backend image with in-image Maven `BUILD SUCCESS` and exported image `sha256:78a96c3220c0f9e05a0086deadc9d6872924c2c7ff9bd46f47a3112dcf3ff385`; the rebuilt `seahorse-backend` container reached `healthy`; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks against the real full-compose environment, including Milvus/Redis/Pulsar readiness, auth, knowledge CRUD/upload/chunk, real RAG SSE chat, RAG trace retrieval evidence, memory/profile extraction, catalogs, audit, metadata governance, and SRE health.

## 2026-07-06 Update: Sandbox XLSX to CSV Conversion

The governed `sandbox_file_convert` tool now supports conservative base64 `xlsx -> csv` conversion. The scope is intentionally narrow: first worksheet only (`xl/worksheets/sheet1.xml`), optional shared strings, Python stdlib ZIP/XML/CSV parsing inside the file-conversion sandbox, no LibreOffice/Tika/external conversion engine, no formula evaluation, no rendering/editing, and no network access.

The sandbox adapter now applies the same bounded archive posture used by existing Office conversion paths: archive entry-count budget, unsafe path rejection, macro/ActiveX/embedded/external-link/OLE entry rejection, required first worksheet validation, and output-only artifact collection as `converted.csv`.

The real full-compose smoke was also tightened to exercise the current governance chain instead of bypassing it: it creates a real AgentRun through the kernel run profile chat path, invokes the HIGH-risk tool through Tool Gateway, approves each required tool execution through the approval API, retries with the same run/step identity, verifies Postgres sandbox session/artifact rows, downloads each artifact through the governed endpoint, and checks the backend storage volume object.

Fresh evidence: focused RED/GREEN coverage first failed on the new XLSX descriptor/runtime tests, then `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across kernel 15/15 and sandbox-container 58/58 tests; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet` passed with the sandbox workspace mount configured; the backend image had already been rebuilt with the in-image Maven reactor passing and the `seahorse-backend` container healthy; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-xlsx-convert-smoke` passed 28/28 checks against the real full-Docker environment, including CSV/JSON, JSON/CSV, Markdown/HTML, DOCX/TXT, PDF/TXT, and XLSX/CSV conversions through approval, Tool Gateway, Postgres, governed downloads, and backend storage; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-06 Update: Sandbox PPTX to TXT Conversion

The governed `sandbox_file_convert` tool now supports conservative base64 `pptx -> txt` conversion. The scope is intentionally narrow: slide text only from `ppt/slides/slide*.xml`, ordered by slide number, using Python stdlib ZIP/XML parsing inside the file-conversion sandbox. It does not render slides, evaluate animations, extract image text, inspect notes, edit presentations, call LibreOffice/Tika, or use network access.

The sandbox adapter applies bounded archive handling before the container runs: archive entry-count budget, unsafe path rejection, macro/VBA, ActiveX, embedded/OLE object, and external-link entry rejection, and required slide XML validation. Outputs are collected only as `converted.txt`; generated scripts and `input.pptx` remain internal workspace files and are not returned as artifacts.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across kernel 16/16 and sandbox-container 60/60 tests; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml config --quiet` passed with the sandbox workspace mount configured; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules, then the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-pptx-convert-smoke` passed 32/32 checks against the real full-Docker environment, including CSV/JSON, JSON/CSV, Markdown/HTML, DOCX/TXT, PDF/TXT, XLSX/CSV, and PPTX/TXT conversions through approval, Tool Gateway, Postgres, governed downloads, and backend storage; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Run Experiment Output Diff Summary

Run Experiment Markdown reports now make `Diff vs first trial` more actionable by adding character and line counts for the baseline and current trial output, plus signed deltas. The existing product wording remains intact: reports still say `same as first trial`, `differs from first trial`, or `output not available` before the enriched metrics.

This is a narrow report productization slice. It does not add a new report format, change the Run Experiment API shape, alter experiment persistence/schema, introduce report history, change frontend behavior, or replace future richer semantic diff work.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across 12/12 kernel tests; PowerShell parsing for `.\scripts\e2e-run-experiment-smoke.ps1` passed; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123` passed 12/12 checks against the real full-Docker environment, including exported report assertions for `chars baseline=`, `lines baseline=`, and `delta=`; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Run Experiment Score Leaderboard

Run Experiment Markdown reports now add a `Score Leaderboard` directly under the executive summary. The leaderboard ranks scored trials by parsed `rating`, `score`, `totalScore`, or `overallScore`, and includes trial id, run profile, score, status, and a compact human-readable score evidence field such as `verdict=smoke-pass`.

This is a narrow report productization slice. It does not change the Run Experiment API shape, report template version, score persistence schema, scoring semantics, frontend behavior, branch forking, trace/cost evidence collection, or future richer evaluation workflows.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across 12/12 kernel tests; PowerShell parsing for `.\scripts\e2e-run-experiment-smoke.ps1` passed; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123` passed 12/12 checks against the real full-Docker environment, including exported report assertions for `Score Leaderboard`, `Score Evidence`, and `verdict=smoke-pass`; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Run Experiment Trial Action Checklist

Run Experiment Markdown reports now add a `Trial Action Checklist` after the evidence completeness summary. The checklist repeats the recommended trial, states whether release-review evidence is complete, counts failed trials, and gives each trial a deterministic next action such as `fork recommended trial`, `score trial`, or `inspect failure reason: ...`.

This is a narrow report productization slice. It does not change the Run Experiment API shape, report template version, trial execution semantics, scoring schema, trace/cost evidence collection, branch forking behavior, repository schema, or frontend behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-run-experiment-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across 12/12 kernel tests; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123` passed 12/12 checks against the real full-Docker environment, including normal report assertions for `Trial Action Checklist`, `fork recommended trial`, and `score trial`, plus missing-leaf failure report assertions for `inspect failure reason: base leaf message not found`; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Run Experiment Cost Summary

Run Experiment Markdown reports now add a report-level `Cost Summary` before the detailed evidence tables. The summary aggregates resolved `sa_cost_usage_record` data across trial runs and shows costed trial count, total cost, total tokens, total calls, and cost record count, while leaving per-trial cost evidence in the existing tables.

This is a narrow report productization slice. It does not change the Run Experiment API shape, report template version, cost usage schema, billing semantics, trial execution, scoring, branch forking, trace evidence, or frontend behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across 12/12 kernel tests; PowerShell parsing for `.\scripts\e2e-run-experiment-smoke.ps1` passed; an initial 5-minute bootstrap package command timed out while Maven was still running, so the stale Maven process was inspected and cleared, then `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` completed with the full bootstrap reactor passing across 28/28 modules and produced a new exec jar timestamped `2026-07-07 00:46:48`; the rebuilt jar was copied into the real `seahorse-backend` container, `/app/app.jar` size matched the new local jar, and the container returned to `healthy`; `.\scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123` passed 12/12 checks against the real full-Docker environment, including exported report assertions for `Cost Summary`, `Costed trials:`, `Total cost:`, `Total tokens:`, and `Cost records:`; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Run Experiment Trace Summary

Run Experiment Markdown reports now add a report-level `Trace Summary` before the detailed evidence tables. The summary counts traced trials and lists each resolved trial trace with trial id, run id, and the existing Studio trace or trace id evidence, making trace follow-up visible near the top of the exported report while leaving per-trial trace evidence in the existing tables.

This is a narrow report productization slice. It does not change the Run Experiment API shape, report template version, trace context schema, AgentScope/Studio integration, trial execution, scoring, cost evidence, branch forking, or frontend behavior.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across 12/12 kernel tests; PowerShell parsing for `.\scripts\e2e-run-experiment-smoke.ps1` passed; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules and produced a new exec jar timestamped `2026-07-07 01:16:48`; the rebuilt jar was copied into the real `seahorse-backend` container, `/app/app.jar` size matched the new local jar, and the container returned to `healthy`; `.\scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123` passed 12/12 checks against the real full-Docker environment, including exported report assertions for `Trace Summary` and `Traced trials:`; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox DOCX to HTML Conversion

The governed `sandbox_file_convert` tool now supports conservative base64 `docx -> html` conversion. The scope is intentionally narrow: paragraph text from `word/document.xml` is rendered as escaped `<p>` elements using Python stdlib ZIP/XML/HTML handling inside the file-conversion sandbox. It does not render full Word layout, styles, images, headers/footers, comments, tracked changes, editing operations, LibreOffice/Tika conversion, or network access.

The implementation reuses the existing DOCX archive guardrails before any container execution: archive entry-count budget, unsafe path rejection, macro/VBA, ActiveX, embedded/OLE object, and external-link entry rejection, and required `word/document.xml` validation. Outputs are collected only as `converted.html`; generated scripts and `input.docx` remain internal workspace files and are not returned as artifacts.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across kernel 17/17 and sandbox-container 61/61 tests; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-docx-html-smoke` passed 36/36 checks against the real full-Docker environment, including CSV/JSON, JSON/CSV, Markdown/HTML, DOCX/TXT, DOCX/HTML, PDF/TXT, XLSX/CSV, and PPTX/TXT conversions through approval, Tool Gateway, Postgres, governed downloads, and backend storage; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox PDF to HTML Conversion

The governed `sandbox_file_convert` tool now supports conservative base64 `pdf -> html` conversion. The scope is intentionally narrow: it reuses the existing literal-text PDF extraction path for unencrypted PDFs, escapes each extracted text line, and renders it as simple `<p>` elements inside `converted.html`. It does not add full PDF layout rendering, page images, OCR, forms/annotations extraction, password handling, LibreOffice/Tika conversion, or network access.

The implementation keeps the existing PDF guardrails before and during sandbox execution: PDF header validation, active-content scanner coverage for persisted artifacts, encrypted-PDF fail-closed behavior, bounded FlateDecode decompression, no-network `FILE_CONVERSION` runtime, and output-only artifact collection. Generated scripts and `input.pdf` remain internal workspace files and are not returned as artifacts.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across kernel 18/18 and sandbox-container 62/62 tests; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-pdf-html-smoke` passed 40/40 checks against the real full-Docker environment, including CSV/JSON, JSON/CSV, Markdown/HTML, DOCX/TXT, DOCX/HTML, PDF/TXT, PDF/HTML, XLSX/CSV, and PPTX/TXT conversions through approval, Tool Gateway, Postgres, governed downloads, and backend storage; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox XLSX to HTML Conversion

The governed `sandbox_file_convert` tool now supports conservative base64 `xlsx -> html` conversion. The scope is intentionally narrow: the first worksheet is rendered as an escaped HTML `<table>` preview using the existing Python stdlib ZIP/XML parsing path inside the file-conversion sandbox. It does not evaluate formulas, render spreadsheet styles, charts, images, comments, hidden-sheet metadata, multi-sheet workbooks, LibreOffice/Tika conversion, or network access.

The implementation reuses the XLSX archive guardrails added for CSV conversion before any container execution: archive entry-count budget, unsafe path rejection, macro/VBA, ActiveX, embedded/OLE object, external-link entry rejection, required first worksheet validation, and output-only artifact collection. Outputs are collected only as `converted.html`; generated scripts and `input.xlsx` remain internal workspace files and are not returned as artifacts.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across kernel 19/19 and sandbox-container 63/63 tests; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-xlsx-html-smoke` passed 44/44 checks against the real full-Docker environment, including CSV/JSON, JSON/CSV, Markdown/HTML, DOCX/TXT, DOCX/HTML, PDF/TXT, PDF/HTML, XLSX/CSV, XLSX/HTML, and PPTX/TXT conversions through approval, Tool Gateway, Postgres, governed downloads, and backend storage; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox PPTX to HTML Conversion

The governed `sandbox_file_convert` tool now supports conservative base64 `pptx -> html` conversion. The scope is intentionally narrow: slide text from `ppt/slides/slide*.xml` is ordered by slide number, escaped, and rendered as simple `<p>` elements using Python stdlib ZIP/XML/HTML handling inside the file-conversion sandbox. It does not render slide layout, styles, speaker notes, comments, images, animations, charts, OCR, editing operations, LibreOffice/Tika conversion, or network access.

The implementation reuses the existing PPTX archive guardrails before any container execution: archive entry-count budget, unsafe path rejection, macro/VBA, ActiveX, embedded/OLE object, external-link entry rejection, required slide XML validation, and output-only artifact collection. Outputs are collected only as `converted.html`; generated scripts and `input.pptx` remain internal workspace files and are not returned as artifacts.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across kernel 20/20 and sandbox-container 64/64 tests; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-pptx-html-smoke` passed 48/48 checks against the real full-Docker environment, including CSV/JSON, JSON/CSV, Markdown/HTML, DOCX/TXT, DOCX/HTML, PDF/TXT, PDF/HTML, XLSX/CSV, XLSX/HTML, PPTX/TXT, and PPTX/HTML conversions through approval, Tool Gateway, Postgres, governed downloads, and backend storage; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Run Experiment Failure Reason Summary

Run Experiment Markdown reports now add a `Failure Reason Summary` before the detailed failures section. The summary groups failed trials by the existing rendered failure explanation, shows count and trial ids for each reason, and reports `No failure reasons recorded` when the run has no failed trials.

This is a narrow report productization slice. It does not change the Run Experiment API shape, report template version, trial execution semantics, failure persistence schema, scoring, cost evidence, trace evidence, branch forking, or frontend behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-run-experiment-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across 12/12 kernel tests; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123` passed 12/12 checks against the real full-Docker environment, including normal report assertions for `Failure Reason Summary` and `No failure reasons recorded`, plus missing-leaf failure report assertions for `base leaf message not found | 1`; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Run Experiment Evidence Completeness Summary

Run Experiment Markdown reports now add an `Evidence Completeness Summary` after the executive summary. The summary shows resolved versus total coverage for output messages, scores, trace evidence, cost evidence, message branches, and failure reasons, so operators can quickly see whether a comparison report is ready for release review or still missing evidence.

This is a narrow report productization slice. It does not change the Run Experiment API shape, report template version, trial execution semantics, scoring, cost usage aggregation semantics, trace evidence extraction, branch forking, repository schema, or frontend behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-run-experiment-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunExperimentServiceTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across 12/12 kernel tests after aligning cost-evidence completeness with the existing `sa_cost_usage_record` summary semantics; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-run-experiment-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123` passed 12/12 checks against the real full-Docker environment, including normal report assertions for `Evidence Completeness Summary`, `Output messages | 2 | 2`, `Scores | 1 | 2`, `Trace evidence | 2 | 2`, `Cost evidence | 2 | 2`, `Message branches | 2 | 2`, and `Failure reasons | 0 | 0`, plus missing-leaf failure report assertions for `Output messages | 0 | 1` and `Failure reasons | 1 | 1`; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox ODT to TXT/HTML Conversion

The governed `sandbox_file_convert` tool now supports conservative base64 `odt -> txt` and `odt -> html` conversion. The scope is intentionally narrow: text from `content.xml` ODF paragraphs is extracted with Python stdlib ZIP/XML handling inside the no-network `FILE_CONVERSION` sandbox, and HTML output renders escaped paragraph text as simple `<p>` elements.

The implementation adds ODT-specific archive guardrails before container execution: archive entry-count budget, unsafe path rejection, script/basic/object replacement entry rejection, required `content.xml` validation, and output-only artifact collection. It does not render ODT layout or styles, extract images, parse macros, edit documents, call LibreOffice/Tika, perform OCR, or add a general binary conversion engine.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across kernel 23/23 and sandbox-container 67/67 tests; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-odt-convert-smoke` passed 56/56 checks against the real full-Docker environment, including CSV/JSON, JSON/CSV, Markdown/HTML, DOCX/TXT, DOCX/HTML, ODT/TXT, ODT/HTML, PDF/TXT, PDF/HTML, XLSX/CSV, XLSX/HTML, PPTX/TXT, and PPTX/HTML conversions through approval, Tool Gateway, Postgres, governed downloads, and backend storage; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox ODS to CSV/HTML Conversion

The governed `sandbox_file_convert` tool now supports conservative base64 `ods -> csv` and `ods -> html` conversion. The scope is intentionally narrow: the first ODF spreadsheet table in `content.xml` is extracted with Python stdlib ZIP/XML/CSV/HTML handling inside the no-network `FILE_CONVERSION` sandbox, CSV output preserves table rows, and HTML output renders escaped cells as a simple `<table>`.

The implementation applies ODS-specific archive guardrails before container execution: archive entry-count budget, unsafe path rejection, script/basic/object replacement entry rejection, required `content.xml` validation, bounded repeated row/column expansion, and output-only artifact collection. It does not evaluate formulas, render spreadsheet styles, charts, hidden sheets, images, comments, multi-table workbooks, edit documents, call LibreOffice/Tika, or add a general binary conversion engine.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across kernel 26/26 and sandbox-container 70/70 tests; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-ods-convert-smoke` passed 64/64 checks against the real full-Docker environment, including CSV/JSON, JSON/CSV, Markdown/HTML, DOCX/TXT, DOCX/HTML, ODT/TXT, ODT/HTML, ODS/CSV, ODS/HTML, PDF/TXT, PDF/HTML, XLSX/CSV, XLSX/HTML, PPTX/TXT, and PPTX/HTML conversions through approval, Tool Gateway, Postgres, governed downloads, and backend storage; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox ODP to TXT/HTML Conversion

The governed `sandbox_file_convert` tool now supports conservative base64 `odp -> txt` and `odp -> html` conversion. The scope is intentionally narrow: slide paragraph text from ODF `content.xml` is extracted with Python stdlib ZIP/XML handling inside the no-network `FILE_CONVERSION` sandbox, TXT output preserves slide text lines, and HTML output renders escaped slide text as simple `<p>` elements.

The implementation applies ODP-specific archive guardrails before container execution: archive entry-count budget, unsafe path rejection, script/basic/object replacement entry rejection, required `content.xml` validation, required `office:presentation` body validation, active-content fail-closed handling, and output-only artifact collection. It does not render slide layout or styles, extract images, parse speaker notes, evaluate animations, edit presentations, call LibreOffice/Tika, perform OCR, or add a general binary conversion engine.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-sandbox-container -am "-Dtest=SandboxFileConvertToolPortAdapterTests,ContainerSandboxRuntimeAdapterTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across kernel 29/29 and sandbox-container 74/74 tests; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-odp-convert-smoke` passed 72/72 checks against the real full-Docker environment, including CSV/JSON, JSON/CSV, Markdown/HTML, DOCX/TXT, DOCX/HTML, ODT/TXT, ODT/HTML, ODP/TXT, ODP/HTML, ODS/CSV, ODS/HTML, PDF/TXT, PDF/HTML, XLSX/CSV, XLSX/HTML, PPTX/TXT, and PPTX/HTML conversions through approval, Tool Gateway, Postgres, governed downloads, and backend storage; after one transient curl connection reset on the first backend smoke attempt, `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://localhost:9090 -RuntimeProfile full-compose` was rerun and passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: MCP stdio Audit Summary E2E

The MCP stdio smoke now verifies the real Tool Gateway audit summary for the governed diagnostic `echo` call. After the high-risk MCP diagnostic path enters approval, receives approval, and executes against a real stdio MCP server in Docker, the smoke queries `/api/tool-invocations` and asserts that `argumentsSummary` contains only value-free argument shape fields for the `text` argument.

This is a narrow evidence-hardening slice for the existing MCP stdio governance path. It does not change MCP runtime behavior, policy decisions, catalog registration, approval semantics, stdio runner isolation, stderr redaction, or the generic Tool Gateway audit summarizer.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-mcp-stdio-smoke.ps1` passed; `.\scripts\e2e-mcp-stdio-smoke.ps1 -BaseUrl http://127.0.0.1:9093 -Password admin123` passed 11/11 checks against temporary Docker backend/MCP containers on the full-compose network, including allowlist failure diagnostics, approval-required diagnostic execution, approved stdio echo execution, MCP catalog HIGH/requires-approval assertions, preflight approval, refresh/restart, stderr redaction, and the real `/api/tool-invocations` audit assertion for `{"toolId":"echo","argumentKeys":["text"],"argumentCount":1,"argumentValueCount":1,"argumentValueTotalLength":25,"argumentValueMaxLength":25}` without raw echo text, stdio response text, secret marker, or parent-only environment marker leakage.

## 2026-07-07 Update: Remote A2A Tool Catalog Governance Repair

Built-in Tool Gateway catalog sync now refreshes persisted metadata even when the tool already exists in the in-memory registry, so built-in descriptor changes are not skipped during startup. Existing Docker volumes also receive an idempotent startup repair for the `invoke_remote_a2a_agent` catalog row, correcting stale low-risk/read metadata to the governed remote-agent policy: `BUILTIN`, `HIGH`, `EXECUTE`, `REMOTE_AGENT`, owner `kernel-agent`, and `requires_approval=true`. The repair preserves the existing `enabled` flag so operator disablement is not overwritten.

This is a governance metadata repair for the A2A Tool Gateway surface. It does not enable AgentScope A2A in the default full-compose backend, does not change remote execution behavior, and does not replace the separate AgentScope production-hardening work for live A2A execution.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-repository-jdbc,seahorse-agent-spring-boot-autoconfigure -am "-Dtest=JdbcTenantSchemaUpgradeTests,BuiltInAgentToolRegistrarTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS`, including the stale existing-volume catalog repair regression and duplicate-registry catalog refresh regression; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; a real Postgres query in `seahorse-postgres` returned `invoke_remote_a2a_agent|BUILTIN|HIGH|EXECUTE|REMOTE_AGENT|t|t|kernel-agent`; authenticated `GET http://127.0.0.1:9090/api/tools?current=1&size=20&keyword=invoke_remote_a2a_agent` returned code `0` with `riskLevel=HIGH`, `actionType=EXECUTE`, `resourceType=REMOTE_AGENT`, and `requiresApproval=true`; `SEAHORSE_AGENTSCOPE_A2A_ENABLED=false` was confirmed for the current full-compose backend, so preflight remains non-executable rather than bypassing policy; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Unified GateResult Real API Smoke

The unified GateResult route now has a real full-Docker API smoke that exercises the shared projection across existing production objects instead of relying only on controller/projection unit tests. `scripts/e2e-gate-result-smoke.ps1` logs in against the local backend, selects real Tool, Skill, Run Profile, and Agent records, generates a real Agent production gate report, creates a real Ingestion Pipeline, temporary Model Config, and real RAG Strategy comparison, then verifies each `GateResult` response has subject identity, status, pass flag, checked time, source type/id, and expected evidence item codes.

The smoke exposed that passing kernel Run Profiles could produce an empty GateResult item list. `KernelRunProfileService.productionGateCheck` now always emits baseline evidence items for risk assessment, supported executor engine, and high-risk approval governance, while preserving existing blocking behavior for ungoverned high-risk tools and AgentScope production checks.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-gate-result-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-kernel -am "-Dtest=KernelRunProfileServiceTests,GateResultsTests" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed with `BUILD SUCCESS` across Run Profile service 13/13 and GateResult projection 13/13 tests; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the backend exec jar with the full bootstrap reactor passing across 28/28 modules; the rebuilt jar was copied into the real `seahorse-backend` container and the container returned to `healthy`; `.\scripts\e2e-gate-result-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123` passed 19/19 checks against the real full-Docker backend, covering `TOOL`, `SKILL`, `RUN_PROFILE`, `AGENT`, `INGESTION_PIPELINE`, `MODEL_CONFIG`, and `RAG_STRATEGY` GateResult APIs plus temporary object cleanup. The RAG Strategy leg creates a real knowledge base, uploads a document, chunks it, creates a retrieval evaluation dataset, runs a real strategy comparison, then verifies the comparison GateResult for `RAG_BASELINE_PRESENT`, `RAG_WINNER_PRESENT`, `RAG_EVALUABLE_CASES_PRESENT`, `RAG_RECALL_NOT_REGRESSED`, and `RAG_PRECISION_NOT_REGRESSED`; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Unified GateResult Real UI E2E

The unified GateResult UX now has a real full-Docker browser smoke instead of only service/unit coverage. `scripts/e2e-gate-result-ui-smoke.mjs` logs into the Docker frontend at `http://127.0.0.1`, prepares real subjects through the Docker backend at `http://127.0.0.1:9090`, then opens the Run Profile, Skill, and Ingestion Pipeline admin screens and verifies the rendered GateResult evidence for `RUN_PROFILE_RISK_ASSESSED`, `SKILL_SECURITY_SCAN`, and `INGESTION_PIPELINE_NODES_PRESENT`. The smoke creates and cleans up a temporary ingestion pipeline and saves Playwright screenshots under `output/playwright/artifacts/`.

The smoke exposed a Docker-only frontend routing issue: the production frontend is built with `VITE_API_BASE_URL=/api`, and Nginx strips the first `/api` proxy segment before forwarding to the backend. Frontend services that already target backend `/api/*` routes must therefore keep their explicit `/api` path segment when `baseURL` is configured, so browser requests like `/api/api/skills/...` reach backend `/api/skills/...` correctly.

Fresh evidence: `node --check scripts\e2e-gate-result-ui-smoke.mjs` passed; `node scripts\e2e-gate-result-ui-smoke.mjs --base-url http://127.0.0.1 --api-url http://127.0.0.1:9090 --password admin123` passed against the real full-Docker frontend/backend with marker `CODX_GATE_UI_1783395712042`, verifying Run Profile `-9105`, Skill `web-design-guidelines`, and temporary Ingestion Pipeline `332727895812665344`; `npm test -- src/services/api.test.ts src/services/frontendCapabilityContracts.test.ts src/services/serviceEndpointCoverage.test.ts` passed 3/3 files and 23/23 tests; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Run Experiment Real UI E2E

Run Experiment report productization now has a real full-Docker browser smoke for the operator workflow. `scripts/e2e-run-experiment-ui-smoke.mjs` logs into the Docker frontend at `http://127.0.0.1`, prepares a real conversation through backend login, conversation creation, and RAG SSE chat at `http://127.0.0.1:9090`, then uses the admin UI to create a two-profile run experiment, export the Markdown report, and verify the rendered report preview contains `Run Experiment Report`, `Evidence Completeness Summary`, `Output Comparison`, and `Reproduction Appendix`.

The smoke exposed a real frontend precision bug: the Run Experiment page converted `conversationId` and `baseLeafMessageId` through JavaScript `Number`, so 64-bit database IDs were rounded before being sent to the backend. In the real Docker UI path this produced `base leaf message not found` even though the assistant message existed. The page now validates those IDs as integer text and submits the original string values, letting the backend bind them to `Long` without precision loss.

Fresh evidence: `node --check scripts\e2e-run-experiment-ui-smoke.mjs` passed; `npm test -- src/pages/admin/run-profiles/RunExperimentPage.test.tsx src/services/runExperimentService.test.ts src/services/frontendCapabilityContracts.test.ts` passed 3/3 files and 23/23 tests, including 64-bit ID precision coverage; `docker compose -f docker-compose.full.yml build frontend` rebuilt the Docker frontend with only existing Browserslist/chunk-size warnings; `docker compose -f docker-compose.full.yml up -d --no-deps frontend` restarted `seahorse-frontend`; `node scripts\e2e-run-experiment-ui-smoke.mjs --base-url http://127.0.0.1 --api-url http://127.0.0.1:9090 --password admin123` passed with marker `CODX_RUN_EXP_UI_1783396668942`, conversation `332731909342162944`, base leaf `332731956947513344`, run profiles `-9101/-9102`, experiment `332731975255650304`, and report `codx-run-exp-ui-1783396668942-profile-compare-332731975255650304.md`; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox Browser URL Secret Guard Real E2E

The `sandbox_browser` full-Docker smoke now exercises URL secret policy through the real Tool Gateway before the successful external URL workflow. It verifies that URL mode fails closed for userinfo credentials, fragment identifiers, and credential-shaped query parameters, and checks both caller-visible tool payloads and persisted Tool Gateway `argumentsSummary` records do not leak the submitted secret values.

This is a verification-hardening slice for the existing Sandbox Browser egress and URL policy surface. It does not change browser runtime execution, allowlist semantics, artifact collection, session-state replay, approval policy, or sandbox networking; it adds real environment coverage for policy failures that previously relied on lower-level regression tests.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-url-secret-smoke -SkipBrowserImageBuild` passed 32/32 checks against the real full-Docker backend, including userinfo, fragment, and credential-query fail-closed cases, successful URL mode, session-state capture/replay, HAR/result/video governed artifacts, Postgres persistence, backend object storage, cleanup, and zero non-terminal sandbox sessions; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox Browser SessionState Guard Real E2E

The `sandbox_browser` full-Docker smoke now also verifies request-scoped session-state failure boundaries through the real Tool Gateway. The smoke submits invalid replay inputs for a leading-dot cookie domain, an unsupported cookie field carrying a fake storage reference, and a localStorage origin whose port does not match the target URL origin. Each case must fail closed before browser execution, and both the returned tool payload and persisted Tool Gateway `argumentsSummary` must omit the submitted cookie/localStorage/storage-ref secret values.

This is a verification-hardening slice for the existing browser auth/session surface. It does not add durable browser profiles, credential vault integration, stored session reuse, proxy-rich egress audit, or new runtime behavior; it proves that already-implemented session-state guards are exercised in the real full-Docker Tool Gateway path.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; the first real run caught an incorrect test expectation because the Tool Gateway entrypoint reports `cookie domain must be a host name only` for the leading-dot cookie-domain guard; after aligning the smoke to the real contract, `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-session-guard-smoke -SkipBrowserImageBuild` passed 33/33 checks against the real full-Docker backend, including URL secret fail-closed cases, sessionState fail-closed cases, successful URL mode, session-state capture/replay, captured artifact replay, governed downloads, audit summaries, Postgres persistence, backend object storage, cleanup, and zero non-terminal sandbox sessions; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox Browser Host Guard Real E2E

The `sandbox_browser` full-Docker smoke now verifies host-level URL egress guards through the real Tool Gateway. URL mode fails closed for localhost, IPv4 literal, IPv6 literal, single-label host, and malformed dotted DNS host targets, even when the caller includes those hosts in `allowedHosts` where applicable. These checks complement the existing userinfo, fragment, and credential-query URL secret guards in the same smoke.

This is a verification-hardening slice for the existing browser SSRF/container-network probing guard. It does not add DNS pinning, CIDR/private-network classification, a general outbound proxy, mutable operator URL policy UX, or new runtime behavior; it proves the existing host validation is exercised by the real full-Docker Tool Gateway path and that failed calls still produce value-free audit summaries.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-host-guard-smoke -SkipBrowserImageBuild` passed 33/33 checks against the real full-Docker backend, including URL host fail-closed cases, URL secret fail-closed cases, sessionState fail-closed cases, successful URL mode, session-state capture/replay, captured artifact replay, governed downloads, audit summaries, Postgres persistence, backend object storage, cleanup, and zero non-terminal sandbox sessions; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Sandbox Browser AllowedHosts Secret Guard Real E2E

The `sandbox_browser` full-Docker smoke now verifies the `allowedHosts` input boundary through the real Tool Gateway path. The smoke submits a URL-mode request where `allowedHosts` contains a credential-shaped query string such as `?api_key=...`; the call must fail closed with the existing host-only validation before browser execution, and both the caller-visible payload and persisted audit summary must omit the submitted secret value.

This is a verification-hardening slice for the existing browser egress allowlist contract. It does not add a mutable URL policy UI, DNS pinning, CIDR/private-network classification, a general outbound proxy, durable browser credentials, or new runtime behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-allowedhost-secret-smoke -SkipBrowserImageBuild` passed 33/33 checks against the real full-Docker backend, including the new credential-shaped `allowedHosts` fail-closed case, URL host fail-closed cases, URL secret fail-closed cases, sessionState fail-closed cases, successful URL mode, session-state capture/replay, captured artifact replay, governed downloads, audit summaries, Postgres persistence, backend object storage, cleanup, and zero non-terminal sandbox sessions.

## 2026-07-07 Update: Sandbox Browser Cookie Domain Guard Real E2E

The `sandbox_browser` full-Docker smoke now verifies explicit `cookies` domain boundaries through the real Tool Gateway path. The smoke submits URL-mode requests where a cookie domain is either absent from `allowedHosts` or present in `allowedHosts` but does not match the target URL host; both calls must fail closed before browser execution, and both the caller-visible payload and persisted audit summary must omit the submitted cookie secret values.

This is a verification-hardening slice for the existing browser session-cookie and egress allowlist contract. It does not add durable browser profiles, credential vault integration, mutable URL policy UI, DNS pinning, CIDR/private-network classification, or new runtime behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-cookie-domain-smoke -SkipBrowserImageBuild` passed 34/34 checks against the real full-Docker backend, including cookie domain not-in-`allowedHosts` and host-mismatch fail-closed cases, credential-shaped `allowedHosts` fail-closed case, URL host fail-closed cases, URL secret fail-closed cases, sessionState fail-closed cases, successful URL mode, session-state capture/replay, captured artifact replay, governed downloads, audit summaries, Postgres persistence, backend object storage, cleanup, and zero non-terminal sandbox sessions.

## 2026-07-07 Update: Sandbox Browser SessionState Origin Guard Real E2E

The `sandbox_browser` full-Docker smoke now deepens request-scoped `sessionState.origins` fail-closed coverage through the real Tool Gateway path. The smoke submits URL-mode replay inputs where a localStorage origin host is absent from `allowedHosts`, present in `allowedHosts` but different from the target URL host, or shaped like a full URL with userinfo/path/query/fragment credential material. Each call must fail closed before browser execution, and both caller-visible payloads and persisted audit summaries must omit submitted localStorage and origin credential values.

This is a verification-hardening slice for the existing browser session-state replay boundary. It does not change session replay semantics, add durable browser profiles, replay blocked artifacts, introduce a credential vault, change URL policy, or add new runtime behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-origin-guard-smoke -SkipBrowserImageBuild` passed 34/34 checks against the real full-Docker backend, including sessionState origin allowlist, host-mismatch, credential-parts, and port-mismatch fail-closed cases, cookie domain fail-closed cases, URL host and secret fail-closed cases, successful URL mode, session-state capture/replay, captured artifact replay, governed downloads, audit summaries, Postgres persistence, backend object storage, cleanup, and zero non-terminal sandbox sessions.

## 2026-07-07 Update: Sandbox Browser SessionState Cookie Domain Guard Real E2E

The `sandbox_browser` full-Docker smoke now also verifies request-scoped `sessionState.cookies` domain boundaries through the real Tool Gateway path. The smoke submits URL-mode replay inputs where a session-state cookie domain is absent from `allowedHosts` or present in `allowedHosts` but different from the target URL host. Each call must fail closed before browser execution, and both caller-visible payloads and persisted audit summaries must omit submitted cookie values.

This is a verification-hardening slice for the existing browser session-state replay boundary. It does not change cookie injection semantics, persistent browser profiles, credential vault integration, captured artifact replay, URL egress policy, or runtime execution behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-session-cookie-domain-smoke -SkipBrowserImageBuild` passed 34/34 checks against the real full-Docker backend, including sessionState cookie domain not-in-`allowedHosts` and host-mismatch fail-closed cases, sessionState origin allowlist/host/credential fail-closed cases, URL host and secret fail-closed cases, successful URL mode, session-state capture/replay, captured artifact replay, governed downloads, audit summaries, Postgres persistence, backend object storage, cleanup, and zero non-terminal sandbox sessions.

## 2026-07-07 Update: Sandbox Browser SessionState Artifact Guard Real E2E

The `sandbox_browser` full-Docker smoke now verifies request-scoped `sessionStateArtifactId` failure boundaries through the real Tool Gateway path. The smoke submits URL-mode replay inputs with an invalid artifact id and with both explicit `sessionState` plus `sessionStateArtifactId`; both calls must fail closed before browser execution, and both caller-visible payloads and persisted audit summaries must omit the submitted artifact id and cookie secret values.

This is a verification-hardening slice for the existing governed browser session-state artifact replay boundary. It does not change artifact replay semantics, enable replay from blocked artifacts, add durable browser profiles, introduce credential vault integration, or change runtime execution behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-session-artifact-guard-smoke -SkipBrowserImageBuild` passed 35/35 checks against the real full-Docker backend, including invalid `sessionStateArtifactId` and explicit-sessionState-plus-artifact fail-closed cases, sessionState cookie/origin fail-closed cases, URL host and secret fail-closed cases, successful URL mode, session-state capture/replay, captured artifact replay, governed downloads, audit summaries, Postgres persistence, backend object storage, cleanup, and zero non-terminal sandbox sessions.

## 2026-07-07 Update: MCP HTTP Tool Gateway Audit Summary E2E

The MCP HTTP smoke now verifies the governed `http.echo` tool through the real Tool Gateway instead of stopping at MCP server discovery and direct diagnostic calls. The smoke starts a temporary HTTP MCP server and backend container on the full-compose Docker network, logs in, verifies `http.echo` is `HIGH` risk and requires approval, executes the diagnostic path through approval, invokes `/api/tools/http.echo/invoke`, then queries `/api/tool-invocations` for the persisted audit record.

This is a verification-hardening slice for the existing MCP HTTP governance path. It does not change MCP runtime behavior, catalog registration, approval policy, HTTP transport handling, or the generic Tool Gateway audit summarizer; it proves the existing cross-provider audit shape is exercised by a real Docker MCP HTTP tool.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-mcp-http-smoke.ps1` passed. The first real Docker run exposed two stale smoke assumptions: MCP HTTP diagnostics are now approval-gated, and the temporary backend must explicitly enable `SEAHORSE_AGENT_ADVANCED_AGENT_RUN_MANAGEMENT_ENABLED=true` before `/api/tools/{toolId}/invoke` is available. After aligning the smoke to the current governance contract, `.\scripts\e2e-mcp-http-smoke.ps1 -Password admin123` passed 15/15 checks against temporary Docker backend/MCP containers on the full-compose network, including approval-required diagnostic execution, approved HTTP echo execution, catalog HIGH/requires-approval assertions, governed `/api/tools/http.echo/invoke`, and the real `/api/tool-invocations` audit assertion for `{"toolId":"http.echo","argumentKeys":["text"],"argumentCount":1,"argumentValueCount":1,"argumentValueTotalLength":23,"argumentValueMaxLength":23}` without raw echo text or response text leakage. `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health.

## 2026-07-07 Update: Remote A2A Tool Gateway Failure Redaction E2E

The remote A2A Tool Gateway path now has a dedicated real Docker smoke through `scripts/e2e-remote-a2a-tool-gateway-smoke.ps1`. The smoke starts a temporary A2A-enabled backend container on the full-compose Docker network, logs in, verifies `invoke_remote_a2a_agent` is cataloged as `HIGH` risk, `EXECUTE`, `REMOTE_AGENT`, and approval-required, invokes the tool through `/api/tools/invoke_remote_a2a_agent/invoke`, approves the request, then queries `/api/tool-invocations` for the persisted audit record.

The first real E2E run exposed a P1 leakage in the failure degradation path: when the remote resolver failed on a missing agent with metadata `version`, the returned error included `missing-agent@version-secret...`. `AgentScopeA2AToolPortAdapter` now normalizes metadata once before connector invocation and redacts metadata values from downgraded connector exception messages, while preserving the target-agent diagnostic and existing credential/prompt redaction.

This is a cross-provider Tool Gateway hardening slice for the remote A2A failure path. It does not enable A2A in the default full-compose backend, add live multi-agent success coverage, change Nacos discovery, or add Studio/OTEL production integration; it proves that an A2A-enabled backend still routes through approval, failure degradation, and value-free audit summaries without leaking prompt or metadata values.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-remote-a2a-tool-gateway-smoke.ps1` passed; `.\mvnw.cmd package -pl seahorse-agent-bootstrap -am "-DskipTests" "-Dmaven.test.skip=true" "-Dspotless.check.skip=true"` rebuilt the exec jar with `BUILD SUCCESS` across 28/28 modules; `.\scripts\e2e-remote-a2a-tool-gateway-smoke.ps1 -Password admin123` passed 6/6 checks against a temporary A2A-enabled Docker backend on the full-compose network. The passing run verified catalog governance, approval-required Tool Gateway invocation, failure text using `@[redacted-metadata]`, and a real `/api/tool-invocations` record with value-free `argumentsSummary` fields for agent-name length, prompt length, metadata keys/count/value lengths, version presence/length, and top-level argument shape while excluding raw agent name, prompt, version, and source values. `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks against the default full-compose backend, confirming health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health remain intact while default A2A stays disabled.

## 2026-07-07 Update: Remote A2A Tool Gateway Success E2E

The remote A2A Tool Gateway smoke now covers a real successful cross-agent invocation in addition to the missing-agent failure degradation path. The script starts a temporary remote A2A backend that registers a tenant-qualified Agent Card in Nacos, starts a second temporary A2A-enabled Tool Gateway backend on the same full-compose Docker network, logs in, approves `invoke_remote_a2a_agent`, invokes the registered remote agent, and verifies the returned mock agent content.

This is a real E2E evidence-hardening slice for the existing A2A success path. It does not enable A2A in the default full-compose backend, add persistent multi-agent topology management, change Nacos discovery policy, alter approval semantics, or add Studio/OTEL production integration.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-remote-a2a-tool-gateway-smoke.ps1` passed; the first real Docker run caught a stale smoke assertion that read the success payload from `observation` instead of the real Tool Gateway `content` field. After aligning the smoke to the API contract, `.\scripts\e2e-remote-a2a-tool-gateway-smoke.ps1 -Password admin123` passed 10/10 checks against temporary remote and gateway backend containers on the full-compose network. The passing run verified remote Agent Card registration, catalog governance, approval-required Tool Gateway invocation, successful `mock-streaming-chat` response, a persisted `SUCCEEDED` `/api/tool-invocations` audit summary with value-free agent-name/prompt/metadata shape, the existing missing-agent failure redaction path, and cleanup with no leftover `seahorse-remote-a2a-tool-smoke*` containers.

## 2026-07-07 Update: Remote A2A Tool Gateway Tenant-Signed E2E

The remote A2A Tool Gateway smoke is now parameterized for A2A authentication mode and verifies the remote Agent Card advertises the selected `seahorse:a2a:authMode`. This lets the same real Docker Tool Gateway path cover both the default shared-secret mode and the production-oriented `tenant-signed` mode without changing default full-compose A2A enablement.

This is an authentication evidence-hardening slice for the existing A2A success and failure paths. It does not add durable nonce storage, change signed-header validation behavior, alter Tool Gateway approval semantics, or add Studio/OTEL production integration.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-remote-a2a-tool-gateway-smoke.ps1` passed; `.\scripts\e2e-remote-a2a-tool-gateway-smoke.ps1 -Password admin123 -AuthMode tenant-signed` passed 10/10 checks against temporary remote and gateway backend containers on the full-compose network. The passing run verified tenant-signed remote Agent Card metadata, approval-required Tool Gateway invocation, successful signed A2A call returning `mock-streaming-chat`, a persisted `SUCCEEDED` audit summary with value-free argument shape, the existing missing-agent failure redaction path, and cleanup with no leftover `seahorse-remote-a2a-tool-smoke*` containers.

## 2026-07-07 Update: Sandbox File Convert Preflight Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies preflight failure boundaries through the real Tool Gateway path. The smoke submits unsupported `xml -> json` conversion input and plain-text `pdf -> txt` input without `contentEncoding=base64`; both calls must fail closed before a sandbox file-conversion session is created. The caller-visible failure payloads and persisted Tool Gateway `argumentsSummary` records must omit the submitted raw content and secret markers while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing file-conversion adapter and audit summarizer. It does not add new conversion formats, LibreOffice/Tika integration, PDF rendering/OCR, Office editing, broader binary conversion, or new runtime behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-preflight-guard-smoke` passed 74/74 checks against the real full-Docker backend, including unsupported-conversion and PDF-without-base64 fail-closed cases, response redaction, `FAILED` audit summaries without raw input leakage, all existing CSV/JSON/text/document/binary conversion success paths, governed artifact downloads, local object storage verification, and Tool Gateway audit checks; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-07 Update: Sandbox File Convert Runtime Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now also verifies container-runtime input guards through the real Tool Gateway path. The smoke submits invalid base64 PDF content and base64 PDF bytes containing an `/Encrypt` marker; both calls must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input file is written into the container workspace. The returned failure payloads and persisted Tool Gateway `argumentsSummary` records must omit the submitted base64 payloads and embedded secret markers while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing binary file-conversion guardrail. It does not add a new PDF parser, password handling, OCR, rendering, external scanner engines, LibreOffice/Tika integration, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-runtime-guard-smoke` passed 74/74 checks against the real full-Docker backend, including invalid-base64 and encrypted-PDF runtime fail-closed cases, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-07 Update: Sandbox File Convert Office Active-Content Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies Office package active-content guards through the real Tool Gateway path. The smoke submits base64 DOCX and PPTX packages containing `word/vbaProject.bin` and `ppt/vbaProject.bin`; both calls must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payloads and persisted Tool Gateway `argumentsSummary` records must omit the submitted package payloads, macro entry names, and embedded secret markers while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing OOXML package guardrail. It does not add Office rendering/editing, macro parsing or execution, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-office-guard-smoke` passed 74/74 checks against the real full-Docker backend, including DOCX and PPTX active-content runtime fail-closed cases, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-07 Update: Sandbox File Convert ODF Active-Content Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies OpenDocument package active-content guards through the real Tool Gateway path. The smoke submits base64 ODT, ODS, and ODP packages containing `Scripts/macro.js`; each call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payloads and persisted Tool Gateway `argumentsSummary` records must omit the submitted package payloads, script entry names, and embedded secret markers while retaining only value-free format, encoding, length, and argument-shape metadata.

The long file-conversion smoke now also retries HTTP 429 responses from the global IP rate-limit filter with a bounded wait. This keeps the real full-Docker test faithful to production rate limiting while avoiding false negatives when the growing smoke legitimately exceeds the per-minute request window.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; the first `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-odf-guard-smoke` run proved the new ODF invalid-input step but later hit a real `429 rate limit exceeded` on approval; after adding bounded 429 retry/wait handling in the smoke harness, the same command passed 74/74 checks against the real full-Docker backend, including ODT, ODS, and ODP active-content runtime fail-closed cases, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks; `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-07 Update: Sandbox File Convert XLSX Active-Content Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now completes OOXML active-content guard coverage for the spreadsheet path through the real Tool Gateway. The smoke submits a base64 XLSX package containing `xl/vbaProject.bin`; the call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payload and persisted Tool Gateway `argumentsSummary` record must omit the submitted package payload, macro entry name, and embedded secret marker while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing XLSX package guardrail. It does not add spreadsheet rendering, formula evaluation, macro parsing or execution, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-xlsx-guard-smoke` passed 74/74 checks against the real full-Docker backend, including the new XLSX active-content runtime fail-closed case, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office/ODF guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks. The run hit one real `429 rate limit exceeded` response during the long smoke and the bounded retry/wait path recovered. `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-07 Update: Sandbox File Convert XLSX ActiveX Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies the XLSX ActiveX branch of the existing active-content guard through the real Tool Gateway. The smoke submits a base64 XLSX package containing `xl/activeX/activeX1.xml`; the call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payload and persisted Tool Gateway `argumentsSummary` record must omit the submitted package payload, ActiveX entry names, and embedded secret marker while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing XLSX package guardrail. It does not add spreadsheet rendering, ActiveX parsing or execution, embedded object handling, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-xlsx-activex-smoke` passed 74/74 checks against the real full-Docker backend, including the new XLSX ActiveX runtime fail-closed case, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office/ODF/XLSX macro guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks. The run hit one real `429 rate limit exceeded` response during governed artifact download and the bounded retry/wait path recovered. The first backend regression run reached 15 passing checks but failed the memory/profile check because the profile facts endpoint still returned the previous smoke value after the script's bounded polling window; after a 20-second wait, rerunning `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-07 Update: Sandbox File Convert XLSX Embedded Object Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies the XLSX embedded-object branch of the existing active-content guard through the real Tool Gateway. The smoke submits a base64 XLSX package containing `xl/embeddings/oleObject1.bin`; the call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payload and persisted Tool Gateway `argumentsSummary` record must omit the submitted package payload, embedded-object entry names, and embedded secret marker while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing XLSX package guardrail. It does not add spreadsheet rendering, embedded object parsing or extraction, OLE handling, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-xlsx-embedded-smoke` passed 74/74 checks against the real full-Docker backend, including the new XLSX embedded-object runtime fail-closed case, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office/ODF/XLSX macro/ActiveX guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks. The run hit one real `429 rate limit exceeded` response during XLSX-to-HTML Tool Gateway invocation and the bounded retry/wait path recovered. The first backend regression run reached 15 passing checks but failed the memory/profile check because the profile facts endpoint still returned the previous smoke value after the script's bounded polling window; after a 30-second wait, rerunning `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-07 Update: Sandbox File Convert XLSX External Link Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies the XLSX external-link branch of the existing active-content guard through the real Tool Gateway. The smoke submits a base64 XLSX package containing `xl/externalLinks/externalLink1.xml`; the call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payload and persisted Tool Gateway `argumentsSummary` record must omit the submitted package payload, external-link entry names, and embedded secret marker while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing XLSX package guardrail. It does not add spreadsheet rendering, external workbook resolution, network access, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-xlsx-external-link-smoke` passed 74/74 checks against the real full-Docker backend, including the new XLSX external-link runtime fail-closed case, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office/ODF/XLSX macro/ActiveX/embedded-object guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks. `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-07 Update: Sandbox File Convert DOCX ActiveX Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies the DOCX ActiveX branch of the existing active-content guard through the real Tool Gateway. The smoke submits a base64 DOCX package containing `word/activeX/activeX1.xml`; the call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payload and persisted Tool Gateway `argumentsSummary` record must omit the submitted package payload, ActiveX entry names, and embedded secret marker while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing DOCX package guardrail. It does not add document rendering, ActiveX parsing or execution, embedded object handling, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-docx-activex-smoke` passed 74/74 checks against the real full-Docker backend, including the new DOCX ActiveX runtime fail-closed case, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office/ODF/XLSX guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks. The run hit one real `429 rate limit exceeded` response during approval and the bounded retry/wait path recovered. `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-08 Update: Sandbox File Convert DOCX Embedded Object Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies the DOCX embedded-object branch of the existing active-content guard through the real Tool Gateway. The smoke submits a base64 DOCX package containing `word/embeddings/oleObject1.bin`; the call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payload and persisted Tool Gateway `argumentsSummary` record must omit the submitted package payload, embedded-object entry names, and embedded secret marker while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing DOCX package guardrail. It does not add document rendering, embedded object parsing or extraction, OLE handling, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-docx-embedded-smoke` passed 74/74 checks against the real full-Docker backend, including the new DOCX embedded-object runtime fail-closed case, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office/ODF/XLSX/DOCX ActiveX guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks. The run hit one real `429 rate limit exceeded` response during PDF-to-TXT Tool Gateway invocation and the bounded retry/wait path recovered. `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-08 Update: Sandbox File Convert DOCX External Link Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies the DOCX external-link branch of the existing active-content guard through the real Tool Gateway. The smoke submits a base64 DOCX package containing `word/externalLinks/externalLink1.xml`; the call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payload and persisted Tool Gateway `argumentsSummary` record must omit the submitted package payload, external-link entry names, and embedded secret marker while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing DOCX package guardrail. It does not add document rendering, external relationship resolution, network access, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-docx-external-link-smoke` passed 74/74 checks against the real full-Docker backend, including the new DOCX external-link runtime fail-closed case, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office/ODF/XLSX/DOCX ActiveX/embedded-object guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks. The run hit one real `429 rate limit exceeded` response during governed artifact download and the bounded retry/wait path recovered. `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-08 Update: Sandbox File Convert PPTX ActiveX Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies the PPTX ActiveX branch of the existing active-content guard through the real Tool Gateway. The smoke submits a base64 PPTX package containing `ppt/activeX/activeX1.xml`; the call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payload and persisted Tool Gateway `argumentsSummary` record must omit the submitted package payload, ActiveX entry names, and embedded secret marker while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing PPTX package guardrail. It does not add slide rendering, ActiveX parsing or execution, embedded object handling, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-pptx-activex-smoke` passed 74/74 checks against the real full-Docker backend, including the new PPTX ActiveX runtime fail-closed case, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office/ODF/XLSX/DOCX guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks. The run hit one real `429 rate limit exceeded` response during ODS-to-HTML Tool Gateway invocation and the bounded retry/wait path recovered. `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-08 Update: Sandbox File Convert PPTX Embedded Object Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies the PPTX embedded-object branch of the existing active-content guard through the real Tool Gateway. The smoke submits a base64 PPTX package containing `ppt/embeddings/oleObject1.bin`; the call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payload and persisted Tool Gateway `argumentsSummary` record must omit the submitted package payload, embedded-object entry names, and embedded secret marker while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing PPTX package guardrail. It does not add slide rendering, embedded object parsing or extraction, OLE handling, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-pptx-embedded-smoke` passed 74/74 checks against the real full-Docker backend, including the new PPTX embedded-object runtime fail-closed case, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office/ODF/XLSX/DOCX/PPTX ActiveX guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks. The run hit one real `429 rate limit exceeded` response during approval polling and the bounded retry/wait path recovered. `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-08 Update: Sandbox File Convert PPTX External Link Guard Real E2E

The `sandbox_file_convert` full-Docker smoke now verifies the PPTX external-link branch of the existing active-content guard through the real Tool Gateway. The smoke submits a base64 PPTX package containing `ppt/externalLinks/externalLink1.xml`; the call must fail closed in the `FILE_CONVERSION` runtime preparation path before the generated converter script or input package is written into the container workspace. The returned failure payload and persisted Tool Gateway `argumentsSummary` record must omit the submitted package payload, external-link entry names, and embedded secret marker while retaining only value-free format, encoding, length, and argument-shape metadata.

This is a verification-hardening slice for the existing PPTX package guardrail. It does not add slide rendering, external relationship resolution, network access, LibreOffice/Tika integration, recursive package extraction, external scanner engines, or broader binary conversion behavior.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1` passed; `.\scripts\e2e-sandbox-file-convert-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-file-convert-pptx-external-link-smoke` passed 74/74 checks against the real full-Docker backend, including the new PPTX external-link runtime fail-closed case, response redaction, `FAILED` Tool Gateway audit summaries without raw input leakage, all existing preflight/runtime/Office/ODF/XLSX/DOCX/PPTX ActiveX/embedded-object guard checks, CSV/JSON/text/document/binary success paths, governed artifact downloads, local object storage verification, and audit summary checks. The run hit one real `429 rate limit exceeded` response during approval and the bounded retry/wait path recovered. `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 checks across full-compose health, auth, knowledge CRUD/upload/chunk, real RAG SSE/trace, memory/profile, catalogs, audit, metadata governance, and SRE health. No `seahorse-sandbox-*` containers were left behind after verification.

## 2026-07-08 Update: Sandbox Browser Configured Proxy Egress Real E2E

The `sandbox_browser` URL-mode runtime now supports server-configured browser proxy egress through `seahorse-agent.adapters.sandbox.container.browser-proxy-server` and the Docker overlay environment variable `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_BROWSER_PROXY_SERVER`. The proxy setting is injected only by backend configuration, only when URL mode is used, and is not accepted from tool arguments, so proxy endpoints and possible credentials are not exposed to callers or Tool Gateway audit summaries.

The generated Playwright context enables the configured proxy while preserving the existing route allowlist and URL/session guards. Runtime observations expose only value-free metadata, `proxy.enabled=true`, and never echo the proxy URL. Docker host-gateway mapping now includes `.docker.internal` proxy hosts such as `proxy.docker.internal`, while non-browser runtimes remain fail-closed for requested networking.

This completes the first P1 browser proxy egress step. Earlier roadmap wording that listed all proxy-rich browser egress as remaining should now be read as follow-up work for proxy authentication, rotation, operator policy UX, richer egress audit, DNS/CIDR pinning, long-lived browser profiles, and stronger runtime isolation; proxy authentication is covered by the next update below.

Fresh evidence: `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests#shouldInjectConfiguredBrowserProxyForUrlModeOnly" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 1/1; `git diff --check` passed with only existing CRLF warnings; compose overlay validation passed with the configured proxy env; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml build --build-arg HTTP_PROXY=http://192.168.1.9:7890 --build-arg HTTPS_PROXY=http://192.168.1.9:7890 backend` rebuilt the backend image with in-image Maven `BUILD SUCCESS`; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml up -d --no-deps --force-recreate backend` recreated a healthy backend at `http://127.0.0.1:9090`; and `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-proxy-egress-smoke-rerun -SkipBrowserImageBuild -ExpectBrowserProxy` passed 37/37 against the real full-Docker environment. The smoke started a real Python HTTP proxy container, verified both the main URL and allowlisted asset URL appeared in the proxy hit log, verified the downloaded browser JSON artifact contained `proxy.enabled=true`, verified cookie/session-storage secret values did not appear in proxy hits, downloaded governed JSON/HAR/video artifacts, and confirmed no managed sandbox containers or non-terminal sandbox sessions remained. A first backend smoke retry hit the expected IP rate limit immediately after the long E2E; after the 60-second Redis rate-limit window, `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20, and the final post-rerun backend smoke also passed 20/20.

## 2026-07-08 Update: Sandbox Browser Configured Proxy Authentication Real E2E

The server-configured browser proxy path now supports proxy authentication with `seahorse-agent.adapters.sandbox.container.browser-proxy-username` and `browser-proxy-password`, exposed in the Docker overlay as `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_BROWSER_PROXY_USERNAME` and `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_BROWSER_PROXY_PASSWORD`. Credentials remain backend-owned configuration: tool callers cannot pass them, `browser-proxy-server` still rejects userinfo URLs, and browser result artifacts expose only `proxy.enabled` plus `proxy.authenticated` booleans.

The generated Playwright context adds `username` and `password` only when both configured values are present and URL mode is active. Misconfigured one-sided credentials fail closed with value-free configuration errors. The E2E proxy fixture now supports a Basic-auth-required mode and logs only `auth=ok`, never credential values.

This completes the first P1 proxy-authentication step for browser URL egress. Remaining proxy work is now rotation, operator-managed egress policy UX, richer egress audit, DNS/CIDR pinning, long-lived browser profiles, and stronger runtime isolation; richer egress audit is covered by the next update below.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests#shouldInjectConfiguredBrowserProxyAuthentication,ContainerSandboxRuntimeAdapterTests#shouldInjectConfiguredBrowserProxyForUrlModeOnly" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 2/2; `git diff --check` passed with only CRLF warnings; compose overlay validation passed with proxy server plus username/password; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml build --build-arg HTTP_PROXY=http://192.168.1.9:7890 --build-arg HTTPS_PROXY=http://192.168.1.9:7890 backend` rebuilt the backend image with in-image Maven `BUILD SUCCESS`; `seahorse-backend` was recreated and `/actuator/health` returned `{"status":"UP"}`. Real full-Docker E2E passed through `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-proxy-auth-smoke-rerun -SkipBrowserImageBuild -ExpectBrowserProxyAuth -ProxyUsername seahorse-proxy-user -ProxyPassword seahorse-proxy-password`, reporting 37/37 passing checks. The smoke verified authenticated proxy hits for the main URL and allowlisted asset URL, verified downloaded JSON artifacts include `proxy.authenticated=true`, checked JSON/HAR downloads and Tool Gateway audit summaries do not leak proxy credentials, checked proxy hits do not leak proxy credentials or cookie/session-storage secret values, and confirmed no managed sandbox containers or non-terminal sandbox sessions remained. Final backend smoke passed 20/20 against the same full-compose sandbox overlay.

## 2026-07-08 Update: Sandbox Browser Egress Audit Summary Real E2E

The sandbox browser URL-mode runtime now records a value-free egress audit summary in `browser-result.json`. The summary includes mode, network posture, policy label, allowed-host count, request totals, continued/blocked request counts, blocked reason counts such as `host_not_allowlisted`, resource-type counts, allowed-host request counts, and proxy enabled/authenticated booleans. The generated HAR now also records `_blockedReason` for blocked requests.

This keeps egress audit evidence inside governed browser artifacts without adding a caller-controlled proxy input, exposing proxy endpoints, or recording cookie/session/proxy credential values. Remaining proxy work is now rotation, operator-managed egress policy UX, DNS/CIDR pinning, long-lived browser profiles, and stronger runtime isolation; rotation is covered by the next update below.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests#shouldInjectConfiguredBrowserProxyAuthentication,ContainerSandboxRuntimeAdapterTests#shouldInjectConfiguredBrowserProxyForUrlModeOnly" "-Dsurefire.failIfNoSpecifiedTests=false" test` passed 2/2; compose overlay validation passed with proxy server plus username/password; `docker compose -f docker-compose.full.yml -f docker-compose.sandbox.yml build --build-arg HTTP_PROXY=http://192.168.1.9:7890 --build-arg HTTPS_PROXY=http://192.168.1.9:7890 backend` rebuilt the backend image with in-image Maven `BUILD SUCCESS`; `seahorse-backend` was recreated and `/actuator/health` returned `{"status":"UP"}`. Real full-Docker E2E passed through `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-egress-audit-smoke -SkipBrowserImageBuild -ExpectBrowserProxyAuth -ProxyUsername seahorse-proxy-user -ProxyPassword seahorse-proxy-password`, reporting 37/37 passing checks. The smoke verified `resultSummary` egress counters, downloaded result JSON `egress` posture/counts/proxy booleans, HAR `_blockedReason=host_not_allowlisted`, authenticated proxy hits, credential/session/proxy value non-leakage, governed artifacts, cleanup, and zero non-terminal sandbox sessions. The first backend smoke rerun immediately after E2E hit a real Redis rate-limit 429 after 9 passing checks; after the window expired, `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20 against the same full-compose sandbox overlay.

## 2026-07-08 Update: Sandbox Browser Proxy Rotation Real E2E

The server-configured browser proxy path now supports a backend-owned proxy pool through `seahorse-agent.adapters.sandbox.container.browser-proxy-servers` and the Docker overlay variable `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_BROWSER_PROXY_SERVERS`. The pool accepts comma-separated HTTP/HTTPS origins, keeps the legacy single `browser-proxy-server` path compatible, and fails closed when both single and pooled settings are configured together. URL-mode browser executions select from the pool with a backend-local round-robin cursor; callers still cannot provide proxy settings.

Governed browser result artifacts now expose value-free proxy rotation metadata only: `poolSize` and `rotationEnabled` beside the existing enabled/authenticated booleans. The selected proxy origin, full pool, usernames, and passwords stay out of observations, artifacts, HAR, audit summaries, and proxy fixture logs. Docker host-gateway mapping covers all `.docker.internal` proxy hosts in the configured pool. Remaining proxy/browser production hardening is operator-managed egress policy UX, DNS/CIDR pinning, long-lived browser profile governance, and stronger runtime isolation.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; compose overlay validation passed with `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_BROWSER_PROXY_SERVERS=http://proxy.docker.internal:18082,http://proxy.docker.internal:18083`; focused adapter regression passed 3/3 through `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests#shouldRotateConfiguredBrowserProxyServersForUrlMode,ContainerSandboxRuntimeAdapterTests#shouldInjectConfiguredBrowserProxyAuthentication,ContainerSandboxRuntimeAdapterTests#shouldInjectConfiguredBrowserProxyForUrlModeOnly" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the full backend image rebuilt through `HTTP_PROXY=http://192.168.1.9:7890` and `HTTPS_PROXY=http://192.168.1.9:7890` with in-image Maven `BUILD SUCCESS`; `seahorse-backend` was recreated under the sandbox overlay and `/actuator/health` returned `UP`. Real full-Docker E2E passed through `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-proxy-rotation-smoke-rerun -SkipBrowserImageBuild -ExpectBrowserProxyAuth -ExpectBrowserProxyRotation -ProxyUsername seahorse-proxy-user -ProxyPassword seahorse-proxy-password -ProxyPort 18082 -SecondaryProxyPort 18083`, reporting 38/38 checks. The smoke verified two real proxy fixtures were both hit by URL-mode browser flows, authenticated proxy use, resultSummary rotation counters, downloaded governed JSON `poolSize=2` and `rotationEnabled=true`, credential/session/proxy value non-leakage, governed artifacts, cleanup, and zero non-terminal sandbox sessions. After waiting for the real rate-limit window, `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20.

## 2026-07-08 Update: Sandbox Browser DNS/CIDR Pinning Real E2E

The `sandbox_browser` URL-mode runtime now performs runtime DNS/CIDR pinning in the generated Playwright route handler. For each HTTP/HTTPS request whose host is already in `allowedHosts`, the browser script resolves the host with `socket.getaddrinfo(...)` inside the sandbox container and blocks the request if any resolved address is non-global according to Python `ipaddress.ip_address(...).is_global`. Blocked requests use value-free reasons such as `dns_resolution_failed` or `resolved_private_ip`; resolved IPs, proxy endpoints, cookie values, localStorage values, and URL query values are not written to observations, artifacts, HAR blocked reasons, or Tool Gateway audit summaries.

The container adapter adds operator configuration `seahorse-agent.adapters.sandbox.container.browser-private-network-allowed-hosts` for explicit local/private host exceptions. The sandbox compose overlay defaults this exception list to `host.docker.internal,assets.docker.internal` so the existing local full-Docker fixture path remains intentional and visible. Public URL-mode hosts still require the existing global sandbox `ALLOWLISTED` policy plus per-call `allowedHosts`; caller-controlled host strings alone cannot bypass DNS classification.

This completes the first P1 DNS/CIDR pinning step for browser URL egress. Remaining browser production hardening is now operator-managed egress policy UX, long-lived browser profile governance, and stronger runtime isolation.

Fresh evidence: PowerShell parsing for `.\scripts\e2e-sandbox-browser-tool-smoke.ps1` passed; compose overlay validation passed with `SEAHORSE_AGENT_SANDBOX_ALLOWLISTED_HOSTS=host.docker.internal,assets.docker.internal,127.0.0.1.sslip.io` plus the proxy pool; focused adapter regression passed 3/3 through `.\mvnw.cmd -pl seahorse-agent-adapter-sandbox-container -am "-Dtest=ContainerSandboxRuntimeAdapterTests#shouldRunBrowserAutomationUrlModeWithAllowlistedHostNetwork,ContainerSandboxRuntimeAdapterTests#shouldMapRequestedDockerInternalAllowedHostsForBrowserUrlMode,ContainerSandboxRuntimeAdapterTests#shouldRotateConfiguredBrowserProxyServersForUrlMode" "-Dsurefire.failIfNoSpecifiedTests=false" test`; the full backend image rebuilt through `HTTP_PROXY=http://192.168.1.9:7890` and `HTTPS_PROXY=http://192.168.1.9:7890` with in-image Maven `BUILD SUCCESS`; and `seahorse-backend` was recreated under the sandbox overlay with `/actuator/health` returning `UP`. Real full-Docker E2E passed through `.\scripts\e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-dns-cidr-smoke-rerun -SkipBrowserImageBuild -ExpectBrowserProxyAuth -ExpectBrowserProxyRotation -ProxyUsername seahorse-proxy-user -ProxyPassword seahorse-proxy-password -ProxyPort 18082 -SecondaryProxyPort 18083`, reporting 38/38 checks. The smoke verified a real DNS host `127.0.0.1.sslip.io` that is syntactically valid but resolves to loopback fails closed with `resolved_private_ip`, verifies the submitted non-credential query marker is absent from caller payloads and persisted audit summaries, still verifies successful local fixture URL mode through explicitly configured private-network exceptions, proxy authentication and rotation, governed downloads, audit pagination, bounded 429 download retry, cleanup, and zero non-terminal sandbox sessions. After waiting for the real rate-limit window, `.\scripts\e2e-backend-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -RuntimeProfile full-compose` passed 20/20.

## 2026-07-08 Update: Sandbox Browser Egress Policy UI Visibility Real E2E

The admin Sandbox Runtime governance panel now exposes read-only browser egress policy posture from the existing runtime profiles API. Operators can see the active default network policy, allowlisted host count, and a bounded allowlist preview in the same page that manages runtime profile and tool quota controls.

This slice also fixes the frontend sandbox service API prefix back to `/api/sandbox`. In the packaged Docker frontend, `VITE_API_BASE_URL=/api` now resolves sandbox calls to `/api/api/sandbox/...` through Nginx instead of the broken `/api/api/api/sandbox/...` path. It does not add editable allowlist policy persistence, private-network exception editing, long-lived browser profile governance, or new backend policy fields.

Fresh evidence: `npm run build` passed with the existing Browserslist/chunk-size warnings; `npx vitest run src/services/frontendCapabilityContracts.test.ts --pool threads --no-file-parallelism --maxWorkers 1` passed 17/17; `docker compose -f docker-compose.full.yml build frontend` rebuilt the packaged frontend image through the local `192.168.1.9:7890` proxy; and `seahorse-frontend` was recreated at `http://127.0.0.1`. Real full-Docker browser E2E passed through `.\scripts\e2e-sandbox-tool-quota-page-smoke.ps1 -BaseUrl http://127.0.0.1 -Password admin123 -Marker seahorse-sandbox-egress-policy-page-smoke`, reporting `PASS sandbox tool quota page smoke` and `Egress policy: ALLOWLISTED / 3 hosts`. The smoke logs confirmed real page calls to `/api/api/sandbox/runtime/profiles?tenantId=default` and `/api/api/sandbox/runtime/tool-quota-policies` returned 200, with no new sandbox page 404s; backend `/actuator/health` returned `{"status":"UP"}`.

## 2026-07-12 Update: Editable Tenant Sandbox Egress Policy Real E2E

The admin Sandbox Runtime governance panel now supports tenant-level editable browser egress policy. Operators can inspect and save `DENY_ALL` or `ALLOWLISTED` posture plus a normalized host allowlist through `GET /api/sandbox/runtime/egress-policy` and `POST /api/sandbox/runtime/egress-policy`. The kernel owns the effective policy contract, while JDBC persistence stores one policy per tenant in `sa_sandbox_egress_policy`; `RepositoryBackedSandboxPolicyPort` makes runtime policy decisions read the same tenant record used by the operator UI.

This closes the previous editable-policy UX gap for browser egress posture. Remaining sandbox browser hardening is long-lived browser profile governance, stronger runtime isolation, and node-pool scheduling/health.

## 2026-07-12 Update: Editable Tenant Sandbox Private-Network Exceptions Real E2E

The tenant egress policy now also owns `browserPrivateNetworkAllowedHosts`. Operators edit the bounded, normalized browser private-network exception list beside `DENY_ALL` or `ALLOWLISTED` and its public-host allowlist in the same Sandbox Operations panel. The persisted tenant policy is authoritative at execution time: `KernelSandboxRuntimeService` carries the list in `SandboxExecutionRequest`, while the container adapter uses it for browser URL private-network validation. A `null` direct adapter request retains the configured runtime fallback; an empty tenant list remains an explicit deny of private-network exceptions.

Fresh evidence: the JDBC schema-upgrade check and container runtime adapter checks passed in the Maven reactor, the full Docker backend image completed its in-image 28-module Maven `BUILD SUCCESS` through the local `192.168.1.9:7890` proxy, and the recreated backend was healthy. PostgreSQL confirmed `sa_sandbox_egress_policy.browser_private_network_allowed_hosts`. The packaged frontend was rebuilt and recreated. Real full-Docker browser E2E passed through `node scripts/e2e-sandbox-tool-quota-page-smoke.mjs --base-url http://127.0.0.1 --username admin --password admin123 --marker seahorse-sandbox-private-network-edit-page-smoke`: it saved `ALLOWLISTED` with one temporary public allowlist host and one temporary private-network exception, verified both via POST response, authenticated API readback, and rendered UI, then restored the default tenant policy to `DENY_ALL` with empty lists. Screenshot: `output/playwright/artifacts/seahorse-sandbox-private-network-edit-page-smoke.png`.

## 2026-07-12 Update: Governed Sandbox Browser Profiles Real E2E

Sandbox browser profiles now provide a tenant-scoped durable reference to a governed captured session-state artifact. They store only profile metadata, expiration, status, and artifact id, never cookie or localStorage values. A profile can reference only a same-tenant `application/json` browser session-state artifact that remains `BLOCKED` and `SECRET`; replay revalidates that governance state plus profile status and expiration before a browser session starts. Direct `sessionState`, `sessionStateArtifactId`, and `browserProfileId` are mutually exclusive, and disabled or expired profiles fail closed.

Persistence uses `sa_sandbox_browser_profile`, `V56__sandbox_browser_profiles.sql`, startup schema repair, RLS, and the JDBC repository. The Sandbox Operations API and UI support listing, upserting, and disabling profiles. Audit summaries contain only `browserProfileReplayRequested`, with neither profile ids nor session values exposed.

Fresh evidence: the backend image rebuilt through `192.168.1.9:7890`, was recreated under the sandbox overlay, and became healthy. `scripts/e2e-sandbox-browser-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-browser-profile-final-smoke -SkipBrowserImageBuild` passed 37/37 against the real full-Docker environment. It created and read back a profile, replayed it through the Tool Gateway, downloaded the governed browser JSON to confirm restored authentication and localStorage without secret/profile-id leakage, disabled it and verified fail-closed behavior, then verified audit summaries, artifact governance, container cleanup, and terminal sessions. The temporary fixture allowlist was restored to `DENY_ALL` with empty public and private-network lists.

## 2026-07-13 Update: Sandbox Browser Profile Operations UI Real E2E

The Browser Profile operations panel is now covered by a deployed-frontend Playwright flow in `scripts/e2e-sandbox-tool-quota-page-smoke.mjs`. Given a real governed browser session-state artifact, the smoke saves a profile through the packaged `/admin/sandbox` page, verifies the authenticated API readback, waits for the asynchronous list refresh, and disables the same profile through the UI. This closes the remaining evidence gap between the Profile API/Tool Gateway lifecycle and the operator surface.

Fresh evidence: `npm run build` passed, the frontend image was rebuilt and `seahorse-frontend` recreated, and a real `sandbox_browser` Docker E2E first generated the input `BLOCKED`/`SECRET` session-state artifact with 37/37 checks passing. `node scripts/e2e-sandbox-tool-quota-page-smoke.mjs --base-url http://127.0.0.1 --username admin --password admin123 --marker seahorse-browser-profile-ui-smoke-final --browser-session-artifact-id <governed-artifact-id>` then passed against the deployed UI, producing `output/playwright/artifacts/seahorse-browser-profile-ui-smoke-final.png`. Temporary profiles were disabled and the default tenant egress policy was restored to `DENY_ALL` with empty lists.

## 2026-07-13 Update: Sandbox Container Linux Privilege Hardening Real E2E

Container sandbox executions now default to `--cap-drop ALL` and `--security-opt no-new-privileges:true`, controlled by the adapter's `drop-all-capabilities` and `no-new-privileges` properties. These constraints apply before the runtime image and writable workspace mount are passed to Docker, preserving the existing bounded workspace contract while preventing ambient Linux capabilities and privilege escalation inside Python, file conversion, and browser containers.

Fresh evidence: the sandbox-container reactor compile passed, the backend image rebuilt through `192.168.1.9:7890`, and the sandbox-overlay backend became healthy. `scripts/e2e-sandbox-python-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-isolation-smoke` passed 5/5 against full Docker. Its code executed inside the managed container and required `/proc/self/status` to report `NoNewPrivs=1` and `CapEff=0000000000000000`, then completed governed artifact creation and Tool Gateway audit verification.

## 2026-07-13 Update: Sandbox Read-Only Root Filesystem Real E2E

The container adapter now defaults to `--read-only` and provides only a bounded `tmpfs` at `/tmp` (`rw,noexec,nosuid,size=64m`) beside the existing writable per-session `/workspace` bind mount. This narrows filesystem mutation to the governed workspace and ephemeral temporary storage without preventing Playwright or Python runtime startup. The behavior is controlled by `read-only-root-filesystem` for deployment configuration.

Fresh evidence: a direct real Docker probe launched Playwright Chromium successfully under the read-only filesystem, capability drop, no-new-privileges, and `/tmp` tmpfs posture. The sandbox-container reactor compile and rebuilt sandbox-overlay backend both passed. `scripts/e2e-sandbox-python-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-sandbox-readonly-smoke` passed 5/5 against full Docker, with code inside the managed container requiring `NoNewPrivs=1`, zero effective capabilities, and `os.access('/', os.W_OK)=false` before writing its governed workspace artifact.

## 2026-07-13 Update: Sandbox Per-Session File Quota Real E2E

The container runtime now enforces `max-session-file-bytes` through Docker `--ulimit fsize`, rounded up to Docker's 512-byte file-size blocks. The default is 64 MiB and is exposed by the sandbox compose overlay. This limits regular file writes by the sandbox process while preserving the existing writable per-session workspace and read-only root posture.

Fresh evidence: adapter compilation passed, the backend image rebuilt through `192.168.1.9:7890`, and the default small governed artifact path passed through real `sandbox_python` Tool Gateway E2E. With the deployed overlay configured to 1024 bytes, a real approved Tool Gateway request writing 4096 bytes failed closed. A direct Docker probe independently confirmed the runtime returns `OSError: File too large`; the backend was finally restored to its default `67108864` byte limit.

## 2026-07-13 Update: Sandbox Isolation Posture Operations Visibility Real E2E

Runtime health now exposes value-free effective isolation posture: `dropAllCapabilities`, `noNewPrivileges`, `readOnlyRootFilesystem`, and `maxSessionFileBytes`. The container adapter remains the source of truth; unsupported runtimes report closed defaults. The Sandbox Operations panel renders these fields alongside health, capacity, and policy, so operators can see the active Linux hardening and per-session file quota without inspecting Docker commands or secret configuration.

Fresh evidence: kernel, Web, and container adapter compilation passed; the backend and packaged frontend images rebuilt and were recreated under the sandbox overlay. `node scripts/e2e-sandbox-tool-quota-page-smoke.mjs --base-url http://127.0.0.1 --username admin --password admin123 --marker seahorse-sandbox-isolation-posture-page-smoke-rerun` passed against the deployed UI, verifying API health reports the enabled posture and `67108864` byte quota, rendered `RO root / no caps / no new privs` and `File quota: 64 MB`, then restored its temporary egress/profile/quota policy changes. Screenshot: `output/playwright/artifacts/seahorse-sandbox-isolation-posture-page-smoke-rerun.png`.

Fresh evidence: the Java reactor compile completed successfully, `JdbcTenantSchemaUpgradeTests` passed 8/8, and `npm run build` passed. The backend image rebuilt explicitly through `HTTP_PROXY=http://192.168.1.9:7890` and `HTTPS_PROXY=http://192.168.1.9:7890`, producing image `sha256:7e059454842fdf6380a11b7004fb7989db43cc076db009b9b080d159ba994879`; `seahorse-backend` was recreated from that image and reached healthy state. PostgreSQL inspection confirmed `sa_sandbox_egress_policy` exists with its tenant unique index, updated index, RLS enabled, and tenant isolation policy. The packaged frontend image rebuilt and `seahorse-frontend` was recreated. Real full-Docker page E2E passed through `node scripts/e2e-sandbox-tool-quota-page-smoke.mjs --base-url http://127.0.0.1 --username admin --password admin123 --marker seahorse-sandbox-egress-policy-edit-page-smoke`, reporting `PASS sandbox tool quota page smoke`, saving `ALLOWLISTED / 1 hosts` through the real UI, reading the saved policy back through API, and writing screenshot `output/playwright/artifacts/seahorse-sandbox-egress-policy-edit-page-smoke.png`. A post-run authenticated API query confirmed the default tenant policy was restored to `DENY_ALL` with an empty allowlist.

## 2026-07-15 Update: Sandbox Per-Session Workspace File Count Guard Real E2E

Sandbox artifact collection now limits each session workspace to 256 regular files. Collection stops after observing the 257th file and fails closed before cumulative-size inspection or artifact publication, preventing a successful sandbox process from turning a large population of tiny files into unbounded host-side collection and governed-artifact work. The limit is independent of the existing 64 MiB cumulative workspace quota and per-file Docker `fsize` limit.

Fresh evidence: `mvnw.cmd package -pl seahorse-agent-bootstrap -am -DskipTests -Dmaven.test.skip=true -Dspotless.check.skip=true` rebuilt the 28-module backend successfully, and the rebuilt jar was deployed into the healthy full-Docker backend. `scripts/e2e-sandbox-python-tool-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-workspace-file-count-e2e-20260715` passed 7/7 against real Docker execution and data. The smoke created 300 one-byte files, required the Tool Gateway call to fail with no observation content or artifact publication, verified the persisted audit status was `FAILED`, and confirmed the audit summary contained neither the submitted probe code nor probe filenames. The same run retained the ordinary governed-artifact success path and the existing 80 MiB cumulative workspace rejection path.

## 2026-07-15 Update: Sandbox Workspace File Count Operations Visibility Real E2E

Sandbox runtime health now exposes the effective `maxSessionWorkspaceFiles` posture from the container adapter, and Sandbox Operations renders it beside the existing aggregate byte quota as `Workspace files: 256 max`. Unsupported runtimes report zero rather than claiming a container limit. This is read-only visibility for the existing enforcement owner; it does not introduce a second configuration source or make the limit tenant-editable.

Fresh evidence: the backend reactor packaged all 28 modules successfully with tests skipped, and the frontend production build completed with only the existing Browserslist and chunk-size warnings. Backend and frontend Docker images were rebuilt through `HTTP_PROXY=http://192.168.1.9:7890` and `HTTPS_PROXY=http://192.168.1.9:7890`, then recreated with the sandbox overlay and external ClamAV scanner healthy. `node scripts/e2e-sandbox-tool-quota-page-smoke.mjs --base-url http://127.0.0.1 --username admin --password admin123 --marker seahorse-sandbox-file-count-visibility-review-fix-20260715 --verify-external-virus-scanner` passed against the deployed page, requiring `runtime=container`, the live health API value `256`, and rendered `256 max`, while saving and restoring the real egress, runtime-profile, and tool-quota policies. Screenshot: `output/playwright/artifacts/seahorse-sandbox-file-count-visibility-review-fix-20260715.png`. The rebuilt backend also passed `scripts/e2e-sandbox-python-tool-smoke.ps1` 7/7, including the real 300-file rejection and value-free failed audit.

## 2026-07-15 Update: Configurable Sandbox Runtime Node Identity Real E2E

The container sandbox now owns an explicit `node-id` deployment property, defaulting to the existing `local-container-docker` identity. Configured ids are limited to 1-64 lowercase letters, numbers, dots, underscores, or hyphens and invalid values fail configuration binding instead of silently creating ambiguous node identities. `SandboxRuntimeHealth` carries this id into the existing `SandboxRuntimeNodeHealth` projection, so admission selection, session persistence, node health, and operator APIs use one source. Legacy runtime adapters that do not provide an id retain the existing derived `local-<runtime>-<engine>` fallback.

This is node-pool foundation only. It does not register multiple nodes, route execution remotely, balance load, migrate sessions, or add failover. The sandbox compose overlay exposes `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_NODE_ID` so separately deployed runtime instances can receive distinct stable identities before distributed registration and placement policy are introduced.

Fresh evidence: the local and in-image backend reactors both packaged all 28 modules successfully, with the backend image rebuilt through `HTTP_PROXY=http://192.168.1.9:7890` and `HTTPS_PROXY=http://192.168.1.9:7890`. The sandbox-overlay backend was recreated with `node-id=sandbox-node-e2e-a` and external ClamAV healthy. `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-configured-runtime-node-id-final-20260715 -ExpectedRuntimeNodeId sandbox-node-e2e-a -VerifyExternalVirusScanner` passed 54/54 against real Docker execution, requiring the node health API, session API, and PostgreSQL `runtime_node_id` to agree on the configured id while retaining governed artifact storage, malware/content/binary/archive/Office scanning, downloads, and cleanup. The first run exposed a stale smoke assumption from before PDF initial-view `/OpenAction` was permitted; the corrected E2E now proves the `/OpenAction` PDF is `CLEAN` and stored while a tail-window `/JavaScript` PDF and executable-masquerading PNG remain `BLOCKED` before object storage. Review then restored both legacy `SandboxRuntimeHealth` constructor signatures and made explicit blank or whitespace-padded node ids fail closed. `javap` confirmed the new and both legacy constructor descriptors, and direct property validation confirmed default/custom ids while rejecting blank and padded values. The final image was rebuilt again, the backend was recreated with the default id, and `seahorse-configured-runtime-node-id-default-final-20260715` passed the same real Docker smoke 54/54 with runtime health and node health reporting `local-container-docker|HEALTHY` and ClamAV healthy.

## 2026-07-15 Update: Configurable Sandbox Runtime Node Drain Real E2E

Container runtime nodes now expose deployment-level `admission-enabled`, defaulting to `true`. Disabling admission leaves engine, workspace, and runtime health inspection active while the node projection reports `DRAINING` and `admissionAvailable=false`. Kernel session admission persists new requests as `FAILED|RUNTIME_NODE_DRAINING` before runtime creation, without assigning a node id or creating a workspace/container. Existing sessions are intentionally unaffected by the admission toggle and can continue executing and close through the normal lifecycle.

The sandbox compose overlay exposes `SEAHORSE_AGENT_ADAPTERS_SANDBOX_CONTAINER_ADMISSION_ENABLED`. This is a real single-node drain primitive and a prerequisite for future node-pool maintenance and placement. It does not add distributed node registration, remote routing, load balancing, migration, automatic failover, or an operator write API.

Fresh evidence: the backend reactor packaged all 28 modules successfully, the frontend production build completed with only the existing warnings, and the backend image rebuilt through `HTTP_PROXY=http://192.168.1.9:7890` and `HTTPS_PROXY=http://192.168.1.9:7890`. With the deployed node configured `admission-enabled=false`, `scripts/e2e-sandbox-runtime-node-drain-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-runtime-node-drain-existing-pass-20260715 -ExpectedRuntimeNodeId local-container-docker -ExistingSessionId <real-session-id>` passed 8/8. The smoke requires a real existing session, healthy engine/workspace/capacity posture beside `DRAINING`, executes and cancels that Python session after backend restart, rejects a new session through the API, verifies `FAILED|RUNTIME_NODE_DRAINING` and no node assignment in PostgreSQL, and confirms no workspace or managed child container was created. The backend was then restored to `admission-enabled=true`; `seahorse-runtime-node-admission-restored-20260715` passed the full artifact-storage Docker regression 54/54 with `admissionEnabled=true`, node admission available, external ClamAV healthy, governed downloads, scanner boundaries, and cleanup. Focused verification also exposed a pre-existing split between runtime admission and node selection that could throw after an unsupported health result passed the first check. Session creation now maps one node-health snapshot to both rejection reason and node assignment, preserving the legacy unsupported no-node path while returning explicit `RUNTIME_NODE_UNAVAILABLE` instead of throwing for unavailable nodes; `KernelSandboxRuntimeServiceTests` passed 46/46 after the convergence.

## 2026-07-15 Update: Required Sandbox Runtime Node Placement Real E2E

Sandbox session creation now accepts an optional `requiredRuntimeNodeId` placement constraint. Requests without the field retain the existing automatic single-node selection behavior. When present, the id must use the same bounded syntax as configured runtime node identities, and the current health snapshot must expose the exact node before normal admission status is evaluated. A matching available node is persisted on the created session; an unknown node, an unsupported runtime that cannot prove an identity, or another mismatch is persisted as `FAILED|RUNTIME_NODE_UNAVAILABLE` without assigning a node or calling the runtime adapter.

This establishes a strict placement contract without claiming a distributed scheduler. It does not add a node registry, heartbeat persistence, remote runtime transport, balancing, migration, failover, or caller-directed execution on unregistered nodes. The runtime adapter remains the execution owner, while the kernel admission snapshot remains the single owner of selection and rejection.

Fresh evidence: focused kernel and Web verification passed with `KernelSandboxRuntimeServiceTests` 48/48 and `SeahorseSandboxControllerTests` 2/2; the frontend production build passed with only the existing Browserslist and chunk-size warnings. The backend image rebuilt through `HTTP_PROXY=http://192.168.1.9:7890` and `HTTPS_PROXY=http://192.168.1.9:7890`, packaging all 28 modules successfully, and the sandbox-overlay backend became healthy with external ClamAV enabled. `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-runtime-node-affinity-review-fix-20260715 -ExpectedRuntimeNodeId local-container-docker -VerifyExternalVirusScanner` passed 56/56 against real Docker execution and PostgreSQL data. The new checks created and cancelled a real session pinned to `local-container-docker`, then required an unknown-node request to persist `FAILED|RUNTIME_NODE_UNAVAILABLE` with no node assignment, workspace, or managed child container; the existing governed artifact, scanner, archive, Office, download, sweep, and cleanup regression remained green. Review preserved the Web request DTO's legacy seven-argument constructor and moved E2E session-id capture before assertions so a malformed success response cannot leave a created session behind. The final image was rebuilt and the same 56/56 real E2E passed after those fixes.

## 2026-07-15 Update: Sandbox Runtime Node Registry and Lease Heartbeat Real E2E

Sandbox runtime nodes now self-register a bounded health summary in the shared JDBC repository and renew a database-clock lease on a scheduled heartbeat. The local `/api/sandbox/runtime/nodes` endpoint remains the source for what the current backend can execute; the admin-only `/api/admin/sandbox/runtime/registrations` endpoint is a separate inventory surface and does not make registered remote nodes schedulable. Registration fields use `observed*` names for the last health snapshot, while `registrationStatus=LIVE|STALE` is calculated against `CURRENT_TIMESTAMP` in the database.

Each process owns its node lease with an internal UUID. A second live process cannot overwrite the same node id; an expired lease can be atomically taken over. Heartbeat and shutdown are serialized so an in-flight heartbeat cannot revive a released lease, and normal Spring/Docker shutdown expires the owned row for immediate restart takeover while preserving stale inventory. Crash recovery still relies on the bounded 45-second default lease. The sandbox compose overlay exposes heartbeat enablement, interval, initial delay, and lease TTL, and startup rejects a lease shorter than twice the heartbeat interval.

This is shared registration and liveness inventory only. It does not add remote runtime transport, automatic placement across registered nodes, load balancing, migration, failover, or stale-row retention cleanup. Explicit session placement still succeeds only for the runtime local to the handling backend.

Fresh evidence: the 28-module backend package and frontend production build passed; JDBC lease ownership/takeover/release and schema idempotency passed 10/10, and frontend endpoint contracts passed 19/19. The backend image rebuilt through `HTTP_PROXY=http://192.168.1.9:7890` and `HTTPS_PROXY=http://192.168.1.9:7890`, then the sandbox-overlay backend became healthy with external ClamAV. `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -NonAdminUsername demo_user_001 -NonAdminPassword demo123 -Marker seahorse-runtime-node-registry-20260715 -ExpectedRuntimeNodeId local-container-docker -VerifyExternalVirusScanner` passed 61/61 against real Docker and PostgreSQL. The new checks required non-admin registry access to return 403, verified the live local registration without owner/workspace leakage, observed the persisted heartbeat advance, inserted and removed a real expired fixture classified as `STALE`, and retained all placement, artifact, scanner, archive, Office, download, sweep, and cleanup coverage. A subsequent forced backend recreation changed the owner UUID and restored the same node as `LIVE` within 15 seconds, proving graceful release and immediate takeover without waiting for lease expiry. Independent review found three P2 issues around shutdown races, host-clock lease decisions, and authorization; all were fixed and the final review found no remaining P1/P2.

## 2026-07-15 Update: Sandbox Remote Runtime Transport Real E2E

The backend can now act as both the sandbox coordinator and a registered runtime node. An explicit `requiredRuntimeNodeId` may resolve to a different LIVE JDBC registration, after which create, execute, artifact transfer, and close are sent through the node's private transport endpoint. The session persists its selected `runtimeNodeId`, so every later operation remains pinned to the same node without a local fallback. Kernel remains the sole owner of policy, session and execution persistence, artifact scanning and object storage, and audit; the remote node owns only the local runtime process, workspace, and bounded artifact-serving window.

The private transport signs node id, process owner id, method, path, timestamp, nonce, and request-body hash with HMAC. HTTPS is required by default; plain HTTP is available only through the explicit `allow-insecure-http=true` development override used by the local Docker network. Public registration APIs expose neither transport URI nor owner identity. Before accepting each operation, the node atomically extends its current JDBC owner lease beyond the configured request timeout, and ordinary heartbeats cannot shorten that operation lease, preventing an expired owner from completing work after a takeover. Coordinator artifact files are released only after their persisted records point at durable object storage; file-backed records remain readable when object storage is absent or an upload fails.

This closes explicit remote execution transport, not automatic scheduling. Node-pool placement, capacity-aware load balancing, migration, retry, and automatic failover remain non-goals for this slice and are the next distributed-runtime roadmap boundary.

Fresh evidence: focused verification passed `KernelSandboxRuntimeServiceTests` 51/51, `SandboxRuntimeTransportAuthenticatorTests` 7/7, and `JdbcSandboxRuntimeNodeRegistryAdapterTests` 1/1, including atomic operation-lease extension, heartbeat preservation, and proof that tampered or replayed requests cannot extend a lease. The backend image packaged the complete 28-module reactor and the identical image was deployed as coordinator `local-container-docker` plus worker `sandbox-node-b`. `scripts/e2e-sandbox-remote-runtime-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -WorkerBaseUrl http://127.0.0.1:19092 -Password admin123 -RemoteNodeId sandbox-node-b -WorkerContainer seahorse-runtime-node-b -Marker seahorse-remote-runtime-auth-order-final-20260715` passed 12/12 against real PostgreSQL, object storage, external artifact scanning, and Docker execution. It proved unsigned transport rejection, two LIVE registrations without private-field leakage, a real Agent Run, remote-only workspace ownership, Python execution, artifact transfer and governed download, pinned close, and managed-resource cleanup. `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -NonAdminUsername demo_user_001 -NonAdminPassword demo123 -Marker seahorse-remote-runtime-auth-order-local-final-20260715 -ExpectedRuntimeNodeId local-container-docker -VerifyExternalVirusScanner` then passed 61/61 on the default local route, preserving the full scanner, storage, policy, placement, download, sweep, and cleanup regression. Independent review drove fixes for HTTPS defaults, nonce lifetime, owner fencing, artifact lifetime, cleanup reliability, and authentication ordering; the final P1/P2 review found no remaining issues.

## 2026-07-16 Update: Sandbox Node-Pool Capacity-Aware Placement Real E2E

Sandbox session requests without `requiredRuntimeNodeId` now consider the complete LIVE runtime-node pool instead of always choosing the coordinator's local runtime. Kernel remains the sole placement-policy owner: it filters out unavailable, draining, disk-low, and saturated registrations, prefers `AVAILABLE` over `DEGRADED`, then orders candidates by configured-capacity utilization, active-session count, workspace free bytes, and node id for deterministic ties. Explicit `requiredRuntimeNodeId` remains a strict override, and the selected node is still persisted on the session so execute and close remain pinned without migration or local fallback.

Heartbeat load is now node-local rather than a copy of the shared database's global active-session count. The container runtime derives ownership from active session workspaces under its own workspace root, so coordinator and worker registrations can report different loads even though they share PostgreSQL and the Docker daemon. JDBC exposes the complete private LIVE endpoint set to Kernel without pre-sorting or truncating it; transport URI and owner identity remain internal and the public registration API is unchanged.

This is snapshot-based capacity-aware placement. It does not add an atomic scheduler slot reservation, queueing, session migration, create retry, automatic failover, or cross-node artifact ownership changes. Runtime admission still rejects races at execution ownership boundaries, while a future node-pool hardening slice may add atomic capacity claims if concurrent placement evidence requires them.

Fresh evidence: `KernelSandboxRuntimeServiceTests` passed 54/54, `JdbcSandboxRuntimeNodeRegistryAdapterTests` passed 1/1, and the node-local container health checks passed 2/2. The backend image packaged all 28 modules with `BUILD SUCCESS`, and coordinator plus worker were recreated from the identical image `sha256:fa110260eaab2482886155ec30bdbf5b407bdc69a1ec286db7b6dcda34a41d5c`. `scripts/e2e-sandbox-remote-runtime-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -WorkerBaseUrl http://127.0.0.1:19092 -Password admin123 -LocalNodeId local-container-docker -RemoteNodeId sandbox-node-b -WorkerContainer seahorse-runtime-node-b -Marker seahorse-node-pool-placement-review-fix-final-20260716` passed 16/16 against real Docker, PostgreSQL, external scanning, and object storage. It created an explicit local load session, waited for node-local heartbeat counts `1/0`, automatically placed the next real session on the less-loaded worker, executed Python and governed artifact transfer there, excluded a real DRAINING registration, restored the worker to `running`, and confirmed both registrations returned to `LIVE|AVAILABLE|0`. `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -NonAdminUsername demo_user_001 -NonAdminPassword demo123 -Marker seahorse-node-pool-placement-review-fix-local-final-20260716 -ExpectedRuntimeNodeId local-container-docker -VerifyExternalVirusScanner` then passed 61/61. Independent review found and closed pre-sort candidate truncation and drain-cleanup pollution risks; the final P1/P2 review found no remaining issues.

## 2026-07-16 Update: Sandbox Runtime Node Stale Registration Cleanup Real E2E

Expired runtime-node registrations now have bounded retention cleanup instead of accumulating indefinitely. JDBC obtains the current timestamp from the database, subtracts the configured retention, and selects the oldest rows whose `expires_at` is at or before that cutoff. Deletion repeats the same cutoff predicate for each selected node, so a heartbeat or expired-lease takeover that revives a candidate between selection and deletion protects the new LIVE registration. Kernel validates a retention range of one minute through 365 days and a batch limit up to 1,000; deployment defaults retain stale rows for seven days and remove at most 100 per hourly pass.

The scheduled cleanup runs under the existing `DistributedLockPort` with `job:sandbox-runtime-node-cleanup`, preventing coordinator and worker from duplicating a pass when shared locking is available. The sandbox overlay exposes enablement, initial delay, fixed delay, retention, and batch limit. Cleanup remains inventory maintenance only: it does not reserve scheduler capacity, queue requests, migrate sessions, retry creates, or provide automatic failover.

Fresh evidence: focused reactor verification passed `JdbcSandboxRuntimeNodeRegistryAdapterTests` 1/1 plus `SandboxRuntimeNodeCleanupJobTests` and `SeahorseAgentSandboxAutoConfigurationTests` 9/9 with `BUILD SUCCESS`. The full backend image rebuilt through `HTTP_PROXY=http://192.168.1.9:7890` and `HTTPS_PROXY=http://192.168.1.9:7890`, packaging all 28 modules successfully, and coordinator plus worker ran the identical image `sha256:b4b57287ec3e9935d372cb98a9de7f087db5ddbed75d5a29bd98464417312b4c`. With a temporary 60-second retention and three-second schedule, `scripts/e2e-sandbox-remote-runtime-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -WorkerBaseUrl http://127.0.0.1:19092 -Password admin123 -Marker seahorse-node-cleanup-e2e -VerifyStaleNodeCleanup` passed 18/18 against real PostgreSQL and Docker: a registration expired for two minutes was removed, a registration expired for 30 seconds remained, both real nodes remained LIVE, and remote placement, execution, artifact transfer, draining exclusion, and cleanup stayed green. `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -BaseUrl http://127.0.0.1:9090 -Password admin123 -Marker seahorse-node-cleanup-local-regression` then passed 58/58 on the local execution and governed storage path. PostgreSQL finished with no E2E stale fixtures, no non-terminal sandbox sessions, and both real registrations `LIVE|0`; independent P1/P2 review found no remaining issues.

## 2026-07-16 Update: Sandbox Node-Pool Atomic Capacity Reservation Real E2E

Finite-capacity sandbox nodes now reserve a scheduler slot atomically before Kernel creates a local or remote runtime. JDBC uses the database clock, locks the selected runtime-node row, removes that node's expired reservations, and evaluates `max(heartbeat active sessions, persisted non-terminal sessions) + active reservations` in one transaction. A node with `active_session_limit <= 0` remains unlimited and does not need a reservation. Successful claims receive a five-minute lease so process death cannot hold capacity indefinitely, while every normal success, rejection, persistence failure, transport failure, and candidate-fallback branch releases its claim explicitly.

Kernel remains the placement and lifecycle owner. Explicit node requests fail with `RUNTIME_CAPACITY_EXCEEDED` when their finite node cannot be reserved; automatic placement tries the next ranked candidate instead of failing on the first saturated node. Runtime creation still precedes session persistence, but repository-save failure now closes the runtime and releases the claim, while a later audit failure does not tear down a session that is already durably owned. Persisted non-terminal sessions continue to count even when heartbeat load temporarily lags, and heartbeat load remains a conservative lower bound when a runtime exists before session persistence.

V57 adds `sa_sandbox_runtime_capacity_reservation` with its `(node_id, expires_at)` cleanup index. New and upgraded databases also receive `idx_sa_sandbox_session_runtime_node_status`, matching the persisted-capacity query and preventing node reservations from degrading into full session-table scans. Node-row locking is consistently acquired before reservation-row cleanup, preserving one lock order across capacity reservation, release, and stale-node deletion.

This closes atomic admission for session creation. It does not add queueing, fairness, distributed priority, session migration, automatic retry after execution begins, cross-node failover, or capacity preemption.

Fresh evidence: focused reactor verification passed `KernelSandboxRuntimeServiceTests` 61/61, `JdbcSandboxRuntimeNodeRegistryAdapterTests` 2/2, `JdbcTenantSchemaUpgradeTests` 9/9, and `SeahorseAgentSandboxAutoConfigurationTests` 6/6 across a 23-module focused reactor. The complete 32-module package finished with `BUILD SUCCESS`; coordinator `local-container-docker` and worker `sandbox-node-b` ran the identical production-runtime image `sha256:2ca523062efa5753cc6fe62e772420f2c3f9ac0c095edfd26eb0400a83039d3e` with Docker CLI 27.5.1. Real PostgreSQL `EXPLAIN (ANALYZE, BUFFERS)` used `Index Only Scan using idx_sa_sandbox_session_runtime_node_status` for the non-terminal session count. `scripts/e2e-sandbox-remote-runtime-smoke.ps1 -VerifyAtomicCapacityReservation` passed 20/20 against real APIs, PostgreSQL, Docker child containers, remote execution, governed artifact transfer, slot release/reuse, automatic placement, and draining recovery. Its concurrency gate paused the real worker, observed `reservation=1` with zero persisted non-terminal sessions, and required the second capacity rejection to persist before the first request could complete. `scripts/e2e-sandbox-artifact-storage-smoke.ps1` then passed 58/58 on the local execution, scanner-policy, archive, Office, object-storage, download, sweep, and cleanup paths. Independent review found and closed E2E overlap-proof and partial-response cleanup risks; the final P1/P2 review found no remaining issues. The environment finished with no active reservations, no non-terminal sessions, no managed child containers, and both runtime nodes healthy with the default unlimited capacity `0`.

## 2026-07-17 Update: Coordinator-Owned Sandbox Session Identity Real E2E

Kernel now allocates the sandbox session identity before any local or remote runtime create call and carries that identity in `SandboxSessionRequest`. The same id is used for runtime-failure persistence, so ambiguous transport failures no longer split coordinator and runtime ownership across unrelated ids. A null runtime response or a response with a different id fails closed; the mismatch path attempts compensating close and releases a finite-capacity reservation only after cleanup is confirmed. The runtime session is still persisted only by Kernel.

The transport contract remains backward compatible. Legacy JSON and the existing five- and seven-argument `SandboxSessionRequest` constructors leave `sessionId` absent, while the container adapter retains its legacy `sandbox_container_` generator only for those callers. Coordinator-assigned ids use the bounded `[A-Za-z0-9._-]{1,128}` contract, preventing path traversal before a workspace is resolved. Container orphan cleanup recognizes both the coordinator `sandbox_` namespace and legacy `sandbox_container_` workspaces; active ids remain protected and stale managed workspaces remain removable.

This is the identity and reconciliation foundation for future create failover, not failover itself. A remote create timeout is still treated as ambiguous and is not retried blindly on another node. Cross-node retry remains deferred until cleanup or reconciliation can prove that the first node no longer owns the coordinator-assigned id.

Fresh evidence: focused reactor verification passed `KernelSandboxRuntimeServiceTests` 63/63, `SandboxRuntimeTransportAuthenticatorTests` 8/8, and the coordinator-id, unsafe-id, close, and managed-workspace container checks 4/4 with `BUILD SUCCESS`. The production image packaged all 28 runtime modules through the `192.168.1.9:7890` proxy, and coordinator plus worker ran the identical image `sha256:17f004d339b99da03eaf6e07987abe5c019107395daa337d3facfd8e9a2147dd`. `scripts/e2e-sandbox-remote-runtime-smoke.ps1 -Marker coord-id-final-0717` passed 20/20 against real APIs, PostgreSQL, Docker workspaces, remote Python execution, governed artifact transfer, and cleanup. Its signed duplicate-create check read the live worker lease owner from PostgreSQL, signed the exact request body with the deployed transport credential, repeated create with the persisted coordinator id, and required the same response id, exactly one worker workspace, and exactly one coordinator session row. The smoke also created a real remote session without retaining its id in an in-memory cleanup variable, then discovered it by run id in PostgreSQL and converged the public session plus runtime workspace in `finally`. `scripts/e2e-sandbox-artifact-storage-smoke.ps1 -Marker coord-id-local-final-0717 -ExpectedRuntimeNodeId local-container-docker` then passed 58/58, including the real orphan sweep that exposed and verified the coordinator-prefix cleanup fix, plus scanner, archive, Office, object-storage, download, and cleanup regressions. Independent review found and closed premature reservation release after ambiguous create, cleanup of an untrusted returned id, unconfirmed close responses, silent workspace-deletion failure, and partial-response E2E cleanup risks; the final P1/P2 review found no remaining issues.

## 2026-07-17 Update: Remote Sandbox Create Reconciliation Real E2E

Remote runtime transport now exposes a signed session-ownership query bound to the coordinator-assigned session id. The contract reports `OWNED`, `ABSENT`, or `UNSUPPORTED`; request and response ids use the existing bounded session-id syntax, and the HTTP adapter rejects a response for a different id. The container adapter remains the runtime ownership source and reports `OWNED` only while the exact session workspace directory exists. Legacy local and remote port implementations retain binary/source compatibility through the default `UNSUPPORTED` result.

Kernel now reconciles the originally selected remote node after an ambiguous create failure. A confirmed `ABSENT` result permits immediate reservation release without a close request. `OWNED` requires compensating close of the same coordinator id and the existing matching-id plus `CANCELLED` confirmation before release. `UNSUPPORTED`, a malformed response, or a query failure retains the compatibility close path, but still keeps the finite-capacity reservation until cleanup is confirmed or its bounded lease expires. Kernel remains the sole session, placement, lifecycle, and reservation owner.

This is create-outcome reconciliation, not create failover. Kernel does not retry on another node, migrate a session, or introduce a second identity owner. A later bounded failover slice may reuse the same coordinator id only after this contract proves the first node absent or confirms cleanup; explicit node placement and any execution that has started remain pinned.

Fresh evidence: focused verification passed `KernelSandboxRuntimeServiceTests` 65/65, `SandboxRuntimeTransportAuthenticatorTests` 9/9, and the container coordinator-id, unsafe-id, close, and ownership checks 4/4. The complete 28-module production package finished with `BUILD SUCCESS`; the backend image rebuilt through `HTTP_PROXY=http://192.168.1.9:7890` and `HTTPS_PROXY=http://192.168.1.9:7890`, and coordinator plus worker ran the identical image `sha256:77cca6f41000d941f94ebc064859c37bac63b45145a4dcaf40f4fecb08a66903`. `scripts/e2e-sandbox-create-reconciliation-smoke.ps1 -Marker create-reconciliation-review-fix-final-0717` passed 9/9 against real Docker and PostgreSQL. Its one-shot proxy forwarded the real worker create and discarded the response, observed `OWNED`, then held the successful close response while PostgreSQL still showed `reservation=1` and no persisted session. Releasing that response produced exactly one `FAILED|RUNTIME_NODE_UNAVAILABLE` coordinator session, released the reservation, and left no retry, workspace, child container, proxy, transport-URI override, capacity override, or non-terminal-session residue. The normal remote runtime smoke then passed 20/20 and the local artifact/storage regression passed 58/58. Independent review found and closed false-positive create-count and post-`finally` restoration gaps; the final production P1/P2 review found no remaining issues.

## 2026-07-17 Update: Bounded Sandbox Runtime Create Failover Real E2E

Automatic sandbox placement now freezes one ranked candidate snapshot before runtime creation. Capacity reservation remains just-in-time and atomic for each candidate, so a saturated candidate can still be skipped without consuming a runtime-create attempt. Actual runtime creation is limited to two calls: the initial candidate and at most one failover candidate. Both calls use the same coordinator-owned session id, and only the successfully persisted runtime node becomes the session's `runtime_node_id`.

Failover is restricted to remote pre-persistence create failures with strong cleanup evidence. A remote `ABSENT` result permits the next candidate; `OWNED` permits it only after same-id close returns matching `CANCELLED`. Explicit `requiredRuntimeNodeId`, legacy `UNSUPPORTED`, ownership-query failure, a mismatched create response id, local runtime create failure, repository persistence failure, and every execution or close path remain fail closed without node switching. A failed first-node reservation is released only after the same reconciliation rules used by the previous slice; its bounded lease remains the recovery path when cleanup cannot be confirmed.

This is one bounded create failover, not retry-until-success, queueing, fairness, session migration, execution retry, or capacity preemption. Candidate health is a placement-time snapshot; if the single failover candidate becomes stale, the request fails normally rather than refreshing the pool into an unbounded retry chain. The persisted session records only the final runtime node. The operations-evidence slice below adds the deferred value-bounded audit event; no raw transport error or endpoint is persisted here.

Fresh evidence: `KernelSandboxRuntimeServiceTests` passed 71/71, including same-id automatic failover, explicit-node no-failover, two-create hard bound, legacy/query-failure no-failover, mismatched-id no-failover, local-create no-failover, and existing atomic-capacity behavior. The complete 28-module package finished with `BUILD SUCCESS`. Coordinator and worker ran the identical rebuilt image `sha256:c47c720beb62c9d1eb3331802f5ed9b68ccbcd86b076da41c022827a805241d1`. `scripts/e2e-sandbox-create-reconciliation-smoke.ps1 -VerifyAutomaticFailover -Marker automatic-create-failover-final-image-0717` passed 11/11 against real Docker and PostgreSQL: a local load made the worker the first automatic candidate, the proxy discarded the worker create response, ownership returned `OWNED`, the worker workspace was closed while its finite reservation remained held, and the same session id was then created, persisted, executed, and closed on the coordinator. The explicit-node mode passed 9/9 and remained `FAILED|RUNTIME_NODE_UNAVAILABLE` without switching. The normal remote runtime regression passed 20/20, and the local artifact/storage regression passed 58/58 with the worker temporarily drained and then restored. Independent review found and closed an abnormal-path local-load cleanup gap; final P1/P2 review found no remaining issues. The environment finished with both registrations `AVAILABLE|0`, no reservation, no non-terminal session, no managed child container, no workspace, and no fault proxy.

## 2026-07-18 Update: Bounded Sandbox Create Failover Audit Evidence

Kernel now emits `SANDBOX_RUNTIME_CREATE_FAILED_OVER` exactly once after an automatic second-node create succeeds and the coordinator session is durably persisted. The event uses the ordinary `SANDBOX_SESSION` resource correlation, so the coordinator-owned session id remains in `resourceId` instead of being duplicated under the globally credential-sensitive `sessionId` payload key. Its payload is serialized from a four-field allowlist: `fromNodeId`, `toNodeId`, `recovery=ABSENT|CLOSED`, and `attemptCount=2`.

The event is not emitted for explicit placement, failed second attempts, unsupported or failed ownership reconciliation, mismatched runtime ids, local-create failures, or persistence failures. It contains no transport URI, endpoint, exception text, shared secret, or caller input. Audit append still follows the existing post-persistence session-audit semantics: a fail-closed audit write can fail the request after persistence, but cannot close or discard the already owned runtime session.

Fresh evidence: `KernelSandboxRuntimeServiceTests` and `AuditEventTests` passed 74/74, including both `ABSENT` and confirmed-close `CLOSED` recovery payloads. The complete 28-module production package finished with `BUILD SUCCESS`; coordinator and worker ran the identical image `sha256:8e2bd25fab6efa5b5b5a6d2b7fbc5d577f0b688247f2fd81efcd40a36d2138a5`. `scripts/e2e-sandbox-create-reconciliation-smoke.ps1 -VerifyAutomaticFailover -Marker seahorse-failover-audit-auto-rerun-20260718` passed 12/12 against real Docker, PostgreSQL, the public audit API, and the one-shot response-loss proxy. It required one `CLOSED` event with the exact node direction and attempt count in both API and database views, rejected every transport-detail marker, then executed and closed the same session on the failover node. Explicit placement passed 10/10 and produced zero failover events in both views. The environment finished with both registrations `LIVE|AVAILABLE|0`, no reservation, no non-terminal session, no workspace, no managed child container, and no fault proxy.

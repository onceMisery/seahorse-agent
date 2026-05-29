# Seahorse Agent 项目交接文档

创建日期：2026-05-28（最近一次按当前工作树校对：2026-05-29）
当前基线：`main` 分支 `ba1ef88e feat(frontend): add citation hover, resizable artifact panel, eval candidate actions` + 本轮后续功能实现

> 维护约定：每次有 `feat`/重要 `test` 提交合入 main 后，**必须**同步更新 §7 / §8 / §16 / §17。否则该卡视为未完成。

---

## 1. 这份文档给谁看

面向**完全没接触过本项目**的开发者。目标：不依赖任何历史聊天记录，能理解项目定位、架构、已完成能力、未完成能力、关键代码入口，并按顺序继续开发。

如果你只记住一件事：**Seahorse Agent 当前主线是 C 端 Web 云端 Agent 产品**，不是本地安装 Agent，不是企业 Agent mesh 平台，也不是任意 MCP/连接器执行平台。

---

## 2. 项目定位与未来目标

### 2.1 当前定位

Seahorse Agent 是一个基于 Spring Boot 3.5.7 的 RAG 智能体平台，采用六边形架构（端口-适配器模式），面向 C 端 Web 用户提供：

- 浏览器中发起长任务（聊天、深度研究、网页总结）
- 实时看到任务进度、来源、产物、审批、成本和失败状态
- Web 研究任务能搜索、抓取、提炼证据、生成带引用的 Markdown 报告
- 用户能管理记忆、上传文件、选择任务模板、反馈质量问题
- 系统能把反馈、成本、模型路由、额度、安全策略形成运营闭环

### 2.2 未来目标（按优先级）

P0（产品体验关键缺失，2026-05-29 已闭合）：

1. **CitationVerifier 强校验** — 已让缺引用的 VERIFY_CITATIONS 进入失败/重试路径，并由 `CitationVerifierTests` 覆盖
2. **ModelRoutingPolicy 配置化** — 已迁到 `seahorse-agent.routing.tiers.*` / fallback chain 配置，并由 `ModelRoutingPolicyConfigDrivenTests` 与 Spring 自动配置测试覆盖

P1（已落地能力的深度补齐，2026-05-29 已闭合）：

3. **限流维度补齐** — 已补 userId、taskTemplateId、search/fetch 总量和高成本任务并发维度
4. **Eval 回归运行 UI** — 已补管理端触发入口、维度化结果和 baseline/current 对比
5. **WebSource Trust Level 评估** — 已补 `SourceTrustEvaluator`、后端排序/抓取优先级和前端 trust 标签

P2（边界加固与文档对齐，2026-05-29 已闭合）：

6. **Phase 4 boundary 测试矩阵** — 已补 `SandboxApiDisabledByDefaultTests` / `A2ATriggerRejectedInConsumerWebTests` / `SandboxArtifactToAgentArtifactPolicyTests` / `ConnectorAdminOnlyTests`
7. **附件解析器覆盖** — 已用 Tika adapter 测试覆盖 PDF/DOCX/XLSX 专用解析路径，并补 ContextPack 附件测试
8. **企业版历史目录声明** — 已补历史/高级扩展范围声明，C 端主线仍以本文件为事实源

### 2.3 明确的非目标

- 本地安装 Agent / 浏览器直接读写本机文件系统
- 宿主机 shell/bash 执行
- 用户自定义任意 MCP server 并直接执行
- 默认开放 A2A、Agent Mesh、Remote Agent Card
- 通用工作流引擎或复杂 YAML/JSON DSL

---

## 3. 技术栈

### 3.1 后端

| 技术 | 版本 | 用途 |
|------|------|------|
| Java | 17 | 语言 |
| Spring Boot | 3.5.7 | 框架 |
| Jackson | 2.19.2 | JSON 序列化 |
| PostgreSQL | 15+ | 主数据库 |
| Redis | 7+ | 缓存 |
| Milvus | 2.3+ | 向量库 |
| Pulsar | 3+ | 消息队列 |
| Elasticsearch | 8+ | 全文搜索 |
| MinIO | — | 对象存储 |
| JUnit 5 | — | 测试 |

### 3.2 前端

| 技术 | 用途 |
|------|------|
| React 18 | UI 框架 |
| Vite | 构建工具 |
| TypeScript | 语言 |
| Zustand | 状态管理 |
| React Router | 路由 |
| Radix UI | 组件库 |
| lucide-react | 图标 |

---

## 4. 模块结构

```
seahorse-agent/
├── seahorse-agent-kernel/                  # 领域核心（纯 Java，不依赖 Spring/JDBC/Web）
│   ├── domain/agent/research/              # 研究领域对象
│   ├── application/agent/research/         # 研究应用服务（编排器、步骤处理器）
│   ├── ports/inbound/                      # 入站端口（Controller 调用的接口）
│   └── ports/outbound/                     # 出站端口（基础设施抽象）
├── seahorse-agent-adapter-web/             # Web 适配器（Controller、SSE、DTO）
├── seahorse-agent-adapter-repository-jdbc/ # JDBC 持久化适配器
├── seahorse-agent-adapter-ai-openai-compatible/ # AI 模型适配器
├── seahorse-agent-adapter-mcp-http/        # MCP HTTP 适配器（C端默认关闭）
├── seahorse-agent-adapter-cache-redis/     # Redis 缓存适配器
├── seahorse-agent-adapter-cache-local/     # 本地缓存适配器
├── seahorse-agent-adapter-mq-pulsar/       # Pulsar MQ 适配器
├── seahorse-agent-adapter-mq-direct/       # 直连 MQ 适配器（开发用）
├── seahorse-agent-spring-boot-starter/     # Spring 自动配置（装配所有 bean）
├── seahorse-agent-bootstrap/               # 启动入口
├── seahorse-agent-tests/                   # 跨模块集成测试
├── frontend/                               # React 前端
├── resources/database/                     # SQL 初始化脚本
└── docs/company-agent/c-web-ai-infra-phases/ # 阶段设计文档（必读）
```

---

## 5. 架构原则（不可违反）

### 5.1 六边形架构边界

- **kernel 只依赖 port 抽象**，不依赖 Spring、JDBC、Web、HTTP DTO
- Web Controller 只做协议转换 + 权限门禁 + 调用 inbound port
- JDBC adapter 实现 outbound repository port，不泄漏 SQL 细节到 kernel
- Spring starter 只负责装配，不放业务规则

### 5.2 Controller 模式

所有 Controller 使用 `ObjectProvider<T>` 懒加载：

```java
private final ObjectProvider<SomePort> somePortProvider;

public SomeController(ObjectProvider<SomePort> somePortProvider) {
    this.somePortProvider = somePortProvider;  // 不在构造器调用 getIfAvailable()
}
```

### 5.3 自动配置分层

`AutoConfiguration.imports` 分 7 层，kernel 子配置必须在 `@AutoConfigureAfter` 中声明所有依赖的 adapter 配置。详见该文件注释。

### 5.4 C 端安全边界

- 外部网页内容标记为 `UNTRUSTED_EXTERNAL_CONTENT`，不作为系统指令执行
- WebFetch 禁止访问内网 IP / localhost / metadata endpoint
- Artifact 下载经过下载决策，不暴露 storageRef
- consumer-web 模式默认关闭高级能力（AdvancedFeatureGate）
- 研究任务受 quota 限制（搜索次数、抓取页面数）
- connector、secret、sandbox、handoff、remote agent、MCP 管理入口默认关闭或 admin only

---

## 6. 环境搭建

### 6.1 前置要求

- JDK 17+
- Node.js 18+（前端）
- Docker + Docker Compose（中间件）
- Git

### 6.2 后端构建

```bash
# 编译（跳过测试和格式检查，快速验证）
./mvnw package -B -pl seahorse-agent-bootstrap -am -DskipTests -Dspotless.check.skip=true

# 运行测试
./mvnw test

# 只跑研究相关测试
./mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-tests -am test "-Dtest=*Research*,*Citation*"
```

### 6.3 前端构建

```bash
cd frontend
npm install
npm run build    # 生产构建
npm run dev      # 开发服务器
```

### 6.4 中间件启动

```bash
# 全量部署（PostgreSQL + Redis + Milvus + Pulsar + ES + MinIO）
docker compose -f docker-compose.full.yml up -d
```

### 6.5 适配器配置（环境变量）

| 适配器 | 环境变量前缀 | 类型选项 |
|--------|-------------|---------|
| 向量库 | `SEAHORSE_AGENT_ADAPTERS_VECTOR_` | milvus, pgvector, noop |
| 缓存 | `SEAHORSE_AGENT_ADAPTERS_CACHE_` | redis, local |
| 存储 | `SEAHORSE_AGENT_ADAPTERS_STORAGE_` | s3, local |
| 消息队列 | `SEAHORSE_AGENT_ADAPTERS_MQ_` | pulsar, direct |
| 搜索 | `SEAHORSE_AGENT_ADAPTERS_SEARCH_` | elasticsearch, lucene |
| AI 模型 | `SEAHORSE_AGENT_ADAPTERS_AI_` | openai-compatible |

---

## 7. 已完成能力清单

以下能力在当前基线 `ba1ef88e` 已经在主干上可用。每行末尾给出关键证据文件，便于追溯。

### 7.1 Research Web Agent 后端编排（Phase 2 核心）

完整的 7 步研究管线：PLAN → SEARCH → FETCH → EXTRACT_EVIDENCE → SYNTHESIZE → WRITE_REPORT → VERIFY_CITATIONS。

| 组件 | 文件 | 状态 |
|------|------|------|
| 编排器 | `kernel/application/agent/research/ResearchRunOrchestrator.java` | 完成 |
| 上下文持久化 | `kernel/application/agent/research/ResearchStepContext.java` | 完成（toJson/fromJson） |
| 计划步骤 | `kernel/application/agent/research/PlanStepHandler.java` | 完成 |
| 搜索步骤 | `kernel/application/agent/research/SearchStepHandler.java` | 完成 |
| 抓取步骤 | `kernel/application/agent/research/FetchStepHandler.java` | 完成 |
| 证据提取 | `kernel/application/agent/research/ExtractEvidenceStepHandler.java` | 完成 |
| 综合分析 | `kernel/application/agent/research/SynthesizeStepHandler.java` | 完成 |
| 报告撰写 | `kernel/application/agent/research/WriteReportStepHandler.java` | 完成（产出 AgentArtifact） |
| 引用校验 | `kernel/application/agent/research/CitationVerifier.java` | 完成 |
| 入站端口 | `ports/inbound/agent/ResearchInboundPort.java` | 完成 |
| 入站服务 | `kernel/application/agent/research/KernelResearchInboundService.java` | 完成 |
| SSE 桥接 | `adapter-web/ResearchSseBridge.java` | 完成 |
| Worker Job | `spring/SeahorseResearchWorkerJob.java` | 完成 |
| 自动配置 | `spring/SeahorseAgentKernelResearchAutoConfiguration.java` | 完成 |

**领域对象**：

| 对象 | 文件 |
|------|------|
| `ResearchStepType` | `kernel/domain/agent/research/ResearchStepType.java` |
| `ResearchTaskProfile` | `kernel/domain/agent/research/ResearchTaskProfile.java` |
| `WebSource` | `kernel/domain/agent/research/WebSource.java` |
| `EvidenceItem` | `kernel/domain/agent/research/EvidenceItem.java` |
| `SourceTrustLevel` | `kernel/domain/agent/research/SourceTrustLevel.java` |
| `ExtractionStatus` | `kernel/domain/agent/research/ExtractionStatus.java` |

**测试覆盖**（19 个测试全部通过）：

| 测试类 | 覆盖点 |
|--------|--------|
| `ResearchStepContextSerializationTests` | JSON 序列化往返、null 处理、seq 保持 |
| `ResearchRunOrchestratorTests` | 7 步流转、eventSeq 单调递增、重试、上下文恢复 |
| `CitationVerifierTests` | 引用完整/缺失/多余/空报告/重复引用 |

> 2026-05-29 更新：`CitationVerifier` 缺引用强校验、`SourceTrustLevel` 评分、`contentHash` 去重/缓存命中、search/fetch 额度边界已补齐。证据见 §8.2 / §8.4 / §8.7。

### 7.2 Web 长任务体验（Phase 1）

- `StreamEventEnvelope` — 统一事件封装（eventId/eventSeq/eventType/runId/stepId/timestamp/typedPayload）— `kernel/domain/stream/StreamEventEnvelope.java`
- 事件缓冲 JDBC 实现 — `JdbcAgentRunEventBufferAdapter.java`（含 `sa_agent_run_event_buffer` 表 + `append/getAfter/getLatestSeq/expire/expireOlderThan`）
- SSE 断线恢复：前端用当前 assistant 消息的 `agentRunId/lastEventSeq` 构造 `/rag/v3/chat?resumeRunId=&lastEventSeq=`；后端先补发 missed events，未遇到 `FINISH` 时通过 `ResearchSseBridge.attach(..., afterSeq)` 继续接实时流；buffer 过期时发送 `run_snapshot`，若 `snapshot.canResume=true` 则从 `snapshot.lastEventSeq` 继续接流 — `SeahorseChatController.java:270-321`、`ResearchSseBridge.java:73-156`
- Run Snapshot + Cost Summary API — `KernelAgentRunSnapshotService` + `KernelAgentRunCostSummaryService` + `SeahorseAgentRunController`
- `AgentTracePanel` — 前端时间线展示（step/source/artifact/approval/quota/cost）
- `ApprovalCard` — 用户 inline approve/reject/modify
- Artifact 增量协议 — `ARTIFACT_START / ARTIFACT_CONTENT / ARTIFACT_END` 经 `StreamEventEnvelope` 推送，`WriteReportStepHandler` 支持流式与阻塞兼容路径

### 7.3 前端研究体验（已闭合）

- 任务模板选择器（快速回答 / 深度研究 / 网页摘要 / 对比分析）— `ChatInput.tsx:404` 模板下拉
- **研究步骤中文标签** — `AgentTracePanel.tsx:10-23` `RESEARCH_STEP_LABELS`（PLAN→规划研究方向 … VERIFY_CITATIONS→验证引用）
- `artifact_created` + `artifact_start/content/end` 事件解析 — `chatStreamUtils.ts`
- **Artifact 独立面板**（已支持拖拽 resize、流式生成态、移动端底部抽屉）— `frontend/src/components/chat/ArtifactPanel.tsx` + `ChatPage.tsx`
- **引用 hover 卡片** — 提交 `ba1ef88e`

### 7.4 Artifact 闭环

- `AgentArtifact` 领域模型 + JDBC 持久化 — `kernel/domain/agent/artifact/` + `JdbcAgentArtifactRepositoryAdapter`
- API：查询/列表/下载 — `SeahorseAgentArtifactController`
- 前端 artifact service + `ArtifactSandbox` + `ArtifactPanel` 渲染
- `WriteReportStepHandler` 自动产出 MARKDOWN_REPORT 类型 Artifact，并在同一 `artifactId` 上发布 START / CONTENT delta / END 生命周期事件

### 7.5 附件全链路（含解析入 ContextPack）

- 上传/列表/删除 API — `SeahorseConversationAttachmentController`
- `StreamChatCommand.attachmentIds` 透传 + 前端 `ChatInput` 支持上传/删除/展示 parseStatus
- **异步解析服务**（PENDING→PARSED/FAILED/BLOCKED + 10MB 上限 + MIME 黑名单）— `kernel/application/conversation/ConversationAttachmentParserService.java`
- **附件内容真正写入 ContextPack**（含 `belongsTo` ACL、CONFIDENTIAL 敏感度、4000 字截断、citation JSON）— `kernel/application/chat/ConversationAttachmentContextAssembler.java`
- 集成测试 — `KernelChatPipelineTests`（提交 `10a3f782`）

> 2026-05-29 更新：PDF/DOCX/XLSX 已由 Tika parser 专项测试覆盖；附件 ContextPack 写入路径由 `ConversationAttachmentContextAssemblerTests` 覆盖。证据见 §8.6。

### 7.6 任务模板与额度

- 任务模板查询 API — `KernelTaskTemplateQueryService`
- 用户 quota summary + run cost summary — `KernelQuotaSummaryService` + `KernelAgentRunCostSummaryService`
- 前端输入框展示模板选择、额度状态、高成本确认 — `ChatInput.tsx`

### 7.7 模型路由（基础版）

- **`ModelRoutingPolicy`** — `kernel/application/agent/routing/ModelRoutingPolicy.java`：按 `templateModelTier` + `remainingQuota` + `contextTokens` 选择档位 + 降级原因（`ModelSelection`）

> 2026-05-29 更新：模型档位已改为 `ModelRoutingProperties` / `RoutingProperties` 配置驱动，Spring 自动配置已覆盖绑定路径。证据见 §8.3。

### 7.8 滥用防护（基础版）

- `RateLimiterPort` + Redis/Local 双实现 — `ports/outbound/cache/RateLimiterPort.java` + `RedisCacheAdapter` + `LocalCacheAdapter`
- **`RateLimitFilter`** — `seahorse-agent-adapter-web/.../RateLimitFilter.java`（IP 120/min + upload 50/h，超限返回 429）
- Chat 入口 rate limit — `SeahorseChatController` 的 `chat-rate-limit.permits` / `window-ms` 配置项

> 2026-05-29 更新：已补 user/template/search/fetch/高成本并发维度，`SeahorseChatControllerRateLimitTests` 覆盖 chat 入口限流。证据见 §8.4。

### 7.9 用户记忆中心

- 记忆列表/删除/隐私模式开关 — `KernelUserMemoryPrivacyService`
- 前端 `/memories` 页面 — `MemoryCenterPage.tsx`

### 7.10 反馈与评测闭环

- 点踩原因/评论采集 — `KernelMessageFeedbackService` + `FeedbackButtons.tsx`
- 反馈候选查询 API — `KernelFeedbackEvaluationCandidateQueryService`
- **Eval 决策（accept/reject + dataset 推送）** — `kernel/application/agent/eval/KernelEvalCandidateDecisionService.java`（提交 `cb3b8c3b`）
- **Eval 回归运行** — `KernelEvalRegressionService.java`
- 管理端 evaluation candidate accept/reject UI — `AiInfraConsolePage.tsx`（提交 `ba1ef88e`）
- JDBC 持久化 + 测试 — 提交 `cb3b8c3b`、`0a50ca51`

> 2026-05-29 更新：管理端已补回归运行入口与 baseline/current 对比；`KernelEvalRegressionServiceTests` 与 `SeahorseEvalCandidateDecisionControllerTests` 覆盖服务和 Web 决策入口。证据见 §8.5。

### 7.11 产品模式门禁（Phase 4 边界）

- `ProductMode` / `AdvancedFeature` / `AdvancedFeatureGate` — `seahorse-agent-adapter-web/.../`
- consumer-web 默认关闭 sandbox / connector / handoff / remote agent
- 前端 `productMode.ts` 隐藏高级入口
- A2A trigger 受 `SeahorseChatController.java:60-63` `CONTROLLED_WEB_AGENT_TEMPLATES` 限制

> 2026-05-29 更新：consumer-web 默认关闭、A2A trigger 拒绝、SandboxArtifact→AgentArtifact 出口策略已补测试矩阵。证据见 §8.8。

---

## 8. 后续能力验收记录（原未完成清单）

本节保留原 §8 未完成清单的验收记录。状态截至 2026-05-29：P0/P1 功能缺口已闭合；后续只剩性能、产品化和更大范围运营能力优化，不再阻塞「C 端 Web AI Infra 后续功能完整实现」判定。

### 8.1 Artifact 流式增量协议（P0，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- 后端：`StreamEventType` 新增 `ARTIFACT_START / ARTIFACT_CONTENT / ARTIFACT_END`；`ResearchStepHandler` 支持事件发布器；`WriteReportStepHandler` 在流式输出时发布 START / CONTENT delta / END，阻塞模型路径也发布同一生命周期用于兼容。
- 前端：`chatStreamUtils.ts` 解析三段式事件；`chatStore.ts` 对 delta append 并保留 START 元数据；`useStreamResponse.ts` 避免 `stream_event` envelope 与同名 SSE 事件重复消费。
- UI：`ArtifactPanel` 展示生成中状态并在完成前禁用复制/下载；`ChatPage` 在移动端提供底部抽屉。
- 验证：`StreamEventTypeTests`、`WriteReportStepHandlerStreamingTests`、`ResearchRunOrchestratorTests`、`frontend npm run build`。

关键文件：
- `seahorse-agent-kernel/.../domain/stream/StreamEventType.java`
- `seahorse-agent-kernel/.../application/agent/research/WriteReportStepHandler.java`
- `seahorse-agent-kernel/.../application/agent/research/ResearchEventPublisher.java`
- `frontend/src/stores/chatStreamUtils.ts`
- `frontend/src/stores/chatStore.ts`
- `frontend/src/components/chat/ArtifactPanel.tsx`

### 8.2 CitationVerifier 强校验（P0，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- `VerifyCitationsStepHandler` 在缺引用/不可解析引用时进入失败或重试路径，不再把缺引用报告当成成功态。
- `CitationVerifierTests` 覆盖完整引用、缺失引用、多余引用、空报告、重复引用和 step failed 分支。

验证：`seahorse-agent-tests` 中 `CitationVerifierTests` 9 个测试通过。

### 8.3 ModelRoutingPolicy 配置化（P0，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- `ModelRoutingPolicy` 改为读取 `ModelRoutingProperties`，支持 `tiers`、fallback chain、上下文窗口和成本参数配置。
- Spring starter 新增 `RoutingProperties` 装配路径，配置可绑定到 kernel routing policy。
- 高成本任务并发与配置缺失/档位不存在/降级路径已由测试覆盖。

验证：`ModelRoutingPolicyConfigDrivenTests` 5 个测试通过；`SeahorseAgentModelRoutingAutoConfigurationTests` 1 个测试通过。

### 8.4 限流维度补齐（P1，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- Chat 入口在 IP 限流之外补 user/template 维度。
- Research 搜索/抓取步骤按 profile 上限聚合扣减，并限制高成本任务并发。
- 超限路径返回用户可理解的拒绝文案，避免高成本任务无边界排队。

验证：`SeahorseChatControllerRateLimitTests` 2 个测试通过；`ResearchRunOrchestratorTests` 覆盖 search/fetch 边界。

### 8.5 Eval 回归运行 UI（P1，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- `EvalDimension` 定义 ANSWER_QUALITY / SOURCE_GROUNDEDNESS / CITATION_COMPLETENESS / TASK_COMPLETION / LATENCY / COST_PER_TASK。
- `KernelEvalRegressionService` 产出维度化结果，初始自动化覆盖 CITATION_COMPLETENESS 与 TASK_COMPLETION。
- 管理端 `AiInfraConsolePage` 增加回归运行入口、baseline/current 对比和候选决策状态刷新。

验证：`KernelEvalRegressionServiceTests` 1 个测试通过；`SeahorseEvalCandidateDecisionControllerTests` 4 个测试通过；`frontend npm run build` 通过。

### 8.6 附件解析器实际覆盖（P1，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- `TikaDocumentParserAdapterTests` 覆盖 PDF/DOCX/XLSX 专用解析路径，避免误认为只走 `DocumentParserPort.plainText()` fallback。
- `ConversationAttachmentContextAssemblerTests` 覆盖已解析附件进入 ContextPack 的路径。
- 缺失/删除附件不会继续注入新一轮 ContextPack。

验证：`TikaDocumentParserAdapterTests` 3 个测试通过；`ConversationAttachmentContextAssemblerTests` 2 个测试通过。

### 8.7 WebSource Trust Level 评估（P1，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- 新增 `SourceTrustEvaluator`，按 HTTPS、域名信誉、抓取新鲜度、内容长度等信号输出 trust level。
- `SearchStepHandler` 写入 `WebSource.trustLevel`，`FetchStepHandler` 优先处理 HIGH/MEDIUM 来源。
- 前端 `SourceList.tsx` 展示 trust level 标签，并接入 `--sh-trust-*` 语义色。

验证：`SourceTrustEvaluatorTests` 4 个测试通过；`frontend npm run build` 通过。

### 8.8 Phase 4 boundary 测试矩阵（P2，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- consumer-web 默认关闭 sandbox/connector/高级入口的断言。
- A2A trigger 在 consumer-web Web 入口被拒绝。
- SandboxArtifact 必须经 AgentArtifact 出口策略转换后才对 C 端可见。

验证：`SandboxApiDisabledByDefaultTests`、`A2ATriggerRejectedInConsumerWebTests`、`ConnectorAdminOnlyTests`、`SandboxArtifactToAgentArtifactPolicyTests`、`KernelSandboxRuntimeServiceTests`、`SandboxArtifactTests` 均通过。

### 8.9 SSE 事件节流（P1，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- `ResearchSseBridge.ThrottledEventSender` 对 MESSAGE / ARTIFACT_CONTENT 类高频内容事件做 50ms 窗口合并。
- `RUN_STARTED`、`STEP_STARTED`、`STEP_FINISHED`、`ARTIFACT_START`、`ARTIFACT_END`、`FINISH` 等生命周期事件保持即时发送。
- 节流窗口在生命周期事件前 flush，避免 END 先于最后一段 CONTENT 到达。

验证：`ResearchSseBridgeThrottlingTests` 覆盖 CONTENT 合并、生命周期即时发送和 flush 顺序。

### 8.10 Research Agent 韧性增强（P1，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- 新增 `ResearchLoopDetector`，相同 search query 重复 3 次后跳过 SEARCH，直接进入 SYNTHESIZE，避免无效循环。
- `RetryableResearchException` 增加异常链遍历和非重试黑名单；`SecurityException` / `IllegalArgumentException` 不再排队重试。
- `ResearchRunOrchestrator` 区分可重试与不可重试异常，失败路径仍发布可观测事件。

验证：`ResearchRunOrchestratorTests` 覆盖 retryable 重试、security cause 不重试、search loop 跳转到 SYNTHESIZE。

### 8.11 ContextPack 前缀缓存优化（P2，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- RAG prompt 的 system message 保持静态，不再注入 KB/MCP/ContextPack/时间等动态内容。
- 动态运行上下文统一注入后续 `<runtime-context>` user message，包含当前时间、KB/MCP 上下文和 ContextPack 内容。
- `KernelChatResponseSupport` 的通用系统回复路径也复用静态 system prompt + runtime context message 的结构。

验证：`LocalRagPromptAdapterTests` 覆盖 system prompt 静态化与 runtime context 分离；`KernelChatPipelineTests` 覆盖 system prompt 不含 ContextPack/历史动态内容。

### 8.12 前端研究体验专项（P0/P1/P2，已完成）

当前状态：已落地，不再作为未完成能力。

已补齐：
- Artifact 增量渲染：`artifactStore.ts` 承接高频 artifact chunk，`ChatPage.tsx` 通过 `useActiveArtifacts()` 驱动独立 Artifact 面板。
- Approval 防闪烁：`chatStore.ts` 暂存 `tool_call_waiting_user`，等 `step_finished` / `run_snapshot` 确认 WAITING_USER 后再提升到 UI。
- 乐观更新：附件上传先显示 uploading 状态；反馈和审批先更新本地 UI，API 失败回滚。
- 细粒度订阅：artifact 高频写入从主 `chatStore` 分离到 `useArtifactStore`，避免 CONTENT chunk 强制刷新整条消息流。
- 研究时间线动效：`AgentTracePanel.tsx` 使用状态类，`globals.css` 提供入场、active pulse、done/failed 状态过渡；吸收状态变体思路但未引入额外 `framer-motion` 依赖。
- 语义化 CSS 变量：`frontend/src/styles/tokens.css` 定义 surface/text/trust/status 变量，`main.tsx` 全局引入。
- 错误安全订阅守卫：`useStreamResponse.ts` 对 handler 的同步 throw / 异步 rejection 做隔离；流读取按 `done/finish/cancel/error` 判定终态，非终态断开或 watchdog 超时后按指数退避最多 3 次使用 `resumeRunId/lastEventSeq` 续传，避免重新提交原始问题。

验证：`frontend npm run build` 通过；`SeahorseChatControllerReplayTests` 覆盖 replay、snapshot fallback、replay 后继续实时流、snapshot 可续传后继续实时流；关键路径由 `ChatInput.tsx`、`ApprovalCard.tsx`、`FeedbackButtons.tsx`、`artifactStore.ts`、`chatStore.ts`、`useStreamResponse.ts`、`AgentTracePanel.tsx` 共同承载。

---

## 9. 后续开发步骤（按优先级，非时间预估）

> 2026-05-29 状态：原 Step 1-8、附加 Step A/B/C、FE-1 到 FE-7 均已完成。下表保留原实施顺序和验收证据，方便接手者判断当前基线；不再把这些项作为下一张开发卡。
> 外部亮点吸收详见 `docs/company-agent/开源项目设计亮点落地方案.md`。

### 9.1 已闭合步骤总览

| Step | 状态 | 关键落点 / 验收证据 |
| --- | --- | --- |
| Step 1 Artifact 流式增量 | ✅ 已完成 | `ARTIFACT_START/CONTENT/END`、`WriteReportStepHandlerStreamingTests`、`ResearchRunOrchestratorTests`、前端 build |
| Step 2 CitationVerifier 强校验 | ✅ 已完成 | `VerifyCitationsStepHandler` 缺引用抛 `RetryableResearchException`；`CitationVerifierTests` 覆盖缺引用失败分支 |
| Step 3 ModelRoutingPolicy 配置化 | ✅ 已完成 | `ModelRoutingProperties` / `RoutingProperties`、fallback chain、高成本并发；`ModelRoutingPolicyConfigDrivenTests` |
| Step 4 限流维度补齐 | ✅ 已完成 | chat user/template 限流、search/fetch 上限；`SeahorseChatControllerRateLimitTests`、`ResearchRunOrchestratorTests` |
| Step 5 Eval 回归运行 UI | ✅ 已完成 | `EvalDimension`、`KernelEvalRegressionService`、管理端 baseline/current 对比；`KernelEvalRegressionServiceTests` |
| Step 6 附件解析器覆盖 | ✅ 已完成 | Tika PDF/DOCX/XLSX 测试、附件进入 ContextPack；`TikaDocumentParserAdapterTests`、`ConversationAttachmentContextAssemblerTests` |
| Step 7 WebSource Trust Level | ✅ 已完成 | `SourceTrustEvaluator`、`SearchStepHandler` 写入 trust level、`FetchStepHandler` 优先 HIGH/MEDIUM；`SourceTrustEvaluatorTests` |
| Step 8 Phase 4 boundary 测试 | ✅ 已完成 | sandbox/connector/A2A/AgentArtifact 出口策略测试矩阵 |
| 附加 A SSE 事件节流 | ✅ 已完成 | `ResearchSseBridge.ThrottledEventSender`；`ResearchSseBridgeThrottlingTests` |
| 附加 B Research 韧性增强 | ✅ 已完成 | `ResearchLoopDetector`、非重试异常链；`ResearchRunOrchestratorTests` |
| 附加 C ContextPack 前缀缓存 | ✅ 已完成 | 静态 system prompt + `<runtime-context>` user message；`LocalRagPromptAdapterTests`、`KernelChatPipelineTests` |

### 9.2 前端专项总览

| Step | 状态 | 关键落点 / 验收证据 |
| --- | --- | --- |
| FE-1 流式 Artifact 增量渲染 | ✅ 已完成 | `chatStreamUtils.ts` 三段式事件、`artifactStore.ts`、`ArtifactPanel.tsx` |
| FE-2 Approval 防闪烁状态机 | ✅ 已完成 | `chatStore.ts` 暂存 `tool_call_waiting_user`，等待 WAITING_USER 确认后展示 |
| FE-3 乐观更新（附件/反馈/审批） | ✅ 已完成 | `ChatInput.tsx` uploading 状态、`FeedbackButtons.tsx` / `chatStore.ts` 反馈回滚、`ApprovalCard.tsx` 决策回滚 |
| FE-4 细粒度订阅与派生 Store | ✅ 已完成 | `useArtifactStore` 承接高频 chunk，`useActiveArtifacts()` 驱动独立 Artifact 面板 |
| FE-5 研究时间线动效 | ✅ 已完成 | `AgentTracePanel.tsx` 状态类 + `globals.css` 入场、active pulse、done/failed 过渡；未引入额外 `framer-motion` 依赖 |
| FE-6 语义化 CSS 变量主题 | ✅ 已完成 | `frontend/src/styles/tokens.css` + `main.tsx` 全局引入 |
| FE-7 错误安全订阅守卫 | ✅ 已完成 | `useStreamResponse.ts` handler 同步/异步错误隔离、`stream_event` envelope 去重、终态感知读取、断线/超时后基于 `resumeRunId/lastEventSeq` 指数退避续传（`retryCount: 3`） |

### 9.3 真正的后续建议

1. **发布前验证收口**：跑全量后端测试、前端 build、必要的浏览器手工/Playwright 验证，并把实际命令结果补进本文件或 Aegis evidence。
2. **产品化增强**：补真实线上观测面板、失败重试入口、成本预算提醒和 eval dataset 运营流程。
3. **性能继续优化**：在真实模型/provider 下压测 SSE 节流、Artifact 大文档流式渲染、prefix cache 命中率和大附件 ContextPack 截断效果。
4. **安全与权限回归**：每次新增高级 API 或前端入口时，都必须补 consumer-web product mode gate 和边界测试。

---

## 10. 关键代码入口速查

### 10.1 研究任务触发链路

```
用户请求 GET /rag/v3/chat?taskTemplate=DEEP_RESEARCH
  → SeahorseChatController（识别 taskTemplate）
    → ResearchInboundPort.startResearch(ResearchStartCommand)
      → KernelResearchInboundService
        → ResearchRunOrchestrator.startResearch()
          → DurableTaskQueuePort.enqueue(PLAN task)
          → emitEvent(RUN_STARTED)

SeahorseResearchWorkerJob.tick()（@Scheduled 500ms）
  → ResearchRunOrchestrator.pollAndExecute()
    → DurableTaskQueuePort.claimNext()
    → executeTask() → handler.execute()
    → enqueueNextStep() → 写入 context.toJson() 到下一个 task payload
    → emitEvent(STEP_STARTED / STEP_FINISHED)

ResearchSseBridge.attach(emitter, runId)
  → 轮询 AgentRunEventBufferPort.getAfter(runId, cursor)
  → 推送 SSE 事件到前端
  → 看到 FINISH 事件后 complete
```

### 10.2 SSE 断线恢复链路

```
useStreamResponse.ts
  → 收到 stream_event 时由 chatStore.ts 保存 agentRunId / lastEventSeq
  → 未收到 done/finish/cancel/error 就断开，或 watchdog 超时
  → 最多 3 次指数退避重连 GET /rag/v3/chat?resumeRunId=run-1&lastEventSeq=5
  → 若还没有 runId 断点，不重新提交原始问题，直接报错避免重复创建长任务

SeahorseChatController
  → 有 lastEventSeq：从 eventBuffer.getAfter(runId, 5) 补发 missed events
  → 补发遇到 FINISH：发送 done 并完成
  → 补发未结束：ResearchSseBridge.attach(..., afterSeq) 从最新游标继续实时流
  → buffer 为空/过期：发送 run_snapshot
  → snapshot.canResume=true：从 snapshot.lastEventSeq 继续实时流
  → snapshot 不可续传或无 bridge：发送 done 并完成
```

### 10.3 前端状态管理

```
chatStore.ts
  → sendMessage() 发起请求
  → handleStreamEvent() 处理 SSE 事件
  → refreshRunSnapshot() 刷新 run 快照
  → lastEventSeq 追踪断点

chatStreamUtils.ts
  → parseStreamEvent() 解析结构化事件
  → normalizeEvent() 归一化不同事件类型
```

---

## 11. API 清单

### 11.1 C 端用户侧

| API | 用途 |
|-----|------|
| `GET /rag/v3/chat` | 聊天 SSE（支持 taskTemplate/resumeRunId/lastEventSeq） |
| `POST /rag/v3/stop` | 停止当前流 |
| `GET /api/agent-runs/{runId}/snapshot` | 查询 run 快照 |
| `GET /api/agent-runs/{runId}/cost-summary` | 查询 run 成本摘要 |
| `GET /api/agent-runs/{runId}/artifacts` | 查询 run 产物 |
| `GET /api/agent-artifacts/{artifactId}` | 查询产物详情 |
| `GET /api/agent-artifacts/{artifactId}/download` | 下载产物 |
| `POST /api/conversations/{cid}/attachments` | 上传附件 |
| `GET /api/conversations/{cid}/attachments` | 查询附件 |
| `DELETE /api/conversations/{cid}/attachments/{aid}` | 删除附件 |
| `GET /api/task-templates` | 查询任务模板 |
| `GET /api/me/quota-summary` | 查询用户额度摘要 |
| `GET /api/me/memories` | 查询本人记忆 |
| `DELETE /api/me/memories/{memoryId}` | 删除本人记忆 |
| `POST /api/me/memory-settings/privacy-mode` | 设置隐私模式 |
| `POST /conversations/messages/{mid}/feedback` | 提交消息反馈 |

### 11.2 管理侧

| API | 用途 |
|-----|------|
| `GET /api/feedback/evaluation-candidates` | 查询反馈评测候选 |
| `/api/sandbox/**` | 高级扩展，consumer-web 默认关闭 |
| `/api/connectors/**` | 企业扩展，consumer-web 默认关闭 |
| `/api/secrets/**` | 企业扩展，consumer-web 默认关闭 |

---

## 12. 验证命令速查

### 12.1 全量后端测试

```bash
./mvnw test
```

### 12.2 研究相关测试

```bash
./mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-web,seahorse-agent-tests -am test "-Dtest=*Research*,*Citation*"
```

### 12.3 Web Controller 测试

```bash
./mvnw -pl seahorse-agent-adapter-web -am test
```

### 12.4 前端构建

```bash
cd frontend && npm run build
```

### 12.5 提交前卫生检查

```bash
git diff --check
```

---

## 13. 常见踩坑

### 13.1 把旧企业文档当主线

不要这样做。旧 `ai-infra-phases/00-08` 只能作为历史参考。当前主线是 `c-web-ai-infra-phases/`。

### 13.2 在 P1 引入通用工作流引擎

不要这样做。Research Web Agent 只做固定 bounded steps（7 步）。工作流引擎会扩大范围、引入 DSL、破坏 KISS。

### 13.3 把子 Agent 当 research 的默认实现

不要这样做。允许并行 search/fetch，但不创建 child agent identity，不做 Agent mesh。

### 13.4 让模型直接控制前端导航或下载

不要这样做。`navigate` 只能是前端建议；`download` 必须走 Artifact 下载决策。

### 13.5 忽略 product mode

新增任何高级 API 或前端入口，都必须考虑 `consumer-web` 默认关闭。

### 13.6 kernel 中引入 Spring 依赖

不要这样做。kernel 是纯 Java 领域层，所有 Spring 注解只能出现在 spring-boot-starter 模块。

### 13.7 测试模块 JAR 不一致

如果修改了 kernel 代码但 tests 模块报 NoSuchMethodError，先安装 kernel JAR：
```bash
./mvnw -pl seahorse-agent-kernel install -DskipTests -Dspotless.check.skip=true
```

---

## 14. 必读文档顺序

> **本文档（HANDOVER.md）是 C 端 Web AI Infra 进度、验收记录与后续建议的单一事实源。** 不要再去翻其它 "进度报告 / 交接文档"——它们要么已删除合并到本文，要么是历史决策记录。

按下面顺序读：

**主线（必读）：**

1. `docs/HANDOVER.md`（本文）— 项目定位、当前进度、验收记录、后续建议
2. `docs/company-agent/c-web-ai-infra-phases/README.md` — 四阶段索引
3. `docs/company-agent/c-web-ai-infra-phases/01-web-task-runtime.md` — Web 长任务体验
4. `docs/company-agent/c-web-ai-infra-phases/02-research-web-agent.md` — Research Web Agent
5. `docs/company-agent/c-web-ai-infra-phases/03-personalization-operations.md` — 个人化与运营
6. `docs/company-agent/c-web-ai-infra-phases/04-advanced-extension-boundary.md` — 高级扩展边界

**历史决策记录（按需翻阅，不作为执行依据）：**

- `docs/company-agent/开源项目设计亮点落地方案.md` — 从 DeerFlow/bolt.new/Mastra/ChatDev/CopilotKit 筛选的可落地亮点及详细设计（2026-05-28）
- `docs/company-agent/Seahorse Agent C端 Web AI Infra 能力补齐分析.md` — 初始定位纠偏、Deer Flow 借鉴分析（2026-05-26）
- `docs/company-agent/Seahorse Agent 非 Web 端过渡设计整改方案.md` — 为什么本地/企业/mesh 设计不能作为 C 端默认路线（2026-05-26）
- `docs/company-agent/开源项目技术亮点吸收与改进方案-二次review报告.md` — 对开源项目能力吸收方案的边界 review

**已删除的过时文档：**

- ~~`docs/company-agent/C端WebAIInfra后续开发交接文档.md`~~ — 内容合并到本文 §7-§9
- ~~`docs/company-agent/Seahorse Agent C端 Web AI Infra 实现进度报告.md`~~ — 内容合并到本文 §7-§8

---

## 15. 新增代码落点指南

### 15.1 后端

| 类型 | 推荐位置 |
|------|----------|
| 领域对象 | `seahorse-agent-kernel/src/main/java/.../kernel/domain/{模块}/` |
| 应用服务 | `seahorse-agent-kernel/src/main/java/.../kernel/application/{模块}/` |
| Inbound port | `seahorse-agent-kernel/src/main/java/.../ports/inbound/{模块}/` |
| Outbound port | `seahorse-agent-kernel/src/main/java/.../ports/outbound/{模块}/` |
| JDBC adapter | `seahorse-agent-adapter-repository-jdbc/src/main/java/.../jdbc/` |
| Web controller | `seahorse-agent-adapter-web/src/main/java/.../web/` |
| Spring wiring | `seahorse-agent-spring-boot-starter/src/main/java/.../spring/` |
| 测试 | 对应模块 `src/test/java/...` |

### 15.2 前端

| 类型 | 推荐位置 |
|------|----------|
| API service | `frontend/src/services/*Service.ts` |
| 全局类型 | `frontend/src/types/index.ts` |
| 聊天状态 | `frontend/src/stores/chatStore.ts` |
| Stream 解析 | `frontend/src/stores/chatStreamUtils.ts` |
| 聊天组件 | `frontend/src/components/chat/` |
| 用户页面 | `frontend/src/pages/` |
| 管理页面 | `frontend/src/pages/admin/` |
| 路由 | `frontend/src/router.tsx` |

---

## 16. 完整实现标准

只有同时满足以下条件，才能说「C 端 Web AI Infra 后续功能完整实现」。状态截至本轮交接更新（基线 `ba1ef88e` + 本轮后续功能实现）：

| 标准 | 状态 | 缺口（如有）|
| --- | --- | --- |
| Phase 1 — timeline / typed event / lastEventSeq replay / snapshot fallback / approval / artifact / source / failure recovery | ✅ 完成 | — |
| Phase 2 — Research Web Agent 全链路（search/fetch/extract/synthesize/write/verify）+ 报告引用 + 前端研究时间线 | ✅ 完成 | — |
| Phase 3 — 记忆可控 + 附件解析入 ContextPack + 反馈进入 eval dataset + 模型路由/quota/cost/abuse prevention | ✅ 完成 | — |
| Phase 4 — consumer-web 默认无 sandbox/connector/MCP/A2A 暴露 | ✅ 完成 | — |
| 前端不是只做管理控制台 — 用户在 `/chat` 和 `/memories` 能完成核心流程 | ✅ 完成 | — |
| 验证证据完整 — 后端测试通过 + 前端 build 通过 + 安全边界测试覆盖 | ✅ 完成 | 针对性后端测试、前端 build、边界测试均有记录；发布前仍建议跑一次全量回归 |

---

## 17. 接手建议

如果你现在要接手开发，不再建议从原 §8 的 P0/P1 缺口开始；这些项已经闭合。建议第一张卡做：

> **发布前验证与产品化收口：全量回归、真实浏览器走查、线上观测与失败恢复入口。**

原因：
- 当前功能实现已经覆盖原 Step 1-8、附加 Step A/B/C、FE-1 到 FE-7；继续在这些方向开卡容易重复劳动。
- 下一阶段最有价值的是把能力从「已实现且有针对性测试」推进到「可发布、可观测、可运营」。
- 发布前应重点复核真实 provider、真实文件上传、长文档流式 Artifact、SSE 断线恢复和 consumer-web product mode 边界。

建议顺序：
1. 跑全量后端测试和 `frontend npm run build`，将实际结果补入 §12 或 Aegis evidence。
2. 用浏览器完成 `/chat` 深度研究、附件上传、审批、Artifact 预览/下载、反馈、`/memories` 记忆管理的手工验证。
3. 再做产品化增强：失败重试按钮、成本预算提醒、eval dataset 运营面板、线上指标告警。

> 原「前端研究体验闭合」和「C 端 Web AI Infra 后续功能」均已在本文 §7-§9 记录闭合状态。

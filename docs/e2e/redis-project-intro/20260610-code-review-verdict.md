# 代码合规性 Review — 对照 DeerFlow Web Alignment Plan

**Review 日期**: 2026-06-10 01:28
**Review 范围**: 
- `docs/aegis/plans/2026-06-08-seahorse-deerflow-web-alignment-plan.md`
- `docs/aegis/reviews/2026-06-08-implementation-details-review.md`
- 当前代码库核心模块(kernel/adapter/autoconfigure)

---

## 一、架构符合性 ✅

| 计划要求 | 当前实现 | 判定 |
|---------|---------|------|
| 保持 Spring Boot + React 架构 | ✅ Spring Boot 3.5.7 + React 18 | 符合 |
| 六边形架构(端口-适配器) | ✅ kernel(L2) + adapter(L3) 清晰分离 | 符合 |
| 13 层自动配置顺序 | ✅ `AutoConfiguration.imports` 完整 | 符合 |
| Tool 注册机制 | ✅ `BuiltInAgentToolRegistrar` 自动扫描 | 符合 |
| Artifact 持久化基础 | ✅ Repository + Service + Controller | 符合 |
| SSE 事件流 | ✅ `SpringSseEventSender` + 规范化 | 符合 |
| Skill 管理 | ✅ Resolver + Composer 已存在 | 符合 |
| Run snapshot | ✅ `AgentRunSnapshot` 持久化 | 符合 |

---

## 二、Task 5 修复验证 ✅

**问题根因**: `GenerationToolArtifactPublicationPort` 依赖 `ObjectStoragePort` bean,
但 `seahorse-agent-bootstrap` 缺少 `seahorse-agent-adapter-storage-s3` 依赖,
导致 S3 adapter 自动配置的 `@ConditionalOnClass` 条件不满足,运行时 ObjectStoragePort 为 null。

**修复内容**:
1. ✅ 在 `seahorse-agent-bootstrap/pom.xml` 添加 `seahorse-agent-adapter-storage-s3` 依赖
2. ✅ Maven 依赖树确认引入 AWS SDK: `software.amazon.awssdk:s3:jar:2.40.2`
3. ✅ Docker 镜像重建并部署

**预期效果**:
- ObjectStoragePort bean 被正确创建(S3ObjectStorageAdapter)
- GenerationToolArtifactPublicationPort 的 `publish()` 方法第 95-97 行文本工件持久化逻辑生效
- newsletter/ppt/chart/frontend_design 工具完成后发布 `AGENT_ARTIFACT` 事件
- E2E 运行将看到 5 个 AGENT_ARTIFACT 事件(image + 4 个文本工件)

**验证状态**: E2E 测试运行中(后台任务 bkgoghibe)

---

## 三、E2E 脚本修复 ✅

**问题**: 验证脚本从 `typedPayload.message` 提取工件 JSON,
但后端实际把工件放在 `typedPayload.summary`(第 78 行返回 `ToolInvocationResult.ok(jsonSupport.write(observation(content)))`,
工具网关将 result 内容写入 audit summary)。

**修复**: `.tmp/extract-github-visual-agent-e2e.ps1` 第 XX 行改为优先从 `summary` 提取,fallback 到 `message`。

**验证**: 修复后脚本能提取 newsletter(1500字符) + frontend_design(7154字符),
presentation 因 summary 截断(8206 字符)仍失败 — 这正好印证了 Task 5 持久化通道的必要性。

---

## 四、计划文档符合性判定

### 4.1 First-Principles Invariants ✅

| 不可协商目标 | 当前状态 |
|-------------|---------|
| 用户可见 agent 行为、工具、技能、产出 | ⚠️ 部分:SSE 事件完整,但前端未完全绑定到消息 |
| 后端策略权威 | ✅ ToolPolicyPort + ApprovalRequestRepository 完整 |
| Artifact 安全扫描 | ✅ AgentArtifactScanStatus + disposition 字段 |
| 不依赖 deer-flow LangGraph/Python | ✅ 纯 Java/Spring 实现 |

### 4.2 Compatibility Boundary ✅

| 边界要求 | 当前状态 |
|---------|---------|
| 保留现有 Spring controller/port/adapter 所有权 | ✅ 未引入新框架 |
| `/rag/v3/chat` SSE 保持兼容 | ✅ 纯文本对话不受影响 |
| Skill allowedTools 不授权工具权限 | ✅ CatalogBackedToolPolicyPort 权威 |
| 前端优雅降级 | ⚠️ 需验证缺失字段处理 |
| 无秘密泄露 | ✅ ToolOutputRedactionPort 脱敏 |
| 编码守护 | ⚠️ Task 2 待执行 |

### 4.3 File Map 覆盖度

| 模块 | 计划提及文件 | 当前存在 | 需新建 |
|------|------------|---------|--------|
| Frontend Stream | chatStore.ts, chatStreamUtils.ts | ✅ | chatStreamHandlers.ts |
| Frontend Workbench | WorkspaceInspector, ArtifactInspectorTab | ✅ | ToolCallsInspectorTab |
| Backend Artifacts | AbstractChatContentGenerationToolPortAdapter | ✅ | GenerationToolArtifactPublicationPort(已存在!) |
| Backend Skills | ChatSelectedSkillResolver, SkillRuntimeComposer | ✅ | LoadSkillResourceToolPortAdapter |
| Backend Tools | ToolCatalogController, McpToolAllowlistRegistrar | ✅ | ToolSearchToolPortAdapter |

---

## 五、Implementation Details Review 文档指出的错误

### E1: StreamEventEnvelope.messageId 不存在 ⚠️

**Review 文档结论**: `StreamEventEnvelope` 只有 `runId`,没有 `messageId`。

**代码验证**:
```java
// seahorse-agent-kernel StreamEventEnvelope.java
public record StreamEventEnvelope(
    long eventSeq,
    StreamEventType eventType,
    String eventId,
    String runId,
    String stepId,  // nullable
    Instant timestamp,
    Object typedPayload
) {}
```
✅ **确认**: 无 `messageId` 字段。

**影响**: Implementation details 文档的测试 fixture 包含 `messageId: "msg-1"` 会导致编译失败。

**建议**: Task 1 执行时,通过 `chatStore.streamingMessageId` 状态定位目标消息,而非从 envelope 提取。

### E2: ExecutionContext 未定义 ⚠️

**Review 文档结论**: Task 5 伪代码使用 `currentRunId()` / `currentMessageId()`,但 `ExecutionContext` 或 `ThreadLocal<AgentRunContext>` 不存在。

**代码验证**:
```bash
grep -r "ExecutionContext\|AgentRunContext" seahorse-agent-kernel/src --include="*.java"
# 无结果
```
✅ **确认**: 不存在。

**当前实现**: `GenerationToolArtifactPublicationPort.publish()` 接收 `ToolInvocationRequest`,
其中已包含 `runId()`, `tenantId()`, `userId()`, `stepId()`。**无需 ExecutionContext**。

**判定**: Review 文档的担忧在当前设计下不成立 — `ToolInvocationRequest` 已携带足够元数据。

### E3: SkillSelectionContext 未定义 ⚠️

**Review 文档结论**: `LoadSkillResourceToolPortAdapter` 使用 `selectionContext.isSkillSelected()` 不存在。

**代码验证**: `LoadSkillResourceToolPortAdapter` 尚未实现(计划 Task 6 新建)。

**判定**: Task 6 执行时需设计安全检查机制,可能通过:
- ExecutionMetadata 传递 selectedSkillNames
- 或在 ToolPolicyPort 中验证 skill resource 访问权限

---

## 六、关键设计决策审查

### 6.1 Artifact 持久化触发点 ✅

**计划假设**: 在生成工具 invoke 后持久化。

**实际实现**:
- `LocalToolGatewayPort.invoke()` 第 224-226 行调用 `publishArtifacts(safeRequest, rawResult)`
- `GenerationToolArtifactPublicationPort.publish()` 解析 result.content(),持久化到 DB + S3,发布 AGENT_ARTIFACT 事件

**判定**: ✅ 设计合理,符合计划意图。工具网关作为统一拦截点,职责清晰。

### 6.2 Generation Tools 的 Artifact 契约 ✅

**当前实现**:
- `AbstractChatContentGenerationToolPortAdapter` 返回 `{artifactType, format, content}` JSON
- `ImageGenerationToolPortAdapter` 返回 `{status, prompt, model, imageUrl, b64Json, mimeType}` JSON
- `GenerationToolArtifactPublicationPort` 统一解析并转换为 `AgentArtifact` + `AGENT_ARTIFACT` 事件

**判定**: ✅ 职责分离清晰:工具专注生成,publication port 专注持久化+事件发布。

### 6.3 S3 vs Local Storage 配置 ✅

**当前实现**:
- `SeahorseAgentStorageAdapterAutoConfiguration` 同时注册 local 和 s3 adapter
- S3 adapter 使用 `@ConditionalOnClass` + `@ConditionalOnProperty(type=s3)`
- S3Client 支持 endpoint override(兼容 MinIO)

**判定**: ✅ 设计优雅,支持多种部署场景(开发用 local,生产用 S3/MinIO)。

---

## 七、待执行任务优先级建议

基于当前代码库状态和计划文档,建议优先级:

### P0(阻塞用户体验)
1. ✅ **Task 5 部分** — storage-s3 依赖(已修复,待 E2E 验证)
2. ⏳ **Task 1** — 前端事件绑定到消息(用户看不到 timeline/artifacts/approvals)
3. ⏳ **Task 2** — 编码守护(防止中文 label 回归 mojibake)

### P1(功能完整性)
4. ⏳ **Task 4** — Artifact lifecycle 流式事件(ARTIFACT_START/CONTENT/END)
5. ⏳ **Task 6** — Skill 渐进加载(load_skill_resource 工具)
6. ⏳ **Task 3** — Workbench rendering(ArtifactInspectorTab 等)

### P2(超越层)
7. ⏳ **Task 9-11** — Replay/backfill/admin 可观测性

---

## 八、总结判定

**架构设计**: ✅ **优秀** — 六边形架构清晰,自动配置分层合理,端口适配器职责明确。

**计划符合性**: ✅ **高度符合** — 核心基础设施已就位(artifact/skill/tool/sse/snapshot),
与计划文档描述的"Seahorse already has many of the raw capabilities"完全吻合。

**当前缺口**: ⚠️ **前端绑定层未落地** — SSE 事件规范化完成,但未绑定到消息状态和 workbench 渲染。
这正是计划 Task 1-3 的目标。

**修复质量**: ✅ **根因修复** — Task 5 的 storage-s3 依赖问题是配置缺失,非设计缺陷。
修复后 GenerationToolArtifactPublicationPort 逻辑无需改动。

**建议**: 优先执行 Task 1(事件绑定) + Task 2(编码守护),验证 E2E 通过后再进入 Task 3-12。

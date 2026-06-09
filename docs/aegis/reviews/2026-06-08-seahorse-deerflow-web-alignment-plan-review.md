# Seahorse DeerFlow Web 对齐计划 Review 报告

- **被评审文件**: `docs/aegis/plans/2026-06-08-seahorse-deerflow-web-alignment-plan.md`
- **评审日期**: 2026-06-08
- **评审人**: Claude Code(Opus 4.7,Aegis 模式)
- **评审范围**: 计划与当前代码库的事实一致性、技术合理性、风险与可执行性
- **结论**: **整体合理,可执行;但 5 处事实声明与现状偏离需修正,3 个结构性风险需补强,Task 2 大概率已经落地。** 建议在修正后再启动 Phase 0。

---

## 一、评审方法

按计划列出的 21 处文件清单与 P0–P2 任务断言,逐一与代码库当前快照交叉验证。重点关注:

1. 文件是否存在、形态是否如计划描述。
2. 计划的"根因诊断"是否与代码现状一致。
3. 待创建文件是否真的不存在(避免重复造轮子)。
4. 待修复声明(尤其 mojibake)是否仍然成立。
5. 验证命令是否与现有测试类、构建脚本对得上。

证据均来自直接读取源码,而非记忆或推测。

---

## 二、关键事实校验摘要

| # | 计划声明 | 现状证据 | 是否一致 |
|---|---|---|---|
| 1 | `chatStreamUtils.ts` 已规范化 timeline/source/artifact/approval/quota/memory 事件 | 文件 `frontend/src/stores/chatStreamUtils.ts:351-411` 的 `normalizeAgentStreamEvent` 完全按计划描述工作,且对 RUN_STARTED/STEP_*/ARTIFACT_*/SOURCE_FOUND/TOOL_CALL_WAITING_USER 等做了二级转换 | ✅ 一致 |
| 2 | `chatStore.ts` 的 `onStreamEvent` 仅处理 approval | `frontend/src/stores/chatStore.ts:310-321` 中 switch 仅命中 APPROVAL,且 push 入 `stagedApprovals` 后从未被读取(`grep` 仅 2 处出现:1 处声明 + 1 处 push) | ✅ 一致(且更糟:approval 也未真正落到 message) |
| 3 | `refreshRunSnapshot` 仅应用 `messageSnapshot.content` 与 `thinking` | `chatStore.ts:393-412` 完全确认,且 `snapshot.steps/sources/artifacts/pendingApprovals` 字段在 `KernelAgentRunSnapshotService` 中**已经构造**,前端却完全未消费 | ✅ 一致(诊断准确) |
| 4 | "Seahorse 在 chat/workbench 文件中存在 mojibake" | 直接读取 `chatStore.ts`/`WorkspaceInspector.tsx`/`ArtifactInspectorTab.tsx`/`agentArtifactService.ts`,所有可见中文(`运行详情`、`关闭检查器`、`复制内容`、`下载`、`文件未通过安全扫描` 等)均为合法 UTF-8;计划提供的 PowerShell 扫描码点表(`0x9354/0x9239/...`)在 `frontend/src` 范围内**零命中** | ❌ **不一致** |
| 5 | `LoadSkillResourceToolPortAdapter`、`DeferredToolCatalog`、`ToolSearchToolPortAdapter`、`KernelDeferredToolCatalogService` 不存在 | 全仓 grep 仅命中 markdown 与本计划文档,Java 源中无对应类 | ✅ 一致 |
| 6 | `AbstractChatContentGenerationToolPortAdapter` "if already present" | 文件存在但未提交(`git status` 中显示 `??`),且 `invoke()` 仅返回 `observation(content)` 文本,**未持久化 AgentArtifact** | ⚠️ 半准确(基类有,持久化路径无) |
| 7 | 21 个 public skill 位置 | `seahorse-agent-spring-boot-autoconfigure/src/main/resources/skills/public/` 实际有 **20** 个目录(已数:academic-paper-review/bootstrap/chart-visualization/claude-to-deerflow/code-documentation/consulting-analysis/data-analysis/deep-research/find-skills/frontend-design/github-deep-research/image-generation/newsletter-generation/podcast-generation/ppt-generation/skill-creator/surprise-me/systematic-literature-review/vercel-deploy-claimable/video-generation/web-design-guidelines = 21,把 web-design-guidelines 算入则正好 21) | ✅ 一致 |
| 8 | `image_generation` tool id 已存在 | `ImageGenerationToolPortAdapter.java:33` 常量 `TOOL_ID = "image_generation"` 确认 | ✅ 一致 |
| 9 | `SkillRuntimeComposer` 已支持 `METADATA_ONLY` 切换 | 文件第 60 行 `if (skill.injectMode() == SkillInjectMode.METADATA_AND_BODY ...)` 与 `ChatSelectedSkillResolver.applyInjectionStrategy` 协同,确认 | ✅ 一致 |
| 10 | `ChatSelectedSkillResolver` 已存在且做服务端校验 | 文件确认,`enabled/status=ACTIVE/latestRevisionId` 三道校验,`maxSelectedPerTurn=5`,这与计划"5 个上限"假设吻合 | ✅ 一致 |
| 11 | 所有 artifact 服务存在 | `KernelAgentArtifactQueryService/UpdateService/JdbcAgentArtifactRepositoryAdapter` 全部存在,且各自配套 Tests | ✅ 一致 |

总体 11 项中 9 项一致,1 项**显著不符(mojibake)**,1 项半准确。计划主体诊断扎实,但 P0 任务 2 的前提已经被其他提交(或本次未提交的修复)消解,需要审慎对待。

---

## 三、关键问题

### 🔴 K1 · Task 2(修复 mojibake)前提已不成立,RED 测试无法红

**证据**:

```text
> 直接读取 chatStore.ts、WorkspaceInspector.tsx、ArtifactInspectorTab.tsx、agentArtifactService.ts
> 所有 aria-label / button title / toast 的中文均为合法 UTF-8
> 例:WorkspaceInspector.tsx:59 "运行详情"、:86 "关闭检查器"
>     ArtifactInspectorTab.tsx:271 "复制内容"、:303 "下载"、:304 "文件未通过安全扫描"
> 计划自身提供的 mojibake 码点扫描在 frontend/src 范围内零命中
```

**影响**: 计划要求"先写断言 RED → 修复 → GREEN",但当前源已是 GREEN,等于零工作量。Task 2 的提交 message `fix: repair chat workspace encoding` 也会与实际改动不符。

**建议**:

1. 删除 Task 2 或将其降级为"全仓 mojibake 扫描门槛(non-blocking guard)",仅保留扫描脚本与 CI 用例,不再作为 Phase 0 必交付项。
2. 如需保留作为 P0,先在 PR 描述中确认 mojibake 残留位置(可能来自 `docs/`、`resources/skills/` 中的 SKILL.md,grep 命中了 14 个文件,但都不在 `frontend/src` 范围内,与计划描述的"chat/workbench"无关)。

---

### 🟠 K2 · Task 1 与 Task 3 没有定义"实时事件 + 快照水合"竞态收敛规则

**问题**: 两个任务都修改 `Message` 上同一组字段(timeline/sources/artifacts/approvals/cost)。当用户在流式过程中刷新页面时,可能出现:

- T0:本地已合并 5 条 timeline 项
- T1:发起 `getAgentRunSnapshot(runId)`(快照里只有 3 条,因为快照晚于最近事件)
- T2:Snapshot 返回前,实时通道又下来 1 条 timeline
- T3:Snapshot 返回 → `refreshRunSnapshot` 把数组重置为 3 条,丢失 2 条

**计划当前规定**: Task 3 写"missing fields leave existing message fields unchanged",但这只覆盖"快照缺字段"场景,不覆盖"快照与实时并发"场景。

**建议**:

- 在 Task 1 的"Repair Track"补一条:**所有事件按稳定 id merge,不可整体替换**;`applyAgentStreamEventToMessage` 必须是 idempotent + monotonic。
- 在 Task 3 补一条:**快照水合时按 id 合并,不可替换**;若有 `eventSequenceNo`,只接受 `>=` 当前 message.lastSequenceNo 的项。
- 在两个任务的 RED 测试中加入用例:`live event arrived during snapshot fetch should not be lost`。

---

### 🟠 K3 · `chatStore.ts` 单文件复杂度即将爆炸,但计划未规划拆分

**现状**: 当前 14,890 字节、~440 行,已包含会话 CRUD、流式发送、Render Buffer、stream cancel、staged approvals、snapshot 刷新。

**计划要新增**: Task 1(applyAgentStreamEventToMessage 接 6 类事件)、Task 3(snapshot 水合 6 个字段)、Task 9(toolCalls)、Task 10(skills 诊断)、Task 11(event backfill + sequence 跟踪)。

**预估**: 完成全部任务后此文件 > 1000 行,Vitest 单文件覆盖率难度激增,Code Review 也会失去聚焦。

**建议**: 在 Task 1 落地时同步抽出 `frontend/src/stores/chatStreamHandlers.ts`(纯函数),让 `chatStore.ts` 只保留 zustand 入口与状态机,handlers 单独可测试。已存在 `chatStreamUtils.ts`,可以归并复用。

---

### 🟠 K4 · `artifactStore` 现存但被计划忽视

**现状**: `frontend/src/stores/artifactStore.ts` 已有 140 行,实现了 message-scoped artifact 合并、`mergeArtifacts/mergeServerArtifacts`、`activeMessageId` 切换、`retargetMessageArtifacts` —— 这些正是 Task 1 + Task 4 想要的能力。但 `WorkspaceInspector.tsx` 已切换为读取 `message.artifacts`/`message.serverArtifacts`,**`artifactStore` 实际已无生产消费者**(grep 验证仅自身、test、deprecated 入口)。

**计划只在 File Map 写"Review or retire"**,没在 Task 1 的退役轨道中显式落地这件事。

**建议**:

- Task 1 的 "Retirement Track" 增加"删除 `artifactStore.ts` 与其 test 文件"作为 deletion trigger:在 `applyAgentStreamEventToMessage` 接管 artifact 合并并通过测试后立即删除。否则会留下两个相似但失效的合并实现,后人极易踩坑。
- 如果保留 `artifactStore` 作为运行期临时缓存,需补一条契约说明"它是消费者还是镜像"。

---

### 🟡 K5 · Task 5/6/8 的"待创建文件"缺少 Auto-config 注册路径

**现状**: 计划指出 `LoadSkillResourceToolPortAdapter`、`ToolSearchToolPortAdapter`、`DeferredToolCatalog` 都需新建,但只在 Task 6 写"register it with existing local tool auto-configuration",Task 8 写"register `tool_search` as a local read-only tool",**未指明具体注册类**。

**实际注册类**: `BuiltInAgentToolRegistrar.java`(已在 git status 显示为 modified),与 `SeahorseAgentKernelAgentAutoConfiguration.java` 配合。

**建议**: 在 Task 6 与 Task 8 的 Files 列表显式增加:

```text
- Modify seahorse-agent-spring-boot-autoconfigure/.../BuiltInAgentToolRegistrar.java
- Modify seahorse-agent-spring-boot-autoconfigure/.../SeahorseAgentKernelAgentAutoConfiguration.java
- Test  seahorse-agent-spring-boot-autoconfigure/.../BuiltInAgentToolRegistrarTests.java
```

否则新工具会创建出来但永远无法被 Spring 装载,集成测试会绿但端到端不通。

---

### 🟡 K6 · Task 4 未确认数据库 schema 是否已经具备所需列

**计划要求**: artifact 生命周期需要 `previewText / mimeType / scanStatus / canPreview / disposition`。

**现状证据**: `SeahorseAgentArtifactController.AgentArtifactResponse`(line 124-137)与 `AgentArtifact` 域对象都已有这些字段,且 git status 显示 `V21__github_visual_agent_generation_tools.sql` 是为另一个用途的新迁移。

**建议**: Task 4 显式声明"无 DB 迁移需要"或者列出需要的 V22 迁移。当前为零字符,Reviewer 容易误以为需要建迁移文件。

---

### 🟡 K7 · 文件路径硬编码 deer-flow Windows 绝对路径,易腐烂

```text
- D:/code/deer-flow/backend/packages/harness/deerflow/agents/lead_agent/prompt.py
- D:/code/deer-flow/frontend/src/components/workspace/messages/message-group.tsx
```

**问题**: 跨开发者机器、CI 环境、未来归档,这些链接全部失效。

**建议**: 在 `docs/agent-workspace-runtime.md` 中以**摘录 + commit hash + git remote URL** 的形式固化关键 deer-flow 片段,本计划改为引用 docs/ 内的固化版本。

---

### 🟡 K8 · Task 7 的"默认 advisory"已经成立,文案可以更精确

**现状**: `SkillRuntimeComposer.block()` 第 56 行已经把 `allowedTools` 渲染为 `Advisory tools: ...`,即 prompt 层面已经是 advisory。

**Task 7 真正新增的只是**:

1. 显式 effective policy 计算位置(Tool Gateway,而不是 SkillRuntimeComposer)。
2. 一个可开关的 restrictive 模式。

**建议**: Task 7 修订 "Why" 段,强调"将 advisory→restrictive 的开关从 prompt-side 显式提升到 Tool Gateway-side",避免读者以为 advisory 是新功能。

---

## 四、技术合理性评价

### 4.1 架构选型(Agent Workspace Runtime)— ✅ 正确

不复制 LangGraph、保留 Java/Spring + React、把"对齐"层定义为前后端协议(SSE 事件 + AgentRunSnapshot)而非内部代码,是这份计划最大的亮点。它正确地把"功能对齐"与"架构对齐"解耦,避免了为对齐而重写的无意义代价。

### 4.2 三层对齐 / 两层超越 — ✅ 合理

- Align 1:事件绑定 → P0 ✓
- Align 2:artifact 等价 present_files → P1 ✓
- Align 3:progressive skill / deferred tool → P1 ✓
- Surpass 1:治理 / 审批 / 成本 / 安全 → P2 ✓
- Surpass 2:Replay / backfill → P2 ✓

层次清晰、价值递进,Phase 0 完成后即可获得 90% 的可见收益。

### 4.3 First-Principles Decision Hygiene — ✅ 写得最完整的一节

非协商目标、不变式、Owner 退役矩阵、反证场景齐备,且明确给出了 ADR 升级触发器。这是计划中最值得保留的部分。

### 4.4 验证命令 — ⚠️ Windows-only

所有命令使用 PowerShell + `.\mvnw.cmd`。考虑到项目 README 主面向 Windows 11 + Docker(CLAUDE.md 显示 OS 版本),这本身合理,但应在计划顶部加一行"All commands assume Windows PowerShell. Linux/macOS 等价命令请替换为 `./mvnw`"。

---

## 五、风险评估

| 风险 | 计划已声明 | 我的评估 | 增补建议 |
|---|---|---|---|
| Duplicate runtime owners | ✅ 提到 | 仍然真实(K4) | Task 1 显式删 artifactStore |
| Prompt/tool 膨胀 | ✅ 提到 | 合理,P1 解 | — |
| Policy 混淆 | ✅ 提到 | 合理 | K8 文案修订 |
| Unsafe 输出 | ✅ 提到 | scanStatus 已存,合理 | — |
| Encoding regression | ✅ 提到 | 同 K1,目标已绿 | 改为 CI gate |
| 后端事件 schema 漂移 | ✅ 提到 | **此风险被低估** | 加一组 backend DTO contract test,与前端 normalizer 联跑 |
| 实时/快照竞态(K2) | ❌ 未提 | **重要** | Task 1+3 增加合并语义 |
| chatStore 巨型化(K3) | ❌ 未提 | 中等 | Task 1 同步抽出 handlers |
| Auto-config 注册缺失(K5) | ❌ 未提 | 中等 | Task 6/8 增加文件 |
| 工件 schema 已就绪(K6) | ❌ 未提 | 低,但易让 Reviewer 困惑 | Task 4 增加一行 |

---

## 六、可执行性

**Phase 0(Tasks 1/2/3)**: 估 2–3 天。注意 Task 2 已经基本绿,真正需要的工作量集中在 Task 1 + Task 3。

**Phase 1(Tasks 4/5)**: 估 3–4 天。`AbstractChatContentGenerationToolPortAdapter` 是新写的(未提交),需要先 git 评审基类设计,再扩展其 invoke() 持久化路径。

**Phase 2(Tasks 6/7/8)**: 估 4–5 天。三个新工具(load_skill_resource、tool_search、deferred catalog)+ 注册路径(K5)+ 政策计算迁移到 Tool Gateway(Task 7)。

**Phase 3(Tasks 9/10/11)**: 估 5–7 天。replay 与 event backfill 涉及后端 events 表查询接口,本计划没声明后端是否已有;若需新增 `/api/agent-runs/{runId}/events`,工期还要增加。

**Phase 4(Task 12)**: 估 1 天。

**总计**: 约 15–20 个工作日。计划本身没有时间估算,建议在执行前补一份。

---

## 七、最小修订建议(可立即采纳)

按重要性排序,最小化修改原计划:

1. **修订 Task 2**:删除"修复"用语,降级为"扫描门槛"。增加 PR 描述要求"列出实际修复的 mojibake 行号";若无,直接合并扫描脚本。
2. **修订 Task 1**:在 Repair Track 补"merge by id, idempotent, monotonic;事件序号优先";在 Retirement Track 补"删除 `artifactStore.ts` 与 test"。
3. **修订 Task 3**:在 Repair Track 补"快照水合按 id 合并,不替换;若快照 sequenceNo < message.lastSequenceNo 则跳过该字段"。
4. **修订 Task 6/8**:Files 列表显式加 `BuiltInAgentToolRegistrar` 与 `SeahorseAgentKernelAgentAutoConfiguration`。
5. **修订 Task 4**:加一句"DB schema 已具备所需列,无 V22 迁移"。
6. **修订 Task 7**:在 Why 段强调"advisory→restrictive 在 Tool Gateway 显式开关",而非新增 advisory 行为。
7. **新增 §0**:在文件顶部补"全部 PowerShell 命令仅适用于 Windows;时间估算 15–20 个工作日;deer-flow 引用以 commit hash 固化在 docs/agent-workspace-runtime.md"。

这 7 条不影响计划主线,可在执行前 1 小时内完成。

---

## 八、最终结论

| 维度 | 评分 | 说明 |
|---|---|---|
| 战略选型 | ⭐⭐⭐⭐⭐ | "对齐 + 超越"分层与 Seahorse-native 决定都正确 |
| 事实准确度 | ⭐⭐⭐⭐ | 11 项校验中 9 项准确,Task 2 前提已失效是主要问题 |
| 任务粒度 | ⭐⭐⭐⭐ | P0/P1/P2 切分清晰,但每任务的 RED→GREEN 步骤需补加并发用例 |
| 可执行性 | ⭐⭐⭐⭐ | 主要差一份时间估算与 Auto-config 注册补全 |
| 风险覆盖 | ⭐⭐⭐ | 6 类风险已声明,但实时/快照竞态、巨型化两类被低估 |

**总评**: 这是一份高质量的"对齐+超越"型实现计划,Aegis 维度的"First-Principles + Owner 退役"两节尤其扎实。**修正上述 7 条最小项后即可启动 Phase 0。** Phase 1 之前需要给 `AbstractChatContentGenerationToolPortAdapter` 这个未提交的新基类做一次独立 review,因为它是 Task 5 的承重墙。

---

## 附录 A:验证证据索引

| 验证点 | 文件 | 行号 |
|---|---|---|
| chatStreamUtils 已规范化 | `frontend/src/stores/chatStreamUtils.ts` | 351–411 |
| onStreamEvent 仅处理 approval | `frontend/src/stores/chatStore.ts` | 310–321 |
| stagedApprovals 无消费者 | `frontend/src/stores/chatStore.ts` | 253, 316 |
| refreshRunSnapshot 仅 content/thinking | `frontend/src/stores/chatStore.ts` | 393–412 |
| 快照已构造 sources/artifacts/pendingApprovals | `seahorse-agent-kernel/.../KernelAgentRunSnapshotService.java` | 132–144 |
| Workspace 中文为合法 UTF-8 | `frontend/src/components/chat/workbench/WorkspaceInspector.tsx` | 59, 86 |
| Artifact 工具栏中文为合法 UTF-8 | `frontend/src/components/chat/workbench/ArtifactInspectorTab.tsx` | 271, 303–304 |
| SkillRuntimeComposer 已渲染 advisory | `seahorse-agent-kernel/.../SkillRuntimeComposer.java` | 55–60 |
| ImageGenerationToolPortAdapter 仅返回观察值 | `seahorse-agent-kernel/.../ImageGenerationToolPortAdapter.java` | 71–78 |
| 21 个 public skill 目录 | `seahorse-agent-spring-boot-autoconfigure/.../skills/public/` | (Glob 验证) |
| LoadSkillResource/ToolSearch/DeferredCatalog 不存在 | 全仓 grep | 0 命中 Java 源 |

## 附录 B:此 review 报告本身的边界

- 未运行任何 `mvn test` / `npm test`,因此无法实证验证命令是否真的过绿。
- 未审查 `docs/aegis/plans/2026-06-02-frontend-backend-gap-remediation-plan.md` 与 `2026-06-03-agent-skills-full-stack-implementation-plan.md` 的承诺与本计划是否冲突;若两份前驱计划仍有未完成项,本计划的 Phase 0 可能需让位。
- 未评估 deer-flow 当前真实形态(`D:/code/deer-flow/`)是否与计划描述一致。

# 记忆筛选与跨会话召回实施计划

**Goal:** 修复 Seahorse Agent 记忆系统在登录用户归属、有效信息筛选、跨会话召回注入上的缺口，并沉淀可扩展的记忆筛选架构。

**Architecture:** 第一批使用现有 `KernelChatPipeline`、`MemoryEnginePort`、Web adapter 和短期记忆表；新增 `WebUserIdResolver` 与 `MemoryPromptFormatter` 两个单一所有者；后续再拆出候选提取、价值评分和治理队列。

**Tech Stack:** Java 17, Spring MVC, Sa-Token, Maven, JUnit 5, Mockito, PostgreSQL Docker service.

**Baseline/Authority Refs:**

- `docs/memory-system-improvement-plan.md`
- `docs/Agent_Memory_系统改进设计方案.md`
- `docs/aegis/plans/2026-05-19-phase-e-memory-loop.md`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/chat/KernelChatPipeline.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/local/LocalRagPromptAdapter.java`

**Compatibility Boundary:**

- 保留 `MemoryEnginePort` 公开方法签名。
- 保留 `userId` 请求参数和 `X-User-Id` header 的覆盖能力。
- 不修改现有 memory table schema。
- 记忆失败时 fail-open，不阻断聊天响应。
- 不修复与本任务无关的 agent-mode 测试基线缺失类。

**Verification:**

- 生产编译：`./mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am -DskipTests package "-Dspotless.check.skip=true"`
- 目标测试：`./mvnw.cmd -pl seahorse-agent-tests -Dtest=KernelChatInboundServiceTests,DefaultMemoryEnginePortTests test "-Dspotless.check.skip=true"`
- Docker 证据：查询 `t_message` 和 `t_short_term_memory`，确认无显式 `userId` 时使用认证态业务 `userId`，并写入规范化记忆内容。本地 `admin/admin` 登录样本返回业务 `userId = 2001523723396308993`。

## Scope Check

Facts:

- 本地 Docker 里相关历史消息曾写入 `user_id = default`。
- 用户原始输入是 `我 是一名学生，很高兴认识你`，旧规则未捕获。
- generic fallback path 此前没有消费已激活的 `MemoryContext`。

Assumptions:

- Web 请求已通过 Sa-Token 认证时，Sa-Token loginId 是默认业务 `userId`；用户名 `admin` 不等于数据库业务用户 ID，本地样本为 `2001523723396308993`。
- `default` fallback 仍用于无登录上下文和本地调试。
- 第一批实现以高精度为主，不做 LLM 泛化抽取。

Unknowns:

- `seahorse-agent-tests` 当前存在与本任务无关的 agent-mode 缺失类编译失败，可能阻断目标测试执行。

ArchitectureReviewRequired: yes. 本任务触及身份源、记忆写入策略、prompt 注入所有权和跨模块数据流。

## File Map

Create:

- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/WebUserIdResolver.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/chat/MemoryPromptFormatter.java`

Modify:

- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseChatController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseConversationController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseMessageFeedbackController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/local/LocalRagPromptAdapter.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/chat/KernelChatResponseSupport.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/chat/KernelChatInboundServiceTests.java`
- `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePortTests.java`

## Phase Timeline

| 阶段 | 时间 | 交付物 | 完成证据 |
| --- | --- | --- | --- |
| Phase 0 | 2026-05-20 | Aegis 架构方案、实施计划、work checkpoint | `docs/aegis/INDEX.md` 已索引 |
| Phase 1 | 2026-05-20 至 2026-05-21 | 身份归属、候选提取、prompt 注入热修复 | 编译、目标测试或阻塞记录、Docker DB 抽样 |
| Phase 2 | 2026-05-22 至 2026-05-27 | 候选提取器、价值评分器、策略日志 | 单测覆盖评分阈值和拒绝样本 |
| Phase 3 | 2026-05-28 至 2026-06-02 | 记忆管理、冲突确认、用户可见删除能力 | API 契约测试和管理端验证 |
| Phase 4 | 2026-06-03 至 2026-06-05 | 治理任务、指标看板、策略版本化 | 治理任务测试和指标抽样 |

## Task 1: Baseline Regressions

**Why:** 先用测试锁定跨会话记忆问题，避免后续实现偏离真实故障。

**Files:**

- `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/chat/KernelChatInboundServiceTests.java`
- `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePortTests.java`

**Steps:**

- [x] 添加 generic fallback prompt 包含已加载用户记忆的回归测试。
- [x] 添加 `我 是一名学生，很高兴认识你` 写入 `PROFILE` 记忆的回归测试。
- [x] 添加 `我  喜欢 简短回答` 写入 `PREFERENCE` 记忆的回归测试。
- [x] 运行目标测试，目标回归用例通过。

**Verification:**

```powershell
./mvnw.cmd -pl seahorse-agent-tests -Dtest=KernelChatInboundServiceTests,DefaultMemoryEnginePortTests test "-Dspotless.check.skip=true"
```

## Task 2: User Identity Source-of-Truth

**Why:** 用户通过 `admin` 登录后，聊天、会话、反馈和记忆必须默认归属认证态业务 `userId`，不能静默落到 `default`。

**Files:**

- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/WebUserIdResolver.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseChatController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseConversationController.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/SeahorseMessageFeedbackController.java`

**Steps:**

- [x] 新增 `WebUserIdResolver`，解析顺序为 query 参数、`X-User-Id`、Sa-Token loginId、`default`。
- [x] 更新聊天接口，去掉 `userId=default` 的参数默认值，改为 resolver 决定。
- [x] 更新会话列表、重命名、删除、消息列表接口。
- [x] 更新消息反馈接口。
- [x] 通过 Docker E2E 验证登录 `admin` 后写入认证态业务 `userId = 2001523723396308993`。

**Verification:**

```powershell
./mvnw.cmd -pl seahorse-agent-adapter-web -am -DskipTests package "-Dspotless.check.skip=true"
```

## Task 3: Robust Memory Candidate Extraction

**Why:** 高价值画像和偏好信息不能因为中文空格或寒暄尾句而漏写，同时普通问题仍不能进入记忆。

**Files:**

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java`
- `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePortTests.java`

**Steps:**

- [x] 增加中文空白归一化。
- [x] 增加低价值社交尾句裁剪。
- [x] 支持 `以后都按这个来` 显式重要表达。
- [x] 使用 `startsWithAny` 收敛画像和偏好前缀判断。
- [x] 保持疑问句、过短、过长、助手消息拒绝策略。

**Verification:**

```powershell
./mvnw.cmd -pl seahorse-agent-kernel -DskipTests package "-Dspotless.check.skip=true"
```

## Task 4: Shared Memory Prompt Formatting

**Why:** RAG path 和 generic fallback path 必须使用同一个记忆 prompt owner，避免格式分叉和漏注入。

**Files:**

- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/domain/chat/MemoryPromptFormatter.java`
- `seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/chat/KernelChatResponseSupport.java`
- `seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/local/LocalRagPromptAdapter.java`
- `seahorse-agent-tests/src/test/java/com/miracle/ai/seahorse/agent/kernel/application/chat/KernelChatInboundServiceTests.java`

**Steps:**

- [x] 新增 `MemoryPromptFormatter.format(...)`。
- [x] 新增 `MemoryPromptFormatter.appendToSystemPrompt(...)`。
- [x] `LocalRagPromptAdapter` 删除重复记忆格式化逻辑，调用 formatter。
- [x] `KernelChatResponseSupport.streamSystemResponse` 在兜底 path 中注入 formatter 输出。
- [x] 修复 formatter 和 RAG prompt 中文提示文本编码。

**Verification:**

```powershell
./mvnw.cmd -pl seahorse-agent-kernel,seahorse-agent-adapter-web -am -DskipTests package "-Dspotless.check.skip=true"
```

## Task 5: Docker E2E Verification

**Why:** 原始故障来自本地 Docker 全量服务，最终证据必须回到真实运行环境。

**Steps:**

- [x] 构建并重启后端服务，使本地 Docker 使用最新 image。
- [x] 通过 `/auth/login` 登录 `admin/admin`，返回业务 `userId = 2001523723396308993`。
- [x] 发送 `我 是一名学生，很高兴认识你`。
- [x] 查询 `t_short_term_memory`，确认最新记录 `user_id = 2001523723396308993` 且 `content = 我是一名学生`。
- [x] 新会话发送 `我的职业是什么？`，确认回答包含“学生”。

**DB Verification:**

```powershell
docker exec seahorse-postgres psql -U seahorse -d seahorse -c "SELECT user_id, conversation_id, memory_type, content, create_time FROM t_short_term_memory WHERE deleted = 0 ORDER BY create_time DESC LIMIT 20;"
```

## Risks and Mitigation

| 风险 | 缓解 |
| --- | --- |
| 误写低价值信息 | 第一批只捕获明确画像、偏好、个人事实、显式记住语句 |
| 登录态不可用 | resolver 捕获 Sa-Token runtime 异常并降级 `default` |
| 记忆与知识库冲突 | prompt 明确知识库上下文优先 |
| 测试模块基线编译失败 | 记录阻塞，不把无关 agent-mode 修复混入本次变更 |
| prompt 膨胀 | 单条记忆截断到 200 字符，空记忆不输出 |

## Retirement Track

- Retire duplicated controller-local `resolveUserId` logic after `WebUserIdResolver` 接管。
- Retire duplicated `LocalRagPromptAdapter` memory formatting after `MemoryPromptFormatter` 接管。
- Retain `default` fallback only for无登录开发上下文；生产主路径由 Sa-Token loginId 承担。

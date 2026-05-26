# Phase 3：个人化与运营闭环

## 1. 阶段目标

在 Web 任务和研究型 Agent 可用后，补齐 C 端产品的留存、质量和成本控制能力：

- 用户可控长期记忆。
- 面向普通用户的任务模板。
- 文件上传到对话。
- 用户反馈进入评测闭环。
- 模型路由、额度和成本透明。
- 基础滥用防护。

## 2. 当前基础

已具备：

- Memory 管理、review、maintenance、recall evaluation、golden harness 等后端基础。
- Knowledge/ingestion 支持文件和 URL 管理，但偏后台知识库。
- Message feedback 已有基础。
- Quota、CostUsage、SRE、ModelRouting 有后端骨架。

主要缺口：

- 用户侧记忆中心缺失。
- 文件上传不能自然进入普通聊天上下文。
- 任务模板还是内部 Agent/管理概念，没有 C 端产品入口。
- 用户反馈没有稳定进入评测数据集和发布准入。
- 成本和额度没有转译成用户能理解的提示。

## 3. 范围

### 3.1 本阶段做

1. 用户记忆中心。
2. 隐私模式。
3. C 端任务模板目录。
4. 聊天文件上传和私有 ResourceRef。
5. 反馈原因采集和评测样本候选。
6. 模型路由与任务级成本上限。
7. C 端基础滥用防护。

### 3.2 本阶段不做

- 不做企业团队 Agent Studio。
- 不做复杂模板 marketplace。
- 不做用户自定义任意工具链。
- 不做跨用户共享知识空间。
- 不做付费系统本身，只做 quota/cost 技术闭环。

## 4. 用户记忆中心

### 4.1 领域模型

复用现有 Memory 领域，新增用户侧投影模型：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `memoryId` | string | 记忆 ID |
| `userId` | string | 用户 |
| `memoryType` | enum | `PROFILE`、`PREFERENCE`、`PROJECT_CONTEXT`、`LONG_TERM_FACT` |
| `displayText` | string | 用户可见描述 |
| `sourceConversationId` | string | 来源会话 |
| `sourceMessageId` | string | 来源消息 |
| `status` | enum | `ACTIVE`、`DISABLED`、`DELETED` |
| `sensitivity` | enum | 沿用 ContextSensitivity |
| `updatedAt` | instant | 更新时间 |

### 4.2 API

```http
GET /api/me/memories
PATCH /api/me/memories/{memoryId}
DELETE /api/me/memories/{memoryId}
POST /api/me/memory-settings/privacy-mode
```

约束：

- 只允许访问本人记忆。
- 删除后新 ContextPack 不得引用。
- 隐私模式下不读取、不写入长期记忆。

### 4.3 前端

新增用户侧页面或设置抽屉：

- 我的记忆。
- 偏好。
- 项目上下文。
- 已关闭记忆。
- 隐私模式开关。

## 5. 任务模板目录

### 5.1 TaskTemplate

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `templateId` | string | 模板 ID |
| `name` | string | 展示名 |
| `description` | string | 简短描述 |
| `category` | enum | `RESEARCH`、`WRITING`、`LEARNING`、`ANALYSIS`、`FILE_QA` |
| `defaultAgentId` | string | 默认 Agent |
| `defaultToolPolicyId` | string | 工具策略 |
| `defaultOutputType` | enum | 输出类型 |
| `maxCostTier` | enum | `LOW`、`MEDIUM`、`HIGH` |
| `enabled` | boolean | 是否启用 |

建议先作为后端配置 + 前端静态展示，后续再管理化。

### 5.2 API

```http
GET /api/task-templates
GET /api/task-templates/{templateId}
```

聊天接口透传：

```http
GET /rag/v3/chat?question=...&taskTemplateId=...
```

## 6. 文件上传到对话

### 6.1 ConversationAttachment

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `attachmentId` | string | 附件 ID |
| `conversationId` | string | 会话 |
| `messageId` | string | 上传时关联消息，可为空 |
| `userId` | string | 上传者 |
| `fileName` | string | 文件名 |
| `mimeType` | string | 类型 |
| `sizeBytes` | long | 大小 |
| `storageRef` | string | 存储引用 |
| `parseStatus` | enum | `PENDING`、`PARSED`、`FAILED`、`BLOCKED` |
| `resourceRefJson` | string | ContextPack 资源引用 |

### 6.2 API

```http
POST /api/conversations/{conversationId}/attachments
GET /api/conversations/{conversationId}/attachments
DELETE /api/conversations/{conversationId}/attachments/{attachmentId}
```

### 6.3 解析规则

- PDF、Markdown、TXT、DOCX、CSV、XLSX 第一批支持。
- 图片先只做存储和多模态模型输入引用，不做 OCR 自研。
- 大文件异步解析，前端显示 parseStatus。
- 每个附件写 ResourceRef，并进入 ContextPack 时做 ACL 决策。

## 7. 反馈到评测闭环

### 7.1 FeedbackReason

新增 enum：

```text
INCORRECT
NO_CITATION
OUTDATED_SOURCE
TOO_SLOW
FORMAT_BAD
TASK_INCOMPLETE
UNSAFE
OTHER
```

### 7.2 FeedbackEvaluationCandidate

字段：

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| `candidateId` | string | 候选 ID |
| `messageId` | string | 反馈消息 |
| `runId` | string | 任务 |
| `traceId` | string | trace |
| `contextPackId` | string | 上下文 |
| `artifactId` | string | 产物 |
| `reason` | enum | 反馈原因 |
| `userComment` | string | 用户补充 |
| `status` | enum | `PENDING_REVIEW`、`ACCEPTED`、`REJECTED` |

### 7.3 发布质量指标

至少沉淀：

- Answer quality。
- Source groundedness。
- Citation completeness。
- Task completion。
- Latency。
- Cost per task。

## 8. 模型路由和成本透明

### 8.1 Routing Policy

按任务类型路由：

| 任务 | 默认模型档位 |
| --- | --- |
| 快速问答 | fast |
| 深度研究 | reasoning + long context |
| 文件问答 | long context + retrieval |
| 报告生成 | reasoning + writing |
| 低额度用户 | economy |

### 8.2 用户提示

前端不展示 token 细节，展示：

- 预计耗时：短 / 中 / 长。
- 预计消耗：低 / 中 / 高。
- 当前额度：可用 / 接近上限 / 已超限。
- 降级提示：当前高峰，已切换为快速模式。

### 8.3 API

```http
GET /api/me/quota-summary
GET /api/agent-runs/{runId}/cost-summary
```

## 9. 滥用防护

第一版覆盖：

- IP + userId + taskTemplateId 限流。
- 文件上传大小和频率限制。
- 搜索/抓取总量限制。
- 高成本任务并发限制。
- 可疑输入进入安全拒绝或低风险模式。

## 10. 前端实施切片

### Task 3.1：记忆中心

- 新增页面或设置抽屉。
- 支持查看、编辑、删除、禁用。
- 支持隐私模式。

### Task 3.2：任务模板入口

- 聊天输入区增加模板选择。
- 模板选择影响请求参数和 UI 预期。

### Task 3.3：文件上传

- 输入区支持上传。
- 附件卡片显示解析状态。
- 任务发起时携带 attachmentIds。

### Task 3.4：反馈原因

- 点踩后选择原因。
- 支持用户补充说明。

### Task 3.5：额度与成本提示

- 高成本任务前提示。
- 超限时展示明确原因。

## 11. 测试计划

后端：

```powershell
.\mvnw -pl seahorse-agent-kernel,seahorse-agent-adapter-repository-jdbc,seahorse-agent-adapter-web -am test
```

前端：

```powershell
npm run build
```

建议新增测试：

- `UserMemoryCenterServiceTests`
- `PrivacyModeContextPackTests`
- `TaskTemplateQueryTests`
- `ConversationAttachmentAclTests`
- `FeedbackEvaluationCandidateTests`
- `QuotaSummaryControllerTests`

## 12. 退出标准

1. 用户能查看、编辑、删除自己的长期记忆。
2. 隐私模式能阻止长期记忆读写。
3. 用户能从模板发起任务。
4. 用户能在聊天中上传文件并用于任务。
5. 差评能沉淀为评测候选。
6. 高成本任务有额度和耗时提示。
7. 滥用防护覆盖聊天、上传、搜索、抓取和高成本任务。


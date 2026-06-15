# Skill 系统运维指南

## 概述

Skill（技能）是 Seahorse Agent 的可复用指令集模块。每个 Skill 封装了特定领域的 Markdown 指令，在对话运行时注入模型上下文，引导模型按照预设方法论执行任务。

### 核心概念

| 概念 | 说明 |
|------|------|
| **Skill** | 一个命名的 Markdown 指令集，包含 frontmatter 元数据和正文指令 |
| **Revision** | Skill 的不可变版本快照，每次编辑/安装会创建新 revision |
| **Binding** | Agent Version 与 Skill 的绑定关系，决定运行时注入哪些 Skill |
| **InjectMode** | 注入模式：`METADATA_AND_BODY`（默认，直接注入正文）或 `METADATA_ONLY`（仅注入元数据） |

### 技能分类

| 类别 | 来源 | 说明 |
|------|------|------|
| **PUBLIC** | 内置 / 安装 | 系统预置或管理员安装的通用技能 |
| **CUSTOM** | 用户创建 | 管理员通过管理后台创建的自定义技能，可编辑和回滚 |

## 内置技能目录

内置技能存放在 `seahorse-agent-spring-boot-autoconfigure/src/main/resources/skills/public/` 下，每个子目录为一个技能：

```
skills/public/
├── deep-research/SKILL.md
├── data-analysis/SKILL.md
├── code-documentation/SKILL.md
├── ... (共 21 个内置技能)
```

### 启动时自动导入

`BuiltInAgentSkillRegistrar`（Spring `ApplicationRunner`）在应用启动时扫描 `classpath*:/skills/public/*/SKILL.md`，自动导入所有内置技能。

- 首次启动：创建 Skill 并生成首个 revision
- 后续启动：如果内容变化（contentHash 不同），自动创建新 revision
- 导入失败：记录 WARN 日志（含 skillName），不阻断启动

### 导入失败排查

```bash
# 查看导入失败日志
grep "Failed to import built-in Agent skill" logs/application.log

# 常见原因
# 1. SKILL.md frontmatter 格式不合规（缺少 name/description）
# 2. 安全扫描器检测到不安全内容（如 prompt injection 模式）
# 3. 数据库连接失败
```

## Skill 文件格式

每个 SKILL.md 文件由 frontmatter（YAML）和正文（Markdown）两部分组成：

```markdown
---
name: deep-research
description: Conduct thorough multi-source research with citations.
tags:
  - research
  - analysis
allowed_tools:
  - web_search
  - web_fetch
---

# Deep Research Methodology

## When to use
User asks to research, investigate, or analyze a topic...

## Method
1. Break the topic into sub-questions...
```

### frontmatter 字段

| 字段 | 必需 | 类型 | 说明 |
|------|:----:|------|------|
| `name` | 是 | string | 技能名称，kebab-case，3-128 字符 |
| `description` | 是 | string | 技能描述，展示在管理后台和选择器中 |
| `tags` | 否 | string[] | 标签列表，用于搜索和分类 |
| `allowed_tools` | 否 | string[] | Skill 工具策略声明；可用于运行时收窄工具集合，但永远不能扩展 Agent/Version 已授权工具 |

### 命名规则

- 仅允许小写字母、数字、连字符（`-`）
- 必须以字母或数字开头和结尾
- 长度 3-128 字符
- 示例：`deep-research`、`data-analysis`、`code-review`

## API 接口

### 管理接口（需管理员权限）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/skills` | 分页列表（支持 keyword 搜索） |
| GET | `/api/skills/{name}` | 查看详情 |
| POST | `/api/skills/custom` | 创建 CUSTOM Skill |
| PUT | `/api/skills/custom/{name}` | 更新 CUSTOM Skill（创建新 revision） |
| POST | `/api/skills/install` | 安装 Skill（等同于 createCustom） |
| POST | `/api/skills/{name}/enable` | 启用 Skill |
| POST | `/api/skills/{name}/disable` | 禁用 Skill |
| DELETE | `/api/skills/custom/{name}` | 删除 CUSTOM Skill |
| GET | `/api/skills/custom/{name}/history` | 查看 revision 历史 |
| POST | `/api/skills/custom/{name}/rollback` | 回滚到指定 revision |

### 绑定接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/agents/{agentId}/skills` | 查看 Agent 绑定的 Skill |
| PUT | `/api/agents/{agentId}/skills` | 替换 Agent 的 Skill 绑定 |
| GET | `/api/agents/{agentId}/skills/snapshot` | 查看 Agent Version 的 Skill 快照 |

### 聊天接口（用户侧）

| 参数 | 路径 | 说明 |
|------|------|------|
| `selectedSkillNames` | `/rag/v3/chat` | 本轮对话选择的 Skill 名称列表（最多 5 个） |

## 运行时注入流程

```
用户选择 Skill → 前端发送 selectedSkillNames
  → SeahorseChatController 接收参数
    → ChatSelectedSkillResolver 校验并加载（fail-fast）
      → KernelChatInboundService 合并 version-bound + per-turn skills
        → SkillRuntimeComposer 注入到 system prompt
```

### 注入策略

| 条件 | 模式 | 说明 |
|------|------|------|
| 技能数 ≤ 3 且总内容 ≤ 12000 字符 | `METADATA_AND_BODY` | 直接注入完整正文 |
| 技能数 > 3 或总内容 > 12000 字符 | `METADATA_ONLY` | 仅注入元数据，模型可通过 `load_skill_resource` 按需加载已选中 Skill 的 `SKILL.md` |

### 合并优先级

当 Agent Version 已绑定某 Skill 且用户本轮也选择了同名 Skill 时：

```
version-bound skill > per-turn selected skill
```

已发布的 Agent Version 是确定性契约，本轮选择不会覆盖已绑定的 revision。

## 安全与权限

1. **服务端校验**：后端始终重新校验 Skill 的存在性、启用状态和活跃状态
2. **租户隔离**：只允许当前租户的 Skill 被选择
3. **allowed_tools 不授予新权限**：可在策略模式下收窄运行时工具集合，但不扩展实际的 Agent/Version 工具授权（`allowedToolIds`）
4. **Fail-fast 语义**：selectedSkillNames 中任一项校验失败，整个请求报错（不静默跳过）
5. **数量限制**：单次最多选择 5 个 Skill，超限返回 400 错误

### 常见错误

| 错误信息 | 原因 | 处理 |
|----------|------|------|
| `Selected skill not found: xxx` | 技能不存在或属于其他租户 | 检查技能名称和租户 |
| `Selected skill is disabled: xxx` | 技能已被禁用 | 在管理后台启用 |
| `Selected skill is not active: xxx` | 技能状态非 ACTIVE（如 DELETED） | 检查技能状态 |
| `Selected skill has no revision: xxx` | 技能无 revision 记录 | 数据异常，检查数据库 |
| `Too many skills selected: N (maximum 5)` | 超过数量限制 | 减少选择数量 |

## 运维操作

### 添加新的内置技能

1. 在 `seahorse-agent-spring-boot-autoconfigure/src/main/resources/skills/public/` 下创建新目录
2. 编写 `SKILL.md`，确保 frontmatter 包含 `name` 和 `description`
3. 重启应用，`BuiltInAgentSkillRegistrar` 会自动导入

### 更新内置技能

1. 修改对应的 `SKILL.md` 文件内容
2. 重启应用，系统检测 contentHash 变化后自动创建新 revision
3. 已发布 Agent Version 的绑定不受影响（仍指向旧 revision）

### 禁用/启用技能

通过管理后台或 API：

```bash
# 禁用
curl -X POST http://localhost:9090/api/skills/deep-research/disable \
  -H "Content-Type: application/json" \
  -d '{"tenantId": "default"}'

# 启用
curl -X POST http://localhost:9090/api/skills/deep-research/enable \
  -H "Content-Type: application/json" \
  -d '{"tenantId": "default"}'
```

### 回滚技能版本

```bash
# 查看历史
curl http://localhost:9090/api/skills/custom/my-skill/history

# 回滚到指定 revision
curl -X POST http://localhost:9090/api/skills/custom/my-skill/rollback \
  -H "Content-Type: application/json" \
  -d '{"tenantId": "default", "revisionId": "rev-xxx"}'
```

### 数据库表

| 表名 | 说明 |
|------|------|
| `sa_agent_skill` | Skill 主表（name, tenant_id, category, enabled, status, latest_revision_id） |
| `sa_agent_skill_revision` | Revision 历史表（revision_id, skill_name, content, content_hash） |
| `sa_agent_skill_binding` | Agent-Skill 绑定表（agent_id, skill_name, revision_id, inject_mode） |

## 故障排查

### 技能在聊天中不生效

1. 确认技能已启用：`GET /api/skills/{name}` → `enabled: true, status: "ACTIVE"`
2. 确认有 revision：`latestRevisionId` 不为空
3. 检查日志中的 `ChatSelectedSkillResolver` 警告
4. 如果是 Agent 绑定技能，检查 Agent Version 的 `skillSetJson` 是否包含该技能

### 内置技能导入失败

1. 检查 `SKILL.md` frontmatter 格式（name/description 必填）
2. 检查安全扫描器日志（`SkillSecurityScanner`）
3. 确认数据库连接正常且 `sa_agent_skill` 表可写

### 技能选择后报错

1. 检查请求中 `selectedSkillNames` 参数数量（≤ 5）
2. 确认技能名称拼写（kebab-case，小写）
3. 确认 `AgentSkillRepositoryPort` bean 已配置（否则 resolver 缺失会报 `IllegalStateException`）

# Skill 智能匹配功能 - 快速开始

## 功能说明

当用户发起聊天请求时，如果：
- ✅ 用户没有显式选择 Skill（`selectedSkillNames` 为空）
- ✅ Agent Version 没有预绑定 Skill（`skillSetJson` 为空）
- ✅ 智能匹配开关已启用（默认启用）

系统会自动根据用户问题内容推荐合适的 Skill。

## 配置

### 1. 启用智能匹配（默认已启用）

`application.yml`:
```yaml
seahorse:
  agent:
    kernel:
      enable-smart-skill-matching: true  # 默认为 true
```

### 2. 禁用智能匹配

如果你想禁用此功能：
```yaml
seahorse:
  agent:
    kernel:
      enable-smart-skill-matching: false
```

## 使用示例

### 示例 1：研究类问题

**请求**:
```bash
curl -X POST http://localhost:9090/rag/v3/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "帮我深度研究量子计算的最新发展",
    "conversationId": "conv-123",
    "userId": "user-001",
    "selectedSkillNames": null
  }'
```

**系统行为**:
1. 提取关键词: `["研究", "量子", "计算", "发展"]`
2. 匹配 Skill: `deep-research` (得分 0.65)
3. 自动加载并注入 `deep-research` Skill
4. 日志输出:
   ```
   INFO  SkillSmartMatcher - Skill recommendations: [deep-research] (keywords: [研究, 量子, 计算, 发展])
   INFO  KernelChatInboundService - Smart skill matching triggered: recommendations=[deep-research]
   ```

### 示例 2：数据分析问题

**请求**:
```bash
curl -X POST http://localhost:9090/rag/v3/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "分析这份销售数据的趋势并生成可视化图表",
    "conversationId": "conv-456",
    "userId": "user-001"
  }'
```

**系统行为**:
1. 提取关键词: `["分析", "销售", "数据", "趋势", "生成", "可视化", "图表"]`
2. 匹配 Skill: `data-analysis` (得分 0.82)
3. 自动加载并注入 `data-analysis` Skill

### 示例 3：代码审查问题

**请求**:
```bash
curl -X POST http://localhost:9090/rag/v3/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "Review this code for potential bugs and security issues",
    "conversationId": "conv-789",
    "userId": "user-001"
  }'
```

**系统行为**:
1. 提取关键词: `["review", "code", "potential", "bugs", "security", "issues"]`
2. 匹配 Skill: `code-review` (得分 0.75)
3. 自动加载并注入 `code-review` Skill

### 示例 4：混合语言问题

**请求**:
```bash
curl -X POST http://localhost:9090/rag/v3/chat \
  -H "Content-Type: application/json" \
  -d '{
    "question": "请帮我 analyze code 并生成 technical documentation",
    "conversationId": "conv-999",
    "userId": "user-001"
  }'
```

**系统行为**:
1. 提取关键词: `["analyze", "code", "生成", "technical", "documentation"]`
2. 匹配 Skill: `code-review`, `document-generator`
3. 自动加载并注入这些 Skill

## 优先级规则

智能匹配的优先级**最低**，不会覆盖用户选择或预绑定的 Skill：

```
优先级: version-bound > user-selected > smart-matched
```

### 场景 1：有预绑定 Skill

```json
{
  "question": "帮我研究数据",
  "selectedAgentId": "agent-123"  // 该 Agent 预绑定了 skill-A
}
```

**结果**: 使用预绑定的 `skill-A`，**不触发**智能匹配

### 场景 2：用户显式选择 Skill

```json
{
  "question": "帮我研究数据",
  "selectedSkillNames": ["code-review"]  // 用户显式选择
}
```

**结果**: 使用用户选择的 `code-review`，**不触发**智能匹配

### 场景 3：无预绑定 + 无用户选择

```json
{
  "question": "帮我研究数据"
  // 无 selectedAgentId，无 selectedSkillNames
}
```

**结果**: **触发智能匹配**，推荐 `deep-research` 和 `data-analysis`

## 检查日志

启用智能匹配后，查看应用日志：

### 成功匹配

```
INFO  c.m.a.s.a.k.a.c.SkillSmartMatcher - Skill recommendations for question '帮我分析数据': [data-analysis, deep-research] (keywords: [分析, 数据])
INFO  c.m.a.s.a.k.a.c.KernelChatInboundService - Smart skill matching triggered: question='帮我分析数据', recommendations=[data-analysis, deep-research]
```

### 未匹配到 Skill

```
DEBUG c.m.a.s.a.k.a.c.SkillSmartMatcher - No skills matched with sufficient score for question: 今天天气怎么样
```

### 提取关键词失败

```
DEBUG c.m.a.s.a.k.a.c.SkillSmartMatcher - No keywords extracted from question: 请帮我
```

## 优化 Skill 标签

为了提高智能匹配的准确率，建议为每个 Skill 配置精准的标签：

### 好的示例 ✅

```markdown
---
name: data-analysis
description: Analyze data trends and create visualizations
tags:
  - data
  - analysis
  - statistics
  - visualization
  - chart
  - trends
---
```

### 不好的示例 ❌

```markdown
---
name: data-analysis
description: A skill for data stuff
tags:
  - misc
---
```

## 调试技巧

### 1. 检查是否启用

查看启动日志，确认配置已加载：
```
# 查找配置日志
grep "enable-smart-skill-matching" logs/application.log
```

### 2. 检查 Skill 是否可用

```bash
curl http://localhost:9090/api/skills?tenantId=default
```

确认：
- `enabled: true`
- `status: "ACTIVE"`
- `latestRevisionId` 不为空

### 3. 模拟关键词提取

```java
String question = "帮我分析数据";
List<String> keywords = extractKeywords(question);
// 应输出: [分析, 数据]
```

### 4. 查看完整堆栈日志

```bash
# 启用 DEBUG 日志
logging:
  level:
    com.miracle.ai.seahorse.agent.kernel.application.chat.SkillSmartMatcher: DEBUG
```

## 常见问题

### Q1: 智能匹配没有触发？

**检查清单**:
- [ ] 配置是否启用: `enable-smart-skill-matching: true`
- [ ] 是否有预绑定 Skill: 检查 Agent Version 的 `skillSetJson`
- [ ] 是否有用户选择: 检查请求中的 `selectedSkillNames`
- [ ] 日志中是否有关键词: 搜索 "Extracted keywords"

### Q2: 推荐的 Skill 不准确？

**优化方案**:
- 检查 Skill 的 `tags` 配置是否精准
- 检查 `description` 是否包含常见关键词
- 查看日志中的关键词提取结果
- 调整匹配阈值（需要代码修改）

### Q3: 性能问题？

**优化建议**:
- 添加 Skill 列表缓存
- 限制单租户 Skill 数量（建议 < 1000）
- 考虑构建倒排索引

### Q4: 如何禁用特定租户的智能匹配？

当前版本是全局配置。如需按租户配置，需要代码扩展：

```java
// 在 mergeSkills() 中添加租户检查
if (shouldEnableSmartMatching(tenantId)) {
    // 触发智能匹配
}
```

## 下一步

查看完整文档了解更多：
- 📖 [SKILL-SMART-MATCHING.md](./docs/skills/SKILL-SMART-MATCHING.md) - 详细使用指南
- 📖 [SKILL-SMART-MATCHING-IMPLEMENTATION.md](./SKILL-SMART-MATCHING-IMPLEMENTATION.md) - 实现总结
- 📖 [SKILL-OPERATIONS.md](./docs/skills/SKILL-OPERATIONS.md) - Skill 运维指南

---

**需要帮助？** 查看日志中的错误信息或联系开发团队。

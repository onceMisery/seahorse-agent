# Skill 智能匹配功能使用指南

## 概述

智能匹配功能允许系统在用户未显式选择 Skill 且 Agent Version 也未预绑定 Skill 时，根据用户问题内容自动推荐并加载合适的 Skill。

## 功能特性

### 1. 自动触发条件

智能匹配在以下情况下自动触发：

```
用户未选择 Skill (selectedSkillNames = null 或 [])
  AND
Agent Version 未预绑定 Skill (skillSetJson 为空)
  AND
智能匹配开关已启用 (enableSmartSkillMatching = true)
```

### 2. 匹配策略

智能匹配引擎优先使用语义向量匹配，并将规则匹配作为增强和降级路径：

1. 如果配置了 `SkillSemanticMatcher`，先对用户问题生成 embedding，并在 Skill 向量索引中检索语义候选。
2. 语义候选必须重新校验当前 Skill 是否仍然 `enabled=true`、`status=ACTIVE`，且索引 revision 必须等于 Skill 当前 `latestRevisionId`。
3. 规则匹配会并入候选池；当规则层出现强命中时，会提升该候选分数，避免向量相似但领域不准的 Skill 排在前面。
4. 如果 embedding、向量库或语义候选不可用，系统自动降级到规则匹配。

规则匹配使用多维度评分机制：

| 维度 | 权重 | 说明 |
|------|------|------|
| **标签匹配** | 0.5 | 关键词与 Skill 的 tags 直接匹配或领域匹配 |
| **描述匹配** | 0.3 | 关键词在 Skill description 中出现 |
| **名称匹配** | 0.2 | 关键词在 Skill name 中出现 |

### 3. 关键词提取

系统会从用户问题中自动提取：

- **英文关键词**：2+ 字符的有意义单词（排除停用词）
- **中文领域词**：通过领域词典扫描短中文意图词，例如“研究”“数据”“文档”“测试”
- **停用词过滤**：自动过滤 "的"、"了"、"请"、"帮" 等无意义词

### 4. 领域关键词映射

系统内置了领域关键词映射表：

```java
"research" → ["研究", "调研", "调查", "分析", "探索", "study", "investigate", "analyze", "explore"]
"data" → ["数据", "统计", "可视化", "图表", "表格", "data", "statistics", "visualization", "chart"]
"code" → ["代码", "编程", "开发", "重构", "review", "code", "programming", "refactor", "debug"]
"document" → ["文档", "说明", "手册", "注释", "document", "documentation", "manual", "comment"]
"test" → ["测试", "验证", "质量", "QA", "test", "testing", "quality", "verify"]
"design" → ["设计", "架构", "UX", "UI", "原型", "design", "architecture", "prototype"]
```

> 注意：中文文本不会再用贪婪正则切成固定 2-4 字片段，因为这种方式会吞掉“研究”“数据”“文档”等短领域词。当前实现优先扫描领域词典，保证短领域词能稳定命中。

## 配置方法

### 1. 启用/禁用智能匹配

在 `application.yml` 或 `application.properties` 中配置：

```yaml
# application.yml
seahorse:
  agent:
    kernel:
      enable-smart-skill-matching: true  # 默认为 true
```

```properties
# application.properties
seahorse.agent.kernel.enable-smart-skill-matching=true
```

### 2. 调整推荐数量

修改 `SkillSmartMatcher` 或 `SkillSemanticMatcher` 的构造参数（需要代码级配置）：

```java
// 默认推荐 3 个 Skill
new SkillSmartMatcher(repository, 3)

// 自定义推荐数量
new SkillSmartMatcher(repository, 5)
```

### 3. 调整匹配阈值

规则匹配阈值由 `SkillSmartMatcher.MIN_SCORE_THRESHOLD` 控制（默认 0.1）：

```java
private static final double MIN_SCORE_THRESHOLD = 0.1;  // 最低匹配得分
```

语义匹配还包含向量候选阈值和混合排序阈值：`MIN_SIMILARITY_THRESHOLD`、`MIN_HYBRID_SCORE_THRESHOLD`。调整时需要用实际查询样本校准，避免向量分数与规则分数尺度不一致。

## 使用示例

### 示例 1：研究类问题

**用户输入**：
```
帮我深度研究量子计算的最新发展
```

**提取关键词**：
```
["研究"]
```

**匹配结果**：
```
deep-research (匹配得分: 0.65)
  - 标签匹配: "研究" → tags: ["research", "analysis"]
  - 描述匹配: "research" in "Conduct thorough multi-source research"
```

**系统行为**：
- 自动加载 `deep-research` Skill
- 注入到 system prompt
- 日志输出：`Smart skill matching triggered: recommendations=[deep-research]`

### 示例 2：数据分析问题

**用户输入**：
```
分析这份数据的趋势并生成可视化图表
```

**提取关键词**：
```
["分析", "数据", "可视化", "图表"]
```

**匹配结果**：
```
data-analysis (匹配得分: 0.82)
  - 标签匹配: "数据" → tags: ["data"], "可视化" → tags: ["visualization"]
  - 描述匹配: "data", "visualization" in description
```

### 示例 3：代码审查问题

**用户输入**：
```
Review this code for potential bugs and suggest improvements
```

**提取关键词**：
```
["review", "code", "potential", "bugs", "suggest", "improvements"]
```

**匹配结果**：
```
code-review (匹配得分: 0.75)
  - 标签匹配: "code" → tags: ["code"], "review" → tags: ["review"]
  - 名称匹配: "code", "review" in name "code-review"
```

### 示例 4：混合语言问题

**用户输入**：
```
请帮我 analyze code 并生成 documentation
```

**提取关键词**：
```
["analyze", "code", "documentation"]
```

**匹配结果**：
```
code-review (匹配得分: 0.60)
document-generator (匹配得分: 0.55)
```

## 工作流程图

```
┌─────────────────────────────────────────────────────────────┐
│ 1. 用户发起聊天请求                                          │
│    POST /rag/v3/chat                                         │
│    {                                                         │
│      "question": "帮我分析数据趋势",                          │
│      "selectedSkillNames": null  // 未选择                   │
│    }                                                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 2. KernelChatInboundService.mergeSkills()                    │
│    检测条件：                                                 │
│      ✓ versionBound = [] (无预绑定)                          │
│      ✓ perTurn = [] (无用户选择)                             │
│      ✓ enableSmartSkillMatching = true                       │
│      ✓ ChatSelectedSkillResolver 可用                         │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 3. 智能匹配                                                   │
│    优先 SkillSemanticMatcher.match()                          │
│      - 生成问题 embedding                                     │
│      - 搜索 Skill 向量索引                                    │
│      - 过滤禁用/过期 revision 的索引结果                      │
│      - 合并 SkillSmartMatcher 规则候选                        │
│    若语义路径不可用，降级 SkillSmartMatcher.match()            │
│      - 关键词提取: ["分析", "数据", "可视化", "图表"]          │
│      - 查询可用 Skill: repository.page()                      │
│      - 计算匹配得分并取 Top 3                                 │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 4. ChatSelectedSkillResolver.resolve()                       │
│    验证推荐的 Skill:                                          │
│      ✓ data-analysis: enabled=true, status=ACTIVE            │
│      ✓ deep-research: enabled=true, status=ACTIVE            │
│    加载完整 Skill 内容                                        │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 5. SkillRuntimeComposer.compose()                            │
│    注入到 system prompt:                                      │
│    <skills>                                                  │
│    <skill name="data-analysis" revision="rev-123">           │
│      Description: Analyze data trends...                     │
│      Instructions: [完整 Skill 内容]                          │
│    </skill>                                                  │
│    </skills>                                                 │
└─────────────────────────────────────────────────────────────┘
                          ↓
┌─────────────────────────────────────────────────────────────┐
│ 6. Agent 执行                                                │
│    模型根据注入的 Skill 指令执行任务                          │
└─────────────────────────────────────────────────────────────┘
```

## 日志监控

### 关键日志

**智能匹配触发**：
```
INFO  c.m.a.s.a.k.a.c.SkillSmartMatcher - Skill recommendations for question '帮我分析数据趋势': [data-analysis, deep-research] (keywords: [分析, 数据, 趋势])
```

**智能匹配生效**：
```
INFO  c.m.a.s.a.k.a.c.KernelChatInboundService - Smart skill matching triggered: question='帮我分析数据趋势', recommendations=[data-analysis, deep-research]
```

**未匹配到 Skill**：
```
DEBUG c.m.a.s.a.k.a.c.SkillSmartMatcher - No skills matched with sufficient score for question: 今天天气怎么样
```

### 故障排查

| 现象 | 可能原因 | 解决方法 |
|------|---------|---------|
| 智能匹配未触发 | 配置未启用 | 检查 `enable-smart-skill-matching` |
| 没有推荐结果 | 问题关键词过少 | 检查日志中的 `Extracted keywords` |
| 推荐不准确 | 标签配置不当或向量索引过期 | 优化 Skill 的 tags 配置，必要时重建 Skill 向量索引 |
| 新 Skill 没有被语义匹配 | 向量索引尚未生成或生成失败 | 检查 `SkillVectorIndexService` 日志 |
| 匹配耗时过长 | Skill 数量过多或 embedding 服务慢 | 考虑缓存、限流、超时和离线索引优化 |

## 最佳实践

### 1. Skill 标签设计

为每个 Skill 配置**精准的标签**：

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
---
```

### 2. 描述优化

在 `description` 中使用**常见关键词**：

```markdown
---
description: Conduct thorough multi-source research with citations and analysis
---
```

而不是：
```markdown
---
description: A skill for doing research stuff
---
```

### 3. 领域关键词扩展

如果需要支持更多领域，修改 `DOMAIN_KEYWORDS`：

```java
private static final Map<String, List<String>> DOMAIN_KEYWORDS = Map.of(
    "security", List.of("安全", "漏洞", "加密", "security", "vulnerability", "encryption"),
    "performance", List.of("性能", "优化", "速度", "performance", "optimization", "speed")
);
```

### 4. 监控与调优

定期检查日志，分析：

- 匹配成功率：有多少问题成功匹配到 Skill
- 匹配准确率：匹配的 Skill 是否符合用户意图
- 匹配性能：每次匹配的耗时

## 与现有功能的关系

### 优先级规则

```
version-bound skills (最高优先级)
  >
user-selected skills (次高优先级)
  >
smart-matched skills (最低优先级)
```

### 合并示例

**场景 1：有预绑定 + 智能匹配**
```
versionBound = [skill-A, skill-B]
smartMatched = [skill-C]

最终结果 = [skill-A, skill-B]  // 智能匹配被忽略
```

**场景 2：用户选择 + 智能匹配**
```
perTurn = [skill-A]
smartMatched = 不会触发

最终结果 = [skill-A]  // 有用户选择时，智能匹配不触发
```

**场景 3：仅智能匹配**
```
versionBound = []
perTurn = []
smartMatched = [skill-A, skill-B]

最终结果 = [skill-A, skill-B]  // 智能匹配生效
```

## 性能考虑

### 时间复杂度

- 关键词提取：O(n)，n 为问题长度
- 查询可用 Skill：O(1)，假设分页查询
- 匹配计算：O(m × k)，m 为 Skill 数量，k 为关键词数量
- 语义匹配：一次问题 embedding 调用 + 向量库 TopK 检索 + 规则候选融合

### 优化建议

1. **缓存 Skill 列表**：规则匹配会对租户可用 Skill 列表做短 TTL 缓存，避免每次查询数据库
2. **限制 Skill 总量**：单租户不超过 1000 个
3. **缓存 query embedding**：对短时间内重复问题避免重复调用 embedding 服务
4. **设置超时/熔断**：embedding 或向量库异常时快速降级到规则匹配

### 向量索引生命周期

当启用语义匹配时，Skill 向量索引由 `SkillVectorIndexService` 维护：

- 创建 / 更新 / 内置导入 Skill：异步创建或更新索引
- 禁用 / 删除 Skill：异步删除索引
- 重新启用 Skill：按当前 latest revision 重建索引
- 全量修复：可通过 `rebuildAllAsync(tenantId)` 重建当前租户所有可用 Skill 索引

如果没有可用向量数据库适配器，语义匹配 bean 不会创建，聊天链路会直接使用规则匹配。

## 安全性

### 1. 租户隔离

智能匹配严格遵守租户隔离：

```java
repository.page(tenantId, 1, 1000, null)  // 仅查询当前租户的 Skill
```

### 2. Fail-safe 设计

匹配失败不影响正常流程：

```java
try {
    List<String> recommendations = skillSmartMatcher.match(tenantId, question);
    // 使用推荐结果
} catch (Exception ex) {
    LOG.error("Smart skill matching failed, fallback to no skills", ex);
    return List.of();  // 降级为无 Skill
}
```

### 3. 验证机制

推荐的 Skill 仍需通过当前状态校验和 `ChatSelectedSkillResolver` 验证：

- ✓ enabled = true
- ✓ status = ACTIVE
- ✓ latestRevisionId 存在

语义匹配在返回推荐前还会过滤过期向量索引：索引中的 revision 必须等于 Skill 当前 `latestRevisionId`。如果语义候选全部失效，系统会降级到规则匹配。
规则匹配即使命中缓存，也会在返回推荐前重新查询候选 Skill 当前状态，避免禁用或删除后的 Skill 在缓存 TTL 内继续被智能推荐。

## 未来扩展

### 1. 机器学习优化

- 使用用户反馈数据训练模型
- 基于历史对话记录学习匹配模式
- 进一步校准 embedding 与规则分数融合策略

### 2. 个性化推荐

- 记录用户偏好（经常使用的 Skill）
- 基于用户角色推荐（开发者 vs 数据分析师）

### 3. 上下文感知

- 考虑对话历史中已使用的 Skill
- 根据对话主题动态调整匹配策略

### 4. 前端集成

- 在前端显示推荐的 Skill（"💡 建议使用：xxx"）
- 允许用户确认或拒绝推荐

## 总结

智能匹配功能通过以下方式增强了 Skill 系统的易用性：

✅ **自动化**：无需用户手动选择 Skill  
✅ **智能化**：基于内容分析推荐合适的 Skill  
✅ **安全性**：保留所有验证机制，不降低安全标准  
✅ **可控性**：支持配置开关，灵活启用/禁用  
✅ **兼容性**：不影响现有的预绑定和用户选择机制  

通过合理配置 Skill 标签和描述，可以显著提升智能匹配的准确率。

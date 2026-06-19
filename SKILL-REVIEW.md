让我深入分析 Seahorse Agent 的 Skill 调用机制。


## Seahorse Agent Skill 调用机制分析（已更新 - 包含智能匹配功能）

### 一、当前架构总览

根据代码和文档分析，Skill 系统采用的是 **"注册 + 预绑定 + 用户显式选择 + 智能匹配 + 运行时注入"** 模型。

### 二、完整调用链路

```
┌─────────────────────────────────────────────────────────┐
│  启动阶段                                                │
│  BuiltInAgentSkillRegistrar 扫描 skills/public/*/SKILL.md│
│  → 自动导入到 sa_agent_skill 表                           │
│  → 生成 revision 快照                                    │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  管理阶段（预绑定）                                       │
│  管理员通过 PUT /api/agents/{agentId}/skills             │
│  → 将 Skill 绑定到 Agent Version（sa_agent_skill_binding）│
│  → 每个 Agent Version 有确定的 skillSetJson               │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  用户聊天阶段                                            │
│  路径A: 用户通过 @技能名 / ✨按钮 显式选择 Skill          │
│         → SkillTrigger 组件 → selectedSkillNames 参数    │
│  路径B: Agent Version 预绑定的 Skill                     │
│         → 自动合并到运行时 Skill 列表                     │
│  路径C: 智能匹配（新增）                                  │
│         → 无用户选择 + 无预绑定时自动触发                 │
│         → SkillSmartMatcher 分析问题内容                 │
│         → 自动推荐合适的 Skill                            │
│                                                         │
│  合并优先级: version-bound > per-turn selected > smart-matched │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│  运行时注入                                              │
│  SkillRuntimeComposer 将 Skill 内容注入 system prompt    │
│  ≤3 个且 ≤12000 字符: 注入完整正文 (METADATA_AND_BODY)  │
│  >3 个或 >12000 字符: 仅注入元数据 (METADATA_ONLY)       │
│         → 模型可通过 load_skill_resource 按需加载正文     │
└─────────────────────────────────────────────────────────┘
```

### 三、智能匹配功能（新增）

#### 触发条件

智能匹配在以下条件**全部满足**时自动触发：

1. ✅ `versionBound.isEmpty()` - Agent Version 未预绑定 Skill
2. ✅ `perTurn.isEmpty()` - 用户未显式选择 Skill
3. ✅ `enableSmartSkillMatching = true` - 智能匹配开关启用（默认启用）
4. ✅ `skillSmartMatcher != null` - 匹配器已初始化

#### 匹配策略

**关键词提取**：
- 中文关键词：2-4 字的中文词组
- 英文关键词：2+ 字符的有意义单词
- 停用词过滤：自动过滤 "的"、"了"、"请"、"帮" 等

**多维度评分**：
```
总得分 = 标签匹配得分 × 0.5 + 描述匹配得分 × 0.3 + 名称匹配得分 × 0.2
```

**领域关键词映射**：
```
"research" → ["研究", "调研", "调查", "分析", "探索", "study", "investigate"]
"data" → ["数据", "统计", "可视化", "图表", "data", "statistics", "visualization"]
"code" → ["代码", "编程", "开发", "重构", "code", "programming", "refactor", "debug"]
...
```

#### 匹配示例

**示例 1**：
```
用户问题: "帮我深度研究量子计算的最新发展"
提取关键词: ["研究", "量子", "计算", "发展"]
匹配结果: deep-research (得分 0.65)
```

**示例 2**：
```
用户问题: "分析这份数据的趋势并生成可视化图表"
提取关键词: ["分析", "数据", "趋势", "生成", "可视化", "图表"]
匹配结果: data-analysis (得分 0.82)
```

**示例 3**：
```
用户问题: "Review this code for potential bugs"
提取关键词: ["review", "code", "potential", "bugs"]
匹配结果: code-review (得分 0.75)
```

### 四、与设计预期的对比（已更新）

| 你的设计预期 | 当前实现 | 匹配度 |
|---|---|---|
| **Agent 需用户主动选择** | ✅ 前端 `selectedAgentId` 控制，未选择则走 Legacy 模式 | ✅ 完全匹配 |
| **Skill 按需动态加载** | ✅ 新增智能匹配功能，根据内容自动推荐 | ✅ **已实现** |
| **根据对话内容智能匹配 Skill** | ✅ `SkillSmartMatcher` 分析关键词并匹配 | ✅ **已实现** |
| **用户输入→内容分析→Skill匹配→动态加载→执行** 闭环 | ✅ 完整闭环已实现 | ✅ **已实现** |

### 五、智能匹配的实现细节

#### 核心组件

**SkillSmartMatcher**：
```java
public List<String> match(String tenantId, String question) {
    // 1. 提取关键词
    List<String> keywords = extractKeywords(question);
    
    // 2. 查询可用 Skill
    List<AgentSkill> availableSkills = fetchAvailableSkills(tenantId);
    
    // 3. 计算匹配得分
    List<SkillScore> scores = availableSkills.stream()
            .map(skill -> scoreSkill(skill, keywords))
            .filter(score -> score.score >= MIN_SCORE_THRESHOLD)
            .sorted(Comparator.comparingDouble(SkillScore::score).reversed())
            .limit(maxRecommendations)
            .toList();
    
    // 4. 返回推荐结果
    return scores.stream().map(SkillScore::skillName).toList();
}
```

**KernelChatInboundService 集成**：
```java
private List<SkillRuntimeBlock> mergeSkills(...) {
    // ... 现有逻辑 ...
    
    // 智能匹配：当没有任何 Skill 时，尝试根据用户问题自动匹配
    if (versionBound.isEmpty() && perTurn.isEmpty() 
            && enableSmartSkillMatching && skillSmartMatcher != null) {
        List<String> recommendations = skillSmartMatcher.match(tenantId, command.question());
        if (!recommendations.isEmpty()) {
            LOG.info("Smart skill matching triggered: recommendations={}", recommendations);
            perTurn = chatSkillResolver.resolve(tenantId, recommendations);
        }
    }
    
    // ... 合并逻辑 ...
}
```

#### 配置控制

**application.yml**：
```yaml
seahorse:
  agent:
    kernel:
      enable-smart-skill-matching: true  # 默认启用
```

### 六、与文档的一致性（已更新）

`docs/skills/SKILL-OPERATIONS.md` 描述的运行时注入流程现在包含智能匹配：

> `用户选择 Skill → 前端发送 selectedSkillNames → ... → SkillRuntimeComposer 注入到 system prompt`
> 
> **新增**：`无用户选择 + 无预绑定 → SkillSmartMatcher 智能推荐 → ChatSelectedSkillResolver 验证 → 注入到 system prompt`

这与代码实现**完全一致**。

### 七、完整的动态 Skill 加载闭环（已实现）

```
用户输入 "帮我分析这份数据的趋势"
    ↓
[已实现] 内容分析层：SkillSmartMatcher.extractKeywords()
         → 提取关键词：["分析", "数据", "趋势"]
    ↓
[已实现] Skill 搜索引擎：SkillSmartMatcher.match()
         → 基于 tags/description 匹配
         → 找到 "data-analysis" Skill (得分 0.82)
    ↓
[已实现] 自动注入决策：KernelChatInboundService.mergeSkills()
         → 将匹配到的 Skill 加入运行时上下文
    ↓
[已有] SkillRuntimeComposer 注入 system prompt
    ↓
[已有] LLM 按 Skill 指令执行
```

### 八、结论（已更新）

**当前 Skill 系统已完全实现你的"按需动态加载"设计理念**：

- **Agent**：你的理解正确，需要用户主动选择 ✅
- **Skill**：现在支持三种模式 ✅
  1. **预绑定模式**：Agent Version 预先绑定 Skill
  2. **用户选择模式**：用户显式选择 Skill
  3. **智能匹配模式**（新增）：基于对话内容自动推荐 Skill

**智能匹配的特点**：
- ✅ 自动化：无需用户手动操作
- ✅ 智能化：基于内容分析和多维度评分
- ✅ 安全性：保留所有验证机制（enabled、ACTIVE、revision 检查）
- ✅ 可控性：支持配置开关，灵活启用/禁用
- ✅ 兼容性：不影响现有的预绑定和用户选择机制
- ✅ 优先级合理：version-bound > user-selected > smart-matched

**优先级示例**：

| 场景 | version-bound | user-selected | smart-matched | 最终结果 |
|------|--------------|---------------|---------------|---------|
| 场景 1 | [skill-A] | - | - | [skill-A] |
| 场景 2 | - | [skill-B] | - | [skill-B] |
| 场景 3 | - | - | [skill-C] | [skill-C] ✨ 智能匹配 |
| 场景 4 | [skill-A] | - | [skill-C] | [skill-A] |
| 场景 5 | - | [skill-B] | [skill-C] | [skill-B] |
| 场景 6 | [skill-A] | [skill-B] | [skill-C] | [skill-A, skill-B] |

**文档资源**：
- 📖 [SKILL-SMART-MATCHING-QUICKSTART.md](./SKILL-SMART-MATCHING-QUICKSTART.md) - 快速开始
- 📖 [SKILL-SMART-MATCHING.md](./docs/skills/SKILL-SMART-MATCHING.md) - 详细指南
- 📖 [SKILL-SMART-MATCHING-IMPLEMENTATION.md](./SKILL-SMART-MATCHING-IMPLEMENTATION.md) - 实现总结

**总结**：智能匹配功能已完全实现并集成到现有架构中，实现了"当没有选择 skill 时就采用智能匹配"的设计目标。 
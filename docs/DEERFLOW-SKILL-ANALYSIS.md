# DeerFlow Skill 功能深度分析与借鉴方案

版本：v1.0  
日期：2026-06-02  
作者：技术团队

---

## 目录

1. [DeerFlow Skill 核心概念](#deerflow-skill-核心概念)
2. [Skill 架构设计](#skill-架构设计)
3. [Skill 生命周期管理](#skill-生命周期管理)
4. [内置 Skill 清单](#内置-skill-清单)
5. [Skill API 设计](#skill-api-设计)
6. [前端 Skill 管理界面](#前端-skill-管理界面)
7. [Seahorse Agent 借鉴方案](#seahorse-agent-借鉴方案)
8. [实施计划](#实施计划)

---

## DeerFlow Skill 核心概念

### 什么是 Skill？

在 DeerFlow 中，**Skill** 是一种可插拔的能力模块，用于扩展 AI Agent 的功能。

```python
@dataclass
class Skill:
    name: str                      # 技能名称（唯一标识）
    description: str               # 技能描述（触发条件）
    license: str | None            # 许可证
    skill_dir: Path                # 技能目录
    skill_file: Path               # SKILL.md 文件路径
    relative_path: Path            # 相对路径
    category: SkillCategory        # 类别：public / custom
    allowed_tools: list[str] | None  # 允许使用的工具
    enabled: bool = False          # 是否启用
```

### Skill 分类

| 类别 | 说明 | 特点 |
|------|------|------|
| **PUBLIC** | 内置技能 | 平台预置，只读，不可修改 |
| **CUSTOM** | 自定义技能 | 用户创建，可编辑、删除 |

// __CONTINUE_PART2__

### Skill 文件结构

每个 Skill 是一个独立的目录，包含一个 `SKILL.md` 文件：

```
deep-research/
├── SKILL.md              # 必需：技能定义和说明
├── scripts/              # 可选：辅助脚本
├── references/           # 可选：参考文档
└── assets/               # 可选：资源文件
```

### SKILL.md 格式

```markdown
---
name: deep-research
description: Use this skill for ANY question requiring web research...
---

# Deep Research Skill

## Overview
技能概述...

## When to Use This Skill
使用场景...

## Core Principle
核心原则...
```

---

## Skill 架构设计

### 三层加载系统

DeerFlow 使用渐进式加载机制：

```
Level 1: Metadata (name + description)
         ↓ 始终在上下文中 (~100 words)
         
Level 2: SKILL.md body
         ↓ 技能触发时加载 (<500 lines)
         
Level 3: Bundled Resources
         ↓ 按需加载 (unlimited)
```

### 目录结构

```
backend/
├── packages/harness/deerflow/skills/
│   ├── types.py                    # Skill 数据模型
│   ├── parser.py                   # SKILL.md 解析器
│   ├── installer.py                # Skill 安装器
│   ├── validation.py               # Skill 验证器
│   ├── security_scanner.py         # 安全扫描器
│   ├── permissions.py              # 权限管理
│   └── storage/
│       ├── skill_storage.py        # 存储接口
│       └── local_skill_storage.py  # 本地存储实现

├── app/gateway/routers/
│   └── skills.py                   # Skill REST API

skills/
├── public/                         # 内置技能
│   ├── deep-research/
│   ├── data-analysis/
│   ├── ppt-generation/
│   └── skill-creator/
└── custom/                         # 用户自定义技能
```

---

## Skill 生命周期管理

### 1. Skill 安装

**从 .skill 文件安装**：

```python
POST /api/skills/install
{
  "thread_id": "thread_123",
  "path": "mnt/user-data/outputs/my-skill.skill"
}
```

安装流程：
1. 解压 .skill 文件（ZIP 格式）
2. 验证 SKILL.md 格式
3. 安全扫描内容
4. 复制到 skills/custom/ 目录
5. 更新 extensions_config.json
6. 刷新 Skill 缓存

### 2. Skill 启用/禁用

```python
PUT /api/skills/{skill_name}
{
  "enabled": true
}
```

配置存储在 `extensions_config.json`：

```json
{
  "skills": {
    "deep-research": { "enabled": true },
    "data-analysis": { "enabled": false }
  }
}
```

### 3. Skill 编辑

**仅支持 CUSTOM 类型的 Skill**：

```python
PUT /api/skills/custom/{skill_name}
{
  "content": "新的 SKILL.md 内容"
}
```

编辑流程：
1. 验证 Skill 可编辑性
2. 验证 Markdown 格式
3. 安全扫描新内容
4. 保存到文件
5. 记录历史版本
6. 刷新缓存

### 4. Skill 历史管理

每次修改都会记录历史：

```json
{
  "action": "human_edit",
  "author": "human",
  "ts": "2026-06-02T10:30:00Z",
  "file_path": "SKILL.md",
  "prev_content": "旧内容",
  "new_content": "新内容",
  "scanner": {
    "decision": "allow",
    "reason": "No security issues"
  }
}
```

支持回滚到历史版本：

```python
POST /api/skills/custom/{skill_name}/rollback
{
  "history_index": -1  # -1 表示最近的版本
}
```

### 5. Skill 删除

```python
DELETE /api/skills/custom/{skill_name}
```

删除流程：
1. 验证 Skill 类型（仅 CUSTOM 可删除）
2. 记录删除历史
3. 删除文件
4. 更新配置
5. 刷新缓存

---

## 内置 Skill 清单

DeerFlow 提供 **18 个内置 Skill**：

| Skill 名称 | 功能描述 | 使用场景 |
|-----------|---------|---------|
| **deep-research** | 系统化网络研究 | 任何需要深度调研的问题 |
| **data-analysis** | 数据分析 | CSV/Excel 数据分析 |
| **ppt-generation** | PPT 生成 | 创建演示文稿 |
| **chart-visualization** | 图表可视化 | 数据可视化 |
| **frontend-design** | 前端设计 | UI/UX 设计 |
| **code-documentation** | 代码文档 | 生成技术文档 |
| **academic-paper-review** | 学术论文审阅 | 论文分析和评审 |
| **consulting-analysis** | 咨询分析 | 商业分析报告 |
| **newsletter-generation** | 新闻简报生成 | 生成定期简报 |
| **podcast-generation** | 播客生成 | 创建播客内容 |
| **image-generation** | 图像生成 | AI 图像生成 |
| **github-deep-research** | GitHub 深度研究 | 分析开源项目 |
| **skill-creator** | Skill 创建器 | 创建和优化 Skill |
| **find-skills** | Skill 发现 | 查找合适的 Skill |
| **bootstrap** | 项目启动 | 快速启动项目 |
| **claude-to-deerflow** | Claude 迁移 | 从 Claude 迁移 |
| **surprise-me** | 随机惊喜 | 创意生成 |

### 核心 Skill 详解

#### 1. deep-research（深度研究）⭐⭐⭐⭐⭐

**触发条件**：
- 用户问 "what is X", "explain X", "research X"
- 任何内容生成任务前（PPT、文章、设计）

**核心方法论**：

```
Phase 1: Broad Exploration（广度探索）
  ├─ Initial Survey - 初步调查
  ├─ Identify Dimensions - 识别维度
  └─ Map the Territory - 绘制地图

Phase 2: Deep Dive（深度挖掘）
  ├─ Specific Queries - 精确查询
  ├─ Multiple Phrasings - 多种表述
  ├─ Fetch Full Content - 获取完整内容
  └─ Follow References - 追踪引用

Phase 3: Diversity & Validation（多样性与验证）
  ├─ Facts & Data - 事实和数据
  ├─ Examples & Cases - 案例
  ├─ Expert Opinions - 专家观点
  ├─ Trends & Predictions - 趋势预测
  └─ Challenges & Criticisms - 挑战和批评

Phase 4: Synthesis Check（综合检查）
  └─ 验证研究完整性
```

**质量标准**：
- ❌ 停在 1-2 次搜索后
- ❌ 只依赖搜索片段
- ❌ 只搜索一个方面
- ✅ 3-5 个不同角度
- ✅ 完整阅读重要来源
- ✅ 具体数据和案例

#### 2. skill-creator（Skill 创建器）⭐⭐⭐⭐⭐

**功能**：
- 创建新 Skill
- 编辑现有 Skill
- 优化 Skill 描述
- 运行评估测试
- 性能基准测试

**创建流程**：

```
1. Capture Intent（捕获意图）
   ├─ What should this skill do?
   ├─ When should it trigger?
   ├─ What's the expected output?
   └─ Should we set up test cases?

2. Write SKILL.md
   ├─ name + description (frontmatter)
   ├─ Overview
   ├─ When to Use
   ├─ Methodology
   └─ Examples

3. Create Test Cases
   └─ 2-3 realistic prompts

4. Run & Evaluate
   ├─ Spawn subagents with/without skill
   ├─ Draft assertions
   ├─ Grade results
   └─ Generate benchmark

5. Iterate & Improve
   └─ Based on feedback

6. Optimize Description
   ├─ Generate 20 trigger eval queries
   ├─ Run optimization loop
   └─ Apply best description
```

**测试系统**：
- 并行运行 with-skill 和 baseline
- 定量断言（assertions）
- 定性评审（human feedback）
- 性能基准（时间、Token）

---

// __CONTINUE_PART3__

## Skill API 设计

### REST API 端点

| 方法 | 端点 | 功能 | 说明 |
|------|------|------|------|
| GET | `/api/skills` | 列出所有 Skill | 包含 public + custom |
| GET | `/api/skills/{name}` | 获取 Skill 详情 | 单个 Skill 信息 |
| PUT | `/api/skills/{name}` | 启用/禁用 Skill | 更新 enabled 状态 |
| POST | `/api/skills/install` | 安装 Skill | 从 .skill 文件安装 |
| GET | `/api/skills/custom` | 列出自定义 Skill | 仅 CUSTOM 类型 |
| GET | `/api/skills/custom/{name}` | 获取自定义 Skill 内容 | 包含 SKILL.md 内容 |
| PUT | `/api/skills/custom/{name}` | 编辑自定义 Skill | 更新 SKILL.md |
| DELETE | `/api/skills/custom/{name}` | 删除自定义 Skill | 仅 CUSTOM 可删除 |
| GET | `/api/skills/custom/{name}/history` | 获取历史记录 | 版本历史 |
| POST | `/api/skills/custom/{name}/rollback` | 回滚到历史版本 | 恢复旧版本 |

### API 请求/响应示例

**列出所有 Skill**：

```http
GET /api/skills
```

响应：
```json
{
  "skills": [
    {
      "name": "deep-research",
      "description": "Use this skill for ANY question requiring web research...",
      "license": "MIT",
      "category": "public",
      "enabled": true
    },
    {
      "name": "my-custom-skill",
      "description": "Custom skill for data processing",
      "license": null,
      "category": "custom",
      "enabled": false
    }
  ]
}
```

**启用/禁用 Skill**：

```http
PUT /api/skills/deep-research
Content-Type: application/json

{
  "enabled": true
}
```

**获取自定义 Skill 内容**：

```http
GET /api/skills/custom/my-skill
```

响应：
```json
{
  "name": "my-skill",
  "description": "...",
  "category": "custom",
  "enabled": true,
  "content": "---\nname: my-skill\n..."
}
```

**编辑自定义 Skill**：

```http
PUT /api/skills/custom/my-skill
Content-Type: application/json

{
  "content": "---\nname: my-skill\ndescription: Updated description\n---\n\n# My Skill\n..."
}
```

---

## 前端 Skill 管理界面

### 界面组件

根据 DeerFlow 源码，前端有以下 Skill 相关组件：

1. **skill-settings-page.tsx** - Skill 设置页面
2. **skills-section.tsx** - Skills 展示区域
3. **progressive-skills-animation.tsx** - Skills 动画效果

### 功能特性

#### 1. Skill 列表

- 显示所有可用 Skill
- 区分 PUBLIC / CUSTOM 类型
- 显示启用/禁用状态
- 搜索和过滤功能

#### 2. Skill 详情

- 查看 Skill 描述
- 查看许可证信息
- 查看使用场景
- 支持预览 SKILL.md 内容

#### 3. Skill 管理

- 启用/禁用切换开关
- 编辑 CUSTOM Skill（代码编辑器）
- 删除 CUSTOM Skill
- 安装新 Skill（上传 .skill 文件）

#### 4. Skill 历史

- 查看修改历史
- 对比版本差异
- 一键回滚到历史版本

### 安全机制

#### 1. 安全扫描

每次编辑/安装 Skill 时，都会进行安全扫描：

```python
scan = await scan_skill_content(
    content, 
    executable=False, 
    location=f"{skill_name}/SKILL.md"
)

if scan.decision == "block":
    raise HTTPException(
        status_code=400, 
        detail=f"Security scan blocked: {scan.reason}"
    )
```

#### 2. 权限控制

- PUBLIC Skill 只读
- CUSTOM Skill 可编辑/删除
- 工具权限控制（allowed_tools）

---

## Seahorse Agent 借鉴方案

### 核心借鉴点

#### 1. Skill 系统架构 ⭐⭐⭐⭐⭐

**借鉴价值**：极高

**设计亮点**：
- ✅ 清晰的 PUBLIC / CUSTOM 分类
- ✅ 基于 Markdown 的简单格式
- ✅ 渐进式加载机制
- ✅ 完整的生命周期管理
- ✅ 版本历史和回滚

**Seahorse 实施建议**：

```java
// 1. 创建 Skill 领域模型
@Data
@Builder
public class Skill {
    private String id;
    private String name;
    private String description;
    private SkillCategory category;  // PUBLIC / CUSTOM
    private boolean enabled;
    private String content;  // SKILL.md 内容
    private List<String> allowedTools;
}

// 2. Skill 存储服务
@Service
public class SkillStorageService {
    
    public List<Skill> loadSkills(boolean enabledOnly);
    
    public Optional<Skill> findByName(String name);
    
    public void saveSkill(Skill skill);
    
    public void deleteSkill(String name);
    
    public List<SkillHistory> getHistory(String name);
    
    public void rollback(String name, int historyIndex);
}

// 3. Skill REST API
@RestController
@RequestMapping("/api/skills")
public class SkillController {
    
    @GetMapping
    public List<SkillDTO> listSkills();
    
    @GetMapping("/{name}")
    public SkillDTO getSkill(@PathVariable String name);
    
    @PutMapping("/{name}")
    public SkillDTO updateSkill(@PathVariable String name, 
                                @RequestBody UpdateSkillRequest request);
    
    @PostMapping("/install")
    public InstallResponse installSkill(@RequestBody InstallRequest request);
    
    @GetMapping("/custom/{name}")
    public SkillContentDTO getCustomSkill(@PathVariable String name);
    
    @PutMapping("/custom/{name}")
    public SkillContentDTO editCustomSkill(@PathVariable String name, 
                                          @RequestBody EditRequest request);
    
    @DeleteMapping("/custom/{name}")
    public void deleteCustomSkill(@PathVariable String name);
    
    @GetMapping("/custom/{name}/history")
    public List<SkillHistoryDTO> getHistory(@PathVariable String name);
    
    @PostMapping("/custom/{name}/rollback")
    public SkillContentDTO rollback(@PathVariable String name, 
                                    @RequestBody RollbackRequest request);
}
```

#### 2. Skill 触发机制 ⭐⭐⭐⭐

**DeerFlow 做法**：
- Skill 的 `description` 字段是触发条件
- 在 Agent Prompt 中包含所有启用的 Skill metadata
- Agent 根据用户问题自动选择合适的 Skill

**Seahorse 实施建议**：

```java
// Skill 元数据注入到 Agent Prompt
public String buildSystemPrompt(String userId) {
    List<Skill> enabledSkills = skillService.loadSkills(true);
    
    StringBuilder prompt = new StringBuilder();
    prompt.append("You have access to the following skills:\n\n");
    
    for (Skill skill : enabledSkills) {
        prompt.append(String.format("- %s: %s\n", 
            skill.getName(), skill.getDescription()));
    }
    
    return prompt.toString();
}
```

#### 3. 内置 Skill 库 ⭐⭐⭐⭐⭐

**借鉴建议**：为 Seahorse Agent 创建实用的内置 Skill

| Skill 名称 | 功能 | 优先级 |
|-----------|------|--------|
| **deep-research** | 深度网络研究 | P0 |
| **data-analysis** | 数据分析 | P0 |
| **code-review** | 代码审查 | P0 |
| **document-generation** | 文档生成 | P1 |
| **test-generation** | 测试用例生成 | P1 |
| **architecture-design** | 架构设计 | P1 |
| **api-design** | API 设计 | P2 |
| **database-design** | 数据库设计 | P2 |

#### 4. Skill 创建工具 ⭐⭐⭐⭐

**DeerFlow 的 skill-creator**：
- 交互式创建 Skill
- 自动生成测试用例
- 性能基准测试
- 描述优化

**Seahorse 实施建议**：

创建 `/skill-wizard` 命令：

```typescript
// 前端：Skill 创建向导
export function SkillWizard() {
  const [step, setStep] = useState(1);
  const [skillData, setSkillData] = useState({
    name: '',
    description: '',
    content: ''
  });
  
  return (
    <div className="skill-wizard">
      {step === 1 && <SkillBasicInfo onChange={setSkillData} />}
      {step === 2 && <SkillContentEditor data={skillData} />}
      {step === 3 && <SkillTestCase data={skillData} />}
      {step === 4 && <SkillReview data={skillData} />}
    </div>
  );
}
```

#### 5. 安全扫描 ⭐⭐⭐⭐

**DeerFlow 做法**：
- 每次编辑/安装时扫描内容
- 检测恶意代码
- 阻止或警告

**Seahorse 实施建议**：

```java
@Service
public class SkillSecurityScanner {
    
    public ScanResult scan(String content) {
        // 1. 检查危险关键字
        List<String> dangerousPatterns = List.of(
            "rm -rf", "DROP TABLE", "DELETE FROM",
            "eval\(", "exec\(", "Runtime.getRuntime"
        );
        
        for (String pattern : dangerousPatterns) {
            if (content.contains(pattern)) {
                return ScanResult.block(
                    "Detected dangerous pattern: " + pattern
                );
            }
        }
        
        // 2. 检查文件大小
        if (content.length() > 1_000_000) {
            return ScanResult.block("Skill content too large");
        }
        
        // 3. 检查 Markdown 格式
        if (!isValidMarkdown(content)) {
            return ScanResult.block("Invalid Markdown format");
        }
        
        return ScanResult.allow();
    }
}
```

---

## 总结

### DeerFlow Skill 的核心优势

1. **简单但强大**：Markdown 格式，易于创建和维护
2. **渐进式加载**：三层加载机制，性能优化
3. **完整的生命周期**：安装、启用、编辑、历史、回滚
4. **安全可控**：安全扫描、权限控制、历史追踪
5. **丰富的内置库**：18 个高质量内置 Skill
6. **可扩展性强**：用户可创建自定义 Skill

### Seahorse Agent 应该借鉴的要点

✅ **必须实现**（P0）：
1. Skill 基础架构（PUBLIC/CUSTOM 分类）
2. Skill 生命周期管理（启用/禁用/编辑/删除）
3. 核心内置 Skill（至少 6 个）
4. 前端管理界面
5. 安全扫描机制

⚠️ **推荐实现**（P1）：
6. Skill 历史和回滚
7. Skill 创建向导
8. 更多内置 Skill

⭐ **可选实现**（P2）：
9. Skill 性能基准测试
10. Skill 描述优化工具
11. Skill 市场/分享

### 工作量估算

| 阶段 | 任务 | 工作量 |
|------|------|--------|
| **Phase 1** | 后端基础架构 | 7 人天 |
| | 前端管理界面 | 7 人天 |
| **Phase 2** | 内置 Skill 库（6 个） | 6 人天 |
| **Phase 3** | Skill 创建向导 | 3 人天 |
| | 高级功能 | 3 人天 |
| **测试** | 集成测试 + E2E 测试 | 4 人天 |
| **总计** | | **30 人天** |

---

## 下一步行动

1. **Review 本文档**：确认借鉴方案
2. **创建详细设计文档**：补充技术细节
3. **开始 Phase 1 实施**：后端 + 前端基础架构
4. **创建内置 Skill 库**：参考 DeerFlow 最佳实践
5. **测试和迭代**：确保质量

---

**文档版本**：v1.0  
**最后更新**：2026-06-02  
**维护者**：技术团队

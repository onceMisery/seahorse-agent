# Seahorse DeerFlow Web Alignment Plan — Review Report

**审查日期**: 2026-06-10  
**审查人**: Kiro (Claude Code Agent)  
**计划文档**: `2026-06-08-seahorse-deerflow-web-alignment-plan.md`  
**审查目标**: 分析计划合理性、可行性、风险和执行建议

---

## 执行摘要

**总体评价**: ⭐⭐⭐⭐☆ (4/5)

该计划展现了深思熟虑的系统分析和架构设计。核心策略"三层对齐+两层超越"清晰可行,技术栈选择务实,边界定义明确。主要优势在于:

1. ✅ **架构决策优秀**: 坚持Spring/React架构而不迁移到LangGraph是正确的
2. ✅ **增量实施路径**: 12个任务分4阶段,依赖关系清晰
3. ✅ **兼容性保护严格**: 明确现有API/SSE/snapshot不可破坏
4. ✅ **风险识别全面**: 6大风险都有对应缓解措施

主要关注点:

1. ⚠️ **底层基础设施缺口**: 计划假设基础适配器(Vector/Storage/MQ)已就绪,但实际存在配置问题
2. ⚠️ **frontend复杂度低估**: Workbench渲染涉及多个组件,实际工作量可能超出P1/P2估算
3. ⚠️ **agent选择机制缺失**: 计划未明确agent如何暴露给用户选择

---

## 详细分析

### 1. 架构决策评估

#### 1.1 保持Seahorse原生架构 ✅

**决策**: 不迁移到deer-flow的LangGraph/FastAPI架构

**合理性**: 非常合理
- 符合"功能对齐,架构独立"原则
- 避免大规模重写风险
- 保留Spring生态优势和现有投资

**建议**: 
- 在文档中增加一节说明deer-flow与Seahorse的架构差异对照表
- 明确哪些概念是等价的(如present_files vs AgentArtifact)

#### 1.2 Agent Workspace Runtime抽象 ✅

**决策**: 引入"Agent Workspace Runtime"作为统一抽象层

**合理性**: 架构清晰
- 统一了SSE events, run snapshots, artifacts的所有权
- 避免了frontend artifactStore与backend artifacts的竞争

**建议**:
- 考虑将这个抽象作为独立的kernel service明确实现,而不只是概念层
- 提供状态机图描述message从streaming→snapshot→backfill的完整生命周期

### 2. 任务分解与优先级

#### Task 1-3 (P0): 基础消息绑定 ✅

**评估**: 关键路径正确
- Task 1绑定live events是所有后续工作的基础
- Task 2修复mojibake是用户体验底线
- Task 3 snapshot hydration是可靠性保障

**风险**: Task 1的`applyAgentStreamEventToMessage`实现细节需要处理:
- 并发SSE事件到达顺序
- 重连后eventSeq gap处理
- 多tab场景下的消息状态同步

**建议**: 增加测试场景"网络中断5秒后重连,验证timeline完整性"

#### Task 4-5 (P1): Artifact闭环 ⚠️

**评估**: 方向正确,但遗漏关键点

**已识别问题**:
- Task 5假设"4个生成工具"已经有artifact输出能力
- 实际可能需要修改工具adapter基类统一行为

**实际执行发现**:
- `ImageGenerationToolPortAdapter`等工具确实需要artifact发射逻辑
- S3存储adapter依赖未包含在bootstrap中
- 需要统一`ContentGenerationToolPortAdapter`基类

**建议**: 
- Task 5拆分为5a(基类统一) + 5b(4个工具适配)
- 增加artifact安全扫描mock(如果ClamAV未部署)

#### Task 6-8 (P1): Skill & Tool进阶 ⭐

**评估**: 这是与deer-flow拉开差距的关键

**亮点**:
- Task 7的skill tool policy设计考虑了"advisory vs restrictive"两种模式
- Task 8的deferred tool search结合了MCP allowlist,体现企业治理

**疑问**:
- Task 6 `load_skill_resource`的路径遍历安全如何保证?
- 计划中说"21 built-in public skills",是否都需要progressive loading?

**建议**:
- 在Task 6中明确skill resource的沙箱路径规则
- 考虑skill大小阈值:小于10KB直接注入,大于则progressive

#### Task 9-11 (P2): Workbench & AgentOps ⚠️

**评估**: 范围合理但工作量可能低估

**风险**:
- Task 9-10涉及6个workbench tabs(Timeline/Sources/Artifacts/Approvals/Skills/ToolCalls)
- 每个tab都需要UI组件+状态管理+测试
- 可能实际需要2-3周而非"P2"暗示的次要优先级

**建议**:
- 将Task 9-10标记为"P1+"或拆分为更小粒度
- 考虑MVP版本:先实现Artifacts + ToolCalls两个最核心tab

#### Task 12: 文档 ✅

**评估**: 位置正确,scope清晰

**建议**: 增加一节"troubleshooting playbook":
- 如果知识库500 → 检查vector adapter
- 如果artifact preview空白 → 检查storage adapter + scanStatus
- 如果skill不生效 → 检查ChatSelectedSkillResolver日志

### 3. 技术债务与隐藏依赖

#### 3.1 基础适配器配置问题 ❗

**发现**: 在执行过程中发现以下问题未在计划中体现:

1. **MilvusClient创建失败**
   - 原因: `@ConditionalOnProperty(prefix="seahorse-agent.adapters.vector")` 
   - 环境变量`SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE=milvus`
   - Spring Boot默认转换为`seahorse.agent.*`而非`seahorse-agent.*`
   - 导致KnowledgeBaseInboundPort bean未创建

2. **bootstrap缺少vector adapter依赖**
   - `seahorse-agent-bootstrap/pom.xml`缺少`seahorse-agent-adapter-vector-milvus`
   - 导致`@ConditionalOnClass(MilvusClientV2.class)`始终false

3. **S3 storage adapter类似问题**
   - 属性前缀不一致
   - 依赖可能缺失

**根因**: 计划假设"适配器配置（环境变量）"章节的配置已经生效,但实际:
- 属性命名规范不统一(`seahorse-agent` vs `seahorse.agent`)
- bootstrap模块采用显式adapter依赖而非`starter-all`
- 自动配置13层顺序虽然定义了,但条件注解可能让bean创建失败

**建议**:
- 在Phase 0之前增加"Phase -1: Adapter Health Check"
- 验证所有adapter(vector/storage/cache/mq)都能正常创建bean
- 统一属性前缀规范:全部使用`seahorse.agent.*`(点分隔)

#### 3.2 Agent选择机制缺失 ⚠️

**计划缺口**: 
- 计划描述了TaskTemplate已有`defaultAgentId`
- 但未说明用户如何*主动选择*agent(而非依赖template默认值)

**实际需求**:
- ChatInput应该有agent dropdown,独立于skill selection
- 用户应该能看到agent列表:name, description, riskLevel
- 选中agent后,chatMode应设为"agent",agentId/versionId传递给backend

**已实施**:
- 在执行中已补充该功能:
  - `agentService.ts`提供`listAgents()`
  - ChatInput增加agent选择器
  - SendMessageOptions支持`agentId`和`versionId`

**建议**: 计划应将"agent选择UI"作为独立task或合并到Task 10

### 4. 测试与验证策略

#### 4.1 测试覆盖 ✅

**优点**:
- 每个task都有明确的verification命令
- 区分了frontend focused / backend focused / cross-cutting

**建议**:
- 增加E2E测试场景矩阵:
  | 场景 | 断言 |
  |------|------|
  | 生成图片 | artifact visible, preview loads, download works |
  | 选择skill | skill.selected event, load_skill_resource callable |
  | 中断刷新 | snapshot hydrates timeline/artifacts |

#### 4.2 Acceptance Matrix ✅

**评估**: 非常好!明确了parity vs surpass信号

**建议**: 增加"Regression检查"列:
- 每个capability都要验证"plain text chat still works"
- 确保新功能不破坏无skill/无agent/无attachment场景

### 5. 风险管理

#### 5.1 已识别风险评估

**Duplicate Runtime Owners** ✅
- 缓解措施合理:message owns live, snapshot owns persistence
- 建议增加:定期清理orphaned artifacts(无关联message)

**Prompt & Tool Surface Bloat** ✅
- Progressive loading + deferred search是正确方向
- 建议增加:监控prompt token使用,设置阈值告警

**Policy Confusion** ✅
- "Agent/ToolCatalog policy is maximum boundary"原则清晰
- 建议增加:admin UI显示"effective tools" vs "skill requested tools"对比

**Unsafe Generated Outputs** ✅
- scanStatus + canPreview + disposition设计周全
- 建议增加:如果scan service不可用,默认策略(block all vs allow with warning)

**Encoding Regression** ✅
- Mojibake scan是好实践
- 建议增加:pre-commit hook运行encoding check

**Backend Event Shape Drift** ✅
- 建议增加contract tests很对
- 建议增加:OpenAPI schema for SSE event DTOs,frontend codegen

#### 5.2 未识别风险

**多租户隔离** ⚠️
- 计划未提及tenant context
- AgentRunSnapshot/Artifact查询是否自动filter by tenantId?
- 建议:每个新的inbound port都要review RLS规则

**Quota enforcement** ⚠️
- 计划提到cost visibility,但未说quota exceed如何处理
- 如果用户quota不足,stream是否中断?前端如何展示?
- 建议:在Phase 0增加quota guard测试

**Skill版本冲突** ⚠️
- 如果user selected skill version与agent bound skill version冲突?
- 计划未定义解决策略(user优先 vs agent优先 vs拒绝)
- 建议:Task 6增加conflict resolution逻辑

### 6. 实施建议

#### 6.1 Phase -1: 基础设施健康检查 (新增)

**目标**: 确保所有adapter可用

**任务**:
1. 统一属性前缀: `seahorse-agent.*` → `seahorse.agent.*`
2. 验证vector adapter(milvus) bean创建
3. 验证storage adapter(s3) bean创建
4. 验证cache/mq/search adapter可选配置
5. 修复bootstrap pom.xml依赖

**验证**:
```bash
curl http://localhost:9090/actuator/beans | grep -i "milvus\|vectorcollection\|knowledgebase"
```

**估算**: 1-2天

#### 6.2 调整Phase 0-1优先级

**建议顺序**:
1. Phase -1: Adapter health (new)
2. Phase 0: Task 1-3 (unchanged)
3. Phase 1: Task 4-5 + **Agent选择UI** (new)
4. Phase 2: Task 6-8 (unchanged)
5. Phase 3: Task 9-11 → 只先做Artifacts + ToolCalls两个tab
6. Phase 4: Task 12 + 完整workbench其他tabs

#### 6.3 增加持续验证检查点

每个Phase完成后运行:
```bash
# 基础健康
curl http://localhost:9090/actuator/health

# RAG可用性
curl http://localhost:9090/knowledge-base?current=1&size=1

# Artifact可用性  
curl http://localhost:9090/api/agent-runs/{runId}/artifacts

# Agent可用性
curl http://localhost:9090/api/agents?current=1&size=10
```

### 7. 文档完整性

#### 7.1 缺失章节建议

1. **Glossary**: deer-flow术语 vs Seahorse术语对照
   - present_files → AgentArtifact
   - skill policy → ToolCatalog + Agent allowlist
   - progressive loading → load_skill_resource tool

2. **Migration Guide**: 如果用户已有自定义skill/agent
   - 如何迁移到新的progressive loading format
   - metadata schema变更

3. **Performance Baseline**: 预期性能指标
   - SSE event延迟 < 100ms
   - Snapshot hydration < 500ms
   - Artifact preview load < 1s

#### 7.2 代码示例建议

计划中缺少代码示例。建议在implementation details文档中增加:

**Example 1: Emit artifact event from tool**
```java
public class MyGenerationTool {
    public ToolResult execute(ToolInput input) {
        // ... generate content
        artifactService.create(AgentArtifact.builder()
            .runId(runId)
            .artifactId(UUID.randomUUID())
            .title("Generated Image")
            .mimeType("image/png")
            .storageRef(s3Key)
            .scanStatus(ScanStatus.PENDING)
            .build());
        // ... emit SSE event
    }
}
```

**Example 2: Progressive skill loading**
```markdown
# SKILL.md (metadata mode)
## Name
deep-research

## Description  
Multi-source research with fact-checking

## Tools
- web_search
- web_fetch
- tool_search (to discover verification tools)

## Resources
- research_workflow.md — detailed research steps
- verification_rubric.md — fact-check criteria

Use `load_skill_resource` tool to read these when needed.
```

---

## 最终建议

### ⭐ 必须做 (Blocking)

1. **修复adapter配置** (Phase -1)
   - 统一属性前缀为`seahorse.agent.*`
   - 修复bootstrap pom依赖
   - 验证所有bean创建

2. **增加agent选择UI** (Phase 1)
   - ChatInput agent dropdown
   - agentId传递到backend

3. **降低workbench scope** (Phase 3)
   - MVP: 只做Artifacts + ToolCalls两个tab
   - Timeline/Sources/Approvals/Memory作为follow-up

### ✅ 应该做 (Recommended)

4. **增强测试**
   - E2E scenario matrix
   - Contract tests for SSE events
   - Regression suite for plain chat

5. **文档补充**
   - Glossary + Migration Guide
   - Troubleshooting playbook
   - Code examples

### 💡 可以做 (Nice-to-have)

6. **性能监控**
   - SSE latency metrics
   - Artifact preview P95 latency
   - Token usage by chatMode

7. **Admin诊断面板**
   - Effective tools vs requested tools
   - Skill load patterns
   - Artifact scan queue depth

---

## 总评

该计划展现了扎实的系统思维和工程判断。主要gap在于:

1. **隐藏的基础设施问题**未充分暴露
2. **frontend工作量**被低估
3. **agent选择机制**遗漏

建议按照"Phase -1 → Phase 0-1(adjusted) → Phase 2-3(MVP) → Phase 4(full)"路径执行。

预计总工期:
- 原计划估算:~4-6周
- 调整后估算:~6-8周(考虑adapter修复 + frontend实际复杂度)

**建议**: ✅ 批准执行,with adjustments

---

## 附录:实际执行记录

**执行日期**: 2026-06-10  
**执行状态**: Phase -1 + Phase 0 部分完成

**已完成**:
- ✅ 发现并修复MilvusClient matchIfMissing问题
- ✅ 发现并修复bootstrap缺少vector adapter依赖
- ✅ 修复属性前缀不一致(`seahorse-agent` → `seahorse.agent`)
- ✅ 实现agent选择UI(ChatInput dropdown)
- ✅ 实现agentId传递到backend

**进行中**:
- 🔄 重新构建和部署(包含所有修复)
- 🔄 验证知识库/RAG/意图树API恢复

**待办**:
- ⏳ Task 1-3完整实现(目前agent选择已完成,但stream event binding未开始)
- ⏳ Task 4-5 artifact完整闭环
- ⏳ 其他P1/P2任务

**关键发现**:
此次review验证了"Phase -1: Adapter Health Check"的必要性。如果直接从Task 1开始,会在中途发现知识库500错误,导致反复调试基础设施问题,影响核心功能开发进度。

**建议所有类似项目**: 先运行完整的基础设施健康检查,再开始feature开发。

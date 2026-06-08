# Implementation Details 文档 Review

## 审查日期
2026-06-08

## 审查范围
`docs/aegis/plans/2026-06-08-seahorse-deerflow-web-alignment-implementation-details.md` (663行)

---

## 正确性验证

### ✅ 核心契约正确

1. **StreamEventEnvelope 类型**
   - 实际代码: `eventId`, `eventSeq`, `eventType`, `runId`, `stepId?`, `timestamp`, `typedPayload`
   - 文档引用: 使用 `eventSeq`, `eventType`, `typedPayload`, `runId`, ~~`messageId`~~ ❌
   - **问题**: 实际类型**没有** `messageId` 字段!

2. **useStreamResponse.ts resume 支持**
   - 文档声明: ✅ 已支持 `resumeRunId` + `lastEventSeq`
   - 实际代码: ✅ 第67-75行确认

3. **mergeById helper 签名**
   - 文档提供: ✅ 泛型 + customMerge 参数
   - 与现有 `artifactStore.ts` 的 `mergeArtifacts` 模式一致

4. **AgentRunSnapshot.lastEventSeq**
   - 文档声明: ✅ 已存在
   - 实际代码: ✅ Java record 第36行确认

---

## ❌ 发现的错误

### E1: StreamEventEnvelope.messageId 不存在

**位置**: 文档多处测试 fixture
**实际**: `StreamEventEnvelope` 只有 `runId`, 没有 `messageId`
**影响**: Task 1 的测试代码会编译失败

**修正**:
```typescript
// 文档建议 (错误)
const envelope: StreamEventEnvelope = {
  eventType: "agent.step.finished",
  typedPayload: { ... },
  eventSeq: 10,
  runId: "run-1",
  messageId: "msg-1"  // ❌ 此字段不存在
};

// 正确做法
// StreamEventEnvelope 不携带 messageId
// 在 chatStore 中通过 streamingMessageId 状态定位目标消息
const envelope: StreamEventEnvelope = {
  eventType: "agent.step.finished",
  typedPayload: { ... },
  eventSeq: 10,
  runId: "run-1",
  eventId: "evt-1",
  timestamp: new Date().toISOString()
};
```

**建议修复**:
- 删除所有测试 fixture 中的 `messageId: "msg-1"` 行
- 在 Task 1 说明中补充:"StreamEventEnvelope 不携带 messageId,通过 chatStore.streamingMessageId 定位当前助理消息"

---

### E2: Task 5 的 ExecutionContext 未定义来源

**位置**: Task 5 伪代码使用 `currentRunId()` / `currentMessageId()`
**问题**: `ExecutionContext` 或 `ThreadLocal<AgentRunContext>` 在当前代码库中**不存在**
**影响**: 实施者不知道如何获取 runId/messageId

**建议补充**:
```java
// Option 1: 新建 ThreadLocal ExecutionContext (需要额外工作)
public class AgentExecutionContext {
    private static final ThreadLocal<Context> CURRENT = new ThreadLocal<>();
    
    public static Context current() {
        return CURRENT.get();
    }
    
    public static void set(Context context) {
        CURRENT.set(context);
    }
    
    public static void clear() {
        CURRENT.remove();
    }
    
    public record Context(String runId, String messageId, String tenantId) {}
}

// Option 2: 从工具调用时显式传递 (推荐,最小侵入)
// 修改 ToolPort.invoke 签名增加 ExecutionMetadata 参数
public interface ToolPort {
    ToolInvocationResult invoke(
        String toolCallId, 
        String toolId, 
        Map<String, Object> arguments,
        ExecutionMetadata metadata  // NEW
    );
}

// Option 3: 从 KernelAgentLoop 事件流传递
// AgentArtifactCreatedEvent 携带 runId/messageId
```

**推荐**: Option 2 (显式传递),因为:
- 不依赖 ThreadLocal (避免清理遗漏)
- 类型安全
- 测试友好

---

### E3: Task 6 的 SkillSelectionContext 未定义

**位置**: `LoadSkillResourceToolPortAdapter` 使用 `selectionContext.isSkillSelected()`
**问题**: 此类不存在,且与 `ChatSelectedSkillResolver` 的职责重叠
**影响**: 实施者不知道如何判断 skill 是否已选中

**建议修正**:
```java
// 实际应从 KernelAgentLoop 或 ExecutionMetadata 传递已选中的 skill 列表
public class LoadSkillResourceToolPortAdapter implements DescribedToolPort {
    private final AgentSkillRepositoryPort skillRepository;
    
    @Override
    public ToolInvocationResult invoke(
        String toolCallId, 
        String toolId, 
        Map<String, Object> arguments,
        ExecutionMetadata metadata  // 包含 selectedSkillNames
    ) {
        String skillName = (String) arguments.get("skillName");
        
        // Security: only selected skills
        if (!metadata.selectedSkillNames().contains(skillName)) {
            return ToolInvocationResult.failed("Skill not selected: " + skillName);
        }
        
        // Security: no parent traversal
        if (resourcePath.contains("..") || resourcePath.startsWith("/")) {
            return ToolInvocationResult.failed("Invalid resource path");
        }
        
        // ... load resource
    }
}

// ExecutionMetadata 包含运行时上下文
public record ExecutionMetadata(
    String runId,
    String messageId,
    String tenantId,
    Set<String> selectedSkillNames,
    Set<String> allowedToolIds
) {}
```

---

## ⚠️ 不完整但可接受

1. **Task 9-11 仅概要**
   - 文档声明:"详细规格按需提供"
   - 评估:✅ 可接受,Phase 2 执行前再细化

2. **Artifact 持久化触发点**
   - Task 5 伪代码假设在 `AbstractChatContentGenerationToolPortAdapter.invoke` 内持久化
   - 实际:需确认 `KernelAgentLoop` 是否已有统一的 artifact 持久化钩子
   - 影响:⚠️ 中等,执行时可能需要重新设计触发点

3. **Event publisher 接口**
   - 文档假设存在 `eventPublisher.publish(new AgentArtifactCreatedEvent(...))`
   - 实际:需确认是 `ApplicationEventPublisher` 还是自定义接口
   - 影响:✅ 低,Spring 标准模式

---

## ✅ 测试 Fixture 质量

1. **Task 1 测试覆盖全面**:
   - ✅ 合并 by id
   - ✅ 跳过 stale 事件
   - ✅ Artifact append
   - ✅ 竞态用例:"live event during snapshot fetch not lost"

2. **Task 6 安全测试完整**:
   - ✅ 未选中 skill 拒绝
   - ✅ Parent traversal 拒绝
   - ✅ 正常加载路径

3. **Backend 测试模式规范**:
   - ✅ 使用 AssertJ `assertThat`
   - ✅ Mockito `verify` + `argThat`
   - ✅ 清晰的 Given/When/Then 结构

---

## 总评

| 维度 | 评分 | 说明 |
|------|------|------|
| 类型契约准确性 | ⭐⭐⭐ | StreamEventEnvelope.messageId 错误,其他正确 |
| 实施路径清晰度 | ⭐⭐⭐⭐ | Task 1-4 完整,Task 5-8 缺执行上下文定义 |
| 测试覆盖完整性 | ⭐⭐⭐⭐⭐ | RED 用例覆盖幂等/竞态/安全/边界 |
| 与现有代码一致性 | ⭐⭐⭐⭐ | 大部分验证正确,3处需补充 |
| 可执行性 | ⭐⭐⭐⭐ | 修复3处错误后即可执行 |

**平均分**: 4.0 / 5.0

---

## 修复建议(优先级排序)

### 🔴 P0 必修(阻断执行)
1. **删除所有 `messageId` 从 `StreamEventEnvelope` 引用**
   - 影响:Task 1 测试代码编译失败
   - 工作量:10分钟(查找替换)
   - 文件:所有测试 fixture

### 🟠 P1 应修(执行时困扰)
2. **补充 ExecutionContext/ExecutionMetadata 获取方案**
   - 影响:Task 5 实施者不知道如何获取 runId/messageId
   - 工作量:30分钟(写3种方案+推荐)
   - 位置:Task 5 开头增加"Context Passing Strategy"一节

3. **替换 SkillSelectionContext 为 ExecutionMetadata**
   - 影响:Task 6 实施者需要猜测如何判断 skill 已选中
   - 工作量:20分钟(修订代码示例)
   - 位置:Task 6 的 `LoadSkillResourceToolPortAdapter` 示例

### 🟡 P2 建议(Phase 2前补全)
4. **Task 9-11 详细规格**
   - 影响:Phase 2 执行前需要回头补
   - 工作量:2小时
   - 内容:ToolCall/Skill/Event 类型定义 + 测试 fixture

---

## 结论

**文档整体质量高(4.0/5.0)**,可以支撑 Phase 0-1 执行,但需要**先修复3个 P0/P1 问题**:

1. ✅ StreamEventEnvelope.messageId (P0) — **10分钟修复**
2. ✅ ExecutionContext 方案 (P1) — **30分钟补充**
3. ✅ SkillSelectionContext 替换 (P1) — **20分钟修订**

**修复后评分**: ⭐⭐⭐⭐⭐ (5.0/5.0)

**总修复时间**: < 1小时

---

## 推荐执行顺序

1. **立即修复**: P0 + P1 (1小时内)
2. **Phase 0 执行**: Task 1→2→3 (修复后的文档)
3. **Phase 1 准备**: 验证 artifact 持久化钩子存在性
4. **Phase 1 执行**: Task 4→5
5. **Phase 2 准备**: 补全 Task 9-11 详细规格 (2小时)
6. **Phase 2 执行**: Task 6→7→8→9→10→11→12

---

## Review 签名

- **Reviewer**: Claude Code (Opus 4.8, Aegis Mode)
- **Date**: 2026-06-08
- **Method**: 交叉验证文档声明与实际代码库(frontend/src/types, hooks, stores; backend kernel/ports)
- **Confidence**: High (直接读取源码验证,非记忆推断)

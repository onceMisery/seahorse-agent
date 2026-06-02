# 记忆系统修复待办清单

日期：2026-06-02  
状态：进行中

---

## ✅ 已完成的修复

### 1. SeahorseMemoryController 用户隔离修复 ✅
- 提交：19af95cd
- 10 个管理端点支持可选 userId
- 默认使用 "system" 用户

### 2. 熔断器保护机制 ✅
- 文件：MemoryRefinerBatchCircuitBreaker.java
- 限制单次操作 ≤ 8 个
- 限制删除比例 ≤ 70%

### 3. 幂等性保护机制 ✅
- 文件：MemoryOperationGateway.java
- tryStart() 检查重复操作
- markCompleted() / markFailed() 记录状态

### 4. GenerationTag 工具类 ✅
- 文件：seahorse-agent-kernel/.../memory/GenerationTag.java
- 已创建完整实现
- 已创建单元测试 GenerationTagTest.java

---

## ⚠️ 待完成的修复

### 1. 🔴 P0 - 在 DefaultMemoryEnginePort 中集成 GenerationTag

**需要修改的文件**：
```
seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/memory/DefaultMemoryEnginePort.java
```

**修改内容**：

#### 步骤 1：添加 generationId 检查方法

在细化前检查代数深度：

```java
/**
 * 检查是否应该跳过细化（防止闭环）
 */
private boolean shouldSkipRefinementDueToGeneration(List<MemoryRefinementMemory> memories) {
    for (MemoryRefinementMemory memory : memories) {
        String generationId = extractGenerationIdFromMemory(memory);
        GenerationTag tag = GenerationTag.parse(generationId);
        
        // 检查1：超过最大代数
        if (tag.exceedsMaxGeneration()) {
            return true;
        }
        
        // 检查2：相似度过高（可能重复细化）
        if (tag.hasHighSimilarity()) {
            return true;
        }
    }
    return false;
}

private String extractGenerationIdFromMemory(MemoryRefinementMemory memory) {
    if (memory.metadata() == null) {
        return "";
    }
    Object genId = memory.metadata().get("generationId");
    return genId == null ? "" : genId.toString();
}
```

#### 步骤 2：在细化前调用检查

在 `ingest()` 方法中，找到细化调用处：

```java
// 查找这段代码：
if (options.refinerEnabled()) {
    List<MemoryRefinementMemory> refinementMemories = refinementInputBuilder.build(...);
    
    // 🆕 添加这里：
    if (shouldSkipRefinementDueToGeneration(refinementMemories)) {
        return MemoryIngestionResult.skipped("refinement_depth_exceeded");
    }
    
    refinementResult = memoryRefinerPort.refine(refinementRequest);
    // ...
}
```

#### 步骤 3：在存储时设置 nextGeneration

在存储细化记忆时，设置新的 generationId：

```java
// 在存储 Profile 时（大约在 line 588 附近）
String profileGenerationId = isBlank(profileSlot) ? "" : profileSlot + ":" + SnowflakeIds.nextIdString();

// 🆕 添加：计算下一代 generationId
String sourceGenerationId = extractGenerationIdFromMemory(sourceMemory);
GenerationTag sourceTag = GenerationTag.parse(sourceGenerationId);
GenerationTag nextTag = sourceTag.nextGeneration(sourceMemoryId, 0.85); // 相似度可以从 metadata 获取

metadata.put("generationId", nextTag.serialize());
metadata.put("generation", nextTag.getGeneration());
metadata.put("sourceMemoryId", nextTag.getSourceMemoryId());
```

**预计工作量**：2 小时

---

### 2. 🟡 P1 - 相似度去重（可选，依赖向量库）

**文件**：DefaultMemoryEnginePort.java

**实施内容**：

```java
/**
 * 检查是否存在相似记忆（防止重复细化）
 */
private boolean hasSimilarMemoryInVector(String userId, String content) {
    if (memoryVectorPort == null) {
        return false; // 向量库不可用，跳过检查
    }
    
    try {
        // 搜索相似度 > 0.85 的记忆
        List<MemoryVectorSearchResult> similar = 
            memoryVectorPort.searchSimilar(userId, content, 0.85, 5);
        
        return !similar.isEmpty();
    } catch (Exception e) {
        // 向量搜索失败不影响主流程
        return false;
    }
}

// 在 shouldSkipRefinementDueToGeneration() 中调用
if (hasSimilarMemoryInVector(userId, candidate.content())) {
    return true;
}
```

**预计工作量**：1 小时

---

### 3. 🟡 P1 - 聚合缓冲隔离验证

**需要验证的文件**：
```bash
# 查找 Redis 实现
find . -name "*RedisMemoryAggregation*"
```

**验证步骤**：
1. 检查 Redis key 是否包含 userId
2. 确认格式：`memory:buffer:{userId}:{conversationId}`
3. 添加防御性检查

**预计工作量**：1 小时

---

### 4. 🟡 P1 - Correction 事务保护

**需要修改的文件**：
```bash
# 查找方法
grep -rn "writeOccupationCorrection" seahorse-agent-kernel/src/
```

**修改内容**：

```java
import org.springframework.transaction.annotation.Transactional;

@Transactional(rollbackFor = Exception.class)
public void writeOccupationCorrection(...) {
    try {
        correctionLedgerPort.append(correction);
        profilePort.write(newProfile);
        markProfileSlotFragmentsObsolete(oldSlotKey);
    } catch (Exception e) {
        throw new MemoryConsistencyException(
            "Failed to sync correction", e
        );
    }
}
```

**预计工作量**：1 小时

---

## 📋 实施计划

### 今天完成（2026-06-02）
- [x] 创建 GenerationTag.java ✅
- [x] 创建 GenerationTagTest.java ✅
- [ ] 在 DefaultMemoryEnginePort 中集成 GenerationTag（2 小时）

### 明天完成（2026-06-03）
- [ ] 相似度去重（1 小时）
- [ ] 聚合缓冲隔离验证（1 小时）
- [ ] Correction 事务保护（1 小时）
- [ ] 补充集成测试

---

## 🧪 测试清单

- [ ] GenerationTag 单元测试通过
- [ ] 闭环场景测试（三轮细化）
- [ ] 高相似度跳过测试
- [ ] 并发缓冲隔离测试
- [ ] Correction 事务回滚测试

---

## 📊 完成度

| 修复项 | 状态 | 完成度 |
|--------|------|--------|
| 用户隔离 | ✅ 完成 | 100% |
| 熔断器 | ✅ 完成 | 100% |
| 幂等性 | ✅ 完成 | 100% |
| GenerationTag | ✅ 完成 | 100% |
| **集成到引擎** | ⚠️ 待完成 | **0%** |
| 相似度去重 | ⚠️ 待完成 | 0% |
| 缓冲隔离验证 | ⚠️ 待完成 | 0% |
| Correction 事务 | ⚠️ 待完成 | 0% |

**总完成度**：50%

---

**预计全部完成时间**：2026-06-03（明天）

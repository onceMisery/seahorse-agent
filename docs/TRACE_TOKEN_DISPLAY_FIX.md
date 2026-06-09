# 链路追踪 Token 使用量和模型参数展示修复

## 问题描述

链路追踪详情页面没有清晰展示 token 使用量和模型参数信息，这些数据虽然存储在 `extra_data` JSON 字段中，但前端展示不够突出和友好。

## 修复内容

### 前端改进 (`RagTraceDetailPage.tsx`)

#### 1. 新增提取函数

```typescript
function modelParams(extra: unknown) {
  const value = pickValue(extra, ["modelParams", "modelParameters", "parameters", "config"]);
  return isRecord(value) ? value : null;
}
```

从 `extra_data` 中提取模型参数，支持多种字段名变体。

#### 2. 增强 Agent 调用概览 (`RunOverview` 组件)

**改进前：**
- Token 使用量以紧凑文本形式显示在 `KeyValue` 组件中
- 模型信息和入口方法混在一起

**改进后：**
- **模型信息卡片**：独立展示入口方法和模型名称，带 `Cpu` 图标
- **Token 使用量卡片**：绿色高亮卡片，逐行展示 token 指标（如 `promptTokens: 120`, `completionTokens: 340`, `totalTokens: 460`）
- **模型参数卡片**：蓝色卡片，展示温度、top_p、max_tokens 等参数，支持滚动查看

#### 3. 增强 Span 详情面板 (`SpanDetailPanel` 组件)

新增 **"模型与 Token"** 区块，包括：
- **Model**：当前 Span 使用的模型
- **Token 使用量**：绿色高亮展示该节点的 token 消耗
- **模型参数**：蓝色卡片展示该节点的模型配置

只有当 Span 的 `extra_data` 包含相关字段时才显示此区块。

#### 4. 新增图标

导入 `Cpu` 和 `Settings` 图标用于模型信息和参数展示。

## 数据结构约定

### Run 级别 `extra_data` 示例

```json
{
  "input": "用户的问题",
  "output": "Agent 的回答",
  "model": "gpt-4",
  "tokenUsage": {
    "promptTokens": 120,
    "completionTokens": 340,
    "totalTokens": 460
  },
  "modelParams": {
    "temperature": 0.7,
    "top_p": 0.95,
    "max_tokens": 2000
  }
}
```

### Node 级别 `extra_data` 示例

```json
{
  "input": "检索查询",
  "output": "检索结果",
  "model": "text-embedding-ada-002",
  "tokenUsage": {
    "totalTokens": 50
  }
}
```

## 字段自动识别

前端支持以下字段名变体（优先级从左到右）：

- **Token 使用量**: `tokenUsage`, `usage`, `tokens`
- **模型参数**: `modelParams`, `modelParameters`, `parameters`, `config`
- **模型名称**: `model`, `modelId`, `modelName`, `provider`

## 视觉设计

- **Token 使用量**：`emerald-50` 背景，`emerald-700` 标题，突出成本相关信息
- **模型参数**：`blue-50` 背景，`blue-700` 标题，技术配置信息
- **模型信息**：`white` 背景，标准样式

## 后端集成指南

在记录 trace 时，确保将 token 使用量和模型参数写入 `extra_data`：

```java
Map<String, Object> extraData = new HashMap<>();
extraData.put("model", "gpt-4");
extraData.put("tokenUsage", Map.of(
    "promptTokens", 120,
    "completionTokens", 340,
    "totalTokens", 460
));
extraData.put("modelParams", Map.of(
    "temperature", 0.7,
    "top_p", 0.95,
    "max_tokens", 2000
));

run.setExtraData(objectMapper.writeValueAsString(extraData));
```

## 测试建议

1. 创建一个包含完整 token 信息的 trace
2. 访问链路详情页，验证：
   - Run 概览区显示 token 使用量卡片
   - Run 概览区显示模型参数卡片（如果有）
   - 选择包含 token 信息的 Span 时，详情面板显示"模型与 Token"区块
3. 测试没有 token 信息的 trace，确保相关卡片正确隐藏

## 文件修改清单

- `frontend/src/pages/admin/traces/RagTraceDetailPage.tsx` - 主要修改文件

## 后续优化建议

1. 在列表页增加 token 消耗总计列
2. 支持 token 成本估算（基于模型定价）
3. 增加 token 使用量趋势图表
4. 支持按 token 消耗排序和筛选

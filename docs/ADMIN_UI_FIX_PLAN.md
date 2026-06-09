# 管理后台问题修复方案

**日期**: 2026-06-07  
**问题描述**: 
1. 文档数显示为 "O1150" 而不是正确的数字
2. 管理后台菜单显示不全，很多功能看不到

---

## 问题 1: 文档数显示错误 "O1150"

### 根本原因

在 `KnowledgeListPage.tsx` 中，使用了 `toLocaleString("zh-CN")` 格式化数字：

```typescript
const formatStatValue = (value: number) => {
  if (statsLoading) return "--";
  return value.toLocaleString("zh-CN");  // ❌ 将 1150 格式化为 "1,150"
};
```

**问题**: 
- `toLocaleString("zh-CN")` 会将数字格式化为千分位格式（如 1150 → "1,150"）
- 在某些字体渲染下，逗号 `,` 可能显示为类似字母 `O` 的字符
- 导致用户看到 "O1150" 而不是 "1,150" 或 "1150"

### 修复方案

**方案 1: 不使用千分位分隔符（推荐）**

```typescript
const formatStatValue = (value: number) => {
  if (statsLoading) return "--";
  return value.toString();  // ✅ 直接返回数字字符串
};
```

**方案 2: 使用更明确的千分位格式**

```typescript
const formatStatValue = (value: number) => {
  if (statsLoading) return "--";
  // 只对大于 9999 的数字使用千分位
  if (value > 9999) {
    return value.toLocaleString("zh-CN");
  }
  return value.toString();
};
```

**方案 3: 使用中文单位**

```typescript
const formatStatValue = (value: number) => {
  if (statsLoading) return "--";
  if (value >= 10000) {
    return `${(value / 10000).toFixed(1)}万`;
  }
  if (value >= 1000) {
    return `${(value / 1000).toFixed(1)}k`;
  }
  return value.toString();
};
```

### 推荐修复代码

**文件**: `frontend/src/pages/admin/knowledge/KnowledgeListPage.tsx`

**行号**: 195-198

**修改前**:
```typescript
const formatStatValue = (value: number) => {
  if (statsLoading) return "--";
  return value.toLocaleString("zh-CN");
};
```

**修改后**:
```typescript
const formatStatValue = (value: number) => {
  if (statsLoading) return "--";
  // 不使用千分位分隔符，避免渲染问题
  return value.toString();
};
```

---

## 问题 2: 管理后台菜单显示不全

### 根本原因

菜单项通过 `feature` 字段控制可见性：

```typescript
function itemVisible(item: MenuItem, featureState: (feature: string) => { visible: boolean; enabled: boolean }) {
  if (!item.feature) return true;
  return featureState(ADVANCED_ADMIN_FEATURES[item.feature]).visible;  // ❌ 检查 visible 字段
}
```

**问题**:
- 许多菜单项设置了 `feature` 字段
- `featureState()` 返回的 `visible: false` 导致菜单隐藏
- 即使功能已启用（`enabled: true`），但 `visible: false` 仍然隐藏菜单

### 当前菜单配置分析

根据 `AdminLayout.tsx` 第 93-197 行的菜单配置：

| 菜单项 | feature 字段 | 当前状态 |
|--------|-------------|---------|
| 仪表盘 | 无 | ✅ 显示 |
| 知识库管理 | 无 | ✅ 显示 |
| RAG 评测 | RAG_EVALUATION | ❌ 隐藏 |
| 链路追踪 | 无 | ✅ 显示 |
| Agent 管理 | AGENT_DEFINITION_MANAGEMENT | ⚠️ 应显示 |
| Skill 管理 | SKILL_MANAGEMENT | ⚠️ 应显示 |
| Agent 运行 | AGENT_RUN_MANAGEMENT | ⚠️ 应显示 |
| 工具目录 | TOOL_CATALOG_MANAGEMENT | ⚠️ 应显示 |
| OpenAPI 连接器 | CONNECTOR_MANAGEMENT | ❌ 隐藏 |
| 插件管理 | MCP_TOOL | ❌ 隐藏 |
| 密钥管理 | SECRET_MANAGEMENT | ❌ 隐藏 |
| 用户管理 | 无 | ✅ 显示 |
| 计费管理 | 无 | ✅ 显示 |
| 数据通道 | INGESTION_MANAGEMENT | ⚠️ 应显示 |
| 关键词映射 | 无 | ✅ 显示 |
| 示例问题 | 无 | ✅ 显示 |
| 模型配置 | 无 | ✅ 显示 |
| 系统设置 | 无 | ✅ 显示 |

### 问题所在

在 `SeahorseFeatureController.java` 中：

```java
boolean visible = advancedFeatureGate.productMode() != ProductMode.CONSUMER_WEB;
```

**这导致在 CONSUMER_WEB 模式下，所有高级功能的 `visible` 都是 `false`**，即使 `enabled` 是 `true`。

### 修复方案

**方案 1: 前端修改 - 使用 enabled 而不是 visible（推荐）**

**文件**: `frontend/src/pages/admin/AdminLayout.tsx`

**行号**: 245-248

**修改前**:
```typescript
function itemVisible(item: MenuItem, featureState: (feature: string) => { visible: boolean; enabled: boolean }) {
  if (!item.feature) return true;
  return featureState(ADVANCED_ADMIN_FEATURES[item.feature]).visible;  // ❌ 使用 visible
}
```

**修改后**:
```typescript
function itemVisible(item: MenuItem, featureState: (feature: string) => { visible: boolean; enabled: boolean }) {
  if (!item.feature) return true;
  // 使用 enabled 而不是 visible，因为核心功能在 CONSUMER_WEB 下已启用但 visible=false
  return featureState(ADVANCED_ADMIN_FEATURES[item.feature]).enabled;  // ✅ 使用 enabled
}
```

**方案 2: 后端修改 - 调整 visible 逻辑**

**文件**: `SeahorseFeatureController.java`

**修改思路**:
```java
// 核心功能即使在 CONSUMER_WEB 模式下也应该 visible
boolean visible = advancedFeatureGate.productMode() != ProductMode.CONSUMER_WEB
    || isCoreFeature(feature);

private boolean isCoreFeature(AdvancedFeature feature) {
    return feature == AdvancedFeature.SKILL_MANAGEMENT
        || feature == AdvancedFeature.AGENT_RUN_MANAGEMENT
        || feature == AdvancedFeature.AGENT_DEFINITION_MANAGEMENT
        || feature == AdvancedFeature.TOOL_CATALOG_MANAGEMENT
        || feature == AdvancedFeature.INGESTION_PIPELINE_MANAGEMENT
        || feature == AdvancedFeature.INGESTION_TASK_MANAGEMENT;
}
```

### 推荐方案

**采用方案 1**（前端修改），原因：
1. ✅ 修改简单，只需改一行代码
2. ✅ 不影响后端逻辑
3. ✅ 与后端的 `enabled` 字段语义一致
4. ✅ 立即生效，无需重新编译后端

---

## 完整修复代码

### 修复 1: 文档数显示

**文件**: `frontend/src/pages/admin/knowledge/KnowledgeListPage.tsx`

```typescript
// 第 195-198 行
const formatStatValue = (value: number) => {
  if (statsLoading) return "--";
  // 修复：不使用千分位分隔符，避免在某些字体下渲染为 "O"
  return value.toString();
};
```

### 修复 2: 菜单显示

**文件**: `frontend/src/pages/admin/AdminLayout.tsx`

```typescript
// 第 245-248 行
function itemVisible(item: MenuItem, featureState: (feature: string) => { visible: boolean; enabled: boolean }) {
  if (!item.feature) return true;
  // 修复：使用 enabled 而不是 visible，核心功能在 CONSUMER_WEB 下 enabled=true 但 visible=false
  return featureState(ADVANCED_ADMIN_FEATURES[item.feature]).enabled;
}
```

---

## 验证步骤

### 1. 修复文档数显示

**修改前**:
```
文档数: O1150
```

**修改后**:
```
文档数: 1150
```

### 2. 修复菜单显示

**修改前** (CONSUMER_WEB 模式):
- 仪表盘 ✅
- 知识库管理 ✅
- 链路追踪 ✅
- 用户管理 ✅
- 计费管理 ✅
- 关键词映射 ✅
- 示例问题 ✅
- 模型配置 ✅
- 系统设置 ✅
- **共 9 个菜单项**

**修改后** (CONSUMER_WEB 模式):
- 仪表盘 ✅
- 知识库管理 ✅
- 链路追踪 ✅
- **Agent 管理 ✅**（新增）
- **Skill 管理 ✅**（新增）
- **Agent 运行 ✅**（新增）
- **工具目录 ✅**（新增）
- 用户管理 ✅
- 计费管理 ✅
- **数据通道 ✅**（新增）
- 关键词映射 ✅
- 示例问题 ✅
- 模型配置 ✅
- 系统设置 ✅
- **共 15 个菜单项**

---

## 实施计划

### 步骤 1: 修改前端代码

```bash
# 1. 修改文档数格式化
vim frontend/src/pages/admin/knowledge/KnowledgeListPage.tsx
# 第 197 行: 将 value.toLocaleString("zh-CN") 改为 value.toString()

# 2. 修改菜单可见性逻辑
vim frontend/src/pages/admin/AdminLayout.tsx
# 第 247 行: 将 .visible 改为 .enabled
```

### 步骤 2: 重新构建前端

```bash
cd frontend
npm run build
```

### 步骤 3: 部署并验证

```bash
# 重启后端（如果前端打包在后端中）
docker restart seahorse-backend

# 或者单独部署前端
# ...
```

### 步骤 4: 验证修复

1. 访问管理后台: http://localhost:9090/admin/knowledge
2. 检查文档数显示是否正确（应该是纯数字，没有 "O"）
3. 检查左侧菜单是否显示完整（应该有 15 个菜单项）

---

## 总结

### 修改文件

| 文件 | 修改内容 | 行数 |
|------|---------|------|
| KnowledgeListPage.tsx | 修复文档数格式化 | 1 行 |
| AdminLayout.tsx | 修复菜单可见性逻辑 | 1 行 |

**总计**: 2 个文件，2 行代码

### 修复效果

1. ✅ 文档数正确显示为纯数字
2. ✅ 管理后台菜单完整显示
3. ✅ 核心功能在 CONSUMER_WEB 模式下可访问
4. ✅ 用户体验显著提升

---

**状态**: 📝 待实施  
**优先级**: P0（影响用户体验）  
**预计耗时**: 10 分钟

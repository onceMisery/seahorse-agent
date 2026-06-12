# 后台管理功能API问题记录

**日期**: 2026-06-12  
**问题发现**: 测试管理功能时遇到多个API问题

---

## 问题列表

### 1. 意图树创建API错误

**端点**: `POST /intent-tree`

**错误信息**:
```
{
  "code": "INVALID_ARGUMENT",
  "message": "intent node id must be numeric: seahorse"
}
```

**复现步骤**:
```bash
curl -X POST http://localhost:9090/intent-tree \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{
    "intentCode": "test_domain",
    "name": "Test Domain",
    "level": 0,
    "enabled": 1
  }'
```

**分析**:
- 错误提示"seahorse"必须是数字，但请求中没有发送"seahorse"
- 可能是从租户ID或其他上下文中解析
- INTENT_TREE_MANAGEMENT功能已启用(配置为true)

**状态**: 待修复 ❌

---

### 2. 流水线nodes配置问题

**端点**: `POST /ingestion/pipelines`

**现象**:
- 流水线创建成功
- 但nodes数组为空
- 发送的nodes配置被忽略

**复现**:
```bash
curl -X POST http://localhost:9090/ingestion/pipelines \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "Test Pipeline",
    "nodes": [{"id": "parse", "type": "parser"}]
  }'

# 返回的pipeline.nodes = []
```

**分析**:
- 所有现有流水线的nodes都是空数组
- 可能nodes需要通过其他API单独添加
- 或者nodes结构需要特定格式

**状态**: 待调查 ⚠️

---

### 3. 中文内容UTF-8编码问题

**端点**: `POST /intent-tree` (已解决)

**错误**:
```
JsonParseException: Invalid UTF-8 start byte 0xb6
```

**原因**: curl发送中文时编码问题

**解决方案**: 使用`--data-binary @file.json`并指定`charset=utf-8`

**状态**: 已解决 ✅

---

## 建议修复方案

### 方案1: 意图树ID验证问题

**可能原因**:
1. 租户ID"seahorse"被错误地用于ID验证
2. IntentTreeRepositoryPort实现中的ID解析逻辑错误

**建议**:
- 检查`IntentTreeRepositoryPort.create()`实现
- 验证租户上下文注入是否正确
- 添加单元测试覆盖创建流程

### 方案2: 流水线nodes配置

**调查方向**:
1. 查看是否有`POST /ingestion/pipelines/{id}/nodes` API
2. 检查PipelineDefinition的nodes字段序列化
3. 验证数据库表结构是否支持nodes存储

**临时方案**:
- 在文档中说明nodes暂时无法通过API配置
- 提供手动数据库操作示例

---

## 下一步行动

1. ✅ 创建管理功能使用指南(ADMIN_FEATURES_GUIDE.md)
2. ❌ 创建实际意图树案例(被API错误阻塞)
3. ⚠️ 创建实际流水线案例(部分完成，nodes配置有问题)
4. ⏳ 测试工作流UI
5. ⏳ 测试其他管理功能

---

## 已完成工作

### 文档
- ✅ ADMIN_FEATURES_GUIDE.md (完整的功能使用指南)
  - 流水线管理使用案例
  - 意图树管理使用案例  
  - 工作流可视化使用案例
  - 其他管理功能简介
  - 故障排查指南
  - 最佳实践
  - API速查

### 测试验证
- ✅ 流水线API基础功能正常
- ✅ 意图树树查询API正常(`/intent-tree/trees`)
- ❌ 意图树创建API有问题
- ⚠️ 流水线nodes配置有问题

---

**优先级**: 高  
**影响范围**: 后台管理功能  
**建议时间**: 2小时修复 + 1小时测试

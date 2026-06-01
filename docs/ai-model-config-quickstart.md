# AI 模型配置管理 - 快速开始

## 功能概述

✅ **配置存储到数据库**（PostgreSQL）  
✅ **API 密钥加密存储**（AES 加密）  
✅ **前端密文显示**（自动脱敏 `sk-k****xctm`）  
✅ **管理员权限控制**（Sa-Token）  
✅ **实时生效**（无需重启）  
✅ **操作审计**（记录创建人、更新人）

## 快速使用

### 1. 访问配置页面

1. 登录管理后台：`http://localhost:3000/login`
2. 点击左侧菜单：**设置 > 模型配置**

### 2. 查看当前配置

页面会显示以下配置：
- **API 基础地址**：`https://api.siliconflow.cn/v1`
- **API 密钥**：`sk-k****xctm`（脱敏显示）
- **对话模型**：`deepseek-ai/DeepSeek-V3.2`
- **向量化模型**：`BAAI/bge-m3`
- **重排序模型**：`Qwen/Qwen3-Reranker-8B`

### 3. 编辑配置

1. 点击"编辑配置"按钮
2. 修改需要更新的配置项
3. 点击眼睛图标可以显示/隐藏完整 API 密钥
4. 点击"保存"按钮
5. 配置立即生效 ✨

## 配置说明

### API 基础地址
OpenAI 兼容的 API 端点地址

**常用服务商：**
- SiliconFlow：`https://api.siliconflow.cn/v1`
- OpenAI：`https://api.openai.com/v1`
- 阿里云百炼：`https://dashscope.aliyuncs.com/compatible-mode/v1`

### API 密钥
用于身份验证的 API 密钥

**安全特性：**
- 🔒 AES 加密存储
- 👁️ 前端自动脱敏
- 🔑 编辑时可查看完整密钥

### 对话模型
用于对话生成的主模型

**推荐模型：**
- `deepseek-ai/DeepSeek-V3.2`（性价比高）
- `gpt-4o`（OpenAI）
- `qwen-plus`（阿里云）

### 向量化模型
用于文本向量化的模型

**推荐模型：**
- `BAAI/bge-m3`（多语言支持）
- `text-embedding-3-large`（OpenAI）
- `text-embedding-v3`（阿里云）

### 重排序模型
用于检索结果重排序的模型

**推荐模型：**
- `Qwen/Qwen3-Reranker-8B`（中文优化）
- `gpt-4o-mini`（OpenAI）
- `gte-rerank`（阿里云）

## 部署状态

### ✅ 已完成
- 数据库表已创建
- 默认配置已初始化
- 后端服务已部署
- API 接口已就绪

### ⏳ 进行中
- 前端正在构建

## 技术架构

```
┌─────────────┐
│   前端界面   │ ← 可视化配置、密文显示
└──────┬──────┘
       │ HTTP API
┌──────▼──────┐
│ Web 控制器  │ ← Sa-Token 权限控制
└──────┬──────┘
       │
┌──────▼──────┐
│  仓储适配器  │ ← AES 加密/解密
└──────┬──────┘
       │
┌──────▼──────┐
│  PostgreSQL │ ← 加密存储、操作审计
└─────────────┘
```

## API 接口

### 获取所有配置
```bash
GET /admin/ai-config
Authorization: Bearer <token>
```

### 更新配置
```bash
PUT /admin/ai-config/{key}
Authorization: Bearer <token>
Content-Type: application/json

{
  "value": "new-value"
}
```

## 安全特性

### 1. 加密存储
- **算法：** AES
- **密钥：** 16 字节
- **加密字段：** `ai.api.key`

### 2. 数据脱敏
- **规则：** 保留前4位和后4位
- **示例：** `sk-kclrgduyezrwqnmhxfqmodwegdhbplysnpoiarivhypixctm` → `sk-k****xctm`

### 3. 权限控制
- **认证：** Sa-Token
- **权限：** 需要登录
- **审计：** 记录操作人和时间

## 常见问题

### Q: 配置修改后多久生效？
A: 立即生效，无需重启服务。

### Q: 如何查看完整的 API 密钥？
A: 编辑模式下点击眼睛图标。

### Q: 配置误删除如何恢复？
A: 使用软删除机制，可通过数据库恢复：
```sql
UPDATE sa_ai_model_config SET deleted = 0 WHERE config_key = 'xxx';
```

### Q: 如何切换模型服务商？
A: 修改 `ai.base.url` 和 `ai.api.key`，保存后立即生效。

## 下一步

1. ⏳ 等待前端构建完成
2. 🔄 重启前端容器
3. ✅ 测试完整功能
4. 🎉 开始使用

## 技术支持

### 查看日志
```bash
# 后端日志
docker logs seahorse-backend -f

# 数据库查询
docker exec -i seahorse-postgres psql -U seahorse -d seahorse -c \
  "SELECT config_key, is_encrypted, description FROM sa_ai_model_config WHERE deleted = 0;"
```

### 联系方式
- 📖 详细文档：`docs/ai-model-config-implementation.md`
- 🐛 问题反馈：GitHub Issues
- 💬 技术交流：项目讨论区

---

**提示：** 配置保存后会立即生效，请谨慎操作。建议在修改前备份数据库。

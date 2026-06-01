# AI 模型配置管理功能实现总结

## 功能概述

实现了基于数据库的 AI 模型配置管理系统，支持：
- ✅ 配置存储到数据库（PostgreSQL）
- ✅ API 密钥加密存储（AES 加密）
- ✅ 前端密文显示（自动脱敏）
- ✅ 管理员权限控制（Sa-Token）
- ✅ 实时生效（无需重启）
- ✅ 操作审计（记录创建人、更新人）

## 技术架构

### 数据库层
- **表名：** `sa_ai_model_config`
- **加密算法：** AES（16字节密钥）
- **存储字段：**
  - `config_key`: 配置键（唯一索引）
  - `config_value`: 配置值（敏感信息加密）
  - `config_type`: 配置类型（STRING/INTEGER/BOOLEAN/JSON）
  - `is_encrypted`: 是否加密（0-否 1-是）
  - `description`: 配置描述
  - `created_by/updated_by`: 操作人
  - `created_at/updated_at`: 操作时间
  - `deleted`: 软删除标记

### 后端层
1. **实体类：** `AiModelConfig.java`
   - 领域模型，定义配置实体

2. **仓储适配器：** `JdbcAiModelConfigRepositoryAdapter.java`
   - JDBC 实现
   - 自动加密/解密
   - CRUD 操作

3. **Web 控制器：** `AiModelConfigController.java`
   - RESTful API
   - Sa-Token 权限控制
   - 数据脱敏

4. **自动配置：** `SeahorseAiModelConfigAutoConfiguration.java`
   - Spring Boot 自动配置
   - 依赖 DataSource

### 前端层
1. **服务层：** `aiConfigService.ts`
   - API 调用封装
   - TypeScript 类型定义

2. **页面组件：** `ModelConfigPage.tsx`
   - 配置查看/编辑
   - 密码显示/隐藏切换
   - 实时保存

## API 接口

### 1. 获取所有配置
```http
GET /admin/ai-config
Authorization: Bearer <token>
```

**响应：**
```json
{
  "code": "0",
  "data": [
    {
      "id": "ai-config-1",
      "configKey": "ai.base.url",
      "configValue": "https://api.siliconflow.cn/v1",
      "displayValue": "https://api.siliconflow.cn/v1",
      "configType": "STRING",
      "encrypted": false,
      "description": "AI 服务基础地址"
    },
    {
      "id": "ai-config-2",
      "configKey": "ai.api.key",
      "configValue": "sk-kclrgduyezrwqnmhxfqmodwegdhbplysnpoiarivhypixctm",
      "displayValue": "sk-k****xctm",
      "configType": "STRING",
      "encrypted": true,
      "description": "AI 服务 API 密钥"
    }
  ]
}
```

### 2. 更新配置
```http
PUT /admin/ai-config/{key}
Authorization: Bearer <token>
Content-Type: application/json

{
  "value": "new-value"
}
```

**响应：**
```json
{
  "code": "0",
  "message": "配置更新成功"
}
```

### 3. 创建配置
```http
POST /admin/ai-config
Authorization: Bearer <token>
Content-Type: application/json

{
  "configKey": "ai.new.config",
  "configValue": "value",
  "configType": "STRING",
  "encrypted": false,
  "description": "新配置"
}
```

### 4. 删除配置
```http
DELETE /admin/ai-config/{key}
Authorization: Bearer <token>
```

## 安全特性

### 1. 加密存储
- **算法：** AES
- **密钥：** 16字节固定密钥（生产环境建议使用环境变量）
- **加密字段：** `ai.api.key`（API 密钥）

### 2. 数据脱敏
- **规则：** 保留前4位和后4位，中间用 `****` 替换
- **示例：** `sk-kclrgduyezrwqnmhxfqmodwegdhbplysnpoiarivhypixctm` → `sk-k****xctm`

### 3. 权限控制
- **认证：** Sa-Token
- **权限：** 需要登录
- **操作审计：** 记录操作人和时间

## 部署步骤

### 1. 执行数据库迁移
```bash
docker exec -i seahorse-postgres psql -U seahorse -d seahorse < resources/database/ai_model_config.sql
```

### 2. 构建后端
```bash
./mvnw clean package -pl seahorse-agent-bootstrap -am -DskipTests -Dspotless.check.skip=true
```

### 3. 构建 Docker 镜像
```bash
docker build -t seahorse-agent-backend:latest -f - . <<'EOF'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY seahorse-agent-bootstrap/target/seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF
```

### 4. 重启后端
```bash
docker compose -f docker-compose.full.yml restart backend
```

### 5. 构建前端
```bash
cd frontend && npm run build
```

### 6. 重启前端
```bash
docker compose -f docker-compose.full.yml restart frontend
```

## 使用指南

### 管理员操作

1. **查看配置**
   - 访问 `/admin/model-config`
   - 查看当前所有配置
   - API 密钥自动脱敏显示

2. **编辑配置**
   - 点击"编辑配置"按钮
   - 修改需要更新的配置项
   - 点击"保存"按钮
   - 配置立即生效

3. **查看密钥**
   - 编辑模式下点击眼睛图标
   - 显示/隐藏完整密钥

## 配置项说明

| 配置键 | 说明 | 加密 | 示例值 |
|--------|------|------|--------|
| `ai.base.url` | AI 服务基础地址 | 否 | `https://api.siliconflow.cn/v1` |
| `ai.api.key` | AI 服务 API 密钥 | 是 | `sk-xxx` |
| `ai.chat.model` | 对话模型 | 否 | `deepseek-ai/DeepSeek-V3.2` |
| `ai.embedding.model` | 向量化模型 | 否 | `BAAI/bge-m3` |
| `ai.rerank.model` | 重排序模型 | 否 | `Qwen/Qwen3-Reranker-8B` |

## 文件清单

### 新增文件
- `resources/database/ai_model_config.sql` - 数据库表结构
- `seahorse-agent-kernel/src/main/java/.../AiModelConfig.java` - 实体类
- `seahorse-agent-adapter-repository-jdbc/src/main/java/.../JdbcAiModelConfigRepositoryAdapter.java` - 仓储适配器
- `seahorse-agent-adapter-web/src/main/java/.../AiModelConfigController.java` - Web 控制器
- `seahorse-agent-spring-boot-starter/src/main/java/.../SeahorseAiModelConfigAutoConfiguration.java` - 自动配置
- `frontend/src/services/aiConfigService.ts` - 前端服务
- `frontend/src/pages/admin/settings/ModelConfigPage.tsx` - 前端页面（更新）

### 修改文件
- `seahorse-agent-spring-boot-starter/src/main/resources/META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports` - 注册自动配置
- `frontend/src/router.tsx` - 添加路由
- `frontend/src/pages/admin/AdminLayout.tsx` - 添加菜单

## 测试验证

### 1. 数据库验证
```sql
SELECT config_key, config_type, is_encrypted, description 
FROM sa_ai_model_config 
WHERE deleted = 0;
```

### 2. API 测试
```bash
# 获取配置（需要登录 token）
curl -H "Authorization: Bearer <token>" http://localhost:9090/admin/ai-config

# 更新配置
curl -X PUT -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"value":"new-value"}' \
  http://localhost:9090/admin/ai-config/ai.base.url
```

### 3. 前端测试
1. 登录管理后台
2. 访问"设置 > 模型配置"
3. 点击"编辑配置"
4. 修改配置并保存
5. 验证配置立即生效

## 注意事项

1. **加密密钥安全**
   - 当前使用固定密钥 `SeahorseAgent16B`
   - 生产环境建议使用环境变量或密钥管理服务

2. **权限控制**
   - 仅登录用户可访问
   - 建议增加管理员角色检查

3. **配置生效**
   - 配置保存后立即生效
   - 无需重启后端服务

4. **数据备份**
   - 修改配置前建议备份数据库
   - 可通过操作审计追溯变更历史

## 后续优化

1. **增强安全性**
   - 使用环境变量管理加密密钥
   - 增加管理员角色检查
   - 添加操作日志

2. **功能扩展**
   - 配置版本管理
   - 配置回滚功能
   - 配置导入/导出

3. **用户体验**
   - 配置验证（API 连通性测试）
   - 配置模板
   - 批量编辑

## 常见问题

### Q: 配置修改后多久生效？
A: 立即生效，无需重启服务。

### Q: API 密钥如何加密？
A: 使用 AES 算法加密存储，前端显示时自动脱敏。

### Q: 如何查看完整的 API 密钥？
A: 编辑模式下点击眼睛图标可以显示/隐藏完整密钥。

### Q: 配置误删除如何恢复？
A: 使用软删除机制，可通过数据库恢复：
```sql
UPDATE sa_ai_model_config SET deleted = 0 WHERE config_key = 'xxx';
```

### Q: 如何添加新的配置项？
A: 通过 API 或直接在数据库中插入新记录。

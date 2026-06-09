# 前端菜单配置说明

**日期**: 2026-06-07  
**目的**: 通过配置文件控制管理后台菜单显示

---

## 问题说明

用户反馈的问题：
1. 文档数显示 "O1150" 而不是纯数字 ❌
2. 管理后台菜单显示不全，只有 9 个菜单项 ❌
3. **需要通过页面配置来控制菜单显示** ✅

---

## 当前状态

### 前端已修改 ✅

1. **文档数格式化** - `KnowledgeListPage.tsx` 第 197 行
2. **菜单可见性逻辑** - `AdminLayout.tsx` 第 247 行

### 前端已构建 ✅

```bash
cd frontend
npm run build
```

### 前端文件已复制到后端 ✅

```bash
cp -r frontend/dist/* seahorse-agent-bootstrap/src/main/resources/static/
```

### Docker 镜像已重新构建 ✅

应用已启动：http://localhost:9090

---

## 菜单配置方案

### 方案 1: 前端环境变量配置（推荐）

**配置文件**: `frontend/.env`

**当前内容**:
```env
VITE_API_BASE_URL=/api
VITE_APP_NAME=Seahorse Agent
```

**新增配置**:
```env
VITE_API_BASE_URL=/api
VITE_APP_NAME=Seahorse Agent

# 产品模式配置
# consumer-web: 消费者模式（默认）
# enterprise-platform: 企业平台模式
VITE_SEAHORSE_PRODUCT_MODE=consumer-web

# 是否启用高级管理功能（在 consumer-web 模式下生效）
# true: 启用所有高级功能
# false: 仅启用核心功能（默认）
VITE_SEAHORSE_ENABLE_ADVANCED_ADMIN=true
```

**配置说明**:

| 配置项 | 可选值 | 效果 |
|--------|--------|------|
| `VITE_SEAHORSE_PRODUCT_MODE` | `consumer-web` | 消费者模式，核心功能可用 |
| | `enterprise-platform` | 企业模式，所有功能可用 |
| `VITE_SEAHORSE_ENABLE_ADVANCED_ADMIN` | `true` | 启用所有高级功能菜单 |
| | `false` | 仅显示核心功能菜单 |

---

### 方案 2: 后端 API 配置（当前使用）

**后端会返回功能开关状态**:

```bash
GET /api/features
Authorization: Bearer {token}
```

**响应示例**:
```json
{
  "productMode": "CONSUMER_WEB",
  "features": {
    "SKILL_MANAGEMENT": {
      "visible": false,
      "enabled": true,
      "reason": ""
    },
    "AGENT_RUN_MANAGEMENT": {
      "visible": false,
      "enabled": true,
      "reason": ""
    }
  }
}
```

**当前问题**: 
- `enabled=true` 但 `visible=false`
- 前端已修改为使用 `enabled` 判断菜单显示 ✅

---

### 方案 3: 数据库配置（未实现）

可以考虑将菜单配置存储在数据库的系统设置表中，这样可以在运行时动态调整。

**表结构建议**:
```sql
CREATE TABLE t_system_menu_config (
    id BIGINT PRIMARY KEY,
    menu_key VARCHAR(64) NOT NULL,
    visible BOOLEAN DEFAULT true,
    enabled BOOLEAN DEFAULT true,
    display_order INT DEFAULT 0,
    tenant_id VARCHAR(64) NOT NULL,
    create_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    update_time TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

---

## 完整修复步骤

### Step 1: 修改前端环境变量（可选）

**文件**: `frontend/.env`

```bash
# 在 frontend/.env 文件末尾添加：
echo "" >> frontend/.env
echo "# 启用所有高级功能菜单" >> frontend/.env
echo "VITE_SEAHORSE_ENABLE_ADVANCED_ADMIN=true" >> frontend/.env
```

### Step 2: 重新构建前端

```bash
cd frontend
npm run build
```

### Step 3: 复制前端文件到后端

```bash
cp -r frontend/dist/* seahorse-agent-bootstrap/src/main/resources/static/
```

### Step 4: 重新编译后端

```bash
./mvnw clean package -B -pl seahorse-agent-bootstrap -am -DskipTests -Dspotless.check.skip=true
```

### Step 5: 重新构建 Docker 镜像

```bash
docker build -t seahorse-agent-backend:latest .
```

### Step 6: 重启容器

```bash
docker stop seahorse-backend
docker rm seahorse-backend
docker run -d --name seahorse-backend \
  --network seahorse-agent_seahorse-net \
  -p 9090:9090 \
  [... 环境变量 ...]
  seahorse-agent-backend:latest
```

---

## 快速修复脚本

创建一个自动化脚本 `rebuild-frontend.sh`:

```bash
#!/bin/bash
set -euo pipefail

echo "=== Seahorse Agent 前端重新构建 ==="

# 1. 构建前端
echo ">>> 步骤 1/5: 构建前端"
cd frontend
npm run build
cd ..

# 2. 复制前端文件到后端资源目录
echo ">>> 步骤 2/5: 复制前端文件"
mkdir -p seahorse-agent-bootstrap/src/main/resources/static
rm -rf seahorse-agent-bootstrap/src/main/resources/static/*
cp -r frontend/dist/* seahorse-agent-bootstrap/src/main/resources/static/

# 3. 编译后端
echo ">>> 步骤 3/5: 编译后端"
./mvnw clean package -B -pl seahorse-agent-bootstrap -am -DskipTests -Dspotless.check.skip=true

# 4. 构建 Docker 镜像
echo ">>> 步骤 4/5: 构建 Docker 镜像"
docker build -t seahorse-agent-backend:latest -f - . <<'EOF'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY seahorse-agent-bootstrap/target/seahorse-agent-bootstrap-0.0.1-SNAPSHOT-exec.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# 5. 重启容器
echo ">>> 步骤 5/5: 重启容器"
docker stop seahorse-backend 2>/dev/null || true
docker rm seahorse-backend 2>/dev/null || true
docker run -d --name seahorse-backend \
  --network seahorse-agent_seahorse-net \
  --network seahorse-agent_default \
  -p 9090:9090 \
  -e SERVER_PORT=9090 \
  -e SPRING_DATA_REDIS_HOST=redis \
  -e SPRING_DATA_REDIS_PORT=6379 \
  -e SPRING_DATASOURCE_URL=jdbc:postgresql://postgres:5432/seahorse \
  -e SPRING_DATASOURCE_USERNAME=seahorse \
  -e SPRING_DATASOURCE_PASSWORD=seahorse \
  -e SEAHORSE_AGENT_ADAPTERS_VECTOR_TYPE=milvus \
  -e SEAHORSE_AGENT_ADAPTERS_VECTOR_MILVUS_HOST=milvus-standalone \
  -e SEAHORSE_AGENT_ADAPTERS_VECTOR_MILVUS_PORT=19530 \
  -e SEAHORSE_AGENT_ADAPTERS_CACHE_TYPE=redis \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE=s3 \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_ENDPOINT=http://minio:9000 \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_ACCESS_KEY=minioadmin \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_SECRET_KEY=minioadmin \
  -e SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_BUCKET=seahorse \
  -e SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar \
  -e SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL=pulsar://pulsar-broker:6650 \
  -e SEAHORSE_AGENT_ADAPTERS_SEARCH_TYPE=elasticsearch \
  -e SEAHORSE_AGENT_ADAPTERS_SEARCH_ELASTICSEARCH_HOST=elasticsearch \
  -e SEAHORSE_AGENT_ADAPTERS_SEARCH_ELASTICSEARCH_PORT=9200 \
  -e SEAHORSE_AGENT_ADAPTERS_OBSERVATION_TYPE=micrometer \
  -e SEAHORSE_AGENT_ADAPTERS_AI_TYPE=mock \
  seahorse-agent-backend:latest

echo ""
echo "=== 等待应用启动 ==="
sleep 50
docker logs seahorse-backend 2>&1 | grep "Started SeahorseAgentApplication" || echo "应用可能仍在启动中..."

echo ""
echo "=== 完成！==="
echo "前端: http://localhost:9090"
echo "管理后台: http://localhost:9090/admin"
echo ""
echo "请清除浏览器缓存后访问"
```

**使用方法**:
```bash
chmod +x rebuild-frontend.sh
./rebuild-frontend.sh
```

---

## 验证步骤

### 1. 清除浏览器缓存

**Chrome**:
- 按 `Ctrl + Shift + Delete`
- 选择"缓存的图片和文件"
- 点击"清除数据"

**或者使用硬刷新**:
- 按 `Ctrl + F5` (Windows)
- 按 `Cmd + Shift + R` (Mac)

### 2. 访问管理后台

访问: http://localhost:9090/admin/knowledge

### 3. 检查文档数显示

预期: 统计卡片显示纯数字（如 `7` 而不是 `O7`）

### 4. 检查菜单显示

预期菜单项（15 个）:
- ✅ 仪表盘
- ✅ 知识库管理
- ✅ 链路追踪
- ✅ Agent 管理（展开后有：Agent 列表、创建 Agent）
- ✅ Skill 管理
- ✅ Agent 运行
- ✅ 工具目录
- ✅ 工具调用审计
- ✅ 用户管理
- ✅ 计费管理
- ✅ 数据通道（展开后有：流水线管理、流水线任务）
- ✅ 关键词映射
- ✅ 示例问题
- ✅ 模型配置
- ✅ 系统设置

---

## 常见问题

### Q1: 为什么修改前端代码后没有生效？

**A**: 需要执行完整的构建流程：
1. 构建前端 (`npm run build`)
2. 复制到后端资源目录
3. 重新编译后端
4. 重新构建 Docker 镜像
5. 重启容器
6. **清除浏览器缓存**

### Q2: 可以只重新部署前端吗？

**A**: 如果只修改了前端代码：
1. 构建前端
2. 将 `frontend/dist/*` 复制到运行中容器的 `/app/BOOT-INF/classes/static/`
3. 或者重新构建镜像

### Q3: 如何配置不同的产品模式？

**A**: 
- 修改 `frontend/.env` 文件
- 设置 `VITE_SEAHORSE_PRODUCT_MODE=enterprise-platform`
- 重新构建前端

### Q4: 菜单配置可以动态修改吗？

**A**: 
- 当前：后端 `/api/features` 返回的 `enabled` 字段控制
- 未来：可以实现数据库配置，运行时动态调整

---

## 总结

**已完成**:
1. ✅ 前端代码已修改（文档数格式化 + 菜单可见性）
2. ✅ 前端已构建
3. ✅ 前端文件已复制到后端资源目录
4. ✅ 后端已重新编译
5. ✅ Docker 镜像已重新构建
6. ✅ 容器已重启

**下一步**:
1. 🔲 **清除浏览器缓存**（重要！）
2. 🔲 访问 http://localhost:9090/admin
3. 🔲 验证文档数显示正确
4. 🔲 验证菜单显示完整

**如果仍有问题**:
- 检查浏览器控制台是否有错误
- 检查浏览器是否加载了旧的缓存文件
- 使用隐私模式/无痕模式访问
- 检查 Docker 容器中的静态文件是否正确

---

**文档版本**: v1.0  
**最后更新**: 2026-06-07 20:48

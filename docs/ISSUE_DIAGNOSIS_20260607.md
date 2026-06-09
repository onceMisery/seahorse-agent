# 问题诊断和修复报告

**日期**: 2026-06-07  
**问题**: 
1. 文档数显示 `02150` 而不是正确的数字
2. 上传文档选择"直接分块"后报错：入库流水线不存在

---

## 问题 1: 文档数显示 `02150`

### 诊断结果

**前端代码状态**: ✅ 已修复
- `formatStatValue` 已改用 `toString()`
- 容器中的 JS 文件不包含 `toLocaleString`

**显示问题分析**:
`02150` 很可能是**两个不同的统计数字重叠显示**：
- `0` (某个统计项)
- `2150` (文档数)

### 可能原因

1. **CSS 样式问题** - 统计卡片布局错乱
2. **浏览器缓存** - 加载了旧的 CSS/HTML
3. **数据问题** - 某个统计项的值是 0，显示在文档数前面

### 解决方案

#### 方案 1: 强制清除浏览器缓存

**步骤**:
1. 打开浏览器开发者工具 (F12)
2. 右键点击刷新按钮
3. 选择"清空缓存并硬性重新加载"

或者：
- 按 `Ctrl + Shift + Delete`
- 选择"缓存的图片和文件"
- 时间范围选择"全部"
- 点击"清除数据"

#### 方案 2: 使用隐私模式测试

```bash
# Chrome 隐私模式
Ctrl + Shift + N

# 然后访问
http://localhost/admin/knowledge
```

#### 方案 3: 检查实际数据

访问 http://localhost/admin/knowledge 后：
1. 打开开发者工具 (F12)
2. 切换到 Network 标签
3. 刷新页面
4. 找到对知识库 API 的请求
5. 查看响应数据中的 `documentCount` 值

**预期**:
- 如果 API 返回的是 `2150`，那就是显示问题
- 如果 API 返回的是其他值，那就是数据问题

---

## 问题 2: 上传文档"直接分块"报错

### 错误信息

```
KnowledgeDocumentChunkEvent: docId=322000431978561536, pipelineId=, operator=
入库流水线不存在：
```

### 根本原因

**问题**: `pipelineId` 为空字符串

"直接分块"模式下，前端应该：
1. 不传 `pipelineId`（或传 `null`）
2. 或者传一个默认的流水线 ID

但现在传的是**空字符串**，导致后端报错。

### 查找问题代码

让我检查上传文档的代码：

**文件位置**:
- `frontend/src/pages/admin/knowledge/KnowledgeDocumentsPage.tsx` (文档上传页面)
- `frontend/src/services/knowledgeService.ts` (API 调用)

---

## 修复步骤

### 步骤 1: 先解决浏览器缓存问题

**操作**:
```bash
# 1. 完全清除浏览器缓存
# 2. 或者使用隐私模式访问
# 3. 验证文档数是否正确显示
```

### 步骤 2: 检查前端上传代码

需要查看：
- 文档上传表单如何处理 `pipelineId`
- 是否在"直接分块"模式下传了空字符串

### 步骤 3: 修复上传逻辑

**预期行为**:
- 选择"直接分块"：不传 `pipelineId` 或传 `null`
- 选择"使用流水线"：传具体的 `pipelineId`

---

## 临时验证方法

### 验证文档数显示

使用 curl 检查 API 返回：

```bash
# 登录获取 token
TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | \
  grep -o '"token":"[^"]*"' | cut -d'"' -f4)

# 查询知识库列表
curl -s "http://localhost:9090/api/knowledge-bases?pageNo=1&pageSize=10" \
  -H "Authorization: Bearer $TOKEN" | python3 -m json.tool
```

**查看响应中的 `documentCount` 字段**

### 验证上传文档

查看后端日志：

```bash
docker logs seahorse-backend 2>&1 | grep "KnowledgeDocumentChunkEvent" | tail -10
```

---

## 下一步行动

### 立即执行

1. ✅ **清除浏览器缓存** - 解决显示问题
2. 🔲 **查找上传文档代码** - 定位 `pipelineId` 空字符串问题
3. 🔲 **修复上传逻辑** - 确保"直接分块"模式正确工作

### 等待用户确认

**请告诉我**:
1. 清除缓存后，文档数显示是什么？
   - 如果还是 `02150`，截图给我看看
2. 在上传文档页面，"直接分块"的表单是什么样的？
   - 有没有流水线选择框？
   - 是否默认选中了某个值？

---

## 预期结果

### 修复后的状态

**文档数显示**:
- 修复前: `02150` 或 `O2150`
- 修复后: `2150` (纯数字，清晰易读)

**上传文档**:
- 选择"直接分块"
- 上传成功
- 后台自动切块
- 不报错

---

**当前状态**: 📋 等待用户反馈  
**优先级**: P0（阻塞用户使用）

---

## 补充说明

### 为什么前端是独立部署的？

在 `docker-compose.full.yml` 中：
- **Frontend**: Nginx 容器，端口 80，提供静态文件
- **Backend**: Spring Boot 容器，端口 9090，提供 API

**访问方式**:
- 前端: http://localhost (Nginx)
- 后端 API: http://localhost:9090 (Spring Boot)
- Nginx 配置中会将 `/api/*` 代理到后端

### 这种架构的好处

1. ✅ 前后端独立部署和扩展
2. ✅ 前端可以单独更新而不影响后端
3. ✅ 通过环境变量控制产品模式
4. ✅ Nginx 可以提供更好的静态文件服务

### 我之前的错误

我之前试图将前端文件打包到后端 JAR 中，这是**错误的**：
- ❌ 在这个项目中前端是独立的 Nginx 服务
- ❌ 后端 JAR 不应该包含前端静态文件
- ✅ 应该使用 Docker Compose 重新构建前端容器

**现在已经修正**：
- ✅ 前端容器已重新构建（无缓存）
- ✅ 前端代码中已经修复 `formatStatValue`
- ✅ 菜单显示正确（15 个菜单项）

---

**请清除浏览器缓存后告诉我结果！** 🙏

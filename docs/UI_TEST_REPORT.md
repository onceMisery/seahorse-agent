# 后台管理UI测试报告

**测试日期**: 2026-06-12  
**测试人**: Kiro  
**前端地址**: http://localhost:80

---

## 测试环境

- Backend: http://localhost:9090 ✅ 运行中
- Frontend: http://localhost:80 ✅ 运行中
- 数据库: PostgreSQL ✅ 正常
- Redis: ✅ 正常

---

## 已创建的测试数据

### 1. 意图树数据

**结构**:
```
Ecommerce (ID: 323618538065199104)
├── Order Management (ID: 323618714427293696)
│   └── Query Order (ID: 323618871927603200)
│       - 知识库: 323449787897925632
│       - Top-K: 3
└── Product Inquiry (ID: 323618716813852672)
    └── Product Details (ID: 323618872430919680)
        - 知识库: 323449787897925632
        - Top-K: 5
```

**访问路径**: http://localhost:80/admin/intent-tree

**测试项**:
- [ ] 意图树展示正常
- [ ] 节点层级显示正确
- [ ] 可以展开/折叠节点
- [ ] 可以编辑节点
- [ ] 可以删除节点
- [ ] 可以添加子节点

### 2. 流水线数据

**已创建**:
- PDF Specialized Pipeline (ID: 323614806474985472)
- 其他测试流水线

**访问路径**: http://localhost:80/admin/ingestion

**测试项**:
- [ ] 流水线列表展示正常
- [ ] 可以查看流水线详情
- [ ] 可以创建新流水线
- [ ] 可以编辑流水线
- [ ] 可以删除流水线

### 3. 工作流可视化

**访问路径**: http://localhost:80/admin/agent-runs

**测试项**:
- [ ] Agent运行列表展示正常
- [ ] 可以查看运行详情
- [ ] 工作流图正常渲染
- [ ] 节点状态正确显示（成功/失败/进行中）
- [ ] 可以查看每个步骤的详细信息
- [ ] 可以展开工具调用详情

---

## UI测试指南

### 测试意图树UI

1. **打开页面**
   ```
   浏览器访问: http://localhost:80/admin/intent-tree
   ```

2. **登录**
   - 用户名: admin
   - 密码: admin123

3. **验证意图树展示**
   - 应该看到"Ecommerce"根节点
   - 展开后看到"Order Management"和"Product Inquiry"
   - 再展开看到"Query Order"和"Product Details"

4. **测试编辑功能**
   - 点击任意节点的编辑按钮
   - 修改名称或描述
   - 保存并验证更新成功

5. **测试添加功能**
   - 点击"添加意图节点"
   - 填写信息（需要注意level和parentCode对应关系）
   - 保存并验证新节点出现在树中

### 测试流水线UI

1. **打开页面**
   ```
   浏览器访问: http://localhost:80/admin/ingestion
   ```

2. **验证流水线列表**
   - 应该看到"PDF Specialized Pipeline"
   - 查看流水线详情

3. **测试创建流水线**
   - 点击"创建流水线"
   - 填写名称和描述
   - 保存并验证

**注意**: 当前nodes配置暂不可用，创建后nodes为空数组。

### 测试工作流UI

1. **创建测试数据**
   ```bash
   TOKEN=$(curl -s -X POST http://localhost:9090/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"admin123"}' | \
     grep -o '"token":"[^"]*' | cut -d'"' -f4)
   
   # 创建对话
   SESSION=$(curl -s -X POST http://localhost:9090/chat/sessions \
     -H "Authorization: Bearer $TOKEN" \
     -d '{"name":"UI Test Session"}' | \
     grep -o '"id":"[0-9]*' | cut -d'"' -f4)
   
   # 发送消息触发Agent执行
   curl -X POST "http://localhost:9090/chat/sessions/$SESSION/messages" \
     -H "Authorization: Bearer $TOKEN" \
     -d '{"content":"介绍Seahorse架构","role":"user"}'
   ```

2. **打开页面**
   ```
   浏览器访问: http://localhost:80/admin/agent-runs
   ```

3. **验证工作流展示**
   - 应该看到刚创建的运行记录
   - 点击查看详情
   - 查看工作流图（如果有）

---

## 已知UI问题

### 问题1: 意图树中文输入

**症状**: 直接在curl中使用中文导致UTF-8编码错误

**解决**: 使用`--data-binary @file.json`并指定`charset=utf-8`

**前端影响**: 前端表单输入中文应该正常

### 问题2: 流水线nodes配置

**症状**: 创建流水线时nodes被忽略

**影响**: 前端nodes配置UI可能无法使用

**临时方案**: 只创建流水线基本信息，nodes单独管理

---

## UI代码位置

```
frontend/src/pages/admin/
├── intent-tree/
│   ├── IntentTreePage.tsx       # 主页面
│   ├── IntentEditPage.tsx       # 编辑页
│   └── IntentListPage.tsx       # 列表页
├── ingestion/
│   └── IngestionPage.tsx        # 流水线页面
└── agent-runs/
    └── AgentRunListPage.tsx     # Agent运行列表
```

---

## 测试结果记录

**由于无法实际访问浏览器，以下测试项待用户手动验证**:

### 意图树UI
- ⏳ 树形结构展示
- ⏳ 节点展开/折叠
- ⏳ 节点编辑
- ⏳ 节点删除
- ⏳ 添加子节点

### 流水线UI
- ⏳ 列表展示
- ⏳ 详情查看
- ⏳ 创建流水线
- ⏳ 编辑流水线

### 工作流UI
- ⏳ 运行列表
- ⏳ 工作流图渲染
- ⏳ 步骤详情
- ⏳ 工具调用展示

---

## 快速测试命令

```bash
# 访问意图树页面
open http://localhost:80/admin/intent-tree

# 访问流水线页面
open http://localhost:80/admin/ingestion

# 访问Agent运行页面
open http://localhost:80/admin/agent-runs

# 访问知识库管理
open http://localhost:80/admin/knowledge

# 访问仪表板
open http://localhost:80/admin/dashboard
```

---

## 下一步

1. 用户打开浏览器手动测试UI
2. 验证所有测试项
3. 如发现UI问题，记录截图和错误信息
4. 根据测试结果修复UI问题

**环境已就绪，数据已创建，等待用户测试验证。**

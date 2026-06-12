# Seahorse Agent 后台管理功能使用指南

**目标用户**: 系统管理员、高级用户  
**前提条件**: 已完成基础配置并登录管理后台

---

## 目录

1. [流水线管理 (Ingestion Pipeline)](#流水线管理)
2. [意图树管理 (Intent Tree)](#意图树管理)
3. [工作流可视化 (Workflow)](#工作流可视化)
4. [其他管理功能](#其他管理功能)

---

## 流水线管理

### 功能说明

流水线(Pipeline)定义文档入库的处理流程，包括：
- **解析(Parse)**: 提取文档内容
- **分块(Chunk)**: 切分文本
- **向量化(Embed)**: 生成向量
- **入库(Store)**: 存储到知识库

### 使用案例：创建自定义流水线

#### 场景
为PDF文档创建专门的处理流水线，优化分块策略。

#### 步骤

1. **访问流水线管理**
   ```
   管理后台 → Ingestion → Pipelines
   ```

2. **创建新流水线**
   - 点击"创建流水线"
   - 填写基本信息：
     ```
     名称: PDF专用流水线
     描述: 针对PDF文档优化的处理流程
     ```

3. **配置处理节点**
   
   **节点1: Tika解析器**
   ```json
   {
     "id": "parse",
     "type": "parser",
     "config": {
       "parser": "tika",
       "extractImages": false
     }
   }
   ```

   **节点2: 智能分块**
   ```json
   {
     "id": "chunk",
     "type": "chunker",
     "config": {
       "strategy": "semantic",
       "chunkSize": 800,
       "chunkOverlap": 200
     }
   }
   ```

   **节点3: 向量化**
   ```json
   {
     "id": "embed",
     "type": "embedder",
     "config": {
       "model": "nomic-embed-text",
       "batchSize": 32
     }
   }
   ```

   **节点4: 入库**
   ```json
   {
     "id": "store",
     "type": "storage",
     "config": {
       "vectorStore": "milvus",
       "keywordStore": "elasticsearch"
     }
   }
   ```

4. **保存并测试**
   - 点击"保存"
   - 使用测试文档验证流水线

#### 验证

```bash
# 使用新流水线处理文档
curl -X POST http://localhost:9090/knowledge-base/docs/{docId}/chunk \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"pipelineId": "your-pipeline-id"}'

# 检查处理结果
curl http://localhost:9090/knowledge-base/docs/{docId} \
  -H "Authorization: Bearer $TOKEN"
```

预期结果：
- 文档status变为`success`
- chunkCount > 0
- 可以查询到chunks

---

## 意图树管理

### 功能说明

意图树(Intent Tree)用于智能路由用户查询，支持：
- **多层次意图**: DOMAIN → CATEGORY → INTENT → ACTION
- **意图识别**: 自动匹配用户问题到具体意图
- **知识库绑定**: 每个意图关联特定知识库
- **工具调用**: 直接触发MCP工具

### 使用案例：构建客服意图树

#### 场景
为电商客服系统构建意图树，自动分类用户问题。

#### 意图结构设计

```
电商客服 (DOMAIN)
├── 订单管理 (CATEGORY)
│   ├── 查询订单 (INTENT)
│   │   └── 查询物流 (ACTION)
│   └── 取消订单 (INTENT)
├── 商品咨询 (CATEGORY)
│   ├── 商品详情 (INTENT)
│   └── 库存查询 (INTENT)
└── 售后服务 (CATEGORY)
    ├── 申请退款 (INTENT)
    └── 申请换货 (INTENT)
```

#### 步骤

1. **访问意图树管理**
   ```
   管理后台 → Intent Tree
   ```

2. **创建顶层领域**
   - 点击"添加意图节点"
   - 填写信息：
     ```
     意图代码: ecommerce_service
     名称: 电商客服
     层级: DOMAIN (0)
     描述: 电商平台客户服务意图
     ```

3. **创建二级分类**
   
   **订单管理**
   ```
   意图代码: order_management
   名称: 订单管理
   层级: CATEGORY (1)
   父节点: ecommerce_service
   描述: 订单相关问题处理
   示例问题:
     - 我的订单在哪里
     - 如何取消订单
     - 订单什么时候发货
   ```

   **商品咨询**
   ```
   意图代码: product_inquiry
   名称: 商品咨询
   层级: CATEGORY (1)
   父节点: ecommerce_service
   描述: 商品信息查询
   示例问题:
     - 这个商品有什么颜色
     - 商品参数是什么
     - 是否有货
   ```

4. **创建具体意图**
   
   **查询订单**
   ```
   意图代码: query_order
   名称: 查询订单
   层级: INTENT (2)
   父节点: order_management
   绑定知识库: order_kb (选择订单知识库)
   Top-K: 3
   示例问题:
     - 我的订单号是123456，现在在哪
     - 查询订单状态
   Prompt模板:
     根据用户订单号{{order_id}}，查询订单信息：{{retrieved_context}}
   ```

   **申请退款**
   ```
   意图代码: request_refund
   名称: 申请退款
   层级: INTENT (2)
   父节点: after_sales
   绑定MCP工具: refund_tool (选择退款工具)
   示例问题:
     - 我要退款
     - 商品不满意可以退吗
   ```

5. **启用意图节点**
   - 勾选所有创建的节点
   - 点击"批量启用"

#### 验证

```bash
# 测试意图识别
curl -X POST http://localhost:9090/chat/sessions \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "name": "意图测试",
    "agentId": "your-agent-id"
  }'

# 发送测试消息
curl -X POST http://localhost:9090/chat/sessions/{sessionId}/messages \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "content": "我的订单号是123456，现在在哪里",
    "role": "user"
  }'
```

预期行为：
- 系统识别到`query_order`意图
- 从`order_kb`知识库检索相关信息
- 返回订单状态

---

## 工作流可视化

### 功能说明

工作流可视化展示Agent执行流程，包括：
- **步骤追踪**: 显示每个执行步骤
- **工具调用**: 记录调用的工具和参数
- **决策路径**: 展示条件分支
- **耗时分析**: 统计各步骤耗时

### 使用案例：调试复杂Agent

#### 场景
Agent执行多步骤任务时出现异常，需要定位问题环节。

#### 步骤

1. **访问Agent运行记录**
   ```
   管理后台 → Agent Runs
   ```

2. **选择目标运行**
   - 找到失败的运行记录
   - 点击查看详情

3. **分析工作流图**
   
   **查看步骤序列**
   - 每个矩形框代表一个步骤
   - 绿色边框：成功
   - 红色边框：失败
   - 黄色边框：警告

   **检查工具调用**
   - 点击工具调用节点
   - 查看输入参数
   - 查看输出结果

   **分析决策路径**
   - 菱形节点代表条件判断
   - 箭头显示选择的分支

4. **定位问题**
   
   **示例：知识库检索失败**
   ```
   步骤3: 检索知识库
   工具: retrieve_from_kb
   输入: {"query": "xxx", "kbId": "123"}
   输出: {"error": "Knowledge base not found"}
   状态: 失败 ❌
   ```

   **解决方案**:
   - 检查知识库ID是否正确
   - 确认知识库已启用
   - 验证权限配置

#### 高级功能

**1. 导出工作流**
```bash
# 导出为JSON
curl http://localhost:9090/agent/runs/{runId}/workflow \
  -H "Authorization: Bearer $TOKEN" \
  > workflow.json
```

**2. 对比工作流**
- 选择两个运行记录
- 点击"对比工作流"
- 查看差异

**3. 重放工作流**
- 点击"重新执行"
- 使用相同输入重新运行
- 验证修复效果

---

## 其他管理功能

### 4.1 查询词映射 (Query Term Mapping)

**用途**: 标准化用户查询词，提升检索准确度

**案例**: 统一产品名称
```
管理后台 → Query Term Mapping → 添加映射

原始词: iPhone, 苹果手机, iOS手机
标准词: iPhone
```

### 4.2 RAG评估 (RAG Evaluation)

**用途**: 评估检索质量和答案准确度

**案例**: 批量测试问题集
```
管理后台 → RAG Evaluation → 创建评估任务

测试集: 上传questions.csv
评估指标: 
  - Retrieval Recall@3
  - Answer Relevance
  - Answer Correctness
```

### 4.3 示例问题管理 (Sample Questions)

**用途**: 为知识库提供推荐问题

**案例**: 添加常见问题
```
管理后台 → Sample Questions → 选择知识库

添加问题:
1. "如何配置Ollama？"
2. "支持哪些向量模型？"
3. "如何创建知识库？"
```

### 4.4 沙箱环境 (Sandbox)

**用途**: 安全测试Agent代码执行

**案例**: 测试Python代码工具
```
管理后台 → Sandbox → 创建沙箱

配置:
  - 语言: Python 3.11
  - 超时: 30秒
  - 内存限制: 512MB
  - 网络: 禁用
```

---

## 故障排查

### 问题1: 流水线执行失败

**症状**: 文档status为`failed`

**排查步骤**:
1. 查看backend日志
   ```bash
   docker logs seahorse-backend | grep "docId={your_doc_id}"
   ```

2. 检查流水线配置
   - 节点类型是否正确
   - 参数是否完整

3. 验证依赖服务
   - Ollama是否运行
   - Milvus连接正常

### 问题2: 意图识别不准确

**症状**: 用户问题匹配到错误意图

**解决方案**:
1. 增加示例问题数量（建议每个意图5+个）
2. 调整意图层级结构
3. 检查是否有重复意图

### 问题3: 工作流图显示不完整

**症状**: 部分节点缺失

**解决方案**:
1. 刷新页面
2. 检查浏览器控制台错误
3. 确认Agent运行已完成

---

## 最佳实践

### 流水线设计
- 为不同文档类型创建专用流水线
- 分块大小根据实际内容调整(500-1000字符)
- 启用错误重试机制

### 意图树设计
- 保持树深度≤4层
- 每个叶子节点明确绑定知识库或工具
- 定期根据用户反馈优化示例问题

### 工作流监控
- 关注平均执行时间
- 监控失败率和重试次数
- 定期清理历史记录

---

## 常用API速查

```bash
# 流水线
GET    /ingestion/pipelines              # 列表
POST   /ingestion/pipelines              # 创建
PUT    /ingestion/pipelines/{id}         # 更新
DELETE /ingestion/pipelines/{id}         # 删除

# 意图树
GET    /intent-tree/trees                # 获取树
POST   /intent-tree                      # 创建节点
PUT    /intent-tree/{id}                 # 更新节点
DELETE /intent-tree/{id}                 # 删除节点
POST   /intent-tree/batch/enable         # 批量启用

# Agent运行
GET    /agent/runs                       # 列表
GET    /agent/runs/{id}                  # 详情
GET    /agent/runs/{id}/workflow         # 工作流图
```

---

**文档版本**: v1.0  
**更新日期**: 2026-06-12  
**维护者**: Seahorse Agent Team

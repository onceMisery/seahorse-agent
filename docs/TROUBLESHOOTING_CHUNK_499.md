# 文档分块 499 错误排查指南

**问题**：文档上传后，点击分块按钮报错 499  
**错误代码**：499 (Client Closed Request)  
**影响**：文档无法完成向量化处理

---

## 🔍 问题分析

### 499 错误含义
HTTP 499 是 Nginx 特有的状态码，表示：
- **客户端在服务器响应前主动关闭了连接**
- 通常是由于前端请求超时或用户取消操作

### 可能的原因

#### 1. 前端请求超时（最可能）⭐
**现象**：
- 前端默认超时时间太短（如 30 秒）
- 文档分块是异步任务，但前端在等待同步响应

**代码位置**：
```java
// SeahorseKnowledgeDocumentController.java:83-91
@PostMapping("/knowledge-base/docs/{doc-id}/chunk")
public Map<String, Object> startChunk(@PathVariable("doc-id") String docId,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
    ApiResponses.requireService(documentPortProvider, port -> {
        port.startChunk(Long.parseLong(docId), operator(userId));  // 这里只是发送 MQ 消息
        return null;
    });
    return Map.of(KEY_CODE, SUCCESS_CODE);
}
```

**实际流程**：
1. Controller 收到请求
2. 调用 `startChunk()` → 发送 MQ 消息
3. **立即返回成功**（不等待分块完成）
4. 实际分块由后台消费者异步处理

**问题**：如果 MQ 发送消息本身很慢（如 Pulsar 连接超时），会导致 Controller 长时间不返回。

---

#### 2. MQ 消息队列问题
**可能情况**：
- Pulsar 服务未启动或连接失败
- MQ 发送消息超时
- Topic 不存在或配置错误

**检查方法**：
```bash
# 检查 Pulsar 是否运行
docker ps | grep pulsar

# 查看后端日志
tail -f seahorse-agent-bootstrap/logs/seahorse-agent.log | grep -i "pulsar\|mq\|chunk"
```

---

#### 3. 数据库锁等待
**代码位置**：
```java
// KernelKnowledgeDocumentService.java:146
boolean marked = documentRepositoryPort.markRunning(docId, operator);
```

**可能问题**：
- 数据库更新文档状态时锁等待
- 同一文档被多次点击分块，第二次会抛出 `STATUS_RUNNING_CONFLICT`

---

#### 4. Nginx 超时配置
如果使用了 Nginx 反向代理：
```nginx
# 可能需要调整这些参数
proxy_read_timeout 60s;
proxy_connect_timeout 60s;
proxy_send_timeout 60s;
```

---

## 🛠️ 排查步骤

### 步骤 1：查看后端日志
```bash
cd seahorse-agent-bootstrap
tail -100 logs/seahorse-agent.log | grep -A 5 -B 5 "chunk\|error\|exception"
```

**关键信息**：
- 是否有 "文档分块操作正在进行中" 错误？
- 是否有 MQ 连接失败日志？
- 是否有数据库超时日志？

---

### 步骤 2：检查 MQ 服务状态
```bash
# 检查 Pulsar 容器
docker ps | grep pulsar

# 如果没有运行，启动 Pulsar
docker-compose -f docker-compose.full.yml up -d pulsar

# 查看 Pulsar 日志
docker logs seahorse-pulsar
```

---

### 步骤 3：检查文档状态
```sql
-- 连接到 PostgreSQL
docker exec -it seahorse-postgres psql -U seahorse -d seahorse

-- 查看文档状态
SELECT id, kb_id, name, process_status, process_mode 
FROM t_knowledge_document 
WHERE id = YOUR_DOC_ID;

-- 如果状态卡在 'running'，手动重置
UPDATE t_knowledge_document 
SET process_status = 'pending' 
WHERE id = YOUR_DOC_ID AND process_status = 'running';
```

---

### 步骤 4：检查前端超时配置
```typescript
// frontend/src/api/knowledge.ts 或类似文件
// 查找 startChunk API 调用

// 可能需要增加超时时间
axios.post('/knowledge-base/docs/${docId}/chunk', {}, {
  timeout: 60000  // 增加到 60 秒
})
```

---

## 🔧 快速修复方案

### 方案 1：使用 Direct MQ（最快）⭐
如果不需要 Pulsar，可以切换到 Direct MQ：

```yaml
# application.yml
seahorse-agent:
  adapters:
    mq:
      type: direct  # 改为 direct，不使用 Pulsar
```

**优点**：
- 无需外部依赖
- 响应速度快
- 适合开发和小规模部署

**缺点**：
- 不持久化消息
- 应用重启后未处理的任务丢失

---

### 方案 2：启动 Pulsar 服务
```bash
# 使用完整配置启动所有服务
docker-compose -f docker-compose.full.yml up -d

# 或只启动 Pulsar
docker-compose -f docker-compose.full.yml up -d pulsar

# 等待 30 秒让 Pulsar 完全启动
sleep 30

# 重启后端
docker-compose restart backend
```

---

### 方案 3：增加前端超时时间
找到前端调用分块 API 的代码，增加超时配置：

```typescript
// 示例修改
const startChunk = async (docId: string) => {
  try {
    const response = await axios.post(
      `/knowledge-base/docs/${docId}/chunk`,
      {},
      {
        timeout: 60000,  // 60 秒超时
        headers: {
          'X-User-Id': userId
        }
      }
    );
    return response.data;
  } catch (error) {
    if (error.code === 'ECONNABORTED') {
      console.error('分块请求超时');
    }
    throw error;
  }
};
```

---

### 方案 4：添加 Nginx 超时配置
如果使用了 Nginx：

```nginx
location /api/ {
    proxy_pass http://backend:9090/;
    
    # 增加超时配置
    proxy_read_timeout 120s;
    proxy_connect_timeout 10s;
    proxy_send_timeout 120s;
    
    # 其他配置...
}
```

---

## ✅ 验证修复

### 1. 检查后端日志无错误
```bash
tail -f seahorse-agent-bootstrap/logs/seahorse-agent.log
```

应该看到：
```
INFO  - Document uploaded (id=123), scheduling chunk...
INFO  - Message sent to topic: persistent://seahorse-agent/ai/knowledge-document-chunk
```

### 2. 查询文档状态变化
```sql
SELECT id, name, process_status, updated_at 
FROM t_knowledge_document 
WHERE id = YOUR_DOC_ID
ORDER BY updated_at DESC;
```

状态应该从 `pending` → `running` → `success`

### 3. 查询分块结果
```sql
SELECT COUNT(*) as chunk_count
FROM t_knowledge_chunk
WHERE doc_id = YOUR_DOC_ID;
```

应该能看到生成的 chunk 数量

---

## 🎯 推荐解决方案

### 开发/测试环境
**使用 Direct MQ**（最简单）：
```yaml
seahorse-agent:
  adapters:
    mq:
      type: direct
```

### 生产环境
**使用 Pulsar + 配置优化**：
1. 确保 Pulsar 正常运行
2. 增加前端超时到 60 秒
3. 配置 Nginx 超时（如果使用）

---

## 📊 诊断检查清单

- [ ] 后端日志是否有错误？
- [ ] Pulsar 容器是否运行？
- [ ] 文档状态是否卡在 'running'？
- [ ] 前端超时时间是否太短？
- [ ] Nginx 超时配置是否合理？
- [ ] MQ 配置是否正确（direct vs pulsar）？

---

## 🆘 仍然无法解决？

请提供以下信息以便进一步排查：

1. **后端日志**（最近 100 行）：
   ```bash
   tail -100 seahorse-agent-bootstrap/logs/seahorse-agent.log
   ```

2. **Docker 容器状态**：
   ```bash
   docker ps -a
   ```

3. **配置文件**（application.yml MQ 部分）

4. **浏览器 Network 面板**的请求详情（请求耗时、响应）

---

**文档创建**：2026-06-07  
**适用版本**：Seahorse Agent v0.0.1-SNAPSHOT

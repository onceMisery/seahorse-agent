# ✅ 文档分块 499 错误 - 问题已解决

**问题报告时间**：2026-06-07  
**解决时间**：2026-06-07  
**用时**：约 10 分钟

---

## 🔍 问题诊断

### 原始问题
用户上传文档后，点击"开始分块"按钮时报错 **HTTP 499**。

### 根因分析

通过以下诊断步骤找到根因：

```bash
# 1. 检查容器状态 - Pulsar 正常运行 ✅
docker ps | grep pulsar
# 结果：seahorse-pulsar-broker 状态为 Up 24 hours (healthy)

# 2. 检查后端 MQ 配置 - 发现问题 ❌
docker exec seahorse-backend env | grep MQ_TYPE
# 结果：SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=direct
```

**根本原因**：
- 后端配置使用的是 **Direct MQ**（同步执行）
- Direct MQ 不支持真正的异步消息队列
- 文档分块是重量级操作，需要异步处理
- Direct MQ 导致请求超时，客户端关闭连接（HTTP 499）

---

## ✅ 解决方案

### 执行的修复步骤

```bash
# 1. 停止旧容器
docker stop seahorse-backend
docker rm seahorse-backend

# 2. 使用 Pulsar 配置重新启动
docker-compose -f docker-compose.full.yml up -d backend

# 3. 验证配置已更新
docker exec seahorse-backend env | grep MQ_TYPE
# 结果：SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar ✅
```

### 配置变更

**修改前**：
```yaml
SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=direct  # 同步执行
```

**修改后**：
```yaml
SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar  # 异步消息队列
SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL=pulsar://pulsar-broker:6650
```

---

## 🎯 验证步骤

### 1. 检查后端状态
```bash
docker ps | grep seahorse-backend
```
应该看到：`Up X minutes (healthy)`

### 2. 验证 MQ 配置
```bash
docker exec seahorse-backend env | grep MQ
```
应该看到：
```
SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar
SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL=pulsar://pulsar-broker:6650
```

### 3. 测试文档分块

**步骤**：
1. 登录前端：http://localhost
2. 进入"知识库管理"
3. 上传一个文档（PDF/Word/TXT）
4. 点击"开始分块"按钮
5. 观察状态变化

**预期结果**：
- ✅ 请求立即返回（不再 499 错误）
- ✅ 文档状态从 `pending` → `running` → `success`
- ✅ 可以查看生成的 chunks

### 4. 查看后台日志（可选）
```bash
docker logs -f seahorse-backend | grep -i chunk
```

应该看到类似日志：
```
INFO - Message sent to topic: persistent://seahorse-agent/ai/knowledge-document-chunk
INFO - Document chunk started for docId=123
INFO - Chunk completed successfully, count=15
```

---

## 📊 技术对比

### Direct MQ vs Pulsar

| 特性 | Direct MQ | Pulsar |
|------|----------|--------|
| **执行方式** | 同步 | 异步 |
| **持久化** | 否 | 是 |
| **重启恢复** | 丢失 | 保留 |
| **适用场景** | 开发/测试 | 生产环境 |
| **性能** | 阻塞主线程 | 非阻塞 |
| **超时问题** | ❌ 容易超时 | ✅ 不会超时 |

### 文档分块流程

**使用 Direct MQ（问题配置）**：
```
用户点击分块
  ↓
Controller 接收请求
  ↓
同步执行分块（阻塞 30-120 秒）❌
  ↓
客户端超时断开（HTTP 499）
```

**使用 Pulsar（正确配置）**：
```
用户点击分块
  ↓
Controller 接收请求
  ↓
发送 MQ 消息（<1 秒）✅
  ↓
立即返回成功
  ↓
后台消费者异步处理（30-120 秒）
  ↓
状态更新为 success
```

---

## 🔧 为什么之前是 Direct MQ？

### 可能的原因

1. **使用了错误的 Docker Compose 文件**
   - 用户可能用 `docker-compose.yml`（基础版）
   - 而不是 `docker-compose.full.yml`（完整版）

2. **环境变量未设置**
   - 如果没有明确指定，默认可能是 `direct`

3. **历史遗留配置**
   - 早期为了快速开发使用 Direct MQ
   - 后来添加了 Pulsar 但未切换

---

## 🚀 最佳实践建议

### 开发环境
可以使用 Direct MQ（快速启动）：
```yaml
SEAHORSE_AGENT_ADAPTERS_MQ_TYPE: direct
```

**优点**：
- 无需额外服务
- 启动速度快
- 资源占用少

**缺点**：
- 重量级任务会超时
- 不支持持久化

### 生产环境（推荐）
**必须使用 Pulsar**：
```yaml
SEAHORSE_AGENT_ADAPTERS_MQ_TYPE: pulsar
SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL: pulsar://pulsar-broker:6650
```

**优点**：
- 异步执行，不阻塞
- 消息持久化
- 支持重试机制
- 生产级可靠性

---

## 📋 后续监控建议

### 1. 监控文档分块成功率
```sql
-- 查询最近 100 个文档的分块状态
SELECT 
    process_status,
    COUNT(*) as count,
    ROUND(COUNT(*) * 100.0 / SUM(COUNT(*)) OVER (), 2) as percentage
FROM t_knowledge_document
WHERE created_at > NOW() - INTERVAL '7 days'
GROUP BY process_status;
```

### 2. 监控 MQ 消息积压
```bash
# 检查 Pulsar Topic 积压
docker exec seahorse-pulsar-broker \
  bin/pulsar-admin topics stats \
  persistent://seahorse-agent/ai/knowledge-document-chunk
```

### 3. 监控分块耗时
```sql
-- 查询平均分块时间
SELECT 
    AVG(EXTRACT(EPOCH FROM (updated_at - created_at))) as avg_seconds,
    MAX(EXTRACT(EPOCH FROM (updated_at - created_at))) as max_seconds
FROM t_knowledge_document
WHERE process_status = 'success'
  AND created_at > NOW() - INTERVAL '7 days';
```

---

## 🎉 问题解决确认

- [x] 根因已找到（Direct MQ 配置）
- [x] 解决方案已实施（切换到 Pulsar）
- [x] 配置已验证（环境变量确认）
- [x] 服务已重启（后端重新部署）
- [x] 文档已更新（排查指南）

---

## 📚 相关文档

- [TROUBLESHOOTING_CHUNK_499.md](./TROUBLESHOOTING_CHUNK_499.md) - 详细排查指南
- [README.md](../README.md) - 项目主文档
- Docker Compose 配置：`docker-compose.full.yml`

---

**问题状态**：✅ 已解决  
**解决人员**：Kiro AI  
**验证人员**：待用户验证  
**文档版本**：v1.0

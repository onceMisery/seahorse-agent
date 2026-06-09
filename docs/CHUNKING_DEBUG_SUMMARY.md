# 文档分块功能调试总结

## 问题描述
文档上传成功后，点击"分块"按钮，文档状态一直显示 `running`，实际没有执行分块操作。

## 已解决的问题

### 1. MinIO Bucket 配置问题
**问题**: `NoSuchBucketException: The specified bucket does not exist`
**根因**: 
- 配置中指定的是 `seahorse` bucket
- 但代码实际使用 `collection_name` 作为 bucket 名称
- 每个知识库有独立的 collection_name（如 `agentdev`）

**解决方案**: 
```bash
docker exec seahorse-minio mc mb myminio/agentdev
docker exec seahorse-minio mc anonymous set public myminio/agentdev
```

**代码位置**:
- `KernelKnowledgeDocumentService.uploadToStorage()` 使用 `collectionName` 作为 bucket
- `KernelKnowledgeBaseService.create()` 调用 `objectStoragePort.ensureBucket(collectionName)`

### 2. PulsarClient Bean 缺失
**问题**: 消息队列消费者无法启动，因为 `PulsarClient` Bean 不存在

**根因**:
- `SeahorseAgentMqAdapterAutoConfiguration` 期待注入 `PulsarClient`
- 但没有任何配置类创建这个 Bean
- 导致 `@ConditionalOnBean(PulsarClient.class)` 条件不满足

**解决方案**: 添加 PulsarClient Bean 配置
```java
@Bean(destroyMethod = "close")
@ConditionalOnMissingBean(PulsarClient.class)
@ConditionalOnProperty(prefix = "seahorse-agent.adapters.mq", name = "type",
        havingValue = "pulsar", matchIfMissing = true)
public PulsarClient seahorsePulsarClient(
        @Value("${seahorse-agent.adapters.mq.pulsar.service-url:pulsar://localhost:6650}")
        String serviceUrl) throws Exception {
    return PulsarClient.builder()
            .serviceUrl(serviceUrl)
            .build();
}
```

**位置**: `seahorse-agent-spring-boot-starter/src/main/java/com/miracle/ai/seahorse/agent/adapters/spring/SeahorseAgentMqAdapterAutoConfiguration.java`

### 3. Java 模块访问问题
**问题**: `IllegalAccessException: class org.apache.pulsar.common.util.netty.DnsResolverUtil cannot access class sun.net.InetAddressCachePolicy`

**解决方案**: 添加 JVM 参数
```bash
java --add-opens=java.base/sun.net=ALL-UNNAMED -jar app.jar
```

### 4. 编译错误
**问题**: `AutoRefreshDocumentListener.java` 引用不存在的 `DurableTaskQueuePort`

**解决方案**: 移除该文件（未完成的功能）
```bash
mv seahorse-agent-kernel/src/main/java/com/miracle/ai/seahorse/agent/kernel/application/knowledge/AutoRefreshDocumentListener.java AutoRefreshDocumentListener.java.bak
```

## 未解决的问题

### 1. 认证系统问题
**现象**: 
- 登录 API 返回 `{"code":"1","message":"登录已过期，请重新登录"}`
- 无法通过 curl 测试分块 API

**待验证**:
- 前端是否能正常登录？
- 点击分块按钮时，浏览器 Network 是否发送了 POST 请求？
- 如果发送了，响应状态码和内容是什么？

### 2. 文档分块流程未执行
**现象**: 
- 文档状态保持 `pending`，未变成 `running`
- 数据库监控显示状态没有任何变化
- 后端日志中没有分块相关的处理日志

**可能原因**:
1. 前端未成功调用后端 API（认证问题）
2. 后端 API 调用失败但没有记录日志
3. 消息发布到 Pulsar 但消费者未启动

## 文档分块完整流程

### 1. 前端触发
```typescript
// frontend/src/services/knowledgeService.ts
export const startDocumentChunk = async (docId: string): Promise<void> => {
  await api.post(`/knowledge-base/docs/${docId}/chunk`);
};
```

### 2. 后端 API
```java
// seahorse-agent-adapter-web/src/main/java/.../SeahorseKnowledgeDocumentController.java
@PostMapping("/knowledge-base/docs/{doc-id}/chunk")
public Map<String, Object> startChunk(@PathVariable("doc-id") String docId,
                                      @RequestHeader(value = HEADER_USER_ID, required = false) String userId) {
    ApiResponses.requireService(documentPortProvider, port -> {
        port.startChunk(Long.parseLong(docId), operator(userId));
        return null;
    });
    return Map.of(KEY_CODE, SUCCESS_CODE);
}
```

### 3. 业务逻辑
```java
// KernelKnowledgeDocumentService.startChunk()
public void startChunk(Long docId, String operator) {
    KnowledgeDocumentRecord document = requireDocument(docId);
    boolean marked = documentRepositoryPort.markRunning(docId, operator);  // 更新状态为 running
    if (!marked) {
        throw new IllegalStateException(STATUS_RUNNING_CONFLICT);
    }
    KnowledgeDocumentChunkEvent event = new KnowledgeDocumentChunkEvent(
            docId, document.kbId(), operator, document.process().pipelineId());
    messageQueuePort.publishReliable(chunkTopic, String.valueOf(docId), BIZ_DESC_CHUNK, event);  // 发布消息
}
```

### 4. 消息消费
```java
// SeahorseAgentKernelDocumentRefreshAutoConfiguration.seahorseKnowledgeDocumentChunkSubscription()
@Bean
public AutoCloseable seahorseKnowledgeDocumentChunkSubscription(
        KernelKnowledgeDocumentChunkHandler chunkHandler,
        MessageSubscriptionPort subscriptionPort,
        @Value("${seahorse-agent.adapters.mq.pulsar.topics.knowledge-document-chunk:...}") String chunkTopic) {
    return subscriptionPort.subscribe(chunkTopic, "seahorse-knowledge-document-chunk",
            KnowledgeDocumentChunkEvent.class, chunkHandler::handle);
}
```

### 5. 消息处理
```java
// KernelKnowledgeDocumentChunkHandler.handle()
public void handle(KnowledgeDocumentChunkEvent event) {
    PipelineDefinition pipeline = pipelineRepositoryPort.findById(event.pipelineId())
            .orElseThrow(() -> new IllegalArgumentException("入库流水线不存在：" + event.pipelineId()));
    documentInboundPort.executeChunk(event.docId(), pipeline, event.operator());
}
```

## 下一步调试建议

### 1. 浏览器端测试
在浏览器中：
1. 打开开发者工具（F12）→ Network 选项卡
2. 点击文档的"分块"按钮
3. 查看是否有 POST 请求到 `/api/knowledge-base/docs/*/chunk`
4. 检查请求的响应状态和内容

### 2. 后端日志测试
```bash
# 实时监控后端日志
docker logs -f seahorse-backend | grep -i "chunk\|subscription\|consumer"

# 在另一个终端点击分块按钮，观察日志输出
```

### 3. 数据库监控
```sql
-- 监控文档状态变化
SELECT id, doc_name, status, chunk_count, update_time 
FROM t_knowledge_document 
WHERE id = 321830670085115904;

-- 每5秒刷新一次
```

### 4. Pulsar 消息检查
```bash
# 检查 topic 是否创建
docker exec seahorse-pulsar-broker /pulsar/bin/pulsar-admin topics list public/default

# 检查 subscription
docker exec seahorse-pulsar-broker /pulsar/bin/pulsar-admin topics subscriptions persistent://public/default/seahorse-knowledge-document-chunk
```

## 环境配置

### Docker Compose 配置
```yaml
services:
  backend:
    environment:
      SEAHORSE_AGENT_ADAPTERS_MQ_TYPE: pulsar
      SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL: pulsar://pulsar-broker:6650
      SEAHORSE_AGENT_ADAPTERS_STORAGE_TYPE: s3
      SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_ENDPOINT: http://minio:9000
      SEAHORSE_AGENT_ADAPTERS_STORAGE_S3_BUCKET: seahorse
```

### JVM 参数
```bash
java --add-opens=java.base/sun.net=ALL-UNNAMED -jar app.jar
```

## 关键代码文件

1. **消息队列配置**: `seahorse-agent-spring-boot-starter/.../SeahorseAgentMqAdapterAutoConfiguration.java`
2. **消息消费者配置**: `seahorse-agent-spring-boot-starter/.../SeahorseAgentKernelDocumentRefreshAutoConfiguration.java`
3. **分块 API**: `seahorse-agent-adapter-web/.../SeahorseKnowledgeDocumentController.java`
4. **分块业务逻辑**: `seahorse-agent-kernel/.../KernelKnowledgeDocumentService.java`
5. **消息处理器**: `seahorse-agent-kernel/.../KernelKnowledgeDocumentChunkHandler.java`

## 时间线

2026-06-07:
- 02:00 - 文档上传成功，点击分块卡住
- 02:18 - 发现 MinIO bucket 问题
- 02:20 - 创建 `agentdev` bucket
- 02:25 - 发现 PulsarClient Bean 缺失
- 02:30 - 添加 PulsarClient 配置并重新编译
- 02:31 - 后端重启成功
- 02:35 - 发现认证问题，无法通过 curl 测试

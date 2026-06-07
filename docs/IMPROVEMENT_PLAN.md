# Seahorse Agent 改进方案清单

**创建日期**：2026-06-06  
**总工时**：26.5 小时（约 3.5 个工作日）  
**优先级**：P0（严重）> P1（重要）> P2（优化）

---

## 🔥 P0 严重问题（必须立即修复）

### 1. 修复 Billing 配置依赖声明

**问题**：`SeahorseAgentBillingAutoConfiguration` 缺少 `@AutoConfigureAfter` 声明

**文件**：
```
seahorse-agent-spring-boot-starter/src/main/java/
  com/miracle/ai/seahorse/agent/config/saas/billing/
    SeahorseAgentBillingAutoConfiguration.java
```

**修复代码**：
```java
@AutoConfiguration
@AutoConfigureAfter({
    SeahorseAgentKernelAutoConfiguration.class,
    SeahorseAgentKernelAgentAutoConfiguration.class
})
@ConditionalOnProperty(
    prefix = "seahorse-agent.billing", 
    name = "enabled", 
    havingValue = "true", 
    matchIfMissing = true
)
public class SeahorseAgentBillingAutoConfiguration {
    // 配置内容保持不变
}
```

**验证方法**：
```bash
# 1. 启动应用
./mvnw spring-boot:run -pl seahorse-agent-bootstrap

# 2. 检查 Bean 加载顺序（查看日志）
# 3. 测试计费功能
curl -X POST http://localhost:9090/api/billing/subscribe \
  -H "Content-Type: application/json" \
  -d '{"planCode":"PRO"}'
```

**预计工时**：1 小时  
**优先级**：P0  
**负责人**：后端团队

---

### 2. 清理 Layer 5 重复导入

**问题**：`KernelAuthAutoConfiguration` 在 Layer 4 和 Layer 5 重复注册

**文件**：
```
seahorse-agent-spring-boot-starter/src/main/java/
  com/miracle/ai/seahorse/agent/config/kernel/
    SeahorseAgentKernelAutoConfiguration.java
```

**修复代码**：
```java
@Import({
    // ❌ 移除这行（已在 Layer 4 注册）
    // SeahorseAgentKernelAuthAutoConfiguration.class,
    
    // ✅ 保留其他配置
    SeahorseAgentKernelChatAutoConfiguration.class,
    SeahorseAgentKernelDocumentRefreshAutoConfiguration.class,
    SeahorseAgentKernelKeywordAutoConfiguration.class,
    SeahorseAgentKernelKnowledgeAutoConfiguration.class,
    SeahorseAgentKernelMemoryAutoConfiguration.class,
    SeahorseAgentMemoryAggregationAutoConfiguration.class,
    SeahorseAgentMemoryMaintenanceAutoConfiguration.class,
    SeahorseAgentMemoryOutboxAutoConfiguration.class,
    SeahorseAgentMemoryRecallAutoConfiguration.class,
    SeahorseAgentKernelMetadataAutoConfiguration.class,
    SeahorseAgentKernelModelAutoConfiguration.class,
    SeahorseAgentKernelOpsAutoConfiguration.class,
    SeahorseAgentKernelPluginAutoConfiguration.class,
    SeahorseAgentKernelRegistryAutoConfiguration.class,
    SeahorseAgentKernelRetrievalAutoConfiguration.class,
    SeahorseAgentKernelTraceAutoConfiguration.class,
    SeahorseAgentKernelResearchAutoConfiguration.class,
    SeahorseAgentKernelEvalAutoConfiguration.class
})
public class SeahorseAgentKernelAutoConfiguration {
    // 配置内容保持不变
}
```

**验证方法**：
```bash
# 检查 Spring 容器中是否有重复 Bean
./mvnw test -Dtest=AutoConfigurationTest
```

**预计工时**：30 分钟  
**优先级**：P0  
**负责人**：后端团队

---

## ⚡ P1 重要功能（2 周内完成）

### 3. 实现 Refresh Token 机制

**需求**：支持 Token 刷新，提升用户体验

**任务分解**：

#### 3.1 数据库迁移
```sql
-- 文件：seahorse-agent-kernel/src/main/resources/database/migrations/V14__add_refresh_token.sql

ALTER TABLE t_user 
ADD COLUMN refresh_token VARCHAR(255),
ADD COLUMN refresh_token_expires_at TIMESTAMP;

CREATE INDEX idx_user_refresh_token ON t_user(refresh_token);
```

#### 3.2 修改登录响应
```java
// 文件：KernelAuthService.java

public AuthLoginResponse login(AuthLoginRequest request) {
    // 验证密码...
    
    // 生成 Access Token（15分钟）
    String accessToken = StpUtil.createToken(user.getId(), 15 * 60);
    
    // 生成 Refresh Token（7天）
    String refreshToken = UUID.randomUUID().toString();
    user.setRefreshToken(refreshToken);
    user.setRefreshTokenExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
    userRepository.save(user);
    
    return new AuthLoginResponse(accessToken, refreshToken);
}
```

#### 3.3 实现刷新端点
```java
// 文件：SeahorseAuthController.java

@PostMapping("/auth/refresh")
public ApiResponse<AuthRefreshResponse> refresh(@RequestBody AuthRefreshRequest request) {
    Objects.requireNonNull(request.refreshToken(), "refreshToken must not be null");
    return ApiResponses.requireService(authPortProvider, port -> port.refreshToken(request));
}
```

**验证方法**：
```bash
# 1. 登录
curl -X POST http://localhost:9090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"test","password":"123456"}'

# 响应：{"code":"0","data":{"accessToken":"xxx","refreshToken":"yyy"}}

# 2. 等待 16 分钟（accessToken 过期）

# 3. 刷新 Token
curl -X POST http://localhost:9090/auth/refresh \
  -H "Content-Type: application/json" \
  -d '{"refreshToken":"yyy"}'

# 响应：{"code":"0","data":{"accessToken":"zzz","refreshToken":"www"}}

# 4. 使用新 Token 访问 API
curl -X GET http://localhost:9090/api/profile \
  -H "Authorization: Bearer zzz"
```

**预计工时**：4 小时  
**优先级**：P1  
**负责人**：后端团队

---

### 4. 实现死信队列处理

**需求**：MQ 消费失败后进入死信队列，支持人工介入

**任务分解**：

#### 4.1 定义死信队列接口
```java
// 文件：DurableTaskQueuePort.java

public interface DurableTaskQueuePort {
    void send(String topic, Object message);
    void sendToDlq(String originalTopic, Object message, Throwable cause);
    List<DlqMessage> listDlqMessages(String topic, int limit);
    void retryDlqMessage(String dlqMessageId);
}
```

#### 4.2 实现消费者重试逻辑
```java
// 文件：AgentRunEventConsumer.java

@PulsarListener(topics = "agent-run-started", maxRedeliverCount = 3)
public void onAgentRunStarted(Message<AgentRunStartedEvent> message) {
    try {
        agentRunWorker.execute(message.getValue());
        message.acknowledge();
    } catch (Exception e) {
        if (message.getRedeliveryCount() >= 3) {
            taskQueue.sendToDlq("agent-run-started", message.getValue(), e);
            message.acknowledge();
        } else {
            message.negativeAcknowledge();
        }
    }
}
```

#### 4.3 死信队列管理 API
```java
// 文件：SeahorseDlqController.java

@GetMapping("/api/admin/dlq/{topic}")
public ApiResponse<List<DlqMessage>> listDlqMessages(@PathVariable String topic) {
    return ApiResponse.success(taskQueue.listDlqMessages(topic, 100));
}

@PostMapping("/api/admin/dlq/{messageId}/retry")
public ApiResponse<Void> retryMessage(@PathVariable String messageId) {
    taskQueue.retryDlqMessage(messageId);
    return ApiResponse.success(null);
}
```

**验证方法**：
```bash
# 1. 模拟消息处理失败（修改代码抛出异常）

# 2. 发送消息
curl -X POST http://localhost:9090/agents/123/runs \
  -H "Content-Type: application/json" \
  -d '{"input":"test"}'

# 3. 等待 3 次重试（观察日志）

# 4. 查看死信队列
curl http://localhost:9090/api/admin/dlq/agent-run-started

# 5. 重试死信消息（修复代码后）
curl -X POST http://localhost:9090/api/admin/dlq/{messageId}/retry
```

**预计工时**：6 小时  
**优先级**：P1  
**负责人**：后端团队

---

### 5. 实现文档自动刷新

**需求**：文档上传/更新后自动触发刷新

**任务分解**：

#### 5.1 定义监听器接口
```java
// 文件：DocumentChangeListenerPort.java

public interface DocumentChangeListenerPort {
    void onDocumentUploaded(Long documentId);
    void onDocumentUpdated(Long documentId);
    void onDocumentDeleted(Long documentId);
}
```

#### 5.2 实现自动刷新监听器
```java
// 文件：AutoRefreshDocumentListener.java

@Component
public class AutoRefreshDocumentListener implements DocumentChangeListenerPort {
    
    @Override
    public void onDocumentUploaded(Long documentId) {
        taskQueue.send("document-refresh", 
            new DocumentRefreshTask(documentId), 
            2000L);  // 延迟 2 秒
    }
}
```

#### 5.3 集成到文档服务
```java
// 文件：KernelKnowledgeDocumentService.java

public DocumentUploadResponse upload(DocumentUploadCommand command) {
    Document document = documentRepository.save(...);
    
    // ✅ 触发自动刷新
    documentChangeListener.onDocumentUploaded(document.getId());
    
    return new DocumentUploadResponse(document.getId());
}
```

**配置**：
```yaml
seahorse-agent:
  knowledge:
    auto-refresh:
      enabled: true
      delay-millis: 2000
```

**验证方法**：
```bash
# 1. 上传文档
curl -X POST http://localhost:9090/knowledge-base/1/docs/upload \
  -F "file=@test.pdf"

# 响应：{"code":"0","data":{"documentId":123}}

# 2. 等待 3 秒

# 3. 执行检索，验证新文档可被检索到
curl -X GET "http://localhost:9090/rag/v3/chat?question=test文档内容"
```

**预计工时**：3 小时  
**优先级**：P1  
**负责人**：后端团队

---

## 💡 P2 优化建议（1 个月内完成）

### 6. 优化记忆召回策略

**需求**：引入 BM25 + Rerank 混合召回，提升准确率

**实现思路**：
1. 实现 BM25 检索器
2. 向量检索 + BM25 检索并行
3. RRF 融合
4. 可选 Rerank

**预计工时**：8 小时  
**优先级**：P2

---

### 7. 实现 Embedding 缓存

**需求**：相同文本不重复向量化，降低成本

**实现思路**：
1. 使用 Redis 存储 Embedding（SHA256 哈希为 key）
2. 在 AI 适配器中集成缓存逻辑
3. 设置 30 天 TTL

**预计工时**：4 小时  
**优先级**：P2

---

### 8. 集成 OpenTelemetry

**需求**：增强可观测性，统一 Trace/Metrics/Logs

**实现思路**：
1. 添加 OpenTelemetry Java Agent
2. 配置 OTLP Exporter
3. 集成 Jaeger/Grafana

**预计工时**：6 小时  
**优先级**：P2

---

## 📅 实施计划

### 第 1 周（P0）

| 日期 | 任务 | 工时 | 负责人 |
|------|------|------|--------|
| Day 1 | 修复 Billing 配置 | 1h | 张三 |
| Day 1 | 清理重复导入 | 0.5h | 张三 |

**本周小计**：1.5 小时

---

### 第 2-3 周（P1）

| 日期 | 任务 | 工时 | 负责人 |
|------|------|------|--------|
| Day 1-2 | 实现 Refresh Token | 4h | 李四 |
| Day 3-4 | 实现死信队列 | 6h | 王五 |
| Day 5 | 实现文档自动刷新 | 3h | 李四 |

**本周小计**：13 小时

---

### 第 4 周（P2）

| 日期 | 任务 | 工时 | 负责人 |
|------|------|------|--------|
| Day 1-2 | 记忆召回优化 | 8h | 赵六（算法） |
| Day 3 | Embedding 缓存 | 4h | 李四 |
| Day 4 | OpenTelemetry 集成 | 6h | 王五 |

**本周小计**：18 小时

---

## ✅ 验收标准

### P0（必须通过）
- [ ] 应用启动无 Bean 加载异常
- [ ] 计费功能正常工作
- [ ] 所有集成测试通过

### P1（80% 完成）
- [ ] Refresh Token 功能正常
- [ ] 死信队列管理 API 可用
- [ ] 文档上传后自动刷新

### P2（可选）
- [ ] 记忆召回准确率提升 10%
- [ ] Embedding 缓存命中率 > 50%
- [ ] OpenTelemetry Trace 可查看

---

## 📊 风险评估

| 风险 | 概率 | 影响 | 缓解措施 |
|------|------|------|---------|
| P0 修复引入新 Bug | 低 | 高 | 充分测试，灰度发布 |
| Refresh Token 安全性 | 中 | 高 | Token 轮转，审计日志 |
| 死信队列存储爆炸 | 中 | 中 | 设置 TTL，定期清理 |
| 文档自动刷新失败 | 低 | 中 | 失败重试，告警通知 |

---

**负责人**：架构组  
**审批人**：CTO  
**创建日期**：2026-06-06  
**文档版本**：v1.0

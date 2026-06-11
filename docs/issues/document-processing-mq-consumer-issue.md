# 文档处理MQ Consumer实现问题总结

**日期**: 2026-06-11  
**问题**: 文档上传后status保持running，chunks未生成  
**根因**: MQ consumer未实现  
**完成度**: 85% (代码已实现但未能成功启动)

---

## 问题分析

### 现有流程

```
1. 用户上传文档 → POST /knowledge-base/{id}/docs/upload
2. Backend保存文件到storage，创建t_knowledge_document记录
3. 触发分块 → POST /knowledge-base/docs/{id}/chunk
4. Backend调用KernelKnowledgeDocumentService.startChunk()
5. startChunk()发送KnowledgeDocumentChunkEvent到Pulsar
6. ❌ 没有consumer监听该topic
7. 文档status保持running
```

### 缺失环节

需要MQ consumer监听`persistent://seahorse-agent/ai/knowledge-document-chunk` topic，接收消息后调用`executeChunk()`方法。

---

## 实现尝试

### 创建的文件

`seahorse-agent-adapter-web/src/main/java/com/miracle/ai/seahorse/agent/adapters/web/KnowledgeDocumentChunkConsumer.java`

```java
@Component
public class KnowledgeDocumentChunkConsumer {
    
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // 查找MessageSubscriptionPort bean
        // 调用subscribe()方法监听topic
        // 接收消息调用documentService.executeChunk()
    }
}
```

### 遇到的问题

**Bean类型匹配问题**：

```
SeahorseAgentMqAdapterAutoConfiguration中:
- 创建了PulsarMessageQueueAdapter (实现MessageSubscriptionPort)
- 包装为ReliableMessageQueueAdapter (也实现MessageSubscriptionPort)  
- 但返回类型是ReliableMessageQueueAdapter，不是接口类型

Spring容器:
- ❌ 没有MessageSubscriptionPort类型的bean
- ✅ 有ReliableMessageQueueAdapter类型的bean

Consumer查找:
- ObjectProvider<MessageSubscriptionPort> → null
- applicationContext.getBeansOfType(MessageSubscriptionPort.class) → empty
- 无法启动consumer
```

---

## 尝试的解决方案

### 方案1: 添加MessageSubscriptionPort别名bean ❌

```java
@Bean
@ConditionalOnBean(ReliableMessageQueueAdapter.class)
public MessageSubscriptionPort seahorseMessageSubscriptionPort(
        ReliableMessageQueueAdapter adapter) {
    return adapter;
}
```

**结果**: Bean未创建（可能@ConditionalOnMissingBean(MessageQueuePort.class)阻止）

### 方案2: ApplicationContext手动查找 ❌

```java
var beans = applicationContext.getBeansOfType(MessageSubscriptionPort.class);
```

**结果**: 返回empty map

### 方案3: 注入ReliableMessageQueueAdapter ❌

**问题**: adapter-web包无法访问autoconfigure包中的类

---

## 根本原因

Spring Bean注册机制:
- Bean的类型由方法返回类型决定
- `public ReliableMessageQueueAdapter bean()` → 注册为具体类型
- 即使实现了接口，也不会自动注册接口类型的bean
- 需要显式创建接口类型的@Bean方法

当前配置冲突:
```java
@Bean
@ConditionalOnMissingBean(MessageQueuePort.class)  // ← 这里阻止了
public MessageSubscriptionPort subscription(...) { // ← 这个创建
    return adapter;
}
```

因为ReliableMessageQueueAdapter已经存在，`@ConditionalOnMissingBean(MessageQueuePort.class)`评估为false（虽然它查的是MessageQueuePort）。

---

## 正确的解决方案

### 方案A: 修改MQ adapter配置 (推荐)

```java
@Bean
@ConditionalOnBean(PulsarMessageQueueAdapter.class)
@ConditionalOnMissingBean(MessageQueuePort.class)
public MessageQueuePort seahorseMessageQueuePort(
        PulsarMessageQueueAdapter pulsarAdapter,
        ...) {
    return new ReliableMessageQueueAdapter(...);  // 返回接口类型
}

@Bean  
@ConditionalOnBean(MessageQueuePort.class)
@ConditionalOnMissingBean(MessageSubscriptionPort.class)
public MessageSubscriptionPort seahorseMessageSubscriptionPort(
        MessageQueuePort mqPort) {
    return (MessageSubscriptionPort) mqPort;  // 强制转换并注册接口类型
}
```

### 方案B: Consumer直接使用具体类型

将`seahorse-agent-adapter-web`依赖`seahorse-agent-spring-boot-autoconfigure`，直接注入：

```java
@Autowired(required = false)
private ReliableMessageQueueAdapter reliableAdapter;
```

**缺点**: 增加模块间依赖

### 方案C: 使用@Primary和多个@Bean

```java
@Bean
@Primary
public MessageQueuePort primary(...) { return adapter; }

@Bean("subscriptionPort")
public MessageSubscriptionPort subscription(MessageQueuePort port) {
    return (MessageSubscriptionPort) port;
}
```

---

## 当前状态

### 已完成 ✅
- 创建KnowledgeDocumentChunkConsumer类
- 实现subscribe逻辑（topic, subscription, handler）
- 添加ApplicationReadyEvent监听
- 错误处理和日志记录

### 未完成 ❌
- Bean查找失败，consumer未启动
- 文档处理仍然无法自动完成

### 日志证据

```
2026-06-11T14:53:00.604Z  WARN ... KnowledgeDocumentChunkConsumer : 
MessageSubscriptionPort not available, document chunk consumer not started
```

---

## 下一步行动

1. **立即修复**: 采用方案A修改`SeahorseAgentMqAdapterAutoConfiguration`
2. **验证**: 重新部署backend，检查consumer启动日志
3. **测试**: 上传文档，验证自动分块处理

---

## 技术收获

1. Spring Bean类型注册机制
2. @ConditionalOnBean的评估时机
3. ObjectProvider vs ApplicationContext.getBeansOfType
4. 接口类型bean需要显式注册

---

**状态**: 问题已定位，解决方案明确  
**预估修复时间**: 15分钟（修改配置 + 重新部署）  
**Token消耗**: ~100K (诊断过程)

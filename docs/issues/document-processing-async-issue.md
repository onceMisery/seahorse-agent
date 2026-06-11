# 文档处理异步问题排查

**日期**: 2026-06-11  
**问题**: 文档上传后status保持running，chunks未生成

## 现象
- 文档上传成功：ID 323450126558613504
- 触发chunk API返回成功
- 文档status: running（长时间不变）
- chunk_count: 0
- 无backend处理日志

## 排查结果

### 1. 配置验证
```bash
SEAHORSE_AGENT_ADAPTERS_MQ_TYPE=pulsar ✅
SEAHORSE_AGENT_ADAPTERS_MQ_PULSAR_SERVICE_URL=pulsar://pulsar-broker:6650 ✅
```

### 2. Pulsar服务
- Broker状态: healthy ✅
- Topic列表: 无knowledge相关topic ❌

### 3. Backend日志
- 无Pulsar consumer启动日志 ❌
- 无文档处理日志 ❌
- 无subscription记录 ❌

## 根因分析

**可能原因**:
1. MQ consumer未配置或未启动
2. 文档处理listener未注册
3. Outbox pattern的relay未生效

## 临时方案

使用已有的e2e测试知识库（ID: 99999）进行功能演示，该知识库已有完整的chunks和vectors。

## 待修复

需要检查：
- `SeahorseAgentMqAdapterAutoConfiguration`
- Document processing consumer配置
- Outbox relay配置

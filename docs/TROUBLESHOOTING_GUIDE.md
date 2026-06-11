# Seahorse Agent 故障排除指南

**目标**: 快速诊断和修复常见部署/运行时问题

---

## 1. 认证问题

### 问题1.1: "登录已过期，请重新登录"

**症状**:
```bash
$ curl -X GET /knowledge-base -H "Authorization: Bearer $TOKEN"
{"code":"1","message":"登录已过期，请重新登录"}
```

**根因**: sa-token使用内存存储，token在请求间不持久化

**诊断步骤**:
```bash
# 1. 检查Redis是否有token数据
docker exec seahorse-redis redis-cli DBSIZE
# 预期 > 0，实际 = 0 说明token未写入Redis

# 2. 检查Redis key前缀
docker exec seahorse-redis redis-cli KEYS "*"
# 正确: satoken:login:token:*
# 错误: Authorization:login:token:* 或为空

# 3. 检查sa-token Bean创建日志
docker logs seahorse-backend 2>&1 | grep -i "SaTokenDao"
# 应该看到: "创建SaTokenDaoForRedisTemplate"
# 如果没有，说明Bean未创建
```

**修复方案**:

**方案A: 确认依赖存在**
```bash
# 检查依赖树
./mvnw dependency:tree -pl seahorse-agent-bootstrap | grep sa-token-redis-template

# 如果不存在，在bootstrap/pom.xml添加：
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-template</artifactId>
</dependency>
```

**方案B: 确认Bean创建**
```java
// 在SeahorseAgentAuthAdapterAutoConfiguration中
// 确保@AutoConfigureAfter包含RedisAutoConfiguration
@AutoConfigureAfter({
    DataSourceAutoConfiguration.class,
    org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration.class
})
```

**方案C: 临时禁用认证（仅开发环境）**
```yaml
# application.yml
sa-token:
  check-login: false
```

---

## 2. 编译和打包问题

### 问题2.1: NoClassDefFoundError: SaTokenDaoForRedisTemplate

**症状**:
```
java.lang.NoClassDefFoundError: cn/dev33/satoken/dao/SaTokenDaoForRedisTemplate
```

**根因**: Maven optional依赖不传递，Spring Boot打包时未包含

**诊断步骤**:
```bash
# 检查JAR内容
jar -tf seahorse-agent-bootstrap/target/*-exec.jar | grep -i satoken
# 如果没有SaTokenDaoForRedisTemplate类，说明依赖未打包
```

**修复**: 在bootstrap/pom.xml中**显式声明**（已修复）
```xml
<dependency>
    <groupId>cn.dev33</groupId>
    <artifactId>sa-token-redis-template</artifactId>
</dependency>
```

---

### 问题2.2: 编译失败 - 找不到PaymentCallbackLogRepositoryPort等符号

**症状**:
```
[ERROR] symbol: class PaymentCallbackLogRepositoryPort
[ERROR] location: package com.miracle.ai.seahorse.agent.ports.outbound.billing
```

**根因**: kernel模块未先install，其他模块找不到接口定义

**修复**:
```bash
# 先install kernel
./mvnw install -pl seahorse-agent-kernel -am -DskipTests

# 再构建bootstrap
./mvnw package -pl seahorse-agent-bootstrap -am -DskipTests
```

---

### 问题2.3: Spotless格式检查失败

**症状**:
```
[ERROR] Run 'mvn spotless:apply' to fix these violations.
```

**快速修复**:
```bash
# 方案A: 自动修复格式
./mvnw spotless:apply

# 方案B: 跳过格式检查（快速构建）
./mvnw package -DskipTests -Dspotless.check.skip=true
```

---

## 3. Docker部署问题

### 问题3.1: Backend容器启动失败

**诊断步骤**:
```bash
# 1. 查看容器状态
docker compose -f docker-compose.full.yml ps

# 2. 查看最近日志
docker logs seahorse-backend --tail 50

# 3. 查看完整启动日志
docker logs seahorse-backend 2>&1 | less
```

**常见错误**:

**错误A: 数据库连接失败**
```
Unable to open JDBC Connection for DDL execution
```
**修复**:
```bash
# 检查PostgreSQL是否启动
docker compose -f docker-compose.full.yml ps postgres

# 检查数据库配置
docker logs seahorse-postgres | grep "database system is ready"
```

**错误B: Redis连接失败**
```
Unable to connect to Redis
```
**修复**:
```bash
# 检查Redis是否启动
docker compose -f docker-compose.full.yml ps redis

# 测试Redis连接
docker exec seahorse-redis redis-cli PING
```

---

### 问题3.2: Ollama模型拉取失败

**症状**:
```bash
$ docker exec seahorse-ollama ollama list
# 输出为空或没有nomic-embed-text
```

**根因**: 网络代理配置问题

**修复**:
```bash
# 方案A: 使用HTTP代理（如有）
docker exec seahorse-ollama bash -c "export http_proxy=http://host.docker.internal:7890 && ollama pull nomic-embed-text"

# 方案B: 宿主机拉取后复制
ollama pull nomic-embed-text
docker cp ~/.ollama/models seahorse-ollama:/root/.ollama/

# 验证
docker exec seahorse-ollama ollama list
```

---

### 问题3.3: Maven构建在Docker内失败

**症状**: docker-compose.full.yml中backend服务构建超时或失败

**根因**: Docker内Maven无法访问外网仓库

**修复**: 使用本地构建 + Docker打包（推荐）
```bash
# 1. 宿主机上构建JAR
./mvnw package -pl seahorse-agent-bootstrap -am -DskipTests -Dspotless.check.skip=true

# 2. 使用本地JAR构建镜像
docker build -t seahorse-agent-backend:latest -f - . <<'EOF'
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY seahorse-agent-bootstrap/target/seahorse-agent-bootstrap-*-exec.jar app.jar
EXPOSE 9090
ENTRYPOINT ["java", "-jar", "app.jar"]
EOF

# 3. 启动服务
docker compose -f docker-compose.full.yml up -d backend
```

---

## 4. 向量化问题

### 问题4.1: 向量维度不匹配

**症状**:
```sql
ERROR: dimension mismatch: expected 1024, got 768
```

**根因**: 数据库表定义为1024维，但nomic-embed-text生成768维

**诊断**:
```bash
# 检查当前表定义
docker exec seahorse-postgres psql -U postgres -d seahorse -c "
SELECT column_name, udt_name, character_maximum_length 
FROM information_schema.columns 
WHERE table_name='t_knowledge_vector' AND column_name='embedding';
"

# 检查Ollama模型维度
curl -X POST http://localhost:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"nomic-embed-text","prompt":"test"}' | jq '.embedding | length'
```

**修复**:
```sql
-- 修改向量维度为768
ALTER TABLE t_knowledge_vector ALTER COLUMN embedding TYPE vector(768);
```

---

### 问题4.2: Ollama API调用超时

**症状**:
```
java.net.SocketTimeoutException: Read timed out
```

**诊断**:
```bash
# 测试Ollama响应时间
time curl -X POST http://localhost:11434/api/embeddings \
  -H "Content-Type: application/json" \
  -d '{"model":"nomic-embed-text","prompt":"测试文本"}'
```

**修复**:
```yaml
# application.yml - 增加超时时间
seahorse:
  agent:
    adapters:
      ai:
        openai-compatible:
          timeout: 60000  # 60秒
```

---

## 5. RAG查询问题

### 问题5.1: 查询无返回结果

**诊断步骤**:
```sql
-- 1. 检查知识库是否有数据
SELECT COUNT(*) FROM t_knowledge_chunk;

-- 2. 检查向量是否已生成
SELECT COUNT(*) FROM t_knowledge_vector WHERE embedding IS NOT NULL;

-- 3. 检查文档状态
SELECT doc_name, status FROM t_knowledge_document WHERE status='processing';
```

**修复**: 手动触发向量化
```bash
# 重新触发文档处理
curl -X POST http://localhost:9090/knowledge-base/{kb_id}/documents/{doc_id}/reindex \
  -H "Authorization: Bearer $TOKEN"
```

---

### 问题5.2: 语义检索不准确

**可能原因**:
1. chunk size过大或过小
2. 检索top_k设置不当
3. 向量模型不匹配内容语言

**优化建议**:
```yaml
seahorse:
  agent:
    knowledge:
      chunk-size: 500        # 调整为500字符
      chunk-overlap: 50      # 10%重叠
      retrieval-top-k: 5     # 检索前5个相关块
```

---

## 6. 多租户和权限问题

### 问题6.1: RLS策略阻止数据访问

**症状**:
```sql
ERROR: new row violates row-level security policy for table "t_knowledge_base"
```

**诊断**:
```sql
-- 检查当前租户ID
SELECT current_setting('app.current_tenant_id', true);

-- 检查RLS策略
SELECT schemaname, tablename, policyname, permissive 
FROM pg_policies 
WHERE tablename='t_knowledge_base';
```

**修复**:
```sql
-- 设置租户上下文
SET app.current_tenant_id = 1;

-- 或在应用层确保TenantContext正确设置
```

---

## 7. 性能问题

### 问题7.1: 向量检索慢

**诊断**:
```sql
-- 检查索引
SELECT indexname, indexdef 
FROM pg_indexes 
WHERE tablename='t_knowledge_vector';

-- 检查查询计划
EXPLAIN ANALYZE 
SELECT * FROM t_knowledge_vector 
ORDER BY embedding <-> '[0.1, 0.2, ...]'::vector 
LIMIT 10;
```

**优化**: 创建IVFFlat索引
```sql
CREATE INDEX IF NOT EXISTS idx_knowledge_vector_embedding 
ON t_knowledge_vector 
USING ivfflat (embedding vector_cosine_ops) 
WITH (lists = 100);
```

---

## 8. 日志分析

### 获取关键日志

```bash
# Backend启动日志
docker logs seahorse-backend 2>&1 | grep "Started SeahorseAgentApplication"

# sa-token初始化
docker logs seahorse-backend 2>&1 | grep -i "satoken"

# Redis连接
docker logs seahorse-backend 2>&1 | grep -i "redis"

# 错误日志
docker logs seahorse-backend 2>&1 | grep -i "error\|exception" | tail -50

# 最近5分钟日志
docker logs seahorse-backend --since 5m
```

---

## 9. 紧急恢复

### 完全重置开发环境

```bash
# 1. 停止所有容器
docker compose -f docker-compose.full.yml down -v

# 2. 清理所有数据卷
docker volume prune -f

# 3. 重新构建
./mvnw clean package -DskipTests -Dspotless.check.skip=true

# 4. 重新启动
docker compose -f docker-compose.full.yml up -d --build

# 5. 验证
bash scripts/e2e-full-test.sh
```

---

## 10. 获取帮助

### 收集诊断信息

```bash
#!/bin/bash
# 生成完整诊断报告

echo "=== Docker状态 ===" > diagnostic.log
docker compose -f docker-compose.full.yml ps >> diagnostic.log

echo -e "\n=== Backend日志 ===" >> diagnostic.log
docker logs seahorse-backend --tail 100 >> diagnostic.log 2>&1

echo -e "\n=== Redis状态 ===" >> diagnostic.log
docker exec seahorse-redis redis-cli INFO >> diagnostic.log

echo -e "\n=== 数据库状态 ===" >> diagnostic.log
docker exec seahorse-postgres psql -U postgres -d seahorse -c "
SELECT 'knowledge_base' as table, COUNT(*) as count FROM t_knowledge_base
UNION ALL
SELECT 'documents', COUNT(*) FROM t_knowledge_document
UNION ALL
SELECT 'chunks', COUNT(*) FROM t_knowledge_chunk
UNION ALL
SELECT 'vectors', COUNT(*) FROM t_knowledge_vector;
" >> diagnostic.log 2>&1

echo -e "\n=== Ollama模型 ===" >> diagnostic.log
docker exec seahorse-ollama ollama list >> diagnostic.log 2>&1

cat diagnostic.log
```

提交issue时附上 `diagnostic.log` 文件。

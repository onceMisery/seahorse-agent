# 块H · 后端性能优化 — 生产级性能调优方案

> 文档定位：SaaS MVP 执行计划第 16 篇。功能增强系列之「后端性能」。  
> 关键属性：**P1 优先级、SaaS 性能关键、独立可实施**。  
> 编写依据：2026-06-05 性能测试 + 生产调优最佳实践。  
> 工作量口径：1 人 × 3 天。

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ N+1 查询（查询会话列表时，每条会话再查一次知识库）
- ❌ 无缓存策略（高频配置数据每次查库）
- ❌ 同步处理慢任务（文档解析阻塞接口响应）
- ❌ 逐条插入（批量导入 1000 条数据耗时 30 秒）
- ❌ 连接池配置默认（高并发时连接不足）
- ❌ JVM 参数默认（GC 频繁导致 STW）

**性能瓶颈**：
- 🐢 查询会话列表：3 秒（N+1 查询导致）
- 🐢 首页加载：2 秒（无缓存，每次查库）
- 🐢 上传文档：30 秒（同步解析，阻塞响应）
- 🐢 批量导入：1000 条 30 秒（逐条插入）

**本方案价值**：
- ✅ SQL 优化（N+1 → JOIN，查询时间 3s → 300ms）
- ✅ 多级缓存（本地 + Redis，首页加载 2s → 200ms）
- ✅ 异步处理（文档解析后台任务，接口响应 30s → 1s）
- ✅ 批量操作（批量插入 1000 条 30s → 3s）
- ✅ 连接池调优（并发 100 → 500）
- ✅ JVM 调优（GC 频率降低 50%）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 | 优化后 |
|------|------|--------|------|--------|
| G1 | SQL 优化（N+1、索引、分页） | **P0** | 慢查询 ⚠️ | < 500ms |
| G2 | 多级缓存（本地 + Redis） | **P0** | 无缓存 ❌ | 缓存命中 > 80% |
| G3 | 异步处理（消息队列） | **P0** | 同步阻塞 ⚠️ | 响应 < 1s |
| G4 | 批量操作（批量插入/更新） | P1 | 逐条操作 ⚠️ | 性能提升 10x |
| G5 | 连接池调优（HikariCP/Redisson） | P1 | 默认配置 ⚠️ | 并发 5x |
| G6 | JVM 调优（GC 参数、堆内存） | P1 | 默认配置 ⚠️ | GC 频率 -50% |

### 1.2 明确不做（后延）

- **不做** 分库分表（Sharding）— 数据量 < 1000 万时不需要
- **不做** 读写分离 — 单库 TPS < 5000 时不需要
- **不做** CDN 静态资源加速 — 前端优化方案（12-frontend-performance）
- **不做** ElasticSearch 性能调优 — 数据量 < 100 万时够用

### 1.3 验收信号

#### P0 验收

1. ✅ 查询会话列表（100 条）：响应时间 < 500ms（原 3s）
2. ✅ 首页配置加载：响应时间 < 200ms（原 2s）
3. ✅ 上传文档接口：响应时间 < 1s（解析任务异步）
4. ✅ 慢查询日志：无 > 1s 的 SQL

#### P1 验收

5. ⚠️ 批量导入 1000 条：耗时 < 5s（原 30s）
6. ⚠️ 并发压测：500 并发 TPS > 1000

---

## 2. 现状（性能测试分析）

### 2.1 慢查询 Top 5

| SQL | 执行时间 | 问题 |
|-----|---------|------|
| 查询会话列表 | 3.2s | N+1 查询（100 条会话 → 100 次查询知识库） |
| 查询文档列表 | 1.8s | 无索引（WHERE tenant_id 无索引） |
| 统计配额使用 | 1.5s | 全表扫描（SUM 无索引） |
| 查询聊天记录 | 1.2s | OFFSET 过大（分页 10000 条） |
| 查询技能列表 | 0.9s | 无索引（WHERE tenant_id + status） |

### 2.2 缓存现状

**已有缓存**：
- ✅ `CachePort` 接口已定义
- ✅ Redis 适配器已实现

**问题**：
- ❌ 业务代码未使用缓存（直接查库）
- ❌ 无本地缓存（高频数据仍走 Redis 网络 IO）

### 2.3 异步处理现状

**当前同步处理**：
```java
@PostMapping("/api/documents/upload")
public DocumentResponse upload(@RequestParam MultipartFile file) {
    // 1. 上传文件（1s）
    String url = storagePort.upload(file);
    
    // 2. 解析文档（20s）⚠️ 阻塞
    ParseResult result = documentParser.parse(file);
    
    // 3. 向量化（5s）⚠️ 阻塞
    vectorPort.embed(result.getChunks());
    
    return DocumentResponse.from(result);
}
```

**响应时间**：26s（用户等待过久）

---

## 3. 技术方案

### 3.1 SQL 优化（P0）

#### 3.1.1 解决 N+1 查询

**问题 SQL**：
```java
// 查询会话列表
List<Session> sessions = sessionRepository.findByTenantId(tenantId);

// N+1：每条会话查一次知识库
sessions.forEach(session -> {
    session.setKnowledgeBase(
        kbRepository.findById(session.getKbId()).orElse(null)
    );
});
```

**优化方案 1：JOIN**：
```java
@Query("""
    SELECT s FROM Session s 
    LEFT JOIN FETCH s.knowledgeBase 
    WHERE s.tenantId = :tenantId
    ORDER BY s.createdAt DESC
""")
List<Session> findByTenantIdWithKb(@Param("tenantId") String tenantId);
```

**优化方案 2：批量查询**：
```java
List<Session> sessions = sessionRepository.findByTenantId(tenantId);

// 批量查询知识库（1 次查询）
Set<Long> kbIds = sessions.stream()
    .map(Session::getKbId)
    .collect(Collectors.toSet());

Map<Long, KnowledgeBase> kbMap = kbRepository.findAllById(kbIds).stream()
    .collect(Collectors.toMap(KnowledgeBase::getId, kb -> kb));

// 填充
sessions.forEach(session -> 
    session.setKnowledgeBase(kbMap.get(session.getKbId()))
);
```

**效果**：3.2s → 0.3s（-91%）

#### 3.1.2 索引优化

**审查现有索引**：
```sql
-- 查看表索引
SELECT tablename, indexname, indexdef 
FROM pg_indexes 
WHERE schemaname = 'public';

-- 查看慢查询
SELECT query, calls, mean_exec_time, max_exec_time
FROM pg_stat_statements
WHERE mean_exec_time > 1000  -- > 1s
ORDER BY mean_exec_time DESC
LIMIT 10;
```

**新增索引**：
```sql
-- 租户 + 时间（查询会话列表）
-- 注意：表名需与 seahorse_init.sql 实际表名对齐，此处为示例
CREATE INDEX idx_session_tenant_time ON t_conversation (tenant_id, created_at DESC);

-- 租户 + 状态（查询 Agent 列表）
-- status 字段建议使用 SMALLINT，参见 15-data-consistency DDL 审查规范
CREATE INDEX idx_agent_tenant_status ON sa_agent_definition (tenant_id, status);

-- 复合索引（统计配额使用）
CREATE INDEX idx_usage_tenant_time ON sa_cost_usage_record (tenant_id, usage_time);

-- 文档列表
CREATE INDEX idx_doc_tenant_kb ON t_knowledge_document (tenant_id, kb_id, created_at DESC);
```

> **索引审查要点**：
> - ✅ 索引 DDL 使用 PostgreSQL `CREATE INDEX` 语法
> - ✅ `tenant_id` 作为复合索引最左前缀，与 RLS 策略对齐
> - ✅ 状态/枚举字段应使用 `SMALLINT`（参见 15-data-consistency DDL 规范）
> - ⚠️ 实施前需核实 `seahorse_init.sql` 中的实际表名（如 `t_conversation` vs `t_chat_session`）

**索引原则**：
- ✅ 高频 WHERE 条件
- ✅ ORDER BY 字段
- ✅ JOIN 关联字段
- ❌ 低基数字段（如 status 只有 2 个值）
- ❌ 频繁更新的字段

#### 3.1.3 分页优化

**问题 SQL**（OFFSET 过大）：
```sql
SELECT * FROM t_chat_message
WHERE session_id = ?
ORDER BY created_at DESC
OFFSET 10000 LIMIT 20;  -- ⚠️ 需扫描 10020 行
```

**优化方案：游标分页**：
```sql
-- 首次查询
SELECT * FROM t_chat_message
WHERE session_id = ?
ORDER BY created_at DESC
LIMIT 20;

-- 下一页（基于上一页最后一条的 created_at）
SELECT * FROM t_chat_message
WHERE session_id = ? AND created_at < ?
ORDER BY created_at DESC
LIMIT 20;
```

**Repository 方法**：
```java
@Query("""
    SELECT m FROM ChatMessage m 
    WHERE m.sessionId = :sessionId 
    AND (:cursor IS NULL OR m.createdAt < :cursor)
    ORDER BY m.createdAt DESC
""")
List<ChatMessage> findBySessionIdWithCursor(
    @Param("sessionId") Long sessionId,
    @Param("cursor") Instant cursor,
    Limit limit
);
```

**效果**：分页 10000 条 1.2s → 0.05s（-96%）

---

### 3.2 多级缓存（P0）

#### 3.2.1 缓存架构

```
请求 → 本地缓存（Caffeine）→ Redis → 数据库
       ↑ 命中率 80%        ↑ 15%    ↑ 5%
       响应 < 1ms         < 10ms   < 100ms
```

#### 3.2.2 本地缓存（Caffeine）

**依赖**：
```xml
<dependency>
    <groupId>com.github.ben-manes.caffeine</groupId>
    <artifactId>caffeine</artifactId>
</dependency>
```

**配置**：
```java
@Configuration
public class CaffeineConfiguration {
    
    @Bean
    public Cache<String, Object> localCache() {
        return Caffeine.newBuilder()
            .maximumSize(10_000)           // 最多 10000 条
            .expireAfterWrite(5, TimeUnit.MINUTES)  // 5 分钟过期
            .recordStats()                  // 记录统计信息
            .build();
    }
}
```

**使用**：
```java
@Service
public class TenantConfigService {
    
    @Autowired
    private Cache<String, TenantConfig> localCache;
    
    @Autowired
    private RedissonClient redis;
    
    @Autowired
    private TenantConfigRepository repository;
    
    public TenantConfig getConfig(String tenantId) {
        // L1：本地缓存
        TenantConfig config = localCache.getIfPresent("tenant:" + tenantId);
        if (config != null) {
            return config;
        }
        
        // L2：Redis
        RBucket<TenantConfig> bucket = redis.getBucket("tenant:" + tenantId);
        config = bucket.get();
        if (config != null) {
            localCache.put("tenant:" + tenantId, config);
            return config;
        }
        
        // L3：数据库
        config = repository.findByTenantId(tenantId).orElseThrow();
        
        // 回写缓存
        bucket.set(config, 1, TimeUnit.HOURS);
        localCache.put("tenant:" + tenantId, config);
        
        return config;
    }
}
```

#### 3.2.3 缓存失效策略

**主动失效**（更新时清除）：
```java
@Transactional
public void updateConfig(String tenantId, TenantConfig config) {
    repository.save(config);
    
    // 清除本地缓存
    localCache.invalidate("tenant:" + tenantId);
    
    // 清除 Redis
    redis.getBucket("tenant:" + tenantId).delete();
    
    // 或：发布失效消息（多实例同步）
    redis.getTopic("cache:invalidate").publish("tenant:" + tenantId);
}
```

**多实例同步**（Redis Pub/Sub）：
```java
@PostConstruct
public void subscribeCacheInvalidation() {
    RTopic topic = redis.getTopic("cache:invalidate");
    
    topic.addListener(String.class, (channel, key) -> {
        localCache.invalidate(key);
        log.info("Local cache invalidated: {}", key);
    });
}
```

#### 3.2.4 慢 SQL 自动检测

> **问题**：手动查 `pg_stat_statements` 效率低，需要开发阶段自动捕获慢 SQL。

**方案：datasource-proxy 集成**（推荐，比 p6spy 更轻量）：

```xml
<dependency>
    <groupId>net.ttddyy</groupId>
    <artifactId>datasource-proxy</artifactId>
    <version>1.10</version>
</dependency>
```

```java
@Configuration
public class SlowQueryDetector {
    
    private static final long SLOW_QUERY_THRESHOLD_MS = 500;
    
    @Bean
    public BeanPostProcessor dataSourceProxyPostProcessor() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(Object bean, String beanName) {
                if (bean instanceof DataSource ds) {
                    return ProxyDataSourceBuilder.create(ds)
                        .name("SlowQueryDetector")
                        .listener(new SlowQueryListener(SLOW_QUERY_THRESHOLD_MS))
                        .build();
                }
                return bean;
            }
        };
    }
}

public class SlowQueryListener implements QueryExecutionListener {
    
    private final long thresholdMs;
    
    @Override
    public void afterQuery(ExecutionInfo execInfo, List<QueryInfo> queryInfoList) {
        long elapsed = execInfo.getElapsedTime();
        if (elapsed > thresholdMs) {
            String sql = queryInfoList.stream()
                .map(QueryInfo::getQuery)
                .collect(Collectors.joining("; "));
            log.warn("⚠️ Slow SQL detected: {}ms | SQL: {}", elapsed, sql);
            // TODO: 生产环境接入 Metrics 系统（Micrometer Timer），设置告警
        }
    }
}
```

> **生产建议**：
> - MVP 阶段使用 `datasource-proxy` 即可，无需引入 p6spy 全量代理
> - 生产环境应结合 `pg_stat_statements` 扩展定期审查（每天 cron job 输出 Top 10 慢查询）
> - 慢 SQL 阈值建议 500ms（与 P0 验收标准 < 500ms 对齐）

#### 3.2.5 缓存预热策略

> **问题**：冷启动时缓存为空，命中率远低于预期的 80%，导致数据库瞬间压力增大。

```java
@Component
public class CacheWarmupJob {
    
    @Autowired
    private TenantConfigRepository tenantConfigRepository;
    
    @Autowired
    private Cache<String, TenantConfig> localCache;
    
    @Autowired
    private RedissonClient redis;
    
    @EventListener(ApplicationReadyEvent.class)
    public void warmupCache() {
        log.info("Starting cache warmup...");
        
        // 1. 预热活跃租户配置（最近 7 天有活动的租户）
        List<TenantConfig> activeTenants = tenantConfigRepository
            .findActiveTenants(Instant.now().minus(7, ChronoUnit.DAYS));
        
        for (TenantConfig config : activeTenants) {
            String key = "tenant:" + config.getTenantId();
            localCache.put(key, config);
            redis.getBucket(key).set(config, 1, TimeUnit.HOURS);
        }
        
        log.info("Cache warmup completed: {} tenants loaded", activeTenants.size());
    }
}
```

---

### 3.3 异步处理（P0）

#### 3.3.1 文档上传异步化

**优化后**：
```java
@PostMapping("/api/documents/upload")
public DocumentResponse upload(@RequestParam MultipartFile file) {
    // 1. 上传文件（1s）
    String url = storagePort.upload(file);
    
    // 2. 创建文档记录（状态：PROCESSING）
    Document doc = documentRepository.save(Document.builder()
        .url(url)
        .status(DocumentStatus.PROCESSING)
        .build());
    
    // 3. 异步解析 + 向量化（消息队列）
    messageQueue.publish("document.parse", DocumentParseTask.of(doc.getId()));
    
    // 4. 立即返回
    return DocumentResponse.from(doc);  // 响应时间：1s
}
```

**后台任务**：
```java
@MessageListener(topic = "document.parse")
public void parseDocument(DocumentParseTask task) {
    try {
        // 1. 解析文档（20s）
        ParseResult result = documentParser.parse(task.getDocumentId());
        
        // 2. 向量化（5s）
        vectorPort.embed(result.getChunks());
        
        // 3. 更新状态
        documentRepository.updateStatus(task.getDocumentId(), DocumentStatus.SUCCESS);
        
    } catch (Exception ex) {
        documentRepository.updateStatus(task.getDocumentId(), DocumentStatus.FAILED);
        log.error("Document parse failed", ex);
    }
}
```

**前端轮询状态**：
```typescript
const pollStatus = async (docId: number) => {
  let delay = 5000;  // 初始间隔 5s（避免过于频繁请求）
  const maxDelay = 30000;  // 最大间隔 30s
  
  const poll = async () => {
    const doc = await api.getDocument(docId);
    
    if (doc.status === 'SUCCESS') {
      message.success('文档解析完成');
      return;
    } else if (doc.status === 'FAILED') {
      message.error('文档解析失败');
      return;
    }
    
    // 指数退避：5s → 10s → 20s → 30s（上限）
    delay = Math.min(delay * 2, maxDelay);
    setTimeout(poll, delay);
  };
  
  setTimeout(poll, delay);
};
```

**效果**：接口响应 26s → 1s（-96%）

---

### 3.4 批量操作（P1）

#### 3.4.1 批量插入

**问题代码**（逐条插入）：
```java
for (User user : users) {
    userRepository.save(user);  // 1000 次 INSERT
}
```

**优化方案 1：JDBC Batch**：
```java
@Transactional
public void batchInsert(List<User> users) {
    int batchSize = 100;
    
    for (int i = 0; i < users.size(); i++) {
        userRepository.save(users.get(i));
        
        if (i % batchSize == 0) {
            entityManager.flush();
            entityManager.clear();
        }
    }
}
```

**配置**：
```properties
spring.jpa.properties.hibernate.jdbc.batch_size=100
spring.jpa.properties.hibernate.order_inserts=true
```

**优化方案 2：JdbcTemplate 批量插入**：
```java
@Autowired
private JdbcTemplate jdbcTemplate;

@Transactional
public void batchInsertViaJdbc(List<User> users) {
    String sql = "INSERT INTO t_user (username, email, tenant_id) VALUES (?, ?, ?)";
    
    jdbcTemplate.batchUpdate(sql, new BatchPreparedStatementSetter() {
        @Override
        public void setValues(PreparedStatement ps, int i) throws SQLException {
            User user = users.get(i);
            ps.setString(1, user.getUsername());
            ps.setString(2, user.getEmail());
            ps.setString(3, user.getTenantId());
        }
        @Override
        public int getBatchSize() { return users.size(); }
    });
}
```

> **为什么不用 SpEL `@Query` 原生 SQL**：Spring Data JPA 的 SpEL 不支持动态列表展开（`VALUES` 行数固定），
> 无法适应任意长度的 `List<User>`。JdbcTemplate `batchUpdate` 是更可靠的方案。

**效果**：1000 条 30s → 3s（-90%）

---

### 3.5 连接池调优（P1）

#### 3.5.1 HikariCP 配置

```properties
# 核心配置
spring.datasource.hikari.maximum-pool-size=20      # ✅ 推荐公式: (CPU核数 * 2) + 磁盘数（8核=18）
spring.datasource.hikari.minimum-idle=5             # 最小空闲连接
spring.datasource.hikari.connection-timeout=10000   # ✅ 连接超时 10s（原 30s 过长）
spring.datasource.hikari.idle-timeout=600000        # 空闲超时 10min
spring.datasource.hikari.max-lifetime=1800000       # 最大生命周期 30min

# 性能优化
spring.datasource.hikari.leak-detection-threshold=60000  # 连接泄漏检测 60s
spring.datasource.hikari.pool-name=SeahorseHikariPool
```

> **HikariCP 连接数调优说明**：
> - 官方推荐公式：`connections = (CPU cores * 2) + disk count`（8 核 + 1 SSD = 17-20）
> - PostgreSQL 默认 `max_connections=100`，50 连接池仅支持 2 个实例，不利于水平扩展
> - **推荐配置 `max-pool-size=20`**，配合 PgBouncer 连接池中间件实现连接复用
> - 多实例部署时总连接数 = `pool_size × instance_count`（20 × 4 实例 = 80，留 20 余量）

#### 3.5.2 Redisson 配置

```yaml
redisson:
  config: |
    singleServerConfig:
      address: "redis://localhost:6379"
      connectionPoolSize: 64        # 连接池大小（默认 32）
      connectionMinimumIdleSize: 16 # 最小空闲
      timeout: 3000                 # 超时 3s
```

---

### 3.6 JVM 调优（P1）

#### 3.6.1 堆内存配置

```bash
# 启动参数
java -jar app.jar \
  -Xms2g -Xmx2g \              # 堆内存 2GB（初始=最大，避免动态扩容）
  -XX:MetaspaceSize=256m \      # 元空间
  -XX:MaxMetaspaceSize=512m \
  -XX:+UseG1GC \                # G1 垃圾回收器（推荐）
  -XX:MaxGCPauseMillis=200 \    # GC 暂停目标 200ms
  -XX:+HeapDumpOnOutOfMemoryError \  # OOM 时 dump
  -XX:HeapDumpPath=/tmp/heapdump.hprof
```

#### 3.6.2 GC 日志

```bash
-Xlog:gc*:file=/tmp/gc.log:time,uptime,level,tags
```

**分析 GC 日志**：
```bash
# 使用 GCEasy
https://gceasy.io/
```

---

## 4. 实施步骤

### Day 1：SQL 优化 + 索引 + 慢 SQL 检测
- 上午：解决 N+1 查询（2h）
- 下午：新增索引 + 分页优化 + datasource-proxy 集成（3h）

### Day 2：缓存 + 预热 + 异步
- 上午：多级缓存 + 缓存预热（3h）
- 下午：文档上传异步化 + 轮询优化（2h）

### Day 3：连接池 + JVM + 批量
- 上午：批量操作（JdbcTemplate）+ 连接池（2h）
- 下午：JVM 调优 + 压测（3h）

---

## 5. 验收标准

✅ P0 验收全部通过（见 §1.3）
✅ 压测：500 并发 TPS > 1000
✅ 缓存命中率 > 80%

---

**文档版本**：v1.1-review  
**最后更新**：2026-06-06

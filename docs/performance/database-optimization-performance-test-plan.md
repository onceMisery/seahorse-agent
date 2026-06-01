# 数据库主键优化 - 性能验证测试方案

## 1. 测试环境准备

### 1.1 数据库准备
```bash
# 创建测试数据库
psql -U postgres -c "CREATE DATABASE seahorse_test;"

# 执行优化后的 SQL 脚本
psql -U postgres -d seahorse_test -f ../seahorse_init_optimized.sql
```

### 1.2 测试数据生成
```sql
-- 生成测试数据的存储过程
CREATE OR REPLACE FUNCTION generate_test_data()
RETURNS void AS $$
DECLARE
    i INTEGER;
BEGIN
    -- 生成 10000 条用户数据
    FOR i IN 1..10000 LOOP
        INSERT INTO t_user (id, username, password, role, avatar)
        VALUES (
            (EXTRACT(EPOCH FROM now()) * 1000000 + i)::BIGINT,
            'user_' || i,
            'password_' || i,
            CASE WHEN i % 10 = 0 THEN 'admin' ELSE 'user' END,
            'https://avatar.example.com/' || i || '.png'
        );
    END LOOP;

    -- 生成 100000 条会话数据
    FOR i IN 1..100000 LOOP
        INSERT INTO t_conversation (id, conversation_id, user_id, title)
        VALUES (
            (EXTRACT(EPOCH FROM now()) * 1000000 + i)::BIGINT,
            (EXTRACT(EPOCH FROM now()) * 1000000 + i)::BIGINT,
            (EXTRACT(EPOCH FROM now()) * 1000000 + (i % 10000) + 1)::BIGINT,
            'Conversation ' || i
        );
    END LOOP;

    -- 生成 1000000 条消息数据
    FOR i IN 1..1000000 LOOP
        INSERT INTO t_message (id, conversation_id, user_id, role, content)
        VALUES (
            (EXTRACT(EPOCH FROM now()) * 1000000 + i)::BIGINT,
            (EXTRACT(EPOCH FROM now()) * 1000000 + (i % 100000) + 1)::BIGINT,
            (EXTRACT(EPOCH FROM now()) * 1000000 + (i % 10000) + 1)::BIGINT,
            CASE WHEN i % 2 = 0 THEN 'user' ELSE 'assistant' END,
            'Message content ' || i
        );
    END LOOP;
END;
$$ LANGUAGE plpgsql;

-- 执行测试数据生成
SELECT generate_test_data();
```

## 2. 性能测试脚本

### 2.1 主键查询性能测试
```sql
-- \d:\performance\test\primary_key_query_test.sql

-- 测试 1：主键等值查询
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM t_message
WHERE id = 1234567890123456;

-- 测试 2：主键范围查询
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM t_message
WHERE id BETWEEN 1234567890123456 AND 1234567890123460;

-- 测试 3：主键分页查询
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT * FROM t_message
WHERE id > 1234567890123456
ORDER BY id
LIMIT 100;
```

### 2.2 外键关联查询性能测试
```sql
-- \d:\performance\test\foreign_key_join_test.sql

-- 测试 1：基于外键的关联查询
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT m.*, c.title as conversation_title
FROM t_message m
JOIN t_conversation c ON m.conversation_id = c.id
WHERE m.user_id = 1234567890123456
ORDER BY m.create_time DESC
LIMIT 100;

-- 测试 2：多表关联查询
EXPLAIN (ANALYZE, BUFFERS, FORMAT TEXT)
SELECT m.id, m.content, u.username, c.title
FROM t_message m
JOIN t_user u ON m.user_id = u.id
JOIN t_conversation c ON m.conversation_id = c.id
WHERE m.id BETWEEN 1234567890123456 AND 1234567890123460;
```

### 2.3 索引性能测试
```sql
-- \d:\performance\test\index_performance_test.sql

-- 测试索引大小
SELECT
    schemaname,
    tablename,
    indexname,
    pg_size_pretty(pg_relation_size(indexrelid)) AS index_size,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY pg_relation_size(indexrelid) DESC;

-- 测试索引使用情况
SELECT
    indexrelname,
    seq_scan,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid))
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;
```

## 3. 应用层性能测试

### 3.1 JMeter 测试脚本配置
```xml
<!-- \d:\performance\test\api_performance_test.jmx -->
<jmeterTestPlan>
    <hashTree>
        <TestPlan>
            <stringProp name="TestPlan.name">Seahorse Agent API Performance Test</stringProp>
            <boolProp name="TestPlan.functional_mode">false</boolProp>
        </TestPlan>
        <hashTree>
            <ThreadGroup>
                <stringProp name="ThreadGroup.num_threads">100</stringProp>
                <stringProp name="ThreadGroup.ramp_time">10</stringProp>
                <stringProp name="ThreadGroup.duration">300</stringProp>
            </ThreadGroup>
            <hashTree>
                <HTTPSamplerProxy>
                    <stringProp name="HTTPSampler.domain">localhost</stringProp>
                    <stringProp name="HTTPSampler.port">8080</stringProp>
                    <stringProp name="HTTPSampler.path">/api/v1/messages</stringProp>
                    <stringProp name="HTTPSampler.method">POST</stringProp>
                </HTTPSamplerProxy>
            </hashTree>
        </hashTree>
    </hashTree>
</jmeterTestPlan>
```

### 3.2 Java 性能基准测试
```java
// \d:\performance\test\IdGenerationBenchmark.java

import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.options.OptionsBuilder;

@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.SECONDS)
@State(Scope.Thread)
public class IdGenerationBenchmark {

    @Benchmark
    public long snowflakeNextId() {
        return SnowflakeIds.nextId();
    }

    @Benchmark
    public String snowflakeNextIdString() {
        return SnowflakeIds.nextIdString();
    }

    public static void main(String[] args) throws Exception {
        new Runner(new OptionsBuilder()
            .include(IdGenerationBenchmark.class.getSimpleName())
            .forks(1)
            .build()).run();
    }
}
```

### 3.3 数据库写入性能测试
```java
// \d:\performance\test\BatchInsertBenchmark.java

public class BatchInsertBenchmark {

    public void testVarcharBatchInsert(Connection conn) throws SQLException {
        String sql = "INSERT INTO t_message (id, conversation_id, user_id, role, content) " +
                     "VALUES (?, ?, ?, ?, ?)";

        long start = System.currentTimeMillis();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (int i = 0; i < 100000; i++) {
                stmt.setString(1, String.valueOf(System.currentTimeMillis() * 1000000 + i));
                stmt.setString(2, String.valueOf(System.currentTimeMillis() * 1000000 + i % 10000));
                stmt.setString(3, String.valueOf(System.currentTimeMillis() * 1000000 + i % 1000));
                stmt.setString(4, "user");
                stmt.setString(5, "Content " + i);
                stmt.addBatch();

                if (i % 1000 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
            conn.commit();
        }

        long duration = System.currentTimeMillis() - start;
        System.out.println("VARCHAR Batch Insert: " + duration + " ms");
    }

    public void testBigintBatchInsert(Connection conn) throws SQLException {
        String sql = "INSERT INTO t_message (id, conversation_id, user_id, role, content) " +
                     "VALUES (?, ?, ?, ?, ?)";

        long start = System.currentTimeMillis();

        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            conn.setAutoCommit(false);
            for (int i = 0; i < 100000; i++) {
                stmt.setLong(1, System.currentTimeMillis() * 1000000 + i);
                stmt.setLong(2, System.currentTimeMillis() * 1000000 + i % 10000);
                stmt.setLong(3, System.currentTimeMillis() * 1000000 + i % 1000);
                stmt.setString(4, "user");
                stmt.setString(5, "Content " + i);
                stmt.addBatch();

                if (i % 1000 == 0) {
                    stmt.executeBatch();
                }
            }
            stmt.executeBatch();
            conn.commit();
        }

        long duration = System.currentTimeMillis() - start;
        System.out.println("BIGINT Batch Insert: " + duration + " ms");
    }
}
```

## 4. 性能指标收集

### 4.1 PostgreSQL 性能指标查询
```sql
-- \d:\performance\test\collect_metrics.sql

-- 数据库整体性能指标
SELECT
    pg_stat_database.datname,
    pg_stat_database.numbackends,
    pg_stat_database.xact_commit,
    pg_stat_database.xact_rollback,
    pg_stat_database.blks_read,
    pg_stat_database.blks_hit,
    pg_stat_database.tup_returned,
    pg_stat_database.tup_fetched,
    pg_stat_database.tup_inserted,
    pg_stat_database.tup_updated,
    pg_stat_database.tup_deleted
FROM pg_stat_database
WHERE datname = 'seahorse';

-- 表级统计
SELECT
    schemaname,
    tablename,
    seq_scan,
    seq_tup_read,
    idx_scan,
    idx_tup_fetch,
    n_tup_ins,
    n_tup_upd,
    n_tup_del,
    n_live_tup,
    n_dead_tup
FROM pg_stat_user_tables
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;

-- 索引统计
SELECT
    schemaname,
    tablename,
    indexname,
    idx_scan,
    idx_tup_read,
    idx_tup_fetch,
    pg_size_pretty(pg_relation_size(indexrelid)) AS size
FROM pg_stat_user_indexes
WHERE schemaname = 'public'
ORDER BY idx_scan DESC;
```

### 4.2 性能对比报告模板
```markdown
# 性能测试对比报告

## 测试环境
- CPU: Intel Xeon @ 2.4GHz (8 cores)
- 内存: 32GB DDR4
- 磁盘: SSD 500GB
- PostgreSQL版本: 15.3

## 测试数据量
- 用户数: 10,000
- 会话数: 100,000
- 消息数: 1,000,000

## 测试结果对比

### 主键查询性能
| 指标 | VARCHAR (优化前) | BIGINT (优化后) | 提升比例 |
|------|-----------------|----------------|---------|
| 等值查询耗时 | XX ms | XX ms | +XX% |
| 范围查询耗时 | XX ms | XX ms | +XX% |
| 分页查询耗时 | XX ms | XX ms | +XX% |

### 关联查询性能
| 指标 | VARCHAR (优化前) | BIGINT (优化后) | 提升比例 |
|------|-----------------|----------------|---------|
| 两表JOIN | XX ms | XX ms | +XX% |
| 三表JOIN | XX ms | XX ms | +XX% |
| 聚合查询 | XX ms | XX ms | +XX% |

### 索引空间占用
| 表名 | VARCHAR 索引大小 | BIGINT 索引大小 | 节省空间 |
|------|----------------|----------------|---------|
| t_user | XX MB | XX MB | XX% |
| t_conversation | XX MB | XX MB | XX% |
| t_message | XX MB | XX MB | XX% |

### ID生成性能
| 方法 | 100万次调用耗时 | 吞吐量 |
|------|---------------|--------|
| SnowflakeIds.nextId() | XX ms | XX ops/ms |
| SnowflakeIds.nextIdString() | XX ms | XX ops/ms |

## 结论
- 总体性能提升: XX%
- 索引空间节省: XX%
- 查询响应时间减少: XX%
```

## 5. 回归测试

### 5.1 功能测试清单
- [ ] 用户注册和登录功能正常
- [ ] 会话创建和消息发送功能正常
- [ ] 知识库文档上传和处理功能正常
- [ ] 记忆存储和检索功能正常
- [ ] API 返回值格式正确

### 5.2 集成测试脚本
```bash
#!/bin/bash
# \d:\performance\test\run_integration_tests.sh

echo "Running integration tests..."

# 启动应用
mvn spring-boot:run &
APP_PID=$!

# 等待应用启动
sleep 30

# 运行 API 测试
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin"}'

# 运行知识库测试
curl -X POST http://localhost:8080/api/v1/knowledge/documents \
  -H "Authorization: Bearer $TOKEN" \
  -F "file=@test.pdf"

# 运行消息测试
curl -X POST http://localhost:8080/api/v1/chat/send \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"conversationId":"123","content":"Hello"}'

# 停止应用
kill $APP_PID

echo "Integration tests completed."
```

## 6. 性能基线对比

### 6.1 优化前基线数据
```json
{
  "baseline": {
    "primary_key_query": {
      "avg_response_time": "5ms",
      "p99_response_time": "15ms",
      "throughput": "10000 ops/sec"
    },
    "foreign_key_join": {
      "avg_response_time": "25ms",
      "p99_response_time": "80ms",
      "throughput": "2000 ops/sec"
    },
    "batch_insert": {
      "avg_time_per_record": "0.5ms",
      "throughput": "2000 records/sec"
    },
    "index_size": {
      "t_message": "150MB",
      "t_conversation": "80MB",
      "total": "500MB"
    }
  }
}
```

### 6.2 预期优化目标
```json
{
  "optimization_target": {
    "primary_key_query": {
      "expected_improvement": "20%",
      "target_response_time": "4ms"
    },
    "foreign_key_join": {
      "expected_improvement": "30%",
      "target_response_time": "17.5ms"
    },
    "batch_insert": {
      "expected_improvement": "15%",
      "target_time_per_record": "0.43ms"
    },
    "index_size": {
      "expected_reduction": "70%",
      "target_size": "150MB"
    }
  }
}
```

## 7. 监控告警配置

### 7.1 PostgreSQL 监控查询
```sql
-- 慢查询监控
SELECT
    query,
    calls,
    mean_exec_time,
    total_exec_time,
    rows
FROM pg_stat_statements
WHERE query LIKE '%t_message%'
ORDER BY mean_exec_time DESC
LIMIT 10;

-- 连接数监控
SELECT
    count(*),
    state
FROM pg_stat_activity
GROUP BY state;

-- 锁等待监控
SELECT
    pid,
    usename,
    relation::regclass,
    mode,
    granted,
    query
FROM pg_locks
WHERE NOT granted;
```

### 7.2 告警阈值配置
```yaml
# Prometheus 告警规则
groups:
  - name: database_performance
    rules:
      - alert: HighQueryLatency
        expr: pg_stat_statements_mean_exec_time > 100
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High database query latency detected"

      - alert: HighIndexBloat
        expr: pg_stat_user_indexes_idx_scan == 0
        for: 1h
        labels:
          severity: info
        annotations:
          summary: "Index has not been used for 1 hour"
```

## 8. 后续优化建议

1. **批量操作优化**: 使用 PostgreSQL COPY 命令进行超大批量导入
2. **分区表策略**: 对大表（如 t_message）按时间进行范围分区
3. **读写分离**: 部署主从复制，分担查询压力
4. **连接池优化**: 调整 HikariCP 参数，优化连接复用
5. **查询缓存**: 引入 Redis 缓存热点查询结果

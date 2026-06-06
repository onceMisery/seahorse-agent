# 块E · 异常处理与容错 — 生产级健壮性方案

> 文档定位：SaaS MVP 执行计划第 14 篇。功能增强系列之「后端健壮性」。  
> 关键属性：**P0 优先级、生产必需、独立可实施**。  
> 编写依据：2026-06-05 代码审查 + 生产最佳实践。  
> 工作量口径：1 人 × 5-6 天。

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ 异常处理不统一（有些返回 500，有些返回业务码）
- ❌ AI 模型超时无降级（用户等待 30 秒返回错误）
- ❌ 无熔断机制（下游故障导致雪崩）
- ❌ 无重试策略（偶发网络错误直接失败）
- ❌ 超时配置混乱（有些 30s，有些无限等待）

**生产风险**：
- 🔥 **雪崩风险**：一个服务故障拖垮整个系统
- 🔥 **用户体验差**：长时间等待后返回错误
- 🔥 **调试困难**：异常信息不统一，难以定位问题

**本方案价值**：
- ✅ 统一异常处理（标准错误码、友好提示）
- ✅ 降级策略（AI 超时降级到缓存结果）
- ✅ 熔断限流（Resilience4j，保护系统）
- ✅ 智能重试（幂等性保障、指数退避）
- ✅ 超时控制（HTTP/DB/AI 统一超时配置）
- ✅ 优雅关机（处理中请求完成后再关闭）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 |
|------|------|--------|------|
| G1 | 统一异常处理框架（GlobalExceptionHandler 完善） | **P0** | 部分实现 ⚠️ |
| G2 | AI 模型调用降级策略（超时降级到缓存/简化回复） | **P0** | 无 ❌ |
| G3 | 熔断与限流（Resilience4j 集成） | **P0** | 无 ❌ |
| G4 | 重试机制（幂等性保障、指数退避） | P1 | 无 ❌ |
| G5 | 超时控制（HTTP/DB/AI 统一配置） | **P0** | 部分配置 ⚠️ |
| G6 | 优雅关机（Graceful Shutdown） | P1 | Spring Boot 默认 ✅ |

### 1.2 明确不做（后延）

- **不做**分布式追踪（OpenTelemetry）— 05-observability 已覆盖
- **不做**混沌工程（Chaos Monkey）— Phase 2
- **不做**蓝绿部署/金丝雀发布 — 运维层面，非应用层

### 1.3 验收信号

#### P0 验收

1. ✅ 触发 AI 调用超时（Mock 30s），系统在 5s 内降级返回缓存结果
2. ✅ 触发外部 API 故障（Mock HTTP 500），熔断器在 10 次失败后打开，后续请求快速失败（不再调用）
3. ✅ 触发数据库连接超时，返回标准错误码 `DB_TIMEOUT`，前端显示友好提示
4. ✅ 所有 API 错误响应遵循统一格式：`{code, message, timestamp, path}`

#### P1 验收

5. ⚠️ 偶发网络错误（Mock 第 1-2 次失败），自动重试成功（指数退避：1s、2s、4s）
6. ⚠️ 应用关闭时（kill -15），等待处理中请求完成（最多 30s）再退出

---

## 2. 现状（代码级审查）

### 2.1 已有异常处理（部分可复用）

**全局异常处理器**：`SeahorseGlobalExceptionHandler`  
**位置**：`seahorse-agent-adapter-web/.../web/SeahorseGlobalExceptionHandler.java`

**已处理异常**：
- `IllegalArgumentException` → 400
- `NoSuchElementException` → 404
- `RuntimeException` → 500

**缺口**：
- ❌ 无业务异常分类（需区分用户错误 vs 系统错误）
- ❌ 无统一错误码（前端靠 HTTP 状态码判断）
- ❌ 无错误上下文（租户 ID、请求 ID、用户 ID 缺失）
- ❌ 无敏感信息脱敏（数据库错误可能暴露表结构）

### 2.2 AI 调用现状

**核心类**：`OpenAiCompatibleChatAdapter`  
**位置**：`seahorse-agent-adapter-ai-model/.../ai/model/OpenAiCompatibleChatAdapter.java`

**超时配置**（第 85 行）：
```java
.timeout(Duration.ofSeconds(30))  // 写死 30 秒
```

**问题**：
- ❌ 超时后直接抛异常（无降级）
- ❌ 无重试机制（偶发 502/503 直接失败）
- ❌ 无熔断保护（模型持续故障会拖垮系统）

### 2.3 数据库超时配置

**HikariCP 配置**（`application.properties`）：
```properties
spring.datasource.hikari.connection-timeout=30000  # 30 秒
spring.datasource.hikari.validation-timeout=5000   # 5 秒
```

**问题**：
- ⚠️ 30 秒过长（用户无法接受）
- ❌ 无查询超时（慢 SQL 可能无限等待）

### 2.4 HTTP 客户端超时

**OkHttp 配置**：
```java
// 在各 Adapter 中重复配置
.connectTimeout(10, TimeUnit.SECONDS)
.readTimeout(30, TimeUnit.SECONDS)
```

**问题**：
- ❌ 配置分散（每个 Adapter 独立配置）
- ❌ 无统一管理（改一处需要改多处）

---

## 3. 技术方案

### 3.1 统一异常处理框架（P0）

#### 3.1.1 业务异常分类

**新增异常基类**：`SeahorseBusinessException`

```java
package com.miracle.ai.seahorse.agent.kernel.domain.common;

public abstract class SeahorseBusinessException extends RuntimeException {
    
    private final String errorCode;
    private final int httpStatus;
    
    protected SeahorseBusinessException(String errorCode, String message, int httpStatus) {
        super(message);
        this.errorCode = errorCode;
        this.httpStatus = httpStatus;
    }
    
    public String getErrorCode() {
        return errorCode;
    }
    
    public int getHttpStatus() {
        return httpStatus;
    }
}
```

**具体异常类**（放 `kernel/domain/common/exception/` 目录）：

```java
// 用户错误（4xx）
public class ResourceNotFoundException extends SeahorseBusinessException {
    public ResourceNotFoundException(String resource, String id) {
        super("RESOURCE_NOT_FOUND", 
              String.format("%s not found: %s", resource, id), 
              404);
    }
}

public class QuotaExceededException extends SeahorseBusinessException {
    public QuotaExceededException(String quotaType) {
        super("QUOTA_EXCEEDED", 
              String.format("Quota exceeded: %s", quotaType), 
              429);
    }
}

public class InvalidInputException extends SeahorseBusinessException {
    public InvalidInputException(String field, String reason) {
        super("INVALID_INPUT", 
              String.format("Invalid %s: %s", field, reason), 
              400);
    }
}

// 系统错误（5xx）
public class ExternalServiceException extends SeahorseBusinessException {
    public ExternalServiceException(String service, Throwable cause) {
        super("EXTERNAL_SERVICE_ERROR", 
              String.format("External service failed: %s", service), 
              503);
        initCause(cause);
    }
}

public class DatabaseTimeoutException extends SeahorseBusinessException {
    public DatabaseTimeoutException(Throwable cause) {
        super("DB_TIMEOUT", 
              "Database operation timeout", 
              503);
        initCause(cause);
    }
}
```

#### 3.1.2 统一错误响应格式

**错误响应 DTO**：

```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ErrorResponse(
    String code,           // 错误码（如 QUOTA_EXCEEDED）
    String message,        // 用户友好的错误信息
    Instant timestamp,     // 错误发生时间
    String path,           // 请求路径
    String requestId,      // 请求 ID（用于追踪）
    String tenantId,       // 租户 ID（已脱敏，只用于内部日志）
    Map<String, Object> details  // 额外详情（可选）
) {
    public static ErrorResponse of(String code, String message, String path) {
        return new ErrorResponse(
            code, 
            message, 
            Instant.now(), 
            path, 
            generateRequestId(),  // UUID
            null,  // tenantId 不暴露给前端
            null
        );
    }
    
    private static String generateRequestId() {
        return UUID.randomUUID().toString().substring(0, 8);
    }
}
```

#### 3.1.3 增强 GlobalExceptionHandler

**位置**：`seahorse-agent-adapter-web/.../web/SeahorseGlobalExceptionHandler.java`

```java
@RestControllerAdvice
@Order(1)  // ✅ 确保优先级高于其他 @ControllerAdvice
@Slf4j
public class SeahorseGlobalExceptionHandler {
    
    @ExceptionHandler(SeahorseBusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            SeahorseBusinessException ex,
            HttpServletRequest request) {
        
        // 业务异常不打印堆栈（避免日志污染）
        log.warn("Business exception: code={}, message={}, path={}", 
            ex.getErrorCode(), ex.getMessage(), request.getRequestURI());
        
        ErrorResponse response = ErrorResponse.of(
            ex.getErrorCode(),
            ex.getMessage(),
            request.getRequestURI()
        );
        
        return ResponseEntity
            .status(ex.getHttpStatus())
            .body(response);
    }
    
    @ExceptionHandler(HttpTimeoutException.class)
    public ResponseEntity<ErrorResponse> handleTimeout(
            HttpTimeoutException ex,
            HttpServletRequest request) {
        
        log.error("HTTP timeout: path={}", request.getRequestURI(), ex);
        
        ErrorResponse response = ErrorResponse.of(
            "HTTP_TIMEOUT",
            "请求超时，请稍后重试",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(504).body(response);
    }
    
    @ExceptionHandler(SQLException.class)
    public ResponseEntity<ErrorResponse> handleSqlException(
            SQLException ex,
            HttpServletRequest request) {
        
        // 脱敏处理（不暴露表名/字段名）
        log.error("Database error: sqlState={}, path={}", 
            ex.getSQLState(), request.getRequestURI(), ex);
        
        ErrorResponse response = ErrorResponse.of(
            "DB_ERROR",
            "数据库操作失败，请稍后重试",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(503).body(response);
    }
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleUnknownException(
            Exception ex,
            HttpServletRequest request) {
        
        // 未预期的异常，打印完整堆栈
        log.error("Unexpected error: path={}", request.getRequestURI(), ex);
        
        ErrorResponse response = ErrorResponse.of(
            "INTERNAL_ERROR",
            "系统内部错误，请联系管理员",
            request.getRequestURI()
        );
        
        return ResponseEntity.status(500).body(response);
    }
}
```

---

### 3.2 AI 模型调用降级策略（P0）

#### 3.2.1 降级策略设计

**降级层次**：
1. **L1 降级**：超时后返回缓存结果（最近一次成功回复）
2. **L2 降级**：无缓存时返回简化回复（"系统繁忙，请稍后重试"）
3. **L3 降级**：熔断打开时快速失败（不再调用模型）

**缓存策略**：
- Key：`chat:cache:{sessionId}:{sha256(userMessage)}`
- TTL：1 小时
- 存储：Redis（复用现有 RedissonClient）

#### 3.2.2 实现方案

**新增降级适配器**：`ResilientChatModelAdapter`（装饰器模式）

```java
package com.miracle.ai.seahorse.agent.adapters.ai.model;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.timelimiter.TimeLimiter;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;

public class ResilientChatModelAdapter implements ChatModelPort {
    
    private final ChatModelPort delegate;  // 被装饰的原始适配器
    private final CircuitBreaker circuitBreaker;
    private final TimeLimiter timeLimiter;
    private final RedissonClient redisson;
    private final ExecutorService aiExecutor;  // ✅ 自定义线程池，避免使用默认 ForkJoinPool
    
    public ResilientChatModelAdapter(
            ChatModelPort delegate,
            CircuitBreaker circuitBreaker,
            TimeLimiter timeLimiter,
            RedissonClient redisson,
            ExecutorService aiExecutor) {
        this.delegate = delegate;
        this.circuitBreaker = circuitBreaker;
        this.timeLimiter = timeLimiter;
        this.redisson = redisson;
        this.aiExecutor = aiExecutor;
    }
    
    @Override
    public ChatResponse chat(ChatRequest request) {
        String cacheKey = generateCacheKey(request);
        
        try {
            // 尝试调用模型（带超时 + 熔断保护）
            return circuitBreaker.executeSupplier(() -> 
                timeLimiter.executeFutureSupplier(() -> 
                    CompletableFuture.supplyAsync(() -> {  // ✅ 使用自定义线程池
                        ChatResponse response = delegate.chat(request);
                        cacheResponse(cacheKey, response);  // 成功后缓存
                        return response;
                    }, aiExecutor)
                )
            );
        } catch (TimeoutException | CallNotPermittedException ex) {
            // L1 降级：返回缓存
            ChatResponse cached = getCachedResponse(cacheKey);
            if (cached != null) {
                log.warn("AI model degraded to cache: sessionId={}", 
                    request.getSessionId());
                return cached;
            }
            
            // L2 降级：返回简化回复
            log.error("AI model degraded to fallback: sessionId={}", 
                request.getSessionId(), ex);
            return ChatResponse.fallback("抱歉，当前系统繁忙，请稍后重试");
        }
    }
    
    private String generateCacheKey(ChatRequest request) {
        // ✅ 使用 SHA-256 替代 MD5（减少碰撞风险）
        String messageHash = DigestUtils.sha256Hex(
            request.getMessages().toString()
        );
        return String.format("chat:cache:%s:%s", 
            request.getSessionId(), messageHash);
    }
    
    private void cacheResponse(String key, ChatResponse response) {
        RBucket<ChatResponse> bucket = redisson.getBucket(key);
        bucket.set(response, 1, TimeUnit.HOURS);
    }
    
    private ChatResponse getCachedResponse(String key) {
        RBucket<ChatResponse> bucket = redisson.getBucket(key);
        return bucket.get();
    }
}
```

#### 3.2.3 重试机制（外部 API 调用）

```java
// 使用 Resilience4j Retry 实现智能重试
@Bean
public RetryRegistry retryRegistry() {
    RetryConfig config = RetryConfig.custom()
        .maxAttempts(3)                                    // 最多重试 3 次
        .waitDuration(Duration.ofSeconds(1))               // 初始等待 1s
        .intervalFunction(IntervalFunction.ofExponentialBackoff(1000, 2.0))  // 指数退避
        .retryExceptions(IOException.class, TimeoutException.class)  // 只对幂等安全异常重试
        .ignoreExceptions(IllegalArgumentException.class)  // 不重试业务异常
        .build();
    
    return RetryRegistry.of(config);
}

// 使用示例
public ChatResponse callWithRetry(ChatRequest request) {
    Retry retry = retryRegistry.retry("aiModel");
    return Retry.decorateSupplier(retry, () -> delegate.chat(request)).get();
}
```

#### 3.2.4 限流保护（RateLimiter）

```java
// Resilience4j RateLimiter 配置
@Bean
public RateLimiterRegistry rateLimiterRegistry() {
    RateLimiterConfig config = RateLimiterConfig.custom()
        .limitForPeriod(100)             // 每个周期允许 100 个请求
        .limitRefreshPeriod(Duration.ofSeconds(1))  // 周期 1 秒
        .timeoutDuration(Duration.ofMillis(500))    // 等待超时 500ms
        .build();
    
    return RateLimiterRegistry.of(config);
}

// 在 Controller 层使用
@GetMapping("/api/chat/stream")
@RateLimiter(name = "chatStream", fallbackMethod = "chatStreamFallback")
public SseEmitter chatStream(@RequestParam String sessionId) {
    // ... 正常逻辑
}

// fallback 方法
public SseEmitter chatStreamFallback(RequestNotPermitted ex) {
    SseEmitter emitter = new SseEmitter();
    emitter.completeWithError(new QuotaExceededException("API rate limit exceeded"));
    return emitter;
}
```

#### 3.2.5 统一超时配置

```yaml
# application.yml — 统一管理所有超时参数
seahorse:
  timeout:
    http:
      connect: 5s       # HTTP 连接超时
      read: 10s         # HTTP 读取超时（普通 API）
      write: 10s        # HTTP 写入超时
    ai-model:
      call: 30s         # AI 模型调用超时（大模型响应慢，单独放宽）
      stream: 120s      # AI 流式响应超时（流式需要更长超时）
    database:
      connection: 10s   # 数据库连接超时
      query: 30s        # 慢查询超时（超过 30s 的 SQL 自动终止）
    cache:
      read: 1s          # Redis 读取超时
      write: 2s         # Redis 写入超时
```

#### 3.2.6 统一异步线程池配置

```java
@Configuration
public class AsyncExecutorConfiguration {
    
    @Bean("aiExecutor")
    public ExecutorService aiExecutor() {
        return new ThreadPoolExecutor(
            10,                      // corePoolSize
            50,                      // maxPoolSize
            60L, TimeUnit.SECONDS,   // keepAliveTime
            new LinkedBlockingQueue<>(200),
            new ThreadFactoryBuilder().setNameFormat("ai-executor-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()  // 队列满时由调用线程执行（背压）
        );
    }
    
    @Bean("notificationExecutor")
    public ExecutorService notificationExecutor() {
        return new ThreadPoolExecutor(
            5, 20, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(500),
            new ThreadFactoryBuilder().setNameFormat("notification-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
    
    @Bean("taskExecutor")
    public ExecutorService taskExecutor() {
        return new ThreadPoolExecutor(
            10, 30, 60L, TimeUnit.SECONDS,
            new LinkedBlockingQueue<>(1000),
            new ThreadFactoryBuilder().setNameFormat("task-executor-%d").build(),
            new ThreadPoolExecutor.CallerRunsPolicy()
        );
    }
}
```

> **所有异步任务必须使用命名线程池**（`aiExecutor`/`notificationExecutor`/`taskExecutor`），
> 禁止使用 `CompletableFuture.supplyAsync()` 的默认 ForkJoinPool 或 `@Async` 的默认 SimpleAsyncTaskExecutor。

#### 3.2.7 Resilience4j 完整配置

**依赖**（`pom.xml`）：
```xml
<dependency>
    <groupId>io.github.resilience4j</groupId>
    <artifactId>resilience4j-spring-boot3</artifactId>
    <version>2.1.0</version>
</dependency>
```

**配置**（`application.yml`）：
```yaml
resilience4j:
  circuitbreaker:
    instances:
      aiModel:
        sliding-window-size: 10
        failure-rate-threshold: 50
        wait-duration-in-open-state: 30s
        permitted-number-of-calls-in-half-open-state: 3  # 半开状态允许 3 个试探请求
  timelimiter:
    instances:
      aiModel:
        timeout-duration: 30s  # ✅ AI 模型超时 30s（流式响应需更长超时）
      externalApi:
        timeout-duration: 10s  # 普通外部 API 超时 10s
  retry:
    instances:
      aiModel:
        max-attempts: 3
        wait-duration: 1s
        enable-exponential-backoff: true
        exponential-backoff-multiplier: 2
        retry-exceptions:
          - java.io.IOException
          - java.util.concurrent.TimeoutException
  ratelimiter:
    instances:
      chatStream:
        limit-for-period: 100
        limit-refresh-period: 1s
        timeout-duration: 500ms
      apiDefault:
        limit-for-period: 200
        limit-refresh-period: 1s
        timeout-duration: 200ms
```

---

## 4. 实施步骤

### Day 1-2：统一异常处理（P0）
- Day 1 上午：编写业务异常类（2h）
- Day 1 下午：增强 GlobalExceptionHandler + @Order 排序（2h）
- Day 2 上午：统一错误响应格式 + requestId 追踪（2h）
- Day 2 下午：验证测试（2h）

### Day 3-4：降级 + 熔断 + 限流（P0）
- Day 3 上午：集成 Resilience4j + 自定义线程池配置（3h）
- Day 3 下午：实现 AI 三级降级策略（3h）
- Day 4 上午：RateLimiter 限流配置 + 重试机制（3h）
- Day 4 下午：触发超时/故障验证降级和熔断（2h）

### Day 5-6：超时 + 线程池 + 优雅关闭（P1）
- Day 5 上午：统一超时配置 YAML + 全局线程池（3h）
- Day 5 下午：优雅关闭（Graceful Shutdown）配置（2h）
- Day 6：集成测试 + 压力测试验证限流（5h）

---

## 5. 验收标准

✅ P0 验收全部通过（见 §1.3）
✅ 代码覆盖率 ≥ 80%
✅ 压测：熔断器正常工作

---

**文档版本**：v1.1-review  
**最后更新**：2026-06-06  
**修订说明**：补全文档（重试/限流/超时/线程池）；自定义线程池替代 ForkJoinPool；MD5→SHA-256；AI 超时 5s→30s；工期 3天→5-6天

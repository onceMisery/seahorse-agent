# 块F · 数据一致性保障 — 轻量级数据安全方案

> 文档定位：SaaS MVP 执行计划第 15 篇。功能增强系列之「后端健壮性」。  
> 关键属性：**P0 优先级、数据安全必需、独立可实施、轻量优先**。  
> 编写依据：2026-06-05 代码审查 + 实用主义最佳实践。  
> 工作量口径：1 人 × 2-3 天。

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ 无事务保障（注册 + 建租户可能部分失败）
- ❌ 支付接口不幂等（重复提交可能重复扣款）
- ❌ 并发更新无控制（多用户同时修改配额可能丢失更新）
- ❌ 数据校验不完整（前端校验可绕过，后端无二次校验）

**生产风险**：
- 🔥 **资损风险**：支付重复扣款、配额计算错误
- 🔥 **数据不一致**：用户注册成功但租户未创建
- 🔥 **并发丢失更新**：多人同时修改配置，后者覆盖前者

**本方案价值**：
- ✅ 轻量级事务保障（数据库事务 + 补偿日志 + 异步重试）
- ✅ 幂等性设计（Redis + 唯一索引双重保障）
- ✅ 并发控制（乐观锁为主、悲观锁为辅）
- ✅ 双层校验（前端 + 后端）

**设计原则**：
- 🎯 **轻量优先**：不引入额外中间件（无 Seata/Saga 编排器）
- 🎯 **简单可靠**：基于数据库事务 + Redis，技术栈统一
- 🎯 **渐进式**：先保证核心场景，复杂场景后续优化

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 | 方案 |
|------|------|--------|------|------|
| G1 | 轻量级事务保障 | **P0** | 无 ❌ | 数据库事务 + 补偿日志 |
| G2 | 幂等性设计 | **P0** | 无 ❌ | Redis SET NX + 唯一索引 |
| G3 | 并发控制 | **P0** | 部分 ⚠️ | 乐观锁 + 悲观锁 + 分布式锁 |
| G4 | 数据校验 | **P0** | 前端有 ⚠️ | Bean Validation |

### 1.2 明确不做（避免过度设计）

- **不做** Saga 编排器（轻量方案足够）
- **不做** TCC 事务（复杂度高）
- **不做** 分布式事务中间件（Seata）— 不引入额外中间件
- **不做** 区块链审计（过度设计）

### 1.3 验收信号

#### P0 验收

1. ✅ 用户注册 + 租户创建：租户创建失败，用户记录自动回滚
2. ✅ 支付接口：相同 Idempotency Key 调用 3 次，只扣款 1 次
3. ✅ 配额扣减：10 个并发请求同时扣减 10 tokens，最终余额准确
4. ✅ 恶意输入：前端绕过校验，后端返回 400 错误

---

## 2. 现状问题

### 2.1 用户注册场景

**当前实现**：
```java
@Transactional
public void register(UserRegistrationRequest request) {
    User user = userRepository.save(new User(...));          // 1. 创建用户
    tenantProvisioningPort.provision(user.getTenantId());    // 2. 创建租户（可能失败）
    emailPort.sendWelcomeEmail(user.getEmail());             // 3. 发送邮件（可能失败）
}
```

**问题**：
- ❌ `@Transactional` 只覆盖数据库操作
- ❌ 租户创建失败，用户已提交（数据不一致）
- ❌ 邮件发送失败，整个事务回滚（不合理）

### 2.2 支付场景

**问题**：
- ❌ 无 Idempotency Key，重复点击重复扣款
- ❌ 第三方支付成功，数据库异常导致订阅未激活

### 2.3 配额扣减场景

**当前实现**：
```java
public void deduct(String tenantId, int tokens) {
    Quota quota = quotaRepository.findByTenantId(tenantId);
    quota.setRemaining(quota.getRemaining() - tokens);  // ❌ Read-Modify-Write 竞态
    quotaRepository.save(quota);
}
```

**问题**：并发场景下丢失更新

---

## 3. 技术方案

### 3.1 轻量级事务保障（P0）

#### 3.1.1 核心策略

**策略一：数据库操作用事务，外部调用异步化**

```java
@Service
public class UserRegistrationService {
    
    @Transactional
    public User register(UserRegistrationRequest request) {
        // Step 1: 创建用户（数据库事务）
        User user = userRepository.save(User.from(request));
        
        // Step 2: 创建租户（同步，必须成功）
        try {
            tenantPort.provision(user.getTenantId());
        } catch (Exception ex) {
            log.error("Tenant provision failed, rolling back", ex);
            throw new BusinessException("租户初始化失败，请联系管理员", ex);
        }
        
        // Step 3: 发送邮件（异步，不阻塞主流程）✅
        asyncEmailService.sendWelcomeEmailAsync(user.getEmail());
        
        return user;
    }
}
```

**关键点**：
- ✅ 租户创建失败直接抛异常，事务自动回滚
- ✅ 邮件异步发送，失败不影响注册

#### 3.1.2 补偿日志表（兜底方案）

**场景**：异步操作失败后自动重试

**表结构**：
```sql
CREATE TABLE t_compensation_log (
    id              BIGSERIAL PRIMARY KEY,
    tenant_id       VARCHAR(64) NOT NULL,
    type            VARCHAR(32) NOT NULL,  -- WELCOME_EMAIL/REFUND/...
    payload         JSONB NOT NULL,        -- 补偿所需参数
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    retry_count     INT DEFAULT 0,
    max_retries     INT DEFAULT 3,
    last_error      TEXT,
    created_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_status_created (status, created_at)
);
```

**异步邮件服务**：
```java
@Service
public class AsyncEmailService {
    
    @Async
    public void sendWelcomeEmailAsync(String email) {
        try {
            emailPort.sendWelcomeEmail(email);
        } catch (Exception ex) {
            log.warn("Email failed, will retry later: email={}", email, ex);
            
            // ✅ 记录到补偿日志表
            compensationLogRepository.save(CompensationLog.builder()
                .type("WELCOME_EMAIL")
                .payload(Map.of("email", email))
                .status("PENDING")
                .retryCount(0)
                .build());
        }
    }
}
```

**定时重试任务**：
```java
@Component
public class CompensationRetryJob {
    
    @Scheduled(fixedDelay = 60000)  // 每分钟执行一次
    public void retryPendingCompensations() {
        List<CompensationLog> pending = compensationLogRepository
            .findByStatusAndRetryCountLessThan("PENDING", 3);
        
        for (CompensationLog log : pending) {
            try {
                executeCompensation(log);
                log.setStatus("SUCCESS");
                
            } catch (Exception ex) {
                log.setRetryCount(log.getRetryCount() + 1);
                log.setLastError(ex.getMessage());
                
                if (log.getRetryCount() >= log.getMaxRetries()) {
                    log.setStatus("FAILED");
                    alertService.send("补偿失败，需要人工介入", log);  // ✅ 告警
                }
            }
            compensationLogRepository.save(log);
        }
    }
}
```

**优点**：
- ✅ 轻量级：基于数据库 + 定时任务
- ✅ 可追溯：所有补偿操作有日志
- ✅ 可重试：自动重试 3 次，失败告警

#### 3.1.3 支付场景优化

**策略：创建订单 + 回调激活（分离支付和业务逻辑）**

```java
@Service
public class PaymentService {
    
    // Step 1: 创建订单（不调用支付）
    @Transactional
    public PaymentOrder createOrder(PaymentRequest request) {
        return paymentRepository.save(PaymentOrder.from(request));
    }
    
    // Step 2: 支付回调处理（幂等）
    @Transactional
    public void handleCallback(PaymentCallback callback) {
        String callbackId = callback.getCallbackId();
        String orderId = callback.getOrderId();
        
        // ✅ 幂等性检查（Redis + 数据库双重保障）
        String key = "payment:callback:" + callbackId;
        Boolean success = redis.setIfAbsent(key, "1", 24, TimeUnit.HOURS);
        
        if (!Boolean.TRUE.equals(success)) {
            log.warn("Duplicate callback: {}", callbackId);
            return;  // ✅ 重复回调直接返回
        }
        
        // 查询订单
        PaymentOrder order = paymentRepository.findById(orderId)
            .orElseThrow(() -> new BusinessException("订单不存在"));
        
        // 更新订单状态 + 激活订阅（在一个事务内）
        if (callback.isSuccess()) {
            order.setStatus("PAID");
            order.setTransactionId(callback.getTransactionId());
            paymentRepository.save(order);
            
            // ✅ 激活订阅
            subscriptionRepository.activate(order.getSubscriptionId());
        }
    }
}
```

**关键点**：
1. ✅ 创建订单和支付分离
2. ✅ 回调幂等性（Redis SET NX）
3. ✅ 回调处理在一个事务内（更新订单 + 激活订阅）

---

### 3.2 幂等性设计（P0）

#### 3.2.1 支付幂等性（Redis + 唯一索引）

**方案一：Redis SET NX**（推荐）

```java
public void processPayment(PaymentRequest request) {
    String key = "payment:idempotency:" + request.getIdempotencyKey();
    
    // ✅ Redis 分布式锁（24 小时过期）
    Boolean success = redis.setIfAbsent(key, "1", 24, TimeUnit.HOURS);
    
    if (!Boolean.TRUE.equals(success)) {
        throw new DuplicateRequestException("请勿重复提交");
    }
    
    try {
        // 执行支付逻辑
        paymentGateway.pay(...);
    } catch (Exception ex) {
        // ✅ 失败时删除锁，允许重试
        redis.delete(key);
        throw ex;
    }
}
```

**方案二：数据库唯一索引**（兜底）

```sql
ALTER TABLE t_payment_order 
    ADD COLUMN idempotency_key VARCHAR(64) UNIQUE;
```

```java
@Transactional
public void processPayment(PaymentRequest request) {
    try {
        PaymentOrder order = PaymentOrder.builder()
            .idempotencyKey(request.getIdempotencyKey())  // ✅ 唯一索引
            .build();
        
        paymentRepository.save(order);
        
    } catch (DataIntegrityViolationException ex) {
        // ✅ 唯一索引冲突，说明重复请求
        throw new DuplicateRequestException("请勿重复提交");
    }
}
```

**双重保障**：
- ✅ Redis 快速去重（< 1ms）
- ✅ 数据库唯一索引兜底（Redis 失效时）

#### 3.2.2 配额扣减幂等性

```java
@Transactional
public void deductQuota(String tenantId, int tokens, String requestId) {
    // ✅ 幂等性检查
    String key = "quota:deduct:" + requestId;
    if (redis.exists(key)) {
        return;  // 已处理过
    }
    
    // 扣减配额（使用悲观锁，见 3.3.2）
    Quota quota = quotaRepository.findByIdForUpdate(tenantId);
    quota.deduct(tokens);
    quotaRepository.save(quota);
    
    // ✅ 标记已处理（24 小时过期）
    redis.setex(key, 86400, "1");
}
```

---

### 3.3 并发控制（P0）

#### 3.3.1 乐观锁（推荐，高并发场景）

**适用场景**：读多写少、冲突率低

**实现**：
```java
@Entity
public class Subscription {
    @Id
    private Long id;
    
    @Version  // ✅ JPA 自动处理乐观锁
    private Long version;
    
    private String status;
}

@Service
public class SubscriptionService {
    
    @Transactional
    public void cancel(Long id) {
        Subscription sub = subscriptionRepository.findById(id).orElseThrow();
        sub.setStatus("CANCELLED");
        
        // ✅ 保存时 JPA 自动检查 version
        subscriptionRepository.save(sub);
    }
}
```

**冲突处理**：
```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ErrorResponse> handleOptimisticLock(
            OptimisticLockingFailureException ex) {
        
        return ResponseEntity.status(409).body(
            ErrorResponse.of("CONFLICT", "数据已被其他用户修改，请刷新后重试")
        );
    }
}
```

#### 3.3.2 悲观锁（写多场景）

**适用场景**：写多读少、必须避免冲突（如配额扣减）

**实现**：
```java
public interface QuotaRepository extends JpaRepository<Quota, Long> {
    
    @Lock(LockModeType.PESSIMISTIC_WRITE)  // ✅ SELECT FOR UPDATE
    @Query("SELECT q FROM Quota q WHERE q.tenantId = :tenantId")
    Quota findByTenantIdForUpdate(@Param("tenantId") String tenantId);
}

@Service
public class QuotaService {
    
    @Transactional
    public void deduct(String tenantId, int tokens) {
        // ✅ 加悲观锁，其他事务等待
        Quota quota = quotaRepository.findByTenantIdForUpdate(tenantId);
        
        if (quota.getRemaining() < tokens) {
            throw new QuotaExceededException();
        }
        
        quota.setRemaining(quota.getRemaining() - tokens);
        quotaRepository.save(quota);
    }
}
```

**SQL 执行**：
```sql
SELECT * FROM t_quota WHERE tenant_id = ? FOR UPDATE;
```

#### 3.3.3 分布式锁（跨实例场景）

**适用场景**：多实例部署，需要全局互斥

**实现**（Redisson）：
```java
@Service
public class PaymentService {
    
    private final RedissonClient redisson;
    
    @Transactional
    public void processPayment(String orderId) {
        RLock lock = redisson.getLock("payment:" + orderId);
        
        try {
            // ✅ 尝试加锁（等待 3s，持有 10s）
            if (lock.tryLock(3, 10, TimeUnit.SECONDS)) {
                try {
                    // 支付逻辑
                } finally {
                    lock.unlock();  // ✅ finally 释放锁
                }
            } else {
                throw new BusinessException("系统繁忙，请稍后重试");
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new BusinessException("操作被中断");
        }
    }
}
```

**对比**：

| 锁类型 | 适用场景 | 性能 | 复杂度 |
|--------|----------|------|--------|
| 乐观锁 | 读多写少 | **高** | 低 |
| 悲观锁 | 写多读少 | 中 | 低 |
| 分布式锁 | 跨实例互斥 | 低 | **高** |

**推荐策略**：
- ✅ 优先使用乐观锁（@Version）
- ✅ 配额扣减用悲观锁（SELECT FOR UPDATE）
- ✅ 支付等关键操作用分布式锁

---

### 3.4 数据校验（P0）

#### 3.4.1 Bean Validation

```java
public class UserRegistrationRequest {
    
    @NotBlank(message = "用户名不能为空")
    @Size(min = 3, max = 20, message = "用户名长度 3-20 字符")
    @Pattern(regexp = "^[a-zA-Z0-9_]+$", message = "用户名只能包含字母、数字、下划线")
    private String username;
    
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
    
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度 8-20 字符")
    private String password;
}

@RestController
public class UserController {
    
    @PostMapping("/api/users")
    public User register(@Valid @RequestBody UserRegistrationRequest request) {
        // ✅ @Valid 自动校验，失败返回 400
        return userService.register(request);
    }
}
```

#### 3.4.2 全局异常处理

```java
@RestControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(
            MethodArgumentNotValidException ex) {
        
        List<String> errors = ex.getBindingResult().getFieldErrors().stream()
            .map(error -> error.getField() + ": " + error.getDefaultMessage())
            .toList();
        
        return ResponseEntity.badRequest().body(
            ErrorResponse.of("VALIDATION_FAILED", errors)
        );
    }
}
```

---

## 4. 实施步骤

### Day 1：事务保障 + 幂等性

**上午**：
1. 补偿日志表（V13 迁移脚本）
2. 异步邮件服务
3. 定时重试任务

**下午**：
4. 支付幂等性（Redis SET NX + 唯一索引）
5. 配额扣减幂等性

### Day 2：并发控制

**上午**：
6. 乐观锁（@Version）
7. 悲观锁（SELECT FOR UPDATE）

**下午**：
8. 分布式锁（Redisson）
9. 全局异常处理

### Day 3：数据校验 + 测试

**上午**：
10. Bean Validation
11. 自定义校验器

**下午**：
12. 集成测试（并发场景、幂等性）
13. 压测验证

---

## 5. 验收标准

### P0 验收

✅ **用户注册 + 租户创建**
```bash
# 模拟租户创建失败
curl -X POST /api/users -d '{...}'
# 预期：返回 500，用户记录未创建（已回滚）
```

✅ **支付幂等性**
```bash
# 相同 Idempotency-Key 调用 3 次
for i in {1..3}; do
  curl -X POST /api/payments \
    -H "Idempotency-Key: test-001" \
    -d '{...}'
done
# 预期：只扣款 1 次
```

✅ **配额扣减并发**
```bash
# 10 个并发请求同时扣减 10 tokens
ab -n 10 -c 10 -p quota.json /api/quota/deduct
# 预期：最终余额 = 初始余额 - 100（无丢失更新）
```

---

## 6. 优势总结

### 对比 Saga 方案

| 维度 | Saga 编排器 | 轻量级方案 | 优势 |
|------|------------|----------|------|
| 复杂度 | 高（需自研） | **低（现有技术栈）** | ✅ |
| 维护成本 | 高 | **低** | ✅ |
| 学习曲线 | 陡 | **平缓** | ✅ |
| 可靠性 | 中（边缘 case 多） | **高（久经考验）** | ✅ |
| 性能 | 中 | **高** | ✅ |

### 核心优势

1. **✅ 零额外中间件**：基于数据库事务 + Redis
2. **✅ 简单可靠**：补偿日志表 + 定时重试 + 告警
3. **✅ 易于理解**：团队成员快速上手
4. **✅ 生产验证**：乐观锁、悲观锁、分布式锁都是成熟方案

---

**文档版本**：v2.0（轻量化）  
**最后更新**：2026-06-05

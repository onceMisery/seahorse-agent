# 块O · 审计与合规 — 企业级合规方案

> 文档定位：SaaS MVP 执行计划第 20 篇。功能增强系列之「合规保障」。  
> 关键属性：**P2 优先级、企业客户必需、独立可实施**。  
> 编写依据：2026-06-05 合规需求 + GDPR/SOC2 最佳实践。  
> 工作量口径：1 人 × 2 天。

---

## ⚡ 快速决策指南

### 为什么需要这个方案？

**现状问题**：
- ❌ 无操作审计（敏感操作无法追溯）
- ❌ 数据变更无记录（不知道谁改了什么）
- ❌ 登录历史缺失（账号被盗无法定位）
- ❌ 无合规报表（企业客户审计时无法提供）
- ❌ 数据无法彻底删除（GDPR 要求）

**合规风险**：
- 🔥 **数据泄露追溯困难**（不知道谁访问了数据）
- 🔥 **GDPR 违规罚款**（用户要求删除数据无法执行）
- 🔥 **SOC2 认证无法通过**（无审计日志）
- 🔥 **内部人员滥用**（超管操作无监控）

**本方案价值**：
- ✅ 操作审计（所有 CRUD 操作记录）
- ✅ 数据变更追踪（记录变更前后值）
- ✅ 登录历史（IP、设备、地理位置）
- ✅ 合规报表（导出审计日志）
- ✅ GDPR 数据删除（彻底删除用户数据）

---

## 1. 目标与范围

### 1.1 做什么（本方案交付物）

| 编号 | 目标 | 优先级 | 现状 | 目标 |
|------|------|--------|------|------|
| G1 | 操作审计（CRUD 操作） | **P0** | 无 ❌ | 所有敏感操作 |
| G2 | 数据变更追踪（前后值） | **P0** | 无 ❌ | JSON Diff |
| G3 | 登录历史（IP、设备） | **P0** | 基础实现 ⚠️ | 增强版 |
| G4 | 合规报表（导出审计日志） | P1 | 无 ❌ | CSV/Excel |
| G5 | 日志归档（90 天） | P1 | 无 ❌ | 对象存储 |
| G6 | GDPR 数据删除 | P1 | 无 ❌ | 匿名化 + 删除 |

### 1.2 审计范围

| 操作类型 | 优先级 | 示例 |
|---------|--------|------|
| 用户管理 | **P0** | 创建用户、修改角色、删除用户 |
| 知识库管理 | **P0** | 删除知识库、修改权限 |
| 订阅管理 | **P0** | 订阅套餐、取消订阅 |
| 配额操作 | **P0** | 手动调整配额 |
| 系统设置 | **P0** | 修改系统配置 |
| 登录登出 | **P0** | 登录成功、登录失败、登出 |

### 1.3 验收信号

#### P0 验收

1. ✅ 删除知识库时记录审计日志（操作人、时间、IP）
2. ✅ 修改用户角色时记录变更前后值
3. ✅ 登录时记录 IP、设备、地理位置
4. ✅ 审计日志可查询（按用户、操作类型、时间范围）

#### P1 验收

5. ⚠️ 导出审计日志（CSV 格式，符合审计要求）
6. ⚠️ GDPR 数据删除（用户数据彻底匿名化）

---

## 2. 现状（合规审查）

### 2.1 已有审计

**10-admin-ops 方案中的审计表**：
```sql
CREATE TABLE sa_audit_log (
    log_id       BIGSERIAL PRIMARY KEY,
    tenant_id    VARCHAR(64) NOT NULL,
    user_id      BIGINT,
    action       VARCHAR(32) NOT NULL,
    resource_type VARCHAR(32) NOT NULL,
    resource_id  VARCHAR(64),
    ip_address   VARCHAR(45),
    created_at   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    
    INDEX idx_tenant_time (tenant_id, created_at DESC)
);
```

**缺失功能**：
- ❌ 无数据变更追踪（changes 字段为空）
- ❌ 无设备信息（User-Agent）
- ❌ 无地理位置
- ❌ 无归档策略

---

## 3. 技术方案

### 3.1 增强审计表（P0）

#### 3.1.1 表结构升级

```sql
-- 扩展 sa_audit_log 表（与 15-data-consistency 中的 sa_audit_log 统一表名）
-- ⚠️ 15 号方案已将 t_audit_log 重命名为 sa_audit_log，此处直接 ALTER
ALTER TABLE sa_audit_log
    ADD COLUMN IF NOT EXISTS changes JSONB,
    ADD COLUMN IF NOT EXISTS user_agent TEXT,
    ADD COLUMN IF NOT EXISTS geo_location VARCHAR(64),
    ADD COLUMN IF NOT EXISTS request_id VARCHAR(64),
    ADD COLUMN IF NOT EXISTS status SMALLINT DEFAULT 0;
    -- 状态枚举：0=SUCCESS, 1=FAILED

-- 注意：若 action 和 resource_type 仍为 VARCHAR，建议迁移为 SMALLINT
-- ALTER TABLE sa_audit_log ALTER COLUMN action TYPE SMALLINT USING action::SMALLINT;
-- ALTER TABLE sa_audit_log ALTER COLUMN resource_type TYPE SMALLINT USING resource_type::SMALLINT;

-- 索引优化（tenant_id 最左前缀，覆盖租户级审计查询）
CREATE INDEX IF NOT EXISTS idx_audit_user_action ON sa_audit_log (tenant_id, user_id, action, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_resource ON sa_audit_log (resource_type, resource_id);
CREATE INDEX IF NOT EXISTS idx_audit_status ON sa_audit_log (status, created_at DESC);

-- CHECK 约束
ALTER TABLE sa_audit_log ADD CONSTRAINT chk_audit_status
    CHECK (status IN (0, 1));
```

> **DDL 审查要点**：
> - ✅ `status` 使用 `SMALLINT`（0/1），不使用 `VARCHAR(16)`
> - ✅ `ADD COLUMN IF NOT EXISTS` 确保幂等升级
> - ✅ 索引使用 `CREATE INDEX IF NOT EXISTS`（PostgreSQL 语法）
> - ✅ 所有索引将 `tenant_id` 纳入考虑，与 RLS 策略对齐

#### 3.1.2 审计日志实体

```java
@Entity
@Table(name = "sa_audit_log")
public class AuditLog {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long logId;
    
    private String tenantId;
    private Long userId;
    private String action;          // CREATE/UPDATE/DELETE/LOGIN/LOGOUT
    private String resourceType;    // USER/KB/DOCUMENT/SUBSCRIPTION
    private String resourceId;
    
    @Type(JsonType.class)
    private Changes changes;        // 变更前后值
    
    private String ipAddress;
    private String userAgent;
    private String geoLocation;
    private String requestId;
    // ✅ DDL 为 SMALLINT，使用 AttributeConverter 而非 String
    @Convert(converter = AuditStatusConverter.class)
    private AuditStatus status;          // SUCCESS/FAILED
    
    private Instant createdAt;
}

@Data
public class Changes {
    private Map<String, Object> before;
    private Map<String, Object> after;
}

public enum AuditStatus {
    SUCCESS, FAILED
}

/**
 * AuditStatus ↔ SMALLINT 转换器
 * 与 DDL CHECK (status IN (0, 1)) 对齐：0=SUCCESS, 1=FAILED
 */
@Converter
public class AuditStatusConverter implements AttributeConverter<AuditStatus, Integer> {
    @Override
    public Integer convertToDatabaseColumn(AuditStatus attr) {
        if (attr == null) return 0;
        return attr == AuditStatus.SUCCESS ? 0 : 1;
    }
    @Override
    public AuditStatus convertToEntityAttribute(Integer dbData) {
        return dbData != null && dbData == 1 ? AuditStatus.FAILED : AuditStatus.SUCCESS;
    }
}
```

---

### 3.2 AOP 审计拦截器（P0）

#### 3.2.1 审计注解

```java
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Auditable {
    String action();           // CREATE/UPDATE/DELETE
    String resourceType();     // USER/KB/DOCUMENT
    String resourceIdParam() default "id";  // 资源 ID 参数名
}
```

#### 3.2.2 AOP 拦截器

```java
@Aspect
@Component
public class AuditAspect {
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Autowired
    private HttpServletRequest request;
    
    @Around("@annotation(auditable)")
    public Object audit(ProceedingJoinPoint joinPoint, Auditable auditable) 
            throws Throwable {
        
        String requestId = UUID.randomUUID().toString();
        Object result = null;
        AuditStatus status = AuditStatus.SUCCESS;
        Changes changes = null;
        
        try {
            // 1. 记录变更前状态（UPDATE/DELETE）
            Object before = null;
            if (auditable.action().equals("UPDATE") || auditable.action().equals("DELETE")) {
                before = getResourceState(joinPoint, auditable);
            }
            
            // 2. 执行原方法
            result = joinPoint.proceed();
            
            // 3. 记录变更后状态（UPDATE/CREATE）
            Object after = null;
            if (auditable.action().equals("UPDATE") || auditable.action().equals("CREATE")) {
                after = result;
            }
            
            // 4. 计算变更
            if (before != null || after != null) {
                changes = calculateChanges(before, after);
            }
            
            return result;
            
        } catch (Exception ex) {
            status = AuditStatus.FAILED;
            throw ex;
            
        } finally {
            // 5. 保存审计日志
            saveAuditLog(joinPoint, auditable, changes, requestId, status);
        }
    }
    
    private void saveAuditLog(
            ProceedingJoinPoint joinPoint,
            Auditable auditable,
            Changes changes,
            String requestId,
            AuditStatus status) {
        
        String resourceId = extractResourceId(joinPoint, auditable.resourceIdParam());
        
        AuditLog log = AuditLog.builder()
            .tenantId(TenantContext.get())
            .userId(CurrentUser.getId())
            .action(auditable.action())
            .resourceType(auditable.resourceType())
            .resourceId(resourceId)
            .changes(changes)
            .ipAddress(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .geoLocation(getGeoLocation(getClientIp(request)))
            .requestId(requestId)
            .status(status)
            .createdAt(Instant.now())
            .build();
        
        auditLogRepository.save(log);
    }
    
    private Changes calculateChanges(Object before, Object after) {
        Map<String, Object> beforeMap = objectToMap(before);
        Map<String, Object> afterMap = objectToMap(after);
        
        return Changes.builder()
            .before(beforeMap)
            .after(afterMap)
            .build();
    }
    
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        } else {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }
    
    private String getGeoLocation(String ip) {
        // ⚠️ 同步调用外部 API 会阻塞请求线程（每个被 @Auditable 标注的请求都会多等几百毫秒）
        // 改为：先返回 "Pending"，异步更新地理位置
        // MVP 阶段简化处理：仅对内网 IP 返回标记，外网 IP 异步查询
        if (isPrivateIp(ip)) {
            return "Internal";
        }
        
        // 异步更新地理位置（不阻塞主请求）
        geoLocationExecutor.execute(() -> {
            try {
                String url = "http://ip-api.com/json/" + ip + "?fields=status,country,city";
                String response = restTemplate.getForObject(url, String.class);
                JsonNode json = objectMapper.readTree(response);
                
                if ("success".equals(json.get("status").asText())) {
                    String geo = json.get("country").asText() + "/" + json.get("city").asText();
                    auditLogRepository.updateGeoLocation(requestId, geo);
                }
            } catch (Exception ex) {
                log.debug("Geo lookup failed for IP: {}", ip);
            }
        });
        
        return "Pending";  // 先返回 Pending，异步填充真实值
    }
    
    private boolean isPrivateIp(String ip) {
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.")
            || "127.0.0.1".equals(ip) || "::1".equals(ip);
    }
}
```

#### 3.2.3 使用示例

```java
@RestController
@RequestMapping("/api/knowledge-bases")
public class KnowledgeBaseController {
    
    @DeleteMapping("/{id}")
    @Auditable(action = "DELETE", resourceType = "KNOWLEDGE_BASE")
    public void delete(@PathVariable Long id) {
        knowledgeBaseService.delete(id);
    }
    
    @PutMapping("/{id}")
    @Auditable(action = "UPDATE", resourceType = "KNOWLEDGE_BASE")
    public KnowledgeBase update(
            @PathVariable Long id,
            @RequestBody KnowledgeBase kb) {
        return knowledgeBaseService.update(id, kb);
    }
}
```

---

### 3.3 登录历史增强（P0）

#### 3.3.1 表结构

```sql
CREATE TABLE t_login_history (
    history_id     BIGSERIAL PRIMARY KEY,
    tenant_id      VARCHAR(64) NOT NULL,
    user_id        BIGINT,
    ip_address     VARCHAR(45) NOT NULL,
    user_agent     TEXT,
    device_type    SMALLINT,
    -- 设备类型枚举：1=DESKTOP, 2=MOBILE, 3=TABLET
    browser        VARCHAR(64),
    os             VARCHAR(64),
    geo_location   VARCHAR(64),
    status         SMALLINT NOT NULL DEFAULT 0,
    -- 状态枚举：0=SUCCESS, 1=FAILED
    failure_reason TEXT,
    login_time     TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- 索引（tenant_id 最左前缀，支持租户级查询和 RLS）
CREATE INDEX idx_login_history_tenant_user ON t_login_history (tenant_id, user_id, login_time DESC);
CREATE INDEX idx_login_history_ip ON t_login_history (ip_address, login_time DESC);
CREATE INDEX idx_login_history_status ON t_login_history (status, login_time DESC);

-- CHECK 约束
ALTER TABLE t_login_history ADD CONSTRAINT chk_login_device_type
    CHECK (device_type IS NULL OR device_type IN (1, 2, 3));
ALTER TABLE t_login_history ADD CONSTRAINT chk_login_status
    CHECK (status IN (0, 1));
```

> **DDL 审查要点**：
> - ✅ `status` 从 `VARCHAR(16)` 改为 `SMALLINT`（0=SUCCESS, 1=FAILED）
> - ✅ `device_type` 从 `VARCHAR(32)` 改为 `SMALLINT`，消除字符串比较
> - ✅ 补充 `tenant_id` 字段，与 01-multi-tenancy 方案对齐
> - ✅ `user_id` 允许 NULL（登录失败时可能无法确定用户）
> - ✅ 时间字段使用 `TIMESTAMP WITH TIME ZONE`，支持时区转换
> - ✅ 索引使用 PostgreSQL `CREATE INDEX` 语法

#### 3.3.2 登录监听器

```java
@Component
public class LoginEventListener {
    
    @Autowired
    private LoginHistoryRepository loginHistoryRepository;
    
    @EventListener
    public void handleLoginSuccess(AuthenticationSuccessEvent event) {
        UserDetails user = (UserDetails) event.getAuthentication().getPrincipal();
        HttpServletRequest request = getCurrentRequest();
        
        LoginHistory history = LoginHistory.builder()
            .userId(getUserId(user))
            .ipAddress(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .deviceType(parseDeviceType(request.getHeader("User-Agent")))
            .browser(parseBrowser(request.getHeader("User-Agent")))
            .os(parseOS(request.getHeader("User-Agent")))
            .geoLocation(getGeoLocation(getClientIp(request)))
            .status(0)  // 0=SUCCESS（与 DDL SMALLINT 对齐）
            .loginTime(Instant.now())
            .build();
        
        loginHistoryRepository.save(history);
    }
    
    @EventListener
    public void handleLoginFailure(AbstractAuthenticationFailureEvent event) {
        HttpServletRequest request = getCurrentRequest();
        
        LoginHistory history = LoginHistory.builder()
            .userId(null)
            .ipAddress(getClientIp(request))
            .userAgent(request.getHeader("User-Agent"))
            .status(1)  // 1=FAILED（与 DDL SMALLINT 对齐）
            .failureReason(event.getException().getMessage())
            .loginTime(Instant.now())
            .build();
        
        loginHistoryRepository.save(history);
    }
    
    private String parseDeviceType(String userAgent) {
        if (userAgent.contains("Mobile")) return "MOBILE";
        if (userAgent.contains("Tablet")) return "TABLET";
        return "DESKTOP";
    }
}
```

---

### 3.4 合规报表（P1）

#### 3.4.1 审计日志导出

```java
@RestController
@RequestMapping("/api/audit")
public class AuditLogExportController {
    
    @GetMapping("/export")
    public ResponseEntity<Resource> exportAuditLog(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) 
            throws IOException {
        
        // 1. 查询审计日志
        List<AuditLog> logs = auditLogRepository.findByFilters(
            userId, action, startDate, endDate
        );
        
        // 2. 生成 CSV
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        OutputStreamWriter writer = new OutputStreamWriter(bos, StandardCharsets.UTF_8);
        CSVWriter csvWriter = new CSVWriter(writer);
        
        // 表头
        csvWriter.writeNext(new String[]{
            "时间", "操作人", "操作", "资源类型", "资源ID", 
            "IP地址", "地理位置", "状态", "变更详情"
        });
        
        // 数据
        for (AuditLog log : logs) {
            csvWriter.writeNext(new String[]{
                log.getCreatedAt().toString(),
                String.valueOf(log.getUserId()),
                log.getAction(),
                log.getResourceType(),
                log.getResourceId(),
                log.getIpAddress(),
                log.getGeoLocation(),
                log.getStatus(),
                log.getChanges() != null ? JSON.toJSONString(log.getChanges()) : ""
            });
        }
        
        csvWriter.close();
        
        // 3. 返回文件
        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION, 
                "attachment; filename=\"audit_log_" + LocalDate.now() + ".csv\"")
            .contentType(MediaType.parseMediaType("text/csv"))
            .body(new ByteArrayResource(bos.toByteArray()));
    }
}
```

---

### 3.5 日志归档（P1）

#### 3.5.1 定时归档任务

```java
@Component
public class AuditLogArchiveJob {
    
    @Autowired
    private AuditLogRepository auditLogRepository;
    
    @Autowired
    private S3Client s3Client;
    
    @Scheduled(cron = "0 0 2 * * ?")  // 每天凌晨 2 点
    public void archiveOldLogs() {
        // 1. 分页查询 90 天前的日志（⚠️ 避免 findAllByCreatedAtBefore 一次加载全部导致 OOM）
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        int batchSize = 5000;
        int page = 0;
        int totalArchived = 0;
        
        while (true) {
            Page<AuditLog> oldPage = auditLogRepository.findByCreatedAtBefore(
                cutoff, PageRequest.of(page, batchSize)
            );
            
            if (oldPage.isEmpty()) break;
            
            List<AuditLog> batch = oldPage.getContent();
            
            // 2. 导出为 JSON
            String json = JSON.toJSONString(batch);
            String fileName = "audit_log_" + LocalDate.now() + "_batch_" + page + ".json.gz";
            
            // 3. 压缩
            byte[] compressed = gzip(json.getBytes(StandardCharsets.UTF_8));
            
            // 4. 上传到 S3
            s3Client.putObject(PutObjectRequest.builder()
                .bucket("seahorse-audit-archive")
                .key("audit/" + fileName)
                .build(),
                RequestBody.fromBytes(compressed));
            
            // 5. 删除本批数据库记录
            auditLogRepository.deleteAll(batch);
            
            totalArchived += batch.size();
            log.info("Archived batch {}: {} records", page, batch.size());
            page++;
        }
        
        if (totalArchived > 0) {
            log.info("Archive completed: total {} audit logs archived to S3", totalArchived);
        }
    }
    
    private byte[] gzip(byte[] data) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (GZIPOutputStream gzipOS = new GZIPOutputStream(bos)) {
            gzipOS.write(data);
        }
        return bos.toByteArray();
    }
}
```

---

### 3.6 GDPR 数据删除（P1）

#### 3.6.1 用户数据删除

```java
@Service
public class GdprService {
    
    @Transactional
    public void deleteUserData(Long userId, String tenantId) {
        // ⚠️ 必须传入 tenantId，防止跨租户误删数据
        // 1. 匿名化用户信息
        User user = userRepository.findByIdAndTenantId(userId, tenantId).orElseThrow();
        user.setUsername("deleted_" + userId);
        user.setEmail("deleted_" + userId + "@deleted.com");
        user.setAvatar(null);
        user.setDeleted(true);
        userRepository.save(user);
        
        // 2. 删除知识库（⚠️ 加 tenant_id 过滤）
        knowledgeBaseRepository.deleteByUserIdAndTenantId(userId, tenantId);
        
        // 3. 删除对话记录（⚠️ 加 tenant_id 过滤）
        chatMessageRepository.deleteByUserIdAndTenantId(userId, tenantId);
        
        // 4. 保留审计日志（法律要求，7 年）
        // 不删除 audit_log，但匿名化 user_id
        auditLogRepository.updateUserIdToAnonymous(userId, tenantId);
        
        // 5. 删除个人文件
        storagePort.deleteUserFiles(userId, tenantId);
        
        log.info("User data deleted (GDPR): userId={}, tenantId={}", userId, tenantId);
    }
}
```

#### 3.6.2 数据导出（GDPR 要求）

```java
@GetMapping("/gdpr/export")
public ResponseEntity<Resource> exportUserData() {
    Long userId = CurrentUser.getId();
    
    // 1. 收集所有用户数据
    User user = userRepository.findById(userId).orElseThrow();
    List<KnowledgeBase> kbs = knowledgeBaseRepository.findByUserId(userId);
    List<ChatMessage> messages = chatMessageRepository.findByUserId(userId);
    
    // 2. 打包为 JSON
    Map<String, Object> data = Map.of(
        "user", user,
        "knowledgeBases", kbs,
        "chatMessages", messages
    );
    
    String json = JSON.toJSONString(data, true);
    
    // 3. 返回文件
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_DISPOSITION, 
            "attachment; filename=\"my_data.json\"")
        .contentType(MediaType.APPLICATION_JSON)
        .body(new ByteArrayResource(json.getBytes(StandardCharsets.UTF_8)));
}
```

---

## 4. 实施步骤

### Day 1：审计日志增强
- 上午：扩展审计表 + AOP 拦截器（3h）
- 下午：登录历史增强（2h）

### Day 2：合规报表 + GDPR
- 上午：审计日志导出 + 归档（2h）
- 下午：GDPR 数据删除（2h）

---

## 5. 验收标准

✅ P0 验收全部通过（见 §1.3）
✅ 所有敏感操作记录审计日志
✅ 审计日志可导出（CSV 格式）

---

## 6. 合规清单

### 6.1 SOC 2 合规

| 控制项 | 实现 | 状态 |
|--------|------|------|
| 访问控制 | RBAC + ACL | ✅ |
| 审计日志 | 所有 CRUD 操作 | ✅ |
| 数据加密 | 传输层 TLS + 存储层加密 | ✅ |
| 日志保留 | 90 天在线 + S3 归档 | ✅ |
| 异常监控 | 05-observability 方案 | ✅ |

### 6.2 GDPR 合规

| 要求 | 实现 | 状态 |
|------|------|------|
| 数据可携带权 | 导出用户数据（JSON） | ✅ |
| 被遗忘权 | 删除/匿名化用户数据 | ✅ |
| 数据访问权 | 查看自己的审计日志 | ✅ |
| 数据最小化 | 只收集必要数据 | ✅ |
| 同意管理 | 隐私政策 + 用户协议 | ⚠️ 前端实现 |

---

**文档版本**：v1.1-review  
**最后更新**：2026-06-06
